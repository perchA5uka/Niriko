# 方案 02：动效阶段与帧预算（把每帧的「重组 + 重测」换成「只重绘」）

> 判据：动画值在 composition 读 → 每帧重组；在 `Modifier.offset{}`/`layout{}` 读 → 每帧 measure/layout；在 `graphicsLayer{}`/`drawBehind`/`Canvas` 读 → 只重绘。本方案只动「读取阶段」，尽量不改观感数值。

## P1 · iOS 搜索组件：手势帧重组 + 重测玻璃卡（HIGH）

**位置**：app/src/main/java/com/otakup/niriko/ui/common/IosStyleSearchComponent.kt:210、:549、:258-259、:154-167、:199-222
**现状**
```kotlin
// :210 组合期读 VM 的手势进度
val dragOffsetX = if (phase == SearchPhase.DRAGGING) -maxTravelDp * progress else 0.dp
// :549 布局级位移
.offset(x = anchorX + dragOffsetX, y = anchorY)
// :258-259 尺寸锁
.requiredWidth(cardW).requiredHeight(cardH)
```
**问题**：`progress` 来自 SearchViewModel（SearchViewModel.kt:40/:105 每个手势事件写），`:549` 的 `offset` 又在组合期读 `anchorX + dragOffsetX` → 左划跟手期间**每帧重组整个组件 + 重新测量含 appleGlassCard 真玻璃的子树**；`:258-259` 让 5 个 `animateDpAsState` 的每个值都变成一次整卡 measure/layout。文件内 :106/:142 的注释自称「不改布局尺寸、无重测卡顿」，与该路径矛盾。
**改法**
1. 位移改为图层阶段读取（不改变视觉，只改阶段）：`:549` 的 `.offset(x = ..., y = ...)` → `.graphicsLayer { translationX = (anchorX + dragOffsetX).toPx(); translationY = anchorY.toPx() }`（在 lambda 内读这三个值 → 只失效图层）。
2. `dragOffsetX` 去掉三元表达式，改用 `Animatable(0f)`：DRAGGING 期间 `snapTo(-maxTravelDp.toPx() * progress)`（在 `Modifier.graphicsLayer` lambda 内读 `value`），离开 DRAGGING 时 `animateTo(0f, cardSpring)` —— 顺带修掉 03 §G12 的「松手一帧跳回」。
3. 尺寸动画（`cardW`/`cardH`）保留：它只在 phase 切换时跑（非手势帧），但把 `phase` 的读取也限制在 `LaunchedEffect(phase)` 内驱动，避免手势帧间接触发。
**验收**：Layout Inspector 的 recomposition 计数在左划跟手期间不再逐帧增长；真机 `framestats` 对比左划的 janky frame 比例（需真机确认）。

## P2 · 底栏滚动隐藏：滚动帧重组底栏 + 布局级位移（HIGH）

**位置**：app/src/main/java/com/otakup/niriko/navigation/NirikoBottomBar.kt:46、:47-49、:54、:55；app/src/main/java/com/otakup/niriko/ui/adaptive/NirikoNavSuite.kt:90、:96-101、:118、:119-122；写入端 app/src/main/java/com/otakup/niriko/ui/common/BottomBarScroll.kt:35-42
**现状**
```kotlin
// BottomBarScroll.kt:39 每个滚动帧写共享状态
fraction.floatValue = (acc / HIDE_PX).coerceIn(0f, 1f)
// NirikoBottomBar.kt:46 组合期读
val hideFraction = bottomBarHideFraction()
// :54 布局级位移，且捕获的是「重组时的 Float」而不是 State
.offset { IntOffset(0, (8 * hideFraction).dp.toPx().roundToInt()) }
```
**问题**：任何列表滚动都会让底栏（LiquidBottomTabs：双层玻璃 + content 组合两遍）**每帧重组**；`:54` 又叠加每帧 layout。对照健康写法：app/src/main/java/com/otakup/niriko/ui/adaptive/AdaptiveInsets.kt:30-56 用 `layout {}` lambda 延迟读同一个值。
**改法**
1. `NirikoBottomBar.kt`：删掉 `:46` 的组合期读取，改为在 `Modifier.graphicsLayer { }` 内**直接读 `LocalBottomBarHideFraction.current.floatValue`**：
```kotlin
val fractionState = LocalBottomBarHideFraction.current   // :46 只取 State 引用，不读 .floatValue
Box(modifier = Modifier
    .graphicsLayer {
        translationY = 8.dp.toPx() * fractionState.floatValue
        alpha = barAlpha.value                    // Animatable，见第 2 步
    }
) { ... }
```
2. `barAlpha`：把 `animateFloatAsState(targetValue = 1f - hideFraction*0.15f, tween(180, AnimEasingDefault))`（:47-49，180ms 属**刻意保留**）换成 `remember { Animatable(1f) }` + `snapshotFlow { hideFraction }.collect { barAlpha.animateTo(1f - it*0.15f, tween(180, AnimEasingDefault)) }`；`Animatable.value` 只在 graphicsLayer lambda 内读 → 动画期间也不重组。
3. `NirikoNavSuite.kt` 同构改造：`:118` 的 `.offset{}` 与 `:119-122` 的 `graphicsLayer` 合并为一个 `graphicsLayer`，在 lambda 内读 `fractionState.floatValue` 与 `appearance.value`/`animatedAlpha`（同样把 `animatedAlpha` 改成 Animatable + snapshotFlow）。
**验收**：滚动列表时底栏 recomposition 计数为 0；隐藏/恢复的视觉与逻辑不变（位移 8dp×f、alpha 1-0.15f、dock 的 -24dp×f 与 -16dp 滑入都要保留）。

## P3 · 顶层 Pager 指示器：拖动帧重组 + 每段 measure（HIGH）

**位置**：app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt:124-146（`:130` 组合期读、`:140` `.size(width = ...)`、`:142` 背景 alpha）
**现状**
```kotlin
val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction   // :130 组合期
.size(width = pageIndicatorWidthDp(index, pagePosition).dp, height = 3.dp)          // :140
.background(primary.copy(alpha = 0.28f + 0.72f * weight))                           // :142
```
**问题**：Pager 跟手拖动时每一帧重组该组件并对 4-5 个节点重新 measure/layout。跟手的意图（:122 注释「不做动画、逐帧跟随手指」）是对的，错的是阶段。
**改法**：每段固定为最大宽度，宽度与颜色都在 **draw 阶段**画：
```kotlin
// 每段
Box(Modifier
    .width(PAGE_INDICATOR_MAX_WIDTH_DP.dp)      // 固定，不再随 pagePosition 变
    .height(3.dp)
    .drawWithContent {
        drawContent()
        val w = pageIndicatorWidthDp(index, pagePosition).dp.toPx()      // draw 阶段读
        val a = 0.28f + 0.72f * pageIndicatorWeight(index, pagePosition)
        drawRoundRect(color = primary.copy(alpha = a),
                      size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
    })
```
若必须保留「背景填充」的合成语义，可退一步只把 `pagePosition` 读取放进 `Modifier.drawBehind {}`（同样只失效绘制）。
**验收**：Pager 拖动期间指示器 recomposition 计数为 0；宽度/透明度曲线与现状一致（纯函数 `pageIndicatorWeight`/`pageIndicatorWidthDp` 已存在，可直接复用并有单测价值）。

## P4 · 玻璃控件：开关与滑块逐帧重组（HIGH）

**位置**：app/src/main/java/com/otakup/niriko/ui/components/GlassControls.kt:66-71（thumb 位移 spring 0.6/400）、:73-76（轨道色）、:96（`.offset(x = thumbOffset)`）、:157/:169/:186（滑块 `fraction`→`fillMaxWidth` + `.offset`）
**改法**
1. 开关 thumb：`.offset(x = thumbOffset)` → `.graphicsLayer { translationX = thumbOffsetPx }`（值只在 lambda 内读；`animateDpAsState` 的 target 变化仍可保留 spring 0.6/400）。
2. 轨道色：`.background(trackColor)` → `.drawBehind { drawRoundRect(color = trackColor, ...) }`（`animateColorAsState` 的读取退到 draw 阶段）。
3. 滑块：把「进度」从布局宽度改为图层绘制：轨道固定宽 + `drawBehind` 画已填充段与 thumb 位置（或 thumb 用 `.graphicsLayer { translationX = ... }`）。若坚持布局宽度，至少把 `fraction` 的读取移入 `Modifier.layout {}`（只重测不重组）。
**验收**：拖动滑块/切换开关时 recomposition 计数为 0（仅重绘）；开关起点/终点位置与震动感不变。

## P5 · shimmer 并发上限（MEDIUM）

**位置**：app/src/main/java/com/otakup/niriko/ui/components/StateComponents.kt:75-89（唯一入口 `Modifier.skeletonShimmer()`，已由 ShimmerPolicy 正确降级）；调用点 ui/stats/StatsSkeleton.kt:41-107（1+2+7+42+4+4 ≈ 60 个块）、ui/search/DiscoverPosterPane.kt:163-173（3 列 × 4 = 12 张卡 = 24 个）、ui/search/SearchResultsPane.kt:232-237、ui/search/TrendingSection.kt:222-230、ui/library/PosterGridCard.kt:184/:203、ui/screens/Screens.kt:648、ui/subject/SubjectDetailScreen.kt:719
**问题**：降级裁决没问题，缺的是**数量上限**——统计页加载态同一帧有约 60 个独立无限扫光（每个块自己一个 `rememberShimmer`），Discover 首屏 24 个，且全部 `ShimmerBounds.Window`。
**改法（保持调用点不变）**：把「每个块一个动画」改成「每个骨架屏一个动画 + 共享 brush」：
1. 在 StateComponents.kt 新增 `object ShimmerGroup`：一个 `Animatable`/`rememberInfiniteTransition` 产出 0f..1f 相位，包成 `CompositionLocal`（如 `LocalShimmerPhase`）。
2. 新增 `@Composable fun SkeletonGroup(content: @Composable () -> Unit)`：内部创建唯一相位并 `CompositionLocalProvider`；`Modifier.skeletonShimmer()` 改为读取该相位后 `drawBehind { drawRect(brush = phaseBrush) }`（读值在 draw/drawBehind → 只重绘），未提供 group 时退化为现有单节点 shimmer（兼容）。
3. 在 8 个骨架屏容器（StatsSkeleton / DiscoverPosterPane 的骨架分支 / SearchResultsPane / TrendingSection / LibrarySkeleton / Screens.kt:648 / SubjectDetailScreen.kt:719 / PosterGridCard 骨架）外层套一次 `SkeletonGroup { ... }`。
**验收**：统计页加载态帧时间可测地下降（真机 framestats，需真机确认）；视觉扫光相位在此前是「各块同时起（相位不同步）」——共享相位后**必须确认观感可接受**（若要求错落，可给 group 一个 per-block 相位偏移参数，但仍只有 1 个动画驱动）。

## P6 · RevealOnScroll 错峰参数完全失效（MEDIUM）

**位置**：app/src/main/java/com/otakup/niriko/ui/animation/RevealOnScroll.kt:30-31（文档）、:43-46、:48、:69-71（空转的 LaunchedEffect）、:75-80、:81-86；调用点 ui/screens/Screens.kt:248、ui/search/TrendingSection.kt:179、ui/search/DiscoverPosterPane.kt:126
**现状**：`staggerIndex` 只做 `LaunchedEffect` 的 key，`delayMillis` 全仓**没有任何调用点**传值；`entered` 只由 `:75` 的 `onGloballyPositioned` 在 `!entered` 守卫下翻转一次 —— 首屏已在视口内的项由同一次布局回调同时入场，实际是「全部一起播 300ms」，错峰为 0（文件头注释却写「staggerIndex × 50ms」）。
**改法（二选一，推荐 B）**
- A（诚实最小化）：删除 `staggerIndex`/`delayMillis` 参数，改注释；3 个调用点同步删参。
- B（实现错峰，带上限）：`:69-71` 改成真正驱动 `entered`：
```kotlin
LaunchedEffect(staggerIndex, entered) {
    if (entered) return@LaunchedEffect
    val delayMs = (delayMillis.takeIf { it > 0 } ?: (staggerIndex.coerceIn(0, 6) * 40))
    delay(delayMs.toLong())
    entered = true
}
```
即：`:75` 的 `onGloballyPositioned` 只把「进入视口」写入一个 `inViewport` 状态，由上面的 effect 延迟翻转 `entered`；上限 6×40ms=240ms（避免长列表末尾等太久）。注意 docs/performance-audit.md 已记录「Lazy 项离屏销毁滚回会重播」——`rememberSaveable` 保持现状即可，若要彻底消除重播需把 `entered` 提到 Lazy 项之外（本次不强制）。
**验收**：首屏 5-6 个卡片能看到明确的 40ms 级错峰且总延迟 ≤240ms；滚动到底再滚回的重播行为与现状一致（不新增）。

## P7 · 局部重绘成本（LOW，逐条独立可做）

| 位置 | 现状 | 改法 |
|---|---|---|
| app/src/main/java/com/otakup/niriko/ui/stats/TimelineSection.kt:113-130 | 在每张卡的作用域读 `pagerState.currentPageOffsetFraction` 后写 `graphicsLayer{ scaleX; alpha }` → 拖动时每帧重组每张已组合的卡（仅失效图层，不重测） | 把 offset 读取移入 `graphicsLayer` lambda（用 `derivedStateOf` 或直接读 `pagerState`：lambda 内读 State 只失效图层） |
| app/src/main/java/com/otakup/niriko/ui/components/InnerShadow.kt:98-142 | 每次重绘 `layer.record{}` + `BlurEffect(radius, radius, TileMode.Decal)` 离屏模糊 | radius/尺寸未变时缓存图层（`remember` + 只在参数变化时 record），或改用 `drawWithCache` |
| app/src/main/java/com/otakup/niriko/ui/bottombar/LiquidBottomTabs.kt:207-241 / LiquidVerticalDock.kt:203-230 | 为折射/镜像把同一份 content 组合渲染两遍，每层各带 `blur + lens` backdrop 采样 | 保持视觉不变下降低采样：`lens` 半径/模糊半径按「显示尺寸」预算，或对非选中片关闭第二遍渲染（**触碰 kyant 上游实现，需单独批准**） |
| app/src/main/java/com/otakup/niriko/ui/components/GlassCard.kt:125-133 | `remember(this, isDark) { Brush.verticalGradient(...) }`：key 含 Modifier receiver → modifier 链变化即重建 Brush | 把 key 收敛为真正影响渐变的参数（颜色/尺寸），或用 `drawWithCache` |
| app/src/main/java/com/otakup/niriko/ui/settings/SettingsGroup.kt:124-134 | 按压 morph 期间每帧重组该行（`animateDpAsState` → `RoundedCornerShape`） | 半径动画本就属布局/形状；改为 `graphicsLayer{ shape = ...; clip = true }`（Compose 支持 `GraphicsLayer.shape` 的 draw 阶段裁剪）或接受现状（**低优先**） |


---
**基线**：git HEAD `97ce7df`（工作树有 20+ 未提交改动）。审计日期 2026-10-01。约束：**不改动任何源码**。
**工程口径**：com.otakup.niriko / Kotlin 2.3.10 / AGP 8.13.2 / composeBom 2025.12.01（Compose UI 1.10.x、material3 1.4.0）/ navigationCompose 2.8.5 / activityCompose 1.9.3 / targetSdk 34 / minSdk 26。
**验收基线**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest`（P0 批次时点为 78 个测试类 / 696 用例 / 0 失败）；涉及帧预算的项另需真机 `adb shell dumpsys gfxinfo com.otakup.niriko framestats`。
**不得再报的刻意取舍**详见 [README.md](README.md) 末尾清单。