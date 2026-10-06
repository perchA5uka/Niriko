package com.otakup.niriko.util

/**
 * 分集评价 → 作品感想/评分的合并规则（F07，计划 §9.3）。
 *
 * 全部是**纯函数**：这一块的风险不是「界面好不好看」，而是三件会静默弄坏用户文字的事 ——
 * 1. 重复执行把自动块追加两遍（用户点两次「生成」就得到两份）；
 * 2. 合并时顺手把手写感想覆盖掉；
 * 3. 舍入不统一（半分/整数各写一套，7.25 一次算 7.0 一次算 7.5）。
 *
 * 因此这里不碰 Compose、不碰数据库：输入是「每集的分与短评 + 现有感想文本」，
 * 输出是「新感想文本 + 平均分」，每一档边界都在 EpisodeReviewMergePolicyTest 里钉住。
 *
 * ## 「自动块」怎么认
 *
 * 生成的内容被一对**稳定标记**包起来，合并时只替换标记之间的部分：
 *
 * ```
 * ⟦NIRIKO:EPISODE_MERGE⟧
 * 第 1 话 8.5 — 很好
 * 平均 8.5 分（1 集有评分 / 共 12 集）
 * ⟦/NIRIKO:EPISODE_MERGE⟧
 * ```
 *
 * 标记本身是普通字符（不是 HTML 注释），因此用户在看感想时能看懂那块是他自己写的、
 * 哪块是自动生成的。只有一个开始标记（被用户删掉了结束标记）时，**标记之后到文本结尾**
 * 都当作自动块处理 —— 宁可多替一点，也不要留下半块孤立的标记。
 */
object EpisodeReviewMergePolicy {

    /** 自动块开始标记。 */
    const val BEGIN_MARKER = "⟦NIRIKO:EPISODE_MERGE⟧"

    /** 自动块结束标记。 */
    const val END_MARKER = "⟦/NIRIKO:EPISODE_MERGE⟧"

    /** 自动块里的说明行：明确告诉用户这块是生成的、可以整块被下次生成替换。 */
    const val BLOCK_NOTE = "（自动生成 · 来自分集评价；下次生成会整块替换，手写内容不受影响）"

    /** 评分有效区间（与每集评分的 0-10 分制一致）。 */
    val SCORE_RANGE = 0f..10f

    /** 合并模式。 */
    enum class Mode {
        /** 只写入每集短评（不碰评分）。 */
        COMMENTS_ONLY,

        /** 只写入平均分（不写短评）。 */
        AVERAGE_ONLY,

        /** 两者都写。 */
        BOTH,
    }

    /** 平均分的舍入方式：默认半分（0.5 步长），可选整数。 */
    enum class Rounding {
        /** 半分制：结果 ∈ {0.0, 0.5, ..., 10.0}。 */
        HALF_UP_HALF,

        /** 整数制：结果 ∈ {0, 1, ..., 10}。 */
        HALF_UP_INTEGER,
    }

    /**
     * 一集的评价（已带显示用的集号标签）。
     *
     * @param score null = 这一集没打分（库里用 -1 占位、或根本没有行）
     * @param comment null/空白 = 这一集没写短评
     */
    data class Entry(
        val epId: Long,
        val order: Int,
        val label: String,
        val score: Float?,
        val comment: String?,
    )

    /** 合并结果：[impression] 可直接写进作品感想；[average] 为 null 表示「此次没有可写入的均分」。 */
    data class Result(
        val impression: String?,
        val average: Float?,
        val ratedCount: Int,
        val commentedCount: Int,
        val totalCount: Int,
    )

    // ==================== 平均分 ====================

    /** 有效评分：非空、有限、在 0..10 内。**未打分的集不计入**（这是均分的分母口径）。 */
    fun validScores(entries: List<Entry>): List<Float> =
        entries.mapNotNull { it.score }
            .filter { it.isFinite() && it in SCORE_RANGE }

    /**
     * 平均分。没有任何有效评分时返回 null（**不是 0** —— 0 分和「还没评」是两件事）。
     */
    fun average(entries: List<Entry>, rounding: Rounding = Rounding.HALF_UP_HALF): Float? {
        val scores = validScores(entries)
        if (scores.isEmpty()) return null
        return roundHalfUp(scores.average().toFloat(), stepOf(rounding))
    }

    /** 舍入步长：半分 0.5 / 整数 1.0。 */
    fun stepOf(rounding: Rounding): Float = when (rounding) {
        Rounding.HALF_UP_HALF -> 0.5f
        Rounding.HALF_UP_INTEGER -> 1f
    }

    /**
     * 按 [step] 做「正好中点向上」的舍入，并把结果夹在 0..10。
     *
     * 例：step=0.5 时 7.25 → 7.5（正好在中点，向上）、7.24 → 7.0；
     *     step=1.0 时 7.5 → 8（正好在中点，向上）、7.49 → 7。
     * 这里不用 `Math.round`：它只做整数中点向上，套到 0.5 步长上会先丢掉精度。
     */
    fun roundHalfUp(value: Float, step: Float): Float {
        if (!value.isFinite() || step <= 0f) return 0f
        val steps = value / step
        val lower = kotlin.math.floor(steps)
        val fraction = steps - lower
        val rounded = if (fraction >= 0.5f) lower + 1f else lower
        val result = rounded * step
        return result.coerceIn(SCORE_RANGE.start, SCORE_RANGE.endInclusive)
    }

    // ==================== 自动块 ====================

    /**
     * 生成自动块文本。
     *
     * 返回 null 表示**这次没有可写入的内容**（例如「只写短评」模式下没有任何短评）——
     * 调用方据此走「移除旧块」的路径，而不是写一个空块。
     */
    fun buildBlock(
        entries: List<Entry>,
        mode: Mode,
        rounding: Rounding = Rounding.HALF_UP_HALF,
    ): String? {
        val ordered = entries.sortedBy { it.order }
        val withComments = ordered.filter { !it.comment.isNullOrBlank() }
        val average = average(entries, rounding)
        val lines = mutableListOf<String>()
        lines += BEGIN_MARKER
        lines += BLOCK_NOTE

        val wantComments = mode == Mode.COMMENTS_ONLY || mode == Mode.BOTH
        val wantAverage = mode == Mode.AVERAGE_ONLY || mode == Mode.BOTH

        if (wantComments) {
            withComments.forEach { entry ->
                val score = entry.score?.takeIf { it.isFinite() && it in SCORE_RANGE }
                val prefix = if (score != null) "${entry.label} ${formatScore(score)} — " else "${entry.label} — "
                lines += prefix + flatten(entry.comment)
            }
        }
        if (wantAverage && average != null) {
            val rated = validScores(entries).size
            lines += "平均 ${formatScore(average)} 分（${rated} 集有评分 / 共 ${entries.size} 集）"
        }
        lines += END_MARKER

        // 只有标记与说明行 = 没有真正的内容要做
        val hasContent = lines.any { line ->
            line != BEGIN_MARKER && line != END_MARKER && line != BLOCK_NOTE
        }
        if (!hasContent) return null
        return lines.joinToString("\n")
    }

    /**
     * 把 [block] 合并进 [existing] 感想：**手写内容原样保留**，只替换原有的自动块。
     *
     * - [block] 为 null → 只移除旧块（源评价被删掉后，旧块必须能被更新掉）；
     * - 结果里**最多只有一个**自动块（所以重复执行是幂等的）；
     * - 手写内容为空且 block 为 null → 返回 null（不留一个空字符串）。
     */
    fun mergeInto(existing: String?, block: String?): String? {
        val handwritten = stripBlock(existing).trimEnd()
        if (block == null) return handwritten.ifBlank { null }
        if (handwritten.isBlank()) return block
        return handwritten + "\n\n" + block
    }

    /**
     * 去掉 [existing] 里的自动块，返回手写部分（保留其余换行与空白）。
     *
     * 只认「开始标记→结束标记」这一对；只有一个开始标记时，从它到文本结尾都算自动块；
     * 只有结束标记时**不动它**（宁可留一行可疑文本，也不要猜着删用户的字）。
     */
    fun stripBlock(existing: String?): String {
        val text = existing ?: return ""
        val begin = text.indexOf(BEGIN_MARKER)
        if (begin < 0) return text
        val endMarker = text.indexOf(END_MARKER, begin)
        val end = if (endMarker >= 0) {
            endMarker + END_MARKER.length
        } else {
            text.length
        }
        return (text.substring(0, begin) + text.substring(end))
    }

    /** 现有感想里是否已经有自动块（界面据此提示「会整块替换」）。 */
    fun hasAutoBlock(existing: String?): Boolean =
        existing?.contains(BEGIN_MARKER) == true

    // ==================== 覆盖确认 ====================

    /**
     * 是否**需要用户显式确认**才允许覆盖作品评分。
     *
     * §16 的边界：默认不覆盖已有手动评分。已经打过分（> 0）时必须由用户勾选才覆盖；
     * 没打过（null / 0）则是「填一个空的」——那是这个功能的主用途，不需要额外确认。
     */
    fun needsOverwriteConfirmation(currentRating: Float?): Boolean =
        currentRating != null && currentRating > 0f

    /** 按模式与用户选择算出最终是否写入评分，以及要写入的值。 */
    fun resolveRating(
        mode: Mode,
        average: Float?,
        currentRating: Float?,
        overwriteConfirmed: Boolean,
    ): Float? {
        if (mode == Mode.COMMENTS_ONLY) return null
        if (average == null) return null
        if (needsOverwriteConfirmation(currentRating) && !overwriteConfirmed) return null
        return average
    }

    /** 一次调用把所有东西算出来（界面只需要这一个入口）。 */
    fun compose(
        entries: List<Entry>,
        existing: String?,
        mode: Mode,
        rounding: Rounding,
        currentRating: Float?,
        overwriteConfirmed: Boolean,
    ): Result {
        val average = average(entries, rounding)
        val block = buildBlock(entries, mode, rounding)
        // COMMENTS_ONLY 时评分不是「没写入」而是「不涉及」——用 null 表达，界面据此不写评分
        val resolvedRating = resolveRating(mode, average, currentRating, overwriteConfirmed)
        return Result(
            impression = if (mode == Mode.AVERAGE_ONLY) existing else mergeInto(existing, block),
            average = resolvedRating,
            ratedCount = validScores(entries).size,
            commentedCount = entries.count { !it.comment.isNullOrBlank() },
            totalCount = entries.size,
        )
    }

    // ==================== 小工具 ====================

    /**
     * 一集在合并块里的显示名（纯函数，因此「集号缺失 / 标题为空」这两个边界可测）。
     *
     * - `ep` 是 Bangumi 的章节号（Double，可能是 0 或小数）；缺失时退回**位置序号**（从 1 数）；
     * - 标题为空时只留集号，不留一个孤零零的空格；
     * - `unit` 由调用方按作品类型给（动画/剧集「集」、漫画「话」、音乐「首」）。
     */
    fun entryLabel(ep: Double, title: String?, fallbackIndex: Int, unit: String = "集"): String {
        val number = if (ep > 0.0) ep.toInt() else fallbackIndex + 1
        val cleanTitle = title?.trim().orEmpty()
        return if (cleanTitle.isEmpty()) "第 $number $unit" else "第 $number $unit $cleanTitle"
    }

    /** 分数显示：整数不带小数点，其余保留一位。 */
    fun formatScore(score: Float): String =
        if (score == score.toInt().toFloat()) score.toInt().toString()
        else String.format(java.util.Locale.ROOT, "%.1f", score)

    /**
     * 把单集短评压成一行：内部的换行/制表/连续空格折叠成单个空格。
     *
     * 为什么必须折叠：合并块是**逐行**的，如果某集短评里有换行，那一行会被拆成两行 ——
     * 之后 `stripBlock` 仍然能删掉整块（标记是行级的），但用户看到的块会碎成一团。
     */
    fun flatten(comment: String?): String =
        comment?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
}
