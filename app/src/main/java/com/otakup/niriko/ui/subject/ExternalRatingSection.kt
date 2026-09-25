package com.otakup.niriko.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.local.entity.ManualAwardEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.ui.components.GlassSectionCard
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 权威评分对照（多源）。
 *
 * 设计原则（来自调研结论）：
 * - **必须显示来源与标尺**：IMDb 8.6/10、Metacritic 87/100、Steam 好评率 96%、
 *   MusicBrainz 4.2/5 —— 混排而不标标尺会误导用户；
 * - **无免费 API 的机构单独成区**（Fami通 / Billboard / Oricon），明确标注「手动录入」，
 *   不伪装成自动抓取；
 * - 未配置 key 的源不出现（由仓库层过滤），整块无数据时整卡隐藏。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExternalRatingSection(
    ratings: List<ExternalRating>,
    manualAwards: List<ManualAwardEntity>,
    subjectType: SubjectType,
    showManualEntry: Boolean,
    onRefresh: () -> Unit = {},
    onAddManualAward: (sourceId: String, score: Float?, scoreMax: Float, rank: Int?, note: String?) -> Unit = { _, _, _, _, _ -> },
    onRemoveManualAward: (Long) -> Unit = {},
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier,
    /**
     * 已被 [RatingCarousel] 的横滑卡取代（阶段 C）；
     * 保留本组件供「非横滑」布局（如未来的平板/无障碍单列模式）复用。
     */
    embedded: Boolean = false,
) {
    if (ratings.isEmpty() && manualAwards.isEmpty() && !showManualEntry) return
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "权威评分",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新权威评分", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))

        GlassSectionCard(
            backdrop = glassBackdrop,
            isScrolling = isScrolling,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (ratings.isEmpty()) {
                    Text(
                        "暂无可用的权威评分。可在「设置 → 权威数据源」配置 TMDb / OMDb / IGDB 等密钥。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ratings.forEach { rating -> RatingPill(rating) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "各源标尺不同，仅作参考；曲线与分集评分请见「剧集」区。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (manualAwards.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "手动录入（无公开 API 的权威机构）",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    manualAwards.forEach { award ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = manualAwardLabel(award.sourceId),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(92.dp),
                            )
                            Text(
                                text = award.displayValue(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            award.note?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                            IconButton(onClick = { onRemoveManualAward(award.id) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (showManualEntry) {
                    Spacer(Modifier.height(10.dp))
                    AssistChip(
                        onClick = { showDialog = true },
                        label = { Text("录入权威机构成绩") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Fami通（40 分制）、Billboard、Oricon 等机构没有可用的公开 API，或条款禁止抓取，" +
                            "因此由你自己录入。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showDialog) {
        ManualAwardDialog(
            subjectType = subjectType,
            onDismiss = { showDialog = false },
            onConfirm = { sourceId, score, scoreMax, rank, note ->
                onAddManualAward(sourceId, score, scoreMax, rank, note)
                showDialog = false
            },
        )
    }
}

@Composable
private fun RatingPill(rating: ExternalRating) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = rating.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = rating.displayScore ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        val sub = buildList {
            rating.voteCount?.let { add("${formatCount(it)} 人") }
            rating.note?.let { add(it) }
        }.joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rating.sourceUrl?.let { url ->
            Text(
                text = "查看来源",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable { runCatching { uriHandler.openUri(url) } },
            )
        }
    }
}

@Composable
internal fun ManualAwardDialog(
    subjectType: SubjectType,
    onDismiss: () -> Unit,
    onConfirm: (String, Float?, Float, Int?, String?) -> Unit,
) {
    val options = manualAwardOptionsFor(subjectType)
    var selected by remember { mutableStateOf(options.first()) }
    var scoreText by remember { mutableStateOf("") }
    var rankText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("录入权威机构成绩") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { option ->
                        FilterChip(
                            selected = selected == option,
                            onClick = { selected = option },
                            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (selected.kind == AwardKind.SCORE) {
                    OutlinedTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it },
                        label = { Text("分数（满分 ${selected.max.toInt()}）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = rankText,
                        onValueChange = { rankText = it },
                        label = { Text("榜位（如 12 表示第 12 名）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注（榜期 / 奖项名，可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val score = scoreText.trim().toFloatOrNull()
                    val rank = rankText.trim().toIntOrNull()
                    onConfirm(
                        selected.id,
                        score,
                        selected.max,
                        rank,
                        noteText.trim().takeIf { it.isNotEmpty() },
                    )
                },
                enabled = scoreText.isNotBlank() || rankText.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private enum class AwardKind { SCORE, RANK }

private data class ManualAwardOption(
    val id: String,
    val label: String,
    val kind: AwardKind,
    val max: Float,
)

private fun manualAwardOptionsFor(type: SubjectType): List<ManualAwardOption> = when (type) {
    SubjectType.GAME -> listOf(
        ManualAwardOption("famitsu", "Fami通", AwardKind.SCORE, 40f),
        ManualAwardOption("metacritic_manual", "Metacritic", AwardKind.SCORE, 100f),
        ManualAwardOption("other", "其它", AwardKind.SCORE, 100f),
    )
    SubjectType.MUSIC -> listOf(
        ManualAwardOption("billboard", "Billboard", AwardKind.RANK, 0f),
        ManualAwardOption("oricon", "Oricon", AwardKind.RANK, 0f),
        ManualAwardOption("metacritic_manual", "Metacritic", AwardKind.SCORE, 100f),
        ManualAwardOption("other", "其它", AwardKind.SCORE, 100f),
    )
    SubjectType.BOOK -> listOf(
        ManualAwardOption("douban_manual", "豆瓣读书", AwardKind.SCORE, 10f),
        ManualAwardOption("goodreads_manual", "Goodreads", AwardKind.SCORE, 5f),
        ManualAwardOption("other", "其它", AwardKind.SCORE, 100f),
    )
    else -> listOf(
        ManualAwardOption("douban_manual", "豆瓣", AwardKind.SCORE, 10f),
        ManualAwardOption("mal_manual", "MyAnimeList", AwardKind.SCORE, 10f),
        ManualAwardOption("rym", "RYM / AOTY", AwardKind.SCORE, 5f),
        ManualAwardOption("other", "其它", AwardKind.SCORE, 100f),
    )
}

fun manualAwardLabel(sourceId: String): String = when (sourceId) {
    "famitsu" -> "Fami通"
    "billboard" -> "Billboard"
    "oricon" -> "Oricon"
    "metacritic_manual" -> "Metacritic"
    "douban_manual" -> "豆瓣"
    "goodreads_manual" -> "Goodreads"
    "mal_manual" -> "MyAnimeList"
    "rym" -> "RYM / AOTY"
    else -> "其它"
}

private fun ManualAwardEntity.displayValue(): String = when {
    rankPosition != null -> "第 $rankPosition 名"
    // 修复 BUG-12：榜位类（Billboard/Oricon）的 scoreMax 存 0，只填分数时会显示成 "8 / 0"
    score != null && scoreMax <= 0f -> "%.1f".format(score)
    score != null -> {
        val rounded = if (scoreMax <= 10f) "%.1f".format(score) else score.toInt().toString()
        "$rounded / ${scoreMax.toInt()}"
    }
    else -> "—"
}

private fun formatCount(value: Int): String = when {
    value >= 10_000 -> "%.1f万".format(value / 10_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}
