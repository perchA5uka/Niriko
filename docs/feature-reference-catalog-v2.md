# Niriko 功能参考目录 v2 · 源码级证据版

> 本文是 `docs/feature-reference-catalog.md`（v1，174 条）的**续篇与升级**，不是它的替代品。
> v1 保留原样（含 57 条已实现项与 59 条不纳入项的附录）；本文做三件 v1 没做的事：
> **① 换血参考池**（v1 的 16 个仓库扩到 36 个，并按可信度重排）**② 细化表单**（每条功能从 5 列变 7 字段，含原理与实现效果）**③ 主动发散**（提出 v1 完全没有、但本项目应该有的功能）。
>
> 分域详解在 `docs/feature-refs/`：每域一个文件，编号与 v1 一一对应，新条目接续编号。
> **不看技术的话，读 [`docs/feature-summary-for-users.md`](feature-summary-for-users.md)** —— 按「屏幕」整理的人话版：你会看到什么、在哪看、有什么用。
> **界面原型：[`docs/round-ui-prototype.html`](round-ui-prototype.html)** —— 17 屏静态原型，覆盖本轮所有会改界面的点（深色 360×720 手机框，纯 CSS 模拟）。
> 本轮研究规范（怎么取证、字段怎么填）见 `docs/feature-refs/_brief.md`。

---

## 0. 摘要

### 0.1 三个判断

**判断一：v1 的参考池需要换血，不是补充。**
v1 把 R7 `AieXile/AnimeTrack`（**18 star**）、R8 `ewiro/GameArchive`（**7 star**）、R9 `NyanTV/NyanTV`（**111 star**）、R16 `Luis-Herry/anime-tracker-desktop`（**2 star**，且本轮复查时该名已 404、疑似改名 `bandi`）当作「功能对标基准」。star 数不等于质量，但**这四个仓库几乎没有社区校验**：README 承诺与代码实际行为是否一致、某个功能是否真做完，都无法由生态背书。v1 因此给它们留了大量「待核」。
本轮换成能被源码验证的参考：`mihonapp/mihon`（24.5k，库管理与备份的工业级实现）、`open-ani/animeko`（21.0k，同技术栈的多源调度）、`JosefNemec/Playnite`（14.2k，游戏库的完成度状态与插件元数据）、`IgnisDa/ryot`（3.6k，跨类型追踪的统计口径）、`actualbudget/actual`（29.6k，本地优先与冲突合并）、`kopia/kopia`（14.4k，快照保留策略）。v1 的四家小仓库继续保留在表里，但**降级为「功能灵感」而不是「实现依据」**。

**判断二：本项目最大的问题不是「功能少」，而是三处底座没有收敛。**

| 底座 | 现状 | 为什么它是底座 |
| --- | --- | --- |
| 待同步队列 | 本地优先写入，但没有「离线写 → 联网补交」的队列（v1 §1.3） | 所有同步、导入、冲突功能都建立在它之上；没有它，每个新写入口都要单独处理失败 |
| 任意源 × 全能力自检 | 只有「搜索三通路」诊断与豆瓣灰通道探针（`data/probe/`） | 加源速度取决于「新源坏没坏」能不能 10 秒看清；也决定 `data/refresh/` 的失败信息是否可读 |
| 筛选 → 统计的统一入参 | **完全不随筛选**（v1 判「部分」不成立：`StatsViewModel` 的 combine 里没有筛选入参） | 统计页已有 10 个图表，每加一个就多一处口径漂移；这是「统计准确」的根 |

**判断三：本轮找到一条没人做的方向 —— 用 Bangumi Archive 做本地元数据快照。**
`bangumi/Archive` 定期导出 wiki 原始数据（Subject 含 id/type/name/name_cn/infobox/platform/summary/nsfw/date/tags/score/score_details/rank，另有人物、角色、章节），官方定位就是「给不需要实时数据的场景用、顺便减少爬虫」。本项目的核心痛点是「断网就只剩已缓存详情」，而 Archive 正好能把「搜索 / 榜单 / 标签 / 评分」搬到本地。详见 §4 的 C-2。

### 0.2 数字

| 项目 | v1 | v2 |
| --- | --- | --- |
| 参考仓库 | 16 | **40**（16 个保留 + 24 个新增，全部核过 star / 许可 / 默认分支） |
| 条目字段 | 5 列 | **7 字段**（现状 / 提升 / 原理 / 效果 / 参考 / 成本风险 / 优先级） |
| 证据等级 | README 快照为主 | `[码]` / `[README]` / `[推断]` 三级，逐条标注 |
| 覆盖条目 | 174（单文件） | **258** = 11 个域 238（v1 的 173 + 新增 65）+ 参考项目域 10 + 本文跨域 10 |
| 新增条目 | 0 | **85**（各域 75 + 跨域 10，其中 2 条为「不做/冲突」） |
| **v1.2.1 范围** | — | **34 条**（7 修复 + 27 新功能）；已撤回 3.11 空结果引导、13.5 首启自检。排期见 `docs/release-v1.2.1-plan.md` |
| 引用可核性 | 无 | `scripts/verify-refs.mjs` 全量校验：**108 条引用，0 异常** |
| 分域详解 | 458 行 | **12 个文件 4,700+ 行**，见 §3 索引 |

---

## 1. 本轮方法（可复现）

### 1.1 环境限制与绕过

本机 `github.com` 与 `api.github.com` 被 DNS 黑洞（直连返回 000）。v1 因此只能靠检索快照，大量条目带「待核」。本轮找到了一条**能读到真实源码**的通路，全部只读、无需密钥：

| 用途 | 端点 |
| --- | --- |
| 列仓库文件树 | `https://data.jsdelivr.com/v1/package/gh/{owner}/{repo}@{branch}/flat` |
| 读单文件 | `https://gcore.jsdelivr.net/gh/{owner}/{repo}@{branch}/{path}` |
| 仓库元数据（star / 默认分支 / 最近推送） | `https://ungh.cc/repos/{owner}/{repo}` |
| 许可与 star 徽章 | `https://img.shields.io/github/{stars,license}/{owner}/{repo}.json` |
| 仓库存在性/分支探测 | 先 `@main` 再 `@master`（部分仓库是 `devel` / `dev` / `develop` / `kmp` / `latest`） |

**这条通路的方法论意义**：从这一轮起，「某个功能在参考仓库里到底怎么实现的」不再靠猜。本文里凡是标 `[码]` 的条目，作者都真的拉过那个文件、能说出类名或函数名。

**仍然取不到的**：Issue / PR / 提交历史（需要 GitHub API）；发布产物体积；CI 配置的运行结果。涉及这些的结论一律标 `[推断]` 或不写。

### 1.2 证据三级

- `[码]`：读过源码文件，能引用关键符号（类 / 函数 / 枚举 / 常量）。
- `[README]`：只读到 README、官方文档或 Release Notes。
- `[推断]`：由截断信息、项目常识或二手描述推导，**不可当事实使用**。

分域文件里每条功能的「参考」字段都带这三档之一；Lead 在合并时对 `[码]` 条目做了抽样复核（见 §6.3）。

### 1.3 本轮对 v1 的两处勘误

1. **v1 的 R3 与 R4 现在指向同一个仓库。** `xiaoyvyv/Bangumi-for-Android` 已重定向到 `xiaoyvyv/bangumi`（同一 star 数 1,145、同一描述、同一推送日）。它们是「CMP 版」与「纯 Android 前身」的历史关系，引用时不要当成两个独立参照。
2. **`javafun/TinyPinyin` 不存在**（404）。真实仓库是 `promeg/TinyPinyin`（3,946 star，Apache-2.0）。但**不要顺手建议「换成它」**：`gradle/libs.versions.toml:85` 已记录项目评估过并放弃（仅在 JitPack、镜像不可达）。要提就必须给「vendor 源码」或 Maven Central 制品路径。

---

## 2. 参考库总表

star 与活跃度取自 2026-10-17；许可读的是仓库里的 LICENSE 文件首段（不是 GitHub 标注）。

### 2.1 v1 的 16 个（保留，重估可信度）

| 编号 | 仓库 | 语言 / 许可 | star | 活跃 | 本轮定位 |
| --- | --- | --- | --- | --- | --- |
| R1 | [open-ani/animeko](https://github.com/open-ani/animeko) | Kotlin / AGPL-3.0 | 21.0k | 2026-10-17 | **主参照**：同技术栈的多源调度、能力声明、源自检 |
| R2 | [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Kotlin / Apache-2.0 | 4.2k | 2026-08-26 | 玻璃**上游**（默认分支 `kmp`，已到 2.x） |
| R3 | [xiaoyvyv/bangumi](https://github.com/xiaoyvyv/bangumi) | Kotlin / GPL-3.0 | 1.1k | 2026-10-11 | CMP 移植参照（R4 已重定向到这里，视为同一仓库） |
| R4 | ~~xiaoyvyv/Bangumi-for-Android~~ | — | （同上） | — | **并入 R3** |
| R5 | [chrisbanes/haze](https://github.com/chrisbanes/haze) | Kotlin / Apache-2.0 | 2.6k | 2026-10-16 | 模糊分层与降级的标准做法 |
| R6 | [skydoves/Cloudy](https://github.com/skydoves/Cloudy) | Kotlin / Apache-2.0 | 1.3k | 2026-09-13 | GPU / CPU 双通道降级范式 |
| R7 | [AieXile/AnimeTrack](https://github.com/AieXile/AnimeTrack) | Kotlin | **18** | 2026-10-14 | 降级为「功能灵感」，不作实现依据 |
| R8 | [ewiro/GameArchive](https://github.com/ewiro/GameArchive) | Kotlin | **7** | 2026-09-09 | 同上 |
| R9 | [NyanTV/NyanTV](https://github.com/NyanTV/NyanTV) | Kotlin / AGPL-3.0 | **111** | 2026-06-28 | 同上（多服务统一收藏模型仍可参考） |
| R10 | [anitabi/anitabi.cn-document](https://github.com/anitabi/anitabi.cn-document) | 文档 | 688 | 2026-08-09 | 巡礼 API 契约 |
| R11 | [FuzzyGrim/Yamtrack](https://github.com/FuzzyGrim/Yamtrack) | Python / AGPL-3.0 | 3.7k | 2026-10-17 | 跨类型状态机、统计口径（v1 一起引用的 Floppy 已单列为 N20） |
| R12 | [EuDs63/steam2record](https://github.com/EuDs63/steam2record) | Python | 6 | 2024-03-05 | Steam → 收藏的**最小实现**，仅看边界处理 |
| R13 | [AimesSoft/NipaPlay-Reload](https://github.com/AimesSoft/NipaPlay-Reload) | 多语言 | 1.8k | 2026-10-17 | 播放场景下的进度写回建模 |
| R14 | [edwardez/BangumiN](https://github.com/edwardez/BangumiN) | Kotlin | 75 | 2023-04-23（归档） | 只读历史参照 |
| R15 | [thinktraveller/Anitabi_MCPserver](https://github.com/thinktraveller/Anitabi_MCPserver) | Python | **0** | 2026-03-23 | 降级：仅「源+自检」思路 |
| R16 | [Luis-Herry/anime-tracker-desktop](https://github.com/Luis-Herry/anime-tracker-desktop) → 疑为 `Luis-Herry/bandi` | Kotlin | 2（复查时无法复取） | 2026-10-12 | 降级：只看「一周预告 / 漏看提醒」的交互；**仓库疑似改名 `bandi`**（两个名字在 jsdelivr 都返回「包超 50MB」而非 404，说明存在且重定向），star 因 ungh 限流未能复取 |

### 2.2 本轮新增的 24 个

| 编号 | 仓库 | 语言 / 许可 | star | 本轮用途 | 已核实 |
| --- | --- | --- | --- | --- | --- |
| N1 | [mihonapp/mihon](https://github.com/mihonapp/mihon) | Kotlin / Apache-2.0 | 24.5k | 库管理、**按类别拆分的备份器**、tracking 抽象、库批量更新 | 分支 main；`data/backup/create/creators/*`、`domain/track/service/*` |
| N2 | [IgnisDa/ryot](https://github.com/IgnisDa/ryot) | Rust+TS / GPL-3.0 | 3.6k | 跨类型追踪的状态/日期字段、活动日历、统计口径 | 分支 main；README 功能面 |
| N3 | [JosefNemec/Playnite](https://github.com/JosefNemec/Playnite) | C# / MIT | 14.2k | 游戏库：完成度状态、游玩时长、插件元数据、主题、备份导入 | 分支 master；LICENSE |
| N4 | [KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu) | Kotlin / GPL-3.0 | 8.9k | CMP 的收藏/历史/推荐与多站点源 | 分支 **devel** |
| N5 | [navidrome/navidrome](https://github.com/navidrome/navidrome) | Go / GPL-3.0 | 24.2k | 评分/收藏/播放计数/智能歌单的计数口径 | 分支 master |
| N6 | [Kareadita/Kavita](https://github.com/Kareadita/Kavita) | C# / GPL-3.0 | 11.9k | 阅读进度、合集与阅读列表 | 分支 **develop** |
| N7 | [bookwyrm-social/bookwyrm](https://github.com/bookwyrm-social/bookwyrm) | Python / Anti-Capitalist 1.4 | 2.8k | 书架、进度、从 Goodreads 导入 | 分支 main |
| N8 | [actualbudget/actual](https://github.com/actualbudget/actual) | TS / MIT | 29.6k | **本地优先 + 冲突合并**的工业实现 | 分支 master |
| N9 | [automerge/automerge](https://github.com/automerge/automerge) | Rust+JS / MIT | 6.7k | CRDT 合并模型（备选，不一定要上） | 分支 main |
| N10 | [kopia/kopia](https://github.com/kopia/kopia) | Go / Apache-2.0 | 14.4k | 快照 + **保留策略**（keep-last / keep-daily） | 分支 master |
| N11 | [restic/restic](https://github.com/restic/restic) | Go / BSD-2 | 36.6k | `forget --keep-*` 语义、快照校验 | 分支 master |
| N12 | [patrykandpatrick/vico](https://github.com/patrykandpatrick/vico) | Kotlin / Apache-2.0 | 3.2k | Compose 图表——**本轮确认项目已在用 3.2.0**，不再评估换库，改看剩余问题（marker、入场动画、胶囊图缺失） | 分支 master |
| N13 | [kizitonwose/Calendar](https://github.com/kizitonwose/Calendar) | Kotlin / MIT | 5.6k | Compose 日历——**已在用 2.10.1**；`HeatMapCalendar` 可零依赖补 5.21 | 分支 main |
| N14 | [promeg/TinyPinyin](https://github.com/promeg/TinyPinyin) | Java / Apache-2.0 | 3.9k | 轻量拼音（替代 pinyin4j） | 分支 master；`lib/.../pinyinhelper/*` |
| N15 | [bangumi/Archive](https://github.com/bangumi/Archive) | 数据 | 356 | **离线元数据快照**（Subject/Person/Character/Episode 定期导出） | 分支 master；README 字段表 |
| N16 | [logseq/logseq](https://github.com/logseq/logseq) | Clojure / AGPL-3.0 | 45.3k | 离线优先 + 冲突提示的产品化（只借鉴交互） | 分支 master |
| N17 | [takahirom/roborazzi](https://github.com/takahirom/roborazzi) | Kotlin / Apache-2.0 | 1.1k | JVM 上的 Compose 截图回归 | 分支 main |
| N18 | [cashapp/paparazzi](https://github.com/cashapp/paparazzi) | Kotlin / Apache-2.0 | 2.6k | 同上（备选） | 分支 master |
| N19 | [androidx/androidx](https://github.com/androidx/androidx) | Kotlin / Apache-2.0 | 6.1k | Glance（桌面小组件）与 benchmark（性能）的源码位置 | 分支 **androidx-main** |
| N20 | [dannyvfilms/Floppy](https://github.com/dannyvfilms/Floppy) | Python / AGPL-3.0 | 876 | Yamtrack 的继任/分支，看迁移与导入演化 | 分支 **latest** |
| N21 | [XxkumaXia/douban-bangumi-sync](https://github.com/XxkumaXia/douban-bangumi-sync) | JS / **MIT** | 未取到 | 同步「扫描→差异→勾选→演练→执行」工作区（13.2） | 分支 main，59 个文件可列；只读 README |
| N22 | [GrahamZen/izuko-tv](https://github.com/GrahamZen/izuko-tv) | Kotlin / **AGPL-3.0** | 578 | 标题 logo 五件套（13.1）、应用内更新、首启自检 | 仓库 >50MB 无文件树；五件套由 `reference-projects-plan.md` 读过 |
| N23 | [feitangyuan/onetake](https://github.com/feitangyuan/onetake) | JS / **PolyForm NC** | 未取到 | 一镜到底发布片产线 + 动效判据（13.7/13.9） | 仓库 >50MB；只读 README |
| N24 | [feitangyuan/motion-web](https://github.com/feitangyuan/motion-web) | JS / **CC BY-NC 4.0** | 744 | 反塑料感动效清单 + 四个特效案例（13.7/13.8） | 分支 main，84 个文件；只读 README |

> 注：`androidx/glance`、`androidx/benchmark`、`androidx/compose-samples` **不是独立可列目录的仓库**（在 `androidx/androidx` 与官方文档里）。v1 若把它们当独立仓库引用，须改。

### 2.3 许可边界（本项目的许可风险）

Niriko 是 **MIT**，参考池却不是同一种许可。读范式与拷代码是两件事：

| 许可 | 本轮池中的仓库 | 能做什么 | 不能做什么 |
| --- | --- | --- | --- |
| **AGPL-3.0** | animeko、Yamtrack、Floppy、logseq、NyanTV | 读架构、读状态机、读协议设计 | 不能拷任何代码；派生作品会被传染 |
| **GPL-3.0** | ryot、Kotatsu、Kavita、navidrome、bangumi(CMP) | 同上（只抄思路） | 同上 |
| **Apache-2.0** | mihon、vico、haze、Cloudy、kopia、roborazzi、paparazzi、androidx、AndroidLiquidGlass、TinyPinyin | 可参考实现细节，必要时可移植**带声明**的代码 | 需保留 NOTICE / 变更说明 |
| **MIT / BSD** | Playnite、automerge、actual、restic、kizitonwose/Calendar | 最宽松 | 保留版权声明 |
| **Anti-Capitalist 1.4** | bookwyrm | 只读 | 条款非标准，不建议移植 |

三条实操规则：

1. **数据模型可以参考，代码不要跨许可拷贝。** 本文所有「原理」都是**重新表述**，没有搬运源码；真要照抄某个函数，只从 Apache-2.0 / MIT 的仓库拿，并在 THIRD-PARTY-NOTICES.md 里登记。
2. **已经拷进仓库的只有 Kyant0 demo（Apache-2.0）**，见 v1 §10.2/§10.3；那是允许的，但必须保留 provenance（版本 + 提交指纹），这正是 10.3 要解决的问题。
3. **libs.versions.toml 里声明但未调用的依赖要清掉**（refs-visual 发现 cloudy 就是这种情况）—— 未使用的依赖同样会出现在「关于 → 开源许可」里，让用户误以为用到了。
---

## 3. 分域索引

| 域 | 文件 | v1 条目 | 本轮新增 | 本域最该先做的 |
| --- | --- | --- | --- | --- |
| 1 收藏与进度 | `docs/feature-refs/01-collection.md` | 26 | 6 | **1.3 待同步队列**；1.13+1.14+1.24 合并成一张 `collection_readthrough` 表；1.18 时间戳（字段有、UI 没画） |
| 2 数据源与聚合 | `docs/feature-refs/02-sources.md` | 18 | 5 | **2.1 统一能力声明**（主链 10 个 boolean vs 游戏链 5 个，链上只检查 4 个）；**2.3 源顺序持久化**（切了不生效）；2.5 全能力自检 |
| 3 发现与检索 | `docs/feature-refs/03-discovery.md` | 17 | 4（3.11 撤回） | 3.1 建议链路耗时与稳定排序；3.9 筛选预设；3.3–3.5 合并成 `Recommendation(reasons)`；榜单箭头 |
| 4 日历、放送与提醒 | `docs/feature-refs/04-calendar.md` | 14 | 6 | **4.3/4.4/4.5 纯函数聚合**（零新请求）+ **4.8 .ics**；4.7 停播必须先于 4.8；4.15 时区 |
| 5 统计与可视化 | `docs/feature-refs/05-stats.md` | 20 | 6 | **5.1+5.2 `StatsQuery` 统一入参**（现状其实完全不随筛选）+ **5.6/5.19 观看量分流**（游戏分钟被当集数） |
| 6 详情与元数据 | `docs/feature-refs/06-detail.md` | 17 | 7 | **6.7 详情离线完整性**（`DetailCacheStore` 只用了 4 个 key）；6.20 区块深链验证 TAB 需求 |
| 7 同步、导入与备份 | `docs/feature-refs/07-sync.md` | 16 | 6 | **7.17 乐观并发（ETag/If-Match）**；7.11 卷进度进同步；7.20 备份 dry-run |
| 8 社区与社会化 | `docs/feature-refs/08-community.md` | 4 | 3 | 只有 **8.4 可分享清单** 值得做；其余 3 条固化为「不做」并给了架构级理由 |
| 9 性能 + 11 平台能力 | `docs/feature-refs/09-11-platform.md` | 13 | 9 | **9.2 macrobenchmark 进 CI**；**11.1 Glance 小组件**；11.7 合并了 12.6 |
| 10 个性化与外观 | `docs/feature-refs/10-personalization.md` | 17 | 6 | **10.1+10.4 玻璃降级 = 设备能力 × 用户偏好**；10.2/10.3 锁 1.0.6 的判据固化；10.17 设置搜索 |
| 12 交互与动效 | `docs/feature-refs/12-motion.md` | 11（12.6 并入 11.7） | 6 | 修 **12.2 错峰入场失效**；补 6 处 reduceMotion 漏网；清理 `TopFadeOverlay` 死代码 |
| 13 参考项目对照 | `docs/feature-refs/13-reference-projects.md` | — | 9（13.5 撤回） | **13.1 标题 logo（P0）**、13.2 同步演练、13.4 应用内更新（源自 `reference-projects-plan.md`） |
| **跨域（本文 §4）** | 本文档 | — | **10** | C-1 数据体检 / C-2 Archive 离线快照 / C-3 快照回滚 / C-4 截图回归 |

---

## 4. 跨域新增功能（Lead 原创 · 不属于任何单一域）

> 这 10 条是「把项目当作一个产品整体」看出来的缺口：它们都横跨两个以上域，所以 v1 的分域列举方式天然漏掉了它们。每条同样 7 字段。

### C-1 「数据体检」Data Doctor
- **现状**｜无｜`ui/settings/pages/RefreshDiagnosticsScreen.kt` 只看**远端源**的新鲜度；本地库自身的一致性（重复条目、孤儿绑定、坏封面、缓存膨胀）没有任何检查入口。
- **提升**｜用户能在 30 秒内回答「我的库坏了没有、坏在哪、能不能修」；工程上把散落在 `data/backup/RestoreMergePolicy.kt`、`data/match/`、各个 DAO 里的兜底判断收敛成一组可测纯函数，顺带补齐 1124 单测里唯一缺失的一类（「数据自身」测试）。
- **原理**｜`interface HealthCheck { val id: String; suspend fun run(s: HealthSnapshot): List<Finding> }`，`Finding(code, severity, subjectIds, message, fix: FixAction?)`。检查项建议 8 个：①同一作品被两个 `sourceKey` 表示（Bangumi 词条 + `steam:` 条目并存）；②绑定表孤儿行（`steam_bindings` 指向已删 subject）；③封面 URL 探测失败（HEAD，并发 ≤4，按源限流）；④`subject_detail_cache` 体积 > 阈值；⑤`library_folder_subject` 指向不存在的条目；⑥备份 dry-run（解析 `BackupManager` 产物但不写入）；⑦评分越界（<0 或 >10，含单集分）；⑧剧集序号断裂（`episodes` sort 不连续）。修复一律先落「快照」（见 C-3）再执行。
- **效果**｜设置 → 数据与备份 → 体检：`7 项检查 · 2 项发现 · 可修复 2 项`；点进去逐条列 subjectId + 封面缩略图；修完自动复跑，全绿给一次静态确认（复用「一次性反馈」）。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupFileValidator.kt` `[码]` —— 把「文件/数据能不能用」抽成独立校验器而不是散在恢复流程里；`kopia/kopia@master` 的 `snapshot verify` 分层校验 `[README]`。
- **成本/风险**｜M｜封面探测会打外网，默认必须关闭并走源级闸门，否则重蹈 v1 §2.9「票数门槛在本地过滤」的体验坑。
- **优先级**｜P0

### C-2 本地元数据快照（Bangumi Archive → 离线秒搜）
- **现状**｜无｜所有词条靠实时 API；断网时只有 `subject_detail_cache` / `subject_relation_cache` 命中的详情可用，**搜索、榜单、标签筛选全部失效**。
- **提升**｜(a) 用户：断网也能搜全站条目名、按标签/评分筛、看榜单；(b) 工程：把 Bangumi 从「必须在线的主源」降级为「可被本地快照满足的主源」，直接压缩 §2.14 / §6.7 的失败面，也让 5.x 统计在离线时不再缺分母。
- **原理**｜`bangumi/Archive` 定期导出 wiki 原始数据，Subject 字段为 `id / type / name / name_cn / infobox / platform / summary / nsfw / date / tags(部分) / score / score_details / rank`，另有人物、角色、章节三类。落地：①新增 Room 表 `subject_index(id, type, name, name_cn, platform, date, score, rank, nsfw)` + FTS4 外部内容表（`name,name_cn,tags`），并复用 `util/PinyinSearch.kt` 的键生成逻辑建拼音列；②导入器流式解析 dump（逐行 → 每 500 行一个事务），写入后把 `archiveVersion` 存 DataStore；③查询链路：FTS 先出卡片（本地），点进详情再走 API 覆盖；④增量：按 `id` upsert，删除仅对「上一版存在、本版不存在」的 id 做软删。
- **效果**｜搜索页顶部出现 `本地索引 · 2026-10` 角标；飞行模式下搜索仍有结果并标注「离线索引，可能滞后」；冷启动首屏从「等网络」变成「200ms 出本地结果 + 远端补齐」。
- **参考**｜`bangumi/Archive@master:README.md` `[码]`（字段表已逐条核对）；SQLite FTS5 官方文档 `[README]`。
- **成本/风险**｜L｜先测 dump 体积再决定是否全量（可只导 Subject）；`tags` 字段官方标注为「部分」，必须声明覆盖度，不能让用户以为本地标签是全集。
- **优先级**｜**P1（战略价值最高的一条）**

### C-3 库级「时间机器」：同步前自动快照 + 可回滚
- **现状**｜部分｜`data/sync/SyncManager.kt` 走 WebDAV LWW 合并、`data/backup/BackupManager.kt` 是全量 JSON；**没有「同步/导入前的自动回退点」**。一次错误的合并目前只能靠上一次手动备份。
- **提升**｜(a) 用户：误覆盖后可一键回到 10 分钟前；(b) 工程：把「危险操作前先落盘」变成统一约定，7.x 的冲突处理（§7.4 / §7.12）才有兜底。
- **原理**｜`LocalSnapshot`：把 `collections / subjects 的收藏相关列 / episodes / library_folder(_subject)` 序列化为 gzip JSON，写 app 私有目录 `snapshots/{yyyyMMdd-HHmmss}.json.gz`；触发点 = 同步前 / 导入前 / 批量操作前 / 手动；保留策略照 kopia `keep-latest N + keep-daily 7 + keep-weekly 4`（去重靠内容 hash）；恢复 = dry-run 生成 diff（新增/更新/删除计数）→ 用户确认 → 覆盖；实现上复用 `RestoreMergePolicy` 的「合并 vs 覆盖」二选一。
- **效果**｜设置 → 同步与备份 → 「本地快照（最近 5 份 · 共 12MB）」，列表带时间与条目数；同步失败后横幅提示「已保留回滚点」。
- **参考**｜`kopia/kopia@master` 的 `policy` 保留规则 `[README]`；`restic/restic@master` 的 `forget --keep-last/--keep-daily` 语义 `[README]`；`mihonapp/mihon` 的 `BackupCreateJob`（后台任务化）`[码]`。
- **成本/风险**｜M｜写放大：必须「数据无变化就不快照」，否则自动同步（15 分钟节流）会持续写盘。
- **优先级**｜P1

### C-4 截图回归基线（Roborazzi / Paparazzi）
- **现状**｜无｜1124 项单测全在纯 JVM 逻辑层；UI 只有 `docs/screenshots/` 的人工截图与 `docs/phone-shared-motion-profile.md` 的实机观测。主题、玻璃降级、深色对比度、大字体这些**视觉断言完全没有自动化**，v1 §10.15 / §11.8 只能挂「待核」。
- **提升**｜(a) 用户：改一行 shape/颜色 token 就能看到 20 张关键页面的 diff，避免「改 A 坏 B」；(b) 工程：把「看起来没坏」变成 CI 可验证，主题相关改动不必每次上真机。
- **原理**｜Roborazzi 在 Robolectric 下渲染 Compose，无需模拟器。矩阵用 `@Config(qualifiers=...)` 组合：`night/light` × `fontScale 1.0/1.3/2.0` × `width 320/411/840` × `motion on/off`；基线图入 `app/src/test/resources/roborazzi/`；比对阈值与「玻璃页面白名单」；项目已依赖 Robolectric 4.17，接入成本主要在依赖与任务配置。
- **效果**｜PR 附 diff 图；`./gradlew recordRoborazziDebug` 更新基线；主题包导入后自动跑一遍关键页。
- **参考**｜`takahirom/roborazzi@main` `[README]`；`cashapp/paparazzi@master` `[README]`（备选，不支持 Robolectric 生态）。
- **成本/风险**｜M｜模糊/折射在 Robolectric 下渲染不准 → 玻璃类页面排除比对或改用纯色占位。
- **优先级**｜P1

### C-5 国际化（当前只有中文）
- **现状**｜无｜`app/src/main/res/` 只有 `values/` 与 `values-night/`，**没有 `values-en`**；界面文案以中文为主，部分硬编码在 Compose 里。README 是中文、许可 MIT、已开源 —— 但非中文用户无法使用。
- **提升**｜(a) 用户：系统语言非中文时出现英文界面，项目具备被国际用户 adopt 的最低条件；(b) 工程：把文案从 Compose 里抽到 `strings.xml` 后，**文案可被审计**（有多少条、哪些没翻译、哪些被复用），也顺带解决「同一个词在不同页面说法不一致」的问题。
- **原理**｜四步：①`grep -P '\p{Han}'` 扫 Compose 硬编码 → 生成 key（`screen_section_action`）与 `values/strings.xml`；②Compose 用 `stringResource(R.string.x)`，带参数的用 `formatArgs`；③复数/数量用 `plurals`（例如「N 集」）；④只翻译 UI 文案，**不翻译**来自 Bangumi 的动态内容（标题/简介）。优先级：导航 → 设置 → 空态/错误 → 其余。
- **效果**｜系统语言切成 English 后，底栏、设置、空态、错误提示全英文；中文用户零变化。
- **参考**｜Android 官方 `strings.xml` / `plurals` 规范 `[README]`；`androidx/androidx` 里的 Compose 示例 `[README]`。
- **成本/风险**｜L｜文案量大，硬编码分散在 475 个 Kotlin 文件里；建议按「页面级」分批抽取，每批都能独立合入。
- **优先级**｜P2（若计划在英文社区分发则升 P1）

### C-6 「收藏健康度」仪表盘
- **现状**｜部分｜v1 §5.15 只提「长期未动的在看提醒」，且没有任何聚合视图；§1.19 的分类完成度也只是分区旁的一个数字。
- **提升**｜用户一屏看出库正在「腐坏」的地方：在看但 90 天没动、看过但没评分、收藏但无标签、空分区。每个数字点进去就是**已筛选的列表**，形成「发现问题 → 当场处理」的闭环。
- **原理**｜纯函数 `HygieneReport(collections, now): List<Issue(kind, count, filter: WorkFilter)>`；`WorkFilter` 已存在（`data/model/WorkFilter.kt`），所以「跳转到已筛选列表」是复用而非新建。四个 issue 的判定：`status=在看 && lastModified < now-90d`、`status=看过 && rating==null`、`tags.isEmpty()`、`folder.memberCount==0`。
- **效果**｜统计页顶部一张玻璃卡：`4 项待整理 · 12 部在看已停滞`，点击进入作品库并自动套用筛选。
- **参考**｜`FuzzyGrim/Yamtrack@dev` 的统计口径组织 `[README]`；GitHub 的 insights「review requested」式行为驱动布局 `[推断]`。
- **成本/风险**｜S｜口径必须写进注释与单测（例如「停滞」是否算节假停播），否则又是一处统计争议。
- **优先级**｜P1

### C-7 「一键补全」元数据修复向导
- **现状**｜部分｜v1 §6.14 只让 Steam 独占条目自动重试匹配；缺封面、缺简介、缺单集时长的条目**没有集中入口**，只能一条条点开详情看。
- **提升**｜(a) 用户：库里的空白区块能被批量填上；(b) 工程：把「补字段」从详情页的副作用变成可调度任务，复用 `data/refresh/` 的 `RetryLadder` 与源级闸门，不再各处写 try/catch。
- **原理**｜`EnrichmentTask(subjectId, missing: Set<Field>)` 入队 → 按字段选源（封面：TMDb 多语言 > Bangumi > 豆瓣灰通道；简介：Bangumi > AniList；时长：Bangumi ep > TMDb）→ 每个源调用前过限流闸门 → 失败退避复用 `RetryLadder` → 结果写 `subject_detail_cache` 并记 `filledBy`。UI 侧只展示「可补全 12 条」，执行时逐条打勾。
- **效果**｜设置 → 数据与备份 → 「补全缺失信息（12 条）」，跑完回列表看封面补齐；失败项保留在队列下次再试。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/track/service/DelayedTrackingUpdateJob.kt` `[码]`（延迟写回队列的形态）；`open-ani/animeko@main` 的 `MediaFetcher` 多源选择 `[README]`。
- **成本/风险**｜M｜批量请求最容易触发源端限流，必须默认 1 并发 + 显式进度。
- **优先级**｜P1

### C-8 「只读镜像」同步角色
- **现状**｜部分｜`data/sync/SyncMergePolicy.kt` 是双向 LWW：两台设备同时编辑会互相覆盖，且当前只保留「冲突优先级」一个粗开关。
- **提升**｜给「只关心看」的设备（平板、备用机）一个零冲突选项：只拉不推，避免它把旧数据写回远端。
- **原理**｜同步角色枚举 `SyncRole { SOURCE, MIRROR }` 存 DataStore；MIRROR 模式下：拉取 → 原子替换本地（写临时库文件再 rename，避免半写）→ 禁用所有写入口（收藏按钮变只读并解释原因）；切回 SOURCE 时把本地未上传的改动一次性提交。
- **效果**｜设置 → 同步与备份 → 「本设备角色：镜像（只读）」，开启后收藏按钮显示锁图标，长按提示「此设备为镜像」。
- **参考**｜`actualbudget/actual@master` 的「服务端持有文件、客户端提交」冲突模型 `[README]`；`logseq/logseq@master` 的离线优先与冲突提示交互 `[README]`。
- **成本/风险**｜M｜镜像模式下用户的编辑会被丢弃 —— 必须**先禁写**再同步，不能靠事后提示。
- **优先级**｜P2

### C-9 封面质量评分（自动选图建议）
- **现状**｜部分｜有 `data/settings/CoverOverrideStore.kt` 与详情页「更换封面」（TMDb 多语言 / 当前源 / 相册），但**没有任何自动评分**；多源封面混用时会出现「一张带日文 logo、一张纯海报、一张被裁过头」的不一致。
- **提升**｜(a) 用户：列表封面不再是「忽大忽小、忽有字忽无字」；(b) 工程：把「哪张图更好」变成一个可单测的纯函数，而不是每次靠人眼挑。
- **原理**｜`score = 2·log(分辨率) + 宽高比接近 2:3 的惩罚项 + 语言匹配 + (无内置文字 ? bonus : 0) - (已被用户手动覆盖 ? 100 : 0)`；文字检测先用「四角边缘能量 + 大字区域」的轻量启发式（不引模型），后续可换 ML Kit；结果缓存到 subject 的派生列，仅作建议不自动覆盖。
- **效果**｜封面选择器按分数排序，第一名标「推荐」；「一键采用推荐封面」只覆盖**未被手动改过**的条目。
- **参考**｜`navidrome/navidrome@master` 的封面/元数据优先级 `[README]`；Coil 的尺寸感知解码（项目已在用）`[码]`。
- **成本/风险**｜M｜启发式文字检测误判会把好封面排后 → 必须保留手动覆盖的最高优先级。
- **优先级**｜P2

### C-10 图片磁盘预算与清理
- **现状**｜部分｜v1 §6.10 只提「核对原图缓存上限与清理入口」；壁纸（静态图 + Media3 视频）、剧照、封面、头像、巡礼缩略图各自落盘，**没有统一的体积视图**。
- **提升**｜(a) 用户：知道「这个 App 为什么占 2.1GB」，并能按类别清理；(b) 工程：把「缓存策略」从各处的 Coil 默认值收成一个可配置预算，避免用户装主题包 + 壁纸后无声膨胀。
- **原理**｜`ImageBudget`: 按类别（cover / still / wallpaper / avatar / anitabi）统计 `diskCache` 目录体积（递归 + 抽样估算），给三类操作：按类别清理、按时间清理（>30 天未访问）、设置上限（超过时 LRU 淘汰）；壁纸单独一条，因为它最大且最不常变。
- **效果**｜设置 → 外观/数据 里出现 `占用 2.1GB：封面 180MB / 剧照 1.2GB / 壁纸 700MB`，每行可清。
- **参考**｜Coil 的 `diskCachePolicy` 与 `MemoryCache` 文档 `[README]`；`mihonapp/mihon` 的 `CoverCache` 清理策略 `[码]`。
- **成本/风险**｜S｜估算体积可能有误差 → 展示用「约」，清理前给出条目数。
- **优先级**｜P1

---

## 5. 优先级：先做什么

### 5.1 四个波次（按「收益 ÷ 风险」排，不是按域排）

**波次 A · 修正确性与数据丢失（全部是 bug，不是功能）**
这些条目在 v1 里被写成「功能缺失」，但本轮读码发现它们是**已经错了**：用户数据正在丢或正在被写错。先修，因为后面所有统计、导出、同步都建立在它们之上。

| # | 事项 | 症状 | 证据 | 成本 |
| --- | --- | --- | --- | --- |
| 5.6 + 5.19 | 观看量把游戏分钟当集数累加 | 「总观看集数」对玩过游戏的人虚高几百 | `data/calculator/StatsCalculator.kt:47` + `CollectionCard.kt:49`（游戏时长存于 watchedEpisodes） | S |
| 4.15 | `airTimeZone` 只写不读 | 非 UTC+8 用户看到的放送时刻全错 1 小时，.ics 导出也错 | 全仓 3 处命中全是写入 | S |
| 7.11 | `watchedVolumes` 不参与序列化 | 换设备 / 恢复备份后卷进度归零 | `SyncManager` 与 `BackupManager` 的 collection 序列化都没有该字段 | S |
| 7.17 | WebDAV 上传是裸 PUT | 两台设备静默互相覆盖 | `data/sync/WebDavClient.kt:35-40`（无 If-Match） | M |
| — | `SyncManager.kt:137` 把 401/500/超时当「远程无数据」成功 | 同步「成功」但什么都没同步 | 源码 | S |
| — | `updateStatus` 不 bump `updateTime` | LWW 判错新旧；1.18 时间戳展示也不对 | `CollectionViewModel.kt:191-199` | S |
| 2.3 | 源顺序重启即丢 + `DataSourceChain` 持懒加载快照 | 设置页切了源，实际不生效 | `PluginManager` + `DataSourceChain` | S |

**波次 B · 补三处底座**（v1 里散落在各域，本轮把它们抽出来）

| # | 事项 | 为什么是底座 | 成本 |
| --- | --- | --- | --- |
| 1.3 | `sync_outbox` 待同步队列（cell 级 + lww/max/union 三规则） | 所有写入口的失败兜底都依赖它 | M |
| 2.1 + 2.2 | 统一能力声明 + 纯函数选择器 | 加一个新源的成本取决于此 | M |
| 2.5 | 任意源 × 全能力自检 | 决定「源坏了」能不能 10 秒看清 | M |
| 5.1 + 5.2 | `StatsQuery` 统一入参（含缓存签名） | 8 个图表共用一条数据流 | M |

**波次 C · 零新请求的纯函数收益**（成本最低、用户立刻可见）

| # | 事项 | 收益 | 成本 |
| --- | --- | --- | --- |
| 4.3 / 4.4 / 4.5 | 未来七天 / 今晚看什么 / 漏看判定 | 打开就知道看什么 | S×3 |
| 4.8 | .ics 导出（UID/RRULE/COUNT/Asia-Shanghai） | 进系统日历 | S |
| C-6 | 收藏健康度仪表盘 | 发现「在看但其实弃了」的条目 | S |
| 5.3 | 统计对比文案（纯函数生成） | 数字变成一句话 | S |
| 1.16 | 「还剩多少」估算 | 列表上就能看到剩余量 | S |

**波次 D · 高价值新面**（要动表或动架构，单独排）

| # | 事项 | 为什么值得单独立项 | 成本 |
| --- | --- | --- | --- |
| C-2 | Bangumi Archive 离线元数据快照 | 断网可用 = 产品形态变化 | L |
| C-3 | 库级快照 + 回滚 | 给所有破坏性操作兜底 | M |
| 6.7 | 详情页离线完整性（4 个 key → 全源） | 已经建好的缓存没用起来 | M |
| 11.1 | Glance 桌面小组件 | 唯一「不打开 app」的入口 | M |
| C-4 | Roborazzi 截图回归 | 让主题/玻璃改动可验证 | M |
| C-1 | 数据体检 | 本地库自身的一致性入口 | M |
| 13.1 | 标题 logo 替代文字标题（来自 `reference-projects-plan.md`） | 数据层已现成（TMDb logos 已解析未使用），是唯一「观感级」改动 | M |
| 13.2 | 同步预览 + 演练面板 | 把 7.4/7.12 的冲突可视化一次做完，复用 `BangumiSyncPlan` | M |

### 5.2 迁移撞车提醒

有 **6 个条目**要求新增列或新表：4.6（逐作品提醒）、4.16（日历事件物化）、5.9（浏览计数）、5.15（健康度）、5.17（弃因分布）、6.17（兜底条目覆盖表）。Room 当前 version 32，**必须合并成一次 v33 迁移**，否则会出现 6 条迁移链交叉、`app/schemas/` 反复变更，1124 单测里的迁移校验会连锁失败。建议：先把 6 张表的 DDL 一次性定稿，再分功能接入。

---

## 6. 本轮勘误：v1 里被推翻的判断 + 新发现的既有缺陷

这一节是本轮**最该先读**的部分：它说明「为什么有些条目不该再讨论」，以及「哪些地方其实已经坏了」。

### 6.1 v1 判断被推翻（12 处）

| 条目 | v1 的说法 | 实际情况 | 证据 |
| --- | --- | --- | --- |
| 4.1 | 三模式与日期条联动需核对 | 模式与锚点是两个正交 StateFlow，翻月不会丢模式；真正缺的是**持久化** | `viewmodel/StatsViewModel.kt:92,113-140` |
| 4.12 | 逐条推送可能刷屏，需合并 | **已实现**合并通知（固定通知 ID + 摘要截断） | `AiringNotificationComposer` |
| 4.14 | README 称已支持，需核对 | **已实现**逐月状态 + `formatAgo` | `BroadcastMonthState` |
| 5.1 | 「部分图表随筛选」 | **完全不随筛选**：`StatsViewModel` 的 combine 里没有筛选入参 | `StatsViewModel` |
| 6.8 | 简介富文本编辑态 | 本项目无内容生产，**建议关闭该条**（只保留可复制） | — |
| 6.15 | 外部源可能未纳入部件列表 | **已纳入**：`DetailSectionId` 里 STEAM/ANILIST/VNDB 都在，缺的只是「折叠」 | `DetailSectionId` |
| 10.9 | 分享卡单一版式 | **已有两种**（卡片 + 海报） | `ShareCardLayout` / `SharePosterCard` |
| 10.12 | 三套玻璃 token 可能重复维度 | `LiquidGlassTokens` **不存在**；两类 token 用途不同，不重复 | `GlassSurfaceTokens` / `GlassChipTokens` |
| 10.15 | 深色对比度待核 | 小件**已有** WCAG 4.5 机械判据（`contrastRatio` / `ensureReadable`），正文仍缺 | `GlassChipTokens` |
| 12.10 | 待多筛选叠加时计数口径 | **已实现**「共 N 条 / 候选池 N 条」真实计数 | 顶部控件 |
| R3/R4 | 两个独立仓库 | `Bangumi-for-Android` 已重定向到 `bangumi`，是同一仓库 | 同一 star / 描述 / 推送日 |
| — | `javafun/TinyPinyin` | 404；真实仓库是 `promeg/TinyPinyin`，且本项目**已评估并放弃**（JitPack 不可达） | `libs.versions.toml:85` |

### 6.2 本轮新发现的既有缺陷（不在 v1 里，但比任何新功能都值得先修）

| 缺陷 | 影响 | 位置 | 类别 |
| --- | --- | --- | --- |
| `totalWatchedEpisodes` 把游戏游玩分钟当集数 | 统计页「总观看集数」对游戏玩家严重虚高 | `StatsCalculator.kt:47`、`CollectionCard.kt:49` | 数据错 |
| `airTimeZone` 只写不读 | 非 UTC+8 用户放送时刻与 .ics 全错 1 小时 | 3 处写入、0 处读取 | 数据错 |
| `watchedVolumes` / `isPrivate` 不参与同步序列化 | 换设备卷进度归零；私密标记不回传 | `SyncManager` / `BackupManager` | 数据丢失 |
| WebDAV 裸 PUT（无 If-Match） | 双设备静默覆盖 | `WebDavClient.kt:35-40` | 数据丢失 |
| 401/500/超时被当成「远程无数据」 | 同步假成功 | `SyncManager.kt:137` | 错误吞掉 |
| `updateStatus` 不更新 `updateTime` | LWW 与时间戳展示都判错 | `CollectionViewModel.kt:191-199` | 数据错 |
| `DataSourceChain` 持懒加载列表快照 | 设置里调整源顺序不生效 | `DataSourceChain` | 逻辑错 |
| `RevealOnScroll.staggerIndex` 不参与延迟计算 | **错峰入场实际不存在**（注释承诺 index×50ms） | `ui/animation/RevealOnScroll` | 实现与承诺不符 |
| `TopFadeOverlay.kt` 全仓 0 调用 | 死代码；且 `hiddenHeight` 有 56.dp / 0.dp 两套口径 | `ui/components/TopFadeOverlay.kt` | 死代码 |
| reduceMotion 漏 6 个点 | dock 三项（指示器滑行/按压缩放/指尖高光）+ 玻璃按压 + 玻璃开关滑杆 + vico 默认入场 | `ui/bottombar/*`、图表 | 无障碍 |
| `DetailCacheStore` 只写了 4 个 sourceKey | 已建好的详情缓存没被 VNDB/AniList/TMDb 用起来，仍靠进程内 `TtlCache` | `DetailCacheStore` / `SubjectDetailViewModel.kt:615` | 资源浪费 |
| `download` 用 `clearAll()+insertAll()` 单事务 | 改分段事务时中途失败会得到空库 | `data/sync/` | 风险 |

### 6.3 引用可核性

本轮写了 `scripts/verify-refs.mjs`：扫描全部 md，抽出 `owner/repo@branch:path`，用 jsdelivr 文件树判定路径是否存在（超大仓库退化为单文件 GET）。**这份脚本本身就是「源码级证据」这个说法的证明** —— 任何人可以重跑。

已知限制：
1. **仓库 >50MB 时 jsdelivr 不给文件树**（`open-ani/animeko`、`chrisbanes/haze`、`xiaoyvyv/bangumi`、`Luis-Herry/bandi`）。这类引用必须写**完整路径**（脚本用 GET 校验），写 `.../` 的会被标为不可核。
2. **jsdelivr 对个别文件会误报 404**（缓存或大小限制），所以判定优先用文件树而不是逐文件 GET。
3. Issue / PR / 提交历史仍然取不到 —— 涉及「为什么这么改」的结论一律标 `[推断]`。

---

## 7. 未验证与开放问题

| # | 问题 | 影响 | 谁来定 |
| --- | --- | --- | --- |
| Q1 | `assets/data/onair/*.json` 的 `timeCN` 是否已从 JST 换算过？ | 若是原样 JST，4.8 的 .ics 会整体早 1 小时，「时区 bug」比 4.15 描述得更大 | 需比对一集的真实首播时刻 |
| Q2 | Anitabi `/lite` 端点是否带经纬度？ | 6.1「应用内地图」若拿不到坐标就只能保持外链 | 抓一次真实响应 |
| Q3 | 旧版客户端收到新增同步字段（`watchedVolumes` / `isPrivate`）会不会回传空值抹掉？ | 决定 7.11 是否需要「版本门槛」 | 需定备份/同步的向后兼容策略 |
| Q4 | Room v33 一次性定稿包含哪些表？ | 6 个条目都要求加表/加列，分散做会连环迁移 | 需先出 DDL 定稿 |
| Q5 | `androidx/compose-samples` 在 jsdelivr 与 ungh 均 404 | 本轮对「Compose 官方范式」零证据，相关建议均标 `[推断]` | 可改用官方文档站 |
| Q6 | `Luis-Herry/bandi` 的 star 与改名细节 | v1 把 R16 当功能基准，本轮只能降级 | ungh 限流恢复后可补 |
| Q7 | Kotatsu 默认分支是 `devel`，部分引用写成 `master` | jsdelivr 会回落，但**写错分支名会让读者以为核过** | 合并时统一成探测到的默认分支 |
| Q8 | 10.2「继续锁 1.0.6」的 cherry-pick 兜底由谁维护？ | 1.0.6 已停更，上游只修 `kmp` 分支 | 需在 `dock-kyant-migration-plan.md` 里写明复检节奏 |

---

## 8. 怎么用这份文档

1. **想找活干**：看 §5.1 的波次 A（7 个 bug），全部 S/M，改完立刻正确。
2. **想评估某个功能**：按域打开 `docs/feature-refs/{NN}-*.md`，每条都有「原理」（能照着实现）与「效果」（能验收）。
3. **想否决某个功能**：看该条的「成本/风险」与 §6；v1 里标「部分」的很多其实是「已实现」，别重复做。
4. **想继续研究**：`docs/feature-refs/_brief.md` 有完整的取证方法（jsdelivr 三条 URL + 证据三级），`scripts/verify-refs.mjs` 可随时复核引用。

> 本文与 v1 的分工：v1 是**功能清单与取舍**（含 57 条已实现、59 条不纳入），v2 是**为什么做、怎么做、做完长什么样**。两份都不含排期；排期见 `docs/reference-repos-plan.md`。