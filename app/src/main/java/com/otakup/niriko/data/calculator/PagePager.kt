package com.otakup.niriko.data.calculator

/**
 * 分页推进规则（**纯函数，可单测**）。
 *
 * 背景（B10）：过去月份放送缺失的直接原因是「6 个月开播窗口 + 单页 100 条」——
 * 一个月度缓存要覆盖 [目标月 −6 个月, 目标月末) 开播的作品，一个季度就有上百个条目，
 * 而请求只取一页、也没有 offset 推进，于是目标月（窗口末尾）的条目被整段截掉。
 * 表现在 UI 上就是：翻到历史月份，日历一片空白。
 *
 * 这里只做一件事：告诉调用方**要不要再取下一页、用哪个 offset**。
 */
object PagePager {

    /**
     * 单次加载最多累计多少条（跨客户端安全上限）。
     *
     * 一个 7 个月窗口在 Bangumi 上量级是几百条，1000 足以覆盖长连载季度；
     * 同时避免服务端 total 异常（例如恒为极大值）时无限翻页。
     */
    const val MAX_ITEMS = 1000

    /**
     * @param loaded 已经累计拿到的**原始**条数（不是去重后的条数，否则翻页会原地打转）
     * @param total 服务端报告的 total；<= 0 表示服务端没给（此时靠「空页」收敛）
     * @return 下一页的 offset；null 表示已经取完
     */
    fun nextOffset(loaded: Int, total: Int): Int? {
        if (loaded <= 0) return null
        if (loaded >= MAX_ITEMS) return null
        if (total > 0 && loaded >= total) return null
        return loaded
    }
}
