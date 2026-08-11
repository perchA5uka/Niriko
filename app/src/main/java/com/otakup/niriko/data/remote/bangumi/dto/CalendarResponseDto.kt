package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.Serializable

/**
 * Bangumi GET /v0/calendar 返回的放送日历条目。
 *
 * API 返回格式：
 * ```json
 * [
 *   {
 *     "weekday": { "id": 0, "name": "日曜日", "en": "Sunday" },
 *     "items": [ { ...SubjectDto... } ]
 *   }
 * ]
 * ```
 */
@Serializable
data class CalendarDayDto(
    val weekday: WeekdayInfo = WeekdayInfo(),
    val items: List<SubjectDto> = emptyList(),
)

@Serializable
data class WeekdayInfo(
    val id: Int = 0,
    val name: String = "",
    val en: String = "",
)
