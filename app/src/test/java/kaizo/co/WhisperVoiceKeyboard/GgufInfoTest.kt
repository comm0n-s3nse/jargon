package kaizo.co.WhisperVoiceKeyboard

import kaizo.co.WhisperVoiceKeyboard.models.Gguf
import kaizo.co.WhisperVoiceKeyboard.models.ModelEngine
import kaizo.co.WhisperVoiceKeyboard.models.engineFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds GGUF headers shaped like the real ones from each engine's files. */
class GgufInfoTest {
    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun le64(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
    private fun str(s: String) = s.toByteArray().let { le64(it.size.toLong()) + it }
    private fun kvString(k: String, v: String) = str(k) + le32(8) + str(v)
    private fun kvU32(k: String, v: Int) = str(k) + le32(4) + le32(v)
    private fun kvF32(k: String, v: Float) = str(k) + le32(6) + le32(java.lang.Float.floatToIntBits(v))
    private fun kvStrings(k: String, items: List<String>) =
        str(k) + le32(9) + le32(8) + le64(items.size.toLong()) + items.fold(ByteArray(0)) { a, s -> a + str(s) }
    private fun kvU32s(k: String, n: Int) =
        str(k) + le32(9) + le32(4) + le64(n.toLong()) + ByteArray(n * 4)

    private fun gguf(vararg kvs: ByteArray) =
        "GGUF".toByteArray() + le32(3) + le64(0) + le64(kvs.size.toLong()) + kvs.fold(ByteArray(0)) { a, b -> a + b }

    private fun read(bytes: ByteArray) = Gguf.read(ByteArrayInputStream(bytes))

    // shaped like handy-computer's canary-1b-v2 file: arrays of strings and numbers between the keys we want
    private val canary = gguf(
        kvString("general.architecture", "canary"),
        kvString("general.name", "Canary 1B v2"),
        kvString("general.license", "cc-by-4.0"),
        kvStrings("general.languages", listOf("bg", "hr", "cs")),
        kvU32s("tokenizer.token_type", 500),
        kvF32("canary.some.float", 1.5f),
    )

    // shaped like Fluid Speech Mini, which parakeet.cpp reads
    private val fluid = gguf(
        kvString("general.architecture", "parakeet"),
        kvString("general.name", "fluid-speech-mini"),
        kvString("parakeet.arch", "tdt"),
        kvU32("parakeet.encoder.n_layers", 24),
    )

    // transcribe.cpp's own Parakeet: the same general.architecture, but no parakeet.* keys
    private val transcribeParakeet = gguf(
        kvString("general.architecture", "parakeet"),
        kvString("general.name", "Parakeet TDT 0.6B v3"),
        kvStrings("general.languages", listOf("en", "pt", "pl")),
    )

    @Test
    fun readsTheKeysWeNeedPastArraysAndOtherTypes() {
        val info = read(canary)!!
        assertEquals("canary", info.architecture)
        assertEquals("cc-by-4.0", info.license)
        assertFalse(info.isParakeetCpp)
    }

    @Test
    fun readsTheLanguagesAModelSupports() {
        assertEquals(listOf("bg", "hr", "cs"), read(canary)!!.languages)
        assertEquals(listOf("en", "pt", "pl"), read(transcribeParakeet)!!.languages)
        assertEquals(emptyList<String>(), read(fluid)!!.languages) // a file that does not list any
    }

    @Test
    fun parakeetCppFilesAreToldApartFromTranscribeCppsOwnParakeet() {
        assertTrue(read(fluid)!!.isParakeetCpp)
        assertFalse(read(transcribeParakeet)!!.isParakeetCpp)
        // same general.architecture, different engines
        assertEquals("parakeet", read(fluid)!!.architecture)
        assertEquals("parakeet", read(transcribeParakeet)!!.architecture)
    }

    @Test
    fun theRuleChoosesTheEngine() {
        assertEquals(ModelEngine.TRANSCRIBE_CPP, engineFor("canary-1b-v2-Q4_K_M.gguf", read(canary)))
        assertEquals(ModelEngine.PARAKEET_CPP, engineFor("fluid-speech-mini-q2k-q4k.gguf", read(fluid)))
        assertEquals(ModelEngine.TRANSCRIBE_CPP, engineFor("parakeet-tdt-0.6b-v3-Q4_K_M.gguf", read(transcribeParakeet)))
        // whisper.cpp's files are .bin, and never looked inside
        assertEquals(ModelEngine.WHISPER_CPP, engineFor("ggml-small-q5_1.bin", null))
    }

    @Test
    fun anUnreadableGgufIsNotGuessedAt() {
        assertNull(engineFor("mystery.gguf", null))
        assertNull(read("not a gguf at all, just text".toByteArray()))
        assertNull(read(canary.copyOf(40))) // header cut off partway
    }

    @Test
    fun aCorruptKeyCountIsRejectedNotLoopedOver() {
        val bad = "GGUF".toByteArray() + le32(3) + le64(0) + le64(Long.MAX_VALUE)
        assertNull(read(bad))
        val noArch = gguf(kvString("general.name", "x"))
        assertNotNull(read(noArch))
        assertNull(read(noArch)!!.architecture)
        assertNull(engineFor("x.gguf", read(noArch))) // a GGUF that does not say what it is
    }
}
