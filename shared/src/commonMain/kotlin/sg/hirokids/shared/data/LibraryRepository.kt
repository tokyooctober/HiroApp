package sg.hirokids.shared.data

import kotlinx.coroutines.CancellationException
import sg.hirokids.shared.domain.LibraryDirectory

/** Where the screens get the list of libraries a user can choose from. */
fun interface DirectorySource {
    suspend fun directory(): LibraryDirectory
}

/**
 * Bundled seed list plus NLB's live branch list (through the proxy). If the live list cannot be fetched the seed list
 * is used as is, so choosing a library still works offline. The result is kept for the life of the app.
 */
class LibraryRepository(
    private val seed: LibrarySeedSource,
    private val api: ProxyApi,
) : DirectorySource {
    private var cached: LibraryDirectory? = null

    override suspend fun directory(): LibraryDirectory = cached ?: build().also { cached = it }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun build(): LibraryDirectory {
        val seeds = parseSeed(seed.load())
        val live =
            try {
                api.libraryBranches()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // offline or a proxy error: fall back to the seed list
            }
        return LibraryDirectory.from(seeds, live)
    }
}
