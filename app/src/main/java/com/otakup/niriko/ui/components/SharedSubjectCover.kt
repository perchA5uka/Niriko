package com.otakup.niriko.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.animation.orSnap
import com.otakup.niriko.util.SubjectNavigationSeed

/** Registers loaded covers without changing the caller's fixed bounds or shared modifier. */
@Composable
fun SharedSubjectCover(
    subjectId: Long,
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val coverSeed = remember(subjectId) { SubjectNavigationSeed.coverFor(subjectId) }
    var overrideUrl by remember(subjectId) { mutableStateOf(coverSeed?.url) }
    LaunchedEffect(subjectId) {
        overrideUrl = context.nirikoApp.coverOverrideStore.overrideFor(subjectId)
    }
    val effectiveUrl = overrideUrl ?: coverUrl
    // 图片淡入（P1-5）：与 CoverImage 同一套「自绘 alpha」做法 —— Coil crossfade 保持关闭，
    // 因为这里画的是共享元素封面，飞行期再叠一层淡入会拖影。本组件拿不到 SharedTransitionScope，
    // 只有 reduceMotion 守卫（飞行期加载的极端情况写进 impl-log 风险项）。
    val reduceMotion = LocalReduceMotion.current
    // P2-2：与 CoverImage 共用同一张进程级「已揭示」表 —— 同一张封面在两个组件之间切换
    // （列表卡 → 详情页共享封面）也不该再闪一次。
    var arrived by remember(effectiveUrl) {
        mutableStateOf(ProcessCoverRevealTracker.isRevealed(effectiveUrl))
    }
    val revealAlpha by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = with(NirikoMotionSpecs) {
            tween<Float>(COVER_IMAGE_CROSSFADE_MS).orSnap(reduceMotion)
        },
        label = "sharedCoverRevealAlpha",
    )
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(effectiveUrl)
            .placeholderMemoryCacheKey(coverSeed?.takeIf { it.url == effectiveUrl }?.memoryCacheKey)
            .crossfade(false)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer { alpha = revealAlpha },
        contentScale = contentScale,
        onSuccess = { state ->
            arrived = true
            ProcessCoverRevealTracker.recordRevealed(effectiveUrl)
            if (effectiveUrl != null) {
                val drawable = state.result.drawable
                SubjectNavigationSeed.rememberCover(
                    subjectId,
                    effectiveUrl,
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    state.result.memoryCacheKey,
                )
            }
        },
    )
}
