package com.otakup.niriko.util

/**
 * 横滑轨道卡片的高度稳定规则（B03，计划 §5.4）。
 *
 * ## 问题本身
 *
 * `LazyRow` 的高度 = 当前**已组合**卡片中最高的那张。因此只要卡片高度依赖数据，
 * 横向滚动时新卡片被组合进来就会让整条轨道的高度变化 —— 用户看到的就是「横滑时上下抖动」。
 *
 * 卡片高度的差异只有两个来源：
 * 1. **标题行数**（短标题一行、长标题两行）；
 * 2. **可选的尾随行**（角色名 / 职业 / 关系标签 / 声优 / 参与身份 —— 有数据的卡多一行）。
 *
 * ## 规则
 *
 * 因此轨道内的卡片必须**恒定占据同样的行数**：
 * - 标题用 `minLines = maxLines = 固定值`（短标题也占满预留行）；
 * - 尾随行**无条件渲染**（没有数据时渲染空文本），并给 `minLines = 1`。
 *
 * 这两条就是 [fixedTextLines] 表达的东西；只要所有卡片都走它，轨道高度就与数据顺序无关，
 * [isUniform] 给出可断言的判据（单测见 RailCardPolicyTest）。
 *
 * 封面一律按固定尺寸或固定比例（各卡片自己保证），不在这里表达。
 */
object RailCardPolicy {

    /** 窄卡（100–120dp）标题固定一行。 */
    const val TITLE_LINES = 1

    /** 关联条目卡（120dp + 两行标题的版面）标题**固定两行**：短标题也占两行。 */
    const val WIDE_TITLE_LINES = 2

    /** 尾随标签行预留数（角色名 / 职业 / 关系 / 声优 —— 每个卡片最多一行）。 */
    const val LABEL_LINES = 1

    /**
     * 一张卡固定的文本行数（**与数据无关** —— 这正是它的意义）。
     *
     * @param titleLines 该卡版面的标题行数（窄卡 [TITLE_LINES]、宽卡 [WIDE_TITLE_LINES]）
     * @param labelLines 该卡版面的尾随标签行数（默认 [LABEL_LINES]）
     */
    fun fixedTextLines(titleLines: Int = TITLE_LINES, labelLines: Int = LABEL_LINES): Int =
        titleLines.coerceAtLeast(1) + labelLines.coerceAtLeast(0)

    /**
     * 同一轨道内所有卡片的高度是否一致。
     *
     * 判据刻意做成「去重后只剩一个值」：只要有一张卡因为数据不同而矮一点，
     * 轨道高度就会在滚动时变化 —— 这就是 B03。
     */
    fun isUniform(heightsDp: List<Float>): Boolean = heightsDp.distinct().size <= 1

    /** Avatar, gap, two label slots, and both layers of 8dp padding. */
    fun staffCardHeightDp(lineHeightDp: Float, labelLines: Int = LABEL_LINES, extraGapDp: Float = 0f): Float =
        64f + 6f + extraGapDp + fixedTextLines(labelLines = labelLines) * lineHeightDp + 32f

    /** 卡片最小内容高度（供需要 `heightIn(min = …)` 的卡片使用）。 */
    fun minContentHeightDp(
        coverHeightDp: Float,
        gapDp: Float = 6f,
        lineHeightDp: Float = 16f,
        titleLines: Int = TITLE_LINES,
        labelLines: Int = LABEL_LINES,
    ): Float = coverHeightDp + gapDp + fixedTextLines(titleLines, labelLines) * lineHeightDp
}
