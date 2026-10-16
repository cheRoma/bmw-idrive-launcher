package online.k73.bmwlauncher.car

/**
 * Decides when starting the car should pull the launcher back in front of whatever the head unit
 * restored — YouTube, the navigator, Yandex Music — which is what it does on its own after a sleep.
 *
 * Pure, because the rules are all about *not* being annoying, and that is easier to prove here than
 * in a moving car:
 * - only the ignition **on** edge counts (a running engine must never yank the driver out of an app
 *   he opened himself);
 * - nothing happens when the launcher is already the screen in front;
 * - and a debounce, because the USB adapter blips off the bus on power dips and comes back with a
 *   fresh ignition reading, which would otherwise read as a second start.
 */
class IgnitionHomePolicy(private val debounceMs: Long = 5 * 60_000) {
    private var lastFiredMs: Long? = null

    /**
     * @param previous last known ignition state; null when we have never seen one (a cold boot,
     *        which *does* count as a start — that is the case the driver notices most).
     */
    fun shouldOpenHome(
        ignitionOn: Boolean,
        previous: Boolean?,
        nowMs: Long,
        launcherAlreadyInFront: Boolean,
    ): Boolean {
        if (!ignitionOn || previous == true) return false
        if (launcherAlreadyInFront) return false
        val last = lastFiredMs
        if (last != null && nowMs - last < debounceMs) return false
        lastFiredMs = nowMs
        return true
    }

    companion object {
        /**
         * The key going to OFF (from ACC or further) is when the head unit is about to sleep, and on
         * waking it restores whatever was in front at that moment: YouTube over the launcher after a
         * start, 29 s in on 2026-10-15. Going home here makes the launcher the thing it restores.
         * ACC alone does not count, so a video watched parked with the key in ACC keeps playing.
         */
        fun keyOffGoesHome(previous: KeyPosition?, now: KeyPosition): Boolean =
            now == KeyPosition.OFF && previous != null && previous != KeyPosition.OFF
    }
}
