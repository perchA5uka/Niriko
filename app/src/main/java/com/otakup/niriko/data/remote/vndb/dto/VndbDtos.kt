package com.otakup.niriko.data.remote.vndb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * VNDB API v2 (Kana) 查询请求体（POST /kana/vn）。
 *
 * ```json
 * {"filters": ["search", "=", "title"], "fields": "...", "results": 10, "sort": "searchrank"}
 * ```
 * 详见官方文档 api-kana.md；无需 API key，限流约 200 次/5 分钟。
 * filters 是混合类型嵌套数组（字符串/数字/数组），用 JsonElement 直接承载避免类型丢失。
 */
@Serializable
data class VndbQueryRequest(
    /** 过滤条件，如 ["search","=","标题"] 或 ["id","=","v123"]。 */
    val filters: JsonElement = JsonArray(emptyList()),
    /** 逗号分隔字段列表（嵌套用点号或花括号）。 */
    val fields: String = "",
    /** 排序字段。 */
    val sort: String = "searchrank",
    /** 是否降序。 */
    val reverse: Boolean = false,
    /** 每页结果数（max 100）。 */
    val results: Int = 10,
    /** 页码（1 起）。 */
    val page: Int = 1,
)

/** VNDB 查询响应。 */
@Serializable
data class VndbQueryResponse(
    val results: List<VndbVisualNovelDto> = emptyList(),
    /** 是否还有更多页。 */
    val more: Boolean = false,
)

/** VNDB 视觉小说条目。 */
@Serializable
data class VndbVisualNovelDto(
    /** vndbid，如 "v17"。 */
    val id: String = "",
    /** 主标题。 */
    val title: String = "",
    /** 副标题/别名（无则 null）。 */
    val alttitle: String? = null,
    /** 多语言标题列表。 */
    val titles: List<VndbTitleDto> = emptyList(),
    /** 中文标题（从 titles 提取 zh/zh-Hans，方便中文用户）。 */
    val ctitle: String? = null,
    /** 简介（HTML 或纯文本，可能很长）。 */
    val description: String? = null,
    /** 开发商。 */
    val developers: List<VndbProducerDto> = emptyList(),
    /** 标签。 */
    val tags: List<VndbTagDto> = emptyList(),
    /** 发售日期 "YYYY-MM-DD" / "YYYY-MM" / "YYYY" / "TBA"。 */
    val released: String? = null,
    /** 评分 0-100（整数）。 */
    val rating: Int? = null,
    /** 评分人数。 */
    val votecount: Int? = null,
    /** 封面图。 */
    val image: VndbImageDto? = null,
    /** 时长档位（1 极短 ~ 5 极长）。 */
    val length: Int? = null,
    /** 平均时长（分钟）。 */
    @SerialName("length_minutes")
    val lengthMinutes: Int? = null,
    /** 平台列表（如 ["win","lin"]）。 */
    val platforms: List<String> = emptyList(),
    /** 原语种。 */
    val olang: String? = null,
    /** 可用语言。 */
    val languages: List<String> = emptyList(),
    /** 截图。 */
    val screenshots: List<VndbScreenshotDto> = emptyList(),

    // ===== 第 4 轮 E：VNDB 差异化字段（Bangumi 没有的） =====

    /**
     * 人气（VNDB 的 popularity，0-100 的相对值）。
     *
     * 与 [rating] 的区别：[rating] 是**贝叶斯加权**后的质量分（少票条目会被拉向均值），
     * popularity 只反映「多少人标记过」。两个一起看才能区分「冷门佳作」与「热门平庸」。
     */
    val popularity: Double? = null,

    /**
     * 关联作品（前作 / 续作 / 同世界观 / 重制…）。
     *
     * 这是用户点名要的字段：《魔法少女的魔女审判》匹配不上，
     * 但它的同世界观作匹配得上——有了 relations，就能从已匹配的条目**反查**到未匹配的。
     */
    val relations: List<VndbRelationDto> = emptyList(),
)

/**
 * VNDB 关联作品。
 *
 * @param relation 关系类型：seq / preq / set / alt / char / side / par / ser / fan / orig
 */
@Serializable
data class VndbRelationDto(
    val id: String = "",
    val relation: String = "",
    /** 是否官方关系（false 为同人）。 */
    @SerialName("relation_official")
    val relationOfficial: Boolean = false,
    /**
     * VNDB 新版可能内联返回对方标题；旧版没有此字段时由调用方二次查询补齐。
     * 两种都要能处理（否则关系列表会全是裸 id）。
     */
    val title: String? = null,
)

/** VNDB 多语言标题。 */
@Serializable
data class VndbTitleDto(
    val lang: String = "",
    val title: String = "",
    val latin: String? = null,
    val official: Boolean = false,
    val main: Boolean = false,
)

/** VNDB 制作商（开发商）。 */
@Serializable
data class VndbProducerDto(
    val id: String = "",
    val name: String = "",
    val original: String? = null,
)

/** VNDB 标签。 */
@Serializable
data class VndbTagDto(
    val id: String = "",
    val name: String = "",
    /** 标签类别：content / sexual / tech。 */
    val category: String? = null,
    /** 标签适用度 0-3。 */
    val rating: Double? = null,
    val spoiler: Int? = null,
)

/** VNDB 图片（封面/截图共用结构）。 */
@Serializable
data class VndbImageDto(
    val id: String = "",
    val url: String = "",
    val thumbnail: String? = null,
    /** [宽, 高]。 */
    val dims: List<Int> = emptyList(),
    @SerialName("thumbnail_dims")
    val thumbnailDims: List<Int>? = null,
)

/** VNDB 截图。 */
@Serializable
data class VndbScreenshotDto(
    val id: String = "",
    val url: String = "",
    val thumbnail: String? = null,
    val dims: List<Int> = emptyList(),
    @SerialName("thumbnail_dims")
    val thumbnailDims: List<Int>? = null,
)
