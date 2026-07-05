package online.k73.bmwlauncher.music

object TimeFormat {
    /** Milliseconds -> "m:ss". Non-positive -> "0:00". */
    fun mmss(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }
}
