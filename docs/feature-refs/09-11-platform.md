# 9 性能观测 + 11 系统集成与平台能力（合卷）

> 一句话结论：本域最值得做的是 **9.2 把性能观测从「文档里的数字」变成 macrobenchmark 基线**（`docs/phone-shared-motion-profile.md` 里 274.63ms → 183.77ms、decodeBitmap 64→28 次这些都是单次同机样本，无法在发版间比较），以及 **11.1 Glance 小组件**（唯一能让人「不打开 app 就看到追番」的平台能力）。
> 11.7 与 12.6 是同一条（焦点可达性），本文件只保留 11.7 并注「合并自 12.6」。
> **不建议做 11.6 实时同步**：纯客户端 + 无服务端的前提下，实时性只能用 WebDAV 轮询换，成本 L、收益不明；只保留「回前台自动拉一次」并让状态可见。
> 9.1 的结论是：**不要自研性能 HUD**，用 AndroidX benchmark 替代——自研 HUD 只能给开发者看，macrobenchmark 能进 CI 做门禁。

## 9.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 9.1 | 性能 HUD | 无 | P2 | S | 不建议自研；用 `FrameTimingMetric` 替代 |
| 9.2 | 大图/长列表滚动性能观测 | 部分 | **P1** | M | `MacrobenchmarkRule.measureRepeated` + `FrameTimingMetric` |
| 9.3 | 详情页首帧预算门禁（新） | 无 · 新提出 | P1 | M | `TraceSectionMetric` + 现有 `Trace` 点 |
| 9.4 | 用户可导出的性能日志（新） | 无 · 新提出 | P2 | S | 与 11.11 的 `CrashLogStore` 同源 |
| 9.5 | 启动阶段预算（新） | 无 · 新提出 | P2 | S | `StartupTimingMetric` |

## 9.1 逐条详解

### 9.1 性能 HUD
- **现状**｜无｜全仓没有帧率/内存采样面板；性能证据只有两份文档：`docs/performance-audit.md`（静态定位 + adb 清单：`dumpsys gfxinfo reset/framestats`）与 `docs/phone-shared-motion-profile.md`（真机 trace 对照）。没有 `Choreographer`/`FrameMetrics` 采样代码。
- **提升**｜用户侧无收益（本来就不该给用户看），开发者侧收益是「把感觉卡变成这一帧 66ms」；工程上**不建议**为此写一套自研 HUD——它会随 Compose 版本腐化，且数据无法横向比较。
- **原理**｜如果仍要做，最小实现是 `Choreographer.getInstance().postFrameCallback` 逐帧记账 + 240 帧环形缓冲，`Debug.MemoryInfo` 取 PSS，`Window.addOnFrameMetricsAvailableListener`（API 24+）取 `TOTAL_DURATION`；入口用「设置页版本号连点 7 次」解锁，面板是可拖拽 `ComposeView`，仅 `BuildConfig.DEBUG` 注入。
```kotlin
Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
        if (last != 0L) ring.add((frameTimeNanos - last) / 1_000_000f)   // ms
        last = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }
})
```
  但**推荐替代方案**：同样的数据用 macrobenchmark 采（见 9.2），产物是 JSON 报表而不是悬浮窗。
- **效果**｜若做：悬浮小窗显示 `FPS 58 · p95 21ms · jank 4/240 · PSS 312MB`，可暂停、可复制 JSON。若不做的收益：少一个必须随 Compose 升级维护的组件。
- **参考**｜`androidx/androidx@androidx-main:benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/Metric.kt` [码] `FrameTimingMetric` / `FrameTimingGfxInfoMetric` / `StartupTimingMetric` / `TraceSectionMetric`（自研 HUD 的四个指标官方都有对应 Metric）。
- **成本/风险**｜S｜不要做成产品功能（会泄露设备信息、增加耗电）；`BuildConfig.DEBUG` 之外的注入路径要关掉。
- **优先级**｜P2（结论：先做 9.2，HUD 仅记录）

### 9.2 大图/长列表滚动性能观测
- **现状**｜部分｜有两次真机记录但**没有可重复基线**：`docs/phone-shared-motion-profile.md` 记录同机对照（最长测量/布局 274.63ms → 183.77ms、Recomposer 55.06 → 45.99ms、decodeBitmap 64 次/496.48ms → 28 次/309.67ms、Late Present 29 → 15）；非 Debug 对照为 62.84ms / 18.78ms。但 `app/src/androidTest` 下有 **0 个文件**，每次都要人肉 trace，数字不能跨版本比较。
- **提升**｜从「记一次数字」变成「每次发版可比」；工程上把 183.77ms 这类结论从文档搬进 CI 报表，把「感觉这次更卡」变成「p95 退化 12%」。
- **原理**｜新增 `benchmark` 模块（`com.android.test`，targetProjectPath = `:app`）+ baselineProfile 模块：
```kotlin
@Test fun libraryScroll() = benchmarkRule.measureRepeated(
    packageName = "com.otakup.niriko",
    metrics = listOf(FrameTimingMetric()),
    compilationMode = CompilationMode.Partial(),
    startupMode = StartupMode.COLD,
    iterations = 5,
    setupBlock = { pressHome(); startActivityAndWait() },
    measureBlock = { device.findObject(By.res("libraryGrid")).fling(Direction.DOWN) },
)
```
  建议三条 case：作品库滚动 / 详情页进入并滑到底 / 剧照全屏翻页。结果 JSON 入库，与上一次比 >10% 退化则失败。
- **效果**｜CI 产出 `libraryScroll p50/p90/p95`、`frameOverrunMs`、`jank count`；PR 里能直接看到「详情页首屏 +18ms」。真机定位仍用 `docs/performance-audit.md` 的 `dumpsys gfxinfo` 清单，两者分工：CI 看趋势，真机看单帧。
- **参考**｜`androidx/androidx@androidx-main:benchmark/benchmark-macro-junit4/src/main/java/androidx/benchmark/macro/junit4/MacrobenchmarkRule.kt` [码] `measureRepeated(metrics, compilationMode, startupMode, iterations, setupBlock, measureBlock)`；`androidx/androidx@androidx-main:benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/MacrobenchmarkScope.kt` [码] `startActivityAndWait(intent)` / `pressHome(delayDurationMs)`。
- **成本/风险**｜M｜需要真机/模拟器与 `profileable` 构建；CI 上跑要接设备云或用本机 adb 定时任务；baselineProfile 会改 APK 体积（收益通常大于成本）。
- **优先级**｜**P1**

## 11.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 11.1 | 桌面小组件（Glance） | 无 | **P1** | M | Glance `provideGlance` + `PreferencesGlanceStateDefinition` |
| 11.2 | 无障碍朗读优化 | 部分 | P1 | M | Kotatsu `onProvideAssistContent` + 逐页脚本 |
| 11.3 | 帐篷模式（水平折痕上下分栏） | 无 | P2 | M | `FoldingFeature.State.HALF_OPENED` |
| 11.4 | 跨端一致体验 | 部分 | P2 | L | `docs/ios-port/` 已有 12 篇调研 |
| 11.5 | 触控笔 / 多点触控 | 部分 | P2 | S | telephoto 已支持，其余待补 |
| 11.6 | 跨设备实时同步 | 部分 | P2（建议不做） | L | actual 的 HLC + merkle |
| 11.7 | 焦点/方向键可达性（合并自 12.6） | 无 | P2 | M | 全仓仅 15 处 focus 相关 |
| 11.8 | 深浅色与大字体布局回归 | 部分 | P1 | M | Roborazzi/Paparazzi 截图基线 |
| 11.9 | 系统分享目标的完整覆盖 | 部分 | P1 | S | Kotatsu 四条独立分享通道 |
| 11.10 | 应用内帮助 / 上手引导 | 部分 | P1 | S | Mihon 步进式 onboarding |
| 11.11 | 崩溃与错误上报的本地化 | 无 | P1 | S | Kotatsu `ErrorDetailsDialog` |
| 11.12 | 通知渠道与免打扰（新） | 无 · 新提出 | P1 | S | 单渠道 → 分档 + quiet hours |
| 11.13 | Doze/省电下的事实与降级（新） | 无 · 新提出 | P1 | M | `canScheduleExactAlarms()` 降级 |
| 11.14 | 成为分享接收方（新） | 无 · 新提出 | P1 | S | 复用 `.nirikotheme` 的 intent-filter |
| 11.15 | 外接鼠标悬停与右键（新） | 无 · 新提出 | P2 | S | `PointerEventType.Enter` |
| 11.16 | 边到边与 insets 回归（新） | 无 · 新提出 | P1 | M | targetSdk 35 前必做 |
| 11.17 | 隐私自述页（新） | 无 · 新提出 | P2 | S | 由 baseUrl 常量生成 |

## 11.1 逐条详解

### 11.1 桌面小组件（Glance）
- **现状**｜无｜`app/src/main/AndroidManifest.xml` 里只有开机广播 receiver（`AiringReminderBootReceiver`），**没有** `APPWIDGET_UPDATE` 的 receiver，也没有 `appwidget-provider` 资源；grep `Glance|AppWidget|glance` 在 `app/src/main/java` 下 0 命中，`gradle/libs.versions.toml` 也没有 glance 依赖。
- **提升**｜用户不打开 app 就能看到「今天更新了哪几部 / 我在看的还差几集」，点一下进详情；工程上复用已有的 `CollectionDao` + `CalendarRangeCalculator`，不需要新数据源，也不需要服务端。
- **原理**｜**数据从哪来：Room → 只读快照，widget 不查库。**
  1) `WidgetWorker`（WorkManager，每日 + 进度变化时 `enqueueUniqueWork(REPLACE)`）：在后台线程读 Room（`SubjectDao`/`CollectionDao` + `CalendarRangeCalculator`），算出一个**扁平、可序列化**的小快照：
```kotlin
@Serializable data class WidgetEntry(val id: Long, val title: String, val cover: String?, val ep: Int, val total: Int)
@Serializable data class WidgetSnapshot(val today: List<WidgetEntry>, val watching: List<WidgetEntry>, val updatedAt: Long)
```
  2) 快照写进 Glance 自己的 `PreferencesGlanceStateDefinition`（DataStore 后端）：`currentState<Preferences>().edit { it[snapshotKey] = json }`，然后 `NirikoWidget().updateAll(context)`。
  3) **刷新与更新的边界**：`GlanceAppWidget.update(context, id)` 只负责「把已有数据重新画成 RemoteViews」，它**不取数**；取数只发生在 Worker。系统的 `onUpdate`（添加/尺寸变化/系统周期）由 `GlanceAppWidgetReceiver` 接住，它内部已经 `goAsync`——源码注释明确要求子类**不要自己调 goAsync**。
  4) `provideGlance` 里只读快照、零 IO；快照超过 24h 时角标显示「可能过期」。
- **效果**｜4×2 组件：上半「今日更新 3 部」（封面 + 集数），下半「在看的 2 部」（进度条 + `5/12`）；点条目 deep link 到详情（走 6.20 的 section 可选）；空态「今天没有更新」；数据超 24h 显示「数据可能过期，点刷新」；点刷新按钮触发一次 `OneTimeWorkRequest`。
- **参考**｜`androidx/androidx@androidx-main:glance/glance-appwidget/src/main/java/androidx/glance/appwidget/GlanceAppWidget.kt` [码] `abstract suspend fun provideGlance(context, id)`、`open val stateDefinition = PreferencesGlanceStateDefinition`、`suspend fun update(context, id)`；`androidx/androidx@androidx-main:glance/glance-appwidget/src/main/java/androidx/glance/appwidget/GlanceAppWidgetReceiver.kt` [码] `onUpdate` → `goAsync(coroutineContext) { doUpdate(context, appWidgetIds) }`，注释「不要自己调 goAsync」。
- **成本/风险**｜M｜新增依赖（`androidx.glance:glance-appwidget`）+ receiver + 两个尺寸档；WorkManager 周期任务在 Doze 下会推迟（见 11.13）；组件进程与 app 进程分离，`NirikoApplication` 的懒加载初始化不能在 widget 里假设已跑完。
- **优先级**｜**P1**

### 11.2 无障碍朗读优化
- **现状**｜部分｜全仓 `contentDescription|semantics {|clearAndSetSemantics` 有 **133 处/61 个文件**（`ui/subject/SubjectDetailScreen.kt` 11 处最多），`RatingComparisonSection.kt:122` 甚至给争议度补了 `add("评分争议度：$it")`；但 `app/src/androidTest` 为 **0 文件**，没有朗读顺序/合并语义的自动化验收，也没有逐页脚本。
- **提升**｜读屏用户能听懂「这一块是什么、几条、什么状态」而不是逐个图标乱念；工程上把「区块级语义」定成约定，新增部件自动遵守，避免每加一块都要补一遍。
- **原理**｜三条规则落成代码约定：
```kotlin
GlassSectionCard(modifier = Modifier.semantics(mergeDescendants = true) {
    heading(); contentDescription = "关联条目，6 条"
})
// 装饰元素（背景墙/玻璃光斑）用 clearAndSetSemantics {} 彻底闭嘴
// 宽屏两栏时用 traversalIndex 显式排「左列 → 右列」
```
  列表用 `collectionInfo`/`collectionItemInfo`；数字类（进度、评分）读成句子而不是「5 斜杠 12」。
- **效果**｜逐页朗读脚本进 `docs/`：作品库卡 =「标题，评分 8.4，看到 5/12」；详情区块 =「关联条目，6 条，横滑」；评分分布 =「9 分，128 人」；弹层打开时焦点自动进弹层、关闭回原位。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/details/ui/DetailsActivity.kt` [码] `onProvideAssistContent` 把当前条目 `publicUrl` 交给 `AssistContent.webUri`（Assist/读屏的上下文出口）。
- **成本/风险**｜M｜`mergeDescendants` 会把内部可点元素吞掉（按钮必须显式保留 `onClick` 语义）；横滑 `LazyRow` 的朗读边界要真机验证。
- **优先级**｜P1

### 11.3 帐篷模式（水平折痕上下分栏）
- **现状**｜无｜`ui/adaptive/WindowClass.kt` 只按宽/高断点分 COMPACT/MEDIUM/EXPANDED（`NirikoWindowLayout.from(widthSizeClass, heightSizeClass)`），`ui/adaptive/AdaptiveDetailScaffold.kt` 做两栏；全仓没有 `FoldingFeature`/`WindowLayoutInfo`/`HALF_OPENED` 引用。
- **提升**｜折叠屏半折立在桌上时，上半屏放封面+标题、下半屏放详情列表，不用手扶；工程上把姿态作为第 4 个布局档位，纯函数可单测，不影响手机端。
- **原理**｜`WindowInfoTracker` 收集 `WindowLayoutInfo`，取 `displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()`：
```kotlin
val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
val layout = when {
    fold?.state == FoldingFeature.State.HALF_OPENED &&
        fold.orientation == FoldingFeature.Orientation.HORIZONTAL -> NirikoWindowLayout.TENT   // 上下分栏
    fold?.state == FoldingFeature.State.HALF_OPENED -> NirikoWindowLayout.BOOK                  // 左右分栏
    else -> NirikoWindowLayout.from(w, h)
}
```
  `occlusionType == FULL` 时把 `fold.bounds` 的高度作为中间留白，内容不跨折痕。新档位走纯函数 `from(foldState, orientation, w, h)`，与现有 `from` 同一套单测。
- **效果**｜帐篷姿态：上半屏封面 + 标题/评分（不滚动），下半屏详情列表独立滚动；折痕位置留 8dp 安全区；非折叠设备/平板完全无变化（档位不进入）。
- **参考**｜`androidx/androidx@androidx-main:window/window/src/main/java/androidx/window/layout/FoldingFeature.kt` [码] `State.FLAT/HALF_OPENED`、`Orientation.VERTICAL/HORIZONTAL`、`OcclusionType.NONE/FULL`。
- **成本/风险**｜M｜无折叠屏真机无法验收（本机无模拟器，见 `docs/performance-audit.md` 的环境说明）；`WindowInfoTracker` 在 Compose 里要用 `callbackFlow` 桥接生命周期。
- **优先级**｜P2

### 11.4 跨端一致体验
- **现状**｜部分｜`docs/ios-port/` **确实存在 12 篇调研**（`01-spike-checklist` … `11-gradle-module-draft`），`07-commonmain-candidates.md` 已给出静态扫描结论：`app/src/main/java` 约 252 个 Kotlin 文件中 **207 个没有直接 `import android.*`**，并列出 A 类可迁移清单（`data/calculator/**`、`data/model/**`、`data/remote/**/dto`、`plugin/bilibili/BilibiliMatcher` 等）；但代码仍是 Android-only（Retrofit/OkHttp/Coil/Room 直依赖）。
- **提升**｜无直接用户可见收益；收益是「把可共享边界钉死」，避免以后每个新功能都顺手写死 `android.util.Log`/`LocalDate` 而永久锁在 Android。
- **原理**｜不移植，只加固边界：给 207 个候选文件加一条 `commonMainCandidates` 白名单 + 一个 Gradle 校验任务（禁止白名单内新增 `import android.`）；把 `java.time.LocalDate` 换成 `kotlinx-datetime`（这是最常见的隐性锁），`android.util.Log` 收进 `NirikoLog` 接口（`docs/ios-port/02-dependency-migration-map.md` 已列同类项）。
- **效果**｜产品无变化；产出是 CI 上一条「commonMain 边界检查」失败提示（例如 `data/calculator/X.kt 新增了 android.util.Log`）。
- **参考**｜`xiaoyvyv/bangumi@master:README.md` [README] KMP + Compose Multiplatform 的模块分层（`shared/core`/`shared/data`/`features/`，Android/iOS/Desktop 同源）；本项目 `docs/ios-port/07-commonmain-candidates.md` [文档级证据]。
- **成本/风险**｜L｜真正的移植是 L；本条只做边界检查，**不要**把「跨端」理解成「这轮要出 iOS 版」。
- **优先级**｜P2

### 11.5 触控笔 / 多点触控
- **现状**｜部分｜`ui/common/ImageViewer.kt` 用 telephoto `ZoomableAsyncImage` 支持捏合缩放/双击/平移，且缩放态自动禁翻页；其余交互（日历、长列表、横滑轨道）无笔输入处理——grep `stylus|PointerType|MotionEvent` 在 `app/src/main/java` 下 **0 命中**。
- **提升**｜用手写笔时不误触滚动，日历能从月视图捏到周视图；工程上把「笔 vs 手指」的阈值做成一处常量，所有手势组件共用。
- **原理**｜`awaitPointerEventScope` 里看 `event.type == PointerType.Stylus`（或 `PointerType.Eraser`）→ 提高拖动触发阈值、关掉 fling；日历用 `detectTransformGestures` 做密度档位切换（月/两周/周），与 `LazyColumn` 抢手势时走 `nestedScroll` 而不是并行 `pointerInput`（否则竖直滚动会被吞）。
```kotlin
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            isStylus = e.type == PointerType.Stylus
            if (isStylus) e.changes.forEach { it.consume() }   // 笔不驱动列表滚动
        }
    }
}
```
- **效果**｜笔尖拖动查看器时只平移不翻页；日历双指从月缩到周、再捏回月；S Pen 悬停不触发按压态。
- **参考**｜本项目 `ui/common/ImageViewer.kt` [码]（telephoto `ZoomableImageState` + `HorizontalPager` 互斥的现有做法，是唯一已验证的手势互斥链路）；外部笔输入规范本轮**未取到源码**（[推断]）。
- **成本/风险**｜S｜`consume()` 过度会让笔完全失效；折叠屏/平板与手机要分别验证。
- **优先级**｜P2

### 11.6 跨设备实时同步
- **现状**｜部分｜WebDAV 为手动/前台触发（`data/sync/SyncManager.kt`，自动同步有节流与退避），无长连接、无推送、无增量协议；两台设备同时改同一集时靠 LWW 决定。
- **提升**｜**建议不做**。收益是「另一台设备 3 秒内看到」，代价是一整套 HLC/merkle/冲突合并 + WebDAV 轮询（仍不是真实时）。可替代收益是「回前台自动拉一次 + 状态可见」——这已经存在，只差 UI 表达（同步后显示「已是最新 · 2 分钟前」）。
- **原理**｜若未来翻案，业界做法是 HLC + merkle 树增量合并：两台设备交换 merkle 根，只拉差异子树，再逐条 LWW。纯客户端下没有「服务端权威时钟」，HLC 只能靠设备时钟 + 漂移容忍；而 WebDAV 没有 diff API，仍要整文件比对。
```ts
// actual 的时钟模型（64-bit hybrid logical clock）
export type Clock = { timestamp: Timestamp; merkle: TrieNode }
```
- **效果**｜若只做「状态可见」：回到前台后同步条依次显示 `同步中 → 已是最新 · 2 分钟前`；冲突条数 >0 时点开看明细（7.12）。不做实时同步时，设置页明确写「同步在你打开 app 或手动下拉时进行」。
- **参考**｜`actualbudget/actual@master:packages/crdt/src/crdt/timestamp.ts` [码] `Timestamp`（HLC、`makeClientId`、`serializeClock/deserializeClock`、5 分钟时钟漂移容忍）；同目录 `merkle.ts`（存在，未细读）。
- **成本/风险**｜L｜做真同步需要改 WebDAV 布局（每设备一个 op log），旧备份不兼容；不做则要在文档里写清楚「非实时」。
- **优先级**｜P2（结论：不做，只补状态可见）

### 11.7 焦点/方向键可达性（合并自 12.6）
- **现状**｜无｜全仓 `focusRequester|onKeyEvent|KeyEvent|focusable(` 仅 **15 处/4 个文件**（`ui/components/GlassAnchoredMenu.kt` 6 处、`ui/subject/DetailPosterCard.kt` 4 处、`ui/subject/SubjectDetailScreen.kt` 3 处、`ui/common/IosStyleSearchComponent.kt` 2 处）；没有 Tab 序审计、没有焦点可见样式、没有 Esc 关闭约定。
- **提升**｜外接键盘/平板触控板的用户能 Tab 走完一屏并看见焦点在哪；工程上把「可聚焦 → 有可见焦点环」做成组件默认行为，不再逐个补。
- **原理**｜三条组合拳：
```kotlin
Modifier
    .focusGroup()                                   // 一行/一列算一个 Tab 停靠点
    .focusProperties { canFocus = visible }         // 装饰元素不进 Tab 序
    .focusable()
    .onFocusChanged { focused -> ... }              // 焦点环 2dp + 玻璃高亮
```
  键盘方向键在同一 `focusGroup` 内移动；`onPreviewKeyEvent` 拦 `Escape`（关弹层）、`DirectionRight/Left`（横滑轨道）。注意：`traversalIndex` 只影响读屏顺序、**不影响**键盘 Tab 序（Tab 序 = 组合顺序）。
- **效果**｜Tab 路径「顶栏 → 筛选条 → 列表卡 → 底栏」；焦点有 2dp 主色描边（复用 `ui/components/GlassControls.kt` 的按压视觉）；Esc 关弹层并把焦点还给触发按钮；横滑轨道里方向键逐卡移动且自动滚动到可见。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt` [码]（`LazyListState` + item key 的列表结构，适合作焦点容器边界）；Compose 焦点 API 本轮**未读源码**（[推断]，API 名以官方文档为准）。
- **成本/风险**｜M｜与横向滚动、玻璃按压动画、共享元素转场都有交互，需真机键鼠验证；定位边界不做 TV/D-Pad（附录 B-22），这里只保证「外接键盘可用」。
- **优先级**｜P2

### 11.8 深浅色与大字体布局回归
- **现状**｜部分｜有 `ui/adaptive/WindowClass.kt` 三档与两栏详情（`AdaptiveDetailScaffold.kt`），但 `app/src/androidTest` **0 文件**、无截图基线、无「最大字体 × 窄屏」清单；大字体导致的裁切只能靠用户报。
- **提升**｜把「大字体下按钮被裁」从口头 bug 变成可复现的 CI 图；工程上 5 页 × 2 主题 × 2 字号 × 3 宽度 = 60 张基线，改动一眼可见。
- **原理**｜JVM 截图测试（不依赖真机）：Roborazzi/Paparazzi 对 `@Preview` 渲染，参数矩阵用 `@Config(qualifiers = "w320dp-h640dp-xhdpi")` + `fontScale`；产物入库做 golden，差异阈值 0.1% 容忍抗锯齿。
```kotlin
@Test fun detail_largeFont_dark() = paparazzi.snapshot {
    NirikoTheme(dark = true) { SubjectDetailScreen(previewState, fontScale = 2f) }
}
```
- **效果**｜CI 出 60 张图；PR 里改圆角/间距会看到 diff；出图失败即失败。用户可见收益是「设置里字号调最大后，筛选 chip 与底栏不再被裁」。
- **参考**｜`takahirom/roborazzi`（1.1k，未读源码，仅确认仓库存在）[推断]；本项目无既有截图测试，属于从 0 建基线。
- **成本/风险**｜M｜首次基线要人工审一遍（否则把 bug 固化）；字体渲染在不同 JDK/系统上有差异，需固定 CI 镜像。
- **优先级**｜P1

### 11.9 系统分享目标的完整覆盖
- **现状**｜部分｜`ui/share/ShareFlow.kt` 只有一条通道：`buildShareIntent(imageUri, summaryText)` → `ACTION_SEND`、`type = "image/png"`、`EXTRA_STREAM` + `EXTRA_TEXT`，再 `Intent.createChooser`。没有「只分享文字/链接」，也不检查有没有接收方。
- **提升**｜用户能「只发链接给朋友」而不是每次都发一张 PNG；工程上按载荷类型拆 intent，接收方兼容性更好（有些应用只吃 text/plain）。
- **原理**｜三条通道 + 一次可达性检查：
```kotlin
fun textIntent(title: String, url: String) = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "$title\n$url")
}
// 分享前：packageManager.queryIntentActivities(intent, 0).isNotEmpty()
```
  图片通道保持现状但加 `clipData`（部分接收方靠它拿权限）；长按分享按钮弹三选：`分享图片 / 只分享文字 / 复制链接`。
- **效果**｜分享按钮长按出现三个选项；「只分享文字」进系统面板时只显示能接文本的应用；接收方为 0 时给「已复制到剪贴板」而不是空选择器。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/core/util/ShareHelper.kt` [码] `shareMangaLink` / `shareMangaLinks` / `shareCbz` / `shareImage` / `getShareTextIntent`（`ShareCompat.IntentBuilder` 按载荷分四条通道）。
- **成本/风险**｜S｜`FileProvider` URI 权限要跟着 `clipData` 给；「复制链接」需要稳定的站内链接生成（无服务端时用 bgm.tv 条目 URL）。
- **优先级**｜P1

### 11.10 应用内帮助 / 上手引导
- **现状**｜部分｜只有首次启动单页填 TMDb Key（`ui/onboarding/FirstRunScreen.kt`，右上常驻「跳过」，注释写明「将来多页时每页都要保留跳过」），由 `data/settings/FirstRunPolicy.kt` 判定是否展示；巡礼/权威评分/刷新诊断/外部绑定这些非直觉功能**没有任何说明入口**。
- **提升**｜用户第一次看到「争议度 1.32」或「灰通道」时能就地看解释，不用去翻文档；工程上帮助内容数据化，加功能只加一条 topic，不改 UI。
- **原理**｜`data class HelpTopic(id, title, body, relatedRoute)`，内置在 `assets/help/*.md`（离线、不发网络）；设置 → 关于 → 「使用帮助」列出 12 条；任意详情区块**长按标题** → 「这是什么」直接打开对应 topic（与 6.19 的脚注共用长按手势，二选一或长按出菜单）。
- **效果**｜设置里多「使用帮助」入口；首次进入巡礼/权威评分区块时，标题右侧出现一次性 `?` 小点（看过即消失，记 DataStore）；帮助页可按关键词过滤。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/presentation/more/onboarding/OnboardingScreen.kt` [码] `steps = remember { listOf(ThemeStep(), StorageStep(), PermissionStep(), GuidesStep(onRestoreBackup)) }` + `isLastStep` + `canAccept = steps[currentStep].isComplete` 的「每步带完成门槛」模型。
- **成本/风险**｜S｜帮助文案会过期（功能一改就错），要跟功能 PR 一起改；不要做「一次性全屏教学」打扰老用户。
- **优先级**｜P1

### 11.11 崩溃与错误上报的本地化
- **现状**｜无｜`NirikoApplication` 没有 `setDefaultUncaughtExceptionHandler`；错误只进 Logcat 与 `ui/settings/pages/RefreshDiagnosticsScreen.kt`（刷新诊断，非崩溃）。用户反馈崩溃时只能口述「打开某页就闪退」。
- **提升**｜崩溃后下次启动能看到「上次异常退出」摘要并一键复制，维护者不用远程日志也能定位；工程上 100 行以内，纯本地、不上传，与本项目「无服务端」定位一致。
- **原理**｜`CrashLogStore`：
```kotlin
val prev = Thread.getDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler { t, e ->
    runCatching {
        File(filesDir, "crash/last.txt").writeText(
            "time=${System.currentTimeMillis()} ver=${BuildConfig.VERSION_NAME}\n" +
            e.stackTraceToString().take(8_000)
        )
    }
    prev?.uncaughtException(t, e)
}
```
  只保留最近 5 个文件（每个 ≤64KB，超出轮转）；设置 → 诊断显示「上次异常：2026-10-15 21:03 · NullPointerException @ SubjectDetailViewModel.kt:412」+「复制全文」+「分享」。
- **效果**｜崩溃后重启，设置页出现红点；点开是摘要 + 折叠的堆栈；复制后可直接贴进反馈。无崩溃时该入口隐藏（不制造噪音）。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/core/ui/dialog/ErrorDetailsDialog.kt` [码] `textViewSummary.text = exception.message` + neutral 按钮 `copyToClipboard(exception.stackTraceToString())`；同仓 `core/util/AcraScreenLogger.kt` [码]（ACRA 屏幕日志，可看「最后一屏」思路）。
- **成本/风险**｜S｜堆栈可能含 URL/标题（隐私），复制前提示「可能包含你浏览过的条目名」；不要写 `/sdcard`，只写 `filesDir`。
- **优先级**｜P1

## 9.新增 本域新增候选（发散 · 9.3–9.5）

> 说明：模板写作「9.9」，但 9.9 不存在且有歧义，故用「9.新增」；编号接 9.2 之后。

### 9.3 详情页首帧预算门禁
- **现状**｜无 · 新提出｜`docs/performance-audit.md` 把详情页列为 P0 开销（整页 backdrop + 1080px hero），`docs/phone-shared-motion-profile.md` 的非 Debug 首帧样本 62.84ms；但没有任何「首帧必须 < X ms」的自动约束。
- **提升**｜为什么是 Niriko：详情页是核心落地页，且它的开销结构（玻璃 backdrop + 大图 + 12 路并发）不会自己变好；把 62.84ms 变成 CI 阈值，才防得住后续加部件的累积。
- **原理**｜benchmark 里加 `TraceSectionMetric("detailFirstFrame", Mode.SUM)`，在 `SubjectDetailBody` 首次组合完成处 `Trace.beginSection("detailFirstFrame")`/`endSection()`；阈值取现有实测 ×1.2（≈75ms），超阈值失败并打印快照。
- **效果**｜报表多一行「详情页首帧 71ms（预算 75ms）」；超了 PR 直接红。
- **参考**｜`androidx/androidx@androidx-main:benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/Metric.kt` [码] `TraceSectionMetric`（文件内注释给了自定义 trace 查询的用法）。
- **成本/风险**｜M｜首帧在不同设备差异极大，阈值要按机型分档或只用「相对基线 ±%」。
- **优先级**｜P1

### 9.4 用户可导出的性能日志
- **现状**｜无 · 新提出｜无服务端意味着「用户说卡」时拿不到任何现场；`docs/phone-shared-motion-profile.md` 的 trace 只能本机做。
- **提升**｜为什么是 Niriko：这是纯客户端项目唯一可行的「现场回收」方式——用户主动导出 txt，而不是后台上报。
- **原理**｜与 11.11 的 `CrashLogStore` 同一个目录约定：`JankRecorder` 仅在用户开启「记录性能」时挂 `Choreographer`，>32ms 的帧记一条 `ts|scene|frameMs`，环形 500 条写 `filesDir/perf/jank.log`；导出走 `ShareFlow` 的 text/plain 通道（11.9）。
- **效果**｜设置 → 诊断 → 「导出性能日志」生成 `jank-2026-10-15.txt` 走系统分享；日志里只有帧时长与场景名，没有条目标题。
- **参考**｜`androidx/androidx@androidx-main:benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/Metric.kt` [码] `FrameTimingMetric`（定义了什么算 jank）；本地 `ui/share/ShareFlow.kt` [码]（分享通道）。
- **成本/风险**｜S｜常驻采样会耗电，必须默认关闭并显示「正在记录」常驻通知；脱敏要彻底。
- **优先级**｜P2

### 9.5 启动阶段预算
- **现状**｜无 · 新提出｜冷启动已做了「两条远端链合并 + 延后首帧」（`NirikoApplication.kt:101-112`，`STARTUP_REMOTE_DELAY_MS`），但没有量化「首帧多少 ms」。
- **提升**｜为什么是 Niriko：启动时同时有 3 个 ViewModel 的刷新 + WorkManager 注册 + 拼音回填（`backfillPinyinKeys`），任何一项挪到主线程都会拖慢首帧，而目前没有护栏。
- **原理**｜`StartupTimingMetric()` + `StartupMode.COLD`，跑 5 次；同时对比「远端链开启/关闭」两组（用测试专用开关），确认延后策略真的有效。
- **效果**｜报表 `timeToInitialDisplay p50=…ms`、`timeToFullDisplay p50=…ms`；若某版本 +30ms 立刻可见。
- **参考**｜`androidx/androidx@androidx-main:benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/Metric.kt` [码] `StartupTimingMetric`；`.../macro/junit4/MacrobenchmarkRule.kt` [码] `startupMode` 参数。
- **成本/风险**｜S｜冷启动指标噪声大（系统缓存/后台），需要 ≥5 次取中位数。
- **优先级**｜P2

## 11.新增 本域新增候选（发散 · 11.12–11.17）

> 说明：模板写作「11.9」，但 11.9 已是既有条目，故用「11.新增」。

### 11.12 通知渠道与免打扰
- **现状**｜无 · 新提出｜`data/notification/AiringReminderScheduler.kt:38` 只建**一个** `NotificationChannel`，所有放送提醒走同一条；grep 全仓无 quiet hours/免打扰/snooze 逻辑（`NotificationChannel` 仅此 3 处命中）。
- **提升**｜为什么是 Niriko：放送提醒是本项目少数「主动打扰」的功能，一个渠道意味着用户只能全关或全开；分渠道 + 夜间静默能把「关掉提醒」变成「只在白天提醒」。
- **原理**｜三渠道（在看更新/想看开播/系统，后者 IMPORTANCE_LOW 只用于同步失败等）+ 静默区间 `settings.airingQuietHours: String = "23:00-08:00"`：Worker 里命中区间则 skip 并记 `skipped_reason=quiet`（刷新诊断可见），不补发；通知加 `Action`「1 小时后」→ `OneTimeWorkRequest(delay=1h, ExistingWorkPolicy.REPLACE)`。
- **效果**｜系统设置里出现两个 Niriko 渠道，可分别关；23:00 后不再响铃，早上首次进 app 的「今日更新」里能看到跳过的集；通知上多一个「1 小时后」按钮。
- **参考**｜本地 `AiringReminderScheduler` [码]（现有单渠道创建与 `schedule/cancel`）；外部渠道分级做法**未取到源码**（[推断]）。
- **成本/风险**｜S｜渠道一旦创建，IMPORTANCE 用户改过就不能程序改回（Android 限制），所以初次就要定对等级。
- **优先级**｜P1

### 11.13 Doze / 省电限制下的事实与降级
- **现状**｜无 · 新提出｜提醒完全依赖 `AiringReminderScheduler.schedule`（WorkManager 周期任务）；grep `AlarmManager|setExact|Doze` **0 命中**，也没有「电池优化白名单」引导。厂商后台限制会让「23:00 更新」变成第二天早上。
- **提升**｜为什么是 Niriko：纯客户端 + 无推送通道，后台被限 = 提醒失效；给用户一条「为什么我没收到」的可解释路径，比再加一个提醒功能更重要。
- **原理**｜设置里一行状态：`PowerManager.isIgnoringBatteryOptimizations(pkg)` 为 false 时显示「后台受限 · 提醒可能延迟 1–3 小时」+ 跳 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；对时效敏感的单集提醒可降级用 `AlarmManager.setExactAndAllowWhileIdle`，但必须先检查 `canScheduleExactAlarms()`（Android 12+），不行就继续用 WorkManager 并在文案里说明。
- **效果**｜设置 → 通知里出现「后台限制：受限」黄条；点击去系统页；允许后条变绿「不受限」；受限时提醒文案带「可能延迟」。
- **参考**｜本地 `data/notification/AiringReminderScheduler.kt` [码]（WorkManager 现状）；精确闹钟与 Doze 行为属平台文档，**未取到源码级参考**（[推断]）。
- **成本/风险**｜M｜`SCHEDULE_EXACT_ALARM` 在 Android 14 起默认拒绝，上架审核要说明用途；不要为了提醒强推精确闹钟。
- **优先级**｜P1

### 11.14 成为分享接收方（从别的 app 分享链接给 Niriko）
- **现状**｜无 · 新提出｜`AndroidManifest.xml` 的**内容型** intent-filter 只有 `.nirikotheme` 的 `ACTION_VIEW`（另有开机广播 receiver，与分享无关）；没有 `ACTION_SEND` text/plain，也没有 `bgm.tv/subject/*` 的 VIEW 过滤，所以浏览器/群里分享的链接进不来。
- **提升**｜为什么是 Niriko：纯客户端最自然的「外部入口」就是系统分享；用户从群里看到 `bgm.tv/subject/328609` 一键发给 Niriko 就能看自己的进度，不需要复制粘贴到搜索框。
- **原理**｜`<intent-filter>` 加 `ACTION_SEND` + `text/plain`，以及 `ACTION_VIEW` + `https` 的 `bgm.tv`/`bangumi.tv` `/subject/` 路径；`MainActivity` 里 `SubjectIdParser.fromUrl(text)` 抽 id（复用现有 `extractFirstUrl` 思路）→ 直达详情；抽不到就把整段填进搜索框。
- **效果**｜从浏览器「分享到 Niriko」直接进详情页；从别处分享一段含链接的文本也能识别；无法识别时打开搜索页并预填。
- **参考**｜本项目 `AndroidManifest.xml` 的 `.nirikotheme` VIEW filter 与 `<queries>` [码]（同一机制扩展）；`ui/share/ShareFlow.kt` [码]（分享出，反向即收）。
- **成本/风险**｜S｜`singleTop` + 已在前台时要走 `onNewIntent` 而不是重建；别把任意 https 都收进来（只收 bgm.tv 域）。
- **优先级**｜P1

### 11.15 外接鼠标悬停与右键
- **现状**｜无 · 新提出｜全仓无 `pointerHoverIcon`/`PointerEventType.Enter`/`PointerButton.Secondary`；平板/折叠屏已被支持（`WindowClass`），但鼠标进来没有任何反馈。
- **提升**｜为什么是 Niriko：平板是已投入的形态（两栏详情、竖向 dock），鼠标悬停/右键是「桌面化」最便宜的一步，且菜单组件已经存在。
- **原理**｜`Modifier.onPointerEvent(PointerEventType.Enter/Exit) { hovering = true }` 做 2dp 抬升 + `pointerHoverIcon(PointerIcon.Hand)`；右键 `event.buttons.isSecondaryPressed` → 弹 `GlassAnchoredMenu`（现有组件），菜单项：加入收藏 / 分享 / 隐藏该条目。
- **效果**｜鼠标悬停卡片微抬升并有手型；右键弹出玻璃锚定菜单；触屏设备完全无变化。
- **参考**｜本项目 `ui/components/GlassAnchoredMenu.kt` [码]（锚定菜单已有）；Compose 指针 API 本轮**未读源码**（[推断]）。
- **成本/风险**｜S｜hover 状态变化会触发重组，长列表里要只对可见项生效。
- **优先级**｜P2

### 11.16 边到边（edge-to-edge）与 insets 回归
- **现状**｜无 · 新提出｜当前 targetSdk 34；升到 35 后系统强制 edge-to-edge，现有 `ui/adaptive/AdaptiveInsets.kt` + 各页 `systemBarsPadding` 有双计风险（详情页顶栏、底栏胶囊、日历全屏三处最敏感）。
- **提升**｜为什么是 Niriko：这不是新功能，是**升级阻塞项**——玻璃 UI 对「内容压到状态栏下」极其敏感，一旦双计会出现顶部内容被顶栏遮、底栏悬空。
- **原理**｜`enableEdgeToEdge()` 统一在 Activity；insets 只在一层消费：容器用 `WindowInsets.systemBars` 生成 `PaddingValues` 传给列表 `contentPadding`，子组件不再各自 `systemBarsPadding`；分叉处用 `WindowInsetsCompat` 判断 API 级别；键盘 insets 用 `imePadding()` 而不是手写。
- **效果**｜升级后状态栏/导航条区域内容不被遮、玻璃底栏贴底不留白；横屏与折叠屏同样正确。
- **参考**｜本项目 `ui/adaptive/AdaptiveInsets.kt` [码]（现有 insets 封装）；外部无源码级参考（[推断]）。
- **成本/风险**｜M｜每页都要回归（4 个顶级页 + 11 个设置页 + 详情/单集/人物）；建议与 11.8 的截图基线一起做。
- **优先级**｜P1（升 targetSdk 前必做）

### 11.17 隐私自述页（网络行为清单）
- **现状**｜无 · 新提出｜项目有 23 个数据源 + 灰通道探针（`data/probe/`）+ 反代端点切换，但设置里没有一处告诉用户「会连哪些域、为什么、能不能关」。
- **提升**｜为什么是 Niriko：「纯客户端、无服务端」是核心卖点，但用户无法验证；一张静态清单成本极低，却能回答最常见的质疑（「它是不是在上传我的收藏」）。
- **原理**｜从 `data/remote/**` 的 baseUrl 常量与 `plugin/**` 的端点用脚本生成 `docs/network-domains.md`，再内嵌为设置页「关于 · 网络与隐私」的静态内容（写死不请求）；每条列：域、用途、是否可关（如灰通道/豆瓣可关）、是否带 key。
- **效果**｜设置 → 关于 → 「网络与隐私」列出约 12 个域 + 一句用途 + 「可在数据源设置中关闭」的链接；页面顶部一句「Niriko 没有服务端账号，不同步到我们」。
- **参考**｜本项目 `AndroidManifest.xml` 的 `<queries>` 与 `data/probe/` [码]（现有网络面）。
- **成本/风险**｜S｜清单会随源变更过期，需与「新增数据源」的 PR 模板挂钩。
- **优先级**｜P2

## 本域新增参考库（合并 9 / 11）

| 仓库 | 许可 | star（截至 2026-10） | 语言 | 为何与本项目相关 | 已验证文件 |
| --- | --- | --- | --- | --- | --- |
| `androidx/androidx`（Glance / Window / Benchmark 三个模块） | Apache-2.0（AndroidX 统一） | 未取到（shields 对 monorepo 返回 invalid） | Kotlin | 11.1 桌面组件、11.3 折叠姿态、9.2/9.3/9.5 性能观测的唯一权威实现 | `glance/glance-appwidget/src/main/java/androidx/glance/appwidget/GlanceAppWidget.kt` · `.../GlanceAppWidgetReceiver.kt` · `window/window/src/main/java/androidx/window/layout/FoldingFeature.kt` · `benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/Metric.kt` · `.../MacrobenchmarkScope.kt` · `benchmark/benchmark-macro-junit4/.../MacrobenchmarkRule.kt` |
| `mihonapp/mihon` | Apache-2.0 | 24k | Kotlin | 11.10 的步进式 onboarding、11.2 的可点信息密度参照 | `app/src/main/java/eu/kanade/presentation/more/onboarding/OnboardingScreen.kt` · `.../presentation/manga/MangaScreen.kt` |
| `KotatsuApp/Kotatsu` | GPL-3.0 | 8.9k | Kotlin | 11.9 按载荷拆分享、11.11 错误详情复制、11.2 AssistContent | `app/src/main/kotlin/org/koitharu/kotatsu/core/util/ShareHelper.kt` · `.../core/ui/dialog/ErrorDetailsDialog.kt` · `.../core/util/AcraScreenLogger.kt` · `.../details/ui/DetailsActivity.kt` |
| `actualbudget/actual` | 未取到 | 未取到（未取） | TypeScript | 11.6 若翻案做实时同步，其 HLC + merkle 是最贴近「本地优先 + 多设备」的参考 | `packages/crdt/src/crdt/timestamp.ts`（`Timestamp`/`makeClientId`/`serializeClock`）· `packages/crdt/src/crdt/merkle.ts`（存在，未细读） |
| `takahirom/roborazzi` | 未取到 | 1.1k | Kotlin | 11.8 大字体/深色截图基线（JVM 渲染，无需真机） | 未读源码（仅确认仓库与 star）→ [推断] |
| `NyanTV/NyanTV` | 未取到 | 未取到 | Kotlin | 曾作为 11.7 的候选（TV/D-Pad），**本轮核实为手机播放器应用、未见焦点系统**，故未采用 | 文件树 252 文件（`/app/src/main/java/com/nyantv/**`，无 focus/TV 相关文件） |

## 计数自检

- v1 条目：9 域 **2** 条（9.1、9.2，按要求并入本文件）+ 11 域 **11** 条（11.1–11.11）→ 详解 **13** 条，编号与名称未改。
- 11.7 标题已注「合并自 12.6」，12 域不再单列（避免重复条目）。
- 新增候选：9 域 **3** 条（9.3–9.5）+ 11 域 **6** 条（11.12–11.17）= **9** 条。
- 汇总表行数：9 域 2 + 3 = **5**；11 域 11 + 6 = **17**；与详解条数一致。
- 结论性判断：11.6 建议**不做**（保留「回前台同步 + 状态可见」）；9.1 建议**不自研 HUD**（用 9.2/9.3 的 benchmark 替代）。
- 未能取证、标待核的地方：11.8 的 roborazzi 实现方式；11.12/11.13/11.15/11.16 的外部源码级参考（均为平台行为，只有官方文档级依据）；`androidx/androidx`、`actualbudget/actual`、`NyanTV/NyanTV` 的 star/license（shields 返回 invalid 或未取，未编造）。
- 性能数字全部引自现有文档（`docs/phone-shared-motion-profile.md`、`docs/performance-audit.md`），本轮**未重测**。
