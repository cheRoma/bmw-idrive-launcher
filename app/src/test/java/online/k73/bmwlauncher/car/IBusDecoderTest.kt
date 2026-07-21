package online.k73.bmwlauncher.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IBusDecoderTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun decodeLast(vararg frames: ByteArray): BordData? {
        var last: BordData? = null
        val d = IBusDecoder(nowMs = { 0L }, emit = { last = it })
        frames.forEach { d.feed(it, it.size) }
        return last
    }

    @Test fun decodes_speed_and_rpm_from_0x18() {
        // 80 05 BF 18 32 1B 0B → speed 0x32*2=100, rpm 0x1B*100=2700
        val d = decodeLast(bytes(0x80, 0x05, 0xBF, 0x18, 0x32, 0x1B, 0x0B))
        assertEquals(100, d?.speedKmh)
        assertEquals(2700, d?.rpm)
    }

    @Test fun decodes_signed_temps_from_0x19() {
        // 80 05 BF 19 F8 5A 81 → outside -8 (0xF8 signed), coolant 90 (0x5A)
        val d = decodeLast(bytes(0x80, 0x05, 0xBF, 0x19, 0xF8, 0x5A, 0x81))
        assertEquals(-8, d?.outsideC)
        assertEquals(90, d?.coolantC)
    }

    // --- key position (0x11) — drives the mirror automation, so the ladder must be exact ---

    @Test fun decodes_key_off() {
        val d = decodeLast(bytes(0x80, 0x04, 0xBF, 0x11, 0x00, 0x2A))
        assertEquals(KeyPosition.OFF, d?.keyPosition)
        assertEquals(false, d?.ignition)
    }

    @Test fun decodes_key_acc() {
        val d = decodeLast(bytes(0x80, 0x04, 0xBF, 0x11, 0x01, 0x2B))
        assertEquals(KeyPosition.ACC, d?.keyPosition)
        assertEquals(true, d?.ignition)
    }

    @Test fun decodes_key_ignition() {
        val d = decodeLast(bytes(0x80, 0x04, 0xBF, 0x11, 0x03, 0x29))
        assertEquals(KeyPosition.IGNITION, d?.keyPosition)
    }

    @Test fun decodes_key_start() {
        val d = decodeLast(bytes(0x80, 0x04, 0xBF, 0x11, 0x07, 0x2D))
        assertEquals(KeyPosition.START, d?.keyPosition)
    }

    @Test fun key_position_takes_the_highest_live_terminal() {
        // Cranking leaves KL_R and KL_15 set as well — START must still win.
        assertEquals(KeyPosition.START, IBusDecoder.keyPosition(0x07))
        assertEquals(KeyPosition.IGNITION, IBusDecoder.keyPosition(0x03))
        assertEquals(KeyPosition.ACC, IBusDecoder.keyPosition(0x01))
        assertEquals(KeyPosition.OFF, IBusDecoder.keyPosition(0x00))
    }

    // --- whole-bus capture: the only trustworthy way to learn a telegram is to watch the car ---

    @Test fun bus_capture_delivers_every_valid_frame() {
        val seen = mutableListOf<IntArray>()
        val d = IBusDecoder(nowMs = { 0L }, emit = {}, onBusFrame = { seen.add(it) })
        d.busCapture = true
        val speed = bytes(0x80, 0x05, 0xBF, 0x18, 0x32, 0x1B, 0x0B)
        val key = bytes(0x80, 0x04, 0xBF, 0x11, 0x03, 0x29)
        d.feed(speed, speed.size); d.feed(key, key.size)
        assertEquals(2, seen.size)
        assertEquals(0x18, seen[0][3])
        assertEquals(0x11, seen[1][3])
    }

    @Test fun bus_capture_off_delivers_nothing() {
        val seen = mutableListOf<IntArray>()
        val d = IBusDecoder(nowMs = { 0L }, emit = {}, onBusFrame = { seen.add(it) })
        val f = bytes(0x80, 0x05, 0xBF, 0x18, 0x32, 0x1B, 0x0B)
        d.feed(f, f.size)
        assertEquals(0, seen.size)
    }

    @Test fun bus_capture_skips_frames_with_a_bad_checksum() {
        val seen = mutableListOf<IntArray>()
        val d = IBusDecoder(nowMs = { 0L }, emit = {}, onBusFrame = { seen.add(it) })
        d.busCapture = true
        val bad = bytes(0x80, 0x05, 0xBF, 0x18, 0x32, 0x1B, 0x00)
        d.feed(bad, bad.size)
        assertEquals("a corrupted frame is worse than no frame when we intend to replay it", 0, seen.size)
    }

    @Test fun rejects_bad_checksum() {
        assertNull(decodeLast(bytes(0x80, 0x05, 0xBF, 0x18, 0x32, 0x1B, 0x00)))
    }

    @Test fun resyncs_after_garbage() {
        // Leading garbage byte 0xAA, then valid 80 05 BF 18 0A 00 28 → decodes.
        val d = decodeLast(bytes(0xAA, 0x80, 0x05, 0xBF, 0x18, 0x0A, 0x00, 0x28))
        assertEquals(20, d?.speedKmh)
        assertEquals(0, d?.rpm)
    }
}
