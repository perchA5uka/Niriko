package com.otakup.niriko.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.animation.orSnap

/**
 * 多选态描边（F01）。
 *
 * 为什么是「内描边」而不是 border：border 会参与测量，选中/取消选中时若宽度变化
 * 就会让列表项抖一下（收藏页 LIST 项还在用 animateContentSize）；这里在 drawContent
 * 之后按 draw 尺寸内缩半个线宽画一圈，布局完全不变，只多一次重绘。
 *
 * 只对已有形状的卡片用：圆角传卡片自身的令牌（如 [com.otakup.niriko.ui.theme.NirikoShapes.Card]），
 * 描边半径再减半个线宽，才不会在圆角处露出「描边比卡片更方」的白边。
 *
 * @param selected 是否选中；false 时 alpha 动画到 0，draw 阶段直接跳过（零成本）。
 * @param color 描边色（收藏页传 primary）。
 * @param cornerRadius 卡片圆角（dp），必须与卡片实际形状一致。
 * @param width 描边宽度；3dp 与 PosterGridCard / LibraryGalleryCard 的既有选中框保持一致。
 */
@Composable
fun Modifier.selectionRing(
    selected: Boolean,
    color: Color,
    cornerRadius: Dp,
    width: Dp = 3.dp,
): Modifier {
    val reduceMotion = LocalReduceMotion.current
    // 只动画 alpha：选中态是「出现/消失」，不该有位移或缩放（列表里位移会顶到相邻项）
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = NirikoMotionSpecs.effectsFast<Float>().orSnap(reduceMotion),
        label = "selectionRingAlpha",
    )
    return this
        // 读屏软件需要「已选中」语义；draw 出来的描边对无障碍树是不可见的
        .semantics { this.selected = selected }
        .drawWithContent {
            drawContent()
            if (alpha <= 0.01f) return@drawWithContent
            val strokePx = width.toPx()
            // 尺寸小到放不下描边（如空占位）时直接跳过，避免画负数尺寸
            if (size.width <= strokePx || size.height <= strokePx) return@drawWithContent
            val inset = strokePx / 2f
            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha),
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokePx, size.height - strokePx),
                cornerRadius = CornerRadius(
                    x = (cornerRadius.toPx() - inset).coerceAtLeast(0f),
                    y = (cornerRadius.toPx() - inset).coerceAtLeast(0f),
                ),
                style = Stroke(width = strokePx),
            )
        }
}
