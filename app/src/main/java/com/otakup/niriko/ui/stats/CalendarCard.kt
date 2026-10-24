package com.otakup.niriko.ui.stats

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kizitonwose.calendar.compose.ContentHeightMode
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import com.otakup.niriko.data.calculator.CalendarRangeCalculator
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.ui.theme.rememberChartTypeColors
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.data.model.stats.BroadcastMonthState
import com.otakup.niriko.data.model.stats.BroadcastMonthStatus
import com.otakup.niriko.ui.components.appleGlassCard
import androidx.core.content.FileProvider
import com.otakup.niriko.data.export.IcsAirEvent
import com.otakup.niriko.data.export.IcsExportScope
import com.otakup.niriko.data.export.IcsExporter
import com.otakup.niriko.data.export.buildIcsSubjectEvents
import com.otakup.niriko.data.model.EpisodeInfo
import java.io.File
import java.time.Instant
import java.time.LocalDate
import com.otakup.niriko.nirikoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.datetime.DayOfWeek as KDayOfWeek
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.YearMonth as KYearMonth
import kotlinx.datetime.toJavaLocalDate as kToJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate as kToKotlinLocalDate
import kotlinx.datetime.toKotlinYearMonth as kToKotlinYearMonth

// ==================== 颜色方案 ====================

// 作品类型颜色统一由 ui/theme/ChartPalette.kt 的主题派生色提供（chartTypeColor /
// rememberChartTypeColors），此处不再保留第二套固定色表。

/** 放送标记颜色（金色菱形 ◆）。 */
private val BroadcastColor = Color(0xFFFDD835)
private val ReleaseColor = Color(0xFF66BB6A)

/** 停播角标颜色（灰）与延期角标颜色（橙，对应 4.10 的「橙色小三角」）。 */
internal val HiatusColor = Color(0xFF90A4AE)
internal val DelayColor = Color(0xFFFB8C00)

private val weekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")
private val dotSize = 4.dp
private val dotSpacing = 1.dp
private val cellShape = RoundedCornerShape(4.dp)

// ==================== CalendarCard（顶层容器） ====================

/**
 * 作品日历卡片，支持个人记录 / 放送信息 / 全部显示三种模式，以及周 / 月两种视图粒度。
 *
 * [anchorDate] 为日历锚点：月视图显示其所在月，周视图显示其所在周（周日起始）；
 * [expanded] = true 为整月视图（默认，与周 / 月双模改造前一致），false 为单周视图。
 * 视图粒度与三态模式正交，互不影响。
 */
@Composable
fun CalendarCard(
    anchorDate: LocalDate,
    expanded: Boolean,
    dayEvents: Map<LocalDate, CalendarDayEvents>,
    calendarMode: CalendarMode,
    broadcastError: String?,
    onSwitchMonth: (Int) -> Unit,
    onSwitchMode: (CalendarMode) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onSwitchView: (Boolean) -> Unit,
    onAnchorDateChange: (LocalDate) -> Unit,
    onRefreshBroadcast: (() -> Unit)? = null,
    /** 放送日历上次成功刷新时间（0 = 从未）。 */
    broadcastLastUpdatedAt: Long = 0L,
    broadcastMonthState: BroadcastMonthState? = null,
    /**
     * 顶级页 Pager 手势锁。非 null 时，手指落在日历上即锁定 Pager，
     * 避免日历横向翻页与顶级页左右滑动抢手势（R2）。
     */
    gestureLock: MutableState<Boolean>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    val monthStart = remember(anchorDate) { YearMonth.from(anchorDate).atDay(1) }
    val monthEnd = remember(anchorDate) { YearMonth.from(anchorDate).atEndOfMonth() }
    val monthSubjects = remember(dayEvents) {
        dayEvents.values.flatMap { it.broadcastSubjects }.distinctBy { it.subjectId }
    }
    // 4.7/4.10：停播/延期要真实集日期才能推断，而 CalendarCard 的入参只有日事件。
    // 这里按「本月出现过的放送作品」读一次 Room（只读、仅 IO 一次/月），不引入新的数据流，
    // 也不改 StatsScreen 的调用点（另一轨正在改它）。
    var monthEpisodeDates by remember { mutableStateOf<Map<Long, List<LocalDate>>>(emptyMap()) }
    LaunchedEffect(monthSubjects.map { it.subjectId }) {
        if (monthSubjects.isEmpty()) {
            monthEpisodeDates = emptyMap()
            return@LaunchedEffect
        }
        monthEpisodeDates = withContext(Dispatchers.IO) {
            // runCatching：预览 / 非 App context 下取不到仓库时退化为"无集数据"（不显示角标），不能崩。
            runCatching {
                val dao = context.nirikoApp.database.episodeDao()
                monthSubjects.associate { subject ->
                    subject.subjectId to mainEpisodeDatesFromAirdates(
                        dao.getMainEpisodes(subject.subjectId).map { it.airdate },
                    )
                }
            }.getOrDefault(emptyMap())
        }
    }
    val airingAdjusts = remember(monthSubjects, monthEpisodeDates, monthStart, monthEnd) {
        mergeAiringAdjusts(monthSubjects, monthEpisodeDates, monthStart, monthEnd)
    }

    // 4.8：导出弹窗（范围 / 计数 / 忙碌态）
    var showExportDialog by remember { mutableStateOf(false) }
    var exportRange by remember { mutableStateOf(IcsExportScope.THIS_MONTH) }
    var exportSummary by remember { mutableStateOf<IcsExportSummary?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    LaunchedEffect(showExportDialog, exportRange, monthEpisodeDates) {
        if (!showExportDialog) return@LaunchedEffect
        exportBusy = true
        exportSummary = null
        val events = buildIcsExport(
            context = context,
            scope = exportRange,
            monthStart = monthStart,
            monthEnd = monthEnd,
            monthSubjects = monthSubjects,
            monthEpisodeDates = monthEpisodeDates,
        )
        exportSummary = IcsExportSummary(
            subjects = events.map { it.subjectId }.distinct().size,
            events = events.size,
            estimated = IcsExporter.estimatedCount(events),
        )
        exportBusy = false
    }

    Box(
        modifier = modifier.fillMaxWidth().appleGlassCard(shape = MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 三态模式（个人 / 放送 / 全部）+ 视图粒度（周 / 月）：两组控件互相正交
            CalendarControlRow(
                currentMode = calendarMode,
                onSwitchMode = onSwitchMode,
                expanded = expanded,
                onSwitchView = onSwitchView,
            )
            // 标题 + 翻页箭头
            CalendarHeader(
                anchorDate = anchorDate,
                expanded = expanded,
                onSwitchMonth = onSwitchMonth,
            )
            // 日期条（R9）：以今天为中心的固定 31 天横向窗口，一键跳到目标日
            CalendarDateStrip(
                anchorDate = anchorDate,
                dayEvents = dayEvents,
                gestureLock = gestureLock,
                onPickDate = onAnchorDateChange,
            )
            // 星期表头：周 / 月两种视图共用，保证列对齐始终一致
            WeekdayHeader()
            // 日期网格：宽度上限收敛，折叠屏 / 平板下居中而不是把日格拉爆
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val gridModifier = Modifier
                    .widthIn(max = maxDayCellSize * 7)
                    .calendarGestureLock(gestureLock)
                if (expanded) {
                    MonthGrid(
                        anchorDate = anchorDate,
                        dayEvents = dayEvents,
                        airingAdjusts = airingAdjusts,
                        onDayClick = onDayClick,
                        onAnchorDateChange = onAnchorDateChange,
                        modifier = gridModifier,
                    )
                } else {
                    WeekGrid(
                        anchorDate = anchorDate,
                        dayEvents = dayEvents,
                        airingAdjusts = airingAdjusts,
                        onDayClick = onDayClick,
                        onAnchorDateChange = onAnchorDateChange,
                        modifier = gridModifier,
                    )
                }
            }

            // 图例
            CalendarLegend(calendarMode = calendarMode)

            // 错误提示 / 陈旧度 + 刷新
            if (calendarMode != CalendarMode.PERSONAL) {
                BroadcastStatusHint(
                    message = broadcastError ?: broadcastMonthState?.let(::broadcastMonthMessage),
                    lastUpdatedAt = broadcastMonthState?.lastSuccessAt ?: broadcastLastUpdatedAt,
                    onRefresh = onRefreshBroadcast,
                )
                // 4.8：导出放送日历（.ics）—— 现算现导出，不落库
                IcsExportEntry(
                    enabled = !exportBusy,
                    onOpen = { showExportDialog = true },
                )
            }
            if (showExportDialog) {
                IcsExportDialog(
                    range = exportRange,
                    onRangeChange = { exportRange = it },
                    summary = exportSummary,
                    busy = exportBusy,
                    onDismiss = { if (!exportBusy) showExportDialog = false },
                    onConfirm = {
                        exportScope.launch {
                            exportBusy = true
                            val events = buildIcsExport(
                                context = context,
                                scope = exportRange,
                                monthStart = monthStart,
                                monthEnd = monthEnd,
                                monthSubjects = monthSubjects,
                                monthEpisodeDates = monthEpisodeDates,
                            )
                            val body = IcsExporter.build(events, Instant.now())
                            runCatching { shareIcsCalendar(context, body) }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        "导出失败：" + (it.message ?: "未知错误"),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            exportBusy = false
                            showExportDialog = false
                        }
                    },
                )
            }
        }
    }
}

// ==================== 日期条（R9：DateSelector 形态，自研实现） ====================

/** 日期条窗口半径（天）：以今天为中心固定展示 31 天。 */
internal const val DATE_STRIP_RADIUS = 15

/** 以 [center] 为中心、半径 [radius] 的日期列表（升序，共 2 × radius + 1 天）。 */
internal fun dateStripDates(center: LocalDate, radius: Int = DATE_STRIP_RADIUS): List<LocalDate> =
    (-radius..radius).map { center.plusDays(it.toLong()) }

/**
 * 日期条窗口的中心：默认锚定在 [today]（点选近处日期时整条不移动，不会在手指下平移），
 * 只有跳到 [today] 窗口之外的日期时才以该日期为中心重新开窗。
 */
internal fun dateStripCenter(
    today: LocalDate,
    anchorDate: LocalDate,
    radius: Int = DATE_STRIP_RADIUS,
): LocalDate = if (kotlin.math.abs(ChronoUnit.DAYS.between(today, anchorDate)) <= radius.toLong()) {
    today
} else {
    anchorDate
}

/** 周日为一周第一天的星期索引（0 = 周日 … 6 = 周六）。 */
internal fun weekdayIndexSunday0(date: LocalDate): Int = date.dayOfWeek.value % 7

/**
 * 横向日期条：31 天窗口，选中日高亮、今天淡色底、有事件的天带小圆点。
 * 手势：套 [calendarGestureLock]，避免与日历网格 / 顶级 Pager 抢横向拖动。
 */
@Composable
private fun CalendarDateStrip(
    anchorDate: LocalDate,
    dayEvents: Map<LocalDate, CalendarDayEvents>,
    gestureLock: MutableState<Boolean>?,
    onPickDate: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val dates = remember(today, anchorDate) { dateStripDates(dateStripCenter(today, anchorDate)) }
    val listState = rememberLazyListState()
    val selectedIndex = dates.indexOf(anchorDate)

    // 选中项回到可见区（左移 3 项，保留前文上下文）
    LaunchedEffect(dates, selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().calendarGestureLock(gestureLock),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
    ) {
        itemsIndexed(dates, key = { _, date -> date.toString() }) { _, date ->
            val selected = date == anchorDate
            val isToday = date == today
            val weekday = weekdayIndexSunday0(date)
            Column(
                modifier = Modifier
                    .width(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                            else -> Color.Transparent
                        },
                    )
                    .clickable { onPickDate(date) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = weekdayLabels[weekday],
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        weekday == 0 || weekday == 6 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (dayEvents[date]?.hasEvents == true) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

// ==================== 模式 / 视图切换 ====================

/**
 * 一组控件，两组语义：左侧三态模式（个人 / 放送 / 全部），右侧视图粒度（周 / 月）。
 * 二者正交：切视图不改模式，切模式不改视图。
 */
@Composable
private fun CalendarControlRow(
    currentMode: CalendarMode,
    onSwitchMode: (CalendarMode) -> Unit,
    expanded: Boolean,
    onSwitchView: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 三态模式：占满剩余宽度，模式变多时横滑
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalendarMode.entries.forEach { mode ->
                CalendarChip(
                    label = mode.label,
                    selected = currentMode == mode,
                    onClick = { onSwitchMode(mode) },
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        // 视图粒度
        CalendarChip(label = "周", selected = !expanded, onClick = { onSwitchView(false) })
        Spacer(Modifier.width(4.dp))
        CalendarChip(label = "月", selected = expanded, onClick = { onSwitchView(true) })
    }
}

@Composable
private fun CalendarChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

// ==================== 图例 ====================

@Composable
private fun CalendarLegend(calendarMode: CalendarMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (calendarMode == CalendarMode.PERSONAL || calendarMode == CalendarMode.ALL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color = Color.White, isStarted = true, size = 6.dp)
                Spacer(Modifier.width(3.dp))
                Text("开始", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color = Color.White, isStarted = false, size = 6.dp)
                Spacer(Modifier.width(3.dp))
                Text("完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
        }
        if (calendarMode == CalendarMode.BROADCAST || calendarMode == CalendarMode.ALL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BroadcastMarker(size = 6.dp)
                Spacer(Modifier.width(3.dp))
                Text("放送", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReleaseMarker(size = 6.dp)
                Spacer(Modifier.width(3.dp))
                Text("发售", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
            // 阶段 F：放送可信度说明（单源，仅供参考）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("放送时间参考 Bangumi 日历", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), fontSize = 9.sp)
            }
        }
    }
}

internal fun broadcastMonthMessage(state: BroadcastMonthState): String = when (state.status) {
    BroadcastMonthStatus.LOADING -> "${state.key} 放送信息加载中"
    BroadcastMonthStatus.SUCCESS -> "${state.key} 放送信息已更新"
    BroadcastMonthStatus.EMPTY -> "${state.key} 暂无放送事件"
    BroadcastMonthStatus.ERROR -> state.error ?: "${state.key} 放送信息加载失败，请重试"
}

/**
 * 放送信息状态行：错误提示 + 「上次更新 X 分钟前」+ 手动刷新。
 *
 * 改造前这里只在出错时出现，且失败被静默吞掉时用户完全看不出数据是新的还是旧的。
 */
@Composable
private fun BroadcastStatusHint(
    message: String?,
    lastUpdatedAt: Long,
    onRefresh: (() -> Unit)? = null,
) {
    // 相对时间走字
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
            }
            if (lastUpdatedAt > 0L) {
                Text(
                    text = "上次更新 " + com.otakup.niriko.data.refresh.RefreshStatusLabels
                        .formatAgo(lastUpdatedAt, nowMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
            }
        }
        if (onRefresh != null) {
            Text(
                text = "刷新",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onRefresh),
            )
        }
    }
}


/** 4.8：导出入口（放在放送状态行下方，与「刷新」同一区）。 */
@Composable
private fun IcsExportEntry(enabled: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "放送日历导出 .ics",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(enabled = enabled, onClick = onOpen),
        )
    }
}

/** 4.8 导出确认：范围（本月 / 本季 / 在追全部）+ 预览计数（其中多少条是预估）。 */
@Composable
private fun IcsExportDialog(
    range: IcsExportScope,
    onRangeChange: (IcsExportScope) -> Unit,
    summary: IcsExportSummary?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出放送日历（.ics）") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IcsExportScope.entries.forEach { scope ->
                        FilterChip(
                            selected = range == scope,
                            onClick = { if (!busy) onRangeChange(scope) },
                            label = { Text(scope.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在统计…", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    val s = summary
                    Text(
                        text = if (s == null) "范围确定后显示将导出的数量"
                        else "将导出 ${s.subjects} 部 / ${s.events} 个事件（其中 ${s.estimated} 个为预估）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "导入手机日历时，同一作品会按 UID 覆盖更新而不是重复；预估事件按每周一集生成。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy && (summary?.events ?: 0) > 0) { Text("导出") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ==================== 标题 + 翻页箭头 ====================

/**
 * 日历头部：左右箭头翻页，标题按粒度显示月份或周区间。
 * 箭头与手势翻页都落在同一个锚点上，因此两条路径的结果完全一致。
 */
@Composable
private fun CalendarHeader(
    anchorDate: LocalDate,
    expanded: Boolean,
    onSwitchMonth: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onSwitchMonth(-1) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = if (expanded) "上个月" else "上一周",
            )
        }
        Text(
            text = CalendarRangeCalculator.title(anchorDate, expanded),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = { onSwitchMonth(1) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "下个月" else "下一周",
            )
        }
    }
}

// ==================== 星期表头 ====================

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        weekdayLabels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ==================== 日期网格（周 / 月双模） ====================

/** 月视图可翻页的月份范围：锚点 ± 60 个月。 */
private const val MonthsAround = 60L

/** 周视图可翻页的周数范围：锚点 ± 260 周（约 5 年）。 */
private const val WeeksAround = 260L

/**
 * 日格最大边长。日格宽度 = 可用宽度 / 7，折叠屏 / 平板下若不设上限会被拉成巨块，
 * 因此把整行宽度收敛到 7 × 56dp 并居中。
 */
private val maxDayCellSize = 56.dp

/**
 * 月视图：kizitonwose 的 HorizontalCalendar，逐月分页横滑。
 *
 * 渲染层由库负责，数据契约仍是 Map<LocalDate, CalendarDayEvents>，与改造前完全一致。
 * 用 [OutDateStyle.EndOfRow]（该月有几行渲染几行）而不是 EndOfGrid，短月才不会在手机上多出一整行。
 */
@Composable
private fun MonthGrid(
    anchorDate: LocalDate,
    dayEvents: Map<LocalDate, CalendarDayEvents>,
    airingAdjusts: Map<LocalDate, List<AiringAdjust>> = emptyMap(),
    onDayClick: (LocalDate) -> Unit,
    onAnchorDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchorMonth = YearMonth.from(anchorDate)
    val kAnchorMonth = anchorMonth.toKotlinYearMonth()
    // 范围只在首次组合确定（remember 无 key）：锚点变化时靠下面的 LaunchedEffect 滚动，
    // 否则每次翻页都会重建 state，吞掉滚动动画
    val calendarState = rememberCalendarState(
        startMonth = remember { anchorMonth.minusMonths(MonthsAround).toKotlinYearMonth() },
        endMonth = remember { anchorMonth.plusMonths(MonthsAround).toKotlinYearMonth() },
        firstVisibleMonth = remember { anchorMonth.toKotlinYearMonth() },
        // 项目为周日起始：必须显式指定，firstDayOfWeekFromLocale() 在中文 locale 下返回周一
        firstDayOfWeek = KDayOfWeek.SUNDAY,
        outDateStyle = OutDateStyle.EndOfRow,
    )

    val latestAnchorDate by rememberUpdatedState(anchorDate)
    val latestOnAnchorDateChange by rememberUpdatedState(onAnchorDateChange)
    // ① 手势翻月 → 停下后回写锚点，让下方内容跟着切到新月份
    LaunchedEffect(calendarState) {
        // 把 isScrollInProgress 一起读进 flow：滑动期间的中间值会被跳过，
        // 停下时该布尔翻回 false，保证最终停稳的那一页一定被回写（否则手势翻月不会更新下方内容）
        snapshotFlow { calendarState.firstVisibleMonth.yearMonth to calendarState.isScrollInProgress }
            .collect { (visible, scrolling) ->
                if (scrolling) return@collect
                // 必须读最新的锚点（rememberUpdatedState）：本 effect 的 key 是 state，lambda 会被长期持有，
                // 直接用组合期的 kAnchorMonth 会变成陈旧捕获，滑回原月份时不再回写
                if (visible != YearMonth.from(latestAnchorDate)) {
                    // 回写可见月的 1 日：MonthGrid 只关心月份，日的部分对月视图无意义
                    latestOnAnchorDateChange(visible.firstDay.toJavaLocalDate())
                }
            }
    }
    // ② 锚点变化（箭头翻页）→ 驱动日历滚到目标月：滑动与箭头结果一致，且不会来回振荡
    LaunchedEffect(calendarState, anchorMonth) {
        if (calendarState.firstVisibleMonth.yearMonth != kAnchorMonth) {
            calendarState.animateScrollToMonth(kAnchorMonth)
        }
    }

    HorizontalCalendar(
        modifier = modifier,
        state = calendarState,
        contentHeightMode = ContentHeightMode.Wrap,
        dayContent = { day ->
            if (day.position == DayPosition.MonthDate) {
                val date = day.date.toJavaLocalDate()
                CalendarDayCell(
                    date = date,
                    events = dayEvents[date],
                    airingAdjusts = airingAdjusts[date].orEmpty(),
                    onClick = { onDayClick(date) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                // 上 / 下月补位格保持空白，与改造前手绘网格一致
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            }
        },
    )
}

/**
 * 周视图：kizitonwose 的 WeekCalendar，逐周分页横滑，恒定 7 天。
 *
 * [anchorDate] 所在的一周（周日起始）显示在同一行里，移动端纵向高度约为月视图的 1/3。
 */
@Composable
private fun WeekGrid(
    anchorDate: LocalDate,
    dayEvents: Map<LocalDate, CalendarDayEvents>,
    airingAdjusts: Map<LocalDate, List<AiringAdjust>> = emptyMap(),
    onDayClick: (LocalDate) -> Unit,
    onAnchorDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchorWeekStart = CalendarRangeCalculator.weekStart(anchorDate)
    val calendarState = rememberWeekCalendarState(
        startDate = remember { anchorWeekStart.minusDays(7L * WeeksAround).toKotlinLocalDate() },
        endDate = remember { anchorWeekStart.plusDays(7L * WeeksAround).toKotlinLocalDate() },
        firstVisibleWeekDate = remember { anchorWeekStart.toKotlinLocalDate() },
        firstDayOfWeek = KDayOfWeek.SUNDAY,
    )

    val latestAnchorDate by rememberUpdatedState(anchorDate)
    val latestOnAnchorDateChange by rememberUpdatedState(onAnchorDateChange)
    // ① 手势翻周 → 停下后回写锚点
    LaunchedEffect(calendarState) {
        // 同 MonthGrid：isScrollInProgress 一并进 flow，停稳后才回写锚点
        snapshotFlow {
            calendarState.firstVisibleWeek.days.first().date.toJavaLocalDate() to
                calendarState.isScrollInProgress
        }.collect { (visibleWeekStart, scrolling) ->
            if (scrolling) return@collect
            if (visibleWeekStart != CalendarRangeCalculator.weekStart(latestAnchorDate)) {
                latestOnAnchorDateChange(visibleWeekStart)
            }
        }
    }
    // ② 锚点变化（箭头翻页 / 切回周视图）→ 驱动日历滚到目标周
    LaunchedEffect(calendarState, anchorWeekStart) {
        if (calendarState.firstVisibleWeek.days.first().date.toJavaLocalDate() != anchorWeekStart) {
            calendarState.animateScrollToDate(anchorWeekStart.toKotlinLocalDate())
        }
    }

    WeekCalendar(
        modifier = modifier,
        state = calendarState,
        dayContent = { weekDay ->
            val date = weekDay.date.toJavaLocalDate()
            CalendarDayCell(
                date = date,
                events = dayEvents[date],
                airingAdjusts = airingAdjusts[date].orEmpty(),
                onClick = { onDayClick(date) },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
        },
    )
}

/**
 * 日历按下即锁住顶级页 Pager，抬手 / 取消时解锁。
 *
 * 日历本身要横滑翻页，顶级页 HorizontalPager 也是横滑；不锁的话两者会抢同一个手势。
 * 与搜索页复用同一把锁（[com.otakup.niriko.navigation.LocalSearchGestureLock]），
 * [lock] 为 null（如预览 / 未提供 CompositionLocal 的场景）时不做任何事。
 */
private fun Modifier.calendarGestureLock(lock: MutableState<Boolean>?): Modifier {
    if (lock == null) return this
    return this.pointerInput(lock) {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false)
                lock.value = true
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    }
                } finally {
                    lock.value = false
                }
            }
        }
    }
}

// kizitonwose 用 kotlinx.datetime 类型，项目其余代码用 java.time：
// 下面三个小转换器把边界收敛在一处，业务代码里不再出现两套日期类型混用。

/** kotlinx.datetime.LocalDate → java.time.LocalDate。 */
private fun KLocalDate.toJavaLocalDate(): LocalDate = kToJavaLocalDate()

/** java.time.LocalDate → kotlinx.datetime.LocalDate。 */
private fun LocalDate.toKotlinLocalDate(): KLocalDate = kToKotlinLocalDate()

/** java.time.YearMonth → kotlinx.datetime.YearMonth。 */
private fun YearMonth.toKotlinYearMonth(): KYearMonth = kToKotlinYearMonth()

// ==================== 单日单元格（支持 2×2 封面网格） ====================

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    events: CalendarDayEvents?,
    /** 4.7/4.10：停播 / 延期角标（来自真实集日期缺口，见 airingAdjustFor）。 */
    airingAdjusts: List<AiringAdjust> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasEvents = events != null && events.hasEvents
    val covers = events?.coverCandidates ?: emptyList()
    // 单格展示封面：优先 displayCover（一图一格轮换），兜底取候选第一张，保证旧数据/边缘回退一致
    val displayCoverUrl = events?.displayCover?.coverUrl
        ?: events?.coverCandidates?.firstOrNull()?.coverUrl
    val isToday = date == LocalDate.now()
    val dayText = "${date.dayOfMonth}"

    Box(
        modifier = modifier
            .clip(cellShape)
            .then(
                if (covers.isNotEmpty()) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = cellShape,
                ) else Modifier,
            )
            .clickable(onClick = onClick),
    ) {
        if (hasEvents && displayCoverUrl != null) {
            // 一图一格：单张封面铺满格子（正方形裁切），避免多图切割
            CalendarCellCover(
                coverUrl = displayCoverUrl,
                modifier = Modifier.fillMaxSize(),
            )
            // 半透明暗色遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent,
                    ),
            )
        }

        // 日期数字 + 标记行（左下角）
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 跨月延续标记：由计算层预计算（StatsCalculator），UI 直接读取，避免组合期字符串解析
                val hasContinuing = events?.hasContinuing == true
                if (hasContinuing) {
                    Text(
                        text = "续",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
                Text(
                    text = dayText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (hasEvents) 10.sp else 9.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (hasEvents && displayCoverUrl != null) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (hasEvents) {
                DotsRow(events = events!!)
            }
        }

        // 停播 / 延期角标（右上角一个字）：停 = 灰色，延 = 橙色（4.10）
        airingAdjusts.firstOrNull()?.let { adjust ->
            Text(
                text = adjust.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (adjust.kind == AiringAdjustKind.DELAYED) DelayColor else HiatusColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
            )
        }

        // 今天标记
        if (isToday && !hasEvents) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .clip(cellShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            )
        }
    }
}

/**
 * 单格封面背景：一图一格。
 * 用 150×150 小档解码（格子显示约 45–50dp @3x ≈ 150px，贴边解码省内存省解码），
 * ContentScale.Crop 铺满整个方形格子。
 */
@Composable
private fun CalendarCellCover(
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = if (coverUrl != null) {
            ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                // 小档尺寸：150×150 —— 格子显示约 45–50dp @3x ≈ 150px，贴边解码，省内存省解码
                .size(150)
                .build()
        } else {
            null
        },
        contentDescription = "日历封面",
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

// ==================== 标记行 ====================

/**
 * 在日历格子左下角渲染个人事件圆点 + 放送标记。
 *
 * 实心 ● = 当天开始观看，颜色 = SubjectType
 * 空心 ○ = 当天完成观看，颜色 = SubjectType
 * 菱形 ◆ = 当天有放送作品，金色
 *
 * 最多显示 4 个标记，超出显示 "+N"。
 */
/** 构建当天标记列表（纯函数；结果由 DotsRow 缓存，避免每次重组重算）。 */
private fun buildMarks(events: CalendarDayEvents, typeColors: Map<SubjectType, Color>): List<MarkInfo> {
    val allMarks = mutableListOf<MarkInfo>()

    // 个人事件圆点：每部作品最多一个（开始优先于完成）
    val processedIds = mutableSetOf<Long>()
    for (cws in events.startedItems) {
        if (cws.collection.id !in processedIds) {
            processedIds.add(cws.collection.id)
            allMarks.add(
                MarkInfo(
                    color = cws.subject?.let { typeColors[it.type] } ?: Color.Gray,
                    type = MarkType.STARTED,
                ),
            )
        }
    }
    for (cws in events.completedItems) {
        if (cws.collection.id !in processedIds) {
            processedIds.add(cws.collection.id)
            allMarks.add(
                MarkInfo(
                    color = cws.subject?.let { typeColors[it.type] } ?: Color.Gray,
                    type = MarkType.COMPLETED,
                ),
            )
        }
    }

    // 放送标记：按类型数量显示，最多 2 个菱形
    if (events.broadcastSubjects.isNotEmpty()) {
        // 按类型分组，每种类型显示一个菱形
        val uniqueTypes = events.broadcastSubjects.map { it.type }.distinct()
        uniqueTypes.take(2).forEach { _ ->
            allMarks.add(
                MarkInfo(
                    color = BroadcastColor,
                    type = MarkType.BROADCAST,
                ),
            )
        }
    }

    // 发售标记（GAME/MUSIC/BOOK）：绿色三角 ▲，最多 1 个
    if (events.releaseDateSubjects.isNotEmpty()) {
        allMarks.add(
            MarkInfo(
                color = ReleaseColor,
                type = MarkType.RELEASE,
            ),
        )
    }

    return allMarks
}

@Composable
private fun DotsRow(events: CalendarDayEvents) {
    // 类型色表来自主题派生色（ui/theme/ChartPalette.kt）
    val typeColors = rememberChartTypeColors()
    // 标记列表按 events + 色表缓存：数据或主题色未变化时重组不重建
    val allMarks = remember(events, typeColors) { buildMarks(events, typeColors) }
    val visibleMarks = allMarks.take(4)
    val remaining = allMarks.size - 4

    Row(
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        modifier = Modifier.padding(start = 1.dp),
    ) {
        visibleMarks.forEach { mark ->
            when (mark.type) {
                MarkType.BROADCAST -> BroadcastMarker(size = dotSize + 1.dp)
                MarkType.RELEASE -> ReleaseMarker(size = dotSize + 1.dp)
                else -> Dot(color = mark.color, isStarted = mark.type == MarkType.STARTED, size = dotSize)
            }
        }
        if (remaining > 0) {
            Text(
                text = "+$remaining",
                fontSize = 7.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private enum class MarkType { STARTED, COMPLETED, BROADCAST, RELEASE }

private data class MarkInfo(
    val color: Color,
    val type: MarkType,
)

// ==================== 标记绘制 ====================

@Composable
private fun Dot(
    color: Color,
    isStarted: Boolean,
    size: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val radius = size.toPx() / 2f
        if (isStarted) {
            drawCircle(color = color, radius = radius)
        } else {
            drawCircle(color = color, radius = radius * 0.75f, style = Stroke(width = 1.5f))
        }
    }
}

/** 金色菱形 ◆ = 放送标记。 */
@Composable
private fun BroadcastMarker(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val half = size.toPx() / 2f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(half, 0f)               // 上
            lineTo(size.toPx(), half)       // 右
            lineTo(half, size.toPx())       // 下
            lineTo(0f, half)               // 左
            close()
        }
        drawPath(path, color = BroadcastColor)
    }
}

/** 绿色三角 ▲ = 发售标记。 */
@Composable
private fun ReleaseMarker(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val half = size.toPx() / 2f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(half, 0f)          // 上顶点
            lineTo(size.toPx(), size.toPx()) // 右下
            lineTo(0f, size.toPx())   // 左下
            close()
        }
        drawPath(path, color = ReleaseColor)
    }
}

// ==================== 停播 / 延期（4.7 / 4.10） ====================

/** 停播判据：正常周更 7 天；连续两集间隔 > 10 天才算停播，避免 ±1 天误差被误判。 */
internal const val HIATUS_GAP_DAYS = 10L

/** 角标类型：格子上只放一个字（停 / 延），完整说明留给日详情面板。 */
internal enum class AiringAdjustKind { HIATUS, DELAYED }

/** 一条停播/延期角标。 */
internal data class AiringAdjust(
    val kind: AiringAdjustKind,
    val label: String,
    val detail: String,
)

/** 本篇（type == 0、sort > 0）且有 airdate 的集日期，升序去重。 */
internal fun mainEpisodeDates(episodes: List<EpisodeInfo>): List<LocalDate> = episodes
    .filter { it.type == 0 && it.sort > 0 }
    .mapNotNull { ep -> ep.airdate?.let(::parseIsoDate) }
    .distinct().sorted()

/** Room 侧（EpisodeEntity）的集日期：DAO 已按 type == 0 过滤，这里只解析 + 去重。 */
internal fun mainEpisodeDatesFromAirdates(airdates: List<String?>): List<LocalDate> =
    airdates.mapNotNull { it?.let(::parseIsoDate) }.distinct().sorted()

private fun parseIsoDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

/**
 * 集数据是否完整到可以推断停播/延期：必须覆盖声明集数。
 * 4.7 的硬约束 —— 缺集与停播无法区分，只在 complete 时启用缺口判定，否则会把「没拉到」误报成停播。
 */
internal fun isEpisodeDataComplete(totalEpisodes: Int?, episodeDates: List<LocalDate>): Boolean =
    totalEpisodes != null && totalEpisodes > 0 && episodeDates.size >= totalEpisodes

/**
 * 某一天的角标（纯函数，4.7 / 4.10）：
 *
 * - 当天是真实集日期，且比「首集 + N 周」晚 → **延期**（延 N 天）；
 * - 当天不是集日期，但正好是每周放送日、且落在 > 10 天的两集缺口里 → **停播**。
 *
 * 集数据不足两集时一律不判定 —— 没有数据不猜（与 4.7「完全没有 episodes 时不估算提醒」一致）。
 */
internal fun airingAdjustFor(
    date: LocalDate,
    episodeDates: List<LocalDate>,
    /** 集数据是否覆盖声明集数；false 时一律不判定（没数据不猜）。 */
    complete: Boolean = true,
): AiringAdjust? {
    if (!complete) return null
    if (episodeDates.size < 2) return null
    val first = episodeDates.first()
    val index = episodeDates.indexOf(date)
    if (index >= 0) {
        val expected = first.plusWeeks(index.toLong())
        val days = ChronoUnit.DAYS.between(expected, date)
        if (days <= 0) return null
        return AiringAdjust(
            kind = AiringAdjustKind.DELAYED,
            label = "延",
            detail = "延期 $days 天（原定 ${expected.monthValue}月${expected.dayOfMonth}日）",
        )
    }
    if (!date.isAfter(first)) return null
    if (ChronoUnit.DAYS.between(first, date) % 7L != 0L) return null
    // 缺口内的每周放送日：从「停播后的第一个本该更新的日子」到「恢复那集」之前（含首尾的哪一边都要对：
    // 紧邻停播周的那一天正好是 from + 1 周，所以用 !isBefore 而不是 isAfter）。
    val inGap = episodeDates.zipWithNext().any { (from, to) ->
        ChronoUnit.DAYS.between(from, to) > HIATUS_GAP_DAYS &&
            !date.isBefore(from.plusWeeks(1)) && date.isBefore(to)
    }
    if (!inGap) return null
    return AiringAdjust(
        kind = AiringAdjustKind.HIATUS,
        label = "停",
        detail = "停播：原定 ${date.monthValue}月${date.dayOfMonth}日未播",
    )
}

/** 一部作品在 [monthStart]..[monthEnd] 内的角标（逐日跑纯规则）。 */
internal fun airingAdjustsForRange(
    episodeDates: List<LocalDate>,
    monthStart: LocalDate,
    monthEnd: LocalDate,
    complete: Boolean = true,
): Map<LocalDate, AiringAdjust> {
    if (!complete || episodeDates.size < 2) return emptyMap()
    val out = linkedMapOf<LocalDate, AiringAdjust>()
    var date = monthStart
    while (!date.isAfter(monthEnd)) {
        airingAdjustFor(date, episodeDates, complete)?.let { out[date] = it }
        date = date.plusDays(1)
    }
    return out
}

/**
 * 多部作品的角标合并成「日期 → 角标列表」（格子显示第一条）。
 *
 * 只有**集数据完整**（覆盖声明集数）的作品参与：残缺数据只能证明「没拉到」，不能证明「停播」。
 */
internal fun mergeAiringAdjusts(
    subjects: List<SubjectEntity>,
    episodeDatesBySubject: Map<Long, List<LocalDate>>,
    monthStart: LocalDate,
    monthEnd: LocalDate,
): Map<LocalDate, List<AiringAdjust>> {
    if (subjects.isEmpty() || episodeDatesBySubject.isEmpty()) return emptyMap()
    val out = linkedMapOf<LocalDate, MutableList<AiringAdjust>>()
    subjects.forEach { subject ->
        val dates = episodeDatesBySubject[subject.subjectId].orEmpty()
        airingAdjustsForRange(
            episodeDates = dates,
            monthStart = monthStart,
            monthEnd = monthEnd,
            complete = isEpisodeDataComplete(subject.totalEpisodes, dates),
        ).forEach { (date, adjust) ->
            out.getOrPut(date) { mutableListOf() }.add(adjust)
        }
    }
    return out
}

// ==================== .ics 导出（4.8） ====================

/** 导出前的计数预览：范围会写进确认弹窗（"12 部 / 138 个事件（其中 41 个预估）"）。 */
internal data class IcsExportSummary(val subjects: Int, val events: Int, val estimated: Int)

/** 计算某个范围要导出的事件（IO 线程里读 Room；上限 200 部，避免长尾拖慢）。 */
internal suspend fun buildIcsExport(
    context: android.content.Context,
    scope: IcsExportScope,
    monthStart: LocalDate,
    monthEnd: LocalDate,
    monthSubjects: List<SubjectEntity>,
    monthEpisodeDates: Map<Long, List<LocalDate>>,
    today: LocalDate = LocalDate.now(),
): List<IcsAirEvent> = withContext(Dispatchers.IO) {
    val app = context.applicationContext as? com.otakup.niriko.NirikoApplication
        ?: return@withContext emptyList()
    if (scope == IcsExportScope.THIS_MONTH) {
        return@withContext monthSubjects.flatMap { subject ->
            buildIcsSubjectEvents(
                subject = subject,
                episodeDates = monthEpisodeDates[subject.subjectId].orEmpty(),
                from = monthStart,
                to = monthEnd,
                sequence = 0,
            )
        }
    }
    val collections = app.database.collectionDao().observeAllWithSubject().first()
    val from = today
    val to = IcsExporter.windowEnd(today)
    val episodeDao = app.database.episodeDao()
    // Sequence.flatMap 的 lambda 不是 suspend，不能在里面读 Room；改成普通循环（上限 200 部）。
    val out = mutableListOf<IcsAirEvent>()
    var taken = 0
    for (item in collections) {
        if (taken >= 200) break
        val status = item.collection.status
        if (status != WatchStatus.WATCHING && status != WatchStatus.PLAN_TO_WATCH) continue
        if (item.collection.isPrivate) continue
        if (scope == IcsExportScope.THIS_SEASON) {
            val firstAir = item.subject.airDate?.let(::parseIsoDate) ?: continue
            if (!IcsExporter.isCurrentSeason(firstAir, today)) continue
        }
        val dates = episodeDao.getMainEpisodes(item.subject.subjectId)
            .map { it.airdate }
            .let(::mainEpisodeDatesFromAirdates)
        out += buildIcsSubjectEvents(
            subject = item.subject,
            episodeDates = dates,
            from = from,
            to = to,
            sequence = IcsExporter.sequenceOf(item.collection.updateTime),
        )
        taken++
    }
    out
}

/** 把 .ics 写进 cache/share/ 并经 FileProvider 分享（不申请存储权限）。 */
internal fun shareIcsCalendar(context: android.content.Context, body: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "niriko-airing-${LocalDate.now().toString().replace("-", "")}.ics")
    file.writeText(body, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/calendar"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "导出放送日历"))
}

// ==================== 预览 ====================

/** 预览用假数据：今天有开始 / 完成 / 放送，两天前有完成。 */
private fun previewDayEvents(today: LocalDate): Map<LocalDate, CalendarDayEvents> = mapOf(
    today to CalendarDayEvents(
        date = today,
        startedItems = listOf(
            CollectionWithSubject(
                collection = CollectionEntity(id = 1, subjectId = 328609, status = WatchStatus.WATCHING, watchedEpisodes = 3, startDate = today),
                subject = SubjectEntity(subjectId = 328609, title = "ぼっち・ざ・ろっく！", titleCN = "孤独摇滚！", type = SubjectType.ANIME, totalEpisodes = 12, coverUrl = null),
            ),
        ),
        completedItems = listOf(
            CollectionWithSubject(
                collection = CollectionEntity(id = 2, subjectId = 303, status = WatchStatus.COMPLETED, finishDate = today),
                subject = SubjectEntity(subjectId = 303, title = "葬送のフリーレン", titleCN = "葬送的芙莉莲", type = SubjectType.ANIME, totalEpisodes = 28, coverUrl = null),
            ),
        ),
        broadcastSubjects = listOf(
            SubjectEntity(subjectId = 999, title = "2026年7月新番", type = SubjectType.ANIME, totalEpisodes = 12),
        ),
    ),
    today.minusDays(2) to CalendarDayEvents(
        date = today.minusDays(2),
        startedItems = emptyList(),
        completedItems = listOf(
            CollectionWithSubject(
                collection = CollectionEntity(id = 3, subjectId = 100, status = WatchStatus.COMPLETED, finishDate = today.minusDays(2)),
                subject = SubjectEntity(subjectId = 100, title = "SPY×FAMILY", titleCN = "间谍过家家", type = SubjectType.MANGA, totalEpisodes = 12, coverUrl = null),
            ),
        ),
    ),
)

@Preview(showBackground = true)
@Composable
private fun CalendarCardPreview() {
    val today = LocalDate.now()
    val testEvents = previewDayEvents(today)

    NirikoTheme {
        CalendarCard(
            anchorDate = today,
            expanded = true,
            dayEvents = testEvents,
            calendarMode = CalendarMode.ALL,
            broadcastError = null,
            onSwitchMonth = {},
            onSwitchMode = {},
            onDayClick = {},
            onSwitchView = {},
            onAnchorDateChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 周视图预览：与月视图共用同一套日格渲染与图例。 */
@Preview(showBackground = true)
@Composable
private fun CalendarCardWeekPreview() {
    val today = LocalDate.now()
    // 与月视图预览共用同一套假数据：两种粒度渲染的是同一份日格与图例
    val testEvents = previewDayEvents(today)

    NirikoTheme {
        CalendarCard(
            anchorDate = today,
            expanded = false,
            dayEvents = testEvents,
            calendarMode = CalendarMode.ALL,
            broadcastError = null,
            onSwitchMonth = {},
            onSwitchMode = {},
            onDayClick = {},
            onSwitchView = {},
            onAnchorDateChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
