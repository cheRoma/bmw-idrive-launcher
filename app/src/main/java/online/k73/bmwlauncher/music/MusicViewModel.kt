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

    private var controller: MusicController? = null
    private var rawController: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var sessionsListener: android.media.session.MediaSessionManager.OnActiveSessionsChangedListener? = null

    private var tickJob: kotlinx.coroutines.Job? = null
    private var coldStartPending = false

    fun start(scope: CoroutineScope) {
        if (tickJob?.isActive == true) return // already running — don't stack tick loops
        sessionsListener = repo.observeSessions { rebind(); refresh() }
        rebind(); refresh()
        // Re-pick the active session AND refresh every second. The notification listener can connect
        // late, and Yandex may swap to a real playing session after it opens — this keeps us bound to
        // whatever is currently active instead of sitting on a stale/empty session (the "grey screen").
        tickJob = scope.launch {
            while (true) { rebind(); refresh(); delay(1000) }
        }
    }

    /**
     * Cold-start: nothing bound yet. Wake Yandex in the BACKGROUND via a media-button PLAY (no
     * full-app UI), then poll for its session. Only if that fails after a few seconds do we fall
     * back to opening the app.
     */
    fun startBackgroundPlay(scope: CoroutineScope, fallbackLaunch: (String) -> Unit) {
        AppLog.d("MUSIC", "cold-start: background PLAY -> $targetPackage")
        coldStartPending = true
        repo.sendPlay(targetPackage)
        scope.launch {
            // Give the media-button a short chance; if Yandex isn't running it won't wake, so fall
            // back to opening the app quickly (no long empty-screen wait). Once a session appears,
            // rebind() nudges it to actually play.
            var tries = 0
            while (controller == null && tries < 3) { delay(500); rebind(); refresh(); tries++ }
            if (controller == null) {
                AppLog.w("MUSIC", "cold-start: no session after PLAY — opening app")
                fallbackLaunch(targetPackage)
            }
        }
    }

    fun stop() {
        tickJob?.cancel(); tickJob = null
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
        // Cold-start: we opened Yandex; if its session is idle, tell it to actually play (once).
        val ctl = controller
        if (coldStartPending && ctl != null) {
            if (ctl.isPlaying()) coldStartPending = false
            else { runCatching { ctl.play() }; AppLog.d("MUSIC", "cold-start: nudge play()") }
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
    fun like() {
        val n = (state.value as? MusicUiState.Playing)?.nowPlaying?.likeActionName
        AppLog.d("MUSIC", "like tapped -> action=${n ?: "none"}")
        if (n != null) controller?.sendLike(n)
    }
}
