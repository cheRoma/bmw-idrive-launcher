package online.k73.bmwlauncher.car

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expected bytes were CONFIRMED on this very car 2026-07-23 by probing each GM 0x0C code and
 * watching the mirrors: 0x39 = fold, 0x3A = unfold (the 0x30/0x31 the OEM app / public lists claim
 * drive the WINDOWS here). Written out literally on purpose: if [MirrorTelegrams] ever changes
 * shape, the test must fail rather than agree with the new code.
 */
class MirrorTelegramsTest {
    @Test fun driver_fold_matches_the_documented_telegram() {   // left, closes — confirmed on car
        assertArrayEquals(intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x01, 0x39, 0x01, 0x0C), MirrorTelegrams.driverFold)
    }

    @Test fun driver_unfold_matches_the_documented_telegram() { // left, opens
        assertArrayEquals(intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x01, 0x3A, 0x01, 0x0F), MirrorTelegrams.driverUnfold)
    }

    @Test fun passenger_fold_matches_the_documented_telegram() {   // right, closes
        assertArrayEquals(intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x02, 0x39, 0x01, 0x0F), MirrorTelegrams.passengerFold)
    }

    @Test fun passenger_unfold_matches_the_documented_telegram() { // right, opens
        assertArrayEquals(intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x02, 0x3A, 0x01, 0x0C), MirrorTelegrams.passengerUnfold)
    }

    @Test fun every_frame_carries_a_valid_xor_checksum() {
        for (f in listOf(
            MirrorTelegrams.driverFold, MirrorTelegrams.driverUnfold,
            MirrorTelegrams.passengerFold, MirrorTelegrams.passengerUnfold,
        )) {
            val body = f.copyOfRange(0, f.size - 1)
            assertEquals("checksum of ${f.joinToString(" ") { "%02X".format(it) }}", f.last(), MirrorTelegrams.checksum(body))
        }
    }

    @Test fun length_byte_counts_destination_through_checksum() {
        // I-Bus length excludes source and the length byte itself; 8-byte frame → 6.
        for (f in listOf(MirrorTelegrams.driverFold, MirrorTelegrams.passengerUnfold)) {
            assertEquals(8, f.size)
            assertEquals(f.size - 2, f[1])
        }
    }

    @Test fun fold_and_unfold_differ_only_in_direction_and_checksum() {
        val fold = MirrorTelegrams.driverFold
        val unfold = MirrorTelegrams.driverUnfold
        assertArrayEquals(fold.copyOfRange(0, 5), unfold.copyOfRange(0, 5))
        assertEquals(MirrorTelegrams.DIR_FOLD, fold[5])
        assertEquals(MirrorTelegrams.DIR_UNFOLD, unfold[5])
    }

    @Test fun sequence_sends_passenger_before_driver() {
        assertArrayEquals(MirrorTelegrams.passengerFold, MirrorTelegrams.sequence(fold = true)[0])
        assertArrayEquals(MirrorTelegrams.driverFold, MirrorTelegrams.sequence(fold = true)[1])
        assertArrayEquals(MirrorTelegrams.passengerUnfold, MirrorTelegrams.sequence(fold = false)[0])
        assertArrayEquals(MirrorTelegrams.driverUnfold, MirrorTelegrams.sequence(fold = false)[1])
    }

    @Test fun the_pair_is_repeated_because_the_bus_drops_frames() {
        assertTrue("one shot is not enough on a single-wire bus", MirrorTelegrams.REPEATS >= 2)
    }
}
