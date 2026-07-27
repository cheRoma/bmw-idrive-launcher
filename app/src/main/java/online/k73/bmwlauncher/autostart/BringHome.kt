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
 * 2. a direct activity start, for when that service isn't enabled.
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
        runCatching {
            context.startActivity(
                Intent(context, HomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
            )
        }.onSuccess { AppLog.d("HOME", "$reason → вывел лаунчер напрямую") }
            .onFailure { AppLog.w("HOME", "$reason → не удалось: ${it.message}") }
    }
}
