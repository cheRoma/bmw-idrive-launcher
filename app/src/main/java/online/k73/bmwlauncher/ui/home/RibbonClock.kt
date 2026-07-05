package online.k73.bmwlauncher.ui.home

import java.time.DayOfWeek
import java.time.LocalDateTime

object RibbonClock {
    private val DOW = mapOf(
        DayOfWeek.MONDAY to "понедельник", DayOfWeek.TUESDAY to "вторник", DayOfWeek.WEDNESDAY to "среда",
        DayOfWeek.THURSDAY to "четверг", DayOfWeek.FRIDAY to "пятница", DayOfWeek.SATURDAY to "суббота",
        DayOfWeek.SUNDAY to "воскресенье",
    )
    private val MONTHS = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    fun time(dt: LocalDateTime): String = "%02d:%02d".format(dt.hour, dt.minute)
    fun date(dt: LocalDateTime): String = "${DOW[dt.dayOfWeek]}, ${dt.dayOfMonth} ${MONTHS[dt.monthValue - 1]}"
}
