package online.k73.bmwlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerCompareTest {
    private val m = UpdateManifest(versionCode = 5, versionName = "1.0.4", apkUrl = "u", notes = "n")

    @Test fun newer_manifest_is_available() {
        val s = UpdateChecker.compare(currentCode = 3, manifest = m)
        assertTrue(s is UpdateStatus.Available)
        assertEquals("1.0.4", (s as UpdateStatus.Available).versionName)
        assertEquals("u", s.apkUrl)
    }
    @Test fun equal_version_is_up_to_date() {
        assertEquals(UpdateStatus.UpToDate, UpdateChecker.compare(5, m))
    }
    @Test fun older_manifest_is_up_to_date() {
        assertEquals(UpdateStatus.UpToDate, UpdateChecker.compare(9, m))
    }
}
