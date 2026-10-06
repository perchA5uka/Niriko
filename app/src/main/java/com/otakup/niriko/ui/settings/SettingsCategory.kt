package com.otakup.niriko.ui.settings

import com.otakup.niriko.navigation.SETTINGS_ABOUT_ROUTE
import com.otakup.niriko.navigation.SETTINGS_APPEARANCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_DATASOURCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_DONATE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_LIBRARY_ROUTE
import com.otakup.niriko.navigation.SETTINGS_REFRESH_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SEARCH_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SYNC_ROUTE

/**
 * B2c：设置分类分组（宽屏两栏左列的三个分组标题）。
 *
 * 取值与窄屏 SettingsScreen 单列列表里硬编码的三组标题／描述一一对应。
 * 窄屏实现按「手机端零变化」硬要求原样保留（不重构为数据驱动），
 * 本文件是它的可单测镜像；两边若不一致由 SettingsCategoryTest 兜住。
 */
enum class SettingsCategoryGroup(val title: String, val description: String) {
    PREFERENCE("偏好", "外观、收藏展示与搜索"),
    DATA("数据", "数据源、账号、同步与备份"),
    OTHER("其他", "诊断与版本信息"),
}

/**
 * B2c：设置分类（宽屏两栏左列的导航项 + 右列要嵌入的二级页）。
 *
 * route 与 NirikoNavHost 注册的二级页路由常量同源，所以同一个分类既能
 * 在窄屏以「跳转独立二级页」表达，也能在宽屏以「切右列内容」表达。
 */
enum class SettingsCategory(
    val route: String,
    val title: String,
    val subtitle: String,
    val group: SettingsCategoryGroup,
) {
    APPEARANCE(
        route = SETTINGS_APPEARANCE_ROUTE,
        title = "外观",
        subtitle = "主题配色、玻璃、壁纸与图标",
        group = SettingsCategoryGroup.PREFERENCE,
    ),
    LIBRARY(
        route = SETTINGS_LIBRARY_ROUTE,
        title = "收藏与展示",
        subtitle = "卡片展示、统计与启动页",
        group = SettingsCategoryGroup.PREFERENCE,
    ),
    SEARCH(
        route = SETTINGS_SEARCH_ROUTE,
        title = "搜索",
        subtitle = "内容过滤与搜索建议",
        group = SettingsCategoryGroup.PREFERENCE,
    ),
    DATASOURCE(
        route = SETTINGS_DATASOURCE_ROUTE,
        title = "数据源与账号",
        subtitle = "Bangumi / Steam 与第三方导入",
        group = SettingsCategoryGroup.DATA,
    ),
    SYNC(
        route = SETTINGS_SYNC_ROUTE,
        title = "同步与备份",
        subtitle = "WebDAV 同步与 JSON 备份",
        group = SettingsCategoryGroup.DATA,
    ),
    REFRESH(
        route = SETTINGS_REFRESH_ROUTE,
        title = "刷新诊断",
        subtitle = "各数据源的新鲜度、退避与强制刷新",
        group = SettingsCategoryGroup.OTHER,
    ),
    ABOUT(
        route = SETTINGS_ABOUT_ROUTE,
        title = "关于",
        subtitle = "版本与开源许可",
        group = SettingsCategoryGroup.OTHER,
    ),
    DONATE(
        route = SETTINGS_DONATE_ROUTE,
        title = "向开发者捐赠",
        subtitle = "帮助我继续更新",
        group = SettingsCategoryGroup.OTHER,
    );

    companion object {
        /** 宽屏打开设置时的默认选中项（第一个分类）。 */
        val default: SettingsCategory = APPEARANCE

        /**
         * 分类路由 → 分类（纯函数）。
         *
         * 未知／空路由回落到 default，这样 rememberSaveable 恢复出的旧值
         * 或外部传入的任意字符串都不会让宽屏右列落到空白。
         */
        fun fromRoute(route: String?): SettingsCategory =
            entries.firstOrNull { it.route == route } ?: default

        /** 某个分组下的分类，保持枚举声明顺序（= 窄屏列表顺序）。 */
        fun of(group: SettingsCategoryGroup): List<SettingsCategory> =
            entries.filter { it.group == group }

        /** 左列渲染顺序：分组顺序 × 组内声明顺序。 */
        fun groups(): List<Pair<SettingsCategoryGroup, List<SettingsCategory>>> =
            SettingsCategoryGroup.entries.map { group -> group to of(group) }

        /** 分类在扁平列表中的下标（0 = 第一个分类）。 */
        fun indexOf(category: SettingsCategory): Int = entries.indexOf(category)
    }
}
