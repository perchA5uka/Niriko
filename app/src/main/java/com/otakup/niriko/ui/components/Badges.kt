package com.otakup.niriko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.verbFor
import com.otakup.niriko.ui.theme.GlassChipTokens
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect
import com.otakup.niriko.ui.theme.statusTone

/**
 * 彩色胶囊徽标体系（AniShelf 借鉴配色）：
 * - 状态：在看橙 / 看过绿 / 想看灰 / 搁置紫；
 * - 评分 ★ 黄 / 集数 S1E· 蓝。
 *
 * ## F05：海报小件统一材质 + 浅色白底
 *
 * 改造前四种小件各自写死 `Color.Black.copy(alpha = 0.55f)`（浅色模式下也是黑纱），
 * 文字直接用品牌色 —— 在浅色界面上既突兀、又和「浅色白底」的要求相反。
 *
 * 现在统一走 [GlassChipTokens]：
 * - 底色 = 浅色「浅白半透明」/ 深色「半透明黑」，并随背后图片亮度轻微自适应；
 * - 文字 = 品牌色经 [GlassChipTokens.ensureReadable] 调到**最坏情况合成底**上达标（AA 4.5:1）——
 *   「浅底 + 状态橙」这类旧实现必然不达标的组合现在也成立（橙色会被压暗，色相不变）；
 * - 玻璃档位关闭 / 低端降级时自动换成更实的底（可读性优先于玻璃观感）。
 *
 * 对比度不是估的：每个状态色 × 两种主题的合成底都在 GlassChipTokensTest 里算过。
 */

/** 观看状态 → 徽标色（在看橙 / 看过绿 / 想看灰 / 搁置紫）。 */
fun watchStatusColor(status: WatchStatus): Color = when (status) {
    WatchStatus.WATCHING -> Color(0xFFF59E0B)
    WatchStatus.COMPLETED -> Color(0xFF22C55E)
    WatchStatus.PLAN_TO_WATCH -> Color(0xFF9CA3AF)
    WatchStatus.ON_HOLD -> Color(0xFF8B5CF6)
    WatchStatus.DROPPED -> Color(0xFF9CA3AF)
}

/** 评分 ★ 的颜色（琥珀）。 */
private val RATING_ACCENT = Color(0xFFF59E0B)

/** 收藏心的颜色（红）。 */
private val FAVORITE_ACCENT = Color(0xFFDC2626)

/** 集数胶囊的底色（蓝）。 */
private val EPISODE_ACCENT = Color(0xFF3B82F6)

/**
 * 小件的材质上下文：底色 + 「在上面写字要用的颜色」（已按 [GlassChipTokens.ensureReadable] 调整）。
 *
 * 抽出来是因为四个小件必须**同底**：底色各自算会让同一张海报上的三个胶囊深浅不一。
 */
@Composable
private fun chipSurface(): Pair<Color, (Color) -> Color> {
    val isDark = LocalDarkTheme.current
    val luma = LocalGlassLuminance.current
    val glassEnabled = GlassChipTokens.glassEnabled(LocalGlassEffect.current, LocalCardGlassLevel.current)
    val materialBase = GlassChipTokens.onImageBackground(isDark, luma, glassEnabled)
    // Compact image badges need a stable backing, otherwise AA adjustment bleaches semantic accents.
    val base = if (isDark) materialBase.copy(alpha = maxOf(materialBase.alpha, 0.80f)) else materialBase
    val worst = GlassChipTokens.worstCaseBackdrop(isDark, base)
    return base to { preferred: Color -> GlassChipTokens.ensureReadable(preferred, worst) }
}

/** 状态胶囊徽标：色点 + 文字。 */
@Composable
fun StatusBadge(
    status: WatchStatus,
    modifier: Modifier = Modifier,
    subjectType: SubjectType? = null,
    shape: Shape = RoundedCornerShape(999.dp),
) {
    val tone = statusTone(status)
    val (base, readable) = chipSurface()
    val textColor = remember(base, tone.accent) { readable(tone.accent) }
    val dotColor = tone.accent
    Row(
        modifier = modifier
            .clip(shape)
            .background(base, shape)
            .border(0.5.dp, GlassChipTokens.chipBorder(LocalDarkTheme.current), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.size(6.dp).background(dotColor, CircleShape),
        ) {}
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        Text(
            // 阶段 B：按类型映射动词（看/读/玩/听）
            text = subjectType?.let { status.verbFor(it) } ?: status.label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 评分胶囊徽标：★ 数字。 */
@Composable
fun RatingBadge(
    rating: Float?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(999.dp),
) {
    if (rating == null) return
    val (base, readable) = chipSurface()
    val textColor = remember(base) { readable(RATING_ACCENT) }
    Row(
        modifier = modifier
            .clip(shape)
            .background(base, shape)
            .border(0.5.dp, GlassChipTokens.chipBorder(LocalDarkTheme.current), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("★", color = RATING_ACCENT, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.Spacer(Modifier.size(2.dp))
        Text(
            text = formatRating(rating),
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 集数胶囊徽标：S1E· 或 n 集。 */
@Composable
fun EpisodeBadge(
    text: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(999.dp),
) {
    val (base, readable) = chipSurface()
    val textColor = remember(base) { readable(EPISODE_ACCENT) }
    Row(
        modifier = modifier
            .clip(shape)
            .background(base, shape)
            .border(0.5.dp, GlassChipTokens.chipBorder(LocalDarkTheme.current), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** 收藏心徽标（已收藏标记）。 */
@Composable
fun FavoriteBadge(
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    shape: Shape = RoundedCornerShape(999.dp),
) {
    val (base, readable) = chipSurface()
    val dark = LocalDarkTheme.current
    val textColor = remember(base, dark) { GlassChipTokens.ensureReadable(FAVORITE_ACCENT, GlassChipTokens.worstCaseBackdrop(dark, base), 3f) }
    Row(
        modifier = modifier
            .clip(shape)
            .background(base, shape)
            .border(0.5.dp, GlassChipTokens.chipBorder(LocalDarkTheme.current), shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (filled) "♥" else "♡", color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatRating(rating: Float): String =
    if (rating == rating.toInt().toFloat()) rating.toInt().toString()
    else String.format("%.1f", rating)