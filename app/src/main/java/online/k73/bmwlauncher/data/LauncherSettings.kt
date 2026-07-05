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
    val nightStartHour: Int = 20,
    val nightEndHour: Int = 7,
)
