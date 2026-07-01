package online.k73.bmwlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val autostart = booleanPreferencesKey("autostart_ibus")
        val bringToFront = booleanPreferencesKey("bring_to_front")
        val themeMode = stringPreferencesKey("theme_mode")
        val musicPkg = stringPreferencesKey("music_pkg")
        val navPkg = stringPreferencesKey("nav_pkg")
        val ibusPkg = stringPreferencesKey("ibus_pkg")
        val carplayPkg = stringPreferencesKey("carplay_pkg")
        val nightStart = intPreferencesKey("night_start")
        val nightEnd = intPreferencesKey("night_end")
    }

    val flow: Flow<LauncherSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun read(): LauncherSettings = context.dataStore.data.first().toSettings()

    suspend fun setThemeMode(mode: ThemeMode) =
        edit { it[Keys.themeMode] = mode.name }
    suspend fun setAutostartIBus(enabled: Boolean) =
        edit { it[Keys.autostart] = enabled }
    suspend fun setBringToFront(enabled: Boolean) =
        edit { it[Keys.bringToFront] = enabled }
    suspend fun setMusicPackage(pkg: String) = edit { it[Keys.musicPkg] = pkg }
    suspend fun setNavPackage(pkg: String) = edit { it[Keys.navPkg] = pkg }
    suspend fun setIBusPackage(pkg: String) = edit { it[Keys.ibusPkg] = pkg }
    suspend fun setCarplayPackage(pkg: String) = edit { it[Keys.carplayPkg] = pkg }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    // Test-only: write raw preference values (e.g. a corrupt theme string) through the
    // same DataStore instance the store reads from, without going via typed setters.
    internal suspend fun editRaw(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) =
        edit(block)

    private fun Preferences.toSettings(): LauncherSettings {
        val defaults = LauncherSettings()
        return LauncherSettings(
            autostartIBus = this[Keys.autostart] ?: defaults.autostartIBus,
            bringLauncherToFront = this[Keys.bringToFront] ?: defaults.bringLauncherToFront,
            themeMode = this[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.themeMode,
            musicPackage = this[Keys.musicPkg] ?: defaults.musicPackage,
            navPackage = this[Keys.navPkg] ?: defaults.navPackage,
            iBusPackage = this[Keys.ibusPkg] ?: defaults.iBusPackage,
            carplayPackage = this[Keys.carplayPkg] ?: defaults.carplayPackage,
            nightStartHour = this[Keys.nightStart] ?: defaults.nightStartHour,
            nightEndHour = this[Keys.nightEnd] ?: defaults.nightEndHour,
        )
    }
}
