package online.k73.bmwlauncher.ui.apps

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import online.k73.bmwlauncher.launch.AppEntry
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

// Full hold duration (ms) required before reboot fires.
private const val REBOOT_HOLD_MS = 2500

@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    onLaunch: (String) -> Unit,
    onRebootHold: () -> Unit,
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(24.dp)) {
        Text("Приложения", color = c.text, fontSize = 26.sp, modifier = Modifier.padding(bottom = 16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(apps, key = { it.packageName }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(c.tile)
                        .pointerInput(app.packageName) { detectTapGestures(onTap = { onLaunch(app.packageName) }) }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(app.label, color = c.text, fontSize = 18.sp)
                }
            }
            item {
                RebootTile(onRebootHold = onRebootHold)
            }
        }
    }
}

@Composable
private fun RebootTile(onRebootHold: () -> Unit) {
    val c = LocalLauncherColors.current
    val scope = rememberCoroutineScope()
    // Progress 0f..1f drives the filling arc; only reaches 1f on a completed hold.
    val progress = androidx.compose.runtime.remember { Animatable(0f) }

    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(c.tile)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Animate 0f -> 1f over the remaining hold time; if the finger
                        // lifts or the gesture cancels first, snap back to 0 and do nothing.
                        val holdJob = scope.launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = (REBOOT_HOLD_MS * (1f - progress.value)).toInt(),
                                ),
                            )
                            if (progress.value >= 1f) onRebootHold()
                        }
                        val completed = tryAwaitRelease()
                        holdJob.cancel()
                        if (!completed || progress.value < 1f) {
                            scope.launch { progress.snapTo(0f) }
                        }
                    },
                )
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 6.dp)) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(40.dp)) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2f
                if (progress.value > 0f) {
                    drawArc(
                        color = Color(0xFFFF7E00),
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                }
            }
            Icon(Icons.Filled.RestartAlt, contentDescription = "Перезагрузка", tint = c.accent)
        }
        Text("Перезагрузка", color = c.text, fontSize = 18.sp)
        Text("удерживать", color = c.textDim, fontSize = 12.sp)
    }
}
