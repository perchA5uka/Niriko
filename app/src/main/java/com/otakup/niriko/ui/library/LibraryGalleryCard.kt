package com.otakup.niriko.ui.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.ui.components.FavoriteBadge
import com.otakup.niriko.ui.components.RatingBadge
import com.otakup.niriko.ui.components.StatusBadge
import com.otakup.niriko.ui.theme.LocalDarkTheme
import java.time.LocalDate

/**
 * 收藏页 · 画廊大卡（AniShelf featured 借鉴）。
 * 全宽沉浸大图 + 顶部起止日期胶囊 + 左上状态 / 右上收藏 + 底部标题与评分。
 * 阶段 A 用封面大图（拼贴为后续可选增强）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryGalleryCard(
    subject: SubjectEntity,
    collection: CollectionEntity,
    onClick: () -> Unit,
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 共享元素：与网格/列表卡同 key（cover_id），进入详情封面缩放飞入
    val sharedModifier = if (sharedElementKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(sharedElementKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        // 封面大图
        AsyncImage(
            model = subject.coverUrl,
            contentDescription = subject.displayTitle,
            modifier = Modifier.then(sharedModifier).fillMaxWidth().height(340.dp),
            contentScale = ContentScale.Crop,
        )
        // 底部渐变（保证标题可读）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f)),
                    ),
                ),
        )
        // 顶部：起止日期胶囊（左右）
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            GalleryDateChip("开始", collection.startDate, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            GalleryDateChip("结束", collection.finishDate, Modifier.weight(1f))
        }
        // 左上状态 / 右上收藏
        StatusBadge(collection.status, Modifier.align(Alignment.TopStart).padding(12.dp))
        FavoriteBadge(filled = true, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        // 底部标题 + 评分
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = subject.displayTitle,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (collection.rating != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    RatingBadge(collection.rating)
                }
            }
        }
    }
}

/** 画廊起止日期胶囊。 */
@Composable
private fun GalleryDateChip(label: String, date: LocalDate?, modifier: Modifier = Modifier) {
    val isDark = LocalDarkTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, color = if (isDark) Color.White.copy(0.7f) else Color(0xFF4a4f47))
            Text(
                date?.let { "${it.year}/${it.monthValue}/${it.dayOfMonth}" } ?: "—",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF1a1c19),
            )
        }
    }
}
