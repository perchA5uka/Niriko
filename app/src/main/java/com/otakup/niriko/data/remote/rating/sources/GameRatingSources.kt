package com.otakup.niriko.data.remote.rating.sources

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.remote.rating.RatingCandidate
import com.otakup.niriko.data.remote.rating.RatingHttp
import com.otakup.niriko.data.remote.rating.RatingSource
import com.otakup.niriko.data.remote.rating.RatingSourceKeys
import com.otakup.niriko.data.remote.rating.arr
import com.otakup.niriko.data.remote.rating.float
import com.otakup.niriko.data.remote.rating.int
import com.otakup.niriko.data.remote.rating.obj
import com.otakup.niriko.data.remote.rating.objects
import com.otakup.niriko.data.remote.rating.ratingUrlEncode
import com.otakup.niriko.data.remote.rating.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 游戏媒体均分（IGDB / RAWG / OpenCritic）。
 *
 * 关于「权威机构」的现实：
 * - **Fami通**（日本 40 分制）**没有任何 API 或开放数据集**，只能外链 + 手动录入，见 ManualAwardEntity；
 * - **Metacritic** 无免费公开 API，项目已通过 Steam appdetails 的 metacritic 字段拿到一部分；
 * - **IGDB** 自带 critic 聚合（aggregated_rating），是目前最规范、免费可用的游戏媒体均分来源；
 * - **RAWG** 只有单个 metacritic 数字，无聚合；
 * - **OpenCritic** 官方渠道需申请 key，日系小众覆盖弱。
 *
 * ⚠️ 不要把 IGDB 的 aggregated_rating 标成 Metacritic —— 两者不是同一个数。
 */
class IgdbRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_IGDB
    override val label: String = "IGDB 媒体均分"
    override val subjectTypes: Set<SubjectType> = setOf(SubjectType.GAME)
    override val requiresKey: Boolean = true
    override val requiresExternalId: Set<String> = setOf(SubjectExternalIdEntity.PROVIDER_IGDB)

    override fun isAvailable(keys: RatingSourceKeys): Boolean =
        !keys.usable(keys.igdbClientId).isNullOrEmpty() && !keys.usable(keys.igdbClientSecret).isNullOrEmpty()

    override suspend fun searchCandidates(subject: SubjectEntity, keys: RatingSourceKeys): List<RatingCandidate> {
        val token = IgdbAuth.token(keys) ?: return emptyList()
        val query = listOfNotNull(subject.titleCN, subject.title).firstOrNull()?.trim().orEmpty()
        if (query.isEmpty()) return emptyList()
        val body = "search \"${query.replace("\"", "")}\" ; " +
            "fields name,first_release_date,aggregated_rating,total_rating_rank,url; limit 8;"
        val array = RatingHttp.postArray(
            url = "https://api.igdb.com/v4/games",
            body = body,
            contentType = "text/plain",
            extraHeaders = mapOf(
                "Client-ID" to keys.usable(keys.igdbClientId).orEmpty(),
                "Authorization" to "Bearer $token",
            ),
        ) ?: return emptyList()

        return array.objects().mapNotNull { item ->
            val id = item.int("id") ?: return@mapNotNull null
            RatingCandidate(
                provider = SubjectExternalIdEntity.PROVIDER_IGDB,
                externalId = id.toString(),
                title = item.str("name") ?: "#$id",
                subtitle = item.float("aggregated_rating")?.let { "媒体均分 %.0f".format(it) },
                confidence = 0f,
            )
        }
    }

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val igdbId = externalIds[SubjectExternalIdEntity.PROVIDER_IGDB]?.toIntOrNull() ?: return null
        val token = IgdbAuth.token(keys) ?: return null
        val array = RatingHttp.postArray(
            url = "https://api.igdb.com/v4/games",
            body = "fields name,aggregated_rating,aggregated_rating_count,rating,rating_count,url; where id = $igdbId;",
            contentType = "text/plain",
            extraHeaders = mapOf(
                "Client-ID" to keys.usable(keys.igdbClientId).orEmpty(),
                "Authorization" to "Bearer $token",
            ),
        ) ?: return null
        val item = array.objects().firstOrNull() ?: return null
        // 优先媒体均分（critic），回退用户均分
        val critic = item.float("aggregated_rating")
        val user = item.float("rating")
        val value = critic ?: user ?: return null
        val count = if (critic != null) item.int("aggregated_rating_count") else item.int("rating_count")
        return ExternalRating(
            sourceId = id,
            label = if (critic != null) label else "IGDB 用户均分",
            score = ExternalRating.toTenPoint(value, 100f),
            nativeScore = value,
            scoreMax = 100f,
            voteCount = count,
            sourceUrl = item.str("url")?.let { if (it.startsWith("http")) it else "https:$it" },
            note = if (critic != null) "媒体均分（非 Metacritic）" else "用户均分",
        )
    }
}

/** Twitch client-credentials 换 IGDB access token（缓存到过期前）。 */
private object IgdbAuth {
    @Volatile private var token: String? = null
    @Volatile private var expireAt: Long = 0L

    suspend fun token(keys: RatingSourceKeys): String? {
        val now = System.currentTimeMillis()
        token?.let { if (now < expireAt - 60_000L) return it }
        val clientId = keys.usable(keys.igdbClientId) ?: return null
        val secret = keys.usable(keys.igdbClientSecret) ?: return null
        val json = withContext(Dispatchers.IO) {
            RatingHttp.postJson(
                url = "https://id.twitch.tv/oauth2/token" +
                    "?client_id=${ratingUrlEncode(clientId)}" +
                    "&client_secret=${ratingUrlEncode(secret)}&grant_type=client_credentials",
                body = "",
            )
        } ?: return null
        val fresh = json.str("access_token") ?: return null
        token = fresh
        expireAt = now + ((json.int("expires_in") ?: 3600) * 1000L)
        return fresh
    }
}

/** RAWG：只有 metacritic 单值 + 用户分，无 critic 聚合。 */
class RawgRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_RAWG
    override val label: String = "RAWG"
    override val subjectTypes: Set<SubjectType> = setOf(SubjectType.GAME)
    override val requiresKey: Boolean = true
    override val requiresExternalId: Set<String> = setOf(SubjectExternalIdEntity.PROVIDER_RAWG)

    override fun isAvailable(keys: RatingSourceKeys): Boolean = !keys.usable(keys.rawgApiKey).isNullOrEmpty()

    override suspend fun searchCandidates(subject: SubjectEntity, keys: RatingSourceKeys): List<RatingCandidate> {
        val apiKey = keys.usable(keys.rawgApiKey) ?: return emptyList()
        val query = listOfNotNull(subject.titleCN, subject.title).firstOrNull()?.trim().orEmpty()
        if (query.isEmpty()) return emptyList()
        val json = RatingHttp.getJson(
            "https://api.rawg.io/api/games?key=${ratingUrlEncode(apiKey)}" +
                "&search=${ratingUrlEncode(query)}&page_size=8"
        ) ?: return emptyList()
        return json.arr("results").objects().mapNotNull { item ->
            val id = item.int("id") ?: return@mapNotNull null
            RatingCandidate(
                provider = SubjectExternalIdEntity.PROVIDER_RAWG,
                externalId = id.toString(),
                title = item.str("name") ?: "#$id",
                subtitle = listOfNotNull(
                    item.str("released")?.take(4),
                    item.int("metacritic")?.let { "Metacritic $it" },
                ).joinToString(" · ").takeIf { it.isNotBlank() },
                imageUrl = item.str("background_image"),
                confidence = 0f,
            )
        }
    }

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val rawgId = externalIds[SubjectExternalIdEntity.PROVIDER_RAWG] ?: return null
        val apiKey = keys.usable(keys.rawgApiKey) ?: return null
        val json = RatingHttp.getJson("https://api.rawg.io/api/games/$rawgId?key=${ratingUrlEncode(apiKey)}")
            ?: return null
        val metacritic = json.int("metacritic")
        val userRating = json.float("rating")
        return when {
            metacritic != null && metacritic > 0 -> ExternalRating(
                sourceId = id,
                label = "Metacritic（RAWG）",
                score = ExternalRating.toTenPoint(metacritic.toFloat(), 100f),
                nativeScore = metacritic.toFloat(),
                scoreMax = 100f,
                voteCount = json.int("ratings_count"),
                sourceUrl = "https://rawg.io/games/$rawgId",
            )
            userRating != null && userRating > 0 -> ExternalRating(
                sourceId = id,
                label = "RAWG 用户分",
                score = ExternalRating.toTenPoint(userRating, 5f),
                nativeScore = userRating,
                scoreMax = 5f,
                voteCount = json.int("ratings_count"),
                sourceUrl = "https://rawg.io/games/$rawgId",
            )
            else -> null
        }
    }
}

/**
 * OpenCritic：官方 API 需在 portal.opencritic.com 申请 key（RapidAPI 是官方渠道）。
 * 未配 key 时整源隐藏——**不做未文档化端点的抓取**。
 */
class OpenCriticRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_OPENCRITIC
    override val label: String = "OpenCritic"
    override val subjectTypes: Set<SubjectType> = setOf(SubjectType.GAME)
    override val requiresKey: Boolean = true

    override fun isAvailable(keys: RatingSourceKeys): Boolean = !keys.usable(keys.openCriticApiKey).isNullOrEmpty()

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val apiKey = keys.usable(keys.openCriticApiKey) ?: return null
        val query = listOfNotNull(subject.titleCN, subject.title).firstOrNull()?.trim().orEmpty()
        if (query.isEmpty()) return null
        val headers = mapOf(
            "X-RapidAPI-Key" to apiKey,
            "X-RapidAPI-Host" to "opencritic-api.p.rapidapi.com",
        )
        val array = RatingHttp.getArray(
            "https://opencritic-api.p.rapidapi.com/game/search?criteria=${ratingUrlEncode(query)}",
            headers,
        ) ?: return null
        val best = array.objects().firstOrNull() ?: return null
        val topScore = best.float("topCriticScore") ?: best.float("averageScore") ?: return null
        val gameId = best.int("id")
        return ExternalRating(
            sourceId = id,
            label = label,
            score = ExternalRating.toTenPoint(topScore, 100f),
            nativeScore = topScore,
            scoreMax = 100f,
            voteCount = best.int("numReviews"),
            sourceUrl = gameId?.let { "https://opencritic.com/game/$it" },
        )
    }
}
