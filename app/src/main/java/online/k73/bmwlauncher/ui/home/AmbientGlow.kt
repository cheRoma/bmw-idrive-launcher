package online.k73.bmwlauncher.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import online.k73.bmwlauncher.ui.theme.BmwAmber

/** A slow breathing amber radial glow low on the screen — E53 instrument-backlight ambiance.
 *  The gradient brush is cached per-size; only the alpha animates each frame. */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "glow")
    val intensity by transition.animateFloat(
        initialValue = 0.10f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "intensity",
    )
    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height * 1.05f)
                val brush = Brush.radialGradient(
                    colors = listOf(BmwAmber, Color.Transparent),
                    center = center, radius = size.width * 0.7f,
                )
                onDrawBehind { drawRect(brush = brush, alpha = intensity) }
            }
    )
}
