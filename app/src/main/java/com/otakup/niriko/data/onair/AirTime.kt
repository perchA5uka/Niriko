package com.otakup.niriko.data.onair

import com.otakup.niriko.data.local.entity.SubjectEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 放送时刻值对象：源时刻（当天第几分钟）+ 源时区。
 *
 * 为什么需要它：`subjects.airTimeZone` 列从 MIGRATION_20_21 起就写入了
 * （[OnAirRepository.enrich] 写 "CN"），但全仓只写不读——`airTimeMinutes`
 * 被直接 `"%02d:%02d"` 格式化成"本地时刻"显示与推送。于是非 UTC+8 的用户
 * 看到的"23:30 放送"其实是北京时间，而且这个错误会随日历/通知一起扩散。
 *
 * 设计取舍：
 * - **源时区可配置**（[DEFAULT_ZONE_ID] 默认 +08:00）：静态 onair 数据的 `timeCN`
 *   到底是不是"日本时间换算后的中国时间"没有代码可证，所以换算方向不能硬编码在调用点；
 *   真抽查出是别的基础时区时，改默认值或调用点传参即可。
 * - **时刻不在这里做"加小时"**：跨日交给 ZonedDateTime，23:30 CN → 次日 00:30 JST
 *   由时区规则算，不靠 24 小时取模猜。
 * - **排序不要用它**：日历/放送的排序键仍用源时区的 `airTimeMinutes`（同区比较才稳定），
 *   只有"给人看"的地方才换算。
 */
data class AirTime(
    /** 源时区下的放送时刻（当天第几分钟，0-1439）。 */
    val minutes: Int,
    /** 源时区标识：支持 "CN"/"JP"/"LOCAL"，也接受 IANA 区名。默认 "CN"。 */
    val zoneId: String = DEFAULT_ZONE_ID,
) {

    /** 归一化到 0-1439，避免脏数据把时分算成 25:10。 */
    private val normalizedMinutes: Int get() = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    val hour: Int get() = normalizedMinutes / 60
    val minute: Int get() = normalizedMinutes % 60

    /** 源时区下的时刻。 */
    fun sourceTime(): LocalTime = LocalTime.of(hour, minute)

    /** 传入日期在设备时区下的放送时刻（可能跨到前/后一天）。 */
    fun toLocal(date: LocalDate, deviceZone: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        val sourceZone = resolvedSourceZone(deviceZone)
        return ZonedDateTime.of(date, sourceTime(), sourceZone)
            .withZoneSameInstant(deviceZone)
            .toLocalDateTime()
    }

    /** 换算后落在几天之后（-1 前一日 / 0 同日 / +1 次日）。 */
    fun dayShift(date: LocalDate, deviceZone: ZoneId = ZoneId.systemDefault()): Int =
        ChronoUnit.DAYS.between(date, toLocal(date, deviceZone).toLocalDate()).toInt()

    /** 设备区与源区的 UTC 偏移是否相同；相同就只该显示一个时刻。 */
    fun isSameOffset(date: LocalDate, deviceZone: ZoneId = ZoneId.systemDefault()): Boolean {
        val sourceZone = resolvedSourceZone(deviceZone)
        if (sourceZone == deviceZone) return true
        val instant = ZonedDateTime.of(date, sourceTime(), sourceZone).toInstant()
        return sourceZone.rules.getOffset(instant) == deviceZone.rules.getOffset(instant)
    }

    /** 源时刻文本，如 "23:30"。 */
    fun formatSource(): String = "%02d:%02d".format(hour, minute)

    /** 设备本地时刻文本；跨日时带"（次日）/（前一日）"，同区返回与 [formatSource] 相同。 */
    fun formatLocal(date: LocalDate, deviceZone: ZoneId = ZoneId.systemDefault()): String {
        if (isSameOffset(date, deviceZone)) return formatSource()
        val local = toLocal(date, deviceZone)
        val suffix = when (dayShift(date, deviceZone)) {
            1 -> "（次日）"
            -1 -> "（前一日）"
            else -> ""
        }
        return "%02d:%02d%s".format(local.hour, local.minute, suffix)
    }

    /** 给用户看的主文本：同区只显示一个时刻，异区显示本地时刻（带跨日标记）。 */
    fun displayText(date: LocalDate, deviceZone: ZoneId = ZoneId.systemDefault()): String =
        formatLocal(date, deviceZone)

    /** 异区时的补充说明（"北京时间 23:30"）；同区返回 null，避免出现"23:30（23:30）"。 */
    fun sourceHint(date: LocalDate, deviceZone: ZoneId = ZoneId.systemDefault()): String? {
        if (isSameOffset(date, deviceZone)) return null
        return sourceLabel() + " " + formatSource()
    }

    /** 源时区中文名（用于"北京时间 23:30"这类补充说明）。 */
    fun sourceLabel(): String = when (zoneId.trim().uppercase()) {
        "CN" -> "北京时间"
        "JP" -> "日本时间"
        "LOCAL" -> "设备时间"
        else -> "源时区"
    }

    /**
     * 把源时区标识解析成 [ZoneId]。
     *
     * "LOCAL" 与无法识别的值都回退到设备时区——与写入侧的宽松语义保持一致
     * （[OnAirRepository.enrich] 只保证写了 "CN"，历史数据可能是 null 或被手改过）。
     */
    private fun resolvedSourceZone(deviceZone: ZoneId): ZoneId = when (zoneId.trim().uppercase()) {
        "CN", "CST", "PRC", "ASIA/SHANGHAI" -> ZoneId.of("Asia/Shanghai")
        "JP", "JST", "ASIA/TOKYO" -> ZoneId.of("Asia/Tokyo")
        "LOCAL", "" -> deviceZone
        else -> runCatching { ZoneId.of(zoneId) }.getOrDefault(deviceZone)
    }

    companion object {
        /** 静态 onair 数据的缺省源时区（中国标准时间，UTC+8）。 */
        const val DEFAULT_ZONE_ID = "Asia/Shanghai"

        private const val MINUTES_PER_DAY = 24 * 60

        /** 从作品取放送时刻；`airTimeMinutes` 为空（没有精确时刻）时返回 null。 */
        fun of(subject: SubjectEntity, defaultZoneId: String = DEFAULT_ZONE_ID): AirTime? {
            val minutes = subject.airTimeMinutes ?: return null
            val zone = subject.airTimeZone?.takeIf { it.isNotBlank() } ?: defaultZoneId
            return AirTime(minutes, zone)
        }

        /** 分钟数 → "HH:mm"（保留给通知等只关心文本的调用点）。 */
        fun format(minutes: Int): String =
            "%02d:%02d".format(
                ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY / 60,
                ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY % 60,
            )
    }
}
