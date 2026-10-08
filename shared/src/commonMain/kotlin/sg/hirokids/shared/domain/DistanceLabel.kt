package sg.hirokids.shared.domain

/** A distance ready for the screen: the number and whether it is in kilometres (else metres). Words come from resources. */
data class DistanceLabel(
    val text: String,
    val kilometres: Boolean,
)

private const val ONE_KILOMETRE = 1_000
private const val ROUND_TO_METERS = 10
private const val METERS_PER_TENTH_OF_A_KILOMETRE = 100.0
private const val DECIMAL_BASE = 10
private const val HALF = 0.5

private fun roundHalfUp(value: Double): Int = (value + HALF).toInt()

/** Metres rounded to the nearest 10 below a kilometre; kilometres to one decimal from there. */
fun distanceLabel(meters: Double): DistanceLabel {
    val nearestTen = roundHalfUp(meters / ROUND_TO_METERS) * ROUND_TO_METERS
    if (nearestTen < ONE_KILOMETRE) return DistanceLabel(nearestTen.toString(), kilometres = false)
    val tenths = roundHalfUp(meters / METERS_PER_TENTH_OF_A_KILOMETRE)
    return DistanceLabel("${tenths / DECIMAL_BASE}.${tenths % DECIMAL_BASE}", kilometres = true)
}
