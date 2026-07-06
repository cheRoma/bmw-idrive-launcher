package online.k73.bmwlauncher.diag

import android.content.Context
import java.io.File

/**
 * Installs a global uncaught-exception handler that logs the crash, writes a full report to
 * filesDir/pending-crash.txt (best-effort), then delegates to the previous handler so the system
 * still handles the crash. The pending file is uploaded on the next launch (see MainApplication).
 */
object CrashHandler {
    const val PENDING_CRASH = "pending-crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                AppLog.e("CRASH", "uncaught on ${thread.name}", throwable)
                val report = buildString {
                    append(DeviceInfo.collect(app))
                    append("\n=== EVENT LOG ===\n")
                    append(AppLog.snapshot())
                    append("\n=== STACKTRACE ===\n")
                    append(stackToString(throwable))
                }
                File(app.filesDir, PENDING_CRASH).writeText(report)
            }
            // Always delegate so the OS still shows/kills the process as usual.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun stackToString(t: Throwable): String {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }
}
