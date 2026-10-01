# Niriko · UI 升级实施计划（参考项目落地 · 第二轮）

> **依据**：本轮 8 个参考项目的逐条评估（2026）。经用户确认，**① AniHyou（AniList 层工程化）与 ⑤ Reorderable（长按拖拽自定义排序）已移出范围**，本文只覆盖剩余 6 项。
> **可视化配套**：`docs/ui-upgrade-prototype.html`（现状 vs 改后；含平板 / 折叠屏形态）。
> **范围纪律**：本文是唯一范围来源。配套原型里出现的形态若本文没有对应条目，视为「讨论稿」，不得直接开工。
> **基线门槛**：每批次开始前先跑 `assembleDebug` + `testDebugUnitTest`，全绿才继续；每批次结束同样全绿。
> **执行前必读**：**§十三「执行前定稿」**（基线快照 / 需要改错的文档陈述 / 旧批次处置 / 每批次收尾定义 / 开工前 8 件事）。
> **当前基线（实测，非 README 口径）**：493 个纯 JVM 用例 / 59 个测试类，`0 failures 0 errors`；README 标注的「436」已过期（更正动作见 §十三·2，需 Q8 点头）。

---

## 〇、范围总览

| 批次 | 主题 | 来源项目 | 落点文件数 | 触碰数据层 | 风险 |
| --- | --- | --- | --- | --- | --- |
| **B1** | 图表体系：Vico 替换手绘 Canvas | patrykandpatrick/vico **3.2.0**（原写 3.3.1，因硬顶 Kotlin 2.4 已更正，见 §十四·4） | 3~4 | 否 | 低-中 |
| **B2** | 平板 / 折叠屏自适应布局 | android/nowinandroid + material3-adaptive | 4~6 | 否 | **高** |
| **B3** | 日历：周 / 月双模 | kizitonwose/Calendar 2.10.1 | 2 | 否 | 中 |
| **B4** | 加载态：shimmer 骨架屏 | valentinilk/compose-shimmer 1.5.0 | 3~5 | 否 | 低 |
| **B5** | 玻璃质感：形状与控件收敛 | Kyant0/AndroidLiquidGlass（backdrop 1.0.6）+ miuix 源码移植 | 3~4 | 否 | 中（版本锁） |
| **B6** | 视觉回归：截图测试 + 迁移测试 | takahirom/roborazzi 1.76.0 | 构建脚本 + 新增测试 | 否 | 中-高（前期成本） |

**已移出范围**：① AniHyou（GraphQL 层工程化）、⑤ Reorderable（拖拽自定义排序 → 原本需要 Room v29→v30 迁移，一并取消，**本计划不产生任何数据库迁移**）。

---

## 一、B0 基线（动工前）

1. 跑 `assembleDebug` + `testDebugUnitTest`，记录真实用例数（当前 493）作为起点留档。
2. **更正文档里与代码不符的陈述**（改错，不是加内容；KDoc 部分无需确认，README 部分需 Q8 点头）：清单与依据见 §十三·2。KDoc 侧至少两处：`LiquidGlassTokens.kt` 的参考仓库写成 QmDeve/MIT（实为 Kyant0/Apache-2.0）、`EpisodeRatingChart.kt` 的「不引入第三方图表库」（B1 会推翻它）。
3. 确认工作区状态：动工前工作区有 28 个已修改 + 21 个未跟踪文件（含 FirstRunPolicy / AiringNotificationComposer / SyncMergePolicy / SourceRateLimiter 等未提交改动）。**建议先提交或 stash**，否则本批次改动会与它们混在同一份 diff 里，无法回滚。
4. 记录并行冲突：B1 与 B3 都动 `ui/stats/StatsScreen.kt`；B2 动 `MainActivity.kt` 与 `navigation/`；B4 动 `ui/components/StateComponents.kt`。**B1 与 B3 必须串行**，其余可并行。

---

## 二、B1 · 图表体系：Vico 替换手绘 Canvas

**来源**：`com.patrykandpatrick.vico:compose-m3:3.2.0`（Apache-2.0）。**版本由 B0 探针修订**：3.3.1 对 `kotlin-stdlib` 下 `strictly 2.4.10`，并会把 Compose UI 顶到 1.12.0，与本工程 Kotlin 2.3.10 冲突（实测见 §十四·4）；3.2.0 实测零版本提升。两者同属 v3 API（`CartesianChartHost` / `rememberCartesianChart`），与 v1 的 `Chart`/`ChartHost` 写法完全不同，**不要照抄旧教程**。

### 现状（实测）

| 位置 | 现状 | 问题 |
| --- | --- | --- |
| `ui/subject/EpisodeRatingChart.kt`（291 行） | 手绘：`Path` 渐变面积 + 折线 + `PathEffect` 虚线均线 + 移动平均 + 极值点；`detectTapGestures` 自算 x 索引 | 无法缩放 / 无法横滑；点击热区按等距估算，集号稀疏时不准 |
| `ui/stats/StatsScreen.kt` `DonutChart`（707 行起） | `drawArc` 手拼扇区 + 3° 缝隙 | 动画已被降级为静态（`animationProgress = 1f`），无交互 |
| `ui/stats/StatsScreen.kt` `BarChart`（768 行起） | `drawRect` + `drawContext.canvas.nativeCanvas.drawText`，**硬编码 `#8C8C8C` 与手算字号** | 深色 / OLED 下对比度不可控；标签字号不随主题排版缩放 |
| `MonthlyTrendSection`（382 行） | **已实现但未被渲染**：`StatsContent` 的 LazyColumn 里没有它的 item 键 | 死代码 |
| `RatingComparisonSection` / `RatingComparisonRow`（590 行起） | **同为死代码**，且只是「7.8 / 8.2」裸文本 | 死代码 |

### 落点

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 依赖 | `gradle/libs.versions.toml` + `app/build.gradle.kts` | 增 `vico = "3.2.0"`（B0 探针修订值）与 `vico-compose-m3`（`compose` / `compose-m3` 同版本） |
| 剧集评分走势 | `ui/subject/EpisodeRatingChart.kt` | 改由 Vico 承载；**函数签名保持不变**（`points` / `showMovingAverage` / 三个 color 参数），调用方 `ui/subject/EpisodeRatingSections.kt:158` 不动 |
| 环形图 | `ui/stats/StatsScreen.kt` → 新 `ui/stats/NirikoDonutChart.kt` | 用 Vico 的 pie/donut 层替换手绘 `drawArc`；`DonutSlice` 数据类保留原样 |
| 柱状图 | `ui/stats/StatsScreen.kt` → 新 `ui/stats/NirikoBarChart.kt` | 替换 `BarChart`/`BarEntry`；同时删掉 `nativeCanvas` 硬编码色 |
| 月度趋势 | `ui/stats/StatsScreen.kt` | **把 `MonthlyTrendSection` 接进 LazyColumn**（键 `"monthly"`，插在 `"type"` 与 `"rating"` 之间），内部换 Vico 柱状图 + 累计折线 |
| 评分对比 | `ui/stats/StatsScreen.kt` | **把 `RatingComparisonSection` 接进 LazyColumn**（键 `"compare"`，插在 `"rating"` 之后），内部换成对柱（个人 / Bangumi）+ 差值线 |
| 配色 | `ui/theme/ChartPalette.kt` | 已是主题派生（HCT），**不改**；Vico 侧通过 `LineComponent` / `ColumnCartesianLayer` 显式传入这些颜色，禁止使用 Vico 默认色 |

### 实现要点

1. **保留手绘实现作为降级**：把现有 Canvas 版本重命名为 `EpisodeRatingChartCanvas` 等私有实现，由 B2 引入的「窗口尺寸 / 性能档位」或 B6 的截图基线决定走哪条；`CardGlassLevel` 已有「按档位降级」的既有范式，沿用同一套思路。
2. **`EpisodeRatingAnalyzer` 不动**：`analyze()` / `movingAverage` / `highlights()` 全是纯函数且已有单测（`EpisodeRatingAnalyzerTest`），Vico 只消费结果。
3. **交互补齐**：点按选中集 → Vico `CartesianMarkerVisibilityListener`；捏合缩放（1~12 集）用 `rememberZoomState()`。当前「点按曲线查看单集」的提示文案保留。
4. **KDoc 必须同步改写**：`EpisodeRatingChart.kt` 现有 KDoc 写着「**不引入第三方图表库**」——那是有意决策，本批次推翻它，必须把理由（交互能力、主题自适应、删掉 `nativeCanvas` 硬编码）写进新的 KDoc，不能留下自相矛盾的注释。
5. **统计页窄屏策略**：`RatingDistributionSection` 现在是「我的 / Bangumi」两个独立 BarChart 上下排 + 各自 `horizontalScroll`。改后合并为同一坐标系的分组柱；若分桶数超过 12 个，保留横向滚动而不是压缩柱宽。

### 验收

1. 剧集页评分曲线：点按某一集显示「第 N 集 · 标题 · 分数（票数）」；双指捏合可缩放到 1~12 集；缩放后极值点高亮仍然正确。
2. 统计页出现「收藏月度趋势」与「评分对比（个人 vs Bangumi）」两个新区块（此前都不显示）。
3. 深色 / OLED 模式下所有图表文字清晰可读（不再有固定 `#8C8C8C`）。
4. 切换自定义主题色 → 图表颜色跟随变化（`ChartPalette` 派生链未被打断）。
5. `MonthlyStats.label.takeLast(2)` 这类标签截断逻辑保持（1 月 / 10 月不能混淆）。

### 单测

图表本身不写单测（已在 B6 用截图覆盖），但**新增的分桶 / 累计计算**必须补：月度趋势累计值、评分对比差值、分桶合并排序。放进 `data/calculator/` 下的纯函数，命名与现有 `StatsCalculator` 一致。

### 风险

1. **Vico v3 API 与旧文档差异大**：`CartesianChartHost` 是 v2 之后的写法，网上大量 v1 示例会误导实现。
2 . **Kotlin / Compose 元数据兼容**：本项目 Kotlin 2.3.10 + Compose BOM 2025.12.01（Compose 1.10.x）。**本风险已在 B0 探针中实测命中并定位**：Vico **3.3.1** 对 `kotlin-stdlib` 下 `strictly 2.4.10`，会把工程顶到 Kotlin 2.4 + Compose UI 1.12.0；**3.2.0 无任何版本提升**。因此 B1 固定使用 3.2.0（推导见 §十四·4），这条从「待验证」变为「已解决」。
3. 关掉 `horizontalScroll` 手写滚动后，Vico 自带的滚动与外层 `LazyColumn` 可能抢夺竖直手势；统计页是 LazyColumn，**图表必须禁用纵向滚动**（只保留缩放 / 横向）。

---

## 三、B2 · 平板 / 折叠屏自适应布局

**来源**：android/nowinandroid（Apache-2.0，做法借鉴）+ `androidx.compose.material3.adaptive:adaptive`（官方库，1.2.0 起支持 large / extra-large 宽度类别）。
**这条正是 README「已知限制与后续计划 · 工程待办」的第 5 条**，也是本轮唯一直接销掉 README 待办的功能项。

### 现状（实测）

- `MainActivity.kt`：单 Activity；`rememberPagerState(pageCount = { TopLevelDestination.entries.size })` 承载四个顶级页；底栏显隐由 `val showBottomBar = currentRoute == MAIN_ROUTE`（第 231 行）控制，`NirikoBottomBar(...)` 用 `Modifier.align(Alignment.BottomCenter)`（第 332 行）贴在底部。
- **全仓库没有任何窗口尺寸类别判断**：搜 `WindowSizeClass` / `calculateWindowSizeClass` 零命中；`BoxWithConstraints` 只用于图片解码尺寸（`CoverImage.kt`）、搜索栏宽度（`IosStyleSearchComponent.kt`）、玻璃控件（`GlassControls.kt`）、底栏（`LiquidBottomTabs.kt`）。
- `SubjectDetailScreen.kt` 约 2400 行单列：封面头 → 评分横滑卡 → 剧集 → 剧照 → 角色 / 制作 / 关联横滑 → 猜你喜欢 → 圣地巡礼 → 外部源区块。
- `TopLevelDestination` 是 4 值 enum（Library / Discover / Stats / Settings），每个都带 selected / unselected 图标，**可直接被竖向 dock 复用，不需要改数据模型**。

### 落点

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 依赖 | `gradle/libs.versions.toml` + `app/build.gradle.kts` | 增 `androidx.compose.material3.adaptive:adaptive`（**不要**再引入旧的 `material3-window-size-class`，它是被 adaptive 取代的库，两套混用会导致尺寸类别判定不一致） |
| 尺寸判定 | 新 `ui/adaptive/WindowClass.kt` | `currentWindowAdaptiveInfo()` 包装 + **纯函数** `NirikoWindowLayout.from(widthSizeClass, heightSizeClass): COMPACT / MEDIUM / EXPANDED`（纯函数 → 可单测，符合项目「纯函数优先」约定） |
| 导航 | 新 `ui/adaptive/NirikoNavSuite.kt` | 按 `NirikoWindowLayout` 在「横向悬浮玻璃胶囊底栏」与**「竖向悬浮玻璃胶囊 dock」**之间切换；底栏沿用现有 `NirikoBottomBar`，竖向 dock 是新组件（见下「⑫ 竖向玻璃 dock」） |
| 宿主 | `MainActivity.kt` | 在 `showBottomBar` 处引入布局判定；`EXPANDED` 时把 `NirikoBottomBar` 换成竖向 dock（仍 `Box` 悬浮覆盖，不是 `Row { rail; content }`），内容侧给出约 96dp 起始内边距，`pagerState` 与 dock 共享（保持「指示器跟手联动」的现有行为） |
| 详情双栏 | 新 `ui/adaptive/AdaptiveDetailScaffold.kt` | 封装 `ListDetailPaneScaffold`（或自建两栏，见「待确认」）；`SubjectDetailScreen` 拆成「概览列」与「内容列」两个可独立渲染的 composable |
| 网格列数 | `ui/library/PosterGridCard.kt` / `ui/screens/Screens.kt` | 海报网格列数按宽度类别取 2 / 3 / 4~6（当前是固定值） |
| 设置页 | `ui/settings/SettingsScreen.kt` | `EXPANDED` 时「分类导航」与「二级页内容」并排（左导航右内容），避免宽屏下一行一行拉长 |

### 实现要点

1. **折叠屏分为两件事**，不要混为一谈：
   - **窗口尺寸类别**（宽 / 窄）→ 决定竖向 dock 与双栏；
   - **折痕（FoldingFeature）** → 用 `collectFoldingFeaturesAsState()` 取 `FoldingFeature.orientation` / `bounds`，在「书本式」展开时把折痕当成两栏的自然分隔线（内容不跨越折痕）。**帐篷模式首轮不做**（见「待确认 Q4」）。
2. **竖向 dock 与纵向 rail 是两件事**（已确认采用前者）：竖向 dock 是现有悬浮胶囊的 90° 旋转，保留真折射与跟手动画；纵向满高 `NavigationRail` 是 Material 默认观感，**已否决**。实现细节见本节的「⑫」小节。
3. **不要用 `NavigationSuiteScaffold` 直接替换**：它自带的 rail / drawer 样式无法承载 Niriko 的玻璃材质与「跟手」动画，会退化成 Material 默认观感。正确做法是用 `currentWindowAdaptiveInfo()` 自己分支，只借鉴它的**断点思路**。
4. **二级页与顶层页的边界**：现有逻辑是「仅顶层 main 路由显示底栏」（`MainActivity.kt:231`）。竖向 dock 在二级页**保留**（宽屏下详情与列表共存，隐藏导航反而让人迷失），这与手机端行为是不同的，需要在实现里显式区分。
5. **横竖屏与窗口自由缩放（桌面模式 / DeX）**：`currentWindowAdaptiveInfo()` 会对窗口变化自动重组，不要缓存到 `remember` 的固定值里。

### ⑫ 竖向玻璃 dock（B2 的一部分，与 B5 联动验收）

**结论：可行，而且几乎是「把现有实现竖过来」。**

**已确认的代码事实（逐项查过）**：

| 项 | 事实 | 影响 |
| --- | --- | --- |
| 现有实现 | `LiquidBottomTabs.kt`（292 行）是 `BoxWithConstraints` + `Row`，高度 64dp、`fillMaxWidth`、`padding(4dp)`，形状 `RoundedCornerShape(50.dp)` | 改竖排 = `Row`→`Column`、`height`↔`width`、`tabWidth`→`tabHeight`、`position.x`→`position.y`、`translationX`→`translationY`、`InteractiveHighlight` 的 `size.height/2f`→`size.width/2f` |
| 玻璃链 | `vibrancy()` + `blur(8f.dp.toPx())` + `lens(24f.dp.toPx(), 24f.dp.toPx())`，选中项第二层再叠 `lens(10dp×progress, 14dp×progress, chromaticAberration = true)` | **整体沿用，不需要改 shader** |
| 折射源 | `MainActivity.kt:253` 的 `rememberKyantLayerBackdrop { drawRect(surface); drawContent() }` 捕获的是**全窗口**（壁纸 + Scaffold） | 左侧 dock 折射壁纸**不需要新增捕获层** |
| 色散方向 | AGSL 里 `dispersionIntensity = chromaticAberration * (centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y)` —— 四叶反对称，**在两条中轴上恒为 0** | 竖排 dock 的彩虹落在**短边**（底栏的 90° 旋转结果），长边保持干净。**无需改 shader** |
| 需要调参 | `gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y))` —— 竖排 dock 窄边（~72dp 的 `halfSize.x`）会把透镜半径钳到约 36dp | 等效于「整条 dock 都在折射」。`LiquidGlassConfig`（`LiquidGlassTokens.kt` 现有 `Default` / `Card` 两个预设）需新增 `VerticalDock` 预设 |
| 收起行为 | 已确认项目有 `bottomBarHideFraction`（`NirikoBottomBar` 里当前用法：位移 8dp + alpha 0.15，`MainActivity.kt:239` 切换顶层页时复位） | 竖排 dock 复用同一 trait，位移方向改为横向（向左滑出）；收起后可保留图标可点 |

**落点**

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 竖排 dock | 新 `ui/bottombar/LiquidVerticalDock.kt` | 近似 `LiquidBottomTabs.kt` 的竖排版本；**不修改** `LiquidBottomTabs.kt`（手机端保持零风险） |
| 玻璃预设 | `ui/components/liquidglass/LiquidGlassTokens.kt` | 新增 `LiquidGlassConfig.VerticalDock`（更小 `cornerRadius` / 更克制 `refractionHeight` 与 `dispersion`）——**具体数值必须真机调，不能在离线精确计算** |
| 导航分支 | `ui/adaptive/NirikoNavSuite.kt`（B2 产出） | 按 `NirikoWindowLayout` 选横向底栏或竖向 dock |
| 宿主 | `MainActivity.kt` | `EXPANDED` 时替换组件；内容起始内边距（约 96dp） |
| 收起位移 | `ui/common/BottomBarScroll.kt` / `NirikoBottomBar.kt` | 复用 `bottomBarHideFraction`，位移方向由纵向改为横向 |

**验收（在 B2 通用验收之外追加）**

1. 竖向 dock 背后能看到**真实壁纸折射**（不是固定色叠加）；手指按下时选中项有玻璃片 + 色散。
2. 竖向拖动手势可在四个目的地间跟手滑动切换，与顶级页 `HorizontalPager` 状态双向同步。
3. 竖向 dock 的长边（左右两侧）**不出现彩虹描边**，短边（上下）有柔和色散。
4. 滚动内容时 dock 向左收起，停止滚动后恢复；`reduceMotion` 时不播放恢复动画。
5. 手机端（<600dp）**完全不受影响**（新组件只有宽屏分支使用）。
6. 二级页在宽屏下保留 dock（含返回项）。


### 验收

1. 手机（<600dp）：与现状**完全一致**——悬浮胶囊底栏、单列详情、网格列数不变（回归零变化是硬要求）。
2. 平板 / 折叠屏展开（≥840dp）：**竖向玻璃 dock** 承载四个顶级页，内容区不再被底栏遮挡；dock 收起时内容内边距同步调整。
3. 宽屏详情页：左列（概览 / 剧照 / 角色）+ 右列（剧集 / 评分走势）并排；两列可独立滚动（或各自内部滚动，外层不滚）。
4. 折痕处内容不跨越：书本式展开时左右两栏之间不出现内容被折痕切断。
5. 玻璃折射在竖向 dock 形态下仍成立：dock 背后能真实看到壁纸（细则见「⑫」的追加验收）。
6. 旋转 / 自由缩放窗口后布局自动切换，无需重启；`reduceMotion` 开关下切换动画跳过。

### 单测

- `NirikoWindowLayout.from()`：手机竖 / 手机横 / 小平板 / 大平板 / 折叠屏展开 / 折叠屏折叠（仅纯函数映射，不做 UI 测试）。
- 网格列数与宽度类别的映射纯函数。
- 折痕 → 分栏策略的纯函数（输入 `FoldingFeature` 的 orientation 与 bounds，输出 `SINGLE / SIDE_BY_SIDE / TOP_BOTTOM`）。

### 风险

1. **风险最高的批次**：动的是导航宿主与详情页两大核心，且 `SubjectDetailScreen.kt` 已 2400 行。建议**分两步走**：先只做「尺寸类别 + 竖向 dock」（不碰详情页），验收通过后再做双栏。两步各自独立可回滚。
2. `ListDetailPaneScaffold` 会引入它自己的返回栈语义，与现有 `NavHost` + `NavController` 可能冲突（同一导航状态两处管理）。若冲突严重，**退回自建两栏**（Row + 两个可滚动列），代价是失去官方返回栈集成。这是「待确认」项。
3. **竖向 dock 的透镜参数无法离线定稿**：`gradRadius` 会被窄边钳到约 36dp（「整条 dock 都在折射」），`cornerRadius` / `refractionHeight` / `dispersion` 必须真机逐档试。**这是 B2 里唯一无法靠推算完成的部分**，需预留调参时间，并把最终值写回 `LiquidGlassTokens.kt` 的 `VerticalDock` 预设。
4. 壁纸是「全局 + 每页覆盖」四页配置（`wallpaperLibraryUri` 等），竖向 dock 形态下 `WallpaperPage.entries.getOrElse(pagerState.currentPage)` 的映射需要重新核对。
5. `compileSdk = 37` + AGP 8.13.2 的「未测试的 compile SDK」警告已存在，新库可能再引入同类警告，属预期但要在构建日志里确认没有新增**错误**。

---

## 四、B3 · 日历：周 / 月双模

**来源**：`com.kizitonwose.calendar:compose:2.10.1`（MIT，另有 `compose-multiplatform` 变体）。阅读器基于 LazyRow/LazyColumn。

### 现状（实测）

- `ui/stats/CalendarCard.kt`（676 行）：自建 `MonthHeader` / `WeekdayHeader` / `DayGrid` / `ModeSwitch` / `CalendarLegend` / `BroadcastStatusHint`；日格用 `aspectRatio` 手算，**只有月视图**。
- 放送金色菱形 ◆ 与发售绿标记是 3 个 Canvas 函数（`CalendarCard.kt` 585 / 598 / 614 行）。
- `CalendarMode` 三态：`PERSONAL` / `BROADCAST` / `ALL`（`data/model/stats/CalendarMode.kt`）。
- 状态来源：`StatsUiState.calendarYear` / `calendarMonth`（由 `StatsViewModel._calendarYear` / `_calendarMonth` 两个 `MutableStateFlow` 驱动），`switchMonth(delta: Int)` 走 `LocalDate.plusMonths`。
- `ui/stats/CalendarDaySheet.kt`（464 行）：某日详情底部弹窗，展示当日放送 + 我的记录。
- README 已声明：「放送条目按精确时刻落格，跨月正确归类」——**这是已实现的既有能力，不能被替换掉**。

### 落点

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 依赖 | `gradle/libs.versions.toml` | 增 `kizitonwose.calendar:compose`（纯 Android 用 `compose`；若走 KMP 则用 `compose-multiplatform`，见风险 3） |
| 日历主体 | `ui/stats/CalendarCard.kt` | `DayGrid` / `MonthHeader` / `WeekdayHeader` 由 `MonthCalendar` / `WeekCalendar` 承载；**仅替换渲染层** |
| 日格内容 | `ui/stats/CalendarCard.kt` | 3 个 Canvas 标记函数**原样搬进** day content（`dayContent { date -> ... }`） |
| 周视图 | `ui/stats/CalendarCard.kt` | 新增「周 / 月」切换（与现有三态模式切换并存，是**两个正交维度**：视图粒度 × 数据来源） |
| ViewModel | `viewmodel/StatsViewModel.kt` | 新增 `calendarExpanded: StateFlow<Boolean>`；保留 `switchMonth(delta)` 供箭头使用，同时接住 pager 的 `currentMonth` 变化 |
| 日详情 | `ui/stats/CalendarDaySheet.kt` | 不改（继续消费 `Map<LocalDate, CalendarDayEvents>`） |

### 实现要点

1. **数据契约不变**：`Map<LocalDate, CalendarDayEvents>` + `episodesBySubject` 原样喂给 `dayContent`。这样「按精确时刻落格 / 跨月归类」的逻辑完全不受影响，风险被限制在 `CalendarCard.kt` 内部。
2. **`switchMonth` 的语义变化**：现在由两个箭头手动翻月；改为可滑动翻月后，需要**双向同步**——pager 滑动 → 更新 `_calendarYear/_calendarMonth`；外部调用 `switchMonth` → 驱动 pager 滚动（`animateScrollToMonth`）。这一处最容易出现「箭头与手势状态不同步」，必须专门验收。
3. **周 / 月切换的过渡**：展开 / 收起时保持「同一周不跳变」——收起时选中周要对应展开后的同一周（`WeekCalendar`/`MonthCalendar` 共享 `firstDayOfWeek`，项目现在用周日起始：`listOf("日","一",...,"六")`）。
4. **删除量**：`MonthHeader` / `WeekdayHeader` / `DayGrid` 三块手写布局预计可删约 300 行。删掉的 KDoc 与注释要一并清理（不留「已废弃」的尸体代码）。
5. **与 B1 的串行**：两者都动 `StatsScreen.kt`（B3 影响的是 `CalendarCard` 调用点与排序），**必须先 B1 后 B3**。

### 验收

1. 月视图：仍能左右翻月（手势 + 箭头两种方式结果一致）；跨月日期归类与现状一致（拿一个跨月放送条目对比）。
2. 周视图：只显示当周 7 天，纵向空间约为月视图的 1/3；选中日期后下方内容随之更新。
3. 三态模式切换（个人记录 / 放送信息 / 全部显示）在**周 / 月两种视图下都生效**。
4. 点某一天 → `CalendarDaySheet` 弹出内容与现状一致。
5. 放送金色菱形 / 发售绿标记在两种视图下的位置与形状不变。
6. `broadcastError` 与 `broadcastLastUpdatedAt`（「上次更新 X 分钟前」+ 强制刷新）行为不变。

### 单测

- 「选中日期 ↔ 所在周 / 月」纯函数（边界：跨月周、跨年周、1 月 1 日落在上一年的周）。
- 周 / 月视图切换时选中日期保持不变的纯函数。
- 现有 `CalendarDayEvents` 相关测试不动，确认无回归。

### 风险

1. **手势冲突**：日历横向滑动与外层 `LazyColumn`（竖直）不冲突，但与**顶级页 `HorizontalPager` 的左右滑切换**冲突——统计页是 Pager 的第三页，日历横滑翻月会把页面切走。项目已有 `SearchGestureLock`（搜索交互期间锁 Pager）作为先例，**日历需要同类锁**，否则「翻月变成换页」。
2. 折叠屏 / 平板下日历变宽，`MonthCalendar` 默认日格会拉得过大，需要按宽度类别限制 `daySize`。
3. **KMP 口径要现在定**：`docs/ios-port/` 正在评估 KMP。若走 `compose-multiplatform` 变体，坐标与依赖配置不同，后期切换需要改 Gradle。建议现在就按「未来 KMP」选 `compose-multiplatform`（它在纯 Android 工程同样可用），避免二次返工——**此项需确认**。

---

## 五、B4 · 加载态：shimmer 骨架屏

**来源**：`com.valentinilk.shimmer:compose-shimmer:1.5.0`（Apache-2.0）。

### 现状（实测）

- `ui/components/StateComponents.kt`（357 行）**已经有骨架屏**：`SkeletonList` / `SkeletonCard`（私有）/ `SkeletonCircle`，实现是 `rememberInfiniteTransition` + alpha 在 `0.3 ↔ 0.7` 之间 `RepeatMode.Reverse`（**每处各起一个无限动画**）。
- 但骨架屏几乎没被用：`LoadingContent()`（居中 `CircularProgressIndicator`）**只有 4 个调用点**——`CharacterDetailScreen:73`、`SubjectDetailScreen:629`、`StaffListScreen:58`、`PersonDetailScreen:99`。
- 全仓库 `CircularProgressIndicator` + `LinearProgressIndicator` **共 49 处**（含 `SubjectDetailScreen` 6 处、`BilibiliSyncScreen` 3 处、`SearchResultsPane` 2 处等）。
- 统计页加载态是**一行文字**「正在加载统计数据…」（`StatsScreen.kt:175`），既无骨架也无进度。

### 落点

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 依赖 | `gradle/libs.versions.toml` | 增 `com.valentinilk.shimmer:compose-shimmer` |
| 骨架原语 | `ui/components/StateComponents.kt` | `SkeletonCard` / `SkeletonCircle` 的呼吸 alpha → `ShimmerBrush`；新增 `SkeletonBlock(width, height, shape)` 通用块 |
| 降级策略 | 新 `ui/components/ShimmerPolicy.kt` | **纯函数** `ShimmerPolicy.shouldAnimate(reduceMotion, systemAnimatorOff, glassEffect)`；沿用 `ui/animation/NirikoAnimation.kt` 的 `LocalReduceMotion`（`Theme.kt:252` 提供） |
| 统计页 | `ui/stats/StatsScreen.kt` | 文字加载态 → 日历格 + 图表 + 概览胶囊的骨架组合 |
| 作品库 | `ui/screens/Screens.kt` + `ui/library/PosterGridCard.kt` | 首屏加载 → 海报网格骨架（沿用卡片真实尺寸，避免加载完成时跳版） |
| 详情页 | `ui/subject/SubjectDetailScreen.kt` | `LoadingContent()` → `GlassSectionCard` 形状的分区骨架（评分区 / 剧集区 / 角色区各一块） |
| 搜索 / 发现 | `ui/search/TrendingSection.kt`、`ui/search/SearchResultsPane.kt` | 转圈 → 卡片骨架 |

### 实现要点

1. **`reduceMotion` 是硬约束**：项目在 `RevealOnScroll.kt:43` 已立下规矩「减少动态效果时跳过一切入场动画」，`MainActivity.kt:175` 还把系统 `animator_duration_scale=0` 并入了 `reduceMotion`。**shimmer 是无限动画，必须在 `reduceMotion` 为真时退化为静态灰块**，否则等于自己破自己的约定。
2. **不要一律替换**：`LinearProgressIndicator` 有 3 处是**进度语义**（详情页顶部细条 `SubjectDetailScreen:636`、搜索建议 `SearchResultsPane:359`、B 站导入 `BilibiliSyncScreen:309`）——它们是「正在下载 / 正在导入」的真实进度，不是占位，必须保留。
3. **内联小转圈保留**：`size(14.dp/16.dp/20.dp)` 的行内指示器（如 `TmdbBindingSection:353`、`SettingsItem:328`、绑定按钮）表示「这个按钮正在处理」，换成骨架反而不清楚——**保留**。
4. **骨架尺寸必须与真实内容一致**：`SkeletonList` 现在固定 `cardHeight = 120.dp`，而作品库是海报网格（3:4 比例）。用现有 `PosterGridCard` 的尺寸常量构造骨架，避免加载完成时布局跳动（CLS）。

### 验收

1. 打开统计页（冷启动、无缓存）：先看到日历 + 图表形状的骨架，数据到达后平滑替换，不出现「空白 → 突然全屏内容」。
2. 打开作品库：海报网格骨架与实际卡片位置一致，加载完成无跳版。
3. 设置「减少动态效果」为开（或在开发者选项关闭动画）→ 骨架**完全静止**（无扫光、无呼吸）。
4. 详情页顶部进度细条、搜索建议进度条、B 站导入进度条行为不变。
5. 按钮内联小转圈不变。

### 单测

`ShimmerPolicy.shouldAnimate`（reduceMotion / 系统动画 / 三档玻璃强度的组合），风格对齐现有 `FirstRunPolicyTest` / `ProgressBumpPolicyTest`。

### 风险

1. `SkeletonList` 的现有调用方若依赖「固定 120dp 行高」，改尺寸会连带影响——落地前先搜清调用面（当前仅 `StateComponents.kt` 内部）。
2. shimmer 的 `ShimmerBrush` 是 `Brush`，在 `GlassSectionCard` 内部使用时要确认不触发额外的 RenderEffect（玻璃卡本身已有 backdrop 捕获），否则低端机可能出现「骨架 + 真玻璃」双重开销。**玻璃档位为 `OFF` 时骨架也同样降级**。
3. 本批次不改数据层，纯 UI，**可与任何批次并行**（除 `StatsScreen.kt` 的改动需与 B1/B3 协调位置）。

---

## 六、B5 · 玻璃质感：形状与控件收敛

**来源**：`Kyant0/AndroidLiquidGlass`（Apache-2.0，**即项目已依赖的 `io.github.kyant0:backdrop` 源码仓库**）+ `compose-miuix-ui/miuix`（Apache-2.0，**源码移植，不升依赖**）。

### 现状（实测，含两处必须先纠正的事实）

- 项目依赖：`io.github.kyant0:backdrop:1.0.6`（`app/build.gradle.kts:117`）+ `top.yukonga.miuix.kmp:miuix-blur-android:0.9.0`（第 112 行，**硬编码**，而 `libs.versions.toml` 里 `miuix = "0.9.3"` —— 版本口径本身不一致）。
- **事实纠正 1**：`ui/components/liquidglass/LiquidGlassTokens.kt` 的 KDoc 写着「参考库 **AndroidLiquidGlassView(QmDeve, MIT)** 的默认参数」——那是**另一个仓库**。实际依赖的 `kyant0/backdrop` 来自 `Kyant0/AndroidLiquidGlass`，许可为 Apache-2.0，最新版 **2.0.1（需 Compose 1.12.0）**。`app/build.gradle.kts:115-117` 的批注「2.0.1 需 AGP 9.1 / Compose 1.12，当前不兼容」**今天依然成立**，不能升级。KDoc 与批注里的来源仓库名需一并更正。
- **事实纠正 2**：miuix 最新 `0.9.4` 依赖 **Kotlin 2.3.20 + Compose 1.10.3 + io.github.kyant0:shapes:1.2.0 + material-color-utilities 4.1.1**；本项目 Kotlin **2.3.10**、`material-color-utilities = 2.1.1`。**升 miuix 会把 `material-color-utilities` 顶到 4.1.1**，而 `ui/theme/SeedColorScheme.kt`（84 行）与 `ui/theme/ThemeColorPicker.kt`（290 行）直接依赖它的 API —— 这会静默改变/破坏整套主题配色。**结论：miuix 只走源码移植，不升依赖。**
- 已自建：`GlassCard.kt`（389 行，`appleGlassCard()`）、`GlassControls.kt`（181 行，`GlassToggle` 弹簧 damping 0.6 + `GlassSlider` 的 `detectDragGestures` + `offsetToValue` 0.5 步进）、玻璃令牌 `LiquidGlassPalette`（HighlightTop / HighlightBottom / BorderLight / BorderDark / CardShadowSpot / CardShadowAmbient）。
- 圆角目前散落字面量：`RoundedCornerShape(20.dp)`（统计区块）、`MaterialTheme.shapes.medium`（日历）、`RoundedCornerShape(trackHeight / 2)`（开关）、`RoundedCornerShape(999.dp)`（胶囊）。

### 落点

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 形状令牌 | 新 `ui/theme/NirikoShapes.kt` | 收敛 `card / section / pill / control` 四档圆角为令牌（含 squircle 平滑圆角实现，从 miuix 源码移植） |
| 材质分层 | `ui/components/liquidglass/LiquidGlassTokens.kt` + `GlassCard.kt` | 用 backdrop 1.0.6 的 highlights / shadow / innerShadow DSL 把现有 5 个色令牌变成真正的材质层；**公开 API（`appleGlassCard`）不变** |
| 控件 | `ui/components/GlassControls.kt` | `GlassToggle` / `GlassSlider` 的**视觉层**换成 Kyant 样例的做法；`offsetToValue` 纯函数**保留**（它有单测价值） |
| 竖向 dock 玻璃 | `ui/components/liquidglass/LiquidGlassTokens.kt` + 新 `ui/bottombar/LiquidVerticalDock.kt`（B2 产出） | 新增 `LiquidGlassConfig.VerticalDock` 预设（更小 `cornerRadius`、更克制的 `refractionHeight` / `dispersion`）；dock 的折射参数真机调 |
| 来源更正 | `LiquidGlassTokens.kt` KDoc + `app/build.gradle.kts` 批注 | 更正参考仓库名与许可（QmDeve/MIT → Kyant0/Apache-2.0） |

### 实现要点

1. **只做「视觉层替换」，不换数据与手势模型**：`GlassSlider` 现有的 `pointerInput` + `offsetToValue` 已验证可用且有步进语义，替换时保留；只替换轨道 / 圆钮 / 高光的绘制。
2. **深色模式已有明确行为**：「卡片回退 M3 surfaceContainer，取消玻璃层次」（`LiquidGlassTokens.kt` 注释）。分层材质只在浅色 + 有壁纸时叠加，深色下不要强行加高光。
3. **`CardGlassLevel` 三档仍是最外层裁决**：`GlassCard.kt:187` 的 `FULL || (COLLECTION_ONLY && isCollectionCard)` 分支不能被打散，新材质必须在这三档下都有明确表现（全效果 / 仅收藏卡 / 静态降级）。
4. **`LocalGlassEffect`（`ui/theme/LocalGlassEffect.kt`，7 行）是另一套档位**：全效果 / 降低 / 关闭。两套叠加名义上是 9 种，但 `GlassEffectLevel.OFF` 会让 `CardGlassLevel` 完全失效（`GlassCard.kt:186` 先判 `glassEffect`）→ **B6 的截图矩阵实取 5 档**，推导见 §十三·4。

### 验收

1. 浅色 + 壁纸：玻璃卡边缘反射随壁纸内容变化（不是固定色叠加）；有明确的内阴影与浮起阴影。
2. 深色模式：卡片为 M3 `surfaceContainer`，无玻璃层次（与现状一致）。
3. 5 档玻璃状态（见 §十三·4）下都不出现空白 / 全黑 / 文字不可读。
4. `GlassSlider` 的 0.5 步进（0~10 共 21 档）与拖拽手感与现状一致。
5. 竖向 dock 形态（若 B2 已落地）玻璃折射成立。

### 单测

- `offsetToValue` 移到独立文件后补测（0 / 满量程 / 中间档 / 范围反转 / 宽度为 0 的边界）。
- 形状令牌到具体 Shape 的映射纯函数（若引入 squircle 的平滑度参数）。

### 风险

1. **不要升级 `backdrop` 到 2.x**（需 Compose 1.12，本项目 1.10.x）；**不要升级 `miuix`**（需 Kotlin 2.3.20 且会顶动 `material-color-utilities`）。两者都会直接构建失败或静默改变主题。
2. 源码移植 miuix 的 squircle 实现时，**必须核对许可头与 `THIRD-PARTY-NOTICES.md`**（README 明确说明第三方组件的许可声明在该文件里）。Apache-2.0 移植需要在 NOTICES 里记录来源与版本。
3. 真玻璃是「每张卡一个 RenderEffect」，新分层材质会增加每卡绘制层数；中低端机需要复测滚动帧率（README 的性能审计在 `docs/performance-audit.md`）。

---

## 七、B6 · 视觉回归：截图测试 + 迁移测试

**来源**：`io.github.takahirom.roborazzi:roborazzi:1.76.0`（Apache-2.0，2026-09-29）+ Robolectric（Native Graphics）。

### 现状（实测）

- 59 个测试类、493 个 JVM 用例，**全是纯函数 test**，UI 层零自动化覆盖。
- `data/local/NirikoMigrationChainTest.kt` 已存在（迁移链的纯 JVM 校验）。
- `app/schemas/` 有 v2~v24、v26~v29，**缺 `25.json`**（README 已承认；迁移链含 24→25→26）。
- 待覆盖的高风险视觉面：`CardGlassLevel` × `GlassEffectLevel`（**矩阵实取 5 档，不是 9 档**，理由见 §十三·4）、主题色（HCT 派生）/ OLED 纯黑 / 动态取色、玻璃降级、图表在深浅色下的可读性。
- **CI 已存在**（`.github/workflows/android-ci.yml`，上一版 B5 落地），B6 的增量是**在它里面追加截图校验步骤**，不是从零搭 CI（见 Q5）。

### 落点

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 测试依赖 | `gradle/libs.versions.toml` + `app/build.gradle.kts` | `testImplementation`：`roborazzi` / `roborazzi-compose` / `roborazzi-junit-rule`、`org.robolectric:robolectric`、`androidx.compose.ui:ui-test-junit4` |
| 插件 | 根 `build.gradle.kts` + `app/build.gradle.kts` | Roborazzi Gradle plugin（提供 `recordRoborazziDebug` / `verifyRoborazziDebug`） |
| 测试开关 | `app/build.gradle.kts` | `testOptions.unitTests.isIncludeAndroidResources = true`（Robolectric 取资源必需） |
| 截图用例 | 新 `app/src/test/java/com/otakup/niriko/ui/ScreenshotBaselineTest.kt` | 按设备形态 × 玻璃档位 × 主题模式生成矩阵 |
| 迁移测试 | 新 `app/src/test/java/com/otakup/niriko/data/local/RoomMigrationTest.kt` | `MigrationTestHelper` 逐版本校验 v2→v29 |
| 补 schema | `app/schemas/25.json` | 从代码迁移链反推补齐（**动工前必须确认 DDL 与导出 `createSql` 逐字一致**） |
| README 更正 | `README.md` | **7 处**过期陈述（见 §十三·2）：测试数 436→493（两处）、限流闸门、迁移测试、平板/折叠屏、release 签名、CI —— 需 Q8 点头 |

### 实现要点

1. **`@GraphicsMode(NATIVE)` + Robolectric**：这是「无需设备」的关键。默认 Robolectric 图形是占位的，必须开 Native Graphics 才渲染真实像素。
2. **截图矩阵取 5 档玻璃、不取 9 档**（推导见 §十三·4）：**4 个页面**（作品库 / 详情 / 统计 / 设置）× **5 个玻璃状态** + **2 个主题模式**（浅 / 深）+ **2 个宽度类别**（手机 / 平板，与 B2 联动）= 80 张；嫌多时按「浅色手机」抽样到 **40 张**。不要做全 9 档笛卡尔积，其中 4 档画面与另 4 档完全相同。
3. **迁移测试与截图测试分开处理**：
   - 迁移测试**不需要截图**，只需要 Robolectric 起真实 SQLite；它的价值是校验 DDL 与 `createSql` 逐字一致（README 明确说「schema 校验只在真机首次开库时执行，编译期不会报错」）。
   - 截图测试**不做数据库**，用固定假数据（`StatsUiState` 已有 `@Preview` 的假数据可直接复用 `StatsScreen.kt:855` 起的 Preview 数据）。
4. **golden 图策略要先定**：PNG 进仓库 → 谁可以重录、重录命令是什么、允许的像素差异阈值是多少。否则第一次改一处圆角就会让全部基线飘。建议：`roborazzi.record` 只由维护者在**明确的 UI 改动批次末尾**执行，并随 PR 附上 diff 图。
5. **`.gitignore` 已确认没有排除截图目录**，无需额外配置。

### 验收

1. `.\gradlew.bat verifyRoborazziDebug` 在干净检出上通过（基线已提交）。
2. 故意改动一处玻璃降级条件（例如把 `COLLECTION_ONLY` 分支写错）→ 对应截图测试**失败**并能看出差异。
3. `.\gradlew.bat testDebugUnitTest` 仍然全绿，且**用例数不减**（新增截图 / 迁移测试后总数上升）。
4. Room 迁移测试逐版本通过；`app/schemas/25.json` 补齐后 v24→v26 可建库。

### 风险

1. **前期成本最高的一条**，且严格来说不产出用户可见功能。建议排在其他批次之后（否则每改一处玻璃就要重录全部基线）。
2. 截图测试对**字体渲染与抗锯齿**敏感，同一 baseline 在不同机器 / JDK 上可能产生像素差异 → 必须配置 `roborazzi.record` 的容差或在 CI 上固定 JDK。**CI 已存在但未在真实 runner 上验证过**，因此追加的截图步骤建议先标 `continue-on-error: true`（见 Q5）。
3. Robolectric 的支持 SDK 上限可能低于 `compileSdk = 37`；若截图在 API 37 上不被支持，用 `@Config(sdk = [34])` 固定到 `targetSdk`（34）——这也是「用户实际看到的版本」。

---

## 八、依赖与执行顺序

```
B0 基线（依赖可解析性验证 + 文档改错 + 工作区提交/stash）  ← 开工前 8 件事，见 §十三·8
 │
 ├─ B4 加载态（shimmer）  ──┐
 │                          │  都动 StatsScreen.kt
 ├─ B1 图表（Vico）      ──┤  → 固定顺序 B1 → B3 → B4
 │                          │
 ├─ B3 日历（Calendar）  ──┘
 │
 ├─ B5 玻璃（源码移植 + 形状令牌）  ← 先落 VerticalDock 预设
 │
 ├─ B2 自适应（5a 尺寸类别+竖向 dock → 5b 详情双栏，串行）  ← 消费 B5 的预设
 │
 └─ B6 截图（5 档矩阵）+ 迁移测试（roborazzi + room-testing + 补 25.json）← 最后做
```

**建议实际排期**：

| 顺序 | 批次 | 理由 |
| --- | --- | --- |
| 1 | **B4** | 纯 UI、零迁移、收益立即可见，且能顺手把 `reduceMotion` 规范补严 |
| 2 | **B1** | 数据与纯函数已就绪，只换渲染层；顺带复活两处死代码 |
| 3 | **B3** | 风险限制在 `CalendarCard.kt` 内；删掉约 300 行手写布局 |
| 4 | **B5** | 视觉收敛，为 B2 的竖向 dock 玻璃铺路 |
| 5 | **B2** | 最高风险，分「尺寸类别 + 竖向 dock」与「详情双栏」两步 |
| 6 | **B6** | UI 稳定后再录 baseline |

---

## 九、受影响文件总览

| 文件 | 涉及批次 | 性质 |
| --- | --- | --- |
| `gradle/libs.versions.toml`、`app/build.gradle.kts` | B1 B2 B3 B4 B6 | 新增依赖 |
| `ui/subject/EpisodeRatingChart.kt` | B1 | 重写（保留签名） |
| `ui/stats/StatsScreen.kt` | B1 B3 B4 | 改动最集中：图表、死代码复活、加载态、日历调用点 |
| `ui/stats/CalendarCard.kt` | B3 | 大改（渲染层替换，预计 -300 行） |
| `ui/components/StateComponents.kt` | B4 | 骨架原语改造 |
| `ui/components/GlassControls.kt`、`GlassCard.kt`、`liquidglass/LiquidGlassTokens.kt` | B5 | 视觉层替换 + 文档更正 |
| `MainActivity.kt`、`navigation/TopLevelDestination.kt` | B2 | 导航宿主分支 |
| `ui/adaptive/*`（新增） | B2 B5 | 尺寸判定、NavSuite、详情 Scaffold |
| `ui/bottombar/LiquidVerticalDock.kt`（新增） | B2 B5 | 竖向玻璃 dock；**不改** `LiquidBottomTabs.kt` |
| `ui/theme/NirikoShapes.kt`（新增） | B5 | 圆角令牌 |
| `ui/components/ShimmerPolicy.kt`（新增） | B4 | 降级纯函数 |
| `ui/stats/NirikoDonutChart.kt`、`NirikoBarChart.kt`（新增） | B1 | 图表封装 |
| `app/src/test/.../ScreenshotBaselineTest.kt`、`RoomMigrationTest.kt`（新增） | B6 | 测试 |
| `app/schemas/25.json`（补齐） | B6 | 迁移前置条件 |
| `THIRD-PARTY-NOTICES.md` | B5 | 移植 mIUiX 源码的许可声明 |
| `README.md` | B0 B6 | 过期口径更正 |

**注意**：本计划**不产生任何 Room 迁移**（Reorderable 移出范围后，第 1 版的 `collections.sortIndex` 改动一并取消）。Room 侧唯一动作是 B6 补 `25.json` 与迁移测试。

---

## 十、风险登记（跨批次）

| # | 风险 | 影响批次 | 应对 |
| --- | --- | --- | --- |
| R1 | ~~Vico 3.3.1 的 Kotlin 元数据与本项目 Kotlin 2.3.10 不兼容~~ **已实测确认并处置**：3.3.1 硬顶 `kotlin-stdlib strictly 2.4.10` + Compose UI 1.12.0；B1 改用 3.2.0（零版本提升） | B1 | **已关闭**：B0 探针跑通四组组合，见 §十四·3/4 |
| R2 | 日历横滑翻月抢走 `HorizontalPager` 的左右滑 | B3 | 复用 `SearchGestureLock` 的既有模式加日历手势锁 |
| R3 | `ListDetailPaneScaffold` 与 `NavHost` 返回栈冲突 | B2 | 冲突严重则退回自建两栏（Row + 双列） |
| R4 | 升级 `backdrop` 2.x / `miuix` 0.9.4 | B5 | **明令禁止**：前者需 Compose 1.12，后者需 Kotlin 2.3.20 且会顶动 `material-color-utilities` 破坏主题配色 |
| R5 | 截图基线在换机器后抖动 | B6 | 固定 JDK / `@Config(sdk=[34])`；只在批次末尾重录 |
| R6 | 玻璃分层材质拖低中低端机滚动帧率 | B5 | 沿用 `CardGlassLevel` 三档；低端机复测（参考 `docs/performance-audit.md`） |
| R7 | 工作区未提交改动与本批次混在同一 diff | 全部 | B0 先提交或 stash |
| R8 | 平板改动破坏手机端现有观感 | B2 | 手机端「零变化」作为硬验收项，B6 截图覆盖两档宽度 |
| R9 | 竖向 dock 的透镜参数无法离线定稿（窄边把 `gradRadius` 钳到 ~36dp） | B2 B5 | 先落结构、参数真机逐档调；最终值写回 `LiquidGlassConfig.VerticalDock` |

---

## 十一、待确认（动工前需拍板）

| # | 问题 | 建议默认 |
| --- | --- | --- |
| Q1 | B2 详情双栏用 `ListDetailPaneScaffold`（官方，但可能与 `NavHost` 冲突）还是自建两栏（简单可控，但没有官方返回栈）？ | **先自建两栏**，把返回栈留给自己管，避免两套导航状态 |
| Q2 | B3 用纯 Android 的 `compose` 变体，还是 KMP 的 `compose-multiplatform`（为 `docs/ios-port/` 铺路）？ | **`compose-multiplatform`**（纯 Android 工程同样可用，避免二次返工） |
| Q3 | B1 是否保留手绘 Canvas 图表作为降级实现，还是直接删除？ | **保留**，由玻璃 / 性能档位裁决；删掉会失去一个已验证可用的兜底 |
| Q4 | B2 折叠屏是否要做「帐篷模式」专属布局（上半屏画面 / 下半屏操作）？ | **首轮不做**，只做折痕不切断内容；帐篷模式留到第二轮 |
| Q5 | ~~B6 是否顺带引入 CI？~~ **问题已过时**：CI 工作流与 release 签名已在上一版 B5 落地（`.github/workflows/android-ci.yml`）。真正的问题变成：**是否在已有 CI 里追加 Roborazzi 截图校验步骤？** | **追加，但标 `continue-on-error: true` 起步**（工作流本身尚未在真实 runner 上验证过，先当信号不当门禁）——见 §十三·3 |
| Q6 | B5 是否移植 miuix 的 squircle 形状（需要改 `THIRD-PARTY-NOTICES.md`）？ | **移植**，但作为独立小步，与材质分层拆开验收 |
| Q7 | 各库版本号是否在动工时以 Maven Central 实际可解析版本为准（本文版本取自 2026-08~09 的上游发布记录）？ | **是**，每批次第一步先跑一次 `dependencies` 校验 |
| **Q8** | **是否允许修正 README 里 7 处与代码不符的陈述**（详见 §十三·2）？上一轮决策是「README 内不添加内容」，但这次是**改错**而非加内容 | **已于本轮决议：允许改错、仍不加新内容**：只改那 7 处事实陈述，不新增章节、不加入参考项目清单（决议记录见 §十四） |

---

## 十二、范围外（本计划明确不做）

| 项目 | 原因 |
| --- | --- |
| **axiel7/AniHyou-android**（GraphQL 查询组织 / 分页游标 / 错误体解析） | 用户确认不需要 |
| **Calvin-LL/Reorderable**（长按拖拽自定义排序） | 用户确认不需要；连带取消 Room v29→v30 迁移与门户顺序自定义 |
| `backdrop` 升 2.x、`miuix` 升 0.9.4 | 版本硬约束，升了会构建失败或破坏主题配色（见 R4） |
| 在 README 内添加参考项目内容 | 沿用上一轮决策（README 内不添加内容）；**但已过期的数字/状态属于「改错」**，见 §十三·2 与 Q8 |

---

## 十三、执行前定稿（本轮最后一批计划）

> 本节回答「还剩什么没定、开工第一步做什么、每批次怎么收尾」。**本节之后不再新增范围**；此后所有讨论只用于把已列条目做对。

### 1. 基线快照（实测，非文档转述）

| 项 | 实测值 | 来源 |
| --- | --- | --- |
| 单元测试 | **493 个，0 失败，0 错误，59 个测试类** | `app/build/test-results/testDebugUnitTest/*.xml` 汇总；与 `docs/rollout-plan.md` §十六（B5 记录：488 + 5）一致 |
| README 口径 | 436 个 | **过期**（上一版计划的笔误，见下） |
| Room schema 文件 | v2~v24 + v26~v29，**缺 25.json** | `app/schemas/com.otakup.niriko.data.local.NirikoDatabase/` |
| CI | `.github/workflows/android-ci.yml` **已存在**（JDK 17 → `testDebugUnitTest` → `assembleDebug` → 上传测试报告），但**未在真实 runner 上验证过** | 仓库文件 + rollout-plan §十六 |
| release 签名 | `app/build.gradle.kts` 已有 `signingConfigs.release`，凭据只从本地 `keystore.properties` 读；无该文件时产出未签名包且构建不失败 | build.gradle.kts |
| 未提交工作区 | 28 个已修改 + 21 个未跟踪文件 | `git status` |

### 2. 需要改错的文档陈述（与代码不符）

**这一节是我上一轮的疏漏更正**：上一版这段只列了 2 条并把限流那条写错方向了。实测后完整清单如下，共 7 处。

**A. README（需 Q8 点头）**

| # | README 行 | 现写作 | 实际 |
| --- | --- | --- | --- |
| 1 | 第 16 行 | 「单元测试 436 个」 | **493 个**（59 类，0 失败） |
| 2 | 第 431 行 | 「当前 436 个用例覆盖统计…」 | 同上，**同一处笔误的第二处** |
| 3 | 第 483 行 | 「按源限流闸门（IGDB 4 req/s…）**尚未实现**」 | **已实现**：`util/SourceRateLimiter.kt` + `SourceRateLimits.kt`，已接入 `data/remote/rating/RatingHttp.kt`（`awaitUrl`）与 `data/remote/vndb/VndbApiClient.kt`（`awaitHostBlocking`） |
| 4 | 第 484 行 | 「Room 迁移测试**未补**」 | 已有静态版 `NirikoMigrationChainTest`（5 例）；真机 SQLite 版由 **B6** 承接 |
| 5 | 第 486 行 | 「平板 / 折叠屏自适应布局**尚未实现**」 | 由 **B2** 承接（含竖向玻璃 dock）——**这条可以不动**，等 B2 完成后再改为已实现 |
| 6 | 第 487 行 | 「`release` 构建**未配置签名**」 | **已配置** `signingConfigs.release`（凭据只从本地 `keystore.properties` 读；无该文件时才退化为未签名） |
| 7 | 第 489 行 | 「仓库**未附带 CI**，单元测试与构建需本地执行」 | **已附带** `.github/workflows/android-ci.yml`；但**尚未在真实 runner 上验证过**，这句要如实写 |

**B. KDoc / 代码注释（不需要确认，随对应批次改）**

| # | 位置 | 现写作 | 实际 / 处置 |
| --- | --- | --- | --- |
| 8 | `ui/components/liquidglass/LiquidGlassTokens.kt` KDoc | 「参考库 AndroidLiquidGlassView(**QmDeve, MIT**)」 | 实为 **Kyant0/AndroidLiquidGlass（Apache-2.0）**；随 B5 更正 |
| 9 | `ui/subject/EpisodeRatingChart.kt` KDoc | 「**不引入第三方图表库**」 | B1 会推翻该决策，需把新理由写进注释（不能留自相矛盾的说明） |
| 10 | `app/build.gradle.kts:115-117` 批注 | 「2.0.1 需 AGP 9.1 / Compose 1.12」 | 结论仍成立（backdrop 2.0.1 确实需 Compose 1.12），但可补一句「上游最新即 2.0.1」以免后人反复查；随 B5 处理 |

> 若你选择「README 一概不动」（Q8 否决），则第 1~7 条降级为「已知差异记录」留在本文档，不改 README；KDoc 部分照改（属代码内注释，不是 README）。

### 3. 旧批次方案的处置（避免两版计划打架）

`docs/rollout-plan.md`（上一版）的 B1~B5 **已全部完成并有实施记录**，因此本计划与其**不构成替代关系**。剩下真正未完成的只有以下几项，逐条定处置：

| 旧编号 | 状态（实测） | 本计划处置 |
| --- | --- | --- |
| **6-1 平板 / 折叠屏自适应** | 旧计划**暂缓** | ✅ **本节起正式取消暂缓**，由 B2 承接（含竖向玻璃 dock） |
| **6-3 补 `25.json`** | 未生成（**有意**：identityHash 由注解处理器算，手写会得到错哈希） | 由 B6 承接。**做法不变**：检出 DB 版本为 25 的那次提交 → 构建一次（Room 自动写出 25.json）→ 提交该文件。**禁止手写伪造** |
| **6-2 动态迁移测试** | 只有静态版 `NirikoMigrationChainTest`（5 例） | 由 B6 承接，与 Roborazzi 同一批引入 `robolectric` + `room-testing`（这是两个新测试依赖，**已包含在 B6 的依赖清单里**，即旧计划「需你点头」的那一项在此一并批准） |
| **6-2 旧残留**：`CalendarTypeColors` 与 `chartTypeColor` 两套映射 | 记录未做 | 归入 **B5**（形状/色彩收敛顺手做），不单列批次 |
| **6-2 旧残留**：tabular figures 只在 `displayLarge` 显式启用 | 记录未做 | 归入 **B4**（骨架屏同批：数字占位与真实数字字形要对齐） |
| **6-5 CI** | ✅ 已完成 | 不再重做；**只在 B6 追加截图步骤**（见 Q5） |
| 7-9 SMB / 7-10 年鉴 / 7-11 社区只读 / 1-7 评分区间服务端化 | 暂缓 | **继续暂缓**，不在本计划范围 |
| `compose-miuix-ui/miuix`、`Kyant0/AndroidLiquidGlass`（旧 §九 参考项目表里的两行） | 未做 | **合并进 B5**（源码移植，不升依赖） |
| 旧 §九 其余 8 个参考项目条目 | — | 由本文 §十二「范围外」取代（AniHyou / Reorderable 已排除；其余并入 B1~B6） |

> 补充事实：`gradle/libs.versions.toml` 里声明了 `cloudy = "1.0.0-alpha01"`，但 `app/build.gradle.kts` **未引用**（旧计划 §九 末尾已记录）。本计划**不用它**；是否清理这行声明由你决定（属于琐碎整备，默认留着不动）。

### 4. 最终决策：B6 截图矩阵取 5 档（不是 9 档）

`CardGlassLevel`（FULL / COLLECTION_ONLY / OFF）× `GlassEffectLevel`（FULL / REDUCED / OFF）确实是 9 种组合，但并非每种都有意义 —— 已核 `GlassCard.kt:186` 的 `realEnabled` 与 `GlassSectionCard.kt:66` 的 `backdropUsable`：

```
realEnabled = cardBackdrop != null && glassEffect != OFF && (cardGlassLevel == FULL || (… && isCollectionCard))
backdropUsable = backdrop != null && glassEffect == FULL
```

`GlassEffectLevel.OFF` 会让 `cardGlassLevel` 完全失效（两处都先判 `glassEffect`）。因此**矩阵只取 5 档**：

| 保留 | 组合 | 说明 |
| --- | --- | --- |
| ✅ | `GlassEffect=FULL` × `CardGlass=FULL` | 桌面基准 |
| ✅ | `GlassEffect=FULL` × `CardGlass=COLLECTION_ONLY` | 只有收藏卡真玻璃（`GlassCard.kt:187` 的分支） |
| ✅ | `GlassEffect=FULL` × `CardGlass=OFF` | 全静态卡，但壁纸层仍参与 |
| ✅ | `GlassEffect=REDUCED` | 壁纸模糊降级（`WallpaperHost.kt:130` 的 `blurPx=0` 分支） |
| ✅ | `GlassEffect=OFF` | 全关闭，作为「最差档」代表 |
| ❌ | `GlassEffect=OFF` × `CardGlass={FULL,COLLECTION_ONLY}` | 与上一行画面完全相同，**省掉 2 档** |
| ❌ | `GlassEffect=REDUCED` × `CardGlass=*` | 卡片判定不看 REDUCED，3 档合并为 1 档，**省掉 2 档** |

即 4 页 × 5 档 × 2 主题 × 2 宽度 = **80 张**；若嫌多，可按「主题 × 宽度」做抽样，最低 **40 张**（4 页 × 5 档 × 浅色手机）。

### 5. 最终执行顺序（含每步的串行/可并行判定）

| 步 | 批次 | 依赖 | 是否可并行 | 收尾产物 |
| --- | --- | --- | --- | --- |
| 0 | **B0 基线 + 文档改错** | — | 串行（最先） | 构建/单测全绿留档；README 改错（待 Q8）；工作区提交或 stash |
| 1 | **B4 加载态骨架屏** | B0 | 可与 B1 并行（不同文件为主） | 骨架屏 + `ShimmerPolicyTest` |
| 2 | **B1 图表（Vico）** | B0（依赖校验先行） | 与 B4 并行；**与 B3 串行** | 图表替换 + 死代码复活 + 分桶/累计单测 |
| 3 | **B3 日历周/月** | B1（同动 `StatsScreen.kt`） | 串行 | `CalendarCard` 渲染层替换 + 周/月纯函数单测 |
| 4 | **B5 玻璃与形状** | B1/B3/B4 收尾后 | 可与 B2 前半并行 | 材质分层 + 形状令牌 + `offsetToValue` 单测 |
| 5 | **B2 自适应（分两步）** | B5（竖向 dock 折射参数） | 5a 与 5b **串行** | 5a：尺寸类别 + 竖向 dock；5b：详情双栏 |
| 6 | **B6 截图 + 迁移测试** | B2~B5 全部稳定 | 串行（最后） | 5 档矩阵 baseline + `RoomMigrationTest` + `25.json` |

**并行动作限制**：`StatsScreen.kt` 同一时刻只允许一个批次改（B1、B3、B4 都碰它）→ 顺序固定为 **B1 → B3 → B4**，或 B4 先做但只改加载态那一段并立刻合并。

### 6. 文件冲突矩阵

| 文件 | 涉及批次 | 冲突处置 |
| --- | --- | --- |
| `gradle/libs.versions.toml` + `app/build.gradle.kts` | B1 B2 B3 B4 B6 | 依赖声明**按批次追加，不同批次不得改写他人行**；B6 的 `testOptions` 与测试依赖单独一段 |
| `ui/stats/StatsScreen.kt` | B1 B3 B4 | **严格串行**：B1（图表 + 死代码复活）→ B3（日历调用点）→ B4（加载态段落） |
| `ui/stats/CalendarCard.kt` | B3 | 独占 |
| `ui/components/StateComponents.kt` | B4 | 独占 |
| `ui/components/GlassCard.kt` / `GlassControls.kt` / `liquidglass/LiquidGlassTokens.kt` | B5（＋B2 读 `VerticalDock` 预设） | B5 先落预设，B2 再消费 |
| `MainActivity.kt` | B2 | 独占；与 B4 无关（B4 不改 MainActivity） |
| `ui/bottombar/LiquidVerticalDock.kt`（新增） | B2 B5 | **同一批次内先 B5 定参、再 B2 接线**；`LiquidBottomTabs.kt` 全程**不改** |
| `app/src/test/**`（新增测试） | 每批次各自新增文件 | 天然无冲突；**共享的** `ShimmerPolicyTest` / `NirikoWindowLayoutTest` 归各自批次 |

### 7. 每批次的收尾定义（Definition of Done）

1. `.\gradlew.bat assembleDebug` BUILD SUCCESSFUL；
2. `.\gradlew.bat testDebugUnitTest` 全绿，且**用例数不低于上一批次**（新增逻辑必须补测，这是 README 的既有约定）；
3. 本批次新增的纯函数都有对应测试（`NirikoWindowLayout` / `ShimmerPolicy` / 周月映射 / 分桶累计 / `offsetToValue`）；
4. 手机端行为**零变化**（B2 全程强制；其余批次以真机或截图对比确认）；
5. 提交信息包含批次号（`B4:` / `B1:` / `B3:` …）+ 一句话验收结论；
6. 若本批次改了 UI，**在该批次末尾**重录截图 baseline（B6 落地后生效）。

**环境提醒（来自旧计划 B1/B2 记录）**：JDK 用 Android Studio 自带 JBR（`C:\Program Files\Android\Android Studio\jbr`）；本机 PATH 上的 Java 8 会让 Gradle 直接失败（`No Java compiler found`）。

### 8. 开工前必须先完成的 8 件事（checklist）

- [x] 1. 确认 **Q1**（自建两栏 vs `ListDetailPaneScaffold`）→ **自建两栏**（2026 决策落定）
- [x] 2. 确认 **Q2**（日历用 `compose` 还是 `compose-multiplatform`）→ **`compose-multiplatform`**
- [x] 3. 确认 **Q6**（是否移植 miuix squircle，需改 `THIRD-PARTY-NOTICES.md`）→ **移植**，与材质分层拆开验收
- [x] 4. 确认 **Q8**（README 是否允许改错）→ **允许改那 7 处事实，仍不新增章节**
- [ ] 5. 决定 **Q5 改写后的版本**（CI 是否追加截图步骤，建议 `continue-on-error: true`）
- [ ] 6. 工作区 28 改 + 21 未跟踪 → **提交或 stash**（否则批次 diff 不可回滚）
- [x] 7. 跑一次基线：`assembleDebug` + `testDebugUnitTest`（JDK 用 JBR），记录 493 这个数 → **BUILD SUCCESSFUL 12m 1s；493 / 59 类 / 0 失败**（见 §十四·6）
- [x] 8. 第一条动作固定为**依赖可解析性验证**：把 B1/B3/B4 的坐标一次性写进 `libs.versions.toml`，跑 `dependencies` 确认全部拉得下来，再开始写代码（对应 R1/Q7）→ 见 §十四

> 第 8 条尤其重要：**本文档所有库版本取自上游发布记录，未在本机验证过**（沙箱曾拒绝执行 `gradlew`）。先验证再动工，避免写完代码才发现拉不到包。

---

## 十四、决策落定与 B0 依赖探针（执行记录）

### 1. 四项待确认的最终决议

| # | 决议 | 落地要点 |
| --- | --- | --- |
| Q1 | **自建两栏**（不用 `ListDetailPaneScaffold`） | 返回栈自管，避免与现有 `NavHost` 形成两套导航状态；对应 R3 风险由此消解为「自管」而非「冲突」 |
| Q2 | **B3 用 `compose-multiplatform` 变体** | 纯 Android 工程同样可用；保留 `docs/ios-port/` 铺路 |
| Q6 | **移植 miuix squircle 形状** | 必须同步更新 `THIRD-PARTY-NOTICES.md`；与玻璃材质分层**拆成两个验收点**，形状先落 |
| Q8 | **允许修正 README 7 处事实陈述** | 只改事实，不新增章节、不加入参考项目清单 |

### 2. B0 探针落点（坐标写入位置）

- `gradle/libs.versions.toml`：新增三个版本号（`vico` / `calendar` / `shimmer`）与三个库坐标（`vico-compose-m3` / `kizitonwose-calendar` / `compose-shimmer`）。
- `app/build.gradle.kts`：在依赖块末尾新增「ui-upgrade-plan-2026 批次依赖 · B0 可解析性探针」段落，仅声明坐标，**不含任何业务代码引用**。
- **改 toml 必须用编辑器工具，禁止 `Set-Content -NoNewline`**：历史上用该方式改坏过换行，导致 `Unresolved reference: core` / `Unresolved reference: pinyin4j`（见旧趟教训）。

### 3. 探针执行结果（实测，非推断）

命令：`:app:dependencies --configuration debugRuntimeClasspath`（JDK 用 Android Studio JBR 21.0.10；`gradlew` 本次在沙箱下**未被拒绝**，配置缓存生效）。判定口径：**三个坐标全部解析出 Android 变体、且不产生任何 `-->` 版本提升**，才算通过。

| 组合 | Vico | 结果 | 版本提升 |
| --- | --- | --- | --- |
| 组合 1 | 3.3.1 | 解析成功，但**顶版** | `kotlin-stdlib:2.3.10 → 2.4.10`、`androidx.compose.ui:ui-tooling → 1.12.0` |
| 组合 2 | 移出 | 通过 | 无 |
| 组合 3 | 3.2.0（仅接 Vico） | 通过 | 无 |
| 组合 4 | 3.2.0（三件同时） | 通过 | 无 |

三件在最终组合里解析出的实际坐标：

```
+--- com.patrykandpatrick.vico:compose-m3:3.2.0
|    \--- com.patrykandpatrick.vico:compose-m3-android:3.2.0
|         +--- com.patrykandpatrick.vico:compose:3.2.0
|         |    \--- com.patrykandpatrick.vico:compose-android:3.2.0
+--- com.kizitonwose.calendar:compose-multiplatform:2.10.1
|    \--- com.kizitonwose.calendar:compose-multiplatform-android:2.10.1
\--- com.valentinilk.shimmer:compose-shimmer:1.5.0
     \--- com.valentinilk.shimmer:compose-shimmer-android:1.5.0
```

### 4. 结论一：Vico 必须用 3.2.0，不能用 3.3.1（R1 从「待验证」升级为「已定位」）

`dependencyInsight` 的直接证据：

```
org.jetbrains.kotlin:kotlin-stdlib:{strictly 2.4.10} -> 2.4.10
\--- debugCompileClasspath

org.jetbrains.kotlin:kotlin-stdlib:2.4.10
\--- com.patrykandpatrick.vico:compose-m3-android:3.3.1
     \--- debugCompileClasspath (requested com.patrykandpatrick.vico:compose-m3-android:{strictly 3.3.1})
```

- Vico 3.3.1 用的是 **`strictly 2.4.10` 硬约束**（不是普通 requires），因此无法用 `resolutionStrategy` 温和降级——要留 3.3.1 就必须把整个工程升到 Kotlin 2.4。
- 本工程被**两处硬约束**锁在 Kotlin 2.3.10：`gradle/libs.versions.toml:3`（`kotlin = "2.3.10"` / `ksp = "2.3.11"`）与 `app/build.gradle.kts:111` 的注释——miuix 0.9.0 恰需 Kotlin 2.3，0.9.2+ 需 2.4 而 KSP 无对应版本，已明确「不采用」。升 Kotlin 2.4 会连带 KSP 与 Compose 编译器插件（`kotlin-compose` 是 `version.ref = "kotlin"`），是工程级动作，不属于 B1 范围。
- 附带证据：组合 1 里 Compose UI 被抬到 `1.12.0`（`by conflict resolution: between versions 1.12.0, 1.10.3 and 1.10.0`），而 1.12.0 正是历史上判定 `backdrop 2.0.1` 不可用的那条线——即 3.3.1 会顺带把工程推到 R4 明令禁止的版本区间。
- **B1 因此改为 Vico 3.2.0**（同属 v3 API：`CartesianChartHost` / `rememberCartesianChart`，实现要点无需改写）。§二与 §八里写的 3.3.1 一律按本节更正。

### 5. 结论二：日历与 shimmer 原版本可用（B3 / B4 的坐标无需降级）

`compose-multiplatform:2.10.1`（→ android 变体）与 `compose-shimmer:1.5.0`（→ android 变体）在组合 2/3/4 中均无任何版本提升。Q2 选 KMP 变体的决定**已由实测支持**：纯 Android 工程确实解析出 `-android` 变体，不额外要求 `androidTarget()`。

### 6. 基线构建与单测（探针带上依赖后实跑）

命令：`:app:assembleDebug :app:testDebugUnitTest`（同一 JDK；三个新坐标此时已在依赖块中）。

| 项 | 结果 |
| --- | --- |
| 构建 | **BUILD SUCCESSFUL in 12m 1s**，47 个任务全部执行 |
| APK | `app/build/outputs/apk/debug/app-debug.apk`，28.13 MB |
| 单测 | **493 个用例 / 59 个测试类 / 0 失败 / 0 错误 / 0 跳过**（按 `<testsuite tests=…>` 求和与按 `<testcase>` 计数两法一致） |
| Kotlin 编译 | 通过；仅既有 warning（如 `StatsViewModel.kt:108` 的 unchecked cast），无新 error |

> 注意：直接 `[xml](Get-Content -Raw)` 解析这些 XML 会失败（测试中文名被按默认编码读成乱码，`<testcase name="…">` 里的字符会破坏 XML 解析）。本次改用「只在头 3 行正则读 `tests=`/`failures=`/`errors=`」的方式聚合，两种口径都得到 493。

因此 §十三·1 基线的「493」得到独立复核；§十三·7「跑一次基线」这一项可勾掉。


---

## 附：与配套原型的对应关系

`docs/ui-upgrade-prototype.html` 中的编号与本文章节对应：

| 原型编号 | 对应条目 |
| --- | --- |
| ① 剧集评分曲线（缩放 / 点选） | B1 |
| ② 收藏月度趋势（死代码复活） | B1 |
| ③ 评分对比（个人 vs Bangumi） | B1 |
| ④ 评分分布（分组柱，替代 `nativeCanvas`） | B1 |
| ⑤ 日历周 / 月双模 | B3 |
| ⑥ 骨架屏（含 `reduceMotion` 静止态） | B4 |
| ⑦ 玻璃分层材质 + 形状令牌 | B5 |
| ⑫ 竖向玻璃 dock（替换原「侧栏导航」方案） | B2（与 B5 联动） |
| ⑨ 详情双栏（平板） | B2 |
| ⑩ 折痕不切断内容 | B2 |
| ⑪ 截图矩阵（5 档玻璃 × 主题 × 宽度） | B6 |
