package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.model.CollectionStats
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.SteamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.collection.CollectionFilter
import com.otakup.niriko.data.model.collection.CollectionListUiState
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.data.model.collection.TagInfo

/**
 * 作品库 ViewModel：观察收藏列表，支持 keyword 搜索、状态过滤、排序。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CollectionViewModel(
    private val collectionRepository: CollectionRepository,
    private val steamRepository: SteamRepository? = null,
) : ViewModel() {

    private val _filter = MutableStateFlow(CollectionFilter())
    val filter: StateFlow<CollectionFilter> = _filter.asStateFlow()

    init {
        // Steam 补充：对收藏中的 GAME 条目触发一次自动匹配（内部已过滤已绑定，失败静默）
        steamRepository?.let { steam ->
            viewModelScope.launch {
                runCatching {
                    val games = collectionRepository.observeAllWithSubject().first()
                        .mapNotNull { it.subject }
                        .filter { it.type == SubjectType.GAME }
                    if (games.isNotEmpty()) steam.matchAndBind(games)
                }
            }
        }
    }

    val uiState: StateFlow<CollectionListUiState> = _filter
        .debounce(300)
        .flatMapLatest { f ->
            val source = if (f.keyword.isBlank()) {
                collectionRepository.observeAllWithSubject()
            } else {
                collectionRepository.observeByKeyword(f.keyword)
            }
            source.map { list ->
                val statusFiltered = if (f.status == null) list
                else list.filter { it.collection.status == f.status }

                val tagFiltered = if (f.selectedTags.isEmpty()) statusFiltered
                else statusFiltered.filter { item ->
                    f.selectedTags.all { tag -> item.collection.personalTags.contains(tag) }
                }

                val sorted = when (f.sort) {
                    SortOrder.UPDATE_TIME -> tagFiltered.sortedByDescending { it.collection.updateTime }
                    SortOrder.CREATE_TIME -> tagFiltered.sortedByDescending { it.collection.createTime }
                    SortOrder.MY_RATING -> tagFiltered.sortedByDescending { it.collection.rating ?: -1f }
                    SortOrder.TITLE -> tagFiltered.sortedBy {
                        val s = it.subject
                        if (s == null) "" else com.otakup.niriko.util.TitleResolver.resolve(s.titleCN, s.title).primary.lowercase()
                    }
                    SortOrder.WATCH_PROGRESS -> tagFiltered.sortedByDescending {
                        val s = it.subject
                        if (s == null) 0f
                        else {
                            val total = s.totalEpisodes
                            val watched = it.collection.watchedEpisodes
                            if (total == null || total <= 0 || watched == null) 0f
                            else watched.toFloat() / total
                        }
                    }
                }

                val ratingAvg = sorted.mapNotNull { it.collection.rating }.average()
                val stats = CollectionStats(
                    totalCount = sorted.size,
                    statusCounts = sorted.groupBy { it.collection.status }.mapValues { it.value.size },
                    averageRating = if (ratingAvg.isNaN() || ratingAvg <= 0.0) 0.0 else ratingAvg,
                    totalWatchedEpisodes = sorted.sumOf { it.collection.watchedEpisodes ?: 0 },
                    completionRate = if (sorted.isEmpty()) 0f
                    else sorted.count { it.collection.status == WatchStatus.COMPLETED }.toFloat() / sorted.size,
                )

                val tagCounts = mutableMapOf<String, Int>()
                sorted.forEach { item ->
                    item.collection.personalTags.forEach { tag ->
                        tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
                    }
                }
                val availableTags = tagCounts.entries
                    .sortedByDescending { it.value }
                    .map { TagInfo(name = it.key, count = it.value) }

                CollectionListUiState(
                    items = sorted,
                    filter = f,
                    stats = stats,
                    availableTags = availableTags,
                    steamGames = steamRepository?.getSupplements(sorted.mapNotNull { it.subject?.subjectId }) ?: emptyMap(),
                )
            }
        }
        // 收藏列表重算（含 Steam 补充查询）移到 Default 线程，避免阻塞主线程导致保存反馈延迟。
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CollectionListUiState(),
        )

    /** 更新筛选条件。 */
    fun updateFilter(transform: (CollectionFilter) -> CollectionFilter) {
        _filter.update(transform)
    }

    /** 快速标记收藏状态（长按卡片调用）。 */
    fun updateStatus(subjectId: Long, status: WatchStatus) {
        viewModelScope.launch {
            val existing = collectionRepository.getBySubjectId(subjectId) ?: return@launch
            collectionRepository.update(existing.copy(status = status))
        }
    }

    /** 批量改收藏状态（阶段 H）。 */
    fun batchUpdateStatus(subjectIds: List<Long>, status: WatchStatus) {
        if (subjectIds.isEmpty()) return
        viewModelScope.launch {
            subjectIds.forEach { id ->
                val existing = collectionRepository.getBySubjectId(id) ?: return@forEach
                collectionRepository.update(existing.copy(status = status))
            }
        }
    }

    /** 批量删除收藏（阶段 H）。 */
    fun batchDelete(subjectIds: List<Long>) {
        if (subjectIds.isEmpty()) return
        viewModelScope.launch {
            subjectIds.forEach { id -> collectionRepository.deleteBySubjectId(id) }
        }
    }
}

/** 通过 [CollectionRepository] 创建 [CollectionViewModel]。 */
class CollectionViewModelFactory(
    private val collectionRepository: CollectionRepository,
    private val steamRepository: SteamRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CollectionViewModel::class.java)) {
            return CollectionViewModel(collectionRepository, steamRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
