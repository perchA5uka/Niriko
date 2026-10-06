package com.otakup.niriko.data.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3 壁纸库：JSON 编解码往返 + 轮换纯函数。 */
class WallpaperLibraryTest {

    private fun entry(id: String, favorite: Boolean = false) = WallpaperLibraryEntry(
        id = id,
        uri = "file:///data/wallpapers/$id.jpg",
        name = "壁纸 $id",
        tags = listOf("风景"),
        favorite = favorite,
        addedAt = 1_700_000_000_000L + id.hashCode(),
    )

    // ===== 编解码 =====

    @Test
    fun codecRoundTripsEntries() {
        val source = listOf(entry("a"), entry("b", favorite = true))
        val restored = WallpaperLibraryCodec.decode(WallpaperLibraryCodec.encode(source))
        assertEquals(source, restored)
    }

    @Test
    fun codecHandlesBlankAndBrokenInput() {
        assertTrue(WallpaperLibraryCodec.decode(null).isEmpty())
        assertTrue(WallpaperLibraryCodec.decode("").isEmpty())
        assertTrue(WallpaperLibraryCodec.decode("   ").isEmpty())
        assertTrue(WallpaperLibraryCodec.decode("{not json").isEmpty())
        assertTrue(WallpaperLibraryCodec.decode("[{\"id\":1}]").isEmpty())
    }

    @Test
    fun codecIgnoresUnknownKeysAndMissingOptionalFields() {
        val raw = "[{\"id\":\"a\",\"uri\":\"file:///a.jpg\",\"future\":42}]"
        val restored = WallpaperLibraryCodec.decode(raw)
        assertEquals(1, restored.size)
        assertEquals("a", restored[0].id)
        assertTrue(restored[0].tags.isEmpty())
        assertEquals(false, restored[0].favorite)
        assertEquals(0L, restored[0].addedAt)
    }

    @Test
    fun codecEncodesEmptyListAsParsableJson() {
        assertEquals("[]", WallpaperLibraryCodec.encode(emptyList()))
        assertTrue(WallpaperLibraryCodec.decode("[]").isEmpty())
    }

    // ===== 轮换 =====

    @Test
    fun candidatesReturnWholeListWhenFavoritesNotRequired() {
        val entries = listOf(entry("a"), entry("b", favorite = true))
        assertEquals(entries, WallpaperRotation.candidates(entries, favoritesOnly = false))
    }

    @Test
    fun candidatesFilterFavoritesWhenRequired() {
        val entries = listOf(entry("a"), entry("b", favorite = true), entry("c", favorite = true))
        assertEquals(listOf("b", "c"), WallpaperRotation.candidates(entries, true).map { it.id })
        assertTrue(WallpaperRotation.candidates(listOf(entry("a")), true).isEmpty())
    }

    @Test
    fun nextFollowsCurrentThenWrapsAround() {
        val entries = listOf(entry("a"), entry("b"), entry("c"))
        assertEquals("b", WallpaperRotation.next(entries, entries[0].uri, false)?.id)
        assertEquals("c", WallpaperRotation.next(entries, entries[1].uri, false)?.id)
        assertEquals("a", WallpaperRotation.next(entries, entries[2].uri, false)?.id)
    }

    @Test
    fun nextStartsAtFirstWhenCurrentUnknownOrBlank() {
        val entries = listOf(entry("a"), entry("b"))
        assertEquals("a", WallpaperRotation.next(entries, null, false)?.id)
        assertEquals("a", WallpaperRotation.next(entries, "", false)?.id)
        assertEquals("a", WallpaperRotation.next(entries, "file:///elsewhere.jpg", false)?.id)
    }

    @Test
    fun nextHonoursFavoritesOnlyPool() {
        val entries = listOf(entry("a"), entry("b", favorite = true), entry("c"))
        assertEquals("b", WallpaperRotation.next(entries, null, true)?.id)
        // 当前壁纸是非收藏项 → 从收藏池第一张开始
        assertEquals("b", WallpaperRotation.next(entries, entries[0].uri, true)?.id)
    }

    @Test
    fun nextReturnsNullWhenPoolEmpty() {
        assertNull(WallpaperRotation.next(emptyList(), null, false))
        assertNull(WallpaperRotation.next(listOf(entry("a")), "file:///a.jpg", true))
    }
}
