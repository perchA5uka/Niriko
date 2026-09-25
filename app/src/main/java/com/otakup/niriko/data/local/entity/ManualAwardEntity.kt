package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 手动录入的权威机构成绩（表 manual_awards）。
 *
 * 用于**没有可接入 API** 的权威机构：Fami通交叉评测（40 分制）、Billboard 榜位、
 * Oricon 榜位、非 Steam 游戏的 Metacritic 分数等。
 * 方案明确：这些机构一律**不做抓取**（版权 + 反爬），改由用户手动录入并在 UI 上标注「手动录入」。
 */
@Entity(
    tableName = "manual_awards",
    indices = [Index(value = ["subjectId"])],
)
data class ManualAwardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val subjectId: Long,
    /** famitsu / billboard / oricon / metacritic / rym / aoty / other。 */
    val sourceId: String,
    /** 分数（Fami通 38、Metacritic 92）。榜位类留空。 */
    val score: Float? = null,
    val scoreMax: Float = 40f,
    /** 榜位（Billboard 200 第 12 名 = 12）。分数类留空。 */
    val rankPosition: Int? = null,
    /** 备注（榜期、奖项名等）。 */
    val note: String? = null,
    val url: String? = null,
    val createTime: Long = 0L,
)
