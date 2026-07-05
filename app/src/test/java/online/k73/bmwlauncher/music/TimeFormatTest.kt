package online.k73.bmwlauncher.music

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun formats_minutes_seconds() {
        assertEquals("1:24", TimeFormat.mmss(84_000))
        assertEquals("0:05", TimeFormat.mmss(5_000))
        assertEquals("3:58", TimeFormat.mmss(238_000))
        assertEquals("10:00", TimeFormat.mmss(600_000))
    }
    @Test fun non_positive_is_zero() {
        assertEquals("0:00", TimeFormat.mmss(0))
        assertEquals("0:00", TimeFormat.mmss(-5))
    }
}
