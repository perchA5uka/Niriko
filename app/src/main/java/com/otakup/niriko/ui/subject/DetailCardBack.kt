package com.otakup.niriko.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.verbFor
import com.otakup.niriko.data.remote.vndb.formatPlaytime
import com.otakup.niriko.ui.components.appleGlassCard
import com.otakup.niriko.viewmodel.SubjectDetailUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class DetailCardBackModel(
    val title: String,
    val status: String,
    val rating: String?,
    val progress: List<String>,
    val collected: String?,
    val tags: List<String>,
    val impression: String?,
)

private val collectionDateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT)

/** A projection of existing personal records, not community metadata or a new entity. */
internal fun detailCardBackModel(
    subject: SubjectEntity,
    state: SubjectDetailUiState,
    zoneId: ZoneId = ZoneId.systemDefault(),
): DetailCardBackModel? {
    if (!state.isInCollection) return null
    fun countText(count: Int?, total: Int?, unit: String): String? = count?.takeIf { it >= 0 }?.let {
        if (total != null && total > 0) "$it / $total $unit" else "$it $unit"
    }
    val progress = when (subject.type) {
        SubjectType.GAME -> listOfNotNull(state.watchedEpisodes?.takeIf { it >= 0 }?.let {
            if (it == 0) "0m" else formatPlaytime(it)
        })
        SubjectType.BOOK, SubjectType.MANGA -> listOfNotNull(
            countText(state.watchedEpisodes, subject.totalEpisodes, "话"),
            countText(state.watchedVolumes, subject.volumes, "卷"),
        )
        SubjectType.MUSIC -> listOfNotNull(countText(
            state.watchedTrackIds.takeIf { it.isNotEmpty() }?.size ?: state.watchedEpisodes,
            subject.totalEpisodes ?: state.episodes.size.takeIf { it > 0 }, "首",
        ))
        else -> listOfNotNull(countText(state.watchedEpisodes, subject.totalEpisodes, "集"))
    }
    return DetailCardBackModel(
        title = subject.displayTitle,
        status = state.currentStatus.verbFor(subject.type),
        rating = state.myRating?.takeIf { it.isFinite() && it in 0f..10f }?.let {
            String.format(Locale.getDefault(), "%.1f / 10", it)
        },
        progress = progress,
        collected = state.collectionCreateTime?.takeIf { it > 0L }?.let {
            Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate().format(collectionDateFormat)
        },
        tags = state.personalTags.map(String::trim).filter(String::isNotEmpty),
        impression = state.personalImpression?.trim()?.takeIf(String::isNotEmpty),
    )
}

@Composable
internal fun DetailCardBack(
    model: DetailCardBackModel,
    shape: Shape,
    modifier: Modifier = Modifier,
    material: com.otakup.niriko.data.model.CardMaterialState = com.otakup.niriko.data.model.CardMaterialState(),
    lighting: com.otakup.niriko.ui.animation.PressTiltLighting? = null,
    breathing: () -> Float = { 0f },
) {
    Column(
        modifier = modifier.fillMaxSize()
            .appleGlassCard(shape = shape, isCollectionCard = true,
                material = material, lighting = lighting, breathing = breathing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("NIRIKO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("PERSONAL COLLECTION", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(model.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BackField("状态", model.status, Modifier.weight(1f))
            model.rating?.let { BackField("评分", it, Modifier.weight(1f)) }
        }
        model.progress.takeIf { it.isNotEmpty() }?.let { BackField("进度", it.joinToString(" · ")) }
        model.collected?.let { BackField("收藏", it) }
        model.tags.takeIf { it.isNotEmpty() }?.let { BackField("标签", it.joinToString(" · ")) }
        model.impression?.let {
            Text("“$it”", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BackField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
