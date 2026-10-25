package kaizo.co.WhisperVoiceKeyboard

/**
 * Words people say for things they cannot type by voice: punctuation at the start or end of
 * what was said ("question mark", "period"), and a few emoji ("smiley face", "facepalm").
 * The model writes them out as words; these turn them into the characters.
 */
object SpokenText {
    /**
     * [text] with the spoken marks replaced; [attachLeft] when it starts with punctuation, which
     * belongs to the word before the cursor; [bare] when it is whitespace to commit exactly as is.
     */
    data class Result(val text: String, val attachLeft: Boolean, val bare: Boolean = false)

    /** One mark, the way it is shown in the list, and the ways people say it (a regex). */
    data class SpokenMark(val label: String, val said: String, val mark: String)

    val PUNCTUATION = listOf(
        SpokenMark("question mark", "question mark", "?"),
        SpokenMark("exclamation point", "exclamation (?:point|mark)", "!"),
        SpokenMark("period", "period|full stop", "."),
        SpokenMark("comma", "comma", ","),
        SpokenMark("semicolon", "semi ?colon", ";"),
        SpokenMark("colon", "colon", ":"),
        SpokenMark("hyphen", "hyphen|dash", "-"),
        SpokenMark("dot dot dot", "dot dot dot|ellipsis", "..."),
        SpokenMark("new line", "new ?line", "\n"),
    )

    // "space" on its own is the space bar; in a sentence it is a word ("outer space")
    private val spaceOnly = Regex("""^\s*space[.,!?]*\s*$""", RegexOption.IGNORE_CASE)
    // the model sometimes writes "cry, laugh" or "hug-face" for a two-word phrase: any gap counts
    // ("winkface", "hug-face", "cry, laugh" are all the same phrase to the person who said it)
    private fun loose(said: String) = said
        .replace(" ?", " ")
        .replace(" ", """[\s,\-]*""")
    private val phrases = PUNCTUATION.joinToString("|") { "(?:${loose(it.said)})" }

    // "Question mark." / "Period, do you need anything" at the very start
    private val leading = Regex("""^\s*($phrases)(?![\p{L}\p{N}])[.,!?]*\s*""", RegexOption.IGNORE_CASE)

    // "...anything else question mark." / "...else, comma" at the very end
    private val trailing = Regex("""[.,;:]?\s+($phrases)(?![\p{L}\p{N}])[.,!?]*\s*$""", RegexOption.IGNORE_CASE)

    // the whole thing is one spoken mark
    private val only = Regex("""^\s*($phrases)(?![\p{L}\p{N}])[.,!?]*\s*$""", RegexOption.IGNORE_CASE)

    // "well hyphen known" between two words joins them: the one mark that belongs in the middle
    private val joiner = Regex("""(?<=[\p{L}\p{N}])[\s,]+(?:hyphen|dash)[\s,]+(?=[\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    // "wait dot dot dot what" trails off in the middle: "wait... what"
    private val trailOff = Regex("""(?<=[\p{L}\p{N}])[\s,]+(?:dot dot dot|ellipsis)[\s,.]+(?=[\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    // "first line new line second line" breaks the line in the middle
    private val lineBreak = Regex("""[\s,]*[.,]?\s+new ?line[\s,.]+(?=\S)""", RegexOption.IGNORE_CASE)

    private fun mark(word: String): String =
        PUNCTUATION.first { Regex(loose(it.said), RegexOption.IGNORE_CASE).matches(word) }.mark

    /**
     * Spoken punctuation at the start or end becomes the character. Only there: "the period of
     * time" in the middle stays words. [capitalize] starts a new sentence after . ! ? (off in
     * casual mode).
     */
    fun punctuation(text: String, capitalize: Boolean = true): Result {
        if (spaceOnly.matches(text)) return Result(" ", attachLeft = false, bare = true)
        only.find(text)?.let { m ->
            val p = mark(m.groupValues[1])
            return Result(p, attachLeft = p != "\n", bare = p == "\n")
        }
        var out = trailOff.replace(joiner.replace(text, "-"), "... ")
        out = lineBreak.replace(out) { m -> "\n" + (if (capitalize) "" else "") }
        if (capitalize) out = out.split("\n").joinToString("\n") { upperFirst(it) }
        var attachLeft = false
        leading.find(out)?.let { m ->
            val p = mark(m.groupValues[1])
            var rest = out.substring(m.range.last + 1)
            rest = if (p in ".!?") {
                if (capitalize) upperFirst(rest) else rest
            } else {
                lowerFirst(rest)
            }
            out = when (p) {
                "-" -> "$p$rest"
                "\n" -> "$p${if (capitalize) upperFirst(rest) else rest}"
                else -> "$p $rest"
            }
            attachLeft = p != "\n"
        }
        trailing.find(out)?.let { m ->
            out = out.substring(0, m.range.first) + mark(m.groupValues[1])
        }
        return Result(out, attachLeft)
    }

    /** One emoji, the way it is shown in the list, and the ways people say it (a regex). */
    data class SpokenEmoji(val label: String, val said: String, val emoji: String)

    val EMOJI = listOf(
        SpokenEmoji("smiley face", "smiley face", "🙂"),
        SpokenEmoji("smiling face", "smiling face", "😊"),
        SpokenEmoji("wink(y) face", "winky? face", "😉"),
        SpokenEmoji("hug face", "hug(?:ging)? face", "🤗"),
        SpokenEmoji("laughing face", "laughing face", "😆"),
        SpokenEmoji("cry laugh", "cry(?:ing)? laugh(?:ing)?", "😂"),
        SpokenEmoji("sweat smile", "sweat (?:smile|laugh)", "😅"),
        SpokenEmoji("grimace", "grimac(?:e|ing)(?: face)?", "😬"),
        SpokenEmoji("sad face", "sad face", "🙁"),
        SpokenEmoji("crying face", "crying face", "😢"),
        SpokenEmoji("eye roll", "eye ?roll", "🙄"),
        SpokenEmoji("facepalm", "face ?palm", "🤦"),
        SpokenEmoji("shrug emoji", "shrug emoji", "🤷"),
        SpokenEmoji("thumbs up", "thumbs up", "👍"),
        SpokenEmoji("thumbs down", "thumbs down", "👎"),
        SpokenEmoji("clapping hands", "clapping hands", "👏"),
        SpokenEmoji("folded hands", "folded hands", "🙏"),
        SpokenEmoji("heart emoji", "heart emoji", "❤️"),
        SpokenEmoji("fire emoji", "fire emoji", "🔥"),
        SpokenEmoji("party popper", "party popper", "🎉"),
        SpokenEmoji("skull emoji", "skull emoji", "💀"),
        SpokenEmoji("rocket emoji", "rocket emoji", "🚀"),
        SpokenEmoji("hundred emoji", "hundred emoji", "💯"),
        SpokenEmoji("sparkle emoji", "sparkles? emoji", "✨"),
        SpokenEmoji("sun emoji", "sun emoji", "☀️"),
        SpokenEmoji("see no evil", "see no evil|monkey (?:covering (?:its |his )?)?eyes|shy monkey", "🙈"),
    )
    private val emojiPhrases = EMOJI.joinToString("|") { "(?:${loose(it.said)})" }

    // the phrase as whole words, swallowing a period the model may put after it
    private val emoji = Regex("""(?<![\p{L}\p{N}])($emojiPhrases)(?![\p{L}\p{N}])\.?""", RegexOption.IGNORE_CASE)
    private val emojiChars = EMOJI.joinToString("|") { Regex.escape(it.emoji) }

    // the pause before "laughing face" comes out as a comma: "a goof, 😂" reads wrong, so the comma goes
    private val commaBefore = Regex("""\s*[,;:]\s*(?=(?:$emojiChars))""")
    private val commaAfter = Regex("""(?<=(?:$emojiChars))\s*[,;:](?=\s|$)""")

    /** Spoken emoji anywhere in [text] become the emoji, with no comma stuck to them. */
    fun emoji(text: String): String {
        val out = emoji.replace(text) { m ->
            val said = m.groupValues[1]
            EMOJI.first { Regex(loose(it.said), RegexOption.IGNORE_CASE).matches(said) }.emoji
        }
        if (out == text) return text
        return commaAfter.replace(commaBefore.replace(out, " "), "").trim()
    }

    // chat shorthand reads as lowercase; the model writes it in capitals
    private val slang = Regex("""\b(lol|lmao|rofl|imo|imho|tbh|idk|btw|smh|brb|afaik|ikr|aka)\b""", RegexOption.IGNORE_CASE)

    /** "LOL" and friends in lowercase, wherever they are. */
    fun chatCase(text: String): String = slang.replace(text) { it.value.lowercase() }

    private fun upperFirst(s: String): String {
        val i = s.indexOfFirst { !it.isWhitespace() }
        return if (i < 0) s else s.substring(0, i) + s[i].uppercaseChar() + s.substring(i + 1)
    }

    // keeps "I" and acronyms, as the first-letter rule elsewhere does
    private fun lowerFirst(s: String): String {
        val i = s.indexOfFirst { !it.isWhitespace() }
        if (i < 0 || !s[i].isUpperCase()) return s
        val next = s.getOrNull(i + 1)
        if (next?.isUpperCase() == true || (s[i] == 'I' && next?.isLetter() != true)) return s
        return s.substring(0, i) + s[i].lowercaseChar() + s.substring(i + 1)
    }
}
