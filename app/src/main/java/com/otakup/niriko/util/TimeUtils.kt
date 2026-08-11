package com.otakup.niriko.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 时间格式化工具。
 */
object TimeUtils {

    /**
     * 将时间戳格式化为相对时间（基于自然日比较）。
     *
     * 规则：
     * - 未来时间 → ""
     * - 今天 → "今天"
     * - 昨天 → "昨天"
     * - 2-7 天 → "N天前"
     * - > 7 天 → ""
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = LocalDate.now()
        val date = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val days = now.toEpochDay() - date.toEpochDay()

        return when {
            days < 0 -> ""
            days == 0L -> "今天"
            days == 1L -> "昨天"
            days in 2..7 -> "${days}天前"
            else -> ""
        }
    }

    /**
     * 解析 Date 字符串（支持 "2024-01-03" 或 "2024-01" 格式）。
     */
    fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr == null) return null
        return try {
            if (dateStr.length >= 10) LocalDate.parse(dateStr.take(10))
            else LocalDate.parse("${dateStr.take(7)}-01")
        } catch (_: Exception) { null }
    }
}
