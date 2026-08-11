package com.otakup.niriko.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 分享卡片 · Compose 布局。
 *
 * 严格复刻 share-card-template.html 确认稿，本文件只做布局，不含任何 Canvas 绘制：
 *   - 最外层 Column 锁比例 aspectRatio(1080f/1400f)，背景 #101410；
 *   - 内边距 padding(上下 44/38, 左右 44)，与原型 .card padding 一致；
 *   - 底部品牌栏前强制插入 Spacer(Modifier.weight(1f))，复刻 CSS margin-top:auto 沉底；
 *   - 头部 Row(horizontalArrangement = spacedBy(24.dp))；
 *     封面固定 Modifier.width(240.dp).aspectRatio(3f/4f).clip(RoundedCornerShape(16.dp))；
 *     右侧信息区 Meta 用 Column(Modifier.weight(1f)) 包裹，防止溢出；
 *   - Meta 内按序：类型标签 → Spacer → 标题 → 副标题 → Spacer → 评分行，
 *     不使用 Box 叠放，杜绝重叠；
 *   - 简介 / 感想容器 fillMaxWidth()，长文本自然换行；
 *   - 评分行 Row(verticalAlignment = Alignment.Bottom, spacedBy(9.dp))，星与分数底对齐。
 */
object ShareCompose {
    val Card = Color(0xFF101410)
    val Ink = Color(0xFFE8EBE4)
    val Ink2 = Color(0xFFB7C0B2)
    val Ink3 = Color(0xFF8A9385)
    val Primary = Color(0xFF81C784)
    val ChipBg = Color(0xFF22301F)
    val ChipInk = Color(0xFFB8E6B0)
    val Line = Color(0xFF2A312A)
    val Star = Color(0xFF81C784)
    val CoverBg = Color(0xFF182019)
    val TagBg = Color(0x1AB8E6B0)
    val TagBorder = Color(0x38B8E6B0)
    val CoverGradStart = Color(0xFF2E5A36)
    val CoverGradEnd = Color(0xFF0F1F14)
    val CoverText = Color(0xFF9ECBA2)
    val GlassBg = Color(0x1A81C784)      // 评分对比卡：半透明绿玻璃底
    val GlassBorder = Color(0x29B8E6B0)  // 评分对比卡：微光描边
}

/** 分享卡数据（作品详情 / 个人收藏共用）。 */
data class ShareCardData(
    val cover: Bitmap? = null,
    val typeLabel: String = "",
    val primaryTitle: String = "",
    val secondaryTitle: String? = null,
    val score: Float? = null,          // 我的评分（收藏卡）
    val communityScore: Float? = null, // Bangumi 社区分（作品卡/收藏卡均为 ratingScore）
    val ratingTotal: String? = null,   // 评价人数文案，如「2.3 万人」
    val metaText: String? = null,      // 元信息行：「平台 · 集数/卷数 · 年份」
    val totalHint: String? = null,     // 保留兼容位（新版不再使用，改用对比卡）
    val summary: String? = null,       // 作品简介（仅作品详情卡）
    val communityTags: List<String> = emptyList(), // 社区标签（仅作品卡，分享时取前 10）
    // ---- 收藏区 ----
    val statusLabel: String? = null,   // 状态 chip（看过/抛弃…）
    val progressText: String? = null,  // 「28 / 28 集」
    val progressRatio: Float = 0f,
    val tags: List<String> = emptyList(),
    val impressions: String? = null,
    val dateText: String? = null,
) {
    val isCollection: Boolean get() = statusLabel != null || tags.isNotEmpty() || impressions != null
}

/**
 * 分享卡片顶层布局（横向版）：
 * 根 Box 锁比例 aspectRatio(1620f/1080f)（3:2）；背景渐变 + 右上柔光；
 * 内层 Row：左侧全高 Hero 封面 + 右侧信息列（TypeChip → 标题 → 原名 → 元信息行
 * → 评分对比卡 → 简介/收藏区 → Spacer(weight 1f) → 品牌行沉底）。
 */
@Composable
fun ShareCardLayout(data: ShareCardData, modifier: Modifier = Modifier) {
    val isCollection = data.isCollection
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1620f / 1080f)
            // §12 材质深度：水平渐变（左侧受光 → 中段基准 → 右侧沉降），同色相亮度微调
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF161C16),
                        ShareCompose.Card,
                        Color(0xFF0B0E0B),
                    ),
                ),
            )
            // 右上 radial 柔光：光从右上"咬住"卡片
            .drawBehind {
                val h = size.height
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x2E81C784), Color(0x0081C784)),
                        center = Offset(size.width * 0.9f, -h * 0.08f),
                        radius = h * 1.2f,
                    ),
                    size = size,
                )
            },
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧：全高 Hero 封面（810×1080，3:4 原比例）——版面第一视觉焦点
            HeroCoverShare(data = data, modifier = Modifier.width(810.dp).fillMaxHeight())
            // 右侧信息栏
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(start = 44.dp, end = 44.dp, top = 48.dp, bottom = 38.dp),
            ) {
                HeadMeta(data = data)
                // 主体：简介（含社区标签） 或 收藏区
                if (isCollection) {
                    Spacer(Modifier.height(20.dp))
                    CollectionSection(data = data)
                } else if (!data.summary.isNullOrBlank() || data.communityTags.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    if (!data.summary.isNullOrBlank()) {
                        // 简介：横向卡下半区空间富余，放宽到 8 行
                        Text(
                            text = data.summary,
                            color = ShareCompose.Ink2,
                            fontSize = 15.5.sp,
                            lineHeight = 29.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // 社区标签：填充下半区留白（前 10 个，样式与收藏区一致）
                    CommunityTagsSection(tags = data.communityTags)
                }
                // 沉底
                Spacer(Modifier.weight(1f))
                BrandBar(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** 社区标签区：小节标题 + FlowRow chips（复用收藏区 Tag 样式与品牌色板）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityTagsSection(tags: List<String>) {
    if (tags.isEmpty()) return
    Spacer(Modifier.height(20.dp))
    Text(
        text = "社区标签",
        color = ShareCompose.Ink3,
        fontSize = 12.5.sp,
        letterSpacing = 0.4.sp,
    )
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            val label = if (tag.startsWith("#")) tag else "# $tag"
            Text(
                text = label,
                color = ShareCompose.ChipInk,
                fontSize = 12.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ShareCompose.TagBg)
                    .border(1.dp, ShareCompose.TagBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
    }
}

/** 全高 Hero 封面：810dp 宽 × 满高，贴左/上/下边；右缘圆角 24dp（海报感）；品牌绿 1dp 微光描边 + 顶部受光上沿。 */
@Composable
private fun HeroCoverShare(data: ShareCardData, modifier: Modifier = Modifier) {
    val cover = data.cover
    val shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(ShareCompose.CoverBg)
            .border(1.dp, ShareCompose.Primary.copy(alpha = 0.16f), shape),
    ) {
        if (cover != null && !cover.isRecycled) {
            Image(
                painter = remember(cover) { BitmapPainter(cover.asImageBitmap()) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ShareCompose.CoverGradStart, ShareCompose.CoverGradEnd),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "封面占位", color = ShareCompose.CoverText, fontSize = 20.sp)
            }
        }
        // §12 受光上沿：顶部 2dp 白色渐变高光条（已 clip 圆角，天然裁切）
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x2EFFFFFF), Color(0x00FFFFFF)),
                    ),
                ),
        )
    }
}

/** 右侧信息区：类型 chip → 标题 → 原名 → 元信息行 → 评分对比卡 →（收藏态）进度短条。 */
@Composable
private fun HeadMeta(data: ShareCardData, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        TypeChip(text = data.typeLabel.takeIf { it.isNotBlank() } ?: "")

        if (data.primaryTitle.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = data.primaryTitle,
                color = ShareCompose.Ink,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                // §15 Apple 大标题：36sp 负字距收紧（小字副标题保持正字距）
                letterSpacing = (-0.7).sp,
            )
        }

        if (!data.secondaryTitle.isNullOrBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = data.secondaryTitle,
                color = ShareCompose.Ink2,
                fontSize = 17.sp,
                letterSpacing = 0.3.sp,
            )
        }

        // 元信息行（详情页 SubjectMetaSection 语义）
        if (!data.metaText.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = data.metaText,
                color = ShareCompose.Ink3,
                fontSize = 12.5.sp,
                letterSpacing = 0.3.sp,
            )
        }

        // 评分对比卡（我的评分 / Bangumi / 评价人数，按数据可用性渲染）
        if (data.score != null || data.communityScore != null || !data.ratingTotal.isNullOrBlank()) {
            Spacer(Modifier.height(18.dp))
            RatingCompareCard(data = data)
        }

        // 观看进度：并入信息区尾部（纤细短条，不再横贯全宽切断版面）
        if (!data.progressText.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            ProgressLine(data = data)
        }
    }
}

/** 观看进度：一行「状态 · 进度文本」+ 132dp × 4dp 纤细圆角短条（§12 细节化，不喧宾夺主）。 */
@Composable
private fun ProgressLine(data: ShareCardData) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = data.statusLabel?.takeIf { it.isNotBlank() } ?: "观看进度",
                color = ShareCompose.Ink2,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = data.progressText.orEmpty(),
                color = ShareCompose.Ink3,
                fontSize = 12.5.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(132.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(ShareCompose.Line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(data.progressRatio.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ShareCompose.Primary),
            )
        }
    }
}

/** 类型 chip：灰绿底 + 圆点 + 亮绿字，完全对齐原型 .type。 */
@Composable
private fun TypeChip(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ShareCompose.ChipBg)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(ShareCompose.ChipInk))
        if (text.isNotBlank()) {
            Text(
                text = text,
                color = ShareCompose.ChipInk,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

/**
 * 评分对比卡（迁移详情页 RatingComparisonSection）：
 * 三栏大数字（我的评分=绿高亮 / Bangumi / 评价人数），静态玻璃质感（半透明绿底 + 微光描边 + 顶部受光）。
 */
@Composable
private fun RatingCompareCard(data: ShareCardData) {
    val cols = buildList {
        if (data.score != null && data.score > 0f) add(RatingCol("我的评分", "%.1f".format(data.score), highlighted = true))
        if (data.communityScore != null && data.communityScore > 0f) add(RatingCol("Bangumi", "%.1f".format(data.communityScore), highlighted = false))
        if (!data.ratingTotal.isNullOrBlank()) add(RatingCol("评价人数", data.ratingTotal, highlighted = false))
    }
    if (cols.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ShareCompose.GlassBg)
            .border(1.dp, ShareCompose.GlassBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalArrangement = if (cols.size == 1) Arrangement.Center else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cols.forEach { col ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = col.label,
                    color = ShareCompose.Ink3,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.4.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = col.value,
                    color = if (col.highlighted) ShareCompose.Primary else ShareCompose.Ink2,
                    fontSize = if (col.highlighted) 28.sp else 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/** 评分对比列数据。 */
private data class RatingCol(
    val label: String,
    val value: String,
    val highlighted: Boolean,
)

private fun buildListOf(vararg cols: RatingCol): List<RatingCol> = cols.toList()

/** 收藏区：标签 → 感想引言 → 日期（状态/进度已并入头部 InfoMeta 的 ProgressLine）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionSection(data: ShareCardData) {
    Column(Modifier.fillMaxWidth()) {
        // 状态/进度已并入头部信息区（HeadMeta → ProgressLine）；收藏区去掉分隔线与整宽进度块，
        // 用 28dp 空气留白承接，上下两部分（信息/评价）保持连续（§12 去硬线 / §14 克制）

        // 标签（.tags，FlowRow 自动换行）
        if (data.tags.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                data.tags.forEach { tag ->
                    val label = if (tag.startsWith("#")) tag else "# $tag"
                    Text(
                        text = label,
                        color = ShareCompose.ChipInk,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShareCompose.TagBg)
                            .border(1.dp, ShareCompose.TagBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // 感想引言（.impression：绿色引号 + 正文，允许换行；横向卡限 4 行防溢出）
        if (!data.impressions.isNullOrBlank()) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "“",
                    color = ShareCompose.Primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = data.impressions,
                    color = ShareCompose.Ink2,
                    fontSize = 15.sp,
                    lineHeight = 27.75.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 日期（.dates）
        if (!data.dateText.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = data.dateText,
                color = ShareCompose.Ink3,
                fontSize = 12.5.sp,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

/** 底部品牌行：上边线 + NIRIKO（N 品牌色）左 + 分享来源右。 */
@Composable
private fun BrandBar(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // §12 分隔线渐隐（Apple scroll edge effect，替代 1px 硬线）
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(ShareCompose.Line, ShareCompose.Line.copy(alpha = 0f)),
                    ),
                ),
        )
        Spacer(Modifier.height(18.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "N",
                    color = ShareCompose.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "IRIKO",
                    color = ShareCompose.Ink2,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )
            }
            Text(
                text = "分享自 Niriko 追番收藏",
                color = ShareCompose.Ink3,
                fontSize = 11.5.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}