package com.otakup.niriko.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Niriko 27 · S0：设计债基线护栏（docs/design-language-plan-2026-11-niriko27.md §7 第 2 条）。
 *
 * 做法沿用 ShapeLiteralPolicyTest：剥掉注释后扫源码，**精确相等**地钉住存量计数。
 * 清理一批就把对应基线调小，同一提交里改；计数变大说明有人又绕开了母版。
 *
 * 字号字面量不在这里：它已经有更严的 TypographyLiteralAuditTest（除 Type.kt、Badges.kt 与
 * share/ 导出图域外一律 0），重复建一条只会让两处各说各话。
 *
 * 基线采集于 2026-11-22（HEAD 4da85c0），口径与本文件的扫描逻辑逐字一致。
 */
class DesignDebtPolicyTest {

    private val uiRoot = File("src/main/java/com/otakup/niriko/ui")

    /**
     * 间距栅格外的 spacedBy 取值 → 次数（栅格见 [NirikoSpacing.gridDp]，0 算合法）。
     * 10dp 与 6dp 是存量大头，S2 按上下文分别迁到 8 / 12 与 4 / 8。
     */
    private val offGridSpacedByBaseline = mapOf(
        "3" to 2,
        "6" to 27,
        "10" to 38,
    )

    /** colorScheme.surfaceVariant 的使用次数：M3 1.4 已弃用，S3 收敛到 surfaceContainer 系。 */
    private val surfaceVariantBaseline = 49

    /** Material AlertDialog 的调用次数：S3 换成 NirikoDialog（材质层级 Modal）。 */
    private val alertDialogBaseline = 36

    private fun sources(excludeShare: Boolean): List<Pair<String, String>> = uiRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(uiRoot).invariantSeparatorsPath to stripComments(it.readText()) }
        .filter { !(excludeShare && it.first.startsWith("share/")) }
        // design/ 是「现状组件对照页」：故意摆着 AlertDialog、M3 按钮这些存量写法，不算新增债务
        .filter { !it.first.startsWith("design/") }
        .sortedBy { it.first }
        .toList()

    /** 只留代码：整行注释 / KDoc 行直接丢，行尾 「//」 之后丢掉。 */
    private fun stripComments(source: String): String = source.split("\n").joinToString("\n") { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) "" else line.substringBefore("//")
    }

    @Test
    fun spacedBy的非栅格取值只许减少() {
        // share/ 是导出图域（固定像素位图，不跟随 App 间距），与字号护栏同一口径豁免
        val actual = mutableMapOf<String, Int>()
        sources(excludeShare = true).forEach { (_, code) ->
            Regex("spacedBy\\(\\s*([0-9.]+)\\.dp").findAll(code).forEach { match ->
                val value = match.groupValues[1]
                val dp = value.toFloatOrNull() ?: return@forEach
                if (!NirikoSpacing.isOnGrid(dp)) actual[value] = (actual[value] ?: 0) + 1
            }
        }
        assertEquals(
            "非栅格 spacedBy 的计数变了。实际：" + actual.toSortedMap() + "；清理后请同一提交内调小基线，变大说明绕开了间距令牌",
            offGridSpacedByBaseline,
            actual.toSortedMap(),
        )
    }

    @Test
    fun surfaceVariant的使用只许减少() {
        val hits = sources(excludeShare = false).sumOf { (_, code) -> Regex("\\.surfaceVariant\\b").findAll(code).count() }
        assertEquals("colorScheme.surfaceVariant 的计数变了（M3 已弃用，新代码请用 surfaceContainer 系）", surfaceVariantBaseline, hits)
    }

    @Test
    fun AlertDialog的调用只许减少() {
        val hits = sources(excludeShare = false).sumOf { (_, code) -> Regex("\\bAlertDialog\\(").findAll(code).count() }
        assertEquals("AlertDialog 的调用计数变了（新弹窗请等 NirikoDialog，S3 落地）", alertDialogBaseline, hits)
    }
}
