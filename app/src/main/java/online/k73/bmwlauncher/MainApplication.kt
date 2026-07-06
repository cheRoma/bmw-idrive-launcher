package online.k73.bmwlauncher

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.k73.bmwlauncher.diag.AnrWatchdog
import online.k73.bmwlauncher.diag.AppLog
import online.k73.bmwlauncher.diag.CrashHandler
import online.k73.bmwlauncher.diag.LogUploader
import java.io.File

class MainApplication : Application() {
    // Process-lifetime scope for background diagnostic uploads (crash/ANR).
    private val diagScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        runCatching {
            AppLog.d("App", "launch v${BuildConfig.VERSION_NAME}")
            CrashHandler.install(this)
            AnrWatchdog.start(onHang = {
                // Watchdog already debounces + calls this off its own thread; upload the snapshot.
                diagScope.launch { runCatching { LogUploader.upload(this@MainApplication, "anr") } }
            })
            // A crash from a previous run left a pending report — send (and it self-deletes) now.
            if (File(filesDir, CrashHandler.PENDING_CRASH).exists()) {
                diagScope.launch { runCatching { LogUploader.upload(this@MainApplication, "crash") } }
            }
        }
    }
}
