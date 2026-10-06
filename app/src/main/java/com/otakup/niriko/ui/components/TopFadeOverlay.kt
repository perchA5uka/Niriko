package com.otakup.niriko.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Legacy color scrim for callers that explicitly need a background-colored overlay.
 * Feed pages use topContentAlphaMask instead so wallpaper and floating controls remain untouched.
 *
 * - **独立 Overlay**：不参与任何列表的测量，放在列表与顶部控件之间；
 * - **连续**：强度由调用方给的 [alpha] 连续驱动（来自 `TopFadePolicy.alphaFor`），
 *   不在这里做任何阈值判断 —— 阈值显隐正是 §8.3 禁止的做法；
 * - **不消费手势**：没有 `pointerInput`，也没有 `clickable`，因此滚动与点击照常穿透
 *   （这条是 F12「防止浮层吞掉第一项或滚动手势」的一半，另一半是列表的顶部内边距）。
 *
 * 颜色取页面背景色：从顶部「实一点」向下渐变到全透明，因此内容滚到控件下面时是**渐隐**而不是被一刀切掉。
 *
 * @param alpha 0..1 的整体强度（已含 CSS 意义上的 min/max 包络）
 * @param color 渐隐底色（默认取页面背景；详情页那种「背景墙铺到状态栏后」的页面可传自己的底色）
 */
@Composable
fun TopFadeOverlay(
    alpha: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 96.dp,
    color: Color = MaterialTheme.colorScheme.background,
) {
    val clamped = alpha.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (clamped <= 0.01f) return@Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    Modifier.drawWithBrush(
                        Brush.verticalGradient(
                            colors = listOf(
                                color.copy(alpha = clamped),
                                color.copy(alpha = clamped * 0.55f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                ),
        )
    }
}

/** 把画刷铺满自身的小工具（避免每个调用点各写一遍 drawBehind）。 */
private fun Modifier.drawWithBrush(brush: Brush): Modifier =
    drawBehind { drawRect(brush = brush, size = size) }
