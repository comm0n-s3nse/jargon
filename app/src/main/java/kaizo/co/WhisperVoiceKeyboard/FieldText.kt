package kaizo.co.WhisperVoiceKeyboard

import android.text.InputType

/** A one-line text box: a search bar, an address or menu entry. Not a message or a note. */
fun isSingleLineField(inputType: Int): Boolean =
    inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
        inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0

/**
 * A single word is rarely a sentence: a name in a form, a reply, a search. The model ends it with
 * a period anyway; that comes off. "?" and "!" stay, they were meant.
 */
fun withoutLoneWordPeriod(text: String): String {
    val t = text.trim()
    val word = t.dropLast(1)
    val lone = t.endsWith('.') && !t.endsWith("..") && word.isNotEmpty() && word.none { it.isWhitespace() } && word.any { it.isLetterOrDigit() }
    return if (lone) word else text
}

/**
 * What goes in a one-line box is a word or a phrase, not a sentence, so the model's closing
 * period or comma is dropped when the text is a single sentence. ! and ? are kept, and so is
 * anything with sentence ends inside it ("Go. Now.").
 */
fun withoutClosingPunctuation(text: String): String {
    val t = text.trimEnd()
    val body = t.trimEnd('.', ',', ';', ':')
    if (body.length == t.length || body.isEmpty()) return t
    if (body.any { it in ".!?" }) return t
    return body
}
