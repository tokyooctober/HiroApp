package sg.hirokids.shared.domain

/** The app runs in exactly one mode at a time (spec section 4). */
enum class Mode {
    CHILDREN,
    ADULT,
}

/**
 * One catalogue record, identified by its BRN. A `SearchTitles` result groups several records under one title; the audience
 * check runs on each record. Fields the screens do not need yet (summary, publisher, copies) arrive with the detail screen in Task 10.
 */
data class Title(
    val brn: Long,
    val isbn: String?,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val subjects: List<String>,
    /** MARC 521 text such as `7+.` or `Key Stage 2.`. Sparse: present on few children's records. */
    val audience: List<String>,
    /** IMDA classification text. Never seen in the recorded data yet. */
    val audienceImda: List<String>,
    val isRestricted: Boolean,
    val reservations: Int,
    /** NLB's format name, e.g. `Book` or `Ebook`. */
    val format: String? = null,
)

enum class CopyStatus {
    AVAILABLE,
    ON_LOAN,
    RESERVED,
    IN_TRANSIT,
    IN_PROCESS,
    MISSING,
}

/** One physical copy from `GetAvailabilityInfo`. */
data class Copy(
    val brn: Long,
    val branchCode: String,
    /** C004 usage level code as NLB sends it, e.g. `JUNIOR PB`. */
    val usageLevel: String,
    val status: CopyStatus,
    val callNumber: String?,
    val minAgeLimit: Int?,
    /** Media code or name, e.g. `BOOK`. Scanned for ratings such as `M18`. */
    val media: String?,
)
