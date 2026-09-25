package com.otakup.niriko.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
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
import com.otakup.niriko.data.repository.VndbRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

private const val TAG = "SubjectDetailVM"

/**
 * 自动写库的置信度阈值（第 4 轮 D）。
 *
 * 用户诉求是「该匹配上的要匹配上」（《魔法少女的魔女审判》此前匹配不上），
 * 但过去的教训是**不能因此放开到 0.7**——同世界观作品标题相似度高，会顶掉正主。
 * 0.92 只放过「标题完全一致」或「infobox 明确 ID」这类几乎确定的匹配。
 */
private const val AUTO_BIND_THRESHOLD = 0.92f

/**
 * 豆瓣自动绑定的置信度阈值（第 4 轮 F）。
 *
 * 比 VNDB/TMDb 的 0.92 略低，但有额外门槛：**年份必须一致**（见 searchDoubanCandidates）。
 * 豆瓣上同名条目多（不同年份的改编、同名剧集），单看标题相似度容易错绑，
 * 加上年份条件后 0.86 已经足够安全。
 */
private const val DOUBAN_AUTO_BIND_THRESHOLD = 0.86f

/**
 * 从文本里抠豆瓣条目 id 的正则。
 *
 * 覆盖三种链接形态与裸 id：
 * - `https://movie.douban.com/subject/1292052/`
 * - `https://www.douban.com/game/1234567/`
 * - `https://book.douban.com/subject/1084336/`
 */
private val DOUBAN_ID_REGEX = Regex("""(?:subject|game|movie|book|music)/(\d{5,12})""")

/**
 * AniList id 提取（第 4 轮 D 的手动兜底）。
 *
 * 覆盖 `12345`、`https://anilist.co/anime/12345`、`anime/12345` 三种写法：
 * 都是「取第一段数字」。刻意不用 `\b`（Unicode 模式下对全角字符行为不直观）。
 */
private val ANILIST_ID_REGEX = Regex("""(\d{1,8})""")

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
    /** 卷进度（书籍/漫画）。阶段 B。 */
    val watchedVolumes: Int? = null,
    /** 私密收藏标记（阶段 B）。 */
    val isPrivate: Boolean = false,
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
    // Steam 成就进度（解锁数/总数；隐私未公开或失败为 null）
    val achievements: com.otakup.niriko.data.remote.steam.SteamAchievements? = null,
    // Steam 活跃玩家排名（未上榜/失败为 null）
    val chartRank: com.otakup.niriko.data.remote.steam.SteamChartEntry? = null,
    // VNDB 补充数据（视觉小说信息：绑定 + 详情）
    val vndbBinding: com.otakup.niriko.data.local.entity.VndbBindingEntity? = null,
    val vndbDetail: com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto? = null,
    /**
     * VNDB 关联作品（前作/续作/同世界观），第 4 轮 E 新增。
     *
     * 存在的意义：某作品匹配不上时，它的同世界观作往往匹配得上——
     * 有了关系链，用户能顺着已绑定的条目反查到未匹配的条目 id 再手动绑定。
     */
    val vndbRelations: List<com.otakup.niriko.data.repository.VndbRepository.RelationWithTitle> = emptyList(),
    /** 每条 VNDB 候选的匹配理由（externalId → 理由列表），第 4 轮 D。 */
    val vndbCandidateReasons: Map<String, List<String>> = emptyMap(),
    /** 上次查询命中的原始候选条数（用于显示「找到 N 条，展示 M 条」）。 */
    val vndbRawMatchCount: Int = 0,
    /** 自动绑定成功时的匹配理由（UI 显示「为什么绑定了这条」）。 */
    val vndbMatchReasons: List<String> = emptyList(),
    /** 手动入口的一行反馈（VNDB 搜索/粘贴 ID 的结果）。 */
    val vndbManualMessage: String? = null,
    /** VNDB 候选（未绑定 GAME 条目：按标题搜索的 Top 候选，供手动绑定）。 */
    val vndbCandidates: List<com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto> = emptyList(),
    // AniList 补充数据（不限制类型：绑定 + 详情，动画/漫画/游戏均可）
    val anilistBinding: com.otakup.niriko.data.local.entity.AniListBindingEntity? = null,
    val anilistDetail: com.otakup.niriko.data.remote.game.GameItemDetail? = null,
    /** AniList 独有富信息（热度/趋势/排名/下一集等）。 */
    val anilistRichDetail: com.otakup.niriko.data.model.AniListRichDetail? = null,
    /** AniList 候选（未绑定条目：按标题搜索的 Top 候选，供手动绑定）。 */
    val anilistCandidates: List<com.otakup.niriko.data.remote.game.GameItem> = emptyList(),
    /** AniList 手动入口（搜索/粘贴 id）的一行反馈，第 4 轮 D。 */
    val anilistManualMessage: String? = null,
    // 圣地巡礼（Anitabi，阶段 K）
    val anitabiCity: String? = null,
    val anitabiPoints: List<com.otakup.niriko.data.remote.anitabi.AnitabiLitePoint> = emptyList(),
    val anitabiPointsLength: Int = 0,
    val anitabiImagesLength: Int = 0,
    // 猜你喜欢（阶段 F：本地 tag 共现）
    val guessYouLike: List<com.otakup.niriko.data.local.entity.SubjectEntity> = emptyList(),

    // ===== 权威评分 + 每集评分（阶段 0–1）=====
    /** 统一权威评分（本地 Bangumi/Bilibili + 远端 TMDb/IMDb/Steam 好评率/IGDB/... 合并）。 */
    val externalRatings: List<com.otakup.niriko.data.remote.rating.ExternalRating> = emptyList(),
    /** Steam Metacritic 字段（不占评分源额度，独立展示）。 */
    val steamMetacritic: com.otakup.niriko.data.remote.rating.ExternalRating? = null,
    /** 手动录入的权威机构成绩（Fami通 / Billboard / Oricon 等无 API 的机构）。 */
    val manualAwards: List<com.otakup.niriko.data.local.entity.ManualAwardEntity> = emptyList(),
    /** 每集评分：epId → sourceId → 实体。 */
    val episodeRatings: Map<Long, Map<String, com.otakup.niriko.data.local.entity.EpisodeRatingEntity>> = emptyMap(),
    /** TMDb 每集评分加载结果（含「N 集可对齐 / M 集未匹配」的可解释状态）。 */
    val episodeRatingLoad: com.otakup.niriko.data.repository.EpisodeRatingRepository.TmdbEpisodeLoad? = null,
    /** IMDb 逐集是否正在加载（该链路请求多，需进度反馈）。 */
    val imdbEpisodesLoading: Boolean = false,
    /** 上一次 IMDb 逐集加载的结果（含 OMDb 季集兜底计数，第 4 轮 H）。 */
    val imdbEpisodeLoad: com.otakup.niriko.data.repository.EpisodeRatingRepository.ImdbEpisodeLoad? = null,
    /**
     * 「加载 IMDb 逐集评分」入口是否可用（修复 BUG-2：此前只看绑定，设置开关形同虚设）。
     * = 权威评分总开关 && IMDb 逐集开关 && 已配 OMDb key。
     */
    val imdbEpisodeEntryEnabled: Boolean = false,
    /**
     * IMDb 逐集的**可解释状态**（第 4 轮 H）。
     *
     * 改造前任一条件不满足就整块不渲染且不说原因（用户反馈「配了 OMDb 但不可用，
     * 也不知道缺什么」）。现在把缺口逐项列出来，并提供一键跳设置。
     */
    val imdbEntry: com.otakup.niriko.data.repository.EpisodeRatingRepository.ImdbEntryStatus? = null,
    /** 我的每集评分：epId → 分数（0-10）。 */
    val myEpisodeRatings: Map<Long, Float> = emptyMap(),
    /** TMDb 绑定（未绑定为 null）。 */
    val tmdbBinding: com.otakup.niriko.data.local.entity.SubjectExternalIdEntity? = null,
    /** TMDb 候选（未绑定时按标题搜索，供用户确认）。 */
    val tmdbCandidates: List<com.otakup.niriko.data.remote.rating.RatingCandidate> = emptyList(),
    /** TMDb 剧集详情（季列表 / 播放状态等）。 */
    val tmdbDetail: com.otakup.niriko.data.remote.tmdb.dto.TmdbTvDetailDto? = null,
    /** TMDb 剧照（背景图，进「剧照」区）。 */
    val tmdbBackdrops: List<com.otakup.niriko.data.remote.tmdb.dto.TmdbImageDto> = emptyList(),

    // ===== TMDb 覆盖与手动入口（第 4 轮 C）=====
    /** TMDb 覆盖此作品类型（含 GAME/BOOK/MUSIC 开关），决定整块是否渲染。 */
    val tmdbSupported: Boolean = false,
    /** 绑定走 movie 端点时的 TMDb movie id（与 tmdbBinding 互斥）。 */
    val tmdbMovieBinding: com.otakup.niriko.data.local.entity.SubjectExternalIdEntity? = null,
    /** 手动搜索 TMDb 的关键词输入（用户可在候选为空时自己搜）。 */
    val tmdbManualQuery: String = "",
    val tmdbManualLoading: Boolean = false,
    /** 手动入口的一行反馈（成功/失败/DM 提示，不再静默）。 */
    val tmdbManualMessage: String? = null,
    /** 粘贴 TMDb ID/链接后待用户确认的候选（不直接写库）。 */
    val tmdbPastedCandidate: com.otakup.niriko.data.remote.rating.RatingCandidate? = null,

    /** 每集剧照 URL：epId → URL。 */
    val episodeStills: Map<Long, String> = emptyMap(),
    /** 评分争议度标签（由 Bangumi 评分分布算标准差，见 RatingInsights）。 */
    val ratingDispute: String? = null,
    /** 本地库内同类型百分位（诚实口径：基于用户自己的收藏库，不是全网排名）。 */
    val localPercentile: Int? = null,
    // ===== 豆瓣剧照（阶段 E，灰色通道，默认关） =====
    /** 是否开启了豆瓣剧照通道。 */
    val doubanEnabled: Boolean = false,
    /** 已绑定的豆瓣条目 id（未绑定为 null）。 */
    val doubanBoundId: String? = null,
    /** 豆瓣剧照（大图 URL）。 */
    val doubanThumbs: List<String> = emptyList(),
    /** 加载豆瓣图片所需的 Referer。 */
    val doubanImageReferer: String? = null,
    /** 豆瓣词条候选（未绑定时按标题搜索，供用户确认）。 */
    val doubanCandidates: List<com.otakup.niriko.data.remote.douban.DoubanClient.DoubanSearchItem> = emptyList(),
    val doubanLoading: Boolean = false,

    /** 更换封面用的 TMDb 候选海报（打开选择器时按需加载）。 */
    val coverCandidates: List<com.otakup.niriko.data.remote.tmdb.dto.TmdbImageDto> = emptyList(),
    val coverCandidatesLoading: Boolean = false,

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
    private val vndbRepository: VndbRepository? = null,
    private val anilistRepository: com.otakup.niriko.data.repository.AniListRepository? = null,
    private val anitabiRepository: com.otakup.niriko.data.repository.AnitabiRepository? = null,
    private val externalRatingRepository: com.otakup.niriko.data.repository.ExternalRatingRepository? = null,
    private val episodeRatingRepository: com.otakup.niriko.data.repository.EpisodeRatingRepository? = null,
    private val tmdbRepository: com.otakup.niriko.data.repository.TmdbRepository? = null,
    private val externalIdRepository: com.otakup.niriko.data.repository.ExternalIdRepository? = null,
    private val manualAwardRepository: com.otakup.niriko.data.repository.ManualAwardRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectDetailUiState())
    val uiState: StateFlow<SubjectDetailUiState> = _uiState.asStateFlow()

    /** 保存原始 createTime，避免更新时被 System.currentTimeMillis() 覆盖。 */
    private var originalCreateTime: Long = 0L

    /** 收藏写操作计数：loadCollectionState 返回过期快照时据此放弃覆盖（防收藏状态竞态）。 */
    private var collectionMutation = 0

    /**
     * 扩展数据（角色/Staff/剧集/补充数据源）独立作用域：
     * 与收藏保存/主 UI 解耦，避免大量网络+写库任务拖住 `viewModelScope` 里保存协程回主线程。
     */
    private val extendedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init { loadSubject() }

    override fun onCleared() {
        extendedScope.cancel()
        super.onCleared()
    }

    /** 重新加载作品详情（供重试按钮使用）。 */
    fun retry() = loadSubject(forceExtended = true)

    /**
     * 加载作品详情 — 缓存优先 + 后台刷新。
     *
     * 1. 先尝试从本地缓存获取（立即返回）
     * 2. 如有网络，后台静默刷新并更新 UI
     * 3. 无缓存时从远程获取
     */
    private fun loadSubject(forceExtended: Boolean = false) {
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

                try {
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
                        _uiState.update { it.copy(subject = fresh) }
                    }
                } finally {
                    // 关键：无论 fresh 是否为 null（条目可能已被删除/升级迁移）都要复位刷新态。
                    // 改造前 isRefreshing = false 写在 if (fresh != null) 内 → 取不到数据时
                    // 进度条会**永远**转下去。
                    _uiState.update { it.copy(isRefreshing = false) }
                }

                // Step 3: 并行加载扩展数据（角色/制作人员/剧集）
                extendedScope.launch { loadExtendedData(force = forceExtended) }

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
                extendedScope.launch { loadExtendedData(force = forceExtended) }
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
                watchedVolumes = existing?.watchedVolumes,
                isPrivate = existing?.isPrivate ?: false,
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

    /**
     * 并行加载扩展数据（角色、制作人员、剧集、评分分布、关联条目、infobox + Steam/VNDB/AniList/Anitabi 补充）。
     *
     * **改造点**：一次详情页打开要并行打 6 个主源接口，外加 Steam / VNDB / AniList / Anitabi 四路补充
     * （未绑定 VNDB/AniList 时还会各搜一次候选 = 额外网络）。用户反复进出同一作品时这些请求全是重复的。
     * 现在按 [EXTENDED_CACHE_TTL_MS] 做进程内短时缓存（跨 ViewModel 实例共享：每次导航都会新建 VM）。
     *
     * @param force 绕过缓存（详情页「重试」/ 占位条目升级后重新加载）
     */
    private suspend fun loadExtendedData(force: Boolean = false) {
        if (!force) {
            extendedCache.get(subjectId)?.let { snap ->
                Log.d(TAG, "loadExtendedData: 命中缓存 subjectId=$subjectId")
                _uiState.update {
                    it.copy(
                        characters = snap.characters,
                        staff = snap.staff,
                        episodes = snap.episodes,
                        ratingDistribution = snap.ratingDistribution,
                        relations = snap.relations,
                        infoBox = snap.infoBox,
                        steam = snap.steam,
                        achievements = snap.achievements,
                        chartRank = snap.chartRank,
                        vndbBinding = snap.vndbBinding,
                        vndbDetail = snap.vndbDetail,
                        vndbCandidates = snap.vndbCandidates,
                        anilistBinding = snap.anilistBinding,
                        anilistDetail = snap.anilistDetail,
                        anilistRichDetail = snap.anilistRichDetail,
                        anilistCandidates = snap.anilistCandidates,
                        anitabiCity = snap.anitabiCity,
                        anitabiPoints = snap.anitabiPoints,
                        anitabiPointsLength = snap.anitabiPointsLength,
                        anitabiImagesLength = snap.anitabiImagesLength,
                        guessYouLike = snap.guessYouLike,
                        isExtendedLoading = false,
                    )
                }
                // 缓存命中路径同样要算洞察（否则二次进页面争议度会消失）
                loadRatingInsights(snap.ratingDistribution)
                return
            }
        }
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
                    // 修复 R1：改造前这里直接打远端、**从不落库**，导致 episodes 表始终为空，
                    // 单集二级页（读库）永远「未找到该集」，B站/TMDb 剧照回填也更新 0 行。
                    // 改为走 EpisodeRepository：缓存优先 + 落库 + TTL。
                    try {
                        nirikoApp().episodeRepository.getEpisodes(subjectId)
                    } catch (_: Exception) {
                        emptyList()
                    }
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
                val achievementsDeferred = async {
                    try { loadAchievements() } catch (_: Exception) { Unit }
                }
                // 第 5 轮 D10：VNDB 匹配**不再并行发起**。
                // 它需要 infobox（L1 的 vndb id、L2 的别名查询串），而 infobox 由下面那一路
                // 并行拉取。改造前两者同时起跑、且 VNDB 从 _uiState 里读 infobox ——
                // 谁先跑完不确定，VNDB 先跑完时拿到的是空 infobox，
                // 于是「别名查询串全缺 + infobox id 永不命中」。
                // 现在改为：等 infobox 到位、写进 state 之后，再串行发起。
                val anilistDeferred = async {
                    try { loadAniListSupplement() } catch (_: Exception) { Unit }
                }
                val anitabiDeferred = async {
                    try { loadAnitabi() } catch (_: Exception) { Unit }
                }
                val guessDeferred = async {
                    try { loadGuessYouLike() } catch (_: Exception) { Unit }
                }
                val externalRatingDeferred = async {
                    try { loadExternalRatingData() } catch (_: Exception) { Unit }
                }
                val episodeRatingDeferred = async {
                    try { loadEpisodeRatingData() } catch (_: Exception) { Unit }
                }
                val tmdbDeferred = async {
                    try { loadTmdbSupplement() } catch (_: Exception) { Unit }
                }
                val characters = charactersDeferred.await()
                val staff = staffDeferred.await()
                val episodes = episodesDeferred.await()
                val ratingDistribution = distDeferred.await()
                val relations = relationsDeferred.await()
                val infoBox = infoBoxDeferred.await()
                biliDeferred.await()
                steamDeferred.await()
                achievementsDeferred.await()
                anilistDeferred.await()
                anitabiDeferred.await()
                guessDeferred.await()
                externalRatingDeferred.await()
                episodeRatingDeferred.await()
                tmdbDeferred.await()
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
                // 修复 R2：洞察必须在 ratingDistribution 写好之后再算。
                // 改造前它被放在并行的 loadExternalRatingData 里，读到的分布恒为空 →
                // 争议度（标准差）几乎永远不显示。
                loadRatingInsights(ratingDistribution)
                // 第 5 轮 D10：VNDB 匹配放在最后，并**显式传入 infobox**（不再从 state 里读，
                // 杜绝「谁先跑完谁说了算」的时序依赖）。改造前它与 infobox 并行，
                // VNDB 先跑完时拿到空 infobox → 别名查询串全缺、infobox 里的 vndb id 永不命中。
                runCatching { loadVndbSupplement(infoBox) }
            }
        }
        // 记录快照：补充数据源是各自直接写 _uiState 的，因此在这里统一抓取最终结果
        extendedCache.put(subjectId, _uiState.value.toExtendedSnapshot())
    }

    private fun SubjectDetailUiState.toExtendedSnapshot(): ExtendedSnapshot = ExtendedSnapshot(
        characters = characters,
        staff = staff,
        episodes = episodes,
        ratingDistribution = ratingDistribution,
        relations = relations,
        infoBox = infoBox,
        steam = steam,
        achievements = achievements,
        chartRank = chartRank,
        vndbBinding = vndbBinding,
        vndbDetail = vndbDetail,
        vndbCandidates = vndbCandidates,
        anilistBinding = anilistBinding,
        anilistDetail = anilistDetail,
        anilistRichDetail = anilistRichDetail,
        anilistCandidates = anilistCandidates,
        anitabiCity = anitabiCity,
        anitabiPoints = anitabiPoints,
        anitabiPointsLength = anitabiPointsLength,
        anitabiImagesLength = anitabiImagesLength,
        guessYouLike = guessYouLike,
    )

    /** 扩展数据快照（详情页十余路并行结果的合并快照）。 */
    private data class ExtendedSnapshot(
        val characters: List<CharacterInfo>,
        val staff: List<StaffInfo>,
        val episodes: List<EpisodeInfo>,
        val ratingDistribution: Map<Int, Int>,
        val relations: List<SubjectRelationInfo>,
        val infoBox: List<InfoBoxEntry>,
        val steam: com.otakup.niriko.data.local.entity.SteamGameEntity?,
        val achievements: com.otakup.niriko.data.remote.steam.SteamAchievements?,
        val chartRank: com.otakup.niriko.data.remote.steam.SteamChartEntry?,
        val vndbBinding: com.otakup.niriko.data.local.entity.VndbBindingEntity?,
        val vndbDetail: com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto?,
        val vndbCandidates: List<com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto>,
        val anilistBinding: com.otakup.niriko.data.local.entity.AniListBindingEntity?,
        val anilistDetail: com.otakup.niriko.data.remote.game.GameItemDetail?,
        val anilistRichDetail: com.otakup.niriko.data.model.AniListRichDetail?,
        val anilistCandidates: List<com.otakup.niriko.data.remote.game.GameItem>,
        val anitabiCity: String?,
        val anitabiPoints: List<com.otakup.niriko.data.remote.anitabi.AnitabiLitePoint>,
        val anitabiPointsLength: Int,
        val anitabiImagesLength: Int,
        val guessYouLike: List<SubjectEntity>,
    )

    companion object {
        /**
         * 详情页扩展数据的短时缓存。
         * 放在 companion（而非实例字段）是必须的：每次导航到 \`subject_detail/{id}\` 都会新建
         * NavBackStackEntry → 新建 ViewModel，实例级缓存永远不命中。
         */
        private const val EXTENDED_CACHE_TTL_MS = 5 * 60 * 1000L
        private val extendedCache =
            com.otakup.niriko.util.TtlCache<Long, ExtendedSnapshot>(
                ttlMs = EXTENDED_CACHE_TTL_MS,
                maxEntries = 8,
            )
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
     * 拉取 Steam 补充数据（仅 GAME 类型）。
     * - steam 独立条目（sourceKey="steam:{appid}"）：打开详情页即自动匹配 bangumi 词条
     *   （rematchPlaceholder 内部命中后 retry 重载为 bangumi 词条）；未命中则拉完整
     *   appdetails（截图/开发商/发行商/标签/在线人数），让详情页内容不再单薄。
     * - bangumi 词条：懒触发一次 Steam 匹配（matchAndBind），已绑定时补充 Steam 商业数据。
     * - 失败静默（Steam 是补充信息，不影响主流程）
     */
    private suspend fun loadSteamSupplement() {
        val steam = steamRepository ?: return
        val current = _uiState.value.subject ?: return
        if (current.type != com.otakup.niriko.data.model.SubjectType.GAME) return

        if (current.isSteamPlaceholder) {
            // steam 独立条目：自动匹配 bangumi（命中升级为 bangumi 词条，rematchPlaceholder
            // 内部成功才 retry；失败返回 null 不重载，避免循环）
            val appId = current.sourceKey?.removePrefix("steam:")?.toIntOrNull()
            if (appId != null) {
                val upgraded = try {
                    rematchPlaceholder()
                } catch (_: Exception) {
                    null
                }
                if (upgraded != null) return
                // 未升级：拉完整 appdetails（截图/开发商/发行商/标签/在线人数）落库并展示
                val game = steam.fetchDetailAndPersist(current.subjectId, appId)
                if (game != null) {
                    _uiState.update { it.copy(steam = game) }
                    return
                }
            }
        } else {
            // bangumi 词条：懒绑定（打开详情页即尝试匹配 Steam）
            runCatching { steam.matchAndBind(listOf(current)) }
        }
        val game = steam.getSupplement(subjectId) ?: return
        _uiState.update { it.copy(steam = game) }
    }

    /**
     * 拉取 VNDB 补充数据（仅 GAME 类型）。
     *
     * 第 4 轮 D/E 重做：
     * - 候选不再走 `VndbRepository.searchCandidates`（单查询串、候选用主标题打分），
     *   改走 **[ExternalMatchService]**：四层匹配（infobox ID → 多查询串 → 全标题集合打分 → 手动兜底），
     *   并把每条的**匹配理由**带出来供 UI 展示（诊断错绑的关键）；
     * - 已绑定时额外解析 **relations**（前作/续作/同世界观）——用户点名要的 VNDB 独有数据，
     *   也是「匹配不上的作品顺关系链反查」的入口；
     * - 高置信度（≥ [AUTO_BIND_THRESHOLD]）时才自动写库：用户诉求是「该匹配上的要匹配上」，
     *   而这个阈值只放过几乎确定的匹配（含 infobox 明确 ID），不会重现「同世界观作顶掉正主」。
     */
    private suspend fun loadVndbSupplement(
        /**
         * 同一次详情加载里已经拿到的 infobox。
         *
         * 第 5 轮 D10：改为**参数传入**而不是读 `_uiState.value.infoBox`。
         * 调用点保证它已经 await 完成 —— 之前是从 state 里读，于是与 infobox 的拉取形成竞态。
         */
        infobox: List<com.otakup.niriko.data.remote.InfoBoxEntry>,
    ) {
        val vndb = vndbRepository ?: run {
            Log.w(TAG, "loadVndbSupplement skipped: vndbRepository null")
            return
        }
        val current = _uiState.value.subject ?: return
        if (current.type != com.otakup.niriko.data.model.SubjectType.GAME) return

        val binding = runCatching { vndb.getBinding(subjectId) }.getOrNull()
        if (binding != null) {
            val detail = runCatching { vndb.getDetail(binding.vndbId) }.getOrNull()
            val relations = loadVndbRelations(vndb, detail)
            Log.w(TAG, "loadVndbSupplement bound vndbId=${binding.vndbId} relations=${relations.size}")
            _uiState.update {
                it.copy(
                    vndbBinding = binding,
                    vndbDetail = detail,
                    vndbRelations = relations,
                    vndbCandidates = emptyList(),
                )
            }
            return
        }

        // —— 未绑定：统一匹配服务（四层） ——
        val matches = runCatching {
            matchService().match(
                provider = com.otakup.niriko.data.remote.rating.sources.VndbRatingSource.PROVIDER_VNDB,
                subject = current,
                infobox = infobox,
            )
        }.getOrDefault(emptyList())
        Log.w(TAG, "loadVndbSupplement unbound matches=${matches.size} best=${matches.firstOrNull()?.confidence}")

        val confident = matches.firstOrNull { it.confidence >= AUTO_BIND_THRESHOLD }
        if (confident != null) {
            val ok = runCatching { vndb.bindManually(current.subjectId, confident.externalId) }
                .getOrDefault(false)
            if (ok) {
                val newBinding = runCatching { vndb.getBinding(current.subjectId) }.getOrNull()
                val detail = newBinding?.let { runCatching { vndb.getDetail(it.vndbId) }.getOrNull() }
                _uiState.update {
                    it.copy(
                        vndbBinding = newBinding,
                        vndbDetail = detail,
                        vndbRelations = loadVndbRelations(vndb, detail),
                        vndbCandidates = emptyList(),
                        vndbMatchReasons = confident.reasons,
                    )
                }
                return
            }
        }

        // 未达阈值：展示候选 + 匹配理由（用户自己判断，不替他决定）
        val candidates = matches.mapNotNull { m ->
            runCatching { vndb.getDetail(m.externalId) }.getOrNull()
        }
        _uiState.update {
            it.copy(
                vndbCandidates = candidates,
                vndbCandidateReasons = matches.associate { it.externalId to it.reasons },
                vndbRawMatchCount = matches.size,
            )
        }
    }

    /** 解析 VNDB relations 的标题（VNDB 只给 id，需二次查询批量补齐）。 */
    private suspend fun loadVndbRelations(
        vndb: com.otakup.niriko.data.repository.VndbRepository,
        detail: com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto?,
    ): List<com.otakup.niriko.data.repository.VndbRepository.RelationWithTitle> {
        val relations = detail?.relations.orEmpty()
        if (relations.isEmpty()) return emptyList()
        return runCatching { vndb.resolveRelationTitles(relations) }.getOrDefault(emptyList())
    }

    /** 重新拉取 VNDB 补充数据（详情页“重试”按钮）。 */
    fun refreshVndb() {
        if (vndbRepository == null) return
        extendedScope.launch {
            try {
                loadVndbSupplement(_uiState.value.infoBox)
            } catch (e: Exception) {
                Log.w(TAG, "refreshVndb failed", e)
            }
        }
    }

    /**
     * 手动绑定 VNDB 条目（详情页「可能是这款」候选操作）。
     * 绑定成功立即拉取详情并更新 UI；失败静默提示。
     */
    fun bindVndb(vndbId: String) {
        val vndb = vndbRepository ?: return
        val current = _uiState.value.subject ?: return
        if (vndbId.isBlank()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    vndb.bindManually(
                        subjectId = current.subjectId,
                        vndbId = vndbId,
                    )
                }.getOrDefault(false)
            }
            if (ok) {
                val binding = withContext(Dispatchers.IO) { vndb.getBinding(current.subjectId) }
                val detail = binding?.let { b -> runCatching { vndb.getDetail(b.vndbId) }.getOrNull() }
                _uiState.update {
                    it.copy(
                        vndbBinding = binding,
                        vndbDetail = detail,
                        vndbCandidates = emptyList(),
                        snackbarMessage = "已绑定 VNDB 条目",
                    )
                }
            } else {
                _uiState.update { it.copy(snackbarMessage = "VNDB 绑定失败，请重试") }
            }
        }
    }

    /** 详情页 VNDB 候选“搜索更多”：按任意关键词重新搜索候选。     *
     * 第 4 轮 D：改走统一匹配服务的 [ExternalMatchService.searchByQuery]，
     * 候选标注匹配度与理由；结果不再因为分数低被过滤（用户自己搜的东西让他看）。
     */
    fun searchMoreVndb(query: String) {
        val vndb = vndbRepository ?: return
        val current = _uiState.value.subject ?: return
        val text = query.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(vndbManualMessage = "请输入要搜索的关键词或 VNDB id") }
            return
        }
        viewModelScope.launch {
            // 允许直接粘贴 id / 链接
            val directId = com.otakup.niriko.data.match.VndbProviderMatcher.normalizeId(text)
            if (directId != null && text.length <= 40 && !text.contains(' ')) {
                val detail = withContext(Dispatchers.IO) {
                    runCatching { vndb.getDetail(directId) }.getOrNull()
                }
                if (detail != null) {
                    _uiState.update {
                        it.copy(
                            vndbCandidates = listOf(detail),
                            vndbManualMessage = null,
                        )
                    }
                    return@launch
                }
            }
            val matches = withContext(Dispatchers.IO) {
                runCatching {
                    matchService().searchByQuery(
                        provider = com.otakup.niriko.data.remote.rating.sources.VndbRatingSource.PROVIDER_VNDB,
                        query = text,
                        subject = current,
                    )
                }.getOrDefault(emptyList())
            }
            val details = withContext(Dispatchers.IO) {
                matches.mapNotNull { m -> runCatching { vndb.getDetail(m.externalId) }.getOrNull() }
            }
            _uiState.update {
                it.copy(
                    vndbCandidates = details,
                    vndbCandidateReasons = matches.associate { m -> m.externalId to m.reasons },
                    vndbManualMessage = if (details.isEmpty()) {
                        "没有搜到「$text」的 VNDB 条目"
                    } else {
                        "找到 ${details.size} 个候选，点选即绑定"
                    },
                )
            }
        }
    }

    /** 解除 VNDB 绑定（详情页已绑定区块）。 */
    fun unbindVndb() {
        val vndb = vndbRepository ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { vndb.unbind(subjectId) } }
            _uiState.update {
                it.copy(
                    vndbBinding = null,
                    vndbDetail = null,
                    vndbCandidates = emptyList(),
                    snackbarMessage = "已解除 VNDB 绑定",
                )
            }
        }
    }

    /**
     * 猜你喜欢（阶段 F）：按本地 tag 共现 + 同类型打分，取前 10。纯本地计算，失败静默。
     */
    private suspend fun loadGuessYouLike() {
        val current = _uiState.value.subject ?: return
        val all = runCatching { subjectRepository.observeAll().first() }.getOrDefault(emptyList())
        if (all.size <= 1) return
        val myTags = current.tags.toSet()
        val scored = all.asSequence()
            .filter { it.subjectId != current.subjectId && !it.isSteamPlaceholder && it.tags.isNotEmpty() }
            .map { s ->
                val overlap = myTags.intersect(s.tags.toSet()).size
                Triple(s, overlap, if (current.type == s.type) 1 else 0)
            }
            .filter { it.second > 0 || it.third > 0 }
            .sortedByDescending { it.second * 10 + it.third }
            .take(10)
            .map { it.first }
            .toList()
        _uiState.update { it.copy(guessYouLike = scored) }
    }

    /**
     * 拉取圣地巡礼（Anitabi）取景地标（阶段 K）：仅动画、非 NSFW；D7 快照缓存；失败静默。
     */
    private suspend fun loadAnitabi() {
        val repo = anitabiRepository ?: return
        val current = _uiState.value.subject ?: return
        if (current.type != com.otakup.niriko.data.model.SubjectType.ANIME) return
        val entity = repo.load(subjectId, current.type, nsfw = false)
        val points = repo.decodePoints(entity)
        _uiState.update {
            it.copy(
                anitabiCity = entity?.city,
                anitabiPoints = points,
                anitabiPointsLength = entity?.pointsLength ?: 0,
                anitabiImagesLength = entity?.imagesLength ?: 0,
            )
        }
    }

    /**
     * 拉取 AniList 补充数据（**不限制类型**：动画/漫画/游戏等任意 bangumi 词条）。
     * - 仅 bangumi 词条（sourceKey == null）触发；AniList 占位条目自身已是 AniList 数据；
     * - 阶段 G 绑定保守化：不做标题自动匹配写库。已绑定（anilistBinding 存在）→ 拉详情
     *   写入 uiState.anilistBinding/anilistDetail；未绑定 → 按标题搜索 Top 候选写入
     *   uiState.anilistCandidates，供详情页手动绑定；
     * - 失败静默（AniList 是补充信息源，不影响主流程）
     */
    private suspend fun loadAniListSupplement() {
        val anilist = anilistRepository ?: run {
            Log.w(TAG, "loadAniListSupplement skipped: anilistRepository null")
            return
        }
        val current = _uiState.value.subject ?: return
        // 仅 bangumi 词条匹配补充（非 bangumi 条目自身已是其他源数据）
        if (current.sourceKey != null) return
        // 阶段 G 绑定保守化：不做标题自动匹配写库，直接读绑定；未绑定则加载候选
        val binding = try {
            anilist.getBinding(subjectId)
        } catch (e: Exception) {
            Log.w(TAG, "loadAniListSupplement getBinding failed subjectId=$subjectId", e)
            null
        }
        if (binding != null) {
            val detail = try {
                anilist.getDetail(binding.anilistId)
            } catch (e: Exception) {
                Log.w(TAG, "loadAniListSupplement getDetail failed anilistId=${binding.anilistId}", e)
                null
            }
            val rich = try {
                anilist.getRichDetail(binding.anilistId)
            } catch (e: Exception) {
                Log.w(TAG, "loadAniListSupplement getRichDetail failed anilistId=${binding.anilistId}", e)
                null
            }
            Log.w(TAG, "loadAniListSupplement bound anilistId=${binding.anilistId} detail=${detail != null} rich=${rich != null}")
            _uiState.update {
                it.copy(anilistBinding = binding, anilistDetail = detail, anilistRichDetail = rich, anilistCandidates = emptyList())
            }
        } else {
            // 未绑定：先尝试保守自动匹配（精确/高置信才写库），否则展示候选。
            val autoBound = try {
                anilist.autoBindMatch(current)
            } catch (e: Exception) {
                Log.w(TAG, "loadAniListSupplement autoBindMatch failed subjectId=$subjectId", e)
                false
            }
            if (autoBound) {
                val newBinding = try {
                    anilist.getBinding(subjectId)
                } catch (e: Exception) {
                    Log.w(TAG, "loadAniListSupplement re-getBinding failed subjectId=$subjectId", e)
                    null
                }
                val detail = newBinding?.let { b ->
                    try {
                        anilist.getDetail(b.anilistId)
                    } catch (e: Exception) {
                        Log.w(TAG, "loadAniListSupplement autoBound getDetail failed anilistId=${b.anilistId}", e)
                        null
                    }
                }
                val rich = newBinding?.let { b ->
                    try {
                        anilist.getRichDetail(b.anilistId)
                    } catch (e: Exception) {
                        Log.w(TAG, "loadAniListSupplement autoBound getRichDetail failed anilistId=${b.anilistId}", e)
                        null
                    }
                }
                _uiState.update {
                    it.copy(anilistBinding = newBinding, anilistDetail = detail, anilistRichDetail = rich, anilistCandidates = emptyList())
                }
            } else {
                val candidates = try {
                    anilist.searchCandidates(current, limit = 5)
                } catch (e: Exception) {
                    Log.w(TAG, "loadAniListSupplement searchCandidates failed subjectId=$subjectId", e)
                    emptyList()
                }
                Log.w(TAG, "loadAniListSupplement unbound candidates=${candidates.size}")
                _uiState.update { it.copy(anilistCandidates = candidates) }
            }
        }
    }

    /** 重新拉取 AniList 补充数据（详情页“重试”按钮）。 */
    fun refreshAnilist() {
        if (anilistRepository == null) return
        extendedScope.launch {
            try {
                loadAniListSupplement()
            } catch (e: Exception) {
                Log.w(TAG, "refreshAnilist failed", e)
            }
        }
    }

    /**
     * 手动绑定 AniList 条目（详情页「可能是这个」候选操作）。
     * 绑定成功立即拉取详情并更新 UI；失败静默提示。
     */
    fun bindAnilist(anilistId: Long) {
        val anilist = anilistRepository ?: return
        val current = _uiState.value.subject ?: return
        if (anilistId <= 0) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    anilist.bindManually(
                        subjectId = current.subjectId,
                        anilistId = anilistId,
                    )
                }.getOrDefault(false)
            }
            if (ok) {
                val binding = withContext(Dispatchers.IO) { anilist.getBinding(current.subjectId) }
                val detail = binding?.let { b -> runCatching { anilist.getDetail(b.anilistId) }.getOrNull() }
                val rich = binding?.let { b -> runCatching { anilist.getRichDetail(b.anilistId) }.getOrNull() }
                _uiState.update {
                    it.copy(
                        anilistBinding = binding,
                        anilistDetail = detail,
                        anilistRichDetail = rich,
                        anilistCandidates = emptyList(),
                        snackbarMessage = "已绑定 AniList 条目",
                    )
                }
            } else {
                _uiState.update { it.copy(snackbarMessage = "AniList 绑定失败，请重试") }
            }
        }
    }

    /** 详情页 AniList 候选“搜索更多”：按任意关键词重新搜索候选，或直接粘贴 AniList id。 */
    fun searchMoreAnilist(query: String) {
        val anilist = anilistRepository ?: return
        val text = query.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(anilistManualMessage = "请输入关键词或 AniList id") }
            return
        }
        viewModelScope.launch {
            // 第 4 轮 D：允许直接粘贴 AniList id（数字）或 anilist.co 链接
            val directId = ANILIST_ID_REGEX.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (directId != null) {
                val item = withContext(Dispatchers.IO) {
                    // getDetail 返回 GameItemDetail（含截图），候选列表只要 GameItem
                    runCatching { anilist.getDetail(directId)?.item }.getOrNull()
                }
                if (item != null) {
                    _uiState.update {
                        it.copy(anilistCandidates = listOf(item), anilistManualMessage = null)
                    }
                    return@launch
                }
            }
            val list = withContext(Dispatchers.IO) {
                runCatching { anilist.searchMore(text) }.getOrDefault(emptyList())
            }
            _uiState.update {
                it.copy(
                    anilistCandidates = list,
                    anilistManualMessage = if (list.isEmpty()) {
                        "没有搜到「$text」的 AniList 条目"
                    } else {
                        "找到 ${list.size} 个候选，点选即绑定"
                    },
                )
            }
        }
    }

    /** 清掉 AniList 手动入口的反馈。 */
    fun clearAnilistManualMessage() {
        _uiState.update { it.copy(anilistManualMessage = null) }
    }

    /** 解除 AniList 绑定（详情页已绑定区块）。 */
    fun unbindAnilist() {
        val anilist = anilistRepository ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { anilist.unbind(subjectId) } }
            _uiState.update {
                it.copy(
                    anilistBinding = null,
                    anilistDetail = null,
                    anilistRichDetail = null,
                    anilistCandidates = emptyList(),
                    snackbarMessage = "已解除 AniList 绑定",
                )
            }
        }
    }

    /**
     * 拉取 Steam 成就进度与活跃排名（仅已绑定 GAME 条目有效）。
     * 需 API key + 已登录（成就）；失败静默（achievements/chartRank 保持 null，UI 隐藏或提示）。
     */
    private suspend fun loadAchievements() {
        val steam = steamRepository ?: return
        val current = _uiState.value.subject ?: return
        if (current.type != com.otakup.niriko.data.model.SubjectType.GAME) return
        val achievements = steam.getAchievements(subjectId)
        val chartRank = _uiState.value.steam?.appId?.let { steam.getChartRank(it) }
        _uiState.update {
            it.copy(
                achievements = achievements,
                chartRank = chartRank,
            )
        }
    }

    /**
     * 占位条目重新匹配升级：用标题搜索 Bangumi GAME 词条，命中后
     * 调用 [SteamRepository.upgradePlaceholder] 把占位条目（-appId）迁移为正式词条。
     * 供详情页「重新匹配」操作与导入预览页使用。
     *
     * @return 升级后的新 subjectId（成功且非占位）；失败返回 null
     */
    suspend fun rematchPlaceholder(): Long? {
        val steam = steamRepository ?: return null
        val current = _uiState.value.subject ?: return null
        if (!current.isSteamPlaceholder) return null
        val appId = (_uiState.value.steam?.appId) ?: return null

        // 用标题搜索 Bangumi 游戏词条，取置信度最高者（title + titleCN 双标题都试）
        val titles = listOfNotNull(current.title, current.titleCN).filter { it.isNotBlank() }.distinct()
        var bestPair: Pair<SubjectEntity, Float>? = null
        for (title in titles) {
            val candidates = try {
                withTimeout(3000) {
                    subjectRepository.search(keyword = title, type = 4, limit = 5)
                }
            } catch (_: Exception) {
                emptyList()
            }
            val m = candidates
                .filter {
                    it.type == com.otakup.niriko.data.model.SubjectType.GAME &&
                        it.subjectId > 0 && it.subjectId != subjectId &&
                        it.sourceKey == null // 只接受真 bangumi 词条，排除 steam/vndb/anilist 独立条目（防匹配到自己）
                }
                .mapNotNull { candidate ->
                    val score = com.otakup.niriko.data.remote.steam.SteamTitleMatcher.bestConfidence(
                        queryTitles = listOf(title),
                        candidateTitles = listOfNotNull(candidate.title, candidate.titleCN),
                    )
                    if (score >= com.otakup.niriko.data.remote.steam.SteamTitleMatcher.MIN_CONFIDENCE) {
                        candidate to score
                    } else null
                }
                .maxByOrNull { it.second }
            if (m != null && (bestPair == null || m.second > bestPair.second)) bestPair = m
        }
        val best = bestPair?.first ?: return null

        val database = com.otakup.niriko.data.local.NirikoDatabase.getInstance(context)
        val ok = steam.upgradePlaceholder(
            appId = appId,
            newSubjectId = best.subjectId,
            database = database,
            subjectDao = database.subjectDao(),
            collectionDao = database.collectionDao(),
            steamLibraryItemDao = database.steamLibraryItemDao(),
        )
        if (!ok) return null
        // 刷新详情（subjectId 已变更，重载页面数据）
        retry()
        return best.subjectId
    }

    /**
     * 解除 Steam 绑定（错绑数据手动解绑，之后可重新匹配）。
     * 仅对非占位条目有效（占位条目本身就是独立作品，不解绑）。
     * 成功后清空 uiState.steam/achievements/chartRank 并提示。
     */
    fun unbindSteam() {
        val steam = steamRepository ?: return
        val current = _uiState.value.subject ?: return
        if (current.isSteamPlaceholder) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { steam.unbind(subjectId) }
            _uiState.update {
                it.copy(
                    steam = null, achievements = null, chartRank = null,
                    snackbarMessage = "已解除 Steam 绑定",
                )
            }
        }
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
                    watchedEpisodes = newProgress, watchedVolumes = s.watchedVolumes,
                    isPrivate = s.isPrivate, rating = s.myRating,
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
                watchedEpisodes = null, watchedVolumes = null, isPrivate = false, myRating = null, personalTags = emptyList(), personalImpression = null, remark = null, watchedTrackIds = emptySet(), startDate = null, finishDate = null) }
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
        watchedVolumes: Int? = null,
        isPrivate: Boolean = false,
    ) {
        val s = _uiState.value
        if (!s.isInCollection || s.collectionId == 0L || s.isUpdating) return
        collectionMutation++
        // 音乐类型：watchedEpisodes 与已听曲目集合保持同步（统计兼容）
        val isMusic = s.subject?.type == com.otakup.niriko.data.model.SubjectType.MUSIC
        val finalWatched = if (isMusic) watchedTrackIds.size else watchedEpisodes
        viewModelScope.launch {
            // 乐观更新：先让 UI 立即反映新值，再后台写 Room；失败由 Snackbar 提示。
            _uiState.update { it.copy(
                isUpdating = true,
                watchedEpisodes = watchedEpisodes,
                watchedVolumes = watchedVolumes,
                isPrivate = isPrivate,
                myRating = myRating,
                personalTags = personalTags,
                personalImpression = personalImpression,
                startDate = startDate,
                finishDate = finishDate,
                watchedTrackIds = watchedTrackIds,
            ) }
            withContext(Dispatchers.IO) {
                collectionRepository.update(CollectionEntity(
                    id = s.collectionId, subjectId = subjectId, status = s.currentStatus,
                    watchedEpisodes = finalWatched, watchedVolumes = watchedVolumes,
                    isPrivate = isPrivate, rating = myRating,
                    personalTags = personalTags, personalImpression = personalImpression,
                    remark = s.remark, startDate = startDate, finishDate = finishDate,
                    watchedTrackIds = watchedTrackIds.toList(),
                    createTime = originalCreateTime, updateTime = System.currentTimeMillis(),
                ))
            }
            _uiState.update { it.copy(isUpdating = false, snackbarMessage = "记录已保存") }
        }
    }

    // ==================== 权威评分 + 每集评分（阶段 0–1）====================

    /**
     * 评分洞察：争议度（评分分布标准差）+ 本地库内同类型百分位。
     * 纯本地计算，零网络请求。
     */
    private suspend fun loadRatingInsights(distribution: Map<Int, Int>) {
        val subject = _uiState.value.subject ?: return
        val dispute = if (distribution.isEmpty()) {
            null
        } else {
            com.otakup.niriko.data.calculator.RatingInsights
                .disputeLabel(com.otakup.niriko.data.calculator.RatingInsights.deviation(distribution))
        }
        val sameType = runCatching {
            nirikoDatabase().subjectDao().getRatingScoresByType(subject.type.name)
        }.getOrDefault(emptyList())
        val percentile = com.otakup.niriko.data.calculator.RatingInsights
            .localPercentile(subject.ratingScore, sameType)
        _uiState.update { it.copy(ratingDispute = dispute, localPercentile = percentile) }
    }

    /** 权威评分聚合（本地 Bangumi/Bilibili + 远端各源；未配置 key 的源自动跳过）。 */
    private suspend fun loadExternalRatingData() {
        val subject = _uiState.value.subject ?: return
        val repo = externalRatingRepository ?: return
        val ratings = repo.ratings(subject)
        val metacritic = if (subject.type == com.otakup.niriko.data.model.SubjectType.GAME) {
            repo.steamMetacritic(subject)
        } else null
        val awards = manualAwardRepository?.awardsOf(subject.subjectId).orEmpty()
        _uiState.update {
            it.copy(
                externalRatings = (ratings + listOfNotNull(metacritic)).distinctBy { r -> r.sourceId },
                steamMetacritic = metacritic,
                manualAwards = awards,
            )
        }
    }

    /**
     * 每集评分：先读库（便宜），仅在缓存过期时才打 TMDb。
     * 过期判定用行内 fetchedAt + 6 小时软 TTL——避免每次进详情页都发一次 season 请求。
     */
    private suspend fun loadEpisodeRatingData() {
        val subject = _uiState.value.subject ?: return
        val repo = episodeRatingRepository ?: return

        val cached = repo.ratingsOf(subject.subjectId)
        // 新鲜度统一由仓储按 RefreshResource.EPISODE_RATINGS 判定（不再在 UI 层写 TTL）
        val stale = !runCatching { repo.isEpisodeRatingsFresh(subject.subjectId) }.getOrDefault(false)

        val load = if (stale) {
            repo.refreshFromTmdb(subject)
        } else {
            null
        }
        val ratings = if (stale) repo.ratingsOf(subject.subjectId) else cached
        val stills = episodesWithStills().toMutableMap()
        // 阶段 D：用 B 站分集封面补齐缺失剧照（只填空缺，不覆盖 TMDb 已写入的）
        fillStillsFromBilibili(subject, stills)
        // 第 4 轮 H：不再只算一个布尔，而是拿到逐项前置条件（UI 需要显示「缺什么」）
        val imdbStatus = runCatching { repo.entryStatus(subject) }.getOrNull()
        // 阶段 E：豆瓣剧照（默认关；开启且已绑定才发请求）
        launchDoubanIfEnabled(subject)
        _uiState.update {
            it.copy(
                episodeRatings = ratings,
                episodeRatingLoad = load,
                episodeStills = stills,
                imdbEntry = imdbStatus,
                imdbEpisodeEntryEnabled = imdbStatus?.enabled == true,
            )
        }
        loadMyEpisodeRatings()
    }

    /**
     * 阶段 D：用 B 站番剧分集封面补齐缺失的剧照。
     *
     * 为什么先做 B 站而不是豆瓣：B 站接口免 key、客户端已具备 UA/Referer、
     * 且映射表（bilibili_site_map）本来就有——完全正当且稳定。
     * 只在 TMDb 没有剧照的空缺位置填，失败静默。
     */
    private suspend fun fillStillsFromBilibili(
        subject: com.otakup.niriko.data.local.entity.SubjectEntity,
        stills: MutableMap<Long, String>,
    ) {
        val mainEpisodes = _uiState.value.episodes.filter { it.type == 0 }.sortedBy { it.sort }
        if (mainEpisodes.isEmpty()) return
        if (mainEpisodes.all { stills[it.id] != null }) return
        val season = runCatching {
            com.otakup.niriko.data.remote.bilibili.BilibiliRatingClient
                .fetchSeasonByBgmId(context.applicationContext, subject.subjectId)
        }.getOrNull() ?: return
        val covers = season.episodes.mapNotNull { it.cover }.filter { it.isNotBlank() }
        if (covers.isEmpty()) return
        mainEpisodes.forEachIndexed { index, episode ->
            if (stills[episode.id] != null) return@forEachIndexed
            val cover = covers.getOrNull(index) ?: return@forEachIndexed
            stills[episode.id] = cover
            // 落库，避免每次进详情页都重新拉一次 B 站
            runCatching { nirikoDatabase().episodeDao().updateStillUrl(episode.id, cover) }
        }
    }

    // ==================== 豆瓣剧照（阶段 E）====================

    /** 读取设置（豆瓣通道的开关与头配方都在里面）。 */
    private suspend fun readSettings(): com.otakup.niriko.data.settings.AppSettings? =
        runCatching {
            (context.applicationContext as com.otakup.niriko.NirikoApplication)
                .settingsDataStore.settings.first()
        }.getOrNull()

    private fun doubanHeaders(
        settings: com.otakup.niriko.data.settings.AppSettings,
    ): com.otakup.niriko.data.remote.douban.DoubanClient.Headers =
        com.otakup.niriko.data.remote.douban.DoubanClient.Headers(
            apiRefererPrefix = settings.doubanApiReferer,
            imageReferer = settings.doubanImageReferer,
        )

    private fun doubanCategory(type: com.otakup.niriko.data.model.SubjectType) =
        when (type) {
            com.otakup.niriko.data.model.SubjectType.BOOK ->
                com.otakup.niriko.data.remote.douban.DoubanCategory.BOOK
            com.otakup.niriko.data.model.SubjectType.MUSIC ->
                com.otakup.niriko.data.remote.douban.DoubanCategory.MUSIC
            com.otakup.niriko.data.model.SubjectType.GAME ->
                com.otakup.niriko.data.remote.douban.DoubanCategory.GAME
            com.otakup.niriko.data.model.SubjectType.REAL ->
                com.otakup.niriko.data.remote.douban.DoubanCategory.TV
            else -> com.otakup.niriko.data.remote.douban.DoubanCategory.MOVIE
        }

    /** 豆瓣通道开启时：读绑定并拉剧照；未绑定则只标记状态（等用户点候选绑定）。 */
    private suspend fun launchDoubanIfEnabled(
        subject: com.otakup.niriko.data.local.entity.SubjectEntity,
    ) {
        val settings = readSettings() ?: return
        if (!settings.doubanPhotosEnabled) {
            _uiState.update {
                it.copy(doubanEnabled = false, doubanThumbs = emptyList(), doubanBoundId = null)
            }
            return
        }
        val boundId = externalIdRepository
            ?.get(subject.subjectId, com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.PROVIDER_DOUBAN)
            ?.externalId
        _uiState.update {
            it.copy(
                doubanEnabled = true,
                doubanImageReferer = settings.doubanImageReferer,
                doubanBoundId = boundId,
            )
        }
        if (boundId == null) return
        val photos = runCatching {
            com.otakup.niriko.data.remote.douban.DoubanClient.photos(
                doubanId = boundId,
                category = doubanCategory(subject.type),
                headers = doubanHeaders(settings),
            )
        }.getOrDefault(emptyList())
        _uiState.update { it.copy(doubanThumbs = photos) }
    }

    /**
     * 解析豆瓣条目 id（第 4 轮 F：三级来源，按可靠性排序）。
     *
     * 第 3 轮的失败根因是把它实现成「客户端实时搜索」，而 Bangumi-master 的豆瓣 id
     * 其实是**服务端预跑任务**的产物——客户端只负责「已知 id → 取剧照」。
     * 因此现在按可靠性分三级：
     *
     * | 级别 | 来源 | 处理 |
     * |---|---|---|
     * | 1 | Bangumi infobox / 简介里的豆瓣链接 | **直接绑定**（零猜测） |
     * | 2 | 豆瓣搜索 + 高阈值匹配（≥0.86 且年份一致） | 直接绑定 |
     * | 3 | 其余情况 | 只展示候选，用户点选 |
     *
     * 另外还提供「手动粘贴豆瓣 id / 链接」的终极兜底（见 [pasteDoubanId]）。
     */
    fun searchDoubanCandidates() {
        val subject = _uiState.value.subject ?: return
        if (_uiState.value.doubanLoading) return
        _uiState.update { it.copy(doubanLoading = true) }
        extendedScope.launch {
            val settings = readSettings()
            if (settings == null || !settings.doubanPhotosEnabled) {
                _uiState.update { it.copy(doubanLoading = false) }
                _emitSnackbar("请先在「设置 → 数据源与账号 → 灰色通道」开启豆瓣剧照")
                return@launch
            }

            // —— L1：infobox 明确 id（零猜测，直接绑定） ——
            val infoboxId = extractDoubanIdFromInfobox()
            if (infoboxId != null) {
                bindDoubanId(
                    id = infoboxId,
                    titleSnapshot = "（来自 infobox）",
                    method = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.METHOD_INFOBOX,
                )
                _uiState.update { it.copy(doubanLoading = false) }
                return@launch
            }

            // —— L2/L3：搜索 + 打分 ——
            val keyword = subject.titleCN?.takeIf { it.isNotBlank() } ?: subject.title
            val items = runCatching {
                com.otakup.niriko.data.remote.douban.DoubanClient
                    .search(keyword, doubanHeaders(settings))
            }.getOrDefault(emptyList())

            val year = subject.airDate?.take(4)?.toIntOrNull()
            val scored = items.map { item ->
                val score = com.otakup.niriko.data.match.MatchScorer.score(
                    bangumiTitles = listOfNotNull(subject.titleCN, subject.title),
                    candidate = com.otakup.niriko.data.match.RawCandidate(
                        externalId = item.id,
                        titles = listOfNotNull(item.title, item.originalTitle),
                        year = item.year?.take(4)?.toIntOrNull(),
                        displayTitle = item.title,
                    ),
                    bangumiYear = year,
                )
                item to score
            }.sortedByDescending { it.second.score }

            // 高阈值 + 年份一致才自动绑定：豆瓣同名/同系列条目非常多，
            // 0.86 以下或年份冲突一律只给候选（用户反馈过「错绑比不绑更糟」）。
            val best = scored.firstOrNull()
            val bestYear = best?.first?.year?.take(4)?.toIntOrNull()
            val yearAgrees = year == null || bestYear == null || year == bestYear
            if (best != null && best.second.score >= DOUBAN_AUTO_BIND_THRESHOLD && yearAgrees) {
                bindDoubanId(
                    id = best.first.id,
                    titleSnapshot = best.first.title,
                    method = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.METHOD_MANUAL,
                )
                _uiState.update { it.copy(doubanLoading = false) }
                _emitSnackbar("已按高置信度匹配绑定豆瓣《${best.first.title}》")
                return@launch
            }

            val ranked = scored.map { it.first }.take(6)
            _uiState.update { it.copy(doubanCandidates = ranked, doubanLoading = false) }
            if (ranked.isEmpty()) {
                _emitSnackbar("豆瓣没有搜到候选（可能被反爬拦截，或该作品未被收录）。可手动粘贴豆瓣 ID。")
            }
        }
    }

    /**
     * 从 Bangumi infobox 里提取豆瓣 id（L1）。
     *
     * 豆瓣链接有三种常见写法：`https://movie.douban.com/subject/1292052/`、
     * `https://www.douban.com/game/1234567/`、以及裸 id。统一用「subject|game 后面那段数字」抓。
     */
    private fun extractDoubanIdFromInfobox(): String? {
        val infobox = _uiState.value.infoBox
        if (infobox.isEmpty()) return null
        val keyHints = listOf("豆瓣", "douban")
        for (entry in infobox) {
            val key = entry.key.lowercase()
            if (keyHints.none { key.contains(it) }) continue
            DOUBAN_ID_REGEX.find(entry.value)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    /** 手动粘贴豆瓣 id / 链接（终极兜底，与 TMDb 的粘贴入口对齐）。 */
    fun pasteDoubanId(raw: String) {
        val subject = _uiState.value.subject ?: return
        val text = raw.trim()
        if (text.isEmpty()) {
            _emitSnackbar("请粘贴豆瓣 ID 或链接")
            return
        }
        val id = DOUBAN_ID_REGEX.find(text)?.groupValues?.getOrNull(1)
            ?: text.takeIf { it.all { ch -> ch.isDigit() } && it.length in 5..12 }
        if (id == null) {
            _emitSnackbar("无法从输入里解析出豆瓣 ID（支持 1292052 或 douban.com/subject/1292052/）")
            return
        }
        extendedScope.launch {
            bindDoubanId(
                id = id,
                titleSnapshot = subject.titleCN ?: subject.title,
                method = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.METHOD_MANUAL,
            )
        }
    }

    /** 统一的豆瓣绑定动作（L1/L2/手动三条路径共用，避免三处各写一遍）。 */
    private suspend fun bindDoubanId(id: String, titleSnapshot: String, method: String) {
        val subject = _uiState.value.subject ?: return
        val repo = externalIdRepository ?: return
        repo.bind(
            subjectId = subject.subjectId,
            provider = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.PROVIDER_DOUBAN,
            externalId = id,
            titleSnapshot = titleSnapshot,
            confidence = if (method == com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.METHOD_INFOBOX) 1f else 0.9f,
            bindMethod = method,
        )
        _uiState.update { it.copy(doubanCandidates = emptyList(), doubanBoundId = id) }
        launchDoubanIfEnabled(subject)
    }

    /** 用户确认绑定豆瓣条目。 */
    fun bindDouban(item: com.otakup.niriko.data.remote.douban.DoubanClient.DoubanSearchItem) {
        val subject = _uiState.value.subject ?: return
        val repo = externalIdRepository ?: return
        extendedScope.launch {
            repo.bind(
                subjectId = subject.subjectId,
                provider = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.PROVIDER_DOUBAN,
                externalId = item.id,
                titleSnapshot = item.title,
                confidence = 1f,
                bindMethod = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.METHOD_MANUAL,
            )
            _uiState.update { it.copy(doubanCandidates = emptyList(), doubanBoundId = item.id) }
            _emitSnackbar("已绑定豆瓣《" + item.title + "》")
            launchDoubanIfEnabled(subject)
        }
    }

    /** 关闭候选弹窗（用户没有选中任何一个）。 */
    fun clearDoubanCandidates() {
        _uiState.update { it.copy(doubanCandidates = emptyList()) }
    }

    fun unbindDouban() {
        val subject = _uiState.value.subject ?: return
        val repo = externalIdRepository ?: return
        extendedScope.launch {
            repo.unbind(
                subject.subjectId,
                com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.PROVIDER_DOUBAN,
            )
            _uiState.update { it.copy(doubanBoundId = null, doubanThumbs = emptyList()) }
        }
    }

    /** 每集剧照（直接读 episodes 表的 stillUrl 列）。 */
    private suspend fun episodesWithStills(): Map<Long, String> {
        val subject = _uiState.value.subject ?: return emptyMap()
        return runCatching {
            nirikoDatabase().episodeDao().getBySubject(subject.subjectId)
                .mapNotNull { ep -> ep.stillUrl?.let { ep.epId to it } }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun nirikoApp(): com.otakup.niriko.NirikoApplication =
        context.applicationContext as com.otakup.niriko.NirikoApplication

    private fun nirikoDatabase() = nirikoApp().database

    private suspend fun loadMyEpisodeRatings() {
        val subject = _uiState.value.subject ?: return
        val map = runCatching {
            nirikoDatabase().externalRatingDao().getMyEpisodeRatings(subject.subjectId)
                // 未打分占位 -1 不能当成分数展示（「只写评价没打分」是常态）
                .filter { it.score >= 0f }
                .associate { it.epId to it.score }
        }.getOrDefault(emptyMap())
        _uiState.update { it.copy(myEpisodeRatings = map) }
    }

    /** 我的每集评分（0-10）；传 null 删除。 */
    fun saveMyEpisodeRating(epId: Long, score: Float?) {
        val subjectId = _uiState.value.subject?.subjectId ?: return
        extendedScope.launch {
            runCatching {
                if (score == null) {
                    nirikoDatabase().externalRatingDao().deleteMyEpisodeRating(epId)
                } else {
                    nirikoDatabase().externalRatingDao().upsertMyEpisodeRating(
                        com.otakup.niriko.data.local.entity.EpisodeMyRatingEntity(
                            epId = epId,
                            subjectId = subjectId,
                            score = score,
                            ratedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            loadMyEpisodeRatings()
        }
    }

    /** 手动触发 IMDb 逐集评分（OMDb，默认关闭：请求多、额度有限）。 */
    fun loadImdbEpisodeRatings() {
        val subject = _uiState.value.subject ?: return
        val repo = episodeRatingRepository ?: return
        if (_uiState.value.imdbEpisodesLoading) return
        _uiState.update { it.copy(imdbEpisodesLoading = true) }
        extendedScope.launch {
            val result = runCatching { repo.loadImdbEpisodeRatings(subject) }.getOrNull()
            val ratings = repo.ratingsOf(subject.subjectId)
            _uiState.update {
                it.copy(
                    episodeRatings = ratings,
                    imdbEpisodesLoading = false,
                    imdbEpisodeLoad = result,
                    // 加载过程可能回填了季号 / IMDb id，顺带刷新一次状态行
                    imdbEntry = runCatching { repo.entryStatus(subject) }.getOrNull(),
                )
            }
            when (result?.state) {
                com.otakup.niriko.data.repository.EpisodeRatingRepository.ImdbEpisodeLoad.State.UNAVAILABLE ->
                    _emitSnackbar("未配置 OMDb API Key（设置 → 数据源与账号 → 权威数据源）")
                com.otakup.niriko.data.repository.EpisodeRatingRepository.ImdbEpisodeLoad.State.NOT_BOUND ->
                    _emitSnackbar("尚未绑定 TMDb 条目")
                com.otakup.niriko.data.repository.EpisodeRatingRepository.ImdbEpisodeLoad.State.FAILED ->
                    _emitSnackbar("IMDb 评分加载失败，请稍后重试")
                com.otakup.niriko.data.repository.EpisodeRatingRepository.ImdbEpisodeLoad.State.OK ->
                    _emitSnackbar(
                        if (result.viaOmdbFallback > 0) {
                            "已加载 ${result.loaded} 集 IMDb 评分（${result.viaOmdbFallback} 集走季集兜底）"
                        } else {
                            "已加载 ${result.loaded} 集 IMDb 评分"
                        },
                    )
                null -> Unit
            }
        }
    }

    /** 强制刷新权威评分（用户点「重试」）。 */
    fun refreshExternalRatings() {
        val subject = _uiState.value.subject ?: return
        val repo = externalRatingRepository ?: return
        extendedScope.launch {
            val ratings = runCatching { repo.refresh(subject) }.getOrDefault(emptyList())
            _uiState.update { it.copy(externalRatings = ratings) }
        }
    }

    /** 手动录入权威机构成绩（Fami通 / Billboard / Oricon 等无 API 的机构）。 */
    fun addManualAward(sourceId: String, score: Float?, scoreMax: Float, rankPosition: Int?, note: String?) {
        val subjectId = _uiState.value.subject?.subjectId ?: return
        val repo = manualAwardRepository ?: return
        extendedScope.launch {
            repo.add(subjectId, sourceId, score, scoreMax, rankPosition, note)
            val awards = repo.awardsOf(subjectId)
            _uiState.update { it.copy(manualAwards = awards) }
        }
    }

    fun removeManualAward(id: Long) {
        val subjectId = _uiState.value.subject?.subjectId ?: return
        val repo = manualAwardRepository ?: return
        extendedScope.launch {
            repo.remove(id)
            val awards = repo.awardsOf(subjectId)
            _uiState.update { it.copy(manualAwards = awards) }
        }
    }

    /**
     * TMDb 补充：绑定状态 + 候选 + 详情 + 剧照。全部失败静默。
     *
     * 第 4 轮 C 的三处修复：
     * 1. 覆盖范围不再硬编码 ANIME/REAL/OTHER，改问 [RatingSourceRegistry.forType]（含用户开关）；
     * 2. 候选/详情同时支持 tv 与 movie 两种 provider；
     * 3. **不再因为「没有候选」就整块不渲染**——[tmdbSupported] 只要为真，
     *    UI 就必须给出「搜索 / 粘贴 ID」入口。
     */
    private suspend fun loadTmdbSupplement() {
        val subject = _uiState.value.subject ?: return
        val repo = tmdbRepository ?: return
        val configured = runCatching { repo.isConfigured() }.getOrDefault(false)
        if (!configured) {
            _uiState.update { it.copy(tmdbSupported = false) }
            return
        }
        val supported = tmdbSourceCovers(subject)
        if (!supported) {
            _uiState.update { it.copy(tmdbSupported = false) }
            return
        }

        val ids = runCatching { externalIdRepository?.entitiesOf(subject.subjectId) }.getOrNull().orEmpty()
        val tvBinding = ids.firstOrNull {
            it.provider == com.otakup.niriko.data.repository.TmdbRepository.PROVIDER_TMDB_TV
        }
        val movieBinding = ids.firstOrNull {
            it.provider == com.otakup.niriko.data.repository.TmdbRepository.PROVIDER_TMDB_MOVIE
        }
        val detail = tvBinding?.externalId?.toIntOrNull()?.let { repo.tvDetail(it) }
        val backdrops = if (tvBinding != null) repo.backdropCandidates(subject) else emptyList()
        val candidates = if (tvBinding == null && movieBinding == null) {
            runCatching { repo.searchCandidates(subject) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        _uiState.update {
            it.copy(
                tmdbSupported = true,
                tmdbBinding = tvBinding,
                tmdbMovieBinding = movieBinding,
                tmdbDetail = detail,
                tmdbBackdrops = backdrops,
                tmdbCandidates = candidates,
                // 已绑定后清掉手动入口的残留状态
                tmdbPastedCandidate = if (tvBinding != null || movieBinding != null) null else it.tmdbPastedCandidate,
            )
        }
    }

    /** TMDb 是否覆盖此作品类型（含「覆盖游戏/书籍/音乐」开关）。 */
    private suspend fun tmdbSourceCovers(
        subject: com.otakup.niriko.data.local.entity.SubjectEntity,
    ): Boolean {
        val keys = runCatching { externalRatingKeys() }.getOrNull() ?: return false
        return com.otakup.niriko.data.remote.rating.RatingSourceRegistry
            .forType(subject.type, keys)
            .any { it.id == com.otakup.niriko.data.remote.rating.ExternalRating.SOURCE_TMDB }
    }

    /** 构造各源的密钥包（含 TMDb 覆盖开关）。 */
    private suspend fun externalRatingKeys(): com.otakup.niriko.data.remote.rating.RatingSourceKeys {
        val s = settingsForSources()
        return com.otakup.niriko.data.remote.rating.RatingSourceKeys(
            tmdbApiKey = s.tmdbApiKey,
            omdbApiKey = s.omdbApiKey,
            igdbClientId = s.igdbClientId,
            igdbClientSecret = s.igdbClientSecret,
            rawgApiKey = s.rawgApiKey,
            discogsToken = s.discogsToken,
            openCriticApiKey = s.openCriticApiKey,
            tmdbIncludeNonTvTypes = s.tmdbIncludeNonTvTypes,
        )
    }

    private suspend fun settingsForSources(): com.otakup.niriko.data.settings.AppSettings =
        nirikoApp().settingsDataStore.settings.first()

    /**
     * 统一匹配服务（第 4 轮 D）。
     *
     * 目前注册了 VNDB；TMDb 走自己的 [TmdbRatingSource] 候选链路（它已有成熟的
     * 季选择逻辑），AniList 走 [AniListRatingSource]（自动展示分数）。
     * 后续接 IGDB / RAWG / 豆瓣时**只需在这里加一行**，UI 与打分口径自动复用。
     */
    private fun matchService(): com.otakup.niriko.data.match.ExternalMatchService =
        com.otakup.niriko.data.match.ExternalMatchService(
            listOf(com.otakup.niriko.data.match.VndbProviderMatcher())
        )

    /** 用户在候选列表里确认绑定 TMDb 条目（保守匹配：只有此处才会写库）。 */
    fun bindTmdb(candidate: com.otakup.niriko.data.remote.rating.RatingCandidate) {
        val subject = _uiState.value.subject ?: return
        val repo = externalIdRepository ?: return
        extendedScope.launch {
            repo.bind(
                subjectId = subject.subjectId,
                provider = candidate.provider,
                externalId = candidate.externalId,
                titleSnapshot = candidate.title,
                confidence = 1f,
                bindMethod = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.METHOD_MANUAL,
                subKey = candidate.subKey,
            )
            _emitSnackbar("已绑定 TMDb：${candidate.title}")
            _uiState.update { it.copy(tmdbPastedCandidate = null, tmdbManualMessage = null) }
            loadTmdbSupplement()
            loadEpisodeRatingData()
            loadExternalRatingData()
        }
    }

    fun unbindTmdb() {
        val subject = _uiState.value.subject ?: return
        val repo = externalIdRepository ?: return
        extendedScope.launch {
            // tv 与 movie 两种绑定都要解除，否则「movie 绑定残留」会让下次加载误判为已绑定
            repo.unbind(subject.subjectId, com.otakup.niriko.data.repository.TmdbRepository.PROVIDER_TMDB_TV)
            repo.unbind(subject.subjectId, com.otakup.niriko.data.repository.TmdbRepository.PROVIDER_TMDB_MOVIE)
            runCatching {
                nirikoDatabase().externalRatingDao()
                    .deleteEpisodeRatingsBySource(subject.subjectId, com.otakup.niriko.data.remote.rating.ExternalRating.SOURCE_TMDB)
            }
            _uiState.update {
                it.copy(
                    tmdbBinding = null,
                    tmdbMovieBinding = null,
                    tmdbDetail = null,
                    tmdbBackdrops = emptyList(),
                    episodeRatings = emptyMap(),
                )
            }
            loadTmdbSupplement()
        }
    }

    /**
     * 加载更换封面用的候选海报（对齐 AniShelf 的 PosterSelection：多语言优先级排序）。
     * 由 UI 在选择器打开时触发，不在详情页加载路径上（避免每次进详情页都发请求）。
     */
    fun loadCoverCandidates() {
        val subject = _uiState.value.subject ?: return
        val repo = tmdbRepository ?: return
        if (_uiState.value.coverCandidatesLoading) return
        if (_uiState.value.coverCandidates.isNotEmpty()) return
        extendedScope.launch {
            // 修复 BUG-11：未配置 TMDb 时不要置 loading（会闪一下且毫无意义）
            if (!runCatching { repo.isConfigured() }.getOrDefault(false)) return@launch
            _uiState.update { it.copy(coverCandidatesLoading = true) }
            val posters = runCatching { repo.posterCandidates(subject) }.getOrDefault(emptyList())
            _uiState.update { it.copy(coverCandidates = posters, coverCandidatesLoading = false) }
        }
    }

    /**
     * 手动按关键词搜索 TMDb 候选（第 4 轮 C：手动入口）。
     *
     * 改造前 keyword 参数**从未被使用**——函数体直接重新按作品标题搜了一遍，
     * 于是「搜索更多」按钮点了跟没点一样。现在真的用用户输入的关键词。
     */
    fun searchMoreTmdb(keyword: String) {
        val subject = _uiState.value.subject ?: return
        val repo = externalRatingRepository ?: return
        val query = keyword.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(tmdbManualMessage = "请输入要搜索的关键词") }
            return
        }
        extendedScope.launch {
            _uiState.update { it.copy(tmdbManualLoading = true, tmdbManualMessage = null) }
            val candidates = runCatching { repo.searchTmdbCandidatesByQuery(subject, query) }
                .getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    tmdbCandidates = candidates,
                    tmdbManualLoading = false,
                    tmdbManualQuery = query,
                    tmdbManualMessage = if (candidates.isEmpty()) {
                        "没有搜到「$query」的 TMDb 条目"
                    } else {
                        "找到 ${candidates.size} 个候选，点选即绑定"
                    },
                )
            }
        }
    }

    /**
     * 粘贴 TMDb ID 或链接 → 解析 → **只产出待确认候选**（不直接写库）。
     *
     * 保守匹配的边界：粘贴 ID 是用户明确意图，但 id 仍可能填错（复制到别的作品），
     * 因此照旧让用户点一下「绑定」确认。
     */
    fun pasteTmdbIdOrUrl(input: String) {
        val subject = _uiState.value.subject ?: return
        val repo = externalRatingRepository ?: return
        val text = input.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(tmdbManualMessage = "请粘贴 TMDb ID 或链接") }
            return
        }
        val parsed = com.otakup.niriko.data.remote.rating.sources.TmdbRatingSource.parseIdOrUrl(text)
        if (parsed == null) {
            _uiState.update {
                it.copy(
                    tmdbPastedCandidate = null,
                    tmdbManualMessage = "无法从输入里解析出 TMDb ID；" +
                        "支持 42509、tv/42509、movie/129 或完整 themoviedb.org 链接",
                )
            }
            return
        }
        extendedScope.launch {
            _uiState.update { it.copy(tmdbManualLoading = true, tmdbManualMessage = null) }
            val candidate = runCatching { repo.resolveTmdbId(subject, text) }.getOrNull()
            _uiState.update {
                it.copy(
                    tmdbPastedCandidate = candidate,
                    tmdbManualLoading = false,
                    tmdbManualMessage = if (candidate == null) {
                        "TMDb #${parsed.second} 不存在或请求失败（检查 API Key / 镜像地址）"
                    } else {
                        "已解析到「${candidate.title}」，确认后绑定"
                    },
                )
            }
        }
    }

    /** 清掉手动入口的临时反馈。 */
    fun clearTmdbManualMessage() {
        _uiState.update { it.copy(tmdbManualMessage = null, tmdbPastedCandidate = null) }
    }

    private fun _emitSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

}

class SubjectDetailViewModelFactory(
    private val subjectRepository: SubjectRepository,
    private val collectionRepository: CollectionRepository,
    private val remoteDataSource: SubjectRemoteDataSource,
    private val subjectId: Long,
    private val context: android.content.Context,
    private val steamRepository: SteamRepository? = null,
    private val vndbRepository: VndbRepository? = null,
    private val anilistRepository: com.otakup.niriko.data.repository.AniListRepository? = null,
    private val anitabiRepository: com.otakup.niriko.data.repository.AnitabiRepository? = null,
    private val externalRatingRepository: com.otakup.niriko.data.repository.ExternalRatingRepository? = null,
    private val episodeRatingRepository: com.otakup.niriko.data.repository.EpisodeRatingRepository? = null,
    private val tmdbRepository: com.otakup.niriko.data.repository.TmdbRepository? = null,
    private val externalIdRepository: com.otakup.niriko.data.repository.ExternalIdRepository? = null,
    private val manualAwardRepository: com.otakup.niriko.data.repository.ManualAwardRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubjectDetailViewModel::class.java)) {
            return SubjectDetailViewModel(
                subjectRepository, collectionRepository, remoteDataSource, subjectId,
                context.applicationContext, steamRepository, vndbRepository, anilistRepository, anitabiRepository,
                externalRatingRepository, episodeRatingRepository, tmdbRepository, externalIdRepository,
                manualAwardRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
