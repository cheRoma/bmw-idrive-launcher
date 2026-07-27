package online.k73.bmwlauncher.data

enum class ThemeMode { DAY, NIGHT, AUTO }

data class LauncherSettings(
    val autostartIBus: Boolean = true,
    val bringLauncherToFront: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.NIGHT,
    val musicPackage: String = "ru.yandex.music",
    val navPackage: String = "ru.yandex.yandexnavi",
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
