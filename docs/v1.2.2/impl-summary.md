# v1.2.2 实施总结（impl-summary）

> 状态：**批次 A + 批次 B/C 主干 + 第八批（2026-10-31）已落地**：外观四项（分段/chip/菜单/统计数字）、V-13 控件迁移、V-15 分区标题 43 处、V-16 六个 drill-in 页标题、V-02/V-03 收口、B-13/B-14/B-15/B-16、A.9 帧阶段、B-9 预测返回（真机未验）、P2-2、F-05、S02–S08、F-07–F-15。
> 仍然未做/刻意保留的只有：真机矩阵与截图、同屏 RenderEffect 实测、B-11 结构重写、V-04（D1 裁决）、58 处无档位圆角、5 处双行 M3 菜单行、Canvas 刻度 8.sp、以及两类已知取舍（hero 单击 300ms 双击等待、剧集评分 600ms 落库）——见 §7。
> 本轮**未跑真机截图**、未做性能测量，全部验证为 JVM 单测 + 格式门 + 编译 + 打包（命令与结果见 §9）：单测 **210 类 / 1551 项 / 0 失败**，APK **32,742,166 B（≈31.2 MiB）**，门禁 **BUILD SUCCESSFUL in 5m 53s**。

本轮输入是 7 份审计文档（`docs/v1.2.2/00`–`05` + `06-roadmap.md`）与用户对 D1–D4 的裁决：

| 决策 | 用户裁决 | 落地后果 |
| --- | --- | --- |
| D1 材质分层（瓷/玻璃二分） | **不收** | 不动 `GlassDecision`/`appleGlassCard` 判定；额外加 `GlassRenderEffectBudgetTest` 护栏：**不新增 RenderEffect 构造点**，总数 ≤ 13 |
| D2 二级设置页横向推入 | 同意 | `SettingsPushPolicy` + `NirikoNavHost` 四条 transition |
| D3 删零调用旧动效令牌 | 同意 | `NirikoAnimation.kt` 删 5 个 spec；`appearSpring()` 保留并真接进 sheet 回弹 |
| D4 M3 Shapes 映射 | Lead 自定 | `NirikoMaterialShapes` {4, 8, 12, 16, 24} 接进 `Theme`；Pill(999)/Card(20) 不进 M3 五档 |

---

## 1 WP0 基础设施（Lead 亲写，纯新增 / 一行接通）

| 文件 | 内容 | 对应根因 |
| --- | --- | --- |
| `ui/theme/NirikoMaterialShapes.kt`（新） | `Shapes(extraSmall=RowInner 4, small=8, medium=Control 12, large=Section 16, extraLarge=Sheet 24)` | R1 令牌双轨 |
| `ui/theme/Theme.kt` | `MaterialTheme(...)` 补 `shapes = NirikoMaterialShapes` | R1：此前全仓 31 处未传 shape 的 M3 组件回落到 4/8/12/16/28 |
| `ui/theme/NirikoShapes.kt` | 新增 `ChipShape = PillShape`（标签/筛选/选集胶囊语义档） | V-12 5 副 chip 面孔 |
| `ui/theme/Type.kt` | 新增 `headlineSmall = 24sp / SemiBold / lineHeight 32` | V-16：二级页标题此前落回 M3 默认 24sp/w400，比主页更淡 |
| `ui/animation/NirikoMotionSpecs.kt` | 新增 `micro()`、顶层 `AnimationSpec.orSnap(reduceMotion)`、`contentTransform()`、`overlayEnter/overlayExit()`；常量 `COVER_IMAGE_CROSSFADE_MS=150`、`CHROME_HIDE_DURATION_MS=220`、`DETAIL_CHROME_DURATION_MS=180` | 六档弹簧**一个没改**，只补组合与命名 |
| `ui/animation/NirikoPressSpecs.kt`（新） | `ROW_PRESS_ALPHA=0.08`、`THUMB_PRESS_SCALE=1.08`、`SLIDER_THUMB_PRESS_SCALE=1.10`、滚动边界 `1.0dp/25dp·s⁻¹`、`pressIn()=spatialDefault` / `pressOut()=spatialSlow`（进快出慢） | M1/M2/M3 |
| `ui/animation/NirikoAnimation.kt` | 删 `pressSpring/panelSpring/countTween/tabFadeIn/tabFadeOut`（8 个零调用中的 5 个）＋新增 `enterFromLeft/exitToLeft`；`appearSpring()` 保留 | D3 |
| `ui/components/GlassMenuOrigin.kt`（新） | `glassMenuTransformOrigin(anchor, hostOrigin, menuPosition, menuSize)` → 缩放锚点落在触发控件上，pivot 分数 clamp −0.25..1.25 | P0-2 菜单从中心胀大 |
| `navigation/SettingsPushPolicy.kt`（新） | `isSettingsSubpageRoute()`（`settings_` 前缀）/ `usesSettingsPushTransition()` | D2 |
| `navigation/NirikoNavHost.kt` | 四条 transition lambda 改 `when` 顺序：**reduceMotion → 封面共享 → 设置推入 → 原 SharedAxisZ**（优先级有契约测试） | D2 + 后退落点 |

## 2 WP1 设置页（settings-ui）

- 新建 `ui/settings/SettingsLayout.kt`：`SettingsPageHorizontalInset=16.dp`、`SettingsRowMinHeight=56.dp`、`SettingsRowTwoLineMinHeight=72.dp`、`SettingsChevronSize=20.dp`、`SettingsCategoryIconSize=36.dp`、`SettingsChevron()`、`SettingsRowIconChip()`——把「三种缩进/三种行/两种箭头/两种当前值」的数字与原子组件收到一处。
- **V-05 缩进收口**：`SettingsSplitGroup` 外层 Column 加 `padding(horizontal = SettingsPageHorizontalInset)`，**11 个二级页调用点零改动**即对齐；组标题、主页标题、宽屏左列标题同用该常量。副作用：9 个非本任务页面（`SharePreviewSheet`、`WallpaperLibraryScreen`、`RatingSource`、`GrayChannel`、`DetailSections`、`Library/About/Donate/SearchSettings`）跟着内缩——这是要的结果（全站设置类页面同一左缘）；宽屏两栏 gutter 变 48dp，**待真机确认**。
- V-06 `ThemePackSection` 两组 20dp → `SettingsPageSplitGroup`(24dp)，「外观」页不再夹两档；V-07 行高/chevron 统一；V-09 删 8dp 圆角徽标，当前值只留右对齐裸文字；V-08 分类图标底改 36dp `ControlShape` + 玻璃 chip（不再实心绿球）、宽屏左列选中态 35% secondaryContainer + chevron；V-16 二级页标题 `headlineSmall`；F09 禁用态整行 `alpha 0.38` 且真传 `enabled`；V-10 搜索框由 `OutlinedTextField` 改 `BasicTextField` + `PillShape` + `surfaceChipBackground`。
- **M1 行按压**：新建 `ui/components/NirikoRowIndication.kt`（`IndicationNodeFactory` + `drawWithContent`：整行矩形高光 α0.08，进 `pressIn()`、出 `pressOut()`，reduceMotion→`snap()`），替换 7 处 M3 圆水波（`SettingsItem.kt` 4 处 + `SettingsScreen.kt` + `AdaptiveSettingsPane.kt`）；动画进度只在 draw 阶段读，不进组合期。
- 契约测试 `SettingsLayoutContractTest`（10 条源码文本断言，防缩进/行高回退）。

## 3 WP2 详情页（detail-ui）

- 新建 `ui/components/SectionHeader.kt`：`SectionHeader(title, modifier, trailing)` = titleMedium 16sp/w600 + 上下 12dp，收敛 V-15 的三套分区标题语言。
- **V-17（P0）**`InfoBoxSection` 改「`SectionHeader("详细信息")` + `Column(heightIn(max=420.dp).verticalScroll)` 兄弟结构」——标题再不参与滚动裁切；代价：卡最坏高度 +~44dp。
- **V-18（P0）**剧集结论条两行 `Row(spacedBy(14.dp))` → `FlowRow(12/4)`，`ChartStat` 两个 Text 都 `maxLines=1` + Ellipsis，窄屏不再串行重叠。
- **V-19** 剧集折线两条同色系 → `themeChartPalette()[0]/[1]` + 图例行（Vico 与 Canvas 两条实现都加；averageColor 仍 tertiary，它同时是极值点填充）。
- **V-20** 「评分分布」柱图从裸 Canvas 包进 `appleGlassCard()` + `SectionHeader`（**刻意不用 GlassSectionCard**：FULL 档会新增实时折射，违反 D1）。
- **B-1 折叠动效**：`if (!collapsed) content()` → `AnimatedVisibility(expandVertically(spatialDefault, Top) + fadeIn(effectsFast))`，chevron 由 ▸/▾ 文字改 `Icons.Filled.ExpandMore` + `graphicsLayer rotationZ 0↔180`（`spatialDefault().orSnap(reduceMotion)`）；三处调用点（:991/:1385/:1659）同受益。

## 4 WP3+WP4 反馈宿主 / 多选可见（feedback-multiselect）

- 新建 `ui/common/LocalNirikoFeedback.kt`：`NirikoFeedbackMessage(text, actionLabel, action)`、`LocalNirikoFeedback`（默认 null → 预览/单测不崩）、`NirikoFeedbackHost`（`ControlShape` + `surfaceContainerHigh` α0.96，**bottom inset 96dp 给 dock 让位**，带 action 时显式 `SnackbarDuration.Long`，后来的提示先 dismiss 旧条）。
- `MainActivity.kt` 窗口层顶层 Box → `NirikoFeedbackHost`（z 序最上，bottomInset = 有底栏 96dp / 竖排 dock 另加 start inset）。
- 新建 `ui/components/SelectionRing.kt`：`Modifier.selectionRing(...)` 在 `drawContent` 后画**内描边**（不参与测量 ⇒ 与 `animateContentSize` 不抖），α 走 `effectsFast`，并挂 `semantics { selected }`。
- `ui/screens/Screens.kt`：LIST 传 `selected`、LIST/GRID 多选时长按不弹状态浮层、长按浮层「进度+」加上限守卫、批量改状态/删除补回执；`ui/library/CollectionCard.kt` 增 `selected` 形参（环半径 = `NirikoShapes.Card` 20dp）。
- `ui/share/SharePreviewSheet.kt`：生成分享图改 try/catch/finally，失败 `show("生成分享图失败")` 且不关 sheet（修「永久生成中…」）。
- `ui/stats/CalendarCard.kt`：刷新由 9-11sp 裸文本改 48dp 胶囊按钮（`glassChip` + `ensureReadable` 4.5:1）；ICS 导出成功给回执。
- `viewmodel/SubjectDetailViewModel.kt`（F02）：权威评分刷新失败**保留旧数据** + `snackbarMessage` 回执（改用详情页既有 snackbar 通道，未动详情页文件）。

## 5 WP5 动效（motion-audit）/ WP10 控件手感（kyant-ref）

**WP5**
- `ui/components/GlassAnchoredMenu.kt`（223→323 行）：删 `if (!expanded || anchorInRoot == null) return`；新增 keepAlive 延迟卸载（退场播完才摘树）；scale/alpha 两个 `Animatable` + `graphicsLayer` 绘制期读；缩放中心由 `onGloballyPositioned` + `glassMenuTransformOrigin` 算出（alpha 初值 0 让「量几何那一帧」不可见）；reduceMotion 瞬切且立即卸载。**有意偏离**：没直接用 `overlayEnter/overlayExit`（`AnimatedVisibility` 不可见时不组合内容 ⇒ 量不到 menuSize ⇒ origin 只能回 Center），改手写等值动画并常量钉住等价关系。
- `ui/components/NirikoNonModalSheet.kt`（168→214）：`dragOffset` 改普通 Float 同步跟手（原先每次 pointer move 起协程 `snapTo`）；松手把位移搬进 settle `Animatable` 再 `animateTo(initialVelocity = velocity)`（px/s，**不除 density**）；回弹用 `appearSpring()`、关闭用 `spatialFast()`；回弹中再按把 settle 值并入 dragOffset；首次拖起 `TextHandleMove` 触感一次（不受 reduceMotion 影响）。72dp/900dp·s⁻¹ 阈值与 Sheet 圆角表达式**逐字保留**（`DialogAndSheetSurfaceTest` 依赖）。
- `ui/components/CoverImage.kt` / `SharedSubjectCover.kt`：保留 `.crossfade(false)` 契约，新增 arrived + `animateFloatAsState(tween(150).orSnap(reduceMotion || flightActive))` → `graphicsLayer { alpha }`（共享飞行期必须 0，否则拖影）。

**WP10**
- `ui/components/GlassControls.kt`：GlassToggle 重写为 `Animatable` + `draggable` 跟手，松手 `|v| ≥ 900px/s` 按方向、否则严格 >0.5 落位；拖动中轨道即时预览；按下放大 1.08（**不是** MiuiX 的 1.127，22dp thumb 在 30dp 轨道里会露头）；保留 `Role.Switch`/`toggleableState`；`pressTilt(3°, pressScale=1f)`；`TextHandleMove` 触感。GlassSlider：自建 interactionSource（只观察不消费）驱动高光、thumb 按下 1.10、位置直接跟手。
- 新建 `ui/components/GlassToggleMath.kt` 纯函数（NaN / 行程 ≤0 收敛）+ `GlassToggleMathTest` 12 例。
- `ui/components/liquidglass/LiquidGlassTokens.kt`：`liquidGlassHighlight(alpha, angleDegrees = 45f)`，`LIQUID_GLASS_HIGHLIGHT_ANGLE_DEGREES=45`（逐字等于 backdrop 1.0.6 `HighlightStyle.Default.angle`）、`..._TILT_DEGREES=20`。
- `ui/components/GlassCard.kt`（仅 highlight lambda + 2 行 import，Lead 窄范围授权）：draw 阶段用 `lighting.rotationY/7f*20f` 算角度并 `coerceIn(25f, 65f)`；`lighting == null` 的 18 个卡片调用点与改动前逐像素同观感。

## 6 Lead 自己收的尾（WP12 片 + 编译/门禁修复）

- 新建 `ui/components/NirikoPillButton.kt`：48dp 高、`PillShape`、`primary` α0.18 底（不实心——实心绿全站只留给选中态），替换全站唯一那处 M3 实心 Button。
- `ui/components/StateComponents.kt`：骨架卡圆角 12→`NirikoShapes.Card`(20)、骨架块→`ControlShape`、骨架行→`RowInner`（V-25 加载完成不再跳圆角）；`ErrorContent` 加 `title="加载失败"`、图标 56→64dp 对齐空态、重试换 `NirikoPillButton`（V-24）。
- P1-1（交叉评审唯一 P1）：`ui/subject/RatingComparisonSection.kt` 的「评分」标题换 `SectionHeader`，「评分分布」卡补 `padding(horizontal = 16.dp)`，与同页其他节统一 32dp 左缘。
- 编译修复（3 类）：①`orSnap` 原是 object 成员扩展（无法 import）→ 移为顶层扩展 + 3 个文件补 import；②`NirikoFeedbackHost` 的 content 改 `BoxScope.() -> Unit`（否则 `NirikoNavSuite` 丢 BoxScope 编译失败）；③ktlint 的 KDoc 放置问题逐条修（文件头 KDoc 改 `//`、孤儿 KDoc 归位、`@OptIn` 与 KDoc 换序）。
- 门禁配置（`app/build.gradle.kts`）：spotless 的 ktlint 显式关掉 **function-naming / property-naming / backing-property-naming** 三条（与仓库既定口径冲突：Compose PascalCase、507 处中文测试名、常量大驼峰、`_uiState` backing 写法），原因写进注释。

## 6.5 第二批追加（控件语言统一 / reduceMotion 收口 / B-12）

| 项 | 改动 | 备注 |
| --- | --- | --- |
| V-10 输入框形态（37 处） | 新建 `ui/components/NirikoTextField.kt` = 全站唯一输入框外观：`NirikoShapes.ControlShape`(12dp) 圆角、去 M3 默认描边、`surfaceContainerHighest` 半透明填充、聚焦时 primary 细描边、错误态 error 描边；脚本把 15 个文件 37 处 `OutlinedTextField(` 换成 `NirikoTextField(`，并删掉失效 import | `singleLine` 默认 **false** 是刻意的：多数调用点是多行感想/备注；调用点实际参数集已核对（不含 colors/leadingIcon/trailingIcon） |
| V-12 选集 chip | `SubjectDetailScreen.kt` 的 `SelectionChip` 从 `MaterialTheme.shapes.small`(8dp)+描边改成 `NirikoShapes.ChipShape` 胶囊 + `GlassChipTokens` 半透明底；选中 = primary α0.24 + 1.5dp 描边。**保留 `combinedClickable`**：换 FilterChip 会让内部 Surface 吃掉长按（R3 已记录） | 3 个调用点零改动 |
| WP11 reduceMotion 收口 | `IosStyleSearchComponent.kt` 13 处、`SearchFilterPanel.kt` 4、`SearchToolbar.kt` 1、`MainPager.kt` 1（顶栏 220ms 判定为「状态切换」）；`ScrollToTop.kt` 两个函数加 `reduceMotion` 形参，6 个回顶调用点全部接线（`Screens.kt`、`StatsScreen.kt`、`TabReselectPolicy.kt` 两个重载、`TrendingSection`/`DiscoverPosterPane`/`DiscoverGridPane`） | 详见 `docs/v1.2.2/impl-logs/wp11-reduce-motion.md`；只换 animationSpec，时长/弹簧/布局零改动 |
| B-12 评分弹跳触发点 | 新增纯函数 `util/RatingBumpPolicy.kt`：`shouldPlayRatingBump(previous, next, reduceMotion)`；`StarRatingInput` 改为只在「用户真的改分」时播（原来 `LaunchedEffect(rating)` 首帧也跑 ⇒ 进页面就弹），弹簧换成 `NirikoMotionSpecs.spatialFast()` | 新增单测 `util/RatingBumpPolicyTest` |

## 6.6 第三批追加（屏幕级阶段替换：B-5 / B-6 / B-7）

| 项 | 做法 | 关键决定 |
| --- | --- | --- |
| B-7 列表 ↔ 网格换视图（`ui/screens/Screens.kt`） | 新增纯函数 `util/PhaseRevealPolicy.kt`（`shouldPlayPhaseReveal` / `phaseRevealStartAlpha` + `VIEW_TOGGLE_DIM_ALPHA`/`PHASE_REVEAL_DIM_ALPHA`/`PHASE_REVEAL_DURATION_MS`）；`LaunchedEffect(viewStyle)` 把 alpha `snapTo(0.6f)` 再 `animateTo(1f, tween(180))`，包络挂在三棵 Lazy 的 `Modifier.reportBottomBarScroll().graphicsLayer{alpha}`；动画值只在 draw 阶段读 | 不用 `AnimatedContent`：过渡期会同时组合列表与网格两棵 Lazy = 双份图片请求/测量，踩 02 文档 §B-7 的性能红线；首次组合传 `previous=null` 不播，避免每次进页面先压暗 |
| B-6 统计页骨架 → 正文（`ui/stats/StatsScreen.kt`） | 同一策略：`LaunchedEffect(state.isLoading)` 在 `true→false` 时把 LazyColumn alpha `0.55→1`（180ms） | 骨架与正文是同一个 LazyColumn 的不同 item 分支，外层 Crossfade 会让两棵 Lazy 同时组合，故走包络；`false→true`（下拉刷新）不压暗 |
| B-5 详情页骨架 → 正文（`ui/subject/SubjectDetailScreen.kt`） | 新建 `ui/subject/DetailPhase.kt`：`enum DetailPhase{Loading,Error,Ready}` + 纯函数 `detailPhaseOf(isLoading, hasError, hasSubject)`（钉住 Loading > Error > Ready 优先级）；只在进入 `Ready` 时给正文容器一次 `0.55→1` 包络 | **没用 `AnimatedContent`**：hero 大封面是正文 LazyColumn 里的一个 item（共享元素的源/目标），包进 AnimatedContent 会在过渡期把它移出组合树、飞行中断；02 文档要求的「hero 上提一层」在本页等于重写滚动结构（hero 必须随页面滚动），风险大于收益 |
| 新增单测 | `util/PhaseRevealPolicyTest`（4 例）、`ui/subject/DetailPhaseTest`（4 例） | 钉住「首次组合不播」「reduceMotion 不播」「error 先于 ready」 |

## 6.7 第四批追加（WP6 形状字面量令牌化）

| 项 | 做法 | 关键决定 |
| --- | --- | --- |
| 未令牌化的 60 处 | 保留并记账：`6.dp`×14、`8.dp`×14、`10.dp`×12（小件，没有对应档位）、`50.dp`×4（dock / tab 胶囊，kyant 移植值刻意不动）、`28.dp`×4（搜索玻璃面，另有 20/24/999 三档待单独统一）、`3.dp`×4、`14.dp`×3、`0.dp`×2、`2/11/48.dp` 各 1 | 强行归一档位会改变小件观感，必须先有设计决定；留给后续批次 |
| 新护栏 `ShapeLiteralPolicyTest`（3 例） | ①令牌档取值（4/12/16/20/24/999 与 `RoundedCornerShape(50)`）不许再以字面量出现；②剩余字面量只许用记账取值且逐值次数 ≤ 基线；③7 个 Shape 工厂必须有真实消费点（防「建了令牌没人用」） | 测试会剥掉注释再统计：`NirikoShapes.kt` 的 KDoc 刻意保留历史口径说明 |
| 连带修 | `LibrarySearchControls.kt` / `VerticalTopSelector.kt` 的 `foundation.layout.*` 通配 import 展开为显式 import（这两个文件此前从未被 ratchet 扫到）；`Badges.kt:31` 顶格 KDoc 改行注释（避免 dangling） | 都是「文件第一次进入 spotless ratchet」暴露的存量风格问题，不是本批引入 |
| 契约测试同步 | `GlassAnchoredMenuTest:59` 的源码断言从 `RoundedCornerShape(24.dp)` 改为 `NirikoShapes.SheetShape`（同一几何，断言强度不变） | 改用令牌后源码文本变化，测试不能锁死旧写法 |
## 6.8 第五批追加（图表小件 + 强调色令牌化：V-02 / V-03 / V-22 / V-23）

| 项 | 做法 | 关键决定 |
| --- | --- | --- |
| 强调色收口（V-02） | 新建 `ui/theme/AccentColors.kt`：`NirikoAccents` 7 色（RatingGold / EpisodeBlue / FavoriteRed / PeopleGreen / StatusCompleted / StatusNeutral / StatusOnHold）+ `CalendarAccents` 4 色（Broadcast / Release / Hiatus / Delay）。`Badges.kt` 的 `watchStatusColor` 与三个私有强调值、`GlassControls.kt` 的 `perfectGold`、`DetailStatCard.kt` 三个图标 tint、`CalendarCard.kt` 四个内部别名、`CalendarDaySheet.kt` 的 `statusColor` 全部改为引用令牌 | 语义色不是主题色，只允许一处定义；**等值搬迁零像素变化** |
| 新护栏 `AccentLiteralPolicyTest`（4 例） | 11 个 0x 只许出现在 `AccentColors.kt` / `Color.kt` / `StatusColors.kt`；令牌文件里每个恰好一次；每个令牌至少有 1 个真实消费点；测试内的出处记账表必须与令牌文件逐一一致 | 后两个白名单文件是**主题色板**与**状态语义表**，本来就合法列出同值条目；把主题色板也逼成引用语义令牌反而错位 |
| 柱图细节（V-23 + V-03） | `RatingComparisonSection.kt`：3px 直角 `drawRect` → `drawRoundRect`，`barCorner = minOf(4.dp.toPx(), barWidth / 2f, barHeight / 2f)`；`labelPaint.textSize = 24f`（写死像素）→ `8.sp.toPx()` | 3x 屏逐像素一致，其它密度与系统字体缩放才正确；`minOf` 保证矮柱不变成水滴 |
| 收藏概览胶囊（V-22） | `CollectionDashboard.kt` 的 `DashboardChip`：弱形态从 `surfaceContainer α0.7` 改为 `surfaceContainerHigh` **实底**；文字色一律过 `GlassChipTokens.ensureReadable` | 半透明底会与壁纸混合、对比度随机跌落；强调与否改由字重与文字色区分，不再靠透明度 |

## 6.9 第六批追加（WP9 / B-10 动效字面量收口，零观感变化）

| 项 | 做法 | 关键决定 |
| --- | --- | --- |
| chrome 时长接线（B-10） | 3 处 220ms（`MainActivity.kt:296` 底栏隐藏、`navigation/MainPager.kt:157` 顶栏、`ui/subject/SubjectSearchScreen.kt:177`）改用 `NirikoMotionSpecs.CHROME_HIDE_DURATION_MS`；2 处 180ms（`ui/subject/SubjectDetailScreen.kt:560` 与 `:2425`）改用 `DETAIL_CHROME_DURATION_MS` | 三个常量此前只有定义没有消费点；本次只换引用，**时长一个毫秒不改**，所以不需要真机比对 |
| 内容尺寸 300ms | `ui/screens/Screens.kt:529`、`ui/search/SearchResultsPane.kt:224` 的 `animateContentSize(tween(300))` 改走旧令牌 `AnimDurationNormal`（=300） | 与 `NirikoAnimation.kt` 既有 `AnimDuration*` 口径一致，避免同一语义两处写法 |
| 新护栏 `MotionLiteralPolicyTest`（4 例） | `spring(` 逐文件次数只减不增（11 文件/32 处基线）；手写 `tween(数字)` 只许出现在记账表里（搜索挡板 140–260ms、`CardRarityMaterial` 3000ms、详情背景墙 350ms）；`CHROME_HIDE_DURATION_MS` 恰好 3 个、`DETAIL_CHROME_DURATION_MS` 恰好 2 个消费点 | chrome 的 220/180 与 300 已从白名单消失，**回归成字面量会立刻变红**；比「约定」更早拦住漂移 |
| 刻意保留 | 详情背景墙 350ms 材质渐显、kyant 底栏 `spring(1f, 300f, 0.5f)` 移植值、`IosStyleSearchComponent` 挡板 140–260ms | 前两者是既定观感（Apple §12 / kyant 1:1 移植）；搜索挡板属 B-11，846 行且必须真机逐帧看 |

## 6.10 第七批追加（WP11 收口：入场/状态切换/折叠展开的减少动态效果）

| 项 | 做法 | 关键决定 |
| --- | --- | --- |
| 三个降级助手 | `ui/animation/NirikoMotionSpecs.kt` 文件末尾新增顶层函数 `expandEnter(reduceMotion)` / `collapseExit(reduceMotion)` / `FiniteAnimationSpec<T>.orSnapFinite(reduceMotion)` | 前两个与 `AnimatedVisibility` 的 Compose 默认值逐帧等价（默认就是 `fadeIn() + expandIn()` / `shrinkOut() + fadeOut()`），所以**关闭动画的用户看到瞬切、其余用户像素完全不变**；`orSnapFinite` 是因为 `animateContentSize` 只收 `FiniteAnimationSpec<IntSize>`，`orSnap` 的返回类型接不上 |
| 折叠面板 9 处 / 5 文件 | `ProviderBindingSection`(3) `TmdbBindingSection`(3) `BilibiliSyncScreen`(1) `GrayChannelSettingsSection`(1) `DiagnosticsSections`(1) 的 `AnimatedVisibility` 全部补 `enter = expandEnter(...)` / `exit = collapseExit(...)` | 这些是二段展开的表单块，此前在「减少动态效果」下仍然做尺寸过渡 |
| 状态切换 6 处 | 详情页材质墙 350ms、详情 chrome 180ms、状态分段色弹簧、搜索页内容 inset 220ms 各补 `.orSnap(LocalReduceMotion.current)`；列表与搜结果 `animateContentSize` 改 `.orSnapFinite(...)` | 都是「状态切换」而非「跟手操作」，按 02 文档 §E.2 口径一律降级；显式类型参数 `tween<Float>` / `spring<Color>` / `tween<Dp>` / `tween<IntSize>` 不能省（否则 `Cannot infer type for type parameter 'T'`） |
| 新护栏 `ReduceMotionCoverageTest`（4 例） | ①5 个折叠文件的 `expandEnter`/`collapseExit` 消费点数逐一等于 3/3/1/1/1；②详情页 3 处、搜索页 1 处 `.orSnap(LocalReduceMotion.current)`、两处 `.orSnapFinite(...)` 各 1；③助手语义（`EnterTransition.None` / `ExitTransition.None` / 真的 `snap<T>()`） | 这类守卫跑起来看不出差别（除非真机打开系统级减少动画），源码断言是 CI 里唯一能拦住「新写 `AnimatedVisibility` 忘了降级」的手段 |
| 刻意保留 | `PressTilt`（按压倾斜）、`SettingsGroup` 行圆角 morph、`GlassCard` 按压高光、`OAuthWebView` 加载进度、`RevealOnScroll`（函数开头整体跳过）、详情 `CollapsibleSourceSection`（已自带 `EnterTransition.None` 分支） | 直接操作与按压反馈必须跟手，不受 reduceMotion 影响；否则会变成「按下去没反应」 |

## 6.11 第八批追加（2026-10-31：控件语言四项 / V-13 / V-15 / V-16 / V-02·V-03 / B-11 / B-13–B-16 / A.9 / B-9 / 反馈与截图项 / 测试护栏）

| 工作包 | 落地内容 | 证据（源码 / 测试） |
| --- | --- | --- |
| 控件语言 A 方案 | `NirikoSegmentedControl` 唯一实现并接线 3 处（统计页日历模式 / 详情观看状态 / 作品库状态计数行），`GlassSegmentedContainer` 删除；`NirikoChip` 四档语义（Tag/Filter/Selection/Action），旧 `TagChip` 删除；`NirikoMenu` + `GlassMenuOverlay` 唯一菜单入口，旧 `GlassAnchoredMenu` 删除；`StatNumber`(24/w600/tnum) + `StatLabel`(13) + `StatNote`(11.5) + `StatTile` 同源 | `ui/components/{NirikoSegmentedControl,NirikoChip,NirikoMenu,GlassMenuOverlay,StatNumber}.kt`、`ControlSourceTest`、`DetailTypeScaleContractTest` |
| V-15 分区标题 | `SectionHeader` 真实调用 4 → **43 处**（详情 19 / 人物 5 / 角色 3 / 剧集 4 / 其它分区 12）；剩 6 处 `titleMedium + Bold` 是数值强调，按文件锁死计数 | `ui/components/SectionHeader.kt`；`DetailTypeScaleContractTest` 断言 `>= 42` 且 `titleMedium+Bold` 精确 3 文件 6 处 |
| V-16 页标题 | 6 个 drill-in 页（作品/人物/角色/制作人员/剧集/Steam 登录）`titleLarge`(22) → `headlineSmall`(24/w600) | 各 Screen；`DetailTypeScaleContractTest` 逐页断言 |
| V-13 控件迁移 | 设置页滑块 → `GlassSlider`；搜索筛选 `Switch` → `GlassToggle`、`RangeSlider` → `GlassRangeSlider`（新 `GlassRangeSliderMath` 纯函数）；B 站同步 `GlassToggle`；剧集评分 `GlassRatingSlider`；设置页搜索框 → `NirikoSearchField`；`ui/` 下 M3 开关/滑块 = 0 | `ui/components/{GlassControls,GlassRangeSliderMath,NirikoSearchField}.kt`；`ControlSourceTest` 三条（M3 控件=0、输入框两副面孔、新控件有消费点） |
| V-02 / V-03 | `CardRarityMaterial` 23 色搬进 `ui/theme/RarityPalette.kt`、搜索挡板 2 绿进 `SearchIndicatorAccents`、`LibraryGalleryCard` 2 色清零 → `ui/` 下 `Color(0x…)` 只剩 `theme/`、`share/`、`LiquidGlassTokens.kt`；统计/日历/详情小字走 `labelSmall`/`labelMedium`，日历角标改实底 badge，`fontSize = N.sp` 只剩 `Type.kt`/`Badges.kt`/`share/**` | `ThemeColorAuditTest`、`TypographyLiteralAuditTest` |
| B-13 四态 | 新 `StateHost`（单实例、0.55→1、180ms、`shouldPlayPhaseReveal` 首帧不播、reduceMotion 瞬切），接线 7 屏；刻意不用 `AnimatedContent`（双 Lazy 红线） | `ui/components/StateHost.kt`；`DetailTypeScaleContractTest` 断言 7 屏各 1 个 `StateHost(stateKey =` 且无 `AnimatedContent` |
| B-14 / B-15 / B-16 | 筛选面板补 `animateContentSize`（spatial / `orSnapFinite`），8 处手写 spring 收进 `panelEnter`/`panelExit`；日历标题 `AnimatedContent`（`calendarTitleDirection` 方向纯函数、`sizeTransform = null`、reduceMotion 两侧 None）；搜索结果/榜单 `animateItem()` 逐条入场 | `SearchFilterPanel.kt`、`CalendarCard.kt`；`CalendarTitleTransitionTest`、`DetailTypeScaleContractTest` |
| B-11 搜索卡 | 12 处手写时长/弹簧 → 六档令牌；5 处 reduceMotion；尺寸/位移读取从组合期移到布局/绘制期（新 `AnimatedLayoutSize.kt`） | `IosStyleSearchComponent.kt`、`AnimatedLayoutSize.kt`；`SearchCardMotionContractTest`（4 条契约） |
| A.9 帧阶段 | 底栏/dock 隐藏进度改 `offset{}`/`graphicsLayer{}` 内读 + `derivedStateOf`；时间线偏移、页面指示器（Canvas）、进度条填充、range thumb 位移移出组合期 | `NirikoBottomBar.kt`、`NirikoNavSuite.kt`、`TimelineSection.kt`、`Screens.kt`、`GlassControls.kt` |
| B-9 预测返回 | navigation-compose 2.8.5 自带 `PredictiveBackHandler + SeekableTransitionState` 对 pop 过渡跟手 seek（aar 字节码核实）；新增护栏测试钉住版本/清单/四条过渡/reduceMotion 分支/popExit 缩放 ≤12% | `PredictiveBackSupportTest`（4 例）；**真机逐帧未验证** |
| P2-2 封面重播 | 进程级 `CoverRevealTracker`（512 FIFO）替换 `remember(url)` 复位，列表滚回不再重播 150ms 淡入 | `CoverRevealTracker.kt`、`CoverImage.kt`、`SharedSubjectCover.kt`；`CoverRevealTrackerTest` |
| 反馈 / 命中区 / 截图项 | F-05 `isBroadcastRefreshing` 外露 + 胶囊「刷新中…」禁用；S02 徽标移到 meta 下一行；S03 行尾常驻 chevron；S04 48dp 命中盒；S07 解绑二次确认 + 回执；S08 `NirikoActionPill(OpenInNew)`；F-07 选集长按触感；F-08/F-10 按压 + selected；F-12 关闭钮 48dp 外壳；F-14 五主卡 `hapticEnabled = true`；F-15 保存回执 `Confirm`；F-11 hero 单击翻面 | `StatsViewModel.kt`、`EpisodeRatingSections.kt`、`TmdbBindingSection.kt`、`SubjectDetailScreen.kt`、`NirikoChip.kt`、`IosStyleSearchComponent.kt`、`SearchToolbar.kt`、`PosterGridCard.kt` 等 |
| 测试护栏 | 新增 15 个测试类（约 72 例）：`GlassMenuOverlayTest`（替代 `GlassAnchoredMenuTest`）、`PredictiveBackSupportTest`、`ThemeColorAuditTest`、`TypographyLiteralAuditTest`、`ControlSourceTest`、`SearchCardMotionContractTest`、`GlassRangeSliderMathTest`、`CoverRevealTrackerTest`、`GlassSliderThumbContrastTest`、`StatNumberTierTest`、`CalendarTitleTransitionTest`、`CalendarTouchTargetTest`、`StatsTypographyContractTest`、`ChartPaletteContrastTest`、`DetailTypeScaleContractTest`；重算 `ShapeLiteralPolicyTest` / `MotionLiteralPolicyTest` 基线 | §8.5 |

## 7 本版未做 / 刻意保留（第八批之后的最终口径）

> 第八批之后，01/02/03 的 P0/P1/P2 条目已全部落地或明确记账；下表是**仅剩**的未做与取舍。

| 项 | 状态 / 为什么保留 |
| --- | --- |
| 真机验证整套 | **未做**：无设备。06 §7 的 320/360/411/600/840dp × 7 主题 × 3 玻璃档 × reduceMotion × fontScale 矩阵无记录；仓库无截图测试设施，`screenshot/` 仍是 2026-09-25 旧图 |
| 同屏 RenderEffect 实测 | **未做**：`GlassRenderEffectBudgetTest` 只数源码构造点（13 个），数不到「同屏运行几个」；FULL 档下分段控件与菜单同屏是否 ≤2 未知 |
| B-11 结构重写 | **未做**：不合并单一 progress、尺寸不走 graphicsLayer 近似；需真机逐帧调，留待下一版 |
| V-04 材质分层 | **刻意不做**（D1 用户裁决）；`GlassDecision` 不动，只留「不新增 RenderEffect 构造点」护栏 |
| 58 处无对应档位的圆角字面量 | **保留**：`ShapeLiteralPolicyTest` 精确相等基线（0/2/3/6/8/10/11/14/28/48/50dp 合计 58 处） |
| 详情页 5 处双行 M3 `DropdownMenuItem` | **保留**：双行副标题无自定义槽位，`ControlLanguagePolicyTest` 按文件锁死数量 |
| `RatingComparisonSection` Canvas 刻度 8.sp | **保留**：改需同步改图表底部留白，本轮不动 |
| hero 单击翻面 300ms 双击等待 | **已知取舍**：保留双击语义的必然代价，已记账；未引入 DataStore 首次提示 |
| 剧集评分「停止拖动 600ms 落库」 | **已知取舍**：防每帧写库；可能丢最后一次拖动（原实现松手即存） |
| V-15 剩余 6 处 `titleMedium + Bold` | **保留**：数值强调而非分区标题，`DetailTypeScaleContractTest` 锁死计数 |
| 单处 `Color(0x…)` | 不再追缴：越界已清零，只剩 `theme/`、`share/`、`LiquidGlassTokens.kt` 三个合法域（11 个强调色令牌保留在 `AccentColors.kt`） |

> 已完成、不再列入未做：M3 `FilterChip`/`AssistChip`/`DropdownMenu`/`Switch`/`Slider`/`RangeSlider`（全 0）、`GlassSegmentedContainer`/`TagChip`/`GlassAnchoredMenu`（删除）、V-21 统计五档字号、日历 7–9sp、B-9 预测返回（有测试，真机未验）、B-14/B-16、S02–S08、F-05/F-07/F-08/F-10/F-11/F-12/F-14/F-15。

## 8 新增测试（27 类：前七批 12 + 第八批 15）

1. `navigation/SettingsPushPolicyTest`：10 条 `settings_*` 路由全部命中；NavHost 里 `usesSettingsPushTransition` 出现 4 次、四个方向动画各自存在；封面判定 index < 设置推入判定 index（优先级契约）。
2. `ui/theme/NirikoMaterialShapesContractTest`：用 `createOutline` 量真实圆角断言 4/8/12/16/24；extraLarge 半径 < 32（防映射成 Pill）；Theme 源码含 `shapes = NirikoMaterialShapes`；`headlineSmall` = 24sp/SemiBold。
3. `ui/components/GlassRenderEffectBudgetTest`（D1 护栏）：源码扫描 RenderEffect 构造点，白名单 + 每文件精确次数，总数 ≤ 13。
4. `ui/settings/SettingsLayoutContractTest`：设置页缩进/行高/chevron 的源码文本契约。
5. `ui/components/GlassToggleMathTest`：开关结算纯函数 12 例（方向速度、0.5 边界、NaN、零行程）。
6. `util/RatingBumpPolicyTest`：B-12 判据 3 例（进页面初值不弹 / 改分才弹 / reduceMotion 下不弹）。
7. `util/PhaseRevealPolicyTest`：包络判据 4 例（首次组合不播 / 阶段未变化不播 / reduceMotion 不播 / 起始不透明度在 reduceMotion 下为 1）。
8. `ui/subject/DetailPhaseTest`：详情页阶段派生 4 例（Loading 优先、error 先于 ready、Ready、三者皆假回退 Loading）。
9. `ui/theme/ShapeLiteralPolicyTest`：WP6 护栏 3 例（令牌档取值不许以字面量出现 / 剩余字面量取值与次数 ≤ 记账基线 / 7 个 Shape 工厂都有消费点）。
10. `ui/theme/AccentLiteralPolicyTest`：第五批护栏 4 例（11 个强调色 0x 只许在令牌文件内 / 每个恰好定义一次 / 每个都有真实消费点 / 测试记账表与令牌文件一致）。
11. `ui/animation/MotionLiteralPolicyTest`：第六批护栏 4 例（`spring(` 逐文件只减不增 / 手写 `tween(数字)` 不超过记账基线 / chrome 时长必须走 `CHROME_HIDE_DURATION_MS`(3 处)·`DETAIL_CHROME_DURATION_MS`(2 处) / 三个 chrome 常量取值仍为 220·180·150）。
12. `ui/animation/ReduceMotionCoverageTest`：第七批护栏 4 例（9 处折叠面板的 `expandEnter`/`collapseExit` 消费点数 / 6 处状态切换的 `orSnap`·`orSnapFinite` 消费点数 / 三个降级助手的瞬时语义 / 全仓 `AnimatedVisibility` 调用段必须带降级标记——剥注释后括号配平扫描、零白名单）。


## 8.5 第八批新增测试（15 类，约 72 例）

| 测试类 | 用例 | 守什么 |
| --- | --- | --- |
| `ui/components/GlassMenuOverlayTest` | 7 | 菜单浮层的锚点缩放/退场/Back·Esc/焦点归还（替代 `GlassAnchoredMenuTest`） |
| `navigation/PredictiveBackSupportTest` | 4 | navigation-compose ≥2.8、清单 `enableOnBackInvokedCallback`、四条过渡、reduceMotion 分支、popExit 缩放 |
| `ui/theme/ThemeColorAuditTest` | 2 | `Color(0x…)` 只许出现在 `theme/`、`share/`、`LiquidGlassTokens.kt` |
| `ui/theme/TypographyLiteralAuditTest` | 2 | 字号字面量只许 `Type.kt`、`Badges.kt`、`share/**` |
| `ui/components/ControlSourceTest` | 3 | M3 开关/滑块归零、输入框只有两副面孔、新控件有真实消费点 |
| `ui/common/SearchCardMotionContractTest` | 5 | 搜索卡无手写 spring/tween、六档令牌 + 降级、尺寸/位移布局期读 |
| `ui/components/GlassRangeSliderMathTest` | 12 | 双 thumb 取值/抓取/相撞结算纯函数 |
| `ui/components/CoverRevealTrackerTest` | 5 | 已揭示封面不重播、512 FIFO 上限 |
| `ui/components/GlassSliderThumbContrastTest` | 3 | 深色下圆钮白芯、与轨道对比度 ≥3:1 |
| `ui/components/StatNumberTierTest` | 4 | 统计数字 24/13/11.5 三档与 `tnum` |
| `ui/stats/CalendarTitleTransitionTest` | 8 | 翻月方向纯函数、reduceMotion 两侧 None、`sizeTransform = null` |
| `ui/stats/CalendarTouchTargetTest` | 4 | 日期条 48dp 边界 |
| `ui/stats/StatsTypographyContractTest` | 4 | 统计页字号收口 |
| `ui/stats/ChartPaletteContrastTest` | 3 | 图表双线对比度 ≥3:1 |
| `ui/subject/DetailTypeScaleContractTest` | 6 | 6 页标题、`SectionHeader` ≥42、`titleMedium+Bold` 精确 6、`TagChip` 退场、`StateHost` 7 屏、`animateItem()` |

另更新 `ShapeLiteralPolicyTest`（第三次重算精确基线：28dp 4→3、8dp 14→13，合计 58 处）与 `MotionLiteralPolicyTest`（`allowedSpringCounts` 归零）。

## 9 验证（命令与结果）

在仓库根（Windows）逐条跑，**串行**，不并行开两个 Gradle。最终一轮把三件事合成一次调用：

```
.\gradlew.bat :app:spotlessApply :app:spotlessCheck :app:testDebugUnitTest --console=plain
# 第二批 1m 36s；第三批 2m 36s；第四批 1m 58s；第五批 1m 46s；第六批 2m 26s（首跑因 KDoc 里出现 `/*` 触发块注释嵌套失败，修后一次过）；第七批 2m 9s
.\gradlew.bat :app:assembleDebug --console=plain
# 第三批 2m 45s；第四批 59s；第五批 1m 13s；第六批 37s；第七批 1m 18s
```

- **单测（前七批结束时）**：从 `app/build/test-results/testDebugUnitTest/TEST-*.xml` 聚合 = **195 个测试类 / 1482 项 / 0 失败 / 0 错误 / 0 跳过**（既有 1419 + 前七批新增 62 项；第三批 +8，第四批 +3，第五批 +4，第六批 +4，第七批 +4）。第八批后重新聚合为 **210 类 / 1551 项**（净增 14 类 / 65 项，旧快照口径 196 类 / 1486 项）。
- **单测（第八批，2026-10-31 12:11 本轮结果）**：**210 个测试类 / 1551 项 / 0 失败 / 0 错误 / 0 跳过**。相对旧快照（196 类 / 1486 项）净增 **14 类 / 65 项**（新增 15 个测试类、删除/替代 `GlassAnchoredMenuTest` 1 个）。
- **APK（第八批）**：`app/build/outputs/apk/debug/app-debug.apk` = **32,742,166 B（≈31.2 MiB）**；`output-metadata.json` 已核实 versionName **1.2.2** / versionCode **5**；已复制为 `dist/niriko-v1.2.2-debug.apk`（debug APK 尺寸随 dex 分段重排有数百 KB 波动，不作为门禁）。
- **格式门**：`spotlessApply` → `spotlessCheck` 绿；`git diff --check` 无输出。
- `git status -- app/src`：83 个改动文件 + 28 个新增文件（含前七批新增测试类 12 个；第八批另增 15 类，`git diff --shortstat` = 89 files / +6179 / −4077），全部是本轮产物；**diff 膨胀主要来自 `spotlessApply` 的 import 排序**（`SubjectDetailScreen.kt` 显示 2841 行变更，忽略空白后 661 行；`Screens.kt` 673→157）。
- **改了 2 个既有测试的断言方式**（不是放宽语义，是让它不锁死格式）：`ui/animation/SubjectCoverMotionTest.kt` 的 `endpointSizesRemainOutsideTheAnimatedSharedNodes` 与 `ui/common/SharedSubjectCoverContractTest.kt` 的 `directEntrancesRegisterLoadedCoversWithoutChangingBounds` 原先用「含换行与精确缩进的字面串」断言 Modifier 链顺序，`spotlessApply` 按行长重排链后必红；改为把源码 `replace(Regex("\\s+"), " ")` 归一化后比较链式顺序，并顺手修掉该测试文件里会触发 `no-wildcard-imports` 的 `import org.junit.Assert.*`（ratchet 一旦扫到该文件就报）。
- 未做：真机截图对比（仓库无 roborazzi/paparazzi）、同屏 RenderEffect 实测、1.5× 字体/7 主题矩阵目视。
- **第八批（2026-10-31）**：`.\gradlew.bat :app:compileDebugKotlin --console=plain` → **BUILD SUCCESSFUL**；`.\gradlew.bat :app:spotlessApply :app:spotlessCheck :app:testDebugUnitTest :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL in 5m 53s**（同一条命令一次通过）；`.\gradlew.bat :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**（debug APK 32,742,166 B（≈31.2 MiB）；三段门禁由 Lead 统一执行，本文件负责源码核对与 XML 聚合）。

## 10 待真机确认的风险清单（第八批后重排：新引入项在前）

1. **SectionHeader 12dp 留白的整体节奏**：分区标题扩到 43 处后，详情/人物/角色/剧集页每节上下各 12dp 叠加，页面整体比原来「松」一档；需确认长详情页不会显得零碎。
2. **StatNumber / StatTile 的主色与薄涂**：主指标用 primary、StatTile 用 accent 14% 薄底；深色壁纸 + FULL 玻璃下对比度是否够（`StatNumberTierTest` 只钉字形档位，不测观感）。
3. **GlassRangeSlider 双 thumb 相撞**：搜索筛选价格区间在窄轨道、双 thumb 同时靠近一端时的观感与抓取手感（`GlassRangeSliderMathTest` 只测数值结算）。
4. **日历日期条 pitch 44→48dp 的窄屏边界**：320dp 宽下 7 列是否仍能放下，命中区与视觉是否错位（`CalendarTouchTargetTest` 只锁 48dp 常量）。
5. **hero 单击翻面的 ~300ms 双击等待**：保留双击语义的代价；单击后要等双击窗口过期才翻面，可能被感知为「点了没反应」。
6. **剧集评分「停止拖动 600ms 落库」**：防每帧写库的取舍，快速松手后立刻退出页面可能丢最后一次拖动（原实现松手即存）。
7. **分段控件（非 `nestedInGlass`）与菜单的实时折射同屏数**：两者都走 `appleGlassCard`/`searchGlassSurface` 实时路径，FULL 档统计页/详情页是否有越过「同屏 ≤2」的可能（G-3，`GlassRenderEffectBudgetTest` 只数源码构造点）。
8. **设置页宽屏两栏 gutter 48dp**（组卡自带 16dp 内距 ×2）是否显得过窄/过挤。
9. `GlassToggle` 轨道 30dp 高、thumb 22dp，放大 1.08 后是否露头；命中区仍靠整行，实际点按手感。
10. `NirikoRowIndication` 整行矩形高光（α0.08）在深色玻璃组卡上是否够可见、是否与 chevron 视觉冲突。
11. 二级设置页横向推入（`enterFromRight` 用 `-it/4` 位移）在宽屏两栏布局下是否合理（宽屏其实是「同一页换列」，可能感觉多余）。
12. 详情页折叠 `AnimatedVisibility` 与既有 `RevealOnScroll` 同时触发时的帧表现；`heightIn(420)` 卡最坏高度 +44dp 对长 infobox 的观感。
13. Snackbar 宿主 bottom inset 96dp 在「无底栏」（竖排 dock / 平板）时是否贴合；新增 F-05「刷新中…」胶囊在日历卡顶部的占位是否挤。
14. 评分分布卡（appleGlassCard）与紧邻「评分」卡（GlassSectionCard）在 FULL 档下的观感差异（**刻意保留**，不是 bug）。
15. `NirikoTextField` 37 个调用点替换后：密排表单页（数据源/同步备份）的行距与页面总高会变，需真机确认没有把原来「描边+留白」撑出的呼吸感压掉。
16. `SelectionChip` 胶囊化后同一行能放下的集数变化（13 集长标题行）；选中态 primary α0.24 + 1.5dp 描边在 FULL 玻璃档下是否够区分。
17. 回顶在「减少动态效果」下变瞬时落位（含跨屏情况），确认不会显得「跳」。
18. **屏幕级 alpha 包络的可见性**：详情/统计从 0.55、列表↔网格从 0.6 起步，深色壁纸下是「看得出呼吸」还是像掉了一档亮度；180ms 是否够短。
19. **详情页正文包络与封面共享飞行重叠**：飞行是 200ms（`SUBJECT_COVER_DURATION_MS`），包络前 180ms 里 hero 在 α0.55→1 之间——若真机能看出封面「变亮」，把起点抬到 0.7，或仅在无飞行种子（`coverSeed == null`）时播包络（本版未做设备验证）。
20. **柱图 4dp 圆角 + 8sp 标签**：卡宽约 240dp、或系统字体 1.5× 时（8sp → 12sp）标签是否会撞到相邻柱；`RatingComparisonSection` 的 8.sp 刻度与新增底部留白是否够。
21. **收藏概览胶囊改实底后**，页面顶部统计条比之前更「实」，与下方半透明玻璃卡的层级感是否被削弱。
22. `spotlessApply` 重排 import 后，改动文件的 git blame 会整段变色（历史追溯成本，不是功能问题）。
