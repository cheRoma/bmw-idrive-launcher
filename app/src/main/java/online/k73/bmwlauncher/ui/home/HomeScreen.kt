package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

val defaultTiles = listOf(
    Tile(TileId.MUSIC, "Музыка", Icons.Filled.MusicNote, priority = true),
    Tile(TileId.NAV, "Навигация", Icons.Outlined.Navigation, priority = true),
    Tile(TileId.APPS, "Приложения", Icons.Outlined.Apps),
    Tile(TileId.IBUS, "Борткомпьютер", Icons.Outlined.Speed),
    Tile(TileId.SETTINGS, "Настройки", Icons.Outlined.Tune),
    Tile(TileId.CARPLAY, "CarPlay", Icons.Outlined.Cast),
    Tile(TileId.YOUTUBE, "YouTube", Icons.Outlined.SmartDisplay),
    // Ahead of ИВИ on purpose: Кинопоиск rides the Плюс subscription the car already signs into for
    // Яндекс.Музыка, and unlike YouTube it needs no tunnel — on the car's own SIM it just works.
    Tile(TileId.KINOPOISK, "Кинопоиск", Icons.Outlined.Theaters),
    Tile(TileId.IVI, "ИВИ", Icons.Outlined.Movie),
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
