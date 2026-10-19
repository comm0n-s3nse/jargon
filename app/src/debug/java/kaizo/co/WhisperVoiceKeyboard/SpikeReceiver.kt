package kaizo.co.WhisperVoiceKeyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.parakeetcpp.ParakeetContext
import com.whispercpp.whisper.WhisperContext
import kaizo.co.WhisperVoiceKeyboard.engine.ParakeetEngine
import kaizo.co.WhisperVoiceKeyboard.engine.SpeechEngine
import kaizo.co.WhisperVoiceKeyboard.engine.WhisperEngine
import kaizo.co.WhisperVoiceKeyboard.media.decodeWaveFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "SPIKE"

/**
 * Debug-only. Loads every model in --es models a,b (whisper .bin or parakeet .gguf; all stay
 * in memory together), then times each on --es wav, --ei runs times at --ei threads.
 * Results go to logcat under SPIKE.
 */
class SpikeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val paths = intent.getStringExtra("models")?.split(",") ?: return
        val wav = intent.getStringExtra("wav") ?: return
        val threads = intent.getIntExtra("threads", 4)
        val runs = intent.getIntExtra("runs", 3)
        // returns at once: a receiver that runs long is treated as hung. The process stays up.
        CoroutineScope(Dispatchers.Default).launch {
            val loaded = mutableListOf<Pair<String, SpeechEngine>>()
            try {
                for (path in paths) {
                    val t0 = SystemClock.elapsedRealtime()
                    val engine = if (path.endsWith(".gguf")) {
                        ParakeetEngine(ParakeetContext.load(path))
                    } else {
                        WhisperEngine(WhisperContext.createContextFromFile(path))
                    }
                    loaded += File(path).name to engine
                    Log.i(TAG, "loaded ${File(path).name} in ${SystemClock.elapsedRealtime() - t0} ms")
                }
                val samples = decodeWaveFile(File(wav))
                Log.i(TAG, "audio ${samples.size / 16} ms, $threads threads")
                for ((name, engine) in loaded) {
                    repeat(runs) { i ->
                        val t0 = SystemClock.elapsedRealtime()
                        val text = engine.transcribe(samples, threads, "en")
                        Log.i(TAG, "$name run ${i + 1}: ${SystemClock.elapsedRealtime() - t0} ms: ${text.trim()}")
                    }
                }
                val mem = File("/proc/self/status").readLines()
                    .filter { it.startsWith("VmRSS") || it.startsWith("VmHWM") }
                Log.i(TAG, "memory with ${loaded.size} model(s) loaded: ${mem.joinToString(" ") { it.replace(Regex("\\s+"), " ") }}")
            } catch (e: Throwable) {
                Log.e(TAG, "failed", e)
            } finally {
                loaded.forEach { it.second.release() }
                Log.i(TAG, "done")
            }
        }
    }
}
