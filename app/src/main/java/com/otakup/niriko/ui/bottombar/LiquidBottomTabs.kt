package com.otakup.niriko.ui.bottombar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.otakup.niriko.ui.bottombar.utils.DampedDragAnimation
import com.otakup.niriko.ui.bottombar.utils.InteractiveHighlight
import com.otakup.niriko.ui.theme.LocalDarkTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    /** 点按**已选中**的 tab（F11）：切页之外的第二条语义，由导航层决定回顶/刷新。 */
    onTabReselected: (index: Int) -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !LocalDarkTheme.current
    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        // 手势期间的同步目标 tab：按下/长按/拖动都先更新它，再让动画跟随。
        // 不能用 DampedDragAnimation.targetValue 做选中依据：updateValue 是异步协程，
        // 快速点按/长按时读到的是旧值，会把按下跳转覆盖掉。
        val dragTarget = remember(selectedTabIndex) {
            mutableFloatStateOf(selectedTabIndex().toFloat())
        }
        val latestSelected by rememberUpdatedState(selectedTabIndex)
        val latestSelect by rememberUpdatedState(onTabSelected)
        val latestReselect by rememberUpdatedState(onTabReselected)
        val touchSlop = LocalViewConfiguration.current.touchSlop
        val contentMarginPx = with(density) { 4.dp.toPx() }
        val touchState = remember(tabWidth, tabsCount, isLtr) {
            DockTouchState(tabsCount, tabWidth, reverse = !isLtr)
        }
        val dampedDragAnimation = remember(animationScope, tabWidth, tabsCount, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { position ->
                    touchState.start(position.x - contentMarginPx)
                    dragTarget.floatValue = touchState.index.toFloat()
                    updateValue(dragTarget.floatValue)
                },
                onDragStopped = {
                    // Commit the hit cell, not the visual center or rounded drag offset.
                    val targetIndex = touchState.index
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    // 用户选择在这里直接上报 Pager，**不能**靠下面 currentIndex 的快照回流：
                    // Pager 滚动途中 currentPage 会依次经过中间页，那条回路会把中间页当成
                    // 用户选择而反过来打断滚动 —— 现象就是「作品库 → 设置」被「发现 / 统计」拦截。
                    // F11：当前在同一个 tab 上再次点按，不切页，改走「重选」通道
                    // （回顶 / 已在顶部则刷新）。切页仍只走 onTabSelected 一条路。
                    if (targetIndex != latestSelected()) {
                        latestSelect(targetIndex)
                    } else if (!touchState.hasDragged(touchSlop)) {
                        latestReselect(targetIndex)
                    }
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    if (dragAmount.x != 0f) {
                        touchState.move(dragAmount.x)
                        dragTarget.floatValue = touchState.indicatorValue
                        updateValue(dragTarget.floatValue)
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
                onDragCancelled = {
                    dragTarget.floatValue = latestSelected().toFloat()
                    animateToValue(dragTarget.floatValue)
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                }
            )
        }
        // 外部选中态（Pager 滑动 / tab 点击）单向流进指示器：只更新选中项与手势基准，
        // 绝不回调 onTabSelected（回调点唯一，见 onDragStopped）。
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { latestSelected() }
                .collectLatest { index ->
                    if (!dampedDragAnimation.gestureActive) {
                        currentIndex = index
                        dragTarget.floatValue = index.toFloat()
                        dampedDragAnimation.animateToValue(index.toFloat())
                    }
                }
        }

        val interactiveHighlight = remember(animationScope, tabWidth, isLtr) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50.dp) },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabInteractive provides false,
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50.dp) },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { RoundedCornerShape(50.dp) },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 8f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56f.dp)
                // Explicit width keeps the indicator step identical to the padded content step.
                // Fractional fill was measured after padding and made the first three indicators drift right.
                .width(with(density) { tabWidth.toDp() })
        )
        // This fixed input plane stays above both exported tabs and the moving glass.
        Box(
            Modifier.matchParentSize()
                .clearAndSetSemantics {}
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
        )

    }
}
