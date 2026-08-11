package com.otakup.niriko.data.model

/**
 * 作品类型（基于 Bangumi Subject 分类）。
 * [label] 用于 UI 展示的中文名称。
 */
enum class SubjectType(val label: String) {
    ANIME("动画"),
    MANGA("漫画"),
    BOOK("书籍"),
    GAME("电子游戏"),
    MUSIC("音乐"),
    REAL("三次元"),
    PERSON("人物"),
    OTHER("其他");

    companion object {
        /** 按中文标签查找，找不到则返回 null。 */
        fun fromLabel(label: String): SubjectType? =
            entries.find { it.label == label }

        /** 从 Bangumi API type 整数映射。 */
        fun fromBangumiType(bangumiType: Int): SubjectType = when (bangumiType) {
            1 -> BOOK
            2 -> ANIME
            3 -> MUSIC
            4 -> GAME
            6 -> REAL
            else -> OTHER
        }
    }
}