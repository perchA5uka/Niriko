package com.otakup.niriko.data.remote.rating.sources

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.remote.rating.RatingHttp
import com.otakup.niriko.data.remote.rating.RatingSource
import com.otakup.niriko.data.remote.rating.RatingSourceKeys
import com.otakup.niriko.data.remote.rating.obj
import com.otakup.niriko.data.remote.rating.str

/**
 * IMDb 评分（经 OMDb 代理）。
 *
 * ## 为什么是 OMDb
 *
 * seriesgraph 的逐集曲线用的正是 IMDb 数字，而 IMDb 没有免费官方 API：
 * - 官方 bulk TSV（title.ratings / title.episode）解压后约 5.5GB、需每日全量重下 + 自建后端 ETL → 移动端不可行；
 * - 爬 IMDb 网页被明确禁止。
 * 因此走 OMDb：免费 key 1,000 次/天，支持 `type=episode` + `i=tt...` 取单集评分。
 *
 * ⚠️ OMDb 内容为 CC BY-NC 4.0、非 IMDb 官方，**不可商用**；因此本源默认关闭，由用户显式开启。
 */
class OmdbRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_IMDB
    override val label: String = "IMDb"

    override val subjectTypes: Set<SubjectType> = setOf(
        SubjectType.ANIME, SubjectType.REAL, SubjectType.OTHER,
    )

    override val requiresKey: Boolean = true
    override val requiresExternalId: Set<String> = setOf(SubjectExternalIdEntity.PROVIDER_IMDB)

    override fun isAvailable(keys: RatingSourceKeys): Boolean = !keys.usable(keys.omdbApiKey).isNullOrEmpty()

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val imdbId = externalIds[SubjectExternalIdEntity.PROVIDER_IMDB] ?: return null
        val apiKey = keys.usable(keys.omdbApiKey) ?: return null
        return fetchById(imdbId, apiKey)
    }

    companion object {
        private const val BASE = "https://www.omdbapi.com/"

        /**
         * 按 IMDb id 取评分。整剧与单集共用此方法（单集用 episode 的 tt id）。
         * 供每集评分链路复用（ImdbEpisodeRatingRepository）。
         */
        suspend fun fetchById(imdbId: String, apiKey: String): ExternalRating? {
            val url = BASE + "?apikey=" + apiKey + "&i=" + imdbId + "&plot=short"
            val json = RatingHttp.getJson(url) ?: return null
            if (json.str("Response") == "False") return null
            val rating = json.str("imdbRating")?.toFloatOrNull() ?: return null
            if (rating <= 0f) return null
            val votes = json.str("imdbVotes")?.replace(",", "")?.toIntOrNull()
            val imdbUrl = json.str("imdbID")?.let { "https://www.imdb.com/title/$it/" }
                ?: "https://www.imdb.com/title/$imdbId/"
            return ExternalRating(
                sourceId = "imdb",
                label = "IMDb",
                score = ExternalRating.toTenPoint(rating, 10f),
                nativeScore = rating,
                scoreMax = 10f,
                voteCount = votes,
                sourceUrl = imdbUrl,
                note = json.str("Type")?.let { type -> if (type == "episode") "单集" else null },
            )
        }

        /**
         * 用**整剧 IMDb id + Season/Episode 参数**直接取单集评分（第 4 轮 H 的兜底）。
         *
         * 为什么需要它：常规链路每集要先打一次 TMDb `episode/external_ids` 拿单集的 tt id，
         * 再打 OMDb——某集在 TMDb 上没有 external_ids 时就整条链路断掉（表现为「某些集没分」）。
         * OMDb 官方支持 `?i=<seriesId>&Season=1&Episode=3` 这种查询，一次请求就能拿到，
         * 因此用它作为**不依赖 TMDb 单集 external_ids** 的替代路径。
         *
         * 注意：OMDb 对季/集参数的 episode 查询要求 `i` 是**剧集（series）**的 id，
         * 传单集 id 会忽略参数。返回 null 表示该集在 OMDb 无数据。
         */
        suspend fun fetchEpisodeBySeries(
            seriesImdbId: String,
            season: Int,
            episode: Int,
            apiKey: String,
        ): ExternalRating? {
            if (season <= 0 || episode <= 0) return null
            val url = BASE + "?apikey=" + apiKey +
                "&i=" + seriesImdbId +
                "&Season=" + season +
                "&Episode=" + episode +
                "&plot=short"
            val json = RatingHttp.getJson(url) ?: return null
            if (json.str("Response") == "False") return null
            val rating = json.str("imdbRating")?.toFloatOrNull() ?: return null
            if (rating <= 0f) return null
            val votes = json.str("imdbVotes")?.replace(",", "")?.toIntOrNull()
            val episodeId = json.str("imdbID")
            return ExternalRating(
                sourceId = "imdb",
                label = "IMDb",
                score = ExternalRating.toTenPoint(rating, 10f),
                nativeScore = rating,
                scoreMax = 10f,
                voteCount = votes,
                sourceUrl = episodeId?.let { "https://www.imdb.com/title/$it/" },
                note = "单集 S${season}E${episode}",
            )
        }

        /**
         * 轻量 key 校验（`i=tt0111161`）。设置页用；返回 null 表示 key 无效或网络失败。
         *
         * 与 TMDb 用 `/3/configuration` 校验对齐：保存前先验一次，避免「填了但其实是错的」。
         */
        suspend fun verifyKey(apiKey: String): String? {
            val url = BASE + "?apikey=" + apiKey + "&i=tt0111161&plot=short"
            val json = RatingHttp.getJson(url) ?: return null
            if (json.str("Response") == "False") {
                return json.str("Error")
            }
            return json.str("Title")
        }
    }
}
