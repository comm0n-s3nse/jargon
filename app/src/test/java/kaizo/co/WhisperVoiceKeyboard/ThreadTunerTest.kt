package kaizo.co.WhisperVoiceKeyboard

import kaizo.co.WhisperVoiceKeyboard.tune.pickThreads
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadTunerTest {
    private fun tune(cores: Int, ms: Map<Int, Long>, tried: MutableList<Int> = mutableListOf()) =
        runBlocking {
            pickThreads(cores, { _, _ -> }) { t ->
                tried += t
                ms.getValue(t)
            }
        }

    // the OnePlus 15 numbers: 6 is fastest and 8 is much slower
    private val onePlus = mapOf(2 to 740L, 3 to 700L, 4 to 600L, 5 to 520L, 6 to 470L, 7 to 600L, 8 to 780L)

    @Test
    fun picksTheFastestNotTheMost() {
        assertEquals(6, tune(8, onePlus))
    }

    @Test
    fun refinesBetweenCoarseSteps() {
        // fastest at 5, which the coarse pass (2, 4, 6, 8) does not try
        val ms = mapOf(2 to 900L, 3 to 800L, 4 to 600L, 5 to 450L, 6 to 500L, 7 to 700L, 8 to 900L)
        assertEquals(5, tune(8, ms))
    }

    @Test
    fun doesNotTryMoreThreadsThanCores() {
        val tried = mutableListOf<Int>()
        val ms = mapOf(1 to 900L, 2 to 500L, 3 to 450L, 4 to 600L)
        assertEquals(3, tune(4, ms, tried))
        assertTrue(tried.all { it in 1..4 })
    }

    @Test
    fun aSixCoreChipIncludesAllSixCores() {
        val tried = mutableListOf<Int>()
        val ms = (1..6).associateWith { 1000L / it }
        assertEquals(6, tune(6, ms, tried))
        assertTrue(6 in tried)
    }

    @Test
    fun tiesGoToFewerThreads() {
        val ms = (1..8).associateWith { 500L }
        assertEquals(2, tune(8, ms))
    }

    @Test
    fun measuresEachThreadCountOnce() {
        val tried = mutableListOf<Int>()
        tune(8, onePlus, tried)
        assertEquals(tried.distinct(), tried)
        assertFalse(tried.isEmpty())
    }

    @Test
    fun reportsProgressToTheTotal() {
        val seen = mutableListOf<Pair<Int, Int>>()
        runBlocking { pickThreads(8, { d, t -> seen += d to t }) { onePlus.getValue(it) } }
        assertEquals(seen.last().first, seen.last().second)
        assertEquals(seen.indices.map { it + 1 }, seen.map { it.first })
    }
}
