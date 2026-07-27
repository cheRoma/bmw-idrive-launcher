package online.k73.bmwlauncher.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusJsonTest {

    @Test fun writes_primitives_without_quoting_them() {
        assertEquals(
            """{"version":"1.6.41","code":69,"connected":true,"speed":null}""",
            StatusJson.of("version" to "1.6.41", "code" to 69, "connected" to true, "speed" to null),
        )
    }

    @Test fun escapes_what_would_otherwise_break_the_body() {
        assertEquals("""{"name":"Doro \"QCM\" \\ unit"}""", StatusJson.of("name" to "Doro \"QCM\" \\ unit"))
        assertEquals("""{"err":"line one\nline two"}""", StatusJson.of("err" to "line one\nline two"))
    }

    @Test fun escapes_control_characters_as_unicode() {
        // Raw bus bytes and ROM strings do reach this report; a stray 0x01 must not land in the body.
        val withControl = "a" + '\u0001' + "b"
        assertEquals("{\"raw\":\"a\\u0001b\"}", StatusJson.of("raw" to withControl))
    }

    @Test fun an_empty_report_is_still_valid_json() {
        assertEquals("{}", StatusJson.of())
    }
}
