package com.otakup.niriko.data.discover

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DiscoveryFeed 的纯函数单测（第 6 轮 §3.5 / §10 的单测清单）。
 *
 * 覆盖：合并去重、质量门槛、类内归一、多样性重排（窗口上限 / 连续上限 / 小类保底）、
 * 不填充、空候选、确定性、分组辅助。
 */
class DiscoveryFeedTest {

    private fun subject(
        id: Long,
        type: SubjectType = SubjectType.ANIME,
        ratingTotal: Int? = 1000,
        ratingScore: Float = 8.0f,
        airDate: String? = null,
    ) = SubjectEntity(
        subjectId = id,
        title = "作品" + id,
        type = type,
        airDate = airDate,
        ratingScore = ratingScore,
        ratingTotal = ratingTotal,
    )

    private fun pool(
        type: SubjectType,
        from: Long,
        count: Int,
        ratingTotal: Int = 1000,
    ): List<SubjectEntity> = (0 until count).map { subject(from + it, type = type, ratingTotal = ratingTotal) }

    private fun group(
        type: SubjectType,
        from: Long,
        count: Int,
        ratingTotal: Int = 1000,
    ) = DiscoveryFeed.TypeCandidates(type, pool(type, from, count, ratingTotal))

    /** 五类各 20 条：与「发现页 · 全部」的真实候选池（5 类 x 30）同形。 */
    private fun balancedGroups(): List<DiscoveryFeed.TypeCandidates> = listOf(
        group(SubjectType.ANIME, 100, 20),
        group(SubjectType.BOOK, 200, 20),
        group(SubjectType.GAME, 300, 20),
        group(SubjectType.MUSIC, 400, 20),
        group(SubjectType.REAL, 500, 20),
    )

    /** 断言窗口内同类 <= maxInWindow、连续同类 <= maxRun。 */
    private fun assertDiversity(
        items: List<DiscoveryFeed.Entry>,
        windowSize: Int = DiscoveryFeed.WINDOW_SIZE,
        maxInWindow: Int = DiscoveryFeed.MAX_SAME_TYPE_IN_WINDOW,
        maxRun: Int = DiscoveryFeed.MAX_CONSECUTIVE_SAME_TYPE,
    ) {
        val types = items.map { it.type }
        for (i in types.indices) {
            val from = (i - windowSize + 1).coerceAtLeast(0)
            val inWindow = types.subList(from, i + 1).count { it == types[i] }
            assertTrue(
                "第 " + i + " 条起窗口内 " + types[i] + " 出现 " + inWindow + " 次，超过上限 " + maxInWindow,
                inWindow <= maxInWindow,
            )
        }
        var run = 1
        for (i in 1 until types.size) {
            if (types[i] == types[i - 1]) {
                run++
                assertTrue("连续 " + run + " 条同类，超过上限 " + maxRun, run <= maxRun)
            } else {
                run = 1
            }
        }
    }

    // ==================== 合并去重 ====================

    @Test
    fun `合并去重_同一 subjectId 只保留首次出现`() {
        val result = DiscoveryFeed.build(
            listOf(
                DiscoveryFeed.TypeCandidates(SubjectType.ANIME, listOf(subject(1), subject(2))),
                DiscoveryFeed.TypeCandidates(
                    SubjectType.REAL,
                    listOf(subject(2, type = SubjectType.REAL), subject(3, type = SubjectType.REAL)),
                ),
            ),
        )
        assertEquals(1, result.duplicateCount)
        assertEquals(3, result.candidateCount)
        assertEquals(3, result.size)
        assertEquals(listOf(1L, 2L, 3L), result.subjects.map { it.subjectId }.sorted())
        assertEquals(listOf(1L, 2L), result.subjects.filter { it.type == SubjectType.ANIME }.map { it.subjectId })
    }

    // ==================== 质量门槛 ====================

    @Test
    fun `质量门槛挡掉低于阈值与无评分人数的候选`() {
        val result = DiscoveryFeed.build(
            listOf(
                DiscoveryFeed.TypeCandidates(
                    SubjectType.ANIME,
                    listOf(
                        subject(1, ratingTotal = 100),
                        subject(2, ratingTotal = 99),
                        subject(3, ratingTotal = null),
                    ),
                ),
            ),
        )
        assertEquals(2, result.rejectedByQuality)
        assertEquals(listOf(1L), result.subjects.map { it.subjectId })
    }

    @Test
    fun `质量门槛可调_默认100`() {
        assertEquals(100, DiscoveryFeed.MIN_RATING_TOTAL)
        val subjects = listOf(subject(1, ratingTotal = 30), subject(2, ratingTotal = 200))
        assertEquals(1, DiscoveryFeed.build(listOf(DiscoveryFeed.TypeCandidates(SubjectType.ANIME, subjects))).size)
        val relaxed = DiscoveryFeed.build(
            listOf(DiscoveryFeed.TypeCandidates(SubjectType.ANIME, subjects)),
            DiscoveryFeed.Params(minRatingTotal = 10),
        )
        assertEquals(2, relaxed.size)
    }

    // ==================== 类内归一化 ====================

    @Test
    fun `类内归一化位置覆盖0到1`() {
        val result = DiscoveryFeed.build(listOf(group(SubjectType.ANIME, 100, 5)))
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75, 1.0), result.items.map { it.normalizedPosition })
        assertEquals(listOf(0, 1, 2, 3, 4), result.items.map { it.typeRank })
    }

    @Test
    fun `单条候选的类内位置为0`() {
        val result = DiscoveryFeed.build(listOf(group(SubjectType.ANIME, 100, 1)))
        assertEquals(listOf(0.0), result.items.map { it.normalizedPosition })
    }

    // ==================== 多样性重排 ====================

    @Test
    fun `五类均衡候选下窗口内同类不超过3且连续不超过2`() {
        val result = DiscoveryFeed.build(balancedGroups())
        assertEquals(30, result.size)
        assertDiversity(result.items)
    }

    @Test
    fun `多样性让五种类型都出现_而不是番剧刷屏`() {
        val result = DiscoveryFeed.build(balancedGroups())
        assertEquals(5, result.typeCounts.keys.size)
        result.typeCounts.forEach { (type, count) ->
            assertTrue("类型 " + type + " 一条都没进", count >= 1)
        }
    }

    @Test
    fun `小类保底_三次元音乐书籍各至少三条`() {
        val groups = listOf(
            group(SubjectType.ANIME, 100, 30),
            group(SubjectType.GAME, 300, 30),
            group(SubjectType.BOOK, 200, 5),
            group(SubjectType.MUSIC, 400, 5),
            group(SubjectType.REAL, 500, 5),
        )
        val result = DiscoveryFeed.build(groups)
        assertEquals(30, result.size)
        assertTrue(result.countOf(SubjectType.BOOK) >= 3)
        assertTrue(result.countOf(SubjectType.MUSIC) >= 3)
        assertTrue(result.countOf(SubjectType.REAL) >= 3)
        assertEquals(9, result.items.count { it.guaranteed })
    }

    @Test
    fun `小类候选不够时不谎报保底`() {
        val groups = listOf(
            group(SubjectType.ANIME, 100, 30),
            DiscoveryFeed.TypeCandidates(
                SubjectType.REAL,
                listOf(subject(500, type = SubjectType.REAL), subject(501, type = SubjectType.REAL)),
            ),
        )
        val result = DiscoveryFeed.build(groups)
        assertEquals("候选不够 3 条时一条都不标保底", 0, result.items.count { it.guaranteed })
        assertTrue("够格的候选仍然会进榜", result.countOf(SubjectType.REAL) >= 1)
        assertTrue(result.countOf(SubjectType.REAL) <= 2)
    }

    @Test
    fun `候选只剩两类时约束让位于不丢条目`() {
        // 只有两类候选时，「窗口内同类 <=3」在数学上无法满足 30 条（10 条窗口里最多放 6 条），
        // 默认策略是放宽约束取满，而不是在 6 条处停住。
        val groups = listOf(group(SubjectType.ANIME, 100, 30), group(SubjectType.REAL, 500, 30))
        val relaxed = DiscoveryFeed.build(groups)
        assertEquals(30, relaxed.size)
    }

    @Test
    fun `严格模式下约束无法满足就提前停止`() {
        val groups = listOf(group(SubjectType.ANIME, 100, 30), group(SubjectType.REAL, 500, 30))
        val strict = DiscoveryFeed.build(groups, DiscoveryFeed.Params(relaxWhenStuck = false))
        assertTrue("严格模式应在窗口约束处停住，实际 " + strict.size, strict.size < 30)
        assertTrue(strict.size > 0)
        assertDiversity(strict.items)
    }

    // ==================== 截断与不填充 ====================

    @Test
    fun `候选不足时不填充`() {
        val result = DiscoveryFeed.build(listOf(group(SubjectType.ANIME, 100, 4)))
        assertEquals(4, result.size)
        assertEquals(4, result.candidateCount)
    }

    @Test
    fun `默认目标30条_可调`() {
        assertEquals(30, DiscoveryFeed.TARGET_SIZE)
        assertEquals(30, DiscoveryFeed.build(balancedGroups()).size)
        assertEquals(7, DiscoveryFeed.build(balancedGroups(), DiscoveryFeed.Params(targetSize = 7)).size)
        assertEquals(0, DiscoveryFeed.build(balancedGroups(), DiscoveryFeed.Params(targetSize = 0)).size)
    }

    @Test
    fun `空候选返回空结果`() {
        val empty = DiscoveryFeed.build(emptyList<DiscoveryFeed.TypeCandidates>())
        assertEquals(0, empty.size)
        assertEquals(0, empty.candidateCount)
        assertEquals(0, empty.duplicateCount)
        assertEquals(0, empty.rejectedByQuality)
        val emptyGroups = DiscoveryFeed.build(listOf(DiscoveryFeed.TypeCandidates(SubjectType.ANIME, emptyList())))
        assertEquals(0, emptyGroups.size)
        assertTrue(emptyGroups.typeCounts.isEmpty())
    }

    @Test
    fun `全部候选都过不了质量门槛时返回空且计数正确`() {
        val result = DiscoveryFeed.build(listOf(group(SubjectType.ANIME, 100, 5, ratingTotal = 10)))
        assertEquals(0, result.size)
        assertEquals(5, result.rejectedByQuality)
    }

    // ==================== 确定性 ====================

    @Test
    fun `同输入同输出_确定性`() {
        val groups = balancedGroups()
        val first = DiscoveryFeed.build(groups)
        val second = DiscoveryFeed.build(groups)
        assertEquals(first.items, second.items)
        assertEquals(first.typeCounts, second.typeCounts)
        assertEquals(first.subjects.map { it.subjectId }, second.subjects.map { it.subjectId })
    }

    @Test
    fun `类型顺序相同则结果相同_与候选池构造方式无关`() {
        val a = DiscoveryFeed.build(
            listOf(
                DiscoveryFeed.TypeCandidates(SubjectType.ANIME, pool(SubjectType.ANIME, 100, 3)),
                DiscoveryFeed.TypeCandidates(SubjectType.MUSIC, pool(SubjectType.MUSIC, 400, 3)),
            ),
        )
        val b = DiscoveryFeed.build(
            listOf(
                DiscoveryFeed.TypeCandidates(SubjectType.ANIME, pool(SubjectType.ANIME, 100, 3)),
                DiscoveryFeed.TypeCandidates(SubjectType.MUSIC, pool(SubjectType.MUSIC, 400, 3)),
            ),
        )
        assertEquals(a.items, b.items)
    }

    // ==================== 分组辅助 ====================

    @Test
    fun `groupByType 保持首次出现顺序且类内不乱序`() {
        val subjects = listOf(subject(1), subject(2, type = SubjectType.MUSIC), subject(3))
        val groups = DiscoveryFeed.groupByType(subjects)
        assertEquals(listOf(SubjectType.ANIME, SubjectType.MUSIC), groups.map { it.type })
        assertEquals(listOf(1L, 3L), groups.first().subjects.map { it.subjectId })
    }

    @Test
    fun `Map 重载按迭代顺序决定优先级`() {
        val byType = linkedMapOf(
            SubjectType.ANIME to listOf(subject(1), subject(2)),
            SubjectType.MUSIC to listOf(subject(3, type = SubjectType.MUSIC)),
        )
        val result = DiscoveryFeed.build(byType)
        assertEquals(3, result.size)
        assertEquals(listOf(1L, 3L, 2L), result.subjects.map { it.subjectId })
    }
}
