package com.otakup.niriko.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-13 / V-10 / P2-1：控件来源护栏（v1.2.2）。
 *
 * 这批迁移的成果全是「换了组件」——编译能过、单测能过，但下次有人写页面时
 * 顺手 import 一个 M3 Switch / Slider / RangeSlider 就能悄悄退回去，视觉上又要重新分叉。
 * 与仓库既有的源码契约测试（SettingsSurfaceTokenTest、ShapeLiteralPolicyTest）同一种做法：
 * grep 级别断言才守得住。
 *
 * 判定用「前面不是标识符字符」的边界：GlassSlider( / GlassRangeSlider( /
 * NirikoSegmentedControl( 都含 Slider( 子串，但它们正是替代品，不能误报。
 */
class ControlSourceTest {

    private val uiRoot = File("src/main/java/com/otakup/niriko/ui")

    @Test
    fun 界面层不再直接使用M3开关与滑块() {
        val violations = mutableListOf<String>()
        uiSources().forEach { (path, code) ->
            listOf("Switch(", "Slider(", "RangeSlider(").forEach { symbol ->
                if (occurrences(code, symbol) > 0) {
                    violations += path + " 直接调用了 M3 " + symbol + "（应使用 GlassToggle / GlassSlider / GlassRangeSlider）"
                }
            }
        }
        assertTrue("以下位置又引入了 M3 开关/滑块：" + violations.joinToString("; "), violations.isEmpty())
    }

    @Test
    fun 输入框只有NirikoTextField与NirikoSearchField两副面孔() {
        // V-10 把 37 处 OutlinedTextField 收进 NirikoTextField；P2-1 把设置页手写搜索框收进
        // NirikoSearchField。搜索卡（IosStyleSearchComponent 的沉浸态输入框）刻意保留
        // 自己的 BasicTextField：它要跟假轨道/绿条/锚点共用一处几何，套进通用输入框会拆掉状态机。
        assertEquals(mapOf("components/NirikoTextField.kt" to 1), filesWith("OutlinedTextField("))
        assertEquals(
            "BasicTextField 只允许出现在 NirikoSearchField 与搜索卡的沉浸态输入框",
            mapOf("common/IosStyleSearchComponent.kt" to 1, "components/NirikoSearchField.kt" to 1),
            filesWith("BasicTextField("),
        )
    }

    @Test
    fun 新增控件都有真实消费点() {
        // 「定义了没人用」是本仓最难查的一类问题（GlassSegmentedContainer 曾经 0 调用）。
        val all = uiSources().values.joinToString("\n")
        val expected = listOf(
            "GlassRangeSlider(" to 2,
            "NirikoSearchField(" to 2,
            "animatedRequiredSize(" to 3,
            "NirikoSegmentedControl(" to 3,
        )
        expected.forEach { (symbol, minimum) ->
            val hits = occurrences(all, symbol)
            assertTrue(symbol + " 的消费点只有 " + hits + " 处（下限 " + minimum + "）：建了没用，或有人把它换回了 M3 组件", hits >= minimum)
        }
    }

    /** 扫描 ui 下全部 .kt；剥掉注释，避免把 KDoc 里的历史说明当成调用。 */
    private fun uiSources(): Map<String, String> = uiRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .associate { it.relativeTo(uiRoot).invariantSeparatorsPath to stripComments(it.readText()) }

    private fun filesWith(symbol: String): Map<String, Int> = uiSources().mapNotNull { (path, code) ->
        val count = occurrences(code, symbol)
        if (count == 0) null else path to count
    }.toMap()

    private fun occurrences(source: String, symbol: String): Int {
        var count = 0
        var index = source.indexOf(symbol)
        while (index >= 0) {
            val before = if (index == 0) ' ' else source[index - 1]
            if (!before.isLetterOrDigit() && before != '_') count++
            index = source.indexOf(symbol, index + symbol.length)
        }
        return count
    }

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
