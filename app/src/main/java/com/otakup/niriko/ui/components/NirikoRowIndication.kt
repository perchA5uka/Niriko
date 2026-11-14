package com.otakup.niriko.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.otakup.niriko.ui.animation.NirikoPressSpecs
import com.otakup.niriko.ui.animation.motionEnabled
import kotlinx.coroutines.flow.collectLatest

/**
 * 设置行 / 列表行的按压反馈（v1.2.2 M1）：整行圆角实心覆盖，替代 M3 矩形水波。
 *
 * 为什么不用 `ripple()`：M3 水波是**以触摸点为圆心的圆形**扩散，落在 56dp 高的设置行里
 * 会溢出到相邻行（组内行间距只有 4dp），压在两行之间时看起来像"点错了行"；
 * 而且水波的深色圆斑和玻璃卡的浅色底反差很大。改造后是一块随压力渐入的整行浅色覆盖，
 * 与"按下这一行"的语义一一对应，也和 MiuiX / HyperLight 设置页的手感一致。
 *
 * 动画值只在 draw 阶段读（[drawWithContent] 闭包里读 `progress.value`）——
 * 若放进 composition 读，每帧都会重组整行；draw 阶段读只重绘，8% alpha 的覆盖层看不出差别，
 * 但 34 个开关行的滚动开销差一个数量级。
 *
 * @param interactionSource 必须与 `clickable` / `selectable` 共用同一个实例，
 *   否则按压事件到不了这里（行内圆角 morph 也依赖同一个实例）。
 * @param cornerRadius 覆盖层的圆角：传行自身的圆角令牌（组内行为 [com.otakup.niriko.ui.theme.NirikoShapes.RowInner]）。
 * @param enabled 行不可点时（禁用态）不画覆盖层，也不收集交互。
 */
@Composable
fun Modifier.nirikoRowIndication(
    interactionSource: MutableInteractionSource,
    cornerRadius: Dp,
    enabled: Boolean = true,
): Modifier {
    val overlayColor = MaterialTheme.colorScheme.onSurface
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val progress = remember { Animatable(0f) }
    val motion = motionEnabled()

    LaunchedEffect(interactionSource, enabled, motion) {
        if (!enabled) {
            // 行中途被禁用（如开始加载）：把残留的按压进度收回去，避免停在半亮状态。
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> progress.animateTo(1f, pressSpec(motion, entering = true))
                is PressInteraction.Release -> progress.animateTo(0f, pressSpec(motion, entering = false))
                is PressInteraction.Cancel -> progress.animateTo(0f, pressSpec(motion, entering = false))
            }
        }
    }

    return drawWithContent {
        drawContent()
        val alpha = progress.value
        if (enabled && alpha > 0f) {
            drawRoundRect(
                color = overlayColor.copy(alpha = alpha * NirikoPressSpecs.ROW_PRESS_ALPHA),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
            )
        }
    }
}

/** 按压进／出规格：进快出慢（见 [NirikoPressSpecs.pressIn]）；减少动态效果时直接跳变。 */
private fun pressSpec(motion: Boolean, entering: Boolean): AnimationSpec<Float> = when {
    !motion -> snap()
    entering -> NirikoPressSpecs.pressIn()
    else -> NirikoPressSpecs.pressOut()
}
