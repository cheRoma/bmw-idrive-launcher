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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import online.k73.bmwlauncher.music.ColdStartPhase
import online.k73.bmwlauncher.music.MusicUiState
import online.k73.bmwlauncher.music.NowPlaying
import online.k73.bmwlauncher.ui.home.RibbonClock
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
    onShuffle: () -> Unit,
    onSource: () -> Unit,
    onColdStartPlay: () -> Unit,
    onBack: () -> Unit,
    coldStart: ColdStartPhase = ColdStartPhase.IDLE,
) {
    val c = LocalLauncherColors.current
    val playing = state as? MusicUiState.Playing
    Box(Modifier.fillMaxSize()) {
        // ── Background: album cover (Playing) or a dark fallback ──
        MusicBackground(if (playing != null) albumArt else null)
        Column(Modifier.fillMaxSize()) {
            MusicTopBar(onBack)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    MusicUiState.NoPermission ->
                        Centered(c.textDim, "Дайте доступ к уведомлениям\nв Настройках") { onSource() }
                    MusicUiState.NoPlayback -> when (coldStart) {
                        // Auto-waking Yandex in the background — informational, non-interactive.
                        ColdStartPhase.WAKING -> Centered(c.textDim, "Запускаю музыку…", onTap = {})
                        // Background wake couldn't rouse a killed Yandex — offer an explicit launch.
                        ColdStartPhase.FAILED -> ColdStartButton(onColdStartPlay)
                        // Fallback (no auto-wake ran): the bare placeholder is itself the tap target.
                        ColdStartPhase.IDLE -> Centered(c.text, "Нет воспроизведения") { onColdStartPlay() }
                    }
                    is MusicUiState.Playing -> PlayingV4(
                        state.nowPlaying, onPlayPause, onNext, onPrev,
                        onSeek, onLike, onShuffle, onSource,
                    )
                }
            }
        }
    }
}

// ── Background: full-screen album cover + scrim + vignette (v4) ────────────────
@Composable
private fun MusicBackground(art: ImageBitmap?) {
    val c = LocalLauncherColors.current
    val scrim = Color(0xFF0B0C0E)
    Box(Modifier.fillMaxSize()) {
        if (art != null) {
            Image(art, "Обложка альбома", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF141619)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MusicNote, null, tint = c.accent.copy(alpha = 0.20f), modifier = Modifier.size(160.dp))
            }
        }
        // Vertical scrim so text stays legible over any cover.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to scrim.copy(alpha = 0.72f), 0.30f to scrim.copy(alpha = 0.38f),
                    0.55f to scrim.copy(alpha = 0.44f), 1f to scrim.copy(alpha = 0.82f),
                ),
            ),
        )
        // Radial vignette.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(0.40f to Color.Transparent, 1f to scrim.copy(alpha = 0.55f)),
            ),
        )
    }
}

// ── Top bar: back chevron (left) + clock (right); no amber divider on this screen ──
@Composable
private fun MusicTopBar(onBack: () -> Unit) {
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = java.time.LocalDateTime.now(); delay(10_000) } }
    Row(
        Modifier.fillMaxWidth().height(51.dp).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 64.dp, height = 43.dp).pressScale(onBack), contentAlignment = Alignment.CenterStart) {
            Text("‹", color = Color(0xFFC7CCD2), fontFamily = Inter, fontSize = 40.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            RibbonClock.time(now), color = Color(0xFFE4E7EA), fontFamily = Inter,
            fontWeight = FontWeight.SemiBold, fontSize = 21.sp,
        )
        // Temperature intentionally hidden until a Microntek broadcast is wired (matches StatusRibbon).
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

/** Explicit "start music" affordance shown when a background wake couldn't rouse a killed Yandex. */
@Composable
private fun ColdStartButton(onPlay: () -> Unit) {
    val c = LocalLauncherColors.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = c.textTertiary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .height(64.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.accent.copy(alpha = 0.10f))
                .border(2.dp, c.accent, RoundedCornerShape(999.dp))
                .pressScale(onPlay)
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, "Включить", tint = c.accent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Text("Включить музыку", color = c.text, fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Playing body v4: cover-bg content — source badge, title/artist, wave progress, transport ──
@Composable
private fun PlayingV4(
    np: NowPlaying, onPlayPause: () -> Unit, onNext: () -> Unit,
    onPrev: () -> Unit, onSeek: (Long) -> Unit, onLike: () -> Unit, onShuffle: () -> Unit, onSource: () -> Unit,
) {
    val titleShadow = Shadow(Color(0x99000000), Offset(0f, 6f), 16f)
    val textShadow = Shadow(Color(0x99000000), Offset(0f, 4f), 12f)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(49.dp)) // center block ≈100dp from screen top (51 top bar + 49)
        SourceBadge(onSource)
        Spacer(Modifier.height(9.dp))
        Text(
            np.title.ifBlank { " " }, color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 37.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(shadow = titleShadow), modifier = Modifier.padding(horizontal = 64.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            np.artist, color = Color(0xFFC7CCD2), fontFamily = Inter, fontSize = 20.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = textShadow),
        )
        Spacer(Modifier.weight(1f))
        EqualizerProgress(np, onSeek)
        Spacer(Modifier.weight(1.1f))
        TransportV4(np, onPlayPause, onNext, onPrev, onLike, onShuffle)
        Spacer(Modifier.height(43.dp))
    }
}

// Source badge pill — tap opens Yandex / grants access (Плейлисты moved here in v4).
@Composable
private fun SourceBadge(onSource: () -> Unit) {
    Row(
        Modifier.height(29.dp).clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF0B0C0E).copy(alpha = 0.5f)).pressScale(onSource).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(LocalLauncherColors.current.accent))
        Spacer(Modifier.width(8.dp))
        Text("Яндекс Музыка · Моя волна", color = Color(0xFFC7CCD2), fontFamily = Inter, fontSize = 14.sp)
    }
}

// ── Transport row: shuffle · prev · play/pause · next · like ──
@Composable
private fun TransportV4(
    np: NowPlaying, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    onLike: () -> Unit, onShuffle: () -> Unit,
) {
    val amber = LocalLauncherColors.current.accent
    val panel = Color(0xFF0B0C0E)
    var shuffleOn by remember { mutableStateOf(false) }
    var liked by remember(np.title, np.artist) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleControl(61.dp, Color.Transparent, onClick = { shuffleOn = !shuffleOn; onShuffle() }) {
            Icon(Icons.Filled.Shuffle, "Перемешать", tint = if (shuffleOn) amber else Color(0xFFC7CCD2), modifier = Modifier.size(27.dp))
        }
        CircleControl(69.dp, panel.copy(alpha = 0.55f), onClick = onPrev) {
            Icon(Icons.Filled.SkipPrevious, "Предыдущий", tint = Color(0xFFE4E7EA), modifier = Modifier.size(29.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp).clip(CircleShape).background(Brush.radialGradient(listOf(amber.copy(alpha = 0.30f), Color.Transparent))))
            CircleControl(91.dp, panel.copy(alpha = 0.6f), ring = amber, ringWidth = 2.dp, onClick = onPlayPause) {
                Icon(if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = amber, modifier = Modifier.size(35.dp))
            }
        }
        CircleControl(69.dp, panel.copy(alpha = 0.55f), onClick = onNext) {
            Icon(Icons.Filled.SkipNext, "Следующий", tint = Color(0xFFE4E7EA), modifier = Modifier.size(29.dp))
        }
        if (np.likeAvailable) {
            CircleControl(
                61.dp,
                if (liked) amber.copy(alpha = 0.14f) else Color.Transparent,
                ring = if (liked) amber.copy(alpha = 0.55f) else null, ringWidth = 1.dp,
                onClick = { if (!liked) { liked = true; onLike() } },
            ) {
                Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Лайк", tint = if (liked) amber else Color(0xFFC7CCD2), modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun CircleControl(
    size: Dp, bg: Color, ring: Color? = null, ringWidth: Dp = 2.dp,
    onClick: () -> Unit, content: @Composable () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .then(if (ring != null) Modifier.border(ringWidth, ring, CircleShape) else Modifier)
            .pressScale(onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ── Progress: passed portion is a live amber equalizer; remainder is a thin line ──
@Composable
private fun EqualizerProgress(np: NowPlaying, onSeek: (Long) -> Unit) {
    val amber = LocalLauncherColors.current.accent
    val amberLite = Color(0xFFFFB25E)
    val frac = if (np.durationMs > 0) (np.positionMs.toFloat() / np.durationMs).coerceIn(0f, 1f) else 0f
    var dragFrac by remember(np.title, np.artist) { mutableStateOf<Float?>(null) }
    LaunchedEffect(np.durationMs) { dragFrac = null }
    val shown = dragFrac ?: frac
    // Running clock that freezes when paused (the effect cancels → tMs stops advancing → bars hold).
    var tMs by remember { mutableStateOf(0L) }
    LaunchedEffect(np.isPlaying) { if (np.isPlaying) while (true) withFrameMillis { tMs = it } }
    // Live spectrum from the global output mix; null ⇒ Visualizer unavailable ⇒ synthetic fallback.
    val spectrum by rememberAudioSpectrum(active = np.isPlaying, nBands = 22)
    Column(Modifier.fillMaxWidth().padding(horizontal = 64.dp)) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).then(
                if (np.durationMs > 0) Modifier
                    .pointerInput(np.durationMs) {
                        detectTapGestures { pos -> onSeek(((pos.x / size.width).coerceIn(0f, 1f) * np.durationMs).toLong()) }
                    }
                    .pointerInput(np.durationMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { pos -> dragFrac = (pos.x / size.width).coerceIn(0f, 1f) },
                            onHorizontalDrag = { ch, _ -> dragFrac = (ch.position.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = { dragFrac?.let { onSeek((it * np.durationMs).toLong()) }; dragFrac = null },
                            onDragCancel = { dragFrac = null },
                        )
                    }
                else Modifier,
            ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val cy = size.height / 2f
                val stepPx = 4.2.dp.toPx()   // thinner + airier than v4 (was 5.3)
                val barW = 1.6.dp.toPx()     // slimmer bars (was 2.7)
                val r = 0.8.dp.toPx()
                val floorH = 4.dp.toPx()
                val maxH = 44.dp.toPx()
                val boundary = w * shown
                val count = (w / stepPx).toInt().coerceAtLeast(1)
                val spec = spectrum
                for (i in 0 until count) {
                    val x = i * stepPx + stepPx / 2f
                    if (x > boundary) break
                    val bh = if (spec != null && spec.isNotEmpty()) {
                        // Live audio: interpolate the spectrum envelope across bars (bass→treble).
                        val f = i.toFloat() / count * spec.size
                        val b0 = f.toInt().coerceIn(0, spec.size - 1)
                        val b1 = (b0 + 1).coerceIn(0, spec.size - 1)
                        val level = (spec[b0] + (spec[b1] - spec[b0]) * (f - b0)).coerceIn(0f, 1f)
                        floorH + level * (maxH - floorH)
                    } else {
                        // Fallback: synthetic per-bar shape + time pulse (freezes when paused).
                        val baseH = ((14f + abs(sin(i * 0.9f) * 26f + sin(i * 0.37f) * 14f)) * 2f / 3f).dp.toPx()
                        val phase = (tMs - (i * 53) % 400).toFloat() / (420 + (i * 37) % 240).toFloat()
                        val amp = (0.625f + 0.375f * sin(phase * 2f * PI.toFloat() + i)).coerceIn(0.25f, 1f)
                        baseH * amp
                    }
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(amberLite, amber), startY = cy - bh / 2, endY = cy + bh / 2),
                        topLeft = Offset(x - barW / 2, cy - bh / 2),
                        size = Size(barW, bh),
                        cornerRadius = CornerRadius(r, r),
                    )
                }
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(boundary, cy - 2.dp.toPx()),
                    size = Size((w - boundary).coerceAtLeast(0f), 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
                drawCircle(amber.copy(alpha = 0.25f), radius = 15.5.dp.toPx(), center = Offset(boundary, cy))
                drawCircle(amber, radius = 11.5.dp.toPx(), center = Offset(boundary, cy))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(TimeFormat.mmss(np.positionMs), color = Color(0xFFC7CCD2), fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(TimeFormat.mmss(np.durationMs), color = Color(0xFFC7CCD2), fontFamily = Inter, fontSize = 15.sp)
        }
    }
}
