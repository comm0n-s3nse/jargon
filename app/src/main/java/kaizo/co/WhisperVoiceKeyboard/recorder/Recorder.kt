package kaizo.co.WhisperVoiceKeyboard.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kaizo.co.WhisperVoiceKeyboard.media.encodeWaveFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

class Recorder {
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var recorder: AudioRecordThread? = null

    suspend fun startRecording(
        outputFile: File,
        onError: (Exception) -> Job,
        transcriptionCallback: ((ShortArray) -> Unit)? = null,
        onLevel: ((Float) -> Unit)? = null, // how loud each chunk was, 0 to 1, about 25 times a second
    ) =
        withContext(scope.coroutineContext) {
            recorder = AudioRecordThread(outputFile, onError, transcriptionCallback, onLevel)
            recorder?.start()
        }

    suspend fun stopRecording() = withContext(scope.coroutineContext) {
        recorder?.stopRecording()
        @Suppress("BlockingMethodInNonBlockingContext")
        recorder?.join()
        recorder = null
    }
}

private class AudioRecordThread(
    private val outputFile: File,
    private val onError: (Exception) -> Job,
    private val transcriptionCallback: ((ShortArray) -> Unit)?,
    private val onLevel: ((Float) -> Unit)?,
) :
    Thread("AudioRecorder") {
    private var quit = AtomicBoolean(false)
    private val meter = LevelMeter()

    @SuppressLint("MissingPermission")
    override fun run() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4
            // 40 ms chunks, so the level meter moves with speech; the hardware buffer stays bufferSize
            val buffer = ShortArray(640)

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            try {
                audioRecord.startRecording()

                // primitive growable buffer; a boxed list copied on every read made long
                // recordings quadratically slower
                var allData = ShortArray(16000 * 30)
                var size = 0
                var lastCallback = 0L

                while (!quit.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        if (size + read > allData.size) {
                            allData = allData.copyOf(maxOf(allData.size * 2, size + read))
                        }
                        System.arraycopy(buffer, 0, allData, size, read)
                        size += read
                        onLevel?.invoke(meter.level(buffer, read))
                    } else {
                        throw java.lang.RuntimeException("audioRecord.read returned $read")
                    }
                    // run transcription callback, at most 4 times a second
                    val now = System.currentTimeMillis()
                    if (transcriptionCallback != null && now - lastCallback >= 250) {
                        lastCallback = now
                        transcriptionCallback?.invoke(allData.copyOf(size))
                    }
                }

                audioRecord.stop()
                encodeWaveFile(outputFile, allData.copyOf(size))
            } finally {
                audioRecord.release()
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun stopRecording() {
        quit.set(true)
    }
}

// Loudness above the background, 0 to 1. Phones apply gain control and rooms differ, so a fixed
// dBFS scale showed a quiet room at half height; this follows the quietest recent chunk as the
// floor (dropping at once, rising slowly) and shows how far above it the chunk is. Speech runs
// 15 to 30 dB over room noise, so SPAN puts a normal voice near the top.
private class LevelMeter {
    private var floorDb = Double.NaN

    fun level(buffer: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) { val s = buffer[i].toDouble(); sum += s * s }
        val db = 20 * log10(sqrt(sum / n) / 32768.0 + 1e-9)
        // the first chunks after the hardware starts are all zeros; they are not a floor
        if (db < SILENCE_DB) return 0f
        floorDb = when {
            floorDb.isNaN() || db < floorDb -> db
            else -> minOf(floorDb + RISE_DB, db)
        }
        return ((db - floorDb) / SPAN_DB).coerceIn(0.0, 1.0).toFloat()
    }

    companion object {
        const val SPAN_DB = 25.0
        const val RISE_DB = 1.0 // per 40 ms chunk: 25 dB a second
        const val SILENCE_DB = -75.0
    }
}