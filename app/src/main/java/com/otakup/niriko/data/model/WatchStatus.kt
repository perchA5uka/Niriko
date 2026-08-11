package com.otakup.niriko.data.model

/**
 * 观看/阅读状态。
 * [label] 用于 UI 展示的中文名称。
 */
enum class WatchStatus(val label: String) {
    PLAN_TO_WATCH("想看"),
    WATCHING("在看"),
    COMPLETED("看过"),
    ON_HOLD("搁置"),
    DROPPED("抛弃");

    companion object {
        /** 按中文标签查找，找不到则返回 null。 */
        fun fromLabel(label: String): WatchStatus? =
            entries.find { it.label == label }
    }
}
