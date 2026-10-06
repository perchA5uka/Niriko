package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分集评价合并规则（F07）的单测。
 *
 * 验收项（§9.3）：中文、英文、已有前后缀、换行、重复执行、取消、部分分集评分、舍入边界。
 * 其中「取消」在纯函数层表现为**不调用**，因此它由界面那条路径保证；
 * 这里覆盖的是另外七项 —— 它们是会静默弄坏用户文字的。
 */
class EpisodeReviewMergePolicyTest {

    private fun entry(
        order: Int,
        label: String,
        score: Float? = null,
        comment: String? = null,
    ) = EpisodeReviewMergePolicy.Entry(epId = order.toLong(), order = order, label = label, score = score, comment = comment)

    // ==================== 有效评分与均分 ====================

    @Test
    fun `未打分与越界的分数都不计入均分`() {
        val entries = listOf(
            entry(1, "第 1 话", score = 8f),
            entry(2, "第 2 话", score = null, comment = "只写了短评"),
            entry(3, "第 3 话", score = -1f),
            entry(4, "第 4 话", score = 12f),
            entry(5, "第 5 话", score = Float.NaN),
            entry(6, "第 6 话", score = 6f),
        )
        val scores = EpisodeReviewMergePolicy.validScores(entries)
        assertEquals(listOf(8f, 6f), scores)
        // 均分只按有效评分算：(8+6)/2 = 7
        assertEquals(7f, EpisodeReviewMergePolicy.average(entries)!!, 0.001f)
    }

    @Test
    fun `没有有效评分时均分是 null 而不是 0`() {
        assertNull(EpisodeReviewMergePolicy.average(emptyList()))
        assertNull(EpisodeReviewMergePolicy.average(listOf(entry(1, "第 1 话", score = null, comment = "写了字"))))
        assertNull(EpisodeReviewMergePolicy.average(listOf(entry(1, "第 1 话", score = -1f))))
    }

    /** 舍入边界：正好中点在半分制与整数制下都**向上**。 */
    @Test
    fun `舍入边界_正好中点向上`() {
        val half = EpisodeReviewMergePolicy.Rounding.HALF_UP_HALF
        assertEquals(7.5f, EpisodeReviewMergePolicy.roundHalfUp(7.25f, 0.5f), 0.0001f)
        assertEquals(7.0f, EpisodeReviewMergePolicy.roundHalfUp(7.24f, 0.5f), 0.0001f)
        assertEquals(7.5f, EpisodeReviewMergePolicy.roundHalfUp(7.26f, 0.5f), 0.0001f)
        // 半分制下 7.25 的平均：第 1 话 7.0 + 第 2 话 7.5 → 7.25 → 7.5
        assertEquals(
            7.5f,
            EpisodeReviewMergePolicy.average(
                listOf(entry(1, "第 1 话", score = 7f), entry(2, "第 2 话", score = 7.5f)),
                half,
            )!!,
            0.0001f,
        )

        val integer = EpisodeReviewMergePolicy.Rounding.HALF_UP_INTEGER
        assertEquals(8f, EpisodeReviewMergePolicy.roundHalfUp(7.5f, 1f), 0.0001f)
        assertEquals(7f, EpisodeReviewMergePolicy.roundHalfUp(7.49f, 1f), 0.0001f)
        // 整数制下 7.25 → 7
        assertEquals(
            7f,
            EpisodeReviewMergePolicy.average(
                listOf(entry(1, "第 1 话", score = 7f), entry(2, "第 2 话", score = 7.5f)),
                integer,
            )!!,
            0.0001f,
        )
    }

    @Test
    fun `舍入结果被夹在 0 到 10`() {
        assertEquals(10f, EpisodeReviewMergePolicy.roundHalfUp(10.4f, 0.5f), 0.0001f)
        assertEquals(0f, EpisodeReviewMergePolicy.roundHalfUp(-1.2f, 0.5f), 0.0001f)
        // 非法步长不崩，退化为 0
        assertEquals(0f, EpisodeReviewMergePolicy.roundHalfUp(5f, 0f))
    }

    // ==================== 自动块 ====================

    @Test
    fun `只写短评模式只输出有短评的集_并折叠换行`() {
        val entries = listOf(
            entry(1, "第 1 话", score = 8.5f, comment = "很好看"),
            entry(2, "第 2 话", score = 7f, comment = null),
            entry(3, "第 3 话", score = null, comment = "这句\n有\n换行"),
        )
        val block = EpisodeReviewMergePolicy.buildBlock(
            entries,
            EpisodeReviewMergePolicy.Mode.COMMENTS_ONLY,
        )!!
        assertTrue(block.startsWith(EpisodeReviewMergePolicy.BEGIN_MARKER))
        assertTrue(block.endsWith(EpisodeReviewMergePolicy.END_MARKER))
        assertTrue("有分数时才显示分数：$block", block.contains("第 1 话 8.5 — 很好看"))
        assertTrue("没写短评的集不出现：$block", !block.contains("第 2 话"))
        assertTrue("换行必须折叠成一行：$block", block.contains("第 3 话 — 这句 有 换行"))
        assertTrue("只评论模式下不写均分：$block", !block.contains("平均"))
    }

    @Test
    fun `没有任何短评时只评论模式返回 null`() {
        assertNull(
            EpisodeReviewMergePolicy.buildBlock(
                listOf(entry(1, "第 1 话", score = 9f)),
                EpisodeReviewMergePolicy.Mode.COMMENTS_ONLY,
            ),
        )
    }

    @Test
    fun `只均分模式不输出分集行`() {
        val entries = listOf(
            entry(1, "第 1 话", score = 8f, comment = "好看"),
            entry(2, "第 2 话", score = 6f, comment = "一般"),
            entry(3, "第 3 话", score = null),
        )
        val block = EpisodeReviewMergePolicy.buildBlock(entries, EpisodeReviewMergePolicy.Mode.AVERAGE_ONLY)!!
        assertTrue("不输出分集行：$block", !block.contains("第 1 话"))
        assertTrue(block.contains("平均 7 分（2 集有评分 / 共 3 集）"))
    }

    @Test
    fun `两者模式_分母只数有效评分_总数数全部集`() {
        val entries = listOf(
            entry(1, "第 1 话", score = 9f, comment = "神回"),
            entry(2, "第 2 话", score = null, comment = "还没评分"),
            entry(3, "第 3 话", score = 5f),
        )
        val block = EpisodeReviewMergePolicy.buildBlock(entries, EpisodeReviewMergePolicy.Mode.BOTH)!!
        assertTrue(block.contains("第 1 话 9 — 神回"))
        assertTrue("未打分但有短评的集也要出现：$block", block.contains("第 2 话 — 还没评分"))
        assertTrue(block.contains("平均 7 分（2 集有评分 / 共 3 集）"))
    }

    // ==================== 合并（手写保护 + 幂等） ====================

    @Test
    fun `手写内容原样保留_自动块追加在后面`() {
        val merged = EpisodeReviewMergePolicy.mergeInto("我自己写的感想。", "BLOCK")!!
        assertTrue(merged.startsWith("我自己写的感想。"))
        assertTrue(merged.endsWith("BLOCK"))
        // 中文与英文都不做任何「智能改写」
        assertTrue(merged.contains("我自己写的感想。\n\nBLOCK"))
    }

    @Test
    fun `重复执行是幂等的_只保留一个自动块`() {
        val block = EpisodeReviewMergePolicy.buildBlock(
            listOf(entry(1, "第 1 话", score = 8f, comment = "好")),
            EpisodeReviewMergePolicy.Mode.BOTH,
        )!!
        val once = EpisodeReviewMergePolicy.mergeInto("手写", block)
        val twice = EpisodeReviewMergePolicy.mergeInto(once, block)
        assertEquals(once, twice)
        assertEquals(1, twice!!.split(EpisodeReviewMergePolicy.BEGIN_MARKER).size - 1)
    }

    @Test
    fun `源评价清空后能整块移除_手写部分留着`() {
        val block = EpisodeReviewMergePolicy.buildBlock(
            listOf(entry(1, "第 1 话", score = 8f, comment = "好")),
            EpisodeReviewMergePolicy.Mode.BOTH,
        )!!
        val withBlock = EpisodeReviewMergePolicy.mergeInto("手写感想", block)!!
        assertTrue(EpisodeReviewMergePolicy.hasAutoBlock(withBlock))
        // 第二次生成时已经没有分集评价 → block 为 null → 只移除旧块
        val removed = EpisodeReviewMergePolicy.mergeInto(withBlock, null)
        assertEquals("手写感想", removed)
        assertFalse(EpisodeReviewMergePolicy.hasAutoBlock(removed))
    }

    @Test
    fun `没有手写内容时不留空壳`() {
        assertNull(EpisodeReviewMergePolicy.mergeInto(null, null))
        assertNull(EpisodeReviewMergePolicy.mergeInto("   ", null))
        assertNull(EpisodeReviewMergePolicy.mergeInto("", null))
    }

    @Test
    fun `只有开始标记的残缺块也按块处理_只有结束标记则不动`() {
        val broken = "手写\n" + EpisodeReviewMergePolicy.BEGIN_MARKER + "\n第 1 话 好"
        assertEquals("手写", EpisodeReviewMergePolicy.stripBlock(broken).trim())
        // 只有结束标记：不猜着删用户的字
        val endOnly = "手写\n" + EpisodeReviewMergePolicy.END_MARKER
        assertEquals(endOnly, EpisodeReviewMergePolicy.stripBlock(endOnly))
    }

    @Test
    fun `部分分集评分不影响其它集的行`() {
        val entries = listOf(
            entry(1, "第 1 话", score = 8f, comment = "一"),
            entry(2, "第 2 话", score = null),
            entry(3, "第 3 话", score = 9f, comment = "三"),
        )
        val block = EpisodeReviewMergePolicy.buildBlock(entries, EpisodeReviewMergePolicy.Mode.BOTH)!!
        assertTrue(block.contains("第 1 话 8 — 一"))
        assertTrue(block.contains("第 3 话 9 — 三"))
        assertTrue("第 2 话既无分也无短评 → 不出现：$block", !block.contains("第 2 话"))
        assertTrue(block.contains("平均 8.5 分（2 集有评分 / 共 3 集）"))
    }

    // ==================== 覆盖确认 ====================

    @Test
    fun `已有手动评分时必须显式确认才覆盖`() {
        assertFalse(EpisodeReviewMergePolicy.needsOverwriteConfirmation(null))
        assertFalse(EpisodeReviewMergePolicy.needsOverwriteConfirmation(0f))
        assertTrue(EpisodeReviewMergePolicy.needsOverwriteConfirmation(8f))

        val avg = 7.5f
        // 只评论：不动评分
        assertNull(EpisodeReviewMergePolicy.resolveRating(EpisodeReviewMergePolicy.Mode.COMMENTS_ONLY, avg, 8f, true))
        // 已有评分且未确认：不覆盖
        assertNull(EpisodeReviewMergePolicy.resolveRating(EpisodeReviewMergePolicy.Mode.BOTH, avg, 8f, false))
        // 确认后才写
        assertEquals(avg, EpisodeReviewMergePolicy.resolveRating(EpisodeReviewMergePolicy.Mode.BOTH, avg, 8f, true))
        // 本来没评分：直接写，不需要确认
        assertEquals(avg, EpisodeReviewMergePolicy.resolveRating(EpisodeReviewMergePolicy.Mode.BOTH, avg, null, false))
        // 没有均分可写：null
        assertNull(EpisodeReviewMergePolicy.resolveRating(EpisodeReviewMergePolicy.Mode.BOTH, null, null, true))
    }

    /** compose 是界面唯一入口：一次算清感想、评分与计数。 */
    @Test
    fun `compose 组装结果_只均分模式不碰感想`() {
        val entries = listOf(
            entry(1, "第 1 话", score = 8f, comment = "好"),
            entry(2, "第 2 话", score = 9f, comment = null),
        )
        val both = EpisodeReviewMergePolicy.compose(
            entries = entries,
            existing = "手写",
            mode = EpisodeReviewMergePolicy.Mode.BOTH,
            rounding = EpisodeReviewMergePolicy.Rounding.HALF_UP_HALF,
            currentRating = null,
            overwriteConfirmed = false,
        )
        assertTrue(both.impression!!.contains("手写"))
        assertTrue(both.impression!!.contains("第 1 话 8 — 好"))
        assertEquals(8.5f, both.average!!, 0.001f)
        assertEquals(2, both.ratedCount)
        assertEquals(1, both.commentedCount)
        assertEquals(2, both.totalCount)

        val avgOnly = EpisodeReviewMergePolicy.compose(
            entries = entries,
            existing = "手写",
            mode = EpisodeReviewMergePolicy.Mode.AVERAGE_ONLY,
            rounding = EpisodeReviewMergePolicy.Rounding.HALF_UP_INTEGER,
            currentRating = 9f,
            overwriteConfirmed = false,
        )
        assertEquals("只均分模式不改感想", "手写", avgOnly.impression)
        assertNull("已有 9 分且未确认 → 不覆盖", avgOnly.average)
    }

    // ==================== 集号标签 ====================

    @Test
    fun `集号标签_缺号时退回位置序号_空标题不留空格`() {
        assertEquals("第 3 集 序曲", EpisodeReviewMergePolicy.entryLabel(3.0, "序曲", fallbackIndex = 0))
        // Bangumi 的 ep 为 0（特别篇/未编号）→ 用位置序号（从 1 数）
        assertEquals("第 5 集 特别篇", EpisodeReviewMergePolicy.entryLabel(0.0, "特别篇", fallbackIndex = 4))
        // 标题为空：只留集号，不留尾随空格
        assertEquals("第 1 集", EpisodeReviewMergePolicy.entryLabel(0.0, "  ", fallbackIndex = 0))
        assertEquals("第 7 集", EpisodeReviewMergePolicy.entryLabel(7.0, null, fallbackIndex = 99))
        // 小数章节号按整数截断（合并块里不适合出现 2.5 这种号）
        assertEquals("第 2 集", EpisodeReviewMergePolicy.entryLabel(2.5, null, fallbackIndex = 0))
        // 单位由调用方按类型给（漫画=话、音乐=首）
        assertEquals("第 2 话", EpisodeReviewMergePolicy.entryLabel(2.0, null, fallbackIndex = 0, unit = "话"))
        assertEquals("第 1 首 主题曲", EpisodeReviewMergePolicy.entryLabel(1.0, "主题曲", fallbackIndex = 0, unit = "首"))
    }

    @Test
    fun `分数显示整数不带小数点`() {
        assertEquals("8", EpisodeReviewMergePolicy.formatScore(8f))
        assertEquals("8.5", EpisodeReviewMergePolicy.formatScore(8.5f))
        assertEquals("7", EpisodeReviewMergePolicy.formatScore(7.0f))
    }
}
