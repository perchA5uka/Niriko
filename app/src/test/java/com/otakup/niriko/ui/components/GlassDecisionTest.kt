package com.otakup.niriko.ui.components

import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.data.settings.GlassEffectLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 玻璃降级判定真值表（纯 JVM 单测，不需要 Robolectric）。
 *
 * 存在理由：原计划用 Roborazzi 截图矩阵做视觉回归（5 档玻璃 × 2 主题 × 2 宽度），但 B6 探针
 * 证明 Robolectric 4.17 的原生 SkSL 编译不了 kyant-backdrop 与本项目自己的 AGSL 着色器，
 * 任何真玻璃界面在 Robolectric 下都会在 DrawBackdropNode.onAttach 阶段抛
 * IllegalArgumentException，45 个矩阵用例 45 全失败（详见 docs/ui-upgrade-plan-2026.md 的 B6
 * 结论）。因此「改坏一处玻璃降级条件必须被测试抓住」这条验收改由本文件承担：
 *   - 生产组件调用 [GlassDecision] 的纯函数（不是影子实现，改生产就会改到被断言的对象）；
 *   - 这里把枚举的全部笛卡尔积逐条钉死，并注明每个组合对应的用户可见后果。
 *
 * 已知的有意不对称（不是 bug，勿「顺手统一」）：
 *   - 卡片：只有 GlassEffectLevel.OFF 关玻璃，REDUCED 与 FULL 等效；
 *   - 区块卡：只有 GlassEffectLevel.FULL 真折射，REDUCED 与 OFF 都降级。
 */
class GlassDecisionTest {

    /** 非 null backdrop 哨兵：真机上是卡片兜底层或详情页封面背景墙。 */
    private val backdrop: Any = Any()

    /** null backdrop：没有可折射的壁纸层 / 封面背景墙。 */
    private val noBackdrop: Any? = null

    private class CardCase(
        val level: CardGlassLevel,
        val isCollection: Boolean,
        val effect: GlassEffectLevel,
        val expected: Boolean,
        val consequence: String,
    ) {
        override fun toString(): String =
            "level=" + level + ", isCollectionCard=" + isCollection +
                ", glassEffect=" + effect + " -> 期望 realEnabled=" + expected +
                "（" + consequence + "）"
    }

    /** 卡片判定真值表（backdrop 非 null 时）：3 档 × 2 卡型 × 3 全局档 = 18 条。 */
    private val cardCases: List<CardCase> = listOf(
        // ── CardGlassLevel.FULL：所有卡片都允许真玻璃 ──
        CardCase(CardGlassLevel.FULL, false, GlassEffectLevel.FULL, true,
            "FULL 档 + 全局 FULL：普通卡也折射壁纸，用户看到真玻璃"),
        CardCase(CardGlassLevel.FULL, true, GlassEffectLevel.FULL, true,
            "FULL 档 + 全局 FULL：收藏卡同样折射壁纸"),
        CardCase(CardGlassLevel.FULL, false, GlassEffectLevel.REDUCED, true,
            "REDUCED 只降壁纸模糊成本，卡片判定不看 REDUCED -> 仍是真玻璃"),
        CardCase(CardGlassLevel.FULL, true, GlassEffectLevel.REDUCED, true,
            "REDUCED + 收藏卡：同样仍是真玻璃"),
        CardCase(CardGlassLevel.FULL, false, GlassEffectLevel.OFF, false,
            "全局关玻璃：即使 FULL 档也必须静态降级（OFF 早判使档位完全失效）"),
        CardCase(CardGlassLevel.FULL, true, GlassEffectLevel.OFF, false,
            "全局关玻璃：收藏卡也必须静态降级"),
        // ── CardGlassLevel.COLLECTION_ONLY：只有收藏卡开玻璃 ──
        CardCase(CardGlassLevel.COLLECTION_ONLY, true, GlassEffectLevel.FULL, true,
            "COLLECTION_ONLY + 收藏卡 + 全局 FULL：真玻璃（这是该档的目标场景）"),
        CardCase(CardGlassLevel.COLLECTION_ONLY, true, GlassEffectLevel.REDUCED, true,
            "COLLECTION_ONLY + 收藏卡 + REDUCED：仍真玻璃（REDUCED 不影响卡片）"),
        CardCase(CardGlassLevel.COLLECTION_ONLY, false, GlassEffectLevel.FULL, false,
            "COLLECTION_ONLY + 普通卡：必须降级，否则等于把该档当成 FULL（省电档失效）"),
        CardCase(CardGlassLevel.COLLECTION_ONLY, false, GlassEffectLevel.REDUCED, false,
            "COLLECTION_ONLY + 普通卡 + REDUCED：降级"),
        CardCase(CardGlassLevel.COLLECTION_ONLY, true, GlassEffectLevel.OFF, false,
            "全局关玻璃：收藏卡也降级"),
        CardCase(CardGlassLevel.COLLECTION_ONLY, false, GlassEffectLevel.OFF, false,
            "全局关玻璃 + 普通卡：降级"),
        // ── CardGlassLevel.OFF：卡片玻璃全关 ──
        CardCase(CardGlassLevel.OFF, false, GlassEffectLevel.FULL, false,
            "卡片档 OFF + 全局 FULL：用户明确关掉卡片玻璃 -> 静态降级"),
        CardCase(CardGlassLevel.OFF, true, GlassEffectLevel.FULL, false,
            "卡片档 OFF：收藏卡也降级（档位优先于卡型）"),
        CardCase(CardGlassLevel.OFF, false, GlassEffectLevel.REDUCED, false,
            "卡片档 OFF + REDUCED：降级"),
        CardCase(CardGlassLevel.OFF, true, GlassEffectLevel.REDUCED, false,
            "卡片档 OFF + REDUCED + 收藏卡：降级"),
        CardCase(CardGlassLevel.OFF, false, GlassEffectLevel.OFF, false,
            "两处都 OFF：降级"),
        CardCase(CardGlassLevel.OFF, true, GlassEffectLevel.OFF, false,
            "两处都 OFF + 收藏卡：降级"),
    )

    /** 逐条核对真值表（backdrop 非 null 的全部 18 种组合）。 */
    @Test
    fun cardTruthTableWithBackdropIsExact() {
        assertEquals("真值表条数应为 3 档 × 2 卡型 × 3 全局档 = 18", 18, cardCases.size)
        for (case in cardCases) {
            val actual = GlassDecision.cardUsesRealGlass(
                cardBackdrop = backdrop,
                glassEffect = case.effect,
                cardGlassLevel = case.level,
                isCollectionCard = case.isCollection,
            )
            assertEquals("卡片玻璃判定不符：" + case, case.expected, actual)
        }
    }

    /** 真值表必须覆盖全部笛卡尔积（新增枚举值时会在此失败，强制补表）。 */
    @Test
    fun cardTruthTableCoversEveryCombination() {
        assertEquals("GlassEffectLevel 新增了档位，请补真值表", 3, GlassEffectLevel.entries.size)
        assertEquals("CardGlassLevel 新增了档位，请补真值表", 3, CardGlassLevel.entries.size)
        val covered = cardCases.map { Triple(it.level, it.isCollection, it.effect) }.toSet()
        assertEquals("真值表应恰好覆盖 18 种组合且不重复", 18, covered.size)
        val full = buildList {
            for (level in CardGlassLevel.entries) {
                for (isCollection in listOf(false, true)) {
                    for (effect in GlassEffectLevel.entries) {
                        add(Triple(level, isCollection, effect))
                    }
                }
            }
        }.toSet()
        assertEquals("真值表漏了组合", full, covered)
    }

    /** backdrop == null 时，无论档位/卡型都必须降级（防止把 backdrop 判空删掉）。 */
    @Test
    fun cardFallsBackWheneverBackdropIsNull() {
        for (case in cardCases) {
            val actual = GlassDecision.cardUsesRealGlass(
                cardBackdrop = noBackdrop,
                glassEffect = case.effect,
                cardGlassLevel = case.level,
                isCollectionCard = case.isCollection,
            )
            assertFalse("无 backdrop 时应一律静态降级：" + case, actual)
        }
    }

    /**
     * 回归守卫：锁死 CardGlassLevel.FULL || (COLLECTION_ONLY && isCollectionCard) 的括号与优先级。
     * 把 || 误写成 &&（或把括号挪成 (FULL || COLLECTION_ONLY) && isCollectionCard）都会在此失败。
     */
    @Test
    fun cardLevelGroupingIsNotSwapped() {
        assertTrue(
            "FULL 档必须对普通卡也开玻璃（写成 (FULL||COLLECTION_ONLY)&&isCollection卡 会在此失败）",
            GlassDecision.cardUsesRealGlass(backdrop, GlassEffectLevel.FULL, CardGlassLevel.FULL, false),
        )
        assertTrue(
            "COLLECTION_ONLY 档必须对收藏卡开玻璃（写成 FULL&&isCollectionCard 会在此失败）",
            GlassDecision.cardUsesRealGlass(backdrop, GlassEffectLevel.FULL, CardGlassLevel.COLLECTION_ONLY, true),
        )
        assertFalse(
            "COLLECTION_ONLY 档不得对普通卡开玻璃",
            GlassDecision.cardUsesRealGlass(backdrop, GlassEffectLevel.FULL, CardGlassLevel.COLLECTION_ONLY, false),
        )
        assertFalse(
            "CardGlassLevel.OFF 档必须降级（哪怕全局 FULL）",
            GlassDecision.cardUsesRealGlass(backdrop, GlassEffectLevel.FULL, CardGlassLevel.OFF, true),
        )
    }

    /** 回归守卫：GlassEffectLevel.OFF 早判必须保留（删掉它会让卡片档重新生效）。 */
    @Test
    fun cardOffDisablesEveryLevel() {
        for (level in CardGlassLevel.entries) {
            for (isCollection in listOf(false, true)) {
                assertFalse(
                    "全局 OFF 时卡片必须降级：level=" + level + ", isCollectionCard=" + isCollection,
                    GlassDecision.cardUsesRealGlass(backdrop, GlassEffectLevel.OFF, level, isCollection),
                )
            }
        }
    }

    /**
     * 区块卡（GlassSectionCard）真值表：backdrop != null && glassEffect == FULL，共 3 × 2 = 6 条。
     * 返回 false 时的用户可见后果：有封面模糊位图 -> BlurredGlassSurface 全幅毛玻璃；
     * 没有 -> 退回不透明静态玻璃（appleGlassCard 静态分支）。
     */
    @Test
    fun sectionBackdropUsableTruthTableIsExact() {
        assertTrue("有封面背景墙 + 全局 FULL：真折射（高斯模糊 + AGSL 圆角折射/色散）",
            GlassDecision.sectionBackdropUsable(backdrop, GlassEffectLevel.FULL))
        assertFalse("全局 REDUCED：省电档必须放弃 AGSL 折射，降级为毛玻璃/静态玻璃",
            GlassDecision.sectionBackdropUsable(backdrop, GlassEffectLevel.REDUCED))
        assertFalse("全局 OFF：必须降级",
            GlassDecision.sectionBackdropUsable(backdrop, GlassEffectLevel.OFF))
        assertFalse("无封面背景墙（详情页 Loading/Error/无封面）：FULL 也要降级",
            GlassDecision.sectionBackdropUsable(noBackdrop, GlassEffectLevel.FULL))
        assertFalse("无封面背景墙 + REDUCED：降级",
            GlassDecision.sectionBackdropUsable(noBackdrop, GlassEffectLevel.REDUCED))
        assertFalse("无封面背景墙 + OFF：降级",
            GlassDecision.sectionBackdropUsable(noBackdrop, GlassEffectLevel.OFF))
    }

    /** 回归守卫：区块卡的 backdrop 判空不得被删（无封面时必须降级，否则会拿 null 去 drawBackdrop）。 */
    @Test
    fun sectionNeverUsableWithoutBackdrop() {
        for (effect in GlassEffectLevel.entries) {
            assertFalse(
                "无 backdrop 时区块卡必须降级：glassEffect=" + effect,
                GlassDecision.sectionBackdropUsable(noBackdrop, effect),
            )
        }
    }

    /**
     * 钉死两处判定的有意差异（REDUCED 档）：卡片仍走真玻璃，区块卡降级。
     * 若有人把两处判定「统一」成一个函数，这条会失败 —— 因为统一必然改变其中一处行为。
     */
    @Test
    fun reducedDivergesBetweenCardAndSection() {
        assertTrue("REDUCED 下卡片仍应真玻璃",
            GlassDecision.cardUsesRealGlass(backdrop, GlassEffectLevel.REDUCED, CardGlassLevel.FULL, false))
        assertFalse("REDUCED 下区块卡应降级",
            GlassDecision.sectionBackdropUsable(backdrop, GlassEffectLevel.REDUCED))
        assertTrue("FULL 档下两处都是真玻璃（除区块卡缺 backdrop 外）",
            GlassDecision.cardUsesRealGlass(backdrop, GlassEffectLevel.FULL, CardGlassLevel.FULL, false))
        assertTrue("FULL 档下区块卡也是真折射",
            GlassDecision.sectionBackdropUsable(backdrop, GlassEffectLevel.FULL))
    }
}
