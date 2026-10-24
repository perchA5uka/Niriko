package com.otakup.niriko.ui.subject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B10 收藏入口两态真值表（纯函数，JVM 可测）。
 *
 * 要锁住的是「两态同形」的前提：同一个外壳只按 [DetailCollectionActionState] 渲染，
 * 所以「未收藏/已收藏」的差异只允许出现在文案与图标上，**可点性只由 isUpdating 决定**。
 * 改造前两态各自判 isUpdating（Button 的 enabled 与 DetailDock 的 clickable 是两处），
 * 漏一处就会留下「保存中还能再点」的死角。
 */
class DetailCollectionActionStateTest {

    @Test
    fun `未收藏两态`() {
        val idle = detailCollectionActionState(inCollection = false, isUpdating = false)
        assertFalse(idle.inCollection)
        assertEquals("加入收藏", idle.label)
        assertEquals(DetailCollectionActionIcon.ADD, idle.icon)
        assertTrue("空闲时可点", idle.enabled)

        val adding = detailCollectionActionState(inCollection = false, isUpdating = true)
        assertFalse(adding.inCollection)
        assertEquals("添加中…", adding.label)
        assertEquals(DetailCollectionActionIcon.ADD, adding.icon)
        assertFalse("写操作在飞时不可点", adding.enabled)
    }

    @Test
    fun `已收藏两态`() {
        val idle = detailCollectionActionState(inCollection = true, isUpdating = false)
        assertTrue(idle.inCollection)
        assertEquals("已收藏", idle.label)
        assertEquals(DetailCollectionActionIcon.STAR, idle.icon)
        assertTrue("已收藏时可点开编辑层", idle.enabled)

        val saving = detailCollectionActionState(inCollection = true, isUpdating = true)
        assertTrue(saving.inCollection)
        assertEquals("保存中…", saving.label)
        assertEquals(DetailCollectionActionIcon.STAR, saving.icon)
        assertFalse(saving.enabled)
    }

    @Test
    fun `可点性只由 isUpdating 决定_与是否收藏无关`() {
        assertTrue(detailCollectionActionState(false, false).enabled)
        assertTrue(detailCollectionActionState(true, false).enabled)
        assertFalse(detailCollectionActionState(false, true).enabled)
        assertFalse(detailCollectionActionState(true, true).enabled)
    }

    @Test
    fun `图标只由是否收藏决定_与写状态无关`() {
        assertEquals(DetailCollectionActionIcon.ADD, detailCollectionActionState(false, false).icon)
        assertEquals(DetailCollectionActionIcon.ADD, detailCollectionActionState(false, true).icon)
        assertEquals(DetailCollectionActionIcon.STAR, detailCollectionActionState(true, false).icon)
        assertEquals(DetailCollectionActionIcon.STAR, detailCollectionActionState(true, true).icon)
    }

    @Test
    fun `四种组合的文案互不相同_避免状态含糊`() {
        val labels = listOf(
            detailCollectionActionState(false, false),
            detailCollectionActionState(false, true),
            detailCollectionActionState(true, false),
            detailCollectionActionState(true, true),
        ).map { it.label }
        assertEquals(4, labels.toSet().size)
    }
}
