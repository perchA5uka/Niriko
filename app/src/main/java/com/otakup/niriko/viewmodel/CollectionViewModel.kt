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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

internal fun collectionStatusCounts(items: List<CollectionWithSubject>): Map<WatchStatus, Int> =
    items.groupingBy { it.collection.status }.eachCount()

/**
 * 作品库 ViewModel：观察收藏列表，支持 keyword 搜索、状态过滤、排序。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CollectionViewModel(
    private val collectionRepository: CollectionRepository,
    private val steamRepository: SteamRepository? = null,
    /** F09：作品库自定义分区。null = 该装配路径没有分区能力（分区条不显示）。 */
    private val libraryFolderDao: com.otakup.niriko.data.local.dao.LibraryFolderDao? = null,
) : ViewModel() {

    private val statusWriteMutex = Mutex()
    private val completionFeedback = CompletionEvents()
    val completionEvents = completionFeedback.events

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

    /** F09：分区条数据（含成员数与折叠态）。没有装配 DAO 时是空流。 */
    val folders: StateFlow<List<com.otakup.niriko.data.model.collection.LibraryFolderSummary>> =
        (libraryFolderDao?.observeFolders() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .map { rows ->
                rows.map { row ->
                    com.otakup.niriko.data.model.collection.LibraryFolderSummary(
                        id = row.id,
                        name = row.name,
                        sortOrder = row.sortOrder,
                        isCollapsed = row.isCollapsed,
                        memberCount = runCatching { libraryFolderDao?.memberCount(row.id) }.getOrNull() ?: 0,
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class LibrarySnapshot(
        val state: CollectionListUiState = CollectionListUiState(isLoading = true),
        val counts: Map<WatchStatus, Int> = emptyMap(),
    )

    private val snapshot: StateFlow<LibrarySnapshot> = combine(_filter, folders) { filter, _ -> filter }
        .debounce(300)
        .flatMapLatest { f ->
            val source = if (f.keyword.isBlank()) {
                collectionRepository.observeAllWithSubject()
            } else {
                collectionRepository.observeByKeyword(f.keyword)
            }
            source.map { list ->
                // F09：分区过滤在状态与标签之后、排序之前 —— 分区是「看哪一批作品」，
                // 与「按什么排」和「看什么状态」互不改变语义。
                val folderMemberIds = f.folderId?.let { folderId ->
                    runCatching { libraryFolderDao?.getSubjectIdsInFolder(folderId) }.getOrNull()?.toSet()
                }
                val folderFiltered = if (f.folderId == null || folderMemberIds == null) list
                else list.filter { item ->
                    item.subject?.subjectId?.let { it in folderMemberIds } ?: false
                }

                val tagFiltered = if (f.selectedTags.isEmpty()) folderFiltered
                else folderFiltered.filter { item ->
                    f.selectedTags.all { tag -> item.collection.personalTags.contains(tag) }
                }

                val counts = collectionStatusCounts(tagFiltered)
                val statusFiltered = if (f.status == null) tagFiltered
                else tagFiltered.filter { it.collection.status == f.status }
                val sorted = when (f.sort) {
                    SortOrder.UPDATE_TIME -> statusFiltered.sortedByDescending { it.collection.updateTime }
                    SortOrder.CREATE_TIME -> statusFiltered.sortedByDescending { it.collection.createTime }
                    SortOrder.MY_RATING -> statusFiltered.sortedByDescending { it.collection.rating ?: -1f }
                    SortOrder.TITLE -> statusFiltered.sortedBy {
                        val s = it.subject
                        if (s == null) "" else com.otakup.niriko.util.TitleResolver.resolve(s.titleCN, s.title).primary.lowercase()
                    }
                    SortOrder.WATCH_PROGRESS -> statusFiltered.sortedByDescending {
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

                LibrarySnapshot(CollectionListUiState(
                    items = sorted,
                    filter = f,
                    stats = stats,
                    availableTags = availableTags,
                    steamGames = steamRepository?.getSupplements(sorted.mapNotNull { it.subject?.subjectId }) ?: emptyMap(),
                ), counts)
            }
        }
        // 收藏列表重算（含 Steam 补充查询）移到 Default 线程，避免阻塞主线程导致保存反馈延迟。
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // B4：首帧即为「加载中」，作品库先渲染海报网格骨架；第一个真实快照到达后 isLoading 变 false。
            initialValue = LibrarySnapshot(),
        )

    val uiState: StateFlow<CollectionListUiState> = snapshot.map { it.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionListUiState(isLoading = true))

    /** Counts share the exact keyword/folder/tag query, before the selected status is applied. */
    val statusCounts: StateFlow<Map<WatchStatus, Int>> = snapshot.map { it.counts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 更新筛选条件。 */
    fun updateFilter(transform: (CollectionFilter) -> CollectionFilter) {
        _filter.update(transform)
    }

    /** 快速标记收藏状态（长按卡片调用）。 */
    fun updateStatus(subjectId: Long, status: WatchStatus) {
        viewModelScope.launch {
            statusWriteMutex.withLock {
                val existing = collectionRepository.getBySubjectId(subjectId) ?: return@withLock
                val written = collectionRepository.update(existing.copy(status = status))
                completionFeedback.onStatusWritten(subjectId, existing.status, status, written)
            }
        }
    }

    /**
     * 长按浮层进度快捷（计划 B1-2）：写回集进度或卷进度。
     *
     * 值由 [com.otakup.niriko.util.ProgressBumpPolicy] 在 UI 侧算好（含 0 下限与总集数上限），
     * 这里只做防御性兜底与落库。
     *
     * @param volume true = 写 watchedVolumes（书籍/漫画），false = 写 watchedEpisodes
     */
    fun setProgress(subjectId: Long, value: Int, volume: Boolean = false) {
        viewModelScope.launch {
            val existing = collectionRepository.getBySubjectId(subjectId) ?: return@launch
            val safe = value.coerceAtLeast(0)
            collectionRepository.update(
                existing.copy(
                    watchedEpisodes = if (volume) existing.watchedEpisodes else safe,
                    watchedVolumes = if (volume) safe else existing.watchedVolumes,
                    updateTime = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** 批量改收藏状态（阶段 H）。 */
    fun batchUpdateStatus(subjectIds: List<Long>, status: WatchStatus) {
        if (subjectIds.isEmpty()) return
        viewModelScope.launch {
            statusWriteMutex.withLock {
                var firstCompletion: Pair<Long, WatchStatus>? = null
                subjectIds.forEach { id ->
                    val existing = collectionRepository.getBySubjectId(id) ?: return@forEach
                    val written = collectionRepository.update(existing.copy(status = status))
                    if (written && existing.status != WatchStatus.COMPLETED &&
                        status == WatchStatus.COMPLETED && firstCompletion == null
                    ) {
                        firstCompletion = id to existing.status
                    }
                }
                firstCompletion?.let { (id, before) ->
                    completionFeedback.onStatusWritten(id, before, status, succeeded = true)
                }
            }
        }
    }

    /** 批量删除收藏（阶段 H）。 */
    fun batchDelete(subjectIds: List<Long>) {
        if (subjectIds.isEmpty()) return
        viewModelScope.launch {
            subjectIds.forEach { id -> collectionRepository.deleteBySubjectId(id) }
            // F09：收藏被删掉后，分区里指向它的成员关系必须一起清掉 ——
            // 否则分区成员数会显示一个点进去看不到的作品（幽灵成员）。
            libraryFolderDao?.let { dao ->
                runCatching {
                    subjectIds.forEach { subjectId ->
                        dao.getFolderIdsOfSubject(subjectId).forEach { folderId ->
                            // *AndTouch：让导航条上的成员数立刻跟着变（否则会出现幽灵计数）
                            dao.removeSubjectAndTouch(folderId, subjectId)
                        }
                    }
                }
            }
        }
    }

    // ===== F09：作品库自定义分区 =====

    /**
     * 查询某部作品当前所属的分区（「加入分区」弹层用）。
     *
     * 异步回调而不是返回值：真正需要它的地方是对话框，它在组合里打开、
     * 不可能为了一个集合去做阻塞查询。回调在查询完成后于主线程触发。
     */
    fun folderIdsOf(subjectId: Long, onResult: (Set<Long>) -> Unit) {
        val dao = libraryFolderDao ?: run { onResult(emptySet()); return }
        viewModelScope.launch {
            val ids = runCatching { dao.getFolderIdsOfSubject(subjectId).toSet() }.getOrDefault(emptySet())
            onResult(ids)
        }
    }

    /** 选择要查看的分区；null = 全部作品。 */
    fun selectFolder(folderId: Long?) {
        _filter.update { it.copy(folderId = folderId) }
    }

    /**
     * 新建分区，名字按首尾去空白；空名不创建。
     *
     * 新分区的 id 是 Room 分配的自增主键，必须**落库之后**才知道 —— 因此用回调而不是返回值：
     * 调用方常见的下一步是「新建后立刻切到这个分区」，那需要真实 id，
     * 不能靠猜测。回调在 IO 完成后于主线程调用（viewModelScope 默认 Main）。
     */
    fun createFolder(name: String, onCreated: (Long) -> Unit = {}) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val dao = libraryFolderDao ?: return
        viewModelScope.launch {
            runCatching {
                dao.insertFolder(
                    com.otakup.niriko.data.local.entity.LibraryFolderEntity(
                        name = clean,
                        sortOrder = dao.maxSortOrder() + 1,
                    ),
                )
            }.getOrNull()?.let(onCreated)
        }
    }

    /** 改名（空名忽略）。 */
    fun renameFolder(folderId: Long, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch { runCatching { libraryFolderDao?.renameFolder(folderId, clean) } }
    }

    /** 删除分区（只删关系与分区行，绝不触碰作品与收藏）。 */
    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            runCatching { libraryFolderDao?.deleteFolder(folderId) }
            // 正在看的就是这个分区 → 回到「全部」，否则会停在一个已不存在的筛选上
            _filter.update { if (it.folderId == folderId) it.copy(folderId = null) else it }
        }
    }

    /**
     * 调整分区顺序（§10.2 第 2 条「排序」）。
     *
     * 做法是「整表重排」而不是「两行交换」：只交换相邻两行时，一旦中途失败（进程被杀、
     * 一次写入抛错）就会留下两行相同的 sortOrder，导航条顺序会随机。整表重排每次
     * 都写成 0..n-1，因此任何一次成功写入之后状态都是自洽的。
     */
    fun moveFolder(folderId: Long, offset: Int) {
        if (offset == 0) return
        viewModelScope.launch {
            runCatching {
                val dao = libraryFolderDao ?: return@runCatching
                val current = dao.getFolders()
                val from = current.indexOfFirst { it.id == folderId }
                if (from < 0) return@runCatching
                val to = (from + offset).coerceIn(0, current.lastIndex)
                if (to == from) return@runCatching
                val reordered = current.toMutableList().apply {
                    add(to, removeAt(from))
                }
                reordered.forEachIndexed { index, folder ->
                    if (folder.sortOrder != index) dao.setSortOrder(folder.id, index)
                }
            }
        }
    }

    /**
     * 清理「收藏里已经不存在的作品」在分区里的成员关系（§10.2 第 6 条的边界）。
     *
     * 分区只随 JSON 备份迁移、不随 WebDAV 同步；而 WebDAV 下载会**整体重写** collections 表。
     * 若不清理，同步之后就出现「分区里有 12 部，点进去只有 9 部」的幽灵成员。
     */
    fun pruneFolderMembersNotInCollections() {
        viewModelScope.launch {
            runCatching {
                val dao = libraryFolderDao ?: return@runCatching
                val alive = collectionRepository.observeAllWithSubject().first()
                    .mapNotNull { it.subject?.subjectId }
                    .toSet()
                dao.getFolders().forEach { folder ->
                    val members = dao.getSubjectIdsInFolder(folder.id)
                    val dead = members.filter { it !in alive }
                    if (dead.isNotEmpty()) {
                        dao.removeSubjectsAndTouch(folder.id, dead)
                    }
                }
            }
        }
    }

    /** 折叠/展开分区（持久化）。 */
    fun setFolderCollapsed(folderId: Long, collapsed: Boolean) {
        viewModelScope.launch { runCatching { libraryFolderDao?.setCollapsed(folderId, collapsed) } }
    }

    /** 把作品加入分区（幂等）。 */
    fun addToFolder(folderId: Long, subjectId: Long) {
        viewModelScope.launch {
            runCatching {
                val dao = libraryFolderDao ?: return@runCatching
                dao.addSubjectAndTouch(
                    com.otakup.niriko.data.local.entity.LibraryFolderSubjectEntity(
                        folderId = folderId,
                        subjectId = subjectId,
                        sortOrder = dao.memberCount(folderId),
                    ),
                )
            }
        }
    }

    /** 批量加入分区（多选批量操作）：全局按 subjectId 去重（§10.2）。 */
    fun addToFolder(folderId: Long, subjectIds: List<Long>) {
        val ids = subjectIds.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val dao = libraryFolderDao ?: return@runCatching
                val base = dao.memberCount(folderId)
                dao.addSubjectsAndTouch(
                    folderId = folderId,
                    members = ids.mapIndexed { index, subjectId ->
                        com.otakup.niriko.data.local.entity.LibraryFolderSubjectEntity(
                            folderId = folderId,
                            subjectId = subjectId,
                            sortOrder = base + index,
                        )
                    },
                )
            }
        }
    }

    /** 从分区移除作品。 */
    fun removeFromFolder(folderId: Long, subjectId: Long) {
        viewModelScope.launch {
            runCatching { libraryFolderDao?.removeSubjectAndTouch(folderId, subjectId) }
        }
    }
}

/** 通过 [CollectionRepository] 创建 [CollectionViewModel]。 */
class CollectionViewModelFactory(
    private val collectionRepository: CollectionRepository,
    private val steamRepository: SteamRepository? = null,
    private val libraryFolderDao: com.otakup.niriko.data.local.dao.LibraryFolderDao? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CollectionViewModel::class.java)) {
            return CollectionViewModel(collectionRepository, steamRepository, libraryFolderDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
