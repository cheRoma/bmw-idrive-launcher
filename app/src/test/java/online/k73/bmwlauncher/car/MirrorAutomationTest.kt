package online.k73.bmwlauncher.car

import online.k73.bmwlauncher.car.KeyPosition.ACC
import online.k73.bmwlauncher.car.KeyPosition.IGNITION
import online.k73.bmwlauncher.car.KeyPosition.OFF
import online.k73.bmwlauncher.car.KeyPosition.START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MirrorAutomationTest {
    private fun decide(prev: KeyPosition?, now: KeyPosition?, enabled: Boolean = true, speed: Int? = 0) =
        MirrorAutomation.decide(prev, now, enabled, speed)

    @Test fun folds_when_the_key_goes_off() {
        assertEquals(MirrorAction.FOLD, decide(IGNITION, OFF))
        assertEquals(MirrorAction.FOLD, decide(ACC, OFF))
    }

    @Test fun unfolds_when_the_key_reaches_ignition() {
        assertEquals(MirrorAction.UNFOLD, decide(OFF, IGNITION))
        assertEquals(MirrorAction.UNFOLD, decide(ACC, IGNITION))
        assertEquals(MirrorAction.UNFOLD, decide(OFF, START))
    }

    @Test fun ignores_the_step_between_ignition_and_start() {
        // Cranking passes IGNITION → START → IGNITION; the mirrors are already out by then.
        assertNull(decide(IGNITION, START))
        assertNull(decide(START, IGNITION))
    }

    @Test fun dropping_to_acc_does_nothing() {
        // Engine off but the driver is still sitting there with the radio on.
        assertNull(decide(IGNITION, ACC))
        assertNull(decide(OFF, ACC))
    }

    @Test fun never_folds_while_the_car_is_moving() {
        assertNull("a fold at speed must be vetoed", decide(IGNITION, OFF, speed = 30))
        assertEquals(MirrorAction.FOLD, decide(IGNITION, OFF, speed = 0))
    }

    @Test fun unknown_speed_still_allows_folding() {
        // The key just left the barrel; blocking on "we never decoded a speed" would mean the
        // feature never works on a bus where 0x18 is quiet.
        assertEquals(MirrorAction.FOLD, decide(IGNITION, OFF, speed = null))
    }

    @Test fun unfolding_is_allowed_even_with_a_stale_moving_speed() {
        assertEquals(MirrorAction.UNFOLD, decide(OFF, IGNITION, speed = 30))
    }

    @Test fun does_nothing_without_a_previous_reading() {
        assertNull("app just started — this is not a transition", decide(null, OFF))
        assertNull(decide(null, IGNITION))
    }

    @Test fun does_nothing_when_the_position_did_not_change() {
        for (p in KeyPosition.values()) assertNull(decide(p, p))
    }

    @Test fun does_nothing_when_disabled() {
        assertNull(decide(IGNITION, OFF, enabled = false))
        assertNull(decide(OFF, IGNITION, enabled = false))
    }

    @Test fun does_nothing_when_the_current_position_is_unknown() {
        assertNull(decide(IGNITION, null))
    }
}
