package online.k73.bmwlauncher.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTrackerTest {
    /** Drive at [kmh] for [seconds], one IKE sample every 2 s, starting at [from]. Returns end time. */
    private fun TripTracker.drive(kmh: Int, seconds: Int, from: Long): Long {
        var t = from
        repeat(seconds / 2) { onSpeed(kmh, t, 284_117); t += 2_000 }
        return t
    }

    @Test fun integrates_a_steady_cruise() {
        val tr = TripTracker()
        tr.drive(60, 600, 0L)                       // 10 min at 60 km/h ≈ 10 km
        val trip = tr.end("18.2 L/100")
        assertNotNull(trip)
        assertEquals(10.0, trip!!.distanceKm, 0.15)
        assertEquals(60, trip.maxSpeedKmh)
        assertEquals(284_117, trip.startOdometerKm)
        assertEquals("18.2 L/100", trip.consumption)
    }

    @Test fun stops_stay_inside_the_trip() {
        val tr = TripTracker()
        var t = tr.drive(50, 120, 0L)
        t = tr.drive(0, 60, t)                       // a red light
        tr.drive(50, 120, t)
        val trip = tr.end(null)!!
        assertEquals(3.3, trip.distanceKm, 0.1)
        assertEquals(4, trip.durationMin.toInt())
    }

    @Test fun idle_cluster_ends_the_trip_and_short_hops_are_dropped() {
        val tr = TripTracker()
        val t = tr.drive(10, 20, 0L)                 // ~55 m
        assertFalse(tr.idle(t + 60_000, t))
        assertTrue(tr.idle(t + 130_000, t))
        assertNull(tr.end(null))
        assertFalse(tr.active)
    }

    @Test fun nothing_starts_while_parked() {
        val tr = TripTracker()
        tr.drive(0, 60, 0L)
        assertFalse(tr.active)
    }
}

class BordFormatTest {
    @Test fun cluster_texts() {
        assertEquals("18,2", BordFormat.consumption("18.2 L/100"))
        assertNull(BordFormat.consumption("---.- L/100"))
        assertEquals("319", BordFormat.rangeKm("319 KM"))
        assertEquals("12,6", BordFormat.voltsText(179))
    }

    @Test fun battery_warning_only_with_the_engine_off() {
        assertNull(BordFormat.batteryNotice(179, null))       // 12.6 V, fine
        assertEquals("АКБ 11,6 В", BordFormat.batteryNotice(165, 0))
        assertNull(BordFormat.batteryNotice(165, 800))        // engine running: reading means little
        assertNull(BordFormat.batteryNotice(null, null))
    }
}
