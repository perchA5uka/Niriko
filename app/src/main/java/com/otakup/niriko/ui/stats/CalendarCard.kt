package com.otakup.niriko.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.ui.components.appleGlassCard
import java.time.LocalDate

// ==================== 颜色方案 ====================

/** 作品类型颜色。 */
val CalendarTypeColors = mapOf(
    SubjectType.ANIME to Color(0xFF42A5F5),
    SubjectType.MANGA to Color(0xFFEF5350),
    SubjectType.BOOK to Color(0xFF66BB6A),
    SubjectType.GAME to Color(0xFFAB47BC),
    SubjectType.MUSIC to Color(0xFFFF7043),
    SubjectType.REAL to Color(0xFFEC407A),
    SubjectType.OTHER to Color(0xFF90A4AE),
)

/** 放送标记颜色（金色菱形 ◆）。 */
private val BroadcastColor = Color(0xFFFDD835)
private val ReleaseColor = Color(0xFF66BB6A)

private val weekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")
private val dotSize = 4.dp
private val dotSpacing = 1.dp
private val cellShape = RoundedCornerShape(4.dp)

// ==================== CalendarCard（顶层容器） ====================

/**
 * 作品日历卡片，支持个人记录 / 放送信息 / 全部显示三种模式。
 */
@Composable
fun CalendarCard(
    year: Int,
    month: Int,
    dayEvents: Map<LocalDate, CalendarDayEvents>,
    calendarMode: CalendarMode,
    broadcastError: String?,
    onSwitchMonth: (Int) -> Unit,
    onSwitchMode: (CalendarMode) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onRefreshBroadcast: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().appleGlassCard(shape = MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 模式切换
            ModeSwitch(
                currentMode = calendarMode,
                onSwitchMode = onSwitchMode,
            )
            // 月份头部
            MonthHeader(year = year, month = month, onSwitchMonth = onSwitchMonth)
            // 星期表头
            WeekdayHeader()
            // 日期网格
            DayGrid(
                year = year,
                month = month,
                dayEvents = dayEvents,
                onDayClick = onDayClick,
            )

            // 图例
            CalendarLegend(calendarMode = calendarMode)

            // 错误提示 + 刷新
            if (broadcastError != null) {
                BroadcastErrorHint(
                    message = broadcastError,
                    onRefresh = onRefreshBroadcast,
                )
            }
        }
    }
}

// ==================== 模式切换 ====================

@Composable
private fun ModeSwitch(
    currentMode: CalendarMode,
    onSwitchMode: (CalendarMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CalendarMode.entries.forEach { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onSwitchMode(mode) },
                label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
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
        }
    }
}

/** 放送信息错误/状态提示行 + 手动刷新。 */
@Composable
private fun BroadcastErrorHint(
    message: String,
    onRefresh: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
        )
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

// ==================== 月份头部 ====================

@Composable
private fun MonthHeader(
    year: Int,
    month: Int,
    onSwitchMonth: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onSwitchMonth(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            text = "%d年%d月".format(year, month),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = { onSwitchMonth(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
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

// ==================== 日期网格 ====================

@Composable
private fun DayGrid(
    year: Int,
    month: Int,
    dayEvents: Map<LocalDate, CalendarDayEvents>,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstDay = LocalDate.of(year, month, 1)
    val daysInMonth = firstDay.lengthOfMonth()
    val firstDayOfWeek = firstDay.dayOfWeek.value % 7

    Column {
        var day = 1
        for (row in 0 until 6) {
            if (day > daysInMonth) break
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val isEmptyCell = (row == 0 && col < firstDayOfWeek) || day > daysInMonth
                    if (isEmptyCell) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = LocalDate.of(year, month, day)
                        val events = dayEvents[date]
                        CalendarDayCell(
                            date = date,
                            events = events,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        )
                        day++
                    }
                }
            }
        }
    }
}

// ==================== 单日单元格（支持 2×2 封面网格） ====================

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    events: CalendarDayEvents?,
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
                    color = if (hasEvents) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (hasEvents) {
                DotsRow(events = events!!)
            }
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
private fun buildMarks(events: CalendarDayEvents): List<MarkInfo> {
    val allMarks = mutableListOf<MarkInfo>()

    // 个人事件圆点：每部作品最多一个（开始优先于完成）
    val processedIds = mutableSetOf<Long>()
    for (cws in events.startedItems) {
        if (cws.collection.id !in processedIds) {
            processedIds.add(cws.collection.id)
            allMarks.add(
                MarkInfo(
                    color = cws.subject?.let { CalendarTypeColors[it.type] } ?: Color.Gray,
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
                    color = cws.subject?.let { CalendarTypeColors[it.type] } ?: Color.Gray,
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
    // 标记列表按 events 缓存：数据未变化时重组不重建
    val allMarks = remember(events) { buildMarks(events) }
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

// ==================== 预览 ====================

@Preview(showBackground = true)
@Composable
private fun CalendarCardPreview() {
    val today = LocalDate.now()
    val testEvents = mapOf(
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

    NirikoTheme {
        CalendarCard(
            year = today.year,
            month = today.monthValue,
            dayEvents = testEvents,
            calendarMode = CalendarMode.ALL,
            broadcastError = null,
            onSwitchMonth = {},
            onSwitchMode = {},
            onDayClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
