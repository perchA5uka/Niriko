package com.otakup.niriko.data.remote.rating

import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.sources.AniListRatingSource
import com.otakup.niriko.data.remote.rating.sources.DiscogsRatingSource
import com.otakup.niriko.data.remote.rating.sources.GoogleBooksRatingSource
import com.otakup.niriko.data.remote.rating.sources.IgdbRatingSource
import com.otakup.niriko.data.remote.rating.sources.MusicBrainzRatingSource
import com.otakup.niriko.data.remote.rating.sources.OmdbRatingSource
import com.otakup.niriko.data.remote.rating.sources.OpenCriticRatingSource
import com.otakup.niriko.data.remote.rating.sources.OpenLibraryRatingSource
import com.otakup.niriko.data.remote.rating.sources.RawgRatingSource
import com.otakup.niriko.data.remote.rating.sources.SteamReviewSource
import com.otakup.niriko.data.remote.rating.sources.TmdbRatingSource
import com.otakup.niriko.data.remote.rating.sources.VndbRatingSource

/**
 * 权威评分源注册表。
 *
 * 设计对齐 [com.otakup.niriko.data.remote.game.GameDataSourceRegistry]：
 * **注册序即展示优先级**，按作品类型派发。
 *
 * 顺序原则：
 * 1. 影视：TMDb（主，免费、一次调用拿全季每集）→ IMDb（可选层）；
 * 2. 游戏：Steam 好评率（无需 key，玩家侧最权威）→ Metacritic（本地字段，见仓库）→ IGDB（媒体均分）→ VNDB（VN）→ RAWG / OpenCritic（可选）；
 * 3. 音乐：MusicBrainz（无 key）→ Discogs（token）；
 * 4. 书籍：Google Books → Open Library；
 *
 * Fami通 / Billboard / Oricon / 非 Steam 的 Metacritic 等**无可用 API 的机构**不在注册表内，
 * 走 ManualAwardEntity 的手动录入通道。
 */
object RatingSourceRegistry {

    val all: List<RatingSource> = listOf(
        TmdbRatingSource(),
        OmdbRatingSource(),
        // AniList 无需 key、无需预绑定（能匹配就直接展示分数），因此排在影视源之后
        AniListRatingSource(),
        SteamReviewSource(),
        IgdbRatingSource(),
        VndbRatingSource(),
        RawgRatingSource(),
        OpenCriticRatingSource(),
        MusicBrainzRatingSource(),
        DiscogsRatingSource(),
        GoogleBooksRatingSource(),
        OpenLibraryRatingSource(),
    )

    /** 某作品类型可用的源（按注册序）。 */
    fun forType(type: SubjectType): List<RatingSource> = all.filter { type in it.subjectTypes }

    /**
     * 某作品类型可用的源，**考虑用户偏好开关**。
     *
     * 与 [forType] 的区别：TMDb 的覆盖范围可由设置扩展（GAME/BOOK/MUSIC 走 movie），
     * 因此不能只看静态的 subjectTypes。
     */
    fun forType(type: SubjectType, keys: RatingSourceKeys): List<RatingSource> =
        all.filter { it.isAvailableFor(type, keys) }

    /** 可用的源（已配置 key / 无需 key）。 */
    fun available(type: SubjectType, keys: RatingSourceKeys): List<RatingSource> =
        forType(type, keys).filter { it.isAvailable(keys) }

    /** 全部需要密钥的源（设置页展示「已配置 / 未配置」用）。 */
    val keyedSources: List<RatingSource> = all.filter { it.requiresKey }

    fun byId(id: String): RatingSource? = all.firstOrNull { it.id == id }
}
