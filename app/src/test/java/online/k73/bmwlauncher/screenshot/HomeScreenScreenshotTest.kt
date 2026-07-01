package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import online.k73.bmwlauncher.ui.home.HomeScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test

class HomeScreenScreenshotTest {
    // Paparazzi 1.3.3 has no NEXUS_5_LAND; build the car's 1280x720 landscape panel from NEXUS_5.
    // The real 7" head unit is low-density (~mdpi) and the mockups treat px == dp, so render at
    // Density.MEDIUM (1.0) => 1280x720 px == 1280x720 dp, otherwise NEXUS_5's xxhdpi shrinks each
    // tile to ~113dp and clips the label + amber underline.
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280,
            screenHeight = 720,
            density = Density.MEDIUM,
            orientation = ScreenOrientation.LANDSCAPE,
        ),
    )

    @Test fun home_night() {
        paparazzi.snapshot { BmwLauncherTheme(isNight = true) { HomeScreen() } }
    }
}
