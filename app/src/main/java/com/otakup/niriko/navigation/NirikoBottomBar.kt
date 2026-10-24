package com.otakup.niriko.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.otakup.niriko.ui.bottombar.LiquidBottomTabs
import com.otakup.niriko.ui.bottombar.LiquidBottomTab
import com.otakup.niriko.ui.common.bottomBarHideFraction
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 底部导航：迁移为 kyant 的 LiquidBottomTabs（悬浮胶囊）。 */
@Composable
fun NirikoBottomBar(
    pagerState: PagerState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    /** 点按已选中的 tab（F11）：切页之外的第二条语义，交给导航层处置。 */
    onTabReselected: (Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 稳定 lambda：避免重组时新实例触发 LiquidBottomTabs 内部 remember 重置。
    val selectedTabIndex = remember(pagerState) { { pagerState.targetPage } }
    // B11-Step1：隐藏进度 0..1 由方向状态机驱动（MainActivity 单源动画）。
    // 与改造前的 8dp + alpha 0.85 不同：这里完整滑出屏幕、alpha 归零，隐藏态自然不拦触摸
    // （offset 改的是 placement，命中区跟着一起走 —— 不能用 translationY）。
    val hideProgress = bottomBarHideFraction().coerceIn(0f, 1f)
    val density = LocalDensity.current
    var measuredHeightPx by remember { mutableIntStateOf(0) }
    // 位移 = 实测高度 + 24dp 余量：保证胶囊与投影完全离开视口，不残留可点区域。
    val shiftPx = measuredHeightPx + with(density) { 24.dp.toPx() }
    val fullyHidden = hideProgress >= 0.999f
    Box(
        modifier = modifier
            .offset { IntOffset(0, (hideProgress * shiftPx).roundToInt()) }
            .onSizeChanged { measuredHeightPx = it.height }
            .graphicsLayer { alpha = 1f - hideProgress }
            .then(if (fullyHidden) Modifier.clearAndSetSemantics { } else Modifier),
        contentAlignment = Alignment.BottomCenter,
    ) {
        LiquidBottomTabs(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { page ->
                scope.launch { pagerState.animateScrollToPage(page) }
            },
            onTabReselected = onTabReselected,
            backdrop = backdrop,
            tabsCount = TopLevelDestination.entries.size,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                LiquidBottomTab(
                    onClick = {
                        if (pagerState.targetPage == destination.ordinal) onTabReselected(destination.ordinal)
                        else scope.launch { pagerState.animateScrollToPage(destination.ordinal) }
                    },
                ) {
                    val selected = pagerState.currentPage == destination.ordinal
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = stringResource(destination.titleRes),
                        tint = if (selected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(destination.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
