package com.otakup.niriko.ui.icon

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Niriko 27 · S0：图标尺寸档。 */
class NirikoIconsTest {

    @Test
    fun 四档尺寸与令牌一致() {
        assertEquals(listOf(16.dp, 20.dp, 24.dp, 32.dp), listOf(NirikoIcons.Small, NirikoIcons.Medium, NirikoIcons.Large, NirikoIcons.XLarge))
        assertEquals(listOf(16, 20, 24, 32), NirikoIcons.sizesDp)
    }

    @Test
    fun 取档时平局取更大一档() {
        // 存量里有 18dp 图标：夹在 16 与 20 之间，取 20
        assertEquals(20, NirikoIcons.nearestSizeDp(18f))
        assertEquals(24, NirikoIcons.nearestSizeDp(22f))
        assertEquals(32, NirikoIcons.nearestSizeDp(28f))
    }

    @Test
    fun 非平局取最近档() {
        assertEquals(16, NirikoIcons.nearestSizeDp(14f))
        assertEquals(24, NirikoIcons.nearestSizeDp(25f))
        assertEquals(32, NirikoIcons.nearestSizeDp(64f))
    }
}
