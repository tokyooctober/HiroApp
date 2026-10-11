package sg.hirokids.shared.domain

/** What one library has of a book: copies on the shelf, all copies, and where to look for it. */
data class BranchCopies(
    val library: Library,
    val available: Int,
    val total: Int,
    /** As on the spine, e.g. `English 567.912 HEP`. Null when NLB sent none. */
    val callNumber: String?,
    /** NLB's name for the usage level, e.g. `Junior Picture Book`. */
    val shelfType: String?,
) {
    val onShelf: Boolean get() = available > 0
}

/** Another library's copies, with how far it is from the library the user is looking at. */
data class OtherBranchCopies(
    val copies: BranchCopies,
    val meters: Double,
)

/**
 * The detail screen's answer (FR-6, FR-15). [here] is null when the library does not hold the book at all; [others] lists only
 * libraries in the directory, those with copies on the shelf first, then the nearest.
 */
data class BookAvailability(
    val here: BranchCopies?,
    val others: List<OtherBranchCopies>,
) {
    companion object {
        /**
         * Builds the answer from [copies] that have already passed [AudiencePolicy]. Libraries the app does not show (closed, not
         * yet open, or not in NLB's live list) are left out, as in the search results.
         */
        fun from(
            copies: List<Copy>,
            current: Library,
            directory: LibraryDirectory,
        ): BookAvailability {
            val byBranch = copies.groupBy { it.branchCode.uppercase() }

            fun at(library: Library): BranchCopies? = byBranch[library.code.uppercase()]?.let { branchCopies(library, it) }

            val others =
                directory.libraries
                    .filterNot { it.code.equals(current.code, ignoreCase = true) }
                    .mapNotNull { library ->
                        at(library)?.let { OtherBranchCopies(it, distanceMeters(current.position, library.position)) }
                    }.sortedWith(
                        compareBy<OtherBranchCopies> { !it.copies.onShelf }
                            .thenBy { it.meters }
                            .thenBy { it.copies.library.name }
                            .thenBy { it.copies.library.code },
                    )
            return BookAvailability(here = at(current), others = others)
        }

        private fun branchCopies(
            library: Library,
            copies: List<Copy>,
        ): BranchCopies {
            // the copy a child would pick up tells where to look; if none is on the shelf, any copy tells the call number
            val pointer = copies.firstOrNull { it.status == CopyStatus.AVAILABLE } ?: copies.first()
            return BranchCopies(
                library = library,
                available = copies.count { it.status == CopyStatus.AVAILABLE },
                total = copies.size,
                callNumber = pointer.formattedCallNumber ?: pointer.callNumber,
                shelfType = pointer.usageLevelName ?: pointer.usageLevel,
            )
        }
    }
}
