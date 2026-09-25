package com.otakup.niriko.data.remote.rating.sources

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.remote.rating.RatingHttp
import com.otakup.niriko.data.remote.rating.RatingSource
import com.otakup.niriko.data.remote.rating.RatingSourceKeys
import com.otakup.niriko.data.remote.rating.int
import com.otakup.niriko.data.remote.rating.obj
import com.otakup.niriko.data.remote.rating.str

/**
 * Steam 玩家好评率（**无需 API key**）。
 *
 * 这是游戏「玩家侧」最权威、最容易拿到的口碑指标，也是项目此前完全缺失的一块
 * （详情页只显示了 Metacritic 与当前在线人数）。
 *
 * 接口：`store.steampowered.com/appreviews/{appid}?json=1&language=all&purchase_type=all&num_per_page=0`
 * ——`num_per_page=0` 只取汇总，不返回评测正文。
 *
 * appId 由 ExternalRatingRepository 从既有 steam_bindings 注入（provider = "steam"），
 * 因此本源无需外部身份解析、也不需要用户操作。
 */
class SteamReviewSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_STEAM_REVIEW
    override val label: String = "Steam 好评率"

    override val subjectTypes: Set<SubjectType> = setOf(SubjectType.GAME)

    override val requiresKey: Boolean = false

    /** 依赖 Steam 绑定（由仓库从 steam_bindings 注入，provider 名为 "steam"）。 */
    override val requiresExternalId: Set<String> = setOf(PROVIDER_STEAM)

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val appId = externalIds[PROVIDER_STEAM]?.toIntOrNull() ?: return null
        val url = "https://store.steampowered.com/appreviews/$appId" +
            "?json=1&language=all&purchase_type=all&num_per_page=0&filter=summary"
        val json = RatingHttp.getJson(url) ?: return null
        val summary = json.obj("query_summary") ?: return null

        val total = summary.int("total_reviews") ?: 0
        if (total <= 0) return null
        val positive = summary.int("total_positive") ?: 0
        val percent = (positive.toFloat() / total.toFloat() * 100f)

        return ExternalRating(
            sourceId = id,
            label = label,
            score = ExternalRating.toTenPoint(percent, 100f),
            nativeScore = percent,
            scoreMax = 100f,
            voteCount = total,
            sourceUrl = "https://store.steampowered.com/app/$appId/#app_reviews_hash",
            note = summary.str("review_score_desc"),
        )
    }

    companion object {
        /** 伪 provider：Steam appId 来自既有 steam_bindings，本身不占 subject_external_ids。 */
        const val PROVIDER_STEAM = "steam"
    }
}
