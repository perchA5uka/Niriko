package com.otakup.niriko.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.2 WP1：设置页对齐收口（V-05/06/07/09/16）、按压反馈（M1）与禁用态（F09）的源码契约。
 *
 * 这批改动全是组合函数里的视觉参数（内距、行高、箭头、覆盖层），没有可直接断言的运行时输出，
 * 沿用 [SettingsSurfaceTokenTest] 的做法用源码文本钉住：
 * 1. 水平内距只有一个常量，页面各处都引用它（否则下一轮又会各写一个 20dp）；
 * 2. 行尾箭头、行高、值排版各只有一种实现；
 * 3. 矩形水波不得回潮（M1 已换成整行圆角覆盖）；
 * 4. 外观页的主题包分组与其余 9 组同为 24dp。
 *
 * 注意：本测试只做文本断言，不保证编译通过、也不衡量真机观感——观感仍要按 ui-review 清单目测。
 */
class SettingsLayoutContractTest {

    private fun source(file: String) = File("src/main/java/com/otakup/niriko/ui/settings/" + file).readText()

    private fun theme(file: String) = File("src/main/java/com/otakup/niriko/ui/theme/" + file).readText()

    @Test
    fun horizontalInsetIsOneConstantAt16dp() {
        val layout = source("SettingsLayout.kt")
        // V-05：整改前同屏 0 / 16 / 20dp 三种缩进，这里是唯一来源
        assertTrue(layout.contains("val SettingsPageHorizontalInset: Dp = 16.dp"))
        // V-07：行高基准 56 / 72dp，箭头与图标容器尺寸也走常量
        assertTrue(layout.contains("val SettingsRowMinHeight: Dp = 56.dp"))
        assertTrue(layout.contains("val SettingsRowTwoLineMinHeight: Dp = 72.dp"))
        assertTrue(layout.contains("val SettingsChevronSize: Dp = 20.dp"))
    }

    @Test
    fun homeScreenUsesTheInsetAndDropsTheTwentyDpStart() {
        val screen = source("SettingsScreen.kt")
        // 页面标题 / 搜索框 / 分类行内距全部引用同一个常量（少了这条就是缩进回潮）
        assertTrue(screen.contains("SettingsPageHorizontalInset"))
        assertFalse("页面标题不得再手写 20dp 左内距", screen.contains("start = 20.dp"))
    }

    @Test
    fun groupCardsCarryTheInsetThemselves() {
        val group = source("SettingsGroup.kt")
        // V-05 采用「组卡自带内距」方案：调用点（含 9 个二级页）不必各写一遍
        assertTrue(group.contains("padding(horizontal = SettingsPageHorizontalInset)"))
        assertTrue(group.contains("start = SettingsPageHorizontalInset"))
    }

    @Test
    fun detailPageTitlesUseHeadlineSmall() {
        // V-16：二级页标题统一 headlineSmall（Type.kt 的 24sp / w600）
        assertTrue(source("SettingsGroup.kt").contains("MaterialTheme.typography.headlineSmall"))
        val type = theme("Type.kt")
        assertTrue(type.contains("headlineSmall = TextStyle("))
        assertTrue(type.contains("fontSize = 24.sp"))
        assertTrue(type.contains("fontWeight = FontWeight.SemiBold"))
    }

    @Test
    fun searchFieldIsTheSharedPillComponent() {
        // V-10 / P2-1：设置页不再自己画输入框（也不再是 M3 Outlined），
        // 而是走全站唯一的 NirikoSearchField（胶囊玻璃 + focus 描边 + 48dp 清除钮）。
        val screen = source("SettingsScreen.kt")
        assertFalse(screen.contains("OutlinedTextField"))
        assertFalse("设置页不应再手写 BasicTextField（统一组件在 ui/components/NirikoSearchField.kt）", screen.contains("BasicTextField("))
        assertTrue(screen.contains("NirikoSearchField("))
        val field = File("src/main/java/com/otakup/niriko/ui/components/NirikoSearchField.kt").readText()
        assertTrue(field.contains("BasicTextField("))
        assertTrue(field.contains("NirikoShapes.PillShape"))
        assertTrue("统一搜索框必须有 focus 描边", field.contains("onFocusChanged"))
    }

    @Test
    fun chevronHasASingleImplementation() {
        // V-07：整改前分类行 24dp、组内行 20dp、搜索结果行各写一遍，现在只有 SettingsLayout 一处
        assertTrue(source("SettingsLayout.kt").contains("fun SettingsChevron(onDark: Boolean = false)"))
        assertFalse(source("SettingsItem.kt").contains("Icons.Outlined.ChevronRight"))
        assertFalse(source("SettingsScreen.kt").contains("Icons.Outlined.ChevronRight"))
        assertFalse(source("AdaptiveSettingsPane.kt").contains("Icons.Outlined.ChevronRight"))
    }

    @Test
    fun rowValuesAreBareTextOnly() {
        val item = source("SettingsItem.kt")
        // V-09：删掉 8dp 圆角「当前值」徽标，滑块行与选择行共用裸文字
        assertFalse(item.contains("RoundedCornerShape(8.dp)"))
        assertFalse(item.contains("ValueBadgeShape"))
        assertTrue(item.contains("SettingsValueText("))
    }

    @Test
    fun rowsUseTheRowIndicationInsteadOfRipple() {
        // M1：矩形水波 → 整行圆角实心覆盖（进度只在 draw 阶段读）
        val item = source("SettingsItem.kt")
        assertFalse(item.contains("indication = ripple()"))
        assertTrue(item.contains("nirikoRowIndication("))
        assertTrue(source("SettingsScreen.kt").contains("nirikoRowIndication("))
        assertTrue(source("AdaptiveSettingsPane.kt").contains("nirikoRowIndication("))
        val indication = File("src/main/java/com/otakup/niriko/ui/components/NirikoRowIndication.kt").readText()
        assertTrue(indication.contains("ROW_PRESS_ALPHA"))
        assertTrue(indication.contains("drawWithContent"))
    }

    @Test
    fun actionRowDisabledStateIsVisuallyDistinct() {
        // F09：禁用态此前和可用态一模一样，用户看不出点不了
        val item = source("SettingsItem.kt")
        assertTrue(item.contains("DisabledRowAlpha = 0.38f"))
        assertTrue(item.contains(".alpha(if (enabled) 1f else DisabledRowAlpha)"))
    }

    @Test
    fun appearanceThemePackGroupsUseTheTwentyFourDpWrapper() {
        // V-06：外观页里这两组曾是 20dp，与同页其余 9 组不一致
        val packs = source("pages/ThemePackSection.kt")
        assertFalse(packs.contains("SettingsSplitGroup("))
        assertEquals(2, Regex("SettingsPageSplitGroup\\(").findAll(packs).count())
    }
}
