package com.otakup.niriko.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 首次启动引导（计划 B1-1）。
 *
 * 只做一件事：填写 TMDb API Key。决策原文「目前只需要 TMDB API KEY 的设置功能，
 * 同时给首次启动引导每页都添加跳过功能」——因此本页右上角常驻「跳过」，底部再给一个等价的
 * 次要按钮；将来若增加到多页，每一页都必须保留同样的「跳过」入口。
 *
 * @param initialKey 已有 Key（升级场景下通常为空串）
 * @param onFinish Key 为空串表示「跳过」；非空表示保存后继续
 */
@Composable
fun FirstRunScreen(
    initialKey: String,
    onFinish: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var key by rememberSaveable { mutableStateOf(initialKey) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
        ) {
            // 每一页都有的「跳过」入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "首次启动引导 · 1/1",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onFinish("") }) { Text("跳过") }
            }

            Spacer(Modifier.height(40.dp))

            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "填写 TMDb API Key",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "用于海报、剧照与权威评分等数据源。不填也能继续使用其它功能，" +
                    "之后可以在「设置 → 数据源与账号」里补填。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("TMDb API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onFinish(key.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存并继续") }
                OutlinedButton(
                    onClick = { onFinish("") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("跳过") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
