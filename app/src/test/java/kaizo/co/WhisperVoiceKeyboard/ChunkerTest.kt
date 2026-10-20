package kaizo.co.WhisperVoiceKeyboard

import kaizo.co.WhisperVoiceKeyboard.audio.Chunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class ChunkerTest {
    // speech-like: 150 ms bursts with 50 ms dips, since real speech always has quiet between syllables
    private fun tone(ms: Int, amp: Double = 0.3) = ShortArray(16 * ms) { i ->
        val a = if ((i / 16) % 200 < 150) amp else 0.01
        (sin(i * 0.3) * a * 32767).toInt().toShort()
    }
    private fun quiet(ms: Int) = ShortArray(16 * ms) { i -> ((i % 7 - 3) * 20).toShort() }

    private fun feed(c: Chunker, vararg parts: ShortArray) {
        for (p in parts) {
            var i = 0
            while (i < p.size) { // 40 ms reads, as the recorder delivers
                val n = minOf(640, p.size - i)
                c.add(p.copyOfRange(i, i + n), n)
                i += n
            }
        }
    }

    @Test
    fun cutsAtAPauseAfterSpeech() {
        val chunks = mutableListOf<FloatArray>()
        val c = Chunker { chunks += it }
        feed(c, quiet(300), tone(3000), quiet(1200), tone(2500))
        val tail = c.flush()
        assertEquals(1, chunks.size)
        // the first piece is the lead-in, three seconds of speech and a little of the pause
        val first = chunks[0].size / 16
        assertTrue("first piece $first ms", first in 3300..3700)
        assertTrue("tail ${tail.size / 16} ms", tail.size / 16 in 2500..3600)
    }

    @Test
    fun aShortDictationIsOnePiece() {
        val chunks = mutableListOf<FloatArray>()
        val c = Chunker { chunks += it }
        feed(c, quiet(200), tone(1500), quiet(400))
        val tail = c.flush()
        assertEquals(0, chunks.size)
        assertEquals(2100, tail.size / 16)
    }

    @Test
    fun cutsNearTwentySecondsWithoutAPause() {
        val chunks = mutableListOf<FloatArray>()
        val c = Chunker { chunks += it }
        feed(c, tone(25_000))
        c.flush()
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].size / 16 in 17_000..20_100)
    }

    @Test
    fun nothingIsEverDropped() {
        var total = 0
        val c = Chunker { total += it.size }
        feed(c, quiet(10_000), tone(30_000), quiet(1000), tone(4000))
        total += c.flush().size
        assertEquals(16 * 45_000, total)
    }

    @Test
    fun continuousSpeechStillCountsAsSpeech() {
        // a cut at the sentence pause, even though the speaker never stopped for the first 12 s
        val chunks = mutableListOf<FloatArray>()
        val c = Chunker { chunks += it }
        feed(c, tone(12_000), quiet(900), tone(3000))
        c.flush()
        assertEquals(1, chunks.size)
        assertTrue("first ${chunks[0].size / 16} ms", chunks[0].size / 16 in 12_000..12_600)
    }
}
