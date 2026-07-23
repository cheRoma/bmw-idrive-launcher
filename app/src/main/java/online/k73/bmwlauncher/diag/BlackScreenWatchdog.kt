package online.k73.bmwlauncher.diag

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.view.PixelCopy
import java.io.File
import java.lang.ref.WeakReference

/**
 * Durable **black-screen** detector — the failure mode the ANR watchdog can't see. On this head unit
 * the launcher window sometimes goes fully black on arrival (the MapLibre GL surface fails to recover
 * after the ACC sleep/wake power cycle) while the **main thread stays responsive**, so [AnrWatchdog]
 * never fires and nothing is recorded. The TextureView switch (v1.6.11) reduced but did not eliminate
 * it, and until now every occurrence left zero evidence.
 *
 * Approach (no MapLibre internals): every [SAMPLE_INTERVAL_MS] — only while one of OUR activities is
 * RESUMED + window-focused + the screen is on — grab a tiny [PixelCopy] of the window and count how
 * many pixels carry any real brightness. A healthy launcher screen always has amber/grey content
 * (clock ribbon, carousel tiles, on-screen values); a black window has none. If the window stays
 * blank for [DARK_SAMPLES_TO_TRIGGER] consecutive samples we PERSIST a report to
 * [CrashHandler.PENDING_CRASH] (so an occurrence the user power-cycles past still ships next launch)
 * and fire [onBlack] once per episode, re-arming only after a healthy frame returns.
 *
 * Detection only — it does not try to repair the surface. Never crashes the app.
 */
object BlackScreenWatchdog {
    // 48×27 keeps the copy + scan cheap while still resolving the small amber UI elements.
    private const val SAMPLE_W = 48
    private const val SAMPLE_H = 27
    // A pixel counts as "content" when its brightest channel clears this — our graphite background
    // (#0A0B0D, max channel 13) and a truly black window (0) both fall below it; amber #FF7E00 (255)
    // and grey text sit well above.
    private const val BRIGHT_CHANNEL_THRESHOLD = 60
    // At/under this many bright pixels the frame is blank. >0 tolerates a stray hot pixel.
    private const val MAX_BRIGHT_FOR_BLACK = 2
    private const val SAMPLE_INTERVAL_MS = 2_500L
    // ~7.5 s of continuous black before we believe it — long enough to skip screen transitions.
    private const val DARK_SAMPLES_TO_TRIGGER = 3

    @Volatile private var started = false
    @Volatile private var appContext: Context? = null
    @Volatile private var current: WeakReference<Activity>? = null
    private var onBlack: () -> Unit = {}
    private var pm: PowerManager? = null
    private lateinit var handler: Handler

    // Touched only from the watchdog thread.
    private var darkStreak = 0
    private var reported = false

    fun start(app: Application, onBlack: () -> Unit = {}) {
        if (started) return
        started = true
        appContext = app.applicationContext
        this.onBlack = onBlack
        pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ht = HandlerThread("blackscreen-watchdog").apply { isDaemon = true; start() }
        handler = Handler(ht.looper)
        app.registerActivityLifecycleCallbacks(lifecycle)
        handler.postDelayed(tick, SAMPLE_INTERVAL_MS)
    }

    // registerActivityLifecycleCallbacks only ever reports OUR OWN activities, so `current` is always
    // one of ours — no package check needed. Cleared on pause so we never sample another app.
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) { current = WeakReference(activity) }
        override fun onActivityPaused(activity: Activity) {
            if (current?.get() === activity) current = null
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {
            if (current?.get() === activity) current = null
        }
    }

    private val tick = Runnable { runCatching { sampleOnce() }.onFailure { reschedule() } }

    private fun reschedule() {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, SAMPLE_INTERVAL_MS)
    }

    private fun sampleOnce() {
        val act = current?.get()
        val win = act?.window
        // Only judge while our window is actually meant to be showing pixels. A backgrounded app, a
        // window without focus (dialog/transition), or a screen that's off is legitimately blank.
        if (act == null || win == null || !win.decorView.hasWindowFocus() || pm?.isInteractive != true) {
            darkStreak = 0
            reported = false
            reschedule()
            return
        }
        val bmp = Bitmap.createBitmap(SAMPLE_W, SAMPLE_H, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(win, bmp, { result ->
                runCatching {
                    if (result == PixelCopy.SUCCESS) analyze(bmp, act)
                    // Any other result = surface not grabbable this tick; don't count it as black.
                }
                runCatching { bmp.recycle() }
                reschedule()
            }, handler)
        }.onFailure {
            runCatching { bmp.recycle() }
            reschedule()
        }
    }

    private fun analyze(bmp: Bitmap, act: Activity) {
        val px = IntArray(SAMPLE_W * SAMPLE_H)
        bmp.getPixels(px, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
        var bright = 0
        for (c in px) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (maxOf(r, g, b) > BRIGHT_CHANNEL_THRESHOLD && ++bright > MAX_BRIGHT_FOR_BLACK) break
        }
        val blank = bright <= MAX_BRIGHT_FOR_BLACK
        if (!blank) {
            darkStreak = 0
            reported = false // healthy frame — re-arm for the next episode
            return
        }
        darkStreak++
        if (darkStreak >= DARK_SAMPLES_TO_TRIGGER && !reported) {
            reported = true
            report(act, bright)
        }
    }

    private fun report(act: Activity, brightCount: Int) {
        val secs = DARK_SAMPLES_TO_TRIGGER * SAMPLE_INTERVAL_MS / 1000
        runCatching {
            AppLog.e(
                "BLACKSCREEN",
                "launcher window blank ≥${secs}s in ${act.localClassName} " +
                    "(bright px=$brightCount/${SAMPLE_W * SAMPLE_H}) — GL surface likely lost, main thread alive",
            )
        }
        persist(act, brightCount, secs)
        // onBlack is already non-blocking (schedules an IO upload); a failed live upload leaves the
        // persisted pending file to ship on the next launch.
        runCatching { onBlack() }
    }

    /**
     * Write a durable report to filesDir/[CrashHandler.PENDING_CRASH] so a black screen the user
     * power-cycles past is still uploaded on the next launch (MainApplication). Shares the pending
     * file with [CrashHandler]/[AnrWatchdog] — last writer wins, which is fine for these rare events.
     */
    private fun persist(act: Activity, brightCount: Int, secs: Long) {
        val ctx = appContext ?: return
        runCatching {
            val report = buildString {
                append(DeviceInfo.collect(ctx))
                append("\n=== BLACK SCREEN DETECTED ===\n")
                append("window blank ≥${secs}s while RESUMED + focused + screen-on; ")
                append("activity=${act.localClassName}; bright px=$brightCount/${SAMPLE_W * SAMPLE_H}\n")
                append("main thread was responsive (an ANR would have been caught separately) — ")
                append("consistent with a lost/stale GL render surface, not a hang.\n")
                append("\n--- threads ---\n")
                append(threadDump())
                append("\n=== EVENT LOG ===\n")
                append(AppLog.snapshot())
            }
            File(ctx.filesDir, CrashHandler.PENDING_CRASH).writeText(report)
        }
    }

    /** Compact all-thread stack dump — the render/GL thread's state is the useful part here. */
    private fun threadDump(): String {
        val all = Thread.getAllStackTraces()
        val main = Looper.getMainLooper().thread
        return buildString {
            append("--- main thread ---\n")
            all[main]?.take(16)?.forEach { append("  at ").append(it).append('\n') }
            append("--- other threads ---\n")
            all.filterKeys { it != main }.forEach { (t, stack) ->
                append(t.name).append(":\n")
                stack.take(10).forEach { append("  at ").append(it).append('\n') }
            }
        }
    }
}
