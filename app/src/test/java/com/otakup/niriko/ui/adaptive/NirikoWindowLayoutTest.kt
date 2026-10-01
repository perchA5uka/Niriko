package com.otakup.niriko.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B2 / 5a：窗口尺寸类别 → 布局档位 / 网格列数的纯函数测试。
 *
 * 输入用 Int 档位常量（0=COMPACT / 1=MEDIUM / 2=EXPANDED），与 androidx 库类型解耦，
 * 纯 JVM 可跑（桥接层 [currentNirikoWindowLayout] 只做库类型 → Int 的降档）。
 */
class NirikoWindowLayoutTest {

    private val wCompact = NirikoWindowLayout.WIDTH_COMPACT
    private val wMedium = NirikoWindowLayout.WIDTH_MEDIUM
    private val wExpanded = NirikoWindowLayout.WIDTH_EXPANDED
    private val hCompact = NirikoWindowLayout.HEIGHT_COMPACT
    private val hMedium = NirikoWindowLayout.HEIGHT_MEDIUM
    private val hExpanded = NirikoWindowLayout.HEIGHT_EXPANDED

    // ==================== NirikoWindowLayout.from() ====================

    @Test
    fun phonePortraitIsCompact() {
        // 360x800dp：宽度 COMPACT，高度 EXPANDED
        assertEquals(
            NirikoWindowLayout.COMPACT,
            NirikoWindowLayout.from(wCompact, hExpanded),
        )
    }

    @Test
    fun phoneLandscapeIsMediumNotDock() {
        // 891x412dp：宽度已是 EXPANDED，但高度 COMPACT（放不下竖排 dock）→ 保持胶囊底栏
        assertEquals(
            NirikoWindowLayout.MEDIUM,
            NirikoWindowLayout.from(wExpanded, hCompact),
        )
    }

    @Test
    fun smallPhoneLandscapeIsMedium() {
        // 780x360dp：宽度 MEDIUM，高度 COMPACT
        assertEquals(
            NirikoWindowLayout.MEDIUM,
            NirikoWindowLayout.from(wMedium, hCompact),
        )
    }

    @Test
    fun smallTabletIsMedium() {
        // 700x1100dp（小平板竖屏）：宽度 MEDIUM
        assertEquals(
            NirikoWindowLayout.MEDIUM,
            NirikoWindowLayout.from(wMedium, hExpanded),
        )
    }

    @Test
    fun largeTabletIsExpanded() {
        // 1280x800dp（大平板横屏）：宽度 EXPANDED，高度 MEDIUM
        assertEquals(
            NirikoWindowLayout.EXPANDED,
            NirikoWindowLayout.from(wExpanded, hMedium),
        )
    }

    @Test
    fun largeTabletPortraitIsExpanded() {
        // 1000x1400dp
        assertEquals(
            NirikoWindowLayout.EXPANDED,
            NirikoWindowLayout.from(wExpanded, hExpanded),
        )
    }

    @Test
    fun unfoldedFoldableIsExpanded() {
        // 841x701dp（折叠屏展开，刚过 840dp 断点）
        assertEquals(
            NirikoWindowLayout.EXPANDED,
            NirikoWindowLayout.from(wExpanded, hMedium),
        )
    }

    @Test
    fun foldedFoldableIsCompact() {
        // 412x914dp（折叠屏折叠态，只有外屏宽度）
        assertEquals(
            NirikoWindowLayout.COMPACT,
            NirikoWindowLayout.from(wCompact, hExpanded),
        )
    }

    @Test
    fun mediumWidthBoundaryDoesNotBecomeExpanded() {
        // 839dp 宽仍是 MEDIUM；840dp 才进入 EXPANDED（断点由库常量保证）
        assertEquals(
            NirikoWindowLayout.MEDIUM,
            NirikoWindowLayout.from(wMedium, hMedium),
        )
        assertEquals(
            NirikoWindowLayout.EXPANDED,
            NirikoWindowLayout.from(wExpanded, hMedium),
        )
    }

    @Test
    fun invalidSizeClassesFallBackToCompact() {
        assertEquals(NirikoWindowLayout.COMPACT, NirikoWindowLayout.from(-1, -1))
        assertEquals(NirikoWindowLayout.COMPACT, NirikoWindowLayout.from(0, 0))
    }

    // ==================== 网格列数映射 ====================

    @Test
    fun gridColumnsForCompactMatchesLegacyFixedThree() {
        // 回归零变化硬要求：手机端现状是 GridCells.Fixed(3)，不得改变
        assertEquals(3, NirikoGridColumns.fromLayout(NirikoWindowLayout.COMPACT))
        assertEquals(3, NirikoGridColumns.fromWidthSizeClass(wCompact))
    }

    @Test
    fun gridColumnsForMediumAndExpanded() {
        assertEquals(4, NirikoGridColumns.fromLayout(NirikoWindowLayout.MEDIUM))
        assertEquals(4, NirikoGridColumns.fromWidthSizeClass(wMedium))
        assertEquals(6, NirikoGridColumns.fromLayout(NirikoWindowLayout.EXPANDED))
        assertEquals(6, NirikoGridColumns.fromWidthSizeClass(wExpanded))
    }

    @Test
    fun gridColumnsInvalidWidthFallsBackToLegacy() {
        assertEquals(3, NirikoGridColumns.fromWidthSizeClass(-1))
    }
}
