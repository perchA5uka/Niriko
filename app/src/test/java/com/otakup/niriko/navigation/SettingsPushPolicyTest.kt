package com.otakup.niriko.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D2：二级设置页「横向推入」转场判定的契约测试（v1.2.2）。
 *
 * 为什么要有它：转场 lambda 只在 Android 运行时生效，看代码只能"看着像对"。
 * 这里锁三件事：
 * 1. 判定只认 `settings_` 前缀——设置根页（`settings`）不参与，否则"根 = 无层级"的心智被破坏；
 * 2. 作品详情（封面共享元素）、搜索（顶栏展开面）、四个顶层页都**不能**命中，
 *    否则会和已有的 SharedAxisZ / 共享元素转场互相打架；
 * 3. 反向（pop）同样命中：pop 的 initial 是设置页，退出必须沿原路反向，不能掉回 Z 轴淡入。
 *
 * 纯函数测试，不需要 Compose 运行时；设置路由清单从 NavHost 源码里读，
 * 防止有人新增二级页却忘了转场归属。
 */
class SettingsPushPolicyTest {

    private fun navHostSource(): String = File("src/main/java/com/otakup/niriko/navigation/NirikoNavHost.kt").readText()

    /** NavHost 里的二级设置页路由常量：`const val SETTINGS_XXX_ROUTE = "settings_xxx"`。 */
    private fun settingsRouteConstants(): List<Pair<String, String>> = Regex("""const val (SETTINGS_[A-Z_]+_ROUTE) = "([^"]+)"""")
        .findAll(navHostSource())
        .map { it.groupValues[1] to it.groupValues[2] }
        .toList()

    @Test
    fun 只有带下划线的二级设置页算设置子页() {
        assertTrue(isSettingsSubpageRoute("settings_appearance"))
        assertTrue(isSettingsSubpageRoute("settings_about"))
        assertTrue(isSettingsSubpageRoute("settings_refresh"))
        // 设置根页不参与：它没有"父页"，横移会破坏"根 = 无层级"的心智
        assertFalse(isSettingsSubpageRoute("settings"))
        // 空值/无关路由
        assertFalse(isSettingsSubpageRoute(null))
        assertFalse(isSettingsSubpageRoute(""))
        assertFalse(isSettingsSubpageRoute("library"))
    }

    @Test
    fun 从设置根页进二级页要走横移() {
        assertTrue(usesSettingsPushTransition("settings", "settings_appearance"))
    }

    @Test
    fun 二级页之间下钻与返回都要走横移() {
        assertTrue(usesSettingsPushTransition("settings_appearance", "settings_wallpaper_library"))
        // pop：initial 是设置页，target 是它的父级
        assertTrue(usesSettingsPushTransition("settings_wallpaper_library", "settings"))
        assertTrue(usesSettingsPushTransition("settings_appearance", "settings"))
    }

    @Test
    fun 非设置页之间的跳转绝不横移() {
        // 顶层四页之间（顶层四页由 MainPager 承载，本身不走 NavHost 转场）
        assertFalse(usesSettingsPushTransition("library", "discover"))
        assertFalse(usesSettingsPushTransition("stats", "library"))
        // 作品详情：已有封面共享元素，横移会双重位移
        assertFalse(usesSettingsPushTransition("library", "subject_detail/42"))
        assertFalse(usesSettingsPushTransition("subject_detail/42", "library"))
        // 搜索：顶栏展开面，属于"同层放大"
        assertFalse(usesSettingsPushTransition("library", "subject_search"))
    }

    @Test
    fun 所有二级设置页路由常量都命中判定() {
        val constants = settingsRouteConstants()
        // 数量本身是契约：新增二级页时必须回来看转场（v1.2.2 审计基线 = 10 条）
        assertEquals(
            "二级设置页路由数量变化（v1.2.2 基线 10 条）：新增/删除后同步本断言与 docs/v1.2.2/00-inventory.md",
            10,
            constants.size,
        )
        for ((name, route) in constants) {
            assertTrue("$name 取值为 \"$route\"，不符合 settings_ 前缀约定", isSettingsSubpageRoute(route))
            assertTrue("从设置根页进入 $route 时没有命中横移", usesSettingsPushTransition("settings", route))
        }
    }

    @Test
    fun NavHost路由数量与审计基线一致() {
        val count = Regex("""\bcomposable\s*\(""").findAll(navHostSource()).count()
        assertEquals("NavHost 内 composable() 数量变化（v1.2.2 审计基线 = 19）", 19, count)
    }

    @Test
    fun NavHost确实把四条转场接到了判定上() {
        val text = navHostSource()
        val call = "usesSettingsPushTransition(initialState.destination.route, targetState.destination.route)"
        assertEquals("四条 transition lambda 的命中条件不一致", 4, text.split(call).size - 1)
        // 进/退方向必须是横向推入，且 pop 反向（这是 D2 的核心观感）
        assertTrue("enter 应走 enterFromRight", text.contains("-> enterFromRight(specs)"))
        assertTrue("exit 应走 exitToLeft", text.contains("-> exitToLeft(specs)"))
        assertTrue("popEnter 应走 enterFromLeft", text.contains("-> enterFromLeft(specs)"))
        assertTrue("popExit 应走 exitToRight", text.contains("-> exitToRight(specs)"))
    }

    @Test
    fun 封面共享转场优先级高于设置横移() {
        val text = navHostSource()
        val coverIndex = text.indexOf("usesSubjectCoverTransition(initialState.destination.route")
        val pushIndex = text.indexOf("usesSettingsPushTransition(initialState.destination.route")
        assertTrue("找不到封面共享转场判定", coverIndex > 0)
        assertTrue("找不到设置横移判定", pushIndex > 0)
        assertTrue("cover 分支必须写在 settings push 之前，否则封面飞行会被抢走", coverIndex < pushIndex)
    }
}
