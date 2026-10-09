package sg.hirokids.shared.data

import kotlinx.serialization.Serializable
import sg.hirokids.shared.domain.Title

/** Shapes of `SearchTitles` and `GetTitles` as recorded in `fixtures/`. Unknown fields are ignored. */
@Serializable
data class SearchTitlesResponse(
    val totalRecords: Int = 0,
    val hasMoreRecords: Boolean = false,
    val nextRecordsOffset: Int = 0,
    val titles: List<TitleGroupDto> = emptyList(),
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
    )
}
