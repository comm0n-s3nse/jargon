package kaizo.co.WhisperVoiceKeyboard

import kaizo.co.WhisperVoiceKeyboard.models.CATALOG
import kaizo.co.WhisperVoiceKeyboard.models.ModelEngine
import kaizo.co.WhisperVoiceKeyboard.models.catalogFor
import kaizo.co.WhisperVoiceKeyboard.models.copyWithSha256
import kaizo.co.WhisperVoiceKeyboard.models.downloadSizeLabel
import kaizo.co.WhisperVoiceKeyboard.models.ramLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CatalogTest {
    @Test
    fun idsAndFileNamesAreUnique() {
        assertEquals(CATALOG.size, CATALOG.map { it.id }.toSet().size)
        assertEquals(CATALOG.size, CATALOG.map { it.file }.toSet().size)
    }

    @Test
    fun everyDownloadIsHttpsWithAPinnedChecksumAndSize() {
        val downloads = CATALOG.filter { it.downloadable }
        assertTrue(downloads.isNotEmpty())
        for (m in downloads) {
            val url = m.url!!
            assertTrue("${m.name} must use https", url.startsWith("https://"))
            assertTrue("${m.name} needs a 64-hex SHA-256", Regex("[0-9a-f]{64}").matches(m.sha256.orEmpty()))
            assertTrue("${m.name} needs a size", m.sizeBytes > 0)
            assertTrue("${m.name} URL should end in its file name", url.endsWith(m.file))
        }
    }

    @Test
    fun everyModelCanBeDownloadedAndOneIsBuiltIn() {
        // the built-in one is also downloadable: a copy that ships without it (F-Droid) gets it that way
        assertTrue(CATALOG.all { it.downloadable && it.sha256 != null && it.sizeBytes > 0 })
        assertEquals(1, CATALOG.count { it.builtIn })
        assertTrue(CATALOG.first { it.hidden }.file == kaizo.co.WhisperVoiceKeyboard.models.LANG_ID_FILE)
    }

    @Test
    fun eachModelNamesTheEngineThatRunsIt() {
        assertEquals(ModelEngine.WHISPER_CPP, CATALOG.first { it.id == "whisper-tiny" }.engine)
        assertEquals(ModelEngine.WHISPER_CPP, CATALOG.first { it.id == "whisper-small" }.engine)
        assertEquals(ModelEngine.PARAKEET_CPP, CATALOG.first { it.id == "fluid-speech-mini" }.engine)
        assertEquals(ModelEngine.TRANSCRIBE_CPP, CATALOG.first { it.id == "canary-1b" }.engine)
        // a .bin is whisper.cpp's and a .gguf never is
        for (m in CATALOG) assertEquals(m.file.endsWith(".bin"), m.engine == ModelEngine.WHISPER_CPP)
    }

    @Test
    fun aModelAddedByHandIsRecognizedByItsFileName() {
        assertEquals("Whisper Small", catalogFor("ggml-small-q5_1.bin")?.name)
        assertEquals("Fluid Speech Mini", catalogFor("fluid-speech-mini-q2k-q4k.gguf")?.name)
        assertNotNull(catalogFor("ggml-tiny.en-q8_0.bin"))
        assertNull(catalogFor("ggml-base-q5_1.bin"))
    }

    @Test
    fun ramAndSizeLabels() {
        assertEquals("~250 MB RAM", ramLabel(250))
        assertEquals("~1 GB RAM", ramLabel(1000))
        assertEquals("~1.5 GB RAM", ramLabel(1500))
        assertEquals("347 MB", downloadSizeLabel(347_357_184))
        assertEquals("190 MB", downloadSizeLabel(190_085_487))
    }

    @Test
    fun sha256OfKnownInput() {
        val out = ByteArrayOutputStream()
        val sha = copyWithSha256(ByteArrayInputStream("abc".toByteArray()), out)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha)
        assertEquals("abc", out.toString())
    }

    @Test
    fun checksumCoversDataLargerThanOneBufferAndReportsProgress() {
        val data = ByteArray(3 * (1 shl 20) + 123) { (it % 251).toByte() }
        val seen = mutableListOf<Long>()
        val out = ByteArrayOutputStream()
        val sha = copyWithSha256(ByteArrayInputStream(data), out) { seen += it }
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, sha)
        assertEquals(data.size, out.size())
        assertEquals(data.size.toLong(), seen.last())
        assertTrue(seen.zipWithNext().all { (a, b) -> b > a })
    }
}
