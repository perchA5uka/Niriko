package com.otakup.niriko.data.seasonal

import com.otakup.niriko.data.discover.DiscoveryFeed
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.CalendarDaySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 「当季热门」纯计算单测（第 5 轮 D26/D27）。
 *
 * 重点覆盖**用户报告的具体缺陷**：
 * 《假面骑士zzz》是每周一集、播一年的特摄（三次元），
 * 改造前因为「只收本季度开播」而完全不出现在当季热门里。
 */
class SeasonalTrendingCalculatorTest {

    private val today = LocalDate.of(2026, 3, 1)

    private fun subject(
        id: Long,
        type: SubjectType = SubjectType.ANIME,
        airDate: String? = null,
        totalEpisodes: Int? = null,
        platform: String? = null,
        ratingTotal: Int? = null,
        ratingScore: Float? = null,
        rank: Int? = null,
        airWeekday: Int? = null,
    ) = SubjectEntity(
        subjectId = id,
        title = "title-$id",
        type = type,
        airDate = airDate,
        totalEpisodes = totalEpisodes,
        platform = platform,
        ratingTotal = ratingTotal,
        ratingScore = ratingScore,
        rank = rank,
        airWeekday = airWeekday,
    )

    // ==================== 在播判定（核心回归） ====================

    @Test
    fun `long running weekly show is still airing`() {
        // 播一年的特摄：300 天前开播、共 50 话 → 估算完结日仍在未来
        val zzz = subject(1L, SubjectType.REAL, today.minusDays(300).toString(), totalEpisodes = 50)
        assertTrue(SeasonalTrendingCalculator.isProbablyAiring(zzz, today))
    }

    @Test
    fun `short show that already ended is not airing`() {
        // 一季番：300 天前开播、共 12 话 → 早就完结
        val old = subject(2L, airDate = today.minusDays(300).toString(), totalEpisodes = 12)
        assertFalse(SeasonalTrendingCalculator.isProbablyAiring(old, today))
    }

    @Test
    fun `show without episode count uses the wide window`() {
        // 无总集数的长期连载：400 天窗口内算在播
        val ongoing = subject(3L, airDate = today.minusDays(300).toString())
        assertTrue(SeasonalTrendingCalculator.isProbablyAiring(ongoing, today))
        // 超出窗口则不算
        val ancient = subject(4L, airDate = today.minusDays(500).toString())
        assertFalse(SeasonalTrendingCalculator.isProbablyAiring(ancient, today))
    }

    @Test
    fun `movie and ova are excluded and upcoming is excluded`() {
        val movie = subject(5L, airDate = today.minusDays(10).toString(), platform = "剧场版")
        assertFalse(SeasonalTrendingCalculator.isProbablyAiring(movie, today))
        val upcoming = subject(6L, airDate = today.plusDays(30).toString(), totalEpisodes = 12)
        assertFalse(SeasonalTrendingCalculator.isProbablyAiring(upcoming, today))
    }

    @Test
    fun `missing air date is never airing`() {
        assertFalse(SeasonalTrendingCalculator.isProbablyAiring(subject(7L, totalEpisodes = 12), today))
    }

    // 第 6 轮返工（用户复核）：`progress()` / `SeasonalProgress` 与「第 N 话」展示整体移除，
    // 相应用例一并删除（当季热门回到旧版的纯卡片列表）。

    // ==================== 日历 / 检索 两条来源 ====================

    @Test
    fun `calendar items are confirmed and carry the weekday`() {
        val calendar = listOf(
            CalendarDaySchedule(
                DayOfWeek.SUNDAY,
                listOf(subject(20L, SubjectType.REAL, today.minusDays(200).toString(), totalEpisodes = 50)),
            ),
            // 同一条目出现在两天：只保留一条（distinctBy subjectId）
            CalendarDaySchedule(
                DayOfWeek.MONDAY,
                listOf(subject(20L, SubjectType.REAL, today.minusDays(200).toString(), totalEpisodes = 50)),
            ),
        )
        val items = SeasonalTrendingCalculator.fromCalendar(calendar, today)
        assertEquals(1, items.size)
        assertTrue(items[0].confirmed)
        assertEquals(DayOfWeek.SUNDAY, items[0].weekday)
    }

    @Test
    fun `calendar keeps an airing movie because the calendar is authoritative`() {
        val calendar = listOf(
            CalendarDaySchedule(DayOfWeek.FRIDAY, listOf(subject(21L, platform = "剧场版", airDate = today.toString()))),
        )
        assertEquals(1, SeasonalTrendingCalculator.fromCalendar(calendar, today).size)
    }

    @Test
    fun `search items are filtered by the airing rule`() {
        val subjects = listOf(
            subject(30L, SubjectType.REAL, today.minusDays(300).toString(), totalEpisodes = 50),
            subject(31L, airDate = today.minusDays(300).toString(), totalEpisodes = 12),
        )
        val items = SeasonalTrendingCalculator.fromSearch(subjects, today)
        assertEquals(listOf(30L), items.map { it.subject.subjectId })
        assertFalse(items[0].confirmed)
    }

    @Test
    fun `merge prefers the calendar entry for the same id`() {
        val calendarItems = SeasonalTrendingCalculator.fromCalendar(
            listOf(CalendarDaySchedule(DayOfWeek.SUNDAY, listOf(subject(40L, airDate = today.toString())))),
            today,
        )
        val fallbackItems = SeasonalTrendingCalculator.fromSearch(
            listOf(subject(40L, airDate = today.toString(), totalEpisodes = 12)),
            today,
        )
        val merged = SeasonalTrendingCalculator.merge(calendarItems, fallbackItems)
        assertEquals(1, merged.size)
        assertTrue(merged[0].confirmed)
        assertEquals(DayOfWeek.SUNDAY, merged[0].weekday)
    }

    // ==================== 类型 / 排序 / 不填充 ====================

    @Test
    fun `type filter follows the top type row mapping`() {
        // 第 6 轮 §2.4：当季热门不再有自己的类型 chips，类型统一由顶部类型行驱动，
        // SeasonalTypes 只负责把「类型行的选中值」翻译成要查的 Bangumi type 集合。
        val items = listOf(
            SeasonalItem(subject(50L, SubjectType.ANIME), confirmed = true),
            SeasonalItem(subject(51L, SubjectType.REAL), confirmed = true),
            SeasonalItem(subject(52L, SubjectType.BOOK), confirmed = true),
        )
        assertEquals(3, SeasonalTrendingCalculator.filterByTypes(items, SeasonalTypes.ALL).size)
        assertEquals(
            1,
            SeasonalTrendingCalculator.filterByTypes(items, listOf(SeasonalTypes.ANIME)).size,
        )
        assertEquals(
            51L,
            SeasonalTrendingCalculator.filterByTypes(items, listOf(SeasonalTypes.REAL))[0].subject.subjectId,
        )
        // 类型行「全部」= 5 类全查（动画/书籍/游戏/音乐/三次元）
        assertEquals(listOf(2, 1, 4, 3, 6), SeasonalTypes.ALL)
        assertEquals(SeasonalTypes.ALL, SeasonalTypes.of(null))
        assertEquals(listOf(4), SeasonalTypes.of(4))
        // 空集合 = 什么都不留（调用方不该传空）
        assertTrue(SeasonalTrendingCalculator.filterByTypes(items, emptyList()).isEmpty())
    }

    // ==================== 人气选取（第 6 轮 §2.3 A 方案） ====================

    @Test
    fun `select takes the top 30 by popularity and never the whole list`() {
        // 120 条候选（动画 60 + 三次元 60），全部过质量门槛。
        // 改造前这里是「全量 300 条」的放送清单 —— 这就是用户说「展示有什么作品上线」的根源。
        val items = buildList {
            (1L..60L).forEach { add(SeasonalItem(subject(1_000L + it, SubjectType.ANIME, ratingTotal = 10_000 - it.toInt()))) }
            (1L..60L).forEach { add(SeasonalItem(subject(2_000L + it, SubjectType.REAL, ratingTotal = 5_000 - it.toInt()))) }
        }
        val selection = SeasonalTrendingCalculator.select(items, SeasonalTypes.ALL, SeasonalSort.HEAT)
        assertEquals(DiscoveryFeed.TARGET_SIZE, selection.items.size)
        assertTrue("必须截断成前 30，而不是把 120 条全倒出来", selection.items.size < items.size)
        assertEquals("候选 N 部 = 门槛前的候选数", 120, selection.candidateCount)
        assertTrue("类内最热的那条必须在", selection.items.any { it.subject.subjectId == 1_001L })
    }

    @Test
    fun `select applies the rating total quality gate`() {
        val items = listOf(
            SeasonalItem(subject(1L, ratingTotal = 10)),
            SeasonalItem(subject(2L, ratingTotal = 99)),
            SeasonalItem(subject(3L, ratingTotal = 100)),
            SeasonalItem(subject(4L)),
        )
        val selection = SeasonalTrendingCalculator.select(items, SeasonalTypes.ALL, SeasonalSort.HEAT)
        assertEquals(listOf(3L), selection.items.map { it.subject.subjectId })
        assertEquals(3, selection.rejectedByQuality)
        assertEquals(4, selection.candidateCount)
    }

    @Test
    fun `select keeps the minority type alive thanks to the diversity guarantee`() {
        // 动画 40 条 + 三次元 4 条（全部过门槛）：没有多样性约束时小类会被挤没，
        // 「长连载的三次元（《假面骑士zzz》那类）看不到」正是第 5 轮的老问题。
        val items = buildList {
            (1L..40L).forEach { add(SeasonalItem(subject(1_000L + it, SubjectType.ANIME, ratingTotal = 10_000 - it.toInt()))) }
            (1L..4L).forEach { add(SeasonalItem(subject(2_000L + it, SubjectType.REAL, ratingTotal = 1_000 - it.toInt()))) }
        }
        val selection = SeasonalTrendingCalculator.select(items, SeasonalTypes.ALL, SeasonalSort.HEAT)
        assertEquals(DiscoveryFeed.TARGET_SIZE, selection.items.size)
        assertTrue(
            "小类（三次元）应当拿满保底 3 条",
            selection.items.count { it.subject.type == SubjectType.REAL } >= 3,
        )
    }

    @Test
    fun `select never fills when candidates are fewer than the target`() {
        val items = (1L..5L).map { SeasonalItem(subject(it, ratingTotal = 1_000)) }
        val selection = SeasonalTrendingCalculator.select(items, SeasonalTypes.ALL, SeasonalSort.HEAT)
        assertEquals(5, selection.items.size)
    }

    @Test
    fun `select on an empty pool returns empty`() {
        val selection = SeasonalTrendingCalculator.select(emptyList(), SeasonalTypes.ALL, SeasonalSort.HEAT)
        assertTrue(selection.items.isEmpty())
        assertEquals(0, selection.candidateCount)
    }

    @Test
    fun `heat sort puts the most rated first and unrated last`() {
        val items = listOf(
            SeasonalItem(subject(60L, ratingTotal = 10)),
            SeasonalItem(subject(61L, ratingTotal = 900)),
            SeasonalItem(subject(62L)),
        )
        val sorted = SeasonalTrendingCalculator.sort(items, SeasonalSort.HEAT)
        assertEquals(listOf(61L, 60L, 62L), sorted.map { it.subject.subjectId })
    }

    @Test
    fun `rank sort keeps unranked items at the end`() {
        val items = listOf(
            SeasonalItem(subject(70L, rank = 500)),
            SeasonalItem(subject(71L)),
            SeasonalItem(subject(72L, rank = 3)),
        )
        assertEquals(listOf(72L, 70L, 71L), SeasonalTrendingCalculator.sort(items, SeasonalSort.RANK).map { it.subject.subjectId })
    }

    @Test
    fun `take does not fill with unrelated data`() {
        // 回归：用户明确否掉「数量不足就用历史排名填充」。
        // 候选 7 条、上限 15 → 结果必须就是 7 条，而不是被撑到 15。
        val items = (1L..7L).map { SeasonalItem(subject(it, ratingTotal = it.toInt())) }
        val shown = SeasonalTrendingCalculator.sort(items, SeasonalSort.HEAT).take(15)
        assertEquals(7, shown.size)
    }

    @Test
    fun `fallback range widens to the configured window`() {
        val range = SeasonalTrendingCalculator.fallbackAirDateRange(today)
        assertEquals(2, range.size)
        assertTrue(range[0].startsWith(">="))
        assertTrue(range[1].startsWith("<"))
        // 起点是 400 天前（不是本季度），这正是长连载能被捞回来的原因
        assertEquals(">=" + today.minusDays(SeasonalTrendingCalculator.DEFAULT_WINDOW_DAYS), range[0])
    }

    // ==================== 「本季」而不是「正在放送」（用户实测倒逼） ====================

    @Test
    fun `a yearly show that finished this quarter still counts`() {
        // 《假面骑士zzz》：2025-09-07 开播、约 50 话 → 估算完结日 2026-08-23。
        // 实测当天 2026-09-19 它**已经播完**，按「必须仍在播」永远进不来，
        // 但它属于「本季刚完结」，必须能看到。
        val zzz = subject(95L, SubjectType.REAL, "2025-09-07", totalEpisodes = 50)
        assertTrue(SeasonalTrendingCalculator.isProbablyAiring(zzz, LocalDate.of(2026, 9, 19)))
        // 到了下个季度（10-12 月）它就该退场，否则一年前的番会永远赖在榜上
        assertFalse(SeasonalTrendingCalculator.isProbablyAiring(zzz, LocalDate.of(2026, 10, 5)))
        assertEquals(LocalDate.of(2026, 10, 1), SeasonalTrendingCalculator.quarterStart(LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun `quarter start follows the 1 4 7 10 boundaries`() {
        assertEquals(LocalDate.of(2026, 1, 1), SeasonalTrendingCalculator.quarterStart(LocalDate.of(2026, 3, 31)))
        assertEquals(LocalDate.of(2026, 4, 1), SeasonalTrendingCalculator.quarterStart(LocalDate.of(2026, 4, 1)))
        assertEquals(LocalDate.of(2026, 7, 1), SeasonalTrendingCalculator.quarterStart(LocalDate.of(2026, 9, 19)))
        assertEquals(LocalDate.of(2026, 10, 1), SeasonalTrendingCalculator.quarterStart(LocalDate.of(2026, 12, 31)))
    }

}
