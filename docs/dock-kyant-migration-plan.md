# Dock：搬迁 kyant LiquidBottomTabs（悬浮胶囊组件）

> 目标：把底部导航整个换成 kyant 的 LiquidBottomTabs 实现（原样搬迁），
> 获得其完整交互：指示器快速跳到触控 tab、跟手移动、指示器是“可扩张的玻璃胶囊”。
> 先只做计划，确认后再动代码。

---

## 一、要搬的是什么（不是“拿 kyant 效果套现有 dock”）

kyant 的 LiquidBottomTabs 是自包含的悬浮胶囊组件，核心三块：

1. **胶囊背景层**：整颗胶囊 vibrancy + blur + lens，按下时 panelOffset 轻微左右滑动。
2. **指示器（可扩张玻璃）**：
   - 宽度 = 1/tabCount；
   - 按压时 scaleX/scaleY 放大（demo 里 pressedScale ≈ 78/56 ≈ 1.39）；
   - 按下/拖动时 lens/highlight/shadow/innerShadow 强度随 pressProgress 增加；
   - 用 layerBlock 做“速度挤压”（velocity squash）；
   - 指示器折射的是“整窗 + tabs 内容”的合并 backdrop（rememberCombinedBackdrop(backdrop, tabsBackdrop)），所以它能把 tab 的文字/图标也折射出来。
3. **交互**：
   - DampedDragAnimation：指示器在 0..tabCount-1 间阻尼移动；
   - 在非当前 tab 上按下 → animateToValue(目标index) 快速滑到该 tab；
   - 拖动 → 指示器跟手（dragAmount.x / tabWidth）；
   - 松手 → 吸附到最近 tab；
   - InteractiveHighlight：指尖处有跟随光晕（我们已在卡片做过简化版）。

---

## 二、要搬迁的文件（从 kyant demo 拷进 Niriko）

外部项目源码位置：
- app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
- app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTab.kt（含 LocalLiquidBottomTabScale）
- app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt
- app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/InteractiveHighlight.kt
- app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/DragGestureInspector.kt（InteractiveHighlight 依赖的 inspectDragGestures）

落到 Niriko 的：
- ui/bottombar/LiquidBottomTabs.kt
- ui/bottombar/LiquidBottomTab.kt
- ui/bottombar/utils/DampedDragAnimation.kt
- ui/bottombar/utils/InteractiveHighlight.kt
- ui/bottombar/utils/DragGestureInspector.kt

依赖说明：
- kyant 库已引入（1.0.6），提供 drawBackdrop / vibrancy / blur / lens / highlight / shadow / innerShadow / rememberLayerBackdrop / rememberCombinedBackdrop / layerBackdrop / Capsule —— 全部可用。
- 需确认 1.0.6 里 rememberCombinedBackdrop 的签名（demo 用的是 2.0.1；1.0.6 的 CombinedBackdrop.kt 已存在，签名应一致，必要时微调）。

---

## 三、NirikoBottomBar 改成薄壳

保留 NirikoBottomBar 作为与 pagerState 对接的一层，内部直接用搬来的组件：

    NirikoBottomBar(pagerState, backdrop = windowKyantBackdrop) {
        LiquidBottomTabs(
            selectedTabIndex = { pagerState.currentPage },
            onTabSelected = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            backdrop = windowKyantBackdrop,
            tabsCount = TopLevelDestination.entries.size,
        ) {
            // RowScope 内渲染每个 tab（图标 + 文字），用 LiquidBottomTab 或自定义
            TopLevelDestination.entries.forEach { ... }
        }
    }

- 删掉现有 NirikoBottomBar 里的手动指示器 offset/scale、自绘 innerShadow、border、shadow。
- 保留滚动隐藏联动（bottomBarHideFraction）：可在胶囊外层再包一层 translateY/alpha（现有逻辑）。
- 保留 tab 内容文案/图标（TopLevelDestination.titleRes / selectedIcon / unselectedIcon）。

---

## 四、MainActivity 变更

- 新增 kyant 全窗口 backdrop：windowKyantBackdrop = rememberKyantLayerBackdrop { drawContent() }。
- 把包裹“壁纸 + Scaffold 内容”的内层 Box 从 miuix .layerBackdrop(glassBackdrop) 改成 kyant .kyantLayerBackdrop(windowKyantBackdrop)。
- NirikoBottomBar(backdrop = windowKyantBackdrop)。
- 详情页/GlassSectionCard 仍用 miuix，不动。

---

## 五、会得到的行为/效果

1. **指示器“可扩张玻璃”**：默认宽度=一个 tab；按压/拖动时放大（最高约 1.2–1.4 倍），是一块带 lens/高光/内厚度的玻璃胶囊，而不是现在的静态指示块。
2. **跟手 + 快速跳转**：在非当前 tab 上按下 → 指示器快速滑到该 tab；按住拖动 → 指示器始终跟随手指；松手吸附到最近 tab。
3. **指尖光晕**：拖动时触点有跟随光晕。
4. **胶囊本身完整液态玻璃**：vibrancy + blur + lens；按下时整颗胶囊轻微左右摆（panelOffset）。
5. **tab 内容被指示器折射**：指示器通过 CombinedBackdrop 折射“整窗 + tabs 内容”，连 tab 图标/文字也会被玻璃折射。
6. **速度挤压**：快拖时胶囊有挤压/回弹的“液体”手感。

---

## 六、风险与降级
- 部分逻辑（DampedDragAnimation / InteractiveHighlight / DragGestureInspector）来自 demo 而非库，需要拷贝并适配包名/依赖。
- 1.0.6 与 2.0.1 可能有 API 差异（如 rememberCombinedBackdrop / Capsule / EaseOut），编译时按实际签名微调。
- 与 pagerState 联动：现有“翻页到位指示器放大”会由 LiquidBottomTabs 的 animateToValue 承担，需验证与 Pager 动画是否同频。
- 滚动隐藏、tab 按压光晕要保留在外层，避免被覆盖。

---

## 七、实施顺序
1. 拷贝组件/工具文件到 ui/bottombar，编译过（先处理包名/import/API 差异）。
2. MainActivity 加 windowKyantBackdrop，dock 用 kyant backdrop。
3. NirikoBottomBar 换成 LiquidBottomTabs，接 pagerState + tab 内容。
4. 保留滚动隐藏联动，删旧指示器逻辑。
5. 对照 demo 调参数（scale、lens 强度、highlight 透明度）。
