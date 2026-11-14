package com.otakup.niriko.ui.common
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.components.NirikoMenu
import com.otakup.niriko.ui.components.NirikoMenuItem
import com.otakup.niriko.ui.components.searchGlassSurface
import com.otakup.niriko.ui.theme.NirikoShapes
import kotlin.math.roundToInt

data class TopSelectorOption(
    val key: String,
    val label: String,
    val onSelect: () -> Unit,
)

/** Current item first; all other entries keep their original order. */
fun orderedTopSelectorOptions(options: List<TopSelectorOption>, selectedKey: String): List<TopSelectorOption> = options.filter { it.key == selectedKey } + options.filter { it.key != selectedKey }

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
            // A.8：原 spring(0.8, 400) 字面量 → spatialDefault（0.9/700）。胶囊宽度是形体变化（spatial）。
            transitionSpec = { if (reduceMotion) snap() else NirikoMotionSpecs.spatialDefault<Dp>() },
            label = "labelWidth",
        ) { label ->
            (with(density) { measurer.measure(AnnotatedString(label), textStyle).size.width.toDp() } + 52.dp)
                .coerceIn(56.dp, widthLimit)
        }
        Row(
            Modifier.width(width).height(56.dp)
                .onGloballyPositioned { buttonRect = it.boundsInRoot() }
                // V-14：触发按钮与菜单面统一到 Sheet 档（24dp）。改造前按钮 28dp / 菜单 24dp，
                // 是「六档圆角」之外的两档手写值；V-14 收口要求全站菜单与触发器同一档。
                .searchGlassSurface(NirikoShapes.SheetShape)
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
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, triangleColor)
            }
        }
        // V-14：自绘菜单退役。改造前这里直接调 GlassMenuOverlay 并自己画 Row 选项 —— 它是
        // LibraryTopBar（NirikoMenu）之外的「第二套写手」，行高/内距/选中勾都得各维护一份。
        // 现在统一走 NirikoMenu + NirikoMenuItem：24dp 玻璃面、从锚点缩放、带回退场（keepAlive）。
        // 不在外面写 if (expanded)：退场动画需要组合树活到播完，生命周期由组件内部 keepAlive 管。
        val menuWidth = widthLimit.coerceAtMost(280.dp)
        NirikoMenu(
            expanded = expanded,
            anchorInRoot = buttonRect,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                // requiredSize 让浮层无视 56dp 的父约束；负 offset 把它移到窗口左上角，
                // 于是浮层内的坐标就是根坐标（采样与触摸都按窗口算）。
                // zIndex(20f) 必须比菜单默认的 10f 大：本选择器在组合顺序上早于右侧搜索卡，
                // 同 zIndex 会被搜索卡盖住（见 NirikoMenu 里 modifier 追加顺序的说明）。
                .requiredSize(screenWidth, screenHeight)
                .offset { IntOffset(-boxOrigin.x.roundToInt(), -boxOrigin.y.roundToInt()) }
                .zIndex(20f),
            alignRight = false,
            maxMenuHeight = 360.dp,
            safePadding = WindowInsets.safeDrawing.asPaddingValues(),
            // 宽度沿用「与触发胶囊等宽」的既有观感；长列表在宿主内滚动而不是撑破屏幕。
            columnModifier = Modifier
                .width(menuWidth)
                .verticalScroll(rememberScrollState()),
        ) {
            orderedTopSelectorOptions(options, selectedKey).forEach { option ->
                NirikoMenuItem(
                    text = option.label,
                    onClick = {
                        expanded = false
                        option.onSelect()
                    },
                    // 「已选中」用前置勾（NirikoMenuItem 只有行首 icon 槽）
                    icon = if (option.key == selectedKey) Icons.Default.Check else null,
                )
            }
            if (actions.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                actions.forEach { action ->
                    NirikoMenuItem(
                        text = action.label,
                        onClick = {
                            expanded = false
                            action.onSelect()
                        },
                    )
                }
            }
        }
    }
}
