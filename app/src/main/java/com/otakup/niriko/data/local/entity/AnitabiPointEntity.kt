package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 圣地巡礼（Anitabi）取景地标快照（阶段 K）。
 * 仅动画且非 NSFW 条目；数据由 Anitabi.cn 提供，D7 缓存。
 */
@Entity(tableName = "anitabi_points")
data class AnitabiPointEntity(
    @PrimaryKey val subjectId: Long,
    val city: String = "",
    val pointsLength: Int = 0,
    val imagesLength: Int = 0,
    /** litePoints JSON 数组（cn/name/ep/s/image）。 */
    val litePointsJson: String = "[]",
    val fetchedAt: Long = System.currentTimeMillis(),
)
