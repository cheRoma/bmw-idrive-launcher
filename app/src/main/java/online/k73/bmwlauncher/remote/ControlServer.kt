package online.k73.bmwlauncher.remote

import online.k73.bmwlauncher.diag.AppLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** What the four control actions actually do. Kept as an interface so the server stays dumb. */
interface ControlHandler {
    fun status(): String
    fun uploadLogs()
    fun handVpnProfile()
    fun restart()
}

/**
 * A deliberately tiny HTTP endpoint the launcher serves **on loopback only**, so the only way in is
 * the reverse tunnel ([RemoteTunnel]) — nothing on the car's network, let alone the internet, can
 * reach it. Requests are routed by [ControlRoute], which also holds the token check.
 *
 * It exists because on this ROM `adbd` is often not running after a reboot; when that happens the
 * adb forward leads nowhere and this endpoint is the only way to see the car and fix things without
 * asking the driver to tap through menus.
 */
class ControlServer(
    private val token: String,
    private val handler: ControlHandler,
    private val port: Int = PORT,
) {
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        thread(name = "control-server", isDaemon = true) { loop() }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
    }

    private fun loop() {
        while (running) {
            try {
                val server = ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))
                socket = server
                AppLog.d("REMOTE", "служебный канал слушает 127.0.0.1:$port")
                while (running) {
                    val client = server.accept()
                    runCatching { serve(client) }.onFailure { AppLog.w("REMOTE", "запрос упал: ${it.message}") }
                    runCatching { client.close() }
                }
            } catch (t: Throwable) {
                if (running) {
                    AppLog.w("REMOTE", "служебный канал: ${t.message}")
                    Thread.sleep(5_000)
                }
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    private fun serve(client: Socket) {
        client.soTimeout = 5_000
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val requestLine = reader.readLine() ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        val out = client.getOutputStream()
        when (val result = ControlRoute.parse(requestLine, headers, token)) {
            is ControlResult.Run -> run(result.action, out)
            ControlResult.Unauthorized -> respond(out, 401, "{\"error\":\"unauthorized\"}")
            ControlResult.NotFound -> respond(out, 404, "{\"error\":\"not found\"}")
            ControlResult.BadRequest -> respond(out, 400, "{\"error\":\"bad request\"}")
        }
    }

    private fun run(action: ControlAction, out: OutputStream) {
        AppLog.d("REMOTE", "команда: $action")
        when (action) {
            ControlAction.STATUS -> respond(out, 200, handler.status())
            ControlAction.LOGS -> {
                handler.uploadLogs()
                respond(out, 200, "{\"ok\":true,\"action\":\"logs\"}")
            }
            ControlAction.VPN -> {
                handler.handVpnProfile()
                respond(out, 200, "{\"ok\":true,\"action\":\"vpn\"}")
            }
            ControlAction.RESTART -> {
                // Answer first: the process is about to go away.
                respond(out, 200, "{\"ok\":true,\"action\":\"restart\"}")
                handler.restart()
            }
        }
    }

    private fun respond(out: OutputStream, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        else -> "Error"
    }

    companion object {
        const val PORT = 8973
    }
}
