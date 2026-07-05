package online.k73.bmwlauncher.ui.home

/** How a carousel card looks given its distance (in pages) from the settled center. */
data class TileTransform(
    val rotationYDeg: Float,
    val scale: Float,
    val alpha: Float,
    val translationXFraction: Float, // fraction of card width; horizontal spread applied to side cards
)

/**
 * Pure driver of the cylindrical 3D look. Tunable constants live here so the feel can be adjusted
 * (and re-shipped via OTA) without touching Compose. pageOffset 0 = centered; ±1 = one card away.
 */
object CarouselGeometry {
    const val MAX_ROTATION = 55f   // degrees at ±1 card
    const val MIN_SCALE = 0.72f    // side card scale
    const val MIN_ALPHA = 0.45f    // side card opacity
    const val TRANSLATION_PULL = 0.10f

    fun transformFor(pageOffset: Float): TileTransform {
        val clamped = pageOffset.coerceIn(-1f, 1f)
        val t = kotlin.math.abs(clamped)
        return TileTransform(
            rotationYDeg = -clamped * MAX_ROTATION,
            scale = 1f - t * (1f - MIN_SCALE),
            alpha = 1f - t * (1f - MIN_ALPHA),
            translationXFraction = -clamped * TRANSLATION_PULL,
        )
    }
}
