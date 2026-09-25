package com.otakup.niriko.ui.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.otakup.niriko.ui.settings.SettingsActionRow
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsInfoRow
import com.otakup.niriko.ui.settings.SettingsSplitGroup

/** 关于页。 */
@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLicenseDialog by remember { mutableStateOf(false) }
    // 运行时读取版本号，避免硬编码与实际版本漂移
    val context = LocalContext.current
    val versionName = runCatching {
        val pm = context.packageManager
        pm.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0.0"

    SettingsDetailScaffold(title = "关于", onBack = onBack, modifier = modifier) {
        SettingsGroupTitle("应用")
        SettingsSplitGroup(content = listOf(
            {
                SettingsInfoRow(
                    icon = Icons.Outlined.Info,
                    title = "版本号",
                    value = "v$versionName",
                )
            },
            {
                SettingsActionRow(
                    icon = Icons.Outlined.Description,
                    title = "开源许可",
                    description = "MIT License",
                    onClick = { showLicenseDialog = true },
                )
            },
            {
                SettingsActionRow(
                    icon = Icons.Outlined.Feedback,
                    title = "反馈与建议",
                    description = "GitHub Issues",
                    onClick = { openExternalUrl(context, REPO_ISSUES_URL) },
                )
            },
        ))
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("开源许可") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("本应用基于 MIT License 开源。")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "仓库与许可证全文：$REPO_URL",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "依赖库：\n" +
                            "• Jetpack Compose (Apache 2.0)\n" +
                            "• Material 3 (Apache 2.0)\n" +
                            "• Navigation Compose (Apache 2.0)\n" +
                            "• Room (Apache 2.0)\n" +
                            "• DataStore (Apache 2.0)\n" +
                            "• WorkManager (Apache 2.0)\n" +
                            "• Media3 (Apache 2.0)\n" +
                            "• Coil (Apache 2.0)\n" +
                            "• Retrofit (Apache 2.0)\n" +
                            "• OkHttp (Apache 2.0)\n" +
                            "• Kotlinx Serialization (Apache 2.0)\n" +
                            "• miuix-blur (Apache 2.0)\n" +
                            "• kyant backdrop (Apache 2.0)\n" +
                            "• material-color-utilities (Apache 2.0)\n" +
                            "• pinyin4j (BSD)\n" +
                            "• 玻璃着色器移植自 AndroidLiquidGlassView (MIT)\n" +
                            "\n完整第三方声明见仓库内 THIRD-PARTY-NOTICES.md",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) { Text("关闭") }
            },
        )
    }
}

/** 项目仓库（反馈入口与许可声明都指向这里）。 */
private const val REPO_URL = "https://github.com/perchA5uka/Niriko"
private const val REPO_ISSUES_URL = "$REPO_URL/issues"

/** 打开外部链接；设备上没有能处理该链接的应用时静默忽略，不影响设置页。 */
private fun openExternalUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url),
            )
        )
    }
}
