package com.otakup.niriko.data.remote.rating

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType

/**
 * 权威评分源。
 *
 * 与 [com.otakup.niriko.data.remote.game.GameDataSource] 同构的设计意图：
 * 注册表 + 能力声明 + 按类型派发；用户没配 key 的源直接跳过、失败静默隐藏。
 *
 * 关键约束：**任何单个源失败都不得影响详情页**。调用方（ExternalRatingRepository）
 * 对每个源单独 runCatching。
 */
interface RatingSource {

    /** 唯一 id（与 ExternalRating.sourceId 对应）。 */
    val id: String

    /** 展示名。 */
    val label: String

    /** 适用的作品类型。 */
    val subjectTypes: Set<SubjectType>

    /** 是否需要用户提供密钥。 */
    val requiresKey: Boolean
        get() = false

    /** 依赖的外部身份 provider（见 SubjectExternalIdEntity 常量）；空表示只用标题/本地数据。 */
    val requiresExternalId: Set<String>
        get() = emptySet()

    /** 该源对当前配置是否可用（无需 key 或 key 已配置）。 */
    fun isAvailable(keys: RatingSourceKeys): Boolean = true

    /**
     * 该源是否覆盖此作品类型。
     *
     * 默认直接查 [subjectTypes]；需要「按用户设置动态扩缩覆盖范围」的源
     * （如 TMDb 对 GAME/BOOK/MUSIC 的开关）覆写本方法。
     * 调用方（注册表 / 仓库）应当用本方法而不是直接读 [subjectTypes]。
     */
    fun isAvailableFor(type: SubjectType, keys: RatingSourceKeys): Boolean = type in subjectTypes

    /**
     * 解析外部身份的候选（供用户在详情页确认后绑定）。
     * 返回空列表表示该源不需要绑定（例如 Steam 好评率复用已有的 steam_bindings）。
     */
    suspend fun searchCandidates(subject: SubjectEntity, keys: RatingSourceKeys): List<RatingCandidate> = emptyList()

    /** 抓取评分。返回 null 表示该条目在此源无数据。 */
    suspend fun fetch(
        subject: SubjectEntity,
        /** provider → externalId（来自 SubjectExternalIdEntity）。 */
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating?
}

/** 外部身份候选（供用户在详情页确认后绑定）。 */
data class RatingCandidate(
    val provider: String,
    val externalId: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val confidence: Float = 0f,
    /** provider 侧子键（TMDb 的季号等）。 */
    val subKey: String? = null,
)
