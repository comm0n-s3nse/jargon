package kaizo.co.WhisperVoiceKeyboard

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldTextTest {
    @Test
    fun dropsAPeriodOrCommaAfterAWord() {
        assertEquals("Maple Street", withoutClosingPunctuation("Maple Street."))
        assertEquals("pizza", withoutClosingPunctuation("pizza,"))
    }

    @Test
    fun keepsQuestionsAndSeveralSentences() {
        assertEquals("Where is it?", withoutClosingPunctuation("Where is it?"))
        assertEquals("Go. Now.", withoutClosingPunctuation("Go. Now."))
        assertEquals(".", withoutClosingPunctuation("."))
    }

    @Test
    fun tellsOneLineBoxesFromMessages() {
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(isSingleLineField(text))
        assertTrue(isSingleLineField(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse(isSingleLineField(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(isSingleLineField(InputType.TYPE_CLASS_NUMBER))
    }
}
