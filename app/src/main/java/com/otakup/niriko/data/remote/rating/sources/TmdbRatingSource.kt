package com.otakup.niriko.data.remote.rating.sources

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.remote.rating.RatingCandidate
import com.otakup.niriko.data.remote.rating.RatingSource
import com.otakup.niriko.data.remote.rating.RatingSourceKeys
import com.otakup.niriko.data.remote.tmdb.TmdbClient
import com.otakup.niriko.data.remote.tmdb.TmdbImageUrl
import com.otakup.niriko.data.remote.tmdb.TmdbLanguage
import com.otakup.niriko.data.remote.tmdb.dto.TmdbMovieSummaryDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbTvSummaryDto

/**
 * TMDb 权威评分（影视 / 动画的主源，并可覆盖 GAME / BOOK / MUSIC）。
 *
 * ## 覆盖范围（第 4 轮 C 项）
 *
 * 改造前 [subjectTypes] 只声明 ANIME / REAL / OTHER，于是 GAME / BOOK / MUSIC
 * **连候选都渲染不出来**（用户反馈「部分作品没有 TMDb 匹配」）。
 * 现在分两层：
 * - **声明层** [subjectTypes] 保持原样（注册表按类型派发时的默认集合）；
 * - **运行时层** [isAvailableFor]：当用户打开「TMDb 覆盖游戏/书籍/音乐」时，
 *   这三类也纳入，且候选与评分走 **TMDb movie** 端点（TMDb 对动画电影、真人电影、
 *   音乐纪录片都有条目）。
 *
 * ## 保守匹配
 *
 * 候选解析**不做自动写库**，只产出候选，由用户在详情页确认（对齐项目阶段 G 的约定）。
 * 候选排序：标题相似度 + 年份邻近 + 集数接近（[TmdbMatchScorer]）。
 */
class TmdbRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_TMDB
    override val label: String = "TMDb"

    override val subjectTypes: Set<SubjectType> = setOf(
        SubjectType.ANIME, SubjectType.REAL, SubjectType.OTHER,
    )

    override val requiresKey: Boolean = true

    override val requiresExternalId: Set<String> = setOf(
        SubjectExternalIdEntity.PROVIDER_TMDB_TV,
        SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE,
    )

    override fun isAvailable(keys: RatingSourceKeys): Boolean = !keys.usable(keys.tmdbApiKey).isNullOrEmpty()

    /**
     * 该源是否覆盖此作品类型（含用户偏好开关）。
     *
     * 仓库调用它而不是直接读 [subjectTypes]，因此设置里打开「覆盖游戏/书籍/音乐」后
     * 立刻生效，无需重建注册表。
     */
    override fun isAvailableFor(type: SubjectType, keys: RatingSourceKeys): Boolean {
        if (type in subjectTypes) return true
        if (!keys.tmdbIncludeNonTvTypes) return false
        return type in NON_TV_TYPES
    }

    override suspend fun searchCandidates(subject: SubjectEntity, keys: RatingSourceKeys): List<RatingCandidate> {
        val queries = buildList {
            add(subject.titleCN)
            add(subject.title)
        }.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        val year = subject.airDate?.take(4)?.toIntOrNull()
        val seen = LinkedHashMap<String, RatingCandidate>()

        for (query in queries) {
            if (preferMovie(subject.type)) {
                searchMovieCandidates(query, year, subject, seen)
            } else {
                searchTvCandidates(query, year, subject, seen)
            }
        }
        return seen.values.sortedByDescending { it.confidence }.take(5)
    }

    /**
     * 手动指定关键词重新搜索候选（不依赖作品标题）。
     *
     * 这是「未绑定时也要有入口」的实现：用户可以拿原文名/英文名/罗马音再搜一次。
     */
    suspend fun searchCandidatesByQuery(
        subject: SubjectEntity,
        query: String,
    ): List<RatingCandidate> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val year = subject.airDate?.take(4)?.toIntOrNull()
        val seen = LinkedHashMap<String, RatingCandidate>()
        searchTvCandidates(trimmed, year, subject, seen)
        // 影视类作品手动搜索时也捎带 movie 结果：TMDb 上剧场版常只在 movie 侧有条目
        if (!preferMovie(subject.type)) {
            searchMovieCandidates(trimmed, year, subject, seen)
        }
        return seen.values.sortedByDescending { it.confidence }.take(10)
    }

    /**
     * 直接按 TMDb id 取候选（手动粘贴 ID / 链接用）。
     *
     * [provider] 决定走 tv 还是 movie 端点；id 不存在或请求失败返回 null（调用方提示用户）。
     */
    suspend fun candidateById(
        subject: SubjectEntity,
        provider: String,
        externalId: Int,
    ): RatingCandidate? = runCatching {
        val isMovie = provider == SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE
        if (isMovie) {
            val d = TmdbClient.apiService.movieDetail(externalId, TmdbLanguage.DEFAULT.code, null)
            if (d.id == 0) return@runCatching null
            RatingCandidate(
                provider = SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE,
                externalId = d.id.toString(),
                title = d.title ?: d.originalTitle ?: "#${d.id}",
                subtitle = listOfNotNull(
                    d.releaseDate?.take(4),
                    d.voteAverage?.let { "\u2605 %.1f".format(it) },
                ).joinToString(" · ").takeIf { it.isNotBlank() },
                imageUrl = TmdbImageUrl.url(d.posterPath, TmdbImageUrl.POSTER_SMALL),
                confidence = 1f,
            )
        } else {
            val d = TmdbClient.apiService.tvDetail(externalId, TmdbLanguage.DEFAULT.code, null)
            if (d.id == 0) return@runCatching null
            RatingCandidate(
                provider = SubjectExternalIdEntity.PROVIDER_TMDB_TV,
                externalId = d.id.toString(),
                title = d.name ?: d.originalName ?: "#${d.id}",
                subtitle = listOfNotNull(
                    d.firstAirDate?.take(4),
                    d.voteAverage?.let { "\u2605 %.1f".format(it) },
                ).joinToString(" · ").takeIf { it.isNotBlank() },
                imageUrl = TmdbImageUrl.url(d.posterPath, TmdbImageUrl.POSTER_SMALL),
                confidence = 1f,
            )
        }
    }.getOrNull()

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val tvId = externalIds[SubjectExternalIdEntity.PROVIDER_TMDB_TV]?.toIntOrNull()
        if (tvId != null) {
            val detail = runCatching {
                TmdbClient.apiService.tvDetail(tvId, TmdbLanguage.DEFAULT.code, "external_ids")
            }.getOrNull() ?: return null

            val score = detail.voteAverage?.toFloat() ?: return null
            return ExternalRating(
                sourceId = id,
                label = label,
                score = ExternalRating.toTenPoint(score, 10f),
                nativeScore = score,
                scoreMax = 10f,
                voteCount = detail.voteCount,
                sourceUrl = "https://www.themoviedb.org/tv/$tvId",
            )
        }
        return movieRating(externalIds)
    }

    private suspend fun movieRating(externalIds: Map<String, String>): ExternalRating? {
        val movieId = externalIds[SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE]?.toIntOrNull() ?: return null
        val detail = runCatching {
            TmdbClient.apiService.movieDetail(movieId, TmdbLanguage.DEFAULT.code, "external_ids")
        }.getOrNull() ?: return null
        val score = detail.voteAverage?.toFloat() ?: return null
        return ExternalRating(
            sourceId = id,
            label = label,
            score = ExternalRating.toTenPoint(score, 10f),
            nativeScore = score,
            scoreMax = 10f,
            voteCount = detail.voteCount,
            sourceUrl = "https://www.themoviedb.org/movie/$movieId",
        )
    }

    // ==================== 候选搜索 ====================

    private suspend fun searchTvCandidates(
        query: String,
        year: Int?,
        subject: SubjectEntity,
        into: MutableMap<String, RatingCandidate>,
    ) {
        val page = runCatching {
            TmdbClient.apiService.searchTv(
                query = query,
                language = TmdbLanguage.DEFAULT.code,
                firstAirDateYear = year,
            )
        }.getOrNull() ?: return

        for (item in page.results) {
            if (item.id == 0) continue
            val key = "${SubjectExternalIdEntity.PROVIDER_TMDB_TV}:${item.id}"
            if (into.containsKey(key)) continue
            into[key] = item.toCandidate(subject, year)
        }
    }

    private suspend fun searchMovieCandidates(
        query: String,
        year: Int?,
        subject: SubjectEntity,
        into: MutableMap<String, RatingCandidate>,
    ) {
        val page = runCatching {
            TmdbClient.apiService.searchMovie(
                query = query,
                language = TmdbLanguage.DEFAULT.code,
                year = year,
            )
        }.getOrNull() ?: return

        for (item in page.results) {
            if (item.id == 0) continue
            val key = "${SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE}:${item.id}"
            if (into.containsKey(key)) continue
            into[key] = item.toCandidate(subject, year)
        }
    }

    private fun TmdbTvSummaryDto.toCandidate(subject: SubjectEntity, year: Int?): RatingCandidate {
        val score = TmdbMatchScorer.score(
            bangumiTitles = listOfNotNull(subject.titleCN, subject.title),
            candidateTitles = listOfNotNull(name, originalName),
            bangumiYear = year,
            candidateYear = firstAirDate?.take(4)?.toIntOrNull(),
            bangumiEpisodes = subject.totalEpisodes,
            candidateEpisodes = null,
        )
        return RatingCandidate(
            provider = SubjectExternalIdEntity.PROVIDER_TMDB_TV,
            externalId = id.toString(),
            title = name ?: originalName ?: "#$id",
            subtitle = listOfNotNull(
                firstAirDate?.take(4),
                voteAverage?.let { "\u2605 %.1f".format(it) },
            ).joinToString(" · ").takeIf { it.isNotBlank() },
            imageUrl = TmdbImageUrl.url(posterPath, TmdbImageUrl.POSTER_SMALL),
            confidence = score,
        )
    }

    private fun TmdbMovieSummaryDto.toCandidate(subject: SubjectEntity, year: Int?): RatingCandidate {
        val score = TmdbMatchScorer.score(
            bangumiTitles = listOfNotNull(subject.titleCN, subject.title),
            candidateTitles = listOfNotNull(title, originalTitle),
            bangumiYear = year,
            candidateYear = releaseDate?.take(4)?.toIntOrNull(),
        )
        return RatingCandidate(
            provider = SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE,
            externalId = id.toString(),
            title = title ?: originalTitle ?: "#$id",
            subtitle = listOfNotNull(
                releaseDate?.take(4),
                voteAverage?.let { "\u2605 %.1f".format(it) },
            ).joinToString(" · ").takeIf { it.isNotBlank() },
            imageUrl = TmdbImageUrl.url(posterPath, TmdbImageUrl.POSTER_SMALL),
            confidence = score,
        )
    }

    companion object {
        /** 走 movie 端点的作品类型（用户开关打开时生效）。 */
        val NON_TV_TYPES: Set<SubjectType> = setOf(
            SubjectType.GAME, SubjectType.BOOK, SubjectType.MUSIC,
        )

        /** 该作品类型应优先查 movie 还是 tv。 */
        fun preferMovie(type: SubjectType): Boolean = type in NON_TV_TYPES

        /**
         * 从 TMDb 链接或裸 ID 解析出 (provider, id)。
         *
         * 接受：
         * - `https://www.themoviedb.org/tv/42509-steinsgate` → (tmdb_tv, 42509)
         * - `https://www.themoviedb.org/movie/129` → (tmdb_movie, 129)
         * - `42509` / `tv/42509` / `movie/129` → 按前缀推断，无前缀默认 tv
         *
         * 解析不出来返回 null（UI 用这个判定「输入非法」而不是静默失败）。
         */
        fun parseIdOrUrl(raw: String): Pair<String, Int>? {
            val text = raw.trim()
            if (text.isEmpty()) return null

            val moviePrefix = Regex("""movie[/=](\d+)""", RegexOption.IGNORE_CASE)
            val tvPrefix = Regex("""tv[/=](\d+)""", RegexOption.IGNORE_CASE)
            moviePrefix.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
                return SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE to it
            }
            tvPrefix.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
                return SubjectExternalIdEntity.PROVIDER_TMDB_TV to it
            }
            Regex("""(\d{1,9})""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it <= 0) return null
                return SubjectExternalIdEntity.PROVIDER_TMDB_TV to it
            }
            return null
        }
    }
}
