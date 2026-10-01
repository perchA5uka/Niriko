package com.otakup.niriko.ui.components

import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.data.settings.GlassEffectLevel

/**
 * 玻璃降级判定（纯函数，不依赖 Compose / Android，可直接在 JVM 单元测试里穷举真值表）。
 *
 * 为什么单独抽出来：视觉回归（Roborazzi 截图矩阵）在本工程不可行 —— Robolectric 4.17 的原生
 * SkSL 编译不了 kyant-backdrop 与本项目自己的 AGSL 着色器（见 docs/ui-upgrade-plan-2026.md 与
 * B6 探针结论），任何走真玻璃的界面在 Robolectric 下都会在 DrawBackdropNode.onAttach 阶段抛
 * IllegalArgumentException（error: 4: color is not a valid layout qualifier /
 * error: 50: cannot swizzle value of type shader）。因此改由本文件的纯函数承担
 * 「改坏一处玻璃降级条件必须被测试抓住」这条验收：判定逻辑集中在此，生产组件只负责调用它。
 *
 * 本文件只搬运表达式，**不改变任何判定顺序或语义**，两处的逐字来源：
 *   - [cardUsesRealGlass]      ← GlassCard.kt 的 appleGlassCard 内 realEnabled（原 157-158 行）
 *   - [sectionBackdropUsable]  ← GlassSectionCard.kt 的 backdropUsable（原 66 行）
 *
 * 注意两处语义有意不对称（真机上的用户可见后果不同，勿「顺手统一」）：
 *   - 卡片：只把 GlassEffectLevel.OFF 当作「关玻璃」，REDUCED 与 FULL 等效；
 *   - 区块卡：只有 GlassEffectLevel.FULL 才用真折射，REDUCED 与 OFF 都降级。
 */
object GlassDecision {

    /**
     * Modifier.appleGlassCard 是否启用真液态玻璃（kyant backdrop：vibrancy → blur → lens 折射壁纸）。
     *
     * 逐字等价于原内联表达式（GlassCard.kt:157-158）：
     *
     *     cardBackdrop != null && glassEffect != GlassEffectLevel.OFF &&
     *         (glassLevel == CardGlassLevel.FULL ||
     *             (glassLevel == CardGlassLevel.COLLECTION_ONLY && isCollectionCard))
     *
     * 判定顺序也必须保持：先判 backdrop 是否为 null（无壁纸层可折射时无论档位都只能静态降级），
     * 再判 GlassEffectLevel.OFF（关闭玻璃时档位完全失效），最后才是档位 + 是否收藏卡的或组合。
     *
     * @param cardBackdrop 卡片折射源（LocalCardGlassBackdrop）。null 表示当前没有可折射的壁纸层。
     * @param glassEffect LocalGlassEffect，全局玻璃档位。只有 OFF 会关掉卡片玻璃；REDUCED 不影响卡片。
     * @param cardGlassLevel LocalCardGlassLevel，卡片玻璃档位。
     * @param isCollectionCard 当前卡片是否为收藏卡（COLLECTION_ONLY 档只对收藏卡开玻璃）。
     */
    fun <T : Any> cardUsesRealGlass(
        cardBackdrop: T?,
        glassEffect: GlassEffectLevel,
        cardGlassLevel: CardGlassLevel,
        isCollectionCard: Boolean,
    ): Boolean = cardBackdrop != null && glassEffect != GlassEffectLevel.OFF &&
        (cardGlassLevel == CardGlassLevel.FULL ||
            (cardGlassLevel == CardGlassLevel.COLLECTION_ONLY && isCollectionCard))

    /**
     * GlassSectionCard 的 backdrop 是否可用（miuix 真折射：高斯模糊 + AGSL 圆角折射/色散）。
     *
     * 逐字等价于原内联表达式（GlassSectionCard.kt:66）：
     *
     *     backdrop != null && glassEffect == GlassEffectLevel.FULL
     *
     * 返回 false 时组件走降级路径：有封面模糊位图（fallbackBitmap 或 LocalDetailCoverFallback）
     * 就用 BlurredGlassSurface 全幅毛玻璃，否则退回不透明静态玻璃（appleGlassCard 的静态分支）。
     *
     * @param backdrop 区块卡折射源（详情页封面背景墙）。null 表示没有封面背景墙可折射。
     * @param glassEffect LocalGlassEffect。只有 FULL 才折射；REDUCED（省电/低端机档）与 OFF 都降级。
     */
    fun <T : Any> sectionBackdropUsable(
        backdrop: T?,
        glassEffect: GlassEffectLevel,
    ): Boolean = backdrop != null && glassEffect == GlassEffectLevel.FULL
}
