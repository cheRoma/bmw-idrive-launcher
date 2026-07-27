package online.k73.bmwlauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLifecycleTest {

    private fun destroyedCount(): Int =
        Regex("destroyed=(\\d+)").find(MapRuntime.state())!!.groupValues[1].toInt()

    @Test fun a_view_that_never_reached_the_screen_is_still_destroyed() {
        // The leak seen in the car: Compose abandoned the composition, so no effect ever ran and the
        // GL context stayed alive with nothing left to close it.
        var destroyed = 0
        val life = MapLifecycle("map") { destroyed++ }
        life.onAbandoned()
        assertEquals(1, destroyed)
        assertTrue(life.released)
    }

    @Test fun the_normal_path_destroys_it_too() {
        var destroyed = 0
        val life = MapLifecycle("map") { destroyed++ }
        life.onRemembered()
        life.onForgotten()
        assertEquals(1, destroyed)
    }

    @Test fun release_runs_once_however_many_times_teardown_calls_it() {
        var destroyed = 0
        val life = MapLifecycle("map") { destroyed++ }
        life.onForgotten()
        life.onAbandoned()
        life.release("вручную")
        assertEquals(1, destroyed)
    }

    @Test fun a_failing_teardown_still_counts_as_released() {
        // MapLibre can throw while tearing down a half-initialised view; swallowing it must not
        // leave the counter claiming a live map that no longer exists.
        val life = MapLifecycle("map") { error("boom") }
        life.onForgotten()
        assertTrue(life.released)
    }

    @Test fun the_diagnostics_counter_follows_every_release() {
        val before = destroyedCount()
        MapLifecycle("map") { }.onForgotten()
        MapLifecycle("map") { }.onAbandoned()
        assertEquals(before + 2, destroyedCount())
    }

    @Test fun a_view_that_failed_to_build_still_balances_the_counter() {
        // nextInstance() has already counted it as created, so the report would claim a live map
        // forever if a failed build skipped the release path.
        val before = destroyedCount()
        MapLifecycle<String>(null) { error("must not be called") }.onAbandoned()
        assertEquals(before + 1, destroyedCount())
    }

    @Test fun instances_are_numbered_so_the_report_can_be_read() {
        val first = MapLifecycle("map") { }
        val second = MapLifecycle("map") { }
        assertTrue("${first.instance} < ${second.instance}", first.instance < second.instance)
        assertFalse(first.released)
        first.release("cleanup")
        second.release("cleanup")
    }
}
