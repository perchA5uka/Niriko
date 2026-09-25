package com.otakup.niriko.ui.settings.pages

import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.BlurCircular
import androidx.compose.material.icons.outlined.BlurLinear
import androidx.compose.material.icons.outlined.BlurOff
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.data.settings.GlassEffectLevel
import com.otakup.niriko.data.settings.ThemeMode
import com.otakup.niriko.data.settings.WallpaperAtmosphere
import com.otakup.niriko.ui.icon.AppIconManager
import com.otakup.niriko.ui.settings.SettingsActionRow
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsInfoRow
import com.otakup.niriko.ui.settings.SettingsPickerRow
import com.otakup.niriko.ui.settings.SettingsRadioRow
import com.otakup.niriko.ui.settings.SettingsSliderRow
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.ui.settings.SettingsSwitchRow
import com.otakup.niriko.ui.theme.SeedColorScheme
import com.otakup.niriko.ui.theme.ThemeColorPickerDialog
import com.otakup.niriko.util.WallpaperPage
import com.otakup.niriko.viewmodel.SettingsViewModel

/**
 * 外观设置页：主题模式 / 自定义主题色 / 动态取色 / OLED / 壁纸 / 主题包。
 *
 * 版式对齐 Kazumi（取舍 ④B）：分组标题 + 玻璃分组容器 + 统一行解剖，
 * 三选项设置改为就地 radio 行，壁纸柔化改为就地滑杆行。
 */
@Composable
fun AppearanceSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showThemeColorPicker by remember { mutableStateOf(false) }
    // 壁纸文件选择：pendingWallpaperTarget=null 表示全局，否则为页面 key
    var pendingWallpaperTarget by remember { mutableStateOf<String?>(null) }
    val wallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val target = pendingWallpaperTarget
        viewModel.setWallpaperEnabled(true)
        if (target == null) viewModel.setWallpaperUri(uri.toString())
        else viewModel.setWallpaperPageUri(target, uri.toString())
        pendingWallpaperTarget = null
    }

    Box(modifier = modifier.fillMaxWidth()) {
    SettingsDetailScaffold(title = "外观", onBack = onBack) {
        // ===== 主题模式（三选项：就地单选） =====
        SettingsGroupTitle("主题模式")
        SettingsSplitGroup(content = listOf(
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.BrightnessAuto,
                    title = "跟随系统",
                    description = "随系统深色/浅色切换",
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onSelect = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.LightMode,
                    title = "浅色",
                    description = "始终使用浅色主题",
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onSelect = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "深色",
                    description = "始终使用深色主题",
                    selected = settings.themeMode == ThemeMode.DARK,
                    onSelect = { viewModel.setThemeMode(ThemeMode.DARK) },
                )
            },
        ))

        // ===== 主题 =====
        SettingsGroupTitle("主题")
        SettingsSplitGroup(content = listOf(
            {
                SettingsPickerRow(
                    icon = Icons.Outlined.Palette,
                    title = "主题色",
                    description = "品牌色种子（默认品牌绿）",
                    value = if (settings.customSeedColor == SeedColorScheme.UnsetSeed)
                        "默认（品牌绿）"
                    else
                        "#%06X".format(0xFFFFFF and settings.customSeedColor),
                    onClick = { showThemeColorPicker = true },
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.ColorLens,
                    title = "Material You 动态取色",
                    description = "使用 Android 12+ 动态配色覆盖品牌色",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Contrast,
                    title = "OLED 纯黑优化",
                    description = "深色模式下使用纯黑背景",
                    checked = settings.oledDark,
                    onCheckedChange = viewModel::setOledDark,
                )
            },
            // 第 5 轮：开屏（品牌绿底 + 大 N 图标 + 衔接动画）已按用户要求整体移除，
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Animation,
                    title = "减少动态效果",
                    description = "跳过列表/分区的入场动画（系统关闭动画时同样生效）",
                    checked = settings.reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion,
                )
            },
        ))

        // ===== 玻璃效果（三选项：就地单选） =====
        SettingsGroupTitle("玻璃效果")
        SettingsSplitGroup(content = listOf(
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.BlurOn,
                    title = "全效果（默认）",
                    description = "真液态玻璃：模糊 + 折射 + 色散",
                    selected = settings.glassEffect == GlassEffectLevel.FULL,
                    onSelect = { viewModel.setGlassEffect(GlassEffectLevel.FULL) },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.BlurLinear,
                    title = "降低特效",
                    description = "关闭折射，保留模糊与发光",
                    selected = settings.glassEffect == GlassEffectLevel.REDUCED,
                    onSelect = { viewModel.setGlassEffect(GlassEffectLevel.REDUCED) },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.BlurOff,
                    title = "关闭玻璃",
                    description = "性能最优，仅保留静态底色",
                    selected = settings.glassEffect == GlassEffectLevel.OFF,
                    onSelect = { viewModel.setGlassEffect(GlassEffectLevel.OFF) },
                )
            },
        ))

        // ===== 卡片玻璃（三选项：就地单选） =====
        SettingsGroupTitle("卡片玻璃")
        SettingsSplitGroup(content = listOf(
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.Layers,
                    title = "全开（性能较高）",
                    description = "每张卡真折射，流畅但耗 GPU",
                    selected = settings.cardGlassLevel == CardGlassLevel.FULL,
                    onSelect = { viewModel.setCardGlassLevel(CardGlassLevel.FULL) },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = "仅已收藏（更省电）",
                    description = "收藏列表真玻璃，搜索/趋势静态",
                    selected = settings.cardGlassLevel == CardGlassLevel.COLLECTION_ONLY,
                    onSelect = { viewModel.setCardGlassLevel(CardGlassLevel.COLLECTION_ONLY) },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.BlurOff,
                    title = "关闭（静态卡片）",
                    description = "最省电，卡片无真玻璃",
                    selected = settings.cardGlassLevel == CardGlassLevel.OFF,
                    onSelect = { viewModel.setCardGlassLevel(CardGlassLevel.OFF) },
                )
            },
        ))

        // ===== 壁纸 =====
        val globalUri = settings.wallpaperUri
        SettingsGroupTitle("壁纸")
        SettingsSplitGroup(content = listOf(
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Wallpaper,
                    title = "启用壁纸",
                    description = "作品库/发现/统计/设置页背景（详情页不受影响）",
                    checked = settings.wallpaperEnabled,
                    onCheckedChange = viewModel::setWallpaperEnabled,
                )
            },
            {
                SettingsPickerRow(
                    icon = Icons.Outlined.Image,
                    title = "全局壁纸",
                    description = "未单独设置时所有页面共用",
                    value = wallpaperKindLabel(context, globalUri),
                    onClick = {
                        pendingWallpaperTarget = null
                        wallpaperLauncher.launch(arrayOf("image/*", "video/*"))
                    },
                )
            },
            {
                WallpaperPageRow(
                    page = WallpaperPage.LIBRARY,
                    overrideUri = settings.wallpaperLibraryUri,
                    globalUri = globalUri,
                    onClickPick = {
                        pendingWallpaperTarget = WallpaperPage.LIBRARY.key
                        wallpaperLauncher.launch(arrayOf("image/*", "video/*"))
                    },
                    onClear = { viewModel.setWallpaperPageUri(WallpaperPage.LIBRARY.key, "") },
                )
            },
            {
                WallpaperPageRow(
                    page = WallpaperPage.DISCOVER,
                    overrideUri = settings.wallpaperDiscoverUri,
                    globalUri = globalUri,
                    onClickPick = {
                        pendingWallpaperTarget = WallpaperPage.DISCOVER.key
                        wallpaperLauncher.launch(arrayOf("image/*", "video/*"))
                    },
                    onClear = { viewModel.setWallpaperPageUri(WallpaperPage.DISCOVER.key, "") },
                )
            },
            {
                WallpaperPageRow(
                    page = WallpaperPage.STATS,
                    overrideUri = settings.wallpaperStatsUri,
                    globalUri = globalUri,
                    onClickPick = {
                        pendingWallpaperTarget = WallpaperPage.STATS.key
                        wallpaperLauncher.launch(arrayOf("image/*", "video/*"))
                    },
                    onClear = { viewModel.setWallpaperPageUri(WallpaperPage.STATS.key, "") },
                )
            },
            {
                WallpaperPageRow(
                    page = WallpaperPage.SETTINGS,
                    overrideUri = settings.wallpaperSettingsUri,
                    globalUri = globalUri,
                    onClickPick = {
                        pendingWallpaperTarget = WallpaperPage.SETTINGS.key
                        wallpaperLauncher.launch(arrayOf("image/*", "video/*"))
                    },
                    onClear = { viewModel.setWallpaperPageUri(WallpaperPage.SETTINGS.key, "") },
                )
            },
            {
                SettingsSliderRow(
                    icon = Icons.Outlined.BlurCircular,
                    title = "壁纸柔化",
                    description = "高斯模糊半径（0 = 不柔化）",
                    value = settings.wallpaperBlurDp.toFloat(),
                    valueRange = 0f..40f,
                    steps = 7,
                    valueLabel = { value -> value.toInt().toString() + " dp" },
                    onValueChange = { viewModel.setWallpaperBlurDp(it.toInt()) },
                )
            },
        ))

        // ===== 壁纸氛围（三选项：就地单选） =====
        SettingsGroupTitle("壁纸氛围")
        SettingsSplitGroup(content = listOf(
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.Contrast,
                    title = "浓郁",
                    description = "压暗更强，文字更清晰",
                    selected = settings.wallpaperAtmosphere == WallpaperAtmosphere.RICH,
                    onSelect = { viewModel.setWallpaperAtmosphere(WallpaperAtmosphere.RICH) },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.Tune,
                    title = "均衡（默认）",
                    description = "压暗与亮度折中",
                    selected = settings.wallpaperAtmosphere == WallpaperAtmosphere.BALANCED,
                    onSelect = { viewModel.setWallpaperAtmosphere(WallpaperAtmosphere.BALANCED) },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.BrightnessHigh,
                    title = "素净",
                    description = "保留壁纸亮度",
                    selected = settings.wallpaperAtmosphere == WallpaperAtmosphere.MINIMAL,
                    onSelect = { viewModel.setWallpaperAtmosphere(WallpaperAtmosphere.MINIMAL) },
                )
            },
        ))

        // ===== 主题包 =====
        ThemePackSection(
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
        )

        // ===== 桌面图标 =====
        var iconHidden by remember { mutableStateOf(AppIconManager.isLauncherIconHidden(context)) }
        // 手动隐藏但桌面没有任何 Niriko 快捷方式时，先确认（否则用户会从桌面彻底找不到应用）
        var confirmHideWithoutShortcut by remember { mutableStateOf(false) }
        val iconScope = rememberCoroutineScope()
        // 主题色变化时只刷新"主题色模式"的快捷方式图标；上传图片模式不覆盖（原 bug 根因）
        // 第 6 轮返工（用户实测定位）：旧流程只看 requestPinShortcut 的返回值就隐藏本体图标，
        // 而小米 HyperOS 上它**不弹窗、返回值仍为 true** → 用户同时失去快捷方式与本体图标。
        // 现在必须**确认桌面已有快捷方式**才隐藏；无法确认就保留原图标并说明原因。
        fun pinAndHide(ctx: android.content.Context, bitmap: android.graphics.Bitmap, mode: String) {
            iconScope.launch {
                when (AppIconManager.pinIcon(ctx, bitmap)) {
                    AppIconManager.PinOutcome.PINNED -> {
                        viewModel.setCustomIconMode(mode)
                        AppIconManager.hideLauncherIcon(ctx)
                        iconHidden = true
                        snackbarHostState.showSnackbar("桌面快捷方式已创建，原图标已隐藏")
                    }
                    AppIconManager.PinOutcome.UNCONFIRMED ->
                        snackbarHostState.showSnackbar(
                            "未能确认桌面已添加快捷方式，已保留原图标。" +
                                "小米/红米请先在「设置 → 应用设置 → 权限管理 → 桌面快捷方式」允许后再试",
                        )
                    AppIconManager.PinOutcome.UNSUPPORTED ->
                        snackbarHostState.showSnackbar("当前设备不支持创建桌面快捷方式，已保留原图标")
                }
            }
        }
        val imagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            iconScope.launch {
                val bitmap = runCatching {
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input, null, opts)
                    }
                }.getOrNull()
                if (bitmap != null) {
                    val icon = AppIconManager.generateIconFromImage(bitmap)
                    AppIconManager.saveCustomImageIcon(context, icon)
                    pinAndHide(context, icon, mode = "image")
                } else {
                    snackbarHostState.showSnackbar("无法读取所选图片")
                }
            }
        }

        SettingsGroupTitle("桌面图标")
        SettingsSplitGroup(content = listOf(
            {
                SettingsActionRow(
                    icon = Icons.Outlined.Brush,
                    title = "生成主题色图标",
                    description = "以当前主题色生成图标，钉到桌面并隐藏原图标",
                    onClick = {
                        pinAndHide(
                            context,
                            AppIconManager.generateThemeIcon(
                                context,
                                if (settings.customSeedColor == SeedColorScheme.UnsetSeed)
                                    SeedColorScheme.DefaultSeed.toArgb()
                                else settings.customSeedColor,
                            ),
                            mode = "theme",
                        )
                    },
                )
            },
            {
                SettingsActionRow(
                    icon = Icons.Outlined.Image,
                    title = "使用图片作图标",
                    description = "从相册选择图片，圆角裁切后钉到桌面",
                    onClick = { imagePicker.launch("image/*") },
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "隐藏桌面图标",
                    description = "关闭后恢复默认桌面图标",
                    checked = iconHidden,
                    onCheckedChange = { hidden ->
                        if (hidden) {
                            if (AppIconManager.isShortcutPinned(context)) {
                                AppIconManager.hideLauncherIcon(context)
                                iconHidden = true
                            } else {
                                confirmHideWithoutShortcut = true
                            }
                        } else {
                            // 恢复默认图标：还原 alias 并清除自定义模式
                            AppIconManager.showLauncherIcon(context)
                            viewModel.setCustomIconMode("none")
                            iconHidden = false
                        }
                    },
                )
            },
            {
                SettingsInfoRow(
                    icon = Icons.Outlined.Info,
                    title = "说明",
                    description = "只有确认桌面已添加快捷方式，才会隐藏原图标；无法确认时原图标会保留。" +
                        "小米/红米若点「生成主题色图标」没有弹窗：设置 → 应用设置 → 权限管理 → 桌面快捷方式，" +
                        "允许后重试。若误删快捷方式：系统设置 → 应用管理 → Niriko 打开后，在本页恢复默认图标。",
                )
            },
        ))

        // 手动隐藏前确认：桌面没有 Niriko 快捷方式时，隐藏后将从桌面彻底找不到应用（第 6 轮返工）
        if (confirmHideWithoutShortcut) {
            AlertDialog(
                onDismissRequest = { confirmHideWithoutShortcut = false },
                title = { Text("桌面还没有 Niriko 的快捷方式") },
                text = {
                    Text(
                        "现在隐藏桌面图标后，桌面将没有任何 Niriko 入口，" +
                            "只能从「系统设置 → 应用管理 → Niriko → 打开」进入，再回到本页恢复。确定要隐藏吗？",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmHideWithoutShortcut = false
                            AppIconManager.hideLauncherIcon(context)
                            iconHidden = true
                        },
                    ) { Text("仍然隐藏") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmHideWithoutShortcut = false }) { Text("取消") }
                },
            )
        }

        // ===== 分享卡 =====
        SettingsGroupTitle("分享卡")
        SettingsSplitGroup(content = listOf(
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Star,
                    title = "包含评分",
                    description = "分享卡显示我的/Bangumi 评分与评价人数",
                    checked = settings.shareIncludeRating,
                    onCheckedChange = viewModel::setShareIncludeRating,
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Timeline,
                    title = "包含进度",
                    description = "分享卡显示观看进度",
                    checked = settings.shareIncludeProgress,
                    onCheckedChange = viewModel::setShareIncludeProgress,
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.LocalOffer,
                    title = "包含个人标签",
                    description = "分享卡显示我的标签",
                    checked = settings.shareIncludeTags,
                    onCheckedChange = viewModel::setShareIncludeTags,
                )
            },
        ))

        // ===== 放送提醒 =====
        SettingsGroupTitle("放送提醒")
        SettingsSplitGroup(content = listOf(
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "在看条目放送提醒",
                    description = "在看作品新一集放送当天发送通知",
                    checked = settings.airingReminderEnabled,
                    onCheckedChange = viewModel::setAiringReminderEnabled,
                )
            },
        ))
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    }

    if (showThemeColorPicker) {
        ThemeColorPickerDialog(
            currentSeed = settings.customSeedColor,
            onSelect = { argb -> viewModel.setCustomSeedColor(argb) },
            onResetToDefault = { viewModel.setCustomSeedColor(null) },
            onDismiss = { showThemeColorPicker = false },
        )
    }
}

/** 单页壁纸覆盖行：值显示当前覆盖类型，已覆盖时给出「清除」尾部控件。 */
@Composable
private fun WallpaperPageRow(
    page: WallpaperPage,
    overrideUri: String,
    globalUri: String,
    onClickPick: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val clearAction: (@Composable () -> Unit)? = if (overrideUri.isNotBlank()) {
        ({ TextButton(onClick = onClear) { Text("清除") } })
    } else {
        null
    }
    SettingsPickerRow(
        icon = wallpaperPageIcon(page),
        title = page.label,
        description = "仅此页面覆盖全局壁纸",
        value = if (overrideUri.isNotBlank()) {
            wallpaperKindLabel(context, overrideUri) + " · 点击更换"
        } else {
            if (globalUri.isNotBlank()) "继承全局 · 点击设置" else "未设置 · 点击选择"
        },
        onClick = onClickPick,
        trailing = clearAction,
    )
}

/** 顶层四页壁纸槽位对应的行图标。 */
private fun wallpaperPageIcon(page: WallpaperPage): ImageVector = when (page) {
    WallpaperPage.LIBRARY -> Icons.Outlined.CollectionsBookmark
    WallpaperPage.DISCOVER -> Icons.Outlined.Explore
    WallpaperPage.STATS -> Icons.Outlined.Insights
    WallpaperPage.SETTINGS -> Icons.Outlined.Settings
}

/** 壁纸 URI 的类型标签（图片/视频/未设置）。 */
@Composable
private fun wallpaperKindLabel(context: Context, uriString: String): String {
    if (uriString.isBlank()) return "未设置"
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return "未设置"
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    return when {
        mime?.startsWith("video/") == true -> "视频"
        mime?.startsWith("image/") == true -> "图片"
        else -> "文件"
    }
}
