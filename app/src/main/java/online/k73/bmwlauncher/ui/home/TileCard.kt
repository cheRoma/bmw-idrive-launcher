package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.TypeTokens
import online.k73.bmwlauncher.ui.theme.pressable

/**
 * A single carousel card, Claude Design "иконко-центричная плитка".
 *
 * Filled graphite (surfaceHi) card, radius 18dp, with the ICON as the hero and a quiet secondary
 * label below. [focused] promotes it: amber icon + amber 2dp border (via [pressable]). The pager
 * (HomeCarousel) owns the 3D transform and the amber glow behind the focused card, so this card
 * just fills the slot it is given.
 */
@Composable
fun TileCard(tile: Tile, focused: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalLauncherColors.current
    val iconTint = if (focused) c.accent else c.textDim
    val labelColor = if (focused) c.textDim else c.textTertiary
    Column(
        modifier
            .fillMaxSize()
            .pressable(RoundedCornerShape(18.dp), focused = focused, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = tile.label,
            tint = iconTint,
            modifier = Modifier.size(if (focused) 58.dp else 43.dp),
        )
        Spacer(Modifier.height(if (focused) 18.dp else 14.dp))
        Text(
            text = tile.label,
            color = labelColor,
            fontFamily = Inter,
            fontSize = if (focused) TypeTokens.title else 15.sp,
            fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
