package com.otakup.niriko.data.calculator

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历「周 / 月」双模的纯日期逻辑。
 *
 * 项目沿用周日起始（与日历表头「日一二三四五六」一致），因此这里的每一周都以周日为第一天，
 * 不依赖系统 locale（[DayOfWeek.firstDayOfWeekFromLocale] 在任何环境下都可能是周一）。
 *
 * 所有函数都是纯函数，便于 JVM 单测覆盖跨月周、跨年周等边界。
 */
object CalendarRangeCalculator {

    /** 一周的第一天（项目约定：周日）。 */
    val FirstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY

    /** 含 [date] 的那一周的第一天（周日）。 */
    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value % 7).toLong())

    /** 含 [date] 的那一周的最后一天（周六）。 */
    fun weekEnd(date: LocalDate): LocalDate = weekStart(date).plusDays(6)

    /**
     * 当前粒度下的可见日期范围（闭区间）。
     *
     * [expanded] = true（月视图）→ 锚点所在月的 1 日 ~ 月末；
     * [expanded] = false（周视图）→ 锚点所在周的周日 ~ 周六。
     *
     * 锚点日期本身不随粒度切换改变，所以「周 ↔ 月」切换天然不会跳变。
     */
    fun visibleRange(anchor: LocalDate, expanded: Boolean): Pair<LocalDate, LocalDate> =
        if (expanded) {
            val month = YearMonth.from(anchor)
            month.atDay(1) to month.atEndOfMonth()
        } else {
            weekStart(anchor) to weekEnd(anchor)
        }

    /** [start] ~ [end] 覆盖到的所有月份（含边界，升序）。周视图跨月时返回 2 个。 */
    fun monthsCovering(start: LocalDate, end: LocalDate): List<YearMonth> {
        if (end.isBefore(start)) return emptyList()
        val last = YearMonth.from(end)
        val months = mutableListOf<YearMonth>()
        var current = YearMonth.from(start)
        while (!current.isAfter(last)) {
            months.add(current)
            current = current.plusMonths(1)
        }
        return months
    }

    /** 翻页：月视图 ±[delta] 月，周视图 ±[delta] 周（头部箭头与手势翻页共用同一套锚点位移）。 */
    fun shiftAnchor(anchor: LocalDate, expanded: Boolean, delta: Int): LocalDate =
        if (expanded) anchor.plusMonths(delta.toLong()) else anchor.plusWeeks(delta.toLong())

    /** 月视图标题：与看板改造前完全一致（如「2026年1月」）。 */
    fun monthTitle(anchor: LocalDate): String {
        val month = YearMonth.from(anchor)
        return "${month.year}年${month.monthValue}月"
    }

    /** 周视图标题：同月内省略重复月份（「1月4日 – 10日」），跨月/跨年写全（「12月28日 – 1月3日」）。 */
    fun weekTitle(anchor: LocalDate): String {
        val start = weekStart(anchor)
        val end = weekEnd(anchor)
        return if (start.year == end.year && start.monthValue == end.monthValue) {
            "${start.monthValue}月${start.dayOfMonth}日 – ${end.dayOfMonth}日"
        } else {
            "${start.monthValue}月${start.dayOfMonth}日 – ${end.monthValue}月${end.dayOfMonth}日"
        }
    }

    /** 按粒度返回标题。 */
    fun title(anchor: LocalDate, expanded: Boolean): String =
        if (expanded) monthTitle(anchor) else weekTitle(anchor)

    /** 两天是否落在同一周（周日起始）。 */
    fun sameWeek(a: LocalDate, b: LocalDate): Boolean = weekStart(a) == weekStart(b)
}
