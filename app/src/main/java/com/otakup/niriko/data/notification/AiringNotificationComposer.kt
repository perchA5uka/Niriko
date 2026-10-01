package com.otakup.niriko.data.notification

/** 一条「今日放送」候选（Worker 收集后交给 [AiringNotificationComposer]）。 */
data class AiringNotificationItem(
    val subjectId: Long,
    val title: String,
    val airTimeMinutes: Int?,
)

/** 一条通知的最终内容。 */
data class AiringNotificationContent(
    val title: String,
    val text: String,
    /** 单条时指向的作品 id；多条合并时为 null（点击落到作品库「在看」列表）。 */
    val subjectId: Long?,
    val merged: Boolean,
)

/**
 * 放送提醒通知文案与合并（计划 B1-3）的纯逻辑。
 *
 * 用户确认：同一天多条**合并为一条**；**不做**免打扰时段。
 * - 1 条 → 「《作品名》今日更新」+（有精确时刻时）「今日 HH:mm 放送，记得观看」
 * - 2 条 → 「《A》《B》今日更新」
 * - ≥3 条 → 「《A》《B》等 N 部今日更新」
 * - 合并标题超过 [MERGED_TITLE_LIMIT] 字 → 降级为「N 部作品今日更新」（避免通知栏标题被截断）
 *
 * 点击落点：单条 → 作品详情页；合并 → 作品库「在看」列表（见 MainActivity / AiringListFilterRequest）。
 */
object AiringNotificationComposer {

    /** 合并通知的固定通知 id：同一天重复触发时覆盖同一条，不堆叠。 */
    const val MERGED_NOTIFICATION_ID = 1001

    /** 合并标题长度上限（按字符数，含书名号）。 */
    const val MERGED_TITLE_LIMIT = 48

    /** 生成通知内容；[items] 为空返回 null（不打扰）。 */
    fun compose(items: List<AiringNotificationItem>): AiringNotificationContent? {
        if (items.isEmpty()) return null

        if (items.size == 1) {
            val only = items.first()
            val text = only.airTimeMinutes
                ?.let { "今日 ${formatMinutes(it)} 放送，记得观看" }
                ?: "今日放送，记得观看"
            return AiringNotificationContent(
                title = "《${only.title}》今日更新",
                text = text,
                subjectId = only.subjectId,
                merged = false,
            )
        }

        val quoted = items.map { "《${it.title}》" }
        val head = quoted.take(2).joinToString("")
        // 与原文案一致：书名号紧接「今日更新」，中间不留空格
        val candidate = if (items.size == 2) {
            "${head}今日更新"
        } else {
            "${head}等 ${items.size} 部今日更新"
        }
        val title = if (candidate.length <= MERGED_TITLE_LIMIT) {
            candidate
        } else {
            "${items.size} 部作品今日更新"
        }
        return AiringNotificationContent(
            title = title,
            text = "今日放送，记得观看",
            subjectId = null,
            merged = true,
        )
    }

    /** 分钟（0-1439）→ "HH:mm"（与详情页 / 日历同一格式）。 */
    fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
