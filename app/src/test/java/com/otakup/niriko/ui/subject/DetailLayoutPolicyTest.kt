package com.otakup.niriko.ui.subject

import com.otakup.niriko.ui.adaptive.NirikoDetailPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 详情部件规则（F06：隐藏 + 排序）的单测。
 *
 * 最要紧的一条是**默认顺序**：枚举声明顺序就是用户今天看到的顺序 ——
 * 一旦它变了，所有没改过设置的用户都会看到版面跳变，所以逐项钉死。
 * 其次是 §9.1 的两条容错边界：未知 ID 忽略、新部件追加到默认尾部。
 */
class DetailLayoutPolicyTest {

    /** 改造前源码里的顺序（逐一抄下来，等于把「默认不变」写成断言）。 */
    private val expectedDefaultKeys = listOf(
        "cover", "title", "infobox", "steam", "anilist", "vndb",
        "relations", "statGrid", "anitabi", "guess",
        "rating", "external_rating", "extended", "thumbs",
        "episodes_rating", "tmdb_binding", "tracks", "collectionBtn",
    )

    @Test
    fun `默认顺序等于改造前的源码顺序`() {
        assertEquals(expectedDefaultKeys, DetailLayoutPolicy.defaultOrder.map { it.key })
    }

    @Test
    fun `部件 key 唯一且非空_标签也不为空`() {
        val keys = DetailSectionId.entries.map { it.key }
        assertEquals("key 是持久化身份，不允许重复", keys.size, keys.toSet().size)
        assertTrue(keys.all { it.isNotBlank() })
        assertTrue(DetailSectionId.entries.all { it.label.isNotBlank() })
    }

    @Test
    fun `核心部件正好是封面标题与收藏入口`() {
        assertEquals(
            listOf("cover", "title", "collectionBtn"),
            DetailLayoutPolicy.defaultOrder.filter { it.isCore }.map { it.key },
        )
        assertFalse(DetailLayoutPolicy.hideable.any { it.isCore })
        assertEquals(expectedDefaultKeys.size - 3, DetailLayoutPolicy.hideable.size)
    }

    @Test
    fun `AniList 与 VNDB 的旧状态 key 归并到父部件`() {
        // §35 第一批把它们写成独立 key，用户可能已经关过；升级后不能失效
        assertEquals(DetailSectionId.ANILIST, DetailLayoutPolicy.fromKey("anilist_pending"))
        assertEquals(DetailSectionId.ANILIST, DetailLayoutPolicy.fromKey("anilist_candidates"))
        assertEquals(DetailSectionId.ANILIST, DetailLayoutPolicy.fromKey("anilist_source"))
        assertEquals(DetailSectionId.VNDB, DetailLayoutPolicy.fromKey("vndb_pending"))
        assertEquals(DetailSectionId.VNDB, DetailLayoutPolicy.fromKey("vndb_candidates"))
        assertEquals(
            setOf(DetailSectionId.ANILIST, DetailSectionId.VNDB),
            DetailLayoutPolicy.hiddenFromEncoded("anilist_pending,vndb_candidates"),
        )
    }

    @Test
    fun `解码_未知key忽略_核心部件即使被写进设置也不生效`() {
        assertEquals(emptySet<DetailSectionId>(), DetailLayoutPolicy.hiddenFromEncoded(null))
        assertEquals(emptySet<DetailSectionId>(), DetailLayoutPolicy.hiddenFromEncoded("   "))
        assertEquals(
            setOf(DetailSectionId.STEAM, DetailSectionId.VNDB, DetailSectionId.TRACKS),
            DetailLayoutPolicy.hiddenFromEncoded("steam, vndb ,tracks"),
        )
        assertEquals(
            setOf(DetailSectionId.STEAM),
            DetailLayoutPolicy.hiddenFromEncoded("steam,some_removed_section,,"),
        )
        assertEquals(
            setOf(DetailSectionId.VNDB),
            DetailLayoutPolicy.hiddenFromEncoded("title,cover,collectionBtn,vndb"),
        )
        assertEquals(setOf(DetailSectionId.TRACKS), DetailLayoutPolicy.hiddenFromEncoded(" TRACKS "))
    }

    @Test
    fun `编码按默认顺序输出_与输入顺序无关`() {
        val a = DetailLayoutPolicy.encodeHidden(listOf(DetailSectionId.TRACKS, DetailSectionId.STEAM))
        val b = DetailLayoutPolicy.encodeHidden(listOf(DetailSectionId.STEAM, DetailSectionId.TRACKS))
        assertEquals("同一集合必须得到同一个字符串", a, b)
        assertEquals("steam,tracks", a)
        assertEquals("", DetailLayoutPolicy.encodeHidden(emptySet()))
        assertEquals("", DetailLayoutPolicy.encodeHidden(setOf(DetailSectionId.TITLE)))
    }

    @Test
    fun `编码解码往返一致`() {
        val hidden = setOf(DetailSectionId.INFOBOX, DetailSectionId.EPISODES_RATING, DetailSectionId.ANITABI)
        val encoded = DetailLayoutPolicy.encodeHidden(hidden)
        assertEquals(hidden, DetailLayoutPolicy.hiddenFromEncoded(encoded))
        assertEquals(encoded, DetailLayoutPolicy.encodeHidden(DetailLayoutPolicy.hiddenFromEncoded(encoded)))
    }

    @Test
    fun `可见性判定_核心永远可见`() {
        val hidden = DetailLayoutPolicy.hiddenFromEncoded("steam,title")
        assertFalse(DetailLayoutPolicy.isVisible(DetailSectionId.STEAM, hidden))
        assertTrue(DetailLayoutPolicy.isVisible(DetailSectionId.VNDB, hidden))
        assertTrue(DetailLayoutPolicy.isVisible(DetailSectionId.TITLE, hidden))
        assertTrue(DetailSectionId.entries.all { DetailLayoutPolicy.isVisible(it, emptySet()) })
    }

    @Test
    fun `切换隐藏_核心部件返回原集合`() {
        val empty = emptySet<DetailSectionId>()
        assertEquals(setOf(DetailSectionId.STEAM), DetailLayoutPolicy.toggleHidden(empty, DetailSectionId.STEAM))
        assertEquals(empty, DetailLayoutPolicy.toggleHidden(setOf(DetailSectionId.STEAM), DetailSectionId.STEAM))
        assertEquals(empty, DetailLayoutPolicy.toggleHidden(empty, DetailSectionId.TITLE))
    }

    // ==================== 第二步：顺序 ====================

    @Test
    fun `空顺序就是默认顺序`() {
        assertEquals(DetailLayoutPolicy.defaultOrder, DetailLayoutPolicy.resolveOrder(null))
        assertEquals(DetailLayoutPolicy.defaultOrder, DetailLayoutPolicy.resolveOrder(""))
        assertTrue(DetailLayoutPolicy.isDefaultOrder(""))
        assertTrue(DetailLayoutPolicy.isDefaultOrder(null))
    }

    @Test
    fun `顺序解析_记住的部分在前_没提到的按默认顺序接在尾部`() {
        // 用户只把 vndb 提到最前
        val resolved = DetailLayoutPolicy.resolveOrder("vndb")
        assertEquals(DetailSectionId.VNDB, resolved.first())
        assertEquals(
            "其余部件保持默认相对顺序（这正是「新部件追加到默认尾部」）",
            expectedDefaultKeys.filter { it != "vndb" },
            resolved.drop(1).map { it.key },
        )
        assertEquals(DetailSectionId.entries.size, resolved.size)
    }

    @Test
    fun `顺序解析_未知key忽略_重复只取第一次_旧key归并`() {
        val resolved = DetailLayoutPolicy.resolveOrder("tracks,tracks,ghost_section,anilist_pending")
        assertEquals(DetailSectionId.TRACKS, resolved[0])
        assertEquals(DetailSectionId.ANILIST, resolved[1])
        // 没有丢部件，也没有重复
        assertEquals(DetailSectionId.entries.size, resolved.size)
        assertEquals(DetailSectionId.entries.size, resolved.toSet().size)
    }

    @Test
    fun `顺序编码_整条写出来_未知项被丢掉_缺失项补到尾部`() {
        val encoded = DetailLayoutPolicy.encodeOrder(
            listOf(DetailSectionId.TRACKS, DetailSectionId.COVER),
        )
        assertEquals("tracks,cover", encoded.split(",").take(2).joinToString(","))
        assertEquals(DetailSectionId.entries.size, encoded.split(",").size)
        // 往返一致
        assertEquals(
            DetailLayoutPolicy.resolveOrder(encoded),
            DetailLayoutPolicy.resolveOrder(DetailLayoutPolicy.encodeOrder(DetailLayoutPolicy.resolveOrder(encoded))),
        )
    }

    @Test
    fun `上移下移一位_越界原样返回`() {
        val defaultEncoded = DetailLayoutPolicy.encodeOrder(DetailLayoutPolicy.defaultOrder)

        // 默认顺序：cover, title, infobox, steam, ... → 把 steam 上移一位应该与 infobox 交换
        val up = DetailLayoutPolicy.moveSection(encodedOrder = "", id = DetailSectionId.STEAM, offset = -1)
        val upOrder = up.split(",")
        assertEquals(listOf("cover", "title", "steam", "infobox"), upOrder.take(4))

        // 下移同样只动一位
        val down = DetailLayoutPolicy.moveSection(defaultEncoded, DetailSectionId.STEAM, offset = 1)
        assertEquals(listOf("cover", "title", "infobox", "anilist", "steam"), down.split(",").take(5))

        // 已经在首位再上移 = 原样返回；已经在末尾再下移 = 原样返回
        assertEquals(defaultEncoded, DetailLayoutPolicy.moveSection(defaultEncoded, DetailSectionId.COVER, offset = -1))
        val lastId = DetailLayoutPolicy.defaultOrder.last()
        assertEquals(defaultEncoded, DetailLayoutPolicy.moveSection(defaultEncoded, lastId, offset = 1))
        // offset = 0 也不动
        assertEquals(defaultEncoded, DetailLayoutPolicy.moveSection(defaultEncoded, DetailSectionId.VNDB, offset = 0))
    }

    @Test
    fun `上移下移是可逆的`() {
        val up = DetailLayoutPolicy.moveSection("", DetailSectionId.VNDB, offset = -1)
        val back = DetailLayoutPolicy.moveSection(up, DetailSectionId.VNDB, offset = 1)
        assertEquals(DetailLayoutPolicy.encodeOrder(DetailLayoutPolicy.defaultOrder), back)
    }

    @Test
    fun `宽屏归属列沿用改造前的分栏`() {
        assertEquals(NirikoDetailPane.CONTENT, DetailSectionId.RATING.pane)
        assertEquals(NirikoDetailPane.CONTENT, DetailSectionId.TRACKS.pane)
        assertEquals(NirikoDetailPane.OVERVIEW, DetailSectionId.COVER.pane)
        assertEquals(NirikoDetailPane.OVERVIEW, DetailSectionId.COLLECTION_BTN.pane)
        // AniList / VNDB 区块在概览列（与改造前一致）
        assertEquals(NirikoDetailPane.OVERVIEW, DetailSectionId.ANILIST.pane)
        assertEquals(NirikoDetailPane.OVERVIEW, DetailSectionId.VNDB.pane)
    }
}
