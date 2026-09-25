package com.otakup.niriko.data.remote.rating

/**
 * 统一的外部权威评分。
 *
 * 不同来源标尺各异（10 分制 / 100 分制 / 5 分制 / 百分制），因此**同时保留**
 * [nativeScore] + [scoreMax]（按原标尺展示）与 [score]（换算到 10 分制，用于同屏排序/对比）。
 * UI 必须显示来源名与标尺，避免「IMDb 8.6 与 Metacritic 87 谁高」这类误读。
 */
data class ExternalRating(
    /** tmdb / imdb / metacritic / steam_review / igdb / opencritic / rawg / musicbrainz / discogs / ... */
    val sourceId: String,
    /** 展示名。 */
    val label: String,
    /** 换算到 10 分制的分数；null 表示该源只有人数或只有名次。 */
    val score: Float? = null,
    /** 来源原生分。 */
    val nativeScore: Float? = null,
    /** 原生标尺上限（10 / 100 / 5 / 100）。 */
    val scoreMax: Float = 10f,
    val voteCount: Int? = null,
    val sourceUrl: String? = null,
    /** 附加说明（如 Steam 的「好评如潮」）。 */
    val note: String? = null,
    /** 数据获取时间。 */
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    /** 形如 "8.6 / 10"、"87 / 100"、"96%" 的展示串。 */
    val displayScore: String?
        get() = nativeScore?.let { s ->
            val rounded = if (scoreMax <= 10f) "%.1f".format(s) else s.toInt().toString()
            if (scoreMax == 100f && sourceId == SOURCE_STEAM_REVIEW) "$rounded%" else "$rounded / ${scoreMax.toInt()}"
        }

    companion object {
        const val SOURCE_TMDB = "tmdb"
        const val SOURCE_IMDB = "imdb"
        const val SOURCE_METACRITIC = "metacritic"
        const val SOURCE_STEAM_REVIEW = "steam_review"
        const val SOURCE_IGDB = "igdb"
        const val SOURCE_OPENCRITIC = "opencritic"
        const val SOURCE_RAWG = "rawg"
        const val SOURCE_MUSICBRAINZ = "musicbrainz"
        const val SOURCE_DISCOGS = "discogs"
        const val SOURCE_ITUNES = "itunes"
        const val SOURCE_LASTFM = "lastfm"
        const val SOURCE_GOOGLE_BOOKS = "googlebooks"
        const val SOURCE_OPEN_LIBRARY = "openlibrary"
        const val SOURCE_VNDB = "vndb"
        const val SOURCE_BANGUMI = "bangumi"
        const val SOURCE_BILIBILI = "bilibili"
        /** AniList 的 averageScore（0-100）。第 4 轮起作为正常评分源自动接入（无需手动绑定）。 */
        const val SOURCE_ANILIST = "anilist"
        /** Jikan（MyAnimeList 的非官方只读镜像；灰色通道，默认关）。 */
        const val SOURCE_JIKAN = "jikan"

        /**
         * 把任意标尺的分数换算到 10 分制。
         *
         * 实现委托给 calculator 层的 [com.otakup.niriko.data.calculator.RatingInsights.toTenPoint]，
         * 避免同一公式在两处各写一遍（此前就是重复实现）。
         */
        fun toTenPoint(nativeScore: Float?, scoreMax: Float): Float? =
            com.otakup.niriko.data.calculator.RatingInsights.toTenPoint(nativeScore, scoreMax)
    }
}

/** 各源所需的用户密钥（从设置一次性取出，避免每个源各自读 DataStore）。 */
data class RatingSourceKeys(
    val tmdbApiKey: String? = null,
    val omdbApiKey: String? = null,
    val igdbClientId: String? = null,
    val igdbClientSecret: String? = null,
    val rawgApiKey: String? = null,
    val discogsToken: String? = null,
    val openCriticApiKey: String? = null,
    /**
     * 是否允许 TMDb 覆盖 GAME / BOOK / MUSIC（走 TMDb movie）。
     *
     * 这是**用户偏好**而非密钥，但各源都只能拿到本对象，因此挂在这里：
     * 否则 RatingSourceRegistry 无法按用户设置决定 TMDb 是否参与这三类作品。
     */
    val tmdbIncludeNonTvTypes: Boolean = false,
) {
    fun usable(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
