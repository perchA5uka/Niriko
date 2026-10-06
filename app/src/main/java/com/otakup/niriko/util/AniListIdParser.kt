package com.otakup.niriko.util

/**
 * AniList 条目 id 解析（纯函数，可 JVM 单测）。
 *
 * 已知需要接受的形态（详情页「粘贴 ID / 链接」与候选点选都会经过这里）：
 * - `media-123`：AniListGameDataSource 的 externalId 前缀，候选绑定的实际入参；
 * - `123`：纯数字 id；
 * - `anime/123`、`https://anilist.co/anime/123`：用户直接粘贴的链接。
 *
 * 取**第一段完整数字**，且该段不能超过 8 位；解析不出正数时返回 null，
 * 由调用方给出可见反馈（历史上绑定入口用 `toLongOrNull()` 解析 `media-123` 恒为 null，
 * 点击没有任何反应）。
 *
 * 为什么用「第一段完整数字」而不是正则 `\d{1,8}`：后者会把 `123456789` 截成
 * `12345678` 当成一个合法 id —— 乱码输入会静默绑到别人的条目上。
 */
object AniListIdParser {

    /** 任意长度的连续数字；由 [parse] 自己判断长度是否合法。 */
    private val DIGIT_RUN = Regex("""\d+""")

    /** AniList id 的位数上限（当前实际 id 远小于这个量级）。 */
    private const val MAX_DIGITS = 8

    fun parse(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val digits = DIGIT_RUN.find(text)?.value ?: return null
        if (digits.length > MAX_DIGITS) return null
        val value = digits.toLongOrNull() ?: return null
        return value.takeIf { it > 0L }
    }
}
