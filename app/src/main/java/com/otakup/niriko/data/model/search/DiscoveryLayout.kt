package com.otakup.niriko.data.model.search

/**
 * 发现页的三种布局（第 5 轮 D28 的两种 + 第 3 轮参考计划 §3.1 新增 [POSTER]）。
 *
 * 用户要求「分出现在的卡片形态和模仿 bangumi-master 的宫格版」——
 * 即两种形态**共存、可切换**，而不是用宫格取代卡片。
 *
 * - [CARD]：现在的形态。顶栏分类标签 + 全屏列表（当季热门 / 历史排名 / Steam；
 *   第 6 轮删除了「找条目」与「评分月刊」两个入口）。
 * - [GRID]：对齐 Bangumi-master 的发现页首页 —— 菜单宫格 + 下方的横向预览，
 *   点宫格项进入对应的全屏列表。
 * - [POSTER]：封面优先的无容器网格（第 3 轮参考计划 §3，形态参考 open-ani/animeko 的探索页
 *   海报网格）—— 2:3 封面 + 图下两行标题 + 评分/收藏角标，只摆封面不摆文案。
 *
 * 切换按钮按**下一个**布局三态循环（卡片 → 宫格 → 海报 → 卡片）。
 * 选择持久化到 DataStore（「AppSettings.discoveryLayout」）：key 不变，老存档值「GRID」照旧兼容。
 */
enum class DiscoveryLayout(val label: String, val key: String) {
    CARD("卡片", "CARD"),
    GRID("宫格", "GRID"),
    POSTER("海报", "POSTER");

    /** 下一个布局（切换按钮用）：卡片 → 宫格 → 海报 → 卡片。 */
    val toggled: DiscoveryLayout get() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** 从持久化的字符串还原；无法识别时回到 [CARD]（绝不因为脏数据崩）。 */
        fun fromKey(raw: String?): DiscoveryLayout =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: CARD
    }
}
