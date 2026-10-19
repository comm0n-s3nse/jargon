package kaizo.co.WhisperVoiceKeyboard.models

/**
 * A speech model the app recommends: what it is, what it costs to run, and where it comes from.
 * A model is recognized as installed by its file name, so one added by hand through the file
 * picker, with the same name, shows up here too.
 */
data class CatalogModel(
    val id: String,
    val name: String,
    val by: String,
    val file: String,
    val ramMb: Int, // memory while running, measured on a phone (about, and it grows with long audio)
    val summary: String, // one line for the list
    val about: String, // a sentence or two for the download screen
    val credit: String, // the credit its license asks for, shown before downloading
    val url: String? = null, // null: built in, or not downloadable yet
    val sha256: String? = null,
    val sizeBytes: Long = 0,
    val builtIn: Boolean = false,
    val note: String? = null,
) {
    val downloadable get() = url != null
    val needsParakeet get() = file.endsWith(".gguf")
}

private const val HF = "https://huggingface.co"

// RAM figures are peak resident memory measured on a OnePlus 15 for a 7.5 s clip, rounded; the
// download sizes and checksums are those of the files that were tested with this app.
val CATALOG = listOf(
    CatalogModel(
        id = "whisper-tiny", name = "Whisper Tiny", by = "OpenAI",
        file = "ggml-tiny.en.bin", ramMb = 250, builtIn = true,
        summary = "English only",
        about = "The built-in model: small and quick, English only.",
        credit = "Whisper by OpenAI, MIT license, converted for whisper.cpp.",
    ),
    CatalogModel(
        id = "fluid-speech-mini", name = "Fluid Speech Mini", by = "FluidVoice",
        file = "fluid-speech-mini-q2k-q4k.gguf", ramMb = 450,
        summary = "English, language auto-detect (beta)",
        about = "Quick on short dictation, with punctuation. Trained for English; it also follows " +
            "other languages, which its authors have not measured.",
        credit = "Fluid Speech Mini by FluidVoice, built on NVIDIA Parakeet TDT 0.6B v3. CC BY 4.0.",
        url = "$HF/FluidVoice-Labs/fluid-speech-mini-gguf/resolve/main/fluid-speech-mini-q2k-q4k.gguf",
        sha256 = "f0796bf40274cc49d3db58563c23cd024b52ca7e2ae4749ccf27af85841726b5",
        sizeBytes = 347_357_184,
        note = "Needs a phone from about 2019 or newer.",
    ),
    CatalogModel(
        id = "whisper-small", name = "Whisper Small", by = "OpenAI",
        file = "ggml-small-q5_1.bin", ramMb = 600,
        summary = "Multilingual, translation (beta)",
        about = "Understands many languages. Set Language to the one you want written, and speech " +
            "in another language comes out translated. Translation quality varies.",
        credit = "Whisper by OpenAI, MIT license, converted for whisper.cpp.",
        url = "$HF/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        sizeBytes = 190_085_487,
    ),
    CatalogModel(
        id = "canary-1b", name = "Canary 1B", by = "NVIDIA",
        file = "canary-1b-v2-Q4_K_M.gguf", ramMb = 1000,
        summary = "Speech translation, English and 24 European languages",
        about = "Coming soon. It needs a speech engine that is not in the app yet.",
        credit = "Canary-1B-v2 by NVIDIA. CC BY 4.0.",
    ),
)

fun catalogFor(fileName: String): CatalogModel? = CATALOG.firstOrNull { it.file == fileName }

/** "~250 MB RAM", "~1 GB RAM" */
fun ramLabel(mb: Int): String =
    if (mb >= 1000) "~${(mb / 100) / 10.0} GB RAM".replace(".0 GB", " GB") else "~$mb MB RAM"

/** "190 MB": download sizes use decimal megabytes, the way download pages do. */
fun downloadSizeLabel(bytes: Long): String = "${(bytes + 500_000) / 1_000_000} MB"
