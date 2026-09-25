# 修复与重做方案（第 4 轮）· 实施记录

> 承接 `fix-and-redesign-plan-round3.md`。第 3 轮的 A/B 阶段已完成；本文件记录**剩余全部项**的实施方案与实施结果。

## 基线 → 结果

| 项 | 前 | 后 |
|---|---|---|
| `assembleDebug` | ✅ | ✅ |
| `testDebugUnitTest` | ✅ 198 个 testcase | ✅ **300 个 testcase，0 失败** |
| DB version | 27 | **28**（新增 `rating_snapshots`） |

## 实施状态（全部完成）

| 阶段 | 内容 | 状态 |
|---|---|---|
| R4c | Bangumi OAuth 登录 + 公共 `OAuthWebView` | ✅ 完成 |
| C | TMDb 覆盖扩展 + 手动入口 + 差异化折叠 | ✅ 完成 |
| H | OMDb/IMDb 逐集可用性诊断 + 季号写回 + Season/Episode 兜底 | ✅ 完成 |
| D | 统一匹配服务 `ExternalMatchService` + `ProviderBindingSection` | ✅ 完成 |
| E | VNDB 差异化数据（时长/贝叶斯/语言/平台/标签权重/relations/截图） | ✅ 完成 |
| G | 发现页重做（去「全部」tab + 找条目 + 评分月刊） | ✅ 完成 |
| F | `Probe` 框架 + 豆瓣自检/ID 来源/Referer + AniList 自动评分 | ✅ 完成 |

---

## R4c · Bangumi OAuth 登录

**新增**：`data/remote/bangumi/BangumiOAuthClient.kt`、`ui/common/OAuthWebView.kt`

- `buildAuthorizeUrl` / `exchangeCode` / `verify` 三段式；token 响应**同时兼容 JSON 与裸 token**两种部署形态（官方 v0 文档是 JSON，部分部署直接回 token 串）。
- 换 token 走 `BangumiClient.authOkHttpClient`（固定官方域、带 UA、**不挂** baseUrl 重写拦截器）——`/oauth/access_token` 返回非 JSON，不能走 Retrofit 序列化转换器，因此暴露该 client 复用配置。
- `OAuthWebView` 把第 3 轮定位的根因一次做对：`useWideViewPort` + `loadWithOverviewMode` + 缩放 + **第三方 Cookie** + `onReceivedError` 可见化 + 加载进度 + 页标题。**未使用 `setInitialScale`**（本环境编译期 unresolved，见踩坑 9）。
- 设置项 `bangumiClientId` / `bangumiClientSecret` / `bangumiRedirectUri`，**由用户自建应用，不内置**。授权成功后调 `/v0/me` 校验并显示用户名。
- 数据源页新增「NSFW 检索：已登录（v0 可用）/ 未登录（走旧版兜底）」状态行——此前这个信息只在失败时隐约可见。

## C · TMDb 三件事

**改动**：`TmdbRatingSource`、`RatingSource`、`RatingSourceRegistry`、`RatingSourceKeys`、`TmdbRepository`、`TmdbBindingSection`、`SubjectDetailViewModel`

1. **覆盖扩展**：`RatingSource` 新增 `isAvailableFor(type, keys)`；`TmdbRatingSource` 覆写它，在用户打开「TMDb 覆盖游戏/书籍/音乐」（新设置项 `tmdbIncludeNonTvTypes`，**默认关**）后，GAME/BOOK/MUSIC 走 **TMDb movie** 端点。`RatingSourceKeys` 增加 `tmdbIncludeNonTvTypes` 以便注册表按用户偏好派发。
2. **永远有手动入口**：`TmdbBindingSection` 不再因「候选为空」整块消失；新增「搜索 TMDb」（关键词真的被使用——改造前 `searchMoreTmdb(keyword)` **从未用过 keyword**，只是按作品标题重搜一遍）与「粘贴 ID / 链接」（`parseIdOrUrl` 支持 `42509` / `tv/42509` / `movie/129` / 完整链接）。
3. **差异化展示 + 默认折叠**：绑定后只留 TMDb 独有的 `networks` / `production_companies` / `status`（译为中文「连载中/已完结」）/ `episode_run_time` / `external_ids`（IMDb/TVDB/Wikidata）/ 多语言海报入口 / 每集评分入口；**移除**名称、首播日、集数、整剧评分（Bangumi 全都有）。

## H · OMDb / IMDb 逐集可用性

**改动**：`EpisodeRatingRepository`、`OmdbRatingSource`、`EpisodeRatingSections`、`SubjectDetailViewmodel`

1. **逐项诊断**：新增 `ImdbEntryStatus`（含 `missing: List<String>`），UI 在剧集区**始终**渲染状态行，不可用时逐条列出「还缺什么」并给「去设置」按钮。改造前任一条件不满足就整块隐藏且不说原因。
2. **季号写回**：`resolveAndPersistSeason` 在自动挑季后把结果写回绑定的 `subKey`。改造前只有自动挑季、**不写回**，于是 `loadImdbEpisodeRatings` 里 `subKey` 恒为 null → 直接 FAILED（用户配好一切也不可用）。
3. **OMDb Season/Episode 兜底**：新增 `OmdbRatingSource.fetchEpisodeBySeries`，某集在 TMDb 上没有 `external_ids` 时改用「整剧 IMDb id + Season/Episode」直接问 OMDb。加载结果回报 `viaOmdbFallback` 计数并在 UI 显示。
4. 另加 `verifyKey`（OMDb 轻量校验，对齐 TMDb 用 `/3/configuration` 的做法）。

## D · 统一匹配服务

**新增**：`data/match/ExternalMatchService.kt`、`data/match/VndbProviderMatcher.kt`、`ui/subject/ProviderBindingSection.kt`

四层：

| 层 | 手段 | 说明 |
|---|---|---|
| L1 | **infobox 明确 ID** | `extractInfoboxId`，置信度 1.0，命中后**不再发查询**（省额度） |
| L2 | **多查询串** | `MatchQueryBuilder.build`：中文名 → 原名 → infobox 别名 → 纯 ASCII 变体；有高置信度命中即提前停止 |
| L3 | **多字段打分** | `MatchScorer`：标题相似度 ×0.78 + 年份（冲突 ≥3 年 **-0.25**）+ 集数 + 平台；完全一致给 0.95 下限；输出**匹配理由**供 UI 展示 |
| L4 | **手动兜底** | `searchByQuery`（不过滤低分候选——用户自己搜的东西必须展示）/ `byId`（粘贴 ID） |

`ProviderBindingSection` 取代 TMDb / VNDB / AniList 各写一套的绑定 UI；已接入 **AniList 槽位**（`state.anilistCandidates` 映射为 `MatchCandidate`，并新增「粘贴 AniList id」通路）。VNDB 与 TMDb 保留各自更专用的区块（VNDB 有 relations 展示、TMDb 有季选择），但共用同一套匹配服务与打分口径。

## E · VNDB 差异化数据

**改动**：`VndbDtos`、`VndbRepository`、`VndbSearchSupport`（新增）、`SubjectDetailScreen.VndbInfoSection`

- **保留（VNDB 独有）**：平均游玩时长 / 贝叶斯评分 + 票数 / popularity / 原语 + 支持语言（`vndbLanguageName` 本地化）/ 平台（`vndbPlatformName`）/ **按权重排序的标签**（改造前取前 8 个且未排序）/ 截图 / **relations**。
- **移除**：整剧评分（已有权威评分卡）、开发商、发售日、简介（改为可展开放在最后）。
- **relations 解析**：VNDB 只给 id 与关系类型，`VndbRepository.resolveRelationTitles` 用 `["id","in",[...]]` **一次批量**补齐标题（省额度），映射成「续作/前作/同世界观/外传/同系列/另一版本/共用角色/同人/原作」；**点击即绑定**——这正是「同世界观作能匹配上 → 反查未匹配作品」的入口。

### 匹配修复（用户报告的具体缺陷）

根因：`VndbGameDataSource.search` 只用**一个**标题（`displayTitle`，中文优先）+ `["search","=",q]`，且 VNDB 返回的 `titles[]` **完全没参与打分**。于是中文名在 VNDB 里不是主标题 → 搜不到；而标题恰好相似的同世界观作反而命中。

修复：`VndbSearchSupport`（多查询串搜索 + 合并去重 + 打分）被 `VndbGameDataSource.search` 与详情页匹配**共用**；`toRawCandidate` 把 `titles[]` 的**全部**语言标题（含 `latin` 罗马音）放进 `RawCandidate.titles`，打分器对全部标题取最优。单测 `ExternalMatchServiceTest` 直接覆盖这个场景（多标题命中 ≥0.80；只给日文主标题时 <0.80；同世界观作 <0.92）。

详情页自动写库阈值取 **0.92**：用户诉求是「该匹配上的要匹配上」，但过去的教训是不能放开到 0.7（同世界观作会顶掉正主）。

## G · 发现页重做

**新增**：`data/discover/FindSubjectsFilter.kt`、`data/discover/FindSubjectsRepository.kt`、`viewmodel/DiscoverViewModel.kt`、`ui/search/DiscoverEntriesPane.kt`、`data/local/entity/RatingSnapshotEntity.kt`、`data/local/dao/RatingSnapshotDao.kt`、`data/repository/RatingMonthlyRepository.kt`

1. **去掉「全部」tab**：`ContentType.ALL` **不再出现在类型行**（`SELECTABLE_CONTENT_TYPES` 只列 5 个真实类型）。Bangumi 的 `filter.type` 是精确枚举（1..6），没有「全部」这个能力——选中它等于不加类型过滤，结果质量差、翻页无意义。`ALL` 退化为「未选中任何类型」的内部状态，通过「再点一次已选类型」回到。
2. **找条目**：类型 tab（无「全部」）+ 年份区间 + 季度 + 标签 + 评分区间 + 排名区间 + 系列 + NSFW + **5 种排序**（匹配度/热度/排名/评分/发售日）+ 触底分页。`SearchFilterDto` 扩展 `rating`（字符串比较表达式 `">=8"`）；`air_date` 由年份+季度组合成精确三个月区间。用**实时** `/v0/search/subjects` 复刻 Bangumi-master「找条目」的**筛选能力**（它用的是季度更新的静态数据，数据源无法照搬）。
3. **评分月刊**：本地快照表 `rating_snapshots(subjectId, month, score, rank, total)`（迁移 **v27→v28**）。每月首次进入发现页时为「已收藏且有评分」的作品记一次；月刊列出本月按评分降序的榜单，并给出与**上一个有数据的月份**的 `scoreDelta` / `rankDelta`。这是 Bangumi-master `vib` 的本地版。
4. **标签**：筛选候选来自本地收藏作品的标签（零请求，用户真正关心的），不足时用内置高频标签补齐。
5. 顶栏分「趋势」（当季热门/历史排名/Steam）与「浏览」（找条目/评分月刊）两组，中间加竖线分隔。

## F · 灰色通道框架

**新增**：`data/probe/ChannelProbe.kt`、`data/probe/Probes.kt`、`data/probe/GrayChannelRunner.kt`、`data/probe/DoubanHtmlFixtures.kt`、`ui/settings/GrayChannelSettingsSection.kt`、`data/remote/rating/sources/AniListRatingSource.kt`

1. **自检（这一项是整个 F 的前提）**：设置页「运行连通性自检」对每个端点**逐个**发请求，**原样**显示 HTTP 状态码、耗时、最终 URL（302 目标）、Content-Type、以及**响应前 400 字节**。没有这个，豆瓣问题只能盲猜。三条豆瓣探针（rexxar JSON / frodo JSON / HTML 剧照页）分别标注了各自预期的失败特征。
2. **豆瓣 ID 来源改为三级**：infobox 明确链接（**直接绑定**，零猜测）→ 搜索 + 高阈值匹配（**≥0.86 且年份一致**才自动绑定）→ 其余只展示候选；另有**手动粘贴 id / 链接**的终极兜底（评测页剧照区新增输入框）。这修正了第 3 轮「把服务端预跑任务实现成客户端实时搜索」的设计误判。
3. **图片 Referer 改为 `https://douban.com`**（不带 path、不带尾斜杠）——Bangumi-master 的实测值；带 path 的 `movie.douban.com/` 反而被图床拒。设置页可改。
4. **fixture 单测**：`DoubanHtmlFixtures` 固化三类真实响应形态（正常剧照页 / 反爬页 / 带 HTML 实体页），`DoubanFixtureParsingTest` 断言解析结果。页面改版时测试会红，而不是静默产出空白剧照区。顺带修掉一个真 bug：总数正则是 `共(\d+)张`，**不认「共 42 张」**（豆瓣模板在两种写法间变动过），已改为 `共\s*(\d+)\s*张`。
5. **AniList 自动评分**：`AniListRatingSource` 作为正常 `RatingSource` 接入（无需 key、`isAvailable` 恒真）。已绑定则按 id 精确取分；**未绑定则自动匹配，高置信度（≥0.72，比展示阈值更严因为直接显示分数）时只展示分数、不写绑定**——满足「能匹配就该看到分」，同时不破坏阶段 G 的保守写库约定。`averageScore` 是 0-100 制，`scoreMax=100` 原生保留，同时给 10 分制换算值供同屏排序。
6. 框架要素齐备：默认关、可配置头（UA/Cookie/Referer）、统一「数据来源非官方」风险标注、静默降级（失败不影响其它剧照来源）、fixture 单测。

### 未做抓取的源（按方案决策保留为「外链 + 手动录入」）

Metacritic / Fami通 / Oricon / 批评空间 / RYM / AOTY：投入产出比低、维护成本高，仍走 `manual_awards` 手动录入 + 外链门户。

## 数据库

迁移 `27→28`：仅 `CREATE TABLE rating_snapshots`，不动任何既有表。

DDL **逐字对齐** Room 生成的 `app/schemas/.../28.json` 的 `createSql`（已核对）：`recordedAt` 是 `INTEGER NOT NULL`，**没有 DEFAULT**——迁移里也不能加，否则两者不等价。Room 的 schema 校验只在真机首次开库时执行，编译期不会报错。

`rating_snapshots` **不进备份 / 不同步**：全是服务端可重新取得的公开数据，且随月份线性增长（收藏 500 部 = 每月 500 行），放进 JSON 备份会让文件随使用时间无限膨胀。

## 新增单测（198 → 300）

| 文件 | 覆盖 |
|---|---|
| `data/match/ExternalMatchServiceTest` | **匹配缺陷回归**：多语言标题集合参与打分、只给主标题会失配、同世界观作不误判、年份冲突扣分、过阈值语义、infobox 优先且不发查询、多查询串去重取最高分、手动搜索不过滤低分、未知 provider 不崩 |
| `data/discover/FindSubjectsFilterTest` | 年份/季度 → air_date（含跨年、单边、年份不等时退化）、评分/排名区间格式与夹取、条件存在性、类型集合无「全部」、5 种排序 apiValue 互异 |
| `data/remote/bangumi/BangumiOAuthClientTest` | 授权 URL 编码（`redirect_uri` 必须被编码）、JSON/裸 token 两种响应、错误/空/多词响应返回 null |
| `data/remote/douban/DoubanFixtureParsingTest` | 三类 fixture 解析、反爬识别、HTML 实体解码、默认 Referer 是不带 path 的 `douban.com` |
| `data/probe/GrayChannelProbeTest` | 五源注册表、豆瓣三链路齐备且各自标注预期、Referer 拼接、frodo apikey 在 header、自检不受开关限制、未开启时产出 SKIPPED 而非静默丢弃、结果摘要含状态码与耗时 |

## 待真机验证（本机无法完成）

本机 pwsh 不能直连外网、`web_fetch` 不能带自定义头，因此以下**只能真机确认**：

1. **Bangumi OAuth**：需要用户在 bgm.tv 自建应用后走完整授权流程（含 token 端点到底返回 JSON 还是裸串）。
2. **豆瓣自检输出**：设置页点「运行连通性自检」，把状态码与响应摘要发回来即可定位——这是豆瓣通道能否真正可用的唯一判据。
3. **NSFW 检索**：登录后 `nsfw=true` 是否即时生效；未登录时旧版接口兜底是否仍能检出 NSFW 条目。
4. **TMDb / OMDb 真实数据**：候选匹配质量、每集评分对齐率、`viaOmdbFallback` 实际命中数。
5. **VNDB**：《魔法少女的魔女审判》能否匹配上（多查询串 + 全标题打分已就位，但真实标题集合需实测）。
6. **登录视口**：Steam / Bilibili / Bangumi 三个 WebView 的实际显示大小。
7. **评分月刊**：需要跨月才能看到 `scoreDelta` / `rankDelta`（首月只有榜单）。

## 已知遗留

- **WebDAV 同步未纳入新表**（`rating_snapshots` 按设计排除；`subject_external_ids` / `episode_my_ratings` / `manual_awards` 仍未进 WebDAV，JSON 备份已包含）。
- **迁移测试未补**（Room testing / Robolectric）——本次迁移已逐字核对 schema，但仍建议后续补真机或 Robolectric 迁移测试。
- **`ExtendedSnapshot` 未含新字段** → 二次进详情页会重跑 TMDb 详情/剧照候选。
- 按源限流闸门（IGDB 4 req/s、MusicBrainz 1 req/s、VNDB 200/5min）仍未做——本次已通过「高置信度提前停止查询」「relations 批量查询」降低请求量，但没有硬闸门。
- 发现页「系列」入口：筛选条件里已支持 `series` 布尔，但**独立「按系列聚合浏览」入口未做**（Bangumi 无「列出所有系列」的端点，需要额外设计）。
- 豆瓣「防剧透翻页」参数已就绪但调用侧未启用。
