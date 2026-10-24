# 方案 04：动效令牌内聚 + 减少动态效果覆盖

## §A 减少动态效果（reduceMotion）覆盖缺口（HIGH）

### A-0 判定口径（先定机制，否则会把「已被系统接管」误判为缺口）

- Compose 的 tween/spring/Animatable/AnimatedVisibility/Pager 滚动**都读 `MotionDurationScale`**；开发者选项 `animator_duration_scale=0` 时 Compose 自身已把它们压到 0。而 app/src/main/java/com/otakup/niriko/MainActivity.kt:181-187 又把系统关闭并入应用开关（`:195 reduceMotion = settings.reduceMotion || systemAnimatorOff`）。
- **因此真正的缺口只有一种：应用内 `reduceMotion = true`、系统动画正常时仍会播放的动效。**下表的 G1-G20 全部按这个口径筛选。
- 现有守卫只有 **8 个点位 / 8 个文件**：`motionEnabled()` 7 处 —— ui/animation/RevealOnScroll.kt:43、ui/adaptive/NirikoNavSuite.kt:91、ui/adaptive/AdaptiveDetailScaffold.kt:216、ui/components/NirikoNonModalSheet.kt:87、ui/settings/AdaptiveSettingsPane.kt:261、ui/settings/SettingsGroup.kt:115、ui/stats/NirikoRadarChart.kt:72；直读 `LocalReduceMotion` 1 处 —— ui/components/StateComponents.kt:76（交给 ui/components/ShimmerPolicy.kt:30 裁决）。
- 对照体量：全源码命中的动画 API 调用 **约 170 处 / 33 个文件**，被守卫覆盖的只有上述 8 处。
- **承诺边界（决定优先级）**：设置页文案 ui/settings/pages/AppearanceSettingsScreen.kt:207 写「跳过列表/分区的入场动画（系统关闭动画时同样生效）」，ui/theme/Theme.kt:222 写「跳过入场类动画」→ 语义限定在**入场类**。故 G1-G9 = 属于已承诺范围（P0）；G10-G19 = 承诺之外（P1，可做但要同步改文案）；G20 边界存疑（P2）。

### A-1 P0：入场类缺口（已承诺，逐条给改法）

| # | 位置（file:line） | 会动的证据 | 改法 |
|---|---|---|---|
| G1 | app/src/main/java/com/otakup/niriko/navigation/NirikoNavHost.kt:99-100（同型 16 个目标页 / 32 个 lambda：`:126` `:146` `:167` `:208` `:232` `:258` `:281` `:307` `:326` `:344` `:362` `:380` `:400` `:422` `:440`） | `enterTransition = { enterSharedAxisZ(specs) }` / `exitTransition = { exitSharedAxisZ(specs) }`（ui/animation/NirikoAnimation.kt:110-125，fade + scale 0.92）；该文件只有 `:82 val specs = NirikoMotionSpecs`，**无 motionEnabled()** | 在 `NirikoAnimation.kt` 加两个封装：`@Composable fun motionEnter(t: EnterTransition) = if (motionEnabled()) t else EnterTransition.None`、`@Composable fun motionExit(t: ExitTransition) = if (motionEnabled()) t else ExitTransition.None`；16 处写成 `enterTransition = { motionEnter(enterSharedAxisZ(specs)) }`（pop 同步） |
| G2 | app/src/main/java/com/otakup/niriko/MainActivity.kt:292-293（`SharedTransitionLayout { val sharedTransitionScope = this`） | 共享元素总作用域，无守卫 | 作用域本身不产生动画，不需要守卫；守卫下沉到 G3/G4 的 `boundsTransform` |
| G3 | app/src/main/java/com/otakup/niriko/ui/components/CoverImage.kt:76-79（`Modifier.sharedElement(sharedContentState = rememberSharedContentState(sharedElementKey))`，未传 `boundsTransform`/`animationSpec` → Compose 默认 spring） | 跨页封面缩放飞入，全仓 28 处 `sharedElementKey =` 都通过它生效 | 补 `boundsTransform = { _, _ -> if (motionEnabled()) boundsSpring else snap() }`（`snap` 来自 `androidx.compose.animation.core`）；或统一走 05 §S 的 `animateBounds` 现代化改造 |
| G4 | 8 个独立挂载点：app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt:946-951、ui/subject/RelationsSection.kt:115-120、ui/subject/CharacterStaffSection.kt:336-339、ui/person/PersonSubjectCard.kt:53-56、ui/person/PersonDetailScreen.kt:137-140、ui/character/CharacterDetailScreen.kt:105-108 与 `:222-225`、ui/library/LibraryGalleryCard.kt:59-62 | 各自 `with(scope) { Modifier.sharedElement(...) }` | 同 G3：把这些散落的 `boundsTransform` 收敛成一个 `@Composable fun Modifier.nirikoSharedBounds(key: Any)` 帮助函数，内部做 motionEnabled 裁决 |
| G5 | app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt:912-918（`AnimatedVisibility(visible = bodyEntered, enter = fadeIn(bodyMotionSpecs.effectsDefault()) + scaleIn(initialScale = 0.97f, bodyMotionSpecs.spatialDefault()))`，`:911 val bodyMotionSpecs = NirikoMotionSpecs`） | 整页正文入场，标准入场动画 | 与同工程既有做法对齐（`SettingsGroup.kt:115`、`AdaptiveSettingsPane.kt:261` 都有守卫）：`enter = if (motionEnabled()) ... else EnterTransition.None` |
| G6 | app/src/main/java/com/otakup/niriko/ui/common/IosStyleSearchComponent.kt:154 `:159` `:199` `:211` `:218`（`cardSpring` 定义在 `:146`）、`:170-174`、`:176-180`、`:181-185`、`:186-190`、`:378-382`、`:404-408`、`:438-442`、`:576-585` | 5×`animateDpAsState` + 6 处 `tween` + 1 组 `AnimatedVisibility`；**全文件 0 个 motionEnabled()** | 入口级动画，建议 P0：`val motion = motionEnabled()`，把 `cardSpring` 换成 `if (motion) cardSpring else snap()`，`AnimatedVisibility` 走 `EnterTransition.None` |
| G7 | app/src/main/java/com/otakup/niriko/ui/subject/SearchFilterPanel.kt:111-117 与 `:327-333`（`fadeIn(spring(StiffnessMediumLow)) + expandVertically(...)` / `fadeOut + shrinkVertically`，共 8 个 spring） | 面板展开/收起无守卫 | 同上（这也是 04 §B/T9 的令牌收敛点） |
| G8 | app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt:286（AnimatedVisibility）、`:493` `:543` `:594`（`Modifier.animateItem()`）、`:511`（`animateContentSize(tween(300))`）；ui/search/SearchResultsPane.kt:138、`:214-215`；ui/search/DiscoverPosterPane.kt:133；ui/search/TrendingSection.kt:187 | 列表项增删位置动画 + 尺寸动画 | `animateItem`/`animateContentSize` 无 `None` 变体 → 用 `Modifier.animateItem(placementSpec = if (motion) spring(...) else snap(), fadeInSpec = ..., fadeOutSpec = ...)` 与 `animateContentSize(if (motion) tween(AnimDurationNormal, AnimEasingDefault) else snap())` |
| G9 | app/src/main/java/com/otakup/niriko/ui/settings/GrayChannelSettingsSection.kt:330、ui/subject/TmdbBindingSection.kt:176 `:370` `:388`、ui/subject/ProviderBindingSection.kt:129 `:236` `:252`、ui/bilibili/BilibiliSyncScreen.kt:150 | `AnimatedVisibility(visible = ...)` 未给 enter/exit → Compose 默认 fade+expand | 8 处统一补 `enter = if (motion) ... else EnterTransition.None`。**最容易漏，因为默认参数不写字面量** |

### A-2 P1：承诺之外（按压/手势/滚动跟随），列出以便决策

| # | 位置 | 说明 |
|---|---|---|
| G10 | ui/components/GlassControls.kt:66-71、`:73-76` | 开关圆钮 `animateDpAsState(spring(0.6f, 400f))` 与轨道色（后者无 spec） |
| G11 | ui/components/GlassCard.kt:137-141 | 卡片按压高亮 `animateFloatAsState(spring(0.5f, 400f), label = "cardHighlight")` |
| G12 | ui/common/SearchToolbar.kt:170-174 | 分段选中背景 `animateColorAsState(spring(0.6f, 400f), label = "trendTabBg")` |
| G13 | ui/components/TouchGlow.kt:30-51 | 触摸光晕 `Animatable`；docs/ui-upgrade-plan-2026.md:264 对同类持续型动画要求退化，此处未纳入 |
| G14 | ui/subject/SubjectDetailScreen.kt:1742-1745 | 评分数字 1.15→1 弹回 |
| G15 | ui/subject/SubjectDetailScreen.kt:1830-1832 | 状态分段背景 `label = "statusSeg"` |
| G16 | ui/subject/SubjectDetailScreen.kt:1530-1534、ui/share/SharePreviewSheet.kt:51 `:76` | `ModalBottomSheet` 展开/收起（含半展开档位） |
| G17 | ui/subject/SubjectSearchScreen.kt:217-229 | `PullToRefreshBox` 指示器（全仓唯一） |
| G18 | MainActivity.kt:227 `:382`、navigation/NirikoBottomBar.kt:61、navigation/MainPager.kt:112、ui/adaptive/NirikoNavSuite.kt:130、ui/stats/CalendarCard.kt:247 `:587` `:651` | 程序化滚动（`animateScrollToPage`/`animateScrollToItem`/`animateScrollToMonth`/`animateScrollToDate`）；`CalendarCard.kt:242-248` 是「选中日期自动滚动」，非用户手势触发 |
| G19 | ui/bottombar/LiquidBottomTabs.kt:84 `:130` `:141`、ui/bottombar/LiquidVerticalDock.kt:94 `:130` `:139`、ui/bottombar/utils/DampedDragAnimation.kt:35-39 `:73-90` `:97-118`、ui/bottombar/utils/InteractiveHighlight.kt:25-26 `:57-74` | dock 按压/回弹/高亮。**注意**：kyant 上游原样搬迁已声明，加守卫属新增行为，需单独批准 |
| G20 | ui/wallpaper/WallpaperHost.kt:137-141（`.crossfade(true)`）与 `:153-209`（VideoWallpaper ExoPlayer `REPEAT_MODE_ALL`）；`.crossfade(true)` 另有 ui/subject/SubjectDetailScreen.kt:2063 `:3322` `:3360`、ui/stats/StatsScreen.kt:686、ui/settings/pages/WallpaperLibraryScreen.kt:329 | Coil 淡入与视频解码不读 `LocalReduceMotion`（只有 ui/components/CoverImage.kt:107 对主封面单独移除了 crossfade）。视频属「内容」而非过渡动画，是否计入缺口需产品决策 |

**同批排除（不是缺口，避免误报）**：ui/stats/CalendarCard.kt:424-431 与 ui/search/TrendingSection.kt:58-65 的 `delay(15_000)` while 循环只刷新「相对时间」文案，不产生动画；ui/share/ShareCardRenderer.kt:62 `:63` `:120` `:121` 的 `withFrameNanos { }` 是等两帧后截图（`:60-61` 注释），非动画；`rememberInfiniteTransition`/`updateTransition`/`AnimatedContent` 全仓 = 0；shimmer 只有 ui/components/StateComponents.kt:75-89 一个入口且已正确降级；全仓 `indication = ripple()` 5 处（ui/settings/SettingsItem.kt:150 `:194` `:240` `:313`、ui/settings/AdaptiveSettingsPane.kt:199、ui/settings/SettingsScreen.kt:184）为 Material 自带、不读 `LocalReduceMotion`（系统 scale 归零时是否完全静止需真机确认）。

### A-3 落地顺序与验收
1. 先做 G1 + G5 + G9（**成本最低、覆盖面最大**：16 个页面过渡 + 正文入场 + 8 处折叠）。2. 再做 G3/G4（共享元素，需先抽 `nirikoSharedBounds` 帮助函数）。3. 再做 G6/G7/G8。4. P1 按需。
**验收**：设置页打开「减少动态效果」（系统动画保持正常）后，逐条录屏核对：页面切换无缩放/淡入、封面不再飞入、列表项直接出现、折叠面板直接展开；且 `:app:testDebugUnitTest` 全绿。

## §B 动效令牌内聚（MEDIUM）

### B-1 现状：至少 9 套语义重复的弹簧 + 9 个自造 tween 时长，令牌层反而零调用

既有令牌：ui/animation/NirikoAnimation.kt:20 `AnimDurationShort=200`、`:23` `AnimDurationNormal=300`、`:26` `AnimDurationLong=500`、`:31` `AnimEasingDefault = FastOutSlowInEasing`、`:41-44` `pressSpring()=spring(0.75f, StiffnessMedium=1500f)`、`:47-50` `appearSpring()=spring(0.85f, StiffnessMediumLow=400f)`、`:53-56` `panelSpring()`（同 appearSpring）、`:72` `countTween()`、`:101-102` `tabFadeIn/Out`；六档规格 ui/animation/NirikoMotionSpecs.kt:27-42。

**关键发现**：`pressSpring/appearSpring/panelSpring/countTween/tabFadeIn/tabFadeOut` 除定义外**全仓零调用点**，而它们的语义却在各处以手写字面量重复出现：

| # | 位置 | 数值 | 对应令牌 | 差异 |
|---|---|---|---|---|
| T1 | ui/components/NirikoNonModalSheet.kt:142 | `spring(0.85f, Spring.StiffnessMediumLow)` | `panelSpring()` | **数值完全相同的手写复制** |
| T2 | ui/components/TouchGlow.kt:32 | `spring<Float>(0.5f, 300f)` | `pressSpring()` | 阻尼 0.75→0.5、刚度 1500→300 |
| T3 | ui/components/GlassCard.kt:139 | `spring(0.5f, 400f)` | `pressSpring()` | 阻尼 0.75→0.5、刚度 1500→400 |
| T4 | ui/subject/SubjectDetailScreen.kt:1745 | `spring(0.5f, Spring.StiffnessMedium)` | `pressSpring()` | 刚度同、阻尼 0.75→0.5 |
| T5 | ui/components/GlassControls.kt:69、ui/common/SearchToolbar.kt:172、ui/subject/SubjectDetailScreen.kt:1832 | `spring(0.6f, 400f)` ×3 | 无 | 同值散落 3 文件 |
| T6 | ui/common/IosStyleSearchComponent.kt:146 | `spring<Dp>(0.8f, 400f)` | `appearSpring()` 0.85/400 | 刚度同、阻尼 0.85→0.8 |
| T7 | ui/common/IosStyleSearchComponent.kt:172 | `spring(0.8f, 300f)` | `NirikoMotionSpecs.spatialSlow()` 0.9/300 | 刚度同、阻尼 0.9→0.8 |
| T8 | ui/common/IosStyleSearchComponent.kt:583 | `spring(0.7f, 420f)` | 无 | 420f 既不在六档也不在 pressSpring |
| T9 | ui/subject/SearchFilterPanel.kt:113-116 与 `:329-332`（共 8 处） | `spring(stiffness = Spring.StiffnessMediumLow)`（省略 dampingRatio → 默认 1.0） | `appearSpring()` 0.85/400 | 语义重复、手感不同（无回弹 vs 有回弹） |
| T10 | ui/bottombar/utils/InteractiveHighlight.kt:23-24 | `spring(0.5f, 300f, 0.001f)` / `spring(0.5f, 300f, Offset.VisibilityThreshold)` | `pressSpring()` | 与 T2 完全同值（0.5/300 全仓 4 处：TouchGlow:32、InteractiveHighlight:23、`:24`、DampedDragAnimation:31） |
| T11 | ui/bottombar/utils/DampedDragAnimation.kt:30-34 | `spring(1f,1000f,vis)`、`spring(0.5f,300f,…)`、`spring(1f,1000f,0.001f)`、`spring(0.6f,250f,0.001f)`、`spring(0.7f,250f,0.001f)` | 无 | **1f/1000f、0.6f/250f、0.7f/250f 在六档与 pressSpring 中都不存在**；全部位置参数无参数名；属 kyant 上游取值 → **只登记加注释，不改数值** |
| T12 | ui/bottombar/LiquidBottomTabs.kt:130 与 ui/bottombar/LiquidVerticalDock.kt:130 | `spring(1f, 300f, 0.5f)` | 无 | 同一行在两文件镜像重复 |
| T13 | ui/common/IosStyleSearchComponent.kt:178 `:183` `:188` `:380` `:406` `:440` `:582` `:584` `:585` | `tween(240)` `(220)` `(220)` `(200)` `(260)` `(260)` `(160)` `(140)` `(200)` | AnimDurationShort=200 / Normal=300 | 8 个值不是令牌；`:380` 与 `:585` 的 200 恰等于 AnimDurationShort 却写字面量 |
| T14 | ui/screens/Screens.kt:511 与 ui/search/SearchResultsPane.kt:215 | `tween(durationMillis = 300)` ×2 | AnimDurationNormal=300 | 同值重复字面量 |
| T15/T16 | navigation/NirikoBottomBar.kt:49、ui/adaptive/NirikoNavSuite.kt:98（180ms）；ui/subject/SubjectDetailScreen.kt:444（350ms） | `tween(180/350, AnimEasingDefault)` | — | **刻意保留**，仅建议在注释里标注「刻意偏离 AnimDurationShort/Normal」 |
| T17 | ui/bottombar/LiquidBottomTabs.kt:89 与 LiquidVerticalDock.kt:99 | `EaseOut.transform(abs(fraction))` | `AnimEasingDefault`（FastOutSlowInEasing） | 全仓第二套缓动曲线，未纳入令牌（kyant 上游） |
| T18 | ui/animation/NirikoAnimation.kt:20 `:23` `:26` `:41` `:47` `:53` `:72` `:101` `:102` | 令牌本身 | — | **令牌层已孤立**：这些名字全仓除定义外无调用点（`AnimDuration*` 只被同样零引用的 `countTween(:72)` 与 `tabFade(:101/:102)` 使用） |
| T19 | ui/settings/SettingsGroup.kt:4 `:5`；ui/subject/UniversalSubjectCard.kt:40；navigation/NirikoNavHost.kt:16 `:19`；ui/subject/SubjectDetailScreen.kt:156；ui/components/NirikoNonModalSheet.kt:39 `:40`；ui/adaptive/AdaptiveDetailScaffold.kt:24；ui/adaptive/NirikoNavSuite.kt:37；ui/settings/AdaptiveSettingsPane.kt:53；ui/stats/NirikoRadarChart.kt:17 | — | — | P0-B 之后的**残留引用**：`import CubicBezierEasing`/`import Easing`、`pressSpring`、`enterFromRight/exitToRight`、`AnimDurationNormal/Short/Long` 只出现在 import 行、无使用（文本检索结论，未跑编译验证）。⚠️ 若按 §B-2 让这些令牌重新被调用，本项自动消解 |
| T20 | ui/stats/NirikoRadarChart.kt:56 vs `:77`；docs/m3-expressive-comparison.md:75 vs `:107` | 注释称「入场动画 = 半径由 0 长到满格（[AnimDurationLong] + [AnimEasingDefault]）」，实际 `:77 growth.animateTo(1f, specs.spatialSlow())`；docs 称时长常量「被 39 处 tween/spring 引用」 | AnimDurationLong | 注释与文档已与代码脱节 |

### B-2 建议的令牌收敛（保留语义、不改观感）

**不动**：`pressSpring/appearSpring/panelSpring/tabFadeIn/tabFadeOut` 的**存在**与数值（docs/m3-expressive-comparison.md:108 已声明刻意保留）；`DampedDragAnimation`/`LiquidBottomTabs`/`LiquidVerticalDock`/`InteractiveHighlight` 的 kyant 上游数值（docs/dock-kyant-migration-plan.md:1-36）。

**建议新增一层「语义弹簧」（ui/animation/NirikoAnimation.kt），让既有令牌成为它的别名**（零行为变化，只消除字面量散落）：

```kotlin
object NirikoSprings {
    fun press()   = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)        // = 现 pressSpring()
    fun reveal()  = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)     // = 现 appearSpring()/panelSpring()
    fun selection() = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)                         // 新：吸收 T5 的 3 处
    fun soft()     = spring<Float>(dampingRatio = 0.5f, stiffness = 400f)                          // 新：吸收 T3
    fun glow()     = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)                          // 新：吸收 T2/T10
    fun dragSnap() = spring<Float>(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f) // T12
}
fun pressSpring() = NirikoSprings.press()   // 既有名字保留为别名，调用点可零成本迁移
fun appearSpring() = NirikoSprings.reveal()
fun panelSpring() = NirikoSprings.reveal()
```

时序常量同理：新增 `AnimDurationXShort=140`、`AnimDurationCompact=220`、`AnimDurationMedium=260`（覆盖 T13 的 140/160/200/220/240/260），把 ui/common/IosStyleSearchComponent.kt 的 9 个 `tween` 与 T14 的 2 处 `tween(300)` 换成常量。**180/350 两条刻意保留项不动**，只在注释写明偏离原因。

**收益（按 T 编号）**：T1/T2/T3/T4/T5/T6/T7/T9/T10/T12/T13/T14 的字面量被令牌吸收；T11/T17 只加注释标明「kyant 上游」；T15/T16 只加注释；T18 因令牌重新被调用而不再孤立；T19 随之消解；T20 按 §C 修。

### B-附：M3 Expressive 弹簧令牌（可选档位，子代理字节码实测）
- **官方 expressive 六档**（`ExpressiveMotionTokens.<clinit>`，damping/stiffness）：DefaultSpatial **0.8/380**、FastSpatial **0.6/800**、SlowSpatial **0.8/200**、DefaultEffects **1.0/1600**、FastEffects **1.0/3800**、SlowEffects **1.0/800**。
- **本工程当前复刻的是 Standard**：spatial 0.9/1400、0.9/700、0.9/300 + effects 1.0/3800、1.0/1600、1.0/800（ui/animation/NirikoMotionSpecs.kt:27-42）。
- **选项 O-1（默认，改动观感风险 0）**：B-2 的收敛保持 Standard 值不变。
- **选项 O-2（对齐 expressive 手感，只改常量）**：spatial 三档换成 0.8/380、0.6/800、0.8/200 → 明显更软更快。这是**唯一能提前吃到 M3 Expressive 手感、又不升 material3 的办法**（官方 `MotionScheme.expressive()` 要等 1.5.0-alpha01+，见 01 §C-2）。风险：`0.6/800` 用于长距离位移时收尾偏快、过冲感变弱 → 必须真机对比两处：顶部 Pager 切页（Screens.kt:130 指示器 + MainPager.kt）与详情页共享元素（SubjectDetailScreen.kt:950）。
- **载体升级（无论 O-1/O-2 都建议做，零观感变化）**：把常量包成 `object NirikoMotionScheme : MotionScheme`（接口只有 6 个 spec 方法）并传进 ui/theme/Theme.kt:250-258 的 `MaterialTheme(..., motionScheme = ...)` —— material3 1.4.0 的 `MaterialTheme.getMotionScheme()` 已是 **public**、`MotionScheme` 接口可自定义实现（只是拿不到官方 `standard()/expressive()` 实例，因为 `$material3` 后缀 = internal）。收益：material3 自带组件与自研组件共用同一套弹簧，而不是各用一套。详见 01 §C-1b。
- **采纳判据**：O-2 是**观感改版**而非缺陷修复 → 需用户先选（与 README 的 (a)/(b)/(c) 并列，见「待用户选择」）。

### B-3 落地顺序与验收
1. 先加 `NirikoSprings` + 新时长常量（纯新增，零调用点）。2. 按文件批量替换字面量（**每次替换后跑 `:app:compileDebugKotlin` + 相关单测**）。3. 最后处理 T19 的残留 import。
**验收**：全仓 `spring(` 出现次数中「带字面量数值」的比例明显下降；视觉零变化（同值替换不可能改观感）；`:app:testDebugUnitTest` 全绿。

## §C 注释与文档脱节（LOW，但会误导后续执行者）

| 位置 | 现在的说法 | 实际 | 改法 |
|---|---|---|---|
| app/src/main/java/com/otakup/niriko/ui/stats/NirikoRadarChart.kt:56 | 入场动画 = 半径由 0 长到满格（`[AnimDurationLong]` + `[AnimEasingDefault]`） | `:77 growth.animateTo(1f, specs.spatialSlow())`（spring 0.9/300） | 注释改为指 `NirikoMotionSpecs.spatialSlow()` |
| docs/m3-expressive-comparison.md:75 | 时长常量「被 39 处 tween(...)/spring(...) 引用」 | P0-B 之后引用面已大幅缩小（当前 `AnimDuration*` 仅被同样零引用的 countTween/tabFade 使用） | 更新数字或改为「引用面以 grep 为准」 |
| app/src/main/java/com/otakup/niriko/ui/animation/RevealOnScroll.kt:30-31 | 「延迟 = index × 50ms」 | `delayMillis` 全仓 0 调用点，错峰实际未发生（详见 [02 §P6](02-motion-phase-performance.md)） | 随 02 §P6 的 A/B 二选一同步修正 |
| app/src/main/java/com/otakup/niriko/ui/common/IosStyleSearchComponent.kt:142、:260-264 | 「只在 phase 切换时动画，非手势帧 → 无卡顿」「纯物理尺寸展开」 | `:549` 的 `offset` 与 `:210` 的 `dragOffsetX` 在手势帧走组合期读取（详见 [02 §P1](02-motion-phase-performance.md)） | 修正表述，避免后续执行者以为该路径已优化 |


---
**基线**：git HEAD `97ce7df`（工作树有 20+ 未提交改动）。审计日期 2026-10-01。约束：**不改动任何源码**。
**工程口径**：com.otakup.niriko / Kotlin 2.3.10 / AGP 8.13.2 / composeBom 2025.12.01（Compose UI 1.10.x、material3 1.4.0）/ navigationCompose 2.8.5 / activityCompose 1.9.3 / targetSdk 34 / minSdk 26。
**验收基线**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest`（P0 批次时点为 78 个测试类 / 696 用例 / 0 失败）。
**不得再报的刻意取舍**详见 [README.md](README.md) 末尾清单（重点：pressSpring/appearSpring/panelSpring 与 tabFadeIn/tabFadeOut 的**存在**是刻意保留；180ms 与 350ms 两条 tween 也是刻意保留）。