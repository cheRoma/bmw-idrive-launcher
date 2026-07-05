package online.k73.bmwlauncher.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.music.MusicUiState
import online.k73.bmwlauncher.music.NowPlaying
import online.k73.bmwlauncher.music.TimeFormat
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

@Composable
fun MusicScreen(
    state: MusicUiState,
    albumArt: ImageBitmap?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onLike: () -> Unit,
    onPlaylists: () -> Unit,
    onColdStartPlay: () -> Unit,
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(24.dp)) {
        // top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Яндекс Музыка", color = c.textDim, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (state is MusicUiState.Playing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onPlaylists() }.padding(4.dp)) {
                    Icon(Icons.Filled.List, "Плейлисты", tint = c.text)
                    Text("Плейлисты", color = c.textDim, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (state) {
            MusicUiState.NoPermission -> Centered(c.textDim, "Дайте доступ к уведомлениям\nв Настройках") { onPlaylists() }
            MusicUiState.NoPlayback -> Centered(c.text, "Нет воспроизведения") { onColdStartPlay() }
            is MusicUiState.Playing -> PlayingBody(state.nowPlaying, albumArt, onPlayPause, onNext, onPrev, onSeek, onLike)
        }
    }
}

@Composable
private fun Centered(color: Color, text: String, onTap: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable { onTap() }, contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 22.sp)
    }
}

@Composable
private fun PlayingBody(
    np: NowPlaying, art: ImageBitmap?, onPlayPause: () -> Unit, onNext: () -> Unit,
    onPrev: () -> Unit, onSeek: (Long) -> Unit, onLike: () -> Unit,
) {
    val c = LocalLauncherColors.current
    Row(Modifier.fillMaxSize()) {
        // album art
        Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
            .background(c.tile), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, "cover", Modifier.fillMaxSize())
            else Icon(Icons.Filled.PlayArrow, null, tint = c.textDim, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.width(28.dp))
        // right column
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(np.title, color = c.text, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(np.artist, color = c.textDim, fontSize = 26.sp, maxLines = 1)
            Spacer(Modifier.height(28.dp))
            // progress (tap-and-drag seek)
            val frac = if (np.durationMs > 0) (np.positionMs.toFloat() / np.durationMs).coerceIn(0f, 1f) else 0f
            var dragFrac by remember(np.title, np.artist) { mutableStateOf<Float?>(null) }
            val shownFrac = dragFrac ?: frac
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .then(
                        if (np.durationMs > 0) Modifier
                            .pointerInput(np.durationMs) {
                                detectTapGestures { pos ->
                                    onSeek(((pos.x / size.width).coerceIn(0f, 1f) * np.durationMs).toLong())
                                }
                            }
                            .pointerInput(np.durationMs) {
                                detectHorizontalDragGestures(
                                    onDragStart = { pos -> dragFrac = (pos.x / size.width).coerceIn(0f, 1f) },
                                    onHorizontalDrag = { change, _ -> dragFrac = (change.position.x / size.width).coerceIn(0f, 1f) },
                                    onDragEnd = { dragFrac?.let { onSeek((it * np.durationMs).toLong()) }; dragFrac = null },
                                    onDragCancel = { dragFrac = null },
                                )
                            }
                        else Modifier
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.tile)) {
                    Box(Modifier.fillMaxWidth(shownFrac).height(6.dp).clip(CircleShape).background(c.accent))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(TimeFormat.mmss(np.positionMs), color = c.textDim, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(TimeFormat.mmss(np.durationMs), color = c.textDim, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
            // transport row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SkipPrevious, "prev", tint = c.text,
                    modifier = Modifier.size(48.dp).clickable { onPrev() })
                Box(Modifier.size(72.dp).clip(CircleShape).clickable { onPlayPause() }, contentAlignment = Alignment.Center) {
                    Icon(if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "play/pause",
                        tint = c.accent, modifier = Modifier.size(56.dp))
                }
                Icon(Icons.Filled.SkipNext, "next", tint = c.text,
                    modifier = Modifier.size(48.dp).clickable { onNext() })
                if (np.likeAvailable) {
                    var liked by remember(np.title, np.artist) { mutableStateOf(false) }
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, label = "likeScale")
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "like",
                        tint = if (liked) c.accent else c.textDim,
                        modifier = Modifier
                            .size(44.dp)
                            .scale(scale)
                            .clickable(interactionSource = interaction, indication = null) { liked = !liked; onLike() },
                    )
                }
            }
        }
    }
}
