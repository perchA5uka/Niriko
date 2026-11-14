# Niriko v1.2.2 视觉/动效/反馈（审计 + 实施）

> **状态（2026-10-31 第八批后）**：审计已完成（`00`–`06`，只读阶段未改代码）；实施已完成八批，本文档集已同步到当前源码。
> 逐工作包的落地内容、验证结果、未做项与真机风险见 `impl-summary.md`（第八批在 §6.11），交叉评审复审见 `review-visual.md` 开头的「2026-10-31 复审」，差距审计快照见 `gap-report-2026-10-31.md`。
> 版本号已升到 1.2.2 / versionCode 5，发布记录在 `docs/release-v1.2.2.md`。
> 本轮新增的审计输入 `gap-report-2026-10-31.md` 是**历史快照**（2026-10-31 10:00–10:25 的源码），其「未修」结论多数已被第八批与并行分支覆盖；最终状态以 `impl-summary.md` §6.11 为准。
>
> 以下「只读审计」段落保留为历史设计输入。全部内容基于 `86dd21b`（v1.2.1）源码逐行核对 +
> 真机截图目视 + 两个上游参考仓（kyant0 `AndroidLiquidGlass-kmp` / `Shapes-1.2.1`、MiuiX 0.9.4）。
> 审计期间**未修改 `app/src` 下任何文件、未跑 Gradle**；临时文件 `tmp/_audit_p1.txt`、`tmp/_audit_p2.txt` 可删。

## 0. 怎么读这份东西

| 想知道什么 | 看哪个文件 |
|---|---|
| 项目长什么样、有哪些页面/材质/组件/动画 | `00-inventory.md`（架构与现状地图：**19** 条路由、六条材质渲染路径、P0-P2 初判，文末 §13 有 16 条事实纠正） |
| **「不协调」到底哪里不协调** | `01-visual-consistency.md`（25 条，含组件形态统一表 20 行） |
| **「缺动画」到底缺在哪** | `02-motion-and-transitions.md`（A 全量清单 → B 缺失过渡 → C 令牌 → D 帧阶段 → E reduceMotion） |
| **「点了没反应」到底哪些点** | `03-interaction-feedback.md`（16 条 + 15 条截图目视 S01–S15） |
| kyant0 的东西还能抄什么 | `04-kyant-reference.md`（6 条可落地 + 9 条不采纳，含一条崩溃级技术边界） |
| HyperLight / 外部优秀动效怎么借 | `05-hyperlight-and-external-motion.md`（6 条 + 7 个外部参考拆解） |
| **先做什么、做多久、怎么验收** | `06-roadmap.md` |
| 设计系统真实 token / 数值 / 约束 | 会话内 skill：`niriko-ui-system`、`niriko-motion`、`niriko-ui-review` |

## 1. 执行摘要：为什么不协调（五个根因，一个判据）

用户一眼看出的「设置页排列外形和玻璃卡片不搭」「二级页各种不一致」，往下挖都落到这五条上。
**它们不是审美问题，是装配问题**——组件和令牌大多已经写好了，只是没接上。

### R1 形状/排版令牌「双轨制」

`ui/theme/Theme.kt:271-275` 的 `MaterialTheme(...)` **没有传 `shapes`**（全仓仅此一处 `MaterialTheme(`）：

```kotlin
MaterialTheme(colorScheme = colorScheme, typography = NirikoTypography, content = content)
```

于是 M3 默认 `Shapes(4/8/12/16/28)` 与自研 `NirikoShapes(Card20/Section16/Control12/Sheet24/RowInner4/Pill)` **两套同时有效**：

- `RoundedCornerShape(数字)` 字面量 **144 处**，`NirikoShapes.` 只被引用 **28 处**（且 9 处集中在 SettingsGroup、6 处 StatsSkeleton）；
- 结果：同一个「列表卡」在作品库是 20dp 手写、在骨架屏是 12dp、在锚定菜单是 20/24dp 两种、在详情选集是 `shapes.small`=8dp；
- 同一屏内水平缩进三种（设置页 **组卡 0dp 贴边 / 组标题 16dp / 标题与搜索框 20dp**，`SettingsScreen.kt:99-131/153`、`SettingsGroup.kt:214-239`）——这是用户说「设置页排列外形和玻璃卡片不协调」的**直接原因**，因为组卡比标题宽出 20dp，视觉上像没对齐的贴纸。

> 注：`NirikoShapes` 是 **object（Dp 常量 + Shape 工厂，`ui/theme/NirikoShapes.kt:48-113`）**，不是 M3 `Shapes` 实例 —— 所以「补 shapes」不是一行赋值，而要新增 `NirikoMaterialShapes: Shapes` 映射表（`extraLarge` 该映射到 `Card(20)` 还是 `Sheet(24)` 需拍板，见 §3 D4）。

### R2 统一组件写好了，但没装配

| 组件 | 状态 |
|---|---|
| `GlassSegmentedContainer`（`GlassControls.kt:144`） | **0 个调用点**；实际分段选择器有 4 套并存 |
| `GlassSlider` | 全站只有 `GlassRatingSlider` 内部用 1 次；设置页「壁纸柔化」、搜索筛选、剧集评分仍是 M3 `Slider` |
| `GlassToggle` | 只有 2 处调用；别处 3 处 M3 `Switch` |
| `NirikoAnimation.kt` 旧令牌 | 8 个 spec **零调用**（`pressSpring/appearSpring/panelSpring/countTween/tabFadeIn/Out/enterFromRight/exitToRight`） |
| `headlineSmall` | 未进 `Type.kt` → 走 M3 默认 24sp/**400**，导致设置二级页标题「更大却更淡」 |

「定义了但没人用」比「没有」更糟：review 时以为已有单一来源，实际每个页面各自为政。

### R3 材质层级失守（瓷 vs 玻璃）

`FULL` 档下 `GlassCard.kt:167-198` 让**所有** `appleGlassCard`（**25 个调用点**；`GlassCard.kt:97` 定义，早前口径 61 是含 `GlassSectionCard`/`searchGlassSurface` 等兄弟函数的粗计数，已被 `00-inventory.md §13` 纠正）都升级到 L2 真折射（vibrancy→blur12→lens20/40）：

- 设计意图「列表卡=瓷、浮层=玻璃」被代码细节抹平；
- 同屏实时 `RenderEffect` 轻易 >2（`niriko-motion` 的性能红线）；
- 观感上就是「满屏都在流动」，没有主次，也没有「玻璃只应出现在该厚的地方」的层次。

### R4 状态切换没有过渡层（`AnimatedContent = 0`）

全仓 `AnimatedContent` **一次都没用过**。`AnimatedVisibility` 13 处、`animateItem()` 7 处、`Crossfade` 2 处——够覆盖出场，但**所有「状态机型」切换天然是硬切**：

加载态↔空态↔错误态↔正文、列表↔网格、骨架↔真实卡、折叠区展开/收起（`SubjectDetailScreen.kt:2363-2369` 的 `CollapsibleSection` 就是 `if (!collapsed) content()`）、锚定菜单出现（`GlassAnchoredMenu.kt:193` 同上）……

这是 P0「跳变」的共同根因，不是巧合。

### R5 反馈宿主缺失 + 反馈通道不成体系

- 全仓 `SnackbarHost` **只有 10 处**，而作品库/发现/搜索/统计页**都没有** → 批量删除、改状态、加入分区、ICS 导出**全部静默成功**；
- 命中区小于 48dp：SelectionChip 24-28dp、统计页「刷新」≈20×14dp、日历日期条 38dp、搜索关闭钮 44dp；`minimumInteractiveComponentSize` **全仓 0 命中**；
- haptic 有死通道：`PressTilt.kt:62 hapticEnabled = false` 默认关，18 个调用点无一传参（`LocalHapticFeedback` 真实取用 6 处、`HapticFeedbackType` 7 处，半数计数曾被 import 行污染，见 `00-inventory.md §13`）；
- 保存成功用了 `LongPress` 触感（规范要求 `Confirm`），语义错位。

### 判据（写进 v1.2.2 验收）

> **任何状态切换，屏幕上都必须有东西在 0→1 之间变化；任何可点区域，视觉边界内外都不能出现"点了没反应"。**

## 2. 文档资产与交叉引用

| 文档 | 条数 | 分级 | 备注 |
|---|---|---|---|
| `00-inventory.md` | 现状地图 | — | **19** 条路由、四页 chrome、六条材质路径、09 节初判清单、13 节事实复核 |
| `01-visual-consistency.md` | **25** | P0 3 / P1 16 / P2 6 | + 组件形态统一表 20 行、刻意保留 9、不建议做 5、未验证 8 |
| `02-motion-and-transitions.md` | **17** 条缺失过渡（652 行） | P0 2 / P1 10 / P2 5 | + 全量清单 A（≈96 动效调用点）、**审计时**令牌越界 21 spring + 20 tween（第八批后实测：24 spring / 业务字面量 0，29 tween / 手写数字 3）、reduceMotion 覆盖率 27%（后补 19 处守卫 + 6 回顶调用点）、七批 ≈9.5 人日 |
| `03-interaction-feedback.md` | **16** 条反馈缺口 | P0 3 / P1 7 / P2 6 | + 截图目视 15 条（S01–S15） |
| `04-kyant-reference.md` | **6** 条可落地 | P1 4 / P2 2 | + 9 条不采纳 + 1 条崩溃级技术边界（N7） |
| `05-hyperlight-and-external-motion.md` | **6** 条可落地 | P1 3 / P2 3 | + 7 个外部参考拆解、6 条不采纳、9 条未验证 |

原始条数合计 **70**；同一问题在多文档重复出现的 6 组（剧集折线无图例 V-19/S06、骨架圆角 V-25/B-6、折叠区块 B-1、设置行按压 05-M1/03-F14 邻近、分区标题 V-15、命中区 F05/F07/S04/S08）已互相标注，
**`06-roadmap.md` 按根因归并成 12 个工作包，不再按文档计数。**

## 3. 三条必须先拍板的决策（会改变方案形状）

| 决策 | 选项 | 影响 |
|---|---|---|
| **D1 瓷/玻璃分层要不要收** | (a) 收：列表卡/组卡/图表容器一律 L1 瓷，只有浮层与 dock 走 L2 玻璃 (b) 不收，只把同屏 RenderEffect 压到 ≤2 | 收 → 视觉主次立刻清楚，但要动 `GlassDecision` + 25 个 `appleGlassCard` 调用点的档位；不收 → 改动小，但「满屏流动」不变 |
| **D2 设置二级页要不要横向推入** | (a) 要：复用现成 `enterFromRight/exitToRight`（零调用），只对 10 条 `settings_*` 路由 (b) 不要：二级页仍共享轴 Z，靠组卡缩进统一来"感觉是一个体系" | 要 → 空间层级更明确；不要 → 与 M3 推荐一致但用户可能觉得"点进去没动静" |
| **D3 `NirikoAnimation.kt` 8 个零调用令牌** | (a) 删（推荐）(b) 保留 `appearSpring` 并真接上 sheet 回弹，其余删 | 影响 `NirikoNonModalSheet.kt:151` 一处字面量回弹是否归位 |
| **D4 M3 `Shapes` 映射表怎么定** | `extraSmall 4 / small 8 / medium 12 / large 16 / extraLarge ?` —— `extraLarge` 映射 `Card(20)` 还是 `Sheet(24)` | 决定「所有仍在用 `MaterialTheme.shapes.*` 的 M3 组件」集体落到哪一档；选错会让弹层顶角从 24 变 20 |

## 4. 与既有约束的一致性

- 方案**不新增时长档位**：六档（spatial/effects × fast/default/slow）够用，02 只补 `micro()`、`orSnap()`、`contentTransform()`、`overlayEnter/Exit()` 与 3 个命名常量；
- **刻意保留不动**：封面共享转场 200ms 与 10ms 关键点、底栏 220ms 隐藏、详情 350ms 材质渐显、dock 的 kyant 弹簧（velocity/10 squash）、21 个 AlertDialog 圆角、设置页 20/24dp 双档本身（改的是缩进，不是圆角）；
- **不做**：滚动联动 blur、列表卡 3D 跟手倾斜、可交互玻璃形变（`Lens` 只接受 `CornerBasedShape`，`squircle()` 会抛 `UnsupportedOperationException`，见 04-N7）、全站 `AnimatedVisibility` 逐个加 `orSnap`。

## 5. 截图基线问题（必须知道）

`screenshot/` 下 17 张图的 mtime 全为 **2026-09-25**，而 HEAD `v1.2.1 = 86dd21b (2026-10-24)`、`v1.2.0 = 3c58aa3 (2026-10-06)` —— **这批截图早于两个版本**，
且有四处渲染与 HEAD 源码直接矛盾（标签全实心绿 vs `TagChip.kt:37-77` 玻璃片；分类明细混「已看集数」vs `StatsOverviewSection.kt:116-151` 已拆卡；「匹配度 0%」vs `ProviderBindingSection.kt:316-317` 已修；发现页「· 点此强制刷新」vs `TrendingSection.kt:63-67` 已删）。

**结论**：截图只能当"历史观感参考"，不能当 v1.2.2 验收基线。`03` 文档 §6.6 已列重拍清单（8 张，在 `86dd21b` 上）。

## 6. 实施状态（v1.2.2 已落地部分）

| 工作包 | 状态 | 证据 |
|---|---|---|
| WP0 基础设施（M3 Shapes 映射、headlineSmall、动效/按压常量、菜单锚点、设置推入策略） | ✅ 完成 | `impl-summary.md` §1 |
| WP1 设置页对齐 + 行按压 + 禁用态 | ✅ 完成 | `impl-logs/settings-ui.md` |
| WP2 详情页三处破相 + 折叠动效 | ✅ 完成 | `impl-logs/detail-ui.md` |
| WP3 反馈宿主 + WP4 多选可见 | ✅ 完成 | `impl-logs/feedback-multiselect.md` |
| WP5 菜单 / 面板 / 封面 / 把手触感 | ✅ 完成（第八批把旧 `GlassAnchoredMenu` 迁到 `NirikoMenu` + `GlassMenuOverlay`） | `impl-logs/motion-audit.md`、`impl-summary.md` §6.11 |
| WP6 形状令牌收口 | ✅ 完成（83 处等值字面量 → 六档令牌，零像素变化；`ShapeLiteralPolicyTest` 第三次重算：剩 58 处无档位字面量） | `impl-summary.md` §6.7 |
| WP7 材质分层 | ⛔ 刻意不做（D1 用户裁决「不收」；只留 `GlassRenderEffectBudgetTest` 不新增构造点） | `impl-summary.md` §7 / §10 |
| WP8 控件语言统一 | ✅ 完成（输入框 37 调用点 → `NirikoTextField`；分段 → `NirikoSegmentedControl`；chip → 四档 `Niriko*Pill`；菜单 → `NirikoMenu`；开关/滑块 → `GlassToggle`/`GlassSlider`/`GlassRangeSlider`；搜索框 → `NirikoSearchField`；M3 控件归零） | `impl-summary.md` §6.5 / §6.11、`ControlSourceTest` |
| WP9 动效令牌 + 状态切换 | ✅ 完成（A.8 业务弹簧字面量 21→0；手写 tween 数字剩 3；B-10/B-12/B-13/B-14/B-15/B-16 全部落地） | `impl-summary.md` §6.9–§6.11 |
| WP10 按压/跟手反馈 + 命中区 | ✅ 完成（行按压、toggle/slider 拖动与按压、5 主卡 `hapticEnabled`、F-07/F-08/F-10/F-12/F-15 补点） | `impl-logs/kyant-ref.md`、`impl-summary.md` §6.11 |
| WP11 reduceMotion 收口 | ✅ 完成（两批共 19 处守卫 + 6 个回顶调用点接线；`ReduceMotionCoverageTest` 零白名单扫描） | `impl-logs/wp11-reduce-motion.md` |
| WP12 图表 / 四态 / 骨架 / 日历 | ✅ 完成（骨架、错误态、评分分布、P1-1、V-21 统计数字、V-22/V-23/V-24/V-25、B-13 四态、B-15 日历切月） | `impl-summary.md` §6 / §6.11 |
| WP13 控件语言 A 方案（外观四项：分段/chip/菜单/统计数字） | ✅ 完成（四项各有唯一实现 + 护栏测试；旧 `GlassSegmentedContainer`/`TagChip`/`GlassAnchoredMenu` 删除） | `impl-summary.md` §6.11、`DetailTypeScaleContractTest` |
| WP14 帧阶段 / 预测返回 / 搜索卡（A.9 / B-9 / B-11） | 🟡 部分完成（A.9 帧阶段全部迁出组合期；B-9 依赖 navigation-compose 2.8.5 自带 seek + `PredictiveBackSupportTest`，**真机未验**；B-11 只做字面量/reduceMotion/帧阶段，**结构重写未做**） | `impl-summary.md` §6.11 / §7、`PredictiveBackSupportTest`、`SearchCardMotionContractTest` |
| 真机矩阵与截图基线 | ⬜ 未做（无设备；仓库无截图测试设施） | `impl-summary.md` §7 / `06-roadmap.md` §7 |

验证：前七批结束时 `compileDebugKotlin` / `testDebugUnitTest`（195 类 / 1482 项 / 0 失败）/ `spotlessCheck` / `assembleDebug` 全部成功；
第八批门禁（2026-10-31）：`spotlessApply spotlessCheck testDebugUnitTest assembleDebug` 同一条命令 **BUILD SUCCESSFUL in 5m 53s**，单测 **210 类 / 1551 项 / 0 失败 / 0 错误 / 0 跳过**（`TEST-*.xml` 12:11 本轮结果），APK **32,742,166 B（≈31.2 MiB）**（`versionName=1.2.2` / `versionCode=5`，已复制 `dist/niriko-v1.2.2-debug.apk`）。

**未跑真机截图与性能测量**。
