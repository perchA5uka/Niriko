# motion-audit · task-13（V-12 chip 五副面孔归一，次要页面）

范围：ui/person/PersonDetailScreen.kt、ui/subject/{TmdbBindingSection,ExternalRatingSection,EpisodeRatingSections,SearchFilterPanel}.kt、ui/episode/EpisodeDetailScreen.kt。未跑 gradle（Lead 统一编译），**未验证编译**。WP5 旧日志已归档为 motion-audit-wp5.md。

## 0. 语义分类规则（本轮定，后续页面沿用）
| 组件 | 语义 | 视觉锚点 |
| --- | --- | --- |
| `NirikoTagPill` | 信息，**不可点**（计数/状态/职业） | 28dp，无手势 |
| `NirikoFilterPill` | **筛选开关**（可多选/独立开关，选中=过滤生效） | 34dp，主色 20% 底 + 1dp 描边 |
| `NirikoSelectionPill` | **互斥取值**（一组里选一个，选中=当前值） | 40dp，同底色、更厚 |
| `NirikoActionPill` | **做一件事**（刷新/录入/跳转） | 主色文字，0.5dp 描边 |

FilterPill 与 SelectionPill 的差别不是配色（共用 chipColors），而是**组件类别 + 40/34dp 高度差**：一行里「开关」与「取值」不再长得一模一样（03 文档 S05 根因）。

## 1. 改动表（文件:行 → 原控件 → 新组件 → 语义判断）
| 文件:行 | 原控件 | 新组件 | 语义判断 |
| --- | --- | --- | --- |
| PersonDetailScreen.kt:182 | FilterChip(selected=false, onClick={}) | NirikoTagPill | 职业标签是信息；原写法有 ripple 无动作=假可点 |
| PersonDetailScreen.kt:221 | FilterChip(selected=false, onClick={}) | NirikoTagPill | 参与职位计数，同上 |
| TmdbBindingSection.kt:238 | FilterChip | NirikoSelectionPill | 季号是「当前值」互斥选择，不是筛选 |
| ExternalRatingSection.kt:171 | AssistChip(leadingIcon=Add) | NirikoActionPill(icon=Add) | 打开录入对话框=动作 |
| ExternalRatingSection.kt:267 | FilterChip | NirikoSelectionPill | 奖项类型 AwardKind 互斥 |
| EpisodeRatingSections.kt:166 | FilterChip | NirikoSelectionPill | 数据源 RatingSourceChoice 互斥 |
| EpisodeRatingSections.kt:183 | FilterChip | NirikoFilterPill | 「移动平均」=图表叠加开关 |
| EpisodeRatingSections.kt:189 | FilterChip | NirikoSelectionPill | 票数阈值 0/10/50 互斥档位 |
| EpisodeRatingSections.kt:227 | AssistChip(+spinner) | NirikoActionPill(enabled=!loading) | 触发 IMDb 拉取=动作 |
| EpisodeRatingSections.kt:472 | AssistChip(check/play) | NirikoActionPill(icon=条件图标) | 「看到这里」=写进度动作 |
| EpisodeRatingSections.kt:486 | AssistChip | NirikoActionPill | 「单集详情 ›」=跳转 |
| EpisodeDetailScreen.kt:344 | AssistChip(PlayArrow) | NirikoActionPill(icon=PlayArrow) | 写进度动作 |
| EpisodeDetailScreen.kt:351 | FilterChip | NirikoFilterPill | 「二刷」是本集布尔开关（不在互斥组） |
| EpisodeDetailScreen.kt:363 | AssistChip | NirikoActionPill | 打开 Bangumi 章节页=跳转 |

另删 2 个死 import（EpisodeDetailScreen 的 `icons.filled.Check`、EpisodeRatingSections 的 `material3.Icon`）与 5 个 FilterChip/AssistChip import；两文件 `CircularProgressIndicator` 另有他用，保留。

## 2. 有意为之的取舍（请 Lead 过目）
- **IMDb 加载按钮失去转圈**：`NirikoActionPill.icon: ImageVector?` 接不了 composable，改为文字态「正在加载 IMDb 评分…」+ `enabled=false`。要保留 spinner 需给它加 leadingContent 插槽（API 变更，等你拍板）。
- **假可点改真不可点**：PersonDetail 两处原本 `onClick={}`，现在连 ripple 都没有，正合 V-12「一眼看出哪些能点」；要它们真可点（跳该职业筛选）是新功能，本轮未做。
- 选中浓度沿用组件内 0.20 底 / 0.70 边（与分段控件同值），没有一处手写 `Color(0xFF…)`。

## 3. 明确未改
- SearchFilterPanel.kt 的 8 处 spring（MotionLiteralPolicyTest 逐文件基线）、面板展开/收起 enter/exit 与 reduceMotion 守卫（WP11 刚收口）——一处没动；评分滑块/进度输入未动。
- ~~SearchFilterPanel.kt:231/:514/:549 三处 DropdownMenu 未迁移~~ → **已由 §5 迁移到页面级锚定菜单（A 方案，Lead 已授权改宿主文件）**。原判断「在 item 内挂 overlay 会错」成立，所以不是就地替换，而是把菜单状态提升到页面根层。

## 4. 未验证项
- 未编译：新组件参数名/类型对照 ui/components/NirikoChip.kt 签名逐条核对；图标 import 均在文件内。
- TmdbBindingSection 季号横向滚动行改用 40dp 的 SelectionPill，行高比原 FilterChip 高约 6dp，需真机看是否挤压。
- 命中区：视觉 34dp 可点胶囊的实际触控高度取决于父行高（FlowRow spacing 6/8dp），48dp 下限未实测。
- 浅/深色 × 玻璃档 4 组下选中/未选中的 4.5:1 由 `GlassChipTokens.ensureReadable` 保证（组件内已做），未逐档截图。
## 5. V-14 收口：筛选/排序下拉菜单 → 页面级锚定菜单（task-13，A 方案）

### 5.1 问题
SearchFilterPanel.kt 原三处 M3 `DropdownMenu`（排序方式 :231 旧行 / 排序维度 :514 旧行 / 年份 :549 旧行）是 `Popup`：
8dp 方角实色面、行高 48dp、无锚点缩放，与详情页 24dp 玻璃锚定菜单是两种控件；且 Popup 的定位/裁剪不受宿主约束，
在 `LazyColumn` item 与 `headerContent`（TrendingSection.kt:211 / DiscoverPosterPane.kt:147）里位置随滚动漂移。

### 5.2 为什么不能就地换成 NirikoMenu
`GlassMenuOverlay` 用 `constraints.maxHeight` 当 viewport 来定位与裁剪（GlassAnchoredMenu.kt:233-241 / :242-248）。
懒列表 item 的主轴约束是 `Infinity`，在 item 内挂 overlay 必然位置/裁剪错；菜单还会跟着列表滚走。
⇒ 不是换组件，而是**把菜单状态提升到页面根层**，面板只「请求一次菜单」。

### 5.3 落点
| 落点 | 内容 |
| --- | --- |
| SearchFilterPanel.kt:64-120 | 新增 `@Stable class FilterMenuHostState`（`request: FilterMenuRequest?` + `expanded: Boolean`，`show(anchorInRoot, content)` / `dismiss()`）、`class FilterMenuRequest(anchorInRoot: Rect, content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit)`、`@Composable fun FilterMenuHost(state, modifier)` = `NirikoMenu(expanded = state.expanded, anchorInRoot = request.anchorInRoot, onDismissRequest = state::dismiss)` |
| SearchFilterPanel.kt:275 | `menuItemLabel(label, selected)` = `label + "  ✓"`：`NirikoMenuItem` 无 selected 态；不用 icon 选中标记，避免选中行文字缩进错位 |
| SearchFilterPanel.kt:279-322 / :561-604 / :606-651 | SortModeSelector / SortRow / YearRow 各加 `menuHost: FilterMenuHostState? = null`；按钮 `Modifier.onGloballyPositioned { boundsInRoot() }` 缓存锚点（root 坐标，懒容器里也正确），onClick = `menuHost?.show(anchor) { dismiss -> 逐项 NirikoMenuItem(text = ..., onClick = { onX(v); dismiss() }) }` |
| SearchResultsPane.kt:98 / :405 / :434 | `filterMenuHost: FilterMenuHostState? = null`（默认 null）→ 3 处 SearchHeaderBlock(:204/:273/:305) → SearchFilterPanel(`menuHost = filterMenuHost`) |
| SubjectSearchScreen.kt:128 | `val filterMenuHost = remember { FilterMenuHostState() }` |
| SubjectSearchScreen.kt:293 / :397 | BrowseFilterPanel（在 `headerContent` 懒容器里）与 SearchResultsPane 各透传同一个宿主 |
| SubjectSearchScreen.kt:435 | 根 Box（:211）的最后一个子节点 `FilterMenuHost(state = filterMenuHost)`：整页尺寸、root 坐标、不随懒列表滚动、不随顶栏淡出 |

**为什么收起时不清空 `request`**：`GlassMenuOverlay` 在 `anchorInRoot == null` 时直接 return（:136），
清空 request 会把 anchor 一起清掉 → 组合树立刻被摘掉、退场动画播不出来。所以只翻 `expanded`，request 保留到下一次 show 覆盖。

**顺带收益**：三处菜单拿到与详情页同一套动效（锚点缩放 spatialDefault 进 / spatialFast 退、alpha effectsFast、reduceMotion 瞬切）
与 24dp 玻璃面；行高从 M3 的 48dp 行改为统一的 44dp 行（`NIRIKO_MENU_ITEM_MIN_HEIGHT`，NirikoMenu.kt:117）。

### 5.4 未替换的 M3 组件及原因（全站口径）
| 位置 | 现状 | 原因 |
| --- | --- | --- |
| SubjectDetailScreen.kt:789/:803/:818/:839/:845 | 分享菜单、传送门菜单的**行**仍是 M3 `DropdownMenuItem`，外壳已是 24dp 玻璃 `GlassAnchoredMenu`（:779/:828） | 详情页不在 task-13 授权的三文件内；且传送门项是「标题 + 副标题」双行，`NirikoMenuItem` 只有单行 text、无 subtitle 槽，直接换会丢信息 —— 需先给行组件加副标题槽再迁移 |
| ui/common/VerticalTopSelector.kt:121 | 自己写 `if (expanded) { GlassMenuOverlay(...) }` | 文件不在授权范围；且这种写法关闭瞬间就摘掉组合树 → 无退场动画（round 1 已报）。应改为传 `expanded=`，由 keepAlive 负责生命周期 |
| 其余 `ui/**` | 已无 `DropdownMenu(` 调用点（只剩 NirikoMenu.kt:29 / LibraryTopBar.kt:57 / Screens.kt:1042 / NirikoMaterialShapes.kt:25 的注释） | 已逐文件核对 |

### 5.5 未验证项
- **未编译**（Lead 统一编译）。已用源码契约核对：ui/ 下 `DropdownMenu` 0 处调用；SearchFilterPanel `spring(` 仍 8（MotionLiteralPolicyTest 逐文件基线没增没减）；SubjectSearchScreen `.orSnap(LocalReduceMotion.current)` 仍 1 处、SearchResultsPane `.orSnapFinite(...)` 仍 1 处；AnimatedVisibility 未新增（SearchFilterPanel 仍 2 处）。括号/字符串配平用词法器核过 4 个文件均 balanced。
- 真机待看 ①：菜单从锚点长出的方向。`NirikoMenu(alignRight = true)` 是默认值，筛选/排序按钮在面板左侧时，菜单**右缘**对齐锚点右缘，视觉上偏左；可能要给这三处传 `alignRight = false`（需真机对比后定，本轮没改默认值以免影响其他 7 个调用点）。
- 真机待看 ②：玻璃采样。菜单在页面根层，`LocalSearchGlassBackdrop` 由 SubjectSearchScreen.kt:210 的 CompositionLocalProvider 供给，采样源是整页 `rememberLayerBackdrop { drawContent() }`——菜单渲染在 provider 内部但在采样层之外，需确认没有「采到自己」的递归/闪烁（详情页同构，预期无问题）。
- 真机待看 ③：收起筛选面板后锚点 Rect 是否过期。request 保留但 `expanded=false` 时菜单不可见；下次 show 会用新锚点覆盖。风险是面板收起的同一个手势里连点 —— 低。
