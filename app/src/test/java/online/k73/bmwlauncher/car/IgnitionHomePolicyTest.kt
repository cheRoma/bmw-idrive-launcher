package online.k73.bmwlauncher.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnitionHomePolicyTest {

    @Test fun a_cold_boot_counts_as_a_start() {
        // No previous reading at all: the unit just powered up with the key, which is exactly the
        // case where it restores YouTube over our launcher.
        val p = IgnitionHomePolicy()
        assertTrue(p.shouldOpenHome(ignitionOn = true, previous = null, nowMs = 0, launcherAlreadyInFront = false))
    }

    @Test fun the_ignition_going_on_opens_home() {
        val p = IgnitionHomePolicy()
        assertTrue(p.shouldOpenHome(true, previous = false, nowMs = 0, launcherAlreadyInFront = false))
    }

    @Test fun a_running_engine_never_pulls_the_driver_out_of_an_app() {
        val p = IgnitionHomePolicy()
        // Same state repeating (snapshots arrive several times a second).
        assertFalse(p.shouldOpenHome(true, previous = true, nowMs = 0, launcherAlreadyInFront = false))
        // And switching off is not a reason to show anything.
        assertFalse(p.shouldOpenHome(false, previous = true, nowMs = 0, launcherAlreadyInFront = false))
    }

    @Test fun nothing_happens_when_the_launcher_is_already_in_front() {
        val p = IgnitionHomePolicy()
        assertFalse(p.shouldOpenHome(true, previous = false, nowMs = 0, launcherAlreadyInFront = true))
    }

    @Test fun an_adapter_blip_does_not_read_as_a_second_start() {
        // The USB adapter drops on power dips and reconnects with a fresh reading; without the
        // debounce every dip would throw the driver back to the home screen.
        val p = IgnitionHomePolicy(debounceMs = 60_000)
        assertTrue(p.shouldOpenHome(true, previous = false, nowMs = 0, launcherAlreadyInFront = false))
        assertFalse(p.shouldOpenHome(true, previous = null, nowMs = 30_000, launcherAlreadyInFront = false))
    }

    @Test fun a_real_second_trip_still_opens_home() {
        val p = IgnitionHomePolicy(debounceMs = 60_000)
        assertTrue(p.shouldOpenHome(true, previous = false, nowMs = 0, launcherAlreadyInFront = false))
        assertTrue(p.shouldOpenHome(true, previous = false, nowMs = 120_000, launcherAlreadyInFront = false))
    }
}
