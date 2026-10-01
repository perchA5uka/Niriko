package com.otakup.niriko.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2b：折痕 → 分栏策略的纯函数单测（风格对齐 [NirikoWindowLayoutTest]）。
 *
 * 折痕判定与窗口尺寸类别**正交**：这里只测折痕本身（[NirikoFoldStrategy] 与
 * [nirikoDetailUsesTwoPanes]），竖向 dock 的判定在 NirikoWindowLayoutTest。
 * 所有入参都是自带单位约定的 Float（生产环境用 dp），不构造任何 Android 类型。
 */
class NirikoFoldStrategyTest {

    // 常见形态的窗口宽度（dp）
    private val phonePortraitWidth = 411f // 手机竖
    private val landscapePhoneWidth = 891f // 手机横（宽度够，但高度不到 EXPANDED 的 480dp）
    private val smallTabletWidth = 800f // 小平板（竖）
    private val largeTabletWidth = 1024f // 大平板
    private val foldableUnfoldedWidth = 884f // 折叠屏展开（内屏）
    private val foldableFoldedWidth = 374f // 折叠屏合起（外屏）

    /** 竖直折痕：书本式展开 / 双屏，位于窗口正中。 */
    private fun verticalFold(windowWidth: Float) = NirikoFoldBounds(
        left = windowWidth / 2f - 10f,
        top = 0f,
        right = windowWidth / 2f + 10f,
        bottom = 2000f,
    )

    /** 水平折痕：平板模式 / 帐篷模式。 */
    private fun horizontalFold(windowWidth: Float) = NirikoFoldBounds(
        left = 0f,
        top = 800f,
        right = windowWidth,
        bottom = 830f,
    )

    private fun from(orientation: Int, bounds: NirikoFoldBounds, windowWidth: Float) =
        NirikoFoldStrategy.from(orientation, bounds, windowWidth)

    // ---------- 手机 ----------

    @Test
    fun phonePortraitVerticalFoldIsSingle() {
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(NirikoFoldStrategy.ORIENTATION_VERTICAL, verticalFold(phonePortraitWidth), phonePortraitWidth),
        )
    }

    @Test
    fun phoneLandscapeHorizontalFoldIsSingle() {
        // 手机横屏即使够宽，水平折痕也不排两栏（首轮不做平板 / 帐篷模式）
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(NirikoFoldStrategy.ORIENTATION_HORIZONTAL, horizontalFold(750f), 750f),
        )
    }

    @Test
    fun landscapePhoneWideEnoughButNotExpandedStaysSingle() {
        // 891dp 宽的横屏手机：纯函数只按宽度判，放得下两栏；实际是否分栏由 EXPANDED
        // （宽 >=840 且高 >=480）把关 —— 横屏手机高度不足，仍是 MEDIUM，单栏。
        assertEquals(
            NirikoPaneArrangement.SIDE_BY_SIDE,
            from(NirikoFoldStrategy.ORIENTATION_VERTICAL, verticalFold(landscapePhoneWidth), landscapePhoneWidth),
        )
        assertFalse(
            nirikoDetailUsesTwoPanes(NirikoWindowLayout.MEDIUM, NirikoPaneArrangement.SIDE_BY_SIDE),
        )
    }

    // ---------- 平板 ----------

    @Test
    fun smallTabletVerticalFoldIsSingle() {
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(NirikoFoldStrategy.ORIENTATION_VERTICAL, verticalFold(smallTabletWidth), smallTabletWidth),
        )
    }

    @Test
    fun largeTabletVerticalFoldIsSideBySide() {
        assertEquals(
            NirikoPaneArrangement.SIDE_BY_SIDE,
            from(NirikoFoldStrategy.ORIENTATION_VERTICAL, verticalFold(largeTabletWidth), largeTabletWidth),
        )
    }

    @Test
    fun largeTabletHorizontalFoldIsTopBottom() {
        assertEquals(
            NirikoPaneArrangement.TOP_BOTTOM,
            from(NirikoFoldStrategy.ORIENTATION_HORIZONTAL, horizontalFold(largeTabletWidth), largeTabletWidth),
        )
    }

    // ---------- 折叠屏 ----------

    @Test
    fun unfoldedFoldableIsSideBySide() {
        assertEquals(
            NirikoPaneArrangement.SIDE_BY_SIDE,
            from(
                NirikoFoldStrategy.ORIENTATION_VERTICAL,
                verticalFold(foldableUnfoldedWidth),
                foldableUnfoldedWidth,
            ),
        )
    }

    @Test
    fun foldedFoldableIsSingle() {
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(
                NirikoFoldStrategy.ORIENTATION_VERTICAL,
                verticalFold(foldableFoldedWidth),
                foldableFoldedWidth,
            ),
        )
    }

    // ---------- 840dp 断点边界 ----------

    @Test
    fun widthBoundaryDoesNotBecomeSideBySide() {
        // 839dp：差 1dp 不分栏（与 NirikoWindowLayout.from 的 EXPANDED 门槛同值）
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(NirikoFoldStrategy.ORIENTATION_VERTICAL, verticalFold(839f), 839f),
        )
    }

    @Test
    fun widthBoundarySplitsAt840() {
        assertEquals(
            NirikoPaneArrangement.SIDE_BY_SIDE,
            from(NirikoFoldStrategy.ORIENTATION_VERTICAL, verticalFold(840f), 840f),
        )
    }

    // ---------- 退化输入 ----------

    @Test
    fun emptyBoundsAreSingle() {
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(
                NirikoFoldStrategy.ORIENTATION_VERTICAL,
                NirikoFoldBounds(0f, 0f, 0f, 0f),
                largeTabletWidth,
            ),
        )
    }

    @Test
    fun degenerateOrientationIsSingle() {
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(
                NirikoFoldStrategy.ORIENTATION_UNKNOWN,
                verticalFold(largeTabletWidth),
                largeTabletWidth,
            ),
        )
    }

    @Test
    fun unboundedWindowWidthIsSingle() {
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(
                NirikoFoldStrategy.ORIENTATION_VERTICAL,
                verticalFold(largeTabletWidth),
                Float.POSITIVE_INFINITY,
            ),
        )
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(NirikoFoldStrategy.ORIENTATION_VERTICAL, verticalFold(largeTabletWidth), Float.NaN),
        )
    }

    @Test
    fun invertedBoundsAreTreatedAsEmpty() {
        assertEquals(
            NirikoPaneArrangement.SINGLE,
            from(
                NirikoFoldStrategy.ORIENTATION_VERTICAL,
                NirikoFoldBounds(left = 900f, top = 0f, right = 500f, bottom = 0f),
                largeTabletWidth,
            ),
        )
    }

    // ---------- 折痕是否落在两栏之间 ----------

    @Test
    fun foldInTheMiddleIsBetweenPanes() {
        assertTrue(
            NirikoFoldStrategy.isVerticalFoldBetweenPanes(verticalFold(largeTabletWidth), largeTabletWidth),
        )
    }

    @Test
    fun foldTouchingScreenEdgeIsNotBetweenPanes() {
        val atLeftEdge = NirikoFoldBounds(left = 0f, top = 0f, right = 20f, bottom = 2000f)
        assertFalse(NirikoFoldStrategy.isVerticalFoldBetweenPanes(atLeftEdge, largeTabletWidth))
        val atRightEdge = NirikoFoldBounds(left = 1004f, top = 0f, right = 1024f, bottom = 2000f)
        assertFalse(NirikoFoldStrategy.isVerticalFoldBetweenPanes(atRightEdge, largeTabletWidth))
    }

    @Test
    fun emptyFoldIsNotBetweenPanes() {
        assertFalse(
            NirikoFoldStrategy.isVerticalFoldBetweenPanes(NirikoFoldBounds(0f, 0f, 0f, 0f), largeTabletWidth),
        )
    }

    // ---------- 两栏门槛（与窗口尺寸类别正交但需要同时成立） ----------

    @Test
    fun twoPanesRequireExpandedLayout() {
        assertTrue(nirikoDetailUsesTwoPanes(NirikoWindowLayout.EXPANDED, NirikoPaneArrangement.SIDE_BY_SIDE))
        // 无折痕的大屏仍然并排（宽屏详情页双栏是窗口尺寸类别的职责）
        assertTrue(nirikoDetailUsesTwoPanes(NirikoWindowLayout.EXPANDED, NirikoPaneArrangement.SINGLE))
        // 水平折痕（平板 / 帐篷模式）首轮不出两栏
        assertFalse(nirikoDetailUsesTwoPanes(NirikoWindowLayout.EXPANDED, NirikoPaneArrangement.TOP_BOTTOM))
        // 手机档一律单栏，无论折痕怎么说
        assertFalse(nirikoDetailUsesTwoPanes(NirikoWindowLayout.MEDIUM, NirikoPaneArrangement.SIDE_BY_SIDE))
        assertFalse(nirikoDetailUsesTwoPanes(NirikoWindowLayout.COMPACT, NirikoPaneArrangement.SIDE_BY_SIDE))
    }
}
