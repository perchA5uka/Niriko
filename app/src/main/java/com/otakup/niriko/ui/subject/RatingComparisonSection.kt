package com.otakup.niriko.ui.subject

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.ui.components.GlassSectionCard
import com.otakup.niriko.ui.components.SectionHeader
import com.otakup.niriko.ui.components.StatLabel
import com.otakup.niriko.ui.components.StatNote
import com.otakup.niriko.ui.components.StatNumber
import com.otakup.niriko.ui.components.StatNumberTone
import com.otakup.niriko.ui.components.appleGlassCard
import com.otakup.niriko.ui.theme.chartBarDefaultColor
import top.yukonga.miuix.kmp.blur.Backdrop

/** 评分对比卡片：显示 Bangumi / Bilibili 社区评分 + 我的评分。 */
@Composable
fun RatingComparisonSection(
    subject: SubjectEntity,
    myRating: Float?,
    modifier: Modifier = Modifier,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    /** 争议度标签（评分分布标准差 → 「异口同声…厨黑大战」）。 */
    disputeLabel: String? = null,
    /** 本地库内同类型百分位（0-100）。口径是"你的收藏库"，不是全网排名。 */
    localPercentile: Int? = null,
) {
    val bgmRating = subject.ratingScore
    val biliRating = subject.biliScore
    val biliTotal = subject.biliRatingTotal
    // 无任何可展示数据则整卡隐藏
    if (bgmRating == null && myRating == null && biliRating == null && biliTotal == null) return

    Column(modifier = modifier.fillMaxWidth()) {
        // P1-1：本节标题原先是裸 Text(titleMedium + Bold)，与其它 section 的 SectionHeader
        // (titleMedium/w600 + onSurface) 字重不一致；横向内距沿用本页惯例（节内 16dp），
        // 与下方卡片的 16dp 对齐后左缘落在同一竖线上。
        SectionHeader(
            title = "评分",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        GlassSectionCard(
            backdrop = glassBackdrop,
            isScrolling = isScrolling,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // 我的评分
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StatLabel("我的评分")
                        // V-21（原型 A）：我的评分是这一块的「主指标」→ 24sp + 主色；未评分用弱色，
                        // 不再是 24sp 主色硬套一个「未评分」。
                        StatNumber(
                            value = myRating?.let { "%.1f".format(it) } ?: "未评分",
                            tone = if (myRating != null) StatNumberTone.Primary else StatNumberTone.Default,
                            color = if (myRating != null) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Bangumi 社区评分
                    if (bgmRating != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StatLabel("Bangumi")
                            StatNumber(
                                value = "%.1f".format(bgmRating),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            subject.ratingTotal?.let { total ->
                                StatNote("$total 人评价")
                            }
                        }
                    }
                    // Bilibili 社区评分
                    if (biliRating != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StatLabel("Bilibili")
                            StatNumber(
                                value = "%.1f".format(biliRating),
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            if (biliTotal != null) {
                                StatNote("$biliTotal 人评价")
                            }
                        }
                    }
                }

                // 阶段 7：争议度 + 本地库内百分位（都是纯本地计算，零请求）
                val insights = buildList {
                    disputeLabel?.let { add("评分争议度：$it") }
                    localPercentile?.let { add("本地库内百分位：$it%") }
                }
                if (insights.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    insights.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (localPercentile != null) {
                        Text(
                            text = "百分位基于你自己的收藏库同类作品，非全站排名",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 社区评分分布柱状图（1-10 分各档投票数）。使用 Compose Canvas 绘制。 */
@Composable
fun RatingDistributionChart(
    distribution: Map<Int, Int>, // key: 分数(1-10), value: 票数
    modifier: Modifier = Modifier,
    /** 嵌入横滑卡片时为 true：不自带标题、也不再套一层玻璃卡（外层已经提供材质与内边距）。 */
    compact: Boolean = false,
) {
    if (distribution.isEmpty()) return

    val maxCount = distribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    // V-20：柱色改用与其它图表同一份主题派生色板（chartBarDefaultColor = palette[0]），
    // 不再单独从 primary 造近似色 —— 换主题色时柱图与折线图保持同一族。
    val barColor = chartBarDefaultColor()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    // Paint 复用（每帧 new Paint 产生 GC 尖峰；实例在 composable 层 remember 一次）
    val labelPaint = remember { android.graphics.Paint() }

    // 柱图本体：卡片路径与 compact（横滑卡内）路径共用同一段绘制，避免两份代码漂移。
    val bars: @Composable () -> Unit = {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val barCount = 10
            val barWidth = size.width / barCount * 0.7f
            val gap = size.width / barCount * 0.3f
            val chartHeight = size.height - 20f

            for (i in 1..10) {
                val count = distribution[i] ?: 0
                val barHeight = (count.toFloat() / maxCount) * chartHeight
                val x = (i - 1) * (barWidth + gap) + gap / 2
                val y = size.height - barHeight - 20f

                // V-23：柱条此前是直角，与全站「圆角 / 胶囊」语言不一致。
                // 半径取 4dp 并按柱宽、柱高收敛：矮柱（1 票）如果直接用 4dp 圆角，
                // 圆角会比柱体本身还高，看着像水滴而不是柱条。
                val barCorner = minOf(4.dp.toPx(), barWidth / 2f, barHeight / 2f)
                drawRoundRect(
                    color = barColor.copy(alpha = 0.85f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barCorner, barCorner),
                )

                // 分数标签（复用 Paint，仅更新 color）
                // V-03：改造前这里是裸像素 24f —— 只在 3x 屏上等于 8sp，换密度就整体走形，
                // 也不会跟随系统字体缩放。改成 8sp 后 3x 屏上与改造前逐像素一致，其它密度正确换算。
                labelPaint.color = onSurfaceColor.toArgb()
                labelPaint.textSize = 8.sp.toPx()
                labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(
                    "$i",
                    x + barWidth / 2,
                    size.height - 4f,
                    labelPaint,
                )
            }
        }
    }

    if (compact) {
        Column(modifier = modifier.fillMaxWidth()) { bars() }
    } else {
        // V-20：改造前这张柱图是裸 Canvas 直接画在壁纸上 —— 没有材质托底，
        // 壁纸亮度一变就在「看不见」和「糊成一片」之间跳。这里套一张与其它 section 同款的
        // 玻璃卡；材质全部由 appleGlassCard 统一给出，不自己写背景色，才会跟着玻璃档位 /
        // 壁纸亮度自适应（D1：不改材质分层，只复用现有材质）。
        // P1-1：这张卡最初没有任何横向内距，卡片左缘比同一文件里的「评分」卡窄 16dp。
        // 本页所有 section 都是「节内自带 16dp + LazyColumn contentPadding 16」= 32dp，
        // 这里补齐 16dp 后卡缘才对齐。材质仍用 appleGlassCard 而非 GlassSectionCard：
        // 后者在 FULL 档会为这张卡新增一处实时折射（D1 约定不新增 RenderEffect 来源，
        // 同屏实时折射数已有预算），而本卡只是柱图数据容器，瓷面已足够。
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .appleGlassCard()
                .padding(16.dp),
        ) {
            SectionHeader(title = "评分分布")
            bars()
        }
    }
}
