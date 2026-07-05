package online.k73.bmwlauncher.music

sealed interface MusicUiState {
    data object NoPermission : MusicUiState
    data object NoPlayback : MusicUiState
    data class Playing(val nowPlaying: NowPlaying) : MusicUiState

    companion object {
        fun selectState(hasPermission: Boolean, nowPlaying: NowPlaying?): MusicUiState = when {
            !hasPermission -> NoPermission
            nowPlaying == null -> NoPlayback
            else -> Playing(nowPlaying)
        }
    }
}
