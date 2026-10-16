package online.k73.bmwlauncher.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import online.k73.bmwlauncher.data.CcEntry
import online.k73.bmwlauncher.ui.checkcontrol.CheckControlBanner
import online.k73.bmwlauncher.ui.checkcontrol.CheckControlCard
import online.k73.bmwlauncher.ui.checkcontrol.CheckControlContent
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** At the unit's real canvas (853x432 dp), like [BordComputerScreenshotTest]. */
class CheckControlScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280,
            screenHeight = 648,
            density = Density.HIGH,
            orientation = ScreenOrientation.LANDSCAPE,
        ),
    )

    private val zone = ZoneId.of("Europe/Moscow")

    @Test fun banner() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                Box(Modifier.fillMaxSize().background(LocalLauncherColors.current.background)) {
                    CheckControlBanner(
                        "CHECK BRAKE LIGHTS", onOpen = {}, onDismiss = {},
                        faultyLamps = listOf("левый стоп-сигнал", "левый задний габарит"),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                    )
                }
            }
        }
    }

    @Test fun card_brake_lights() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                CheckControlCard("CHECK BRAKE LIGHTS", listOf("левый стоп-сигнал", "левый задний габарит"), onClose = {})
            }
        }
    }

    @Test fun card_oil_pressure() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) { CheckControlCard("STOP!ENGINE OILPRESS", emptyList(), onClose = {}) }
        }
    }

    @Test fun history() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                CheckControlContent(
                    history = listOf(
                        CcEntry(1_792_086_720_436, "CHECK BRAKE LIGHTS"),
                        CcEntry(1_792_059_904_726, "FASTEN SEAT BELTS"),
                        CcEntry(1_783_492_504_897, "LIMIT 6 KM/H"),
                    ),
                    active = "CHECK BRAKE LIGHTS",
                    zone = zone,
                )
            }
        }
    }
}
