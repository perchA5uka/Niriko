package com.otakup.niriko.data.local.entity

import androidx.room.Entity

/**
 * 作品的外部身份绑定（表 subject_external_ids）。
 *
 * 与 steam_bindings / vndb_bindings / anilist_bindings 同构，但**通用化**：
 * 一个 provider 一条记录，主键 (subjectId, provider)，因此同一作品可以同时绑定
 * TMDb 剧集、IMDb、IGDB、MusicBrainz、Discogs 等多个外部身份。
 *
 * 覆盖 plan 的「保守匹配」约定：provider 侧只产出候选，**用户确认后才写库**
 * （bindMethod = MANUAL），只有从 infobox 解析出的明确 ID 才允许直接绑定
 * （bindMethod = INFOBOX，confidence = 1）。
 *
 * 无二级索引：所有查询都以 subjectId 为前缀（主键左侧），无需反向查找。
 */
@Entity(
    tableName = "subject_external_ids",
    primaryKeys = ["subjectId", "provider"],
)
data class SubjectExternalIdEntity(
    val subjectId: Long,
    /**
     * 外部身份提供方：
     * tmdb_tv / tmdb_movie / imdb / igdb / opencritic / rawg /
     * musicbrainz_release_group / discogs_release / googlebooks / openlibrary /
     * itunes_album / bangumi_ep 等。
     */
    val provider: String,
    /** provider 侧的 id（TMDb 为数字字符串，IMDb 为 ttXXXXXXX，Discogs 为 release id）。 */
    val externalId: String,
    /** 绑定时对方的标题快照（便于诊断错绑，不参与逻辑）。 */
    val titleSnapshot: String? = null,
    /** 匹配置信度（0-1）。来自 infobox 的明确 ID 为 1。 */
    val confidence: Float = 0f,
    /** INFOBOX（词条内明确给出）/ TOKEN（手动搜索选中）/ MANUAL（候选列表确认）。 */
    val bindMethod: String = "MANUAL",
    /** provider 侧子键。例：TMDb 剧集的 season number（多季条目按季绑定）。 */
    val subKey: String? = null,
    val boundAt: Long = 0L,
) {
    companion object {
        const val PROVIDER_TMDB_TV = "tmdb_tv"
        const val PROVIDER_TMDB_MOVIE = "tmdb_movie"
        const val PROVIDER_IMDB = "imdb"
        const val PROVIDER_IGDB = "igdb"
        const val PROVIDER_OPENCRITIC = "opencritic"
        const val PROVIDER_RAWG = "rawg"
        const val PROVIDER_MUSICBRAINZ = "musicbrainz_release_group"
        const val PROVIDER_DISCOGS = "discogs_release"
        const val PROVIDER_GOOGLE_BOOKS = "googlebooks"
        const val PROVIDER_OPEN_LIBRARY = "openlibrary"
        const val PROVIDER_ITUNES_ALBUM = "itunes_album"
        const val PROVIDER_BANGUMI_EP = "bangumi_ep"
        /** 豆瓣条目（剧照来源；灰色通道，默认关）。 */
        const val PROVIDER_DOUBAN = "douban"

        const val METHOD_INFOBOX = "INFOBOX"
        const val METHOD_TOKEN = "TOKEN"
        const val METHOD_MANUAL = "MANUAL"
    }
}
