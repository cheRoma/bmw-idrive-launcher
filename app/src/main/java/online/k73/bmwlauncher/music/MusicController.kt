package online.k73.bmwlauncher.music

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState

/** Thin wrapper translating a framework MediaController into our NowPlaying + transport actions. */
class MusicController(private val controller: MediaController) {

    fun registerCallback(cb: MediaController.Callback) = controller.registerCallback(cb)
    fun unregisterCallback(cb: MediaController.Callback) = controller.unregisterCallback(cb)

    fun albumArt(): Bitmap? {
        val md = controller.metadata ?: return null
        return md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    fun nowPlaying(nowMs: Long): NowPlaying? {
        val md = controller.metadata ?: return null
        val ps = controller.playbackState ?: return null
        val actionNames = ps.customActions.map { it.action }
        return NowPlaying(
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = PlaybackMapper.currentPositionMs(
                ps.position, ps.lastPositionUpdateTime, ps.playbackSpeed, nowMs
            ),
            isPlaying = PlaybackMapper.isPlaying(ps.state),
            likeActionName = PlaybackMapper.likeActionName(actionNames),
        )
    }

    fun playPause() {
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) controller.transportControls.pause() else controller.transportControls.play()
    }
    fun next() = controller.transportControls.skipToNext()
    fun prev() = controller.transportControls.skipToPrevious()
    fun seekTo(ms: Long) = controller.transportControls.seekTo(ms)
    fun sendLike(actionName: String) = controller.transportControls.sendCustomAction(actionName, null)
}
