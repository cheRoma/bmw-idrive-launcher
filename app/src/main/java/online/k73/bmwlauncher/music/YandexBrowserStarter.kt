package online.k73.bmwlauncher.music

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import online.k73.bmwlauncher.diag.AppLog

/**
 * Starts Yandex Music playback WITHOUT bringing its Activity to the front — the fix for the
 * "Yandex screen flashes before our player" cold-start. We connect to Yandex's
 * MediaBrowserService (`ru.yandex.music/.common.media.mediabrowser.MusicBrowserService`, the same
 * entry point Android Auto uses), then drive its session's transport controls:
 *
 *   1. `play()` — triggers media3 playback-resumption (resumes «Моя волна» / last queue) if Yandex
 *      supports it, with no UI.
 *   2. If the root exposes playable items, `playFromMediaId` the «Моя волна» / first playable item
 *      as a fallback (also headless).
 *
 * All best-effort and guarded: if Yandex rejects the connection (Auto-only allowlist) or anything
 * throws, [start] reports failure and the caller falls back to foregrounding the app (the flash).
 */
class YandexBrowserStarter(private val context: Context, private val pkg: String) {

    private var browser: MediaBrowserCompat? = null

    /** @param onConnected true once we've issued a headless play command, false if we couldn't. */
    fun start(onConnected: (Boolean) -> Unit) {
        val component = ComponentName(pkg, SERVICE)
        val cb = object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                val ok = runCatching {
                    val b = browser ?: return
                    val ctl = MediaControllerCompat(context, b.sessionToken)
                    AppLog.d("MUSIC", "browser connected (root=${b.root}); sending play()")
                    ctl.transportControls.play()
                    subscribeAndPlayWave(b, ctl)
                    true
                }.getOrElse {
                    AppLog.w("MUSIC", "browser onConnected error: ${it.message}"); false
                }
                onConnected(ok)
            }

            override fun onConnectionFailed() {
                AppLog.w("MUSIC", "browser connection FAILED (Yandex rejected the client?)")
                onConnected(false)
            }

            override fun onConnectionSuspended() {
                AppLog.w("MUSIC", "browser connection suspended")
            }
        }
        browser = MediaBrowserCompat(context, component, cb, null)
        runCatching { browser?.connect() }
            .onFailure { AppLog.w("MUSIC", "browser connect() threw: ${it.message}"); onConnected(false) }
    }

    /** Browse the root once; if it lists playable items, start «Моя волна» (or the first playable). */
    private fun subscribeAndPlayWave(b: MediaBrowserCompat, ctl: MediaControllerCompat) {
        runCatching {
            b.subscribe(b.root, object : MediaBrowserCompat.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
                    runCatching {
                        AppLog.d("MUSIC", "browser root: " + children.joinToString { "${it.description.title}#${it.isPlayable}" })
                        val wave = children.firstOrNull {
                            val t = it.description.title?.toString()?.lowercase().orEmpty()
                            it.isPlayable && ("волна" in t || "wave" in t || "моя" in t)
                        } ?: children.firstOrNull { it.isPlayable }
                        if (wave != null) {
                            AppLog.d("MUSIC", "browser playFromMediaId: ${wave.description.title}")
                            ctl.transportControls.playFromMediaId(wave.mediaId, null)
                        }
                    }
                }

                override fun onError(parentId: String) {
                    AppLog.w("MUSIC", "browser subscribe error: $parentId")
                }
            })
        }
    }

    fun release() {
        runCatching { browser?.disconnect() }
        browser = null
    }

    private companion object {
        const val SERVICE = "ru.yandex.music.common.media.mediabrowser.MusicBrowserService"
    }
}
