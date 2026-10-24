# v1.2.1 · 批次 B 拆分（视觉与交互一致性 + 悬浮专项）

> **实现状态（2026-10-18）**：**11 张卡全部落地**，编译与全量单测通过（JDK 21）。B7 按「先做高频」完成 3 个对话框 + 2 个弹层，其余列清单；B9B（Dialog 匀速）为可选项未做。详见 `docs/v1.2.1/impl-report-b.md`。

> 11 张卡，全部**只改 UI 层**，**0 张动 Room**（v33 只服务批次 C）。
> 明细：`docs/v1.2.1/tasks-b-ui.md`（B1–B5）、`tasks-b-visual.md`（B6/B7/B9/B10）、`tasks-b-floating.md`（B8/B11）。
> 环境更新：本轮已用 **JDK 21（Android Studio JBR）** 跑通全量单测 —— 1220 项全绿（此前唯一的 `GlassProviderIdentityTest` 环境失败消失）。

## 0. 一页看完

| 卡 | 主题 | 预估 | 依赖 |
| --- | --- | --- | --- |
| B1 | 顶部渐隐四页统一（U5） | S | 无（与 A-X4/X5 同文件，需 rebase） |
| B2 | 骨架屏顶部对齐（U6） | S | B1 |
| B3 | 搜索态部件无上边界（U8） | M | B1；与 B4 同文件，B4 先做 |
| B4 | 搜索键展开误触排序（U9） | S | 无 |
| B5 | 左上菜单采样错位（U10） | M | 无 |
| B6 | 玻璃开关与进度条统一（U11） | M | 无（先于 B7b） |
| B7 | 表面清单与圆角 token 收敛（U12） | L（拆 5 批 B7a–B7e） | B6、B9 |
| B8 | 两页悬浮按钮一体化（U13） | M | 无（B11-Step2 依赖它） |
| B9 | 编辑层贴顶 + 匀速动画（U16） | S（A）/ M（B） | B10 |
| B10 | 收藏按钮两态统一并悬浮（U18） | M | 无（B9、B11 依赖它的 visible 接口） |
| B11 | 悬浮控件自动显隐（S1 专项） | L | B8 + B10 |

**合计 11 张：2 L + 5 M + 4 S**，粗估 2–3 周串行；B4/B5/B6/B8/B11-Step1 可并行。

## 1. 建议执行顺序

```
轨 1（UI 一致性）：  B4 ─▶ B3 ─▶ B1 ─▶ B2 ─▶ B5
轨 2（玻璃）：      B7a ─▶ B6 ─▶ B7b ─▶ B7c ─▶ B7d ─▶ B7e
轨 3（详情）：      B10 ─▶ B9A ─▶（可选）B9B
轨 4（悬浮）：      B11-Step1(Dock) ─▶ B8 ─▶ B11-Step2 ─▶ B11-Step3
```

为什么：
1. **B4 先于 B3**：两者同文件（搜索展开容器）；先修「收起态仍可点」再谈上边界，否则会被误触干扰验收。
2. **B1 先于 B2**：骨架要对齐的是「统一后的内容顶距」，先把常量与调用点收敛。
3. **B7a（token 收敛）先行且零视觉变化**：`NirikoShapes` 加 `Sheet=24dp`、`RowInner=4dp`，后面三类表面才有共同目标值。
4. **B10 先于 B9/B11**：收藏按钮先变成悬浮胶囊并暴露 `visible` 接口，B9 换宿主、B11 接管显隐都不必返工。
5. **B11 分三步**：Step1 只改 Dock（可并行）、Step2 接页面按钮（依赖 B8）、Step3 空白点击恢复（依赖 B10 的 FAB）。

## 2. 拆分阶段纠正的 20 处（挑最要紧的）

| # | 原计划写法 | 代码实际 |
| --- | --- | --- |
| 1 | U5「两套 hiddenHeight」 | 实为**三种**：作品库 56dp、发现/详情 0dp、统计/设置**完全没有遮罩** |
| 2 | U5 统一值直接改 | 与 `TopFadePolicyTest`（断言 `WINDOW_MASK_HIDDEN_DP==0f`）和 `ApprovedTopControlsTest`（字符串锁死内距）冲突，B1/B3 必须同卡改测试 |
| 3 | `TopFadeOverlay` 是没生效的渐隐 | 它自述是 legacy color scrim；真正生效的一直是 `TopContentAlphaMask`。删它是零运行时变化 |
| 4 | 骨架在 `Screens.kt:968` | 骨架在 `:930-976`（三分支 `top=4.dp`）；`:968` 只是画廊骨架里的一个 Box |
| 5 | 搜索展开时加 topInset 避让 | `contentTopInset` 是常量；两处注释声称的「相位避让」代码里不存在 |
| 6 | 误触排序发生在展开动画期间 | 根因是 COLLAPSED 时行 2–4 仍在组合树（`alpha=0` 但未裁剪），收起态点按钮下缘就命中 |
| 7 | 菜单采样问题在 `GlassAnchoredMenu` | 它是正确实现；真凶是 `VerticalTopSelector` 的 `Popup` 独立窗口 |
| 8 | 换 kyant 的开关/滑条 | kyant **库**没有开关/滑条，只有 demo（还依赖 `com.kyant.shapes`，本项目未引入）→ 改用已有 GlassToggle/GlassSlider |
| 9 | 进度条只有一处 | 实际 7 处 M3 `LinearProgressIndicator`，高度/圆角三套 |
| 10 | 编辑层不贴顶是 `skipPartiallyExpanded` 没开 | 早已 `true`；真因是 sheet 高度 = 内容高度 |
| 11 | 给 sheet 加 `animationSpec` | material3 1.4.0 的 `SheetState` 三个入口都**没有** `animationSpec`（javap 实证）→ 想匀速只能换 Dialog |
| 12 | 收藏按钮在 `:1700` | `:1700` 是注释，Button 在 `:1713` |
| 13 | B8 改 `UniversalSearchBar` | 它是**死代码**（只有一条未用 import）；真按钮是 `IosStyleSearchComponent`，发现页顶栏在 `SubjectSearchScreen.kt:353-378` |
| 14 | 「两个悬浮按钮」 | 每页各一对（库=分区+搜索、发现=趋势+搜索），状态都在页面私有域，必须先提升 |
| 15 | Dock 没有显隐 | 已有连续比例（8dp/alpha .85，宽屏 24dp），**永不真隐藏**；S1 是改语义，不是从零加 |
| 16 | 滚动容器都上报了 | 每个容器各持累加器却共写一个 fraction；还有 5 个容器没上报（DiscoverGrid/Poster、SearchResults 三处） |
| 17 | 宽屏 dock 也能完整滑出 | 它与 inset 绑定，完整滑出会重排 → 宽屏只做 24dp+alpha→0+禁交互 |
| 18 | `BottomBarScroll` 的 dy 符号可信 | 无单测、注释与代码未必一致 → 策略只吃 `ScrollIntent`，符号收在一行 adapter |
| 19 | 详情顶栏也纳入显隐 | 明确不纳入（不在清单内），给「不能纳入」表的理由 |
| 20 | B7 一次换完 | 实际 24 处 AlertDialog + 3 处弹层 + 3 套卡片圆角 + 设置条目 morph，必须分 5 批 |

## 3. 五条裁决（已替你定）

| # | 问题 | 决定 | 理由 |
| --- | --- | --- | --- |
| 1 | 统计/设置要不要加渐隐 | **不加**，只统一内容顶距 | 加遮罩 = 新增视觉，超出 U5「修边界」的范围；记录为后续提案 |
| 2 | 统一值 inset+56 还是 +64 | **inset + 56** | 与现有 56dp 常量、`ApprovedTopControlsTest` 一致；函数化后改一行即可 |
| 3 | B7a 是否加两个 token | **批准**：`Sheet=24dp`、`RowInner=4dp` 进 `NirikoShapes` | 纯 theme，零视觉变化，是后续三类的共同目标 |
| 4 | B9 先 A 还是直接 B | **先 A（贴顶，S）**，B 作为可选后续 | A 只 10 行；B 要自绘 Dialog 并自己接 predictive-back，风险高、本轮不必 |
| 5 | B11 是否纳入 5 个未上报容器 | **纳入 Step1** | 不上报就会「Dock 藏了、按钮没藏」，专项等于没做 |

## 4. 与批次 A 的冲突点（开工前看）

| 位置 | A 已改 | 对 B 的约束 |
| --- | --- | --- |
| `SettingsItem.kt` 的 `checked` 来源 | X3 首帧种子 | B6 只换控件，**不得改 checked 传参与首帧策略** |
| `SubjectDetailScreen` 收层三处 | X5 `recordSavedEvents`/`withFrameNanos`/`completionPlayback.isActive` | B9 换宿主必须逐字搬运，否则「庆祝中提前收层」回归 |
| `SubjectDetailScreen:2163` | X4 `VolumeProgressPolicy` | B9 只改外壳 |
| `TopFadePolicy` | 批次 A 已统一 `WINDOW_MASK_HIDDEN_DP` | B10 的悬浮 pill 不得复用 `topInset` 逻辑 |

## 5. 完成定义

1. `:app:assembleDebug` + `:app:testDebugUnitTest` 通过（用 JDK 21；本机当前全量 1220 全绿）。
2. 每张卡附「改动前/后」真机截图或明确的视觉差异描述；纯函数（渐隐常量、显隐方向判定）必须有 JVM 单测。
3. 不改导航/Dock 路由与点击行为；不新增依赖；不引入全屏透明层。
4. 若发现必须动 Room 或数据层，**停下来找 Lead**，不要自行扩范围。

## 6. 下一步

建议从 **B4（误触排序，S）→ B5（采样错位，M）→ B7a（token，S）→ B6（玻璃控件，M）** 这一组开始：全是小改、互不依赖，且能立刻在真机看到差异。B11 的 Step1（Dock）可以同时开。