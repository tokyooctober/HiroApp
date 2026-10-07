package sg.hirokids.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/** Every NLB call goes through the proxy; the app never holds the NLB key (NFR-3, NFR-4). */
class ProxyApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /** Public libraries (type PL) from `GetBranches`. */
    suspend fun publicLibraries(): List<BranchDto> =
        client
            .get("${baseUrl.trimEnd('/')}/library/GetBranches") {
                parameter("ListType", "active")
                parameter("LibraryTypes", "PL")
            }.body<BranchesResponse>()
            .branches
}

@Serializable
data class BranchesResponse(
    val totalRecords: Int = 0,
    val branches: List<BranchDto> = emptyList(),
)

@Serializable
data class BranchDto(
    val branchCode: String,
    val branchName: String,
)
