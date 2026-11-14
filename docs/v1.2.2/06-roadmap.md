# v1.2.2 实施路线图（分批 / 依赖 / 验收 / 风险）

> 输入：`01-visual-consistency.md`（25 条）、`02-motion-and-transitions.md`（16 条缺失过渡 + A 全量清单）、
> `03-interaction-feedback.md`（16 条 + 15 条截图目视）、`04-kyant-reference.md`（6 条）、`05-hyperlight-and-external-motion.md`（6 条）。
> 五份文档原始条数 70，**按根因归并成 12 个工作包**（跨文档 6 组重复已合并）。
> 本文档只排计划，不含实现。

## 0. 一句话策略

**先止血（P0 破相 + 静默失败），再收口（形状/材质/控件/动效四套令牌），最后打磨（图表与四态）。**
批次 A 是「用户能指着屏幕说出来的那几条」，批次 B 是「说不出来但能感觉到的系统性不协调」，批次 C 是小版本随手带。

## 1. 批次总览

| 批次 | 主题 | 工作包 | 人日 | 上线建议 |
|---|---|---|---|---|
| **A 止血** | 一眼可见的破相与静默失败 | WP1–WP5 | **5.5** | v1.2.2 必上 |
| **B 收口** | 令牌双轨 → 单一来源 | WP6–WP11 | **11.5** | v1.2.2 能上多少上多少，按 WP 独立可发 |
| **C 打磨** | 图表、四态、骨架、日历 | WP12 | **2.0** | v1.2.3 |

合计 ≈ **19 人日**（扣掉重复条目后）。若只做「最小可交付」= WP1 + WP2 + WP5 + 02 的批次 1-2，约 **4 人日**即可显著改善观感。

### 2026-10-31 第八批实际完成（WP → 状态 → 证据）

| WP | 状态 | 证据文档 / 源码入口 |
|---|---|---|
| WP1 设置页对齐 | ✅ 完成 | `impl-summary.md` §2 |
| WP2 详情页三处破相 | ✅ 完成 | `impl-summary.md` §3 |
| WP3/WP4 反馈宿主 / 多选可见 | ✅ 完成 | `impl-summary.md` §4 |
| WP5 菜单 / 面板 / 封面 / 触感 | ✅ 完成（第八批迁到 `NirikoMenu` + `GlassMenuOverlay`） | `impl-summary.md` §5 / §6.11 |
| WP6 形状令牌 | ✅ 完成（剩 58 处无档位字面量，精确基线护栏） | `impl-summary.md` §6.7 |
| WP7 材质分层 | ⛔ 刻意不做（D1） | `impl-summary.md` §7 |
| WP8 控件语言统一 | ✅ 完成（输入框 / 分段 / chip / 菜单 / 开关滑块 / 搜索框） | `impl-summary.md` §6.5 / §6.11 |
| WP9 动效令牌 + 状态切换 | ✅ 完成（A.8/A.9/B-10/B-12/B-13/B-14/B-15/B-16） | `impl-summary.md` §6.9–§6.11 |
| WP10 按压 / 跟手 / 命中区 | ✅ 完成（含 F-07/F-08/F-10/F-12/F-14/F-15） | `impl-summary.md` §6.11 |
| WP11 reduceMotion 收口 | ✅ 完成（19 处守卫 + 6 个回顶调用点） | `impl-logs/wp11-reduce-motion.md` |
| WP12 图表 / 四态 / 骨架 / 日历 | ✅ 完成 | `impl-summary.md` §6 / §6.11 |
| WP13 控件语言 A 方案（外观四项） | ✅ 完成 | `impl-summary.md` §6.11、`docs/v1.2.2/prototype-pending-changes.html` |
| WP14 帧阶段 / B-9 / B-11 | 🟡 部分（A.9 全部迁出组合期；B-9 有护栏但**真机未验**；B-11 结构重写未做） | `impl-summary.md` §6.11 / §7 |
| 真机矩阵 + 截图基线 | ⬜ 未做（无设备、无截图设施） | §7 / §8 |

## 2. 批次 A · 止血（5.5 人日）

### WP1 · 设置页对齐骨架（1.5 人日）

- **覆盖**：V-05（P0）、V-07、V-09、V-16、V-06、V-08
- **目标**：一屏之内只有**一种**水平缩进、一种行形态、一种 chevron 尺寸。
- **改动**：`ui/settings/SettingsScreen.kt:99-131/153`（title/搜索框 20dp→统一 inset，组卡加左右 padding）、`ui/settings/SettingsGroup.kt:151-161/169-207/214-239`、`ui/settings/AdaptiveSettingsPane.kt:144-148/190-245`、`ThemePackSection.kt:171/195`
- **新建**：`SettingsPageHorizontalInset = 16.dp`（放 `SettingsGroup.kt` 或 `ui/theme/NirikoShapes.kt` 同层度量文件）、`ui/settings/SettingsChevron.kt`（唯一 chevron）
- **验收**：新 `SettingsLayoutContractTest`（断言 10 个二级页的组卡水平 padding == 组标题 padding）；真机 411dp 走查设置首页 + 外观页滚动
- **风险**：组卡从「贴边」改为「有边距」会让玻璃卡变窄，需复看 FULL 档折射边缘是否有漏光

### WP2 · 详情页三处破相（1.0 人日）

- **覆盖**：V-17（P0 标题被裁）、V-18（P0 结论条串行）、V-20（P1 评分分布裸图）、V-15（分区标题语言）
- **改动**：`ui/subject/SubjectDetailScreen.kt:4182-4190`（标题移出 `heightIn+verticalScroll`）、`ui/subject/EpisodeRatingChart.kt:576-622`（`FlowRow` + `maxLines=1`）、`ui/subject/RatingComparisonSection.kt:164-213`（包 GlassSectionCard）、`SubjectDetailScreen.kt:1354`
- **验收**：320dp / 411dp 两档真机目视；`EpisodeRatingChart` 相关既有单测不回归
- **风险**：标题移出滚动区会改变「详细信息」卡的内边距节奏，需与 `GlassSectionCard` 的 contentPadding 一起调（建议 16→0 内层自 padding）

### WP3 · 反馈宿主统一（1.5 人日）

- **覆盖**：F03（P0 无 SnackbarHost）、F02（P0 刷新失败清空数据）、F06（P1 分享永久「生成中…」）、F05（P1 统计刷新命中区）
- **改动**：`ui/NirikoNavSuite.kt`（顶层统一 `hostState` + `SnackbarHost`）、`SubjectDetailViewModel.kt:1955-1962`、`ExternalRatingSection.kt:92-94`、`SharePreviewSheet.kt:62-96`、`CalendarCard.kt:589-596`
- **新建**：`ui/common/LocalNirikoFeedback.kt`（`CompositionLocal` + `rememberNirikoFeedback()`，四种语义：成功/失败/撤销/提示）
- **验收**：新 `FeedbackHostContractTest`（断言四个顶层页在 `NirikoNavSuite` 内均可解析到宿主）；真机：批量删除、改状态、ICS 导出、分享失败各有文案
- **风险**：`Snackbar` 会被底部 dock 遮挡 → 需 `snackbarHost` 加 dock inset（复用 `VERTICAL_DOCK_INSET_DP`）

### WP4 · 多选可见性（0.5 人日）

- **覆盖**：F01（P0 列表多选零反馈）、F13（多选时长按仍弹浮层）、F04（长按浮层进度+无上限）
- **改动**：`ui/library/Screens.kt:483-487/995-1004`、`ui/library/CollectionCard.kt:27-45`
- **新建**：`ui/library/SelectionRing.kt`（`drawWithContent` 内描边，**零位移**，与网格 `PosterGridCard.kt:81-86` 的 3dp 主色描边对齐）
- **验收**：真机列表视图进入多选后每卡可见描边 + 已选计数；长按在多选态不再弹历史浮层

### WP5 · 两个「每次都用」的硬切（1.0 人日）

- **覆盖**：02-B-1（折叠区 `CollapsibleSection` 整块啪）、02-B-2（`GlassAnchoredMenu` 无进出场）
- **改动**：`SubjectDetailScreen.kt:2363-2369`、`ui/components/GlassAnchoredMenu.kt:193`
- **新建**：`ui/components/GlassMenuOrigin.kt`（纯函数 `glassMenuTransformOrigin(anchor, host)`）、可选 `ui/components/CollapsibleContent.kt`
- **验收**：新 `GlassMenuOriginTest`（四象限）；真机：展开简介滚动位置不移位、菜单从锚点长出来
- **风险**：折叠区动画与 `LazyColumn` 重排叠加可能抖 → 验收项含「展开时首可见项不跳」

## 3. 批次 B · 收口（11.5 人日）

### WP6 · 形状令牌收口（2.0 人日）

- **覆盖**：R1 / V-01 / V-23 / 全仓 144 处字面量
- **改动**：`ui/theme/Theme.kt:271-275`（**补 `shapes = NirikoMaterialShapes`**，新增映射表，不是直接塞 `NirikoShapes` —— 它是 object，不是 M3 `Shapes`）、`ui/theme/NirikoShapes.kt`（补 `Chip` 档）
- **映射表（需 D4 拍板）**：`extraSmall=4 / small=8 / medium=Control(12) / large=Section(16) / extraLarge=Card(20)`（若选 `Sheet(24)`，所有弹层顶角会从 24 变 20）
- **做法**：先补 `shapes` 参数（一行，立刻让 `MaterialTheme.shapes.*` 走 Niriko 档），再按「高频组件优先」迁移字面量：列表卡 20 → `NirikoShapes.Card`、输入框 12 → `Control`、弹层 24 → `Sheet`、chip 统一 `Chip`
- **验收**：`NirikoShapesTest` 扩（三档真值 + 断言 `MaterialTheme.shapes.medium == Control`）；新 `ShapeLiteralAuditTest`（源码扫描：`ui/` 下 `RoundedCornerShape(数字)` 白名单计数只能降）
- **风险**：**这是全仓影响面最大的一条**（`MaterialTheme.shapes.small/medium/large` 的现有调用会集体换值）→ 必须单独 PR + 全量单测 + 7 组主题矩阵目视。建议先在 `feature/shape-token` 分支上跑 `assembleDebug` 看编译期影响

### WP7 · 材质分层（1.5 人日）

- **覆盖**：R3 / V-04（需 D1 拍板）
- **改动**：`ui/components/GlassDecision.kt:48-50`、`GlassCard.kt:167-198`
- **做法**：`GlassDecision` 增 `cardRole: CardRole { PORCELAIN, GLASS }`，`appleGlassCard` 暴露同名参数默认 `PORCELAIN`；列表卡/组卡/图表容器显式 `PORCELAIN`，浮层/dock `GLASS`
- **验收**：`GlassDecisionTest` 加档位真值表（LIGHT/FULL × PORCELAIN/GLASS 四组合）；真机 FULL 档下同屏 RenderEffect 计数 ≤2（开发者「GPU 呈现模式」看 `RenderEffect` 层数）
- **风险**：`appleGlassCard` **25 个调用点**（`GlassCard.kt:97`）视觉全变（变「哑光」）→ 需 7 组主题 × 3 档材质矩阵截图对比（见 `niriko-ui-review` skill）。**注意另有 13 处 `GlassSectionCard`、21 处 `searchGlassSurface` 是不同函数**，不要一起改（`00-inventory.md §13`）。

### WP8 · 控件语言统一（2.5 人日）

- **覆盖**：V-10（38 处 OutlinedTextField）、V-11（分段 4 套 / `GlassSegmentedContainer` 0 调用）、V-12（chip 5 副面孔）、V-13（开关滑块只覆盖设置页）、V-14（菜单三套）
- **新建**：`ui/components/NirikoTextField.kt`（`Control` 12dp + 无描边 + 玻璃可选）、`ui/components/NirikoSegmented.kt`（包 `GlassSegmentedContainer`）、`ui/components/NirikoChip.kt`（合并 `TagChip`）
- **做法**：按文件迁移，一次一个屏幕（DataSource 6 → SyncBackup 3 → SubjectDetail 8 → …），每屏目视后提交
- **验收**：新 `ControlSourceTest`（源码扫描禁止新 `OutlinedTextField(` / `Switch(` / `Slider(` / `FilterChip(`）；`GlassChipTokensTest` 扩
- **风险**：`NirikoTextField` 要保留 M3 的可访问性语义（`textFieldSemantics`），别自己 `BasicTextField` 造

### WP9 · 动效令牌 + 状态切换（2.5 人日）

- **覆盖**：02-B-3/4/5/6/7/9/10/11/12、A.8 的 21 spring + 20 tween 越界
- **改动**：`ui/animation/NirikoMotionSpecs.kt`（补 `micro()`、`orSnap()`、`contentTransform()`、`overlayEnter/Exit()`、`COVER_IMAGE_CROSSFADE_MS`、`CHROME_HIDE_DURATION_MS`、`DETAIL_CHROME_DURATION_MS`）、`NirikoNonModalSheet.kt:151`、`SubjectDetailScreen.kt:540/2374`、`SearchToolbar.kt`、`StateComponents.kt`、`MainPager.kt:148-153`
- **顺序建议**（按 02-F 的感知收益）：折叠/菜单（已在 WP5）→ sheet 初速 + 图片淡入 + 星级触发点 + TouchGlow 取消 → 详情/统计 `AnimatedContent` → 视图切换 + chrome + 预测返回 → 搜索卡（单独 PR）
- **验收**：`NirikoMotionSpecsContractTest`（新：禁止裸 `spring(`/`tween(` 出现在 `ui/` 业务文件）、`PredictiveDismissTest`、真机 10 条路径走查
- **风险**：`AnimatedContent` 会重建内容 → 详情 hero 封面若在 `AnimatedContent` 内会重建（02-B-5 已标注），必须把 hero 留在外层

### WP10 · 按压/跟手反馈 + 命中区（2.0 人日）

- **覆盖**：05-M1/M2/M3、F07（命中区 <48dp）、F14（haptic 死通道）、F08、F09、F12、F16
- **改动**：`SettingsItem.kt:150/196/242/315`、`SettingsScreen.kt:265`、`AdaptiveSettingsPane.kt:205`（6 处 ripple → 行按压）、`GlassControls.kt:88-118`（toggle 拖拽 + 高光 `angle` 接通）、`PressTilt.kt:58-64`（haptic 接通）、`SubjectDetailScreen.kt:2846-2877`（SelectionChip 48dp）
- **新建**：`ui/components/NirikoRowIndication.kt`（`IndicationNodeFactory`）、`ui/animation/NirikoPressSpecs.kt`（`ROW_PRESS_ALPHA = 0.08f`、`THUMB_PRESS_SCALE = 1.08f`、`SLIDER_THUMB_PRESS_SCALE = 1.10f`）、`GlassToggleMath.kt`（拖拽结算纯函数）
- **验收**：`GlassToggleMathTest`（拖拽 5 例 + 速度结算）、`GlassSliderMathTest` 保持通过；真机：开关能拖动、拖出 slop 松手也切换
- **风险**：行按压换掉 ripple 会失去 M3 的「水波」可访问性提示 → 用色块 + 触感替代，需在 `niriko-ui-review` 里记录该决策

### WP11 · reduceMotion 收口（1.0 人日）

- **覆盖**：02-E（覆盖率 27% → 目标 90%）
- **做法**：落地 `AnimationSpec.orSnap(reduceMotion)` + `animatedFloatAsState(...)` 助手，替换 8 个「必须 snap」点；分类判据（入场/状态切换必 snap，跟手/按压保留）写进 skill
- **验收**：新 `ReduceMotionCoverageTest`；真机开「移除动画」走查 10 条路径
- **风险**：`orSnap` 若在 `@Composable` 外读 `LocalReduceMotion` 会崩 → 助手签名必须显式收 `reduceMotion: Boolean`

## 4. 批次 C · 打磨（2.0 人日）

### WP12 · 图表 / 四态 / 骨架 / 日历

- **覆盖**：V-19（折线同色无图例）、V-21（统计数值五档）、V-22（胶囊三形态）、V-24（错误态不对称）、V-25（骨架圆角跳变）、02-B-13/14/15/16、V-03（7-10sp 正文）、V-02（39 处色值越界）
- **新建**：`ui/components/StatNumber.kt`、`StateHost.kt`、`ChartPalette` 二色（`ui/theme/` 已有 `ChartPaletteTest`）
- **验收**：`ChartPaletteTest` 扩（两条线对比度 ≥3:1）、`StateHostTest` 四态切换不闪
- **说明**：V-02 的色值越界建议单独开 `ThemeColorAuditTest`（源码扫描 `Color(0x`），与 WP12 拆开做

## 5. 依赖与并行

```
WP6 形状令牌 ──┬──> WP8 控件语言（输入框/分段/chip 都要形状档）
               └──> WP12 图表圆角
WP9 动效令牌 ──┬──> WP5 折叠/菜单（overlayEnter/Exit 依赖令牌）
               └──> WP11 reduceMotion（orSnap）
WP7 材质分层 ──> 视觉矩阵验收（依赖 WP1 完成后的设置页稳定版）
WP3 反馈宿主 ──> WP10（命中区与文案出口一起评审）
无依赖可并行：WP1 / WP2 / WP4
```

**并行纪律**：`app/src/main` 同文件不并行改；`WP6`（Theme.kt/NirikoShapes）与 `WP8`（大量 UI 文件）必须串行，否则 `spotless` 与编译冲突频发。

## 6. 每批验收命令（Windows，仓库根）

```powershell
git status --short
.\gradlew.bat :app:compileDebugKotlin --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain      # 1419 项 JVM 单测
.\gradlew.bat :app:spotlessCheck --console=plain          # 报错先 spotlessApply
.\gradlew.bat :app:assembleDebug --console=plain          # 首次/clean 后必跑
```

- 定向：`--tests "*SettingsLayoutContractTest*"`、`--tests "*GlassDecisionTest*"`（类名写错会报 no tests found，不算通过）
- **不要并行开两个 Gradle 进程**（已配 parallel + configuration-cache，多 Agent 时必须 Lead 统一跑）
- CI 只跑 Spotless + 单测 + assembleDebug，不跑 lint/detekt/release/真机 → 真机矩阵必须人工走查

## 7. 需要人工走查的矩阵（无截图测试基础设施）

仓库内 **没有** roborazzi / paparazzi（`app/` 与 `gradle/libs.versions.toml` 均 0 命中）→ 所有视觉验收只能真机。

| 维度 | 取值 |
|---|---|
| 屏幕宽 | 320 / 360 / 411 / 600 / 840 dp |
| 主题 | 7 组（`niriko-ui-system` skill 列出的主题包） |
| 玻璃档 | LIGHT / FULL / OFF（`LocalGlassLevel`） |
| 动态效果 | 开 / 系统「移除动画」 |
| 字体 | 100% / 130%（`fontScale`） |

> **2026-10-31 状态：仍未验证。** 第八批没有设备，矩阵无任何记录；`screenshot/` 仍是 2026-09-25 旧图，不能当 v1.2.2 验收基线。

## 8. 风险表

| 风险 | 影响 | 缓解 |
|---|---|---|
| WP6 补 `shapes` 后所有 `MaterialTheme.shapes.*` 调用集体换值（`medium 12→12`、`large 16→16` 恰好不变，但 `extraLarge 28→20/24`、`small 8→8` 需逐项核对） | 高 | 映射表先单测钉住 5 档真值，再改 Theme；**绝不把 `Pill(999)` 放进 `extraLarge`**（会让所有 M3 组件变胶囊）；单独 PR |
| WP7 全库变哑光，用户觉得「玻璃没了」 | 高 | 分两档灰度：先只把**列表卡**改 PORCELAIN，看观感再决定组卡 |
| WP9 `AnimatedContent` 重建内容导致 hero 封面闪 | 中 | hero 留在 `AnimatedContent` 外层；`SharedEntryRestorationTest` 必须通过 |
| WP10 去掉 ripple 影响可访问性 | 中 | 行按压加 `semantics { role = Role.Button }` + 触感；在 `niriko-ui-review` 记录 |
| 截图基线不可用（早于 v1.2.1） | 中 | 实施前先在 `86dd21b` 重拍 `03` §6.6 的 8 张；**2026-10-31 仍未重拍** |
| 第八批新引入的视觉风险（SectionHeader 12dp 节奏 / StatNumber 主色薄涂 / GlassRangeSlider 双 thumb / 日期条 48dp / hero 单击延迟 / 剧集评分 600ms 落库） | 中 | **全部未验证**（需真机）；清单见 `impl-summary.md` §10 第 1–6 条 |
| 19 人日超出 v1.2.2 窗口 | 中 | 「最小可交付」= WP1 + WP2 + WP5 + 02 批次 1-2 ≈ 4 人日 |

## 9. Definition of Done（每个 WP）

1. `git status` 干净、单测全绿、`spotlessCheck` 通过；
2. 新增/扩展的契约测试**先红后绿**（禁止只写实现不写测试）；
3. 该 WP 覆盖的条目在对应审计文档里划掉，并在 `06` 更新实际人日；
4. 真机走查表（第 7 节矩阵的子集）截图留档到 `docs/v1.2.2/after/`；
5. 若偏离审计方案，必须在 PR 描述里写「为什么偏离」——审计文档是设计意图的记录，不是不可改的教条。

> **第八批（2026-10-31）DoD 记录**：新增护栏测试 **15 个测试类 / 约 72 个用例**（`GlassMenuOverlayTest` 替代 `GlassAnchoredMenuTest`，净增 14 类；清单见 `impl-summary.md` §8.5），另重算 `ShapeLiteralPolicyTest`（精确基线 58 处）与 `MotionLiteralPolicyTest`（`allowedSpringCounts` 归零）两套基线；对应条目的审计勾选见 `01`/`02`/`03` 文件开头的「2026-10-31 复审状态」。**唯独第 4 条（真机走查截图留档）没有完成**。
