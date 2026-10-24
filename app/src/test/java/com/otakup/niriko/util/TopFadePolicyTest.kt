package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 顶部渐隐与悬浮控件让位（F12 / F13）的纯规则测试。
 *
 * §8.3 给了两条硬约束，这里逐条钉住：
 * - **连续**：滚动距离变化一点，强度就跟着变一点 —— 「不使用滚动阈值离散显隐」意味着
 *   任何形如「超过 40px 才显示」的台阶都是错的；
 * - **不吞掉第一项**：列表顶部内边距必须跟着控件实测高度走，且控件还没测出来时也要有下限。
 */
class TopFadePolicyTest {
    @Test
    fun windowMaskHelperNoLongerDefinesTheMaskStart() {
        // v1.2.1 真机裁决：渐隐上边界回到窗口原点（三处 mask 传 0.dp）。
        // 这个函数只剩详情页过渡期引用（另一张卡迁移），这里只锁住它的数值不被误改大。
        assertEquals(56f, TopFadePolicy.windowMaskHiddenDp(0f), 0f)
        assertEquals(80f, TopFadePolicy.windowMaskHiddenDp(24f), 0f)
        assertEquals(104f, TopFadePolicy.windowMaskHiddenDp(48f), 0f)
        // 非法（负）inset 按 0 处理，不会把渐隐带推出屏幕
        assertEquals(56f, TopFadePolicy.windowMaskHiddenDp(-10f), 0f)
        // 渐隐带本身的行为不变（alphaFor / contentAlphaAt 与遮罩起点无关）
        val strength = TopFadePolicy.MAX_ALPHA / TopFadePolicy.MAX_ALPHA
        assertEquals(0f, TopFadePolicy.contentAlphaAt(0f, 24f, strength), 0f)
        assertEquals(0.5f, TopFadePolicy.contentAlphaAt(12f, 24f, strength), 0f)
        assertEquals(1f, TopFadePolicy.contentAlphaAt(24f, 24f, strength), 0f)
        assertEquals(1f, TopFadePolicy.contentAlphaAt(56f, 24f, strength), 0f)
    }

    @Test
    fun feedTopPaddingStaysByteForByteEquivalent() {
        // B1 是「只搬运不改语义」：新函数必须与四页原先手写的 inset + searchContentPaddingDp(56f) 同值。
        assertEquals(72f, TopFadePolicy.feedTopPaddingDp(0f), 0f)
        assertEquals(96f, TopFadePolicy.feedTopPaddingDp(24f), 0f)
        assertEquals(120f, TopFadePolicy.feedTopPaddingDp(48f), 0f)
        // 与旧写法逐点相等
        for (inset in listOf(0f, 8f, 24f, 32f, 48f)) {
            assertEquals(
                inset + TopFadePolicy.searchContentPaddingDp(56f),
                TopFadePolicy.feedTopPaddingDp(inset),
                0f,
            )
        }
    }

    @Test
    fun searchExpandedTopInsetGivesWayToTheExpandedCard() {
        // B3：收起态 = 56dp 工具栏（与 feedTopPaddingDp 同值）
        assertEquals(96f, TopFadePolicy.searchExpandedTopInsetDp(24f, 200f, 68f, collapsed = true, immersiveCollapsed = false), 0f)
        // 沉浸态未编辑：卡片 68dp，让位到 inset + 8 + 68
        assertEquals(100f, TopFadePolicy.searchExpandedTopInsetDp(24f, 200f, 68f, collapsed = false, immersiveCollapsed = true), 0f)
        // 菜单展开：inset + 8 + 200
        assertEquals(232f, TopFadePolicy.searchExpandedTopInsetDp(24f, 200f, 68f, collapsed = false, immersiveCollapsed = false), 0f)
        // inset = 0 也成立
        assertEquals(208f, TopFadePolicy.searchExpandedTopInsetDp(0f, 200f, 68f, collapsed = false, immersiveCollapsed = false), 0f)
        // 展开让位必须比收起态更深（否则内容仍被卡片压住）
        assertTrue(
            TopFadePolicy.searchExpandedTopInsetDp(24f, 200f, 68f, collapsed = false, immersiveCollapsed = false) >
                TopFadePolicy.searchExpandedTopInsetDp(24f, 200f, 68f, collapsed = true, immersiveCollapsed = false),
        )
    }

    @Test
    fun maskOnlyChangesTheShortTopBand() {
        assertEquals(0.08f, TopFadePolicy.contentAlphaAt(0f, 24f, 0.92f), 0.0001f)
        assertEquals(0.54f, TopFadePolicy.contentAlphaAt(12f, 24f, 0.92f), 0.0001f)
        assertEquals(1f, TopFadePolicy.contentAlphaAt(24f, 24f, 0.92f), 0f)
        assertEquals(1f, TopFadePolicy.contentAlphaAt(100f, 24f, 0.92f), 0f)
        assertEquals(1f, TopFadePolicy.contentAlphaAt(0f, 0f, 0.92f), 0f)
        assertEquals(1f, TopFadePolicy.contentAlphaAt(0f, 24f, -1f), 0f)
        assertEquals(0f, TopFadePolicy.contentAlphaAt(0f, 24f, 2f), 0f)
    }

    @Test
    fun searchPaddingIncludesFloatingToolbarOffsetAndGap() {
        assertEquals(72f, TopFadePolicy.searchContentPaddingDp(56f), 0f)
        assertEquals(84f, TopFadePolicy.searchContentPaddingDp(68f), 0f)
        assertEquals(216f, TopFadePolicy.searchContentPaddingDp(200f), 0f)
    }


    @Test
    fun `强度随滚动连续变化_没有台阶`() {
        val distance = 100f
        // 两端
        assertEquals(TopFadePolicy.MIN_ALPHA, TopFadePolicy.alphaFor(0f, distance), 0.0001f)
        assertEquals(TopFadePolicy.MAX_ALPHA, TopFadePolicy.alphaFor(distance, distance), 0.0001f)
        // 中间值落在两端之间（而不是「要么最小要么最大」）
        val mid = TopFadePolicy.alphaFor(distance / 2f, distance)
        assertTrue("中点应在两端之间：$mid", mid > TopFadePolicy.MIN_ALPHA && mid < TopFadePolicy.MAX_ALPHA)
        // 连续：走 1/100 的距离，强度变化也应是整体的 1/100（允许 1e-4 误差）
        val step = (TopFadePolicy.MAX_ALPHA - TopFadePolicy.MIN_ALPHA) / 100f
        assertEquals(step, TopFadePolicy.alphaFor(2f, distance) - TopFadePolicy.alphaFor(1f, distance), 0.0001f)
    }

    @Test
    fun `单调不减且越界被夹住`() {
        val distance = 50f
        var previous = -1f
        for (offset in listOf(0f, 5f, 10f, 25f, 50f, 120f)) {
            val alpha = TopFadePolicy.alphaFor(offset, distance)
            assertTrue("offset=$offset 时强度不应下降", alpha >= previous - 0.0001f)
            previous = alpha
        }
        // 负数（橡皮筋回弹）不会比「刚到顶」更亮
        assertEquals(TopFadePolicy.MIN_ALPHA, TopFadePolicy.alphaFor(-500f, distance), 0.0001f)
        // 非法渐隐带高度不崩、不除零
        assertTrue(TopFadePolicy.alphaFor(10f, 0f) in TopFadePolicy.MIN_ALPHA..TopFadePolicy.MAX_ALPHA)
        assertTrue(TopFadePolicy.alphaFor(10f, -5f) in TopFadePolicy.MIN_ALPHA..TopFadePolicy.MAX_ALPHA)
    }

    @Test
    fun `两端不是 0 与 1 —— 刻意留层次`() {
        // 起点不为 0：否则顶部控件与内容会贴死、看不出层次
        assertTrue(TopFadePolicy.MIN_ALPHA > 0f)
        // 终点不为 1：否则会变成一条实心色带
        assertTrue(TopFadePolicy.MAX_ALPHA < 1f)
        assertTrue(TopFadePolicy.MIN_ALPHA < TopFadePolicy.MAX_ALPHA)
        assertTrue(TopFadePolicy.FADE_DISTANCE_DP in 8f..64f)
    }

    @Test
    fun `列表顶部内边距跟控件实测高度走_未测量时有下限`() {
        assertEquals(TopFadePolicy.MIN_HEADER_PADDING_DP, TopFadePolicy.listTopPaddingDp(0f), 0.0001f)
        // 还没测量出来（负值/0）也不能把内容顶到控件下面
        assertEquals(TopFadePolicy.MIN_HEADER_PADDING_DP, TopFadePolicy.listTopPaddingDp(-10f), 0.0001f)
        // 有实测高度时 = 高度 + 呼吸空间
        assertEquals(104f, TopFadePolicy.listTopPaddingDp(100f), 0.0001f)
        assertEquals(64f, TopFadePolicy.listTopPaddingDp(60f, extraDp = 4f), 0.0001f)
        // 单调
        assertTrue(TopFadePolicy.listTopPaddingDp(80f) > TopFadePolicy.listTopPaddingDp(40f))
    }

    @Test
    fun `首项索引非零时当作已滚很远`() {
        // 停在第一项：用真实偏移
        assertEquals(12f, TopFadePolicy.scrollOffsetFor(0, 12), 0.0001f)
        // 已经滚过第一项：offset 往往归零，此时必须仍然算「滚开了」，否则渐隐会突然变淡
        val far = TopFadePolicy.scrollOffsetFor(5, 0)
        assertTrue("首项索引 > 0 时应视为已滚很远：$far", far >= 1000f)
        assertEquals(TopFadePolicy.MAX_ALPHA, TopFadePolicy.alphaFor(far, 100f), 0.0001f)
    }
}
