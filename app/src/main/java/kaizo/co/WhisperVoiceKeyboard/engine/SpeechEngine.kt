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
     * model can do it (see [canTranslate]); the others ignore it. [vocabulary] is the names and
     * terms the person taught the keyboard, for models that can be told.
     */
    suspend fun transcribe(
        samples: FloatArray, threads: Int, language: String, translateTo: String? = null,
        vocabulary: List<String> = emptyList(),
        digits: Boolean = true, // numbers as figures, for models with their own switch (ITN)
    ): String

    /** Whether the loaded model can translate speech into another language. */
    val canTranslate: Boolean get() = false

    /** Whether the model works out the spoken language by itself ("auto"); Canary cannot. */
    val canDetectLanguage: Boolean get() = true

    /** The languages the model knows, when it says; empty means any. */
    val languages: List<String> get() = emptyList()

    suspend fun release()
}

class WhisperEngine(private val context: WhisperContext) : SpeechEngine {
    override suspend fun transcribe(
        samples: FloatArray, threads: Int, language: String, translateTo: String?, vocabulary: List<String>, digits: Boolean,
    ) = context.transcribeData(samples, false, threads, language, whisperPrompt(vocabulary))

    override suspend fun release() = context.release()
}

/** Whisper takes its terms as a sentence it believes it already heard; a list reads best. */
fun whisperPrompt(vocabulary: List<String>): String? =
    vocabulary.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(", ", postfix = ".")
