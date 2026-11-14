package com.otakup.niriko.ui.theme

import androidx.compose.ui.graphics.Color

// 强调色令牌（V-02：改造前金色写了 3 处、日历四色写了 2 处、详情图标色写了 3 处）。
//
// 这些颜色不是主题色：它们表达「语义」（琥珀=评分、蓝=集数、红=收藏、金菱=放送），
// 因此不能从 MaterialTheme 派生，只能集中一处定义。改造前它们是散落在
// Badges / GlassControls / DetailStatCard / CalendarCard / CalendarDaySheet 里的 0x 字面量，
// 同一个语义改一处忘一处就会分叉（金色 0xFFF59E0B 已经写了 3 遍，日历四色写了 2 遍）。
//
// **本批只做等值搬迁（零像素变化）**：每个值都能在改造前的字面量里逐一对应，
// 不做任何取色调整；AccentLiteralPolicyTest 会锁死「同一个 0x 只允许在令牌文件出现一次」。

/** 跨页面复用的强调色（徽标、评分、详情统计卡图标）。 */
object NirikoAccents {
    /** 评分 ★ / 满分确认 / 在看状态：琥珀。 */
    val RatingGold = Color(0xFFF59E0B)

    /** 集数徽标 / 总集数图标：蓝。 */
    val EpisodeBlue = Color(0xFF3B82F6)

    /** 收藏心：红。 */
    val FavoriteRed = Color(0xFFDC2626)

    /** 评分人数图标：品牌绿（与主题 palette 的 GreenPrimaryLight 同值，但语义独立）。 */
    val PeopleGreen = Color(0xFF2E7D32)

    /** 状态徽标：看过（绿）。 */
    val StatusCompleted = Color(0xFF22C55E)

    /** 状态徽标：想看的 / 抛弃的（中性灰）。 */
    val StatusNeutral = Color(0xFF9CA3AF)

    /** 状态徽标：搁置（紫）。 */
    val StatusOnHold = Color(0xFF8B5CF6)
}

/**
 * 日历/放送标记的四色。
 *
 * 这四个值原先同时写在 `CalendarCard.kt`（棋盘格角标与菱形三角）与
 * `CalendarDaySheet.kt`（当日列表的状态文字）里，两处各写一遍 —— 只要有人改单边，
 * 同一天在两处就会显示成不同颜色。现在只有这里一处。
 */
object CalendarAccents {
    /** 放送中 / 放送标记（金色菱形 ◆）。 */
    val Broadcast = Color(0xFFFDD835)

    /** 即将发售 / 发售标记（绿三角 ▲）。 */
    val Release = Color(0xFF66BB6A)

    /** 已完结 / 未知 / 停播（灰）。 */
    val Hiatus = Color(0xFF90A4AE)

    /** 延期（橙）。 */
    val Delay = Color(0xFFFB8C00)
}
