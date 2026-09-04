package online.k73.bmwlauncher.autostart

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the shape of the fallback intent. The map leak the car reported (`created=2 destroyed=0`)
 * came from this call starting [online.k73.bmwlauncher.ui.HomeActivity] by class: that puts a second
 * instance in its own task while the home stack keeps the first, and each instance owns a map view.
 */
@RunWith(RobolectricTestRunner::class)
class BringHomeTest {

    @Test fun fallback_asks_the_system_for_home() {
        val intent = BringHome.homeIntent("online.k73.bmwlauncher")

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue("HOME category is what puts it in the home task", intent.hasCategory(Intent.CATEGORY_HOME))
    }

    @Test fun fallback_never_targets_our_class_directly() {
        val intent = BringHome.homeIntent("online.k73.bmwlauncher")

        // An explicit component is the regression: it bypasses home-task placement and builds a
        // second HomeActivity, hence a second map view that nothing ever destroys.
        assertNull(intent.component)
    }

    @Test fun fallback_can_only_open_our_own_launcher() {
        val intent = BringHome.homeIntent("online.k73.bmwlauncher")

        // Without the package pin, a moment when we are not the default HOME would hand the driver
        // somebody else's launcher.
        assertEquals("online.k73.bmwlauncher", intent.`package`)
    }

    @Test fun fallback_starts_a_task_and_does_not_animate() {
        val intent = BringHome.homeIntent("online.k73.bmwlauncher")

        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0)
    }

    @Test fun package_pin_follows_the_caller() {
        assertEquals("com.example.other", BringHome.homeIntent("com.example.other").`package`)
    }
}
