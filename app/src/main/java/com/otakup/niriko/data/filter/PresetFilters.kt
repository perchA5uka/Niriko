package com.otakup.niriko.data.filter

import com.otakup.niriko.data.model.SubjectType

/**
 * 按条目类型的预设筛选维度。
 * 硬编码常量，结构保持可扩展（实现 FilterDimensionLoader 接口）。
 * 参考 Bangumi-master 的 FILTER_DS 设计。
 */
object PresetFilterLoader : FilterDimensionLoader {

    override fun getDimensions(type: SubjectType?): List<FilterDimension> {
        return when (type) {
            SubjectType.ANIME -> animeFilters()
            SubjectType.REAL -> realFilters()
            SubjectType.BOOK, SubjectType.MANGA -> bookFilters()
            SubjectType.GAME -> gameFilters()
            SubjectType.MUSIC -> musicFilters()
            null -> defaultFilters()
            SubjectType.PERSON, SubjectType.OTHER -> emptyList()
        }
    }

    private fun animeFilters() = listOf(
        FilterDimension(
            title = "地区",
            key = "region",
            mode = SelectMode.SINGLE,
            options = listOf(
                FilterOption("日本", "日本"),
                FilterOption("中国", "中国"),
            ),
        ),
        FilterDimension(
            title = "版本",
            key = "format",
            mode = SelectMode.SINGLE,
            options = listOf(
                FilterOption("TV", "TV"),
                FilterOption("剧场版", "剧场版"),
                FilterOption("OVA", "OVA"),
                FilterOption("WEB", "WEB"),
            ),
        ),
        FilterDimension(
            title = "类型",
            key = "anime_tag",
            mode = SelectMode.MULTI,
            options = listOf(
                FilterOption("原创", "原创"),
                FilterOption("漫画改", "漫画改"),
                FilterOption("轻小说改", "轻小说改"),
                FilterOption("游戏改", "游戏改"),
                FilterOption("战斗", "战斗"),
                FilterOption("搞笑", "搞笑"),
                FilterOption("校园", "校园"),
                FilterOption("冒险", "冒险"),
                FilterOption("科幻", "科幻"),
                FilterOption("奇幻", "奇幻"),
                FilterOption("治愈", "治愈"),
                FilterOption("热血", "热血"),
                FilterOption("日常", "日常"),
                FilterOption("恋爱", "恋爱"),
                FilterOption("百合", "百合"),
                FilterOption("机战", "机战"),
                FilterOption("悬疑", "悬疑"),
                FilterOption("泡面番", "泡面番"),
                FilterOption("运动", "运动"),
                FilterOption("音乐", "音乐"),
            ),
        ),
    )

    private fun bookFilters() = listOf(
        FilterDimension(
            title = "类型",
            key = "book_type",
            mode = SelectMode.SINGLE,
            options = listOf(
                FilterOption("小说", "小说"),
                FilterOption("漫画", "漫画"),
                FilterOption("画集", "画集"),
                FilterOption("设定集", "设定集"),
            ),
        ),
        FilterDimension(
            title = "标签",
            key = "book_tag",
            mode = SelectMode.MULTI,
            options = listOf(
                FilterOption("奇幻", "奇幻"),
                FilterOption("科幻", "科幻"),
                FilterOption("推理", "推理"),
                FilterOption("恋爱", "恋爱"),
                FilterOption("历史", "历史"),
                FilterOption("恐怖", "恐怖"),
            ),
        ),
    )

    private fun gameFilters() = listOf(
        FilterDimension(
            title = "平台",
            key = "platform",
            mode = SelectMode.SINGLE,
            options = listOf(
                FilterOption("PC", "PC"),
                FilterOption("PlayStation", "PlayStation"),
                FilterOption("Nintendo Switch", "Nintendo Switch"),
                FilterOption("Xbox", "Xbox"),
                FilterOption("iOS/Android", "iOS"),
                FilterOption("街机", "街机"),
            ),
        ),
        FilterDimension(
            title = "类型",
            key = "game_type",
            mode = SelectMode.SINGLE,
            options = listOf(
                FilterOption("RPG", "RPG"),
                FilterOption("ACT", "ACT"),
                FilterOption("AVG", "AVG"),
                FilterOption("SLG", "SLG"),
                FilterOption("ADV", "ADV"),
                FilterOption("FPS", "FPS"),
            ),
        ),
    )

    private fun musicFilters() = listOf(
        FilterDimension(
            title = "类型",
            key = "music_type",
            mode = SelectMode.MULTI,
            options = listOf(
                FilterOption("动画歌曲", "动画歌曲"),
                FilterOption("JPOP", "JPOP"),
                FilterOption("声优", "声优"),
                FilterOption("同人音乐", "同人音乐"),
                FilterOption("OST", "OST"),
            ),
        ),
    )

    private fun realFilters() = listOf(
        FilterDimension(
            title = "地区",
            key = "real_region",
            mode = SelectMode.SINGLE,
            options = listOf(
                FilterOption("日本", "日本"),
                FilterOption("中国", "中国"),
                FilterOption("韩国", "韩国"),
                FilterOption("欧美", "欧美"),
            ),
        ),
        FilterDimension(
            title = "类型",
            key = "real_type",
            mode = SelectMode.SINGLE,
            options = listOf(
                FilterOption("电视剧", "电视剧"),
                FilterOption("电影", "电影"),
                FilterOption("综艺", "综艺"),
                FilterOption("纪录片", "纪录片"),
            ),
        ),
    )

    private fun defaultFilters() = listOf(
        FilterDimension(
            title = "类型",
            key = "default_tag",
            mode = SelectMode.MULTI,
            options = listOf(
                FilterOption("原创", "原创"),
                FilterOption("战斗", "战斗"),
                FilterOption("搞笑", "搞笑"),
                FilterOption("校园", "校园"),
                FilterOption("冒险", "冒险"),
                FilterOption("科幻", "科幻"),
                FilterOption("奇幻", "奇幻"),
                FilterOption("治愈", "治愈"),
                FilterOption("日常", "日常"),
                FilterOption("恋爱", "恋爱"),
                FilterOption("推理", "推理"),
                FilterOption("百合", "百合"),
            ),
        ),
    )
}
