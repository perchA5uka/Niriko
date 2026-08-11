package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Steam 游戏库导入的原始数据快照（表 steam_library_items）。
 *
 * 与 [CollectionEntity]（用户收藏主表）解耦：GetOwnedGames 拉取的游戏先落此表，
 * 用户确认后由导入器合并进 subjects / collections。
 *
 * 匹配结果二态：
 * - [bgmSubjectId] > 0 → 匹配到 Bangumi 词条，正常导入；
 * - [isPlaceholder] = true → Steam 有词条但 Bangumi 无 → 以负数占位 subjectId（-appId）
 *   创建占位条目展示，后续可重新匹配升级为正式 Bangumi 词条。
 */
@Entity(tableName = "steam_library_items")
data class SteamLibraryItemEntity(
    /** Steam appid（游戏库主键）。 */
    @PrimaryKey
    val appId: Int,
    /** 所属用户的 SteamID64。 */
    val steamId64: String,
    /** Steam 商店名称。 */
    val name: String,
    /** 封面 URL（header/capsule 图）。 */
    val coverUrl: String? = null,
    /** 总游玩时长（分钟）。 */
    val playtimeForeverMinutes: Int = 0,
    /** 近两周游玩时长（分钟），无则 null。 */
    val playtime2WeeksMinutes: Int? = null,
    /** 匹配到的 Bangumi subjectId（>0）；占位条目为 null。 */
    val bgmSubjectId: Long? = null,
    /** 是否为占位条目（Bangumi 无词条，以 -appId 占位展示）。 */
    val isPlaceholder: Boolean = false,
    /** 已合并进 subjects/collections。 */
    val imported: Boolean = false,
    val importTime: Long = System.currentTimeMillis(),
)
