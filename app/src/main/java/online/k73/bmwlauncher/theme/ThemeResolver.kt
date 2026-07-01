package online.k73.bmwlauncher.theme

import online.k73.bmwlauncher.data.ThemeMode
import java.time.LocalTime

object ThemeResolver {
    /**
     * @param nightStartHour hour [0..23] when night theme begins (inclusive)
     * @param nightEndHour   hour [0..23] when night theme ends (exclusive)
     * The window wraps past midnight when start > end (e.g. 20 -> 7).
     */
    fun isNight(mode: ThemeMode, now: LocalTime, nightStartHour: Int, nightEndHour: Int): Boolean =
        when (mode) {
            ThemeMode.DAY -> false
            ThemeMode.NIGHT -> true
            ThemeMode.AUTO -> {
                val start = LocalTime.of(nightStartHour, 0)
                val end = LocalTime.of(nightEndHour, 0)
                if (start <= end) now >= start && now < end
                else now >= start || now < end
            }
        }
}
