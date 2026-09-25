# 激进数据接入 + 单集二级页 + 评分卡合并 + 缺陷审计 · 方案（Niriko）

> **实施状态：阶段 A–F 已实施，阶段 E 部分实施**。`assembleDebug` + `testDebugUnitTest` 通过（**236 个单测全绿**）。
>
> **已完成**：
> - 阶段 A：BUG-1（TMDb 海报语言过滤）/ BUG-2（IMDb 开关失效）/ BUG-10（图片下载无 Referer、无超时、无大小上限）/ BUG-11 / BUG-12 / BUG-13（假的「标记 8 分」）；`RefreshResource` 死枚举清理并真正接入；删除死设置（lastFmApiKey）、死参数（useSeriesPosters）、重复的 `toTenPoint`；`highlights`/`volatilityLabel` 接到曲线卡。
> - 阶段 B：迁移 v26→v27（`episode_my_ratings.comment` / `rewatch`）+ `EpisodeDetailScreen` + `EpisodeDetailViewModel` + 路由 `episode_detail/{subjectId}/{epId}` + 三处入口（选集 chip 长按 / 剧集列表「单集详情 ›」/ 收藏编辑面板）+ 备份序列化。
> - 阶段 C：`RatingCarousel` 横滑部件（我的 / 社区 / 权威 / 分布 / 榜单成绩），并**去掉了 Bangumi·Bilibili 被展示两遍**的问题。
> - 阶段 D：B 站番剧分集封面（`BilibiliSeasonDto.episodes[]`，客户端早已在调该接口却把整段丢掉）→ 补齐缺失剧照。
> - 阶段 E（部分）：`DoubanClient` 三级 fallback（rexxar JSON → frodo JSON → HTML 解析）+ `DoubanHtmlParser`（纯函数 + 7 个单测）+ 设置项（默认关）+ Referer 贯通 Coil 显示与保存 + 候选确认绑定 UI。
> - 阶段 F（部分）：BUG-3（pruneStale 几乎不执行）/ BUG-5（选集上限 52）/ BUG-8（导入孤儿数据）。
>
> **未完成（明确记录）**：
> - BUG-6 `ExtendedSnapshot` 未含新字段（二次进详情页会重跑 TMDb 详情/剧照候选）；
> - BUG-7 封面覆盖仍存完整 URL（方案承诺的 `provider:path` 未做）；
> - BUG-9 WebDAV 未纳入新表（按决策 D7 单独排期）；
> - **迁移测试**（Room testing / Robolectric 或仪器测试）未补——这是本轮最遗憾的一项；
> - 按源限流闸门（IGDB 4 req/s、MusicBrainz 1 req/s）、`episodes(subjectId, type)` 复合索引；
> - 豆瓣的「防剧透翻页」只保留了设计（`start` 参数已就绪，未在调用侧启用）。
>
> 另：`ExternalRatingSection` 在阶段 C 之后不再被详情页调用（保留给非横滑布局），属于已知的待清理项。

> 状态：**待批准**。本文件只做方案，不含代码改动。
> 关联文档：`docs/authoritative-ratings-detail-parity-plan.md`（上一轮已实施）、`docs/rating-data-sources-research.md`（数据源实测）。

---

## 〇、本轮要解决的 5 件事

| # | 需求 | 结论 |
|---|---|---|
| 1 | **豆瓣剧照** | 可行，但需要「带 Referer 的图片加载 + 多级 fallback + 可配置头配方」。同时我发现一个**更好且完全正当**的补充源：**B 站番剧分集封面**（客户端已具备 UA/Referer，只差解析字段） |
| 2 | **UI 接入审计** | **没有全部接入**。查出 **2 个我上一轮引入的真 bug** + 6 处「接口写了但没人用」的死代码 |
| 3 | **单集二级页**（名称/截图/简介/评价/评分） | 需新增 `episode_my_ratings.comment` 列（v26→v27）+ 新路由 `episode_detail/{subjectId}/{epId}` |
| 4 | **评分卡与权威评分合并为横向可滑部件** | 现状是 3 个纵向区块信息重叠，抽成 `RatingCarousel`（6 张横滑卡） |
| 5 | **缺陷/优化审计** | 见第五节（含既有代码的 11 项） |

---

## 一、豆瓣剧照：可行性实证与接入方案

### 1.1 我实际验证到的东西（不是猜）

**① Bangumi-master 的「加密常量」只是混淆，密钥就在仓库里。**

`src/constants/cdn/ds.ts` 用 `Crypto.get(...)` 包住域名，`src/utils/crypto/index.ts` 的解密密钥是 `APP_ID`（`src/constants/constants/app.ts:9` = `bgm8885c4d524cd61fc`）。我用 EVP_BytesToKey(MD5) + AES-256-CBC 解出来：

```
HOST_DB         = "https://www.douban.com"
HOST_DB_MOVIE   = "https://movie.douban.com"
HOST_DB_M       = "https://m.douban.com"
HOST_DB_REFERER = "douban.com"        ← 加载豆瓣图片时用的 Referer
HOST_AC         = "https://www.bilibili.com"
HOST_AC_REFERER = "bilibili.com"
```

**结论：它就是直接打 douban.com，没有任何私有中转。** 它的「剧照」= HTML 抓取 `movie.douban.com/subject/{id}/photos?type=S&size=a&subtype=o`，取 `.cover img` 的 `src`，图片加载时带 `Referer: douban.com`。

**② 我实测了几个入口的真实返回（本机网络，非手机 IP）：**

| 入口 | 实测结果 | 判读 |
|---|---|---|
| `movie.douban.com/subject/1292052/photos?...` | **302 → `sec.douban.com`** | 触发豆瓣反爬「安全验证」。机房/云 IP 基本必挂，**住宅/手机 IP 通常能过**（Bangumi-master 长期可用即为佐证） |
| `m.douban.com/rexxar/api/v2/movie/{id}/photos` | **400 `invalid_request_1284`** | 端点存在，但**校验 `Referer`**（需 `https://m.douban.com/movie/subject/{id}/`） |
| `frodo.douban.com/api/v2/movie/{id}/photos?apikey=...` | **400 code `997`** | 端点存在，但 **apikey 必须放 header 且要匹配 UA/签名**（放 query 无效） |

**③ 所以豆瓣剧照的接入是「有条件的可行」，不是「填个 URL 就行」。**

### 1.2 方案：三级 fallback + 可配置头配方

**为什么不做成硬编码的一条路**：豆瓣随时会改校验；把「头配方」做成设置项，坏掉时用户自己就能修，不必等发版。

```
新增 data/remote/douban/
├── DoubanClient.kt            # 三级 fallback 编排 + 结果缓存
├── DoubanPhotoDto.kt          # rexxar/frodo JSON DTO
├── DoubanHtmlParser.kt        # 纯函数：从 photos HTML 抠出图片 URL（便于单测）
├── DoubanHeaders.kt           # 头配方（Referer/UA/apikey），来自设置
└── DoubanPhotoSource.kt       # 实现 ThumbSource 接口
```

**取数顺序（逐级降级，任一级成功即停）：**

1. **rexxar JSON**：`GET https://m.douban.com/rexxar/api/v2/{movie|tv|book|music|game}/{id}/photos?start=0&count=40&type=S`
   - 头：`Referer: https://m.douban.com/{type}/subject/{id}/`、浏览器 UA
   - 拿到 `photos[].photo.url` / `.large.url`（JSON，最干净）
2. **frodo JSON**：`GET https://frodo.douban.com/api/v2/{type}/{id}/photos?count=40&type=S`
   - 头：`apikey: {用户配置}`（默认填社区公开的那个）、`User-Agent: MicroMessenger/8.0.0`
3. **HTML 解析**：`GET https://movie.douban.com/subject/{id}/photos?type=S&size=a&subtype=o`
   - 用 `DoubanHtmlParser` 抠 `.cover img` 的 `src`；命中 `sec.douban.com` 直接判失败（不重试、不弹错）
4. 全失败 → **静默返回空**，剧照区只显示其它来源（不影响详情页）

**图片加载（关键）**：豆瓣图床有防盗链，Coil 必须带 Referer：

```kotlin
ImageRequest.Builder(ctx)
    .data(url)
    .addHeader("Referer", doubanHeaders.imageReferer)   // 默认 "https://movie.douban.com/"
    .addHeader("User-Agent", doubanHeaders.userAgent)
```

注意：Coil 的磁盘缓存键默认不含 header，跨来源同名 URL 冲突的概率极低（豆瓣图文 URL 自带唯一 p-id），但方案里仍然要求**按 header 值分目录缓存**（`diskCacheKey = url + "#" + referer`）作为稳妥做法。

**防剧透**（学 Bangumi-master 的一个聪明点）：豆瓣剧照按时间倒序，前几张常是后段剧情。策略：先取官方剧照（`type=S`）；若总数 ≥30，**从靠后位置翻页取**（`start = count - 30`），再倒序展示。

**Bangumi 词条 ↔ 豆瓣 ID 映射**：

- 优先：**infobox / summary 里的豆瓣链接**（部分词条有）→ 直接绑定，置信度 1.0
- 其次：豆瓣搜索 `https://www.douban.com/search?cat=1002&q={标题}` → 解析结果 → 复用 `TmdbMatchScorer` 打分（Bangumi-master 用 similar ≥ 0.7 + 年份一致）→ **只产候选，用户确认才写 `subject_external_ids(provider="douban")`**（与阶段 G 的保守匹配约定一致）

**设置项（新）**：

| 键 | 默认 | 说明 |
|---|---|---|
| `doubanPhotosEnabled` | **false** | 总开关。默认关——这是灰色通道，用户主动打开 |
| `doubanImageReferer` | `https://movie.douban.com/` | 图片防盗链 Referer |
| `doubanApiReferer` | `https://m.douban.com/` | rexxar 的 Referer 前缀（会拼 subject 路径） |
| `doubanUserAgent` | 桌面 Chrome UA | 部分入口校验 UA |
| `doubanFrodoApiKey` | 社区公开值 | frodo 的 apikey（header） |
| `doubanSpoilerGuard` | true | 防剧透翻页 |

**UI 上的诚实标注**：剧照区每张豆瓣图打 `豆瓣` 角标；设置页写「豆瓣剧照走非官方接口，可能随时失效；数据版权归豆瓣所有，仅供个人查看」。

### 1.3 我建议同时接入的「正当」源：B 站番剧分集封面

这是我这次翻代码的意外收获：`BilibiliRatingClient` **已经在调** `pgc/view/web/season`（免登录、UA+Referer 已配好、用户已有映射表 `bilibili_site_map`），但 `BilibiliSeasonDto` **只解析了 `rating`，把 `episodes[]` 全丢了**。

而该接口的 `result.episodes[]` 每项都带 `cover`（分集封面图）、`title`、`long_title`、`pub_time`、`duration`——**动画剧照的正规免费来源**。

所以剧照优先级建议为：

| 优先级 | 来源 | 性质 |
|---|---|---|
| 1 | TMDb backdrops + 每集 still | ✅ 已实现，官方 API |
| 2 | **B 站番剧分集封面** | ✅ 免 key、已有鉴权头，**新增**（性价比最高） |
| 3 | Anitabi 取景截图 | ✅ 已实现 |
| 4 | Steam 截图 | ✅ 已实现 |
| 5 | **豆瓣剧照** | ⚠️ 灰色通道，默认关，用户可开 |
| 6 | **用户本地上传剧照** | ✅ 100% 可靠，作为终极兜底（新增） |

### 1.4 风险披露（我必须说清楚）

- 豆瓣**没有**允许第三方抓取；`sec.douban.com` 就是明确的反爬。接入后随时可能失效，且**住宅 IP 可用、机房/VPN 出口大概率不可用**。
- 若将来把 Niriko 上架分发，**带豆瓣抓取会构成 ToS 风险**；因此默认关 + 设置页免责说明 + 可一键关闭。
- 我不建议把它做成「核心依赖」：剧照区必须**没有豆瓣也能正常工作**（现在的实现已经满足）。

---

## 二、UI 接入审计：上一轮的改动有没有都接到 UI？

**结论：没有全部接入。** 查出 2 个真 bug + 6 处死代码。

### 2.1 两个真 bug（我上一轮引入的）

**BUG-1（影响用户可见结果）`TmdbImageSelection.sortPosters` 会在不传语言时丢掉所有带语言的图。**

```kotlin
// 现状：调用方 posterCandidates() 不传 originalLanguageCode/metadataLanguageCode
val rules = listOfNotNull(...)          // → 空列表
val priority = when {
    isNoLanguage(code) -> rules.size    // → 0
    else -> rules.indexOfFirst { ... }  // → -1 → return@mapNotNull null  ← 带语言的图被全部丢弃
}
```

**后果**：换封面时 TMDb 候选**只剩「无语言」海报**（通常 1-3 张），中文/日文海报全被过滤掉——与 AniShelf「原语言 → 元数据语言 → 无语言」的排序意图相反。
**修复**：无 rules 时按「无语言优先 → 其余按宽度降序」排序，而不是丢弃；同时把作品的原语言（`airDate` 无法判断时用 TMDb `original_language`）传进来。

**BUG-2 `imdbEpisodeRatingsEnabled` 设置从不生效。**

`RatingSourceSettingsSection` 读写该开关，但详情页调用处是：
```kotlin
EpisodeRatingSection(imdbAvailable = state.tmdbBinding != null, ...)   // ← 只看绑定，没看开关
```
**后果**：用户关掉开关，IMDb 按钮照样出现（这是"设置骗人"，比缺功能更糟）。
**修复**：把开关放进 `RatingSectionCallbacks`/state，`imdbAvailable = binding != null && settings.imdbEpisodeRatingsEnabled`。

### 2.2 死代码 / 未接入清单

| 项 | 现状 | 处置建议 |
|---|---|---|
| `RefreshResource.TMDB_DETAIL / EXTERNAL_RATINGS / THUMBNAILS` | 只在枚举里定义，**无任何消费方**；VM 里另写了一个硬编码 `EPISODE_RATING_TTL_MS = 6h`（与 `EPISODE_RATINGS` 重复定义） | 让 `EpisodeRatingRepository`/`ExternalRatingRepository` 真正走 `FreshnessDecider`，删掉硬编码常量 |
| `TmdbRepository.posterCandidates(useSeriesPosters)` | 参数**无调用方，实现里也没用** | 要么实现「本季/整剧」切换（方案里承诺过），要么删参 |
| `lastFmApiKey` + `SOURCE_LASTFM` + `RatingSourceKeys.lastFmApiKey` | 设置项与常量都在，**没有 `LastFmRatingSource`** | Last.fm 只有 listeners/playcount（无评分）→ 建议**删掉设置项**，或实现为「热度」并明确标注非评分 |
| `EpisodeRatingAnalyzer.highlights / volatilityLabel / maxDeviation` | 写了 + 有单测，**UI 未用** | 接到曲线卡片（高光回/崩坏回、波动描述） |
| `RatingInsights.toTenPoint` | 与 `ExternalRating.toTenPoint` 完全重复 | 删一个（保留 calculator 层，remote 层委托） |
| `ExternalIdRepository.entitiesOf` / `TmdbClient.hasApiKey` | 无调用方 | 删，或用于多源绑定面板 |
| `RatingComparisonSection` ↔ `ExternalRatingSection` | 两块**信息重叠**（都展示 Bangumi/Bilibili 评分），纵向堆在一起显得重复 | 正是需求 4 要解决的 —— 合并为横滑部件 |
| `TmdbRepository.applyEndpoint()` 在多个方法里重复调用 | 轻微重复 | 抽到调用入口一次 |
| `CoverPickerDialog` 打开即请求候选 | 未配 TMDb key 时也会把 `coverCandidatesLoading` 置 true（无网络请求但会闪） | 先判 `isConfigured` |

---

## 三、单集二级页面（从收藏编辑面板进入）

### 3.1 数据模型（迁移 v26 → v27）

`episode_my_ratings` 现在只有 `score`，缺「评价」。新增一列：

```sql
ALTER TABLE `episode_my_ratings` ADD COLUMN `comment` TEXT;
```

（`ADD COLUMN` 可空、无默认值 → 与 Room 期望 schema 一致；已按 `app/schemas/.../26.json` 的写法核对过命名与可空性。）

顺带补两个「用户数据」字段（可选，见决策点 D3）：
```sql
ALTER TABLE `episode_my_ratings` ADD COLUMN `rewatch` INTEGER NOT NULL DEFAULT 0;  -- 二刷标记
```

### 3.2 新增路由与页面

```
navigation/NirikoNavHost.kt      composable("episode_detail/{subjectId}/{epId}")
ui/episode/EpisodeDetailScreen.kt
viewmodel/EpisodeDetailViewModel.kt
```

页面结构（自上而下）：

| 区块 | 内容 | 数据来源 |
|---|---|---|
| 头图 | 剧照（16:9，点击进全屏 `ImageViewer`）；无剧照时显示占位 | `episodes.stillUrl` / 剧照池 |
| 标题区 | `EP{n} · 中文名 / 原名`、播出日、时长、`Air/Today/NA` 状态、讨论数 | `EpisodeEntity` |
| **我的评分** | 0-10 步进（复用分享卡的星级/滑条样式），可清除 | `episode_my_ratings.score` |
| **我的评价** | 多行输入 + 自动保存（失焦/防抖 800ms），字数上限 2000 | `episode_my_ratings.comment`（新列） |
| 权威评分 | TMDb / IMDb 该集分数 + 票数（无则显示「无数据」并给「加载 IMDb 评分」入口） | `episode_ratings` |
| 本集简介 | `episodes.description`（上一轮刚从 Bangumi 补回来） | `EpisodeEntity.desc` |
| 快捷操作 | 「看到这里」（写回收藏进度）、上一集 / 下一集（同页内切换，不新建路由） | — |
| 外链 | Bangumi 章节页 `bgm.tv/ep/{epId}` | `PortalLauncher` |

**入口（三处）**

1. **收藏编辑面板的「选集」**：现在是 `SelectionChip(选中=已看, onClick=设进度)`。改为 **单击 = 设进度（保持既有习惯不变）；长按 = 打开单集二级页**；并在 chip 上加一个极小的「详情」角标提示可长按。
2. **详情页「剧集」列表**：每行右侧加「详情 ›」按钮（现在展开是行内面板，与二级页并存亦可；建议**行内面板保留简介，二级页承载评分与评价**，避免重复造两套输入控件）。
3. **评分走势曲线的点选气泡**：加「查看本集 ›」。

### 3.3 ViewModel 与仓储改动

- `ExternalRatingDao` 增加：`getMyEpisodeRating(epId)`、`upsertMyEpisodeRating`（已存在）、`updateMyEpisodeComment(epId, comment)`。
- `EpisodeMyRatingEntity` 增加 `comment: String?`、`rewatch: Boolean`。
- `EpisodeDetailViewModel`：读 `EpisodeEntity` + `episode_ratings` + `episode_my_ratings` + 剧照池；写评分/评价；「看到这里」复用 `CollectionRepository`。
- **备份**：`EpisodeMyRatingEntity` 已有备份通道，新增两列要同步进 `BackupManager` 的 `myEpisodeRatings` 序列化（含 `comment`/`rewatch`）。

---

## 四、评分卡合并为「横向可滑部件」

### 4.1 现状问题

详情页现在有三块分开且信息重叠的内容：

```
item("rating")            → RatingComparisonSection（我的/Bangumi/Bilibili）+ RatingDistributionChart（柱状图）
item("external_rating")   → ExternalRatingSection（多源权威评分 + 手动录入）
item("episodes_rating")   → EpisodeRatingSection（走势曲线 + 剧集列表）
```

用户要滑很久才能把「评分」这件事看完，且 Bangumi/Bilibili 被展示了两遍。

### 4.2 目标形态：`RatingCarousel`

一个横向 `LazyRow`，卡片顺序固定、可左右滑，每张卡一个主题：

| # | 卡片 | 内容 | 宽度 |
|---|---|---|---|
| 1 | **我的评分** | 大号分数 + 星级 + 「编辑」入口（打开收藏编辑面板） | 132dp |
| 2 | **社区评分** | Bangumi（分数/人数/排名）+ Bilibili（分数/人数）+ **争议度** + **本地库内百分位** | 200dp |
| 3 | **权威评分** | 多源 grid（TMDb/IMDb/Metacritic/Steam 好评率/IGDB/VNDB/MusicBrainz…），每源带来源名 + 标尺 + 人数，点击跳来源；末尾「＋」打开手动录入 | 240dp |
| 4 | **评分分布** | 现有的 1-10 柱状图（Canvas 复用） | 240dp |
| 5 | **评分走势** | 走势曲线缩略（Canvas 复用），点「展开」跳/滚到剧集区 | 260dp |
| 6 | **榜单成绩** | 手动录入的 Fami通/Billboard/Oricon + 「＋录入」 | 180dp |

**实现要点**

- 新组件 `ui/subject/RatingCarousel.kt`，把现有的 3 个 composable **改成内部卡片**（`RatingCommunityCard` / `RatingAuthorityCard` / `RatingDistributionCard` / `RatingTrendCard` / `RatingAwardCard`），旧组件名保留为薄封装或直接删除并更新调用点。
- 详情页只留 **一个** `item(key = "rating_carousel")`，位置放在「统计卡网格」之后、「角色」之前。
- 卡片高度统一（约 150dp），内部滚动/换行受控，保证横滑手感一致。
- 「全部/收起」：权威评分卡在源很多时可内部纵向滚动（现在是 `FlowRow` 自动换行，会撑高）→ 需要限高 + 内部滚动。

### 4.3 顺带修掉的重复

- 删除 `ExternalRatingSection` 里重复的 Bangumi/Bilibili 展示（只保留远端权威源）。
- `RatingComparisonSection` 的「我的评分」与收藏编辑面板的评分是同一个数据，卡片 1 只做展示 + 跳转，避免两处可编辑控件不一致。

---

## 五、缺陷与优化审计（含既有代码）

### 5.1 数据库与迁移（最高风险区）

**已做的核对**：我把 `app/schemas/.../26.json` 里每个新表的期望 `createSql` 与 `NirikoDatabase` 的迁移 DDL 逐条比对了：

| 表 | 期望 | 迁移 | 结果 |
|---|---|---|---|
| `episodes` | `description TEXT`(可空) / `disc INTEGER NOT NULL` / `stillUrl TEXT` | `ADD COLUMN description TEXT` / `disc INTEGER NOT NULL DEFAULT 0` / `stillUrl TEXT` | ✅ 一致 |
| `subject_external_ids` | `PRIMARY KEY(subjectId, provider)`，**无二级索引** | 同 | ✅ |
| `subject_external_ratings` | `PRIMARY KEY(subjectId, sourceId)`，无索引 | 同 | ✅ |
| `episode_ratings` | 复合主键 + `index_episode_ratings_subjectId` | 同 | ✅ |
| `episode_my_ratings` | `PRIMARY KEY(epId)` + `index_episode_my_ratings_subjectId` | 同 | ✅ |
| `manual_awards` | `id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL` + `index_manual_awards_subjectId` | 同 | ✅ |

唯一差异是 `episodes.disc` 我写了 `DEFAULT 0`（实体没声明 `@ColumnInfo(defaultValue)`）——Room 的 `TableInfo.Column.equals` 在「实体无默认值」时**跳过默认值比较**，且 `steam_games` 早有同样的先例（`DEFAULT '[]'`），因此判定为**安全**。

**AUDIT-1（既有）迁移完全没有自动化测试。**
- `app/schemas/` 里有 1..24、26（25 缺失是正常的：一次构建直接跳到 26，Room 只导出当前版本）。
- 但现在**没有任何测试验证 v24→v26 的迁移**。Room 的 schema 校验只在真机首次打开库时执行 —— **一旦不匹配就是用户侧崩溃**。
- 建议：加 `androidTestImplementation("androidx.room:room-testing")` + 一个 `MigrationTestHelper` 仪器测试（需要设备/模拟器），或引入 Robolectric 做 JVM 侧迁移测试。这是**本轮最值得补的一件基础设施**。

### 5.2 上一轮新增代码的缺陷

见 2.1（BUG-1 语言过滤、BUG-2 开关失效）与 2.2（死代码表）。

### 5.3 既有代码的缺陷

| ID | 位置 | 问题 | 影响 | 建议 |
|---|---|---|---|---|
| BUG-3 | `EpisodeRepository.fetchAndStore` | `if (entities.isNotEmpty()) dao.insertAll(...)` 与 `if (entities.isNotEmpty()) pruneStale()` 判断重复；且 `pruneStale` **只在有数据时**执行，`episodes` 表会无限增长 | 磁盘慢性膨胀 | 合并判断；`pruneStale` 改为独立的机会式清理（如启动时一次） |
| BUG-4 | `EpisodeRepository.getEpisodes` | `RefreshDecision.REVALIDATE` 分支被当作重建处理（注释已自认），缺少 stale-while-revalidate | 剧集页偶尔白等一次网络 | 先返回缓存，后台刷新后二次发射（与 `SubjectRepository` 一致） |
| BUG-5 | `ProgressInputSection` | `total in 1..52` 才显示选集；53 集以上**直接降级为纯数字输入** | 长篇（海贼/柯南/长篇国产）没有选集入口 | 上限提到 200，或改为「按季/按页」分片 |
| BUG-6 | `SubjectDetailViewModel.loadExtendedData` | `ExtendedSnapshot` **不包含**上一轮新增的字段（`episodeRatings`/`externalRatings`/`tmdbBinding`/…） | 二次进入详情页时会**重跑**这些加载（虽然多数走 DB 缓存，但 TMDb 详情/剧照候选会重复请求） | 把新字段纳入快照 |
| BUG-7 | `CoverOverrideStore` 存的是**完整 URL**，不是方案里承诺的 `provider:path` | TMDb 换尺寸档/域名后，已选封面可能失效；也无法按显示尺寸选档 | 与 `TmdbImageUrl` 的设计意图不一致 | 改存 `tmdb:/abc.jpg` 等引用 + 兼容读取旧值 |
| BUG-8 | `BackupManager.importFromJson` | 新表导入**不校验** `subjectId` 是否存在（旧表 collections 有校验） | 导入孤儿数据 | 加一层过滤 |
| BUG-9 | `SyncManager` | 只同步 collections / workItems / history；**`subject_external_ids`、封面覆盖、我的每集评分全部不同步** | 多设备下这些数据各设备一套 | 纳入 WebDAV JSON（工作量中等） |
| BUG-10 | `ImageViewer.saveImageToGallery` | 用 `URL(url).openStream()` 下载——**无 Referer、无超时、无大小上限** | 豆瓣图会 403；大图可能 OOM | 改走 OkHttp（带 header/超时）+ 流式写入 |
| BUG-11 | `CoverPickerDialog` | 打开即 `onRequestRemote()`，未判 `isConfigured` | 无 key 用户会看到短暂的 loading 闪烁 | 加判断 |
| BUG-12 | `ManualAwardDialog` | 选「榜位」类（Billboard/Oricon）时 `scoreMax` 存成 0f | 若同时填了分数，`displayValue` 会显示 `0 / 0` | 榜位类固定 `scoreMax = 0f` 但展示分支只认 `rankPosition`；补一个兜底 |
| BUG-13 | `EpisodeRatingSection` 的「标记 8 分」 | 只能标 8 分或清除，**不是真正的评分输入** | 功能名义存在但不能用 | 正是本轮需求 3 要做的（二级页给正经输入） |

### 5.4 性能与体验优化

| 项 | 现状 | 建议 |
|---|---|---|
| 走势曲线点数 | 单季 ≤ 26 点，Canvas 每帧重建 `Path` | 目前无问题；跨季（L2）时要加抽样 |
| `episodes` 表 | 只有 `subjectId` 索引 | 加 `(subjectId, type)` 复合索引（剧集列表按 type 过滤） |
| 评分源并发 | 全部并行（`async`），无全局闸门 | TMDb 有 429 处理，但 IGDB/MusicBrainz 有严格限流（4 req/s、1 req/s）→ 加**按源的并发/间隔闸门**（复用 `AsyncSerialQueue`） |
| Coil 缓存 | 封面按显示尺寸解码（已有）；剧照未做尺寸档 | 剧照统一走 `TmdbImageUrl.STILL_*` 尺寸档，避免拉原图 |
| 详情页长度 | 现在 17 个区块纵向堆叠 | 合并评分卡（需求 4）后可减少 2 个区块；「剧照」「剧集」「权威评分」「TMDb」建议默认折叠或顺序后移 |
| `RevealOnScroll` | 已有 | 新卡片也要挂上，避免一次性 compose 全部 |
| 刷新诊断页 | 已有 | 新资源（TMDb/每集评分/剧照）应出现在诊断页并可强制刷新（现在 `RefreshResource` 新增项没接） |

---

## 六、分阶段实施计划

### 阶段 A · 修 bug + UI 接入补全（0.5–1 天，风险低）
- BUG-1（TMDb 语言过滤）、BUG-2（IMDb 开关失效）、BUG-10（ImageViewer 下载）、BUG-11、BUG-12
- 死代码处理：`RefreshResource` 三枚举真正接入、删 `lastFmApiKey`、删重复 `toTenPoint`、`useSeriesPosters` 要么实现要么删
- `EpisodeRatingAnalyzer` 的 `highlights/volatilityLabel` 接到曲线卡

### 阶段 B · 单集二级页（1–1.5 天，风险中）
- 迁移 v26 → v27（`episode_my_ratings.comment` [+ 可选 `rewatch`]）
- `EpisodeDetailScreen` + `EpisodeDetailViewModel` + 路由
- 三处入口（选集长按 / 剧集列表 / 曲线气泡）
- 备份序列化补新列

### 阶段 C · 评分卡合并为横滑部件（1 天，风险中）
- `RatingCarousel` + 5 张卡片拆分；删除重复展示；详情页减少 2 个区块

### 阶段 D · B 站分集封面（0.5 天，风险低，性价比最高）
- `BilibiliSeasonDto` 补 `episodes[]`（cover/title/long_title/pub_time/duration）
- 剧照池合并 + `episodes.stillUrl` 回填（B 站优先于 TMDb？建议 TMDb 优先，B 站补空缺）

### 阶段 E · 豆瓣剧照（1.5–2 天，风险高）
- `DoubanClient` 三级 fallback + `DoubanHtmlParser`（纯函数 + 单测）+ 头配方设置 + Coil Referer
- Bangumi↔豆瓣 ID：infobox 优先 → 搜索候选 → 用户确认绑定（`provider="douban"`）
- 剧照池合并 + 「豆瓣」角标 + 防剧透翻页
- 设置项 + 免责说明

### 阶段 F · 遗留缺陷与基础设施（1–1.5 天）
- BUG-3/4/5/6/7/8（磁盘膨胀、SWR、长篇选集、快照遗漏、封面引用化、导入校验）
- **迁移测试**（Room testing + Robolectric 或仪器测试）
- 按源限流闸门；`episodes(subjectId, type)` 索引
- BUG-9（WebDAV 纳入新表）—— 工作量中等，可单独排期

**合计约 6–8 个工作日。**

---

## 七、待你确认的决策点

| # | 决策 | 选项 | 我的建议 |
|---|---|---|---|
| D1 | 豆瓣剧照默认开关 | 默认开 / **默认关** | **默认关**：灰色通道 + 可能随时失效，让用户主动开；同时剧照区在无豆瓣时功能完整 |
| D2 | 豆瓣 ID 映射 | 搜索自动绑 / **只产候选由用户确认** | **只产候选**，与阶段 G 的保守匹配一致（豆瓣同名作品极多，自动绑必错） |
| D3 | 单集二级页是否加「二刷标记」 | 加 / 不加 | 加（成本 1 列 + 1 个开关，收藏统计可用） |
| D4 | 单集入口交互 | 长按选集 chip / 单击改跳转 / 加独立按钮 | **长按**（保持单击=设进度的肌肉记忆），列表与曲线另加入口 |
| D5 | 剧照来源优先级 | TMDb → B站 → 豆瓣 / B站 → TMDb → 豆瓣 | **TMDb → B站 → 豆瓣**（TMDb 有官方剧照，B 站补空缺） |
| D6 | 评分卡合并后「我的评分」是否可直接编辑 | 只展示 + 跳转收藏面板 / 卡内直接编辑 | **只展示 + 跳转**（避免两处编辑控件状态不同步） |
| D7 | WebDAV 是否纳入新表（BUG-9） | 本轮做 / 单独排期 | **单独排期**（本轮已排满，且它不影响单机体验） |
| D8 | 是否引入 HTML 解析库 | 加 `jsoup`（约 450KB）/ 纯正则 | **先纯正则**（豆瓣 photos 页结构简单，`.cover img` 的 src 用正则足够；且能写成纯函数单测） |

---

## 八、验收清单

**构建与测试**
- [ ] `.\gradlew.bat assembleDebug` + `testDebugUnitTest` 通过
- [ ] 新增单测：`DoubanHtmlParser`（正常页/反爬页/空页/分页）、`TmdbImageSelection.sortPosters`（无 rules 不再丢图）、剧照池合并优先级
- [ ] **迁移测试**：v26 建库 → 迁移 v27，断言 `comment` 列存在、旧数据保留

**功能**
- [ ] 未开豆瓣开关时：剧照区正常（TMDb + B站 + Anitabi + Steam），无任何豆瓣请求
- [ ] 开启豆瓣后：能命中则显示带「豆瓣」角标的剧照；命中反爬/失败时**静默降级**、不弹错、不卡详情页
- [ ] 更换封面：TMDb 候选**包含中文与日文海报**（BUG-1 已修），语言与分辨率角标正确
- [ ] 关掉「IMDb 逐集评分入口」后，剧集区**不再出现**该按钮（BUG-2 已修）
- [ ] 收藏编辑面板长按任一集 → 进入单集二级页 → 显示名称/剧照/简介；输入评价与评分后返回，重进仍在
- [ ] 单集二级页的「看到这里」能正确写回收藏进度
- [ ] 详情页评分区变成一张横滑卡组，左右滑动能看到 6 张卡；不再出现 Bangumi 评分展示两遍
- [ ] 全屏查看器保存豆瓣图成功（带 Referer）

---

## 九、风险登记

| 风险 | 影响 | 缓解 |
|---|---|---|
| 豆瓣反爬（`sec.douban.com`） | 豆瓣剧照全部不可用 | 默认关；三级 fallback；住宅 IP 更可能成功；**剧照区不依赖豆瓣** |
| 豆瓣 API 头配方失效 | 同上 | 头配方做成设置项，用户可自行修，不必发版 |
| 上架后 ToS 风险 | 合规 | 默认关 + 设置页免责声明 + 可一键关闭；若将来上架，建议移除或仅在本地构建保留 |
| 迁移 v27 出错 | 用户数据丢失 | 只做 `ADD COLUMN`（可空）；补迁移测试 |
| 单集二级页引入新路由 | 导航栈变复杂 | 复用既有 detail 路由模式；上一集/下一集在页内切换而非压栈 |
| 评分卡合并造成回归 | 详情页视觉回归 | 保留旧组件为薄封装，逐个卡片迁移，最后删壳 |

---

## 十、需要真机验证的点（我本机验证不到）

1. 豆瓣 rexxar / frodo 在**真实手机网络 + 正确头**下是否返回 200（本机为机房 IP 且 web_fetch 不能带头，只能验证到「端点存在但校验头」）。
2. 豆瓣图床带 `Referer` 后能否稳定加载（Coil 侧）。
3. B 站 `pgc/view/web/season` 的 `episodes[].cover` 在**港澳台限定**番剧上是否仍可访问（`area_limit` 场景）。
4. 豆瓣搜索页在手机 IP 下是否也跳 `sec.douban.com`（若非，则 ID 匹配链路也要走 fallback）。
