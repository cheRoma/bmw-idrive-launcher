package online.k73.bmwlauncher.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelBackoffTest {

    @Test fun the_first_retry_is_quick_then_it_backs_off() {
        val b = TunnelBackoff(firstDelayMs = 5_000, maxDelayMs = 300_000)
        assertEquals(5_000, b.nextDelayMs())
        assertEquals(10_000, b.nextDelayMs())
        assertEquals(20_000, b.nextDelayMs())
    }

    @Test fun it_never_waits_longer_than_the_cap() {
        // The car comes and goes; a runaway delay would leave it unreachable for an hour after a
        // rough patch of network.
        val b = TunnelBackoff(firstDelayMs = 5_000, maxDelayMs = 60_000)
        repeat(20) { b.nextDelayMs() }
        assertEquals(60_000, b.nextDelayMs())
    }

    @Test fun a_successful_connection_resets_the_wait() {
        val b = TunnelBackoff(firstDelayMs = 5_000, maxDelayMs = 300_000)
        repeat(5) { b.nextDelayMs() }
        b.reset()
        assertEquals(5_000, b.nextDelayMs())
    }

    @Test fun attempts_are_counted_for_the_status_line() {
        val b = TunnelBackoff()
        assertEquals(0, b.attempts)
        b.nextDelayMs(); b.nextDelayMs()
        assertEquals(2, b.attempts)
        b.reset()
        assertEquals(0, b.attempts)
    }

    @Test fun the_default_cap_is_minutes_not_hours() {
        val b = TunnelBackoff()
        repeat(50) { b.nextDelayMs() }
        assertTrue("cap must stay within a few minutes", b.nextDelayMs() <= 10 * 60_000)
    }
}
