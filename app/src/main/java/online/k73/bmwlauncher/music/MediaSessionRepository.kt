package online.k73.bmwlauncher.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent

/** Finds the active MediaController for [targetPackage] via our enabled notification listener. */
class MediaSessionRepository(private val context: Context) {
    private val manager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listener = ComponentName(context, MediaNotificationListener::class.java)

    fun activeController(targetPackage: String): MediaController? =
        runCatching { manager.getActiveSessions(listener) }.getOrNull()
            ?.filter { it.packageName == targetPackage }
            ?.let { list ->
                // Yandex can expose more than one session (an idle one + the real playing one).
                // Prefer the session that's actually playing so we don't bind to the empty one.
                list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: list.firstOrNull { !(it.playbackState?.state == PlaybackState.STATE_NONE && it.metadata == null) }
                    ?: list.firstOrNull()
            }

    /**
     * Start playback in [pkg] via a background media-button — wakes the player and creates its
     * MediaSession WITHOUT bringing its full UI to the front. Best-effort.
     */
    fun sendPlay(pkg: String) {
        runCatching {
            for (a in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(a, KeyEvent.KEYCODE_MEDIA_PLAY))
                })
            }
        }
    }

    /** Diagnostic: describe every active session (package, state, custom actions, title). */
    fun dumpSessions(): String = runCatching {
        val sessions = manager.getActiveSessions(listener)
        if (sessions.isEmpty()) return "media-sessions: none"
        sessions.joinToString("\n") { c ->
            val ps = c.playbackState
            val actions = ps?.customActions?.joinToString(",") { it.action } ?: ""
            val title = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            "session ${c.packageName}: state=${ps?.state} customActions=[$actions] title=\"$title\""
        }
    }.getOrElse { "media-sessions: error ${it.message}" }

    /** Notifies [onChanged] whenever the set of active sessions changes. */
    fun observeSessions(onChanged: () -> Unit): MediaSessionManager.OnActiveSessionsChangedListener {
        val l = MediaSessionManager.OnActiveSessionsChangedListener { onChanged() }
        runCatching { manager.addOnActiveSessionsChangedListener(l, listener) }
        return l
    }

    fun stopObserving(l: MediaSessionManager.OnActiveSessionsChangedListener) {
        runCatching { manager.removeOnActiveSessionsChangedListener(l) }
    }
}
