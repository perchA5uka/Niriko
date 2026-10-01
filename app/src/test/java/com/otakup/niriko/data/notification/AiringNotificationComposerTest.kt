package com.otakup.niriko.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 放送提醒通知合并单元测试（计划 B1-3）：单条保留原文案，多条合并成一条。
 */
class AiringNotificationComposerTest {

    private fun item(id: Long, title: String, minutes: Int? = null) =
        AiringNotificationItem(subjectId = id, title = title, airTimeMinutes = minutes)

    @Test
    fun empty_returnsNull() {
        assertNull(AiringNotificationComposer.compose(emptyList()))
    }

    @Test
    fun single_withAirTime_keepsOriginalWording() {
        val c = AiringNotificationComposer.compose(listOf(item(1, "作品A", 21 * 60 + 30)))!!
        assertEquals("《作品A》今日更新", c.title)
        assertEquals("今日 21:30 放送，记得观看", c.text)
        assertEquals(1L, c.subjectId)
        assertFalse(c.merged)
    }

    @Test
    fun single_withoutAirTime_usesFallbackText() {
        val c = AiringNotificationComposer.compose(listOf(item(2, "作品B")))!!
        assertEquals("《作品B》今日更新", c.title)
        assertEquals("今日放送，记得观看", c.text)
        assertEquals(2L, c.subjectId)
        assertFalse(c.merged)
    }

    @Test
    fun twoItems_mergeWithoutCountSuffix() {
        val c = AiringNotificationComposer.compose(listOf(item(1, "A"), item(2, "B")))!!
        assertEquals("《A》《B》今日更新", c.title)
        assertEquals("今日放送，记得观看", c.text)
        assertTrue(c.merged)
        assertNull(c.subjectId)
    }

    @Test
    fun threeItems_mergeWithCountSuffix() {
        val c = AiringNotificationComposer.compose(listOf(item(1, "A"), item(2, "B"), item(3, "C")))!!
        assertEquals("《A》《B》等 3 部今日更新", c.title)
        assertTrue(c.merged)
    }

    @Test
    fun overlyLongMergedTitle_fallsBackToCountOnly() {
        val long1 = "这是一个非常非常长的作品标题用来把合并后的通知标题顶爆掉甲"
        val long2 = "这是一个非常非常长的作品标题用来把合并后的通知标题顶爆掉乙"
        val long3 = "这是一个非常非常长的作品标题用来把合并后的通知标题顶爆掉丙"
        val c = AiringNotificationComposer.compose(listOf(item(1, long1), item(2, long2), item(3, long3)))!!
        assertEquals("3 部作品今日更新", c.title)
        assertTrue(c.merged)
    }

    @Test
    fun formatMinutes_padsToTwoDigits() {
        assertEquals("00:00", AiringNotificationComposer.formatMinutes(0))
        assertEquals("09:05", AiringNotificationComposer.formatMinutes(9 * 60 + 5))
        assertEquals("21:30", AiringNotificationComposer.formatMinutes(21 * 60 + 30))
    }
}
