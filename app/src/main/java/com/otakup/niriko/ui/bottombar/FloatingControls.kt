package com.otakup.niriko.ui.bottombar

/**
 * B11-Step1：悬浮控件（Dock / 顶栏 / FAB）的显隐方向状态机（纯 Kotlin，无 Compose 依赖）。
 *
 * 改造前只有一条连续比例（`LocalBottomBarHideFraction`，acc/1200），没有方向、没有阈值：
 * 轻微回弹就抖、反向滚动要「抵消」全部已累计距离、且永远只偏移 8dp 从不真隐藏。
 * 这里把判定抽成纯函数，UI 层只负责把 dy 归一化成 [ScrollIntent] 并驱动动画。
 *
 * 符号约定（**唯一一处**）：adapter 负责把 Compose nestedScroll 的 `available.y` 翻成
 * [ScrollIntent]；本文件不猜符号，只吃归一化后的意图。
 */
enum class ScrollIntent { UP, DOWN }

/**
 * 判定参数。
 *
 * @property hideThresholdDp 向下累计多少 dp 后隐藏（S1：48dp）
 * @property showThresholdDp 向上累计多少 dp 后恢复（16dp，故意更灵敏：「恢复不要求回到顶部」）
 * @property jumpGuardPx 单事件跨度超过它视为程序化跳转（回顶 / 恢复位置），只清累计、不改显隐
 */
data class FloatingBarConfig(
    val hideThresholdDp: Float = 48f,
    val showThresholdDp: Float = 16f,
    val jumpGuardPx: Float = 1600f,
) {
    fun hideThresholdPx(density: Float): Float = hideThresholdDp * density
    fun showThresholdPx(density: Float): Float = showThresholdDp * density

    companion object {
        val Default = FloatingBarConfig()
    }
}

/**
 * 显隐状态。
 *
 * @property shown 目标态（UI 用连续动画逼近它，不直接跳变）
 * @property accumulatePx 当前方向上的累计位移（换方向即清零，见 [onScroll]）
 * @property lastIntent 上一次的方向；null = 尚未有过滚动（首帧 / 刚复位）
 */
data class FloatingBarState(
    val shown: Boolean = true,
    val accumulatePx: Float = 0f,
    val lastIntent: ScrollIntent? = null,
)

/**
 * 吃一次滚动事件，返回新状态。判定顺序（逐条可测）：
 *
 * 1. `|deltaPx| > jumpGuardPx` → 只清累计，**不改** shown（回顶/恢复位置不算用户手势）；
 * 2. intent 与上次不同 → **先清累计**再累计（反向判定：向上 10dp 不会先抵掉向下 30dp）；
 * 3. DOWN 累计 ≥ hideThreshold → `shown = false` 并清零；
 * 4. UP 累计 ≥ showThreshold → `shown = true` 并清零；
 * 5. 其余保持（累计继续增长，但封顶到阈值，避免长时间滚动后数字无界）。
 *
 * @param deltaPx 本次位移的**绝对值**由本函数取（调用方传有符号值也安全）
 * @param density dp→px；UI 传 `LocalDensity.current.density`，单测直接给 2.75 之类
 */
fun FloatingBarState.onScroll(
    intent: ScrollIntent,
    deltaPx: Float,
    config: FloatingBarConfig = FloatingBarConfig.Default,
    density: Float = 1f,
): FloatingBarState {
    val magnitude = kotlin.math.abs(deltaPx)
    if (magnitude == 0f) return this
    // ① 程序化跳转：只清累计，显隐不动
    if (magnitude > config.jumpGuardPx) return copy(accumulatePx = 0f)

    // ② 反向即清零，然后重新累计
    val switched = lastIntent != intent
    val accumulated = (if (switched) 0f else accumulatePx) + magnitude
    val hidePx = config.hideThresholdPx(density)
    val showPx = config.showThresholdPx(density)

    return when (intent) {
        // ③ 向下：够阈值就隐藏并清零；已隐藏时不再累计（无意义且会无界增长）
        ScrollIntent.DOWN -> if (shown && accumulated >= hidePx) {
            copy(shown = false, accumulatePx = 0f, lastIntent = intent)
        } else {
            copy(
                accumulatePx = if (shown) accumulated.coerceAtMost(hidePx) else 0f,
                lastIntent = intent,
            )
        }

        // ④ 向上：够阈值就恢复并清零；已显示时继续累计（上限 showPx），
        //    这样「DOWN 30 → UP 10」之后再 UP 6 能凑满 16dp 恢复（反向判定不丢新方向的量）。
        ScrollIntent.UP -> if (!shown && accumulated >= showPx) {
            copy(shown = true, accumulatePx = 0f, lastIntent = intent)
        } else {
            copy(accumulatePx = accumulated.coerceAtMost(showPx), lastIntent = intent)
        }
    }
}

/** 点空白处恢复（B11-Step3 使用）：立即显示并清空累计。 */
fun FloatingBarState.onBlankTap(): FloatingBarState = FloatingBarState()

/** 切页 / 返回 / 换视图：立即显示并清空累计（复位不带动画，由 UI snap）。 */
fun FloatingBarState.onPageSwitch(): FloatingBarState = FloatingBarState()
