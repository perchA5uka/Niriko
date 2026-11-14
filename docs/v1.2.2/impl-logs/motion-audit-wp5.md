# motion-audit · WP5（P0-2 菜单硬切 / P1-3 sheet 物理不连续 / P1-5 封面淡入 / F16 把手触感）

范围：ui/components/{GlassAnchoredMenu,NirikoNonModalSheet,CoverImage,SharedSubjectCover}.kt。未跑 gradle（Lead 统一编译），**未验证编译**。

## GlassAnchoredMenu.kt（223→323 行）
- P0-2：删掉 `if (!expanded || anchorInRoot == null) return`；GlassMenuOverlay 新增最后一个参数 `expanded: Boolean = true` + keepAlive 延迟卸载（退场动画播完才 keepAlive=false）；BackHandler 挪到 keepAlive 判断之后，关死后不再拦系统返回。
- 进场/退场 = 两个 Animatable（scale 1↔0.92/0.96、alpha 1↔0）+ graphicsLayer 绘制期读值，不进组合期（改造前的菜单根本没有动画，现在也不引入每帧重组）。
- 缩放中心由 Column 的 onGloballyPositioned + `glassMenuTransformOrigin(anchorInRoot, hostOrigin, positionInParent, coords.size)` 算出，只在值变化时写 state；alpha 初值 0 让「量几何那一帧」不可见，不会先闪在 Center 再跳到锚点。
- **偏离（有意）**：没有直接用 NirikoMotionSpecs.overlayEnter/overlayExit —— 它们是 AnimatedVisibility 的 EnterTransition/ExitTransition，而 AnimatedVisibility 在不可见/首帧不组合内容，菜单就没法先量出 menuSize，origin 只能退回 Center（从菜单中心胀大，正是要修的观感）。改为手写等值动画：scale 走 spatialDefault(进)/spatialFast(退)、alpha 走 effectsFast，数值逐字等于那两个工厂内部值；常量 MENU_ENTER_SCALE=0.92f / MENU_EXIT_SCALE=0.96f 带注释钉住这层等价关系。
- reduceMotion：snap 到终态并立即 keepAlive=false（无动画、无残留组合）。
- 调用点普查：GlassMenuOverlay 只有 2 处调用 —— GlassAnchoredMenu.kt:293（传 expanded）与 ui/common/VerticalTopSelector.kt:121。后者自己写 `if (expanded) { GlassMenuOverlay(...) }`，父级先摘组合树，**该选择器菜单仍无退场动画**；该文件不在本任务范围，只报告不修。

## NirikoNonModalSheet.kt（168→214 行）
- P1-3 跟手：`drag` Animatable 改成 `dragOffset: Float` 普通状态，draggable 的 onDelta 同步写（原来每个 pointer move 起一个协程 snapTo，高刷屏积压 → 手指与面板之间可见滞后）。
- 松手：手势位移原样搬进 `settle` Animatable（snapTo），再 `animateTo(target, initialVelocity = velocity)`；回原位用 appearSpring()、关闭用 spatialFast()；先归零 dragOffset 再动画 → 同一帧视线位置不变，无跳变。
- initialVelocity 直接传 draggable 给的 px/s，**不除 density**（Animatable 位移单位同为 px；除密度会把甩动速度改错一个密度倍数）。
- 回弹未停时再次按住：onDragStarted 把 settle.value 并进 dragOffset 再 snapTo(0)，避免画面跳一下。
- F16：拖动手势第一次开始时 performHapticFeedback(TextHandleMove) 一次（hapticFired 标记，onDragStopped 复位）；触感不受 reduceMotion 影响。
- reduceMotion：不做弹簧，直接 snap 到终点（只有关闭才 onDismiss）。
- 契约未动：SHEET_DISMISS_DISTANCE_DP=72f / SHEET_DISMISS_VELOCITY_DP=900f、shouldDismissSheet 签名、`RoundedCornerShape(topStart = NirikoShapes.Sheet, topEnd = NirikoShapes.Sheet)`（DialogAndSheetSurfaceTest 逐字钉住）。

## CoverImage.kt（197→216 行）
- P1-5：`.crossfade(false)` 保持关闭（CoverFlightPerformanceTest 逐字钉住；且共享飞行期再叠 Coil 淡入会拖影），改为「首张成功图到达时淡入自绘 alpha」：arrived + `animateFloatAsState(tween(COVER_IMAGE_CROSSFADE_MS=150).orSnap(reduceMotion || flightActive))` → `graphicsLayer { alpha }`，只改绘制。
- flightActive = `sharedTransitionScope?.isTransitionActive == true`：飞行期走 snap，避免「图飘着变透明」。
- onSuccess 里 `arrived = true` 放在原有 failed=false / rememberCover 之前；契约串（placeholderMemoryCacheKey / Dimension.Undefined / subjectCoverPathBounds / .aspectRatio(aspectRatio)+.then(sharedModifier) 的换行缩进）全部原样保留。

## SharedSubjectCover.kt（52→70 行）
- 同一套自绘 alpha 淡入（tween 150ms，orSnap(reduceMotion)），`.crossfade(false)` 保留；契约串（overrideFor/subjectId、rememberCover、intrinsicWidth/Height、memoryCacheKey）未动，也未引入 aspectRatio。

## 风险 / 未验证
- **未验证编译**：无编译权，未跑 gradle；以下均为静态自查结论，不是编译或运行证据。
- 新增的 glassMenuTransformOrigin / overlayEnter·overlayExit / NirikoPressSpecs 目前**没有单测**；现有 GlassAnchoredMenuTest 只测 glassMenuPosition 纯函数。建议 Lead 编译后补一条 origin 纯函数测试（锚点落在菜单右下 / 左上时的 pivotFraction）。
- GlassAnchoredMenuTest 的源码契约串已逐条核对仍在（BackHandler 只是移到 keepAlive 之后，位置变了、文字没变）。
- `positionInParent()` 语义：菜单 Column 的直接父节点就是 GlassMenuOverlay 的 Layout，与 place() 的坐标同一套；以后有人在中间插 wrapper 层，origin 会算偏（当前无此结构）。
- 退出竞态：首次展开后「origin 还没量出来就立刻关闭」时，effect 里 `if (menuOrigin == null) return` + keepAlive 保留会走到正常退场；该路径无测试。
- 每个可见封面多一个 graphicsLayer（alpha 层）：纯图片卡多一层绘制成本，需真机滚列表复核 P95（未在本次范围实测）。
- SharedSubjectCover 拿不到 SharedTransitionScope，飞行期无守卫（只有 reduceMotion 下 snap）；若飞行中图片恰好网络加载完成，理论上会有淡入拖影，概率极低（飞行通常发生在内存缓存命中时）。
- 未做真机目视：菜单 0.92 起缩+锚点 origin 是否「从按钮长出来」、sheet 甩动初速是否接得住、封面 150ms 淡入是否可感，都要 Lead 编译后上机确认。

## 给 Lead 的真机验收项
1) 详情页「分享 / 传送门」菜单：点开从按钮处长出、关闭缩回按钮，不再硬切闪断；点外部与返回键关闭同样有退场。
2) 锚定菜单打开时快速二次点击：不出现「菜单已消失但点在空处」的死区（keepAlive 期间外层仍吃掉点击）。
3) 非模态 sheet：快速下甩立即飞出（不顿一下再从 0 起跑）、过 72dp 慢拖松手回弹到 0、拖动开始有一次轻震。
4) 封面：列表→详情飞行中封面不透明；直接进详情页封面 150ms 淡入。
5) 打开「减少动态效果」后三项全部瞬切、无震动以外的残留动画。