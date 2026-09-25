# Niriko 刷新机制改造方案

> 状态：**P0 / P1 / P2 全部落地**（构建 + 170 项单测全绿）。
> 本文档为实施依据；进度见文末「实施进度」。

---

## 一、目标

把散落在 Application / 各 ViewModel / 各 Repository 里的「刷新」收敛为**一处编排**：

1. **可判定** —— 每个远端资源有明确的新鲜度（soft/hard TTL），不再靠裸 Boolean 和内存变量猜。
2. **不重复** —— 并发触发的同一刷新合并成一次（single-flight），远端写串行化。
3. **不风暴** —— 冷启动/后台预取不再同时发起多条互不知情的链路；Room 写入批量且只写变化的行。
4. **有退避** —— 失败不再静默重打，按退避梯压制后续非强制刷新。
5. **跟生命周期** —— 冷启动、进前台各有一轮受控刷新；后台停掉非必需任务。
6. **有反馈** —— UI 能区分「刷新中 / 上次更新 / 失败可重试」。

非目标见 §七。

---

## 二、现状：刷新是怎么发生的

| 触发器 | 位置 | 实际行为 |
| --- | --- | --- |
| 冷启动 | `NirikoApplication.onCreate` | 并发起 5 条互不知情的后台任务（迁移 / Steam 自动绑定 / 放送提醒 / Steam 排行预取 / 拼音回填） |
| Discover 页首次组合 | `SubjectSearchViewModel.init:108` | 调 `refreshTrending()` |
| 作品库 VM 创建 | `CollectionViewModel.init:47-56` | 对全库 GAME 跑 `steam.matchAndBind` |
| 统计页 VM 创建 | `StatsViewModel.init:105` | 拉放送日历 + 季节数据 + 最多 60 个剧集预取 |
| 下拉刷新 | `SubjectSearchScreen.kt:171-181` | `refreshTrending()`，只清当前类型缓存 |
| 进详情页 | `SubjectDetailViewModel.loadSubject` | 先出缓存，再**无条件**拉远端详情 + 14 个并行扩展接口 |
| 手动 | 设置页同步按钮 / 统计页日历刷新 / 详情页 VNDB·AniList 重试 | 单次触发 |

**缺失的能力**：进前台/回前台刷新、统一的新鲜度判定、失败退避、并发去重、陈旧度展示。

---

## 三、问题清单（审计结论，含证据）

### P0-1 冷启动刷新风暴

`NirikoApplication.kt:94-96` 的 `prefetchSteamChart()` 与 `:77-79` 的 `autoBindGames()` **均无持久化节流**：
`lastAutoBindTime` 是进程内 `@Volatile`（`:145-147`），注释自己写明「冷启动即跑一次」；README 第 78 行却声称 6h 节流。

叠加 `MainPager.kt:51` 的 `beyondViewportPageCount = 1`（相邻页预组合），冷启动瞬间同时跑 4 条远端链、数百次 Room 写：
无网络判断、无优先级、无全局互斥、无用户可见状态。

### P0-2 缓存写放大 → 界面失效风暴

`CollectionDao.observeAllWithSubject()` 是 `@Transaction @Query` + `@Relation(subjects)`，Room **同时观察 `collections` 与 `subjects` 两张表**。而：

- `DataSourceChain` 每次远端返回都 `upsertAll`（`:68,95,131,193,333,348,363`）——每敲一次键（300ms 防抖）写一批；
- `BroadcastFetcher.kt:70`、`SeasonalFetcher.kt:37,81` **逐条** upsert（N 个独立事务）；
- `SteamRepository.kt:529-544` 榜单 100 条**逐条** upsert（100 个独立事务）。

→ 每次后台预取写入都会让 `CollectionViewModel.uiState`（`CollectionViewModel.kt:59-131`）**全量重算**：排序 + 统计 + 标签计数 + 一次 `steamRepository.getSupplements()` 数据库查询。

### P0-3 `forceRefresh` 形同虚设 / 死代码

- `SubjectRepository.search(forceRefresh)` 与 `getDetail(forceRefresh)` 声明了参数**从未使用**（`:45-75, 81-99`），调用方却到处传 `forceRefresh = true`（`SubjectSearchViewModel.kt:366,377,412,420,432,442`）。
- `SubjectRepository.rankingCache` 30s TTL，但 `clearRankingCache()`（`:234-237`）**全仓无调用方** → 下拉刷新后 30 秒内仍返回旧榜。
- `EpisodeRepository.getEpisodes` 注释写「缺/过期再拉」，实际 `cached.isNotEmpty() && !forceRefresh` **永不过期**（`:25`），`forceRefresh` 无任何调用方；`EpisodeDao.deleteOlderThan`（`EpisodeDao.kt:22`）同为死代码。

### P0-4 竞态与卡死

- `SubjectSearchViewModel.doRefreshTrending` 的 `finally { isLoadingTrending = false }`（`:485-487`）对**过期请求**同样执行；`requestId` 只保护结果写入 → 新请求进行中时旧请求把转圈关掉（下拉指示器闪烁/提前消失）。
- 同一方法无 single-flight，可从下拉、`onRetry`、`setTrendingMode`、`setType`、`refreshTrendingWithFilters`、`setPersonSearch` 六路并发进入，仅部分路径 cancel。
- Steam 分支在刷新协程内**同步等待** `autoBindNewSteamToBangumi`（`:316, 944-1017`）：100 条按 8/批串行 13 批、每条 `withTimeout(2000)` 包一次 Bangumi 搜索 → **最坏 ~26 秒**才更新 UI；另对 100 个 appId 串行 `getBoundSubjectIdByAppId`（`:283-287`）＝100 次串行 DB 查询。
- `SubjectDetailViewModel.loadSubject:182-184`：`if (fresh != null) { … isRefreshing = false }` —— `fresh == null` 时 **`isRefreshing` 永远为 true**（详情页进度条卡死）。
- `StatsViewModel.fetchBroadcastSchedule()` 可从 init / `switchMonth` / 手动刷新三路并发；`inflightSeasonal` 只防同月重复、不防并发写。

### P1-5 进前台不刷新

全仓无 `ProcessLifecycleOwner` / `ON_START` 观察者（`MainActivity.kt:133-140` 的 `LifecycleResumeEffect` 只用于恢复桌面图标）。
App 长期驻留后台回到前台后，趋势、放送、详情全靠手动下拉。

### P1-6「自动同步」是死开关

`AppSettings.webDavAutoSync`（`AppSettings.kt:117`）与 `bangumiAutoSync`（`:131`）在设置页有开关（`SyncBackupSettingsScreen.kt:142-143`）、
有 DataStore 读写、有备份导入导出（`BackupManager.kt:284,292,462,472`），但**全仓没有任何消费方**。用户打开后什么都不会发生。

### P1-7 无退避、失败全静默

除 `SteamRepository.lastChartError`（`:192-195`）外，刷新失败一律 `runCatching {}` 静默（`NirikoApplication.kt:66,78,95,98` 等）。
失败后下一次触发可立即重打接口，没有退避梯，用户也看不到原因。

### P2-8 无陈旧度表达

只有「下拉」，没有「上次更新时间」、没有 stale 提示、没有「强制绕过缓存」与「用缓存」的区分。

---

## 四、参考实现（已核实源码）

| 来源 | 可移植的做法 | 证据 |
| --- | --- | --- |
| **AniShelf**（Swift/SwiftUI 项目，仅思路可搬） | `SyncGate`＝single-flight + waiter 搭车 + 结束后补跑 + 冲突挂起；**分离 `lastAttemptDate` / `lastSuccessfulSyncDate` / `retryState`** 并持久化；**diff 写**（只写内容变化的行）；**didSave 驱动 UI + deferred-depth 计数器**（N 次写 → 1 次刷新）；退避梯 `[30,60,120,300]` + 3 次 300s，且**退避截止时间压制后续防抖**；429 尊重 `Retry-After` | `LibrarySyncCoordinator+Pipeline.swift:245-287`；`LibraryPreferences.swift:83-100`；`LibraryMetadataRefreshWriter.swift:78-90`；`ModelContextSaveObserver.swift`；`LibrarySyncScheduler.swift:41-48,116-157`；`RedirectingHTTPClient.swift:38-86` |
| **Kazumi**（Flutter） | `AsyncSingleFlight`（并发调用共享同一 Future）+ `AsyncSerialQueue`（FIFO 串行化所有远端写）；**新鲜度窗口 + 显式绕过**；**存储变更流驱动的 300ms 防抖刷新**；损坏数据**隔离而非失败**；临时文件 + rename 原子发布 | `lib/utils/async_single_flight.dart`；`lib/utils/async_serial_queue.dart`；`lib/services/sync/webdav.dart:29-35,70-72`；`lib/plugins/plugins_controller.dart:210-214`；`lib/pages/my/my_controller.dart:47-98` |
| **Bangumi-master**（React Native） | 刷新**状态机** `Idle/HeaderRefreshing/FooterRefreshing/EmptyData/NoMoreData` + **4 秒看门狗**强制释放转圈（且仅当仍处于「刷新中」时释放，避免误改已完成态）+ 挂载守卫 + 触底**重入锁** | `src/components/list-view/hooks/useRefreshState.ts:19-136` |

---

## 五、目标架构

```
       触发源                         编排层                        执行层
 ┌──────────────┐          ┌────────────────────────┐   ┌──────────────────┐
 │ 冷启动        │          │  RefreshCoordinator     │   │ Repository       │
 │ ON_START     │─────────▶│  · FreshnessPolicy 判定 │──▶│  · 远端拉取       │
 │ 下拉刷新      │          │  · AsyncSingleFlight    │   │  · SubjectWrite  │
 │ 进详情页      │          │  · AsyncSerialQueue     │   │    Gateway       │
 │ 自动同步开关  │          │  · FreshnessStore(持久) │   │    (批量/diff)   │
 └──────────────┘          │  · RetryLadder 退避     │   └──────────────────┘
                            │  · StateFlow 状态广播   │            │
                            └────────────────────────┘            ▼
                                                          Room(subjects/…)
                                                                  │
                                                          失效通知（合并后）
                                                                  ▼
                                                            UI / ViewModel
```

---

## 六、模块设计

### 模块 1 · 统一新鲜度与退避状态

- `data/refresh/FreshnessPolicy.kt`：按资源定义 `softTtl / hardTtl`。

  | 资源 | soft TTL | hard TTL | 说明 |
  | --- | --- | --- | --- |
  | 当季趋势 / 历史排名 | 5 min | 30 min | 下拉与进前台受 soft 约束，超出 hard 强制 |
  | 放送日历 | 30 min | 6 h | |
  | 季节 / 月度放送 | 6 h | 7 d | |
  | 作品详情 | 0 | 7 d | 保持「进页面即出缓存 + 后台刷新」体感 |
  | 剧集表 | 6 h | 7 d | 修复「永不过期」 |
  | Steam 扩展详情 | 30 min | 24 h | 沿用现值 |
  | Steam 排行榜 | 30 min | 24 h | |
  | Anitabi 取景地标 | 7 d | 30 d | 沿用现值 |

- `data/refresh/FreshnessStore.kt`：**独立 DataStore**（`niriko_refresh`，不进设置、不进备份），持久化每个资源 key 的
  `lastSuccessAt` / `lastAttemptAt` / `failureCount` / `backoffUntil`。参考 AniShelf 的双时间戳分离。
- 判定：`decide(lastSuccessAt, lastAttemptAt, backoffUntil, policy, force, now) -> FRESH | REVALIDATE | EXPIRED | BACKOFF`。

### 模块 2 · `RefreshCoordinator`（核心编排）

- `util/AsyncSingleFlight.kt`：同 key 并发调用共享同一个 `Deferred`（Kazumi 同款，Kotlin 实现）。
- `util/AsyncSerialQueue.kt`：FIFO 串行化（Kazumi 同款）。
- `data/refresh/RefreshCoordinator.kt`：`refresh(key, policy, force, block)`

  1. 命中 soft 且非 force → 直接返回，不发网络；
  2. 处于退避窗口且非 force → 返回 `Skipped(backoffUntil)`；
  3. single-flight 合并并发同 key 请求；
  4. 成功 → 记 `lastSuccessAt`、清退避；
  5. 失败 → `failureCount+1`，按 `[30s, 60s, 120s, 300s, 300s, 300s]` 记 `backoffUntil`（AniShelf 梯子）。

- 额外参考 AniShelf `SyncGate`：`waitForRunningPass()`（并发调用搭车）与 `consumeRerunRequest()`（刷新中又来请求 → 结束后补跑一次）。
- 广播 `StateFlow<Map<RefreshKey, RefreshState>>` 供 UI 显示「刷新中 / 上次更新 / 失败可重试」。

### 模块 3 · 消灭写放大（`SubjectWriteGateway`）

- 所有 subjects 落库统一走 `data/local/SubjectWriteGateway.kt`：
  1. **批量单事务**（沿用 `upsertAll`）；
  2. **内容 diff 写**：先按 id 批量读回，只写字段真正变化的行（AniShelf `LibraryMetadataRefreshWriter` 做法）；
  3. **合并窗口**：100ms 内的多次写请求合并为一次事务（AniShelf deferred-depth 计数器）。
- 改造调用方：`BroadcastFetcher.kt:70`、`SeasonalFetcher.kt:37,81`、`SteamRepository.fetchStoreTopSellers`（`:529-544`）。
- `CollectionViewModel`：把 `steamRepository.getSupplements()` 从 flow `map` 中移出为独立 `combine` 源并按 subjectId 集合 memo，避免每次 subjects 写入都重算列表。

### 模块 4 · 生命周期触发器

- 新增依赖 `androidx.lifecycle:lifecycle-process`，注册 `ProcessLifecycleOwner` `ON_START` / `ON_STOP`。
- `data/refresh/RefreshTriggers.kt`：`onForeground()` 串行、低优先级依次触发（全部经 `RefreshCoordinator`，TTL + single-flight + 退避自动生效）：
  放送日历 → 当季趋势 → 已绑定条目的 Steam 补充（有界批量）。
- `ON_STOP` 取消非必需的前台刷新任务。

### 模块 5 · 冷启动拆解

- `onCreate` 只保留本地任务（迁移、拼音回填）。
- `prefetchSteamChart` 移到 `ON_START` 之后，受 `FreshnessStore` 30min 节流 + 有网判断。
- `autoBindGames` 节流改持久化，并限量（每轮上限 N 条、有界并发）。

### 模块 6 · 修正具体缺陷

1. **`SubjectSearchViewModel`**：`finally` 仅在 `requestId` 仍最新时清 loading；裸 Boolean 换成 Bangumi-master 式 `RefreshState` 状态机 + 4s 看门狗；`refreshTrending()` 全路径 single-flight；`autoBindNewSteamToBangumi` 移出刷新关键路径（先出榜、绑定结果异步补）；新增 DAO `getBindingsByAppIds(ids)` 批量查询替代 100 次串行查库。
2. **`SubjectDetailViewModel`**：`isRefreshing` 用 `try/finally` 保证复位；`loadExtendedData` 加内存缓存（同 subjectId 5 分钟内不重复拉 14 个接口）。
3. **`StatsViewModel`**：`fetchBroadcastSchedule` 走 single-flight + 30min TTL；手动刷新同时清 `prefetchedSubjectIds` 与月度缓存；季节数据「逐条 getDetail 补集数」改有界并发 + 总量上限；`_seasonalAiringMap` 加 TTL 与淘汰。
4. **`SubjectRepository`**：删除或真正实现 `forceRefresh`；把 `clearRankingCache()` 接进下拉刷新；`getDetail` 加 soft TTL。
5. **`EpisodeRepository`**：加 6h TTL，并接上已有的 `EpisodeDao.deleteOlderThan` 做清理。
6. **README 第 78 行**与代码不符（称启动时批量跑 AniList 匹配，实际 `NirikoApplication.kt:166-168` 已在「阶段 G」移除），一并更正。

### 模块 7 · 接上「自动同步」死开关

- `webDavAutoSync` / `bangumiAutoSync` 接入 `ON_START` 触发器 + `RefreshCoordinator` 节流（WebDAV ≥15min、Bangumi ≥30min），
  并经 `AsyncSerialQueue` 串行化（`BangumiSyncManager.kt:56` 已有 `Mutex`，抽成公共工具）。
- 失败走退避梯不再静默；设置页显示「上次同步时间 / 下次可同步时间」。
- 冲突仍沿用现有 LWW / 优先级策略；自动路径只做上传/合并，不覆盖用户刚做的本地修改。

### 模块 8 · 刷新诊断（可选）

设置页隐藏入口展示各资源 key 的 `lastSuccess / lastAttempt / backoff / 最近错误`——项目已有 `SteamRepository.lastChartError` 之类诊断字段先例。

---

## 七、分期与验收

| 阶段 | 内容 | 验收标准 |
| --- | --- | --- |
| **P0** | 模块 2/3 基础设施 + 模块 6 全部 + 模块 5 持久化节流 | ① 下拉转圈不再被旧请求提前关闭；② 下拉刷新后 30s 内确实拿到新榜；③ 详情页进度条不再卡死；④ Steam 标签刷新不再阻塞 20s+；⑤ 冷启动远端请求数与 Room 写事务数显著下降 |
| **P1** | 模块 1/4 + 模块 3 全量 + 模块 7 | ① 回前台自动刷新且不重复打接口；② 自动同步开关真正生效并显示上次同步时间；③ 失败进入退避、界面可重试 |
| **P2** | 模块 8 + UI 陈旧度展示 + 手动「强制刷新」入口 | 可观测与用户可控 |

**验证手段**

- 单元测试（沿用 `data/calculator` 已有纯函数单测先例）：`FreshnessPolicy` 判定、`RetryLadder`、`AsyncSingleFlight` 并发合并、diff 写。
- `gradlew.bat testDebugUnitTest`、`gradlew.bat assembleDebug`。
- 真机：临时 OkHttp Interceptor + Room 写计数埋点，对比改造前后冷启动 30 秒内的**请求数**与**写事务数**（本项目无模拟器，见 `docs/performance-audit.md` 第 4 行）。

---

## 八、非目标与风险

**非目标**：不改 UI 视觉；不引入 DI 框架；不改 Room schema 版本（仅可能新增查询方法与索引）；不重构数据源插件链。

**风险**

- 统一 TTL 可能改变「进详情页必定拉远端」的既有体验 → 详情页 soft TTL 取 0，仅消除重复拉取。
- 自动同步与用户手动操作冲突 → 串行队列 + 自动路径只做上传/合并。
- 无模拟器，退避梯与看门狗需真机验证时长体感。

---

## 九、实施进度

| 模块 | 状态 | 落地内容 |
| --- | --- | --- |
| 方案文档 | ✅ | 本文档 |
| 模块 1 新鲜度/退避 | ✅ | `data/refresh/FreshnessPolicy.kt`（`RefreshResource` / `RefreshDecision` / `FreshnessSnapshot` / `RetryLadder` / `FreshnessDecider`）、`data/refresh/FreshnessStore.kt`（DataStore 持久化 + 内存实现） |
| 模块 2 编排器 | ✅ | `util/AsyncSingleFlight.kt`、`util/AsyncSerialQueue.kt`、`data/refresh/RefreshCoordinator.kt`、`data/refresh/RefreshKeys.kt` |
| 模块 3 写网关 | ✅ | `data/local/SubjectWriteGateway.kt` + `SubjectDao.getByIds`；已改造 BroadcastFetcher / SeasonalFetcher / fetchStoreTopSellers / fetchChartTopGameNames / DataSourceChain |
| 模块 4 生命周期 | ✅ | `lifecycle-process` 依赖、`data/refresh/AppForegroundSignals.kt`、`NirikoApplication` 的 `ProcessLifecycleOwner` 观察者（onStart 轻量预取 + onStop 取消） |
| 模块 5 冷启动 | ✅ | 两条远端链合并为一条串行链 + 3s 错峰 + 有网判断 + 持久化节流（移除进程内 `@Volatile` 节流） |
| 模块 6 缺陷修正 | ✅ | 见下「已修缺陷」 |
| 模块 7 自动同步 | ✅ | `webDavAutoSync`/`bangumiAutoSync` 接上 ON_START + 15min/30min 节流 + 退避；`SyncManager` 串行化；设置页显示上次同步/下次可重试；补上此前根本不存在的 Bangumi 自动同步开关 |
| 模块 8 诊断 | ✅ | `data/refresh/RefreshStatusLabels.kt`（纯函数文案）、`RefreshCommands.kt`（应用级强制刷新指令）、`ui/settings/pages/RefreshDiagnosticsScreen.kt`（设置 → 刷新诊断）、`FreshnessSource.resetAll()` |
| 陈旧度展示 | ✅ | 发现页趋势区「上次更新 X 分钟前」、统计页日历卡片底部同一行；相对时间每 15s 自走字 |
| 手动强制刷新入口 | ✅ | ① 诊断页「立即强制刷新应用级数据」（Steam 排行榜/自动匹配，绕过 TTL 与退避，并击穿仓储 30min 榜单缓存）；② 诊断页「清除全部刷新记录」；③ 发现页「点此强制刷新」；④ 统计页日历「刷新」（已经是 force） |

### 已修缺陷对照

| 缺陷 | 修法 |
| --- | --- |
| 趋势刷新 `finally` 无条件关转圈 → 旧请求关掉新请求的指示器 | `SubjectSearchViewModel`：仅当 `requestId == trendingRequestId` 时才关闭 |
| 趋势刷新可从 6 条路径并发触发 | `refreshTrending()` 改为唯一入口 + `trendingJob` 合并；另加 15s 看门狗兜底释放 |
| Steam 标签刷新被 Bangumi 匹配阻塞最坏 ~26s | 出榜与匹配解耦：先渲染榜单，匹配完成后**原地替换** |
| 榜单反查 100 次串行数据库查询 | 新增 `SteamDao.getBindingsByAppIds` / `SteamRepository.getBoundSubjectIdsByAppIds` |
| `SubjectRepository.forceRefresh` 声明后从未使用 | 删除（含 6 处调用点传参） |
| `clearRankingCache()` 全仓无调用方 → 下拉 30s 内仍是旧榜 | 接进 `refreshTrending()`，并给 `getRanking` 加 `forceRefresh` |
| `EpisodeRepository` 剧集永不过期 | 按 `EPISODES`(6h) 判定；`EpisodeEntity.lastSyncTime` 作新鲜度锚点；接上 `EpisodeDao.deleteOlderThan` 清理 |
| 详情页 `isRefreshing` 卡死 | `loadSubject` 用 `try/finally` 无条件复位 |
| 详情页每次打开重打 14 路接口 | `TtlCache`（5 分钟、跨 VM 实例共享）缓存扩展数据快照 |
| 统计页放送日历三路并发 | 单飞 + 30 分钟 TTL |
| `refreshBroadcastSchedule()` 实际什么都不会重拉 | 清 `prefetchedSubjectIds` / 月度缓存 / `inflightSeasonal` 后强制刷新 |
| 季节数据缓存永不失效 | 加 6 小时 TTL + 最多保留 6 个月 |
| 季节补集数串行几十次网络请求 | 有界并发（4）+ 单条 3s 超时 + 上限 24 条 |
| 冷启动 `@Volatile` 节流失效（每次冷启动全量重跑） | 改用持久化的 `RefreshCoordinator` 窗口 |
| 逐条 upsert 造成 Room 失效风暴 | `SubjectWriteGateway`：单事务 + 内容 diff |
| 进前台不刷新 | `ProcessLifecycleOwner` + `AppForegroundSignals` |
| `webDavAutoSync`/`bangumiAutoSync` 死开关 | 接上自动同步（下载合并 → 上传发布，串行化） |
| README 声称启动批量跑 AniList 匹配（代码已移除） | README 已更正 |

### 与方案的偏离（有意）

1. **`AsyncSingleFlight` 不做 AniShelf `SyncGate` 的「补跑一轮」**。
   `SyncGate` 补跑是因为它同步**本地写**（一轮 pass 从旧快照开始，期间的新修改必须补一轮才不丢）；
   本项目这里是**只读刷新**，并发请求要的就是「最新远端数据」，正在飞的那一次返回的就是最新数据，
   补跑只会让网络开销翻倍——恰好是要修的那个症状。
2. **`CollectionViewModel` 的 Steam 补充查询未搬出 flow `map`**。
   原计划把它挪成独立 `combine` 源；实施时判断根因在**写入侧**（每次 subjects 写入都触发失效），
   已由 `SubjectWriteGateway` 从源头消除。在此之上再加一层按 id 集合的 memo 会引入「Steam 数据更新后卡片不刷新」的风险，
   收益不抵风险，故不做。
3. **`lastSyncTime` 只用于导出，不参与 diff**。
   若参与，每次刷新都会推进时间戳 → diff 永远判定为「变了」，diff 写形同虚设。
4. **逐条目新鲜度不放进 `FreshnessStore`**。
   作品详情/剧集用数据库里已有的逐行 `lastSyncTime`，避免把上千个 id 灌进 DataStore。

### P2 的取舍

- **陈旧度只做到页面级，没做卡片级**。卡片级 stale 标记需要把新鲜度按 subjectId 下发到每个列表项，
  而列表项的封面/评分本来就会随 subjects 写入自动更新；逐卡标记的视觉噪音大于信息量。页面级（趋势区 / 日历）
  覆盖了真正「整块数据可能是旧的」的两个场景。
- **「强制刷新」没有做成全局开关**。它分散在四处入口，每处的「强制」语义都不同：
  趋势要点破榜单微缓存、应用级资源要绕过仓储 30min 缓存与编排器窗口。合成一个按钮反而会让人误以为它刷新一切。

### 尚未处理（已发现，不属于本次范围）

- `SubjectDetailViewModel` 的 `subjectId` 是构造期常量；`rematchPlaceholder()` 升级后条目 id 会变，
  但 VM 仍按旧 id 查询（旧的占位行已被删除）→ 升级后可能落到「无法加载作品详情」。属于占位条目升级流程的既有问题。
