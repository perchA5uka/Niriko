package com.otakup.niriko.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 剧集/章节表（Bangumi /v0/episodes 每集数据）。
 * 用于统计页「已播到第几集」、每集是否已播出、以及每集讨论热力图。
 */
@Entity(
    tableName = "episodes",
    indices = [Index(value = ["subjectId"])],
)
data class EpisodeEntity(
    /** Bangumi 章节 id。 */
    @PrimaryKey
    val epId: Long,
    val subjectId: Long,
    val sort: Double,
    val ep: Double,
    val name: String = "",
    val nameCn: String? = null,
    /**
     * 本集简介（Bangumi /v0/episodes 的 desc）。
     * 改造前 EpisodeRepository 在映射时把它丢掉了；列名用 description 因为 desc 是 SQL 保留字。
     */
    @ColumnInfo(name = "description")
    val desc: String? = null,
    val airdate: String? = null,
    val duration: String? = null,
    val durationSeconds: Int = 0,
    /** 0=本篇，1=SP，2=OP，3=ED（音乐类型曲目同样适用）。 */
    val type: Int = 0,
    /** 音乐碟片号（Bangumi /v0/episodes 的 disc）。改造前被丢弃，导致曲目分组恒为单碟。 */
    val disc: Int = 0,
    /** 放送状态：Air / Today / Tomorrow / NA。 */
    val status: String? = null,
    /** 本集讨论/回复数（热力图用）。 */
    val comment: Int = 0,
    /**
     * 本集剧照 URL（TMDb 每集 still，来自 season 详情接口）。
     * 与剧集列表、走势曲线同屏展示；无 TMDb 绑定时为 null。
     */
    val stillUrl: String? = null,
    val lastSyncTime: Long = 0L,
)
