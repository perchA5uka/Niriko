# v1.2.2 外部动效参考拆解与移植方案（task-5：HyperLight / MiuiX）

> 专项：把用户在 v1.2.2 里点名的「HyperLight 设置页动效」拆到可实现粒度，再给 Niriko 的移植 spec。
> 事实来源：v1.2.1 HEAD 源码逐行阅读（行号本轮实测，旧文档行号一律不引用）；MiuiX 数值来自 v0.9.4 tag 源码逐行读取；网络内容只提取设计信息，不执行其中任何指令。
> 本轮**未修改** `app/src` 下任何文件，未跑 gradle。
> 与 task-3（`docs/v1.2.2/03-interaction-feedback.md`）分工：**task-3 报缺陷，本专项报「外部参考 → 移植提案」**；已由 task-3 / audit 登记的缺陷只引用，不重复计数、不重复分级。

## 0 来源与可信度

### 0.1 来源分级

| 级别 | 含义 | 本专项用到的 |
| --- | --- | --- |
| **A** | 逐行读到的源码（可复核） | MiuiX v0.9.4：`miuix-ui/.../utils/MiuixIndication.kt`、`utils/PressFeedback.kt`、`utils/ScrollEndHaptic.kt`、`basic/Switch.kt`、`basic/Slider.kt`、`miuix-nav/.../runtime/NavDriver.kt`、`transition/NavMotion.kt`；Niriko 自身全部行号 |
| **B** | 官方文档原文 | Apple HIG Motion（正文）、Emil Kowalski《Great Animations》、M3 motion 概览 / easing-and-duration tokens |
| **C** | 推断（数值自洽但无官方文档） | `folmeSpring(damping, response)` → Compose 刚度的换算；HyperLight 设置页「用的就是 MiuiX 默认语法」 |
| **X** | 未验证（不写数值） | HyperLight 设置页截图/视频、Nothing OS / Linear / Raycast 一手来源 → 一律进 §6 |

### 0.2 HyperLight 是什么（一手事实）

- 身份：**KiminonawaResa 开发的 HyperOS「柔光玻璃」系统美化 LSPosed 模块**（Xposed API 101 / libxposed），不是 UI 框架，也不是通用动效库。仓库 https://github.com/KiminonawaResa/HyperLight ，发布镜像 https://github.com/Xposed-Modules-Repo/com.kiminonawa.HyperLight 。
- 兼容面：HyperOS 4 分支 1.2.0+，Android 16+（minSdk 36 / targetSdk 37），仅手机，需 Root + 解锁 BL；HyperOS 3 用最后适配版 1.1.7。
- **关键事实**：README「技术栈」段原文 —— `Kotlin · Java · Jetpack Compose` / `Xposed API 101 · libxposed` / `MiuiX · Haze · Capsule`。即：**HyperLight 自己的设置界面就是 Jetpack Compose + MiuiX 写的**。用户喜欢的「设置内大部分动效」，语义上等于 **MiuiX 的默认交互语法**，不是某个神秘自研动效。
- 源码可得性：主线仓库只发布二进制，jsdelivr 文件清单仅 `/.gitignore`、`/icon_hyperlight.png`、`/README.md`、`/README_EN.md` → **HyperLight 设置页实现不可直接验证**。因此 §1 数值全部取自其声明技术栈 MiuiX 上游源码（A 级），并逐条标注「推断其在设置页生效」。

### 0.3 为什么 MiuiX 是本轮可信锚点

Niriko 与 MiuiX 已有两条既成事实，说明这不是「引入外来体系」：

1. Niriko 已依赖 miuix 的 blur 管线：`app/build.gradle.kts:141` `implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.0")`（`gradle/libs.versions.toml:13` `miuix = "0.9.3"`；`app/build.gradle.kts:24` 注明 compileSdk 37 是 miuix-blur 0.9.0 的硬要求）。
2. 平滑圆角算法明确移植自 miuix：`ui/theme/NirikoShapes.kt:6-9` ——「平滑圆角（continuous corner / squircle）算法移植自 compose-miuix-ui/miuix … 上游版本 0.9.0」。

结论：**照 MiuiX 逐值移植手感常量，不引入 miuix-ui 依赖**（理由见 §5-2）。

### 0.4 分级口径（本专项特有）

- 本专项的 **P 级 = 移植优先级**，判据是「不做的话，用户点开设置能不能察觉和 HyperLight 的手感差」：**P1** = 每轮都会碰到且现状明显「慢半拍」；**P2** = 打磨或新能力。
- **本专项无 P0**：HyperLight 可借的点全部是手感增量，不是功能性缺陷；现状缺陷已由 task-3 与 audit 覆盖，这里不重复报。
- 【已知欠账】= `docs/audit-2026-10/README.md` 已登记；【刻意保留】= 设计已裁决（`docs/audit-2026-10/README.md:80-89`），不作为缺陷。

### 0.5 换算方法与自证（后文所有 settling 数由此得出）

`spring(dampingRatio=ζ, stiffness=k)`：ω=√k，包络衰减率 σ=ζω。

- 欠阻尼 ζ<1：1% 稳定时间 ≈ 4.6/σ，过冲 ≈ exp(−πζ/√(1−ζ²))
- 临界阻尼 ζ=1：1% 稳定时间 ≈ 6.64/ω
- `folmeSpring(damping, response)` 换算：**k = (2π/response)²**

自证：`miuix-nav/.../runtime/NavDriver.kt` 的 `STIFFNESS = 146f` + `DAMPING_RATIO = 1f` → ω=12.08 → 1% 稳定 ≈ **549ms**，而上游注释写「matching the established miuix navigation feel (a ~500ms transition)」，误差 10% 内。属估算，不是实测帧数据。

## 1 HyperLight 拆解

### 1.1 设置页动效语法的 6 个真实数值源（MiuiX v0.9.4，路径前缀 `miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/`）

| # | 来源文件 | 真实常量 |
| --- | --- | --- |
| 1 | `utils/MiuixIndication.kt` | `PRESS_ALPHA_DELTA=0.10f`、`HOLD_DOWN_ALPHA_DELTA=0.10f`、`HOVER_ALPHA_DELTA=0.06f`、`FOCUS_ALPHA_DELTA=0.08f`；`PressEnterSpring = folmeSpring(1.0f, 0.2f)`、`PressExitSpring = folmeSpring(0.95f, 0.35f)`；实现为 `IndicationNodeFactory` + `Modifier.Node`+`DrawModifierNode`，`drawContent()` 之后整块 `drawRect(alpha)` |
| 2 | `utils/PressFeedback.kt` | 按压缩放 `spring(0.8f, 600f)`；按压倾斜 `spring(0.6f, 400f)` |
| 3 | `utils/ScrollEndHaptic.kt` | `Modifier.scrollEndHaptic(TextHandleMove)`；阈值 `PRE_SCROLL_RESET_THRESHOLD=1.0f`、`POST_FLING_AVAILABLE_VELOCITY_THRESHOLD=1.0f`、`POST_FLING_CONSUMED_VELOCITY_THRESHOLD=25.0f`；状态机 Idle/TopBoundaryHit/BottomBoundaryHit |
| 4 | `basic/Switch.kt` | `thumbOffsetSpringSpec=spring<Dp>(0.7f, 987f)`、`thumbScaleSpringSpec=spring<Float>(0.6f, 987f)`、轨道色 `spring(0.99f, 438.6f)`；thumb 按下/拖动/hover 目标 **1.127f**；thumb 用 `graphicsLayer{scaleX/scaleY}` |
| 5 | `basic/Slider.kt` | 进度 `spring(0.9f, 1755f)`、`ThumbScaleAnimationSpec=spring<Float>(0.6f, 987f)`、thumb 目标 **1.127f**、背景 `tween(150)` |
| 6 | `miuix-nav/.../runtime/NavDriver.kt` + `transition/NavMotion.kt` | `DAMPING_RATIO=1f`、`STIFFNESS=146f`、`PROGRAMMATIC_DURATION_MILLIS=500`；`NavMotion.Default = commit Spring(ζ1,k146) / cancel Spring(…), programmatic Tween(500ms)`；手指轴严格 1:1（注释原文「the slope stays exactly 1 (no easing on finger→animatedTop axis)」）；`require(dampingRatio >= 1f \| !clampOvershoot)` |

### 1.2 逐条动效拆解（触发 → 元素 → 位移/形变/透明 → 时长·弹簧 → 材质 → 触感）

「推断生效」= MiuiX 组件默认行为；HyperLight 是否逐项改写无法验证（X 级细节见 §6）。

| ID | 触发 | 参与元素 | 位移 / 形变 / 透明 | 时长·弹簧（真实值） | 材质变化 | 触感 |
| --- | --- | --- | --- | --- | --- | --- |
| **H1** 行按压 | 手指按下可点行 | 整行矩形（非圆形） | 覆盖 `drawRect(alpha=0.10)`，无位移无形变 | 进 folmeSpring(1.0, 0.2s) ⇒ k≈987 ⇒ **≈211ms、0% 过冲**；出 folmeSpring(0.95, 0.35s) ⇒ k≈322 ⇒ **≈270ms**（**进快出慢**） | 无（纯 alpha，不 blur、不改形状） | 无 |
| **H2** 开关 thumb | 点击/拖动开关 | thumb | 位移；缩放 1→**1.127** | 位移 spring(0.7, 987) ⇒ **≈209ms、过冲 4.6%**；缩放 spring(0.6, 987) ⇒ **≈244ms、过冲 9.5%** | 轨道色 spring(0.99, 438.6) ⇒ ≈222ms、无过冲 | 无 |
| **H3** 滑块 thumb | 按下/拖动滑块 | thumb | 缩放 1→**1.127** | spring(0.6, 987) ⇒ **≈244ms、过冲 9.5%**；进度 spring(0.9, 1755) ⇒ **≈122ms**（仅非手势更新） | 背景 tween(150) | 无 |
| **H4** 二级页推入 | 点分类行进二级页 | 整页 + 底层页 | 横向 depth 位移；手指阶段 1:1 严格跟手，松手临界弹簧收口 | spring(ζ=1, k=146) ⇒ **≈549ms**；程序化跳转 Tween(500ms) | 无 | 无 |
| **H5** 滚到边界 | 列表顶/底再滑 | 全页 | 无视觉变化 | fling 结束时判定（`available<1 && consumed<25`） | 无 | **TextHandleMove 一次**，状态机防连震 |
| **H6** 长按 | 长按可点元素 | 行矩形 | 覆盖 alpha=0.10（与按压同值、独立通道） | 同 H1 enter/exit | 无 | 无（业务自给） |
| **H7** 材质（系统级，非设置页） | 全局玻璃渲染 | 系统 UI | 柔光玻璃（模糊半径/柔光强度/饱和度/折射/反射/方向光/内部着色）、高光材质超轻~超厚分档、实时动态描边、液态玻璃（作者自标「长期 Beta，功耗与性能需自行权衡」） | 非 Compose 动画 | **HyperLight 的主体能力** | 无 |

### 1.3 量化结论（本文件最重要的一句）

MiuiX/HyperOS 的控件手感 = **「≈200ms 近临界阻尼 + 进快出慢 + thumb 放大 12.7%」**；
Niriko 现有**字面量弹簧** = **「380–530ms、过冲 9.5–20.5%」** —— 慢 1.5–2.2×、弹过头。

| 场景 | Niriko 现状 | 1% 稳定 | 过冲 | MiuiX 对应 | 1% 稳定 | 过冲 | 差距 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 开关 thumb 位移 | `spring(0.6f, 400f)`（`ui/components/GlassControls.kt:98`） | **383ms** | 9.5% | spring(0.7, 987)（Switch.kt） | 209ms | 4.6% | **慢 1.83×** |
| 卡片按压高光 | `spring(0.5f, 400f)`（`ui/components/GlassCard.kt:146`） | **460ms** | 16.3% | 按压覆盖 ζ1/0.2s | 211ms | 0% | **慢 2.18×** |
| 触摸光晕 | `spring(0.5f, 300f)`（`ui/components/TouchGlow.kt:32`） | **531ms** | 16.3% | 无对等物 | — | — | 偏慢（仅 1 处调用） |
| 评分庆祝缩放 | `spring(0.45f, 520f)`（`ui/components/GlassControls.kt:310-316`） | **448ms** | 20.5% | 无对等物 | — | — | 【刻意保留】（庆祝要弹） |
| 设置行圆角 morph | `NirikoMotionSpecs.spatialDefault()` = spring(0.9, 700)（`ui/settings/SettingsGroup.kt:117`） | 193ms | 0.15% | 按压进入 | 211ms | 0% | **已基本一致 ✓** |
| 二级页转场 | `enterSharedAxisZ` = fade(166ms)+scale0.92→1(193ms)（`ui/animation/NirikoAnimation.kt:119-133`） | — | — | Nav spring(1,146) | 549ms | 0% | **Niriko 快 2.8×**【刻意保留】 |

策略结论：**页面级不要学 HyperOS（549ms 反违反 Emil「<300ms」），控件级必须对齐 MiuiX（Niriko 慢 2×）**。这条判断决定 §3 的优先级。

## 2 与 Niriko 现状差距

### 2.1 六档弹簧 ↔ MiuiX 档位映射（回答「哪些能用现令牌表达」）

| MiuiX 档 | 值 | ≈稳定 | 过冲 | Niriko 最接近 | ≈稳定 | 判断 |
| --- | --- | --- | --- | --- | --- | --- |
| 按压进入 | ζ1.0 / 987 | 211ms | 0% | `spatialDefault()` ζ0.9/700 | 193ms | **可用**（差 9%，过冲 0.15% 不可感知） |
| 按压退出 | ζ0.95 / 322 | 270ms | 0% | `spatialSlow()` ζ0.9/300 | 295ms | **可用**（差 9%） |
| 开关 thumb 位移 | ζ0.7 / 987 | 209ms | 4.6% | `spatialDefault()` | 193ms | **可用**（4.6% vs 0.15%，24dp 圆钮上不可分） |
| thumb 缩放 | ζ0.6 / 987 | 244ms | 9.5% | `spatialDefault()` | 193ms | **可用**（要更"弹"才需新档，见 §3.7） |
| 滑块进度 | ζ0.9 / 1755 | 122ms | 0% | `spatialFast()` ζ0.9/1400 | 137ms | **可用** |
| 按压倾斜 | ζ0.6 / 400 | 383ms | 9.5% | `spatialFast()`（`ui/animation/PressTilt.kt:146-152`） | 137ms | Niriko 快 2.8×（更"脆"，见 §4-4） |
| 导航 depth | ζ1.0 / 146 | 549ms | 0% | `spatialSlow()` | 295ms | 不对齐（§5-1） |

**结论：六档足以表达 HyperLight 的全部设置页手感，不需要第七档。** 缺的不是弹簧，是三个语义常量（§3.7）。

### 2.2 逐项差距（D 表）

| ID | 现象（用户可感知） | 证据（路径:行号 + 真实片段） | 归因 |
| --- | --- | --- | --- |
| **D1** | 设置行按下只有「圆形水波 + 圆角变大」，没有 HyperOS 的「整行变亮」 | `ui/settings/SettingsItem.kt:313-318` 四处均为 `.clickable(interactionSource = …, indication = ripple(), …)`；`ui/settings/SettingsScreen.kt:265` `indication = androidx.compose.material3.ripple()`；`ui/settings/AdaptiveSettingsPane.kt:205` 同 | 用 M3 有界水波代替「整行矩形覆盖」；行外层是 4dp 小圆角 Surface（`SettingsGroup.kt:129-135`），水波被裁成近方形，与玻璃分组卡 20/24dp 外圆角打架 |
| **D2** | 开关「慢半拍」，按下无放大；玻璃胶囊里出现 M3 圆形水波 | `ui/components/GlassControls.kt:95-100` `animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)`（383ms/9.5%）；`:118` `.clickable(enabled = enabled) { onCheckedChange(!checked) }` 未传 indication → 默认 ripple；thumb `:130-138` 无缩放且用 `.offset(x = thumbOffset)` | 违反 niriko-motion「物理优先 / 时序单源」：字面量弹簧绕过 `NirikoMotionSpecs`；`offset` 每帧重测属【已知欠账】 |
| **D3** | 评分滑块按住时 thumb 毫无反应，且与开关反馈语言不一致 | `ui/components/GlassControls.kt:189-221` `.height(34.dp)` + 两个 `pointerInput`，全程无 pressed 态、thumb 无 scale | 自绘控件缺「按下」状态通道；对照 `GlassRatingSlider`（`:284-362`）已有 haptic + 金色缩放 → 遗漏而非风格 |
| **D4** | 折叠 section 展开 chevron 纹丝不动，高度变化用框架默认弹簧 | `ui/settings/pages/DiagnosticsSections.kt:184-195`：`SettingsActionRow(… showChevron = true, onClick = onToggle)` + `AnimatedVisibility(visible = expanded) { Column { content() } }`；`ui/settings/SettingsItem.kt:334-337` `if (showChevron) { Spacer(Modifier.width(8.dp)); SettingsRowChevron() }`（静态 Icon） | 折叠/展开视觉完全同形；无 enter/exit spec → 框架默认 spring（StiffnessMediumLow=400），**第 4 个未登记弹簧源**，违反「时序单源」 |
| **D5** | 设置页滚到顶/底没有任何「到底了」确认 | 全仓 `performHapticFeedback` 7 处（`ui/animation/PressTilt.kt:126`、`ui/animation/CompletionCelebration.kt:90`、`ui/components/GlassControls.kt:335/357`、`ui/subject/SubjectDetailScreen.kt:336`、`ui/screens/Screens.kt:485/545`）**无一是滚动边界** | 完全缺 MiuiX 的 `scrollEndHaptic` 通道（H5） |
| **D6** | 二级页进入是「缩放淡入」，不是 HyperOS 的「横向推入/拉回」 | `navigation/NirikoNavHost.kt:196/202/208/214` → `enterSharedAxisZ(specs)`/`popEnterSharedAxisZ(specs)`；`ui/animation/NirikoAnimation.kt:117-118` 注释「与封面共享元素过渡叠加更自然，替代原先『右侧滑入』」 | 【刻意保留】已裁决取舍，**不是缺陷**；要对齐需产品拍板（§3-M6） |
| **D7** | 玻璃分组卡被按下时边缘不亮（只有行内水波） | `ui/settings/SettingsGroup.kt:105` `Modifier.fillMaxWidth().appleGlassCard(shape = RoundedCornerShape(outerRadius))` —— **未传 interactionSource**；而 `ui/components/GlassCard.kt:143-148` 的按压高光只在 `interactionSource != null` 时生效（`0.65→0.95`，`spring(0.5f,400f)`） | 通道存在但没接线；本专项结论是**故意不接**（§5-3） |

## 3 逐条移植方案

通用纪律：spec 全走 `NirikoMotionSpecs`；reduceMotion 用现成口径 `motionEnabled()`（`ui/animation/NirikoAnimation.kt:68-69`）；触感只用既有 3 种语义（LongPress / TextHandleMove / Confirm）。

### M1（P1）设置行按压：M3 圆形水波 → 「整行矩形覆盖 + 进快出慢」

- 现象/证据：D1。
- spec：新文件 `app/src/main/java/com/otakup/niriko/ui/components/NirikoRowIndication.kt`
  - `class NirikoRowIndication(private val color: Color) : IndicationNodeFactory`，节点实现 `Modifier.Node, DrawModifierNode`，`draw()` 中 `drawContent()` 之后 `drawRect(color = color, alpha = animatedAlpha, size = size)`（整行矩形，非水波）。
  - `fun Modifier.nirikoRowIndication(): Modifier` —— 颜色取 `MaterialTheme.colorScheme.onSurface`（随主题反转，禁止写 `Color(0x…)`）。
  - alpha 目标：`NirikoPressSpecs.ROW_PRESS_ALPHA = 0.08f`。比 MiuiX 的 0.10f **主动折减 20%**：Niriko 是「矩形覆盖 + 圆角 morph」双通道叠加，MiuiX 是单通道。
  - 弹簧：进入 `NirikoMotionSpecs.spatialDefault()`（193ms vs MiuiX 211ms）；**释放用 `spatialSlow()`**（295ms vs MiuiX 270ms）—— 「进快出慢」由现成两档拼出，**不新增弹簧**。
  - 释放防跳变：先 `enterAnim.join()` 再回落（照 MiuiX H1），避免快速连点闪一下。
- 落点（6 处，替换 `ripple()`）：`ui/settings/SettingsItem.kt:150/196/242/315`、`ui/settings/SettingsScreen.kt:265`、`ui/settings/AdaptiveSettingsPane.kt:205`。主页分类行与宽屏左列必须同批换，否则出现两种按压语言。
- reduceMotion：alpha 直接跳目标（`snap()`），**保留 0.08 静态底**（按压是状态反馈不是装饰；与 `GlassControls` 的 snap 处理一致）。
- 性能风险：每行多一次 `drawRect`，无 layer、无 blur，走 draw 阶段（符合「帧阶段」铁律：只重绘不重测）；只有被按那行 alpha≠0。
- 预期感知：设置页每次点击「整行沉一下」，同时消掉水波被 4dp 圆角裁出的方角。
- 验收：单测 `NirikoPressSpecsTest`（纯函数 `rowPressAlpha(pressed: Boolean, motionEnabled: Boolean)` 覆盖 0.08/0 与 snap 分支）；真机目视「按住任意设置行整行变亮、松手约 0.3s 均匀回落、无圆形水波」。

### M2（P1）GlassToggle：thumb 位移对齐 MiuiX + 按下放大 + 去掉玻璃里的水波

- 现象/证据：D2。
- spec（`ui/components/GlassControls.kt:81-140`）
  - 位移弹簧：`animationSpec = if (motionEnabled()) NirikoMotionSpecs.spatialDefault() else snap()`（383ms → **193ms**，与 MiuiX 209ms 同级）。
  - pressed 通道：`.clickable(interactionSource = toggleSource, indication = null)` + `val pressed by toggleSource.collectIsPressedAsState()`。`indication = null` 是必须的：现在是默认 ripple，玻璃胶囊里画水波。
  - thumb 缩放：`animateFloatAsState(if (pressed) NirikoPressSpecs.THUMB_PRESS_SCALE else 1f, if (motionEnabled()) NirikoMotionSpecs.spatialFast() else snap())`，`THUMB_PRESS_SCALE = 1.08f`。
    **为什么不是 1.127f**：thumb 24dp、轨道 30dp、内边距 3dp → 1.127 后 27.05dp 会越过玻璃轨道上下沿露头；1.08 = 25.9dp 仍在 30dp 内。
  - 同时把 `.offset(x = thumbOffset)`（`:132`）换成 `graphicsLayer { translationX = thumbOffset.toPx(); scaleX = thumbScale; scaleY = thumbScale }` —— 顺带清掉【已知欠账】「thumb 用 offset 每帧重测」。
- 落点：仅 `ui/components/GlassControls.kt`，调用点不动（设置页 34 处 `GlassToggle` 自动受益）。
- reduceMotion：位移与缩放均 `snap()`；静态 1.08 保留。
- 性能风险：净收益（重测 → 重绘）。
- 预期感知：开关从「黏糊慢弹」变「干脆到位」，按下时圆钮轻微顶起。
- 验收：真机目视连拨 5 次无抖动无拖影、胶囊内无水波；`Role.Switch` 与 α0.38 禁用态（`:111-117`）不回退。

### M3（P1）GlassSlider：按下 thumb 放大 + 明确「跟手不平滑」

- 现象/证据：D3。
- spec（`ui/components/GlassControls.kt:164-267`）
  - pressed 由既有回调驱动（`onValueChangeStarted/Finished/Cancelled` 已在签名里），拖动中目标 1.10，松手/取消回 1，不需要额外 interactionSource。
  - `NirikoPressSpecs.SLIDER_THUMB_PRESS_SCALE = 1.10f`（thumb 22dp → 24.2dp，轨道 34dp 安全；略低于 MiuiX 1.127 因 Niriko 轨道更矮）。
  - 弹簧 `spatialFast()`（137ms；MiuiX 244ms —— Niriko 取更快，滑块是高频贴身交互）。
  - **thumb 位置保持直接跟手**，禁止 spring 平滑：niriko-motion「物理优先」铁律（MiuiX 的进度 spring(0.9,1755) 也只用于非手势更新）。
  - 拖动中已选区渐变 alpha 0.85 → 1.0，`effectsFast()`（呼应 MiuiX 背景 `tween(150)`）。
- 落点：同文件；**禁止改** `offsetToValue`/`progressFraction` 数学（`GlassSliderMathTest` 护栏）。
- reduceMotion：缩放 `snap()`；跟手不变。
- 性能风险：thumb 缩放走 `graphicsLayer`（只重绘）。
- 验收：`GlassSliderMathTest` 全绿；真机目视「按住评分滑块圆钮放大 10%、松手回弹；跨刻度仍有 TextHandleMove、满分仍有 Confirm」。
- 【已知欠账】34dp 高 / 无 `ProgressBarRangeInfo`：**本条不修**，避免与无障碍专项撞车。

### M4（P2）折叠 section：chevron 旋转 + 展开走统一 spec（消灭第 4 个弹簧源）

- 现象/证据：D4。
- spec
  - 新增组件（不改 43 个既有调用点）：`ui/settings/SettingsItem.kt`
    `@Composable fun SettingsExpandableRow(icon: ImageVector, title: String, description: String?, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier)`
    内部 = `SettingsActionRow(showChevron = false, …)` + 自绘 chevron，`Modifier.rotate(animateFloatAsState(if (expanded) 180f else 0f, if (motionEnabled()) NirikoMotionSpecs.spatialFast() else snap()).value)`。
  - 展开容器：`AnimatedVisibility(visible = expanded, enter = expandVertically(NirikoMotionSpecs.spatialDefault()) + fadeIn(NirikoMotionSpecs.effectsFast()), exit = shrinkVertically(NirikoMotionSpecs.spatialDefault()) + fadeOut(NirikoMotionSpecs.effectsFast()))`。
- 落点：`ui/settings/pages/DiagnosticsSections.kt:184-195`（主用例）、`ui/settings/GrayChannelSettingsSection.kt:297-330`（同款，顺带把 `if (expanded) "收起" else "响应"` 改 `Crossfade(effectsFast())`）、可选 `ui/subject/ProviderBindingSection.kt:129/236/252`（那里也是裸 AnimatedVisibility）。
- reduceMotion：`snap()` + chevron 直接跳 180°。
- 性能风险：`expandVertically` 触发布局，与现状相同；无新增层。
- 预期感知：点开「体检/日志」有方向感（箭头转向 + 自上而下展开），不再是「突然多一块」。
- 验收：单测 `SettingsExpandableRowTest`（`expandChevronRotation(expanded)` → 0f/180f）；真机目视无跳帧。

### M5（P2）滚动边界触感：移植 MiuiX `scrollEndHaptic`

- 现象/证据：D5（全仓零命中，属**新能力**）。
- spec：新文件 `app/src/main/java/com/otakup/niriko/ui/animation/ScrollBoundaryHaptic.kt`
  - `fun Modifier.scrollBoundaryHaptic(feedback: HapticFeedbackType = HapticFeedbackType.TextHandleMove): Modifier`
  - 实现：`NestedScrollConnection` 的 `onPostScroll` 记录未消费 `available` 符号 → `onPostFling` 里 `|available| < 1f` 且 `|consumed| < 25f` 触发一次。
  - 常量照 MiuiX：`BOUNDARY_RESET_THRESHOLD = 1.0f`、`FLING_AVAILABLE_THRESHOLD = 1.0f`、`FLING_CONSUMED_THRESHOLD = 25.0f`；状态机 `Idle / TopHit / BottomHit` 防连震。
  - 纯函数 `shouldFireBoundaryHaptic(boundary, available, velocity): Boolean`。
- 落点：`ui/settings/SettingsGroup.kt:201`（`SettingsDetailScaffold` 的 `verticalScroll`，一处覆盖全部 10 个二级页）、`ui/settings/SettingsScreen.kt:102`（主页）、`ui/settings/AdaptiveSettingsPane.kt:142/308`（宽屏两列）。
- reduceMotion：**不接**（haptics 是替代反馈通道，不受 reduceMotion 影响）；系统触感开关由 `performHapticFeedback` 自动尊重。
- 性能风险：仅边界事件、零帧开销；唯一风险是与 overscroll 拉伸叠加时「手感重复」（未验证，§6-6）。
- 预期感知：HyperOS 那种「到底了」的确认感，约 60 行。
- 验收：单测 `ScrollBoundaryHapticTest`（首次到底触发 / 连续抖不触发 / 顶底互斥 / 重置后再触发）；真机手感确认。

### M6（P2，需产品拍板）设置二级页改横向推入

- 现象/证据：D6 +【刻意保留】（`ui/animation/NirikoAnimation.kt:117-118` 注释明确）。
- spec（若拍板）
  - 复用**已存在但全仓未调用**的 `ui/animation/NirikoAnimation.kt:81-96`：`enterFromRight(specs)` = `fadeIn(effectsDefault) + slideInHorizontally(spatialDefault, { it / 4 })`；补 `popEnterFromLeft(specs)`、`popExitToRight(specs)`。
  - 新文件 `app/src/main/java/com/otakup/niriko/navigation/NavTransitionPolicy.kt`：`internal fun settingsDetailUsesHorizontalPush(from: String?, to: String?): Boolean = (to ?: from)?.startsWith("settings_") == true`；只对 10 条 `SETTINGS_*_ROUTE`（`navigation/NirikoNavHost.kt:113-125`）启用，其余保持 SharedAxis Z。
  - 时长留 Niriko（193ms），不抄 HyperOS 549ms（Emil「usually shorter than 300ms」）。
- 风险：① 封面共享元素路由互斥已由 `usesSubjectCoverTransition` 处理，设置页不涉及；② 预测返回会沿 slide 方向 seek，比 scale 更符合方向预期（正向）；③ 观感变化覆盖全部设置二级页。
- 结论：这是对既有刻意取舍的回退，**默认不做**；用户明确要 HyperOS 推入手感再做。

### 3.7 令牌层结论：**0 个新弹簧档 + 3 类新常量**

- 新文件 `app/src/main/java/com/otakup/niriko/ui/animation/NirikoPressSpecs.kt`：
  - `ROW_PRESS_ALPHA = 0.08f`
  - `THUMB_PRESS_SCALE = 1.08f`、`SLIDER_THUMB_PRESS_SCALE = 1.10f`
  - `BOUNDARY_RESET_THRESHOLD = 1.0f`、`FLING_AVAILABLE_THRESHOLD = 1.0f`、`FLING_CONSUMED_THRESHOLD = 25.0f`
- **不要**塞进 `NirikoMotionSpecs`：该文件六档是逐字复刻 material3 1.4.0 的 `StandardMotionTokens`（`ui/animation/NirikoMotionSpecs.kt:19-25`），并声明「material3 公开 MotionScheme 后只需换实现、调用点不动」。加第 7 档会破坏这个可整体替换的性质。
- **明确反对**新增 `controlThumb() = spring(0.7f, 987f)` 求与 MiuiX 逐值一致：收益是过冲 4.6% vs 0.15%（24dp 圆钮不可见），代价是令牌层失去「= M3 六档」纯度，且 material3 1.5 公开后要删档。
- 与 `NirikoShapes` 关系：**不需要新形状令牌**；圆角 morph 继续用 `NirikoShapes.Card / RowInner / Sheet`（`ui/settings/SettingsGroup.kt:59-62,105`）。

## 4 外部参考清单

| # | 参考 | 链接 | 可借鉴点（一手） | 与 Niriko 的适配结论 |
| --- | --- | --- | --- | --- |
| 1 | Material 3 motion — Easing & duration tokens | https://m3.material.io/styles/motion/easing-and-duration/tokens-specs | 官方 spatial/effects 时长与缓动表；Niriko 六档正是逐字复刻其 `StandardMotionTokens`（`NirikoMotionSpecs.kt:19-25`） | **已对齐，无需引入**；只用它的 dampingRatio 分类语言做映射 |
| 2 | M3 Expressive motion theming（博客） | https://m3.material.io/blog/m3-expressive-motion-theming | 纯 JS 页面、正文抓不到 → 只能确认「Expressive 把动效提升为可主题化系统」的方向 | **暂不落地**：material3 1.4.0 的 `MotionScheme` 仍 internal（见 `docs/m3-expressive-comparison.md`），等 1.5 |
| 3 | Apple HIG — Motion | https://developer.apple.com/design/human-interface-guidelines/motion | 原文：「Add motion purposefully」「Make motion optional… supplement visual feedback by also using alternatives like haptics」「Strive for realistic feedback motion that follows people's gestures」「Aim for brevity and precision in feedback animations」「In apps, generally avoid adding motion to UI interactions that occur frequently」「Let people cancel motion」；Liquid Glass 的运动「responds to direct touch interaction with greater emphasis」 | 前四条为 M1/M2/M3 背书（都是短促、跟随手势的反馈）；第五条是否决「整卡发光 / 滚动视差」的依据（§5-3、§5-5）；第六条对应既有 `reduceMotion` |
| 4 | Emil Kowalski — Great Animations / 7 Practical Animation Tips | https://emilkowal.ski/ui/great-animations 、https://emilkowal.ski/ui/7-practical-animation-tips | 原文：「best type of easing … is ease-out. It starts fast and slows down」「Your animations should also usually be shorter than 300ms」「never animate keyboard initiated actions」 | ① M1「进快出慢」= ease-out 的弹簧版；② 所有移植项 ≤295ms，M6 因此拒绝 HyperOS 549ms；③ **MiuiX `TabRow` 的 `tween(200, LinearEasing)` 不采纳**（§5-4） |
| 5 | MiuiX（`miuix-kotlin-multiplatform/miuix`، v0.9.4） | https://github.com/miuix-kotlin-multiplatform/miuix | §1.1 全部真实数值；`NavMotion` 对欠阻尼的强制声明（`require(dampingRatio >= 1f \| !clampOvershoot)`） | **只移植数值与手法，不引入依赖**：Niriko 只需 `miuix-blur`（已有），`miuix-ui` 会带进第二套 Shapes/Typography |
| 6 | kyant0 — Backdrop / smoother rounded corners | https://kyant.gitbook.io/backdrop/tutorials/smoother-rounded-corners | 连续曲率几何与 `drawBackdrop` 的 vibrancy→blur→lens 链 | **已对等**：`NirikoShapes.SquircleCornerShape`（`NirikoShapes.kt:214-238`）与 `GlassCard.kt:179-198` 的 `drawBackdrop(vibrancy/blur/lens/chromaticAberration)` 同族；HyperLight 的液态玻璃在 Niriko 无需外部移植 |
| 7 | Apple WWDC25「Build a SwiftUI app with the new design」 | https://developer.apple.com/videos/play/wwdc2025/323/ | 新设计里「交互以直接触碰为主，动效更强调」「玻璃材质作为层次而非装饰」 | 只取方向；具体数值未取得（§6-5） |

## 5 不采纳项（附理由）

1. **把 Navigation-Compose 换成 MiuiX `miuix-nav`（1:1 手指 + ζ1/146）**。理由：NavDriver 自带 `NavReconciler`/`NavSaveableStateHolder`/`PredictiveBackHandler` 整套导航栈；Niriko 现有 22 个 `composable{}` + 封面共享元素 + 预测返回都绑在 androidx Navigation 上，替换成本远超收益，且 549ms 违反 Emil <300ms。可取部分已压缩为 M6（方向感），不搬引擎。
2. **引入 `miuix-ui` 依赖**。理由：会带进第二套 Shapes/Typography/Theme 与 `NirikoShapes`、`MaterialTheme` 并行；数值可以抄，体系不能并。
3. **整张玻璃分组卡随行按下发光（给 `SettingsSplitGroup` 的 `appleGlassCard` 接 interactionSource）**。理由：按一行让 20/24dp 整卡边缘亮起（`GlassCard.kt:143-148`），在「组内 3–8 行」密度下会让卡边频繁闪动，属 Apple HIG「avoid adding motion to frequent interactions」的反例；行内已由 M1 承担反馈。保持现状不接线。
4. **MiuiX `TabRow` 的 `indicatorOffset.animateTo(tween(200, LinearEasing))`**。理由：线性缓动违反 Emil「ease-out 起快后慢」；且 Niriko 底栏走 kyant `DampedDragAnimation`（【刻意保留】），不该再混一套指示器动画。
5. **装饰性滚动视差 / 列表入场错峰**。理由：HyperLight 设置页无此证据（X 级，§6-2）；Apple HIG 明确不建议给高频交互加动效。
6. **HyperLight 的「柔光玻璃分档 + 实时动态描边 + Haze 背景模糊」搬进设置页**。理由：直接违反 Niriko 两层材质学（L0 消化壁纸 / L1 磨砂瓷 / L2 真折射）与「同屏实时 RenderEffect ≤ 2」；设置卡片是 L1，做实时 blur 会把每张卡变成 L2。HyperLight 自己也把液态玻璃标为「长期 Beta，功耗与性能需自行权衡」。

## 6 未验证项（结论边界）

1. **HyperLight 设置页源码不可得**（主线仓库仅 4 个文件）。§1 数值全部来自其声明技术栈 MiuiX v0.9.4 源码；「设置页原样使用 MiuiX 默认值」是 C 级推断。
2. **未取得 HyperLight 设置页截图/演示视频**：GitHub 页面与 README 只有文字；`mp.weixin.qq.com` 需环境校验、`coolapk1s.com` / `bbs.binmt.cc` 有 JS 反爬，均不可读。H4/H7 的观感未能与源码逐帧对齐。
3. **HyperLight 未公开其 MiuiX 版本**：本文以 v0.9.4 为准，个别数值可能随版本漂移。
4. **Spring → 时长的换算是数学估算**（1% 稳定时间），不是真机帧数据；`k=(2π/response)²` 属推断，但被两处自证（Switch 987 ↔ response 0.2；Nav 146 ↔ 注释 "~500ms"）。
5. **WWDC25 323 正文未取得**（只有标题与主题），§4-7 不引用其内部数值。
6. **M5 边界触感是否与 Niriko 现有 overscroll/回弹叠加重复触发**：只能真机验证。
7. **M2/M3 放大比例（1.08/1.10）按「不越出轨道」推的几何上限**，未真机目视；若显突兀下调至 1.05。
8. **未跑 gradle、未写任何测试**：§3 里所有 `*Test` 类名都是**待写契约**，不是既有测试。
9. **Nothing OS / Linear / Raycast 动效**：本轮未取得一手来源，不写结论（需要则另起专项）。
