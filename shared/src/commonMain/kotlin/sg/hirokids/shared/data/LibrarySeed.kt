package sg.hirokids.shared.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.LibraryStatus
import sg.hirokids.shared.domain.SeedLibrary
import sg.hirokids.shared.resources.Res

/** The text of `data/nlb_libraries.json`. A seam so tests can supply their own. */
fun interface LibrarySeedSource {
    suspend fun load(): String
}

/** Reads the bundled copy (Gradle copies `data/nlb_libraries.json` into the app's resources). */
class ResourceSeedSource : LibrarySeedSource {
    override suspend fun load(): String = Res.readBytes("files/nlb_libraries.json").decodeToString()
}

@Serializable
private data class SeedFileDto(
    val libraries: List<SeedLibraryDto> = emptyList(),
)

@Serializable
private data class SeedLibraryDto(
    val branchCode: String? = null,
    val name: String,
    val lat: Double,
    val lng: Double,
    val status: String = "",
)

private val seedJson = Json { ignoreUnknownKeys = true }

fun parseSeed(json: String): List<SeedLibrary> =
    seedJson.decodeFromString<SeedFileDto>(json).libraries.map {
        SeedLibrary(it.branchCode, it.name, LatLon(it.lat, it.lng), LibraryStatus.parse(it.status))
    }
