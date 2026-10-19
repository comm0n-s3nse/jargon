package kaizo.co.WhisperVoiceKeyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.parakeetcpp.ParakeetContext
import com.transcribecpp.TranscribeContext
import com.whispercpp.whisper.WhisperContext
import kaizo.co.WhisperVoiceKeyboard.engine.ParakeetEngine
import kaizo.co.WhisperVoiceKeyboard.engine.SpeechEngine
import kaizo.co.WhisperVoiceKeyboard.engine.TranscribeEngine
import kaizo.co.WhisperVoiceKeyboard.engine.WhisperEngine
import kaizo.co.WhisperVoiceKeyboard.media.decodeWaveFile
import kaizo.co.WhisperVoiceKeyboard.models.Gguf
import kaizo.co.WhisperVoiceKeyboard.models.ModelEngine
import kaizo.co.WhisperVoiceKeyboard.models.engineFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "PROBE"

/**
 * Debug-only. For each model path in --es models a,b: picks its engine the way the app does (from
 * the file's header), loads it, and times --ei runs transcriptions of --es wav at --ei threads,
 * optionally with --es language xx and --es translate yy. Results go to logcat under PROBE.
 * The app must be in the foreground, or Android freezes it as idle part-way through.
 */
class EngineProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val paths = intent.getStringExtra("models")?.split(",") ?: return
        val wav = intent.getStringExtra("wav") ?: return
        val threads = intent.getIntExtra("threads", 4)
        val runs = intent.getIntExtra("runs", 3)
        val language = intent.getStringExtra("language") ?: "en"
        val translate = intent.getStringExtra("translate")
        CoroutineScope(Dispatchers.Default).launch {
            val loaded = mutableListOf<Pair<String, SpeechEngine>>()
            try {
                for (path in paths) {
                    val file = File(path)
                    val engine = engineFor(file.name, Gguf.read(file))
                        ?: throw IllegalStateException("${file.name}: no engine for this file")
                    val t0 = SystemClock.elapsedRealtime()
                    val speech: SpeechEngine = when (engine) {
                        ModelEngine.WHISPER_CPP -> WhisperEngine(WhisperContext.createContextFromFile(path))
                        ModelEngine.PARAKEET_CPP -> ParakeetEngine(ParakeetContext.load(path))
                        ModelEngine.TRANSCRIBE_CPP -> TranscribeEngine(TranscribeContext.load(path, threads))
                    }
                    loaded += file.name to speech
                    Log.i(TAG, "${file.name}: engine=$engine, loaded in ${SystemClock.elapsedRealtime() - t0} ms, canTranslate=${speech.canTranslate}")
                }
                val samples = decodeWaveFile(File(wav))
                Log.i(TAG, "audio ${samples.size / 16} ms, $threads threads, language=$language, translate=$translate")
                for ((name, speech) in loaded) {
                    repeat(runs) { i ->
                        val t0 = SystemClock.elapsedRealtime()
                        val text = speech.transcribe(samples, threads, language, translate)
                        Log.i(TAG, "$name run ${i + 1}: ${SystemClock.elapsedRealtime() - t0} ms: ${text.trim()}")
                    }
                }
                val mem = File("/proc/self/status").readLines().filter { it.startsWith("VmHWM") }
                Log.i(TAG, "memory with ${loaded.size} model(s) loaded: ${mem.joinToString(" ").replace(Regex("\\s+"), " ")}")
            } catch (e: Throwable) {
                Log.e(TAG, "failed", e)
            } finally {
                loaded.forEach { it.second.release() }
                Log.i(TAG, "done")
            }
        }
    }
}
