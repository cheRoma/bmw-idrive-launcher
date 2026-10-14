package online.k73.bmwlauncher.car

/**
 * Engine-oil change tracking against the cluster's own odometer. Pure — storage lives in
 * [online.k73.bmwlauncher.data.OilStore], the odometer comes from the IKE `0x17` broadcast.
 */
data class OilChange(
    val lastKm: Int,
    val intervalKm: Int,
    /** ISO date (yyyy-MM-dd) of the last change, null when it was seeded from memory. */
    val lastDate: String? = null,
) {
    enum class Status { OK, SOON, OVERDUE }

    val dueKm: Int get() = lastKm + intervalKm

    fun remainingKm(odometerKm: Int): Int = dueKm - odometerKm

    fun status(odometerKm: Int): Status {
        val left = remainingKm(odometerKm)
        return when {
            left < 0 -> Status.OVERDUE
            left <= SOON_KM -> Status.SOON
            else -> Status.OK
        }
    }

    /** Short reminder for the home ribbon; null while the change is still far off. */
    fun ribbonText(odometerKm: Int?): String? {
        val odo = odometerKm ?: return null
        val left = remainingKm(odo)
        return when (status(odo)) {
            Status.OK -> null
            Status.SOON -> "Масло: ${km(left)} км"
            Status.OVERDUE -> "Масло: пора менять"
        }
    }

    /** Line for the on-board computer screen. */
    fun summary(odometerKm: Int?): String {
        val odo = odometerKm ?: return "Масло: замена на ${km(dueKm)} км"
        val left = remainingKm(odo)
        return if (left >= 0) "Масло: через ${km(left)} км · замена на ${km(dueKm)}"
        else "Масло: просрочено на ${km(-left)} км · было нужно на ${km(dueKm)}"
    }

    companion object {
        /** Warn from this many km before the change is due. */
        const val SOON_KM = 500

        /** 284082 → "284 082" (narrow no-break space, so a number never wraps). */
        fun km(v: Int): String = String.format(java.util.Locale.ROOT, "%,d", v).replace(',', ' ')
    }
}

/** IKE `80 0A BF 17 <km: 3 bytes little-endian> ...` — the cluster's odometer. */
fun odometerFrom(m: IntArray): Int? =
    if (m.size >= 8 && m[0] == 0x80 && m[3] == 0x17) m[4] or (m[5] shl 8) or (m[6] shl 16) else null
