package com.otakup.niriko.ui.adaptive

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.otakup.niriko.navigation.NirikoBottomBar
import com.otakup.niriko.navigation.TopLevelDestination
import com.otakup.niriko.ui.animation.AnimDurationShort
import com.otakup.niriko.ui.animation.motionEnabled
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.bottombar.LiquidVerticalDock
import com.otakup.niriko.ui.bottombar.LocalLiquidBottomTabScale
import com.otakup.niriko.ui.bottombar.LocalLiquidBottomTabInteractive
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import com.otakup.niriko.ui.common.bottomBarHideFraction
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 导航宿主（计划书 §三 B2 / 5a）：按 [NirikoWindowLayout] 在
 * 「横向悬浮玻璃胶囊底栏」与「竖向悬浮玻璃胶囊 dock」之间切换。
 *
 * 刻意**不用** NavigationSuiteScaffold 直接替换：它承载不了玻璃材质与跟手动画，
 * 这里只借鉴它的断点思路（见 [NirikoWindowLayout]）。
 *
 * 非 [NirikoWindowLayout.EXPANDED] 分支逐字沿用原有调用表达式
 * （NirikoBottomBar + Modifier.align(BottomCenter)），保证手机端（<600dp）像素零变化。
 */
@Composable
fun BoxScope.NirikoNavSuite(
    layout: NirikoWindowLayout,
    pagerState: PagerState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onDestinationSelected: ((Int) -> Unit)? = null,
    /** 点按已选中的 tab（F11）：切页之外的第二条语义，两条 dock 形态共用同一入口。 */
    onTabReselected: ((Int) -> Unit)? = null,
) {
    if (layout == NirikoWindowLayout.EXPANDED) {
        VerticalDockSlot(
            pagerState = pagerState,
            backdrop = backdrop,
            onDestinationSelected = onDestinationSelected,
            onTabReselected = onTabReselected,
            modifier = modifier.align(Alignment.CenterStart),
        )
    } else {
        NirikoBottomBar(
            pagerState = pagerState,
            backdrop = backdrop,
            modifier = modifier.align(Alignment.BottomCenter),
            onTabReselected = { page -> onTabReselected?.invoke(page) },
        )
    }
}

@Composable
private fun VerticalDockSlot(
    pagerState: PagerState,
    backdrop: Backdrop,
    onDestinationSelected: ((Int) -> Unit)?,
    onTabReselected: ((Int) -> Unit)? = null,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    // 稳定 lambda：避免重组时新实例触发 dock 内部 remember 重置（与 NirikoBottomBar 同款）
    val selectedTabIndex = remember(pagerState) { { pagerState.targetPage } }
    // B11-Step1：与手机端共用同一个隐藏进度（方向状态机给出），动画已在 MainActivity 单源完成。
    // 宽屏裁决：不做完整滑出（dock 与内容起始 inset 绑定，完整滑出会重排），
    // 只做「向左 24dp + alpha→0 + 隐藏态禁交互」。
    val hideProgress = bottomBarHideFraction().coerceIn(0f, 1f)
    val motion = motionEnabled()
    val specs = NirikoMotionSpecs
    val dockAlpha = 1f - hideProgress
    val dockInteractive = hideProgress < 0.5f

    // 出现动画：切到宽屏（旋转 / 自由缩放）时淡入 + 轻微左移入场；reduceMotion 时跳过
    val appearance = remember { Animatable(0f) }
    LaunchedEffect(motion) {
        appearance.snapTo(if (motion) 0f else 1f)
        if (appearance.value < 1f) {
            appearance.animateTo(
                1f,
                specs.spatialFast()
            )
        }
    }
    val slideIn = 1f - appearance.value

    Box(
        modifier = modifier
            .offset { IntOffset(-(24f * hideProgress).dp.toPx().roundToInt(), 0) }
            .graphicsLayer {
                alpha = dockAlpha * appearance.value
                translationX = -16f.dp.toPx() * slideIn
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 隐藏过半即禁交互：宽屏只位移 24dp，必须显式关掉 tab 点击（命中区还在屏内）
        CompositionLocalProvider(LocalLiquidBottomTabInteractive provides dockInteractive) {
        LiquidVerticalDock(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { page ->
                val handler = onDestinationSelected
                if (handler != null) handler(page)
                else scope.launch { pagerState.animateScrollToPage(page) }
            },
            onTabReselected = { page -> onTabReselected?.invoke(page) },
            backdrop = backdrop,
            tabsCount = TopLevelDestination.entries.size,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
        ) {
            val tabScale = LocalLiquidBottomTabScale.current
            val interactive = LocalLiquidBottomTabInteractive.current
            TopLevelDestination.entries.forEach { destination ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(enabled = interactive, role = Role.Tab) {
                            if (pagerState.targetPage == destination.ordinal) onTabReselected?.invoke(destination.ordinal)
                            else if (onDestinationSelected != null) onDestinationSelected(destination.ordinal)
                            else scope.launch { pagerState.animateScrollToPage(destination.ordinal) }
                        }
                        .graphicsLayer { scaleX = tabScale(); scaleY = tabScale() },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val selected = pagerState.currentPage == destination.ordinal
                    Icon(
                        imageVector = if (selected) destination.selectedIcon
                        else destination.unselectedIcon,
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
}
