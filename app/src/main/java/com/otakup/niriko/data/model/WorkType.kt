package com.otakup.niriko.data.model

/**
 * 作品类型。
 * [label] 用于 UI 展示的中文名称。
 */
enum class WorkType(val label: String) {
    ANIME("动画"),
    MANGA("漫画"),
    LIGHT_NOVEL("轻小说"),
    OTHER("其他");

    companion object {
        /** 按中文标签查找，找不到则返回 null。 */
        fun fromLabel(label: String): WorkType? =
            entries.find { it.label == label }
    }
}
