package com.otakup.niriko.data.repository

import com.otakup.niriko.data.calculator.EpisodeAlignment
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.remote.rating.RatingCandidate
import com.otakup.niriko.data.remote.tmdb.TmdbClient
import com.otakup.niriko.data.remote.tmdb.TmdbImageUrl
import com.otakup.niriko.data.remote.tmdb.TmdbLanguage
import com.otakup.niriko.data.remote.tmdb.dto.TmdbEpisodeDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbImageDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbSeasonDetailDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbTvDetailDto
import com.otakup.niriko.data.settings.AppSettings

/**
 * TMDb 数据仓储：绑定、季选择、每集数据、海报/背景候选。
 *
 * 所有方法都**不抛异常**（返回 null / 空列表）。TMDb 在本项目里是"锦上添花"的补充源，
 * 任何失败都不能影响详情页主数据。
 */
class TmdbRepository(
    private val externalIdRepository: ExternalIdRepository,
    private val settingsProvider: suspend () -> AppSettings,
) {

    suspend fun isConfigured(): Boolean {
        val settings = runCatching { settingsProvider() }.getOrNull() ?: return false
        return settings.externalRatingsEnabled && settings.tmdbApiKey.isNotBlank()
    }

    /** 应用当前设置里的端点（官方 / 自定义镜像）。每次取数前调用，保证切换立即生效。 */
    suspend fun applyEndpoint() {
        val settings = runCatching { settingsProvider() }.getOrNull() ?: return
        TmdbClient.setApiKey(settings.tmdbApiKey)
        TmdbClient.setEndpoint(settings.tmdbApiUrl, settings.tmdbImageUrl)
    }

    /** 已绑定的 TMDb tv id（未绑定返回 null）。 */
    suspend fun boundTvId(subjectId: Long): Int? =
        externalIdRepository.get(subjectId, PROVIDER_TMDB_TV)?.externalId?.toIntOrNull()

    suspend fun tvDetail(tvId: Int): TmdbTvDetailDto? {
        applyEndpoint()
        return runCatching {
            TmdbClient.apiService.tvDetail(tvId, TmdbLanguage.DEFAULT.code, "external_ids")
        }.getOrNull()
    }

    suspend fun seasonDetail(tvId: Int, seasonNumber: Int): TmdbSeasonDetailDto? {
        applyEndpoint()
        return runCatching {
            TmdbClient.apiService.seasonDetail(tvId, seasonNumber, TmdbLanguage.DEFAULT.code)
        }.getOrNull()
    }

    /**
     * 选取与该 Bangumi 词条对应的 TMDb 季。
     * 优先使用绑定记录里的 subKey（用户手动指定过季号时），否则按年份 + 集数自动挑。
     */
    suspend fun resolveSeasonNumber(subject: SubjectEntity, detail: TmdbTvDetailDto): Int? {
        val explicit = externalIdRepository.get(subject.subjectId, PROVIDER_TMDB_TV)
            ?.subKey?.toIntOrNull()
        if (explicit != null) return explicit

        val refs = detail.seasons.map { season ->
            EpisodeAlignment.SeasonRef(
                seasonNumber = season.seasonNumber,
                year = season.airDate?.take(4)?.toIntOrNull(),
                episodeCount = season.episodeCount,
                name = season.name,
            )
        }
        val subjectYear = subject.airDate?.take(4)?.toIntOrNull()
        return EpisodeAlignment.pickSeason(refs, subjectYear, subject.totalEpisodes)
    }

    /**
     * 剧集候选海报（多语言优先级排序见 [TmdbImageSelection.sortPosters]）。
     *
     * 注：曾计划提供「本季 / 整剧」切换（AniShelf 的 PosterBrowserView），
     * 但本项目 Bangumi 词条本身已按季拆分、且未保存「整剧 TMDb id」，
     * 因此移除了当时留下的死参数 `useSeriesPosters`（无调用方、实现也未使用）。
     */
    suspend fun posterCandidates(subject: SubjectEntity): List<TmdbImageDto> {
        val tvId = boundTvId(subject.subjectId) ?: return emptyList()
        applyEndpoint()
        val images = runCatching { TmdbClient.apiService.tvImages(tvId) }.getOrNull() ?: return emptyList()
        val posters = images.posters
        return TmdbImageSelection.sortPosters(posters)
    }

    /** 背景图（剧照区用）。 */
    suspend fun backdropCandidates(subject: SubjectEntity): List<TmdbImageDto> {
        val tvId = boundTvId(subject.subjectId) ?: return emptyList()
        applyEndpoint()
        val images = runCatching { TmdbClient.apiService.tvImages(tvId) }.getOrNull() ?: return emptyList()
        return images.backdrops
            .filter { it.filePath != null }
            .sortedByDescending { it.width }
    }

    /** 每集剧照 URL（来自季详情，无需额外请求）。 */
    fun episodeStillUrl(episode: TmdbEpisodeDto): String? =
        TmdbImageUrl.url(episode.stillPath, TmdbImageUrl.STILL_MEDIUM)

    /**
     * 按标题自动搜候选（含用户「覆盖游戏/书籍/音乐」开关的判断）。
     *
     * 第 4 轮 C：详情页此前自己 new 一个 TmdbRatingSource 并传空的 RatingSourceKeys，
     * 于是 GAME/BOOK/MUSIC 永远搜不到候选。现在统一走这里，由仓储负责取设置。
     */
    suspend fun searchCandidates(subject: SubjectEntity): List<RatingCandidate> {
        val settings = runCatching { settingsProvider() }.getOrNull() ?: return emptyList()
        if (!settings.externalRatingsEnabled || settings.tmdbApiKey.isBlank()) return emptyList()
        applyEndpoint()
        val source = com.otakup.niriko.data.remote.rating.sources.TmdbRatingSource()
        val keys = com.otakup.niriko.data.remote.rating.RatingSourceKeys(
            tmdbApiKey = settings.tmdbApiKey,
            tmdbIncludeNonTvTypes = settings.tmdbIncludeNonTvTypes,
        )
        if (!source.isAvailableFor(subject.type, keys)) return emptyList()
        return runCatching { source.searchCandidates(subject, keys) }.getOrDefault(emptyList())
    }

    companion object {
        const val PROVIDER_TMDB_TV = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.PROVIDER_TMDB_TV
        const val PROVIDER_TMDB_MOVIE = com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE
    }
}

/**
 * TMDb 多语言图片优先级（对齐 AniShelf 的 TMDbImageSelection，纯函数）。
 *
 * 规则：原语言 → 元数据语言 → 无语言，同级按宽度降序。
 * 「无语言」判定集合：空串 / null / xx / und / zxx。
 */
object TmdbImageSelection {

    private val NO_LANGUAGE = setOf("", "null", "xx", "und", "zxx")

    fun isNoLanguage(code: String?): Boolean =
        code == null || NO_LANGUAGE.contains(code.trim().lowercase())

    fun sortPosters(
        posters: List<TmdbImageDto>,
        originalLanguageCode: String? = null,
        metadataLanguageCode: String? = null,
    ): List<TmdbImageDto> {
        val valid = posters.filter { it.filePath != null }
        if (valid.isEmpty()) return emptyList()

        val explicitRules = listOfNotNull(
            originalLanguageCode?.trim()?.takeIf { it.isNotEmpty() && !isNoLanguage(it) },
            metadataLanguageCode?.trim()?.takeIf { it.isNotEmpty() && !isNoLanguage(it) },
        ).distinct()

        // 修复（BUG-1）：此前 rules 为空时 indexOfFirst 返回 -1，会把**所有带语言的海报全部丢弃**，
        // 只剩「无语言」那 1-3 张。现在无显式规则时回退到图片语言白名单（zh → ja → en），
        // 保证中文/日文海报一定是候选。
        val rules = explicitRules.ifEmpty { TmdbLanguage.IMAGE_LANGUAGE_CODES }

        val ranked = valid.map { image ->
            val code = image.languageCode
            val priority = when {
                isNoLanguage(code) -> rules.size
                else -> rules.indexOfFirst { it.equals(code, ignoreCase = true) }
                    // 命中白名单外的语言（如 ko/fr）排在「无语言」之后，但**保留**而不是丢弃
                    .let { if (it < 0) rules.size + 1 else it }
            }
            Triple(image, priority, image.width)
        }
        return ranked
            .sortedWith(compareBy({ it.second }, { -it.third }))
            .map { it.first }
    }
}
