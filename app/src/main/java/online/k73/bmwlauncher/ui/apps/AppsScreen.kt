package online.k73.bmwlauncher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.launch.AppEntry
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    onLaunch: (String) -> Unit,
    onRebootHold: () -> Unit,
) {
    val c = LocalLauncherColors.current
    var showReboot by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.background).padding(24.dp)) {
        Text("Приложения", color = c.text, fontSize = 26.sp, modifier = Modifier.padding(bottom = 16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(c.tile)
                        .clickable { onLaunch(app.packageName) }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(app.label, color = c.text, fontSize = 18.sp)
                }
            }
            item {
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(c.tile)
                        .clickable { showReboot = true }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = "Перезагрузка",
                        tint = c.accent,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Text("Перезагрузка", color = c.text, fontSize = 18.sp)
                }
            }
        }
    }

    if (showReboot) {
        AlertDialog(
            onDismissRequest = { showReboot = false },
            containerColor = c.tile,
            title = { Text("Перезагрузка", color = c.text) },
            text = { Text("Перезагрузить систему?", color = c.text) },
            confirmButton = {
                Text(
                    "Перезагрузить",
                    color = c.accent,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showReboot = false; onRebootHold() }
                        .padding(12.dp),
                )
            },
            dismissButton = {
                Text(
                    "Отмена",
                    color = c.textDim,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { showReboot = false }
                        .padding(12.dp),
                )
            },
        )
    }
}
