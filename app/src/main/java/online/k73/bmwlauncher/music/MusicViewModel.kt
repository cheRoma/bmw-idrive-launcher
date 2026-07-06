package online.k73.bmwlauncher.music

import android.content.Context
import android.media.session.MediaController
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import online.k73.bmwlauncher.diag.AppLog

/**
 * Owns the MusicUiState. Binds to Yandex Music's active session, re-reads on callbacks,
 * and ticks the position once a second while playing. Call [start]/[stop] with a scope.
 */
class MusicViewModel(
    private val context: Context,
    private val repo: MediaSessionRepository,
    private val targetPackage: String,
) {
    private val _state = MutableStateFlow<MusicUiState>(MusicUiState.NoPlayback)
    val state: StateFlow<MusicUiState> = _state

    private val _coldStart = MutableStateFlow(ColdStartPhase.IDLE)
    val coldStart: StateFlow<ColdStartPhase> = _coldStart

    private var controller: MusicController? = null
    private var rawController: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var sessionsListener: android.media.session.MediaSessionManager.OnActiveSessionsChangedListener? = null

    private var tickJob: kotlinx.coroutines.Job? = null
    private var wakeJob: kotlinx.coroutines.Job? = null
    // Foreground launcher (opens the Yandex app so «Моя волна» starts). Stored so the retry button
    // can trigger it; injected by the UI via [start].
    private var foregroundLauncher: ((String) -> Unit)? = null

    fun start(scope: CoroutineScope, launchForeground: (String) -> Unit) {
        this.foregroundLauncher = launchForeground
        if (tickJob?.isActive == true) return // already running — don't stack tick loops
        sessionsListener = repo.observeSessions { rebind(); refresh() }
        rebind(); refresh()
        // Re-pick the active session AND refresh every second. The notification listener can connect
        // late, and Yandex may swap to a real playing session after it opens — this keeps us bound to
        // whatever is currently active instead of sitting on a stale/empty session (the "grey screen").
        tickJob = scope.launch {
            while (true) { rebind(); refresh(); delay(1000) }
        }
        // Auto cold-start: opening Music with nothing playing should try to start it, no extra tap.
        if (controller?.isPlaying() != true) wake(scope)
    }

    /**
     * Background wake: send a media-button PLAY (no full-app UI) and, if a session binds, nudge it a
     * few times over ~3s. This resumes a WARM (paused) Yandex silently. A truly-killed Yandex comes
     * up idle with no queue, so play() is a no-op → we mark [ColdStartPhase.FAILED] and the UI shows
     * an explicit "Включить музыку" button (which opens Yandex foreground — the only reliable way).
     */
    fun wake(scope: CoroutineScope) {
        if (_coldStart.value == ColdStartPhase.WAKING) return
        AppLog.d("MUSIC", "cold-start: background wake -> $targetPackage")
        _coldStart.value = ColdStartPhase.WAKING
        repo.sendPlay(targetPackage)
        wakeJob?.cancel()
        wakeJob = scope.launch {
            var tries = 0
            while (tries < 6) { // ~3s, bounded (no infinite play() spam)
                delay(500); rebind(); refresh()
                controller?.let { ctl ->
                    if (ctl.isPlaying()) { _coldStart.value = ColdStartPhase.IDLE; return@launch }
                    runCatching { ctl.play() } // nudge a bound-but-idle session
                }
                tries++
            }
            _coldStart.value = ColdStartPhase.FAILED
            AppLog.w("MUSIC", "cold-start: background wake failed — offering foreground launch")
        }
    }

    /** Retry button: open Yandex in the FOREGROUND so «Моя волна» auto-plays; state fills in via ticks. */
    fun launchForeground(scope: CoroutineScope) {
        val lf = foregroundLauncher ?: return
        AppLog.d("MUSIC", "cold-start: foreground launch -> $targetPackage")
        _coldStart.value = ColdStartPhase.WAKING
        lf(targetPackage)
        wakeJob?.cancel()
        wakeJob = scope.launch {
            var tries = 0
            while (tries < 16) { // ~8s — foreground Yandex needs longer to spin up + start
                delay(500); rebind(); refresh()
                if (controller?.isPlaying() == true) { _coldStart.value = ColdStartPhase.IDLE; return@launch }
                tries++
            }
            _coldStart.value = ColdStartPhase.FAILED
        }
    }

    fun stop() {
        tickJob?.cancel(); tickJob = null
        wakeJob?.cancel(); wakeJob = null
        callback?.let { rawController?.unregisterCallback(it) }
        sessionsListener?.let { repo.stopObserving(it) }
    }

    private fun rebind() {
        val rc = repo.activeController(targetPackage)
        // getActiveSessions() returns a fresh MediaController each call, so compare by session token
        // (not object identity) to re-wire the callback only when the session actually changes.
        if (rc?.sessionToken != rawController?.sessionToken) {
            callback?.let { cb -> runCatching { rawController?.unregisterCallback(cb) } }
            rawController = rc
            controller = rc?.let { MusicController(it) }
            AppLog.d("MUSIC", "rebind: ${if (rc != null) "session" else "none"} — ${repo.dumpSessions()}")
            if (rc != null) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(m: android.media.MediaMetadata?) = refresh()
                    override fun onPlaybackStateChanged(s: android.media.session.PlaybackState?) = refresh()
                    override fun onSessionDestroyed() { rawController = null; controller = null; refresh() }
                }
                callback = cb
                rc.registerCallback(cb)
            }
        }
    }

    private fun refresh() {
        try {
            val np = controller?.nowPlaying(SystemClock.elapsedRealtime())
            // An open-but-idle session (STATE_NONE + blank title) must NOT render as an empty "Playing"
            // screen — that is the grey void. Only treat it as playback with a real track / while playing.
            val real = np != null && (np.title.isNotBlank() || np.isPlaying)
            val next = MusicUiState.selectState(
                hasPermission = NotificationAccess.isGranted(context),
                nowPlaying = if (real) np else null,
            )
            // Log only on state-class transitions (not every 1s tick) to avoid per-tick spam.
            val prev = _state.value
            if (prev::class != next::class) {
                AppLog.d("MUSIC", "state: ${prev::class.simpleName} -> ${next::class.simpleName}")
            }
            // Real playback reached (by us or externally) — clear any cold-start phase.
            if (next is MusicUiState.Playing) _coldStart.value = ColdStartPhase.IDLE
            _state.value = next
        } catch (t: Throwable) {
            AppLog.e("MUSIC", "refresh failed", t)
        }
    }

    fun albumArt() = controller?.albumArt()
    fun playPause() = controller?.playPause()
    fun next() = controller?.next()
    fun prev() = controller?.prev()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun toggleShuffle() { runCatching { controller?.trySendCustomAction("shuffle") } }
    fun like() {
        val n = (state.value as? MusicUiState.Playing)?.nowPlaying?.likeActionName
        AppLog.d("MUSIC", "like tapped -> action=${n ?: "none"}")
        if (n != null) controller?.sendLike(n)
    }
}
