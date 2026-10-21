package kaizo.co.WhisperVoiceKeyboard.engine

import com.transcribecpp.TranscribeContext

/**
 * transcribe.cpp: the third engine, for the model families it brings (Canary, which can translate
 * speech, and others). Models that cannot detect the spoken language are told English when the
 * setting says "auto", rather than failing.
 */
class TranscribeEngine(private val context: TranscribeContext) : SpeechEngine {
    override val canTranslate get() = context.capabilities.canTranslate
    override val canDetectLanguage get() = context.capabilities.canDetectLanguage
    override val languages get() = context.capabilities.languages

    override suspend fun transcribe(
        samples: FloatArray, threads: Int, language: String, translateTo: String?, vocabulary: List<String>,
    ): String {
        val caps = context.capabilities
        val spoken = if (language == "auto" && !caps.canDetectLanguage) "en" else language
        // translating into the language already spoken is just transcription
        val target = translateTo?.takeIf { caps.canTranslate && it.isNotEmpty() && it != spoken }
        return context.transcribe(samples, threads, spoken, target, vocabulary)
    }

    override suspend fun release() = context.release()
}
