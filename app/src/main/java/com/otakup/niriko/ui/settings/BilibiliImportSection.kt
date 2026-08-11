package com.otakup.niriko.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 「从哔哩哔哩导入」设置入口。
 *
 * 点击进入全屏导入工作流页（[com.otakup.niriko.ui.bilibili.BilibiliSyncScreen]）：
 * WebView 登录 → 自动拉取追番列表 + 用户评分/短评 → 预览勾选 → 一键导入本地收藏。
 *
 * @param onNavigateToBilibiliSync 跳转导入页回调（由外层 NavHost 提供）。
 */
@Composable
fun BilibiliImportSection(
    onNavigateToBilibiliSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToBilibiliSync)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = "从哔哩哔哩导入追番与评分", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "在网页中登录 B 站账号后，导入追番列表、评分与短评",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}