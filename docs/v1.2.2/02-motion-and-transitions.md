# 02 · v1.2.2 动效盘点与缺失过渡方案

> **范围**：全仓动效表面机械盘点 + 真实交互路径上的「跳变」缺口 + 令牌/帧阶段/reduceMotion 纪律。
> **基线**：当前工作区源码（v1.2.1 收敛后）。所有行号来自本轮逐文件阅读，**不使用旧文档行号**。
> **路径约定**：除特别标注外，所有路径省略前缀 `app/src/main/java/com/otakup/niriko/`，`ui/...` 即 `app/src/main/java/com/otakup/niriko/ui/...`。
> **采集方式**：`read` 逐文件全文或指定段 + `grep` 精确正则（下表给出正则与命中数，便于复核）。**未运行 app、未跑 gradle**，真机验证项见 §G.3。

**一句话结论**：动效基础设施**是合格的**（六档弹簧在、reduceMotion 在、共享元素 8/8 都接了 `subjectCoverPathBounds`、按压/手势多数消费事件）；缺的**几乎全是「接缝」**——状态与状态之间的过渡（骨架→正文、折叠→展开、菜单→出现、封面→图）不是「时长不对」。因此本轮 P0 全部是**缺失过渡**，不是「动画太快/太慢」。**本文件不改任何既有 duration，也不提「拉长时长救掉帧」。**

| 级别 | 条数 | 性质 |
| --- | --- | --- |
| P0 | 2 | 布局/整块硬跳，用户每次都会看到 |
| P1 | 10 | 明显但可忍：缺失过渡、物理不连续、帧阶段开销 |
| P2 | 5 | 打磨：令牌收敛、错峰失效、绘制期优化 |
| 已知欠账 | 11 行映射 | 已在 `docs/audit-2026-10/`、`plans/` 登记，本轮给方案不给新缺陷（§G.2） |
| 刻意保留（勿报） | 7 条 + 4 条推翻旧结论 | §G.1 |

---

## 0.0 2026-10-31 复审状态（第八批后，覆盖下文旧计数）

> 下表是第八批（2026-10-31）之后的最终判定；下文 §0–§G 保留为 v1.2.1 审计原文，**「21 spring + 20 tween」「AnimatedContent = 0」等计数已过期**，不要引用。

**过期计数更正（当前源码实测）**

| 口径 | 审计原文 | 2026-10-31 实测 | 证据 |
|---|---|---|---|
| `spring(` | 41 处 / 业务字面量 21 | **24 处 / 业务字面量 0**（只剩 `NirikoMotionSpecs` 令牌 7 + kyant bottombar 移植 11 + `NirikoAnimation.appearSpring`） | `ui/animation/NirikoMotionSpecs.kt:76-105`、`ui/animation/NirikoAnimation.kt:48` |
| `tween(` | 28 处 / 手写数字 12 | **29 处 / 手写数字 3**（`CardRarityMaterial.kt:336-337` 的 3000×2、详情背景墙 350×1；其余为常量/策略引用） | `MotionLiteralPolicyTest` |
| `AnimatedContent` | 0 | **1**（日历切月） | `ui/stats/CalendarCard.kt:739`；`CalendarTitleTransitionTest` |
| `AnimatedVisibility` | 13 | **14**，且 0 处未声明 reduceMotion 降级 | `ReduceMotionCoverageTest` |
| `animateItem()` | 7 | **7**（搜索结果/榜单/作品库逐条入场） | `DetailTypeScaleContractTest` 的 B-16 断言 |

**B-1…B-16 最终状态**

| 编号 | 状态 | 证据（当前源码 / 护栏） |
|---|---|---|
| B-1 折叠区块 | ✅ 已修 | `SubjectDetailScreen.kt` `AnimatedVisibility(expandVertically + fadeIn)` + chevron `graphicsLayer rotationZ` |
| B-2 锚定菜单进出场 | ✅ 已修 | `ui/components/NirikoMenu.kt` + `GlassMenuOverlay.kt`（锚点缩放/退场/Back·Esc/焦点归还）；旧 `GlassAnchoredMenu` 删除 |
| B-3 sheet 松手不接初速 | ✅ 已修 | `ui/components/NirikoNonModalSheet.kt` 位移搬进 settle `Animatable` + `initialVelocity` |
| B-4 图片「跳」出来 | ✅ 已修 | `CoverImage.kt` 150ms 淡入（飞行期 0）+ `CoverRevealTracker` 防重播 |
| B-5 详情页骨架→正文 | ✅ 已修 | `ui/subject/DetailPhase.kt` + 正文 0.55→1 包络（不用 `AnimatedContent`） |
| B-6 统计页骨架→正文 | ✅ 已修 | `StatsScreen.kt` 同一包络方案 |
| B-7 列表↔网格 | ✅ 已修 | `util/PhaseRevealPolicy.kt` + 三棵 Lazy 的外层 alpha 包络 |
| B-8 sheet 拖拽每帧起协程 | ✅ 已修 | `NirikoNonModalSheet.kt` 改普通 Float 跟手 + 单一 settle |
| B-9 页面级预测返回 | ✅ 依赖库自带（真机未验） | navigation-compose 2.8.5 `SeekableTransitionState`；`PredictiveBackSupportTest.kt` 钉版本/清单/四条过渡/降级/缩放 |
| B-10 chrome 时长 | ✅ 已修 | `CHROME_HIDE_DURATION_MS`(3) / `DETAIL_CHROME_DURATION_MS`(2) 令牌；`MotionLiteralPolicyTest` |
| B-11 搜索卡 | 🟡 部分修（结构重写未做） | 六档令牌 + 5 处降级 + `animatedRequiredSize`/`offset {}` 布局期读（`SearchCardMotionContractTest.kt`）；单一 progress / graphicsLayer 尺寸近似未做 |
| B-12 评分弹跳触发点 | ✅ 已修 | `util/RatingBumpPolicy.kt` + `RatingBumpPolicyTest` |
| B-13 四态硬替换 | ✅ 已修 | `ui/components/StateHost.kt:35`（单实例、0.55→1、180ms、首帧不播、reduceMotion 瞬切），7 屏接线 |
| B-14 chip 增删硬切 | ✅ 已修 | `SearchFilterPanel.kt:195/:420` `animateContentSize` + `panelEnter/panelExit` |
| B-15 日历切月硬替换 | ✅ 已修 | `ui/stats/CalendarCard.kt:739` `AnimatedContent` + `calendarTitleDirection` 纯函数 + `sizeTransform = null` |
| B-16 搜索历史/建议列表 | ✅ 已修 | 搜索结果/榜单/作品库列表项 `animateItem()`；`DetailTypeScaleContractTest.kt` |

**A.8 / A.9 / E 最终状态**

- **A.8 字面量**：业务 `spring(` 0；手写 `tween(数字)` 3（两条刻意保留：`CardRarityMaterial` 3000ms 材质循环、详情背景墙 350ms）。
- **A.9 帧阶段**：**6/6 迁出组合期** —— 底栏/宽屏 dock 隐藏进度（`offset {}`/`graphicsLayer {}` + `derivedStateOf`，`ui/navigation/NirikoBottomBar.kt`、`ui/adaptive/NirikoNavSuite.kt`）、时间线页偏移（`ui/stats/TimelineSection.kt:119-121`）、页面指示器（`ui/screens/Screens.kt:178` 改 Canvas 自绘）、进度条填充（`GlassControls.kt:764` `drawBehind`）、range thumb 位移（`GlassControls.kt` graphicsLayer.translationX）、开关 thumb（已在前批）。
- **E reduceMotion**：19 处折叠/状态守卫 + 6 个回顶调用点接线；新增 `expandEnter`/`collapseExit`/`orSnapFinite`；`ReduceMotionCoverageTest` 零白名单扫描。

## 0. 统计口径（A 表前置）

### 0.1 机械统计

| API | 正则 | 命中 | 文件 | 备注 |
| --- | --- | --- | --- | --- |
| `animate*AsState` | `animate[A-Za-z]*AsState(` | **34** | 12 | 全部为调用点（导入行不含 `(`） |
| `Animatable` | `Animatable(` | **21** | 15 | 手写弹簧/飞行底座 |
| `animateTo` | `.animateTo(` | **31** | 15 | |
| `snapTo` | `.snapTo(` | **19** | 12 | |
| `spring(` | `spring(` 与 `spring<T>(` | **41** | 16 | 拆解见 0.2 |
| `tween(` | `tween(`（词边界，排除 between） | **28** | 16 | 拆解见 0.2 |
| `AnimatedVisibility` | `AnimatedVisibility(` | **13** | 7 | 另有 **59** 处 `AnimatedVisibilityScope` 参数，**不是**动画组件，勿混算 |
| `AnimatedContent` | — | **0** | 0 | 全仓一次都没用 → §B 的根因 |
| `Crossfade` | `Crossfade(` | **2** | 2 | `AdaptiveSettingsPane.kt:271`、`VerticalTopSelector.kt:107` |
| `updateTransition` | — | **1** | 1 | `VerticalTopSelector.kt:89` |
| `animateItem()` | `animateItem(` | **7** | 4 | `Screens.kt:471,538,605`、`SearchResultsPane.kt:144,222`、`TrendingSection.kt:224`、`DiscoverPosterPane.kt:156` |
| `animateContentSize` | — | **2** | 2 | `Screens.kt:491`、`SearchResultsPane.kt:223` |
| `sharedElement` | `.sharedElement(` | **8** | 8 | 8/8 都传 `boundsTransform`（§G.1，勿再报「只有 CoverImage 传」） |
| `rememberInfiniteTransition` / `keyframes` | — | **0** / **1** | 0/1 | keyframes 唯一在 `NirikoMotionSpecs.kt:54`（合规） |

**动效表面总量约 96 个调用点**（34+21+31+13+7+2+8 去重）；加上一次性反馈与共享飞行 ≈ 110 处。

### 0.2 令牌合规拆解（41 处 spring / 28 处 tween）

| 类别 | spring | tween | 判定 |
| --- | --- | --- | --- |
| `NirikoMotionSpecs.kt:63-78`（唯一合法来源） | 6 | 1（`:46`） | ✅ 合规 |
| `NirikoAnimation.kt:41/47/53`（旧令牌定义） | 3 | 2（`:101-102`） | ⚠ 定义在，**调用点为 0**（§C.1） |
| kyant 上游（`DampedDragAnimation.kt:32-36`、`InteractiveHighlight.kt:23-24`、`LiquidBottomTabs.kt:148,164`、`LiquidVerticalDock.kt:149,165`） | 11 | 0 | 🔒 刻意保留（上游弹簧，勿动） |
| 业务代码字面量 | **21** | **20** | ❌ 越界，清单见 §A.8 / §C |
| policy 常量引用（EpisodeWave / SubjectCoverPath / CrtPolicy…） | — | 4 | ✅ 常量单源 |

### 0.3 五条铁律自查

| 铁律 | 现状 | 判定 |
| --- | --- | --- |
| 时序单源 | 96 个调用点里 **27 处**是裸 spec；`NirikoAnimation` 的 8 个旧令牌零调用（只剩 `AnimDuration*` 在用） | ⚠ 部分失效 |
| 物理优先 | 跟手滚动/拖拽全部带 `initialVelocity`（`DampedDragAnimation.kt:131`）；**松手回弹**缺初速（§B-3） | ⚠ 一处缺口 |
| 可中断 | sheet / 封面路径有 cancel 分支；`TouchGlow` 不消费（§B-8） | ✅ 主体合规 |
| 帧阶段 | 底栏/顶栏/设置/封面共享合规；**4 处**组合期读滚动值（§A.9） | ⚠ 4 处 |
| 反馈只播一次 | `CompletionCelebration`/`EpisodeCompletionWave`/`SubjectReturnGlow` 全是事件口令 + 防重入 | ✅ |

---

## A. 全量动效清单

> 列含义：**阶段** = 动画驱动值被读取的阶段（C 组合 / L 布局 / D 绘制）；**守卫** = reduceMotion；**可中断** = 是否带 initialVelocity 或 drag cancel。

### A.1 导航与顶层容器

| 位置 | 触发 | 真实 spec | 阶段 | 守卫 | 可中断 |
| --- | --- | --- | --- | --- | --- |
| `NirikoNavHost.kt:192-197` enter | push 任意路由 | 封面路由 `fadeIn(subjectCoverSeek)`；否则 `enterSharedAxisZ` = fadeIn `effectsDefault` + scaleIn 0.92 `spatialDefault` | D | ✅ `:187/192` | n/a |
| `NirikoNavHost.kt:198-203` exit | 覆盖/pop | 同构对称（fadeOut / `exitSharedAxisZ`） | D | ✅ | n/a |
| `NirikoNavHost.kt:204-209` popEnter | 返回 | 封面路由 `fadeIn(subjectCoverSeek)`；否则 `popEnterSharedAxisZ`（0.92 反向） | D | ✅ | n/a |
| `NirikoNavHost.kt:210-215` popExit | 返回 | `popExitSharedAxisZ`（scaleOut 1.08） | D | ✅ | n/a |
| `SubjectCoverPath.kt:52-68` | 封面飞行 | `tween(SUBJECT_COVER_DURATION_MS=200, LinearEasing)` + bounds `keyframes` 每 10ms | D ✅ | ✅ `:56` | ✅ seeking `:62` |
| `MainPager.kt:148-153` | 滚动隐藏顶栏 | `animateFloatAsState(..., tween(220))` | D（`PagerTopBar.kt:121-127` offset+alpha） | ❌ 未读 LocalReduceMotion | n/a |
| `MainPager.kt:137-142` | 切页 | 收起搜索卡 + 清焦点（**无动画**，§B-10） | — | — | — |
| `MainPager.kt:167-174` | 横向滑动 | `HorizontalPager` 内建弹簧（不可配） | D | 受系统 | ✅ 跟手 |
| `Screens.kt:163` | 页面指示器 | 无 spec（`pagePosition` 直接算 dp 宽） | **C + L** ❌（4 个 Box 每帧重组重测） | n/a | ✅ 跟手 |
| `util/ScrollToTop.kt:88-90` | 底栏重选回顶 | `SPRING = spring(stiffness=380, dampingRatio=0.9)`；距离 >2400px 直接落位 | D | ❌ | ✅ 可打断 |
| `MainActivity.kt:280-293` | 滚动隐藏底栏 | `animate(..., tween(220))` 写 `mutableFloatStateOf` | 状态源（D 端读） | ✅ `:287` 硬落位 | n/a |
| `MainActivity.kt:388-394` | 进入二级页 | `if (showBottomBar) TopLevelPageIndicator(...)` → **瞬消/瞬现**（§B-10） | — | — | — |

### A.2 底栏 / dock（含 kyant 上游）

| 位置 | 触发 | 真实 spec | 阶段 | 守卫 | 可中断 |
| --- | --- | --- | --- | --- | --- |
| `NirikoBottomBar.kt:54` | 隐藏进度 | 组合期读 `bottomBarHideFraction()` → **每帧重组整条底栏** | **C** ❌ | ✅（源端 `MainActivity.kt:287` 硬落位） | n/a |
| `NirikoBottomBar.kt:58-67` | 隐藏 | `offset{}` + `graphicsLayer{alpha}`（合规做法） | **D** ✅ | — | n/a |
| `LiquidBottomTabs.kt:119-167` | 拖动/点选 tab | `DampedDragAnimation`（5 个上游弹簧 `DampedDragAnimation.kt:32-36`）+ 松手 `offsetAnimation.animateTo(0f, spring(1f,300f,0.5f))` `:148,164` | D | ❌ | ✅ onDrag/onDragCancelled |
| `LiquidBottomTabs.kt:194-260` | 玻璃折射 | 双层渲染同一内容（第 2 层 `alpha(0f)` + `clearAndSetSemantics`） | D | n/a | — |
| `LiquidBottomTabs.kt:262-308` | 跟手片 | 色散/挤压按 velocity 变化 | D ✅ | ❌ | ✅ |
| `utils/DampedDragAnimation.kt:125-140` | 跟手 | `valueAnimation.animateTo(target, valueAnimationSpec, initialVelocity)` + 每帧灌 velocity | D ✅ | ❌ | ✅ **带初速**（健康范本） |
| `utils/InteractiveHighlight.kt:31-76` | 按压 | `spring(0.5f,300f,0.001f)` + 每 delta `snapTo` | D（读）+ 每 delta 协程 | ❌ | ✅ |
| `NirikoNavSuite.kt:106-125` | 切宽屏/旋转 | `appearance.animateTo(1f, spatialFast())`；隐藏 = `offset{-24dp*hideFraction}` + alpha | C（`:100` 组合期读）⚠ | ✅ `:109` | ✅ |
| `FloatingControls.kt:60-96` | 滚动意图 | **纯状态机，无动画**（48dp 隐藏 / 16dp 显示 / 1600px 跳变守卫） | — | — | — |
| `BlankTapRestore.kt:27-49` | 隐藏态点空白恢复 | 无动画；Pass.Final 观察不消费 | — | — | ✅ |

### A.3 搜索（含搜索卡）

| 位置 | 触发 | 真实 spec | 阶段 | 守卫 | 可中断 |
| --- | --- | --- | --- | --- | --- |
| `IosStyleSearchComponent.kt:162` | 卡片尺寸 | `cardSpring = spring<Dp>(0.8f, 400f)`（字面量） | **L**（`:271-287` requiredWidth/Height） | ❌ | ⚠ 1:1 跟手但松手缺初速 |
| `IosStyleSearchComponent.kt:190-194` | 内容露出 | `reveal = animateFloatAsState(..., spring(0.8f, 300f))` | D | ❌ | — |
| `IosStyleSearchComponent.kt:196-210` | 内容/行 | `contentAlpha=tween(240)`、`rowsAlpha=tween(220)`、`rowsShift=tween(220)` | D | ❌ | — |
| `IosStyleSearchComponent.kt:219-242` | 锚点位移 | 3 条 `animateDpAsState(cardSpring)`；DRAGGING 时 `dragOffsetX` 1:1 | **C + L** | ❌ | ✅ 跟手 |
| `IosStyleSearchComponent.kt:404-478` | 绿条/输入框 | `fakeAlpha=tween(200)`、`indSettle=tween(260)`、`inputAlpha=tween(260)` | D | ❌ | — |
| `IosStyleSearchComponent.kt:624-655` | 关闭钮 | `AnimatedVisibility`：`fadeIn(tween(160)) + slideInHorizontally(spring(0.7f,420f))` / `fadeOut(tween(140)) + slideOutHorizontally(tween(200))` | D | ❌ | — |
| `SubjectSearchScreen.kt:166-176` | 内容让位 | `animateDpAsState(..., tween(220))`（注释称「与卡片同一条曲线」→ **与事实不符**） | L（padding） | ❌ | — |
| `SubjectSearchScreen.kt:199-204` | 预测返回跟手 | `graphicsLayer{ scale=1-0.04p; alpha=1-0.15p }` | D ✅ | ✅ `:125` | ✅ `reset()` |
| `SearchFilterPanel.kt:111-117`、`:327-333` | 筛选面板展开 | `fadeIn(spring(stiffness=MediumLow)) + expandVertically(同)` / 对称收起 | D | ❌ | — |
| `SearchResultsPane.kt:222-223` | 结果新增 | `animateItem()`（默认 spring）+ `animateContentSize(tween(300))` | D | ❌ | — |
| `TrendingSection.kt:213,224` / `DiscoverPosterPane.kt:149,156` | 卡片入场/增删 | `RevealOnScroll(staggerIndex=...)` + `animateItem()` | D | ✅（Reveal 内） | — |

### A.4 详情 / 评分 / 分享

| 位置 | 触发 | 真实 spec | 阶段 | 守卫 | 可中断 |
| --- | --- | --- | --- | --- | --- |
| `SubjectDetailScreen.kt:530` | 材质渐显 | `tween(350, AnimEasingDefault)`（刻意保留） | D | ❌ | — |
| `SubjectDetailScreen.kt:535-542` | 详情顶栏显隐 | `tween(180)`（字面量，§C.2 命名后不改值） | D | ❌ | — |
| `SubjectDetailScreen.kt:2277-2282` | 收藏胶囊 | `tween(180, FastOutSlowInEasing)` + offset/alpha | D ✅ | ⚠ `visible` 本轮恒 true（死动画） | — |
| `SubjectDetailScreen.kt:2369` | **折叠区块** | **无动画**：`if (!collapsed) { content() }` | — | — | — |
| `SubjectDetailScreen.kt:2374-2382` | 星级数字 | `Animatable`：`snapTo(1.15f)` → `animateTo(1f, spring(0.5f, StiffnessMedium))` | D ✅ | ❌ | — |
| `SubjectDetailScreen.kt:2472-2496` | 状态分段 | `animateColorAsState(..., spring(0.6f,400f))` | D | ❌ | — |
| `SubjectDetailScreen.kt:688-695` | 加载→正文 | **无过渡**（`when{}` 整块替换） | — | — | — |
| `SubjectDetailScreen.kt:1995-2024` | 收藏编辑 sheet | M3 `ModalBottomSheet` 内建弹簧（不可配，§G.1） | D | ❌ | M3 内建 |
| `SharePreviewSheet.kt:21-25,98` | 分享预览 | M3 `ModalBottomSheet(skipPartiallyExpanded=false)` | D | ❌ | M3 内建 |
| `DetailPosterCard.kt:67-72` | 卡面翻转 | `tween(AnimDurationNormal=300, AnimEasingDefault)`；reduceMotion 时 `snap()` 且跳过翻转判定 | D | ✅ `:66` | — |
| `CardRarityMaterial.kt:283-305` | 稀有度呼吸 | `animateTo(1f, tween(3000))` 往返 + 可见性停表 | D | ✅ `:289` | 可取消（lifecycle） |
| `TouchGlow.kt:30-53` | 按压白晕 | `spring(0.5f,300f)` 字面量（§A.8） | D ✅（`drawWithContent` + `graphicsLayer` 内读值） | —（按压反馈，规范允许不守卫） | 🔒 不消费 = **刻意**：kdoc `:23-25` 明示「只读事件不消费 → 不影响点击/长按/滚动」（§G.1 #12） |
| `PressTilt.kt:144-162` | 按压倾斜 | `spatialFast()` × 4 值 | D ✅ | ✅ `:65,110` | ✅ 消费事件 |
| `RevealOnScroll.kt:54-87` | 入场 | `effectsDefault()` + `spatialDefault()`（alpha / scale 0.97 / offset 14dp） | D ✅ | ✅ `:43` | 只播一次 |

### A.5 统计与图表

| 位置 | 触发 | 真实 spec | 阶段 | 守卫 | 可中断 |
| --- | --- | --- | --- | --- | --- |
| `NirikoRadarChart.kt:67-79` | 首次出现 | `growth.animateTo(1f, spatialSlow())`（仅当 <1f；数据变化不重播） | D ✅ | ✅ `:72` | 可（Animatable） |
| `TimelineSection.kt:109-131` | 竖向翻页 | 无 spec：`graphicsLayer{scale/alpha}` 读**组合期算好的 Float** | **C** ❌ | ❌ | 跟手 |
| `NirikoBarChart.kt:167-177` / `NirikoDonutChart.kt:53-59` | 数据变化 | Vico `producer.runTransaction{}`（项目侧零过渡） | D（库内） | ❌ | — |
| `StatsScreen.kt:208-217` | 加载→内容 | **无过渡**：骨架 item → 20+ item 同时出现 | — | — | — |
| `StateComponents.kt:74-90` | 骨架 shimmer | `shimmer()`（单一入口）+ `ShimmerPolicy` 降级静态灰块 | D（库内） | ✅ `:76` | — |
| `CalendarCard.kt` / `CalendarDaySheet.kt` | 切月/选日 | 除 sheet 本体与 pressTilt 外**零动画**（日期切换硬替换） | — | — | — |

### A.6 组件层（sheet / 菜单 / 骨架 / 共享元素 / 设置）

| 位置 | 触发 | 真实 spec | 阶段 | 守卫 | 可中断 |
| --- | --- | --- | --- | --- | --- |
| `NirikoNonModalSheet.kt:106-112` | 入场 | `entry.animateTo(0f, spatialDefault())`（1=屏外） | D ✅ | ✅ `:82` | ✅ 可被预测返回接管 |
| `NirikoNonModalSheet.kt:139-155` | 拖拽把手 | 每 delta `scope.launch { drag.snapTo(...) }`（**每帧一协程**）；dismiss = `animateTo(flyOut, spatialFast())` (:148)；否则 `animateTo(0f, spring(0.85f, Spring.StiffnessMediumLow))` (:151) | D ✅ | ✅ | ❌ **两处均无 initialVelocity**（`velocity` 参数拿到未用）→ §B-3 / §B-8 |
| `NirikoNonModalSheet.kt:94-104` | 预测返回 | `entry.snapTo(backPreview.progress)` → 提交 `animateTo(1f, spatialFast)` | D | ✅ | ✅ 部分 |
| `GlassAnchoredMenu.kt:193` | 展开/收起 | **无动画**：`if (!expanded || anchorInRoot == null) return` | — | — | — |
| `GlassAnchoredMenu.kt:120-168` | 定位 | `Layout` 一次 `glassMenuPosition`（纯函数，有单测） | L | — | — |
| `CoverImage.kt:107-116` | 图片完成 | `ImageRequest.crossfade(false)` | — | — | — |
| `SharedSubjectCover.kt:19-30` | 同上 | `crossfade(false)` + `placeholderMemoryCacheKey` | — | — | — |
| `GlassControls.kt:95-100` | 开关 | `spring(0.6f, 400f)` 字面量，读在 `:132 .offset(x=…)` | **L** ❌ | ❌ | — |
| `GlassControls.kt:102-105` | 轨道色 | `animateColorAsState` 无 spec | D | ❌ | — |
| `GlassControls.kt:308-316` | 满分庆祝 | `spring(0.45f, 520f)`，reduceMotion 时目标恒 1f | D ✅ | ✅ | — |
| `GlassControls.kt:403-419` | 进度条 | `if (reduceMotion) snap() else effectsDefault()` ✅；读在 `fillMaxWidth(animated)` | **L** ⚠ | ✅ | — |
| `GlassCard.kt:144-148` | 按压发光 | `spring(0.5f, 400f)` 字面量 | D | ❌ | — |
| `SettingsGroup.kt:104-125` | 整行圆角 morph | `spatialDefault()`，reduceMotion→`snap()` | L（宽度是布局量）+D | ✅ | — |
| `AdaptiveDetailScaffold.kt:224-244` | 双栏 gutter | `spatialDefault()`，守卫 | L | ✅ | — |
| `AdaptiveSettingsPane.kt:259-290` | 宽屏分类切换 | `Crossfade(effectsFast)`，守卫 | D | ✅ | — |

### A.7 一次性反馈（登记备查，本轮不改）

| 位置 | 时长 | easing | 守卫 |
| --- | --- | --- | --- |
| `CompletionCelebration.kt:36,91-92` | 1050ms / 24 粒子 | Linear | ✅ |
| `EpisodeCompletionWave.kt:26-39` | 700ms（policy） | Linear | ✅ |
| `SubjectReturnGlow.kt:41-114` | 300ms | Linear | ✅ |
| `CrtOverlay.kt:79-89` | 4000ms（policy） | Linear | ✅ |
| `GlassControls.kt:283-362` | 600ms 庆祝（policy） | spring 0.45/520（见 §A.8） | ✅ |
| 触感 3 语义（LongPress / TextHandleMove / Confirm） | — | — | 不受 reduceMotion 影响（正确） |

### A.8 字面量绕过 `NirikoMotionSpecs` 清单（**21 spring + 20 tween**）

**spring（21 处业务）**

| 值 | 位置 | 语义 | 建议 |
| --- | --- | --- | --- |
| `0.6f/400f` ×3 | `GlassControls.kt:98`、`SearchToolbar.kt:137`、`SubjectDetailScreen.kt:2477` | 二态 thumb / 选中底色 | → 新档 `micro()`（§C.2） |
| `0.45f/520f` | `GlassControls.kt:314` | 满分弹跳 | → `micro()` |
| `0.5f/400f` | `GlassCard.kt:146` | 按压发光 alpha | → `micro()` |
| `0.5f/300f` | `TouchGlow.kt:32` | 白晕 | → `micro()` |
| `0.5f/Medium` | `SubjectDetailScreen.kt:2381` | 星级弹跳 | → `spatialFast()` |
| `0.85f/MediumLow` | `NirikoNonModalSheet.kt:151` | sheet 回弹（与 `appearSpring()` 同值） | → `appearSpring()` |
| `0.8f/400f` | `IosStyleSearchComponent.kt:162` | 搜索卡尺寸 | → 先试 `spatialDefault()`，嫌硬再议 `spatialSoft` |
| `0.8f/300f` | `IosStyleSearchComponent.kt:192` | 内容露出 | → `spatialDefault()` |
| `0.7f/420f` | `IosStyleSearchComponent.kt:631` | 关闭钮滑入 | → `spatialFast()` |
| `0.8f/400f` | `VerticalTopSelector.kt:91` | 趋势胶囊宽度 | → `spatialDefault()` |
| `MediumLow` ×8 | `SearchFilterPanel.kt:113-116,329-332` | 筛选面板展开 | → `spatialDefault()` |
| `380f/0.9f` | `util/ScrollToTop.kt:90` | 重选回顶 | → `spatialDefault()`（现 380 明显慢于 700） |

**tween（20 处时长字面量）**：`180`×2（`SubjectDetailScreen.kt:540,2280`）、`220`×3（`MainActivity.kt:290`、`MainPager.kt:151`、`SubjectSearchScreen.kt:174`）、`160/140/200/200/240/260/260`（`IosStyleSearchComponent.kt:198,203,208,406,432,466,630,632,633`）、`300`×2（`Screens.kt:491`、`SearchResultsPane.kt:223`）、`3000`×2（`CardRarityMaterial.kt:296-297`）、`350`（`SubjectDetailScreen.kt:532`，**刻意保留**）。

**关键观察**：同一屏内 **180 / 220 / 240 / 260 / 300 / 350** 六个时长并存；底栏 220ms 与顶栏 220ms 是**两条独立动画**（`MainActivity.kt:280-293` 的 `animate()` vs `MainPager.kt:148-153` 的 `animateFloatAsState`），只靠「同参数」视觉对齐。

### A.9 帧阶段违规清单（niriko-motion 第七节）

| # | 位置 | 违规 | 影响 | 修法 |
| --- | --- | --- | --- | --- |
| 1 | `NirikoBottomBar.kt:54` | 组合期读每帧变化的 `hideProgress`，并把结果捕获进 `offset{}/graphicsLayer{}` | 隐藏的 220ms 内**每帧重组整条底栏**（下游 `LiquidBottomTabs` 双份 content + 3 层 `drawBackdrop`） | 改 `val fraction = { bottomBarHideFraction().coerceIn(0f,1f) }`，`offset { … fraction() … }` / `graphicsLayer { alpha = 1 - fraction() }`；离散 `fullyHidden` 用 `derivedStateOf` 包 |
| 2 | `NirikoNavSuite.kt:100,119-125` | 同上（dock 隐藏 24dp 位移 + alpha） | 宽屏隐藏期间每帧重组竖向 dock | 同上 |
| 3 | `TimelineSection.kt:113-116` | 组合期算 `pageOffset/scale/alpha` 再传给 `graphicsLayer` | 竖向翻页时相邻 timeline 页**每帧重组**（含封面+文字） | 三行移进 `graphicsLayer { … }` lambda（绘制期读 `pagerState`） |
| 4 | `Screens.kt:163` | 组合期读 `currentPageOffsetFraction`，驱动 4 个 `Box.size(width=…)` | 每帧重组+重测 4 个小 Box（成本低，但违反纪律） | 自绘 `Canvas`/`drawBehind` 读 state，或把宽度改 `graphicsLayer{ scaleX }` |
| 5 | `GlassControls.kt:132,259` | 动画/进度值喂给 `.offset(x = …)`（布局阶段） | 每帧重测 thumb（尺寸小，成本可控） | `graphicsLayer { translationX = … }` |
| 6 | `GlassControls.kt:419` | `fillMaxWidth(animated)`（布局期读进度） | 进度条每帧重测 | `drawBehind { drawRect(width = size.width * animated) }` 或 `graphicsLayer{ scaleX }` |

**健康范本（勿改，可作 review 基准）**：`AdaptiveInsets.kt:30-56`（`layout{}` 里读 hideFraction，只重测不重组）、`NirikoBottomBar.kt:58-67`（隐藏用 offset+graphicsLayer 而非 translationY，命中区正确）、`RevealOnScroll.kt:72-87`、`InnerShadow.kt:119-122`（BlurEffect 只在 radius 变化时重建）。

---

## B. 缺失过渡清单（真实交互路径上的「跳变」）

**判定方法**：对每条用户能走到的路径，问「A 状态与 B 状态之间，屏幕上有没有任何东西在 0→1 之间变化」。没有 = 硬切。全仓 `AnimatedContent = 0` 意味着**所有状态机型的切换（加载/空/错误/正文/网格/列表/骨架）天然是硬切**——这是本轮 P0 的共同根因，不是巧合。

### B-1 · P0 · 详情页折叠区块：内容整块「啪」出现/消失

- **现象**：点「展开全部简介 / 展开标签」，下方内容瞬间撑开，滚动位置跳半屏；再点收起，内容瞬间消失，手指下的原文案凭空不见。这是全 app 最容易被一眼看出的硬切。
- **证据**：`ui/subject/SubjectDetailScreen.kt:2369`（`CollapsibleSection` 的收尾，函数签名在 `:2336-2342`，参数含 `label / summary / collapsed / onToggle / content`）

  ```kotlin
          Text(
              if (collapsed) "▸" else "▾",   // :2363-2367 箭头翻转是唯一「过渡」
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
      }
      if (!collapsed) content()               // :2369 ← 内容整块进出，无 AnimatedVisibility
  }
  ```

  调用点：`:991`（简介）、`:1385`（标签/制作组）、`:1659`（角色）。三处共用同一个壳，**只有箭头符号在变**。
- **归因**：违反 niriko-motion 铁律 1（时序单源）+ 「缺失过渡」原则——状态切换没有过渡层。不涉及任何 token 越界（这里根本没有动画）。
- **方案**：
  - 展开：`AnimatedVisibility(visible = !collapsed, enter = expandVertically(animationSpec = NirikoMotionSpecs.spatialDefault(), expandFrom = Alignment.Top) + fadeIn(NirikoMotionSpecs.effectsFast()), exit = shrinkVertically(animationSpec = NirikoMotionSpecs.spatialFast(), shrinkTowards = Alignment.Top) + fadeOut(NirikoMotionSpecs.effectsFast()))`。
  - **顺序纪律**：位移（expand/shrink）走 `spatial*`，透明度走 `effects*`，两者并联 = 高度与淡入同时开始、淡入先结束（"高度跟上、内容先清晰"），符合 M3 expand 语感。
  - 高度变化导致的 LazyColumn 滚动锚定：`Modifier.animateItem()` 只对 item 生效，**跨 item 的高度变化需把整个区块包成一个 item**；现三处调用点都在同一 LazyColumn item 内，无需额外处理，但要确认不触发 `LazyListState` 重排抖动（验收项）。
- **落点**：`ui/subject/SubjectDetailScreen.kt:2363` 的 `CollapsibleSection`（原地改，签名不变，零调用点改动）；可选抽出到 `ui/components/CollapsibleContent.kt` 供 share/settings 复用。
- **reduceMotion**：`LocalReduceMotion.current` 为 true 时 enter/exit 全部换成 `EnterTransition.None / ExitTransition.None`（净效果 = 现在的行为，但集中一处）. 用 §E.3 的 `orSnap(reduceMotion)` 助手一行业务代码。
- **验收**：新增单测 `CollapsibleSectionMotionTest`（Robolectric，断言 reduceMotion=true 时无 Transition 播放、reduceMotion=false 时 `AnimatedVisibilityScope` 存在）；真机目视：展开时滚动位置不移位、收起时上方内容不闪。

### B-2 · P0 · 锚定菜单（GlassAnchoredMenu）没有任何进出场

- **现象**：点搜索框旁的筛选/排序图标，菜单**当帧出现**（无淡入、无缩放、无从锚点展开），点外部**当帧消失**。位置是对的（`glassMenuPosition` 有单测），但观感是「贴图弹出来」。
- **证据**：`ui/components/GlassAnchoredMenu.kt:193`

  ```kotlin
  if (!expanded || anchorInRoot == null) {
      return
  }
  ```

  定位逻辑在 `:135-168`（`Layout` + 纯函数 `glassMenuPosition(anchor, host)`，输出 `IntOffset`）。整个文件 `animate*` 命中 = 0。
- **归因**：缺失过渡（P0 级：用户第一眼就注意到「菜单没有出现动画」）。附带的次生问题：无 `transformOrigin`，未来加缩放时必须从锚点方向长出来，否则「从中心放大」的默认原点会与锚点错位。
- **方案**：
  - 外层包 `AnimatedVisibility(visible = expanded && anchorInRoot != null, enter = fadeIn(NirikoMotionSpecs.effectsFast()) + scaleIn(NirikoMotionSpecs.spatialFast(), initialScale = 0.94f, transformOrigin = glassMenuTransformOrigin(anchorRect, hostSize)), exit = fadeOut(effectsFast()) + scaleOut(spatialFast(), targetScale = 0.97f, transformOrigin = 同))`。
  - 退出用 0.97 而非 0.94：退场幅度应小于入场（视觉上「收回去」而不是「再长一次」）。
  - 新增纯函数（可直接单测，不依赖 Compose）：`fun glassMenuTransformOrigin(anchor: Rect, host: IntSize): TransformOrigin`——由锚点中心相对 host 的归一化比例给出，锚点在下方则 `pivotY = 0f`（从下往上长）。落点 `ui/components/GlassMenuOrigin.kt`，与 `GlassMenuPosition.kt` 同目录。
- **落点**：`ui/components/GlassAnchoredMenu.kt:193` + 新文件 `ui/components/GlassMenuOrigin.kt`。
- **reduceMotion**：enter/exit 换成 None（= 现状）；**但 `visible` 判定必须保持与 reduceMotion 无关**，否则菜单在 reduceMotion 下永不出现（易踩坑）。
- **验收**：`GlassMenuOriginTest`（4 个象限的 origin 断言：上/下/左/右各一例）；真机：从右上角图标点开，菜单从该角长出来，不是从屏幕中心放大。

### B-3 · P1 · 非模态 sheet 松手回弹不接初速（物理不连续）

- **现象**：把 sheet 往下拖一点再松手（未达 dismiss 阈值），它**从静止开始**往回弹——先顿一下再加速，和手指甩出去的速度完全无关。快速小幅甩动时这个「顿」非常明显。
- **证据**：`ui/components/NirikoNonModalSheet.kt:139-155`（把手上的 `Modifier.draggable`，`onDragStopped = { velocity -> ... }` 拿到 `velocity` 却**没用**）：

  ```kotlin
  onDragStopped = { velocity ->
      scope.launch {
          if (shouldDismissSheet(drag.value, velocity, distanceThresholdPx, velocityThresholdPx)) {
              val flyOut = panelHeightPx.coerceAtLeast(1f) + distanceThresholdPx
              drag.animateTo(flyOut, specs.spatialFast())                     // :148 无 initialVelocity
              onDismiss()
          } else {
              drag.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))  // :151 无 initialVelocity + 字面量
          }
      }
  },
  ```

  两处 `.animateTo` 都从静止起步 → 拇指甩出去的动量被丢弃。
- **归因**：违反铁律 2「物理优先：手势位移 spring + initialVelocity」。对比健康范本 `ui/bottombar/utils/DampedDragAnimation.kt:125-140` 的 `animateTo(target, valueAnimationSpec, initialVelocity)`——同一代码库里已有正确写法。
- **方案**：
  - `drag.animateTo(0f, animationSpec = NirikoMotionSpecs.appearSpring(), initialVelocity = velocity)`——**不需要除 density**：`Animatable<Float>` 的值是 px、`onDragStopped` 给的 `velocity` 也是 px/s，两者单位已一致。
  - 回弹 spec 换成 `NirikoMotionSpecs.appearSpring()`（= spring(0.85f, MediumLow)，与现字面量同值 → **视觉零回归**，只补速度），删掉字面量。
  - `onDragCancel` 分支同样处理；`Animatable` 自带 `stop()` 语义，无需改 MutatorMutex。
- **落点**：`ui/components/NirikoNonModalSheet.kt:139-154`。
- **reduceMotion**：现状 `:82` 已守卫入场；回弹动画在 reduceMotion 下应直接 `snapTo(0f)`（否则「减少动效」用户仍看到一次回弹）。
- **验收**：新增 `NonModalSheetVelocityTest`（Robolectric 难测速度，改为**纯函数**抽出 `fun sheetSettleTarget(dragOffset: Float, velocity: Float, threshold: Dp): SheetSettle` 并单测「向上甩 → 回弹、向下甩过阈值 → dismiss」）；真机：小幅快甩回弹无停顿。

### B-4 · P1 · 封面/图片加载完成是「跳」出来的

- **现象**：详情页大封面、列表卡片封面从占位灰块**瞬间变成图片**（无淡入）。图片已缓存时不明显，首次加载（弱网/深翻）可见硬切。
- **证据**：`ui/components/CoverImage.kt:107-116` 的 `ImageRequest.Builder(...).crossfade(false)`；`ui/components/SharedSubjectCover.kt:21-30` 同样 `crossfade(false)`。
- **归因**：Coil 的 `crossfade(false)` 是为了**共享元素飞行不糊**（飞行期间禁止额外 alpha 变化）而关的，这个决定本身正确；问题是**只关了全局**，没区分「正在飞行的这一张」与「普通加载」。
- **方案**：把 `crossfade(false)` 改成条件式：
  ```kotlin
  val sharing = LocalSharedTransitionScope.current != null && LocalDetailHeroKey.current != null
  .crossfade(if (sharing) 0 else COVER_IMAGE_CROSSFADE_MS)  // 150
  ```
  `crossfade(false)` 与 `crossfade(0)` 等价；`COVER_IMAGE_CROSSFADE_MS = 150` 放 `NirikoMotionSpecs`（§C.2）。**不要用 200ms**（200 是封面共享路径的专属时长，动画混用会让「飞行」与「加载」两条路径在 code review 里无法区分）。
  - `SharedSubjectCover` 同理；它是共享元素的源/目标，`sharing` 判定恒为 true 时可安全保持 0。
- **落点**：`ui/components/CoverImage.kt:107-116`、`ui/components/SharedSubjectCover.kt:21-30`、`NirikoMotionSpecs.kt` 加常量。
- **reduceMotion**：reduceMotion 下强制 `crossfade(0)`（淡入属于入场类，规范承诺覆盖）。
- **验收**：`CoverFadePolicyTest`（纯函数 `fun coverCrossfadeMs(sharing: Boolean, reduceMotion: Boolean): Int` 四象限断言）；真机：清空 Coil 缓存后进详情，封面渐显而非瞬现。

### B-5 · P1 · 详情页「骨架 → 正文」整块硬替换（且 hero 封面会重建）

- **现象**：从列表点进详情，先是灰色骨架；数据到达的瞬间**整屏换血**：骨架消失、正文出现、hero 封面也从占位跳到真图。封面共享飞行本应覆盖这段，但 `when` 分支重建 Composable 会让飞行中的 hero 参与重组，飞行末期会有一次轻微「贴合抖动」。
- **证据**：`ui/subject/SubjectDetailScreen.kt:688-695`

  ```kotlin
  when {
      // B4：首屏用分区骨架替代居中转圈（形状对齐真实正文，数据到达不跳版）
      state.isLoading -> SubjectDetailSkeleton(topInset = headerHeight)     // :690
      state.error != null -> ErrorContent(message = state.error ?: "", onRetry = onRetry)  // :691-694
      subject != null -> Box { SubjectDetailBody(...) }                     // :695+
  }
  ```

  三个分支直接替换组合树，无 `AnimatedContent`、无 `Crossfade`（全仓 `AnimatedContent` 命中 0）。注意 `state.error != null` 与 `subject != null` 可**同时为真**（有旧数据 + 刷新失败），分支顺序决定了「有内容时错误只弹 toast 不换页」，改过渡时不得改变这个优先级。
- **归因**：缺失过渡 + 违反「共享飞行期不重建 hero」的隐式约束。骨架→内容的硬切同时**放大了骨架与真实排版的高度差**（骨架 block 高度是估值），硬切瞬间会看到一次页面「上跳」。
- **方案**：
  - 引入 `enum class DetailPhase { Loading, Error, Ready }`（放 `ui/subject/DetailPhase.kt`，纯 Kotlin，UiState 里用它替代 sealed 类或补一个派生属性）。
  - `AnimatedContent(targetState = phase, transitionSpec = { fadeIn(NirikoMotionSpecs.effectsFast()) togetherWith fadeOut(NirikoMotionSpecs.effectsFast()) }, label = "detailPhase")`——**只用淡入淡出，不做 sizeTransform**（`SizeTransform` 会在动画中每帧测量两个页面，成本高且含 LazyColumn）。
  - **关键纪律**：hero 封面（`sharedElement` 那个 `SubcomposeAsyncImage`）与顶栏必须**提到 `AnimatedContent` 之外**，否则飞行中的共享元素会被移出组合树，飞行中断。现状 `:1260` 的 hero 在 `DetailContent` 内 → 需要把 hero key 与 hero 组件上提一层。
  - 骨架高度与真实排版对齐：`.sizeIn(minHeight = ...)` 或在 Loading 阶段就渲染真实 section 骨架（`StateComponents.kt:74-90` 的 shimmer 已有），减少淡入时的位移感。
- **落点**：`ui/subject/SubjectDetailScreen.kt:688-695` + 新 `ui/subject/DetailPhase.kt`。
- **reduceMotion**：`transitionSpec` 在 reduceMotion 时返回 `EnterTransition.None togetherWith ExitTransition.None`。
- **验收**：`DetailPhaseTest`（假 UiState 依次 Loading→Ready，断言 phase 派生正确）；真机：从列表点进详情，骨架→正文是淡的，hero 封面无二次闪。

### B-6 · P1 · 统计页「骨架 → 20+ 卡片」整屏同帧出现

- **现象**：进统计页，先是 3 个骨架块；数据到达后 20+ 个卡片 + 图表**在同一帧全部出现**，视觉上是「闪一下」。
- **证据**：`ui/stats/StatsScreen.kt:208-217`（`when` 分支返回 `StatsSkeleton()` 或 `LazyColumn { ... }`，两分支间无过渡）；`NirikoRadarChart.kt:67-79` 的 growth 动画只在 `growth < 1f` 时跑，**不参与**骨架→内容的接缝。
- **归因**：缺失过渡 + 入场动画的对象选错：radar growth 是「图表自己长出来」，但用户看到的是「整屏换了」。
- **方案**：
  - 外层 `Crossfade(targetState = isLoading, animationSpec = NirikoMotionSpecs.effectsFast(), label = "statsPhase")`（`Crossfade` 比 `AnimatedVisibility` 更适合二态整体切换，且比 `AnimatedContent` 便宜）。
  - radar growth 的触发时机：把 `growth` 的 `LaunchedEffect` 从「首次组合」改为「`isLoading` 首次变 false 之后」——需要把 `animate = !isLoading` 参数传下去，或让 `NirikoRadarChart` 接 `enabled: Boolean`。**顺序**：先 fade 到 0.5 左右再让图表开始长，避免两个动画互相抢注意力（用 `LaunchedEffect(isLoading) { if (!isLoading) { delay(fadeMs / 2); growth.animateTo(...) } }`）。
- **落点**：`ui/stats/StatsScreen.kt:208-217`、`ui/stats/NirikoRadarChart.kt:67-79` 加 `enabled` 参数。
- **reduceMotion**：`Crossfade` spec 换 `snap()`；growth 直接 `snapTo(1f)`（保持「无过渡但数据正确」）。
- **验收**：真机：进统计页，骨架淡出、内容淡入、图表随后生长，三段可辨但不抢；`motionEnabled()` 关闭时三段合一（瞬时）。

### B-7 · P1 · 列表 ↔ 网格视图切换是「整块重排」

- **现象**：搜索结果 / 库页点视图切换按钮，卡片从 1 列变 2 列（或反之）瞬间重排，无任何过渡；而且切换瞬间滚动位置被重置到顶部附近。
- **证据**：`ui/screens/Screens.kt:457-458` 的 `} else { when (viewStyle) {`，`:458-459` 起三分支 `LibraryViewStyle.LIST -> LazyColumn(...)` / `GRID -> LazyVerticalGrid(...)` / `GALLERY -> ...`（三棵 Lazy 子树互斥）；`ui/search/SearchResultsPane.kt` 同构。
- **连带证据**：顶部渐隐的输入也是三选一 —— `Screens.kt:406-419` 的 `headerFadeAlpha = when (viewStyle) { LIST -> ...listState...; GRID -> ...gridState...; GALLERY -> ...galleryState... }`。切视图 = 换一个滚动状态源 → **渐隐 alpha 在切换瞬间按新列表的初始 offset 重算**，视觉上顶栏亮度会跳一档。
- **归因**：缺失过渡 + **刻意为之的性能取舍**（`AnimatedContent` 会把两棵 Lazy 同时组合 → 双份 item、双份图片请求）。所以方案必须避开「双份 Lazy」。
- **方案**（低成本、不双组合）：
  - 方案 A（推荐）：`AnimatedContent(targetState = isGrid, transitionSpec = { fadeIn(NirikoMotionSpecs.effectsFast()) togetherWith EnterTransition.None }, contentKey = { it })`——**exit 用 None**，出向的 Lazy 立即移除（不参与淡出），只有入向淡入。代价：切换瞬间背景会闪一下黑（因为出向已消失、入向 alpha 从 0 起）。
  - 方案 B（更稳）：保留互斥，但给**外层容器**加一次 `animateFloatAsState` 的 alpha 包络：切换时 `alpha 1→0.6→1`（`tween(120)+tween(180)`），期间完成 Lazy 替换。视觉上是「呼吸式切换」，不闪黑，零双组合成本。
  - 两案都必须**保留滚动位置**：切换前 `rememberLazyListState` 的 `firstVisibleItemIndex` 注入新 Lazy（现重置行为不属于动效问题，但会放大「重排」观感，记为连带修复项）。
- **落点**：`ui/screens/Screens.kt:457-458`（库页）、`ui/search/SearchResultsPane.kt` 的视图分支。
- **reduceMotion**：方案 A 的 `fadeIn` 换 None；方案 B 的包络 `snap()`。
- **验收**：真机：切换视图时不闪黑、滚动位置保持；`ViewToggleMotionTest`（纯函数 `viewToggleAlphaEnvelope(reduceMotion)` 返回 1f/包络）。

### B-8 · P1 · sheet 拖拽：每帧移动起一个协程 + 头尾两条 spec 不一致

- **现象**：拖把手过程中，面板跟手有「橡皮筋」感（手指走了、面板慢半拍才到），低端机上尤其明显；松手后无论往下甩还是回弹，动画起点都与手指速度无关（B-3 的另一半）。
- **证据**：`ui/components/NirikoNonModalSheet.kt:139-143`

  ```kotlin
  .draggable(
      orientation = Orientation.Vertical,
      state = rememberDraggableState { delta ->
          scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }   // :142 每个 delta 起一个协程
      },
      onDragStopped = { velocity -> ... },
  ),
  ```

  `rememberDraggableState` 的 lambda 是同步回调，这里为了用 `Animatable` 的 `snapTo` 而 `launch` 协程 → 每个 pointer move 都要过 `MutatorMutex`，且与 `:148/:151` 的 `animateTo` 争同一把锁；拖动期间若恰好有一次 `animateTo` 未结束，`snapTo` 会被互斥排队 → 面板「追不上手指」。
- **归因**：违反铁律 3（可中断 = 跟手不能被动画排队拖住）+ plans M6（**该欠账本轮确认成立**，见 §G.2）。与 niriko-motion「跟手用持续手势驱动、只在松手时用 spring」的物理优先原则冲突：**跟手不该经过动画引擎**。
- **方案**：
  - 跟手位移改为普通状态：`var dragOffset by remember { mutableFloatStateOf(0f) }`，`rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) }`（同步、零协程）。
  - 松手才有动画：`scope.launch { animate(initialValue = dragOffset, targetValue = 0f, animationSpec = appearSpring(), initialVelocity = velocity) { v, _ -> dragOffset = v } }`（或保留一个只在松手时使用的 `Animatable`，初始值 `dragOffset`）。
  - `graphicsLayer` 里 `translationY = panelHeightPx * entry.value + dragOffset + panelHeightPx * preview`。
  - 头尾 spec 统一：进入（`:108`）`spatialDefault()`、被预测返回打断（`:101`）`spatialFast()`、回弹 `appearSpring()`——**这是三个不同语义，保留差异是对的**，但回弹必须补 `initialVelocity`（B-3），并把 `:151` 字面量换成 `appearSpring()`（同值）。
- **落点**：`ui/components/NirikoNonModalSheet.kt:139-155`（跟手状态 + 松手动画），`:120-123`（`translationY` 表达式）。
- **reduceMotion**：跟手照常（直接操作）；松手回弹在 reduceMotion 下 `snap` 到 0（现 `:98-112` 已有 guard，补 `:151` 分支即可）。
- **验收**：`SheetDragSettleTest`（纯函数 `settleTarget(dragOffsetPx, velocityPx, distThreshold, velThreshold)` 覆盖四象限：回弹/关闭 × 慢/快）；真机：连续拖把手，面板严格 1:1 跟手且无回滞。


### B-9 · P1 · 页面级预测返回没有跟手（只有 2 个组件接了 PredictiveDismiss）

- **现象**：在详情页/设置页从屏幕边缘做返回手势，页面**没有跟手位移/缩小**，只有系统返回箭头浮层；松手后页面按 `popExit` 播 1.08 放大的退场。也就是说：预测返回的「跟手」这一半体验在整个 app 里只有搜索结果卡与 sheet 有。
- **证据**：`ui/components/PredictiveDismiss.kt:37-46` 是全仓唯一入口（`ProgressFlow`/`BackPreview` 封装）；实际接入点仅 `NirikoNonModalSheet.kt:94`、`SubjectSearchScreen.kt:124,199-204`（后者是 `graphicsLayer { scale = 1 - 0.04f * p; alpha = 1 - 0.15f * p }`）。`AndroidManifest.xml:59` 已 `android:enableOnBackInvokedCallback="true"`。**导航层（NirikoNavHost）没有任何 `SeekableTransitionState` 观测**。
- **归因**：缺失过渡（预测返回的「跟手」部分）。注意：NavHost 的 `NavTransitions` 在 navigation-compose 2.8+ 会**自带** seek 到 `popEnter/popExit` 的能力，需真机确认（§G.3 未验证项 1）——若自带，本条降级为「验证 + 给详情页补一个 scale 降级以强化跟手感」；若不自带，才需要自己做。
- **方案**（先验证后实现）：
  - 步骤 1（0.5d）：真机开「开发者选项 → 预测返回动画」做边缘滑动，录屏逐帧看 NavHost 内容是否跟手缩放。
  - 步骤 2a（若已跟手）：仅在 `NirikoNavHost` 里确认 `popEnter/popExit` 的 `scale` 与系统 seek 叠加不超 12%（否则边缘滑到底时内容缩成小方块）。
  - 步骤 2b（若不跟手）：把 `PredictiveDismiss` 提升为 `CompositionLocal`（`LocalBackPreview`）由 NavHost 提供，详情页/设置页在 `graphicsLayer` 里消费：`scale = 1f - 0.03f * p`、`alpha = 1f - 0.08f * p`（**幅度比搜索卡小**，因为整页 + 顶栏 + 底栏一起缩会露背景）。
- **落点**：`navigation/NirikoNavHost.kt`、`ui/components/PredictiveDismiss.kt`、`ui/subject/SubjectDetailScreen.kt`（最外层 `Modifier.graphicsLayer`）、`ui/settings/SettingsScreen.kt`。
- **reduceMotion**：预测返回跟手属「系统直接操作」类，规范允许保留（同触感）；但松手后的 `popExit` 在 reduceMotion 下应为 None（现已由 `NirikoNavHost:187` 守卫）。
- **验收**：真机边缘滑动录屏（逐帧）；单测护栏：`BackPreviewScaleTest`（纯函数 `detailBackPreviewScale(p: Float)` 断言 p=0→1、p=1→0.97、单调）。

### B-10 · P1 · 顶层 chrome（底栏/页面指示器/顶栏）进出二级页是瞬消瞬现

- **现象**：从首页进详情/设置，底栏与页面指示器**当帧消失**；返回时又当帧出现。页面本身有 `enterSharedAxisZ` 的 0.92 缩放，但底栏是「硬切」，两者不同步，观感像「页面推进去了，底栏被人一把抽走」。
- **证据**：`app/src/main/java/com/otakup/niriko/MainActivity.kt:240-243`（`val showBottomBar = currentRoute in topLevelRoutes` 直接参与 `if`）、`:388-394`（`if (showBottomBar) { TopLevelPageIndicator(...) }`）；`ui/screens/MainPager.kt:249-274` 的顶栏搜索卡收起也有同类硬切。
- **归因**：缺失过渡（chrome 是「页面容器的一部分」，容器在变而 chrome 无过渡）。
- **方案**：
  - `AnimatedVisibility(visible = showBottomBar, enter = fadeIn(NirikoMotionSpecs.effectsFast()) + slideInVertically(effectsFast()) { it / 3 }, exit = fadeOut(effectsFast()) + slideOutVertically(effectsFast()) { it / 3 })`——位移只走 1/3 高度（底栏矮，全高位移会撞屏幕边缘）。**不要**改 `MainActivity.kt:280-293` 那条 220ms 的滚动隐藏（刻意保留值）。
  - 时序：chrome 淡出 `effectsFast`（≈200ms 量级感受）与页面 `enterSharedAxisZ` 同期开始 → 「页面推进、chrome 同步让位」。
  - 页面指示器跟随底栏用同一个 `AnimatedVisibility`（现状是两个独立 `if`，会各自闪）。
- **落点**：`MainActivity.kt:240-243`、`:388-394`；`MainPager.kt:249-274`。
- **reduceMotion**：None（瞬切 = 现状）。
- **验收**：真机：从首页点进详情，底栏「退场」而非「消失」，返回时「入场」；`ChromeVisibilityTest`（纯函数 `chromeVisible(route): Boolean` 已有语义，补 TopLevel 判定测试）。

### B-11 · P1 · 搜索卡：12 条并行动画 + 尺寸走布局阶段 + 无 reduceMotion 守卫

- **现象**：点搜索框，卡片从「一行 pill」展开成整屏搜索页。观感上它不是「一次过渡」，而是**十几个元素各自赶路**：卡片宽高、内容透明度、行透明度、行位移、绿条、输入框、关闭钮——彼此错位，展开末段能看到 30-60ms 的「对齐抖动」。低端机上这一段最明显。
- **证据**：`ui/search/IosStyleSearchComponent.kt`：`:162` 尺寸弹簧字面量 `spring<Dp>(0.8f, 400f)`；`:171-185` 尺寸 `animateDpAsState`×N；`:190-194` `reveal = animateFloatAsState(spring(0.8f, 300f))`；`:196-210` `contentAlpha=tween(240)`/`rowsAlpha=tween(220)`/`rowsShift=tween(220)`；`:219-242` 三条 `animateDpAsState(cardSpring)` + 拖拽 1:1；`:404-478` `fakeAlpha=tween(200)`/`indSettle=tween(260)`/`inputAlpha=tween(260)`；`:624-655` 关闭钮 `fadeIn(tween(160)) + slideInHorizontally(spring(0.7f,420f))` / `fadeOut(tween(140)) + slideOutHorizontally(tween(200))`；尺寸实际读取在 `:271-287` 的 `Modifier.requiredWidth/requiredHeight` —— **布局阶段**。全文无 `LocalReduceMotion`。
- **归因**：违反铁律 1（同一物理量 4 种 spec：0.8/400、0.8/300、tween 200-260、spring 0.7/420）+ 铁律 4（尺寸动画走布局阶段）。**不是时长问题，是规格不统一**。
- **方案**（不缩短、不拉长，只收敛）：
  - 尺寸：`spatialDefault()`（0.9/700）；内容/行/绿条/输入框透明度：全部 `effectsFast()`（≈200ms 观感，与现 tween 220-260 接近）；关闭钮位移：`spatialFast()`；关闭钮透明度：`effectsFast()`。
  - **结构收敛**：把「展开进度」收敛成单一 `progress`（0..1，`spatialDefault()`），其余 alpha 全部由 `progress` 派生（`alpha = progress`），派生用 `graphicsLayer { alpha }` 或 `Modifier.alpha`（绘制期），不再各自 `animateFloatAsState`。这直接消掉「12 条并行」的错位。
  - 尺寸阶段：过渡期间把内容包一层 `Modifier.graphicsLayer { scaleX/scaleY }` 近似（避免每帧 `requiredWidth` 重测），动画结束后落回真实尺寸一次（不可见）。或用 `Modifier.animateContentSize(spatialDefault())` 替代 `requiredWidth`（仍走布局，但只有一个测量源）。
- **落点**：`ui/search/IosStyleSearchComponent.kt`（`:162,171-242,404-478,624-655`）；`ui/search/SubjectSearchScreen.kt:166-176` 改为 `spatialDefault()` 对齐（现 tween(220) 且注释与实现不符）。
- **reduceMotion**：所有 spec 换 `snap()`（用 §E.3 `orSnap`）；**状态机不能依赖动画回调**（现无 `.finished` 依赖，安全）。
- **验收**：`SearchCardSpecSelfTest`：断言这些 spec 引用自 `NirikoMotionSpecs`（可用 `@Test fun noLiteralSpringInSearchCard()` 反射/grep 式校验，参照现有 `NirikoMotionSpecsContractTest`）；真机：展开「一次流动」、末段无对齐抖动；低端机录屏。

### B-12 · P1 · 评分星级在「进入页面」时就弹一下（错误触发点）

- **现象**：进详情页，星级数字/星形图标就播一次 1.15 → 1.0 的弹跳（用户还没评分）。真正打分时反而只是数字变化。
- **证据**：`ui/subject/SubjectDetailScreen.kt:2374-2382`

  ```kotlin
  val scale = remember { Animatable(1f) }
  LaunchedEffect(rating) {
      scale.snapTo(1.15f)
      scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
  }
  ```

  `LaunchedEffect(rating)` 在**首次组合**时就会执行一次（`rating` 初值即 key）。真正的用户回调在 `:329-360`（`onValueChange` / `onValueChangeFinished`）。
- **归因**：一次性反馈的**触发点错**（违反「反馈只播一次且只响应真实事件」）。字面量 `spring(0.5f, Medium)` 属令牌越界（§A.8）。
- **方案**：
  - 把弹跳从 `LaunchedEffect(rating)` 挪到用户事件：`:329-360` 的 `onValueChangeFinished` 里触发一次（用 `var bump by mutableIntStateOf(0)` 作为 key，或直接 `scope.launch { playBump() }`）。
  - spec 换 `NirikoMotionSpecs.spatialFast()`（0.9/1400，比 0.5 阻尼更「脆」，符合打分反馈的即时感）；幅度保持 1.15（不被视为「刻意保留」值，属可调）。
  - 首帧禁止播放：`LaunchedEffect` 里加 `if (rating == initialRating) return@LaunchedEffect`，或改用事件口令（与 `CompletionCelebration` 同模式）。
- **落点**：`ui/subject/SubjectDetailScreen.kt:2374-2382` + `:329-360`。
- **reduceMotion**：reduceMotion 时不播弹跳（只保留数字更新）；「反馈只播一次」不受影响。
- **验收**：`RatingBumpTriggerTest`（断言初始 rating 不触发、用户改动触发一次）；真机：进页面不弹、打分弹一次。

### B-13 ~ B-16 · P2 · 其余小接缝（一行一条，避免注水）

| # | 跳变点 | 证据 | 方案 | 验收 |
| --- | --- | --- | --- | --- |
| B-13 | 空态/错误态 ↔ 内容硬替换 | `ui/components/StateComponents.kt:256-433`（各 State 直接 `when` 返回） | 抽 `StateHost(state) { content() }`，内部 `AnimatedContent(effectsFast)`；四态均可复用 | `StateHostTest` 四态切换不闪 |
| B-14 | 筛选项增删硬切（面板内） | `SearchFilterPanel.kt:111-117,327-333` 只有面板整体进出，chip 增删无过渡 | chip 容器加 `animateContentSize(spatialDefault())` + chip 自身 `animateItem`（若在 Lazy 内） | 真机：勾选筛选项面板高度平滑 |
| B-15 | 日历切月/选日硬替换 | `ui/stats/CalendarCard.kt` 内除 sheet/pressTilt 外零动画（grep 命中 0） | 月份文字 `AnimatedContent(effectsFast, 单向 fadeIn + slideInHorizontally(1/8))`；日期格不动（网格本身已有 `animateItem` 就够） | 真机：切月方向感正确 |
| B-16 | 搜索历史/建议列表出现 | `IosStyleSearchComponent.kt` 的 rows 只靠 `rowsAlpha` 整体淡入 | 沿用 rows 的 `animateItem()`（Lazy 内）让逐条入场，避免「一块布」淡入 | 真机：建议逐条落下 |

---

## C. 令牌越界与新增令牌

### C.1 旧令牌：定义了但没人用（8 个零调用）

`ui/animation/NirikoAnimation.kt` 里这些 spec 在**全仓调用点为 0**（`:41 pressSpring`、`:47 appearSpring`、`:53 panelSpring`、`:72 countTween`、`:101 tabFadeIn`、`:102 tabFadeOut`、`:81-86 enterFromRight`、`:91 exitToRight`），只有 `AnimDurationShort/Normal/Long` 还在用（`DetailPosterCard.kt:69`、`NirikoNavSuite.kt:36` 未使用 import）。

- **归因**：令牌「定义了但绕过了」= 比没有令牌更糟，因为 code review 会以为已有单源。这是 plans M1「令牌不内聚」的直接证据。
- **方案**：两个选择，**需要 team 决定**（本文件给推荐）：
  - 推荐：**删除** 8 个零调用 spec + 2 个未使用 import，把 `NirikoAnimation.kt` 收缩成只剩 duration 常量 + `LocalReduceMotion`（避免两个 spec 来源）。
  - 保守：保留 `appearSpring()` 并**真的接上**（`NirikoNonModalSheet.kt:151` 的回弹字面量与它同值，见 B-3），其余 7 个删除。
- **落点**：`ui/animation/NirikoAnimation.kt`、`NirikoNavSuite.kt:36`、`AdaptiveSettingsPane.kt:55`、`UniversalSubjectCard.kt:42`（未使用 import）。
- **验收**：`NirikoMotionSpecsContractTest` 加一条断言：`NirikoAnimation` 中不存在未被引用的 `AnimationSpec` 属性（或直接删除后编译期保证）。

### C.2 新增令牌（本文件全部方案只依赖这些新符号）

> 纪律：**不新增时长档位**（六档够用）；只补「六档没覆盖的语义」与「重复字面量的命名」。全部放 `ui/animation/NirikoMotionSpecs.kt`（唯一 source of truth）。

| 新符号 | 定义 | 替代的现字面量 | 用在哪 |
| --- | --- | --- | --- |
| `fun micro(): SpringSpec<Float>` | `spring(dampingRatio = 0.7f, stiffness = 700f)` | `0.6f/400f`×3、`0.5f/400f`、`0.5f/300f`、`0.45f/520f` | 开关 thumb、选中底色、按发光、白晕、满分弹跳 |
| `fun <T> AnimationSpec<T>.orSnap(reduceMotion: Boolean)` | `if (reduceMotion) snap() else this` | — | 所有 `animate*AsState` 的守卫（§E.3） |
| `fun contentTransform(reduceMotion: Boolean): ContentTransform` | `if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None else fadeIn(effectsFast()) togetherWith fadeOut(effectsFast())` | — | §B-5/§B-6/§B-13 的 `AnimatedContent` |
| `fun overlayEnter(origin: TransformOrigin): EnterTransition` | `fadeIn(effectsFast()) + scaleIn(spatialFast(), initialScale = 0.94f, transformOrigin = origin)` | 无（B-2 全新） | GlassAnchoredMenu / 未来 popup |
| `fun overlayExit(origin: TransformOrigin): ExitTransition` | `fadeOut(effectsFast()) + scaleOut(spatialFast(), targetScale = 0.97f, transformOrigin = origin)` | 同上 | 同上 |
| `const val COVER_IMAGE_CROSSFADE_MS = 150` | Int | `crossfade(false)` 的全局关闭（B-4） | CoverImage / SharedSubjectCover |
| `const val CHROME_HIDE_DURATION_MS = 220` | Int（**值不变**，仅命名） | `MainActivity.kt:290`、`MainPager.kt:151` 的 `tween(220)` | 底栏滚动隐藏 / 顶栏隐藏 |
| `const val DETAIL_CHROME_DURATION_MS = 180` | Int（**值不变**，仅命名） | `SubjectDetailScreen.kt:540` 的 `tween(180)` | 详情页顶栏显隐 |

**注意**：`CHROME_HIDE_DURATION_MS = 220` 与「底栏 220ms 刻意保留值」是**同一个值**——本条只是把散在 2 个文件的 `tween(220)` 命名，**不改数值**，因此不触碰刻意保留决策。

### C.3 明确不做的三件事（防止后续 review 被带偏）

1. **不引入 `MotionScheme`**：`material3 1.4.0` 的 `MaterialTheme.motionScheme` 是 `internal`（见 `docs/m3-expressive-comparison.md` 结论），外部拿不到；强行反射不值得。
2. **不新增第 7 档弹簧**：`micro()` 是「二态切换」语义的必要补充（0.7/700 介于 `spatialFast` 0.9/1400 与 `spatialDefault` 0.9/700 之间，阻尼更低 → 有轻微过冲，适合开关手感），不是「快一档」；加完后 **6 档 + micro 封顶**。
3. **不改任何既有 duration 数值**：本文件全部方案要么「用现成档」，要么「补缺失的过渡」，**没有一条靠调时长**。

---

## D. 帧阶段与性能红线

### D.1 现状 6 处组合期读动画值（= 每帧重组）

见 §A.9 表格。**修复优先级**（按每帧成本从高到低）：

| 顺序 | 位置 | 为什么优先 | 修法要点 |
| --- | --- | --- | --- |
| 1 | `NirikoBottomBar.kt:54` | 底栏包含 kyant 双层内容（`:194-260` 双份 content + 每层 `drawBackdrop`），重组一次 = 最贵的重组 | `hideFraction` 改成 lambda 参数，只进 `offset{}/graphicsLayer{}` |
| 2 | `NirikoNavSuite.kt:100,119-125` | 宽屏 dock 同理 | 同上 |
| 3 | `TimelineSection.kt:113-116` | 相邻 timeline 页含封面图 + 文本 | 三行移进 `graphicsLayer{}` |
| 4 | `Screens.kt:163` | 成本低（4 个小 Box），但违反纪律 | 自绘或 `scaleX` |
| 5 | `GlassControls.kt:132,259` | 每帧重测 thumb | `graphicsLayer{ translationX }` |
| 6 | `GlassControls.kt:419` | 每帧重测进度条 | `drawBehind` 或 `scaleX` |

### D.2 四条硬红线（本轮新增动画必须遵守）

1. **中端机列表滚动 P95 ≤ 12ms**（niriko-motion 第七节）。新增的 `AnimatedContent/Crossfade` **禁止**同时组合两棵 Lazy（§B-7 已按此取舍：exit = None）。
2. **同屏实时 `RenderEffect` ≤ 2**。§B 全部方案**不新建任何 RenderEffect**；`GlassSectionCard` 的 50ms/500ms 节流（`:168,171`）与 `InnerShadow` 的 `prevRadius` 守卫（`:119-122`）**保持现状**。
3. **共享元素飞行期禁新建 RenderEffect / 禁 alpha 变化**（这是 B-4 里 `sharing` 判定存在的原因）：`SharedSubjectCover` 在共享作用域内必须保持 `crossfade(0)`。
4. **滚动联动只允许连续函数**：顶部渐隐（`TopFadePolicy` 24dp 带 0.35→0.92）与滚动隐藏（48/16dp）已合规，**本章任何方案不得在滚动回调里加 spring**（会产生二次跟手，与 `FloatingControls` 状态机冲突）。

### D.3 断帧风险自检（每条新增过渡的预算）

| 新增过渡 | 每帧工作量 | 风险 | 缓解 |
| --- | --- | --- | --- |
| §B-1 折叠展开 | `expandVertically` 每帧测量内容 1 次 | 低（内容是文本/标签，无图片） | 若内容含异步图，先 `minHeight` 占位 |
| §B-2 菜单进出 | 缩放 + 淡入，菜单是 5-8 行文本 | 极低 | 无 |
| §B-5 详情 phase | 淡入淡出两个子树 | **中**（正文含 Lazy + 图片） | 只淡入淡出、不做 sizeTransform；hero 提到外面（已写） |
| §B-6 统计 phase | 淡入淡出整屏 Lazy | 中（图表 RenderEffect） | `Crossfade` 期间图表 growth 延后启动（已写），避免叠加 |
| §B-7 视图切换 | 方案 B 只改外层 alpha | 低 | 若选方案 A 需实测双组合峰值 |
| §B-10 chrome | 底栏 `AnimatedVisibility` + 玻璃本体 | **中** | 玻璃已有 RenderEffect；淡出期间不新增 layer，只 alpha（现 `:58-67` 已这样做） |

---

## E. reduceMotion 缺口

### E.1 覆盖率（本轮实测）

- 有守卫的读取点：`LocalReduceMotion.current` **22 处** + `motionEnabled()` **8 处** = 30 个点，分布在 ~24 文件。
- 动效调用点总数 ≈ **96** → 覆盖率约 **27%**（与 plans H5 的「8/170」口径不同，因 H5 统计的是文件数；两口径都低）。
- **该覆盖但没覆盖的高频项**（按用户触达频率排序）：

| 未守卫项 | 证据 | 是否必须补 |
| --- | --- | --- |
| 顶栏滚动隐藏 | `MainPager.kt:148-153` | ✅ 必须（系统「移除动画」开着的用户仍看到 220ms 位移） |
| 搜索卡全组件 | `IosStyleSearchComponent.kt`（0 命中） | ✅ 必须（最大的一块） |
| 筛选项展开 | `SearchFilterPanel.kt:111-117,327-333` | ✅ 必须 |
| 开关 thumb / 选中底色 | `GlassControls.kt:95-105`、`SearchToolbar.kt:135` | ⚠ 可选（属「直接操作反馈」，规范允许保留） |
| 评分弹跳 | `SubjectDetailScreen.kt:2374-2382` | ✅ 必须（B-12） |
| 重选回顶 | `ScrollToTop.kt:88-90` | ✅ 必须（一次性滚动，属「动效」） |
| `animateItem()` / `AnimatedVisibility` | 库内建，无 reduceMotion 概念 | ⚠ 接受（逐项加 `orSnap` 成本高、收益低） |
| `ModalBottomSheet`（分享/收藏编辑） | M3 内建 | ⚠ 接受（改造成本 > 收益） |

### E.2 分类判据（写进规范，避免以后每处都要辩论）

| 类别 | 例子 | reduceMotion 行为 |
| --- | --- | --- |
| **入场/退场**（页面、区块、菜单、图片、骨架→内容） | §B-1/2/4/5/6/10 | **必须 snap** |
| **状态切换**（tab 内容、视图切换、空/错态） | §B-7/13 | **必须 snap** |
| **直接操作跟手**（拖拽、pager、预测返回、滚动隐藏） | `NirikoNonModalSheet` 拖拽、`SubjectSearchScreen:199-204` | **保留**（系统直接操作，同触感） |
| **按压/触感反馈**（pressTilt、白晕、开关） | `PressTilt`、`TouchGlow`、`GlassControls` thumb | **保留**（规范明示触感不受影响） |
| **一次性反馈**（庆祝、wave、CRT） | 已全覆盖 | 保留（现行为） |

### E.3 统一助手（一处实现，全仓引用）

放 `ui/animation/NirikoMotionSpecs.kt`（或新 `ui/animation/ReduceMotionSpecs.kt`）：

```kotlin
fun <T> AnimationSpec<T>.orSnap(reduceMotion: Boolean): AnimationSpec<T> =
    if (reduceMotion) snap() else this

@Composable
fun animatedFloatAsState(target: Float, reduceMotion: Boolean, spec: AnimationSpec<Float>): State<Float> =
    animateFloatAsState(targetValue = target, animationSpec = spec.orSnap(reduceMotion), label = "motion")
```

这样每处守卫从「3 行 if/else」变为「1 个参数」，**这是把 27% 提到 90% 的唯一现实路径**（否则改动面太大没人做）。

### E.4 验收

- 单测：`ReduceMotionCoverageTest` —— 枚举一个「必须 snap」清单（§E.2 前两行对应的 8 个 spec 引用点），断言每处调用都传了 `reduceMotion`（可用纯函数 + 反射/源码断言，沿用现有 `NirikoMotionSpecsContractTest` 风格）。
- 真机：系统设置开「移除动画」后走查 10 条路径（进出详情、展开折叠、打开菜单、图片加载、统计骨架、视图切换、搜索展开、返回、切 tab、分享 sheet），**允许保留**跟手与按压反馈。

---

## F. 分阶段落地顺序（低成本高感知优先）

| 批次 | 内容 | 依赖 | 人日 | 感知收益 |
| --- | --- | --- | --- | --- |
| **1** | §B-1 折叠区块 + §B-2 锚定菜单 | 无 | 1.0 | ★★★★★（两个「每次都用」的硬切） |
| **2** | §B-3 sheet 初速 + §B-4 图片淡入 + §B-12 星级触发点 + §B-8 TouchGlow 取消 | 无 | 1.0 | ★★★★（手感级修复，改动小） |
| **3** | §B-5 详情 phase + §B-6 统计 phase + §E.3 助手 + 覆盖 8 处守卫 | §E.3 | 1.5 | ★★★★（进详情/统计的「换血感」消失） |
| **4** | §B-7 视图切换 + §B-10 chrome + §B-9 预测返回（先验证 0.5d） | 无 | 1.5 | ★★★（容器级观感） |
| **5** | §B-11 搜索卡收敛（最大改动，风险最高，单独 PR） | §E.3 | 2.0 | ★★★（搜索是主路径，但现状「能用」） |
| **6** | §A.9/D.1 六处帧阶段重绘 + §C.1 旧令牌清理 | 无 | 1.5 | ★★（用户不直接看见，但为后续动画腾预算） |
| **7** | §B-13~16 P2 + §C.2 令牌标号 | 前 1-2 批 | 1.0 | ★（打磨） |

**合计 ≈ 9.5 人日**。**批次 1-2 共 2 人日即可交付「手感明显不同」的 v1.2.2**，建议作为必上线范围；批次 3-4 视排期。

**每批的 review 纪律**：改完必须实测「列表滚动 P95」（D.2 红线 1）与「同屏 RenderEffect ≤2」，两者任一超限就回退该批的 `AnimatedContent`，改用 §B-7 方案 B 的 alpha 包络。

---

## G. 附录

### G.1 刻意保留 / 复核后推翻了旧结论（**勿再报为缺陷**）

| # | 项 | 事实 | 依据 |
| --- | --- | --- | --- |
| 1 | 封面共享路径 200ms | `SubjectCoverPath.kt:52-68` 用 `tween(200, LinearEasing)` + bounds 每 10ms keyframes | 刻意保留值，**不得改** |
| 2 | 底栏滚动隐藏 220ms | `MainActivity.kt:280-293` 的 `animate(tween(220))` | 刻意保留值 |
| 3 | 详情渐显 350ms | `SubjectDetailScreen.kt:530` | 刻意保留值 |
| 4 | dock/tab kyant 弹簧 | `LiquidBottomTabs.kt:148,164`、`LiquidVerticalDock.kt:149,165`、`DampedDragAnimation.kt:32-36` | 上游库，**不改** |
| 5 | `GlassSectionCard` 50ms/500ms 节流 | `:168,171` | 刻意保留 |
| 6 | `ModalBottomSheet` 出入 | `SubjectDetailScreen.kt:1995-2024`、`SharePreviewSheet.kt:98` | M3 内建，不接管 |
| 7 | 切页无交叉淡入 | `MainPager.kt:137-142` 按 settledPage 直接换 | **v1.2.1 已修闪烁，不要提议加 Crossfade** |
| 8 | sharedElement 全部有 `boundsTransform` | 8 处 × 2（state+bounds）共 16 命中：`CoverImage:84/86`、`PersonDetailScreen:144/146`、`CreditSubjectCard:114/116`、`CharacterDetailScreen:115/117`、`LibraryGalleryCard:71/73`、`CharacterStaffSection:369/371`、`RelationsSection:129/131`、`SubjectDetailScreen:1260/1262` | **推翻「只有 CoverImage 传」的旧说法** |
| 9 | 二级页 `popEnter/popExit` | `NirikoAnimation.kt:107-113`：popEnter = scaleIn 0.92、popExit = scaleOut 1.08 | **推翻 plans M2「沿用前进规格」**（几何是正确反向） |
| 10 | `InnerShadow` 每帧 BlurEffect | `InnerShadow.kt:119-122` 已加 `prevRadius != radius` 守卫 | **推翻 plans L1 的该子项** |
| 11 | `openSubject` 两份实现 | `MainPager.kt:90-99` 虽不 consume，但 `:94/:96` 已 peek/prepare；顶层卡 key `cover_$id` 与详情 hero 回退值（`SubjectDetailScreen.kt:1260`）一致 → 飞行仍成立 | 仅维护重复，**不报用户可见缺陷** |
| 12 | `TouchGlow` 只读事件不消费 | `TouchGlow.kt:23-25` kdoc：「只读事件不消费 → 不影响点击/长按/滚动」；松手靠 `:49` 的 `event.changes.none { it.pressed }` 归零，正常路径不会卡住 | **刻意保留**，勿当缺陷报；只有 spec 字面量属 §A.8 越界 |
| 13 | sheet 返回键关闭无退场 | `NirikoNonModalSheet.kt:94-104` `PredictiveDismissHandler` 已实现：`snapTo(progress)` → `animateTo(1f, spatialFast())` → `onDismiss()` | **推翻 plans H4 的「无退场」子项**；H4 只剩「无初速」一半，见 §B-3 |

### G.2 与已知欠账的映射（这些不是本轮新发现，方案在此给出）

| 欠账来源 | 原文 | 本文档条目 |
| --- | --- | --- |
| audit-2026-10 P1 | 「动效字面量绕过 specs（NirikoNonModalSheet/TouchGlow/GlassCard）」 | §A.8 + §C.2 |
| audit-2026-10 P1 | 「sheet 两个 animateTo 无 initialVelocity」 | §B-3 |
| audit-2026-10 P1 | 「TouchGlow 只读不消费」 | §B-8 |
| audit-2026-10 P1 | 「GlassControls 组合期读动画值」 | §A.9 #5/#6 |
| plans H2 | 组合期读值 | §A.9 / §D.1 |
| plans H4 | sheet 无初速 + 返回键无退场 | §B-3（返回键无退场属 M3 内建，见 G.1 #6，不另开条目） |
| plans H5 | reduce-motion 覆盖不足 | §E |
| plans M1 | 令牌不内聚 | §C.1 / §C.2 |
| plans M4 | `RevealOnScroll` 错峰失效 | `RevealOnScroll.kt:69-71` 的 `LaunchedEffect` 没 gate `entered`，且 `delayMillis` 全仓 0 调用 → 错峰实际只有一个值；P2，本轮不修，仅记录 |
| plans M5 | 手势不消费 | **不成立**：`TouchGlow` 不消费是 kdoc `:23-25` 的刻意设计（不影响点击/滚动），已列入 §G.1 #12 |
| plans M6 | sheet 每 delta 起协程 | **确认成立**：`NirikoNonModalSheet.kt:141-143` 的 `rememberDraggableState { delta -> scope.launch { drag.snapTo(...) } }` 每个 pointer move 起一个协程；方案见 §B-8 |

### G.3 未验证项（**需要真机/低端机确认，本文件不下结论**）

1. **NavHost 预测返回是否自带 seek**：navigation-compose 2.8+ 会 seek `popEnter/popExit`；若自带，§B-9 只需验证 + 补详情页 scale 降级；若不自带，需实现 `CompositionLocal` 方案。**这是 §B-9 的唯一阻塞项。**
2. **系统 `ANIMATOR_DURATION_SCALE = 0` 时 Compose spring 是否真的压成 0**：`snap()` 路径不受影响，但 spring/tween 的 `durationScale` 行为需实测（影响 §E.1 的「必须补守卫」范围）。
3. **搜索卡展开在低端机的掉帧**：§B-11 的收敛方案需要实测对比（收敛前 12 条并行 vs 收敛后 1 条 progress）。
4. **详情页同屏 RenderEffect 实时计数**：正文 hero（共享）+ 玻璃顶栏 + 玻璃胶囊，是否已到 3 个（规范上限 2）需真机 GPU 检查（`dumpsys gfxinfo` 或 Layout Inspector）。若超，§B-5 的 `AnimatedContent` 必须避开玻璃层。
5. **`RevealOnScroll` 错峰是否真的无感**：`staggerIndex` 传了但 `delayMillis` 全仓 0 调用，实际所有卡同时入场；用户是否在意需目视。
6. **无新截图**：本文件全部结论基于源码阅读与 grep，**没有真机截图对比**（v1.2.2 设计阶段不做视觉回归）。

---

*本文件为 v1.2.2 设计阶段产出，未修改任何 `app/src` 文件。所有行号以本轮阅读的工作区源码为准。*