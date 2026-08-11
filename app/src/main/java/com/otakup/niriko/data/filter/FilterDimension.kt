package com.otakup.niriko.data.filter

/**
 * 筛选维度定义。
 * 每个维度对应一行筛选 UI（标题 + 选项列表）。
 *
 * @property title 展示给用户的名称，如「地区」「版本」
 * @property key   维度标识符，如 "region"、"format"
 * @property mode  单选或可多选
 * @property options 可选值列表
 */
data class FilterDimension(
    val title: String,
    val key: String,
    val mode: SelectMode,
    val options: List<FilterOption>,
)

enum class SelectMode {
    /** 单选：选中新选项时清除同组其他选项。 */
    SINGLE,
    /** 多选：多个选项可同时选中，叠加后传入 API。 */
    MULTI,
}

/**
 * 筛选选项。
 *
 * @property label UI 显示文本，如「日本」「TV」
 * @property apiQueryValue 传给 Bangumi search API 的值（用于 tag / air_date 等参数）
 * @property apiParamKey 对应 API 参数名，默认 "tag"
 * @property apiExtraParams 额外参数，如 airDate 范围、rank 范围
 */
data class FilterOption(
    val label: String,
    val apiQueryValue: String,
    /** 此选项对应的 API 参数名。默认 "tag"（传给 SearchFilterDto.tag）。 */
    val apiParamKey: String = "tag",
    /** 额外参数，key→value 对，会合并到搜索参数中。例如 airDate=[">=2026-01-01", "<2026-04-01"]。 */
    val apiExtraParams: Map<String, List<String>> = emptyMap(),
)

/**
 * 可扩展的筛选维度加载器接口。
 * 默认使用预设数据；未来可实现远程加载版本。
 */
interface FilterDimensionLoader {
    fun getDimensions(type: com.otakup.niriko.data.model.SubjectType?): List<FilterDimension>
}
