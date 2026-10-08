package sg.hirokids.shared.data

import com.russhwolf.settings.Settings

/** The library the user chose, kept on the phone so it survives a restart. Only the branch code is stored. */
class CurrentLibraryStore(
    private val settings: Settings,
) {
    var code: String?
        get() = settings.getStringOrNull(KEY)
        set(value) {
            if (value == null) settings.remove(KEY) else settings.putString(KEY, value)
        }

    fun clear() {
        code = null
    }

    private companion object {
        const val KEY = "currentLibraryCode"
    }
}
