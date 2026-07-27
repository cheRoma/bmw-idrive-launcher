package online.k73.bmwlauncher.car

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import online.k73.bmwlauncher.diag.AppLog
import online.k73.bmwlauncher.ui.HomeActivity

/**
 * Redirects hardware panel buttons to our own screens.
 *
 * The XTRONS panel buttons are handled by the MCU and fire a hard-coded `startActivity` of a stock
 * Microntek app — they emit NO interceptable Android key event (verified live via getevent: only
 * «Назад» sends a real KEY_BACK). We can't catch the key, but we CAN catch the stock app coming to
 * the foreground here and jump to our matching screen instead. Downside: a brief flash of the stock
 * app before we take over.
 *
 * To add a mapping later: add one line to [map] (and there's an equivalent screen).
 */
class ButtonRedirectService : AccessibilityService() {

    /** Stock package the MCU launches → our nav route to open instead. */
    private val map = mapOf(
        "com.microntek.music" to "music",
    )

    private var lastRedirectMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val route = map[pkg] ?: return
        // Debounce: a single button press can raise several window events.
        val now = SystemClock.uptimeMillis()
        if (now - lastRedirectMs < 1500) return
        lastRedirectMs = now
        AppLog.d("BTNREDIR", "$pkg → route $route")
        runCatching {
            startActivity(
                Intent(this, HomeActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    )
                    putExtra(HomeActivity.EXTRA_NAV_ROUTE, route)
                },
            )
        }.onFailure { AppLog.w("BTNREDIR", "redirect failed: ${it.message}") }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppLog.d("BTNREDIR", "служба доступности подключена")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    companion object {
        @Volatile
        private var instance: ButtonRedirectService? = null

        /**
         * Press Home on behalf of the app. An accessibility service may do this from the background,
         * which nothing else here can — see [online.k73.bmwlauncher.autostart.BringHome].
         *
         * @return true when the press was delivered.
         */
        fun pressHome(): Boolean {
            val service = instance ?: return false
            return runCatching { service.performGlobalAction(GLOBAL_ACTION_HOME) }.getOrDefault(false)
        }
    }
}
