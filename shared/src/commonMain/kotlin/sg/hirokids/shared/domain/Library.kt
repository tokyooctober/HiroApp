package sg.hirokids.shared.domain

/** A library the user can choose. Only `open` libraries that NLB lists ever become one. */
data class Library(
    val code: String,
    val name: String,
    val position: LatLon,
)

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
)

/** One branch from NLB's live `GetBranches` list. */
data class LiveBranch(
    val code: String,
    val position: LatLon?,
)
