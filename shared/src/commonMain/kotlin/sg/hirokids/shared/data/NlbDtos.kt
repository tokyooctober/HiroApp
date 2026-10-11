package sg.hirokids.shared.data

import kotlinx.serialization.Serializable
import sg.hirokids.shared.domain.Copy
import sg.hirokids.shared.domain.CopyStatus
import sg.hirokids.shared.domain.Title

/** Shapes of `SearchTitles` and `GetTitles` as recorded in `fixtures/`. Unknown fields are ignored. */
@Serializable
data class SearchTitlesResponse(
    val totalRecords: Int = 0,
    val hasMoreRecords: Boolean = false,
    val nextRecordsOffset: Int = 0,
    val titles: List<TitleGroupDto> = emptyList(),
    val facets: List<FacetDto> = emptyList(),
)

/** One facet of a `SearchTitles` response, for example `location`: how many of the matching books each value has. */
@Serializable
data class FacetDto(
    val id: String = "",
    val values: List<FacetValueDto> = emptyList(),
)

@Serializable
data class FacetValueDto(
    val id: String = "",
    val count: Int = 0,
)

/** `GetTitles` returns the records themselves, not groups of them. */
@Serializable
data class GetTitlesResponse(
    val totalRecords: Int = 0,
    val hasMoreRecords: Boolean = false,
    val nextRecordsOffset: Int = 0,
    val titles: List<RecordDto> = emptyList(),
)

@Serializable
data class TitleGroupDto(
    val title: String = "",
    val author: String? = null,
    val coverUrl: CoverDto? = null,
    val records: List<RecordDto> = emptyList(),
)

@Serializable
data class CoverDto(
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
data class FormatDto(
    val code: String = "",
    val name: String = "",
)

@Serializable
data class RecordDto(
    val brn: Long,
    val title: String? = null,
    val author: String? = null,
    val coverUrl: CoverDto? = null,
    val isbns: List<String> = emptyList(),
    val format: FormatDto? = null,
    val subjects: List<String> = emptyList(),
    val audience: List<String> = emptyList(),
    val audienceImda: List<String> = emptyList(),
    val isRestricted: Boolean? = null,
    val activeReservationsCount: Int = 0,
    val language: List<String> = emptyList(),
    /** Only `GetTitleDetails` sends it. */
    val summary: List<String> = emptyList(),
)

private const val COVER_BASE = "https://eservice.nlb.gov.sg/bookcoverwrapper/cover/"

/** Fills a title from a record and, for `SearchTitles`, from the group it came in. A missing restricted flag counts as restricted. */
internal fun RecordDto.toTitle(group: TitleGroupDto? = null): Title {
    val isbn = isbns.firstOrNull()
    val cover = group?.coverUrl ?: coverUrl
    return Title(
        brn = brn,
        isbn = isbn,
        // GetTitles sends the whole title statement ("Dinosaur / written by ..."); the card wants the part before the slash
        title = (group?.title ?: title.orEmpty()).substringBefore(" / ").trim(),
        author = group?.author ?: author,
        coverUrl = cover?.medium ?: isbn?.let { "$COVER_BASE$it?s=MD" },
        subjects = subjects,
        audience = audience,
        audienceImda = audienceImda,
        isRestricted = isRestricted ?: true,
        reservations = activeReservationsCount,
        format = format?.name?.takeIf { it.isNotBlank() },
        languages = language.filter { it.isNotBlank() },
        summary = summary.joinToString(" ").trim().takeIf { it.isNotEmpty() },
    )
}

/** `GetAvailabilityInfo`: one row per physical copy, at every branch. */
@Serializable
data class AvailabilityResponse(
    val totalRecords: Int = 0,
    val hasMoreRecords: Boolean = false,
    val nextRecordsOffset: Int = 0,
    val items: List<ItemDto> = emptyList(),
)

@Serializable
data class CodeNameDto(
    val code: String = "",
    val name: String = "",
)

@Serializable
data class ItemDto(
    val brn: Long,
    val callNumber: String? = null,
    val formattedCallNumber: String? = null,
    val media: CodeNameDto? = null,
    val usageLevel: CodeNameDto? = null,
    val location: CodeNameDto? = null,
    val status: CodeNameDto? = null,
    val transactionStatus: CodeNameDto? = null,
    val minAgeLimit: Int? = null,
) {
    /**
     * A copy with no branch or usage level cannot be placed, so it is dropped (null). Only a copy NLB says is in (`In`) and available
     * (`S`) is on the shelf: one still being shelved (`Shelving`) is not there to pick up yet.
     */
    fun toCopy(): Copy? {
        val branch = location?.code?.takeIf { it.isNotBlank() } ?: return null
        val usage = usageLevel?.code?.takeIf { it.isNotBlank() } ?: return null
        return Copy(
            brn = brn,
            branchCode = branch.uppercase(),
            usageLevel = usage,
            status = copyStatus(),
            callNumber = callNumber?.takeIf { it.isNotBlank() },
            minAgeLimit = minAgeLimit,
            media = media?.code?.takeIf { it.isNotBlank() },
            usageLevelName = usageLevel.name.takeIf { it.isNotBlank() },
            formattedCallNumber = formattedCallNumber?.takeIf { it.isNotBlank() },
        )
    }

    private fun copyStatus(): CopyStatus =
        when {
            transactionStatus?.code == "C" -> CopyStatus.ON_LOAN
            transactionStatus?.code == "S" && status?.code.equals("In", ignoreCase = true) -> CopyStatus.AVAILABLE
            else -> CopyStatus.IN_PROCESS // shelving, in transit, on hold, or a code we do not know: not there to pick up
        }
}
