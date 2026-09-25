package com.otakup.niriko.data.match

import android.util.Log
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import com.otakup.niriko.data.remote.vndb.VndbApiClient
import com.otakup.niriko.data.remote.vndb.VndbApiService
import com.otakup.niriko.data.remote.vndb.dto.VndbQueryRequest
import com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto
import com.otakup.niriko.data.remote.vndb.toRawCandidate
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

private const val TAG = "VndbMatcher"

/**
 * VNDB 的 [ProviderMatcher] 实现（第 4 轮 D）。
 *
 * ## 修掉的具体缺陷
 *
 * 改造前 `VndbGameDataSource.search` 是：
 * ```kotlin
 * filters = ["search", "=", query]   // query 只有一个标题（中文名优先）
 * sort = "searchrank"
 * ```
 * 两个致命点：
 * 1. **只用中文名搜**：VNDB 的主标题是日文/罗马音，中文名往往搜不到
 *    → 《魔法少女的魔女审判》匹配不上；
 * 2. **VNDB 返回的 `titles[]` 没参与打分**：即使搜到了，也无法判断哪条才是对的
 *    → 它的同世界观未发售作（标题恰好与搜索串相似）反而被当成命中。
 *
 * 现在：
 * - 查询串由 [ExternalMatchService] 用 [MatchQueryBuilder] 生成（中文名 → 原名 → 别名 → ASCII）；
 * - 打分时把 **`titles[]` 的全部语言标题** 都传给 [MatchScorer]（本文件的 [toRawCandidate]）；
 * - 阈值由统一打分器判定，不再各源自定义。
 */
class VndbProviderMatcher(
    private val apiService: VndbApiService = VndbApiClient.apiService,
) : ProviderMatcher {

    override val provider: String =
        com.otakup.niriko.data.remote.rating.sources.VndbRatingSource.PROVIDER_VNDB

    override val label: String = "VNDB"

    /**
     * infobox 里可能出现的 VNDB 键名。
     *
     * Bangumi 的游戏词条常在 infobox 里写「vndb」或直接给 vndb.org 链接，
     * 这是 L1「零猜测」通道的来源。
     */
    override val infoboxKeys: List<String> = listOf("vndb", "vndb id", "vndb链接")

    /** 支持 `v12345` 与 `https://vndb.org/v12345` 两种写法。 */
    override val infoboxIdPattern: Regex = Regex("""v(\d{1,6})""", RegexOption.IGNORE_CASE)

    /** 搜索用字段：**必须**包含 titles 全字段，否则打分器拿不到多语言标题。 */
    private val searchFields = listOf(
        "title", "alttitle",
        "titles.lang", "titles.title", "titles.latin", "titles.official", "titles.main",
        "released", "rating", "votecount", "popularity",
        "platforms", "length_minutes",
        "image.url", "image.thumbnail",
        "developers.name",
    ).joinToString(", ")

    /**
     * 用**一个**查询串取候选（第 5 轮 D12 起有二级兜底）。
     *
     * 改造前只有 `["search", "=", q]` 一条路。`search` 是 VNDB 的全文检索，
     * 对中文标题与罗马音未必命中 —— 这正是《魔法少女的魔女审判》之类的
     * 「中文名搜不到」的来源之一。
     *
     * 现在：先走全文检索；**只有返回 0 条时**才退回 `["or", search, title~, alttitle~]`
     * （`~` 是子串匹配）。加「只有 0 条才退」这个条件是为了不白白多打一倍请求 ——
     * VNDB 限流 200 次 / 5 分钟。
     */
    override suspend fun query(text: String, subject: SubjectEntity): List<RawCandidate> {
        if (text.isBlank()) return emptyList()
        val primary = runQuery(searchFilters(text))
        if (primary.isNotEmpty()) return primary
        return runQuery(substringFilters(text))
    }

    /** 执行一次 VNDB 查询；失败静默返回空（VNDB 是补充源，不能拖垮主流程）。 */
    private suspend fun runQuery(filters: kotlinx.serialization.json.JsonArray): List<RawCandidate> =
        runCatching {
            apiService.query(
                VndbQueryRequest(
                    filters = filters,
                    fields = searchFields,
                    sort = "searchrank",
                    results = 10,
                )
            ).results.map { it.toRawCandidate() }
        }.onFailure { Log.w(TAG, "query failed filters=$filters", it) }
            .getOrDefault(emptyList())

    private fun searchFilters(text: String) = buildJsonArray {
        add("search"); add("="); add(text)
    }

    /** 子串匹配兜底：VNDB 的 `~` 是「包含」语义。 */
    private fun substringFilters(text: String) = buildJsonArray {
        add("or")
        add(buildJsonArray { add("search"); add("="); add(text) })
        add(buildJsonArray { add("title"); add("~"); add(text) })
        add(buildJsonArray { add("alttitle"); add("~"); add(text) })
    }

    override suspend fun byId(externalId: String, subject: SubjectEntity): RawCandidate? {
        val id = normalizeId(externalId) ?: return null
        return runCatching {
            apiService.query(
                VndbQueryRequest(
                    filters = buildJsonArray { add("id"); add("="); add(id) },
                    fields = searchFields,
                    results = 1,
                )
            ).results.firstOrNull()?.toRawCandidate()
        }.onFailure { Log.w(TAG, "byId failed id=$id", it) }
            .getOrNull()
    }

    /**
     * VNDB 条目 → 统一原始候选。
     *
     * 转调顶层 [toRawCandidate]：同一份映射逻辑被 `VndbGameDataSource.search`
     * 与详情页匹配共用，避免两处口径漂移。
     */
    private fun VndbVisualNovelDto.toRawCandidate(): RawCandidate =
        com.otakup.niriko.data.remote.vndb.toRawCandidate(this)

    companion object {
        /**
         * 规范化 VNDB id：接受 `v17` / `17` / `https://vndb.org/v17`，统一成 `v17`。
         *
         * 用户手粘的往往是链接或裸数字，这里统一处理（解析不出来返回 null，
         * 让 UI 提示「输入非法」而不是发一个必然 400 的请求）。
         */
        fun normalizeId(raw: String): String? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            Regex("""v(\d{1,6})""", RegexOption.IGNORE_CASE).find(text)?.let {
                return "v" + it.groupValues[1]
            }
            return Regex("""^(\d{1,6})$""").find(text)?.let { "v" + it.groupValues[1] }
        }
    }
}
