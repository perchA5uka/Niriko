# 外部仓库功能参照规划

> 对象：Niriko（`com.otakup.niriko`，Kotlin + Jetpack Compose + Room 单模块，无 DI 框架）。
> 本文把「可参照的外部仓库」翻译成**本项目可执行的动作**：每项都写明参照哪个仓库的哪个设计、本项目涉及哪些文件、怎么改、怎么验收。
> 边界声明：本机无法直连 github.com，本文对参照仓库的描述来自其 README / `docs/contributing/*` 等公开文档的检索快照，**没有逐行读过它们的源码**；带「待核」的条目请先在仓库里确认后再动手。本项目一侧的文件与行号均已实际读过。

---

## 0. 参照仓库清单与定位

| # | 仓库 | 语言 / 许可 | 在本规划中的定位 |
| --- | --- | --- | --- |
| R1 | [open-ani/animeko](https://github.com/open-ani/animeko) | Kotlin · AGPL-3.0 | **结构参照主源**：能力声明 + 分段选择器 + 可取消多源请求 + 数据源自检工具 |
| R2 | [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Kotlin · Apache-2.0 | 已依赖（`kyant backdrop`）的**上游**：升级与取舍依据 |
| R3 | [xiaoyvyv/bangumi](https://github.com/xiaoyvyv/bangumi) | Kotlin · GPL-3.0 | **移植参照**：KMP 分层、数据层/CMP 边界、iOS 可行性 |
| R4 | [xiaoyvyv/Bangumi-for-Android](https://github.com/xiaoyvyv/Bangumi-for-Android) | Kotlin | R3 的纯 Android 前身：**共享逻辑抽取**的中间态证据 |
| R5 | [chrisbanes/haze](https://github.com/chrisbanes/haze) | Kotlin | 背景模糊分层与降级标准做法 |
| R6 | [skydoves/Cloudy](https://github.com/skydoves/Cloudy) | Kotlin | GPU 加速 + **老设备 CPU 回退**的降级范式 |
| R7 | [AieXile/AnimeTrack](https://github.com/AieXile/AnimeTrack) | Kotlin | **功能对标基准**：Bangumi+TMDB、WebDAV、年度卡、更新提醒 |
| R8 | [ewiro/GameArchive](https://github.com/ewiro/GameArchive) | Kotlin | Steam 时长 × Bangumi 进度的**单一活动记录**模型 |
| R9 | [NyanTV/NyanTV](https://github.com/NyanTV/NyanTV) | Kotlin · AGPL-3.0 | 多服务统一收藏模型；Compose 整体重写经验；TV/大屏 |
| R10 | [anitabi/anitabi.cn-document](https://github.com/anitabi/anitabi.cn-document)（[开放 API 文档](https://navi.anitabi.cn/docs/api/)） | 文档 | 本机 `AnitabiClient` 的**契约来源**：字段对齐 |
| R11 | [FuzzyGrim/Yamtrack](https://github.com/FuzzyGrim/Yamtrack) · [dannyvfilms/Floppy](https://github.com/dannyvfilms/Floppy) | Python · AGPL-3.0 | 跨类型统一状态机、统计口径、迁移做法 |
| R12 | [EuDs63/steam2record](https://github.com/EuDs63/steam2record) | 脚本 | Steam→收藏导入的**最小实现**与边界处理 |
| R13 | [AimesSoft/NipaPlay-Reload](https://github.com/AimesSoft/NipaPlay-Reload) | 多语言 | Bangumi 进度/评分写回在播放场景下的建模 |
| R14 | [edwardez/BangumiN](https://github.com/edwardez/BangumiN)（已归档，只读） | Kotlin(Android) | 历史参照：功能取舍清单，**仅读不作依赖** |
| R15 | [thinktraveller/Anitabi_MCPserver](https://github.com/thinktraveller/Anitabi_MCPserver) | Python | 「数据源 + 自检」思路的小型样本 |

不进入本规划（体量与目标不匹配）：`ekibun/Bangumi`、`zubinxiong/Bgm38`、`PhantomLGZ/BangumiNote`、`limichange/bangumi-client`、`Chesire/Nekome`、`Marco-9456/AniSync`、`bangumi/Bangumi-iOS`、`ximu3/vnite`。其中两项仍值得**只做 30 分钟阅读**：`ekibun/Bangumi` 的插件注册方式、`ximu3/vnite` 的多源字段冲突处理（待核）。

> 与已有文档的关系：`docs/bangumi-reference-plan.md`（对标 czy0729/Bangumi）、`docs/anishelf-reference-plan.md`（对标 AniShelf）保持现状。本文与之**不重叠**：那两篇对标的是产品功能面，本文对标的是**工程实现面**（数据源抽象、刷新编排、玻璃降级、移植分层）。

---

## 1. 关键发现与总体判断

1. **本项目已经独立长出了 animeko 的核心两级结构**，只是名字与职责划分不同：
   - `data/remote/DataSource.kt:14` 的 `SubjectRemoteDataSource` ≈ animeko 的 `MediaSource`（一个接口描述「某个源能提供什么」）；
   - `plugin/DataSourceChain.kt:41` ≈ animeko 的 `MediaFetcher` + `MediaSelector`（多源调度 + 选择策略）；
   - `data/remote/game/GameDataSource.kt:7-18` 的 `GameDataSourceCapabilities` ≈ animeko 的 `SourceCapabilities`，**而且已经比主链更先进**——主链的能力声明在 `plugin/DataSourceCapabilities.kt`，游戏链在 `data/remote/game/`，两套并行。
   → 参照动作以「**统一与收敛**」为主，不是推倒重来。

2. **刷新编排（`data/refresh/`）是本项目相对 animeko 的强项**，不需要参照：`FreshnessPolicy.kt` 的软/硬 TTL、`RetryLadder`、`FreshnessDecider`、`AsyncSingleFlight`、`AsyncSerialQueue` 已经比多数同类客户端完整，且注释里已注明分别参考过 AniShelf 与 AniShelf 的 `failureRetryIntervals`。**R11（Yamtrack/Floppy）在这一层没有可学项**，它在同步/迁移层。

3. **最值得投入的三件事**（按价值/成本比）：
   - **A. 统一能力声明与选择器分层**（R1）：把 `DataSourceChain` 中的选择策略抽成纯函数选择器，为「数据源可插拔 + 可自检」铺路。成本中，收益高（直接决定后续加源速度）。
   - **B. 数据源自检工具**（R1 + R15）：本机已有 `data/probe/`（灰通道探针）与「诊断搜索链路」，把它升级为**任意源的一键自检**，这是本机最缺、也最容易按「纯函数 + fixture」验收的一环（因为 Robolectric 跑不了真玻璃，见 §3 的 B6 结论）。成本低，收益高。
   - **C. 玻璃降级条件集中化收尾**（R2 + R6）：`ui/components/GlassDecision.kt` 已经开了头（把真值表抽成纯函数），但注释里明确写了「只搬运表达式、不改变语义」，仍是**两套有意不对称的语义**。参照 R6 的「GPU 可用性 + 设备能力」两级判定，把它变成一张**可穷举的真值表 + 单一入口**。成本中，收益中高（这是本项目最容易「改坏一处、无人发现」的地方）。

4. **明令不做的**（见 §5）：不引入 DI 框架、不做截图视觉回归（B6 已证伪）、不引入 Hilt/Koin（R3 用了但本项目「无 DI 框架」是显式约定）、不为对齐 animeko 而引入 BitTorrent/弹幕体系。

---

## 2. 逐仓库参照规划

标注含义：**已核** = 本项目一侧文件已实读；**待核** = 参照仓库一侧需先确认。

### R1 · open-ani/animeko（结构参照主源）

| 编号 | 学什么（animeko） | 本项目现状（已核） | 参照动作 |
| --- | --- | --- | --- |
| A1 | 数据源接口按**能力**分区，未声明即不调用；能力声明是**单一真相** | `plugin/DataSourceCapabilities.kt` 与 `data/remote/game/GameDataSource.kt:7-18` 两套并行，`supportsSubjectsInDateRange` 还**默认继承** `supportsSubjectsByMonth`（`:24`） | 抽出统一的 `CapabilitySet`（值对象），主链/游戏链共用；把「派生默认」显式化为构造时计算，禁止在调用点再判 |
| A2 | 选择（选择器）与获取（fetcher）分层；支持多阶段自动选择、按用户偏好排序、记忆手动选择 | `DataSourceChain.search` 是「按注册序串行、首个非空返回」（`:83-92`），选择策略与获取逻辑混在一个 506 行类里 | 抽出 `SourceSelector`：纯函数，入参 = 候选结果 + 能力 + 偏好，出参 = 选中项 + 理由；`DataSourceChain` 只负责调用与落库 |
| A3 | 查询可获取进度、可重启、可取消 | `searchSuggestions` 有 `withTimeout(2000)` 并行 + 任一非空即返回（`:109-120`），但正式 `search` 无取消与进度 | 为「发现页/搜索」引入可取消的查询句柄；`searchSuggestions` 的并行+限时策略**保持不动**（已有明确设计注释，勿回退为串行） |
| A4 | 独立的数据源自检工具，端到端验证「搜索→定位→取出资源」 | 已有 `data/probe/ChannelProbe.kt`、`data/probe/Probes.kt`、`SearchChainProbe.kt`、`GrayChannelRunner.kt`，以及搜索页「诊断搜索链路」 | 把诊断从「搜索三条通路」推广到「任意已注册数据源的全能力自检」，输出 = 能力矩阵 × 每项 HTTP 状态/条数/耗时；纯函数解析 + fixture 单测 |
| A5 | 构建期依赖外部服务需在 `local.properties` 声明 | 参照仓库做法 | 仅记录不采纳：本项目为纯客户端，无自建服务端依赖，**不引入** |

验收（A1–A4 通用）：新增/调整逻辑必须落在**不依赖 Android 的纯函数**里并补 JVM 单测（本项目既有约定，见 README「开发约定」第 2 条）。

### R2 · Kyant0/AndroidLiquidGlass（已依赖库的上游）

| 编号 | 学什么 | 本项目现状（已核） | 参照动作 |
| --- | --- | --- | --- |
| B1 | 库 2.0 起转为 Compose Multiplatform 库，`backdrop` 模块化 | 依赖 `miuix-blur 0.9.0` + `kyant backdrop`，自实现折射/模糊 | 先**只对齐版本与 API 面**：确认所用版本与上游 2.x 的 API 差异（待核），再把「自实现折射」逐块比对上游是否已提供等价能力，能删则删 |
| B2 | 边缘折射、gel-press 交互、按设置降级 | `ui/components/GlassDecision.kt` 的真值表；`GlassEffectLevel` 三档降级 | 把上游的降级**判据**（设备能力/系统版本/用户设置）与本项目的档位语义对齐，形成单一判定入口 |

### R3 / R4 · xiaoyvyv/bangumi（KMP）与其 Android 前身

| 编号 | 学什么 | 本项目现状 | 参照动作 |
| --- | --- | --- | --- |
| C1 | 同一功能在「纯 Android」与「KMP」两版之间的**可抽取边界** | `docs/ios-port/` 已有 spike、依赖映射、模块划分 | 用 R4→R3 的实际演进作为**证据**，校对本项目 `ios-port/` 里「哪些模块能共享」的结论；把差异写回文档（不改生产代码） |
| C2 | MVI + 模块化 | 本项目是手写 Factory + Repository + 纯函数计算器 | **不采纳 MVI 与多模块化**（与「无 DI 框架」「纯函数优先」两条既有约定冲突，收益不足以抵消回归面）。只采纳「计算逻辑必须可单测」这一条，本项目已满足 |
| C3 | 跨平台数据层的抽象顺序 | 全部业务逻辑位于 `app/src/main/java/com/otakup/niriko/` | 仅在 `docs/ios-port/` 补充一页「Android-only 依赖清单」（`android.util.Log`、`Context`、`WorkManager`、`Coil`、Media3 等出现位置），作为后续抽 `commonMain` 的输入 |

### R5 / R6 · haze / Cloudy（模糊与降级）

| 编号 | 学什么 | 本项目现状（已核） | 参照动作 |
| --- | --- | --- | --- |
| D1 | 背景捕获（backdrop）作为**独立一层**，前后景各自消费 | 壁纸层与悬浮底栏共用 backdrop 捕获层（README 已述）；`ui/components/GlassDecision.kt` 用 `cardBackdrop` / `backdrop` 两个泛型参数表达 | 把「有没有可折射层」与「选哪档效果」两个判断在**类型上**分开，避免像现在这样两处语义有意不对称（`GlassDecision.kt:20-22`）却只能靠注释约束 |
| D2 | GPU 加速 + **老设备 CPU 回退** | 三档降级（全效果/降低/关闭）由设置驱动 | 增加一条**设备判据**（GPU/API 等级）作为独立输入，与用户档位取交；判据本身是纯函数，进 JVM 真值表测试 |
| D3 | 模糊半径/样式的可播放（replayable）样式对象 | `GlassSurfaceTokens`、`GlassChipTokens`、`LiquidGlassTokens` 多个 token 对象 | 只做**收敛**：确认这些 token 是否表达同一维度，重复的合并（待核，需先读这三个文件） |

### R7 · AnimeTrack（功能对标基准）

| 编号 | 学什么 | 本项目现状 | 参照动作 |
| --- | --- | --- | --- |
| E1 | Bangumi + TMDB 双源搜索/添加 | 已是 Bangumi 主源 + AniList 兜底 + Steam/VNDB 旁路，且 TMDb 已接入 | 无需动作，仅做一次能力对照表（本项目的源更多） |
| E2 | **WebDAV 播放**（从 WebDAV 直接取媒体） | 现有 WebDAV 只做 JSON 备份同步（`data/sync/WebDavClient.kt`） | **不采纳**：与「不内置播放器」的既有定位冲突；记入 `docs/remaining-phases.md` 备查 |
| E3 | 年度汇总卡片 | 已有「年度总结」（README 统计节） | 只比对**卡片文案与指标口径**（总追番数/总集数/题材占比），差异项作为可选微调，不新增功能 |
| E4 | 「自有账号 / Bangumi / Bilibili / WebDAV」四选一同步 | 已有 WebDAV + Bangumi OAuth + B站/Steam 导入，JSON 备份独立 | 可参照其**选择界面的心智模型**：把「同步」与「导入」在设置页分成两组入口，避免现在的混排（见 `设置 · 同步与备份` 与 `数据源与账号`） |

### R8 · GameArchive（单一活动记录）

| 编号 | 学什么 | 本项目现状 | 参照动作 |
| --- | --- | --- | --- |
| F1 | Steam 游玩时长 + Bangumi 观看进度 **汇总为同一份个人活动记录** | 统计页有「时间线」（按作品聚合的操作记录），与 Steam 时长/进度各自分散 | 动作：在 `data/calculator/` 增加纯函数「活动记录合并器」，把两类事件统一为同一 `ActivityEvent` 序列；先只在统计页时间线复用，不改库表 |
| F2 | 账号配置/标签/备注/进度基线/活动历史**全部本地**，无账号体系 | 本项目有账号（Bangumi OAuth/Steam），但数据本地优先 | 无需动作。作为「本地优先」决策的**旁证**记入文档 |

### R9 · NyanTV（多服务统一模型 / 大屏）

| 编号 | 学什么 | 本项目现状 | 参照动作 |
| --- | --- | --- | --- |
| G1 | 多服务（AniList/MAL/Simkl）收敛为**一套统一收藏模型** | `subjects.sourceKey` 已是跨源稳定唯一键（`steam:570` / `anilist:media-123` / `vndb:v123`），Bangumi 为 `null` | 动作：补一份**跨源状态映射表**的单测（各源状态 → 本项目 5 态），把隐式映射显式化并锁死（现有 `plugin/bilibili/BilibiliSyncModels.kt:114`、`data/sync/bangumi/BangumiStatusMapper.kt` 各写一份） |
| G2 | 用 Compose 整体重写并保持 D-Pad 全导航 | 已有 `ui/adaptive/`（宽屏竖向 dock、折叠屏、`NirikoFoldStrategy`） | **不采纳 TV 形态**。仅取「焦点/方向键可达性」检查清单，作为无障碍验收的补充项 |

### R10 · anitabi 官方文档（契约对齐）

| 编号 | 学什么 | 本项目现状（已核） | 参照动作 |
| --- | --- | --- | --- |
| H1 | `/bangumi/{id}/lite` 的字段、图片域名、错误语义 | `data/remote/anitabi/AnitabiClient.kt:25` 硬编码 `https://api.anitabi.cn/bangumi/{id}/lite`，`ignoreUnknownKeys=true`，失败静默返回 null | 逐字段比对响应模型（`AnitabiResponse`）与官方文档；把「字段缺失/接口变更」的表现显式化（现在是静默 null，无法区分「无地标」与「接口坏了」） |
| H2 | 图片 API 基址（`image.anitabi.cn`） | 图片 URL 处理分散 | 确认是否需要独立基址与适配（若需要，统一到 `util/CoverUrlResolver.kt` 的同一套解析，不要新开一处） |

### R11 · Yamtrack / Floppy（跨类型状态机与迁移）

| 编号 | 学什么 | 本项目现状 | 参照动作 |
| --- | --- | --- | --- |
| I1 | 电影/剧/番/书/游戏/音乐/播客**同库**，状态机与统计口径统一 | 本项目 5 类（动画/书籍/游戏/音乐/三次元），状态 5 态 + 按类型换动词 | 动作：把「动词按类型变化」的映射（README 收藏模型节）抽成 `WatchStatusHelper` 的显式表并补单测；核对统计口径（完成率的分母是否含「抛弃」）并写进文档 |
| I2 | 自托管迁移/数据导入的版本处理 | 备份格式 v4、可读 v1–v4；Room 迁移链 v2→v32，显式迁移 | 无需动作（本项目更严）。取其「导入即校验并报告」的做法：备份恢复失败时给出**逐项原因**而非整包失败（核对现有 `data/backup/` 行为，待核） |

### R12 · steam2record（导入最小实现）

| 编号 | 学什么 | 本项目现状 | 参照动作 |
| --- | --- | --- | --- |
| J1 | Steam 记录 → 收藏的字段映射与「无法匹配」的处理 | `data/remote/steam/SteamLibraryImporter.kt`、`SteamLibraryMatcher.kt`、`SteamTitleMatcher.kt`，含家庭共享、时长→状态推断 | 动作：对照其「匹配失败怎么办」，核对我方的 `skippedNotMatched` 计数是否**可解释**（每个未匹配项应能给出原因），补一条单测 |

### R13 · NipaPlay-Reload（写回建模）

| 编号 | 学什么 | 本项目现状 | 参照动作 |
| --- | --- | --- | --- |
| K1 | 进度/评分/评论写回 Bangumi 的**批量与幂等** | `data/sync/bangumi/BangumiSyncManager.kt`、`BangumiSyncPlanner.kt`、`BangumiStatusMapper.kt` | 动作：核对「同一批写回重复执行是否产生重复请求」；对照其「以远端为准/以本地为准」的冲突表述，确认我方 `冲突优先级` 设置的语义写在文档里 |

### R14 · BangumiN（已归档）

| 编号 | 学什么 | 参照动作 |
| --- | --- | --- |
| L1 | 功能取舍与页面清单 | 仅做一次「功能覆盖对照」，用于确认无重大功能缺口；**不引入代码**（仓库已归档，许可证与代码状态需谨慎，待核） |

### R15 · Anitabi MCP Server（数据源 + 自检）

| 编号 | 学什么 | 参照动作 |
| --- | --- | --- |
| M1 | 把「搜索 → 拿 ID → 取附属数据」串成一条可自检链路 | 与 R1 的 A4 合并执行：诊断页输出**每个源的能力矩阵 + 实网结果**，并支持导出诊断文本便于反馈 |

---

## 3. 可迁移模式清单（跨仓库汇总，共 15 条）

按「值不值得做」排序。每条的「涉及文件」均已实读。

| # | 模式 | 现状 | 参照 | 优先级 |
| --- | --- | --- | --- | --- |
| 1 | 能力声明单一真相 | 主链/游戏链两套并行 | R1 | P0 |
| 2 | 选择器与获取分离 | 混在 `DataSourceChain`（506 行） | R1 | P0 |
| 3 | 数据源一键自检（能力矩阵 + 实网结果 + 导出） | 仅有搜索三通路诊断 | R1 / R15 | P0 |
| 4 | 跨源状态映射显式化 | 各导入器各写一份 | R9 / R11 | P1 |
| 5 | 玻璃降级单一判定入口（含设备能力） | 纯函数已抽但只搬运、两处语义不对称 | R2 / R6 | P1 |
| 6 | 查询可取消 / 带进度 | 仅建议链路限时并行 | R1 | P1 |
| 7 | 匹配失败可解释（逐项原因） | 只有计数 | R12 | P1 |
| 8 | 年度/统计口径文档化 + 锁定 | 口径散在 README | R7 / R11 | P2 |
| 9 | 统一活动记录（观看 + 游玩合并） | 时间线仅观看侧 | R8 | P2 |
| 10 | 写回幂等性核对 | 已有 Planner，未见幂等断言 | R13 | P2 |
| 11 | 本地依赖清单（为 KMP 铺路） | `docs/ios-port/` 已有调研 | R3 / R4 | P2 |
| 12 | Android-only 依赖清单 | 同上 | R3 | P2 |
| 13 | 同步/导入入口分组 | 设置页混排 | R7 | P3 |
| 14 | 备份恢复逐项报错 | 待核 | R11 | P3 |
| 15 | 图片基址统一（Anitabi） | 分散 | R10 | P3 |
| 16 | 插件优先级列表有测试 | 只有 `HistoricalDataSourceChainTest`，`PluginManager` 零覆盖 | R1 | P1 |

**第 16 条的由来（本次实读的附带发现）**：`plugin/PluginManager.kt:47` 与 `:55` 的交换写法是

```kotlin
_plugins[idx] = _plugins[idx - 1].also { _plugins[idx - 1] = _plugins[idx] }
```

**它是对的**（赋值先把右值求完再写回，`also` 里读到的仍是 `idx` 的旧值，因此 `idx-1` = 旧的 `idx`，随后 `idx` = 旧的 `idx-1`，构成一次正确交换）。但这条正确性完全依赖 Kotlin 的求值顺序，写法极易被误读为自赋值、也很容易在后续「顺手重构」时改坏。而 `PluginManager` 的移动功能**决定数据源链的优先级顺序**（`val primary` 取 `firstOrNull()`，见 `:60`），却没有任何测试。动作：补 `PluginManagerTest`（注册去重、moveToTop/moveUp/moveDown 边界、primary 语义）——这条与第 1 条同波次做，成本极低。

---

## 4. 分阶段路线图

每波结束必须：`.\gradlew.bat testDebugUnitTest` 全绿 + `assembleDebug` 通过 + 新增/改动逻辑有对应 JVM 单测（编号与 §3 的模式清单对应）。

### 波次 1（P0：不改变用户可见行为）

1. **统一能力声明**：抽出 `CapabilitySet`，主链与游戏链共用；`supportsSubjectsInDateRange` 的派生默认显式化。
   - 涉及：`plugin/DataSourceCapabilities.kt`、`plugin/DataSourcePlugin.kt`、`data/remote/game/GameDataSource.kt`、`plugin/DataSourceChain.kt`
   - 验收：两种注册组合下的能力矩阵单测；`DataSourceChain` 行为不变（现有测试全绿）
2. **抽 `SourceSelector`**：把 `DataSourceChain.search` 的选择策略抽成纯函数（串行首个非空 → 选择器表达），获取与落库留在 Chain。
   - 验收：选择器真值表单测（含全部失败、部分失败、旁路游戏源命中）；`searchSuggestions` 的并行+2s 限时**不得改动**
3. **数据源自检升级**：把「诊断搜索链路」推广为「任意源 × 全能力」自检，可导出文本。
   - 涉及：`data/probe/*`、搜索空态诊断入口
   - 验收：解析逻辑纯函数化 + fixture 单测（含 4xx/5xx/超时/空响应四种）
4. **补 `PluginManagerTest`**（§3 第 16 条）：现无任何覆盖，而它决定数据源链优先级。
   - 验收：注册去重、moveToTop / moveUp / moveDown 的边界（首位/末位/不存在）、`primary` 语义

### 波次 2（P1：结构性改造）

5. 跨源状态映射表 + 单测（覆盖 Bangumi/AniList/Steam/Bilibili/Kazumi 五源）
6. 玻璃降级单一入口：并入设备能力判据，重构 `GlassDecision` 为显式真值表
   - 特别注意：`GlassDecision.kt:20-22` 说明「两处语义有意不对称」，改造前必须先在文档里写清**新语义**并确认这不是回归
   - 可先扩 `GlassProviderIdentityTest`（本机已用 Robolectric + `Backdrop` Proxy 跑通状态逻辑），把它升级为降级真值表测试
7. 可取消查询句柄（发现页/搜索）
8. Steam 导入未匹配项逐项原因

### 波次 3（P2：文档与口径）

9. 统计口径文档化并在 `data/calculator/` 锁定（完成率分母、均分权重、票数门槛）
10. 统一活动记录合并器（纯函数 + 单测，仅用于统计页时间线）
11. 写回幂等性核对 + 断言
12. `docs/ios-port/` 补 Android-only 依赖清单与可共享边界结论（用 R3/R4 的实际演进校正）

### 波次 4（P3：整理项，可随时做）

13. 设置页同步/导入入口分组；14. 备份恢复逐项报错；15. Anitabi 字段与图片基址对齐；16. 玻璃 token 收敛

---

## 5. 取舍与不做清单

**明确不做**（理由均已落到本项目既有约定上）：

- **不引入 DI 框架**（Hilt/Koin）：README「开发约定」明确「无 DI 框架」，R3 用了但不构成采纳理由。
- **不做截图视觉回归**：B6 探针已证伪——Robolectric 的 native SkSL 编译不了 kyant backdrop 与本项目 AGSL 着色器，失败点在 `DrawBackdropNode.onAttach`。玻璃的**像素级**验收只能靠真机；但**状态/降级逻辑**可测——`GlassProviderIdentityTest` 已用 Robolectric + `Backdrop` 反射 Proxy 跑通组合逻辑，这是比 B6 结论更宽的可行边界，应充分利用。
- **不引入播放/弹幕/BitTorrent**：与既有定位冲突（R1 依赖自建服务端，R7 的 WebDAV 播放同样越过边界）。
- **不做多模块化与 MVI 重构**：收益不足以抵消全仓回归面。
- **不引入已归档仓库的代码**（R14）。

**允许引入的新依赖**：无。本规划全部项均可用既有依赖（Compose/Room/OkHttp/序列化）完成；若确需新增，须先按 README 约定讨论。

---

## 6. 验收与风险

- **验收基线**：`.\gradlew.bat testDebugUnitTest`（当前 1124 项）+ `assembleDebug`；新增逻辑必须有纯函数单测。
- **不可自动验证的部分**（玻璃折射、共享元素转场、CRT/网点）：只能列真机检查清单，**不得**用 JVM 单测冒充视觉验收。
- **迁移风险**：本规划不新增 Room 表、不改迁移链。若后续需动表（如统一活动记录落库），必须遵守「DDL 与 `app/schemas` 的 `createSql` 逐字一致」。
- **参照仓库许可证**：R1 / R9 / R11 为 AGPL-3.0，R3 为 GPL-3.0。本规划只借鉴**设计思路**，不复制代码；若将来要复制任何片段，必须先解决 GPL/AGPL 与本项目 MIT 的兼容问题。
