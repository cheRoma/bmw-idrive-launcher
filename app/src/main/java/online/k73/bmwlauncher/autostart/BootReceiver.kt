package online.k73.bmwlauncher.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import online.k73.bmwlauncher.ui.HomeActivity

/**
 * On boot, bring the launcher HOME to the foreground. The heavy lifting (i-Bus autostart with
 * retries) runs in HomeActivity.onResume so it shares the same coroutine scope and lifecycle.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startActivity(
                Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
