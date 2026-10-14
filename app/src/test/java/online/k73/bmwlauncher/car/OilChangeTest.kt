package online.k73.bmwlauncher.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OilChangeTest {
    // This car on 2026-10-14: changed at 280 332, every 4 000 km, odometer 284 082.
    private val oil = OilChange(lastKm = 280_332, intervalKm = 4_000)

    @Test fun due_and_remaining() {
        assertEquals(284_332, oil.dueKm)
        assertEquals(250, oil.remainingKm(284_082))
    }

    @Test fun status_ladder() {
        assertEquals(OilChange.Status.OK, oil.status(283_000))
        assertEquals(OilChange.Status.SOON, oil.status(283_832))   // exactly 500 left
        assertEquals(OilChange.Status.SOON, oil.status(284_332))   // due today
        assertEquals(OilChange.Status.OVERDUE, oil.status(284_333))
    }

    @Test fun ribbon_only_when_close() {
        assertNull(oil.ribbonText(283_000))
        assertNull(oil.ribbonText(null))
        assertEquals("Масло: 250 км", oil.ribbonText(284_082))
        assertEquals("Масло: пора менять", oil.ribbonText(284_400))
    }

    @Test fun summary_lines() {
        assertEquals("Масло: через 250 км · замена на 284 332", oil.summary(284_082))
        assertEquals("Масло: просрочено на 68 км · было нужно на 284 332", oil.summary(284_400))
        assertEquals("Масло: замена на 284 332 км", oil.summary(null))
    }

    @Test fun odometer_from_real_ike_frame() {
        // 2026-10-14 log: 80 0A BF 17 B2 55 04 80 1F 32 CC A0 → 0x0455B2 = 284 082 km
        val m = intArrayOf(0x80, 0x0A, 0xBF, 0x17, 0xB2, 0x55, 0x04, 0x80, 0x1F, 0x32, 0xCC, 0xA0)
        assertEquals(284_082, odometerFrom(m))
        var last: BordData? = null
        IBusDecoder(nowMs = { 0L }, emit = { last = it }).feed(ByteArray(m.size) { m[it].toByte() }, m.size)
        assertEquals(284_082, last?.odometerKm)
    }
}
