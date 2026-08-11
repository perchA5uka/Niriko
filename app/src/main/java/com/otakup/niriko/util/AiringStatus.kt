package com.otakup.niriko.util

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import java.time.LocalDate

/**
 * 放送/发售状态判断工具。
 * 处理作品的生命周期阶段：未开播、连载中、已完结。
 */
object AiringStatus {

    // ==================== 放送阶段 ====================

    /** 作品放送/发售生命周期阶段。 */
    enum class AiringPhase(val label: String) {
        UPCOMING("即将开播"),
        AIRING("连载中"),
        COMPLETED("已完结"),
        UNKNOWN("未知"),
    }

    /**
     * 获取作品当前所处的放送/发售阶段。
     *
     * @param now 当前日期（注入以便测试）
     */
    fun getPhase(
        subject: SubjectEntity,
        now: LocalDate = LocalDate.now(),
    ): AiringPhase {
        val airDate = TimeUtils.parseDate(subject.airDate) ?: return AiringPhase.UNKNOWN

        return when {
            airDate > now -> AiringPhase.UPCOMING          // 还未开播/发售
            subject.totalEpisodes != null && subject.totalEpisodes > 0 -> {
                // 有总集数：估算完结日
                val estimatedEnd = getEstimatedEndDate(airDate, subject.totalEpisodes)
                if (estimatedEnd != null && estimatedEnd >= now) AiringPhase.AIRING else AiringPhase.COMPLETED
            }
            else -> {
                // 无总集数（长期连载/未定集数）：过去 180 天内首播视为可能仍在连载
                val cutoff = now.minusDays(180)
                if (airDate >= cutoff) AiringPhase.AIRING else AiringPhase.COMPLETED
            }
        }
    }

    /**
     * 估算完结日期：首播日 + 总集数×7天。
     * 用于连载中/已完结的判断。
     */
    fun getEstimatedEndDate(
        airDate: LocalDate,
        totalEpisodes: Int?,
    ): LocalDate? {
        if (totalEpisodes == null || totalEpisodes <= 0) return null
        // 对于 2 集以下的特殊情况（电影/OVA等），用 1 天估算
        val weeks = if (totalEpisodes <= 2) 1L else totalEpisodes.toLong()
        return airDate.plusDays(weeks * 7)
    }

    /**
     * 判断此作品是否应当出现在"每周放送日历"中。
     *
     * 规则：
     * 1. 只有 ANIME 和 REAL 类型有每周放送概念
     * 2. 必须有合法的 airDate（/calendar API 本身已返回正确的周播数据，
     *    不需要额外按放送阶段过滤，避免因集数估算偏差误判为 COMPLETED）
     */
    fun shouldShowInWeeklyCalendar(
        subject: SubjectEntity,
        now: LocalDate = LocalDate.now(),
    ): Boolean {
        if (subject.type != SubjectType.ANIME && subject.type != SubjectType.REAL) return false
        return parseDate(subject.airDate) != null
    }

    /**
     * 判断此作品是否应当出现在"发售日"展示中。
     * GAME/MUSIC/BOOK/OTHER 类型适用：仅在 airDate 当天显示。
     */
    fun isReleaseDateType(subject: SubjectEntity): Boolean {
        return subject.type == SubjectType.GAME
            || subject.type == SubjectType.MUSIC
            || subject.type == SubjectType.BOOK
            || subject.type == SubjectType.OTHER
    }

    /**
     * 判断作品是否为每周放送类型（TV/Web）。
     * platform 为 TV/WEB/null 视为电视或网络放送 → 每周更新。
     */
    fun isWeeklyAnime(subject: SubjectEntity): Boolean {
        if (subject.type != SubjectType.ANIME && subject.type != SubjectType.REAL) return false
        val p = subject.platform?.lowercase() ?: return true
        return p.contains("tv") || p.contains("web")
            || p.contains("电视") || p.contains("网络")
    }

    /**
     * 判断作品是否为剧场版/OVA/电影，这类作品只应在 airDate 当天显示。
     */
    fun isMovieOrOva(subject: SubjectEntity): Boolean {
        if (subject.type != SubjectType.ANIME && subject.type != SubjectType.REAL) return false
        val p = subject.platform?.lowercase() ?: return false
        return p.contains("剧场") || p.contains("ova") || p.contains("movie")
            || p.contains("电影") || p.contains("映画")
    }

    /** 判断是否有明确的 airDate。 */
    fun hasAirDate(subject: SubjectEntity): Boolean =
        parseDate(subject.airDate) != null

    // ==================== 季度范围 ====================

    /**
     * 获取当前季度范围（起始日期，结束日期）。
     *
     * 季度划分：
     * 1-3月 → 冬季（1/1 ~ 4/1）
     * 4-6月 → 春季（4/1 ~ 7/1）
     * 7-9月 → 夏季（7/1 ~ 10/1）
     * 10-12月 → 秋季（10/1 ~ 次年1/1）
     */
    fun getCurrentSeasonRange(now: LocalDate = LocalDate.now()): SeasonRange {
        val year = now.year
        val month = now.monthValue
        val (startMonth, endMonth, endYear) = when (month) {
            1, 2, 3 -> Triple(1, 4, year)
            4, 5, 6 -> Triple(4, 7, year)
            7, 8, 9 -> Triple(7, 10, year)
            else -> Triple(10, 1, year + 1)
        }
        return SeasonRange(
            start = LocalDate.of(year, startMonth, 1),
            end = LocalDate.of(endYear, endMonth, 1),
        )
    }

    /** 解析 Date 字符串（支持 "2024-01-03" 或 "2024-01"）。使用 [TimeUtils.parseDate]。 */
    private fun parseDate(dateStr: String?): LocalDate? = TimeUtils.parseDate(dateStr)
}

/** 季度范围。 */
data class SeasonRange(
    val start: LocalDate,
    val end: LocalDate,
)
