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

    /**
     * PDC capture gets its own, far deeper ring: while capturing we poll the parking module at 4 Hz
     * and log both our request and its reply, so ~8 lines/s would blow through [CAP] in about a
     * minute — losing the approach to the obstacle, which is the only part worth decoding.
     */
    const val PDC_CAP = 6000

    // Fixed formatter; guarded by the buffer lock so a single SimpleDateFormat is safe.
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val buffer = ArrayDeque<String>(CAP)
    private val pdcBuffer = ArrayDeque<String>(64)

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

    /**
     * Add a raw parking-module frame line. Separate buffer from [add] so a capture session neither
     * evicts the event log nor gets evicted by it — both survive into the uploaded report.
     */
    fun pdc(line: String) {
        synchronized(pdcBuffer) {
            pdcBuffer.addLast(line)
            while (pdcBuffer.size > PDC_CAP) pdcBuffer.removeFirst()
        }
    }

    /** The buffer joined by newlines, oldest first. */
    fun snapshot(): String = synchronized(buffer) { buffer.joinToString("\n") }

    /** The PDC capture buffer joined by newlines, oldest first. */
    fun pdcSnapshot(): String = synchronized(pdcBuffer) { pdcBuffer.joinToString("\n") }

    /** Test hook: clear both buffers. */
    fun clear() {
        synchronized(buffer) { buffer.clear() }
        synchronized(pdcBuffer) { pdcBuffer.clear() }
    }

    fun size(): Int = synchronized(buffer) { buffer.size }

    fun pdcSize(): Int = synchronized(pdcBuffer) { pdcBuffer.size }

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
