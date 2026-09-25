package com.otakup.niriko.data.remote.vndb

import com.otakup.niriko.data.match.MatchQueryBuilder
import com.otakup.niriko.data.match.MatchScorer
import com.otakup.niriko.data.match.RawCandidate
import com.otakup.niriko.data.match.VndbProviderMatcher
import com.otakup.niriko.data.remote.vndb.dto.VndbQueryRequest
import com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

/**
 * VNDB 多查询串搜索（第 4 轮 D）。
 *
 * ## 为什么单独抽出来
 *
 * 匹配逻辑改造前有两份各自为政的实现：
 * - `VndbGameDataSource.search(query, limit)` —— 只用**一个**查询串、无本地打分；
 * - `VndbRepository.searchVndb(title)` —— 同样单查询串，靠 `SteamTitleMatcher` 事后打分。
 *
 * 两处都缺「多查询串 + 全标题集合打分」，这正是《魔法少女的魔女审判》匹配不上的原因。
 * 现在两条链路共用本对象，口径一致。
 *
 * ## 行为
 *
 * 按 [MatchQueryBuilder] 给出的查询串顺序逐个搜索，合并去重后用 [MatchScorer] 打分排序。
 * 任一查询失败静默跳过（VNDB 是补充源，不能拖垮主流程）。
 */
object VndbSearchSupport {

    /**
     * 多查询串搜索 + 本地打分排序。
     *
     * @param queries 查询串（调用方一般传 [MatchQueryBuilder.build] 的结果）
     * @param perQuery 每个查询串取多少条（VNDB 限流 200/5min，默认 10 条足够）
     * @param maxQueries 最多用几个查询串（省额度）
     * @param limit 最终返回条数
     * @param scorer 打分函数：返回 (分数, 理由)。允许调用方注入以便复用既有打分口径。
     */
    suspend fun search(
        apiService: VndbApiService,
        queries: List<String>,
        perQuery: Int = 10,
        maxQueries: Int = 4,
        limit: Int = 8,
        scorer: (RawCandidate) -> MatchScorer.ScoredMatch,
    ): List<ScoredVndbMatch> {
        val used = queries.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(maxQueries)
        if (used.isEmpty()) return emptyList()

        val merged = LinkedHashMap<String, VndbVisualNovelDto>()
        for (query in used) {
            val results = runCatching {
                apiService.query(
                    VndbQueryRequestFactory.search(query, perQuery)
                ).results
            }.getOrDefault(emptyList())
            results.forEach { vn -> if (vn.id.isNotBlank()) merged.putIfAbsent(vn.id, vn) }
        }
        if (merged.isEmpty()) return emptyList()

        return merged.values
            .map { vn ->
                val raw = toRawCandidate(vn)
                val scored = scorer(raw)
                ScoredVndbMatch(vn = vn, raw = raw, score = scored.score, reasons = scored.reasons)
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /** 一条带本地置信度的 VNDB 结果。 */
    data class ScoredVndbMatch(
        val vn: VndbVisualNovelDto,
        val raw: RawCandidate,
        val score: Float,
        val reasons: List<String>,
    )
}

/** VNDB 查询请求构造（集中一处，避免 filters 语法在多个文件里各写一遍）。 */
object VndbQueryRequestFactory {

    /** 搜索字段：包含 titles 全字段，否则打分器拿不到多语言标题。 */
    const val SEARCH_FIELDS =
        "id, title, alttitle, titles.lang, titles.title, titles.latin, " +
            "released, rating, votecount, popularity, platforms, length_minutes, " +
            "image.url, image.thumbnail, developers.name"

    fun search(query: String, results: Int = 10) = VndbQueryRequest(
        filters = buildJsonArray {
            add("search"); add("="); add(query)
        },
        fields = SEARCH_FIELDS,
        sort = "searchrank",
        results = results.coerceIn(1, 100),
    )
}

/**
 * VNDB 条目 → 统一原始候选（第 4 轮 D 的**修复核心**）。
 *
 * [RawCandidate.titles] 把 `titles[]` 里**所有**语言的标题都放进来
 * （含 `latin` 罗马音字段）——这正是「中文名匹配不上、反而命中同世界观作」的修复点：
 * 改造前打分器只看主标题，中文名与 VNDB 主标题（日文）没有公共子串，得分接近 0。
 */
fun toRawCandidate(vn: VndbVisualNovelDto): RawCandidate {
    val allTitles = buildList<String> {
        add(vn.title)
        vn.alttitle?.let { add(it) }
        vn.ctitle?.let { add(it) }
        vn.titles.forEach { t ->
            add(t.title)
            t.latin?.let { add(it) }
        }
    }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    // 展示标题：中文优先 → 主标题
    val display = vn.ctitle?.takeIf { it.isNotBlank() }
        ?: vn.titles.firstOrNull { it.lang.startsWith("zh") }?.title?.takeIf { it.isNotBlank() }
        ?: vn.title

    return RawCandidate(
        externalId = vn.id,
        titles = allTitles,
        year = vn.released?.take(4)?.toIntOrNull(),
        episodes = null,
        platforms = vn.platforms,
        displayTitle = display,
        subtitle = buildList {
            vn.released?.takeIf { it != "TBA" && it != "unknown" }?.let { add(it) }
            vn.rating?.let { add("\u2605 %.1f/10".format(it / 10f)) }
            vn.lengthMinutes?.let { add(formatPlaytime(it)) }
        }.joinToString(" · ").takeIf { it.isNotBlank() },
        imageUrl = vn.image?.url?.takeIf { it.isNotBlank() } ?: vn.image?.thumbnail,
    )
}

/**
 * 游玩时长格式化（VNDB 独有数据，UI 多处要用）。
 *
 * 统一成「12h30m」这种紧凑写法：详情页一行能放下，且比「750 分钟」直观。
 * 不足 1 小时只显示分钟。
 */
fun formatPlaytime(minutes: Int): String = when {
    minutes <= 0 -> "—"
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h${minutes % 60}m"
}

/**
 * VNDB 语言代码 → 展示名。
 *
 * VNDB 用 ISO 639-1（ja / en / zh / zh-Hans …）；UI 里显示代码对中文用户不友好，
 * 因此映射常用语言，未知回退原码（**不丢信息**）。
 */
fun vndbLanguageName(code: String): String = when (code.lowercase()) {
    "ja" -> "日语"
    "en" -> "英语"
    "zh", "zh-hans" -> "简体中文"
    "zh-hant" -> "繁体中文"
    "ko" -> "韩语"
    "ru" -> "俄语"
    "fr" -> "法语"
    "de" -> "德语"
    "es" -> "西班牙语"
    "it" -> "意大利语"
    "pt-br", "pt" -> "葡萄牙语"
    "pl" -> "波兰语"
    "nl" -> "荷兰语"
    "vi" -> "越南语"
    "th" -> "泰语"
    "id" -> "印尼语"
    "tr" -> "土耳其语"
    "uk" -> "乌克兰语"
    "cs" -> "捷克语"
    "hu" -> "匈牙利语"
    "ar" -> "阿拉伯语"
    "no" -> "挪威语"
    "sv" -> "瑞典语"
    "fi" -> "芬兰语"
    "da" -> "丹麦语"
    "el" -> "希腊语"
    "he" -> "希伯来语"
    else -> code
}
