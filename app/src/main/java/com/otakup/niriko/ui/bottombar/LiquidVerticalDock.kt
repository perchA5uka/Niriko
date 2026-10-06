package com.otakup.niriko.ui.bottombar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.otakup.niriko.ui.components.liquidglass.LiquidGlassConfig
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.NirikoShapes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt

/**
 * 竖向悬浮玻璃 dock（计划书 §三 B2 / 5a）：[LiquidBottomTabs] 的 90° 旋转版本。
 *
 * 逐条镜像横向版：Row→Column、height↔width、tabWidth→tabHeight、position.x→position.y、
 * translationX→translationY、InteractiveHighlight 的 size.height/2f→size.width/2f。
 * 玻璃链（vibrancy + blur + lens + 真折射 backdrop + 选中片色散）与跟手动画整体沿用，
 * **不改 shader**：色散 dispersionIntensity = chromaticAberration * (centeredCoord.x*centeredCoord.y)
 * /(halfSize.x*halfSize.y) 是四叶反对称，两条中轴恒为 0，故竖排彩虹自然落在短边（上下），
 * 长边（左右）不会出现彩虹描边。
 *
 * 折射来源是 MainActivity 里捕获全窗口的 kyant backdrop（`rememberLayerBackdrop { drawRect(surface); drawContent() }`），
 * 竖向 dock 不需要新增捕获层。[LiquidBottomTabs] 本身不改动（手机端 <600dp 必须逐像素保持现状）。
 */
@Composable
fun LiquidVerticalDock(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    /** 点按**已选中**的 tab（F11）：切页之外的第二条语义，由导航层决定回顶/刷新。 */
    onTabReselected: (index: Int) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = !LocalDarkTheme.current
    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    // 圆角取 B5 定稿的 LiquidGlassConfig.VerticalDock.cornerRadius（26dp，按 72dp 窄边定），
    // 形状 API 用 NirikoShapes（B5 为「B2 竖向 dock」预留了 squircle；但 kyant drawBackdrop 对
    // Outline.Generic 的处理无法离线验证，首轮先用同族的线性圆角，B6 截图定稿时可一行切换）。
    val dockShape = NirikoShapes.rounded(LiquidGlassConfig.VerticalDock.cornerRadius)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val density = LocalDensity.current
        val tabHeight = with(density) { (constraints.maxHeight.toFloat() - 8f.dp.toPx()) / tabsCount }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxHeight) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxHeight).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) { mutableIntStateOf(selectedTabIndex()) }
        val dragTarget = remember(selectedTabIndex) { mutableFloatStateOf(selectedTabIndex().toFloat()) }
        val latestSelected by rememberUpdatedState(selectedTabIndex)
        val latestSelect by rememberUpdatedState(onTabSelected)
        val latestReselect by rememberUpdatedState(onTabReselected)
        val touchSlop = LocalViewConfiguration.current.touchSlop
        val contentMarginPx = with(density) { 4.dp.toPx() }
        val touchState = remember(tabHeight, tabsCount) {
            DockTouchState(tabsCount, tabHeight)
        }
        val dampedDragAnimation = remember(animationScope, tabHeight, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { position ->
                    touchState.start(position.y - contentMarginPx)
                    dragTarget.floatValue = touchState.index.toFloat()
                    updateValue(dragTarget.floatValue)
                },
                onDragStopped = {
                    val targetIndex = touchState.index
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    // 用户选择在这里直接上报 Pager，**不能**靠下面 currentIndex 的快照回流：
                    // Pager 滚动途中 currentPage 会依次经过中间页，那条回路会把中间页当成
                    // 用户选择而反过来打断滚动（与横向胶囊底栏同一个坑）。
                    // F11：与横向胶囊底栏同一语义 —— 同一 tab 上再次点按走「重选」通道。
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
                    if (dragAmount.y != 0f) {
                        touchState.move(dragAmount.y)
                        dragTarget.floatValue = touchState.indicatorValue
                        updateValue(dragTarget.floatValue)
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.y)
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
        // 外部选中态单向流进指示器：只更新选中项与手势基准，绝不回调 onTabSelected
        //（回调点唯一，见 onDragStopped）。
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

        val interactiveHighlight = remember(animationScope, tabHeight) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        size.width / 2f,
                        (dampedDragAnimation.value + 0.5f) * tabHeight + panelOffset
                    )
                }
            )
        }

        // 第 1 层：玻璃面（真实折射壁纸：vibrancy + blur + lens）
        Column(
            Modifier
                .graphicsLayer { translationY = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { dockShape },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.height, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .width(64f.dp)
                .fillMaxHeight()
                .padding(4f.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
        // 第 2 层：给选中片做折射的 tabs 层（同一个 content 渲染第二遍，alpha 0）
        CompositionLocalProvider(LocalLiquidBottomTabInteractive provides false, LocalLiquidBottomTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }) {
        Column(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationY = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { dockShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .width(56f.dp)
                .fillMaxHeight()
                .padding(vertical = 4f.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
        }
        // 第 3 层：跟手选中的玻璃片（按下时色散 + 高光 + 阴影）
        Box(
            Modifier
                .padding(vertical = 4f.dp)
                .graphicsLayer {
                    translationY = dampedDragAnimation.value * tabHeight + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { dockShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                    },
                    shadow = {
                        Shadow(alpha = dampedDragAnimation.pressProgress)
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 8f.dp * dampedDragAnimation.pressProgress,
                            alpha = dampedDragAnimation.pressProgress
                        )
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
                .width(56f.dp)
                .height(with(density) { tabHeight.toDp() })
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
