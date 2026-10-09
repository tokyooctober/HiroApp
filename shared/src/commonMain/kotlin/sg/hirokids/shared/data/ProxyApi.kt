package sg.hirokids.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.LiveBranch

/** Every NLB call goes through the proxy; the app never holds the NLB key (NFR-3, NFR-4). */
class ProxyApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /**
     * Public (PL) and regional (RL) libraries from `GetBranches`. Regional libraries, such as Tampines and Jurong,
     * are a separate type, so asking for PL alone would leave them out. Only branch data is requested: nothing about the user.
     */
    suspend fun libraryBranches(): List<LiveBranch> =
        client
            .get("${baseUrl.trimEnd('/')}/library/GetBranches") {
                parameter("ListType", "active")
                parameter("LibraryTypes", "PL,RL")
            }.body<BranchesResponse>()
            .branches
            .map { it.toLiveBranch() }

    /** `SearchTitles` (FR-1). The request holds only what to look for and a branch code, nothing about the user. */
    suspend fun searchTitles(request: SearchTitlesRequest): SearchTitlesResponse =
        client
            .get("${baseUrl.trimEnd('/')}/catalogue/SearchTitles") {
                parameter("Keywords", request.keywords)
                parameter("IntendedAudiences", request.audience)
                parameter("MaterialTypes", "bks")
                request.location?.let { parameter("Locations", it) }
                if (request.availableOnly) parameter("Availability", "true")
                parameter("Limit", request.limit)
                parameter("Offset", request.offset)
            }.body()

    /** `GetTitles` by ISBN: no audience parameter exists, so callers must run the audience policy on the result. */
    suspend fun titlesByIsbn(
        isbn: String,
        offset: Int = 0,
    ): GetTitlesResponse =
        client
            .get("${baseUrl.trimEnd('/')}/catalogue/GetTitles") {
                parameter("ISBN", isbn)
                parameter("Offset", offset)
            }.body()
}

/** NLB pages hold up to 20 titles here (spec section 5). */
const val SEARCH_PAGE_SIZE = 20

data class SearchTitlesRequest(
    val keywords: String,
    val audience: String,
    val location: String?,
    val availableOnly: Boolean,
    val limit: Int = SEARCH_PAGE_SIZE,
    val offset: Int = 0,
)

@Serializable
data class BranchesResponse(
    val totalRecords: Int = 0,
    val branches: List<BranchDto> = emptyList(),
)

@Serializable
data class BranchDto(
    val branchCode: String,
    val branchName: String = "",
    val branchType: BranchTypeDto? = null,
    val coordinates: CoordinatesDto? = null,
) {
    fun toLiveBranch(): LiveBranch = LiveBranch(branchCode, coordinates?.toLatLon())
}

@Serializable
data class BranchTypeDto(
    val code: String = "",
)

private val NO_POSITION = LatLon(0.0, 0.0)

/** NLB sends coordinates as text, and uses empty text or 0,0 for "none" (for example the Mobile Library). */
@Serializable
data class CoordinatesDto(
    val geoLatitude: String? = null,
    val geoLongitude: String? = null,
) {
    fun toLatLon(): LatLon? {
        val lat = geoLatitude?.toDoubleOrNull()
        val lon = geoLongitude?.toDoubleOrNull()
        val position = if (lat != null && lon != null) LatLon(lat, lon) else null
        return position?.takeUnless { it == NO_POSITION }
    }
}
