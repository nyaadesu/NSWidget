package app.nswidget.data

/**
 * Reads the free-text notes NS attaches to a departure during disruptions ("Rijdt niet verder dan
 * Rotterdam Centraal", "Stopt niet in Delft Campus", "Rijdt niet"). NS writes them in Dutch by
 * default; the English wording is matched too in case the API answers in English.
 */
object Disruptions {
    private val ENDS_AT = listOf(
        Regex("""niet verder dan\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""\brijdt\s+(?:vandaag\s+)?(?:alleen\s+)?tot\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""\beindigt\s+(?:vandaag\s+)?(?:in|op|te)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:terminates|ends)\s+(?:today\s+)?(?:at|in)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:runs|goes|travels)\s+(?:today\s+)?only\s+(?:as far as|to|until)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""\bno further than\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""\bnot\s+(?:run|go|travel|continue)\s+(?:any\s+)?(?:further|beyond)\s+(?:than\s+)?(.+)""", RegexOption.IGNORE_CASE),
    )

    private val SKIPS = listOf(
        Regex("""\bstopt\s+(?:vandaag\s+)?niet\s+(?:in|op|te)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:does not|doesn't|will not)\s+(?:stop|call)\s+(?:today\s+)?(?:in|at)\s+(.+)""", RegexOption.IGNORE_CASE),
    )

    private val CANCELLED = Regex(
        """^(?:let op[,:]?\s*)?(?:deze trein\s+)?(?:rijdt\s+(?:vandaag\s+)?niet|valt\s+(?:vandaag\s+)?uit|""" +
            """(?:train\s+)?(?:is\s+)?cancell?ed|(?:this train\s+)?does not run(?:\s+today)?)[.!]?$""",
        RegexOption.IGNORE_CASE,
    )

    /** The note says the whole train is cancelled. */
    fun isCancelled(note: String): Boolean = CANCELLED.matches(note.trim())

    /** The station a shortened train now ends at, or null if the note isn't about that. */
    fun endsAt(note: String): String? =
        ENDS_AT.firstNotNullOfOrNull { it.find(note) }?.let { firstPlace(it.groupValues[1]) }

    /** Stations the note says the train skips ("Stopt niet in Delft Campus en Schiedam Centrum"). */
    fun skippedStops(note: String): List<String> {
        val list = SKIPS.firstNotNullOfOrNull { it.find(note) }?.groupValues?.get(1) ?: return emptyList()
        return sentence(list)
            .split(Regex(""",\s*|\s+(?:en|and)\s+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** Cuts a captured station name off at the end of its sentence or clause. */
    private fun firstPlace(text: String): String? =
        sentence(text).split(Regex(""",\s*|\s+(?:en|and|door|due to|because)\s+""")).first().trim().takeIf { it.isNotEmpty() }

    // A full stop ends the sentence, except after a one-letter abbreviation like "Rotterdam C." or "Hardinxveld-G.".
    private fun sentence(text: String): String =
        text.split(Regex("""(?<![\s-]\p{L})\.(?:\s|$)|[;!]|\s+[-–(]""")).first().trim()

    /**
     * Whether two NS station names mean the same station, allowing for NS's short forms:
     * "Rotterdam C." = "Rotterdam Centraal", "Den Haag HS" = "Den Haag Hollands Spoor".
     */
    fun sameStation(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return matches(tokens(a), tokens(b)) || matches(tokens(b), tokens(a))
    }

    private fun tokens(name: String): List<String> =
        name.lowercase().replace('’', '\'').split(Regex("""[^\p{L}\p{N}']+""")).filter { it.isNotEmpty() }

    /** [short] matches [long] word by word; a short word may abbreviate one long word or be its initials. */
    private fun matches(short: List<String>, long: List<String>): Boolean {
        if (short.isEmpty() || long.isEmpty()) return short.isEmpty() && long.isEmpty()
        val word = short.first()
        if (long.first().startsWith(word) && matches(short.drop(1), long.drop(1))) return true
        // Initials: "hs" for "hollands spoor".
        if (word.length in 2..long.size &&
            word.indices.all { long[it].startsWith(word[it]) } &&
            matches(short.drop(1), long.drop(word.length))
        ) {
            return true
        }
        return false
    }
}
