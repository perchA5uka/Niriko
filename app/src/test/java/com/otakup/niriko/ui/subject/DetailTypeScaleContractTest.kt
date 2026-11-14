package com.otakup.niriko.ui.subject

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-12 / V-15 / V-16 与 B-13 的收口护栏（v1.2.2）。
 *
 * 这几条改造都是「把散落各处的写法统一到共享组件」，如果只靠 Code Review，下一批人
 * 又会顺手写回手抄样式。仓库已有的 [com.otakup.niriko.ui.components.ControlLanguagePolicyTest]
 * 与 [com.otakup.niriko.ui.theme.ShapeLiteralPolicyTest] 都是源码扫描式断言，本测试沿用同一做法：
 *
 * 1. 六个 drill-in 二级页标题必须是 headlineSmall(24/w600)，不许退回 titleLarge(22)；
 * 2. 详情/人物/角色/剧集页不再手写 titleMedium + FontWeight.Bold 的分区标题
 *    （只剩「数值强调」这一类，数量锁死 —— 新增标题会让计数变大而失败）；
 * 3. SectionHeader 必须真的被消费（避免建了组件没人用）；
 * 4. TagChip 已删除，全 UI 不再有代码引用；
 * 5. B-13 的三态切换用单实例 StateHost 包络，不用 AnimatedContent 包 Lazy 正文；
 * 6. B-16 搜索结果/榜单列表逐条 animateItem()。
 */
class DetailTypeScaleContractTest {

    private val mainRoot = File("src/main/java/com/otakup/niriko")
    private val uiRoot = File(mainRoot, "ui")

    /** 本轮写作用域里需要扫描的目录 + 单个文件。 */
    private val scopeDirs = listOf("subject", "person", "character", "episode", "steam", "search", "library")
    private val extraFiles = listOf("screens/Screens.kt")

    @Test
    fun `六个二级页标题统一在 headlineSmall 档`() {
        val pages = listOf(
            "subject/SubjectDetailScreen.kt",
            "person/PersonDetailScreen.kt",
            "character/CharacterDetailScreen.kt",
            "subject/StaffListScreen.kt",
            "episode/EpisodeDetailScreen.kt",
            "steam/SteamLoginScreen.kt",
        )
        pages.forEach { path ->
            val source = stripped(path)
            assertTrue(
                "$path 的 drill-in 页标题必须用 MaterialTheme.typography.headlineSmall（24sp/w600），V-16",
                source.contains("MaterialTheme.typography.headlineSmall"),
            )
        }
    }

    @Test
    fun `分区标题不再手写 titleMedium 加粗`() {
        // 允许剩下的只有「数值强调」：评分数字 / Steam 价格 / MetaCritic / AniList 分 /
        // VNDB 统计值 / 单集权威评分数字 —— 它们是数据，不是分区标题，用 Bold 表达权重。
        // 数量锁死：谁再手抄一处 titleMedium + Bold 的标题，这里立刻变红。
        val allowed = mapOf(
            "subject/ExternalRatingSection.kt" to 1,
            "subject/SubjectDetailScreen.kt" to 4,
            "episode/EpisodeDetailScreen.kt" to 1,
        )
        val actual = mutableMapOf<String, Int>()
        scopeSources().forEach { (path, source) ->
            val hits = Regex("typography\\.titleMedium,\\s*fontWeight = FontWeight\\.Bold").findAll(source).count()
            if (hits > 0) actual[path] = hits
        }
        assertEquals(
            "分区标题必须改走 SectionHeader；新增的手写 titleMedium+Bold 要么改组件，要么在注释里登记为什么是数值强调",
            allowed.toSortedMap(),
            actual.toSortedMap(),
        )
    }

    @Test
    fun `SectionHeader 在详情与二级页有真实消费点`() {
        val total = scopeSources().values.sumOf { Regex("SectionHeader\\(").findAll(it).count() }
        // 基线 = V-15 收口完成时的实测值（42）。只许增不许减：减了说明有人改回手写 Text。
        assertTrue("SectionHeader 消费点 $total 少于 V-15 收口基线 42，有人把分区标题写回去了", total >= 42)
    }

    @Test
    fun `TagChip 已退场且全 UI 无代码引用`() {
        assertFalse(
            "TagChip.kt 应已删除（V-12：标签统一到 NirikoTagPill / NirikoActionPill）",
            File(uiRoot, "components/TagChip.kt").exists(),
        )
        val offenders = walk(uiRoot)
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> stripped(file.relativeTo(uiRoot).invariantSeparatorsPath).contains("TagChip") }
            .map { it.name }
        assertTrue(
            "TagChip 已删除，但仍被这些文件引用：$offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `三态切换用单实例 StateHost 而不是 AnimatedContent`() {
        // 只扫本次接线的屏：Person / Character / 三个搜索面板 / 作品库首屏 / 制作人员列表。
        // StateHost 必须是「外层一个实例按 key 变化播放」——每个分支各包一个不会触发动画（
        // 分支切换换了调用点，实例是新的、previous 恒为 null）。
        val wired = listOf(
            "subject/StaffListScreen.kt",
            "person/PersonDetailScreen.kt",
            "character/CharacterDetailScreen.kt",
            "search/TrendingSection.kt",
            "search/DiscoverPosterPane.kt",
            "search/SearchResultsPane.kt",
            "screens/Screens.kt",
        )
        wired.forEach { path ->
            val source = stripped(path)
            assertEquals(
                "$path 必须用外层单实例 StateHost(stateKey = ...) 包住三态（分支各包一个不会播动画）",
                1,
                Regex("StateHost\\(").findAll(source).count(),
            )
            assertTrue("$path 的 StateHost 必须以 stateKey 命名参数接线", source.contains("StateHost(stateKey ="))
            assertFalse(
                "$path 不得用 AnimatedContent 包正文（过渡期会同时组合两棵 Lazy，踩 02 文档性能红线）",
                source.contains("AnimatedContent("),
            )
        }
    }

    @Test
    fun `搜索结果与榜单列表项逐条入场`() {
        // B-16：逐条 animateItem()，而不是给整块 rowsAlpha 做一次「一块布」淡入。
        listOf(
            "search/SearchResultsPane.kt",
            "search/TrendingSection.kt",
            "search/DiscoverPosterPane.kt",
            "screens/Screens.kt",
        ).forEach { path ->
            assertTrue("$path 的列表项必须使用 animateItem()（B-16）", stripped(path).contains("animateItem()"))
        }
    }

    private fun stripped(path: String): String = stripComments(File(uiRoot, path).readText())

    private fun scopeSources(): Map<String, String> = buildMap {
        scopeDirs.forEach { dir ->
            walk(File(uiRoot, dir))
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.path }
                .forEach { put("$dir/${it.name}", stripComments(it.readText())) }
        }
        extraFiles.forEach { put(it, stripped(it)) }
    }

    private fun walk(dir: File): List<File> {
        val out = mutableListOf<File>()
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) out += walk(file) else out += file
        }
        return out
    }

    /** 剥注释：KDoc 里刻意写着「TagChip / FilterChip」这类历史说明，那是文档不是调用。 */
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
