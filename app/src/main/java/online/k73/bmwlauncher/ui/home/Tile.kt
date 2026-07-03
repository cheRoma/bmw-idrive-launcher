package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

enum class TileId { MUSIC, NAV, APPS, IBUS, SETTINGS, CARPLAY }

data class Tile(val id: TileId, val label: String, val icon: ImageVector, val priority: Boolean = false)

@Composable
fun TileCell(tile: Tile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalLauncherColors.current
    Column(
        modifier = modifier
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(14.dp))
            .background(c.tile)
            .clickable { onClick() }
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.material3.Icon(
            imageVector = tile.icon,
            contentDescription = tile.label,
            tint = c.text,
            modifier = Modifier.height(if (tile.priority) 64.dp else 56.dp),
        )
        Text(
            text = tile.label,
            color = c.text,
            fontSize = if (tile.priority) 22.sp else 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            softWrap = true,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(3.dp)
                .background(if (tile.priority) c.accent else c.tile)
        )
    }
}
