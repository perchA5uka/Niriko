package com.otakup.niriko.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 作品信息分享卡的版式单测：标签/短评截断、评分占位、进度收敛。 */
class ShareShowcasePolicyTest {

    @Test
    fun `标签去空去重并截到 4 个`() {
        val tags = listOf(" 恋爱 ", "", "校园", "恋爱", "群像", "TV", "漫画改")
        assertEquals(listOf("恋爱", "校园", "群像", "TV"), ShareShowcasePolicy.tagsToShow(tags))
    }

    @Test
    fun `短评空串视为没有`() {
        assertNull(ShareShowcasePolicy.clampImpression(null))
        assertNull(ShareShowcasePolicy.clampImpression("   "))
    }

    @Test
    fun `短评超长截断补省略号`() {
        val long = "好".repeat(80)
        val clamped = ShareShowcasePolicy.clampImpression(long)!!
        assertEquals(57, clamped.length)
        assertEquals(true, clamped.endsWith("…"))
    }

    @Test
    fun `评分没有时显示占位`() {
        assertEquals("—", ShareShowcasePolicy.scoreText(null))
        assertEquals("8.4", ShareShowcasePolicy.scoreText(8.44f))
    }

    @Test
    fun `进度比例收敛到 0 到 1`() {
        assertEquals(0f, ShareShowcasePolicy.progressFraction(null, 12), 0.001f)
        assertEquals(0f, ShareShowcasePolicy.progressFraction(8, 0), 0.001f)
        assertEquals(0.5f, ShareShowcasePolicy.progressFraction(6, 12), 0.001f)
        assertEquals(1f, ShareShowcasePolicy.progressFraction(20, 12), 0.001f)
    }

    @Test
    fun `进度文案区分有无总数`() {
        assertEquals("8 / 12 集", ShareShowcasePolicy.progressLabel(8, 12))
        assertEquals("已看 8 集", ShareShowcasePolicy.progressLabel(8, null))
        assertNull(ShareShowcasePolicy.progressLabel(null, 12))
    }
}
