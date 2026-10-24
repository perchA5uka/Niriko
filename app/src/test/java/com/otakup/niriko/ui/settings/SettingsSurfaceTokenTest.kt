package com.otakup.niriko.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B7b / B7c：设置条目与二级页卡片的「圆角 + 材质」收敛契约。
 *
 * 这两批只做视觉，没有可断言的运行时输出（组合函数），所以用源码契约钉住：
 * 1. 行内 / 组外圆角必须走 [NirikoShapes] 令牌，不能退回字面量（否则下一轮又会各写各的）；
 * 2. 二级页只覆盖 4 个高频页，其余页必须仍是默认 20dp（防止「顺手全换」造成大 diff）。
 */
class SettingsSurfaceTokenTest {

    private fun source(file: String) =
        File("src/main/java/com/otakup/niriko/ui/settings/" + file).readText()

    @Test
    fun rowRadiiComeFromShapeTokens() {
        val group = source("SettingsGroup.kt")
        assertTrue(group.contains("private val GroupOuterRadius: Dp = NirikoShapes.Card"))
        assertTrue(group.contains("private val RowInnerRadius: Dp = NirikoShapes.RowInner"))
        // 字面量不得回潮（B7b 的核心）
        assertFalse(group.contains("GroupOuterRadius = 20.dp"))
        assertFalse(group.contains("RowInnerRadius = 4.dp"))
    }

    @Test
    fun groupContainerAndFirstLastRowsShareTheSameOuterRadius() {
        val group = source("SettingsGroup.kt")
        assertTrue(group.contains("outerRadius: Dp = GroupOuterRadius"))
        assertTrue(group.contains("appleGlassCard(shape = RoundedCornerShape(outerRadius))"))
        // 首/末行的 morph 目标必须跟组容器同一个值，否则 24dp 组里会露出 20dp 的角
        assertTrue(group.contains("if (pressed || index == 0) outerRadius else RowInnerRadius"))
        assertTrue(group.contains("if (pressed || index == content.lastIndex) outerRadius else RowInnerRadius"))
    }

    @Test
    fun pageGroupWrapperPinsSheetRadius() {
        val group = source("SettingsGroup.kt")
        assertTrue(group.contains("fun SettingsPageSplitGroup("))
        assertTrue(group.contains("outerRadius = NirikoShapes.Sheet"))
    }

    @Test
    fun fourHighFrequencyPagesUseThePageWrapperEverywhere() {
        val pages = mapOf(
            // 10 → 11：批次 C 新增「详情页标题」分组（C · 13.1 的设置入口）。
            "pages/AppearanceSettingsScreen.kt" to 11,
            "pages/DataSourceSettingsScreen.kt" to 3,
            "pages/SyncBackupSettingsScreen.kt" to 2,
            "pages/RefreshDiagnosticsScreen.kt" to 5,
        )
        for ((file, expected) in pages) {
            val text = source(file)
            assertTrue(
                "$file 缺少 SettingsPageSplitGroup 的 import",
                text.contains("import com.otakup.niriko.ui.settings.SettingsPageSplitGroup"),
            )
            assertEquals(
                "$file 的卡片数量与预期不符（新增/删除分组时同步更新本测试）",
                expected,
                Regex("SettingsPageSplitGroup\\(").findAll(text).count(),
            )
            assertFalse(
                "$file 仍有未统一到 24dp 的分组",
                text.contains("SettingsSplitGroup("),
            )
        }
    }

    @Test
    fun pagesOutsideB7cKeepTheTwentyDpDefault() {
        // B7c 明确只做 4 页；其余页仍在用默认 20dp（Card）——这条断言防止「顺手全换」
        val about = source("pages/AboutSettingsScreen.kt")
        assertTrue(about.contains("SettingsSplitGroup("))
        assertFalse(about.contains("SettingsPageSplitGroup("))
    }
}
