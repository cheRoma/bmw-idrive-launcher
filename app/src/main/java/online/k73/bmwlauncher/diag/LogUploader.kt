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
        val report = buildString {
            append(DeviceInfo.collect(app))
            append("\n=== EVENT LOG ===\n")
            append(AppLog.snapshot())
            append("\n=== LOGCAT ===\n")
            append(captureLogcat())
            if (crashFile.exists()) {
                append("\n=== LAST CRASH ===\n")
                append(runCatching { crashFile.readText() }.getOrDefault(""))
            }
        }
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

    /** Dump our own process's logcat. Best-effort; returns "" on any failure. */
    private fun captureLogcat(): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
            val lines = proc.inputStream.bufferedReader().useLines { seq ->
                // Keep the tail: bounded deque of the last MAX_LOGCAT_LINES lines.
                val deque = ArrayDeque<String>(MAX_LOGCAT_LINES)
                for (line in seq) {
                    deque.addLast(line)
                    if (deque.size > MAX_LOGCAT_LINES) deque.removeFirst()
                }
                deque.toList()
            }
            runCatching { proc.destroy() }
            var text = lines.joinToString("\n")
            if (text.length > MAX_LOGCAT_BYTES) {
                text = text.substring(text.length - MAX_LOGCAT_BYTES)
            }
            text
        } catch (_: Throwable) {
            ""
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
