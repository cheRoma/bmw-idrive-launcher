package online.k73.bmwlauncher.autostart

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import online.k73.bmwlauncher.car.ButtonRedirectService
import online.k73.bmwlauncher.diag.AppLog
import online.k73.bmwlauncher.ui.HomeActivity

/** Whether one of our screens is the thing the driver is looking at right now. */
object LauncherForeground {
    @Volatile
    var isResumed: Boolean = false
}

/**
 * Puts the launcher back in front after the car is started.
 *
 * The head unit restores whatever was open before it slept — often YouTube or the navigator — so the
 * driver turns the key and lands in the wrong app. This does the equivalent of pressing Home.
 *
 * Two paths, in order of reliability on this ROM:
 * 1. **the accessibility service**, which is allowed to act from the background and whose
 *    `GLOBAL_ACTION_HOME` is literally the Home button — the system then opens us, since we are the
 *    default HOME app;
 * 2. a HOME intent, for when that service isn't enabled — see [homeIntent] for why it must be a HOME
 *    intent and not a start of our own class.
 *
 * And it fires **twice**, spaced out: the ROM finishes restoring its own last app a beat after the
 * screen comes up, and a single early attempt would simply be covered by it.
 */
object BringHome {
    private val handler = Handler(Looper.getMainLooper())
    private val ATTEMPT_DELAYS_MS = longArrayOf(2_500, 7_000)

    fun afterIgnition(context: Context) {
        val app = context.applicationContext
        ATTEMPT_DELAYS_MS.forEach { delay ->
            handler.postDelayed({ now(app, "зажигание +${delay / 1000}с") }, delay)
        }
    }

    fun now(context: Context, reason: String) {
        if (LauncherForeground.isResumed) {
            AppLog.d("HOME", "$reason → лаунчер уже впереди")
            return
        }
        if (ButtonRedirectService.pressHome()) {
            AppLog.d("HOME", "$reason → «Домой» через службу доступности")
            return
        }
        runCatching { context.startActivity(homeIntent(context.packageName)) }
            .onSuccess { AppLog.d("HOME", "$reason → вывел лаунчер напрямую") }
            .onFailure { AppLog.w("HOME", "$reason → не удалось: ${it.message}") }
    }

    /**
     * The fallback asks the system for HOME instead of starting [HomeActivity] by class.
     *
     * Starting our own component with `NEW_TASK` builds a SECOND HomeActivity in a task of its own:
     * the home stack keeps the instance the system launched, and ours lands beside it. Two live
     * activities mean two live map views, and the car reported exactly that — `created=2 destroyed=0`
     * in one process, with no `view#… destroyed` line in between. A HOME intent is placed in the home
     * task, so an existing instance is reused (`onNewIntent`) instead of a new one being created.
     * This also makes the fallback mean the same thing as path 1, which is literally the Home button.
     *
     * Pinned to our own package: without it, a moment when we are not the default HOME would open
     * somebody else's launcher instead of ours.
     */
    fun homeIntent(packageName: String): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
}
