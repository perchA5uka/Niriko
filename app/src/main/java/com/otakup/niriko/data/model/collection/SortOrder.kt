package com.otakup.niriko.data.model.collection

/** 排序方式。 */
enum class SortOrder(val label: String) {
    UPDATE_TIME("最近修改"),
    CREATE_TIME("添加时间"),
    MY_RATING("评分最高"),
    TITLE("标题排序"),
    WATCH_PROGRESS("观看进度"),
}
