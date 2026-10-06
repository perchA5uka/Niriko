package com.otakup.niriko.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.otakup.niriko.data.model.search.DiscoveryLayout
import com.otakup.niriko.data.model.search.TrendingMode
import com.otakup.niriko.ui.components.searchGlassSurface

/**
 * 搜索工具栏（iOS 26 顶栏）：左侧趋势标签（当季热门/历史排名）+ 右侧搜索表面。
 *
 * 布局采用叠层 Box（左右边距由 [IosStyleSearchComponent] 内部统一负责，本组件不再加 padding）：
 * - 底层：趋势标签（仅 COLLAPSED 显示）；
 * - 顶层：右侧隔离 Box（仅锁死占位高度 56dp，宽由内容决定），内部 [IosStyleSearchComponent]
 *   以右上角为锚点（contentAlignment=TopEnd）展开为全宽并向下溢出（MENU/DRAGGING/IMMERSIVE 时盖住趋势标签）。
 *
 * Box 隔离法：外层隔离 Box 用 Modifier.height(56.dp) 欺骗 Row 测量（始终以为只有 56dp 高，与左侧
 * 趋势标签齐平）；组件内部用 requiredWidth/requiredHeight 决定自身尺寸（收起=56dp、展开=全宽），
 * 绝不残留全宽悬浮层遮挡左侧点击。
 *
 * [IosStyleSearchComponent] 负责 4 状态状态机（按钮⇄菜单⇄长按拖拽⇄沉浸搜索⇄×）与手势；
 * 本组件只做顶栏布局编排与回调转发。
 */
@Composable
fun SearchToolbar(
    viewModel: SearchViewModel,
    trendingMode: TrendingMode,
    onSetTrendingMode: (TrendingMode) -> Unit,
    onModeSelected: (SearchMode) -> Unit,
    onTypeSelected: (ContentType) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onGestureLockChange: (Boolean) -> Unit,
    /** 当前发现页布局（第 5 轮 D28）。 */
    layout: DiscoveryLayout = DiscoveryLayout.CARD,
    /** 按「下一个布局」三态循环切换：卡片 → 宫格 → 海报 → 卡片。 */
    onToggleLayout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 表面展开（MENU/DRAGGING/IMMERSIVE）时盖住趋势标签，仅 COLLAPSED 显示
    val showTabs = viewModel.phase == SearchPhase.COLLAPSED

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        if (showTabs) {
            VerticalTopSelector(
                selectedKey = trendingMode.name,
                options = TrendingMode.TREND_MODES.map { mode ->
                    TopSelectorOption(mode.name, mode.label) { onSetTrendingMode(mode) }
                },
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .padding(start = 16.dp, end = 88.dp),
            )
        }

        // —— 顶层：搜索表面（4 状态状态机），从右上角锚点展开为全宽 ——
        // Box 隔离法：外层隔离 Box 仅锁死占位高度 56dp（让 Row/布局以为永远只有 56dp，与左侧
        // 趋势标签齐平），宽由内容决定（收起=56dp、展开=全宽）；contentAlignment=TopEnd 锁定
        // 右上角锚点 → 展开时组件只向左/向下溢出，绝不顶进状态栏，也绝不残留全宽悬浮层。
        // 组件不 fillMaxSize：尺寸由内部 requiredWidth/requiredHeight 决定。
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(56.dp)
                // 右缘 16dp 边距补偿：删除卡片内部 offset 后，用隔离 Box 右侧内边距统一约束
                // 组件右缘 —— COLLAPSED 按钮与展开菜单最右缘都在距屏幕右 16dp 处，绝对垂直对齐
                .padding(end = 16.dp)
                .zIndex(10f),
            contentAlignment = Alignment.TopEnd,
        ) {
            IosStyleSearchComponent(
                viewModel = viewModel,
                onModeSelected = onModeSelected,
                onTypeSelected = onTypeSelected,
                onQueryChanged = onQueryChanged,
                onSearchSubmit = onSearchSubmit,
                onGestureLockChange = onGestureLockChange,
                tools = {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    val (icon, description) = when (layout) {
                        DiscoveryLayout.CARD -> Icons.Default.Menu to "切到宫格视图"
                        DiscoveryLayout.GRID -> Icons.Outlined.ViewModule to "切到海报视图"
                        DiscoveryLayout.POSTER -> Icons.AutoMirrored.Filled.List to "切回卡片视图"
                    }
                    SearchToolButton(description, onToggleLayout) {
                        Icon(icon, description, Modifier.size(20.dp))
                    }
                },
                modifier = Modifier.zIndex(10f),
            )
        }
    }
}

/** 趋势切换标签（与旧版样式一致）。 */
@Composable
private fun TrendTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "trendTabBg",
    )
    Box(
        modifier = Modifier
            .searchGlassSurface(RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
