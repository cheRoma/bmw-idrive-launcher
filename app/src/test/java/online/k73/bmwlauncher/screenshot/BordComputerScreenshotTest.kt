package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import online.k73.bmwlauncher.car.BordData
import online.k73.bmwlauncher.car.PdcStats
import online.k73.bmwlauncher.ui.bordcomputer.BordComputerContent
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test

/**
 * The head unit's REAL canvas, not a convenient one: the panel reports 1280x720 px at 240 dpi and
 * the system bars take 48 dp, leaving 853x432 dp. The other screenshot tests render at
 * Density.MEDIUM (1280x720 dp) — two thirds more height than the car has — which is exactly why a
 * layout that overflowed on the unit still looked fine in CI. This screen has no scrolling, so it
 * must be checked at the size it actually gets.
 */
class BordComputerScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280,
            screenHeight = 648,      // 432 dp at 1.5x — the panel minus the system bars
            density = Density.HIGH,  // 240 dpi, as reported by the unit
            orientation = ScreenOrientation.LANDSCAPE,
        ),
    )

    private val live = BordData(
        connected = true,
        speedKmh = 84,
        avgSpeedKmh = 37,
        rpm = 2400,
        coolantC = 91,
        outsideC = -8,
    )

    @Test fun bordcomputer_live() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) { BordComputerContent(data = live) }
        }
    }

    @Test fun bordcomputer_capturing() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                BordComputerContent(
                    data = live,
                    pdcOn = true,
                    pdcStats = PdcStats(sent = 128, echo = 128, replies = 64),
                )
            }
        }
    }
}
