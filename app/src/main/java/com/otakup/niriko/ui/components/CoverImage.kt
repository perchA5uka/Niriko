@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.otakup.niriko.ui.animation.subjectCoverPresentation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.otakup.niriko.nirikoApp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import coil.size.Dimension
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
    // Presentation feedback lives inside the untransformed shared bounds.
    coverContentModifier: Modifier = Modifier,
) {
    // 用户封面覆盖优先（CoverOverrideStore）
    val app = LocalContext.current.nirikoApp
    val coverId = subjectId ?: sharedElementKey?.removePrefix("cover_")?.toLongOrNull()
    val coverSeed = remember(coverId) { coverId?.let(com.otakup.niriko.util.SubjectNavigationSeed::coverFor) }
    var overrideUrl by remember(coverId) { mutableStateOf(coverSeed?.url) }
    LaunchedEffect(coverId) {
        if (coverId != null) overrideUrl = app.coverOverrideStore.overrideFor(coverId)
    }
    val effectiveCoverUrl = overrideUrl ?: coverUrl
    // 共享元素：key 非空且 scope 齐备时挂 sharedElement（bounds 过渡 = 整个封面）
    val sharedModifier = if (sharedElementKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(sharedElementKey),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = com.otakup.niriko.ui.animation.NirikoMotionSpecs.subjectCoverPathBounds(androidx.compose.ui.platform.LocalDensity.current.density),
            )
        }
    } else {
        Modifier
    }
    // Decode dimensions stay independent of animated constraints. No per-frame subcomposition.
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .then(sharedModifier)
            .subjectCoverPresentation(animatedVisibilityScope)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (effectiveCoverUrl != null) {
            val context = LocalContext.current
            val density = LocalDensity.current
            val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
            val reqW = coverStableDecodeWidth(screenWidthPx, requestWidth)
            val request = remember(context, effectiveCoverUrl, reqW, requestHeight, coverSeed?.memoryCacheKey) {
                ImageRequest.Builder(context)
                    .data(effectiveCoverUrl)
                    .placeholderMemoryCacheKey(coverSeed?.takeIf { it.url == effectiveCoverUrl }?.memoryCacheKey)
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .size(Size(Dimension.Pixels(reqW), requestHeight?.let { Dimension.Pixels(it) } ?: Dimension.Undefined))
                    .build()
            }
            var failed by remember(effectiveCoverUrl) { mutableStateOf(false) }
            // 加载失败时恢复占位图：v1.1.0 的 loading/error 槽位在改写为普通 AsyncImage 时被删掉，
            // 只剩纯色底 —— 卡片会永远显示一块灰色矩形，看不出「这张封面没有」。占位图先画，
            // 成功图后画会自然盖住它，因此不影响共享飞行的正常图片。
            if (failed) PlaceholderContent()
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().then(coverContentModifier),
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    val id = subjectId ?: sharedElementKey?.removePrefix("cover_")?.toLongOrNull()
                    if (id != null) {
                        failed = false
                        val drawable = state.result.drawable
                        com.otakup.niriko.util.SubjectNavigationSeed.rememberCover(
                            id, effectiveCoverUrl, drawable.intrinsicWidth, drawable.intrinsicHeight,
                            state.result.memoryCacheKey,
                        )
                    }
                },
                onError = { failed = true },

            )
        }
    }
}

internal fun coverStableDecodeWidth(screenWidthPx: Float, explicitWidth: Int?): Int =
    explicitWidth?.coerceAtLeast(1) ?: (screenWidthPx * 2f / 3f).roundToInt().coerceIn(360, 800)

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
