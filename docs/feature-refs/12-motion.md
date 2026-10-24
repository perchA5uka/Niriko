# 12 交互与动效

> 一句话结论：本域 12 条里 **11 条已落地**（12.6 与 11.7 重复，已并入 11.7 并从计数扣除），价值不在「再补功能」，而在两份可核对的事实表：
> 1. **一致性核对矩阵**（12.B.1）：全仓 35 处 `spring(` + 27 处 `tween(`，只有 **13 处**走 `NirikoMotionSpecs`，17 处仍用旧 `NirikoAnimation` 常量，另有约 14 处内联字面量；其中 **11 处是真不一致（该改）**，7 组是刻意例外（不改）。
> 2. **减少动态效果覆盖矩阵**（12.B.2）：30 个文件带 `reduceMotion`/`motionEnabled()` 守卫，但 **dock 的三项动效（指示器滑行/按压缩放/指尖高光）、玻璃按压缩放、vico 图表默认入场全部漏网**（`ui/bottombar` 目录 0 处守卫、图表文件 0 处）。
> 3. 另有两处「写了但没生效」：`TopFadeOverlay.kt` 全仓 **0 调用**（死代码）；`RevealOnScroll` 的 `staggerIndex` **不参与延迟计算**（注释承诺「index × 50ms」，代码只当 `LaunchedEffect` 的 key），两个调用点都只传了 `staggerIndex` → **错峰入场实际不存在**。

## 12.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 12.1 | 连续顶部渐隐 | 已实现（3 页），两套 hiddenHeight 口径 + 统计/设置页无渐隐 | P1 | S | `TopFadePolicy` + `topContentAlphaMask` |
| 12.2 | 入场动画按视口触发并错峰 | 部分（视口对、**错峰未生效**） | P1 | S | `RevealOnScroll` |
| 12.3 | 弹性/弹簧规范集中定义 | 部分（13/49 处走 spec，11 处该改） | P1 | M | `NirikoMotionSpecs`（M3 六档） |
| 12.4 | 触感反馈（评分刻度） | 部分（4 种类型散落 7 处，dock 无触感） | P1 | S | `HapticFeedbackType` 用法表 |
| 12.5 | 一次性反馈（完成/返回） | 已实现（token + isActive 闸门 + reduceMotion 停） | P2 | — | `CompletionCelebrationState` |
| 12.6 | 焦点/方向键可达性 | **已并入 11.7**（不重复展开，计数扣除） | — | — | 见 6/11 域 |
| 12.7 | 玻璃按压动效（gel-press） | 部分（自研 scale/tilt，未用 `pressProgress` 驱动 lens） | P2 | M | kyant `LiquidButton/LiquidToggle` |
| 12.8 | 加载骨架屏（Shimmer） | 部分（只有 `StateComponents` 一个入口） | P2 | S | `ShimmerPolicy` |
| 12.9 | 非模态面板关闭阈值 | 已实现（72dp / 900 纯函数 + 预测性返回） | P2 | — | `shouldDismissSheet` |
| 12.10 | 顶部控件布局与状态计数 | 已实现（「共 N 条 / 候选池 N 条」+「已选 N」） | P2 | — | `SearchFilterPanel` |
| 12.11 | 手势与滚动冲突协调 | 已实现（一把全局锁 + 日历复用） | P2 | — | `LocalSearchGestureLock` |
| 12.12 | 触摸拖动响应与流畅度 | 部分（两份观测文档，无回归门槛） | P2 | S | `docs/performance-audit.md` |
| 12.13 | 动效调试开关与慢放（新） | 无 · 新提出 | P2 | S | 自建 `LocalMotionScale` |
| 12.14 | dock 动画接入 reduceMotion（新） | 无 · 新提出 | P1 | S | 12.B.2 矩阵驱动 |
| 12.15 | 图表入场动效统一（新） | 无 · 新提出 | P1 | S | vico `animateIn/animationSpec` |
| 12.16 | 共享元素转场的降级路径（新） | 部分（有 reduceMotion 分支，语义未定） | P2 | S | `NirikoMotionSpecs.subjectCover*` |
| 12.17 | 手势完成度反馈（新） | 无 · 新提出 | P2 | S | 预测拖动 progress |
| 12.18 | 动效预算与帧率回归（新） | 无 · 新提出 | P2 | M | gfxinfo framestats |

## 12.1 逐条详解

### 12.1 连续顶部渐隐

- **现状**｜已实现但**跨页不一致**。纯规则在 `util/TopFadePolicy.kt`（`FADE_DISTANCE_DP=24f`、`MIN_ALPHA=0.35f`、`MAX_ALPHA=0.92f`、`WINDOW_MASK_HIDDEN_DP=0f`、`scrollOffsetFor` 把「首项索引>0」映射成「已滚很远」、`searchContentPaddingDp` 令顶部内边距等于控件实测高度）。消费方 3 处：`ui/screens/Screens.kt:377-393`（作品库三视图各取一次 `alphaFor`，`hiddenHeight = 56.dp`）、`ui/subject/SubjectSearchScreen.kt:154-175`（`hiddenHeight = WINDOW_MASK_HIDDEN_DP`）、`ui/subject/SubjectDetailScreen.kt:1066-`。**`TopFadeOverlay.kt` 全仓 0 调用**（死代码）；统计页/设置页无渐隐。
- **提升**｜用户侧：四个顶级页的顶部渐隐一致（现在设置页滑到作品库会看到「一个有渐隐一个没有」）。工程侧：删死文件 + 把 `hiddenHeight` 收进 policy，少两处魔数。
- **原理**｜区分「渐隐带高度」与「内容可见高度」：`WINDOW_MASK_HIDDEN_DP`（=0，edge-to-edge 从窗口 y=0 起渐隐）与 `FADE_DISTANCE_DP`（滚多远到满强度）。现在 `Screens.kt` 传 56.dp 等于「保留一个顶栏高度不渐隐」，与 `SubjectSearchScreen` 的 0.dp 语义冲突。统一为 `fun maskHiddenHeight(headerHeightDp, edgeToEdge) = if (edgeToEdge) WINDOW_MASK_HIDDEN_DP else headerHeightDp`，两处都从它取；统计/设置页补渐隐只需外层套 `Modifier.topContentAlphaMask(strength = { alphaFor(...) }, hiddenHeight = ...)` 并接滚动上报（index+offset）。
- **效果**｜从设置页滚到作品库，顶部 24dp 内内容亮度 0.35→0.92 连续变化，四页手感一致；删掉 `TopFadeOverlay.kt` 后不再存在第二套渐隐实现可被误用。
- **参考**｜本项目 `util/TopFadePolicy.kt`、`ui/components/TopContentAlphaMask.kt`、`ui/components/TopFadeOverlay.kt`（0 调用）[码]；「内容延伸到浮层下方」的 edge-to-edge 惯例 [推断]。
- **成本/风险**｜S｜风险：`scrollOffsetFor` 对「首项索引>0」返回 `Float.MAX_VALUE/4`，若 10.14 引入 stickyHeader 会冲突（首项永远是 header，offset 语义变了）。
- **优先级**｜P1

### 12.2 入场动画按视口触发并错峰

- **现状**｜部分。`ui/animation/RevealOnScroll.kt`：`motionEnabled()==false` 直接渲染；`rememberSaveable{entered}` 防滚回重播；视口判据 `onGloballyPositioned{ positionInWindow().y < screenHeight*0.9 }`；alpha 走 `effectsDefault()`，scale(0.97→1)/offset(14dp→0) 走 `spatialDefault()`。**缺陷**：`staggerIndex` 只作 `LaunchedEffect(staggerIndex, delayMillis)` 的 key，**不参与延迟**（注释写「延迟 = index × 50ms」）；两个调用点 `ui/search/DiscoverPosterPane.kt:149`（`index % columns`）与 `ui/search/TrendingSection.kt:204`（`index % 5`）都没传 `delayMillis` → 同屏 5 个卡片**同时**入场。
- **提升**｜用户侧：同屏卡片依次 0/50/100/150ms 出现，有节奏而非「一起弹出」。工程侧：修完注释与实现一致，调用点不用改。
- **原理**｜`val effectiveDelay = delayMillis + staggerIndex * 50`，塞进同一个 `LaunchedEffect(effectiveDelay)`；因为动画由 `animateFloatAsState` 的 target 驱动，延迟只决定「何时把 target 0→1」，不需要新状态机。`rememberSaveable` 已保证滚回的项不再播，所以错峰只发生在首次进入可见。
- **效果**｜发现页海报墙首屏三列错开 0/50/100ms 淡入 + 上移 14dp；快速滚动时不再多个项同时动画争帧（`docs/performance-audit.md` 已记录该争帧现象）。
- **参考**｜本项目 `ui/animation/RevealOnScroll.kt`、`ui/search/TrendingSection.kt:204`、`ui/search/DiscoverPosterPane.kt:149` [码]。
- **成本/风险**｜S｜风险：错峰让人感觉首屏变慢，延迟上限必须 clamp（建议 ≤150ms）。
- **优先级**｜P1

### 12.3 弹性/弹簧规范集中定义

- **现状**｜部分。`ui/animation/NirikoMotionSpecs.kt` 复刻 M3 StandardMotionTokens 六档（`spatialFast/Default/Slow = spring(0.9, 1400/700/300)`、`effectsFast/Default/Slow = spring(1.0, 3800/1600/800)`，文件头记录「material3 1.4.0 的 `MotionScheme` 是 internal，只能自建」），**13 处调用**；旧体系 `ui/animation/NirikoAnimation.kt`（`AnimDurationShort/Normal/Long = 200/300/500`、`pressSpring/appearSpring/panelSpring`）仍有 **17 处引用**；约 14 处完全内联。明细见 12.B.1。
- **提升**｜用户侧：同类交互在不同页面手感一致。工程侧：改一个数只需改一处；material3 1.5 公开 `MotionScheme` 后只改一个文件，调用点不动。
- **原理**｜三档收敛规则：①空间动效（位置/尺寸/形状）→ `spatial*`，按距离量级选 fast/default/slow；②效果动效（透明度/颜色）→ `effects*`，damping 固定 1.0（过冲的 alpha 会闪）；③手势跟手（dock 指示器、面板拖拽）保留专用 spring，因为目标不是「稳定落位」而是「速度衰减」，这 4 处登记例外。落地加一条 lint 式单测 `MotionSpecHygieneTest`：扫 `app/src/main/java` 源文本，断言新出现的 `spring(/tween(` 必须在白名单内，防第 15 个魔数偷长。
- **效果**｜用户感觉不到「参数表」，能感觉到设置行展开、面板上滑、列表入场三处节奏一致（现在 180ms/300ms/spring 混着来）。
- **参考**｜本项目 `NirikoMotionSpecs.kt`、`NirikoAnimation.kt`、`docs/m3-expressive-comparison.md:104-110`（P0-B 落地记录 + 「刻意不动」清单）[码]；`androidx/compose-samples` 本轮未取到（CDN 404），不引用。
- **成本/风险**｜M｜风险：300ms tween 换 spatial spring 会改节奏，`m3-expressive-comparison.md` 要求逐批真机确认；`NirikoAnimation.kt` 的 17 处里含页面转场，换错会「返回时页面还在动」。
- **优先级**｜P1

### 12.4 触感反馈（评分刻度）

- **现状**｜部分。4 种 `HapticFeedbackType` 共 7 处：`Confirm`（`CompletionCelebration.kt:90`、`GlassControls.kt:333`）、`TextHandleMove`（`PressTilt.kt:126`、`GlassControls.kt:311`）、`LongPress`（`Screens.kt:450/506`、`SubjectDetailScreen.kt:316`）。**底部 dock 的 tab 切换与按压缩放没有任何触感**（`ui/bottombar` 0 处）；评分「跨刻度」判定在 `util/RatingFeedbackPolicy.kt`（纯函数，仅 `GlassControls` 滑块使用）。
- **提升**｜用户侧：点 dock 换页不再是「哑的」。工程侧：把「何处分级」写成表，堵住第 5 种用法。
- **原理**｜固定三档语义：`TextHandleMove` = 连续手势的刻度脉冲（评分滑块、拖动排序、dock 拖动），**必须节流**（只在整数刻度变化时触发）；`Confirm` = 状态确认（完成一集、收藏成功、真切换 tab）；`LongPress` = 进入选择态。禁止用于滚动到底/页面进入等无用户动作场景。dock 侧只在 `page != current` 时发 `Confirm`。
- **效果**｜点非当前 tab：一记轻震 + 指示器滑过去；连点当前 tab：不震。评分 7→8：过 8 的瞬间震一次，8.0–8.9 之间继续拖不再震。
- **参考**｜本项目 `util/RatingFeedbackPolicy.kt`、`ui/components/GlassControls.kt:265-333`、`ui/animation/PressTilt.kt:117-126` [码]；上游 1.0.6/2.0.1 的 `LiquidBottomTabs` demo **无任何 Haptic 引用**（[码]），故须自研。
- **成本/风险**｜S｜风险：节流写错会连续震动（体验灾难），必须纯函数测试。
- **优先级**｜P1

### 12.5 一次性反馈（完成/返回）

- **现状**｜已实现且设计干净。`ui/animation/CompletionCelebration.kt`：`events: Flow<Long>`（token）+ `CompletionCelebrationState.isActive` 闸门（注释：「No replay, saved state, layout displacement or pointer interception」）+ `lifecycle.repeatOnLifecycle(RESUMED)`（后台不播）+ `Animatable(1f)` 时间轴 + `reduceMotion` 时 `finish()` 并 `stop()`，并用 `rememberUpdatedState` 读 reduceMotion/haptic 避免重组丢引用。同族：`EpisodeCompletionWave`（`events.filter{it==subjectId}`）、`SubjectReturnGlow`。
- **提升**｜机制已达标。可补「同一次完成在详情页与列表各播一次」的去重（现在两个 host 各自消费 token）。
- **原理**｜token 化事件的通用模式：事件源发 `Long`（自增 id），host 只在 `!playbackState.isActive` 时消费；跨页共享时用 `SharedFlow(replay=0)` 而非 State（replay 会重组重播）；若需「跨页只播一次」，加进程级 `ConsumedEventRegistry`（LRU 64 的 `MutableSet<Long>`）。
- **效果**｜标记看完 → 中央星屑 + 触感；返回列表不再闪第二次；后台标记不播、回前台不补播。
- **参考**｜本项目 `CompletionCelebration.kt`、`EpisodeCompletionWave.kt`、`SubjectReturnGlow.kt` [码]。
- **成本/风险**｜—（加跨页去重为 S）｜风险：token 用时间戳时同毫秒两次完成会撞 key。
- **优先级**｜P2

### 12.6 焦点/方向键可达性（已并入 11.7）

- **现状**｜无，但**本条不再单独展开**：与 11.7 完全同义（全界面键盘/遥控可达、Tab 顺序与焦点可见性），按要求只在 11.7 维护一份，本编号占位保留、**不计入本域条数**。
- **原理/效果/参考**｜见 11.7（`ui/adaptive/*` 的窗口布局与焦点顺序、`androidx.compose.ui.focus` 的 `focusRequester/focusProperties`）。本文件只在 12.B.2 留一行与动效相关的事实：焦点移动的 `focusRestorer` 与 dock 指示器动画并发时会抢帧。
- **成本/风险**｜—｜**优先级**｜—（已并入）

### 12.7 玻璃按压动效（gel-press）

- **现状**｜部分，自研参数。`ui/animation/PressTilt.kt` 用 `animateFloatAsState(NirikoMotionSpecs.spatialFast())` 驱动 `rotationX/rotationY` + scale 并发 `TextHandleMove`；`GlassCard.kt:146` 按压缩放 `spring(0.5f, 400f)`；`GlassControls.kt:81` 是 `spring(0.6f,400f)`、`:290` 是 `spring(0.45f,520f)`；dock 侧 `DampedDragAnimation.kt:32-36` 有 `scaleX(0.6,250)/scaleY(0.7,250)/pressProgress(1,1000)` 五条 spring。上游做法（`LiquidButton.kt` [码]）是「一个 `pressProgress∈[0,1]` 同时驱动等比放大与**方向性挤压**，配合 `InteractiveHighlight` 的指尖光晕，`lens` 仍取常量」，本项目则是「tilt 与 scale 各一条 spring、无方向性挤压」，两者不同构。
- **提升**｜用户侧：按下玻璃卡时高光向内收（lens 变深）而非整块放大，更像真玻璃。工程侧：3 条 spring 合成 1 个 `pressProgress`，少两个动画状态与重组点。
- **原理**｜`val pressProgress = remember { Animatable(0f) }`，`pointerInput` 里 `animateTo(1f/false)`；参数写成 `lens(refractionHeight = lerp(18f,26f,p), depth = lerp(1f,1.6f,p))`、highlight alpha `lerp(0.35f,0.55f,p)`、shadow elevation `lerp(2f,0f,p)`。上游 `LiquidBottomTab` 已用此模式（`LocalLiquidBottomTabScale`）。
- **效果**｜按住收藏卡 200ms：卡片不位移，边缘折射带变窄、内阴影加深、高光偏向按压点；松手 250ms 回弹（damping 0.75，轻微过冲）。
- **参考**｜本项目 `PressTilt.kt`、`GlassCard.kt:146`、`DampedDragAnimation.kt:32-36` [码]；`Kyant0/AndroidLiquidGlass@kmp:app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidButton.kt:62-87` [码] —— 2.x demo 的按压范式是「读 `interactiveHighlight.pressProgress` → 等比放大 `lerp(1f, 1f + 4dp/height, progress)` + 方向性挤压（`translationX/Y` 走 `tanh` 递减，`scaleX/Y` 按 `atan2(offset)` 的 cos/sin 分配）+ `lens(12dp, 24dp)` 静态折射」，本项目只做了 tilt/scale，没有「方向性挤压」。
- **成本/风险**｜M｜风险：改按压驱动会增加 uniform 更新，需确认不被打包节流（`GlassSectionCard.kt:124` 的 500ms 静止节流）冻住。
- **优先级**｜P2

### 12.8 加载骨架屏（Shimmer）

- **现状**｜部分。`ShimmerPolicy.shouldAnimate(reduceMotion, systemAnimatorOff, glassEffect) = !reduceMotion && !systemAnimatorOff && glassEffect != OFF`（`ShimmerPolicy.kt:26-30`）；唯一消费点 `StateComponents.kt:80`，用 valentinilk shimmer 的 `rememberShimmer(shimmerBounds = ShimmerBounds.Window)`；已有 `LibrarySkeleton`（三视图）与 `StatsSkeleton`。**不是所有异步区块都有骨架**：详情页区块、搜索趋势区、日历卡片都没有（grep `shimmer` 仅命中这两文件）。
- **提升**｜用户侧：进详情不再白屏 300ms 再跳内容。工程侧：骨架复用同一 policy，玻璃关闭时不扫光（省一次合成）。
- **原理**｜骨架做成「行/卡/图」三档占位，由各 Lazy 区块的 `isLoading && items.isEmpty()` 驱动；扫光只在一个 `Shimmer` 宿主里跑，子占位用 `.shimmer(sameInstance)` 挂同一实例（项目已如此），避免每占位各开动画。
- **效果**｜详情页先出现灰色区块轮廓 + 1.2s 周期斜向扫光；玻璃档 OFF 时是静态灰块（现有行为）。
- **参考**｜本项目 `ShimmerPolicy.kt`、`StateComponents.kt:42/80-86`、`ui/stats/StatsSkeleton.kt` [码]；`valentinilk/compose-shimmer` 1.5.0（`libs.versions.toml`）[README]。
- **成本/风险**｜S｜风险：`ShimmerBounds.Window` 在长列表里让每项参与窗口级裁剪计算，条目多时耗时；换 `View` 更省但要逐项实例。
- **优先级**｜P2

### 12.9 非模态面板关闭阈值

- **现状**｜已实现。`NirikoNonModalSheet.kt`：`SHEET_DISMISS_DISTANCE_DP = 72f`、`SHEET_DISMISS_VELOCITY_DP = 900f`；判定抽成纯函数 `shouldDismissSheet(dragDistancePx, dragVelocityPx, distanceThresholdPx, velocityThresholdPx) = dragDistancePx > distanceThresholdPx || dragVelocityPx > velocityThresholdPx`；关闭时 `flyOut = panelHeightPx + distanceThresholdPx` 再 `onDismiss()`；手势走 `PredictiveDismissHandler`（预测性返回）。
- **提升**｜机制已达标；可补「轻扫（速度达标距离短）」与「慢拖过阈值」两组边界单测（纯函数已可测）。
- **原理**｜双阈值 OR = 「距离派 ∪ 速度派」：慢拖看距离（72dp），快扫看速度（900）。注意速度阈值命名带 DP 但参与比较的是 px/s，命名有歧义，建议改 `SHEET_DISMISS_VELOCITY_PX_PER_S`。
- **效果**｜上滑超 72dp 或快速一甩即关闭；关闭前面板先飞出屏幕边缘，不会原地消失。
- **参考**｜本项目 `NirikoNonModalSheet.kt:44-58/142-147`、`ui/animation/PredictiveDismiss.kt` [码]。
- **成本/风险**｜—｜风险：预测性返回松手时系统先截一帧再交回应用，`flyOut` 可能与系统快照竞争留下余影。
- **优先级**｜P2

### 12.10 顶部控件布局与状态计数

- **现状**｜已实现。筛选面板 `SearchFilterPanel.kt:321` 显示「共 N 条 / 候选池 N 条」（入参 `resultCount`/`poolCount`）；批量栏 `Screens.kt:622-623` 显示「批量管理：已选 N」；排序/视图菜单用 `Icons.Default.Check` 标当前项（无计数）。
- **提升**｜已达标。可补：排序菜单显示「按评分（N 部）」这类分组计数，让用户切换前知道会剩多少。
- **原理**｜计数口径分层：`poolCount` = 硬过滤后的候选池；`resultCount` = 再叠加评分区间/排序后的条数；顶部控件只显示 resultCount，展开面板才显示两者，避免主界面两个数打架。
- **效果**｜打开筛选面板顶部一行「共 128 条 / 候选池 342 条」；改评分区间时两数同时变。
- **参考**｜本项目 `ui/subject/SearchFilterPanel.kt:269-321`、`ui/screens/Screens.kt:608-645` [码]。
- **成本/风险**｜—｜风险：无（已分层）。
- **优先级**｜P2

### 12.11 手势与滚动冲突协调

- **现状**｜已实现。`navigation/SearchGestureLock.kt` 提供 `LocalSearchGestureLock: MutableState<Boolean>`（未提供时 `error()` 硬失败）；`MainPager` 用 `userScrollEnabled = !lock` 锁顶层 pager；搜索页按下/拖拽期间置 true（`Screens.kt:590 onGestureLockChange`）；日历复用同一把锁（`CalendarCard.kt:684` 注释明确）。
- **提升**｜机制已达标。真缺口：锁是**布尔**，两个子手势同时上锁时先松手的会解掉别人的锁 → 改计数器。
- **原理**｜手势冲突通用解法「谁先赢得手指谁锁路由」：`pointerInput{ awaitEachGesture }` 第一帧置位，`userScrollEnabled=false` 立刻生效；松手/取消减一。布尔下 A、B 同锁 → A 先解锁 → B 还在拖但 pager 已能滑。
- **效果**｜搜索框内左右拖文字不翻页；日历横滑周不翻页；两组件同时被按住也不互相解锁。
- **参考**｜本项目 `SearchGestureLock.kt`、`MainPager.kt:84`、`CalendarCard.kt:135-171/684` [码]。
- **成本/风险**｜S｜风险：忘减一会把 pager 永久锁死，必须 `try/finally` 或 `awaitEachGesture` 的 finally 释放。
- **优先级**｜P2

### 12.12 触摸拖动响应与流畅度

- **现状**｜部分。有实机观测：`docs/phone-shared-motion-profile.md`（进/退/暖路径 framestats，并明确「首次进入是本测试第一次，不宣称磁盘冷缓存」）与 `docs/performance-audit.md`（P0：底部胶囊 `drawBackdrop` 每帧捕获全页；列表项离屏重组重播 300ms 争帧）。**没有**可回归的门槛（无基准/无帧率断言）。
- **提升**｜工程侧：把「拖动流畅」从感觉变成 CI 能拦的红线（至少拦住「新增每帧 GPU 分配」这类回归）。
- **原理**｜三层：①Macrobenchmark/BaselineProfile + `FrameTimingMetric` 跑「列表快滑 / dock 拖动 / 详情进出」，阈值 p90 < 16.7ms（60Hz）；②单测层拦「每帧分配」：`GlassSectionCard.kt:98-170` 已按尺寸缓存 RenderEffect chain 并有静止节流，补测试断言滚动中不重建 chain；③跟手感：`DampedDragAnimation` 用 `Animatable + dragAmount` 直接跟手（不经动画队列），已正确。
- **效果**｜用户侧无新界面；回归时 CI 报「详情进出 p90 19.2ms > 16.7ms」并拦下 PR。
- **参考**｜本项目 `docs/performance-audit.md`、`docs/phone-shared-motion-profile.md`、`GlassSectionCard.kt:98-170`、`DampedDragAnimation.kt` [码]。
- **成本/风险**｜S｜风险：Macrobenchmark 需真机与稳定温控，CI 易 flaky；建议仅 nightly 输出报告不断言。
- **优先级**｜P2

## 12.13 本域新增候选（发散）

### 12.13 动效调试开关与慢放

- **现状**｜无 · 新提出｜调 spring 只能改代码重装，`AnimDurationShort` 等常量散落。
- **提升**｜能把全局动效放慢 0.25×–4×，一眼看清「这 300ms 里发生了什么」；把参数调试从改代码变成改设置。
- **原理**｜`NirikoTheme` 提供 `LocalMotionScale: Float`，`NirikoMotionSpecs.*()` 内部按 scale 换算（tween 直接乘时长；spring 需按 `stiffness/scale²` 缩放或提示不支持）；入口放「关于 › 开发者选项」（连点版本号解锁）。
- **效果**｜开启后列表入场变 4 倍慢动作；调试行显示「motionScale = 0.25」。
- **参考**｜本项目 `NirikoMotionSpecs.kt` [码]；系统 `animator_duration_scale` 同类机制 [推断]（`MainActivity.kt:183-190` 已在读）。
- **成本/风险**｜S｜风险：只应在 debug 生效，release 保留会被当成卡顿。｜**优先级** P2
- **为什么是 Niriko**：本项目的动效参数是**自建**的（复刻 M3 字节码常量），没有官方 API 可对照，调参需求比用 `MaterialTheme.motionScheme` 的应用强得多。

### 12.14 dock 动画接入 reduceMotion

- **现状**｜无 · 新提出｜`ui/bottombar` 4 文件 0 处守卫；`LiquidBottomTabs.kt:148/164`、`LiquidVerticalDock.kt:149/165` 的 `spring(1f, 300f, 0.5f)`、指示器 `animateToValue`、`DampedDragAnimation` 五条 spring 全部无条件播放。
- **提升**｜开启「减少动态效果」的晕动症用户不再看到指示器来回滑；把 12.B.2 中「未覆盖」清零 3 项。
- **原理**｜顶层读 `LocalReduceMotion.current`，为 true 时：tab 切换 `animateToValue` → `snapTo`；按压 `pressProgress` 直接 0/1；指示器宽度变化用 `snap()`。**保留跟手拖动**（手指直接驱动，关掉会变「拖不动」）。
- **效果**｜开关开：点 tab 立即跳过去（无滑行）、按压无缩放；拖动仍跟手。
- **参考**｜本项目 `LiquidBottomTabs.kt`、`DampedDragAnimation.kt`、`RevealOnScroll.kt:42`（`motionEnabled()` 既有模式）[码]。
- **成本/风险**｜S｜风险：只 snap 指示器而 pager 仍滑会出现「指示器先到、页面后到」，须连 pager 一起 snap。｜**优先级** P1
- **为什么是 Niriko**：dock 是本项目最贵也最显眼的动效（整窗 backdrop 折射），也是唯一完全自研、绕过全部既有 guard 的链路。

### 12.15 图表入场动效统一

- **现状**｜无 · 新提出｜`ui/stats/NirikoBarChart.kt` 与 `ui/subject/EpisodeRatingChart.kt` 都调 `CartesianChartHost(chart, modelProducer, modifier, scrollState, ...)`，**未传** `animateIn`/`animationSpec`（grep 0 命中）→ 走 vico 默认 `animateIn = true`、`animationSpec = defaultCartesianDiffAnimationSpec`；因此图表动效既不受 `LocalReduceMotion` 控制，也不走 `NirikoMotionSpecs`。
- **提升**｜用户侧：减少动态效果开时图表直接显示终值，不再从左长出来。工程侧：bar/line/radar 三图口径一致（radar 自绘需同样处理）。
- **原理**｜vico 3.2.0 的 `CartesianChartHost` 有两个旋钮（[码]）：`animationSpec: AnimationSpec<Float>? = defaultCartesianDiffAnimationSpec` 与 `animateIn: Boolean = true`。做法：`animateIn = !reduceMotion`，`animationSpec = if (reduceMotion) null else tween(...)`；`null` 语义 = 跳过差分动画。
- **效果**｜统计页柱状图首次进入 300ms 长出来；减少动态效果开则瞬间呈现；切筛选时柱子平滑过渡（有 spec）或直接替换（null）。
- **参考**｜`patrykandpatrick/vico@3.2.0:vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/CartesianChartHost.kt` [码]；本项目 `NirikoBarChart.kt:14-28`、`EpisodeRatingChart.kt:353-357` [码]。
- **成本/风险**｜S｜风险：`animationSpec=null` 后数据更新会「跳」，需确认与 `CartesianChartModelProducer` 的 rewind/scroll 行为不冲突。｜**优先级** P1
- **为什么是 Niriko**：图表已从自绘迁到 vico，但动效口径还是 vico 默认——「换库后忘接规范」的典型欠账。

### 12.16 共享元素转场的降级路径

- **现状**｜部分。`NirikoMotionSpecs` 的封面转场是 200ms 路径动画（`SUBJECT_COVER_DURATION_MS=200` + `subjectCoverExpand/ContractEasing` + 每 10ms 采样的 `keyframes` bounds）；`SubjectCoverPath.kt:56` 有 `reduced` 分支，但「降级成什么」无明确语义，返回反向路径也没单独定义。
- **提升**｜用户侧：减少动态效果时封面**不飞**，详情封面淡入；工程侧：给「转场降级」统一语义，替代各页 `if (reduceMotion) snap`。
- **原理**｜拆「位移」与「透明度」两条轨道：reduceMotion 时位移取 `snap`（封面直接出现在目标位），透明度保留 `effectsFast`（~150ms 淡入）——「不飞但有交代」，比全 `None` 更不易迷失。实现上 `sharedBounds` 的 `boundsTransform` 返回常量 Rect + `EnterTransition.None`。
- **效果**｜点封面进详情：原地淡成头图（无缩放位移）；返回反向淡出。
- **参考**｜本项目 `NirikoMotionSpecs.kt:29-54`、`SubjectCoverPath.kt:56`、`SubjectReturnGlow.kt:74-96` [码]；`docs/phone-shared-motion-profile.md:149`（「连续可求导 Rect 路径」结论）[码]。
- **成本/风险**｜S｜风险：`sharedBounds` 传常量 Rect 会触发「无动画转场」告警，须显式 `EnterTransition.None`。｜**优先级** P2
- **为什么是 Niriko**：本项目的共享元素是「可用 Navigation seek 的路径动画」（自研 `subjectCoverPathRect`），降级语义必须自定义，不能套 Compose 默认。

### 12.17 手势完成度反馈

- **现状**｜无 · 新提出｜预测性返回只有「到点关闭」，拖动中没有「再拖一点就关」的视觉反馈（搜索页的返回缩放 `backPreview.progress` 是例外，非模态面板没有）。
- **提升**｜用户侧：上滑面板时面板随距离缩小变淡，「拖一半」不再心里没底。工程侧：完成度与判定共用同一公式，不会出现「看起来够了却没关」。
- **原理**｜在双阈值基础上算 `progress = max(distance/72dp, velocity/900).coerceIn(0,1)`（两路取最大，与 OR 判定同构），驱动 alpha `1-0.25p` 与圆角 `lerp(0,28dp,p)`；越 1 锁存，回拖到 0.6 以下才复位（迟滞防抖）。
- **效果**｜上滑拖到一半：面板已轻微缩小变淡；越过阈值瞬间飞出；松手回弹时进度归零。
- **参考**｜本项目 `NirikoNonModalSheet.kt:54-58`、`SubjectSearchScreen.kt:169-174` [码]。
- **成本/风险**｜S｜风险：进度驱动 alpha 会让面板半透明时透出列表，长文本可读性下降。｜**优先级** P2
- **为什么是 Niriko**：本项目面板是**非模态**的（列表仍可滚），完成度反馈是「非模态」与「可撤销」之间的关键信息。

### 12.18 动效预算与帧率回归

- **现状**｜无 · 新提出｜`docs/performance-audit.md` 是静态审查 + 一次性 framestats，没有「每加一个动效多花多少帧」的预算概念。
- **提升**｜评审时能说「这个动效占 0.8ms/帧」，而不是上线后才发现掉帧。
- **原理**｜给每类动效定预算：backdrop 捕获 ≤3ms/帧、uniform 更新 ≤0.5ms、同屏入场动画 ≤3 个（超出排队）；用 Macrobenchmark + `FrameTimingMetric` 把「同屏动画数」作为参数扫 1/3/5/10，输出曲线并写进 `docs/performance-audit.md`。
- **效果**｜评审文档出现「同屏动画数 vs p90 帧耗时」表，超预算的动效在设计阶段被砍。
- **参考**｜本项目 `docs/performance-audit.md`（gfxinfo framestats 方法）、`docs/phone-shared-motion-profile.md` [码]。
- **成本/风险**｜M｜风险：真机差异大（60Hz/120Hz 结论不同），预算表必须标设备。｜**优先级** P2
- **为什么是 Niriko**：本项目动效密度远高于普通清单应用（玻璃 + 路径转场 + 材质 + 粒子）且全为 Compose 自绘，通用预算不适用。

## 12.B 一致性核对矩阵

### 12.B.1 动效参数一致性（谁走 spec、谁是例外）

口径：`grep -c "spring(" app/src/main/java` = 35，`tween(` = 27；`NirikoMotionSpecs.` 引用 = 13；`NirikoAnimation` 旧常量/工厂引用 = 17。

| 场景 | 参数来源 | 位置 | 走 spec？ | 结论 |
| --- | --- | --- | --- | --- |
| 列表项入场（alpha） | `effectsDefault()` | `RevealOnScroll.kt:61` | ✅ | 保留 |
| 列表项入场（scale/offset） | `spatialDefault()` | `RevealOnScroll.kt:66/71` | ✅ | 保留 |
| 按压倾斜 | `spatialFast()` | `PressTilt.kt:146-150` | ✅ | 保留 |
| 设置行展开 | `spatialDefault()` | `settings/SettingsGroup.kt:117` | ✅ | 保留 |
| 封面共享元素 | 自研 200ms 路径 + `keyframes` | `NirikoMotionSpecs.kt:29-54` | 自研（有意） | 保留（可 seek） |
| **筛选面板展开（8 处）** | `spring(stiffness = StiffnessMediumLow)` | `SearchFilterPanel.kt:113-116/329-332` | ❌ | **改 `spatialDefault()`** |
| **iOS 风搜索（2 处）** | `spring(0.8,300)` / `spring(0.7,420)` | `IosStyleSearchComponent.kt:174/584` | ❌ | **改 `spatialDefault()`** |
| **搜索工具栏** | `spring(0.6,400)` | `SearchToolbar.kt:137` | ❌ | **改 `spatialFast()`** |
| 顶部选择器 | `spring(0.8,400)`，reduceMotion 时 `snap` | `VerticalTopSelector.kt:67` | ❌ | 可改 `spatialFast()`（已带 guard） |
| 玻璃卡按压 | `spring(0.5,400)` | `GlassCard.kt:146` | ❌ | 刻意例外（按压要过冲）→ 登记 |
| 玻璃开关 | `spring(0.6,400)` | `GlassControls.kt:81` | ❌ | 刻意例外（轨道跟手） |
| 玻璃滑杆 | `spring(0.45,520)` | `GlassControls.kt:290` | ❌ | 刻意例外（滑块要弹） |
| dock 指示器跟随 | `spring(1f,300f,0.5f)` | `LiquidBottomTabs.kt:148/164`、`LiquidVerticalDock.kt:149/165` | ❌ | 刻意例外（手势动量） |
| dock 按压/缩放 | `spring(0.6/0.7,250)` 等 5 条 | `DampedDragAnimation.kt:32-36` | ❌ | 刻意例外（上游参数，别改） |
| dock 指尖高光 | `spring(0.5,300)` | `InteractiveHighlight.kt:23-24` | ❌ | 刻意例外（上游参数） |
| 非模态面板拖拽 | `spring(0.85, StiffnessMediumLow)` | `NirikoNonModalSheet.kt:149` | ❌ | 刻意例外（回弹手感） |
| 详情页缩放/材质 | `spring(0.5,Medium)` / `spring(0.6,400)` | `SubjectDetailScreen.kt:2004/2095` | ❌ | 材质渐显刻意保留（手调曲线） |
| 图表入场/差分 | vico 默认 | `NirikoBarChart.kt`、`EpisodeRatingChart.kt` | ❌（外部） | **应接（12.15）** |
| 页面转场 | `tween(180/200/300ms)` | `NirikoNavHost.kt:151-175`、`NirikoBottomBar.kt:49` | ❌（有意） | 保留（跟手性优先，见 m3-expressive:108） |

**小结**：该走 spec 没走的共 **11 处**（筛选面板 8、iOS 搜索 2、搜索工具栏 1，另顶部选择器 1 可顺带），这是 12.3 的真实工作量；其余 7 组是**上游参数或手调曲线**，进白名单即可，不要「统一」掉。

### 12.B.2 减少动态效果覆盖矩阵

口径：`grep -rln "reduceMotion|motionEnabled()" app/src/main/java` = **30 个文件**，`LocalReduceMotion.current` 直读 = 21 处。

| 动效点 | 有 guard？ | 证据 | 处理 |
| --- | --- | --- | --- |
| 页面进入/退出转场 | ✅ | `NirikoNavHost.kt:151-175`（`EnterTransition.None`） | 保留 |
| 单栏↔两栏分隔线 | ✅ | `AdaptiveDetailScaffold.kt:203/223`（`snap`） | 保留 |
| 宽屏二级页出现 | ✅ | `NirikoNavSuite.kt:112` | 保留 |
| 列表入场 | ✅ | `RevealOnScroll.kt:42`（`motionEnabled()`） | 保留 |
| 卡片稀有度呼吸/完美高光 | ✅ | `CardRarityMaterial.kt:106/224/260/288` | 保留 |
| 环境倾斜反射 | ✅ | `AmbientTiltMath.kt:48`、`AmbientTiltReflection.kt:115` | 保留 |
| 完成庆祝 | ✅ | `CompletionCelebration.kt:74-106` | 保留 |
| CRT 彩蛋 | ✅ | `CrtOverlay.kt:70-76`、`util/CrtPolicy.kt` | 保留 |
| 逐集完成波纹 | ✅ | `EpisodeCompletionWave.kt:24-31` | 保留 |
| 返回扫光 | ✅ | `SubjectReturnGlow.kt:80-96` | 保留 |
| 按压倾斜 | ✅ | `PressTilt.kt:65/110` | 保留 |
| 封面路径转场 | ✅ | `SubjectCoverPath.kt:56` | 语义待明确（12.16） |
| 骨架扫光 | ✅ | `ShimmerPolicy.kt:26-30`（另看 systemAnimatorOff 与玻璃档） | 保留 |
| 顶部选择器 | ✅ | `VerticalTopSelector.kt:67` | 保留 |
| 搜索页返回缩放 | ✅ | `SubjectSearchScreen.kt:170` | 保留 |
| 非模态面板回弹 | ✅ | `NirikoNonModalSheet.kt` | 保留 |
| 设置页分组动画 | ✅ | `SettingsGroup.kt`、`AdaptiveSettingsPane.kt`、`AppearanceSettingsScreen.kt` | 保留 |
| **dock：指示器滑行** | ❌ | `LiquidBottomTabs.kt:148/164`、`LiquidVerticalDock.kt:149/165` | **12.14 补** |
| **dock：按压缩放/挤压** | ❌ | `DampedDragAnimation.kt:32-36` | **12.14 补** |
| **dock：指尖高光** | ❌ | `InteractiveHighlight.kt:23-24` | **12.14 补** |
| **玻璃卡按压缩放** | ❌ | `GlassCard.kt:146` | 补（只缩短不过冲） |
| **玻璃开关/滑杆** | ❌ | `GlassControls.kt:81/290` | 补（同上） |
| **图表入场/差分** | ❌ | vico 默认 `animateIn=true` | **12.15 补** |
| 顶部渐隐 | n/a | `TopContentAlphaMask`（滚动映射，非时间动效） | 不需要 |
| 预测性返回进度 | n/a | 系统驱动 | 不需要 |

**小结**：真正缺 guard 的 **6 个点**（dock 三项、玻璃两项、图表一项），其中 dock 三项占「用户最常触发的动效」约 80%，图表只需一行参数。**不要**给 dock 的跟手拖动加 guard——那是手指直接驱动，关掉就变「拖不动」。

## 12.A 本域新增参考库

| 仓库 | 许可 | star | 语言 | 为何与本项目相关 | 已验证文件 |
| --- | --- | --- | --- | --- | --- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Apache-2.0 | 4194（截至 2026-10，Lead 核对；默认分支 `kmp`） | Kotlin / CMP | dock 全部动效参数与 gel-press 范式上游（`interactiveHighlight.pressProgress` 驱动等比放大 + 方向性挤压，`lens` 为常量折射）；本项目 `ui/bottombar/utils/*` 的来源 | `Kyant0/AndroidLiquidGlass@1.0.6:catalog/src/main/java/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt`、`…/LiquidBottomTab.kt`、`…/LiquidButton.kt`、`…/LiquidToggle.kt`、`…/LiquidSlider.kt`、`Kyant0/AndroidLiquidGlass@1.0.6:catalog/src/main/java/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt`、`…/InteractiveHighlight.kt`、`…/DragGestureInspector.kt`、`Kyant0/AndroidLiquidGlass@kmp:app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidButton.kt`、`…/LiquidToggle.kt` |
| [patrykandpatrick/vico](https://github.com/patrykandpatrick/vico) | Apache-2.0（`libs.versions.toml` 注释口径） | 未取到（shields 超时） | Kotlin / CMP | 本项目图表已用 vico 3.2.0；`CartesianChartHost` 的 `animateIn`/`animationSpec` 是「图表动效是否受 reduceMotion 约束」的唯一开关 | `vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/CartesianChartHost.kt` |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | Apache-2.0 | 2.6k（截至 2026-10，shields） | Kotlin / CMP | 「模糊分档 + 采样策略」的建模方式，可作「降级不靠 if」的参照（本项目不引入） | `haze/src/commonMain/kotlin/dev/chrisbanes/haze/Haze.kt`、`haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffect.kt`、`haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInput.kt` |
| [mihonapp/mihon](https://github.com/mihonapp/mihon) | Apache-2.0 | 24k（截至 2026-10，shields） | Kotlin | 设置跳转 + 目标高亮闪烁，12.17 的「高亮找到它」需求可借 | `app/src/main/java/eu/kanade/presentation/more/settings/screen/SearchableSettings.kt`、`app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSearchScreen.kt` |
| androidx/compose-samples | Apache-2.0 | — | Kotlin | **本轮未取得源码**（`data.jsdelivr.com` 与 `gcore` 对该仓库均 404），故未引用其任何证据；参数口径改用 M3 与项目字节码实测 | 无 |
| androidx/androidx（glance 目录） | Apache-2.0 | — | Kotlin | 本轮**未使用**（桌面小组件属 6/11 域）；按 Lead 提示，若需引用 Glance 应走本仓库的 `glance/` 目录，不要虚构仓库 | 未读取 |

## 计数自检

- v1 12 条中 **12.6 已并入 11.7**，本域实际详解 **11 条**（12.1–12.5、12.7–12.12）+ 新增候选 **6 条**（12.13–12.18）= 汇总表 **17 行**。✔
- 一致性矩阵 20 个场景（11 处判「该改」、7 组「刻意例外」、2 处外部/有意）；减少动态效果矩阵 24 个动效点（18 已 guard、6 未 guard、3 n/a；其中「预测性返回」与「顶部渐隐」计 n/a）。✔
- 参考均带 `owner/repo@branch:path` 与证据等级；本文件 `[码]` 为主，`[推断]` 仅用于「系统 animator_duration_scale 机制」与「edge-to-edge 惯例」。✔
- 与 v1 的修正：12.2（错峰**未生效**，v1 记为已实现）、12.1（多一个 0 调用的 `TopFadeOverlay.kt`，且两套 hiddenHeight 口径）、12.10（已实现，含「共 N 条 / 候选池 N 条」）、12.12（已有两份观测文档但无回归门槛）。✔
- 未验证项：`androidx/compose-samples`（CDN 404，已声明）；vico 的 `defaultCartesianDiffAnimationSpec` 内部实现未逐行读，仅引用其默认值声明。✔
