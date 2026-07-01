package online.k73.bmwlauncher.launch

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppLauncherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launcher = AppLauncher(context)

    @Test fun missing_package_is_not_installed() {
        assertFalse(launcher.isInstalled("com.nope.missing"))
        assertNull(launcher.launchIntentFor("com.nope.missing"))
    }

    @Test fun installed_package_yields_launch_intent() {
        val pkg = "ru.yandex.yandexnavi"
        val pm = shadowOf(context.packageManager)
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
        pm.addActivityIfNotPresent(android.content.ComponentName(pkg, "$pkg.Main"))
        pm.addIntentFilterForActivity(
            android.content.ComponentName(pkg, "$pkg.Main"),
            android.content.IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        )
        assertTrue(launcher.isInstalled(pkg))
        val intent = launcher.launchIntentFor(pkg)
        assertTrue(intent != null && intent.hasCategory(Intent.CATEGORY_LAUNCHER))
    }
}
