package online.k73.bmwlauncher.remote

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import online.k73.bmwlauncher.diag.AppLog
import java.io.ByteArrayInputStream
import kotlin.concurrent.thread

/** One reverse forward: a port on the VPS that lands on [localHost]:[localPort] here in the car. */
data class Forward(val remotePort: Int, val localHost: String, val localPort: Int)

/**
 * The launcher's own reverse SSH tunnel to our VPS — the thing that ends the dependency on Termux
 * holding an `autossh` session on this head unit. It carries two ports back to the server:
 *
 * - the device's **adb** port, which gives a root shell whenever `adbd` happens to be running;
 * - the launcher's own [ControlServer], which works even when it isn't.
 *
 * Security posture, all enforced on the server too: the account is `nologin`, its key may only bind
 * those two loopback ports (`permitlisten`), local forwards are refused, and the host key is pinned
 * here — a tunnel that trusted any server would hand the car to whoever answers first.
 *
 * The connection is expected to die constantly (ignition off, LTE, garages); [TunnelBackoff] paces
 * the retries and every state change is published to [RemoteStatus] so the Settings screen can show
 * the truth without me having to be reachable.
 */
class RemoteTunnel(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val privateKeyPem: ByteArray,
    private val knownHostLine: String,
    private val forwards: List<Forward>,
) {
    @Volatile private var running = false
    @Volatile private var session: Session? = null

    fun start() {
        if (running) return
        if (privateKeyPem.isEmpty() || knownHostLine.isBlank()) {
            RemoteStatus.set("выключен: сборка без ключа")
            return
        }
        running = true
        thread(name = "remote-tunnel", isDaemon = true) { loop() }
    }

    fun stop() {
        running = false
        runCatching { session?.disconnect() }
        session = null
        RemoteStatus.set("выключен")
    }

    private fun loop() {
        val backoff = TunnelBackoff()
        while (running) {
            try {
                RemoteStatus.set(if (backoff.attempts == 0) "подключаюсь…" else "переподключаюсь (попытка ${backoff.attempts + 1})")
                connectAndHold()
                backoff.reset()
            } catch (t: Throwable) {
                val reason = (t.message ?: t.javaClass.simpleName).take(70)
                RemoteStatus.set("нет связи: $reason")
                AppLog.w("REMOTE", "туннель отвалился: $reason")
            } finally {
                runCatching { session?.disconnect() }
                session = null
            }
            if (!running) break
            sleepInterruptibly(backoff.nextDelayMs())
        }
        RemoteStatus.set("выключен")
    }

    private fun connectAndHold() {
        val jsch = JSch()
        jsch.addIdentity("bmw-launcher", privateKeyPem, null, null)
        jsch.setKnownHosts(ByteArrayInputStream(knownHostLine.toByteArray()))
        val s = jsch.getSession(user, host, port).apply {
            // Pinned host key + key-only auth: no prompts, no fallbacks, nothing to answer.
            setConfig("StrictHostKeyChecking", "yes")
            setConfig("PreferredAuthentications", "publickey")
            // The server presents an RSA host key; naming it keeps JSch from negotiating a type it
            // would need an extra dependency to verify.
            setConfig("server_host_key", "rsa-sha2-512,rsa-sha2-256,ssh-rsa")
            setServerAliveInterval(30_000)
            setServerAliveCountMax(3)
        }
        session = s
        s.connect(CONNECT_TIMEOUT_MS)
        forwards.forEach {
            // The bind address must be explicit: with an empty one the server's `permitlisten`
            // restriction does not match and the forward is refused.
            s.setPortForwardingR("127.0.0.1", it.remotePort, it.localHost, it.localPort)
        }
        RemoteStatus.set("подключён")
        AppLog.d("REMOTE", "туннель поднят: ${forwards.joinToString { "${it.remotePort}→${it.localPort}" }}")
        while (running && s.isConnected) Thread.sleep(HOLD_POLL_MS)
        if (running) throw IllegalStateException("сессия закрыта сервером")
    }

    /** Sleep in slices so [stop] doesn't have to wait out a three-minute backoff. */
    private fun sleepInterruptibly(totalMs: Long) {
        var left = totalMs
        while (running && left > 0) {
            val slice = minOf(left, 1_000L)
            Thread.sleep(slice)
            left -= slice
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val HOLD_POLL_MS = 5_000L
    }
}
