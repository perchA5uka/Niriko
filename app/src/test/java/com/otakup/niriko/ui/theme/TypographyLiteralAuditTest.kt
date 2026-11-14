package com.otakup.niriko.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-03：字号字面量护栏（v1.2.2）。
 *
 * 「正文 / 图例 / 角标」此前有 7 / 8 / 9 / 10sp 一堆手写字号，低于可读下限，且与格宽耦合；
 * 现在字号只允许来自 NirikoTypography 的层级，例外只有两处，都有明确理由：
 * - ui/components/Badges.kt：压在海报上的 10sp 徽标（图上的字要小，已登记为刻意保留）；
 * - ui/share/ 目录：导出图是独立设计域（打印尺寸自定，不跟随 App 排版）。
 *
 * 另外禁止低于 11sp 的字面量出现在其余 UI 里：这是 V-03 的数值下限（labelSmall = 11.5sp），
 * 允许的例外同样只有上面两处。测试剥注释后扫描源码。
 */
class TypographyLiteralAuditTest {

    private val uiRoot = File("src/main/java/com/otakup/niriko/ui")

    private val allowedFiles = setOf(
        "theme/Type.kt",
        "components/Badges.kt",
    )

    @Test
    fun 字号只允许来自排版令牌或两处豁免() {
        val violations = mutableListOf<String>()
        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relative = file.relativeTo(uiRoot).invariantSeparatorsPath
                if (relative.startsWith("share/") || relative in allowedFiles) return@forEach
                val code = stripComments(file.readText())
                Regex("fontSize\\s*=\\s*([0-9.]+)\\.sp").findAll(code).forEach { match ->
                    violations += relative + ": fontSize = " + match.groupValues[1] + ".sp"
                }
            }
        assertTrue(
            "以下位置手写了字号（应改用 MaterialTheme.typography 的层级）：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun 正文最小字号不低于11sp() {
        val violations = mutableListOf<String>()
        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relative = file.relativeTo(uiRoot).invariantSeparatorsPath
                if (relative.startsWith("share/") || relative in allowedFiles) return@forEach
                val code = stripComments(file.readText())
                Regex("fontSize\\s*=\\s*([0-9.]+)\\.sp").findAll(code).forEach { match ->
                    val size = match.groupValues[1].toFloatOrNull() ?: return@forEach
                    if (size < 11f) violations += relative + ": fontSize = " + match.groupValues[1] + ".sp"
                }
            }
        assertTrue(
            "以下文字低于 11sp 可读下限（压图徽标与导出图除外）：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
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
