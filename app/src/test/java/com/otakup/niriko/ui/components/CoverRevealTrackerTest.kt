package com.otakup.niriko.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2-2：封面「已揭示」记账的回归。
 *
 * 这条护栏的意义：封面淡入用 remember(url) 记「已到达」，而 remember 会随列表项滚出组合而销毁，
 * 滚回来就重播一次 150ms 淡入。记账对象必须满足：同 url 只首次返回 true、空 url 不参与、
 * 容量上界（长时间浏览不会无界增长）、且**真的被封面组件消费**（否则只是建了个没人用的对象）。
 */
class CoverRevealTrackerTest {

    private fun source(relative: String) = File("src/main/java/com/otakup/niriko/" + relative).readText()

    @Test
    fun firstRecordWinsAndRepeatsReturnFalse() {
        val tracker = CoverRevealTracker(capacity = 8)
        assertTrue("首次揭示应返回 true", tracker.recordRevealed("a.jpg"))
        assertFalse("第二次不该再当作首次", tracker.recordRevealed("a.jpg"))
        assertTrue(tracker.isRevealed("a.jpg"))
        assertFalse(tracker.isRevealed("b.jpg"))
    }

    @Test
    fun nullUrlNeverEntersTheLedger() {
        val tracker = CoverRevealTracker(capacity = 8)
        assertFalse(tracker.recordRevealed(null))
        assertFalse(tracker.isRevealed(null))
        assertEquals(0, tracker.size())
    }

    @Test
    fun capacityEvictsOldestFirst() {
        val tracker = CoverRevealTracker(capacity = 3)
        tracker.recordRevealed("a")
        tracker.recordRevealed("b")
        tracker.recordRevealed("c")
        tracker.recordRevealed("d")
        assertEquals(3, tracker.size())
        assertFalse("最旧的 a 应被淘汰", tracker.isRevealed("a"))
        assertTrue(tracker.isRevealed("d"))
    }

    @Test
    fun rehitMovesEntryToTheBack() {
        // 常被看到的封面不该被一次冷门浏览挤掉：再次命中会把条目移到队尾
        val tracker = CoverRevealTracker(capacity = 2)
        tracker.recordRevealed("a")
        tracker.recordRevealed("b")
        tracker.recordRevealed("a")
        tracker.recordRevealed("c")
        assertTrue(tracker.isRevealed("a"))
        assertFalse(tracker.isRevealed("b"))
    }

    @Test
    fun bothCoverComponentsConsumeTheTracker() {
        // 只建对象不接线的收口是假收口：两个封面组件都必须「查初值 + 成功后记账」
        for (file in listOf("ui/components/CoverImage.kt", "ui/components/SharedSubjectCover.kt")) {
            val text = source(file)
            assertTrue("$file 应从记账表取初值", text.contains("ProcessCoverRevealTracker.isRevealed("))
            assertTrue("$file 应在加载成功后记账", text.contains("ProcessCoverRevealTracker.recordRevealed("))
        }
    }
}
