package online.k73.bmwlauncher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMapperTest {
    @Test fun playing_state_is_playing() {
        assertTrue(PlaybackMapper.isPlaying(3))   // PlaybackState.STATE_PLAYING
        assertFalse(PlaybackMapper.isPlaying(2))  // STATE_PAUSED
        assertFalse(PlaybackMapper.isPlaying(0))
    }

    @Test fun finds_like_action_excluding_dislike() {
        assertEquals("ru.yandex.like", PlaybackMapper.likeActionName(listOf("skip", "ru.yandex.like", "ru.yandex.dislike")))
        assertEquals("heart_toggle", PlaybackMapper.likeActionName(listOf("heart_toggle")))
        assertNull(PlaybackMapper.likeActionName(listOf("shuffle", "repeat", "dislike_only")))
    }

    @Test fun computes_current_position_with_speed() {
        // base 10s at update t=1000ms, speed 1.0, now t=4000ms -> 10s + 3s = 13s
        assertEquals(13_000L, PlaybackMapper.currentPositionMs(10_000, 1_000, 1.0f, 4_000))
    }
    @Test fun position_never_negative_and_ignores_zero_update_time() {
        assertEquals(10_000L, PlaybackMapper.currentPositionMs(10_000, 0, 1.0f, 999_999))
        assertEquals(0L, PlaybackMapper.currentPositionMs(-500, 0, 1.0f, 0))
    }
}
