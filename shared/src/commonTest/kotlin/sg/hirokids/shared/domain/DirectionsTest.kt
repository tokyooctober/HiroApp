package sg.hirokids.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectionsTest {
    private val tampines = Library("TRL", "Tampines Regional Library", LatLon(1.352391, 103.940821))

    @Test
    fun googleLinkNamesOnlyTheDestination() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=1.352391,103.940821", DirectionsLinks.google(tampines))
    }

    @Test
    fun appleLinkNamesOnlyTheDestination() {
        assertEquals("https://maps.apple.com/?daddr=1.352391,103.940821", DirectionsLinks.apple(tampines))
    }

    @Test
    fun coordinatesKeepSixDecimalsAndNeverUseAnExponent() {
        val odd = Library("X", "X", LatLon(1.3, 0.0000004))
        assertEquals("https://maps.apple.com/?daddr=1.300000,0.000000", DirectionsLinks.apple(odd))
        val south = Library("X", "X", LatLon(-1.5, -103.25))
        assertEquals("https://maps.apple.com/?daddr=-1.500000,-103.250000", DirectionsLinks.apple(south))
    }

    @Test
    fun theLinksWeBuildAreOnTheAllowList() {
        assertTrue(LinkAllowList.isAllowed(DirectionsLinks.google(tampines)))
        assertTrue(LinkAllowList.isAllowed(DirectionsLinks.apple(tampines)))
    }

    @Test
    fun otherSchemesAreRefused() {
        for (url in listOf(
            "http://www.google.com/maps/dir/?api=1&destination=1,1",
            "geo:1.35,103.94",
            "javascript:alert(1)",
            "intent://maps#Intent;scheme=https;end",
            "file:///etc/passwd",
            "tel:999",
            "market://details?id=x",
        )) {
            assertFalse(LinkAllowList.isAllowed(url), url)
        }
    }

    @Test
    fun otherHostsAndLookAlikesAreRefused() {
        for (url in listOf(
            "https://evil.example/maps/dir/?api=1",
            "https://www.google.com.evil.example/maps/dir/?api=1",
            "https://www.google.com@evil.example/maps/dir/",
            "https://evil.example/?u=https://www.google.com/maps/dir/",
            "https://www.google.com:8443/maps/dir/?api=1",
            "https://maps.apple.com.evil.example/?daddr=1,1",
            "https://notmaps.apple.com/?daddr=1,1",
            "https://google.com/maps/dir/?api=1",
        )) {
            assertFalse(LinkAllowList.isAllowed(url), url)
        }
    }

    @Test
    fun onlyTheDirectionsPageOfGoogleIsAllowed() {
        assertFalse(LinkAllowList.isAllowed("https://www.google.com/search?q=x"))
        assertFalse(LinkAllowList.isAllowed("https://www.google.com/maps/dir/../../search"))
        assertFalse(LinkAllowList.isAllowed("https://maps.apple.com/other?daddr=1,1"))
    }

    @Test
    fun blankAndSpacedLinksAreRefused() {
        assertFalse(LinkAllowList.isAllowed(""))
        assertFalse(LinkAllowList.isAllowed("https://maps.apple.com/?daddr=1,1 and more"))
        assertFalse(LinkAllowList.isAllowed(" https://maps.apple.com/?daddr=1,1"))
    }
}
