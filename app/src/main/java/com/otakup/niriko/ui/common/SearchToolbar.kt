package com.otakup.niriko.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.otakup.niriko.data.model.search.TrendingMode

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
    modifier: Modifier = Modifier,
) {
    // 表面展开（MENU/DRAGGING/IMMERSIVE）时盖住趋势标签，仅 COLLAPSED 显示
    val showTabs = viewModel.phase == SearchPhase.COLLAPSED

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        // —— 底层：趋势标签（当季热门 / 历史排名） · 仅 COLLAPSED 可见 ——
        if (showTabs) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrendingMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    TrendTab(
                        label = mode.label,
                        selected = trendingMode == mode,
                        onClick = { onSetTrendingMode(mode) },
                    )
                }
            }
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
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}
