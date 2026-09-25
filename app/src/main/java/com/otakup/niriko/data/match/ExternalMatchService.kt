package com.otakup.niriko.data.match

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.remote.InfoBoxEntry

/**
 * 跨数据源统一匹配服务（第 4 轮 D）。
 *
 * ## 为什么需要它
 *
 * 用户反馈《魔法少女的魔女审判》在 VNDB 匹配不上，而它的**同世界观未发售作**能匹配上。
 * 定位到的原因是 `VndbGameDataSource.search` 只用**一个标题**（`subject.displayTitle`，
 * 中文优先）+ `["search","=",q]` 查询，且 VNDB 返回的 `titles[]`（多语言标题集合）
 * **完全没参与打分**。于是：
 * - 中文名在 VNDB 里不是主标题 → 搜不到；
 * - 搜到的那条（同世界观作）标题恰好相似 → 反而命中。
 *
 * 更普遍的问题是：每个源（TMDb / VNDB / AniList / 豆瓣 …）的匹配逻辑各写一套、
 * 彼此不一致，且都缺「手动兜底」。
 *
 * ## 四层匹配
 *
 * | 层 | 手段 | 置信度 |
 * |---|---|---|
 * | L1 | **infobox 明确 ID** | 1.0（零猜测，可自动写库） |
 * | L2 | **多查询串**：中文名 / 原名 / 罗马音 / 别名，逐个查询后合并去重 | — |
 * | L3 | **多字段打分**：候选侧用**全部**标题集合 + 年份 + 集数/时长 + 平台 | 0..1 |
 * | L4 | **手动兜底**：自定义关键词 / 粘贴 ID / 候选点选 | 用户确认 |
 *
 * 本文件负责 L1–L3 的**通用部分**（查询串构造、打分、infobox 解析）与编排器
 * [ExternalMatchService]；各源的「怎么发请求」由 [ProviderMatcher] 实现。
 */

/** 一次匹配的候选（比 RatingCandidate 多带匹配来源与解释）。 */
data class MatchCandidate(
    val provider: String,
    val externalId: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    /** 置信度 0..1。 */
    val confidence: Float = 0f,
    /** provider 侧子键（TMDb 季号等）。 */
    val subKey: String? = null,
    /** 匹配依据（UI 展示「为什么认为它是这条」，可解释性是本项目的一贯要求）。 */
    val reasons: List<String> = emptyList(),
    /** 命中该候选的查询串（诊断错绑用）。 */
    val matchedQuery: String? = null,
    /** 来源层：INFOBOX / QUERY / MANUAL。 */
    val source: String = SOURCE_QUERY,
) {
    fun toRatingCandidate() = com.otakup.niriko.data.remote.rating.RatingCandidate(
        provider = provider,
        externalId = externalId,
        title = title,
        subtitle = subtitle,
        imageUrl = imageUrl,
        confidence = confidence,
        subKey = subKey,
    )

    companion object {
        const val SOURCE_INFOBOX = "INFOBOX"
        const val SOURCE_QUERY = "QUERY"
        const val SOURCE_MANUAL = "MANUAL"
    }
}

/**
 * 一个数据源的匹配实现。
 *
 * 每个 provider（VNDB / TMDb / AniList / 豆瓣 / IGDB …）实现本接口，
 * 由 [ExternalMatchService] 统一驱动「多查询串 + 打分 + 去重 + 排序」。
 */
interface ProviderMatcher {

    /** provider 名（与 SubjectExternalIdEntity 常量 / 绑定表对齐）。 */
    val provider: String

    /** 展示名。 */
    val label: String

    /**
     * 用**一个**查询串请求候选。
     *
     * 实现方只需关心「怎么搜」，不要自己做标题相似度判断——
     * 那部分由 [MatchScorer] 统一做，保证各源口径一致。
     */
    suspend fun query(text: String, subject: SubjectEntity): List<RawCandidate>

    /**
     * 按 provider 侧 ID 直接取候选（粘贴 ID / infobox ID 用）。
     * 默认不支持。
     */
    suspend fun byId(externalId: String, subject: SubjectEntity): RawCandidate? = null

    /** 该 provider 在 infobox 里可能的键名（用于 L1 明确 ID 提取）。 */
    val infoboxKeys: List<String> get() = emptyList()

    /** 从 infobox 值里抠出 provider id 的正则（第一个捕获组即 id）。 */
    val infoboxIdPattern: Regex? get() = null
}

/**
 * provider 侧返回的原始候选（只带事实，不带判断）。
 *
 * [titles] 是**最关键**的字段：必须把 provider 返回的**全部**标题放进来
 * （VNDB 的 `titles[]`、TMDb 的 name+original_name、AniList 的 native/romaji/english），
 * 否则「中文名 vs 主标题」这类错配会重现。
 */
data class RawCandidate(
    val externalId: String,
    val titles: List<String>,
    val year: Int? = null,
    val episodes: Int? = null,
    val platforms: List<String> = emptyList(),
    /** 展示用主标题（一般是 titles 的第一个）。 */
    val displayTitle: String = titles.firstOrNull().orEmpty(),
    val subtitle: String? = null,
    val imageUrl: String? = null,
    /** provider 侧重定向子键（TMDb 季号）。 */
    val subKey: String? = null,
)

/**
 * 多字段打分（纯函数，可单测）。
 *
 * 与 [com.otakup.niriko.data.remote.rating.sources.TmdbMatchScorer] 的关系：
 * 那个评分器是「TMDb 专用、双标题列表 + 年份 + 集数」的早期实现。
 * 本评分器是它的**扩展与泛化**（多字段 + 平台 + 解释输出），
 * 但**保留 TMDb 那个不动**——它的阈值已被既有候选排序依赖，改动会引发回归。
 *
 * 打分构成（总和 clamp 到 0..1）：
 * - 标题相似度（0..1）× **0.78**：双向最长公共子串占比取最大
 * - 年份：同年 +0.12，±1 +0.06，明确冲突（>=3 年）**-0.25**（同名重制版很多，必须惩罚）
 * - 集数：相等 +0.06，差 ≤2 +0.03
 * - 平台重叠（游戏类）：有交集 +0.04
 * - 标题完全相等（归一化后）：直接给 0.95 下限（避免被其它字段拖低）
 */
object MatchScorer {

    /** 打分阈值：达到即可作为「高置信度命中」（是否写库由调用方按策略决定）。 */
    const val HIGH_CONFIDENCE = 0.80f

    fun score(
        bangumiTitles: List<String>,
        candidate: RawCandidate,
        bangumiYear: Int? = null,
        bangumiEpisodes: Int? = null,
        bangumiPlatforms: List<String> = emptyList(),
    ): ScoredMatch {
        val reasons = mutableListOf<String>()

        val titleResult = bestTitle(bangumiTitles, candidate.titles)
        if (titleResult.score <= 0f) {
            return ScoredMatch(0f, listOf("标题无重合"))
        }
        reasons += "标题「${titleResult.candidateTitle}」相似度 ${"%.0f".format(titleResult.score * 100)}%"

        var total = titleResult.score * 0.78f

        if (bangumiYear != null && candidate.year != null) {
            when (kotlin.math.abs(bangumiYear - candidate.year)) {
                0 -> {
                    total += 0.12f
                    reasons += "年份一致（$bangumiYear）"
                }
                1 -> {
                    total += 0.06f
                    reasons += "年份相差 1 年（$bangumiYear vs ${candidate.year}）"
                }
                else -> {
                    if (kotlin.math.abs(bangumiYear - candidate.year) >= 3) {
                        total -= 0.25f
                        reasons += "年份冲突（$bangumiYear vs ${candidate.year}）"
                    }
                }
            }
        }

        if (bangumiEpisodes != null && candidate.episodes != null && bangumiEpisodes > 0) {
            val diff = kotlin.math.abs(bangumiEpisodes - candidate.episodes)
            when {
                diff == 0 -> {
                    total += 0.06f
                    reasons += "集数一致（$bangumiEpisodes）"
                }
                diff <= 2 -> {
                    total += 0.03f
                    reasons += "集数接近（$bangumiEpisodes vs ${candidate.episodes}）"
                }
            }
        }

        if (bangumiPlatforms.isNotEmpty() && candidate.platforms.isNotEmpty()) {
            val overlap = bangumiPlatforms.map { it.lowercase() }
                .intersect(candidate.platforms.map { it.lowercase() }.toSet())
            if (overlap.isNotEmpty()) {
                total += 0.04f
                reasons += "平台重叠（${overlap.take(3).joinToString("/")}）"
            }
        }

        // 完全相等时给下限：某些源（VNDB 的日文原名）标题一字不差，
        // 此时不应因为「缺年份」而落到阈值之下。
        if (titleResult.exact) {
            total = maxOf(total, 0.95f)
            reasons += "标题完全一致"
        }

        return ScoredMatch(
            score = total.coerceIn(0f, 1f),
            reasons = reasons,
            exactTitle = titleResult.exact,
            matchedCandidateTitle = titleResult.candidateTitle,
        )
    }

    /** 双向取最优：候选的**任意**标题与作品的**任意**标题比较。 */
    private fun bestTitle(left: List<String>, right: List<String>): TitleResult {
        var best = 0f
        var bestTitle = ""
        var exact = false
        for (a in left) {
            val na = normalize(a)
            if (na.isEmpty()) continue
            for (b in right) {
                val nb = normalize(b)
                if (nb.isEmpty()) continue
                if (na == nb) {
                    return TitleResult(1f, b, true)
                }
                val s = similarity(na, nb)
                if (s > best) {
                    best = s
                    bestTitle = b
                }
            }
        }
        return TitleResult(best, bestTitle, exact)
    }

    private data class TitleResult(val score: Float, val candidateTitle: String, val exact: Boolean)

    /**
     * 归一化：小写、去空白与标点、全角转半角、去常见季/期后缀。
     *
     * 与 TmdbMatchScorer 的 normalize 保持同样规则（两处口径必须一致，
     * 否则同一个候选在两个源里会得到不同的相似度）。
     */
    fun normalize(raw: String): String {
        val lowered = raw.lowercase()
        val sb = StringBuilder(lowered.length)
        for (ch in lowered) {
            val c = when {
                ch.code == 0x3000 -> ' '
                ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                else -> ch
            }
            if (c.isLetterOrDigit()) sb.append(c)
        }
        return sb.toString()
            .removeSuffix("season")
            .removeSuffix("th")
            .removeSuffix("nd")
            .removeSuffix("rd")
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        val small = if (a.length <= b.length) a else b
        val large = if (a.length <= b.length) b else a
        if (small.isEmpty()) return 0f
        val lcs = longestCommonSubstring(small, large)
        return lcs.toFloat() / small.length
    }

    private fun longestCommonSubstring(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        var best = 0
        val prev = IntArray(b.length + 1)
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else 0
                if (cur[j] > best) best = cur[j]
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
            java.util.Arrays.fill(cur, 0)
        }
        return best
    }

    /** 打分结果（含解释，便于 UI 显示与排障）。 */
    data class ScoredMatch(
        val score: Float,
        val reasons: List<String>,
        val exactTitle: Boolean = false,
        val matchedCandidateTitle: String = "",
    )
}

/**
 * 查询串构造（L2 的核心）。
 *
 * 用户反馈的根因之一就是「只用一个标题」。这里把所有**已知**的标题变体都取出来：
 * 中文名、原名、infobox 里的别名/罗马音、以及纯 ASCII 变体。
 */
object MatchQueryBuilder {

    /** infobox 里可能存放别名/罗马音的键名（Bangumi 词条的常见写法）。 */
    private val ALIAS_KEYS = listOf(
        "别名", "別名", "英文名", "英文名/其他", "罗马音", "羅馬音", "other name",
        "alias", "aliases", "原名", "原作名", "中文名", "外文名", "日文名",
    )

    /** infobox 值里以这些分隔符分隔多个别名。 */
    private val SPLIT_REGEX = Regex("[/、,，;；|]")

    /**
     * 构造查询串列表（保序、去重、去空）。
     *
     * 顺序即优先级：中文名 → 原名 → infobox 别名 → 纯 ASCII 变体。
     * 调用方应按序查询并在达到足够候选后提前停止（省额度）。
     */
    fun build(subject: SubjectEntity, infobox: List<InfoBoxEntry> = emptyList()): List<String> {
        val out = LinkedHashSet<String>()

        fun add(raw: String?) {
            val v = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return
            // 过长/含换行的值不是标题（infobox 值可能是整段文本）
            if (v.length > 120 || v.contains('\n')) return
            out += v
        }

        add(subject.titleCN)
        add(subject.title)
        aliasValues(infobox).forEach { add(it) }
        romanizationHints(subject).forEach { add(it) }

        return out.toList()
    }

    /** 从 infobox 抠出别名类条目的值（按分隔符拆开）。 */
    fun aliasValues(infobox: List<InfoBoxEntry>): List<String> = infobox
        .filter { entry ->
            val key = entry.key.trim().lowercase()
            ALIAS_KEYS.any { it.lowercase() == key || key.contains(it.lowercase()) }
        }
        .flatMap { it.value.split(SPLIT_REGEX) }
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.length <= 120 }
        .distinct()

    /**
     * 罗马音/ASCII 变体。
     *
     * 本项目没有日文→罗马音的转换库，因此这里只做**保守的**一件事：
     * 若已有纯 ASCII 的标题变体（英文名），它本身就是可用的查询串。
     *
     * 刻意不做「假名转写」——错误转写会产生大量假阳性候选，
     * 反而比少一个查询串更糟（用户宁可看到「没匹配到，请手动搜索」）。
     */
    fun romanizationHints(subject: SubjectEntity): List<String> = buildList {
        val ascii = listOfNotNull(subject.title, subject.titleCN)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.all { ch -> ch.code < 128 } }
        addAll(ascii)
    }

    /**
     * 从 infobox 值里提取 provider 明确 ID（L1）。
     *
     * 支持两种写法：
     * - 纯 id：`v12345`、`42509`、`tt1234567`、`1292052`
     * - URL：`https://vndb.org/v12345`、`themoviedb.org/tv/42509`
     *
     * 返回第一个能匹配上的 id；无法确定时返回 null（**不做猜测**——L1 的全部意义就是零猜测）。
     */
    fun extractInfoboxId(
        infobox: List<InfoBoxEntry>,
        keys: List<String>,
        pattern: Regex?,
    ): String? {
        if (pattern == null || keys.isEmpty()) return null
        for (entry in infobox) {
            val key = entry.key.trim().lowercase()
            if (keys.none { key == it.lowercase() || key.contains(it.lowercase()) }) continue
            pattern.find(entry.value)?.let { match ->
                val id = match.groupValues.getOrNull(1)?.trim()
                if (!id.isNullOrEmpty()) return id
            }
        }
        return null
    }
}

/**
 * 统一匹配编排器（第 4 轮 D 的入口）。
 *
 * 驱动流程（全部 provider 共用同一套口径，杜绝「每个源各写一套」）：
 *
 * 1. **L1 infobox 明确 ID**：命中即返回 `confidence = 1.0` 的候选，零猜测；
 * 2. **L2 多查询串**：按 [MatchQueryBuilder.build] 的顺序逐个查询，
 *    同一 provider 的结果按 externalId 去重合并；
 * 3. **L3 多字段打分**：用 [MatchScorer] 对**每个候选的全部标题集合**打分，
 *    带年份/集数/平台；
 * 4. **L4 手动兜底**：由 UI 调用 [searchByQuery] / [byId]。
 *
 * 编排器**不写库**——保守匹配约定要求写绑定必须由用户确认
 * （infobox 命中虽然零猜测，也由调用方显式决定是否自动写）。
 */
class ExternalMatchService(
    private val matchers: List<ProviderMatcher>,
) {

    /** 已注册的 provider。
     *
     * 命名为 registered 而不是 all，避免与 Kotlin 的 all 扩展在调用点混淆。 */
    val registered: List<ProviderMatcher> get() = matchers

    fun matcher(provider: String): ProviderMatcher? = matchers.firstOrNull { it.provider == provider }

    /**
     * 对某作品跑完整匹配（L1 → L3）。
     *
     * @param maxQueries 最多使用前几个查询串。VNDB 限流 200 次/5 分钟，
     *   全量查询串（含各种别名）可能到 6-8 个，因此调用方通常限制到 3-4 个。
     */
    suspend fun match(
        provider: String,
        subject: SubjectEntity,
        infobox: List<InfoBoxEntry> = emptyList(),
        maxQueries: Int = 4,
        limit: Int = 8,
    ): List<MatchCandidate> {
        val target = matcher(provider) ?: return emptyList()

        // —— L1：infobox 明确 ID（零猜测，直接返回单条） ——
        val infoboxId = MatchQueryBuilder.extractInfoboxId(
            infobox = infobox,
            keys = target.infoboxKeys,
            pattern = target.infoboxIdPattern,
        )
        if (infoboxId != null) {
            val raw = runCatching { target.byId(infoboxId, subject) }.getOrNull()
            if (raw != null) {
                return listOf(
                    raw.toMatchCandidate(
                        confidence = 1f,
                        reasons = listOf("infobox 明确给出 ${target.label} id（$infoboxId）"),
                        queryText = null,
                        source = MatchCandidate.SOURCE_INFOBOX,
                        providerName = target.provider,
                    )
                )
            }
        }

        // —— L2 + L3：多查询串 + 多字段打分 ——
        val queries = MatchQueryBuilder.build(subject, infobox).take(maxQueries)
        if (queries.isEmpty()) return emptyList()

        val merged = LinkedHashMap<String, MatchCandidate>()
        val year = subject.airDate?.take(4)?.toIntOrNull()
        // 第 5 轮 D11：打分目标用**全部已知标题**（含 infobox 别名），与查询串同一份集合。
        // 改造前这里只有 titleCN/title，而别名仅被当作查询串 ——
        // 于是「用别名搜到了正确条目，却因主标题与候选日文主标题不相似而分数不过阈值」，
        // 用户看到的现象就是「搜到了却显示没匹配上」。
        // 注意用的是**未截断**的完整集合：maxQueries 限制的是发出去的请求数，不是打分口径。
        val bangumiTitles = scoringTitles(subject, infobox)

        for (q in queries) {
            val raws = runCatching { target.query(q, subject) }.getOrDefault(emptyList())
            for (raw in raws) {
                if (raw.externalId.isBlank()) continue
                // 同一候选可能被多个查询串命中：保留**置信度最高**的那次，并记下命中它的查询串
                val scored = MatchScorer.score(
                    bangumiTitles = bangumiTitles,
                    candidate = raw,
                    bangumiYear = year,
                    bangumiEpisodes = subject.totalEpisodes,
                    bangumiPlatforms = listOfNotNull(subject.platform),
                )
                val existing = merged[raw.externalId]
                if (existing == null || scored.score > existing.confidence) {
                    merged[raw.externalId] = raw.toMatchCandidate(
                        confidence = scored.score,
                        reasons = scored.reasons,
                        queryText = q,
                        source = MatchCandidate.SOURCE_QUERY,
                        providerName = target.provider,
                    )
                }
            }
            // 已经有一个高置信度命中时提前停止，省掉后续查询的额度
            if (merged.values.any { it.confidence >= MatchScorer.HIGH_CONFIDENCE }) break
        }

        return merged.values.sortedByDescending { it.confidence }.take(limit)
    }

    /**
     * L4 手动兜底之一：用**用户输入的关键词**搜索（不依赖作品标题）。
     *
     * 关键词仍会经过 [MatchScorer] 打分，因此用户能看到「匹配度」，
     * 但**不因为分数低就被过滤掉**——用户自己搜出来的东西，展示出来让他判断。
     */
    suspend fun searchByQuery(
        provider: String,
        query: String,
        subject: SubjectEntity,
        limit: Int = 10,
        /** 可选：手动搜索时也带上 infobox，让别名参与打分（第 5 轮 D11）。 */
        infobox: List<InfoBoxEntry> = emptyList(),
    ): List<MatchCandidate> {
        val target = matcher(provider) ?: return emptyList()
        val text = query.trim()
        if (text.isEmpty()) return emptyList()

        val year = subject.airDate?.take(4)?.toIntOrNull()
        val bangumiTitles = scoringTitles(subject, infobox)

        return runCatching { target.query(text, subject) }.getOrDefault(emptyList())
            .filter { it.externalId.isNotBlank() }
            .map { raw ->
                val scored = MatchScorer.score(
                    bangumiTitles = bangumiTitles,
                    candidate = raw,
                    bangumiYear = year,
                    bangumiEpisodes = subject.totalEpisodes,
                    bangumiPlatforms = listOfNotNull(subject.platform),
                )
                raw.toMatchCandidate(
                    confidence = scored.score,
                    reasons = scored.reasons,
                    queryText = text,
                    source = MatchCandidate.SOURCE_MANUAL,
                    providerName = target.provider,
                )
            }
            .sortedByDescending { it.confidence }
            .take(limit)
    }

    /**
     * L4 手动兜底之二：**直接粘贴 provider ID**。
     *
     * 返回 null 表示该 id 在 provider 侧不存在（UI 应提示用户，而不是静默失败）。
     */
    suspend fun byId(
        provider: String,
        externalId: String,
        subject: SubjectEntity,
    ): MatchCandidate? {
        val target = matcher(provider) ?: return null
        val id = externalId.trim()
        if (id.isEmpty()) return null
        val raw = runCatching { target.byId(id, subject) }.getOrNull() ?: return null
        return raw.toMatchCandidate(
            confidence = 1f,
            reasons = listOf("手动指定 ${target.label} id（$id）"),
            queryText = null,
            source = MatchCandidate.SOURCE_MANUAL,
            providerName = target.provider,
        )
    }

    /**
     * 打分用的「作品名集合」（第 5 轮 D11）。
     *
     * 直接复用查询串的构造器 —— 它返回的本来就是「这部作品的全部名字」：
     * 中文名、原名、infobox 别名/罗马音、纯 ASCII 变体。
     * 两边口径一致，才不会出现「搜得到、算分算不出来」。
     */
    private fun scoringTitles(subject: SubjectEntity, infobox: List<InfoBoxEntry>): List<String> =
        MatchQueryBuilder.build(subject, infobox)
            .ifEmpty { listOfNotNull(subject.titleCN, subject.title).map { it.trim() }.filter { it.isNotEmpty() } }

    private fun RawCandidate.toMatchCandidate(
        confidence: Float,
        reasons: List<String>,
        queryText: String?,
        source: String,
        /** provider 名由调用方注入——[RawCandidate] 本身不携带 provider。 */
        providerName: String,
    ) = MatchCandidate(
        provider = providerName,
        externalId = externalId,
        title = displayTitle.ifBlank { titles.firstOrNull().orEmpty() }.ifBlank { externalId },
        subtitle = subtitle,
        imageUrl = imageUrl,
        confidence = confidence,
        subKey = subKey,
        reasons = reasons,
        matchedQuery = queryText,
        source = source,
    )
}
