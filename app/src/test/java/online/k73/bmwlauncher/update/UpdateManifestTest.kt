package online.k73.bmwlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateManifestTest {
    @Test fun parses_valid_json() {
        val json = """{"versionCode":3,"versionName":"1.0.2","apkUrl":"https://x/y.apk","notes":"hi"}"""
        val m = UpdateManifest.parse(json)
        assertEquals(3, m.versionCode)
        assertEquals("1.0.2", m.versionName)
        assertEquals("https://x/y.apk", m.apkUrl)
        assertEquals("hi", m.notes)
    }
    @Test fun notes_defaults_to_empty_when_missing() {
        val m = UpdateManifest.parse("""{"versionCode":1,"versionName":"1.0","apkUrl":"u"}""")
        assertEquals("", m.notes)
    }
    @Test fun throws_on_malformed_json() {
        assertThrows(Exception::class.java) { UpdateManifest.parse("not json") }
    }
}
