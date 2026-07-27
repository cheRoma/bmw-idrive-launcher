package online.k73.bmwlauncher.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class VpnProfileTest {
    private val url = "https://k73.online/newBMW/vpn/deadbeef.json"

    @Test fun builds_the_scheme_sing_box_registers() {
        val link = VpnProfile.importLink(url)
        assertTrue(link, link.startsWith("sing-box://import-remote-profile?url="))
    }

    @Test fun the_url_survives_a_round_trip_through_the_query() {
        // sing-box parses it as Query().Get("url") — the separators inside our URL must be encoded,
        // or the profile address arrives truncated and the import fails in the car.
        val link = VpnProfile.importLink(url)
        val query = URI(link).rawQuery
        val encoded = query.removePrefix("url=")
        assertEquals(url, URLDecoder.decode(encoded, "UTF-8"))
        assertTrue("separators must not sit raw in the query: $query", !encoded.contains("://"))
    }

    @Test fun the_profile_name_travels_in_the_fragment() {
        val link = VpnProfile.importLink(url, "Ostov-NL")
        assertEquals("Ostov-NL", link.substringAfterLast('#'))
    }
}
