package com.otakup.niriko.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import com.otakup.niriko.ui.bottombar.FloatingBarConfig
import com.otakup.niriko.ui.bottombar.FloatingBarState
import com.otakup.niriko.ui.bottombar.ScrollIntent
import com.otakup.niriko.ui.bottombar.onBlankTap
import com.otakup.niriko.ui.bottombar.onPageSwitch
import com.otakup.niriko.ui.bottombar.onScroll

/**
 * 悬浮控件滚动联动（B11-Step1，S1 专项）。
 *
 * 改造前：每个滚动容器**各自**持有一个累加器（`private var acc`）却共写同一个 0..1 比例
 * → 后写覆盖 + 轻微回弹就抖，而且没有方向/阈值，Dock 永远只是轻微起伏。
 *
 * 改造后：
 * - 所有容器只把 dy **派发**给唯一状态持有者 [FloatingControlsState]（不再各自累计）；
 * - 方向判定在 [com.otakup.niriko.ui.bottombar.FloatingBarState.onScroll] 纯函数里（有 JVM 单测）；
 * - [LocalBottomBarHideFraction] 保留为「隐藏进度」的输出（0=完全显示，1=完全隐藏），
 *   Dock / 宽屏 inset 继续按它做位移与 alpha，读取路径零改动。
 */
val LocalBottomBarHideFraction = staticCompositionLocalOf<MutableFloatState> { mutableFloatStateOf(0f) }

/**
 * 运行时方向状态（唯一写方）。
 *
 * 由 MainActivity 创建并 provide；滚动容器通过 [reportFloatingScroll] 派发，Dock 与顶栏读它。
 * 未 provide 时（预览 / 单测里的裸组合）[LocalFloatingControls] 为 null，派发自动变成 no-op。
 */
@Stable
class FloatingControlsState internal constructor(initialShown: Boolean = true) {

    private var policy = FloatingBarState(shown = initialShown)

    /** 目标显隐态（动画由 UI 层用连续 progress 逼近它，不直接跳变）。 */
    var shown by mutableStateOf(initialShown)
        private set

    /**
     * 搜索表面 / 弹层展开期间强制显示（B11-Step2）：
     * 展开那一刻复位到显示态，展开期间**拒绝滚动派发**（否则 200dp 搜索卡会被移出屏幕）。
     */
    private var forcedShown by mutableStateOf(false)

    /** 当前是否处于「强制显示」锁定（供 UI/测试读取）。 */
    val isForcedShown: Boolean get() = forcedShown

    fun forceShown(force: Boolean) {
        if (forcedShown == force) return
        forcedShown = force
        if (force) restore()
    }

    /** 吃一次纵向滚动（dyPx 用 nestedScroll 的原始符号，归一化只发生在下面这一行）。 */
    fun dispatch(dyPx: Float, density: Float = 1f) {
        if (forcedShown || dyPx == 0f) return
        policy = policy.onScroll(intentOf(dyPx), dyPx, FloatingBarConfig.Default, density)
        shown = policy.shown
    }

    /** 切页 / 换视图 / 返回：立即显示并清空累计（UI 用 snap，不播动画）。 */
    fun reset() {
        policy = policy.onPageSwitch()
        shown = true
    }

    /** 点空白恢复（B11-Step3 用；本步仅提供能力）。 */
    fun restore() {
        policy = policy.onBlankTap()
        shown = true
    }

    /**
     * **唯一一处**符号归一化（task-25 按真机反馈翻转）。
     *
     * 真机实测：nestedScroll 的 `available.y > 0` 对应「手指上滑、内容往后翻」，
     * 用户要求这种手势**显示**控件、反方向（y < 0）才隐藏。因此：
     * `available.y > 0 → UP（显示）`，`available.y < 0 → DOWN（隐藏）`。
     * 若将来再反，只改这一行。
     */
    private fun intentOf(dyPx: Float): ScrollIntent =
        if (dyPx > 0f) ScrollIntent.UP else ScrollIntent.DOWN
}

/** 方向状态持有者（MainActivity provide）。 */
val LocalFloatingControls = staticCompositionLocalOf<FloatingControlsState?> { null }

/**
 * 为滚动容器挂上「派发滚动方向」的 nestedScroll。
 *
 * 与旧 [reportBottomBarScroll] 的区别：**不再持有累加器**，也不写比例——只把 dy 交给唯一状态。
 * 同一帧里多个嵌套容器各自派发也不会互相覆盖（没有共享可写量）。
 */
@Composable
fun Modifier.reportFloatingScroll(): Modifier {
    val controls = LocalFloatingControls.current
    val density = LocalDensity.current.density
    val connection = remember(controls, density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                controls?.dispatch(available.y, density)
                return Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}

/**
 * task-25：把某个滚动容器直接绑到指定的状态持有者（详情页用）。
 *
 * 详情页不在 MAIN 路由里、且顶栏/收藏胶囊要跟着同一个 [FloatingControlsState] 走，
 * 所以不能依赖全局的 CompositionLocal；直接在详情自己的 LazyColumn 上挂这个 modifier 即可
 * （原始 dy 派发，不经 firstVisibleItemIndex/offset 换算 —— 第一项之后同样有效）。
 */
@Composable
fun Modifier.reportFloatingScrollTo(state: FloatingControlsState): Modifier {
    val density = LocalDensity.current.density
    val connection = remember(state, density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                state.dispatch(available.y, density)
                return Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}

/** 旧名兼容（B11-Step1）：既有调用点自动升级到方向派发，逐页改名可留到后续清理。 */
@Composable
fun Modifier.reportBottomBarScroll(): Modifier = reportFloatingScroll()

/** 供 Dock / 宽屏 inset 读取隐藏**进度**（0..1，0=完全显示，1=完全隐藏）。 */
@Composable
fun bottomBarHideFraction(): Float = LocalBottomBarHideFraction.current.floatValue
