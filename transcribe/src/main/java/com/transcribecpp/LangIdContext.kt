package com.transcribecpp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** What the language-ID model heard: a code such as "sl", how sure it was, and how much of its belief fell inside the allowed set. */
data class Detection(val code: String, val p: Float, val allowedMass: Float)

/** A loaded language-ID model (VoxLingua107 through transcribe.cpp). One call at a time. */
class LangIdContext private constructor(private var ptr: Long) {
    private val scope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    /** The most likely language of [samples] (16 kHz mono), among [allowed] when given. */
    suspend fun detect(samples: FloatArray, allowed: List<String>): Detection? = withContext(scope.coroutineContext) {
        require(ptr != 0L)
        val raw = LangIdLib.detect(ptr, samples, allowed.takeIf { it.isNotEmpty() }?.toTypedArray()) ?: return@withContext null
        val parts = raw.split(';')
        if (parts.size < 3) return@withContext null
        Detection(parts[0], parts[1].toFloatOrNull() ?: 0f, parts[2].toFloatOrNull() ?: 0f)
    }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            LangIdLib.free(ptr)
            ptr = 0L
        }
    }

    companion object {
        fun load(path: String): LangIdContext {
            val p = LangIdLib.load(path)
            if (p == 0L) throw RuntimeException("couldn't load the language detector: ${LangIdLib.lastError()}")
            return LangIdContext(p)
        }
    }
}

private object LangIdLib {
    init {
        System.loadLibrary("transcribe_jni")
    }

    external fun load(path: String): Long
    external fun lastError(): String
    external fun detect(ctx: Long, audio: FloatArray, allowed: Array<String>?): String?
    external fun free(ctx: Long)
}
