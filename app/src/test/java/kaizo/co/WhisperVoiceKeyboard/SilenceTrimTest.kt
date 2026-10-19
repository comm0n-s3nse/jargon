package kaizo.co.WhisperVoiceKeyboard

import kaizo.co.WhisperVoiceKeyboard.audio.trimSilence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SilenceTrimTest {
    private val rate = 16000
    private val rng = Random(1)

    private fun noise(seconds: Double, level: Float) =
        FloatArray((seconds * rate).toInt()) { (rng.nextFloat() - 0.5f) * 2 * level }

    // a voice-like tone with a little noise on top
    private fun speech(seconds: Double, level: Float) =
        FloatArray((seconds * rate).toInt()) { i ->
            level * sin(2 * PI * 220 * i / rate).toFloat() + (rng.nextFloat() - 0.5f) * 0.002f
        }

    private fun seconds(a: FloatArray) = a.size.toDouble() / rate

    @Test
    fun silenceOnlyIsNull() {
        assertNull(trimSilence(noise(2.0, 0.002f)))
    }

    @Test
    fun tooShortIsNull() {
        assertNull(trimSilence(FloatArray(300)))
    }

    @Test
    fun trimsLeadingAndTrailingSilence() {
        val input = noise(1.5, 0.002f) + speech(1.0, 0.1f) + noise(2.0, 0.002f)
        val out = trimSilence(input)
        assertNotNull(out)
        // 1.0 s of speech plus the 0.24 s lead and 0.4 s tail pads, within a frame or two
        assertEquals(1.64, seconds(out!!), 0.08)
    }

    @Test
    fun keepsPausesInsideTheUtterance() {
        val input = noise(1.0, 0.002f) + speech(0.6, 0.1f) + noise(0.8, 0.002f) +
            speech(0.6, 0.1f) + noise(1.0, 0.002f)
        val out = trimSilence(input)!!
        assertEquals(0.6 + 0.8 + 0.6 + 0.24 + 0.4, seconds(out), 0.08)
    }

    @Test
    fun speechAtTheEdgesKeepsTheEdges() {
        val input = speech(1.0, 0.1f)
        val out = trimSilence(input)!!
        assertEquals(input.size, out.size)
    }

    @Test
    fun aBriefClickIsNotSpeech() {
        val click = FloatArray(160) { 0.5f } // 10 ms
        assertNull(trimSilence(noise(1.0, 0.002f) + click + noise(1.0, 0.002f)))
    }

    @Test
    fun steadyLoudNoiseIsKeptRatherThanDropped() {
        val out = trimSilence(noise(2.0, 0.1f))
        assertTrue(out != null)
    }
}
