package online.k73.bmwlauncher.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import online.k73.bmwlauncher.autostart.AutostartController
import online.k73.bmwlauncher.data.LauncherSettings
import online.k73.bmwlauncher.data.SettingsStore
import online.k73.bmwlauncher.data.ThemeMode
import online.k73.bmwlauncher.launch.AppLauncher
import online.k73.bmwlauncher.launch.InstalledApps
import online.k73.bmwlauncher.system.RootShell
import online.k73.bmwlauncher.system.ShellCommands
import online.k73.bmwlauncher.theme.ThemeResolver
import online.k73.bmwlauncher.ui.apps.AppsScreen
import online.k73.bmwlauncher.ui.home.HomeScreen
import online.k73.bmwlauncher.ui.home.TileId
import online.k73.bmwlauncher.ui.settings.SettingsScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import java.time.LocalTime

class HomeActivity : ComponentActivity() {
    private val store by lazy { SettingsStore(applicationContext) }
    private val launcher by lazy { AppLauncher(applicationContext) }
    private val shell by lazy { RootShell() }
    private var autostartDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsFlow = store.flow.stateIn(lifecycleScope, SharingStarted.Eagerly, LauncherSettings())
        setContent {
            val settings by settingsFlow.collectAsState()
            val isNight = ThemeResolver.isNight(settings.themeMode, LocalTime.now(), settings.nightStartHour, settings.nightEndHour)
            BmwLauncherTheme(isNight = isNight) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(onTile = { id ->
                            when (id) {
                                TileId.MUSIC -> nav.navigate("music_stub")
                                TileId.APPS -> nav.navigate("apps")
                                TileId.SETTINGS -> nav.navigate("settings")
                                TileId.NAV -> launcher.launch(settings.navPackage)
                                TileId.IBUS -> launcher.launch(settings.iBusPackage)
                                TileId.CARPLAY -> launcher.launch(settings.carplayPackage)
                            }
                        })
                    }
                    composable("apps") {
                        AppsScreen(
                            apps = InstalledApps(applicationContext).list(),
                            onLaunch = { launcher.launch(it) },
                            onRebootHold = { shell.exec(ShellCommands.reboot()) },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = settings,
                            onAutostart = { lifecycleScope.launch { store.setAutostartIBus(it) } },
                            onBringToFront = { lifecycleScope.launch { store.setBringToFront(it) } },
                            onThemeMode = { lifecycleScope.launch { store.setThemeMode(it) } },
                        )
                    }
                    // Phase 2 replaces this stub with the real Music screen.
                    composable("music_stub") { HomeScreen() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!autostartDone) {
            autostartDone = true
            lifecycleScope.launch {
                val s = store.read()
                if (s.bringLauncherToFront) {
                    startActivity(Intent(this@HomeActivity, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
                if (s.autostartIBus && s.iBusPackage.isNotBlank()) {
                    AutostartController(shell, onLaunched = {
                        startActivity(Intent(this@HomeActivity, HomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    }).ensureRunning(s.iBusPackage)
                }
            }
        }
    }
}
