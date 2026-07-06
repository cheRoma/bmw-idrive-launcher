package online.k73.bmwlauncher.music.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.music.MusicUiState
import online.k73.bmwlauncher.music.NowPlaying
import online.k73.bmwlauncher.music.TimeFormat
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.pressScale

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
    onBack: () -> Unit,
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(horizontal = 32.dp, vertical = 20.dp)) {
        // Top bar: tappable back chevron — returns to the carousel.
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).pressScale(onBack), contentAlignment = Alignment.Center) {
                Text("‹", color = c.textDim, fontFamily = Inter, fontSize = 40.sp, fontWeight = FontWeight.Normal)
            }
        }
        // subtle amber baseline under the top bar
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(
                Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.7f), c.accent.copy(alpha = 0.06f))),
            ),
        )
        Spacer(Modifier.height(8.dp))
        when (state) {
            MusicUiState.NoPermission ->
                Centered(c.textDim, "Дайте доступ к уведомлениям\nв Настройках") { onPlaylists() }
            MusicUiState.NoPlayback ->
                Centered(c.text, "Нет воспроизведения") { onColdStartPlay() }
            is MusicUiState.Playing ->
                PlayingBody(state.nowPlaying, albumArt, onPlayPause, onNext, onPrev, onSeek, onLike, onPlaylists)
        }
    }
}

@Composable
private fun Centered(color: Color, text: String, onTap: () -> Unit) {
    val c = LocalLauncherColors.current
    Column(
        Modifier.fillMaxSize().clickable { onTap() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = c.textTertiary, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, color = color, fontFamily = Inter, fontSize = 22.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlayingBody(
    np: NowPlaying, art: ImageBitmap?, onPlayPause: () -> Unit, onNext: () -> Unit,
    onPrev: () -> Unit, onSeek: (Long) -> Unit, onLike: () -> Unit, onPlaylists: () -> Unit,
) {
    val c = LocalLauncherColors.current
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // ── Left: album art with a gentle "alive" motion while playing ──
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center) {
            val infinite = rememberInfiniteTransition(label = "art")
            val zoom by infinite.animateFloat(
                initialValue = 1f, targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
                label = "kenburns",
            )
            val glow by infinite.animateFloat(
                initialValue = 0.05f, targetValue = 0.16f,
                animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Reverse),
                label = "glow",
            )
            Box(contentAlignment = Alignment.Center) {
                if (np.isPlaying) {
                    // breathing amber halo behind the cover
                    Box(
                        Modifier.fillMaxHeight(0.80f).aspectRatio(1f).scale(1.16f).clip(RoundedCornerShape(28.dp))
                            .background(Brush.radialGradient(listOf(c.accent.copy(alpha = glow), Color.Transparent))),
                    )
                }
                Envelope(art, Modifier.fillMaxHeight(0.80f).aspectRatio(1f), zoom = if (np.isPlaying) zoom else 1f)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent))
                Spacer(Modifier.width(8.dp))
                Text("Яндекс Музыка · Моя волна", color = c.textDim, fontFamily = Inter, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.width(32.dp))
        // ── Right column: track info + controls ──
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                np.title, color = c.text, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(np.artist, color = c.textDim, fontFamily = Inter, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(28.dp))
            SeekBar(np, onSeek)
            Spacer(Modifier.height(24.dp))
            TransportRow(np.isPlaying, onPlayPause, onNext, onPrev)
            Spacer(Modifier.height(20.dp))
            PillRow(np, onLike, onPlaylists)
        }
    }
}

// ── Album art card ───────────────────────────────────────────────────────────
@Composable
private fun Envelope(art: ImageBitmap?, modifier: Modifier = Modifier, zoom: Float = 1f) {
    val c = LocalLauncherColors.current
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(c.tile)) {
        if (art != null) {
            // slow Ken-Burns zoom on the cover; the card clip keeps it in bounds
            Image(art, "Обложка альбома", Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom })
        } else {
            // Striped placeholder (45° stripes) + caption.
            val stripe = c.surfaceHi
            Canvas(Modifier.fillMaxSize()) {
                val s = 26.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(
                        color = stripe,
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = 13.dp.toPx(),
                    )
                    x += s * 2
                }
            }
            Text(
                "обложка альбома · 287×287 dp",
                color = c.textTertiary, fontFamily = Inter, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // Dark left spine.
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(9.dp).background(Color.Black.copy(alpha = 0.28f)))
    }
}

// ── Seek bar (preserves existing tap + horizontal-drag seek + dragFrac guard) ──
@Composable
private fun SeekBar(np: NowPlaying, onSeek: (Long) -> Unit) {
    val c = LocalLauncherColors.current
    val frac = if (np.durationMs > 0) (np.positionMs.toFloat() / np.durationMs).coerceIn(0f, 1f) else 0f
    var dragFrac by remember(np.title, np.artist) { mutableStateOf<Float?>(null) }
    // If durationMs changes mid-drag, the pointerInput is torn down without onDragCancel;
    // clear the optimistic fill so it can't stick.
    LaunchedEffect(np.durationMs) { dragFrac = null }
    val shownFrac = dragFrac ?: frac
    BoxWithConstraints(
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
                else Modifier,
            ),
    ) {
        val trackW = maxWidth
        // Trackline.
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                .background(c.text.copy(alpha = 0.12f)).align(Alignment.CenterStart),
        ) {
            Box(Modifier.fillMaxWidth(shownFrac).height(5.dp).clip(CircleShape).background(c.accent))
        }
        // Knob + amber halo.
        val knobD = 19.dp
        val haloD = 25.dp
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = trackW * shownFrac - haloD / 2)
                .size(haloD)
                .clip(CircleShape)
                .background(c.accent.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(knobD).clip(CircleShape).background(Color(0xFFE4E7EA)))
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        Text(TimeFormat.mmss(np.positionMs), color = c.textDim, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(TimeFormat.mmss(np.durationMs), color = c.textDim, fontFamily = Inter, fontSize = 15.sp)
    }
}

// ── Transport row ────────────────────────────────────────────────────────────
@Composable
private fun TransportRow(playing: Boolean, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit) {
    val c = LocalLauncherColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        // Prev
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(c.text.copy(alpha = 0.04f))
                .pressScale { onPrev() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.SkipPrevious, "Предыдущий", tint = c.text, modifier = Modifier.size(30.dp)) }
        // Play / pause with amber ring + glow
        Box(contentAlignment = Alignment.Center) {
            // radial glow
            Box(
                Modifier.size(105.dp).clip(CircleShape).background(
                    Brush.radialGradient(listOf(c.accent.copy(alpha = 0.20f), Color.Transparent)),
                ),
            )
            Box(
                Modifier.size(85.dp).clip(CircleShape).background(c.surfaceHi)
                    .border(2.dp, c.accent, CircleShape).pressScale { onPlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play/Pause", tint = c.accent, modifier = Modifier.size(40.dp),
                )
            }
        }
        // Next
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(c.text.copy(alpha = 0.04f))
                .pressScale { onNext() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.SkipNext, "Следующий", tint = c.text, modifier = Modifier.size(30.dp)) }
    }
}

// ── Pill row: like toggle + playlists ────────────────────────────────────────
@Composable
private fun PillRow(np: NowPlaying, onLike: () -> Unit, onPlaylists: () -> Unit) {
    val c = LocalLauncherColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (np.likeAvailable) {
            // Preserve like toggle: local liked keyed on track + press feedback.
            var liked by remember(np.title, np.artist) { mutableStateOf(false) }
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "likeScale")
            Row(
                Modifier
                    .scale(scale)
                    .height(51.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (liked) c.accent.copy(alpha = 0.10f) else Color.Transparent)
                    .border(
                        width = if (liked) 2.dp else 1.dp,
                        color = if (liked) c.accent.copy(alpha = 0.5f) else c.hairline,
                        shape = RoundedCornerShape(999.dp),
                    )
                    // One-way "add to Мне нравится": one tap likes; we DON'T toggle off on a second
                    // tap, because Yandex's actionLike may un-like and it never reports the real state.
                    .clickable(interactionSource = interaction, indication = null) { if (!liked) { liked = true; onLike() } }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    "Лайк", tint = if (liked) c.accent else c.text, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("В любимом", color = if (liked) c.accent else c.text, fontFamily = Inter, fontSize = 16.sp)
            }
        }
        // Playlists (outline)
        Row(
            Modifier
                .height(51.dp)
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
                .pressScale { onPlaylists() }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.List, "Плейлисты", tint = c.text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Плейлисты", color = c.text, fontFamily = Inter, fontSize = 16.sp)
        }
    }
}
