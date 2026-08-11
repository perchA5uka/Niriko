package com.otakup.niriko.data.remote.steam

/**
 * 单游戏成就进度（GetPlayerAchievements + GetSchemaForGame 合并结果）。
 */
data class SteamAchievements(
    /** 已解锁成就数。 */
    val unlocked: Int = 0,
    /** 成就总数。 */
    val total: Int = 0,
    /** 成就明细（含定义与解锁状态）。 */
    val items: List<SteamAchievementItem> = emptyList(),
) {
    /** 解锁百分比（0-100）。 */
    val percent: Float get() = if (total == 0) 0f else unlocked.toFloat() / total * 100f
}

/** 单条成就：定义（schema）+ 用户解锁状态（playerAchievements）合并。 */
data class SteamAchievementItem(
    /** 成就 API 名。 */
    val apiName: String = "",
    /** 展示名（schema displayName，缺失回退 API 名）。 */
    val name: String = "",
    /** 成就描述（可能缺失）。 */
    val description: String? = null,
    /** 是否已解锁。 */
    val achieved: Boolean = false,
    /** 解锁时间戳（秒）；未解锁为 null。 */
    val unlockTime: Long? = null,
    /** 成就图标 URL（CDN 完整地址，可能缺失）。 */
    val icon: String? = null,
)
