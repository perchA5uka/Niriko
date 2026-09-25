# 修复方案（第 5 轮）· 三个未解决项

> 承接 `fix-and-redesign-plan-round4.md`。第 4 轮改动后用户复查，仍有三项未解决，且产生了新的回归。
> 本文件只做**方案**，经用户同意后再改代码。

## 结论摘要（先看这个）

| 用户反馈 | 我的定位 | 性质 |
|---|---|---|
| 《魔法少女的魔女审判》依旧没匹配上 | 找到 **1 个确定缺陷**（infobox 竞态）+ 1 个确定设计缺陷（打分目标不含别名）+ 3 个待真机验证的假设 | 需先加诊断，再改算法 |
| 豆瓣剧照不可用 | **至今没有任何真机证据**；第 4 轮的自检埋在设置页、用户不会去找；OkHttp 直连已知会被反爬 | 需要换通路，不是调参数 |
| 发现页「全部」被删 + 逻辑没对齐 Bangumi-master | 第 4 轮**改错了地方**：删的是搜索菜单的共享常量，找条目那边根本没被改到；且找条目的 type 是**非空**字段，等于永远无法跨类型检索 | 确定缺陷，可立即修 |
| **当季热门收不到长连载**（《假面骑士zzz》不出现） | 当季热门用的是「**本季度开播**」的 air_date 窗口，而正确语义是「**正在放送**」；全仓已有 `/calendar` 通路却没用上；不足时用历史排名填充是错误做法 | 确定缺陷，见第 4 节 |

三项里第 3 项是纯 bug、当天可修；第 1、2 项都需要**先让失败可见**，否则只是继续猜。

---

## 实施状态（滚动更新）

| 批次 | 内容 | 状态 |
|---|---|---|
| 第 1 批（部分） | D21 两个「全部」+ 常量拆分、D22 类型行横滑、D25 部分（KDoc） | ✅ 完成 |
| 第 1.5 批 | D26 当季热门改用「正在放送」、D27 放送进度、删除历史排名填充 | ✅ 完成 |
| 第 1 批（剩余） | D24 找条目「全部」+ 改动即查询 | ✅ 完成 |
| 第 4 批 | D28 多视图（卡片/宫格）、D29 每日放送页、D23 发现页对齐 | ✅ 完成 |
| 第 2 批 | 问题 1 · VNDB：D10 竞态 / D11 打分目标 / D12 OR 兜底 | ✅ 完成 |
| 第 2 批（剩余） | D9 诊断面板、D13 阈值分层 UI、D14 relations 反查绑定、D15 手动通道 | ⏳ 待做 |
| 第 3 批 | 问题 2 · 豆瓣（D16-D19） | ⏳ 未开始 |

**本轮已验证**：`assembleDebug` ✅ + `testDebugUnitTest` ✅ **322 个 testcase，0 失败**
（第 4 轮基线 300 → 新增 22 个当季热门单测）。

### 真机反馈 #1：看不到《假面骑士zzz》→ 已定位并修掉

用户装包后确认「当季热门里没有 zzz」。定位到**两个叠加的原因**，都不是数据源问题：

1. **截断**：候选是「全部本季作品」约两百条，代码里 `take(15)` ——
   一部评分人数几百的在播特摄，会被评分人数上万的当红番整体挤出前 15。
   已改为**不截断**（上限 300 ≈ 全量），并新增**列表内关键词检索**（纯内存、零请求），
   保证任何一部在季作品都能被立刻找到。
2. **判定过严**：实测当天是 **2026-09-19**，而 zzz（2025-09-07 开播、约 50 话）的
   估算完结日是 **2026-08-23** —— 它**已经播完**了。按「必须仍在播」的规则它永远进不来。
   已把语义从「**正在放送**」放宽为「**本季**」= 本季度内还在播、或在本季度内完结：
   - 播到一半的长连载 → 完结日在未来 → 收；
   - 本季刚完结（如 zzz） → 完结日 >= 本季度起始日 → 收；
   - 上季度及更早完结的短番 → 不收（否则一年前的番会永远赖在榜上）。

   UI 文案随之从「正在放送 · N 部」改为「**本季 · 共 N 部**」。

> ⚠️ 这条规则是**按用户诉求放宽**的。如果你要的其实是严格的「此刻正在播」，
> 把 `isProbablyAiring` 里的 `quarterStart(today)` 换回 `today` 即可（一行）。

### 第 1 批（剩余）+ 第 4 批：发现页多视图 / 宫格 / 每日放送 / 找条目（全部完成）

| 项 | 内容 |
|---|---|
| D28 | **发现页多视图**：`DiscoveryLayout { CARD, GRID }`，顶栏右侧一键切换，选择持久化到 `AppSettings.discoveryLayout`（DataStore）。CARD = 原来的顶栏标签 + 全屏列表；GRID = Bangumi-master 形态的宫格首页（菜单宫格 + 横向预览），点宫格项切到对应列表并回到卡片视图。 |
| D29 | **每日放送页**：新增 `TrendingMode.CALENDAR`（刻意不进顶栏标签，只做宫格入口），复用 `BroadcastFetcher`（→ 共享 `CalendarCache`，与统计页同一份 /calendar，不会多打一次）。按星期分组，「第 N 话」复用 `SeasonalTrendingCalculator.progress`。这是长连载最自然的归宿。 |
| D21 | 找条目 **「全部」**：`FindSubjectsFilter.type` 改为可空（null = 不限类型），`buildRequest` 在 null 时**不生成 `filter.type`**（发空数组在 Bangumi 上等于无结果）。「全部」chip 由 UI 层拼在 `SUPPORTED_TYPES` 前面，那个常量保持「真实类型」语义。 |
| D24 | 找条目 **改动即查询**：年份/季度/标签/系列/NSFW/排序改动后立刻重查（对齐 Bangumi-master 索引页的即时生效）；评分与排名是文本输入，仍由各自的「应用」按钮提交，避免逐键打接口。 |
| D22 | 搜索类型行横向可滚动（6 个类型含「全部」）。 |

**新增文件**：`data/model/search/DiscoveryLayout.kt`、`ui/search/DiscoverGridPane.kt`、`ui/search/BroadcastCalendarPane.kt`

**宫格为什么只有 6 个入口**：Bangumi-master 的默认菜单有 10 项，但「标签 / 系列 / 目录 / 索引」
各自需要独立的数据通路（标签聚合、系列聚合、目录与索引接口），本批**不放死按钮** ——
一个点了没反应的格子比少一个格子更糟。它们与「按类型分区块的首页列表体」一起排在后续批次。

**与 Bangumi-master 的差异（有意为之）**：它的宫格首页列表体是「按 5 个类型分区块、每块一张大封面 + 横滑」
（`list-item.tsx`），数据来自抓取 bgm.tv 首页的 `#featuredItems`。我们没有可稳定抓取的等价数据源，
因此宫格下半部分改用**本季横向预览**（复用已经拉到的当季热门数据，零额外请求）。

### 第 1 / 1.5 批实际改动

**新增**

- `data/seasonal/SeasonalModels.kt` —— `SeasonalSort` / `SeasonalTypeFilter` / `SeasonalItem` / `SeasonalProgress` / `SeasonalTrendingResult`
- `data/seasonal/SeasonalTrendingCalculator.kt` —— 纯函数：在播判定 / 进度 / 去重合并 / 类型过滤 / 排序 / 兜底窗口
- `data/seasonal/SeasonalTrendingRepository.kt` —— 编排：/calendar（权威）+ 放宽窗口检索（兜底）取并集
- `data/remote/CalendarCache.kt` —— /calendar 的共享内存缓存（新鲜期 6h + 失败退回旧数据）
- `test/.../SeasonalTrendingCalculatorTest.kt` —— 18 个用例

**改动**

- `SubjectSearchViewModel` —— 新增当季热门早退分支（在 STEAM 分支之后），
  **删除 530-538 的历史排名填充**；新增 `setSeasonalSort` / `setSeasonalTypeFilter`（**本地重排，零请求**）；
  构造函数与 Factory 增加 `seasonalTrendingRepository`
- `SubjectSearchUiState` —— `seasonalSort` / `seasonalTypeFilter` / `seasonalTotalCandidates` / `trendingSubtitle` / `seasonalNotice`
- `ui/search/TrendingSection.kt` —— 新增 `SeasonalHeader`（正在放送计数 + 来源提示 + 类型/排序 chips）、
  卡片副标题（放送日 · 第 N 话）、尾部「查看历史排名 ›」出口
- `ui/common/SearchState.kt` —— **`SELECTABLE_CONTENT_TYPES` 恢复 `ContentType.ALL`**，KDoc 记录第 4 轮改错位置
- `ui/common/IosStyleSearchComponent.kt` —— 类型行改横向可滚动（6 个类型不再硬挤一行）
- `data/remote/BroadcastFetcher.kt` —— 改用 `CalendarCache`，与当季热门共用同一份 /calendar
- `NirikoApplication` / `MainPager` / `NirikoNavHost` —— 注册与注入 `calendarCache`、`seasonalTrendingRepository`

### 实施时发现的两个额外问题（未修，记录在案）

1. **`BroadcastFetcher` 写库会抹掉本地补充列**：它 `upsertAll` 的是 /calendar 的原始条目，
   而该响应不含 `biliScore` / `pinyinKey` / `tags` 等本地列 —— upsert 会把它们覆盖成 null。
   本项目在 `getDetail` 上已经处理过同类问题（合并本地 B 站列），这里漏了。
   **本轮新写的 `SeasonalTrendingRepository` 刻意不写库**以规避该问题，
   但 `BroadcastFetcher` 的既有路径仍在，建议下一批一并修。
2. **`AiringStatus.getPhase` 的 180 天 fallback** 对长连载会误判成 `COMPLETED`。
   当季热门已绕开它（只用 `isProbablyAiring`），但统计页/日历仍在使用它，同样是隐患。

---

## 第 0 步 · 基线

```
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

第 4 轮结束时记录为 **300 个 testcase 全绿、DB version 28**。本轮开工前先复现一次，确认基线；
若已红，先修基线再动本方案。

---

# 问题 1 · 《魔法少女的魔女审判》VNDB 匹配不上

## 1.1 已经确定的缺陷（不是猜测，读代码即可确认）

### A. infobox 竞态 —— 最可能的元凶

`SubjectDetailViewModel.loadVndbSupplement()`（约 669 行）里：

- 匹配是 `extendedScope.launch { ... }` 并行批中的一路；
- 它读的是 `_uiState.value.infoBox`（约 698 行）；
- 而 `infoBox` 由**同一批的另一条 deferred** 写入。

两者谁先跑完不确定。只要 VNDB 这一路先跑，`infoBox` 就是**空列表**，于是：

- L1「infobox 明确 vndb id」永远不触发（Bangumi 游戏词条常见的 `vndb | v12345` 白白丢掉）；
- L2 的**别名查询串全部缺失**（`MatchQueryBuilder.build` 很大一部分输入来自 infobox）。

对一个「中文名 VNDB 搜不到、只能靠别名/罗马音」的作品，这足以解释「一直匹配不上」。
同类时序 bug 本项目已经踩过一次（第 3 轮 R2：评分洞察在 `ratingDistribution` 写入前读取），
`loadRatingInsights(distribution)` 的修法可以直接照搬：**把 infobox 作为参数传进来，而不是从状态里读**。

### B. 打分目标里没有别名

`ExternalMatchService.match()` 约 466 行：

```kotlin
val bangumiTitles = listOfNotNull(subject.titleCN, subject.title)
```

infobox 里的别名**只被当成查询串**，不参与打分。于是：

- 用别名搜到了正确的条目；
- 但打分时拿 `titleCN` / `title` 去比 VNDB 的日文主标题 → 相似度低 → 分数不过阈值；
- 最终表现为「搜到了却显示没匹配上」。

`MatchQueryBuilder.build(subject, infobox)` 返回的本来就是「这部作品的全部名字」，
打分目标应当与查询串**同一份集合**。

### C. 阈值 0.92 让「有候选」看起来像「没匹配」

`AUTO_BIND_THRESHOLD = 0.92f`。一个正确但标题非完全一致的匹配很容易落在 0.82 左右
（标题相似度 0.90 x 0.78 + 年份一致 0.12 ≈ 0.82），于是不自动写库、
而 UI 在候选为空与候选未达阈值之间**没有明确区分**，用户看到的就是「没匹配上」。

## 1.2 待真机验证的假设（自己猜不出来，必须让 App 说出来）

| 编号 | 假设 | 验证方式 |
|---|---|---|
| H4 | VNDB 的 `search` 过滤器对中文标题无效（只索引日文/罗马音） | 诊断面板里逐个查询串看返回条数 |
| H5 | 该作在 VNDB 的主标题与 Bangumi `name` 差异较大（汉字写法不同，如 裁判/审判） | 诊断面板看 top5 标题与相似度 |
| H6 | 该作根本不是 GAME 类型（`loadVndbSupplement` 一开始就 `return`） | 诊断面板显示「跳过原因」 |

## 1.3 决策

- **D9 · 先做诊断面板，再改算法。** 详情页 VNDB 区块加一个可展开的「匹配诊断」，**逐条**显示：
  - 是否因类型不是 GAME 而跳过；
  - 实际用过的每个查询串 → 各返回几条 → top5 的标题/年份/得分/匹配理由；
  - infobox 是否已加载、是否命中 vndb id。
  一次真机截图就能定死是 A / H4 / H5 里的哪一个，避免继续改错地方。
- **D10 · 修 A（竞态）**：`loadVndbSupplement(infobox: List<InfoBoxEntry>)` 改为参数传入；
  并且匹配**必须在 infobox 那一路 await 完成后**才发（顺序 await，不再并行）。
  若 infobox 为空则显式记录原因，不再静默降级。
- **D11 · 修 B（打分目标）**：`bangumiTitles` 改为 `MatchQueryBuilder.build(subject, infobox)`；
  同时把 `subject.airDate` / `totalEpisodes` / `platform` 的取值抽成一处，避免两条链路各写一遍。
- **D12 · 增加 VNDB 的 OR 兜底查询**：现在只有 `["search", "=", q]`。
  增加 `["or", ["search","=",q], ["title","~",q], ["alttitle","~",q]]`
  （`~` 是子串匹配），作为每个查询串的**第二次尝试**，只在该查询串首次返回 0 条时使用——省额度。
- **D13 · 阈值分层，UI 说清区别**：
  - 自动写库阈值仍是 **0.92**（防错绑的教训不能丢）；
  - 但候选**永远展示**（含 0.5 以上的全部），每条带得分与理由，并给「就是它，绑定」按钮；
  - 文案区分三态：**没搜到候选** / **有候选但置信度不足（列出最高的那条）** / **已自动绑定**。
- **D14 · 关联反查（VNDB 独有）**：匹配失败时，允许从任意一条 VNDB 结果的 **relations** 里点进正主并绑定
  （第 4 轮 E 已经解析了 relations，缺的只是「从关联作品绑定」这个动作）。
- **D15 · 手动通道补齐**：粘贴 VNDB id / 链接（已有 `normalizeId`）保留；
  另加「用英文名/罗马音再搜一次」的快捷按钮（VNDB 对罗马音最友好）。

## 1.4 验收

- 诊断面板能完整说明「为什么没匹配上」；
- 《魔法少女的魔女审判》在真机上要么自动绑定，要么给出可一键绑定的候选；
- 新增单测：竞态修法（infobox 未加载时不发查询 / 加载后补发）、别名参与打分、
  同世界观作在别名参与打分后**仍然**低分（防回归，第 4 轮已有的用例要保住）。

---

# 问题 2 · 豆瓣剧照不可用

## 2.1 为什么到现在还定位不了

第 4 轮把自检做在了**设置页**，而且要用户主动去找、主动去点。结果就是：
用户看到的是「剧照区空着」，而不是「豆瓣返回了 302 / 400 / 997」。
**没有反馈通路的自检等于没有自检。**

已知事实（第 3 轮记录的实测）：

- `movie.douban.com/subject/{id}/photos` → 302 到 `sec.douban.com`（反爬）；
- `m.douban.com/rexxar/api/v2/...` → 400（校验 Referer）；
- `frodo.douban.com/api/v2/...` → 400 code 997（apikey 必须在 header）。

三条都指向同一件事：**用 OkHttp 伪装成浏览器打豆瓣，成功率很低。**
调 Referer / UA / apikey 是在猜，猜错一次就白等一个版本。

## 2.2 决策

- **D16 · 自检下沉到剧照区。** 只要「豆瓣通道已开启」且剧照为空，剧照区顶部就显示一张诊断卡：
  三条链路各自的状态码 / 耗时 / 最终 URL / 响应前 200 字，加一个「复制报告」按钮。
  设置页的自检保留，但不再是唯一入口。这是本轮**第一优先**改动。
- **D17 · 换通路：用 WebView 取图，不再用 OkHttp 硬打。** 这是本项的关键决策。
  用真实 `WebView` 加载 `movie.douban.com/subject/{id}/photos`（必要时先让用户在 WebView 里登录豆瓣），
  再用 `evaluateJavascript` 抽取页面里的 `img[src]`（`img*.doubanio.com/view/photo/...`）并回传。
  理由：WebView 是**真实浏览器引擎 + 真实 Cookie 罐 + 真实 UA**，
  访问一个普通剧照页属于正常用户行为，天然绕过我们在猜的那一堆 header 校验。
  同一条通路也可以用来做**豆瓣搜索**（加载 `www.douban.com/search?q=` 抽 `sid`）。
  - 复用第 4 轮已有的 `OAuthWebView` 经验（视口/第三方 Cookie/错误可见化）；
  - 隐藏式加载，不打断用户；有进度与失败态；
  - 仍然默认关、仍然标注「数据来源非官方」。
- **D18 · 剧照不再单吊豆瓣。** 先把其他来源补全并排好优先级，豆瓣只是锦上添花：
  TMDb backdrops（现有）→ 豆瓣（灰通道）→ B 站分集封面（现有）→ Anitabi（现有）
  → **Steam 截图**（新增接线）→ **VNDB 截图**（数据已有，确认是否接进了剧照区）
  → MUSIC 用 Cover Art Archive（新增，免费无 key）。
  目标是：**即使豆瓣永远修不好，剧照区也不该是空的。**
- **D19 · 兜底手动**：剧照区给一个输入框，可粘贴**豆瓣剧照页链接**或**图片直链**；
  前者走 WebView 通路，后者直接进 ImageViewer。
- **D20 · 参考系不必是 Bangumi-master。** 它取豆瓣剧照用的是「服务端预跑好的 id + 直连图床」，
  RN 项目没有可用的 WebView 抓取方案；这一项我们**不该**照抄，而应走 WebView。
  （这条要写进文档，避免下一轮又有人去对着它复刻。）

## 2.3 验收

- 在真机上能拿到一份**可复制的**自检报告（含状态码与响应片段）；
- 豆瓣绑定成功后剧照区**必须**有图，否则显示明确原因；
- 即使豆瓣关闭/失败，剧照区仍有 TMDb/B站/Anitabi/Steam/VNDB 的图；
- 新增单测：图片 URL 抽取（用固定 HTML fixture）、Cover Art Archive 响应解析、
  WebView 抽取脚本的正则/JS 片段（纯函数部分）。

---

# 问题 3 · 发现页「全部」被删 + 未对齐 Bangumi-master

## 3.1 精确根因（已读代码确认）

第 4 轮想「去掉发现页的『全部』tab」，但**改错了对象**：

1. 真正被改的是 `ui/common/SearchState.kt:76` 的 `SELECTABLE_CONTENT_TYPES`，
   它**只被搜索菜单**（`IosStyleSearchComponent.kt:524`）使用 →
   **搜索菜单的「全部」被删掉了**，这就是用户说的「搜索的全部标签也不再可用」。
2. 「找条目」用的是完全另一个常量 `FindSubjectsRepository.SUPPORTED_TYPES`（第 119 行），
   第 4 轮的改动**根本没碰到它**。
3. 更糟的是 `FindSubjectsFilter.type` 是**非空** `SubjectType`（默认 ANIME），
   也就是说找条目**永远无法跨类型检索**——想「按标签 + 评分」找全类型作品是不可能的。
   第 4 轮「type 是精确枚举所以不要全部」的理由是**误读**：
   `/v0/search/subjects` 的 `filter.type` 本来就是**可选**的，不传就是合法的「不限类型」。

结论：两个入口**都需要「全部」**，但语义不同——搜索的「全部」= 不过滤；找条目的「全部」= 不限类型。

## 3.2 Bangumi-master 的实际形态（已读源码，供对齐）

它的发现页**不是**一排顶栏 tab，而是：

- `screens/discovery/index/index.tsx`：列表页；
- 头部 `component/dashboard/`：奖项入口 + **菜单宫格**（`SortMenu`）+ 在线状态 + 今日放送；
- `constants/constants/data.ts:415` 的 **`MENU_MAP`** 定义菜单项与默认显隐，
  默认显示：**排行榜 / 找条目 / 每日放送 / 索引 / 目录 / 新番 / 标签 / Dollars / 日志 / 自定义**；
  隐藏项含：搜索 / 猜你喜欢 / 资讯 / 关联系列 / 社区项目 / 照片墙 / 我的词云 / 时间线 / 年鉴 / 我的人物 / 我的目录 / 本地备份 …；
- 菜单项**可自定义排序与显隐**（这就是「自定义」那个入口）；
- 每个菜单项是一个**独立页面**（`screens/discovery/{rank,anime,calendar,browser,catalog,tags,series,vib,...}/`）；
- 首页列表体是**按类型分组的区块**（`list/utils.tsx` 的 `renderItem` 按 `SubjectType` 出 `ListItem`）。

对比我们现在的实现：顶栏 56dp 里塞了「趋势 3 个 + 竖线 + 浏览 2 个」共 5 个 tab，
窄屏会挤；而且「找条目 / 评分月刊」是**页面内分支**而不是独立入口，与它的结构完全不同。

## 3.3 决策

- **D21 · 拆分常量、两边都恢复「全部」**：
  - `SEARCH_CONTENT_TYPES`：含 `ALL`（搜索用），
  - `FIND_CONTENT_TYPES`：含 `ALL`，且 **`FindSubjectsFilter.type` 改为可空**（`SubjectType?`），
    `bangumiType()` 在 null 时不写入 `filter.type`；
  - 两处都**不再共用一个常量**，从根上杜绝「改一个坏另一个」。
- **D22 · 搜索类型行改为横向可滚动的 chip 行**（而不是「一行 5 个图标格子」），
  放下 6 个类型（含全部）在小屏也不挤。
- **D23 · 发现页对齐 Bangumi-master 的形态**（细节见第 4 节的 **D28**）。
  **注意**：原来这里写的是「宫格取代现在的顶栏 tab」，已被 **D28 取代** ——
  用户的诉求是「**分出**现在的卡片形态和宫格版」，即**两种视图共存、可切换**，
  而不是把宫格变成唯一形态。
- **D24 · 「找条目」页对齐它的索引页交互**：
  - 顶部固定工具条：**类型 / 年份 / 月份 / 排序 / 筛选**，各自点开下拉或 sheet；
  - **改动即查询**，去掉现在的「应用并查询」按钮（Bangumi-master 是即时生效的）；
  - 保留触底分页，并补页码/总数展示；
  - 保留我们的增强：评分区间、排名区间、系列、NSFW；
  - 类型里补「全部」（D21）。
- **D25 · 顺手整理真实缺陷**：
  - `TrendingSection` 第 120 行的 `if (!state.trendingMode.isTrend) return` 是第 4 轮加的**补丁**，
    D23 之后应删除（入口改成独立页面后它不再收到 FIND/MONTHLY）；
  - `SubjectSearchScreen` 里 `DiscoverEntriesPane` 与 `TrendingSection` 两条分支重复计算 `contentTopInset`，合并；
  - `SearchState.kt` 中 `ContentType.ALL` 的 KDoc 仍在解释「它不再作为 tab 出现」，需按 D21 改写。

## 3.4 验收

- 搜索菜单里「全部」回来，且点它 = 不按类型过滤；
- 找条目里「全部」可用，且能「不限类型 + 标签 + 评分区间」检索；
- 发现页首页是宫格 + 区块，每个入口进独立页面；菜单顺序/显隐可改并持久化；
- 新增单测：`FindSubjectsFilter` 在 `type == null` 时不产出 `type` 过滤项；
  类型常量两两独立（不再共用）；菜单顺序持久化的序列化往返。

---

# 问题 4 · 当季热门收不到长连载 + 发现页多视图

## 4.1 Bangumi-master 是怎么做的（读源码得出，不是推测）

它用**两个完全不同的数据源**承担两件不同的事：

### (a) 每日放送 = 「正在播出」——这才是「当季热门」应有的语义

| 环节 | 位置 | 做法 |
|---|---|---|
| 接口 | `constants/api/index.ts:179` | `API_CALENDAR = () => API_HOST + '/calendar'`（旧接口，**返回当前正在放送的番组**，按星期分组） |
| 拉取 | `stores/calendar/fetch.ts:120` | `fetchCalendar()`，`list: true, storage: true` 持久化 |
| 合并 | `stores/calendar/computed.ts:33` | 与**云端 onAir** 数据合并，得到「星期几 / 放送时间（精确到分）/ **已放到第几集**」 |
| 集数 | `stores/calendar/fetch.ts:164-168` | 从 eps 里 status 为 `Air`/`Today` 的最后一条取 `sort` = 当前集数 |
| 星期 | `computed.ts:126-164` | 四级优先：**用户自定义 → 云端 onAir → 本地时区换算 → API 的 air_weekday** |
| 排序 | `computed.ts:73 calendarFlat` | 扁平化后**按放送时间**排 |

**关键：它按「是否正在放送」收录，完全不看「是否本季度开播」。**
所以《假面骑士zzz》这种播一年的特摄照样在榜，而且还能显示「已放到第几集」。

### (b) 首页「近期热门」区块 = 抓前端页面

`stores/calendar/fetch.ts:19 fetchHome()`：抓 `bgm.tv` 首页的 `<ul id="featuredItems">`
（官方首页的「近期热门」），解析成 **5 个类型各一列**：anime / game / book / music / real，
每条含 cover / title / subjectId / info。

展示形态（`screens/discovery/index/component/list-item/list-item.tsx`）：
**每个类型一个区块 = 第一张大封面 + 其余横向滚动的小封面**（`CoverLg` + `HorizontalList/CoverSm`），
下面还可能跟一行「好友在看」（`CoverXs`）。

### (c) 发现页首页整体

`screens/discovery/index/index.tsx` = `List` + 头部 `dashboard`：
**奖项入口 → 菜单宫格（`SortMenu`）→ 在线状态 + 今日放送（`Today`）**，
列表体是上面那种**按类型分组的区块**。

菜单项由 `constants/constants/data.ts:415 MENU_MAP` 定义，默认显示 10 项：
**排行榜 / 找条目 / 每日放送 / 索引 / 目录 / 新番 / 标签 / Dollars / 日志 / 自定义**；
宫格支持**拖拽排序与显隐**（`sort-menu`，存 `discoveryMenu` / `discoveryMenuNum`），
每一项进入**独立页面**。

## 4.2 Niriko 现在错在哪（已确认）

1. `SubjectSearchViewModel.kt:342` → `TrendingCalculator.computeSeasonDateRange()` = **当前季度**的 air_date 窗口；
2. 紧接着用 `subjectRepository.search(airDate = 本季度, rank = [">0","<=99999"], sort = heat)`
   → **只收「本季度开播」的作品**。上一个季度开播、现在还在播的《假面骑士zzz》**根本不在窗口里** → 永不展示。
3. `SubjectSearchViewModel.kt:530-538`：当季数量不足时用 `sort = "rank"` 的**历史排名**补足
   → 补进来的是「历来排名高」而不是「现在在播」，等于用无关数据把列表撑满。**用户已明确否掉这个做法。**
4. 真正的元凶是「语义搞错了」：把「当季热门」实现成了「本季度开播」，
   而 Bangumi（以及用户的直觉）说的是「**现在在播**」。

**而且全仓已经有正确的通路，只是没被用上**：

- `DataSource.kt:56 getCalendar()` —— Bangumi `/calendar`；
- `BroadcastFetcher` —— 已把它转成按星期分组的 `AiringSubject`，并用本地缓存补齐集数/封面/评分；
- `AiringStatus.shouldShowInWeeklyCalendar()` —— 只要求「是 ANIME/REAL 且有 airDate」，
  **故意不做完结判断**，注释写明「/calendar 本身已返回正确的周播数据，
  避免因集数估算偏差误判为 COMPLETED」。

这最后一条正是《假面骑士zzz》能上榜的原因 —— 而我们自己的当季热门偏偏没用它。

## 4.3 决策

- **D26 · 当季热门改用「正在放送」**：
  - 数据源：`remoteDataSource.getCalendar()`（复用 `BroadcastFetcher` 的转换与本地补齐逻辑，不重写一份）；
  - 排序：默认**按放送时间**（对齐 `calendarFlat`），并保留用户的排序选择（热度/评分/排名）作为可选；
  - **删掉「不足则用历史排名填充」**：不足就显示不足，并在底部给一个「查看历史排名 ›」的入口，
    **绝不用无关数据撑满列表**；
  - 类型筛选保留「全部 / 动画 / 三次元」，其中**「全部」= 动画 + 三次元都收**（`/calendar` 本来就同时含这两类）；
  - **不再做二次完结过滤**：`AiringStatus.getPhase` 对「无总集数的长期连载」用「180 天内首播」当 fallback，
    对播一年的特摄会误判成 COMPLETED。既然 `/calendar` 已经保证在播，就沿用
    `shouldShowInWeeklyCalendar` 的既有决定，**只信 calendar**。
- **D27 · 顺手把「放到第几集」做出来**：`今天 − 开播日` 的周数 + 1，配合 `totalEpisodes` 显示
  「第 N 话 / 共 M 话（约）」。Bangumi-master 用云端 onAir 的 eps 状态算，我们用算术算，
  **不引入新数据源**；标注「约」以免与官方误差引起误解。
- **D28 · 发现页多视图（用户明确要求）**：
  - 新增 `DiscoveryLayout { CARD, GRID }`，**两种形态共存、可切换**；
  - 顶栏右侧加一个切换按钮（卡片图标 / 宫格图标），选择持久化到 `AppSettings.discoveryLayout`；
  - **CARD 视图 = 现在的形态**（顶栏 tab + 全屏列表：当季热门 / 历史排名 / Steam / 找条目 / 评分月刊）；
  - **GRID 视图 = Bangumi-master 的形态**：
    - 顶部：奖项/专题入口（可先放「评分月刊」）；
    - **菜单宫格**（2 行 x 5，`MENU_MAP` 的本地版）：
      **排行榜 / 找条目 / 每日放送 / 标签 / 系列 / 评分月刊 / Steam / 索引 / 目录 / 自定义**；
    - 下方：**按类型分区块**（动画 / 书籍 / 游戏 / 音乐 / 三次元），
      每块 = 第一张大封面 + 横向滚动的其余封面（对齐 `list-item.tsx`）；
    - 宫格项进**独立页面**（新 route 或现有 route 的一个 mode）；
    - 宫格**顺序与显隐可自定义**（`AppSettings.discoveryMenuOrder` / `discoveryMenuHidden`），对应它的「自定义」；
  - 两个视图**共享同一份数据与状态机**，切换只换排版，**不重拉数据**。
- **D29 · 新增「每日放送」页面**（GRID 宫格项之一）：按星期分组的正在放送列表，
  显示放送时间与「第 N 话」。数据已有（`BroadcastFetcher`），这是长连载作品最自然的归宿。

## 4.4 验收

- 《假面骑士zzz》出现在当季热门里，且显示「第 N 话 / 共 M 话（约）」；
- 当季数量不足时列表就是短的，**没有**历史排名的填充项；
- 卡片/宫格两种视图可切换、可持久化，且切换时**不重新请求**；
- 宫格顺序与显隐可改并持久化；
- 新增单测：`/calendar` 响应 → 当季热门列表的映射（含无 totalEpisodes 的长连载**必须保留**）、
  「第 N 话」计算（跨年、开播当天、无 totalEpisodes）、
  **删除填充逻辑的回归测试**（数量不足时长度就等于实际条数）、视图/菜单顺序的持久化往返。

## 4.5 「当季热门」的具体实现方案（D26/D27 落地细节）

### 关键前提：`/calendar` 里到底有没有三次元，尚未验证

《假面骑士zzz》在 Bangumi 是**三次元**。Bangumi-master 里它能出现，可能是：

- 每日放送（`/calendar`）本身含三次元；**或**
- 发现页首页抓的 `#featuredItems` 里有独立的 `real` 一列（见 4.1(b)，这是确定的）。

我们**不能**假设 `/calendar` 一定含三次元。因此实现上采用**并集**，而不是把 `/calendar` 当唯一来源——
这样即使日历只回动画，长连载的三次元也不会丢。

### 数据通路

```
① 正在放送（权威）  remoteDataSource.getCalendar()            // 已有：GET /calendar → List<CalendarDaySchedule>
② 长连载兜底（推算） subjectRepository.search(
                      type = ANIME|REAL,
                      airDate = [今天-400天, 明天),        // 放宽窗口，不是「本季度」
                      sort = "heat", limit = 100)
        ↓ 合并去重（按 subjectId）
        ↓ 在播判定（纯函数，见下）
        ↓ 类型过滤（全部 = 动画 ∪ 三次元）
        ↓ 排序（默认热度）
        ↓ take(TRENDING_COUNT)   ← 不填充
```

`/calendar` 里出现过的条目 = **确认在播**，并且顺带拿到**放送星期**与 `air_weekday`；
只从 ② 来的条目 = **推算在播**，没有放送星期。两者都进列表，前者优先（同 id 时以 ① 的为准）。

### 在播判定（**不能**用 `AiringStatus.getPhase`）

`getPhase` 对「无总集数的长期连载」用「180 天内首播」当 fallback，
会把播一年的特摄误判成 `COMPLETED`。新增一个专用纯函数：

```kotlin
// data/seasonal/SeasonalTrendingCalculator.kt
fun isProbablyAiring(subject, today, windowDays = 400): Boolean {
    val airDate = parseDate(subject.airDate) ?: return false
    if (airDate > today) return false                       // 未开播不进「正在放送」
    if (isMovieOrOva(subject)) return false                  // 剧场版/OVA 不占周播位
    val total = subject.totalEpisodes
    return if (total != null && total > 0)
        getEstimatedEndDate(airDate, total) >= today          // 有集数：按集数估算
    else
        airDate >= today.minusDays(windowDays)                // 无集数：400 天窗口
}
```

### 放送到第几集（D27）

```kotlin
fun progress(airDate, totalEpisodes, today): Progress? {
    val weeks = ChronoUnit.WEEKS.between(airDate, today) + 1   // 开播当天 = 第 1 话
    if (weeks <= 0) return null
    val eps = totalEpisodes?.takeIf { it > 0 }?.let { min(weeks, it) } ?: weeks
    return Progress(current = eps, total = totalEpisodes?.takeIf { it > 0 })
}
```

文案：`第 12 话 / 共 50 话（约）`；无总集数时只显示 `第 12 话（约）`。
标「约」是因为我们用算术推算，Bangumi-master 用的是云端 onAir 的真实 eps 状态。

### 排序的可选项（受 `/calendar` 字段限制）

`SubjectDto`（`/calendar` 的 item）里**有** `rating`（分数 + 人数）、`images`、`date`/`air_date`、
`air_weekday`、`total_episodes`、`platform`、`type`；**没有 `heat`、也没有 `rank`**。

因此：

| 选项 | 依据 | 备注 |
|---|---|---|
| **热度（默认）** | `rating.total` 降序 | 最接近「热门」的可得指标；无评分排末尾 |
| 评分 | `rating.score` 降序 | 同上 |
| 开播日 | `date` 降序 | |
| 放送日 | `air_weekday` | 与「每日放送」一致 |
| 排名 | `subject.rank` | **只对本地缓存里有的条目有效**，缺的排末尾 |

### 缓存与新鲜度

- 复用 `RefreshResource.SEASONAL`（软 6h / 硬 7d）；
- **新增 `data/remote/CalendarCache`**：内存 + 时间戳，`BroadcastFetcher` 与当季热门**共用同一份 `/calendar` 响应**，
  避免统计页与发现页各打一次；
- 排序/类型切换**纯内存**，0 请求；
- 列表仍写回 `subjectRepository.upsertAll(...)`（照 `BroadcastFetcher` 的做法），
  让封面/标题/评分在其它页面也能命中缓存。

### 降级链

1. `/calendar` 成功 → 正常；
2. `/calendar` 失败 → 用 `CalendarCache` 的上次结果，UI 标注「数据可能过期」；
3. 仍然为空 → **只用 ②（放宽窗口 + 在播推算）**，UI 标注「放送日历不可用，以下为按开播日推算」；
4. 全失败 → 空态 + 重试，**绝不**回填历史排名。

### 改动清单

**新增**

- `data/seasonal/SeasonalModels.kt` —— `SeasonalSort` / `SeasonalTypeFilter` / `SeasonalItem` / `SeasonalProgress`
- `data/seasonal/SeasonalTrendingCalculator.kt` —— 纯函数：在播判定 / 进度 / 排序 / 类型过滤 / 去重合并
- `data/seasonal/SeasonalTrendingRepository.kt` —— 编排：拉日历 + 兜底检索 + 本地补齐 + 落库
- `data/remote/CalendarCache.kt` —— `/calendar` 响应的共享内存缓存
- `test/.../SeasonalTrendingCalculatorTest.kt`

**改动**

- `SubjectSearchViewModel.kt:522-539` —— SEASONAL 分支整体替换（**删掉 530-538 的填充逻辑**）
- `data/model/search/SubjectSearchUiState.kt` —— 加 `seasonalSort` / `seasonalTypeFilter` / `trendingSubtitle: Map<Long, String>`
- `ui/search/TrendingSection.kt` —— 标题行（`正在放送 · N 部`）、排序/类型 chips、卡片副标题、尾部「查看历史排名 ›」
- `data/remote/BroadcastFetcher.kt` —— 改用 `CalendarCache`
- `NirikoApplication.kt` —— 注册 `SeasonalTrendingRepository`

### 验收

1. 《假面骑士zzz》出现在当季热门（**无论 `/calendar` 是否含三次元**，靠并集都能进）；
2. 显示「第 N 话 / 共 M 话（约）」；
3. 数量不足 15 时列表就是短的，**没有**历史排名填充项，尾部有「查看历史排名 ›」；
4. 切排序/切类型**不产生网络请求**；
5. 单测覆盖：长连载保留、`isProbablyAiring` 的 400 天/集数两条分支、进度跨年与缺集数、
   **不填充回归**、类型过滤并集、`/calendar` 与兜底检索的合并去重（同 id 以日历为准）。

---

## 本轮的取舍（不做什么）

- **不做** Metacritic / Fami通 / Oricon / 批评空间 / RYM 的抓取：仍走外链 + 手动录入。
- **不做** WebDAV 新表同步（沿用第 4 轮 D7 的推迟决定）。
- **不做** Room 迁移测试（若本轮新增表则照旧逐字核对 schema，测试仍推迟）。
- **不动** 第 4 轮已通过的匹配阈值语义（0.92 自动写库），只加展示与手动通道。
- **不照抄** Bangumi-master 的豆瓣剧照方案（它没有客户端抓取能力，见 D20）。

## 数据库影响

预计需要一处迁移：

- 菜单自定义顺序若存 DataStore（`AppSettings`）则**不需要迁移**；
- 若「手动粘贴的豆瓣/图片链接」要落库，则新增 `subject_manual_media(subjectId, kind, url, referer)`
  → **v28 → v29**。

倾向：菜单顺序放 DataStore（零迁移）；豆瓣手动链接放 DataStore 的一个 JSON 字段
（数量少、无需查询）→ **本轮可以完全没有迁移**。除非实施时发现需要查询，再升级 DB。

## 真机验证清单（本机做不到）

1. VNDB 诊断面板的完整输出（《魔法少女的魔女审判》那条）；
2. 豆瓣剧照区的诊断报告（复制出来发我）；
3. WebView 取图通路是否真的能拿到图；
4. 发现页新形态在窄屏的排版；
5. 搜索菜单「全部」恢复后的实际检索结果；
6. **当季热门里能否看到《假面骑士zzz》**，以及「第 N 话」与官方是否吻合；
7. 卡片/宫格两种视图的排版（窄屏下宫格 2 行 x 5 是否放得下）。

## 交付顺序（建议分三批，每批都可单独验收）

| 批次 | 内容 | 依赖 |
|---|---|---|
| **第 1 批** | 问题 3（D21-D25）：纯 bug + 结构对齐，确定性最高 | 无 |
| **第 1.5 批** | 问题 4 的**当季热门语义**（D26-D27）：这是 bug，且不依赖 UI 重构 | 无 |
| **第 2 批** | 问题 1（D9-D15）：先诊断面板，再修竞态/打分/OR 查询 | 无（可与前两批并行） |
| **第 3 批** | 问题 2（D16-D19）：先把自检搬到剧照区，再做 WebView 通路 | 需要真机反馈第 2 步结果 |
| **第 4 批** | 问题 4 的**多视图 + 宫格 + 每日放送页**（D28-D29）：UI 体量最大 | 依赖第 1.5 批的数据层 |

每批结束跑 `assembleDebug` + `testDebugUnitTest`，并更新 `README.md` 与本文档的「实施状态」表。
