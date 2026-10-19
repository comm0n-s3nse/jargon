package kaizo.co.WhisperVoiceKeyboard.models

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import com.parakeetcpp.ParakeetContext
import com.whispercpp.whisper.WhisperContext
import kaizo.co.WhisperVoiceKeyboard.engine.ParakeetEngine
import kaizo.co.WhisperVoiceKeyboard.engine.SpeechEngine
import kaizo.co.WhisperVoiceKeyboard.engine.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val ACTIVE_MODEL_KEY = "active_model"
const val LANGUAGE_KEY = "language"

// what the speech language setting offers, "auto" first
val LANGUAGES = listOf(
    "auto" to "Auto-detect", "en" to "English", "pt" to "Portuguese", "es" to "Spanish",
    "fr" to "French", "de" to "German", "it" to "Italian", "nl" to "Dutch", "pl" to "Polish",
    "sv" to "Swedish", "tr" to "Turkish", "ru" to "Russian", "uk" to "Ukrainian",
    "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "ar" to "Arabic", "hi" to "Hindi",
)

enum class ModelFormat { WHISPER_GGML, GGUF, UNKNOWN }

/** What a model file is, from its first four bytes. */
fun detectFormat(head: ByteArray): ModelFormat = when {
    head.size < 4 -> ModelFormat.UNKNOWN
    // whisper.cpp files start with the 32-bit magic 0x67676d6c ("ggml"), little-endian
    head[0] == 0x6c.toByte() && head[1] == 0x6d.toByte() &&
        head[2] == 0x67.toByte() && head[3] == 0x67.toByte() -> ModelFormat.WHISPER_GGML
    head[0] == 'G'.code.toByte() && head[1] == 'G'.code.toByte() &&
        head[2] == 'U'.code.toByte() && head[3] == 'F'.code.toByte() -> ModelFormat.GGUF
    else -> ModelFormat.UNKNOWN
}

/** A file name safe to create in the app's model folder, whatever the picker called it. */
fun safeModelFileName(display: String?): String {
    val base = (display ?: "model").substringAfterLast('/').substringAfterLast('\\')
    val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_').ifEmpty { "model" }
    return if (cleaned.endsWith(".bin", ignoreCase = true)) cleaned else "$cleaned.bin"
}

/** "ggml-small-q5_1.bin" -> "small-q5_1" */
fun labelFor(fileName: String): String =
    fileName.removeSuffix(".bin").removeSuffix(".gguf").removePrefix("ggml-")

/** Gives [name] the extension its format uses: .gguf for GGUF, .bin for whisper's ggml. */
fun withExtensionFor(name: String, format: ModelFormat): String {
    val stem = name.removeSuffix(".bin").removeSuffix(".gguf")
    return stem + if (format == ModelFormat.GGUF) ".gguf" else ".bin"
}

/** whisper's English-only models are named like "tiny.en" or "small.en-q5_1". */
fun isEnglishOnly(fileName: String): Boolean = Regex("""\.en([-_.]|$)""").containsMatchIn(labelFor(fileName))

/** The language to ask the model for: English-only models ignore the setting. */
fun languageFor(fileName: String, setting: String): String =
    if (isEnglishOnly(fileName)) "en" else setting

data class ModelEntry(
    val id: String, // "asset:<file>" for the bundled model, "file:<name>" for an imported one
    val fileName: String,
    val sizeBytes: Long,
    val builtIn: Boolean,
) {
    val label get() = labelFor(fileName)
    val format get() = if (fileName.endsWith(".gguf", ignoreCase = true)) ModelFormat.GGUF else ModelFormat.WHISPER_GGML
    val englishOnly get() = format == ModelFormat.WHISPER_GGML && isEnglishOnly(fileName)
}

class ModelImportException(message: String) : Exception(message)

/** The models the keyboard can run: the one bundled in the APK plus any the user adds. */
class ModelStore(private val context: Context) {
    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    fun list(): List<ModelEntry> {
        val bundled = (context.assets.list("models") ?: emptyArray())
            .filter { it.endsWith(".bin") || it.endsWith(".gguf") }
            .map { ModelEntry("asset:$it", it, assetSize(it), builtIn = true) }
        val added = dir.listFiles { f -> f.isFile && (f.name.endsWith(".bin") || f.name.endsWith(".gguf")) }
            .orEmpty().sortedBy { it.name }
            .map { ModelEntry("file:${it.name}", it.name, it.length(), builtIn = false) }
        return bundled + added
    }

    /** Whether this phone can run [entry]: parakeet needs instructions older chips lack. */
    fun runnable(entry: ModelEntry): Boolean =
        entry.format != ModelFormat.GGUF || CpuFeatures.supportsParakeet()

    /** The selected model, falling back to the bundled one if it was removed or cannot run here. */
    fun active(prefs: SharedPreferences): ModelEntry {
        val all = list()
        val saved = prefs.getString(ACTIVE_MODEL_KEY, null)
        return all.firstOrNull { it.id == saved && runnable(it) } ?: all.first()
    }

    fun open(entry: ModelEntry): SpeechEngine =
        if (entry.format == ModelFormat.GGUF) {
            ParakeetEngine(ParakeetContext.load(File(dir, entry.fileName).absolutePath))
        } else {
            WhisperEngine(openWhisper(entry))
        }

    fun openWhisper(entry: ModelEntry): WhisperContext =
        if (entry.builtIn) {
            WhisperContext.createContextFromAsset(context.assets, "models/${entry.fileName}")
        } else {
            WhisperContext.createContextFromFile(File(dir, entry.fileName).absolutePath)
        }

    fun remove(entry: ModelEntry) {
        if (!entry.builtIn) File(dir, entry.fileName).delete()
    }

    /** Copies a model the user picked into the app, after checking it is one we can run. */
    suspend fun import(uri: Uri, onProgress: (copied: Long, total: Long) -> Unit): ModelEntry =
        withContext(Dispatchers.IO) {
            var display: String? = null
            var size = -1L
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    display = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            val input = context.contentResolver.openInputStream(uri)
                ?: throw ModelImportException("Couldn't open that file.")
            input.use { stream ->
                val head = ByteArray(4)
                var read = 0
                while (read < 4) {
                    val n = stream.read(head, read, 4 - read)
                    if (n < 0) break
                    read += n
                }
                val format = detectFormat(head)
                if (format == ModelFormat.GGUF && !CpuFeatures.supportsParakeet()) {
                    throw ModelImportException(
                        "This phone's processor is too old to run parakeet models. Whisper models work on every phone."
                    )
                }
                if (format == ModelFormat.UNKNOWN) {
                    throw ModelImportException(
                        "That isn't a model file this keyboard can run (a whisper.cpp .bin or a parakeet .gguf)."
                    )
                }
                if (size > 0 && dir.usableSpace < size + 50L * 1024 * 1024) {
                    throw ModelImportException("Not enough free storage for that model.")
                }
                val stem = withExtensionFor(safeModelFileName(display), format)
                var name = stem
                var n = 2
                while (File(dir, name).exists()) {
                    name = withExtensionFor(stem.removeSuffix(".bin").removeSuffix(".gguf") + "-$n", format)
                    n++
                }
                val part = File(dir, "$name.part")
                try {
                    part.outputStream().use { out ->
                        out.write(head, 0, read)
                        var copied = read.toLong()
                        val buffer = ByteArray(1 shl 20)
                        while (true) {
                            val len = stream.read(buffer)
                            if (len < 0) break
                            out.write(buffer, 0, len)
                            copied += len
                            onProgress(copied, size)
                        }
                    }
                    val target = File(dir, name)
                    if (!part.renameTo(target)) throw ModelImportException("Couldn't save the model.")
                    ModelEntry("file:$name", name, target.length(), builtIn = false)
                } finally {
                    part.delete()
                }
            }
        }

    private fun assetSize(name: String): Long = try {
        context.assets.open("models/$name").use { it.available().toLong() }
    } catch (e: Exception) {
        0L
    }
}
