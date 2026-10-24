# 3 发现与检索（17 条 v1 + 5 条新增）

> 一句话结论：本域最值得做的是 **3.1 建议链路的排序与可观测**（P1，机制已相当完整——本地前缀/拼音 + 历史 + 远程单飞复用 + 四态文案，缺的是每阶段耗时与稳定排序）、**3.9 检索条件保存/复用**（P1，`BrowseFilter` 已有 15 个字段却只活在内存，Kotatsu/ryot 都有现成范式）、以及 **3.12 拼音键覆盖面**（P1，现在只吃 title/titleCN，别名与标签完全不进 key，且 pinyin4j 的 316KB 体积与首调初始化是可量化的）。
>
> 3.3–3.5 是同一件事的三段：推荐来源（本地共现 → 外源 similar）、排除已看、给出理由。建议一次做成 `Recommendation(subject, reasons, score)`，避免三次改详情页。3.7（榜单快照）本轮没有找到源码级参考，原理给自建设计并明确标注。

## 3.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 3.1 | 搜索建议本地+远程混合 | 部分 | P1 | S | 本项目 `buildSuggestionItems/mergeSuggestions/suggestionStateOf`；Kotatsu `SearchSuggestionType` |
| 3.2 | 趋势 / 热搜区 | 部分 | P2 | M | 本项目 `TrendingMode`+`DiscoveryFeed`；Kotatsu `SourcesSortOrder` |
| 3.3 | 相似作品推荐 | 部分 | P1 | M | ryot `collection_recommendations` + provider suggestions |
| 3.4 | 推荐中隐藏已完成项 | 无 | P1 | S | mihon `hideInLibraryItems` + `globalSearchFilterState` |
| 3.5 | 推荐结果可解释 | 部分 | P1 | S | animeko `MediaExclusionReason` 的可解释排除范式 |
| 3.6 | 流媒体平台可用性 | 部分 | P2 | M | 本项目 23 个 `PortalTarget`；AniList `externalLinks`（[推断]） |
| 3.7 | 榜单历史快照对比 | 无 | P2 | M | 本轮未找到源码级参考；自建 `rank_snapshots`（见原理） |
| 3.8 | 检索结果与本地库的去重合并 | 部分 | P1 | L | ryot HashSet 并集；animeko 稳定 `mediaId` |
| 3.9 | 检索条件的保存与复用 | 无 | P1 | M | Kotatsu `SavedFiltersRepository`；ryot `use-presets` |
| 3.10 | 检索结果导出 | 无 | P2 | S | ryot `media_exports`；mihon `creators/` 目录 |
| 3.11 | 搜索空态引导 | 部分 | **不做**（v1.2.1 撤回） | — | 保持现有四态文案与诊断入口 |
| 3.12 | 拼音搜索覆盖面 | 部分 | P1 | S/M | 本项目 `PinyinSearch`；promeg/TinyPinyin 三路径（见原理） |
| 3.13 | 人物检索与作品联动 | 部分 | P2 | M | Kotatsu `SearchSuggestionType.AUTHORS`；本项目 `PersonSearchCard` |
| 3.14 | NSFW 检索策略透明化 | 部分 | P1 | S | mihon `ContentWarning` + `enabledContentWarnings` |
| 3.15 | 跨类型统一检索的排序口径 | 部分 | P1 | S | 本项目 `DiscoveryFeed.Params`；animeko 确定性 comparator |
| 3.16 | 检索历史管理 | 部分 | P2 | S | Kotatsu `SearchSuggestionType`；ryot preset `lastUsedAt` |
| 3.17 | 检索性能可观测 | 部分 | P1 | S | animeko 每源 fetch 状态；本项目 `SearchChainProbe` |
| 3.18 | 排序解释面板 | 无 · 新提出 | P2 | S | 本项目 `DiscoveryFeed.Entry.normalizedPosition` |
| 3.19 | 查询语法自动补全/校验 | 无 · 新提出 | P1 | S | 本项目 `SearchQueryParser` |
| 3.20 | 本地 FTS 检索索引 | 无 · 新提出 | P1 | M | 本项目 `pinyinKey` + `searchByPinyin` |
| 3.21 | 零结果近似纠错 | 无 · 新提出 | P1 | S | fuzzywuzzy `partialRatio`（算法参考，非代码引入） |
| 3.22 | 检索结果批量入分区/清单 | 无 · 新提出 | P2 | M | 本项目 `LibraryFolderEntity` 多对多 |

## 3.1 逐条详解

### 3.1 搜索建议本地+远程混合

- **现状**｜部分：机制完整。`doLoadSuggestions` 先本地（`subjectRepository.searchByPrefix` → `searchByKeywordPrefix`，空了再 `searchByPinyinPrefix`）+ 历史 `contains`，走 `buildSuggestionItems`（历史 &lt;=3 + 本地 &lt;=3）；远程 `remoteSuggestionAttempt` 复用正式搜索的 `keywordShareKey` 与 `SearchRequestShare`（`AsyncSingleFlight` + 5s TTL），主链空才走 `searchForSuggestions`（含游戏源旁路）；`mergeSuggestions` 本地在前、远程补 `maxRemote=3`、按 id/标题去重；四态 `SuggestionState` 与 `suggestionNoticeOf` 把「设置关闭/无命中/网络失败」说出来。缺的是：没有每阶段耗时统计，没有对建议项做统一打分排序（只按来源分组），远程失败的本地建议与「无命中」在 UI 上仍靠 notice 区分。
- **提升**｜(a) 用户可见：建议列表稳定按「历史 → 本地前缀 → 拼音命中 → 远程」排序且每组不超过 2 条，避免 6 条建议里 5 条同源；网络失败时保留本地建议并在底部一行说明，而不是整块消失；(b) 工程上把 `buildSuggestionItems/mergeSuggestions` 的隐式顺序变成显式 `score`，补 8 组纯函数测试（历史命中、拼音命中、远程重复、空输入、关闭开关）。
- **原理**｜`data class SuggestionCandidate(val item: SuggestionItem, val origin: Origin, val score: Int)`，`Origin{HISTORY, LOCAL_PREFIX, LOCAL_PINYIN, REMOTE}`，分值 100/80/70/50，同分按 `lastUsedAt` 再按 id；`fun rank(candidates): List<SuggestionItem>` 纯函数，去重键 `subjectId` + 规范化标题。采集耗时：在 `doLoadSuggestions` 的本地/远程两段各记 `SystemClock.elapsedRealtime()`，写 3.17 的 telemetry。
- **效果**｜输入「juren」：建议区先出历史「巨人」、再出本地「进击的巨人」、再出远程 1–2 条；每项右侧有来源小字「历史/本地/远程」；断网时本地 3 条仍在，底部「远程建议不可用」。设置关闭建议时显示「搜索建议已在设置中关闭」。
- **参考**｜本项目 `viewmodel/SubjectSearchViewModel.kt:2092-2129` [码] `buildSuggestionItems/mergeSuggestions`、`:2055-2085` [码] 四态；`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/core/prefs/SearchSuggestionType.kt` [码] `GENRES/QUERIES_RECENT/QUERIES_SUGGEST/MANGA/SOURCES/RECENT_SOURCES/AUTHORS` 七类，直接把「建议来源」做成用户可勾选的类型。
- **成本/风险**｜S｜风险：建议每次击键都跑本地 SQL + 可能的远程请求，排序纯函数必须 O(n log n) 且 n&lt;20，别引入全库扫描。
- **优先级**｜P1

### 3.2 趋势 / 热搜区

- **现状**｜部分：`TrendingMode` 三模式——当季热门（候选 = `/calendar` ∪ 放宽到 400 天的开播日检索，`DiscoveryFeed` 统一排序，质量门槛 100 票、窗口同类 ≤3、连续 ≤2、小类保底 3、目标 30、零填充）、历史排名（`BrowseFilter` 15 维 + 46 标签 + 制作公司别名展开）、Steam 热销前 100（自动尝试匹配 Bangumi，已收藏显示收藏态）。来源单一（Bangumi 为主，Steam 仅游戏），没有跨源趋势合并，也没有「为什么它热」的解释。
- **提升**｜(a) 用户可见：当季热门卡片角标「Bangumi 热度 #12」/「Steam 在线 3.2 万」，同一作品跨源时不重复；历史榜与当季热门共用同一套多样性参数，切换不再忽多忽少；(b) 工程上把候选池的 `provenance` 带进 `DiscoveryFeed.Entry`，排序与 UI 共用一份解释。
- **原理**｜`data class TrendingCandidate(subject, sourceId, typeRank, heat: Double, provenance: Provenance)`；`DiscoveryFeed.build` 的 `Entry` 增加 `sources: Set<String>` 与 `heatLabel: String`。跨源合并键用 3.8 的 `MergeKey`（先只处理 `sourceKey` 已绑定的同作品），未绑定不猜。排序仍只用「类内归一位置」，不引入跨类加权（现有注释已解释量纲问题）。
- **效果**｜当季热门卡右下角来源徽标（Bangumi/Steam）；Steam 榜里已匹配到 Bangumi 的条目显示中文名与 Bangumi 封面；同一游戏不在两个模式里出现两次；切换模式时列表长度稳定 30（候选不足时明示不足）。
- **参考**｜本项目 `data/discover/DiscoveryFeed.kt` [码] 与 `data/discover/BrowseFilter.kt` [码]；`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/explore/data/SourcesSortOrder.kt` [码] 展示「排序口径」可枚举化；`IgnisDa/ryot@main:crates/services/collection/src/recommendation_operations.rs` [码] 用随机抽样 10 条 + 缓存来避免「每次都一样」，可作为趋势刷新策略。
- **成本/风险**｜M｜风险：Steam 榜与 Bangumi 榜的时间窗/量纲不同，混排会让「热」不可比；只在卡片上标来源，不做跨源加权总分。
- **优先级**｜P2

### 3.3 相似作品推荐

- **现状**｜部分：详情页「猜你喜欢」是纯本地 —— `SubjectDetailViewModel.loadGuessYouLike` 取 `observeAll()`，按「共同标签数 × 10 + 同类型 1」排序取前 10（`SubjectDetailViewModel.kt:913-930`）。`Similar` 外部通道（VNDB relations / Steam similar / AniList recommendations）**一个都没接**；`SubjectRepository` 也没有 similar 方法。
- **提升**｜(a) 用户可见：本地库不够 10 条时用外源 similar 补足，卡片带「VNDB 相似」/「Steam 相似」徽标；点进去若是新条目，先落一张占位条目再打开详情；(b) 工程上把「推荐来源」抽象成 provider，本地共现只是其中一个，排序与去重统一。
- **原理**｜`interface SimilarProvider { val id: String; suspend fun similar(subject, limit): List<SimilarHit> }`；`data class SimilarHit(externalId, title, year, coverUrl, score, reason)`；`fun mergeRecommendations(local: List<Recommendation>, external: List<SimilarHit>, exclude: Set<Long>, limit: Int): List<Recommendation>` 纯函数，去重键 = `subjectId` 或 `sourceKey`，同键取 `max(score)` 并合并 reasons。外源命中先查绑定表；未绑定则生成 `sourceKey="vndb:v123"` 的占位条目（与现有 Steam/AniList 占位同机制），**不自动写收藏**。
- **效果**｜详情页「猜你喜欢」从最多 10 条本地变为「本地 6 + VNDB 2 + Steam 1」；每张卡下方一行理由「共同标签：奇幻/治愈」或「VNDB relations：同世界观」；不足 3 条时整块隐藏（不显示空轨）。
- **参考**｜`IgnisDa/ryot@main:crates/services/collection/src/recommendation_operations.rs` [码] `collection_recommendations`：随机取 10 条收藏 → 每条调 `generic_metadata().suggestions` → `HashSet` 并集 → 分页取 metadata，并用 `MEDIA_SOURCES_WITHOUT_RECOMMENDATIONS` 排除不支持的源；`FuzzyGrim/Yamtrack@0.26.3:src/app/providers/services.py` [码] 说明「类型→provider」分派是接入 similar 的前置。
- **成本/风险**｜M｜风险：外源 similar 会把「同世界观/同人」混进来，必须保留 provider 的 relation 类型并在 UI 标注；占位条目污染本地库，需在空详情/无收藏时清理。
- **优先级**｜P1

### 3.4 推荐中隐藏已完成项

- **现状**｜无：`loadGuessYouLike` 只排除当前 subjectId 与 `isSteamPlaceholder`，**不查收藏表**；`DiscoveryFeed` 也没有排除集参数。于是「猜你喜欢」会把已经看过的作品再推一遍；当季热门也会推已收藏的当季番。
- **提升**｜(a) 用户可见：已看/在看/抛弃的条目从推荐与发现页消失，开关「隐藏已收藏」默认开；隐藏后为空时给「已全部看过，去看历史排名」；(b) 工程上把排除集做成 `DiscoveryFeed`/`loadGuessYouLike` 的公共参数，省掉两处各写一遍 `filter`。
- **原理**｜`suspend fun excludedSubjectIds(includeStatuses: Set<WatchStatus>): Set<Long>`（从 `collections` 表查，`WATCHED/DROPPED/ON_HOLD` 默认排除，`WANT_TO_WATCH` 保留）；`DiscoveryFeed.build(groups, params, exclude: Set<Long> = emptySet())` 在①去重阶段就跳过（保证 `duplicateCount` 语义不变，新增 `excludedCount`）。UI 开关存 DataStore，默认 true；外源 similar 命中同样过滤。
- **效果**｜详情页推荐数量从 10 降到 4（库小时）且不再出现「已看过」；发现页筛选器新增「隐藏已收藏」chip，打开后列表顶部小字「已隐藏 86 条已收藏」；全被排除时空态给「关闭隐藏」按钮。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` [码] `hideInLibraryItems: Preference<Boolean>` 与 `globalSearchFilterState`；`IgnisDa/ryot@main:crates/services/collection/src/recommendation_operations.rs` [码] 的 required_set 没有排除已收藏，说明这是常见缺口（ryot 也没做），可作为「必须自己补」的反证。
- **成本/风险**｜S｜风险：排除集会随收藏变化频繁失效，需用一个 `Flow<Set<Long>>` 合并到推荐流，避免每次 recompute 全表。
- **优先级**｜P1

### 3.5 推荐结果可解释

- **现状**｜部分：本地共现算法**有能力**解释（共同标签、同类型），但 `guessYouLike` 的类型是 `List<SubjectEntity>`，理由在排序时算出后即丢弃（`Triple(s, overlap, sameType)` → `map { it.first }`）。外源推荐更没有理由字段。
- **提升**｜(a) 用户可见：每张推荐卡下方一行「共同标签：奇幻 · 治愈」/「同类高分 · 8.6」，外源显示「VNDB：同世界观」；(b) 工程上把推荐结果从裸实体升级为带 `reasons` 的对象，3.3 的外源接入直接复用。
- **原理**｜`data class Recommendation(val subject: SubjectEntity, val score: Int, val reasons: List<RecReason>)`；`sealed interface RecReason { data class SharedTags(val tags: List<String>); data class SameTypeHighScore(val score: Float); data class ExternalSimilar(val provider: String, val relation: String?) }`。只保留前 2 条理由（避免卡片高度膨胀）；理由文案由 UI 层映射，不进数据库。
- **效果**｜推荐卡底部小字一行（最多 2 个 chip）；点击卡片无变化；「为什么推荐」不需要额外弹窗；外源条目 chip 带 provider 名。
- **参考**｜`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt` [码] `MaybeExcludedMedia.Excluded(media, reason)` 把「排除原因」做成 sealed class 并一路带到 UI，是「推荐/排除理由结构化」的现成范式；`IgnisDa/ryot@main:crates/services/collection/src/recommendation_operations.rs` [码] 的 suggestions 只有 id 没有 reason，反证需要自建。
- **成本/风险**｜S｜风险：理由文案过长会破坏横滑卡高度；只显示 2 条、单条 ≤12 字。
- **优先级**｜P1

### 3.6 流媒体平台可用性

- **现状**｜部分：`data/model/PortalTarget.kt` 注册了 **23 个**外链目标（Bilibili/Bangumi App/Steam/VNDB/AniList/网易云/QQ音乐/Mihon/IMDb/TMDb/MAL/AniDB/豆瓣/Fami通/Metacritic/OpenCritic/Billboard/Oricon/MusicBrainz/Discogs/RYM/Goodreads/批评空间），每个 `resolve` 返回「精确 scheme → 搜索 scheme → 网页」三级降级，无可用动作则隐藏。但这是「跳过去搜」，不是「这部作品在哪些平台可看」；没有 region、没有「已下架」、没有平台 logo 聚合。
- **提升**｜(a) 用户可见：详情页「在线观看」区显示 1–3 个平台 chip（Bilibili 有正版 → 直接跳季页；没有则显示「搜索 Bilibili」），并标注「日本 / 中国」区域；(b) 工程上把 23 个 portal 的 `resolve` 结果按 `availability` 分组，外链门户与可看性共用一份数据。
- **原理**｜新增 `data class Availability(provider: String, region: String?, kind: EXACT|SEARCH, url: String, source: String)`；来源优先级：① 已有精确 ID（biliSeasonId）→ EXACT；② AniList `externalLinks`（需在 `AniListQueries.kt` 的 DETAIL 查询里加 `externalLinks { site url type }`，**当前未查**，标 [推断]）；③ 回退到现有 23 portal 的 SEARCH。`fun availabilityOf(subject, ids, links): List<Availability>` 纯函数，按 region + kind 排序，最多显示 3 个。
- **效果**｜详情页「在线观看」行：Bilibili · 中国 · 正片 / Netflix · 日本 · 搜索；点 chip 走现有 `PortalLauncher` 三级降级；无任何来源时整行隐藏（不显示「暂无」）。
- **参考**｜本项目 `data/model/PortalTarget.kt` [码] 23 个目标与三级降级；`IgnisDa/ryot@main:crates/services/collection/src/recommendation_operations.rs` [码] 的 `MEDIA_SOURCES_WITHOUT_RECOMMENDATIONS` 说明「源能力差异」需要显式建模；AniList `externalLinks` 字段为 [推断]（官方 schema 有，但本项目查询未接）。
- **成本/风险**｜M｜风险：版权区域数据不稳定，容易显示「可看」实际不可看；只显示 EXACT（有季 ID）为「可看」，SEARCH 一律标「搜索」。
- **优先级**｜P2

### 3.7 榜单历史快照对比

- **现状**｜无：`subjects.rank` 只存当前排名，`search_history`/收藏表都没有 rank 时间序列；发现页没有任何「↑3 / ↓5」角标。榜单每次刷新直接覆盖，历史不可回溯。
- **提升**｜(a) 用户可见：历史排名卡片角标「本周 ↑3」，详情页显示「历史最高 #12 · 当前 #15」，并有 7/30 天小折线；(b) 工程上把「排名变化」变成一次本地查询，不需要每次拉两份榜单对比。
- **原理**｜新表 `rank_snapshots(id, subjectId, sourceId, periodKey, rank, ratingTotal, capturedAt)`，唯一索引 `(subjectId, sourceId, periodKey)`；`periodKey = yyyy-MM-dd`（日快照）或 `yyyy-Www`（周快照）。采集时机：发现页历史排名刷新成功后，对返回的前 N（如 100）条批量 upsert；保留 90 天，旧数据按 `periodKey` 删除。查询 `fun rankDelta(current: Int, previous: Int?): Delta?` 纯函数；折线用现有图表组件。序列化进 JSON 备份可选（体积大，默认不带）。
- **效果**｜历史榜卡片右上「↑3」绿色/「↓5」红色/「—」；详情页「榜单成绩」区块显示「历史最高 #12（2026-08-03）· 当前 #15」；点开 30 天小折线；无历史时只显示当前排名。
- **参考**｜本轮**未找到源码级参考**（v1 把 R11 Yamtrack 列为参考，但 Yamtrack 没有排名快照表；本轮未复核其 README 声明，不写成事实）。表设计与保留策略参考本项目 `FreshnessSnapshot` 的时间戳/计数范式 [码]，以及 `data/refresh/` 的 TTL 思路。
- **成本/风险**｜M｜风险：每次榜单刷新多 100 行写入；用 `upsertAll` 单事务 + 唯一索引，且只对「前 100 且排名变化」写入。
- **优先级**｜P2

### 3.8 检索结果与本地库的去重合并

- **现状**｜部分：`mergeSearchAttempts` 只在**同一次搜索的多路请求**之间按 `subjectId` 去重（`SubjectSearchViewModel.kt:2027`）；`searchGameSources` 用 `distinctBy { sourceKey }` 合并游戏源。跨源同一作品（Bangumi 条目 vs 它的 AniList/Steam/VNDB 绑定条目）不会合并，因为主链「首个非空即返回」通常只出一个源；但游戏旁路与主链同时命中时可能出现两条同作品。没有声明式的字段冲突优先级。
- **提升**｜(a) 用户可见：搜「赛博朋克」只出一条，角标「Bangumi · Steam」；点进去是 Bangumi 主条目，价格来自 Steam；不再出现两条封面不同、标题差一个字的同一作品；(b) 工程上把「合并」从 `distinctBy` 升级成 union-find 簇，搜索结果、推荐、发现页共用。
- **原理**｜`data class MergeKey(val kind: BANGUMI|ANILIST|STEAM|VNDB, val id: String)`；`class EntityCluster` 用并查集：初始每条一个簇，遍历绑定表（`anilist_bindings`/`steam_bindings`/`vndb_bindings`/`subject_external_ids`）把「本地 subjectId ↔ 外部 id」union；查询时按任一 key 找根。字段合并用 2.20 的 `FieldRule`（标题/封面取 Bangumi，价格取 Steam，评分并列）。**不做标题猜测**，只合并已绑定。
- **效果**｜结果卡右下角多源徽标；同一作品在「全部」类型下只出现一次；点开详情是主条目，外部字段已填充；未绑定的同作品仍是两条（可手动绑定，见 2.11）。
- **参考**｜`IgnisDa/ryot@main:crates/services/collection/src/recommendation_operations.rs` [码] 用 `HashSet<String>` 对 metadata id 做并集去重，是「先聚簇再分页」的最小实现；`open-ani/animeko@main:datasource/api/src/commonMain/kotlin/source/MediaSource.kt` [码] 要求同一资源 `mediaId` 跨查询稳定、下载去重依赖它，说明「稳定身份」是合并的前提。
- **成本/风险**｜L｜风险：并查集要读 4 张绑定表，搜索路径上不能每次都全表扫；在内存缓存 `Map<MergeKey, rootId>`，绑定变更时失效。
- **优先级**｜P1

### 3.9 检索条件的保存与复用

- **现状**｜无：`data/filter/PresetFilters.kt` 是**硬编码**的类型维度（动画 3 维、书籍 2 维、游戏 2 维、音乐 1 维、三次元 2 维），不是用户可保存的筛选组合；`BrowseFilter` 15 个字段只活在 ViewModel 内存里，切页/重启即丢；没有「我的筛选」入口。
- **提升**｜(a) 用户可见：历史排名筛选器顶部一排 preset chips，可「保存当前筛选」「长按删除」「改名」；应用后一键恢复 15 个维度并立即查询；最近使用的 preset 排最前；(b) 工程上把 `BrowseFilter` 的序列化收敛到一处，导出/备份也能带上（与 7.x 联动）。
- **原理**｜Room 表 `filter_presets(id, contextType, contextKey, name, payloadJson, lastUsedAt, createdAt)`；`contextType = DISCOVER_BROWSE`，`contextKey = type?.name ?: "ALL"`（不同作品类型的筛选不混）。`payloadJson` 存 `BrowseFilter` 的 kotlinx.serialization JSON（枚举用 `@Serializable` 或自定义 serializer）。应用时 `Json.decodeFromString<BrowseFilter>` 后整体替换 ViewModel 状态；「当前筛选是否偏离 preset」用 data class 的 `equals`（ryot 用 `isEqual`），偏离即取消高亮。长按删除 + 二次确认。
- **效果**｜历史排名页顶部 chips「本季新番」「MAPPA 高分」「2000 前经典」；点一下 1 秒内列表刷新且筛选面板同步勾选；长按弹「删除/重命名」；保存时输入名称（≥3 字），重名覆盖提示；切到游戏类型时只显示游戏类 preset。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/filter/data/SavedFiltersRepository.kt` [码] 按源分 SharedPreferences、`__pf_` 前缀、JSON 存 `PersistableFilter(name, source, filter)`，提供 save/rename/delete；`IgnisDa/ryot@main:apps/frontend/app/lib/hooks/filters/use-presets.ts` [码] `contextType + contextInformation` 作用域、`lastUsedAt` 排序、应用后 `isEqual` 判断偏离；`IgnisDa/ryot@main:apps/frontend/app/components/common/filter-presets.tsx` [码] chip + 长按删除。
- **成本/风险**｜M｜风险：`BrowseFilter` 未来加字段会导致旧 JSON 反序列化失败；用 `ignoreUnknownKeys + 默认值`，并在 payload 里存 `version`。
- **优先级**｜P1

### 3.10 检索结果导出

- **现状**｜无：搜索结果只能看/点，不能导出；JSON 备份导出的是收藏、历史、绑定等，不含「一次检索的结果集」。
- **提升**｜(a) 用户可见：结果页菜单「导出为 CSV / Markdown」，生成当前筛选下的结果（标题/类型/评分/排名/标签/链接），走系统分享；(b) 工程上与 7.5「清单导出」共用 `SearchExport` 模型，避免两套格式化。
- **原理**｜`data class SearchExportRow(id, title, titleCN, type, score, votes, rank, tags, source, url)`；`fun toCsv(rows): String` 纯函数（转义逗号/引号/换行，UTF-8 BOM 兼容 Excel）；`fun toMarkdown(rows): String`（表格，最多 200 行）。导出用 `FileProvider` + `ACTION_SEND`，不写公共目录。行数上限 500，超出提示「已截断」。
- **效果**｜菜单项「导出结果（CSV）」；分享面板出现 Niriko 生成的文件；CSV 用 Excel 打开中文不乱码；空结果时菜单项禁用。
- **参考**｜`IgnisDa/ryot@main:crates/services/exporter/src/media_exports.rs` [推断]（文件存在，未读内容；从文件名推断按媒体类型拆）；`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/` [推断·目录] 按类别拆 BackupCreator（Categories/Manga/Preference/Sources/ExtensionRepo），说明导出应按域拆分而不是一个巨型函数。
- **成本/风险**｜S｜风险：CSV 注入（标题以 `=`/`+` 开头）；导出前对单元格做前缀转义。
- **优先级**｜P2

### 3.11 搜索空态引导（v1.2.1 决定不做）

- **状态**｜❌ 不实施 —— 保持现有空态（四态文案 + 「诊断搜索链路」入口）。原因：本轮你明确不需要。以下字段仅存档。

- **现状**｜部分：空态有「未找到相关作品」+「试试原名、别名或更短的关键词」+ `suggestionNotice`（关闭/无命中/网络失败）+ 「诊断搜索链路」按钮（`SearchResultsPane.kt:268-316、424-448`）。缺「放宽条件 / 换源重试 / 去发现页」三个动作出口；人物空态只有两句文案。
- **提升**｜(a) 用户可见：空态给 3 个可点按钮，一键把「tag:xxx」「R18」「排序」等条件放宽并重查，或换源，或跳发现页；(b) 工程上把「空态原因 → 建议动作」做成纯函数，避免文案与逻辑分叉。
- **原理**｜`data class EmptyAction(val id: String, val label: String, val kind: RELAX|SWITCH_SOURCE|DISCOVER, val payload: Any?)`；`fun emptyActions(query, parsed, filters, nsfw, remoteFailed): List<EmptyAction>` 纯函数：有 tag → 「去掉标签筛选（tag:xxx）」；nsfw 开 → 「关闭 R18 重试」；有筛选 → 「清空筛选」；永远有「换源重试」与「去发现页」。
- **效果**｜空态从上到下：标题、原因、3 个按钮（主按钮「清空筛选重试」）、诊断入口；人物空态给「切回作品搜索」。点「去发现页」保留 query 到发现页搜索框（或直接回退）。
- **参考**｜本项目 `SearchResultsPane.kt` [码] 现有四态文案；`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/core/prefs/SearchSuggestionType.kt` [码] 的 `QUERIES_SUGGEST` 可映射为「试试这些」。外部没有完全对口的空态实现，标 [推断]。
- **成本/风险**｜S｜风险：按钮过多会挤压空态；最多 3 个，按命中概率排序。
- **优先级**｜已撤回（原 P1）

### 3.12 拼音搜索覆盖面

- **现状**｜部分：`util/PinyinSearch.kt` 用 pinyin4j 2.5.1，`pinyinOf` 逐字 `PinyinHelper.toHanyuPinyinStringArray(ch, format)[0]`，`searchKey(title,titleCN) = pinyin(title)+pinyin(titleCN)+title+titleCN` 小写；`matches` 先直接 `contains` 再比拼音；`pinyinKey` 落 `subjects.pinyinKey` 供 `searchByPinyin` SQL LIKE。**别名/标签不进 key**，所以「进击的巨人」的别名、英文名、罗马音都搜不到；多音字永远取数组第一个（pinyin4j 与 TinyPinyin 都如此），没有自定义词典。
  体积事实：pinyin4j-2.5.1.jar 实际 **323,843 字节**（repo1.maven.org HEAD）；TinyPinyin README 称核心包约 80KB、不添加词典时 &lt;30KB 内存、转换速度是 pinyin4j 的 4–16 倍、首次调用 pinyin4j 约 2000ms（这些是 TinyPinyin README 自述，非本机实测，标 [README]）。TinyPinyin 源码里 `Pinyin.java/Engine.java` 都依赖 `org.ahocorasick.trie`（ahocorasick-0.4.0.jar 23,133 字节），即使不用自定义词典，编译也需要该类；这是上一轮放弃它的真实工程成本。
- **提升**｜(a) 用户可见：搜别名「Attack on Titan」「巨人」都能命中；冷启动首次搜索不再因拼音库初始化卡一下；(b) 工程上把拼音键从「标题派生」升级为「可重建的检索索引」，并明确体积/依赖取舍。
- **原理**｜三条路径，按判据选（**不再写「直接换成 TinyPinyin」**）：
  1. **vendor**：把 promeg/TinyPinyin 的核心文件（`Pinyin.java`/`Engine.java`/`PinyinData.java`/`PinyinCode1..3.java`）拷进 `util/pinyin/`，Apache-2.0 允许；若要保留多音字词典，另需 `PinyinDict/PinyinMapDict/PinyinRules/SegmentationSelector/Utils/ForwardLongestSelector` + ahocorasick（23KB），或者删掉 `Engine` 的 Trie 分支只保留单字路径（改动约 40 行）。**判据**：能接受新增 ~40KB 字典 + 23KB 依赖、愿意维护 vendored 代码。
  2. **Maven Central 等价制品**：`com.github.promeg:tinypinyin` 在 Central **不存在**（`repo1.maven.org/maven2/com/github/promeg/` 404）；Central 上只有第三方转发 `io.github.biezhi:TinyPinyin:2.0.3.RELEASE`（jar 121,379B，pom 声明 Apache-2.0 + ahocorasick 0.4.0）。**判据**：能接受非官方转发者（供应链风险），且确认包名仍是 `com.github.promeg.pinyinhelper`（本轮未解包，待核）。
  3. **保留 pinyin4j 但优化**：① 启动时用一个后台线程预热一次 `PinyinHelper.toHanyuPinyinStringArray('中')`，把首调 2s 移出首屏；② 只对 `title/titleCN` 建**首字母键**（`jdr`）而不是全拼（省 70% 长度，LIKE 更快），全拼键按需生成；③ 别名/英文名直接进 `searchKey` 原文，不转拼音（英文不需要）。**判据**：不动依赖、不改构建，收益最小但风险最低。
  另外，无论走哪条，都要把 `aliases`（infobox 的「别名/英文名/罗马音」）与个人标签并入 `searchKey`；`pinyinKey` 加 `@Serializable` 版本号，便于重建。
- **效果**｜搜索「juren」「jdr」「Attack on Titan」「进击」都出同一部；输入别名时结果卡显示「别名命中」；冷启动首搜不再 1–2s 才出建议；安装包体积按所选路径变化（vendor ≈ +40KB，Central 转发 ≈ +121KB+23KB，保留 pinyin4j ≈ 0）。
- **参考**｜`promeG/TinyPinyin@master:lib/src/main/java/com/github/promeg/pinyinhelper/Pinyin.java` [码] `toPinyin(char)`、`init(Config)`、`PinyinMapDict`；`promeG/TinyPinyin@master:lib/src/main/java/com/github/promeg/pinyinhelper/Engine.java` [码] `toPinyin(String, Trie, ...)` 与单字 fallback；`promeG/TinyPinyin@master:LICENSE` [码] Apache-2.0；`promeG/TinyPinyin@master:README.md` [README] 体积/速度/初始化数据；`xdrop/fuzzywuzzy@master:src/me/xdrop/fuzzywuzzy/ratios/PartialRatio.java` [码] 可作 3.21 的纠错算法参考（GPL-2.0，只参考算法不拷代码）。
- **成本/风险**｜S/M｜风险：路径①要维护 vendored 代码与 ProGuard；路径②的第三方转发包可能改包名/漏类；路径③收益有限。**不要**直接把 pinyin4j 换成 JitPack 依赖——上一轮已确认镜像不可达（`gradle/libs.versions.toml:85` 注释）。
- **优先级**｜P1

### 3.13 人物检索与作品联动

- **现状**｜部分：作品/人物模式切换（`setPersonSearch`），人物搜索走 `subjectRepository.searchPersons` → `DataSourceChain.searchPersons`（首个非空插件），结果卡 `PersonSearchCard` 显示代表作品；空态「未找到相关人物 / 试试中文名、别名或完整姓名」。没有模糊匹配、没有按职业/性别筛选、没有「人物 → 参与作品」的筛选面板，也没有本地人物缓存（每次全打远程）。
- **提升**｜(a) 用户可见：搜「花泽」能出「花泽香菜」，结果可按「声优/导演/作者」筛，卡片显示「参与 42 部 · 收藏 3 部」；(b) 工程上人物检索与作品检索共用拼音/别名规范化，省一套匹配逻辑。
- **原理**｜`data class PersonQuery(keyword, roles: Set<PersonRole>, minWorks: Int)`；归一化复用 `PinyinSearch.searchKey`（人物名也建拼音键），远程返回后本地按 `roles` 过滤；人物结果缓存到 Room（`person_search_cache(keyword, payloadJson, fetchedAt)`，TTL 1 天）。「人物 → 作品」用现有 `PersonDetailScreen` 的参与作品查询，不加新接口。
- **效果**｜人物搜索页顶部筛选 chips「全部/声优/导演/作者」；结果卡右侧「参与 42 部」；无结果时给「只搜作品」出口；二次搜索同关键词秒出（缓存）。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/core/prefs/SearchSuggestionType.kt` [码] `AUTHORS` 作为建议分类；`open-ani/animeko@main:datasource/api/src/commonMain/kotlin/source/Browse.kt` [码] `BrowseSubject/BrowseChannel` 把「站点条目/线路/剧集」分层，人物检索可借鉴「先列实体再取关联」的两段式。本项目 `PersonSearchCard` [推断·文件存在]。
- **成本/风险**｜M｜风险：Bangumi 人物搜索本身不支持职业过滤，客户端过滤会漏（只返回前 N）；需要把 `limit` 提到 50 并在 UI 说明「按已返回结果筛选」。
- **优先级**｜P2

### 3.14 NSFW 检索策略透明化

- **现状**｜部分：策略在 `BangumiDataSource.search`：`nsfw == true && keyword.isNotBlank()` 时**先走旧版** `legacySearch`（免 token、含 NSFW），空才走 v0；v0 带筛选语义时 POST 空**不**兜底旧版（`shouldUseLegacySearch` + `hasFilterSemantics`，`BangumiDataSource.kt:85-98`）。设置页「NSFW 检索」行只显示「已登录，v0 接口可用」/「未登录，走旧版接口兜底」（`DataSourceSettingsScreen.kt:210-221`）。用户看不到「这一次搜索实际走了哪条」「旧版不支持 tag/air_date/rank」。
- **提升**｜(a) 用户可见：开启 R18 搜索时，结果区顶部一行「R18 · 旧版兜底（不支持标签/日期筛选）」；设置页显示「当前：未登录 → 旧版；登录后：v0（支持筛选）」；(b) 工程上把路由判定抽成纯函数并随结果返回，UI 不再猜。
- **原理**｜`enum class NsfwRoute { V0, LEGACY, LEGACY_FIRST, UNAVAILABLE }`；`fun routeNsfw(token: Boolean, keyword: String, hasFilters: Boolean, nsfw: Boolean): NsfwRoute` 纯函数（照抄现有 if 顺序并补 `UNAVAILABLE`）；`SearchOutcome` 增加 `route: NsfwRoute?`，由 `BangumiDataSource` 在返回时带回，UI 只渲染。
- **效果**｜开启 R18 后搜「xxx」：结果顶部灰字「R18 · 旧版兜底 · 不支持标签筛选」；若同时选了 tag，提示「已忽略标签筛选」；设置页行文案随登录态变化，登录后显示「v0（支持筛选）」。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` [码] `enabledContentWarnings: Preference<Set<ContentWarning>>`（SAFE/MIXED/NSFW）+ `applyContentWarningsToInstalled`，把「内容警告等级」做成显式三态而不是一个布尔；本项目 `BangumiDataSource.kt` [码]。
- **成本/风险**｜S｜风险：路由文案会暴露「未登录」状态，需与隐私说明一致；不要把 NSFW 开关做成三态迁移（会动设置 schema）。
- **优先级**｜P1

### 3.15 跨类型统一检索的排序口径

- **现状**｜部分：`DiscoveryFeed` 已把「全部」的排序写成流水线（去重 → 类内归一 → 质量门槛 → 多样性贪心 → 截断），参数集中在 `Params`（`minRatingTotal=100, windowSize=10, maxSameTypeInWindow=3, maxConsecutiveSameType=2, minPerMinorType=3, targetSize=30, relaxWhenStuck=true`），并声明「无随机、无时钟、不读环境」；`app/src/test/java/com/otakup/niriko/data/discover/DiscoveryFeedTest.kt` 已存在，但口径只写在代码注释里、没有文档，现有测试覆盖面需读测试逐条核对。
- **提升**｜(a) 用户可见：「全部」与「当季热门」用同一套规则，列表长度稳定、不出现连续 5 条动画；小类（三次元/音乐/书籍）至少有 3 条；(b) 工程上把这组参数写进 docs，并把现有 `DiscoveryFeedTest` 补成表驱动单测锁死，改阈值必须改测试。
- **原理**｜保留现有算法，补两块：① 在文档里记录口径（质量门槛/窗口/连续/保底/零填充/放宽条件）；② 在现有 `DiscoveryFeedTest` 上补表驱动 8 组：全同类候选（应放宽到 target 或停止）、小类只有 2 条（不保底）、重复 id（duplicateCount 正确）、ratingTotal=null（被门槛挡）、`relaxWhenStuck=false`（连续上限停住）、空输入、targetSize=0、windowSize=1。排序的确定性已有 `subjectId` 末位 tiebreak（`ordering`）。
- **效果**｜「全部」前 30 条里同类型最多连续 2 条；三次元/音乐/书籍各至少 3 条（候选够时）；候选不足 30 时不补齐，底部显示「当前只有 22 条」；参数改动会红测试。
- **参考**｜本项目 `data/discover/DiscoveryFeed.kt` [码]；`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt` [码] 的 comparator 同样用稳定末位键保证确定性，可类比。
- **成本/风险**｜S｜风险：补测试时可能发现现有实现与注释不一致（如 `minPerMinorType` 的「候选够 3 条」判定），以代码为准并修正文档。
- **优先级**｜P1

### 3.16 检索历史管理

- **现状**｜部分：`search_history(id autoGenerate, keyword, createTime)`；`upsertByKeyword` 先 `deleteByKeyword` 再 insert（去重 + 更新时间），`observeRecent` 只取 20 条；UI 支持单条删除、清空（带确认）。没有置顶、没有记录当时的搜索模式/类型/筛选、没有「重命名」、没有命中次数；同一关键词在作品/人物模式共用一条。
- **提升**｜(a) 用户可见：历史 chip 可长按置顶/删除；点历史恢复当时的「人物/作品 + 类型 + 筛选」；常用词按使用次数加粗/靠前；(b) 工程上把历史从「字符串列表」升级为「查询快照」，与 3.9 的 preset 共用序列化。
- **原理**｜迁移 v33 给 `search_history` 加 `isPinned: Boolean=false`、`searchMode: String="WORKS"`、`selectedType: Int?=null`、`filterJson: String?=null`、`hitCount: Int=0`。排序 `ORDER BY isPinned DESC, createTime DESC LIMIT 20`；`upsertByKeyword` 改成「同 keyword 则 hitCount+1、更新时间」，保留 id 以便置顶。点历史时若 `filterJson` 非空则恢复筛选（复用 3.9 的反序列化）。
- **效果**｜历史区顶部固定 2 条置顶（图钉 icon）；点「巨人」若当时是人物模式则直接回人物结果；长按 chip 出「置顶/删除」；清空仍二次确认；导出 JSON 备份包含这些字段（向后兼容旧备份）。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/core/prefs/SearchSuggestionType.kt` [码] 把 recent queries 与 recent sources 分开；`IgnisDa/ryot@main:apps/frontend/app/lib/hooks/filters/use-presets.ts` [码] `lastUsedAt` 排序 + 应用后记录使用，是「历史/preset 共用最近使用序」的现成做法。
- **成本/风险**｜S｜风险：加列迁移要写显式 migration 并更新 `app/schemas`；旧备份恢复时缺字段用默认值。
- **优先级**｜P2

### 3.17 检索性能可观测

- **现状**｜部分：`SearchChainProbe` 单次测量 Bangumi 三层（HTTP 状态/耗时/条数/响应前 400B），只在搜索空态手动触发；`SearchResultsPane` 不显示耗时；`doSearch` 的 5 类型并行、变体标签并行、建议 120ms/正式 300ms 防抖都没有埋点；没有 p50/p95 或趋势。
- **提升**｜(a) 用户可见：诊断页「检索性能」显示最近 20 次搜索的 p50/p95、每源命中率、建议链路耗时；慢搜索（&gt;2s）时结果页角标「2.4s」；(b) 工程上把「为什么慢」从主观变数据：本地/远程/解析/落库四段分别计时。
- **原理**｜`data class SearchTrace(id, at, query, mode, type, stages: Map<Stage, Long>, sourceId, resultCount, outcome, route)`；`Stage{LOCAL_PREFIX, REMOTE_MAIN, REMOTE_FALLBACK, PARSE, PERSIST, TOTAL}`。采集用 `SystemClock.elapsedRealtime()`；内存环形 50 条 + DataStore 日聚合（p50/p95 用 `fun percentile(sorted, p)` 纯函数）。UI 只读汇总，不上传。
- **效果**｜诊断页「最近 20 次：p50 380ms · p95 1.2s；建议 p50 90ms；Bangumi 命中 17/20，AniList 2/20」；单次搜索 &gt;2s 时结果页显示耗时；「导出诊断」带 JSON。
- **参考**｜`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt` [码] 每源 `state/results` 流与 `RateLimited(retryAt)`，天然可展示每源进度/耗时；本项目 `SearchChainProbe.kt` [码] 已有 `elapsedMs/itemCount/bodyPreview`，把它从「手动单次」升级为「自动聚合」即可。
- **成本/风险**｜S｜风险：埋点要用固定 key，否则 DataStore 无限膨胀；只保留 14 天日聚合。
- **优先级**｜P1

## 3.9 本域新增候选（发散）

### 3.18 排序解释面板

- **现状**｜无 · 新提出：`DiscoveryFeed.Entry` 已带 `typeRank` 与 `normalizedPosition`，但 UI 只用它们排序，不解释「为什么这部在第三」；「全部」排序对用户是黑盒。**为何是 Niriko**：排序参数就在客户端纯函数里，只有本项目能就地解释而不必请求服务端。
- **提升**｜(a) 用户可见：长按/点「i」在发现页展开「本次排序规则」：质量门槛 ≥100 票、窗口同类 ≤3、连续 ≤2、小类保底 3、目标 30；每张卡标「类内 #2 / 归一 0.12」；(b) 工程上把 `Params` 直接渲染，改阈值不用改文案。
- **原理**｜`data class FeedExplanation(params: DiscoveryFeed.Params, candidateCount, duplicateCount, rejectedByQuality, typeCounts, entries: List<Entry>)`；`fun explain(result: DiscoveryFeed.Result, params): FeedExplanation` 纯函数；UI 用底部弹层，卡片角标读 `Entry.typeRank/normalizedPosition`。
- **效果**｜发现页工具行「排序规则」入口；弹层里一组只读参数 + 本次统计；卡片右上小字「#2」；关闭后不记忆。
- **参考**｜本项目 `data/discover/DiscoveryFeed.kt` [码]；`open-ani/animeko@main:app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt` [码] 的 `MatchMetadata` 把排序依据随条目带出，UI 才能解释。
- **成本/风险**｜S｜风险：暴露内部阈值会让用户要求可调；第一版只读。
- **优先级**｜P2

### 3.19 查询语法自动补全/校验

- **现状**｜无 · 新提出：`SearchQueryParser` 支持 `tag:` 与 `sort:`，但输入时没有任何提示；拼错 `tags:`/`srot:` 会被当成普通关键词，用户以为筛选生效了。**为何是 Niriko**：语法解析器完全在本地，补全与校验不需要也不应该有服务端。
- **提升**｜(a) 用户可见：输入 `tag:` 时弹出标签候选（46 词 + 个人标签），`sort:` 弹 match/heat/rank/score；未知前缀给黄字「未知筛选词 tags:，已按关键词搜索」；(b) 工程上把语法定义从正则升级为小词法表，解析器与补全共用一份。
- **原理**｜`data class QueryToken(val kind: KEYWORD|TAG|SORT|UNKNOWN, val raw: String, val value: String?)`；`fun tokenize(query): List<QueryToken>` 纯函数（现有正则可保留作 fallback），`fun suggest(query, caret): List<Completion>`。UI 用 `BasicTextField` + 候选行，不引第三方。
- **效果**｜输入「异世界 tag:」→ 下方 5 个高频标签；选中后插入「tag:奇幻 」；`tags:xx` 下方黄字提示；回车照常搜索。
- **参考**｜本项目 `data/search/SearchQueryParser.kt` [码] 与 `data/search/TagAliasMap.kt` [码]；`mihonapp/mihon@main:app/src/main/java/eu/kanade/presentation/browse/GlobalSearchScreen.kt` [推断·文件存在] 的全局搜索过滤条可参考「条件可见化」。
- **成本/风险**｜S｜风险：中文输入法组合期会频繁触发 tokenize；用 200ms 防抖并只在「:」后弹。
- **优先级**｜P1

### 3.20 本地 FTS 检索索引

- **现状**｜无 · 新提出：本地命中靠 `searchByKeyword`（LIKE）与 `searchByPinyin`（pinyinKey LIKE），两者都是全表 `%kw%` 扫描；库大（数千条）时建议与离线回退会慢；别名/标签不在索引里。**为何是 Niriko**：离线可用是 README 明确的跨页面能力，FTS 是本地检索的自然升级。
- **提升**｜(a) 用户可见：离线/弱网时建议与搜索仍在 100ms 内出结果；搜别名、标签、英文名都能命中；(b) 工程上把「检索键」从一列字符串升级为 FTS 表，省掉 LIKE 全表扫。
- **原理**｜Room 支持 `@Fts4`：新表 `subject_fts(subjectId UNINDEXED, title, titleCn, aliases, tags, pinyinKey)`，`contentEntity = SubjectEntity` 或触发器同步；查询 `MATCH 'juren*'` + `ORDER BY rank`（bm25）。写入在 `SubjectWriteGateway` 的 diff 分支里同步；重建用一个幂等 `RebuildSearchIndex`（启动后后台跑一次，版本号存 DataStore）。
- **效果**｜离线搜索「juren」200 条库 &lt; 50ms；搜别名「Attack on Titan」命中；建议区本地结果更全；「重建索引」在诊断页可见。
- **参考**｜本项目 `data/local/dao/SubjectDao.kt` [码] 与 `util/PinyinSearch.kt` [码]；`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` [码] 的 `globalSearchFilterState` 说明全局检索是独立状态，可挂 FTS 结果。
- **成本/风险**｜M｜风险：FTS 表与实体表同步易漂移；用 Room 的 `contentEntity` 或显式触发器 + 单测，别手写双写。
- **优先级**｜P1

### 3.21 零结果近似纠错

- **现状**｜无 · 新提出：空态只提示「试试原名/别名/更短关键词」，不会给「你是不是想找《进击的巨人》」；拼音精确子串不命中时没有近似建议。**为何是 Niriko**：无服务端拼写服务，纠错只能在本地库上算。
- **提升**｜(a) 用户可见：搜「进击巨人」（少「的」）或「jujin」时，空态出现「你是不是想找：进击的巨人」；(b) 工程上复用 fuzzywuzzy 的 partial ratio / token sort 思路，在本地库（≤2000 条）做一次 Top-3 纠错，不发网络。
- **原理**｜`fun suggestCorrections(query, candidates: List<Pair<Long,String>>, limit=3): List<Correction>`：对标题与拼音键各算 `partialRatio`（fuzzywuzzy 的 matching-block 思路）与 `tokenSortRatio`，取 `score ≥ 70` 的前 3；中文按字符 n-gram Jaccard 代替词序。候选来自 `subject_fts`（3.20）或内存 LRU。**只参考算法，不引入 GPL-2.0 代码。**
- **效果**｜空态从 2 行文案变「未找到 · 你是不是想找：进击的巨人 / 进击的巨人 第三季」；点一下直接搜该标题；无候选时保持原文案。
- **参考**｜`xdrop/fuzzywuzzy@master:src/me/xdrop/fuzzywuzzy/ratios/PartialRatio.java` [码] matching blocks + Levenshtein；`xdrop/fuzzywuzzy@master:src/me/xdrop/fuzzywuzzy/FuzzySearch.java` [码] `partialRatio/tokenSortPartialRatio`。
- **成本/风险**｜S｜风险：纠错过度（把用户精确搜的冷门词改掉）；只在 `results.isEmpty()` 且用户没有点过纠错时展示。
- **优先级**｜P1

### 3.22 检索结果批量入分区/清单

- **现状**｜无 · 新提出：搜索结果只能逐条点进详情再加收藏；要把「MAPPA 全部作品」一次性放进自定义分区，需要点 20 次。收藏页有批量操作，搜索页没有。**为何是 Niriko**：自定义分区是本地多对多模型，批量入口应复用同一写链路而不是新表。
- **提升**｜(a) 用户可见：结果页长按进入多选，底部「加入分区 / 加入想看 / 导出」；选 10 条一键入区；(b) 工程上复用 `LibraryFolderSubject` 的多对多与 `SubjectWriteGateway` 批量写，不加新表。
- **原理**｜`data class SearchSelection(val ids: Set<Long>)`；`suspend fun addToFolder(ids, folderId)` 批量 insert（单事务，冲突忽略）；UI 用 `LazyColumn` 的 `combinedClickable` + 顶部选择条，与作品库多选同交互。批量收藏要写 `collections` 默认状态（想看），并复用现有 `CollectionRepository` 的写回链路（含 Bangumi 同步队列）。
- **效果**｜长按结果卡 → 顶部「已选 3/20」+ 全选；底部「加入分区」弹出分区列表；完成后 snackbar「已加入 12 部到『MAPPA』」；退出多选恢复原状。
- **参考**｜本项目 `ui/library/` 多选（目录）与 `data/local/entity/LibraryFolderEntity.kt` [推断·文件存在]；`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/source/interactor/GetSourcesWithFavoriteCount.kt` [推断·文件存在] 说明「库内/库外」是批量操作的前提。
- **成本/风险**｜M｜风险：批量收藏会触发大量 Bangumi 写回，需与 1.x 的待同步队列/2.15 限流联动；第一版只写本地，同步交给后台队列。
- **优先级**｜P2

## 3.A 本域新增参考库

| 仓库 | 许可 | star（截至 2026-10） | 语言 | 为何与本项目相关 | 已验证文件（均可 GET 200） |
| --- | --- | --- | --- | --- | --- |
| KotatsuApp/Kotatsu | GPL-3.0 | 8.9k | Kotlin | 搜索建议七分类、筛选预设按源持久化、源排序四模式，适合 3.1/3.9/3.13/3.16 | `app/src/main/kotlin/org/koitharu/kotatsu/core/prefs/SearchSuggestionType.kt`、`app/src/main/kotlin/org/koitharu/kotatsu/filter/data/SavedFiltersRepository.kt`、`app/src/main/kotlin/org/koitharu/kotatsu/explore/data/SourcesSortOrder.kt`、`app/src/main/kotlin/org/koitharu/kotatsu/core/model/MangaSourceInfo.kt`、`app/src/main/kotlin/org/koitharu/kotatsu/explore/ui/model/RecommendationsItem.kt` |
| IgnisDa/ryot | GPL-3.0 | 未取到 | Rust + React | 推荐聚合（随机抽样 + suggestions 并集 + 缓存）、筛选预设（context/lastUsed/isEqual/long-press），适合 3.3/3.8/3.9 | `crates/services/collection/src/recommendation_operations.rs`、`apps/frontend/app/lib/hooks/filters/use-presets.ts`、`apps/frontend/app/components/common/filter-presets.tsx`、`crates/services/exporter/src/media_exports.rs` |
| mihonapp/mihon | Apache-2.0 | 24k | Kotlin | 内容警告三态、隐藏库内条目、全局搜索过滤状态，适合 3.4/3.14 | `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`、`domain/src/main/java/tachiyomi/domain/source/model/Source.kt`、`app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/`（目录） |
| open-ani/animeko | AGPL-3.0 | 未取到 | Kotlin/Compose MP | 可解释的排除原因（sealed）、确定性排序、每源 fetch 状态，适合 3.5/3.15/3.17 | `datasource/api/src/commonMain/kotlin/source/MediaSource.kt`、`datasource/api/src/commonMain/kotlin/source/Browse.kt`、`app/shared/app-data/src/commonMain/kotlin/domain/media/selector/filter/MediaSelectorFilterSortAlgorithm.kt`、`app/shared/app-data/src/commonMain/kotlin/domain/media/fetch/MediaFetcher.kt` |
| promeG/TinyPinyin | Apache-2.0 | 3.9k | Java | 轻量拼音（无音调/大写/自定义词典），是 3.12 路径①的源码；注意依赖 ahocorasick | `lib/src/main/java/com/github/promeg/pinyinhelper/Pinyin.java`、`lib/src/main/java/com/github/promeg/pinyinhelper/Engine.java`、`LICENSE`、`README.md` |
| xdrop/fuzzywuzzy | GPL-2.0 | 未取到 | Java | partial ratio / token sort 相似度算法，仅作 3.21 算法参考（GPL 代码不可直接拷入 APK） | `src/me/xdrop/fuzzywuzzy/ratios/PartialRatio.java`、`src/me/xdrop/fuzzywuzzy/FuzzySearch.java` |
| FuzzyGrim/Yamtrack | 未取到 | 未取到 | Python/Django | 「类型 → provider」分派与按 host 限流，适合 3.3 接入 external similar 前的能力建模 | `src/app/providers/services.py`、`src/integrations/imports/`（目录） |

## 3.B 自检

- 汇总表 22 行 = 详解 17 条（3.1–3.17）+ 新增 5 条（3.18–3.22）。✅
- 每条参考都带 `owner/repo@branch:path` 的**完整路径**（本文件不使用省略路径；超大仓库 animeko 虽无法列树，但每个文件都单独 GET 验证过 200）。
- 证据等级：[码] 均实际 fetch 并读到关键符号；[README] 为项目文档/README；[推断] 已在条目内显式标注（3.6 的 AniList `externalLinks`、3.11 的外部空态）。
- star 取数：mihon 24k、Kotatsu 8.9k、TinyPinyin 3.9k 于 2026-10 取到；animeko/ryot/Yamtrack/fuzzywuzzy 的 shields 本轮超时，写「未取到」。
- 3.7 未找到源码级参考，已明确写出，未把 v1 的 README 声明伪装成源码事实。
- 待核：① 3.12 路径②的 `io.github.biezhi:TinyPinyin:2.0.3.RELEASE` 未解包确认包名；② 3.15 的 `DiscoveryFeedTest` 现有覆盖需逐条读测试确认（文件存在，本轮未展开）；③ 3.20 的 FTS 方案需评估 Room `contentEntity` 与现有迁移链 v32→v33 的兼容。


