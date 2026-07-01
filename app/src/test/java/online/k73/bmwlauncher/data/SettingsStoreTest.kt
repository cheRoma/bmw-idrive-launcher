package online.k73.bmwlauncher.data

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    private val store = SettingsStore(ApplicationProvider.getApplicationContext())

    @Test fun reads_defaults_before_any_write() = runTest {
        val s = store.read()
        assertEquals(ThemeMode.AUTO, s.themeMode)
    }

    @Test fun persists_theme_mode_and_toggle() = runTest {
        store.setThemeMode(ThemeMode.NIGHT)
        store.setAutostartIBus(false)
        val s = store.read()
        assertEquals(ThemeMode.NIGHT, s.themeMode)
        assertFalse(s.autostartIBus)
    }

    @Test fun persists_ibus_package() = runTest {
        store.setIBusPackage("de.example.ibus")
        assertEquals("de.example.ibus", store.read().iBusPackage)
    }

    @Test fun falls_back_to_default_on_corrupt_theme_value() = runTest {
        // Write a bogus raw value under the same key SettingsStore reads, bypassing setThemeMode.
        store.editRaw { it[stringPreferencesKey("theme_mode")] = "PURPLE" }
        assertEquals(ThemeMode.AUTO, store.read().themeMode)
    }
}
