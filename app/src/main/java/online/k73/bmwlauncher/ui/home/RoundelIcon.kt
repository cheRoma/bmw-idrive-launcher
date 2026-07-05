package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/** A small, stylised BMW roundel drawn with Canvas (no image asset needed). */
@Composable
fun RoundelIcon(
    modifier: Modifier,
    ring: Color = Color(0xFF2E2E2E),
    light: Color = Color(0xFFBFC3C8),
    dark: Color = Color(0xFF0E0E0E),
) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(ring, radius = r)
        val inner = r * 0.80f
        drawCircle(dark, radius = inner)
        val box = Rect(center.x - inner, center.y - inner, center.x + inner, center.y + inner)
        // BMW quadrants: top-left and bottom-right light, the other two dark
        drawArc(light, startAngle = 180f, sweepAngle = 90f, useCenter = true,
            topLeft = box.topLeft, size = box.size)
        drawArc(light, startAngle = 0f, sweepAngle = 90f, useCenter = true,
            topLeft = box.topLeft, size = box.size)
        // thin ring separating quadrants from the outer ring
        drawCircle(ring, radius = inner, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.06f))
    }
}
