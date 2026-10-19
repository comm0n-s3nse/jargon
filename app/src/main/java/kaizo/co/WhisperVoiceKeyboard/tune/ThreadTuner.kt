package kaizo.co.WhisperVoiceKeyboard.tune

import android.content.SharedPreferences
import java.io.File

/**
 * Which thread count whisper uses. More threads is not faster: on a phone with mixed cores,
 * using all of them lets a slow or busy core hold up every pass (measured on a OnePlus 15:
 * 8 threads took up to 3x longer than 6). So the keyboard times itself once and keeps the
 * fastest setting.
 */
object Threads {
    const val TUNED = "tuned_threads"
    const val TUNED_MODEL = "tuned_model"

    // set while a tune is running; still set at the next start means the tune never finished
    const val TUNING_STARTED = "tuning_started"

    // used until the first tune has run
    private const val UNTUNED_DEFAULT = 4

    private var fastCoresCache = 0

    /**
     * How many cores are worth using: the ones near the fastest core's speed. The efficiency
     * cores of a big.LITTLE chip are left out, because a pass waits for its slowest thread
     * (on a ThinkPhone, 6 to 8 threads ran 5 to 50 times slower than 4). Needs no permission;
     * if the speeds cannot be read, half the cores is the cautious guess.
     */
    fun fastCores(totalCores: Int): Int {
        if (fastCoresCache > 0) return fastCoresCache
        val speeds = (0 until totalCores).mapNotNull { cpu ->
            try {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toInt()
            } catch (e: Exception) {
                null
            }
        }
        val fast = if (speeds.size == totalCores) fastCoreCount(speeds) else maxOf(2, totalCores / 2)
        fastCoresCache = fast.coerceIn(1, totalCores)
        return fastCoresCache
    }

    /** What to use when there is no tuned value, or tuning could not finish. */
    fun safeDefault(totalCores: Int): Int = minOf(UNTUNED_DEFAULT, fastCores(totalCores))

    // an explicit choice in settings wins, then the tuned value, then a safe guess
    fun effective(prefs: SharedPreferences, userKey: String, cores: Int): Int {
        if (prefs.contains(userKey)) return prefs.getInt(userKey, cores).coerceIn(1, cores)
        val tuned = prefs.getInt(TUNED, 0)
        return (if (tuned > 0) tuned else safeDefault(cores)).coerceIn(1, cores)
    }

    fun needsTuning(prefs: SharedPreferences, userKey: String, model: String): Boolean =
        !prefs.contains(userKey) &&
            (prefs.getInt(TUNED, 0) <= 0 || prefs.getString(TUNED_MODEL, null) != model)
}

/** Cores whose top speed (kHz) is within 70% of the fastest core's. */
fun fastCoreCount(maxFreqsKhz: List<Int>): Int {
    val top = maxFreqsKhz.maxOrNull() ?: return 0
    return maxFreqsKhz.count { it >= top * 0.7 }
}

/**
 * Times a few thread counts and returns a good one. Tries 2, 4, 6, 8 and all cores (up to
 * [cores], which should be the fast cores), then the two neighbors of the winner. [timeMs]
 * runs one trial at that thread count and returns how long it took. Anything within 10% of
 * the fastest counts as equally fast, and the fewest threads among those wins: a smaller
 * thread count holds up better when other apps compete for the cores, and draws less power.
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
    val fastest = results.getValue(results.entries.minByOrNull { it.value }!!.key)
    return results.entries.filter { it.value <= fastest * 1.10 }.minOf { it.key }
}
