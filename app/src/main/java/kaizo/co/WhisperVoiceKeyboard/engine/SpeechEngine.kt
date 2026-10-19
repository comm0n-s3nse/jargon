package kaizo.co.WhisperVoiceKeyboard.engine

import com.whispercpp.whisper.WhisperContext

/**
 * A speech-to-text engine with one model loaded. The keyboard keeps a single engine in memory;
 * switching models releases the old one first.
 */
interface SpeechEngine {
    /** [samples] are 16 kHz mono floats; [language] is a code such as "pt", or "auto". */
    suspend fun transcribe(samples: FloatArray, threads: Int, language: String): String

    suspend fun release()
}

class WhisperEngine(private val context: WhisperContext) : SpeechEngine {
    override suspend fun transcribe(samples: FloatArray, threads: Int, language: String) =
        context.transcribeData(samples, false, threads, language)

    override suspend fun release() = context.release()
}
