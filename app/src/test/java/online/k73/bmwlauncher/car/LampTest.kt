package online.k73.bmwlauncher.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LampTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16) }.toIntArray()

    @Test fun decodes_the_brake_light_fault_seen_on_this_car() {
        // 2026-10-15 evening, while the cluster showed CHECK BRAKE LIGHTS.
        assertEquals(setOf(Lamp.BRAKE_LEFT, Lamp.REAR_LEFT), Lamp.faults(hex("D0 08 BF 5B 00 00 0A 0A 00 3C")))
    }

    @Test fun lights_on_without_faults() {
        assertEquals(emptySet<Lamp>(), Lamp.faults(hex("D0 08 BF 5B 03 00 00 00 07 38")))
    }

    @Test fun per_side_low_beam_replaces_the_aggregate() {
        assertEquals(setOf(Lamp.LOW_BEAM_LEFT), Lamp.faults(hex("D0 08 BF 5B 02 02 00 20 00 00")))
        assertEquals(setOf(Lamp.LOW_BEAM, Lamp.PLATE), Lamp.faults(hex("D0 08 BF 5B 02 82 00 00 00 00")))
    }

    @Test fun ignores_other_frames() {
        assertNull(Lamp.faults(hex("D0 05 BF 5C FF 00 C9")))
    }
}
