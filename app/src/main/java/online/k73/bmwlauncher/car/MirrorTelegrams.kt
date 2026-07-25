package online.k73.bmwlauncher.car

/**
 * I-Bus telegrams that fold and unfold the electric side mirrors on an E53.
 *
 * The mirrors are not driven directly: we pose as the diagnostic tester (`0x3F`) and ask the
 * General Module (ZKE body controller, node `0x00`) to run job `0x0C` for one side. The GM then
 * decides whether the car is in a state where folding is allowed and drives the mirror modules.
 *
 * Bytes CONFIRMED ON THE CAR 2026-07-23 by probing each GM 0x0C code and watching the mirrors:
 *   `3F 06 00 0C 02 39 01 0F` folds the RIGHT mirror, `01 39` folds the LEFT;
 *   `3F 06 00 0C 02 3A 01 0C` unfolds the RIGHT, `01 3A` unfolds the LEFT.
 * So on THIS car the mirror direction byte is **0x39 = fold, 0x3A = unfold** — NOT the 0x31/0x30
 * from the public lists / OEM Control-screen fields, which on this GM drive the WINDOWS (window
 * incident 2026-07-21). See docs/superpowers/specs/2026-07-21-mirror-fold-design.md and
 * ~/bmw-ibus-recon/ibus-write-catalog.md.
 */
object MirrorTelegrams {
    private const val DIAG = 0x3F      // us, posing as the diagnostic tester
    private const val GM = 0x00        // General Module (ZKE)
    private const val LEN = 0x06       // bytes from destination through checksum
    private const val JOB = 0x0C       // "actuate mirror"
    private const val ARG = 0x01       // constant tail parameter in every documented mirror job

    const val SIDE_DRIVER = 0x01       // left
    const val SIDE_PASSENGER = 0x02    // right
    const val DIR_FOLD = 0x39          // confirmed on the car — 0x31 was WINDOWS
    const val DIR_UNFOLD = 0x3A        // confirmed on the car — 0x30 was WINDOWS

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
