package online.k73.bmwlauncher.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object NotificationAccess {
    /** True if our NotificationListenerService is enabled in Settings → Notification access. */
    fun isGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
    }

    /** Intent to the system screen where the user grants notification access. */
    fun settingsIntent(): Intent =
        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
