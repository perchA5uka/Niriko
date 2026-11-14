package com.otakup.niriko.ui.share

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.ui.theme.NirikoShapes
import com.otakup.niriko.util.ShareCardLayoutPolicy

/**
 * 批次 C 的三张分享卡（8.5 分区海报 / 8.6 评分对照 / 8.7 追番周报）。
 *
 * 与既有 ShareCardLayout 分开的原因：那张卡是「单作品海报」，尺寸与字段都锁死了；
 * 这三张是**不同信息形态**（刻度/统计/网格），共用的是颜色 token、渲染桥与分享管线。
 * 纯布局规则（分页/刻度/文案）在 ShareCardLayoutPolicy，这里只负责画。
 */
object ShareCardVariants {
    const val PAGE_WIDTH = 1080
    const val PAGE_HEIGHT = 1400
    const val RATING_WIDTH = 1080
    const val RATING_HEIGHT = 1080

    suspend fun renderRating(context: Context, host: ShareBitmapHost, data: RatingComparisonCardData): Bitmap = host.renderContent(context, RATING_WIDTH, RATING_HEIGHT) { RatingComparisonCard(data) }

    suspend fun renderWeekly(context: Context, host: ShareBitmapHost, data: WeeklyReportCardData): Bitmap = host.renderContent(context, PAGE_WIDTH, PAGE_HEIGHT) { WeeklyReportCard(data) }

    suspend fun renderPartitionPage(context: Context, host: ShareBitmapHost, data: PartitionPosterCardData): Bitmap = host.renderContent(context, PAGE_WIDTH, PAGE_HEIGHT) { PartitionPosterCard(data) }

    /** 作品信息分享卡（重做版）：全幅封面 + 标题/评分/标签/短评/进度。 */
    suspend fun renderSubjectShowcase(context: Context, host: ShareBitmapHost, data: SubjectShowcaseCardData): Bitmap = host.renderContent(context, PAGE_WIDTH, PAGE_HEIGHT) { SubjectShowcaseCard(data) }

    /** 日历分享卡：7 列固定网格，行数与前置空格由 [CalendarShareLayoutPolicy] 算出。 */
    suspend fun renderCalendar(context: Context, host: ShareBitmapHost, data: CalendarShareCardData): Bitmap = host.renderContent(context, PAGE_WIDTH, PAGE_HEIGHT) { CalendarShareCard(data) }

    /**
     * 把一整个分区切成若干页（每页 12 部，见 ShareCardLayoutPolicy.pages）。
     *
     * 分页规则只在纯函数里定义一次；这里只做「切封面 + 打页码」，避免渲染侧再算一套。
     */
    fun partitionPages(
        folderName: String,
        covers: List<Bitmap?>,
        averageScore: Float? = null,
        yearRange: String? = null,
    ): List<PartitionPosterCardData> {
        val ranges = ShareCardLayoutPolicy.pages(covers.size)
        return ranges.mapIndexed { index, range ->
            PartitionPosterCardData(
                folderName = folderName,
                totalCount = covers.size,
                averageScore = averageScore,
                yearRange = yearRange,
                covers = range.map { covers[it] },
                pageIndex = index,
                pageCount = ranges.size,
            )
        }
    }

    /** 渲染所有页（顺序 IO；一次最多 4 页，没必要并行抢内存）。 */
    suspend fun renderPartitionPages(
        context: Context,
        host: ShareBitmapHost,
        pages: List<PartitionPosterCardData>,
    ): List<Bitmap> = pages.map { renderPartitionPage(context, host, it) }
}

/** 8.6 评分对照卡的数据（社区分/分布缺失时各字段可空，卡里分别降级）。 */
data class RatingComparisonCardData(
    val title: String,
    val mine: Float?,
    val community: Float?,
    val distribution: Map<Int, Int> = emptyMap(),
    val percentile: Int? = null,
)

/** 8.7 周报卡的数据（用本地放送/进度算出来，不依赖账号）。 */
data class WeeklyReportCardData(
    val weekLabel: String,
    val updatedCount: Int,
    val watchedEpisodes: Int,
    val finishedCount: Int,
    val highlightTitle: String? = null,
    val highlightCover: Bitmap? = null,
)

/** 作品信息分享卡（重做版，1080×1400）的数据；封面已预加载成 Bitmap。 */
data class SubjectShowcaseCardData(
    val cover: Bitmap? = null,
    val typeLabel: String = "",
    val title: String = "",
    val secondaryTitle: String? = null,
    val communityScore: Float? = null,
    val ratingTotal: String? = null,
    val metaText: String? = null,
    val tags: List<String> = emptyList(),
    val impression: String? = null,
    val progressText: String? = null,
    val progressFraction: Float = 0f,
)

/** 日历分享卡的一格（天号 + 当天放送作品数）。 */
data class CalendarShareDay(
    val dayOfMonth: Int,
    val subjectCount: Int,
    /** 该格展示的封面地址：优先应用内同款 displayCover，兜底候选第一张。 */
    val coverUrl: String? = null,
    /** 预加载后的封面（渲染是同步的，位图必须先由调用方拉好）。 */
    val cover: Bitmap? = null,
)

/** 日历分享卡的数据（由日历页当月的 dayEvents 现算，不落库）。 */
data class CalendarShareCardData(
    val monthLabel: String,
    val headerMeta: String,
    val stats: List<Pair<String, String>> = emptyList(),
    val days: List<CalendarShareDay> = emptyList(),
    val leadingBlanks: Int = 0,
)

/** 8.5 分区海报的一页（封面已由调用方预加载成 Bitmap，渲染是同步的）。 */
data class PartitionPosterCardData(
    val folderName: String,
    val totalCount: Int,
    val averageScore: Float?,
    val yearRange: String?,
    val covers: List<Bitmap?> = emptyList(),
    val pageIndex: Int = 0,
    val pageCount: Int = 1,
)

@Composable
private fun CardBase(width: Int, height: Int, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(width.dp, height.dp)
            .background(ShareCompose.Card),
    ) {
        content()
    }
}

@Composable
private fun BrandFooter(modifier: Modifier = Modifier) {
    Text(
        text = "——— 来自 Niriko",
        style = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
        color = ShareCompose.Ink3,
        modifier = modifier,
    )
}

@Composable
private fun RatingComparisonCard(data: RatingComparisonCardData) {
    val trackWidth = 952f
    val mineX = data.mine?.let { ShareCardLayoutPolicy.scoreMarkerX(it, trackWidth) }
    val communityX = data.community?.let { ShareCardLayoutPolicy.scoreMarkerX(it, trackWidth) }

    CardBase(ShareCardVariants.RATING_WIDTH, ShareCardVariants.RATING_HEIGHT) {
        Column(modifier = Modifier.fillMaxSize().padding(64.dp)) {
            Text("评分对照", style = androidx.compose.ui.text.TextStyle(fontSize = 26.sp), color = ShareCompose.Primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                text = data.title,
                style = androidx.compose.ui.text.TextStyle(fontSize = 44.sp),
                color = ShareCompose.Ink,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(36.dp))

            // 刻度：0–10，两个标记（我 / 社区）；缺一边就不画那一边。
            Box(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .align(Alignment.CenterStart)
                        .background(ShareCompose.Line, RoundedCornerShape(3.dp)),
                )
                mineX?.let { x ->
                    Box(Modifier.offset(x = x.dp).size(22.dp).clip(CircleShape).background(ShareCompose.Primary))
                }
                communityX?.let { x ->
                    Box(Modifier.offset(x = x.dp).size(18.dp).clip(CircleShape).background(ShareCompose.Ink2))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp), color = ShareCompose.Ink3)
                Text("10", style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp), color = ShareCompose.Ink3)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("我 " + (data.mine?.let { "%.1f".format(it) } ?: "未评"), style = androidx.compose.ui.text.TextStyle(fontSize = 30.sp), color = ShareCompose.Primary, fontWeight = FontWeight.Bold)
                Text("社区 " + (data.community?.let { "%.1f".format(it) } ?: "暂无"), style = androidx.compose.ui.text.TextStyle(fontSize = 30.sp), color = ShareCompose.Ink2)
            }
            ShareCardLayoutPolicy.deltaText(data.mine, data.community)?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = androidx.compose.ui.text.TextStyle(fontSize = 26.sp), color = ShareCompose.Ink)
            }
            data.percentile?.let {
                Spacer(Modifier.height(8.dp))
                Text("本地同类作品百分位 $it", style = androidx.compose.ui.text.TextStyle(fontSize = 22.sp), color = ShareCompose.Ink3)
            }

            Spacer(Modifier.height(28.dp))
            // 迷你分布柱：只在有分布时画，避免一条空刻度。
            if (data.distribution.isNotEmpty()) {
                val maxCount = (data.distribution.values.maxOrNull() ?: 1).coerceAtLeast(1)
                Row(modifier = Modifier.fillMaxWidth().height(150.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..10).forEach { score ->
                        val count = data.distribution[score] ?: 0
                        val h = (count.toFloat() / maxCount * 140f).coerceAtLeast(if (count > 0) 4f else 2f)
                        Box(Modifier.weight(1f).height(h.dp).background(if (count > 0) ShareCompose.ChipInk else ShareCompose.Line, RoundedCornerShape(3.dp)))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            BrandFooter()
        }
    }
}

@Composable
private fun WeeklyReportCard(data: WeeklyReportCardData) {
    CardBase(ShareCardVariants.PAGE_WIDTH, ShareCardVariants.PAGE_HEIGHT) {
        Column(modifier = Modifier.fillMaxSize().padding(64.dp)) {
            Text("Niriko 周报", style = androidx.compose.ui.text.TextStyle(fontSize = 26.sp), color = ShareCompose.Primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(data.weekLabel, style = androidx.compose.ui.text.TextStyle(fontSize = 48.sp), color = ShareCompose.Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(40.dp))

            StatLine("本周更新", data.updatedCount, "部")
            Spacer(Modifier.height(22.dp))
            StatLine("本周看掉", data.watchedEpisodes, "集")
            Spacer(Modifier.height(22.dp))
            StatLine("本周追完", data.finishedCount, "部")
            Spacer(Modifier.height(44.dp))

            if (data.highlightTitle != null) {
                Text("最期待", style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp), color = ShareCompose.Ink3)
                Spacer(Modifier.height(12.dp))
                data.highlightCover?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(300.dp).height(420.dp).clip(NirikoShapes.SectionShape),
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    data.highlightTitle,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 34.sp),
                    color = ShareCompose.Ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            BrandFooter()
        }
    }
}

@Composable
private fun StatLine(label: String, value: Int, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$value", style = androidx.compose.ui.text.TextStyle(fontSize = 72.sp), color = ShareCompose.Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(unit, style = androidx.compose.ui.text.TextStyle(fontSize = 28.sp), color = ShareCompose.Ink2)
        Spacer(Modifier.width(16.dp))
        Text(label, style = androidx.compose.ui.text.TextStyle(fontSize = 26.sp), color = ShareCompose.Ink3)
    }
}

@Composable
private fun PartitionPosterCard(data: PartitionPosterCardData) {
    CardBase(ShareCardVariants.PAGE_WIDTH, ShareCardVariants.PAGE_HEIGHT) {
        Column(modifier = Modifier.fillMaxSize().padding(56.dp)) {
            Text(data.folderName, style = androidx.compose.ui.text.TextStyle(fontSize = 46.sp), color = ShareCompose.Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            val meta = buildString {
                append(data.totalCount).append(" 部")
                data.averageScore?.let { append(" · 均分 ").append("%.1f".format(it)) }
                data.yearRange?.let { append(" · ").append(it) }
                if (data.pageCount > 1) append(" · 第 ").append(data.pageIndex + 1).append("/").append(data.pageCount).append(" 页")
            }
            Text(meta, style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp), color = ShareCompose.Ink3)
            Spacer(Modifier.height(28.dp))

            // 3 × 4 网格：固定 12 格，缺位画占位块（网格不破洞）。
            val slots = (0 until ShareCardLayoutPolicy.POSTER_PER_PAGE).map { data.covers.getOrNull(it) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                slots.chunked(ShareCardLayoutPolicy.POSTER_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { bmp ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(300.dp)
                                    .clip(NirikoShapes.ControlShape)
                                    .background(ShareCompose.CoverBg),
                            ) {
                                if (bmp != null) {
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            BrandFooter()
        }
    }
}

/**
 * 日历分享卡（1080×1400）：月份大字标题 + 7 列网格 + 底部三项统计。
 *
 * 行数、前置空格、点数上限全部走 [CalendarShareLayoutPolicy]，卡面只负责画。
 */
@Composable
private fun CalendarShareCard(data: CalendarShareCardData) {
    val rows = CalendarShareLayoutPolicy.rowCount(
        dayCount = data.days.size,
        leadingBlanks = data.leadingBlanks,
    )
    CardBase(ShareCardVariants.PAGE_WIDTH, ShareCardVariants.PAGE_HEIGHT) {
        Column(modifier = Modifier.fillMaxSize().padding(64.dp)) {
            Text(
                "Niriko 追番日历",
                style = TextStyle(fontSize = 26.sp),
                color = ShareCompose.Primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                data.monthLabel,
                style = TextStyle(fontSize = 52.sp),
                color = ShareCompose.Ink,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                data.headerMeta,
                style = TextStyle(fontSize = 24.sp),
                color = ShareCompose.Ink3,
            )
            Spacer(Modifier.height(36.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CalendarShareLayoutPolicy.WeekdayLabels.forEach { label ->
                    Text(
                        label,
                        style = TextStyle(fontSize = 20.sp),
                        color = ShareCompose.Ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            val byDay = data.days.associateBy { it.dayOfMonth }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(rows) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        repeat(CalendarShareLayoutPolicy.COLUMNS) { col ->
                            val dayNumber = row * CalendarShareLayoutPolicy.COLUMNS + col - data.leadingBlanks + 1
                            CalendarShareCell(
                                dayNumber = dayNumber,
                                dayCount = data.days.size,
                                subjectCount = byDay[dayNumber]?.subjectCount ?: 0,
                                cover = byDay[dayNumber]?.cover,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            if (data.stats.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    data.stats.forEach { (label, value) ->
                        Column {
                            Text(
                                value,
                                style = TextStyle(fontSize = 44.sp),
                                color = ShareCompose.Ink,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                label,
                                style = TextStyle(fontSize = 22.sp),
                                color = ShareCompose.Ink3,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            BrandFooter()
        }
    }
}

/** 单日格：天号 + 放送点数；不属于本月的格子只占位不画。 */
@Composable
private fun CalendarShareCell(
    dayNumber: Int,
    dayCount: Int,
    subjectCount: Int,
    cover: Bitmap?,
    modifier: Modifier = Modifier,
) {
    // 月末余位不是「31 号」：按当月实际天数判断，否则 11 月会出现一个不该存在的 31 格。
    if (!CalendarShareLayoutPolicy.isInMonth(dayNumber, dayCount)) {
        Box(modifier = modifier.height(120.dp))
        return
    }
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(NirikoShapes.ControlShape)
            .background(if (subjectCount > 0) ShareCompose.CoverBg else Color.Transparent),
    ) {
        // 与应用内日历日格同款：一图一格铺满 + 0.45 暗纱，保证白字可读
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        ) {
            Text(
                dayNumber.toString(),
                style = TextStyle(fontSize = 22.sp),
                color = if (cover != null) {
                    Color.White
                } else if (subjectCount > 0) {
                    ShareCompose.Ink
                } else {
                    ShareCompose.Ink3
                },
                fontWeight = if (subjectCount > 0) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(CalendarShareLayoutPolicy.dotCount(subjectCount)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ShareCompose.Primary),
                    )
                }
            }
        }
    }
}

/**
 * 作品信息分享卡（1080×1400）：上 55% 全幅封面 + 底部渐隐，下半是标题/评分/标签/短评/进度。
 *
 * 与旧版横向 3:2 卡的区别：封面从 3:4 侧栏变成通栏主视觉，文字信息压到封面下沿，
 * 更适合聊天窗口的竖屏预览（原型 07）。
 */
@Composable
private fun SubjectShowcaseCard(data: SubjectShowcaseCardData) {
    CardBase(ShareCardVariants.PAGE_WIDTH, ShareCardVariants.PAGE_HEIGHT) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(760.dp)) {
                if (data.cover != null) {
                    Image(
                        bitmap = data.cover.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(ShareCompose.CoverGradStart, ShareCompose.CoverGradEnd),
                                ),
                            ),
                    )
                }
                // 底部渐隐：把封面压到卡面底色，文字落在过渡区上
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, ShareCompose.Card.copy(alpha = 0.92f), ShareCompose.Card),
                            ),
                        ),
                )
                // 类型 chip（左上角）
                Box(
                    modifier = Modifier
                        .padding(56.dp)
                        .clip(NirikoShapes.ControlShape)
                        .background(ShareCompose.ChipBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        data.typeLabel,
                        style = TextStyle(fontSize = 20.sp),
                        color = ShareCompose.ChipInk,
                    )
                }
                // 标题块（封面下沿）
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 56.dp, vertical = 40.dp),
                ) {
                    Text(
                        data.title,
                        style = TextStyle(fontSize = 56.sp),
                        color = ShareCompose.Ink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    data.secondaryTitle?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = TextStyle(fontSize = 26.sp),
                            color = ShareCompose.Ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 56.dp)) {
                Spacer(Modifier.height(28.dp))
                // 评分 + 元信息
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        ShareShowcasePolicy.scoreText(data.communityScore),
                        style = TextStyle(fontSize = 44.sp),
                        color = ShareCompose.Primary,
                        fontWeight = FontWeight.Bold,
                    )
                    data.ratingTotal?.let {
                        Spacer(Modifier.width(10.dp))
                        Text(it, style = TextStyle(fontSize = 20.sp), color = ShareCompose.Ink3)
                    }
                    Spacer(Modifier.weight(1f))
                    data.metaText?.let {
                        Text(
                            it,
                            style = TextStyle(fontSize = 20.sp),
                            color = ShareCompose.Ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val tags = ShareShowcasePolicy.tagsToShow(data.tags)
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(NirikoShapes.ControlShape)
                                    .background(ShareCompose.ChipBg)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) { Text(tag, style = TextStyle(fontSize = 18.sp), color = ShareCompose.ChipInk) }
                        }
                    }
                }

                val impression = ShareShowcasePolicy.clampImpression(data.impression)
                if (impression != null) {
                    Spacer(Modifier.height(28.dp))
                    Text("我的一句话", style = TextStyle(fontSize = 18.sp), color = ShareCompose.Ink3)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "「" + impression + "」",
                        style = TextStyle(fontSize = 30.sp),
                        color = ShareCompose.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                data.progressText?.let { label ->
                    Spacer(Modifier.height(28.dp))
                    Text("我的进度", style = TextStyle(fontSize = 18.sp), color = ShareCompose.Ink3)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(NirikoShapes.ControlShape)
                                .background(ShareCompose.Line),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(data.progressFraction.coerceIn(0f, 1f))
                                    .background(ShareCompose.Primary),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = TextStyle(fontSize = 20.sp), color = ShareCompose.Ink2)
                    }
                }

                Spacer(Modifier.weight(1f))
                BrandFooter()
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
