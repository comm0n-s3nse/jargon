package com.parakeetcpp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** A loaded parakeet.cpp model (a GGUF file). Calls are serialized on one thread. */
class ParakeetContext private constructor(private var ptr: Long) {
    private val scope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    /** [language] only matters to multilingual models; others ignore it. */
    suspend fun transcribe(samples: FloatArray, threads: Int, language: String): String =
        withContext(scope.coroutineContext) {
            require(ptr != 0L)
            ParakeetLib.transcribe(ptr, threads, samples, language)
                ?: throw RuntimeException("parakeet transcription failed")
        }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            ParakeetLib.free(ptr)
            ptr = 0
        }
    }

    companion object {
        fun load(path: String): ParakeetContext {
            val ptr = ParakeetLib.load(path)
            if (ptr == 0L) throw RuntimeException("Couldn't load $path: ${ParakeetLib.loadError()}")
            return ParakeetContext(ptr)
        }
    }
}

private object ParakeetLib {
    init {
        System.loadLibrary("parakeet_jni")
    }

    external fun load(path: String): Long
    external fun loadError(): String
    external fun free(ctx: Long)
    external fun transcribe(ctx: Long, threads: Int, audio: FloatArray, language: String): String?
}
