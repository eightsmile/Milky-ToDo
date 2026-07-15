package com.quicktodo.voice

import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class StreamingAsrResult(
    val success: Boolean,
    val text: String = "",
    val error: String = ""
)

class StreamingAsrClient(
    private val apiKey: String,
    private val resourceId: String,
    private val endpoint: String
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val resultBuilder = StringBuilder()
    private var errorMsg: String? = null
    private var sequenceNum = 0

    companion object {
        const val MSG_FULL_REQUEST = 0b0001
        const val MSG_AUDIO_ONLY = 0b0010
        const val MSG_SERVER_RESPONSE = 0b1001
        const val MSG_ERROR = 0b1111
        const val FLAG_HAS_SEQ = 0b0001
        const val FLAG_LAST_HAS_SEQ = 0b0011
    }

    private fun makeFrame(messageType: Int, flags: Int, payload: ByteArray): ByteArray {
        val frame = ByteArrayOutputStream()
        // Byte 0: version(4) + header_size(4) in 4-byte units (= 1 for 4 bytes)
        frame.write(((0b0001 shl 4) or 0b0001).toInt())
        // Byte 1: message_type(4) + flags(4)
        frame.write(((messageType shl 4) or flags).toInt())
        // Byte 2: serialization(4)=JSON(1) + compression(4)=none(0)
        frame.write(0b0001_0000.toInt())
        // Byte 3: reserved
        frame.write(0)

        // Sequence number (4 bytes, big-endian)
        val seq = if (flags == FLAG_LAST_HAS_SEQ) -(++sequenceNum) else ++sequenceNum
        val seqBytes = ByteBuffer.allocate(4).putInt(seq).array()
        frame.write(seqBytes)

        // Body/payload size (4 bytes, big-endian) — required by protocol
        val sizeBytes = ByteBuffer.allocate(4).putInt(payload.size).array()
        frame.write(sizeBytes)

        // Payload
        frame.write(payload)
        return frame.toByteArray()
    }

    fun transcribe(audioProvider: (ChunkSender) -> Unit, onDebug: ((String) -> Unit)? = null): StreamingAsrResult {
        val requestId = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("X-Api-Key", apiKey)
            .header("X-Api-Resource-Id", resourceId)
            .header("X-Api-Request-Id", requestId)
            .header("X-Api-Sequence", "-1")
            .header("X-Api-Connect-Id", requestId)

        webSocket = client.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                onDebug?.invoke("WebSocket connected")
                // Send full client request (JSON config)
                val config = JSONObject().apply {
                    put("user", JSONObject().apply { put("uid", apiKey.take(15)) })
                    put("audio", JSONObject().apply {
                        put("format", "pcm")
                        put("codec", "raw")
                        put("rate", 16000)
                        put("channel", 1)
                    })
                    put("request", JSONObject().apply {
                        put("model_name", "bigmodel")
                        put("enable_punc", true)
                        put("enable_itn", true)
                    })
                }
                val payload = config.toString().toByteArray(Charsets.UTF_8)
                val frame = makeFrame(MSG_FULL_REQUEST, FLAG_HAS_SEQ, payload)
                ws.send(okio.Buffer().write(frame).snapshot())
                onDebug?.invoke("Config sent (${payload.size} bytes)")

                // Start audio streaming in background thread
                Thread {
                    try {
                        audioProvider(object : ChunkSender {
                            override fun sendChunk(data: ByteArray, isLast: Boolean) {
                                val flags = if (isLast) FLAG_LAST_HAS_SEQ else FLAG_HAS_SEQ
                                val frame = makeFrame(MSG_AUDIO_ONLY, flags, data)
                                webSocket?.send(okio.Buffer().write(frame).snapshot())
                            }
                        })
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "Audio streaming error"
                    }
                }.start()
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (bytes.size < 4) return
                val raw = bytes.toByteArray()
                val flags = raw[1].toInt() and 0x0F
                val messageType = (raw[1].toInt() shr 4) and 0x0F

                when (messageType) {
                    MSG_SERVER_RESPONSE -> {
                        // Server ALWAYS sends seq + body_size, regardless of flags bit
                        val payloadStart = 12
                        if (payloadStart < raw.size) {
                            val payloadStr = raw.copyOfRange(payloadStart, raw.size).toString(Charsets.UTF_8).trim()
                            if (payloadStr.isNotEmpty()) {
                                try {
                                    val json = JSONObject(payloadStr)
                                    // Accumulate text from result
                                    val result = json.optJSONObject("result")
                                    if (result != null) {
                                        val text = result.optString("text", "")
                                        if (text.isNotEmpty()) {
                                            resultBuilder.setLength(0)  // replace, don't append
                                            resultBuilder.append(text)
                                        }
                                    }
                                    // Server sends audio_info when processing is complete
                                    if (json.has("audio_info")) {
                                        latch.countDown()
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }
                    MSG_ERROR -> {
                        errorMsg = "ASR error: ${bytes.utf8().take(200)}"
                        latch.countDown()
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                errorMsg = t.message ?: "WebSocket connection failed"
                onDebug?.invoke("WS failed: $errorMsg")
                latch.countDown()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onDebug?.invoke("WS closed: $code $reason")
                latch.countDown()
            }
        })

        latch.await(15, TimeUnit.SECONDS)

        webSocket?.close(1000, "OK")
        webSocket = null

        if (errorMsg != null) return StreamingAsrResult(false, error = errorMsg!!)

        val text = resultBuilder.toString().trim()
        return if (text.isNotEmpty()) StreamingAsrResult(true, text = text)
        else StreamingAsrResult(false, error = "No voice input detected")
    }

    interface ChunkSender {
        fun sendChunk(data: ByteArray, isLast: Boolean)
    }

    fun cancel() {
        webSocket?.close(1000, "Cancelled")
        webSocket = null
    }
}
