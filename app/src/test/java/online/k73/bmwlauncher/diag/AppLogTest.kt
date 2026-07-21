package online.k73.bmwlauncher.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLogTest {
    @Before fun setUp() = AppLog.clear()

    @Test fun format_prefixes_time_level_tag() {
        // 0 epoch millis in the JVM default TZ; assert the shape rather than the exact hour.
        val line = AppLog.format(0L, 'D', "Boot", "hello")
        assertTrue("has level/tag/msg: $line", line.endsWith(" D/Boot: hello"))
        // "HH:mm:ss.SSS " prefix = 13 chars before the level.
        assertEquals(13, line.indexOf('D'))
    }

    @Test fun buffer_evicts_oldest_past_cap() {
        val total = AppLog.CAP + 50
        for (i in 0 until total) AppLog.add("line-$i")
        assertEquals(AppLog.CAP, AppLog.size())
        val snap = AppLog.snapshot().lines()
        // Oldest 50 evicted: buffer holds line-50 .. line-(total-1), oldest first.
        assertEquals("line-50", snap.first())
        assertEquals("line-${total - 1}", snap.last())
    }

    @Test fun snapshot_is_oldest_first() {
        AppLog.add("a"); AppLog.add("b"); AppLog.add("c")
        assertEquals("a\nb\nc", AppLog.snapshot())
    }

    // --- PDC capture buffer -------------------------------------------------
    // Parking frames arrive ~8/s while capturing, which would evict the whole event log (and then
    // itself) from the 600-line ring. They get their own, much deeper buffer instead.

    @Test fun pdc_lines_do_not_touch_the_event_log() {
        AppLog.add("event")
        AppLog.pdc("60 0E 3F A0")
        assertEquals("event", AppLog.snapshot())
        // Capture lines carry an "HH:mm:ss.SSS  " prefix so a press can be located in time.
        assertTrue("timestamped frame: ${AppLog.pdcSnapshot()}", AppLog.pdcSnapshot().endsWith("  60 0E 3F A0"))
        assertEquals("HH:mm:ss.SSS is 12 chars", 12, AppLog.pdcSnapshot().indexOf(" "))
    }

    @Test fun event_log_flood_does_not_evict_pdc_frames() {
        AppLog.pdc("frame-1")
        for (i in 0 until AppLog.CAP * 2) AppLog.add("noise-$i")
        assertTrue(AppLog.pdcSnapshot().endsWith("  frame-1"))
    }

    @Test fun pdc_buffer_holds_a_full_reversing_session() {
        // ~8 lines/s (our request echo + the module's reply at 4 Hz) → the cap must cover minutes,
        // not the ~75 s the 600-line event ring would give.
        assertTrue("pdc cap covers >5 min of capture", AppLog.PDC_CAP >= 8 * 300)
    }

    @Test fun pdc_buffer_evicts_oldest_past_cap() {
        val total = AppLog.PDC_CAP + 10
        for (i in 0 until total) AppLog.pdc("f-$i")
        val snap = AppLog.pdcSnapshot().lines()
        assertEquals(AppLog.PDC_CAP, snap.size)
        assertTrue(snap.first().endsWith("  f-10"))
        assertTrue(snap.last().endsWith("  f-${total - 1}"))
    }

    @Test fun clear_empties_both_buffers() {
        AppLog.add("a"); AppLog.pdc("b")
        AppLog.clear()
        assertEquals("", AppLog.snapshot())
        assertEquals("", AppLog.pdcSnapshot())
    }
}
