package com.otakup.niriko.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-02：色值越界护栏（v1.2.2）。
 *
 * 规则：Color(0x…) 只允许出现在「定义颜色的地方」——
 * - ui/theme/ 目录：主题色板 / 语义令牌 / 图表与稀有度派生色；
 * - ui/share/ 目录：导出图是刻意隔离的设计域，不跟随 App 主题，允许自带色值；
 * - ui/components/liquidglass/LiquidGlassTokens.kt：材质物理参数（高光/折射的固定色）。
 *
 * 其余位置的色值必须来自主题或 AccentColors，否则换主题时这些点不会跟着变，
 * 深色/高饱和种子下会发灰或糊掉（V-02 记录的就是「同一枚评分金被三个文件各写一遍」）。
 * 测试用源码文本断言（剥注释），因为仓库没有 Compose 截图测试设施。
 */
class ThemeColorAuditTest {

    private val uiRoot = File("src/main/java/com/otakup/niriko/ui")

    @Test
    fun 色值字面量只允许出现在颜色定义域() {
        val allowedPrefixes = listOf(
            "theme/",
            "share/",
            "components/liquidglass/LiquidGlassTokens.kt",
        )
        val violations = mutableListOf<String>()
        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relative = file.relativeTo(uiRoot).invariantSeparatorsPath
                if (allowedPrefixes.any { relative.startsWith(it) }) return@forEach
                val code = stripComments(file.readText())
                Regex("Color\\(0x[0-9A-Fa-f]+").findAll(code).forEach { match ->
                    violations += relative + ": " + match.value
                }
            }
        assertTrue(
            "以下 UI 色值没有走主题/令牌（换主题不会跟着变）：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun 主题域保留颜色定义() {
        val themeSource = File(uiRoot, "theme/Color.kt").readText()
        assertTrue("Color.kt 不应被误删", themeSource.contains("Color(0x"))
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
