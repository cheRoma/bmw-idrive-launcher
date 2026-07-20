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
import online.k73.bmwlauncher.diag.AppLog
import online.k73.bmwlauncher.diag.LogSendState
import online.k73.bmwlauncher.diag.LogUploader
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
import online.k73.bmwlauncher.car.IBusService
import online.k73.bmwlauncher.ui.bordcomputer.BordComputerScreen
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
    private val logState = androidx.compose.runtime.mutableStateOf(LogSendState.Idle)

    private val musicRepo by lazy { MediaSessionRepository(applicationContext) }
    private val musicVm by lazy { MusicViewModel(applicationContext, musicRepo, "ru.yandex.music") }

    // RootDetector.hasRoot() spawns a `su` process synchronously; never call it during composition.
    // Resolve it ONCE off the main thread in onCreate and hold the result in this state.
    private val hasRootState = androidx.compose.runtime.mutableStateOf(false)

    // Whether this launcher is the current default HOME. Resolved in onCreate and refreshed in
    // onResume (so it updates after the user returns from the system role/default-apps dialog).
    private val isDefaultLauncherState = androidx.compose.runtime.mutableStateOf(false)

    // Route requested by an external intent (panel-button redirect). Observed in setContent to
    // navigate the NavController; cleared after it's consumed.
    private val pendingRoute = androidx.compose.runtime.mutableStateOf<String?>(null)

    private fun navRouteFromIntent(i: Intent?): String? {
        i?.getStringExtra(EXTRA_NAV_ROUTE)?.let { return it.takeIf { r -> r in VALID_ROUTES } }
        // A plain HOME/launcher press (incl. the panel «Домой» button, which the MCU fires as
        // ACTION_MAIN + CATEGORY_HOME) → always return to our home carousel. This is the reliable
        // escape from any sub-screen (e.g. when the Music cold-start left Yandex layered behind us).
        if (i?.action == Intent.ACTION_MAIN && i.hasCategory(Intent.CATEGORY_HOME)) return "home"
        return null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navRouteFromIntent(intent)?.let { pendingRoute.value = it }
    }

    private fun computeIsDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == packageName
    }

    /** Ask once for location so the map background can follow the car. Guarded — HOME must not crash. */
    private fun requestLocationForMap() {
        runCatching {
            val perm = android.Manifest.permission.ACCESS_FINE_LOCATION
            if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm, android.Manifest.permission.ACCESS_COARSE_LOCATION), 0)
            }
        }
    }

    private fun requestDefaultLauncher() {
        // Reliable path on this Microntek ROM (and how other launchers get selected): open the
        // system "Home app" picker so the user chooses us. The RoleManager ROLE_HOME one-tap dialog
        // is nicer but does NOT reliably take on this OEM ROM, so it's only a secondary attempt.
        // All calls guarded — this is the HOME app and must never crash.
        if (runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }.isSuccess) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                if (runCatching { startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) }.isSuccess) return
            }
        }
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }

    companion object {
        const val MANIFEST_URL = "https://k73.online/newBMW/latest.json"

        // Intent extra used by ButtonRedirectService to open a specific launcher screen (panel-button
        // redirect). Only these routes may be opened this way.
        const val EXTRA_NAV_ROUTE = "nav_route"
        val VALID_ROUTES = setOf("music", "apps", "settings", "bordcomputer")

        // How long to let i-Bus initialize its I-Bus/USB link before we pull the launcher back to the
        // front. On non-root stock Android an activity launch is necessarily briefly visible; NO_ANIMATION
        // + a short settle shrinks the i-Bus flash to a flicker. Raise if i-Bus needs longer to connect.
        const val IBUS_SETTLE_MS = 300L

        // Cold-start Music: Yandex must be foregrounded briefly to start playing, then we pull our
        // launcher back. NO_ANIMATION + this short settle shrinks the Yandex flash to a flicker
        // (a launched activity can't be fully hidden without root). Raise if Yandex needs longer.
        const val MUSIC_SETTLE_MS = 1200L

        // After foregrounding Yandex we poll for real playback (nudging PLAY) before pulling our
        // launcher back, so we never return before «Моя волна» starts. Bounded by MAX so a stuck
        // Yandex can't hang us on its own screen forever.
        const val MUSIC_POLL_MS = 400L
        const val MUSIC_FOREGROUND_MAX_MS = 5000L
    }

    /**
     * Cold-start Music without leaving the user staring at the full Yandex app: launch Yandex
     * (no animation) so a session can appear, then after a short settle send a media-button PLAY
     * and reorder our launcher back to the front. Yandex only flickers; our now-playing then binds.
     */
    private fun launchYandexAndReturn(pkg: String) {
        val intent = launcher.launchIntentFor(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION) ?: return
        startActivity(intent)
        lifecycleScope.launch {
            // Let Yandex reach the foreground, then wait until «Моя волна» actually starts (nudging
            // PLAY each poll), and only THEN pull our launcher back. Returning too early yanks Yandex
            // to the background before it begins → nothing plays (the old MUSIC_SETTLE-only bug).
            kotlinx.coroutines.delay(MUSIC_SETTLE_MS)
            var waited = 0L
            while (waited < MUSIC_FOREGROUND_MAX_MS) {
                runCatching { musicRepo.sendPlay(pkg) }
                val playing = runCatching {
                    musicRepo.activeController(pkg)?.playbackState?.state ==
                        android.media.session.PlaybackState.STATE_PLAYING
                }.getOrDefault(false)
                if (playing) break
                kotlinx.coroutines.delay(MUSIC_POLL_MS)
                waited += MUSIC_POLL_MS
            }
            runCatching {
                startActivity(
                    Intent(this@HomeActivity, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            }
        }
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
        AppLog.d("KEY", "windowFocus=$hasFocus")
        // Re-hide the bars whenever we regain focus (they reappear after other apps / dialogs).
        if (hasFocus) {
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    // Diagnostics for Roma's "physical panel buttons misbehave" report: log every hardware key the
    // XTRONS panel delivers (keycode + whether our window had focus at press time). An uploaded drive
    // log then shows exactly which codes the panel sends and whether they're being dropped during the
    // wake/no-focus window (the "Cancelling event (no window focus)" symptom). Purely observational —
    // we do NOT consume keys here (return super), so nothing changes behaviourally.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            AppLog.d(
                "KEY",
                "down code=${event.keyCode} (${android.view.KeyEvent.keyCodeToString(event.keyCode)}) focus=${hasWindowFocus()}",
            )
        }
        return super.dispatchKeyEvent(event)
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
        AppLog.d("UPDATE", "check start")
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val status = updateChecker.fetch(BuildConfig.VERSION_CODE)
            AppLog.d("UPDATE", "check result: ${status::class.simpleName}")
            updateState.value = when (status) {
                is UpdateStatus.UpToDate -> UpdateUiState.UpToDate
                is UpdateStatus.Available -> UpdateUiState.Available(status.versionName, status.apkUrl, status.notes)
                is UpdateStatus.Error -> UpdateUiState.Failed(status.reason)
            }
        }
    }

    private fun onSendLogs() {
        if (logState.value == LogSendState.Sending) return
        logState.value = LogSendState.Sending
        AppLog.d("DIAG", "manual log upload requested")
        lifecycleScope.launch {
            val r = LogUploader.upload(applicationContext, "manual")
            AppLog.d("DIAG", "manual log upload result: ${if (r.isSuccess) "ok" else "fail"}")
            logState.value = if (r.isSuccess) LogSendState.Sent else LogSendState.Failed
        }
    }

    private fun onInstallUpdate() {
        val avail = updateState.value as? UpdateUiState.Available ?: return
        AppLog.d("UPDATE", "install start: ${avail.versionName}")
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = downloader.download(avail.apkUrl) { p -> updateState.value = UpdateUiState.Downloading(p) }
                updateState.value = UpdateUiState.Installing
                when (val r = apkInstaller.install(file)) {
                    is InstallResult.Failed -> { AppLog.w("UPDATE", "install failed: ${r.message}"); updateState.value = UpdateUiState.Failed(r.message) }
                    else -> { /* silent: process restarts; intent: system UI takes over */ }
                }
            } catch (t: Throwable) {
                AppLog.e("UPDATE", "install error", t)
                updateState.value = UpdateUiState.Failed(t.message ?: "download failed")
            }
        }
    }

    // Autostart i-Bus only once per process (after boot, when we first become HOME).
    @Volatile
    private var autostartHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d("HOME", "onCreate")
        navRouteFromIntent(intent)?.let { pendingRoute.value = it }
        enableImmersiveMode()
        requestLocationForMap()
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
                // Open a screen requested from outside (panel-button redirect via ButtonRedirectService).
                val route by pendingRoute
                LaunchedEffect(route) {
                    route?.let { r ->
                        if (r == "home") {
                            // Pop everything above the home carousel (no-op if already there).
                            nav.popBackStack("home", inclusive = false)
                        } else {
                            nav.navigate(r) { launchSingleTop = true }
                        }
                        pendingRoute.value = null
                    }
                }
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        // Live outside temperature from the shared I-Bus reader → status bar.
                        val ibus = remember { IBusService.get(applicationContext) }
                        val bord by ibus.data.collectAsState()
                        HomeCarousel(
                            now = nowDateTime,
                            temp = bord.outsideC?.let { "$it°" },
                            onTile = { id ->
                                AppLog.d("NAV", "tile tapped: $id")
                                when (id) {
                                    TileId.MUSIC -> nav.navigate("music")
                                    TileId.APPS -> nav.navigate("apps")
                                    TileId.SETTINGS -> nav.navigate("settings")
                                    TileId.NAV -> launcher.launch(settings.navPackage)
                                    TileId.IBUS -> nav.navigate("bordcomputer")
                                    TileId.CARPLAY -> launcher.launch(settings.carplayPackage)
                                    TileId.YOUTUBE -> launcher.launch("com.google.android.youtube")
                                }
                            },
                        )
                    }
                    composable("bordcomputer") {
                        BordComputerScreen(onBack = { nav.popBackStack() })
                    }
                    composable("apps") {
                        AppsScreen(
                            apps = InstalledApps(applicationContext).list(),
                            onLaunch = { launcher.launch(it) },
                            onRebootHold = { rebootDevice() },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("settings") {
                        val update by updateState
                        val hasRoot by hasRootState
                        val isDefault by isDefaultLauncherState
                        val logSend by logState
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
                            logState = logSend,
                            onSendLogs = { onSendLogs() },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("music") {
                        val musicState by musicVm.state.collectAsState()
                        val coldStart by musicVm.coldStart.collectAsState()
                        DisposableEffect(Unit) {
                            musicVm.start(lifecycleScope) { launchYandexAndReturn(it) }
                            onDispose { musicVm.stop() }
                        }
                        MusicScreen(
                            state = musicState,
                            coldStart = coldStart,
                            albumArt = musicVm.albumArt()?.asImageBitmap(),
                            onPlayPause = { musicVm.playPause() },
                            onNext = { musicVm.next() },
                            onPrev = { musicVm.prev() },
                            onSeek = { musicVm.seekTo(it) },
                            onLike = { musicVm.like() },
                            onSource = {
                                // Tapping the source badge (v4) opens Yandex Music; also the
                                // "grant permission" tap target when notification access is missing.
                                if (!NotificationAccess.isGranted(applicationContext)) {
                                    startActivity(NotificationAccess.settingsIntent())
                                } else {
                                    launcher.launch("ru.yandex.music")
                                }
                            },
                            onShuffle = { musicVm.toggleShuffle() },
                            onColdStartPlay = {
                                // Explicit "Включить музыку": open Yandex foreground so «Моя волна»
                                // auto-plays (the only reliable way from a fully-killed app), then
                                // our now-playing fills in and we return to it.
                                musicVm.launchForeground(lifecycleScope)
                            },
                            onBack = { nav.popBackStack() },
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
        // i-Bus autostart REMOVED: we now read the I-Bus ourselves (our own «Борткомпьютер») and USB
        // is single-owner — launching the OEM app would steal the adapter. The tile opens our screen.
    }
}
