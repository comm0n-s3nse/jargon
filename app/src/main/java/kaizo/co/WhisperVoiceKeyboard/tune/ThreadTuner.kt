package kaizo.co.WhisperVoiceKeyboard.tune

import android.content.SharedPreferences

/**
 * Which thread count whisper uses. More threads is not faster: on a phone with mixed cores,
 * using all of them lets a slow or busy core hold up every pass (measured on a OnePlus 15:
 * 8 threads took up to 3x longer than 6). So the keyboard times itself once and keeps the
 * fastest setting.
 */
object Threads {
    const val TUNED = "tuned_threads"
    const val TUNED_MODEL = "tuned_model"

    // used until the first tune has run
    private const val UNTUNED_DEFAULT = 4

    // an explicit choice in settings wins, then the tuned value, then a safe guess
    fun effective(prefs: SharedPreferences, userKey: String, cores: Int): Int {
        if (prefs.contains(userKey)) return prefs.getInt(userKey, cores).coerceIn(1, cores)
        val tuned = prefs.getInt(TUNED, 0)
        return (if (tuned > 0) tuned else UNTUNED_DEFAULT).coerceIn(1, cores)
    }

    fun needsTuning(prefs: SharedPreferences, userKey: String, model: String): Boolean =
        !prefs.contains(userKey) &&
            (prefs.getInt(TUNED, 0) <= 0 || prefs.getString(TUNED_MODEL, null) != model)
}

/**
 * Times a few thread counts and returns the fastest. Tries 2, 4, 6, 8 and all cores (up to
 * [cores]), then the two neighbors of the winner. [timeMs] runs one trial at that thread
 * count and returns how long it took. Ties go to fewer threads, which draw less power.
 */
suspend fun pickThreads(
    cores: Int,
    onProgress: (done: Int, total: Int) -> Unit,
    timeMs: suspend (threads: Int) -> Long,
): Int {
    val coarse = (listOf(2, 4, 6, 8) + cores).filter { it in 1..cores }.distinct().sorted()
    val total = coarse.size + 2
    val results = LinkedHashMap<Int, Long>()
    var done = 0

    for (t in coarse) {
        results[t] = timeMs(t)
        onProgress(++done, total)
    }
    val best = results.entries.minByOrNull { it.value }!!.key
    for (t in listOf(best - 1, best + 1)) {
        if (t in 1..cores && t !in results) results[t] = timeMs(t)
        onProgress(++done, total)
    }
    return results.entries.minByOrNull { it.value }!!.key
}
