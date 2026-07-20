package online.k73.bmwlauncher.car

/**
 * Accumulates trip **average speed** from a stream of speed samples, time-weighted:
 *   average = total distance / total time = ∫ speed·dt / ∫ dt.
 *
 * This includes stops — a speed=0 sample adds time but no distance, so idling at a light lowers the
 * average, exactly like the BMW on-board computer's «средняя скорость». Pure & unit-testable; fed by
 * [IBusReader]. In-memory only (resets if the launcher process restarts) — persistence can be added
 * later. [tripKm] (distance since reset) is a free by-product, kept for a future «пробег» readout.
 */
class TripStats {
    private var distanceKm = 0.0
    private var timeH = 0.0
    private var lastMs = -1L // -1 = no prior sample yet (0 is a valid timestamp)

    /**
     * Feed one speed sample. Intervals longer than [maxGapMs] (ignition off / adapter silent) are
     * skipped so a parked gap between drives doesn't distort the running average.
     */
    fun onSpeed(speedKmh: Int, nowMs: Long, maxGapMs: Long = 5_000L) {
        val prev = lastMs
        lastMs = nowMs
        if (prev < 0L) return
        val dtMs = nowMs - prev
        if (dtMs <= 0L || dtMs > maxGapMs) return
        val dtH = dtMs / 3_600_000.0
        distanceKm += speedKmh.coerceAtLeast(0) * dtH
        timeH += dtH
    }

    /** true once at least one interval has been accumulated (so the UI can show "—" before that). */
    val hasData: Boolean get() = timeH > 0.0
    val averageKmh: Double get() = if (timeH > 0.0) distanceKm / timeH else 0.0
    val tripKm: Double get() = distanceKm

    fun reset() {
        distanceKm = 0.0
        timeH = 0.0
        lastMs = -1L
    }
}
