package com.otakup.niriko.ui.share

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.ui.settings.SettingsSwitchRow
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * 分享预览 Sheet（AniShelf 借鉴）：预览海报风分享卡 + 配置（圆角/评分/进度/标签）+ 分享。
 */
@Composable
fun SharePreviewSheet(
    context: Context,
    host: ShareBitmapHost,
    data: ShareCardData,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rounded by remember { mutableStateOf(true) }
    var showRating by remember { mutableStateOf(true) }
    var showProgress by remember { mutableStateOf(true) }
    var showTags by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }

    fun doShare() {
        scope.launch {
            sharing = true
            val bmp = host.renderPoster(context, data, showRating, showProgress, showTags, rounded)
            val text = buildString {
                append(data.primaryTitle)
                data.statusLabel?.let { append("  ·  " + it) }
                if (showRating && data.score != null) append("  ·  评分 " + data.score)
                if (showProgress && data.progressText != null) append("  ·  " + data.progressText)
                append("\n\n——— 来自 Niriko")
            }
            val uri = ShareFlow.saveAndGetUri(context, bmp, "share_" + System.currentTimeMillis() + ".png")
            context.startActivity(ShareFlow.buildChooser(ShareFlow.buildShareIntent(uri, text), "分享到"))
            sharing = false
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),   // 关键：内容可滚动，防止开关被裁剪/重叠
        ) {
            Text("分享卡预览", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            // 预览（限高，避免撑爆 Sheet 导致下方开关不可见）
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                SharePosterCard(
                    data = data, showRating = showRating, showProgress = showProgress, showTags = showTags, rounded = rounded,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("自定义", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            // 与设置页同一套行解剖：圆形/玻璃分组容器 + leading 图标 + 标题 + 描述 + Switch
            SettingsSplitGroup(content = listOf(
                {
                    SettingsSwitchRow(
                        icon = Icons.Outlined.RoundedCorner,
                        title = "圆角卡片",
                        description = "分享卡使用圆角样式",
                        checked = rounded,
                        onCheckedChange = { rounded = it },
                    )
                },
                {
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Star,
                        title = "包含评分",
                        description = "显示我的/Bangumi 评分",
                        checked = showRating,
                        onCheckedChange = { showRating = it },
                    )
                },
                {
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Timeline,
                        title = "包含进度",
                        description = "显示观看进度",
                        checked = showProgress,
                        onCheckedChange = { showProgress = it },
                    )
                },
                {
                    SettingsSwitchRow(
                        icon = Icons.Outlined.LocalOffer,
                        title = "包含个人标签",
                        description = "显示我的标签",
                        checked = showTags,
                        onCheckedChange = { showTags = it },
                    )
                },
            ))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                androidx.compose.material3.Button(onClick = { doShare() }, enabled = !sharing, modifier = Modifier.weight(1f)) {
                    Text(if (sharing) "生成中…" else "分享")
                }
            }
        }
    }
}