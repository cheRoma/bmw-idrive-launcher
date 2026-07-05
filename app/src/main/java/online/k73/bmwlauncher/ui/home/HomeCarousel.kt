package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import java.time.LocalDateTime
import kotlin.math.absoluteValue

/**
 * Infinitely-looping 3D cylindrical tile carousel. Tap any tile to open it. The 3D look comes
 * entirely from CarouselGeometry, so it can be tuned without editing this file.
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

    Box(Modifier.fillMaxSize().background(c.background)) {
        AmbientGlow()
        Column(Modifier.fillMaxSize()) {
            StatusRibbon(RibbonClock.time(now), RibbonClock.date(now), temp)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val cardWidth = maxWidth * 0.30f
                val sidePadding = (maxWidth - cardWidth) / 2f
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(cardWidth),
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    modifier = Modifier.fillMaxWidth().height(maxHeight * 0.82f),
                ) { page ->
                    val tile = tiles[page % tiles.size]
                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val tf = CarouselGeometry.transformFor(offset)
                    TileCard(
                        tile = tile,
                        center = offset.absoluteValue < 0.5f,
                        modifier = Modifier
                            .width(cardWidth)
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = tf.rotationYDeg
                                scaleX = tf.scale
                                scaleY = tf.scale
                                alpha = tf.alpha
                                cameraDistance = 16f * density
                                translationX = tf.translationXFraction * size.width
                            },
                        onClick = { onTile(tile.id) },
                    )
                }
            }
        }
    }
}
