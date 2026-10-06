package com.otakup.niriko.ui.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.util.TrackListPolicy

/**
 * 音乐曲目列表（F04）：按碟片分组、逐首勾选标记已听。
 *
 * 这一份**同时**被详情页与状态编辑弹层使用（§9.2 的验收线是「详情和编辑页进度一致」——
 * 两处共用同一个 `watchedTrackIds` 状态与同一份渲染代码，一致性是结构保证的，不是靠对齐）。
 *
 * 三个来自 §9.2 的硬要求：
 * - **只在音乐类型显示**：由调用方判断类型（这里只管渲染），两处调用都在 MUSIC 分支里；
 * - **取消不写入**：本组件的勾选只改调用方持有的本地状态（详情页的 `watchedTrackIdsLocal`），
 *   落库仍走各自的保存路径 —— 因此「打开编辑 → 勾两首 → 取消」什么都不会发生；
 * - **大量曲目折叠**：默认只渲染 [TrackListPolicy.PREVIEW_LIMIT] 首（[TrackListPolicy] 负责计数），
 *   其余用「展开剩余 N 首」放出，避免一次组合上百行。
 *
 * @param collapsible 详情页那边已经在 LazyColumn 里（只占一个 item），仍然折叠：
 *   一次组合上百行在低端机上就是可感知的卡顿，与它外面是不是惰性列表无关。
 */
@Composable
internal fun TrackListSection(
    episodes: List<EpisodeInfo>,
    watchedTrackIds: Set<Long> = emptySet(),
    onToggleTrack: (Long) -> Unit = {},
    collapsible: Boolean = true,
) {
    val groups = remember(episodes) { TrackListPolicy.groupByDisc(episodes) }
    val rows = remember(groups) { TrackListPolicy.flatten(groups) }
    // 折叠状态按作品生命周期保存：旋转/返回不该把用户展开的列表收回去
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = remember(rows, expanded, collapsible) {
        if (collapsible) TrackListPolicy.visibleRows(rows, expanded) else rows
    }
    val hidden = if (collapsible) TrackListPolicy.hiddenCount(rows.size, expanded) else 0
    val watched = remember(rows, watchedTrackIds) { TrackListPolicy.watchedCount(rows, watchedTrackIds) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            TrackListPolicy.headerText(watched, rows.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "点击曲目标记/取消已听",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // 碟片标题只在多碟时出现：单碟作品多一行「Disc 1」是噪音
        val showDiscLabels = groups.size > 1
        var lastDisc: Int? = null
        visible.forEach { row ->
            if (showDiscLabels && row.disc != lastDisc) {
                lastDisc = row.disc
                Text(
                    "Disc ${row.disc}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
            }
            TrackRow(
                track = row.track,
                isWatched = row.track.id in watchedTrackIds,
                onClick = { onToggleTrack(row.track.id) },
            )
        }
        if (collapsible && TrackListPolicy.shouldShowExpandControl(rows.size)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(TrackListPolicy.expandLabel(hidden, expanded))
            }
        }
    }
}

/** 单曲行：曲序 + 曲名 + 时长 + 类型标记 + 已听勾选。 */
@Composable
private fun TrackRow(
    track: EpisodeInfo,
    isWatched: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            track.sort.toInt().toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            // name_cn 常返回空字符串（非 null），需 isNotBlank 回退 name
            val title = track.nameCn?.takeIf { it.isNotBlank() } ?: track.name
            Text(
                if (title.isBlank()) "曲目 ${track.sort.toInt()}" else title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isWatched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val sub = listOfNotNull(
                track.trackTypeLabel(),
                track.durationSeconds.takeIf { it > 0 }?.let { formatTrackDuration(it) }
                    ?: track.duration?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            imageVector = if (isWatched) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (isWatched) "已听" else "未听",
            tint = if (isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 曲目类型标记（0=本篇 无标记，2=OP，3=ED，1=SP）。 */
private fun EpisodeInfo.trackTypeLabel(): String? = when (type) {
    2 -> "OP"
    3 -> "ED"
    1 -> "SP"
    else -> null
}

/** 秒数 → "mm:ss"。 */
private fun formatTrackDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
