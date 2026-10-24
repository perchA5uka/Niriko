# 5 统计与可视化

> **一句话结论**：本域的技术选型**已经定了**——柱状图/环形图跑在 Vico 3.2.0（`ui/stats/NirikoBarChart.kt`、`NirikoDonutChart.kt`），只有**雷达图仍自绘**（`ui/stats/NirikoRadarChart.kt`，文件头已写明理由：compose-charts 1.0.0 无 radar 图层），词云是 `FlowRow` 文字（`TagWordCloud.kt`）。所以"换不换库"不该再讨论，Vico 3.3.1 已被实测否掉（顶 kotlin-stdlib 2.4.10 / Compose 1.12.0，`gradle/libs.versions.toml:26-28`）。真正的问题在**口径**：统计页目前**没有任何筛选入参**（`StatsViewModel.uiState` 的 `combine` 里没有 filter，v1 说的"部分图表随筛选"不成立），而 `totalWatchedEpisodes` 把**游戏分钟数**和**动画集数**加在了一起（`StatsCalculator.kt:47` + `ui/library/CollectionCard.kt:49` 注释"game 时长存于 watchedEpisodes"）——这是本域第一个必须修的可解释性缺陷。
> **本域最值得做的 3 件**：**P0** 5.1+5.2 统一 `StatsQuery` 入参（一次改造让 8 个图表同时"可筛选"，并把缓存键从 `items.hashCode` 扩成 `items + query`）；**P0** 5.6+5.19 观看量分流（集数/分钟/估算标记，否则任何"总时长"都是错的）；**P1** 5.5 口径显式化（完成率分母含"抛弃"，现在 UI 没有任何说明）。

## 5.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 5.1 | 筛选感知的统计面板 | 无（v1 判"部分"，实为无） | **P0** | M | ryot `UserAnalyticsInput` + 缓存键含入参 |
| 5.2 | 统计的媒体类型过滤 | 无 | **P0** | S | 与 5.1 共用 `StatsQuery.types` |
| 5.3 | 统计对比文案 | 无 | P1 | S | taiga 先算可空数值再决定文案 |
| 5.4 | 统一活动记录 | 无 | P1 | M | ryot `daily_user_activity` 按类型分列 |
| 5.5 | 完成率口径显式化 | 部分（有分母，无说明） | P1 | S | 本项目 `completionRate` + 三口径常量 |
| 5.6 | 总观看集数 / 总时长 | 部分（集数混分钟，无时长） | **P0** | S | taiga `CalculateLifeSpentWatching` |
| 5.7 | 各题材平均分 / 中位数 | 无 | P2 | S | 类型分布扩为分布+均分+中位 |
| 5.8 | 与全站总评分的同步率 | 部分（只有逐条差值） | P1 | S | taiga `CalculateScoreDeviation` |
| 5.9 | 条目访问次数统计 | 无 | P2 | M | 无对口；本地轻量表 |
| 5.10 | 剧集讨论人气标注 | 已实现 | P2 | S | `computeEpisodeAirState.heat` + EpisodeHeatStrip |
| 5.11 | 年度总结的可分享性 | 部分（单作品分享卡有） | P1 | M | ryot analytics 用 html2canvas 出图 |
| 5.12 | 历年对比视图 | 部分（逐年列表，非并排） | P1 | S | Vico 分组柱 + 右轴折线 |
| 5.13 | 时光机 / 追番回顾 | 部分（时间线无范围选择） | P1 | S | ryot 日/月/年自适应分桶 |
| 5.14 | 统计数据的导出 | 无 | P2 | S | `ChartSeriesCalculator` 可直接序列化 |
| 5.15 | 收藏健康度提示 | 无（缺 `lastProgressAt`） | P1 | M | 需加列，`updateTime` 不可替代 |
| 5.16 | 评分分布的多源并列 | 部分（个人 vs Bangumi 两路） | P2 | S | `mergeRatingBuckets` 扩 N 路 |
| 5.17 | 完成与弃番的原因分布 | 无（依赖 1.25） | P2 | M | 需先有 `dropReason` |
| 5.18 | 标签统计的时序变化 | 无 | P2 | S | `createTime` 已够算首次使用月 |
| 5.19 | 观看时长估算 | 部分（`durationSeconds` 在库，统计未用） | **P0** | S | taiga `EstimateDuration` 兜底 |
| 5.20 | 统计页加载与空态 | 部分（骨架屏有，卡片级错误态无） | P1 | M | `BroadcastMonthState` 作模板 |
| 5.21 | 观看活动热力图 | 无 · 新提出 | P1 | S | kizitonwose `HeatMapCalendar` |
| 5.22 | 口径说明卡（每个数字可解释） | 无 · 新提出 | P1 | S | 5.5 的 UI 化 |
| 5.23 | 追番节奏 / 弃番率曲线 | 无 · 新提出 | P2 | S | 分组柱 + 折线 |
| 5.24 | 图表可交互读数与无障碍 | 部分 · 新提出 | P1 | S | Vico marker 已在本项目用过 |
| 5.25 | 统计快照与基线对比 | 无 · 新提出 | P2 | M | 需新表/新文件 |
| 5.26 | 评分漂移检测 | 无 · 新提出 | P2 | S | 评分 + `finishDate` 已有 |

## 5.1 逐条详解

### 5.1 筛选感知的统计面板
- **现状**｜无（v1 判"部分"，以代码为准应为无）｜`viewmodel/StatsViewModel.kt:113-140` 的 `combine` 只并入 9 个流：`safeCollectionFlow / _calendarAnchor / _calendarExpanded / _calendarMode / _broadcastSchedule / _seasonalAiringMap / _broadcastError / _episodesBySubject / _broadcastMonths`——**没有任何 filter/type 输入**；数据源是 `collectionRepository.observeAllWithSubject()`（全库）；`ui/stats/StatsScreen.kt` 的 `StatsContent` 也没有筛选控件。唯一"筛选"是 `TagWordCloud` 的点击（`onTagClick`），它跳到作品库而不是改统计。
- **提升**｜用户在统计页切"只看动画/只看 2026 年"时，8 个卡片同时变——这在别处是常规功能，但本项目现在连入口都没有；工程上省掉"每个图表各自 `filter { }`"（现在没有，一旦照 v1 建议逐个接入就会有 8 份筛选逻辑），改成**一个 `StatsQuery` 在 `computeStats` 入口切一次**。
- **原理**｜统一数据流（照此实现）：
```kotlin
data class StatsQuery(
    val types: Set<SubjectType> = emptySet(),      // 空 = 全部
    val statuses: Set<WatchStatus> = emptySet(),
    val yearRange: IntRange? = null,               // 按 createTime 的年份
    val tag: String? = null,
    val source: QuerySource = QuerySource.ALL,     // ALL / LIBRARY_FILTER（复用作品库当前筛选）
) {
    fun matches(cws: CollectionWithSubject): Boolean = /* 纯函数，逐条判断 */
}
```
  链路：`items(safeCollectionFlow) + _statsQuery(MutableStateFlow) → computeStats(items, query, ...)`；`computeStats` 第一行 `val base = query.applyTo(items)`，**其后所有计算只消费 base**，日历的事件计算单独用未筛选的 items（日历是事实，"个人记录"模式单独吃 `query.types`，避免同页两个口径）。缓存：现有 `baseItemsSignature:Int`（:392）扩成 `signature = items.size * 31 + items.hashCode() * 17 + query.hashCode()`，命中才复用 `cachedBaseStats`——当前只要 items 不变就复用，加了 query 后必须把 query 并入键，否则切筛选无反应。
- **效果**｜统计页顶部出现一排 `FilterChip`（全部/动画/书籍/游戏/音乐/三次元 + 年份下拉），选中后标题下方一行摘要"当前：动画 · 2026 年 · 42 部"，所有卡片（概览/状态分布/类型分布/月度趋势/评分分布/年度总结/雷达/词云）同步变化；切到"书籍"时以动画为主的图表自动空态（显示"该筛选下没有数据"而不是 0）。
- **参考**｜`IgnisDa/ryot@main:crates/services/statistics/src/analytics_operations.rs` [码]：`user_analytics(ss, user_id, input: UserAnalyticsInput)`，且缓存键是 `ApplicationCacheKey::UserAnalytics(UserLevelCacheKey { input, user_id })`——**筛选入参进缓存键**，正是 5.1 要的结构；`daily_activity_operations.rs` [码] 再按 `group_by` 决定 day/month/year。
- **成本/风险**｜M｜风险：`StatsUiState` 有 20+ 字段，改动会波及 `StatsScreen` 全部 section；建议先只接"类型 + 年份"两个维度（覆盖 80% 需求），标签维度留到 5.18 一起做。
- **优先级**｜**P0**

### 5.2 统计的媒体类型过滤
- **现状**｜无｜`StatsCalculator.computeStats` 一次性算全部类型的 `statusDistribution`（`WatchStatus.entries` 五态）与 `typeDistribution`（`SubjectType.entries`），但**不能只要其中一类**；`ui/stats/StatsScreen.kt:355 TypeDistributionSection` 只是把 5 类画成条，点它没有 `onClick`。
- **提升**｜"一键只看动画的统计"是追番用户最高频的切分（书籍/游戏的口径完全不同）；工程上与 5.1 共用 `StatsQuery.types`，UI 只是它的一个 chip row，不再造第二套过滤。
- **原理**｜`TypesRow(selected: Set<SubjectType>, onSelect: (Set<SubjectType>) -> Unit)`，点击写 `_statsQuery.update { it.copy(types = ...) }`；多选（动画+三次元）用 `FilterChip` 的 selected 态，点"全部"清空集合（空集=全部，比 `null` 少一个分支）。类型条本身也要能点：`TypeBarRow` 加 `onClick = { onSelect(setOf(item.type)) }`，形成"图表即筛选器"的手势闭环。
- **效果**｜点类型分布里的"动画"条 → 顶部 chip"动画"高亮，页面滚动到顶部，概览变"动画 120 部 · 均分 7.4"；再点一次取消回到全部。返回栈不受影响（筛选存在 ViewModel，不写路由）。
- **参考**｜`IgnisDa/ryot@main:apps/frontend/app/routes/_dashboard.analytics.tsx` [码]：analytics 页用 Mantine `BarChart/PieChart/RadarChart` + 一组过滤控件，图表与筛选同页（筛选态进 URL/atom，服务端按入参聚合）。
- **成本/风险**｜S｜风险：与 5.1 同一个改造窗口，单独做会返工；若 `types` 影响日历（见 5.1 原理），要接受"日历只剩动画"的观感突变，需在图例旁提示。
- **优先级**｜**P0**
### 5.3 统计对比文案
- **现状**｜无｜`StatsScreen` 的 `YearlySummarySection`（:595）只显示当前年 + `YearlyStatItem(label, value)` 的静态数字；没有任何"比上月多看 X 部"的句子，也没有文案生成的纯函数。
- **提升**｜数字变成一句话，用户不用自己心算（"本月 12 部，比上月多 5 部"）；工程上把"对比"抽成可空返回值，数据不足时**降级为不显示**而不是显示"比上月多 0 部"。
- **原理**｜`data/calculator/StatsNarratives.kt`（纯函数，无 Android 依赖）：
```kotlin
sealed interface Comparison {
    data class Up(val delta: Int) : Comparison
    data class Down(val delta: Int) : Comparison
    data object Flat : Comparison
    data object Insufficient : Comparison   // 任一侧样本 < 3
}
fun compareCount(cur: Int?, prev: Int?): Comparison
fun narrative(c: Comparison, unit: String): String?   // Insufficient -> null
```
  调用点：`MonthlyTrendSection`（:504）已在渲染 `monthlyTrend`，取最后两项比较即可；年度用 `yearlyStats` 最后两项。文案模板固定三条，避免"多看了 5 部（+71%）"这种百分比在基数小时误导（`prev < 5` 时只给绝对差）。
- **效果**｜月度趋势标题右侧小字"比上月多 5 部"；上月无数据时该小字消失（不留空白占位）；连续三月下降显示"连续 3 个月下降"（用现有 `monthlyTrend` 尾部三项判定）。
- **参考**｜`erengy/taiga@master:src/taiga/stats.cpp` [码]：`CalculateMeanScore / CalculateScoreDeviation` 先把"可比数值"算成可空/哨兵，再由 UI 决定怎么说（`life_spent_watching = seconds > 0 ? ToDateString(seconds) : L"None"`）——"先算数、再决定文案"的分层可直接照搬。
- **成本/风险**｜S｜风险：文案里的"部/集/小时"单位要跟 5.6 的分流口径一致，否则会出现"本月 12 部（含游戏）"的荒谬句。
- **优先级**｜P1

### 5.4 统一活动记录
- **现状**｜无｜活动只有一种表达：`StatsCalculator.computeTimelineEvents:405` 从 `collections` 派生三条事件（ADDED/STARTED/COMPLETED，按日期倒序），**没有游戏时长事件**、也没有"看了第 N 集"的逐集事件；Steam 时长只在 `SteamLibraryItemEntity.playtimeForeverMinutes` / 游戏的 `watchedEpisodes` 里，不参与时间线。
- **提升**｜时间线从"收藏动作流"变成"我真的做了什么"：看了一集、通关一款游戏、给一张碟打勾，都在同一条流里；工程上先只做"只读聚合器"（不建表），把 5.13/5.21 的展示基础一次备好。
- **原理**｜`data/calculator/ActivityMerger.kt`：
```kotlin
enum class ActivityKind { ADDED, STARTED, FINISHED, EPISODE, GAME_SESSION, TRACK }
enum class AmountUnit { EPISODE, MINUTE, TRACK }
data class ActivityEvent(
    val date: LocalDate, val atMs: Long, val kind: ActivityKind,
    val subjectId: Long, val title: String,
    val amount: Int, val unit: AmountUnit, val estimated: Boolean,
)
fun merge(collections, steamItems, episodesBySubject): List<ActivityEvent>
```
  关键约束：**amount 必须带 unit**，游戏走 `MINUTE`、动画走 `EPISODE`、音乐走 `TRACK`；排序键 `(date, atMs)` 倒序；同一天同作品的 `EPISODE` 事件合并为一条（"看了 3 集"）。不写库：每次由 `items` 现算，纯函数可单测。
- **效果**｜时间线里出现"10 月 12 日 · 看了《X》第 8 集"、"10 月 11 日 · 通关《Y》（游玩 42 小时）"；游戏行的时间来自 Steam 同步时间（`updateTime`），标注"按同步时间近似"。
- **参考**｜`IgnisDa/ryot@main:crates/services/statistics/src/daily_activity_operations.rs` [码]：`daily_user_activity` 按 `AnimeCount / BookCount / AudioBookCount / WorkoutDuration` **分列计数与时长**，聚合时各自 `sum()`——"按类型分列、不混单位"是这件事的核心教训。
- **成本/风险**｜M｜风险：逐集事件需要"每集观看时间"，而本项目只有"已看到第 N 集"这一个数字（没有每集时间戳），所以"10 月 12 日看了第 8 集"只能是**推断**（把 `updateTime` 归给最后一集）——必须标 `estimated = true`，别当事实展示。
- **优先级**｜P1

### 5.5 完成率口径显式化
- **现状**｜部分｜`completionRate = completedCount.toFloat() / totalCount`（`StatsCalculator.kt:48-49`），分母是**全部收藏**，含 `DROPPED`（抛弃）与 `PLAN_TO_WATCH`（想看）；`StatsOverviewSection` 只显示一个百分比，没有任何说明；README 也没定义口径。
- **提升**｜"完成率 36%" 在含 200 部想看时会低得离谱，用户会以为统计坏了；工程上把口径做成**显式常量 + 单测锁定**，UI 上可切换，避免后续 diff 里口径被无意改动。
- **原理**｜三口径常量 + 一个纯函数：
```kotlin
enum class CompletionCaliber { ALL, EXCLUDE_PLAN, STRICT }
fun completionRate(items: List<CollectionWithSubject>, caliber: CompletionCaliber): Float {
    val denom = when (caliber) {
        CompletionCaliber.ALL -> items.size
        CompletionCaliber.EXCLUDE_PLAN -> items.count { it.collection.status != WatchStatus.PLAN_TO_WATCH }
        CompletionCaliber.STRICT -> items.count { it.collection.status in setOf(
            WatchStatus.WATCHING, WatchStatus.COMPLETED, WatchStatus.ON_HOLD, WatchStatus.DROPPED) }
    }
    if (denom == 0) return 0f
    return items.count { it.collection.status == WatchStatus.COMPLETED }.toFloat() / denom
}
```
  默认 `EXCLUDE_PLAN`（想看不算分母），并在胶囊右下角加可点的 `?`。单测覆盖 5 种状态组合 × 3 口径（现有 1124 项单测里已有 StatsCalculator 测试，直接扩）。
- **效果**｜概览胶囊文案"完成率 62%"，点 `?` 弹一句"已看完 42 / 剔除想看后 68；想看 12 部不计入"；设置里可选三种口径。
- **参考**｜`IgnisDa/ryot@main:crates/services/statistics/src/daily_activity_operations.rs` [码]（count 列各自独立，不混算）；本条口径是产品决策，无外部标准 [推断]。
- **成本/风险**｜S｜风险：口径变更会让历史截图/年度分享图（5.11）数字突变——需要把口径写进分享图角落，否则用户会怀疑数据错。
- **优先级**｜P1

### 5.6 总观看集数 / 总时长
- **现状**｜部分｜`totalWatchedEpisodes = items.sumOf { it.collection.watchedEpisodes ?: 0 }`（`StatsCalculator.kt:47`）——**对所有类型一视同仁**，而游戏的"集数"位置上存的是**游玩分钟**（`ui/library/CollectionCard.kt:49-60`：`// game 时长存于 watchedEpisodes`，`completionTime = if (isGame) watchedEpisodes else null`；换算见 `data/remote/game/PlaytimeConverter.kt`）。结果：一个玩了 3000 分钟的游戏会往"总集数"里加 3000。**总计时长完全没有**。
- **提升**｜"累计 1,284 集 · 约 22 天 6 小时"是本域最有传播力的一组数字；工程上把混合单位拆开，同时修掉一个**现有数据错误**（总集数虚高），并为 5.19/5.12/4.4 提供正确基数。
- **原理**｜`StatsCalculator.computeWatchTotals(items): WatchTotals`：
```kotlin
data class WatchTotals(val episodes: Int, val minutes: Int,
                       val estimatedSubjects: Int, val games: Int)
// episodes: 只累加 ANIME/REAL/BOOK 的 watchedEpisodes
// minutes : Σ(minutesPerEpisode(subject) * watchedEpisodes) + Σ(GAME 的 watchedEpisodes)
// minutesPerEpisode: subject.durationSeconds / 60 > 0 ?: fallback(type)   // 动画 24 / 三次元 45 / 书籍 0
```
  书籍/漫画不计分钟（页数不是时间），避免造数。`estimatedSubjects` = 用了 fallback 的作品数，UI 上"约"字由它驱动。
- **效果**｜概览新增两枚胶囊"累计集数 1,284"与"累计时长 22 天 6 小时（3 部按 24 分钟估算）"；游戏另起一枚"游戏 42 小时"；点"22 天 6 小时"可展开"≈ 看完 1,284 集动画"。
- **参考**｜`erengy/taiga@master:src/taiga/stats.cpp` [码]：`CalculateLifeSpentWatching()` 用 `EstimateDuration(item) * 60 * episodes_watched` 累加秒数，再 `ToDateString(seconds)`（"None" 处理 0）；同文件 `CalculateLifePlannedToWatch()` 反向算"还想看要花多久"——两个方向都可借。
- **成本/风险**｜S｜风险：改口径会让"总集数"变小（游戏不再计入），老用户会觉得数据掉了；需在变更说明里写清"游戏时长改到独立胶囊"。
- **优先级**｜**P0**

### 5.7 各题材平均分 / 中位数
- **现状**｜无｜`typeDistribution` 只有 `count/percentage`（`TypeDistItem`）；没有按类型/标签算的均分或中位数。`RatingInsights.localPercentile` 能算"同类型内百分位"（MIN_SAMPLE=5），但统计页没用它。
- **提升**｜回答"我到底偏爱哪一类"——只看数量会被"动画本来最多"带偏，加上均分/中位数才能看出"动画我打 7.4、书籍只打 6.1"；工程上复用现成的 `rating` 字段，一次 groupBy 即可，不新增数据。
- **原理**｜`fun typeQuality(items): List<TypeQuality>`，`TypeQuality(type, count, mean, median, rated)`；median 用 `ratings.sorted().let { if (it.size % 2 == 0) (it[it.size / 2 - 1] + it[it.size / 2]) / 2 else it[it.size / 2] }`；样本 <5 时 `mean/median = null`，UI 显示"—"（与 `RatingInsights.MIN_SAMPLE` 保持一致，别再造一个阈值）。中位数比均值更抗"10 分神作+3 分弃番"的撕裂。
- **效果**｜类型卡每行变成"动画 120 部 · 均分 7.4 · 中位 7.5 · 已评 86"；未评分的作品显示在"已评 86/120"里，用户能看出评分覆盖率。
- **参考**｜本项目 `RatingInsights.localPercentile` [码]（同类内比较+最小样本）；`ChartSeriesCalculator.sortBuckets/bucketStart` [码] 展示了"桶标签解析 + 稳定排序"的写法，均分排序可复用同一套路。
- **成本/风险**｜S｜风险：中位数与均值并列会让卡片变宽，手机上易换行——用两行或折叠。
- **优先级**｜P2

### 5.8 与全站总评分的同步率
- **现状**｜部分｜有逐条对比：`ratingComparison` 取"个人分与 Bangumi 分都存在"的前 20 条按我的评分降序（`StatsCalculator.kt:100-109`），`ChartSeriesCalculator.ratingDifference` 算差值、`NirikoBarChart` 用 `BarLine(dashed = true)` 叠差值折线。**没有**整体统计量（平均偏差、相关系数、一致度），也没有"我比全站苛刻还是宽容"的结论。
- **提升**｜从"看得见 20 条"变成"知道自己的口味位置"：一句话"你比全站平均高 0.42 分，一致度 0.71（n=86）"；工程上是纯函数，输入已在内存里（`collection.rating` + `subject.ratingScore`），零请求。
- **原理**｜`data/calculator/RatingAgreement.kt`：
```kotlin
data class Agreement(val n: Int, val meanBias: Float, val meanAbsDiff: Float, val pearson: Float?)
fun of(pairs: List<Pair<Float, Float>>): Agreement?   // n < 10 -> null
// pearson = cov / (σx·σy)；任一 σ == 0 -> null（全打同分时无相关可言）
```
  UI 三条：偏差（我 − 全站）、平均绝对差、一致度（pearson 映射成 0-100 的"一致度"，并注明是相关系数不是百分比）。
- **效果**｜评分对比卡顶部一行"你比 Bangumi 平均高 0.42 分 · 一致度 71 · 基于 86 部"；样本不足 10 部时整行不渲染；一致性低于 40 时补一句"你的口味很独立"。
- **参考**｜`erengy/taiga@master:src/taiga/stats.cpp` [码] `CalculateScoreDeviation()`：把"分数离散度"作为独立统计量先行计算（与本条"先算统计量再出文案"同构）；本项目 `RatingInsights.deviation/disputeLabel` [码] 已有标准差+标签的实现范式，可复用阈值风格。
- **成本/风险**｜S｜风险：`subject.ratingScore` 是 0–10 但用户评分是 0.5 步进，量表不完全同构；结论必须写"参考"，不能写"准确率"。
- **优先级**｜P1

### 5.9 条目访问次数统计
- **现状**｜无｜全仓无 `visitCount/viewCount/browseCount`（grep 零命中）；`SubjectDetailScreen` 打开即请求详情，不记任何本地计数。
- **提升**｜"最常查看"是"我到底在意什么"的直接证据，也能给"最近常看但没收藏"的作品一个入口；工程上是一张极小表（或 DataStore 里 top-50），写入在详情页 `onResume` 一次，读在统计页一次。
- **原理**｜表 `subject_views(subjectId PK, count INTEGER, lastViewedAt INTEGER)`，写入用 `ON CONFLICT(subjectId) DO UPDATE SET count = count + 1, lastViewedAt = ?`（Room 支持 `@Query` 原生 upsert，避免读-改-写竞态）。只保留 top 200（定时删除不在前 200 的行）。隐私：设置开关"不记录浏览"，关掉即清空该表。
- **效果**｜统计页（或设置）"最常查看"前 10 列表，右侧"看过 12 次 · 最近 3 天前"；开关打开时该卡片显示"浏览记录已关闭"。
- **参考**｜无对口源码（ryot/navidrome 都是服务端口径，不适合本地轻量计数）[推断]：Android 侧惯用原生 upsert + 原子自增。
- **成本/风险**｜M｜风险：多一张表 = 又一次 Room 迁移（v33）；若与 4.6/4.16/5.15 一起改，务必**一次迁移加完所有列/表**，否则要连做 4 次迁移。
- **优先级**｜P2

### 5.10 剧集讨论人气标注
- **现状**｜已实现｜`EpisodeEntity.comment`（"本集讨论/回复数（热力图用）"，`data/local/entity/EpisodeEntity.kt:41`）→ `StatsCalculator.computeEpisodeAirState:481-488` 做 min-max 归一化成 `heat: List<Float>`（全等时全 0，不除零）→ `CalendarDaySheet.EpisodeHeatStrip`（:435）渲染热力条；详情页 `ui/subject/EpisodeRatingChart.kt` 用 Vico 折线 + marker 展示每集评分/讨论。
- **提升**｜v1 判"部分"，实为已实现。可补的是"跨作品的当日热度排序"：日历 sheet 里同一天多部作品，目前按 `ratingScore` 排（`computeCalendarEvents:305`），把"本集讨论数"加进排序键，让"今天大家都在看的那部"排前面。
- **原理**｜排序键改为 `compareByDescending { todayComment(it.subjectId) }.thenByDescending { it.ratingScore }`，`todayComment` 从 `episodesBySubject` 里找 `airdate == date` 的那一集的 `comment`；缺数据回退 `ratingScore`（保持现有行为，不倒退）。
- **效果**｜日历点开周五：讨论 320 条的那部排在最上，右侧一个"本周最热"小标签。
- **参考**｜本项目自身 [码]；外部可对照 `Ludi-Herry/bandi@main:README.md` [README] 的"社区口碑卡同时显示 Bangumi、MAL、AniList 和豆瓣评分"——同为"用外部热度辅助排序"。
- **成本/风险**｜S｜风险：`comment` 需要 `prefetchEpisodes` 成功（60 条上限 + 3 秒超时），失败时排序静默回退，用户不会察觉——可接受。
- **优先级**｜P2

### 5.11 年度总结的可分享性
- **现状**｜部分｜分享卡是**单作品**：`ui/share/ShareCardData` + `ShareCardRenderer.render()`（Compose→Bitmap，离屏 `leftMargin = -WIDTH - 200`）+ `SharePreviewSheet`/`SharePosterCard`，入口在 `SubjectDetailScreen.kt:500,3895`；`YearlySummarySection`（StatsScreen:595）只有数字列表，没有"生成年度图"。
- **提升**｜年度总结是最值得发出去的一屏（也是唯一"用户主动传播"的场景）；工程上复用现成的离屏渲染管线，只新增一份数据装配 + 一个卡片布局，不引入新依赖。
- **原理**｜`YearlyWrapped(year, totalAdded, completed, episodes, hours, topTags(3), topRated(3), typeShare, ratingMean)`；渲染复用 `ShareCardRenderer`（注意：**玻璃/模糊在离屏渲染里不可用**，必须走 `ui/components/GlassDecision.kt` 的降级分支，否则截出来是纯色块）；输出 1080×1920 PNG，走 `FileProvider` + `ACTION_SEND`（与 4.8 同一套出口）。
- **效果**｜年度总结卡右上"生成年度图"→ 预览 sheet（可切换"数据版/海报版"）→ 分享；图上固定包含"完成 42 部 · 284 集 · 约 5 天 3 小时"与口径脚注（呼应 5.5）。
- **参考**｜`IgnisDa/ryot@main:apps/frontend/app/routes/_dashboard.analytics.tsx` [码]：`import html2canvas from "html2canvas"`——Web 端用 html2canvas 把统计面板截成图分享，同属"统计页 → 图片"的出口设计。
- **成本/风险**｜M｜风险：Compose 离屏渲染在低端机上 1080×1920 可能 OOM/超时（现有单作品卡已存在此风险，年度图内容更多）；需在后台线程 + 尺寸降级（720×1280）。
- **优先级**｜P1

### 5.12 历年对比视图
- **现状**｜部分｜`YearlyStats(year, totalAdded, completedCount, totalEpisodes, averageRating)`（`data/model/stats/YearlyStats.kt`）已逐年算好，`YearlySummarySection` 用 `YearlyRow` 一年一行堆叠展示；**没有并排对比**（`NirikoBarChart` 支持多 `BarSeries`，但从未被用于年份）。
- **提升**｜"今年比去年多看了吗"必须并排看才有意义；工程上零新计算——`yearlyStats` 直接转成两个 `BarSeries`，Vico 的分组柱与右轴折线都已在本项目验证可用（`NirikoBarChart` 的 `line` + `Axis.Position.Vertical.End`）。
- **原理**｜`ChartSeriesCalculator` 增 `yearlySeries(stats): List<BarSeries>`：`labels = years.map { it.year.toString() + "年" }`，series1 = totalAdded（新增加入），series2 = completedCount（完成），`BarLine` = averageRating（0–10 量纲不同 → 走右轴）；仅当 `stats.size >= 2` 才渲染图，1 年时保留原列表（空态不是"一根柱"）。
- **效果**｜年度总结顶部一张"2022–2026"分组柱 + 虚线均分折线，图例"加入/完成/均分"；长按柱可读出"2024：加入 62 / 完成 41 / 均分 7.6"（依赖 5.24）。
- **参考**｜`IgnisDa/ryot@main:crates/services/statistics/src/daily_activity_operations.rs` [码]：跨度 ≥500 天自动按年分桶、≥200 天按月——"时间跨度决定粒度"的规则可借来定"哪些年份该合并成'更早'"。
- **成本/风险**｜S｜风险：年份多（>8）时柱会挤成线，需 `bucketsPerScreen` 横滑（`NirikoBarChart` 已支持 `Zoom.fixed`）。
- **优先级**｜P1

### 5.13 时光机 / 追番回顾
- **现状**｜部分｜`ui/stats/TimelineSection.kt`（197 行）是 iOS Smart Stack 风格卡片堆叠、默认折叠，数据来自 `computeTimelineEvents`（ADDED/STARTED/COMPLETED 三条，倒序）；**没有时间范围选择**，也不能"只看 2024"。
- **提升**｜从"一条永远很长的流"变成"选一段时期回顾"；工程上给 `computeTimelineEvents` 加区间参数即可（函数已是纯函数，改动局部）。
- **原理**｜`computeTimelineEvents(items, range: ClosedRange<LocalDate>? = null)`：在现有三条事件生成后 `filter { range == null || it.date in range }`；UI 上年份 chip（从 `yearlyStats.map { it.year }` 派生，无需新数据）+ 折叠态摘要"2024 · 37 部 · 284 集"。分页：按 `date.year` 分组 sticky header，避免一次渲染 2000 条卡片。
- **效果**｜时间线顶部一排年份 chip；选 2024 后标题变"2024 年 · 你加入了 62 部、看完 41 部"；再点一次"全部"回到全量。
- **参考**｜`IgnisDa/ryot@main:crates/services/statistics/src/daily_activity_operations.rs` [码]：`ApplicationDateRange` + `group_by` 的日/月/年三档，正是"回顾视图"的粒度模型；`user_analytics_parameters` [码]（先查 min/max 日期再决定展示范围）可避免"选了没有数据的年份"。
- **成本/风险**｜S｜风险：`TimelineSection` 是已有动效组件（卡片堆叠+折叠），加 sticky header 可能与其滚动动效冲突，需实测。
- **优先级**｜P1

### 5.14 统计数据的导出
- **现状**｜无｜没有 CSV/JSON 导出入口；`ChartSeriesCalculator` 的所有输出（`cumulativeCounts/monthlyCumulative/ratingDifferences/mergeRatingBuckets`）都是纯数据，天然可序列化，但没人序列化。
- **提升**｜用户能把统计拿去做 Excel/Notion 年度复盘；工程上导出器是纯函数（输入输出都是 `String`/数据类），可单测且零依赖，出口复用 4.8 的 `FileProvider`。
- **原理**｜`StatsCsvExporter.export(state: StatsUiState): String`，两段：概览（指标,值）+ 明细（年,月,加入,完成,集数,均分）。规则：UTF-8 **带 BOM**（Excel 中文不乱码）、CRLF、字段含逗号/引号时用双引号包裹并双写引号；文件 `niriko-stats-YYYYMMDD.csv`。与 7.5 的"清单导出"合并实现（同一出口，不同列）。
- **效果**｜设置→"同步与备份"里"导出统计（CSV）"，分享后用 Excel 打开是正确的中文列头；空数据时按钮禁用并提示"还没有可导出的统计"。
- **参考**｜无对口源码 [推断]（ryot 只做图片导出）；CSV 规则按 RFC 4180。
- **成本/风险**｜S｜风险：与 5.11 的图片导出共用出口，命名/目录要统一，否则用户找不到文件。
- **优先级**｜P2

### 5.15 收藏健康度提示
- **现状**｜无｜`CollectionEntity` 有 `updateTime` 但**没有 `lastProgressAt`**（`data/local/entity/CollectionEntity.kt:26-46`：status/watchedEpisodes/watchedVolumes/isPrivate/rating/startDate/finishDate/personalTags/impression/remark/trackIds/createTime/updateTime）；`updateTime` 会被"改评分/改备注/改私密"刷新，用它判"多久没看"必然误报。
- **提升**｜把"在看但半年没动"的作品收进"待整理"，用户能批量改搁置/抛弃，减少统计里永远挂着的一堆在看；工程上加一列（不是一张表），写入点唯一（进度变更处），判定是纯函数。
- **原理**｜迁移加 `lastProgressAt INTEGER`；`CollectionRepository` 的进度更新路径里同时写 `lastProgressAt = now`（**只在这里写**，评分/备注不动它）。判定 `HealthFlag.stale(items, now, days = 30)`：`status == WATCHING && (now - (lastProgressAt ?: startDate ?: createTime)) > days` 且 `AiringStatus.getPhase != COMPLETED`。UI 三行：作品 / 搁置天数 / 一键"改为搁置"。
- **效果**｜统计页底部"待整理 · 3 部"：每行"《X》· 在看 · 47 天没有更新进度"，右侧"搁置"按钮；处理后该卡片自动消失。
- **参考**｜无对口（各家的"停滞提醒"多为服务端邮件）[推断]；判定逻辑与 4.5 的 `CatchUpCalculator` 共用 `CollectionEntity` 读侧，注意别互相污染阈值。
- **成本/风险**｜M｜风险：加列即迁移（v33）；老数据 `lastProgressAt` 为空会全被标"停滞"，必须回填 `lastProgressAt = updateTime` 作为初值，并给 7 天宽限期。
- **优先级**｜P1

### 5.16 评分分布的多源并列
- **现状**｜部分｜已有个人 vs Bangumi 两路：`ChartSeriesCalculator.mergeRatingBuckets(mine, bangumi)` 取区间并集、按 `bucketStart` 排序、缺失补 0、同标签累加；`NirikoBarChart` 用两个 `BarSeries` 分组柱渲染。权威源（`data/remote/rating/ExternalRating.toTenPoint` 已有 0–10 换算）**没有进同一张图**。
- **提升**｜"我 vs Bangumi vs 豆瓣/IMDb"三层同屏，才能看出"只有我跟豆瓣一致"；工程上把两路合并函数泛化成 N 路，配色沿用 `ui/theme/ChartPalette`，不新增图表组件。
- **原理**｜`fun mergeBuckets(sources: List<Pair<String, List<RatingDistribution>>>): List<NamedBucket>`（内部就是现有 `mergeRatingBuckets` 的 N 路推广：并集 → 排序 → 逐源补 0）。约束：只有"同一部作品三方都有分"时才计入（否则分母不同，柱高不可比）——这点必须写进注释，否则后人会直接把三份分布拼起来。
- **效果**｜评分分布卡右上多一排 `FilterChip`（Bangumi / 豆瓣 / TMDb），默认全选；柱从 2 根变 3 根，图例带来源名；某源未配置密钥时对应 chip 置灰并提示"未配置 TMDb 密钥"。
- **参考**｜本项目 `ChartSeriesCalculator.mergeRatingBuckets` [码]（两路版）；`RatingInsights.toTenPoint` [码]（跨源量表换算，注释明确"再放一份是为了让 calculator 层不依赖 remote 层"——扩展时保持这条分层纪律）。
- **成本/风险**｜S｜风险：外部评分覆盖率低（密钥/地区限制），三路柱会大量为 0，看起来像"我没评"——必须按"共同样本"过滤后再画。
- **优先级**｜P2

### 5.17 完成与弃番的原因分布
- **现状**｜无｜`CollectionEntity` 无 `dropReason` 字段；v1 已注明依赖 1.25（弃番原因）落地，目前 1.25 也不存在。
- **提升**｜弃番原因是"选番口味"的唯一显式信号，比评分更能指导推荐（也直接喂给 3.x 发现页）；工程上分两步：先加枚举 + 一次可选弹窗，再出环形图。
- **原理**｜`enum class DropReason { PACING, STORY, PRODUCTION, TIME, TASTE, OTHER }`，存 `collections.dropReason TEXT?`（迁移同批）；触发点：状态改为 `DROPPED` 时弹一次底部 sheet（可跳过/可"不再询问"）。统计：用现有 `NirikoDonutChart`，slices = reasons.map { DonutSlice(label, count, chartTypeColor) }；与 5.1 的 `StatsQuery` 联动（只看动画的弃番原因）。
- **效果**｜改为抛弃时弹"为什么弃？（可跳过）"六个 chip；统计页新增"弃番原因"环形，"节奏太慢 12 · 剧情 8 · 没时间 5 · 未填 20"。
- **参考**｜无对口源码 [推断]（ryot 有 `seen` 但不记原因）；形态借 `NirikoDonutChart` 既有调用点（状态分布/类型分布）。
- **成本/风险**｜M｜风险：弹窗打断操作，用户会烦——必须"可跳过 + 记住不再问"，且默认不阻塞状态变更（先写 DROPPED，再问原因）。
- **优先级**｜P2

### 5.18 标签统计的时序变化
- **现状**｜无｜`tagStats` 是全局累计：`tagCountMap[tag]++`（`StatsCalculator.kt:96-98`），没有时间维度；`TagWordCloud(tags)` 只接一个列表，字号映射 `12sp + ratio * 16sp`。
- **提升**｜回答"我这两年的口味怎么变的"——2023 满屏"异世界"，2026 变成"日常/治愈"；工程上只用 `collection.createTime` 的 `YearMonth`（已在 `monthlyMap` 用过），零新字段。
- **原理**｜`fun tagTimeline(items, years: IntRange?): List<TagStat>`：按 `createTime.year in years` 过滤后重算 `tagCountMap`；`TagWordCloud` 保持纯展示，由调用方先过滤（**不要**给组件加时间维度）。再补 `fun tagFirstUsed(items): Map<String, YearMonth>`，首次使用落在窗口内的标签加一个小圆点。
- **效果**｜词云上方一排年份 chip；选"2026"后只剩该年新用/在用的标签，首次出现的标签右上角带小绿点；标注"该年新用 7 个标签"。
- **参考**｜本项目 `monthlyMap`（`createTime → "yyyy-MM"`）[码] 与 `TagWordCloud` 的字号映射 [码]。
- **成本/风险**｜S｜风险：早年数据 `createTime` 可能是导入时间（Steam/B站导入会集中到同一天），时序会失真——UI 需注明"按加入时间统计"。
- **优先级**｜P2

### 5.19 观看时长估算
- **现状**｜部分｜`EpisodeEntity.durationSeconds` 在库里（`entity/EpisodeEntity.kt:33`，由 `EpisodeRepository:108/130` 从 `EpisodeDto.durationSeconds` 写入），但**统计层从未读取**（`grep durationSeconds` 仅剩 `TrackListSection` 用它显示音乐曲目时长）；`SubjectDetailData.durationSeconds` 同样只在详情页展示。没有任何"缺时长时的类型默认值"。
- **提升**｜"约 22 天 6 小时"里的"约"要有依据：有 `durationSeconds` 就用真值，没有就用类型默认并**显式标注估算**，而不是干脆不算；工程上把"每集分钟数"抽成一个可测函数，5.6 与详情页共用。
- **原理**｜`fun minutesPerEpisode(subject, episodes): IntEstimate`：优先 `episodes.firstOrNull { it.durationSeconds > 0 }?.durationSeconds / 60` → 否则 `subject.durationSeconds / 60` → 否则 `fallback(type)`（ANIME 24 / REAL 45 / BOOK 0）；返回 `IntEstimate(value, source: EXACT | SUBJECT | TYPE_DEFAULT)`。汇总时按 `source != EXACT` 计 `estimatedSubjects`，UI 的"约"与 tooltip 由它驱动。
- **效果**｜"累计时长 22 天 6 小时（约）"，点开 tooltip"其中 41 部用真实单集时长，3 部按类型默认 24 分钟估算"；某类型无时长数据时不显示时长胶囊（而不是显示 0）。
- **参考**｜`erengy/taiga@master:src/taiga/stats.cpp` [码]：`EstimateDuration(item)` 就是"缺时长用估算"的独立函数，且被 `CalculateLifeSpentWatching` 与 `CalculateLifePlannedToWatch` 两处复用——与本条"抽一个函数给两处用"完全同构。
- **成本/风险**｜S｜风险：`durationSeconds` 依赖 `prefetchEpisodes`（60 条上限），长番/老番大量缺失 → 估算比例高，文案要诚实。
- **优先级**｜**P0**

### 5.20 统计页加载与空态
- **现状**｜部分｜有骨架屏 `ui/stats/StatsSkeleton.kt`（107 行）与空态 `StatsScreen.EmptyStatsPlaceholder`（:291，"去发现页"按钮）；但**没有卡片级错误态**：`computeStats` 在一个 `combine` 里算全部，`catch { Log.e }` 后整体保持上次值或空，单张图失败无法表达；放送日历倒是有逐月状态（可作模板）。
- **提升**｜"某一张图失败"不再等于"整页空白"；工程上把 `StatsUiState` 从"一个大对象"改成"分区状态"，与 5.1 的 query 改造同批做最省事（反正要动 combine）。
- **原理**｜`data class SectionState<T>(val data: T?, val loading: Boolean, val error: String?)`，`StatsUiState` 的每个图表字段换成 `SectionState<...>`（或旁挂 `sectionErrors: Map<Section, String>` 更小改动）。渲染层统一 `SectionCard(state) { content }`：`loading`→骨架、`error`→一行"加载失败 · 重试"、`data == null || isEmpty`→空态文案。
- **效果**｜标签统计失败时只有那张卡显示"标签数据加载失败 · 重试"，其余 7 张正常；重试只重算该分区。
- **参考**｜本项目 `BroadcastMonthState(key, status, lastSuccessAt, error, isPartial, eventCount)` [码]（`data/model/stats/StatsUiState.kt:15-23`）就是"分区状态"的成熟样例，直接照搬到图表分区；`RefreshStatusLabels.formatAgo` [码] 提供"上次更新"文案。
- **成本/风险**｜M｜风险：`StatsUiState` 有 20+ 字段，全换 `SectionState` 会波及预览（`StatsScreenPreview`）与全部调用点；更小的做法是先加 `sectionErrors` 字典。
- **优先级**｜P1

## 5.9 本域新增候选（发散）· 条目 5.21–5.26

### 5.21 观看活动热力图
- **现状**｜无 · 新提出｜没有"日历热力图"；`CalendarCard` 是月/周网格（事件导向），不是"每天多少集"的强度图。
- **提升**｜一年一屏看"我什么时候在追番"，补上"时间分布"这一维（现有所有图都是"按作品/按类型"，没有"按天"）。
- **为什么是 Niriko 需要**｜本项目已有 `episodes`（集日期）+ `watchedEpisodes` + 1124 项单测文化，热力图的数据与测试成本都很低；而"每天看几集"正是纯客户端应用最擅长（无需服务端埋点）。
- **原理**｜依赖 5.4 的 `ActivityEvent`（没有逐集时间戳时用 `updateTime` 近似，标 `estimated`）；聚合 `Map<LocalDate, Int>`；渲染用 kizitonwose 的 `HeatMapCalendar`（与本项目已在用的 2.10.1 同库，**不新增依赖**），色阶 5 档由 `ChartPalette` 派生。
- **效果**｜统计页底部"观看热力图"：一年 53 列，深色=看得多；点某天弹"10 月 12 日 · 3 集"；年度切换 chip。
- **参考**｜`kizitonwose/Calendar@main:compose/src/main/java/com/kizitonwose/calendar/compose/heatmapcalendar/HeatMapCalendar.kt` [码] `HeatMapCalendarImpl(state: HeatMapCalendarState, weekHeaderPosition, dayContent: (CalendarDay, HeatMapWeek) -> Unit)`。
- **成本/风险**｜S｜风险：数据来源是"推断的每日集数"，只能做趋势不能做账；UI 必须标"按进度更新时间估算"。
- **优先级**｜P1

### 5.22 口径说明卡（每个数字可解释）
- **现状**｜无 · 新提出｜概览胶囊、完成率、均分、总集数都没有"这个数怎么来的"入口；`RatingInsights` 的注释里写着"UI 必须标注'本地库内百分位'，不能暗示是全网排名"（`RatingInsights.kt:55-57`）——**代码里已经意识到这个问题，但 UI 没做**。
- **提升**｜每个数字旁一个 `?`，点开一段口径 + 样本量；直接消灭"统计页数字看着不对但不知道哪不对"的信任问题。
- **为什么是 Niriko 需要**｜本项目无服务端，所有数字都是本地算的，用户无法与"官方统计"对照；`RatingInsights` 的注释证明团队已在自律，但缺一个统一的呈现层。这也符合项目"可信度可见"的一贯风格（`RefreshStatusLabels`、`isPartial`、`GlassDecision`）。
- **原理**｜`data/calculator/MetricGlossary.kt`：`enum class Metric { COMPLETION_RATE, TOTAL_EPISODES, AVG_RATING, TOTAL_HOURS, RATING_PERCENTILE }`，每个带 `title/definition/denominator/sampleOf(state)`；UI 组件 `MetricInfoButton(metric)` 弹 `AlertDialog`（一行定义 + 一行"基于 n 部作品"）。
- **效果**｜"完成率 62% ?"→"已看完 42 部 ÷ 剔除想看后的 68 部。想看 12 部不计入。"；"本地库里百分位 72 ?"→"在你自己的 86 部同类型收藏中，好于 72%。不是全网排名。"
- **参考**｜`RatingInsights.localPercentile` [码]（注释即口径文本来源）；`IntEstimate.source`（5.19）提供"精确/估算"标记，两者共用一套"数字可信度"语言。
- **成本/风险**｜S｜风险：口径文案与实现会漂移——把定义字符串**放在计算器同文件**，单测断言"定义里出现的分母 == 实际用的分母"。
- **优先级**｜P1

### 5.23 追番节奏 / 弃番率曲线
- **现状**｜无 · 新提出｜现有月度趋势只有"加入数"（`monthlyTrend.count`），没有"完成/弃番/搁置"的分流，也看不到弃番率。
- **提升**｜看出"我在哪个月放弃得最多"，比"加入多少"更有自省价值；工程上仍是 `groupBy`，但要把状态也算进去（现在只算数量）。
- **为什么是 Niriko 需要**｜本项目五态模型完整（看/在看/搁置/抛弃/想看），弃番率是这套模型的**唯一独特产出**，别家（只有三态）算不出来。
- **原理**｜`MonthlyFlow(month, added, completed, dropped, onHold)`，按 `createTime` 归月、按当前 `status` 分列（注意：这是"当前状态的快照"，不是"当月变更"，需在 UI 注明）；图用分组柱（4 根/月），或 2 根柱 + 弃番率折线（`dropped / (completed + dropped)`）。
- **效果**｜月度趋势下方新增"追番节奏"：柱=加入/完成，折线=弃番率；切年份后重算。
- **参考**｜`IgnisDa/ryot@main:crates/services/statistics/src/daily_activity_operations.rs` [码]（按类型分列 count，天然支持"4 根柱"）。
- **成本/风险**｜S｜风险：状态快照 ≠ 当月变更，容易被误读成"10 月弃了 5 部"；必须用"当前状态分布"措辞，或按 `finishDate` 归月（更准，但 `finishDate` 大量为空）。
- **优先级**｜P2

### 5.24 图表可交互读数与无障碍
- **现状**｜部分 · 新提出｜`NirikoBarChart` 只有 `dataLabel`（数值直接画在柱上，`showValueLabels = bars.size == 1`），**没有 marker/长按读数**；`NirikoDonutChart` 没有点选；`NirikoRadarChart` 自绘、无 hit test；三个图都没有 `contentDescription` 摘要，TalkBack 只能读出"图表"这个空壳。
- **提升**｜① 多序列柱（分组柱）现在**看不到数值**（`showValueLabels` 被关掉），长按读数补上；② 无障碍摘要让视障用户也能知道"月度趋势：8 月 12 部，9 月 7 部"。
- **为什么是 Niriko 需要**｜本项目已有 `EpisodeRatingChart` 用 Vico marker 的先例（`rememberDefaultCartesianMarker`），API 与配色都已跑通，复制成本极低；而"玻璃/单色系"自定义配色让数值更难从视觉读出，读数交互是刚需。
- **原理**｜`NirikoBarChart` 加参数 `marker: Boolean = true`：`rememberDefaultCartesianMarker(valueFormatter = ...)` + `markerVisibilityListener`（长按显示/抬起隐藏）；`NirikoDonutChart` 用 `BoxWithConstraints` 里已有的尺寸做角度 hit test（atan2 + 半径区间）；每个图外包 `Modifier.semantics { contentDescription = summary }`，summary 由 `ChartSeriesCalculator` 生成（"最高 8 月 12 部，最低 2 月 3 部"）。
- **效果**｜长按分组柱出现浮层"9 月：个人 8 / Bangumi 12"；TalkBack 聚焦图表先读一句摘要；开启"减弱动态效果"时浮层无动画（复用 `motionEnabled`）。
- **参考**｜`patrykandpatrick/vico@master:vico/compose/src/main/java/com/patrykandpatrick/vico/compose/cartesian/marker/DefaultCartesianMarker.kt` [码]（目录确认）；本项目 `ui/subject/EpisodeRatingChart.kt` [码] 已用 `rememberDefaultCartesianMarker` + `CartesianMarkerVisibilityListener`。
- **成本/风险**｜S｜风险：marker 在本项目只在"单序列折线"上验证过，分组柱的 marker target 是 `ColumnCartesianLayerMarkerTarget`，需实测列选择是否准确。
- **优先级**｜P1

### 5.25 统计快照与基线对比
- **现状**｜无 · 新提出｜统计只有"当前值"，没有历史快照，因此"我今年比去年进步了吗"只能靠 `yearlyStats`（它按 `createTime` 归年，不是"当时的统计值"）。
- **提升**｜每月自动存一份概览快照，之后可看"3 个月前 vs 现在"；工程上存 JSON 文件（复用 `data/backup/` 的 JSON 序列化纪律），不进 Room。
- **为什么是 Niriko 需要**｜本项目是纯本地、无服务端，没有"账号历史"可查；而 `yearlyStats` 的"按加入年份"口径会让老收藏永远留在老年份，无法表达"我现在的口味"。快照是唯一能保留"当时状态"的手段。
- **原理**｜`data/stats/StatsSnapshotStore`：每月首次进统计页时写 `filesDir/stats-snapshots/YYYY-MM.json`（字段 = `StatsUiState` 的标量部分，10 个左右），最多留 36 份。对比视图 `SnapshotDiff(baseline, current): List<Delta(metric, before, after, deltaPercent)>`。
- **效果**｜概览卡右上"对比 3 个月前"下拉；选中后每个胶囊下方出现 "+12 部 / +8%" 小字。
- **参考**｜`data/backup/BackupManager` 的 JSON v4 + `RestoreMergePolicy` [推断]（本项目已有的版本化 JSON 纪律，直接沿用其 version 字段做法）。
- **成本/风险**｜M｜风险：快照是"用户不可见的新文件"，备份/恢复要不要带上它需要决策（不带上会丢，带上会让 JSON 备份更大）。
- **优先级**｜P2

### 5.26 评分漂移检测
- **现状**｜无 · 新提出｜`ratingComparison` 只做"我的分 vs 全站分"的横向对比；没有"同一部作品我的评分随时间变化"的纵向记录（`CollectionEntity.rating` 是**单值**，改分即覆盖）。
- **提升**｜"你给《X》打了 6 分，3 个月后又改成 9 分"——这类"口味变化"是追番 App 里最有故事性的数据；工程上只需在改分时记一条轻量历史。
- **为什么是 Niriko 需要**｜本项目评分是"可反复修改"的（详情页随时改），但没有任何历史；而 1124 项单测 + 纯函数文化让"评分历史聚合"这类新逻辑很容易被验证。
- **原理**｜表 `rating_history(subjectId, score, at)`（或 DataStore 里每部最多 5 条，控体积）；聚合 `RatingDrift(subjectId, first, last, delta, spanDays)`；统计页展示"评分变化最大的 5 部"与"平均漂移 ±0.3"。判噪：相邻两次改分间隔 <1 天视为一次（误触修正）。
- **效果**｜统计页"口味漂移"卡："《X》6→9（+3，间隔 92 天）"、"你的评分平均每月上浮 0.04"。
- **参考**｜无对口源码 [推断]；形态可借 `computeTimelineEvents` 的"事件流 → 时间线"写法。
- **成本/风险**｜S｜风险：又是 Room 迁移；若不进 Room 而进 DataStore，查询/排序会很难写——建议与 5.9/5.15 一起在 v33 一次加完。
- **优先级**｜P2

## 5.A 本域新增参考库

| 仓库 | 许可 | star | 语言 | 为何与本项目相关 | 已验证文件 |
| --- | --- | --- | --- | --- | --- |
| patrykandpatrick/vico | 未取到（本项目 `libs.versions.toml:96` 注释记 Apache-2.0） | 3.2k（截至 2026-10） | Kotlin（Compose + KMP） | **已在用** 3.2.0（柱/环/每集折线）；marker 体系是 5.24 的现成能力；3.3.1 因顶版被否，暂不升级 | `vico/compose/src/main/java/com/patrykandpatrick/vico/compose/cartesian/marker/DefaultCartesianMarker.kt` [码]（目录确认）；本项目 `ui/subject/EpisodeRatingChart.kt` 已用 |
| kizitonwose/Calendar | MIT（shields 取到） | 5.6k（截至 2026-10） | Kotlin（Compose，含 KMP） | **已在用** 2.10.1（日历月/周）；同库的 `HeatMapCalendar` 让 5.21 零新依赖 | `compose/src/main/java/com/kizitonwose/calendar/compose/heatmapcalendar/HeatMapCalendar.kt` [码] |
| IgnisDa/ryot | GPL-3.0（shields 取到） | 未取到 | Rust + React | 统计的**筛选入参 + 缓存键含入参 + 日/月/年自适应分桶**；是 5.1/5.12/5.13 的直接蓝本 | `crates/services/statistics/src/analytics_operations.rs` [码] `user_analytics` / `UserAnalyticsInput` / `ApplicationCacheKey::UserAnalytics`；`daily_activity_operations.rs` [码] `grouped_by` 阈值 500/200 天、按类型分列 sum；`apps/frontend/app/routes/_dashboard.analytics.tsx` [码]（Mantine Bar/Pie/Radar + html2canvas 导出） |
| erengy/taiga | GPL-3.0（`stats.cpp` 头部 GNU GPL v3 声明已读） | 未取到 | C++ | "缺时长用估算 + 秒数归一到人类可读"的完整实现；统计的 8 个 `Calculate*` 是 5.6/5.8/5.19 的方法论参照 | `src/taiga/stats.cpp` [码] `CalculateLifeSpentWatching` / `CalculateLifePlannedToWatch` / `CalculateScoreDeviation` / `EstimateDuration` |
| FuzzyGrim/Yamtrack | 未取到 | 未取到 | Python（Django） | 活动/事件物化思路（与本域 5.4 的"按类型分列"同源，日历域更直接） | `src/events/calendar/main.py` [码]（详见 4.A） |

## 5.B 计数自检

- 详解条目：**20**（5.1–5.20，编号与 v1 完全一致，无合并；其中 5.10 由 v1「部分」改判「已实现」，5.1/5.2 由「部分」改判「无」）。
- 新增候选：**6**（5.21–5.26）。
- 汇总表行数：**26** = 20 + 6 ✅。
- 证据等级分布：本项目侧全部 [码]（实读文件/行号，含 `StatsCalculator.kt` / `StatsViewModel.kt` / `CollectionCard.kt:49` / `NirikoBarChart.kt` / `EpisodeEntity.kt:33,41`）；外部侧 [码] 3 处（ryot ×2、taiga）、[README] 1 处（bandi，用于 5.10 排序取向）、[推断] 5 处（5.9 实现方式、5.14 CSV 规则、5.15 判定、5.17 形态、5.25/5.26 形态）。
- 待核 0 处（本域所有"现状"均已实读；v1 的"部分图表随筛选"经代码确认**不成立**，已在 5.0/5.1 显式纠正）。
- star 取数日期：2026-10；未取到的写"未取到"，未编造。
