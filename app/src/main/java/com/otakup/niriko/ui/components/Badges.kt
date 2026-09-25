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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.verbFor
import com.otakup.niriko.ui.theme.statusTone

/**
 * 彩色胶囊徽标体系（AniShelf 借鉴配色）：
 * - 状态：在看橙 / 看过绿 / 想看灰 / 搁置紫；
 * - 评分 ★ 黄 / 集数 S1E· 蓝。
 * 取代现有纯文本状态标签，可在海报网格、列表行、详情页复用。
 */

/** 观看状态 → 徽标色（在看橙 / 看过绿 / 想看灰 / 搁置紫）。 */
fun watchStatusColor(status: WatchStatus): Color = when (status) {
    WatchStatus.WATCHING -> Color(0xFFF59E0B)
    WatchStatus.COMPLETED -> Color(0xFF22C55E)
    WatchStatus.PLAN_TO_WATCH -> Color(0xFF9CA3AF)
    WatchStatus.ON_HOLD -> Color(0xFF8B5CF6)
    WatchStatus.DROPPED -> Color(0xFF9CA3AF)
}

/** 状态胶囊徽标：色点 + 文字。 */
@Composable
fun StatusBadge(
    status: WatchStatus,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    subjectType: SubjectType? = null,
    shape: Shape = RoundedCornerShape(999.dp),
) {
    val tone = statusTone(status)
    val chipShape = shape
    // on-image chip 行业做法：黑纱底，不用灰色底（避免在封面上变"灰泥点"）
    Row(
        modifier = modifier
            .clip(chipShape)
            .background(Color.Black.copy(alpha = 0.55f), chipShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.size(6.dp).background(tone.accent, CircleShape),
        ) {}
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        Text(
            // 阶段 B：按类型映射动词（看/读/玩/听）
            text = subjectType?.let { status.verbFor(it) } ?: status.label,
            color = tone.accent,
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
    val chipShape = shape
    Row(
        modifier = modifier
            .clip(chipShape)
            .background(Color.Black.copy(alpha = 0.55f), chipShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("★", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.Spacer(Modifier.size(2.dp))
        Text(
            text = formatRating(rating),
            color = Color(0xFFF59E0B),
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
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF3B82F6).copy(alpha = 0.95f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** 收藏心徽标（已收藏标记）。 */
@Composable
fun FavoriteBadge(
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    shape: Shape = RoundedCornerShape(999.dp),
) {
    val chipShape = shape
    Row(
        modifier = modifier
            .clip(chipShape)
            .background(Color.Black.copy(alpha = 0.55f), chipShape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (filled) "♥" else "♡", color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatRating(rating: Float): String =
    if (rating == rating.toInt().toFloat()) rating.toInt().toString()
    else String.format("%.1f", rating)
