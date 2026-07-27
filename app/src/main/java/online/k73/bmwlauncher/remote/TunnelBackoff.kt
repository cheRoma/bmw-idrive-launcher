package online.k73.bmwlauncher.remote

import kotlin.math.min
import kotlin.math.pow

/**
 * Reconnect pacing for [RemoteTunnel]. Pure so the pacing is provable off-device.
 *
 * The car's network comes and goes constantly (ignition, LTE, parking garages), so retries start
 * quickly and grow — but the cap stays in minutes: a runaway delay would leave the unit unreachable
 * long after the network came back, which defeats the point of the tunnel.
 */
class TunnelBackoff(
    private val firstDelayMs: Long = 5_000,
    private val maxDelayMs: Long = 3 * 60_000,
    private val factor: Double = 2.0,
) {
    var attempts = 0
        private set

    fun nextDelayMs(): Long {
        val delay = min(maxDelayMs.toDouble(), firstDelayMs * factor.pow(attempts)).toLong()
        attempts++
        return delay
    }

    /** Call once a session is actually up, so the next outage starts from a quick retry again. */
    fun reset() {
        attempts = 0
    }
}
