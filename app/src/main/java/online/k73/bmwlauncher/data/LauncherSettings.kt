package online.k73.bmwlauncher.data

enum class ThemeMode { DAY, NIGHT, AUTO }

data class LauncherSettings(
    val autostartIBus: Boolean = true,
    val bringLauncherToFront: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val musicPackage: String = "ru.yandex.music",
    val navPackage: String = "ru.yandex.yandexnavi",
    val iBusPackage: String = "",
    val carplayPackage: String = "",
    val nightStartHour: Int = 20,
    val nightEndHour: Int = 7,
)
