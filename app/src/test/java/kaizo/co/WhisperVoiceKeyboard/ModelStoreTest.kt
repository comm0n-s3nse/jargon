package kaizo.co.WhisperVoiceKeyboard

import kaizo.co.WhisperVoiceKeyboard.models.ModelFormat
import kaizo.co.WhisperVoiceKeyboard.models.detectFormat
import kaizo.co.WhisperVoiceKeyboard.models.isEnglishOnly
import kaizo.co.WhisperVoiceKeyboard.models.labelFor
import kaizo.co.WhisperVoiceKeyboard.models.languageFor
import kaizo.co.WhisperVoiceKeyboard.models.safeModelFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStoreTest {
    @Test
    fun recognizesWhisperGgmlGgufAndNeither() {
        assertEquals(ModelFormat.WHISPER_GGML, detectFormat(byteArrayOf(0x6c, 0x6d, 0x67, 0x67, 1, 2)))
        assertEquals(ModelFormat.GGUF, detectFormat("GGUF".toByteArray()))
        assertEquals(ModelFormat.UNKNOWN, detectFormat("PK\u0003\u0004".toByteArray()))
        assertEquals(ModelFormat.UNKNOWN, detectFormat(byteArrayOf(0x6c, 0x6d)))
    }

    @Test
    fun fileNamesCannotEscapeTheModelFolder() {
        assertEquals("passwd.bin", safeModelFileName("../../etc/passwd"))
        assertEquals("ggml-base-q5_1.bin", safeModelFileName("ggml-base-q5_1.bin"))
        assertEquals("my_model_v2.bin", safeModelFileName("my model v2"))
        assertEquals("model.bin", safeModelFileName(null))
        assertEquals("model.bin", safeModelFileName("...."))
    }

    @Test
    fun labelsDropTheBoilerplate() {
        assertEquals("small-q5_1", labelFor("ggml-small-q5_1.bin"))
        assertEquals("tiny.en", labelFor("ggml-tiny.en.bin"))
    }

    @Test
    fun spotsEnglishOnlyModels() {
        assertTrue(isEnglishOnly("ggml-tiny.en.bin"))
        assertTrue(isEnglishOnly("ggml-small.en-q5_1.bin"))
        assertFalse(isEnglishOnly("ggml-tiny.bin"))
        assertFalse(isEnglishOnly("ggml-base-q5_1.bin"))
        assertFalse(isEnglishOnly("ggml-large-v3-turbo.bin"))
    }

    @Test
    fun englishOnlyModelsIgnoreTheLanguageSetting() {
        assertEquals("en", languageFor("ggml-tiny.en.bin", "pt"))
        assertEquals("pt", languageFor("ggml-base-q5_1.bin", "pt"))
        assertEquals("auto", languageFor("ggml-tiny.bin", "auto"))
    }
}
