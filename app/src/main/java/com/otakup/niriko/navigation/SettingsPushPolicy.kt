package com.otakup.niriko.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/**
 * 二级设置页的「横向推入」转场判定（D2 决策，v1.2.2 新增）。
 *
 * 为什么单独抽成纯函数：路由判定必须能在 JVM 单测里跑，而 NavHost 的 transition lambda
 * 是 Android 运行时才有的；把「哪条路由算二级设置页」写死在 lambda 里，就再也测不了。
 *
 * 为什么只对设置页生效：
 *  - 作品详情页已经有封面共享元素（`usesSubjectCoverTransition`），再叠横向推入会双重位移；
 *  - 搜索页是从顶栏展开面进入的，属于「同层放大」，不是层级下钻；
 *  - 设置树是唯一纯粹「父 → 子 → 孙」的层级结构，横向推入在这条路径上表达层级最自然，
 *    也正好复用 v1.2.1 就写好的 [com.otakup.niriko.ui.animation.enterFromRight]（此前零调用）。
 */
private const val SETTINGS_ROUTE_PREFIX = "settings_"

/** 该路由是否是设置树里的二级页（`settings_appearance` / `settings_about` …）。 */
fun isSettingsSubpageRoute(route: String?): Boolean = route != null && route.startsWith(SETTINGS_ROUTE_PREFIX)

/**
 * 这对「起止路由」是否应该走横向推入。
 *
 * 命中任意一边即成立：从设置主页进二级页（target 是设置页）、二级页进三级页、
 * 以及返回时的反向（pop）——pop 的 initial 是设置页，同样要命中原路退出。
 */
fun usesSettingsPushTransition(initialRoute: String?, targetRoute: String?): Boolean = isSettingsSubpageRoute(initialRoute) || isSettingsSubpageRoute(targetRoute)
