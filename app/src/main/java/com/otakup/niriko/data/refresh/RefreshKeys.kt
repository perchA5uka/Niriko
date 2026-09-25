package com.otakup.niriko.data.refresh

/**
 * [RefreshCoordinator] 使用的资源 key。
 *
 * 集中定义是为了让「写侧」（Application / ViewModel 触发刷新）与「读侧」
 * （设置页展示上次同步时间）引用同一份字面量，避免拼错导致看起来「从来没同步过」。
 */
object RefreshKeys {
    const val STEAM_CHART = "steam:chart"
    const val AUTO_BIND = "steam:autobind"
    const val AUTO_SYNC_WEBDAV = "autosync:webdav"
    const val AUTO_SYNC_BANGUMI = "autosync:bangumi"
    const val BROADCAST_CALENDAR = "broadcast:calendar"
}
