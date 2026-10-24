package com.otakup.niriko.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlin.math.roundToInt

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
    content: @Composable ColumnScope.() -> Unit,
) {
    if (anchorInRoot == null) return
    BackHandler(onBack = onDismissRequest)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var hostOrigin by remember { mutableStateOf<Offset?>(null) }
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
            .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } },
        content = {
            if (hostOrigin != null) {
                Column(
                    // 菜单面吃掉空白点击，不让它冒泡到外层 dismiss 层。
                    modifier = Modifier
                        .pointerInput(Unit) { detectTapGestures {} }
                        .then(columnModifier),
                    content = content,
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

/** Same-page overlay, placed as a sibling AFTER the sampled page, never inside its capture. */
@Composable
fun GlassAnchoredMenu(
    expanded: Boolean,
    anchorInRoot: Rect?,
    onDismissRequest: () -> Unit,
    anchorFocusRequester: FocusRequester,
    title: String,
    source: Backdrop?,
    safePadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
    restoreAnchorFocus: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val menuFocus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (!expanded && hadFocus) {
            if (restoreAnchorFocus) anchorFocusRequester.requestFocus()
            hadFocus = false
        }
    }
    if (!expanded || anchorInRoot == null) return
    GlassMenuOverlay(
        anchorInRoot = anchorInRoot,
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxSize().zIndex(10f),
        gap = 4.dp,
        maxMenuWidth = 280.dp,
        alignRight = true,
        safePadding = safePadding,
        columnModifier = Modifier
            .focusRequester(menuFocus)
            .onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                    onDismissRequest()
                    true
                } else false
            }
            .focusGroup()
            .semantics { paneTitle = title; isTraversalGroup = true }
            .searchGlassSurface(RoundedCornerShape(24.dp), source)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        // 只有真正放到最终位置后（hostOrigin 已就绪）才请求焦点：过早调用时 FocusRequester 还没 attach。
        LaunchedEffect(Unit) {
            menuFocus.requestFocus()
            hadFocus = true
        }
        content()
    }
}
