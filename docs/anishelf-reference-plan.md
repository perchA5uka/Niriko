# AniShelf 借鉴 · 细化实施方案（Niriko）

> 参考项目：AniShelf（iOS，SwiftUI + SwiftData，TMDb 主源 + TVMaze 补源）。
> 本文基于其 6 张 App Store 截图与源码（DataProvider / Views / Network / ViewModels）提炼，
> 面向 Niriko 的 Android/Compose 落地。全部为**建议方案**，未经批准不动工。

---

## 总览：AniShelf 的设计语言

从截图可归纳的核心视觉模式：

1. **液态玻璃背景贯穿全页**：整页背景是随内容变化的柔和渐变或毛玻璃海报，所有卡片是白/半透明圆角浮层（浅色下贴近 iOS 原生材质），卡片圆角较大（详情页卡约 28dp，海报卡约 20dp）。
2. **信息用彩色胶囊徽标**：状态（在看=橙、看过=绿、想看=灰）、集数（S1E6=蓝）、评分（星=黄）、收藏（心形描边）。
3. **悬浮胶囊工具栏**：页面底部一条液态玻璃浮栏（视图切换 | 排序筛选 | 搜索）。
4. **多视图容器**：列表 / 海报网格 / 画廊大卡（多图拼贴）。
5. **统计概览**：总作品大卡 + 2x2 彩色状态卡 + 分类/时长明细。

---

## 一、收藏页三视图 + 徽标规范（最高优先，UI 红利最大）

参考截图：poster-grid-view（海报网格）、library-list-view（列表）、featured-library-card（画廊大卡）
源码：AnimeEntryCard.swift、LibraryView.swift（LibraryViewStyle: gallery/list）、LibraryToolbar.swift

在收藏页加视图切换（顶部或悬浮胶囊内），三种模式：

| 模式 | 卡片形态 | 徽标叠加 |
|---|---|---|
| 列表（现有，默认） | UniversalSubjectCard 横排大卡 | 保持现状 |
| 海报网格 | 2:3 竖版 CoverImage | 左上状态点、右上收藏心、底部进度条、左下评分星 |
| 画廊大卡 | 大头图或角色拼贴 + 底部标题 | 左上状态图标、右上收藏心、底部评分胶囊、卡头起止日期胶囊 |

- 状态胶囊配色（映射到 Niriko WatchStatus）：在看=橙、看过=绿、想看/搁置=灰。
- 评分胶囊：星 + 数字，浅黄底圆角。
- 徽标实现：复用 CoverImage + 新增 PosterGridCard（Box 叠 Overlay 徽标，保留 animateItem）。背景用现有 appleGlassCard。

工作量：新增 ui/library/PosterGridCard.kt + LibraryGalleryCard.kt + 视图切换状态（rememberSaveable 的 viewStyle），收藏页 LibraryScreenContent 加切换控件。

## 二、详情页统计卡网格

参考截图：anime-detail-overview 顶部三卡（评分/集数/时长，白玻璃圆角卡 + 彩色图标）；源码：EntryDetailStatGrid.swift（LazyVGrid 自适应 1-3 列，卡片按 EntryDetailStatKind 分派，点击跳转/弹 popover）。

Niriko 详情页在评分分布上方加一行统计小卡（白玻璃圆角配彩色图标）：我的评分/Bangumi 评分（星）、总集数或游戏时长、平均时长或类型（时钟）。卡片可点击跳转到对应区块。复用现有 appleGlassCard。

## 三、收藏管理 → 内联折叠编辑卡（视听管理，高价值）

参考截图：watch-management-sheet（折叠面板：分段状态选择 + 起止日期胶囊 + 笔记多行输入）；源码：AnimeEntryTrackingControls.swift。

Niriko 详情页「我的收藏」升级为页内折叠卡：
- 一段式状态选择：想看/在看/看过/搁置（FilterChip 或分段选择）。
- 开始/结束日期（可选新增字段，对应收藏表加 startDate/finishDate 可空列，含迁移 v20→v21）；日期复用现有 DatePickerDialogComponent。
- 笔记：多行输入（若不做 DB 迁移则先存 DataStore 每条目键）。
- 进度 + 评分保留现有。

## 四、统计页彩色概览卡

参考截图：library-stats-overview（总作品大卡 + 2x2 彩色状态卡 + 分类/时长明细）。

Niriko 统计页「收藏概览」从纯数字改为 AniShelf 式卡片组：总作品大卡（封面缩略 + 大数字）、2x2 彩色状态卡（想看/在看/看过/搁置各一色带图标）、分类明细（动画/漫画/游戏条数 + 总时长，复用 StatsCalculator 的 TypeDistItem/MonthlyStats）。卡片用 appleGlassCard 或浅色 tint 玻璃。

## 五、海报选择器（用户换封面，新功能方向）

参考：PosterSelectionView/PosterBrowserView/TMDbImageSelection（从源站取多张海报，用户选一张替换，含原语言海报优先 + matched geometry 预览）。

Niriko 新增「更换封面」入口：数据源用 Bangumi SubjectImages 多图候选；选择后本地写封面覆盖（新表 subject_cover_overrides 或 DataStore key，coverUrl 兜底优先读覆盖值），不污染元数据。预览动画用现有 SharedTransitionLayout 的 sharedElement。

## 六、放送时间可靠性分级角标（数据质量）

参考：docs/broadcast-time-feature/broadcast-availability-policy.md（决策矩阵：双源交叉验证 → 正常/预计/可能不可靠/不可用）；源码：TVMazeResolver.swift、EntryDetailBroadcast*。

统计页日历（放送信息模式）与详情页放送区块加角标：预计（无明确 airstamp 仅日期）、可能不准（多源打架）、待定（单源）。保留现有 Bangumi 主源，角标先基于单源无确认给待定；未来接第二放送源再按矩阵分级。

## 七、绑定流程保守化（数据质量）

参考：TVMazeResolver（自动只做 ID 通道；标题搜索必须用户确认后才写 TVMazeConfirmedMappingStore）。

AniList/VNDB 懒绑定改为：自动尝试 ID 通道命中才写绑定；标题匹配仅产候选供用户在详情页确认，不再自动写绑定表。降低错绑率。

## 八、批量操作（效率）

参考：LibraryBatchAction + LibraryView+MultiSelection（多选 → 批量改状态/删除；选择渲染与 store 解耦保持流畅）。

收藏页长按进入多选模式，多选后底部批量栏：批量改状态/删除/加标签。

## 九、分享卡可配置（小增强）

参考：AnimeSharingPreferences + SharingCardExportPipeline（分享前配置展示内容）。

把 Niriko 现有 ShareFlow 加配置抽屉：是否包含评分/进度/个人标签。

## 十、放送提醒通知（新功能，中等工作量）

参考：AiringReminderCoordinator + ios-recurring-notifications-qa.md。

基于日历放送数据 + WorkManager 周期检查 + 本地通知（NotificationCompat），提醒「在看」条目新集放送。

---

## 建议动工顺序

| 优先级 | 阶段 | 内容 | 风险 |
|---|---|---|---|
| 1 | A | 收藏页三视图 + 状态胶囊徽标 | 低 |
| 2 | B | 详情页统计卡网格 | 低 |
| 3 | E | 海报选择器（换封面） | 中 |
| 4 | D | 统计页彩色概览卡 | 低 |
| 5 | C | 收藏管理内联折叠卡（含日期/笔记） | 中 |
| 6 | F/G | 放送分级 + 绑定保守化 | 中 |
| 7 | H | 批量操作 | 中 |
| 8 | I | 分享卡配置 | 低 |
| 9 | J | 放送提醒 | 中 |

## 明确不做/不建议照搬

- iCloud Sync：平台绑定，Niriko 的 WebDAV 是正确的跨平台选择。
- 液态玻璃原生材质：iOS 26 专属（README 明示 26 以下不支持）；Niriko 的纯 Compose 自实现兼容 Android 8+，更通用，只需在视觉上贴近 AniShelf 的白色浅玻璃观感（浅色模式卡片可更白/更透）。
- SwiftData 迁移链：工程严谨但无直接可抄性。