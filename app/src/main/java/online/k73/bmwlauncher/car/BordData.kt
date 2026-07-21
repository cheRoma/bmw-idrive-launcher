package online.k73.bmwlauncher.car

/**
 * Where the key is, from the IKE's `0x11` frame. The three terminals are separate bits, so this is
 * a ladder, not a flag: [ACC] is radio-on, [IGNITION] is the engine running or ready to, [START] is
 * the starter turning. Folding the mirrors keys off [OFF]; unfolding keys off [IGNITION]/[START],
 * because a driver sitting in [ACC] listening to music has not gone anywhere.
 */
enum class KeyPosition { OFF, ACC, IGNITION, START }

/** One live snapshot of the car's on-board data, decoded from the I-Bus. */
data class BordData(
    val connected: Boolean = false,
    val speedKmh: Int? = null,
    val avgSpeedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantC: Int? = null,
    val outsideC: Int? = null,
    val ignition: Boolean? = null,
    val keyPosition: KeyPosition? = null,
    val updatedAtMs: Long = 0L,
)

/**
 * Live counters for a PDC capture session, shown on screen so a reversing run can be judged in the
 * car instead of after decoding logs.
 *
 * [echo] counts our own requests seen coming back off the bus: without it an empty capture is
 * ambiguous — a silent module and an adapter that can't transmit look identical.
 */
data class PdcStats(
    val sent: Int = 0,
    val echo: Int = 0,
    val replies: Int = 0,
    val error: String? = null,
)
