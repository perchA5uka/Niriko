# WP10 实施日志：kyant 参考落地的开关 / 滑块手感（task-7）

改动：GlassControls.kt、liquidglass/LiquidGlassTokens.kt、GlassToggleMath.kt（新）、
GlassToggleMathTest.kt（新，12 例）；GlassCard.kt 仅 highlight lambda + 2 行 import（Lead 授权）。

## 1. K3 高光受光角度（LiquidGlassTokens / GlassCard）
- `LIQUID_GLASS_HIGHLIGHT_ANGLE_DEGREES = 45f` 逐字等于 backdrop 1.0.6 `HighlightStyle.Default.angle`
  （sources.jar：createShader 里 `angle * PI / 180`）。默认路径（lighting == null 的 18 个卡片调用点）
  因此与改动前逐像素同观感。
- `LIQUID_GLASS_HIGHLIGHT_TILT_DEGREES = 20f`：pressTilt 的 rotationY 归一上限 7°（pressTiltRotation
  的 coerceIn），±7° 叠在 45° 上肉眼看不出，±20° 才跟手；>25° 亮边会跑到对侧像描边错位，故角度
  再 coerceIn(25f, 65f)。
- 角度在 `val highlight = { ... }` 的 draw lambda 内算（draw 阶段读 State 只重绘、不重组整卡）。
- 限制：backdrop 高光 shader 仅 SDK >= 33 返回；API 26–32 无高光层、角度无效（KDoc 已写）。

## 2. K1/M2 开关拖拽 + 按压（GlassToggle）
- GlassToggleMath.kt 三个纯函数：glassToggleTargetOffset / glassToggleDragOffset /
  glassToggleValueForOffset，Float px，NaN 与行程 <= 0 一律收敛到安全值。
- 松手：|velocity| >= 900px/s 按甩动方向（GLASS_TOGGLE_SETTLE_VELOCITY_PX_PER_SEC）；否则按位移比
  **严格 > 0.5** 翻面（正好 0.5 保持原状，避开阈值抖动）。
- 位移 Animatable + graphicsLayer.translationX（原 .offset 每帧触发布局）；拖动中直接写
  mutableFloatStateOf 不经协程；onDragStopped 先 snapTo 松手位置再 animateTo，否则从旧目标值回跳一帧。
- 拖动中轨道按 glassToggleValueForOffset(…, 0f) 即时预览颜色，松手前已能看到结果。
- 按压：thumb 放大 THUMB_PRESS_SCALE(1.08)，pressIn/pressOut；整块玻璃 pressTilt(maxRotation=3f,
  pressScale=1f)——30dp 高控件整体放大会"肿"，缩放只给圆钮。
- F14 触感放在控件内的 PressInteraction.Press（TextHandleMove），不进 pressTilt 的 hapticEnabled：
  后者有 18 个卡片调用点，改了会碰别人的文件。
- 旧 Dp 版 glassToggleThumbOffset 保留不动（GlassProgressBarTest 断言），语义与新纯函数一致。
- 可访问性：Role.Switch + toggleableState 保留；30dp 控件自身触控目标仍由 SettingsSwitchRow 整行兜住。

## 3. M3/K2 滑块按压与 thumb 材质（GlassSlider）
- 手势仍为裸 pointerInput（detectTapGestures + detectDragGestures），无 clickable，故自建
  interactionSource + 一层"只观察不消费"的 pointerInput：Initial pass 拿 down 发 Press，Final pass
  观察抬起或被父级滚动消费，发 Release / Cancel。放在两个手势前，取值数学零改动。
- thumb 放大 SLIDER_THUMB_PRESS_SCALE(1.10)；材质由"白芯 + 2dp 主题色环"改为
  surfaceContainerHighest + 0.5dp outline(α0.4)：白块在浅色玻璃上没有材质关系，2dp 色环是 M3 口径。
- 按压高光依赖 appleGlassCard 的 interactionSource 参数（只在收到 source 时把边缘高光 0.65→0.95），
  此前滑块完全没接，所以"按下玻璃亮一下"从未发生。
- 评分条不加触感：GlassRatingSlider 已有每刻度 TextHandleMove，重复会响两下。

## 4. 未验证 / 风险
- **未验证编译**：orSnap 缺 import 已补（GlassControls.kt:69），补完未再跑 gradle，等 Lead 统一编译。
- 未真机验证：900px/s 与 0.5 中线的手感、3° 倾角是否够看、1.08 放大在 30dp 控件上是否与轨道重叠。
- 每个开关多一层 pressTilt graphicsLayer；设置页同屏多个开关行时的合成开销需真机确认。
- API 26–32 无高光角度效果，视觉与 v1.2.1 一致（预期内）。
