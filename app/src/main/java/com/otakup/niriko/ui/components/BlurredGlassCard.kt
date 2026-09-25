package com.otakup.niriko.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.LocalDarkTheme
import kotlin.math.max

/**
 * 模糊封面玻璃卡片（与详情页关联条目同款）：
 * 背景 = 共享模糊封面按卡片尺寸全幅覆盖绘制（Crop），每张卡不再依赖窗口坐标采样，
 * 长内容卡片也不会因越界 Clamp 塌缩成纯色带/断裂。
 * 叠加主题 tint（浅色白 / 深色黑）+ 顶部受光/底部沉降 + 圆角高光边框。
 *
 * [bitmap] 为 null（无封面背景墙/加载中）时回退 [appleGlassCard] 静态玻璃，保证可读性。
 */
@Composable
fun BlurredGlassSurface(
    bitmap: Bitmap?,
    shape: Shape,
    modifier: Modifier = Modifier,
    /** 高光边框圆角（与 [shape] 视觉一致；Compose Shape 不便提取 CornerRadius，显式传入）。 */
    borderCornerRadius: Float = 16f,
    content: @Composable () -> Unit,
) {
    if (bitmap == null) {
        // 加载中/无封面：用中性半透明底（surfaceContainerHigh），避免纯黑。
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (LocalDarkTheme.current) 0.55f else 0.75f),
                ),
        ) { content() }
        return
    }
    val isDark = LocalDarkTheme.current
    val coverBitmap = bitmap.asImageBitmap()
    // 中性 tint：必须在 @Composable 上下文中读取，供 drawWithCache 的非 Composable 绘制块使用。
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier = modifier
            // 圆角裁剪必须在 drawWithCache 之前：clip 在 drawWithCache 内层时裁剪不到玻璃矩形
            .clip(shape)
            .drawWithCache {
                val bmp = coverBitmap
                onDrawBehind {
                    if (size.width > 0 && size.height > 0) {
                        // 全幅覆盖绘制：把模糊封面按 ContentScale.Crop 铺满整张卡。
                        // 不再用「窗口坐标采样 + Clamp」，长卡片不会因越界塌缩成纯色带/断裂。
                        val scale = max(
                            size.width / bmp.width.toFloat(),
                            size.height / bmp.height.toFloat(),
                        )
                        val dstW = bmp.width * scale
                        val dstH = bmp.height * scale
                        val dstLeft = ((size.width - dstW) / 2f).toInt()
                        val dstTop = ((size.height - dstH) / 2f).toInt()
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(dstLeft, dstTop),
                            dstSize = IntSize(dstW.toInt(), dstH.toInt()),
                        )
                    }
                    // 中性 tint 遮罩（surfaceContainerHigh）：深色不再纯黑，模糊封面透出后仍有玻璃厚度。
                    drawRect(
                        color = glassTint.copy(alpha = if (isDark) 0.45f else 0.35f),
                    )
                    // 顶部受光高光 + 底部微沉降（玻璃层次）
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.06f else 0.18f),
                                Color.Transparent,
                                Color.Black.copy(alpha = if (isDark) 0.04f else 0.01f),
                            ),
                        ),
                    )
                    // 圆角高光边框
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (isDark) 0.15f else 0.5f),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(borderCornerRadius, borderCornerRadius),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
    ) { content() }
}
