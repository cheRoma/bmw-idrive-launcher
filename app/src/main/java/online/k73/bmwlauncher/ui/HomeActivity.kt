package online.k73.bmwlauncher.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.content.FileProvider
import online.k73.bmwlauncher.BuildConfig
import online.k73.bmwlauncher.update.ApkDownloader
import online.k73.bmwlauncher.update.ApkInstaller
import online.k73.bmwlauncher.update.HttpUrlClient
import online.k73.bmwlauncher.update.InstallResult
import online.k73.bmwlauncher.update.RootDetector
import online.k73.bmwlauncher.update.UpdateChecker
import online.k73.bmwlauncher.update.UpdateStatus
import online.k73.bmwlauncher.update.UpdateUiState
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import online.k73.bmwlauncher.music.MediaSessionRepository
import online.k73.bmwlauncher.music.MusicViewModel
import online.k73.bmwlauncher.music.NotificationAccess
import online.k73.bmwlauncher.music.ui.MusicScreen
import online.k73.bmwlauncher.theme.ThemeResolver
import online.k73.bmwlauncher.ui.apps.AppsScreen
import online.k73.bmwlauncher.ui.home.HomeCarousel
import online.k73.bmwlauncher.ui.home.TileId
import online.k73.bmwlauncher.ui.settings.SettingsScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import java.time.LocalTime

class HomeActivity : ComponentActivity() {
    private val store by lazy { SettingsStore(applicationContext) }
    private val launcher by lazy { AppLauncher(applicationContext) }
    private val shell by lazy { RootShell() }

    private val rootDetector by lazy { RootDetector(shell) }
    private val updateChecker by lazy { UpdateChecker(HttpUrlClient(), MANIFEST_URL) }
    private val downloader by lazy { ApkDownloader(HttpUrlClient(), cacheDir) }
    private val apkInstaller by lazy {
        ApkInstaller(
            hasRoot = { rootDetector.hasRoot() },
            shell = shell,
            component = "$packageName/.ui.HomeActivity",
            launchInstaller = { file -> launchSystemInstaller(file) },
        )
    }
    private val updateState = androidx.compose.runtime.mutableStateOf<UpdateUiState>(UpdateUiState.Idle)

    private val musicRepo by lazy { MediaSessionRepository(applicationContext) }
    private val musicVm by lazy { MusicViewModel(applicationContext, musicRepo, "ru.yandex.music") }

    // RootDetector.hasRoot() spawns a `su` process synchronously; never call it during composition.
    // Resolve it ONCE off the main thread in onCreate and hold the result in this state.
    private val hasRootState = androidx.compose.runtime.mutableStateOf(false)

    // Whether this launcher is the current default HOME. Resolved in onCreate and refreshed in
    // onResume (so it updates after the user returns from the system role/default-apps dialog).
    private val isDefaultLauncherState = androidx.compose.runtime.mutableStateOf(false)

    private fun computeIsDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == packageName
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        // Fallback: open the system "default apps / home" screen so the user can pick us.
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
        // If neither settings screen resolves, do nothing rather than crash — this is the HOME app.
    }

    companion object {
        const val MANIFEST_URL = "https://k73.online/newBMW/latest.json"

        // How long to let i-Bus initialize its I-Bus/USB link before we pull the launcher back to the
        // front. On non-root stock Android an activity launch is necessarily briefly visible; NO_ANIMATION
        // + a short settle shrinks the i-Bus flash to a flicker. Raise if i-Bus needs longer to connect.
        const val IBUS_SETTLE_MS = 300L
    }

    /**
     * Reboot the head unit WITHOUT root. This Microntek/XTRONS ROM (Android 13) has a privileged
     * service (android.microntek.service, holds android.permission.REBOOT) that reboots on receiving
     * the "com.microntek.hctreboot" broadcast — confirmed working from an unprivileged app. Falls
     * back to `su -c reboot` for rooted units (harmless no-op without root).
     */
    private fun rebootDevice() {
        sendBroadcast(Intent("com.microntek.hctreboot"))
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { shell.exec(ShellCommands.reboot()) }
        }
    }

    /** Hide the system status/navigation bars so the launcher uses the full screen. */
    private fun enableImmersiveMode() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-hide the bars whenever we regain focus (they reappear after other apps / dialogs).
        if (hasFocus) {
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun launchSystemInstaller(file: java.io.File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun onCheckUpdate() {
        updateState.value = UpdateUiState.Checking
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val status = updateChecker.fetch(BuildConfig.VERSION_CODE)
            updateState.value = when (status) {
                is UpdateStatus.UpToDate -> UpdateUiState.UpToDate
                is UpdateStatus.Available -> UpdateUiState.Available(status.versionName, status.apkUrl, status.notes)
                is UpdateStatus.Error -> UpdateUiState.Failed(status.reason)
            }
        }
    }

    private fun onInstallUpdate() {
        val avail = updateState.value as? UpdateUiState.Available ?: return
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = downloader.download(avail.apkUrl) { p -> updateState.value = UpdateUiState.Downloading(p) }
                updateState.value = UpdateUiState.Installing
                when (val r = apkInstaller.install(file)) {
                    is InstallResult.Failed -> updateState.value = UpdateUiState.Failed(r.message)
                    else -> { /* silent: process restarts; intent: system UI takes over */ }
                }
            } catch (t: Throwable) {
                updateState.value = UpdateUiState.Failed(t.message ?: "download failed")
            }
        }
    }

    // Autostart i-Bus only once per process (after boot, when we first become HOME).
    @Volatile
    private var autostartHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        // Resolve default-launcher status once up front so the first Settings visit is correct.
        isDefaultLauncherState.value = computeIsDefaultLauncher()
        // Resolve root status ONCE off the main thread; hasRoot() spawns `su` and must never
        // run during composition (blocks the UI thread → ANR on a launcher).
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            hasRootState.value = rootDetector.hasRoot()
        }
        val settingsFlow = store.flow.stateIn(lifecycleScope, SharingStarted.Eagerly, LauncherSettings())
        setContent {
            val settings by settingsFlow.collectAsState()
            // AUTO theme must re-evaluate over time: tick once per minute so the
            // composition recomputes isNight and flips the palette at the boundary.
            var now by remember { mutableStateOf(LocalTime.now()) }
            var nowDateTime by remember { mutableStateOf(java.time.LocalDateTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = LocalTime.now()
                    nowDateTime = java.time.LocalDateTime.now()
                    delay(60_000)
                }
            }
            val isNight = ThemeResolver.isNight(settings.themeMode, now, settings.nightStartHour, settings.nightEndHour)
            BmwLauncherTheme(isNight = isNight) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeCarousel(
                            now = nowDateTime,
                            temp = null, // wired to the Microntek outside-temp broadcast later
                            onTile = { id ->
                                when (id) {
                                    TileId.MUSIC -> nav.navigate("music")
                                    TileId.APPS -> nav.navigate("apps")
                                    TileId.SETTINGS -> nav.navigate("settings")
                                    TileId.NAV -> launcher.launch(settings.navPackage)
                                    TileId.IBUS -> launcher.launch(settings.iBusPackage)
                                    TileId.CARPLAY -> launcher.launch(settings.carplayPackage)
                                }
                            },
                        )
                    }
                    composable("apps") {
                        AppsScreen(
                            apps = InstalledApps(applicationContext).list(),
                            onLaunch = { launcher.launch(it) },
                            onRebootHold = { rebootDevice() },
                        )
                    }
                    composable("settings") {
                        val update by updateState
                        val hasRoot by hasRootState
                        val isDefault by isDefaultLauncherState
                        SettingsScreen(
                            settings = settings,
                            onAutostart = { lifecycleScope.launch { store.setAutostartIBus(it) } },
                            onBringToFront = { lifecycleScope.launch { store.setBringToFront(it) } },
                            onThemeMode = { lifecycleScope.launch { store.setThemeMode(it) } },
                            currentVersion = BuildConfig.VERSION_NAME,
                            hasRoot = hasRoot,
                            updateState = update,
                            onCheckUpdate = { onCheckUpdate() },
                            onInstallUpdate = { onInstallUpdate() },
                            isDefaultLauncher = isDefault,
                            onSetDefault = { requestDefaultLauncher() },
                        )
                    }
                    composable("music") {
                        val musicState by musicVm.state.collectAsState()
                        DisposableEffect(Unit) {
                            musicVm.start(lifecycleScope)
                            onDispose { musicVm.stop() }
                        }
                        MusicScreen(
                            state = musicState,
                            albumArt = musicVm.albumArt()?.asImageBitmap(),
                            onPlayPause = { musicVm.playPause() },
                            onNext = { musicVm.next() },
                            onPrev = { musicVm.prev() },
                            onSeek = { musicVm.seekTo(it) },
                            onLike = { musicVm.like() },
                            onPlaylists = {
                                // Phase 3 will try MediaBrowser; for now open Yandex Music (also the
                                // "grant permission" tap target when access is missing).
                                if (!NotificationAccess.isGranted(applicationContext)) {
                                    startActivity(NotificationAccess.settingsIntent())
                                } else {
                                    launcher.launch("ru.yandex.music")
                                }
                            },
                            onColdStartPlay = {
                                launcher.launch("ru.yandex.music")
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh default-launcher status every resume (e.g. after returning from the system dialog).
        // Must run before the autostart early-return so it always updates.
        isDefaultLauncherState.value = computeIsDefaultLauncher()
        // Autostart i-Bus once per process (after boot, when we first become HOME). This ROM has
        // no root, so we launch i-Bus with a normal startActivity (allowed — we are the foreground
        // HOME), then bring our launcher back to the front. Once-per-process so returning Home
        // later doesn't relaunch i-Bus.
        if (autostartHandled) return
        autostartHandled = true
        lifecycleScope.launch {
            val s = store.read()
            if (s.autostartIBus && s.iBusPackage.isNotBlank()) {
                val ibusIntent = launcher.launchIntentFor(s.iBusPackage)?.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                if (ibusIntent != null) {
                    startActivity(ibusIntent)
                    if (s.bringLauncherToFront) {
                        kotlinx.coroutines.delay(IBUS_SETTLE_MS)
                        startActivity(
                            Intent(this@HomeActivity, HomeActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        )
                    }
                }
            }
        }
    }
}
