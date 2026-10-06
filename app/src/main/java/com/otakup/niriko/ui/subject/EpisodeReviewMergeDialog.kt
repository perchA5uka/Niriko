package com.otakup.niriko.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.TagChip
import com.otakup.niriko.util.EpisodeReviewMergePolicy

/**
 * 分集评价合并的**预览与确认**（F07，计划 §9.3）。
 *
 * 这个弹层存在的唯一理由：在用户的作品感想被改写之前，让他**看见**将要写入的确切文本。
 * 因此这里没有「智能摘要」——预览区显示的就是 [EpisodeReviewMergePolicy.compose] 的输出原文。
 *
 * 三条边界（都有单测）直接体现在界面上：
 * - 只替换自动块：说明行写清了「会整块替换上次生成的内容，手写部分不受影响」；
 * - 默认不覆盖已有手动评分：本来打过分时，必须**勾选**才允许覆盖（复选框默认不勾）；
 * - 取消 = 什么都不发生：确认按钮把结果**填回编辑区**，真正的持久化仍由「保存记录」完成
 *   （只保留一条写路径，避免两个地方都能写同一份用户数据）。
 *
 * @param currentRating 作品现有评分（> 0 才算「已打分」）；用来决定是否需要覆盖确认
 * @param existingImpression 现有感想原文（用来算合并结果，也在预览里对比）
 */
@Composable
internal fun EpisodeReviewMergeDialog(
    entries: List<EpisodeReviewMergePolicy.Entry>,
    currentRating: Float?,
    existingImpression: String?,
    onDismiss: () -> Unit,
    onConfirm: (EpisodeReviewMergePolicy.Result) -> Unit,
) {
    var mode by remember { mutableStateOf(EpisodeReviewMergePolicy.Mode.BOTH) }
    var rounding by remember { mutableStateOf(EpisodeReviewMergePolicy.Rounding.HALF_UP_HALF) }
    var overwrite by remember { mutableStateOf(false) }

    val result = remember(entries, existingImpression, mode, rounding, currentRating, overwrite) {
        EpisodeReviewMergePolicy.compose(
            entries = entries,
            existing = existingImpression,
            mode = mode,
            rounding = rounding,
            currentRating = currentRating,
            overwriteConfirmed = overwrite,
        )
    }
    val needsConfirm = EpisodeReviewMergePolicy.needsOverwriteConfirmation(currentRating)
    val hasRated = result.ratedCount > 0
    val hasAnything = result.impression != existingImpression || result.average != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从分集评价生成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 概览：分母口径写在明面上（有评分 vs 全部）
                Text(
                    text = if (entries.isEmpty()) {
                        "还没有分集数据：先在单集页面里写下评分或短评。"
                    } else {
                        "共 ${result.totalCount} 集 · ${result.ratedCount} 集有评分 · ${result.commentedCount} 集有短评" +
                            if (hasRated) " · 平均 ${EpisodeReviewMergePolicy.formatScore(result.average ?: 0f)} 分" else " · 暂无可算的均分"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("写入内容", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EpisodeReviewMergePolicy.Mode.entries.forEach { candidate ->
                        TagChip(
                            label = when (candidate) {
                                EpisodeReviewMergePolicy.Mode.COMMENTS_ONLY -> "仅评论"
                                EpisodeReviewMergePolicy.Mode.AVERAGE_ONLY -> "仅均分"
                                EpisodeReviewMergePolicy.Mode.BOTH -> "两者"
                            },
                            emphatic = mode == candidate,
                            onClick = { mode = candidate },
                        )
                    }
                }

                Text("均分舍入", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip(
                        label = "半分（推荐）",
                        emphatic = rounding == EpisodeReviewMergePolicy.Rounding.HALF_UP_HALF,
                        onClick = { rounding = EpisodeReviewMergePolicy.Rounding.HALF_UP_HALF },
                    )
                    TagChip(
                        label = "整数",
                        emphatic = rounding == EpisodeReviewMergePolicy.Rounding.HALF_UP_INTEGER,
                        onClick = { rounding = EpisodeReviewMergePolicy.Rounding.HALF_UP_INTEGER },
                    )
                }

                // 默认不覆盖已有手动评分（§16）
                if (needsConfirm && mode != EpisodeReviewMergePolicy.Mode.COMMENTS_ONLY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                        Text(
                            text = "覆盖现有评分 ${EpisodeReviewMergePolicy.formatScore(currentRating ?: 0f)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!overwrite) {
                        Text(
                            text = "未勾选：只更新感想，作品评分保持原样。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text("预览（将要写入的文本）", style = MaterialTheme.typography.labelLarge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    Text(
                        text = result.impression
                            ?: "（此次没有需要写入的感想内容：没有短评，或选择了「仅均分」）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (result.average != null) {
                    Text(
                        // 预览里同时写清「评分会变成多少」——用户不必自己回到滑块去看
                        text = "作品评分将写入 ${EpisodeReviewMergePolicy.formatScore(result.average)} 分" +
                            if (needsConfirm && !overwrite) "（未勾选覆盖，实际不会写入）" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "会整块替换上次生成的内容（标记之间的部分），手写部分不受影响。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!hasAnything && entries.isNotEmpty()) {
                    Text(
                        text = "按当前选择没有可写入的内容。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasAnything && entries.isNotEmpty(),
                onClick = { onConfirm(result) },
            ) { Text("填入编辑区") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
