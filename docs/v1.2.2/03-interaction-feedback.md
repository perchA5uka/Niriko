# v1.2.2 交互反馈缺口审计（task-3）

> 专项：所有可交互元素的**触点反馈 / 状态变化反馈 / haptic / 可点性错觉 / 反馈时机**。
> 事实来源：v1.2.1 HEAD 源码逐行阅读（`app/src/main/java`），行号均为本轮实测；文档只作判据，不复述。
> 本轮**未修改** `app/src` 下任何文件，未跑 gradle。

## 0.0 2026-10-31 复审状态（第八批后，覆盖下文旧判）

> 下表是第八批（2026-10-31）之后的最终判定；下文 §0–§6 保留为 v1.2.1 审计原文，**「无 pending」「无 48dp」「indication = null」等旧描述已过期**。
> 证据为当前源码实测；行号会漂移，以符号名为准。

| 编号 | 状态 | 证据（当前源码 / 护栏） |
|---|---|---|
| F-01 多选列表视图零反馈 | ✅ 已修 | `ui/components/SelectionRing.kt` + `ui/screens/Screens.kt` LIST 传 `selected`（描边环，零位移） |
| F-02 评分刷新失败清空数据 | ✅ 已修 | `viewmodel/SubjectDetailViewModel.kt` 失败保留旧数据 + `snackbarMessage` 回执 |
| F-03 四个一级页无反馈宿主 | ✅ 已修 | `ui/common/LocalNirikoFeedback.kt` + `MainActivity` 窗口层 `NirikoFeedbackHost`（bottom inset 96dp） |
| F-04 长按浮层「进度+」无上限 | ✅ 已修 | `ui/screens/Screens.kt` 长按浮层加「进度上限」守卫 |
| F-05 统计刷新命中区/无 pending | 🟡 部分修 | 命中区已改 48dp 胶囊（前批）；第八批 `StatsViewModel.isBroadcastRefreshing` 外露 + 胶囊「刷新中…」禁用（`StatsViewModel.kt:87`、`StatsScreen.kt:284`）；**失败回执仍未单独加** |
| F-06 分享失败永久「生成中…」 | ✅ 已修 | `ui/share/SharePreviewSheet.kt` try/catch/finally + 失败提示且不关 sheet |
| F-07 选集 chip 命中区/长按提示 | 🟡 部分修 | `ui/components/NirikoChip.kt:154` 补 `HapticFeedbackType.LongPress`；命中区仍 40dp（`:130` 注释明确刻意不套 48dp 壳），「可长按」提示仍未加 |
| F-08 搜索菜单无按压/无 selected | ✅ 已修 | `ui/common/IosStyleSearchComponent.kt:756` `collectIsPressedAsState` + selected 语义 |
| F-09 禁用行与可用行同貌 | ✅ 已修 | `ui/settings/SettingsItem.kt` 禁用态 α0.38 + 真传 `enabled` |
| F-10 TrendTab 无按压 | ✅ 已修 | `ui/common/SearchToolbar.kt:143` `collectIsPressedAsState` + selected + 按压高亮 |
| F-11 双击翻面零可发现性 | ✅ 已修（有取舍） | `ui/subject/DetailPosterCard.kt:87` 单击即翻面（保留双击）；单击有 ~300ms 双击等待，已记账 |
| F-12 两处 <48dp 自绘热点 | ✅ 已修 | `ui/common/IosStyleSearchComponent.kt:120` `SearchCloseTouchTarget = 48.dp`（视觉 44 保持）；日期条 38dp 已不存在 |
| F-13 多选时长按仍弹浮层 | ✅ 已修 | `ui/screens/Screens.kt` 多选态长按不再弹状态浮层 |
| F-14 pressTilt 触感死通道 | ✅ 已修 | 5 张主卡显式 `hapticEnabled = true`（`PosterGridCard.kt:81`、`LibraryGalleryCard.kt:96`、`UniversalSubjectCard.kt:100`、`DetailPosterCard.kt:87/:117`、详情 hero `SubjectDetailScreen.kt:3010`） |
| F-15 保存成功用 LongPress | ✅ 已修 | `ui/subject/SubjectDetailScreen.kt:345` 改 `HapticFeedbackType.Confirm` |
| F-16 非模态 sheet 把手无触感 | ✅ 已修（前七批） | `ui/components/NirikoNonModalSheet.kt` 首次拖起 `TextHandleMove` 一次 |
| S-02 剧集元信息截断 | ✅ 已修 | `ui/subject/EpisodeRatingSections.kt:413-441`：meta 单行 + 徽标移到下一行 |
| S-03 剧集行无展开外观 | ✅ 已修 | `EpisodeRatingSections.kt:443-450` 行尾常驻 `ExpandMore` + `graphicsLayer rotationZ` |
| S-04 「全部 N 张」命中区 | ✅ 已修 | `EpisodeRatingSections.kt:547` `heightIn(min = 48.dp)` |
| S-07 解绑无确认/无回执 | ✅ 已修 | `ui/subject/TmdbBindingSection.kt:179` 二次确认 `AlertDialog` + VM 回执；`SubjectDetailViewModel.unbindTmdb()` |
| S-08 infobox 打开链接 | ✅ 已修 | `ui/subject/SubjectDetailScreen.kt:4417` `NirikoActionPill(OpenInNew)`（48dp 壳） |

**未验证**：触感/按压类只能真机主观判断（F-07/F-08/F-10/F-12/F-14/F-15 的「手感」），本轮无设备；F-11 的 300ms 双击等待与 F-05 的失败回执属已知取舍/缺口。

## 0 审计方法

### 0.1 机械盘点（`grep -c` on app/src/main/java，去 import/注释后的人工核对值）

| 检索项 | 命中 | 说明 |
| --- | --- | --- |
| `clickable` | 128 | 绝大多数走默认 ripple（M3 主题下 `LocalIndication` = 有界水波） |
| `combinedClickable` | 16 | 真实 6 处：PosterGridCard.kt:89、FolderNavBar.kt:92、UniversalSubjectCard.kt:113、DetailPosterCard.kt:88、SubjectDetailScreen.kt:2865、WallpaperLibraryScreen.kt:330 |
| `IconButton` | 47 | M3 自带 48dp + ripple，缺 contentDescription 的已在 ui-review 专项 |
| `pointerInput` | 33 | 自绘手势（搜索框展开/收起、滑块、图表等），不产生任何按压视觉 |
| `pressTilt` | 43（含定义/调用） | 实际调用 18 处，**无一传 hapticEnabled** |
| `touchGlow` | 3（定义 1 + 调用 2） | 只有 UniversalSubjectCard.kt:103 |
| `indication = null` | **7** | LiquidBottomTab:37、NirikoNonModalSheet:130、DetailPosterCard:88、UniversalSubjectCard:117、SearchToolbar:146、IosStyleSearchComponent:680/702 |
| `semantics { }` | **5** | VerticalTopSelector:101、LibrarySearchControls:35、GlassControls:114、GlassAnchoredMenu:211、DetailPosterCard:96 |
| `role = Role.*` | 9 | 见 §1 表脚注 |
| `toggleable` / `selectable` / `draggable` / `swipeable` | 2 / 7 / 2 / 0 | 侧滑零使用 |
| `performHapticFeedback`（真实调用） | **7** | 见 §4 清点表 |
| `SnackbarHost(` | **10** | 收藏页/发现页/搜索页/统计页**都不在其中** |
| `showSnackbar` | 33 | 集中在设置/详情/同步/B 站/图片查看器 |
| `Toast` | **3** | 全仓仅 ui/stats/CalendarCard.kt:4/311/314 |
| `minimumInteractiveComponentSize` / `LocalMinimumInteractiveComponentSize` / `ProgressBarRangeInfo` | **0 / 0 / 0** | 自绘元件没有任何 48dp 命中区扩展与范围语义 |
| `collectIsPressedAsState` | 5 | GlassCard.kt:143（卡片按压高亮）、SettingsGroup.kt:113（设置行圆角 morph） |

### 0.2 既有反馈通道（审计/方案只允许复用这 8 条，不新造）

| # | 通道 | 定义位置 | 语义 |
| --- | --- | --- | --- |
| C1 | `Modifier.touchGlow()` | ui/components/TouchGlow.kt:28-73 | 白光晕径向扩散（`Color.White α0.24·p`，半径 `minDimension·1.2`）+ 缩放 1→0.97（spring 0.5/300） |
| C2 | `Modifier.pressTilt(...)` | ui/animation/PressTilt.kt:58-163 | 按下边缘倾斜（cameraDistance = 12·density）+ `lighting` 光斑；**自带 haptic 通道（TextHandleMove）但默认关闭** |
| C3 | `appleGlassCard(interactionSource=)` 按压高亮 | ui/components/GlassCard.kt:143-149 | `collectIsPressedAsState` → `highlightAlpha 0.65→0.95`（spring 0.5/400） |
| C4 | `GlassControls` 内部状态 | ui/components/GlassControls.kt | 开关 `animateColorAsState` 轨道（:102-105）、滑块 thumb 位移、评分条金色 + `celebrateScale` |
| C5 | `haptic`（三语义） | LocalHapticFeedback | `LongPress`（长按浮层入口）/ `TextHandleMove`（跨刻度、按压倾斜）/ `Confirm`（满分松手、完成庆祝）。**不受 reduceMotion 影响** |
| C6 | 视觉状态（形状/颜色/位移） | SettingsGroup.kt:113-135、LiquidBottomTabs.kt:207-248 | 设置行按下圆角 morph 4→20dp；dock `pressProgress` 驱动容器 scale + tab scale 1.2 + highlight α |
| C7 | 文案提示 | Snackbar / Toast / 内联 pending 文案 | 一次性消息必须事件式（样例 MainActivity.kt:249-261 的 `TabReselectSignal`） |
| C8 | 选中描边 | PosterGridCard.kt:80-86、LibraryGalleryCard.kt:81 | `border(3.dp, primary, shape)` |

### 0.3 分级与标注规则

- **P0** = 用户一眼看出不对（点了完全没反应 / 数据看起来丢了 / 主流程无确认）。
- **P1** = 明显但不致命（能忍，但每个用户每轮都会碰到）。
- **P2** = 打磨（命中区差几 dp、语义小错、可发现性）。
- 标注【已知欠账】= docs/audit-2026-10/README.md 已登记，本轮只核现状、不重复计数；【刻意保留】= 设计已裁决，不作为缺陷。

### 0.4 未验证项（结论边界）

1. 未跑 gradle/单测（本轮禁止）；下列「验收」均为**待写**契约，不是既有测试。
2. 未真机/模拟器目视：凡写「目视」的条目需在真机确认一次。
3. 未做 TalkBack 实机走查：读屏结论基于 `semantics{}` 静态清点（全仓 5 处）。
4. 未在 60Hz/120Hz 双端对比按压反馈时延。

## 1 反馈矩阵主表

图例：Y=有，N=无，P=部分/有条件。每格后面的 file:line 是通道实现或缺口位置。

| # | 组件（位置） | ripple | glow/tilt | haptic | 视觉状态 | 文案 | 判定 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 手机底栏 tab（LiquidBottomTab.kt:35-41，`interactionSource = null, indication = null`） | N | N | N | **Y**：dock 容器 scale（LiquidBottomTabs.kt:209）+ tab scale 1.2（:226）+ highlight α=pressProgress（:248） | N | P2：反馈成立但只在容器层；`interactionSource=null` 导致读屏无 pressed 态 |
| 2 | 宽屏纵向 dock tab（NirikoNavSuite.kt:151-156） | Y（默认 ripple） | N | N | Y（`tabScale()` 同 1.2） | N | 双通道，与 #1 不一致但不构成缺陷 |
| 3 | 收藏列表卡 LIST（CollectionCard.kt:27-46 → UniversalSubjectCard.kt:99-118） | N（`indication = null`） | Y（`touchGlow()`） | N | **N：无 selected 参数**（多选态零反馈，见 F01） | N | **P0** |
| 4 | 收藏网格/画廊卡（PosterGridCard.kt:78-95、LibraryGalleryCard.kt:81） | Y | Y（pressTilt） | N | Y（selected 3dp 描边） | N | 正向样本 |
| 5 | 详情「猜你喜欢」横滑卡（SubjectDetailScreen.kt:2776-2798） | Y | Y（pressTilt + interactionSource） | N | Y | N | 正向样本 |
| 6 | 详情收藏胶囊（SubjectDetailScreen.kt:2293-2298） | Y | N | N | Y（「添加中…/保存中…」+ disabled，:2249-2255） | Y（保存成功 Snackbar，:337） | 正向（缺触感，P2 可加 C5） |
| 7 | 选集 `SelectionChip`（SubjectDetailScreen.kt:2857-2875） | Y | N | **N（长按也无）** | Y（secondaryContainer + 1.5dp 边框） | N（长按能力零提示） | **P1**，见 F07 |
| 8 | 设置行 switch/picker/radio（SettingsItem.kt:148-153 / :244） | Y | N | N | Y（按下圆角 morph 4→20dp，SettingsGroup.kt:113-125） | N | 正向样本（skill 明确「不要再加水波纹/阴影」） |
| 9 | 设置 action 行（SettingsItem.kt:297-339） | Y | N | N | P（`isLoading` 有 20dp spinner，:328-330；**`enabled=false` 无任何视觉差异**） | N | **P1**，见 F09 |
| 10 | `GlassToggle`（GlassControls.kt:81-140） | —（自绘） | — | N | Y（thumb 位移 + 轨道色；禁用 α0.38，:111） | N | 【已知欠账】30dp 高（:88-91） |
| 11 | `GlassSlider`（GlassControls.kt:164-267） | —（自绘 drag） | — | N | Y（thumb 跟手） | N | 【已知欠账】34dp 高（:192）、无 semantics、无 pressed 态 |
| 12 | `GlassRatingSlider`（GlassControls.kt:284-362） | — | — | **Y**：跨刻度 `TextHandleMove`（:335）、满分 `Confirm`（:357） | Y（金色 + 600ms 庆祝） | N | **正面范本**（haptic 非唯一反馈） |
| 13 | 搜索框 X / 搜索锚点（IosStyleSearchComponent.kt:635-646 / :594-621） | Y | N | N | Y（展开动画） | contentDescription ✓ | P2：X 仅 44dp（`anchorSmall`，:156） |
| 14 | 搜索菜单 Chip / TypeCell（IosStyleSearchComponent.kt:674-682 / :697-703） | **N（indication = null）** | N | N | Y（选中底色 + 文字色） | N | **P1**，见 F08 |
| 15 | 搜索 TrendTab（SearchToolbar.kt:140-158） | **N（indication = null）** | N | N | P（选中底色动画，已选中再点无变化） | N | P2，见 F10 |
| 16 | 非模态 sheet 把手（NirikoNonModalSheet.kt:135-163） | N | N | N | P（仅面板跟手位移） | N（无「可拖」提示） | P2，见 F16 |
| 17 | 锚定菜单（GlassAnchoredMenu.kt:193-222） | 行内默认 ripple | N | N | **N：无出现/消失动画**（:193 直接 `return`） | N | 动效专项，本表只记「无动效」事实 |
| 18 | 长按浮层（Screens.kt:940-1040） | 行默认 ripple | N | P（入口 LongPress ✓，:484/:544；**`+/-` 无触感**） | Y（Check 勾选、进度文本）；`-` 有空值变灰、**`+` 无上限变灰** | N | **P1**，见 F04 |
| 19 | 日历日期条（CalendarCard.kt:380-396） | Y（被 clip 成圆角） | N | N | Y（选中 α0.22 / 今天 α0.30 + 自动滚入视口，:370-372） | N | P2：宽 38dp，见 F12 |
| 20 | 日历格（CalendarCard.kt:944-955） | Y | N | N | Y（hasEvents 描边、isToday 背景） | N | 正向（格子约 48dp） |
| 21 | 统计页「刷新」/ ICS 导出（CalendarCard.kt:589-596 / :602-621） | P（文本 clickable） | N | N | P（ICS 禁用有颜色区分 ✓；刷新无 pending） | P（导出失败 Toast ✓ / **成功零提示**） | **P1**，见 F05 |
| 22 | 分享预览 Sheet 按钮（SharePreviewSheet.kt:173-183） | Y | N | N | Y（`sharing` → 「生成中…」+ disabled） | N（**失败无回滚文案**） | **P1**，见 F06 |
| 23 | 权威评分刷新（ExternalRatingSection.kt:92-94） | Y（IconButton） | N | N | **N（无 pending / 无禁用）** | **N（失败静默且清空数据）** | **P0**，见 F02 |
| 24 | 对话框按钮（M3 AlertDialog / SingleChoiceDialog，SettingsGroup.kt:245-281、Screens.kt:628-675） | Y | N | N | Y（点击关闭 + 状态变化） | N（批量动作后无提示） | 按钮本身合格；结果反馈见 F03 |
| 25 | `TagFilterBar`（Screens.kt:1042-1071）/ 详情页主 CTA（EpisodeRatingSections.kt:447-465） | Y（M3 FilterChip/AssistChip 自带 48dp） | N | N | Y | N | 正向样本 |

> 表脚注：9 处 `role = Role.*` 为 GlassControls:115(Switch)、SettingsItem:244(RadioButton)、VerticalTopSelector:102/143/155、AdaptiveSettingsPane:206(Tab)、NirikoNavSuite:151(Tab)、LibrarySearchControls:36(RadioButton)、LiquidBottomTab:39(Tab)、GlassAnchoredMenu:211(paneTitle)。

## 2 状态变化反馈缺口

### F01【P0】多选模式下「点卡片」在列表视图零反馈，只有顶栏计数在动

- **现象**：收藏页开「多选管理」后，网格/画廊视图点卡有 3dp 主色描边，**列表视图点卡卡片本身毫无变化**——用户只能盯着顶栏「已选 N」的数字确认自己点中了谁；而列表视图恰好是最常用的浏览形态。
- **证据**：
  - 列表项 `app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt:468-492`：`onClick = { if (multiSelect) toggleSelect(item.subject.subjectId) else onSubjectClick(item.subject) }` —— **没有把 `selected` 传给卡片**，也没有任何包裹的选中装饰。
  - 卡片签名 `app/src/main/java/com/otakup/niriko/ui/library/CollectionCard.kt:27-46`：`fun CollectionCard(subject, status, watchedEpisodes, totalEpisodes, watchedVolumes, totalVolumes, myRating, updateTime, personalTags, steam, onClick, onLongClick, sharedElementKey, sharedTransitionScope, animatedVisibilityScope, modifier)` —— **无 `selected` 参数**（`grep selected CollectionCard.kt` = 0 命中）。
  - 对照：网格 `Screens.kt:539-552` 传 `selected = item.subject.subjectId in selectedIds`；`PosterGridCard.kt:81-86` 用 `Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(PosterCardMetrics.CornerRadius))`；`LibraryGalleryCard.kt:81` 同款。
- **归因**：违反「每个可交互元素必须在触点后给出可感知的即时反馈」与四态纪律（选中态是第三态）；同时违反一致性——同一个多选行为在三种视图里两种表现。
- **方案**：
  1. 新建 `app/src/main/java/com/otakup/niriko/ui/library/SelectionRing.kt`：`fun Modifier.selectionRing(selected: Boolean, shape: Shape, color: Color = MaterialTheme.colorScheme.primary, width: Dp = 3.dp): Modifier`，用 `drawWithContent { drawContent(); if (selected) drawRoundRect(color, style = Stroke(width.toPx())) }` **画内描边**（零布局位移，避免多选时列表跳动）。
  2. `CollectionCard` 加 `selected: Boolean = false`，内部 `UniversalSubjectCard(..., modifier = modifier.selectionRing(selected, RoundedCornerShape(20.dp)))`（卡片圆角即 UniversalSubjectCard.kt:107 的 `RoundedCornerShape(20.dp)`）。
  3. `Screens.kt:483` 的 `onClick` 旁加 `selected = multiSelect && item.subject.subjectId in selectedIds`；同时给外层加 `.semantics { this.selected = selected }`（与 LibrarySearchControls.kt:35 同写法），让读屏能报「已选中」。
  4. 把 PosterGridCard/LibraryGalleryCard 的 `border(3.dp…)` 一并换成 `selectionRing`，三者共用，后续改宽度只改一处。
- **验收**：真机：列表多选逐张点，卡内出现 3dp 主色描边、与网格视觉一致、列表不跳动；单测 `SelectionRingTest`（Robolectric）在 `assertIsSelected()` 与 `assertIsNotSelected()` 两条路径上通过（语义侧），描边由目视确认。

### F02【P0】刷新「权威评分」失败时把已有评分清空，且没有任何提示

- **现象**：详情页「权威评分」右侧有一个刷新 IconButton。网络不好时点它：**评分全部消失**，页面上变成空态文案「暂无可用的权威评分。可在「设置 → 权威数据源」配置…」。用户的第一反应是「我的数据被弄丢了」，或者以为配置坏了。
- **证据**：
  - 调用 `app/src/main/java/com/otakup/niriko/ui/subject/ExternalRatingSection.kt:92-94`：`IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "刷新权威评分", tint = onSurfaceVariant) }` —— 无 `isLoading`、无 `enabled`、无结果提示。
  - 实现 `app/src/main/java/com/otakup/niriko/viewmodel/SubjectDetailViewModel.kt:1955-1962`：`fun refreshExternalRatings() { …; extendedScope.launch { val ratings = runCatching { repo.refresh(subject) }.getOrDefault(emptyList()); _uiState.update { it.copy(externalRatings = ratings) } } }` —— **`getOrDefault(emptyList())` 把失败当成功写进 UI 状态**。
- **归因**：违反「失败必须可见、且不得破坏既有可信数据」（四态里的 error 态被折叠成 empty 态）；`runCatching` 吞掉了异常，事件式反馈缺失。
- **方案**：
  1. `refreshExternalRatings()` 改三态：`_uiState.update { it.copy(externalRatingRefreshing = true) }`；`runCatching{}.onSuccess { _uiState.update { s -> s.copy(externalRatings = it, externalRatingRefreshing = false) } }.onFailure { _uiState.update { s -> s.copy(externalRatingRefreshing = false, snackbarMessage = "权威评分刷新失败，已保留上次结果") } }` —— **失败不写空列表**。
  2. 该页已有 Snackbar 宿主（SubjectDetailScreen.kt:348），复用 `state.snackbarMessage`（:322-344 的事件式消费，末尾 `viewModel.clearSnackbarMessage()`）。
  3. 按钮态：`enabled = !refreshing`，`refreshing` 时 18dp `CircularProgressIndicator(strokeWidth = 2.dp, gapSize = 0.dp)`（与 SettingsItem.kt:328-330 同款）。
- **验收**：飞行模式点刷新 → 旧评分仍在 + Snackbar「权威评分刷新失败，已保留上次结果」+ 按钮可再次重试；单测 `ExternalRatingRefreshPolicyTest`（纯函数：`(result, previous) -> UiState`，断言 failure 时 `externalRatings == previous`）。

### F03【P0】四个一级页面没有反馈宿主：批量删除/改状态/加入分区/进度±/导出 全部静默成功

- **现象**：在收藏页多选 → 批量删除 N 部、批量改状态、加入分区；在长按浮层 ± 进度、切状态；在统计页刷新放送日历、导出 .ics —— **全部点完什么文案都没有**。删除是最高危动作，做完连一句「已删除 N 部」都没有，用户只能在列表里自己数。
- **证据**：
  - `SnackbarHost(` 全仓仅 10 处：PersonDetailScreen.kt:85、EpisodeDetailScreen.kt:90、SubjectDetailScreen.kt:348、ImageViewer.kt:221、AdaptiveSettingsPane.kt:325、DataSourceSettingsScreen.kt:86、AppearanceSettingsScreen.kt:109、SyncBackupSettingsScreen.kt:96、WallpaperLibraryScreen.kt:83、BilibiliSyncScreen.kt:127 —— **收藏页(Screens.kt)、发现页(DiscoverGridPane)、搜索页、统计页(StatsScreen) 都不在其中**。
  - 批量动作收尾 `app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt:636-646` / `:663-669`：`onBatchUpdateStatus?.invoke(selectedIds.toList(), status); selectedIds = emptySet(); multiSelect = false; showBatchActions = false`；删除确认 `TextButton(onClick = { showDeleteConfirm = false; onBatchDelete?.invoke(selectedIds.toList()); selectedIds = emptySet(); multiSelect = false })` —— **状态清空即结束，无任何反馈调用**。
  - 统计页 ICS 导出 `app/src/main/java/com/otakup/niriko/ui/stats/CalendarCard.kt:317-318`：成功分支只 `exportBusy = false; showExportDialog = false`，无提示（失败反而有 Toast，:309-316）。
- **归因**：反馈宿主（SnackbarHost）是页面级基础设施，缺失时所有页内动作都不可能给出文案反馈；同时「一次性反馈必须事件式」的纪律在收藏页没有落点（该页连事件流都没有）。
- **方案**：
  1. 新建 `app/src/main/java/com/otakup/niriko/ui/components/NirikoFeedback.kt`：`class NirikoFeedbackHost { private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4); val messages = _messages.asSharedFlow(); fun show(message: String) }` + `val LocalNirikoFeedback = staticCompositionLocalOf<NirikoFeedbackHost?> { null }` + `@Composable fun NirikoSnackbarHost(host: SnackbarHostState, modifier: Modifier = Modifier)`（内部 `collectLatest` 消费，与 SubjectDetailScreen.kt:322-344 同构）。
  2. 在 `ui/adaptive/NirikoNavSuite.kt` 的顶层 Scaffold（撑起一级页面的那一层）挂**一个**统一 `SnackbarHost`，并用 `CompositionLocalProvider(LocalNirikoFeedback provides host)` 下发；四个一级页面共用，避免各建一套。Snackbar 样式沿用 M3 默认但 `shape = RoundedCornerShape(16.dp)` 对齐本仓胶囊语言。
  3. 接入点（最小集）：批量改状态 →「已将 N 部设为「看过」」；批量删除 →「已删除 N 部收藏」+ `actionLabel = "撤销"`（删除前把 `List<SubjectEntity>` 快照存进 host，撤销走既有 restore 路径；若短期不做撤销，至少给纯文案）；批量加入分区 →「已加入「X」」；长按浮层 ± 进度 → 不弹（有进度文本即时变化，避免噪音），成功即静默；ICS 导出成功 →「已导出 X 天放送日历」。
- **验收**：真机：多选删 3 部 → 底部出现「已删除 3 部收藏」；单测 `NirikoFeedbackHostTest`（Robolectric 或纯 JVM + Turbine）：`host.show("x")` 后 `messages.take(1)` 收到一条；`FakeFeedback` 注入后 `onBatchDelete` 断言调用了一次 show。

### F04【P1】长按浮层的「进度 +」没有上限守卫：进度已满时点了完全没反应

- **现象**：某作品 12/12 集，长按卡片打开浮层点「+」—— 什么都不会发生（值被 clamp 在 12），按钮依旧亮着可点；而旁边「−」在 0 时会自动变灰。同一行两个按钮，一个会告诉你「到头了」，一个不会。
- **证据**：
  - `app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt:984-1004`：`-` 是 `IconButton(onClick = { onProgressSet(ProgressBumpPolicy.next(snapshot, -1), …) }, enabled = snapshot.value > 0)`（:984-994）；`+` 是 `IconButton(onClick = { onProgressSet(ProgressBumpPolicy.next(snapshot, 1), …) })` —— **无 `enabled` 参数**（:995-1004）。
  - `app/src/main/java/com/otakup/niriko/util/ProgressBumpPolicy.kt:74-77`：`next()` 内部 `coerceAtMost(total)` → 到上限后值恒等，UI 不变。
- **归因**：可点性错觉——按钮看起来可用、命中后无任何状态变化，也没有「已到上限」的语义。违反「禁用必须可见」与「边界必须可预期」。
- **方案**：`enabled = snapshot.total?.let { snapshot.value < it } ?: true`（未知总数时不设上限，保持现状）；同时把进度标签改为 `ProgressBumpPolicy.label(snapshot)` 加上满值后缀「（已满）」（在 `ProgressBumpPolicy` 内加纯函数 `fun labelWithCap(snapshot): String`，便于单测）。
- **验收**：单测 `ProgressBumpPolicyTest` 增 `labelWithCap` 用例（12/12 → 「进度 12/12 集（已满）」）；真机 12/12 时 `+` 为禁用灰。

### F05【P1】统计页「刷新」是 9-11sp 纯文本：命中区约 20×14dp，且无 pending、无结果

- **现象**：放送日历那一行的「刷新」两个字就是整个刷新入口 —— 手指通常点不中，点中也无法判断有没有在刷（数据没变化时完全看不出发生过）。
- **证据**：`app/src/main/java/com/otakup/niriko/ui/stats/CalendarCard.kt:589-596`：`Text("刷新", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onRefresh))`；标签 11sp → 命中区约 20×14dp（无 padding、无 `minimumInteractiveComponentSize`）。实现 `app/src/main/java/com/otakup/niriko/viewmodel/StatsViewModel.kt:359-365` 只清缓存 + 重新拉取，**无 `isRefreshing`、无错误外露**。
- **归因**：违反 48dp 最小触控目标（niriko-ui-review 硬性项）；异步动作缺 pending 三态。
- **方案**：把 Text 换成 `IconButton(onClick = onRefresh, enabled = !refreshing)` + `Icon(Icons.Filled.Refresh, contentDescription = "刷新放送日历", modifier = Modifier.size(18.dp))` —— 天然 48dp + ripple（与 ExternalRatingSection.kt:92-94、SearchToolButton.kt:13-26 同款）；`refreshing` 为 true 时图标改 16dp `CircularProgressIndicator(strokeWidth = 2.dp)`，或用 `Modifier.graphicsLayer { rotationZ = spin }` 让图标转（reduceMotion 时退化为 spinner）。`StatsViewModel.refreshBroadcastSchedule()` 暴露 `isRefreshing` 状态并在 `fetchBroadcastSchedule` 完成后复位；失败经 F03 的 `LocalNirikoFeedback` 发「放送日历刷新失败」。
- **验收**：单测 `StatsRefreshStateTest`（纯 JVM：viewModel 状态机，刷新中 true → 完成 false）；真机：点刷新出现旋转/加载，失败有 Snackbar。

### F06【P1】分享失败后按钮永久卡在「生成中…」，用户只能关掉 Sheet

- **现象**：分享预览里点「分享」，若海报渲染或存图抛异常（大图 OOM、存储不可写），按钮文字永久停在「生成中…」且永久 disabled；Sheet 关掉重开才恢复。异常被 coroutine 吞掉，没有任何提示。
- **证据**：`app/src/main/java/com/otakup/niriko/ui/share/SharePreviewSheet.kt:60-96`：`var sharing by remember { mutableStateOf(false) }`；`fun doShare() { scope.launch { sharing = true; val bmp = host.renderPoster(...); …; val uri = ShareFlow.saveAndGetUri(...); context.startActivity(ShareFlow.buildChooser(...)); sharing = false; onDismiss() } }` —— **没有 try/catch/finally**；按钮 `Text(if (sharing) "生成中…" else "分享")`、`enabled = !sharing`（:178-183）。
- **归因**：缺少失败回滚（feedback timing 的 finally 缺失）；这是「乐观 UI 无回滚」的典型。
- **方案**：`scope.launch { sharing = true; try { …; onDismiss() } catch (t: Throwable) { feedback?.show("分享失败：" + (t.message ?: "未知错误")) } finally { sharing = false } }`；`remember { mutableStateOf(false) }` 改为 `rememberSaveable`，并在 `DisposableEffect` 里 `onDispose { job?.cancel() }` 取消进行中的任务，避免 Sheet 关闭后仍在写文件。
- **验收**：单测不便（依赖 Activity）；真机：临时把 `ShareFlow.saveAndGetUri` 抛 `IOException`（测试开关）→ 按钮恢复「分享」+ Snackbar。可加纯函数 `ShareResultPolicy`（`(t) -> UiMessage`）便于 JVM 测。

## 3 可点性错觉与触控目标

### F07【P1】选集 `SelectionChip` 命中区约 24-28dp、长按能力零提示、无触感

- **现象**：详情页一部 12 集的番，选集是一排小胶囊「1 2 3 … 12」。胶囊视觉高 ≈ 24-28dp，行内间距小 → 想点第 7 集经常点到第 8 集，**直接改错进度**；长按胶囊能打开单集详情（隐藏手势），但界面上没有任何提示，没人能发现。
- **证据**：`app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt:2857-2875`：`Surface(shape = MaterialTheme.shapes.small, color = …, border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor), modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) + Text(style = labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))` —— labelSmall(11sp) 行高约 14-16dp + 12dp ≈ 26dp；**无 `minimumInteractiveComponentSize`、无 haptic、无长按提示**。调用点 :2914-2919 `onLongClick = epInfo?.let { info -> { onOpenEpisodeDetail(info.id) } }`。
- **归因**：违反 48dp 最小触控目标；隐藏手势违反「可发现性」（niriko-motion：隐藏手势必须有一次可发现的提示）；`combinedClickable` 未接 `LocalHapticFeedback`。
- **方案**：
  1. 命中区：在 `Surface` 外层 `Modifier.minimumInteractiveComponentSize()`（Material3 提供的 48dp 扩展，仓库当前 0 处使用）—— 视觉仍是小胶囊，触摸范围扩到 48dp；若担心相邻 chip 命中区重叠，把行间距从现有值加到 4dp 并把胶囊高度从 `padding(vertical = 6.dp)` 提到 `8.dp`（视觉高 ≈30dp，命中 48dp）。
  2. 长按触感：`val haptic = LocalHapticFeedback.current`，`onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick?.invoke() }`（与 Screens.kt:484-487 长按浮层入口同语义，不新造）。
  3. 可发现性：在选集行首加 `Icon(Icons.Filled.TouchApp, contentDescription = "长按集数可打开单集详情", Modifier.size(14.dp))`，或首次进入详情页时用一次性事件（仿 `rememberReturnGlowModifier` 范式）给第一个未看集扫一次淡光。
- **验收**：单测 `SelectionChipTouchTargetTest`（Robolectric）：`assertTouchHeightIsAtLeast(48.dp)`（compose-ui-test 的 touchBounds 断言）；真机：连点相邻集不误点、长按有振感。

### F08【P1】搜索菜单的 Chip / TypeCell 关闭了 indication：按下期间零反馈，且没有 selected 语义

- **现象**：搜索栏展开后的筛选菜单里，点类型格（全部/动画/书籍…）和小胶囊标签，**按下时完全没有任何视觉变化**，只有在松手后选中底色才切换；读屏念这些格子也听不出「已选中」。
- **证据**：`app/src/main/java/com/otakup/niriko/ui/common/IosStyleSearchComponent.kt:674-682`：`.clip(RoundedCornerShape(16.dp)).background(if (selected) primary.copy(alpha = 0.9f) else Color.Transparent).clickable(interactionSource = interactionSource, indication = null, onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp)`；`:697-703` 同构（TypeCell，选中底色 α0.15）。两处 `interactionSource` 只被创建（:668-672），未被任何 `collectIsPressedAsState` 消费 → **事件流无人监听**。
- **归因**：`indication = null` 在没有替代按压通道（glow/tilt/高亮）时就是反馈真空；M3 语义要求 toggle 类控件暴露 `selected`。
- **方案**：把两处 `indication = null` 改为 `indication = ripple()`（默认水波），并补 `.semantics { this.selected = selected; role = Role.Tab }`；若嫌水波破坏玻璃感，改用仓库既有通道 C3——`Modifier.pressHighlight(interactionSource)`（若不新建，则直接用 `collectIsPressedAsState` 把 `background` 再叠 `primary.copy(alpha = 0.08f)`，与 GlassCard.kt:143-148 同思路，常量取 0.08）。
- **验收**：真机按下有可见反馈；单测 `SearchMenuChipSemanticsTest`（Robolectric）`assertIsSelected()`。

### F09【P1】`SettingsActionRow` 禁用态与可用态长得一模一样

- **现象**：壁纸设置里「轮换壁纸」在开关关掉时、候选壁纸为空时，「生成候选」这类行点不动，但**文字、图标、箭头、底色没有任何变化**，用户只会以为 App 卡了。
- **证据**：`app/src/main/java/com/otakup/niriko/ui/settings/SettingsItem.kt:297-339`：`.clickable(interactionSource, indication = ripple(), enabled = enabled && !isLoading, onClick = onClick)`（:313-318）；`if (isLoading) { Spacer(12.dp); CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, gapSize = 0.dp) }`（:328-330）—— **全函数无任何 `enabled` 相关 alpha/tint 分支**。调用点：`ui/settings/pages/WallpaperLibraryScreen.kt:163 enabled = settings.wallpaperRotationEnabled`、`:175 enabled = candidates.isNotEmpty()`、`ui/settings/GrayChannelSettingsSection.kt:155 enabled = !running`、`ui/settings/KazumiImportSection.kt:95 enabled = !isLoading`。
- **归因**：违反「禁用必须可见」（Material 三态纪律）；对照正向：GlassToggle 用 `.graphicsLayer { alpha = if (enabled) 1f else 0.38f }`（GlassControls.kt:111）。
- **方案**：抽常量 `internal const val DisabledAlpha = 0.38f` 到 `ui/components/` 的新文件（或放进 `ui/theme/`，与 GlassControls.kt:111 共用），`SettingsActionRow` 内 `val contentAlpha = if (enabled) 1f else DisabledAlpha`，用 `CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = contentAlpha))` 包内容（**不要**对整行 `graphicsLayer` 加 alpha，否则会连背景玻璃一起变淡，破坏材质一致性）；`isLoading` 仍保留 spinner（已有）。
- **验收**：真机：关闭壁纸轮换 → 该行变灰；单测 `SettingsDisabledVisualTest`（Robolectric 读 `LocalContentColor` 或用 `assertIsNotEnabled()` + 颜色快照）。

### F10【P2】`TrendTab` 同样关闭了 indication，已选中时再点没有反馈

- **证据**：`app/src/main/java/com/otakup/niriko/ui/common/SearchToolbar.kt:140-158`：`.searchGlassSurface(RoundedCornerShape(999.dp)).background(bg, RoundedCornerShape(999.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp)`；`bg` 由 `selected` 驱动（spring 0.6/400，:135-139）→ 点未选中项有底色动画，**点已选中项时 `selected` 不变，一步反馈都没有**。
- **归因**：同 F08；另外「点当前项」应至少有按压视觉。
- **方案**：`indication = ripple()`（或 C3 按压高亮叠加 `primary.copy(alpha = 0.08f)`）。高度已达标（14dp×2 + titleSmall ≈ 48dp），不需改尺寸。
- **验收**：真机按下有反馈；`TrendTab` 的视觉与搜索菜单内其他 chip 一致。

### F11【P2】详情页封面「双击翻面」零可发现性，单击是空手势

- **现象**：收藏卡的卡背翻面只支持**双击**；单击绑的是空 lambda（故意的：避免误翻），于是用户点一下封面「没反应」，永远不会知道双击能翻。
- **证据**：`app/src/main/java/com/otakup/niriko/ui/subject/DetailPosterCard.kt:86-99`：`Modifier.pressTilt(interactionSource, lighting = lighting).combinedClickable(interactionSource = interactionSource, indication = null, onClick = {}, onDoubleClick = flip)` + `semantics { stateDescription = …, customActions = listOf(CustomAccessibilityAction(flipLabel) { flip(); true }) }`（无障碍侧已照顾，视觉侧没有）。
- **归因**：隐藏手势缺一次性可发现提示。
- **方案**：复用仓库既有的一次性提示范式 `rememberReturnGlowModifier`（PosterGridCard.kt:72-73 注释「刚从详情页返回…扫一次约 300ms 的淡光（一次性事件，播完即清）」）：首次进入详情页且 `record != null` 时，在封面右下角显示 1.5s 的 `Text("双击翻面", labelSmall, onSurfaceVariant)` 并淡出，用 DataStore `hasSeenFlipHint` 只播一次。
- **验收**：真机首次进入详情页看到提示、第二次不再出现；单测 `FlipHintPolicyTest`（纯函数 `shouldShow(hasSeen)`）。

### F12【P2】两处不到 48dp 的自绘热点：日历日期条 38dp、搜索关闭按钮 44dp

- **证据**：`app/src/main/java/com/otakup/niriko/ui/stats/CalendarCard.kt:380-396`：`.width(38.dp).clip(RoundedCornerShape(10.dp)).background(…).clickable { onPickDate(date) }.padding(vertical = 5.dp)`；`app/src/main/java/com/otakup/niriko/ui/common/IosStyleSearchComponent.kt:156`：`val anchorSmall = 44.dp  // 菜单/沉浸态锚点（滑块）尺寸`，被 `:635-646` 的 X 按钮使用。
- **归因**：48dp 最小触控目标；日期条是横滑列表，38dp 宽连点容易选错日期。
- **方案**：日期条外层加 `Modifier.minimumInteractiveComponentSize()`（视觉仍 38dp）；X 按钮 `anchorSmall` 保持 44dp 视觉，外层 `Box(Modifier.size(48.dp))` 承接点击（当前 `.size(anchorSmall)` 同时是视觉与命中区，需拆分）。
- **验收**：`CalendarTouchTargetTest` / `SearchCloseTouchTargetTest`（Robolectric touchBounds）。

### F13【P2】多选模式下长按仍会弹出状态浮层，与「点=选」互相打架

- **证据**：`app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt:484-487` / `:544-547`：`onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showStatusMenu = true }` —— 未判断 `multiSelect`；而 `onClick` 已经分流到 `toggleSelect`。
- **归因**：同一手势在两种模式里语义冲突（用户在多选时想连续多选，长按却打开单条浮层）。
- **方案**：`onLongClick = if (multiSelect) null else { … }`（`combinedClickable` 的 `onLongClick = null` 即不注册长按）。
- **验收**：真机：多选中长按不弹浮层、无振感。

## 4 haptic 缺口

### 4.1 现状清点（全仓真实调用 7 处，语义口径正确）

| 语义 | 位置 | 用途 | 判定 |
| --- | --- | --- | --- |
| LongPress | Screens.kt:485、Screens.kt:545 | 收藏页长按打开状态浮层 | ✓ 正确 |
| LongPress | SubjectDetailScreen.kt:336 | **保存成功后**振一下 | ✗ 语义误用（见 F15） |
| TextHandleMove | GlassControls.kt:335 | 评分跨刻度 | ✓ 正确 |
| TextHandleMove | PressTilt.kt:126 | 按压倾斜（`hapticEnabled` 分支） | ✓ 但**从未被接线**（见 F14） |
| Confirm | GlassControls.kt:357 | 评分满分松手 | ✓ 正确 |
| Confirm | CompletionCelebration.kt:90 | 完成庆祝 | ✓ 正确 |

> 结论：语义口径已收敛到三种（plans/README.md 里「只有 LongPress 3 处」的记录已过期）；缺口不在语义数量，而在**覆盖**。

### F14【P1】`pressTilt` 的触感通道是死代码：18 个调用点没有一个传 `hapticEnabled = true`

- **现象**：卡片按下去有倾斜（视觉有反馈），但没有那一下轻微的机械触感。而 PressTilt 内部明明实现了这个通道。
- **证据**：
  - 签名默认关闭：`app/src/main/java/com/otakup/niriko/ui/animation/PressTilt.kt:58-64`：`internal fun Modifier.pressTilt(maxRotation: Float = 7f, pressScale: Float = 0.99f, enabled: Boolean = true, hapticEnabled: Boolean = false, lighting: PressTiltLighting? = null): Modifier`。
  - 通道实现 `:125-127`：`if (hapticEnabled && interaction.pressPosition.isSpecified) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)`。
  - 调用点（全部未传该参数）：`ui/library/PosterGridCard.kt:78`、`ui/library/LibraryGalleryCard.kt:92`、`ui/subject/UniversalSubjectCard.kt:102`、`ui/subject/DetailPosterCard.kt:87/100`、`ui/subject/SubjectDetailScreen.kt:2798`、`ui/subject/PersonSearchCard.kt:46`、`ui/subject/CharacterStaffSection.kt:119/258`、`ui/search/DiscoverPosterCard.kt:60`、`ui/common/CreditSubjectCard.kt:152`、`ui/person/PersonDetailScreen.kt:345`、`ui/stats/CalendarDaySheet.kt:201/304`、`ui/subject/StaffListScreen.kt:101`。
- **归因**：新增通道未接线（feature 只做了一半）；且默认值 `false` 让它静默失效，没有任何测试能发现。
- **方案**（**不要**全局改默认值为 true，`TextHandleMove` 是细粒度触感，列表滚动误触会变成噪音）：只在「按下去就等于激活」的位置显式开：`PosterGridCard.kt:78`、`LibraryGalleryCard.kt:92`、`UniversalSubjectCard.kt:102`（搜索/收藏主卡）、`SubjectDetailScreen.kt:2798`（猜你喜欢）、`DetailPosterCard.kt:87` 五处 → `Modifier.pressTilt(interactionSource, hapticEnabled = true, lighting = lighting)`。并在 `PressTilt.kt` 的 KDoc 补一句「列表滚动容器内不要开：拖动手势会被识别为按下」。
- **验收**：单测扩 `PressTiltTest`：构造 `hapticEnabled = true` 的 composition，dispatch `PressInteraction.Press` 后断言 fake haptic 收到一次 `HapticFeedbackType.TextHandleMove`（Compose 的 `LocalHapticFeedback` 可用 fake 注入）；真机：按卡有轻微触感、滑动列表不触发。

### F15【P2】「保存成功」用 `LongPress` 语义振：应该是 `Confirm`

- **现象**：详情页收藏保存成功后会振一下；但用的是「长按」语义（弱、短、像误触），而「完成/确认」语义（`Confirm`）才对。用户在两处会听到不同手感描述同一个概念。
- **证据**：`app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt:322-344`：`if (msg == CollectionSavePolicy.SAVED_MESSAGE) { if (completionPlayback.isActive) return@LaunchedEffect; haptic.performHapticFeedback(HapticFeedbackType.LongPress); coroutineScope.launch { snackbarHostState.showSnackbar(msg) } }`；对照正确用法 `GlassControls.kt:357`（满分松手 = Confirm）、`CompletionCelebration.kt:90`（完成 = Confirm）。
- **归因**：haptic 语义混用（`LongPress` 被当成通用的「成功」）。
- **方案**：改为 `HapticFeedbackType.Confirm`；同时 `Screens.kt:485/545` 保留 `LongPress`（长按入口，语义正确）。若担心「保存」还用不到 Confirm 的强度，宁可静默也不要误用 LongPress。
- **验收**：真机对比：保存成功的手感与评分满分松手一致；`grep HapticFeedbackType.LongPress` 只剩两处长按入口。

### F16【P2】sheet 把手没有任何触感或按压视觉，纯靠位移暗示可拖

- **证据**：`app/src/main/java/com/otakup/niriko/ui/components/NirikoNonModalSheet.kt:135-163`：把手 `Box(...).height(28.dp).draggable(Orientation.Vertical, state = rememberDraggableState { … }, onDragStopped = { … })`；视觉只有 `Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(onSurfaceVariant.copy(alpha = 0.38f)))`；**无 pressed 视觉、无 haptic、无「上滑关闭」提示**。对照正向：SharePreviewSheet.kt:121-128 在半展开时给出「上滑展开自定义选项」文案。
- **归因**：可发现性 + 触点反馈双缺；按住把手时视觉无变化（只能靠面板跟手判断）。
- **方案**：`drag` 起始时把把手背景 alpha 从 0.38 提到 0.62（`animateFloatAsState`，用 `NirikoMotionSpecs.effectsFast()`）；`onDragStarted` 里 `haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)`（与滑块跨刻度同语义，不新造）；首次显示时给一行「下拉可收起」的 `labelSmall` 提示（复用 SharePreviewSheet.kt:121-128 的文案范式）。
- **验收**：真机拖把手有按压高亮 + 一次触感；`SheetHandleFeedbackTest`（Robolectric 断言 drag 后把手 alpha 变化）。

## 5 分优先级落地

### 5.1 清单

| 级别 | 编号 | 一句话 | 主要落点 | 工作量 |
| --- | --- | --- | --- | --- |
| **P0** | F01 | 列表多选点卡零反馈 | CollectionCard.kt + Screens.kt + 新 SelectionRing.kt | 0.5d |
| **P0** | F02 | 权威评分刷新失败清空数据 | SubjectDetailViewModel.kt + ExternalRatingSection.kt | 0.5d |
| **P0** | F03 | 四个一级页面无反馈宿主 | 新 NirikoFeedback.kt + NirikoNavSuite.kt + Screens.kt | 1.5d |
| P1 | F04 | 长按浮层「+」无上限守卫 | Screens.kt:995 + ProgressBumpPolicy.kt | 0.2d |
| P1 | F05 | 统计页「刷新」9sp 文本入口 | CalendarCard.kt:589 + StatsViewModel.kt:359 | 0.5d |
| P1 | F06 | 分享失败卡在「生成中…」 | SharePreviewSheet.kt:62-96 | 0.2d |
| P1 | F07 | 选集 chip 命中区 24-28dp | SubjectDetailScreen.kt:2857-2875 | 0.5d |
| P1 | F08 | 搜索 chip/TypeCell 无按压、无语义 | IosStyleSearchComponent.kt:674-703 | 0.3d |
| P1 | F09 | SettingsActionRow 禁用无视觉 | SettingsItem.kt:313-330 | 0.3d |
| P1 | F14 | pressTilt haptic 从未接线 | PressTilt 调用点 5 处 | 0.2d |
| P2 | F10 | TrendTab 无 indication | SearchToolbar.kt:144-148 | 0.1d |
| P2 | F11 | 双击翻面无提示 | DetailPosterCard.kt:86-99 | 0.5d |
| P2 | F12 | 日期条 38dp / X 44dp | CalendarCard.kt:380、IosStyleSearchComponent.kt:635 | 0.2d |
| P2 | F13 | 多选态长按冲突 | Screens.kt:484/544 | 0.1d |
| P2 | F15 | 保存成功用 LongPress | SubjectDetailScreen.kt:336 | 0.05d |
| P2 | F16 | sheet 把手无反馈 | NirikoNonModalSheet.kt:135-163 | 0.5d |

合计：P0 3 条（≈2.5 人日）、P1 7 条（≈2.2 人日）、P2 6 条（≈1.45 人日），共 16 条。

### 5.2 落地顺序（每批可独立发版）

1. **批次 A（P0，先补「点了没反应」）**：F03 反馈宿主 → 立刻让 F05/F06/F02 有文案出口；F01 选中环；F02 刷新三态。三件都改 UI 状态，`GlassDecisionTest`/`SettingsSurfaceTokenTest` 不受影响。
2. **批次 B（P1）**：F04（一行判定）、F07（命中区 + 长按触感）、F14（5 个调用点传参）、F08（换 ripple + 语义）、F09（禁用 alpha）、F05（换 IconButton）、F06（try/finally）。
3. **批次 C（P2）**：F10/F11/F12/F13/F15/F16，随下一个小版本一起。

### 5.3 需要新增/复用的测试契约

| 契约 | 覆盖 | 类型 |
| --- | --- | --- |
| `SelectionRingTest`（新） | F01 | Robolectric：`assertIsSelected()` / `assertIsNotSelected()` |
| `NirikoFeedbackHostTest`（新） | F03 | 纯 JVM + Turbine：`show()` → 收到事件 |
| `ExternalRatingRefreshPolicyTest`（新） | F02 | 纯 JVM：failure 时保留上一次列表 |
| `ProgressBumpPolicyTest`（扩） | F04 | 纯 JVM：`labelWithCap` 满值分支 |
| `StatsRefreshStateTest`（新） | F05 | 纯 JVM 状态机 |
| `SelectionChipTouchTargetTest` / `CalendarTouchTargetTest` / `SearchCloseTouchTargetTest`（新） | F07/F12 | Robolectric：`assertTouchWidthIsAtLeast(48.dp)`（若 API 名不符，退化为 `getUnclippedBoundsInRoot()` 断言） |
| `PressTiltTest`（扩） | F14 | fake `LocalHapticFeedback` 断言收到 `TextHandleMove` |
| `SettingsDisabledVisualTest`（新） | F09 | Robolectric：禁用行 `assertIsNotEnabled()` + 颜色断言 |

复用：`DockTouchStateTest`（dock 按压通道，不受本方案影响）、`GlassSliderMathTest`、`GlassAnchoredMenuTest`、`RatingFeedbackPolicy` 相关测试作为回归护栏。

### 5.4 标注：已知欠账（勿当新发现）与刻意保留（勿报）

**【已知欠账，本轮核对仍成立】**
1. `GlassToggle` 高 30dp（GlassControls.kt:88-91，`trackHeight = 30.dp`），独立使用时命中区不足 48dp；设置页内靠 `SettingsSwitchRow` 整行 clickable（SettingsItem.kt:148-153）兜底 → 现状可接受，**不算命中区缺陷**，但若将来在非整行场景独立使用须补 `minimumInteractiveComponentSize`。
2. `GlassSlider` 高 34dp（GlassControls.kt:192）、**无 `ProgressBarRangeInfo`、无 `Role`、无 clickable 语义**（全仓 `ProgressBarRangeInfo` 0 命中）→ 见 docs/audit-2026-10 P0-3，与 docs/m3-expressive-comparison.md P1-A 的官方 Slider 槽位化方案同源，本轮不重复计数。
3. `Modifier.touchGlow()` 的 `pointerInput` 只观察不消费（TouchGlow.kt:65-72）→ 滚动容器内按住后滚动会残留按压态（plans/03 §G10）。
4. `GlassControls` 在组合期读动画值 / 用 `thumbOffset` 每帧重测（GlassControls.kt:132、:259）—— 动效专项。

**【刻意保留，勿报】**
1. dock 的 kyant 弹簧参数与容器级按压缩放（LiquidBottomTabs.kt:207-248）；宽屏 dock 隐藏只位移 24dp（NirikoNavSuite.kt:121）。
2. 详情页收藏胶囊 180ms `FastOutSlowIn` 显隐（SubjectDetailScreen.kt:2278-2282）；220ms 底栏隐藏过渡、350ms 详情材质渐显。
3. 搜索框动画窗口内的点击挡板（IosStyleSearchComponent.kt:583-591，README v1.2.1「搜索遮挡与误触」已裁决）。
4. 重选当前 tab 在非发现页顶部为 NONE（TabReselectPolicy.kt:19-20 注释已说明「无操作」是刻意的），本就不该报缺陷；若要打磨，最多给一条 `TextHandleMove`（属 F 级之外的锦上添花）。
5. 21 个 AlertDialog 圆角未统一、`ThemePalettes` 遗留、shimmer 低档退化、Robolectric 不能渲染真玻璃、设置页 20/24dp 并存 —— 均已在 docs/audit-2026-10 与 v1.2.1 README 登记，不属本轮。
6. 详情页「编辑层贴顶」去把手 —— README v1.2.1 明写刻意。

---

## 6. 真机截图目视复核（v1.2.1）

> task-3 追加项：对 `screenshot/` 下 17 张 1200x2608 真机图逐张目视，只记录**画面上能指认**的问题，并逐条回查 HEAD 源码；不编译、不改 app/src。

### 6.0 方法与证据强度

- 样本：`screenshot/*.jpg` 17 张，mtime 全为 `2026-09-25 19:22–19:33`。我逐张看的是 11 张：发现页steam排名、发现页历史排名、统计页示例2/3/4、详情页示例2–7（其余 6 张 lead 已看）。
- 规则：① 结论必须有「截图文件名 + 画面位置」；② 每条回查 HEAD 源码并给 `路径:行号`；③ 证据强度 **A**（画面可见且 HEAD 源码同向成立）/ **B**（画面可见、HEAD 无法确认，须重拍）/ **C**（画面可见但 HEAD 已改，作废，仅作「截图会骗人」的反例）。
- 手段：`read_image` 逐张；裁切/对比度存疑处用 PIL 放大到 `%TEMP%/niriko_crops`（未写仓库）。

### 6.1 S01｜P0｜这批截图不是 v1.2.1，不能当验收基线（对应 NEW，方法学）

- 证据：HEAD = `86dd21b 2026-10-24 20:10 feat: v1.2.1`，v1.2.0 = `3c58aa3 2026-10-06 19:17`，而截图 mtime 是 `2026-09-25`——**早于两个版本**。四处渲染与 HEAD 源码直接矛盾：标签实心绿 vs `ui/components/TagChip.kt:37-77`（玻璃片）、「分类明细」混入「已看集数」vs `ui/stats/StatsOverviewSection.kt:127-151`（已拆「观看量」独立卡）、绑定卡恒显「匹配度 0%」vs `ui/subject/ProviderBindingSection.kt:316-317`（已按 confidence 算）、发现页「· 点此强制刷新」vs `ui/search/TrendingSection.kt:63-67,106`（已删文案）。
- 方案：把这 17 张从「v1.2.1 验收基线」降级为「v1.2.0 之前的参考」；本节只保留 A 级条目，验收按 6.6 在 HEAD 上重拍。
- 验收：`git log -1 --format=%H` 与图片文件名绑定（重拍命名 `v1.2.1-<hash7>-<画面>.jpg`）。

### 6.2 A 级新条目（画面可见且 HEAD 源码同向成立）

**S02｜P1｜剧集行元信息被评分徽标挤断（F12 类）**
- 画面：`详情页示例7.jpg` 第 5–13 行元信息显示为「2024-05-24 · 00:23:…」，时长永远看不全；右侧「8.2 / TMDb」徽标占掉两行宽。
- 证据：`ui/subject/EpisodeRatingSections.kt:400-413` meta `joinToString(" · ")` 后 `maxLines = 1, overflow = TextOverflow.Ellipsis`；`:415-417` RatingBadge 排在 `weight(1f)` 列之后先占位。
- 方案：meta 拆两行（日期一行、时长+讨论数一行）；或给 RatingBadge 外层 `widthIn(max = 48.dp)` 并把 meta 放回 `weight(1f)`——优先拆行，中文文案变动后更稳。
- 验收：`EpisodeRowMetaTest`（断言 `00:23` 未被 ellipsis）。

**S03｜P1｜剧集整行可点展开，但没有任何可展开外观（F13 类）**
- 画面：`详情页示例7.jpg` 13 行全是纯行（集号方块+标题+元信息+徽标），无 chevron、无「展开」文字，但整行可点=展开。
- 证据：`EpisodeRatingSections.kt:363-368` `.clip(RoundedCornerShape(12.dp)).clickable { onToggleExpand() }`；展开区在 `:420 if (expanded)`，收起态零指示。
- 方案：行尾常驻 `Icon(Icons.Outlined.ExpandMore, 16.dp, alpha = 0.6f)`，展开时 `animateFloatAsState(if (expanded) 180f else 0f, NirikoMotionSpecs.spatialFast)` 旋转；加 `semantics { stateDescription = if (expanded) "已展开" else "已收起" }`。
- 验收：`EpisodeRowExpandAffordanceTest`（存在「展开/收起」语义节点 + 旋转目标值）；真机目视：未展开行右侧有 chevron。

**S04｜P2｜「全部 23 张」入口等于文字本身大小（F12 类）**
- 画面：`详情页示例6.jpg` 右上「全部 23 张」绿字与标题基线对齐，命中区≈文字。
- 证据：`EpisodeRatingSections.kt:519-526` `Text("全部 ${items.size} 张", labelMedium, primary, Modifier.clickable { ... })`，无 padding、无 `minimumInteractiveComponentSize`。
- 方案：外包 `Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd)` + `Modifier.touchGlow()`；视觉字号不变。
- 验收：`EpisodeStillAllEntryTouchTargetTest`（`assertTouchWidthIsAtLeast(48.dp)`）。

**S05｜P1｜同款实心 chip 里混着一个开关和一个单选（F08 类）**
- 画面：`详情页示例6.jpg` 剧集卡底部「移动平均」「全部票数」**同时实心绿**（读起来像两个独立单选），右侧「≥10 票」「≥50 票」描边；下一行「加载 IMDb 逐集评分」也是描边 AssistChip——四者像同一组。
- 证据：`EpisodeRatingSections.kt:166-187`：`FilterChip(selected = showMovingAverage)` 布尔开关 + `listOf(0, 10, 50)` 单选组，全用 Material 默认 selected 实心样式；`:212-228` 的加载动作是 AssistChip。
- 方案：开关改 `FilterChip(selected, leadingIcon = if (selected) Check else null)`（不再靠实心底表达）；单选组前加 `Spacer(6.dp)` + 组首 `labelSmall("票数")`；「加载 IMDb 逐集评分」移出 FlowRow 单独右对齐一行。
- 验收：`EpisodeRatingFilterChipSemanticsTest`（开关 role=Checkbox、单选项 role=RadioButton）；真机目视：同一行不再有两个实心 chip。

**S06｜P2｜双折线同色系且没有图例（NEW）**
- 画面：`详情页示例6.jpg` 折线区两条线都是绿系（一条带圆点、一条不带），一条绿虚线是均线，**全图无图例**。
- 证据：`ui/subject/EpisodeRatingChart.kt:104-105` `averageColor = MaterialTheme.colorScheme.tertiary`、`movingAverageColor = secondary`（主题里都是绿系），且无 legend 绘制。
- 方案：图下方一行图例（8dp 圆点 + `labelSmall`「单集」「移动平均」），颜色直接取 `averageColor/movingAverageColor` 两个参数；或仅在「移动平均」开启时画均线并降到 0.5 alpha。
- 验收：`EpisodeRatingChartLegendTest`；真机目视：不看说明也能分辨两条线。

**S07｜P1｜「解绑」是即时破坏性动作：同色、无确认、无结果提示（NEW）**
- 画面：`详情页示例7.jpg` TMDb 卡右下「详情　解绑」两个绿字并排、同色同权重；`详情页示例3.jpg` 的「搜索」「粘贴 ID / 链接」「绑定」三颗绿字同理。
- 证据：`ui/subject/TmdbBindingSection.kt:172-173` 相邻两个默认 `TextButton`、`ProviderBindingSection.kt:109` 同款；`viewmodel/SubjectDetailViewModel.kt:2117-2139` `unbindTmdb()` 直接 `repo.unbind(...)`，**无确认、无 `_emitSnackbar`**（对比 `:2111` 切季会提示「已切换到第 N 季」），`:2122` 的 unbind 也未包 `runCatching`，失败无出口。
- 方案：解绑按钮 `TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error))`；点击先弹确认 AlertDialog（沿用现有对话框）；ViewModel 包 `runCatching` 并 `_emitSnackbar("已解绑 TMDb" / "解绑失败：…")`（宿主 `SubjectDetailScreen.kt:348` 已有）。
- 验收：`BindingUnbindConfirmTest` + `BindingUnbindSnackbarTest`；真机目视：破坏性动作红色且需二次确认。

**S08｜P1｜infobox「打开链接」胶囊高约 20dp（F12 类）**
- 画面：`详情页示例2.jpg` 官方网站值下方那颗绿胶囊（放大实测 y1965-2002 ≈ 37px/3 ≈ 12dp 纯填充，含字约 20dp），像标签不像按钮。
- 证据：`ui/subject/SubjectDetailScreen.kt:4217-4227`：`labelSmall` + `padding(top = 2.dp)` + `clip(RoundedCornerShape(8.dp))` + `background(primaryContainer.copy(alpha = 0.5f))` + `clickable` + `padding(horizontal = 8.dp, vertical = 2.dp)`。
- 方案：加 `Modifier.minimumInteractiveComponentSize()`（M3 默认 48dp，视觉不变）；可选加 12dp `Icons.Outlined.OpenInNew` 前导图标以更像按钮。
- 验收：`InfoboxLinkChipTouchTargetTest`。

### 6.3 B 级：画面看得见、但 HEAD 无法确认（须先重拍）

- **S09｜P2｜信息卡顶行被卡片边缘横切**（`详情页示例2.jpg` y≈1000，只剩半截、不可读，字形近「作品详情」）——详情页信息区在 v1.2.0 重构过；若重拍复现，疑为 `appleGlassCard` 的 clip 与滚动/折叠头重叠，先复现再定位，不猜代码。
- **S10｜P2｜绑定候选卡副标题出现字面 `null`**（`详情页示例3.jpg`「null / 결즈 밴드 크라이 ~Secr…」）——绑定卡已重构；若复现，是别名拼接未过滤 `null` 字符串，`joinToString` 前加 `filterNotNull().filter(String::isNotBlank)`。
- **S11｜P2｜发现页卡片内类型词重复一行**（`发现页steam排名.jpg` 每卡首行「游戏 ★7.0」＋第四行单独「游戏」）——趋势卡在 v1.2.0 后重构（`TrendingSection.kt:149-151` 注释：头部计数行/排序 chips/「第 N 话」副标题全部移除）；若复现则删重复行。

### 6.4 C 级：截图里像缺陷、HEAD 已改（作废，反例）

- **S12** 标签 7 行 30 枚全实心绿（`详情页示例5.jpg`）→ HEAD `TagChip.kt:37-77` 是玻璃片（`theme/GlassChipTokens.kt:97-108` 即使 glass OFF 也只返回 `White.copy(alpha = 0.10f)`），且只有前 3 枚 emphatic（`SubjectDetailScreen.kt:1723-1732`）。**作废**。
- **S13** 「分类明细」里混着「已看集数 3961」（`统计页示例2.jpg`）→ HEAD `StatsOverviewSection.kt:116-151` 已拆卡。**作废**。
- **S14** 「匹配度 0%」恒 0（`详情页示例3.jpg`）→ HEAD `ProviderBindingSection.kt:316-317` 已修。**作废**。
- **S15** 发现页「上次更新 刚刚 · 点此强制刷新」绿字（两张发现页）→ HEAD `TrendingSection.kt:63-67,106` 已按用户要求删文案，`:86-87` 只渲染「上次更新 X」、整行仍可点。**作废**；注意它与 F05（统计页「刷新」裸文本仍存活）是两回事，别混判。

### 6.5 总判断（对静态审计的影响）

- 静态审计的 **P0/P1 一条都没被截图推翻**：F01（收藏页多选零反馈）、F02（权威评分失败清空）、F03（四页无 Snackbar 宿主）、F06（分享卡死）都发生在「点下去之后」，而这批图只有静态首页，看不到。
- 被截图**推翻**的是三个「看上去就是缺陷」的推断：标签实心绿、分类明细混量纲、匹配度 0%（S12–S14）——HEAD 均已修。所以这批图既不能证实也不能推翻 v1.2.1 的源码结论。
- 唯一真正被推翻的是前提本身：**「17 张截图 = v1.2.1 截图」不成立**（S01）。动效/按压态本来就是截图测不到的东西，本轮静态结论仍以源码为准。

### 6.6 重拍清单（在 `86dd21b` 上，8 张覆盖 P0/P1 的「点下去之后」）

1. 收藏页列表视图 → 长按进多选 → 点第 2 张（F01）
2. 断网后详情页「权威评分」点刷新（F02）
3. 收藏页/发现页做一次批量操作后（F03）
4. 详情页分享 → 生成中与失败态（F06）
5. 统计页「刷新」入口按下（F05）
6. 设置页禁用行（F09）
7. 搜索页 chip 按下瞬间（F08）
8. 详情页剧集行展开与收起对比（S03）

