package com.otakup.niriko.ui.animation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP9 / B-10：动效字面量护栏（v1.2.2 第六批）。
 *
 * 背景：审计 A.8 记录「21 处手写 spring + 20 处手写 tween 完全绕过 NirikoMotionSpecs」，
 * 于是同一个「手感」在不同控件里各写一份数字，改一处不会带动另一处，
 * 最终没人说得清「设置行按下的弹簧」到底是哪一档。
 *
 * 这一批只做**等值收口**（时长一个毫秒都不改）：
 * - 顶栏 / 底栏 / 详情 chrome 的 220ms、180ms 改为 [NirikoMotionSpecs.CHROME_HIDE_DURATION_MS]、
 *   [NirikoMotionSpecs.DETAIL_CHROME_DURATION_MS]（这两个常量在 WP0 就已定义，此前只有定义没有消费点）；
 * - `animateContentSize` 的 300ms 改为旧令牌 `AnimDurationNormal`。
 *
 * 测试守四件事：
 * 1. 不许再新增「手写 spring」——弹簧只允许出现在已记账的 11 个文件里（六档令牌本体、
 *    kyant 底栏移植值、既有的几个按压/高光），且次数不许超过基线；
 * 2. 时长字面量只许保留已记账的组合，次数不许超过基线（回归成 `tween(220)` 会立刻变红）；
 * 3. chrome 时长必须真的走令牌（三个 220 站点 + 两个 180 站点）；
 * 4. 令牌取值与本次收口时一致（防止有人悄悄把 220 改成 300 还说「只是重命名」）。
 *
 * 注意：本测试剥掉注释再统计——令牌文件的 KDoc 里会引用具体数值，那是文档不是代码。
 */
class MotionLiteralPolicyTest {

    private val mainRoot = File("src/main/java/com/otakup/niriko")

    /**
     * 仍允许出现 `spring(` 的文件 → 基线次数（2026-10 收口基线，只减不增）。
     *
     * B-11 / V-13 收口后只剩两组合法出处：
     * - `NirikoMotionSpecs`：六档令牌本体（逐字复刻 material3 StandardMotionTokens）；
     * - `ui/bottombar/` 目录：kyant 底栏与竖向 dock 的 1:1 上游移植值（明令「只登记不改数值」）。
     * 业务文件里的手写弹簧（搜索卡 2、筛选面板 8、玻璃开关/滑块/高光、回顶 380/0.9 …）
     * 已全部改走六档 / `NirikoPressSpecs`，本表只减不增。
     */
    private val allowedSpringCounts = mapOf(
        "ui/animation/NirikoMotionSpecs.kt" to 7,
        "ui/bottombar/LiquidBottomTabs.kt" to 2,
        "ui/bottombar/LiquidVerticalDock.kt" to 2,
        "ui/bottombar/utils/DampedDragAnimation.kt" to 5,
        "ui/bottombar/utils/InteractiveHighlight.kt" to 2,
    )

    /**
     * 仍允许的 `tween(数字)` 组合 → 基线次数（格式：`文件 :: 毫秒`）。
     *
     * B-11 把搜索挡板的一整组 140–260ms 全部换成 `effectsFast` / `spatialFast` / `spatialDefault`，
     * 于是只剩两处「不是控件手感」的时长：
     * - 卡面稀有度 3000ms 双段：特效节奏；
     * - 详情页背景墙 350ms 材质渐显：刻意保留（Apple §12 Materialize）。
     */
    private val allowedTweenLiterals = mapOf(
        // 卡面稀有度 3000ms 双段：特效节奏，不是控件手感（B-11 之外的刻意保留）
        "ui/components/CardRarityMaterial.kt :: 3000" to 2,
        // 详情页背景墙 350ms 材质渐显：刻意保留（Apple §12 Materialize，需设计决定才能动）
        "ui/subject/SubjectDetailScreen.kt :: 350" to 1,
    )

    /** chrome 令牌的消费点数量（只增不减：新增 chrome 也必须走令牌）。 */
    private val chromeTokenUses = mapOf(
        "CHROME_HIDE_DURATION_MS" to 3,
        "DETAIL_CHROME_DURATION_MS" to 2,
    )

    private fun sources(): List<Pair<String, String>> = mainRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(mainRoot).invariantSeparatorsPath to stripComments(it.readText()) }
        .sortedBy { it.first }
        .toList()

    /** 只留代码：整行注释/KDoc 行直接丢，行尾 `//` 之后丢掉。 */
    private fun stripComments(source: String): String = source.split("\n").joinToString("\n") { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) "" else line.substringBefore("//")
    }

    private fun springCounts(): Map<String, Int> = sources().mapNotNull { (path, source) ->
        val count = Regex("spring\\(").findAll(source).count()
        if (count == 0) null else path to count
    }.toMap()

    private fun tweenLiterals(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        sources().forEach { (path, source) ->
            Regex("tween(?:<[^>]*>)?\\(\\s*(?:durationMillis\\s*=\\s*)?([0-9]+)\\b").findAll(source).forEach { match ->
                val key = path + " :: " + match.groupValues[1]
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts
    }

    private val allSource = sources().joinToString("\n") { it.second }

    @Test
    fun 不再新增手写弹簧() {
        val violations = springCounts().flatMap { (path, count) ->
            val baseline = allowedSpringCounts[path] ?: 0
            if (count > baseline) {
                listOf(path + " 有 " + count + " 处手写 spring（基线 " + baseline + "）；新的弹簧请走 NirikoMotionSpecs 六档或 NirikoPressSpecs")
            } else {
                emptyList()
            }
        }

        assertTrue(
            "以下位置新增了手写弹簧（每个手写数字都在制造第 N 套手感）：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun 时长字面量不许超过记账基线() {
        val violations = tweenLiterals().flatMap { (key, count) ->
            val baseline = allowedTweenLiterals[key] ?: 0
            if (count > baseline) {
                listOf(key.replace(" :: ", " 的 ") + "ms 字面量有 " + count + " 处（基线 " + baseline + "）")
            } else {
                emptyList()
            }
        }

        assertTrue(
            "以下位置新增了手写时长（chrome 走 CHROME_HIDE_DURATION_MS / DETAIL_CHROME_DURATION_MS，其余走六档或旧令牌）：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun chrome时长必须走令牌() {
        val actual = chromeTokenUses.keys.associateWith { name ->
            Regex("NirikoMotionSpecs\\." + name).findAll(allSource).count()
        }

        assertEquals(
            "chrome 令牌消费点数量与收口时不一致（改动请同步本表）",
            chromeTokenUses,
            actual,
        )
    }

    @Test
    fun 令牌取值与收口时一致() {
        val specs = File(mainRoot, "ui/animation/NirikoMotionSpecs.kt").readText()

        assertTrue("CHROME_HIDE_DURATION_MS 取值变了（本轮是等值收口，取值必须仍是 220）", specs.contains("const val CHROME_HIDE_DURATION_MS = 220"))
        assertTrue("DETAIL_CHROME_DURATION_MS 取值变了（本轮是等值收口，取值必须仍是 180）", specs.contains("const val DETAIL_CHROME_DURATION_MS = 180"))
        assertTrue("COVER_IMAGE_CROSSFADE_MS 取值变了（150ms 封面淡入已定稿）", specs.contains("const val COVER_IMAGE_CROSSFADE_MS = 150"))
    }
}
