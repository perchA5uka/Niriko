package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.local.dao.SteamDao
import com.otakup.niriko.data.local.entity.SteamLibraryItemEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.steam.dto.SteamOwnedGameDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Steam 游戏库条目 → Bangumi 词条匹配器。
 *
 * 匹配策略（按优先级）：
 * 1. **绑定表优先**：反查 [SteamDao.getBindingByAppId] 得到已绑定的 Bangumi subjectId
 *    （此前搜索/导入已绑定过，直接复用，免重复匹配）；
 * 2. **标题兜底**：未绑定时用 Steam 名称走 Bangumi 搜索（GAME 类型），
 *    经 [SteamTitleMatcher.confidence] 置信度判定，≥ 阈值才采纳；
 * 3. **占位判定**：两者均未命中 → 标记为占位候选（Bangumi 无词条），
 *    由导入器以负数 subjectId（-appId）创建占位条目展示。
 *
 * 匹配结果以 [SteamLibraryPreview] 行模型表达（原始数据 + 匹配结果 + 勾选态），
 * 与 BilibiliSyncPreview 同构，供导入预览 UI 使用。
 */

/** 预览行：Steam 库原始数据 + 匹配结果 + 本地收藏对照 + 勾选态。 */
data class SteamLibraryPreview(
    val appId: Int,
    val name: String,
    val coverUrl: String? = null,
    val playtimeForeverMinutes: Int = 0,
    val playtime2WeeksMinutes: Int? = null,
    /** 匹配到的 Bangumi subjectId（>0）；占位条目为 null。 */
    val bgmSubjectId: Long? = null,
    /** 占位候选（Bangumi 无词条，将用 -appId 占位展示）。 */
    val isPlaceholder: Boolean = false,
    /** 本地是否已存在该条目对应收藏。 */
    val alreadyInCollection: Boolean = false,
    /** 本地已匹配条目标题（供 UI 展示）。 */
    val localSubjectTitle: String? = null,
    /** 勾选态（默认勾选已匹配项）。 */
    val selected: Boolean = false,
) {
    val isMatched: Boolean get() = bgmSubjectId != null

    /** 占位条目 subjectId（负数映射，稳定可逆，全应用共用约定）。 */
    val placeholderSubjectId: Long get() = -appId.toLong()
}

/** 匹配所需的外部依赖（纯函数可注入，便于单测）。 */
class SteamLibraryMatcher(
    private val steamDao: SteamDao? = null,
    private val searchBangumiGame: suspend (String) -> List<SubjectEntity> = { emptyList() },
    private val inCollection: suspend (Long) -> Boolean = { false },
    private val subjectTitle: suspend (Long) -> String? = { null },
) {

    companion object {
        /** 标题兜底匹配的置信度阈值（与 SteamTitleMatcher 一致）。 */
        const val MIN_CONFIDENCE = SteamTitleMatcher.MIN_CONFIDENCE
    }

    /**
     * 将 GetOwnedGames 原始条目批量转换为预览行（并发匹配）。
     * @param games Steam 库原始条目
     * @return 预览行列表（保持输入顺序；已绑定/已收藏标记齐全）
     */
    suspend fun toPreviews(games: List<SteamOwnedGameDto>): List<SteamLibraryPreview> {
        if (games.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            coroutineScope {
                games.map { game ->
                    async { toPreview(game) }
                }.awaitAll()
            }
        }
    }

    /** 单条匹配。 */
    suspend fun toPreview(game: SteamOwnedGameDto): SteamLibraryPreview {
        val appId = game.appid
        // 1) 绑定表优先
        val bound = steamDao?.getBindingByAppId(appId)
        if (bound != null) {
            val subjectId = bound.subjectId
            val title = subjectTitle(subjectId)
            return SteamLibraryPreview(
                appId = appId,
                name = game.name ?: "",
                coverUrl = coverUrl(game),
                playtimeForeverMinutes = game.playtimeForever,
                playtime2WeeksMinutes = game.playtime2Weeks,
                bgmSubjectId = subjectId.takeIf { it > 0 },
                isPlaceholder = subjectId < 0,
                alreadyInCollection = inCollection(subjectId),
                localSubjectTitle = title,
                selected = subjectId > 0, // 已绑定正式词条默认勾选；占位不默认勾
            )
        }

        // 2) 标题兜底
        val name = game.name ?: ""
        if (name.isNotBlank()) {
            val candidates = runCatching { searchBangumiGame(name) }.getOrDefault(emptyList())
            val best = candidates
                .mapNotNull { candidate ->
                    val score = SteamTitleMatcher.confidence(name, candidate.titleCN ?: candidate.title)
                    if (score >= MIN_CONFIDENCE) candidate to score else null
                }
                .maxByOrNull { it.second }
                ?.first
            if (best != null) {
                return SteamLibraryPreview(
                    appId = appId,
                    name = name,
                    coverUrl = coverUrl(game),
                    playtimeForeverMinutes = game.playtimeForever,
                    playtime2WeeksMinutes = game.playtime2Weeks,
                    bgmSubjectId = best.subjectId,
                    isPlaceholder = false,
                    alreadyInCollection = inCollection(best.subjectId),
                    localSubjectTitle = best.titleCN ?: best.title,
                    selected = true,
                )
            }
        }

        // 3) 占位候选（Bangumi 无词条）
        return SteamLibraryPreview(
            appId = appId,
            name = name.ifBlank { "App $appId" },
            coverUrl = coverUrl(game),
            playtimeForeverMinutes = game.playtimeForever,
            playtime2WeeksMinutes = game.playtime2Weeks,
            bgmSubjectId = null,
            isPlaceholder = true,
            alreadyInCollection = inCollection(-appId.toLong()),
            selected = false, // 占位默认不勾选（用户确认后导入）
        )
    }

    /** 封面 URL：优先 icon，其次 logo（CDN 前缀拼接）。 */
    private fun coverUrl(game: SteamOwnedGameDto): String? {
        val id = game.appid
        return when {
            !game.imgIconUrl.isNullOrBlank() ->
                "https://media.steampowered.com/steamcommunity/public/images/apps/$id/${game.imgIconUrl}.jpg"
            !game.imgLogoUrl.isNullOrBlank() ->
                "https://media.steampowered.com/steamcommunity/public/images/apps/$id/${game.imgLogoUrl}.jpg"
            else -> null
        }
    }
}
