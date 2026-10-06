package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType

/**
 * 书籍 / 漫画的半调网点（F18，计划 §11.3）。
 *
 * 三条硬约束都落在这里的纯规则上：
 * - **按类型**：只有 [SubjectType.BOOK] / [SubjectType.MANGA] 自动启用，其他类型一律关闭；
 * - **轻量**：强度有上限（[MAX_ALPHA]），且按深浅主题各给一档 —— 深色底上稍强、浅色底上更淡；
 * - **可独立关闭**：总开关在设置里（`AppSettings.halftoneEnabled`），关掉即完全不画。
 *
 * 「不能遮挡正文和点击区域」是**画法**保证的（点阵画在内容**之下**，见 `Modifier.halftoneDots`），
 * 因此它不是一条需要运行时判断的规则 —— 这里只负责「画不画、画多密、多强」。
 *
 * 与「减少动态效果」无关：网点是**静态纹理**，不是动效；用户要关它用总开关。
 */
object HalftonePolicy {

    /** 自动启用网点的类型。 */
    val supportedTypes: Set<SubjectType> = setOf(SubjectType.BOOK, SubjectType.MANGA)

    /** 强度上限：超过这个值就会影响正文可读性（§11.3 的底线）。 */
    const val MAX_ALPHA = 0.10f

    /** 浅色主题下的强度（更淡：浅底上点更容易显脏）。 */
    const val LIGHT_ALPHA = 0.045f

    /** 深色主题下的强度。 */
    const val DARK_ALPHA = 0.075f

    /** 点阵间距（dp）。 */
    const val DOT_SPACING_DP = 12f

    /** 点半径（dp）。 */
    const val DOT_RADIUS_DP = 1.1f

    /**
     * 这个作品要不要画网点。
     *
     * @param type 作品类型（null 一律不画）
     * @param enabled 设置里的总开关
     */
    fun shouldApply(type: SubjectType?, enabled: Boolean): Boolean =
        enabled && type != null && type in supportedTypes

    /** 该主题下的强度（一定 ≤ [MAX_ALPHA]）。 */
    fun alphaFor(isDark: Boolean): Float =
        (if (isDark) DARK_ALPHA else LIGHT_ALPHA).coerceIn(0f, MAX_ALPHA)

    /**
     * 一屏大约多少个点（供性能评估与测试断言）。
     *
     * 错行排布时实际点数略多于按行估算，这里给的是**上界**：`ceil(w/s) * ceil(h/s)`。
     * 用它可以在单测里断言「1080p 手机（约 393×830dp）的点数不会失控」。
     */
    fun maxDotCount(widthDp: Float, heightDp: Float, spacingDp: Float = DOT_SPACING_DP): Int {
        if (widthDp <= 0f || heightDp <= 0f) return 0
        val spacing = spacingDp.coerceAtLeast(2f)
        val cols = kotlin.math.ceil(widthDp / spacing).toInt()
        val rows = kotlin.math.ceil(heightDp / spacing).toInt()
        return cols * rows
    }

    /** 间距是否合理（太小会变成灰幕、太大就看不出网点）。 */
    fun isSpacingSane(spacingDp: Float = DOT_SPACING_DP): Boolean = spacingDp in 6f..24f
}
