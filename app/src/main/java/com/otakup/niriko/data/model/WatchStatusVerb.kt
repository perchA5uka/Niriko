package com.otakup.niriko.data.model

/**
 * 按作品类型映射状态动词（阶段 B）：动画/三次元=看，书籍/漫画=读，游戏=玩，音乐=听。
 * 参考 Bangumi-master 的 collect(subjectId, typeCn) 动词替换。
 */
fun WatchStatus.verbFor(type: SubjectType): String = when (this) {
    WatchStatus.PLAN_TO_WATCH -> when (type) {
        SubjectType.MANGA, SubjectType.BOOK -> "想读"
        SubjectType.GAME -> "想玩"
        SubjectType.MUSIC -> "想听"
        else -> "想看"
    }
    WatchStatus.WATCHING -> when (type) {
        SubjectType.MANGA, SubjectType.BOOK -> "在读"
        SubjectType.GAME -> "在玩"
        SubjectType.MUSIC -> "在听"
        else -> "在看"
    }
    WatchStatus.COMPLETED -> when (type) {
        SubjectType.MANGA, SubjectType.BOOK -> "读过"
        SubjectType.GAME -> "玩过"
        SubjectType.MUSIC -> "听过"
        else -> "看过"
    }
    WatchStatus.ON_HOLD -> "搁置"
    WatchStatus.DROPPED -> "抛弃"
}
