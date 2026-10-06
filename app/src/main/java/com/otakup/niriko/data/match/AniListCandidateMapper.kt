package com.otakup.niriko.data.match

import com.otakup.niriko.data.remote.game.GameItem
import com.otakup.niriko.data.remote.steam.SteamTitleMatcher

/**
 * AniList 搜索/详情返回的 [GameItem] → 可展示、可绑定的 [MatchCandidate]（纯函数，可 JVM 单测）。
 *
 * 这里是 AniList「匹配度」的唯一计算点。此前详情页自己手搓 MatchCandidate 却漏传
 * confidence，落回默认 0f，界面于是无论真实匹配度多少都显示 0%。
 *
 * 置信度沿用全 App 统一的标题匹配规则 [SteamTitleMatcher.bestConfidence]；
 * 候选标题取 `title` + `aliases`（AniList 的 synonyms 以 " / " 连接）。
 */
object AniListCandidateMapper {

    const val PROVIDER = "anilist"

    /** 用于比对的查询标题（去空、去重，保持首位是主标题）。 */
    fun queryTitles(title: String?, titleCN: String?): List<String> =
        listOfNotNull(title, titleCN)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun map(
        item: GameItem,
        queryTitles: Collection<String>,
        query: String? = null,
        confidenceOverride: Float? = null,
    ): MatchCandidate {
        val confidence = confidenceOverride
            ?: SteamTitleMatcher.bestConfidence(queryTitles, candidateTitles(item))
        return MatchCandidate(
            provider = PROVIDER,
            externalId = item.sourceGameId,
            title = item.title,
            subtitle = item.aliases?.takeIf { it.isNotBlank() }
                ?: item.ratingScore?.let { "%.1f 分".format(it) },
            imageUrl = item.coverUrl,
            confidence = confidence,
            reasons = reasons(confidence, query),
            matchedQuery = query,
        )
    }

    fun mapAll(
        items: Collection<GameItem>,
        queryTitles: Collection<String>,
        query: String? = null,
    ): List<MatchCandidate> = items.map { map(it, queryTitles, query) }

    private fun candidateTitles(item: GameItem): List<String> =
        buildList {
            add(item.title)
            // AniList 的 aliases 形如「日文名 / 罗马音 / 英文别名」
            item.aliases?.split(" / ", ",", "、")?.forEach { add(it) }
        }.map { it.trim() }.filter { it.isNotEmpty() }

    private fun reasons(confidence: Float, query: String?): List<String> = buildList {
        when {
            confidence >= 1f -> add("标题完全一致")
            confidence >= SteamTitleMatcher.MIN_CONFIDENCE -> add("标题高度相似")
            confidence > 0f -> add("标题部分相似")
        }
        if (!query.isNullOrBlank()) add("搜索词：$query")
    }
}
