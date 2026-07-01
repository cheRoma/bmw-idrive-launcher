package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

val defaultTiles = listOf(
    Tile(TileId.MUSIC, "Музыка", Icons.Filled.MusicNote, priority = true),
    Tile(TileId.NAV, "Навигация", Icons.Filled.Navigation, priority = true),
    Tile(TileId.APPS, "Приложения", Icons.Filled.GridView),
    Tile(TileId.IBUS, "Борткомпьютер", Icons.Filled.DirectionsCar),
    Tile(TileId.SETTINGS, "Настройки", Icons.Filled.Settings),
    Tile(TileId.CARPLAY, "CarPlay", Icons.Filled.PhoneIphone),
)

@Composable
fun HomeScreen(tiles: List<Tile> = defaultTiles, onTile: (TileId) -> Unit = {}) {
    val c = LocalLauncherColors.current
    Column(
        Modifier.fillMaxSize().background(c.background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        tiles.chunked(3).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                rowTiles.forEach { tile ->
                    TileCell(tile, modifier = Modifier.weight(1f)) { onTile(tile.id) }
                }
            }
        }
    }
}
