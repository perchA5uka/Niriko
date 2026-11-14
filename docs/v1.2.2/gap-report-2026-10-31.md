# v1.2.2 差距审计（2026-10-31）

> **历史审计快照**：本文是 2026-10-31 10:00–10:25 的源码快照判读；本轮之后的最终状态见 `docs/v1.2.2/impl-summary.md` §6.11 与 `docs/v1.2.2/impl-logs/round-2026-10-31-changes.md`（交接单）。正文保留不改，其中的「未修」结论多数已被第八批与并行分支覆盖。

> **审计对象**：`C:\what i made\niriko` 当前工作树（HEAD = `86dd21b` v1.2.1，未提交改动即 v1.2.2）。
> **快照时间**：2026-10-31 10:00–10:25（Asia/Shanghai）。**本报告以快照时刻读到的源码为准**，行号可能因并行改动漂移 ±几行。
> **重要前提**：审计期间工作树仍在被并行实现修改（实测 `StatNumber` 从"零调用"变成接线 6 个文件、`TagChip` 从 6 调用变成 0 调用、M3 `Switch` 从 3 处变成 0 处）。因此本报告的"剩余工作"可能已经由其他批次完成一半；**数字与结论必须在 Lead 合并完所有分支后重跑一次门禁再确认**。
> **方法**：只读 `app/src`；未跑 Gradle / adb / 编译；用 Node 复刻了 6 个源码契约测试的判定逻辑（见 §6）。未修改 `app/src` 下任何文件。

---

## 0. 一句话结论

**审计文档 01/02/03 里被判"未修"的条目，多数已经在本轮并行实现里落地**（V-11/V-12/V-14/V-21/B-1/B-3/B-13/B-15/F-01/F-02/F-03/F-04/F-06/F-09/F-13/F-16），文档的状态表（README §6、impl-summary §7、review-visual §1）已整体过期。
真正还欠的是三类：

1. **一条门禁必红**：`ShapeLiteralPolicyTest` 第 3 个用例（7 个 Shape 工厂消费点基线）实测有 5 个低于基线 → `testDebugUnitTest` 会失败（§6.1，**必修**）。
2. **"建了组件不接线"的回潮**：`NirikoSearchField`（0 调用）、`StatTile`（0 调用）、`TagChip`（迁移后 0 调用）——正是 V-11「GlassSegmentedContainer 零调用」的同类问题换了个组件重演。
3. **原审计里明确"难/高风险"的条目不约而同地被留到最后**：B-9（页面级预测返回）、B-11（搜索卡 12 条并行）、A.9 帧阶段 5/6 处、V-15（30+ 处手写分区标题）、F-08/F-10/F-11/F-14/F-15、S02–S08。

---

## 1. 必修（阻断门禁 / 明确回归）

| # | 状态 | 当前证据 | 剩余工作 | 风险 | 归属 |
|---|---|---|---|---|---|
| **G-1 ShapeLiteralPolicyTest 必红** | 未修 | `app/src/test/.../ui/theme/ShapeLiteralPolicyTest.kt:104-112` 的 `shapeFactoryUses` 基线 vs 源码实测：`CardShape 12<13`、`SheetShape 9<10`、`ControlShape 16<17`、`RowInnerShape 14<15`、`ChipShape 12<13`（`SectionShape 19`、`PillShape 21` 达标） | 二选一：① 把 5 处基线改成当前实测值（并承认"只增不减"语义失效）；② 补回真实消费点。**推荐 ①，同时把断言从"≥ 基线"改成"每个工厂至少 1 处"**，否则这条测试只会反复绊倒无关改动 | **高（门禁红）** | lead |
| **G-2 零调用组件回潮** | 未修 | `ui/components/NirikoSearchField.kt:67` 全仓 **0 调用**（P2-1 的产物）；`ui/components/StatNumber.kt:125 StatTile` **0 调用**（V-21 的产物）；`ui/components/TagChip.kt:38` 迁移到 `NirikoTagPill` 后 **0 调用** | `NirikoSearchField`：接 `SettingsScreen.kt:171` 的 `BasicTextField` 与作品库顶栏；`StatTile`：接 `StatsOverviewSection` 四张状态卡；`TagChip`：删文件或标 `@Deprecated` 并合并进 `NirikoTagPill` | 中（死代码 + 文档说谎） | controls / stats / lead |
| **G-3 新一轮 RenderEffect 同屏预算风险** | 未验证 | `ui/components/NirikoSegmentedControl.kt:78` 非 `nestedInGlass` 时走 `Modifier.appleGlassCard(shape)`；`GlassRenderEffectBudgetTest` 只数**源码构造点**（13 个），数不到"同屏运行几个"。FULL 档下每个独立分段控件都是一处实时折射 | 真机 FULL 档确认「统计页分段 + 顶栏 + 底部导航」同屏 ≤2；或在 `NirikoSegmentedControl` 默认 `nestedInGlass = true`（分段几乎总在卡内） | 高（性能红线） | lead |

---

## 2. 应修（明显但不阻断）

| 编号 | 状态 | 当前证据（路径:行号 + 片段） | 剩余工作 | 风险 | 归属 |
|---|---|---|---|---|---|
| V-02 | 部分修 | 强调色收口成功（`AccentLiteralPolicyTest` 实测通过；`ui/theme/AccentColors.kt` 11 色）。剩余越界：`ui/common/IosStyleSearchComponent.kt:105/108/791/792`（4 处绿系）、`ui/library/LibraryGalleryCard.kt:157/162`（压图文字 `0xFF4a4f47`/`0xFF1a1c19`） | 搜索挡板 4 色并进 `AccentColors`；图库压图文字用 `GlassChipTokens` 的 onScrim | 低 | controls |
| V-03 | 部分修 | 日历 7–9sp 已清零（`Calendar*` 无 `fontSize = N.sp`）；剩 `ui/library/LibraryGalleryCard.kt:160` `fontSize = 9.sp`（压图标签）、`:135 19.sp`、`:163 12.sp` | 9sp → `labelSmall`(11.5)；19sp 用 `titleLarge`；12sp 用 `labelSmall/labelMedium` | 低 | stats |
| V-04 | 刻意不做 | `GlassDecision.kt:43-50` 仍 `FULL → 真玻璃`（D1 用户裁决"不收"）；新增唯一护栏 `GlassRenderEffectBudgetTest` | 无（保持），但 §1 G-3 的"运行期同屏数"仍无护栏 | 中 | lead |
| V-11 | 部分修 | `NirikoSegmentedControl` 已落地 2 处：`ui/stats/CalendarCard.kt:504`、`ui/subject/SubjectDetailScreen.kt:2663`；`GlassSegmentedContainer` 已删除。**残余 2 套"选一个"**：`ui/common/LibrarySearchControls.kt:29 LibraryStatusSearchRow`（自绘状态计数行）、`ui/common/VerticalTopSelector.kt:85`（28dp 玻璃下拉，被 `SearchToolbar.kt:82`、`FolderNavBar.kt:57` 使用） | StatusRow 是"带计数的分段"，可给 `NirikoSegmentedControl` 加 `trailingLabel` 复用；VerticalTopSelector 保留（语义是"下拉"不是"分段"，但圆角 28 与 `SheetShape` 24 并存，见 V-14 记账） | 低 | stats / controls |
| V-13 | 部分修 | M3 `Switch` 已清零；`RangeSlider` 已换 `GlassRangeSlider`（`GlassControls.kt` 内、`SearchFilterPanel.kt:737`）。**剩 1 处 M3 Slider**：`ui/episode/EpisodeDetailScreen.kt:237 Slider(`（剧集评分）| 换 `GlassRatingSlider`（`GlassControls.kt:284` 已有；注意它绑定"收藏评分"语义，需确认 0..10 steps=19 兼容） | 低 | detail |
| V-15 | 部分修 | `SectionHeader` 真实调用仅 4 处：`ui/subject/RatingComparisonSection.kt:57/236`、`ui/subject/SubjectDetailScreen.kt:1413/4411`。**仍有 36+ 处手写 `titleMedium + FontWeight.Bold`**：`PersonDetailScreen.kt:198/216/252/277/326`、`CharacterDetailScreen.kt:146/187/201`、`EpisodeDetailScreen.kt:202/228/263/294/328`、`SubjectDetailScreen.kt:2520/2678/2886/2963`、`StatsScreen.kt:735` 等 | 按文件分批替换为 `SectionHeader(title=…)`；`SubjectDetailScreen` 那几处"收藏编辑层内的小标题"可保留（弹层内节奏不同），需在注释里写明例外 | 中（跨 6 文件、易冲突） | detail / stats |
| V-16 | 部分修 | 已改：`ui/settings/SettingsGroup.kt:185`（headlineSmall）、`ui/stats/CalendarDaySheet.kt:86`。**未改**：`SubjectDetailScreen.kt:650`、`CharacterDetailScreen.kt:78`、`PersonDetailScreen.kt:92`、`EpisodeDetailScreen.kt:106`、`StaffListScreen.kt:58`、`SteamLoginScreen.kt:61` 仍 `titleLarge`(22sp) | 6 处统一到 `headlineSmall`(24sp/w600)；或反向把 `SettingsDetailScaffold` 降回 `titleLarge`（二选一，先拍板） | 低 | lead |
| B-9 | 未修 | `PredictiveDismissHandler` 仍只有 2 个接入点：`NirikoNonModalSheet.kt:102`、`SubjectSearchScreen.kt:134`；`NirikoNavHost.kt` 无 `SeekableTransitionState` / back-progress 消费 | 先按 02 §G.3-1 真机验证 navigation-compose 2.8 是否自带 seek；不自带再做 `LocalBackPreview` + 详情页 `graphicsLayer scale/alpha`（幅度 ≤3%/8%）| 中（需真机验证，本轮不做可） | lead |
| B-11 | 部分修 | reduceMotion 已接（`IosStyleSearchComponent.kt:151,181,206,211,216,233,243,248,422,448,482` 全 `orSnap(reduceMotion)`）；**结构性收敛未做**：`:170 val cardSpring = spring<Dp>(0.8f, 400f)` + 240/220/220 tween 仍各自独立，尺寸仍走 `requiredWidth/Height` | 收敛成单一 `progress` 驱动 + 尺寸走 `graphicsLayer`；属"846 行搜索面重做"，建议**本轮不做**（已在 README/impl-summary 记账） | 高（改搜索面） | lead |
| A.9-1/2 底栏与 dock 每帧重组 | 未修 | `navigation/NirikoBottomBar.kt:54 val hideProgress = bottomBarHideFraction()…`，`:62 .offset { … hideProgress … }` / `:64 graphicsLayer { alpha = 1f - hideProgress }`；`ui/adaptive/NirikoNavSuite.kt:100` + `:121` 同构 | 把 `hideProgress` 改成 `() -> Float` 传进 `offset{}/graphicsLayer{}` 内读；`fullyHidden`/`dockInteractive` 用 `derivedStateOf` 包 | 中（220ms 内每帧重组整条 kyant 底栏） | controls |
| A.9-3 Timeline 组合期算 | 未修 | `ui/stats/TimelineSection.kt:114-117` 组合期算 `pageOffset/scale/alpha`，`:127-131` 再喂 `graphicsLayer` | 三行移进 `graphicsLayer { }` lambda | 低 | stats |
| A.9-4 页面指示器重测 | 未修 | `ui/screens/Screens.kt:174 val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction`，`:184 .size(width = pageIndicatorWidthDp(index, pagePosition).dp, …)` | 改 `Canvas/drawBehind` 自绘，或 `graphicsLayer{ scaleX }` | 低（4 个小 Box） | controls |
| A.9-6 进度条布局期读 | 部分修 | `GlassControls.kt:239-240` 开关 thumb 已改 `graphicsLayer.translationX` ✅；`:408 .fillMaxWidth(fraction)`（GlassProgressBar）、`:589 .offset(x = horizontalPad + …)`（GlassRangeSlider thumb）仍是布局期 | 进度用 `drawBehind`；range thumb 用 `graphicsLayer.translationX` | 低 | controls |
| F-05 pending 态 | 部分修 | 入口已改 `NirikoActionPill`（`CalendarCard.kt:617-621`，48dp），但 `StatsViewModel.refreshBroadcastSchedule()` 仍无 `isRefreshing` 外露 → 点了没有"正在刷" | VM 加 `isRefreshing` + 失败走 `LocalNirikoFeedback` | 低 | stats |
| F-07 选集可发现性 | 部分修 | `NirikoChip.kt:130` `NirikoSelectionPill` 40dp（注释说明刻意不套 48dp 壳）；`SubjectDetailScreen.kt:3053` 三处选集已复用；但**长按触感与"可长按"提示仍无** | 在 `NirikoSelectionPill` 的 `onLongClick` 前补 `HapticFeedbackType.LongPress`（与 Screens.kt:532 同语义） | 低 | detail |
| F-08 搜索菜单无按压 | 未修 | `IosStyleSearchComponent.kt:720` 与 `:745` `.clickable(interactionSource = interactionSource, indication = null, …)`，两处 `interactionSource` 仍无人 `collectIsPressedAsState` | `indication = ripple()` 或叠 `primary α0.08` 按压高亮；补 `semantics { selected }` | 低 | controls |
| F-10 TrendTab 无按压 | 未修 | `ui/common/SearchToolbar.kt:151 indication = null`，已选中再点零反馈 | 同上 | 低 | controls |
| F-11 双击翻面无提示 | 未修 | `ui/subject/DetailPosterCard.kt:89 onClick = {}, onDoubleClick = flip`，无一次性提示 | 复用 `rememberReturnGlowModifier` 的一次性口令范式（首次进详情播 1.5s "双击翻面"） | 低 | detail |
| F-12 X 按钮 44dp | 部分修 | 日历日期条 `width(38.dp)` 已在快照中不存在（视为已修）；`IosStyleSearchComponent.kt:164 anchorSmall = 44.dp` + `:674 .size(anchorSmall)` 仍是视觉=命中区 | 外层 `Box(Modifier.size(48.dp))` 承接点击、视觉保持 44 | 低 | controls |
| F-14 pressTilt haptic 仍未接线 | 未修 | `hapticEnabled = true` 全仓 **0 处**；`PressTilt.kt:62` 仍默认 false，19 个 `pressTilt(` 调用点无一传参 | 只在 5 个主卡（PosterGridCard/LibraryGalleryCard/UniversalSubjectCard/DetailPosterCard/猜你喜欢）显式开；KDoc 注明"滚动容器内不要开" | 低 | controls |
| F-15 保存成功用 LongPress | 未修 | `ui/subject/SubjectDetailScreen.kt:349 haptic.performHapticFeedback(HapticFeedbackType.LongPress)`（收藏保存回执） | 改 `Confirm`（`GlassControls.kt` 满分松手同语义） | 低 | detail |
| S-02 剧集元信息截断 | 未修 | `EpisodeRatingSections.kt:401-413` meta 仍 `joinToString(" · ")` 单行 `maxLines=1 + Ellipsis`，而 `RatingBadge` 在 `:416-420` 先占位 | 拆两行（日期 / 时长+讨论）或给 badge `widthIn(max=48.dp)` | 低 | detail |
| S-03 剧集行无展开外观 | 未修 | `EpisodeRatingSections.kt:365 .clickable { onToggleExpand() }`，行尾仍无 chevron / `stateDescription` | 行尾常驻 `Icons.Outlined.ExpandMore` 16dp，展开时 `graphicsLayer rotationZ` | 低 | detail |
| S-04 "全部 N 张"命中区 | 未修 | `EpisodeRatingSections.kt:519-526 Text("全部 N 张", labelMedium, primary, Modifier.clickable{…})`，无 48dp | 外包 `Box(heightIn(min=48.dp))` | 低 | detail |
| S-07 解绑无确认/无回执 | 未修 | `TmdbBindingSection.kt:176 TextButton(onClick = onUnbind) { Text("解绑") }`；`ProviderBindingSection.kt:112` 同；`SubjectDetailViewModel.unbindTmdb()`(:2213) 无确认、无 snackbar | 红色 `error` 文字 + 二次确认 AlertDialog + `_uiState.snackbarMessage` 回执 | 中（破坏性动作） | detail |
| S-08 infobox 打开链接 | 未修 | `SubjectDetailScreen.kt:4441-4450`：`labelSmall + clip(RoundedCornerShape(8.dp)) + primaryContainer α0.5 + padding(h8,v2)`，无 48dp、无前导图标 | 换 `NirikoActionPill(text="打开链接", icon=OpenInNew)`（`NirikoChip.kt:167` 已具备 48dp 壳） | 低 | detail |
| P2-1 搜索框统一 | 部分修 | 新组件 `NirikoSearchField.kt:67` 已有 focus 描边（`:84-86`），但 **0 调用**；`SettingsScreen.kt:171` 仍是文件内自绘 `BasicTextField`（无 focus 态） | 接 `NirikoSearchField` 到设置页 + 作品库顶栏；再删设置页那段自绘 | 低 | controls |
| P2-2 封面淡入随 item 重建重播 | 未修 | `CoverImage.kt:104 remember(effectiveCoverUrl) { mutableStateOf(false) }`、`SharedSubjectCover.kt:43` 同构 —— 列表项滚出销毁、滚回重播 150ms 淡入 | 列表级 `CompositionLocal` 记已揭示 url，或给列表卡片传 `revealAnimation = false` | 低 | controls |
| B-14 chip 增删无过渡 | 未修 | `ui/subject/SearchFilterPanel.kt` 面板整体进出已有 spatial/effects（`:756-760` 注释），但面板内 chip 集合变化无 `animateContentSize`（全仓 `animateContentSize` 仅 2 处：Screens/SearchResultsPane） | chip 容器加 `animateContentSize(spatialDefault())` | 低 | controls |
| B-16 搜索建议无逐条入场 | 未修 | `IosStyleSearchComponent` 内 `animateItem` 命中 0，仍整块 `rowsAlpha` 淡入 | 建议列表若是 Lazy，加 `animateItem()` | 低 | controls |

---

## 3. 可留 / 刻意保留（不要再报）

| 项 | 依据 | 结论 |
|---|---|---|
| V-04 材质分层 | D1 用户裁决"不收"（`impl-summary.md:11`）；护栏 `GlassRenderEffectBudgetTest` 实测 13 个构造点和基线一致 | 保留；只补 G-3 的运行期同屏验证 |
| 剩余 60 处圆角字面量 | 实测逐值 60 处，与 `ShapeLiteralPolicyTest` 白名单**逐值完全一致**（0/2/3/6/8/10/11/14/28/48/50dp） | 保留；护栏有效 |
| 60 处之外的"28dp 搜索玻璃面 / 50dp dock" | `VerticalTopSelector.kt:118 RoundedCornerShape(28.dp)`、kyant dock 50dp | 既有刻意差，不并入 24/20 |
| 220/350ms、kyant 弹簧 | `NirikoMotionSpecs.CHROME_HIDE_DURATION_MS=220`（3 消费点）、`DETAIL_CHROME_DURATION_MS=180`（2）、详情 350ms、dock spring | 保留（实测消费点数量与 `MotionLiteralPolicyTest` 一致） |
| 搜索挡板 140–260ms tween | `MotionLiteralPolicyTest` 记账 9 处，与源码逐项一致 | 保留（B-11 重做时一并处理） |
| M3 `DropdownMenuItem` 5 处 | `SubjectDetailScreen.kt`（`ControlLanguagePolicyTest` 已锁 `{"SubjectDetailScreen.kt" to 5}`） | 保留（双行副标题无槽位） |
| 分享卡字面量（Color/fontSize/圆角） | `ui/share/**` 隔离域：40 处 fontSize、27 处 Color(0x | 保留 |
| 压图徽标 10sp | `Badges.kt:113/137/142/165` | 保留 |
| `isSystemInDarkTheme` 在 `MainActivity.kt:191`、`Theme.kt:228` | 主题入口按 `ThemeMode.SYSTEM` 取系统值 / 默认参数，合法 | 保留；但 `SubjectDetailScreen.kt:2043` 是**死调用**（`isSystemInDarkTheme().let { LocalDarkTheme.current }`，系统值被丢弃）→ 建议删这一行（低风险） |
| A.9-5 开关 thumb | `GlassControls.kt:239-240` 已改 `graphicsLayer` | 已修 |

---

## 4. 旧口径当前真实计数（app/src/main/java/com/otakup/niriko，快照 10:25）

| 口径 | 当前 | 所在文件（全部） | 对比 v1.2.1 基线 |
|---|---|---|---|
| `FilterChip(` | **0** | — | 15 → 0 |
| `AssistChip(` | **0** | — | 7 → 0 |
| `DropdownMenu(` | **0** | — | 3 → 0 |
| `TagChip(` | **1（仅定义）** | `ui/components/TagChip.kt:38` | 7 调用 → 0 调用（迁移到 `NirikoTagPill`） |
| `OutlinedTextField(` | **1** | `ui/components/NirikoTextField.kt:55`（组件内部基座） | 38 → 1 |
| `NirikoTextField(` | **38** | 16 文件（SubjectDetailScreen 11、DataSource 5、GrayChannel 4、ExternalRating 3、SyncBackup 3、Screens 2、ProviderBinding/TmdbBinding/Wallpaper/ThemeColorPicker/Steam/Episode/FirstRun/ThemeColorPicker、SearchFilterPanel、RatingSource 各 1） | 新增 |
| M3 `Switch(` | **0** | — | 3 → 0 |
| M3 `Slider(`（排除 Glass） | **1** | `ui/episode/EpisodeDetailScreen.kt:237` | 3 → 1 |
| `RangeSlider(`（排除 GlassRangeSlider） | **0** | — | 1 → 0 |
| `fontSize = N.sp` | **62** | `ui/share/ShareCardLayout.kt` 18、`ui/share/ShareCardVariants.kt` 18、`ui/theme/Type.kt` 13（定义）、`ui/components/Badges.kt` 5（10sp 压图）、`ui/share/SharePosterCard.kt` 4、`ui/library/LibraryGalleryCard.kt` 3、`ui/subject/DetailStatCard.kt` 1（注释） | 79 → 62；**非豁免代码仅 LibraryGalleryCard 3 处** |
| `fontSize = N`（裸像素 f） | **0** | — | 1 → 0（`24f` 已改 `8.sp`，`RatingComparisonSection.kt:205`） |
| `Color(0x` | **276** | theme 合法 220（Color 172 / StatusColors 30 / AccentColors 11 / Theme 3 / SeedColorScheme 3 / LiquidGlassTokens 1）、share 27、**越界 29**（CardRarityMaterial 23、IosStyleSearchComponent 4、LibraryGalleryCard 2） | 256 → 276（AccentColors 新增 11、CardRarityMaterial +6） |
| `spring(` | **24 / 10 文件** | 令牌 7（NirikoMotionSpecs）、kyant 11（DampedDrag 5 / LiquidBottomTabs 2 / LiquidVerticalDock 2 / InteractiveHighlight 2）、**业务字面量 3**（`IosStyleSearchComponent.kt:170`、`VerticalTopSelector.kt:109`、`GlassCard.kt:146`）、NirikoPressSpecs 1 | 41 → 24；业务 21 → 3 |
| `tween(` | **29 / 19 文件** | 手写数字 12 处：IosStyle 9（240/220×2/200×2/260×2/160/140）、CardRarity 3000×2、SubjectDetail 350×1；其余为常量/策略引用 | 28 → 29（统计/日历新增 1 处，属状态包络前实验） |
| `AnimatedContent(` | **1** | `ui/stats/CalendarCard.kt:733`（B-15 切月） | 0 → 1 |
| `AnimatedVisibility(` | **14 / 8 文件**（ui 层） | 13 处在快照前已核对；**0 处未接降级**（复刻 `ReduceMotionCoverageTest` 判定通过） | 13 → 14 |
| `.sharedElement(` | **8 / 8 文件** | 与 v1.2.1 一致 | 不变 |
| `LocalReduceMotion` | **112 / 45 文件**；`motionEnabled()` 10 / 9 文件 | — | 30 → 112 |
| `SnackbarHost(` | **11**（含 `LocalNirikoFeedback` 的宿主不入此计数） | + 窗口层 `NirikoFeedbackHost`（`MainActivity.kt:309`） | 10 → 11 |
| `NirikoTagPill/FilterPill/SelectionPill/ActionPill` | **14 / 13 / 6 / 10** | 替掉 M3 chip + 裸绿字 | 新增 |
| `StatNumber(` | **14（12 真实调用 / 6 文件）** | StatsOverviewSection 6、StatNumber 内部 2、RatingComparisonSection 3、DetailStatCard 1、StatsScreen 1、NirikoDonutChart 1 | 新增 |
| `SectionHeader(` | **11**，其中真实组件调用 **4** | + `CalendarDaySheet.kt:166` 私有同名函数 5 处（**这是门禁统计的盲区**，见 §6.2） | 新增 |
| `NirikoSearchField(` / `StateHost(` / `StatTile(` | **1 / 3 / 1**（1 = 仅定义；StateHost 真实 2） | 见 §5 | — |

---

## 5. 组件建了但零调用

**本轮新增却未接线（3 个，必修/应修）**

| 组件 | 定义 | 应接到 |
|---|---|---|
| `NirikoSearchField` | `ui/components/NirikoSearchField.kt:67` | `SettingsScreen.kt:171` 自绘 BasicTextField、`LibraryTopBar` 搜索入口 |
| `StatTile` | `ui/components/StatNumber.kt:125` | `StatsOverviewSection.kt` 四张状态卡（现在仍各自写容器） |
| `TagChip` | `ui/components/TagChip.kt:38` | 迁移后 0 调用 → 删除或合并进 `NirikoTagPill` |

**历史遗留死代码（10 个，v1.2.2 之前就存在，本轮未扩大）**：`EpisodeBadge`（Badges.kt:150）、`LiquidGlassCard`（LiquidGlassCard.kt:33）、`SkeletonList`、`SkeletonCircle`、`NetworkErrorContent`、`EmptyContent`、`SearchEmptyContent`、`ListEmptyContent`、`InlineErrorHint`（均 StateComponents.kt）、`UniversalSearchBar`（UniversalSearchBar.kt:20）。
注：`SkeletonSubjectCard`、`ErrorContent`、`NirikoPillButton`、`StateHost`（CharacterDetail:91 / PersonDetail:123）、`selectionRing`、`nirikoRowIndication`、`NirikoMenuItem` 均已有真实调用。

---

## 6. 门禁测试 vs 实际源码（复刻判定，不跑 Gradle）

> 方法：用 Node 逐条复刻测试的源码扫描逻辑（剥注释规则、正则、白名单、括号配平），在 10:25 快照上执行。

### 6.1 会红

| 测试 | 用例 | 判定 | 证据 |
|---|---|---|---|
| `ui/theme/ShapeLiteralPolicyTest` | `每个令牌Shape工厂都有真实消费点` | **FAIL** | 见 §1 G-1（5 个工厂各少 1 次） |
| 同上 | `令牌档取值不再以字面量出现` | PASS | 0 违规 |
| 同上 | `剩余字面量只许用已记账的取值且不超过基线` | PASS | 逐值完全一致 |

### 6.2 会绿但护栏有盲区（建议本轮顺手加固）

| 测试 | 判定 | 问题 |
|---|---|---|
| `ui/components/ControlLanguagePolicyTest` | PASS | ① `SectionHeader(` 基线 10 把 `CalendarDaySheet.kt:166` 的**私有同名函数**也算进"组件消费点"，组件真实调用只有 4 处 → 这条断言守不住"V-15 没推进"；② 只禁 M3 组件，不禁止 `TagChip` vs `NirikoTagPill` **两套 chip 并存**（迁移后 `TagChip` 变死代码却仍编译进包） |
| `ui/animation/MotionLiteralPolicyTest` | PASS | `allowedSpringCounts` 已过期：白名单仍给 `SearchFilterPanel 8 / GlassControls 1 / SubjectDetailScreen 1`，实际三处均为 0。只减不增方向安全，但"白名单 = 现状"的语义失效，后人加弹簧时仍有 8 次额度可钻 |
| `ui/theme/AccentLiteralPolicyTest` | PASS | 正常（11 色、每个有消费点） |
| `ui/animation/ReduceMotionCoverageTest` | PASS | 正常（折叠面板 3/3/1/1/1、detail 2、search 1、orSnapFinite 2、AnimatedVisibility 0 未降级） |
| `ui/settings/SettingsLayoutContractTest` | PASS | 21 条断言全部复刻通过（含 `start = 20.dp` 缺席、无 ChevronRight、ThemePackSection 2 组） |
| `ui/components/GlassRenderEffectBudgetTest` | PASS | 6 文件构造点与基线逐一相同，总数 13 |

### 6.3 其它门禁注意

- `DialogAndSheetSurfaceTest` / `SettingsSurfaceTokenTest` 未被本轮改动破坏（`NirikoNonModalSheet` 的 72dp/900dp/s 阈值与 Sheet 圆角逐字保留），但 `NirikoMenu` 新增了 **20dp** 的 `NirikoShapes.SheetShape`? —— 实测 `NirikoMenu.kt:68` 用的是 `SheetShape`(24dp)，不冲突。
- `app/build.gradle.kts` spotless ratchet 会扫到本轮新增文件；新增 4 个组件文件目前无通配 import（已核对）。
- README:16 的「1482 项 / 195 类」已不可信：本轮至少又新增 `ControlLanguagePolicyTest`、`GlassToggleMathTest` 扩、`ReduceMotionCoverageTest` 等，**不要在合并前引用这个数**。

---

## 7. 文档声称 vs 源码现实（两个方向）

### 7.1 「文档说没做，源码已经做了」（状态表过期，占绝大多数）

| 文档位置 | 文档口径 | 现实 |
|---|---|---|
| `README.md:83` | 「M3 FilterChip/AssistChip 仍是独立一套、3 套锚定菜单、GlassSegmentedContainer 仍零调用」 | 全部已收口：M3 chip 0、`DropdownMenu` 0、`NirikoMenu` 5 处 + `NirikoSegmentedControl` 2 处，`GlassSegmentedContainer` 已删除 |
| `README.md:86` | 「统计页数字五档未归一」 | `StatNumber`(24/13/11) 已接线 6 文件 |
| `impl-summary.md:140-144` | V-11 / V-12 / V-13 / V-14 / V-21 「未做」 | V-12/V-14 已修；V-11/V-13/V-21 部分修（剩 §2 所列） |
| `impl-summary.md:141` | 「日历格子 7–9sp 未动」 | 日历相关 `fontSize` 字面量已清零 |
| `impl-summary.md:143` | 「V-12 五副 chip 面孔、V-13 玻璃开关/滑块只覆盖设置页」 | chip 已统一；M3 Switch 0、M3 Slider 剩 1 |
| `review-visual.md:11-14` | 「已修 9 / 部分 5 / 未修 11」 | 已过期：V-01/06/07/08/09/10/11/12/13/14/18/19/20/21/22/23/24/25 都有新进展 |
| `01-visual-consistency.md §2` | 多处「未修」与行号 | 行号已漂移（如 `UniversalSubjectCard.kt:105` → `:108-109`） |
| `02-motion-and-transitions.md:178-199` | 「21 spring + 20 tween」 | 24 spring（业务 3）/ 29 tween（手写数字 12） |
| `02-motion-and-transitions.md:33` | `AnimatedContent = 0` | 1（CalendarCard 切月） |
| `README.md:21-89` | v1.2.2 更新段 | 内容基本真实，但缺「四项外观提案（分段/chip/菜单/统计数字）」这一整段（见 §8） |

### 7.2 「文档说做了 / 审计方案要求做，但源码仍缺口」（真正的未落地）

| 来源 | 声称 | 现实 |
|---|---|---|
| `03-interaction-feedback.md:163-172`（F07 方案） | 选集 chip 命中区 48dp + 长按触感 + 可发现性 | `NirikoSelectionPill` 40dp（注释明确不做 48dp 壳）、无长按触感、无提示 |
| `03-interaction-feedback.md:234-243`（F14 方案） | 5 个主卡显式 `hapticEnabled = true` | 全仓 0 处 |
| `03-interaction-feedback.md:245-251`（F15） | 保存成功改 `Confirm` | 仍是 `LongPress`（`SubjectDetailScreen.kt:349`） |
| `03-interaction-feedback.md:174-180`（F08）/ `:190-195`（F10） | 两处 `indication = null` 改 ripple / 语义 | 未动（3 处 `indication = null` 仍在） |
| `03-interaction-feedback.md §6.2`（S02/S03/S04/S07/S08） | A 级截图项（有源码证据） | 全部未修（impl-summary §7 也承认"留待下轮"，但 README v1.2.2 段未列这 5 条） |
| `review-visual.md:75-80`（P2-2） | 封面淡入随 item 重建重播，建议列表级 tracker | 仍 `remember(effectiveCoverUrl)`，未修 |
| `review-visual.md:69-73`（P2-1） | 抽 `NirikoSearchField` 统一搜索框 | 组件已建但 0 调用，设置页仍旧自绘无 focus 态 |
| `02 §A.9 / D.1` | 6 处帧阶段违规 | 5 处未修（仅开关 thumb 一处修了） |
| `06-roadmap.md §7` | 真机矩阵（320/360/411/600/840dp × 7 主题 × 3 玻璃档 × reduceMotion × fontScale）| 无任何记录；`screenshot/` 仍是 2026-09-25 旧图（03 §6.6 的 8 张重拍未做） |
| `01-visual-consistency.md:862-866`（未验证项） | 宽屏、RenderEffect 实测、7 主题矩阵 | 仍全部未验证 |

---

## 8. 文档更正清单（建议由 lead 统一改，避免多人改同一段）

1. **`docs/v1.2.2/README.md:83`**：删「M3 FilterChip/AssistChip 仍是独立一套」「3 套锚定菜单」「GlassSegmentedContainer 仍零调用」三条——已完成。
2. **`README.md:86`**：删「统计页数字字号五档未归一」；改为「统计页数字已收口到 `StatNumber`(24/13/11)，日历角标不再用 7–9sp 字面量」。
3. **`README.md:21-89` v1.2.2 段**：补一节「控件语言统一（外观四项提案，A 方案）」：分段 → `NirikoSegmentedControl`、chip → `NirikoTagPill/FilterPill/SelectionPill/ActionPill`、菜单 → `NirikoMenu`(24dp 玻璃 + 锚点缩放)、统计数字 → `StatNumber`(24/13/11)；并注明 `TagChip`/`StatTile`/`NirikoSearchField` 的接线状态。
4. **`README.md:16`**：「1482 项 / 195 类」在 Lead 合并后重跑一次再写。
5. **`README.md:37`**：「全站 37 处输入框」→ 38 处（`NirikoTextField` 调用点实测 38；其内部基座是 1 处 `OutlinedTextField`，这是刻意保留可访问性语义）。
6. **`docs/v1.2.2/impl-summary.md:140-147`（§7 未做清单）**：V-11/V-12/V-13/V-14/V-21 五行需要按本报告 §2 重写为「已修 / 部分修 + 剩余点」；新增「§7.1 剩余必修：ShapeLiteralPolicyTest 工厂基线」。
7. **`docs/v1.2.2/impl-summary.md:174`**：测试数量与批次计时过期，不要作为验收证据。
8. **`docs/v1.2.2/review-visual.md:9-17`**：结论摘要表整体重判（本报告 §2/§3 可直接引用）；§3 的 P1-1 已修（`RatingComparisonSection.kt:57/232/236`），但"两卡材质不同（GlassSectionCard vs appleGlassCard）"仍是 D1 下的刻意保留，需从 P1 降为"已知取舍"。
9. **`docs/v1.2.2/review-visual.md:75-80`（P2-2）与 `:82-86`（P2-3）**：P2-3 已修（`glassSliderThumbColor`），标记为已修；P2-2 仍在。
10. **`docs/v1.2.2/review-visual.md:88-93`（P2-4）**：已修（触感挪到状态落定，`GlassControls.kt:145` 注释）；P2-5 已修（`StatsScreen.kt:227` 页标题不再叠 Bold）。
11. **`docs/v1.2.2/02-motion-and-transitions.md` §0.1/§A.8/§A.9**：更新计数（spring 24/业务 3、tween 29/手写 12、AnimatedContent 1、AnimatedVisibility 14）与「已修：底栏 offset/graphicsLayer、开关 thumb」；`A.9` 保留 1/2/3/4/6 五条为未修。
12. **`docs/v1.2.2/03-interaction-feedback.md`**：F01/F02/F03/F04/F06/F09/F13/F16 标已修（附本报告路径:行号）；F05/F07 标部分修；F08/F10/F11/F12/F14/F15 标未修；§6.2 的 S02/S03/S04/S07/S08 仍开。
13. **`docs/v1.2.2/06-roadmap.md`**：WP8/WP12 状态更新（WP8 已收口、WP12 剩 B-14/B-16/StatTile）；§7 真机矩阵与 §8 截图基线风险仍未闭环，保持"未验证"而不是"完成"。
14. **`docs/v1.2.2/prototype-pending-changes.html`**：四项均按 A 方案落地，可在文首加一行「已实施（v1.2.2，A 方案）」避免下次重复讨论。

---

## 9. 交给 Lead 的 3 个最高风险

1. **编译门禁**：`ShapeLiteralPolicyTest` 第 3 用例必红（5 个 Shape 工厂消费点各少 1），`:app:testDebugUnitTest` 会失败；且工作树仍在被并行写，**请在所有分支合并后重跑一次再判定**。
2. **视觉/性能**：`NirikoSegmentedControl`(非 nested) 与 `NirikoMenu` 都新引入 `appleGlassCard/searchGlassSurface` 实时折射路径，`GlassRenderEffectBudgetTest` 只数源码构造点数不到"同屏几个"——FULL 档统计页/详情页有越过"同屏 ≤2"的可能，需真机确认。
3. **一致性债收尾不彻底**：V-15（30+ 手写分区标题）、V-16（6 处 22sp 二级页标题）、V-11（两套"选一个"）、B-14/B-16 都停在"做了一半"，而文档已宣布"收口完成"；若本轮不再推进，必须在 README/impl-summary 明确列为"部分修 + 剩余清单"，否则下一轮审计会重复报。
