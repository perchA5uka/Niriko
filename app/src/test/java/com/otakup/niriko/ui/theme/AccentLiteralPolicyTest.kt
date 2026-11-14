package com.otakup.niriko.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-02：强调色字面量护栏（v1.2.2 第五批）。
 *
 * 背景：审计 V-02 记录「Color(0x…) 39 处越界，金色三写、日历四色两写」——
 * 同一个语义色散落在多个文件里，改一处忘一处就会分叉：比如日历的「放送中」金色
 * 同时写在 `CalendarCard.kt`（棋盘格角标）与 `CalendarDaySheet.kt`（当日列表状态文字），
 * 只要有人改单边，同一天在两处就显示成不同颜色。
 *
 * 这一批只做**等值搬迁**（零像素变化）：把 11 个跨文件重复的强调色收进
 * `ui/theme/AccentColors.kt` 的 `NirikoAccents` / `CalendarAccents`，其余位置改引用令牌。
 *
 * 测试守两件事：
 * 1. 这 11 个 0x 只允许出现在令牌文件（以及主题 palette 文件里本来就有的同值条目，
 *    那是主题色板的合法出处，不是「语义色重复」）；
 * 2. 令牌必须真的被消费（每个至少一个引用点），避免建了令牌没人用。
 */
class AccentLiteralPolicyTest {

    private val mainRoot = File("src/main/java/com/otakup/niriko")

    /** 收口后的强调色 → 唯一合法出处（8 位 ARGB，含 alpha 前缀）。 */
    private val tokenHexes = mapOf(
        "FFF59E0B" to "RatingGold",
        "FF3B82F6" to "EpisodeBlue",
        "FFDC2626" to "FavoriteRed",
        "FF2E7D32" to "PeopleGreen",
        "FF22C55E" to "StatusCompleted",
        "FF9CA3AF" to "StatusNeutral",
        "FF8B5CF6" to "StatusOnHold",
        "FFFDD835" to "CalendarAccents.Broadcast",
        "FF66BB6A" to "CalendarAccents.Release",
        "FF90A4AE" to "CalendarAccents.Hiatus",
        "FFFB8C00" to "CalendarAccents.Delay",
    )

    /**
     * 允许出现被收口 0x 的文件。
     *
     * - `AccentColors.kt`：令牌本体，每个值必须恰好出现一次；
     * - `Color.kt` / `StatusColors.kt`：主题色板与状态语义色表，本来就按调色板列出同值条目
     *   （0xFF2E7D32 = GreenPrimaryLight、0xFFDC2626 / 0xFF90A4AE = 状态色），
     *   把它们也算「重复」会把主题色板逼成引用语义令牌，反而错位。
     */
    private val allowedHomes = setOf(
        "ui/theme/AccentColors.kt",
        "ui/theme/Color.kt",
        "ui/theme/StatusColors.kt",
    )

    private fun sources(): List<Pair<String, String>> = mainRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(mainRoot).invariantSeparatorsPath to stripComments(it.readText()) }
        .sortedBy { it.first }
        .toList()

    /** 只留代码：整行注释/KDoc 行直接丢，行尾 `//` 之后丢掉。 */
    private fun stripComments(source: String): String = source.split("\n").joinToString("\n") { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
            ""
        } else {
            line.substringBefore("//")
        }
    }

    private fun hexOccurrences(): Map<String, MutableList<String>> {
        val hits = mutableMapOf<String, MutableList<String>>()
        sources().forEach { (path, source) ->
            Regex("Color\\(0x([0-9A-Fa-f]{8})\\)").findAll(source).forEach { match ->
                val hex = match.groupValues[1].uppercase()
                if (hex in tokenHexes) hits.getOrPut(hex) { mutableListOf() }.add(path)
            }
        }
        return hits
    }

    @Test
    fun 强调色0x只允许出现在令牌文件里() {
        val violations = mutableListOf<String>()
        hexOccurrences().forEach { (hex, paths) ->
            paths.filter { it !in allowedHomes }.forEach { path ->
                violations += path + " 直接写了 0x" + hex + "，请改用 NirikoAccents / CalendarAccents 的 " + tokenHexes[hex]
            }
        }

        assertTrue(
            "以下位置又把强调色写回了字面量（同一个语义色多写一份 = 迟早分叉）：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun 令牌文件里每个强调色恰好定义一次() {
        val hits = hexOccurrences()
        val missing = tokenHexes.keys.filter { hex -> hits[hex]?.count { it == "ui/theme/AccentColors.kt" } != 1 }
        assertTrue("令牌文件里这些 0x 不是恰好一次：" + missing.joinToString(), missing.isEmpty())
    }

    @Test
    fun 每个强调色令牌都有真实消费点() {
        val allSource = sources().joinToString("\n") { it.second }
        val unused = tokenHexes.values.filter { token ->
            // CalendarAccents.X 与 NirikoAccents.X 都按「最后一个点之后的名字」核消费：
            // 定义处 1 次 + 至少 1 个引用点 = 2 次才算真的接上了
            val name = token.substringAfterLast(".")
            Regex("\\.?" + name + "\\b").findAll(allSource).count() < 2
        }

        assertTrue(
            "以下强调色令牌只有一个引用点（含定义本身），说明建了没用：" + unused.joinToString(),
            unused.isEmpty(),
        )
    }

    @Test
    fun 令牌出处表与令牌文件一致() {
        val source = File(mainRoot, "ui/theme/AccentColors.kt").readText()
        val defined = Regex("val ([A-Za-z]+) = Color\\(0x([0-9A-Fa-f]{8})\\)").findAll(source)
            .associate { it.groupValues[2].uppercase() to it.groupValues[1] }
        assertEquals(
            "AccentColors.kt 里的 0x 与测试记账表不一致（新增/删除强调色时请同步本表）",
            tokenHexes.keys.sorted(),
            defined.keys.sorted(),
        )
    }
}
