package sg.hirokids.shared.domain

/**
 * The language filter of a search (FR-7). [facetId] is NLB's code for the `Languages` parameter and [nlbName] the name it puts on a
 * record (`language`). [ALL] sends no filter. Indonesian and other languages show under [ALL] only.
 */
enum class BookLanguage(
    val facetId: String?,
    val nlbName: String?,
) {
    ALL(null, null),
    ENGLISH("eng", "English"),
    CHINESE("chi", "Chinese"),
    MALAY("may", "Malay"),
    TAMIL("tam", "Tamil"),
    ;

    /**
     * For saved titles shown offline. A title saved before languages were kept has none recorded and stays visible, because
     * hiding it would hide books that may well be the ones asked for.
     */
    fun allows(title: Title): Boolean = nlbName == null || title.languages.isEmpty() || nlbName in title.languages

    companion object {
        /** What a new install starts with: English books first. */
        val DEFAULT = ENGLISH

        fun parse(name: String?): BookLanguage = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
