package com.quicktodo.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class StreamingAudioRecorder(private val context: android.content.Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_MS = 200
        const val CHUNK_SIZE = SAMPLE_RATE * 2 * CHUNK_MS / 1000  // 6400 bytes
    }

    private var audioRecord: AudioRecord? = null
    @Volatile var isRecording = false
        private set
    val isRunning: Boolean get() = isRecording
    private var sentFirstChunk = false
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

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_SIZE * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            onError("AudioRecord initialization failed")
            return
        }

        chunkCallback = onChunk
        isRecording = true
        audioRecord?.startRecording()

        // Send first chunk immediately as the last flag needs to be set at the end
        sentFirstChunk = false
        val buffer = ByteArray(CHUNK_SIZE)
        try {
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, CHUNK_SIZE) ?: -1
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    onChunk(chunk, false)
                    sentFirstChunk = true
                } else if (read < 0) {
                    break
                }
            }
            // Send empty chunk as last signal
            if (sentFirstChunk) onChunk(ByteArray(0), true)
            onComplete()
        } catch (e: Exception) {
            onError(e.message ?: "Recording error")
        } finally {
            stop()
        }
    }

    fun startBlocking(onChunk: (ByteArray, Boolean) -> Unit, onError: (String) -> Unit) {
        val latch = CountDownLatch(1)
        var errorRef: String? = null
        start(
            onChunk = onChunk,
            onComplete = { latch.countDown() },
            onError = { errorRef = it; latch.countDown() }
        )
        latch.await(60, TimeUnit.SECONDS)
        if (errorRef != null) onError(errorRef!!)
    }

    fun stop() {
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) { }
        try {
            audioRecord?.release()
        } catch (_: Exception) { }
        audioRecord = null
        chunkCallback = null
    }
}
