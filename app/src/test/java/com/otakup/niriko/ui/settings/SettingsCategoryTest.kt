package com.otakup.niriko.ui.settings

import com.otakup.niriko.navigation.SETTINGS_ABOUT_ROUTE
import com.otakup.niriko.navigation.SETTINGS_APPEARANCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_DATASOURCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_DONATE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_LIBRARY_ROUTE
import com.otakup.niriko.navigation.SETTINGS_REFRESH_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SEARCH_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SYNC_ROUTE
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2c：设置分类模型的纯函数测试（宽屏两栏左列的分组 / 路由 → 选中项映射）。
 *
 * 窄屏单列列表刻意保持硬件编码、不重构为数据驱动（「手机端零变化」硬要求），
 * [SettingsCategory] 是它的可单测镜像；这里兜住两边不一致与路由漂移。
 */
class SettingsCategoryTest {

    // ==================== default / indexOf ====================

    @Test
    fun defaultIsFirstCategory() {
        // 宽屏首次进入设置时右列显示第一个分类：外观
        assertEquals(SettingsCategory.APPEARANCE, SettingsCategory.default)
        assertEquals(0, SettingsCategory.indexOf(SettingsCategory.default))
    }

    @Test
    fun indexOfFollowsGroupsFlattenOrder() {
        // 左列渲染顺序 == groups() 展平顺序；indexOf 必须与之逐项对齐
        val flattened = SettingsCategory.groups().flatMap { it.second }
        assertEquals(SettingsCategory.entries.toList(), flattened)
        flattened.forEachIndexed { index, category ->
            assertEquals(index, SettingsCategory.indexOf(category))
        }
    }

    @Test
    fun indexOfIsUniqueForEveryCategory() {
        val indices = SettingsCategory.entries.map { SettingsCategory.indexOf(it) }
        assertEquals(indices.size, indices.toSet().size)
    }

    // ==================== fromRoute ====================

    @Test
    fun fromRouteMapsEveryRouteToItsCategory() {
        SettingsCategory.entries.forEach { category ->
            assertEquals(category, SettingsCategory.fromRoute(category.route))
        }
    }

    @Test
    fun fromRouteFallsBackToDefault() {
        // 未知 / 空 / null 路由都必须回落到默认项，右列不允许空白
        assertEquals(SettingsCategory.default, SettingsCategory.fromRoute("not_a_settings_route"))
        assertEquals(SettingsCategory.default, SettingsCategory.fromRoute(""))
        assertEquals(SettingsCategory.default, SettingsCategory.fromRoute(null))
    }

    // ==================== of / groups ====================

    @Test
    fun groupsArePreferenceDataOtherInOrder() {
        // 与窄屏单列列表的三个分组标题同序
        assertEquals(
            listOf(
                SettingsCategoryGroup.PREFERENCE,
                SettingsCategoryGroup.DATA,
                SettingsCategoryGroup.OTHER,
            ),
            SettingsCategory.groups().map { it.first },
        )
    }

    @Test
    fun everyCategoryBelongsToExactlyOneNonEmptyGroup() {
        val flattened = SettingsCategory.groups().flatMap { it.second }
        assertEquals(SettingsCategory.entries.size, flattened.size)
        assertEquals(SettingsCategory.entries.toSet(), flattened.toSet())
        SettingsCategoryGroup.entries.forEach { group ->
            assertTrue("分组 ${group.title} 不应为空", SettingsCategory.of(group).isNotEmpty())
        }
    }

    @Test
    fun ofKeepsDeclarationOrderWithinGroup() {
        assertEquals(
            listOf(SettingsCategory.APPEARANCE, SettingsCategory.LIBRARY, SettingsCategory.SEARCH),
            SettingsCategory.of(SettingsCategoryGroup.PREFERENCE),
        )
        assertEquals(
            listOf(SettingsCategory.DATASOURCE, SettingsCategory.SYNC),
            SettingsCategory.of(SettingsCategoryGroup.DATA),
        )
        assertEquals(
            listOf(SettingsCategory.REFRESH, SettingsCategory.ABOUT, SettingsCategory.DONATE),
            SettingsCategory.of(SettingsCategoryGroup.OTHER),
        )
    }

    @Test
    fun groupTitlesMatchNarrowScreenListVerbatim() {
        // 窄屏 SettingsScreen 里硬编码的三组标题 / 描述，逐字一致
        assertEquals("偏好", SettingsCategoryGroup.PREFERENCE.title)
        assertEquals("外观、收藏展示与搜索", SettingsCategoryGroup.PREFERENCE.description)
        assertEquals("数据", SettingsCategoryGroup.DATA.title)
        assertEquals("数据源、账号、同步与备份", SettingsCategoryGroup.DATA.description)
        assertEquals("其他", SettingsCategoryGroup.OTHER.title)
        assertEquals("诊断与版本信息", SettingsCategoryGroup.OTHER.description)
    }

    // ==================== 与导航路由常量的一致性 ====================

    @Test
    fun routesMatchNavigationConstants() {
        // 同一个分类既要能「跳独立二级页」也要能「切右列内容」，路由必须同源
        assertEquals(SETTINGS_APPEARANCE_ROUTE, SettingsCategory.APPEARANCE.route)
        assertEquals(SETTINGS_LIBRARY_ROUTE, SettingsCategory.LIBRARY.route)
        assertEquals(SETTINGS_SEARCH_ROUTE, SettingsCategory.SEARCH.route)
        assertEquals(SETTINGS_DATASOURCE_ROUTE, SettingsCategory.DATASOURCE.route)
        assertEquals(SETTINGS_SYNC_ROUTE, SettingsCategory.SYNC.route)
        assertEquals(SETTINGS_REFRESH_ROUTE, SettingsCategory.REFRESH.route)
        assertEquals(SETTINGS_ABOUT_ROUTE, SettingsCategory.ABOUT.route)
        assertEquals(SETTINGS_DONATE_ROUTE, SettingsCategory.DONATE.route)
    }

    // ==================== F19 捐赠入口 ====================

    @Test
    fun donateRowKeepsTheRequestedTitleAndSubtitleAndBothLayoutsRenderIt() {
        // 需求原文：设置 - 其他 里一条标题「向开发者捐赠」、介绍「帮助我继续更新」
        assertEquals("向开发者捐赠", SettingsCategory.DONATE.title)
        assertEquals("帮助我继续更新", SettingsCategory.DONATE.subtitle)
        assertEquals(SettingsCategoryGroup.OTHER, SettingsCategory.DONATE.group)

        val narrow = File("src/main/java/com/otakup/niriko/ui/settings/SettingsScreen.kt").readText()
        assertTrue(narrow.contains("title = \"向开发者捐赠\""))
        assertTrue(narrow.contains("subtitle = \"帮助我继续更新\""))
        assertTrue(narrow.contains("onClick = { onNavigateToCategory(SETTINGS_DONATE_ROUTE) }"))

        // 宽屏左列点进来必须能渲染右列，否则是一条点了没反应/空白的分组项
        val adaptive = File("src/main/java/com/otakup/niriko/ui/settings/AdaptiveSettingsPane.kt").readText()
        assertTrue(adaptive.contains("SettingsCategory.DONATE -> DonateSettingsContent()"))
        assertTrue(adaptive.contains("SettingsCategory.DONATE -> Icons.Outlined.VolunteerActivism"))

        // 窄屏点进来要有真实路由，且该路由在导航宿主里注册过
        val navigation = File("src/main/java/com/otakup/niriko/navigation/NirikoNavHost.kt").readText()
        assertTrue(navigation.contains("const val SETTINGS_DONATE_ROUTE = \"settings_donate\""))
        assertTrue(navigation.contains("SETTINGS_DONATE_ROUTE,\n        ) {"))
        assertTrue(navigation.contains("DonateSettingsScreen("))
    }

    @Test
    fun narrowSettingsListLeavesMoreThanOneDockHeightBelowTheLastGroup() {
        // 回归：新增「向开发者捐赠」后它成了分组最后一行，被悬浮 dock 压住 ——
        // 设置主页（顶层页，dock 挂在 MainPager 上）必须在底部预留超过一个 dock 高度的空白。
        val narrow = File("src/main/java/com/otakup/niriko/ui/settings/SettingsScreen.kt").readText()

        // dock 胶囊高度直接来自组件自身，避免这里的常量与真实 dock 脱钩
        val dock = File("src/main/java/com/otakup/niriko/ui/bottombar/LiquidBottomTabs.kt").readText()
        assertTrue(dock.contains(".height(64f.dp)"))

        val declared = narrow.substringAfter("private val DockBottomReserve = ").substringBefore(".dp").toFloatOrNull()
        assertNotNull("设置页底部没有为悬浮 dock 预留空白", declared)
        assertTrue("底部留白 ${declared}dp 必须超过一个 dock 高度 64dp", declared!! > 64f)

        // 留白还要叠加系统导航栏 inset（dock 自己也在 navigationBarsPadding）：
        // 少这一层时三键导航机型仍会把最后一行压掉一半
        val spacer = narrow
            .substringAfter("底部留白必须盖住悬浮 dock")
            .substringBefore("private val DockBottomReserve")
        assertTrue("底部 Spacer 缺少 navigationBarsPadding()", spacer.contains("navigationBarsPadding()"))
        assertTrue("底部留白没有用在设置分组之后", spacer.contains("Spacer("))
    }

    @Test
    fun donatePageShowsBothPaymentCodesFromRealAssets() {
        val page = File("src/main/java/com/otakup/niriko/ui/settings/pages/DonateSettingsScreen.kt").readText()
        assertTrue(page.contains("R.drawable.donate_alipay"))
        assertTrue(page.contains("R.drawable.donate_wechat"))
        // 图片必须真的在资源目录里（且非空），否则这一页打开只有说明文字
        assertTrue(File("src/main/res/drawable-nodpi/donate_alipay.jpg").length() > 0)
        assertTrue(File("src/main/res/drawable-nodpi/donate_wechat.png").length() > 0)
        // 两种收款方式都要有可读的标题
        assertTrue(page.contains("\"支付宝\""))
        assertTrue(page.contains("\"微信支付\""))
    }

    @Test
    fun routesAreNonBlankAndUnique() {
        val routes = SettingsCategory.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
        routes.forEach { route -> assertTrue("路由不应为空", route.isNotBlank()) }
    }

    @Test
    fun titlesAndSubtitlesAreNonBlank() {
        SettingsCategory.entries.forEach { category ->
            assertTrue("${category.name} 缺标题", category.title.isNotBlank())
            assertTrue("${category.name} 缺副标题", category.subtitle.isNotBlank())
        }
        SettingsCategoryGroup.entries.forEach { group ->
            assertTrue("${group.name} 缺标题", group.title.isNotBlank())
            assertTrue("${group.name} 缺描述", group.description.isNotBlank())
        }
    }
}
