# 权威评分数据源接入 + 详情页补齐 · 详细方案（Niriko）

> **实施状态**：阶段 0–7 **全部实施完毕**，`assembleDebug` + `testDebugUnitTest` 通过（**229 个单测全绿**）。
> 实现要点见 `README.md` 的「权威评分数据源 · 每集评分与走势曲线」「人物 / 角色详情补齐」「阶段 7 收尾」三节。
>
> **已知未做（明确记录，非遗漏）**：
> - 角色的**声优（CV）列表**：Bangumi 无「按角色查声优」端点，声优关系只存在于作品的角色列表里 → 角色详情页不展示 CV。
> - **WebDAV 同步**仍未覆盖新表（JSON 备份已覆盖）；`SyncManager` 目前只同步 collections / workItems / history。
> - **TMDb 季号手动切换**：多季作品自动挑季，暂无手动下拉。
> - **iTunes / Apple 榜单**：iTunes Search API 无评分字段、Apple 榜单 feed 未验证 → 未接入。
> - 封面候选来源目前只有 TMDb + 当前源 + 本地相册（未做 AniList / VNDB / Steam / Anitabi 分组）。

> 本文件保留为方案原文（含取舍与数据源调研结论）；实施细节以 README 与代码为准。
> 本文件保留为方案原文（含取舍与数据源调研结论）；实施细节以 README 与代码为准。
> 参考实现：AniShelf（iOS/SwiftUI，TMDb 深度集成）、Bangumi-master（React Native，Bangumi 全功能客户端）、seriesgraph（分集评分走势站点）。
> 目标：为**每种作品类型**接入对应的权威机构/评分数据，落地**番剧每集评分 + 整体评分走势曲线**，并把作品/人物详情页缺失的内容（剧照截图等）补齐。
>
> 配套调研报告（各源的 API / 限流 / 覆盖度实测）：`docs/rating-data-sources-research.md`。

---

## 〇、三句话总结

1. **每集评分是全新功能**（AniShelf 自己都没做，它的 `AnimeEntryEpisodeSummaryDTO` 明确丢掉了 TMDb 的 `vote_average`/`vote_count`）。**关键考证：seriesgraph 的逐集数据来自 IMDb，不是 TMDb**（Steins;Gate 的 E22 9.7 / E24 9.6 与 IMDb 剧集页一致，而 TMDb 同季是 6.9 / 6.8；图片与元数据才来自 TMDb）。所以两条源都要：**TMDb 季详情接口**（免费 key、一次请求带全季每集评分，作默认）+ **IMDb via OMDb**（复刻 seriesgraph 观感的可选层，1000 次/天）。曲线用 **Compose Canvas 手绘**（项目现在没有任何图表库，已有 3 处 Canvas 图表，沿用同一风格）。
2. **权威评分**做成一个**注册表 + 统一表结构**（`subject_external_ratings` / `subject_external_ids`），按类型派发：动画/三次元 → TMDb + IMDb；游戏 → Steam 好评率 + Metacritic + IGDB + OpenCritic（Fami通 无 API，只能外链 + 手动录入）；音乐 → MusicBrainz + Discogs（Billboard / Oricon 无免费 API，外链 + 手动榜单）；书籍 → Google Books + Open Library；VN → VNDB（已有）。**所有源都可缺省降级，不阻断详情页。**
3. **详情页缺的东西**（对照 Bangumi-master 非社区部分）：**剧集/章节列表区块**、**剧照/截图区块**、**全屏图片查看器**（项目当前 0 处）、infobox 外链不可点、评分分歧度/同类百分位、人物页的**基本信息/职位统计/合作者/收藏数**、角色页的基本信息。封面更换已有雏形，按 AniShelf 升级为**多源候选海报浏览器**。

---

## 一、现状盘点（代码级证据）

### 1.1 已经有的（不要重复造）

| 能力 | 位置 | 说明 |
|---|---|---|
| 更换封面（雏形） | `ui/subject/CoverPickerDialog.kt` + `data/settings/CoverOverrideStore.kt` + `CoverImage(subjectId=)` | 目前候选只有「当前源封面」+「相册选图」；覆盖值存 DataStore（独立文件 `cover_overrides`），**不在备份/WebDAV 里** |
| 评分对比 | `ui/subject/RatingComparisonSection.kt` | 只有 Bangumi / Bilibili / 我的评分 三个数 |
| 评分分布柱状图 | 同上文件 `RatingDistributionChart` | Compose Canvas 手绘 |
| 剧集数据 | `EpisodeEntity` / `EpisodeDao` / `EpisodeRepository` | 已有 Bangumi `/v0/episodes` 落库 |
| Steam 截图/成就/在线/活跃排名 | `SteamInfoSection`（`SubjectDetailScreen.kt:1781`） | 仅游戏 |
| 圣地巡礼截图 | `AnitabiSection` | 仅动画 |
| 外链门户 | `data/model/PortalTarget.kt` | Bilibili / Bangumi / Steam / VNDB / AniList / 网易云 / QQ音乐 / Mihon |
| 刷新治理 | `data/refresh/FreshnessPolicy.kt` | 每个资源 soft/hard TTL + 退避 + 诊断页 |
| 保守匹配约定 | 阶段 G（README） | VNDB/AniList 只产候选，**手动确认才写绑定** |
| 标题置信度匹配 | `data/remote/steam/SteamTitleMatcher.kt`（11.6KB） | 已被 VNDB/AniList 复用 |

### 1.2 确认缺失（本次要补）

| 缺口 | 证据 | 严重度 |
|---|---|---|
| **完全没有 TMDb / IMDb** | 全仓 grep `TMDB|tmdb|themoviedb|imdb|omdb` = **0 命中** | 高 |
| **没有每集评分** | `EpisodeEntity` 无任何评分列 | 高（本次核心） |
| **没有评分走势曲线** | 无图表库；Canvas 只用于柱状图/环形图/日历热力图 | 高 |
| **剧集列表区块不存在** | `SubjectDetailScreen.kt` 的 15 个区块里没有「剧集」；`episodes` 只用于统计页与进度选择器 | 高（= Bangumi-master 的 `Ep` 区块） |
| **没有通用剧照/截图区块** | 剧照只在 Steam 区块内部（游戏）与 Anitabi（动画取景地） | 高（= Bangumi-master 的 `Thumbs` 区块） |
| **没有全屏图片查看器** | grep 全仓 `Zoomable / transformable / detectTransformGestures` = **0 命中** | 高 |
| infobox 是纯文本，外链不可点 | `InfoBoxSection`（`SubjectDetailScreen.kt:2775`）用 `Text(entry.value)` | 中 |
| `EpisodeRepository` **丢弃 `desc` 与 `disc`** | `toEpisodeInfo()` 里硬编码 `desc = null` / `disc = 0`，导致音乐碟片分组实际失效 | 中（顺带修 bug） |
| 人物页丢字段 | `PersonDetailInfo` 不含 `gender` / `birthYear/Month/Day` / `blood_type` / `stat`（DTO 里有，映射时丢了） | 中 |
| 角色页极简 | `CharacterDetailScreen.kt` 只有「简介 + 出演作品」 | 中 |
| 封面候选只有 1 个 | 见上 | 中 |
| 封面覆盖 / 绑定表**不进备份** | `BackupManager` 只写 collections/workItems/settings；`SyncManager` 只同步 collections/workItems/history | 中（换设备后封面与绑定全丢） |
| 评分无分歧度 / 百分位 | Bangumi-master 有「异口同声 … 厨黑大战」+ 同类百分位 | 低 |

---

## 二、参考项目结论

### 2.1 AniShelf（TMDb 用法 / 换封面）

**它的 TMDb 用法（照抄即可）**

- **Key 由用户自备**：iOS Keychain 存 `TMDbAPIKey`；UI 录入后用 `GET /3/configuration` 校验；**绝不硬编码**。启用 query 参数 `api_key`（v3 key，不是 Bearer）。
- **语言只有 3 档**：`en` / `zh-CN` / `ja`；图片语言白名单 ja/en/zh。
- **只存 TMDb `file_path`，不存完整 URL**（如 `/dhzbCznEzU67RXWYb53fyPe9Keb.jpg`），显示时按尺寸档拼 `https://image.tmdb.org/t/p/{size}{path}`，并能从旧 URL 反解。→ **强烈建议照搬**。
- **图片配置缓存**：`/3/configuration` 结果内存缓存 + 单飞；另有编译期硬编码 fallback，避免冷启动为渲染 URL 发请求。
- **429 处理**：最多重试 2 次，指数退避 500ms×2^n，**优先尊重 `Retry-After`**。
- **宽松解码兜底**：`/translations` 的 title/overview 在部分语言下会返回 `null`（注释点名 tv/35610 的 zh-TW），DTO 必须声明为 nullable。
- **代理开关**：`RedirectingHTTPClient` 把 host 从 `api.themoviedb.org` 重写到自建镜像（默认关）。**对我们的意义极大**：`api.themoviedb.org` / `image.tmdb.org` 在国区不可直连。

**它的换封面（`PosterSelection/` 4 个文件）**

- 候选来源：**只有 TMDb `/images`**（`posters`）；支持「本季 / 整季系列」切换（分段控件）。
- **排序规则（纯函数，可直接翻译成 Kotlin）**：原语言 → 元数据语言 → 无语言，同级按宽度降序；无语言判定集合是 空串 / null / xx / und / zxx。
- 交互：网格预览 → 全屏分页滑动看原图（显示原始分辨率）→ 确认弹窗。
- 持久化：`usingCustomPoster` + `customPosterPath`；**元数据刷新不会覆盖自定义封面**（`preservingCustomPoster`）；同步时新老字段双写。

### 2.2 Bangumi-master（详情页区块对照，剔除社区）

`src/screens/home/subject/component/header-component/ds.ts` 定义了完整区块顺序：

```
TopEls    = Lock, Box, Ep, SMB, Tags, Summary, Thumbs, Info
BottomEls = Game, Rating, Character, Staff, Anitabi, Comic, Relations, Catalog, Like, Blog, Topic, Recent, Comment, TrackComment
```

剔除社区项（Catalog / Like / Blog / Topic / Recent / Comment / TrackComment）与超纲项（SMB 本地媒体，项目已暂缓）后，**Niriko 缺的**：

| Bangumi-master 区块 | 内容 | Niriko |
|---|---|---|
| **`Ep`** | 剧集列表（动画）/ 章节（书籍，`book-ep`）/ 碟片曲目（音乐，`disc`）/ 自定义放送时刻（`onair-custom`） | 只有音乐曲目（且 disc 被丢） |
| **`Thumbs`** | 剧照横滑（源：豆瓣 `photos`，带 Referer 防盗链 + 反序防剧透）+ `preview` / `video` 子组件 | 无（只有 Steam / Anitabi） |
| **`Game`** | 游戏专属：`game/details`、`game/thumbs`、`game/externalThumbs`（外部站截图） | 部分（Steam 截图在 Steam 区块里） |
| **`Rating`** | 评分 + 排名 + 评分分布 + `vib` / `vib-trend` 评分月刊趋势 | 缺排名 / 趋势 / 分歧度 |
| **`Lock`** | 受限（NSFW）提示 | 缺 |
| **`Comic`** | 书籍 / 漫画章节 | 缺 |
| `Box` | 收藏盒（状态 / 评分 / 进度 / 短评） | 已有（`DetailDock` + `CollectionEditSheet`） |

**可直接搬的两个纯函数（`rating/chart/utils.ts`，小而美）**

- **分歧度**：由评分分布算标准差 → 小于 1.0「异口同声」、1.15「基本一致」、1.3「略有分歧」、1.45「莫衷一是」、1.6「各执一词」、1.75「你死我活」、≥1.75「厨黑大战」。
- **同类百分位**：用一份「按类型的评分分布」静态表算本作落在第几百分位（他们有 `type_score_distribution.json`）。→ 我们可用**本地库内同类型分布**（诚实标注「本地库内百分位」），避免引入需要人工维护的静态资产。

**人物页（`src/screens/home/mono/component/`）区块**：`Cover` / `Content`（简介）/ `Detail`（基本信息）/ `Voice`（配音角色）/ `Works`（参与作品）/ **`Jobs`（职位统计）** / **`Collabs`（合作者，带合作次数）** / **`Collected`（收藏数）** / `SectionTitle`（更多资料外链）。

→ Niriko 人物页已有：头像 / 名称 / 职业标签 / 简介 / 参与作品（按类型分组横滑）/ 配音角色。**缺**：基本信息、职位统计、合作者、收藏数、更多资料外链。

**剧照来源的现实评估**：Bangumi-master 的剧照走**豆瓣 HTML 抓取**（`getPreview` + Referer 防盗链 + 自家 KV 缓存），脆弱且有 ToS 风险。**我们不照搬**，改用 **TMDb 官方 `/images`（backdrops）+ 每集 `still_path`** —— 同一份 API、干净、且顺带把「每集剧照」也拿到。

### 2.3 seriesgraph（实测考证，修正了一个常见误解）

- **站点**：seriesgraph.com（Web + Android + iOS）。一剧一页，URL 形如 `/show/42509-steinsgate` —— **42509 就是 TMDb ID**。
- **呈现方式**：整剧分（如 8.8，9.6 万票）+ **每季逐集网格/热力图**（Season 1 avg 8.5，E1 7.5 E2 7.5 … E24 9.6）+ Top / Bottom Episodes 排行 + 社区自评分层。
- **数据来源 = IMDb 逐集评分，不是 TMDb**：
  - Steins;Gate 的 E22 9.7 / E24 9.6 / E23 9.5 / E16 9.4 与 IMDb 剧集页**逐项一致**；
  - 同一季在 TMDb 上第 1、2 集是 69% / 68%（≈6.9 / 6.8），与 seriesgraph 的 7.5 / 7.5 **不符**；
  - 图片与剧集元数据来自 `image.tmdb.org`（TMDb）。
- **没有官方开源仓库**（GitHub 上同名的 `inkorange/SeriesGraph` 是无关的 SVG 图表库）。
- **对 Niriko 的意义**：曲线可以只靠 **TMDb 一个接口**自绘（免费、一次调用拿全季、有中文）；若要复刻 seriesgraph 的「IMDb 数字」，再叠加 **OMDb** 逐集层。**两个源数值不同、趋势一致，UI 必须标清来源与标尺。**

---

## 三、总体架构设计

### 3.1 新增目录

```
app/src/main/java/com/otakup/niriko/
├── data/
│   ├── local/
│   │   ├── entity/SubjectExternalIdEntity.kt      # 通用外部身份（tmdb_tv/imdb/igdb/...）
│   │   ├── entity/SubjectExternalRatingEntity.kt  # 通用权威评分
│   │   ├── entity/EpisodeRatingEntity.kt          # 每集评分（按来源）
│   │   ├── entity/EpisodeMyRatingEntity.kt        # 可选：我的每集评分
│   │   ├── entity/ManualAwardEntity.kt            # 可选：Fami通/Billboard/Oricon 手动录入
│   │   └── dao/ExternalRatingDao.kt / ExternalIdDao.kt / EpisodeRatingDao.kt
│   ├── remote/
│   │   ├── tmdb/ TmdbClient.kt / TmdbApiService.kt / TmdbImageUrl.kt / TmdbLanguage.kt / dto/
│   │   ├── imdb/ OmdbClient.kt / dto/
│   │   └── rating/ RatingSource.kt / RatingSourceRegistry.kt / ExternalRating.kt
│   │              sources/ TmdbRatingSource / ImdbRatingSource / SteamReviewSource /
│   │                       OpenCriticSource / IgdbSource / RawgSource /
│   │                       MusicBrainzSource / DiscogsSource /
│   │                       GoogleBooksSource / OpenLibrarySource / VndbRatingSource
│   ├── repository/
│   │   ├── ExternalRatingRepository.kt   # 汇总所有源 → 详情页
│   │   ├── EpisodeRatingRepository.kt    # 每集评分抓取/落库/读取
│   │   └── ExternalIdRepository.kt       # 绑定/解绑/候选
│   └── calculator/
│       ├── EpisodeRatingAnalyzer.kt      # 纯函数：均值/极值/回归斜率/波动/趋势/移动平均
│       └── RatingInsights.kt             # 纯函数：分歧度（标准差）/ 本地库内百分位 / 归一化
├── ui/
│   ├── subject/
│   │   ├── EpisodeListSection.kt         # 剧集/章节/曲目列表（评分角标 + 剧照缩略 + 看到这里）
│   │   ├── EpisodeRatingChart.kt         # 评分走势曲线（Canvas）
│   │   ├── EpisodeDetailSheet.kt         # 单集详情（描述/评分/剧照/外链）
│   │   ├── ThumbsSection.kt              # 剧照/截图横滑
│   │   ├── ExternalRatingSection.kt      # 权威评分对照（升级 RatingComparisonSection）
│   │   ├── TmdbInfoSection.kt            # TMDb 补充信息（与 Steam/VNDB/AniList 区块同构）
│   │   ├── TmdbCandidateSection.kt       # TMDb 绑定候选 / 手动搜索
│   │   └── CoverPickerDialog.kt          # 升级：多源候选 + 全屏预览
│   ├── common/ImageViewer.kt             # 全屏图片查看器（缩放/翻页/保存）
│   └── settings/pages/DataSourceSettingsScreen.kt  # 增加「权威数据源与密钥」分区
```

### 3.2 外部身份：`subject_external_ids`

问题：Bangumi 词条与 TMDb tv id 的映射**没有权威通道**（AniShelf 自己也没有，它是 TMDb 原生 app）。

策略（**沿用项目已确立的保守匹配 + 用户确认约定**，参考阶段 G）：

1. **ID 通道优先**：解析 Bangumi 词条的 infobox / summary 中的 `TMDB` / `IMDb` 链接（部分词条确实有这两个键），命中即绑定，`bindMethod = infobox`，置信度 1.0。
2. **标题匹配只产候选**（不自动写库）：
   - 查询：`GET /3/search/tv?query={中文名}` + `{原名}` + `{罗马音}`，`language=zh-CN`；
   - 打分：复用 `SteamTitleMatcher`（标题相似度）+ **年份邻近**（Bangumi `airDate` 年份 vs `first_air_date` 年份，±1 年加权）+ **集数接近**（`totalEpisodes` vs `number_of_episodes`）+ **开播月接近**；
   - 展示为候选卡（与 `VndbCandidateSection` / `AniListCandidateSection` 完全同构），用户点「绑定」才写库。
3. **手动搜索**：候选区提供「搜索更多 TMDb」，用户直接搜标题绑定。
4. **解绑 / 重试**：与现有 Steam / VNDB / AniList 区块一致。

```kotlin
@Entity(tableName = "subject_external_ids",
    primaryKeys = ["subjectId", "provider"],
    indices = [Index(value = ["provider", "externalId"])])
data class SubjectExternalIdEntity(
    val subjectId: Long,
    /** tmdb_tv / tmdb_movie / imdb / igdb / opencritic / rawg / musicbrainz_release_group /
     *  discogs_release / googlebooks / openlibrary / famitsu / billboard / oricon */
    val provider: String,
    val externalId: String,
    val titleSnapshot: String? = null,
    val confidence: Float = 0f,
    /** infobox / title_match / manual / id_chain */
    val bindMethod: String = "manual",
    /** provider 侧子键（TMDb 的 season number、Discogs 的 release id 等）。 */
    val subKey: String? = null,
    val boundAt: Long = 0L,
)
```

### 3.3 权威评分源抽象

```kotlin
/** 统一评分（内部一律换算到 10 分制，展示时按 scoreMax 还原原生分）。 */
data class ExternalRating(
    val sourceId: String,        // tmdb / imdb / metacritic / steam_review / opencritic / igdb ...
    val label: String,           // TMDB / IMDb / Metacritic / Steam 好评率 ...
    val score: Float?,           // 换算到 10 分制；null = 只有人数或只有名次
    val nativeScore: Float?,     // 原始分（Metacritic 87、IMDb 8.6、Steam 92%）
    val scoreMax: Float,         // 10 / 100 / 5 / 100（百分比）
    val voteCount: Int?,
    val sourceUrl: String?,
    val extra: Map<String, String> = emptyMap(),   // Steam 的「好评如潮」、OpenCritic 的推荐率等
    val fetchedAt: Long,
)

interface RatingSource {
    val id: String
    val label: String
    /** 适用作品类型。 */
    val subjectTypes: Set<SubjectType>
    /** 是否需要用户提供密钥。 */
    val requiresKey: Boolean get() = false
    /** 依赖哪些外部身份（provider 名）。 */
    val requiresExternalId: Set<String> get() = emptySet()
    /** key 就绪判定。 */
    fun isAvailable(keys: RatingSourceKeys): Boolean
    /** 抓取（不落库）。返回 null = 该条目在此源无数据。 */
    suspend fun fetch(subject: SubjectEntity, ids: Map<String, String>): ExternalRating?
}

object RatingSourceRegistry {
    val all: List<RatingSource> = listOf(...)
    fun forType(type: SubjectType): List<RatingSource> = all.filter { type in it.subjectTypes }
}
```

设计要点：

- **与 `GameDataSourceRegistry` 同构**（README 已声明「注册序即优先级」），复用同一套心智模型。
  - 现状旁证：`NirikoApplication.kt:412` 已有注释「后续接入：register(RawgGameDataSource(...))、register(NeoDbGameDataSource(...))」——说明**游戏源的扩展位已经预留**，RAWG 可以直接作为 `GameDataSource` 接（顺带补齐搜索），而不是走本方案的 `RatingSource`。两条路都可行，实施时二选一：
    - 走 `GameDataSource`：能同时补搜索/封面/元数据，但要实现 `GameItemMapper`；
    - 走 `RatingSource`：只补评分，改动最小。
  - **建议**：Steam 好评率 + MusicBrainz + Google Books 等「只出评分」的源走 `RatingSource`；IGDB/RAWG 这类「整条游戏元数据」的源走 `GameDataSource`。
- **不污染 `subjects` 表**（延续 Steam / VNDB / AniList 的独立扩展表惯例）。
- **单个源失败绝不影响其它源**：`ExternalRatingRepository` 内部 `coroutineScope { sources.map { async { runCatching { it.fetch(...) } } } }`，与 `loadExtendedData` 现有写法一致。
- **未配置密钥的源直接跳过**（不出现在 UI），设置页可查「可用 / 缺失」。
- **每次抓取写 `fetchedAt`**，TTL 走 `RefreshResource.EXTERNAL_RATINGS`（建议 soft 1 天 / hard 7 天）。

### 3.4 每集评分（核心）

**数据流**

```
Bangumi subjectId
  └─ subject_external_ids(tmdb_tv) ─→ TMDb tv id（用户确认过的绑定）
        ├─ GET /3/tv/{id}?append_to_response=external_ids&language=zh-CN
        │     → 整体评分、季列表、imdb_id（整剧）
        ├─ 选季：单季动画默认 season 1；多季条目按 集数 / 开播日 与 Bangumi 词条对齐
        ├─ GET /3/tv/{id}/season/{n}?language=zh-CN
        │     → episodes[]: episode_number, name, overview, air_date, runtime,
        │                   still_path, vote_average, vote_count   （一次拿全季每集评分）
        ├─ 映射 episode_number → Bangumi episodes.sort（按序号对齐，index 兜底）
        └─ 写入 episode_ratings(sourceId=tmdb) + episodes.stillUrl

可选（用户显式点「加载 IMDb 评分」才跑，避免 N×2 次请求）：
  └─ 每集 GET /3/tv/{id}/season/{n}/episode/{e}/external_ids → imdb_id
        └─ OMDb GET https://www.omdbapi.com/?i={imdbId}&apikey={OMDB_KEY}
              → imdbRating, imdbVotes → episode_ratings(sourceId=imdb)
```

**表结构**

```kotlin
@Entity(tableName = "episode_ratings",
    primaryKeys = ["epId", "sourceId"],
    indices = [Index(value = ["subjectId"])])
data class EpisodeRatingEntity(
    val epId: Long,            // Bangumi episode id（与 episodes.epId 对齐）
    val subjectId: Long,
    val sourceId: String,      // tmdb / imdb /（未来）douban / mal
    val score: Float?,         // 10 分制
    val scoreMax: Float = 10f,
    val voteCount: Int? = null,
    val fetchedAt: Long = 0L,
)
```

**为什么用独立表而不是给 `episodes` 加两列**：与 `subject_external_ratings` 同构、天然支持第 3/第 4 个源、抓取失败只丢一个源。同时 `episodes` 表仍需新增 `stillUrl`（剧照）与修 `description` / `disc` 丢失的 bug。

**对齐算法的诚实边界**（必须在 UI 上标注）：

- Bangumi 的季/集划分与 TMDb 不总是 1:1（例：Bangumi 把两季合成一个词条、或含 SP / OVA）。
- 对齐策略：优先 `episode_number == round(sort)`；缺失时按序号顺序补位；**无法对齐的集不写评分**。
- UI 标注「评分来自 TMDb · N 集可对齐 / M 集未匹配」，不做静默猜测。

### 3.5 评分走势曲线

**纯函数层**（`data/calculator/EpisodeRatingAnalyzer.kt`，可单测，无 Android 依赖）

```kotlin
data class RatingPoint(val ep: Double, val label: String, val score: Float, val votes: Int?, val sourceId: String)

data class TrendStats(
    val count: Int,
    val average: Float,
    val max: RatingPoint?,
    val min: RatingPoint?,
    /** 最小二乘斜率（每集变化的分值）。 */
    val slope: Float,
    /** 评分标准差（波动性）。 */
    val volatility: Float,
    val direction: TrendDirection,   // RISING / FALLING / FLAT
    val movingAverage: List<Float>,
    val aboveAverage: List<RatingPoint>,
)

object EpisodeRatingAnalyzer {
    fun analyze(points: List<RatingPoint>, window: Int = 3): TrendStats
    /** 判定阈值：|slope| < 0.02 视为 FLAT。 */
    fun directionOf(slope: Float): TrendDirection
}
```

**绘制层**（`ui/subject/EpisodeRatingChart.kt`，Compose Canvas，**不引第三方图表库**）

- 坐标：X = 集号（连续编号；跨季时按季分段并加分隔线与季标签），Y = 分值（按数据 min/max 自适应留白，可选锁定 0–10）。
- 元素：渐变面积底 + 折线 + 圆点；**虚线平均线**；可选**移动平均线**（窗口 3 / 5 切换）；最高分 / 最低分高亮 + 标注。
- 交互：`detectTapGestures` 点选 → 气泡显示「第 N 集 · 标题 · 7.8（1.2k 票）」；集数多时支持横向滚动。
- 顶部结论条：`平均 7.82 · 最高 EP12 9.1 · 最低 EP4 5.3 · 趋势 ▲ 上升（+0.14/集）· 波动 0.86`。
- 多源切换：`TMDb | IMDb | 全部`（全部时双折线 + 图例）。
- 复用 `ui/theme/ChartPalette.kt` 与现有 Canvas 图表风格（`RatingDistributionChart` / `StatsScreen` 环形图）。
- 无障碍：曲线同时给出「数据表」文本视图（长按展开逐集分数列表），不依赖颜色传达信息。

**「番剧整体评分走势曲线」的三层解读**（用户可选做到第几层）

| 层 | 内容 | 成本 |
|---|---|---|
| L1 | 单季每集评分曲线（核心） | 小 |
| L2 | 跨季：TMDb 整剧各季平均分连线（需把多个 Bangumi 词条归到同一 TMDb 剧集） | 中 |
| L3 | 我的每集评分 vs TMDb / IMDb 双曲线（新增本地 `episode_my_ratings`） | 中 |

**可选加分项**：把「本季曲线」放进分享卡（复用现有 `ShareCardRenderer`）。

### 3.6 剧照 / 截图区块 + 全屏查看器

**数据源（按优先级，全部干净可用）**

| 来源 | 取法 | 适用 | 备注 |
|---|---|---|---|
| TMDb backdrops | `GET /3/tv/{id}/images?include_image_language=zh,ja,en,null` → `backdrops[]` | 动画 / 三次元 / 影视 | 官方 API，多语言 |
| TMDb 每集剧照 | `/3/tv/{id}/season/{n}` → `still_path` | 同上 | 与每集评分同一次请求，零额外成本 |
| TMDb movie images | `/3/movie/{id}/images` | 剧场版 | |
| Steam 截图 | 已有 `steam_games.screenshots` | 游戏 | 从 Steam 区块抽出，统一进「剧照」区（Steam 区块保留价格 / 成就 / 在线） |
| Anitabi 取景截图 | 已有 `anitabi_points` | 动画 | 与「取景地标」区块共用数据 |
| 豆瓣剧照 | **不做**（HTML 抓取 + 防盗链 Referer，脆弱且 ToS 风险） | — | 在方案中显式记录这个取舍 |

**UI**：`ThumbsSection` 横滑卡片（16:9）+ 右上「全部 N 张」→ 全屏查看器；每张图角标标注来源（TMDb / Steam / Anitabi）与集号。

**全屏查看器 `ui/common/ImageViewer.kt`**（项目当前完全没有，是本次的高性价比增量）

- `HorizontalPager` 翻页 + `Modifier.pointerInput` 的 `detectTransformGestures`（捏合缩放 / 双指平移）+ **双击缩放**（1× 与 2.5× 切换）；
- 缩放态下禁用翻页（`userScrollEnabled = scale <= 1f`），处理手势冲突；
- 顶部：`3 / 24` 计数 + 来源标签；底部：保存到相册（MediaStore）、分享、浏览器打开原图；
- 复用场景：剧照、Anitabi 取景、Steam 截图、封面候选预览、角色 / 人物头像。

### 3.7 封面候选浏览器（升级 `CoverPickerDialog`）

**候选来源（分组 Tab）**

| 组 | 取法 | 备注 |
|---|---|---|
| TMDb | `/3/tv/{id}/images`（posters）或 `/3/movie/{id}/images` | 多语言，按「原语言 → 元数据语言 → 无语言」排序；支持「本季 / 整剧」切换 |
| Bangumi | 现有 `images.large` | 1 张 |
| AniList | 已绑定时的 `coverImage.extraLarge` | 复用 `anilist_bindings` |
| VNDB | 已绑定时的 `image.url` | 仅 GAME |
| Steam | `headerImage` + `https://cdn.cloudflare.steamstatic.com/steam/apps/{appid}/library_600x900.jpg` | 仅 GAME |
| Anitabi | 取景地截图（大图） | 仅动画 |
| 本地 | 相册选图（现有） | 拷贝到 `filesDir/covers/` |

**排序规则**（翻译 AniShelf 的纯函数）：原语言 → 元数据语言 → 无语言，同级按分辨率降序；显示每张图的 `宽×高` 与语言角标。

**持久化改进（重要）**

- 覆层值从「完整 URL」升级为 **`{provider}:{path}` 引用**（如 `tmdb:/abc.jpg`、`steam:{appid}`、`local:{file}`），显示时按上下文尺寸解析 → 避免 TMDb 换尺寸目录后全库封面失效。
- 旧值（`http(s)://...` / `file://...`）保持兼容读取，不做强制迁移。
- 候选列表落 `cover_candidates` 缓存表（按 sourceId），避免每次打开都打远端。
- **把封面覆盖纳入 JSON 备份与 WebDAV 同步**（仅 `provider:path` 形式的远程引用；本地文件只存引用并提示「本机图片不随备份迁移」）。→ 顺带修掉「换设备封面全丢」的既有缺陷。

### 3.8 外链门户扩展（infobox 可点 + 新站点）

- `InfoBoxSection` 的 value 支持：URL 正则 / 已知站点键名（`官方网站`、`IMDb`、`TMDB`、`MAL`、`AniDB`、`豆瓣`、`Twitter`、`Pixiv`、`引用来源`）→ 渲染为可点链接（`PortalLauncher` 三级降级）。
- `PortalRegistry` 新增：IMDb、TMDb、MyAnimeList、AniDB、豆瓣（电影 / 游戏 / 音乐 / 读书）、**Fami通**（`famitsu.com` 搜索）、Metacritic、OpenCritic、IGDB、RAWG、Billboard、Oricon、MusicBrainz、Discogs、Google Books、Goodreads、ErogameScape（批评空间）、Getchu、DLsite、Bangumi 章节页（`bgm.tv/ep/{id}`）。
- 排序：有精确 ID 的排前（`PortalIds` 扩展 `tmdbId` / `imdbId` / `malId`），仅有标题搜索的排后。

### 3.9 人物 / 角色详情页补齐

**人物页**（`PersonDetailScreen.kt` + `PersonDetailViewModel` + `PersonDto`）

| 新增区块 | 数据来源 | 成本 |
|---|---|---|
| 基本信息（性别 / 生日 / 血型 / 身高 / 体重 / 出身地 / 引用来源 / 官网 / Twitter / Instagram） | `/v0/persons/{id}` 已有 `gender` / `birth_*` / `blood_type`；其余来自 `infobox`（需确认 v0 person 是否返回 infobox，见「待外部核实」） | 小 |
| 职位统计 Jobs | 对 `getPersonSubjects` 的 `staff` 字段做本地聚合（导演 12 · 脚本 5 …） | 小（纯函数） |
| 合作者 Collabs | 方案 ① 外链 `bgm.tv/{monoId}/collabs`（零成本）；方案 ② 本地计算：对该人物 Top-K 作品并发取 staff（复用 `searchPersons` 里已有的 `Semaphore(2)` + 上限 30 的限流写法），统计共同出现次数 | 小 / 中 |
| 收藏数 / 评论数 | DTO 的 `stat { collects, comments }`（当前被丢掉） | 极小 |
| 更多资料外链 | `bgm.tv/person/{id}` | 极小 |

**角色页**（`CharacterDetailScreen.kt`）

| 新增区块 | 数据来源 |
|---|---|
| 基本信息（别名 / 性别 / 生日 / 血型 / 身高 / 体重） | `CharacterDetailDto` 需扩展 `infobox` |
| 声优（CV）列表 | `CharacterDto.actors`（已有，但角色页未展示） |
| 收藏数 / 评论数 | `stat` |
| 出演作品（已有）保留，并对齐人物页的分组样式 | — |

---

## 四、权威数据源全景（按作品类型）

> 图例：可直连 = 免费公开 API 可直接接 ｜ 需密钥 = 用户自备免费 key ｜ 仅外链 = 只能外链 / 手动录入 ｜ 有风险 = 非官方或 ToS 存疑

### 4.1 动画 / 三次元（影视）

| 来源 | 权威性 | API | 接入 | 备注 |
|---|---|---|---|---|
| **Bangumi** | 中文圈社区主源 | 已有 | 已有 | 主源 |
| **Bilibili 评分** | 国内社区 | 已有 | 已有 | |
| **TMDb** | 国际主流聚合（**含每集评分**） | 需密钥：v3 `api_key`（也支持 v4 Bearer） | **本次接入（默认源）** | 实测：`/3/tv/{id}/season/{n}` **一次调用返回整季 `episodes[]`**，每集含 `vote_average` / `vote_count` / `still_path`，无需逐集请求；`/3/tv/{id}/external_ids` 与 `.../episode/{e}/external_ids` 都返回 `imdb_id`；`/configuration` 可校验 key；**旧限流 40 req/10s 已于 2019-12-16 取消，现约 40 req/s 上限，遇 429 退避**；`language=zh-CN` 可用；**国区需镜像 / 代理** |
| **IMDb** | 最权威的影视评分；**seriesgraph 的实际数据源** | 需密钥：**OMDb**（`omdbapi.com`，免费 **1,000 次/天**；Patreon 1 美元/月约 10 万次） | 本次接入（**默认关**，用户点「加载 IMDb 逐集评分」才跑） | 实测支持 `type=episode` + `i=tt...` 取**单集 IMDb 评分**；IMDb id 来自 TMDb 的 `external_ids`（整剧与单集都有）。⚠️ OMDb 内容为 **CC BY-NC 4.0、非 IMDb 官方**，不可商用 |
| IMDb 官方数据集 | 最权威原始分 | 批量 TSV：`title.ratings.tsv.gz`、`title.episode.tsv.gz` | **不做** | 解压后总计约 5.5GB、每日全量重下、需自建后端 ETL，**且仅限非商业**；移动端不可行 |
| MyAnimeList（Jikan） | 动画社区 | 可直连：`api.jikan.moe`（无 key，3 req/s、60/min） | 可选，且**必须熔断 + 本地缓存** | **更正一个常见误解：MAL 网站其实有逐集评分**（剧集页 Vote average，标尺是 **1–5 星**），只是 **MAL 官方 API v2 没有 episode 端点**；Jikan 靠爬 MAL 补上（其自述 *scrapes the website … which MyAnimeList lacks*），但**实测当前返回 504**，不可作唯一来源 |
| AniList | 动画社区 | 可直连：GraphQL（无 key） | 已有 | 已作兜底源 |
| AniDB | 老牌动画数据库（逐集标题/播出时间强） | 有风险：需注册 API client（UDP/HTTP），协议老旧、严限流 | 建议只外链 | **无逐集评分** |
| 豆瓣 | 中文权威社区 | **已封闭**：实测 `api.douban.com/v2/movie/subject/*` → **403 invalid_apikey** | 只做外链 | 不做抓取 |
| 日本电视收视率（Video Research） | 权威收视 | **无 API**；官网只发周榜，且**颗粒度是「番組平均」而非单集** | 不做 | 页面标注「無断転載禁止」→ **逐集日语收视率不可得**，已在文档中明确记录 |

### 4.2 游戏

| 来源 | 权威性 | API | 接入 | 备注 |
|---|---|---|---|---|
| **Fami通（ファミ通）交叉评测** | **日本最权威游戏评分（4 人 × 10 分 = 40 分制）** | **无 API、无开放数据集** | 仅外链 + **手动录入** | 分数散见：famitsu.com 周刊、Wikipedia 的 Famicom/Famitsu scores 列表（仅满分与高分）、Fandom、nintendoeverything 每周英文汇总。周刊有版权 → **方案：详情页「权威榜单成绩」区块允许用户录入 `Fami通 38/40`**，同时给搜索外链；可选做一个**人工整理的内置静态表**（需署名，仅覆盖名作）。**不做抓取** |
| **Metacritic** | 国际权威聚合（100 分制） | 无免费公开 API（RapidAPI 收费） | **已有半条路**：Steam `appdetails.metacritic.score` | 建议扩充 `metacritic.url` 外链；非 Steam 游戏走手动 / 外链 |
| **OpenCritic** | 国际权威聚合（推荐率 + 均分） | 官方 API **需在 portal.opencritic.com 申请 key**（RapidAPI 是官方渠道）；未文档化的 `api.opencritic.com` 属有风险 | 可选（标记实验性、默认关） | 日系小众作品覆盖较弱 |
| **IGDB（Twitch）** | 事实标准，**自带 critic 聚合** | 需密钥：Twitch client-credentials（免费），`api.igdb.com/v4` | 推荐接入 | 实测字段：`aggregated_rating`（**媒体/critic 均分**）+ `aggregated_rating_count`、`rating`（用户分）+ `rating_count`、`total_rating` / `total_rating_count`；**限流 4 req/s、最多 8 并发**。⚠️ IGDB **不含 Metacritic 分数**（那是它自家聚合） |
| **RAWG** | 游戏库（元数据为主） | 需密钥：`api.rawg.io`（免费约 2 万次/月） | 可选（与 IGDB 二选一或都接） | 有 `metacritic`（单平台数字）/ `rating` / `ratings_count` / `playtime`；**只有数字，没有 critic 聚合** |
| **Steam 好评率** | 玩家口碑（玩家侧最权威） | 可直连：`appreviews/{appid}?json=1&language=all&purchase_type=all`（`num_per_page=0` 只要汇总）**无需 key** | **强烈推荐，本次接入** | 实测 `query_summary.review_score_desc`（如「好评如潮」）+ `total_positive` / `total_negative` / `total_reviews`；`appdetails` 同时给 `metacritic.score` 与 `recommendations.total`；非正式限流约 200 req/5min |
| Steam Metacritic 字段 | — | 已有 | 已有 | |
| VNDB | VN 权威 | 已有 | 已有 | |
| ErogameScape / 批评空间 中央値 | 日式 VN 权威 | 无 API（HTML） | 仅外链 | |
| HowLongToBeat | 通关时长 | 有风险：非官方 API | 可选 / 外链 | 时长已有 VNDB `length_minutes` 与 Steam 游玩时长 |
| 游民 / 篝火 / IGN 中国 / 4Gamer / 电击 | 媒体评分 | 无 | 仅外链 | |

### 4.3 音乐

| 来源 | 权威性 | API | 接入 | 备注 |
|---|---|---|---|---|
| **Billboard** | 美国最权威榜单（Hot 100 / Billboard 200） | **无免费官方 API** | 仅外链 + **手动录入榜单成绩**（如 `Billboard 200 #12`） | 不抓取 |
| **Oricon（公信榜）** | 日本最权威榜单 | 无免费官方 API | 仅外链 + 手动录入（如 `Oricon 周榜 #3`） | 动画歌尤其常见 |
| **MusicBrainz** | 开放音乐元数据事实标准（**含社区评分**） | 可直连：`musicbrainz.org/ws/2/`（**无 key**） | 推荐接入 | 限流 **1 req/s（按 IP 平均）**，**必须带有效 `User-Agent`**；`release-group?query=...&fmt=json`，`inc=ratings` → `rating.value`（5 分制）/ `rating.votes-count`；同时可拿首发日期 / 厂牌 / 曲目 |
| **Discogs** | 权威唱片数据库 + 社区评分 | 需密钥：`api.discogs.com`（token；认证后 60 req/min，未认证 25） | 推荐接入 | `community.rating.average` / `count`、`year`、`label`、`format` |
| **Last.fm** | 收听量（热度，非评分） | 需密钥：`ws.audioscrobbler.com/2.0/`（免费 key） | 可选 | `album.getInfo` → `listeners` / `playcount`；标注为「热度」 |
| **iTunes / Apple Music Search API** | 官方曲库 + 榜单 | 可直连：`itunes.apple.com/search`（**无需 key**） | **推荐接入** | **日文歌 / 动画歌覆盖极佳**；可取 artwork、`previewUrl`、专辑信息；另有 Apple 的榜单 feed（`rss.applemarketingtools.com/api/v2/{country}/music/most-played/{n}/albums.json`，无需 key，按国家/地区）——可作为 Billboard/Oricon 的**免费替代榜单**（标尺与口径不同，需在 UI 注明） |
| Spotify | 流媒体元数据 | 需密钥：client credentials | 不建议 | ⚠️ **2024-11-27 起新应用已禁用 Audio Features / Audio Analysis / Recommendations**，只剩 popularity，**不能当评分用** |
| RateYourMusic (RYM) | 硬核乐迷权威 | 无官方 API | 仅外链 | |
| Album of the Year (AOTY) | 权威聚合 | 无官方 API | 仅外链 | |
| Pitchfork | 权威媒体评分 | 无公开 API（仅 RSS） | 仅外链 | |
| 豆瓣音乐 / 网易云 / QQ 音乐 | 中文社区 | 无官方 API / 已有外链 | 仅外链（网易云与 QQ 音乐门户已有） | |

### 4.4 书籍 / 漫画

| 来源 | 权威性 | API | 接入 |
|---|---|---|---|
| **Google Books** | 覆盖广（含中文书） | 可直连：`googleapis.com/books/v1/volumes`（可无 key，配额低；带 key 默认约 1,000/天） | 推荐接入：`averageRating`（5 分制）/ `ratingsCount`，**但评分极稀疏**，无数据时静默隐藏 |
| **Open Library** | 开放图书馆 | 可直连：`openlibrary.org/search.json`（无 key） | 推荐接入：`ratings_average` / `ratings_count` |
| 豆瓣读书 | 中文权威 | 无公开 API | 仅外链 |
| Goodreads | 英文书评权威 | **已停**：2020-12-08 起停止发放新 key，老 key 30 天未用即失效 | 仅外链 |
| Hardcover | Goodreads 替代（英文书） | 可直连：GraphQL + 免费 token | 可选备选 |
| Amazon | 商业 | 无 | 不做 |

### 4.5 视觉小说（VN）

| 来源 | API | 接入 |
|---|---|---|
| **VNDB** | 已有（读接口免鉴权） | 已有；实测限流 **200 req/5min**；字段 `rating`（贝叶斯均分）/ `average`（原始均分）/ `votecount` / `popularity` / `length_minutes` |
| ErogameScape 中央値 / 批评空间 | 无 API（HTML 统计表，中央値順 / 平均値順） | 仅外链。**日系旧作覆盖最强**，但无 API、无授权数据集，不抓取 |
| Getchu / DLsite | 无官方 API（仅销量榜与用户评论） | 仅外链 |

### 4.6 推荐接入顺序（权威性 × 可得性 ÷ 成本）

**第一批（现在就做：免费、无需后端、移动端可直连）**

1. **TMDb** — 影视/动画骨架：每集评分（一次调用拿全季）+ 剧照 + 封面候选 + `external_ids` 映射 IMDb。**逐集曲线的主引擎。**
2. **Bangumi** — 已有主源，中文覆盖最好。
3. **VNDB** — 已有，VN 唯一权威评分。
4. **Steam 好评率 + Metacritic 字段** — 已有 `appdetails`，只差 `appreviews` 正评率。
5. **MusicBrainz**（无 key，含社区评分）+ **iTunes Search API**（无 key，日文歌/动画歌覆盖极佳）。
6. **IGDB**（游戏 critic 聚合 `aggregated_rating`，4 req/s）。

**第二批（值得加，有局限）**

7. **OMDb**（1,000/天）— 用户想看 **IMDb 数字**时补一层；**CC BY-NC 不可商用**，加缓存 + 失败降级 TMDb。
8. **Discogs**（token）— 音乐官方社区评分。
9. **Google Books + Open Library**（无 key）— 书籍评分（稀疏，命中才显示）。
10. **RAWG**（与 IGDB 二选一）、**Jikan**（MAL 总分；**当前 504，必须熔断 + 本地缓存**）。

**第三批（不做，只外链 / 手动录入）**

11. **Fami通、Metacritic、OpenCritic、Billboard、Oricon、豆瓣、Goodreads、RYM、AOTY、Pitchfork、Getchu、DLsite、ErogameScape、日本收视率、AniDB** —— 无 API、已封闭或有风险。
12. **IMDb 官方 TSV 数据集** —— 最权威但需自建后端每日 ETL，**等有服务端再说**。
13. **MobyGames**（个人档 $9.99/月）、**Amazon PA-API**（需销量） —— 门槛过高，排除。

### 4.7 许可证与署名（合规要点）

| 源 | 许可 / 署名要求 | 动作 |
|---|---|---|
| TMDb | 需标注不是 TMDb 官方认可 | 设置页与「关于」页加官方署名声明 |
| OMDb / IMDb 数据 | **CC BY-NC 4.0，不可商用** | UI 标注来源；不得用于付费版 |
| IMDb 官方数据集 | 仅限个人/非商业 | 本项目不做 |
| MusicBrainz | 数据 CC0/CC BY-NC-SA（视字段），**要求有效 User-Agent** | 请求头带 `Niriko/<version> (contact)` |
| VNDB | 有使用条款与限流要求 | 已有 User-Agent + 200/5min |
| 日本收视率（Video Research） | 页面标注「無断転載禁止」 | 明确不做 |
| 豆瓣 / RYM / AOTY / 批评空间 | 条款禁止抓取 | 只做外链 |

---

## 五、数据库迁移方案

当前 `NirikoDatabase` = **version 24**，迁移链 v2 → v24。本次建议 **v24 → v25（结构）**；若采纳 L3 与手动录入，再加 **v25 → v26**。

### v24 → v25（必需）

```sql
-- 1) 修既有 bug + 新列
ALTER TABLE episodes ADD COLUMN description TEXT;
ALTER TABLE episodes ADD COLUMN disc INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN stillUrl TEXT;

-- 2) 每集评分（按来源）
CREATE TABLE IF NOT EXISTS episode_ratings (
  epId INTEGER NOT NULL, subjectId INTEGER NOT NULL, sourceId TEXT NOT NULL,
  score REAL, scoreMax REAL NOT NULL DEFAULT 10.0, voteCount INTEGER, fetchedAt INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(epId, sourceId));
CREATE INDEX IF NOT EXISTS index_episode_ratings_subjectId ON episode_ratings(subjectId);

-- 3) 权威评分
CREATE TABLE IF NOT EXISTS subject_external_ratings (
  subjectId INTEGER NOT NULL, sourceId TEXT NOT NULL, label TEXT NOT NULL,
  score REAL, nativeScore REAL, scoreMax REAL NOT NULL DEFAULT 10.0, voteCount INTEGER,
  sourceUrl TEXT, extraJson TEXT, fetchedAt INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(subjectId, sourceId));

-- 4) 外部身份
CREATE TABLE IF NOT EXISTS subject_external_ids (
  subjectId INTEGER NOT NULL, provider TEXT NOT NULL, externalId TEXT NOT NULL,
  titleSnapshot TEXT, confidence REAL NOT NULL DEFAULT 0.0,
  subKey TEXT, bindMethod TEXT NOT NULL DEFAULT 'manual', boundAt INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(subjectId, provider));
CREATE INDEX IF NOT EXISTS index_subject_external_ids_provider ON subject_external_ids(provider, externalId);

-- 5) 封面候选缓存（可选，但推荐）
CREATE TABLE IF NOT EXISTS cover_candidates (
  subjectId INTEGER NOT NULL, sourceId TEXT NOT NULL, candidatesJson TEXT NOT NULL,
  fetchedAt INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(subjectId, sourceId));
```

> 注意：**`desc` 是 SQLite 保留字**，列名用 `description`，Kotlin 字段仍可叫 `desc`（`@ColumnInfo(name = "description")`）。

### v25 → v26（可选，取决于是否采纳 L3 与手动录入）

```sql
CREATE TABLE IF NOT EXISTS episode_my_ratings (
  epId INTEGER PRIMARY KEY, subjectId INTEGER NOT NULL, score REAL NOT NULL, ratedAt INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS index_episode_my_ratings_subjectId ON episode_my_ratings(subjectId);

CREATE TABLE IF NOT EXISTS manual_awards (
  id INTEGER PRIMARY KEY AUTOINCREMENT, subjectId INTEGER NOT NULL, sourceId TEXT NOT NULL,
  score REAL, scoreMax REAL, rankPosition INTEGER, note TEXT, url TEXT);
CREATE INDEX IF NOT EXISTS index_manual_awards_subjectId ON manual_awards(subjectId);
```

**同步 / 备份策略（需明确决策，见第七节）**

- `episode_ratings` / `subject_external_ratings`：**纯远端缓存，不进备份**（可重抓）。
- `subject_external_ids`：**进备份**（重建成千上万条绑定代价高）。
- `episode_my_ratings`、`manual_awards`、**封面覆盖（远程引用形式）**：**进备份 + WebDAV 同步**（是用户数据）。

---

## 六、分阶段实施计划

### 阶段 0 · 基础设施（1–1.5 天，风险低）

- `TmdbClient` / `TmdbApiService` / DTO（`kotlinx.serialization`，**title / overview 一律 nullable**）/ `TmdbImageUrl`（只存 path + 尺寸档）/ `TmdbLanguage`（en / zh-CN / ja）。
- **端点与密钥**：`AppSettings` 增 `tmdbApiKey`、`tmdbEndpoint`（OFFICIAL / MIRROR / CUSTOM）、`tmdbMirrorBaseUrl`、`omdbApiKey`、`igdbClientId` / `Secret`、`rawgApiKey`、`discogsToken`、`lastFmApiKey`。
  - **可直接复用现成写法**：`data/remote/BangumiClient.kt:128-143` 的 `baseUrlInterceptor` 已经是「运行时动态 baseUrl：Retrofit baseUrl 构建时固定，拦截器在每次请求前把 scheme/host/port 重写到目标端点，路径与查询参数保持不变」——TMDb 官方/镜像切换照抄这一段即可，UI 复用设置页现有的「Bangumi 接口端点」下拉样式。
- **密钥校验**：`GET /3/configuration` 校验 TMDb key（UI 给「校验中 / 有效 / 无效」）；OMDb 用 `?i=tt0111161` 校验。
- **429 / Retry-After** 拦截器；并发去重复用项目已有的 `AsyncSingleFlight`。
- 设置页新增「**权威数据源与密钥**」分区（`DataSourceSettingsScreen.kt`），每源显示「已配置 / 未配置 / 校验状态」+ 说明 + **TMDb 版权声明**。
- `RefreshResource` 增 `TMDB_DETAIL`、`EPISODE_RATINGS`、`EXTERNAL_RATINGS`、`THUMBNAILS`。
- 迁移 v24 → v25 + DAO / Entity。
- **顺带修 bug**：`EpisodeRepository.toEpisodeInfo()` 不再丢 `desc` / `disc`（并新增 `stillUrl`）。

### 阶段 1 · 每集评分 + 走势曲线（2–3 天，风险中；本方案核心）

- TMDb 绑定：`subject_external_ids` 候选区（`TmdbCandidateSection`）+ infobox ID 通道 + 手动搜索。
- `EpisodeRatingRepository`：`/tv/{id}` → 选季 → `/season/{n}` → 落 `episode_ratings` + `episodes.stillUrl`。
- 集号对齐算法 + 「N 集可对齐 / M 集未匹配」标注。
- `EpisodeRatingAnalyzer`（纯函数）+ **单测**（均值 / 极值 / 斜率 / 波动 / 移动平均 / 对齐）。
- `EpisodeRatingChart`（Canvas）+ 结论条 + 点选气泡 + 多源切换。
- `EpisodeListSection`（集号 / 标题 / 播出日 / 时长 / 讨论数 / 评分角标 / 剧照缩略图 / 「看到这里」写进度）。
- `EpisodeDetailSheet`（单集描述 + 评分 + 剧照 + Bangumi 章节页外链）。
- 可选 IMDb 分集：`external_ids` → OMDb，按钮触发 + 并发限流 + 落库缓存 + 进度提示。

### 阶段 2 · 剧照 / 截图 + 全屏查看器（1–1.5 天，风险低）

- `ThumbsSection`（TMDb backdrops + 每集 stills + Steam 截图 + Anitabi，按来源角标）。
- `ui/common/ImageViewer.kt`（捏合 / 双击缩放 + Pager + 保存相册 + 分享）。
- 把 Steam 区块的截图横滑迁移到统一剧照区（Steam 区块保留价格 / 成就 / 在线）。

### 阶段 3 · 封面候选浏览器升级（1–1.5 天，风险低）

- `CoverPickerDialog` → 分组 Tab + 语言优先排序 + 分辨率角标 + 全屏预览确认。
- 覆层值升级为 `provider:path`（旧值兼容读取）+ `cover_candidates` 缓存 + 「本季 / 整剧」切换。
- 封面覆盖纳入 JSON 备份（远程引用）；本地图给出明确提示。

### 阶段 4 · 游戏权威评分（1–2 天，风险低）

- Steam 好评率（`appreviews`，无 key）→ 游戏权威评分卡。
- Metacritic 补 `metacritic.url` 外链 + Steam 全区好评率。
- IGDB（Twitch client-credentials，4 req/s）：用 **`aggregated_rating`（critic 聚合）**作为游戏媒体均分；与 RAWG（只有 `metacritic` 单值）二选一即可。
- **注意不要张冠李戴**：IGDB 的 `aggregated_rating` **不是 Metacritic 分数**，UI 标签必须写清来源。
- **Fami通**：外链 + 手动录入（`manual_awards`），UI 明确标注「手动录入」；可选做一个需署名的人工静态表（仅覆盖名作）。

### 阶段 5 · 音乐 / 书籍 / VN 权威评分（1.5–2 天，风险低）

- MusicBrainz（无 key，含社区评分；1 req/s + 有效 User-Agent）+ Discogs（token 评分）。
- **iTunes / Apple Music Search API**（无 key）：补曲目、封面与试听；**日文歌 / 动画歌覆盖极佳**。
- **Apple 榜单 feed**（`rss.applemarketingtools.com`，无 key，按国别）作为 Billboard / Oricon 的**免费替代榜单**（口径不同，UI 注明；实施前先按 11.2-6 验证字段）。
- Google Books + Open Library（无 key；评分稀疏，无数据即隐藏）。
- Billboard / Oricon / RYM / AOTY：外链 + 手动榜单成绩录入。
- VNDB 评分迁入统一评分卡（数据已有）。

### 阶段 6 · 人物 / 角色详情补齐（1–1.5 天，风险低）

- 人物：基本信息 / 职位统计 / 收藏数 / 更多资料外链；合作者先做外链，本地计算作为可选加项。
- 角色：基本信息 / CV 列表 / 收藏数。

### 阶段 7 · 收尾（1 天，风险低）

- 评分分歧度（标准差 → 异口同声 … 厨黑大战）+ 本地库内同类百分位。
- infobox 外链可点 + `PortalRegistry` 扩展。
- 备份 / 同步策略落地 + 恢复回归。
- README / docs 更新。

**合计约 10–14 个工作日**（不含真机联调与国区网络验证）。

---

## 七、待你确认的决策点

| # | 决策 | 选项 | 我的建议 |
|---|---|---|---|
| D1 | 走势曲线做到第几层 | L1 单季 / L2 跨季 / L3 我的每集评分 | **先 L1**（阶段 1），L3 作为 v26 可选 |
| D2 | IMDb 分集评分是否接入（**seriesgraph 的真正数据源就是 IMDb**） | 接（OMDb key，1,000/天，N×2 请求）/ 不接（只用 TMDb 逐集分） | **接，但默认关**：默认用 TMDb（免费、一次调用拿全季）；用户点「加载 IMDb 逐集评分」再拉，带进度提示与断点续拉。**两个源数值不同，UI 必须标清来源** |
| D3 | 游戏媒体均分走哪家 | IGDB / RAWG / 都接 / 都不接（只用 Steam 好评率 + Metacritic） | **Steam 好评率 + 已有 Metacritic 字段**打底；IGDB 作为第一优先扩展 |
| D4 | Fami通 / Billboard / Oricon 怎么处理 | 手动录入 + 外链 / 抓取（不推荐）/ 完全不做 | **手动录入 + 外链**（合法、零维护） |
| D5 | TMDb 国区可达性 | 内置公共镜像 / 用户自填镜像地址 / 只支持直连 + 提示 | **用户自填镜像 + 官方/自定义开关**（公共镜像有法律与稳定性风险，不宜硬编码） |
| D6 | 绑定是否自动 | 仅候选需确认（与阶段 G 一致）/ 高置信度自动绑 | **仅候选需确认**（保持一致性与保守化） |
| D7 | 封面覆盖是否进备份 | 进（远程引用）/ 不进 | **进**（顺带修「换设备封面全丢」） |
| D8 | 是否引第三方图表库（Vico 等） | 引 / 手绘 Canvas | **手绘 Canvas**（与现有 3 处图表一致，零依赖、可控） |

---

## 八、明确不做

1. **豆瓣抓取**（剧照 / 评分）——HTML + 防盗链 + ToS 风险，脆弱。只做外链。
2. **IMDb 官方批量数据集**（`title.ratings.tsv.gz` 等）——移动端体积 / 更新成本不划算。
3. **Fami通 / Billboard / Oricon 的网络抓取**——版权与反爬。改手动录入。
4. **AniDB / ErogameScape / Getchu 的 API 逆向**——收益低、限流严。只做外链。
5. **社区内容**（吐槽箱 / 讨论版 / 目录 / 小组 / 好友 / 超展开）——项目定位明确排除。
6. **本地媒体文件夹（SMB）**——`docs/bangumi-reference-plan.md` 已列为暂缓 P2。
7. **硬编码任何 API key**（AniShelf 明确写了 NEVER COMMIT A KEY）。

---

## 九、验收清单

**构建与测试**

- [ ] `.\gradlew.bat assembleDebug` 通过
- [ ] `.\gradlew.bat testDebugUnitTest` 通过
- [ ] 新增纯函数单测：`EpisodeRatingAnalyzer`（斜率 / 波动 / 移动平均 / 极值 / 空数据）、`RatingInsights`（标准差→分歧度、归一化）、`TmdbImageUrl`（path 解析与反解）、集号对齐算法
- [ ] 迁移单测：构造 v24 库 → 迁移到 v25，断言新表 / 新列存在且既有数据不丢

**功能验收**

- [ ] 未配置任何 TMDb key 时：详情页一切照旧（不出现空区块、不报错、不阻塞）
- [ ] 配置 TMDb key 后：动画详情页出现「TMDb」区块 + 候选 / 绑定入口
- [ ] 绑定后：出现「剧集」列表（含每集评分角标）+「评分走势」曲线 +「剧照」区
- [ ] 曲线点选显示单集标题与票数；平均线 / 最高 / 最低 / 趋势标注正确
- [ ] 全屏查看器：捏合缩放、双击缩放、翻页、保存到相册均可用
- [ ] 更换封面：至少 2 个来源的候选、语言排序正确、选择后列表卡与详情页同步生效、「恢复默认」可用
- [ ] 游戏：Steam 好评率与 Metacritic 同屏显示；Fami通 手动录入后可展示与删除
- [ ] 音乐：MusicBrainz 评分（若可命中）+ Billboard / Oricon 手动录入
- [ ] 人物页：基本信息 / 职位统计 / 收藏数 / 合作者（或外链）出现
- [ ] 备份导出 → 清数据 → 导入：`subject_external_ids`、封面覆盖、我的每集评分（若做）全部恢复
- [ ] 刷新诊断页出现新资源且可强制刷新

**文档**

- [ ] README 增加「权威评分数据源」「每集评分与走势曲线」「剧照与全屏查看器」小节
- [ ] 本文件更新为「已实施」状态（或另开实施记录文档）

---

## 十、风险登记

| 风险 | 影响 | 缓解 |
|---|---|---|
| `api.themoviedb.org` / `image.tmdb.org` 国区不可直连 | TMDb 全功能不可用 | 镜像 / 自定义 base URL 开关（D5）；所有 TMDb 功能**可缺省降级**；未配置时不出现任何 TMDb UI |
| TMDb 集号与 Bangumi 季 / 集划分不一致 | 曲线错位 | 只写能对齐的集；UI 标注「N 集可对齐 / M 集未匹配」；提供手动「季选择」下拉 |
| 动画每集评分票数偏少（新番刚播） | 曲线噪声大 | 曲线上标注票数；低于阈值（如 < 10 票）的点用空心点区分，并提供「隐藏低票数」开关 |
| OMDb 免费额度 1000/天 | 分集 IMDb 拉不全 | 默认关；按钮触发；进度提示；失败可续拉（已拉过的有 `fetchedAt`） |
| IGDB / RAWG / OpenCritic 密钥与 ToS | 源不可用 | 全部可选、独立开关、失败静默隐藏 |
| 新增区块导致详情页变长 / 变慢 | 体感回归 | 沿用 `loadExtendedData` 的并行 + 进程内快照缓存；新资源全部走 `FreshnessPolicy` + 退避；`RevealOnScroll` |
| 迁移出错 | 用户数据丢失 | 迁移单测 + 只做 `ALTER TABLE ADD COLUMN` 与 `CREATE TABLE`（不改列类型、不删列） |
| TMDb 版权 / 署名要求 | 合规 | 设置页与「关于」页加 TMDb 官方要求的署名声明 |

---

## 十一、核实状态

### 11.1 已核实（本轮通过 web_fetch 直抓官方文档 / API 实测，详见 `docs/rating-data-sources-research.md`）

| 结论 | 影响 |
|---|---|
| **seriesgraph 的逐集数据来自 IMDb，不是 TMDb** | 决定了「TMDb 默认 + IMDb 可选」的双源设计（见 2.3、4.1） |
| `/3/tv/{id}/season/{n}` **一次调用返回整季每集 `vote_average`/`vote_count`** | 每集评分成本从 N 次请求降到 1 次 |
| TMDb 整剧与单集的 `external_ids` 都返回 `imdb_id` | IMDb 分集链路可行 |
| TMDb 旧限流（40 req/10s）已于 2019-12-16 取消，现约 40 req/s | 方案里的限额数字已更新 |
| **OMDb 免费 1,000 次/天，支持 `type=episode`**，内容 CC BY-NC 4.0 | IMDb 层可行性确认；许可限制已记入 4.7 |
| **Bangumi `/v0/episodes` 无任何逐集评分字段**（`comment` 是评论数） | 不再考虑「Bangumi 分集评分」源 |
| **AniList 无 Episode 类型、无逐集评分** | 排除 |
| **MAL 网站有逐集评分但官方 API 无 episode 端点**，Jikan 靠爬且当前 504 | 不把 Jikan 逐集分作为来源；Jikan 仅作整作分且必须熔断 |
| **Video Research 只有「番組平均」周榜，无逐集数据，且禁止转载** | 明确不做 |
| **Metacritic / Fami通 / Oricon / Billboard / 豆瓣 / Goodreads / RYM / AOTY 均无可用 API**（豆瓣实测 403、Goodreads 2020-12-08 停发 key） | 一律只做外链 + 手动录入 |
| **IGDB 含 critic 聚合 `aggregated_rating`，限流 4 req/s**，但**不含 Metacritic 分数** | IGDB 作为游戏媒体均分首选；不要把 IGDB 的分数标成 Metacritic |
| Spotify 新应用已禁用 Audio Features（2024-11-27） | 不把 Spotify 当评分源 |
| VNDB 免 key、200 req/5min，字段含 `rating`/`average`/`votecount` | 已接入，无需改动 |

### 11.2 仍需实施时用真实 key / 真机验证

1. `/3/tv/{id}/season/{n}` 的逐集评分在**中文与日文条目**上的实际填充率（部分冷门番可能为 0，需据此定「低票数」阈值）。
2. 中文动画条目的 **TMDb 覆盖度与标题匹配命中率**（建议抽样 50 个 Bangumi 动画词条统计，用于定候选排序权重）。
3. Bangumi `/v0/persons/{id}` 是否返回 `infobox`（决定人物「基本信息」走 API 还是需额外取 HTML）。
4. TMDb 的实际限流表现与**镜像可用性**（国区网络）。
5. OMDb 对**日文动画分集** IMDb id 的命中率。
6. Apple 榜单 feed（`rss.applemarketingtools.com`）的字段与更新频率（决定能否作为 Billboard/Oricon 的免费替代）。
