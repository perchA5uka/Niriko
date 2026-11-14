# v1.2.2 批次 A · 视觉交叉评审

> 对象：`git status` 里的未提交改动（HEAD = v1.2.1），即「批次 A」。
> 方法：只读 `app/src`，用 grep/read 逐条核对 `docs/v1.2.2/01-visual-consistency.md` 的 V-01…V-25；所有证据都是当前源码的 file:line。**未跑 gradle**（编译由 Lead 统一负责），故本文不含「能否编译」结论，只核对到「签名/调用点参数是否对得上」这一层。
> 判定四级：已修（根因消除）/ 部分修（主路径修了、支路仍是旧形态）/ 未修（未触碰）/ 不该修。

## 0. 2026-10-31 复审（第八批后，替代 §1 的旧结论表）

> 本段是第八批（2026-10-31）之后的整体重判；下面 §1–§4 保留为批次 A 当时的历史记录，**旧计数与「未修」结论已过期**。
> 依据：`gap-report-2026-10-31.md`（只读审计）+ 当前源码逐条 grep 复核；每条「已修」都能在源码找到实现，不照抄旧行号。

### 0.1 V-01…V-25 整体重判

| 判定 | 数量 | 编号 |
| --- | --- | --- |
| 已修（根因消除） | 22 | V-02 / 03 / 05 / 06 / 07 / 08 / 09 / 10 / 11 / 12 / 13 / 14 / 16 / 17 / 18 / 19 / 20 / 21 / 22 / 23 / 24 / 25 |
| 部分修 / 记账保留 | 2 | V-01（令牌化继续走令牌，剩 58 处无档位字面量由 `ShapeLiteralPolicyTest` 精确基线锁定）、V-15（`SectionHeader` 4→43 处；剩 6 处 `titleMedium+Bold` 是数值强调，锁死计数） |
| 刻意不做 | 1 | V-04（D1 裁决「不收」；`GlassRenderEffectBudgetTest` 只守不新增构造点） |
| 未修 | 0 | — |

**关键计数更正**（以源码为最终事实）：
- M3 `FilterChip` / `AssistChip` / `DropdownMenu` / `Switch` / `Slider` / `RangeSlider` = **0**（`ControlSourceTest` 锁死）；`OutlinedTextField` = **1**（`NirikoTextField.kt` 内部基座）；`TagChip` / `GlassSegmentedContainer` / `GlassAnchoredMenu` 已删除
- `SectionHeader` 组件真实调用 **43 处**（另有 `CalendarDaySheet` 的私有同名函数 4 处，不计入）
- 6 个 drill-in 页标题统一 `headlineSmall`(24/w600)；`detail` 区手写 `titleMedium+Bold` 仅剩 **6 处**（ExternalRating 1 / SubjectDetail 4 / EpisodeDetail 1）
- `ui/` 下 `Color(0x…)` 只在 `theme/`、`share/`、`LiquidGlassTokens.kt`；`fontSize = N.sp` 只在 `Type.kt`、`Badges.kt`、`share/**`

### 0.2 批次 A 的 5 个新引入问题（P1-1 / P2-1…P2-5）

| 编号 | 现状 | 证据 |
| --- | --- | --- |
| **P1-1** 详情页 RATING 区两卡基线差 16dp | ✅ 已修 | `RatingComparisonSection.kt` 标题换 `SectionHeader`、卡内不再叠 `padding(horizontal = 16.dp)`（由详情页 `contentPadding` 作唯一基线）；柱图补 4dp 圆角 |
| P2-1 设置页搜索框第 6 副面孔、无 focus 态 | ✅ 已修 | 新 `NirikoSearchField`（Pill 玻璃 + 放大镜 + 48dp 清除 + focus 描边）接进 `SettingsScreen`，自绘 `BasicTextField` 删除；`ControlSourceTest` 锁「输入框只有两副面孔」 |
| P2-2 封面 150ms 淡入随 item 重建重播 | ✅ 已修 | `CoverRevealTracker`（进程级、512 FIFO）+ `CoverImage`/`SharedSubjectCover` 读它；`CoverRevealTrackerTest` |
| P2-3 深色下 GlassSlider 圆钮失去白色锚点 | ✅ 已修 | `glassSliderThumbColor(isDark, …)` 深色返回纯白，`GlassSliderThumbContrastTest` 断言对比度 ≥3:1 |
| P2-4 设置页 34 个开关逐个发按压触感 | ✅ 已修 | 触感挪到「值确实翻转」后并改用 `Confirm`（`GlassControls.kt` 注释与实现一致） |
| P2-5 一级页标题 w700 vs w600 | ✅ 已修 | `StatsScreen` 页标题不再叠 `FontWeight.Bold`，与设置页同 `headlineMedium` token |

### 0.3 未验证（保持原判，不要当成已完成）

1. 真机矩阵：320/360/411/600/840dp × 7 主题 × LIGHT/FULL/OFF × reduceMotion × fontScale **仍是 0 记录**；`screenshot/` 仍是 2026-09-25 旧图。
2. 同屏 RenderEffect 实测：`GlassRenderEffectBudgetTest` 只数源码构造点（13）；`NirikoSegmentedControl`（非 nested）与 `NirikoMenu` 在 FULL 档同屏是否 ≤2 未知。
3. B-9 预测返回：只核到 aar 字节码里 NavHost 用 `SeekableTransitionState`，**真机边缘滑动逐帧未验**。
4. B-11：只做字面量/降级/帧阶段，**结构重写未做**；搜索卡真机是否仍抖未验。
5. 第八批新风险：SectionHeader 12dp 节奏、StatNumber 主色薄涂、GlassRangeSlider 双 thumb 相撞、日期条 48dp 窄屏边界、hero 单击 ~300ms 等待、剧集评分 600ms 落库——全部未验证（详见 `impl-summary.md` §10）。
6. 已知取舍（不算缺陷）：详情页 5 处双行 M3 `DropdownMenuItem`、`share/**` 与 `Badges.kt` 的字号/色值隔离、`RatingComparisonSection` Canvas 刻度 8.sp、`Theme.shapes` 收口让 31 处 AlertDialog 圆角 28→24（正向副作用，未逐处验证）。

## 1. 结论摘要（批次 A 当时，已被 §0 覆盖）

| 判定 | 数量 | 编号 |
| --- | --- | --- |
| 已修 | 9 | V-06 / 07 / 08 / 09 / 17 / 18 / 19 / 24 / 25 |
| 部分修 | 5 | V-05 / 13 / 15 / 16 / 20 |
| 未修 | 11 | V-01 / 02 / 03 / 04 / 10 / 11 / 12 / 14 / 21 / 22 / 23 |
| 不该修 | 0 | — |
| **新引入问题** | **P0 0 / P1 1 / P2 5** | 见 §3（另 1 条风险项交真机） |

一句话总评：**设置树这一段真的收口了**——缩进、行高、箭头、按压、禁用态、二级页组卡圆角都收敛到单一来源，并有 `SettingsLayoutContractTest` 用源码文本钉住；详情页两个 P0（「详细信息」标题被滚动容器裁掉、剧集结论条串行）与图表无卡/双线同色无图例也确实修好了。但「同名元素只有一种长相」这个目标**只完成了设置树一段**：chip 仍有 22 处 M3、搜索/输入框成了第 6 副面孔、二级页标题 22 vs 24sp 并存、统计磁贴与作品库统计条原样、Color(0x…)/fontSize 字面量几乎没动，`GlassSegmentedContainer` 仍是零调用。

5 项回归检查（全部实测）：

| 检查项 | 结果 |
| --- | --- |
| 硬编码圆角/字号新增 | 新增 `RoundedCornerShape(8.dp)` 仅 1 处（V-12 旧码平移）；新增 `fontSize` 仅 `Type.kt` 的 24sp/32sp（typography 定义本身）。总数：`RoundedCornerShape(<数字>` 144→140，`fontSize = <数字>` 82→79 |
| `Color(0xFF…)` 新增 | 0（总数仍 256；越界分布见 V-02，本次未恶化） |
| `isSystemInDarkTheme()` | 0（全仓库仍 0） |
| `LocalReduceMotion` 守卫 | 新动画全有守卫：`NirikoMotionSpecs.overlayEnter/overlayExit`、`NirikoNonModalSheet` 松手弹簧、`NirikoRowIndication`（`motionEnabled()`）、`CollapsibleSourceSection`、以及新 `orSnap()` 内建降级路径。**未见缺口** |
| 新可点件 ≥48dp | 达标：`NirikoPillButton` 48、设置行 56/72、`SettingsChevron` 不可点（随整行）、搜索清除 IconButton 48、日历「刷新」胶囊 48。**仍欠**：`GlassToggle` 52×30（靠行点击兜底）、详情页私密开关不在行内 ⇒ 真实命中区 52×30 |

## 2. 逐条对账（V-01…V-25）

| 编号 | 判定 | 证据（只含实测行号/片段） | 备注 |
| --- | --- | --- | --- |
| V-01 | 未修 | `subject/UniversalSubjectCard.kt:105` `shape = RoundedCornerShape(20.dp)` | 该文件 diff 只删了 1 行未用 import，圆角未动 |
| V-02 | 未修 | ui 下 `Color(0x` 共 256 处；合规 theme 188（Color 172 / StatusColors 10 / Theme 3 / SeedColorScheme 3）；越界 `components/CardRarityMaterial.kt` 17、`components/Badges.kt` 8、`stats/CalendarCard.kt` 4、`stats/CalendarDaySheet.kt` 4、`common/IosStyleSearchComponent.kt` 4、`subject/DetailStatCard.kt` 3、`library/LibraryGalleryCard.kt` 2、`components/GlassControls.kt` 1、`liquidglass/LiquidGlassTokens.kt` 1；share 域 24 处按「刻意隔离」豁免 | diff 新增 0，无恶化 |
| V-03 | 未修 | `fontSize = <数字>` 79 处：`stats/CalendarCard.kt` 11、`theme/Type.kt` 12（定义，合规）、`components/Badges.kt` 5（压图徽标，豁免）、`stats/StatsTodaySection.kt` 3、`library/LibraryGalleryCard.kt` 3、`subject/DetailStatCard.kt` 2、`stats/StatsOverviewSection.kt:277` 26sp、share 域 36（豁免） | 9sp 正文仍在 `CalendarCard.kt`/`StatsTodaySection.kt`/`CalendarDaySheet.kt:382` |
| V-04 | 未修 | `components/GlassCard.kt:148-165` 只把高光角度绑到 pressTilt（新增 `LiquidGlassTokens.LIQUID_GLASS_HIGHLIGHT_ANGLE_DEGREES=45f` / `…_TILT_DEGREES=20f`，coerceIn 25..65）；`GlassDecision.cardUsesRealGlass` 未动 | FULL 档所有 `appleGlassCard` 仍是 L2 真折射 = 材质分层债唯一完全没碰的一项 |
| V-05 | 部分修 | 新增 `ui/settings/SettingsLayout.kt:31` `SettingsPageHorizontalInset = 16.dp`；`SettingsGroup.kt:103-106` 组卡自带 16dp、`:224-232` 组标题 16dp、`SettingsScreen.kt:110-120` 页标题 16dp | 支路仍 20dp：`pages/DetailSectionsSettingsScreen.kt:74/:82/:142`、`pages/SyncBackupSettingsScreen.kt:231/:258`、`pages/WallpaperLibraryScreen.kt:199`；`pages/DonateSettingsScreen.kt:117` `RoundedCornerShape(20.dp)` |
| V-06 | 已修 | `settings/pages/ThemePackSection.kt:173/:197` 两组改用 `SettingsPageSplitGroup`（24dp） | 外观页 20/24 并存消失 |
| V-07 | 已修 | `SettingsItem.kt` 所有行 `indication = null` + `nirikoRowIndication(RowInner)` + `heightIn(rowMinHeight(description))`；chevron 统一 `SettingsChevron()`（`SettingsLayout.kt:60`）；`:53` `DisabledRowAlpha = 0.38f` | 矩形水波与两种 chevron 均消失 |
| V-08 | 已修 | `AdaptiveSettingsPane.kt:198-247` 导航行 = `secondaryContainer` α0.35 底 + `NirikoShapes.CardShape` + 72dp + `SettingsRowIconChip` + `SettingsChevron(onDark = selected)` | 宽屏左列选中态可辨 |
| V-09 | 已修 | `SettingsItem.kt` 滑块行删掉 `Surface(secondaryContainer)` 值徽标 → `SettingsValueText`（labelLarge/onSurfaceVariant） | 同组两种「当前值」形态合并 |
| V-10 | 未修 | `OutlinedTextField(` 仍 37 处（只换了设置搜索这 1 处） | 27 个二级页里的文本域仍是方角描边 |
| V-11 | 未修 | `GlassSegmentedContainer` 定义 `components/GlassControls.kt:250`，**调用点 0** | 分段控件仍是 4 套手写形态 |
| V-12 | 未修 | `TagChip(` 7 处 vs M3 四类 chip（`FilterChip` / `AssistChip` / `SuggestionChip` / `InputChip`）22 处（实测）；详情「打开链接」仍 `clip(RoundedCornerShape(8.dp))` | 未长出新面孔，但也一副没并 |
| V-13 | 部分修 | 控件本体大改：拖拽跟手 `GlassToggleMath.kt:28-85`（`SETTLE_VELOCITY=900f`）、圆钮 `GlassControls.kt:411-416`（surfaceContainerHighest + 0.5dp outline α0.4）、按压触感 `:143-150` | **调用点仍 3 个**：`SettingsItem.kt:177`、`SubjectDetailScreen.kt:2598`、`GlassControls.kt:481`；设置页数值行仍是 M3 `Slider`（`SettingsItem.kt:19` import、`:413`） |
| V-14 | 未修 | 菜单加了进场/退场（`GlassAnchoredMenu.kt` keepAlive + `overlayEnter/Exit`），圆角仍 4 档：`screens/LibraryTopBar.kt:95/:148`（20）、`common/IosStyleSearchComponent.kt:286`（28）`/:414`（24）、`common/SearchToolbar.kt:142`（999）、`GlassAnchoredMenu.kt:312`（24） | 动效修了、形态没修 |
| V-15 | 部分修 | 新增 `components/SectionHeader.kt:31`（titleMedium/w600/onSurface/vertical 12dp），调用点仅 3 处：`SubjectDetailScreen.kt:1365`、`:4241`、`RatingComparisonSection.kt:221` | 其余小节标题仍手写 `titleMedium + FontWeight.Bold`：`SubjectDetailScreen.kt:1732/:2394/:2553/:2743/:2820`；设置页 `SettingsGroupTitle` 仍 titleSmall(15sp)+primary → 两套语言未合 |
| V-16 | 部分修 | `theme/Type.kt:53-59` 新增 headlineSmall（24sp/32sp/w600）→ `SettingsGroup.kt:185` 用它 | 其余二级页仍 titleLarge 22sp：`SubjectDetailScreen.kt:642`、`character/CharacterDetailScreen.kt:78`、`person/PersonDetailScreen.kt:91`、`subject/StaffListScreen.kt:58`、`episode/EpisodeDetailScreen.kt:106`、`steam/SteamLoginScreen.kt:61`、`stats/CalendarDaySheet.kt:83` ⇒ 22 vs 24 并存 |
| V-17 | 已修 | `SubjectDetailScreen.kt:4233-4287`：`Column { SectionHeader("详细信息"); Column(heightIn(max=420.dp).verticalScroll(…)) { entries } }` | 标题已移出滚动容器，被裁半截消失 |
| V-18 | 已修 | `EpisodeRatingChart.kt:604-640` 三处 `Row`→`FlowRow`；`:651-668` `ChartStat` 两 Text 均 maxLines=1+Ellipsis | 窄屏不再串行（作者对「没给 weight」有注释说明） |
| V-19 | 已修 | `EpisodeRatingChart.kt:108-113` `themeChartPalette()[0]/[1]`；`:359-370` 与 `:403-412` 两条渲染路径都插图例；`:672-720` `EpisodeRatingChartLegend/LegendItem` | 双线同色 + 无图例均解决 |
| V-20 | 部分修 | `RatingComparisonSection.kt:216-222` 非 compact 路径套 `appleGlassCard().padding(16.dp)` + `SectionHeader("评分分布")`；柱色 `:164` `chartBarDefaultColor()` | 柱仍 `drawRect` 直角（`:188`）；与上方评分卡横向基线差 16dp → 见 §3 P1-1 |
| V-21 | 未修 | `ui/stats/StatsScreen.kt`、`StatsOverviewSection.kt`（磁贴 3 套布局 `:66/:118/:130/:165/:180`、`:258` `RoundedCornerShape(16.dp)`）、`NirikoBarChart.kt` 均不在 `git diff --stat` 内 | 统计页数值五档字号/磁贴三布局原样 |
| V-22 | 未修 | `screens/Screens.kt` 改动只有多选（`selected`/`onLongClick=null`，`:485-500/:553-566`）与批量回执 | 作品库统计条「灰底胶囊 vs 纯绿字」未动 |
| V-23 | 未修 | `RatingComparisonSection.kt:188` `drawRect(` 直角 | 与其它圆角柱图仍不一致 |
| V-24 | 已修 | `components/StateComponents.kt:261-312`：新增 `title: String = "加载失败"`、图标 64dp、重试换 `NirikoPillButton`（`NirikoPillButton.kt:42`，48dp/PillShape） | 6 处调用点全命名参数（CharacterDetail 82/93、PersonDetail 105/119、DiscoverPosterPane 82/181、StateComponents 321） |
| V-25 | 已修 | `StateComponents.kt:106` ControlShape、`:144-161` `SkeletonCard(cardRadius = Card)`+cover clip(20)、`:188` RowInner、`:221` CardShape、`:235` ControlShape | 骨架与真实卡圆角对齐 |

## 3. 新引入问题

### P1-1 详情页 RATING 区块两张卡横向基线差 16dp（+ 标题字重、材质入口三重不一致）

- 现象：新套上的「评分分布」卡比它上方 8dp 处的「评分」卡左右各宽 16dp，左缘明显不齐。
- 证据：`SubjectDetailScreen.kt:967` `contentPadding = PaddingValues(start = 16.dp, end = 16.dp, …)`；`RatingComparisonSection.kt:57`（标题）与 `:65`（卡）各写 `.padding(horizontal = 16.dp)` ⇒ 评分卡实际左缘 **32dp**；新卡 `RatingComparisonSection.kt:216-222` 的 `Column` 无任何水平 padding ⇒ 左缘 **16dp**。同区块标题：`RatingComparisonSection.kt:55-58` = titleMedium + `FontWeight.Bold`，而「评分分布」= `SectionHeader`（w600）。材质：评分卡 `GlassSectionCard`（`RatingComparisonSection.kt:61-67`，surfaceContainerHigh tint）vs 分布卡 `appleGlassCard`（`RatingComparisonSection.kt:218`，白/黑纱）——深色下两卡紧邻可见色温差异。
- 归因：LazyColumn contentPadding 与 section 内部 padding 双重叠加，详情页没有唯一水平基线；违反「同一元素同一种长相」「section 材质只有一个入口」。
- 最小修法：删 `RatingComparisonSection.kt:57` 与 `:65` 的 `padding(horizontal = 16.dp)`，让 `SubjectDetailScreen.kt:967` 的 contentPadding 成为唯一基线；标题统一改 `SectionHeader`；材质把分布卡从 `appleGlassCard` 换成 `GlassSectionCard(backdrop = glassBackdrop, isScrolling = isScrolling)`。更彻底的做法：新增 `ui/subject/DetailSectionScaffold.kt` 的 `fun DetailSection(title: String, backdrop: Backdrop?, isScrolling: Boolean, content: @Composable ColumnScope.() -> Unit)` = SectionHeader + GlassSectionCard + inner 12dp，两卡都走它。
- 验收：新增源码契约测试（沿用 `SettingsLayoutContractTest` 的文本断言法）断言 `ui/subject/RatingComparisonSection.kt` 不含 `padding(horizontal = 16.dp)`；真机竖屏滚到评分区，两卡左右边缘必须重合。

### P2-1 设置页搜索框成为第 6 副「输入框面孔」，且没有 focus 态

- 证据：`SettingsScreen.kt:142-143` `heightIn(min = SettingsRowMinHeight).clip(NirikoShapes.PillShape)` + `:171` `BasicTextField(`；无 `keyboardOptions(imeAction = Search)`、无 focus 描边/底色变化，只有 `cursorBrush = primary`。同站其它搜索：`LibraryTopBar.kt:95/:148`（searchGlassSurface 20dp）、`IosStyleSearchComponent.kt:286`（28）、`SearchToolbar.kt:142`（999）、`VerticalTopSelector.kt:100`（28）、`GlassAnchoredMenu.kt:312`（24）。
- 修法：抽 `components/NirikoSearchField.kt`：`fun NirikoSearchField(value, onValueChange, placeholder, modifier = Modifier, shape = NirikoShapes.PillShape, minHeight = 56.dp, imeAction = ImeAction.Search)`，内部 `BasicTextField` + placeholder + focus 描边（`.border(1.dp, MaterialTheme.colorScheme.primary, shape)`，focused 时）；设置页与作品库改用它。
- 验收：契约测试断言全 ui 下 `BasicTextField(` 只出现在 `NirikoSearchField.kt`（计数 = 1）；真机点入搜索框能看到描边变化。

### P2-2 封面 150ms 淡入在 LazyColumn 滚回时重播

- 证据：`components/CoverImage.kt:104` `var arrived by remember(effectiveCoverUrl) { mutableStateOf(false) }` + `:105-110` `tween(COVER_IMAGE_CROSSFADE_MS).orSnap(reduceMotion || flightActive)`；`components/SharedSubjectCover.kt:43-48` 同构。remember 的 key 只有 url，列表项滚出组合被销毁、滚回重建 ⇒ arrived 复位为 0，重新 150ms 淡入。
- 影响：作品库/发现/搜索列表快速回滚时，每张重新进入视口的封面各闪一次（从全透明到 1）。是否刺眼需真机确认，但代码层面「每次重组合都重播」是确定的。
- 修法：把「已揭示」提升到列表级：`val LocalCoverRevealTracker = compositionLocalOf { mutableSetOf<String>() }`（列表 Composable 里 `remember { mutableSetOf<String>() }` 后 Provide），`CoverImage` 里 `val arrived by remember(url) { mutableStateOf(tracker.contains(url)) }`，动画启动后 `tracker.add(url)`；或给 `CoverImage` 加 `revealAnimation: Boolean = true`，列表项传 `false`（只让首屏/详情页淡入）。
- 验收：真机作品库上下滚三次，封面不应重复淡入；单测 `CoverRevealTrackerTest` 断言同 url 第二次组合初始 alpha = 1f。

### P2-3 深色主题下 GlassSlider 圆钮失去白色锚点

- 证据：`GlassControls.kt:411` `.background(colorScheme.surfaceContainerHighest)` + `:414` `.border(0.5.dp, colorScheme.outline.copy(alpha = 0.4f))`；轨道同为 `surfaceContainerHighest` α0.55（`:164`、`:551`）。thumb 与轨道同源色，仅剩 0.5dp/40% 描边区分。
- 修法：thumb 底色按明暗取样：`if (LocalDarkTheme.current) Color.White.copy(alpha = 0.92f) else colorScheme.surfaceContainerHighest`，或保留现底但把描边提到 `outline` α0.9 / 1dp；并用 `GlassChipTokens.ensureReadable(thumb, trackComposite, 3.0)` 校验图形元素 ≥3:1。
- 验收：真机深色 + 玻璃 OFF 两档各截一张滑杆特写比对；纯函数对比度断言 `GlassSliderThumbContrastTest`。

### P2-4 设置页 34 个开关逐个发按压触感

- 证据：`GlassControls.kt:143-150`：`LaunchedEffect(interactionSource, haptic) { interactionSource.interactions.collect { if (it is PressInteraction.Press) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }`；唯一设置入口 `SettingsItem.kt:177`。
- 影响：连续拨 5 个开关震 5 次「按下」，而约定是触感只用于确认性动作（TextHandleMove 本是文本光标语义）。
- 修法：把触感从 Press 移到状态落定（`onCheckedChange` 回调后、或 `animateFloatAsState` 结束的 settle 分支）并改用 `HapticFeedbackType.Confirm`；或在 `enabled && checked 变化` 时只发一次。
- 验收：真机连续拨 5 个开关只感到 5 次「落定」；无自动化门槛（触感不可测）。

### P2-5 一级页标题字重：收藏统计 w700 vs 设置 w600

- 证据：`stats/StatsScreen.kt:203-206` `headlineMedium` + `fontWeight = FontWeight.Bold`；`settings/SettingsScreen.kt:112` `headlineMedium`（无覆盖，Type.kt 定 w600）。同字号不同字重。
- 修法：删 `StatsScreen.kt:205` 的 `fontWeight = FontWeight.Bold`（token 即 w600），或反向把 headlineMedium 定到 w700 并删调用点覆盖（推荐前者：token 唯一来源）。
- 验收：`grep -c "fontWeight = FontWeight.Bold" ui/stats/StatsScreen.kt` = 0；真机对比「收藏统计」「设置」两页标题粗细一致。

### 风险项（不计入 P 级，需真机确认）：Theme.shapes 收口让 31 处 AlertDialog 圆角 28→24dp

- 证据：`ui/theme/Theme.kt:276` `shapes = NirikoMaterialShapes`；`ui/theme/NirikoMaterialShapes.kt:38` `extraLarge = RoundedCornerShape(NirikoShapes.Sheet)`（24dp，M3 默认 28dp）；`AlertDialog(` 全仓库 34 处，显式传 `shape =` 的 0 处（grep 实测）。
- 判定：**正向副作用**（顺手统一了 V-24 相邻的对话框圆角债），但它是没有逐处验证的全局观感变化，且 `ModalBottomSheet`/`DropdownMenu` 等同样吃 extraLarge。列入 §4 待确认，不算缺陷。

## 4. 未验证项 / 文档与源码不一致

未验证（需真机或编译）：
1. 批次 A 是否编译通过 —— 本轮未跑 gradle；我只核到「签名与调用点参数匹配」这一层（如 `ErrorContent` 6 处全命名参数、`appleGlassCard` 新参数默认值齐全）。
2. 玻璃档位矩阵（FULL / COLLECTION_ONLY / OFF）× 深色 OLED 下，新的「评分分布」卡（appleGlassCard）与邻居（GlassSectionCard）的色温/边缘差是否可接受。
3. 字体缩放 1.5x 时，24sp 的 `SettingsDetailScaffold` 顶栏（maxLines=1）与 22sp 的详情页顶栏是否截断。
4. reduceMotion=true 时新加的三处转场（设置页横向推入 `SettingsPushPolicy`、菜单 scale、封面淡入）是否真的瞬切——代码有守卫，未运行。
5. 375dp 窄屏与平板两栏（`activePane`）两种上下文下，§3 P1-1 的错位是否同样成立。
6. `GlassToggle` 在详情页私密开关（`SubjectDetailScreen.kt:2598`）不在可点行内 ⇒ 命中区 52×30 < 48dp，仍是欠账（设置页已由整行兜底）。

文档与源码不一致（含我上一份审计的偏差，供下一轮收敛）：
1. `01-visual-consistency.md` 写 V-16 方案是「1 处 Type.kt + 7 处调用点」，实际只改了 Type.kt 与 `SettingsDetailScaffold`，其余 7 处未动；本轮按实际状态判为部分修。
2. 该文档把问题描述成「脚手架 24sp 与全站 22sp 不一致」，现状是 24sp 已进 token，不一致方向变成「设置树 24 / 其余二级页 22」。
3. README v1.2.1 段「对话框仍是不透明表面色」「本版未做其余 21 个 AlertDialog 圆角统一」——现状是未显式传 shape 的 31 处默认圆角已从 28 变 24（统一了圆角，但仍非玻璃表面），下一版文档需更正。
4. ui-review 清单「设置组 20/24 并存是有意层级差」仍成立（`SettingsSplitGroup` 20 / `SettingsPageSplitGroup` 24），本批次未破坏。
5. `SettingsLayoutContractTest` 只做源码文本断言（含 `assertFalse(screen.contains("start = 20.dp"))`），挡得住「20dp 回潮」，挡不住「换成 `start = 16.dp + 4.dp`」这类写法；建议补一条静态规则：`ui/settings/**` 的水平内距字面量只允许出现在 `SettingsLayout.kt`。
