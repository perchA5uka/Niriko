package com.otakup.niriko.ui.share

import java.time.DayOfWeek

/**
 * 日历分享卡的版式规则（纯函数，JVM 可测）。
 *
 * 卡面是 7 列固定网格：把「本月 1 号是周几」换算成前置空格数，再按天号定位格子。
 * 放在纯函数里，是为了让「2 月 1 号是周日」「31 天的月份要 6 行」这类边界有单测兜底，
 * 而不是靠人眼在 1080×1440 的卡面上数格子。
 */
object CalendarShareLayoutPolicy {

    /** 一周 7 列，周一起始（与 App 内日历一致）。 */
    const val COLUMNS = 7

    /** 单格最多画 3 个点；再多的作品用「3 点」表示「3 部以上」。 */
    const val MAX_DOTS = 3

    val WeekdayLabels: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    /** 本月 1 号前面的空格数：周一 = 0 … 周日 = 6。 */
    fun leadingBlanks(firstDayOfMonth: DayOfWeek): Int = firstDayOfMonth.value - 1

    /** 需要几行：按「天号 + 前置空格」向上取整。 */
    fun rowCount(dayCount: Int, leadingBlanks: Int): Int {
        if (dayCount <= 0) return 0
        return (dayCount + leadingBlanks + COLUMNS - 1) / COLUMNS
    }

    /** 天号 → (行, 列)，行列都从 0 开始。 */
    fun cellOf(dayOfMonth: Int, leadingBlanks: Int): Pair<Int, Int> {
        require(dayOfMonth >= 1) { "dayOfMonth must be >= 1" }
        val index = dayOfMonth - 1 + leadingBlanks
        return index / COLUMNS to index % COLUMNS
    }

    /** 单格点数：0 部不画，超过 [MAX_DOTS] 收敛到 3。 */
    fun dotCount(subjectCount: Int): Int = subjectCount.coerceIn(0, MAX_DOTS)

    /** 该格是否属于本月：月末余位（如 11 月的 31）不算。 */
    fun isInMonth(dayOfMonth: Int, dayCount: Int): Boolean = dayOfMonth in 1..dayCount

    /** 本月标题：「2026 年 11 月」。 */
    fun monthTitle(year: Int, month: Int): String = year.toString() + " 年 " + month + " 月"

    /** 卡头副文案：「本月放送 12 部 · 更新 9 天」。 */
    fun headerMeta(subjectCount: Int, activeDays: Int): String = "本月放送 " + subjectCount + " 部 · 更新 " + activeDays + " 天"
}

/**
 * 由「当月每天放送多少部」拼出卡面数据（纯函数）。
 *
 * @param month 目标月份
 * @param subjectCountByDate 当月每一天的放送作品数（缺省视为 0）
 * @param monthSubjectCount 当月去重后的作品数（用于卡头文案）
 */
fun buildCalendarShareData(
    month: java.time.YearMonth,
    subjectCountByDate: Map<java.time.LocalDate, Int>,
    monthSubjectCount: Int,
): CalendarShareCardData {
    val days = (1..month.lengthOfMonth()).map { day ->
        CalendarShareDay(
            dayOfMonth = day,
            subjectCount = subjectCountByDate[month.atDay(day)] ?: 0,
        )
    }
    val activeDays = days.count { it.subjectCount > 0 }
    val busiest = days.maxByOrNull { it.subjectCount }
    return CalendarShareCardData(
        monthLabel = CalendarShareLayoutPolicy.monthTitle(month.year, month.monthValue),
        headerMeta = CalendarShareLayoutPolicy.headerMeta(monthSubjectCount, activeDays),
        stats = listOf(
            "本月放送" to monthSubjectCount.toString(),
            "更新天数" to activeDays.toString(),
            "最忙一天" to (busiest?.subjectCount ?: 0).toString(),
        ),
        days = days,
        leadingBlanks = CalendarShareLayoutPolicy.leadingBlanks(month.atDay(1).dayOfWeek),
    )
}
