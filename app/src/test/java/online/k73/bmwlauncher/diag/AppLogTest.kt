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
}
