package online.k73.bmwlauncher.diag

import org.junit.Assert.assertEquals
import org.junit.Test

class BlackScreenPolicyTest {

    private fun blank(p: BlackScreenPolicy, times: Int): List<BlackAction> =
        (1..times).map { p.onSample(BlackSample.BLANK) }

    @Test fun stays_quiet_until_the_window_has_been_blank_long_enough() {
        val p = BlackScreenPolicy()
        assertEquals(listOf(BlackAction.NONE, BlackAction.NONE), blank(p, 2))
    }

    @Test fun reports_once_the_streak_is_reached() {
        val p = BlackScreenPolicy()
        assertEquals(BlackAction.REPORT, blank(p, 3).last())
    }

    @Test fun escalates_map_then_activity_then_process_then_gives_up() {
        val p = BlackScreenPolicy()
        blank(p, 3) // REPORT
        // The first repair follows one sample later — the screen is already 7.5 s dead.
        assertEquals(BlackAction.DROP_MAP, p.onSample(BlackSample.BLANK))
        // Each further step waits samplesPerStep blank samples to see whether the last one worked.
        assertEquals(BlackAction.NONE, p.onSample(BlackSample.BLANK))
        assertEquals(BlackAction.RECREATE_ACTIVITY, p.onSample(BlackSample.BLANK))
        assertEquals(BlackAction.NONE, p.onSample(BlackSample.BLANK))
        assertEquals(BlackAction.RESTART_PROCESS, p.onSample(BlackSample.BLANK))
        // Nothing left to try — keep quiet rather than loop.
        assertEquals(listOf(BlackAction.NONE, BlackAction.NONE, BlackAction.NONE), blank(p, 3))
    }

    @Test fun a_healthy_frame_ends_the_episode_and_names_the_step_that_worked() {
        val p = BlackScreenPolicy()
        blank(p, 3)
        assertEquals(BlackAction.DROP_MAP, p.onSample(BlackSample.BLANK))
        assertEquals(BlackAction.RECOVERED, p.onSample(BlackSample.CONTENT))
        assertEquals(BlackAction.DROP_MAP, p.lastRepair)
        // Recovered means idle again: a healthy frame is not a second recovery.
        assertEquals(BlackAction.NONE, p.onSample(BlackSample.CONTENT))
    }

    @Test fun an_unjudgeable_sample_neither_advances_nor_ends_an_episode() {
        val p = BlackScreenPolicy()
        blank(p, 3)
        // Our own repairs (activity recreate) drop window focus, which makes samples unjudgeable.
        // The episode must survive that gap, otherwise escalation restarts from zero forever.
        assertEquals(BlackAction.NONE, p.onSample(BlackSample.UNKNOWN))
        assertEquals(BlackAction.NONE, p.onSample(BlackSample.UNKNOWN))
        assertEquals(BlackAction.DROP_MAP, p.onSample(BlackSample.BLANK))
    }

    @Test fun an_unjudgeable_sample_clears_a_streak_that_never_became_an_episode() {
        val p = BlackScreenPolicy()
        blank(p, 2)
        p.onSample(BlackSample.UNKNOWN) // e.g. a screen transition — not evidence of a dead window
        assertEquals(listOf(BlackAction.NONE, BlackAction.NONE), blank(p, 2))
        assertEquals(BlackAction.REPORT, p.onSample(BlackSample.BLANK))
    }

    @Test fun keeps_reporting_but_stops_repairing_after_repeated_episodes() {
        val p = BlackScreenPolicy(maxRepairedEpisodes = 2)
        repeat(2) {
            blank(p, 3)
            p.onSample(BlackSample.BLANK) // DROP_MAP
            p.onSample(BlackSample.CONTENT)
        }
        // Third episode: still worth a report, but self-healing that keeps failing is not.
        assertEquals(BlackAction.REPORT, blank(p, 3).last())
        assertEquals(listOf(BlackAction.NONE, BlackAction.NONE, BlackAction.NONE), blank(p, 3))
    }

    @Test fun counts_episodes_for_the_report() {
        val p = BlackScreenPolicy()
        blank(p, 3)
        p.onSample(BlackSample.CONTENT)
        blank(p, 3)
        assertEquals(2, p.episodes)
    }
}
