package kaizo.co.WhisperVoiceKeyboard

import android.content.SharedPreferences
import kaizo.co.WhisperVoiceKeyboard.tune.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Just enough of Android's SharedPreferences to run the tuning logic on the JVM. */
private class FakePrefs : SharedPreferences {
    val map = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String, def: String?) = map[key] as String? ?: def
    override fun getStringSet(key: String, def: MutableSet<String>?) = def
    override fun getInt(key: String, def: Int) = map[key] as Int? ?: def
    override fun getLong(key: String, def: Long) = map[key] as Long? ?: def
    override fun getFloat(key: String, def: Float) = map[key] as Float? ?: def
    override fun getBoolean(key: String, def: Boolean) = map[key] as Boolean? ?: def
    override fun contains(key: String) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        val removed = mutableSetOf<String>()
        override fun putString(k: String, v: String?) = apply { pending[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { pending[k] = v }
        override fun putInt(k: String, v: Int) = apply { pending[k] = v }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v }
        override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
        override fun remove(k: String) = apply { removed += k }
        override fun clear() = apply { removed += map.keys }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            removed.forEach { map.remove(it) }
            map.putAll(pending)
        }
    }
}

class PerModelTuningTest {
    private val userKey = "num_threads"
    private val tiny = "asset:ggml-tiny.en.bin"
    private val fluid = "file:fluid-speech-mini-q2k-q4k.gguf"

    @Test
    fun aModelIsTunedOnceNoMatterHowOftenYouSwitch() {
        val prefs = FakePrefs()
        assertTrue(Threads.needsTuning(prefs, userKey, tiny))
        Threads.saveTuned(prefs.edit(), tiny, 6).apply()
        assertTrue(Threads.needsTuning(prefs, userKey, fluid)) // a different model still needs it
        Threads.saveTuned(prefs.edit(), fluid, 4).apply()

        // back and forth: neither needs tuning again, and each keeps its own number
        assertFalse(Threads.needsTuning(prefs, userKey, tiny))
        assertFalse(Threads.needsTuning(prefs, userKey, fluid))
        assertEquals(6, Threads.tuned(prefs, tiny))
        assertEquals(4, Threads.tuned(prefs, fluid))
    }

    @Test
    fun effectiveUsesTheLoadedModelsValue() {
        val prefs = FakePrefs()
        Threads.saveTuned(prefs.edit(), tiny, 6).apply()
        Threads.saveTuned(prefs.edit(), fluid, 3).apply()
        assertEquals(6, Threads.effective(prefs, userKey, 8, tiny))
        assertEquals(3, Threads.effective(prefs, userKey, 8, fluid))
    }

    @Test
    fun aValueSetByHandWinsAndSkipsTuning() {
        val prefs = FakePrefs()
        prefs.edit().putInt(userKey, 2).apply()
        assertFalse(Threads.needsTuning(prefs, userKey, tiny))
        assertEquals(2, Threads.effective(prefs, userKey, 8, tiny))
    }

    @Test
    fun forgettingClearsEveryModelsValue() {
        val prefs = FakePrefs()
        Threads.saveTuned(prefs.edit(), tiny, 6).apply()
        Threads.saveTuned(prefs.edit(), fluid, 4).apply()
        prefs.edit().putInt(userKey, 5).apply() // not a tuned value; must survive
        Threads.forgetTuned(prefs, prefs.edit()).apply()
        assertTrue(prefs.contains(userKey))
        assertEquals(0, Threads.tuned(prefs, tiny))
        assertEquals(0, Threads.tuned(prefs, fluid))
    }

    @Test
    fun aValueFromAnOlderBuildStillCountsForTheModelItWasTunedWith() {
        val prefs = FakePrefs()
        prefs.edit().putInt("tuned_threads", 6).putString("tuned_model", tiny).apply()
        assertEquals(6, Threads.tuned(prefs, tiny))
        assertEquals(0, Threads.tuned(prefs, fluid)) // it was another model's number
    }
}
