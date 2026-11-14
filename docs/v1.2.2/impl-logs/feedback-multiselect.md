# feedback-multiselect · WP3 全局反馈宿主 + WP4 多选可见性

范围：03-interaction-feedback.md 的 F01/F02/F03/F04/F05/F06/F13。**未验证编译**（按团队约定不由我跑 gradle）。

## 改动文件与理由

1. **新建 `ui/common/LocalNirikoFeedback.kt`**（F03）
   - `NirikoFeedback(sink)` + `NirikoFeedbackMessage` + `LocalNirikoFeedback`（staticCompositionLocalOf，默认 null → 调用方 `?.show` 静默跳过，预览/单测不崩）。
   - `NirikoFeedbackHost`：内部 remember SnackbarHostState + scope；Box{ content(); SnackbarHost(align BottomCenter) }。样式用 `NirikoShapes.ControlShape` + surfaceContainerHigh α0.96 + action 色 primary。
   - 「后来的提示优先」：sink 里先 dismiss 当前条再 show，避免连点排队弹两条；带 actionLabel 时显式给 `SnackbarDuration.Long`（M3 默认 Indefinite 会钉在屏幕上）。
2. **新建 `ui/components/SelectionRing.kt`**（F01）：`Modifier.selectionRing(selected, color, cornerRadius, width=3.dp)`。
   - 用 drawContent 之后画**内描边**（topLeft 内缩 width/2、尺寸减 width、圆角 cornerRadius-width/2）：border 会参与测量，选中切换会让 LIST 项（带 animateContentSize）抖一下。
   - alpha 用 `animateFloatAsState(effectsFast.orSnap(reduceMotion))`，只重绘不重组；同时挂 `semantics { selected }` 给读屏。
3. **`MainActivity.kt`**（F03）：把窗口层 `Box(Modifier.fillMaxSize())` 换成 `NirikoFeedbackHost(...)`，位置仍在内容与 NirikoNavSuite **之后**（z 序最上，dock 与二级页都盖不住它）。
   - `bottomInset = if (showBottomBar) FEEDBACK_SNACKBAR_BOTTOM_INSET(96dp) else 0.dp`：顶层页有悬浮玻璃 dock 要抬上去；二级页没有 dock，不加余量免得提示飘在屏幕中部。
   - `startInset = if (useVerticalDock) VERTICAL_DOCK_INSET_DP(96dp) else 0.dp`：复用 `ui/adaptive/AdaptiveInsets.kt` 的既有常量。**任务卡写的是 24dp，但那只是 dock 收起时的位移量，展开态宽度是 96dp**；用 24dp 提示条仍会压在竖向 dock 上。
4. **`ui/library/CollectionCard.kt`**（F01）：加 `selected: Boolean = false`，modifier 链末尾 `.selectionRing(color=primary, cornerRadius=NirikoShapes.Card)`。圆角必须传 Card 令牌——UniversalSubjectCard 的玻璃卡片就是 20dp，传错会在四角露白边。
5. **`ui/screens/Screens.kt`**
   - F01：LIST 分支传 `selected = multiSelect && subjectId in selectedIds`（栅格/画廊早就有 selected，只有列表漏了）。
   - F13：LIST/GRID 的 `onLongClick = if (multiSelect) null else ({ 触感; showStatusMenu = true })`——多选态下长按不再弹状态浮层，触感只留给真正会弹浮层的那次。
   - F04：长按浮层加号 `enabled = snapshot.total?.let { value < it } ?: true`（减号侧本来就有；M3 禁用态自带 0.38 透明度）。
   - F03：批量改状态/批量删除收尾各补一条 `feedback?.show(...)` 回执。
6. **`ui/share/SharePreviewSheet.kt`**（F06）：两个出口都改成 try/catch/finally，finally 复位 `sharing`，catch 走 `LocalNirikoFeedback.show("生成分享图失败")`；sheet 不关 → 按钮恢复可点即天然重试入口。取消原样重抛。**sheet 顶角表达式与 containerColor 逐字未动**（ui/DialogAndSheetSurfaceTest 钉住）。
7. **`ui/stats/CalendarCard.kt`**
   - F05：「刷新」9sp 纯文本（约 20×14dp 命中区）→ 胶囊按钮：PillShape + GlassChipTokens.surfaceChipBackground/chipBorder + labelLarge，`heightIn(min=48.dp)` + 水平 padding 16dp，文字色过 `ensureReadable` 保证 4.5:1。左列「上次更新」保持 labelSmall，整行仍是 Row(SpaceBetween, CenterVertically)。
   - F03：ICS 导出成功分支加了回执（原来只失败 Toast、成功静默），失败仍用原 Toast。
8. **`viewmodel/SubjectDetailViewModel.kt`**（F02，按 lead m00059 口径）：`runCatching{}.getOrDefault(emptyList())` → try/catch；失败**不写 externalRatings**（保留旧数据）并 `_emitSnackbar("评分刷新失败，已保留上次结果")`，取消原样重抛。UiState 未改（复用既有 snackbarMessage，详情页已有消费点）。`ui/subject/ExternalRatingSection.kt` 按 lead 指示**未改**。

## 风险 / 未验证

- **反馈是乐观口径**：`CollectionViewModel.batchUpdateStatus/batchDelete` 是 fire-and-forget、不回传成败，所以「已把 N 个收藏设为…」只代表请求已发出。要精确需给 VM 加失败回传（超出本次 bounds）。
- F02 的「点此重试」行**没做**（会牵动 ExternalRatingSection 调用点 / SubjectDetailScreen，lead 定为 follow-up）；用户只能再点一次头部刷新按钮。
- `showUndo` 目前零调用点（批量删除不可撤销，给假 Undo 更糟）；保留为 API 供后续可撤销场景用。
- CalendarCard 的 `IcsExportEntry`（导出入口）仍是 9sp 纯文本链、命中区同样偏小，属同类问题，本次未动。
- Snackbar 与 dock 的验算：96dp 是 dock 高度 + 间隙的估计值，**没有真机截图核对**；宽屏 96dp 起点内缩同理。
## 第五批（task-12）· V-12 胶囊统一 + V-14 锚定菜单
范围：作品库/发现页的裸文字与三套菜单；仅动 3 个文件（Screens.kt / LibraryTopBar.kt / CollectionDashboard.kt）。**未验证编译**。
1. **`ui/library/CollectionDashboard.kt`**：私有 DashboardChip（tonal 底 + labelSmall）整体删除，13 颗统计改 `NirikoTagPill`。
   - 映射：部数、均分 → `emphatic = true`（主统计，实底）；累计集数 / 游戏时长 / 已读 / 完成率 → 默认档；状态计数（在追/抛弃/搁置/看过）→ 默认档，文案 `「${status.label} $count」`。
   - 代价：NirikoTagPill 无 color 形参，状态计数失去原来的 `colorScheme.secondary` 色，改中性文字色（靠文案区分，换来所有胶囊共用一套对比度校验）。
2. **`ui/screens/Screens.kt` TagFilterBar**：M3 `FilterChip`（8dp 方角 + 实心 primaryContainer + 名字/计数两段 labelSmall）→ `NirikoFilterPill`，计数拼进同一段文字，间距 6dp。
3. **`ui/screens/Screens.kt` 长按浮层**：`DropdownMenu` → `NirikoMenu(anchorInRoot, alignRight = false)` + `NirikoMenuItem`；当前状态改行首勾（NirikoMenuItem 无 trailing 槽）。
   - 菜单节点从 Lazy item 提到**页面最外层**（与正文同级、仍在 LocalSearchGlassBackdrop provider 内），state 提升为 `longPressTargetId / longPressAnchor / longPressMenuExpanded`；
   - 锚点来源：LIST/GRID item 包一层 `Box(Modifier.animateItem().onGloballyPositioned { cardBounds = it.boundsInRoot() })`，长按时把 `cardBounds` 写进页面级 anchor；
   - 数据按 targetId 回查 `state.items`（不存 item 快照），筛选/刷新后菜单仍对得上；F04 的加号 `enabled` 与进度 Row 原样保留。
4. **`ui/screens/LibraryTopBar.kt`**：排序 / 视图两个菜单 → `NirikoMenu` + `NirikoMenuItem`（当前项 `Icons.Default.Check`），锚点是包裹 `SearchToolButton` 的 `Box.boundsInRoot()`；
   - 该组件被 PagerTopBar 限死在 56dp 高，而菜单宿主按 constraints 当 viewport → 用 `requiredSize(屏幕宽高) + offset(-boxOrigin)` 把宿主铺满窗口（同 `ui/common/VerticalTopSelector.kt:145`）+ `zIndex(20f)`。
5. 保留未动：多选长按语义、SelectionRing、批量删除回执、dock 弹簧、搜索展开面（B-11）。
6. 未验证：①菜单在 56dp 顶栏里溢出绘制是否被祖先 clip（PagerTopBar 不 clip，未真机确认）；②长按锚点取自 item boundsInRoot，弹出后列表继续滚菜单不跟随（与 M3 DropdownMenu 一致）；③CollectionDashboard 丢 secondary 色；④菜单选中勾从尾部改行首；⑤Lead 名单里的「刷新/上次更新」裸字不在我范围（CalendarCard.kt 属 visual-audit/task-11；发现页 `ui/search/TrendingSection.kt:87`、`ui/search/DiscoverPosterPane.kt:70` 不在 writeScopes）。
