package online.k73.bmwlauncher.diag

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.k73.bmwlauncher.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Builds a diagnostic report (device info + event log + logcat + last crash) and POSTs it to the
 * server as text/plain. See spec for the report layout and upload contract.
 */
object LogUploader {
    private const val MAX_LOGCAT_LINES = 4000
    private const val MAX_LOGCAT_BYTES = 200 * 1024
    private const val TIMEOUT_MS = 15_000

    /** Build the report name from device model + version + reason + timestamp, then sanitize. */
    fun sanitizedName(reason: String): String =
        sanitize("${Build.MODEL}-v${BuildConfig.VERSION_NAME}-$reason-${System.currentTimeMillis()}")

    /**
     * Assemble the report body. Pure/testable — no Android, no I/O. The PDC section is omitted
     * unless a capture actually ran, and sits before the (huge) logcat so it stays easy to find.
     */
    fun buildReport(device: String, eventLog: String, pdc: String, logcat: String, crash: String?): String =
        buildString {
            append(device)
            append("\n=== EVENT LOG ===\n")
            append(eventLog)
            if (pdc.isNotBlank()) {
                append("\n=== CAPTURE ===\n")
                append(pdc)
            }
            append("\n=== LOGCAT ===\n")
            append(logcat)
            if (!crash.isNullOrBlank()) {
                append("\n=== LAST CRASH ===\n")
                append(crash)
            }
        }

    /** Replace every char not in [A-Za-z0-9._-] with '_'. Pure/testable. */
    fun sanitize(raw: String): String =
        raw.map { if (it in ALLOWED) it else '_' }.joinToString("")

    private val ALLOWED: Set<Char> =
        (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('.', '_', '-')).toSet()

    suspend fun upload(context: Context, reason: String): Result<String> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (BuildConfig.LOG_UPLOAD_TOKEN.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Загрузка логов не настроена (нет токена)"))
        }
        val crashFile = File(app.filesDir, CrashHandler.PENDING_CRASH)
        val report = buildReport(
            device = DeviceInfo.collect(app),
            eventLog = AppLog.snapshot(),
            pdc = AppLog.pdcSnapshot(),
            logcat = captureLogcat(),
            crash = if (crashFile.exists()) runCatching { crashFile.readText() }.getOrDefault("") else null,
        )
        val name = sanitizedName(reason)
        val result = runCatching { post(report, name) }
        result.onSuccess {
            // Crash report delivered — drop the pending file so it isn't re-sent.
            runCatching { if (crashFile.exists()) crashFile.delete() }
        }
        result
    }

    private fun post(report: String, name: String): String {
        val url = URL(
            "${BuildConfig.LOG_UPLOAD_URL}?k=${enc(BuildConfig.LOG_UPLOAD_TOKEN)}&name=${enc(name)}"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.outputStream.use { it.write(report.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 200) {
                return body
            }
            throw java.io.IOException("HTTP $code: $body")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Dump our own process's logcat. Best-effort; returns "" on any failure. Also used by
     * [BlackScreenWatchdog], with a smaller tail, to bake the driver's own errors into the report it
     * persists — that copy is what survives when the car has no network at the time of the failure.
     */
    internal fun captureLogcat(
        maxLines: Int = MAX_LOGCAT_LINES,
        maxBytes: Int = MAX_LOGCAT_BYTES,
    ): String {
        return try {
            // redirectErrorStream drains stderr into stdout — reading a single stream to EOF
            // then can't deadlock on an unread stderr pipe. `logcat -d` dumps and exits.
            val proc = ProcessBuilder("logcat", "-d", "-v", "time")
                .redirectErrorStream(true)
                .start()
            val lines = proc.inputStream.bufferedReader().useLines { seq ->
                // Keep the tail: bounded deque of the last maxLines lines.
                val deque = ArrayDeque<String>(maxLines)
                for (line in seq) {
                    deque.addLast(line)
                    if (deque.size > maxLines) deque.removeFirst()
                }
                deque.toList()
            }
            runCatching {
                if (!proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) proc.destroyForcibly()
            }
            var text = lines.joinToString("\n")
            if (text.length > maxBytes) {
                text = text.substring(text.length - maxBytes)
            }
            text
        } catch (_: Throwable) {
            ""
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
