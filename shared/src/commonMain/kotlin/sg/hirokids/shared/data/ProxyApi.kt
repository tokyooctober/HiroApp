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
}

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
