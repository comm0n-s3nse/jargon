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
    val engine: ModelEngine = ModelEngine.WHISPER_CPP, // which engine runs this file
    val translates: Boolean = false, // listed under "Translation model": it writes in a language you choose
    val recommended: Boolean = false, // the one to get, for a phone with the room: said so in its row
) {
    val downloadable get() = url != null
    val needsGguf get() = engine != ModelEngine.WHISPER_CPP
}

private const val HF = "https://huggingface.co"

// peak memory of transcribe.cpp's CLI on a ThinkPhone, 7.5 s clip: 1237 MB (Q4_K_M, 4 threads)
private const val CANARY_RAM_MB = 1250
// peak resident memory of transcribe.cpp's CLI on a OnePlus 15, 7.5 s clip, Q8_0, 4 threads
private const val CANARY_180M_RAM_MB = 390

// RAM figures are peak resident memory measured on phones for a 7.5 s clip, rounded (see each model's
// note in KAIBOARD.md); the
// download sizes and checksums are those of the files that were tested with this app.
val CATALOG = listOf(
    CatalogModel(
        id = "whisper-tiny", name = "Whisper Tiny", by = "OpenAI",
        // 8-bit quantized: 44 MB instead of 78, a fifth faster, 30 MB less memory, same output on test speech
        file = "ggml-tiny.en-q8_0.bin", ramMb = 180, builtIn = true,
        summary = "English only",
        about = "Small and quick, English only. Built in when the app ships with a model; otherwise the first one to get.",
        credit = "Whisper by OpenAI, MIT license, converted for whisper.cpp.",
        // the same file scripts/fetch-model.sh bundles; a copy built without it downloads it here
        url = "$HF/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q8_0.bin",
        sha256 = "5bc2b3860aa151a4c6e7bb095e1fcce7cf12c7b020ca08dcec0c6d018bb7dd94",
        sizeBytes = 43_550_795,
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
        engine = ModelEngine.PARAKEET_CPP,
    ),
    CatalogModel(
        id = "canary-180m", name = "Canary 180M Flash", by = "NVIDIA",
        file = "canary-180m-flash-Q8_0.gguf", ramMb = CANARY_180M_RAM_MB,
        summary = "English and 4 European languages",
        about = "The small Canary: listens in English, German, Spanish or French and can translate " +
            "between them. On a 2025 phone it finished a minute of dictation half a second after " +
            "the stop, with cleaner text than Whisper Tiny. The one to get if your phone has the room.",
        recommended = true,
        credit = "Canary-180M-Flash by NVIDIA, CC BY 4.0, converted for transcribe.cpp by handy-computer.",
        url = "$HF/handy-computer/canary-180m-flash-gguf/resolve/main/canary-180m-flash-Q8_0.gguf",
        sha256 = "f942ab746040b531a29ab78ca3afc8dc9564efd8e4c942b5541e53644be9c408",
        sizeBytes = 218_447_648,
        engine = ModelEngine.TRANSCRIBE_CPP,
    ),
    CatalogModel(
        id = "whisper-small", name = "Whisper Small", by = "OpenAI",
        file = "ggml-small-q5_1.bin", ramMb = 600,
        summary = "99 languages, translation (beta); the slowest here",
        about = "Understands 99 languages. Set Language to the one you want written, and speech " +
            "in another language comes out translated. Translation quality varies, and it is the " +
            "slowest model here; Canary 180M Flash is quicker if your languages are among its four.",
        credit = "Whisper by OpenAI, MIT license, converted for whisper.cpp.",
        url = "$HF/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        sizeBytes = 190_085_487,
        translates = true,
    ),
    CatalogModel(
        id = "canary-1b", name = "Canary 1B", by = "NVIDIA",
        file = "canary-1b-v2-Q4_K_M.gguf", ramMb = CANARY_RAM_MB,
        summary = "Translates speech between 25 European languages (beta)",
        about = "Listens in 25 European languages, including Polish and Portuguese, and can translate " +
            "what it hears into another: set Translate to, below. Slower than the others, about " +
            "1.6 seconds for a sentence on a mid-range phone, and it needs a roomy phone.",
        credit = "Canary-1B-v2 by NVIDIA, CC BY 4.0, converted for transcribe.cpp by handy-computer.",
        url = "$HF/handy-computer/canary-1b-v2-gguf/resolve/main/canary-1b-v2-Q4_K_M.gguf",
        sha256 = "49e0a67e219bec95a254c2348460b6350e75a7ac6f93a131e48244b4c7cb53b9",
        sizeBytes = 735_476_448,
        engine = ModelEngine.TRANSCRIBE_CPP,
        translates = true,
    ),
)

fun catalogFor(fileName: String): CatalogModel? = CATALOG.firstOrNull { it.file == fileName }

/** "~250 MB RAM", "~1 GB RAM" */
fun ramLabel(mb: Int): String =
    if (mb >= 1000) "~${(mb / 100) / 10.0} GB RAM".replace(".0 GB", " GB") else "~$mb MB RAM"

/** "190 MB": download sizes use decimal megabytes, the way download pages do. */
fun downloadSizeLabel(bytes: Long): String = "${(bytes + 500_000) / 1_000_000} MB"
