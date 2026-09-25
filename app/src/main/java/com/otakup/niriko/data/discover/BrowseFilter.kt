package com.otakup.niriko.data.discover

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.bangumi.dto.SearchFilterDto
import com.otakup.niriko.data.remote.bangumi.dto.SearchRequestDto
import com.otakup.niriko.data.search.TagAliasMap
import com.otakup.niriko.data.seasonal.SeasonalTrendingCalculator
import com.otakup.niriko.util.PinyinSearch
import com.otakup.niriko.util.TimeUtils
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

/**
 * 「历史排名」的筛选模型（第 6 轮 §6.2/§6.3；替代已被删除的 FindSubjectsFilter）。
 *
 * ## 它替代什么
 *
 * 第 4 轮那套（年份区间 / 季度 / 标签 / 评分 / 排名 / 系列 / NSFW）是照「条件浏览」
 * 自己想的，与 Bangumi-master 的「找条目」（它的 MENU_MAP.Anime）不是一回事。
 * 本轮按它的 9 个维度重做：地区 / 版本 / 年份 / 季度 / 状态 / 类型 / 制作 / 排序 / 收藏，
 * 并**保留我们的三项增强**（评分区间 / 排名区间 / NSFW；规格 §12 已确认）。
 *
 * 本文件只做**纯映射**：筛选条件 → Bangumi v0 请求参数，以及官方不支持的那几个维度的
 * 本地过滤 / 本地排序。不含任何 IO，全部可单测。
 *
 * ## 官方 schema 的硬约束（规格 §1）
 *
 * POST /v0/search/subjects 的 filter **只有** type / meta_tags / tag / air_date /
 * rating / rating_count / rank / nsfw，**没有 series**。第 4 轮发的 filter.series 是
 * 未定义字段，服务端若严格校验会 400 整条拒掉 —— 正是「找条目完全是错的」的一条候选根因。
 * 本轮删除「系列」维度，SearchFilterDto 里也不再声明 series。
 *
 * ## 每个维度落到哪（规格 §6.2 映射表）
 *
 * 地区 → filter.tag；版本 → filter.tag；年份 → filter.air_date 整年窗口（「2000以前」退化为
 * 单边区间，含 2000 整年）；季度 → 年份 + 三个月的精确窗口（10 月正确跨年）；状态 → 未播放走服务端
 * air_date >= today，连载/完结在候选池内本地判定；类型 → filter.tag 多值且（46 词表）；
 * 制作 → filter.tag + TagAliasMap 变体展开后多请求合并；排序 → 官方 rank/date/heat/score，
 * 随机与名称本地实现；收藏 → 本地过滤。
 *
 * 固定取值与 Bangumi-master src/utils/subject/anime/ds.ts 的 ANIME_AREA / ANIME_TYPE /
 * ANIME_YEAR / ANIME_BEGIN / ANIME_STATUS / ANIME_TAGS / ANIME_SORT / ANIME_COLLECTED 对应。
 *
 * @property type 条目类型；null = 不限类型（沿用顶部类型行；不生成 filter.type）。
 * @property area 地区（日本 / 中国）。
 * @property version 版本（TV / 剧场版 / OVA / WEB）。
 * @property year 年份单选；见 [BrowseYear]（含「2000以前」）。
 * @property quarter 季度（1/4/7/10 月）；只在指定了精确年份时生效（单给季度没有确定时间段）。
 * @property status 状态（连载 / 完结 / 未播放）。
 * @property tags 内容标签（AND 语义，多选）；建议只从 [CONTENT_TAGS] 里选。
 * @property studio 制作公司名（单选）；请求时展开别名变体。
 * @property sort 排序。
 * @property hideCollected 收藏维度：隐藏已收藏（本地过滤）。
 * @property minRating 评分下限（0-10，含）。
 * @property maxRating 评分上限（0-10，含）。
 * @property minRatingCount 评分人数下限（含）—— 我们的增强，官方 filter.rating_count 支持。
 * @property maxRatingCount 评分人数上限（含）。
 * @property rankFrom 排名下限（含，1 为最高）。
 * @property rankTo 排名上限（含）。
 * @property nsfw 包含 NSFW（需要 Bangumi token，否则走旧版兜底）。
 */
data class BrowseFilter(
    val type: SubjectType? = null,
    val area: BrowseArea? = null,
    val version: BrowseVersion? = null,
    val year: BrowseYear? = null,
    val quarter: Int? = null,
    val status: BrowseStatus? = null,
    val tags: List<String> = emptyList(),
    val studio: String? = null,
    val sort: BrowseSort = BrowseSort.HEAT,
    val hideCollected: Boolean = false,
    val minRating: Float? = null,
    val maxRating: Float? = null,
    val minRatingCount: Int? = null,
    val maxRatingCount: Int? = null,
    val rankFrom: Int? = null,
    val rankTo: Int? = null,
    val nsfw: Boolean = false,
) {

    /**
     * 是否存在任何非默认条件（UI 用来显示「已筛选」标记）。
     *
     * 刻意**不含** [type] 与 [sort]：类型是顶部工具条的常驻维度、排序永远有值，
     * 把它们算进来会让「已筛选」永远为真。
     */
    val hasAnyCondition: Boolean
        get() = area != null || version != null || year != null || quarter != null ||
            status != null || tags.isNotEmpty() || !studio.isNullOrBlank() ||
            hideCollected || minRating != null || maxRating != null ||
            minRatingCount != null || maxRatingCount != null ||
            rankFrom != null || rankTo != null || nsfw

    /**
     * 状态维度的「按开播日期推算」标注位（规格 §6.2 要求 UI 必须标注）。
     *
     * 非 null 即表示当前有状态筛选，且结果是**推算**的（官方没有放送状态字段）。
     */
    val statusNote: String? get() = if (status != null) STATUS_NOTE else null

    /** 制作维度的「按标签匹配」标注位（规格 §6.2 要求 UI 必须标注）。 */
    val studioNote: String? get() = if (!studio.isNullOrBlank()) STUDIO_NOTE else null

    /** 状态维度是否需要本地再过一遍（未播放走服务端，连载/完结只能本地判定）。 */
    val statusRequiresLocalFilter: Boolean get() = status != null && !status.serverSide

    // ==================== 请求参数映射 ====================

    /**
     * 生成 Bangumi filter.air_date 表达式列表（AND 语义）。
     *
     * 三种来源会叠加（都是「且」）：
     * 1. 年份：精确年份 → 整年窗口；「2000以前」→ 单边 <2001-01-01（含 2000 整年，与
     *    Bangumi-master 的 /^(2000|1\d{3})/ 口径一致；captain 2026-09-25 批准）；
     * 2. 季度：**必须搭配精确年份**，生成该季度的三个月窗口（10 月 → 次年 1 月 1 日）；
     * 3. 状态为「未播放」→ 追加 >=today（官方推荐的服务端过滤，规格 §6.2）。
     *
     * 返回 null 表示不加 air_date（**不能发空数组** —— 空数组在 Bangumi 上等于无结果）。
     * 「2000以前」与季度组合无意义（季度需要确定年份），此时季度被忽略。
     */
    fun airDateRange(today: LocalDate): List<String>? {
        val clauses = ArrayList<String>(3)
        when (val y = year) {
            is BrowseYear.Exact -> {
                val startYear = y.year
                if (quarter != null && quarter in 1..12) {
                    val startMonth = quarter
                    val endMonth = quarter + 3
                    clauses += ">=" + LocalDate.of(startYear, startMonth, 1)
                    clauses += "<" + if (endMonth > 12) {
                        LocalDate.of(startYear + 1, 1, 1)
                    } else {
                        LocalDate.of(startYear, endMonth, 1)
                    }
                } else {
                    clauses += ">=" + LocalDate.of(startYear, 1, 1)
                    clauses += "<" + LocalDate.of(startYear + 1, 1, 1)
                }
            }
            is BrowseYear.Before2000 -> clauses += "<" + BrowseYear.BEFORE_2001_CUTOFF
            null -> Unit // 没有年份：季度无从定位，忽略
        }
        if (status == BrowseStatus.UPCOMING) clauses += ">=$today"
        return clauses.takeIf { it.isNotEmpty() }
    }

    /**
     * 生成 Bangumi filter.rating 区间（0-10，一位小数）。
     *
     * 官方把区间类 filter 设计成「字符串数组 + 运算符前缀」，整数也能工作
     * （实测 >=8 有效），因此整数去掉多余小数位。
     */
    fun ratingRange(): List<String>? = buildList {
        minRating?.let { add(">=" + formatBoundary(it)) }
        maxRating?.let { add("<=" + formatBoundary(it)) }
    }.takeIf { it.isNotEmpty() }

    /**
     * 生成 Bangumi filter.rating_count 区间（**新增，官方支持**，格式与 rating 相同）。
     *
     * 它同时是「质量门槛」在服务端的表达：规格 §3.3 的 v1 规则版就是
     * filter.rating_count = [">=100"]，挡掉「10 分 3 人」。
     */
    fun ratingCountRange(): List<String>? = buildList {
        minRatingCount?.let { add(">=" + it.coerceAtLeast(0)) }
        maxRatingCount?.let { add("<=" + it.coerceAtLeast(0)) }
    }.takeIf { it.isNotEmpty() }

    /** 生成 Bangumi filter.rank 区间（1 为最高，下限夹到 1）。 */
    fun rankRange(): List<String>? = buildList {
        rankFrom?.let { add(">=" + it.coerceAtLeast(1)) }
        rankTo?.let { add("<=" + it.coerceAtLeast(1)) }
    }.takeIf { it.isNotEmpty() }

    /**
     * 与「制作」无关的基础标签子句：内容标签 + 地区 + 版本。
     *
     * filter.tag 是**且**语义，顺序不影响结果，但固定顺序便于单测与日志比对。
     */
    fun baseTagClauses(): List<String> = buildList {
        addAll(tags)
        area?.let { add(it.label) }
        version?.let { add(it.label) }
    }.distinct()

    /**
     * 制作公司的标签变体（TagAliasMap 展开去重，保留输入词本身）。
     *
     * 未配置制作维度时返回空列表。
     */
    fun studioVariants(): List<String> =
        studio?.takeIf { it.isNotBlank() }?.let { TagAliasMap.expand(it) } ?: emptyList()

    /**
     * 单次请求体。
     *
     * @param today 今天（未播放的服务端过滤要用；显式传入，便于单测）。
     * @param studioVariant 本次请求使用的制作公司变体；null = 不加制作标签。
     *   一般不要直接传，用 [toRequests] 展开别名。
     */
    fun toRequest(today: LocalDate, studioVariant: String? = studio): SearchRequestDto {
        val tagClauses = buildList {
            addAll(baseTagClauses())
            studioVariant?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()

        val dto = SearchFilterDto(
            // null = 不限类型 → **不生成该字段**（发空数组等于无结果）
            type = type?.let { listOf(bangumiTypeOf(it)) },
            tag = tagClauses.takeIf { it.isNotEmpty() },
            airDate = airDateRange(today),
            rating = ratingRange(),
            ratingCount = ratingCountRange(),
            rank = rankRange(),
            nsfw = nsfw.takeIf { it },
        )
        return SearchRequestDto(
            // 条件浏览没有关键词；Bangumi 在 filter 给定时会忽略空 keyword
            keyword = "",
            sort = sort.requestSort,
            filter = dto,
        )
    }

    /**
     * 展开成「一个变体一次请求」的请求列表（制作维度需要多请求合并）。
     *
     * 没有制作条件时返回单个请求。
     */
    fun toRequests(today: LocalDate): List<SearchRequestDto> {
        val variants = studioVariants()
        return if (variants.isEmpty()) {
            listOf(toRequest(today, null))
        } else {
            variants.map { toRequest(today, it) }
        }
    }

    // ==================== 本地过滤 / 本地排序 ====================

    /**
     * 官方不支持（或不宜服务端做）的两个维度的本地处理：
     * - 状态为「连载 / 完结」→ 按 [localStatusOf] 本地判定；
     * - 收藏维度「隐藏已收藏」→ 按 collectedIds 本地过滤。
     *
     * 「未播放」走的是服务端 air_date >= today，因此这里**不重复过滤**
     * （本地缺 air_date 的条目不该被误杀）。
     */
    fun applyLocalFilters(
        items: List<SubjectEntity>,
        collectedIds: Set<Long> = emptySet(),
        today: LocalDate,
    ): List<SubjectEntity> {
        var result = items
        if (hideCollected && collectedIds.isNotEmpty()) {
            result = result.filter { it.subjectId !in collectedIds }
        }
        val current = status
        if (current != null && !current.serverSide) {
            result = result.filter { localStatusOf(it, today) == current }
        }
        return result.filter { matchesRatingLocally(it) }
    }

    /**
     * 评分区间 / 评分人数区间的**本地**判定（第 6 轮 §6.3 的已知限制，captain 已裁定本轮接受）。
     *
     * ## 限制是什么
     *
     * 「历史排名」拿到的是一页**候选池**（全部类型：每类 30 条；单类型：30 条），
     * 这两项是在**候选池内**过滤，**不是服务端全量过滤** —— 也就是说，
     * 池子里没有的高分作品不会因为「评分 ≥ 9」而被捞进来，只会把池子里不达标的剔掉。
     * 排序键（默认评分人数）与门槛（DiscoveryFeed 的 rating_total ≥ 100）已经让池子偏向热门，
     * 因此实际观感与「筛出高分作品」一致；但这不是等价实现，**不要把它当成服务端过滤**。
     *
     * ## 为什么不做服务端
     *
     * v0 的搜索通路（SubjectRemoteDataSource）只有 type / tag / air_date / rank / nsfw 形参，
     * **没有 rating 与 rating_count**；扩这两个形参要同时改
     * data/remote/DataSource.kt（接口）+ BangumiDataSource + AniList/DataSourceChain 实现
     * + SubjectRepository（两处 search）+ 本类的请求构建 —— 跨多个已完成任务的 inScope，
     * 发布前夕不动（captain 2026-09-25 裁定）。
     *
     * ## 将来服务端化的改动点
     *
     * 数据源补上 rating / ratingCount 形参后：把 [ratingRange] / [ratingCountRange] 直接塞进请求的
     * SearchFilterDto（字段早已就绪），然后从这里删掉本地判定即可 —— 表达式生成器与它们的单测不用动。
     *
     * 缺字段（null）视为**不满足**（无法证明达标），与 DiscoveryFeed 的质量门槛同口径。
     */
    fun matchesRatingLocally(subject: SubjectEntity): Boolean {
        val min = minRating
        if (min != null && (subject.ratingScore?.takeIf { it > 0f } ?: return false) < min) return false
        val max = maxRating
        if (max != null && (subject.ratingScore?.takeIf { it > 0f } ?: return false) > max) return false
        val minCount = minRatingCount
        if (minCount != null && (subject.ratingTotal?.takeIf { it > 0 } ?: return false) < minCount) {
            return false
        }
        val maxCount = maxRatingCount
        if (maxCount != null && (subject.ratingTotal?.takeIf { it > 0 } ?: return false) > maxCount) {
            return false
        }
        return true
    }

    /**
     * 本地排序（纯函数；RANDOM 用注入的 [random] 保证可测）。
     *
     * 为什么服务端已经排好了还要本地排：
     * - RANDOM / NAME 官方没有对应 sort，只能本地；
     * - 制作维度的多请求合并会打乱服务端顺序，合并后必须按同一排序键重排一次，
     *   否则翻页/追加会把顺序搞乱。
     */
    fun sortLocally(
        items: List<SubjectEntity>,
        random: Random = Random.Default,
    ): List<SubjectEntity> = when (sort) {
        BrowseSort.RANK -> items.sortedWith(
            compareBy<SubjectEntity> { it.rank ?: Int.MAX_VALUE }
                .thenByDescending { it.ratingScore ?: 0f }
                .thenBy { it.subjectId },
        )
        BrowseSort.DATE -> items.sortedWith(
            compareByDescending<SubjectEntity> { it.airDate ?: "" }
                .thenBy { it.subjectId },
        )
        BrowseSort.HEAT -> items.sortedWith(
            compareByDescending<SubjectEntity> { it.ratingTotal ?: 0 }
                .thenByDescending { it.ratingScore ?: 0f }
                .thenBy { it.subjectId },
        )
        BrowseSort.SCORE -> items.sortedWith(
            compareByDescending<SubjectEntity> { it.ratingScore ?: 0f }
                .thenByDescending { it.ratingTotal ?: 0 }
                .thenBy { it.subjectId },
        )
        BrowseSort.NAME -> items.sortedWith(
            compareBy<SubjectEntity> { nameSortKey(it) }
                .thenBy { it.displayTitle }
                .thenBy { it.subjectId },
        )
        BrowseSort.RANDOM -> items.shuffled(random)
    }

    /**
     * 条件摘要文案（保留第 4 轮就有的「筛选摘要行」，规格 §6.3）。
     *
     * 空条件返回空串，由 UI 决定是否隐藏该行。
     */
    fun summary(): String = buildList {
        area?.let { add(it.label) }
        version?.let { add(it.label) }
        year?.let { add(it.label) }
        quarter?.let { add(it.toString() + "月") }
        status?.let { add(it.label) }
        if (tags.isNotEmpty()) add(tags.joinToString("/"))
        studio?.takeIf { it.isNotBlank() }?.let { add(it) }
        intervalText("评分", formatBoundaryOrNull(minRating), formatBoundaryOrNull(maxRating))
            ?.let { add(it) }
        intervalText("评分人数", minRatingCount?.toString(), maxRatingCount?.toString())
            ?.let { add(it) }
        intervalText("排名", rankFrom?.toString(), rankTo?.toString())?.let { add(it) }
        if (hideCollected) add("隐藏已收藏")
        if (nsfw) add("R18")
    }.joinToString(" · ")

    // ==================== 内部工具 ====================

    private fun formatBoundary(value: Float): String =
        if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            String.format(Locale.ROOT, "%.1f", value)
        }

    private fun formatBoundaryOrNull(value: Float?): String? = value?.let { formatBoundary(it) }

    companion object {

        /** 状态维度的固定标注（官方没有放送状态字段，只能按开播日 + 集数推算）。 */
        const val STATUS_NOTE = "按开播日推算"

        /** 制作维度的固定标注（公司名是当标签用的，不是官方字段）。 */
        const val STUDIO_NOTE = "按标签匹配"

        /** 季度候选（Bangumi-master ANIME_BEGIN）。 */
        val QUARTERS: List<Int> = listOf(1, 4, 7, 10)

        /**
         * 内容标签选项：Bangumi-master src/utils/subject/anime/ds.ts 的 ANIME_TAGS（46 个）。
         *
         * 2026-09-03 版；其后的 ANIME_OFFICIAL（约 120 家制作公司）不并入本表 ——
         * 制作维度用 [BrowseFilter.studio] 单值 + [TagAliasMap] 变体展开。
         */
        val CONTENT_TAGS: List<String> = listOf(
            "奇幻", "战斗", "搞笑", "校园", "冒险",
            "科幻", "治愈", "热血", "百合", "爱情",
            "后宫", "励志", "悬疑", "轻小说", "青春",
            "日常", "恋爱", "竞技", "剧情", "泡面番",
            "女性向", "机战", "歌舞", "魔法", "运动",
            "神魔", "萝莉", "战争", "社会", "玄幻",
            "亲子", "历史", "美少女", "职场", "推理",
            "游戏", "犯罪", "耽美", "武侠", "恐怖",
            "欢乐向", "血腥", "吸血鬼", "伪娘", "穿越",
            "偶像",
        )

        /** 地区选项（Bangumi-master ANIME_AREA）。 */
        val AREAS: List<BrowseArea> = BrowseArea.entries.toList()

        /** 版本选项（Bangumi-master ANIME_TYPE）。 */
        val VERSIONS: List<BrowseVersion> = BrowseVersion.entries.toList()

        /** 状态选项（Bangumi-master ANIME_STATUS）。 */
        val STATUSES: List<BrowseStatus> = BrowseStatus.entries.toList()

        /** 排序选项（Bangumi-master ANIME_SORT + 我们的「评分」）。 */
        val SORTS: List<BrowseSort> = BrowseSort.entries.toList()

        /** 收藏选项（Bangumi-master ANIME_COLLECTED，只有「隐藏」一项）。 */
        val COLLECTED_OPTIONS: List<String> = listOf("隐藏")

        /** 年份下拉候选：当前年往前到 2001，再加「2000以前」。 */
        fun yearOptions(nowYear: Int): List<BrowseYear> =
            (maxOf(nowYear, 2001) downTo 2001).map { BrowseYear.Exact(it) } + BrowseYear.Before2000

        /** SubjectType → Bangumi type 整数。 */
        fun bangumiTypeOf(type: SubjectType): Int = when (type) {
            SubjectType.BOOK, SubjectType.MANGA -> 1
            SubjectType.ANIME -> 2
            SubjectType.MUSIC -> 3
            SubjectType.GAME -> 4
            SubjectType.REAL -> 6
            // OTHER / PERSON 在 Bangumi 没有对应类型；退到动画而不是崩
            else -> 2
        }

        /**
         * 状态三态判定（本地推算，规格 §6.2）：
         * - air_date 在未来 → 未播放；
         * - 否则复用第 5 轮的 [SeasonalTrendingCalculator.isProbablyAiring]（已修正
         *   「无总集数的长期连载被误判完结」的坑）→ 在播；
         * - 其余 → 完结。
         *
         * 返回 null = 无法判定（没有合法 air_date），此时不匹配任何状态。
         */
        fun localStatusOf(subject: SubjectEntity, today: LocalDate): BrowseStatus? {
            val airDate = TimeUtils.parseDate(subject.airDate) ?: return null
            return when {
                airDate.isAfter(today) -> BrowseStatus.UPCOMING
                SeasonalTrendingCalculator.isProbablyAiring(subject, today) -> BrowseStatus.AIRING
                else -> BrowseStatus.COMPLETED
            }
        }

        /** 名称排序键：显示名的拼音（Bangumi-master 的「名称」用拼音，这里用全拼 + 原文兜底）。 */
        fun nameSortKey(subject: SubjectEntity): String =
            PinyinSearch.pinyinOf(subject.displayTitle).lowercase(Locale.ROOT)

        /** 区间文案：a–b / ≥a / ≤b。 */
        private fun intervalText(label: String, from: String?, to: String?): String? = when {
            from != null && to != null -> "$label $from–$to"
            from != null -> "$label ≥$from"
            to != null -> "$label ≤$to"
            else -> null
        }
    }
}

/** 地区维度（Bangumi-master ANIME_AREA；值直接当标签用）。 */
enum class BrowseArea(val label: String) {
    JAPAN("日本"),
    CHINA("中国"),
}

/** 版本维度（Bangumi-master ANIME_TYPE；值直接当标签用）。 */
enum class BrowseVersion(val label: String) {
    TV("TV"),
    MOVIE("剧场版"),
    OVA("OVA"),
    WEB("WEB"),
}

/**
 * 状态维度（Bangumi-master ANIME_STATUS）。
 *
 * 官方 v0 没有放送状态字段，因此：
 * - [UPCOMING]（未播放）用服务端 air_date >= today；
 * - [AIRING] / [COMPLETED] 只能在候选池内**本地判定**（见 [BrowseFilter.localStatusOf]）。
 */
enum class BrowseStatus(val label: String) {
    AIRING("连载"),
    COMPLETED("完结"),
    UPCOMING("未播放");

    /** 是否走服务端过滤。 */
    val serverSide: Boolean get() = this == UPCOMING
}

/**
 * 排序维度（Bangumi-master ANIME_SORT + 我们的「评分」）。
 *
 * 官方 sort 枚举只有 match / heat / rank / score，所以「随机」与「名称」只能本地实现。
 */
enum class BrowseSort(val label: String, val apiValue: String?) {
    RANK("排名", "rank"),
    DATE("上映时间", "date"),
    HEAT("评分人数", "heat"),
    SCORE("评分", "score"),
    RANDOM("随机", null),
    NAME("名称", null);

    /** true = 官方没有该排序，必须本地实现。 */
    val isLocalOnly: Boolean get() = apiValue == null

    /**
     * 实际发给服务端的 sort。
     *
     * 本地排序（随机/名称）仍要发一个合法 sort，否则请求会被拒；用 rank 让候选池
     * 落在「有排名的头部」而不是噪声里 —— 本地随机/名称只是在这一池内重排，
     * 这是本数据源下的已知降级（规格 §6.2 的「降级」一列）。
     */
    val requestSort: String get() = apiValue ?: "rank"
}

/**
 * 年份维度（Bangumi-master ANIME_YEAR：2026…2001 + 「2000以前」）。
 *
 * 「2000以前」在官方 API 里退化为**单边** air_date 区间（<2001-01-01，即含 2000 整年）。
 */
sealed interface BrowseYear {

    /** 下拉展示文案，与 Bangumi-master 的文字一致。 */
    val label: String

    /** 精确年份（含）。 */
    data class Exact(val year: Int) : BrowseYear {
        override val label: String get() = year.toString()
    }

    /** 「2000以前」：air_date < 2001-01-01（含 2000 整年）。 */
    data object Before2000 : BrowseYear {
        override val label: String get() = "2000以前"
    }

    companion object {
        /** 「2000以前」的上界（不含）：<2001-01-01，也就是把 2000 整年算进「2000以前」。 */
        const val BEFORE_2001_CUTOFF = "2001-01-01"
    }
}
