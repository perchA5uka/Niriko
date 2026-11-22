# Niriko 27 · 设计语言重建与工程质量计划

> 版本：2026-11-22 · 基线 HEAD `4da85c0`（= origin/main，工作树干净，无未提交改动）
> 状态：**方向已定案**（2026-11-22，见 §12 决策记录）；尚未开工。
> 范围：**只出计划，不改源码**。本文取代 `docs/ui-prototype-plan-2026-11.md` 作为设计方向的唯一入口；
> 那份文件里仍然有效的排期（批 B 分享卡 / 批 C 七天合并 / 批 D 角色与关系图 / LG-1）已折进本文 §9 的 N 项，不另开一套。
> 事实基线：772 个 Kotlin 文件 / 约 11.7 万行；Compose BOM 2025.12.01、Kotlin 2.3.10、AGP 8.13.2、minSdk 26 / target 34 / compileSdk 37；
> 依赖里已有 kyant `backdrop 1.0.6`、miuix `blur 0.9.0`、Vico 3.2.0、kizitonwose calendar 2.10.1、shimmer 1.5.0、Coil 2.7.0、Telephoto 0.19.0。

---

## 0. 一句话结论

现在的 Niriko 不是「没有玻璃」，而是**各层玻璃各自为政、规矩太散**：列表卡、详情区块、区块降级、无封面区块底，四处底色不透明度各写各的，
弹窗却还是 Material 默认白底，间距的事实基数是 10dp 而不是 8，圆角有 11 种取值（59 处字面量），按钮按下有 5 套以上的反应。
本计划要把「拼起来的玻璃」改成一整套**分层的光**——先有一份母版（single source of truth），
再逐层收敛实现，最后逐页套用，每一层都有可执行的验收。

---

## 1. 先诊断那份计划：它为什么「审美差」

现有 `docs/ui-prototype-plan-2026-11.md` 并不是不用心，它的问题在**方法论**上，六条：

| # | 问题 | 具体表现 | 后果 |
|---|---|---|---|
| 1 | **拿现状当标准** | §0「只取仓库现有 token」——圆角只取 20/16/12/999，字号只取 `Type.kt` 现有层级 | v1.2.2 里为避免改观感而保留的历史妥协（4dp 行圆角、15/16 双档字阶、10dp 间距）被写成「规范」，问题被制度化 |
| 2 | **只做加法** | 增加关系图、角色页、三张分享卡、按钮改造；没有一条「删除 / 合并」预算 | 页面越做越满，而「信息密度过高」正是看起来不好看的主因之一 |
| 3 | **原型不是母版** | 10 张原型是 Python 脚本画的静态 PNG（`prototypes/gen_*.py`）与 SVG 线框 | 落地时每人各自理解，观感在实现期二次发散；原型与 Compose 之间没有共享代码 |
| 4 | **没有目标态** | 全文没有一句「这套语言为什么长这样」，只有「沿用 72px 外边距」 | 遇到新页面就没有判据，只能继续问用户 |
| 5 | **验收只有一把尺** | 「差 8dp 以上算 bug」+ 对比度 4.5:1 | 缺密度、层级、动效、字体缩放、玻璃同屏预算的矩阵 |
| 6 | **决策甩回用户** | 结尾 4 条「需要你拍板」没有推荐值与代价 | 计划停在原地等拍板，等于没排期 |

**新计划的方法**：定母版 → 收敛实现 → 逐页套用 → 用矩阵验收。每一层都给**判据 + 数字 + 改动面**，
而不是「看起来顺眼」。

---

## 2. 目标语言：Niriko 27

名字只是代号，指「本应用自己的 2026 代语言」。参考 2026 年 WWDC 之后 iOS 27 的液态玻璃走向，
但**不照抄苹果的皮肤，只借它的秩序**。

### 2.1 行业事实（决定我们改哪几个参数）

2026 年 6 月 WWDC26 之后，iOS 27 对液态玻璃做了二次调整，公开报道的要点：

1. **折射更均匀、对比度提高**，复杂内容在玻璃下层时仍能看清；
2. **新增透明度 / 着色滑条**（设置 → 外观 → Liquid Glass），从「极致清晰」到「完全着色」连续可调；
3. 玻璃从「一层厚罩子」改为**多层独立材质**（图标最明显：每层各有材质与折射，不再统一覆盖）；
4. 新壁纸语言 "Celosia"：大面积流动重叠的曲面 + 柔和阴影；
5. 系统图标重绘、状态栏图形更圆润。

对我们的直接含义：**降不透明度、加对比保护、把玻璃做成多层可调参数、把曲面语言用到背景与空态上**。

### 2.2 六条原则（本项目的判据）

1. **光是层，不是罩。** 任何玻璃面至少有「折射 / tint / 受光 / 阴影」四个可分辨的层，层序全站一致；
   不许用单层 alpha 糊出一个「看起来像」的效果。
2. **玻璃服从文字。** 文字区先满足可读性：正文对比 ≥ 4.5:1；玻璃在文字背后必须自动加深 tint 或加 scrim，
   不允许「字压在封面纹理上碰运气」。
3. **连续曲率。** 圆角用平滑圆角（squircle），并且**同心**：内层圆角 = 外层圆角 − 内边距（下限 4dp）。
   这是 iPhone 与 Material 观感差最大的细节，也是本仓库写好却没用起来的资产。
4. **一屏一个主角。** 每屏只允许一个「最大声」的元素（大标题、封面、或大数字），其余降一档。
   详情页 / 统计页现在的问题是 3–4 个元素同时抢注意力。
5. **动效解释空间。** 每次转场回答「从哪来、到哪去」：位移说明层级，形变说明容器，弹性说明可中断。
   不许把淡入淡出当万能胶。
6. **触感即确认。** 可逆操作（切换、选中、翻面）都有触感 + 形变；不可逆操作（删除）只有触感 + 明确视觉代价。

### 2.3 与现有资产的对应

| 语言要素 | 现状 | 本计划动作 |
|---|---|---|
| 平滑圆角 | `NirikoShapes.squircle()` 已实现（`NirikoShapes.kt:129`），**全仓 0 处调用** | 接进 7 档形状令牌，卡片 / 胶囊 / 弹层全量切换（S2） |
| 折射玻璃 | kyant backdrop 16 文件 + miuix 12 文件 + 直接 RuntimeShader/RenderEffect 8 文件 | 收成「卡 / 浮层 = kyant，全幅区块 = miuix」两套，第三套进封装（S1） |
| 玻璃档位 | `GlassEffectLevel` 三档枚举 + `CardGlassLevel`，无连续调节、无按背景自适应 | 新增连续 tint 滑条 + 自动对比（S1 + N1） |
| 大标题 | 顶部 56dp 固定栏（`LibraryTopBar.kt:88`），全仓 `LargeTopAppBar` 0 处 | 引入 collapsing large title（S6 + N6） |
| 动态字体 | `fontScale` 全仓 0 处引用 | 字号全走 sp，固定高度改 `heightIn`（S4） |
| 图标 | Outlined 183 / Filled+Default 66 / AutoMirrored 13 / Rounded 1 混用 | 单一线形图标集 + 4 档尺寸（S3） |

---

## 3. 令牌层：要新建 / 改写的部分

令牌是新语言的「法律」，全部放在 `ui/theme/`，并用 Policy 护栏测试锁死（§7.2）。

### 3.1 材质层级（新建 `SurfaceLevels.kt`，已落地于 S0）

| 层 | 名称 | 用在哪 | blur | tint α（浅 / 深） | 边缘 | 阴影 |
|---|---|---|---|---|---|---|
| L0 | `Canvas` | 页面底 | — | 壁纸 / `surface` | 无 | 无 |
| L1 | `Section` | 全幅区块、设置分组 | 20dp | 0.06 / 0.10 | 1dp 受光 12% | 无 |
| L2 | `Card` | 列表卡、统计卡 | 16dp | 0.10 / 0.14 | 1dp 受光 16% | 0 4dp 12dp 8% |
| L3 | `Floating` | 底栏、dock、浮动按钮 | 24dp | 0.14 / 0.18 | 1dp 受光 22% | 0 8dp 24dp 12% |
| L4 | `Modal` | sheet、dialog、菜单 | 28dp | 0.20 / 0.24 | 1dp 受光 26% | 0 16dp 40dp 18% |

关键改动（2026-11-22 S1 查证后更正）：原稿此处写「`LightGlassBase = White α0.85` 是卡片底色」，**这是错的**——该令牌零引用，已删除。
卡片真实底色是 `appleGlassCard` 里 α0.14–0.42 的亮度自适应值，并不厚。真正要解决的是各表面不透明度各自为政（见下表「现状实测」），
而不是把「厚罩子」换成「分层」。下面的 L1–L4 是**目标梯子**，是否收敛过去要等真机对照（S1b）。
换成 **L2 = blur + tint α0.10 + 受光 + 阴影** 的叠加。结果会明显更透、更有层次；
深色模式也不存在「回退 `surfaceContainer`」：`appleGlassCard` 深色走黑色 tint（α0.22–0.42），那条头部注释已过期并已更正。

**现状实测（2026-11-22，默认观感，亮度自适应的卡片给出区间）**

| 表面 | tint 来源 | 浅色 α | 深色 α |
|---|---|---|---|
| 列表卡 `appleGlassCard` | 白 / 黑 | 0.14–0.32 | 0.22–0.42 |
| 详情区块（真折射）`GlassSectionCard` | `surfaceContainerHigh` | 0.50 | 0.38 |
| 同一区块的降级 `BlurredGlassSurface` | `surfaceContainerHigh` | 0.35 | 0.45 |
| 无封面时的区块底 | `surfaceContainerHigh` | 0.75 | 0.55 |

同一个「区块」会因为有没有折射源在 0.50 / 0.35 / 0.75 之间跳；区块比卡片更实是有意的（压在封面大图上要保文字可读）。

表内数值是**起始值**，S1 在真机上调参后定稿并回写本表。它们不是凭感觉拍的：以 iOS 27「提高对比度」为前提，L2 的 tint 上限由 4.5:1 反推（tint 后背景与 `onSurface` 的对比不得低于 4.5），验证见 §10.3。

### 3.2 形状（改写 `NirikoShapes.kt`）

| 档 | 半径 | 形状 | 用途 |
|---|---|---|---|
| `Micro` | 4dp | squircle(0.6) | 行内小块、徽标底 |
| `Control` | 12dp | squircle(0.6) | 输入框、小按钮 |
| `RowInner` | 同心值 | squircle(0.6) | 分组内行的**按压高亮**形状 = `concentric(Card, 内距)`；常态不再有行圆角（见下） |
| `Section` | 16dp | squircle(0.6) | 次级卡片 |
| `Card` | 20dp | squircle(0.6) | 主卡片 |
| `Sheet` | 28dp | squircle(0.7) | 弹层、dialog |
| `Pill` | 50% | 胶囊 | 按钮、chip、dock |

同心规则（纯函数 + 单测）：

```kotlin
fun concentric(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(4.dp)
```

现状反例：`SettingsGroup.kt:56-66` 的分组是「对齐 Kazumi SplitListGroup」的做法——行与行之间留 4dp 缝（`RowGap`）、行自带 4dp 小圆角、按压时 morph 成 20dp。
这是 **M3 Expressive 的分段列表语言**，放进玻璃卡里就成了「一张卡里又切了几块小卡」。iOS 式分组是**一整块连续面 + 内缩发丝分隔线**（分隔线左端与文字对齐）。
S3 的 `NirikoRow` 按后者做；这会替换掉设置页现有的圆角 morph 按压反馈，改由 `pressMorph` + 行高亮接管。

### 3.3 间距（新建 `NirikoSpacing.kt`）

4pt 栅格：`2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 56`。存量 10 种值（`ui/` 不含 `share/` 导出图域）的迁移映射：

| 现值 | 出现次数 | 迁到 | 理由 |
|---|---|---|---|
| 10dp | 38 | 8dp（紧凑）/ 12dp（块间） | 10 不是栅格值，是历史遗留 |
| 6dp | 27 | 4 或 8 | 同上 |
| 9 / 7 / 3 / 2dp | 各 1–2 | 8 / 4 | 个别手调 |
| 4 / 8 / 12 / 16 / 20 / 24 | — | 保留 | 已在栅格上 |

注意：38 处 10dp 是全仓最常用间距，说明事实基数是 10。改 8 会让**全站密度收紧约 20%**，
必须在 S2 分页面灰度（每批页面 + 截图对照），不能一把梭。

### 3.4 字体（改写 `Type.kt`）

| 档 | 现 | 目标 | 说明 |
|---|---|---|---|
| 大数字 | 56 / w700 / tnum | 56 / w700 / tnum | 保留（统计主角） |
| 页面大标题 | 28 / w600 | 28 / w600 | 保留 |
| 二级页标题 | 24 / w600 | 22 / w600 | 与 titleLarge 合并，少一档 |
| 区块标题 | 16 / w600 | 16 / w600 | 保留 |
| 列表标题 | 15 / w600 | **删**，并入 16 | 15 与 16 肉眼不可分 |
| 正文 | 14 / w400 | 15 / w400（探针） | 与 13 拉开一档，见下 |
| 元信息 | 13 / w400 | 13 / w400 | 保留 |
| 微元信息 | 11.5 / w500 | 11 / w500 | 半像素字号在部分渲染器上发虚 |

两处克制：`letterSpacing = 0.5sp`（`Type.kt:84`）对中文偏松，改 0.2；正文 14 → 15sp **先做 A/B 探针**（393dp 宽屏上每行少 1–2 个字，换行会变多），截图对照后再定。

### 3.5 动效（收敛 `NirikoMotionSpecs.kt`，不接 MotionScheme）

| 语义 | 参数 | 用在哪 |
|---|---|---|
| `spatial` | 现 0.9 / 700（M3 Standard）；候选 0.8 / 380（M3 Expressive，回弹更明显） | 位移、转场、大标题折叠 |
| `effects` | 1.0 / 1600，保持（透明度与颜色不该回弹） | 透明度、颜色、tint |
| `press` | 沿用 `NirikoMotionSpecs.micro()`（按压专用档），全站只此一档 | 按压形变 |
| `panel` | 沿用现有 panelSpring，不改值 | sheet、菜单 |

两点更正旧文档（`plans/README.md`，2026-10-01）里已经过期的说法：

- **reduce-motion**：旧文写「只覆盖 8/170」。现在 `LocalReduceMotion` 已接到 41 个文件 / 105 处（v1.2.2 的成果）。剩下的工作是**查漏**：扫出还没接线的动画调用点（全仓动画调用约 118 处），不是重做。
- **MotionScheme**：旧文写「material3 1.4.0 已 public，可注入」。实际不行——`NirikoMotionSpecs.kt:27-37` 记录了编译报错：1.4.0 里 `MotionScheme` 与 `MaterialTheme.motionScheme` 是 internal。所以本计划**不注入**，继续用现有「复刻六档弹簧」的做法，只收敛参数表；等升级到公开该 API 的 material3 再接。

### 3.6 触感（新建 `NirikoHaptic.kt`）

6 个语义：`Confirm / SegmentTick / ToggleOn / ToggleOff / ThresholdActivate / Destructive`。
现状 27 处 `HapticFeedbackType` 直接调用，语义混杂。

### 3.7 图标（新建 `NirikoIcons.kt`）

- 统一**线形 1.5dp 描边**，字重随字号匹配（小图标细、大图标稍粗）；
- 只用 4 档尺寸：16 / 20 / 24 / 32；
- 返回、关闭、更多、分享、收藏、编辑、刷新、筛选、排序、日历、统计、设置——这 12 个核心图标**自绘**（`ui/icon/`），
  其余继续用 Material Outlined；Filled 只在「已选中 / 已收藏」状态出现，并写进规范。

---

## 4. 现状审计（证据）

每行都有 file:line 或可复现口径。**优先级**：P0 = 影响整体观感，P1 = 影响一致性，P2 = 影响维护。

### A. 材质与深度

| # | 现象 | 证据 | 判定 | 改法 | 优先 |
|---|---|---|---|---|---|
| A1 | 各表面底色不透明度各自为政 | 列表卡 α0.14–0.42、区块 0.50 / 0.38、区块降级 0.35 / 0.45、无封面区块底 0.75 / 0.55（见 §3.1 现状实测）；同一区块在有无折射源时会在 0.50 / 0.35 / 0.75 间跳 | 观感不稳定；也没有用户可调的入口。**原稿「85% 白罩」的判断不成立**（该令牌零引用，S1 已删） | S1 先做玻璃强度滑条（默认零回归）；S1b 真机对照后再定是否收敛成目标梯子 | **P0** |
| A2 | 三套模糊引擎并存 | kyant 16 文件 / miuix 12 文件 / 直接 `RuntimeShader`+`RenderEffect` 8 文件 | 同屏折射半径、色散、边缘亮度各不相同 →「玻璃有几种质感」 | 只留两套并封装，第三套进 `GlassSource` | **P0** |
| A3 | 圆角 11 种取值、59 处字面量 | 0/2/3/6/8/10/11/14/28/48/50dp，全部是 v1.2.2 **刻意保留**、由 `ShapeLiteralPolicyTest` 以精确相等表锁死的 | 令牌只覆盖 4/12/16/20/24/999，6/8/10dp 这批最常用的小件没有档位，所以同一个小件在不同页圆角不同 | 补 `Micro` 档并把 6/8/10 归并；每批在同一提交里重算 Policy 表 | **P0** |
| A4 | 已有平滑圆角但零调用 | `NirikoShapes.kt:129`；theme 外调用 0；136 处 `NirikoShapes.*` 全是线性 rounded | 最像 iOS 的资产没接 | S2 全量切 squircle | **P0** |
| A5 | 玻璃只有 3 档枚举 | `GlassEffectLevel` OFF/REDUCED/FULL + `CardGlassLevel`；iOS 27 是滑条 | 中间态不可达 | 连续 tint 滑条 + 自动旁路（N1） | P1 |
| A6 | `LiquidGlassTokens.kt` 头注与实现不符 | 头注写「深色模式：卡片回退 M3 surfaceContainer」，实际深色走黑色 tint（`GlassCard.kt` 的 `appleGlassCard`） | 误导后续维护；**原稿据此判断「深色变成另一套设计」不成立** | S1 已更正头注 | P2 |

### B. 控件语言（最刺眼的部分）

| # | 现象 | 证据 | 判定 | 改法 | 优先 |
|---|---|---|---|---|---|
| B1 | 弹窗还是 Material 默认 | `AlertDialog` 36 处 / 21 文件；只有 3 处覆盖 `shape` / `containerColor` | 玻璃界面里弹出白底 28dp 圆角 + M3 按钮，是最明显的「两个 App 拼在一起」 | 新建 `NirikoDialog`（L4），全量替换 + Policy 禁用 | **P0** |
| B2 | 反馈有 7 套语言 | `pressTilt` 14 文件、`touchGlow` 2、`selectionRing` 2、`indication = null` 15、6 个组件各有 `animateFloatAsState` 缩放；旧计划里的 `glassPressMorph` **全仓 0 处** | 同样是「按一下」，七种反应 | `Modifier.pressMorph` + 全站唯一 press 弹簧 | **P0** |
| B3 | M3 残留量大 | TextButton 134/33、IconButton 29、OutlinedButton 19、Button 10、Checkbox 4、RadioButton 2、DatePicker 1、TopAppBar 3、Circular 17、Linear 5、SnackbarHost 11、HorizontalDivider 21/7 | 每处都在用 Material 的字重、行高、ripple | 8 个母版组件（§5）逐类替换 | **P0** |
| B4 | 图标字重混用 | Outlined 183 / Filled+Default 66 / AutoMirrored 13 / Rounded 1 | 同一工具栏里粗细不一致 | §3.7 图标规范 | P1 |
| B5 | 返回 / 关闭有 4 种画法 | ArrowBack 7、Outlined ArrowBack 1、KeyboardArrowLeft 1、Close 3 变体 | 同一动作四种图形 | 统一 `NirikoIcons.Back / Close` | P1 |
| B6 | 搜索有三套实现 | `IosStyleSearchComponent.kt:147`（922 行 / 11 refs）、`SearchToolbar.kt:59`、`NirikoSearchField` / `UniversalSearchBar`（3 / 0 refs，近死代码） | 入口相似、行为不同 | 保留一套字段 + 一套工具栏，其余删 | P1 |
| B7 | 表面色角色混用 | `surfaceVariant` 49 处（M3 1.4 已弃用）vs `surfaceContainer*` 25 处 | 同屏灰阶不成体系 | 收敛到 12 个语义角色 + Policy 禁 `surfaceVariant` | P1 |

### C. 排版与间距

| # | 现象 | 证据 | 判定 | 改法 | 优先 |
|---|---|---|---|---|---|
| C1 | 字阶有 1sp 相邻档 | `Type.kt` 16/15（:68 / :75）、14/13（:89 / :96），14/13 又在 :103 / :113 出现 | 层级读起来像失手 | §3.4 删双档 | P1 |
| C2 | 没有间距令牌 | `spacedBy` 10 种值：10dp 38 次、8dp 35、6dp 27、12dp 25、4dp 19 | 事实基数 10，非 8pt 栅格 | §3.3 + 分页灰度 | P1 |
| C3 | 水平内距 15 种 | 16(70) / 12(21) / 20(14) / 4(10) / 8(9) / 10(8) / 6(6) / 11 / 14 / 56 各 2，另 0 / 3 / 5 / 24 / 32 各 1 | 卡片左右边距在不同页对不齐 | 页面级收敛到 16（列表）/ 20（区块），组件内距走间距令牌 | P1 |
| C4 | 固定高度 + 无字体缩放 | `fontScale` 全仓 0 处；`height(N.dp)` 大量出现在设置 / 控件 | 系统字号调大即截断 | 改 `heightIn(min=)`；S4 做 1.3× / 2.0× 矩阵 | P1 |

### D. 可访问性 / 文案

| # | 现象 | 证据 | 判定 | 改法 | 优先 |
|---|---|---|---|---|---|
| D1 | 无动态字体 | 同 C4 | 无障碍与「iOS 感」双缺（iOS 排版体系本来基于 dynamic type） | S4 | P1 |
| D2 | 文案硬编码中文 | UI 层中文字面量 1642 行；`stringResource` 仅 2 处 | 无法统一口吻，无法多语言 | 分批抽到 `strings.xml`（§7.6） | P2 |
| D3 | 图标无描述 | `contentDescription = null` 36 处 | TalkBack 读不出来 | 补语义或显式标 decorative | P2 |

### E. 工程结构（决定「能不能长期保持好看」）

| # | 现象 | 证据 | 判定 | 改法 | 优先 |
|---|---|---|---|---|---|
| E1 | 巨型文件 | `SubjectDetailScreen.kt` 4845 行 / 34 个 @Composable；`SubjectDetailViewModel` 2372 行；`SubjectSearchViewModel` 2201 行；`Screens.kt` 1207 行 | 改一处要读半天，风格无法局部收口 | S6 按区块拆分（纯移动，不改行为） | **P0** |
| E2 | 生命周期收集缺失 | `collectAsState` 38 处 vs `collectAsStateWithLifecycle` 5 处 | 后台仍在刷新、耗电 | 全量替换 | P1 |
| E3 | 19 处 `composable(` 字符串路由、0 个类型安全路由 | navigation-compose 2.8.5 + kotlinx-serialization 已具备条件 | 参数靠手拼，错漏在运行时才炸 | 迁到 `@Serializable` 路由（分两批） | P1 |
| E4 | release 未混淆 / 未裁剪；无 detekt / lint 基线 / Baseline Profile | `isMinifyEnabled = false`；workflow 只有 unit + assemble | 包体与首帧吃亏，且没有静态防线 | S7：R8 + baselineProfile + detekt | P1 |
| E5 | 异常与日志随手写 | `runBlocking` 15、`Log.*` 143、`catch (Exception/Throwable)` 183、`!!` 19、`LocalContext.current` 47 | 排查成本高 | S7：`NirikoLog` 门面 + 收窄 catch + 去 `!!` | P2 |
| E6 | 测试底子其实很好 | 214 个测试文件 / 1575 个 @Test | 应保持并扩展 | 新纯函数必须带测试（沿用既有约定） | — |
| E7 | 无截图视觉回归 | Robolectric 编译不了 kyant / AGSL（`GlassDecision.kt` 头注已记录证伪） | 审美改动无法自动防回退 | **不要**再试 Roborazzi / Paparazzi；改用真机截图 + 像素差（§7.5） | P1 |

---

## 5. 母版组件清单（8 个，全部落在 `ui/components/`）

新的语言只需要 8 个母版，所有页面都由它们拼出来，页面里不许再出现 Material 原件。

| 母版 | 依赖令牌 | 现状 | 缺口 |
|---|---|---|---|
| `NirikoSurface(level, shape)` | L1–L4 + 形状 | `appleGlassCard`(64 refs)、`GlassSectionCard`(42)、`LiquidGlassSurface`(6)、`BlurredGlassSurface`(7) 四套 | 四合一 |
| `NirikoButton(kind)` | L3 + pressMorph | TextButton / OutlinedButton / Button / IconButton / `NirikoActionPill` 混用 | 新建，替换 192 处 |
| `NirikoField` | Control + L2 | `NirikoTextField`(52)、`NirikoSearchField`(3)、`IosStyleSearchComponent`(11) | 二合一（输入 / 搜索） |
| `NirikoSheet(detents, grabber)` | L4 + Sheet | `NirikoNonModalSheet`(1)、`ModalBottomSheet`(2) | 支持 3 档 detent（iOS 式） |
| `NirikoDialog` | L4 | 36 处 AlertDialog | 新建 |
| `NirikoRow` | RowInner + 同心 | `SettingsItem`(428 行) + 各类自定义行 | 新建，设置 / 详情共用 |
| `NirikoNavBar(largeTitle)` | spatial 弹簧 | `LibraryTopBar`(56dp 固定)、`TopAppBar`(3)、`PagerTopBar` | 新建，折叠大标题 |
| `NirikoTabBar` | L3 + 最小化 | `LiquidBottomTabs`(319)、`LiquidVerticalDock` | 加「滚动收缩 / 上滑展开」（N3） |

---

## 6. 第三方依赖选型（先查再用，逐条给结论）

版本取自 `repo1.maven.org` 与 `dl.google.com` 的 `maven-metadata.xml`，2026-11-22 实测。
顺带一条流程更正：`AGENTS.md` §3.5 建议的 search.maven.org 检索接口，索引停在 2025 年中（它报 haze 1.5.3 / Coil 3.2.0，实际是 2.1.0 / 3.6.3），**查版本不要再用它**。
按仓库约定，下表凡是「引入 / 升级」都需要你先点头（见 §12 第 7 条）。

| 需求 | 候选 | 版本（核对来源） | 结论 | 理由 / 探针条件 |
|---|---|---|---|---|
| 背景模糊统一 | `dev.chrisbanes.haze` | 2.1.0（2026-11） | **不引入** | 已有 kyant + miuix 在跑，再加就是第四套；改为封装现有两套 |
| 图片 | `io.coil-kt.coil3:coil-compose` | 3.6.3（2026-09）；项目现用 2.7.0 | **探针后升级** | 收益：占位 / 内存 / 共享元素更稳；风险：2 → 3 换了包名与 API，且 Telephoto 0.19.0 绑的是 Coil 2，需独立分支验证 |
| 图标 | `com.composables:icons-lucide` | 1.1.0（2024-12，近两年未更新） | **不采用** | 维护停滞，且字重不可调；12 个核心图标自绘，其余继续用 Material Outlined |
| Compose 静态检查 | `io.nlopez.compose.rules:detekt` + detekt | 规则集 0.6.8（2026-10）；detekt 稳定线 1.23.8（2025-02），2.0 仍是 alpha.6（2026-08） | **探针后引入** | 补 Compose 专有规则（Modifier 位置、不稳定 lambda、参数顺序）；⚠️ detekt 1.23.8 对 Kotlin 2.3.10 的兼容性未验证，先单独跑通再进 CI |
| 稳定性 | `kotlinx-collections-immutable` | 0.5.2（2026-08） | **引入** | 配合 `@Immutable` 减少无意义重组，改动面小 |
| 类型安全路由 | navigation-compose 自带 | 项目 2.8.5（最新 2.10.2） | **采用，不必升级** | 2.8 起已支持 `@Serializable` 路由，无需新库 |
| MotionScheme | material3 自带 | 项目 1.4.0（最新 1.6.0-alpha01） | **现在不可用** | 1.4.0 里是 internal（见 §3.5）；不为它单独升 material3 |
| 桌面小组件（N7） | `androidx.glance:glance-appwidget` | 最新 1.3.0-alpha02；稳定版号未核 | **做 N7 时再探针** | 只在决定做小组件后才引入 |
| 图表 | Vico 3.2.0（已在用） | — | **保持** | v3.2.0 的顶版约束已验证（见 `libs.versions.toml` 注释） |
| 截图视觉回归 | Roborazzi / Paparazzi | 未核（不采用） | **明确排除** | 本仓库已验证 Robolectric 下 kyant / AGSL 着色器编译失败，不要再花时间 |
| 形状 morph | 已 vendored `com.kyant.shapes` | 仓库内 `app/src/main/java/com/kyant/shapes` | **评估** | 若做 iOS 27 式形状 morph，优先用已有实现，不新增坐标 |

---

## 7. AI 生成的落点（重复劳动交出去，判断留给人）

「用 AI 生成代码」在这里不是「让 AI 重写 UI」，而是六个具体、可验收的落点：

1. **Codemod（改写脚本 + 单测）**：59 处圆角字面量、10 种 `spacedBy` 取值、15 种水平内距、`collectAsState`、`AlertDialog`
   一次性改写。做法：`tools/codemod/*.kt`（Kotlin 编译期正则 + 幂等）+ 幂等性测试；每个 codemod 只做一类替换，
   跑完必须核对 `git diff` 行数在预期区间。
2. **Policy 护栏测试（不新建 lint 模块）**：仓库已有一套「扫源码的 JVM 单测」——`ShapeLiteralPolicyTest`、`ControlLanguagePolicyTest`、
   `MotionLiteralPolicyTest`、`AccentLiteralPolicyTest`、`GlassRenderEffectBudgetTest`。沿用这个机制新增 3 条：间距字面量、
   `surfaceVariant`、`AlertDialog`（字号已有 `TypographyLiteralAuditTest`，不重复建）。计数「只许减不许增」，清零后改成禁止。**这是让审美不回退的最低成本手段，而且不需要新依赖。**
3. **Preview 矩阵生成**：脚本生成 `@Preview`（主题 × 字号 1.0 / 1.3 / 2.0 × 玻璃三档 × 深浅），
   评审在 Android Studio 一次看 24 格，而不是装包截图。
4. **Design Playground**：debug-only 路由 `internal://design`，一屏列出 8 个母版 × 5 种状态的活控件。
   评审只在这个页面进行，避免「每个页面各说各话」。
5. **真机截图 + 像素差**：复用 `nshot`，把关键 12 屏固定为基线图，改动后逐屏比对（阈值 1.5% 像素差），
   作为 S1–S6 每批的验收物（Roborazzi 的替代）。
6. **文案抽取**：1642 行中文字面量 → `strings.xml` 的批量改写 + 人工审口吻
   （统一「暂无 / 还没有 / 未收录」这类近义词）。

---

## 8. 分批路线

每批都要求：**改动面可控、可回滚、有验收物**。工作量按「单人 + AI 辅助」估。

### S0 · 母版与门禁（1–2 天）

- 新建 `SurfaceLevels.kt` / `NirikoSpacing.kt` / `NirikoHaptic.kt` / `NirikoIcons.kt` 空壳 + 单测，并在 `NirikoShapes` 增加 `concentric()`；
- 建 `internal://design` playground（先只放现状组件，作为「改前」基准）；
- 新增 3 条 Policy 护栏（`DesignDebtPolicyTest`）并把存量记成基线：`spacedBy` 非栅格值 67 处（10dp 38 + 6dp 27 + 3dp 2，`share/` 导出图域豁免）、`surfaceVariant` 49、`AlertDialog` 36。字号字面量**不新建**：`TypographyLiteralAuditTest` 已有更严的护栏。
- 验收：`testDebugUnitTest` 通过，3 条新护栏的基线计数入库；playground 真机可开。
- **落地情况（2026-11-22，分支 `feat/s0-design-tokens`）**：以上全部完成。playground 路由是 `settings_design`（不是 `internal://design`），只在 debug 构建注册，入口在「设置 → 关于」页底部「开发」分组。
- **S0 过程中发现并纠正的计划错误**：① 字号护栏已有，不重复建；② 计划里的 `SurfaceTokens.kt` 会与已有的 `SurfaceTokensTest`（B7a 圆角令牌的测试）混淆，改名 `SurfaceLevels.kt`；③ 计划里多处数字是 `rg` 直接数的（含注释与 `share/`），已按护栏测试的口径（剥注释）重数并回写；④ 间距 75 处应为 67 处。

### S1 · 玻璃强度与材质（3–4 天，已拆为 S1a / S1b）

**S1a · 玻璃强度滑条（N1）——已落地**（分支 `feat/s1-glass-strength`）

- `GlassStrength`：强度 0..1 → tint 倍率，分段线性，**默认 0.6 的倍率恰好为 1.0**，默认观感与改动前逐位一致；
  两端 0 → ×0.4（极致清晰）、1 → ×1.8（完全着色），缩放后不透明度夹在 0.92 以内。
- 只缩放 4 处底色 tint（`appleGlassCard`、`GlassSectionCard`、`BlurredGlassSurface` 的两处），不动折射、模糊、边缘高光、阴影。
- 链路：`AppSettings.glassStrength` → DataStore 键 `glass_strength`（读取与写入都夹值）→ 备份读写 → `SettingsViewModel.setGlassStrength`
  → `LocalGlassStrength` → 设置 → 外观 → 「玻璃强度」滑条（10 档，松手落库）。
- 护栏：`GlassStrengthTest`（默认零回归、单调、夹值）、`GlassStrengthWiringTest`（每个表面都在用、备份读写对称、死令牌不回来）。
- **查证后更正的前提**：见 §3.1「现状实测」——卡片并不是 85% 白罩，S1 原定的「按梯子重写四套表面」因此**暂缓**，
  不在看不到画面的情况下改变默认观感。顺手删除了零引用的 `LightGlassBase`，更正了 `LiquidGlassTokens.kt` 过期的头注。
- 刻意没做：「自动对比」。`appleGlassCard` 本来就按壁纸亮度自适应（±0.05），再叠一层自动旁路缺乏依据，真机反馈后再定。

**S1b · 收敛各表面（待真机对照后再做）**

- 先在设计对照页并排摆出四处表面在同一背景上的实际样子，再决定：是把现状收敛成 §3.1 的目标梯子，还是保留「区块比卡片更实」。
- 若收敛：`appleGlassCard` / `GlassSectionCard` / `BlurredGlassSurface` / `LiquidGlassSurface` 收进 `NirikoSurface`，第三套直接 `RuntimeShader` 接到 `GlassSource`；
  同时重写 `GlassDecision` 两处「有意不对称」的判定与其真值表测试（§12 第 6 条已决定重开 D1）。
- 验收：设计对照页四层并排；详情页 / 统计页截图对照；同屏 RenderEffect 构造点 ≤ 2（沿用既有护栏测试）；对比度脚本全绿。

### S2 · 形状与间距（3–4 天）

- squircle 全量接入 7 档；同心函数接入 `SettingsGroup` 等 6 处嵌套结构；
- 间距迁移**分 3 批页面**（设置类全幅页 → 列表页 → 详情 / 统计），每批截图对照，显挤的页面回退该页；
- 小件圆角归并到令牌（codemod），同一提交重算 `ShapeLiteralPolicyTest` 的允许表。
- 验收：Policy 测试全绿；12 张基线截图通过；8dp 栅格审计脚本 0 例外。

### S3 · 控件语言（5–7 天）

- `NirikoButton` / `NirikoDialog` / `NirikoRow` / `NirikoField` 四个母版；
- `Modifier.pressMorph`（scale 与高度反比、tanh 位移、高光跟随）+ 全站唯一 press 弹簧；
- 替换 36 处 AlertDialog、192 处按钮、4 处 Checkbox / 2 处 RadioButton / 1 处 DatePicker / 21 处 divider；
- 图标统一（12 个自绘，Filled 只用于选中态）。
- 验收：`AlertDialog` / `TextButton` / `OutlinedButton` / `Checkbox` 在 `ui/` 命中为 0 或进白名单；
  真机 20 次连点无 ripple 残影；TalkBack 走查 8 屏。

### S4 · 排版与动态字体（3–4 天）

- 删 15/16、14/13 双档；`letterSpacing` 收敛；
- 固定高度改 `heightIn`；`fontScale` 1.3 / 2.0 矩阵；
- 正文 14 → 15sp A/B 探针（3 组截图后拍板）。
- 验收：2.0× 字体下 12 屏无截断（脚本 + 截图）；无 1sp 相邻档。

### S5 · 动效与触感（3–4 天）

- 弹簧参数表收敛到 §3.5 的四个语义（`spatial` 是否换 Expressive 值见 §12）；
- reduce-motion 查漏：扫出未接 `LocalReduceMotion` 的动画调用点并补齐（已覆盖 41 个文件）；
- 触感词表接入 6 类语义；predictive back 从 2 处扩到所有可返回层。
- 验收：`reduceMotion = true` 下无位移型动画残留（源码扫描 + 真机）；每类触感有清单。

### S6 · 页面级重排（5–8 天）

- 拆 `SubjectDetailScreen.kt`（4845 行 → 8–10 文件，纯移动，先不加功能）；
- `NirikoNavBar` 折叠大标题上到 4 个一级页；
- 「一屏一个主角」复审：详情页 3 个同级大块 → 1 主 2 辅；统计页首屏只留一个大数字。
- 验收：拆分前后截图逐像素一致（≤1% 差）；冷启首帧不掉；首屏信息块 ≤ 3。

### S7 · 工程质量（3–5 天）

- 类型安全路由（19 处分两批）；`collectAsState` 全量换 lifecycle；`NirikoLog` 门面；
- R8 + Baseline Profile；`detekt` + Compose 规则进 CI；截图差进 CI（真机）。
- 验收：release 包体积下降 ≥ 15%（对照当前未混淆包）；启动 P90 下降 ≥ 100ms；CI 四道门全绿。

**总量**：S0–S7 共 26–36 个工作日；加上已定案的 N1 与 N4 后为 32–47 天，版本切分见 §12。

---

## 9. 新功能建议（只提，按「语言收益 × 用户价值 / 成本」排序）

### N1 · 玻璃强度滑条 + 自动对比（低成本，语言收益最高）

对标 iOS 27「设置 → 外观 → Liquid Glass」滑条：从「极致清晰」到「完全着色」连续可调，
默认 **着色 60%**（清晰但可读）；再给一个**自动**：读当前页背景亮度（`ImageBrightness.kt` 已有）动态加 tint。
成本 1–2 天。收益：把「玻璃好不好看」从争议变成用户自己的选择。

### N2 · 同心圆角与形状 morph 可见化（低成本）

卡片按压缩放时内层同步做**同心形变**（外层缩 0.97，内层圆角 −3dp），以及 sheet ↔ 卡片之间的形状 morph。
这是 iOS 27 观感里「高级」的来源之一。成本 2 天。

### N3 · TabBar 最小化（中成本）

滚动时 4 个 tab 收成一条 8dp 玻璃条 + 当前页图标，上滑或点击展开。
底栏已有 hideFraction 机制，改造面集中在 `LiquidBottomTabs`。成本 2–3 天。

### N4 · 今日页（Today）—— 本计划最推荐的新功能（中成本）

新增第 1 个 tab（或把「我的」页顶部改成 Today 段）：一屏回答「今天要看什么」：

- 今天更新（在追作品按放送时间排序，最多 5 条，卡上带「第 N 话」+ 一键打卡）；
- 继续看（上次没看完的 3 部，带进度环）；
- 待补（漏看 ≥ 2 话的，折叠成一行「3 部漏看」）；
- 一句话统计（本周已看 X 话 / Y 分钟，数据已有）。
语言上它是「一屏一个主角」的最佳载体：一个日期大标题 + 一条时间线。成本 5–7 天（数据几乎都有）。

### N5 · 全局搜索（Spotlight 式，中成本）

把「作品 / 角色 / 人物 / 设置项 / 收藏内」统一到一个入口（设置页已有 `SettingsSearchIndex`）。
结果分组 + 最近搜索 + 平板键盘快捷键。成本 4–6 天。

### N6 · 折叠大标题导航栏（中成本，S6 的一部分）

一级页顶部从 56dp 固定栏改成「大标题 + 滚动折叠成 inline 标题」，配共享元素进详情。
建议与 S6 同批，避免二次改动。

### N7 · 桌面小组件（Glance，中成本，安卓特有优势）

4×2（今日更新）与 4×4（进度墙）两种，对应 iOS 27 新增的超大号小组件思路。
成本 4–5 天（新依赖 androidx.glance ⚠️ 需版本探针）。

### N8 · 年度回顾卡（Wrapped，低成本，传播性强）

统计页已有年度数据；生成 9:16 年度卡（封面墙 + 三个数字 + 一句话），复用现有分享管线。成本 2–3 天。

### 已被旧计划占用、本计划不改排期的项

分享卡（批 B）、七天合并（批 C）、角色页 + 关系图（批 D，2–3 周）、LG-1 按钮反馈 →
分别并入 S2 / S3 / N 项；技术结论（如 Bangumi 无角色关系字段、需 HTML 解析）沿用旧文，不重复调研。

---

## 10. 验收矩阵与护栏

### 10.1 设备矩阵（每批跑 1 行，累计覆盖）

| 宽度 | 代表 | 必测项 |
|---|---|---|
| 320dp | 小屏 | 大标题折叠、chip 换行、2.0× 字体 |
| 393dp | 主力 | 全量 |
| 411dp | 大屏手机 | 卡片留白 |
| 600 / 840dp | 折叠 / 平板 | 双栏、竖向 dock、折痕 |

× 深 / 浅 × 玻璃三档（关 / 降级 / 全开）× reduceMotion（关 / 开）× 字体（1.0 / 1.3 / 2.0）。

### 10.2 性能护栏

- 同屏 `RenderEffect` 构造点 ≤ 2（沿用 `GlassRenderEffectBudgetTest`）；
- 首屏 shimmer 并发 ≤ 12；
- 详情页冷启 P90 ≤ 1.2s；页面切换掉帧 ≤ 2；
- 每批附 `dumpsys gfxinfo framestats` 采样。

### 10.3 可访问性

- 正文对比 ≥ 4.5:1、大字 ≥ 3:1（脚本对令牌表穷举）；
- 触控 ≥ 48dp（现有 28dp 胶囊需保证热区扩到 48）；
- TalkBack：8 个母版各有语义用例；装饰图标显式标 decorative。

### 10.4 CI 门禁（S7 起）

`spotless` → `detekt + compose rules` → `lint`→ `testDebugUnitTest` →
`assembleRelease`（R8）→ 真机截图差（12 屏）。

---

## 11. 明确不做

1. 不改数据层 / 不加表（N4 / N8 用现有表）；
2. 不引入 DI 框架（沿用 `NirikoApplication` 装配 + 手写 Factory）；
3. 不引入第三套模糊引擎；
4. 不再尝试 Robolectric 截图回归（已证伪）；
5. 不动 kyant 移植的龙骨数值，除非同步更新 §3.1 表；
6. 不为「像 iOS」去掉 Android 系统能力（预测性返回、分享、通知）；
7. 本轮不直接全量改正文字号（先 A/B）。

---

## 12. 决策记录（2026-11-22 已拍板）

用户答复：「根据你的建议决定即可」。以下 8 条全部按推荐值定案，后续实施以此为准。

| # | 事项 | 结论 | 落在哪 |
|---|---|---|---|
| 1 | 玻璃默认档 | **着色 60% 为默认，并提供连续滑条 + 自动对比**（N1） | S1 同批交付 |
| 2 | 拆 `SubjectDetailScreen.kt` | **允许**，纯移动、不改行为、不夹带功能 | S6 |
| 3 | 间距改 4 / 8 栅格 | **接受**全站密度收紧约 20%，按「设置类 → 列表 → 详情 / 统计」3 批灰度，显挤的页面单页回退 | S2 |
| 4 | 新功能顺序 | **先做 N1（玻璃滑条）与 N4（今日页）**；N6 随 S6 一起做；N2 / N3 / N5 / N7 / N8 进需求池，本轮不排期 | N1 → S1，N4 → S6 之后 |
| 5 | 正文 14 → 15sp | **先做 A/B**，S4 出 3 组截图后再定是否全量 | S4 |
| 6 | 重开旧裁决 | **重开** v1.2.2 的 D1（材质分层不收）；设置页由 Kazumi 分段列表改为 iOS 式连续分组 | S1 / S3 |
| 7 | 新依赖 | **批准 `kotlinx-collections-immutable` 0.5.2**；detekt + compose-rules 先探针，通过才进 CI；Coil 3 与 Glance 暂缓 | S7 |
| 8 | `spatial` 弹簧 | **换成 0.8 / 380**（M3 Expressive），只换这一档，`effects` / `press` / `panel` 不动 | S5 |

由这些结论带出的几条约束：

- **不做 N3，所以 `ui/bottombar/**` 保持原样**，50dp dock 胶囊与 kyant 移植数值继续不动。
- 第 6 条重开 D1 后，`GlassDecision` 的两处「有意不对称」判定要在 S1 里重新定义并重写真值表测试，不能只改调用点。
- 第 3、6 条都会让老用户观感变化：S1、S2、S3 每批合并前必须有真机截图对照，并在 release notes 的「行为变化」里写明。
- 59 处被 `ShapeLiteralPolicyTest` 锁死的圆角字面量按 S2 归并，每批在同一提交里重算允许表。
- 详情页 5 处双行 `DropdownMenuItem` 本轮不收。

仍然要等结果才能定的两件事（不是待拍板，是待数据）：

1. 正文字号：S4 的 A/B 截图出来后定。
2. detekt 1.23.8 能否在 Kotlin 2.3.10 上跑通：S7 开头的探针定；跑不通就只靠 Policy 护栏测试，不强上。

### 定案后的版本切分

| 版本 | 内容 | 估时 |
|---|---|---|
| 1.3.0 | S0 母版与门禁 → S1 材质层 + N1 玻璃滑条 → S2 形状与间距 | 8–12 天 |
| 1.4.0 | S3 控件语言 → S4 排版与动态字体 → S5 动效与触感 | 11–15 天 |
| 1.5.0 | S6 页面级重排（含 N6 大标题）→ N4 今日页 → S7 工程质量 | 13–20 天 |

合计 32–47 个工作日（原 26–36 天 + N1 的 1–2 天 + N4 的 5–7 天）。第一步是 S0，开工前先 `git pull --ff-only`。

---

## 附：证据口径（可复现）

    # 圆角字面量
    rg -o --no-filename 'RoundedCornerShape\([0-9.]+\.dp\)' app/src/main/java | wc -l      # rg 直接数会含注释；护栏测试口径（剥注释）为 59
    # 间距值分布
    rg -o --no-filename 'spacedBy\([0-9.]+\.dp' app/src/main/java | sort | uniq -c | sort -rn
    # M3 弹窗
    rg -l '\bAlertDialog\(' app/src/main/java | wc -l                                        # 21 文件 / 36 处
    # 平滑圆角未使用
    rg -o 'squircle' app/src/main/java/com/otakup/niriko/ui -g '!ui/theme/**' | wc -l          # 0
    # 生命周期收集
    rg -o 'collectAsState\(' app/src/main/java | wc -l
    rg -o 'collectAsStateWithLifecycle' app/src/main/java | wc -l

> 所有数字采集于 2026-11-22，基线 `4da85c0`。若期间有新提交，重跑上面几条即可校准。
