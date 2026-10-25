package kaizo.co.WhisperVoiceKeyboard

/**
 * Spoken numbers as digits: "forty ninety" → "4090", "two thousand five" → "2005", "nineteen
 * eighty four" → "1984", "three point one four" → "3.14". Models that write words for what people
 * read as figures (model numbers, years, codes) are the reason; the rule people mostly want is
 * "digits", with one exception kept here: a number word on its own stays a word below ten ("two
 * cats", "one of them"), since that is prose, not a figure.
 */
object Numbers {
    private val UNITS = mapOf(
        "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val SCALES = mapOf("hundred" to 100, "thousand" to 1_000, "million" to 1_000_000, "billion" to 1_000_000_000)
    private val POINT = setOf("point")
    private val WORD = Regex("""[A-Za-z]+(?:-[A-Za-z]+)?|\d+|[^\sA-Za-z\d]+|\s+""")

    private fun isNumberWord(w: String) = w in UNITS || w in TENS || w in SCALES || w in POINT || w == "and" ||
        (w.contains('-') && w.split('-').all { it in UNITS || it in TENS })

    /** [text] with each run of number words replaced by digits. */
    fun digits(text: String): String {
        val tokens = WORD.findAll(text).map { it.value }.toList()
        val out = StringBuilder()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val lower = t.lowercase()
            if (!isNumberWord(lower) || lower == "and" || lower == "point") {
                out.append(t)
                i++
                continue
            }
            // collect the run: number words, with "and"/"point" allowed inside, separated by spaces or hyphens
            val run = mutableListOf<String>()
            var j = i
            var lastWasNumber = false
            while (j < tokens.size) {
                val tj = tokens[j].lowercase()
                when {
                    tj.isBlank() && tokens[j].all { it == ' ' } -> { if (!lastWasNumber) break; j++; continue }
                    isNumberWord(tj) -> {
                        if ((tj == "and" || tj == "point") && (run.isEmpty() || j + 2 >= tokens.size)) break
                        run += tj; lastWasNumber = true; j++
                    }
                    else -> break
                }
            }
            // "and"/"point" cannot end a run
            while (run.isNotEmpty() && (run.last() == "and" || run.last() == "point")) run.removeAt(run.lastIndex)
            val converted = convert(run)
            if (converted == null) {
                out.append(t)
                i++
                continue
            }
            // put back the tokens the run did not use (trailing spaces/words after trimming)
            val used = countTokens(tokens, i, run.size)
            out.append(converted)
            i += used
        }
        return out.toString()
    }

    // how many tokens from [start] make up [words] number words (spaces between them included)
    private fun countTokens(tokens: List<String>, start: Int, words: Int): Int {
        var n = 0; var seen = 0; var k = start
        while (k < tokens.size && seen < words) {
            if (tokens[k].isNotBlank()) seen++
            n++; k++
        }
        return n
    }

    /** The digits for one run, or null when it should stay words. */
    fun convert(words: List<String>): String? {
        if (words.isEmpty()) return null
        if (words.size == 1) {
            val w = words[0]
            if (w.contains('-')) return convert(w.split('-'))
            val v = UNITS[w] ?: TENS[w] ?: SCALES[w] ?: return null
            return if (v < 10) null else v.toString() // "ten" and up on their own; "two cats" stays
        }
        val flat = words.flatMap { if (it.contains('-')) it.split('-') else listOf(it) }
        // a decimal: whole part, "point", then digits one by one
        val point = flat.indexOf("point")
        if (point > 0) {
            val whole = convert(flat.subList(0, point)) ?: value(flat.subList(0, point))?.toString() ?: return null
            val frac = flat.subList(point + 1, flat.size)
            if (frac.isEmpty() || frac.any { UNITS[it] == null || UNITS[it]!! > 9 }) return null
            return whole + "." + frac.joinToString("") { UNITS[it].toString() }
        }
        value(flat)?.let { return it.toString() }
        // not one number: groups read out in sequence, "forty ninety", "nineteen eighty four", "one two three"
        val groups = mutableListOf<Long>()
        var k = 0
        while (k < flat.size) {
            val w = flat[k]
            when {
                TENS[w] != null -> {
                    var g = TENS[w]!!.toLong()
                    if (k + 1 < flat.size && UNITS[flat[k + 1]] != null && UNITS[flat[k + 1]]!! in 1..9) { g += UNITS[flat[k + 1]]!!; k++ }
                    groups += g
                }
                UNITS[w] != null -> groups += UNITS[w]!!.toLong()
                w == "and" -> {}
                else -> return null
            }
            k++
        }
        if (groups.size < 2) return null
        return groups.joinToString("") { it.toString() }
    }

    /** The value of a well-formed number phrase ("two thousand and five"), or null. */
    private fun value(words: List<String>): Long? {
        var total = 0L; var current = 0L; var seenAny = false
        for (w in words) {
            when {
                w == "and" -> if (!seenAny) return null
                UNITS[w] != null -> { if (current % 10 != 0L && UNITS[w]!! < 10 && current < 100) return null; current += UNITS[w]!!; seenAny = true }
                TENS[w] != null -> { if (current % 100 != 0L) return null; current += TENS[w]!!; seenAny = true }
                w == "hundred" -> { if (!seenAny) current = 1; current *= 100 }
                SCALES[w] != null -> { if (!seenAny) current = 1; total += current * SCALES[w]!!; current = 0 }
                else -> return null
            }
        }
        val v = total + current
        // "nineteen eighty four" is not 19+84: a unit/teen followed by tens is a read-out, not a value
        var prevTeen = false
        for (w in words) {
            val u = UNITS[w]
            if (prevTeen && (TENS[w] != null || (u != null && u >= 10))) return null
            prevTeen = u != null && u >= 10 || (u != null && TENS[w] == null && u < 10 && words.size > 1 && SCALES.keys.none { it in words })
            if (SCALES[w] != null || w == "and") prevTeen = false
        }
        return if (seenAny || total > 0) v else null
    }
}
