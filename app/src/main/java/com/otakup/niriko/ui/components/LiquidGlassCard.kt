package com.otakup.niriko.ui.components

// 卡片级"液态玻璃"真折射（背垫"封面墙"）。
//
// 与 LiquidGlassSurface 同管线，差异只在默认参数与主题策略：
//   ├── 默认 config = LiquidGlassConfig.Card（小圆角 20dp、更克制的折射/色散/模糊，
//   │   卡片尺寸小，过强会糊）
//   ├── 浅色模式：背垫 backdrop（调用方给"封面墙"：封面放大铺底 + 弱化蒙层），
//   │   API 33+ 由 AGSL 对封面墙做边缘折射 + 7 色彩带色散；31-32 高斯模糊；26-30 tint
//   └── 深色模式：回退 M3 surfaceContainer 普通卡片（与 appleGlassCard 策略一致，
//       取消玻璃层次，保证可读性）

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.liquidglass.LiquidGlassConfig

/**
 * 卡片级液态玻璃（真折射，背垫"封面墙"）。
 *
 * 调用方通过 [backdrop] 提供卡片背后的内容源（如封面墙：封面放大铺底 + 弱化蒙层），
 * AGSL 渲染链会对其边缘做折射/色散（API 33+），内容层保持锐利。
 * 深色模式同样保留液态玻璃（tint 用 dark），保证材质一致。
 *
 * @param backdrop 背后内容源
 * @param shape    卡片形状（折射 SDF 圆角与剪裁圆角对齐）
 * @param config   液态玻璃渲染参数（默认卡片调校 LiquidGlassConfig.Card）
 */
@Composable
fun LiquidGlassCard(
    backdrop: @Composable (Modifier) -> Unit,
    shape: Shape = RoundedCornerShape(20.dp),
    modifier: Modifier = Modifier,
    tint: Color? = null,
    config: LiquidGlassConfig = LiquidGlassConfig.Card,
    content: @Composable () -> Unit,
) {
    LiquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        modifier = modifier,
        tint = tint,
        config = config,
        content = content,
    )
}