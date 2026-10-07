package sg.hirokids.shared

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** multiplatform-settings is wired for later tasks (current library, mode); this checks the in-memory test double. */
class SettingsStoreTest {
    @Test
    fun aStoredValueIsReadBackAndAMissingOneIsNull() {
        val settings = MapSettings()
        assertNull(settings.getStringOrNull("currentLibrary"))
        settings.putString("currentLibrary", "TRL")
        assertEquals("TRL", settings.getStringOrNull("currentLibrary"))
    }
}
