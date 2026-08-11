package com.otakup.niriko.data.remote.game

/**
 * 游戏时长单位换算（存储统一分钟、UI 输入/展示小时）。
 *
 * 背景：Steam 导入写分钟（playtime_forever），详情页编辑按小时输入，
 * 收藏卡按分钟显示——历史单位混乱。统一约定：watchedEpisodes 对 GAME 类型
 * 恒存分钟，所有 UI 边界经此换算。
 */
object PlaytimeConverter {

    /** 分钟 → 小时（UI 输入框展示；整除取整）。 */
    fun minutesToHours(minutes: Int): Int = minutes / 60

    /** 小时 → 分钟（UI 输入 → 存储；null/负值返回 null）。 */
    fun hoursToMinutes(hours: Int?): Int? = hours?.takeIf { it >= 0 }?.times(60)

    /** 分钟 → 可读字符串（"2h30m" / "45m" / "3h"）。 */
    fun format(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}
