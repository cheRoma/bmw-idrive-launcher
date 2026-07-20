package online.k73.bmwlauncher.car

/** One live snapshot of the car's on-board data, decoded from the I-Bus. */
data class BordData(
    val connected: Boolean = false,
    val speedKmh: Int? = null,
    val avgSpeedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantC: Int? = null,
    val outsideC: Int? = null,
    val ignition: Boolean? = null,
    val updatedAtMs: Long = 0L,
)
