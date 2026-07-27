package online.k73.bmwlauncher.remote

/**
 * Minimal JSON writer for `GET /status`. Pure, and hand-rolled rather than pulled from a library
 * because the payload is a flat map of primitives — but the escaping is still tested: an unescaped
 * quote coming from, say, a device name would produce a broken body that is maddening to debug over
 * a channel whose whole purpose is debugging.
 */
object StatusJson {
    fun of(vararg fields: Pair<String, Any?>): String =
        fields.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key)}\":${render(value)}"
        }

    private fun render(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Int, is Long, is Double -> value.toString()
        else -> "\"${escape(value.toString())}\""
    }

    fun escape(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }
}
