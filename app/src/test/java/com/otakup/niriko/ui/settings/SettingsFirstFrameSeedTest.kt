package com.otakup.niriko.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * X3：设置二级页「首帧种子」契约测试。
 *
 * 现象：进「设置 → 搜索」时「允许显示 R18 内容」开关自己播一次 thumb 位移动画。
 * 根因：每个二级页自建 SettingsViewModel，而 SettingsViewModel.settings 是
 * stateIn(..., initialValue = initialSettings ?: AppSettings())；不传种子时首帧是默认值
 * （nsfwEnabled = false），DataStore 真实值随后到达 → M3 Switch 从 false 播到 true。
 *
 * 这个 bug 没法在 JVM 单测里用 Compose 断言（SettingsViewModel 的 viewModelScope 需要主线程，
 * 见 SearchStateTest 的注释），所以按本仓库既有的「源码契约」风格（SettingsCategoryTest /
 * SharedSubjectCoverContractTest）钉住三件事：
 * 1. 种子的**来源**是 Activity 作用域实例的 settings（StateFlow，首帧 .value 即真实值）；
 * 2. 种子的**上游**没断：MainActivity 创建该实例时确实传了 initialSettings；
 * 3. 每个二级页（尤其搜索页）的工厂都把这个种子传下去了。
 *
 * 任何一条被改坏，R18 开关的动画就会回来 —— 这就是这三个断言存在的理由。
 */
class SettingsFirstFrameSeedTest {

    private fun source(path: String): String =
        File("src/main/java/com/otakup/niriko/$path").readText()

    private val navHost by lazy { source("navigation/NirikoNavHost.kt") }
    private val mainActivity by lazy { source("MainActivity.kt") }

    /** 种子必须在 NavHost 之前取，且取自 settings（StateFlow）而不是 first()（挂起）。 */
    @Test
    fun rootSeedIsTheActivityScopedStateFlowValue() {
        assertTrue(
            "NavHost 必须从 Activity 作用域实例取 settings 作为二级页种子",
            navHost.contains("val rootSettings = viewModel<SettingsViewModel>("),
        )
        assertTrue(
            "种子必须读 StateFlow 的 .value（首帧同步可用），不能读 suspend 的 first()",
            navHost.contains(").settings\n") &&
                navHost.contains("initialSettings = rootSettings.value"),
        )
        assertTrue(
            "rootSettings 必须在 NavHost 之前取到（否则路线内的 value 读不到同一个实例）",
            navHost.indexOf("val rootSettings") in 0 until navHost.indexOf("    NavHost("),
        )
    }

    /** 上游：MainActivity 用真实设置初始化了 Activity 作用域那个实例（否则种子是假的）。 */
    @Test
    fun mainActivitySeedsTheActivityScopedViewModel() {
        val call = mainActivity.substringAfter("SettingsViewModelFactory(").substringBefore(")")
        assertTrue(
            "MainActivity 创建设置 VM 时必须传 initialSettings = initialSettings，否则 rootSettings.value 退化为 AppSettings()",
            call.contains("initialSettings = initialSettings"),
        )
    }

    /** 下游：每个二级页工厂都传了种子；搜索页（R18 开关所在页）必须包含在内。 */
    @Test
    fun everyDetailPageFactoryPassesTheSeed() {
        var checked = 0
        var index = navHost.indexOf("SettingsViewModelFactory(")
        while (index >= 0) {
            // 一个工厂调用实际 4 行（实测到 initialSettings 约 310 字符），留到 700 字符抗注释增删
            val block = navHost.substring(index, (index + 700).coerceAtMost(navHost.length))
            if (block.contains("app.bangumiSyncManager")) {
                checked++
                assertTrue(
                    "二级页工厂必须传 initialSettings = rootSettings.value（第 $checked 个漏了）",
                    block.contains("initialSettings = rootSettings.value"),
                )
            }
            index = navHost.indexOf("SettingsViewModelFactory(", index + 1)
        }
        assertEquals("NirikoNavHost 里应有 8 个设置二级页工厂", 8, checked)

        val searchBlock = navHost.substringAfter("SETTINGS_SEARCH_ROUTE,").take(700)
        assertTrue(
            "搜索页（R18 开关）必须被种子覆盖",
            searchBlock.contains("initialSettings = rootSettings.value"),
        )
    }

    /**
     * 宽屏两栏右列（SettingsScreen → SettingsExpandedScreen → AdaptiveSettingsPane.settingsViewModel()）
     * 不走 NavHost 路由，种子经 LocalSettingsSeed 共享。这条分支漏掉的话，平板/折叠屏展开时
     * R18 开关照样会动 —— 所以单独钉一条。
     */
    @Test
    fun wideScreenPaneAlsoGetsTheSeedThroughTheSharedLocal() {
        val pane = source("ui/settings/AdaptiveSettingsPane.kt")
        assertTrue(
            "宽屏工厂必须用 LocalSettingsSeed 作为种子",
            pane.contains("LocalSettingsSeed.current?.value"),
        )
        val call = pane.substringAfter("SettingsViewModelFactory(").substringBefore("initialSettings")
        assertTrue(
            "宽屏工厂必须在参数列表里出现 initialSettings",
            call.isNotEmpty() && pane.contains("initialSettings = seed"),
        )
        assertTrue(
            "NirikoNavHost 必须在 MAIN 路由提供共享种子（否则宽屏读到 null）",
            navHost.contains("CompositionLocalProvider(LocalSettingsSeed provides rootSettings)"),
        )
        assertTrue(
            "共享种子必须是可空默认的 compositionLocal（预览/测试组合时不能崩）",
            navHost.contains("internal val LocalSettingsSeed") &&
                navHost.contains("compositionLocalOf { null }"),
        )
    }
}
