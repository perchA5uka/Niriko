package com.otakup.niriko.data.seasonal

import com.otakup.niriko.data.local.entity.SubjectEntity
import java.time.DayOfWeek

/**
 * 「当季热门」的排序方式（第 5 轮 D26）。
 *
 * 可选项受 Bangumi /calendar 返回字段的限制：它有 rating（分数 + 人数）、date、
 * air_weekday、total_episodes、platform、type，但**没有 heat、也没有 rank**
 * （rank 只能取本地缓存里已有的）。
 *
 * 因此「热度」用**评分人数**（rating.total）作代理指标 —— 这是最接近「热门」的可得字段。
 * 用户已确认默认用热度。
 */
enum class SeasonalSort(val label: String) {
    HEAT("热度"),
    SCORE("评分"),
    RANK("排名"),
    AIR_DATE("开播日"),
    WEEKDAY("放送日"),
}

/**
 * 「当季热门」的类型映射（第 6 轮 §2.4）。
 *
 * 当季热门**不再有自己的类型 chips**（第 5 轮的 SeasonalTypeFilter 已删除）：
 * 类型统一由顶部类型行（SubjectSearchViewModel.selectedType）驱动，
 * 本对象只负责把「类型行的选中值」翻译成要查的 Bangumi type 整数集合。
 *
 * 「全部」= 5 类全查 —— /calendar 以动画为主，放宽窗口的检索才会带出三次元的长连载。
 */
object SeasonalTypes {

    const val ANIME = 2
    const val BOOK = 1
    const val GAME = 4
    const val MUSIC = 3
    const val REAL = 6

    /** 顶部类型行选「全部」时要查的类型。 */
    val ALL: List<Int> = listOf(ANIME, BOOK, GAME, MUSIC, REAL)

    /** 类型行的选中值（null = 全部）→ 要查的类型集合。 */
    fun of(selectedType: Int?): List<Int> = selectedType?.let { listOf(it) } ?: ALL
}

/**
 * 一条「当季热门」条目。
 *
 * 第 6 轮返工（用户复核）：**删掉了 [SeasonalProgress] 与「第 N 话」**。
 * 原实现按「开播日 + 周数」对所有类型推进度，把 63 周前发售的原声带（MUSIC）
 * 显示成「第 63 话（约）」——把非分集条目当成分集番剧展示。
 * 用户要求「先恢复旧版（纯列表）再叠加新逻辑」，因此这一层整体移除。
 */
data class SeasonalItem(
    val subject: SubjectEntity,
    /** 放送星期。来自 /calendar；推算出来的条目用 airWeekday 兜底，可能为 null。 */
    val weekday: DayOfWeek? = null,
    /**
     * true = /calendar 明确返回（权威「正在放送」）；
     * false = 由开播日 + 集数推算得出。
     *
     * UI 据此决定要不要标注「按开播日推算」。
     */
    val confirmed: Boolean = false,
)

/** 一次「当季热门」加载的完整结果。 */
data class SeasonalTrendingResult(
    /**
     * 最终展示的条目（第 6 轮起 = **类内人气序 + 质量门槛 + 多样性重排**后的前 N 条，默认 30）。
     *
     * 改造前这里是「全部正在放送」的清单（用户看到的是放送表，不是人气榜）。
     */
    val items: List<SeasonalItem> = emptyList(),
    /**
     * 合并后的**候选池**（未按类型过滤、未排序、未截断）。
     *
     * 存在的唯一理由：切排序/切类型时用它**本地重排**，做到零网络请求
     * （验收项之一）。改造前每切一次排序都要重打一遍接口。
     */
    val all: List<SeasonalItem> = emptyList(),
    /**
     * 「候选 N 部」里的 N：合并去重、**质量门槛之前**的候选数。
     *
     * 与展示条数（items.size ≤ 30）分开，用户能看出「这个季度有多少部、我们展示了前 30」。
     */
    val totalCandidates: Int = 0,
    /** /calendar 是否可用。false 时 UI 必须标注「按开播日推算」。 */
    val calendarAvailable: Boolean = false,
    /** 是否退回了过期的日历缓存。 */
    val stale: Boolean = false,
    /**
     * 两条来源（/calendar 与放宽窗口检索）**都失败**（第 6 轮 R7）。
     *
     * 「网络失败」必须与「确实没有在播作品」区分开：前者上层要写 state.error 并显示重试，
     * 后者才是空态文案。只要有一条来源成功（含过期日历缓存），这里就是 false。
     */
    val loadFailed: Boolean = false,
)
