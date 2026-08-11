package com.otakup.niriko.util

/**
 * 观看状态建议工具。
 *
 * 根据观看进度推荐合适的 WatchStatus。
 * 仅提供建议，不自动修改用户状态。
 */
object WatchStatusHelper {

    /**
     * 根据观看进度建议 WatchStatus。
     *
     * @param watchedEpisodes 已观看集数，null 表示未记录
     * @param totalEpisodes 总集数，null 表示未知
     * @return 建议的 WatchStatus，null 表示不提供建议
     */
    fun suggestStatus(
        watchedEpisodes: Int?,
        totalEpisodes: Int?,
    ): com.otakup.niriko.data.model.WatchStatus? {
        if (watchedEpisodes == null || watchedEpisodes == 0) return null
        if (totalEpisodes == null || totalEpisodes <= 0) return null

        if (watchedEpisodes >= totalEpisodes) {
            return com.otakup.niriko.data.model.WatchStatus.COMPLETED
        }
        if (watchedEpisodes > 0) {
            return com.otakup.niriko.data.model.WatchStatus.WATCHING
        }
        return null
    }
}
