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
import com.otakup.niriko.data.remote.rating.int
import com.otakup.niriko.data.remote.rating.obj
import com.otakup.niriko.data.remote.rating.objects
import com.otakup.niriko.data.remote.rating.ratingUrlEncode
import com.otakup.niriko.data.remote.rating.str

/**
 * 音乐权威评分。
 *
 * ## 为什么是这两个源
 *
 * 调研结论：**Billboard 与 Oricon 都没有免费的官方 API**（Billboard 官方 API 2013 年终止；
 * Oricon 只有榜单网页），RYM / AOTY / Pitchfork / AllMusic 同样无 API 或仅 RSS。
 * 因此榜单成绩走「外链 + 手动录入」（ManualAwardEntity），**不做抓取**。
 *
 * 真正有可用评分 API 的是：
 * - **MusicBrainz**：开放数据库，社区评分（5 分制），无需 key（要求有效 User-Agent，1 req/s）；
 * - **Discogs**：唱片目录权威，社区评分（5 分制），需 token。
 */
class MusicBrainzRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_MUSICBRAINZ
    override val label: String = "MusicBrainz"
    override val subjectTypes: Set<SubjectType> = setOf(SubjectType.MUSIC)
    override val requiresKey: Boolean = false
    override val requiresExternalId: Set<String> =
        setOf(SubjectExternalIdEntity.PROVIDER_MUSICBRAINZ)

    override suspend fun searchCandidates(subject: SubjectEntity, keys: RatingSourceKeys): List<RatingCandidate> {
        val query = listOfNotNull(subject.titleCN, subject.title).firstOrNull()?.trim().orEmpty()
        if (query.isEmpty()) return emptyList()
        val escaped = query.replace("\"", " ")
        val url = "https://musicbrainz.org/ws/2/release-group/?query=" +
            "releasegroup:\"${ratingUrlEncode(escaped)}\"&fmt=json&limit=8"
        val json = RatingHttp.getJson(url) ?: return emptyList()
        return json.arr("release-groups").objects().mapNotNull { item ->
            val mbid = item.str("id") ?: return@mapNotNull null
            val artists = item.arr("artist-credit").objects()
                .mapNotNull { it.str("name") }
                .joinToString(" / ")
            RatingCandidate(
                provider = SubjectExternalIdEntity.PROVIDER_MUSICBRAINZ,
                externalId = mbid,
                title = item.str("title") ?: mbid,
                subtitle = listOfNotNull(
                    artists.takeIf { it.isNotBlank() },
                    item.str("first-release-date")?.take(4),
                ).joinToString(" · ").takeIf { it.isNotBlank() },
                // MusicBrainz 是开放社区评分，样本普遍偏少 → 置信度只用于排序
                confidence = 0f,
            )
        }
    }

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val mbid = externalIds[SubjectExternalIdEntity.PROVIDER_MUSICBRAINZ] ?: return null
        val json = RatingHttp.getJson(
            "https://musicbrainz.org/ws/2/release-group/$mbid?inc=ratings&fmt=json"
        ) ?: return null
        val ratingObj = json.obj("rating") ?: return null
        val value = ratingObj.str("value")?.toFloatOrNull() ?: return null
        if (value <= 0f) return null
        return ExternalRating(
            sourceId = id,
            label = label,
            score = ExternalRating.toTenPoint(value, 5f),
            nativeScore = value,
            scoreMax = 5f,
            voteCount = ratingObj.int("votes-count"),
            sourceUrl = "https://musicbrainz.org/release-group/$mbid",
        )
    }
}

/** Discogs 社区评分（需个人 token；未配置时整源隐藏）。 */
class DiscogsRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_DISCOGS
    override val label: String = "Discogs"
    override val subjectTypes: Set<SubjectType> = setOf(SubjectType.MUSIC)
    override val requiresKey: Boolean = true
    override val requiresExternalId: Set<String> = setOf(SubjectExternalIdEntity.PROVIDER_DISCOGS)

    override fun isAvailable(keys: RatingSourceKeys): Boolean = !keys.usable(keys.discogsToken).isNullOrEmpty()

    override suspend fun searchCandidates(subject: SubjectEntity, keys: RatingSourceKeys): List<RatingCandidate> {
        val token = keys.usable(keys.discogsToken) ?: return emptyList()
        val query = listOfNotNull(subject.titleCN, subject.title).firstOrNull()?.trim().orEmpty()
        if (query.isEmpty()) return emptyList()
        val json = RatingHttp.getJson(
            "https://api.discogs.com/database/search?type=release&per_page=8" +
                "&q=${ratingUrlEncode(query)}&token=${ratingUrlEncode(token)}"
        ) ?: return emptyList()
        return json.arr("results").objects().mapNotNull { item ->
            val id = item.int("id") ?: return@mapNotNull null
            RatingCandidate(
                provider = SubjectExternalIdEntity.PROVIDER_DISCOGS,
                externalId = id.toString(),
                title = item.str("title") ?: "#$id",
                subtitle = item.str("year"),
                imageUrl = item.str("thumb"),
                confidence = 0f,
            )
        }
    }

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val releaseId = externalIds[SubjectExternalIdEntity.PROVIDER_DISCOGS] ?: return null
        val token = keys.usable(keys.discogsToken) ?: return null
        val json = RatingHttp.getJson(
            "https://api.discogs.com/releases/$releaseId?token=${ratingUrlEncode(token)}"
        ) ?: return null
        val ratingObj = json.obj("community")?.obj("rating") ?: return null
        val average = ratingObj.str("average")?.toFloatOrNull() ?: return null
        if (average <= 0f) return null
        return ExternalRating(
            sourceId = id,
            label = label,
            score = ExternalRating.toTenPoint(average, 5f),
            nativeScore = average,
            scoreMax = 5f,
            voteCount = ratingObj.int("count"),
            sourceUrl = "https://www.discogs.com/release/$releaseId",
        )
    }
}
