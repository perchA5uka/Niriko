package com.otakup.niriko.data.remote.rating.sources

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.remote.rating.RatingHttp
import com.otakup.niriko.data.remote.rating.RatingSource
import com.otakup.niriko.data.remote.rating.RatingSourceKeys
import com.otakup.niriko.data.remote.rating.float
import com.otakup.niriko.data.remote.rating.int
import com.otakup.niriko.data.remote.rating.objects
import com.otakup.niriko.data.remote.rating.arr

/**
 * VNDB 评分（VN 界唯一权威库，读接口免鉴权）。
 *
 * 项目此前已在「VNDB 信息」区块展示评分；本源的用途是把它**并入统一权威评分卡**，
 * 从而支持"跨源横向对比"（VNDB / Steam 好评率 / Metacritic / IGDB 同屏）。
 *
 * 接口：POST https://api.vndb.org/kana/vn，filter ["id","=","v123"]，fields rating,votecount
 * 限流约 200 次/5 分钟。
 */
class VndbRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_VNDB
    override val label: String = "VNDB"
    override val subjectTypes: Set<SubjectType> = setOf(SubjectType.GAME)
    override val requiresKey: Boolean = false

    /** vndbId 由仓库从既有 vndb_bindings 注入（provider 名为 "vndb"）。 */
    override val requiresExternalId: Set<String> = setOf(PROVIDER_VNDB)

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val vndbId = externalIds[PROVIDER_VNDB]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val payload = """{"filters":["id","=","${vndbId.replace("\"", "")}"],""" +
            """"fields":"id,title,rating,votecount,average"}"""
        val json = RatingHttp.postJson(
            url = "https://api.vndb.org/kana/vn",
            body = payload,
            contentType = "application/json",
        ) ?: return null
        val item = json.arr("results")?.objects()?.firstOrNull() ?: return null
        val rating = item.float("rating") ?: item.float("average") ?: return null
        if (rating <= 0f) return null
        return ExternalRating(
            sourceId = id,
            label = label,
            score = ExternalRating.toTenPoint(rating, 10f),
            nativeScore = rating,
            scoreMax = 10f,
            voteCount = item.int("votecount"),
            sourceUrl = "https://vndb.org/$vndbId",
        )
    }

    companion object {
        /** 伪 provider：VNDB id 来自既有 vndb_bindings。 */
        const val PROVIDER_VNDB = "vndb"
    }
}
