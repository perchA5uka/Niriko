package com.otakup.niriko.data.remote.anilist

import android.util.Log
import com.otakup.niriko.data.model.AniListRanking
import com.otakup.niriko.data.model.AniListRichDetail
import com.otakup.niriko.data.remote.game.GameDataSource
import com.otakup.niriko.data.remote.game.GameDataSourceCapabilities
import com.otakup.niriko.data.remote.game.GameItem
import com.otakup.niriko.data.remote.game.GameItemDetail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "AniListRepo"

/** JsonElement 安全取值：字段为 JSON null（JsonNull）或类型不符时返回 null，而不是抛异常。 */
private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

/** JsonElement 安全取数组：字段为 JSON null（JsonNull）或类型不符时返回 null。 */
private fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

/**
 * AniList 作为通用作品数据源（[GameDataSource] 实现，id="anilist"）。
 *
 * 与 AniListDataSource（插件链搜索兜底）不同，本类是"补充查询通道"：
 * - search：按标题搜 ANIME + MANGA（GraphQL SEARCH），映射为 [GameItem]；
 * - getDetail：按 sourceGameId（`media-{id}`）拉取详情（DETAIL 查询）；
 * - 能力：search + detail。
 *
 * sourceGameId 用非纯数字 `media-{id}` 形式 → [com.otakup.niriko.data.remote.game.GameItemMapper.deriveSubjectId]
 * 走哈希，避免与 Bangumi subjectId 冲突；sourceKey=`anilist:media-{id}` 落库后，
 * Bangumi 无词条的作品即可成为独立条目（可收藏/进详情），与 Steam/VNDB 对齐。
 *
 * 全部方法异常保护，失败返回空/null（补充数据源，不影响主流程）。
 */
class AniListGameDataSource(
    private val client: AniListClient = AniListClient(),
) : GameDataSource {

    override val id: String = "anilist"

    override val displayName: String = "AniList"

    override val capabilities: GameDataSourceCapabilities = GameDataSourceCapabilities(
        supportsSearch = true,
        supportsDetail = true,
    )

    /** sourceGameId 前缀（区分纯数字 Bangumi subjectId）。 */
    private val idPrefix = "media-"

    /** 搜索两个类型：ANIME + MANGA（Bangumi 无词条的动画/漫画都能兜底命中）。 */
    private val searchTypes = listOf("ANIME", "MANGA")

    override suspend fun search(query: String, limit: Int): List<GameItem> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            searchTypes.flatMap { type ->
                val vars = mutableMapOf<String, JsonPrimitive>()
                vars["search"] = JsonPrimitive(query)
                vars["type"] = JsonPrimitive(type)
                vars["perPage"] = JsonPrimitive((limit / 2).coerceAtLeast(1))
                val pageObj = client.query(
                    AniListQueries.SEARCH,
                    vars,
                    listOf("data", "Page"),
                )
                (pageObj.jsonObject["media"]?.jsonArray ?: return@flatMap emptyList())
                    .mapNotNull { media ->
                        media.jsonObject.takeIf { it["id"]?.jsonPrimitive?.content?.toLongOrNull() != null }
                            ?.toGameItem()
                    }
            }.distinctBy { it.sourceGameId }.take(limit.coerceAtLeast(1))
        }.getOrDefault(emptyList())
    }

    override suspend fun getDetail(sourceGameId: String): GameItemDetail? {
        val id = sourceGameId.removePrefix(idPrefix).toLongOrNull() ?: return null
        return runCatching {
            val media = client.query(
                AniListQueries.DETAIL_BASIC,
                mapOf("id" to JsonPrimitive(id)),
                listOf("data", "Media"),
            ).jsonObject
            GameItemDetail(
                item = media.toGameItem(),
                screenshots = emptyList(),
            )
        }.getOrNull()
    }

    /** 拉取 AniList 独有富信息（热度/趋势/排名/下一集等）。失败返回 null。 */
    suspend fun getRichDetail(anilistId: Long): AniListRichDetail? {
        if (anilistId <= 0) return null
        return try {
            val media = client.query(
                AniListQueries.DETAIL,
                mapOf("id" to JsonPrimitive(anilistId)),
                listOf("data", "Media"),
            ).jsonObject
            val rich = media.toAniListRichDetail()
            Log.w(TAG, "getRichDetail anilistId=$anilistId popularity=${rich.popularity} rankings=${rich.rankings.size} nextAiring=${rich.nextAiringEpisode}")
            rich
        } catch (e: Exception) {
            Log.w(TAG, "getRichDetail failed anilistId=$anilistId", e)
            null
        }
    }

    /** Media 节点 → AniList 独有富信息模型。 */
    private fun kotlinx.serialization.json.JsonObject.toAniListRichDetail(): AniListRichDetail {
        val title = this["title"].objectOrNull()
        val id = this["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        val rankings = (this["rankings"].arrayOrNull() ?: emptyList())
            .mapNotNull { r ->
                val obj = r as? JsonObject ?: return@mapNotNull null
                AniListRanking(
                    rank = obj["rank"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    type = obj["type"]?.jsonPrimitive?.content,
                    year = obj["year"]?.jsonPrimitive?.content?.toIntOrNull(),
                    season = obj["season"]?.jsonPrimitive?.content,
                )
            }
        val next = this["nextAiringEpisode"].objectOrNull()
        val nextAiringAt = next?.get("airingAt")?.jsonPrimitive?.content?.toLongOrNull()
        val nextAiringEpisode = next?.get("episode")?.jsonPrimitive?.content?.toIntOrNull()
        return AniListRichDetail(
            id = id,
            titleRomaji = title?.get("romaji")?.jsonPrimitive?.content,
            titleEnglish = title?.get("english")?.jsonPrimitive?.content,
            titleNative = title?.get("native")?.jsonPrimitive?.content,
            format = this["format"]?.jsonPrimitive?.content,
            status = this["status"]?.jsonPrimitive?.content,
            source = this["source"]?.jsonPrimitive?.content,
            season = this["season"]?.jsonPrimitive?.content,
            seasonYear = this["seasonYear"]?.jsonPrimitive?.content?.toIntOrNull(),
            episodes = this["episodes"]?.jsonPrimitive?.content?.toIntOrNull(),
            chapters = this["chapters"]?.jsonPrimitive?.content?.toIntOrNull(),
            volumes = this["volumes"]?.jsonPrimitive?.content?.toIntOrNull(),
            duration = this["duration"]?.jsonPrimitive?.content?.toIntOrNull(),
            popularity = this["popularity"]?.jsonPrimitive?.content?.toIntOrNull(),
            favourites = this["favourites"]?.jsonPrimitive?.content?.toIntOrNull(),
            trending = this["trending"]?.jsonPrimitive?.content?.toIntOrNull(),
            rankings = rankings,
            nextAiringAt = nextAiringAt,
            nextAiringEpisode = nextAiringEpisode,
            siteUrl = this["siteUrl"]?.jsonPrimitive?.content,
        )
    }

    override suspend fun getSimilarGames(sourceGameId: String, limit: Int): List<GameItem> =
        emptyList() // AniList 无相似作品接口，留空

    /** Media 节点 → GameItem（sourceGameId = "media-{id}"）。 */
    private fun kotlinx.serialization.json.JsonObject.toGameItem(): GameItem {
        val id = this["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        val title = this["title"].objectOrNull()
        val cover = this["coverImage"].objectOrNull()
        val startDate = this["startDate"].objectOrNull()
        val year = startDate?.get("year")?.jsonPrimitive?.content?.toIntOrNull()
        val month = startDate?.get("month")?.jsonPrimitive?.content?.toIntOrNull()
        val day = startDate?.get("day")?.jsonPrimitive?.content?.toIntOrNull()
        val releaseDate = if (year != null) {
            "%04d-%02d-%02d".format(year, month ?: 1, day ?: 1)
        } else null
        val tags = (this["genres"].arrayOrNull()?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()) +
            (this["tags"].arrayOrNull()?.mapNotNull { tag ->
                (tag as? JsonObject)?.get("name")?.jsonPrimitive?.content
            } ?: emptyList())
        val score = this["averageScore"]?.jsonPrimitive?.content?.toIntOrNull()
        // 类型按 AniList Media.type（SEARCH/DETAIL 均含该字段）：MANGA → 漫画，其余 → 动画。
        // 与 GameItemMapper 配合使"bangumi 没有的动画/漫画"以正确类型落库（而非 GAME）。
        val mediaType = this["type"]?.jsonPrimitive?.content
        val subjectType = if (mediaType == "MANGA") com.otakup.niriko.data.model.SubjectType.MANGA
        else com.otakup.niriko.data.model.SubjectType.ANIME
        return GameItem(
            sourceGameId = "$idPrefix$id",
            title = title?.get("romaji")?.jsonPrimitive?.content
                ?: title?.get("english")?.jsonPrimitive?.content
                ?: title?.get("native")?.jsonPrimitive?.content
                ?: "Unknown",
            aliases = listOfNotNull(
                title?.get("english")?.jsonPrimitive?.content,
                title?.get("native")?.jsonPrimitive?.content,
            ).distinct().ifEmpty { null }?.joinToString(" / "),
            coverUrl = cover?.get("large")?.jsonPrimitive?.content
                ?: cover?.get("extraLarge")?.jsonPrimitive?.content,
            summary = this["description"]?.jsonPrimitive?.content
                ?.replace(Regex("<[^>]*>"), ""), // 剥 HTML 标签
            platforms = listOfNotNull(
                this["format"]?.jsonPrimitive?.content,
            ),
            ratingScore = score?.let { it / 10f },
            // AniList 没有评分人数，platforms 也不应混入 status；
            // 热度/趋势/收藏数等独有字段后续走专用 AniListDetail 模型，避免误填到 GameItem。
            ratingCount = null,
            tags = tags.distinct(),
            releaseDate = releaseDate,
            type = subjectType,
        )
    }
}
