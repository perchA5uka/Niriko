package com.otakup.niriko.data.model

import com.otakup.niriko.data.local.entity.SubjectEntity
import kotlinx.serialization.Serializable

/**
 * 详情页全量数据容器。
 * 由 ViewModel 一次性并行加载后供 UI 消费。
 */
data class SubjectDetailData(
    val subject: SubjectEntity,
    val characters: List<CharacterInfo> = emptyList(),
    val staff: List<StaffInfo> = emptyList(),
    val episodes: List<EpisodeInfo> = emptyList(),
)

/**
 * 角色条目。
 *
 * B15：加 @Serializable 是为了把详情页的集合类结果落进 `subject_relation_cache`
 * （每行一条，见 DetailCacheStore）—— 重启后不用为同一部作品重新拉一遍角色/Staff/关联作品。
 */
@Serializable
data class CharacterInfo(
    val id: Long,
    val name: String,
    val nameCn: String?,
    val roleName: String?,
    val imageUrl: String?,
    val actors: List<StaffInfo> = emptyList(),
)

/** 制作人员条目（同样为 B15 的集合缓存加了 @Serializable）。 */
@Serializable
data class StaffInfo(
    val id: Long,
    val name: String,
    val nameCn: String?,
    val roleName: String?,
    val imageUrl: String?,
)

/** 剧集条目（同样为 B15 的集合缓存加了 @Serializable）。 */
@Serializable
data class EpisodeInfo(
    val id: Long,
    val name: String,
    val nameCn: String?,
    val desc: String?,
    val ep: Double,
    val sort: Double,
    val airdate: String?,
    val duration: String?,
    /** 放送状态：Air / Today / Tomorrow / NA。 */
    val status: String? = null,
    /** 本集讨论/回复数（热力图用）。 */
    val comment: Int = 0,
    /** 音乐曲目的碟片数（音乐类型，0 表示未分组）。 */
    val disc: Int = 0,
    /** 服务器解析的时长（秒）。 */
    val durationSeconds: Int = 0,
    /** 剧集类型：0=本篇，1=SP，2=OP，3=ED。 */
    val type: Int = 0,
    /** 本集剧照 URL（TMDb 每集 still）。无 TMDb 绑定时为 null。 */
    val stillUrl: String? = null,
)
