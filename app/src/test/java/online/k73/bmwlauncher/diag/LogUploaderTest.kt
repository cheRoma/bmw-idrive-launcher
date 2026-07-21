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

    @Test fun report_includes_pdc_section_before_logcat_when_captured() {
        val r = LogUploader.buildReport("DEV", "events", "60 0E 3F A0", "logcat", null)
        assertTrue("has PDC section", r.contains("=== PDC CAPTURE ===\n60 0E 3F A0"))
        assertTrue("PDC comes before logcat", r.indexOf("=== PDC CAPTURE ===") < r.indexOf("=== LOGCAT ==="))
    }

    @Test fun report_omits_pdc_section_when_no_capture_ran() {
        val r = LogUploader.buildReport("DEV", "events", "", "logcat", null)
        assertTrue("no empty PDC header", !r.contains("PDC CAPTURE"))
    }

    @Test fun report_appends_crash_only_when_present() {
        assertTrue(LogUploader.buildReport("D", "e", "", "l", "boom").contains("=== LAST CRASH ===\nboom"))
        assertTrue(!LogUploader.buildReport("D", "e", "", "l", null).contains("LAST CRASH"))
        assertTrue(!LogUploader.buildReport("D", "e", "", "l", "").contains("LAST CRASH"))
    }

    @Test fun sanitize_replaces_slashes_query_and_unicode() {
        val out = LogUploader.sanitize("Redmi Note?k=secret&x=1 модель")
        val allowed = (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('.', '_', '-')).toSet()
        assertTrue("no disallowed chars remain: $out", out.all { it in allowed })
        assertEquals("Redmi_Note_k_secret_x_1_______", out)
    }
}
