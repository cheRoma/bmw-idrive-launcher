package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import online.k73.bmwlauncher.data.LauncherSettings
import online.k73.bmwlauncher.ui.settings.SettingsScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test

class SettingsScreenScreenshotTest {
    // Paparazzi 1.3.3 has no NEXUS_5_LAND; build the car's 1280x720 landscape panel from NEXUS_5.
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280,
            screenHeight = 720,
            orientation = ScreenOrientation.LANDSCAPE,
        ),
    )

    @Test fun settings_night() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                SettingsScreen(LauncherSettings(), {}, {}, {})
            }
        }
    }
}
