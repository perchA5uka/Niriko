package com.otakup.niriko.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D1：D1 决策"不收材质分层"的护栏（v1.2.2）。
 *
 * 用户明确否决了"按卡片角色拆 L1 瓷 / L2 玻璃"的大改，改为一条更窄的承诺：
 * **不新增 RenderEffect 来源**，并保持同屏实时 RenderEffect ≤ 2。
 *
 * 前者可以在 JVM 里静态守住（本测试）；后者依赖真实绘制时长，只能真机验收，
 * 已在 docs/v1.2.2/06-roadmap.md 的验收矩阵里列为必查项。
 *
 * 为什么用"文件 → 次数"的白名单而不是总数：总数下降是好事（说明有人删了模糊），
 * 但**新增文件**或**在某文件里多开一处**是真正的风险（FULL 档下每多一处 = 一次全屏采样），
 * 两边都要能看见，所以逐文件断言。
 */
class GlassRenderEffectBudgetTest {

    private val mainRoot = File("src/main/java/com/otakup/niriko")

    /**
     * v1.2.2 审计基线：现有 RenderEffect 构造点（`RenderEffect.create*` 调用）。
     *
     * 只允许**减少**，不允许增加；新增必须在 PR 里显式说明为什么同屏预算还够。
     */
    private val budget = mapOf(
        "ui/wallpaper/WallpaperHost.kt" to 2,
        "ui/subject/SubjectDetailScreen.kt" to 1,
        "ui/components/liquidglass/LiquidGlassShader.kt" to 4,
        "ui/components/liquidglass/BlurredCover.kt" to 2,
        "ui/components/GlassSectionCard.kt" to 3,
        "ui/animation/CrtOverlay.kt" to 1,
    )

    private fun countIn(file: File): Int = Regex("RenderEffect\\.create|createBlurEffect|createRuntimeShaderEffect").findAll(file.readText()).count()

    @Test
    fun 没有新增的RenderEffect构造文件() {
        val actual = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(mainRoot).invariantSeparatorsPath to countIn(it) }
            .filter { it.second > 0 }
            .toMap()

        val unexpected = actual.keys - budget.keys
        assertTrue(
            "新增了 RenderEffect 来源：" + unexpected.joinToString() + "。" +
                "FULL 玻璃档下每处都是一次全屏采样，先确认同屏 ≤2 的预算仍然成立再更新本测试的白名单。",
            unexpected.isEmpty(),
        )
        val disappeared = budget.keys - actual.keys
        assertTrue("以下文件的 RenderEffect 构造点消失了，请下调白名单：" + disappeared.joinToString(), disappeared.isEmpty())
    }

    @Test
    fun 每个文件的构造点数量不超过基线() {
        for ((path, expected) in budget) {
            val file = File(mainRoot, path)
            assertTrue("$path 不存在（白名单过期）", file.isFile)
            val actual = countIn(file)
            assertEquals("$path 的 RenderEffect 构造点数量变化，确认预算后更新白名单", expected, actual)
        }
    }

    @Test
    fun 全仓构造点总数不超过13() {
        val total = budget.values.sum()
        assertTrue("RenderEffect 构造点总数 $total 超过 v1.2.2 基线 13", total <= 13)
    }
}
