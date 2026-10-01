package com.otakup.niriko.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * Niriko 窗口布局档位（计划书 §三 B2 / 5a）。
 *
 * 判定分两步：
 * 1. [currentNirikoWindowLayout] 把 androidx 的窗口尺寸类别降成两个 Int 档位；
 * 2. 纯函数 [NirikoWindowLayout.from] 再映射成布局档位（JVM 可单测，不依赖库类型）。
 *
 * 档位含义（本批次）：
 * - [COMPACT]：手机竖直窗口（<600dp 宽）——**与升级前逐像素一致**（胶囊底栏 + 现有单列布局）；
 * - [MEDIUM]：600~839dp 宽，或「宽但矮」的横屏手机（宽度 EXPANDED 但高度 COMPACT）——这轮仍用胶囊底栏；
 * - [EXPANDED]：≥840dp 宽且竖向空间足够（高度 ≥ MEDIUM，即 ≥480dp）——改用竖向玻璃 dock。
 *
 * 为什么 EXPANDED 额外要求高度不是 COMPACT：横屏手机的典型尺寸是 891x412dp，
 * 宽度已是 EXPANDED，但 412dp 的高度放竖排 dock（4 个目的地 + 系统栏）会挤爆；
 * 且手机横屏保持现状可以把 R8（平板改动破坏手机端观感）的回归风险降到最低。
 */
enum class NirikoWindowLayout {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        /** 宽度类别：< 600dp（与 WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND 对应）。 */
        const val WIDTH_COMPACT = 0

        /** 宽度类别：600dp ~ 839dp。 */
        const val WIDTH_MEDIUM = 1

        /** 宽度类别：≥ 840dp。 */
        const val WIDTH_EXPANDED = 2

        /** 高度类别：< 480dp。 */
        const val HEIGHT_COMPACT = 0

        /** 高度类别：480dp ~ 899dp。 */
        const val HEIGHT_MEDIUM = 1

        /** 高度类别：≥ 900dp。 */
        const val HEIGHT_EXPANDED = 2

        /**
         * 宽度类别 + 高度类别 → 布局档位（纯函数）。
         *
         * 非法/越界输入按最小档位处理（防御式，绝不因为异常尺寸崩掉）。
         */
        fun from(widthSizeClass: Int, heightSizeClass: Int): NirikoWindowLayout = when {
            widthSizeClass >= WIDTH_EXPANDED && heightSizeClass >= HEIGHT_MEDIUM -> EXPANDED
            widthSizeClass >= WIDTH_MEDIUM -> MEDIUM
            else -> COMPACT
        }
    }
}

/**
 * 当前窗口的布局档位。
 *
 * 直接调用 [currentWindowAdaptiveInfo]，**不缓存到 remember**：窗口尺寸变化
 * （旋转、自由缩放、折叠/展开、分屏）时它会自动重组并返回新档位。
 */
@Composable
fun currentNirikoWindowLayout(): NirikoWindowLayout {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return NirikoWindowLayout.from(
        widthSizeClass = windowSizeClass.nirikoWidthSizeClass(),
        heightSizeClass = windowSizeClass.nirikoHeightSizeClass(),
    )
}

// 用 window-core 的断点常量 + isWidth/isHeightAtLeastBreakpoint 把库类型降成 Int。
// 不用（已废弃的）getWindowWidthSizeClass()/getWindowHeightSizeClass()，
// 也不用旧的 androidx.compose.material3:material3-window-size-class（计划书明令禁止混用）。
private fun WindowSizeClass.nirikoWidthSizeClass(): Int = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
        NirikoWindowLayout.WIDTH_EXPANDED

    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
        NirikoWindowLayout.WIDTH_MEDIUM

    else -> NirikoWindowLayout.WIDTH_COMPACT
}

private fun WindowSizeClass.nirikoHeightSizeClass(): Int = when {
    isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND) ->
        NirikoWindowLayout.HEIGHT_EXPANDED

    isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
        NirikoWindowLayout.HEIGHT_MEDIUM

    else -> NirikoWindowLayout.HEIGHT_COMPACT
}

/**
 * 海报网格列数（计划书 §三 B2：按宽度类别取 2/3/4~6）。
 *
 * 落点说明：计划书同时要求「手机 <600dp 与现状完全一致（回归零变化是硬要求）」，
 * 而现状是 GridCells.Fixed(3)，故 [COMPACT_COUNT] 绑定 3（优先满足零变化硬约束）。
 */
object NirikoGridColumns {
    /** 手机宽度（<600dp）：与现状一致，固定 3 列。 */
    const val COMPACT_COUNT = 3

    /** 小平板宽度（600~839dp）：4 列。 */
    const val MEDIUM_COUNT = 4

    /** 大平板/折叠展开（≥840dp）：6 列（计划书给的是 4~6）。 */
    const val EXPANDED_COUNT = 6

    /** 布局档位 → 列数。 */
    fun fromLayout(layout: NirikoWindowLayout): Int = when (layout) {
        NirikoWindowLayout.COMPACT -> COMPACT_COUNT
        NirikoWindowLayout.MEDIUM -> MEDIUM_COUNT
        NirikoWindowLayout.EXPANDED -> EXPANDED_COUNT
    }

    /** 宽度类别 → 列数（纯函数，高度不参与网格列数判定）。 */
    fun fromWidthSizeClass(widthSizeClass: Int): Int = fromLayout(
        NirikoWindowLayout.from(
            widthSizeClass = widthSizeClass,
            heightSizeClass = NirikoWindowLayout.HEIGHT_MEDIUM,
        )
    )
}
