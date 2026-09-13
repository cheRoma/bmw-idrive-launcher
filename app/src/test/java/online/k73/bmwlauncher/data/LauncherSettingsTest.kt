package online.k73.bmwlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingsTest {
    @Test fun defaults_are_sane() {
        val s = LauncherSettings()
        assertTrue(s.autostartIBus)
        assertTrue(s.bringLauncherToFront)
        assertEquals(ThemeMode.NIGHT, s.themeMode)
        assertEquals("ru.yandex.yandexnavi", s.navPackage)
        assertEquals("com.zjinnova.zlink", s.carplayPackage)
    }

    @Test fun nav_app_is_resolved_from_its_package() {
        assertEquals(NavApp.YANDEX, NavApp.of("ru.yandex.yandexnavi"))
        assertEquals(NavApp.YANGO, NavApp.of("com.yango.maps.android"))
    }

    @Test fun unknown_nav_package_falls_back_to_navigator() {
        // A package set by hand over the tunnel must not leave the selector with nothing lit.
        assertEquals(NavApp.YANDEX, NavApp.of("com.google.android.apps.maps"))
        assertEquals(NavApp.YANDEX, NavApp.of(""))
    }
}
