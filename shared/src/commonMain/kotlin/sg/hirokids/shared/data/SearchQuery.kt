package sg.hirokids.shared.data

/** What the user typed, decided once so the screen and the repository agree (FR-1). */
sealed interface SearchQuery {
    data class Keywords(
        val text: String,
    ) : SearchQuery

    /** A 10 or 13 digit ISBN with hyphens and spaces removed; the last character of a 10 digit one may be X. */
    data class Isbn(
        val value: String,
    ) : SearchQuery
}

private const val MIN_QUERY_LENGTH = 2
private val whitespace = Regex("""\s+""")
private val isbn13 = Regex("""\d{13}""")
private val isbn10 = Regex("""\d{9}[\dX]""")

/** Null when the query is too short to send (fewer than 2 characters). An ISBN-shaped query goes to `GetTitles?ISBN=`. */
fun parseSearchQuery(raw: String): SearchQuery? {
    val text = raw.trim().replace(whitespace, " ")
    val compact = text.replace(" ", "").replace("-", "").uppercase()
    return when {
        isbn13.matches(compact) || isbn10.matches(compact) -> SearchQuery.Isbn(compact)
        text.length >= MIN_QUERY_LENGTH -> SearchQuery.Keywords(text)
        else -> null
    }
}
