package com.otakup.niriko.ui.subject

import com.otakup.niriko.ui.adaptive.NirikoDetailPane

/**
 * 详情页的可配置部件（F06，计划 §9.1）。
 *
 * ## 为什么先要有稳定 ID
 *
 * 详情页的区块原先只有**字符串字面量**做 key（`detailPaneItem("steam", ...)`），
 * 顺序由源码位置决定。要做「隐藏 / 排序」就得先有一份**稳定且可持久化**的 ID 列表：
 * 名字一旦写进用户的设置就不该再改（改了等于把用户的配置清空），
 * 因此 [key] 是显式写死的字符串而不是 `name`。
 *
 * ## 「一个部件」= 用户看得见的那一块
 *
 * 枚举里每一项都对应 itemsBody 里的**一条语句**（可能带自己的 `if` / `when` 守卫）：
 * AniList 的「已绑定详情 / 已绑定待拉取 / 候选」三种状态是**同一个部件**的三个分支，
 * 因此合成了一个 [ANILIST]；VNDB 同理。这样设置页里一行就是用户心里的一块，
 * 而不是「AniList 候选」这种他自己都没法预判何时出现的状态名。
 *
 * 兼容：隐藏集合里若出现旧 key（`anilist_pending` 等，§35 第一批写过），
 * 会按 [legacyAliases] 归并到父部件 —— 用户已经做过的设置不会被丢弃。
 *
 * ## 默认顺序 = 源码顺序
 *
 * 枚举的**声明顺序就是现在的默认顺序**（也就是用户今天看到的顺序）。这是硬约束：
 * 默认值一变，所有没改过设置的用户都会看到版面跳变。[DetailLayoutPolicy.defaultOrder]
 * 直接取 `entries`，并有单测把它逐项钉住。
 *
 * @param key 持久化用的稳定 ID（等于改造前那个字符串 key）
 * @param label 设置页显示名
 * @param pane 宽屏并排两栏时归属哪一列（B2b）
 * @param hideable false = 核心部件，不允许隐藏（§9.1：标题、收藏、返回与保存操作）
 */
enum class DetailSectionId(
    val key: String,
    val label: String,
    val pane: NirikoDetailPane,
    val hideable: Boolean = true,
) {
    COVER("cover", "封面", NirikoDetailPane.OVERVIEW, hideable = false),
    TITLE("title", "标题与评分概览", NirikoDetailPane.OVERVIEW, hideable = false),
    INFOBOX("infobox", "基本信息", NirikoDetailPane.OVERVIEW),
    STEAM("steam", "Steam", NirikoDetailPane.OVERVIEW),
    ANILIST("anilist", "AniList", NirikoDetailPane.OVERVIEW),
    VNDB("vndb", "VNDB", NirikoDetailPane.OVERVIEW),
    RELATIONS("relations", "关联条目", NirikoDetailPane.OVERVIEW),
    STAT_GRID("statGrid", "数据概览格", NirikoDetailPane.OVERVIEW),
    ANITABI("anitabi", "圣地巡礼", NirikoDetailPane.OVERVIEW),
    GUESS("guess", "猜你喜欢", NirikoDetailPane.OVERVIEW),
    RATING("rating", "评分与分布", NirikoDetailPane.CONTENT),
    EXTERNAL_RATING("external_rating", "权威评分", NirikoDetailPane.CONTENT),
    EXTENDED("extended", "扩展信息（标签/简介）", NirikoDetailPane.OVERVIEW),
    THUMBS("thumbs", "剧照与截图", NirikoDetailPane.OVERVIEW),
    EPISODES_RATING("episodes_rating", "分集评分", NirikoDetailPane.CONTENT),
    TMDB_BINDING("tmdb_binding", "TMDb 绑定", NirikoDetailPane.CONTENT),
    TRACKS("tracks", "曲目列表", NirikoDetailPane.CONTENT),
    COLLECTION_BTN("collectionBtn", "收藏操作入口", NirikoDetailPane.OVERVIEW, hideable = false),
    ;

    /** 核心部件（不可隐藏）。 */
    val isCore: Boolean get() = !hideable
}

/**
 * 详情部件可见性与顺序的**纯规则**（F06）。
 *
 * 三条边界（§9.1）：
 * - **未知 ID 忽略**：解析时不认识的 key 一律丢掉，不让历史/手工改过的设置把页面弄坏；
 * - **新部件追加到默认尾部**：以后新增的部件不在用户的顺序列表里，就按默认顺序接在后面
 *   —— 因此升级后新部件一定看得见，不会「消失了」；
 * - **核心部件不可隐藏**：即使被写进设置也不生效（双保险；界面上根本不给开关）。
 */
object DetailLayoutPolicy {

    /** 默认顺序 = 枚举声明顺序（= 改造前的源码顺序）。 */
    val defaultOrder: List<DetailSectionId> get() = DetailSectionId.entries

    /** 允许被隐藏的部件（设置页只列这些）。 */
    val hideable: List<DetailSectionId> get() = defaultOrder.filter { it.hideable }

    /**
     * 旧 key → 当前部件（§35 第一批把 AniList/VNDB 的各状态写成了独立 key）。
     *
     * 只用于**解析**：让用户已经做过的隐藏设置继续生效，而不是升级后集体失效。
     */
    private val legacyAliases: Map<String, DetailSectionId> = mapOf(
        "anilist_pending" to DetailSectionId.ANILIST,
        "anilist_candidates" to DetailSectionId.ANILIST,
        "anilist_source" to DetailSectionId.ANILIST,
        "vndb_pending" to DetailSectionId.VNDB,
        "vndb_candidates" to DetailSectionId.VNDB,
    )

    /** key（含旧 key）→ 部件。大小写与空白由调用方先去干净。 */
    fun fromKey(key: String): DetailSectionId? {
        val clean = key.trim()
        if (clean.isEmpty()) return null
        DetailSectionId.entries.firstOrNull { it.key.equals(clean, ignoreCase = true) }?.let { return it }
        return legacyAliases[clean.lowercase()]
    }

    // ==================== 隐藏 ====================

    /** 编码：按默认顺序输出，保证同一集合永远得到同一个字符串（便于比较与去重）。 */
    fun encodeHidden(hidden: Collection<DetailSectionId>): String =
        defaultOrder.filter { it in hidden && it.hideable }.joinToString(",") { it.key }

    /**
     * 解码：逗号分隔的 key → 隐藏集合。未知 key 忽略；旧 key 归并到父部件；核心部件不生效。
     */
    fun hiddenFromEncoded(encoded: String?): Set<DetailSectionId> {
        val raw = encoded?.takeIf { it.isNotBlank() } ?: return emptySet()
        return raw.split(',')
            .mapNotNull { fromKey(it) }
            .filter { it.hideable }
            .toSet()
    }

    /** 某个部件此刻是否可见：核心部件永远可见，其余看是否在隐藏集合里。 */
    fun isVisible(id: DetailSectionId, hidden: Set<DetailSectionId>): Boolean =
        !id.hideable || id !in hidden

    /** 在设置页里切换一个部件：核心部件不可切换（返回原集合）。 */
    fun toggleHidden(
        hidden: Set<DetailSectionId>,
        id: DetailSectionId,
    ): Set<DetailSectionId> = when {
        !id.hideable -> hidden
        id in hidden -> hidden - id
        else -> hidden + id
    }

    // ==================== 顺序 ====================

    /**
     * 解析用户保存的顺序（F06 第二步）。
     *
     * 规则：认识的 key 按保存顺序保留（重复只取第一次）→ **未知 key 忽略** →
     * **没出现在列表里的部件按默认顺序追加到尾部**。
     * 因此：老版本写的顺序可以用、新版本新增的部件一定出现、乱序/重复/垃圾数据都不会丢部件。
     */
    fun resolveOrder(encoded: String?): List<DetailSectionId> {
        val raw = encoded?.takeIf { it.isNotBlank() } ?: return defaultOrder
        val stored = raw.split(',')
            .mapNotNull { fromKey(it) }
            .distinct()
        val rest = defaultOrder.filterNot { it in stored }
        return stored + rest
    }

    /** 编码顺序：只写非默认位置的项会丢信息，所以**整条顺序都写**（可读、可人工修）。 */
    fun encodeOrder(order: List<DetailSectionId>): String =
        (order.filter { it in defaultOrder } + defaultOrder.filterNot { it in order })
            .distinct()
            .joinToString(",") { it.key }

    /**
     * 把 [id] 在顺序里上移/下移一位（[offset] = -1 / +1），越界时原样返回。
     *
     * 作用在**完整顺序列表**上（先 resolve 再移动），因此不会因为用户的列表不完整而失效。
     */
    fun moveSection(
        encodedOrder: String?,
        id: DetailSectionId,
        offset: Int,
    ): String {
        val current = resolveOrder(encodedOrder)
        val from = current.indexOf(id)
        if (from < 0 || offset == 0) return encodeOrder(current)
        val to = (from + offset).coerceIn(0, current.lastIndex)
        if (to == from) return encodeOrder(current)
        val moved = current.toMutableList().apply { add(to, removeAt(from)) }
        return encodeOrder(moved)
    }

    /** 是否是「还没改过顺序」（用于设置页显示「默认」）。 */
    fun isDefaultOrder(encoded: String?): Boolean = resolveOrder(encoded) == defaultOrder
}
