package com.otakup.niriko.data.local

import androidx.room.TypeConverter
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.WorkType
import org.json.JSONArray
import java.time.LocalDate

/**
 * Room 类型转换：枚举、LocalDate、标签列表。
 */
class Converters {

    // —— WorkType ——
    @TypeConverter
    fun fromWorkType(value: WorkType): String = value.name

    @TypeConverter
    fun toWorkType(value: String): WorkType = try {
        WorkType.valueOf(value)
    } catch (_: Exception) {
        WorkType.OTHER
    }

    // —— WatchStatus ——
    @TypeConverter
    fun fromWatchStatus(value: WatchStatus): String = value.name

    @TypeConverter
    fun toWatchStatus(value: String): WatchStatus = try {
        WatchStatus.valueOf(value)
    } catch (_: Exception) {
        WatchStatus.PLAN_TO_WATCH
    }

    // —— SubjectType ——
    @TypeConverter
    fun fromSubjectType(value: SubjectType): String = value.name

    @TypeConverter
    fun toSubjectType(value: String): SubjectType = try {
        when (value) {
            "LIGHT_NOVEL" -> SubjectType.BOOK
            else -> SubjectType.valueOf(value)
        }
    } catch (_: IllegalArgumentException) {
        SubjectType.OTHER
    }

    // —— LocalDate（存 epoch day，便于比较与排序）——
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun toLocalDate(value: Long?): LocalDate? =
        value?.let { LocalDate.ofEpochDay(it) }

    // —— 标签列表（JSON 数组字符串）——
    @TypeConverter
    fun fromTagList(tags: List<String>): String {
        val array = JSONArray()
        tags.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toTagList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            buildList {
                for (i in 0 until array.length()) {
                    runCatching { add(array.getString(i)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // —— Long 列表（已听曲目 id，JSON 数组字符串）——
    @TypeConverter
    fun fromLongList(list: List<Long>): String {
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toLongList(value: String): List<Long> {
        if (value.isBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            buildList {
                for (i in 0 until array.length()) {
                    runCatching { add(array.getLong(i)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
