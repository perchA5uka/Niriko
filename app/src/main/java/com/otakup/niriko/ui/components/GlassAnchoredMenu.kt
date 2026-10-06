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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/** Pure placement contract: all inputs are in the overlay host's coordinate space. */
internal fun glassMenuPosition(
    anchor: Rect,
    menu: IntSize,
    viewport: Rect,
    gap: Int,
    direction: LayoutDirection,
): IntOffset {
    val maxX = (viewport.right - menu.width).coerceAtLeast(viewport.left)
    val maxY = (viewport.bottom - menu.height).coerceAtLeast(viewport.top)
    val x = if (direction == LayoutDirection.Ltr) anchor.right - menu.width else anchor.left
    val below = anchor.bottom + gap
    val above = anchor.top - gap - menu.height
    val y = when {
        below + menu.height <= viewport.bottom -> below
        above >= viewport.top -> above
        else -> below.coerceIn(viewport.top, maxY)
    }
    return IntOffset(x.coerceIn(viewport.left, maxX).roundToInt(), y.coerceIn(viewport.top, maxY).roundToInt())
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
    BackHandler(onBack = onDismissRequest)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var hostOrigin by remember { mutableStateOf<Offset?>(null) }
    val margin = with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 4.dp.roundToPx() }
    val maxWidth = with(density) { 280.dp.roundToPx() }
    val left = with(density) { safePadding.calculateLeftPadding(direction).roundToPx() } + margin
    val right = with(density) { safePadding.calculateRightPadding(direction).roundToPx() } + margin
    val top = with(density) { safePadding.calculateTopPadding().roundToPx() } + margin
    val bottom = with(density) { safePadding.calculateBottomPadding().roundToPx() } + margin
    Layout(
        modifier = modifier.fillMaxSize().zIndex(10f)
            .onGloballyPositioned { hostOrigin = it.positionInRoot() }
            .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } },
        content = {
            if (hostOrigin != null) {
                LaunchedEffect(Unit) {
                    menuFocus.requestFocus()
                    hadFocus = true
                }
                Column(
                    modifier = Modifier
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
                        // The menu surface consumes blank-area taps, without forwarding to the page.
                        .pointerInput(Unit) { detectTapGestures {} }
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    content = content,
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val viewport = Rect(left.toFloat(), top.toFloat(), (width - right).coerceAtLeast(left).toFloat(),
            (height - bottom).coerceAtLeast(top).toFloat())
        val placeable = measurables.firstOrNull()?.measure(Constraints(
            maxWidth = maxWidth.coerceAtMost(viewport.width.toInt()),
            maxHeight = viewport.height.toInt(),
        ))
        val origin = hostOrigin
        val position = if (placeable != null && origin != null) glassMenuPosition(
            anchorInRoot.translate(-origin), IntSize(placeable.width, placeable.height), viewport, gap, direction,
        ) else IntOffset.Zero
        layout(width, height) { placeable?.place(position.x, position.y) }
    }
}
