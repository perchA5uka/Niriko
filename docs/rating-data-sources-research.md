# Niriko 评分数据源调研报告（Critic / Aggregate Rating 可接入性）

> **图例**：✅ = 本次用 `web_fetch` 直接抓取官方文档或 API 实测验证；🟡 = 来自搜索摘要 / 既有知识，本次未能直接抓取（调研期间 `web_search`/`read_page` 故障，且部分站点对本机网络不可达）。
> **背景**：Niriko 为 Kotlin/Compose 移动端，**无自建后端**。因此"bulk 数据集"类方案（IMDb TSV）实际不可用，优先选"移动端直接 HTTPS 调用 + 免费 key"的源。

---

## 一、通用结论（先说重点）

1. **没有任何"权威评分机构"提供免费的官方评分 API**。Metacritic、Famitsu、Oricon、Billboard、Douban、Goodreads、RateYourMusic、AllMusic、Pitchfork、Getchu、DLsite 全部**没有**开放 API。
2. 真正能用的是三类：**① 聚合数据库 API**（TMDB / IGDB / RAWG / OMDb / VNDB / Bangumi / MusicBrainz / iTunes）；**② 社区评分 API**（Jikan / AniList / Kitsu）；**③ 官方 bulk 数据集**（IMDb，仅非商业，需自建后端）。
3. **日中内容覆盖最好且免费**：TMDB（日剧/动画元数据+逐集分）、Bangumi（中文动画/书籍/游戏/音乐）、VNDB（VN）、AniList/Jikan（动画）、iTunes（日文歌）。
4. **Metacritic 分数在 IGDB/RAWG 里都没有**（IGDB 是自家 critic 聚合；RAWG 只有一个 `metacritic` 数字字段）。

---

## 二、游戏（Game）

| 来源 | 权威性说明 | 公开 API? | 文档 URL | 免费 key? | 速率限制 | 中日亚覆盖 | 接入难度 | 备注 |
|---|---|---|---|---|---|---|---|---|
| **IGDB**（Twitch） | 业界事实标准，含 critic 聚合 | ✅ 有 | [api-docs.igdb.com](https://api-docs.igdb.com/) | ✅ 免费（Twitch 应用 client credentials） | **4 req/s**，最多 8 并发 ✅ | 中 | 低 | ✅ 实测字段：`aggregated_rating`、`aggregated_rating_count`、`rating`、`rating_count`、`total_rating`、`total_rating_count`；`aggregated_rating` = **媒体均分(critic)**，`rating` = 用户分 |
| **RAWG.io** | 元数据为主 | ✅ 有 | [api.rawg.io/docs](https://api.rawg.io/docs/) | ✅ 免费 2 万次/月🟡 | 20,000 req/月（免费） | 中 | 低 | 🟡 有 `metacritic` 字段（单平台数字）；本机 API 域名不可达未能实测 |
| **OpenCritic** | 游戏 critic 聚合 | ⚠️ 官方 API 需申请 key | [RapidAPI 官方页](https://rapidapi.com/opencritic-opencritic-default/api/opencritic-api) | ⚠️ [portal.opencritic.com](https://portal.opencritic.com/) 申请🟡 | 未公开🟡 | 弱（日系小众差） | 中 | 🟡 本次域名不可达 |
| **Metacritic** | 最知名 critic 聚合 | ❌ 无官方 API | — | — | — | 弱 | 高 | 2013 年后无公开 API；ToS 禁止抓取，**不建议依赖** |
| **Steam（官方）** | 玩家评测 + 内嵌 Metacritic | ✅ 有 | [appdetails](https://wiki.teamfortress.com/wiki/User:RJackson/StorefrontAPI) / [getreviews](https://partner.steamgames.com/doc/store/getreviews) | ✅ 无需 key | 约 200 req/5min（非正式）🟡 | 中日系游戏很全 | 低 | ✅ getreviews 文档可达；`appdetails` 返回 `metacritic.score` 与 `recommendations.total`；`appreviews?json=1&num_per_page=0` 返回 `query_summary.total_positive/total_negative/review_score_desc` → **正评率** |
| **VNDB** | VN 界唯一权威库 | ✅ 有 | [api.vndb.org/kana](https://api.vndb.org/kana) | ✅ **无需 key**（读接口免鉴权）✅ | **200 req/5min**，1s 执行时间/分钟 ✅ | **日系 VN 全覆盖** | 低 | ✅ `/vn` 字段含 `rating`（贝叶斯均分）、`average`、`votecount`、`popularity`、`length_minutes` |
| **MobyGames** | 老牌游戏库 | ✅ 有 | [mobygames.com/api](https://www.mobygames.com/api/) | ❌ **已收费**（Hobbyist $9.99/月；商用 Bronze $99.99/月）🟡 | 阶梯 | 中 | — | 个人项目也不免费，**排除** |
| **Famitsu クロスレビュー** | 日本最权威游戏评分（4 人 × 10 分 = 40 分） | ❌ **无 API、无开放数据集** | — | — | — | **日系最强** | 高 | 分数散见：famitsu.com 周刊、[Wikipedia「Famitsu scores」](https://en.wikipedia.org/wiki/Famitsu_scores)（仅满分/高分）、Fandom「Famitsu scores」、[nintendoeverything](https://nintendoeverything.com/famitsu-review-scores-july-30-2025/) 每周英文汇总。**只能爬或人工录入**，周刊有版权 |
| **HowLongToBeat** | 通关时长（非评分） | ❌ 无官方 API | — | — | — | 中 | 中 | 仅第三方爬虫库（howlongtobeatpy 等）；只能当时长补充 |
| **Backloggd / 4Gamer / 電撃 / Douban 游戏** | 社区/媒体评分 | ❌ 均无公开 API | — | — | — | 中日 | 高 | Backloggd 无 API；4Gamer/電撃只有文章；Douban 见 §四 |
| **GameRankings** | 已停运 | — | — | — | — | — | — | 2019 年关闭并入 Metacritic |

---

## 三、音乐 / 专辑（Music）

| 来源 | 权威性说明 | 公开 API? | 文档 URL | 免费 key? | 速率限制 | 中日亚覆盖 | 接入难度 | 备注 |
|---|---|---|---|---|---|---|---|---|
| **MusicBrainz** | 开放音乐元数据事实标准 | ✅ 有 | [musicbrainz.org/doc/MusicBrainz_API](https://musicbrainz.org/doc/MusicBrainz_API) | ✅ **无需 key** | **1 req/s（按 IP 平均）**，全局 300/s ✅ | 中 | 低 | ✅ 必须带有效 **User-Agent**；有 rating 字段但以元数据为主 |
| **iTunes / Apple Music Search API** | 官方曲库 + 榜单 | ✅ 有 | [performance-partners.apple.com/search-api](https://performance-partners.apple.com/search-api) | ✅ **无需 key** | 约 20 calls/min 🟡 | **日文歌/动画歌极佳** | 低 | 可搜日文曲名/专辑，取 artwork + previewUrl；有 feed 榜单接口 |
| **Spotify Web API** | 流媒体元数据 | ✅ 有 | [developer.spotify.com](https://developer.spotify.com/documentation/web-api) | ✅ client credentials | 滚动限流 | 中 | 低 | ✅ **2024-11-27 起新应用禁用 Audio Features / Audio Analysis / Recommendations / Related Artists**；只剩 popularity，**不能当评分用** |
| **Discogs** | 唱片目录权威 | ✅ 有 | [discogs.com/developers](https://www.discogs.com/developers) | ✅ 需 token | 25 req/min（鉴权）🟡 | 中（日版发行收录好） | 低 | 本机被 Cloudflare 拦截未能实测 |
| **Last.fm** | 播放量社区 | ✅ 有 | [last.fm/api](https://www.last.fm/api) | ✅ 免费 key | 约 5 req/s 🟡 | 中 | 低 | 有 listeners/playcount，**不是专业评分**，当"热度" |
| **Billboard（Hot 100/200）** | 美国最权威榜单 | ❌ **无官方 API** | — | — | — | 弱（Billboard Japan 有动画榜） | 高 | 官方 API 2013 年终止；只能非官方爬虫（github `guoguo12/billboard-charts`）；[kworb.net](https://kworb.net/) 有整理数据 |
| **Oricon** | 日本最权威销量榜 | ❌ 无公开 API | — | — | — | **日系最强** | 高 | oricon.co.jp 榜单页每周二更新；动画主题歌/Anison 榜需自行抓取 |
| **RateYourMusic (RYM)** | 乐迷评分权威 | ❌ 无官方 API | — | — | — | 弱-中 | 高 | 仅非官方 `rymscraper`；ToS 禁止抓取 |
| **Album of the Year / Pitchfork / AllMusic** | 乐评聚合/专业乐评 | ❌ 无 API | — | — | — | 弱 | 高 | Pitchfork 只有 RSS；分数需解析页面 |
| **网易云音乐 / QQ 音乐** | 中文社区评分 | ⚠️ 非官方 | [NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi) | — | 不保证 | 中文强 | 中 | 需自建实例，随时失效 |
| **Douban 音乐** | 中文权威评分 | ❌ 已封闭 | — | — | — | 中文强 | 高 | api.douban.com 已封闭 ✅ |

---

## 四、动画 / TV / 电影（Anime / TV / Movie）

| 来源 | 权威性说明 | 公开 API? | 文档 URL | 免费 key? | 速率限制 | 中日亚覆盖 | 接入难度 | 备注 |
|---|---|---|---|---|---|---|---|---|
| **TMDB** | 元数据事实标准；**有逐集评分** | ✅ 有 | [developer.themoviedb.org](https://developer.themoviedb.org/reference/tv-season-details) | ✅ 免费（v3 api_key / v4 Bearer）✅ | 旧限流 40 req/10s 已 2019-12-16 取消；**上限约 40 req/s**，尊重 429 ✅ | **中/日/韩剧 + 动画齐全**，`language=zh-CN` ✅🟡 | 低 | ✅ `/3/tv/{id}/season/{n}` **一次调用返回整季 episodes[]**，每集含 `vote_average` / `vote_count`；✅ `/3/tv/{id}/external_ids` 与 `.../episode/{e}/external_ids` 均返回 `imdb_id` |
| **OMDb** | IMDb 评分代理（免费路线） | ✅ 有 | [omdbapi.com](https://www.omdbapi.com/) | ✅ **免费 key，1,000 次/天** ✅ | 1,000/天（免费）；🟡 $1/月=10 万/天 | 中（冷门日影日剧缺） | 低 | ✅ 支持 `type=episode` + `i=tt...` 取**单集 IMDb 评分**；内容 CC BY-NC 4.0、非 IMDb 官方 ✅ |
| **IMDb 官方数据集** | IMDb 原始评分（最权威） | ✅ bulk TSV（非 API） | [non-commercial-datasets](https://developer.imdb.com/non-commercial-datasets/) / [datasets.imdbws.com](https://datasets.imdbws.com/) | ✅ 免费但**仅限非商业** | 每日全量重下 | 全球 | **高（移动端不可行）** | ✅ 文件：`title.ratings.tsv.gz`(tconst, averageRating, numVotes)、`title.episode.tsv.gz`(tconst, parentTconst, seasonNumber, episodeNumber)；🟡 体积约 ratings≈8MB / episode≈40MB / basics≈190MB，解压后总计 ~5.5GB。**必须自建后端 ETL** |
| **TVDB v4** | 剧集元数据（欧美强） | ✅ 有 | [thetvdb.github.io/v4-api](https://thetvdb.github.io/v4-api/) | ⚠️ 年收入 <$50k 免费（需署名），否则 $1,000/年起；或用户自带 $12/年订阅 🟡 | 未公开🟡 | 中 | 中 | 评分能力弱，主要用于元数据/集号 |
| **Trakt** | 用户社区评分/追番 | ✅ 有 | [trakt.docs.apiary.io](https://trakt.docs.apiary.io/) | ✅ 免费 client id | 1,000 req/5min 🟡 | 中 | 低 | 有 rating/votes（社区分）；apiary 需 JS 未实测 |
| **Jikan（非官方 MAL）** | MAL 数据事实通道 | ✅ 有 | [docs.api.jikan.moe](https://docs.api.jikan.moe/) | ✅ **无需 key** | **3 req/s、60 req/min、日上限无限** ✅ | **日系动画最强** | 低 | ✅ 官方自述 "It **scrapes** the website … which **MyAnimeList lacks**"；✅ 有 `/anime/{id}/episodes`。⚠️ **实测当前 504**（MAL 上游拒连），稳定性差 |
| **MAL 官方 API v2** | 官方 | ✅ 有（但无 episode 端点）🟡 | [myanimelist.net/apiconfig](https://myanimelist.net/apiconfig/references/api/v2) | ✅ OAuth2 | 未明示 | 日系 | 中 | 🟡 文档为 Redoc 壳；**未提供章节列表/章节评分端点** |
| **AniList** | 动画社区权威（GraphQL） | ✅ 有 | [docs.anilist.co](https://docs.anilist.co/) | ✅ 无需 key | 90 req/min（降级 30）🟡 | **日系动画+日漫最强** | 低-中 | 🟡 **无 Episode 类型**：只有 `episodes`（集数）、`meanScore`（总分）、`streamingEpisodes`（元数据），**没有逐集评分** |
| **AniDB** | 老牌动画库（逐集标题/播出） | ✅ 有（UDP/HTTP） | [anidb.net/software](https://anidb.net/software) | ⚠️ 需注册 API client（要有账号） | 严格 | **日系元数据最强** | 高 | **无逐集评分**；协议老旧，不适配移动端 |
| **Kitsu** | 动画社区 | ✅ 有 | [kitsu.docs.apiary.io](https://kitsu.docs.apiary.io/) | ✅ 公开读免 key | 未明示🟡 | 中 | 低 | JSON:API；评分是整体 averageRating |
| **Bangumi（番组计划）** | **中文圈权威** | ✅ 有 | [bangumi.github.io/api](https://bangumi.github.io/api/) | ✅ 公开读多数免 token | 有 429 限流🟡 | **中文最强** | 低 | ✅ `GET /v0/episodes` 实测字段：id, type, name, name_cn, sort, ep, airdate, comment, duration, desc, disc, duration_seconds —— **comment 是评论数，无任何逐集评分字段** |
| **Douban 电影** | 中文权威评分 | ❌ 官方已封闭 | — | — | — | 中文最强 | 高 | ✅ 实测 `api.douban.com/v2/movie/subject/*` → **403 invalid_apikey**；只能非官方接口/爬虫 |
| **SeriesGraph**（参考实现） | 逐集曲线标杆 | ❌ 无 API | [seriesgraph.com](https://seriesgraph.com/) | — | — | 动画/美剧 | — | 见 §七-1：**数据来自 IMDb 逐集评分**，证明"TMDB 一次调用 + 自绘曲线"可行 |

---

## 五、图书 / 漫画（Book / Manga）

| 来源 | 权威性说明 | 公开 API? | 文档 URL | 免费 key? | 速率限制 | 中日亚覆盖 | 接入难度 | 备注 |
|---|---|---|---|---|---|---|---|---|
| **Google Books** | 元数据 + 少量评分 | ✅ 有 | [developers.google.com/books/docs/v1/using](https://developers.google.com/books/docs/v1/using) | ⚠️ 可无 key（低配额）；带 key 默认 1,000/天 🟡 | 1,000 req/天 🟡 | 中（中文书可搜到） | 低 | 有 averageRating/ratingsCount，但极稀疏 |
| **Open Library** | 开放图书馆元数据 | ✅ 有 | [openlibrary.org/developers/api](https://openlibrary.org/developers/api) | ✅ 无需 key | Covers API 100 req/5min 🟡 | 弱 | 低 | 基本无评分 |
| **Goodreads** | 英文书评权威 | ❌ **API 已停** | — | — | — | 弱 | — | ✅ 2020-12-08 起停止发放新 key，老 key 30 天未用即失效 |
| **Douban 图书** | 中文书评权威 | ❌ 已封闭 | — | — | — | 中文最强 | 高 | 同电影 |
| **Bangumi 书籍** | 中文漫画/轻小说权威 | ✅ 有 | [bangumi.github.io/api](https://bangumi.github.io/api/) | ✅ | 429 限流🟡 | **中文漫画/轻小说最强** | 低 | `/v0/subjects` 支持 book 类型 |
| **MAL 漫画（经 Jikan）** | 日漫权威 | ✅ 有 | [docs.api.jikan.moe](https://docs.api.jikan.moe/) | ✅ 无需 key | 3/s、60/min ✅ | 日漫 | 低 | Jikan 有 `/manga/{id}`；**章节无评分** |
| **Amazon（PA-API）** | 销量/星级 | ✅ 有 | [webservices.amazon.com](https://webservices.amazon.com/paapi5/documentation/) | ❌ 需有销量才开通 | 1 req/s | 中 | 高 | 个人项目基本拿不到 |
| **Hardcover** | Goodreads 替代 | ✅ 有（GraphQL） | [hardcover.app](https://hardcover.app/) 🟡 | ✅ 免费 token | 未明示 | 弱 | 中 | 英文书备选 |

---

## 六、视觉小说（Visual Novel）

| 来源 | 权威性说明 | 公开 API? | 文档 URL | 免费 key? | 速率限制 | 中日亚覆盖 | 接入难度 | 备注 |
|---|---|---|---|---|---|---|---|---|
| **VNDB** | VN 界唯一权威 | ✅ 有 | [api.vndb.org/kana](https://api.vndb.org/kana) | ✅ **无需 key** ✅ | 200 req/5min ✅ | **日系 VN 全覆盖** | 低 | ✅ `rating`(贝叶斯)、`average`(原始均分)、`votecount`。Niriko 已接入，最稳 |
| **ErogameScape（批评空间）** | 日文 VN 评分权威（中央值） | ❌ 无 API | [erogamescape](https://erogamescape.dyndns.org/~ap2/ero/toukei_kaiseki/) | — | — | **日系最强（含旧作）** | 高 | HTML 统计表（中央値順/平均値順）；无 API、无授权数据集，**只能爬**；本机网络不可达未实测 |
| **Getchu** | 日本 VN 销售/评价 | ❌ 无 API | — | — | — | 日系 | 高 | 只有销量榜与用户评论 |
| **DLsite** | 销量榜/评分 | ❌ 无官方 API | — | — | — | 日系（含同人） | 高 | 仅非官方爬虫 |
| **Bangumi 游戏** | 中文 VN 评分权威 | ✅ 有 | [bangumi.github.io/api](https://bangumi.github.io/api/) | ✅ | 429 限流🟡 | **中文 VN 最强** | 低 | subject 类型 4 = 游戏 |

---

## 七、专项问题回答

### 1. SeriesGraph 是什么？逐集评分/趋势曲线怎么来的？

- **站点**：<https://seriesgraph.com/>（Web + Android + iOS，开发者 **Henri Elezi**，Google Play 5 万+ 下载、4.4 分）✅
- **呈现方式**：一剧一页（URL 形如 `/show/42509-steinsgate`，**42509 就是 TMDB ID**）：全局分 **8.8 (96,693)**、**每季逐集网格/热力图**（`Season 1 (avg 8.5)` + `E1 7.5 E2 7.5 … E24 9.6`）＝所谓"评分趋势曲线"，外加 **Top / Bottom Episodes** 排行、**Public Ratings** vs **Custom ratings**（自评）、**Taste Match**、**Wrapped**。
- **数据来源（实测判定）：IMDb 逐集评分，不是 TMDB。**
  - 证据：SeriesGraph 上 Steins;Gate `E22 9.7 / E24 9.6 / E23 9.5 / E16 9.4` 与 [IMDb 剧集页](https://www.imdb.com/title/tt1910272/episodes/) 的 **9.7 / 9.6 / …** 完全一致 ✅；而 TMDB 同季页第 1、2 集是 **69% / 68%**（≈6.9/6.8），与 SeriesGraph 的 7.5/7.5 **不符** ✅。
  - 图片与剧集元数据来自 **TMDB**（`image.tmdb.org`）✅。
  - 页面上另有 "Rated by Series Graph community"，说明**社区自评分**是叠加层。
- **开源仓库**：**没有官方开源仓库**。GitHub 搜索 "seriesgraph" 6 个结果中首个是同名但无关的 SVG 图表库 [inkorange/SeriesGraph](https://github.com/inkorange/SeriesGraph) ✅；未见该站点源码。
- **启示**：逐集曲线 Niriko 完全可以**自己用 TMDB 一个接口画**。

### 2. AniList / MAL 有没有逐集评分？

- **AniList：没有逐集评分。** GraphQL `Media` 只有 `episodes`（集数）、`meanScore`/`averageScore`（整部）、`streamingEpisodes`（元数据）；**schema 中没有 Episode 对象**🟡（[docs.anilist.co](https://docs.anilist.co/)，GraphQL 仅接受 POST，本机无法 GET 实测）。
- **MAL：需要更正——MAL 其实有逐集评分，但官方 API 不给。**
  - ✅ **实测** <https://myanimelist.net/anime/9253/Steins_Gate/episode> 的剧集表逐集都有 **"Vote average 4.2 / 4.3 … 4.8"** 及票数（E22 = 4.8/848 票，E24 = 4.7/1,919 票）。
  - ⚠️ 标尺是 **1–5 星**，不是 10 分制，混排必须换算。
  - 🟡 MAL **官方 API v2 没有 episode 端点**。
  - ✅ Jikan 自述 "It **scrapes** the website … which **MyAnimeList lacks**"，确有 `/anime/{id}/episodes`；但**实测当前 504**。
  - 想拿 MAL 逐集分**只能爬 MAL 剧集页**（社区先例：[Anime Stats / animestats.tf](https://www.animestats.tf/)，自述 "episode ratings **from MyAnimeList or IMDb**"，并做了 MAL 网页增强扩展来显示"真实逐集分数"，反证官方接口不提供）。

### 3. TMDB：逐集评分接口、限流、中文、IMDb 映射

- ✅ **逐集评分 = 季详情接口**：`GET /3/tv/{series_id}/season/{season_number}`，**一次调用返回整季 `episodes[]`**，每集含 `vote_average` / `vote_count`（官方示例：`{"episode_number":1,"vote_average":8.1,"vote_count":396}`）。**无需逐集请求**。
- ✅ 单集接口（按需）：`/3/tv/{series_id}/season/{n}/episode/{e}`，支持 `append_to_response`。
- ✅ **IMDb 映射**：整剧 `GET /3/tv/{id}/external_ids` → `imdb_id`（示例 tt0944947）、tvdb_id、wikidata_id；单集 `GET /3/tv/{id}/season/{n}/episode/{e}/external_ids` → `imdb_id`（示例 tt1480055）；反查 `GET /3/find/{external_id}?external_source=imdb_id`。
- ✅ **限流**：官方文档写明旧限流（40 req/10s）**已于 2019-12-16 取消**，现仅保留**约 40 req/s** 的防批量上限，遇 **429 请退避**。
- ✅ **鉴权**：v3 `api_key` 或 v4 `Authorization: Bearer <token>`（官方 OpenAPI 即定义 Bearer header）。
- ✅ **语言**：`language` 参数（示例 en-US / pt-BR），ISO-639-1 + ISO-3166-1 格式；**`zh-CN` / `zh-TW` 为 TMDB 支持值**🟡（文档未逐项列举，但 TMDB 全站提供简繁中文）。

### 4. 不花钱拿 IMDb 评分的路径

| 路径 | 可行性 | 说明 |
|---|---|---|
| **OMDb** | ✅ **最现实** | ✅ 免费 key **1,000 次/天**（官网明写 "FREE! (1,000 daily limit)"）；🟡 Patreon $1/月=10 万/天、$5/月=50 万/天。✅ 支持 `type=episode` 取**单集 IMDb 评分**。注意非 IMDb 官方、CC BY-NC 4.0（**不可商用**） |
| **IMDb 官方 bulk 数据集** | ⚠️ 数据免费但**移动端不可行** | ✅ `title.ratings.tsv.gz` + `title.episode.tsv.gz` join 可得出**全部逐集 IMDb 评分**（SeriesGraph 类做法）。**仅限个人/非商业**，每日全量重下 → 必须自建后端 ETL |
| **TMDB 逐集 vote_average** | ✅ **推荐替代** | 免费、一次调用给全季、有官方配额说明；缺点是**TMDB 用户分，不是 IMDb 分**（数值不同、趋势一致） |
| **爬 IMDb 网页** | ❌ 不推荐 | 明确禁止爬取，结构频繁变化 |
| RapidAPI 上的"IMDb API" | ❌ | 第三方代理，付费 + 随时失效 |

### 5. 日本动画**逐集收视率**（Video Research）

- ✅ **没有可用的逐集数据，也没有 API。** 实测 <https://www.videor.co.jp/tvrating/>：只发布**周榜**（関東地区），按类型分 ドラマ / バラエティ / スポーツ / **アニメ** / 音楽 / 映画 / 報道 / 教育･教養･実用，每行是"**番組平均** 個人視聴率 / 世帯視聴率"——**颗粒度是"节目"而非"某一集"**，页面并标注 **"無断転載禁止"**（禁止转载）。
- 商业产品（TV POP!、PMビューーン！）面向广告主付费，无公开接口。
- 结论：**Niriko 不要做日本电视收视率**；动画侧应用 **IMDb/TMDB 逐集评分**（SeriesGraph 模式）或 **MAL 1–5 星逐集票**。

---

## 八、推荐接入顺序（Solo Android Dev，零成本）

### 第一批：马上做（免费 + 无后端 + 移动端直连）
1. **TMDB** — 电影/剧集/动画骨架：免费 key、一次调用拿整季逐集 `vote_average`、`zh-CN`、`external_ids` 映射 IMDb。**逐集曲线的主引擎。**
2. **Bangumi** — 中文动画/书籍/游戏/音乐，免费，中文覆盖最好。
3. **AniList GraphQL** — 日系动画/漫画整体分，免 key、90 req/min。
4. **VNDB** — 已接入，VN 唯一评分来源，保持。
5. **iTunes Search API** — 音乐：免 key，日文歌/动画歌覆盖极好。
6. **Steam appdetails + appreviews** — 已接入：正评率 + `metacritic.score`。
7. **IGDB** — 游戏 critic 聚合 `aggregated_rating`，4 req/s，比 Steam 的 Metacritic 字段更规范。

### 第二批：值得加，但有局限
8. **OMDb**（1,000/天）— 用户想看"IMDb 评分"时补一层；**CC BY-NC 不可商用**，加缓存 + 失败降级 TMDB。
9. **Jikan** — MAL 总分/榜单；**当前 504，必须熔断 + 本地缓存**，不可作唯一来源。
10. **MusicBrainz / Last.fm** — 音乐元数据与热度，免 key。
11. **Trakt / Kitsu** — 社区分补充（可选）。

### 第三批：不建议
- **Metacritic / Famitsu / Oricon / Billboard / Douban / Goodreads / RYM / AOTY / Getchu / DLsite / ErogameScape**：无 API 或已封闭，只能爬，法律与稳定性风险高。
- **IMDb TSV 数据集**：最权威，但需自建后端每日 ETL；**等 Niriko 有服务端再说**。
- **MobyGames / Amazon PA-API**：付费或门槛过高。
- **日本电视收视率**：不可得。

### 关键取舍
- **逐集评分只用两条路**：TMDB 季接口（免费、合法、一次调用、有中文）+ OMDb 按 IMDb 单集 ID（用户要 IMDb 数字时才请求）。
- **MAL 逐集 1–5 星**只在要复刻 SeriesGraph 的 MAL 版时才爬，且**必须自建缓存层**；不要把爬虫放进 App 直连。
- **跨源展示必须标注来源与标尺**（IMDb 10 分 / TMDB 10 分 / MAL 5 星 / Bangumi 10 分 / VNDB 10 分 / IGDB 100 分），否则用户会混淆。
