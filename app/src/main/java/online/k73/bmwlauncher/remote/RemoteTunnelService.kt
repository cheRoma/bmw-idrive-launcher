package online.k73.bmwlauncher.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Base64
import online.k73.bmwlauncher.BuildConfig
import online.k73.bmwlauncher.R
import online.k73.bmwlauncher.diag.AppLog

/**
 * Owns the reverse tunnel and the control endpoint for as long as the launcher lives.
 *
 * A foreground service on purpose: the launcher is backgrounded whenever YouTube or Yandex is in
 * front, and that is exactly when the head unit's low-memory killer used to take the old Termux
 * tunnel down — the failure mode this feature exists to end.
 */
class RemoteTunnelService : Service() {
    private var tunnel: RemoteTunnel? = null
    private var control: ControlServer? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification())
        val key = runCatching { Base64.decode(BuildConfig.TUNNEL_KEY_B64, Base64.DEFAULT) }
            .getOrDefault(ByteArray(0))
        if (key.isEmpty()) {
            // A public build: no credentials, so there is nothing to connect with. Say so plainly
            // instead of retrying forever.
            RemoteStatus.set("выключен: сборка без ключа")
            AppLog.d("REMOTE", "удалённый доступ не настроен в этой сборке")
            stopSelf()
            return
        }
        control = ControlServer(BuildConfig.CONTROL_TOKEN, DeviceControl(applicationContext)).also { it.start() }
        tunnel = RemoteTunnel(
            host = BuildConfig.TUNNEL_HOST,
            port = BuildConfig.TUNNEL_PORT,
            user = BuildConfig.TUNNEL_USER,
            privateKeyPem = key,
            knownHostLine = BuildConfig.TUNNEL_KNOWN_HOST,
            forwards = listOf(
                Forward(BuildConfig.REMOTE_ADB_PORT, "127.0.0.1", ADB_PORT),
                Forward(BuildConfig.REMOTE_CONTROL_PORT, "127.0.0.1", ControlServer.PORT),
            ),
        ).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        tunnel?.stop()
        control?.stop()
        tunnel = null
        control = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Удалённый доступ", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            runCatching { manager?.createNotificationChannel(channel) }
        }
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Удалённый доступ")
            .setContentText("Туннель к серверу")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "remote-tunnel"
        private const val NOTIFICATION_ID = 4573
        private const val ADB_PORT = 5555

        /** Guarded: a HOME app must not crash because a service refused to start. */
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, RemoteTunnelService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { AppLog.w("REMOTE", "не смог запустить сервис: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, RemoteTunnelService::class.java)) }
        }
    }
}
