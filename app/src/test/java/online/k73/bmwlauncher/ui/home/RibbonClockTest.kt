package online.k73.bmwlauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class RibbonClockTest {
    @Test fun time_is_hh_mm_zero_padded() {
        assertEquals("19:42", RibbonClock.time(LocalDateTime.of(2026, 7, 5, 19, 42)))
        assertEquals("07:05", RibbonClock.time(LocalDateTime.of(2026, 7, 5, 7, 5)))
    }
    @Test fun date_is_full_lowercase_weekday() {
        // 2026-07-05 is a Sunday
        assertEquals("воскресенье, 5 июля", RibbonClock.date(LocalDateTime.of(2026, 7, 5, 0, 0)))
        // 2026-01-01 is a Thursday
        assertEquals("четверг, 1 января", RibbonClock.date(LocalDateTime.of(2026, 1, 1, 0, 0)))
    }
}
