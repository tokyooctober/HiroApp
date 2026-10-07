package sg.hirokids.shared.ui.library

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sg.hirokids.shared.data.ProxyApi
import sg.hirokids.shared.data.createHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryCountViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun api(failFirst: Boolean = false): ProxyApi {
        var calls = 0
        val engine =
            MockEngine {
                calls++
                if (failFirst && calls == 1) {
                    respondError(HttpStatusCode.ServiceUnavailable)
                } else {
                    respond(
                        """{"totalRecords":3,"branches":[""" +
                            """{"branchCode":"A","branchName":"A"},{"branchCode":"B","branchName":"B"},""" +
                            """{"branchCode":"C","branchName":"C"}]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        return ProxyApi(createHttpClient(engine), "http://proxy.test")
    }

    // The mock engine answers on a background thread, so wait for the state to settle instead of advancing virtual time.
    private suspend fun LibraryCountViewModel.settled() = state.first { it !is LibraryCountState.Loading }

    @Test
    fun startsLoadingThenShowsTheLibraryCount() =
        runTest(dispatcher) {
            val vm = LibraryCountViewModel(api())
            assertEquals(LibraryCountState.Loading, vm.state.value)
            assertEquals(LibraryCountState.Loaded(3), vm.settled())
        }

    @Test
    fun aFailureShowsTheErrorAndRetryRecovers() =
        runTest(dispatcher) {
            val vm = LibraryCountViewModel(api(failFirst = true))
            assertIs<LibraryCountState.Error>(vm.settled())
            vm.load()
            assertEquals(LibraryCountState.Loading, vm.state.value)
            assertEquals(LibraryCountState.Loaded(3), vm.settled())
        }
}
