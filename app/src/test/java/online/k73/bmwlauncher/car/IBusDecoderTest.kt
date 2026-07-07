package online.k73.bmwlauncher.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IBusDecoderTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun decodeLast(vararg frames: ByteArray): BordData? {
        var last: BordData? = null
        val d = IBusDecoder(nowMs = { 0L }) { last = it }
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
