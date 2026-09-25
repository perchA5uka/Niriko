package com.otakup.niriko.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AppRegistration
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.remote.BangumiClient.BangumiEndpoint
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.settings.BilibiliImportSection
import com.otakup.niriko.ui.settings.KazumiImportSection
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsInfoRow
import com.otakup.niriko.ui.settings.SettingsPickerRow
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.ui.settings.SettingsSwitchRow
import com.otakup.niriko.ui.settings.RatingSourceSettingsGroup
import com.otakup.niriko.viewmodel.SettingsViewModel

/** 数据源与账号设置页：数据源切换 / Steam / Bangumi 账号同步 / 第三方导入。 */
@Composable
fun DataSourceSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToBilibiliSync: () -> Unit = {},
    onNavigateToSteamSync: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showSteamConfig by remember { mutableStateOf(false) }
    var showBangumiConfig by remember { mutableStateOf(false) }
    var steamApiKeyInput by remember { mutableStateOf(settings.steamApiKey) }
    var bangumiTokenInput by remember { mutableStateOf("") }
    LaunchedEffect(showSteamConfig) {
        if (showSteamConfig) steamApiKeyInput = settings.steamApiKey
    }

    // ===== 数据源行 =====
    val datasourceRows = mutableListOf<@Composable () -> Unit>()
    if (viewModel.plugins.isNotEmpty()) {
        datasourceRows.add {
            val endpoint = settings.bangumiEndpoint
            SettingsPickerRow(
                icon = Icons.Outlined.Cloud,
                title = "Bangumi 接口",
                description = "官方站与国内反代一键切换",
                value = if (endpoint == BangumiEndpoint.PROXY) "国内反代" else "官方站",
                onClick = {
                    viewModel.setBangumiEndpoint(
                        if (endpoint == BangumiEndpoint.PROXY) BangumiEndpoint.OFFICIAL else BangumiEndpoint.PROXY,
                    )
                },
            )
        }
        viewModel.plugins.forEach { plugin ->
            datasourceRows.add {
                val isActive = plugin.id == viewModel.primaryPluginId
                SettingsPickerRow(
                    icon = Icons.Outlined.Extension,
                    title = plugin.name,
                    description = "数据源插件",
                    value = if (isActive) "✓ 当前" else "点击切换",
                    onClick = { if (!isActive) viewModel.setActiveDataSourceId(plugin.id) },
                )
            }
        }
        datasourceRows.add {
            SettingsPickerRow(
                icon = Icons.Outlined.Key,
                title = "Steam API Key",
                description = "可选：公开接口无需 Key",
                value = if (settings.steamApiKey.isEmpty()) "未配置(可选)" else "已配置(点击修改)",
                onClick = { showSteamConfig = true },
            )
        }
        datasourceRows.add {
            SettingsPickerRow(
                icon = Icons.Outlined.SportsEsports,
                title = "Steam 账号与游戏库",
                description = "登录后导入 Steam 游戏库",
                value = if (settings.steamId64.isEmpty()) "未登录" else "已登录 · 点击导入",
                onClick = onNavigateToSteamSync,
            )
        }
    }

    // ===== Bangumi 账号行 =====
    // 自动同步状态（改造前该设置连 UI 都没有，开关在代码里被写入却无任何消费方）
    val refreshSnapshots by viewModel.refreshSnapshots.collectAsState()
    val bangumiSnapshot = refreshSnapshots[com.otakup.niriko.data.refresh.RefreshKeys.AUTO_SYNC_BANGUMI]
    val nowMs = System.currentTimeMillis()
    val bangumiAutoSyncSummary = buildString {
        append("回到前台时自动双向同步")
        bangumiSnapshot?.lastSuccessAt?.takeIf { it > 0L }?.let {
            val minutes = (nowMs - it).coerceAtLeast(0L) / 60_000
            append(" · 上次 ").append(if (minutes < 1) "刚刚" else "${minutes}分钟前")
        }
        bangumiSnapshot?.backoffUntil?.takeIf { it > nowMs }?.let {
            append(" · 上次失败，稍后重试")
        }
    }

    val oauthState by viewModel.bangumiOAuth.collectAsState()
    var showOAuthAppConfig by remember { mutableStateOf(false) }
    var oauthClientIdInput by remember { mutableStateOf("") }
    var oauthClientSecretInput by remember { mutableStateOf("") }
    var oauthRedirectInput by remember { mutableStateOf("") }
    LaunchedEffect(showOAuthAppConfig) {
        if (showOAuthAppConfig) {
            oauthClientIdInput = settings.bangumiClientId
            oauthClientSecretInput = settings.bangumiClientSecret
            oauthRedirectInput = settings.bangumiRedirectUri
        }
    }

    val bangumiRows = mutableListOf<@Composable () -> Unit>()
    bangumiRows.add {
        SettingsPickerRow(
            icon = Icons.Outlined.VpnKey,
            title = "Access Token",
            description = "手动粘贴或走下方 OAuth 登录",
            value = if (settings.bangumiAccessToken.isEmpty()) "未配置" else "已配置(点击修改)",
            onClick = { showBangumiConfig = true },
        )
    }
    bangumiRows.add {
        SettingsInfoRow(
            icon = Icons.Outlined.AccountCircle,
            title = "登录状态",
            value = if (settings.bangumiUsername.isNotEmpty()) "已登录:" + settings.bangumiUsername else "未登录",
        )
    }
    // R4c：NSFW 检索是否可用，完全取决于是否有 token。以前这个信息只在失败时才隐约可见。
    bangumiRows.add {
        SettingsInfoRow(
            icon = Icons.Outlined.Visibility,
            title = "NSFW 检索",
            value = if (settings.bangumiAccessToken.isNotEmpty()) {
                "已登录，v0 接口可用"
            } else {
                "未登录，走旧版接口兜底"
            },
        )
    }
    // R4c：OAuth 应用凭据（用户自建，不内置）
    bangumiRows.add {
        SettingsPickerRow(
            icon = Icons.Outlined.AppRegistration,
            title = "OAuth 应用",
            value = when {
                settings.bangumiClientId.isEmpty() -> "未配置(点击填写)"
                settings.bangumiClientSecret.isEmpty() -> "已填 ID，缺 Secret"
                else -> "已配置"
            },
            onClick = { showOAuthAppConfig = true },
        )
    }
    bangumiRows.add {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            OutlinedButton(
                onClick = { viewModel.startBangumiOAuth() },
                modifier = Modifier.weight(1f),
                enabled = !oauthState.isExchanging,
            ) { Text("用 Bangumi 账号登录（OAuth）") }
            if (settings.bangumiAccessToken.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.logoutBangumi() },
                    enabled = !oauthState.isExchanging,
                ) { Text("退出") }
            }
        }
    }
    oauthState.message?.let { message ->
        bangumiRows.add { SettingsInfoRow(icon = Icons.Outlined.Info, title = "OAuth", value = message) }
    }
    oauthState.error?.let { message ->
        bangumiRows.add { SettingsInfoRow(icon = Icons.Outlined.ErrorOutline, title = "OAuth 失败", value = message) }
    }
    // 授权页：命中回跳地址即自动换 token
    oauthState.authorizeUrl?.takeIf { !oauthState.completed }?.let { authorizeUrl ->
        bangumiRows.add {
            com.otakup.niriko.ui.common.OAuthWebViewShell(
                title = "Bangumi 授权",
                description = "在下方网页里登录并授权；回到本页后会自动完成登录。",
                expanded = true,
                onToggleExpanded = { viewModel.cancelBangumiOAuth() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                com.otakup.niriko.ui.common.OAuthWebView(
                    url = authorizeUrl,
                    redirectPrefix = oauthState.redirectPrefix,
                    onRedirect = viewModel::onBangumiOAuthRedirect,
                    failureHostHint = "bgm.tv / api.bgm.tv",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    bangumiRows.add {
        SettingsSwitchRow(
            icon = Icons.Outlined.Sync,
            title = "启用同步",
            description = "双向同步收藏状态与评分",
            checked = settings.bangumiSyncEnabled,
            onCheckedChange = viewModel::setBangumiSyncEnabled,
        )
    }
    bangumiRows.add {
        SettingsSwitchRow(
            icon = Icons.Outlined.Autorenew,
            title = "自动同步",
            description = bangumiAutoSyncSummary,
            checked = settings.bangumiAutoSync,
            onCheckedChange = { viewModel.setBangumiAutoSync(it) },
        )
    }
    bangumiRows.add {
        SettingsPickerRow(
            icon = Icons.Outlined.CompareArrows,
            title = "冲突优先级",
            description = "同一作品两边状态不一致时的取值",
            value = if (settings.bangumiSyncPriority == com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority.LOCAL_FIRST) "本地优先" else "Bangumi 优先",
            onClick = {
                viewModel.setBangumiSyncPriority(
                    if (settings.bangumiSyncPriority == com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority.LOCAL_FIRST)
                        com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority.BANGUMI_FIRST
                    else
                        com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority.LOCAL_FIRST,
                )
            },
        )
    }
    bangumiRows.add {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            OutlinedButton(
                onClick = { viewModel.loginBangumi() },
                modifier = Modifier.weight(1f),
                enabled = !viewModel.isBangumiSyncing.value && settings.bangumiAccessToken.isNotEmpty(),
            ) { Text("登录 Bangumi") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { viewModel.syncBangumi() },
                modifier = Modifier.weight(1f),
                enabled = !viewModel.isBangumiSyncing.value && settings.bangumiSyncEnabled,
            ) {
                if (viewModel.isBangumiSyncing.value) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("立即同步")
                }
            }
        }
    }
    viewModel.bangumiLoginState.value?.let { state ->
        bangumiRows.add { SettingsInfoRow(icon = Icons.Outlined.TaskAlt, title = "登录结果", value = state) }
    }
    if (viewModel.bangumiProgress.value.isNotEmpty()) {
        bangumiRows.add {
            SettingsInfoRow(
                icon = Icons.Outlined.HourglassEmpty,
                title = "进度",
                value = viewModel.bangumiProgress.value,
            )
        }
    }
    viewModel.bangumiSyncResult.value?.let { result ->
        bangumiRows.add { SettingsInfoRow(icon = Icons.Outlined.Info, title = "同步结果", value = result.message) }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        SettingsDetailScaffold(title = "数据源与账号", onBack = onBack) {
            if (datasourceRows.isNotEmpty()) {
                SettingsGroupTitle("数据源")
                SettingsSplitGroup(content = datasourceRows)
            }
            SettingsGroupTitle("Bangumi 账号")
            SettingsSplitGroup(content = bangumiRows)
            SettingsGroupTitle("权威数据源")
            RatingSourceSettingsGroup(
                viewModel = viewModel,
                settings = settings,
                snackbarHostState = snackbarHostState,
            )
            // 第 4 轮 F：灰色通道 + 连通性自检（豆瓣等非官方接口的唯一可观测手段）
            com.otakup.niriko.ui.settings.GrayChannelSettingsGroup(
                settings = settings,
                onGrayChannelEnabled = viewModel::setGrayChannelEnabled,
                onProbeHeaders = viewModel::setProbeHeaders,
                onDoubanReferers = viewModel::setDoubanReferers,
            )
            SettingsGroupTitle("导入")
            SettingsSplitGroup(content = listOf(
                {
                    KazumiImportSection(
                        subjectDao = context.nirikoApp.database.subjectDao(),
                        collectionDao = context.nirikoApp.database.collectionDao(),
                        snackbarHost = snackbarHostState,
                    )
                },
                {
                    BilibiliImportSection(
                        onNavigateToBilibiliSync = onNavigateToBilibiliSync,
                    )
                },
            ))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Steam API Key 配置弹窗（可选：公开接口无需 key，仅未来扩展用）
    if (showSteamConfig) {
        AlertDialog(
            onDismissRequest = { showSteamConfig = false },
            title = { Text("Steam Web API Key") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "可选配置。当前游戏信息（价格/在线人数/开发商等）使用公开接口,无需 Key。\n如需使用更多 Steam Web API,可在 steamcommunity.com/dev/apikey 免费申请后填入。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = steamApiKeyInput,
                        onValueChange = { steamApiKeyInput = it },
                        label = { Text("API Key（留空清除）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSteamApiKey(steamApiKeyInput)
                    showSteamConfig = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSteamConfig = false }) { Text("取消") }
            },
        )
    }

    // Bangumi OAuth 应用凭据弹窗（用户自建，**不内置**——内置公共应用违反 Bangumi 应用条款）
    if (showOAuthAppConfig) {
        AlertDialog(
            onDismissRequest = { showOAuthAppConfig = false },
            title = { Text("Bangumi OAuth 应用") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "在 bgm.tv 的「设置 → 应用」里新建一个应用，把下面三项填过来。\n" +
                            "回跳地址（Redirect URI）必须与应用里登记的完全一致；默认值 " +
                            "niriko://oauth/bangumi 可直接使用。\n" +
                            "凭据仅保存在本机，不会上传。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = oauthClientIdInput,
                        onValueChange = { oauthClientIdInput = it },
                        label = { Text("Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = oauthClientSecretInput,
                        onValueChange = { oauthClientSecretInput = it },
                        label = { Text("Client Secret") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = oauthRedirectInput,
                        onValueChange = { oauthRedirectInput = it },
                        label = { Text("Redirect URI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBangumiOAuthApp(
                        oauthClientIdInput.trim(),
                        oauthClientSecretInput.trim(),
                        oauthRedirectInput.trim(),
                    )
                    showOAuthAppConfig = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showOAuthAppConfig = false }) { Text("取消") }
            },
        )
    }

    // Bangumi Token 配置弹窗
    if (showBangumiConfig) {
        AlertDialog(
            onDismissRequest = { showBangumiConfig = false },
            title = { Text("Bangumi Access Token") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "在 bangumi.tv 设置 → 同步管理中创建一个 Access Token 后粘贴到下方。Token 仅保存在本机,请勿泄露。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = bangumiTokenInput,
                        onValueChange = { bangumiTokenInput = it },
                        label = { Text("Access Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBangumiAccessToken(bangumiTokenInput.trim())
                    bangumiTokenInput = ""
                    showBangumiConfig = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showBangumiConfig = false }) { Text("取消") }
            },
        )
    }
}
