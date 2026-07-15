package com.quicktodo.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat

class StreamingAudioRecorder(private val context: android.content.Context) {

    companion object {
        const val TAG = "StreamingRecorder"
        const val SAMPLE_RATE = 16000
        const val CHUNK_MS = 200
        const val CHUNK_SIZE = SAMPLE_RATE * 2 * CHUNK_MS / 1000  // 6400
    }

    private var audioRecord: AudioRecord? = null
    @Volatile var isRecording = false
        private set
    var totalBytesSent: Int = 0
        private set

    var onDebug: ((String) -> Unit)? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start(
        onChunk: (ByteArray, Boolean) -> Unit,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!hasPermission()) {
            onError("Microphone permission required")
            return
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onError("Sample rate $SAMPLE_RATE not supported")
            return
        }
        onDebug?.invoke("Min buffer: $minBuffer bytes")

        val bufferSize = minBuffer.coerceAtLeast(CHUNK_SIZE * 2)
        onDebug?.invoke("Buffer size: $bufferSize bytes")

        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            onError("AudioRecord failed: ${e.message}")
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            onError("AudioRecord not initialized")
            return
        }

        totalBytesSent = 0
        isRecording = true
        audioRecord?.startRecording()
        onDebug?.invoke("Recording started at $SAMPLE_RATE Hz")

        // Use ShortArray for proper 16-bit PCM reading
        val shortBuffer = ShortArray(CHUNK_SIZE / 2)
        var sentFirst = false
        var readCount = 0

        try {
            while (isRecording) {
                val samplesRead = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                readCount++
                if (samplesRead > 0) {
                    // Convert ShortArray to little-endian ByteArray
                    val byteBuf = ByteArray(samplesRead * 2)
                    for (i in 0 until samplesRead) {
                        val s = shortBuffer[i].toInt()
                        byteBuf[i * 2] = (s and 0xFF).toByte()
                        byteBuf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    onChunk(byteBuf, false)
                    totalBytesSent += byteBuf.size
                    sentFirst = true
                } else if (samplesRead < 0) {
                    onDebug?.invoke("read error at count $readCount: $samplesRead")
                    break
                }
                // samplesRead == 0 means no data yet, keep looping
            }

            // Always send last packet — even if no audio was captured
            onChunk(ByteArray(0), true)
            onDebug?.invoke("Recording done. Read $readCount times, sent ${totalBytesSent} bytes")
            onComplete()
        } catch (e: Exception) {
            onError(e.message ?: "Recording error")
        } finally {
            stop()
        }
    }

    fun stop() {
        isRecording = false
        try { audioRecord?.stop() } catch (_: Exception) { }
        try { audioRecord?.release() } catch (_: Exception) { }
        audioRecord = null
    }
}
