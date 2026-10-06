# M3 Expressive 逐组件比对与迁移清单（P5 · R8）

> **参考仓库**：emertozd/Compose-Material-3-Expressive-Catalog（无 LICENSE 文件 → **只比对、不抄任何代码**；本轮仅读取其 README 与文件清单作为「组件索引」，**未读取任何 sample 源码**）。
> **本轮性质**：纯文档交付，**不改任何 Kotlin 源码、不改 gradle 版本目录、不新增依赖**。
> **产出**：8 项控件的比对表 + 带优先级的迁移清单，每一项都有 M3 官方文档依据。
> **调查日期**：2026-10-01。

---

## 〇 结论（先看这里）

1. **关键前提——我们的 material3 是 1.4.0**：`gradle/libs.versions.toml:8` 的 `composeBom = "2025.12.01"` 解析出 `androidx.compose.material3:material3-android:1.4.0`（证据：Gradle 缓存 `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\androidx.compose.material3\material3-android\1.4.0\6064b108969ab5557a0c19c7e026554f377cecf5\material3.aar`，`classes.jar` 共 1378 项）。**所有「能不能做」的判断都以这个实际产物为准，不以官方文档/浏览器示例为准。**
2. **参考仓库是「新」的**：它是 AndroidX Compose Material3 Catalog 的独立抽取版，用 `material3 1.5.0-alpha26` + Compose UI 1.13.0-alpha01（其 README 自述）。因此它列举的组件里，**有一部分我们的 1.4.0 根本没有**（只有 tokens，没有 composable）。
3. **8 项比对结论**：**4 项建议迁移**（2 项 P0、2 项 P1，全部零新依赖、1.4.0 已具备）、**1 项保留自研**（底部导航）、**3 项推迟**（需 material3 1.5.0-alpha）。
4. **不建议升级 material3 到 alpha**：最新已发布版本是 `1.5.0-alpha29`（119 个版本号，见 `https://dl.google.com/android/maven2/androidx/compose/material3/material3/maven-metadata.xml`），升它会连带把 Compose UI 顶到 1.13.0-alpha 级别，与当前 Kotlin 2.3.10 + BOM 2025.12.01（Compose UI 1.10.3）组合是未验证的。**推迟项一律等 1.5.0 stable。**
5. **P0 两项已在 m01556 批次实现**：① 进度指示器——旧重载在 1.4.0 标的是 `@Deprecated(level = HIDDEN)`，**HIDDEN 不参与重载解析**，所以源码一直绑定在带 `gapSize` 的新重载上，gap + 停止点本来就是默认值；真正要改的只有 9 处行内小 spinner（直径 14–20dp，默认缺口会吃掉整圈）→ 显式 `gapSize = 0.dp`。② 全局动效——`MaterialTheme.motionScheme` 这条路在 1.4.0 **不存在**（`MotionScheme` 与 `MaterialTheme.motionScheme` 均为 `internal`，引用直接编译失败），改为按字节码实测值自建 `ui/animation/NirikoMotionSpecs.kt` 复刻 StandardMotionTokens 六档弹簧，8 文件 12 处替换完成。

---

## 一 方法与证据来源

| # | 证据 | 具体来源 |
|---|---|---|
| 1 | 参考仓库定位与版本 | 仓库 README（`raw.githubusercontent.com/emertozd/Compose-Material-3-Expressive-Catalog/main/README.md`）：自述「extracted from the AndroidX repository」，Used Versions = material3 **1.5.0-alpha26** / Compose UI **1.13.0-alpha01** / material3-adaptive 1.3.0；无 LICENSE 文件 |
| 2 | 组件索引 | 该仓库 `app/src/main/java/com/emertozd/compose/catalog/samples/*.kt` 共 **49 个** sample 文件（ButtonGroup / LoadingIndicator / MaterialShapes / SplitButton / FloatingToolbar / FloatingActionButtonMenu / DragHandle / Carousel / SegmentedButton / ToggleButton / Typography …），仅作为「哪些东西属于 M3 Expressive」的目录使用 |
| 3 | **版本能力（决定性证据）** | 本机 1.4.0 aar 反编译：`javap -v -p -classpath <classes.jar 解包目录>` 列出真实 composable 与**参数名**（Kotlin 把签名串写进常量池，例如 `LinearProgressIndicator(modifier, color, trackColor, strokeCap, gapSize)`） |
| 4 | 官方规范依据 | `m3.material.io` 逐条 URL，2026-10-01 实测 HTTP **200**（详见各项「依据」列） |
| 5 | 本工程现状 | `app/src/main/java` 下 **407** 个 `.kt`；组件使用计数见 §二 末表 |

**已实测 404 的 URL（不要引用）**：`/components/floating-toolbar/overview`、`/components/toggle-button/overview`、`/components/toggle-buttons/overview`、`/components/docked-toolbar/overview`、`/styles/shape/overview`。浮动工具栏与开关按钮的官方入口是 `/components/toolbars/overview` 与 `/components/icon-buttons/overview`。

---

## 二 版本能力矩阵（我们能不能用）

| M3 Expressive 形态 | 1.4.0（当前）实际内容 | 可用性 |
|---|---|---|
| 进度指示器 gap + 停止点 | `LinearProgressIndicator(modifier, color, trackColor, strokeCap, gapSize)`、`CircularProgressIndicator(progress, modifier, color, strokeWidth, trackColor, strokeCap, gapSize)` | ✅ **可直接用**；旧重载带 deprecation 文案「Use the overload that takes \`gapSize\`…」 |
| 动效方案 motionScheme | `MotionScheme` 接口（`defaultSpatialSpec / fastSpatialSpec / slowSpatialSpec / defaultEffectsSpec / fastEffectsSpec / slowEffectsSpec`）+ `MaterialTheme.motionScheme` + `ExperimentalMaterial3ExpressiveApi` | ❌ **外部模块不可用**：`MotionScheme` 与 `MaterialTheme.motionScheme` 都是 `internal`（源码实测 `Cannot access 'val motionScheme: MotionScheme': it is internal in 'androidx.compose.material3.MaterialTheme'`；`ExperimentalMaterial3ExpressiveApi` 同样是 internal，连 `@OptIn` 都引用不到），`Companion.standard()` / `expressive()` 也是模块内可见（javap 的 `standard$material3()` / `expressive$material3()`）→ 规格值改为按字节码实测自建（见 §四 P0-B 实现段） |
| Slider 轨道/圆钮槽位 | `Slider(value, onValueChange, modifier, enabled, onValueChangeFinished, colors, interactionSource, steps, thumb, track, valueRange)` | ✅ 可用（`thumb`/`track` 槽位在） |
| Switch 圆钮内容 | `Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors, interactionSource)` | ✅ 可用 |
| 导航栏 ShortNavigationBar | `ShortNavigationBar` / `ShortNavigationBarItem`（`ShortNavigationBar.kt`）+ `WideNavigationRail` | ✅ 存在（Expressive 导航形态） |
| 轮播 Carousel | `androidx.compose.material3.carousel`：`Carousel`、`CarouselState`、`Keyline`、`KeylineList`、`Strategy` … | ✅ 存在（本项目未用） |
| 分段按钮 / 图标按钮 | `SegmentedButton` / `SingleChoiceSegmentedButtonRow` / `MultiChoiceSegmentedButtonRow`；`IconToggleButton` / `FilledIconToggleButton` / `FilledTonalIconToggleButton` / `OutlinedIconToggleButton` | ✅ 存在 |
| 拖拽把手 / 提示气泡 / 下拉刷新 | `VerticalDragHandle`；`TooltipBox` / `PlainTooltip` / `RichTooltip`；`material3.pulltorefresh.PullToRefreshBox` | ✅ 存在 |
| **Button groups / Split button / Toggle button / 浮动工具栏 / 停靠工具栏 / FAB menu / Loading indicator（波浪）/ MaterialShapes / 弹性 AppBar** | **只有 tokens，没有 composable**：`tokens/ButtonGroupSmallTokens`、`tokens/ConnectedButtonGroupSmallTokens`、`tokens/SplitButton{XSmall,Small,Medium,Large,XLarge}Tokens`、`tokens/FloatingToolbarTokens`、`tokens/LoadingIndicatorTokens`；`ButtonGroupKt` / `SplitButtonKt` / `ToggleButtonKt` / `FloatingToolbarKt` / `LoadingIndicatorKt` / `MaterialShapesKt` **均不存在**；`AppBarKt` 仍只有 `TopAppBar` / `CenterAlignedTopAppBar` / `MediumTopAppBar` / `LargeTopAppBar` | ❌ **需 1.5.0-alpha**（最新 1.5.0-alpha29） |
| 形状令牌 | `Shapes`（M3 经典五档）；**无** `MaterialShapes`（cookie / clover / 变形形状） | ❌ 需 1.5.0-alpha |

> 1.4.0 的 64 个根级 composable 文件（用于判断「有没有」）：AlertDialog、AppBar、Badge、BottomSheetScaffold、Button、Card、Checkbox、Chip、DatePicker、Divider、DragHandle、ExposedDropdownMenu、FloatingActionButton、IconButton、ListItem、Menu、ModalBottomSheet、NavigationBar、NavigationDrawer、NavigationRail、ProgressIndicator、RadioButton、Scaffold、SearchBar、SegmentedButton、**ShortNavigationBar**、Slider、Snackbar、Surface、SwipeToDismissBox、Switch、Tab、TabRow、TextField、TimePicker、Tooltip、**WideNavigationRail**、MaterialTheme、MotionScheme、Shapes、Typography 等。

**本工程组件使用计数（`app/src/main/java`，匹配数 / 涉及文件数）**：

| 组件 | 计数 | 组件 | 计数 |
|---|---|---|---|
| `CircularProgressIndicator` | 31 / 15 | `LinearProgressIndicator` | 13 / 6 |
| `HorizontalDivider` | 25 / 6 | `NirikoShapes.` | 14 / 7 |
| `SkeletonBlock` | 23 / 4 | `skeletonShimmer` | 13 / 5 |
| `RatingBadge` | 12 / 5 | `FavoriteBadge` | 7 / 4 |
| `ModalBottomSheet` | 10 / 4 | `appleGlassCard` | 56 / 24 |
| `GlassToggle` | 5 / 2 | `Switch(` | 4 / 3 |
| `GlassSlider` | 2 / 1 | `Slider(` | 2 / 2 |
| `SearchBar(` | 2 / 2 | `ListItem(` | 2 / 1 |
| `PullToRefreshBox` | 1 / 1 | `Carousel` | 1 / 1 |
| `NavigationBar` / `NavigationBarItem` / `ShortNavigationBar` / `WideNavigationRail` | **0** | `FloatingActionButton` / `SegmentedButton` / `BadgedBox` / `TooltipBox` / `SwipeToDismissBox` / `TabRow` / `DragHandle` | **0** |

（`@OptIn(ExperimentalMaterial3Api|Material3ExpressiveApi|ComponentOverrideApi)` 现有 7 个文件 20 处 —— 引入 Expressive API 的 opt-in 成本已经付过一次，不是新增负担。）

---

## 三 逐项比对表

| # | 现状（本工程） | M3 Expressive 规范形态 | 判定 | 优先级 | 依据 |
|---|---|---|---|---|---|
| 1 | 进度/加载指示器：`CircularProgressIndicator` 31 处 / 15 文件、`LinearProgressIndicator` 13 处 / 6 文件，全部是旧重载（无 gap、无停止点） | 轨道在「已选值」与「轨道末端」之间留 gap，末端画停止点（stop indicator），线宽/圆角按 Expressive 规格 | **迁移** | **P0** | [Progress indicators](https://m3.material.io/components/progress-indicators/overview) · [specs](https://m3.material.io/components/progress-indicators/specs)（1.4.0 已具备 `gapSize`） |
| 2 | 全局动效：`ui/animation/NirikoAnimation.kt` 手写常量 `AnimDurationShort=200`(:20) / `AnimDurationNormal=300`(:23) / `AnimDurationLong=500`(:26) / `AnimEasingDefault=FastOutSlowInEasing`(:31)，被 39 处 `tween(...)`/`spring(...)` 引用（`ui/subject/SubjectDetailScreen.kt`、`ui/adaptive/NirikoNavSuite.kt`、`navigation/NirikoBottomBar.kt:49`、`ui/components/NirikoNonModalSheet.kt`、`ui/stats/NirikoRadarChart.kt`、`ui/animation/RevealOnScroll.kt` …） | `MotionScheme` 六档规格（spatial/effects × fast/default/slow），空间类用弹簧、效果类用缓动 | **迁移** | **P0** | [Motion](https://m3.material.io/styles/motion/overview) · [Easing and duration tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs) · [Transitions](https://m3.material.io/styles/motion/transitions/transition-patterns) |
| 3 | 滑块：`ui/components/GlassControls.kt:128 GlassSlider`（8dp 轨道 / 22dp 白芯圆钮 / 2dp 主题色环）、`:198 GlassRatingSlider` | Expressive slider：轨道内 gap、末端停止点、圆钮 handle（可带 handle bar）；用官方 `thumb`/`track` 槽位承载自绘外观 | **迁移（形态）** | **P1** | [Sliders](https://m3.material.io/components/sliders/overview)（1.4.0 `Slider` 有 `thumb`/`track` 槽位） |
| 4 | 开关：`ui/components/GlassControls.kt:54 GlassToggle`（52×30 轨道 / 24 圆钮 / `spring(dampingRatio = 0.6f, stiffness = 400f)` / 白芯投影） | Expressive switch：轨道 52×32、圆钮 28、选中态可带 `thumbContent` 图标；按压有状态层 | **迁移（几何 + thumbContent）** | **P1** | [Switch](https://m3.material.io/components/switch/overview)（1.4.0 `Switch` 有 `thumbContent`） |
| 5 | 徽标：`ui/components/Badges.kt:44 StatusBadge` / `:77 RatingBadge` / `:104 EpisodeBadge` / `:122 FavoriteBadge`，共 24 处引用；统一黑纱底 `Color.Black.copy(alpha = 0.55f)`、10sp 文字、`padding(8dp, 4dp)` 或 `(8dp, 3dp)`、`RoundedCornerShape(999.dp)` | M3 徽标形态：紧凑高度、8dp 内边距、`labelSmall` 字号、圆角来自 shape 令牌 | **迁移（只对齐令牌）** | **P2** | [Badge](https://m3.material.io/components/badges/overview) |
| 6 | 底部导航：`navigation/NirikoBottomBar.kt` 自研玻璃条（无 `NavigationBarItem`，全仓 `NavigationBar` 使用 = 0） | `ShortNavigationBar`（Expressive 导航栏，指示器为胶囊，图标/标签间距有专门令牌） | **保留自研**（借指示器几何，R9 已做 `TopLevelPageIndicator`） | — | [Navigation bar](https://m3.material.io/components/navigation-bar/overview)（1.4.0 已有 `ShortNavigationBar`，但换掉会推翻玻璃美学与折叠屏 dock） |
| 7 | 形状：`ui/theme/NirikoShapes.kt:48 NirikoShapes`、`:139 squircleCorner(pixelRadius, smoothing)`、`:196 SquircleCornerShape`，14 处引用 | Expressive shape morph：`MaterialShapes` 预置形状（cookie / clover / 花瓣…）与形状过渡 | **推迟** | P2 | [Shape / shape morph](https://m3.material.io/styles/shape/shape-morph)（1.4.0 **无** `MaterialShapes`） |
| 8 | 排版：`ui/theme/Type.kt:23 NirikoTypography`（displayLarge 56sp w700 tnum −0.5sp、headlineLarge 32/40、titleMedium 16/24、titleSmall 15/20、bodyMedium 14/20 +0.2sp …） | Expressive type scale 官方 token 值 | **迁移（仅校准数值）** | **P2** | [Typography](https://m3.material.io/styles/typography/applying-type) · [type scale tokens](https://m3.material.io/styles/typography/type-scale-tokens) |

### 附：明确不迁移的 Expressive 组件

| 组件 | 理由 |
|---|---|
| FAB menu / Floating toolbar / 停靠工具栏 / 弹性 AppBar（`MediumFlexibleTopAppBar` 等） | 全仓 `FloatingActionButton` 使用 = **0**；无停靠工具栏场景；顶栏无重构需求。且 1.4.0 无对应 composable。 |
| Button groups / Split button / Toggle button | 1.4.0 无 composable；分段需求已由 `GlassSegmentedContainer`（`ui/components/GlassControls.kt:108`）+ `ui/common/SearchToolbar.kt` 满足。 |
| Carousel | 1.4.0 有，但现有横滑区（`FlowRow` 40 处、各行 `LazyRow`/Pager）已达目的，换 Carousel 只增加 keyline 复杂度。 |
| Loading indicator（波浪） | 1.4.0 无 `LoadingIndicator`；见 §四 P0-A 的替代路径（gap + 停止点）。 |
| DragHandle / Tooltip / SwipeToDismissBox | 全仓使用 = 0（`ModalBottomSheet` 自带拖拽把手），无迁移对象。 |
| 全量换用官方组件 | `appleGlassCard` 24 个文件、56 处，是本项目视觉身份；Expressive 是形态参考，不是替换目标。 |

---

## 四 迁移清单（按优先级，可直接执行）

### P0-A 进度指示器补齐 Expressive 形态 —— ✅ 已实现（m01556 批次）
- **实测修正（推翻了下面的原计划）**：1.4.0 里旧重载标的是 `@Deprecated(level = DeprecationLevel.HIDDEN)`（常量池有 `Lkotlin/DeprecationLevel;.HIDDEN` 与文案「Use the overload that takes `gapSize`…」），**HIDDEN 级别的 deprecated 不参与重载解析**，所以本工程源码一直绑定在带 `gapSize` 的新重载上 → `gapSize = ProgressIndicatorDefaults.*IndicatorTrackGapSize` 与停止点（`*IndicatorTokens.stopSize`）**本来就是默认值**，无需新增统一入口（`ui/components/NirikoProgress.kt` 未创建）、也无需逐处替换 44 个指示器。
- **唯一真正要改的**：行内小 spinner（14–20dp、`strokeWidth = 2.dp`）——默认缺口是按标准尺寸分配的，在这么小的直径上会吃掉整圈，故显式收紧为 `gapSize = 0.dp`，共 **9 处**：`ui/subject/TmdbBindingSection.kt:353`、`ui/steam/SteamSyncScreen.kt:209`、`ui/subject/ProviderBindingSection.kt:193`、`ui/subject/EpisodeRatingSections.kt:221`、`ui/settings/SettingsItem.kt:328`、`ui/settings/GrayChannelSettingsSection.kt:159`、`ui/settings/pages/DataSourceSettingsScreen.kt:325`、`ui/settings/pages/SyncBackupSettingsScreen.kt:221` 与 `:230`。
- **验收**（已过）：`:app:compileDebugKotlin` 零新增告警（用默认 `gapSize` 不需要任何 opt-in）+ `:app:testDebugUnitTest` → **78 类 / 696 用例 / 0 失败**（进度条无单测，回归靠既有用例）。
- **未做**：真机目视（加载中形态、深色模式对比度）——已随 debug APK 交用户验收。

### P0-B 动效统一到 M3 spatial / effects 规格 —— ✅ 已实现（m01556 批次）
- **决定性阻塞（原计划的「转发 MaterialTheme.motionScheme」在 1.4.0 不可能）**：`androidx.compose.material3.MotionScheme` 与 `MaterialTheme.motionScheme` 均为 `internal`，编译报错 `Cannot access 'val motionScheme: MotionScheme': it is internal in 'androidx.compose.material3.MaterialTheme'`；`@ExperimentalMaterial3ExpressiveApi` 也是 internal（连 `@OptIn` 都引用不到，报 `Cannot access 'annotation class ExperimentalMaterial3ExpressiveApi : Annotation': it is internal in file`）。
- **落地方案**：新增 `ui/animation/NirikoMotionSpecs.kt` —— `object` + 六个**泛型**工厂，值取自 `MotionScheme$StandardMotionSchemeImpl.<clinit>` 的 javap 实测：`spatialFast/Default/Slow = spring(dampingRatio = 0.9f, stiffness = 1400f/700f/300f)`、`effectsFast/Default/Slow = spring(1.0f, 3800f/1600f/800f)`，签名 `fun <T> spatialFast(): FiniteAnimationSpec<T>` 等（泛型是必须的：`slideInHorizontally` 要 `FiniteAnimationSpec<IntOffset>`，`fadeIn/scaleIn` 要 `<Float>`）。material3 1.5+ 公开 `MotionScheme` 后只需改这一个文件，调用点全不动。
- **已替换（8 文件 12 处）**：`ui/animation/RevealOnScroll.kt:56/:61/:66`（alpha→effectsDefault，scale/offset→spatialDefault）、`ui/animation/NirikoAnimation.kt:81/:91/:110/:120`（四个页面过渡工厂改为接收 `specs: NirikoMotionSpecs` 参数）、`navigation/NirikoNavHost.kt:82` + 32 处 `enter/exitSharedAxisZ(specs)`、`ui/adaptive/NirikoNavSuite.kt:110`、`ui/components/NirikoNonModalSheet.kt:100/:139`、`ui/adaptive/AdaptiveDetailScaffold.kt:229`、`ui/settings/SettingsGroup.kt:117`（分组 morph，并删除已无引用的 `RowMorphMillis`/`RowMorphEasing` 常量）、`ui/settings/AdaptiveSettingsPane.kt:267`、`ui/stats/NirikoRadarChart.kt:77`、`ui/subject/SubjectDetailScreen.kt:914/:917`（整页正文入场 `val bodyMotionSpecs` 在 :911）。
- **刻意不动（换官方规格属回退）**：`navigation/NirikoBottomBar.kt:49` 与 `ui/adaptive/NirikoNavSuite.kt:98` 的 180ms tween（跟手 alpha，快了才跟手）、`ui/subject/SubjectDetailScreen.kt:444` 的 350ms 材质渐显（Apple §12 materialize 手调曲线）、`pressSpring`/`appearSpring`/`panelSpring`(`:41/:47/:53`) 与 `tabFadeIn/tabFadeOut`。`ui/theme/Theme.kt` 未加任何 motion 提供者（已回退成原样）。
- **验收**（已过）：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` → **78 类 / 696 用例 / 0 失败**，新增代码零告警。
- **未做**：逐批真机手感确认（spatial 弹簧替代 300/500ms tween 后的列表入场 / 非模态面板 / 页面过渡节奏）——已随 debug APK 交用户；`motionEnabled() == false` 的瞬时语义未变（仍只有 `motionEnabled()` 守卫）。

### P1-A 滑块槽位化
- **落点**：`ui/components/GlassControls.kt:128 GlassSlider`、`:198 GlassRatingSlider`；用 `Slider(..., thumb = {...}, track = {...})`（`SliderDefaults`）承载现有玻璃轨道 + 白芯圆钮，取值数学 `offsetToValue` 与 `steps = 19`（0..10，半步进）**保持不变**。
- **收益**：无障碍语义（`ProgressBarRangeInfo`、拖动增量、TalkBack 播报）由官方实现接管，目前是纯手绘 `pointerInput`。
- **验收**：编译 + 单测 + 真机目视（评分滑块的取值是否仍精确到 0.5、玻璃轨道是否与旧外观一致）。

### P1-B 开关几何与 thumbContent
- **落点**：`ui/components/GlassControls.kt:54 GlassToggle` —— 轨道 52×30 → 52×32、圆钮 24 → 28dp、选中态加 `thumbContent`（可选，评估是否与白芯冲突）。
- **验收**：编译 + 真机目视（玻璃底上的对比度、深色模式）。

### P2-A 徽标令牌对齐
- **落点**：`ui/components/Badges.kt:44/:77/:104/:122` —— 字号 10sp → `labelSmall`、圆角改走 `NirikoShapes` 令牌（目前写死 `RoundedCornerShape(999.dp)`）、内边距统一 8dp/4dp。**黑纱底与配色保持不变**（on-image chip 的行业做法，注释见 :53）。
- **验收**：编译 + 单测（`formatRating` 已有测试覆盖）+ 真机目视（封面上的可读性）。

### P2-B 排版数值校准
- **落点**：`ui/theme/Type.kt:23 NirikoTypography` —— 逐档对比 Expressive type scale 的 fontSize/lineHeight/letterSpacing；**保留** displayLarge 的 56sp + `fontFeatureSettings = "tnum"`（统计数字不跳动，见 :19-20 注释）。
- **验收**：编译 + 单测 + 真机目视（统计页大数字与长标题是否溢出）。

### 推迟项（等 material3 1.5.0 stable）
- `MaterialShapes` 形状变形 → 与 `NirikoShapes` squircle 收益重叠，等 stable 后重估。
- `LoadingIndicator`（波浪）替换 31 处圆形进度 → 波浪形态在密集列表里反而更吵，且需 alpha。
- 其余 1.5.0-alpha 专属组件（Button groups / Split button / Toggle button / 浮动工具栏 / 弹性 AppBar）→ 无使用场景。

---

## 五 本轮边界与遗留

- **未做（P5 文档批次）**：P5 自身未改源码。**P0 两项随后在 m01556 批次实现**（见 §四两节的「已实现」段），真机观感仍待用户验收。
- **未读**：参考仓库的 sample 源码（无 license）。判定依据全部来自 ① 它的 README/文件清单 ② 我们自己的 1.4.0 产物 ③ m3.material.io。
- **已澄清（原「未验证」项）**：`MaterialTheme.motionScheme` 与 `ExperimentalMaterial3ExpressiveApi` **不是 opt-in 问题**——1.4.0 里它们都是 `internal`，源码实测 `Cannot access 'val motionScheme: MotionScheme': it is internal in 'androidx.compose.material3.MaterialTheme'`，@OptIn 同样引用不到注解类，所以该路线被彻底排除，规格值改由字节码实测自建（无需任何 opt-in）。`ShortNavigationBar` 仍属推迟项（等 1.5.0 stable）。
- **下一步**：P0 已完成并产出 debug APK 交用户测试。剩余可选项按 §四优先级：P1-A（滑块槽位化）、P1-B（开关几何 `thumbContent`）、P2-A（徽标令牌）、P2-B（排版数值校准）；3 项推迟项一律等 material3 1.5.0 stable。
