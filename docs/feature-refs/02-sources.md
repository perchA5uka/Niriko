# 2 数据源与聚合（18 条 v1 + 5 条新增）

> 一句话结论：本域最值得先做的是 **2.1 统一能力声明**（P0，主链 10 个 boolean 与游戏链 5 个 boolean 各说各话，链上真正被检查的只有 4 个）、**2.3 源顺序持久化修复**（P0，设置页能切源、重启就丢，且 `DataSourceChain` 在 lazy 初始化时拿的是列表快照，切了也不生效），以及 **2.5 任意源 × 全能力自检**（P1，现有 `data/probe/` 只覆盖 5 个灰色通道与 Bangumi 三条搜索路，能力矩阵完全不可见）。
>
> 2.2 多阶段选择器与 2.5 自检都给出纯函数签名与真值表，可直接落成 JVM 单测；2.12 的匹配解释已有源码级基础（`MatchScorer.reasons`），只差 UI 渲染。

## 2.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 2.1 | 能力声明单一真相 | 部分 | **P0** | M | animeko `MediaSource` + `supportsBrowsing`；mihon `Source.supportsLatest` |
| 2.2 | 多阶段自动选择 | 部分 | **P0** | M | animeko `MediaSelectorFilterSortAlgorithm`（过滤→排序→偏好→选择 + 排除原因） |
| 2.3 | 用户偏好排序源 | 部分 | **P0** | S | mihon `pinnedSources`/`lastUsedSource`；Kotatsu `SourcesSortOrder` |
| 2.4 | 记忆手动选择并快速切换 | 无 | P1 | S | mihon `lastUsedSource`；animeko `mediaSourcePrecedence` |
| 2.5 | 数据源自检 / 连通性探测 | 部分 | P1 | M | animeko `checkConnection` + 数据源测试面板；本项目 `ChannelProbe` |
| 2.6 | 数据源配置可视化编辑 | 无 | P2 | M | animeko `updateConfig` + JSON 源测试器；mihon `ConfigurableSource` |
| 2.7 | 权威评分多源对照 | 部分 | P1 | S | ryot「类型→provider」分派表；本项目 `RatingSourceRegistry` |
| 2.8 | 原生分与换算分并存 | 部分 | P1 | S | mihon `get10PointScore`/`indexToScore`；本项目 `ExternalRating` |
| 2.9 | 票数门槛筛选 | 部分 | P2 | S | 本项目 `DiscoveryFeed.MIN_RATING_TOTAL` + `BrowseFilter.ratingCount` |
| 2.10 | 跨源状态映射 | 部分 | P1 | M | mihon `Tracker.getStatusList`/`setRemoteStatus` |
| 2.11 | 跨源绑定与候选确认 | 部分 | P1 | M | mihon `Tracker.search`/`bind`；本项目 `ExternalMatchService` |
| 2.12 | 匹配理由与置信度展示 | 部分 | P1 | S | 本项目 `MatchScorer`；fuzzywuzzy `PartialRatio`；animeko `MatchMetadata.similarity` |
| 2.13 | 手动粘贴 ID / 链接绑定 | 部分 | P1 | S | 本项目 `MatchQueryBuilder.extractInfoboxId` + `AniListIdParser`；mihon `Tracker.bind` |
| 2.14 | 数据源失败隔离 | 部分 | P1 | S | animeko 每源 `MediaSourceFetchState`；本项目 `PluginFailureTracker` |
| 2.15 | 源级限流闸门 | 部分 | P1 | S | Yamtrack `LimiterAdapter` 按 host 限速；animeko 限流自动重试预算 |
| 2.16 | 源内 ID 直连路由 | 部分 | P1 | M | animeko 全局唯一 `mediaSourceId` + 实例直路由；mihon `Source.id` |
| 2.17 | 源健康度可视化 | 无 | P1 | M | animeko 每源状态/结果展示；本项目 `FreshnessSnapshot` |
| 2.18 | 源变更告警 | 无 | P1 | S | animeko `MediaSourceFetchState` 状态机；Yamtrack `ProviderAPIError` |
| 2.19 | 来源信封（provenance） | 无 · 新提出 | P1 | S | ryot metadata 的 `source` 字段；本项目 `sourceKey` |
| 2.20 | 字段级多源合并策略 | 无 · 新提出 | P1 | M | 本项目 `mergeBiliSupplementary` 的单字段补丁范式 |
| 2.21 | 源请求预算仪表 | 无 · 新提出 | P2 | S | Yamtrack `LimiterAdapter`；本项目 `SourceRateLimiter` |
| 2.22 | 最小可用能力集巡检 | 无 · 新提出 | P1 | S | animeko `MediaSourceInfo.isSpecial` + 能力声明 |
| 2.23 | 源级 kill switch | 无 · 新提出 | P2 | S | mihon `disabledSources` / `ToggleSource` |

## 2.1 逐条详解

### 2.1 能力声明单一真相

- **现状**｜部分：两套并行声明。主链 `plugin/DataSourceCapabilities.kt` 有 10 个 boolean，但 `DataSourceChain` 真正检查的只有 `supportsCalendar`、`supportsSubjectsByMonth`、`supportsSubjectsInDateRange`、`supportsEpisodes` 四个（`plugin/DataSourceChain.kt:332/349/356/361`）；`supportsSearchWithTotal`、`supportsCharacters/Staff/Detail`、`supportsRatingDistribution` 只被声明、从不读取。游戏链 `data/remote/game/GameDataSource.kt` 另有一套 `GameDataSourceCapabilities`（5 个 boolean，默认全 false），由 `GameDataSourceRegistry.searchable()/detailSources()` 读取；两套类型无共同父接口。此外 `supportsSubjectsInDateRange = supportsSubjectsByMonth` 是**派生默认值**，Bangumi 只显式写 `supportsSubjectsByMonth = true`（`NirikoApplication.kt:407-411`），于是「能按月查」被隐式解释成「能按日期段查」。
- **提升**｜(a) 用户可见：设置页「数据源」每行显示能力徽章，当前源不支持的操作在 UI 上直接置灰并给出原因，而不是点了之后静默空列表；(b) 工程上省掉 `DataSourceChain` 里 8 处「先试再说」的 try/catch，能力不足的源不再被发无效请求，也省掉游戏链与主链各写一份的注册表过滤。
- **原理**｜用一个枚举 + `EnumSet` 取代 boolean 群：
  ```kotlin
  enum class Capability { SEARCH, SEARCH_TOTAL, DETAIL, EPISODES, CALENDAR,
      SUBJECTS_BY_MONTH, SUBJECTS_IN_DATE_RANGE, RATING_DISTRIBUTION, BROWSE }
  interface SourceDescriptor { val id: String; val caps: Set<Capability> }
  fun capabilityMatrix(sources: List<SourceDescriptor>): Map<Capability, List<String>>  // 纯函数
  ```
  派生能力必须**显式**：`SUPPORTS_IN_DATE_RANGE = BangumiCaps + SUBJECTS_BY_MONTH` 写成工厂函数 `capsOfBangumi()`，禁止用 data class 默认参数继承另一个 flag。链上调度改成 `when (op) { Op.Calendar -> plugins.filter { CAP_CALENDAR in it.caps } ... }`；游戏链的 `GameDataSource` 实现 `SourceDescriptor` 适配器即可复用同一套矩阵，但 `BROWSE` 只在游戏链可用。
- **效果**｜设置→数据源：AniList 行显示「搜索 ✓ / 详情 ✓ / 剧集 ✓ / 日历 ✗ / 月度 ✗ / 评分分布 ✗」；点日历页不会向 AniList 发请求；`getEpisodes` 对无剧集能力的源返回明确的 `UnsupportedOperationException`（现在只对首个插件判断一次）。能力矩阵单测覆盖 2×9 个格子。
- **参考**｜`open-ani/animeko@main:datasource/api/src/commonMain/kotlin/source/MediaSource.kt` [码] `MediaSource.supportsBrowsing`（默认 false，注释说明「运行时无法区分『支持但没搜到』与『不支持』」）、`MediaSourceInfo`（displayName/tier/isSpecial）；`mihonapp/mihon@main:domain/src/main/java/tachiyomi/domain/source/model/Source.kt` [码] `supportsLatest`；`FuzzyGrim/Yamtrack@0.26.3:src/app/providers/services.py` [码] 反例：用 `media_type -> lambda` 的硬编码分派表，没有能力声明，新增类型必须改三处。
- **成本/风险**｜M｜最大风险是「主链能做的语义」与「游戏链只做补充查询」不同，强行合并会把 `getDetail` 的语义塞给游戏源（它们只返回 GameItemDetail）。建议第一版只统一**值对象与矩阵**，不统一接口。
- **优先级**｜P0

### 2.2 多阶段自动选择

- **现状**｜部分：只有「串行 + 首个非空」单段策略——`DataSourceChain.search` 按 `plugins` 顺序 for 循环，`result.isNotEmpty()` 即 return（`plugin/DataSourceChain.kt:83-100`）；没有打分、没有按能力预筛、没有每源超时、没有「上次成功源优先」。唯一例外是建议链：`searchSuggestions` 并行 + `withTimeout(2000)`（同文件 109-143）。
- **提升**｜(a) 用户可见：同样关键词下首条结果更快出现（慢源不再阻塞，2s 超时），失败源自动跳过并在结果页标注「已跳过 1 个源」；(b) 工程上把「选哪个源」从 500 行命令式链里抽出来，变成可穷举单测的纯函数，未来加 IGDB/RAWG 只需注册一个 descriptor。
- **原理**｜纯函数 + 四阶段（能力→健康→偏好→预算），入参与出参固定：
  ```kotlin
  data class SourceCandidate(val id: String, val caps: Set<Capability>, val priority: Int,
      val lastSuccessAt: Long?, val consecutiveFailures: Int, val backoffUntil: Long?, val p95Ms: Long)
  data class SourceRequest(val op: Capability, val keyword: String, val type: Int?, val nsfw: Boolean?)
  enum class SkipReason { NO_CAPABILITY, BACKOFF, DISABLED, ZERO_RESULT_LAST_TIME, BUDGET }
  data class Selection(val ordered: List<String>, val skipped: Map<String, SkipReason>, val reason: String)
  fun select(candidates: List<SourceCandidate>, req: SourceRequest, policy: SelectorPolicy, nowMs: Long): Selection
  ```
  算法：① 过滤 `op in caps`；② 过滤 `backoffUntil > now` 与 `disabled`；③ 按 `policy.preferredIds + priority` 稳定排序；④ `maxAttempts` 截断；⑤ 同分用 `id` 字典序保证确定性。执行器拿 `ordered` 并发/串行发请求，首个非空即返回；全部空返回空，全部失败抛 `AllPluginsFailedException`（保留现有语义）。
  真值表（请求 `SEARCH_TOTAL`，keyword 非空）：

  | 候选 | 能力 | 健康 | 用户偏好 | 结果 |
  | --- | --- | --- | --- | --- |
  | bangumi | 有 | 正常 | 第 1 | **选中**，attempt=1 |
  | anilist | 有 | 正常 | 第 2 | 备选 |
  | vndb(game) | 无 SEARCH_TOTAL | — | — | `NO_CAPABILITY` 跳过 |
  | steam(game) | 无 SEARCH_TOTAL | 退避中 | — | `BACKOFF` 跳过 |
  | bangumi | 有 | 退避中 | 第 1 | 跳过，anilist 升为第 1 |
  | 全部 | — | 全失败 | — | 抛 `AllPluginsFailedException` |
  | 全部 | — | 全部空 | — | 返回 `emptyList()`（不是失败） |

  边界：`attempts=0` 不算全失败（沿用 `PluginFailureTracker.allFailed` 的语义，`DataSourceChain.kt:495`）。
- **效果**｜搜索页首屏骨架最多等 `maxAttempts × perSourceTimeout`；失败源在结果计数旁显示「已跳过：steam（退避中）」；诊断页可展开每个候选的 `SkipReason`；切换排序/筛选导致的重复请求仍由 `AsyncSingleFlight` 合并。
- **参考**｜`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt` [码] 四阶段与 `MediaExclusionReason`（`EpisodeMismatch`/`SubjectNameMismatch`/`MediaWithoutSubtitle`/`FromSeriesSeason`）；`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorContext.kt` [码] `mediaSourcePrecedence: List<String>?` 与 `mediaSourceTiers`；`docs/contributing/code/media/media-selector.md` [README] 明确「过滤→排序→偏好→选择」及排除原因可展示。
- **成本/风险**｜M｜风险：默认顺序必须保持 Bangumi→AniList，否则老用户会看到结果源变化；「零结果也继续试下一个」的语义要保留（`searchWithTotal` 现在就是这样），不能因为打分就只试一个。
- **优先级**｜P0

### 2.3 用户偏好排序源

- **现状**｜部分：`PluginManager` 有 `moveToTop/moveUp/moveDown`（`plugin/PluginManager.kt:34-53`），设置页点一下会调 `setActiveDataSourceId` 并写 DataStore（`viewmodel/SettingsViewModel.kt:247-252`）。但 **① `NirikoApplication.dataSourceChain` 在 lazy 构造时传入 `pluginManager.plugins`，而该 getter 返回 `toList()` 快照**（`NirikoApplication.kt:430-436`），之后 `moveToTop` 只改 PluginManager 内部列表，链仍按旧顺序发请求；**② 冷启动没有任何地方读取 `settings.activeDataSourceId` 并回放 `moveToTop`**（只在 settings / backup 的读写路径里出现，没有启动回放点），重启即回到 Bangumi 第一；③ `PluginManager` 本身没有测试（`app/src/test/java/com/otakup/niriko/plugin/` 下只有 `HistoricalDataSourceChainTest` 与 `PluginFailureTrackerTest`，没有 `PluginManagerTest`）。
- **提升**｜(a) 用户可见：设置里选 AniList 后，搜索/详情立即按 AniList 优先，重启后仍保持，设置行显示「✓ 当前」；(b) 工程上省掉一次「设置值与实际行为不一致」的隐性 bug，补 4 组边界测试。
- **原理**｜把「顺序」变成唯一真相：`PluginManager` 内部持 `MutableStateFlow<List<String>> order`，`plugins` 从 order 派生；`DataSourceChain` 构造参数改成 `pluginsProvider: () -> List<DataSourcePlugin>`（或直接传 `PluginManager`），每次请求读当前值。DataStore 键 `source_order`（JSON 数组）与 `active_data_source_id` 同时写；应用启动时在 `NirikoApplication` 首个协程里 `settingsDataStore.settings.first()` 后按 `source_order` 重排，缺失/未知 id 忽略。迁移旧值：若只有 `activeDataSourceId`，把它移到首位。
- **效果**｜设置 → 数据源：点 AniList 后该行变「✓ 当前」，下方小字「搜索与详情优先使用」；杀进程重进仍保持；搜索首条来自 AniList（AniList 无结果时才回 Bangumi）；`PluginManagerTest` 覆盖 moveToTop 已在首位、未知 id、重复 register、顺序持久化回放。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` [码] `pinnedSources: Set<String>`、`lastUsedSource: Long`；`domain/src/main/java/tachiyomi/domain/source/model/Source.kt` [码] `pin` 与 `isUsedLast`；`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/explore/data/SourcesSortOrder.kt` [码] `ALPHABETIC/POPULARITY/MANUAL/LAST_USED` 四序；`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceManager.kt` [码] `partiallyReorderInstances(instanceIds)`。
- **成本/风险**｜S｜坑在 DataStore 首次读取是异步的，链可能在回放前已发请求；需要 `Application.onCreate` 里阻塞到第一个 settings（现有 `settingsDataStore.settings` 是 Flow，可 `runBlocking` 一次或加 `startupLatch`）。
- **优先级**｜P0

### 2.4 记忆手动选择并快速切换

- **现状**｜无：没有「上次手选的源」这一维度。`setActiveDataSourceId` 是全局切换（且 2.3 失效），搜索失败后用户只能去设置页切源；结果页没有「换源重试」入口。
- **提升**｜(a) 用户可见：搜索空态/错误态出现「用 AniList 重试」；点过之后该 (操作, 类型) 下次自动优先 AniList，并在搜索框下显示「上次用 AniList」；(b) 工程上省掉用户「设置→数据源→切换→返回搜索」四步，也避免全局切源影响详情页。
- **原理**｜DataStore 存 `manual_source_memory: Map<String, String>`，key = `"${op}:${type ?: "all"}"`（如 `SEARCH:2`），value = sourceId，最多 20 条 LRU。`SelectorPolicy` 增加 `preferredId: String?`，在 2.2 的第③阶段插到队首但**不改变**全局顺序；「换源重试」把当前源加入 `excludeIds` 后再跑一次。记忆写入只在用户显式点击时发生，自动兜底不写（避免一次网络抖动永久改偏好）。
- **效果**｜搜索「巨人」超时 → 错误卡出现主按钮「用 AniList 重试」+ 次按钮「诊断」；点主按钮立即重查，成功后下次同类型搜索 AniList 排第一；设置→数据源显示「上次手动：AniList（搜索·动画）」；清空数据源设置时一并清空记忆。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` [码] `lastUsedSource`；`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorContext.kt` [码] `mediaSourcePrecedence`；`docs/contributing/code/media/media-selector.md` [README] 「只用用户偏好的源，全部筛不到再 fallback 任意源」。
- **成本/风险**｜S｜记忆 key 含 type，切类型会「忘记」上次选择；需在空态文案说明，或退化为按 op 记忆。
- **优先级**｜P1

### 2.5 数据源自检 / 连通性探测

- **现状**｜部分：`data/probe/` 是一套**灰色通道**框架——`ChannelProbe` 接口 + `ProbeEndpoint` + `ProbeState{OK,BLOCKED,UNREACHABLE,SKIPPED}` + `ProbeResult(httpStatus, elapsedMs, bodyPreview(400B), finalUrl, itemCount)`（`data/probe/ChannelProbe.kt:82-144`），注册表只有 5 个探针/7 个端点（豆瓣 rexxar/frodo/HTML、AniList、Jikan、AniDB、Bangumi 旧版）。`SearchChainProbe` 另测 Bangumi 三条路，`countItems` 兼容 data/list/results 三种形状。没有「任意源 × 全能力」矩阵，没有导出，"200 但 0 条" 仍记为 `OK`。
- **提升**｜(a) 用户可见：设置→数据源自检显示一张「源 × 能力」表格，单元格是「可用 · HTTP 200 · 12 条 · 380ms」/「被拒 403」/「不可达」/「未实现」，一键复制报告发反馈；(b) 工程上把「哪个源哪项能力坏了」从猜变成一次测量，且每个解析器都能用自检响应固化成 fixture 单测。
- **原理**｜两个纯函数 + 一个新状态：
  ```kotlin
  enum class ProbeState { OK, EMPTY, BLOCKED, UNREACHABLE, TIMEOUT, SKIPPED }
  data class ProbeCell(val sourceId: String, val capability: Capability, val state: ProbeState,
      val httpStatus: Int?, val itemCount: Int?, val elapsedMs: Long, val error: String?)
  fun planProbes(sources: List<SourceDescriptor>, only: Set<String> = emptySet()): List<ProbeSpec> // 纯函数：源×能力展开
  fun summarize(cells: List<ProbeCell>): ProbeReport // 纯函数：按源聚合可用率、按能力聚合缺口
  ```
  真值表（单格判定，`expectedNonEmpty` 由能力决定）：

  | HTTP | 解析条数 | 能力声明 | 判定 |
  | --- | --- | --- | --- |
  | 200 | >0 | 支持 | OK |
  | 200 | 0 | 支持 | **EMPTY**（接口通但无内容/被吞） |
  | 200 | null | 支持 | BLOCKED（响应不是可识别结构） |
  | 401/403/400 | — | 支持 | BLOCKED |
  | 302→login/sec | — | 支持 | BLOCKED（`finalUrl` 不同） |
  | IOException | — | 支持 | UNREACHABLE |
  | 超时 | — | 支持 | TIMEOUT |
  | 任意 | — | 不支持 | SKIPPED（原因=NO_CAPABILITY） |
  | 任意 | — | 灰度关闭 | SKIPPED（原因=DISABLED） |

  执行器顺序跑（沿用 `GrayChannelRunner` 的「不并发打同一站」原则），每格用干净的 OkHttp（不挂 baseUrl/token 拦截器，同 `ProbeHttp.client`），超时 8/10s。报告格式：`Niriko 数据源自检 v1 · 2026-10-17 18:40` + 每源一段 + JSON 附件。
- **效果**｜表格 6 源 × 8 能力；空单元格标灰「未实现」；`EMPTY` 用黄色而不是绿色；顶部汇总「3/6 源可用；缺失能力：日历（仅 Bangumi）、评分分布（仅 Bangumi）」；「复制报告」写入剪贴板；设置页保留「上次自检时间」。
- **参考**｜`open-ani/animeko@main:datasource/api/src/commonMain/kotlin/source/MediaSource.kt` [码] `suspend fun checkConnection(): ConnectionStatus`（注释「这会在设置的数据源测试中使用」）；`datasource/api/src/commonMain/kotlin/source/ConnectionStatus.kt` [码] `SUCCESS/FAILED`；`docs/contributing/code/media/media-source.md` [README] JSON 源测试面板「只在请求变化时重新请求，改 JSONPath 用缓存重解析」；`FuzzyGrim/Yamtrack@0.26.3:src/app/providers/services.py` [码] `ProviderAPIError` 保留 `status_code` 与 provider label；本项目 `data/probe/ChannelProbe.kt` [码]。
- **成本/风险**｜M｜风险：全能力探测会对 Bangumi 发真实写请求吗？不会，但会打详情/日历，注意只在用户点「自检」时跑，默认不后台跑（现有 `ChannelProbe` 注释已定此原则）；豆瓣等灰通道仍要遵守开关。
- **优先级**｜P1

### 2.6 数据源配置可视化编辑

- **现状**｜无：设置页只有「Bangumi 接口（官方/反代）」「Steam API Key」「Bangumi OAuth」等固定项（`ui/settings/pages/DataSourceSettingsScreen.kt`），没有按源编辑 UA/超时/端点的表单，也不支持新增源；源注册硬编码在 `NirikoApplication.pluginManager` 与 `gameDataSourceRegistry`。
- **提升**｜(a) 用户可见：每个源一张可展开卡片，能改「接口端点 / 自定义 UA / 超时 / 是否启用」，改完点「测试」就地显示结果；(b) 工程上把散落在 Client 构造里的 baseUrl/UA 收敛成 `SourceConfig`，反代切换不再写死一处。
- **原理**｜按 2.1 的 `SourceDescriptor.id` 存 `source_configs: Map<String, SourceConfig>`（kotlinx.serialization JSON 存 DataStore）。`SourceConfig` 只放**参数**不放逻辑：`endpointOverride`、`userAgent`、`connectTimeoutMs`、`enabled`。Client 构造时读 config 建 OkHttp；「测试」按钮调 2.5 的 `planProbes(only = setOf(id))`。**不引入用户自定义源/插件市场**（与本项目定位冲突），只做「已注册源的可配置化」。
- **效果**｜设置→数据源→Bangumi 卡片展开：端点下拉（官方/反代/自定义 URL）、UA 输入框、超时滑杆、启用开关；底部「测试连接」显示「200 · 12 条 · 420ms」；保存后返回搜索立即生效；非法 URL 就地红字，不写 DataStore。
- **参考**｜`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceManager.kt` [码] `addInstance/updateConfig/setEnabled/removeInstance`；`docs/contributing/code/media/media-source.md` [README] 订阅 JSON 编解码与兼容写法。
- **成本/风险**｜M｜风险：配置一旦可改，支持成本上升（用户填错反代导致全站失败）。默认值必须锁官方，自定义项折叠在「高级」里，并在失败诊断里回显当前端点。
- **优先级**｜P2

### 2.7 权威评分多源对照

- **现状**｜部分：`RatingSourceRegistry` 注册 12 个源（TMDb/OMDb/AniList/Steam 好评率/IGDB/VNDB/RAWG/OpenCritic/MusicBrainz/Discogs/Google Books/Open Library），按 `SubjectType` 派发，未配 key 的源 `isAvailable=false` 自动隐藏；详情页 `ui/subject/ExternalRatingSection.kt` 并列展示。缺的是「同一作品多个源都返回时如何排序/标注可信度」，以及无数据时只说「暂无」不说为什么（未配置 key / 未绑定外部 ID / 该源无此条目）。
- **提升**｜(a) 用户可见：评分卡每行带「来源 · 票数 · 取数时间」，无数据的源折叠成「3 个源无数据（未配置/未绑定）」，展开可看原因；(b) 工程上把「为什么没显示」从 UI 猜变成 `RatingAvailability` 纯函数，省掉详情页多次 `runCatching` 的静默吞错。
- **原理**｜`data class RatingAvailability(sourceId, available: Boolean, reason: UnavailableReason)`，`reason` 枚举 `NO_KEY / NO_BINDING / NOT_FOR_TYPE / FETCH_FAILED / NOT_FOUND`；由 `RatingSourceRegistry.available(type, keys)` + `SubjectExternalIdEntity` + 抓取结果合成纯函数 `fun availability(...): List<RatingAvailability>`。展示按 `(hasScore desc, ratingTotal desc, registryOrder)` 排序。
- **效果**｜评分卡「权威评分」区：有分源按票数降序，票数 &lt;10 标「样本少」；无分源折叠为一行「3 源无数据 ›」，点开逐条原因；下拉刷新只重试 `FETCH_FAILED` 的源。
- **参考**｜`FuzzyGrim/Yamtrack@0.26.3:src/app/providers/services.py` [码] `get_media_metadata` 的 `media_type -> provider` 分派与 `ProviderAPIError`；`open-ani/animeko@main:datasource/api/src/commonMain/kotlin/source/MediaSource.kt` [码] `MediaSourceInfo(displayName, websiteUrl, isSpecial, tier)`；v1 的 R7/R11 只有 README 级，本轮未找到评分聚合源码，标 [README]。
- **成本/风险**｜S｜风险低；不要在 UI 里把不同标尺的分数直接平均。
- **优先级**｜P1

### 2.8 原生分与换算分并存

- **现状**｜部分：`data/remote/rating/ExternalRating.kt` 同时存 `nativeScore`、`scoreMax`、`score`（10 分制），`score` 由 `RatingInsights.toTenPoint(nativeScore, scoreMax)` 换算；UI 多处显示原生值与 10 分制，但**没有标注哪一行是原生、哪一行是换算**，跨源排序时读者容易把 87/100 当成 8.7/10 的同一标尺。
- **提升**｜(a) 用户可见：每行固定格式「8.7 / 10（原生 87 / 100 · 1.2 万票）」，鼠标/长按出说明「换算分仅用于同屏排序」；(b) 工程上把换算规则收敛到一个 `ScoreScale`，杜绝某源把 5 分制当 10 分制传入。
- **原理**｜`data class ScoreScale(val max: Float, val kind: Kind)` + `fun display(r: ExternalRating): ScoreDisplay(nativeText, tenText, scaleNote)` 纯函数；`ExternalRating` 增加 `scaleVerified: Boolean`，未验证的源（如 OpenCritic 百分制）在 UI 加「≈」。所有排序键统一用 `score`，原始值只用于展示。
- **效果**｜评分卡每行两段数字 + 来源徽标；「≈」标记换算值；同屏排序按 10 分制稳定；分享卡只输出 10 分制并注明「换算」。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/track/Tracker.kt` [码] `get10PointScore`/`indexToScore` 与 TODO「Store all scores as 10 point in the future maybe?」；本项目 `ExternalRating.kt` [码]。
- **成本/风险**｜S｜风险：不同源的 10 分制本身口径不同（媒体均分 vs 用户均分），UI 必须写清「不可直接平均」。
- **优先级**｜P1

### 2.9 票数门槛筛选

- **现状**｜部分：存在**两套**门槛。发现页「当季热门」在本地按 `DiscoveryFeed.MIN_RATING_TOTAL = 100` 过滤（`data/discover/DiscoveryFeed.kt:41`，`ratingTotal == null` 一并挡掉）；「历史排名」把 `ratingCountRange` 塞进 Bangumi `filter.rating_count` 走服务端（`data/discover/BrowseFilter.kt:165` 注释「挡掉 10 分 3 人」）。单集评分另有 0/10/50 票的图表筛选（`EpisodeRatingSections.kt`），与作品门槛不是同一件事。没有用户可调的全局阈值。
- **提升**｜(a) 用户可见：发现页筛选器增加「票数 ≥ 全部 / 10 / 50 / 100」，切换后列表即时变化并显示「已过滤 12 条低票」；(b) 工程上把三处阈值收敛成一个 `VoteThreshold`，当季热门也能读用户设置，且不再出现「历史排名可调、当季热门写死 100」。
- **原理**｜`@JvmInline value class MinVotes(val value: Int)`；`DiscoveryFeed.Params.minRatingTotal` 由调用方从 `BrowseFilter.minVotes` 注入（默认 100）；服务端能表达的（Bangumi `filter.rating_count = [">=N"]`）优先服务端，不能表达的（当季热门候选池来自 /calendar）本地过滤。统计「过滤条数」放进 `DiscoveryFeed.Result.rejectedByQuality`（已有字段）。
- **效果**｜当季热门工具行出现「票数」chip；选 ≥50 后列表从 30 条变 26 条，底部小字「已过滤 4 条（&lt;50 票）」；空态给「降低门槛」出口；历史排名的 rating_count 与本地门槛共用同一设置值。
- **参考**｜本项目 `DiscoveryFeed.kt` [码] 与 `BrowseFilter.kt` [码]；`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt` [码] 的「过滤即带原因」范式（`MediaExclusionReason`）。
- **成本/风险**｜S｜风险：服务端 `rating_count` 只对 Bangumi 有效，AniList 兜底结果没有票数字段，需按「未知=不参与」处理而不是全挡。
- **优先级**｜P2

### 2.10 跨源状态映射

- **现状**｜部分：映射散落——`plugin/bilibili/BilibiliSyncModels.kt`（B 站追番状态→本地）、`data/sync/bangumi/`（本地↔Bangumi 数值）、`data/remote/steam/SteamLibraryImporter.kt` 的 `inferStatus(playtimeMinutes)`（游玩时长→状态推断）。三处各写一份 `when`，没有统一的「源状态 → 本项目 5 态」表，也没有反向映射的单测。
- **提升**｜(a) 用户可见：导入预览每行显示「B站『在看』→ 在看」「Steam 已玩 0 分钟 → 想看」，冲突时标黄让用户选；(b) 工程上新增第 4 个源时只加一行表项，导入/同步共用一套转换，省掉 3 份 `when` 与各自的边角测试。
- **原理**｜`data class StatusMapping(val source: String, val remote: String, val local: WatchStatus, val lossy: Boolean)`；`object StatusMapper { fun toLocal(source, remote): WatchStatus?; fun toRemote(source, local): String? }`，表用 `Map<Pair<String,String>, WatchStatus>`，未知返回 null 由 UI 显示「无法识别（保持默认）」。反向映射多个远端值对应同一本地态时，选「最不具侵入性」的那个（如 `在看` → B 站 `在看`，不选 `想看`）。
- **效果**｜B 站导入预览表新增「映射」列；Steam 导入预览显示推断依据「0 分钟 → 想看」；Bangumi 同步失败时错误信息带「本地『抛弃』在远端没有对应值」。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/track/Tracker.kt` [码] `getStatusList()/getStatus()/getReadingStatus()/setRemoteStatus()`（每个 tracker 自己映射，接口只要求能列出）；`app/src/main/java/eu/kanade/tachiyomi/data/track/model/TrackSearch.kt` [码] 候选模型；`FuzzyGrim/Yamtrack@0.26.3:src/integrations/imports/` [推断·目录] 每个来源一个 importer（mal/anilist/trakt/steam…），映射写在各自的 importer 里。
- **成本/风险**｜M｜风险：Bangumi 的 5 态与 B 站「在看/追番/看过/抛弃」不是一一对应，必须允许 `lossy` 并在 UI 标注，不能强行双射。
- **优先级**｜P1

### 2.11 跨源绑定与候选确认

- **现状**｜部分：`ExternalMatchService` 实现 L1 infobox ID → L2 多查询串 → L3 多字段打分（`data/match/ExternalMatchService.kt`），但**只注册了 VNDB 一个 matcher**（`SubjectDetailViewModel.kt:2035-2038`）；TMDb 走 `TmdbRatingSource` 自己的候选链路，AniList 走 `AniListRatingSource`，豆瓣另有一套。绑定表 4 张（`vndb_bindings`/`anilist_bindings`/`steam_bindings`/`subject_external_ids`），写入策略不一致。
- **提升**｜(a) 用户可见：所有外部源的候选卡片长得一样：封面 + 标题 + 年份 + 「匹配度 87%」+ 理由 chips + 「绑定」按钮；绑定成功后详情页对应区块立即出现数据；(b) 工程上把「搜候选/打分/确认/写库」四步收敛成一个 `BindingFlow`，新增源只实现 `ProviderMatcher`。
- **原理**｜统一 `MatchCandidate(provider, externalId, title, subtitle, imageUrl, confidence, reasons, matchedQuery, source{INFOBOX,QUERY,MANUAL})`（已有）。写库策略 `AutoBindPolicy(provider): enum { NEVER, INFOBOX_ONLY, HIGH_CONFIDENCE }`：VNDB/TMDb/AniList/豆瓣 = NEVER（只出候选），Steam = 按 appid 精确绑定，infobox 命中可 `INFOBOX_ONLY` 自动。候选查询用 `ExternalMatchService.match(provider, subject, infobox, maxQueries, limit)`；手动搜索用 `searchByQuery`，不过滤低分。
- **效果**｜详情页「外部信息源」区块：未绑定显示候选横滑（最多 5 张），每张有匹配度与理由；点绑定 → snackbar「已绑定 VNDB：v12345」→ 区块原地刷新；「搜索其他候选」打开输入框，结果不因低分隐藏；绑定后可「解除绑定」。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/track/Tracker.kt` [码] `suspend fun search(query): List<TrackSearch>` + `suspend fun bind(track, hasReadChapters)`；`app/src/main/java/eu/kanade/tachiyomi/data/track/model/TrackSearch.kt` [码] 候选自带 title/cover/summary/total_chapters；本项目 `ExternalMatchService.kt` [码]。
- **成本/风险**｜M｜风险：不同源候选字段差异大（TMDb 有季、VNDB 有标题集合），统一卡片必须允许 `subtitle` 自由文本，别做过度结构化。
- **优先级**｜P1

### 2.12 匹配理由与置信度展示

- **现状**｜部分：`MatchScorer.score` 已经产出 `ScoredMatch(score, reasons, exactTitle, matchedCandidateTitle)`，打分构成明确（标题 0.78、年份 +0.12/+0.06/-0.25、集数 +0.06/+0.03、平台 +0.04、完全相等下限 0.95），`MatchCandidate.reasons` 也一路带到 UI；但 VNDB/AniList 的候选卡没有把 reasons 全量渲染（VNDB 走统一卡片，TMDb/AniList 各有自己的候选组件，具体渲染点待核），负向理由（年份冲突）在低分候选上常被折叠。
- **提升**｜(a) 用户可见：候选卡显示「标题 92% · 年份差 1 年 · 集数一致」，冲突时红字「年份冲突 -0.25」；置信度用 3 段色条（&lt;0.6 红 / 0.6–0.8 黄 / ≥0.8 绿）；(b) 工程上把理由从「调试字符串」变成结构化 `MatchReason`，可排序、可本地化、可单测。
- **原理**｜`sealed interface MatchReason { data class Title(val pct:Int); data class YearEqual(val y:Int); data class YearConflict(val a:Int,val b:Int,val penalty:Float); data class EpisodeEqual(val n:Int); data class PlatformOverlap(val p:List<String>); object ExactTitle }`；`MatchScorer` 返回 `List<MatchReason>`，UI 按 `weight desc` 渲染 chips；分数条用 `ConfidenceBand` 纯函数分档。保留现有 `reasons: List<String>` 作为 `toString()` 兼容层，避免改 UI 调用点。
- **效果**｜候选卡：绿色「87%」+ chips「标题 92%」「年份一致 2024」「集数一致 12」；低分候选展开后看到「年份冲突 2019 vs 2024 -0.25」；手动搜索的低分候选也有理由，不再只显示一个数字。
- **参考**｜本项目 `MatchScorer` [码]；`xdrop/fuzzywuzzy@master:src/me/xdrop/fuzzywuzzy/ratios/PartialRatio.java` [码] 用 matching blocks 取最优子串相似度、`FuzzySearch.tokenSortPartialRatio` [码] 处理词序，可作为「为什么是 92%」的另一种算法口径；`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt` [码] `MatchMetadata(subjectMatchKind, episodeMatchKind, similarity)` 也是「匹配元数据随候选走」。
- **成本/风险**｜S｜风险：理由文案过长会撑爆候选卡，最多渲染 3 条 + 「更多」；不要显示原始小数（0.8732）而应显示百分比整数。
- **优先级**｜P1

### 2.13 手动粘贴 ID / 链接绑定

- **现状**｜部分：`MatchQueryBuilder.extractInfoboxId` 支持纯 id 与 URL 两种写法、按 provider 的 `infoboxKeys`/`infoboxIdPattern` 提取（不做猜测）；`MatchCandidate.SOURCE_INFOBOX` 置信度 1.0。手填入参侧，AniList 有 `util/AniListIdParser.kt` 专门解析 `media-123` 并给出可见反馈（历史上 `toLongOrNull()` 恒 null）；VNDB matcher 实现了 `byId`（`data/match/VndbProviderMatcher.kt:135`）。但 TMDb/豆瓣没有统一的「粘贴 ID/链接」入口，AniList 有 `util/AniListIdParser.kt` 的可见反馈（入口具体位置待核）。
- **提升**｜(a) 用户可见：每个外部源区块底部都有「粘贴 ID / 链接」；粘 `https://vndb.org/v12345`、`media-123`、`tt1234567` 都能识别，识别不出时说明「这个链接属于 X，不是 VNDB」；(b) 工程上解析收敛成一个 `ExternalIdParser`，新增源只加正则，不再各写 `removePrefix`。
- **原理**｜`data class ParsedExternalId(provider: String, externalId: String, matchedUrl: Boolean)`；`object ExternalIdParser { val patterns: Map<String, Regex>; fun parse(raw: String, expected: String?): Result }` —— 先按 URL host 判 provider（vndb.org/themoviedb.org/anilist.co/douban.com），再按正则抠 id；纯数字输入若 `expected != null` 只认该 provider，否则让用户选源。解析成功后调 `ProviderMatcher.byId` 取候选，命中即显示「已识别：VNDB v12345 · 标题」。
- **效果**｜粘贴框：输入即时解析，合法显示绿色「识别为 VNDB v12345」+ 候选预览；host 不匹配显示红色「这是 TMDb 链接，请到 TMDb 区块粘贴」；无网络时给「仅按 ID 绑定（不校验标题）」。
- **参考**｜本项目 `MatchQueryBuilder.extractInfoboxId` [码]、`AniListIdParser.kt` [码]、`VndbProviderMatcher.byId` [码]；`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/track/Tracker.kt` [码] `bind(track)` 是「候选确认后写库」的统一入口。
- **成本/风险**｜S｜风险：短链/带追踪参数的 URL 会解析失败；只支持白名单 host，不做重定向跟随（避免 SSRF/隐私问题）。
- **优先级**｜P1

### 2.14 数据源失败隔离

- **现状**｜部分：`DataSourceChain.searchWithTotal` 用 `PluginFailureTracker` 记录每源失败，全部失败抛 `AllPluginsFailedException`，部分成功照常返回（`DataSourceChain.kt:205-230`）；`search` 只 `Log.w` 不记录。详情/角色/人物等方法对每个插件 `catch (_: Exception) {}` 后回退缓存，**失败与"该源没有数据"在 UI 上不可区分**；没有统一的失败摘要。
- **提升**｜(a) 用户可见：详情页顶部出现一条可折叠的「部分数据源失败：Bangumi（超时）· 正在用缓存」；搜索页显示「已跳过 1 个源」；全部失败时错误页列出每源原因而不是一句「网络失败」；(b) 工程上省掉「每个方法各写一份 catch」的重复，失败信息沿一个 `OperationReport` 向上带。
- **原理**｜`data class SourceFailure(val sourceId: String, val op: Capability, val reason: String, val httpStatus: Int?)`；`data class OperationReport(val usedSourceId: String?, val failures: List<SourceFailure>, val fromCache: Boolean)`。`DataSourceChain` 每个方法返回 `Result<T>` 或把 report 写进 `SourceHealthStore`；UI 只读 `SourceHealthStore` 的 `lastFailures`。关键：区分「全部失败」（抛错）与「部分失败 + 缓存兜底」（200 + 标注）。
- **效果**｜详情页顶部黄色条「Bangumi 请求失败（超时），以下内容来自本地缓存 · 重试」；搜索页结果计数旁「已跳过：AniList（401）」；诊断页按源列最近失败；缓存兜底不再静默。
- **参考**｜`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt` [码] 每源独立 `state`（Idle/Working/Completed/Disabled/RateLimited）与 `restart()`；`FuzzyGrim/Yamtrack@0.26.3:src/app/providers/services.py` [码] `ProviderAPIError(provider, status_code, message)` 保留 HTTP 状态；本项目 `PluginFailureTracker` [码]。
- **成本/风险**｜S｜风险：详情页缓存兜底是**设计行为**（README 明确「优先展示本地结果」），标注不能变成每页常驻红条；按 `lastSuccessAt` 超过 TTL 才显示。
- **优先级**｜P1

### 2.15 源级限流闸门

- **现状**｜部分：`util/SourceRateLimiter.kt` 是纯逻辑（窗口内突发到上限、超限后按 `window/max` 匀速排队），规则硬编码 3 个 host（IGDB 4/s、MusicBrainz 1/s、VNDB 200/5min）；`SourceRateLimits` 提供挂起/阻塞两个入口。实际接入只有两处：`data/remote/rating/RatingHttp.kt:77`（评分源）与 `data/remote/vndb/VndbApiClient.kt:39`（VNDB 拦截器）；TMDb 另有自己的 `rateLimitInterceptor`（`TmdbClient.kt:138`），Bangumi/AniList/Steam/Anitabi 完全没接。
- **提升**｜(a) 用户可见：诊断页显示「MusicBrainz 限流中 · 还需 600ms」，批量刷新不再突然 503；VNDB 大量绑定时候选请求排队而不是失败；(b) 工程上把限流从「各 Client 自觉」变成「全局 OkHttp 拦截器 + 规则表」，新增源只加一行 host 规则。
- **原理**｜统一 `RateRule(hostSuffix, maxRequests, windowMs)`，匹配 host 及其子域；`acquire(host, now)` 返回等待毫秒（已在 2.15 实现，纯逻辑可单测）。在**所有** OkHttpClient 的 `addInterceptor` 里调 `SourceRateLimits.awaitHostBlocking(host)`（拦截器跑在 IO 线程，可阻塞）；对 429 读 `Retry-After`，写回 `SourceHealthStore.backoffUntil`。规则从硬编码改成 `Map<String, RateRule>`，可被 2.6 的配置覆盖。
- **效果**｜设置→刷新诊断新增「限流」列：当前等待中的源 + 剩余毫秒；MusicBrainz 连续请求时日志出现「rate limit wait 1000ms」而非 503；VNDB 批量绑定进度条不再卡在失败重试。
- **参考**｜`FuzzyGrim/Yamtrack@0.26.3:src/app/providers/services.py` [码] `LimiterSession(per_second=5)` + 按 host `LimiterAdapter(per_minute=30/85/3/2/190/20/50)` + 429 `Retry-After` 后递归重试；`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt` [码] `MediaSourceFetchState.RateLimited(retryAt)` + `rateLimitAutoRestartBudget=1`；本项目 `SourceRateLimiter` [码]。
- **成本/风险**｜S｜风险：全局拦截器对同一个 OkHttpClient 的并发请求会排队，可能把「详情页 6 个并行请求」变成串行；只对命中规则的三类 host 生效，其余放行。
- **优先级**｜P1

### 2.16 源内 ID 直连路由

- **现状**｜部分：`DataSourceChain.getDetail` 先读 `subjectDao.getById(id).sourceId` 精确路由到插件，失败才顺序尝试（`DataSourceChain.kt:235-298`）；游戏源条目按 `sourceKey` 拆 `sourceId` 后用 `GameDataSourceRegistry.get(sourceId)` 刷新详情；但 `getCharacters/getStaff/getEpisodes/getRatingDistribution` 只按 `sourceId` 找一次，失败/为 null 就退回 `plugins.firstOrNull()`；`getCharacterDetail/getPersonDetail` 完全没有 sourceId，永远从 Bangumi 开始试。
- **提升**｜(a) 用户可见：从 AniList 条目点进角色/人物页，直接打 AniList，不再先等 Bangumi 超时；Steam/VNDB 条目的角色/关联加载不再走 Bangumi 再回退；(b) 工程上省掉「首源失败 → 回退 → 再失败」的两段等待，人物页首屏少 1–2 个 RTT。
- **原理**｜在 `subject_external_ids` 之外新增通用 `entity_source_refs(entityType, localId, sourceId, sourceLocalId)`（或给 `characters`/`persons` 实体加 `sourceId` 列，迁移 v33）。所有 `getXxxDetail` 先查 ref 表：有 ref → 只调该源，失败返回缓存/空；无 ref → 按 2.2 的 `select` 顺序尝试。跨源跳转时（如 AniList 角色 → Bangumi 角色）只允许「同 id 类型 + 已绑定」直连，不做标题猜测。
- **效果**｜AniList 条目详情 → 角色卡 → 角色页：请求只发 AniList，页面顶部小字「来自 AniList」；Bangumi 条目仍走 Bangumi；无 ref 的老数据自动补建一次。
- **参考**｜`open-ani/animeko@main:datasource/api/src/commonMain/kotlin/source/MediaSource.kt` [码] `mediaSourceId` 全局唯一、用于偏好与缓存来源；`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceManager.kt` [码] 按 instance 直路由；`mihonapp/mihon@main:domain/src/main/java/tachiyomi/domain/source/model/Source.kt` [码] `id: Long` 作为稳定身份。
- **成本/风险**｜M｜风险：实体表加列要迁移 + 回填，老数据没有 sourceId 时不能假定 Bangumi（AniList 落库的角色可能被误标）。先写 ref 表、保留 null 语义，比直接改实体列安全。
- **优先级**｜P1

### 2.17 源健康度可视化

- **现状**｜无：`data/refresh/FreshnessSnapshot` 有 `failureCount/backoffUntil/lastError/lastSuccessAt`，但它是**按 RefreshKey（资源）** 记的，不是按源；`RefreshDiagnosticsScreen` 列出「各数据源新鲜度、失败次数、退避剩余与最近错误」（README 166 行），无法回答「Bangumi 最近成功率/延迟」。`SearchChainProbe` 只显示单次 `elapsedMs`，不落库。
- **提升**｜(a) 用户可见：设置→刷新诊断新增「按源」表：源 / 成功率 / p50 / p95 / 最近错误 / 下次可重试；(b) 工程上把「源坏了」与「某个资源 TTL 到了」分开，排障从看 10 个 RefreshKey 变成看 6 个源。
- **原理**｜`data class SourceMetric(sourceId, op, ok: Boolean, elapsedMs: Long, at: Long)`，写入内存环形缓冲（每源每 op 最近 50 条）+ 每日聚合落 DataStore（成功率/计数/p50/p95，保留 14 天）。采集点放在 `DataSourceChain` 的每个 `try/catch` 前后（`measureTimeMillis`），或统一包一层 `suspend fun <T> traced(sourceId, op, block)`。聚合 `fun percentile(sorted: LongArray, p: Double): Long` 纯函数。
- **效果**｜诊断页「Bangumi · 搜索：98.2% · p50 320ms · p95 1.4s · 最近错误 502（2 分钟前）」；低于 90% 的行标黄；点行展开最近 10 次；「清除统计」按钮。
- **参考**｜`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt` [码] 每源 `MediaSourceFetchState` 与 `results/state` 流；`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceManager.kt` [码] `mediaSourceTiersFlow` 按实例聚合；本项目 `FreshnessSnapshot` [码]。
- **成本/风险**｜M｜风险：采集点很多，容易漏（尤其 `searchSuggestions`、游戏源旁路）；先只在 `searchWithTotal/getDetail/getCalendar` 三个主路径埋，别一次全铺。
- **优先级**｜P1

### 2.18 源变更告警

- **现状**｜无：源连续失败只在详情页静默回退缓存；搜索全失败抛 `AllPluginsFailedException` 但只显示一次错误，没有「连续 N 次」判定，也没有「接口改版/字段缺失」这一类告警。`RefreshDiagnostics` 只在用户主动进入时展示。
- **提升**｜(a) 用户可见：某源连续 3 次失败或解析异常时，发现页顶部出现一条可关闭的横幅「Bangumi 可能不可用（连续 3 次失败）· 查看诊断」，而不是等用户发现列表空；(b) 工程上把「网络失败」与「解析失败」分开计数，接口改版能更早被发现。
- **原理**｜`SourceAlertPolicy` 纯函数：输入最近 `N=5` 次 `SourceMetric`，输出 `SourceAlert(sourceId, severity, reason, since)`。规则：连续失败 ≥3 → WARNING；解析异常（`SerializationException`/`JsonDecodingException`）≥2 → CRITICAL（接口改版信号）；一旦成功清零。告警存 DataStore（按源 + 日期去重，每天最多一条横幅），UI 在发现页/诊断页读。
- **效果**｜发现页顶部横幅文案「Bangumi 连续 3 次失败（HTTP 502）· 查看诊断 / 忽略今天」；诊断页对应源行红点；恢复成功后横幅自动消失，诊断页保留历史。
- **参考**｜`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt` [码] 状态机把 `RepositoryNetworkException/RepositoryRateLimitedException/RepositoryRequestError` 分开处理；`FuzzyGrim/Yamtrack@0.26.3:src/app/providers/services.py` [码] `ProviderAPIError` 带 `status_code` 与 provider 名。
- **成本/风险**｜S｜风险：误报（用户断网也会连续失败）。判定前先看全局网络状态（`ConnectivityManager`），断网时不发源级告警。
- **优先级**｜P1

## 2.9 本域新增候选（发散）

### 2.19 来源信封（provenance）

- **现状**｜无 · 新提出：`subjects` 已有 `sourceId`/`sourceKey`/`lastSyncTime`，但没有「这条记录的哪个字段来自哪个源、什么时候取的、置信度多少」。`mergeBiliSupplementary` 只对 B 站三列做字段级补丁，其他字段一律远端覆盖。**为何是 Niriko**：本项目无服务端，字段只能靠客户端 diff 写与本地合并，来源必须随字段一起存。
- **提升**｜(a) 用户可见：详情页每个区块角落显示「来自 Bangumi · 2 分钟前」/「来自 Steam · 昨天」，冲突字段显示「Steam 说 ¥98，Bangumi 无价格」；(b) 工程上刷新时能按字段决定是否覆盖（价格 1h、简介 7d、封面 30d），省掉「一次全量覆盖」导致的手工改封面被冲掉。
- **原理**｜`data class FieldProvenance(sourceId, fetchedAt, confidence)`，存 `subject_field_provenance(subjectId, field, sourceId, fetchedAt, confidence)`（或 JSON 列）。读取时 `fun resolve(field, candidates: List<FieldValue>): FieldValue` 按 `confidence × freshness` 选；写入时只更新「值变了」的字段（复用 `SubjectWriteGateway` 的 diff 思路）。
- **效果**｜详情页「简介」标题右侧小字「Bangumi · 刚刚」；「价格」小字「Steam · 12 分钟前」；手动改封面后 30 天内刷新不再被 TMDb 覆盖。
- **参考**｜`IgnisDa/ryot@main:crates/services/collection/src/recommendation_operations.rs` [码] 里每个 metadata 都带 `source` 并据此排除（`MEDIA_SOURCES_WITHOUT_RECOMMENDATIONS`），说明「字段/条目级来源」是跨源合并的前置；本项目 `SubjectEntity.sourceKey` [码]。
- **成本/风险**｜S｜风险：字段级 provenance 是 1:N 表，23 张表再 +1，迁移与备份都要带；先只覆盖 5 个易冲突字段（封面/简介/评分/价格/标签）。
- **优先级**｜P1

### 2.20 字段级多源合并策略

- **现状**｜无 · 新提出：同一作品多源时的合并是「详情接口整体覆盖 + B 站三列特判」，没有声明式的「哪个字段优先哪个源」。Steam 价格被 Bangumi 详情刷新覆盖的场景只能靠 `mergeBiliSupplementary` 这种一次性补丁避免。**为何是 Niriko**：主源是 Bangumi、Steam/VNDB 只作补充，字段冲突是本地常态而不是服务端聚合问题。
- **提升**｜(a) 用户可见：同一条目在详情页同时显示 Bangumi 简介 + Steam 价格 + VNDB 评分 + TMDb 海报，且刷新任一个源不会清掉其他源字段；(b) 工程上新增源时只写一张 `FieldPriority` 表，不再改 merge 函数。
- **原理**｜`data class FieldRule(field: Field, order: List<String>, mode: MERGE|REPLACE, maxAgeMs: Long)`；`fun merge(base: SubjectEntity, incoming: Map<String, FieldValue>, rules: List<FieldRule>, now: Long): SubjectEntity` 纯函数。例：`PRICE -> [steam] REPLACE maxAge 1h`；`SUMMARY -> [bangumi, anilist, vndb] MERGE maxAge 7d`；`COVER -> [manual, bangumi, tmdb]`（manual 永远第一）。测试用表驱动，每个字段一组。
- **效果**｜刷新 Bangumi 后 Steam 价格仍在；手动换的封面不被 TMDb 冲掉；VNDB 评分与 Bangumi 评分并排而不是互相覆盖；合并结果可单测（给定 3 份输入 → 期望快照）。
- **参考**｜本项目 `SubjectRepository.mergeBiliSupplementary` [码]（现有唯一字段级补丁）；`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt` [码] 的分阶段「规则表 + 纯函数」写法可复用为 merge 的骨架。
- **成本/风险**｜M｜风险：规则一多会变成隐式 DSL；第一版只做 5 个字段 + 3 条规则，别做通用表达式引擎。
- **优先级**｜P1

### 2.21 源请求预算仪表

- **现状**｜无 · 新提出：`SourceRateLimiter` 只保证不超限，用户看不到「今天已经打了多少 / 还剩多少」。VNDB 200 次/5 分钟、IGDB 4/s 这类额度对批量绑定/导入是硬约束，但没有任何 UI 提示。**为何是 Niriko**：纯客户端没有服务端配额池，只能靠本地预算避免 VNDB/IGDB 封禁。
- **提升**｜(a) 用户可见：设置→刷新诊断显示「VNDB 今日 132/200（窗口剩余 3m12s）」「IGDB 当前 2/4 每秒」，批量绑定前给出「预计需要 18 次请求，额度够」；(b) 工程上批量任务能按预算主动分批，而不是撞限流后失败重试。
- **原理**｜`SourceRateLimiter` 增加只读 `fun snapshot(host, now): Budget(used, max, windowResetAt)`；`SourceHealthStore` 按自然日累计 `requestCount`（只统计命中规则的 host）。批量任务入口用 `fun planBatches(items, budget, perItem=1): List<Int>` 纯函数切批。UI 只读快照，不参与限流决策。
- **效果**｜诊断页「额度」卡：进度条 + 重置倒计时；批量绑定弹窗显示「将分 2 批发送，预计 4 分钟」；额度不足时禁用按钮并说明「VNDB 窗口剩余 12 次」。
- **参考**｜`FuzzyGrim/Yamtrack@0.26.3:src/app/providers/services.py` [码] 按 host 的 `LimiterAdapter` 规则表；本项目 `SourceRateLimiter.acquire` [码] 已有窗口队列，加一个只读查询即可。
- **成本/风险**｜S｜风险：纯客户端本地计数会被系统杀进程清零；只作为估算，不作为「服务端真实额度」承诺。
- **优先级**｜P2

### 2.22 最小可用能力集巡检

- **现状**｜无 · 新提出：应用启动/进设置时不检查「日历至少要有 1 个 supportsCalendar 源」「详情至少要有 1 个 supportsDetail 源」。如果用户把唯一支持日历的 Bangumi 禁用，日历页只会空，没有任何解释。**为何是 Niriko**：无 DI、无插件市场，源是硬编码注册的，用户禁用唯一源后的能力缺口只能靠本地巡检发现。
- **提升**｜(a) 用户可见：设置页顶部「能力覆盖」卡：日历 1/1、评分分布 1/1、剧集 1/2…；缺能力时点进去显示「没有启用的源支持日历，日历页将为空」；(b) 工程上把「功能是否可用」从运行期空列表提前到配置期，省掉用户报「日历坏了」的排障。
- **原理**｜`data class Coverage(capability, enabledSources: List<String>, minRequired: Int)`；`fun audit(sources: List<SourceDescriptor>, required: Set<Capability>): List<Coverage>` 纯函数。启动时跑一次，结果 `MutableStateFlow` 给设置页；`minRequired` 来自功能注册表（日历=1、搜索=1、详情=1、剧集=1）。
- **效果**｜设置→数据源顶部 3 行覆盖状态；禁用 Bangumi 后立即变红「日历 0/1」并给「重新启用 Bangumi」按钮；日历页空态复用同一文案。
- **参考**｜`open-ani/animeko@main:datasource/api/src/commonMain/kotlin/source/MediaSource.kt` [码] `MediaSourceInfo.isSpecial` 与 `supportsBrowsing` 让 UI 能区分「特殊源/仅浏览源」；2.1 的能力矩阵是它的前置。
- **成本/风险**｜S｜风险：要求集写死会阻碍「用户只用 AniList」的合法配置；缺能力只提示不阻止。
- **优先级**｜P1

### 2.23 源级 kill switch

- **现状**｜无 · 新提出：源失效时只能发版改代码或让用户切主源；没有「临时禁用某源」的本地开关（`DataSourceSettingsScreen` 只有「点击切换」，没有禁用）。**为何是 Niriko**：发版周期长而源随时失效，本地 kill switch 是纯客户端唯一的止损手段。
- **提升**｜(a) 用户可见：设置→数据源每行右侧开关，关掉后该源不参与任何链（搜索/详情/日历/评分），并显示「已禁用」；批量导入时可临时关掉慢源；(b) 工程上排障时先关源定位，比改代码/清数据快。
- **原理**｜DataStore `disabled_sources: Set<String>`；`PluginManager` 与 `GameDataSourceRegistry` 在 `plugins`/`sources` getter 过滤 disabled；2.2 的 `select` 把 disabled 归入 `SkipReason.DISABLED`。UI 开关写 DataStore，链每次读取（配合 2.3 的 live provider）。与 2.22 联动：禁用后覆盖卡即时更新。
- **效果**｜开关关闭 AniList → 搜索只出 Bangumi，结果页标注「AniList 已禁用」；重启保持；「恢复默认」一键全部启用。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` [码] `disabledSources: Set<String>`；`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceManager.kt` [码] `setEnabled(instanceId, enabled)` 与批量版。
- **成本/风险**｜S｜风险：用户禁用唯一源后全站空；用 2.22 的覆盖卡兜底提示。
- **优先级**｜P2

## 2.A 本域新增参考库

| 仓库 | 许可 | star（截至 2026-10） | 语言 | 为何与本项目相关 | 已验证文件（均可 GET 200） |
| --- | --- | --- | --- | --- | --- |
| open-ani/animeko | AGPL-3.0 | 未取到 | Kotlin/Compose MP | 数据源能力声明、四阶段选择器、排除原因、每源 fetch 状态机与限流重试，是本域最完整的范式 | `datasource/api/src/commonMain/kotlin/source/MediaSource.kt`、`datasource/api/src/commonMain/kotlin/source/ConnectionStatus.kt`、`datasource/api/src/commonMain/kotlin/source/Browse.kt`、`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/MediaSelectorContext.kt`、`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt`、`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaSourceManager.kt`、`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt`、`docs/contributing/code/media/media-source.md`、`docs/contributing/code/media/media-selector.md` |
| mihonapp/mihon | Apache-2.0 | 24k | Kotlin | 追踪服务注册/搜索/绑定抽象、源 pin/lastUsed/disabled、内容警告分级，适合 2.3/2.4/2.10/2.11/2.23 | `app/src/main/java/eu/kanade/tachiyomi/data/track/Tracker.kt`、`app/src/main/java/eu/kanade/tachiyomi/data/track/model/TrackSearch.kt`、`domain/src/main/java/tachiyomi/domain/source/model/Source.kt`、`domain/src/main/java/tachiyomi/domain/source/model/Pin.kt`、`app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` |
| KotatsuApp/Kotatsu | GPL-3.0 | 8.9k | Kotlin | 多站点源管理、源排序四模式、搜索建议分类、筛选预设持久化，适合 2.3/3.1/3.9/3.16 | `app/src/main/kotlin/org/koitharu/kotatsu/explore/data/SourcesSortOrder.kt`、`app/src/main/kotlin/org/koitharu/kotatsu/core/model/MangaSourceInfo.kt`、`app/src/main/kotlin/org/koitharu/kotatsu/core/prefs/SearchSuggestionType.kt`、`app/src/main/kotlin/org/koitharu/kotatsu/filter/data/SavedFiltersRepository.kt` |
| FuzzyGrim/Yamtrack | 未取到 | 未取到 | Python/Django | 「作品类型 → provider」分派表 + 按 host 限流 + 带状态码的 ProviderAPIError，是 2.1/2.7/2.15 的反例/正例参照 | `src/app/providers/services.py`、`src/integrations/imports/`（目录） |
| IgnisDa/ryot | GPL-3.0 | 未取到 | Rust + React | 推荐聚合（随机抽样 + suggestions 并集 + HashSet 去重 + 缓存）、筛选预设（lastUsed/isEqual/long-press 删除），适合 2.7/3.3/3.8/3.9 | `crates/services/collection/src/recommendation_operations.rs`、`apps/frontend/app/lib/hooks/filters/use-presets.ts`、`apps/frontend/app/components/common/filter-presets.tsx` |
| xdrop/fuzzywuzzy | GPL-2.0 | 未取到 | Java | partial ratio / token sort 的匹配打分实现，可作为 2.12 的第二种口径（注意 GPL 许可，不能直接拷进闭源 APK） | `src/me/xdrop/fuzzywuzzy/ratios/PartialRatio.java`、`src/me/xdrop/fuzzywuzzy/FuzzySearch.java` |

## 2.B 自检

- 汇总表 23 行 = 详解 18 条（2.1–2.18）+ 新增 5 条（2.19–2.23）。✅
- 每条参考都带 `owner/repo@branch:path` 的**完整路径**（本文件不使用省略路径；超大仓库 animeko 虽然无法列树，但每个文件都单独 GET 验证过 200）。
- 证据等级：[码] 的文件本轮都实际 fetch 并读到关键符号；[README] 为项目文档/README；未读源码的仅 2.7 的 v1 R7/R11，已显式标 [README]。
- star 取数：mihon 24k、Kotatsu 8.9k 于 2026-10 取到；animeko/ryot/Yamtrack/fuzzywuzzy 的 shields 端点本轮超时，写「未取到」，未编造。
- 待核：① 2.17 的采集点是否覆盖 `searchSuggestions` 与游戏源旁路，需实现时逐一对 `DataSourceChain` 方法核；② 2.20 的字段级合并与现有 `SubjectWriteGateway` diff 写的交互需在实现前画一次写入时序。
