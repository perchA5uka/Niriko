package com.otakup.niriko.util

import com.otakup.niriko.data.model.EpisodeInfo

/**
 * 音乐曲目列表的展示规则（F04，计划 §9.2）。
 *
 * 这些规则原先散在 `TrackListSection` 的组合代码里（分组、排序、类型过滤、计数），
 * 于是「已听几首」只能写成 `watchedTrackIds.size.coerceAtMost(totalCount)` 这种近似 ——
 * 集合里可能有别的作品的 id，也可能有被类型过滤掉的 SP。抽成纯函数后可以逐条断言。
 *
 * **大量曲目时不允许一次全部渲染**（§9.2 明确要求「使用折叠或分页」）：
 * 默认只渲染前 [PREVIEW_LIMIT] 首，其余用「展开剩余 N 首」按钮放出。
 * 计数与展开逻辑都在这里，界面只做渲染。
 */
object TrackListPolicy {

    /** 默认一次渲染的曲目上限（超过则折叠）。 */
    const val PREVIEW_LIMIT = 12

    /** 一个碟片的曲目（[number] 已把 disc=0 归一为 1）。 */
    data class Disc(val number: Int, val tracks: List<EpisodeInfo>)

    /** 展平后的一行：(碟片号, 曲目)。 */
    data class Row(val disc: Int, val track: EpisodeInfo)

    /**
     * 是否算「曲目」：本篇（0）、OP（2）、ED（3）。
     *
     * 与改造前**逐字一致**：SP（1）虽然能拿到标签，但确实不在这个列表里
     * （它在本篇集数之外，混进来会让「已听 X / Y 首」的分母不对）。这里保留现状并写明，
     * 不顺手改变既有语义。
     */
    fun isTrackType(type: Int): Boolean = type == 0 || type == 2 || type == 3

    /** 按碟片分组：disc<=0 归一为第 1 碟，碟片号升序，碟内按 sort 升序。 */
    fun groupByDisc(episodes: List<EpisodeInfo>): List<Disc> =
        episodes
            .filter { isTrackType(it.type) }
            .groupBy { if (it.disc > 0) it.disc else 1 }
            .toSortedMap(compareBy { it })
            .map { (disc, tracks) -> Disc(number = disc, tracks = tracks.sortedBy { it.sort }) }

    /** 展平成渲染顺序（碟片升序、碟内 sort 升序）。 */
    fun flatten(groups: List<Disc>): List<Row> =
        groups.flatMap { group -> group.tracks.map { Row(disc = group.number, track = it) } }

    /** 本次渲染多少行：折叠时是阈值与总数的小者。 */
    fun visibleCount(total: Int, expanded: Boolean, limit: Int = PREVIEW_LIMIT): Int {
        if (total <= 0) return 0
        val safeLimit = limit.coerceAtLeast(1)
        return if (expanded) total else minOf(total, safeLimit)
    }

    /** 还有多少行没渲染（折叠时才 > 0）。 */
    fun hiddenCount(total: Int, expanded: Boolean, limit: Int = PREVIEW_LIMIT): Int =
        (total - visibleCount(total, expanded, limit)).coerceAtLeast(0)

    /** 是否需要「展开 / 收起」控件：只有超过阈值时才需要。 */
    fun shouldShowExpandControl(total: Int, limit: Int = PREVIEW_LIMIT): Boolean = total > limit.coerceAtLeast(1)

    /** 本次实际渲染的行。 */
    fun visibleRows(rows: List<Row>, expanded: Boolean, limit: Int = PREVIEW_LIMIT): List<Row> =
        rows.take(visibleCount(rows.size, expanded, limit))

    /** 展开按钮的文案（折叠时给出剩余数量，展开时只写「收起」）。 */
    fun expandLabel(hidden: Int, expanded: Boolean): String =
        if (expanded) "收起" else "展开剩余 $hidden 首"

    /**
     * 已听数量：**只数本列表里出现过的曲目**。
     *
     * 改造前用 `watchedTrackIds.size.coerceAtMost(totalCount)`：当集合里混入其它作品的 id 时
     * 计数会虚高（被 coerce 掩盖成「看起来对」）。这里改成真正的交集。
     */
    fun watchedCount(rows: List<Row>, watchedTrackIds: Set<Long>): Int =
        rows.count { it.track.id in watchedTrackIds }

    /** 标题：「曲目（已听 X / Y 首）」。 */
    fun headerText(watched: Int, total: Int): String = "曲目（已听 $watched / $total 首）"
}
