package com.otakup.niriko.data.repository

import android.util.Log
import com.otakup.niriko.data.calculator.EpisodeAlignment
import com.otakup.niriko.data.calculator.EpisodeRatingAnalyzer
import com.otakup.niriko.data.local.dao.EpisodeDao
import com.otakup.niriko.data.local.dao.ExternalRatingDao
import com.otakup.niriko.data.local.entity.EpisodeRatingEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.remote.rating.sources.OmdbRatingSource
import com.otakup.niriko.data.remote.tmdb.TmdbClient
import com.otakup.niriko.data.remote.tmdb.dto.TmdbEpisodeDto
import com.otakup.niriko.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val TAG = "EpisodeRatingRepo"

/**
 * 每集评分仓储（本方案的核心）。
 *
 * ## 数据流
 *
 * ```
 * Bangumi subjectId
 *   → subject_external_ids(tmdb_tv) 得到 TMDb tv id（用户确认过的绑定）
 *   → GET /3/tv/{id}                 整剧详情（含 seasons[]、imdb_id）
 *   → 选季（EpisodeAlignment.pickSeason：年份 + 集数最接近）
 *   → GET /3/tv/{id}/season/{n}      一次拿到整季 episodes[]（含 vote_average / vote_count / still_path）
 *   → 与 Bangumi episodes 对齐（EpisodeAlignment.align）
 *   → 落 episode_ratings(sourceId=tmdb) + episodes.stillUrl
 * ```
 *
 * IMDb 逐集是**可选的第二层**：默认关闭（OMDb 免费额度 1000/天，且每集要 2 次请求），
 * 由用户在剧集列表显式触发 [loadImdbEpisodeRatings]。
 */
class EpisodeRatingRepository(
    private val episodeDao: EpisodeDao,
    private val externalRatingDao: ExternalRatingDao,
    private val externalIdRepository: ExternalIdRepository,
    private val settingsProvider: suspend () -> AppSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 拉取并落库 TMDb 每集评分 + 剧照；返回对齐结果供 UI 展示「N 集可对齐 / M 集未匹配」。 */
    suspend fun refreshFromTmdb(subject: SubjectEntity): TmdbEpisodeLoad =
        withContext(Dispatchers.IO) {
            val settings = settingsOrNull() ?: return@withContext TmdbEpisodeLoad.Unavailable
            if (!settings.externalRatingsEnabled || settings.tmdbApiKey.isBlank()) {
                return@withContext TmdbEpisodeLoad.Unavailable
            }
            TmdbClient.setApiKey(settings.tmdbApiKey)
            TmdbClient.setEndpoint(settings.tmdbApiUrl, settings.tmdbImageUrl)

            val tvId = externalIdRepository.get(subject.subjectId, PROVIDER_TMDB_TV)
                ?.externalId?.toIntOrNull()
                ?: return@withContext TmdbEpisodeLoad.NotBound

            val detail = runCatching {
                TmdbClient.apiService.tvDetail(tvId, com.otakup.niriko.data.remote.tmdb.TmdbLanguage.DEFAULT.code, "external_ids")
            }.getOrNull() ?: return@withContext TmdbEpisodeLoad.Failed

            val seasonRefs = detail.seasons.map {
                EpisodeAlignment.SeasonRef(
                    seasonNumber = it.seasonNumber,
                    year = it.airDate?.take(4)?.toIntOrNull(),
                    episodeCount = it.episodeCount,
                    name = it.name,
                )
            }
            val seasonNumber = externalIdRepository.get(subject.subjectId, PROVIDER_TMDB_TV)
                ?.subKey?.toIntOrNull()
                ?: EpisodeAlignment.pickSeason(
                    seasonRefs,
                    subject.airDate?.take(4)?.toIntOrNull(),
                    subject.totalEpisodes,
                )
                ?: return@withContext TmdbEpisodeLoad.Failed

            val season = runCatching {
                TmdbClient.apiService.seasonDetail(tvId, seasonNumber, com.otakup.niriko.data.remote.tmdb.TmdbLanguage.DEFAULT.code)
            }.getOrNull() ?: return@withContext TmdbEpisodeLoad.Failed

            // IMDb id 回填：整剧 imdb id 若尚未绑定则顺手写入（仍属"明确 ID"，可自动写）
            detail.externalIds?.imdbId?.takeIf { it.startsWith("tt") }?.let { imdbId ->
                if (externalIdRepository.get(subject.subjectId, PROVIDER_IMDB) == null) {
                    externalIdRepository.bind(
                        subjectId = subject.subjectId,
                        provider = PROVIDER_IMDB,
                        externalId = imdbId,
                        titleSnapshot = detail.name,
                        confidence = 1f,
                        bindMethod = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.METHOD_INFOBOX,
                    )
                }
            }

            val bangumiEps = runCatching { episodeDao.getMainEpisodes(subject.subjectId) }
                .getOrDefault(emptyList())
                .map { EpisodeAlignment.BangumiEp(it.epId, it.sort) }

            val tmdbEps = season.episodes.map { it.toAlignmentEp() }
            val alignment = EpisodeAlignment.align(bangumiEps, tmdbEps)

            val now = clock()
            val ratingRows = alignment.matched
                .filter { it.tmdb.score != null }
                .map {
                    EpisodeRatingEntity(
                        epId = it.epId,
                        subjectId = subject.subjectId,
                        sourceId = ExternalRating.SOURCE_TMDB,
                        score = it.tmdb.score,
                        scoreMax = 10f,
                        voteCount = it.tmdb.voteCount,
                        fetchedAt = now,
                    )
                }
            runCatching {
                externalRatingDao.deleteEpisodeRatingsBySource(subject.subjectId, ExternalRating.SOURCE_TMDB)
                externalRatingDao.upsertEpisodeRatings(ratingRows)
            }

            alignment.matched.forEach { aligned ->
                runCatching { episodeDao.updateStillUrl(aligned.epId, aligned.tmdb.stillUrl) }
            }

            TmdbEpisodeLoad(
                state = TmdbEpisodeLoad.State.OK,
                seasonNumber = seasonNumber,
                matchedCount = alignment.matchedCount,
                unmatchedBangumi = alignment.unmatchedBangumi,
                unmatchedTmdb = alignment.unmatchedTmdb,
                withScoreCount = ratingRows.size,
            )
        }

    /**
     * 叠加 IMDb 逐集评分（可选层）。
     *
     * 每集需要 2 次请求（TMDb episode external_ids + OMDb）。为控制配额与限流：
     * 并发上限 4、每批之间 200ms 间隔；只处理尚未有 IMDb 评分的集。
     *
     * 第 4 轮 H 增加两条改进：
     * 1. **季号写回**：绑定 TMDb 时若没有 subKey，这里补齐（否则后续链路永远拿不到季号）；
     * 2. **OMDb 兜底**：某集在 TMDb 上没有 external_ids 时，改用整剧 IMDb id + Season/Episode
     *    直接问 OMDb，避免「有 key 但部分集没分」。
     */
    suspend fun loadImdbEpisodeRatings(subject: SubjectEntity): ImdbEpisodeLoad =
        withContext(Dispatchers.IO) {
            val settings = settingsOrNull() ?: return@withContext ImdbEpisodeLoad.Unavailable
            val apiKey = settings.omdbApiKey.trim()
            if (!settings.externalRatingsEnabled || apiKey.isEmpty()) return@withContext ImdbEpisodeLoad.Unavailable
            TmdbClient.setApiKey(settings.tmdbApiKey)
            TmdbClient.setEndpoint(settings.tmdbApiUrl, settings.tmdbImageUrl)

            val binding = externalIdRepository.get(subject.subjectId, PROVIDER_TMDB_TV)
                ?: return@withContext ImdbEpisodeLoad.NotBound
            val tvId = binding.externalId.toIntOrNull() ?: return@withContext ImdbEpisodeLoad.Failed

            // 季号：优先用绑定里的 subKey；没有就现场挑一次并**写回**（H 项的核心修复）
            val seasonNumber = binding.subKey?.toIntOrNull()
                ?: resolveAndPersistSeason(subject, tvId, binding)
                ?: return@withContext ImdbEpisodeLoad.Failed

            val mainEps = runCatching { episodeDao.getMainEpisodes(subject.subjectId) }.getOrDefault(emptyList())
            if (mainEps.isEmpty()) return@withContext ImdbEpisodeLoad.Failed

            // 整剧 IMDb id：OMDb 兜底路径需要它（TMDb external_ids 里带）
            val seriesImdbId = externalIdRepository.get(subject.subjectId, PROVIDER_IMDB)?.externalId
                ?: runCatching {
                    TmdbClient.apiService.tvDetail(tvId, com.otakup.niriko.data.remote.tmdb.TmdbLanguage.DEFAULT.code, "external_ids")
                        .externalIds?.imdbId
                }.getOrNull()?.takeIf { it.startsWith("tt") }

            val existing = runCatching {
                externalRatingDao.getEpisodeRatingsBySource(subject.subjectId, ExternalRating.SOURCE_IMDB)
            }.getOrDefault(emptyList()).map { it.epId }.toHashSet()

            val semaphore = Semaphore(4)
            val now = clock()
            var viaOmdbFallback = 0
            val rows = coroutineScope {
                mainEps.mapIndexedNotNull { index, ep ->
                    if (ep.epId in existing) return@mapIndexedNotNull null
                    async {
                        semaphore.withPermit {
                            if (index % 8 == 0) delay(200)
                            val episodeNumber = kotlin.math.round(ep.sort).toInt()
                            if (episodeNumber <= 0) return@withPermit null

                            // 主路径：TMDb 单集 external_ids → OMDb 按 tt id 查
                            val perEpisodeImdbId = runCatching {
                                TmdbClient.apiService.episodeExternalIds(tvId, seasonNumber, episodeNumber).imdbId
                            }.getOrNull()?.takeIf { it.startsWith("tt") }

                            val rating = if (perEpisodeImdbId != null) {
                                OmdbRatingSource.fetchById(perEpisodeImdbId, apiKey)
                            } else {
                                null
                            } ?: seriesImdbId?.let { series ->
                                // 兜底路径：整剧 id + Season/Episode（不依赖 TMDb 单集 external_ids）
                                val fallback = OmdbRatingSource.fetchEpisodeBySeries(
                                    seriesImdbId = series,
                                    season = seasonNumber,
                                    episode = episodeNumber,
                                    apiKey = apiKey,
                                )
                                if (fallback != null) viaOmdbFallback++
                                fallback
                            } ?: return@withPermit null

                            EpisodeRatingEntity(
                                epId = ep.epId,
                                subjectId = subject.subjectId,
                                sourceId = ExternalRating.SOURCE_IMDB,
                                score = rating.nativeScore,
                                scoreMax = 10f,
                                voteCount = rating.voteCount,
                                fetchedAt = now,
                            )
                        }
                    }
                }.mapNotNull { it.await() }
            }

            if (rows.isNotEmpty()) {
                runCatching { externalRatingDao.upsertEpisodeRatings(rows) }
            }
            ImdbEpisodeLoad(
                state = ImdbEpisodeLoad.State.OK,
                loaded = rows.size,
                total = mainEps.size,
                viaOmdbFallback = viaOmdbFallback,
                seasonNumber = seasonNumber,
            )
        }

    /**
     * 现场挑季并把结果写回绑定记录的 subKey。
     *
     * 改造前只有「自动挑季」而**不写回**，于是 `loadImdbEpisodeRatings` 里
     * `binding.subKey` 恒为 null → 直接 FAILED（用户在设置里配好了一切也不可用）。
     */
    private suspend fun resolveAndPersistSeason(
        subject: SubjectEntity,
        tvId: Int,
        binding: com.otakup.niriko.data.local.entity.SubjectExternalIdEntity,
    ): Int? {
        val detail = runCatching {
            TmdbClient.apiService.tvDetail(tvId, com.otakup.niriko.data.remote.tmdb.TmdbLanguage.DEFAULT.code, null)
        }.getOrNull() ?: return null
        val refs = detail.seasons.map {
            EpisodeAlignment.SeasonRef(
                seasonNumber = it.seasonNumber,
                year = it.airDate?.take(4)?.toIntOrNull(),
                episodeCount = it.episodeCount,
                name = it.name,
            )
        }
        val picked = EpisodeAlignment.pickSeason(
            refs,
            subject.airDate?.take(4)?.toIntOrNull(),
            subject.totalEpisodes,
        ) ?: return null
        runCatching {
            externalIdRepository.bind(
                subjectId = subject.subjectId,
                provider = binding.provider,
                externalId = binding.externalId,
                titleSnapshot = binding.titleSnapshot,
                confidence = binding.confidence,
                bindMethod = binding.bindMethod,
                subKey = picked.toString(),
            )
        }
        return picked
    }

    /**
     * 「加载 IMDb 逐集评分」入口是否可用（修复 BUG-2）。
     *
     * 此前 UI 只判断「有没有绑定 TMDb」，用户在设置里关掉开关后按钮照样出现——
     * 设置不生效比没有设置更糟。现在入口可用性 = 总开关 && IMDb 开关 && 有 OMDb key。
     */
    suspend fun isImdbEntryEnabled(): Boolean = entryStatus(null).enabled

    /**
     * IMDb 逐集入口的**可解释状态**（第 4 轮 H）。
     *
     * 返回每一项前置条件是否满足 + 一句「缺什么」，让 UI 能直接显示而不是静默隐藏。
     * 传入 [subject] 时额外检查 TMDb 绑定与 IMDb id；传 null 只做纯设置检查。
     */
    suspend fun entryStatus(subject: SubjectEntity?): ImdbEntryStatus {
        val settings = settingsOrNull()
        val ratingsOn = settings?.externalRatingsEnabled == true
        val imdbToggleOn = settings?.imdbEpisodeRatingsEnabled == true
        val omdbConfigured = settings?.omdbApiKey?.isNotBlank() == true
        val tmdbBound = subject?.let {
            externalIdRepository.get(it.subjectId, PROVIDER_TMDB_TV) != null
        } ?: false
        // 整剧 IMDb id 可以来自 external_ids 回填；只有连这个都没有才算彻底断链
        val imdbIdKnown = subject?.let {
            externalIdRepository.get(it.subjectId, PROVIDER_IMDB)?.externalId?.startsWith("tt") == true
        } ?: false

        return ImdbEntryStatus(
            externalRatingsEnabled = ratingsOn,
            imdbToggleEnabled = imdbToggleOn,
            omdbKeyConfigured = omdbConfigured,
            tmdbBound = tmdbBound,
            imdbIdKnown = imdbIdKnown,
        )
    }

    /**
     * 每集评分缓存是否仍然新鲜。
     *
     * 统一走 [RefreshResource.EPISODE_RATINGS]，UI 不再自己维护 TTL 常量
     * （改造前 VM 里另写了 `EPISODE_RATING_TTL_MS = 6h`，与这里重复且容易漂移）。
     */
    suspend fun isEpisodeRatingsFresh(subjectId: Long): Boolean {
        val newest = runCatching {
            externalRatingDao.getEpisodeRatings(subjectId).maxOfOrNull { it.fetchedAt } ?: 0L
        }.getOrDefault(0L)
        return com.otakup.niriko.data.refresh.FreshnessDecider.decideByTimestamp(
            policy = com.otakup.niriko.data.refresh.RefreshResource.EPISODE_RATINGS,
            lastSuccessAt = newest,
            now = clock(),
        ) == com.otakup.niriko.data.refresh.RefreshDecision.FRESH
    }

    /** 读取已落库的每集评分（epId → sourceId → 实体）。 */
    suspend fun ratingsOf(subjectId: Long): Map<Long, Map<String, EpisodeRatingEntity>> =
        withContext(Dispatchers.IO) {
            runCatching { externalRatingDao.getEpisodeRatings(subjectId) }
                .getOrDefault(emptyList())
                .groupBy { it.epId }
                .mapValues { (_, list) -> list.associateBy { it.sourceId } }
        }

    /** 构建走势曲线所需的点（某个来源）。 */
    fun toRatingPoints(
        episodes: List<com.otakup.niriko.data.local.entity.EpisodeEntity>,
        ratings: Map<Long, Map<String, EpisodeRatingEntity>>,
        sourceId: String,
    ): List<EpisodeRatingAnalyzer.RatingPoint> =
        episodes.mapNotNull { ep ->
            val rating = ratings[ep.epId]?.get(sourceId) ?: return@mapNotNull null
            val score = rating.score ?: return@mapNotNull null
            EpisodeRatingAnalyzer.RatingPoint(
                epId = ep.epId,
                ep = ep.ep,
                label = ep.nameCn?.takeIf { it.isNotBlank() } ?: ep.name,
                score = score,
                votes = rating.voteCount,
                sourceId = sourceId,
            )
        }

    private fun TmdbEpisodeDto.toAlignmentEp() = EpisodeAlignment.TmdbEp(
        episodeNumber = episodeNumber,
        score = voteAverage?.toFloat()?.takeIf { it > 0f },
        voteCount = voteCount,
        stillUrl = com.otakup.niriko.data.remote.tmdb.TmdbImageUrl.url(
            stillPath,
            com.otakup.niriko.data.remote.tmdb.TmdbImageUrl.STILL_MEDIUM,
        ),
    )

    private suspend fun settingsOrNull(): AppSettings? = runCatching { settingsProvider() }.getOrNull()

    /** 每集评分加载结果（供 UI 展示可解释的状态，而不是静默失败）。 */
    data class TmdbEpisodeLoad(
        val state: State,
        val seasonNumber: Int? = null,
        val matchedCount: Int = 0,
        val unmatchedBangumi: Int = 0,
        val unmatchedTmdb: Int = 0,
        val withScoreCount: Int = 0,
    ) {
        enum class State { OK, NOT_BOUND, UNAVAILABLE, FAILED }

        val isOk: Boolean get() = state == State.OK

        companion object {
            val Unavailable = TmdbEpisodeLoad(State.UNAVAILABLE)
            val NotBound = TmdbEpisodeLoad(State.NOT_BOUND)
            val Failed = TmdbEpisodeLoad(State.FAILED)
        }
    }

    data class ImdbEpisodeLoad(
        val state: State,
        val loaded: Int = 0,
        val total: Int = 0,
        /** 其中有多少集是靠 OMDb Season/Episode 兜底拿到的（TMDb 无 external_ids）。 */
        val viaOmdbFallback: Int = 0,
        val seasonNumber: Int? = null,
    ) {
        enum class State { OK, NOT_BOUND, UNAVAILABLE, FAILED }

        companion object {
            val Unavailable = ImdbEpisodeLoad(State.UNAVAILABLE)
            val NotBound = ImdbEpisodeLoad(State.NOT_BOUND)
            val Failed = ImdbEpisodeLoad(State.FAILED)
        }
    }

    /**
     * IMDb 逐集入口的前置条件逐项状态（第 4 轮 H）。
     *
     * 存在的意义：把「为什么按钮不可用」变成**可穷举、可展示**的布尔集合。
     * 改造前 UI 只有一个 `imdbAvailable = binding != null && entryEnabled`，
     * 任一条件不满足就整块消失且不说明原因。
     */
    data class ImdbEntryStatus(
        val externalRatingsEnabled: Boolean = false,
        val imdbToggleEnabled: Boolean = false,
        val omdbKeyConfigured: Boolean = false,
        val tmdbBound: Boolean = false,
        val imdbIdKnown: Boolean = false,
    ) {
        /** 三项设置全部就绪（不含绑定要求）——决定是否显示入口本身。 */
        val settingsReady: Boolean
            get() = externalRatingsEnabled && imdbToggleEnabled && omdbKeyConfigured

        /** 完全可用：设置就绪且已有可用的 IMDb/整剧信息。 */
        val enabled: Boolean
            get() = settingsReady && tmdbBound

        /**
         * 「还缺什么」的逐项清单（UI 直接逐行显示，每行可跳对应设置）。
         * 全部就绪时返回空列表。
         */
        val missing: List<String>
            get() = buildList {
                if (!externalRatingsEnabled) add("总开关「权威评分」未打开")
                if (!imdbToggleEnabled) add("「启用 IMDb 逐集评分入口」未打开")
                if (!omdbKeyConfigured) add("未配置 OMDb API Key")
                if (!tmdbBound) add("尚未绑定 TMDb 条目（需要季号才能定位单集）")
                if (tmdbBound && !imdbIdKnown) add("尚无整剧 IMDb id（首次加载时会从 TMDb 自动回填）")
            }
    }

    companion object {
        const val PROVIDER_TMDB_TV =
            com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.PROVIDER_TMDB_TV
        const val PROVIDER_IMDB =
            com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.PROVIDER_IMDB
    }
}
