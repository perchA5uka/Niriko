package com.otakup.niriko.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.ui.bottombar.LiquidBottomTabs
import com.otakup.niriko.ui.common.bottomBarHideFraction
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 底部导航：迁移为 kyant 的 LiquidBottomTabs（悬浮胶囊）。 */
@Composable
fun NirikoBottomBar(
    pagerState: PagerState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // 稳定 lambda：避免重组时新实例触发 LiquidBottomTabs 内部 remember 重置。
    val selectedTabIndex = remember { { pagerState.currentPage } }
    val hideFraction = bottomBarHideFraction()
    val barAlpha by animateFloatAsState(
        targetValue = 1f - hideFraction * 0.15f,
        animationSpec = tween(durationMillis = 180, easing = AnimEasingDefault),
        label = "bottomBarAlpha",
    )
    Box(
        modifier = modifier
            .offset { IntOffset(0, (8 * hideFraction).dp.toPx().roundToInt()) }
            .graphicsLayer { alpha = barAlpha },
        contentAlignment = Alignment.BottomCenter,
    ) {
        LiquidBottomTabs(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { page ->
                scope.launch { pagerState.animateScrollToPage(page) }
            },
            backdrop = backdrop,
            tabsCount = TopLevelDestination.entries.size,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
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
