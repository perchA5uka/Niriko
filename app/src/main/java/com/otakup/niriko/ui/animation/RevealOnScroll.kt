package com.otakup.niriko.ui.animation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 滚动到视口才播放入场（Apple：空间感知，浏览到才动画）。
 *
 * 关键：内容始终在组合树（graphicsLayer 控制 alpha/scale/offset），检测节点
 * onGloballyPositioned 才会回调；不能用 AnimatedVisibility 包裹检测节点
 * （visible=false 时不组合子内容，检测永不触发）。LazyColumn 下离屏 item 销毁、
 * 滚回重组 → 每次进入视口都重新播放入场。
 *
 * @param staggerIndex 同屏多个 RevealOnScroll 的错峰序号（延迟 = index × 50ms）
 * @param delayMillis  额外延迟（配合 staggerIndex 叠加）
 * @param offsetY      入场上移距离（dp）
 * @param content      内容
 */
@Composable
fun RevealOnScroll(
    staggerIndex: Int = 0,
    delayMillis: Int = 0,
    offsetY: Float = 14f,
    content: @Composable () -> Unit,
) {
    // 减少动态效果时直接渲染，跳过一切入场动画
    if (!motionEnabled()) {
        content()
        return
    }
    // rememberSaveable：Lazy 项滚出销毁、滚回重组时不再重播入场动画（阶段 P：避免滚动时多动画争帧）
    var entered by rememberSaveable { mutableStateOf(false) }
    val viewportHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx() * 0.9f
    }
    val revealAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
        label = "revealAlpha",
    )
    val revealScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.97f,
        animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
        label = "revealScale",
    )
    val revealOffset by animateFloatAsState(
        targetValue = if (entered) 0f else offsetY,
        animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
        label = "revealOffset",
    )
    LaunchedEffect(staggerIndex, delayMillis) {
        if (delayMillis > 0) delay(delayMillis.toLong())
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                // 窗口坐标 y < 视口 90% 即认为已进入可视浏览区
                if (!entered && coords.positionInWindow().y < viewportHeightPx) {
                    entered = true
                }
            }
            .graphicsLayer {
                alpha = revealAlpha
                scaleX = revealScale
                scaleY = revealScale
                translationY = revealOffset
            },
    ) { content() }
}
