package com.otakup.niriko.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle

val LocalDetailNavigationState = staticCompositionLocalOf<SavedStateHandle?> { null }
val LocalDetailHeroKey = staticCompositionLocalOf<String?> { null }
val LocalDetailSubjectId = staticCompositionLocalOf { 0L }

object SubjectCoverHandoff {
    private var pending: Pair<Long, String>? = null
    @Synchronized fun prepare(id: Long, key: String) { pending = id to key }
    @Synchronized fun consume(id: Long): String? {
        val result = pending?.takeIf { it.first == id }?.second
        pending = null
        return result
    }
}

fun relationCoverKey(parentId: Long, subjectId: Long, index: Int): String = "cover_" + subjectId + ":relation:" + parentId + ":" + index

/**
 * 「猜你喜欢」横轨的共享键。与 [relationCoverKey] 同形，只把来源段换成 recommendation ——
 * 关联作品与推荐可能命中同一 subject，但属于不同的一格，键必须区分。
 *
 * 抽成具名函数是为了让「源卡键 = 目标页 hero 键」这条链路能在 JVM 单测里钉住，
 * 而不是散落在 composable 里的字符串拼接。
 */
fun recommendationCoverKey(parentId: Long, subjectId: Long, index: Int): String = "cover_" + subjectId + ":recommendation:" + parentId + ":" + index
