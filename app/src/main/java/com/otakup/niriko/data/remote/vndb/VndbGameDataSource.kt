package com.otakup.niriko.data.remote.vndb

import com.otakup.niriko.data.match.MatchScorer
import com.otakup.niriko.data.remote.game.GameDataSource
import com.otakup.niriko.data.remote.game.GameDataSourceCapabilities
import com.otakup.niriko.data.remote.game.GameItem
import com.otakup.niriko.data.remote.game.GameItemDetail
import com.otakup.niriko.data.remote.vndb.dto.VndbQueryRequest
import com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add

/**
 * VNDB 作为通用游戏数据源（[GameDataSource] 实现）。
 *
 * 纯信息源（不做用户库导入）：查询 VNDB REST API v2（POST /kana/vn）。
 * - search：filters=["search","=",标题] + sort=searchrank；
 * - getDetail：filters=["id","=",vndbId] 拉全字段（开发者/标签/截图/时长/语言等）；
 * - 能力：search + detail + screenshots（截图含在 detail 内）。
 *
 * 全部方法异常保护（补充数据源，失败返回空/null 不影响主流程）。
 */
class VndbGameDataSource(
    private val apiService: VndbApiService = VndbApiClient.apiService,
) : GameDataSource {

    override val id: String = "vndb"

    override val displayName: String = "VNDB"

    override val capabilities: GameDataSourceCapabilities = GameDataSourceCapabilities(
        supportsSearch = true,
        supportsDetail = true,
        supportsScreenshots = true,
    )

    /** 详情字段（搜索也带全字段，保证卡片信息完整）。 */
    private val detailFields = listOf(
        "title", "alttitle", "titles.lang", "titles.title", "titles.latin", "titles.official", "titles.main",
        "description", "developers.id", "developers.name", "developers.original",
        "tags.id", "tags.name", "tags.category", "tags.rating",
        "released", "rating", "votecount",
        "image.url", "image.thumbnail", "image.dims", "image.thumbnail_dims",
        "length", "length_minutes", "platforms", "olang", "languages",
        "screenshots.id", "screenshots.url", "screenshots.thumbnail", "screenshots.dims",
    ).joinToString(", ")

    override suspend fun search(query: String, limit: Int): List<GameItem> {
        if (query.isBlank()) return emptyList()
        // 第 4 轮 D：单查询串 → 多查询串 + 全标题集合打分。
        // 用户可输入「中文名 / 罗马音」等任意串，这里把输入本身作为一个变体再补一个
        // 归一化变体（去空格/标点），提升「标题里带副标题」时的命中率。
        val queries = buildList {
            val trimmed = query.trim()
            add(trimmed)
            val compact = trimmed.replace(Regex("[\\s\\u3000]+"), " ")
            if (compact != trimmed) add(compact)
            // 中文名与拉丁名混写的「A / B」形式，拆开各搜一次
            trimmed.split('/', '／', '|').map { it.trim() }
                .filter { it.isNotEmpty() && it != trimmed }
                .forEach { add(it) }
        }.distinct()

        return runCatching {
            VndbSearchSupport.search(
                apiService = apiService,
                queries = queries,
                perQuery = limit.coerceIn(1, 20),
                maxQueries = 3,
                limit = limit.coerceIn(1, 20),
                // 无 Bangumi 上下文（这是「按关键词搜」入口），只按候选内部一致性打分：
                // 标题互相之间不加权，直接给出 1.0，让 VNDB 的 searchrank 顺序保持主导。
                scorer = { MatchScorer.ScoredMatch(1f, emptyList()) },
            ).map { it.vn.toGameItem() }
        }.getOrDefault(emptyList())
    }

    override suspend fun getDetail(sourceGameId: String): GameItemDetail? {
        if (sourceGameId.isBlank()) return null
        return runCatching {
            val vn = apiService.query(
                VndbQueryRequest(
                    filters = buildJsonArray { add("id"); add("="); add(sourceGameId) },
                    fields = detailFields,
                    results = 1,
                )
            ).results.firstOrNull() ?: return null
            GameItemDetail(
                item = vn.toGameItem(),
                screenshots = vn.screenshots.map { it.url },
            )
        }.getOrDefault(null)
    }

    override suspend fun getSimilarGames(sourceGameId: String, limit: Int): List<GameItem> {
        // VNDB vn 条目暂无可直接查询的 similar 关联端点；返回空（能力未声明）
        return emptyList()
    }

    /** VNDB 视觉小说 → 统一 GameItem。 */
    private fun VndbVisualNovelDto.toGameItem(): GameItem {
        // 中文标题优先（zh/zh-Hans/zh-Hant），否则主标题
        val cnTitle = titles.firstOrNull { it.lang.startsWith("zh") }?.title
        val displayTitle = cnTitle ?: title
        return GameItem(
            sourceGameId = id,
            title = displayTitle,
            aliases = listOfNotNull(title.takeIf { it != displayTitle }, alttitle)
                .distinct()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", "),
            coverUrl = image?.url,
            summary = description?.let { stripHtml(it).take(200) },
            platforms = platforms.mapNotNull { platformName(it) },
            developers = developers.map { it.name },
            publishers = emptyList(),
            // VNDB rating 0-100 → 统一按 10 分制存（与 Bangumi 一致），保留一位小数；
            // 无评分（非名作稀疏字段）保持 null，UI 显示「暂无评分」
            ratingScore = rating?.let { (it / 10f).let { f -> Math.round(f * 10) / 10f } },
            ratingCount = votecount,
            tags = tags.take(8).map { it.name },
            releaseDate = released?.takeIf { it != "TBA" && it != "unknown" },
        )
    }

    /** VNDB 平台代码 → 展示名（部分映射；未知返回原代码）。 */
    private fun platformName(code: String): String = when (code) {
        "win" -> "Windows"
        "lin" -> "Linux"
        "mac" -> "macOS"
        "web" -> "Web"
        "and" -> "Android"
        "ios" -> "iOS"
        "dvd" -> "DVD"
        "bd" -> "Blu-ray"
        "vnds" -> "NDS"
        "psp" -> "PSP"
        "ps2" -> "PS2"
        "ps3" -> "PS3"
        "ps4" -> "PS4"
        "ps5" -> "PS5"
        "psv" -> "PS Vita"
        "vita" -> "PS Vita"
        "swi" -> "Switch"
        "nin" -> "Nintendo"
        "3ds" -> "3DS"
        "nds" -> "NDS"
        "gba" -> "GBA"
        "gb" -> "GB"
        "xbo" -> "Xbox"
        "x360" -> "Xbox 360"
        "xone" -> "Xbox One"
        "xsx" -> "Xbox Series"
        "dos" -> "DOS"
        else -> code
    }

    /** 剥 HTML 标签（VNDB description 含 <br> 等）。 */
    private fun stripHtml(raw: String): String = raw
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
