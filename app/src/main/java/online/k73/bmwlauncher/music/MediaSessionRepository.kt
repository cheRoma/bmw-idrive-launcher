package online.k73.bmwlauncher.music

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager

/** Finds the active MediaController for [targetPackage] via our enabled notification listener. */
class MediaSessionRepository(private val context: Context) {
    private val manager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listener = ComponentName(context, MediaNotificationListener::class.java)

    fun activeController(targetPackage: String): MediaController? =
        runCatching { manager.getActiveSessions(listener) }.getOrNull()
            ?.firstOrNull { it.packageName == targetPackage }

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
