package online.k73.bmwlauncher.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import online.k73.bmwlauncher.autostart.AutostartController
import online.k73.bmwlauncher.data.LauncherSettings
import online.k73.bmwlauncher.data.SettingsStore
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
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import java.time.LocalTime

class HomeActivity : ComponentActivity() {
    private val store by lazy { SettingsStore(applicationContext) }
    private val launcher by lazy { AppLauncher(applicationContext) }
    private val shell by lazy { RootShell() }

    // Guards against overlapping resumes double-running the autostart check.
    // Reset every time the coroutine finishes, so each wake re-checks i-Bus.
    @Volatile
    private var autostartInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsFlow = store.flow.stateIn(lifecycleScope, SharingStarted.Eagerly, LauncherSettings())
        setContent {
            val settings by settingsFlow.collectAsState()
            // AUTO theme must re-evaluate over time: tick once per minute so the
            // composition recomputes isNight and flips the palette at the boundary.
            var now by remember { mutableStateOf(LocalTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = LocalTime.now()
                    delay(60_000)
                }
            }
            val isNight = ThemeResolver.isNight(settings.themeMode, now, settings.nightStartHour, settings.nightEndHour)
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
                    composable("music_stub") { MusicPlaceholder() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Autostart runs on EVERY wake (device resume from sleep is the primary trigger).
        // ensureRunning() early-returns when pidof shows i-Bus alive, so this is cheap and
        // idempotent. A single in-flight flag prevents overlapping resumes double-running it.
        if (autostartInFlight) return
        autostartInFlight = true
        lifecycleScope.launch {
            try {
                val s = store.read()
                if (s.autostartIBus && s.iBusPackage.isNotBlank()) {
                    AutostartController(shell, onLaunched = {
                        // Re-foreground HOME only during an actual i-Bus launch, and only
                        // when the user opted into bring-to-front. Not on idle resume.
                        if (s.bringLauncherToFront) {
                            startActivity(Intent(this@HomeActivity, HomeActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                        }
                    }).ensureRunning(s.iBusPackage)
                }
            } finally {
                autostartInFlight = false
            }
        }
    }
}

@Composable
private fun MusicPlaceholder() {
    val c = LocalLauncherColors.current
    Box(
        Modifier.fillMaxSize().background(c.background),
        contentAlignment = Alignment.Center,
    ) {
        Text("Музыка — скоро", color = c.textDim, fontSize = 24.sp)
    }
}
