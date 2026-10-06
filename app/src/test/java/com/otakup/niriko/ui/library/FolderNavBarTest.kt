package com.otakup.niriko.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作品库分区条（F09）的纯规则测试。
 *
 * 这些规则看起来只是「一行 if」，但它们是**用户能直接看到的**行为：
 * 空名字按了确认却没反应、点当前分区不收起、分区名空白成一个空胶囊。
 */
class FolderNavBarTest {

    @Test
    fun `点当前分区等于收起回全部`() {
        assertNull("正在看 3 号分区时再点 3 号 = 回到全部", nextSelectedFolder(current = 3L, clicked = 3L))
    }

    @Test
    fun `点别的分区就是切过去`() {
        assertEquals(5L, nextSelectedFolder(current = 3L, clicked = 5L))
        assertEquals(2L, nextSelectedFolder(current = null, clicked = 2L))
    }

    @Test
    fun `分区名去空白后非空才可用`() {
        assertFalse(isUsableFolderName(""))
        assertFalse(isUsableFolderName("   "))
        assertFalse(isUsableFolderName("\t\n"))
        assertTrue(isUsableFolderName("待补"))
        assertTrue(isUsableFolderName("  待补  "))
    }

    @Test
    fun `空名分区有兜底显示名`() {
        assertEquals("未命名分区", folderDisplayName(""))
        assertEquals("未命名分区", folderDisplayName("   "))
        assertEquals("待补", folderDisplayName("  待补  "))
    }
}
