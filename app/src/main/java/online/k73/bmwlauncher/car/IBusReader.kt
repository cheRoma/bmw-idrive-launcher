package online.k73.bmwlauncher.car

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import online.k73.bmwlauncher.diag.AppLog

/**
 * Reads the car's BMW I-Bus directly over the CP210x USB-serial adapter and exposes the decoded
 * live data as a [StateFlow]. Read-only — we never write to the bus. Everything is guarded; a
 * missing adapter, denied permission, or a busy port just leaves [data] disconnected.
 *
 * Single-owner caveat: only one app may hold the USB device. The OEM i-Bus app must not be running
 * (we no longer autostart it) or [openDevice] returns null.
 */
class IBusReader(private val appContext: Context) {
    private val _data = MutableStateFlow(BordData())
    val data: StateFlow<BordData> = _data

    private var port: UsbSerialPort? = null
    private var io: SerialInputOutputManager? = null
    private var receiver: BroadcastReceiver? = null
    @Volatile private var rxLog = 0

    private val decoder = IBusDecoder(nowMs = { System.currentTimeMillis() }) { _data.value = it }

    fun start() {
        runCatching { connect() }.onFailure { AppLog.w("IBUS", "start failed: ${it.message}") }
    }

    private fun connect() {
        val usb = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
            .firstOrNull { it.device.vendorId == CP210X_VID }   // the Resler I-Bus↔USB adapter
            ?: run { AppLog.d("IBUS", "no CP210x adapter found"); return }
        if (!usb.hasPermission(driver.device)) {
            requestPermission(usb, driver.device); return
        }
        val connection = usb.openDevice(driver.device)
            ?: run { AppLog.w("IBUS", "openDevice failed — port busy (OEM app still running?)"); return }
        val p = driver.ports.first()
        p.open(connection)
        p.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_EVEN)   // I-Bus = 9600 8E1
        port = p
        io = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                runCatching {
                    decoder.feed(data, data.size)
                    if (rxLog++ < 40) AppLog.d(
                        "IBUS",
                        "rx ${data.size}B ${data.take(20).joinToString(" ") { "%02X".format(it) }}",
                    )
                }
            }

            override fun onRunError(e: Exception) {
                AppLog.w("IBUS", "run error: ${e.message}")
                _data.value = _data.value.copy(connected = false)
            }
        }).also { it.start() }
        _data.value = _data.value.copy(connected = true)
        AppLog.d("IBUS", "connected to CP210x @9600 8E1")
    }

    private fun requestPermission(usb: UsbManager, device: UsbDevice) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                runCatching { appContext.unregisterReceiver(this) }
                receiver = null
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) start()
                else AppLog.d("IBUS", "USB permission denied")
            }
        }
        receiver = r
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(
            appContext, 0, Intent(ACTION_PERM).setPackage(appContext.packageName), flags,
        )
        val filter = IntentFilter(ACTION_PERM)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") appContext.registerReceiver(r, filter)
        }
        usb.requestPermission(device, pi)
        AppLog.d("IBUS", "requesting USB permission")
    }

    fun stop() {
        runCatching { io?.stop() }
        runCatching { port?.close() }
        runCatching { receiver?.let { appContext.unregisterReceiver(it) } }
        io = null; port = null; receiver = null
        _data.value = _data.value.copy(connected = false)
    }

    private companion object {
        const val CP210X_VID = 0x10C4          // 4292 — Silicon Labs
        const val ACTION_PERM = "online.k73.bmwlauncher.USB_PERMISSION"
    }
}
