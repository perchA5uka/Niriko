package com.otakup.niriko.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.stats.HygieneKind
import com.otakup.niriko.data.model.stats.HygieneReport
import com.otakup.niriko.ui.components.appleGlassCard

/**
 * 「收藏健康度」卡：四类待整理项 + 入口。
 *
 * 为什么只做"发现问题"不做"批量修复"：修复涉及写库与撤销策略，属于另一张卡；
 * 这里的目标是让用户扫一眼就知道库在哪腐坏，并一步跳到对应列表。
 * 全部为 0 时整卡不渲染——健康的时候不该占一屏。
 */
@Composable
fun HygieneSection(
    report: HygieneReport,
    onOpenHygiene: (HygieneKind) -> Unit = {},
    onOpenWatching: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (report.actionableCount == 0) return
    Box(modifier = modifier.fillMaxWidth().appleGlassCard(shape = MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "收藏健康度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = report.actionableCount.toString() + " 项待整理",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            report.issues.filter { it.count > 0 }.forEach { issue ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 「在看停滞」直接复用放送通知那条已接好的链路：切到作品库「在看」
                            if (issue.kind == HygieneKind.WATCHING_STALE) onOpenWatching()
                            else onOpenHygiene(issue.kind)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = labelOf(issue.kind),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = issue.count.toString() + " 部",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun labelOf(kind: HygieneKind): String = when (kind) {
    HygieneKind.WATCHING_STALE -> "在看已停滞 90 天"
    HygieneKind.COMPLETED_UNRATED -> "看过但没评分"
    HygieneKind.UNTAGGED -> "没有个人标签"
    HygieneKind.PLAN_STALE -> "想看超过一年"
}
