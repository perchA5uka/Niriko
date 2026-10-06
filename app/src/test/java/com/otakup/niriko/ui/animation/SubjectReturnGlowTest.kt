package com.otakup.niriko.ui.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 返回余光（F17）的一次性事件语义测试。
 *
 * 这条反馈最怕的不是画得不好看，而是**变成噪音**：做成状态就会每次重组闪一次，
 * 或者被「别的作品」的请求吃掉。因此这里断言的是事件语义本身。
 */
class SubjectReturnGlowTest {

    private fun reset() {
        SubjectReturnGlow.pending.value.let { if (it != null) SubjectReturnGlow.clear(it) }
    }

    @Test
    fun `初始没有待播放口令`() {
        reset()
        assertNull(SubjectReturnGlow.pending.value)
        assertFalse(SubjectReturnGlow.hasPending())
    }

    @Test
    fun `请求之后只有匹配的作品会看到`() {
        reset()
        SubjectReturnGlow.request(1234L)
        assertEquals(1234L, SubjectReturnGlow.pending.value)
        assertTrue(SubjectReturnGlow.hasPending())
        // 不匹配的卡片读到的不是自己 → 不播（这一条是「只有那一张卡亮」的全部依据）
        assertTrue(SubjectReturnGlow.pending.value != 999L)
    }

    @Test
    fun `清除只清自己那一次_不吞掉别人的请求`() {
        reset()
        SubjectReturnGlow.request(111L)
        // A 的动画还没跑完，用户已经进了 B 又退回来
        SubjectReturnGlow.request(222L)
        // A 动画结束时调用 clear(111) —— 不能把 B 的口令清掉
        SubjectReturnGlow.clear(111L)
        assertEquals(222L, SubjectReturnGlow.pending.value)
        // 真正消费 B 之后才清空
        SubjectReturnGlow.clear(222L)
        assertNull(SubjectReturnGlow.pending.value)
    }

    @Test
    fun `非法 id 不产生口令`() {
        reset()
        SubjectReturnGlow.request(0L)
        SubjectReturnGlow.request(-5L)
        assertNull(SubjectReturnGlow.pending.value)
    }

    @Test
    fun `重复请求同一作品是幂等的`() {
        reset()
        SubjectReturnGlow.request(7L)
        SubjectReturnGlow.request(7L)
        assertEquals(7L, SubjectReturnGlow.pending.value)
        SubjectReturnGlow.clear(7L)
        assertNull(SubjectReturnGlow.pending.value)
    }

    @Test
    fun `扫光强度两端为零_中段最强`() {
        assertEquals(0f, returnGlowAlpha(0f), 0.0001f)
        assertEquals(0f, returnGlowAlpha(1f), 0.0001f)
        assertEquals(1f, returnGlowAlpha(0.5f), 0.0001f)
        // 越界被夹住（不会算出负值或超过 1）
        assertEquals(0f, returnGlowAlpha(-3f), 0.0001f)
        assertEquals(0f, returnGlowAlpha(4f), 0.0001f)
        // 单调上升到中点（0.1 → 0.25 变亮），之后单调下降（0.75 → 0.9 变暗）
        assertTrue(returnGlowAlpha(0.25f) > returnGlowAlpha(0.1f))
        assertTrue(returnGlowAlpha(0.9f) < returnGlowAlpha(0.75f))
        assertEquals(300, SubjectReturnGlow.DURATION_MS)
    }
}
