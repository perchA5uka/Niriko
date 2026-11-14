package com.otakup.niriko.ui.animation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP11 第二批护栏：「减少动态效果」的覆盖不许漏回去。
 *
 * 为什么用源码文本断言：这些守卫都是「加一个 if / 换一个 spec」，跑起来看不出差别（除非真机把系统级
 * 减少动画打开），单测是唯一能在 CI 里拦住「新写一个 AnimatedVisibility 忘了降级」的手段。
 * 只断「存在性与数量」，不断具体写法，避免把实现钉死。
 */
class ReduceMotionCoverageTest {

    private val sourceRoot = File("src/main/java/com/otakup/niriko")

    private fun source(relative: String): String {
        val file = File(sourceRoot, relative)
        assertTrue("找不到源码文件：$relative", file.isFile)
        return file.readText()
    }

    @Test
    fun `折叠面板的展开收起全部显式降级到瞬时`() {
        val expect = mapOf(
            "ui/subject/ProviderBindingSection.kt" to 3,
            "ui/subject/TmdbBindingSection.kt" to 3,
            "ui/bilibili/BilibiliSyncScreen.kt" to 1,
            "ui/settings/GrayChannelSettingsSection.kt" to 1,
            "ui/settings/pages/DiagnosticsSections.kt" to 1,
        )
        for ((file, count) in expect) {
            val text = source(file)
            assertEquals("$file 的 expandEnter 消费点数量变了", count, text.split("expandEnter(LocalReduceMotion.current)").size - 1)
            assertEquals("$file 的 collapseExit 消费点数量变了", count, text.split("collapseExit(LocalReduceMotion.current)").size - 1)
        }
    }

    @Test
    fun `chrome与选中态的过渡全部走即时降级`() {
        val detail = source("ui/subject/SubjectDetailScreen.kt")
        // 原来是三处：材质墙渐显、chrome 收起、观看状态分段的颜色过渡。
        // V-11 把观看状态换成了 NirikoSegmentedControl（颜色过渡在组件内部走自己的
        // orSnap(reduceMotion)），详情页只剩两处直接降级点。
        assertEquals("详情页材质渐显 / chrome 收起 两处都该降级", 2, detail.split(".orSnap(LocalReduceMotion.current)").size - 1)
        val search = source("ui/subject/SubjectSearchScreen.kt")
        assertEquals("搜索页内容 inset 切换该降级", 1, search.split(".orSnap(LocalReduceMotion.current)").size - 1)
        for (file in listOf("ui/screens/Screens.kt", "ui/search/SearchResultsPane.kt")) {
            assertEquals("$file 的 animateContentSize 该走有限规格降级", 1, source(file).split(".orSnapFinite(LocalReduceMotion.current)").size - 1)
        }
    }

    @Test
    fun `全仓的AnimatedVisibility都显式声明了降级`() {
        // 判据：调用段里出现 ReduceMotion（大小写不敏感，覆盖 LocalReduceMotion 与形参 reduceMotion）、
        // EnterTransition.None，或 enterTransition（SubjectDetailScreen 的 CollapsibleSourceSection 由局部变量传入）。
        // 目前全仓零白名单：任何新加的 AnimatedVisibility 忘了降级都会在这里变红。
        val allowed = emptyMap<String, Int>()
        val offenders = mutableListOf<String>()
        val root = File(sourceRoot, "ui")
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { file ->
            val text = stripComments(file.readText())
            val relative = file.relativeTo(sourceRoot).invariantSeparatorsPath
            var index = text.indexOf("AnimatedVisibility(")
            var unguarded = 0
            while (index >= 0) {
                val open = index + "AnimatedVisibility".length
                var depth = 0
                var end = open
                while (end < text.length) {
                    when (text[end]) {
                        '(' -> depth++

                        ')' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                    end++
                }
                val call = text.substring(index, minOf(end + 1, text.length))
                val guarded = call.contains("ReduceMotion", ignoreCase = true) || call.contains("EnterTransition.None") || call.contains("enterTransition")
                if (!guarded) unguarded++
                index = text.indexOf("AnimatedVisibility(", index + 1)
            }
            val expected = allowed[relative] ?: 0
            if (unguarded != expected) offenders += "$relative 有 $unguarded 处未降级（白名单 $expected）"
        }
        assertTrue("发现了未接降级的 AnimatedVisibility：\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `降级助手语义保持瞬时`() {
        val specs = source("ui/animation/NirikoMotionSpecs.kt")
        val enterBlock = specs.substringAfter("fun expandEnter(", "")
        val exitBlock = specs.substringAfter("fun collapseExit(", "")
        assertTrue("expandEnter 必须在 reduceMotion 下返回瞬现", enterBlock.contains("EnterTransition.None"))
        assertTrue("collapseExit 必须在 reduceMotion 下返回瞬隐", exitBlock.contains("ExitTransition.None"))
        assertTrue("orSnapFinite 必须真的返回 snap", specs.contains("if (reduceMotion) snap<T>() else this"))
    }

    // 扫源码前先剥掉注释：KDoc 里会写「AnimatedVisibility(fadeIn + slideInHorizontally)」这类说明文字，
    // 不剥掉会把文档当成调用点。
    private fun stripComments(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text.startsWith("/*", i)) {
                val end = text.indexOf("*/", i + 2)
                if (end < 0) break
                i = end + 2
            } else if (text.startsWith("//", i)) {
                val newline = text.indexOf('\n', i)
                i = if (newline < 0) text.length else newline
            } else {
                out.append(text[i])
                i++
            }
        }
        return out.toString()
    }
}
