package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import online.k73.bmwlauncher.launch.AppEntry
import online.k73.bmwlauncher.ui.apps.AppsScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test

class AppsScreenScreenshotTest {
    // Same 1280x720 landscape car panel at Density.MEDIUM (px == dp) as the other screen goldens.
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280,
            screenHeight = 720,
            density = Density.MEDIUM,
            orientation = ScreenOrientation.LANDSCAPE,
        ),
    )

    @Test fun apps_night() {
        val apps = listOf(
            AppEntry("com.yandex.music", "Яндекс Музыка", null),
            AppEntry("ru.yandex.yandexnavi", "Навигатор", null),
            AppEntry("com.google.maps", "Карты", null),
            AppEntry("com.spotify.music", "Spotify", null),
            AppEntry("org.telegram", "Telegram", null),
            AppEntry("com.android.chrome", "Chrome", null),
        )
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                AppsScreen(apps = apps, onLaunch = {}, onRebootHold = {}, onBack = {})
            }
        }
    }
}
