package com.otakup.niriko.util

import com.otakup.niriko.data.model.EpisodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音乐曲目列表展示规则（F04）的单测。
 *
 * 锁的是三件事：类型过滤与碟片分组（不能因为重构把 SP 混进来）、
 * 折叠计数（大量曲目不得一次渲染）、以及**已听计数取交集**
 * （改造前是 `watchedTrackIds.size.coerceAtMost(total)`，集合里混入别的作品 id 时计数虚高）。
 */
class TrackListPolicyTest {

    private fun track(
        id: Long,
        sort: Double,
        disc: Int = 0,
        type: Int = 0,
        name: String = "曲目 $id",
    ) = EpisodeInfo(
        id = id,
        name = name,
        nameCn = null,
        desc = null,
        ep = sort,
        sort = sort,
        airdate = null,
        duration = null,
        disc = disc,
        type = type,
    )

    @Test
    fun `曲目类型只含本篇与OPED_不含SP`() {
        assertTrue(TrackListPolicy.isTrackType(0))
        assertTrue(TrackListPolicy.isTrackType(2))
        assertTrue(TrackListPolicy.isTrackType(3))
        assertFalse("SP 不在曲目列表里（保持改造前语义）", TrackListPolicy.isTrackType(1))
        assertFalse(TrackListPolicy.isTrackType(9))
    }

    @Test
    fun `按碟片分组_disc为零归一为第一碟_碟内按sort升序`() {
        val groups = TrackListPolicy.groupByDisc(
            listOf(
                track(id = 1, sort = 3.0, disc = 2),
                track(id = 2, sort = 1.0, disc = 0),
                track(id = 3, sort = 2.0, disc = 0),
                track(id = 4, sort = 1.0, disc = 2),
                track(id = 9, sort = 5.0, disc = 1, type = 1), // SP：必须被过滤掉
            ),
        )
        assertEquals(listOf(1, 2), groups.map { it.number })
        assertEquals("disc=0 归一到第 1 碟，且碟内按 sort 升序", listOf(2L, 3L), groups[0].tracks.map { it.id })
        assertEquals(listOf(4L, 1L), groups[1].tracks.map { it.id })
    }

    @Test
    fun `展平顺序等于渲染顺序`() {
        val rows = TrackListPolicy.flatten(
            TrackListPolicy.groupByDisc(
                listOf(
                    track(id = 1, sort = 2.0, disc = 0),
                    track(id = 2, sort = 1.0, disc = 0),
                    track(id = 3, sort = 1.0, disc = 2),
                ),
            ),
        )
        assertEquals(listOf(2L, 1L, 3L), rows.map { it.track.id })
        assertEquals(listOf(1, 1, 2), rows.map { it.disc })
    }

    @Test
    fun `折叠计数_默认只渲染上限并给出剩余数`() {
        val total = TrackListPolicy.PREVIEW_LIMIT + 7
        assertEquals(TrackListPolicy.PREVIEW_LIMIT, TrackListPolicy.visibleCount(total, expanded = false))
        assertEquals(7, TrackListPolicy.hiddenCount(total, expanded = false))
        assertEquals(total, TrackListPolicy.visibleCount(total, expanded = true))
        assertEquals(0, TrackListPolicy.hiddenCount(total, expanded = true))
    }

    @Test
    fun `曲目数不超过上限时不显示展开控件`() {
        assertFalse(TrackListPolicy.shouldShowExpandControl(TrackListPolicy.PREVIEW_LIMIT))
        assertTrue(TrackListPolicy.shouldShowExpandControl(TrackListPolicy.PREVIEW_LIMIT + 1))
        // 空列表与单曲
        assertEquals(0, TrackListPolicy.visibleCount(0, expanded = false))
        assertFalse(TrackListPolicy.shouldShowExpandControl(1))
    }

    @Test
    fun `非法上限不会渲染出负数或零行`() {
        // limit = 0 会被抬到 1：折叠状态下至少要看得见一行，否则界面像坏了
        assertEquals(1, TrackListPolicy.visibleCount(5, expanded = false, limit = 0))
        assertEquals(0, TrackListPolicy.hiddenCount(0, expanded = false))
        assertEquals(0, TrackListPolicy.visibleCount(-3, expanded = false))
    }

    @Test
    fun `visibleRows 只取前缀_不重排`() {
        val rows = TrackListPolicy.flatten(
            TrackListPolicy.groupByDisc(
                (1..20).map { track(id = it.toLong(), sort = it.toDouble()) },
            ),
        )
        val visible = TrackListPolicy.visibleRows(rows, expanded = false, limit = 5)
        assertEquals(5, visible.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), visible.map { it.track.id })
    }

    @Test
    fun `展开文案带剩余数量_展开后是收起`() {
        assertEquals("展开剩余 7 首", TrackListPolicy.expandLabel(hidden = 7, expanded = false))
        assertEquals("收起", TrackListPolicy.expandLabel(hidden = 0, expanded = true))
    }

    @Test
    fun `已听计数只数本列表里的曲目`() {
        val rows = TrackListPolicy.flatten(
            TrackListPolicy.groupByDisc(
                listOf(track(id = 1, sort = 1.0), track(id = 2, sort = 2.0), track(id = 3, sort = 3.0)),
            ),
        )
        // 集合里有 5 个 id，其中只有 1 个属于这个列表 —— 必须算 1，而不是被 coerce 成 3
        val watched = setOf(1L, 901L, 902L, 903L, 904L)
        assertEquals(1, TrackListPolicy.watchedCount(rows, watched))
        assertEquals(3, TrackListPolicy.watchedCount(rows, setOf(1L, 2L, 3L)))
        assertEquals(0, TrackListPolicy.watchedCount(rows, emptySet()))
    }

    @Test
    fun `标题带已听与总数`() {
        assertEquals("曲目（已听 0 / 0 首）", TrackListPolicy.headerText(0, 0))
        assertEquals("曲目（已听 3 / 12 首）", TrackListPolicy.headerText(3, 12))
    }
}
