package online.k73.bmwlauncher.car

/**
 * I-Bus telegrams that fold and unfold the electric side mirrors on an E53.
 *
 * The mirrors are not driven directly: we pose as the diagnostic tester (`0x3F`) and ask the
 * General Module (ZKE body controller, node `0x00`) to run job `0x0C` for one side. The GM then
 * decides whether the car is in a state where folding is allowed and drives the mirror modules.
 *
 * Bytes verified from two independent sources that agree exactly: the decompiled OEM «i-Bus App»
 * 2.2.1.4 (fields `e0`/`f0`/`g0`/`h0` of its Control screen), which is what actually folded the
 * mirrors in this car, and the public I-Bus command lists that assign the same set to E39/E53.
 * See docs/superpowers/specs/2026-07-21-mirror-fold-design.md.
 */
object MirrorTelegrams {
    private const val DIAG = 0x3F      // us, posing as the diagnostic tester
    private const val GM = 0x00        // General Module (ZKE)
    private const val LEN = 0x06       // bytes from destination through checksum
    private const val JOB = 0x0C       // "actuate mirror"
    private const val ARG = 0x01       // constant tail parameter in every documented mirror job

    const val SIDE_DRIVER = 0x01
    const val SIDE_PASSENGER = 0x02
    const val DIR_FOLD = 0x31
    const val DIR_UNFOLD = 0x30

    /** XOR of every byte — how the I-Bus checksums a frame. */
    fun checksum(bytes: IntArray): Int = bytes.fold(0) { acc, b -> acc xor b }

    /** Build one mirror job. The checksum is computed, never hard-coded. */
    fun frame(side: Int, direction: Int): IntArray {
        val body = intArrayOf(DIAG, LEN, GM, JOB, side, direction, ARG)
        return body + checksum(body)
    }

    val driverFold: IntArray get() = frame(SIDE_DRIVER, DIR_FOLD)
    val driverUnfold: IntArray get() = frame(SIDE_DRIVER, DIR_UNFOLD)
    val passengerFold: IntArray get() = frame(SIDE_PASSENGER, DIR_FOLD)
    val passengerUnfold: IntArray get() = frame(SIDE_PASSENGER, DIR_UNFOLD)

    /**
     * Passenger first, then driver — the order the OEM app used. The whole pair is repeated
     * [REPEATS] times because the single-wire bus drops frames; one shot is not reliable.
     */
    const val REPEATS = 4
    const val GAP_BETWEEN_SIDES_MS = 300L
    const val GAP_BETWEEN_REPEATS_MS = 400L

    fun sequence(fold: Boolean): List<IntArray> =
        if (fold) listOf(passengerFold, driverFold) else listOf(passengerUnfold, driverUnfold)
}
