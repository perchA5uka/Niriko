package com.otakup.niriko.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-11 / V-12 / V-14 收口护栏（v1.2.2）。
 *
 * 背景：审计记录同一件事有「五副 chip 面孔」（M3 FilterChip 15 处、AssistChip 7 处、
 * 玻璃 TagChip、8dp 绿描边选集、裸绿字动作）与「三套锚定菜单」（24dp 玻璃 / 20dp 玻璃 /
 * 20dp 普通 DropdownMenu）。本批把它们收敛成 [NirikoTagPill] / [NirikoFilterPill] /
 * [NirikoSelectionPill] / [NirikoActionPill] / [NirikoSegmentedControl] / [NirikoMenu]。
 *
 * 这个测试守「不许退回去」：
 * 1. M3 的 FilterChip / AssistChip / DropdownMenu 在 UI 层**零调用点**；
 * 2. 详情页分享/传送门菜单行仍是 M3 DropdownMenuItem —— 那是**已知且刻意**的残留
 *    （外壳已经是 GlassAnchoredMenu，行内是「标题 + 副标题」双行，NirikoMenuItem 没有副标题槽），
 *    数量锁死，多一处就报错，避免有人顺手再加一层 M3 菜单；
 * 3. 新组件必须真的有消费点（避免「建了组件没人用」这种假收口）。
 *
 * 与仓库既有的源码扫描式契约测试（[com.otakup.niriko.ui.settings.SettingsSurfaceTokenTest]、
 * [com.otakup.niriko.ui.theme.ShapeLiteralPolicyTest]）同一种做法：这类约束用 grep 级别的
 * 断言才守得住，靠 Code Review 记不住。
 */
class ControlLanguagePolicyTest {

    private val uiRoot = File("src/main/java/com/otakup/niriko/ui")

    @Test
    fun `界面层不再出现M3芯片与M3下拉菜单`() {
        val source = uiSources()
        listOf("FilterChip(", "AssistChip(", "DropdownMenu(").forEach { symbol ->
            val hits = occurrences(source, symbol)
            assertEquals("UI 层不该再直接调用 M3 的 $symbol，请用 Niriko 胶囊/菜单组件", 0, hits)
        }
    }

    @Test
    fun `详情页菜单行仍是M3的已知残留且数量锁死`() {
        val hits = mutableMapOf<String, Int>()
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val count = occurrences(stripComments(file.readText()), "DropdownMenuItem(")
            if (count > 0) hits[file.name] = count
        }
        // 外壳已经是 GlassAnchoredMenu，只有「行」还是 M3 的（双行副标题无槽位）。
        assertEquals(mapOf("SubjectDetailScreen.kt" to 5), hits)
    }

    @Test
    fun `收口后的新组件都有真实消费点`() {
        val source = uiSources()
        // 基线 = v1.2.2 收口完成时的调用点（含组件自身定义那一处）。只许增不许减：
        // 减了说明有人把某处又换回了 M3 组件或裸文字。
        val baseline = mapOf(
            "NirikoTagPill(" to 11,
            "NirikoFilterPill(" to 10,
            "NirikoSelectionPill(" to 6,
            "NirikoActionPill(" to 10,
            "NirikoSegmentedControl(" to 3,
            "NirikoMenu(" to 5,
            "NirikoMenuItem(" to 10,
            "NirikoPillButton(" to 2,
            "SectionHeader(" to 10,
        )
        baseline.forEach { (symbol, expected) ->
            val actual = occurrences(source, symbol)
            assertTrue("$symbol 的消费点 $actual < 基线 $expected：有人把它换回 M3 组件或裸文字了", actual >= expected)
        }
    }

    @Test
    fun `可点胶囊的命中区由48dp触控壳承担`() {
        val source = stripComments(File(uiRoot, "components/NirikoChip.kt").readText())
        // 筛选胶囊与动作胶囊：视觉 34dp + 外壳 48dp 命中区。选集网格刻意不套壳（会多出一整行）。
        assertTrue(
            "NirikoFilterPill / NirikoActionPill 必须套 minimumInteractiveComponentSize() 才有 48dp 命中区",
            occurrences(source, "minimumInteractiveComponentSize()") >= 2,
        )
    }

    /** 把所有 UI 源码拼起来统计：跨文件统计更简单，也不怕某个符号被挪到别的文件。 */
    private fun uiSources(): String = buildString {
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.forEach { file ->
            append(stripComments(file.readText()))
            append('\n')
        }
    }

    private fun occurrences(source: String, symbol: String): Int {
        var count = 0
        var index = source.indexOf(symbol)
        while (index >= 0) {
            count++
            index = source.indexOf(symbol, index + symbol.length)
        }
        return count
    }

    /** 剥掉块注释与行尾注释：KDoc 里刻意写着「M3 FilterChip」这种历史说明，那是文档不是调用。 */
    private fun stripComments(text: String): String {
        val noBlock = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '/' && text[i + 1] == '*') {
                val end = text.indexOf("*/", i + 2)
                i = if (end < 0) text.length else end + 2
                noBlock.append('\n')
            } else {
                noBlock.append(text[i])
                i++
            }
        }
        return noBlock.lineSequence().joinToString("\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }
    }
}
