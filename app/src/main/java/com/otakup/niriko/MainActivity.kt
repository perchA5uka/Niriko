@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import com.otakup.niriko.data.settings.ThemeMode
import com.otakup.niriko.navigation.LocalSearchGestureLock
import kotlinx.coroutines.flow.first
import com.otakup.niriko.navigation.MAIN_ROUTE
import com.otakup.niriko.navigation.NirikoBottomBar
import com.otakup.niriko.navigation.NirikoNavHost
import com.otakup.niriko.navigation.TopLevelDestination
import com.otakup.niriko.navigation.rememberNirikoNavState
import com.otakup.niriko.navigation.SETTINGS_APPEARANCE_ROUTE
import com.otakup.niriko.navigation.rememberSearchGestureLock
import com.otakup.niriko.ui.common.LocalBottomBarHideFraction
import com.otakup.niriko.ui.common.rememberImageLuminance
import com.otakup.niriko.ui.components.LocalCardGlassBackdrop
import com.otakup.niriko.ui.components.LocalCardGlassLevel
import com.otakup.niriko.ui.components.LocalGlassLuminance
import com.otakup.niriko.ui.theme.LocalGlassEffect
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.ui.wallpaper.WallpaperHost
import com.otakup.niriko.util.WallpaperPage
import com.otakup.niriko.viewmodel.SettingsViewModel
import com.otakup.niriko.viewmodel.SettingsViewModelFactory
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantLayerBackdrop
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import com.otakup.niriko.data.notification.AiringReminderScheduler

/**
 * 单 Activity 宿主：承载底部导航与全部 Compose 页面。
 */
class MainActivity : ComponentActivity() {

    /** 放送提醒通知点击待打开的 subjectId（Compose 可观察，cold/warm start 均能触发导航）。 */
    private val pendingAiringSubjectId = androidx.compose.runtime.mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // .nirikotheme 外部打开：暂存 URI，由外观页主题管理消费
        handleThemePackIntent(intent)
        // 放送提醒通知点击：暂存待打开 subjectId，由 Compose 消费并导航到详情页
        intent.getLongExtra(AiringReminderScheduler.EXTRA_SUBJECT_ID, -1L).takeIf { it > 0 }?.let {
            pendingAiringSubjectId.value = it
        }
        // 单 Activity 已运行时的再次打开（singleTop 重新派发）
        // onNewIntent 见下方覆写
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = (applicationContext as NirikoApplication)
        // 开屏期间同步读取设置（DataStore 首读为毫秒级）：VM 以真实设置初始化，
        // 首帧即正确主题，无"默认绿→自定义色"闪烁；读完系统开屏立即放行
        val initialSettings = kotlinx.coroutines.runBlocking {
            app.settingsDataStore.settings.first()
        }
        setContent {
            val settingsViewModel = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                    app.settingsDataStore,
                    initialSettings = initialSettings,
                ),
            )
            val settings by settingsViewModel.settings.collectAsState()

            // 首次请求通知权限（阶段 J）：开关开启且未授权（API 33+）时请求
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* 结果无需处理：拒绝则 Worker 跳过发通知 */ }
            androidx.compose.runtime.LaunchedEffect(settings.airingReminderEnabled) {
                if (settings.airingReminderEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // 主题色变化 → 仅"主题色模式"刷新已钉入桌面的快捷方式图标
            // （"image"=用户上传图，绝不覆盖；未钉入时 updateShortcuts 为空操作）
            androidx.compose.runtime.LaunchedEffect(settings.customSeedColor) {
                if (settings.customIconMode == "theme") {
                    val color = if (settings.customSeedColor == com.otakup.niriko.ui.theme.SeedColorScheme.UnsetSeed)
                        com.otakup.niriko.ui.theme.SeedColorScheme.DefaultSeed.toArgb()
                    else settings.customSeedColor
                    com.otakup.niriko.ui.icon.AppIconManager.updatePinnedIcon(
                        this@MainActivity,
                        com.otakup.niriko.ui.icon.AppIconManager.generateThemeIcon(this@MainActivity, color),
                    )
                }
            }
            // 快捷方式缺失 → 回到前台自动恢复本体图标（防"隐形"丢失入口）
            // 第 6 轮返工：只在「用快捷方式替换图标」这条流程留下的隐藏态上兜底
            // （customIconMode != none）。用户主动在设置里隐藏的（none）不自动恢复，尊重用户选择；
            // 小米 HyperOS 上 requestPinShortcut 不弹窗时旧流程会留下「图标没了、快捷方式也没有」的状态，
            // 这一条是把它救回来的最后一道保险。
            androidx.lifecycle.compose.LifecycleResumeEffect(settings.customIconMode) {
                val iconManager = com.otakup.niriko.ui.icon.AppIconManager
                if (settings.customIconMode != "none" &&
                    iconManager.isLauncherIconHidden(this@MainActivity) &&
                    !iconManager.isShortcutPinned(this@MainActivity)
                ) {
                    iconManager.showLauncherIcon(this@MainActivity)
                }
                onPauseOrDispose { }
            }

            val isDarkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // 系统"关闭动画"（开发者选项 animator_duration_scale=0）→ 同样进入减少动态效果
            val systemAnimatorOff = runCatching {
                android.provider.Settings.Global.getFloat(
                    contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            }.getOrDefault(false)

            NirikoTheme(
                darkTheme = isDarkTheme,
                dynamicColor = settings.dynamicColor,
                oledDark = settings.oledDark,
                themeColorIndex = settings.themeColorIndex,
                customSeedColor = settings.customSeedColor,
                reduceMotion = settings.reduceMotion || systemAnimatorOff,
            ) {
                val navState = rememberNirikoNavState()
                // 放送提醒通知点击 → 打开对应详情页（key 用 state 值，cold/warm 均触发）
                androidx.compose.runtime.LaunchedEffect(pendingAiringSubjectId.value) {
                    val id = pendingAiringSubjectId.value
                    if (id != null) {
                        navState.navController.navigate("subject_detail/$id")
                        pendingAiringSubjectId.value = null
                    }
                }
                // 顶层四页 Pager 状态：MainPager 与 NirikoBottomBar 共享（指示器跟手联动）
                val pagerState = rememberPagerState(pageCount = { TopLevelDestination.entries.size })
                // Pager 手势锁：搜索交互按下/拖拽期间锁定 Pager 翻页（根治左划冲突）
                val searchGestureLock = rememberSearchGestureLock()
                val navBackStackEntry by navState.navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                // 仅顶层 main 路由显示底栏；二级页面（详情/搜索等）隐藏
                val showBottomBar = currentRoute == MAIN_ROUTE
                // 外部 .nirikotheme 打开：自动进入外观页（消费 ThemePackImportRequest）
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (com.otakup.niriko.data.themepack.ThemePackImportRequest.uri != null) {
                        navState.navController.navigate(SETTINGS_APPEARANCE_ROUTE)
                    }
                }

                val bottomBarHideFraction = remember { mutableFloatStateOf(0f) }
                // 切换顶层页时复位底栏隐藏状态，避免跨页残留
                androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
                    bottomBarHideFraction.floatValue = 0f
                }
                CompositionLocalProvider(
                    LocalSearchGestureLock provides searchGestureLock,
                    LocalGlassEffect provides settings.glassEffect,
                    LocalBottomBarHideFraction provides bottomBarHideFraction,
                ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 液态玻璃 backdrop（kyant）：捕获全窗口内容（壁纸 + Scaffold），
                    // 底部悬浮胶囊折射的来源；surface 色是 @Composable 读取，须在 DrawScope block 外取值
                    val backdropSurface = MaterialTheme.colorScheme.surface
                    val windowKyantBackdrop = rememberKyantLayerBackdrop {
                        drawRect(backdropSurface)
                        drawContent()
                    }
                    // 卡片专用 backdrop（kyant）：只捕获壁纸层，供每张卡做真液态玻璃折射。
                    val cardGlassBackdrop = rememberKyantLayerBackdrop {
                        drawContent()
                    }
                    val wallpaperLuma = rememberImageLuminance(settings.wallpaperUri) ?: 0.5f
                    CompositionLocalProvider(
                        LocalCardGlassBackdrop provides cardGlassBackdrop,
                        LocalCardGlassLevel provides settings.cardGlassLevel,
                        LocalGlassLuminance provides wallpaperLuma,
                    ) {
                    // 共享元素过渡作用域（Kazumi 同款：卡片封面 → 详情页封面缩放飞入）
                    SharedTransitionLayout {
                        val sharedTransitionScope = this
                        // 捕获层内先画壁纸（全窗口，含状态栏区域）再画页面：
                        // 1) 底栏 drawBackdrop 可折射壁纸；2) 状态栏区域显示壁纸而非 window 背景
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .kyantLayerBackdrop(windowKyantBackdrop),
                        ) {
                            // 壁纸层单独捕获（kyant）→ 供每张卡做真液态玻璃折射的内容源
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .kyantLayerBackdrop(cardGlassBackdrop),
                            ) {
                                WallpaperHost(
                                    enabled = settings.wallpaperEnabled,
                                    globalUri = settings.wallpaperUri,
                                    perPageUris = mapOf(
                                        "library" to settings.wallpaperLibraryUri,
                                        "discover" to settings.wallpaperDiscoverUri,
                                        "stats" to settings.wallpaperStatsUri,
                                        "settings" to settings.wallpaperSettingsUri,
                                    ),
                                    currentPage = WallpaperPage.entries
                                        .getOrElse(pagerState.currentPage) { WallpaperPage.LIBRARY },
                                    active = showBottomBar,
                                    blurDp = settings.wallpaperBlurDp,
                                    atmosphere = settings.wallpaperAtmosphere,
                                )
                            }
                            // Scaffold：透明容器（不遮壁纸），但显式指定 contentColor=onBackground，
                            // 避免透明背景推导出错误的文字默认色（深色模式下标题变黑的根因）
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                            ) { innerPadding ->
                                // 内容全高 + 悬浮胶囊叠加层：内容可滚动到胶囊后方，信息从半透明胶囊透出
                                // 详情页（subject_detail）让背景墙延伸到状态栏后：顶部不避让，仅保留 bottom/left/right；
                                // 其他页面保持原样（由 Scaffold systemBars inset 避让）。
                                val isSubjectDetail = currentRoute?.startsWith("subject_detail") == true
                                val boxPadding = if (isSubjectDetail) {
                                    PaddingValues(
                                        start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                                        top = 0.dp,
                                        end = innerPadding.calculateEndPadding(LayoutDirection.Ltr),
                                        bottom = innerPadding.calculateBottomPadding(),
                                    )
                                } else {
                                    innerPadding
                                }
                                Box(modifier = Modifier.fillMaxSize().padding(boxPadding)) {
                                    NirikoNavHost(
                                        navController = navState.navController,
                                        pagerState = pagerState,
                                        sharedTransitionScope = sharedTransitionScope,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                    if (showBottomBar) {
                        NirikoBottomBar(
                            pagerState = pagerState,
                            backdrop = windowKyantBackdrop,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                    }
                    // 开屏动画已按用户要求移除（系统 SplashScreen 主题与衔接动画一并删除）。
                    // Android 12+ 冷启动系统仍会绘制一帧启动画面，但现在是纯 Window 背景，
                    // 没有品牌绿底、也没有大 N 图标。
                }
                }
            }
        }
    }

    /** singleTop 重派发：应用已运行时再次打开 .nirikotheme。 */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleThemePackIntent(intent)
        intent.getLongExtra(AiringReminderScheduler.EXTRA_SUBJECT_ID, -1L).takeIf { it > 0 }?.let {
            pendingAiringSubjectId.value = it
        }
    }

    /** 识别 .nirikotheme 打开方式并暂存 URI（外观页主题管理消费）。 */
    private fun handleThemePackIntent(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (intent.action != android.content.Intent.ACTION_VIEW) return
        val path = data.lastPathSegment?.lowercase()
        if (path?.endsWith(".nirikotheme") != true) return
        com.otakup.niriko.data.themepack.ThemePackImportRequest.uri = data.toString()
    }
}