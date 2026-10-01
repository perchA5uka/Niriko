package com.otakup.niriko.data.remote.douban

/**
 * 豆瓣剧照「防剧透」取图策略（计划 B4 · 4-13，用户选择：**用户开关**）。
 *
 * 规则：开关打开时跳过第一页（第一页集中了开播前的宣传图与早期剧情图，
 * 对还没看到那里的人最容易剧透），从第二页起取；开关关闭时照常从 0 开始。
 *
 * 只提供起点计算，**不做网络回退**——回退在调用方（防剧透取不到图时退回 0，
 * 不能因为开了防剧透就把剧照功能弄没）。
 */
object DoubanSpoilerPolicy {

    /**
     * 剧照请求起点。
     * @param antiSpoiler 用户开关
     * @param pageSize 一页的张数（与请求的 count 一致）
     */
    fun photoStart(antiSpoiler: Boolean, pageSize: Int): Int =
        if (antiSpoiler) pageSize.coerceAtLeast(0) else 0
}
