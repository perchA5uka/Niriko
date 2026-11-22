package com.otakup.niriko.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Niriko 27 · S0：同心圆角 = 外层圆角 − 内边距，下限 4dp。 */
class ConcentricRadiusTest {

    @Test
    fun 二十减六得十四() {
        assertEquals(14.dp, NirikoShapes.concentric(outer = 20.dp, inset = 6.dp))
    }

    @Test
    fun 内边距为零时与外层相同() {
        assertEquals(20.dp, NirikoShapes.concentric(outer = 20.dp, inset = 0.dp))
    }

    @Test
    fun 内边距过大时落在四dp下限() {
        assertEquals(4.dp, NirikoShapes.concentric(outer = 20.dp, inset = 18.dp))
        assertEquals(4.dp, NirikoShapes.concentric(outer = 12.dp, inset = 40.dp))
    }

    @Test
    fun 内边距越大圆角不会变大() {
        val radii = listOf(0, 2, 4, 8, 12, 16, 20, 24).map { NirikoShapes.concentric(outer = 20.dp, inset = it.dp) }
        radii.zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
    }
}
