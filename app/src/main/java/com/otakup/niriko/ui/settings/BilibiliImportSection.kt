package com.otakup.niriko.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 「从哔哩哔哩导入」设置入口。
 *
 * 点击进入全屏导入工作流页（com.otakup.niriko.ui.bilibili.BilibiliSyncScreen）：
 * WebView 登录 → 自动拉取追番列表 + 用户评分/短评 → 预览勾选 → 一键导入本地收藏。
 *
 * @param onNavigateToBilibiliSync 跳转导入页回调（由外层 NavHost 提供）。
 */
@Composable
fun BilibiliImportSection(
    onNavigateToBilibiliSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsActionRow(
        icon = Icons.Outlined.Download,
        title = "从哔哩哔哩导入追番与评分",
        description = "在网页中登录 B 站账号后，导入追番列表、评分与短评",
        onClick = onNavigateToBilibiliSync,
        modifier = modifier,
    )
}
