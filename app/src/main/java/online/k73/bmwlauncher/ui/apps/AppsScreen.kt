package online.k73.bmwlauncher.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import online.k73.bmwlauncher.launch.AppEntry
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.ScreenHeader
import online.k73.bmwlauncher.ui.theme.TypeTokens
import online.k73.bmwlauncher.ui.theme.pressScale
import online.k73.bmwlauncher.ui.theme.pressable

private val TileShape = RoundedCornerShape(16.dp)

@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    onLaunch: (String) -> Unit,
    onRebootHold: () -> Unit,
) {
    val c = LocalLauncherColors.current
    var showReboot by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.background).padding(horizontal = 28.dp, vertical = 24.dp)) {
        ScreenHeader("Приложения")
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(app, onClick = { onLaunch(app.packageName) })
            }
            item {
                RebootTile(onClick = { showReboot = true })
            }
        }
    }

    if (showReboot) {
        AlertDialog(
            onDismissRequest = { showReboot = false },
            containerColor = c.tile,
            title = { Text("Перезагрузка", color = c.text, fontFamily = Inter) },
            text = { Text("Перезагрузить систему?", color = c.textDim, fontFamily = Inter) },
            confirmButton = {
                Text(
                    "Перезагрузить",
                    color = c.accent,
                    fontSize = TypeTokens.body,
                    fontFamily = Inter,
                    modifier = Modifier
                        .pressScale { showReboot = false; onRebootHold() }
                        .padding(12.dp),
                )
            },
            dismissButton = {
                Text(
                    "Отмена",
                    color = c.textDim,
                    fontSize = TypeTokens.body,
                    fontFamily = Inter,
                    modifier = Modifier
                        .pressScale { showReboot = false }
                        .padding(12.dp),
                )
            },
        )
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit) {
    val c = LocalLauncherColors.current
    Column(
        Modifier
            .height(172.dp)
            .pressable(TileShape, onClick = onClick)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(61.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = remember(app.icon) {
                app.icon?.let { runCatching { it.toBitmap(120, 120).asImageBitmap() }.getOrNull() }
            }
            if (bmp != null) {
                Image(bmp, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Text(
                    app.label.firstOrNull()?.uppercase() ?: "?",
                    color = c.textDim,
                    fontSize = TypeTokens.title,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            app.label,
            color = c.text,
            fontSize = TypeTokens.label,
            fontFamily = Inter,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RebootTile(onClick: () -> Unit) {
    val c = LocalLauncherColors.current
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f) }
    Column(
        Modifier
            .height(172.dp)
            .clip(TileShape)
            .pressScale(onClick)
            .drawBehind {
                drawRoundRect(
                    color = c.accent,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash),
                )
            }
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.PowerSettingsNew,
            contentDescription = "Перезагрузка",
            tint = c.accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Перезагрузка",
            color = c.accent,
            fontSize = TypeTokens.label,
            fontFamily = Inter,
            textAlign = TextAlign.Center,
        )
    }
}
