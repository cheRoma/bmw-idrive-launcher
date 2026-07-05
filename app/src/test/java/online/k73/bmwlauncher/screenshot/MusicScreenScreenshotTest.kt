package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import online.k73.bmwlauncher.music.MusicUiState
import online.k73.bmwlauncher.music.NowPlaying
import online.k73.bmwlauncher.music.ui.MusicScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test

class MusicScreenScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280, screenHeight = 720,
            orientation = ScreenOrientation.LANDSCAPE, density = Density.MEDIUM,
        ),
    )

    private val np = NowPlaying("Ночной город", "Дельфин", 84_000, 238_000, true, "like")

    @Test fun music_playing() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                MusicScreen(MusicUiState.Playing(np), null, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }
    @Test fun music_no_playback() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                MusicScreen(MusicUiState.NoPlayback, null, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }
}
