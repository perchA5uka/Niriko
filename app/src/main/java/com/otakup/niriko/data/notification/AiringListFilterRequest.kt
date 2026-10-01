package com.otakup.niriko.data.notification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 合并放送通知点击后的待处理请求（计划 B1-3）。
 *
 * 与 ThemePackImportRequest 同一套路（Activity 写、页面消费后清空），但用 Compose 可观察状态：
 * 冷启动与「应用已在后台」两种情况下，作品库页都能收到并切到「在看」筛选。
 */
object AiringListFilterRequest {

    /** 是否有待消费的「切到在看列表」请求。 */
    var pending: Boolean by mutableStateOf(false)
}
