package kaizo.co.WhisperVoiceKeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenTextTest {
    @Test
    fun aMarkOnItsOwnAttachesToTheWordBefore() {
        val r = SpokenText.punctuation("Question mark.")
        assertEquals("?", r.text)
        assertTrue(r.attachLeft)
        assertEquals("!", SpokenText.punctuation("exclamation point").text)
        assertEquals(",", SpokenText.punctuation(" Comma, ").text)
    }

    @Test
    fun aMarkAtTheStartBeginsTheNextSentence() {
        val r = SpokenText.punctuation("Period do you need anything else?")
        assertEquals(". Do you need anything else?", r.text)
        assertTrue(r.attachLeft)
        assertEquals(", and the lights", SpokenText.punctuation("Comma and the lights").text)
        assertEquals(", I think so", SpokenText.punctuation("Comma I think so").text)
        assertEquals(". do you", SpokenText.punctuation("period do you", capitalize = false).text)
    }

    @Test
    fun aMarkAtTheEndReplacesTheModelsOwn() {
        assertEquals("Do you need anything else?", SpokenText.punctuation("Do you need anything else question mark.").text)
        assertEquals("Do you need anything else?", SpokenText.punctuation("Do you need anything else, question mark").text)
        assertEquals("Turn them off!", SpokenText.punctuation("Turn them off exclamation mark").text)
        assertFalse(SpokenText.punctuation("Turn them off, period.").attachLeft)
    }

    @Test
    fun wordsInTheMiddleStayWords() {
        assertEquals("The period of time was long.", SpokenText.punctuation("The period of time was long.").text)
        assertEquals("A comma splice is common.", SpokenText.punctuation("A comma splice is common.").text)
        assertEquals("Periodically it fails.", SpokenText.punctuation("Periodically it fails.").text)
    }

    @Test
    fun hyphenJoinsWordsAndAttaches() {
        assertEquals("A well-known trick", SpokenText.punctuation("A well hyphen known trick").text)
        assertEquals("A well-known trick", SpokenText.punctuation("A well, dash, known trick").text)
        val r = SpokenText.punctuation("hyphen known")
        assertEquals("-known", r.text)
        assertTrue(r.attachLeft)
        assertEquals("-", SpokenText.punctuation("Dash.").text)
        assertEquals("Wait semi colon then go", SpokenText.punctuation("Wait semi colon then go").text) // only at the edges
    }

    @Test
    fun chatShorthandIsLowercase() {
        assertEquals("That was great lol", SpokenText.chatCase("That was great LOL"))
        assertEquals("imo it works", SpokenText.chatCase("IMO it works"))
        assertEquals("The LOLA bus", SpokenText.chatCase("The LOLA bus"))
    }

    @Test
    fun spokenEmojiBecomeEmoji() {
        assertEquals("See you soon 🙂", SpokenText.emoji("See you soon smiley face."))
        assertEquals("I forgot again 🤦", SpokenText.emoji("I forgot again facepalm"))
        assertEquals("Great 👍 thanks", SpokenText.emoji("Great thumbs up thanks"))
        assertEquals("A sad faced man", SpokenText.emoji("A sad faced man"))
    }

    @Test
    fun theWaysPeopleSayOneEmoji() {
        assertEquals("Hi 😉", SpokenText.emoji("Hi winky face"))
        assertEquals("Hi 😉", SpokenText.emoji("Hi wink face"))
        assertEquals("Oops 🤦", SpokenText.emoji("Oops face palm"))
        assertEquals("Ha 😂", SpokenText.emoji("Ha cry laugh"))
        assertEquals("Ha 😂", SpokenText.emoji("Ha crying laughing"))
        assertEquals("Ha 😆", SpokenText.emoji("Ha laughing face"))
        assertEquals("Phew 😅", SpokenText.emoji("Phew sweat smile"))
        assertEquals("Well 😬", SpokenText.emoji("Well grimace"))
        assertEquals("Well 😬", SpokenText.emoji("Well grimace face"))
        assertEquals("Bye 🤗", SpokenText.emoji("Bye hug face"))
    }

    @Test
    fun noCommaStuckToAnEmoji() {
        assertEquals("You're a goof 😆", SpokenText.emoji("You're a goof, laughing face."))
        assertEquals("Fine 🤷 whatever", SpokenText.emoji("Fine, shrug emoji, whatever"))
        assertEquals("🙂 see you soon.", SpokenText.emoji("Smiley face, see you soon."))
        assertEquals("Nice, really nice", SpokenText.emoji("Nice, really nice"))
    }
}
