package online.k73.bmwlauncher.diag

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-memory ring-buffer event log. Thread-safe, keeps the last [CAP] entries and mirrors every
 * line to logcat (android.util.Log). Extracted [format]/eviction so the core is unit-testable
 * without Android; the logcat mirror is wrapped so it no-ops in a plain JVM test.
 */
object AppLog {
    const val CAP = 600

    // Fixed formatter; guarded by the buffer lock so a single SimpleDateFormat is safe.
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val buffer = ArrayDeque<String>(CAP)

    /** Pure line formatter — testable without Android. */
    fun format(timeMillis: Long, level: Char, tag: String, msg: String): String {
        val time = synchronized(timeFmt) { timeFmt.format(Date(timeMillis)) }
        return "$time $level/$tag: $msg"
    }

    /** Add a formatted line to the ring buffer, evicting the oldest past [CAP]. */
    fun add(line: String) {
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > CAP) buffer.removeFirst()
        }
    }

    fun log(level: Char, tag: String, msg: String) {
        add(format(System.currentTimeMillis(), level, tag, msg))
        mirrorToLogcat(level, tag, msg)
    }

    fun d(tag: String, msg: String) = log('D', tag, msg)
    fun w(tag: String, msg: String) = log('W', tag, msg)

    fun e(tag: String, msg: String, t: Throwable? = null) {
        val full = if (t != null) "$msg\n${stackToString(t)}" else msg
        log('E', tag, full)
    }

    /** The buffer joined by newlines, oldest first. */
    fun snapshot(): String = synchronized(buffer) { buffer.joinToString("\n") }

    /** Test hook: clear the buffer. */
    fun clear() = synchronized(buffer) { buffer.clear() }

    fun size(): Int = synchronized(buffer) { buffer.size }

    private fun stackToString(t: Throwable): String {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }

    // android.util.Log isn't available under plain JUnit; swallow the stub's RuntimeException.
    private fun mirrorToLogcat(level: Char, tag: String, msg: String) {
        try {
            when (level) {
                'E' -> android.util.Log.e(tag, msg)
                'W' -> android.util.Log.w(tag, msg)
                else -> android.util.Log.d(tag, msg)
            }
        } catch (_: Throwable) {
            // Not running on a device / Log not stubbed — ignore.
        }
    }
}
