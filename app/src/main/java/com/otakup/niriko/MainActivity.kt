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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import com.otakup.niriko.data.settings.ThemeMode
import com.otakup.niriko.navigation.LocalSearchGestureLock
import com.otakup.niriko.navigation.MAIN_ROUTE
import com.otakup.niriko.navigation.NirikoBottomBar
import com.otakup.niriko.navigation.NirikoNavHost
import com.otakup.niriko.navigation.TopLevelDestination
import com.otakup.niriko.navigation.rememberNirikoNavState
import com.otakup.niriko.navigation.rememberSearchGestureLock
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.viewmodel.SettingsViewModel
import com.otakup.niriko.viewmodel.SettingsViewModelFactory
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 单 Activity 宿主：承载底部导航与全部 Compose 页面。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = (applicationContext as NirikoApplication)
            val settingsViewModel = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(app.settingsDataStore),            )
            val settings by settingsViewModel.settings.collectAsState()

            val isDarkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            NirikoTheme(
                darkTheme = isDarkTheme,
                dynamicColor = settings.dynamicColor,
                oledDark = settings.oledDark,
            ) {
                val navState = rememberNirikoNavState()
                // 顶层四页 Pager 状态：MainPager 与 NirikoBottomBar 共享（指示器跟手联动）
                val pagerState = rememberPagerState(pageCount = { TopLevelDestination.entries.size })
                // Pager 手势锁：搜索交互按下/拖拽期间锁定 Pager 翻页（根治左划冲突）
                val searchGestureLock = rememberSearchGestureLock()
                val navBackStackEntry by navState.navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                // 仅顶层 main 路由显示底栏；二级页面（详情/搜索等）隐藏
                val showBottomBar = currentRoute == MAIN_ROUTE

                CompositionLocalProvider(LocalSearchGestureLock provides searchGestureLock) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
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
                        // 液态玻璃 backdrop：捕获下方页面内容（底栏真折射的来源，SukiSU 同款 miuix）
                        // surface 色是 @Composable 读取，须在 DrawScope block 外取值
                        val backdropSurface = MaterialTheme.colorScheme.surface
                        val glassBackdrop = rememberLayerBackdrop {
                            drawRect(backdropSurface)
                            drawContent()
                        }
                        // 共享元素过渡作用域（Kazumi 同款：卡片封面 → 详情页封面缩放飞入）
                        SharedTransitionLayout {
                            val sharedTransitionScope = this
                            NirikoNavHost(
                                navController = navState.navController,
                                pagerState = pagerState,
                                sharedTransitionScope = sharedTransitionScope,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .layerBackdrop(glassBackdrop),
                            )
                        }
                        if (showBottomBar) {
                            NirikoBottomBar(
                                pagerState = pagerState,
                                backdrop = glassBackdrop,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
                }
            }
        }
    }
}