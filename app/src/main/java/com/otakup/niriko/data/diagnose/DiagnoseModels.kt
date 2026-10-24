package com.otakup.niriko.data.diagnose

/**
 * 数据体检 / 自检 / 快照共用的只读模型。
 *
 * 为什么单独抽模型：批次 C 的这几项都要求「检查项纯函数化、可 JVM 单测」，
 * 因此把「输入快照」与「检查结果」做成不依赖 Android 的数据类，
 * 运行时只负责采集输入、渲染结果；判定逻辑全部可穷举。
 */
enum class Severity { OK, INFO, WARN, ERROR }

/** 单条体检发现。id 稳定（进报告/日志，不要改）。 */
data class Finding(
    val id: String,
    val title: String,
    val severity: Severity,
    val detail: String,
    /** 可执行的修复建议；null = 只报告不可一键修。 */
    val fix: String? = null,
)

/** 体检汇总（UI 顶部一行）。 */
data class HealthSummary(
    val checked: Int,
    val ok: Int,
    val warnings: Int,
    val errors: Int,
) {
    val hasProblem: Boolean get() = warnings > 0 || errors > 0
}

/**
 * 体检输入：全部来自只读采集（DataStore / 文件系统 / 计数查询）。
 *
 * 刻意不放 Repository 或 Context：这样 8 项检查可以在 JVM 单测里用任意组合穷举，
 * 不需要 Robolectric，也不会因为某个源不可达而让整页体检失败。
 */
data class HealthInput(
    /** 收藏总数。 */
    val collectionCount: Int = 0,
    /** 收藏里「进度为空」的条数（正常追番应该有进度）。 */
    val collectionWithoutProgress: Int = 0,
    /** 已看/在读但没打分的条数。 */
    val collectionWithoutRating: Int = 0,
    /** 已注册的数据源数量（0 = 链是空的，任何刷新都会失败）。 */
    val registeredSourceCount: Int = 5,
    /** 本地快照数量。 */
    val snapshotCount: Int = 0,
    /** 最近一次快照距今小时数；无快照时传 null。 */
    val lastSnapshotAgeHours: Long? = null,
    /** 图片/缓存总字节数。 */
    val cacheBytes: Long = 0,
    /** 缓存上限字节数（0 = 不设限）。 */
    val cacheLimitBytes: Long = 0,
    /** 是否配置了 WebDAV。 */
    val webDavConfigured: Boolean = false,
    /** 待同步队列长度。 */
    val pendingSyncCount: Int = 0,
    /** 深色主题下未达标的对比度组合数。 */
    val contrastFailures: Int = 0,
    /** 是否开启了玻璃自动降档。 */
    val glassAutoEnabled: Boolean = true,
)
