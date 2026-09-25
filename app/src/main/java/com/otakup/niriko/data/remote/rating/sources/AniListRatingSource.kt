package com.otakup.niriko.data.remote.rating.sources

import android.util.Log
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.anilist.AniListClient
import com.otakup.niriko.data.remote.anilist.AniListQueries
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.remote.rating.RatingCandidate
import com.otakup.niriko.data.remote.rating.RatingSource
import com.otakup.niriko.data.remote.rating.RatingSourceKeys
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "AniListRatingSource"

/**
 * AniList 权威评分源（第 4 轮 F：能匹配上的动画**直接展示分数**，不再要求手动绑定）。
 *
 * ## 与既有 AniList 绑定的关系
 *
 * 项目里已有一个 `anilist_bindings` 表与详情页的「AniList 信息」区块，但那条路要求用户
 * **手动确认绑定**才会展示。用户反馈「AniList 能匹配却要手动」——因为绑定的语义是
 * 「写入一份长期关系」，而用户的诉求只是「看到分数」。
 *
 * 因此本源采用**双轨**策略（对齐阶段 G 的保守写库约定）：
 * - **读不写**：标题 + 年份高置信度命中时，直接把 `averageScore` 作为权威评分展示，
 *   **但不写任何绑定表**（本源的 [searchCandidates] 仅供 UI 展示候选，autobind 由调用方决定）；
 * - 想要封面/角色等深度数据时，用户仍可点「确认绑定」（走既有的 anilist_bindings 流程）。
 *
 * ## 为什么不需要 key
 *
 * AniList 的 GraphQL 是完全公开的（国内也可直连），因此 [requiresKey] = false，
 * [isAvailable] 恒为真——只要有网就有分。
 *
 * ## 标尺
 *
 * AniList 的 `averageScore` 是 **0-100** 的加权均分（不是 10 分制），
 * 因此 `nativeScore` 保留原值、`scoreMax = 100`，UI 会显示成「84 / 100」。
 * 同时按 [ExternalRating.toTenPoint] 给出统一的 10 分制用于同屏排序。
 */
class AniListRatingSource(
    private val client: AniListClient = AniListClient(),
) : RatingSource {

    override val id: String = ExternalRating.SOURCE_ANILIST
    override val label: String = "AniList"

    /** AniList 只覆盖动画与漫画（GAME/MUSIC/REAL 无对应条目）。 */
    override val subjectTypes: Set<SubjectType> = setOf(
        SubjectType.ANIME, SubjectType.MANGA, SubjectType.BOOK,
    )

    override val requiresKey: Boolean = false

    /**
     * 不需要预绑定：本源的主要用途就是「不用绑定也能看分」。
     * 已绑定 AniList 的用户由 [fetch] 走 id 精确取分（更准）。
     */
    override val requiresExternalId: Set<String> = emptySet()

    override fun isAvailable(keys: RatingSourceKeys): Boolean = true

    /**
     * 自动匹配阈值。
     *
     * 比 TMDb 的候选展示更严（0.72）：因为这里**不弹候选、直接展示分数**，
     * 匹配错的代价是「张冠李戴的分数」——比「少显示一个源」严重得多。
     */
    private val autoMatchThreshold = 0.72f

    /** 最近一次自动匹配的结果（供 UI 显示「AniList 分数来自哪个条目」）。 */
    data class AutoMatch(
        val anilistId: Int,
        val title: String,
        val score: Float,
        val scoreMax: Float,
        val confidence: Float,
        val siteUrl: String?,
        val episodes: Int?,
    )

    override suspend fun searchCandidates(
        subject: SubjectEntity,
        keys: RatingSourceKeys,
    ): List<RatingCandidate> {
        val media = queryTopMedia(subject) ?: return emptyList()
        val year = subject.airDate?.take(4)?.toIntOrNull()
        return media.mapNotNull { item ->
            val id = item["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            val titles = mediaTitles(item)
            if (titles.isEmpty()) return@mapNotNull null
            val score = TmdbMatchScorer.score(
                bangumiTitles = listOfNotNull(subject.titleCN, subject.title),
                candidateTitles = titles,
                bangumiYear = year,
                candidateYear = item["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content?.toIntOrNull(),
            )
            RatingCandidate(
                provider = PROVIDER_ANILIST,
                externalId = id.toString(),
                title = titles.first(),
                subtitle = buildSubtitle(item),
                imageUrl = null,
                confidence = score,
            )
        }.sortedByDescending { it.confidence }
    }

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        // 1) 已绑定 AniList：按 id 精确取分（零歧义）
        val boundId = externalIds[PROVIDER_ANILIST]?.toIntOrNull()
        if (boundId != null) {
            fetchById(boundId)?.let { return it.toExternalRating(note = "已绑定条目") }
        }
        // 2) 未绑定：自动匹配，仅当置信度足够高才展示（不写库）
        val auto = autoMatch(subject) ?: return null
        return auto.toExternalRating(note = "自动匹配（未写入绑定）")
    }

    /**
     * 自动匹配：标题 + 年份高置信度命中才返回。
     *
     * 单独暴露给 UI/诊断使用（详情页可以显示「AniList 自动匹配到 X」而不仅是分数）。
     */
    suspend fun autoMatch(subject: SubjectEntity): AutoMatch? {
        val media = queryTopMedia(subject) ?: return null
        val year = subject.airDate?.take(4)?.toIntOrNull()
        var best: AutoMatch? = null
        for (item in media) {
            val id = item["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
            // AniList 的 averageScore 对冷门条目可能为 null；此时不作为候选
            val rawScore = item["averageScore"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
            if (rawScore <= 0) continue
            val titles = mediaTitles(item)
            if (titles.isEmpty()) continue
            val candidateYear = item["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content?.toIntOrNull()
            val confidence = TmdbMatchScorer.score(
                bangumiTitles = listOfNotNull(subject.titleCN, subject.title),
                candidateTitles = titles,
                bangumiYear = year,
                candidateYear = candidateYear,
                bangumiEpisodes = subject.totalEpisodes,
                candidateEpisodes = item["episodes"]?.jsonPrimitive?.content?.toIntOrNull(),
            )
            if (confidence < autoMatchThreshold) continue
            // 年份明确不一致时直接否决（AniList 同名重制版很多）
            if (year != null && candidateYear != null && kotlin.math.abs(year - candidateYear) > 1) continue
            if (best == null || confidence > best.confidence) {
                best = AutoMatch(
                    anilistId = id,
                    title = titles.first(),
                    score = rawScore.toFloat(),
                    scoreMax = 100f,
                    confidence = confidence,
                    siteUrl = item["siteUrl"]?.jsonPrimitive?.content,
                    episodes = item["episodes"]?.jsonPrimitive?.content?.toIntOrNull(),
                )
            }
        }
        return best
    }

    private suspend fun fetchById(anilistId: Int): AutoMatch? = runCatching {
        val media = client.query(
            AniListQueries.DETAIL,
            mapOf("id" to JsonPrimitive(anilistId)),
            listOf("data", "Media"),
        ).jsonObject
        val rawScore = media["averageScore"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        if (rawScore <= 0) return null
        AutoMatch(
            anilistId = anilistId,
            title = mediaTitles(media).firstOrNull() ?: "#$anilistId",
            score = rawScore.toFloat(),
            scoreMax = 100f,
            confidence = 1f,
            siteUrl = media["siteUrl"]?.jsonPrimitive?.content,
            episodes = media["episodes"]?.jsonPrimitive?.content?.toIntOrNull(),
        )
    }.onFailure { Log.w(TAG, "fetchById($anilistId) failed", it) }.getOrNull()

    /** 统一的 AniList Media 搜索（只取评分相关字段）。 */
    private suspend fun queryTopMedia(
        subject: SubjectEntity,
    ): List<kotlinx.serialization.json.JsonObject>? = runCatching {
        val query = listOfNotNull(subject.titleCN, subject.title)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() } ?: return null
        val type = if (subject.type == SubjectType.MANGA) "MANGA" else "ANIME"
        val page = client.query(
            AniListQueries.RATING_ONLY,
            mapOf("search" to JsonPrimitive(query), "type" to JsonPrimitive(type)),
            listOf("data", "Page"),
        ).jsonObject
        page["media"]?.jsonArray?.map { it.jsonObject }.orEmpty()
    }.onFailure { Log.w(TAG, "AniList search failed", it) }.getOrNull()

    /** title 的三个语言字段都参与打分（用户反馈「罗马音没参与」的问题在这里修掉）。 */
    private fun mediaTitles(media: kotlinx.serialization.json.JsonObject): List<String> {
        val title = media["title"]?.jsonObject ?: return emptyList()
        return listOfNotNull(
            title["native"]?.jsonPrimitive?.content,
            title["romaji"]?.jsonPrimitive?.content,
            title["english"]?.jsonPrimitive?.content,
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun buildSubtitle(media: kotlinx.serialization.json.JsonObject): String? {
        val year = media["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content
        val score = media["averageScore"]?.jsonPrimitive?.content
        return listOfNotNull(
            year,
            score?.let { "\u2605 $it/100" },
            media["format"]?.jsonPrimitive?.content,
        ).joinToString(" · ").takeIf { it.isNotBlank() }
    }

    private fun AutoMatch.toExternalRating(note: String?) = ExternalRating(
        sourceId = ExternalRating.SOURCE_ANILIST,
        label = label,
        // score 是 10 分制（用于同屏排序），nativeScore 保留 AniList 的 0-100 原值
        score = ExternalRating.toTenPoint(score, scoreMax),
        nativeScore = score,
        scoreMax = scoreMax,
        voteCount = null,
        sourceUrl = siteUrl ?: "https://anilist.co/anime/$anilistId",
        note = note,
    )

    companion object {
        /**
         * provider 名。
         *
         * 注意：**刻意复用既有的 `anilist` 绑定表键名**（anilist_bindings 用 subjectId ↔ id），
         * 但本源不写库；这里只在「已绑定」时把它当作 externalIds 的 key 读出来。
         * 因此本常量与 AniListRepository 里的 provider 名必须保持一致。
         */
        const val PROVIDER_ANILIST = "anilist"
    }
}
