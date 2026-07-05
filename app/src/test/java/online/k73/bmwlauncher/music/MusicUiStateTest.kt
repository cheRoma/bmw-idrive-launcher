package online.k73.bmwlauncher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicUiStateTest {
    private val np = NowPlaying("Ночной город", "Дельфин", 84_000, 238_000, true, "like")

    @Test fun no_permission_wins() {
        assertEquals(MusicUiState.NoPermission, MusicUiState.selectState(hasPermission = false, nowPlaying = np))
    }
    @Test fun no_session_is_no_playback() {
        assertEquals(MusicUiState.NoPlayback, MusicUiState.selectState(hasPermission = true, nowPlaying = null))
    }
    @Test fun active_session_is_playing() {
        val s = MusicUiState.selectState(hasPermission = true, nowPlaying = np)
        assertTrue(s is MusicUiState.Playing)
        assertEquals("Ночной город", (s as MusicUiState.Playing).nowPlaying.title)
    }
}
