package com.otakup.niriko.ui.stats

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.SubjectType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4.7 / 4.10 停播·延期判定的纯函数测试。
 *
 * 规则只有两条，但都关乎「用户会不会白等一集」：
 * - 延期 = 真实集日期比「首集 + N 周」晚；
 * - 停播 = 每周放送日落在 > 10 天的两集缺口里。
 * 集数据不足两集一律不判定（没数据不猜）。
 */
class CalendarAiringAdjustTest {

    private val first = LocalDate.of(2026, 10, 2)

    private fun subject(id: Long, total: Int?) = SubjectEntity(
        subjectId = id,
        title = "作品$id",
        type = SubjectType.ANIME,
        totalEpisodes = total,
    )

    private fun ep(sort: Double, airdate: String?, type: Int = 0) = EpisodeInfo(
        id = sort.toLong(),
        name = "ep$sort",
        nameCn = null,
        desc = null,
        ep = sort,
        sort = sort,
        airdate = airdate,
        duration = null,
        type = type,
    )

    @Test
    fun mainEpisodeDatesKeepOnlyMainEpisodesWithValidAirdates() {
        val dates = mainEpisodeDates(
            listOf(
                ep(1.0, "2026-10-02"),
                ep(2.0, null),
                ep(3.0, "2026-10-16"),
                ep(1.0, "2026-10-02"), // 重复
                ep(1.0, "2026-10-09", type = 1), // SP 不算
                ep(9.0, "不是日期"),
            ),
        )
        assertEquals(listOf(first, LocalDate.of(2026, 10, 16)), dates)
    }

    @Test
    fun weeklyCadenceHasNoAdjustment() {
        val dates = listOf(first, first.plusWeeks(1), first.plusWeeks(2))
        assertTrue(dates.all { airingAdjustFor(it, dates) == null })
    }

    @Test
    fun lateEpisodeIsMarkedDelayedWithDays() {
        val dates = listOf(first, first.plusWeeks(2)) // 第 2 集原定 10/09，实际 10/16
        val adjust = airingAdjustFor(LocalDate.of(2026, 10, 16), dates)
        assertEquals(AiringAdjustKind.DELAYED, adjust?.kind)
        assertEquals("延", adjust?.label)
        assertTrue(adjust!!.detail.contains("7 天"))
    }

    @Test
    fun skippedWeeklySlotInsideLongGapIsMarkedHiatus() {
        // 10/02 之后隔了 21 天到 10/23 → 10/09 与 10/16 都是停播
        val dates = listOf(first, LocalDate.of(2026, 10, 23))
        val hiatus = airingAdjustFor(LocalDate.of(2026, 10, 9), dates)
        assertEquals(AiringAdjustKind.HIATUS, hiatus?.kind)
        assertEquals("停", hiatus?.label)
        assertNull("非放送日不标停播", airingAdjustFor(LocalDate.of(2026, 10, 10), dates))
        // 恢复那一集本身是「延期」（比每周节奏晚了两周），不是停播
        assertEquals(AiringAdjustKind.DELAYED, airingAdjustFor(LocalDate.of(2026, 10, 23), dates)?.kind)
    }

    @Test
    fun singleEpisodeOrEmptyDataNeverGuesses() {
        assertNull(airingAdjustFor(first, emptyList()))
        assertNull(airingAdjustFor(first, listOf(first)))
        assertTrue(airingAdjustsForRange(emptyList(), first, first.plusDays(31)).isEmpty())
    }

    @Test
    fun rangeScanOnlyCoversRequestedMonth() {
        val dates = listOf(first, LocalDate.of(2026, 10, 23))
        val october = airingAdjustsForRange(dates, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertTrue(october.containsKey(LocalDate.of(2026, 10, 9)))
        assertTrue(october.containsKey(LocalDate.of(2026, 10, 16)))
        val november = airingAdjustsForRange(dates, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertTrue("11 月不应看到 10 月的停播", november.isEmpty())
    }

    @Test
    fun mergedAdjustsGroupByDate() {
        val merged = mergeAiringAdjusts(
            subjects = listOf(subject(1, total = 2), subject(2, total = 2)),
            episodeDatesBySubject = mapOf(
                1L to listOf(first, LocalDate.of(2026, 10, 23)),
                2L to listOf(first, first.plusWeeks(1)),
            ),
            monthStart = LocalDate.of(2026, 10, 1),
            monthEnd = LocalDate.of(2026, 10, 31),
        )
        // 1 号作品在 10/09 停播；2 号作品正常周更，不该给 10/09 加角标
        assertEquals(1, merged[LocalDate.of(2026, 10, 9)]?.size)
        assertEquals(AiringAdjustKind.HIATUS, merged[LocalDate.of(2026, 10, 9)]?.first()?.kind)
    }

    @Test
    fun incompleteEpisodeDataNeverMarksAnything() {
        val dates = listOf(first, LocalDate.of(2026, 10, 23))
        assertNull(airingAdjustFor(LocalDate.of(2026, 10, 9), dates, complete = false))
        assertTrue(
            airingAdjustsForRange(dates, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), complete = false).isEmpty(),
        )
        // 声明 12 集、只拿到 2 集 → 合并时整部作品跳过（缺集 ≠ 停播）
        assertTrue(
            mergeAiringAdjusts(
                subjects = listOf(subject(1, total = 12)),
                episodeDatesBySubject = mapOf(1L to dates),
                monthStart = LocalDate.of(2026, 10, 1),
                monthEnd = LocalDate.of(2026, 10, 31),
            ).isEmpty(),
        )
    }
}
