package com.otakup.niriko.data.onair

import android.content.Context
import com.otakup.niriko.data.local.entity.SubjectEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** onair 静态数据单条（bangumi-data / Bangumi-master 同源）。 */
@Serializable
data class OnAirRaw(
    val weekDayCN: Int? = null,
    val timeCN: String? = null,
    val type: String? = null,
    val tag: String? = null,
    val origin: String? = null,
)

/** 解析后的精确放送点（中国时区，星期 + 分钟）。 */
data class OnAirPoint(
    val subjectId: Long,
    /** 1=周一 ... 7=周日（与 DayOfWeek.value 一致）。 */
    val weekDay: Int,
    val hour: Int,
    val minute: Int,
    val timeCN: String,
) {
    val totalMinutes: Int get() = hour * 60 + minute
}

/**
 * 每日放送静态数据源（阶段 A）。
 * 从 assets/data/onair 下的 JSON（由 Bangumi-master 的 onair 每季数据转换而来）加载，
 * 为本地作品补齐**精确放送时刻**（airTimeMinutes / airTimeZone）。
 */
class OnAirRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val map: Map<Long, OnAirPoint> by lazy { load(context) }

    fun lookup(subjectId: Long): OnAirPoint? = map[subjectId]

    /** 用静态 onair 数据补齐作品的精确放送时刻（不覆盖已有 / 无匹配返回原对象）。 */
    fun enrich(subject: SubjectEntity): SubjectEntity {
        val p = lookup(subject.subjectId) ?: return subject
        return subject.copy(
            airWeekday = subject.airWeekday ?: p.weekDay,
            airTimeMinutes = subject.airTimeMinutes ?: p.totalMinutes,
            airTimeZone = subject.airTimeZone ?: "CN",
        )
    }

    fun enrichAll(list: List<SubjectEntity>): List<SubjectEntity> = list.map(::enrich)

    private fun load(context: Context): Map<Long, OnAirPoint> {
        val out = HashMap<Long, OnAirPoint>()
        val names = context.assets.list("data/onair") ?: return out
        for (name in names) {
            if (!name.endsWith(".json")) continue
            val tree = runCatching {
                json.parseToJsonElement(
                    context.assets.open("data/onair/" + name).bufferedReader().use { it.readText() },
                )
            }.getOrNull() as? JsonObject ?: continue
            for ((key, value) in tree) {
                val id = key.toLongOrNull() ?: continue
                val raw = runCatching { json.decodeFromJsonElement<OnAirRaw>(value) }.getOrNull() ?: continue
                val wd = raw.weekDayCN ?: continue
                val t = raw.timeCN ?: continue
                if (t.length < 4) continue
                val hour = t.substring(0, 2).toIntOrNull() ?: continue
                val minute = t.substring(2, 4).toIntOrNull() ?: continue
                out[id] = OnAirPoint(id, wd, hour, minute, t)
            }
        }
        return out
    }
}
