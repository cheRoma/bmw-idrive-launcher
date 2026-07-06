package online.k73.bmwlauncher.diag

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import online.k73.bmwlauncher.BuildConfig
import online.k73.bmwlauncher.music.NotificationAccess

/** Compact device/app snapshot for diagnostic reports. Null-safe; never throws. */
object DeviceInfo {
    fun collect(context: Context): String {
        val sb = StringBuilder()
        fun line(s: String) = sb.append(s).append('\n')

        runCatching {
            line("app: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        }
        runCatching {
            line("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            line("android: SDK ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        }
        runCatching {
            val dm = context.resources.displayMetrics
            val cfg = context.resources.configuration
            line("screen: ${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi}dpi · ${cfg.screenWidthDp}x${cfg.screenHeightDp}dp")
        }
        runCatching {
            val rt = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val maxMb = rt.maxMemory() / (1024 * 1024)
            line("heap: ${usedMb}MB used / ${maxMb}MB max")
        }
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                line("mem: ${mi.availMem / (1024 * 1024)}MB avail / ${mi.totalMem / (1024 * 1024)}MB total · low=${mi.lowMemory}")
            }
        }
        runCatching {
            line("uptime: ${SystemClock.elapsedRealtime() / 1000}s")
        }
        runCatching {
            line("notification-access: ${NotificationAccess.isGranted(context)}")
        }
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val res = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            line("default-home: ${res?.activityInfo?.packageName == context.packageName}")
        }
        return sb.toString().trimEnd('\n')
    }
}
