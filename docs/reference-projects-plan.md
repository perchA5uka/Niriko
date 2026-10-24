# 参考项目对照与可落地清单（douban-bangumi-sync / izuko-tv / onetake / motion-web）

> 目的：判断这四个参考项目里，哪些能力能落在 Niriko 现有架构上，并给出优先级、落地位置、验收口径与风险。
> 取证方式：读四个仓库的 README + 文件树；izuko-tv 额外读了标题 logo 相关的实现与测试（`TvTitleLogo.kt` / `TvTitleLogoBitmaps.kt` / `TvTitleLogoContrast.kt` / `TvTitleLogoState.kt` / `TvTitleLogoBoxTest.kt` / `TvTitleLogoFlipTest.kt` / `TvNativeHeroTextLogoTest.kt` / `TvNativeHeroTitleHandoffTest.kt` / `TmdbTitleLogoPickTest.kt`）。
> 覆盖缺口见 §8。本文只做**功能取舍**，不含代码搬运。

---

## 0. 结论速览

| 参考项目 | 许可 | 值得拿的东西 | 判决 |
| --- | --- | --- | --- |
| [douban-bangumi-sync](https://github.com/XxkumaXia/douban-bangumi-sync) | MIT（可借鉴代码，需署名） | 「扫描 → 匹配 → 差异 → 逐条勾选方向 → **演练** → 执行」的同步工作区；映射表缓存；接口自检；写入限流 | **值得做**，且与本仓库已有的 `BangumiSyncPlan` 天然衔接；豆瓣**写回**单独决策（灰区） |
| [izuko-tv](https://github.com/GrahamZen/izuko-tv) | **AGPL-3.0**（不可抄代码） | **标题 logo 替代文字标题**（选图 / 尺寸框 / 对比度翻色与柔光 / 看不清回退文字 / 进场等待 / 交接位图缓存 / 预取） | **首选做**：本仓库 TMDb 侧已经拿到 `logos` 只是没用（§1.2） |
| [izuko-tv](https://github.com/GrahamZen/izuko-tv) | AGPL-3.0 | 应用内更新、首启连通性自检、帧性能诊断 | 次级可做（§3） |
| [izuko-tv](https://github.com/GrahamZen/izuko-tv) | AGPL-3.0 | 屏保 / 主屏幕频道 / 界面缩放 / Web 控制台 / 播放器 / 弹幕 | **不建议**：TV 形态专用，本仓库是手机·平板单 Activity（§3.4） |
| [onetake](https://github.com/feitangyuan/onetake) | PolyForm **Noncommercial** | 一镜到底的**发布片**生产线 + 「每个边界都要有东西活下来」的动效判据 + carry 打分验收 | 作为**工具链/方法学**用，不是 App 功能；代码不可并入 MIT 仓库 |
| [motion-web](https://github.com/feitangyuan/motion-web) | CC BY-**NC** 4.0 | 反 AI 塑料感清单（禁线性匀速）、真实弹簧阻尼、定格阶梯抖动、Verlet 绳排、粘性叠层、滚轮视差轨道、headless oracle | 拿**方法与 1–2 个特效**；代码不可并入 |

一句话：**能直接进 App 的第一名是「标题 logo 替代标题」（来自 izuko，但必须自研）**；第二名是「同步预览 + 演练面板」（来自 douban-bangumi-sync，MIT，可与现有 `BangumiSyncPlan` 复用）；onetake / motion-web 主要价值在「给 Niriko 做一条发布片」和「补动效规范」，不在功能清单。

---

## 1. izuko-tv：标题 logo 替代文字标题（P0）

### 1.1 它实际做了什么（照抄它的规则没有意义，但规则本身值得记）

izuko 在 hero（首屏大标题块）、详情页首屏、播放器三处，用**作品原版 logo 图**占据标题位，替代文字标题：

1. **选图**（`TmdbTitleLogoPickTest`）：按语言挑 —— 先按显示语言（原语言 / 界面语言）；中文时区域优先 **CN > TW > HK**；同语言里按 `vote_average` 降序、再按 `vote_count`；**跳过 .svg 与 aspect_ratio = 0**；其它语言与无语言 logo **永不使用**。
2. **尺寸框**（`TvTitleLogoBox`，照 Prime Video 实机量的五部回归）：`面积恒定`——宽高比越宽越矮。高上限 = 2 × 标题行高；宽上限 = 3.38 × 高，且不超过原文字标题的宽；面积 = 2.19 × 高²。实测 960×540 上 Prime 五部（宽高比 1.16→43×37、2.45→91×37、3.06→95×31、5.4→124×23、7.9→125×16 dp）。
3. **对比度处理**（`TvTitleLogoContrast.kt`，约 22KB，本仓库只读了方法说明）：用 **APCA 对比度**（WCAG 3 草案模型）算 logo 不透明处与正下方背景的 **|Lc| 中位数**；两者都暗（OKLab L 中位数 < 0.5）或其他规则命中 → 判「看不清」。处理顺序：① 把 logo 里**黑白灰**的部分翻到另一头（彩色原样、透明度不变），够清就用；② 还不够（整体是深色彩色字）→ 在 logo 背后加一团**柔光**（白/黑叠层，按 logo 形状摊开、离得越远越淡，只加到刚好可读）；③ 整体看得清但**局部字被吃掉**（「看丢的墨」）→ 只翻色，翻不好就原样、不加柔光。实测 800 部热门番日文 logo 只有约 2% 触发。
4. **看不清时回退文字**（设置档 `Off / 始终 / 看不清时显示文字`），且任意一处判过看不清就全处（含播放器）回退文字。
5. **进场等待**（`TvNativeHeroTextLogoTest`）：logo 查询还没回来时**先不进场**；查询完成立刻进场；超过约 250ms 放弃等待、按文字进场；已知有 logo 则**先占 logo 位**（标题位高 = logo 框高），图加载失败才换文字。
6. **交接位图缓存**（`TvTitleLogoBitmaps`）：按「logo 路径 + 是否翻色（+ force）」为键，12 张 LRU。原因写得很实在：Compose 图片组件要等布局量出尺寸才发请求，**新建实例即使图在内存缓存里也要空一两帧**，共享元素转场那一刻 logo 会闪。
7. **预取**：列表页 hero 流水线替邻居先查好；进程内热表 + 快照订阅。

### 1.2 为什么本仓库几乎「一步之遥」

| 事实 | 证据 |
| --- | --- |
| TMDb 图片端点（TV / Movie）的响应里**已经反序列化 `logos`**，全仓库无人使用 | `app/src/main/java/com/otakup/niriko/data/remote/tmdb/dto/TmdbDtos.kt:183`（`TmdbImagesDto.logos`）；`TmdbRepository` 只取 `posters` / `backdrops`（`TmdbRepository.kt:84`、`:93`） |
| 图片端点已在调用，且语言参数已按中文场景配好 | `TmdbApiService.kt:71`（`/tv/{id}/images`）、`:77`（`/movie/{id}/images`），`include_image_language = "zh,ja,en,null"` |
| 已有「多语言图片优先级」纯函数可作模板 | `TmdbRepository.kt` 的 `TmdbImageSelection.sortPosters`（纯函数 + 注释记录过一次 BUG-1 回归） |
| 已有 TMDb 绑定与镜像化的图片基址 | `TmdbRepository.boundTvId`（`:39`）、`SubjectExternalIdEntity.PROVIDER_TMDB_TV/MOVIE`、`TmdbImageUrl.imageBaseUrl` |
| 已有「按背景亮度决定文字可读性」的家底 | `ui/common/ImageBrightness.kt`（`rememberImageLuminance`：32px 缩略图 **p80 分位**，进程级缓存）、`ui/common/CoverImageAnalysis.kt`（480px、8MiB LRU、同 URL 并发合并） |
| 详情页正好是「模糊封面墙 + 玻璃卡」结构，logo 的对比度判定有落点 | `ui/subject/SubjectDetailScreen.kt:523-538`（封面放大铺底 + `layerBackdrop` + `pageCardGlassBackdrop`） |
| 详情页已有「部件显示 / 隐藏 / 排序」，标题是其中之一 | `ui/subject/DetailSectionId.kt:43`（`TITLE("title", "标题与评分概览", hideable = false)`）、`ui/settings/pages/DetailSectionsSettingsScreen.kt`、`AppSettings.detailHiddenSections/detailSectionOrder` |
| 开关走 DataStore + JSON 备份，加字段有既定路径 | `data/settings/AppSettings.kt`、`SettingsDataStore.kt`、`data/backup/BackupManager.kt` |

结论：**数据层几乎零成本（响应里已有 logos），工作量集中在校验过的纯函数 + 一处标题渲染 + 对比度处理 + 开关与单测。**

### 1.3 落地拆分（建议按此顺序，每步都能独立验收）

| 步骤 | 内容 | 主要文件（新增/改动） | 验收 |
| --- | --- | --- | --- |
| S1 | 纯函数：**选图**（语言优先 → 票均 → 票数；跳过 svg / aspect ≤ 0）与**尺寸框**（面积恒定 + 高/宽上限 + 不超过标题宽） | 新增 `data/remote/tmdb/TitleLogoPick.kt`、`ui/subject/TitleLogoBox.kt`（或并入现有 `TmdbImageSelection`） | JVM 单测：照 izuko 的用例**自己重写**（语言/简繁序、同分看票数、svg 与 0 宽高被跳过、极端宽高比被上限截断、行高为 0 返回 null） |
| S2 | 数据：`TmdbRepository.titleLogoCandidates(subject)`（TV/Movie 两条绑定通路，复用同一 images 响应；失败静默返回空） | `TmdbRepository.kt`、新增 `boundTmdbId(subjectId)` | 单测：无绑定 → 空；有绑定但响应无 logos → 空；有 logos → 排序结果稳定 |
| S3 | UI：`TitleLogoText`（占位高 = logo 框高；加载中与失败都回落文字；`LocalReduceMotion` 下不做交接等待） | 新增 `ui/subject/TitleLogoText.kt`；接入 `DetailSectionId.TITLE` 区块 | 截图/手动：有无 logo 两种情形版面不跳；失败不空窗 |
| S4 | 对比度：复用 `loadCoverAnalysisBitmap` 取缩略 logo → 新增纯函数 `logoNeedsFlip(tone)` / `flipMonochrome(bitmap)`；背景侧复用 `rememberImageLuminance`（必要时升到 APCA 判定） | `ui/common/` 或新增 `ui/subject/TitleLogoContrast.kt` | 单测：合成像素行（纯黑 / 深灰 / 彩色 / 浅色 + alpha）翻色结果逐像素断言；色调比例阈值边界 |
| S5 | 设置：`detailTitleLogoMode`（关 / 始终 / 看不清时显示文字）+ 语言档（原语言 / 界面语言）+ 备份字段 | `AppSettings.kt`、`SettingsDataStore.kt`、`BackupManager.kt`、`DetailSectionsSettingsScreen.kt`（或外观页）、`SettingsViewModel.kt` | 单测：默认值、往返（备份导出/恢复）、关掉时完全不发请求 |
| S6 | 缓存与预取（可选，体感项）：热表 + 12 张 LRU + hero/相邻卡预取 | 新增 `TitleLogoCache.kt` | 单测：键区分翻色档；LRU 上限；失败不写入 |
| S7 | 扩面（可选）：作品库「画廊大卡」与发现海报卡的标题位 | `LibraryGalleryCard.kt`、`DiscoverPosterCard.kt` | 手动：小尺寸下 logo 是否还认得出来，不行就只留详情页 |

工作量：S1–S5 为**中**（估 1–2 个会话，按本仓库「新增逻辑补 JVM 单测」的约定），S6/S7 可后置。

### 1.4 与 izuko 的差异决策（手机 ≠ 电视，不能照搬）

| 事项 | izuko（TV） | 建议 Niriko（手机） |
| --- | --- | --- |
| 尺寸框来源 | Prime Video 实机五部回归 | 保留「面积恒定 + 高≤2 行 + 宽≤3.38×高」的**规则**，数字按详情页标题字号重算一次并写进注释；不要引用它的 dp 表当事实 |
| 看不清处理 | APCA + 翻色 + 柔光 + 局部「看丢的墨」 | 第一版只做「色调翻色 + 看不清则回退文字」，**柔光与局部判定后置**（成本/收益比不高，且详情页本身有模糊封面墙当底） |
| 默认档 | 有独立设置 | 默认**关**（保持现有文本标题），开了也默认「看不清时显示文字」 |
| 数据源 | TMDb | 只用 TMDb（AniList / VNDB 不提供带透明通道的标题字图；fanart.tv 需另配 key，先不引入） |
| 交接 | 用位图缓存避免共享元素转场闪一帧 | 本仓库共享元素转的是**封面**不是标题，暂不需要；但**详情页首屏与「换封面」重进**仍可能出现文字→logo 跳变，S3 的「占位高」已能挡住 |

### 1.5 这张牌的真实覆盖度（先讲清楚，别指望 100%）

- **只有 TMDb 绑定的条目**才有候选：动画 / 三次元（以及用户手动绑定的其它类型）。书籍 / 游戏 / 音乐没有 → 仍是文字标题。
- **中文 logo 覆盖率天然低于日文**：中文条目常常只有日文/无语言 logo，而 izuko 的规则里「无语言 logo 永不使用」。第一版建议：中文缺失时**回落到原语言 logo**（并保持「看不清/不存在 → 文字」的兜底），把这条例外写进单测。
- **未验证项**：TMDb 图片对象是否带 `iso_3166_1`（区域）——izuko 的选图测试里按 CN/TW/HK 排序，说明它的候选结构里有区域字段，但它的区域来自哪个端点/映射表我没有读完。**没有 TMDb key 也无法实测**。若本仓库拿到的 logos 不含区域，则简繁优先要另找信号（或先只按语言 + 票数）。

---

## 2. douban-bangumi-sync：把「同步」做成可预览、可演练的工作区（P1）

### 2.1 它的能力清单（README 口径）

双向同步 **状态 / 评分 / 短评 / 标签**，每项独立开关，类目（动画/三次元/图书/音乐/游戏）可分别开关；流程是 **接口自检 → 扫描两侧 → 自动匹配 → 计算差异 → 逐条勾选方向 → 演练 → 执行**；面板有五个页签（差异 / 待确认匹配 / Bangumi 独有 / 日志 / 演练结果）；条目匹配支持自动 + 人工确认 + 手动指定 ID，映射表缓存；**演练模式**会把每条将发出的请求（URL、表单字段、静态检查项）完整列出且不真写，结果可复制 / 导出 JSON；评分换算是**有损**的（10 分制 ↔ 5 星），提供阶梯 / 线性 / 自定义三种，并建议固定一个权威源；写入间隔 1.2s、翻页 0.8s 防 429 / 验证码；短评同步**默认关闭**（写错不可逆）。

### 2.2 本仓库现状（哪些已经有了）

| 能力 | Niriko 现状 |
| --- | --- |
| 差异计算（本地独有 / 远端独有 / 冲突 / 一致） | **已有纯数据模型** `data/sync/bangumi/BangumiSyncPlan.kt`（`localOnly` / `remoteOnly` / `conflict` / `match`）+ `BangumiSyncPlanner.kt` + `BangumiSyncPriority` |
| 冲突优先级 | **已有**（本地优先 / Bangumi 优先） |
| 可同步字段 | **已有**：状态 / 评分 0–10 / 短评 `personalImpression` / 个人标签 / 私密 / 起止日期 / 进度（`CollectionEntity`） |
| 接口自检 | **已有**：`data/probe/ChannelProbe.kt`、`Probes.kt`、`GrayChannelRunner.kt`、`SearchChainProbe.kt`（HTTP 状态、耗时、最终 URL、Content-Type、前若干字节）；设置里已有「诊断搜索链路」与豆瓣连通性自检 |
| 第三方登录会话（Cookie 保持） | **已有可复用范式**：B 站导入用 WebView 登录 + 注入脚本 + JS bridge + **域名白名单**（`ui/bilibili/BilibiliSyncScreen.kt:323-400`、`plugin/bilibili/BilibiliWebBridge.kt`、`BilibiliSyncViewModel.kt`） |
| 写入限流 | **已有**：`util/SourceRateLimiter.kt` + `SourceRateLimits.kt`（已接 IGDB 4 req/s、MusicBrainz 1 req/s、VNDB 200 次/5min） |
| 外部条目 id 映射 | **已有**：`subject_external_ids`（`PROVIDER_TMDB_TV/MOVIE` 等）+ 统一匹配服务 `data/match`；豆瓣 id 目前已绑但只在详情页会话快照里（`SubjectDetailSnapshot.doubanBoundId`） |
| WebDAV / Bangumi 同步的执行与节流 | **已有**（`SyncManager`、`BangumiSyncManager`、串行互斥、退避梯） |

**缺的只有「看得见的计划」和「不落盘的预演」。** 这正是 dbsync 最值得拿的部分。

### 2.3 建议落地（低风险、复用现有纯函数）

| 步骤 | 内容 | 主要文件 | 验收 |
| --- | --- | --- | --- |
| S1 | 同步**预览面板**：把 `BangumiSyncPlan` 渲染成四段（一致 / 仅本地 → 待上传 / 仅远端 → 待下载 / 冲突 → 按优先级），每段可折叠、每行可勾选（默认全选，冲突项显示两边状态） | 新增 `ui/settings/SyncPlanSheet.kt`；`SettingsViewModel` / `SyncBackupSettingsContent` 接入 | 单测：计划 → 面板行的映射（含 0 条时的空态）；手动：勾选与「执行」只提交被勾的条目 |
| S2 | **演练模式**：把将要发出的请求按 `BangumiSyncPlan` 展开成只读列表（方法 / URL / 表单字段 / 静态检查），支持复制与导出 JSON；演练**绝不落库** | 新增 `data/sync/SyncDryRun.kt`（纯函数）+ 面板页签 | 单测：演练输出与真实执行路径共用同一份「操作 → 请求」映射（同一函数，行为不可能漂移）；演练后本地库无写入 |
| S3 | 映射缓存：把豆瓣条目 id 纳入 `subject_external_ids`（新增 provider），绑定后跨会话复用 | `SubjectExternalIdEntity`、`data/match`、`SubjectDetailViewModel` | 单测：写入/读取/幂等；迁移（**注意**：Room 迁移 DDL 必须与 `app/schemas` 导出的 `createSql` 逐字一致，且版本号 +1 后要补迁移测试两条链） |
| S4 | 自检补端点：豆瓣收藏页/表单字段端点加入 `Probes.kt`，沿用现有探针 UI | `data/probe/Probes.kt` | 单测：端点清单与描述；手动：失败时给出「登录问题」还是「选择器失配」的区分 |
| S5 | 限流档：给豆瓣写/翻页加档（写入 ≥1.2s、翻页 ≥0.8s 起步，照 dbsync 的实测值） | `util/SourceRateLimits.kt` | 单测：限速器对同 host 的等待计算 |

### 2.4 需要**你**拍板的部分：豆瓣写回

本仓库目前把豆瓣定位为**灰通道、默认关**（README：豆瓣未授权第三方抓取；若要分发建议移除）。dbsync 的写回能力意味着：用你的 Cookie 往豆瓣账号**写入**状态 / 评分 / 短评 / 标签。

- **读入方向**（豆瓣 → 本地导入，与现有 B 站/Steam 导入同构）：风险可控，可做。
- **写回方向**（本地 → 豆瓣）：风控/ToS/不可逆三项都更重，且短评写错不可回滚。若要做，建议强制三条：**默认关**、**必须演练**、**写前逐条确认 + 限流 + 不可写短评的独立开关**。

我没有替你决定，也不建议在没有明确同意前动这块。

---

## 3. izuko-tv 其它可移植项（P2）

| 候选 | 简要 | 判决与理由 |
| --- | --- | --- |
| 应用内更新（`domain/update/UpdateManager.kt`、`GitHubDownloadMirrors.kt`、`ui/remote/RemoteAppUpdate.kt`） | 查 GitHub Release → 提示 → 下载 APK → 调系统安装器；带镜像列表 | **可做**（中）。本仓库在仓库根目录就直接放 APK，更新源天然存在；需要 FileProvider + `REQUEST_INSTALL_PACKAGES` 权限 + 校验（sha256/size）。注意：Google Play 分发时该权限受限，本仓库走 GitHub 分发不受影响 |
| 首启连通性自检 + 镜像建议（`BangumiMirror*` 一组） | 首次打开检测 Bangumi 是否可达，连不上引导换代理/镜像 | **可做且便宜**（小）：`data/probe` 已经能打 HTTP 状态；本仓库已有「Bangumi 端点（官方 / 国内反代）」切换，缺的是「首次自动跑一遍并给建议」 |
| 帧性能诊断（`TvPerfDiagnostics` / `PerfAnalysis` / `TvPerfRecorder`） | 记录帧耗时、丢帧分析、健康度 | **可选**（中）。本仓库有 `docs/performance-audit.md` 与 `plans/` 的性能清单，但没有线上可看的帧诊断；如果继续做动效，这个面板能省下大量「凭感觉」 |
| 背景墙对比度（`TvBackdropContrast.kt`） | 按背景决定前景可读性 | **方法可借鉴**：本仓库已有 p80 亮度（`ImageBrightness.kt`），若做 §1 的 logo 对比度，顺手可以把文字/图标的可读性判定一起做扎实 |
| 启动 logo 动画（`TvStartupLogo.kt` + `TvStartupLogoPaletteMirror.kt`） | 冷启动 logo + 调色板镜像 | **不做**：本仓库的开屏动画已按你的要求移除（`MainActivity.kt:414` 明确记录「系统 SplashScreen 主题与衔接动画一并删除」），这类改动会被直接退回 |
| 屏保（`AniDreamService.kt`）、主屏幕频道（`TvHomeChannels.kt`）、界面缩放 50–250%（`TvPosterWallScale.kt`）、Web 控制台、播放器/弹幕/离线缓存/数据源订阅 | TV 形态与播放器能力 | **不建议**：本仓库 manifest 里 **leanback / TvProvider / DreamService / appwidget 命中 0**，是手机·平板单 Activity 应用；要吃得下这些等于另起一个 TV 模块（izuko 自己就是 `app/shared/ui-tv` 独立模块 + 6324 个文件的量级）。界面缩放在手机上有系统字号/密度，另做一套反而割裂 |

---

## 4. onetake 与 motion-web：工具链与方法学（P3）

### 4.1 性质

这两个都不是「App 功能仓库」，是 **Agent Skill**：
- **onetake**：做一镜到底发布片的产线 —— 把参考片拆成数字 → 三个概念选一 → **节拍表**（镜头长短相差 ≥4×，每个边界写明「什么活下来」）→ 一个 HTML + 动作库 + 真实运动模糊（180° 快门多采样在线性光下平均）→ 按事件配音 → **oracle 自动验收**（carry score；一律相同长度、没有静止段落、快动作无模糊、主体出画都会被判不合格）。
- **motion-web**：动效即材质的网页交互动效体系 —— 禁无意义 `opacity 0→1`、禁线性匀速；二阶弹簧阻尼、各向异性推力、11Hz 定格阶梯抖动；7 个自包含案例（绳索钟 / WebGL2 墨线群 / 粘性叠层唱片 / 滚轮视差轨道 / 硬切翻页 / Verlet 门帘 / 因果生态）；每个案例配无头 Chromium oracle。

### 4.2 三种可用的用法

1. **给 Niriko 做一条发布/功能演示片**（onetake 的正当用法）。现状：本机 `node` / `pnpm` 有，`ffmpeg` **没有**，playwright/chromium 也**没装**（onetake 的依赖：python3 + playwright + numpy/scipy/Pillow/matplotlib + ffmpeg + node）。所以这是一次**要装工具的独立任务**，不是改 App。收益：仓库里已经有 `screenshot/` 与 `docs/*prototype.html`，但**没有**一条能放进 README / 发布页的片子。
2. **借方法学补动效规范**。本仓库 `plans/` 已做过一轮很硬的动效审计（`plans/README.md`，M1 明确指出「9+ 套按压/揭示弹簧、9 个自造 tween 时长、令牌层零调用」）。onetake 的「每个边界必须有元素活下来」与 motion-web 的「禁线性 / 有阻尼有过冲 / 要有静止」正好可以写成一份**可检查的动效判据**，接在现有审计后面。这两件事方向一致，不冲突。
3. **借 1–2 个具体特效**（挑与现有代码同构的）：
   - **定格阶梯抖动（11Hz 硬切）** → 与已有 CRT 模式（`ui/animation/CrtOverlay.kt`，约 4 秒扫描线）天然一对；小改动、可 JVM 单测时间函数。
   - **Verlet 绳排**（`char-curtain`：列间零水平约束、指针掠过像撩帘） → 可做统计页**标签词云**的物理化；物理步进是纯函数，符合本仓库「计算器可单测」的约定。
   - **粘性叠层**（`press-stack`：多屏 sticky 后屏骑压前屏） → 可做作品库的「叠层书脊」视图；属于新视图，工作量偏大。
   - **滚轮视差轨道**（`wheel-rail`） → 平板/折叠屏的横向海报轨道（本仓库已有宽屏 dock 与折痕分栏）；中等。
   - **WebGL2 墨线群**（`ink-crowd`，MRT + 深度不连续勾线） → Android 侧要落成 AGSL/RuntimeShader；本仓库有 AGSL 玻璃着色器底子，但**Robolectric 下 SkSL 编译失败**（README B6 已证伪截图回归），做出来只能靠真机验收。**不建议先做。**
4. **不要把 oracle 直接理解成「像素级回归」**：本仓库已经证明离屏光栅化走不通（`DrawBackdropNode.onAttach` 里 AGSL/native SkSL 编译失败）。能落地的是**纯函数动效断言**（时间 → 数值），比如把 carry 拍成「边界两侧共享的视觉元素 id 集合非空」这种**结构断言**，而不是像素比对。

---

## 5. 许可与合规边界（必读，先于一切实现）

| 项目 | 许可 | 能不能抄代码 | 结论 |
| --- | --- | --- | --- |
| douban-bangumi-sync | **MIT** | **可以** | 借鉴需保留版权与许可声明（并入 `THIRD-PARTY-NOTICES.md`）。即便如此，它的代码是浏览器扩展（MV3 + DOM 解析），**照抄没用**，要按 Android 侧重写 |
| izuko-tv | **AGPL-3.0** | **不可以** | AGPL 有传染性：搬进 MIT 仓库会让整仓受 AGPL 约束。**只提炼行为与思想、自研实现、自写测试**（本文 §1 全部按此写法给出，`TvTitleLogo` 的注释、常量、命名都不要复制）。若只是「参考它量出来的 Prime Video 数字」，请自行复测一遍再落进注释 |
| onetake | **PolyForm Noncommercial 1.0.0** | **不可以（并入 MIT 仓库）** | 非商用可用；但把 `lib/motion.js` 之类搬进本仓库会造成许可冲突。当**外部工具**用（生成的片子是产出物，不构成代码衍生），商业用途另说 |
| motion-web | **CC BY-NC 4.0** | **不可以（并入 MIT 仓库）** | 同上：方法、判据、物理模型可用，代码不要搬 |

一句话：**思想无版权，代码有。本仓库保持 MIT 的前提是「参考、重写、自己测」。**

---

## 6. 建议执行顺序

| 优先级 | 事项 | 依赖 | 预估 |
| --- | --- | --- | --- |
| **P0** | 标题 logo 替代标题（§1.3 S1→S5，S6/S7 后置） | TMDb key（已有设置项）+ TMDb 绑定 | 中：1–2 会话 |
| **P1** | 同步「预览 + 勾选 + 演练」面板（§2.3 S1/S2） | 无（`BangumiSyncPlan` 已在） | 中：1 会话 |
| **P1'** | 豆瓣自检端点 + 限流档（§2.3 S4/S5） | 无 | 小 |
| **P2** | 应用内更新 / 首启连通性自检（§3） | 无 | 中 / 小 |
| **P3** | 动效规范（onetake carry + motion-web 反塑料感）落地到 `plans/`；挑 1 个特效（定格抖动或 Verlet 词云） | 需先定规范 | 中 |
| **P4** | Niriko 发布片（onetake，需先装 ffmpeg + playwright） | 工具链 | 独立任务 |
| 不做 | TV 屏保 / 主屏幕频道 / leanback 整包 / 界面缩放 / 播放器 / 开屏动画 / 豆瓣写回（待你拍板） | — | — |

---

## 7. 证据索引

- TMDb logos 已解析未使用：`app/src/main/java/com/otakup/niriko/data/remote/tmdb/dto/TmdbDtos.kt:181-195`；取图路径 `data/repository/TmdbRepository.kt:84,93`；端点与语言参数 `data/remote/tmdb/TmdbApiService.kt:70-80`；图片基址可镜像 `data/remote/tmdb/TmdbImageUrl.kt`
- 图像分析家底：`ui/common/ImageBrightness.kt`（p80 亮度）、`ui/common/CoverImageAnalysis.kt`（480px / 8MiB LRU / 并发合并 / 不允许硬件位图）
- 详情页结构：`ui/subject/SubjectDetailScreen.kt:523-538`（封面铺底 + layerBackdrop + pageCardGlassBackdrop）；部件开关 `ui/subject/DetailSectionId.kt:43`、`ui/settings/pages/DetailSectionsSettingsScreen.kt`
- 同步计划：`data/sync/bangumi/BangumiSyncPlan.kt`、`BangumiSyncPlanner.kt`、`BangumiSyncManager.kt`、`data/sync/SyncMergePolicy.kt`；UI `ui/settings/pages/SyncBackupSettingsScreen.kt`
- 探针与自检：`data/probe/ChannelProbe.kt`、`Probes.kt`、`GrayChannelRunner.kt`、`SearchChainProbe.kt`
- 限流：`util/SourceRateLimiter.kt`、`util/SourceRateLimits.kt`
- WebView 登录范式：`ui/bilibili/BilibiliSyncScreen.kt:320-400`、`plugin/bilibili/BilibiliWebBridge.kt`
- 动效现状与既有审计：`ui/animation/`（`NirikoMotionSpecs.kt` 六档弹簧、`CrtOverlay.kt`、`HalftoneOverlay.kt`、`RevealOnScroll.kt`、`SubjectCoverPath.kt`）、`plans/README.md`（M1 令牌不内聚、H5 reduce-motion 覆盖 8/170）
- 截图回归被证伪：README「已知限制 · 工程待办」（Robolectric native SkSL 编译失败，失败点 `DrawBackdropNode.onAttach`）
- 开屏动画已移除：`MainActivity.kt:414`
- 上游参考（外部）：izuko-tv 的 `TvTitleLogo*` 系列与 `TmdbTitleLogoPickTest`；douban-bangumi-sync 的 README「快速开始 / 能同步什么 / 隐私与安全」；onetake 与 motion-web 的 README 中文节

---

## 8. 覆盖缺口声明

1. **izuko-tv 未通读**：仓库 6324 个文件（含上游 Animeko 的 KMP 代码），我只读了 README、文件树，以及标题 logo 的五件套与四个测试。§3 里「屏保 / 频道 / 更新 / 诊断」的判断依据是 README 描述 + 文件路径，**没有逐行读实现**（尤其 `UpdateManager` 的签名校验与镜像策略、`TvPerfRecorder` 的采样口径）。
2. **douban-bangumi-sync 未读源码**：只读 README + 文件树。豆瓣侧的字段名、请求形态、写入通道（背景请求被拒时注入到已打开的豆瓣页面）都在它的 `src/adapters/` 里，真要照做必须先读那一层；本仓库按 Android 侧重写时，也要重新做一遍「读取收藏表单字段」的自检，不能假设字段名不变。
3. **onetake / motion-web 只读 README**：`SKILL.md`、`lib/motion.js`、7/11 个案例源码都没读（许可也不允许搬运）。§4.2 里对具体特效可行性的判断是**架构级判断**，不是实测。
4. **TMDb 区域字段未验证**：TMDb 图片对象是否带 `iso_3166_1`，本机没有 key，无法实测；izuko 的选图用例里有 CN/TW/HK 排序，但区域来源未追到底。
5. **无真机验收**：所有「观感」判断（手机尺寸下 logo 是否还认得出来、翻色阈值是否合适）都需要你或真机确认；JVM 单测只能覆盖规则与几何，覆盖不了观感。
6. **估时口径**：按本仓库既有的「一会话=一个可独立验收的批次」粗估，不含你 review 后的返工。
