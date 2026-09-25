package com.otakup.niriko.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AniShelf 分享卡（安利海报风格）：全封面 + 底部黑渐变 + 底部左对齐标题/胶囊。
 * 供分享预览与导出共用。配置项：圆角、显示评分/进度/标签。
 */
@Composable
fun SharePosterCard(
    data: ShareCardData,
    showRating: Boolean = true,
    showProgress: Boolean = true,
    showTags: Boolean = true,
    rounded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = if (rounded) RoundedCornerShape(48.dp) else RoundedCornerShape(0.dp)
    Box(
        modifier = modifier
            .background(Color(0xFF101410), shape)
            .shadow(if (rounded) 30.dp else 0.dp, shape),
    ) {
        // 封面/渐变底
        if (data.cover != null) {
            Image(bitmap = data.cover.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A3A3A), Color(0xFF101410)))))
        }
        // 底部黑渐变
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.1f), Color.Black.copy(0.65f), Color.Black.copy(0.85f)))));
        // 底部左对齐内容
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(28.dp),
        ) {
            // detail 行（类型·状态胶囊 + 可选评分/进度/标签）
            val detail = listOfNotNull(
                data.typeLabel,
                data.statusLabel,
                if (showRating) data.score?.let { "评分 " + it } else null,
                if (showProgress) data.progressText else null,
            ).joinToString("  ·  ")
            if (detail.isNotBlank()) {
                Text(
                    detail.uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(0.85f),
                    modifier = Modifier.background(Color.White.copy(0.12f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                data.primaryTitle,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(4.dp)),
            )
            if (!data.secondaryTitle.isNullOrBlank()) {
                Text(data.secondaryTitle, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            // 标签行（可选）
            if (showTags && data.tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row {
                    data.tags.take(4).forEach { tag ->
                        Text("#" + tag, fontSize = 12.sp, color = Color.White.copy(0.9f), modifier = Modifier.background(Color.White.copy(0.15f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp).padding(end = 6.dp))
                    }
                }
            }
        }
    }
}