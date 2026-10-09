package sg.hirokids.shared.domain

/** Why [AudiencePolicy] hid a record or a copy. Used by tests and the hide-rate report; the UI only needs "hidden". */
enum class HideReason {
    /** The Adult column of the policy is built in Task 12; until then that mode shows nothing. */
    MODE_NOT_AVAILABLE,
    RESTRICTED,
    RATED_MEDIA,

    /** The audience text names teens or adults, or an age above the children's band. */
    OLDER_AUDIENCE,

    /** Neither a children's audience text nor a "Juvenile" subject heading: NLB's own data does not say it is for children. */
    NO_CHILDREN_MARKER,
    MIN_AGE_LIMIT,
    NOT_A_CHILDREN_SHELF,

    /** The record passes but none of its copies do, or none were listed. */
    NO_COUNTABLE_COPY,
}

/**
 * The client-side gate of spec section 4 (layer 2): decides whether a record or a copy may be shown in [mode].
 * Pure common Kotlin with no platform dependencies. Anything ambiguous is hidden.
 *
 * Children mode today; the teen toggle (FR-13) is off, so teen markers and the `TEEN` shelf are hidden. Adult mode comes in Task 12.
 */
class AudiencePolicy(
    private val mode: Mode,
) {
    /** Why [title] must not be shown, or null if its audience fields allow it. Use this on `SearchTitles` results (no copies yet). */
    fun hideReason(title: Title): HideReason? =
        when (mode) {
            Mode.CHILDREN -> childrenTitleReason(title)
            Mode.ADULT -> HideReason.MODE_NOT_AVAILABLE
        }

    /** Why [copy] must not be counted or listed, or null if it may be. */
    fun hideReason(copy: Copy): HideReason? =
        when (mode) {
            Mode.CHILDREN -> childrenCopyReason(copy)
            Mode.ADULT -> HideReason.MODE_NOT_AVAILABLE
        }

    /** As [hideReason] for a title, and also hidden when none of its [copies] may be shown. Use this once copies are known. */
    fun hideReason(
        title: Title,
        copies: List<Copy>,
    ): HideReason? = hideReason(title) ?: if (copies.any(::allows)) null else HideReason.NO_COUNTABLE_COPY

    fun allows(title: Title): Boolean = hideReason(title) == null

    fun allows(copy: Copy): Boolean = hideReason(copy) == null

    /** The copies that count in this mode: a title with junior and adult copies counts only the ones for this mode. */
    fun countableCopies(copies: List<Copy>): List<Copy> = copies.filter(::allows)

    private fun childrenTitleReason(title: Title): HideReason? {
        val audienceTexts = title.audience + title.audienceImda
        val bands = audienceTexts.map(AudienceText::band)
        return when {
            title.isRestricted -> HideReason.RESTRICTED
            audienceTexts.any(AudienceText::isRated) -> HideReason.RATED_MEDIA
            AudienceBand.OLDER in bands -> HideReason.OLDER_AUDIENCE
            AudienceBand.CHILD in bands || title.subjects.any(AudienceText::isJuvenileSubject) -> null
            else -> HideReason.NO_CHILDREN_MARKER
        }
    }

    private fun childrenCopyReason(copy: Copy): HideReason? =
        when {
            AudienceText.normaliseUsageLevel(copy.usageLevel) !in CHILDREN_USAGE_LEVELS -> HideReason.NOT_A_CHILDREN_SHELF
            (copy.minAgeLimit ?: 0) > 0 -> HideReason.MIN_AGE_LIMIT
            copy.media?.let(AudienceText::isRated) == true -> HideReason.RATED_MEDIA
            else -> null
        }

    private companion object {
        /**
         * Shelves whose copies are for children. Seen in the recorded data: `JUNIOR PB`, `ELL 0-3`, `ELL 4-6`, `JUNIOR RSING`,
         * `JUNIOR SING`; `JUNIOR` and `RACL` come from spec section 4. `ATT` (accompanying item) and `TEEN` are left out on purpose.
         * An exact list, not a prefix: a shelf NLB adds later stays hidden until someone has looked at it.
         */
        val CHILDREN_USAGE_LEVELS = setOf("JUNIOR", "JUNIOR PB", "JUNIOR RSING", "JUNIOR SING", "RACL", "ELL 0-3", "ELL 4-6")
    }
}
