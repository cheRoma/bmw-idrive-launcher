package online.k73.bmwlauncher.theme

import online.k73.bmwlauncher.data.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ThemeResolverTest {
    @Test fun manual_day_is_never_night() {
        assertFalse(ThemeResolver.isNight(ThemeMode.DAY, LocalTime.of(23, 0), 20, 7))
    }
    @Test fun manual_night_is_always_night() {
        assertTrue(ThemeResolver.isNight(ThemeMode.NIGHT, LocalTime.of(12, 0), 20, 7))
    }
    @Test fun auto_evening_is_night() {
        assertTrue(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(21, 30), 20, 7))
    }
    @Test fun auto_pre_dawn_is_night() {
        assertTrue(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(6, 0), 20, 7))
    }
    @Test fun auto_midday_is_day() {
        assertFalse(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(13, 0), 20, 7))
    }
    @Test fun auto_exact_night_start_is_night() {
        assertTrue(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(20, 0), 20, 7))
    }
    @Test fun auto_exact_night_end_is_day() {
        assertFalse(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(7, 0), 20, 7))
    }
}
