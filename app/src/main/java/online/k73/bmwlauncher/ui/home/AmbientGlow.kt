package online.k73.bmwlauncher.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import online.k73.bmwlauncher.ui.theme.BmwAmber

/** A slow breathing amber radial glow low on the screen — E53 instrument-backlight ambiance. */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "glow")
    val intensity by transition.animateFloat(
        initialValue = 0.10f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "intensity",
    )
    Canvas(modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 1.05f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(BmwAmber.copy(alpha = intensity), Color.Transparent),
                center = center, radius = size.width * 0.7f,
            )
        )
    }
}
