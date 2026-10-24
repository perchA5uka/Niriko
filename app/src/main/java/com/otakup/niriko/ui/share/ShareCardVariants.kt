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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    suspend fun renderRating(context: Context, host: ShareBitmapHost, data: RatingComparisonCardData): Bitmap =
        host.renderContent(context, RATING_WIDTH, RATING_HEIGHT) { RatingComparisonCard(data) }

    suspend fun renderWeekly(context: Context, host: ShareBitmapHost, data: WeeklyReportCardData): Bitmap =
        host.renderContent(context, PAGE_WIDTH, PAGE_HEIGHT) { WeeklyReportCard(data) }

    suspend fun renderPartitionPage(context: Context, host: ShareBitmapHost, data: PartitionPosterCardData): Bitmap =
        host.renderContent(context, PAGE_WIDTH, PAGE_HEIGHT) { PartitionPosterCard(data) }

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
                        modifier = Modifier.width(300.dp).height(420.dp).clip(RoundedCornerShape(16.dp)),
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
                                    .clip(RoundedCornerShape(12.dp))
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
