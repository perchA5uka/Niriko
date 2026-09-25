# Niriko 阶段进度与剩余方案

> 本会话已完成全部阶段（含 G/J）。接续时先跑 `assembleDebug + testDebugUnitTest` 确认基线，本会话末已通过。本文档记录已完成清单与实施要点。

## 已完成（全部 assembleDebug + 单元测试通过）

- **A** 收藏页三视图（列表/海报网格/画廊大卡）+ 彩色徽标体系 + 视图切换（Screens.kt / PosterGridCard / LibraryGalleryCard / LibraryViewStyle / Badges）
- **B** 详情页统计卡网格（DetailStatCard）
- **C** 收藏编辑面板升级：分段状态选择器 + 日期胶囊 + 抛弃状态（SubjectDetailScreen CollectionEditSheet / DatePill）
- **D** 统计页彩色概览卡（StatsOverviewSection）
- **E** 更换封面（CoverOverrideStore / CoverImage(subjectId) 覆盖优先 / CoverPickerDialog）
- **I** 分享重构为 AniShelf 海报风（SharePosterCard / ShareBitmapHost.renderPoster / SharePreviewSheet 预览+配置+分享）
- **H** 收藏批量操作（ViewModel batchUpdateStatus/batchDelete + 收藏页多选/批量栏）
- **F** 日历放送可信度标注（CalendarCard CalendarLegend）
- **G** 绑定保守化：AniList/VNDB 取消标题自动写库，标题匹配仅产候选，绑定仅靠详情页手动确认（AniListRepository/VndbRepository 移除 matchAndBind，更新 NirikoApplication/SubjectDetailViewModel 调用方）
- **J** 放送提醒通知：WorkManager 每日周期 Worker（网络日历优先+离线本地字段回退）+ NotificationCompat + 设置开关 + 启动请求权限 + 开机恢复（androidx.work:work-runtime-ktx:2.10.0）
- **修复** 画廊共享元素、编辑面板抛弃状态、分享 Sheet 滚动重叠/预览限高

## 已实现 · G 绑定保守化（本会话）

> 关键决策（用户确认）：当前数据模型下 bangumi 词条无已知 anilistId/vndbId 映射 → 彻底取消标题自动写库；候选+手动绑定为唯一写绑定入口。原 `matchAndBind` 已从 AniListRepository/VndbRepository 移除。

目标：把 AniList/VNDB 的自动标题匹配改为「自动仅 ID 通道 + 标题候选需用户确认」，降低错绑（参考 AniShelf TVMazeResolver）。

### 现状（需阅读确认）
- AniListRepository / VndbRepository：详情页懒绑定、启动批量自动绑定的自动匹配（≥0.7 置信度直接写绑定表）
- SubjectDetailViewModel：loadAniListSupplement / loadVndbSupplement，已有候选区（AniListCandidateSection / VndbCandidateSection）与 onBindAnilist/onBindVndb 手动确认入口

### 改动点
1. 自动绑定流程改为：只尝试 ID 通道（AniList 若已知 anilistId 映射、VNDB 若已知 vndbId）命中才写绑定；标题匹配仅产出候选列表，不写库。
2. ViewModel 自动懒绑定：去掉自动写绑定的标题匹配分支，仅加载候选到 state（复用现有候选 UI）。
3. 保留详情页候选区手动绑定按钮（onBindAnilist/onBindVndb）作为唯一写绑定入口。
4. 回归：详情页打开不自动错绑；候选仍可见可点。

## 已实现 · J 放送提醒通知（本会话）

> 关键决策（用户确认）：今日放送判定=网络日历优先+离线本地字段回退；通知默认文案（《作品名》今日更新 / 今日放送，记得观看），点击进详情页；首次通知权限在 MainActivity 启动时请求。依赖 androidx.work:work-runtime-ktx:2.10.0。

目标：基于日历放送数据 + WorkManager 周期检查 + 本地通知，提醒「在看」条目新集放送（参考 AniShelf AiringReminderCoordinator）。

### 改动点
1. 依赖：androidx.work:work-runtime-ktx（gradle.libs.versions.toml + app/build.gradle.kts）。
2. 权限：AndroidManifest 加 POST_NOTIFICATIONS（API 33+ runtime 请求）+ 可选 RECEIVE_BOOT_COMPLETED（重启后恢复）。
3. Worker：周期（如每日）读取「在看」条目与 broadcastSchedule/日历放送数据，比对今日放送，命中则发通知（NotificationCompat）。
4. 设置开关：AppSettings.airingReminderEnabled + 外观页开关。
5. 首次请求通知权限：MainActivity 或设置页提醒。

## 建议
- G/J 已在本会话完成并通过 assembleDebug + testDebugUnitTest；后续改动请以 `docs/remaining-phases.md` 为基线，先跑构建确认再落地。