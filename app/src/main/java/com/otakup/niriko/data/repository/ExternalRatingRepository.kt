package com.otakup.niriko.data.repository

import android.util.Log
import com.otakup.niriko.data.local.dao.ExternalIdDao
import com.otakup.niriko.data.local.dao.ExternalRatingDao
import com.otakup.niriko.data.local.dao.SteamDao
import com.otakup.niriko.data.local.dao.VndbDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.SubjectExternalRatingEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.remote.rating.RatingCandidate
import com.otakup.niriko.data.remote.rating.RatingSourceKeys
import com.otakup.niriko.data.remote.rating.RatingSourceRegistry
import com.otakup.niriko.data.remote.rating.sources.SteamReviewSource
import com.otakup.niriko.data.remote.rating.sources.VndbRatingSource
import com.otakup.niriko.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

private const val TAG = "ExternalRatingRepo"

/**
 * 权威评分聚合仓储。
 *
 * ## 职责
 *
 * 1. 把**用户已配置**的评分源按作品类型并行抓取（单源失败静默丢弃）；
 * 2. 与**本地已有数据**（Bangumi / Bilibili / Steam Metacritic）合并成统一列表；
 * 3. 落库缓存（`subject_external_ratings`），按 [com.otakup.niriko.data.refresh.RefreshResource.EXTERNAL_RATINGS] 判新鲜度；
 * 4. 提供"外部身份候选搜索"给详情页绑定 UI。
 *
 * ## 关键设计
 *
 * - **未配置 key 的源直接跳过**（不出现在 UI），未绑定的源不发起请求；
 * - **单源异常绝不影响其它源**（每个源单独 runCatching）；
 * - Steam / VNDB 的 id 来自既有绑定表，通过伪 provider 注入，无需用户再绑一次。
 */
class ExternalRatingRepository(
    private val externalIdDao: ExternalIdDao,
    private val externalRatingDao: ExternalRatingDao,
    private val steamDao: SteamDao,
    private val vndbDao: VndbDao,
    private val settingsProvider: suspend () -> AppSettings,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * AniList 绑定读取（可选）。
     *
     * 第 4 轮 F：AniList 升格为正常评分源后，需要把「已绑定的 anilistId」注入
     * [resolveIds]，这样已绑定用户走 id 精确取分、未绑定用户走自动匹配。
     * 声明为可空是为了不打乱既有构造点（NirikoApplication）。
     */
    private val anilistDao: com.otakup.niriko.data.local.dao.AniListDao? = null,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 读取（缓存优先）。 */
    suspend fun ratings(subject: SubjectEntity): List<ExternalRating> = withContext(Dispatchers.IO) {
        val settings = settingsOrNull() ?: return@withContext emptyList()
        val local = localRatings(subject)
        if (!settings.externalRatingsEnabled) return@withContext local

        val cached = runCatching { externalRatingDao.getSubjectRatings(subject.subjectId) }
            .getOrDefault(emptyList())
        val cachedRatings = cached.map { it.toExternalRating() }
        val missing = availableSources(subject.type, settings).filter { source ->
            cachedRatings.none { it.sourceId == source.id }
        }
        // 真正按 RefreshResource.EXTERNAL_RATINGS 判定新鲜度：
        // 改造前只看"缓存里有没有行"，于是缓存永不刷新（Steam 好评率/票数会长期停在上一次的值）。
        val newest = cached.maxOfOrNull { it.fetchedAt } ?: 0L
        val decision = com.otakup.niriko.data.refresh.FreshnessDecider.decideByTimestamp(
            policy = com.otakup.niriko.data.refresh.RefreshResource.EXTERNAL_RATINGS,
            lastSuccessAt = newest,
            now = clock(),
        )
        if (missing.isEmpty() &&
            decision == com.otakup.niriko.data.refresh.RefreshDecision.FRESH
        ) {
            return@withContext (local + cachedRatings).sortedByDescending { it.score ?: -1f }
        }

        val fetched = fetchSources(subject, settings, missing)
        if (fetched.isNotEmpty()) {
            runCatching { externalRatingDao.upsertSubjectRatings(fetched.map { it.toEntity(subject.subjectId) }) }
        }
        (local + cachedRatings + fetched).distinctBy { it.sourceId }
            .sortedByDescending { it.score ?: -1f }
    }

    /** 强制刷新（忽略缓存；用于「重试」）。 */
    suspend fun refresh(subject: SubjectEntity): List<ExternalRating> = withContext(Dispatchers.IO) {
        val settings = settingsOrNull() ?: return@withContext emptyList()
        runCatching { externalRatingDao.deleteSubjectRatings(subject.subjectId) }
        ratings(subject)
    }

    /** 某个 provider 的外部身份候选（供详情页绑定 UI）。 */
    suspend fun searchCandidates(subject: SubjectEntity, provider: String): List<RatingCandidate> =
        withContext(Dispatchers.IO) {
            val settings = settingsOrNull() ?: return@withContext emptyList()
            val source = RatingSourceRegistry.all.firstOrNull { provider in it.requiresExternalId }
                ?: return@withContext emptyList()
            if (!source.isAvailable(keysOf(settings))) return@withContext emptyList()
            runCatching { source.searchCandidates(subject, keysOf(settings)) }
                .onFailure { Log.w(TAG, "searchCandidates failed provider=$provider", it) }
                .getOrDefault(emptyList())
        }

    /** 本地已有评分（不需要任何外部 key，永远可用）：Bangumi / Bilibili / Steam Metacritic。 */
    fun localRatings(subject: SubjectEntity): List<ExternalRating> = buildList {
        subject.ratingScore?.takeIf { it > 0f }?.let { score ->
            add(
                ExternalRating(
                    sourceId = ExternalRating.SOURCE_BANGUMI,
                    label = "Bangumi",
                    score = score,
                    nativeScore = score,
                    scoreMax = 10f,
                    voteCount = subject.ratingTotal,
                    sourceUrl = "https://bgm.tv/subject/${subject.subjectId}",
                )
            )
        }
        subject.biliScore?.takeIf { it > 0f }?.let { score ->
            add(
                ExternalRating(
                    sourceId = ExternalRating.SOURCE_BILIBILI,
                    label = "Bilibili",
                    score = score,
                    nativeScore = score,
                    scoreMax = 10f,
                    voteCount = subject.biliRatingTotal,
                )
            )
        }
    }

    /** Steam Metacritic 字段（从 steam_games 读，不发请求）。 */
    suspend fun steamMetacritic(subject: SubjectEntity): ExternalRating? = withContext(Dispatchers.IO) {
        val score = runCatching { steamDao.getGame(subject.subjectId)?.metacriticScore }.getOrNull()
            ?: return@withContext null
        if (score <= 0) return@withContext null
        ExternalRating(
            sourceId = ExternalRating.SOURCE_METACRITIC,
            label = "Metacritic",
            score = ExternalRating.toTenPoint(score.toFloat(), 100f),
            nativeScore = score.toFloat(),
            scoreMax = 100f,
            sourceUrl = "https://www.metacritic.com/search/${subject.displayTitle}",
        )
    }

    // ==================== 内部 ====================

    private suspend fun settingsOrNull(): AppSettings? =
        runCatching { settingsProvider() }.getOrNull()

    private fun keysOf(settings: AppSettings) = RatingSourceKeys(
        tmdbApiKey = settings.tmdbApiKey,
        omdbApiKey = settings.omdbApiKey,
        igdbClientId = settings.igdbClientId,
        igdbClientSecret = settings.igdbClientSecret,
        rawgApiKey = settings.rawgApiKey,
        discogsToken = settings.discogsToken,
        openCriticApiKey = settings.openCriticApiKey,
        tmdbIncludeNonTvTypes = settings.tmdbIncludeNonTvTypes,
    )

    /**
     * 用自定义关键词搜 TMDb 候选（手动入口用，不依赖作品标题）。
     *
     * 用户反馈「未绑定时没有手动入口，候选为空就整块不渲染」——
     * 本方法与 [searchCandidates] 一起构成「永远有入口」的后端。
     */
    suspend fun searchTmdbCandidatesByQuery(
        subject: SubjectEntity,
        query: String,
    ): List<RatingCandidate> = withContext(Dispatchers.IO) {
        val settings = settingsOrNull() ?: return@withContext emptyList()
        val source = acceptTmdbKey(settings) ?: return@withContext emptyList()
        runCatching { source.searchCandidatesByQuery(subject, query) }
            .onFailure { Log.w(TAG, "tmdb manual search failed", it) }
            .getOrDefault(emptyList())
    }

    /** 按用户粘贴的 TMDb ID / 链接取单个候选（手动入口用）。 */
    suspend fun resolveTmdbId(
        subject: SubjectEntity,
        rawInput: String,
    ): RatingCandidate? = withContext(Dispatchers.IO) {
        val settings = settingsOrNull() ?: return@withContext null
        val source = acceptTmdbKey(settings) ?: return@withContext null
        val parsed = com.otakup.niriko.data.remote.rating.sources.TmdbRatingSource
            .parseIdOrUrl(rawInput) ?: return@withContext null
        val (provider, id) = parsed
        runCatching { source.candidateById(subject, provider, id) }
            .onFailure { Log.w(TAG, "tmdb manual id failed", it) }
            .getOrNull()
    }

    /** 把 TMDb key 写进客户端并返回源；未配置 key 时返回 null。 */
    private fun acceptTmdbKey(
        settings: AppSettings,
    ): com.otakup.niriko.data.remote.rating.sources.TmdbRatingSource? {
        if (!settings.externalRatingsEnabled) return null
        if (settings.tmdbApiKey.isBlank()) return null
        com.otakup.niriko.data.remote.tmdb.TmdbClient.setApiKey(settings.tmdbApiKey)
        com.otakup.niriko.data.remote.tmdb.TmdbClient.setEndpoint(
            settings.tmdbApiUrl,
            settings.tmdbImageUrl,
        )
        return com.otakup.niriko.data.remote.rating.sources.TmdbRatingSource()
    }

    private fun availableSources(type: SubjectType, settings: AppSettings) =
        RatingSourceRegistry.available(type, keysOf(settings))

    /** 解析所有可用 id（含 Steam / VNDB / AniList 伪 provider）。 */
    private suspend fun resolveIds(subjectId: Long): Map<String, String> = coroutineScope {
        val external = async { runCatching { externalIdDao.getBySubject(subjectId) }.getOrDefault(emptyList()) }
        val steam = async { runCatching { steamDao.getBindingBySubjectId(subjectId) }.getOrNull() }
        val vndb = async { runCatching { vndbDao.getBindingBySubjectId(subjectId) }.getOrNull() }
        val anilist = async {
            anilistDao?.let { runCatching { it.getBindingBySubjectId(subjectId) }.getOrNull() }
        }
        buildMap {
            external.await().forEach { put(it.provider, it.externalId) }
            steam.await()?.let { put(SteamReviewSource.PROVIDER_STEAM, it.steamAppId.toString()) }
            vndb.await()?.let { put(VndbRatingSource.PROVIDER_VNDB, it.vndbId) }
            anilist.await()?.let {
                put(com.otakup.niriko.data.remote.rating.sources.AniListRatingSource.PROVIDER_ANILIST, it.anilistId.toString())
            }
        }
    }

    private suspend fun fetchSources(
        subject: SubjectEntity,
        settings: AppSettings,
        sources: List<com.otakup.niriko.data.remote.rating.RatingSource>,
    ): List<ExternalRating> = coroutineScope {
        val ids = resolveIds(subject.subjectId)
        val keys = keysOf(settings)
        sources
            .filter { source -> source.requiresExternalId.all { it in ids } }
            .map { source ->
                async {
                    runCatching { source.fetch(subject, ids, keys) }
                        .onFailure { Log.w(TAG, "fetch failed source=${source.id}", it) }
                        .getOrNull()
                }
            }
            .mapNotNull { it.await() }
    }

    private fun SubjectExternalRatingEntity.toExternalRating() = ExternalRating(
        sourceId = sourceId,
        label = label,
        score = score,
        nativeScore = nativeScore,
        scoreMax = scoreMax,
        voteCount = voteCount,
        sourceUrl = sourceUrl,
        note = runCatching {
            val obj = json.parseToJsonElement(extraJson)
            (obj as? kotlinx.serialization.json.JsonObject)
                ?.get("note")
                ?.let { (it as? JsonPrimitive)?.content }
        }.getOrNull(),
        fetchedAt = fetchedAt,
    )

    private fun ExternalRating.toEntity(subjectId: Long) = SubjectExternalRatingEntity(
        subjectId = subjectId,
        sourceId = sourceId,
        label = label,
        score = score,
        nativeScore = nativeScore,
        scoreMax = scoreMax,
        voteCount = voteCount,
        sourceUrl = sourceUrl,
        extraJson = note?.let { JSONObject().put("note", it).toString() } ?: "{}",
        fetchedAt = clock(),
    )
}
