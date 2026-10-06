package com.otakup.niriko.data.model.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 发现页布局枚举（第 3 轮参考计划 §3.13）。
 *
 * 三态循环 + 持久化 key 的向后兼容，是 P0 唯一新增的纯逻辑，因此单独锁死：
 * 老存档值 `"GRID"` 必须照旧落在宫格，脏数据必须兜底 [DiscoveryLayout.CARD]。
 */
class DiscoveryLayoutTest {

    @Test
    fun entriesAreCardGridPoster() {
        assertEquals(
            listOf(DiscoveryLayout.CARD, DiscoveryLayout.GRID, DiscoveryLayout.POSTER),
            DiscoveryLayout.entries.toList(),
        )
    }

    @Test
    fun toggledCyclesThroughThreeLayouts() {
        assertEquals(DiscoveryLayout.GRID, DiscoveryLayout.CARD.toggled)
        assertEquals(DiscoveryLayout.POSTER, DiscoveryLayout.GRID.toggled)
        assertEquals(DiscoveryLayout.CARD, DiscoveryLayout.POSTER.toggled)
    }

    @Test
    fun toggledReturnsToStartAfterAFullCycle() {
        var layout = DiscoveryLayout.CARD
        repeat(DiscoveryLayout.entries.size) { layout = layout.toggled }
        assertEquals(DiscoveryLayout.CARD, layout)
    }

    @Test
    fun persistenceKeysAreStable() {
        // key 是写进 DataStore 的值：改了会让老用户的存档失效
        assertEquals("CARD", DiscoveryLayout.CARD.key)
        assertEquals("GRID", DiscoveryLayout.GRID.key)
        assertEquals("POSTER", DiscoveryLayout.POSTER.key)
        assertEquals("海报", DiscoveryLayout.POSTER.label)
    }

    @Test
    fun legacyGridKeyStillResolvesToGrid() {
        assertEquals(DiscoveryLayout.GRID, DiscoveryLayout.fromKey("GRID"))
        assertEquals(DiscoveryLayout.GRID, DiscoveryLayout.fromKey("grid"))
    }

    @Test
    fun posterKeyResolvesIgnoringCaseAndSpaces() {
        assertEquals(DiscoveryLayout.POSTER, DiscoveryLayout.fromKey("POSTER"))
        assertEquals(DiscoveryLayout.POSTER, DiscoveryLayout.fromKey("poster "))
        assertEquals(DiscoveryLayout.POSTER, DiscoveryLayout.fromKey(" Poster"))
    }

    @Test
    fun unknownOrMissingKeyFallsBackToCard() {
        assertEquals(DiscoveryLayout.CARD, DiscoveryLayout.fromKey(null))
        assertEquals(DiscoveryLayout.CARD, DiscoveryLayout.fromKey(""))
        assertEquals(DiscoveryLayout.CARD, DiscoveryLayout.fromKey("   "))
        assertEquals(DiscoveryLayout.CARD, DiscoveryLayout.fromKey("TRIPLE"))
    }
}
