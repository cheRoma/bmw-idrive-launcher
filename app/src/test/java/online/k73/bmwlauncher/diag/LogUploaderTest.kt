package online.k73.bmwlauncher.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUploaderTest {
    @Test fun sanitize_keeps_allowed_chars() {
        assertEquals("Abc-1.2_3", LogUploader.sanitize("Abc-1.2_3"))
    }

    @Test fun sanitize_replaces_spaces_and_symbols() {
        assertEquals("BMW_X5_v1.4.1", LogUploader.sanitize("BMW X5/v1.4.1"))
    }

    @Test fun sanitize_replaces_slashes_query_and_unicode() {
        val out = LogUploader.sanitize("Redmi Note?k=secret&x=1 модель")
        val allowed = (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('.', '_', '-')).toSet()
        assertTrue("no disallowed chars remain: $out", out.all { it in allowed })
        assertEquals("Redmi_Note_k_secret_x_1_______", out)
    }
}
