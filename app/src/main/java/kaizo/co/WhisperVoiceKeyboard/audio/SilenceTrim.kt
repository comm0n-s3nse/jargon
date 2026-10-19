package kaizo.co.WhisperVoiceKeyboard.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val FRAME = 16000 / 50 // 20 ms at 16 kHz
private const val MIN_SPEECH_FRAMES = 3 // 60 ms of sound before it counts as speech
private const val LEAD_PAD_FRAMES = 12 // keep 240 ms before the first speech
private const val TAIL_PAD_FRAMES = 20 // and 400 ms after the last
private const val ABSOLUTE_FLOOR = 0.004f // quieter than this is never speech
private const val CLEARLY_LOUD = 0.02f // louder than this counts as sound even without a quiet part
private const val SUSTAINED_FRAMES = 10 // ...if there is at least 200 ms of it

/**
 * The part of [samples] (16 kHz mono floats) from just before the first speech to just after
 * the last, or null if nothing in it sounds like speech. Whisper invents text ("Thank you.",
 * repeated phrases) from silence, so it should not be handed any.
 *
 * Speech is whatever is well above the recording's own noise floor, so a noisy room does not
 * count as one long utterance.
 */
fun trimSilence(samples: FloatArray): FloatArray? {
    val frames = samples.size / FRAME
    if (frames < MIN_SPEECH_FRAMES) return null

    val rms = FloatArray(frames) { f ->
        var sum = 0f
        for (i in f * FRAME until (f + 1) * FRAME) sum += samples[i] * samples[i]
        sqrt(sum / FRAME)
    }
    val noiseFloor = rms.sortedArray()[frames / 10]
    val threshold = max(ABSOLUTE_FLOOR, noiseFloor * 3f)

    fun loudRun(end: Int) = (end - MIN_SPEECH_FRAMES + 1..end).all { it >= 0 && rms[it] > threshold }
    val lastFrameOfFirstRun = (MIN_SPEECH_FRAMES - 1 until frames).firstOrNull { loudRun(it) }
    val lastFrame = (frames - 1 downTo MIN_SPEECH_FRAMES - 1).firstOrNull { loudRun(it) }
    if (lastFrameOfFirstRun == null || lastFrame == null) {
        // nothing stands out: steady loud sound (a noisy room) is kept, a click is not
        return if (rms.count { it >= CLEARLY_LOUD } >= SUSTAINED_FRAMES) samples else null
    }

    val firstFrame = lastFrameOfFirstRun - MIN_SPEECH_FRAMES + 1
    val start = max(0, firstFrame - LEAD_PAD_FRAMES) * FRAME
    val endFrame = lastFrame + 1 + TAIL_PAD_FRAMES
    val end = if (endFrame >= frames) samples.size else min(samples.size, endFrame * FRAME)
    return samples.copyOfRange(start, end)
}
