# 共享简报 · Niriko 功能参考目录 v2（研究工作包）

> 本文件由 Lead 维护，所有分域研究员**先读它再动手**。你只需要写自己那 1–2 个域的文件，不要改别人的文件、不要改 v1 主表。

---

## 0. 本轮任务与产出

**背景**：docs/feature-reference-catalog.md（v1，174 条，1.1–12.12）是上一轮产物，只有「功能 / 说明 / 参考来源 / 缺在哪 / 怎么补」五列，且外部仓库一侧只到 README 级证据。本轮要：

1. **继续找**：扩充参考库池（新仓库、新语言、新范式），并**读源码**而非只看 README。
2. **细化表单**：把每条功能写成可执行的 7 字段详解（现状 / 提升 / 原理 / 效果 / 参考 / 成本风险 / 优先级）。
3. **发散**：主动提出本项目还没有、但应该有的功能，并给出参考实现。

**你的产出**：docs/feature-refs/{NN}-{slug}.md（只写分配给你的文件）。结构固定：

```
# {域号} {域名}

> 一句话结论：本域最值得做的 2–3 件事。（3–5 行）

## {域号}.0 汇总表
| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
（一行一条，覆盖本域全部条目，便于扫读）

## {域号}.1 逐条详解
### {域号}.{n} 功能名
- **现状**｜无/部分/待核｜一句话，带本项目文件路径
- **提升**｜…
- **原理**｜…
- **效果**｜…
- **参考**｜owner/repo@branch:path [码|README|推断] 关键符号
- **成本/风险**｜S/M/L｜一句话
- **优先级**｜P0/P1/P2

## {域号}.9 本域新增候选（发散）
（≥5 条，格式同上，**现状写「无 · 新提出」**）

## {域号}.A 本域新增参考库
| 仓库 | 许可 | star | 语言 | 为何与本项目相关 | 已验证文件 |
```

**编号规则**：v1 已有条目**编号与名称保持不变**（1.1、1.2 …），只在后面补字段；新提出的功能接在该域末尾（如 1.27、1.28 …）。明显重复的条目（例如 11.7 与 12.6 都是「焦点/方向键可达性」）**合并为一条**，并在标题后注「（合并自 12.6）」。

**篇幅**：每条 6–14 行；不要为凑字数重复项目事实。整个域文件 250–500 行是正常区间。

---

## 1. 项目事实（写之前必须与这些一致；与 v1 冲突时以代码为准并指出）

Niriko = com.otakup.niriko，**Kotlin + Jetpack Compose + Material 3 的 Android 单 Activity 应用**，纯客户端、无服务端、无 DI 框架（ViewModel 手写 Factory）。版 1.2.0，minSdk 26 / targetSdk 34 / compileSdk 37。Room **23 张表（version 32）**，显式迁移 v2→v32，app/schemas/ 需提交；DataStore Preference；1124 项纯 JVM 单测。依赖集中在 gradle/libs.versions.toml。

四个顶级页（HorizontalPager）：**作品库 / 发现 / 统计 / 设置**；二级页走 NavHost（navigation/NirikoNavHost.kt、ui/screens/Screens.kt）。

关键代码地图（**引用现状时尽量带真实路径**）：

- 数据源：data/remote/{BangumiClient,DataSource}.kt、plugin/DataSourceChain.kt、data/remote/{anilist,steam,vndb,tmdb,bilibili,douban,anitabi,game,rating}/
- 刷新：data/refresh/{RefreshCoordinator,FreshnessPolicy,RefreshKeys,RefreshStatusLabels}.kt
- 匹配：data/match/；搜索：data/search/{SearchQueryParser,TagAliasMap}.kt、ui/search/
- 统计纯函数：data/calculator/{StatsCalculator,ChartSeriesCalculator,RatingInsights,CalendarRangeCalculator,EpisodeRatingAnalyzer,TrendingCalculator,PagePager}.kt；图表在 ui/stats/{NirikoBarChart,NirikoDonutChart,NirikoRadarChart,TagWordCloud,TimelineSection,CalendarCard}.kt
  - **更正（Lead 2026-10-17 核实）**：图表**已用 Vico 3.2.0**（`gradle/libs.versions.toml:28,97`；NirikoBarChart/NirikoDonutChart/EpisodeRatingChart 均 import `com.patrykandpatrick.vico`），日历**已用 kizitonwose 2.10.1**（`libs.versions.toml:30,99`；CalendarCard 用 HorizontalCalendar/WeekCalendar）。**不要再把「自绘图表/自绘日历」当现状**；需要评估的是换库后的剩余问题（数据准确性、聚合能力、导出）。
- 收藏模型：data/model/{WatchStatus,WatchStatusVerb,WorkType,WorkFilter,CollectionStats}.kt、data/local/entity/{WorkItem,Collection,Episode,LibraryFolder}Entity.kt
- 同步/备份：data/sync/{SyncManager,SyncMergePolicy,WebDavClient}.kt、data/sync/bangumi/、data/backup/{BackupManager,RestoreMergePolicy,BackupFolderRestore}.kt（JSON v4，读 v1–v4）
- 导入：plugin/bilibili/、plugin/kazumi/、ui/steam/（Steam 游戏库）
- UI 基础件：ui/components/（GlassDecision.kt 玻璃降级真值表、ShimmerPolicy.kt、TopFadeOverlay.kt）；RevealOnScroll 在 ui/animation/；liquidglass/
- 主题/壁纸/主题包：ui/theme/、ui/wallpaper/、data/wallpaper/、data/themepack/
- 后台：data/notification/（WorkManager 放送提醒）、data/probe/（灰通道连通性自检）
- 设置：data/settings/{AppSettings,SettingsDataStore}.kt、ui/settings/pages/*.kt（11 个二级页）

**定位边界（v1 已确认，不要翻案除非单列「翻案建议」并说明理由变化）**：不做播放器/弹幕/下载/挂载媒体服务器；不做社区与内容生产（发帖/好友/私信/维基）；不做账号体系与自建服务端；不做 TV/D-Pad 形态；不做浏览器扩展与插件市场。

---

## 2. 网络工具链（**github.com 与 api.github.com 在本机被 DNS 黑洞，直连全部失败**）

已验证可用（2026-10）：

| 用途 | 方式 | 备注 |
| --- | --- | --- |
| 搜索/发现仓库 | run_code 里 web_search（firecrawl 通道） | platform_search(github)、domain_search(github_releases)、web_fetch **均不可用**，别浪费步数 |
| 读 GitHub 页面 | 不要 read_page(github.com)（被拦） | read_page(cdn.jsdelivr.net/...) 可以 |
| **列仓库文件树** | https://data.jsdelivr.com/v1/package/gh/{owner}/{repo}@{branch}/flat | 返回 JSON 的 files[].name，可能 300KB+，**必须过滤后再打印** |
| **读单文件** | https://gcore.jsdelivr.net/gh/{owner}/{repo}@{branch}/{path} | 200 正常；单文件上限约 20MB |
| star / license | https://img.shields.io/github/stars/{owner}/{repo}.json 与 .../license/{owner}/{repo}.json | 取 message 字段 |
| 备选 CDN | fastly.jsdelivr.net 会 301 不要用；cdn.jsdelivr.net 在 curl 下 301、read_page 下可用 | 首选 gcore |
| 分支名 | 先 main，失败再 master（或 dev） | data.jsdelivr.com/v1/packages/... 只给 tag 列表 |

run_code 里可以直接 await fetch(...)（有网）。片段：

```ts
const o = "mihonapp", rp = "mihon", br = "main";
const tree = await (await fetch(`https://data.jsdelivr.com/v1/package/gh/${o}/${rp}@${br}/flat`)).json();
const names = (tree.files ?? []).map((f) => f.name);
console.log(names.filter((n) => /track|sync|backup/i.test(n)).slice(0, 40));
const txt = await (await fetch(`https://gcore.jsdelivr.net/gh/${o}/${rp}@${br}/${p}`)).text();
console.log(txt.slice(0, 3000));
```

**省 token 的规矩**：只打印你要的那几行；大文件先 slice；目录列表先过滤再打印。一次 run_code 可以 Promise.all 并发拉多个 URL。

---

## 3. 证据规范（本轮核心卖点：从「README 快照」升级到「可复现的源码证据」）

- [码]：你真读过该文件，能说出关键符号名（类/函数/枚举值）。**必须给完整 owner/repo@branch:path**。
- [README]：只读到 README / 官方文档 / Release Notes，没读源码。要注明。
- [推断]：从截断信息、二手描述或常识推的，**必须显式标注**，不许伪装成事实。
- **禁止**编造 star 数、函数名、行号、文件路径。取不到 star 就写「未取到」。
- 路径必须能对上：写进文档前用目录列表或直接 fetch 确认存在。
- 不要写「据称」「可能支持」这种既非事实也非推断的句子 —— 要么验证，要么标 [推断]。

---

## 4. 字段怎么写（这是「细化表单」，写不好等于没做）

- **现状**：一句话说清 data/ 里现在到底有什么（或没有什么），带路径。宁可写「未见相关实现，data/sync/SyncManager.kt 只有全量上传」，也不要写「待完善」。
- **提升**：说清两件事——(a) 用户能感知到什么变化；(b) 工程上省掉什么（少一次请求 / 少一张表 / 少一个特判）。不要写「提升体验」「增强健壮性」这种空气话。
- **原理**：**怎么做到**。要具体到数据结构、状态机、算法、协议、key 设计、并发模型、UI 手势链路。读者应该能照着它直接写出类图或迁移计划。允许出现代码片段（3–10 行）与公式。
- **效果**：**实现后用户看到什么**。界面元素、文案、交互反馈、数字变化、边界行为（空态/失败态/降级）。不要重复「提升」。
- **参考**：仓库 + 路径 + 关键符号 + 证据等级。一个功能可以给 1–3 个参考（不同语言/不同范式更有说服力）。
- **成本/风险**：S（≤1 天，纯函数或单文件）/ M（3–5 天，动一两层）/ L（≥1 周，动表或动架构）+ 一句最可能翻车的点。
- **优先级**：P0（本域先做）/ P1（排得进计划）/ P2（记录待定）。

**「发散」条目的额外要求**：每条都要回答「为什么是 Niriko 需要，而不是别家需要」——与本项目的纯客户端、Bangumi 主源、玻璃 UI、1124 单测、无 DI 这些约束挂钩。

---

## 5. 参考库池

### 5.1 v1 已有（R1–R16，可继续引用，但**要补路径**）
R1 open-ani/animeko（Kotlin/AGPL，能力声明+多源选择+自检）· R2 Kyant0/AndroidLiquidGlass（玻璃上游）· R3 xiaoyvyv/bangumi（KMP 移植）· R4 xiaoyvyv/Bangumi-for-Android · R5 chrisbanes/haze · R6 skydoves/Cloudy · R7 AieXile/AnimeTrack · R8 ewiwo/GameArchive（应为 ewiwo/GameArchive，注意核对）· R9 NyanTV/NyanTV · R10 anitabi/anitabi.cn-document · R11 FuzzyGrim/Yamtrack + dannyvfilms/Floppy · R12 EuDs63/steam2record · R13 AimesSoft/NipaPlay-Reload · R14 edwardez/BangumiN（归档）· R15 thinktraveller/Anitabi_MCPserver · R16 Luis-Herry/anime-tracker-desktop

### 5.2 本轮建议优先验证的新候选（按域挑，不一定全用；可自行再找）
- **追踪器范式**：IgnisDa/ryot（Rust+React，跨类型追踪、日历、统计、集成）
- **漫画库管理**：mihonapp/mihon（Tachiyomi 继任：库更新、分类、tracking services、备份格式）· KotatsuApp/Kotatsu（CMP、收藏/历史/推荐）· aniyomi/aniyomi
- **游戏库**：JosefNemec/Playnite（完成状态、游玩时长、插件元数据、主题、备份/导入）
- **阅读**：bookwyrm-social/bookwyrm（书架/进度/导入 Goodreads）· Kareadita/Kavita（阅读进度/合集/阅读列表）
- **音乐**：navidrome/navidrome（评分/收藏/播放计数/智能歌单）
- **统计图表**：patrykandpatrick/vico（Compose 原生图表，替换自绘）· kizitonwose/Calendar（Compose 日历，替换自绘日历）
- **搜索**：~~javafun/TinyPinyin~~（404）真实仓库是 **promeg/TinyPinyin**（3.9k, Apache-2.0）。**但注意**：本项目已评估过并放弃（`gradle/libs.versions.toml:85`：仅在 JitPack、镜像不可达，改用 Maven Central 的 pinyin4j）。再提它必须给「vendor 源码」或 Maven Central 制品路径，否则不可行。
- **本地优先同步**：actualbudget/actual（CRDT/合并模型）· automerge/automerge · logseq/logseq（离线优先+冲突）
- **备份保留策略**：kopia/kopia、restic/restic（快照+保留策略）
- **导入导出**：MAL/AniList 导出格式（XML/JSON 规范）· bangumi/scripts
- **Compose 工程范式**：androidx/compose-samples（Jetsocial/Reply 的自适应与状态建模）

### 5.3 已知不收录
zubinxiong/Bgm38、PhantomLGZ/BangumiNote、limichange/bangumi-client、Chesire/Nekome、Marco-9456/AniSync（均证据不足，v1 附录 D）。若要翻案必须给源码级证据。

---

## 6. 已有文档（避免重复造轮子）

- docs/feature-reference-catalog.md：v1 主表，你是它的明细展开，**不要改它**。
- docs/reference-repos-plan.md：讲**工程实现**（数据源抽象、刷新编排、玻璃降级、移植分层）与波次排期。你写「功能面」，可引用它的结论，不要复述。
- docs/bangumi-reference-plan.md、docs/anishelf-reference-plan.md、docs/portal-feature-plan.md：已分别对标 czy0729/Bangumi、AniShelf 与其门户能力，重复内容一句话带过。
- docs/performance-audit.md、docs/phone-shared-motion-profile.md：性能与动效的实机观测，9/12 域要引用而不是重测。

---

## 7. 交付前自检（写完必做）

1. 每条的「参考」路径都真的 fetch 过（或目录列表里有）？
2. 有没有把「部分」写成「无」、把「已实现」写成「缺失」？不确定就标 待核 并说明查哪里。
3. 有没有 air 话（「提升体验」「优化性能」）？全部换成可指的界面/数据变化。
4. 汇总表条数 = 详解条目数 + 新增候选数？（自己数一遍写进文件末尾）
5. 引用 star 数是否标了取数日期？（写「截至 2026-10」）