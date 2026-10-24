# 交付物 ①：AOSP / AndroidX / Material Components for Android 可跟进清单

> 结论先行：对本工程**唯一具备「小功能、大收益」形态**的可跟进项是**预测性返回（predictive back）**——它的依赖早已在 classpath 上（activity-compose 1.9.3 已含 `PredictiveBackHandler`，navigation-compose 2.8.5 的 `NavHost` 已内建 predictive-back 通道），当前只差一个 manifest 开关与两处 `BackHandler` 的替换。

## 0. 一手证据（本机 Gradle 缓存 AAR / 字节码实测，非文档转述）

| 证据 | 内容 |
|---|---|
| `androidx.activity:activity:1.9.3` aar | 含 `androidx/activity/BackEventCompat.class`、`BackEventCompat$SwipeEdge.class` |
| `androidx.activity:activity-compose:1.9.3` aar | 含 `androidx/activity/compose/PredictiveBackHandlerKt.class`、`PredictiveBackHandlerCallback.class`、`LocalOnBackPressedDispatcherOwner.class` |
| `androidx.navigation:navigation-compose:2.8.5` aar → `NavHostKt.class` 常量池 | 含 `androidx/activity/compose/PredictiveBackHandlerKt`、`inPredictiveBack$delegate`、`createPopEnterTransition`、`createPopExitTransition`、`getPopEnterTransition$navigation_compose_release`；同一 aar 无独立 `PredictiveBack*` 类 → 预测性返回是内建在 `NavHost` 里、复用 activity-compose 的 handler |
| `androidx.navigationevent:navigationevent-compose-android:1.1.1` aar（缓存中已存在） | 含 `NavigationEventHandlerKt`（`NavigationEventHandler`）、`NavigationEventState`、`ComposeNavigationEventHandler`、`LocalNavigationEventDispatcherOwner`、`rememberNavigationEventState` → 新一代统一手势事件 API |
| `androidx.compose.animation:animation-android:1.10.x` aar | 含 `AnimateBoundsModifierKt$animateBounds`、`BoundsAnimationModifierNode$approachMeasure`、`SharedTransitionScope$ResizeMode`、`SharedTransitionScope$PlaceholderSize`、`SharedTransitionScope$skipToLookaheadPosition`/`skipToLookaheadSize`、`OnLookaheadMeasured` |
| `androidx.compose.foundation:foundation-android:1.10.x` aar | 含 `androidx/compose/foundation/OverscrollFactory.class`、`AndroidEdgeEffectOverscrollFactory.class`、`StretchOverscrollNode.class`、`GlowOverscrollNode.class` |
| `androidx.compose.ui:ui-android:1.10.x` aar → `HapticFeedbackType$Companion.class` 常量 | `LongPress`、`TextHandleMove`、`VirtualKey`、`KeyboardTap`、`Confirm`、`ContextClick`、`Reject`、`GestureEnd`、`GestureThresholdActivate`、`SegmentTick`、`SegmentFrequentTick`、`ToggleOn`、`ToggleOff` |
| `com.google.android.material`（MDC-Android） | **本机 Gradle 缓存中不存在该构件** → 本工程零 View 依赖（纯 Compose），见 §B 的定位说明 |
| 工程侧现状 | app/src/main/AndroidManifest.xml:57-66 的 `<application>` 无 `android:enableOnBackInvokedCallback`；全仓 `PredictiveBackHandler`/`onBackPressed`/`BackEventCompat` = 0；`BackHandler` 仅 2 处：ui/subject/SubjectSearchScreen.kt:85、ui/stats/CalendarDaySheet.kt:67 |

## §A 预测性返回（P0，用户点名）

### A-1 为什么现在做

- 现状：Android 13+ 设备上返回手势**没有任何 progress 跟手**，动画只在手势提交、pop 真正发生之后才开始；手势中途不能取消。
- **精确规则（AOSP/AndroidX 调研子代理回补；证据：`ParsingPackageUtils.java:2507` `.setOnBackInvokedCallbackEnabled(bool(targetSdk > Build.VERSION_CODES.VANILLA_ICE_CREAM, ...))`，`VANILLA_ICE_CREAM = 35`；拒绝注册点在 `WindowOnBackInvokedDispatcher.java:766-772`）**：`enableOnBackInvokedCallback` 的默认值由 **targetSdk** 推导 —— **targetSdk > 35 才默认 true；34 与 35 都是 false**。未启用时系统会直接拒绝注册 `OnBackInvokedCallback` 并打警告日志。
- **对本工程的结论**：targetSdk 34 → 预测性返回默认关闭、`PredictiveBackHandler` 收不到 progress；**在 manifest 显式写 `"true"` 即可生效，不必先升 targetSdk**（升到 36 只是把默认值变成 true）。Android 13 (API 33) 起该开关可用。系统侧「回桌面/跨任务」的返回动画面向已 opt-in 的应用播放；未 opt-in 的兼容回退是 `windowSwipeToDismiss` 风格的窗口属性，且需 `persist.wm.debug.predictive_back_fallback_window_attribute=1`（`WindowOnBackInvokedDispatcher.java:828-839`）。
- ⚠️ 仍待联网核对：developer.android.com 对应页面的表述与各 Android 版本面向用户的启用时间线（本次环境无法联网，见 README 覆盖缺口 1）。

### A-2 改动点 1：manifest 开关（1 行）

```xml
<!-- app/src/main/AndroidManifest.xml:57-66 的 <application> 内新增 -->
<application
    android:name=".NirikoApplication"
    android:enableOnBackInvokedCallback="true"
    ... >
```
打开后 `NavHost` 的 predictive-back 通道即生效（证据见 §0 `NavHostKt.class` 常量池：`inPredictiveBack` + `createPopEnterTransition`/`createPopExitTransition`）→ 13 条二级路由的返回手势自动变成「跟手进入 pop 过渡」。这一步不需要改 NirikoNavHost.kt:99-100 等 32 个 lambda 的写法。

**子代理回补的关键细节（navigation-compose 2.8.5 源码实测）**：`NavHost` 内部早已 `PredictiveBackHandler(currentBackStack.size > 1)`，**取消时手动倒放动画**（NavHost.kt:632 注释），并处理 `zIndex`（:667）与 `inPredictiveBack` 状态（:513-526）；2.9.0 只是把这段挪到 commonMain（KMP），**零功能变化**。

→ **重要边界**：想让返回方向的动效对齐 `NirikoMotionSpecs`，公开 API `predictivePopEnterTransition`/`predictivePopExitTransition` **要等 navigation-compose 2.10.0+**（NavHost.kt:269-276、427-434、585；DefaultNavTransitions.kt:52/58；ComposeNavigator.kt:132/137；旧 NavHost 重载被标 `@Deprecated`；stable 下限 2.10.2，需连带 navigation-runtime 2.10.x + Compose 1.10.4+）。**本工程 2.8.5 只能吃到 NavHost 默认的返回动效**，因此 §A-3 的 `popEnterTransition`/`popExitTransition`（现有公开参数）仍是当前版本下的正确落点：它作用于普通 pop；predictive 手势期间由 NavHost 自己倒放。

**手势取消的语义（子代理回补）**：progress 从起边 0 走到对边约 1；**手势取消时系统会持续回调直到 progress 动画回 0**（`BackEvent.java:100-120`）→ §A-4 里必须按 progress 反向插值、并在 `finally` 复位，**不能用一次性动画**。另：activity-compose 1.9.3 与 1.11.0 的 `PredictiveBackHandler` **没有任何 `@Experimental` 注解**（1.13.0 才要求 `@OptIn(ExperimentalActivityApi)`），所以本工程可直接使用。

### A-3 改动点 2：给 16 个目标页补 popEnter/popExit（让跟手有正确的反向规格）

现状（navigation/NirikoNavHost.kt:99-100，同型共 16 个目标页 / 32 个 lambda：:126 :146 :167 :208 :232 :258 :281 :307 :326 :344 :362 :380 :400 :422 :440）：
```kotlin
enterTransition = { enterSharedAxisZ(specs) }
exitTransition = { exitSharedAxisZ(specs) }
// 未声明 popEnterTransition / popExitTransition → 默认取 enter/exit（与前进同向）
```
`enterSharedAxisZ` = fadeIn(effectsDefault) + scaleIn(initialScale = 0.92f, spatialDefault)、`exitSharedAxisZ` = fadeOut + scaleOut(0.92f)（ui/animation/NirikoAnimation.kt:110-125）→ 返回看起来「又来了一遍前进」。

建议（保持 SharedAxis Z 家族，只做方向/幅度区分，可直接内联到每个 composable）：
```kotlin
// 建议在 ui/animation/NirikoAnimation.kt 新增（与既有 enterSharedAxisZ 同族）：
fun popEnterSharedAxisZ(specs: NirikoMotionSpecs): EnterTransition =
    fadeIn(animationSpec = specs.effectsDefault()) +
        scaleIn(initialScale = 0.94f, animationSpec = specs.spatialDefault())
fun popExitSharedAxisZ(specs: NirikoMotionSpecs): ExitTransition =
    fadeOut(animationSpec = specs.effectsDefault()) +
        scaleOut(targetScale = 1.06f, animationSpec = specs.spatialDefault())
```
然后 16 处：`popEnterTransition = { popEnterSharedAxisZ(specs) }`、`popExitTransition = { popExitSharedAxisZ(specs) }`。
备选（已存在但零引用）：ui/animation/NirikoAnimation.kt:81-96 的 `enterFromRight`/`exitToRight` —— 若希望返回有明确左右方向感，把 16 处 pop 换成它即可（横向位移 ±1/4 屏宽 + fade）。

### A-4 改动点 3：两处 BackHandler → PredictiveBackHandler（真正的 progress 跟手）

`PredictiveBackHandler` 语义（activity-compose 1.9.3，aar 已证）：`@Composable fun PredictiveBackHandler(enabled: Boolean = true, onBack: suspend (progress: Flow<BackEventCompat>) -> Unit)`；在 flow 上 `collect` 驱动位移，**协程正常结束 = 提交返回，抛 CancellationException（collect 被取消）= 手势取消，必须把位移弹回**。

(1) ui/subject/SubjectSearchScreen.kt:85（现状 `BackHandler(enabled = searchVm.phase != SearchPhase.COLLAPSED) { ... }`，返回 = 收起搜索）
```kotlin
PredictiveBackHandler(enabled = searchVm.phase != SearchPhase.COLLAPSED) { progress ->
    try {
        progress.collect { event ->            // event.progress 为 0f..1f
            searchVm.setBackProgress(event.progress)   // 新增：把手势进度写进 VM，UI 读它做跟手位移/缩放
        }
        searchVm.commitBack()                  // 手势提交 → 收起搜索（等价原 onBack 分支）
    } finally {
        searchVm.setBackProgress(0f)           // 提交或取消都必须复位，取消即自动弹回
    }
}
```
(2) ui/stats/CalendarDaySheet.kt:67（现状 `BackHandler(enabled = true, onBack = onDismiss)`）+ ui/components/NirikoNonModalSheet.kt
```kotlin
PredictiveBackHandler(enabled = true) { progress ->
    try {
        progress.collect { event -> sheetDragProgress = event.progress }  // 面板位移 = progress × panelHeightPx
        onDismiss()                                                       // 提交：走已有 flyOut 动画
    } finally { sheetDragProgress = 0f }                                  // 取消：弹回（这一条同时修掉 03 §G6「返回键关闭无退场」）
}
```
说明：`event` 是 `androidx.activity.BackEventCompat`，除 `progress` 还有 `swipeEdge`（`BackEventCompat.SwipeEdge.EDGE_LEFT/EDGE_RIGHT`）可用于区分左右边缘、给不同方向的位移。

### A-5 验收

1. `adb shell settings put global enable_on_back_invoked_callback` 无需（flag 在 manifest）；真机 Android 13+ 上做返回手势：二级页应在**手指移动过程中**同步缩放/淡出，松手前可撤回且回到原位无跳变。
2. 搜索页：IMMERSIVE_SEARCH 状态下从边缘慢滑，搜索框随 progress 收起；中途取消回到搜索态。
3. 日历 sheet：返回手势期间面板随 progress 下移；取消回弹；提交走飞出动画（不再是瞬移）。
4. 回归：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` 全绿；目标 API < 33 设备行为不变（PredictiveBackHandler 在旧平台退化为普通返回）。

### A-6 版本红线：现在不要跟的东西（子代理回补）

| 事项 | 为什么不要跟 | 依据 |
|---|---|---|
| activity 1.13.0 的预测性返回重构 | `PredictiveBackHandler` 改走 `androidx.navigationevent`（`LocalNavigationEventDispatcherOwner ?: LocalOnBackPressedDispatcherOwner`）+ `LifecycleStartEffect`；优先级从「最内层」变成「**最后 compose 的启用者胜**」，且 `enabled` 从 true 翻 false 有**同帧竞态** → 要求无条件调用、叠加顺序重排、并新增 navigationevent 依赖（1.1.2 stable） | PredictiveBackHandler.kt:112-123、BackHandler.kt:106；b/375343407、b/384186542；开关 `ActivityFlags.isOnBackPressedLifecycleOrderMaintained` |
| 平台 API 36 的返回增量 | `BackEvent.EDGE_NONE`（=2，三键导航/硬件返回键）、`BackEvent(float,float,float,int,long frameTimeMillis)`、`getFrameTimeMillis()`、`OnBackInvokedDispatcher.PRIORITY_SYSTEM_NAVIGATION_OBSERVER` 均需 **compileSdk ≥ 36 且 activity ≥ 1.13**（本工程 compileSdk 37 满足前者、activity 1.9.3 不满足后者）。**EDGE_NONE 时不要做左右方向动效**；`frameTimeMillis` 仅在 SDK 36+ 有值，可用于速度/惯性推算 | `BackEvent.java:30-98`；`BackMotionEvent`/`BackNavigationInfo` 为 @hide |
| `SystemOnBackInvokedCallbacks` | API 36 新增 `moveTaskToBackCallback(Activity)` / `finishAndRemoveTaskCallback(Activity)`，但标 `@FlaggedApi(FLAG_PREDICTIVE_BACK_SYSTEM_OVERRIDE_CALLBACK)`，且只在系统覆盖场景注册、不要自己调用 | 官方参考页 |
| `Modifier.scrollAway` / `dragContainerAndDrop` | **不存在**（material3 1.4.0 与 1.5.0-alpha29、foundation 三版本对两者命中均为 0）。底栏滚走只能自研 nestedScroll 驱动（本工程 ui/common/BottomBarScroll.kt 正是这条路线），拖放要用 `dragAndDropSource`/`dragAndDropTarget` | material3 `AppBar.kt:1718`/`:2202` 只有 AppBar scrollBehavior |
| `animateItemPlacement` | 已**删除**（foundation 1.10.3 命中 0），`animateItem` 是唯一 API → 本工程 7 处 `animateItem` 用法正确，**无迁移工作** | LazyItemScope.kt:93(1.10.3)/:96(1.12.1)；LazyGridItemScope.kt:47；LazyStaggeredGridItemScope.kt:48 |

## §B Material Components for Android（MDC-Android）定位与可跟进内容

**关键判断（MDC/material3 调研子代理回补，2026-10-01）**：MDC-Android 是 View 体系库（`com.google.android.material:*`），本工程是 100% Jetpack Compose 单 Activity，Gradle 缓存里既没有该构件、源码里也没有任何 View/XML 布局引用 → **MDC 对本工程没有可直接集成的代码**。**并且 MDC 已进入维护模式**：仓库 README 标题为 `[MAINTENANCE MODE]`，正文写明 "There are no more planned feature releases for Views"（源于 2026 I/O 的 Compose-first 宣布），**最后功能版本 1.14.0（2026-05-13）**，此后无新版本 → **不要期待 MDC 再出新的 transition**。若将来真要引入 1.14.0：要求 minSdk ≥ 23（本工程 26 满足）、compileSdk 35、AGP 8.11.1 + Gradle 8.13，1.13.0 起还要求 `android.nonTransitiveRClass=true` → **结论：不引入**（等于给纯 Compose 工程加一层 View 依赖，收益为 0）。对它有意义的「可跟进内容」是**规格与设计模式**，以及 Compose 侧的对应物：

| MDC-Android 中的东西 | 可直接抄进 Compose 的内容 | 本工程落点 |
|---|---|---|
| `MaterialContainerTransform`（路径 + scale + fade 的容器变换） | 变换的**参数化思路**：起点/终点 bounds 插值 + 淡入淡出曲线分配 | 对应 Compose `Modifier.animateBounds`/`sharedBounds`；本工程 9 处 sharedElement（05 §S） |
| `MaterialSharedAxis`（X/Y/Z 三轴） | 三轴语义：Z 轴 = fade + scale(0.92)，与 ui/animation/NirikoAnimation.kt:110-125 的 `enterSharedAxisZ` 完全同源 | 已采用；补 pop 规格（§A-3） |
| `MotionUtils` / `MaterialSpring` 令牌表 | spring 规格的**语义命名 + 取值**（fast/default/slow × spatial/effects） | 本工程已有 `NirikoMotionSpecs`（ui/animation/NirikoMotionSpecs.kt:27-42，因为 material3 1.4.0 的 `MotionScheme` 是 internal 才自建）；用 MDC 规格表交叉校验取值即可 |
| `MaterialElevationScale` / `MaterialFadeThrough` / `MaterialFade` | 按压高度缩放、through 过渡 | 本工程没有 FadeThrough 语义（顶层页用 HorizontalPager，非路由切换） |

**子代理回补的四条硬结论**（证据：MDC 仓库 README/releases、dl.google.com Maven 元数据、material3 AAR 字节码）：

1. **MDC 的 transition 家族没有拿到预测性返回支持**：1.13.x/1.14.x 全部 release notes 中 `material-transitions` 模块**零新增零改动**；`predictive back` 只出现在 SearchBar/SearchView（commit `44eabe31b86555a19d6eb1543294fdaa75a6e531`、`1b44a73a41f940704eba278a74862465f0dcef90`）→ 抄 MDC 的容器变换规格可以，但**别指望它给返回手势的跟手逻辑**。
2. **Compose 侧的对应物已经白拿**：material3 **1.4.0 内置** `DrawerPredictiveBackState`、`RailPredictiveBackState`、`internal/PredictiveBackState`、`ModalBottomSheetDialogWrapper$PredictiveBackOnBackPressedCallback`（1.3.2 尚无）→ 若将来改用官方 `ModalBottomSheet`/抽屉，预测性返回**无需自研**；本工程自研的 NirikoNonModalSheet 则仍需 §A-4 与 03 §G5/§G6 的方案。
3. **1.13.0 唯一的动效令牌变更**：`MotionUtil` 新增 default style 参数以解析主题 spring 属性（commit `f4342f1eb8aeae9932967529b2df9eea1a7b3f20`），同时 `androidx.dynamicanimation` 由 1.0.0 升 **1.1.0**（后者可独立采纳：`dynamicanimation-ktx` 1.1.0 stable、minSdk 21、无 Compose 依赖；但本工程已有 Animatable 体系，仅在需要 `SpringForce`/`FlingAnimation` 语义时才考虑）。
4. **1.14.0 的 shape/motion 能力全在 shape 与焦点环**：`StateListShapeAppearance`（进 MaterialCardView）、新 `ShapeAppearance` 接口、`DeterminateDrawable` 弹簧力可定制（commit `659e3bcb6d7582a3de4c204f91c0c12a8db35981`）、`FocusRingDrawable` 动画、SearchView `AnimationCoordinator`（`299bfa590693270a8f6b5e9b6789e222088adf81`）→ 均**无 Compose 等价 API**，登记即可、不迁移。

## §C material3（Compose）可跟进内容

### C-1 立即可用（1.4.0 已在 classpath）
- `ShortNavigationBar` / `WideNavigationRail`：ui/adaptive/NirikoNavSuite.kt:46-54 与 docs 已明示「刻意不用 NavigationSuiteScaffold」→ **不作为建议**，仅登记。
- `SegmentedButton`：可替换 ui/subject/SubjectDetailScreen.kt:1830-1832 的手写状态分段（含它自己的 spring）。
- `Slider` 的 thumb/track 槽位：可替换 ui/components/GlassControls.kt:150-186 的手写 GlassSlider（同时解决 03 §G9 的 consume 问题）。
- `Switch` 的 `thumbContent`：ui/components/GlassControls.kt:66-76 的开关目前是自绘 thumb 位移（spring 0.6/400）。
- `VerticalDragHandle`：ui/components/NirikoNonModalSheet.kt:126-130 的 28dp 把手。
- `PullToRefreshBox`：已用 1 处（ui/subject/SubjectSearchScreen.kt:217）。

### C-1b 1.4.0 上就能做的「不升版本」升级（子代理回补，**新增可执行项**）
- **`MaterialScheme` 自定义实现可以现在就接进 `MaterialTheme`**：1.4.0 的 `MaterialTheme.getMotionScheme(Composer, int)` 是 **public**（只是内部转发到 internal 的 `getLocalMotionScheme$material3()`），而 **`MotionScheme` 接口本身可用于自定义实现**（只有 6 个 spec 方法）→ 可以把 ui/animation/NirikoMotionSpecs.kt 包一层 `object NirikoMotionScheme : MotionScheme`，再 `MaterialTheme(colorScheme, motionScheme = NirikoMotionScheme, ...)`。收益：`MaterialTheme` 内部组件（含未来接入的 material3 组件）会自动用同一套弹簧，而不是「Niriko 组件用自研 spring、material3 组件用官方默认」。**成本低、零观感变化**（弹簧值不变，仅换载体）。
- **`MaterialExpressiveTheme` 在 1.4.0 已有带 `MotionScheme` 的重载**（1.3.2 只有 `MaterialExpressiveTheme(ColorScheme, Shapes, Typography, content)` 四参版）→ 若想把 ui/theme/Theme.kt:250-258 的 `MaterialTheme` 换成 expressive 壳，1.4.0 即可，只是拿不到官方 `MotionScheme.expressive()` 实例（需自实现，见上条）。
- **motion 令牌符号可引用**：`androidx.compose.material3.tokens.StandardMotionTokens`/`ExpressiveMotionTokens`/`MotionTokens`/`MotionSchemeKeyTokens` 在 1.4.0 与 1.5.0-alpha29 都存在（属性 getter 被 mangle 成 `getDefaultSpatial-x8lHwR4()` 等，**属 internal 编码、不能直接引用**）；但 `MotionSchemeKt.fromToken(MotionScheme, MotionSchemeKeyTokens)` 与 `value(MotionSchemeKeyTokens, Composer, int)` 是 **public** → 自定义 `MotionScheme` 实现里可以用符号而非硬编码数字表达 spring。

### C-2 等 material3 1.5.0 stable（**维持原结论**；子代理给出了精确时间点与代价）
- **精确时间点**：`MotionScheme.standard()` / `expressive()` **自 1.5.0-alpha01 起才 public**。1.4.0 的字节码方法名是 `standard$material3()` / `expressive$material3()`（`$material3` 后缀 = Kotlin internal，应用代码不可调用）→ 这就是 ui/animation/NirikoMotionSpecs.kt 存在的**根因**，前提成立。`LocalMotionScheme` 要到 **1.5.0-alpha20** 才 public（且到 alpha29 已被 `_localMaterialTheme` 取代）。
- **版本现状**：material3 stable = **1.4.0**（无 beta 线），alpha = **1.5.0-alpha29**（maven-metadata Last-Modified 2026-09-23）；`material3-window-size-class`、`material3-adaptive-navigation-suite` 同为 1.4.0 → **本工程锁的 1.4.0 就是当前 stable，不存在「落后」**。
- **升 alpha 的真实代价**（子代理实测）：1.5.0-alpha29 相对 1.4.0 **顶层类从 337 → 444**，且 alpha 内 API 仍在改名 → 维持 docs/m3-expressive-comparison.md 的「等 stable」结论。转公开后：删除 NirikoMotionSpecs.kt 的手抄值、改用官方 `MaterialTheme.motionScheme`（只需改这一个文件）。
- **expressive 弹簧令牌实测值（`ExpressiveMotionTokens.<clinit>`）**：DefaultSpatial **0.8/380**、FastSpatial **0.6/800**、SlowSpatial **0.8/200**、DefaultEffects **1.0/1600**、FastEffects **1.0/3800**、SlowEffects **1.0/800**（damping/stiffness）。对照本工程复刻的 Standard（spatial 0.9/700、0.9/1400、0.9/300）→ **expressive 明显更软更快，只改常量即可提前对齐规范**（方案见 04 §B-附）。旁证：04 §B 里那批散落的 0.6/400、0.5/300 字面量其实更接近 expressive 手感，而非 Standard。
- **`MaterialShapes`**：**1.5.0-alpha29 新增、1.4.0 完全不存在**，公开 35 个形状属性（Circle/Square/Slanted/Arch/Fan/Arrow/SemiCircle/Oval/Pill/Triangle/Diamond/ClamShell/Pentagon/Gem/Sunny/VerySunny/Cookie4Sided…Cookie12Sided/Ghostish/Clover4Leaf/Clover8Leaf/Burst/SoftBurst/Boom/SoftBoom/Flower/Puffy/PuffyDiamond/PixelCircle/PixelTriangle/Bun/Heart）+ `MaterialShapesKt.toShape(RoundedPolygon, …): Shape`；形变过渡 `AnimatedShapeState.animateToShape()`/`rememberAnimatedShape()` **仍是 internal**。→ **不必等 material3**：直接依赖 `androidx.graphics:graphics-shapes:1.1.0`（§D）就得到同一套 `RoundedPolygon`，本工程 ui/theme/NirikoShapes.kt:93-94 `squircle`/`:191` `SquircleCornerShape` 可直接升级成形状形变。
- **组件级 expressive 动效（1.4.0 全无）**：`EnlargeOnPressElement/Node`（按压放大 —— 可对照本工程 TouchGlow.kt:32 与 DampedDragAnimation.kt:33-34 的 `1 - 0.03f*p` 自研缩放）、`WavyProgressIndicatorKt/WavyProgressIndicatorDefaults`、`ToggleButton`/`ButtonGroup`/`SplitButton`/`FloatingToolbar`/`LoadingIndicator`、`InteractiveList*` 状态族。
- **Compose animation 侧（独立于 material3）**：1.12.1 stable 与 1.13.0-alpha03 的 `SharedTransitionScope` **公开面完全一致** → 升 animation 不改变共享元素行为；1.13.0-alpha03 新增 lookahead 动效可视化调试（`ExperimentalLookaheadAnimationVisualDebugApi`、`LookaheadAnimationVisualDebugConfig`、`LocalLookaheadAnimationVisualDebugColor`、`OnLookaheadMeasured`）、共享元素退出动量 `SharedElement$momentumAnimationOffset`/`updateExitVelocity`、`PendingAnimatedContentTransitionScope`、`EnterExitTransitionConfig`（登记，暂不接）。


## §D 补充：AndroidX 面其他可跟进项（子代理回补，2026-10-01）

| 项 | 现在能不能用 | 精确落点 | 与本工程的关系 / 成本 |
|---|---|---|---|
| `Modifier.magnifier` | 1.10.3 即有、**非实验性** | foundation-android 1.10.3 `Magnifier.android.kt:93`（1.12.1 同为 :93） | 海报/封面「长按细节放大」可用；与共享元素不冲突。**成本低** |
| `androidx.graphics:graphics-shapes:1.1.0` | **stable 1.1.0** | `Shapes.android.kt:48` `RoundedPolygon.toPath(path)`、`:53` `Morph.toPath(progress, path)`；Compose 侧 `AndroidPath.android.kt:28-35` 的 `asComposePath()` | `Morph(start,end).toPath(progress).asComposePath()` 可做形状形变（与 ui/theme/NirikoShapes.kt:93-94 `squircle`/`:191` `SquircleCornerShape` 天然配套）。**成本低（新增一个依赖）** |
| `LookaheadScope` / `Modifier.approachLayout` | 1.10.3 已公开且 1.12.1 未变 | ui 1.10.3 `LookaheadScope.kt:50`（`LookaheadScope`）与 `:102`（`approachLayout`） | 05 §S 的 `animateBounds` 方案基础；「接近目标尺寸才插关键帧」。概念成本高于 API 成本 |
| 拖放 `dragAndDropSource`/`dragAndDropTarget` | foundation 1.10.3 即有 | `DragAndDropSource.kt:84`/`:105`、`DragAndDropTarget.kt:41` | 收藏列表拖拽排序时可用（含拖拽期间的动效）；**成本低** |
| SharedTransition | **自 1.10.3 起已 stable**；1.10.3→1.12.1 签名零变化。**我自己的 javap 实测（animation-android 1.10.5 aar）**：`SharedTransitionScope` 的 `sharedElement`/`sharedBounds` 方法上**没有任何 `ExperimentalSharedTransitionApi` 注解**（整个 class dump 对该注解名命中 0），而注解类本身仍在（`@RequiresOptIn(message="This is an experimental shared transition API.")`、BINARY retention）→ 现有 `@OptIn(ExperimentalSharedTransitionApi)` 已是**冗余**，可顺手清理（LOW）。MDC 子代理曾称「共享元素仍 @Experimental，未稳定」，与实测不符，以字节码为准 | `SharedTransitionScope.kt:582`(1.10.3) 对 `:620`(1.12.1)；`sharedBounds` :683/:721；`animateBounds` `AnimateBoundsModifier.kt:118`/`:130` | 本工程 9 个挂载点**无需为版本做迁移**；已知限制：共享元素绘制在 overlay，**clip/alpha 必须写在 `sharedBounds(...)` 之后**；默认 resizeMode 走 lookahead 缩放，内容需真实重排时用 `RemeasureToBounds`（详见 05 §S） |
| edge-to-edge 强制 | targetSdk 34 **暂未受影响** | compat change 309578419 `ENFORCE_EDGE_TO_EDGE`（targetSdk 35 起强制）、377864165 `DISABLE_OPT_OUT_EDGE_TO_EDGE`（36 起让 `windowOptOutEdgeToEdgeEnforcement` 失效）；`PhoneWindow.java:177-191`/`:459-496`/`:2537-2548`、`WindowManager.java:3535-3540` | `enableEdgeToEdge()` 签名与默认值 1.9.3→1.13.0 完全未变（本工程 MainActivity.kt:98 已调用）→ **无 API 迁移**；升 targetSdk 时真正要回归的是 inset 正确性（systemBars/displayCutout/ime）与玻璃底栏在透明条上的对比度。**成本中（回归为主）** |
| Compose BOM 升级路径 | — | 子代理实测：2026.02.01 = 1.10.4、2026.05.01 = 1.11.2、**2026.09.00 = ui/foundation/animation 1.12.1，但 material3 仍是 1.4.0** | **任何 BOM 都不含 material3 1.5.0-alpha** → 升 BOM 不会被动把 material3 顶到 1.5.0，故「`NirikoMotionSpecs` 手抄 spring 仍必要」在升 BOM 后依然成立；navigation-compose 2.10.x 需 Compose 1.10.4+ |

---

**基线**：git HEAD `97ce7df`（工作树有 20+ 未提交改动）。审计日期 2026-10-01。约束：**不改动任何源码**。
**工程口径**：com.otakup.niriko / Kotlin 2.3.10 / AGP 8.13.2 / composeBom 2025.12.01（Compose UI 1.10.x、material3 1.4.0）/ navigationCompose 2.8.5 / activityCompose 1.9.3 / targetSdk 34 / minSdk 26。
**验收基线**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest`（P0 批次时点为 78 个测试类 / 696 用例 / 0 失败）；涉及帧预算的项另需真机 `adb shell dumpsys gfxinfo com.otakup.niriko framestats`。
**不得再报的刻意取舍**（docs/m3-expressive-comparison.md:108、docs/dock-kyant-migration-plan.md:1-36、docs/ui-upgrade-plan-2026.md:154/166/256/264/273、docs/performance-audit.md 全篇）：NirikoBottomBar.kt:49 与 NirikoNavSuite.kt:98 的 180ms tween；SubjectDetailScreen.kt:442-445 的 350ms 材质渐显；pressSpring/appearSpring/panelSpring/tabFadeIn/tabFadeOut 保留；ui/bottombar/** 原样搬迁 kyant（其中 spring(1f,1000f,…)=DampedDragAnimation.kt:30 等为上游取值）；shimmer 静态退化、dock 恢复动画跳过、布局切换动画跳过；Stats 图表静态化、CoverImage 移除外层 crossfade、详情页 hero 大图 1080×1440；performance-audit.md 的 P0/P1/P2 性能结论（本批不重复，只补其未覆盖的「动画阶段」维度）。
