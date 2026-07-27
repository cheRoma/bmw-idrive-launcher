package online.k73.bmwlauncher.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.k73.bmwlauncher.BuildConfig
import online.k73.bmwlauncher.car.IBusService
import online.k73.bmwlauncher.diag.AppLog
import online.k73.bmwlauncher.diag.LogUploader
import online.k73.bmwlauncher.launch.AppLauncher
import online.k73.bmwlauncher.ui.home.MapRuntime
import online.k73.bmwlauncher.vpn.VpnProfile
import java.net.InetSocketAddress
import java.net.Socket

/**
 * What the four control actions do on this device. Everything here is something the launcher can
 * already do for itself — the channel adds reach, not privilege.
 */
class DeviceControl(private val context: Context) : ControlHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val launcher by lazy { AppLauncher(context) }

    override fun status(): String {
        val bord = runCatching { IBusService.get(context).data.value }.getOrNull()
        val metrics = context.resources.displayMetrics
        return StatusJson.of(
            "app" to BuildConfig.VERSION_NAME,
            "code" to BuildConfig.VERSION_CODE,
            "device" to "${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}",
            "uptimeSec" to SystemClock.elapsedRealtime() / 1000,
            "screen" to "${metrics.widthPixels}x${metrics.heightPixels} @${metrics.densityDpi}dpi",
            "defaultHome" to isDefaultHome(),
            // The whole reason this endpoint exists: adb is often down after a reboot on this ROM,
            // and knowing that before trying to connect saves a pointless round trip.
            "adbReachable" to portOpen(ADB_PORT),
            "sfaInstalled" to launcher.isInstalled(VpnProfile.SFA_PACKAGE),
            "ibus" to (bord?.connected ?: false),
            "ignition" to bord?.ignition,
            "speedKmh" to bord?.speedKmh,
            "outsideC" to bord?.outsideC,
            "map" to MapRuntime.state(),
            "tunnel" to RemoteStatus.state.value,
        )
    }

    override fun uploadLogs() {
        scope.launch { runCatching { LogUploader.upload(context, "remote") } }
    }

    override fun handVpnProfile() {
        val url = BuildConfig.VPN_PROFILE_URL
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(VpnProfile.importLink(url)))
            .setPackage(VpnProfile.SFA_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { AppLog.w("REMOTE", "SFA не принял профиль: ${it.message}") }
    }

    override fun restart() {
        AppLog.d("REMOTE", "перезапуск по команде")
        runCatching {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                ?.let { context.startActivity(it) }
        }
        scope.launch {
            Thread.sleep(1_000)
            Process.killProcess(Process.myPid())
        }
    }

    private fun isDefaultHome(): Boolean = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)

    private fun portOpen(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); true }
    }.getOrDefault(false)

    private companion object {
        const val ADB_PORT = 5555
    }
}
