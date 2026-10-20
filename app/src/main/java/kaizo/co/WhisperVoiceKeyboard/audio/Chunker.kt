package kaizo.co.WhisperVoiceKeyboard.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Cuts a recording into pieces as it arrives, so each piece can be transcribed while the person
 * is still talking and only the last one is left to do when they stop. A cut happens at a pause
 * of [PAUSE_MS] once a piece holds at least [MIN_CHUNK_MS] of audio, or, with no pause, at the
 * quietest moment near [MAX_CHUNK_MS]. Pieces are what the engine would be given anyway; the
 * text is joined afterwards, so nothing appears early.
 *
 * Fed from the recorder's thread; [flush] is called after it has stopped.
 */
class Chunker(private val onChunk: (FloatArray) -> Unit) {
    private var buf = FloatArray(RATE * 25)
    private var size = 0
    private var frameFill = 0 // samples of the frame being built up
    private var frameSum = 0f
    private var frames = 0 // whole frames in buf
    private var quietFrames = 0 // trailing frames below the threshold
    private var speechFrames = 0 // frames above it in this piece
    private val recentRms = FloatArray(MAX_CHUNK_MS / FRAME_MS + 1)
    // the room's noise level: the quietest tenth of the last few seconds, as trimSilence measures it,
    // so continuous speech (whose dips between syllables are the quiet tenth) never becomes "quiet"
    private val floorWindow = FloatArray(FLOOR_WINDOW_FRAMES)
    private val sorted = FloatArray(FLOOR_WINDOW_FRAMES)
    private var floorFill = 0

    fun add(samples: ShortArray, n: Int) {
        for (i in 0 until n) {
            val v = samples[i] / 32768f
            if (size == buf.size) buf = buf.copyOf(buf.size * 2)
            buf[size++] = v
            frameSum += v * v
            if (++frameFill == FRAME) {
                frameDone(sqrt(frameSum / FRAME))
                frameFill = 0
                frameSum = 0f
            }
        }
    }

    private fun frameDone(rms: Float) {
        floorWindow[frames % FLOOR_WINDOW_FRAMES] = rms
        if (floorFill < FLOOR_WINDOW_FRAMES) floorFill++
        System.arraycopy(floorWindow, 0, sorted, 0, floorFill)
        java.util.Arrays.sort(sorted, 0, floorFill)
        val floor = sorted[floorFill / 10]
        val threshold = max(ABSOLUTE_FLOOR, floor * 3f)
        recentRms[frames % recentRms.size] = rms
        frames++
        if (rms > threshold) {
            speechFrames++
            quietFrames = 0
        } else {
            quietFrames++
        }
        when {
            // a pause after enough speech: cut, keeping a little of the quiet as the piece's tail
            speechFrames >= MIN_SPEECH_FRAMES && frames * FRAME_MS >= MIN_CHUNK_MS && quietFrames >= PAUSE_FRAMES ->
                cut(frames - (quietFrames - TAIL_FRAMES))
            // no pause at all: cut at the quietest frame of the last few seconds
            frames * FRAME_MS >= MAX_CHUNK_MS -> {
                val window = min(frames, SEARCH_FRAMES)
                var best = frames - 1
                var bestRms = Float.MAX_VALUE
                for (f in frames - window until frames) {
                    val r = recentRms[f % recentRms.size]
                    if (r < bestRms) { bestRms = r; best = f }
                }
                cut(best + 1)
            }
        }
    }

    /** Hands out the first [atFrame] frames and keeps the rest as the start of the next piece. */
    private fun cut(atFrame: Int) {
        val at = atFrame.coerceIn(1, frames) * FRAME
        onChunk(buf.copyOfRange(0, at))
        drop(atFrame)
        speechFrames = 0
    }

    private fun drop(framesToDrop: Int) {
        val at = framesToDrop.coerceIn(0, frames) * FRAME
        System.arraycopy(buf, at, buf, 0, size - at)
        size -= at
        frames -= framesToDrop.coerceIn(0, frames)
        quietFrames = min(quietFrames, frames)
    }

    /** Whatever is left, after the recorder has stopped. */
    fun flush(): FloatArray {
        val out = buf.copyOfRange(0, size)
        size = 0
        frames = 0
        frameFill = 0
        frameSum = 0f
        quietFrames = 0
        speechFrames = 0
        return out
    }

    companion object {
        const val RATE = 16000
        const val FRAME_MS = 20
        const val FRAME = RATE * FRAME_MS / 1000
        const val PAUSE_MS = 600
        const val PAUSE_FRAMES = PAUSE_MS / FRAME_MS
        const val MIN_CHUNK_MS = 3000
        const val MIN_SPEECH_FRAMES = 50 // a second of actual speech before a pause may cut
        const val MAX_CHUNK_MS = 20_000
        const val SEARCH_FRAMES = 3000 / FRAME_MS // where to look for the quietest frame when forced
        const val TAIL_FRAMES = 200 / FRAME_MS // quiet kept at the end of a piece
        const val FLOOR_WINDOW_FRAMES = 5000 / FRAME_MS
        const val ABSOLUTE_FLOOR = 0.004f
    }
}
