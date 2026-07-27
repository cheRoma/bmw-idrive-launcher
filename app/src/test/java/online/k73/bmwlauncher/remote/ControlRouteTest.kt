package online.k73.bmwlauncher.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlRouteTest {
    private val token = "s3cret"
    private val auth = mapOf("x-token" to token)

    private fun parse(line: String, headers: Map<String, String> = auth) =
        ControlRoute.parse(line, headers, token)

    @Test fun routes_the_four_actions_and_nothing_else() {
        assertEquals(ControlResult.Run(ControlAction.STATUS), parse("GET /status HTTP/1.1"))
        assertEquals(ControlResult.Run(ControlAction.LOGS), parse("POST /logs HTTP/1.1"))
        assertEquals(ControlResult.Run(ControlAction.VPN), parse("POST /vpn HTTP/1.1"))
        assertEquals(ControlResult.Run(ControlAction.RESTART), parse("POST /restart HTTP/1.1"))
        assertEquals(ControlResult.NotFound, parse("GET /shell HTTP/1.1"))
    }

    @Test fun a_query_string_does_not_hide_the_path() {
        assertEquals(ControlResult.Run(ControlAction.STATUS), parse("GET /status?full=1 HTTP/1.1"))
    }

    @Test fun the_method_has_to_match() {
        // /restart via GET would let a stray browser prefetch reboot the launcher.
        assertEquals(ControlResult.NotFound, parse("GET /restart HTTP/1.1"))
    }

    @Test fun header_names_are_case_insensitive() {
        assertEquals(ControlResult.Run(ControlAction.STATUS), parse("GET /status HTTP/1.1", mapOf("X-Token" to token)))
    }

    @Test fun a_wrong_or_missing_token_is_rejected_before_routing() {
        // Checked before the path so an unauthorized caller cannot map which endpoints exist.
        assertEquals(ControlResult.Unauthorized, parse("GET /status HTTP/1.1", mapOf("x-token" to "nope")))
        assertEquals(ControlResult.Unauthorized, parse("GET /status HTTP/1.1", emptyMap()))
        assertEquals(ControlResult.Unauthorized, parse("GET /shell HTTP/1.1", emptyMap()))
    }

    @Test fun an_unset_token_locks_the_endpoint_instead_of_opening_it() {
        // A build without the secret (the public one) must not expose a passwordless control port.
        assertEquals(ControlResult.Unauthorized, ControlRoute.parse("GET /status HTTP/1.1", mapOf("x-token" to ""), ""))
    }

    @Test fun garbage_is_a_bad_request() {
        assertEquals(ControlResult.BadRequest, parse(""))
        assertEquals(ControlResult.BadRequest, parse("HELLO"))
    }
}
