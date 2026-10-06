package com.otakup.niriko.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.util.TopFadePolicy

/**
 * Apply only to the feed sibling, not the wallpaper or floating controls.
 * Window-origin feeds pass hiddenHeight = 0.dp; legacy inset viewports keep their plateau.
 */
@Composable
fun Modifier.topContentAlphaMask(
    strength: () -> Float,
    height: Dp = TopFadePolicy.FADE_DISTANCE_DP.dp,
    hiddenHeight: Dp = 56.dp,
): Modifier {
    val heightPx = with(LocalDensity.current) { height.toPx() }
    val hiddenPx = with(LocalDensity.current) { hiddenHeight.toPx() }
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val start = hiddenPx.coerceIn(0f, size.height)
            val band = heightPx.coerceIn(0f, size.height - start)
            val effectiveStrength = (strength() / TopFadePolicy.MAX_ALPHA).coerceIn(0f, 1f)
            if (start > 0f) {
                drawRect(Color.White.copy(alpha = 1f - effectiveStrength),
                    size = Size(size.width, start), blendMode = BlendMode.DstIn)
            }
            if (band > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = TopFadePolicy.contentAlphaAt(0f, band, effectiveStrength)),
                            Color.White,
                        ),
                        startY = start,
                        endY = start + band,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, start),
                    size = Size(size.width, band),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
