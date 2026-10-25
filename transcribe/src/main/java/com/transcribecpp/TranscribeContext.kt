package com.transcribecpp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** What a loaded model can do, as reported by transcribe.cpp. */
data class TranscribeCapabilities(
    val canTranslate: Boolean,
    val canDetectLanguage: Boolean,
    val languages: List<String>,
)

/** A model loaded by transcribe.cpp (a GGUF such as Canary). Calls are serialized on one thread. */
class TranscribeContext private constructor(
    private var ptr: Long,
    val capabilities: TranscribeCapabilities,
) {
    private val scope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    /**
     * [language] is the language spoken, or "auto" to detect it where the model can. When
     * [targetLanguage] is set the speech is translated into it.
     */
    suspend fun transcribe(
        samples: FloatArray, threads: Int, language: String, targetLanguage: String?,
        vocabulary: List<String> = emptyList(), // custom terms, for models that take them
        digits: Boolean = true, // numbers as figures (ITN), for families with the switch; others ignore it
    ): String = withContext(scope.coroutineContext) {
        require(ptr != 0L)
        TranscribeLib.transcribe(ptr, threads, samples, language, targetLanguage, vocabulary.toTypedArray(), digits)
            ?: throw RuntimeException("transcription failed: ${TranscribeLib.lastError()}")
    }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            TranscribeLib.free(ptr)
            ptr = 0
        }
    }

    companion object {
        fun load(path: String, threads: Int): TranscribeContext {
            val ptr = TranscribeLib.load(path, threads)
            if (ptr == 0L) throw RuntimeException("Couldn't load $path: ${TranscribeLib.lastError()}")
            return TranscribeContext(ptr, parseCapabilities(TranscribeLib.capabilities(ptr)))
        }

        // "translate=1;detect=0;languages=en,de,es"
        internal fun parseCapabilities(raw: String): TranscribeCapabilities {
            val fields = raw.split(';').mapNotNull {
                val i = it.indexOf('=')
                if (i < 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
            return TranscribeCapabilities(
                canTranslate = fields["translate"] == "1",
                canDetectLanguage = fields["detect"] == "1",
                languages = fields["languages"].orEmpty().split(',').filter { it.isNotEmpty() },
            )
        }
    }
}

private object TranscribeLib {
    init {
        System.loadLibrary("transcribe_jni")
    }

    external fun load(path: String, threads: Int): Long
    external fun lastError(): String
    external fun capabilities(ctx: Long): String
    external fun transcribe(
        ctx: Long, threads: Int, audio: FloatArray, language: String, targetLanguage: String?,
        vocabulary: Array<String>?, digits: Boolean,
    ): String?
    external fun free(ctx: Long)
}
