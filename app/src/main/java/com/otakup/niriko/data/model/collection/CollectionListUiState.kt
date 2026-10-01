package com.otakup.niriko.data.model.collection

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.model.CollectionStats

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
