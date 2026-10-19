package kaizo.co.WhisperVoiceKeyboard.models

import android.os.Build
import java.io.File

/** The instruction-set features listed on the "Features" line of /proc/cpuinfo. */
fun parseCpuFeatures(cpuinfo: String): Set<String> =
    cpuinfo.lineSequence()
        .firstOrNull { it.startsWith("Features") }
        ?.substringAfter(':')
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        .orEmpty()

object CpuFeatures {
    private val features: Set<String> by lazy {
        try {
            parseCpuFeatures(File("/proc/cpuinfo").readText())
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * The parakeet.cpp and transcribe.cpp engines (GGUF models) are built for dot-product ("asimddp") and half-precision ("asimdhp")
     * instructions. A phone without them would crash with an illegal-instruction error, so it
     * is not offered parakeet models; whisper runs everywhere.
     */
    fun supportsGguf(): Boolean =
        Build.SUPPORTED_ABIS[0] == "arm64-v8a" && "asimddp" in features && "asimdhp" in features
}
