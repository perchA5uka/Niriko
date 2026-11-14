package com.otakup.niriko.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6：形状字面量护栏（v1.2.2）。
 *
 * 背景：审计 V-01 记录「圆角令牌齐全却被绕开」——`RoundedCornerShape` 字面量 144 处，
 * `NirikoShapes.` 只有 28 处。WP6 把与令牌**取值逐一相等**的字面量换成 Shape 工厂
 * （4/12/16/20/24/999dp），这一步是纯等价替换、不改变任何像素；剩下的字面量要么是
 * 没有对应档位的取值（6/8/10dp 小件、14dp 封面、28dp 搜索玻璃面、50dp dock 胶囊等），
 * 要么是运行时算出来的（`height / 2`、`cardRadius`）。
 *
 * 这个测试守三件事，都是「将来更容易退回去」的方向：
 * 1. 令牌档取值**不许**再以字面量出现（否则令牌又变成双轨）；
 * 2. 剩余字面量只许用已记账的取值，且总数不许超过迁移时的基线；
 * 3. 令牌 Shape 工厂必须真的有消费点（避免「建了令牌没人用」这种假收口）。
 *
 * 注意：本测试会剥掉注释再统计——`NirikoShapes.kt` 的 KDoc 里刻意保留了
 * `RoundedCornerShape(20.dp)` 这类历史口径说明，那是文档不是代码。
 */
class ShapeLiteralPolicyTest {

    private val mainRoot = File("src/main/java/com/otakup/niriko")

    /** 已经有令牌的取值：出现在代码里就是绕过令牌。 */
    private val tokenizedRadii = setOf(4, 12, 16, 20, 24, 999)

    /**
     * 迁移后仍保留的字面量取值 → 允许出现的次数（2026-10 迁移基线）。
     *
     * 每一条都有理由：0dp = 直角（分享卡「不圆角」分支）、2/3dp = 细条与角标、
     * 6/8/10dp = 小控件（没有对应档位，M3 small/medium 之间）、11/14dp = 封面小图与取色器、
     * 28dp = 搜索玻璃面（另有 20/24/999 三档，待单独统一）、48dp = 分享图大圆角、
     * 50dp = dock/tab 胶囊（kyant 移植值，刻意不动）。
     *
     * 2026-10-31 第三次重算（V-13 / V-14 / B-11 批次之后）：垂直选择器触发胶囊 28→Sheet 档（28dp −1），
     * 别处同时收敛了几个 8dp 小件。**这张表是精确相等比较**，后续任一批次再动圆角字面量都要重算。
     */
    private val allowedLiteralCounts = mapOf(
        "0.dp" to 2,
        "2.dp" to 1,
        "3.dp" to 4,
        "6.dp" to 14,
        "8.dp" to 13,
        "10.dp" to 12,
        "11.dp" to 1,
        "14.dp" to 3,
        // 2026-11-14 合并手机端 A1：底栏「传送门」胶囊复用底栏 28dp 玻璃胶囊口径（+1）。
        "28.dp" to 4,
        "48.dp" to 1,
        "50.dp" to 4,
    )

    /**
     * 令牌 Shape 工厂 → 至少应有的消费点数（只增不减）。
     *
     * 2026-10-31 第三次重算（原基线在 v1.2.2 外观四项「分段 / chip / 菜单 / 统计数字」之后）：
     * V-13 / V-14 / B-11 又把手写胶囊与手写卡片收进共享组件（NirikoSegmentedControl / NirikoMenu /
     * GlassRangeSlider），令牌消费点随调用点一起减少，而圆角字面量并没有回流（字面量白名单那条
     * 测试仍逐值卡住）。这条断言只做「不少于下限」，所以随每次收口把下限下调到当前实测值仍能挡住
     * 「令牌建了没人用」的回潮，同时不会因为正常收口而变红。
     */
    private val shapeFactoryUses = mapOf(
        "CardShape" to 12,
        "SectionShape" to 16,
        "SheetShape" to 8,
        "ControlShape" to 16,
        "RowInnerShape" to 14,
        "PillShape" to 19,
        "ChipShape" to 12,
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

    private fun radiusArguments(source: String): List<String> = Regex("RoundedCornerShape\\(([^)]*)\\)").findAll(source).map { it.groupValues[1].trim() }.toList()

    @Test
    fun 令牌档取值不再以字面量出现() {
        val violations = mutableListOf<String>()
        sources().forEach { (path, source) ->
            radiusArguments(source).forEach { argument ->
                val dp = Regex("^(\\d+)\\.dp$").find(argument)?.groupValues?.get(1)?.toIntOrNull()
                if (dp != null && dp in tokenizedRadii) {
                    violations += path + " 里的 RoundedCornerShape(" + argument + ")：请改用 NirikoShapes 的对应 Shape 工厂"
                }
                // 单参 Int 重载 = 百分比圆角；50% 就是胶囊，这里只允许令牌自家定义处出现
                if (argument == "50" && path != "ui/theme/NirikoShapes.kt") {
                    violations += path + " 里的 RoundedCornerShape(50)：请改用 NirikoShapes.PillShape"
                }
            }
        }

        assertTrue(
            "以下位置又绕开了形状令牌：\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun 剩余字面量只许用已记账的取值且不超过基线() {
        val counts = mutableMapOf<String, Int>()
        val unexpected = mutableListOf<String>()
        sources().forEach { (path, source) ->
            radiusArguments(source).forEach { argument ->
                if (!Regex("^\\d+\\.dp$").matches(argument)) return@forEach
                counts[argument] = (counts[argument] ?: 0) + 1
                if (argument !in allowedLiteralCounts) unexpected += path + " 的 RoundedCornerShape(" + argument + ")"
            }
        }

        assertTrue(
            "出现了没记过账的圆角魔数（要么用令牌，要么在本测试白名单里写明理由）：\n" + unexpected.joinToString("\n"),
            unexpected.isEmpty(),
        )
        assertEquals(
            "字面量总数只许减少。实际：" + counts.toSortedMap(),
            allowedLiteralCounts,
            counts.toSortedMap(),
        )
    }

    @Test
    fun 每个令牌Shape工厂都有真实消费点() {
        val allSource = sources().joinToString("\n") { it.second }
        val missing = shapeFactoryUses.filter { (factory, minimum) ->
            Regex("NirikoShapes\\." + factory).findAll(allSource).count() < minimum
        }.keys

        assertTrue(
            "以下令牌 Shape 工厂的消费点比迁移基线少（令牌建了却没人用 = 假收口）：" + missing.joinToString(),
            missing.isEmpty(),
        )
    }
}
