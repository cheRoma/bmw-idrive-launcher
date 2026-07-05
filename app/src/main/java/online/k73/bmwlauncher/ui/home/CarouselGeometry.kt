package online.k73.bmwlauncher.ui.home

import kotlin.math.abs
import kotlin.math.sign

/** How a carousel card looks given its distance (in pages) from the settled center. */
data class TileTransform(
    val rotationYDeg: Float,
    val scale: Float,
    val alpha: Float,
    val translationXFraction: Float, // unused for spacing now (the pager owns spacing); kept for API compat
)

/**
 * Pure driver of the cylindrical 3D look, matching the Claude Design handoff
 * (docs/mockups/claude-design/screen_1j.png). Discrete per-position "stops" define how a tile
 * looks at integer distance |offset| = 0,1,2,3 from the settled center; fractional offsets
 * linearly interpolate between adjacent stops; |offset| >= 3 clamps to the 3-stop.
 *
 * Stop arrays are exposed as tunable [val]s so the feel can be re-shipped (e.g. OTA) without
 * touching Compose. pageOffset 0 = centered; a positive offset (tile to the right of center)
 * turns negative, matching the launcher's sign convention.
 */
object CarouselGeometry {
    /** Scale relative to the 203dp focus tile: 1.0, 160/203, 140/203, 127/203. */
    val SCALES = floatArrayOf(1f, 0.7882f, 0.6897f, 0.6256f)

    /** rotationY magnitude in degrees at each stop. */
    val ROTATIONS = floatArrayOf(0f, 26f, 38f, 48f)

    /** Alpha (opacity) at each stop. */
    val ALPHAS = floatArrayOf(1f, 0.55f, 0.32f, 0.16f)

    private val LAST = SCALES.size - 1 // == 3

    fun transformFor(pageOffset: Float): TileTransform {
        val dist = abs(pageOffset)
        val scale = interp(SCALES, dist)
        val alpha = interp(ALPHAS, dist)
        val rotMag = interp(ROTATIONS, dist)
        // Right-side neighbour (positive offset) turns negative.
        val rotationYDeg = -sign(pageOffset) * rotMag
        return TileTransform(
            rotationYDeg = rotationYDeg,
            scale = scale,
            alpha = alpha,
            translationXFraction = 0f,
        )
    }

    /** Linear interpolation across the stop array by absolute distance, clamped at the last stop. */
    private fun interp(stops: FloatArray, dist: Float): Float {
        if (dist <= 0f) return stops[0]
        if (dist >= LAST) return stops[LAST]
        val lo = dist.toInt()
        val hi = lo + 1
        val f = dist - lo
        return stops[lo] + (stops[hi] - stops[lo]) * f
    }
}
