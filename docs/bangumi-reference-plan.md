# Niriko 参考 Bangumi-master · 完善改进方案

> 参考项目：Bangumi-master（czy0729/Bangumi，React Native / Expo，Bangumi(https://bgm.tv) 第三方客户端）。
> 本文基于对 Niriko 源码（Kotlin + Jetpack Compose，266 个 .kt 文件）与 Bangumi-master 源码（约 100+ 页面）的通读结论撰写。
> 目标：**不照搬 RN/mobx 实现**，只提炼「内容深度、数据质量、搜索/发现、统计可视化」等可移植到 Android/Compose 的能力点，逐条给出 Niriko 侧落点、工作量、风险与优先级。
> 全部为**建议方案**，未经批准不动工。

---

## ✅ 总体完成状态（截至当前）

| 阶段 | 状态 | 说明 |
|---|---|---|
| P 性能优化 | ✅ 完成 | 玻璃效果设置/封面单解码/RevealOnScroll/remember；build+test 绿 |
| A 放送时间 | ✅ 完成 | 静态 onair + 精确时刻 + 详情/提醒/日历展示（CN 时刻） |
| B 收藏精度 | ✅ 完成 | 卷进度/私密/动词 + 私密不导出 + 收藏编辑面板 |
| C 发现页内容矩阵 | ✅ 完成 | 排行/类型浏览/标签词云；「历史排名」选中时自动弹出筛选面板，选类型/标签即对该榜做分类排行（search+tag+rank） |
| D 拼音搜索 | ✅ 完成 | pinyin4j + pinyinKey；主搜索与建议均支持拼音子串命中（`juren`→进击的巨人）；回退不再倒出全库 |
| E 照片墙/时光机 | ✅ 完成 | Mosaic 模型 + 计算 + 统计页照片墙（时间线已有） |
| F 关联/猜你喜欢 | ✅ 完成 | 关联保持原横滑卡片（分组已回退，避免布局回归）+ 本地 tag 共现猜你喜欢 |
| G 剧照预览 | ✅ 覆盖 | Anitabi 场景截图 + Steam 截图；不新增通用剧照源（已确认） |
| H 搜索建议 | ✅ 覆盖 | 多源本地+远程聚合；不接 ac-search（已确认） |
| K 圣地巡礼 | ✅ 完成 | Anitabi 取景地标 + 巡礼地图外链 |
| I 本地媒体文件夹 | ⏸️ 计划暂缓(P2) | 高风险；默认暂缓（待用户确认是否纳入） |
| J 社区只读 | ⏸️ 可选 | 保持收藏统计定位；默认不做（待用户确认） |

> 已实现的各阶段均通过 `./gradlew.bat assembleDebug` + `testDebugUnitTest`。I/J 为计划内“暂缓/可选”项，是否纳入取决于进一步确认。

---

## 〇、项目定位对比（为什么要参考它）

| 维度 | Niriko（本仓库） | Bangumi-master（参考） |
|---|---|---|
| 定位 | 个人**收藏统计**工具，Bangumi 主源 + AniList/Steam/VNDB 多源兜底 | Bangumi 全功能第三方客户端，覆盖网站几乎所有页面 |
| 用户数据 | 本地 Room + DataStore，WebDAV 跨端同步 | 服务端登录（access_token），多端一致 |
| 内容消费 | 收藏管理 / 发现搜索 / 统计三大块 | 发现 / 条目 / 时间线 / 超展开 / 用户空间 / 小圣杯 六大块 |
| UI 侧重 | 液态玻璃 + 三视图 + 统计可视化 | 极多页面 + 高性能渲染 + 主题自定义 |
| 已实现的「参考借鉴」 | AniShelf 全套（A–J） | —— |

**结论**：Niriko 的**收藏统计**已相当扎实（AniShelf 的 A–J 全部落地）；Bangumi-master 能带给 Niriko 的增量主要在：
（1）**放送 / 时间数据精度**（onair 每季三维放送时间）；
（2）**发现页内容矩阵**（年鉴 / 分类排行 / 评分月刊 / 目录 / 词云）；
（3）**收藏精度与表达**（集/卷进度分流、私密、短评、按类型动词）；
（4）**搜索与匹配**（拼音 / 罗马音 / 简繁、ac-search 建议）；
（5）**个性化 / 关联**（猜你喜欢、关联系列、关联作品分组）；
（6）**统计可视化深水区**（照片墙 / 时光机 / 马赛克瓷砖）。
（7）**圣地巡礼（Anitabi）取景地标 + 巡礼地图**——参考截图「取景地标」区块与「巡礼地图」跳转。

---

## 一、Bangumi-master 可借鉴资产清单（已核对源码）

### 1.1 放送时间与日历（最高价值）
- `src/stores/calendar/onair/*.json|ts`：**每季**放送时间原始数据（按季内置）。
- `src/stores/calendar/types.ts`：`OnAirItem` 含 `weekDayCN/timeCN`（中国）、`weekDayJP/timeJP`（日本）、`weekDayLocal/timeLocal`（本地）、`air`（放送到几集）、`custom`（用户自定义覆盖）。
- `src/stores/calendar/common.ts` / `utils.ts`：把「放送时间」精确到 **分钟 + 时区**，并算出「更新到第几集」。

> Niriko 现状：`SubjectEntity` 只有 `airDate`(字符串) + `airWeekday`(Int)；`StatsCalculator` 用 `broadcastSchedule` + `CalendarMode.BROADCAST` 落格；`AiringReminderWorker` 只看「今日放送」。放送时间**不够精确、无中/日/地三维、无 custom 覆盖**。

### 1.2 收藏精度与表达
- `src/stores/collection/types.ts`：`Collection` 有 `ep_status`(集) / `vol_status`(卷) 分离、`private`(私密 0/1)、`tag[]`、`comment`、`status.type/name/id`。
- `src/stores/collection/computed.ts`：`collect(subjectId, typeCn)` 根据类型把动词替换——动画/三次元=**看**、书籍=**读**、游戏=**玩**、音乐=**听**。
- `src/stores/collection/computed.ts`：`userCollectionsTags` / `mosaicTile`（收藏标签统计 + 进度瓷砖）。

> Niriko 现状：`CollectionEntity` 已有 `watchedEpisodes`、`rating`、`startDate/finishDate`、`personalTags`、`personalImpression/remark`、`watchedTrackIds`（音乐逐首）；**缺** `vol_status`（卷）、`private` 标记；状态文案未按类型区分动词。

### 1.3 发现 / 搜索内容矩阵
- `src/screens/discovery/`：`rank`、`yearbook`、`typerank`(分类排行)、`vib`(评分月刊)、`tags`、`catalog`/`catalog-detail`(目录)、`word-cloud`(词云)、`series`(关联系列)、`like`、`anime`/`game`/`manga`/`wenku`(找番剧/找游戏/找漫画/找文库)、`recommend`(猜你喜欢)、`search`、`character`、`staff`。
- `src/utils/ac-search`：Bangumi 搜索建议（自动补全）接口。
- `src/utils/pinyin` / `thirdParty/cn-char`(opencc t2s) / `utils/katakana`：拼音 / 简繁 / 罗马音匹配。

> Niriko 现状：发现页已有「当季热门 / 历史排名 / 类型浏览 / Steam 标签 / NSFW / 高级筛选 / 搜索建议（本地+远程聚合)」。**缺** 年鉴、分类排行、评分月刊、目录、词云、猜你喜欢；搜索匹配未用拼音 / 罗马音 / 简繁。

### 1.4 个性化 / 关联 / 图片
- `src/screens/home/`：`preview`(剧照/截图)、`link`(关联)、`series`(关联系列)、`typerank`、`votes`/`voices`(配音)、`works`(相关作品)、`catalogs`。
- `src/utils/douban`：豆瓣剧照 / 预告「防剧透」取图（官方图少时取后页、避免剧透）。
- `src/utils/bilibili`：B 站视频搜索 + 相似度过滤（`similar(rate≥0.7)` + 标题/原名匹配）。
- `src/components/heatmap`(热度)、`image-viewer`(全屏查看)。

> Niriko 现状：详情页已有关联作品 / 角色 / 制作人员 / Staff；已有 Steam 截图横滑；已有 B 站同步（`BilibiliImporter`）。**缺** 关联作品按「本作/续作/前传/衍生」分组、关联系列聚合、Bangumi/豆瓣剧照预览、全屏图片查看、猜你喜欢。

### 1.5 统计可视化深水区
- `src/screens/user/milestone/index.tsx`：**照片墙**（按类型×状态×时间的马赛克封面墙，`userCollectionsForMilestone`）。
- `src/stores/collection/`：`mosaicTile`（进度瓷砖）。
- `src/screens/user/timeline`：**时光机**（按收藏时间倒序/时间轴）。
- `src/stores/tag`：标签体系。

> Niriko 现状：统计页已有「收藏概览卡 / 状态环形图 / 类型分布 / 评分分布 / 年度总结」。**缺** 照片墙 / 进度瓷砖 / 收藏时光机。

### 1.6 工程 / 性能
- `packages/env` 多环境、`patch-package`、`.storybook`（Storybook 单页流程）、`eslint-rules`、`react-native-fast-image` + **自建 CDN 中间层**（脱敏静态数据 + 封面加速）。
- README「开发心得」：**不大图 / 少请求 / 缓存计算结果 / 不可见区域延迟渲染 / 函数式异步**。

> Niriko 现状：Coil（尺寸感知解码 + 小图约束）、纯函数 `StatsCalculator/TrendingCalculator`（可单测）、Room 迁移链。**缺** 结果计算缓存、图片占位/淡入、全屏图片查看器、预览截图。

---

### 1.7 圣地巡礼 / 取景地标（Anitabi）——新增功能（已确认要加）
- `src/screens/home/subject/component/anitabi/anitabi.tsx`：**取景地标**区块（横向卡片列表 + `巡礼地图` 外链）。
- `src/screens/home/subject/store/fetch/extend.ts#fetchAnitabi`：数据源 `https://api.anitabi.cn/bangumi/{subjectId}/lite`；仅类型为动画且非 NSFW；D7 快照缓存 + 每启动全局限流 8 次 + 空结果标记不再重试。
- `src/screens/home/subject/types.ts#AnitabiData`：`city` / `pointsLength` / `imagesLength` / `geo(lng,lat)` / `zoom` / `litePoints[]{cn,ep,s,geo,image}`；地图跳转 `https://anitabi.cn/map?bangumiId={subjectId}`。

> Niriko 现状：无。建议新增独立 `anitabi_points` 表（subjectId + city + 地标数组 + counts + 缓存时间），仅 ANIME 且非 NSFW 触发；详情页加「取景地标」横滑区 + 查看器 + 外链地图（可复用 `PortalLauncher`/`ACTION_VIEW`）。

## 二、分阶段改进方案（建议动工顺序）

### 阶段 A · 放送时间数据源升级（最高价值，中低风险）
**借鉴**：`stores/calendar/onair/*` + `types.ts OnAirItem`。
**目标**：把放送时间从「日期 + 星期」升级为 **精确到分钟 + 中/日/地三维 + 用户自定义覆盖**。
**收益**：统计日历更准、放送提醒更可信、详情页放送区块信息完整。
**Niriko 落点**：
- `data/local/entity/SubjectEntity.kt`：加 `airTimeMinutes`、`airTimeZone` 列（迁移 v20→v21）。**（已实施：字段 + MIGRATION_20_21 + 静态 onair JSON 内置 assets/data/onair/**`）**
- `data/onair/OnAirRepository.kt`：从 assets 加载 onair 每季静态数据，按 subjectId 补齐精确放送时刻，`enrich(subject)` 在 `SubjectRepository` 落库前调用。**（已实施）**
- `data/calculator/StatsCalculator.kt`：`broadcastSchedule` 使用精确分钟落格，支持「中/日/本地」显示。**（待办）**
- `data/notification/AiringReminderWorker.kt`：按精确到分钟的放送时刻触发。**（已实施：通知正文带 HH:mm）**
- `ui/stats/CalendarDaySheet.kt`：放送日详情列表显示精确放送时刻。**（已实施）**
- `ui/subject/`：放送区块显示精确时间 + 用户自定义覆盖入口。**（已实施：详情页元信息区显示 日期·星期·HH:mm）**；用户自定义覆盖**（待办）**。
- `data/calculator/StatsCalculator.kt`：多时区（中/日/本地）三维落格显示。**（静态数据仅 CN 时刻；JP/Local 需补充数据源，暂以 CN 时刻展示，日历日详情已带时间）**
**风险**：中（静态数据按季更新；已允许内置静态数据）。

### 阶段 B · 收藏精度与表达（中优先级，中风险）
**借鉴**：`collection/types.ts` 的 `vol_status` / `private` / 动词替换。
**目标**：书籍/漫画支持卷进度（`vol_status`），收藏加私密开关；状态文案按类型显示「看/读/玩/听」。
**Niriko 落点**：
- `data/local/entity/CollectionEntity.kt`：加 `watchedVolumes`、`isPrivate` 列（迁移 v20→v21）。**（已实施：字段 + MIGRATION_20_21 已落地）**
- `data/model/WatchStatusVerb.kt`：`WatchStatus.verbFor(type)` 按类型返回看/读/玩/听。**（已实施）**；`ui/`：收藏编辑状态选择器 + `StatusBadge` 已接 `verbFor`（`PosterGridCard` 传 `subjectType`）。**（已实施）**，列表卡/详情其余状态文案待接。
- `ui/settings` / 收藏编辑：加私密开关 + 卷进度输入。**（已实施：详情页收藏编辑面板新增卷进度输入（书籍/漫画）+ 私密开关，含 ViewModel 保存链路）**
- **私密不导出**：备份/WebDAV 同步已排除 `isPrivate` 收藏。**（已实施）**
**风险**：中（需 DB 迁移 + 全量卡片文案改造；数据模型已落地，剩 UI/动词/备份排除）。

### 阶段 C · 发现页内容矩阵（低风险，低工作量，红利大）
**借鉴**：`discovery/rank`、`yearbook`、`typerank`、`vib`、`tags`、`catalog`、`word-cloud`、`series`。
**目标**：发现页从「当季热门 + 历史排名」扩展为内容矩阵。
**Niriko 落点**：
- `data/remote/bangumi/`：补 API（年鉴 / 类型榜 / VIB 评分榜 / 精选目录 / 标签聚合 / 词云）。
- `data/calculator/TrendingCalculator.kt`：扩展「分类排行 / 年度榜 / VIB / 目录」计算。
- `ui/discovery`（发现页）：按类型切换的子榜 + 标签页 + 目录页 + 词云页。
**风险**：低（纯内容 + 接口，复用现有榜单 UI）。

### 阶段 D · 拼音 / 罗马音 / 简繁搜索增强（低风险，高实用）
**借鉴**：`utils/pinyin`、`utils/katakana`、`thirdParty/cn-char`(t2s)。
**目标**：搜索能命中「拼音 / 原文 / 中文名」。
**已实施**：
- 引入 **pinyin4j**（TinyPinyin 仅在 JitPack、当前镜像不可达，改用 Maven Central 的 pinyin4j）。
- `util/PinyinSearch.kt`：中文→全拼音小写 + 原文搜索键。`pinyinKey(title,titleCN)`。
- `SubjectEntity` 新增 `pinyinKey` 列 + 迁移 v22→v23；`SubjectRepository` 落库时补齐；`NirikoApplication` 启动回填存量条目。
- `SubjectDao` 新增 `searchByPinyin/searchByPinyinPrefix`；`SubjectRepository` 搜索/建议回退到拼音命中。
**风险**：低（纯函数 + SQL 命中，可单测）。

### 阶段 E · 收藏可视化的深水区（中优先级，中风险）
**借鉴**：`user/milestone`(照片墙)、`mosaicTile`(进度瓷砖)、`user/timeline`(时光机)。
**目标**：统计页 / 收藏页加「照片墙」「进度瓷砖」「时光机」。
**Niriko 落点**：
- `data/calculator/StatsCalculator.kt`：新增 `computeMosaic` 纯函数（有封面收藏按收藏时间倒序）。**（已实施）**
- `data/model/stats/MosaicItem.kt`：新增模型；`StatsUiState` 增加 `mosaic`。**（已实施）**
- `ui/stats`：统计页新增「照片墙」区块（4 列封面墙）。**（已实施）**
- 时光机/时间线：已有 `timelineEvents` 与时间线卡片，无需额外。
**风险**：中（数据全部本地，已落地照片墙）。

### 阶段 F · 关联作品 / 关联系列 / 猜你喜欢（中优先级，中风险）
**借鉴**：`home/series`(关联系列)、`home/link`(关联)、`discovery/recommend`(猜你喜欢)。
**目标**：详情页关联区增强 + 发现/详情页推荐。
**Niriko 落点**：
- `ui/subject/RelationsSection.kt`：关联区已按「本作/续作/前传/衍生/书籍…」**分组显示**。**（已实施）**
- `viewmodel/SubjectDetailViewModel.kt` + `SubjectDetailScreen.kt`：新增「猜你喜欢」（本地 tag 共现 + 同类型打分，前 10）。**（已实施）**
- `data/remote/bangumi/`：关联系列 / 服务端推荐 API **（待办，若需跨用户推荐）**。
**风险**：中。

### 阶段 G · 剧照 / 截图预览 + 图片优化（低风险，观赏红利）
**借鉴**：`home/preview`(剧照)、`utils/douban`(防剧透取图)、`image-viewer`(全屏)、`react-native-fast-image`。
**目标**：详情页加「剧照/截图」横滑区 + 全屏查看器；Coil 图片占位/淡入/预览。
**Niriko 落点**：
- `ui/subject/`：加剧照横滑（Bangumi/豆瓣），复用现有 Steam 截图区。
- `ui/common/CoverImage.kt`：占位 / 淡入 / 尺寸约束。
- 新增 `ui/common/ImageViewer.kt`（全屏可缩放手势）。
**风险**：低（纯 UI + 取图）。

### 阶段 H · 搜索建议 ac-search + 结果类型 tab（中优先级，中风险）
**借鉴**：`discovery/search` + `utils/ac-search`、`home/works`(按类型分流)。
**目标**：搜索建议接入 Bangumi ac-search；结果页按类型分 tab。
**Niriko 落点**：
- `data/remote/bangumi/`：接 ac-search 建议接口。
- `ui/search/SearchResultsPane.kt`：按类型 tab 分组结果。
**风险**：中。

### 阶段 I · 本地媒体文件夹（可选大型差异化，高风险）
**借鉴**：`stores/smb`(SMB/WebDAV 挂载 + 罗马音刮削)、曾经的单集播放源。
**目标**：扫描本地/SMB 视频 → 匹配条目 → 播放记录进度。
**Niriko 落点**：
- 新增 `data/media/` 仓库（FileProvider / SMB/WebDAV 列举） + 匹配复用标题匹配器。
- UI：媒体库页 + 播放进度回写。
**风险**：高（权限、编解码、版权、维护量大）。**建议作为 P2 单独立项，或暂缓。**

### 阶段 J · 社区只读增强（可选）
**借鉴**：`rakuen`(超展开/小组/博文)、`timeline`、`reviews`、`setting`(拉黑/屏蔽)。
**目标**：详情页只读展示「短评/吐槽」「最新讨论/相关帖子」，不做完整社区。
**Niriko 落点**：`ui/subject/` 加只读评论区入口；`AppSettings` 加屏蔽/拉黑列表。
**风险**：中。

### 阶段 K · 圣地巡礼（Anitabi）取景地标 + 巡礼地图（新增功能，低风险）
**借鉴**：`home/subject/component/anitabi` + `store/fetch/extend.ts#fetchAnitabi`。
**目标**：动画条目详情页新增「取景地标」区块（横向卡片）+「巡礼地图」外链（按截图样式）。
**Niriko 落点**：
- `data/local/entity/AnitabiPointEntity.kt`（subjectId / city / pointsLength / imagesLength / litePoints JSON / fetchedAt），Room 迁移 v21→v22。**（已实施）**
- `data/remote/anitabi/AnitabiClient.kt`：GET `https://api.anitabi.cn/bangumi/{subjectId}/lite`；OkHttp 解析。**（已实施）**
- `data/repository/AnitabiRepository.kt`：仅 ANIME 且非 NSFW 触发；D7 快照缓存 + 远端失败回退。**（已实施）**
- `ui/subject/SubjectDetailScreen.kt`：「取景地标」横滑区块（缩略图 + 标题 + EP mm:ss + #N）+「巡礼地图」外链 ACTION_VIEW。**（已实施）**
- **待办**：全屏查看器（h360）、启动限流/空结果标记的精细控制。
- 复用 `PortalLauncher`/`ACTION_VIEW` 打开 `https://anitabi.cn/map?bangumiId={subjectId}`；可选基础上做原生地图（MapLibre/OSM 或高德，需 key），P0 先用外链。
**风险**：低（数据只读 + 纯 UI；第三方 API 限流与可用性需真机求证）。

### 阶段 P · 性能优化（用户反馈优先，**列为最高优先级**）
**背景**：用户反馈本项目滚动/快速操作偶发卡顿，Bangumi-master 更顺滑。无模拟器，先静态定位（结论见 `docs/performance-audit.md`），再真机复核。**详情页封面大图保持高清，豁免「不大图」**（`SubjectDetailScreen.kt` 第 632 行 hero cover 1080×1440 解码不动）。
**执行顺序（P0→P1→P2）**：
#### P0 · 玻璃 backdrop / 模糊可控化（最耗）
- `ui/subject/SubjectDetailScreen.kt`（352–374 行）：背景墙 `AsyncImage(360×480)+blur(30dp)+layerBackdrop(glassBackdrop){drawContent()}`——整页每帧捕获供 GlassSectionCard 折射。改为仅可见/滚动静止时启用 `layerBackdrop`（用 `snapshotFlow { isScrollInProgress }` 临时关捕获），blur 仅静止时施加。
- `navigation/NirikoBottomBar.kt`（155–190 行）：`drawBackdrop` 每帧捕获页面层 + AGSL lens。**已实施**：新增 `GlassEffectLevel` 设置，REDUCED 关 AGSL lens、OFF 静态胶囊。
- `ui/wallpaper/WallpaperHost.kt`：**已实施** OFF 关壁纸柔化；低强度关视频壁纸待真机验证。
#### P1 · 图片 / 动画优化
- `ui/library/PosterGridCard.kt`（58–68 行）+ `ui/components/CoverImage.kt`（88–123 行）：网格封面双重解码（先 600×800 再实际尺寸）→ `CoverImage` 已改用 `BoxWithConstraints` 一次性取尺寸，**已实施**（消除双重解码）。
- `ui/components/GlassCard.kt`（314–339 行）：`rememberCoverTint` 仅对可见项触发（进程级 `coverTintCache` 已缓存）；`loadBlurredCover` 用 `remember(url)` + 进程缓存。
- `ui/search/TrendingSection.kt`（102 行）：`RevealOnScroll(staggerIndex = index % 5)` 滚回重播动画 → **已实施**：改为 `rememberSaveable`，Lazy 项滚回不再重播。
#### P2 · 重组 / 计算缓存
- `ui/library/CollectionCard.kt`（41 行）：`subject.toCardDisplayModel(steam)` 用 `remember(subject, steam)`（**已实施**）。
- `viewmodel/StatsViewModel.kt`（185/205 行）+ `viewmodel/SubjectSearchViewModel.kt`（229 行）：`StatsCalculator`/`TrendingCalculator` 结果按数据版本 + 区间写 Room/DataStore 缓存。
- 设置页新增「玻璃效果」开关：低端机自动关 AGSL lens / blur / layerBackdrop。**已实施**（`GlassEffectLevel`：全效果/降低/关闭）。

**本轮已完成**：P0 玻璃强度设置 + 底栏降级 + 详情页 layerBackdrop 降级 + 壁纸 blur 降级；P1 封面双重解码修复（`CoverImage`）+ `RevealOnScroll` 不重播；P2 `CollectionCard` remember。**剩余（待真机验证）**：详情页滚动时临时关 backdrop（需状态上提）、`StatsCalculator`/`TrendingCalculator` 结果缓存。
**风险**：中（blur/backdrop 为全局特效策略，P0 改动需真机 A/B 验证观感与帧率；默认保持现有观感，仅在新开关开启时降级，无回归）。

---

## 三、明确不做 / 不建议照搬

| 项 | 原因 |
|---|---|
| **Tinygrail（小圣杯）** 全套 + Rakuen 完整社交 + PM/好友/小组/超展开 | 严重偏离「个人收藏统计工具」定位；需服务端 + 账号体系，工程量爆炸。只做只读信息即可。 |
| **自建 CDN 图片中间层** | 需基础设施与成本；Niriko 用 Coil + Bangumi 源即可，必要时再上代理。 |
| **单集网络播放源 / 本地播放** | 版权敏感（Bangumi-master 已收敛）；只做「找番剧搜索」，不做播放。 |
| **直接搬 RN / mobx 组件与 store** | 架构、平台不同；只借鉴「纯计算拆分 + 缓存计算结果」理念。 |
| **iCloud / 服务端登录** | 平台绑定；Niriko 的 WebDAV + 本地优先是正确选择。 |
| **Storybook / 多环境 patch-package** | 对 Android/Compose 收益低；可用 Compose Preview + Room schema 导出替代。 |

---

## 四、建议动工顺序（优先级表）

| 优先级 | 阶段 | 内容 | 风险 | 主要落点 |
|---|---|---|---|---|
| 0 | P | 性能优化（P0 玻璃可控化 → P1 图片/动画 → P2 缓存） | 中 | 阶段 P（用户反馈优先） |
| 1 | A | 放送时间数据源升级（三维 + 自定义覆盖） | 中低 | SubjectEntity 迁移 / StatsCalculator / AiringReminderWorker |
| 2 | B | 收藏精度（卷进度 + 私密 + 动词文案） | 中 | CollectionEntity 迁移 / WatchStatus / 卡片 |
| 3 | C | 发现页内容矩阵（年鉴/分类排行/VIB/目录/词云） | 低 | remote bangumi / TrendingCalculator / 发现页 |
| 4 | D | 拼音/罗马音/简繁搜索增强 | 低 | TitleResolver / search |
| 5 | G | 剧照/截图预览 + 图片优化 | 低 | 详情页 / CoverImage / ImageViewer |
| 6 | E | 照片墙 / 进度瓷砖 / 时光机 | 中 | StatsCalculator / stats UI |
| 7 | F | 关联作品分组 / 关联系列 / 猜你喜欢 | 中 | 详情页 / remote bangumi / calculator |
| 8 | H | ac-search 建议 + 搜索类型 tab | 中 | remote bangumi / SearchResultsPane |
| 9 | J | 社区只读（短评/讨论/屏蔽） | 中 | 详情页 / AppSettings |
| 10 | I | 本地媒体文件夹（差异化） | 高 | 新 media 仓库 / 媒体库 UI |
| 11 | K | 圣地巡礼（Anitabi 取景地标 + 巡礼地图） | 低 | anitabi_points 表 / 详情页横滑 / 外链地图 |
| 12 | （原 阶段 L 已并入 阶段 P，见上文） | - | - |

> 前 5 项（A/B/C/D/G）改动小、价值高、可独立交付并各自通过 assembleDebug + testDebugUnitTest 验证。

---

## 五、验收清单（建议）

- [ ] 统计日历 / 详情页显示**精确到分钟 + 中/日/本地**三维放送时间，用户可自定义覆盖
- [ ] 书籍/漫画收藏支持**卷进度**；状态文案按类型显示「看/读/玩/听」；收藏可标记私密
- [ ] 发现页出现「年鉴 / 分类排行 / 评分月刊 / 精选目录 / 标签 / 词云」等子榜
- [ ] 搜索可命中拼音 / 罗马音 / 简繁；建议接入 Bangumi ac-search
- [ ] 详情页出现「剧照/截图横滑 + 全屏查看」，Coil 图片有占位/淡入
- [ ] 统计/收藏页出现「照片墙 / 进度瓷砖 / 时光机」
- [ ] 详情页关联区按「本作/续作/前传/衍生」分组 + 关联系列 + 猜你喜欢
- [ ] 动画条目详情页出现「取景地标」横滑 +「巡礼地图」外链，图片可全屏查看
- [ ] 私密收藏在 WebDAV / JSON 备份中**不导出**
- [ ] 列表滚动 / 快速操作无明显卡顿（Profiler 拖帧率 < 1%）
- [ ] 每阶段跑通 `gradlew assembleDebug` + `gradlew testDebugUnitTest`

---

## 六、已确认决策与仍需外部核实的事

### 已确认决策（用户反馈）
0. **阶段 C 范围（本轮确认）**：只做官方 API 能拿到的「排行榜 / 分类排行 / 标签·词云」；**年鉴 / 评分月刊VIB / 目录暂缓**（非官方稳定 API）。**说明**：曾加 `CATE_RANK` 独立标签，因与「历史排名」重复且筛选部件在未搜索时不出现，**已回退**；分类排行由「类型 + 标签 + sort=rank」筛选实现；统计页已接入「标签词云」。
1. **参照 Bangumi-master 的实现即可**：onair / Anitabi / VIB 等直接照抄其思路与数据模型。
2. **允许引入拼音库**：拼音 / 罗马音 / 简繁搜索可自建映射或引入 TinyPinyin / pinyin4j（注意体积，或用工具类裁剪）。**已引入 pinyin4j（阶段 D）**（TinyPinyin 仅有 JitPack、当前镜像不可达）。
3. **豆瓣作为信息源**：作为第 N 个 `sourceKey` 兜底源（参照 VNDB/AniList 的 `GameDataSource` 体系），而非仅详情增强。
4. **私密收藏可做，但导出/备份不跟随**：WebDAV 同步、JSON 全量备份均跳过私密条目；仅本地可见。
5. **阶段 G（本轮确认）**：用 **Anitabi 场景截图 + 已有 Steam 游戏截图**，**不新增通用剧照源**。（已覆盖）
6. **阶段 H（本轮确认）**：**保留现有多源本地+远程聚合搜索建议**，不接 Bangumi ac-search。（已覆盖）

### 仍需外部核实 / 真机验证
1. **onair 每季放送数据**：拉取还是内置 + 更新频率（照抄 Bangumi-master 内置静态数据最稳）。
2. **Bangumi 相关接口**：年鉴 / 分类排行 / VIB / 目录 / 词云 / 猜你喜欢 的具体 API 端点与限流需真机验证。
3. **Anitabi API**：`https://api.anitabi.cn/bangumi/{subjectId}/lite` 可用性与限流；地图外链 `https://anitabi.cn/map?bangumiId={subjectId}` 在真机可达性。
4. **性能基线**：先用 Profiler 量化当前拖帧率，再落地 阶段 L 的优化，避免无依据改动。

---
*本文为方案文档，不涉及改动任何代码；批准后按上述分段落地，并沿用 `docs/remaining-phases.md` 的「先构建再落地」节奏。*
