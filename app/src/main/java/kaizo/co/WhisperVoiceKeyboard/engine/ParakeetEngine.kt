package kaizo.co.WhisperVoiceKeyboard.engine

import com.parakeetcpp.ParakeetContext

/** parakeet.cpp: GGUF models such as Fluid Speech Mini. */
class ParakeetEngine(private val context: ParakeetContext) : SpeechEngine {
    override suspend fun transcribe(
        samples: FloatArray, threads: Int, language: String, translateTo: String?,
    ) = context.transcribe(samples, threads, language)

    override suspend fun release() = context.release()
}
