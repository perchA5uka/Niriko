package com.otakup.niriko.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.components.GlassMenuOverlay
import com.otakup.niriko.ui.components.searchGlassSurface
import kotlin.math.roundToInt

data class TopSelectorOption(
    val key: String,
    val label: String,
    val onSelect: () -> Unit,
)

/** Current item first; all other entries keep their original order. */
fun orderedTopSelectorOptions(options: List<TopSelectorOption>, selectedKey: String): List<TopSelectorOption> =
    options.filter { it.key == selectedKey } + options.filter { it.key != selectedKey }

/**
 * 左上分区 / 趋势选择器（作品库与发现页共用）。
 *
 * 菜单是**同窗口**浮层，不再是 Popup（B5）：Popup 属于独立窗口，kyant 的玻璃采样以弹窗自身
 * 为坐标原点，于是背景从按钮角点开始、与菜单实际位置错开。现在复用 GlassMenuOverlay 的
 * 「一次布局先量后排」，菜单画在同一窗口里，采样与最终位置一致。
 *
 * 为了跳出外层 56dp 的顶栏 Box，浮层用 requiredSize(屏幕宽高) + 负 offset 铺满窗口并把原点
 * 对齐到窗口左上角；这也是它能接管「点空白关闭」的前提。
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun VerticalTopSelector(
    selectedKey: String,
    options: List<TopSelectorOption>,
    modifier: Modifier = Modifier,
    actions: List<TopSelectorOption> = emptyList(),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var buttonRect by remember { mutableStateOf<Rect?>(null) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    val currentLabel = options.firstOrNull { it.key == selectedKey }?.label ?: options.firstOrNull()?.label.orEmpty()
    val textStyle = MaterialTheme.typography.titleSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val screenWidth = with(LocalConfiguration.current) { screenWidthDp.dp }
    val screenHeight = with(LocalConfiguration.current) { screenHeightDp.dp }
    BoxWithConstraints(
        modifier
            .height(56.dp)
            .onGloballyPositioned { boxOrigin = it.positionInRoot() },
    ) {
        val widthLimit = maxWidth.coerceAtLeast(56.dp)
        val transition = updateTransition(currentLabel, label = "topSelector")
        val width by transition.animateDp(
            transitionSpec = { if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 400f) },
            label = "labelWidth",
        ) { label ->
            (with(density) { measurer.measure(AnnotatedString(label), textStyle).size.width.toDp() } + 52.dp)
                .coerceIn(56.dp, widthLimit)
        }
        Row(
            Modifier.width(width).height(56.dp)
                .onGloballyPositioned { buttonRect = it.boundsInRoot() }
                .searchGlassSurface(RoundedCornerShape(28.dp))
                .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            transition.Crossfade(modifier = Modifier.weight(1f)) { label ->
                Text(label, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val triangleColor = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(10.dp, 9.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height); close()
                }
                drawPath(path, triangleColor)
            }
        }
        if (expanded) {
            val menuWidth = widthLimit.coerceAtMost(280.dp)
            GlassMenuOverlay(
                anchorInRoot = buttonRect,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    // requiredSize 让浮层无视 56dp 的父约束；负 offset 把它移到窗口左上角，
                    // 于是浮层内的坐标就是根坐标（采样与触摸都按窗口算）。
                    .requiredSize(screenWidth, screenHeight)
                    .offset { IntOffset(-boxOrigin.x.roundToInt(), -boxOrigin.y.roundToInt()) }
                    .zIndex(20f),
                gap = 4.dp,
                maxMenuWidth = menuWidth,
                maxMenuHeight = 360.dp,
                alignRight = false,
                safePadding = WindowInsets.safeDrawing.asPaddingValues(),
                columnModifier = Modifier
                    .width(menuWidth)
                    .searchGlassSurface(RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            ) {
                orderedTopSelectorOptions(options, selectedKey).forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.RadioButton) { expanded = false; option.onSelect() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (option.key == selectedKey) Icon(Icons.Default.Check, "已选中", Modifier.size(18.dp))
                    }
                }
                if (actions.isNotEmpty()) {
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    actions.forEach { action ->
                        Text(action.label, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { expanded = false; action.onSelect() }
                            .padding(horizontal = 16.dp, vertical = 14.dp))
                    }
                }
            }
        }
    }
}
