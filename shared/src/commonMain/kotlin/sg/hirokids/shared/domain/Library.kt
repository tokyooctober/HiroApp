package sg.hirokids.shared.domain

/** A library the user can choose. Only `open` libraries that NLB lists ever become one. */
data class Library(
    val code: String,
    val name: String,
    val position: LatLon,
    /** Other text a person might type to find it: NLB's own name for the branch, and the street address (which names the mall). */
    val aliases: List<String> = emptyList(),
) {
    /** True when every word typed is found in the name, an alias or the address, whatever the letter case. Blank matches everything. */
    fun matches(query: String): Boolean {
        val words = query.lowercase().split(whitespace).filter { it.isNotEmpty() }
        val text = (listOf(name) + aliases).joinToString(" ").lowercase()
        return words.all { it in text }
    }

    private companion object {
        val whitespace = Regex("""\s+""")
    }
}

data class LibraryDistance(
    val library: Library,
    val meters: Double,
)

/** Status in `data/nlb_libraries.json`. Anything that is not clearly open is hidden (hide when ambiguous). */
enum class LibraryStatus {
    OPEN,
    UPCOMING,
    CLOSED,
    UNKNOWN,
    ;

    companion object {
        fun parse(value: String): LibraryStatus =
            when (value.trim().lowercase()) {
                "open" -> OPEN
                "upcoming" -> UPCOMING
                "closed" -> CLOSED
                else -> UNKNOWN
            }
    }
}

/** One entry of the bundled seed list (`data/nlb_libraries.json`). */
data class SeedLibrary(
    val code: String?,
    val name: String,
    val position: LatLon,
    val status: LibraryStatus,
    val address: String = "",
)

/** One branch from NLB's live `GetBranches` list. */
data class LiveBranch(
    val code: String,
    val position: LatLon?,
    /** NLB's name, such as "Tampines Library"; the seed list's names differ for most branches. */
    val name: String = "",
)
