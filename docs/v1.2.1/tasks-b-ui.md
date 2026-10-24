# 批次 B · UI 一致性任务卡（B1–B5）

> 范围：`docs/release-v1.2.1-plan.md` §1 的 U5 / U6 / U8 / U9 / U10（§2 S1 的显隐规则只在 B-3/B-4 的手势边界处引用）。卡号 B-1…B-5 对应任务单里的 B1…B5。
> 只读产出：本文件不含代码改动；所有 `file:line` 均已回当前工作区确认（A 批次已落地的代码之上）。
> 批次 B 硬约束：只改 UI 层；不动数据层/Repository/同步；**0 张卡动 Room**（v33 只服务批次 C）；不新增依赖；不引入全屏透明触摸层；不改导航/Dock/路由/尺寸/颜色/点击行为。
> 共同前置：`./gradlew :app:assembleDebug` 通过；新增纯函数进 `:app:testDebugUnitTest`。

## ⚠ 与计划不符（7 条，先看这里）

| # | 计划原写法 | 代码实际 | 影响 |
| --- | --- | --- | --- |
| 1 | U5「两套 hiddenHeight（56dp / 0dp）」 | 其实是**三种**边界：作品库 56dp（`Screens.kt:393`）、发现/搜索/详情 0dp（`SubjectSearchScreen.kt:175`、`SubjectDetailScreen.kt:1748`）、**统计与设置完全没有遮罩**（`MainPager.kt:170,186` 只有整页 `padding(top=contentTopInset)`） | B-1 先定边界再谈四页；统计/设置"加不加遮罩"需拍板 |
| 2 | U5 要"修掉 TopFadeOverlay 死代码" | 确实 0 调用（只有自身 KDoc 提到 mask），但它自述是 legacy color scrim，真正生效的一直是 `TopContentAlphaMask` | 删它=零运行时变化，可安全清理 |
| 3 | U5 统一到 `windowTopInset + 56dp` | 与既有单测直接冲突：`TopFadePolicyTest.edgeToEdgeMaskStartsAtWindowTopWithoutToolbarPlateau` 断言 `WINDOW_MASK_HIDDEN_DP == 0f`；`ApprovedTopControlsTest` 又用**字符串契约**锁死两句顶部内距写法 | B-1/B-3 必须同卡改这两处测试，否则 CI 红 |
| 4 | U6 骨架在 `Screens.kt:968` | 骨架在 `:930-976`（:968 是画廊骨架里那个 340dp Box）；正式列表顶部内距是 `:371` 的 `headerTopPadding`，用在 `:427/:487/:544`；`:570` 是悬浮行 `padding(top=windowTopInset+8.dp).height(56.dp)` | B-2 的改动点是 `:396` 与三个骨架分支 |
| 5 | U8「搜索展开时加 topInset 避让」 | `SubjectSearchScreen.kt:146` 的 `contentTopInset` 是常量；`:149` 与 `:201` 两处注释都声称"展开是 overlay / 菜单展开时顶部避让"，但代码里**没有任何按 phase 变化的避让** | 注释与实现不符，B-3 要连着改注释 |
| 6 | U9「展开动画期间偶发」 | 根因不是动画：COLLAPSED 时行 2–4 仍**在组合树里**（只 `alpha=0`，`IosStyleSearchComponent.kt:358-585`）、外层卡片 `requiredHeight(56dp)` 未裁剪（`:257-262`），库页"排序"在行 2（`Screens.kt:595`，y≈68dp 起）→ 收起态点按钮下缘就能命中不可见的排序 | 修法要覆盖"收起态"，不只是动画期 |
| 7 | U10 指向 `GlassAnchoredMenu` | `GlassAnchoredMenu`（`GlassAnchoredMenu.kt:69-147`）已经是正确做法（同窗口 `Layout` 先量后排）；真正的坏例子是左上分区/趋势选择器 `VerticalTopSelector` 的 **`Popup` 独立窗口**（`VerticalTopSelector.kt:94-126`） | B-5 的主改点是 `VerticalTopSelector`，`GlassAnchoredMenu` 只做抽取复用 |

## 共用常量与调用点清单（B-1 先落，B-2/B-3 复用）

新增到 `util/TopFadePolicy.kt`（纯函数，无 Compose 依赖，可直接 JVM 单测）：

- `const val FLOATING_CONTROL_ROW_DP = 56f` —— 悬浮控件行高（`Screens.kt:570`、`SubjectSearchScreen.kt:377` 的 `.height(56.dp)` 写死值）
- `fun windowMaskHiddenDp(topInsetDp: Float): Float = topInsetDp + FLOATING_CONTROL_ROW_DP` —— **渐隐起点 = 真实顶部 inset + 56**（不是内容顶距！）
- `fun feedTopPaddingDp(topInsetDp: Float): Float = topInsetDp + searchContentPaddingDp(FLOATING_CONTROL_ROW_DP)` —— 与现有 `windowTopInset + searchContentPaddingDp(56f)` **数学等价**，纯搬运
- `fun skeletonTopPaddingDp(topInsetDp: Float): Float = feedTopPaddingDp(topInsetDp) + 4f` —— B-2 用

| # | 位置 | 文件:行（当前） | 现状值 | 统一后 |
| --- | --- | --- | --- | --- |
| 1 | 作品库 · 遮罩 | `Screens.kt:393` | `hiddenHeight = 56.dp`（绝对，不随 inset） | `windowMaskHiddenDp(windowTopInset.value).dp` |
| 2 | 作品库 · 内容顶距 | `Screens.kt:371` | `windowTopInset + searchContentPaddingDp(56f).dp` | `feedTopPaddingDp(windowTopInset.value).dp` |
| 3 | 作品库 · 悬浮行 | `Screens.kt:570` | `top = windowTopInset + 8.dp`，高 56dp | **不动**（只把 56 换成常量） |
| 4 | 发现 · 遮罩 | `SubjectSearchScreen.kt:175` | `WINDOW_MASK_HIDDEN_DP.dp`（0） | `windowMaskHiddenDp(windowTopInset.value).dp` |
| 5 | 发现 · 内容顶距 | `SubjectSearchScreen.kt:146` | `windowTopInset + searchContentPaddingDp(56f).dp` | `feedTopPaddingDp(windowTopInset.value).dp` |
| 6 | 发现 · 工具栏 | `SubjectSearchScreen.kt:377` | `top = windowTopInset + 8.dp` | **不动** |
| 7 | 详情 · 遮罩 | `SubjectDetailScreen.kt:1748` | `WINDOW_MASK_HIDDEN_DP.dp`（0） | `windowMaskHiddenDp(statusTop.value).dp`（`statusTop` = `statusBars` 顶，`SubjectDetailScreen.kt:487`；**别传 `headerHeight`**，它已含 +56） |
| 8 | 统计页 | `MainPager.kt:170` + `ui/stats/StatsScreen.kt:187` | 无遮罩；整页 pad(inset)，列表 top=16dp | 本轮不加遮罩（需拍板）；顶部内距对齐 `feedTopPaddingDp` |
| 9 | 设置页 | `MainPager.kt:186` + `ui/settings/SettingsScreen.kt:95` | 无遮罩；整页 pad(inset)，首标题 top=20dp | 同上 |
| 10 | 作品库 · 骨架 | `Screens.kt:396, 936, 952, 959` | 三分支都是 `padding(top = 4.dp)` | `skeletonTopPaddingDp(windowTopInset.value).dp + 4.dp` |

> 8dp 口径提醒：悬浮行占 `[inset+8, inset+64]`，内容顶距 `inset+72`，而计划给的统一值 `inset+56` 比行底高 8dp。三处 mask 只要走同一个函数，将来改成 `inset+64` 就是一行；**不要**在调用点各写各的偏移。

---

## B-1 顶部渐隐四页统一（U5）

- **⚠ 与计划不符**｜见上表 #1/#2/#3：三种边界而非两套；且统一值与 `TopFadePolicyTest` + `ApprovedTopControlsTest` 两处测试直接冲突，必须同卡修改。
- **问题**｜四页渐隐起点各不相同，证据：作品库把 56dp 写成**绝对字面量**（`Screens.kt:393`，不随 `windowTopInset`：inset=24 时渐隐带落在 56–80dp，正好压在悬浮行 32–88dp 的中部）；发现页从窗口 y=0 淡起（`SubjectSearchScreen.kt:175` 传 0）；统计/设置整页下移 `contentTopInset` 却完全没有渐隐（`MainPager.kt:170,186`）。`TopFadeOverlay.kt` 全仓 0 调用，是自述 "Feed pages use topContentAlphaMask instead" 的遗留色罩。
- **改哪里**｜`util/TopFadePolicy.kt`（加 1 常量 + 3 函数）；`ui/components/TopContentAlphaMask.kt:22-26`（去掉默认值）；`ui/screens/Screens.kt:371,393`；`ui/subject/SubjectSearchScreen.kt:146,175`；`ui/subject/SubjectDetailScreen.kt:1748`（用 `:487` 的 `statusTop`）；删除 `ui/components/TopFadeOverlay.kt`。
- **怎么改**｜
  1. 先按上表把 4 个纯函数/常量加进 `TopFadePolicy`，KDoc 写明"渐隐起点 = 真实顶部 inset + 控件行高"；把 `WINDOW_MASK_HIDDEN_DP` 标 `@Deprecated`（保留常量，避免一次性改所有引用）。
  2. 三处 mask 调用点把字面量换成 `windowMaskHiddenDp(...)`；**只搬这一个参数**：各页的 `TopFadePolicy.alphaFor(...)` 采样、滚动上报（`scrollOffsetFor`）、层结构（`layerBackdrop` / `CompositionLocalProvider`）一行都不动——这就是"只搬运不改语义"：滚多少、亮多少完全不变，只移动渐隐带起始 y。
  3. `topContentAlphaMask` 删掉 `hiddenHeight: Dp = 56.dp` 默认值（改必填）：默认值是两个值能长期共存的根源；这是编译期强制，运行时零变化。
  4. 统计/设置：不新增遮罩，只把 `MainPager` 传下去的顶部内距换成 `feedTopPaddingDp`，让四页"第一像素"落在同一相对位置；两页都没有需要让位的悬浮控件（U13 才把按钮提出 pager），现在加渐隐属于新增视觉，需 Lead 拍板。
  5. 删 `TopFadeOverlay.kt`（0 调用、无运行时影响），并在 `TopContentAlphaMask` 的 KDoc 写明"这是唯一的顶栏遮罩入口"，避免再来一个色罩。
- **测试**｜`TopFadePolicyTest` 追加：`windowMaskHiddenDp(0f)=56f`、`(24f)=80f`、`(48f)=104f`；`feedTopPaddingDp(24f)=96f`；`skeletonTopPaddingDp(24f)=100f`。**同时改**：`edgeToEdgeMaskStartsAtWindowTopWithoutToolbarPlateau`（改名 `windowMaskStartsBelowFloatingRow`，断言 56/80/104，不再断言 `WINDOW_MASK_HIDDEN_DP==0f`）；`ApprovedTopControlsTest.searchExpansionDoesNotChangeEitherFeedInset` 的两条字符串断言换成新写法。
- **验收**｜同一台 inset=24dp 设备逐页截图：① 作品库首帧渐隐起点 = 80dp（改动前 56dp，可观测差异：顶部实心带下移 24dp）；② 发现页状态栏下 80dp 内不再淡出（改动前从 y=0 淡，最明显）；③ 详情页封面在头部行下方 80dp 处才开始渐隐（改动前 0）；④ 统计/设置首屏内容起点与另两页视觉一致（允许 8dp 容差：它们列表另有 16dp/20dp 内距）；⑤ 三档玻璃（全效果/降低/关闭）各看一次，无明显硬边；⑥ `:app:testDebugUnitTest` 绿。
- **风险/回滚**｜① 详情/搜索页从"0 台"变成"56dp 台"，顶部 56dp 不再淡出——本卡最大可见变化，需真机确认封面顶边没有硬切；② 详情页若误传 `headerHeight`（已含 56）会变成 `inset+112`，渐隐几乎看不见——传 `statusTop`；③ `windowTopInset` 与 `statusBarsPadding` 是否双计需先打印一次；④ 回滚：三处改回字面量，新函数保留无害。
- **预估**｜S
- **依赖**｜B-2/B-3 复用本卡常量；与 A-X4/A-X5 改同一文件 `SubjectDetailScreen.kt`（A 已改保存与卷进度），需在 A 之上 rebase 后再动 `:1748`。

---

## B-2 骨架屏顶部对齐（U6）

- **⚠ 与计划不符**｜计划写 `Screens.kt:968`；实际骨架函数在 `:930-976`（`:968` 是画廊骨架的 340dp `Box`），三个分支的顶部内距分别在 `:936/:952/:959`，都是 `top = 4.dp`。
- **问题**｜骨架三种视图都是 `Column` + `padding(start=12,end=12,top=4.dp)`（`Screens.kt:936/952/959`），从窗口 y≈4dp 开始画；而正式列表用 `contentPadding(top = headerTopPadding)`（`Screens.kt:371,427/487/544`，= `windowTopInset + 72dp`）。于是加载首帧骨架顶到状态栏、被 `:570` 的 56dp 悬浮行整块盖住；数据到达后列表跳到 inset+72 → 可见跳版。
- **改哪里**｜`ui/screens/Screens.kt:396`（调用点）；`:930-976`（三个分支的 padding）；无新文件。
- **怎么改**｜
  1. `LibrarySkeleton(viewStyle, topPadding: Dp)` 加参数（唯一新增签名，调用点只有 `:396` 一处）。
  2. 三个分支的 `top` 从 `4.dp` 改成 `topPadding + 4.dp`：保留原来那 4dp 呼吸；横向 12dp 与 `spacedBy` 不动。
  3. 调用点传 `TopFadePolicy.skeletonTopPaddingDp(windowTopInset.value).dp`（= inset+76），使骨架首项与正式列表首项（inset+72）只差原来那 4dp 呼吸；**不做**骨架全屏、不改骨架几何（2×3 网格 / 3 张列表卡 / 2×340dp 画廊都保持）。
  4. 不改判定条件 `state.isLoading && state.items.isEmpty()`（`:394-396`），不改替换时机。
- **测试**｜沿用仓库已有的源码契约写法（`ApprovedTopControlsTest` 用 `File("src/main/java/...").readText()`）：断言 `Screens.kt` 含 `LibrarySkeleton(viewStyle = viewStyle, topPadding =`、含 `topPadding + 4.dp`、且不再出现 `padding(start = 12.dp, end = 12.dp, top = 4.dp)`；纯函数断言 `skeletonTopPaddingDp(0f)=76f`、`(24f)=100f`（随 B-1 一起测）。
- **改动前/后差异（可观测）**｜加载首帧骨架首行 y：4dp → `inset+76`（inset=24 时 100dp）；真实列表首行 y 不变（`inset+72`）→ 骨架与列表之间只剩 4dp 呼吸差，切换不再顶跳。
- **验收**｜清应用数据 → 断网冷启进入作品库：① 骨架首行贴在悬浮行下方（改动前贴屏幕顶、被按钮压住）；② 骨架切到真实列表时第一行 y 位移 ≤4dp（截图对比）；③ 三种视图（列表/宫格/画廊）各看一次；④ 骨架没有铺满全屏（底部仍可见背景）。
- **风险/回滚**｜骨架只在首屏出现，极易漏测"从骨架切到真实内容"那一帧——验收必须录屏；`headerTopPadding` 与 `skeletonTopPaddingDp` 若算成两个值（差 4dp）会造成一次 4dp 跳动，这是有意的呼吸差，别在实现时把 4 抹掉。回滚：参数与三处 padding 一起还原。
- **预估**｜S
- **依赖**｜B-1（需要 `feedTopPaddingDp`/`skeletonTopPaddingDp` 先存在，否则本卡要自建常量，会分裂成两套）。

---

## B-3 发现页搜索态部件无上边界（U8）

- **⚠ 与计划不符**｜`SubjectSearchScreen.kt:149` 注释写 "Only the collapsed toolbar reserves initial space; every expanded surface is an overlay."、`:201` 又写"菜单展开（MENU/DRAGGING）时顶部避让"，但 `contentTopInset`（`:146`）是常量，全文件没有任何按 `searchVm.phase` 变化的避让 → 注释描述的行为不存在。
- **问题**｜搜索表面展开后高度 = `expandedHeight`（`IosStyleSearchComponent.kt:124` 默认 200dp，两个调用点都没覆盖），整块压在内容上；而表面是**半透明玻璃**（`SearchGlassSurface.kt:31-43`：blur + `onDrawSurface = surface.copy(alpha = 0.4f)`），所以趋势标签、分类筛选行、结果首项都"透出来"却没有边界。IMMERSIVE 态还有第二处：卡片高度 `collapsedH = 68dp`（`:139`）从 `inset+8` 起 → `inset+76`，而结果列表 `topInset = contentTopInset = inset+72`（`:146,346`），首项固定被压 4dp；`filterRowExpanded` 时卡片回到 200dp，被压 128dp。
- **改哪里**｜`ui/subject/SubjectSearchScreen.kt:146, 193, 279, 299, 346, 149, 201`；`ui/components/SearchGlassSurface.kt:24-43`；`ui/common/IosStyleSearchComponent.kt:124`（不要在调用点硬编码 200）。
- **怎么改**｜先定方案（**推荐 B**）：**A（让位）** `val expandedTopInset = when (phase) { COLLAPSED -> contentTopInset; IMMERSIVE_SEARCH -> windowTopInset + 8 + collapsedH; else -> windowTopInset + 8 + menuH }` + `animateDpAsState(spring(dampingRatio = .8f, stiffness = 400f))` 传给 4 处 `topInset`（`:193/:279/:299/:346`），代价是列表重排、内容被推走、且要改 `ApprovedTopControlsTest` 的字符串契约；**B（不透明覆盖，plan 后半句）** 只给展开态表面加实心底（`SearchGlassSurface` 增 `opaque: Boolean = false`，展开传 `true` → `onDrawSurface` 用 0.94f，与 `GlassEffectLevel.OFF` 分支同值），200dp 卡片下缘 8dp 渐隐用已有 `contentAlphaAt`，内容不动、滚动不跳、不碰契约测试。
  1. 抽纯函数 `SearchExpansionPolicy.isOpaque(phase): Boolean` 与 `SearchExpansionPolicy.topInsetDp(phase, inset, menuH, collapsedH): Float`（两方案共用同一处判据，测试只测这里）。
  2. 只改视觉层：**不要**加全屏 `Box` 拦截层（brief 硬约束 3）；面板外区域必须继续穿透到列表（Compose 按节点 bounds 命中，现状已满足，改动中不得引入 `fillMaxSize().pointerInput{}`）。
  3. 手势边界写清：面板只消费自身矩形内的 down；`IosStyleSearchComponent.kt:275-281` 的锚点区判定（`down.position.y > colPad+trackH` 或 `x < width-anchorZone` 就 `return@awaitEachGesture` 放行）保持不变；列表滚动、卡片点击、长按、筛选面板都不受影响。
  4. 收尾改掉 `:149` 与 `:201` 两条失效注释，避免下一个人按注释理解代码。
- **测试**｜`SearchExpansionPolicyTest`：`topInsetDp(COLLAPSED, 24f, 200f, 68f)=96f`（= inset+72）、`(IMMERSIVE_SEARCH)=100f`（inset+8+68）、`(MENU_EXPANDED)=232f`（inset+8+200）；`isOpaque(COLLAPSED)=false`、其余 `true`（方案 B）。
- **改动前/后差异（可观测）**｜展开 MENU 时菜单区域下方 128dp 内：改动前能透字看到趋势卡片标题与筛选行，改动后是连续玻璃底（`0.4f` → `0.94f`，透字消失）；IMMERSIVE 的 4dp 重叠仍在，但由实心底干净覆盖，不再半透叠加；方案 A 则是内容整体下移、无重叠。
- **验收**｜① 展开 MENU：菜单下方不再透出趋势卡片/筛选行（改动前明显透字）；② IMMERSIVE 编辑态结果首项不与输入框半透叠加（方案 B：被实心底干净覆盖；方案 A：不被压住）；③ 展开时点菜单外仍能滚动列表、点卡片（手势未被吞）；④ 收起后内容位置与展开前一致（方案 B 必须零位移）；⑤ 玻璃三档各看一次（OFF 档本来就是 0.94 实心，不能出现两套观感）；⑥ 录屏确认无跳版。
- **风险/回滚**｜① 改 `SearchGlassSurface` 会波及底栏/分区菜单/所有 `searchGlassSurface` 调用点 → 参数默认 `false`，只让展开态显式传 `true`；② 若选方案 A，滚动位置会跳且契约测试要改，风险高于 B；③ 回滚：单参数回调为默认值即可。
- **预估**｜M
- **依赖**｜B-1（`feedTopPaddingDp`）；与 B-4 同文件 `IosStyleSearchComponent.kt`，先 B-4（行为修复）再 B-3（视觉），避免两次重排该文件。

---

## B-4 作品库搜索键展开误触排序（U9）

- **⚠ 与计划不符**｜计划说"展开动画期间偶发"；实际 COLLAPSED（根本没展开）时行 2–4 仍参与组合与命中，点搜索按钮下缘就会落到不可见的"排序"上——比动画期宽得多。
- **问题**｜收起态卡片被 `requiredHeight(cardH = 56dp)` 限高（`IosStyleSearchComponent.kt:257-262`），但内容列是 `fillMaxSize().padding(colPad)` 里的完整 4 行（`:358-585`），只靠 `contentAlpha = 0`（`:178,364`）淡出，**既不从组合树移除、外层也没有裁剪**；行 2 从 y ≈ `colPad+trackH+4` ≈ 68dp 开始，库页"排序"就在行 2 的 `tools` 里（`Screens.kt:595`，接进 `IosStyleSearchComponent.kt:493` 的 `tools()`）。Compose 的命中测试按各节点自身矩形进行，父容器的 56dp 限高不会把子节点排除在命中之外 → 点搜索按钮下方 68–108dp 区间会打开排序菜单（"偶发"取决于落点）。
- **改哪里**｜`ui/common/IosStyleSearchComponent.kt:124, 178-186, 257-262, 358-585`（尤其 `:473-493` 行 2）；不改 `Screens.kt` 的排序按钮逻辑（只做热区核对）。
- **怎么改**｜
  1. 收起态不组合菜单行：把行 2–4 包进 `if (!isCollapsed) { ... }`（或抽 `SearchExpansionPolicy.shouldComposeMenuRows(phase)`），保留 `contentAlpha/rowsAlpha` 淡入（phase 一变就组合，动画照旧）。
  2. 动画期禁用工具行：用 `val transition = updateTransition(phase)` 取 `transition.isRunning`，把 `toolsEnabled = !transition.isRunning && !isCollapsed` 通过新增参数（如 `toolsInteractive: Boolean = true`）传给 `tools` 的 RowScope 包装层（`Modifier.pointerInput(toolsInteractive){ if (!toolsInteractive) awaitPointerEventScope { while (true) awaitPointerEvent() } }`）；**不要**改 `tools` 的 lambda 签名，两个调用点（`Screens.kt:593`、`SearchToolbar.kt:111`）都不必改。
  3. 热区核对（必做，验证后再删）：临时给 `SearchToolButton`（`ui/common/SearchToolButton.kt:14-27` 的 `IconButton`）加 `Modifier.onGloballyPositioned { Log.i("HitArea", it.boundsInRoot().toString()) }`，确认 COLLAPSED 下排序按钮矩形确实落在 56dp 卡片外。
  4. 手势边界写清：锚点区判定（`:279-281`）保持——`y > colPad+trackH` 或 `x < width-anchorZone` 一律放行，不扩大卡片手势区；收起态不消费任何点击；Pager 手势锁 `onGestureLockChange`（`SubjectSearchScreen.kt:371`、`Screens.kt:590`）时机不变（`lockChanged.value(true)` 仅在锚点 down 之后）。
- **测试**｜新增纯函数 `SearchExpansionPolicy.shouldComposeMenuRows(phase)`：COLLAPSED→false，MENU/DRAGGING/IMMERSIVE→true；`shouldEnableToolRow(isCollapsed, isRunning)`：`(true,false)=false`、`(true,true)=false`、`(false,true)=false`、`(false,false)=true`。源码契约（沿用 `ApprovedTopControlsTest.source()`）：断言 `IosStyleSearchComponent.kt` 含包住行 2–4 的 `if (!isCollapsed)` 标记。
- **改动前/后差异（可观测）**｜COLLAPSED 下点搜索按钮下方 68–108dp 区间：改动前会打开「排序」菜单（可稳定复现），改动后该区间没有任何节点接收点击；展开态行为完全不变。
- **验收**｜① COLLAPSED 下在搜索按钮**下缘 4 / 8 / 12dp** 三处各点 10 次：排序菜单 0 次弹出（改动前可复现）；② 点按钮本体仍能展开/收起；③ 展开动画进行中快速点行 2 的排序/标签/模式：无响应、不误触；④ 动画结束后工具行正常；⑤ 展开态左右滑 Pager 仍被锁（手势语义不变）；⑥ 结果列表滚动/卡片点击/长按无变化。
- **风险/回滚**｜① `if (!isCollapsed)` 会让展开首帧多一次组合，若与 `requiredHeight` 动画同帧可能有一帧空白——用 `contentAlpha` 已能盖住，录制确认；② 用 `pointerInput` 吞事件时注意别把锚点区也吞了（锚点 pointerInput 在父 Box 上、先于子节点处理）；③ 回滚：删 `if` 与 `toolsInteractive` 两处即可，无数据影响。
- **预估**｜S
- **依赖**｜无（与 B-3 同文件，B-4 先做）。

---

## B-5 左上悬浮菜单采样错位（U10）

- **⚠ 与计划不符**｜计划点名 `GlassAnchoredMenu.kt`；但它已经是"同窗口 `Layout`、先量后排"的正确实现（`GlassAnchoredMenu.kt:69` 注释 "Same-page overlay, placed as a sibling AFTER the sampled page, never inside its capture"，`:103-147` 测量→`glassMenuPosition`→`place`）。真正的坏例子是左上分区/趋势选择器：`VerticalTopSelector` 用 **`Popup`**（`ui/common/VerticalTopSelector.kt:94-126`）。
- **问题**｜`Popup` 是独立窗口：菜单表面在弹窗内调 `searchGlassSurface(RoundedCornerShape(24.dp))`（`VerticalTopSelector.kt:103`），默认源是 `LocalSearchGlassBackdrop`（页面层捕获，`SearchGlassSurface.kt:20`），而该捕获属于主窗口坐标空间 → 弹窗内采样以弹窗原点（= 按钮左上角，`offset = IntOffset(0, 60.dp)`，`VerticalTopSelector.kt:97`）为基准，于是"背景从按钮边角开始"，与菜单最终位置错位。同类还有库页两个 `DropdownMenu`（`Screens.kt:598` 排序、`:635` 切换视图）——它们也是 Popup。
- **改哪里**｜`ui/common/VerticalTopSelector.kt:94-126`（Popup 段）；`ui/components/GlassAnchoredMenu.kt:49-147`（抽出可复用 host，逻辑不动）；`ui/components/GlassSource.kt:12-40`（KDoc 补一条硬规则）；`ui/components/SearchGlassSurface.kt:20`（注释：只允许同窗口使用）。
- **怎么改**｜
  1. 把 `GlassAnchoredMenu` 的 host 抽成内部可复用件 `GlassAnchorOverlay(anchorInRoot, gap, safePadding, onDismiss, content)`（纯搬运：`Layout` + `onGloballyPositioned { hostOrigin = it.positionInRoot() }` + `glassMenuPosition` + `place`），`GlassAnchoredMenu` 改成调它，行为零变化。
  2. `VerticalTopSelector` 去掉 `Popup`：按钮加 `onGloballyPositioned { anchor = it.boundsInRoot() }`，菜单改用 `GlassAnchorOverlay`（gap = 4dp、`safePadding = WindowInsets.safeDrawing.asPaddingValues()`、宽度上限 280dp、`heightIn(max = 360dp)` 保持）。
  3. **"菜单最终矩形怎样在一次布局后拿到"（本卡核心）**：`Layout` 的 measure 阶段 `val placeable = measurables.first().measure(Constraints(maxWidth = min(280dp, viewport.width), maxHeight = viewport.height))` → 此刻 `placeable.width/height` 就是**最终尺寸**；再用 `glassMenuPosition(anchor, IntSize(w, h), viewport, gap, direction)` 得最终偏移 → `placeable.place(x, y)`。一次布局内完成"先量后排"，不需要第二轮 measure。
  4. 采样对齐：玻璃表面画在**被 place 的那个节点**上（同窗口即按最终位置取样）；若后续要把矩形显式交给着色器（lens/高光），用 `Modifier.onGloballyPositioned { menuRect = it.boundsInRoot() }` 存进 `MutableState<Rect?>`——它在布局**之后**回调、只触发一次重组，正好是"一次布局后更新 backdrop 区域"；**不要**在 measure 里写 state（会无限失效）。
  5. 关闭语义照搬：全屏 `detectTapGestures { onDismissRequest() }`（`GlassAnchoredMenu.kt:106`）+ 菜单面 `pointerInput { detectTapGestures {} }`（`:126`）→ 点菜单外关闭且不穿透；`BackHandler` + Esc + 焦点回归（`GlassAnchoredMenu.kt:85-122`）保持。注意这个全屏层只存在于菜单打开期间、只用于 dismiss，不是 brief 禁止的"全局透明触摸层"，实现时也不要让它出现在其它状态。
- **测试**｜`glassMenuPosition` 已有纯函数：补三例——菜单宽 280 时靠右锚点不越界、视口高不足时回退到 `viewport.top`、上/下两种翻转；新增 `fun menuRect(placed: IntOffset, size: IntSize): Rect` 单测（同窗口最终矩形）。源码契约：`VerticalTopSelector.kt` 不再含 `Popup(`，且含 `onGloballyPositioned`。
- **验收**｜① 作品库左上分区菜单、发现页趋势菜单打开：玻璃折射内容与菜单背后实际像素一致（截图对比：按钮左上角不再是采样原点，菜单 200dp 高度处折射的是该处真实背景）；② 菜单位置 = 按钮下方 4dp、右缘不越出 16dp 边距、贴底时向上翻转且不出安全区；③ 点菜单外关闭、返回键关闭、Esc（外接键盘）关闭，关闭后焦点回按钮；④ 菜单打开期间点外面**不会**穿透点到底下的卡片；⑤ 收起后页面滚动位置不变。
- **风险/回滚**｜① Popup→同窗口 overlay 后 z 序必须 ≥ 内容（`zIndex(10f)`，与 `GlassAnchoredMenu` 一致），否则被底部栏遮住；② 两个页面各有一个 selector 实例，同页出现两个展开时需确认唯一性（现状 Popup 也一样，不是回归）；③ 库页两个 `DropdownMenu`（`Screens.kt:598/635`）是同类缺陷，但换掉 `DropdownMenu` 的 API 风险更高 → **本卡不做**，只在该处留 TODO 注释并列入批次 C 候选（避免一张卡改三处菜单）；④ 回滚：`VerticalTopSelector` 恢复 `Popup` 段即可，host 抽取可留着。
- **预估**｜M
- **依赖**｜无（与 B-1 无共享文件；`GlassAnchoredMenu` 抽取若与详情页其它改动并行，先合抽取再加 selector）。
