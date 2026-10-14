package online.k73.bmwlauncher.car

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObcPollTest {
    @Test fun consumption_request_matches_the_known_probe_frame() =
        assertArrayEquals(intArrayOf(0x3B, 0x05, 0x80, 0x41, 0x04, 0x01, 0xFA), ObcPoll.request(ObcPoll.CONSUMPTION_1))

    @Test fun parses_the_average_speed_reply_seen_on_the_car() {
        // 2026-07-08 log: 80 0E E7 24 0A 00 "36.5 KM/H" 18
        val m = intArrayOf(0x80, 0x0E, 0xE7, 0x24, 0x0A, 0x00, 0x33, 0x36, 0x2E, 0x35, 0x20, 0x4B, 0x4D, 0x2F, 0x48, 0x18)
        assertEquals(0x0A to "36.5 KM/H", ObcPoll.parseReply(m))
    }

    @Test fun ignores_frames_that_are_not_obc_replies() {
        assertNull(ObcPoll.parseReply(intArrayOf(0x80, 0x05, 0xBF, 0x18, 0x32, 0x1B, 0x0B)))
    }

    @Test fun polls_only_while_the_cluster_broadcasts() {
        assertFalse(ObcPoll.shouldPoll(100_000, 0))
        assertTrue(ObcPoll.shouldPoll(100_000, 95_000))
        assertFalse(ObcPoll.shouldPoll(100_000, 80_000))
    }

    @Test fun decoder_keeps_the_latest_obc_text_and_marks_the_ike_awake() {
        var last: BordData? = null
        val seen = mutableListOf<Pair<Int, String>>()
        val d = IBusDecoder(nowMs = { 42L }, emit = { last = it }, onObcText = { p, t -> seen += p to t })
        val reply = intArrayOf(0x80, 0x0E, 0xE7, 0x24, 0x0A, 0x00, 0x33, 0x36, 0x2E, 0x35, 0x20, 0x4B, 0x4D, 0x2F, 0x48, 0x18)
        val speed = intArrayOf(0x80, 0x05, 0xBF, 0x18, 0x32, 0x1B, 0x0B)
        for (f in listOf(reply, speed)) d.feed(ByteArray(f.size) { f[it].toByte() }, f.size)
        assertEquals("36.5 KM/H", last?.obc?.get(0x0A))
        assertEquals(listOf(0x0A to "36.5 KM/H"), seen)
        assertEquals(42L, d.lastIkeBroadcastMs)
    }
}

class LcmVoltageTest {
    // 2026-10-14 log, parked: D0 23 3F A0 C1 C0 00 00 00 00 00 00 00 B3 ... → raw 0xB3 = 179
    private val reply = intArrayOf(
        0xD0, 0x23, 0x3F, 0xA0, 0xC1, 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xB3, 0x00, 0x00,
        0x00, 0x08, 0x10, 0x55, 0x02, 0x00, 0x28, 0x00, 0x00, 0x00, 0x00, 0x02, 0x05, 0x00, 0x00, 0x00,
        0x00, 0xFF, 0xFF, 0x00, 0xBE,
    )

    @Test fun reads_the_voltage_byte() = assertEquals(179, ObcPoll.lcmVoltageRaw(reply))

    @Test fun request_checksum_is_valid() =
        assertEquals(0xE7, MirrorTelegrams.checksum(ObcPoll.lcmStatusRequest.copyOfRange(0, 4)))

    @Test fun decoder_carries_the_raw_byte() {
        var last: BordData? = null
        IBusDecoder(nowMs = { 0L }, emit = { last = it }).feed(ByteArray(reply.size) { reply[it].toByte() }, reply.size)
        assertEquals(179, last?.voltageRaw)
    }
}
