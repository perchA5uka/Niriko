# 批次 B · 悬浮控件任务卡（B8 · B11）

> 范围：`docs/release-v1.2.1-plan.md` §1-U13、§2（S1 全节）、§7-4；拆分为 **B-B8（一体化）**、**B-B11（自动显隐专项）**。文中 **B10 = U18「详情页收藏按钮改悬浮」**（同批次另一张卡，非本文件产出）。
> 只读产出：本文件不含任何代码改动；所有 `file:line` 均已回当前工作区（批次 A 落地后）逐条读过确认。
> 硬约束（`_tasks-b-brief.md`）：只改 UI 层；不改导航架构；不改 Dock 顺序/路由/尺寸/颜色/点击；不新增依赖；不引入全局透明触摸层；0 张卡动 Room。
> 长度说明：B8 按 9 字段常规长度；B11 按 Lead 要求展开为「阶段一分析结论 + 分步实现计划」，刻意超出 30 行。

## ⚠ 与计划/旧卡不符（先看这里）

| # | 计划写法 | 代码实际 |
| --- | --- | --- |
| 1 | B8 改 `ui/components/UniversalSearchBar.kt` | **死代码**：全仓只有 `Screens.kt:107` 一条未使用 import，零调用；真正的右上按钮是 `ui/common/IosStyleSearchComponent.kt:112`，发现页顶栏是 `ui/common/SearchToolbar.kt:55`，页面在 `ui/subject/SubjectSearchScreen.kt:353-378`（不是 `Screens.kt`） |
| 2 | B8「两个按钮」 | 每页各一对：作品库 = 左上 `FolderNavBar` + 右上搜索；发现页 = 左上趋势选择 + 右上搜索。共 4 个组件、2 个槽位 |
| 3 | B8「提到 pager 外层」即可 | 左右两侧都绑在**页面私有状态**上：`Screens.kt:297-308`（viewStyle/multiSelect/selectedIds/searchVm）、`SubjectSearchScreen.kt:104`（searchVm）。不先做状态提升，直接搬会拿不到状态或丢掉 `rememberSaveable` 语义 |
| 4 | 无 | 已有源码契约测试 `ApprovedTopControlsTest`（`app/src/test/java/com/otakup/niriko/ui/common/ApprovedTopControlsTest.kt:27-33`）锁死：`Screens.kt`/`SubjectSearchScreen.kt` 的 `searchContentPaddingDp(56f)` 字面量、`SearchToolbar.kt`/`FolderNavBar.kt` 含 `VerticalTopSelector(`。搬迁时**必须保留**这些字符串与两个组件文件 |
| 5 | B11「Dock 没有显隐」 | **已有**：`ui/common/BottomBarScroll.kt:20-50` 的 `LocalBottomBarHideFraction` + `reportBottomBarScroll()`，但它是**连续比例、无方向判定**：手机端只位移 8dp、alpha 到 0.85（`NirikoBottomBar.kt:50-58`），宽屏只位移 24dp（`NirikoNavSuite.kt:105-131`）→ 永远不完全隐藏，§2.2.6「隐藏态不拦截触摸」不成立。S1 是**改语义**（比例→方向状态机），不是从零加 |
| 6 | B11「多滚动容器不重复触发」 | 现状是反例：每个容器**各自**持有累加器（`BottomBarScroll.kt:34` `private var acc`）却共写同一个 fraction → 后写覆盖；且 `DiscoverGridPane.kt:101`、`DiscoverPosterPane.kt:188`、`SearchResultsPane.kt:97/122/165` **完全没上报** |
| 7 | B11「向上滚恢复」的符号 | `BottomBarScroll.kt:34-39` 注释称「向下滚动 fraction 增大」，但代码是 `acc + dy` 且**无单测**；Compose nestedScroll 的 dy 符号不可靠记忆。新代码必须先把 dy 归一化成方向枚举，别复用符号直觉 |
| 8 | B11 Dock「向下滑出」 | 宽屏竖 dock 与内容起始内边距绑定（`AdaptiveInsets.kt:14-52`，只收 24dp）；完全滑出会让内容重排，违反「不改 Dock 尺寸 + 不得导致列表重排」。宽屏取「24dp + alpha→0 + 禁交互」 |
| 9 | §2.1 清单 | 详情页顶栏（返回/分享/传送门，`SubjectDetailScreen.kt:594-621`）与 `TopLevelPageIndicator`（`Screens.kt:154-175`）不在清单内，本卡**不纳入**；前者隐藏会让「返回」不可发现，后者是切页唯一位置反馈 |
| 10 | 与 U5/U6 的边界 | 顶栏有两套 `hiddenHeight`（`Screens.kt:393` = 56dp vs `SubjectSearchScreen.kt:175` = `WINDOW_MASK_HIDDEN_DP`0f）与骨架顶部内边距（`Screens.kt:936` 只有 `top = 4.dp`）——属 **U5/U6 的卡**，本卡只搬控件、不改渐隐带与骨架 |

| 卡 | 主题 | 预估 | 依赖 | 动 Room | 新增依赖 |
| --- | --- | --- | --- | --- | --- |
| B-B8 | 两页悬浮按钮一体化（提到 pager 外层 + 状态提升） | M | 无 | 否 | 否 |
| B-B11 | 悬浮控件自动显隐（S1 专项） | **L** | **B8 先**（页面按钮步骤）；**B10 先**（详情 FAB 步骤）；Step 1 可与 B8 并行 | 否 | 否 |

---

## B-B8 作品库与发现页悬浮按钮一体化（U13）

- **⚠ 与计划不符**｜见文首 #1–#4、#10：`UniversalSearchBar` 是死代码（真实右上是 `IosStyleSearchComponent`）；发现页顶栏在 `SubjectSearchScreen.kt` 而不是 `Screens.kt`；两侧按钮状态都在页面私有作用域，必须先提升；且 `ApprovedTopControlsTest` 锁死了两处内边距字面量与 `VerticalTopSelector(`。
- **问题**｜两页各画一套、各黏在自己页面上：作品库顶栏是 `Screens.kt:569-581`（`Box(TopStart, fillMaxWidth, padding(top=windowTopInset+8.dp), height 56.dp, zIndex 10)`）+ 右侧搜索 `582-656`；发现页是 `SubjectSearchScreen.kt:353-378` 的 `SearchToolbar`（同几何，内部 `SearchToolbar.kt:73-103` 再包 56dp Box + `zIndex(10)`）。两套都画在 `HorizontalPager` 的每一页里（`MainPager.kt:95-191`），所以左右滑页时按钮跟着页面横向平移；这是「不像 dock」的直接原因。改前：滑到一半能看到两组按钮叠着走；改后：按钮 x/y 全程不动，只有内容切换。
- **改哪里**｜新增 `ui/components/PagerTopBar.kt`（外壳 + 两个槽）；`navigation/MainPager.kt:67,87-94,196-204`（挂载点 + 状态提升 + VM 提升）；`ui/screens/Screens.kt:297-308,569-657`（库顶栏抽成 `LibraryTopBar` 并移出页面，657 之后的`showTagFilters/showBatchActions/showDeleteConfirm` 三个对话框随 bar 一起搬走）；`ui/subject/SubjectSearchScreen.kt:104,353-378`（`searchVm` 参数化，删顶栏调用）；`ui/common/SearchToolbar.kt`、`ui/library/FolderNavBar.kt` **不改**（只换调用点，保住 `ApprovedTopControlsTest` 契约）。
- **怎么改**｜
  1. 新建 `PagerTopBar.kt`：
     ```kotlin
     enum class TopBarSlot { LIBRARY, DISCOVER }            // null = 该页无悬浮按钮
     fun topBarSlotFor(page: Int): TopBarSlot?              // 纯函数，可单测

     @Stable class PagerTopBarState(
         val librarySearch: SearchViewModel,                // 由 MainPager rememberSaveable(Saver)
         val discoverSearch: SearchViewModel,
     ) { var viewStyle by mutableStateOf(LibraryViewStyle.GRID)   // 其余三项从 LibraryScreenContent 提升
         var multiSelect by mutableStateOf(false)
         var selectedIds by mutableStateOf<Set<Long>>(emptySet()) }

     @Composable fun PagerTopBar(page: Int, windowTopInset: Dp, state: PagerTopBarState,
         collection: CollectionViewModel, search: SubjectSearchViewModel, …callbacks, modifier: Modifier)
     ```
     外壳几何**逐字照抄**现有值：`Box(modifier.fillMaxWidth().padding(top = windowTopInset + 8.dp).height(56.dp).zIndex(10f))`，`Box` 不 clip（展开搜索卡 200dp 靠溢出绘制，`SearchToolbar.kt:90-101` 同款）。
  2. 挂载点：`MainPager.kt` 外层 `Box`（:89）内、**pageBackdrop 捕获层（:90-192）之外**，与 `NirikoNavSuite`（:196-204）同级；玻璃源用已有 `barBackdrop`（:88 = 壁纸 + pageBackdrop），在外壳外包一层 `CompositionLocalProvider(LocalSearchGlassBackdrop provides barBackdrop)` —— 页面内部的 provider（`Screens.kt:366-369`、`SubjectSearchScreen.kt:168`）保留不动。
  3. 内容切换按 `pagerState.settledPage`：Library → `LibraryTopBar(...)`；Discover → `SearchToolbar(...)`（原参数照搬 `SubjectSearchScreen.kt:354-377`）；Stats/Settings → 不渲染。用 `Crossfade(150ms)` 只切内容：外壳位置/尺寸/圆角**不参与任何动画**，这就是「像 dock 一样不动」。分区名动画仍在组件内（`VerticalTopSelector.kt:65-72` 宽度 spring + `:82-84` Crossfade），不因搬迁而失效。
  4. 状态提升（最小集，缺一不可）：`viewStyle`/`multiSelect`/`selectedIds`/`searchVm` 四个从 `LibraryScreenContent` 提到 `MainPager` 的 `rememberSaveable`；`searchVm` 用现成 `SearchViewModel.Saver`（`SearchViewModel.kt:154`）。`LibraryScreen`/`LibraryScreenContent` 增参数接收；`toggleSelect`（`Screens.kt:304-305`）随状态一起上提。
  5. `CollectionViewModel` / `SubjectSearchViewModel` 的 `viewModel(factory=…)` 从 pager 分支（`MainPager.kt:111-118,134-145`）**提到 MainPager 顶部**（同一 LocalViewModelStoreOwner=MAIN，实例不变），供页面与顶栏共用，避免两处各建一次 factory。
  6. 页面删渲染、留内边距：删 `Screens.kt:569-657` 与 `SubjectSearchScreen.kt:353-378`，**保留** `Screens.kt:371` `val headerTopPadding = windowTopInset + TopFadePolicy.searchContentPaddingDp(56f).dp` 与 `SubjectSearchScreen.kt:146` 同款字面量（契约测试锁定），并把库的 `showTagFilters`/`showBatchActions`/`showDeleteConfirm` 三个 AlertDialog（`658-720`）搬进 `LibraryTopBar`；`folderPickerSubjectId`/`showBatchFolderPicker`（内容触发，`:838`）**留在内容层**。
  7. 切页副作用：`LaunchedEffect(settledPage)` 把离开页的 `searchVm` `collapse()` + 清焦点收键盘（否则「在发现页却残留作品库的展开搜索卡」，返回时又突然弹开）。
- **测试**｜① 纯函数 `topBarSlotFor`：0→LIBRARY、1→DISCOVER、2/3→null、越界→null。② 纯函数 `phaseAfterPageSwitch(phase: SearchPhase): SearchPhase = COLLAPSED`（切页策略）。③ 源码契约 `PagerTopBarContractTest`（照 `ApprovedTopControlsTest` 的 `File.readText()` 写法）：`PagerTopBar.kt` 含 `FolderNavBar(` 与 `SearchToolbar(`；`MainPager.kt` 含 `PagerTopBar(`；`LibraryScreenContent` 区间内不再出现 `IosStyleSearchComponent(`（防「搬一半、两套顶栏都在」）。④ `ApprovedTopControlsTest` 四例保持绿。
- **验收**｜① 作品库↔发现页慢滑/快甩各 3 次：两按钮 x/y **零位移**（录屏 + 逐帧比对或开发者「显示布局边界」），只有内容 150ms 交叉淡入（改前：按钮随页横向平移）。② 作品库连续点 3 个分区：胶囊宽度 spring + 名称 Crossfade 与改前一致。③ 发现页拖拽展开搜索（200dp 卡）→ pager 锁仍生效、顶栏不位移；收起后按钮回到原位。④ 切到统计/设置：按钮淡出、不残留；切回恢复；无滑入动画。⑤ 多选 3 部 → 切到发现页 → 切回：选中态还在（状态提升生效）。⑥ 展开搜索卡玻璃仍有折射（不是纯色块）。
- **风险/回滚**｜最易翻车：状态漏提一个（`selectedIds`/viewStyle）→ 切页后多选或视图样式被重置，验收⑤专门抓它。其次：顶栏从「页面图层」改到「pager 图层」，若忘了 provide `LocalSearchGlassBackdrop`，展开卡会静默降级成纯色（`GlassDecision` 路径，不崩溃但很明显）。第三：庆祝动效（`CompletionCelebrationHost` 包在 `LibraryScreenContent` 外层）与顶栏的层级顺序会变化，验收⑥顺带看一眼。回滚成本低：`PagerTopBar` 只被 MainPager 引用，删调用 + 还原两段 JSX 即可，页面内 `LibraryTopBar` 可保留不用。
- **预估**｜M
- **依赖**｜无。**B11 的 Step 2 依赖本卡**（先一体化，才有「一个宿主 + 一组按钮」可整体隐藏）。

---

## B-B11 悬浮控件自动显隐（S1 专项 · 阶段一分析 + 分步实现）

- **⚠ 与计划不符**｜见文首 #5–#9（另及 #3）：Dock **已有**一套连续比例显隐（手机 8dp / 宽屏 24dp，永不真正隐藏）；每个滚动容器各持累加器；dy 符号无单测；宽屏与 inset 绑定、不能完整滑出；详情页顶栏与页面指示器不在 §2.1 清单内。
- **问题**｜要的是「向下滚 → 控件退出视野；向上滚/点空白 → 原位恢复」，现状有三种偏差：① **没有真隐藏**——Dock 只位移 8dp、alpha 0.85（`NirikoBottomBar.kt:50-58`），宽屏 24dp（`NirikoNavSuite.kt:105-131`），控件始终可点、始终可见，§2.2.1/§2.2.6 都不成立；② **没有方向状态**——驱动量是连续比例 `acc/HIDE_PX`（`BottomBarScroll.kt:23,34-39`），不含阈值/反向判定，轻微回弹就抖；③ **没有覆盖页面按钮**——`FolderNavBar`/`SearchToolbar` 完全不参与显隐，滚动时始终压在内容上。改前：滚动只让 Dock 轻微起伏；改后：Dock 与当前页顶栏按方向整体退出/复位，点空白也能复位。
- **改哪里**｜新增 `ui/common/FloatingControls.kt`（纯策略 + 状态持有者 + 两个 Modifier）；`ui/common/BottomBarScroll.kt:20-50`（把「每容器累加」改成「单向派发到单一状态」，保留旧 API）；`navigation/NirikoBottomBar.kt:49-59`；`ui/adaptive/NirikoNavSuite.kt:99-133`；`MainActivity.kt:264-273,351-355`（动画单源 + 页面切换复位）；`ui/components/PagerTopBar.kt`（B8 产物，加位移）；`ui/screens/Screens.kt:391`、`ui/subject/SubjectSearchScreen.kt:169`（空白点击）；`ui/subject/SubjectDetailScreen.kt:1078-1081,1750-1756`（详情 FAB，B10 之后）。
- **怎么改**｜四段落地：**① 阶段一清单**（下表逐项结论：哪些纳入、退出方向、几何来源）→ **② 纯策略**（`FloatingBarState.onScroll/onBlankTap/onPageSwitch`，阈值/反向判定/跳转护栏，先单测）→ **③ 手势边界**（空白点击只在页面根 Final-pass、隐藏用 offset 不用 translation、搜索展开期强制显示）→ **④ 阶段二分三步实现 + 阶段三验收清单**（Dock → 页面按钮 → 空白恢复，每步独立可验收）。

### ① 阶段一 · 控件清单（逐项结论）

| 控件 | 文件 / 行 | 当前定位方式 | 点击 / 手势 | 纳入 | 退出方向（隐藏位移） |
| --- | --- | --- | --- | --- | --- |
| Dock（手机横胶囊） | `NirikoBottomBar.kt:49-98`，挂载 `MainPager.kt:196-204` | `Box` BottomCenter + `offset{}` + alpha | `LiquidBottomTabs` 横拖选 tab、点按切页（`LiquidBottomTabs.kt:151-166`） | **是** | 向下滑出 = 实测高度(64dp) + 底部 padding(14dp) + navBar inset + 8dp |
| Dock（宽屏竖 dock） | `NirikoNavSuite.kt:88-133`，挂载 `MainActivity.kt:397-413` | CenterStart + `offset{-24dp}` | 点按 / 拖拽 | **是（受限）** | 向左 24dp + alpha→0 + `LocalLiquidBottomTabInteractive=false`（不做完整滑出，避免重排，见 ⚠8） |
| 作品库左上分区按钮 | `Screens.kt:569-581` → `FolderNavBar.kt:47-72` → `VerticalTopSelector.kt:51-128` | 页面内 `Box(TopStart)`，`windowTopInset+8`，h56，zIndex10 | 点击开 `Popup`（下方 60dp，`:94-99`）；点项切分区 | **是** | 向上滑出 = 56 + 8 + windowTopInset |
| 作品库右上搜索按钮 | `Screens.kt:582-656` → `IosStyleSearchComponent.kt:112-268` | 同上 TopEnd，56dp 隔离 Box | 横拖拉长/点击展开，展开期锁 pager（`:226-247`） | **仅 COLLAPSED 按钮**；展开层不纳入 | 与左栏同组，向上 |
| 发现页左上趋势/年份选择 | `SubjectSearchScreen.kt:353-378` → `SearchToolbar.kt:78-87` | 同库 | `Popup` 选择 | **是** | 向上 |
| 发现页右上搜索按钮 | `SearchToolbar.kt:94-103` | 同库 | 同库 | **仅按钮** | 向上 |
| 详情页「加入收藏」 | 现状是内容区 `Button(SubjectDetailScreen.kt:1713)`；B10 改悬浮后 | 说明：已收藏态 `DetailDock`（`:1989-2022`）是**内容卡**，不是悬浮 | 点击开编辑层 | **是（B10 之后）** | 向下滑出；恢复阈值取最小（§7-4「主操作恢复延迟最短」） |
| 详情页顶栏（返回/分享/传送门） | `SubjectDetailScreen.kt:594-621` | 页面内固定 Row，`statusBarsPadding`，zIndex1 | 点击 | **否** | — |
| 顶部页面指示器 | `Screens.kt:154-175`（放置 `MainActivity.kt:363-368`） | TopCenter 固定 | 无（只显示） | **否** | — |
| 搜索展开层（MENU/IMMERSIVE 卡） | `IosStyleSearchComponent.kt:150-230` | 从 56dp 锚点缩放展开（`anchorX/anchorY/anchorSize` 全以 56dp 轨道为基准） | 输入/拖拽 | **否** | — |
| 设置条目 / 卡片内按钮 / 筛选菜单 | `SettingsScreen.kt`、`LibraryLongPressMenu`、`DropdownMenu`、`TagFilterBar` | 内容流内或独立 Popup 窗口 | 点击 | **否** | — |

### ② 规则落地 · 纯策略（先写这一层，再接 UI）

```kotlin
// ui/common/FloatingControls.kt —— 纯 Kotlin，无 Compose 依赖，JVM 可直接单测
enum class ScrollIntent { UP, DOWN }                       // 归一化：adapter 负责把 dy 符号翻成它
data class FloatingBarState(val shown: Boolean = true, val accumulatePx: Float = 0f)

data class FloatingBarConfig(
    val hideThresholdDp: Float = 48f,   // 向下累计 48dp → 隐藏
    val showThresholdDp: Float = 16f,   // 向上累计 16dp → 恢复（更灵敏：§2.2.2「恢复不要求回到顶部」）
    val jumpGuardPx: Float = 1600f,     // 单事件跨度超此值 = 程序化跳转（回顶 / 恢复位置），不参与判定
)
fun FloatingBarState.onScroll(intent: ScrollIntent, deltaPx: Float, cfg: FloatingBarConfig): FloatingBarState
fun FloatingBarState.onBlankTap(): FloatingBarState        // → shown=true, accumulate=0
fun FloatingBarState.onPageSwitch(): FloatingBarState      // → shown=true, accumulate=0（切 Tab/返回/换视图）
```
判定顺序（写进 KDoc，逐条可测）：① `|deltaPx| > jumpGuardPx` → 只清累计、**不改** shown；② intent 与上次不同 → 先清累计再累计（反向判定，不跨方向累加）；③ DOWN 累计 ≥ hideThreshold → shown=false 并清零；④ UP 累计 ≥ showThreshold → shown=true 并清零；⑤ 其余保持。注意与 `TopFadePolicy` 的分工：渐隐 alpha 仍是**连续函数**（`TopFadePolicy.kt:18,37`，F13 的「不要离散显隐」只约束渐隐带）；控件显隐是**离散状态**，但动画仍用连续 progress（`animateFloatAsState`），两者不冲突。
- **多滚动容器协调**｜单一状态持有者 `FloatingControlsState { val shown: MutableState<Boolean>; fun dispatch(intent, deltaPx) }`，在 `MainActivity` 与 `LocalBottomBarHideFraction` 同处 provide（`MainActivity.kt:269-273`）；各容器改成 `Modifier.reportFloatingScroll()`（内部只 `dispatch`，**不再各自持累加器**——直接删掉 `BottomBarScroll.kt:34` 的 `private var acc`）。已上报的 7 处（`Screens.kt:426/486/543`、`TrendingSection.kt:198`、`DiscoverPosterPane.kt:139`、`StatsScreen.kt:186`、`SettingsScreen.kt:90`、`AdaptiveSettingsPane.kt:309`）换名即可；**补漏** `DiscoverGridPane.kt:101`、`DiscoverPosterPane.kt:188`、`SearchResultsPane.kt:97/122/165`。**切页/换视图/返回即复位**：沿用 `MainActivity.kt:266-268` 的 `LaunchedEffect(pagerState.currentPage)` 调 `onPageSwitch()`；`LaunchedEffect(viewStyle)` 与 `LaunchedEffect(currentRoute)` 各一次。复位要走 `snap()`（无动画），满足「切 Tab/返回不得跳动」。
- **动画与系统区**｜单一动画源：`MainActivity` 里 `val hideProgress by animateFloatAsState(if (shown) 0f else 1f, tween(220, FastOutSlowInEasing))`，仍以 `LocalBottomBarHideFraction provides mutableFloatStateOf(hideProgress)` 对外——`verticalDockStartInset { … }`（`AdaptiveInsets.kt:22-52`）与现有读取路径零改动。手机 Dock 位移 = `measuredHeight + 14dp + WindowInsets.navigationBars.bottom + 8dp`（用 `onSizeChanged` 实测，别硬编码）；顶栏位移 = `56dp + 8dp + windowTopInset`；FAB 同 Dock 方向。**隐藏必须用 `Modifier.offset{}`（改 placement → 命中区一起外移）**，绝不能用 `graphicsLayer.translationY`（视觉走了、命中区还在）。`reduceMotion` → `snap()`（`NirikoAnimation.kt:65-69`）。

### ③ 手势冲突边界（§2.2.3 / §2.2.6）

1. **点空白恢复**：只在页面根容器（`Screens.kt:391`、`SubjectSearchScreen.kt:169`）加 `Modifier.floatingBarRestoreOnBlankTap(onRestore)`；实现是 `pointerInput { awaitPointerEventScope { … awaitPointerEvent(PointerEventPass.Final) … } }`，**仅当**一次 down→up 全程没有任何 change 被 `isConsumed`、且位移 < `ViewConfiguration.touchSlop` 时恢复。禁止 `Box.fillMaxSize().clickable{}`（会吞卡片点击），也不提 zIndex。
2. **卡片长按/点击**：`combinedClickable`（`CollectionCard`→`UniversalSubjectCard`）在 Main pass 消费 down，Final 观察者天然跳过 → 长按菜单、多选、加入分区不受影响（验收 5 专门测）。
3. **Popup/弹窗/下拉**：`DropdownMenu`/`AlertDialog`/`Popup`（含 `VerticalTopSelector` 的 Popup）是独立窗口，点击不落到页面根 → 不误触发恢复，也不会被位移带走。
4. **搜索展开期**：`searchVm.phase != COLLAPSED` 时**强制 shown 并暂停自动隐藏**（否则 200dp 卡会被移出屏幕），pager 锁保持现状（`LocalSearchGestureLock`，`MainPager.kt:84,98`）。
5. **Dock 自身横拖**（`LiquidBottomTabs.kt:151-166`）与 **pager 横滑**（dy≈0）不产生纵向累计；Dock 显隐期间不改变其内部拖拽几何。
6. **详情页 FAB**：不新增滚动监听，复用已有 `detailScrollSample`（`SubjectDetailScreen.kt:1078-1081`，由 `:1750/:1753/:1756` 采样）派生方向；`showThresholdDp` 单独取 8f。

### ④a 阶段二 · 三步实现（每步可独立验收、独立回滚）

| Step | 内容 | 验收锚点 |
| --- | --- | --- |
| **1 · Dock** | 新增 `FloatingControls.kt`（策略 + state + 两个 Modifier）→ 改 `BottomBarScroll.kt` 只做派发 → `NirikoBottomBar.kt:49-59` 换成完整滑出 → `NirikoNavSuite.kt:99-133` 宽屏 alpha/禁交互 → `MainActivity` 动画单源 + 复位 | 作品库 LIST 向下滑 48dp：Dock 完全离开屏幕（截图底部 100px 无胶囊像素）；向上滑 16dp：回位且与隐藏前截图像素一致；隐藏态点原位置无切页 |
| **2 · 页面按钮**（依赖 B8） | 在 `PagerTopBar` 外壳加同样的 `offset`/alpha；首开与切页 shown；搜索展开期强制 shown | 发现页向下滑：顶栏一组按钮整体上移出屏（含搜索圆钮），左栏与右栏同进同出；向上滑原位恢复 |
| **3 · 空白恢复 + 详情 FAB**（FAB 依赖 B10） | 两个页面根加空白点击 Modifier；详情页 FAB 接 `detailScrollSample` | 隐藏态点列表空白 → 恢复；点卡片 → 进详情且不恢复；长按 → 菜单出现且不恢复；FAB 向下滚退出、上滑 8dp 即回 |

### ④b 阶段三 · 验收清单（编译后逐条手测）

1. 方向：慢滚/快甩/回弹各 3 次，只在方向变化时切换，无抖动、无「半隐半现」。
2. 恢复：向上 16dp 即回；恢复后位置/尺寸/材质与隐藏前**逐像素一致**（截图叠加）。
3. 空白点击：隐藏态点空白恢复；点卡片/长按/搜索/筛选/弹窗一律不误触。
4. 动画方向：Dock 向下、顶栏向上、FAB 向下；180–250ms；无越界回弹；`reduceMotion` 时瞬时。
5. 卡片手势：长按菜单、多选、加入分区、拖拽均不受影响。
6. 页面切换/返回/换内容类型：控件即时在位（不滑动入场），旧页状态不残留。
7. 触摸穿透：隐藏态在控件原位置点击无响应；系统返回手势区无障碍、无残影。
8. 系统区：手势导航与三键导航各一次，bottom inset 不额外抬高、隐藏后不进入手势区。
9. 性能：滚动 30s 不掉帧（`Choreographer` 丢帧 < 1%），无每帧重组放大（状态读取保持单点）。

### 不能纳入的控件与原因（明确排除）

| 控件 | 原因 |
| --- | --- |
| 搜索展开层（MENU/IMMERSIVE 200dp 卡） | 用户正在交互的主动表面；且它的全部几何以 56dp 锚点为基准（`IosStyleSearchComponent.kt:194-224`），位移会破坏展开锚点 |
| 搜索面板内的工具/筛选行、`SuggestionDropdown` | 同上，且属于展开层内部 |
| 设置条目（`SettingsActionRow` 等） | 非悬浮，属页面内容，随滚动自然离开视口 |
| 卡片内按钮（`LibraryLongPressMenu` 的 ±1、批量管理、`DropdownMenu` 工具） | 内容内交互；隐藏等于禁用操作；Popup 是独立窗口，位移会与锚点错位 |
| 详情页顶栏（返回/分享/传送门） | §2.1 未列；隐藏后「返回」不可发现（虽有系统手势），且属导航控件，红线禁止改动 |
| `TopLevelPageIndicator` | 非按钮；是切页唯一位置反馈 |
| 页面指示/骨架/渐隐带 | 属 U5/U6，本卡不动（见 ⚠10） |

- **测试**｜① `FloatingControlsPolicyTest`（纯 JVM）：DOWN 60dp→隐藏；UP 20dp→恢复；DOWN 30 后 UP 10→仍显示、累计清零后重计为 10（反向判定不复用旧累计）；DOWN 30 + DOWN 30→隐藏（跨事件累计）；单事件 2000px→状态不变（jumpGuard）；`onBlankTap`/`onPageSwitch` 复位；dp→px 边界（48dp@2.75 恰好等于阈值）。② `BottomBarScrollContractTest`（源码契约）：`BottomBarScroll.kt` 不再含 `private var acc`；`NirikoBottomBar.kt` 含 `offset` 且不含 `graphicsLayer.*translationY`；三处页面根含 `floatingBarRestoreOnBlankTap`；`PagerTopBar.kt` 含 `hideProgress`。③ 既有 `TopFadePolicyTest`、`ApprovedTopControlsTest` 保持绿（证明没动渐隐与顶栏组件）。
- **验收**｜见上「阶段三」9 条 + 每 Step 的锚点；另加：`./gradlew :app:assembleDebug`（Lead 统一跑）与本卡新增单测全绿。
- **风险/回滚**｜① **方向符号反**（最大坑）：`BottomBarScroll.kt:34-39` 无单测且注释与代码可能不一致 —— Step 1 先用日志确认「手指上滑时 intent=DOWN」再合并；策略函数只吃 `ScrollIntent`，符号只在一行 adapter 里翻。② **空白点击吞事件**：Final-pass 观察者若写成 Main-pass 或加 `fillMaxSize().clickable`，会吞卡片点击 —— 验收 3/5 卡死。③ **宽屏完整滑出会重排**（`AdaptiveInsets.kt:22-52` 只收 24dp）：本卡明确不做完整滑出，只 alpha→0 + 禁交互；若产品坚持完整滑出，需另开卡同时改 inset 与内容 padding。④ **多容器双写**：必须删掉各自的 `acc`，否则 Step 1 之后仍是「后写覆盖」。⑤ 回滚：`FloatingControls.kt` 与两个 Local 都是新增，Step 1/2/3 各自可单独撤；旧 `reportBottomBarScroll()` 保留为 `reportFloatingScroll()` 的委托 → 撤销 UI 改动即回到当前 8dp/24dp 连续比例。
- **预估**｜**L**（Step 1 约 1 天；Step 2 依赖 B8，约 1 天；Step 3 + 全量手测约 1 天）
- **依赖**｜**B8 先**（Step 2 需要「一个宿主的一组按钮」；B8 未落地时顶栏仍有两套，无法整体隐藏）；**B10 先**（Step 3 的详情 FAB）；**Step 1 与 B8 无耦合、可并行开工**。与 U5/U6 的顶栏渐隐/骨架卡无依赖但共享同一顶部区域，改完先跑 U5/U6 的验收避免互相踩。

---

## 收尾说明

- 本文件为只读产出：未改任何代码、未新增依赖、未动数据层/Room（v33 与本批次无关）。
- 顺序建议：**B8 →（并行）B11-Step1 → B10 → B11-Step2 → B11-Step3**。
- 两个卡都**不改** `IosStyleSearchComponent` 内部几何、`VerticalTopSelector` 的 label 动画、`TopFadePolicy` 的渐隐带与两页的 feed 内边距字面量——这些是既有契约测试（`ApprovedTopControlsTest`）与 U5/U6 的边界。
