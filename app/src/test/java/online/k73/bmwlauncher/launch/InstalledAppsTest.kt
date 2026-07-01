package online.k73.bmwlauncher.launch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstalledAppsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun returns_launchable_apps_sorted_by_label() {
        val apps = InstalledApps(context).list()
        // Robolectric provides at least the test package; result must be non-null and sorted.
        val labels = apps.map { it.label }
        assertTrue(labels == labels.sortedBy { it.lowercase() })
    }
}
