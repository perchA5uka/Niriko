# 修复与重做方案（第 3 轮）

> **实施状态：阶段 A + B 已完成**，`assembleDebug` + `testDebugUnitTest` 通过（236 单测全绿）。
>
> **已完成**：
> - R1 剧集落库：详情页改走 `EpisodeRepository`（缓存+落库+TTL）；单集页加「库里没有就按 subjectId 拉一次远端」兜底。→ 单集详情、每集剧照、B站/TMDb 剧照回填同时被修好。
> - R2 洞察时序：`loadRatingInsights(distribution)` 改为接收参数，并在 `ratingDistribution` 写入之后（含缓存命中路径）调用。→ 争议度不再恒为空。
> - R3 长按：选集 chip 从 `FilterChip` 改为自绘 `Surface + combinedClickable`。
> - R4a/R4b 登录 WebView：Steam 补 `useWideViewPort`/`loadWithOverviewMode`/缩放/第三方 Cookie/`onReceivedError` 可见化；Bilibili 登录容器由写死的 300dp 改为 360–560dp。
> - R5 NSFW 兜底：新增 Bangumi **旧版搜索**（免 token、含 NSFW）作为 `nsfw=true` 时的通道。
> - B 评分区回退：撤掉 5 张横滑卡，恢复「一张评分卡（我的/Bangumi/Bilibili + 争议度 + 百分位）+ 评分分布 + 权威评分」；删除 `RatingCarousel.kt`。
>
> **未完成（本轮剩余）**：R4c Bangumi OAuth 登录、C TMDb 覆盖/手动入口/差异化折叠、H OMDb 可用性提示、D 统一匹配服务、E VNDB 差异化数据、G 发现页重做、F 灰色通道框架。


> 状态：**待批准**。本文件只做方案，不含代码改动。
> 本轮针对你反馈的 14 个问题；**其中 5 个我已经定位到确切根因**（不是猜测），列在第一节。

---

## 〇、结论速览

| # | 你反馈的问题 | 根因 | 性质 |
|---|---|---|---|
| 1 | 单集详情不可用 | **剧集从未落库**：详情页直接读远端、不写 `episodes` 表，而单集页从库里读 | 我上轮的实现缺陷（连带影响剧照） |
| 2 | 部分作品没有 TMDb 匹配 | `TmdbRatingSource.subjectTypes` 只覆盖 ANIME/REAL/OTHER；且未绑定时无手动入口 | 设计缺口 |
| 3 | TMDb 数据与其它源雷同 | 绑定后展示的是名称/首播/评分等 Bangumi 已有的字段 | 设计问题 |
| 4 | 豆瓣剧照完全不可用 | 见 §3：我的三级 fallback 有两处会必然失败，且无法在本机验证网络 | 实现缺陷 + 环境 |
| 5 | 账号登录网页过小 / 登不上 | Steam WebView **缺视口设置**；Bilibili WebView 容器只有 **300dp**；Bangumi **根本没有 OAuth 登录 UI** | 确切根因 |
| 6 | OMDb 配了但 IMDb 逐集不可用 | 入口要求「总开关 && IMDb 开关 && OMDb key」三者齐备，且需要已绑 TMDb；失败原因不显示 | 交互缺陷 |
| 7 | VNDB 需要差异化数据 | 现有区块展示的评分/开发商/平台 Bangumi 大多也有；VNDB 独有字段未展示 | 设计缺口 |
| 8 | 收藏面板长按单集无反应 | `FilterChip` 自己消费点击，`Modifier.combinedClickable` 不生效 | 确切根因 |
| 9 | 评分区太细碎 | 是我上一轮做成了 5 张横滑卡 | 设计回退 |
| 10 | 标准差大部分不显示 | `loadRatingInsights()` 读到的 `ratingDistribution` **此时还是空的**（时序问题） | 确切根因 |
| 11 | Bilibili 评分消失 | 横滑社区卡宽度不足导致被裁切（待真机确认） | 待验证 |
| 12 | AniList 能匹配却要手动 | AniList 未作为「评分源」自动接入 | 设计缺口 |
| 13 | NSFW 不可读 | Bangumi 的 `nsfw=true` 检索**需要 access token**，而登录失败 | 确切根因 |
| 14 | 发现页「全部」标签难用 | 该 tab 是硬编码的、API 无此能力 | 设计问题 |

---

## 一、五个已定位的确切根因（含代码位置）

### R1 · 单集详情不可用 = 剧集从未落库（连带影响剧照与每集评分）

`SubjectDetailViewModel` 里读剧集是：
```kotlin
val episodesDeferred = async { remoteDataSource.getEpisodes(subjectId) }   // 只读远端
```
而单集二级页是：
```kotlin
val episode = episodeDao.getById(currentEpId)   // 只读本地 episodes 表
```
**`episodes` 表在本 App 里只有统计页的预取会写**（`EpisodeRepository` 仅被 `StatsViewModel` 使用）。所以：
- 没进过统计页 → `episodes` 表空 → 单集页永远「未找到该集」；
- 更糟的是我上轮加的 `episodeDao.updateStillUrl(epId, url)`（B 站/TMDb 剧照回填）**更新的是 0 行** → 剧照也从未落库。

**修复**：详情页改用 `episodeRepository.getEpisodes(subjectId)`（缓存优先 + 落库 + TTL），单集页再加一层「库里没有就按 subjectId 拉一次远端」的兜底。

### R2 · 标准差几乎不显示 = 读取时序错误

```kotlin
private suspend fun loadExternalRatingData() {
    ...
    loadRatingInsights()      // ← 这里读 _uiState.value.ratingDistribution
}
// loadExtendedData 里所有 async 都跑完之后，才一次性写入：
_uiState.update { it.copy(ratingDistribution = ratingDistribution, ...) }
```
`loadExternalRatingData` 与 `distDeferred` 是**并行**的，所以它读到的 `ratingDistribution` 恒为空 → `dispute = null` → 争议度不显示。
**修复**：把 `loadRatingInsights(distribution)` 改为**接收参数**而不是读 state，并在 `ratingDistribution` 落库后调用；或在 `loadExtendedData` 的最终 `update` 之后统一计算洞察。

### R3 · 收藏面板长按单集无反应 = 手势被 FilterChip 吃掉

```kotlin
FilterChip(
    onClick = onClick,
    modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
)
```
`FilterChip` 内部自带可点击 Surface，**它会消费手势**，挂在外层 Modifier 的 `combinedClickable` 收不到长按。
**修复**：选集 chip 不再用 `FilterChip`，改为自绘的 `Surface + combinedClickable`（保留原有 tonal 填充/边框视觉），或把长按入口改成 chip 上的独立小按钮。

### R4 · 登录网页过小 / 登不上 = WebView 视口与容器尺寸

| 位置 | 现状 | 问题 |
|---|---|---|
| `SteamLoginScreen.kt` 的 `SteamOpenIdWebView` | 只设了 `javaScriptEnabled` / `domStorageEnabled` | **没有** `useWideViewPort`、`loadWithOverviewMode`、`setInitialScale`、`setSupportZoom`、`setAcceptThirdPartyCookies` → `steamcommunity.com` 桌面页挤成一条、Cookie 也可能因第三方策略被拒 |
| `BilibiliSyncScreen.kt` 的 `LoginWebView` | 视口设置齐全 | 但容器写死 `Modifier.height(300.dp)` → 登录页只显示一小块 |
| Bangumi | **全仓没有任何 OAuth 登录 UI**（`AppSettings` 注释写着「手动粘贴或 OAuth 写入」，但没有 OAuth 入口） | 只能手贴 token；而 NSFW 检索**必须有 token** |

### R5 · NSFW 不可读 = Bangumi 的 nsfw 检索要 token

`SearchRequestDto.nsfw` 直接传给 `POST /v0/search/subjects`。Bangumi 规定：**`nsfw=true` 必须带 access token**，否则该次检索会被拒/被忽略；而 token 只能靠（缺失的）OAuth 或手贴获得。
于是你看到的是：开关打开 → 检索报错或回到空；开关关闭 → NSFW 条目根本不出现 →「完全无法阅读」。

---

## 二、TMDb：覆盖、入口、以及「差异化展示」（问题 2、3）

### 2.1 为什么部分作品没有 TMDb 匹配

`TmdbRatingSource.subjectTypes = setOf(ANIME, REAL, OTHER)` —— **GAME / BOOK / MUSIC 直接没有这个源**，所以 TMDb 区块不渲染。
另外 `loadTmdbSupplement()` 还会在这些情况整体不渲染：未配置 key、类型不在集合内、**未绑定且没有搜到候选**。

**改造**：
- 扩展覆盖：`ANIME / REAL / OTHER` 走 TMDb **tv**；`BOOK / MUSIC / GAME` 允许走 TMDb **movie**（TMDb 对动画电影、真人电影、音乐纪录片都有条目；GAME 默认关，由设置决定）。
- **永远提供手动入口**：即使候选为空也要显示「搜索 TMDb」，并支持**直接粘贴 TMDb ID / 链接**（`themoviedb.org/tv/42509-steinsgate` 可直接解析）。

### 2.2 差异化展示（你指出的真问题）

现状绑定后展示：名称 / 首播日 / 季数·集数 / 状态 / 整剧评分 / 电视台 / IMDb —— **除 IMDb id 外，Bangumi 全都有**。

**改造原则：绑定后不再重复 Bangumi 已有的字段，只展示 TMDb 独有且有用的东西**：

| 保留（TMDb 独有 / 对本项目有直接价值） | 移除（Bangumi 已有） |
|---|---|
| `networks`（播出电视台） | 名称 / 原名 |
| `production_companies`（制作公司，带 logo） | 首播日（Bangumi `airDate` 更全） |
| `status`（Returning/Ended）—— 判断「是否还在播」 | 集数（Bangumi `totalEpisodes`） |
| `episode_run_time`（单集时长） | 整剧评分（已有权威评分卡） |
| `spoken_languages` / `origin_country` | — |
| **每集评分与剧照的入口**（这是绑定 TMDb 的唯一理由） | — |
| `external_ids`（IMDb / TVDB / Wikidata / 社交账号，一键跳转） | — |
| 多语言海报（换封面用） | — |

并且**默认折叠**：绑定成功后只留一行「已绑定 TMDb #xxxx · N 部剧照 · 每集评分可用」+「详情」展开。这样既满足差异化，也不会继续占版面。

---

## 三、豆瓣剧照：为什么"完全不可用"（问题 4）

### 3.1 我的实现里有两处会必然导致失败（本机可确认）

**(a) 搜索候选这条链路写错了**：
```kotlin
// DoubanClient.search() 里：
val url = "https://www.douban.com/search?cat=1002&q=..."   // 先拼了 HTML 搜索页
val json = RatingHttp.getJson("https://m.douban.com/rexxar/api/v2/search?q=...&type=movie&count=10")
```
`url` 只用于后面的 HTML 兜底，但**rexxar 的 search 端点我用的是猜的路径**（实测 `/rexxar/api/v2/search` 并非稳定公开端点）。一旦它失败，就落到 HTML 搜索页 `www.douban.com/search`，而**这个页面同样会跳 `sec.douban.com`**。→ 候选恒为空 → 无法绑定 → 剧照恒为空。**这就是"完全不可用"的最直接原因。**

**(b) 我从未在真机验证过任何一条豆瓣链路**（方案里 §10 已声明），而 `web_fetch` 不能带自定义头，所以「带对头到底能不能过」我至今没有证据。

### 3.2 参考 Bangumi-master 得到的结论

它的做法（我已解密其常量并核对代码）：
- 域名：`movie.douban.com` / `www.douban.com`（不是 m 域，也不是 rexxar）；
- **剧照**：抓 `movie.douban.com/subject/{id}/photos?type=S&start=N&sortby=time&size=a&subtype=o`，取 `.cover img` 的 `src`；
- **图片 Referer**：`douban.com`（不是 `movie.douban.com/`）；
- **它不搜索**：豆瓣 id 的来源是**它自己服务端预跑的任务**（结果存进自家 KV，客户端只读缓存）；客户端只做「豆瓣 id 已知 → 取剧照」。
- 关键差异：**它的豆瓣 id 不是客户端实时搜出来的**。我把它当成「客户端实时搜索」来实现，这是设计上的误判。

### 3.3 修正方案

1. **先做「连通性自检」**（设置页一个按钮）：依次跑 rexxar / frodo / HTML 三条探针，**把 HTTP 状态码与响应前 200 字原样显示出来**。没有这个，我们只能盲猜——这是本轮唯一能真正解决豆瓣问题的工程手段。
2. **豆瓣 id 的来源改为三种，按可靠性排序**：
   1. **Bangumi infobox / 简介里的豆瓣链接**（明确、零猜测）；
   2. **豆瓣 movie 搜索页 + 高阈值匹配**（`similar ≥ 0.86` 且年份一致才自动绑定，否则只展示候选）；
   3. **手动粘贴豆瓣 ID 或链接**（终极兜底，与 TMDb 一致）。
3. **图片 Referer 改为 `douban.com`**（对齐 Bangumi-master），并做成设置项。
4. **HTML 解析器按真实页面重构**：现在的正则基于我的猜测；自检工具能拿到真实 HTML 后，用它做 fixture 固化单测。
5. 明确预期管理：**豆瓣通道默认关，且在设置页写明「机房/VPN 出口 IP 大概率被拦截，移动网络成功率更高」**。

---

## 四、账号登录（问题 5）

### 4.1 Steam（OpenID）
- **补视口与缩放**：`useWideViewPort = true`、`loadWithOverviewMode = true`、`setInitialScale(0)`（自适应）、`builtInZoomControls = true`（隐藏按钮）、`setSupportZoom(true)`。
- **补 Cookie 策略**：`CookieManager.setAcceptCookie(true)` + `setAcceptThirdPartyCookies(webView, true)`（Steam 的 SSO 依赖第三方 Cookie）。
- **补容器尺寸**：登录 WebView 至少占屏幕 60% 高度，并支持全屏展开（现在是一个小方框）。
- **补错误可见性**：`onReceivedError` 要显示出来（`steamcommunity.com` 在国区常不可达，现在失败后界面无任何提示）。
- **兜底保留**：手动输入 SteamID64（已有）应更显眼。

### 4.2 Bilibili
- 容器高度写死 `300.dp` → 改为**可展开的全屏/大半屏**（保留折叠按钮）。
- 视口设置已有，但补 `setInitialScale(0)` 与「强制移动 UA 的开关」（部分页面会跳桌面版）。
- `onReceivedError` / `onReceivedHttpError` 要可见。

### 4.3 Bangumi（当前完全没有）
**新增 OAuth 授权码流程**（Bangumi 支持 `https://bgm.tv/oauth/authorize`）：
- 新增设置项 `bangumiClientId` / `bangumiClientSecret` / `bangumiRedirectUri`（用户自建应用，默认不内置）。
- 新增 `BangumiOAuthWebView`（同样的视口设置）→ 拦截 redirect → 用 `https://bgm.tv/oauth/access_token` 换 token → 存 `bangumiAccessToken`。
- 换到 token 后立刻调 `/v0/me` 校验并显示用户名（现在 `BangumiAuthApi` 已存在但没有 UI 消费它）。
- **这一步是 NSFW 能否可用的前提**（见下）。

### 4.4 统一的登录体验
抽一个公共组件 `ui/common/OAuthWebView.kt`：视口/缩放/Cookie/错误回调/加载进度/关闭按钮全部统一，三个登录入口都用它。

---

## 五、OMDb 已配置但 IMDb 逐集不可用（问题 6）

当前入口显示条件（我上一轮加的）：
```kotlin
imdbAvailable = state.tmdbBinding != null && state.imdbEpisodeEntryEnabled
// imdbEpisodeEntryEnabled = externalRatingsEnabled && imdbEpisodeRatingsEnabled && omdbApiKey.isNotBlank()
```
所以只要**任一**不满足就完全不显示按钮，且**不告诉用户缺什么**。另外 `loadImdbEpisodeRatings()` 还要求绑定记录里有 `subKey`（季号），否则直接 `FAILED`。

**改造**：
- 在剧集区显示**明确的可见状态**：「IMDb 逐集：未开启（设置里打开）／缺 OMDb Key／需先绑定 TMDb／已加载 N 集」，点击可直达对应设置项。
- 绑定 TMDb 时**写入 `subKey`（季号）**（现在只有自动挑季、没写回，导致后续链路拿不到季号）。
- 增加**不依赖 TMDb 的兜底**：允许直接输入 IMDb 剧集 id（`tt1234567`）或整剧 id，用 OMDb 的 `Season=`/`Episode=` 参数取分（`omdbapi.com/?i=tt0944947&Season=1&Episode=3`）。
- 逐步加载 + 断点续拉（已有 `fetchedAt` 机制，但集数多时应显示进度）。

---

## 六、VNDB：差异化数据 + 匹配机制（问题 7、12）

### 6.1 差异化展示（只留 VNDB 独有的）

| 保留（Bangumi 没有或更好） | 移除/弱化 |
|---|---|
| **平均游玩时长**（`length_minutes`，VNDB 独有且很实用） | 评分（已有权威评分卡） |
| **VNDB 贝叶斯评分 + 票数 + `popularity`** | 开发商 / 发行日期（Bangumi 通常有） |
| **原语（original language）+ 支持语言列表** | 简介（Bangumi 有） |
| **平台（Windows/DVD/PSV…）+ 各平台发售日** | — |
| **标签（带权重/投票数）** —— VNDB 的标签体系是它最强的部分 | — |
| **截图（VNDB 的截图常比 Steam 全，尤其是日式 PC 游戏）** | — |
| **关联作品（VNDB 的 relations：前作/续作/同世界观）** —— 可显著改善「同世界观作品」的发现 | — |

补一句：你举的《魔法少女的魔女审判》/《主播少女的秘密账号迷宫》正是 **VNDB relations 里明确标注同世界观**的例子 —— 接入 relations 后，即使匹配少，也能通过已匹配作品反查到未匹配的作品。

### 6.2 匹配机制（我建议统一重做，不只 VNDB）

**现状缺陷**（`VndbGameDataSource.search`）：
```kotlin
filters = ["search", "=", query]   // 只用一个查询串（subject.displayTitle）
sort = "searchrank"
```
- 只用**一个标题**去搜（中文名优先），日文原名/罗马音/别名都没用上；
- VNDB 返回的 `titles`（多语言）没有参与打分；
- 阈值 0.7 + 只展示前几个候选，匹配不上的没有手动通道。

**重做方案：一个统一的 `ExternalMatchService`**（TMDb / VNDB / IGDB / RAWG / Discogs / MusicBrainz / 豆瓣 / Google Books 共用），分四层：

| 层 | 手段 | 说明 |
|---|---|---|
| L1 | **infobox 明确 ID** | Bangumi 的 infobox / 简介里常有「vndb」「TMDB」「IMDb」「豆瓣」链接 → 直接绑定，置信度 1.0，零猜测 |
| L2 | **多查询串** | 用 `title` / `titleCN` / 罗马音（AniList 提供）/ infobox 别名 / `series` 名，逐个查询后合并去重 |
| L3 | **多字段打分** | 候选侧要用**全部标题集合**（VNDB `titles[]`、TMDb `name/original_name`）、年份、集数/时长、平台；打分函数复用 `TmdbMatchScorer` 并扩展 |
| L4 | **手动兜底** | 每个源都提供：自定义关键词搜索 / 直接粘贴 ID 或链接 / 候选列表点选绑定（已有约定） |

**并且把「匹配」集中到一个可复用的 UI**：`ProviderBindingSection`（现在 TMDb/VNDB/AniList 各写一套、彼此不一致，且 TMDb 那套还没有手动入口）。

---

## 七、收藏面板长按无反应（问题 8）

根因见 R3。**修复方案**：
- 选集 chip 改为自绘 `Surface + Modifier.combinedClickable`（保留 tonal 填充、边框 1dp→1.5dp 的现有视觉）；
- 长按后弹出**单集快捷菜单**（打开单集详情 / 标记看到这里 / 标记未看），而不是直接跳页 —— 长按更适合弹菜单，且给用户发现感；
- 同时在剧集列表与曲线气泡保留直达入口（已有）。

---

## 八、评分区重做：撤销横滑卡，回到「一张评分卡 + 下方排版」（问题 9、10、11）

我上一轮把它做成 5 张横滑卡，你的反馈是**太细碎**。回退并重排：

```
┌─ 评分卡（沿用改造前的 RatingComparisonSection 版式）────────────┐
│  我的评分         Bangumi          Bilibili                  │
│   7.5             8.4 (12.3万)     9.1 (2.1千)               │
│  争议度 莫衷一是 · 本地库内 72% · Bangumi 排名 #143            │
└──────────────────────────────────────────────────────────────┘
   评分分布（10 档柱状图，整宽，保持现状）
   权威评分（多源，FlowRow 自然换行；末尾「＋录入」）
```

- **不再横滑**，恢复「一张卡 + 下方纵向内容」，与你的描述一致；
- 「我的评分」只展示 + 跳转（编辑仍在收藏面板，避免两处状态不同步）；
- 「权威评分」保留多源，但**默认只显示前 6 个源**，其余折叠（避免长页面）。

**同时修掉两个真 bug**：
- **标准差（争议度）不显示**：根因见 R2 → 改为在 `ratingDistribution` 就绪后计算洞察。
- **Bilibili 评分消失**：横滑卡宽度不足会把第二列裁掉；回退成整宽卡后自然消失。若仍不显示，则说明 `fetchAndPersistBilibiliRating()` 的 `_uiState.subject` 更新被后续 `loadSubject()` 覆盖（时序），需要加「bili 数据晚到则合并而非覆盖」的保护。**这一条我会在实现时用日志确认，不靠猜。**

---

## 九、AniList 评分自动接入 + 「灰色通道」框架（问题 12 的后半段）

你的两点诉求拆开：

### 9.1 AniList 能匹配到的，就不该要用户手动

AniList 的 `Media.averageScore`（0-100）就是现成的权威评分，而现在只有当用户在详情页**手动绑定** AniList 后才会展示，且不进评分卡。
**改造**：
- 把 AniList 提升为一个正常的 `RatingSource`（`anilist`）：`isAvailable` 恒为真（无需 key），候选搜索走 GraphQL `Page.media(search:)`；
- **自动匹配 + 自动展示评分**（不再需要用户确认即可"看到分数"，但**写绑定仍保守**）：即：搜索命中高置信度时**只展示评分**（不落 `anilist_bindings`），详情页另给「确认绑定」入口以获得封面/角色等深度数据。这样既满足"能匹配就该显示"，又不破坏阶段 G 的保守写库约定。

### 9.2 「不一定完全符合规范但可以使用」的其他权威源

我建议做一个**统一的灰色通道框架**（沿用豆瓣那套：默认关 + 可配置头 + 自检 + 静默降级），把下面这些一次性纳入。它们的共同点是「有可用接口但非官方/未文档化/需绕」：

| 源 | 可用路径（非官方） | 域名/路径 | 风险 |
|---|---|---|---|
| **豆瓣** 电影/剧集/书籍/音乐/游戏 | rexxar API / HTML 抓取 | `m.douban.com/rexxar/api/v2/...`、`movie.douban.com/subject/{id}/photos` | 高（反爬） |
| **AniList** | GraphQL 官方公开 | `graphql.anilist.co` | 低 |
| **Jikan（MAL）** | 官方公开（第三方实现） | `api.jikan.moe/v4` | 低（当前偶发 504） |
| **AniDB** | 未文档化 HTTP API | `anidb.net/api/anime-titles.xml.gz`（标题映射，免 key） | 中 |
| **Bangumi 旧版搜索** | 官方旧接口，免 token | `api.bgm.tv/search/subject/{kw}?type=2&responseGroup=large` | 低（**可用于 NSFW 兜底**） |
| **Metacritic** | 页面内嵌 JSON | `www.metacritic.com/{game|movie}/{slug}/` 的 `__NEXT_DATA__` | 中 |
| **OpenCritic** | 未文档化公开 API | `api.opencritic.com/api/game/search?criteria=` | 中 |
| **Fami通** | 无接口 | `famitsu.com` 搜索页 + Wikipedia/Fandom 榜单页 | 中 |
| **Oricon / Billboard** | 无接口 | 榜单页 HTML | 中 |
| **ErogameScape（批评空间）** | 无接口 | 统计页 HTML（中央値） | 中 |
| **RateYourMusic / AOTY** | 无接口 | 页面 HTML（有 Cloudflare） | 高 |
| **Steam 好评率** | 官方公开 | 已接入 | 低 |
| **IMDb** | OMDb | 已接入 | 低 |

**框架要素**（每个源只要实现一个 `Probe` 接口）：
- `probe()`：返回 可用/被拦/不可达 + 原始状态码与响应摘要（就是上面豆瓣要的那个自检）；
- `headers()`：可配置（Referer / UA / Cookie / apikey），**放在设置页**，坏了用户自己修，不必发版；
- 统一开关 + 统一「数据来源非官方」标注 + 统一静默降级；
- **每个源都要有 fixture 单测**（把自检拿到的真实响应存成测试资源，解析逻辑才不会随页面改版静默失效）。

> 我的建议：**优先做前 5 行**（豆瓣、AniList、Jikan、AniDB 标题映射、Bangumi 旧版搜索）。后面那些 HTML 抓取（Metacritic/Fami通/Oricon/批评空间/RYM）投入产出比低、维护成本高，建议先只做「外链 + 手动录入」，等你确认真的需要再逐个接。

---

## 十、NSFW 不可读（问题 13）

**根因链**：NSFW 检索需要 Bangumi token → token 只能手贴（没有 OAuth UI）→ 登录失败 → `nsfw=true` 请求被拒 → 打开开关也没用；关掉开关则 NSFW 条目不出现。

**三级方案**：
1. **补 OAuth 登录**（§4.3）—— 治本；
2. **免 token 兜底**：NSFW 检索失败时自动回退到 **Bangumi 旧版搜索** `api.bgm.tv/search/subject/{kw}?responseGroup=large`（免 token、含 NSFW），按 id 走 `/v0/subjects/{id}` 取详情；
3. **状态可见**：设置页与发现页明确显示「NSFW 检索：已登录（可用）/ 未登录（走旧版接口兜底）」；若两条都失败，给出可操作提示而不是静默空列表。

另外要检查：**NSFW 条目的详情页是否也被拦**（按 id 取 `/v0/subjects/{id}` 通常不需要 token，若被拦则同样走旧接口）。

---

## 十一、发现页重做（问题 14）

### 11.1 问题
`ui/screens/Screens.kt:236` 里硬编码了一个「全部」tab，而 Bangumi API 的类型过滤是 `type=1..6`，**没有"全部"这个能力**（传空会退化成无类型过滤的宽搜，结果质量差、翻页无意义）—— 你自己也说不好用。

### 11.2 参考 Bangumi-master 的发现页结构
它的发现页是一组**并列入口**（不是 tab 拼盘）：排行榜 / **找条目** / 每日放送 / 索引 / 目录 / 新番 / 标签 / 年鉴 / 系列 / 每日推荐 / 评分月刊 / 词云 / 大赏。
其中「找条目」的资料页**不是实时 API**（是它自己季度更新的静态集），所以我们要做的是**用实时 API 复刻它的筛选能力**，而不是复刻它的数据源。

### 11.3 建议的新发现页

```
发现页
├─ 当季热门（保留现有 TrendingSection 的 SEASONAL）
├─ 排行榜（排行榜入口，先选类型 → 榜单列表，保留现有 RANKING）
├─ 找条目 ★新增（核心）：
│    顶部：类型 tab（动画 / 书籍 / 游戏 / 音乐 / 三次元）  ← 无「全部」
│    筛选面板（复用/扩展 SearchFilterPanel）：
│      · 放送年份区间（air_date 范围）
│      · 季度（1/4/7/10 月）
│      · 标签（多选，取 Bangumi 热门标签）
│      · 评分区间（rating）
│      · 排名区间（rank）
│      · 是否系列（series）
│      · NSFW（受 §10 的 token 状态影响）
│    排序：匹配度 / 热度 / 排名 / 评分 / 发售日
│    分页：触底加载
├─ 标签（热门标签云 → 点进去按标签浏览，已有 TagWordCloud 可复用）
├─ 系列（按 series 聚合，新增）
├─ 评分月刊（本地实现：按月记录已收藏作品的 Bangumi 评分/排名变化 → 对齐 Bangumi-master 的 vib）
└─ Steam 热门（保留现有本地条目标签）
```

关键点：
- **去掉「全部」**，类型必须显式选择；
- 现有 `data/filter/FilterDimension` + `PresetFilters` 已经是一套可扩展的筛选维度机制，**扩展它而不是新造**；
- `/v0/search/subjects` 的 `filter` 支持 `type/tag/air_date/rating/rank/nsfw/series`，全部可做（`rating`/`rank` 区间我们已经在用）；
- 「评分月刊」需要**本地历史快照**（新增一张 `rating_snapshots(subjectId, month, score, rank, total)` 表，每月首次打开时记一条）—— 这是 Bangumi-master vib 的本地版，成本低、价值高。

---

## 十二、阶段计划

| 阶段 | 内容 | 工作量 | 风险 |
|---|---|---|---|
| **A** | 五个确切根因：R1 剧集落库 / R2 洞察时序 / R3 长按 / R4 登录 WebView（含 Bangumi OAuth）/ R5 NSFW 兜底 | 2 天 | 中 |
| **B** | 评分区回退重做（§8）+ 修 Bilibili 消失 | 0.5 天 | 低 |
| **C** | TMDb：覆盖扩展 + 手动 ID + 差异化折叠（§2） | 1 天 | 低 |
| **D** | 统一匹配服务 + `ProviderBindingSection`（§6.2）：多查询串 + 多字段打分 + infobox ID + 手动兜底 | 1.5 天 | 中 |
| **E** | VNDB 差异化数据（时长/原语/平台/标签权重/截图/relations）（§6.1） | 1 天 | 低 |
| **F** | AniList 自动评分 + 灰色通道框架（先做豆瓣/AniList/Jikan/AniDB/旧版搜索）+ 自检工具（§9） | 2 天 | 高 |
| **G** | 发现页重做（§11，含评分月刊表） | 1.5 天 | 中 |
| **H** | OMDb/IMDb 逐集可用性（§5） | 0.5 天 | 低 |

**合计约 10 个工作日。** 建议顺序：A → B → C → H → D → E → G → F（F 最难且最依赖真机验证，放最后）。

---

## 十三、待你确认的决策点

| # | 决策 | 选项 | 我的建议 |
|---|---|---|---|
| D1 | 豆瓣 id 的来源 | 客户端实时搜索 / infobox + 高阈值搜索 + 手动粘贴 | **后者**（Bangumi-master 的 id 其实是服务端预跑的，客户端实时搜不可靠） |
| D2 | 豆瓣 Referer 默认值 | `movie.douban.com/` / **`douban.com`** | **`douban.com`**（对齐 Bangumi-master 的实测值） |
| D3 | TMDb 对 GAME/BOOK/MUSIC 是否开放 | 开放（走 movie）/ 保持只覆盖影视动画 | **开放但默认关**（TMDb 对这三类覆盖差，避免制造噪声） |
| D4 | AniList 自动展示评分的写库策略 | 自动写绑定 / **只展示不写库，另给确认绑定** | **只展示不写库**（保持阶段 G 的保守约定） |
| D5 | 灰色通道范围 | 只做豆瓣 / **先做 5 个**（豆瓣、AniList、Jikan、AniDB 标题映射、Bangumi 旧版搜索）/ 全做 | **先做 5 个**；HTML 抓取类（Metacritic/Fami通/Oricon/批评空间/RYM）先只做外链+手动录入 |
| D6 | 评分月刊是否做 | 做（需新增快照表）/ 不做 | **做**（本地实现，成本低、是发现页最有内容的差异化功能） |
| D7 | 发现页是否保留 Steam 标签 | 保留 / 移除 | **保留**（是本地差异化能力） |
| D8 | Bangumi OAuth 的 client id/secret | 内置一个公共应用 / **由用户自建并填写** | **用户自建**（内置会违反 Bangumi 应用条款，且容易被封） |

---

## 十四、验收清单

- [ ] `assembleDebug` + `testDebugUnitTest` 通过；新增 `DoubanHtmlParser` fixture 单测（用自检拿到的真实 HTML）
- [ ] **单集**：进入任一有剧集的动画 → 收藏面板长按第 3 集 → 弹出快捷菜单 → 打开单集详情 → 显示名称/剧照/简介/评分/评价；返回后重进仍在
- [ ] 单集剧照：绑定 TMDb 或匹配到 B 站后，`episodes.stillUrl` 在数据库里**真的有值**（可用导出 JSON 验证）
- [ ] **登录**：Steam 网页正常大小可点；Bilibili 网页占大半屏；Bangumi 能完成 OAuth 并显示用户名
- [ ] **NSFW**：未登录时走旧版接口仍能检索到 NSFW 条目；登录后开关即时生效
- [ ] **评分区**：一张卡（我的/Bangumi/**Bilibili**）+ 争议度 + 百分位 + 排名；下方评分分布与权威评分
- [ ] **争议度**：打开任一有评分分布的条目，争议度**必定显示**
- [ ] **TMDb**：未绑定时有「搜索 / 粘贴 ID」入口；绑定后不再重复 Bangumi 字段
- [ ] **长按**：收藏面板选集 chip 长按有反应
- [ ] **IMDb**：配了 OMDb 但缺其它条件时，剧集区显示**具体缺什么**并可一键跳设置
- [ ] **VNDB**：已匹配条目显示平均游玩时长/原语/平台/标签权重/relations；《魔法少女的魔女审判》能匹配到
- [ ] **发现页**：无「全部」tab；找条目支持年份/季度/标签/评分/排名/系列 筛选 + 5 种排序 + 分页
- [ ] **AniList**：能匹配的动画**无需手动绑定**即可在评分区看到 AniList 分数

---

## 十五、我需要的真机信息（否则只能盲猜）

1. **豆瓣自检的输出**（实现后你在手机上点一下，把状态码/响应摘要发我）—— 这是唯一能让豆瓣通道真正可用的办法。
2. Steam/Bilibili/Bangumi 登录失败时**页面显示的具体报错**（我会加 `onReceivedError` 可见化）。
3. Bilibili 评分到底是「没数据」还是「被裁切」——实现后看日志即可确认。
