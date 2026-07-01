package online.k73.bmwlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LauncherColors(
    val background: Color,
    val tile: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color = BmwAmber,
)

val LocalLauncherColors = staticCompositionLocalOf {
    LauncherColors(NightBackground, NightTile, NightText, NightTextDim)
}

@Composable
fun BmwLauncherTheme(isNight: Boolean, content: @Composable () -> Unit) {
    val colors = if (isNight)
        LauncherColors(NightBackground, NightTile, NightText, NightTextDim)
    else
        LauncherColors(DayBackground, DayTile, DayText, DayTextDim)

    CompositionLocalProvider(LocalLauncherColors provides colors) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = colors.background,
                surface = colors.tile,
                primary = colors.accent,
                onBackground = colors.text,
                onSurface = colors.text,
            ),
            typography = Typography(),
            content = content,
        )
    }
}
