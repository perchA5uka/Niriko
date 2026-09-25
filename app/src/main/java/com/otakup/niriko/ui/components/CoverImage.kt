@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.otakup.niriko.nirikoApp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import kotlin.math.roundToInt

/**
 * 作品封面组件。
 * 封装 Coil SubcomposeAsyncImage，统一处理：
 * - 加载中/加载失败回退占位图
 * - 封面 URL 为 null 时直接显示占位图
 * - 统一圆角 / 宽高比
 * - 可选共享元素过渡（Kazumi 同款：列表卡封面 → 详情页封面缩放飞入）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CoverImage(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    aspectRatio: Float = 3f / 4f,
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    // 已知目标请求尺寸（px）。由调用方（如 CoverThumbnail）按固定宽高推算时传入，
    // 可跳过 onSizeChanged 状态往返，避免“默认尺寸→实际尺寸”的双重解码。
    requestWidth: Int? = null,
    requestHeight: Int? = null,
    // 阶段 E：条目 id，非空时优先读取用户封面覆盖（更换封面）。
    subjectId: Long? = null,
) {
    // 用户封面覆盖优先（CoverOverrideStore）
    val app = LocalContext.current.nirikoApp
    var overrideUrl by remember(subjectId) { mutableStateOf<String?>(null) }
    LaunchedEffect(subjectId) {
        if (subjectId != null) overrideUrl = app.coverOverrideStore.overrideFor(subjectId)
    }
    val effectiveCoverUrl = overrideUrl ?: coverUrl
    // 共享元素：key 非空且 scope 齐备时挂 sharedElement（bounds 过渡 = 整个封面）
    val sharedModifier = if (sharedElementKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(sharedElementKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }
    // 显示尺寸感知解码：从 BoxWithConstraints 一次取到显示尺寸（真实约束），
    // 直接算出请求尺寸（×2 超采样，cap 800×1000），避免「默认 600×800 → onSizeChanged → 二次解码」的双重解码。
    // 有固定请求尺寸时（requestWidth != null）直接使用，跳过约束推导。
    BoxWithConstraints(
        modifier = modifier
            .then(sharedModifier)
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (effectiveCoverUrl != null) {
            val maxWidthPx = constraints.maxWidth.takeIf { it > 0 && it != Constraints.Infinity }
            val reqW = requestWidth
                ?: maxWidthPx?.let { (it * 2f).roundToInt().coerceAtMost(800) }
                ?: 360
            val reqH = requestHeight
                ?: (reqW / aspectRatio).roundToInt().coerceAtMost(1000)
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(effectiveCoverUrl)
                    // crossfade 移除：Pager 滑动/预组合时多路并行淡入与手势争帧
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    // 超采样 ×2 保证共享元素过渡放大不糊，同时避免固定大图放大内存
                    .size(Size(reqW, reqH))
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { PlaceholderContent() },
                error = { PlaceholderContent() },
            )
        }
    }
}

@Composable
private fun PlaceholderContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = "无封面",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxSize(0.4f),
        )
    }
}

/**
 * 简化版封面组件：固定宽度，高度由 aspectRatio 自动计算。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CoverThumbnail(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    width: Dp = 80.dp,
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    subjectId: Long? = null,
) {
    // 固定请求尺寸：宽已知、高按 3:4 比例推算（与 CoverImage 的 ×2 超采样 + cap 逻辑一致），
    // 传入后跳过 onSizeChanged 状态往返，避免“默认尺寸→实际尺寸”的双重解码。
    val density = LocalDensity.current
    val requestWidth = with(density) { (width.toPx() * 2f).roundToInt().coerceAtMost(800) }
    val requestHeight = (requestWidth * 4f / 3f).roundToInt().coerceAtMost(1000)
    CoverImage(
        coverUrl = coverUrl,
        contentDescription = contentDescription,
        modifier = modifier.width(width),
        aspectRatio = 3f / 4f,
        requestWidth = requestWidth,
        requestHeight = requestHeight,
        sharedElementKey = sharedElementKey,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        subjectId = subjectId,
    )
}
