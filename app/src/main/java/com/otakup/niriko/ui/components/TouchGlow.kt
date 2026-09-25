package com.otakup.niriko.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 液态玻璃“触碰反馈”：触点白晕 + 整卡按压缩放（1→0.97，spring）。
 * 不做小范围拖拽平移（旧版会在长按松手时抽搐），只读事件不消费 → 不影响点击/长按/滚动。
 */
@Composable
fun Modifier.touchGlow(): Modifier {
    val position = remember { mutableStateOf(Offset.Unspecified) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val springSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)

    return this
        .graphicsLayer {
            val p = progress.value
            scaleX = 1f - 0.03f * p
            scaleY = 1f - 0.03f * p
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                position.value = down.position
                scope.launch { progress.animateTo(1f, springSpec) }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null) position.value = change.position
                    if (event.changes.none { it.pressed }) break
                }
                scope.launch { progress.animateTo(0f, springSpec) }
            }
        }
        .drawWithContent {
            drawContent()
            val p = progress.value
            if (p > 0f && position.value != Offset.Unspecified) {
                val pos = position.value
                val radius = size.minDimension * 1.2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.24f * p),
                            Color.White.copy(alpha = 0f),
                        ),
                        center = pos,
                        radius = radius,
                    ),
                    radius = radius,
                    center = pos,
                )
            }
        }
}
