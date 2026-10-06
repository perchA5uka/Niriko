package com.otakup.niriko.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.otakup.niriko.nirikoApp
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
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(effectiveUrl)
            .placeholderMemoryCacheKey(coverSeed?.takeIf { it.url == effectiveUrl }?.memoryCacheKey)
            .crossfade(false)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onSuccess = { state ->
            if (effectiveUrl != null) {
                val drawable = state.result.drawable
                SubjectNavigationSeed.rememberCover(
                    subjectId, effectiveUrl, drawable.intrinsicWidth, drawable.intrinsicHeight,
                    state.result.memoryCacheKey,
                )
            }
        },
    )
}
