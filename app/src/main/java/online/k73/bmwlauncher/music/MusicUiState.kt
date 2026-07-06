package online.k73.bmwlauncher.music

/**
 * Cold-start progress, shown only over the NoPlayback state.
 * IDLE = nothing happening (bare placeholder), WAKING = trying to wake Yandex in the background,
 * FAILED = background wake didn't start playback → offer an explicit "Включить музыку" button.
 */
enum class ColdStartPhase { IDLE, WAKING, FAILED }

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
