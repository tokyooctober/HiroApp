package sg.hirokids.shared.ui.library

import sg.hirokids.shared.data.DirectorySource
import sg.hirokids.shared.domain.LatLon
import sg.hirokids.shared.domain.LibraryDirectory
import sg.hirokids.shared.domain.LibraryStatus
import sg.hirokids.shared.domain.LocationProvider
import sg.hirokids.shared.domain.LocationResult
import sg.hirokids.shared.domain.SeedLibrary
import sg.hirokids.shared.domain.northBy

/** Records how the view model asked for the location and answers with a scripted result. */
class FakeLocationProvider(
    var result: LocationResult = LocationResult.Denied,
) : LocationProvider {
    val prompts = mutableListOf<Boolean>()

    override suspend fun locate(allowPrompt: Boolean): LocationResult {
        prompts += allowPrompt
        return result
    }
}

class FakeDirectorySource(
    private val directory: LibraryDirectory,
) : DirectorySource {
    override suspend fun directory(): LibraryDirectory = directory
}

/** Three libraries at known spots: Tampines, 3 km north (Pasir Ris stand-in) and 9 km north (Punggol stand-in). */
object Spots {
    val tampines = LatLon(1.352391, 103.940821)
    val pasirRis = tampines.northBy(3_100.0)
    val punggol = tampines.northBy(9_000.0)

    fun seeds(): List<SeedLibrary> =
        listOf(
            SeedLibrary("PRL", "Punggol Regional Library", punggol, LibraryStatus.OPEN),
            SeedLibrary("TRL", "Tampines Regional Library", tampines, LibraryStatus.OPEN),
            SeedLibrary("PRPL", "Pasir Ris Public Library", pasirRis, LibraryStatus.OPEN),
            SeedLibrary("AMKPL", "Ang Mo Kio Public Library", tampines.northBy(6_000.0), LibraryStatus.UPCOMING),
            SeedLibrary("QUPL", "Queenstown Public Library", tampines.northBy(1_000.0), LibraryStatus.CLOSED),
        )

    fun directory(): LibraryDirectory = LibraryDirectory.from(seeds(), live = null)
}
