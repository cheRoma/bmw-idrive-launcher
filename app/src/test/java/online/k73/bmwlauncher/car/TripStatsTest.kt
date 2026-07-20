package online.k73.bmwlauncher.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStatsTest {

    @Test fun noDataYet() {
        val t = TripStats()
        assertFalse(t.hasData)
        assertEquals(0.0, t.averageKmh, 1e-6)
    }

    @Test fun constantSpeedAveragesToThatSpeed() {
        val t = TripStats()
        var now = 1_000L
        t.onSpeed(60, now) // baseline sample (establishes lastMs)
        repeat(100) { now += 100; t.onSpeed(60, now) } // 10 s at 60
        assertTrue(t.hasData)
        assertEquals(60.0, t.averageKmh, 1e-6)
    }

    @Test fun stopsLowerTheAverage() {
        val t = TripStats()
        var now = 0L
        t.onSpeed(100, now)
        repeat(50) { now += 100; t.onSpeed(100, now) } // 5 s @ 100
        repeat(50) { now += 100; t.onSpeed(0, now) }   // 5 s stopped
        assertEquals(50.0, t.averageKmh, 1e-6)         // half distance over full time
    }

    @Test fun longGapIsSkipped() {
        val t = TripStats()
        t.onSpeed(60, 0L)
        t.onSpeed(60, 100L)       // 100 ms @ 60 counts
        t.onSpeed(200, 10_000L)   // 10 s gap > maxGap → not accumulated
        assertEquals(60.0, t.averageKmh, 1e-6)
    }

    @Test fun distanceAccumulates() {
        val t = TripStats()
        var now = 0L
        t.onSpeed(60, now)
        repeat(3600) { now += 1000; t.onSpeed(60, now) } // 1 h at 60 km/h
        assertEquals(60.0, t.tripKm, 0.1)
    }

    @Test fun resetZeroes() {
        val t = TripStats()
        t.onSpeed(60, 0L); t.onSpeed(60, 1_000L)
        t.reset()
        assertFalse(t.hasData)
        assertEquals(0.0, t.averageKmh, 1e-6)
    }
}
