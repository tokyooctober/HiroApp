package sg.hirokids.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceLabelTest {
    @Test
    fun underAKilometreIsShownInMetresRoundedToTen() {
        assertEquals(DistanceLabel("0", kilometres = false), distanceLabel(4.0))
        assertEquals(DistanceLabel("40", kilometres = false), distanceLabel(43.0))
        assertEquals(DistanceLabel("50", kilometres = false), distanceLabel(45.0))
        assertEquals(DistanceLabel("990", kilometres = false), distanceLabel(994.0))
    }

    @Test
    fun aKilometreOrMoreIsShownInKilometresToOneDecimal() {
        assertEquals(DistanceLabel("1.0", kilometres = true), distanceLabel(996.0)) // rounds up to 1000 m
        assertEquals(DistanceLabel("3.1", kilometres = true), distanceLabel(3_140.0))
        assertEquals(DistanceLabel("9.0", kilometres = true), distanceLabel(8_960.0))
        assertEquals(DistanceLabel("12.5", kilometres = true), distanceLabel(12_500.0))
    }
}
