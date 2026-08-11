package com.otakup.niriko.data.model

import com.otakup.niriko.data.local.entity.SubjectEntity

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

data class CharacterInfo(
    val id: Long,
    val name: String,
    val nameCn: String?,
    val roleName: String?,
    val imageUrl: String?,
    val actors: List<StaffInfo> = emptyList(),
)

data class StaffInfo(
    val id: Long,
    val name: String,
    val nameCn: String?,
    val roleName: String?,
    val imageUrl: String?,
)

data class EpisodeInfo(
    val id: Long,
    val name: String,
    val nameCn: String?,
    val desc: String?,
    val ep: Double,
    val sort: Double,
    val airdate: String?,
    val duration: String?,
    /** 音乐曲目的碟片数（音乐类型，0 表示未分组）。 */
    val disc: Int = 0,
    /** 服务器解析的时长（秒）。 */
    val durationSeconds: Int = 0,
    /** 剧集类型：0=本篇，1=SP，2=OP，3=ED。 */
    val type: Int = 0,
)
