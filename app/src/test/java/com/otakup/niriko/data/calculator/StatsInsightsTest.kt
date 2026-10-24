package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.CompletionCaliber
import com.otakup.niriko.data.model.stats.HygieneKind
import com.otakup.niriko.data.model.stats.MonthlyStats
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 5.3 一句话对比 / 5.5 完成率口径 / C-6 收藏健康度。 */
class StatsInsightsTest {

    private val now = 1_800_000_000_000L

    private fun cws(
        id: Long,
        status: WatchStatus = WatchStatus.WATCHING,
        rating: Float? = null,
        tags: List<String> = emptyList(),
        updateTime: Long = now,
    ) = CollectionWithSubject(
        collection = CollectionEntity(
            subjectId = id, status = status, rating = rating,
            personalTags = tags, updateTime = updateTime,
        ),
        subject = SubjectEntity(subjectId = id, title = "S" + id, type = SubjectType.ANIME),
    )

    private fun monthOf(index: Int, count: Int) = MonthlyStats(
        label = "2026-" + (index + 1).toString().padStart(2, '0'),
        year = 2026,
        month = index + 1,
        count = count,
    )

    private fun trend(vararg counts: Int): List<MonthlyStats> =
        counts.mapIndexed { index, count -> monthOf(index, count) }

    // ---------- 5.3 一句话对比 ----------

    @Test
    fun compare_insufficient_when_both_sides_are_tiny() {
        assertEquals(CountComparison.Insufficient, StatsNarratives.compareCount(2, 1))
    }

    @Test
    fun compare_up_and_down_deltas() {
        assertEquals(CountComparison.Up(5), StatsNarratives.compareCount(12, 7))
        assertEquals(CountComparison.Down(5), StatsNarratives.compareCount(7, 12))
        assertEquals(CountComparison.Flat, StatsNarratives.compareCount(9, 9))
    }

    @Test
    fun narrative_is_null_when_not_enough_months() {
        assertNull(StatsNarratives.monthlyNarrative(emptyList()))
        assertNull(StatsNarratives.monthlyNarrative(trend(5)))
    }

    @Test
    fun narrative_reports_month_over_month_delta() {
        assertEquals("比上月多 5 部", StatsNarratives.monthlyNarrative(trend(10, 15)))
        assertEquals("比上月少 3 部", StatsNarratives.monthlyNarrative(trend(10, 7)))
    }

    @Test
    fun narrative_prefers_declining_streak() {
        assertEquals(3, StatsNarratives.decliningStreak(trend(10, 8, 6, 4)))
        assertEquals("连续 3 个月下降", StatsNarratives.monthlyNarrative(trend(10, 8, 6, 4)))
    }

    // ---------- 5.5 完成率口径 ----------

    @Test
    fun completion_detail_counts_each_bucket() {
        val items = listOf(
            cws(1, WatchStatus.COMPLETED),
            cws(2, WatchStatus.PLAN_TO_WATCH),
            cws(3, WatchStatus.WATCHING),
            cws(4, WatchStatus.DROPPED),
        )
        val detail = CompletionCaliberCalculator.detail(items)
        assertEquals(1, detail.completed)
        assertEquals(4, detail.total)
        assertEquals(1, detail.planToWatch)
        assertEquals(3, detail.excludePlanDenominator)
        assertEquals(3, detail.strictDenominator)
        assertEquals(1f / 3f, detail.rate(CompletionCaliber.EXCLUDE_PLAN), 0.0001f)
        assertEquals(1f / 4f, detail.rate(CompletionCaliber.ALL), 0.0001f)
        assertEquals(1f / 3f, detail.rate(CompletionCaliber.STRICT), 0.0001f)
    }

    @Test
    fun completion_rate_is_zero_on_empty_library() {
        val detail = CompletionCaliberCalculator.detail(emptyList())
        assertEquals(0f, detail.rate(CompletionCaliber.ALL), 0.0001f)
        assertEquals(0f, detail.rate(CompletionCaliber.EXCLUDE_PLAN), 0.0001f)
    }

    // ---------- C-6 收藏健康度 ----------

    @Test
    fun hygiene_counts_the_four_kinds() {
        val stale = now - TimeUnit.DAYS.toMillis(120)
        val planStale = now - TimeUnit.DAYS.toMillis(400)
        val items = listOf(
            cws(1, WatchStatus.WATCHING, updateTime = stale),
            cws(2, WatchStatus.WATCHING, updateTime = now),
            cws(3, WatchStatus.COMPLETED, rating = null),
            cws(4, WatchStatus.COMPLETED, rating = 8f, tags = listOf("x")),
            cws(5, WatchStatus.PLAN_TO_WATCH, updateTime = planStale),
        )
        val report = HygieneCalculator.report(items, now)
        assertEquals(4, report.issues.size)
        assertEquals(
            mapOf(
                HygieneKind.WATCHING_STALE to 1,
                HygieneKind.COMPLETED_UNRATED to 1,
                HygieneKind.UNTAGGED to 4,
                HygieneKind.PLAN_STALE to 1,
            ),
            report.issues.associate { it.kind to it.count },
        )
        // 四类都非零（无标签那类覆盖 4 部，与"停滞"类有重叠）
        assertEquals(4, report.actionableCount)
        assertEquals(7, report.totalCount)
    }

    @Test
    fun hygiene_is_empty_for_healthy_library() {
        val items = listOf(cws(1, WatchStatus.COMPLETED, rating = 9f, tags = listOf("神作")))
        val report = HygieneCalculator.report(items, now)
        assertEquals(0, report.actionableCount)
        assertEquals(0, report.totalCount)
    }
}
