package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保存反馈与跳转的纯规则测试（A-X5）。
 *
 * 要锁住两件事：**写库失败必须是失败文案**（改造前无条件写「记录已保存」），
 * 以及**只有新建收藏才允许自动返回**（本轮没有该入口，所以现有调用恒为 false）。
 */
class CollectionSavePolicyTest {

    @Test
    fun `写库成功与失败的文案必须不同`() {
        assertEquals("记录已保存", CollectionSavePolicy.messageFor(written = true))
        assertEquals("保存失败，请重试", CollectionSavePolicy.messageFor(written = false))
        assertFalse(
            "失败文案不能和成功文案相同，否则又会退页",
            CollectionSavePolicy.messageFor(false) == CollectionSavePolicy.messageFor(true),
        )
    }

    @Test
    fun `只有新建收藏且写库成功才自动返回`() {
        assertTrue(CollectionSavePolicy.shouldAutoBack(isNewCollection = true, written = true))
        assertFalse("已有收藏保存后留在详情页", CollectionSavePolicy.shouldAutoBack(isNewCollection = false, written = true))
        assertFalse("写库失败一律不退页", CollectionSavePolicy.shouldAutoBack(isNewCollection = true, written = false))
        assertFalse(CollectionSavePolicy.shouldAutoBack(isNewCollection = false, written = false))
    }
}
