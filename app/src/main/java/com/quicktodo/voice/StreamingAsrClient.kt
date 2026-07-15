package com.quicktodo.voice

import okhttp3.*
import okio.ByteString
import org.json.JSONObject
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

    private fun makeHeader(messageType: Int, flags: Int): ByteArray {
        val headerSize = 8 // always include sequence number
        val header = ByteArray(headerSize)
        header[0] = ((0b0001 shl 4) or (headerSize / 4)).toByte()
        header[1] = ((messageType shl 4) or flags).toByte()
        header[2] = 0b0001_0000.toByte() // JSON serialization, no compression
        val seq = if (flags == FLAG_LAST_HAS_SEQ) -(++sequenceNum) else ++sequenceNum
        val seqBytes = ByteBuffer.allocate(4).putInt(seq).array()
        System.arraycopy(seqBytes, 0, header, 4, 4)
        return header
    }

    fun transcribe(audioProvider: (ChunkSender) -> Unit): StreamingAsrResult {
        val requestId = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("X-Api-Key", apiKey)
            .header("X-Api-Resource-Id", resourceId)
            .header("X-Api-Request-Id", requestId)
            .header("X-Api-Sequence", "-1")

        webSocket = client.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                // Send full client request
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
                val header = makeHeader(MSG_FULL_REQUEST, FLAG_HAS_SEQ)
                ws.send(ByteString.of(*header, *payload))

                // Start audio streaming in background thread
                Thread {
                    try {
                        audioProvider(object : ChunkSender {
                            override fun sendChunk(data: ByteArray, isLast: Boolean) {
                                val flags = if (isLast) FLAG_LAST_HAS_SEQ else FLAG_HAS_SEQ
                                val hdr = makeHeader(MSG_AUDIO_ONLY, flags)
                                webSocket?.send(ByteString.of(*hdr, *data))
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
                val messageType = (raw[1].toInt() shr 4) and 0x0F

                when (messageType) {
                    MSG_SERVER_RESPONSE -> {
                        val headerLen = if (raw.size >= 8 && (raw[0].toInt() and 0x0F) > 1) 8 else 4
                        val payloadStart = if (raw.size > headerLen + 4 && (raw[1].toInt() and 0x0F) == 0b0011) headerLen + 4 else headerLen
                        if (payloadStart < raw.size) {
                            val payloadStr = raw.copyOfRange(payloadStart, raw.size).toString(Charsets.UTF_8).trim()
                            if (payloadStr.isNotEmpty()) {
                                try {
                                    val json = JSONObject(payloadStr)
                                    val result = json.optJSONObject("result")
                                    if (result != null) {
                                        val text = result.optString("text", "")
                                        if (text.isNotEmpty()) resultBuilder.append(text)
                                    }
                                    if (json.has("code")) {
                                        val code = json.optInt("code")
                                        if (code == 20000000) {
                                            latch.countDown()
                                        }
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }
                    MSG_ERROR -> {
                        val payloadStr = bytes.utf8()
                        errorMsg = "ASR error: ${payloadStr.take(200)}"
                        latch.countDown()
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                errorMsg = t.message ?: "WebSocket connection failed"
                latch.countDown()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                latch.countDown()
            }
        })

        latch.await(30, TimeUnit.SECONDS)

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
