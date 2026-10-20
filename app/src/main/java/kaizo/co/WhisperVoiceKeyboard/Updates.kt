package kaizo.co.WhisperVoiceKeyboard

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub for the newest release and remembers the answer. The app never does this by itself
 * unless the person switched it on, and then only when settings open; About can also ask on demand. It only
 * reads the version and the release page's address; installing is the person's own step.
 */
object Updates {
    const val AUTO_KEY = "update_auto"
    const val TAG_KEY = "update_tag"
    const val URL_KEY = "update_url"
    const val AT_KEY = "update_checked_at"
    const val RELEASES = "https://github.com/comm0n-s3nse/jargon/releases"
    private const val LATEST = "https://api.github.com/repos/comm0n-s3nse/jargon/releases/latest"

    class Result(val tag: String, val url: String)

    /** Fetches the latest release and stores it; throws on any failure. */
    suspend fun check(prefs: SharedPreferences): Result = withContext(Dispatchers.IO) {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "jargon-keyboard")
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val result = Result(json.getString("tag_name"), json.optString("html_url", RELEASES))
            prefs.edit()
                .putString(TAG_KEY, result.tag)
                .putString(URL_KEY, result.url)
                .putLong(AT_KEY, System.currentTimeMillis())
                .apply()
            result
        } finally {
            conn.disconnect()
        }
    }

    /** Whether opening settings should ask now: whenever the switch is on. */
    fun due(prefs: SharedPreferences): Boolean = prefs.getBoolean(AUTO_KEY, false)

    /** The remembered newer release, if the last answer was newer than [installed]. */
    fun available(prefs: SharedPreferences, installed: String): Result? {
        val tag = prefs.getString(TAG_KEY, null) ?: return null
        if (!isNewer(tag, installed)) return null
        return Result(tag, prefs.getString(URL_KEY, null) ?: RELEASES)
    }

    /** "v1.2" is newer than "1.1.3"; anything that is not numbers and dots compares as 0. */
    fun isNewer(tag: String, installed: String): Boolean {
        val a = parts(tag)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
        .split('.').map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
}
