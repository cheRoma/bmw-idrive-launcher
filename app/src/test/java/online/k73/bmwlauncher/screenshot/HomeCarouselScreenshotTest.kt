package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import online.k73.bmwlauncher.ui.home.HomeCarousel
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class HomeCarouselScreenshotTest {
    // Match HomeScreenScreenshotTest: build the car's 1280x720 landscape panel from NEXUS_5 at
    // Density.MEDIUM (1.0) so px == dp and the layout lines up with the mockups.
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280,
            screenHeight = 720,
            density = Density.MEDIUM,
            orientation = ScreenOrientation.LANDSCAPE,
        ),
    )

    @Test fun carousel_home() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                HomeCarousel(now = LocalDateTime.of(2026, 7, 5, 19, 42), temp = "+18°")
            }
        }
    }
}
