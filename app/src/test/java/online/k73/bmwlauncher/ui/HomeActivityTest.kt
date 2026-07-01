package online.k73.bmwlauncher.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeActivityTest {
    @Test fun home_activity_starts() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test fun app_context_available() {
        assertNotNull(ApplicationProvider.getApplicationContext<android.content.Context>())
    }
}
