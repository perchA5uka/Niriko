package com.otakup.niriko.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Niriko 27 · S0：材质层级的不变量。
 *
 * 数值取自计划 §3.1 的起始值；S1 真机调参后要同步改这里的「钉值」那一条，
 * 其余条目是层级关系，调参不应该打破。
 */
class SurfaceLevelsTest {

    private val glassLevels = SurfaceLevel.entries.filter { it.isGlass }

    @Test
    fun 页面底不是玻璃也没有任何材质参数() {
        val canvas = SurfaceLevel.Canvas
        assertFalse(canvas.isGlass)
        assertEquals(0.dp, canvas.blur)
        assertEquals(0f, canvas.tintAlphaLight, 0f)
        assertEquals(0f, canvas.tintAlphaDark, 0f)
        assertEquals(0f, canvas.edgeAlpha, 0f)
        assertNull(canvas.shadow)
    }

    @Test
    fun 层级越高tint与受光越强() {
        glassLevels.zipWithNext().forEach { (lower, higher) ->
            assertTrue("浅色 tint 应逐层变大：" + lower + " → " + higher, higher.tintAlphaLight > lower.tintAlphaLight)
            assertTrue("深色 tint 应逐层变大：" + lower + " → " + higher, higher.tintAlphaDark > lower.tintAlphaDark)
            assertTrue("边缘受光应逐层变大：" + lower + " → " + higher, higher.edgeAlpha > lower.edgeAlpha)
        }
    }

    @Test
    fun 浮起的层级越高模糊越强() {
        // 刻意只从 Card 往上比：Section 是全幅大面积区块（20dp），比小尺寸的 Card（16dp）模糊更宽，
        // 否则大面积玻璃会显得颗粒感重。S1 真机觉得不合理再统一，改这里的同时改 SurfaceLevel。
        val floating = listOf(SurfaceLevel.Card, SurfaceLevel.Floating, SurfaceLevel.Modal)
        floating.zipWithNext().forEach { (lower, higher) ->
            assertTrue("blur 应逐层变大：" + lower + " → " + higher, higher.blur > lower.blur)
        }
    }

    @Test
    fun 深色主题的tint不低于浅色() {
        glassLevels.forEach { level ->
            assertTrue(level.toString() + " 深色 tint 不应比浅色更淡", level.tintAlphaDark >= level.tintAlphaLight)
            assertEquals(level.tintAlphaLight, level.tintAlpha(isDark = false), 0f)
            assertEquals(level.tintAlphaDark, level.tintAlpha(isDark = true), 0f)
        }
    }

    @Test
    fun 所有不透明度都在零到一之间() {
        SurfaceLevel.entries.forEach { level ->
            listOf(level.tintAlphaLight, level.tintAlphaDark, level.edgeAlpha, level.shadow?.alpha ?: 0f).forEach {
                assertTrue(level.toString() + " 的不透明度越界：" + it, it in 0f..1f)
            }
        }
    }

    @Test
    fun 卡片以上才有投影且投影逐层加重() {
        assertNull(SurfaceLevel.Section.shadow)
        val shadowed = listOf(SurfaceLevel.Card, SurfaceLevel.Floating, SurfaceLevel.Modal)
        shadowed.forEach { assertNotNull(it.toString() + " 应有投影", it.shadow) }
        shadowed.zipWithNext().forEach { (lower, higher) ->
            val a = lower.shadow!!
            val b = higher.shadow!!
            assertTrue(b.offsetY > a.offsetY)
            assertTrue(b.blur > a.blur)
            assertTrue(b.alpha > a.alpha)
        }
    }

    @Test
    fun 起始值钉在计划表上() {
        // S1 调参后改这里：计划 §3.1 的起始值
        assertEquals(16.dp, SurfaceLevel.Card.blur)
        assertEquals(0.10f, SurfaceLevel.Card.tintAlphaLight, 0f)
        assertEquals(0.14f, SurfaceLevel.Card.tintAlphaDark, 0f)
        assertEquals(0.16f, SurfaceLevel.Card.edgeAlpha, 0f)
        assertEquals(28.dp, SurfaceLevel.Modal.blur)
        assertEquals(40.dp, SurfaceLevel.Modal.shadow!!.blur)
    }
}
