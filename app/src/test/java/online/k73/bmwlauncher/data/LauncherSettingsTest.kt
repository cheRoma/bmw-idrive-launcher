package online.k73.bmwlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingsTest {
    @Test fun defaults_are_sane() {
        val s = LauncherSettings()
        assertTrue(s.autostartIBus)
        assertTrue(s.bringLauncherToFront)
        assertEquals(ThemeMode.AUTO, s.themeMode)
        assertEquals("ru.yandex.yandexnavi", s.navPackage)
        assertEquals("", s.carplayPackage)
    }
}
