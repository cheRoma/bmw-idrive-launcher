package online.k73.bmwlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class WritingHttp(val bytes: ByteArray) : HttpClient {
    override fun getText(url: String) = ""
    override fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        dest.writeBytes(bytes); onProgress(100)
    }
}

class ApkDownloaderTest {
    @Test fun downloads_to_cache_and_reports_progress() {
        val tmp = File.createTempFile("cache", "").parentFile
        val http = WritingHttp("APKDATA".toByteArray())
        var lastPercent = -1
        val downloader = ApkDownloader(http, tmp)
        val file = downloader.download("https://x/app.apk") { lastPercent = it }
        assertTrue(file.exists())
        assertEquals("APKDATA", file.readText())
        assertEquals(100, lastPercent)
    }
}
