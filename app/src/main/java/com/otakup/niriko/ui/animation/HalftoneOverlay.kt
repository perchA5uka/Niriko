package com.otakup.niriko.ui.animation

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.unit.dp
import com.otakup.niriko.util.HalftonePolicy

/**
 * 半调网点覆盖层（F18，计划 §11.3）。
 *
 * ## 为什么这样画
 *
 * - **画在内容之下**（`drawWithCache` + 只画自己，不重绘内容）：因此天然不会遮挡正文，
 *   也绝不会拦点击 —— 这两个「不能」是画法保证的，不靠运行时判断。
 * - **一次 drawPoints 画完**：错行点阵先把偏移算好（缓存在 cache 块里，只在尺寸变化时重算），
 *   再一次性提交。不是每点一次 drawCircle、更不是每帧跑 shader（§11.3 明确禁止每卡一个 shader）。
 * - 强度由 [HalftonePolicy] 给（有上限），颜色只取一点点当前前景色：浅底更淡、深底稍强。
 *
 * @param isDark 深色主题（决定强度）
 * @param contentColor 取色基准（一般传当前 onBackground / onSurface）
 */
fun Modifier.halftoneDots(
    isDark: Boolean,
    contentColor: Color,
): Modifier = drawWithCache {
    val spacingPx = HalftonePolicy.DOT_SPACING_DP.dp.toPx().coerceAtLeast(2f)
    val radiusPx = HalftonePolicy.DOT_RADIUS_DP.dp.toPx().coerceAtLeast(0.5f)
    val alpha = HalftonePolicy.alphaFor(isDark)
    val color = contentColor.copy(alpha = alpha)
    val points = ArrayList<Offset>()
    var row = 0
    var y = 0f
    while (y <= size.height) {
        // 错行排布（相邻行横向偏移半格），看起来像印刷网点而不是方阵
        val xOffset = if (row % 2 == 0) 0f else spacingPx / 2f
        var x = xOffset
        while (x <= size.width) {
            points += Offset(x, y)
            x += spacingPx
        }
        row++
        y += spacingPx
    }
    onDrawBehind {
        if (points.isEmpty()) return@onDrawBehind
        drawPoints(
            points = points,
            pointMode = PointMode.Points,
            color = color,
            strokeWidth = radiusPx * 2f,
        )
    }
}
