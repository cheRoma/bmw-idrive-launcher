package online.k73.bmwlauncher.diag

/** What a single window sample told us. */
enum class BlackSample {
    /** The window rendered nothing — no tile, no clock, no amber. */
    BLANK,

    /** Normal content on screen. */
    CONTENT,

    /** Not judgeable: no focus, screen off, or the pixel copy failed. Evidence of nothing. */
    UNKNOWN,
}

/** What the watchdog should do about it. */
enum class BlackAction {
    NONE,

    /** Persist + upload the evidence. Always the first thing that happens in an episode. */
    REPORT,

    /** Take the MapLibre view out of the tree — the prime suspect, and the cheapest thing to undo. */
    DROP_MAP,

    /** Rebuild the activity (new window, same process). */
    RECREATE_ACTIVITY,

    /** Last resort — the only cure known to work by hand (force-stop + relaunch). */
    RESTART_PROCESS,

    /** Content came back: the episode is over, and [BlackScreenPolicy.lastRepair] names the cure. */
    RECOVERED,
}

/**
 * Decision logic of [BlackScreenWatchdog], kept free of Android so the escalation can be tested on
 * the JVM — the failure itself only happens in the car, so the rules must at least be provably right
 * here.
 *
 * An episode starts when the window has been blank for [samplesToTrigger] samples in a row, and ends
 * only when real content returns. In between we escalate through the repairs above, one step at a
 * time, waiting [samplesPerStep] samples after each to see whether it took — so the log of the next
 * episode names the layer that was actually broken instead of leaving us to guess again.
 *
 * Two deliberate rules:
 * - an UNKNOWN sample never ends an episode, because our own repairs (recreate, restart) drop window
 *   focus and would otherwise reset the escalation to zero on every step;
 * - after [maxRepairedEpisodes] the watchdog still reports but stops repairing — self-healing that
 *   keeps failing is a restart loop in a car, which is worse than a black screen.
 */
class BlackScreenPolicy(
    private val samplesToTrigger: Int = 3,
    private val samplesPerStep: Int = 2,
    private val maxRepairedEpisodes: Int = 3,
) {
    private enum class Stage { IDLE, REPORTED, MAP_DROPPED, ACTIVITY_RECREATED, RESTARTED }

    private var stage = Stage.IDLE
    private var blankStreak = 0
    private var stepStreak = 0

    /** Episodes seen since the process started — reported so a repeat offender is visible. */
    var episodes = 0
        private set

    /** The last repair attempted in the current (or just-finished) episode. */
    var lastRepair = BlackAction.NONE
        private set

    /** True between the first report and the return of real content. */
    val inEpisode: Boolean get() = stage != Stage.IDLE

    fun onSample(sample: BlackSample): BlackAction = when (sample) {
        BlackSample.CONTENT -> onContent()
        BlackSample.UNKNOWN -> onUnknown()
        BlackSample.BLANK -> onBlank()
    }

    private fun onContent(): BlackAction {
        blankStreak = 0
        stepStreak = 0
        if (stage == Stage.IDLE) return BlackAction.NONE
        stage = Stage.IDLE
        return BlackAction.RECOVERED
    }

    private fun onUnknown(): BlackAction {
        if (stage == Stage.IDLE) blankStreak = 0
        return BlackAction.NONE
    }

    private fun onBlank(): BlackAction {
        if (stage == Stage.IDLE) {
            blankStreak++
            if (blankStreak < samplesToTrigger) return BlackAction.NONE
            stage = Stage.REPORTED
            stepStreak = 0
            episodes++
            lastRepair = BlackAction.NONE
            return BlackAction.REPORT
        }
        stepStreak++
        // The first repair follows the report immediately — the window has been dead for the whole
        // trigger window already. Later steps wait, to judge whether the previous one helped.
        val needed = if (stage == Stage.REPORTED) 1 else samplesPerStep
        if (stepStreak < needed) return BlackAction.NONE
        stepStreak = 0
        if (episodes > maxRepairedEpisodes) return BlackAction.NONE
        val repair = when (stage) {
            Stage.REPORTED -> { stage = Stage.MAP_DROPPED; BlackAction.DROP_MAP }
            Stage.MAP_DROPPED -> { stage = Stage.ACTIVITY_RECREATED; BlackAction.RECREATE_ACTIVITY }
            Stage.ACTIVITY_RECREATED -> { stage = Stage.RESTARTED; BlackAction.RESTART_PROCESS }
            Stage.RESTARTED, Stage.IDLE -> BlackAction.NONE
        }
        if (repair != BlackAction.NONE) lastRepair = repair
        return repair
    }
}
