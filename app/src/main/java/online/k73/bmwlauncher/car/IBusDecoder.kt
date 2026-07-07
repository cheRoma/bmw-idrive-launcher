package online.k73.bmwlauncher.car

/**
 * Frames a raw BMW I-Bus byte stream and decodes the standard IKE broadcasts we show on our
 * on-board computer. Pure and unit-tested — no Android, no I/O.
 *
 * I-Bus message layout:  `[src][len][dst][data...][chk]`
 *  - `len` = number of bytes from `dst` up to and including `chk`
 *  - `chk` = XOR of every preceding byte (src ^ len ^ dst ^ data...)
 * Wire settings are 9600 baud, 8 data bits, EVEN parity, 1 stop bit.
 *
 * Decoded (all from src 0x80 = IKE instrument cluster, dst 0xBF = global broadcast):
 *  - `0x18` speed/rpm:  speed_km/h = data[0] * 2,  rpm = data[1] * 100   (confirmed vs the OEM app)
 *  - `0x19` temps:      outside_°C = signed data[0],  coolant_°C = signed data[1]
 *  - `0x11` ignition:   bit0 = ignition on
 */
class IBusDecoder(
    private val nowMs: () -> Long,
    private val emit: (BordData) -> Unit,
    /** Called once per DISTINCT (src, command) message type — one example frame each. Lets the log
     *  capture a full inventory of what the car broadcasts (for decoding fuel/consumption/etc.). */
    private val onNewType: (IntArray) -> Unit = {},
) {
    private val buf = ArrayDeque<Int>()
    private var cur = BordData(connected = true)
    private val seenTypes = HashSet<Int>()

    /** Raw messages, most-recent last — a small ring for on-car diagnostics (hex dump). */
    val recentFrames = ArrayDeque<IntArray>()

    fun feed(bytes: ByteArray, len: Int) {
        for (i in 0 until len) buf.addLast(bytes[i].toInt() and 0xFF)
        drain()
    }

    private fun drain() {
        while (buf.size >= 2) {
            val msgLen = buf.elementAt(1)               // dst..chk
            if (msgLen < 3 || msgLen > 40) {            // implausible → drop one byte and resync
                buf.removeFirst(); continue
            }
            val total = 2 + msgLen
            if (buf.size < total) return                // wait for the rest of the frame
            val msg = IntArray(total) { buf.elementAt(it) }
            var chk = 0
            for (i in 0 until total - 1) chk = chk xor msg[i]
            if (chk != msg[total - 1]) {                // bad checksum → resync by a single byte
                buf.removeFirst(); continue
            }
            repeat(total) { buf.removeFirst() }
            remember(msg)
            val typeKey = (msg[0] shl 8) or (if (msg.size > 3) msg[3] else 0xFFF)
            if (seenTypes.add(typeKey)) onNewType(msg)
            handle(msg)
        }
    }

    private fun remember(msg: IntArray) {
        recentFrames.addLast(msg)
        while (recentFrames.size > 24) recentFrames.removeFirst()
    }

    private fun handle(m: IntArray) {
        // m = [src, len, dst, cmd, payload..., chk]
        val src = m[0]
        val cmd = if (m.size > 3) m[3] else return
        var next = cur
        if (src == 0x80) {                              // IKE
            when (cmd) {
                0x18 -> if (m.size >= 6) next = next.copy(speedKmh = m[4] * 2, rpm = m[5] * 100)
                0x19 -> if (m.size >= 6) next = next.copy(outsideC = signed(m[4]), coolantC = signed(m[5]))
                0x11 -> if (m.size >= 5) next = next.copy(ignition = (m[4] and 0x01) != 0)
            }
        }
        if (next != cur) {
            cur = next.copy(connected = true, updatedAtMs = nowMs())
            emit(cur)
        }
    }

    private fun signed(b: Int): Int = b.toByte().toInt()
}
