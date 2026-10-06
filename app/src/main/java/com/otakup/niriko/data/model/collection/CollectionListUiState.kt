package com.otakup.niriko.data.model.collection

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.model.CollectionStats

/**
 * 导航条上的一个分区（F09）。
 *
 * @param memberCount 分区内作品数（**只数关系行**，不看这些作品是否还在收藏里 ——
 *   关系行才是这个分区的真实内容，收藏被删时由调用方负责清理成员）。
 */
data class LibraryFolderSummary(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val isCollapsed: Boolean,
    val memberCount: Int,
)

/**
 * 作品收藏列表 UI 快照。
 */
data class CollectionListUiState(
    val items: List<CollectionWithSubject> = emptyList(),
    val filter: CollectionFilter = CollectionFilter(),
    val stats: CollectionStats = CollectionStats(),
    val availableTags: List<TagInfo> = emptyList(),
    /** Steam 补充数据（已绑定游戏，subjectId → 扩展数据）。 */
    val steamGames: Map<Long, SteamGameEntity> = emptyMap(),
    /**
     * 首帧加载中（B4）：数据流尚未发出第一个快照，作品库应显示海报网格骨架而非空态。
     * 由 [com.otakup.niriko.viewmodel.CollectionViewModel] 的 initialValue 置 true，
     * 第一个真实快照到达后自然为 false。
     */
    val isLoading: Boolean = false,
)
