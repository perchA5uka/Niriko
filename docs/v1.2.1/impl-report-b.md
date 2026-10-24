# 批次 B 实现报告（v1.2.1，进行中）

> 日期：2026-10-18 · 范围：11 张卡（`docs/release-v1.2.1-batch-b.md`）
> 已交付：**全部 11 张卡**（B7 按「先做高频」的范围完成 3 个对话框 + 2 个弹层，其余列清单；B9B 可选项未做）。
> 验证：`compileDebugKotlin` ✅ · 全量 `testDebugUnitTest` ✅（**JDK 21**）。

---

## 1. Kyant Shapes：怎么内置的、为什么不是加依赖

**结论：能实现，但不能直接加 Maven 依赖；采用源码内置（Apache-2.0）。**

| 事实 | 细节 |
| --- | --- |
| 上游 | `Kyant0/Shapes` 1.2.1，坐标 `io.github.kyant0:shapes:1.2.1`，Apache-2.0 |
| 构建链 | AGP 9.3.2 / Kotlin **2.4.10** / Compose 1.12.0（CMP） |
| 本项目 | AGP 8.13.2 / Kotlin **2.3.10** / Compose BOM 2025.12.01 |
| 直接引 AAR 的风险 | Kotlin 2.4 产物的 metadata 2.3 编译器读不了（Module was compiled with an incompatible version） |
| 另一个坑 | `backdrop 1.0.6` 已**传递**依赖 `io.github.kyant0:shapes-android:1.2.0`（旧 APK 的 classes19.dex 里有 `com/kyant/shapes/Capsule`）→ 内置同包同名会 `Duplicate class` |
| 采用方案 | 把 1.2.1 的 11 个源码文件（1079 行）内置到 `app/src/main/java/com/kyant/shapes/`，逐文件加 provenance；在 `app/build.gradle.kts` 的 backdrop 依赖里 `exclude(group="io.github.kyant0", module="shapes-android")` |
| 兼容性核对 | 内置源码与 backdrop 依赖的 1.2.0 源码 **diff 全 0**；backdrop 只用 `RoundedRectangularShape`（Lens.kt） |
| 合规 | `THIRD-PARTY-NOTICES.md` 已加 Shapes 1.2.1 / Apache-2.0 条目 |

**用途**：B6 的玻璃开关与进度条改用 `Capsule` / `RoundedRectangle(..., Continuous)`；后续 B7 的连续曲率圆角也用它。

---

## 2. 每张卡

| 卡 | 状态 | 关键改动 |
| --- | --- | --- |
| B1 渐隐统一 | ✅ | `TopFadePolicy.windowMaskHiddenDp/feedTopPaddingDp`；作品库/发现/详情三处 mask 切换；删除 `TopFadeOverlay.kt` 死代码；统计/设置不加遮罩（裁决 1） |
| B2 骨架对齐 | ✅ | `LibrarySkeleton(topPadding)` 三分支 `top = topPadding + 4.dp`，与正式列表同顶距 |
| B3 搜索遮挡 | ✅ | 展开态 94% 实心底；`contentTopInset` 按 phase 让位（220ms）；两处过期注释改写 |
| B4 误触排序 | ✅ | 收起态不再组合行 2–4；动画期专用挡板（只盖行 2–4，非全屏） |
| B5 菜单采样 | ✅ | 抽出同窗口 `GlassMenuOverlay`（先量后排）；`VerticalTopSelector` 删掉 Popup |
| B6 玻璃控件 | ✅ | 设置 34 处 M3 Switch → `GlassToggle`（内置 Capsule + Role.Switch）；新增 `GlassProgressBar`，替换收藏卡/统计两处 6dp 进度条 |
| B7a token | ✅ | `NirikoShapes.Sheet=24dp`、`RowInner=4dp` |
| B7b 设置条目 | ✅ | `SettingsGroup` 令牌化（零视觉）；组容器/行内角统一 |
| B7c 二级页卡片 | ✅ | 外观/数据源/同步备份/刷新诊断 4 页分组卡 20→24dp |
| B7d 对话框 | ✅（3/24） | 同步冲突三选一、Steam API Key、壁纸长按操作 → `Sheet 24dp` + `surfaceContainerHigh`；其余 21 处列了清单未动 |
| B7e 弹层 | ✅（2/3） | `NirikoNonModalSheet` 24dp 字面量 → token；`SharePreviewSheet` 补 24dp 顶角与表面色 |
| B8 按钮一体化 | ✅ | 顶栏外壳挂到 `MainPager` pager 外层（`PagerTopBar`，Crossfade 150ms）；状态提升到 `LibraryControlsState`（含 Saver）；两份 SearchViewModel 上提；`showOwnTopBar` 保住独立路由 `subject_search` |
| B9A 编辑层贴顶 | ✅ | `sheetMaxWidth=Unspecified/dragHandle=null/contentWindowInsets={0}/shape=0dp` + 内容 `fillMaxHeight/statusBars/navBars` |
| B10 收藏按钮 | ✅ | 未收藏 Button 与已收藏 DetailDock 合并为 28dp 玻璃胶囊两态，overlay 悬浮 + `navigationBarsPadding`；暴露 `visible` 参数给 B11 |
| B11-Step1 Dock | ✅ | `BottomBarScroll` 成唯一派发器（删 11 处私有 acc）；手机完整滑出（offset+alpha，命中区随 placement）+ 宽屏 24dp/alpha→0/禁交互；补 5 个未上报容器 |
| B11-Step2 页面按钮 | ✅ | 顶栏按**同一个** `bottomBarHideFraction()` 向上退出（offset 64dp + alpha→0 + 全隐清语义）；详情收藏胶囊接入 `visible`（offset 隐藏，不再是 translationY）；搜索/弹层展开时 `forceShown` |
| B11-Step3 空白恢复 | ✅ | 新 `BlankTapRestore.kt`：Final-pass 观察，不消费事件、无全屏层、无 clickable；条件 = 无消费 + 位移 ≤ touchSlop + 非长按 |

---

## 3. 集成期间发生的事故与修复

1. **Screens.kt 被误删尾部**：一位成员用「整文件 read→write」时 read 被截断，删掉了 TagFilterBar/StatsScreen/Preview；随后 `git show HEAD` 还原，把 B1/B2 在该文件的改动一起冲掉。已由原作者用定点 edit 恢复（mask + skeleton topPadding），现全绿。
2. **两处契约测试是「朴素子串匹配」**：源码注释里保留了 `private var acc` 与 `translationY` 的说明文字，导致测试误红。已改成「先剥离注释行再断言」。
3. **`contentWindowInsets` 是 `@Composable () -> WindowInsets`**：B9A 初版传了值，编译失败；改成 `{ WindowInsets(0,0,0,0) }`。

---

## 4. 仍待真机确认

| 项 | 为什么 |
| --- | --- |
| B5 同窗口浮层命中 | 依赖「子节点越界仍可命中」（与搜索组件 requiredSize 同机制）；若不通，回退 `searchGlassSurface(source=null)` 平底 |
| Dock 方向手感 | 方向符号按旧 `acc + dy` 语义归一化（`available.y > 0 = DOWN`）；相反只改 `FloatingControlsState.intentOf` 一行 |
| 宽屏 Dock | 24dp + alpha→0 + 禁交互，需平板/折叠屏看是否重排 |
| B8 顶栏 Crossfade | 150ms 只切内容；两页按钮 x/y 是否真的不动 |
| B10 悬浮胶囊 | 与列表滚动、nav bar inset、B11 显隐的叠加 |

---

## 5. 执行中新增的取舍与发现

1. **对话框无法真玻璃化**：`AlertDialog`/`ModalBottomSheet` 有独立窗口，kyant backdrop 跨窗口捕获不到。真玻璃要 API 34 `WindowBlurBehindEffect` + 自绘 Dialog，已记录为独立小卡，不在本轮硬做。
2. **顶栏与 Dock 共用一个进度源**：`MainPager` 直接读 `bottomBarHideFraction()`，从结构上消除「Dock 藏了按钮还挡着」。
3. **详情页没有搜索表面**：卡里的「搜索展开强制显示」在详情页落成「收藏编辑弹层打开时 forceShown」。
4. **滚动方向必须带 density**：详情页用 3x 屏时，不换算会导致 1/3 距离就隐藏。
5. **`BlankTapRestore` 的边界**：只观察不消费、不装全屏层；代价是极端手势组合下可能不恢复（宁可不动，也不破坏点击）。

## 6. 下一步

1. B7d/B7e：对话框与弹层的玻璃化（先做 2 个高频对话框）；
2. B11-Step2/3：页面按钮接入 `visible`、空白点击恢复；
3. 真机走查上述 5 项；
4. 批次 C（新功能，需要 v33 迁移）。