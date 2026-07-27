package online.k73.bmwlauncher.remote

/** The complete set of things the outside world can ask this launcher to do. */
enum class ControlAction { STATUS, LOGS, VPN, RESTART }

sealed interface ControlResult {
    data class Run(val action: ControlAction) : ControlResult
    data object Unauthorized : ControlResult
    data object NotFound : ControlResult
    data object BadRequest : ControlResult
}

/**
 * Parsing and routing for the launcher's control endpoint — pure, so the rules that decide what a
 * remote caller may do are provable here rather than on a head unit I can only reach *through* this
 * very channel.
 *
 * Deliberate choices:
 * - the token is checked **before** the path, so an unauthorized caller cannot map the endpoints;
 * - a blank configured token rejects everything, so a build without the secret (the public one) has
 *   a locked control port rather than an open one;
 * - the method must match — a `GET /restart` would let any stray prefetch reboot the launcher.
 *
 * There is no action here that runs an arbitrary command: the enum above is the whole surface.
 */
object ControlRoute {
    fun parse(requestLine: String, headers: Map<String, String>, token: String): ControlResult {
        val parts = requestLine.trim().split(' ')
        if (parts.size < 2) return ControlResult.BadRequest
        val method = parts[0].uppercase()
        val path = parts[1].substringBefore('?')
        if (method.isEmpty() || !path.startsWith('/')) return ControlResult.BadRequest

        val presented = headers.entries.firstOrNull { it.key.equals(TOKEN_HEADER, ignoreCase = true) }?.value
        if (token.isBlank() || presented == null || presented != token) return ControlResult.Unauthorized

        return when (method to path) {
            "GET" to "/status" -> ControlResult.Run(ControlAction.STATUS)
            "POST" to "/logs" -> ControlResult.Run(ControlAction.LOGS)
            "POST" to "/vpn" -> ControlResult.Run(ControlAction.VPN)
            "POST" to "/restart" -> ControlResult.Run(ControlAction.RESTART)
            else -> ControlResult.NotFound
        }
    }

    const val TOKEN_HEADER = "X-Token"
}
