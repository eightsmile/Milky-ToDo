package com.quicktodo.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmRecorder(private val context: android.content.Context) {

    companion object {
        const val SAMPLE_RATE = 16000
    }

    private var audioRecord: AudioRecord? = null
    @Volatile var isRecording = false
        private set
    private val buffer = ByteArrayOutputStream()

    var onDebug: ((String) -> Unit)? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start(onError: (String) -> Unit = {}) {
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

        // Use exactly the min buffer to avoid any resampling artifacts
        val bufferSize = minBuffer.coerceAtLeast(SAMPLE_RATE * 2 / 10) // ~100ms
        onDebug?.invoke("PCM: minBuf=$minBuffer bufSize=$bufferSize")

        // Try built-in VOICE_RECOGNITION first, fall back to MIC
        audioRecord = try {
            createAudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufferSize)
        } catch (e: Exception) {
            try {
                createAudioRecord(MediaRecorder.AudioSource.MIC, bufferSize)
            } catch (e2: Exception) {
                onError("AudioRecord failed: ${e2.message}")
                return
            }
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            onError("AudioRecord not initialized")
            return
        }

        buffer.reset()
        isRecording = true
        audioRecord?.startRecording()
        onDebug?.invoke("PCM: recording at $SAMPLE_RATE Hz")

        // Read in background thread to avoid blocking
        Thread({
            val shortBuf = ShortArray(bufferSize / 2)
            val byteBuf = ByteBuffer.allocate(bufferSize)
            byteBuf.order(ByteOrder.LITTLE_ENDIAN)
            val byteShortBuf = byteBuf.asShortBuffer()

            try {
                while (isRecording) {
                    val read = audioRecord?.read(shortBuf, 0, shortBuf.size) ?: -1
                    if (read > 0) {
                        byteBuf.clear()
                        byteShortBuf.clear()
                        byteShortBuf.put(shortBuf, 0, read)
                        byteBuf.position(0)
                        buffer.write(byteBuf.array(), 0, read * 2)
                    } else if (read < 0) break
                }
            } catch (_: Exception) { }
        }, "PcmRecorder").start()
    }

    private fun createAudioRecord(source: Int, bufferSize: Int): AudioRecord {
        return AudioRecord(
            source,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    fun signalStop(): ByteArray {
        isRecording = false
        try { audioRecord?.stop() } catch (_: Exception) { }
        try { audioRecord?.release() } catch (_: Exception) { }
        audioRecord = null
        return buffer.toByteArray()
    }
}
