package online.k73.bmwlauncher.data

import online.k73.bmwlauncher.car.Trip
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripStoreTest {
    @Test fun json_round_trip() {
        val trips = listOf(
            Trip(1_000L, 2_401_000L, 35.2, 112, 284_082, "18.2 L/100"),
            Trip(5_000L, 65_000L, 0.8, 30),
        )
        assertEquals(trips, TripStore.decode(TripStore.encode(trips)))
    }
}
