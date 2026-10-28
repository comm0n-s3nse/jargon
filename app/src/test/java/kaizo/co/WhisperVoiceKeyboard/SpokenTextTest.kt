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
    fun aSentenceEnderTheModelClosedEndsTheSentenceMidway() {
        assertEquals("There is great news today!", SpokenText.punctuation("There is great news today, exclamation point.").text)
        assertEquals("There is great news today! We leave at six.", SpokenText.punctuation("There is great news today, exclamation point. We leave at six.").text)
        assertEquals("Is it true? I think so.", SpokenText.punctuation("Is it true question mark. I think so.").text)
        assertEquals("I'm done. Next thing.", SpokenText.punctuation("I'm done, period. Next thing.").text)
        assertEquals("is it true? i think so.", SpokenText.punctuation("is it true question mark. i think so.", capitalize = false).text)
        // "period" without the model's comma is prose; a mark the model did not close is left alone
        assertEquals("The end of the period. Next one.", SpokenText.punctuation("The end of the period. Next one.").text)
        assertEquals("Put a question mark there.", SpokenText.punctuation("Put a question mark there.").text)
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
    fun dotDotDotTrailsOff() {
        assertEquals("Wait... what", SpokenText.punctuation("Wait dot dot dot what").text)
        assertEquals("Wait... what", SpokenText.punctuation("Wait, dot dot dot, what").text)
        assertEquals("I guess...", SpokenText.punctuation("I guess dot dot dot.").text)
        val r = SpokenText.punctuation("Ellipsis")
        assertEquals("...", r.text)
        assertTrue(r.attachLeft)
        assertEquals("... and then", SpokenText.punctuation("Dot dot dot and then").text)
    }

    @Test
    fun spaceAndNewLine() {
        val sp = SpokenText.punctuation("Space.")
        assertEquals(" ", sp.text)
        assertTrue(sp.bare)
        assertEquals("Outer space is big.", SpokenText.punctuation("Outer space is big.").text)
        val nl = SpokenText.punctuation("New line")
        assertEquals("\n", nl.text)
        assertTrue(nl.bare)
        assertFalse(nl.attachLeft)
        assertEquals("Dear Sam\nThanks for the lamp.", SpokenText.punctuation("Dear Sam new line thanks for the lamp.").text)
        assertEquals("Dear Sam\nThanks for the lamp.", SpokenText.punctuation("Dear Sam, newline, thanks for the lamp.").text)
        assertEquals("Thanks\n", SpokenText.punctuation("Thanks new line").text)
        assertEquals("Bye 🙈", SpokenText.emoji("Bye see no evil"))
        assertEquals("Bye 🙈", SpokenText.emoji("Bye monkey covering its eyes"))
    }

    @Test
    fun otherLanguagesHaveTheirOwnPhrasesAndKeepTheEnglishOnes() {
        assertEquals("Até amanhã 🙂", SpokenText.emoji("Até amanhã carinha feliz", "pt"))
        assertEquals("Até amanhã 👍", SpokenText.emoji("Até amanhã joinha", "pt"))
        assertEquals("Até amanhã 🙂", SpokenText.emoji("Até amanhã smiley face", "pt")) // English travels
        assertEquals("Se vidimo 😉", SpokenText.emoji("Se vidimo pomezik", "sl")) // diacritics optional
        assertEquals("Até amanhã carinha feliz", SpokenText.emoji("Até amanhã carinha feliz", "en")) // not in English
        assertEquals("Onde estás?", SpokenText.punctuation("Onde estás ponto de interrogação", lang = "pt").text)
        assertEquals("Kje si?", SpokenText.punctuation("Kje si vprasaj", lang = "sl").text)
        assertEquals("Chegamos ao ponto.", SpokenText.punctuation("Chegamos ao ponto.", lang = "pt").text) // "ponto" only alone
        assertEquals(".", SpokenText.punctuation("Ponto.", lang = "pt").text)
        assertEquals("Hi 😉", SpokenText.emoji("Hi wing face")) // what Canary hears
    }

    @Test
    fun chatShorthandIsLowercase() {
        assertEquals("That was great lol", SpokenText.chatCase("That was great LOL"))
        assertEquals("imo it works", SpokenText.chatCase("IMO it works"))
        assertEquals("The LOLA bus", SpokenText.chatCase("The LOLA bus"))
        assertEquals("Jargon, aka the keyboard", SpokenText.chatCase("Jargon, AKA the keyboard"))
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
        // the model's own comma or hyphen inside the phrase
        assertEquals("Bye 🤗", SpokenText.emoji("Bye hug, face"))
        assertEquals("Ha 😂", SpokenText.emoji("Ha, cry, laugh."))
        assertEquals("Hi 😉", SpokenText.emoji("Hi winky-face"))
        assertEquals("Hi 😉", SpokenText.emoji("Hi winkface"))
        assertEquals("Ok 👍", SpokenText.emoji("Ok thumbsup"))
        assertEquals("Really?", SpokenText.punctuation("Really question, mark").text)
    }

    @Test
    fun noCommaStuckToAnEmoji() {
        assertEquals("You're a goof 😆", SpokenText.emoji("You're a goof, laughing face."))
        assertEquals("Fine 🤷 whatever", SpokenText.emoji("Fine, shrug emoji, whatever"))
        assertEquals("🙂 see you soon.", SpokenText.emoji("Smiley face, see you soon."))
        assertEquals("Nice, really nice", SpokenText.emoji("Nice, really nice"))
    }
}
