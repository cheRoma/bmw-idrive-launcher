package online.k73.bmwlauncher

import android.app.Application
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.k73.bmwlauncher.car.IBusService
import online.k73.bmwlauncher.data.SettingsStore
import online.k73.bmwlauncher.diag.AnrWatchdog
import online.k73.bmwlauncher.diag.AppLog
import online.k73.bmwlauncher.diag.BlackScreenWatchdog
import online.k73.bmwlauncher.diag.CrashHandler
import online.k73.bmwlauncher.diag.LogUploader
import java.io.File

class MainApplication : Application() {
    // Process-lifetime scope for background diagnostic uploads (crash/ANR).
    private val diagScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Robolectric runs this Application in unit tests; its slow env trips the ANR watchdog and
        // would spam the real upload endpoint. Skip the live diagnostics there.
        if ("robolectric".equals(Build.FINGERPRINT, ignoreCase = true)) return
        runCatching {
            AppLog.d("App", "launch v${BuildConfig.VERSION_NAME}")
            // (Map engine is MapLibre now — it self-initialises in MapBackground, no key/global init.)
            CrashHandler.install(this)
            AnrWatchdog.start(this, onHang = {
                // Watchdog already debounces + calls this off its own thread; upload the snapshot.
                diagScope.launch { runCatching { LogUploader.upload(this@MainApplication, "anr") } }
            })
            // Black-screen detector: catches the blank-window-with-live-main-thread failure the ANR
            // watchdog can't see (lost GL surface after ACC sleep/wake). One report per episode.
            BlackScreenWatchdog.start(this, onBlack = {
                diagScope.launch { runCatching { LogUploader.upload(this@MainApplication, "blackscreen") } }
            })
            // A crash from a previous run left a pending report — send (and it self-deletes) now.
            if (File(filesDir, CrashHandler.PENDING_CRASH).exists()) {
                diagScope.launch { runCatching { LogUploader.upload(this@MainApplication, "crash") } }
            }
            // Mirror automation reacts to the key being switched off, which is exactly when no
            // screen of ours is in the foreground — so the setting is fed to the process-wide
            // reader here rather than from a Composable.
            diagScope.launch {
                runCatching {
                    SettingsStore(this@MainApplication).flow.collect { s ->
                        IBusService.get(this@MainApplication).mirrorAutoEnabled = s.mirrorAutoFold
                    }
                }
            }
        }
    }
}
