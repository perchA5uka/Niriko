package com.otakup.niriko.data.model

/** AniList 独有信息（热度/趋势/排名/下一集/来源等），与 Bangumi 主条目差异化展示。 */
data class AniListRichDetail(
    val id: Long,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val format: String? = null,
    val status: String? = null,
    val source: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val episodes: Int? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val duration: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val trending: Int? = null,
    val rankings: List<AniListRanking> = emptyList(),
    val nextAiringAt: Long? = null,
    val nextAiringEpisode: Int? = null,
    val siteUrl: String? = null,
)

data class AniListRanking(
    val rank: Int,
    val type: String? = null,
    val year: Int? = null,
    val season: String? = null,
)
