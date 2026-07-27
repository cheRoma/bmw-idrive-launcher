package online.k73.bmwlauncher.vpn

import java.net.URLEncoder

/**
 * Hands the YouTube VPN profile to **SFA** (sing-box for Android) without anyone typing a URL on the
 * head unit's on-screen keyboard.
 *
 * SFA keeps its profiles in its own private storage, so a non-root app cannot write them. What it
 * does expose is sing-box's documented import scheme, registered on its MainActivity:
 *
 * ```
 * sing-box://import-remote-profile?url=<urlEncodedURL>#<name>
 * ```
 *
 * (Verified against `experimental/libbox/remote_profile.go` in sing-box v1.13.14 — the same version
 * the head unit runs — where the parser reads `Query().Get("url")` and takes the name from the
 * fragment.) Firing it opens SFA's import screen with everything filled in; the driver only confirms.
 *
 * The profile is **remote**, so SFA re-fetches it: changing VPN servers later needs no visit to the
 * car at all.
 */
object VpnProfile {
    const val SFA_PACKAGE = "io.nekohasekai.sfa"
    const val DEFAULT_NAME = "Ostov-NL"

    /** Pure and testable: the exact link sing-box's own parser expects. */
    fun importLink(profileUrl: String, name: String = DEFAULT_NAME): String =
        "sing-box://import-remote-profile?url=${enc(profileUrl)}#${enc(name)}"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
