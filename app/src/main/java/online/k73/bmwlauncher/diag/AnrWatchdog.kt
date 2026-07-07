package online.k73.bmwlauncher.diag

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Main-thread hang (ANR) watchdog. A single daemon thread posts a no-op to the main looper every
 * [CHECK_INTERVAL_MS] and waits up to [HANG_TIMEOUT_MS] for it to run. If the main thread is
 * unresponsive it captures all stack traces (emphasising `main`), logs them, PERSISTS a report to
 * [CrashHandler.PENDING_CRASH] (so a hang the user power-cycles past still ships on next launch —
 * an immediate network upload alone is lost if the ROM is rebooted mid-hang), and fires [onHang]
 * exactly once per hang episode — re-arming only after the main thread recovers. Never crashes.
 */
object AnrWatchdog {
    const val CHECK_INTERVAL_MS = 2_000L
    const val HANG_TIMEOUT_MS = 5_000L

    @Volatile private var started = false
    @Volatile private var appContext: Context? = null

    fun start(context: Context, onHang: () -> Unit = {}) {
        if (started) return
        started = true
        appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        val thread = Thread({ loop(mainHandler, onHang) }, "anr-watchdog")
        thread.isDaemon = true
        thread.start()
    }

    /**
     * Write a diagnostic report to filesDir/[CrashHandler.PENDING_CRASH] so it survives a reboot and
     * is uploaded on the next launch (MainApplication). Best-effort; the AppLog snapshot already
     * carries the just-logged ANR stack, so the persisted report is self-contained.
     */
    private fun persistPending() {
        val ctx = appContext ?: return
        runCatching {
            val report = buildString {
                append(DeviceInfo.collect(ctx))
                append("\n=== EVENT LOG ===\n")
                append(AppLog.snapshot())
            }
            File(ctx.filesDir, CrashHandler.PENDING_CRASH).writeText(report)
        }
    }

    private fun loop(mainHandler: Handler, onHang: () -> Unit) {
        while (true) {
            try {
                val completed = java.util.concurrent.CountDownLatch(1)
                val posted = mainHandler.post { completed.countDown() }
                if (!posted) {
                    // Looper is quitting/gone — nothing to watch.
                    Thread.sleep(CHECK_INTERVAL_MS)
                    continue
                }
                val ran = completed.await(HANG_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!ran) {
                    // Hang detected. Fire once, then BLOCK until the main thread finally processes
                    // our token (recovery) — this is what makes it one-shot per hang episode: no
                    // re-fire until the loop comes back around after recovery.
                    runCatching {
                        AppLog.e("ANR", "main thread unresponsive >${HANG_TIMEOUT_MS / 1000}s\n${dumpMainStack()}")
                    }
                    // Persist BEFORE the network attempt: a reboot mid-hang would lose an
                    // upload-only report, but a pending file ships on the next launch.
                    persistPending()
                    // Off-thread so a slow network upload never blocks the watchdog loop.
                    runCatching {
                        Thread({ runCatching { onHang() } }, "anr-upload").apply { isDaemon = true }.start()
                    }
                    completed.await()
                } else {
                    Thread.sleep(CHECK_INTERVAL_MS)
                }
            } catch (_: InterruptedException) {
                return
            } catch (_: Throwable) {
                // Watchdog must never crash the app; back off and continue.
                runCatching { Thread.sleep(CHECK_INTERVAL_MS) }
            }
        }
    }

    /** All thread stacks, main first and clearly labelled. */
    private fun dumpMainStack(): String {
        val all = Thread.getAllStackTraces()
        val main = Looper.getMainLooper().thread
        val sb = StringBuilder()
        sb.append("--- main thread ---\n")
        all[main]?.forEach { sb.append("  at ").append(it).append('\n') }
        sb.append("--- other threads ---\n")
        all.filterKeys { it != main }.forEach { (t, stack) ->
            sb.append(t.name).append(":\n")
            stack.take(12).forEach { sb.append("  at ").append(it).append('\n') }
        }
        return sb.toString()
    }
}
