# WP11 · reduceMotion 收口（搜索卡 / 筛选面板 / 趋势标签 / 顶栏 / 回顶）

目标：给「入场/退场 + 状态切换」类动画补 reduceMotion 降级；**直接操作跟手、按压触感、一次性反馈一律保留**。
统一用顶层扩展 `AnimationSpec<T>.orSnap(reduceMotion)`；AnimatedVisibility 的 EnterTransition 用 `EnterTransition.None` / `ExitTransition.None`。

## 逐文件

### app/src/main/java/com/otakup/niriko/ui/common/IosStyleSearchComponent.kt（13 处）
- composable 顶部读一次：`val reduceMotion = LocalReduceMotion.current`（:149，紧跟 searchFocusRequester 之后）。
- 卡片几何：cardW(:179) / cardH(:189) / anchorX(:231) / anchorY(:241) / anchorSize(:246) 共用 `cardSpring` → `cardSpring.orSnap(reduceMotion)`；reduceMotion 时尺寸硬切换，不再有 400-stiffness 的生长过程。
- 内容浮现：reveal(:198 spring 0.8/300)、contentAlpha(:204 tween 240)、rowsAlpha(:209 tween 220)、rowsShift(:214 tween 220)、fakeAlpha(:412 tween 200)、indSettle(:438 tween 260)、inputAlpha(:472 tween 260) → 逐个 `.orSnap(reduceMotion)`。
- X 关闭按钮 AnimatedVisibility(:636-647)：enter/exit 改成 `if (reduceMotion) EnterTransition.None / ExitTransition.None`，正常分支的 fadeIn(160)+slideInHorizontally(spring 0.7/420)、fadeOut(140)+slideOutHorizontally(200) 逐字未改。
- **保留**：`dragOffsetX`（DRAGGING 时 -maxTravelDp*progress）是手指 1:1 位移，不经过任何 spec；指针手势/`SearchPhase` 状态机/`shouldComposeMenuRows` 挡板逻辑均未动；尺寸常量与布局走向未动（只加守卫）。

### app/src/main/java/com/otakup/niriko/ui/subject/SearchFilterPanel.kt（2 个 AnimatedVisibility）
- `SearchFilterPanel`(:85) 与 `BrowseFilterPanel`(:311) 各读一次 `LocalReduceMotion.current`。
- 两处 `fadeIn+expandVertically / fadeOut+shrinkVertically`(:118-131、:344-357)：reduceMotion 时直接用 `EnterTransition.None/ExitTransition.None`（面板瞬时出现/消失）；非降级分支的 `Spring.StiffnessMediumLow` 数值逐字未改。

### app/src/main/java/com/otakup/niriko/ui/common/SearchToolbar.kt（1 处）
- `TrendTab` 选中底色 `animateColorAsState(spring(0.6,400))`(:141) → `spring<Color>(...).orSnap(reduceMotion)`，并在 :138 之前加注释说明属「状态切换」而非按压反馈。

### app/src/main/java/com/otakup/niriko/navigation/MainPager.kt（1 处）
- `topBarHideProgress` 的 220ms tween(:156) → `tween<Float>(220).orSnap(reduceMotion)`；:153 读 `LocalReduceMotion.current`。
- 判定为状态切换而非跟手：`shown` 翻转后整条顶栏走固定 220ms 位移（不是逐帧跟手）；且底栏在 MainActivity.kt:286-297 已用 `settings.reduceMotion` 硬落位，只补底栏会让「底栏瞬切、顶栏仍滑 220ms」。**没有改 220ms 数值**。
- 保留：HorizontalPager 手势、beyondViewportPageCount=1、切页 collapse 的 LaunchedEffect 均未动。

### app/src/main/java/com/otakup/niriko/util/ScrollToTop.kt（2 个函数签名，待接线）
- `LazyListState.beginScrollToTop(reduceMotion: Boolean = false)`(:29)、`LazyGridState.beginScrollToTop(...)`(:50)：`animateScrollBy(-distance, SPRING.orSnap(reduceMotion))`(:34/:61)；远距离本来就是 `scrollToItem(0)` 直接落位。
- 这两个是 suspend 工具、读不到 CompositionLocal，只能由调用方传值；默认 `false` → 现有调用点不改也照常编译、行为不变。
- **待接线（不在本轮文件清单内，请 Lead 决定是否派工）**：ui/screens/Screens.kt:384-386、ui/stats/StatsScreen.kt:191、navigation/TabReselectPolicy.kt:177/192（再上层是 ui/search/TrendingSection.kt:175、ui/search/DiscoverPosterPane.kt:109、ui/search/DiscoverGridPane.kt:95 的 `applyTabReselectTo`）。

## 未做 / 拿不准
- 未跑 gradle（按约定由 Lead 统一编译）；本日志只描述改动，不对编译结果做判断。
- ScrollToTop 的 6 个调用点未改 → 重选回顶的弹簧滚动目前仍不响应 reduceMotion，需要接线后才生效。
- 所有动画时长/弹簧数值一个没动；只是把既有 spec 在 reduceMotion 下换成 snap。
