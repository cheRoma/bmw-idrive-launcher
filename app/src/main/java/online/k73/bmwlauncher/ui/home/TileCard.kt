package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.TileBorder

/** A single carousel card. [center] switches to the amber highlighted state. */
@Composable
fun TileCard(tile: Tile, center: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalLauncherColors.current
    val accent = if (center) c.accent else TileBorder
    val content = if (center) c.accent else c.text
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.background)
            .border(if (center) 3.dp else 1.dp, accent, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tile.icon, tile.label, tint = content, modifier = Modifier.height(if (center) 76.dp else 60.dp))
        Text(
            tile.label, color = content, fontSize = if (center) 24.sp else 20.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            maxLines = 2, softWrap = true,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
