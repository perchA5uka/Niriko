package com.otakup.niriko.data.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingInsightsTest {

    @Test
    fun `空分布返回无数据哨兵值并显示破折号`() {
        assertEquals(RatingInsights.NO_DEVIATION, RatingInsights.deviation(emptyMap()), 0.0001f)
        assertEquals("—", RatingInsights.disputeLabel(RatingInsights.NO_DEVIATION))
    }

    @Test
    fun `全部集中在同一档时标准差为0且判为异口同声`() {
        val deviation = RatingInsights.deviation(mapOf(8 to 100))
        assertEquals(0f, deviation, 0.0001f)
        assertEquals("异口同声", RatingInsights.disputeLabel(deviation))
    }

    @Test
    fun `两极分化时判为厨黑大战`() {
        // 1 分与 10 分各一半 → 均值 5.5，标准差 4.5
        val deviation = RatingInsights.deviation(mapOf(1 to 50, 10 to 50))
        assertEquals(4.5f, deviation, 0.01f)
        assertEquals("厨黑大战", RatingInsights.disputeLabel(deviation))
    }

    @Test
    fun `分歧度阈值边界`() {
        assertEquals("基本一致", RatingInsights.disputeLabel(1.0f))
        assertEquals("略有分歧", RatingInsights.disputeLabel(1.15f))
        assertEquals("莫衷一是", RatingInsights.disputeLabel(1.3f))
        assertEquals("各执一词", RatingInsights.disputeLabel(1.45f))
        assertEquals("你死我活", RatingInsights.disputeLabel(1.6f))
        assertEquals("厨黑大战", RatingInsights.disputeLabel(1.75f))
    }

    @Test
    fun `样本不足时百分位返回null`() {
        assertNull(RatingInsights.localPercentile(8f, listOf(7f, 8f, 9f, 6f)))
    }

    @Test
    fun `百分位按低于本作的比例计算`() {
        // 10 个样本里 6 个低于 8 分 → 60 百分位
        val scores = listOf(6f, 7f, 7f, 6f, 7f, 6f, 9f, 9f, 10f, 8f)
        assertEquals(60, RatingInsights.localPercentile(8f, scores))
    }

    @Test
    fun `分数为null时百分位为null`() {
        assertNull(RatingInsights.localPercentile(null, List(10) { 8f }))
    }

    @Test
    fun `跨标尺换算到十分制`() {
        assertEquals(8.7f, RatingInsights.toTenPoint(87f, 100f)!!, 0.001f)
        assertEquals(9.2f, RatingInsights.toTenPoint(4.6f, 5f)!!, 0.001f)
        assertNull(RatingInsights.toTenPoint(null, 10f))
        assertNull(RatingInsights.toTenPoint(8f, 0f))
    }
}
