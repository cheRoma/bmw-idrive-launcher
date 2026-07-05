package online.k73.bmwlauncher.music

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationAccessTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun not_granted_when_setting_empty() {
        Settings.Secure.putString(ctx.contentResolver, "enabled_notification_listeners", "")
        assertFalse(NotificationAccess.isGranted(ctx))
    }
    @Test fun granted_when_our_component_listed() {
        val comp = "${ctx.packageName}/${ctx.packageName}.music.MediaNotificationListener"
        Settings.Secure.putString(ctx.contentResolver, "enabled_notification_listeners", "com.other/x:$comp")
        assertTrue(NotificationAccess.isGranted(ctx))
    }
}
