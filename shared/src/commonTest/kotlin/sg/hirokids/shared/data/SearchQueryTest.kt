package sg.hirokids.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchQueryTest {
    @Test
    fun aQueryNeedsAtLeastTwoCharacters() {
        assertNull(parseSearchQuery(""))
        assertNull(parseSearchQuery("   "))
        assertNull(parseSearchQuery("d"))
        assertNull(parseSearchQuery("  d  "))
        assertEquals(SearchQuery.Keywords("dr"), parseSearchQuery("dr"))
    }

    @Test
    fun keywordsAreTrimmedAndSpacesCollapsed() {
        assertEquals(SearchQuery.Keywords("dinosaur eggs"), parseSearchQuery("  dinosaur    eggs "))
    }

    @Test
    fun aThirteenDigitNumberIsAnIsbn() {
        assertEquals(SearchQuery.Isbn("9781801041850"), parseSearchQuery("9781801041850"))
    }

    @Test
    fun aTenDigitNumberIsAnIsbnAndATrailingXIsAllowed() {
        assertEquals(SearchQuery.Isbn("1801041857"), parseSearchQuery("1801041857"))
        assertEquals(SearchQuery.Isbn("080442957X"), parseSearchQuery("080442957x"))
    }

    @Test
    fun hyphensAndSpacesInsideAnIsbnAreIgnored() {
        assertEquals(SearchQuery.Isbn("9781801041850"), parseSearchQuery("978-1-80104-185-0"))
        assertEquals(SearchQuery.Isbn("1801041857"), parseSearchQuery(" 1 80104 185 7 "))
    }

    @Test
    fun otherNumbersAreSearchedAsWords() {
        assertEquals(SearchQuery.Keywords("123456789"), parseSearchQuery("123456789")) // nine digits
        assertEquals(SearchQuery.Keywords("12345678901"), parseSearchQuery("12345678901")) // eleven digits
        assertEquals(SearchQuery.Keywords("1801041857 dinosaur"), parseSearchQuery("1801041857 dinosaur"))
        assertEquals(SearchQuery.Keywords("X801041857"), parseSearchQuery("X801041857")) // X only at the end
    }
}
