package com.otakup.niriko.ui.stats

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-03 / P2-5：统计页的字号与标题字重护栏。
 *
 * 背景：审计 V-03 记录统计页与日历大量出现 7 / 8 / 9 / 10sp 字面量（日历角标、今日卡、
 * 日期条），它们绕过 [com.otakup.niriko.ui.theme.NirikoTypography]，低于正文下限，
 * 而且与格宽耦合——系统字体放大后只有这几处不跟着变。P2-5 则记录一级页标题里只有统计页
 * 手写 fontWeight = Bold（设置页走 token 的 w600），两个一级页标题一粗一细。
 *
 * 这个测试用源码扫描而不是截图：字号/字重回退在真机上要逐屏看，在源码里是确定性文本。
 */
class StatsTypographyContractTest {

    private val mainRoot = File("src/main/java/com/otakup/niriko/ui/stats")

    private fun sources(): List<Pair<String, String>> = mainRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(mainRoot).invariantSeparatorsPath to stripComments(it.readText()) }
        .sortedBy { it.first }
        .toList()

    /** 只留代码：整行注释/KDoc 行直接丢，行尾 `//` 之后丢掉（注释里会引用历史字号）。 */
    private fun stripComments(source: String): String = source.split("\n").joinToString("\n") { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) "" else line.substringBefore("//")
    }

    @Test
    fun `ui_stats下不允许数字字号字面量`() {
        // 允许的行为只有「走 MaterialTheme.typography」；TagWordCloud 那种
        // fontSize = fontSize.sp（变量）是词云的尺寸编码，不是字面量，正则会放行。
        val pattern = Regex("fontSize\\s*=\\s*[0-9]+(?:\\.[0-9]+)?f?\\.sp")
        val violations = sources().flatMap { (path, source) ->
            pattern.findAll(source).map { path + " -> " + it.value }.toList()
        }
        assertTrue(
            "以下位置又写死了字号（应走 labelSmall / labelMedium 等 token）：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `统计页标题不再手写Bold覆盖token`() {
        // P2-5：headlineMedium 本身是 28sp/w600，调用点再叠 Bold 就和设置页标题不同粗细。
        val statsScreen = stripComments(File(mainRoot, "StatsScreen.kt").readText())
        val stackedBold = Regex("headlineMedium\\s*,\\s*\\n\\s*fontWeight\\s*=\\s*FontWeight\\.Bold")
        assertFalse(
            "StatsScreen 的 headlineMedium 又叠了 FontWeight.Bold（P2-5 回退）",
            stackedBold.containsMatchIn(statsScreen),
        )
    }

    @Test
    fun `ui_stats下不允许硬编码色值`() {
        // V-02 在本目录的收口结果：强调色（评分金/集数蓝/日历四色）全部收进
        // ui/theme/AccentColors.kt，ui/stats 内不允许再出现 Color(0x…) —— 语义色只允许
        // 一处定义（AccentLiteralPolicyTest 在主题层已有全局口径，这里锁本目录的回归）。
        val violations = sources().flatMap { (path, source) ->
            Regex("Color\\(0x").findAll(source).map { path + " -> " + it.value }.toList()
        }
        assertTrue(
            "ui/stats 下出现了硬编码色值（应收进 ui/theme/AccentColors.kt 或改用主题色）：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `日历角标改成badge不靠字号区分`() {
        val card = stripComments(File(mainRoot, "CalendarCard.kt").readText())
        // 日期数字、跨月「续」、停/延角标、溢出「+N」都必须是 labelSmall（11.5sp）档，
        // 而不是各自 7/8/9/10sp。断言「这些文案附近出现 labelSmall」太脆弱，
        // 这里用整体口径：日历卡片里不允许出现 7-10sp（上面的字面量扫描已覆盖），
        // 同时确认角标已改成 badge（有实底背景），不靠字号区分。
        assertTrue("停播/延期角标应改成实底 badge", card.contains("background(badgeColor)"))
    }
}
