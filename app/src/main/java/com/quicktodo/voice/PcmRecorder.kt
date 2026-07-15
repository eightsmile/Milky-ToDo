package com.quicktodo.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

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
        onDebug?.invoke("Min buffer: $minBuffer bytes")

        val bufferSize = minBuffer.coerceAtLeast(SAMPLE_RATE * 2 / 5) // at least 100ms
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

        buffer.reset()
        isRecording = true
        audioRecord?.startRecording()
        onDebug?.invoke("Recording started at $SAMPLE_RATE Hz")

        // Read in background thread
        Thread {
            val readBuffer = ShortArray(bufferSize / 2)
            try {
                while (isRecording) {
                    val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (read > 0) {
                        // Convert ShortArray to little-endian ByteArray
                        for (i in 0 until read) {
                            val s = readBuffer[i].toInt()
                            buffer.write(s and 0xFF)
                            buffer.write((s shr 8) and 0xFF)
                        }
                    } else if (read < 0) break
                }
            } catch (_: Exception) { }
        }.start()
    }

    fun signalStop(): ByteArray {
        isRecording = false
        try { audioRecord?.stop() } catch (_: Exception) { }
        try { audioRecord?.release() } catch (_: Exception) { }
        audioRecord = null
        return buffer.toByteArray()
    }
}
