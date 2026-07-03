package online.k73.bmwlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeHttp(val body: String? = null, val boom: Boolean = false) : HttpClient {
    override fun getText(url: String): String {
        if (boom) throw IOException("network down")
        return body!!
    }
    override fun download(url: String, dest: File, onProgress: (Int) -> Unit) {}
}

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerFetchTest {
    private val json = """{"versionCode":5,"versionName":"1.0.4","apkUrl":"u","notes":"n"}"""

    @Test fun fetch_returns_available_when_newer() {
        val checker = UpdateChecker(FakeHttp(body = json), "https://manifest")
        val s = checker.fetch(currentCode = 3)
        assertTrue(s is UpdateStatus.Available)
    }
    @Test fun fetch_returns_up_to_date_when_same() {
        val checker = UpdateChecker(FakeHttp(body = json), "https://manifest")
        assertEquals(UpdateStatus.UpToDate, checker.fetch(currentCode = 5))
    }
    @Test fun fetch_returns_error_on_network_failure() {
        val checker = UpdateChecker(FakeHttp(boom = true), "https://manifest")
        val s = checker.fetch(currentCode = 1)
        assertTrue(s is UpdateStatus.Error)
    }
    @Test fun fetch_returns_error_on_bad_json() {
        val checker = UpdateChecker(FakeHttp(body = "garbage"), "https://manifest")
        assertTrue(checker.fetch(1) is UpdateStatus.Error)
    }
}
