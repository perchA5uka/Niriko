# 方案 03：可中断性、手势交接与物理连续性

> 目标：手势期间 1:1 跟手、松手把速度交给动画（而不是从静止重新起跑）、任何目标值中途变化都不抖不卡、返回手势可跟手可取消。

## G1 · 底栏跟手是「弹簧追手」，不是 1:1（HIGH）

**位置**：app/src/main/java/com/otakup/niriko/ui/bottombar/utils/DampedDragAnimation.kt:94-98（`updateValue` → `valueAnimation.animateTo(targetValue, spring(1f, 1000f))`）；`spring(1f,1000f,vis)` 定义在 `:30`（kyant 上游取值，**不得改数值**）；调用链 LiquidBottomTabs.kt:113-122、:133-144
**问题**：选中片与光晕位置都取 `valueAnimation.value`（LiquidBottomTabs.kt:250、:272-278），而它是「目标追手指」的阻尼跟随 → 跟手有可感知滞后；唯一 1:1 的只有 `panelOffset`（LiquidBottomTabs.kt:141 `offsetAnimation.snapTo(...)`）。
**改法**：跟手阶段改为直接映射（保留 `spring(1f,1000f,…)` 用于**松手吸附**）：
```kotlin
// DampedDragAnimation.updateValue（:94-98）
fun updateValue(targetValue: Float) {
    if (targetValue == this.targetValue) return
    this.targetValue = targetValue
    animationScope.launch { value.snapTo(targetValue) }   // 跟手：1:1，不再追
}
```
松手吸附仍在 `animateToValue` 里用 `valueAnimationSpec`（:30 的弹簧）→ 观感从「一直被拖着的橡皮筋」变成「手指跟得住、松手有惯性收束」。
**验收**：拖动指示器时选中片与指尖位移一致（无可见滞后）；松手仍有一次干净的吸附；kyant 上游弹簧常量零改动。

## G2 · 松手速度被丢弃/错源（HIGH）

**位置**：DampedDragAnimation.kt:115-118（`velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))` → 只喂 `velocityAnimation`）、:71（`press()` 里 `velocityTracker.resetTracking()` 把速度清零）、:97 与 :106（两处 `animateTo` 都没有 initialVelocity）；全工程 `initialVelocity` **0 命中**
**问题**：速度采样输入是**被跟随的动画值**的运动，不是手指；输出的速度只用于形变（LiquidBottomTabs.kt:275-277 挤压），位移层面永不接收初速 → 松手没有动量交接。
**改法**
1. 在手指层采速度：app/src/main/java/com/otakup/niriko/ui/bottombar/utils/DragGestureInspector.kt:35-55 的 `awaitPointerEventScope` 循环里 `velocityTracker.addPosition(change.uptimeMillis, change.position)`，在 up 时算出 `velocityX` 并传给 `onDragStopped(velocityX)`。
2. `DampedDragAnimation` 增加参数：`fun animateToValue(targetValue: Float, initialVelocity: Float = 0f)`，内部：
```kotlin
value.animateTo(targetValue, valueAnimationSpec, initialVelocity = initialVelocity)
```
3. 宿主传入：LiquidBottomTabs.kt:128 与 LiquidVerticalDock.kt 同构处、以及 `snapshotFlow { currentIndex }.drop(1).collectLatest { ... }`（LiquidBottomTabs.kt:154-158）——外部翻页那条**传 0**（不是用户手势）。
4. 顺序修正：`animateToValue` 里先读速度再 `press()`，或让 `press()` 不在松手路径上无条件 `resetTracking()`（把 reset 放到吸附动画结束之后）。
**验收**：快速甩动底栏后指示器带惯性越过多一格；慢速拖动到一半松手回到最近格（无越格）；外部 Pager 翻页时行为与现状一致。

## G3 · `MutatorMutex` 空转 + press/release 可重入（HIGH）

**位置**：DampedDragAnimation.kt:101-112
**现状**：`mutatorMutex.mutate { press(); ...; launch { valueAnimation.animateTo(...) }; ...; release() }` —— 块内只有 `launch`、无挂起点，且 `launch` 的接收者是外层 `animationScope` → `mutate` 立即返回，**互斥锁没有覆盖任何动画**；两路 `animateToValue` 并发时 `press()`/`release()` 会重入，可能出现「提前 unpressed」或「按下态一直亮着」。
**改法**：把所有挂起调用放进**同一个** `mutate` 协程：
```kotlin
fun animateToValue(targetValue: Float, initialVelocity: Float = 0f) {
    animationScope.launch {
        mutatorMutex.mutate {
            press()
            value.animateTo(targetValue, valueAnimationSpec, initialVelocity)
            release()
        }
    }
}
```
并确认 `release()` 内部不再 `launch` 出与本锁无关的协程（当前 :79-92 的 `withFrameNanos`/snapshotFlow 等待见 G4）。
**验收**：连点底栏 tab、快速来回滑 Pager 时不会有「一直按下的高光」；连续两次程序化切页不产生 press/release 交错（可用单元测试对 `DampedDragAnimation` 的状态机做覆盖）。

## G4 · release 的「等值才回弹」会让按压态卡住（HIGH）

**位置**：DampedDragAnimation.kt:79-87
**现状**
```kotlin
if (value != targetValue) {
    snapshotFlow { valueAnimation.value }
        .filter { abs(it - valueAnimation.targetValue) < threshold }
        .first()
}
// 之后才播 pressProgress / scaleX / scaleY 回弹
```
**问题**：按压反馈的回收被推迟到指示器「几乎到位」；目标值频繁变化（连点、连续翻页）时 `first()` 长期挂起 → 放大 + 高光 + lens/highlight/shadow 一直停在按下态。
**改法**：按压反馈与位移解耦 —— 撤销「等值」门：`release()` 立即执行 `pressProgress.animateTo(0f, pressProgressAnimationSpec)` 与 scale 回弹；若确实需要「位移到位后再收高光」，把等待改成有上限的 `withTimeoutOrNull(240)` 并允许目标变化时重启。
**验收**：连点 3 个 tab、连续翻页 5 次后，底栏无残留按下态高光；正常单次点击的按压-回弹节奏不变（可录屏对比）。

## G5 · 非模态 sheet 松手没有初速（HIGH）

**位置**：app/src/main/java/com/otakup/niriko/ui/components/NirikoNonModalSheet.kt:135-143
**现状**：`velocity` 只喂 `shouldDismissSheet`（阈值 `SHEET_DISMISS_DISTANCE_DP=72f``:`50、`SHEET_DISMISS_VELOCITY_DP=900f``:`53），两个分支的 `animateTo` 都不带 `initialVelocity` → 快速甩动松手后面板从静止重新起跑。
**改法**
```kotlin
val v = velocity            // 来自 onDragStopped，单位 px/s（注意与 900dp/s 阈值同一单位系）
if (shouldDismissSheet(drag.value, velocity, ...)) {
    drag.animateTo(panelHeightPx + margin, specs.spatialFast(), initialVelocity = v.coerceAtLeast(0f))
    onDismiss()
} else {
    drag.animateTo(0f, spring(0.85f, Spring.StiffnessMediumLow), initialVelocity = v)
}
```
（`spring(0.85f, Spring.StiffnessMediumLow)` 与 ui/animation/NirikoAnimation.kt:53-56 的 `panelSpring()` 数值完全相同 —— 直接改用 `panelSpring()`，见 04 §B/T1。）
**验收**：向上轻拖 + 快速甩动 → 面板带明显惯性飞出；向下轻甩 + 慢速拖到一半 → 回弹（不越阈值）。

## G6 · 返回键关闭 sheet 没有退场动画（HIGH）

**位置**：app/src/main/java/com/otakup/niriko/ui/stats/CalendarDaySheet.kt:67（`BackHandler(enabled = true, onBack = onDismiss)`）、app/src/main/java/com/otakup/niriko/ui/stats/StatsScreen.kt:127-135（`selectedDay?.let { CalendarDaySheet(...) }`）；面板本体只有入场：NirikoNonModalSheet.kt:91、:98-104
**问题**：返回键把 `selectedDay` 置空 → sheet 立即离开组合；只有拖拽关闭因为先 `drag.animateTo(flyOut)` 再 `onDismiss()` 才有退出。全工程 14 处 `AnimatedVisibility` 无一包住它。
**改法（两步都做）**
1. 面板本体：`NirikoNonModalSheet` 内部增加退场分支（`onDismissRequest` 统一入口：先把 `entry` 从 0 反向 `animateTo(1f, specs.spatialDefault())` 再回调 `onDismiss()`），这样无论谁触发关闭都会走同一段退出。
2. 宿主：用 `AnimatedVisibility` 包住面板并保留「最后一次非空」的日期：
```kotlin
var lastDay by remember { mutableStateOf<LocalDate?>(null) }
selectedDay?.let { lastDay = it }
AnimatedVisibility(visible = selectedDay != null, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
    lastDay?.let { CalendarDaySheet(date = it, onDismiss = { selectedDay = null }) }
}
```
3. 与预测性返回合并（见 01 §A-4(2)）：`PredictiveBackHandler` 的 `progress` 直接驱动面板位移，取消时回弹。
**验收**：返回键关闭有可见下滑 + 淡出（不再是瞬移）；拖拽关闭与返回关闭走同一段退出动画；关闭过程中再次打开不会出现幽灵面板。

## G7 · 二级页 pop 过渡与前进同向（MEDIUM）

**位置**：app/src/main/java/com/otakup/niriko/navigation/NirikoNavHost.kt:99-100 等 16 个目标页 / 32 个 lambda（:126 :146 :167 :208 :232 :258 :281 :307 :326 :344 :362 :380 :400 :422 :440）
**问题**：未声明 `popEnterTransition`/`popExitTransition` → 默认取 enter/exit（SharedAxis Z 同向 0.92 缩放），返回看起来像又前进了一次；被返回的页重播入场；ui/animation/NirikoAnimation.kt:81-96 的 `enterFromRight/exitToRight` 全工程零引用。
**改法**：见 [01-predictive-back-android-mdc-followups.md](01-predictive-back-android-mdc-followups.md) §A-3（新增 `popEnterSharedAxisZ`/`popExitSharedAxisZ` 并把 16 处补上；或改用现成的 `enterFromRight/exitToRight`）。
**验收**：前进与后退能一眼区分方向（Z：前进缩小进入、后退放大退出 + 被返回页不重播入场）；predictive back 跟手期间过渡与松手后的过渡连续。

## G8 · 底栏手势不 consume、无独占（MEDIUM）

**位置**：app/src/main/java/com/otakup/niriko/ui/bottombar/utils/DragGestureInspector.kt:21-25、:35-55
**问题**：`down` 当帧即 `onDragStart`（无 touch slop、无长按判定），全程 `change.consume()` 0 次；底栏与 `Scaffold` 是同一 `Box` 的**兄弟**（MainActivity.kt:296 的 Box 内 :319 `Scaffold` 与 :371 `if (showNavSuite) NirikoNavSuite`）→ 命中测试对重叠兄弟都派发，在底栏上竖滑会边拖指示器边滚列表、横滑会边拖指示器边翻页，直到某一方 consume（下层 consume 后 `:46` 的 `if (change.isConsumed) return null` 让底栏取消）。
**改法**
1. 加 slop：用 `awaitTouchSlopOrCancellation(pointerId) { change, over -> change.consume(); ... }`（或 `awaitFirstDown` + 自行累计 `viewConfiguration.touchSlop`），超过 slop 后再 `onDragStart`。
2. 决定独占后立即 `change.consume()`（横滑归底栏、竖滑让给列表——按 `abs(dx) > abs(dy)` 判定）。
3. `onDragCancel` 必须回到中性态（当前依赖 `onDragStopped`/`release()`，取消路径要显式覆盖）。
**验收**：在底栏上竖滑 → 只滚列表，指示器不动、不高亮；横滑 → 只拖指示器，Pager 不翻页；点击 tab 仍即时响应（slop 不应吞掉点击）。

## G9 · GlassSlider 拖动不 consume（MEDIUM）

**位置**：app/src/main/java/com/otakup/niriko/ui/components/GlassControls.kt:150-152（`detectDragGestures { change, _ -> onValueChange(offsetToValue(...)) }`）
**改法**：在 `detectDragGestures` 的 `onDragStart`/`onDrag` 中 `change.consume()`，并用 `awaitFirstDown(requireUnconsumed = false)` 保证起点能接管。
**验收**：放在可滚动容器里的滑块拖动不再被父滚动接管；点击轨道跳值的既有行为不变。

## G10 · TouchGlow 按住后滚走仍保持按压态（MEDIUM）

**位置**：app/src/main/java/com/otakup/niriko/ui/components/TouchGlow.kt:40-53（down 当帧 `progress.animateTo(1f, spring(0.5f, 300f))`，只读事件不 consume；仅抬手才 `animateTo(0f, ...)`）
**改法**：加 slop 语义 —— 手指移动超过 `viewConfiguration.touchSlop` 即视为「开始滚动」并立刻 `progress.animateTo(0f, springSpec)`；或在检测到父层消费/滚动时取消按压。注意 ui/components/TouchGlow.kt:23-26 注释声明「不做小范围拖拽平移、只读事件不消费」是**刻意设计**——本项只改「滚动后残留按压态」，不改「是否 consume」。
**验收**：按住卡片立刻滚动列表 → 卡片缩放立即回弹（不再等到抬手）。

## G11 · sheet 每 delta 起协程 + `.offset{}` 每帧重排玻璃面板（MEDIUM）

**位置**：NirikoNonModalSheet.kt:112-114（`.offset { IntOffset(0, (panelHeightPx * entry.value + drag.value).roundToInt()) }`）、:132-133（`rememberDraggableState { delta -> scope.launch { drag.snapTo(...) } }`）；面板是 `appleGlassCard` 真玻璃
**问题**：`.offset{}` lambda 确实避开了重组（对），但每帧重新**测量/摆放**整块 0.62 屏高的玻璃面板并让 backdrop 重新采样；每个 drag delta 新起一个协程做 `snapTo`，位移不保证同帧生效。
**改法**
1. 位移改到图层：`Modifier.graphicsLayer { translationY = panelHeightPx * entry.value + dragOffset }`（状态读取在 lambda 内）。
2. 拖动期不要用 `Animatable`+`launch`：用 `val dragOffset = remember { mutableFloatStateOf(0f) }`，`rememberDraggableState { delta -> dragOffset.floatValue = (dragOffset.floatValue + delta).coerceAtLeast(0f) }`；松手时再 `Animatable(initialValue = dragOffset.floatValue).animateTo(target, spec, initialVelocity = v)`，动画帧把结果写回 `dragOffset`（或直接用 `Animatable` 但只在松手后使用）。
**验收**：拖动面板时 Layout 阶段不再每帧重排（Layout Inspector 的 layout 计数不随手势增长）；高采样率设备上手指与面板无可见滞后（需真机确认）。

## G12 · iOS 搜索组件：弹簧 + 非动画拼接导致的位置跳变（MEDIUM）

**位置**：IosStyleSearchComponent.kt:199-207（`anchorX = animateDpAsState(cardSpring)`）与 :210（`dragOffsetX` 非动画三元式）；:409-412（Canvas 绿条随 phase 一帧消失）
**问题**：phase 一变 `dragOffsetX` **立刻归 0**，而 `anchorX` 还在弹簧中途 → 松手/提交瞬间锚点位置跳变；绿色轨道同帧消失（同一个 `progress` 既做 1:1 跟手又做显隐）。
**改法**：`dragOffsetX` 改为 `Animatable`：DRAGGING 期间 `snapTo(-maxTravel * progress)`，非 DRAGGING 时 `animateTo(0f, cardSpring)`（与 `anchorX` 同族）；绿条的显隐改成「alpha 随相位淡出」而不是 `return@Canvas`。
**验收**：左划松手不再有位置跳变；绿条消失有短淡出（不是闪断）。

## G13 · 需要真机确认的两条（不改代码，仅登记）

| 位置 | 观察点 |
|---|---|
| app/src/main/java/com/otakup/niriko/ui/common/ImageViewer.kt:93-95、:121-124 | `isCurrentPageZoomed` 由 telephoto 的 `zoomFraction` 在**组合期**反推并绑到 `userScrollEnabled` → 手势进行中该值翻转会让「翻页 vs 缩放」的胜负随当帧数值改变 |
| app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt:1742-1746 / ui/adaptive/NirikoNavSuite.kt:104-113 / ui/adaptive/AdaptiveDetailScaffold.kt:224-233 | `LaunchedEffect(key) { snapTo(起手); animateTo(目标) }` 模式：key 变化即从头重播（连点星、旋转/折叠、窗格变化）。低优先，但如果 key 在连续交互中会抖，应改为「起手只在首次」+ 目标值变化只改 `animateTo` 目标 |


---
**基线**：git HEAD `97ce7df`（工作树有 20+ 未提交改动）。审计日期 2026-10-01。约束：**不改动任何源码**。
**工程口径**：com.otakup.niriko / Kotlin 2.3.10 / AGP 8.13.2 / composeBom 2025.12.01（Compose UI 1.10.x、material3 1.4.0）/ navigationCompose 2.8.5 / activityCompose 1.9.3 / targetSdk 34 / minSdk 26。
**验收基线**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest`（P0 批次时点为 78 个测试类 / 696 用例 / 0 失败）；涉及帧预算的项另需真机 `adb shell dumpsys gfxinfo com.otakup.niriko framestats`。
**不得再报的刻意取舍**详见 [README.md](README.md) 末尾清单。