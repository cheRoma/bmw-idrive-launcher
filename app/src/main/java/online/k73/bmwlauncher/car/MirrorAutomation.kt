package online.k73.bmwlauncher.car

/** What the automation wants done with the mirrors right now. */
enum class MirrorAction { FOLD, UNFOLD }

/**
 * Decides whether a change of key position should move the mirrors. Pure on purpose: every guard
 * below is a rule we can only test on a bench, never in the car — debugging this by switching off
 * a real engine would be miserable.
 */
object MirrorAutomation {

    /**
     * @param prev last key position we saw, null if we have not seen one yet
     * @param now current key position
     * @param enabled the user's setting
     * @param speedKmh last known speed; null means we never decoded one
     */
    fun decide(prev: KeyPosition?, now: KeyPosition?, enabled: Boolean, speedKmh: Int?): MirrorAction? {
        if (!enabled) return null
        if (now == null) return null
        // No previous reading means the app just started (or just connected to the bus). Seeing
        // "key is off" then is not the same as watching it turn off, and folding on launch would
        // fight whatever the driver did by hand.
        if (prev == null) return null
        if (prev == now) return null    // act on the edge, not the level — else we'd fire ~4x/second

        val moving = speedKmh != null && speedKmh > 0
        return when {
            // Mirrors folding while the car is moving is the one outcome that must never happen,
            // so a stale non-zero speed vetoes the fold. Unfolding is always safe.
            now == KeyPosition.OFF && !moving -> MirrorAction.FOLD
            now == KeyPosition.IGNITION || now == KeyPosition.START -> {
                if (prev == KeyPosition.IGNITION || prev == KeyPosition.START) null else MirrorAction.UNFOLD
            }
            else -> null
        }
    }
}
