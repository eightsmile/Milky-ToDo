package com.quicktodo.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

class StreamingAudioRecorder(private val context: android.content.Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_MS = 200
        const val CHUNK_SIZE = SAMPLE_RATE * 2 * CHUNK_MS / 1000  // 6400 bytes
    }

    private var audioRecord: AudioRecord? = null
    @Volatile var isRecording = false
        private set
    private var chunkCallback: ((ByteArray, Boolean) -> Unit)? = null

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

        val bufferSize = minBuffer.coerceAtLeast(CHUNK_SIZE * 2)

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
            onError("AudioRecord not initialized (try changing sample rate)")
            return
        }

        chunkCallback = onChunk
        isRecording = true
        audioRecord?.startRecording()

        val buffer = ByteArray(CHUNK_SIZE)
        var sentFirst = false

        try {
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, CHUNK_SIZE) ?: -1
                if (read > 0) {
                    val chunk = if (read == CHUNK_SIZE) buffer else buffer.copyOf(read)
                    onChunk(chunk, false)
                    sentFirst = true
                } else if (read < 0) {
                    break
                }
            }
            if (sentFirst) onChunk(ByteArray(0), true)
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
        chunkCallback = null
    }
}
