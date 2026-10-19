package kaizo.co.WhisperVoiceKeyboard.models

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import com.parakeetcpp.ParakeetContext
import com.transcribecpp.TranscribeContext
import kaizo.co.WhisperVoiceKeyboard.engine.TranscribeEngine
import com.whispercpp.whisper.WhisperContext
import kaizo.co.WhisperVoiceKeyboard.engine.ParakeetEngine
import kaizo.co.WhisperVoiceKeyboard.engine.SpeechEngine
import kaizo.co.WhisperVoiceKeyboard.engine.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val ACTIVE_MODEL_KEY = "active_model"
const val LANGUAGE_KEY = "language"
const val TRANSLATE_KEY = "translate_to" // empty: just transcribe
const val RECENT_TRANSLATE_KEY = "recent_translate_to" // comma list, newest first
val DEFAULT_RECENT_TRANSLATE = listOf("pt", "es", "fr", "de")
const val RECENT_TRANSLATE_MAX = 4

fun recentTranslateTargets(prefs: android.content.SharedPreferences): List<String> {
    val saved = prefs.getString(RECENT_TRANSLATE_KEY, null)?.split(",")?.filter { it.isNotEmpty() }
    return (saved ?: DEFAULT_RECENT_TRANSLATE).take(RECENT_TRANSLATE_MAX)
}

/** The language just chosen goes first in the quick list; the oldest falls off. */
fun rememberTranslateTarget(prefs: android.content.SharedPreferences, code: String) {
    if (code.isEmpty()) return
    val list = (listOf(code) + recentTranslateTargets(prefs).filter { it != code }).take(RECENT_TRANSLATE_MAX)
    prefs.edit().putString(RECENT_TRANSLATE_KEY, list.joinToString(",")).apply()
}

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
    val engine: ModelEngine = ModelEngine.WHISPER_CPP, // which of the three engines runs it
    val arch: String? = null, // a GGUF's general.architecture, e.g. "canary"
    val languages: List<String> = emptyList(), // languages the model supports, when its file says
) {
    val label get() = labelFor(fileName)
    val format get() = if (fileName.endsWith(".gguf", ignoreCase = true)) ModelFormat.GGUF else ModelFormat.WHISPER_GGML
    val englishOnly get() = engine == ModelEngine.WHISPER_CPP && isEnglishOnly(fileName)

    /** Canary can translate speech into other languages; the setting only appears for such models. */
    val canTranslate get() = arch == "canary"
}

class ModelImportException(message: String) : Exception(message)

/** The models the keyboard can run: the one bundled in the APK plus any the user adds. */
class ModelStore(private val context: Context) {
    private companion object {
        val headerCache = HashMap<String, GgufInfo?>()
    }

    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    fun list(): List<ModelEntry> {
        val bundled = (context.assets.list("models") ?: emptyArray())
            .filter { it.endsWith(".bin") || it.endsWith(".gguf") }
            .map { ModelEntry("asset:$it", it, assetSize(it), builtIn = true) }
        val added = dir.listFiles { f -> f.isFile && (f.name.endsWith(".bin") || f.name.endsWith(".gguf")) }
            .orEmpty().sortedBy { it.name }
            .mapNotNull { f ->
                val info = headerOf(f)
                val engine = engineFor(f.name, info) ?: return@mapNotNull null // not a model we can run
                ModelEntry(
                    "file:${f.name}", f.name, f.length(), builtIn = false, engine = engine,
                    arch = info?.architecture, languages = info?.languages.orEmpty()
                )
            }
        return bundled + added
    }

    // GGUF headers are read once per file, not on every redraw of the settings screen
    private fun headerOf(file: File): GgufInfo? {
        if (!file.name.endsWith(".gguf", ignoreCase = true)) return null
        val key = "${file.name}:${file.length()}:${file.lastModified()}"
        return headerCache.getOrPut(key) { Gguf.read(file) }
    }

    /** Whether this phone can run [entry]: the GGUF engines need instructions older chips lack. */
    fun runnable(entry: ModelEntry): Boolean =
        entry.engine == ModelEngine.WHISPER_CPP || CpuFeatures.supportsGguf()

    /** The selected model, falling back to the bundled one if it was removed or cannot run here. */
    /** The model to use, or null when none is installed (a copy that ships without one, before the first download). */
    fun active(prefs: SharedPreferences): ModelEntry? {
        val all = list()
        val saved = prefs.getString(ACTIVE_MODEL_KEY, null)
        return all.firstOrNull { it.id == saved && runnable(it) }
            ?: all.firstOrNull { it.builtIn }
            ?: all.firstOrNull { runnable(it) }
    }

    /** Loads [entry] with the engine its file needs. */
    fun open(entry: ModelEntry, threads: Int = 4): SpeechEngine = when (entry.engine) {
        ModelEngine.WHISPER_CPP -> WhisperEngine(openWhisper(entry))
        ModelEngine.PARAKEET_CPP ->
            ParakeetEngine(ParakeetContext.load(File(dir, entry.fileName).absolutePath))
        ModelEngine.TRANSCRIBE_CPP ->
            TranscribeEngine(TranscribeContext.load(File(dir, entry.fileName).absolutePath, threads))
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
                if (format == ModelFormat.GGUF && !CpuFeatures.supportsGguf()) {
                    throw ModelImportException(
                        "This phone's processor is too old to run these models. Whisper models work on every phone."
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
                    if (format == ModelFormat.GGUF && engineFor(name, Gguf.read(part)) == null) {
                        throw ModelImportException("That GGUF file's header could not be read, so it was not added.")
                    }
                    if (!part.renameTo(target)) throw ModelImportException("Couldn't save the model.")
                    val info = headerOf(target)
                    ModelEntry(
                        "file:$name", name, target.length(), builtIn = false,
                        engine = engineFor(name, info) ?: ModelEngine.WHISPER_CPP, arch = info?.architecture,
                        languages = info?.languages.orEmpty()
                    )
                } finally {
                    part.delete()
                }
            }
        }

    /**
     * Saves a model that arrived some other way (a download) into the model folder, checking its
     * SHA-256 against [sha256] as it is written. A file that does not match is deleted.
     */
    suspend fun installVerified(
        input: java.io.InputStream, fileName: String, sha256: String, totalBytes: Long,
        onProgress: (copied: Long, total: Long) -> Unit,
    ): ModelEntry = withContext(Dispatchers.IO) {
        if (totalBytes > 0 && dir.usableSpace < totalBytes + 50L * 1024 * 1024) {
            throw ModelImportException("Not enough free storage for that model.")
        }
        val part = File(dir, "$fileName.part")
        try {
            val actual = part.outputStream().use { out ->
                copyWithSha256(input, out) { copied -> onProgress(copied, totalBytes) }
            }
            if (!actual.equals(sha256, ignoreCase = true)) {
                throw ModelImportException(
                    "The download did not match the checksum this app expects, so it was thrown away."
                )
            }
            val target = File(dir, fileName)
            target.delete()
            if (!part.renameTo(target)) throw ModelImportException("Couldn't save the model.")
            val info = headerOf(target)
            ModelEntry(
                "file:$fileName", fileName, target.length(), builtIn = false,
                engine = engineFor(fileName, info) ?: ModelEngine.WHISPER_CPP, arch = info?.architecture,
                languages = info?.languages.orEmpty()
            )
        } finally {
            part.delete()
        }
    }

    private fun assetSize(name: String): Long = try {
        context.assets.open("models/$name").use { it.available().toLong() }
    } catch (e: Exception) {
        0L
    }
}
