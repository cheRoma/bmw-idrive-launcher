package online.k73.bmwlauncher.car

/**
 * On-board-computer values the instrument cluster (IKE, 0x80) computes itself and only hands out on
 * request: nothing broadcasts them, and this car has no OEM nav/GT left to ask. A request is
 * `3B 05 80 41 <prop> 01 <chk>` ("GT asks IKE for property"); the IKE answers
 * `80 <len> E7 24 <prop> 00 <ASCII text> <chk>` — the same text the cluster would show, e.g.
 * `36.5 KM/H` for average speed (seen on this car 2026-07-08).
 */
object ObcPoll {
    const val CONSUMPTION_1 = 0x04
    const val CONSUMPTION_2 = 0x05
    const val RANGE = 0x06
    const val AVG_SPEED = 0x0A

    /** What the test build asks for, in order, once per cycle. */
    val polled = intArrayOf(CONSUMPTION_1, CONSUMPTION_2, RANGE, AVG_SPEED)

    /** Seconds between cycles: the cluster's own values change slowly, the bus is shared. */
    const val CYCLE_MS = 10_000L
    const val GAP_MS = 250L

    /**
     * Only talk while the IKE is awake and broadcasting (speed/temps arrive every few seconds with
     * the key in). A frame on a sleeping bus wakes modules — on a parked car that drains the battery.
     */
    const val IKE_ALIVE_MS = 15_000L

    /**
     * Light module (LCM, 0xD0) status, diagnostic read `3F 03 D0 0B E7`. Read-only: it reports lamp
     * and voltage state, switches nothing. Payload byte 9 (frame index 13) tracks battery voltage —
     * 201 with the engine running, 167–179 parked — at a factor that still needs a multimeter check.
     */
    val lcmStatusRequest = intArrayOf(0x3F, 0x03, 0xD0, 0x0B, 0xE7)

    /** Ask the LCM once every this many OBC cycles (~30 s). */
    const val LCM_EVERY_CYCLES = 3

    /** Candidate volts-per-count, from published LCM decodes; verify against a meter. */
    const val VOLT_FACTOR = 0.0705

    /** Raw voltage byte from an LCM status reply `D0 .. 3F A0 ...`; null if not one. */
    fun lcmVoltageRaw(m: IntArray): Int? =
        if (m.size > 14 && m[0] == 0xD0 && m[2] == 0x3F && m[3] == 0xA0) m[13] else null

    fun shouldPoll(nowMs: Long, lastIkeBroadcastMs: Long): Boolean =
        lastIkeBroadcastMs > 0 && nowMs - lastIkeBroadcastMs in 0..IKE_ALIVE_MS

    fun request(prop: Int): IntArray {
        val body = intArrayOf(0x3B, 0x05, 0x80, 0x41, prop, 0x01)
        return body + MirrorTelegrams.checksum(body)
    }

    fun label(prop: Int): String = when (prop) {
        CONSUMPTION_1 -> "расход 1"
        CONSUMPTION_2 -> "расход 2"
        RANGE -> "запас хода"
        AVG_SPEED -> "средняя скорость"
        else -> "0x%02X".format(prop)
    }

    /** Decode an IKE `0x24` reply into (prop, trimmed text); null if the frame is not one. */
    fun parseReply(m: IntArray): Pair<Int, String>? {
        if (m.size < 7 || m[0] != 0x80 || m[3] != 0x24) return null
        val text = (6 until m.size - 1).map { m[it].toChar() }.joinToString("").trim()
        return m[4] to text
    }
}
