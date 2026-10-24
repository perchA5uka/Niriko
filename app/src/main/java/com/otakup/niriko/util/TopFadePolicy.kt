package com.otakup.niriko.util

/**
 * 顶部渐隐与悬浮控件让位的**纯规则**（F12 / F13，计划 §8.3）。
 *
 * 这两条要求本身就把「怎么做」写清楚了：
 * - F13：「顶部渐隐采用独立 Overlay 和**连续** alpha/gradient，**不使用滚动阈值离散显隐**」——
 *   因此这里只能是「滚动距离 → 透明度」的连续函数，任何 `if (offset > 40) 显示` 都是错的；
 * - F12：「用 Box overlay 将控件固定在窗口层，列表内容可延伸到其下方」+
 *   「防止浮层吞掉第一项或滚动手势」——因此列表的顶部内边距必须**等于控件实测高度**，
 *   既不遮第一项，也不留无谓空白。
 *
 * 两条规则都不依赖 Compose，因此可以在 JVM 单测里逐点断言（包含边界与单调性）。
 */
object TopFadePolicy {

    /** 渐隐带高度（dp）：滚动到这么远时顶部渐隐完全展开。 */
    const val FADE_DISTANCE_DP = 24f

    /** 悬浮控件行高（dp）：顶栏上的搜索按钮 / 分区选择器 / 详情页头部都是这个高度。 */
    const val FLOATING_CONTROL_ROW_DP = 56f

    /** 悬浮控件行与窗口顶之间的间隙（dp）：四页的顶栏都是 `windowTopInset + 8.dp`。 */
    const val FLOATING_CONTROL_TOP_GAP_DP = 8f

    /** 渐隐起点透明度（刚到顶时）：不为 0，否则顶部控件与内容会「贴死」看不出层次。 */
    const val MIN_ALPHA = 0.35f

    /** 渐隐终点透明度（滚开之后）：不为 1，否则会变成一条实心色带。 */
    const val MAX_ALPHA = 0.92f

    /**
     * 滚动进度 → 渐隐强度（**连续**）。
     *
     * @param scrollOffsetPx 当前列表相对顶部的滚动距离（像素；首项索引非 0 时应当视作「已滚很远」）
     * @param fadeDistancePx 渐隐带高度（像素）
     *
     * 返回 [MIN_ALPHA]..[MAX_ALPHA]；负数（橡皮筋回弹）按 0 处理，因此不会出现「越界越亮」。
     */
    fun alphaFor(scrollOffsetPx: Float, fadeDistancePx: Float): Float {
        val distance = fadeDistancePx.coerceAtLeast(1f)
        val t = (scrollOffsetPx / distance).coerceIn(0f, 1f)
        // 末尾必须再夹一次：浮点累加会让 t=1 时算出比 MAX_ALPHA 大一丁点的值
        // （0.35f + 0.57f = 0.92000008 > 0.92f），那样「返回值一定在 MIN..MAX 内」这条契约就破了。
        return (MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t).coerceIn(MIN_ALPHA, MAX_ALPHA)
    }

    /** 便捷重载：用默认渐隐带高度（dp → 需要调用方给密度）。 */
    fun alphaForDp(scrollOffsetDp: Float, fadeDistanceDp: Float = FADE_DISTANCE_DP): Float =
        alphaFor(scrollOffsetDp, fadeDistanceDp)

    /**
     * 列表的顶部内边距（dp）= 控件实测高度 + 一点点呼吸空间。
     *
     * 控件高度为 0（还没测量出来）时返回 [MIN_HEADER_PADDING_DP]：
     * 宁可先留一点空白，也不要在第一帧把内容顶到控件下面（那会让第一项被遮住一瞬间）。
     */
    const val MIN_HEADER_PADDING_DP = 8f

    fun listTopPaddingDp(headerHeightDp: Float, extraDp: Float = 4f): Float =
        (headerHeightDp.coerceAtLeast(0f) + extraDp).coerceAtLeast(MIN_HEADER_PADDING_DP)

    /** Feed alpha at a point in the short mask; outside the band stays fully opaque. */
    fun contentAlphaAt(yPx: Float, bandHeightPx: Float, strength: Float): Float {
        if (bandHeightPx <= 0f) return 1f
        val progress = (yPx / bandHeightPx).coerceIn(0f, 1f)
        return 1f - strength.coerceIn(0f, 1f) * (1f - progress)
    }

    /** Initial padding lives inside the scroll container, not around its viewport. */
    fun searchContentPaddingDp(surfaceHeightDp: Float): Float =
        listTopPaddingDp(surfaceHeightDp + 8f, extraDp = 8f)

    /** 首项索引非 0 时的「已滚很远」判据：此时渐隐应当保持最亮（不要因为 offset 归零而突然变淡）。 */
    fun scrollOffsetFor(firstVisibleItemIndex: Int, firstVisibleItemOffsetPx: Int): Float =
        if (firstVisibleItemIndex > 0) Float.MAX_VALUE / 4f else firstVisibleItemOffsetPx.toFloat()

    // ===== 顶部渐隐边界与内容避让 =====
    // 遮罩起点：窗口原点（0.dp，三处调用点直接传字面量）；内容避让：feedTopPaddingDp。

    /**
     * **已不再是遮罩起点**（v1.2.1 真机反馈的裁决）。
     *
     * 渐隐的上边界回到「窗口原点 / 屏幕顶部（含状态栏区域）」：三处 `topContentAlphaMask` 都传
     * `hiddenHeight = 0.dp`，与 v1.2.0 的发现页一致。本函数是曾经的「inset + 控件行高」实心台，
     * 现在只剩详情页在过渡期引用（由另一张卡迁移），迁完即可删除。
     *
     * 内容避让（列表顶部内距）请用 [feedTopPaddingDp] —— 两者不是一回事：
     * 遮罩起点在屏幕顶部，内容仍要从悬浮控件下方开始，否则第一项会被按钮盖住。
     */
    fun windowMaskHiddenDp(windowTopInsetDp: Float): Float =
        windowTopInsetDp.coerceAtLeast(0f) + FLOATING_CONTROL_ROW_DP

    /**
     * 内容列表 / 网格的顶部内距（dp）。
     *
     * 与各页原先手写的 `windowTopInset + searchContentPaddingDp(56f)` **数学等价**，
     * 只是把同一条算式收进一处，便于以后统一调（B1 的「只搬运不改语义」）。
     */
    fun feedTopPaddingDp(windowTopInsetDp: Float): Float =
        windowTopInsetDp.coerceAtLeast(0f) + searchContentPaddingDp(FLOATING_CONTROL_ROW_DP)

    /**
     * 搜索表面展开后，内容区的顶部让位（dp）：内容不能钻进展开卡片下面（B3）。
     *
     * - `collapsed`：只有 56dp 工具栏（与 [feedTopPaddingDp] 同值，展开前的原值）；
     * - `immersiveCollapsed`：沉浸搜索但未编辑 / 未展开筛选行，卡片只有 68dp；
     * - 其余：完整菜单高度（默认 200dp）。
     *
     * 卡片本身从 `inset + 8.dp` 开始（见 SearchToolbar 的 top padding），所以让位值 =
     * inset + 8 + 卡片高。
     */
    fun searchExpandedTopInsetDp(
        windowTopInsetDp: Float,
        menuHeightDp: Float,
        collapsedHeightDp: Float,
        collapsed: Boolean,
        immersiveCollapsed: Boolean,
    ): Float {
        val inset = windowTopInsetDp.coerceAtLeast(0f)
        return when {
            collapsed -> inset + searchContentPaddingDp(FLOATING_CONTROL_ROW_DP)
            immersiveCollapsed -> inset + FLOATING_CONTROL_TOP_GAP_DP + collapsedHeightDp.coerceAtLeast(0f)
            else -> inset + FLOATING_CONTROL_TOP_GAP_DP + menuHeightDp.coerceAtLeast(0f)
        }
    }
}
