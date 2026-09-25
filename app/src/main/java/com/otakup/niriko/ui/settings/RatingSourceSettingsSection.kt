package com.otakup.niriko.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.StarHalf
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.remote.rating.RatingSourceRegistry
import com.otakup.niriko.data.remote.tmdb.TmdbClient
import com.otakup.niriko.data.settings.AppSettings
import com.otakup.niriko.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive

/**
 * 「权威数据源」设置分组。
 *
 * 设计要点（对齐 AniShelf 的 key 录入体验 + 项目既有 Steam API Key 的做法）：
 * - **密钥由用户自备**，绝不硬编码；
 * - 保存前用轻量端点**校验 key**（TMDb 用「/3/configuration」，OMDb 用「?i=tt0111161」）；
 * - TMDb 的 **api 与 image 两个地址必须成对配置** —— 国区两者都不可直连；
 * - 每个源都展示「已配置 / 未配置」，未配置的源在详情页自动隐藏（不产生空区块）。
 */
@Composable
fun RatingSourceSettingsGroup(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var editTarget by remember { mutableStateOf<KeyField?>(null) }
    var validating by remember { mutableStateOf(false) }

    // 行解剖统一到 SettingsSplitGroup + SettingsSwitchRow / KeyRow（后者已改为 SettingsPickerRow）
    val rows = mutableListOf<@Composable () -> Unit>()
    rows.add {
        SettingsSwitchRow(
            icon = Icons.Outlined.Verified,
            title = "权威评分",
            description = "关闭后详情页不显示任何外部评分（Bangumi / Bilibili 仍正常显示）",
            checked = settings.externalRatingsEnabled,
            onCheckedChange = { viewModel.setExternalRatingsEnabled(it) },
        )
    }
    rows.add {
        KeyRow(
            icon = Icons.Outlined.Key,
            title = "TMDb API Key",
            subtitle = "每集评分 / 剧照 / 海报候选的主源（免费申请：themoviedb.org）",
            configured = settings.tmdbApiKey.isNotBlank(),
            onClick = { editTarget = KeyField.TMDB_KEY },
        )
    }
    rows.add {
        KeyRow(
            icon = Icons.Outlined.Dns,
            title = "TMDb 镜像地址",
            subtitle = if (settings.tmdbApiUrl.isBlank()) {
                "当前使用官方域名（国区通常不可直连，建议填写自建镜像）"
            } else {
                "API：${settings.tmdbApiUrl}"
            },
            configured = settings.tmdbApiUrl.isNotBlank(),
            onClick = { editTarget = KeyField.TMDB_URLS },
        )
    }
        // 第 4 轮 C：TMDb 覆盖范围开关。
        // 改造前 TmdbRatingSource 只声明 ANIME/REAL/OTHER，GAME/BOOK/MUSIC 连候选都渲染不出来
        // （用户反馈「部分作品没有 TMDb 匹配」）。TMDb 对这三类覆盖确实差，因此**默认关**，
        // 由用户显式打开（打开后走 TMDb movie 端点）。
    rows.add {
        SettingsSwitchRow(
            icon = Icons.Outlined.Category,
            title = "TMDb 覆盖游戏 / 书籍 / 音乐",
            description = "打开后这三类也查 TMDb（走 movie 端点）。默认关闭：TMDb 对它们覆盖较差，易产生噪声候选。",
            checked = settings.tmdbIncludeNonTvTypes,
            onCheckedChange = { viewModel.setTmdbIncludeNonTvTypes(it) },
        )
    }
    rows.add {
        KeyRow(
            icon = Icons.Outlined.Key,
            title = "OMDb API Key",
            subtitle = "用于叠加 IMDb 逐集评分（免费 1,000 次/天；内容 CC BY-NC，不可商用）",
            configured = settings.omdbApiKey.isNotBlank(),
            onClick = { editTarget = KeyField.OMDB_KEY },
        )
    }
    rows.add {
        SettingsSwitchRow(
            icon = Icons.Outlined.StarHalf,
            title = "启用 IMDb 逐集评分入口",
            description = "关闭时详情页不出现「加载 IMDb 逐集评分」按钮（该链路请求较多）",
            checked = settings.imdbEpisodeRatingsEnabled,
            onCheckedChange = { viewModel.setImdbEpisodeRatingsEnabled(it) },
        )
    }
    rows.add {
        KeyRow(
            icon = Icons.Outlined.SportsEsports,
            title = "IGDB（Twitch）",
            subtitle = "游戏媒体均分 aggregated_rating（免费申请 Twitch 应用）",
            configured = settings.igdbClientId.isNotBlank() && settings.igdbClientSecret.isNotBlank(),
            onClick = { editTarget = KeyField.IGDB },
        )
    }
    rows.add {
        KeyRow(
            icon = Icons.Outlined.SportsEsports,
            title = "RAWG API Key",
            subtitle = "游戏的 Metacritic 单值（可选，与 IGDB 二选一即可）",
            configured = settings.rawgApiKey.isNotBlank(),
            onClick = { editTarget = KeyField.RAWG },
        )
    }
    rows.add {
        KeyRow(
            icon = Icons.Outlined.Album,
            title = "Discogs Token",
            subtitle = "音乐唱片社区评分（5 分制）",
            configured = settings.discogsToken.isNotBlank(),
            onClick = { editTarget = KeyField.DISCOGS },
        )
    }
        // ===== 豆瓣剧照（阶段 E：灰色通道，默认关） =====
    rows.add {
        SettingsSwitchRow(
            icon = Icons.Outlined.PhotoLibrary,
            title = "豆瓣剧照",
            description = "豆瓣未授权第三方抓取，且会对非住宅 IP 触发反爬（sec.douban.com）。" +
                "开启后需在作品详情页手动绑定豆瓣词条；失败时静默降级，不影响其它剧照来源。",
            checked = settings.doubanPhotosEnabled,
            onCheckedChange = { viewModel.setDoubanPhotosEnabled(it) },
        )
    }
    if (settings.doubanPhotosEnabled) {
        rows.add {
            KeyRow(
                icon = Icons.Outlined.Link,
                title = "豆瓣 Referer",
                subtitle = "图片：" + settings.doubanImageReferer + " · API：" + settings.doubanApiReferer,
                configured = true,
                onClick = { editTarget = KeyField.DOUBAN_REFERER },
            )
        }
    }
    rows.add {
        KeyRow(
            icon = Icons.Outlined.Key,
            title = "OpenCritic API Key",
            subtitle = "游戏媒体聚合（官方渠道需申请，未配置则整源隐藏）",
            configured = settings.openCriticApiKey.isNotBlank(),
            onClick = { editTarget = KeyField.OPENCRITIC },
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSplitGroup(content = rows)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "本应用使用 TMDb API，但未获得 TMDb 的认可或认证。" +
                "Fami通、Billboard、Oricon 等机构没有可用的公开 API（或条款禁止抓取），" +
                "相关成绩请在作品详情页手动录入。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "无需密钥即可用：" + RatingSourceRegistry.all
                .filter { !it.requiresKey }
                .joinToString(" / ") { it.label } + "（Steam 好评率、MusicBrainz、VNDB、Google Books、Open Library）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    editTarget?.let { target ->
        KeyEditDialog(
            target = target,
            settings = settings,
            validating = validating,
            onDismiss = { editTarget = null },
            onSave = { values ->
                validating = true
                scope.launch {
                    val result = validateAndSave(target, values, viewModel)
                    validating = false
                    editTarget = null
                    snackbarHostState.showSnackbar(result)
                }
            },
        )
    }
}

private enum class KeyField { TMDB_KEY, TMDB_URLS, OMDB_KEY, IGDB, RAWG, DISCOGS, OPENCRITIC, DOUBAN_REFERER }

@Composable
private fun KeyRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    configured: Boolean,
    onClick: () -> Unit,
) {
    SettingsPickerRow(
        icon = icon,
        title = title,
        description = subtitle,
        value = if (configured) "已配置 · 点击修改" else "未配置 · 点击填写",
        onClick = onClick,
    )
}

@Composable
private fun KeyEditDialog(
    target: KeyField,
    settings: AppSettings,
    validating: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val titles = mapOf(
        KeyField.TMDB_KEY to "TMDb API Key",
        KeyField.TMDB_URLS to "TMDb 镜像地址",
        KeyField.OMDB_KEY to "OMDb API Key",
        KeyField.IGDB to "IGDB（Twitch 应用）",
        KeyField.RAWG to "RAWG API Key",
        KeyField.DISCOGS to "Discogs Token",
        KeyField.OPENCRITIC to "OpenCritic API Key",
        KeyField.DOUBAN_REFERER to "豆瓣 Referer",
    )
    val initial = when (target) {
        KeyField.TMDB_KEY -> listOf(settings.tmdbApiKey)
        KeyField.TMDB_URLS -> listOf(settings.tmdbApiUrl, settings.tmdbImageUrl)
        KeyField.OMDB_KEY -> listOf(settings.omdbApiKey)
        KeyField.IGDB -> listOf(settings.igdbClientId, settings.igdbClientSecret)
        KeyField.RAWG -> listOf(settings.rawgApiKey)
        KeyField.DISCOGS -> listOf(settings.discogsToken)
        KeyField.OPENCRITIC -> listOf(settings.openCriticApiKey)
        KeyField.DOUBAN_REFERER -> listOf(settings.doubanImageReferer, settings.doubanApiReferer)
    }
    val labels = when (target) {
        KeyField.TMDB_URLS -> listOf("API 地址（如 https://tmdb.example.com/）", "图片地址（如 https://img.example.com/t/p/）")
        KeyField.IGDB -> listOf("Client ID", "Client Secret")
        KeyField.DOUBAN_REFERER -> listOf("图片 Referer（如 https://movie.douban.com/）", "API Referer 前缀（如 https://m.douban.com）")
        else -> listOf("密钥")
    }
    var values by remember(target) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = { if (!validating) onDismiss() },
        title = { Text(titles[target] ?: "配置") },
        text = {
            Column {
                labels.forEachIndexed { index, label ->
                    OutlinedTextField(
                        value = values.getOrElse(index) { "" },
                        onValueChange = { input ->
                            values = values.toMutableList().also { it[index] = input }
                        },
                        label = { Text(label) },
                        singleLine = true,
                        visualTransformation = if (target == KeyField.TMDB_URLS || target == KeyField.DOUBAN_REFERER) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = when (target) {
                        KeyField.TMDB_URLS ->
                            "TMDb 的 API 与图片是两个域名（api.themoviedb.org / image.tmdb.org），" +
                                "国内均不可直连。请填写你自建的镜像地址，两个都必须填。"
                        KeyField.TMDB_KEY -> "与 TMDb 官网「API Key (v3 auth)」一致，注意不是 Read Access Token。"
                        KeyField.OMDB_KEY -> "免费额度 1,000 次/天；内容为 CC BY-NC 4.0，不可用于商业用途。"
                        KeyField.DOUBAN_REFERER ->
                            "豆瓣图床有防盗链，图片 Referer 必须与访问页面匹配；接口被拦截时可尝试调整这两个值。"
                        else -> "留空即关闭该数据源。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values) }, enabled = !validating) {
                Text(if (validating) "校验中…" else "保存并校验")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !validating) { Text("取消") } },
    )
}

/** 保存前校验：能省掉"配了错 key 却以为功能坏了"的排查成本。 */
private suspend fun validateAndSave(
    target: KeyField,
    values: List<String>,
    viewModel: SettingsViewModel,
): String = when (target) {
    KeyField.TMDB_KEY -> {
        val key = values.getOrElse(0) { "" }.trim()
        viewModel.setTmdbApiKey(key)
        if (key.isEmpty()) return "已清空 TMDb API Key"
        val ok = withContext(Dispatchers.IO) {
            TmdbClient.setApiKey(key)
            runCatching { TmdbClient.apiService.configuration() }.isSuccess
        }
        if (ok) "TMDb API Key 有效，已保存" else "TMDb 校验失败：Key 无效或网络不可达（国区请先配置镜像地址）"
    }
    KeyField.TMDB_URLS -> {
        val api = values.getOrElse(0) { "" }.trim()
        val image = values.getOrElse(1) { "" }.trim()
        viewModel.setTmdbUrls(api, image)
        if (api.isEmpty() && image.isEmpty()) "已恢复 TMDb 官方域名" else "已保存 TMDb 镜像地址"
    }
    KeyField.OMDB_KEY -> {
        val key = values.getOrElse(0) { "" }.trim()
        viewModel.setOmdbApiKey(key)
        if (key.isEmpty()) return "已清空 OMDb API Key"
        val ok = withContext(Dispatchers.IO) {
            val json = com.otakup.niriko.data.remote.rating.RatingHttp.getJson(
                "https://www.omdbapi.com/?apikey=$key&i=tt0111161"
            )
            json?.get("Response")
                ?.let { element -> runCatching { element.jsonPrimitive.content }.getOrNull() } != "False"
        }
        if (ok) "OMDb API Key 有效，已保存" else "OMDb 校验失败：Key 无效或网络不可达"
    }
    KeyField.IGDB -> {
        viewModel.setIgdbCredentials(values.getOrElse(0) { "" }, values.getOrElse(1) { "" })
        "已保存 IGDB 凭据"
    }
    KeyField.RAWG -> {
        viewModel.setRawgApiKey(values.getOrElse(0) { "" })
        "已保存 RAWG API Key"
    }
    KeyField.DISCOGS -> {
        viewModel.setDiscogsToken(values.getOrElse(0) { "" })
        "已保存 Discogs Token"
    }
    KeyField.OPENCRITIC -> {
        viewModel.setOpenCriticApiKey(values.getOrElse(0) { "" })
        "已保存 OpenCritic API Key"
    }
    KeyField.DOUBAN_REFERER -> {
        viewModel.setDoubanReferers(values.getOrElse(0) { "" }, values.getOrElse(1) { "" })
        "已保存豆瓣 Referer"
    }
}
