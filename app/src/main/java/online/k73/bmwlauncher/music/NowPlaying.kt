package online.k73.bmwlauncher.music

data class NowPlaying(
    val title: String,
    val artist: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val likeActionName: String?,   // null → no like button
) {
    val likeAvailable: Boolean get() = likeActionName != null
}
