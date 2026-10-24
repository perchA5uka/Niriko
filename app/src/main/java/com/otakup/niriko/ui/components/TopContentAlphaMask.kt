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
 * 顶部渐隐遮罩（全仓唯一入口）。Apply only to the feed sibling, not the wallpaper or floating controls.
 *
 * **上边界 = 窗口原点**：feed 页一律传 `hiddenHeight = 0.dp`，渐隐带从屏幕顶部（含状态栏区域）
 * 开始、向下 24dp 把内容淡入 —— 与 v1.2.0 的发现页一致（v1.2.1 真机反馈的裁决）。
 *
 * `hiddenHeight` 参数保留只为详情页过渡期（它还在传 inset + 56dp 的实心台）；默认值同样是
 * 0.dp，新增调用点不要传非 0 值。内容避让看 `TopFadePolicy.feedTopPaddingDp`，不是这里。
 */
@Composable
fun Modifier.topContentAlphaMask(
    strength: () -> Float,
    height: Dp = TopFadePolicy.FADE_DISTANCE_DP.dp,
    hiddenHeight: Dp = 0.dp,
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
