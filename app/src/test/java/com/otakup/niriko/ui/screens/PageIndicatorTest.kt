package com.otakup.niriko.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 顶部页面指示器（R9 Pager / Indicator）几何的纯函数测试。 */
class PageIndicatorTest {

    @Test
    fun weightIsFullOnTheExactPage() {
        assertEquals(1f, pageIndicatorWeight(index = 2, pagePosition = 2f), 1e-6f)
    }

    @Test
    fun weightIsZeroOnePageAway() {
        assertEquals(0f, pageIndicatorWeight(index = 0, pagePosition = 1f), 1e-6f)
        assertEquals(0f, pageIndicatorWeight(index = 3, pagePosition = 2f), 1e-6f)
    }

    @Test
    fun weightInterpolatesWhileDragging() {
        assertEquals(0.5f, pageIndicatorWeight(index = 1, pagePosition = 0.5f), 1e-6f)
        assertEquals(0.25f, pageIndicatorWeight(index = 0, pagePosition = 0.75f), 1e-6f)
        // 从第 2 页向第 1 页拖到 1.75：目标段 2 得 0.75，离开段 1 只剩 0.25
        assertEquals(0.25f, pageIndicatorWeight(index = 1, pagePosition = 1.75f), 1e-6f)
        assertEquals(0.75f, pageIndicatorWeight(index = 2, pagePosition = 1.75f), 1e-6f)
    }

    @Test
    fun weightIsClampedForOutOfRangePages() {
        assertEquals(0f, pageIndicatorWeight(index = 3, pagePosition = 0f), 1e-6f)
        assertEquals(0f, pageIndicatorWeight(index = 0, pagePosition = 3f), 1e-6f)
    }

    @Test
    fun widthIsMinWhenInactiveAndMaxWhenActive() {
        assertEquals(PAGE_INDICATOR_MIN_WIDTH_DP, pageIndicatorWidthDp(index = 0, pagePosition = 1f), 1e-4f)
        assertEquals(PAGE_INDICATOR_MAX_WIDTH_DP, pageIndicatorWidthDp(index = 1, pagePosition = 1f), 1e-4f)
    }

    @Test
    fun widthStaysWithinDesignBoundsForEveryPosition() {
        for (index in 0..3) {
            for (page in 0..3) {
                for (offset in listOf(0f, 0.25f, 0.5f, 0.75f)) {
                    val width = pageIndicatorWidthDp(index, page + offset)
                    assertTrue("width=$width", width >= PAGE_INDICATOR_MIN_WIDTH_DP - 1e-4f)
                    assertTrue("width=$width", width <= PAGE_INDICATOR_MAX_WIDTH_DP + 1e-4f)
                }
            }
        }
    }

    @Test
    fun indicatorConstantsAreSane() {
        assertTrue(PAGE_INDICATOR_MIN_WIDTH_DP > 0f)
        assertTrue(PAGE_INDICATOR_MAX_WIDTH_DP > PAGE_INDICATOR_MIN_WIDTH_DP)
    }
}
