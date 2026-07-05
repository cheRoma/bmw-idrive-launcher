package online.k73.bmwlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LauncherColors(
    val background: Color,     // bgBase
    val tile: Color,           // surface
    val surfaceHi: Color,      // NEW
    val text: Color,           // textPrimary
    val textDim: Color,        // textSecondary
    val textTertiary: Color,   // NEW
    val accent: Color,
    val hairline: Color,       // NEW
    val callGreen: Color,      // NEW
)

val NightLauncherColors = LauncherColors(
    background = NightBgBase,
    tile = NightSurface,
    surfaceHi = NightSurfaceHi,
    text = NightTextPrimary,
    textDim = NightTextSecondary,
    textTertiary = NightTextTertiary,
    accent = BmwAmber,
    hairline = NightHairline,
    callGreen = CallGreen,
)

val DayLauncherColors = LauncherColors(
    background = DayBgBase,
    tile = DaySurface,
    surfaceHi = DaySurfaceHi,
    text = DayTextPrimary,
    textDim = DayTextSecondary,
    textTertiary = DayTextTertiary,
    accent = DayAmber,
    hairline = DayHairline,
    callGreen = CallGreen,
)

val LocalLauncherColors = staticCompositionLocalOf { NightLauncherColors }

@Composable
fun BmwLauncherTheme(isNight: Boolean, content: @Composable () -> Unit) {
    val colors = if (isNight) NightLauncherColors else DayLauncherColors

    CompositionLocalProvider(LocalLauncherColors provides colors) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = colors.background,
                surface = colors.tile,
                primary = colors.accent,
                onBackground = colors.text,
                onSurface = colors.text,
            ),
            typography = AppTypography,
            content = content,
        )
    }
}
