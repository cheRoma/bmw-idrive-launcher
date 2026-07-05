package online.k73.bmwlauncher.music

object PlaybackMapper {
    const val STATE_PLAYING = 3 // android.media.session.PlaybackState.STATE_PLAYING

    fun isPlaying(stateInt: Int): Boolean = stateInt == STATE_PLAYING

    /** Yandex exposes like/dislike as custom actions; pick the "like" one, never a "dislike". */
    fun likeActionName(customActionNames: List<String>): String? =
        customActionNames.firstOrNull { it.contains("like", true) && !it.contains("dislike", true) }
            ?: customActionNames.firstOrNull { it.contains("heart", true) }

    /** Interpolate the reported position by elapsed wall time * playback speed. */
    fun currentPositionMs(basePositionMs: Long, lastUpdateMs: Long, speed: Float, nowMs: Long): Long {
        if (lastUpdateMs <= 0L) return basePositionMs.coerceAtLeast(0)
        val delta = ((nowMs - lastUpdateMs) * speed).toLong()
        return (basePositionMs + delta).coerceAtLeast(0)
    }
}
