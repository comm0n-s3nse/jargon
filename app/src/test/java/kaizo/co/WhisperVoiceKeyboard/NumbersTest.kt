package kaizo.co.WhisperVoiceKeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class NumbersTest {
    private fun d(s: String) = Numbers.digits(s)

    @Test
    fun modelNumbersAndYears() {
        assertEquals("RTX 4090 is fast.", d("RTX forty ninety is fast."))
        assertEquals("Born in 1984.", d("Born in nineteen eighty four."))
        assertEquals("Room 123", d("Room one two three"))
        assertEquals("The code is 4071.", d("The code is forty seventy one."))
    }

    @Test
    fun realNumbers() {
        assertEquals("21 days", d("twenty one days"))
        assertEquals("21 days", d("twenty-one days"))
        assertEquals("2005 was it", d("two thousand and five was it"))
        assertEquals("Call 10 people", d("Call ten people"))
        assertEquals("About 3.14", d("About three point one four"))
        assertEquals("a 100 times", d("a hundred times"))
        assertEquals("1500 words", d("fifteen hundred words"))
    }

    @Test
    fun proseStaysProse() {
        assertEquals("I have two cats", d("I have two cats"))
        assertEquals("one of them", d("one of them"))
        assertEquals("Point taken", d("Point taken"))
        assertEquals("and so on", d("and so on"))
        assertEquals("No numbers here.", d("No numbers here."))
    }
}
