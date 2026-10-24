# 批次 B · 视觉一致性任务卡（B6 / B7 / B9 / B10）

> 范围：U11（玻璃开关与进度条）、U12（表面清单 + 圆角统一）、U16（编辑层贴顶与匀速动画）、U18（收藏按钮两态统一并悬浮）。
> 方法：只读回码，所有 file:line 均已回文件确认；kyant 上游用 jsDelivr 读源码（仓库 >50MB 拿不到文件树 → 走单文件 GET，标 [码-单文件]）。material3 的 API 结论用本机 1.4.0 aar 的 `javap` 实证 [码-字节码]。
> 本批次不改数据层、不加依赖、不动 Room、不跑 gradle。

## 0. 总表

| 卡 | 主题 | 现状基线（回码） | 预估 | 依赖 |
| --- | --- | --- | --- | --- |
| B-6 | 玻璃开关与进度条统一 | 设置 34 行用 M3 Switch；弹层用自研 GlassToggle；7 处 M3 LinearProgressIndicator | M | 无（B-6 先于 B-7b） |
| B-7 | 表面清单与圆角 token 收敛 | 24 处 AlertDialog、3 处底部弹层、3 套二级页卡片圆角、设置条目 morph 圆角 | L（拆 5 批） | B-6、B-9（B-7e 与 B-9 合并） |
| B-9 | 编辑层贴顶 + 匀速动画 | skipPartiallyExpanded 已开；sheet 高度=内容高；M3 1.4.0 无法传 animationSpec | S（A 方案）/ M（B 方案） | B-10（同文件先做） |
| B-10 | 收藏按钮两态统一并悬浮 | 未收藏=M3 Button(:1713)；已收藏=DetailDock(:1989)；两者都在列表流内 | M | B-10 先于 B-9；B-11 依赖本卡 `visible` 接口 |

## 与批次 A 的冲突点（开工前先看）

| 位置 | 批次 A 已改 | 对本批次约束 |
| --- | --- | --- |
| `SettingsSwitchRow` 的 `checked` 来源 | X3 首帧种子（`app/src/test/.../SettingsFirstFrameSeedTest.kt`） | B-6 只能换控件，**不得改 checked 的传参与首帧策略** |
| `SubjectDetailScreen.kt:1768-1779` 收层 | X5 的 `recordSavedEvents` + `withFrameNanos` + `completionPlayback.isActive` 三处细节 | B-9 换宿主时必须逐字搬运，否则「庆祝播放中提前收层」回归 |
| `SubjectDetailScreen.kt:2163` 卷进度判据 | X4 的 `VolumeProgressPolicy` | B-9 只改外壳，不动内容顺序与判据 |
| 顶部内边距 | A 批次统一了 `TopFadePolicy.WINDOW_MASK_HIDDEN_DP`（:1748） | B-10 的悬浮 pill 不得复用 `topInset` 逻辑 |

## B-6 玻璃开关与进度条统一（U11）

- **问题**｜同一应用两套开关：设置页 34 个开关行全部用 Material3 `Switch`（`ui/settings/SettingsItem.kt:135` 定义、`:164` 控件），收藏编辑弹层用自研玻璃 `GlassToggle`（`ui/components/GlassControls.kt:66`：轨道 52×30、圆钮 24、内缩 3、位移 52−24−6=22dp、`spring(0.6,400)`）。进度条 7 处 M3 `LinearProgressIndicator`：`UniversalSubjectCard.kt:237`（收藏卡进度）、`StatsScreen.kt:409`、`SubjectDetailScreen.kt:2841`（成就）、`:693`（刷新）、`SearchResultsPane.kt:365`、`OAuthWebView.kt:93`、`BilibiliSyncScreen.kt:309`；高度/圆角各不相同（统计与收藏卡 6dp、成就条 8dp、刷新条默认 4dp，且 6dp 那两处用 `shapes.small`）。
- **⚠ 与计划不符**｜计划说「项目自绘进度条 / Material3 Switch → 换成 kyant 组件」。实测：kyant **库**里没有开关/滑条，只有 demo 组件 `Kyant0/AndroidLiquidGlass@kmp:app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidToggle.kt` [码-单文件] 与 `.../LiquidSlider.kt` [码-单文件]，且它们 import `com.kyant.shapes.Capsule` 与 demo 内 `catalog/utils/DampedDragAnimation`；本项目 `com.kyant.shapes` 全仓 0 引用（backdrop 1.0.6 用 `implementation` 引入、不传递）→ 直接搬要加依赖，违反批次 B 硬约束 3。结论：**用已有的玻璃等价物（GlassToggle/GlassSlider）补齐缺的位置**，不引 kyant。
- **改哪里**｜`SettingsItem.kt:135-171`（Switch→GlassToggle）；`GlassControls.kt` 新增 `GlassProgressBar`（与 GlassToggle 同文件，复用 `NirikoShapes.capsule` + `appleGlassCard`）；替换 `UniversalSubjectCard.kt:237`、`StatsScreen.kt:409`、`SubjectDetailScreen.kt:2841` 三处。
- **怎么改**｜
  ①`SettingsSwitchRow` 的 `Switch(...)` → `GlassToggle(checked, onCheckedChange, enabled)`，行点击与 `LocalRowInteractionSource` 不变（34 个调用点一处改完）；
  ②`GlassToggle` 补无障碍与 RTL：`Modifier.semantics { role = Role.Switch; toggleableState = ToggleableState(checked) }`，disabled 时 alpha 0.38 且 `clickable(enabled)`，偏移按 `LocalLayoutDirection` 取正负（当前写死正向 offset，RTL 会反向）；
  ③新增 `GlassProgressBar(progress: Float, height: Dp = 8.dp, indeterminate: Boolean = false)`：轨道 `NirikoShapes.capsule(height)`、底 `surfaceContainerHighest α0.55`（与 GlassToggle 同色）、填充 `primary`，不确定态用 `motionEnabled()` 门控的线性位移；
  ④三处替换只换组件、不改尺寸口径（卡片与统计沿用 6dp、成就条 8dp），把高度收成两种并在 `GlassProgressBar` 里做成参数，避免同屏出现 4/6/8dp 三种条；
  ⑤OAuth/Bilibili 两处保留 M3（窗口内瞬时、收益低），并在文件头注明"刻意不换"，防止下轮又来回改。
- **测试**｜纯函数单测：`glassToggleThumbOffsetDp(checked, track=52, thumb=24, pad=3)` 断言未选中 0 / 选中 22；`progressClamped(x)` 断言 0/1 边界与 NaN→0。源码契约测试 `SettingsSwitchContractTest`：读 `SettingsItem.kt` 断言不再出现 `Switch(` 且出现 `GlassToggle(`；既有 `SettingsFirstFrameSeedTest`（A-X3）保持绿。
- **差异**｜改动前：设置页开关是 M3 轨道 52×32 + 圆钮 28、无玻璃；改成玻璃后被压在卡片上的深色轨道换成半透明材质。进度条改动前 4/8dp 混用、直角与半圆混用；改动后统一 8dp 胶囊、主色填充。
- **验收**｜设置页任一开关与收藏弹层「私密收藏」开关外观一致（同尺寸/同圆钮/同高光）；拖动轨道无，点击后圆钮 22dp 位移、200–400ms 到位；TalkBack 读「开关，已开启/已关闭」；玻璃三档（全效果/降低/关闭）各看一次，OFF 时仍是实心胶囊、无折射。
- **风险/回滚**｜最可能翻车：直接换掉 `Switch` 丢掉 `Role.Switch` 语义导致 TalkBack 退化；其次设置页 10+ 个开关同时走 `appleGlassCard` 的 backdrop 路径会掉帧 → 开关只走静态玻璃分支（不带 backdrop 参数）。回滚 = 还原一处 `Switch(` + 删 GlassProgressBar 调用。
- **预估**｜M（开关 S + 进度条 S，合计约 1.5 天）
- **依赖**｜无；但若 B-7b 同时改设置行容器，B-6 必须先落（否则截图基线互相踩）

## B-7 二级菜单/窗口/设置条目液态玻璃化与圆角同步（U12）

- **问题**｜四类表面各自为政，且圆角来源分裂。①**对话框 24 处** `AlertDialog`：`Screens.kt` 9、`SyncBackupSettingsScreen.kt` 3、`DataSourceSettingsScreen.kt` 3、`SubjectDetailScreen.kt` 2、`WallpaperLibraryScreen.kt` 2、`GrayChannelSettingsSection.kt` 2、`ThemeColorPicker.kt`/`SearchHistorySection.kt`/`ExternalRatingSection.kt`/`EpisodeReviewMergeDialog.kt`/`CoverPickerDialog.kt`/`ThemePackSection.kt` 各 1 —— 全部 M3 默认形状、不透明 surface、无玻璃。②**底部弹层 3 处**：`SubjectDetailScreen.kt:1781` 与 `SharePreviewSheet.kt:76`（M3 默认顶角、不透明）＋ `NirikoNonModalSheet.kt:90`（自研 24dp 顶角 + `appleGlassCard`）。③**二级页卡片**：`SettingsGroup.kt:105` 20dp 玻璃、`DonateSettingsScreen.kt:117` 20dp、`WallpaperLibraryScreen.kt:325` 16dp。④**设置条目**：`SettingsGroup.kt:129` Surface morph 4↔20dp + `surfaceContainerLow α0.55`（常量为 `:58/:61/:64/:71` 的私有字面量 20/4/4/0.55）。token 侧：`NirikoShapes.kt:50-93` 有 Card 20 / Section 16 / Pill 999（→percent 50）/ Control 12 / Squircle 0.6，但上述 24/20/16/4 全是字面量、没走 token。
- **改哪里**｜`ui/theme/NirikoShapes.kt`（新增 `Sheet = 24.dp`、`RowInner = 4.dp`）；`ui/settings/SettingsGroup.kt:58-71`；`ui/components/NirikoNonModalSheet.kt:90`；`ui/settings/pages/DonateSettingsScreen.kt:117`；`ui/settings/pages/WallpaperLibraryScreen.kt:325`；后续批次再动 `Screens.kt` 等对话框。
- **怎么改**｜按「一次只换一类表面」拆 5 批，每批一个 PR、diff ≤3 文件/≤120 行：
  **B7a token 收敛（必须先做，0 视觉变化）**：NirikoShapes 增 Sheet/RowInner，把 `GroupOuterRadius→NirikoShapes.Card`、`RowInnerRadius→NirikoShapes.RowInner`、NonModalSheet 24→Sheet、Donate 20→Card、WallpaperLibrary 16→Section；验收＝逐像素截图无差异。
  **B7b 设置条目**：组容器已 20dp 玻璃，只统一行底色/morph 参数；**不要**给每行加玻璃（10+ drawBackdrop/页）。
  **B7c 二级页卡片**：Donate/WallpaperLibrary/About 版本卡统一 `appleGlassCard` + Card/Section。
  **B7d 对话框**：抽 `NirikoGlassDialog`（Dialog + Card 圆角 + `appleGlassCard` + scrim α0.32），只替换 2 个高频（`Screens.kt` 的删除/批量确认、`SubjectDetailScreen` 的取消收藏确认），其余排队。
  **B7e 底部弹层**：ModalBottomSheet 只补 `shape = RoundedCornerShape(topStart = Sheet, topEnd = Sheet)` + 内容玻璃；**与 B-9 合并做**（B-9 若改 Dialog，本批按 Dialog 走）。
- **测试**｜`NirikoShapesTest`：Card 20 / Section 16 / Sheet 24 / RowInner 4 / Pill percent 50，且 `squircle(r, 0f)` 与 `rounded(r)` 逐点等价；源码契约 `SurfaceRadiusContractTest`：settings 包内 `RoundedCornerShape(` 只允许白名单 2 处（morph 行与 token 文件）；B7d 的 `Screens.kt` 断言删除确认使用 `NirikoGlassDialog`。
- **差异**｜B7a 前后像素零差异；B7c 后二级页卡片从「部分实心/部分玻璃混合」变成统一玻璃；B7d 后对话框从实心 surface 变成能透出壁纸的玻璃（低端机会更"闷"）。
- **验收**｜玻璃三档 × {设置首页, 外观页, 分享弹层, 取消收藏确认} 四表面各一遍；20/24 两档圆角不再混用（外观页 20、弹层 24）；深色下对话框正文对比度 ≥4.5（复用 `GlassChipTokens.contrastRatio`）。
- **风险/回滚**｜24 处对话框一次性换会让低端机首帧变慢（每个都要捕获）；ModalBottomSheet `containerColor` 透明后预测性返回快照会露出下层 → 必须配 `scrimColor`；玻璃 OFF 档必须走 GlassDecision 的静态分支。回滚 = 每批独立 revert。
- **预估**｜L（拆 5 批后每批 S/M）
- **依赖**｜B-6（设置行先改，避免截图基线冲突）、B-9（B7e 与 B-9 合并）

## B-9 状态编辑页顶部不贴合 + 不匀速动画（U16）

- **问题**｜编辑层宿主 `SubjectDetailScreen.kt:1764` 已是 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`，`:1781` 的 `ModalBottomSheet` 只传了 onDismissRequest/sheetState；内容 `CollectionEditSheet`（:2066）是 `Column + verticalScroll + padding(horizontal 20dp, bottom 32dp)`（:2103-2108）→ **sheet 高度＝内容高度**，内容短于屏时顶边停在内容处（"不贴顶"）。动画：material3 1.4.0 的 `SheetState.expand()/show()/hide()` 都没有 animationSpec 参数，唯一带 spec 的 `animateTo$material3(...)` 是 internal [码-字节码]；`ModalBottomSheet` 参数表也无 animationSpec（可传：sheetMaxWidth / sheetGesturesEnabled / shape / containerColor / scrimColor / dragHandle / contentWindowInsets / properties [码-字节码]）→ **用 ModalBottomSheet 改不出 tween(220) 匀速**。
- **⚠ 与计划不符**｜计划写「全屏 Dialog 或 sheet skipPartiallyExpanded」：skipPartiallyExpanded **早已是 true**，不是原因；"不匀速"来自 M3 内部弹簧，无法靠参数改。
- **改哪里**｜`SubjectDetailScreen.kt:1763-1834`（弹层宿主）、`:2066-2108`（内容外壳）。
- **怎么改**｜
  **A 方案（先做，只解决"不贴顶"，约 10 行）**：`ModalBottomSheet(sheetMaxWidth = Dp.Unspecified, dragHandle = null, contentWindowInsets = WindowInsets(0,0,0,0), shape = RoundedCornerShape(0.dp), containerColor = MaterialTheme.colorScheme.surface)`；
  A 的配套：内容根加 `fillMaxHeight()`（让 sheet 高度＝屏高而不是内容高）+ `statusBarsPadding()`，列表底部 `navigationBarsPadding()`；`dragHandle = null` 省掉顶部 24–32dp 把手占位，拖拽仍可作用于内容顶部（`sheetGesturesEnabled` 默认 true）；
  **B 方案（再评估，解决"不匀速"）**：换 `Dialog`（`DialogProperties(decorFitsSystemWindows = false, usePlatformDefaultWidth = false)`）+ `AnimatedVisibility(enter = slideInVertically(tween(220, FastOutSlowInEasing)) { it } + fadeIn(tween(160)), exit = slideOutVertically(tween(180, FastOutSlowInEasing)) { it })`；
  B 的保留项：顶部圆角 0（直接覆盖顶部）、内容 `appleGlassCard`、保留 `WindowBlurBehindEffect()`（`GlassCard.kt:324`）与返回手势（`PredictiveDismissHandler`，用法见 `NirikoNonModalSheet.kt:92`）。
- **测试**｜`NirikoMotionSpecs` 增 `SHEET_ENTER_MS = 220` / `SHEET_EXIT_MS = 180` + `sheetEnterEasing = FastOutSlowInEasing`，单测断言数值与 easing；源码契约 `SheetPresentationContractTest`：A 方案断言 `ModalBottomSheet(` 调用含 `dragHandle = null` 与 `sheetMaxWidth`；B 方案断言文件内不再出现 `ModalBottomSheet(`。
- **差异**｜改动前：短内容时编辑层顶边悬在半空、上方露 scrim 与详情页；进入是先快后慢再回弹的弹簧。改动后（A）：顶边＝窗口顶、无空隙；（B）进入严格 220ms 减速、退出 180ms、无回弹。
- **验收**｜打开编辑层顶边 y=0；连续上滑无"半展开"停靠；关闭 ≤200ms；内容只有「私密 + 保存」时仍贴顶；预测性返回缩略图与最终位置一致。
- **风险/回滚**｜A 的 `fillMaxHeight()` 在内容短时底部留白（可改 `heightIn(min = 屏高 * 0.9f)`）；B 放弃 M3 内置 predictive-back 缩放，需自己接 `PredictiveDismissHandler`，易与系统快照抢帧；`WindowBlurBehindEffect` 在 Dialog 新窗口上 API34 可能闪一次。回滚 = A 只删 4 个参数；B 是独立文件级改动。
- **预估**｜A = S；B = M
- **依赖**｜B-10（同文件，先 B-10 再 B-9）；B7a（若走 B 方案要吃 Sheet token → 但"覆盖顶部"是 0 圆角，可不吃）

## B-10 加入收藏按钮统一形态并改悬浮（U18）

- **问题**｜同一入口两态不同形、不同位：已收藏＝`DetailDock`（:1989，`GlassSectionCard` 圆角 28dp、fillMaxWidth、**作为列表 item** 渲染，滚动会滚走）；未收藏＝M3 `Button`（:1713，fillMaxWidth、方形圆角、`enabled = !state.isUpdating`）。两态都在 `detailPaneItem(COLLECTION_BTN)`（:1699-1719）里，而 `COLLECTION_BTN` 是 `hideable = false` 的核心部件（`ui/subject/DetailSectionId.kt:59`）。
- **⚠ 与计划不符**｜计划写「:1700 的 Button」——实际 :1700 是注释，`Button(` 在 **:1713**。
- **改哪里**｜`SubjectDetailScreen.kt:1699-1719`（item 内分支）、`:1989-2022`（DetailDock 收敛为唯一外壳）、`:914`（列表 bottom padding 16dp）、以及 `SubjectDetailBody` 里 `AdaptiveDetailScaffold`（:1747）同作用域的 overlay。
- **怎么改**｜
  ①新增私有 `DetailCollectionAction(inCollection, statusLabel, ratingText, progressText, isUpdating, visible = true, onClick, modifier)`，内部统一 `GlassSectionCard(shape = RoundedCornerShape(28.dp))`；未收藏态＝`Icon(Add) + 「加入收藏」 + ›`，已收藏态＝复用 DetailDock 的 Star/状态/评分/进度 行布局；
  ②`COLLECTION_BTN` 的 item 内容改为空占位（保留 `detailPaneItem` 调用点，免得破坏双栏/隐藏/顺序），真正按钮挪到 overlay；
  ③overlay：`Box { AdaptiveDetailScaffold(...); DetailCollectionAction(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).navigationBarsPadding()) }`，列表 `contentPadding.bottom` 16dp→96dp（:914）；
  ④显隐挂点：`visible` 默认 true，内部 `graphicsLayer { translationY = if (visible) 0f else heightPx; alpha = ... }` + `tween(180, FastOutSlowInEasing)`；B-10 **不实现滚动判定**（B-11 传 `visible`）；
  ⑤与 A-X5 共存：`recordSavedEvents` 收层（:1768-1779）保持原样，收层后 `state.isInCollection` 已 true → pill 自动切已收藏态；庆祝在 sheet window、pill 在页面 window，天然隔离；
  ⑥与 B-11 共存：B-11 只算方向并调 `visible`，点空白恢复由 B-11 在页面容器处理（禁用全屏透明层），pill 自己只渲染与响应点击。
- **测试**｜纯函数 `detailCollectionActionState(isInCollection, isUpdating)` → (label, icon, enabled)，4 组：未收藏 / 添加中 / 已收藏 / 保存中；源码契约 `CollectionActionLayoutContractTest`：断言 COLLECTION_BTN 分支不再含 `Button(`、`DetailDock(` 不再被直接调用、`contentPadding` bottom ≥ 96dp。
- **差异**｜改动前：未收藏是列表里的方形按钮，滚过就消失；已收藏是列表里的玻璃条，同样滚走。改动后：两态同一个 28dp 玻璃胶囊，悬浮在底部（B-11 后随滚动隐藏/恢复）。
- **验收**｜未收藏/已收藏外形一致（同高 ≥56dp、同 28dp 圆角、同左右 16dp）；点未收藏 → 成功后原地变已收藏 pill 且不跳页；列表最后一项不被遮挡；宽屏双栏时 pill 居中于整屏；系统导航栏 inset 正确；点 pill 以外区域不误触。
- **风险/回滚**｜overlay 与内容点击区重叠 → pill 只吃自身矩形内的点击，其余穿透；玻璃 pill 每帧 backdrop 采样（复用页面 `glassBackdrop`，不要新建捕获）；28dp 是硬编码，B7a 落地后应换 token；若 B-11 未做，`visible = true` 保持现状不回归。回滚 = 把 COLLECTION_BTN 内容改回 if/else，删 overlay。
- **预估**｜M
- **依赖**｜B-10 先于 B-9；B-11 依赖本卡的 `visible` 接口

## 跨卡建议顺序（含未分到的卡）

`B7a（token，0 视觉）→ B-6（开关+进度）→ B-10（收藏 pill 统一）→ B-9A（贴顶）→ B7b/c（设置条目/二级页卡）→ B7d/e + B-9B（对话框与弹层）→ B-11（滚动显隐接 `visible`）`

理由：B7a 提供 Sheet/RowInner 常量且零风险，先落；B-6 与 B-7b 同改设置视觉，先小后大；B-10 提供 `visible` 接口后 B-11 才有挂点；B-9 与本批其他卡无耦合但同文件，故排在 B-10 之后单独一个 PR。

## 计数与证据自检

- 4 张卡（B-6/B-7/B-9/B-10），每卡 9 字段（问题/改哪里/怎么改/测试/差异/验收/风险回滚/预估/依赖）。✔
- 所有 file:line 均已回码：SettingsItem 135/164、SettingsGroup 58/61/64/71/105/129、GlassControls 66/120/140/260、NirikoShapes 50/53/56/59/85/93、NirikoNonModalSheet 90、SubjectDetailScreen 914/1699/1713/1747/1763/1764/1781/1789/1989/2001/2066/2103-2108/2163/2191/2841、SharePreviewSheet 76、GlassCard 324、StatsScreen 409、UniversalSubjectCard 237、SearchResultsPane 365、OAuthWebView 93、BilibiliSyncScreen 309、DonateSettingsScreen 117、WallpaperLibraryScreen 325。✔
- 上游证据：`Kyant0/AndroidLiquidGlass@kmp:.../components/LiquidToggle.kt`（64×28 轨道、40×24 圆钮、pad 2、pressProgress 驱动 `blur(8dp*(1-p))`/`lens(5dp*p,10dp*p)`）与 `LiquidSlider.kt`，均为 `com.kyant.shapes.Capsule` + demo 内 DampedDragAnimation [码-单文件]；material3 1.4.0 的 `SheetState/ModalBottomSheet` 签名 [码-字节码]。✔
- 未验证：M3 AlertDialog/ModalBottomSheet 的默认圆角与 containerColor 具体数值未逐像素核（标 M3 默认，未作为结论）；真机"不贴顶"的直接成因（内容高度 vs contentWindowInsets）需在 A 方案落地后确认是哪一个。
