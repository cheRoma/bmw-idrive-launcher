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
    // CarbitLink (net.easyconn) — the app Roma uses for CarPlay on this unit (label "CarbitLink").
    val carplayPackage: String = "net.easyconn",
    val nightStartHour: Int = 20,
    val nightEndHour: Int = 7,
)
