package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

/** Carousel page indicator: one rounded strip per tile; the active one is amber and wider. */
@Composable
fun PageIndicator(count: Int, active: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val c = LocalLauncherColors.current
    val current = active.coerceIn(0, count - 1)
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val isActive = i == current
            Box(
                Modifier
                    .height(3.dp)
                    .width(if (isActive) 21.dp else 11.dp)
                    .clip(shape)
                    .background(if (isActive) c.accent else c.textTertiary),
            )
        }
    }
}
