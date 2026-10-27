package kaizo.co.WhisperVoiceKeyboard

/**
 * Words people say for things they cannot type by voice: punctuation at the start or end of
 * what was said ("question mark", "period"), and a few emoji ("smiley face", "facepalm").
 * The model writes them out as words; these turn them into the characters.
 *
 * The English phrases work in every language, since "lol" and "smiley face" travel; a language
 * can add its own phrases, which are active only while that language is the one spoken, so the
 * sounds of one language never trigger another's emoji.
 */
object SpokenText {
    /**
     * [text] with the spoken marks replaced; [attachLeft] when it starts with punctuation, which
     * belongs to the word before the cursor; [bare] when it is whitespace to commit exactly as is.
     */
    data class Result(val text: String, val attachLeft: Boolean, val bare: Boolean = false)

    /** One mark, the way it is shown in the list, and the ways people say it (a regex). [edges]: also at the start or end of a dictation, not only on its own. */
    data class SpokenMark(val label: String, val said: String, val mark: String, val edges: Boolean = true)

    /** One emoji, the way it is shown in the list, and the ways people say it (a regex). */
    data class SpokenEmoji(val label: String, val said: String, val emoji: String)

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

    val EMOJI = listOf(
        SpokenEmoji("smiley face", "smiley face", "🙂"),
        SpokenEmoji("smiling face", "smiling face", "😊"),
        SpokenEmoji("wink(y) face", "win[kg]y? face", "😉"), // Canary hears "wing face"
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

    // Phrases of other languages, written the way their speakers name the marks and the pictures.
    // Drafted from dictionaries, not by native speakers yet: corrections welcome. Diacritics are
    // optional in the match, since the models often drop them.
    val LOCAL_MARKS: Map<String, List<SpokenMark>> = mapOf(
        "pt" to listOf(
            SpokenMark("ponto de interrogação", "ponto de interrogação", "?"),
            SpokenMark("ponto de exclamação", "ponto de exclamação", "!"),
            SpokenMark("ponto final", "ponto final", "."),
            SpokenMark("ponto", "ponto", ".", edges = false), // "ao ponto" is a phrase; alone it is the mark
            SpokenMark("vírgula", "vírgula", ","),
            SpokenMark("ponto e vírgula", "ponto e vírgula", ";"),
            SpokenMark("dois pontos", "dois pontos", ":"),
            SpokenMark("hífen", "hífen|travessão", "-"),
            SpokenMark("reticências", "reticências", "..."),
            SpokenMark("nova linha", "nova linha|quebra de linha", "\n"),
        ),
        "sl" to listOf(
            SpokenMark("vprašaj", "vprašaj", "?"),
            SpokenMark("klicaj", "klicaj", "!"),
            SpokenMark("pika", "pika", "."),
            SpokenMark("vejica", "vejica", ","),
            SpokenMark("podpičje", "podpičje", ";"),
            SpokenMark("dvopičje", "dvopičje", ":"),
            SpokenMark("vezaj", "vezaj|pomišljaj", "-"),
            SpokenMark("tri pike", "tri pike", "..."),
            SpokenMark("nova vrstica", "nova vrstica", "\n"),
        ),
    )

    val LOCAL_EMOJI: Map<String, List<SpokenEmoji>> = mapOf(
        "pt" to listOf(
            SpokenEmoji("carinha feliz", "carinha (?:feliz|sorridente)", "🙂"),
            SpokenEmoji("piscadela", "piscadela|carinha piscando", "😉"),
            SpokenEmoji("abraço emoji", "abraço emoji", "🤗"),
            SpokenEmoji("carinha rindo", "carinha rindo", "😆"),
            SpokenEmoji("chorando de rir", "chorando de rir", "😂"),
            SpokenEmoji("riso nervoso", "riso nervoso", "😅"),
            SpokenEmoji("careta", "careta", "😬"),
            SpokenEmoji("carinha triste", "carinha triste", "🙁"),
            SpokenEmoji("carinha chorando", "carinha chorando", "😢"),
            SpokenEmoji("olhos revirados", "olhos revirados", "🙄"),
            SpokenEmoji("mão na cara", "mão na cara", "🤦"),
            SpokenEmoji("dar de ombros", "dar de ombros", "🤷"),
            SpokenEmoji("joinha", "joinha|polegar para cima", "👍"),
            SpokenEmoji("polegar para baixo", "polegar para baixo", "👎"),
            SpokenEmoji("palmas", "palmas emoji|aplausos", "👏"),
            SpokenEmoji("mãos juntas", "mãos juntas", "🙏"),
            SpokenEmoji("coração emoji", "coração emoji", "❤️"),
            SpokenEmoji("fogo emoji", "fogo emoji", "🔥"),
            SpokenEmoji("festa emoji", "festa emoji", "🎉"),
            SpokenEmoji("caveira emoji", "caveira emoji", "💀"),
            SpokenEmoji("foguete emoji", "foguete emoji", "🚀"),
            SpokenEmoji("cem emoji", "cem emoji", "💯"),
            SpokenEmoji("brilhos emoji", "brilhos? emoji", "✨"),
            SpokenEmoji("sol emoji", "sol emoji", "☀️"),
            SpokenEmoji("macaco tapando os olhos", "macaco tapando os olhos", "🙈"),
        ),
        "sl" to listOf(
            SpokenEmoji("nasmejan obraz", "nasmejan obraz", "🙂"),
            SpokenEmoji("pomežik", "pomežik|mežikajoč obraz", "😉"),
            SpokenEmoji("objem emoji", "objem emoji", "🤗"),
            SpokenEmoji("smejoči obraz", "smejoči obraz", "😆"),
            SpokenEmoji("jok od smeha", "jok od smeha", "😂"),
            SpokenEmoji("živčen smeh", "živčen smeh", "😅"),
            SpokenEmoji("grimasa", "grimasa", "😬"),
            SpokenEmoji("žalosten obraz", "žalosten obraz", "🙁"),
            SpokenEmoji("jokajoči obraz", "jokajoči obraz", "😢"),
            SpokenEmoji("zavijanje z očmi", "zavijanje z očmi", "🙄"),
            SpokenEmoji("roka na obrazu", "roka na obrazu", "🤦"),
            SpokenEmoji("skomig", "skomig(?: z rameni)?", "🤷"),
            SpokenEmoji("palec gor", "palec gor", "👍"),
            SpokenEmoji("palec dol", "palec dol", "👎"),
            SpokenEmoji("ploskanje", "ploskanje", "👏"),
            SpokenEmoji("sklenjene roke", "sklenjene roke", "🙏"),
            SpokenEmoji("srce emoji", "srce emoji", "❤️"),
            SpokenEmoji("ogenj emoji", "ogenj emoji", "🔥"),
            SpokenEmoji("zabava emoji", "zabava emoji", "🎉"),
            SpokenEmoji("lobanja emoji", "lobanja emoji", "💀"),
            SpokenEmoji("raketa emoji", "raketa emoji", "🚀"),
            SpokenEmoji("sto emoji", "sto emoji", "💯"),
            SpokenEmoji("iskrice emoji", "iskrice emoji", "✨"),
            SpokenEmoji("sonce emoji", "sonce emoji", "☀️"),
            SpokenEmoji("opica z zakritimi očmi", "opica z zakritimi očmi", "🙈"),
        ),
    )

    private val LOCAL_SPACE = mapOf("pt" to "espaço", "sl" to "presledek")

    /** The marks in force for [lang]: its own first, then the English ones, which always work. */
    fun marksFor(lang: String): List<SpokenMark> = LOCAL_MARKS[code(lang)].orEmpty() + PUNCTUATION
    fun emojiFor(lang: String): List<SpokenEmoji> = LOCAL_EMOJI[code(lang)].orEmpty() + EMOJI
    fun hasLocal(lang: String) = code(lang) in LOCAL_MARKS || code(lang) in LOCAL_EMOJI

    private fun code(lang: String) = lang.lowercase().take(2)

    // diacritics are optional when matching: "vprasaj" is "vprašaj" to the person who said it
    private val FOLD = mapOf(
        'á' to "[áa]", 'à' to "[àa]", 'ã' to "[ãa]", 'â' to "[âa]", 'é' to "[ée]", 'ê' to "[êe]", 'í' to "[íi]",
        'ó' to "[óo]", 'ô' to "[ôo]", 'õ' to "[õo]", 'ú' to "[úu]", 'ç' to "[çc]", 'č' to "[čc]", 'š' to "[šs]", 'ž' to "[žz]",
    )
    private fun fold(p: String) = buildString { for (c in p) append(FOLD[c] ?: c.toString()) }

    // the model sometimes writes "cry, laugh" or "hug-face" for a two-word phrase: any gap counts
    // ("winkface", "hug-face", "cry, laugh" are all the same phrase to the person who said it)
    private fun loose(said: String) = fold(said.replace(" ?", " ").replace(" ", """[\s,\-]*"""))

    private val IC = RegexOption.IGNORE_CASE

    /** The compiled rules for one language. */
    private class Rules(lang: String) {
        val marks = marksFor(lang)
        val emojis = emojiFor(lang)
        private val edgeMarks = marks.filter { it.edges }
        private val phrasesAll = marks.joinToString("|") { "(?:${loose(it.said)})" }
        private val phrasesEdge = edgeMarks.joinToString("|") { "(?:${loose(it.said)})" }
        private fun alt(mark: String) = marks.filter { it.mark == mark }.joinToString("|") { "(?:${loose(it.said)})" }
        val spaceOnly = Regex("""^\s*(?:space${LOCAL_SPACE[lang]?.let { "|" + fold(it) } ?: ""})[.,!?]*\s*$""", IC)
        // "Question mark." / "Period, do you need anything" at the very start
        val leading = Regex("""^\s*($phrasesEdge)(?![\p{L}\p{N}])[.,!?]*\s*""", IC)
        // "...anything else question mark." / "...else, comma" at the very end
        val trailing = Regex("""[.,;:]?\s+($phrasesEdge)(?![\p{L}\p{N}])[.,!?]*\s*$""", IC)
        // the whole thing is one spoken mark
        val only = Regex("""^\s*($phrasesAll)(?![\p{L}\p{N}])[.,!?]*\s*$""", IC)
        // "well hyphen known" between two words joins them: the one mark that belongs in the middle
        val joiner = Regex("""(?<=[\p{L}\p{N}])[\s,]+(?:${alt("-")})[\s,]+(?=[\p{L}\p{N}])""", IC)
        // "wait dot dot dot what" trails off in the middle: "wait... what"
        val trailOff = Regex("""(?<=[\p{L}\p{N}])[\s,]+(?:${alt("...")})[\s,.]+(?=[\p{L}\p{N}])""", IC)
        // "first line new line second line" breaks the line in the middle
        val lineBreak = Regex("""[\s,]*[.,]?\s+(?:${alt("\n")})[\s,.]+(?=\S)""", IC)
        val matchers = marks.map { Regex(loose(it.said), IC) to it.mark }
        fun mark(word: String) = matchers.first { it.first.matches(word) }.second

        private val emojiPhrases = emojis.joinToString("|") { "(?:${loose(it.said)})" }
        // the phrase as whole words, swallowing a period the model may put after it
        val emoji = Regex("""(?<![\p{L}\p{N}])($emojiPhrases)(?![\p{L}\p{N}])\.?""", IC)
        val emojiMatchers = emojis.map { Regex(loose(it.said), IC) to it.emoji }
        private val emojiChars = emojis.joinToString("|") { Regex.escape(it.emoji) }
        // the pause before "laughing face" comes out as a comma: "a goof, 😂" reads wrong, so the comma goes
        val commaBefore = Regex("""\s*[,;:]\s*(?=(?:$emojiChars))""")
        val commaAfter = Regex("""(?<=(?:$emojiChars))\s*[,;:](?=\s|$)""")
    }

    private val cache = HashMap<String, Rules>()
    private fun rules(lang: String): Rules = synchronized(cache) { cache.getOrPut(code(lang)) { Rules(code(lang)) } }

    /**
     * Spoken punctuation at the start or end becomes the character. Only there: "the period of
     * time" in the middle stays words. [capitalize] starts a new sentence after . ! ? (off in
     * casual mode). [lang] is the language spoken, whose own phrases join the English ones.
     */
    fun punctuation(text: String, capitalize: Boolean = true, lang: String = "en"): Result {
        val r = rules(lang)
        if (r.spaceOnly.matches(text)) return Result(" ", attachLeft = false, bare = true)
        r.only.find(text)?.let { m ->
            val p = r.mark(m.groupValues[1])
            return Result(p, attachLeft = p != "\n", bare = p == "\n")
        }
        var out = r.trailOff.replace(r.joiner.replace(text, "-"), "... ")
        out = r.lineBreak.replace(out) { "\n" }
        if (capitalize) out = out.split("\n").joinToString("\n") { upperFirst(it) }
        var attachLeft = false
        r.leading.find(out)?.let { m ->
            val p = r.mark(m.groupValues[1])
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
        r.trailing.find(out)?.let { m ->
            out = out.substring(0, m.range.first) + r.mark(m.groupValues[1])
        }
        return Result(out, attachLeft)
    }

    /** Spoken emoji anywhere in [text] become the emoji, with no comma stuck to them. */
    fun emoji(text: String, lang: String = "en"): String {
        val r = rules(lang)
        val out = r.emoji.replace(text) { m ->
            val said = m.groupValues[1]
            r.emojiMatchers.first { it.first.matches(said) }.second
        }
        if (out == text) return text
        return r.commaAfter.replace(r.commaBefore.replace(out, " "), "").trim()
    }

    // chat shorthand reads as lowercase; the model writes it in capitals
    private val slang = Regex("""\b(lol|lmao|rofl|imo|imho|tbh|idk|btw|smh|brb|afaik|ikr|aka)\b""", IC)

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
