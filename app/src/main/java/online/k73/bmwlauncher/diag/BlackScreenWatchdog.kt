package online.k73.bmwlauncher.diag

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import online.k73.bmwlauncher.ui.home.MapRuntime
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Durable **black-screen** detector and repair — the failure mode the ANR watchdog can't see. On this
 * head unit the launcher window sometimes goes fully black (first blamed on the ACC sleep/wake cycle;
 * the first captured episode instead followed a return to the home screen, i.e. a fresh MapLibre GL
 * context) while the **main thread stays responsive**, so [AnrWatchdog] never fires.
 *
 * Detection: every [SAMPLE_INTERVAL_MS] — only while one of OUR activities is RESUMED + window-focused
 * + the screen is on — grab a tiny [PixelCopy] of the window and count pixels carrying real
 * brightness. A healthy launcher screen always has amber/grey content; a black window has none.
 *
 * What [BlackScreenPolicy] does with those samples: report first (persisted to
 * [CrashHandler.PENDING_CRASH] so an episode the user power-cycles past still ships later), then
 * escalate through repairs — drop the map, recreate the activity, restart the process — pausing after
 * each to see whether pixels came back. Whichever step ends the episode is written to the log, so the
 * next report names the broken layer instead of leaving us to guess.
 *
 * The report carries the evidence needed to tell the two candidate faults apart:
 * - a **software draw** of the same window (bypasses the GPU): content there but not on screen means
 *   the view tree is fine and the GPU/composition path is what died;
 * - the **draw count** since the previous sample: zero means the UI stopped drawing at all;
 * - the **map churn** ([MapRuntime]) and a **logcat tail**, where the driver's own EGL errors land.
 *
 * Never crashes the app; every step is wrapped.
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
    // Enough logcat to cover the seconds around the failure without bloating the pending file.
    private const val REPORT_LOGCAT_LINES = 800
    private const val REPORT_LOGCAT_BYTES = 96 * 1024
    // A process restart is the last resort; two of them in quick succession would be a boot loop.
    private const val MIN_RESTART_INTERVAL_MS = 5 * 60_000L
    private const val PREFS = "diag"
    private const val KEY_LAST_RESTART = "black_last_restart_ms"

    @Volatile private var started = false
    @Volatile private var appContext: Context? = null
    @Volatile private var current: WeakReference<Activity>? = null
    private var onEvent: (String) -> Unit = {}
    private var pm: PowerManager? = null
    private lateinit var handler: Handler

    // Counted on the main thread during draw dispatch, read from the watchdog thread.
    private val drawCount = AtomicLong()
    private val drawListener = ViewTreeObserver.OnDrawListener { drawCount.incrementAndGet() }

    // Touched only from the watchdog thread.
    private val policy = BlackScreenPolicy()
    private var lastDrawCount = 0L
    private var lastDrawDelta = 0L

    /** [onEvent] receives an upload reason: "blackscreen" when it breaks, "blackscreen-ok" when healed. */
    fun start(app: Application, onEvent: (String) -> Unit = {}) {
        if (started) return
        started = true
        appContext = app.applicationContext
        this.onEvent = onEvent
        pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ht = HandlerThread("blackscreen-watchdog").apply { isDaemon = true; start() }
        handler = Handler(ht.looper)
        app.registerActivityLifecycleCallbacks(lifecycle)
        handler.postDelayed(tick, SAMPLE_INTERVAL_MS)
    }

    // registerActivityLifecycleCallbacks only ever reports OUR OWN activities, so `current` is always
    // one of ours — no package check needed. Cleared on pause so we never sample another app.
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            current = WeakReference(activity)
            runCatching { activity.window.decorView.viewTreeObserver.addOnDrawListener(drawListener) }
        }

        override fun onActivityPaused(activity: Activity) {
            runCatching { activity.window.decorView.viewTreeObserver.removeOnDrawListener(drawListener) }
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
        val now = drawCount.get()
        lastDrawDelta = now - lastDrawCount
        lastDrawCount = now
        val act = current?.get()
        val win = act?.window
        // Only judge while our window is actually meant to be showing pixels. A backgrounded app, a
        // window without focus (dialog/transition/our own repair), or a screen that's off is
        // legitimately blank — and must not end an episode either.
        if (act == null || win == null || !win.decorView.hasWindowFocus() || pm?.isInteractive != true) {
            handle(policy.onSample(BlackSample.UNKNOWN), act, 0)
            reschedule()
            return
        }
        val bmp = Bitmap.createBitmap(SAMPLE_W, SAMPLE_H, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(win, bmp, { result ->
                runCatching {
                    if (result == PixelCopy.SUCCESS) {
                        analyze(bmp, act)
                    } else {
                        // Surface not grabbable this tick — evidence of nothing.
                        handle(policy.onSample(BlackSample.UNKNOWN), act, 0)
                    }
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
        val bright = brightPixels(bmp)
        val blank = bright <= MAX_BRIGHT_FOR_BLACK
        handle(policy.onSample(if (blank) BlackSample.BLANK else BlackSample.CONTENT), act, bright)
    }

    private fun brightPixels(bmp: Bitmap): Int {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        var bright = 0
        for (c in px) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (maxOf(r, g, b) > BRIGHT_CHANNEL_THRESHOLD && ++bright > MAX_BRIGHT_FOR_BLACK) break
        }
        return bright
    }

    private fun handle(action: BlackAction, act: Activity?, bright: Int) {
        when (action) {
            BlackAction.NONE -> Unit
            BlackAction.REPORT -> act?.let { report(it, bright) }
            BlackAction.DROP_MAP -> dropMap()
            BlackAction.RECREATE_ACTIVITY -> recreateActivity(act)
            BlackAction.RESTART_PROCESS -> restartProcess()
            BlackAction.RECOVERED -> recovered()
        }
    }

    private fun report(act: Activity, brightCount: Int) {
        val secs = 3 * SAMPLE_INTERVAL_MS / 1000
        val probe = softwareProbe(act)
        runCatching {
            AppLog.e(
                "BLACKSCREEN",
                "launcher window blank ≥${secs}s in ${act.localClassName} " +
                    "(bright px=$brightCount/${SAMPLE_W * SAMPLE_H}, эпизод #${policy.episodes}) — " +
                    "software draw: $probe, draws/2.5s=$lastDrawDelta, map ${MapRuntime.state()}",
            )
        }
        persist(act, brightCount, secs, probe)
        // Already non-blocking (schedules an IO upload); a failed live upload leaves the persisted
        // pending file to ship on the next launch.
        runCatching { onEvent("blackscreen") }
    }

    private fun recovered() {
        runCatching {
            AppLog.e("BLACKSCREEN", "экран вернулся после ${policy.lastRepair} (эпизод #${policy.episodes})")
        }
        // Upload now: the event ring buffer is minutes deep on this car, so waiting for the next
        // report would lose which repair worked — the whole point of the escalation.
        runCatching { onEvent("blackscreen-ok") }
    }

    /**
     * Draw the same window into a **software** canvas on the main thread. It bypasses the GPU
     * entirely, so a bright result next to a black [PixelCopy] means the view tree is alive and the
     * render/composition path is the broken layer. (A TextureView contributes nothing here — that is
     * expected and does not affect the verdict, which rests on the tiles, ribbon and labels.)
     */
    private fun softwareProbe(act: Activity): String {
        var result = "n/a"
        val latch = CountDownLatch(1)
        val posted = runCatching {
            Handler(Looper.getMainLooper()).post {
                result = runCatching { drawToBitmapDescription(act.window.decorView) }
                    .getOrElse { "failed: ${it.message}" }
                latch.countDown()
            }
        }.getOrDefault(false)
        if (!posted) return "not posted"
        return if (latch.await(2, TimeUnit.SECONDS)) result else "main thread did not answer in 2s"
    }

    private fun drawToBitmapDescription(decor: View): String {
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0) return "window has no size"
        val bmp = Bitmap.createBitmap(SAMPLE_W, SAMPLE_H, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bmp)
            canvas.scale(SAMPLE_W.toFloat() / w, SAMPLE_H.toFloat() / h)
            decor.draw(canvas)
            return "bright px=${brightPixels(bmp)}/${SAMPLE_W * SAMPLE_H}"
        } finally {
            runCatching { bmp.recycle() }
        }
    }

    private fun dropMap() {
        AppLog.e("BLACKSCREEN", "ремонт 1/3: убираю карту с главного экрана")
        runCatching { Handler(Looper.getMainLooper()).post { MapRuntime.disable("black screen") } }
    }

    private fun recreateActivity(act: Activity?) {
        AppLog.e("BLACKSCREEN", "ремонт 2/3: пересоздаю Activity")
        val a = act ?: current?.get() ?: return
        runCatching { Handler(Looper.getMainLooper()).post { runCatching { a.recreate() } } }
    }

    /**
     * The cure that is known to work by hand (force-stop + relaunch): start the launcher afresh, then
     * end this process. As the HOME app the system brings us straight back, and the new process comes
     * up with the map enabled again — so if the map really is the culprit, the pattern repeats in the
     * log instead of hiding behind a permanent workaround.
     */
    private fun restartProcess() {
        val ctx = appContext ?: return
        val prefs = runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()
        val last = prefs?.getLong(KEY_LAST_RESTART, 0L) ?: 0L
        val now = System.currentTimeMillis()
        if (last > 0 && now - last < MIN_RESTART_INTERVAL_MS) {
            AppLog.e("BLACKSCREEN", "ремонт 3/3 пропущен: перезапуск был ${(now - last) / 1000}s назад")
            return
        }
        AppLog.e("BLACKSCREEN", "ремонт 3/3: перезапускаю процесс лаунчера")
        runCatching { prefs?.edit()?.putLong(KEY_LAST_RESTART, now)?.commit() }
        runCatching {
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                ?.let { ctx.startActivity(it) }
        }
        // Give the report upload and the relaunch intent a moment before the process goes away.
        handler.postDelayed({ Process.killProcess(Process.myPid()) }, 1_500L)
    }

    /**
     * Write a durable report to filesDir/[CrashHandler.PENDING_CRASH] so a black screen the user
     * power-cycles past is still uploaded on the next launch (MainApplication). Shares the pending
     * file with [CrashHandler]/[AnrWatchdog] — last writer wins, which is fine for these rare events.
     */
    private fun persist(act: Activity, brightCount: Int, secs: Long, probe: String) {
        val ctx = appContext ?: return
        runCatching {
            val report = buildString {
                append(DeviceInfo.collect(ctx))
                append("\n=== BLACK SCREEN DETECTED ===\n")
                append("window blank ≥${secs}s while RESUMED + focused + screen-on; ")
                append("activity=${act.localClassName}; bright px=$brightCount/${SAMPLE_W * SAMPLE_H}; ")
                append("episode #${policy.episodes}\n")
                append("main thread was responsive (an ANR would have been caught separately).\n")
                append("\n--- probes ---\n")
                append("software draw (GPU bypassed): ").append(probe).append('\n')
                append("draws recorded in the last ").append(SAMPLE_INTERVAL_MS / 1000).append("s: ")
                append(lastDrawDelta).append('\n')
                append("map: ").append(MapRuntime.state()).append('\n')
                append("\n--- threads ---\n")
                append(threadDump())
                append("\n=== EVENT LOG ===\n")
                append(AppLog.snapshot())
                // Kept last and bounded: this is where the driver's own EGL/HWUI errors land, and it
                // is the piece that goes missing when the live upload can't reach the server.
                append("\n=== LOGCAT (tail) ===\n")
                append(LogUploader.captureLogcat(REPORT_LOGCAT_LINES, REPORT_LOGCAT_BYTES))
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
