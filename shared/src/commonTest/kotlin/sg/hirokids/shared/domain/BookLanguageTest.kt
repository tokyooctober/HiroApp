package sg.hirokids.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookLanguageTest {
    private fun title(vararg languages: String) =
        Title(
            brn = 1,
            isbn = null,
            title = "T",
            author = null,
            coverUrl = null,
            subjects = emptyList(),
            audience = emptyList(),
            audienceImda = emptyList(),
            isRestricted = false,
            reservations = 0,
            languages = languages.toList(),
        )

    @Test
    fun englishIsTheStartingChoice() {
        assertEquals(BookLanguage.ENGLISH, BookLanguage.DEFAULT)
        assertEquals(BookLanguage.ENGLISH, BookLanguage.parse(null))
        assertEquals(BookLanguage.ENGLISH, BookLanguage.parse("klingon"))
    }

    @Test
    fun aSavedChoiceIsReadBack() {
        for (language in BookLanguage.entries) assertEquals(language, BookLanguage.parse(language.name))
    }

    @Test
    fun nlbsCodesAreTheOnesTheFacetUses() {
        assertEquals(listOf(null, "eng", "chi", "may", "tam"), BookLanguage.entries.map { it.facetId })
    }

    @Test
    fun aLanguageAllowsTitlesInItAndTitlesWithNoRecordedLanguage() {
        assertTrue(BookLanguage.CHINESE.allows(title("Chinese")))
        assertTrue(BookLanguage.CHINESE.allows(title("Chinese", "English"))) // bilingual
        assertTrue(BookLanguage.CHINESE.allows(title())) // not recorded: not hidden
        assertFalse(BookLanguage.CHINESE.allows(title("English")))
        assertTrue(BookLanguage.ALL.allows(title("Indonesian")))
    }
}
