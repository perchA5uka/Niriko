package com.otakup.niriko.ui.settings

import com.otakup.niriko.navigation.SETTINGS_ABOUT_ROUTE
import com.otakup.niriko.navigation.SETTINGS_APPEARANCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_DATASOURCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_LIBRARY_ROUTE
import com.otakup.niriko.navigation.SETTINGS_REFRESH_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SEARCH_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SYNC_ROUTE
import org.junit.Assert.assertEquals
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
            listOf(SettingsCategory.REFRESH, SettingsCategory.ABOUT),
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
