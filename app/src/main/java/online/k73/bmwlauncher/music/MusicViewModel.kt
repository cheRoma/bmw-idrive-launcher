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

    fun start(scope: CoroutineScope) {
        sessionsListener = repo.observeSessions { rebind() }
        rebind()
        scope.launch {
            while (true) { refresh(); delay(1000) }
        }
    }

    fun stop() {
        callback?.let { rawController?.unregisterCallback(it) }
        sessionsListener?.let { repo.stopObserving(it) }
    }

    private fun rebind() {
        callback?.let { rawController?.unregisterCallback(it) }
        val rc = repo.activeController(targetPackage)
        rawController = rc
        controller = rc?.let { MusicController(it) }
        if (rc != null) {
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(m: android.media.MediaMetadata?) = refresh()
                override fun onPlaybackStateChanged(s: android.media.session.PlaybackState?) = refresh()
                override fun onSessionDestroyed() = rebind()
            }
            callback = cb
            rc.registerCallback(cb)
        }
        refresh()
    }

    private fun refresh() {
        val np = controller?.nowPlaying(SystemClock.elapsedRealtime())
        _state.value = MusicUiState.selectState(
            hasPermission = NotificationAccess.isGranted(context),
            nowPlaying = np,
        )
    }

    fun albumArt() = controller?.albumArt()
    fun playPause() = controller?.playPause()
    fun next() = controller?.next()
    fun prev() = controller?.prev()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun like() { val n = (state.value as? MusicUiState.Playing)?.nowPlaying?.likeActionName; if (n != null) controller?.sendLike(n) }

    /** Cold-start: nothing playing → launch Yandex Music so a session appears. */
    fun startPlaybackColdStart(launch: (String) -> Unit) = launch(targetPackage)
}
