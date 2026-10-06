package com.otakup.niriko.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.motionEnabled
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import kotlinx.coroutines.launch

// ==================== 非模态底部面板（R6：skydoves/FlexibleBottomSheet 形态，自研实现） ====================

/** 关闭阈值：向下拖动超过该距离（dp）即关闭。 */
internal const val SHEET_DISMISS_DISTANCE_DP = 72f

/** 关闭阈值：拖动速度超过该值（dp/s）即关闭（快速轻甩也能关）。 */
internal const val SHEET_DISMISS_VELOCITY_DP = 900f

/**
 * 是否应该关闭面板：位移或速度任一超过阈值即关闭。
 *
 * 纯函数（不读 Compose 状态），便于单测覆盖手势判定。
 */
internal fun shouldDismissSheet(
    dragDistancePx: Float,
    dragVelocityPx: Float,
    distanceThresholdPx: Float,
    velocityThresholdPx: Float,
): Boolean = dragDistancePx > distanceThresholdPx || dragVelocityPx > velocityThresholdPx

/**
 * 非模态底部面板（可交互背景）。
 *
 * 与 [androidx.compose.material3.ModalBottomSheet] 的区别：**没有 scrim 遮罩**，
 * 面板之外的区域保持可点击 / 可滚动 —— 用户能一边看面板内容一边扫背景列表。
 * 代价是没有「点外部关闭」：关闭靠拖把手下滑或宿主返回键（调用方自行接 BackHandler）。
 *
 * 手势分工（验收项「手势不打架」）：只有顶部 28dp 的把手吃竖向拖动手势，
 * 面板内容（LazyColumn / 列表）的滚动不受影响，背景滚动也不受影响。
 *
 * @param heightFraction 面板高度占可用高度的比例；默认 0.62（半屏多一点，背景上半部仍可见）。
 */
@Composable
fun NirikoNonModalSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    heightFraction: Float = 0.62f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val animate = motionEnabled()
    val specs = NirikoMotionSpecs
    val scope = rememberCoroutineScope()
    // entry：入场位移系数（1 = 完全在屏外 → 0 = 就位）；drag：手指拖动的实时位移（px，向下为正）
    val entry = remember { Animatable(1f) }
    val drag = remember { Animatable(0f) }
    var panelHeightPx by remember { mutableFloatStateOf(0f) }
    val distanceThresholdPx = with(density) { SHEET_DISMISS_DISTANCE_DP.dp.toPx() }
    val velocityThresholdPx = with(density) { SHEET_DISMISS_VELOCITY_DP.dp.toPx() }
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val backPreview = remember { com.otakup.niriko.ui.animation.BackPreviewState() }
    com.otakup.niriko.ui.animation.PredictiveDismissHandler(
        enabled = true,
        preview = backPreview,
    ) {
        if (animate) {
            entry.snapTo(backPreview.progress.coerceAtLeast(entry.value))
            backPreview.reset()
            entry.animateTo(1f, specs.spatialFast())
        }
        onDismiss()
    }

    LaunchedEffect(Unit) {
        if (animate) {
            entry.animateTo(0f, specs.spatialDefault())
        } else {
            entry.snapTo(0f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .graphicsLayer {
                    val preview = if (animate) backPreview.progress else 0f
                    translationY = panelHeightPx * entry.value + drag.value + panelHeightPx * preview
                }
                .onSizeChanged { panelHeightPx = it.height.toFloat() }
                .clip(shape)
                .appleGlassCard(shape = shape)
                // 面板自身吃掉落在它上面的点击：背景元素（日历格等）不会被误触
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            // 拖拽把手：只有这里吃竖向拖动 → 与面板内容滚动 / 背景滚动互不抢手势
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                        },
                        onDragStopped = { velocity ->
                            scope.launch {
                                if (shouldDismissSheet(drag.value, velocity, distanceThresholdPx, velocityThresholdPx)) {
                                    val flyOut = panelHeightPx.coerceAtLeast(1f) + distanceThresholdPx
                                    drag.animateTo(flyOut, specs.spatialFast())
                                    onDismiss()
                                } else {
                                    drag.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)),
                )
            }
            content()
        }
    }
}
