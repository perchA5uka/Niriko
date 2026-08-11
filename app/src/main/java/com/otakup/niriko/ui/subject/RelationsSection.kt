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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.LocalDarkTheme
import coil.compose.AsyncImage
import com.otakup.niriko.data.remote.SubjectRelationInfo

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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val isDark = LocalDarkTheme.current
    // 卡片相对窗口的位置（背景墙从窗口 0,0 铺满）→ 模糊图 srcOffset 用
    var cardGlobalPos by remember { mutableStateOf(IntOffset.Zero) }
    val coverBitmap = blurredCover?.asImageBitmap()

    Box(
        modifier = Modifier
            .width(120.dp)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                cardGlobalPos = IntOffset(pos.x.toInt(), pos.y.toInt())
            }
            // 圆角裁剪必须在 drawWithCache 之前：Modifier 链外层先绘制，
            // clip 在 drawWithCache 内层时裁剪不到玻璃矩形（会露出方角）。
            // 口径与其他卡片（appleGlassCard 默认 16.dp）统一。
            .clip(RoundedCornerShape(16.dp))
            .drawWithCache {
                // 共享模糊封面作玻璃底：按卡片窗口位置偏移裁剪（滚动时背景跟随），
                // 全程只模糊一次（BlurredCoverCache），多张小卡零额外模糊开销。
                val bitmap = coverBitmap
                onDrawBehind {
                    if (bitmap != null) {
                        // 背景墙铺满窗口，卡片偏移即背景偏移；
                        // 模糊图尺寸与窗口按比例映射 srcOffset。
                        val scaleX = bitmap.width.toFloat() / size.width.toFloat() * 1.2f
                        val scaleY = bitmap.height.toFloat() / size.height.toFloat() * 1.2f
                        val srcOffset = IntOffset(
                            (cardGlobalPos.x * scaleX).toInt(),
                            (cardGlobalPos.y * scaleY).toInt(),
                        )
                        val drawW = size.width
                        val drawH = size.height
                        val srcLeft = srcOffset.x.coerceIn(0, bitmap.width - 1)
                        val srcTop = srcOffset.y.coerceIn(0, bitmap.height - 1)
                        val srcRight = (srcLeft + drawW * scaleX).toInt().coerceAtMost(bitmap.width)
                        val srcBottom = (srcTop + drawH * scaleY).toInt().coerceAtMost(bitmap.height)
                        if (srcRight > srcLeft && srcBottom > srcTop) {
                            drawImage(
                                image = bitmap,
                                srcOffset = IntOffset(srcLeft, srcTop),
                                srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            )
                        }
                    }
                    // 主题 tint 遮罩（浅色白 / 深色黑）：保证前景可读性，与背景墙蒙层统一
                    drawRect(
                        color = if (isDark) Color.Black.copy(alpha = 0.45f)
                                else Color.White.copy(alpha = 0.55f),
                    )
                    // 顶部受光高光 + 底部微沉降（玻璃层次）
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = if (isDark) 0.08f else 0.02f),
                            ),
                        ),
                    )
                    // 圆角高光边框（与 clip 16.dp 一致）
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (isDark) 0.15f else 0.5f),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(16f, 16f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            // 封面（3:4 比例）— 共享元素：跳转关联详情页时缩放飞入
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
            AsyncImage(
                model = relation.imageUrl,
                contentDescription = relation.titleCN ?: relation.title,
                modifier = Modifier
                    .then(sharedModifier)
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(6.dp))
            // 关联类型徽标
            relation.relation?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 标题
            Text(
                text = relation.titleCN ?: relation.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
