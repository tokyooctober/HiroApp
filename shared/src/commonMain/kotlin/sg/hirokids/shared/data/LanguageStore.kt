package sg.hirokids.shared.data

import com.russhwolf.settings.Settings
import sg.hirokids.shared.domain.BookLanguage

/** The language filter the user last chose, kept on the phone. English until they choose another (owner decision, 11 Oct 2026). */
class LanguageStore(
    private val settings: Settings,
) {
    var language: BookLanguage
        get() = BookLanguage.parse(settings.getStringOrNull(KEY))
        set(value) = settings.putString(KEY, value.name)

    private companion object {
        const val KEY = "searchLanguage"
    }
}
