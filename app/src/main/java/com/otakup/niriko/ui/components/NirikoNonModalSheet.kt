package com.otakup.niriko.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.animation.appearSpring
import com.otakup.niriko.ui.animation.motionEnabled
import com.otakup.niriko.ui.theme.NirikoShapes

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
    // entry：入场位移系数（1 = 完全在屏外 → 0 = 就位）
    val entry = remember { Animatable(1f) }
    // 跟手位移用普通状态，不再用 Animatable.snapTo + 每个 delta 起一个协程：
    // 手指期间只是一个 Float 读写（同帧生效、零延迟），松手才交给下面的 settle 做物理动画。
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // settle：松手后的收尾位移（px）。与手指位移分开持有，才能把抬起速度作为 initialVelocity 传入 ——
    // 动画从手指离开的位置和速度继续，不会出现「先顿一下再从 0 起跑」的断裂（P1-3）。
    val settle = remember { Animatable(0f) }
    var panelHeightPx by remember { mutableFloatStateOf(0f) }
    val distanceThresholdPx = with(density) { SHEET_DISMISS_DISTANCE_DP.dp.toPx() }
    val velocityThresholdPx = with(density) { SHEET_DISMISS_VELOCITY_DP.dp.toPx() }
    // 手感线索：拖动真正开始时震一次（TextHandleMove = 系统定义的「抓住手柄」语义）。
    // 触感不是动效，因此不受 reduceMotion 影响。
    val haptic = LocalHapticFeedback.current
    var hapticFired by remember { mutableStateOf(false) }
    // B7e：弹层顶角收敛到 Sheet（24dp），与设置页卡片（Card/RowInner）同一套形状 token
    val shape = RoundedCornerShape(topStart = NirikoShapes.Sheet, topEnd = NirikoShapes.Sheet)
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
                    // 三段位移同层相加：入场系数、手指跟手、松手后的物理收尾。
                    // 都在绘制期读，跟手不会每帧重组面板内容。
                    translationY = panelHeightPx * entry.value + dragOffset + settle.value + panelHeightPx * preview
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
                            // 同步写状态：同一次 pointer 事件里就生效，零协程调度开销（原来每 delta 起协程，
                            // 高刷屏上一次拖动会积压几十个 snapTo，手指和面板之间有肉眼可见的滞后）。
                            dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                        },
                        onDragStarted = {
                            if (!hapticFired) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                hapticFired = true
                            }
                            // 上一次回弹还没停就再次按住：把残余位移并进手指位移，避免画面跳一下。
                            if (settle.isRunning) {
                                dragOffset += settle.value
                                settle.snapTo(0f)
                            }
                        },
                        onDragStopped = { velocity ->
                            hapticFired = false
                            val dismiss = shouldDismissSheet(
                                dragDistancePx = dragOffset,
                                dragVelocityPx = velocity,
                                distanceThresholdPx = distanceThresholdPx,
                                velocityThresholdPx = velocityThresholdPx,
                            )
                            // 交棒：手指位移原样搬进 settle，再把 dragOffset 归零 —— 同一帧完成，视觉位置不变。
                            val released = dragOffset
                            dragOffset = 0f
                            settle.snapTo(released)
                            if (!animate) {
                                // 减少动态效果：不做回弹/甩出，直接落到终点。
                                settle.snapTo(if (dismiss) panelHeightPx.coerceAtLeast(1f) + distanceThresholdPx else 0f)
                                if (dismiss) onDismiss()
                            } else if (dismiss) {
                                settle.animateTo(
                                    targetValue = panelHeightPx.coerceAtLeast(1f) + distanceThresholdPx,
                                    // 初速沿用手指抬起速度：px/s，与 Animatable 的位移单位同为 px，
                                    // 不做 density 换算（除 density 会把甩动速度改错一个密度倍数）。
                                    animationSpec = specs.spatialFast(),
                                    initialVelocity = velocity,
                                )
                                onDismiss()
                            } else {
                                // 回弹：appearSpring 是 v1.2.2 仅存的「旧规格」，语义就是「面板落回原位」。
                                settle.animateTo(
                                    targetValue = 0f,
                                    animationSpec = appearSpring(),
                                    initialVelocity = velocity,
                                )
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
