package sg.hirokids.shared.domain

/** A library counts as "the one you are in" when it is at most this far from the phone (FR-3). */
const val DETECTION_RADIUS_METERS = 300.0

/** The libraries a user can choose from, with the rules for ordering them and detecting the current one. */
class LibraryDirectory(
    libraries: List<Library>,
) {
    /** By name, for the picker when no location is known. */
    val libraries: List<Library> = libraries.sortedWith(compareBy({ it.name }, { it.code }))

    fun byCode(code: String?): Library? = code?.let { wanted -> libraries.firstOrNull { it.code.equals(wanted, ignoreCase = true) } }

    /** Nearest first. Equal distances are ordered by name, then code, so the list never shuffles. */
    fun closestFirst(from: LatLon): List<LibraryDistance> =
        libraries
            .map { LibraryDistance(it, distanceMeters(from, it.position)) }
            .sortedWith(compareBy({ it.meters }, { it.library.name }, { it.library.code }))

    /** The nearest library when it is within [radiusMeters], otherwise nothing. */
    fun detect(
        from: LatLon,
        radiusMeters: Double = DETECTION_RADIUS_METERS,
    ): Library? = closestFirst(from).firstOrNull()?.takeIf { it.meters <= radiusMeters }?.library

    companion object {
        /**
         * Builds the list of selectable libraries:
         *  - only `open` seed entries that have a branch code;
         *  - when NLB's live list is available, a library it does not list is hidden, and the live position wins;
         *  - when the live list is unavailable or empty (offline, or a bad response) the open seed libraries are kept,
         *    so the picker still works.
         */
        fun from(
            seed: List<SeedLibrary>,
            live: List<LiveBranch>?,
        ): LibraryDirectory {
            val liveByCode = live?.takeIf { it.isNotEmpty() }?.associateBy { it.code.uppercase() }
            val chosen =
                seed
                    .filter { it.status == LibraryStatus.OPEN && !it.code.isNullOrBlank() }
                    .mapNotNull { entry ->
                        val code = entry.code!!.uppercase()
                        if (liveByCode == null) {
                            Library(code, entry.name, entry.position)
                        } else {
                            liveByCode[code]?.let { Library(code, entry.name, it.position ?: entry.position) }
                        }
                    }
            return LibraryDirectory(chosen)
        }
    }
}
