package online.k73.bmwlauncher.car

import java.util.Locale

/** One finished drive. Distance is integrated from the IKE speed stream (the odometer only arrives
 *  when the cluster wakes up, so it cannot mark where a trip ended). */
data class Trip(
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Int,
    val startOdometerKm: Int? = null,
    /** The cluster's «расход 1» text at the end of the trip, e.g. "18.2 L/100". */
    val consumption: String? = null,
) {
    val durationMin: Long get() = ((endMs - startMs) / 60_000L).coerceAtLeast(0)
    val avgKmh: Int get() {
        val h = (endMs - startMs) / 3_600_000.0
        return if (h > 0) (distanceKm / h).toInt() else 0
    }
}

/**
 * Splits the speed stream into trips. A trip starts on the first moving sample and ends when the
 * key goes off or the cluster has been silent for [idleEndMs] (the adapter loses power with the
 * ignition, so silence is the common ending). Stops at lights stay inside the trip. Pure.
 */
class TripTracker(
    private val minKm: Double = 0.3,
    private val idleEndMs: Long = 120_000L,
    private val maxGapMs: Long = 5_000L,
) {
    private var startMs = -1L
    private var lastMs = -1L
    private var distanceKm = 0.0
    private var maxSpeed = 0
    private var startOdo: Int? = null

    val active: Boolean get() = startMs >= 0

    fun onSpeed(speedKmh: Int, nowMs: Long, odometerKm: Int?) {
        if (!active) {
            if (speedKmh <= 0) return
            startMs = nowMs; lastMs = nowMs; distanceKm = 0.0; maxSpeed = speedKmh; startOdo = odometerKm
            return
        }
        val dt = nowMs - lastMs
        if (dt in 1..maxGapMs) distanceKm += speedKmh.coerceAtLeast(0) * dt / 3_600_000.0
        if (dt > 0) lastMs = nowMs
        if (speedKmh > maxSpeed) maxSpeed = speedKmh
    }

    /** True when an active trip should be closed because the cluster went quiet. */
    fun idle(nowMs: Long, lastIkeMs: Long): Boolean = active && nowMs - maxOf(lastIkeMs, lastMs) > idleEndMs

    /** Close the active trip; null when nothing was running or it was too short to keep. */
    fun end(consumption: String?): Trip? {
        if (!active) return null
        val t = Trip(startMs, lastMs, distanceKm, maxSpeed, startOdo, consumption)
        startMs = -1L; lastMs = -1L
        return if (t.distanceKm >= minKm) t else null
    }
}

/** Display helpers for the cluster's ASCII on-board-computer texts and the voltage byte. */
object BordFormat {
    // Leading number only: "---.- L/100" (no data yet) must not read as 100.
    private val number = Regex("""^\s*\d+(?:\.\d+)?""")

    /** "18.2 L/100" → "18,2"; "---.- L/100" or null → null. */
    fun consumption(text: String?): String? = text?.let { number.find(it)?.value?.trim() }?.replace('.', ',')

    /** "319 KM" → "319". */
    fun rangeKm(text: String?): String? = text?.let { number.find(it)?.value?.trim() }

    fun volts(raw: Int?): Double? = raw?.let { it * ObcPoll.VOLT_FACTOR }

    fun voltsText(raw: Int?): String? = volts(raw)?.let { String.format(Locale.ROOT, "%.1f", it).replace('.', ',') }

    /** Below this with the engine off the battery is getting flat (12.6 V is a full one at rest). */
    const val LOW_VOLTS = 11.8

    /** Home-ribbon battery warning; only with the engine off, where the reading means something. */
    fun batteryNotice(raw: Int?, rpm: Int?): String? {
        val v = volts(raw) ?: return null
        if ((rpm ?: 0) > 0 || v >= LOW_VOLTS) return null
        return "АКБ ${voltsText(raw)} В"
    }

    fun km1(v: Double): String = String.format(Locale.ROOT, "%.1f", v).replace('.', ',')
}
