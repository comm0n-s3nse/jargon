package kaizo.co.WhisperVoiceKeyboard.engine

import com.whispercpp.whisper.WhisperContext

/**
 * A speech-to-text engine with one model loaded. The keyboard keeps a single engine in memory;
 * switching models releases the old one first.
 */
interface SpeechEngine {
    /**
     * [samples] are 16 kHz mono floats; [language] is the language spoken, a code such as "pt", or
     * "auto". [translateTo] asks for the speech to come out in that language, for engines whose
     * model can do it (see [canTranslate]); the others ignore it.
     */
    suspend fun transcribe(
        samples: FloatArray, threads: Int, language: String, translateTo: String? = null,
    ): String

    /** Whether the loaded model can translate speech into another language. */
    val canTranslate: Boolean get() = false

    suspend fun release()
}

class WhisperEngine(private val context: WhisperContext) : SpeechEngine {
    override suspend fun transcribe(
        samples: FloatArray, threads: Int, language: String, translateTo: String?,
    ) = context.transcribeData(samples, false, threads, language)

    override suspend fun release() = context.release()
}
