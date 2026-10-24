package com.otakup.niriko.data.export

import com.otakup.niriko.data.local.entity.SubjectEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/**
 * 放送日历导出（4.8）：把追番表生成为 RFC 5545 的 `.ics` 文本。
 *
 * 纯函数、零依赖：不读数据库、不碰 Android，输入事件列表 + 导出时刻，输出字符串，
 * 因此可以在 JVM 单测里逐行断言（UID / RRULE / 折行 / 转义都钉住）。
 *
 * 三条硬规则（照任务卡）：
 * 1. **UID 是幂等键**：逐集 `niriko-{subjectId}-ep{n}@niriko.local`，整季 `niriko-{subjectId}@niriko.local`；
 *    同一 UID 重复导入时日历端更新而不是新增。
 * 2. **估算只用 COUNT，不用 UNTIL**：`FREQ=WEEKLY;INTERVAL=1;COUNT=N`。停播会让 UNTIL 落到错误日期，
 *    且 RFC 5545 要求 UNTIL 是 UTC 形式，语义也不对。
 * 3. **折行按 75 octets**（UTF-8 字节，不是字符）：中文标题很容易超长，按字符折会把多字节字符劈开。
 */
enum class IcsExportScope(val label: String) {
    THIS_MONTH("本月"),
    THIS_SEASON("本季"),
    ALL_WATCHING("在追全部"),
}

/** 一条待导出事件（估算模式用 [episodeCount] 生成 RRULE；逐集模式 [episode] 非空）。 */
data class IcsAirEvent(
    val subjectId: Long,
    val title: String,
    val date: LocalDate,
    /** 放送时刻：中国时区当天的第几分钟（0-1439）。 */
    val minutesOfDay: Int,
    val durationMinutes: Int = IcsExporter.DEFAULT_DURATION_MINUTES,
    /** 逐集事件的本集序号；null = 估算的整季事件。 */
    val episode: Int? = null,
    /** 估算事件的周数（COUNT）；逐集事件为 null。 */
    val episodeCount: Int? = null,
    val sequence: Int = 0,
    /** 该事件是估算的（SUMMARY 加「（预估）」）。 */
    val estimated: Boolean = false,
    /** 该集比每周节奏晚了（SUMMARY 加「（延期）」）。 */
    val delayed: Boolean = false,
)

object IcsExporter {

    const val PROD_ID = "-//Niriko//Airing Calendar//CN"
    const val TIME_ZONE_ID = "Asia/Shanghai"
    const val DEFAULT_DURATION_MINUTES = 24
    const val MAX_ESTIMATED_EPISODES = 200
    const val MAX_LINE_OCTETS = 75

    private val zone: ZoneId = ZoneId.of(TIME_ZONE_ID)
    private val localFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** 幂等键：逐集事件带集号，估算事件整部作品一个 UID。 */
    fun uid(subjectId: Long, episode: Int?): String =
        if (episode == null) "niriko-$subjectId@niriko.local"
        else "niriko-$subjectId-ep$episode@niriko.local"

    /** 估算事件的周数规则：COUNT 夹在 1..200，绝不使用 UNTIL。 */
    fun weeklyCountRrule(count: Int): String =
        "RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=${count.coerceIn(1, MAX_ESTIMATED_EPISODES)}"

    /** SEQUENCE：秒级单调递增，同 UID 的修改才能覆盖日历端旧值。 */
    fun sequenceOf(updateTimeMillis: Long): Int =
        (updateTimeMillis / 1000L).toInt().coerceAtLeast(0)

    /** RFC 5545 TEXT 转义：反斜杠 → \\、分号 → \;、逗号 → \,、换行 → \n。 */
    fun escapeText(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

    fun summaryFor(event: IcsAirEvent): String {
        val title = "《${escapeText(event.title)}》"
        val base = if (event.episode != null) "$title 第 ${event.episode} 集"
        else "$title 放送"
        return buildString {
            append(base)
            if (event.estimated) append("（预估）")
            if (event.delayed) append("（延期）")
        }
    }

    /** 75 octets 折行：续行以「CRLF + 一个空格」开头，空格本身也算 octet。 */
    fun foldLine(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_LINE_OCTETS) return line
        val out = StringBuilder()
        var used = 0
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val piece = line.substring(index, index + charCount)
            val pieceBytes = piece.toByteArray(Charsets.UTF_8).size
            if (used + pieceBytes > MAX_LINE_OCTETS) {
                out.append("\r\n ")
                used = 1 // 续行开头的空格
            }
            out.append(piece)
            used += pieceBytes
            index += charCount
        }
        return out.toString()
    }

    /** 生成完整 VCALENDAR：内联 VTIMEZONE（Outlook 必需），事件按日期排序。 */
    fun build(events: List<IcsAirEvent>, stamp: Instant): String {
        val lines = mutableListOf<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:$PROD_ID"
        lines += "CALSCALE:GREGORIAN"
        lines += "METHOD:PUBLISH"
        lines += "X-WR-TIMEZONE:$TIME_ZONE_ID"
        lines += "X-WR-CALNAME:Niriko 放送日历"
        lines += "BEGIN:VTIMEZONE"
        lines += "TZID:$TIME_ZONE_ID"
        lines += "BEGIN:STANDARD"
        lines += "TZOFFSETFROM:+0800"
        lines += "TZOFFSETTO:+0800"
        lines += "TZNAME:CST"
        lines += "DTSTART:19700101T000000"
        lines += "END:STANDARD"
        lines += "END:VTIMEZONE"
        events.sortedWith(compareBy({ it.date }, { it.minutesOfDay })).forEach { event ->
            val start = event.date.atStartOfDay(zone)
                .plusMinutes(event.minutesOfDay.toLong().coerceIn(0L, 24L * 60 - 1))
            val duration = event.durationMinutes.coerceAtLeast(1)
            lines += "BEGIN:VEVENT"
            lines += "UID:${uid(event.subjectId, event.episode)}"
            lines += "DTSTAMP:${utcFormat.format(stamp)}"
            lines += "DTSTART;TZID=$TIME_ZONE_ID:${localFormat.format(start)}"
            lines += "DURATION:PT${duration}M"
            lines += "SUMMARY:${summaryFor(event)}"
            lines += "SEQUENCE:${event.sequence}"
            lines += "TRANSP:TRANSPARENT"
            lines += "URL:https://bgm.tv/subject/${event.subjectId}"
            if (event.episode == null) {
                lines += weeklyCountRrule(event.episodeCount ?: 1)
            }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString(separator = "\r\n", postfix = "\r\n") { foldLine(it) }
    }

    fun estimatedCount(events: List<IcsAirEvent>): Int = events.count { it.estimated }

    /** 导出区间（本月 / 本季 / 在追全部共用的 90 天窗口）。 */
    fun windowEnd(from: LocalDate, days: Long = 90L): LocalDate = from.plusDays(days)

    fun isCurrentSeason(date: LocalDate, today: LocalDate): Boolean {
        val quarter = ((today.monthValue - 1) / 3) * 3 + 1
        return date.year == today.year && date.monthValue in quarter..(quarter + 2)
    }
}

/**
 * 由一部作品 + 已取到的本篇集日期生成导出事件（纯函数，4.7/4.8 共用口径）。
 *
 * - `episodeDates` 覆盖了声明集数 → 逐集事件（真实日期，停播周天然没有事件）；
 * - 否则 → 一条估算事件（RRULE COUNT = 剩余集数），起点是区间内第一个放送日；
 * - 没有精确放送时刻 / 没有首播日 → 不生成（宁可不导出，也不写 00:00 这种假数据）。
 */
fun buildIcsSubjectEvents(
    subject: SubjectEntity,
    episodeDates: List<LocalDate>,
    from: LocalDate,
    to: LocalDate,
    sequence: Int,
): List<IcsAirEvent> {
    val firstAirDate = subject.airDate
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return emptyList()
    val minutes = subject.airTimeMinutes?.coerceIn(0, 24 * 60 - 1) ?: return emptyList()
    if (to.isBefore(from)) return emptyList()
    val total = subject.totalEpisodes?.takeIf { it > 0 }
    val dates = episodeDates.filter { it >= firstAirDate }.distinct().sorted()
    val complete = total != null && dates.size >= total

    if (complete) {
        return dates.mapIndexedNotNull { index, date ->
            if (date.isBefore(from) || date.isAfter(to)) return@mapIndexedNotNull null
            val expected = firstAirDate.plusWeeks(index.toLong())
            IcsAirEvent(
                subjectId = subject.subjectId,
                title = subject.displayTitle,
                date = date,
                minutesOfDay = minutes,
                episode = index + 1,
                sequence = sequence,
                delayed = ChronoUnit.DAYS.between(expected, date) > 0,
            )
        }
    }

    // 估算：从首播日按 7 天推进到导出区间内，剩余集数作为 COUNT
    var start = firstAirDate
    while (start.isBefore(from)) start = start.plusWeeks(1)
    if (start.isAfter(to)) return emptyList()
    val elapsedWeeks = ChronoUnit.WEEKS.between(firstAirDate, start).toInt().coerceAtLeast(0)
    val remaining = (total ?: 12) - elapsedWeeks
    return listOf(
        IcsAirEvent(
            subjectId = subject.subjectId,
            title = subject.displayTitle,
            date = start,
            minutesOfDay = minutes,
            episode = null,
            episodeCount = remaining.coerceIn(1, IcsExporter.MAX_ESTIMATED_EPISODES),
            sequence = sequence,
            estimated = true,
        ),
    )
}
