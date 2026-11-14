# v1.2.2 kyant0 两仓逆向与可落地借鉴映射（task-4）

> 专项：把 `AndroidLiquidGlass-kmp`（88 kt）与 `Shapes-1.2.1`（16 kt）逆到参数级，回答「Niriko 还缺什么、能不能搬、搬了会不会撞红线」。
> 事实来源：① v1.2.1 HEAD 源码逐行阅读（路径:行号均为本轮实测）；② kyant 两仓源码；③ `io.github.kyant0:backdrop:1.0.6` 的 **sources.jar 解包核对**（比 KMP master 更权威，本文所有 AAR 行号来自解包副本）。
> 纪律：本轮未修改 `app/src` 下任何文件，未跑 gradle；旧文档行号会漂移，本文不引用旧文档行号。

## 0 覆盖范围与既成事实

### 0.1 先确认「本仓已有什么」——不要再提「引入新库」

| 项 | 事实 | 证据 |
| --- | --- | --- |
| backdrop 依赖 | 官方二进制 AAR **1.0.6**，已排除传递依赖 `kyant0:shapes-android` | `app/build.gradle.kts:154-156`（注释 :143-153 说明 2.0.1 需 AGP 9.1 / Compose 1.12，当前不可升级） |
| shapes 源码 | **内嵌 11 个文件**（Capsule / RoundedRectangle / RoundedRectangularShape / Lerp / ContinuousCurvature…Builder / RoundedRectangleOutline / UnevenRoundedRectangle / Rectangle / Copy / RectangleCornerRadii / RoundedCornerStyle） | `app/src/main/java/com/kyant/shapes/` |
| 版本对不对 | 与上游 `Shapes-1.2.1` **逐字相同，唯一差异是首行 vendored 注释**（我做了 `diff`：11/11 文件均只有 `1d0` 一行差异） | `diff app/src/main/java/com/kyant/shapes <上游 commonMain>` |
| 使用面 | 内嵌形状库**只有 1 个文件引用**：`Capsule()` 出现在 `ui/components/GlassControls.kt:94` 与 `:409`；`Lerp` / `squircle()` **零调用点** | `grep` 全仓 |
| 真玻璃管线接入点 | 10 处 `drawBackdrop`：GlassCard 2、LiquidBottomTabs 3、LiquidVerticalDock 3、GlassSectionCard 1、SearchGlassSurface 1 | `grep drawBackdrop\(|drawPlainBackdrop\(` |
| 上游工具类 | `DampedDragAnimation` / `InteractiveHighlight` / `DragGestureInspector` 已被原样搬进 `ui/bottombar/utils/`，且均带注释与 spring 五档 | `DampedDragAnimation.kt:20-61`、`:32-36` |

**结论**：本次逆向的产出不是「引入库」，而是「库已在、能力未用满」——重心在控件层（toggle / slider / button）与高光方向性。

### 0.2 精读清单

- kyant catalog：components 5（LiquidButton / LiquidSlider / LiquidToggle / LiquidBottomTab / LiquidBottomTabs）+ destinations 14（Home / LockScreen / Magnifier / Dialog / Buttons / SliderContent / ToggleContent / BottomTabs / ControlCenter 443 行 / GlassPlayground / AdaptiveLuminanceGlass / ProgressiveBlur / ScrollContainer×2）+ utils 7 + 根 6。全量清点 88 kt，精读 30。
- backdrop 库：`BackdropEffectScope` / `effects/{Lens,Blur,ColorFilter}` / `highlight/{Highlight,HighlightStyle}` / `shadow/{Shadow,InnerShadow}` / `backdrops/{Combined,Layer,Canvas}` / `internal/{ShapeProvider,Shaders}` / `DrawBackdropModifier`（385 行）。
- Shapes：库 11 + 示例 `Button.kt` / `Slider.kt` / `Border.kt` / `MainContent.kt`。

### 0.3 未验证项（结论边界）

1. 未跑 gradle、未真机目视：所有「目视」验收项需真机确认一次；按 `README.md:617`，**不得声称 Robolectric 截图回归**。
2. 未测 `lens` 在 API<33 的降级观感（库内 `effects/Lens.kt:22` 在 `SDK_INT < TIRAMISU` 直接 `return`，只剩 blur，视觉上「玻璃变成磨砂」）。
3. 未测新增高光角度对帧率的影响（高光是独立元素、只重绘，理论不进 RenderEffect 链，但未实测）。
4. 未验证 lead 广播的 `Graph.kt:50-53` 材质名字符串比较：本仓 `grep` `"appleGlass"` / `"liquidGlass"` **0 命中**，该文件在本仓不存在（`**/*Graph*.kt` 也 0 命中）——结论：**本仓当前无该隐患，但「新增玻璃件必须走 GlassDecision」的纪律仍成立**。

## 1 kyant 设计语言总述（观感特征，全部带数值）

1. **形状只有两种**：矩形与 `Capsule()`。`Capsule` 的 `style` 默认 `Continuous`（连续曲率），半径 = `minDimension/2`（`Shapes Capsule.kt:10-38`）。整个 demo 没有一处「24dp 圆角卡片」这种 M3 中间态。
2. **材质的活性全在 pressed**：未按压时控件是**不透明白芯**，按压才露出折射——`LiquidToggle.kt:197` / `LiquidSlider.kt:206` 的 `onDrawSurface = drawRect(White, alpha = 1 - progress)`。即「白球 → 玻璃球」是按压语义本身，不是附加装饰。
3. **控件比卡片更「透」也更「小」**：按钮 `blur(2dp) + lens(12,24)`（`LiquidButton.kt:60-61`）、开关 `blur(8dp) + lens(5p,10p)`（`:160-161`）、滑块 `blur(8dp) + lens(10p,14p)`（`LiquidSlider.kt:169-170`）；dock 才 `lens(24,24)`。参数随「件的大小」线性缩放，**没有一处常量写死**。
4. **边缘由 highlight 定义，且参数极细**：库默认 `Highlight(width = 0.5.dp, blurRadius = width/2)`（AAR `Highlight.kt:10-28`）。Niriko 用 `width = 1.dp, blurRadius = 0.5.dp`（`LiquidGlassTokens.kt:157-158`）——更粗更亮，这是**既有风格选择，不报文**。
5. **动效 = 两层，且都带初速**：`pressProgress` spring(1f, 1000f, 0.001f) 与 `velocity/10` 的挤压（`DampedDragAnimation.kt:32-36`、`LiquidBottomTabs.kt:289-295`）。kyant 从不做「跨越式 alpha 跳变」。
6. **数字都极小**：4dp 位移、10–20% 缩放、0.03 黑度差、`EaseOut` 侧移 4dp。观感上的「贵」来自克制，不来自强度。

## 2 逐组件参数级对照表

> 图例：「建议」= 采纳 / 改造 / 不采纳；「风险」列只写会撞红线的项。

| 组件 | kyant 真实数值（file:line） | Niriko 现状（file:line） | 差距 | 建议 | 风险 |
| --- | --- | --- | --- | --- | --- |
| LiquidButton | 48dp 高、水平 padding 16、Row spacing 8；`blur(2.dp)`+`lens(12.dp,24.dp)`（`LiquidButton.kt:60-61,116`）；按压 `scale = lerp(1, 1+4.dp/height, progress)`（`:69`）+ 沿指偏移 `maxOffset*tanh(0.05*offset/maxOffset)`（`:74-75`）+ 逐边 `maxDragScale*|cos/sin*offset/maxDim|`（`:79-84`） | 玻璃胶囊按钮：收藏按钮 `appleGlassCard(shape = RoundedCornerShape(999.dp))`（`ui/subject/SubjectDetailScreen.kt:2469`）；按压只有 ripple，无 scale/位移 | 缺「捏得到」的形变；圆角用 `RoundedCornerShape(999.dp)` 而非 `Capsule()` | 改造：加 `Modifier.glassPressMorph()`（见 §3-K4） | 28dp 小件上 4dp/height 会放成 1.14×，须按尺寸收敛 |
| LiquidToggle | 轨道 64×28dp；thumb 40×24dp；`dragWidth = 20.dp`（`LiquidToggle.kt:64`）；`pressedScale = 1.5f`（`:75`）；拖动 `delta = dragAmount.x/dragWidth`（`:91`）；轨道色 `lerp(track, accent, fraction)`（`:127`）；thumb 形变 `lerp(2/3, 0.75)`/`lerp(0, 0.75)`（`:150-151`）；`blur(8*(1-p))`+lens（`:160-161`）；velocity/50 挤压（`:191-193`）；白芯 α=1-p（`:197`） | 52×30dp、thumb 24dp、`Capsule()`、`animateDpAsState spring(0.6f,400f)` 目标值跳变（`GlassControls.kt:88-100`）；`clickable` 单点（`:118`）；无 drag、无 velocity、无 pressed 材质 | **拖不动（无跟手）；且拖出 slop 后松手 click 被取消 → 开关完全不响应**；白芯恒定不透明，按压材质无变化 | 改造（§3-K1） | 复用 `DampedDragAnimation` 需注意它自带 `pointerInput`（`DampedDragAnimation.kt:63-70`），与 `clickable` 二选一 |
| LiquidSlider | 轨道 6dp（`LiquidSlider.kt:127,135`）；thumb **40×24dp 胶囊**；`pressedScale = 1.5f`（`:83`）；tap 走 `animateToValue`（`:77` 附近）；thumb 位移 `(-w/2 + track*progress).coerceIn(-w/4, track-3w/4)`（`:148-151`）；`blur(8*(1-p))`+`lens(10p,14p)`（`:169-170`）；velocity/10 挤压（`:200-202`）；白芯（`:206`） | 34dp 高、轨道 8dp、thumb **22dp 白圆 + 2dp 主题色环**、`shadow(3.dp)`（`GlassControls.kt:182-184,255-265`）；已有 tap/drag 跟手与 `change.consume()`（`:194-227`） | thumb 是**唯一不参与玻璃材质的元素**，且「白圆+色环」与 dock 的玻璃胶囊语言冲突；无按压形变、无速度挤压；34dp 高 + 无 `ProgressBarRangeInfo`（【已知欠账】audit P0-3） | 改造（§3-K2） | 40×24 换形态属视觉改版，需设计确认；新增 drawBackdrop 会破 RenderEffect ≤2（必须用 graphicsLayer/drawBehind） |
| LiquidBottomTabs | 64dp 胶囊容器、padding 4、`blur(8)+lens(24,24)`、`layerBlock scale = lerp(1, 1+16.dp/w, p)`；隐藏选中层 + `combined(backdrop, tabsBackdrop)` 指示胶囊 `lens(10p,14p,chromatic)`；velocity/10 挤压；`panelOffset = 4dp*sign*EaseOut(|frac|)`（`LiquidBottomTabs.kt:194-221,262-309`） | **已 1:1 移植**：3 处 drawBackdrop（`LiquidBottomTabs.kt:194-309`）、同一套 spring 与 squash（`:289-295`）、同一套 panelOffset | 仅两点：① shape 用 `RoundedCornerShape(50.dp)` 而非 `Capsule()`（`:201,239,272`）；② highlight 固定 45°（`:279`） | 不改造（已达标）；仅做 K3/K5 的收口 | 任何「重写 dock」都是倒退，本轮禁止 |
| LiquidBottomTab | `LocalLiquidBottomTabScale` 1.2 缩放、`indication = null` + `Role.Tab`（`LiquidBottomTab.kt`） | 同构（`ui/bottombar/LiquidBottomTab.kt:34-49`，`clip(RoundedCornerShape(50.dp))`） | 圆角口径同上 | 同 K5 | — |
| FlightIcon | 24dp / 960 viewport 的飞机 ImageVector，**只是图标**，不是共享元素机制 | 共享元素走 Compose sharedBounds，`SUBJECT_COVER_DURATION_MS = 200` 已定稿 | 无差距 | 不采纳 | — |
| Ripple | AOSP 2024 fork，`RippleAlpha` 改 pressed 0.1 / hovered 0.08（`Ripple.kt`） | 全仓 `Ripple` 0 命中，走 M3 默认 `LocalIndication` | 无差距（未接管 indication） | 不采纳（见 §4-N2） | 改 indication 会影响全部 128 处 clickable |
| DialogContent | 48dp 圆角、`colorControls(brightness 0.2/0, saturation 1.5)`+`blur(16/8)`+`lens(24,48,depth)`、`Highlight.Plain`（`DialogContent.kt`） | AlertDialog 不透明（21 处圆角未统一 = 【已知欠账】audit P0-4；README v1.2.1 已说明跨窗口 backdrop 不成立） | 档位不同（对话框是**不透明**而非玻璃） | 不采纳（§4-N8） | 跨窗口 backdrop 需要 API34 WindowBlurBehindEffect + 自绘 |
| ButtonsContent | 4 变体：透明 / `surfaceColor(White α0.3)` / `tint` + `BlendMode.Hue` 再叠 α0.75 / 橙色 tint | 收藏按钮 tint 走 `onDrawSurface` 渐变色（`GlassCard.kt:121-131`，α0.05 只画上 1/3） | 没有「色相染色玻璃」这一档 | 观察项，不列入本轮 | `BlendMode.Hue` 在低 alpha 下会发灰 |
| ControlCenterContent | `HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f)`（`:99-106`）→ 高光轴跟设备倾角；面板级 `BlurEffect(4dp*progress)` 一处模糊；`lens(24p,48p,depth)` | 高光角度**恒 45°**（`LiquidGlassTokens.kt:156-161` 未传 angle），全库无 angle 传参；环境光只喂稀有卡 painter（`CardRarityMaterial.kt:213-245`，`:220` STANDARD 早退） | **缺「材质对环境有反应」**：倾角/按压都不改变高光方向 | 改造（§3-K3） | 传感器门控在 `ambientReflectionEnabled`（`AmbientTiltMath.kt:48-49`，绑 visibleRarity）；`HighlightStyle.Ambient` 的 angle **硬编码 45°、无法传参**（AAR `HighlightStyle.kt:113-114`），只能用 `Default` |
| MagnifierContent | `128×96dp` 胶囊 + `lens(8,24,depth,chromatic)` + `withTransform{scale(1.5)}` 放大光标上方 80dp | 无同类需求 | — | 不采纳（§4-N4） | — |
| ProgressiveBlurContent | `drawPlainBackdrop(blur(4dp) + runtimeShader(AlphaMask))`，AGSL 内 `mix(content*blurAlpha, tint*tintAlpha, 0.8)`，`smoothstep(size.y, size.y*0.5, y)` | `topContentAlphaMask`（`ui/components/TopContentAlphaMask.kt:27-57`）只做 `DstIn` alpha 渐隐，无 blur、无 tint | 我们的更轻；kyant 的模糊半径是**常量**、渐隐靠 alpha 混 | 不采纳（§4-N5） | 全屏 RenderEffect 撞 ≤2 红线 |
| AdaptiveLuminanceGlassContent | 背后亮度采样（25px）→ `colorControls` 亮度/对比随 luma 变、`blur` 8→16dp、文字 `tween(1000)` 黑/白反色 | 有 `LocalGlassLuminance` + 底色自适应 `adaptive = (luma-0.5)*0.10`（`GlassCard.kt:113-117`）与 `extractAmbientTint`（`GlassCard.kt:389-426`）；**无文字反色** | 玻璃上的文字/图标不随背后亮度换色 | 改造（§3-K6） | 全局改文字色风险大，只限 L2 浮层 |
| HomeContent / LockScreenContent | SDF 纹理时钟（`drawPlainBackdrop` + `sdfShader`、`draggable2D`） | 无需求 | — | 不采纳 | — |
| utils: DampedDragAnimation | 5 档 spring（`valueAnimation` spring(1,1000,vis)、`velocity` spring(0.5,300,vis*10)、`pressProgress` spring(1,1000,0.001)、`scaleX` spring(0.6,250)、`scaleY` spring(0.7,250)）；`value/progress/targetValue/pressProgress/scaleX/scaleY/velocity` 全暴露 | 同构已搬：`ui/bottombar/utils/DampedDragAnimation.kt:20-61`（`:32-36` 即五档） | 只被 dock 用，控件层未复用 | 采纳（K1/K2 复用） | 它自带 `pointerInput`（`:63-70`），控件里需用 `value` 侧接而非直接挂 |
| utils: InteractiveHighlight | AGSL 径向高光 + 全屏 `Plus` 0.08；`pressProgress` spring(0.5,300,0.001) | 同构已搬（`ui/bottombar/utils/InteractiveHighlight.kt`），仅 dock/tab 用 | 未用于 toggle/slider | 可选 | 每件一个 shader 缓存，控件层批量用需节流 |
| utils: DragGestureInspector | `awaitFirstDown(false, Initial)` 预读 + 拖动中 `change.isConsumed` 即中止（不抢已消费手势） | 同构已搬（`ui/bottombar/utils/DragGestureInspector.kt:22-53`），并被 `PressTilt.kt:72-82` / `TouchGlow.kt:41` / `BlankTapRestore.kt:30-38` 复用 | 无差距 | 维持 | — |
| utils: UISensor | `TYPE_ACCELEROMETER` + `SENSOR_DELAY_UI`，α=0.5 低通，输出 **度**（`UISensor.android.kt:34-68`），初值 45f | `AmbientTiltMath`/`AmbientTiltReflection` 更强：`TYPE_ROTATION_VECTOR`、20Hz、33ms 节流、0.005 阈值、指数低通、lifecycle 门控、输出归一化 ±1（`AmbientTiltMath.kt:9-49`、`AmbientTiltReflection.kt:55-122`） | 我们更完整，但**消费面过窄** | 不采纳实现，只借「angle 跟重力」的语义（K3） | 扩门控会带来常驻传感器耗电 |
| backdrop: HighlightStyle | `Default(color White α0.5, Plus, angle = 45f, falloff = 1f)`，angle 单位=度（AAR `HighlightStyle.kt:49-54,83`）；shader `pow(abs(dot(grad, normal)), falloff)`（`Shaders.kt:161-181`）→ **对称双高光**；`Ambient` 是 `step(0,d)` 单侧光、angle 不可配（`:113-114`） | `HighlightStyle.Default(color = BorderLight)` 只改色不改角（`LiquidGlassTokens.kt:156-161`） | 高光轴固定 45°、falloff=1（边缘均匀亮） | 采纳改造（K3） | abs() 导致「对称两束光」，做方向性高光时须靠 falloff 收束，否则两端都亮 |
| backdrop: effects/Lens | `refractionHeight/Amount <= 0` 直接 return；cornerRadii **只接受** `RoundedRectangularShape` / `AbsoluteRoundedCornerShape` / `CornerBasedShape`，否则 `throw UnsupportedOperationException`（AAR `Lens.kt:16-120`，`:116-120`）；`refractionAmount` 取负（`:49`） | 3 处调用全部传 `RoundedCornerShape` / `Capsule()`（两者都合格） | 无差距，但**存在雷区**：`NirikoShapes.squircle()` 产出 `SquircleCornerShape`（`Outline.Generic`） | 不采纳 squircle 进玻璃（§4-N7） | 一旦有人把 `squircle()` 传给 `appleGlassCard` → **运行时崩溃** |
| backdrop: DrawBackdropModifier | `effects{}` 里的 State 读取走 `observeReads{ updateEffects() }` → **每帧重建 RenderEffect 链**；`highlight/shadow/layerBlock/onDrawSurface` 里的 State 只触发重绘（AAR `DrawBackdropModifier.kt:104-132,347-365`） | dock 在 `effects{}` 内读 `pressProgress`（`LiquidBottomTabs.kt:273-276`）—— 与上游写法一致 | 无差距（既有取舍） | 维持【刻意保留】：与 kyant 上游同款，本轮不动 | 新增控件若要「按压改变 lens」必须接受重建成本，否则只在 highlight/layerBlock 里做 |
| backdrop: backdrops | `rememberCombinedBackdrop` 顺序叠加；`LayerBackdrop` 可导出采样；`CanvasBackdrop` 支持纯色底（demo 用来证明组件不依赖壁纸） | dock 用 `combined(backdrop, tabsBackdrop)`（`LiquidBottomTabs.kt:271`）；搜索垫用 `LocalSearchGlassBackdrop`；纯色底场景由 `emptyBackdrop()` 兜底（`GlassCard.kt:199-210`） | 无差距 | 维持 | — |
| Shapes: Capsule / Lerp | `Capsule` style 默认 `Continuous`（`Capsule.kt:10-38`）；`lerp(start: RoundedRectangularShape, stop, fraction, style?)` 逐角插值再钳 `minDim*0.5`（`Lerp.kt`） | 内嵌可用，但 `Lerp` **零调用**；`squircle()` **零调用** | 形变能力库内有、产品未用 | 不采纳逐帧形变（§4-N7）；只采纳 `Capsule()` 收口（K5） | 改 shape ⇒ 重建 RenderEffect，逐帧形变不可行 |

## 3 可移植清单（按收益排序）

> 每条 = 现象 / 证据 / 归因 / 方案（token·数值·签名·落点）/ 验收。
> 分级：P0 一眼看出不对 · P1 明显不致命 · P2 打磨。
> **本轮无 P0**：dock/底栏已 1:1 移植（含 velocity squash），缺陷集中在控件层材质响应，属 P1。若把「开关能拖动」当体感卖点，K1 可提 P0。

**三分类结论（先给判断，再给清单）**

| 分类 | 条目 | 成本量级 | 说明 |
| --- | --- | --- | --- |
| 能直接搬（**0 条**） | — | — | 唯一「原样可搬」的东西（`DampedDragAnimation` / `InteractiveHighlight` / `DragGestureInspector`、dock 三层玻璃）**已经搬完**：`ui/bottombar/utils/` 三件套 + `LiquidBottomTabs.kt` 全链路。所以本轮不存在「零适配移植」项——这是逆向的诚实结论，也是最容易被误报成「v1.2.2 新能力」的部分。 |
| 需要改（**4 条 P1**） | K1 开关拖动结算 · K2 滑块 thumb 材质 · K3 高光方向性 · K4 按钮按压形变 | 每条 = 1 个纯函数文件 + 1 处组件内改动（约 60–120 行含单测） | 共同点：kyant 的**机制可借、数值必须按 Niriko 尺寸重标**；四条全部禁止新增 `drawBackdrop`。 |
| 不能搬（**9 条**） | N1–N9（见 §4） | — | 三类阻断：与两层材质学冲突（N5）、与 RenderEffect 预算冲突（N5/N7）、库与平台能力边界（N7 的 `lens` 只吃 `CornerBasedShape`；N8 的跨窗口 backdrop）。 |

### K1【P1】玻璃开关：拖动跟手 + 松手按速度结算（现实现会丢手势）

- **现象**：手指按住 `GlassToggle` 圆钮横向拖动，圆钮**纹丝不动**；一旦拖出 touch slop 再松手，开关**完全不切换**（`clickable` 的 tap 已被 slop 取消），视觉与行为同时「死机」。kyant 侧是全程跟手、松手按「>0.5 → 1，否则 0」+ 反向拖动回弹。
- **证据**：`ui/components/GlassControls.kt:88-100` 只有 `thumbOffset = animateDpAsState(targetValue = glassToggleThumbOffset(...), spring(0.6f, 400f))`（纯目标值动画）；`:118` 唯一交互是 `.clickable(enabled) { onCheckedChange(!checked) }`；对照 kyant `LiquidToggle.kt:64`（`dragWidth = 20.dp`）、`:91`（`delta = dragAmount.x / dragWidth`）、`:75`（`pressedScale = 1.5f`）。
- **归因**：违反 motion 纪律「可中断、跟手优先」（`spring` 目标值动画在被手指拖拽时应被指针位置接管）；同时违反「开关是拖拽控件」的既有平台语义（M3 `Switch` 支持拖动，自绘件替换后丢了该能力）。
- **方案**：抽纯函数到 `ui/components/glass/GlassToggleMath.kt`：`glassToggleDragFraction(startFraction: Float, dragX: Float, trackPx: Float): Float = (startFraction + dragX / (trackPx - thumbPx)).coerceIn(0f, 1f)` 与 `settleTarget(fraction: Float, velocityDpPerSec: Float): Boolean`（`|v| > 900` 时按方向、否则 `fraction >= 0.5f`，阈值与 `NirikoNonModalSheet.kt:45-60` 的 900dp/s 对齐）。组件内：`pointerInput` 用 `detectDragGestures` 起手 `startFraction = if (checked) 1f else 0f`，拖动时 `onCheckedChange(frac >= 0.5f)` 只更新视觉、松手才 `onCheckedChange(settleTarget)`；thumb 位移改由本地 `fraction` 驱动（`animateFloatAsState` 仅在非拖拽时启用）；按下 `scale 1f → 1.12f`（`spring(dampingRatio = 0.5f, stiffness = 400f)`，与 `GlassCard.kt:146` 同值）。**不要**直接挂 `DampedDragAnimation.modifier`（它自带 `pointerInput`，见 `DampedDragAnimation.kt:63-70`），只用其五档数值。
- **验收**：新增 `GlassToggleMathTest`（纯函数：中点两侧、速度翻转、clamp 边界）；真机：① 按住圆钮拖到最右，圆钮跟手；② 中途反向不跳变；③ 从 0 拖到 60% 松手 → 变 1，40% 松手 → 回 0；④ 快速甩到 30% 松手 → 过冲后回弹 0。

### K2【P1】玻璃滑块 thumb：让「白圆」变成参与材质的玻璃胶囊

- **现象**：`GlassSlider` 的圆钮是全 App 唯一**不参与玻璃材质**的玻璃控件元素：一颗 22dp 不透明白圆 + 2dp 主题色环 + 3dp 投影（`GlassControls.kt:255-265`），而它坐在一块真折射玻璃胶囊上。kyant 的滑块 thumb 是 40×24dp 的**玻璃胶囊**：未按压时「白芯」（`alpha = 1 - progress`，`LiquidSlider.kt:206`），按下瞬间白芯淡出、`lens(10p,14p)` 浮现，同时 `pressedScale 1.5`、`blur 8dp*(1-p)` 退化成清晰透镜。
- **证据**：`ui/components/GlassControls.kt:182-184`（`trackHeight 8.dp` / `thumbSize 22.dp` / `horizontalPad 14.dp`）、`:255-265`（`.shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White).border(2.dp, primary, CircleShape)`）；对照 `LiquidSlider.kt:83,141-151,169-170,200-202,206`。
- **归因**：违反「L2 材质学」的一致性——同一块玻璃上的子件必须共享材质语言；也是「按压时材质变化」通道的缺口（`docs/audit-2026-10` 之外的观感项）。
- **方案**（分两步，避免一次改版）：① **形变层（本轮建议）** 在 thumb 外包一层 `graphicsLayer`：`scale = if (pressed) 1.12f else 1f`（`animateFloatAsState(spring(0.5f, 400f))`）、`translationX` 由 `fraction` 直接驱动（已有），阴影 `3.dp → 6.dp`；按下时 `border` 色由 `primary` 改为 `Color.White.copy(alpha = 0.85f)`（让白圆「融化」进玻璃）。② **材质层（后续版本）** 给 thumb 加一个 **不新增 RenderEffect** 的镜面环（见 K3 的 `specularRing`），并用 `BlendMode.Plus` 画；**禁止**在 thumb 上再挂 `drawBackdrop`（会把同屏真玻璃件从 3 条推到 4 条，破「同屏实时 RenderEffect ≤2」的实际预算）。
- **验收**：扩展 `GlassSliderMathTest`（新增 `thumbScale(pressed)` / `thumbBorderAlpha(pressed)` 纯函数断言）；真机：① 按住 thumb 瞬间放大且阴影加深；② 甩动时无跳变；③ 松手后 250ms 内回到 1.0×；④ 顺带补 `semantics { progressBarRangeInfo = ProgressBarRangeInfo(...) }`（归 audit P0-3，不重复计为本轮新缺陷）。

### K3【P1】让玻璃边缘的镜面高光有方向：angle 跟随按压倾斜（+ 接通 interactionSource）

- **现象**：所有真玻璃件的高光带**永远在 45°**，无论按压哪个角、手机怎么倾。kyant 把高光轴接到重力角：`Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f))`（`ControlCenterContent.kt:99-106`）。更近的一层证据是「本来就有」的通道：`appleGlassCard` 已经接收 `lighting: PressTiltLighting?`（`GlassCard.kt:103`），但只喂给稀有卡 painter，没喂给高光。
- **证据**：`ui/components/liquidglass/LiquidGlassTokens.kt:156-161`（`liquidGlassHighlight(alpha)` 只透传 alpha，`HighlightStyle.Default(color = BorderLight)` 用默认 angle 45f / falloff 1f）；`GlassCard.kt:149`（`val highlight = { liquidGlassHighlight(alpha = highlightAlpha) }`）；`GlassCard.kt:154`（`rememberCardRaritySurface(material, shape, lighting, breathing)` 是 lighting 唯一消费点）；`CardRarityMaterial.kt:220`（`STANDARD` 早退 → 绝大多数卡拿不到）；`PressTilt.kt:37-38`（`rotationX/rotationY` 是**度**，±7°）。kyant 侧 `HighlightStyle.Default(angle, falloff)` 的 angle 单位=度（AAR `HighlightStyle.kt:83`），`pow(abs(dot(grad, normal)), falloff)`（AAR `Shaders.kt:161-181`）→ falloff 从 1 提到 1.5–2 会明显收束高光带。
- **归因**：违反「材质对环境有反应」（三层材质学的 L2 语义：玻璃应当反射环境，而非贴一条固定渐变）；同时是 `PressTiltLighting` 通道的**未接通**，属于既有能力复用而非新造机制。
- **方案**：① 扩展签名 `fun liquidGlassHighlight(alpha: Float, angle: Float = 45f, falloff: Float = 1.5f): Highlight = Highlight.Default.copy(..., style = HighlightStyle.Default(color = LiquidGlassPalette.BorderLight, angle = angle, falloff = falloff))`（`LiquidGlassTokens.kt:156`，默认值保证 3 个既有调用点零改动）。② 新增纯函数 `ui/components/glass/SpecularAngleMath.kt`：`fun specularAngleDegrees(rotationX: Float, rotationY: Float, base: Float = 45f, gain: Float = 4f): Float = (base + (rotationY - rotationX) * gain).coerceIn(0f, 90f)`（±7° 倾角 → 最多偏移 28°）。③ `appleGlassCard` 内：`val highlight = { liquidGlassHighlight(alpha = highlightAlpha, angle = lighting?.let { specularAngleDegrees(it.rotationX, it.rotationY) } ?: 45f) }`（`GlassCard.kt:149` 一行）；`reduceMotion` 时强制 45°（复用 `LocalReduceMotion`，与 `PressTilt.kt:65` 同判据）。④ 同时修「开关/滑块玻璃外壳按压不亮」：`GlassControls.kt:112` 的 `appleGlassCard(shape = shape)` **未传 interactionSource**，导致 `:143-148` 的 0.65→0.95 高光动画对控件永不生效——传入与 `clickable` 共用的 `MutableInteractionSource`。
- **验收**：新增 `SpecularAngleTest`（无倾角=45°、单调性、clamp 0..90、reduceMotion=45°）；真机：① 按住卡片左上角，高光带移向右下（方向以最终 sign 为准，需目视定标一次）；② 松手 300ms 内回 45°；③ 开关按下时玻璃边缘变亮；④ 打开「减少动态效果」后高光恒 45° 且无闪烁。

### K4【P1】玻璃胶囊按钮：按压时「下沉 + 沿指位移」

- **现象**：收藏按钮（28dp 悬浮玻璃胶囊）按下只有水波纹，玻璃本身**不变形、不移动**。kyant 的按钮按下会按尺寸比例放大 4dp，并沿手指偏移量 `tanh` 挤压（越往边缘拖，偏移越趋饱和）。
- **证据**：`ui/subject/SubjectDetailScreen.kt:2469`（`.appleGlassCard(shape = RoundedCornerShape(999.dp))`）；`GlassCard.kt:143-149` 只有高光 alpha 变化；对照 `LiquidButton.kt:69`（`scale = lerp(1f, 1f + 4.dp/height, progress)`）、`:74-75`（`translation = maxOffset * tanh(0.05 * offset / maxOffset)`）。
- **归因**：缺「按压材质变化」第三层（材质 + 影子 + 形变），当前只有影子与高光。
- **方案**：新增 `ui/components/liquidglass/GlassPressMorph.kt`：`fun Modifier.glassPressMorph(interactionSource: MutableInteractionSource, maxScaleDp: Dp = 2.dp, maxOffsetDp: Dp = 3.dp, pressed: Boolean): Modifier`，内部 `graphicsLayer { val p = if (pressed) 1f else 0f; scaleX = scaleY = 1f + maxScaleDp.toPx() / size.height; translationX = -offsetX * tanh(...) }`，`animateFloatAsState(spring(0.5f, 400f))`（与 `GlassCard.kt:146` 一致）。**数值收敛**：`maxScaleDp = 2.dp`（28dp 件 → 1.07×；kyant 的 4dp 在 48dp 件上约 1.08×，比例一致）。
- **验收**：新增 `GlassPressMorphTest`（scale 随高度反比、上下限 clamp）；真机：按下收藏按钮时胶囊下沉 1dp 且轻微放大，松手 200ms 内回位；与相邻「分享」按钮节奏一致。

### K5【P2】胶囊几何收口：dock 与 tab 的 5 处 `RoundedCornerShape(50.dp)` → `Capsule()`

- **现象**：App 里最显眼的两块玻璃（底栏 64dp 胶囊、tab 选中区）用的是**圆弧胶囊**，而同一屏的玻璃开关/评分条已经改成 `Capsule()`（**连续曲率**）。两者高度差 1 个几何口径，端头肩部曲率不同，肉眼在大屏/桌面模式下能看出「同一套材质、两种圆角」。
- **证据**：`ui/bottombar/LiquidBottomTabs.kt:201`、`:239`、`:272`，`ui/bottombar/LiquidBottomTab.kt:34`（均 `RoundedCornerShape(50.dp)`）；对照 `ui/components/GlassControls.kt:94`（`val shape: Shape = Capsule()`，注释 :92-93 明说「与 kyant 上游开关同一几何口径，也让玻璃控件形状从 RoundedCornerShape 世界收敛到一套库上」）与 `:409`。
- **归因**：M1「令牌不内聚」的几何分支——圆角口径两条来源（`NirikoShapes.PillShape = RoundedCornerShape(percent = 50)`（`NirikoShapes.kt:94`）vs `com.kyant.shapes.Capsule`）。
- **方案**：把上述 5 处替换为 `Capsule()`（`com.kyant.shapes.Capsule`，默认 `Continuous`）；`NirikoShapes` 增设 `val CapsuleShape: Shape = Capsule()` 并在 `NirikoShapes.kt:94` 的 `PillShape` 注释里标注「非玻璃路径的圆角仍用它」。**须先核**：`lens` 接受 `Capsule`（AAR `Lens.kt:64-72` 判定 `RoundedRectangularShape` → 通过），3 处 `drawBackdrop` 形状合法。
- **验收**：新增 `PillShapeContractTest`（源码字符串断言：`LiquidBottomTabs.kt` 内不再出现 `RoundedCornerShape(50.dp)`，且使用 `Capsule(`）；真机放大目视底栏端头与开关圆钮肩部曲率一致（`reduceMotion` / 玻璃 OFF 档各看一次）。

### K6【P2】L2 浮层上的文字随背后亮度反色（借 AdaptiveLuminance 的语义，不借实现）

- **现象**：玻璃件的**底**已经自适应亮度（`adaptive = (luma - 0.5f) * 0.10f`，浅色 clamp 0.14–0.32 / 深色 0.22–0.42），但**字色不变**。在亮壁纸 + 亮色主题下，dock/搜索垫上的浅字靠 0.42 上限的底色兜底，属「刚好够」；kyant 的做法是采样后把文字 1s 内切到黑/白（`AdaptiveLuminanceGlassContent.kt:87-120`）。
- **证据**：`ui/components/GlassCard.kt:113-117`；`ui/components/liquidglass/LiquidGlassTokens.kt:196-199`（`liquidGlassTint()` 只看 `LocalDarkTheme`）；`ui/components/GlassCard.kt:389-426`（`extractAmbientTint` 只取色相，不判明暗）。
- **归因**：材质学「可读性优先于美感」的下半句——只做了底、没做字。
- **方案**：在 `LiquidGlassTokens.kt` 增 `fun onGlassContentColor(isDark: Boolean, luma: Float): Color`：`luma > 0.62f && !isDark → Color(0xFF1B1B1F)`，`luma < 0.30f && isDark → Color(0xFFF4F4F8)`，其余返回 `Color.Unspecified`（= 不动，最小侵入）。只在 **L2 浮层**消费（dock 文本、搜索实底垫、收藏胶囊内图标），**不要**改卡片内正文（会与 `LocalContentColor` 打架）。`reduceMotion` 不参与（颜色切换非动效），但切换用 `animateColorAsState(tween(600))` 而非硬切。
- **验收**：新增 `GlassLuminanceTextTest`（阈值边界 + Unspecified 分支）；真机：纯白壁纸下 dock 文字对比度 ≥ 4.5:1（`ui-review` 的无障碍线），深色 + OLED 黑壁纸下反向成立。

## 4 不采纳项与理由

| # | 不采纳项 | 理由（含数值/机制） | 若将来要做的前置条件 |
| --- | --- | --- | --- |
| N1 | FlightIcon 作为共享元素机制 | 它只是 24dp/960 viewport 的飞机 `ImageVector`（`FlightIcon.kt`），飞行是 Compose `sharedBounds` 的能力，不是 kyant 的东西。Niriko 已有 200ms + 10ms keyframes 的封面共享（不可改）。 | — |
| N2 | 接管 ripple（AOSP fork，pressed 0.1 / hovered 0.08） | 全仓 128 处 `clickable` 走 M3 默认 indication；单独改 alpha 会与 60+ 个 M3 组件（Button/Card/Chip）不一致，收益 < 一致性代价。 | 若做，必须成对改 `LocalIndication` + 全部自绘件，且要有「玻璃上水波纹是否该存在」的整体裁决。 |
| N3 | ControlCenterContent 整块（68dp 双格、拖拽 0..1 折叠、面板级 `BlurEffect(4dp*progress)`） | Niriko 没有同类页面（控制中心式大面板）；单独搬会新增第 4 套玻璃装配语法。只借 `angle` 语义（K3）。 | 出现「快捷面板」需求时再评估。 |
| N4 | MagnifierContent（`layerBackdrop` + 1.5× 放大镜 + 光标 4×24dp 胶囊） | 无产品需求；且它每帧走 `LayerBackdrop` 采样 + `scale(1.5)` 变换，成本高。 | — |
| N5 | ProgressiveBlur 的 AGSL `mix(content*blurAlpha, tint*tintAlpha)` 与全屏 blur | 与「同屏实时 RenderEffect ≤2」直接冲突（这是全屏级 RenderEffect）；且 Niriko 的可读性兜底已由「搜索展开 94% 实心底」+ `TopContentAlphaMask` 的 `DstIn` 淡出解决。加 tint 会把「内容淡出」变成「实底遮罩」，与 L1 磨砂瓷的壁纸透出冲突。 | 若未来要 blur，只能走 API34 `WindowBlurBehindEffect`（`GlassCard.kt:324-345` 已有实现，仅 `SubjectDetailScreen.kt:2449` 在用）。 |
| N6 | AdaptiveLuminance 的「每帧 `layer.toImageBitmap()` 采样」 | 需要 GPU→CPU 回读（demo 用 5×5 降采样到 25px 缓解）；Niriko 已有纯 CPU 的 `LocalGlassLuminance` + `extractAmbientTint`（`GlassCard.kt:389-426`，step = `min(w,h)/16`，跳过 lum 12..243、sat > 0.3 优先），成本更低。 | — |
| N7 | 形状形变（`Lerp` / `squircle()` / 连续曲率半径动画） | 三重阻断：① `Lens` 只接受 `RoundedRectangularShape`/`CornerBasedShape`，`SquircleCornerShape` 是 `Outline.Generic` → **真玻璃路径直接抛异常**（AAR `Lens.kt:116-120`）；② 逐帧改 shape 会触发 `drawBackdrop` 的 `effects` 重算 → 每帧重建 RenderEffect；③ 上游自己也只在 `MainContent.kt:44-50` 做了静态 `lerp(Rectangle, Capsule(), r).createOutline()`，没有产品级形变。 | 若真要形变，只能用于**非玻璃**元素（封面图裁剪、静态卡片），且不得传入 `lens` 所在的 `appleGlassCard`。 |
| N8 | DialogContent 的 48dp 圆角 + `lens(24,48,depth)` 真玻璃对话框 | `AlertDialog` 是独立窗口，backdrop 跨不过去（README v1.2.1 已记录）；「21 个 AlertDialog 圆角未统一」为【已知欠账】audit P0-4，本轮只核现状、不重复计数。 | 需 API34 `WindowBlurBehindEffect` + 自绘对话框（成本远超本轮）。 |
| N9 | 直接用 UISensor 替换 `AmbientTilt*` | 我们已有更强实现（rotation vector / 20Hz / 33ms 节流 / 0.005 阈值 / lifecycle 门控 / ±1 归一化）；kyant 的 α=0.5 低通 + `SENSOR_DELAY_UI` 是 demo 级。 | — |

## 5 风险与验收

### 5.1 风险（按触发概率排序）

1. **RenderEffect 预算被控件吃掉**：dock 单个组件内就有 3 处 `drawBackdrop`（容器 `LiquidBottomTabs.kt:194` / 隐藏内容层 `:223` / 指示胶囊 `:262`），卡片 1 处（`GlassCard.kt:179`）——同屏已 4 条渲染链，与「同屏实时 RenderEffect ≤2」的目标本就紧张（既有性能账，不在本轮范围）。因此 **硬约束：K1/K2/K4 只允许 `graphicsLayer` / `drawBehind` / `onDrawSurface`，不得新增第 5 条 `drawBackdrop`。**
2. **在 `effects{}` 里读动画值 = 每帧重建渲染链**（AAR `DrawBackdropModifier.kt:347-365`）。dock 现在就在 `effects{}` 里读 `pressProgress`（`LiquidBottomTabs.kt:273-276`）——与上游同款，属【刻意保留】，本轮不动；但 K3 的 angle **必须**放进 `highlight{}`（只重绘），不要放 `effects{}`。
3. **两套几何的雷区**：任何 `Outline.Generic` 形状（`squircle()`、自定义 Path、`BlurMaskFilter` 描边）都不能进真玻璃 `lens`，否则抛 `UnsupportedOperationException`（AAR `Lens.kt:116-120`）。落地 K5 时必须确认只换了 `Capsule`。
4. **传感器门控**：`ambientReflectionEnabled(active, visibleRarity, reduceMotion)`（`AmbientTiltMath.kt:48-49`）绑 `visibleRarity`，所以「用环境光驱动高光」需要改 demand 语义（`AmbientTiltReflection.kt` 的 `AmbientTiltDemand.visibleRarity :48-52`），会带来常驻传感器耗电。K3 用 `PressTiltLighting`（已有、无需传感器）规避此风险。
5. **API 版本降级**：`lens` 在 `SDK_INT < TIRAMISU` 直接 return（AAR `Lens.kt:22`）。K1/K2/K4 不依赖 RenderEffect，全版本可用；K5 换 `Capsule` 也不涉及 API 分支——**这是本轮清单可以无 API 版本风险落地的根本原因**。
6. **材质名/档位判定必须走 GlassDecision**：本仓当前无字符串比较（`grep` 0 命中），但 `appleGlassCard` 有 30+ 调用点，任何新增「玻璃类型」都要过 `GlassDecision` / `normalizeMaterialName`，否则真玻璃/静态降级会静默失配。

### 5.2 验收

- **可写单测（契约级，纯函数优先）**：`GlassToggleMathTest`（K1）、`SpecularAngleTest`（K3）、`GlassPressMorphTest`（K4）、`GlassLuminanceTextTest`（K6）、`PillShapeContractTest`（K5，源码字符串断言）；K2 扩展既有 `GlassSliderMathTest`。全部为**待写**，不是既有测试。
- **必须真机目视**：① 开关拖动跟手/反向/甩动回弹（K1）；② 按住滑块 thumb 的放大与阴影（K2）；③ 按压四角时高光带方向（K3，**需先定标 sign**）；④ 收藏胶囊下沉（K4）；⑤ 底栏端头曲率（K5）；⑥ 纯白壁纸下 dock 文字 4.5:1（K6）。
- **回归护栏（改动这些文件必跑）**：`GlassDecisionTest`、`GlassProviderIdentityTest`、`SurfaceTokensTest`、`NirikoShapesTest`、`GlassSliderMathTest`、`DockTouchStateTest`、`PressTiltTest`、`SubjectCoverMotionTest`。
- **禁止**：用 Robolectric 声称真玻璃截图回归（`README.md:617`：无法渲染 kyant/AGSL 真玻璃）。

### 5.3 与其它专区的去重声明

- K1/K2 与 `docs/v1.2.2/03-interaction-feedback.md` 的第 10/11 行（`GlassToggle` / `GlassSlider`）**重叠**：本轮只补 kyant 侧数值与移植做法，不重复计级；其中「拖出 slop 后开关无响应」与「thumb 材质语言」是本文新增判断。
- 以下为【已知欠账】，本文不重复计数：`GlassToggle` 30dp 高、`GlassSlider` 34dp 高且无 `ProgressBarRangeInfo`（audit P0-3）；21 个 `AlertDialog` 圆角未统一（audit P0-4）。
- 以下为【刻意保留，勿报】：dock 的 kyant 弹簧（`spring(1f, 1000f, …)` 等五档）、220ms 底栏隐藏、350ms 详情材质渐显、`Shimmer` 静态退化、`AlertDialog` 不透明、`pressSpring`/`appearSpring`/`panelSpring`。