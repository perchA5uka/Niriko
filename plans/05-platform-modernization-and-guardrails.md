# 方案 05：平台现代化与小项（haptics / overscroll / 共享元素 / 测试护栏）

## §H 触感反馈（HapticFeedback）词表升级（LOW，成本极低）

**现状（一手 grep）**：全仓 `hapticFeedback` 只有 2 文件 / 3 调用点，且**全部**是 `HapticFeedbackType.LongPress`：
- app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt:111（import）、:286
- app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt:71（import）、:505、:550

**可用而未用的词表**（androidx.compose.ui:ui-android 1.10.x 的 `HapticFeedbackType$Companion` 常量实测，13 个）：`LongPress`、`TextHandleMove`、`VirtualKey`、`KeyboardTap`、`Confirm`、`ContextClick`、`Reject`、`GestureEnd`、`GestureThresholdActivate`、`SegmentTick`、`SegmentFrequentTick`、`ToggleOn`、`ToggleOff`。

**结论：现有 3 处只有 1 处语义错。**（这一条要写清楚，避免执行者「为了新词表而改对的代码」）
| 位置 | 现在的语义 | 判定 |
|---|---|---|
| app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt:505 与 `:550` | `onLongClick` → 弹「长按浮层：状态 + 进度快捷」（`:513` 注释） | **保持 LongPress**（长按 = 长按语义，正确） |
| app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt:286 | 收到 `msg == "记录已保存"` → 触感后弹 Snackbar 并返回（`:283-288`） | **建议改 `Confirm`**：这是「保存成功确认」，用 LongPress 会误导（长按的触感被用来表示确认） |

**建议新增的触感点（都属新行为，需产品确认）**
| 交互 | 位置 | 建议类型 |
|---|---|---|
| 底栏/导航切换页 | app/src/main/java/com/otakup/niriko/MainActivity.kt:382、navigation/MainPager.kt:112、ui/adaptive/NirikoNavSuite.kt:130（`animateScrollToPage` 之前） | `SegmentTick`（连续翻页用 `SegmentFrequentTick`） |
| 玻璃开关切换 | ui/components/GlassControls.kt:66-76 的 Switch 路径 | `ToggleOn` / `ToggleOff`（按目标状态选） |
| sheet 拖动越过分级阈值 | ui/components/NirikoNonModalSheet.kt:137 `shouldDismissSheet(...)` 判定为 true 时 | `GestureThresholdActivate` |
| 长按浮层里选中一个状态 | ui/screens/Screens.kt:522-528 的 `onStatusSet` / `onProgressSet` | `Confirm` 或 `SegmentTick` |
| 进入/退出多选 | ui/screens/Screens.kt:503 的 `multiSelect` 分支切换处 | `ContextClick` |
| 同步/绑定失败提示 | 各 Sync 页面失败分支（如 ui/bilibili/BilibiliSyncScreen.kt 的错误态） | `Reject` |

**注意**：haptics 经 `LocalHapticFeedback.current` 获取（现有调用点已如此），**不受 `MotionDurationScale`/`reduceMotion` 影响**；在 reduceMotion 下**应保留**（触感正是动态效果的替代反馈通道），不要顺手把它一起关掉。系统级「触感反馈」开关由平台处理。

## §O 平台级拉伸 overscroll（LOW，可能「无需改动」）

**现状**：全仓 `flingBehavior`/`SnapFlingBehavior` 命中 **0**（没有任何自定义 fling），滑动到底/顶用的是 Compose 默认 overscroll（Android 12+ 为 EdgeEffect 拉伸）。
**可跟进项（一手 AAR 证据）**：androidx.compose.foundation:foundation-android 1.10.x aar 里已存在新的 overscroll 抽象：`androidx/compose/foundation/OverscrollFactory.class`、`AndroidEdgeEffectOverscrollFactory.class`、`StretchOverscrollNode.class`、`GlowOverscrollNode.class`。
**结论**：默认行为已经是平台拉伸，本工程没有自定义 overscroll 的需求 → **建议不做**。仅登记一条：若将来要做「下拉刷新的自定义拉伸」或「底栏滑走的手感交接」，入口应走这套工厂（⚠️ 公开入口的确切名称/是否 `@ExperimentalFoundationApi` 未在本机验证，实施前需 curl 无效——用 `javap`/`api` dump 或联网核对）。
关联：ui/subject/SubjectSearchScreen.kt:217-229 的 `PullToRefreshBox`（全仓唯一下拉刷新）已用材料级实现，不需要自研。

## §S 共享元素与 `animateBounds` 现代化（LOW-MEDIUM）

**现状（一手行号）**：`SharedTransitionLayout` 在 app/src/main/java/com/otakup/niriko/MainActivity.kt:292-293 提供作用域；`sharedElement(` 共 9 个挂载点：
- ui/components/CoverImage.kt:76-79（**代理通道**：28 处 `sharedElementKey =` 都经它生效）
- ui/subject/SubjectDetailScreen.kt:946-951
- ui/subject/RelationsSection.kt:115-120
- ui/subject/CharacterStaffSection.kt:336-339
- ui/person/PersonSubjectCard.kt:53-56
- ui/person/PersonDetailScreen.kt:137-140
- ui/character/CharacterDetailScreen.kt:105-108 与 :222-225
- ui/library/LibraryGalleryCard.kt:59-62
全部**未传 `boundsTransform`/`animationSpec`** → 走 Compose 默认 spring；且全部**零 reduceMotion 守卫**（见 [04 §A-1 G3/G4](04-tokens-and-reduce-motion.md)）。

**可用而未用（androidx.compose.animation:animation-android 1.10.x aar 实测）**：`AnimateBoundsModifierKt$animateBounds`、`BoundsAnimationModifierNode$approachMeasure`、`OnLookaheadMeasured`、`SharedTransitionScope$ResizeMode`、`SharedTransitionScope$PlaceholderSize`、`SharedTransitionScope$skipToLookaheadPosition`/`skipToLookaheadSize`。

**建议做两件事**
1. **收敛 + 守卫 + 可中断**：新增一个帮助函数，把 9 个挂载点（尤其 CoverImage.kt:76-79 这条代理通道）统一成
```kotlin
@Composable
fun Modifier.nirikoSharedBounds(key: Any): Modifier {
    val boundsSpec = if (motionEnabled()) spring<Rect>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) else snap()
    return with(sharedTransitionScope!!) {
        sharedElement(rememberSharedContentState(key), boundsTransform = { _, _ -> boundsSpec })
    }
}
```
（`spring<Rect>` 用作 `BoundsTransform` 的返回：`FiniteAnimationSpec<Rect>`；`snap()` 来自 `androidx.compose.animation.core`。）
2. **列表→详情的非共享布局动画**：本工程有 7 处 `Modifier.animateItem()`（ui/screens/Screens.kt:493 `:543` `:594`、ui/search/DiscoverPosterPane.kt:133、ui/search/SearchResultsPane.kt:138 `:214`、ui/search/TrendingSection.kt:187）——若希望「筛选/排序后卡片重排」也有平滑移动，可在列表容器外包 `LookaheadScope` 并给卡片加 `Modifier.animateBounds(lookaheadScope)`；`ResizeMode.RemeasureToBounds` 适合内容会随宽度重排的卡片（如 PosterGridCard）。

**版本与限制（AOSP/AndroidX 子代理回补）**：SharedTransition 自 animation **1.10.3 起已 stable**（`ExperimentalSharedTransitionApi` 已不被任何 API 使用），`Modifier.sharedElement` 在 1.10.3（SharedTransitionScope.kt:582）与 1.12.1（:620）参数与默认值完全一致 → 本工程 9 个挂载点**没有版本迁移工作**，本方案纯属观感 / 守卫 / 可中断性改进。两个必须知道的限制：① 共享元素绘制在 **overlay** 上，`clip` / `alpha` 必须写在 `sharedBounds(...)` **之后**（写在之前会被截断）；② 默认 `resizeMode` 走 lookahead 缩放，**内容需要真实重排时用 `RemeasureToBounds`**（例如卡片内的 Row 要重排到动画尺寸）。基础 API `LookaheadScope`（ui 1.10.3 LookaheadScope.kt:50）与 `Modifier.approachLayout`（:102）在同版本已公开。另：haptics 词表在 ui 1.10.3 已是全集（HapticFeedbackType.kt:135-147，两版本一致），**不需要等任何版本**。 `@OptIn(ExperimentalSharedTransitionApi)` 在 1.10.5 上已是冗余（我自己的 javap 实测：`SharedTransitionScope` 的 `sharedElement`/`sharedBounds` 上无该注解，注解类仍在）→ 可顺手清理，见 01 §D。

**风险**：`sharedElement` 的 `boundsTransform` 一旦显式给出，会覆盖默认 spring —— 改动必须逐屏录屏比对（尤其 CoverImage 这条代理通道影响 28 处）。建议**先只做 1（守卫 + 统一 spec）**，2 单独排期。
**验收**：前进/后退时封面飞入仍然顺滑且方向正确；开启 reduceMotion 后不再飞入（瞬时到位）；连续快速前进后退不出现共享元素残留（可中断性来自 spring 本身，仍需真机确认）。

## §T 测试与 CI 护栏（LOW，但决定长期不回归）

**现状**：app/src/test 共 78 个测试类；与动效相关的只有 2 个 —— app/src/test/java/com/otakup/niriko/ui/components/ShimmerPolicyTest.kt、app/src/test/java/com/otakup/niriko/ui/components/NirikoNonModalSheetTest.kt（后者覆盖纯函数 `shouldDismissSheet`）。

**可加的单测（都是纯函数，零渲染依赖）**
| 目标 | 位置 | 断言 |
|---|---|---|
| `pageIndicatorWeight` / `pageIndicatorWidthDp` | ui/screens/Screens.kt:112-118（internal，同模块可测） | 边界：pagePosition 恰为整数时 weight=1；相邻页 weight=0；宽度落在 `PAGE_INDICATOR_MIN_WIDTH_DP..MAX_WIDTH_DP` |
| `NirikoSprings` 等价性 | 04 §B-2 新增 | `press()/reveal()` 的 dampingRatio/stiffness 与旧 `pressSpring()/appearSpring()/panelSpring()` 逐值相等（防止令牌收敛时误改观感） |
| `ShimmerPolicy.shouldAnimate` 组合表 | ui/components/ShimmerPolicy.kt:30 | 已有测试，补充 `glassEffect == OFF` 分支 |
| `shouldDismissSheet` 阈值边界 | ui/components/NirikoNonModalSheet.kt:60-65 | 72dp / 900dp-s 的临界值与方向（向上不触发、向下触发） |

**不建议**：为 ui/bottombar/** 或 glass 面板加 Robolectric 渲染测试 —— 已知 Robolectric 4.17 无法渲染 kyant 着色器（SkSL 报 `'color' is not a valid layout qualifier` / `cannot swizzle value of type 'shader'`），会得到假失败。

**CI 报告（不设门禁，只出数）**：在 .github/workflows/android-ci.yml 加一步只读脚本，输出 4 个数字并作为 artifact：
1. 带字面量数值的 `spring(`/`tween(` 数量（令牌收敛进度）；
2. `motionEnabled()` 调用点数（04 §A 的覆盖进度）；
3. 拖拽路径中 `animateTo(` 未传 `initialVelocity` 的数量（03 §G2/G5 进度）；
4. 组合期读取动画值的可疑点（`animateDpAsState`/`animateFloatAsState` 结果直接用于 `.offset(`/`.size(` 的匹配，需人工判读）。

**可选（未排期）**：加 `benchmark` 模块（Macrobenchmark）+ Baseline Profile，用 `FrameTimingMetric` 量化 02/03 的收益；本工程当前没有该模块，docs/performance-audit.md 也指出无模拟器、只能静态审查 + 真机 `dumpsys gfxinfo`。


---
**基线**：git HEAD `97ce7df`（工作树有 20+ 未提交改动）。审计日期 2026-10-01。约束：**不改动任何源码**。
**工程口径**：com.otakup.niriko / Kotlin 2.3.10 / AGP 8.13.2 / composeBom 2025.12.01（Compose UI 1.10.x、material3 1.4.0）/ navigationCompose 2.8.5 / activityCompose 1.9.3 / targetSdk 34 / minSdk 26。本机 Gradle 缓存路径：C:\Users\21977\.gradle\caches\modules-2\files-2.1。
**验收基线**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest`（P0 批次时点为 78 个测试类 / 696 用例 / 0 失败）。
**不得再报的刻意取舍**详见 [README.md](README.md) 末尾清单。