package com.otakup.niriko.data.diagnose

/**
 * 体检所需的只读计数（C-1）。
 *
 * 为什么不把 DAO 传进 ViewModel：计数是「只读、可失败、可由宿主替换」的能力，
 * 用函数式接口注入后，ViewModel 仍然可以纯 JVM 构造（默认 null 时体检跳过计数类检查），
 * 也不必为测试去 mock 整张 Room 数据库。
 */
data class CollectionCounts(
    val total: Int = 0,
    /** 两种进度（集数/卷数）都为空。 */
    val withoutProgress: Int = 0,
    /** 未打分（rating 为空或 0）。 */
    val withoutRating: Int = 0,
    /** 已注册的数据源数量（0 = 链是空的，所有刷新都会失败）。 */
    val registeredSources: Int = 0,
)

fun interface CollectionCountsProvider {
    suspend fun load(): CollectionCounts
}
