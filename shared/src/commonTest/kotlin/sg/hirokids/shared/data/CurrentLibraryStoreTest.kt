package sg.hirokids.shared.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentLibraryStoreTest {
    @Test
    fun startsEmpty() {
        assertNull(CurrentLibraryStore(MapSettings()).code)
    }

    @Test
    fun theChoiceSurvivesARestart() {
        val settings = MapSettings() // stands in for the on-disk store that outlives the app process
        CurrentLibraryStore(settings).code = "TRL"
        assertEquals("TRL", CurrentLibraryStore(settings).code) // a new store, as after a restart
    }

    @Test
    fun clearingForgetsTheChoice() {
        val store = CurrentLibraryStore(MapSettings())
        store.code = "TRL"
        store.clear()
        assertNull(store.code)
    }
}
