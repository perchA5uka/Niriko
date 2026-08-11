package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.data.remote.SubjectRelationInfo
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.SteamRepository
import com.otakup.niriko.data.repository.SubjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 作品详情 UI 快照。
 */
data class SubjectDetailUiState(
    // 作品信息
    val subject: com.otakup.niriko.data.local.entity.SubjectEntity? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    // 收藏状态
    val isInCollection: Boolean = false,
    val collectionId: Long = 0L,
    val currentStatus: WatchStatus = WatchStatus.PLAN_TO_WATCH,
    val isUpdating: Boolean = false,
    // 个人记录
    val watchedEpisodes: Int? = null,
    val myRating: Float? = null,
    val personalTags: List<String> = emptyList(),
    val personalImpression: String? = null,
    val remark: String? = null,
    /** 已听曲目 id 集合（音乐类型逐首勾选）。 */
    val watchedTrackIds: Set<Long> = emptySet(),
    val startDate: LocalDate? = null,
    val finishDate: LocalDate? = null,
    // 扩展数据（角色/声优/剧集）
    val characters: List<CharacterInfo> = emptyList(),
    val staff: List<StaffInfo> = emptyList(),
    val episodes: List<EpisodeInfo> = emptyList(),
    val isExtendedLoading: Boolean = false,
    // 关联条目（前后传/版本/系列）
    val relations: List<SubjectRelationInfo> = emptyList(),
    // 条目 infobox（艺术家/发行商/发售日期等）
    val infoBox: List<InfoBoxEntry> = emptyList(),
    // 评分分布（1-10 分各档票数）
    val ratingDistribution: Map<Int, Int> = emptyMap(),
    // Steam 补充数据（游戏商业信息：价格/在线/开发商等）
    val steam: com.otakup.niriko.data.local.entity.SteamGameEntity? = null,
    // Snackbar 消息
    val snackbarMessage: String? = null,
)

/**
 * 作品详情 ViewModel。
 */
class SubjectDetailViewModel(
    private val subjectRepository: SubjectRepository,
    private val collectionRepository: CollectionRepository,
    private val remoteDataSource: SubjectRemoteDataSource,
    private val subjectId: Long,
    private val context: android.content.Context,
    private val steamRepository: SteamRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectDetailUiState())
    val uiState: StateFlow<SubjectDetailUiState> = _uiState.asStateFlow()

    /** 保存原始 createTime，避免更新时被 System.currentTimeMillis() 覆盖。 */
    private var originalCreateTime: Long = 0L

    /** 收藏写操作计数：loadCollectionState 返回过期快照时据此放弃覆盖（防收藏状态竞态）。 */
    private var collectionMutation = 0

    init { loadSubject() }

    /** 重新加载作品详情（供重试按钮使用）。 */
    fun retry() = loadSubject()

    /**
     * 加载作品详情 — 缓存优先 + 后台刷新。
     *
     * 1. 先尝试从本地缓存获取（立即返回）
     * 2. 如有网络，后台静默刷新并更新 UI
     * 3. 无缓存时从远程获取
     */
    private fun loadSubject() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Step 1: 读缓存（不阻塞网络）
            val subject = withContext(Dispatchers.IO) {
                subjectRepository.getById(subjectId)
            }

            if (subject != null) {
                // 有缓存 → 立即展示，后台刷新
                _uiState.update { it.copy(
                    subject = subject, isLoading = false, isRefreshing = true,
                ) }
                loadCollectionState(subject)

                // Step 2: 后台获取最新数据
                try {
                    withContext(Dispatchers.IO) {
                        subjectRepository.getDetail(subjectId)
                    }
                } catch (_: Exception) {
                    // 远程详情解析失败（如 infobox 序列化异常）→ 保留缓存，不闪退
                }
                // 重新读取更新后的缓存
                val fresh = withContext(Dispatchers.IO) {
                    subjectRepository.getById(subjectId)
                }
                if (fresh != null) {
                    _uiState.update { it.copy(subject = fresh, isRefreshing = false) }
                }

                // Step 3: 并行加载扩展数据（角色/制作人员/剧集）
                loadExtendedData()

            } else {
                // 无缓存 → 从远程获取
                val fetched = try {
                    withContext(Dispatchers.IO) {
                        subjectRepository.getDetail(subjectId)
                    }
                } catch (_: Exception) {
                    null
                }
                if (fetched == null) {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = "无法加载作品详情") }
                    return@launch
                }

                _uiState.update { it.copy(subject = fetched, isLoading = false) }
                loadCollectionState(fetched)

                // Step 3: 并行加载扩展数据
                loadExtendedData()
            }
        }
    }

    /** 加载收藏状态。 */
    private suspend fun loadCollectionState(subject: com.otakup.niriko.data.local.entity.SubjectEntity) {
        val mark = collectionMutation
        val existing = withContext(Dispatchers.IO) {
            collectionRepository.getBySubjectId(subjectId)
        }
        // 查询期间用户已执行收藏写操作 → 放弃过期快照，避免覆盖最新状态
        if (collectionMutation != mark) return
        originalCreateTime = existing?.createTime ?: System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isInCollection = existing != null,
                collectionId = existing?.id ?: 0L,
                currentStatus = existing?.status ?: WatchStatus.PLAN_TO_WATCH,
                watchedEpisodes = existing?.watchedEpisodes,
                myRating = existing?.rating,
                personalTags = existing?.personalTags ?: emptyList(),
                personalImpression = existing?.personalImpression,
                remark = existing?.remark,
                watchedTrackIds = existing?.watchedTrackIds?.toSet() ?: emptySet(),
                startDate = existing?.startDate,
                finishDate = existing?.finishDate,
            )
        }
    }

    /** 并行加载扩展数据（角色、制作人员、剧集）。 */
    private suspend fun loadExtendedData() {
        _uiState.update { it.copy(isExtendedLoading = true) }
        withContext(Dispatchers.IO) {
            coroutineScope {
                val charactersDeferred = async {
                    try { remoteDataSource.getCharacters(subjectId) } catch (_: Exception) { emptyList() }
                }
                val staffDeferred = async {
                    try { remoteDataSource.getStaff(subjectId) } catch (_: Exception) { emptyList() }
                }
                val episodesDeferred = async {
                    try { remoteDataSource.getEpisodes(subjectId) } catch (_: Exception) { emptyList() }
                }
                val distDeferred = async {
                    try { remoteDataSource.getRatingDistribution(subjectId) } catch (_: Exception) { emptyMap<Int, Int>() }
                }
                val relationsDeferred = async {
                    try { remoteDataSource.getSubjectRelations(subjectId) } catch (_: Exception) { emptyList() }
                }
                val infoBoxDeferred = async {
                    try { remoteDataSource.getInfoBox(subjectId) } catch (_: Exception) { emptyList() }
                }
                val biliDeferred = async {
                    try { fetchAndPersistBilibiliRating() } catch (_: Exception) { Unit }
                }
                val steamDeferred = async {
                    try { loadSteamSupplement() } catch (_: Exception) { Unit }
                }
                val characters = charactersDeferred.await()
                val staff = staffDeferred.await()
                val episodes = episodesDeferred.await()
                val ratingDistribution = distDeferred.await()
                val relations = relationsDeferred.await()
                val infoBox = infoBoxDeferred.await()
                biliDeferred.await()
                steamDeferred.await()
                _uiState.update {
                    it.copy(
                        characters = characters,
                        staff = staff,
                        episodes = episodes,
                        ratingDistribution = ratingDistribution,
                        relations = relations,
                        infoBox = infoBox,
                        isExtendedLoading = false,
                    )
                }
            }
        }
    }

    /**
     * 拉取 Bilibili 社区评分并写回本地(lib 缓存刷新用)。
     * - 通过 bilibili_site_map 查 bgmId → season_id(大陆优先,港澳台兜底)
     * - 命中后调 BilibiliRatingClient,成功后 persist 到 subjects 表
     * - 任何失败静默忽略(评分是补充信息,不影响主流程)
     */
    private suspend fun fetchAndPersistBilibiliRating() {
        val current = _uiState.value.subject
        val bgmId = current?.subjectId ?: subjectId

        val rating = com.otakup.niriko.data.remote.bilibili.BilibiliRatingClient
            .fetchRatingByBgmId(context.applicationContext, bgmId)
            ?: return

        val seasonId = com.otakup.niriko.data.remote.bilibili.BilibiliSiteMap
            .seasonId(context.applicationContext, bgmId)
            ?: com.otakup.niriko.data.remote.bilibili.BilibiliSiteMap
                .seasonId(context.applicationContext, bgmId, com.otakup.niriko.data.remote.bilibili.BilibiliSiteMap.Region.HK_MO_TW)
            ?: return

        val base = subjectRepository.getById(bgmId) ?: current ?: return
        subjectRepository.upsert(
            base.copy(
                biliScore = rating.score.toFloat(),
                biliRatingTotal = rating.count,
                biliSeasonId = seasonId,
            )
        )
        // 刷新 UI 快照(local 已更新 → 读一次最新)
        val fresh = subjectRepository.getById(bgmId)
        if (fresh != null) {
            _uiState.update { it.copy(subject = fresh) }
        }
    }

    /**
     * 拉取 Steam 补充数据（仅 GAME 类型且已绑定时有效）。
     * - 30 分钟缓存内直接读库；过期自动拉取 appdetails + 当前游玩人数并落库
     * - 失败静默（Steam 是补充信息，不影响主流程）
     */
    private suspend fun loadSteamSupplement() {
        val steam = steamRepository ?: return
        val current = _uiState.value.subject ?: return
        if (current.type != com.otakup.niriko.data.model.SubjectType.GAME) return
        val game = steam.getSupplement(subjectId) ?: return
        _uiState.update { it.copy(steam = game) }
    }

    fun addToCollection() {
        if (_uiState.value.isUpdating) return
        collectionMutation++
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val newId = withContext(Dispatchers.IO) {
                collectionRepository.add(CollectionEntity(subjectId = subjectId, status = WatchStatus.PLAN_TO_WATCH))
            }
            if (newId > 0) {
                originalCreateTime = System.currentTimeMillis()
                _uiState.update { it.copy(isInCollection = true, collectionId = newId, currentStatus = WatchStatus.PLAN_TO_WATCH, isUpdating = false) }
            } else {
                _uiState.update { it.copy(isUpdating = false) }
            }
        }
    }

    fun updateStatus(newStatus: WatchStatus) {
        val s = _uiState.value
        if (!s.isInCollection || s.collectionId == 0L || s.isUpdating) return
        collectionMutation++
        val total = when (s.subject?.type) {
            com.otakup.niriko.data.model.SubjectType.ANIME,
            com.otakup.niriko.data.model.SubjectType.REAL -> s.subject?.totalEpisodes
            com.otakup.niriko.data.model.SubjectType.BOOK,
            com.otakup.niriko.data.model.SubjectType.MANGA -> s.subject?.volumes ?: s.subject?.totalEpisodes
            else -> null
        }
        val newProgress = when (newStatus) {
            WatchStatus.PLAN_TO_WATCH -> null
            WatchStatus.COMPLETED -> total?.takeIf { it > 0 }
            else -> s.watchedEpisodes
        }
        val newStart = if (newStatus == WatchStatus.WATCHING && s.startDate == null) LocalDate.now() else s.startDate
        val newFinish = if (newStatus == WatchStatus.COMPLETED && s.finishDate == null) LocalDate.now() else s.finishDate
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            withContext(Dispatchers.IO) {
                collectionRepository.update(CollectionEntity(
                    id = s.collectionId, subjectId = subjectId, status = newStatus,
                    watchedEpisodes = newProgress, rating = s.myRating,
                    personalTags = s.personalTags, personalImpression = s.personalImpression,
                    remark = s.remark, startDate = newStart, finishDate = newFinish,
                    watchedTrackIds = s.watchedTrackIds.toList(),
                    createTime = originalCreateTime, updateTime = System.currentTimeMillis(),
                ))
            }
            _uiState.update { it.copy(
                currentStatus = newStatus, watchedEpisodes = newProgress,
                startDate = newStart, finishDate = newFinish, isUpdating = false,
            ) }
        }
    }

    fun removeFromCollection() {
        val s = _uiState.value
        if (!s.isInCollection || s.isUpdating) return
        collectionMutation++
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            withContext(Dispatchers.IO) { collectionRepository.deleteBySubjectId(subjectId) }
            _uiState.update { it.copy(isInCollection = false, collectionId = 0L, currentStatus = WatchStatus.PLAN_TO_WATCH, isUpdating = false,
                watchedEpisodes = null, myRating = null, personalTags = emptyList(), personalImpression = null, remark = null, watchedTrackIds = emptySet(), startDate = null, finishDate = null) }
        }
    }

    /** 保存所有个人记录字段。 */
    fun saveRecord(
        watchedEpisodes: Int?,
        myRating: Float?,
        personalTags: List<String>,
        personalImpression: String?,
        startDate: LocalDate?,
        finishDate: LocalDate?,
        watchedTrackIds: Set<Long> = emptySet(),
    ) {
        val s = _uiState.value
        if (!s.isInCollection || s.collectionId == 0L || s.isUpdating) return
        collectionMutation++
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            withContext(Dispatchers.IO) {
                // 音乐类型：watchedEpisodes 与已听曲目集合保持同步（统计兼容）
                val isMusic = s.subject?.type == com.otakup.niriko.data.model.SubjectType.MUSIC
                val finalWatched = if (isMusic) watchedTrackIds.size else watchedEpisodes
                collectionRepository.update(CollectionEntity(
                    id = s.collectionId, subjectId = subjectId, status = s.currentStatus,
                    watchedEpisodes = finalWatched, rating = myRating,
                    personalTags = personalTags, personalImpression = personalImpression,
                    remark = s.remark, startDate = startDate, finishDate = finishDate,
                    watchedTrackIds = watchedTrackIds.toList(),
                    createTime = originalCreateTime, updateTime = System.currentTimeMillis(),
                ))
            }
            _uiState.update { it.copy(
                watchedEpisodes = watchedEpisodes, myRating = myRating,
                personalTags = personalTags, personalImpression = personalImpression,
                startDate = startDate, finishDate = finishDate,
                watchedTrackIds = watchedTrackIds,
                isUpdating = false, snackbarMessage = "记录已保存",
            )}
        }
    }
}

class SubjectDetailViewModelFactory(
    private val subjectRepository: SubjectRepository,
    private val collectionRepository: CollectionRepository,
    private val remoteDataSource: SubjectRemoteDataSource,
    private val subjectId: Long,
    private val context: android.content.Context,
    private val steamRepository: SteamRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubjectDetailViewModel::class.java)) {
            return SubjectDetailViewModel(
                subjectRepository, collectionRepository, remoteDataSource, subjectId,
                context.applicationContext, steamRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
