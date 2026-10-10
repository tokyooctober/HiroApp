package sg.hirokids.shared.domain

/** One row of "More on the shelf at other libraries" (FR-15): a library, how many books it has on the shelf, and how far it is. */
data class OtherLibrary(
    val library: Library,
    val onShelf: Int,
    val meters: Double,
)

/** The grouping rules that need no network and no screen, so they can be tested on their own (spec section 5, group 3). */
object ResultGrouper {
    /**
     * The libraries other than [current] that have books on the shelf, from the `location` facet of the all-libraries search
     * ([counts]: branch code to number of books). Only libraries in [directory] are listed, which keeps out the closed and not
     * yet open ones and any code NLB lists that we do not show. Most books first, then nearest to [current], then by name, so the
     * list never shuffles.
     */
    fun otherLibraries(
        counts: Map<String, Int>,
        current: Library,
        directory: LibraryDirectory,
    ): List<OtherLibrary> =
        counts.entries
            .groupBy({ it.key.uppercase() }, { it.value })
            .mapValues { (_, values) -> values.sum() }
            .mapNotNull { (code, books) ->
                val library = directory.byCode(code)
                if (library == null || books <= 0 || library.code.equals(current.code, ignoreCase = true)) {
                    null
                } else {
                    OtherLibrary(library, books, distanceMeters(current.position, library.position))
                }
            }.sortedWith(
                compareByDescending<OtherLibrary> { it.onShelf }
                    .thenBy { it.meters }
                    .thenBy { it.library.name }
                    .thenBy { it.library.code },
            )
}
