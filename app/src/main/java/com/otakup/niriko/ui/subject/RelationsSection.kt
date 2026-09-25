package com.otakup.niriko.ui.subject

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.GlassSectionCard
import com.otakup.niriko.ui.theme.LocalDarkTheme
import coil.compose.AsyncImage
import com.otakup.niriko.data.remote.SubjectRelationInfo
import com.otakup.niriko.data.remote.displayTitle
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 关联条目区块（前后传/版本/系列等）。
 * 对齐 Bangumi-master 的 Relations 区块：横滑卡片，点击跳转对应条目。
 * 封面挂 sharedElement（key = "cover_关联subjectId"）→ 详情页跳转详情页时封面缩放飞入。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RelationsSection(
    relations: List<SubjectRelationInfo>,
    onRelationClick: (Long) -> Unit = {},
    blurredCover: Bitmap? = null,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    if (relations.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "关联条目",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        // 阶段 F（复原）：单个横滑卡片列表（与原始效果一致）
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 音乐条目的关联常指向同一作品多个关系（subjectId 重复），key 用 index 保证绝对唯一
            itemsIndexed(relations) { index, relation ->
                RelationCard(
                    relation = relation,
                    onClick = { onRelationClick(relation.subjectId) },
                    blurredCover = blurredCover,
                    glassBackdrop = glassBackdrop,
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RelationCard(
    relation: SubjectRelationInfo,
    onClick: () -> Unit,
    blurredCover: Bitmap? = null,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val scope = sharedTransitionScope
    val avScope = animatedVisibilityScope
    val sharedModifier = if (scope != null && avScope != null) {
        with(scope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState("cover_${relation.subjectId}"),
                animatedVisibilityScope = avScope,
            )
        }
    } else Modifier

    GlassSectionCard(
        backdrop = glassBackdrop,
        isScrolling = isScrolling,
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = 8.dp,
    ) {
        Box(Modifier.clickable(onClick = onClick)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(4.dp),
            ) {
                AsyncImage(
                    model = relation.imageUrl,
                    contentDescription = relation.displayTitle,
                    modifier = Modifier
                        .then(sharedModifier)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(6.dp))
                relation.relation?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = relation.displayTitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
