package com.otakup.niriko.ui.common

/**
 * 作品类型的中文标签（Bangumi type 值）。
 *
 * 原先是 `PersonDetailScreen` / `CharacterDetailScreen` 各自一份 private 拷贝；
 * 参与作品卡（F01）统一到 [com.otakup.niriko.ui.common.CreditSubjectCard] 之后，
 * 这里成为唯一一份 —— 同一类型在两页显示成不同文字是用户能直接看到的错误。
 * 未知类型回退到 "其他"（而不是空串），保证徽标位置永远有内容、卡片高度不塌。
 */
fun subjectTypeLabel(type: Int): String = when (type) {
    1 -> "书籍"
    2 -> "动画"
    3 -> "音乐"
    4 -> "游戏"
    5 -> "三次元"
    6 -> "三次元"
    else -> "其他"
}
