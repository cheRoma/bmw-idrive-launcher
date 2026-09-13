package online.k73.bmwlauncher.data

enum class ThemeMode { DAY, NIGHT, AUTO }

/**
 * What the НАВИГАТОР tile opens. Yango Maps is the international twin of Яндекс Карты — same maps,
 * same traffic, no ads — but it is a maps app with a navigation mode, not the driver-first
 * Навигатор. Which one is better on the road is not something the code can decide, so the choice
 * sits in Settings and can be flipped mid-trip instead of needing a new build.
 */
enum class NavApp(val pkg: String, val label: String) {
    // Labels are the segment captions under the «Навигатор» row, so they name the vendor, not the app.
    YANDEX("ru.yandex.yandexnavi", "Яндекс"),
    YANGO("com.yango.maps.android", "Yango"),
    ;

    companion object {
        /** A package set by hand over the tunnel is still honoured; the UI just shows it as Навигатор. */
        fun of(pkg: String): NavApp = entries.firstOrNull { it.pkg == pkg } ?: YANDEX
    }
}

data class LauncherSettings(
    val autostartIBus: Boolean = true,
    val bringLauncherToFront: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.NIGHT,
    val musicPackage: String = "ru.yandex.music",
    val navPackage: String = NavApp.YANDEX.pkg,
    // Discovered on the real head unit (Microntek/XTRONS, Android 13):
    val iBusPackage: String = "com.e39.ak.e39ibus.app",
    // Z-Link (ZLINK5) is the working CarPlay app on this unit; CarbitLink/net.easyconn does not work.
    val carplayPackage: String = "com.zjinnova.zlink",
    // Mirrors fold when the key goes off, unfold on ignition. Off by default: it must not start
    // driving motors before the manual buttons have been tried in the car.
    val mirrorAutoFold: Boolean = false,
    // The launcher's own reverse tunnel to the VPS. On by default: it is the only way to reach the
    // car without asking the driver to tap through menus, which is the problem it was built for.
    // Starting the car should land on the launcher, not on whatever the unit restored from the last
    // trip (YouTube, the navigator). On by default — it is what the driver expects.
    val homeOnIgnition: Boolean = true,
    val remoteAccess: Boolean = true,
    val nightStartHour: Int = 20,
    val nightEndHour: Int = 7,
)
