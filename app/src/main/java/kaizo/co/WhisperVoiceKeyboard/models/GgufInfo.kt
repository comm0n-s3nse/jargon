package kaizo.co.WhisperVoiceKeyboard.models

import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Which of the app's three speech engines runs a model file.
 *
 * - whisper.cpp reads its own `.bin` files.
 * - parakeet.cpp and transcribe.cpp both read GGUF files, so the extension cannot tell them apart.
 */
enum class ModelEngine { WHISPER_CPP, PARAKEET_CPP, TRANSCRIBE_CPP }

/** The few things in a GGUF file's header that the app uses. */
data class GgufInfo(
    val architecture: String?,     // general.architecture: "canary", "whisper", "parakeet", ...
    val isParakeetCpp: Boolean,    // has parakeet.cpp's own "parakeet.arch" key
    val license: String?,          // general.license
    val languages: List<String> = emptyList(), // general.languages: what the model can listen to
)

/**
 * The rule, from comparing real files from both projects: parakeet.cpp's files carry extra keys
 * named `parakeet.*` (such as `parakeet.arch`). transcribe.cpp's files, even its own Parakeet ones,
 * do not, and name what they are in `general.architecture`. Anything else that is a GGUF goes to
 * transcribe.cpp, which says clearly if it does not know the architecture.
 */
fun engineFor(fileName: String, gguf: GgufInfo?): ModelEngine? = when {
    !fileName.endsWith(".gguf", ignoreCase = true) -> ModelEngine.WHISPER_CPP
    gguf == null -> null // unreadable or damaged header
    gguf.isParakeetCpp -> ModelEngine.PARAKEET_CPP
    gguf.architecture != null -> ModelEngine.TRANSCRIBE_CPP
    else -> null
}

/** Reads a GGUF file's header (a few kilobytes at the start), not the model itself. */
object Gguf {
    private const val MAX_KEYS = 100_000
    private const val MAX_STRING = 64L * 1024 * 1024

    fun read(file: File): GgufInfo? = try {
        file.inputStream().buffered(1 shl 16).use { read(it) }
    } catch (e: Exception) {
        null
    }

    /** Null for anything that is not a complete, well-formed GGUF header (including a truncated one). */
    fun read(input: InputStream): GgufInfo? = try {
        parse(input)
    } catch (e: Exception) {
        null
    }

    private fun parse(input: InputStream): GgufInfo? {
        val inp = DataInputStream(input)
        val magic = ByteArray(4)
        inp.readFully(magic)
        if (String(magic, Charsets.ISO_8859_1) != "GGUF") return null
        u32(inp) // version
        u64(inp) // tensor count
        val keys = u64(inp)
        if (keys < 0 || keys > MAX_KEYS) return null

        var architecture: String? = null
        var license: String? = null
        var languages: List<String> = emptyList()
        var parakeet = false
        for (i in 0 until keys) {
            val key = string(inp)
            val type = u32(inp).toInt()
            when {
                key == "general.architecture" && type == TYPE_STRING -> architecture = string(inp)
                key == "general.license" && type == TYPE_STRING -> license = string(inp)
                key == "general.languages" && type == TYPE_ARRAY -> languages = stringArray(inp)
                else -> skipValue(inp, type)
            }
            if (key == "parakeet.arch") parakeet = true
        }
        return GgufInfo(architecture, parakeet, license, languages)
    }

    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9

    // GGUF is little-endian
    private fun u32(inp: DataInputStream): Long {
        val b = ByteArray(4)
        inp.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    private fun u64(inp: DataInputStream): Long {
        val b = ByteArray(8)
        inp.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun string(inp: DataInputStream): String {
        val n = u64(inp)
        if (n < 0 || n > MAX_STRING) throw IllegalStateException("bad string length")
        val b = ByteArray(n.toInt())
        inp.readFully(b)
        return String(b, Charsets.UTF_8)
    }

    // an array of strings; anything else in an array is skipped and gives an empty list
    private fun stringArray(inp: DataInputStream): List<String> {
        val elem = u32(inp).toInt()
        val n = u64(inp)
        if (n < 0 || n > MAX_KEYS) throw IllegalStateException("bad array length")
        if (elem != TYPE_STRING) {
            for (i in 0 until n) skipValue(inp, elem)
            return emptyList()
        }
        return List(n.toInt()) { string(inp) }
    }

    private fun skip(inp: DataInputStream, n: Long) {
        var left = n
        while (left > 0) {
            val s = inp.skipBytes(minOf(left, Int.MAX_VALUE.toLong()).toInt())
            if (s <= 0) { inp.readByte(); left-- } else left -= s
        }
    }

    private fun fixedSize(type: Int): Int = when (type) {
        0, 1, 7 -> 1
        2, 3 -> 2
        4, 5, 6 -> 4
        10, 11, 12 -> 8
        else -> -1
    }

    private fun skipValue(inp: DataInputStream, type: Int) {
        val size = fixedSize(type)
        when {
            size > 0 -> skip(inp, size.toLong())
            type == TYPE_STRING -> skip(inp, u64(inp).also { if (it < 0 || it > MAX_STRING) throw IllegalStateException() })
            type == TYPE_ARRAY -> {
                val elem = u32(inp).toInt()
                val n = u64(inp)
                if (n < 0) throw IllegalStateException("bad array length")
                val elemSize = fixedSize(elem)
                if (elemSize > 0) skip(inp, n * elemSize) else for (i in 0 until n) skipValue(inp, elem)
            }
            else -> throw IllegalStateException("unknown GGUF value type $type")
        }
    }
}
