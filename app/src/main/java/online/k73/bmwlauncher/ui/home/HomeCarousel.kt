package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import java.time.LocalDateTime
import kotlin.math.absoluteValue

// ── Carousel geometry (Claude Design, screen_2a / screen_1j) ─────────────────
// The focus tile is a 264dp square (radius 18dp). Neighbours scale down via CarouselGeometry.
// Page slots are narrower than the card (233dp centre-to-centre), so cards overlap and the side
// tiles bleed off both edges the way the mockup shows.
private val FocusCardSize = 264.dp
private val PageWidth = 233.dp
private val GlowSize = 416.dp

/**
 * Infinitely-looping 3D cylindrical tile carousel (Claude Design home). Tap any tile to open it.
 * The 3D look comes from CarouselGeometry; the amber halo behind the focused tile and the assembly
 * (ribbon + pager + page indicator over the ambient glow) live here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeCarousel(
    tiles: List<Tile> = defaultTiles,
    now: LocalDateTime,
    temp: String? = null,
    onTile: (TileId) -> Unit = {},
) {
    val c = LocalLauncherColors.current
    val loops = 1000
    val startPage = (loops / 2) * tiles.size
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { loops * tiles.size })

    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        // Live map (our dark+amber style) as the backdrop, behind everything. Renders nothing if the
        // map is unavailable → the solid c.background above shows through (the previous look).
        MapBackground(Modifier.fillMaxSize())
        // Scrim over the map so it recedes into a calm backdrop and the tiles / status / dots stay
        // legible — darker at the top and bottom edges, lighter through the middle so the amber
        // street network still reads.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xD90A0B0D),
                    0.30f to Color(0x8C0A0B0D),
                    0.62f to Color(0x730A0B0D),
                    1f to Color(0xB00A0B0D),
                ),
            ),
        )
        // Center the settled tile using the REAL available width (never a hardcoded 1280dp).
        val sidePadding = (maxWidth - PageWidth) / 2
        AmbientGlow()
        Column(Modifier.fillMaxSize()) {
            StatusRibbon(RibbonClock.time(now), RibbonClock.date(now), temp)
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(PageWidth),
                contentPadding = PaddingValues(horizontal = sidePadding),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val tile = tiles[page % tiles.size]
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val tf = CarouselGeometry.transformFor(offset)
                val focused = offset.absoluteValue < 0.5f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .graphicsLayer {
                            rotationY = tf.rotationYDeg
                            scaleX = tf.scale
                            scaleY = tf.scale
                            alpha = tf.alpha
                            cameraDistance = 16f * density
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (focused) {
                        val glow = c.accent
                        Spacer(
                            Modifier
                                .size(GlowSize)
                                .drawBehind {
                                    drawRect(
                                        Brush.radialGradient(
                                            colors = listOf(glow.copy(alpha = 0.38f), Color.Transparent),
                                            center = Offset(size.width / 2f, size.height / 2f),
                                            radius = size.minDimension / 2f,
                                        ),
                                    )
                                },
                        )
                    }
                    Box(Modifier.size(FocusCardSize)) {
                        TileCard(tile = tile, focused = focused, onClick = { onTile(tile.id) })
                    }
                }
            }
            PageIndicator(
                count = tiles.size,
                active = pagerState.currentPage % tiles.size,
                modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp),
            )
        }
    }
}
