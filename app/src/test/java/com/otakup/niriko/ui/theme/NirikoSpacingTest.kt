package com.otakup.niriko.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Niriko 27 · S0：间距栅格令牌与纯函数。 */
class NirikoSpacingTest {

    @Test
    fun 栅格升序且无重复() {
        val grid = NirikoSpacing.gridDp
        assertEquals(grid.sorted(), grid)
        assertEquals(grid.toSet().size, grid.size)
    }

    @Test
    fun 除发丝档外全是4的倍数() {
        NirikoSpacing.gridDp.filter { it != 2 }.forEach {
            assertEquals("栅格档应为 4 的倍数：" + it, 0, it % 4)
        }
    }

    @Test
    fun 命名令牌与栅格一一对应() {
        val named = listOf(
            NirikoSpacing.Xxs, NirikoSpacing.Xs, NirikoSpacing.S, NirikoSpacing.M, NirikoSpacing.L,
            NirikoSpacing.Xl, NirikoSpacing.Xxl, NirikoSpacing.X3, NirikoSpacing.X4, NirikoSpacing.X5,
        )
        assertEquals(NirikoSpacing.gridDp.map { it.dp }, named)
    }

    @Test
    fun 存量常见的非栅格值被判为不在栅格上() {
        listOf(3f, 6f, 7f, 9f, 10f, 14f).forEach { assertFalse("不应在栅格上：" + it, NirikoSpacing.isOnGrid(it)) }
        listOf(0f, 2f, 4f, 8f, 12f, 16f, 20f, 24f, 56f).forEach { assertTrue("应在栅格上：" + it, NirikoSpacing.isOnGrid(it)) }
    }

    @Test
    fun 最近栅格档平局时取更小一档() {
        assertEquals(8, NirikoSpacing.nearestOnGrid(10f))
        assertEquals(4, NirikoSpacing.nearestOnGrid(6f))
        assertEquals(12, NirikoSpacing.nearestOnGrid(13f))
        assertEquals(16, NirikoSpacing.nearestOnGrid(15f))
    }

    @Test
    fun 栅格内的值原样返回() {
        NirikoSpacing.gridDp.forEach { assertEquals(it, NirikoSpacing.nearestOnGrid(it.toFloat())) }
    }

    @Test
    fun 越界输入落到首尾档() {
        assertEquals(2, NirikoSpacing.nearestOnGrid(-5f))
        assertEquals(56, NirikoSpacing.nearestOnGrid(500f))
    }
}
