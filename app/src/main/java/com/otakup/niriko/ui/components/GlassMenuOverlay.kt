package com.otakup.niriko.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.theme.NirikoShapes
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Pure placement contract: all inputs are in the overlay host's coordinate space.
 *
 * @param alignRight true = 菜单右缘对齐锚点右缘（详情页分享 / 传送门）；false = 左缘对齐锚点左缘
 *   （左上分区 / 趋势选择器，视觉上像从按钮下方「长出来」）。
 */
internal fun glassMenuPosition(
    anchor: Rect,
    menu: IntSize,
    viewport: Rect,
    gap: Int,
    direction: LayoutDirection,
    alignRight: Boolean = true,
): IntOffset {
    val maxX = (viewport.right - menu.width).coerceAtLeast(viewport.left)
    val maxY = (viewport.bottom - menu.height).coerceAtLeast(viewport.top)
    val x = when {
        !alignRight -> anchor.left
        direction == LayoutDirection.Ltr -> anchor.right - menu.width
        else -> anchor.left
    }
    val below = anchor.bottom + gap
    val above = anchor.top - gap - menu.height
    val y = when {
        below + menu.height <= viewport.bottom -> below
        above >= viewport.top -> above
        else -> below.coerceIn(viewport.top, maxY)
    }
    return IntOffset(x.coerceIn(viewport.left, maxX).roundToInt(), y.coerceIn(viewport.top, maxY).roundToInt())
}

/**
 * 菜单进场缩放起点 0.92 / 退场终点 0.96（逐字等于 [NirikoMotionSpecs.overlayEnter] 的 scaleIn 起点
 * 与 [NirikoMotionSpecs.overlayExit] 的 scaleOut 终点）。
 *
 * 为什么不直接复用那两个 EnterTransition 工厂：它们只能挂在 AnimatedVisibility 上，而
 * AnimatedVisibility 在不可见首帧**不组合**内容 —— 菜单尺寸量不出来，
 * glassMenuTransformOrigin 也就没有 menuSize 可用，缩放会退回 Center 起播（锚定失效）。
 * 因此这里用同一档弹簧 + 同一组数值手写，起点从真实几何算。
 */
private const val MENU_ENTER_SCALE = 0.92f
private const val MENU_EXIT_SCALE = 0.96f

/**
 * 同窗口锚定浮层（B5）：**一次布局内先量后排**，玻璃采样因此与菜单最终位置一致。
 *
 * 为什么不能用 [androidx.compose.ui.window.Popup]：kyant backdrop 的采样靠
 * `LayoutCoordinates.positionInWindow()` 换算，而 Popup 是独立窗口 —— 弹窗内的坐标以弹窗自身为
 * 原点，采样就会从触发按钮的角点开始，与菜单实际位置错开。放进同一窗口就没有这个问题。
 *
 * 宿主尺寸由调用方给：详情页是整页 `fillMaxSize`；左上选择器用
 * `requiredSize(屏幕宽高) + 负 offset` 跳出 56dp 父约束。
 *
 * 布局契约：measure 阶段先量出菜单最终尺寸（`placeable.width/height`），再用 [glassMenuPosition]
 * 算出最终偏移并 `place` —— 一次布局完成，不需要第二轮 measure，也不需要把结果写回 state
 * （回写只用于调用方要显式矩形时，且必须在 `onGloballyPositioned` 里做，不能在 measure 里写）。
 */
@Composable
internal fun GlassMenuOverlay(
    anchorInRoot: Rect?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 4.dp,
    maxMenuWidth: Dp = 280.dp,
    maxMenuHeight: Dp? = null,
    alignRight: Boolean = true,
    safePadding: PaddingValues = PaddingValues(0.dp),
    columnModifier: Modifier = Modifier,
    /**
     * v1.2.2：菜单是否展开。
     *
     * false 时本组件**先播退场动画再卸载**（见 keepAlive），所以调用方不要在外面再写
     * `if (expanded) GlassMenuOverlay(...)` —— 那会在首帧就摘掉组合树，退场动画无从播放。
     * 默认 true 兼容仍手工控制生命周期的历史调用点。
     */
    expanded: Boolean = true,
    /**
     * 是否在展开时把焦点移进菜单、关闭时还给锚点。
     *
     * 旧 GlassAnchoredMenu 自带这套行为；V-14 迁移到 NirikoMenu 后由调用方按需开启
     * （详情页分享/传送门是键盘与读屏用户的高频入口，所以保持等价；纯触摸菜单可不开）。
     */
    requestMenuFocus: Boolean = false,
    anchorFocusRequester: FocusRequester? = null,
    restoreAnchorFocus: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (anchorInRoot == null) return
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val reduceMotion = LocalReduceMotion.current
    val specs = NirikoMotionSpecs
    var hostOrigin by remember { mutableStateOf<Offset?>(null) }
    // 延迟卸载（P0-2）：expanded=false 之后组合树要活到退场动画播完。
    // 改动前 GlassAnchoredMenu 直接 `if (!expanded) return`，菜单在关闭那一帧就被摘掉 —— 用户看到的是硬切。
    var keepAlive by remember { mutableStateOf(expanded) }
    // 缩放中心只能来自「量出来的菜单位置 + 尺寸」：glassMenuTransformOrigin 与 glassMenuPosition 用同一套
    // 宿主坐标。首帧还没有几何，而 alpha 初值是 0，所以「量几何的那一帧」用户看不到，
    // 也就不会出现从 Center 起播再跳到锚点的闪动。
    var menuOrigin by remember { mutableStateOf<TransformOrigin?>(null) }
    val menuScale = remember { Animatable(1f) }
    val menuAlpha = remember { Animatable(0f) }
    // 焦点归还：与旧 GlassAnchoredMenu 等价——菜单关闭后把焦点还给触发控件，
    // 否则键盘/读屏用户关掉菜单后焦点会丢到窗口根节点。
    val menuFocus = remember { FocusRequester() }
    var hadMenuFocus by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (!expanded && hadMenuFocus) {
            if (restoreAnchorFocus) anchorFocusRequester?.requestFocus()
            hadMenuFocus = false
        }
    }
    // 这两个 effect 必须声明在下面的提前 return 之前：菜单关掉后它们仍留在组合里，才能等到下一次展开。
    LaunchedEffect(expanded) { if (expanded) keepAlive = true }
    LaunchedEffect(expanded, menuOrigin, reduceMotion) {
        if (menuOrigin == null) return@LaunchedEffect
        if (reduceMotion) {
            // 减少动态效果：不等动画，瞬切并立刻卸载。
            menuScale.snapTo(if (expanded) 1f else MENU_EXIT_SCALE)
            menuAlpha.snapTo(if (expanded) 1f else 0f)
            if (!expanded) keepAlive = false
            return@LaunchedEffect
        }
        if (expanded && menuAlpha.value <= 0f) {
            // 从「完全关闭」进场才回到起点；若是在退场中途重新拉开，则从当前中间态继续，不回跳。
            menuScale.snapTo(MENU_ENTER_SCALE)
            menuAlpha.snapTo(0f)
        }
        // 形体（缩放）走 spatial 弹簧、透明度走 effects 弹簧：与 NirikoMotionSpecs.overlayEnter /
        // overlayExit 同一档同一组数值，只是把「起播前必须先拿到 menuSize」这件事自己安排。
        coroutineScope {
            launch {
                menuScale.animateTo(
                    targetValue = if (expanded) 1f else MENU_EXIT_SCALE,
                    animationSpec = if (expanded) specs.spatialDefault() else specs.spatialFast(),
                )
            }
            launch {
                menuAlpha.animateTo(
                    targetValue = if (expanded) 1f else 0f,
                    animationSpec = specs.effectsFast(),
                )
            }
        }
        if (!expanded) keepAlive = false
    }
    if (!keepAlive) return
    // BackHandler 只在菜单真的存在时才装：关死后不该继续拦截系统返回。
    BackHandler(onBack = onDismissRequest)
    val margin = with(density) { 8.dp.roundToPx() }
    val gapPx = with(density) { gap.roundToPx() }
    val maxWidthPx = with(density) { maxMenuWidth.roundToPx() }
    val maxContentHeightPx = maxMenuHeight?.let { with(density) { it.roundToPx() } }
    val left = with(density) { safePadding.calculateLeftPadding(direction).roundToPx() } + margin
    val right = with(density) { safePadding.calculateRightPadding(direction).roundToPx() } + margin
    val top = with(density) { safePadding.calculateTopPadding().roundToPx() } + margin
    val bottom = with(density) { safePadding.calculateBottomPadding().roundToPx() } + margin
    Layout(
        modifier = modifier
            .onGloballyPositioned { hostOrigin = it.positionInRoot() }
            .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
            // 旧 GlassAnchoredMenu 支持 Esc 关闭（外接键盘 / 桌面模式）；迁到统一浮层后仍保留。
            .onPreviewKeyEvent { event ->
                if (requestMenuFocus && event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                    onDismissRequest()
                    true
                } else {
                    false
                }
            },
        content = {
            if (hostOrigin != null) {
                Column(
                    // 菜单面吃掉空白点击，不让它冒泡到外层 dismiss 层。
                    modifier = Modifier
                        .pointerInput(Unit) { detectTapGestures {} }
                        // 量到真实几何才算缩放中心（只在值变化时写 state，绝不在 measure 里写 state）。
                        .onGloballyPositioned { coordinates ->
                            val origin = hostOrigin ?: return@onGloballyPositioned
                            val position = coordinates.positionInParent()
                            val next = glassMenuTransformOrigin(
                                anchorInRoot = anchorInRoot,
                                hostOrigin = origin,
                                menuPosition = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
                                menuSize = coordinates.size,
                            )
                            if (next != menuOrigin) menuOrigin = next
                        }
                        // alpha / scale 只在绘制期读：菜单内容不会因为动画每帧重组。
                        .graphicsLayer {
                            val origin = menuOrigin
                            if (origin != null) {
                                transformOrigin = origin
                                scaleX = menuScale.value
                                scaleY = menuScale.value
                            }
                            alpha = menuAlpha.value
                        }
                        .focusRequester(menuFocus)
                        .then(columnModifier),
                    content = {
                        // 只有真正放到最终位置后（hostOrigin 已就绪）才请求焦点：过早调用时 requester 还没 attach。
                        LaunchedEffect(Unit) {
                            if (requestMenuFocus) {
                                menuFocus.requestFocus()
                                hadMenuFocus = true
                            }
                        }
                        content()
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val viewport = Rect(
            left.toFloat(),
            top.toFloat(),
            (width - right).coerceAtLeast(left).toFloat(),
            (height - bottom).coerceAtLeast(top).toFloat(),
        )
        val placeable = measurables.firstOrNull()?.measure(
            Constraints(
                maxWidth = maxWidthPx.coerceAtMost(viewport.width.toInt()),
                maxHeight = (maxContentHeightPx ?: viewport.height.toInt())
                    .coerceAtMost(viewport.height.toInt()),
            ),
        )
        val origin = hostOrigin
        val position = if (placeable != null && origin != null) {
            glassMenuPosition(
                anchor = anchorInRoot.translate(-origin),
                menu = IntSize(placeable.width, placeable.height),
                viewport = viewport,
                gap = gapPx,
                direction = direction,
                alignRight = alignRight,
            )
        } else {
            IntOffset.Zero
        }
        // 菜单出现的第一帧还没有 hostOrigin（布局回调之前）：不画内容，避免先在 (0,0) 闪一下。
        layout(width, height) {
            if (placeable != null && origin != null) placeable.place(position.x, position.y)
        }
    }
}
