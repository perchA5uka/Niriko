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

    /** Edge-to-edge feeds fade from window y=0, with no opaque toolbar-height plateau. */
    const val WINDOW_MASK_HIDDEN_DP = 0f

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
}
