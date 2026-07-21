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
import kotlin.math.roundToInt

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
    @Volatile private var typeLog = 0

    // Trip average speed (time-weighted, includes stops). Lives here so it accumulates whenever the
    // car moves — even with the Борткомпьютер screen closed — since IBusService is process-wide.
    private val trip = TripStats()

    private val decoder = IBusDecoder(
        nowMs = { System.currentTimeMillis() },
        emit = { snap ->
            snap.speedKmh?.let { trip.onSpeed(it, System.currentTimeMillis()) }
            _data.value = snap.copy(avgSpeedKmh = trip.averageKmh.roundToInt().takeIf { trip.hasData })
            onSnapshotForMirrors(snap)
        },
        // Log one example of each distinct message type so an uploaded log inventories the whole bus
        // (this is how we decode fuel/consumption/etc. from real data).
        onNewType = { m ->
            if (typeLog++ < 200) {
                AppLog.d("IBUS", "type ${m.joinToString(" ") { "%02X".format(it) }}")
            }
        },
        // PDC capture: log every parking-module frame so a reversing session can be decoded offline.
        // Own buffer (not the 600-line event ring) — at ~8 lines/s a session would evict itself.
        onPdcFrame = { m ->
            AppLog.pdc(m.joinToString(" ") { "%02X".format(it) })
            bumpPdc(isReply = m[0] == 0x60)
        },
        // Whole-bus capture: what does the car itself put on the wire when a physical button is
        // pressed? That is the only trustworthy source for a telegram we intend to replay.
        onBusFrame = { m ->
            AppLog.pdc(m.joinToString(" ") { "%02X".format(it) })
            _busFrames.value = _busFrames.value + 1
        },
    )

    private val _pdcCapturing = MutableStateFlow(false)
    val pdcCapturing: StateFlow<Boolean> = _pdcCapturing
    private val _pdcStats = MutableStateFlow(PdcStats())
    val pdcStats: StateFlow<PdcStats> = _pdcStats
    private val pdcLock = Any()
    @Volatile private var pollThread: Thread? = null

    private val _busCapturing = MutableStateFlow(false)
    val busCapturing: StateFlow<Boolean> = _busCapturing
    private val _busFrames = MutableStateFlow(0)
    val busFrames: StateFlow<Int> = _busFrames

    /**
     * Record every frame on the bus. Pure listening — nothing is written, so this cannot actuate
     * anything in the car. Turn on, press the car's own button, turn off, send logs: the frame the
     * car emits is the one worth replaying.
     */
    fun setBusCapture(on: Boolean) {
        decoder.busCapture = on
        _busCapturing.value = on
        if (on) {
            _busFrames.value = 0
            AppLog.pdc("=== ЗАПИСЬ ШИНЫ ВКЛЮЧЕНА — нажмите нужную кнопку в машине ===")
            AppLog.d("BUSCAP", "bus capture on")
        } else {
            AppLog.pdc("=== ЗАПИСЬ ШИНЫ ВЫКЛЮЧЕНА — кадров: ${_busFrames.value} ===")
            AppLog.d("BUSCAP", "bus capture off: ${_busFrames.value} frames")
        }
    }

    /** Mirror automation: off until the user turns it on (and only after the manual buttons work). */
    @Volatile var mirrorAutoEnabled = false
    private var lastKeyPosition: KeyPosition? = null
    private var lastMirrorActionMs = 0L

    /** Frames arrive on the serial IO thread, `sent` increments on the poll thread — hence the lock. */
    private fun bumpPdc(isReply: Boolean) = synchronized(pdcLock) {
        val s = _pdcStats.value
        _pdcStats.value = if (isReply) s.copy(replies = s.replies + 1) else s.copy(echo = s.echo + 1)
    }

    /** First write failure earns a log line; after that it only lives in the on-screen stats. */
    private fun notePdcError(msg: String) = synchronized(pdcLock) {
        if (_pdcStats.value.error == null) AppLog.pdc("!! запись в шину не удалась: $msg")
        _pdcStats.value = _pdcStats.value.copy(error = msg)
    }

    /**
     * Toggle PDC capture. The E53 PDC (I-Bus node 0x60) answers on REQUEST — nothing broadcasts its
     * distances, so passive listening sees nothing. While capturing we actively poll it ~4 Hz with the
     * diagnostic job `3F 03 60 1B 47` (from KLUSEK/BMW-RPi-iBUS) and log every reply (tag PDCCAP), so a
     * reversing session gives us the raw per-sensor distance sequence to decode + verify on his module.
     */
    fun setPdcCapture(on: Boolean) {
        decoder.pdcCapture = on
        _pdcCapturing.value = on
        if (on) {
            _pdcStats.value = PdcStats()
            AppLog.pdc("=== capture ON — polling 3F 03 60 1B 47 @4Hz ===")
            AppLog.d("PDCCAP", "capture on")
            startPdcPoll()
        } else {
            val s = _pdcStats.value
            val summary = "sent=${s.sent} echo=${s.echo} replies=${s.replies}" + (s.error?.let { " error=$it" } ?: "")
            AppLog.pdc("=== capture OFF — $summary ===")
            AppLog.d("PDCCAP", "capture off: $summary")
            pollThread = null // loop below exits when pdcCapture=false
        }
    }

    private fun startPdcPoll() {
        if (pollThread != null) return
        val poll = byteArrayOf(0x3F, 0x03, 0x60, 0x1B, 0x47) // diag 3F → PDC 60, job 1B, xor chk 47
        val t = Thread {
            while (decoder.pdcCapture) {
                // A silent failure here is the worst outcome: an empty capture can't tell "module
                // stayed quiet" from "we never got a byte onto the bus" (adapters with no TX line).
                val p = port
                if (p == null) {
                    notePdcError("порт закрыт — адаптер не подключён")
                } else {
                    runCatching { p.write(poll, 200) }
                        .onSuccess { synchronized(pdcLock) { _pdcStats.value = _pdcStats.value.let { it.copy(sent = it.sent + 1) } } }
                        .onFailure { notePdcError(it.message ?: it.javaClass.simpleName) }
                }
                runCatching { Thread.sleep(250) }
            }
            pollThread = null
        }
        pollThread = t
        t.isDaemon = true
        t.start()
    }

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
                    if (rxLog++ < 4) AppLog.d("IBUS", "rx ${data.size}B (data flowing)")
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

    /**
     * One-shot WRITE to the I-Bus: clears the BMW OBC speed limit ("LIMIT 6 KM/H") that the OEM iBus
     * app latched into the instrument cluster and never cleared when Roma uninstalled it (→ the >6 km/h
     * gong keeps firing). Telegram = the EXACT one the OEM app sends when its «Limit» toggle is switched
     * OFF, decompiled from `o1.t1()`:
     *     3B 05 80 41 09 08 FE   (src 0x3B on-board-monitor → dst 0x80 IKE, cmd 41 09, flag 08 = off;
     *                             last byte = XOR checksum of the preceding six)
     * Sent a few times because the bus is noisy and a single frame is easily missed. This is the ONLY
     * write we ever perform — the reader is otherwise strictly read-only. Guarded; a missing/busy port
     * just no-ops. Must be called off the main thread (blocking USB bulk write).
     */
    fun clearSpeedLimit(): Boolean {
        val p = port ?: run { AppLog.w("IBUS", "clearSpeedLimit: no open port"); return false }
        val frame = byteArrayOf(0x3B, 0x05, 0x80.toByte(), 0x41, 0x09, 0x08, 0xFE.toByte())
        var ok = false
        repeat(3) {
            runCatching { p.write(frame, 300); ok = true }
                .onFailure { AppLog.w("IBUS", "clearSpeedLimit write failed: ${it.message}") }
            runCatching { Thread.sleep(120) }
        }
        if (ok) AppLog.d("IBUS", "sent clear-speed-limit telegram 3B 05 80 41 09 08 FE")
        return ok
    }

    /**
     * Fold / unfold the side mirrors. See [MirrorTelegrams] for the bytes and why the pair is
     * repeated. Returns false only when there is no open port — the bus itself never answers, so a
     * true here means "sent", not "the mirrors moved".
     */
    fun foldMirrors(): Boolean = sendMirrorSequence(fold = true)

    fun unfoldMirrors(): Boolean = sendMirrorSequence(fold = false)

    private fun sendMirrorSequence(fold: Boolean): Boolean {
        // DISABLED 2026-07-21 after a live test: on THIS car's General Module the documented
        // "mirror" job (0C 01 31 / 0C 02 31) drives the WINDOWS, not the mirror motors — pressing
        // fold rolled the windows down with the car parked. The published E39/E53 mapping does not
        // hold for this GM, so nothing goes on the bus until the right channel is identified from a
        // capture of the car's own fold button. See docs/superpowers/specs/2026-07-21-mirror-fold-design.md.
        if (!MIRROR_COMMANDS_ENABLED) {
            AppLog.w("MIRROR", "disabled — the documented mirror job opens the windows on this car")
            return false
        }
        val p = port ?: run { AppLog.w("MIRROR", "no open port — adapter not connected"); return false }
        val what = if (fold) "fold" else "unfold"
        // Off-thread: the full sequence takes ~3 s and this is called from the serial reader thread.
        Thread {
            val frames = MirrorTelegrams.sequence(fold)
            var failed = 0
            repeat(MirrorTelegrams.REPEATS) {
                frames.forEachIndexed { i, f ->
                    runCatching { p.write(ByteArray(f.size) { f[it].toByte() }, 300) }
                        .onFailure { failed++; AppLog.w("MIRROR", "$what write failed: ${it.message}") }
                    if (i < frames.lastIndex) runCatching { Thread.sleep(MirrorTelegrams.GAP_BETWEEN_SIDES_MS) }
                }
                runCatching { Thread.sleep(MirrorTelegrams.GAP_BETWEEN_REPEATS_MS) }
            }
            AppLog.d("MIRROR", "$what sequence sent (${MirrorTelegrams.REPEATS} repeats, $failed write errors)")
        }.apply { isDaemon = true }.start()
        return true
    }

    /**
     * Key-position edges move the mirrors when the user has switched the automation on. Kept here
     * because this reader is the only thing alive process-wide that sees the bus; the rules
     * themselves live in [MirrorAutomation] so they can be tested without a car.
     */
    private fun onSnapshotForMirrors(snap: BordData) {
        val now = snap.keyPosition ?: return
        val prev = lastKeyPosition
        lastKeyPosition = now
        // Hard off: see sendMirrorSequence. The decision logic and its tests stay, the bus does not.
        if (!MIRROR_COMMANDS_ENABLED) return
        val action = MirrorAutomation.decide(prev, now, mirrorAutoEnabled, snap.speedKmh) ?: return
        val t = System.currentTimeMillis()
        if (t - lastMirrorActionMs < MIRROR_DEBOUNCE_MS) {
            AppLog.d("MIRROR", "skip $action — only ${t - lastMirrorActionMs} ms since the last one")
            return
        }
        lastMirrorActionMs = t
        AppLog.d("MIRROR", "key $prev -> $now → $action")
        when (action) {
            MirrorAction.FOLD -> foldMirrors()
            MirrorAction.UNFOLD -> unfoldMirrors()
        }
    }

    /** Reset the trip average speed (and its distance). */
    fun resetTrip() {
        trip.reset()
        _data.value = _data.value.copy(avgSpeedKmh = null)
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
        // A glitchy frame must not be able to make the mirror motors chatter.
        const val MIRROR_DEBOUNCE_MS = 5_000L
        // Kill switch — the telegram we had actuates the windows on this car. Do not flip back on
        // without a capture proving which channel is the mirrors.
        const val MIRROR_COMMANDS_ENABLED = false
    }
}
