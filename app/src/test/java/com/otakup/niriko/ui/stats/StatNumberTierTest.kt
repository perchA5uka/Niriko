package com.otakup.niriko.ui.stats

import com.otakup.niriko.ui.components.STAT_LABEL_SIZE_SP
import com.otakup.niriko.ui.components.STAT_NOTE_SIZE_SP
import com.otakup.niriko.ui.components.STAT_NUMBER_SIZE_SP
import com.otakup.niriko.ui.theme.NirikoTypography
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-21：统计数字三档的「唯一来源」护栏。
 *
 * 审计现状是同一个「大数字」用了 20/22/24/26/28/32sp 六种字号（原审计口径五档），
 * 归并方案（原型 ④ 方案 A）只有三档：关键数字 24 / 辅助标签 13 / 注释 11。
 * 三档不是"再定三个数字"，而是必须**等于排版 token** —— 否则 StatNumber 自己就变成
 * 第四套字号来源。所以这里直接断言 StatNumber.kt 里的常量与 [NirikoTypography] 一致，
 * 再反向确认统计页与详情评分区不再绕过组件直接写 titleLarge / headlineMedium 档。
 */
class StatNumberTierTest {

    private val appRoot = File("src/main/java/com/otakup/niriko")

    private fun read(path: String): String = File(appRoot, path).readText()

    /** 只留代码：注释里会引用改造前的字号（"改造前是 headlineMedium(28)"），不剥掉会误报。 */
    private fun stripComments(source: String): String = source.split("\n").joinToString("\n") { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) "" else line.substringBefore("//")
    }

    @Test
    fun `三档字号等于排版token`() {
        assertEquals("关键数字必须是 headlineSmall 的 24sp", 24f, STAT_NUMBER_SIZE_SP)
        assertEquals("辅助标签必须是 labelMedium 的 13sp", 13f, STAT_LABEL_SIZE_SP)
        assertEquals("注释必须是 labelSmall 的 11.5sp", 11.5f, STAT_NOTE_SIZE_SP)

        assertEquals(STAT_NUMBER_SIZE_SP, NirikoTypography.headlineSmall.fontSize.value)
        assertEquals(STAT_LABEL_SIZE_SP, NirikoTypography.labelMedium.fontSize.value)
        assertEquals(STAT_NOTE_SIZE_SP, NirikoTypography.labelSmall.fontSize.value)
    }

    @Test
    fun `StatNumber自带tnum而标签不带`() {
        val source = stripComments(read("ui/components/StatNumber.kt"))
        // tnum 只给数值：数字刷新时宽度不变，整行不跳；标签加 tnum 反而破坏字形节奏。
        assertTrue(source.contains("fontFeatureSettings = \"tnum\""))
        assertEquals(1, Regex("fontFeatureSettings").findAll(source).count())
    }

    @Test
    fun `统计页大数字必须走StatNumber`() {
        val overview = stripComments(read("ui/stats/StatsOverviewSection.kt"))
        // 总作品数 / 均分 / 完成率 = 3 个直接消费点；四张状态卡改为 StatTile（内部再用 StatNumber）。
        assertTrue(
            "StatsOverviewSection 的统计数值应全部走 StatNumber/StatTile，实际只有 " +
                Regex("StatNumber\\(").findAll(overview).count() + " 处",
            Regex("StatNumber\\(").findAll(overview).count() >= 3,
        )
        assertTrue("四张状态卡必须走 StatTile（G-2：不留零调用组件）", overview.contains("StatTile("))
        assertFalse("统计概览不应再直接写 headlineLarge(32)", overview.contains("headlineLarge"))
        assertFalse("统计概览不应再直接写 titleLarge(22)", overview.contains("titleLarge"))

        val statsScreen = stripComments(read("ui/stats/StatsScreen.kt"))
        assertTrue("年度总结的四个数值也走 StatNumber", statsScreen.contains("StatNumber(value)"))
        assertTrue("年份用 StatNumber 的 unit 参数（24sp + 13sp 年），不再拼进字符串", statsScreen.contains("unit = \"年\""))
        assertFalse("统计页不应再出现 titleLarge(22sp) 数值档（五档残留归零）", statsScreen.contains("titleLarge"))

        val donut = stripComments(read("ui/stats/NirikoDonutChart.kt"))
        assertTrue(donut.contains("StatNumber(totalText)"))
        assertFalse("环心总数不应再是 headlineMedium(28)", donut.contains("headlineMedium"))
    }

    @Test
    fun `详情评分区大数字必须走StatNumber`() {
        val statCard = stripComments(read("ui/subject/DetailStatCard.kt"))
        assertTrue(statCard.contains("StatNumber(value)"))
        assertFalse("详情统计卡不应再自己写 headlineMedium", statCard.contains("headlineMedium"))

        val comparison = stripComments(read("ui/subject/RatingComparisonSection.kt"))
        assertTrue(comparison.contains("StatNumber("))
        assertTrue("我的评分是主指标，应使用主色语气", comparison.contains("StatNumberTone.Primary"))
        assertFalse("评分对比不应再自己写 headlineSmall", comparison.contains("headlineSmall"))
    }
}
