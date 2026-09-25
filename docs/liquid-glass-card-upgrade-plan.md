# Niriko 卡片液态玻璃升级方案（基于 kyant/backdrop 实证）

> 范围：仅“卡片/浮层玻璃”的视觉效果升级。**不改代码**，先定方案。
> 参考项目：C:\what i download\AndroidLiquidGlass-kmp（io.github.kyant0:backdrop，Apache-2.0）。
> 对照现状：Niriko 使用 top.yukonga.miuix.kmp:miuix-blur-android:0.9.0 提供 drawBackdrop/layerBackdrop，
> 自实现 LiquidGlassSurface / GlassSectionCard / appleGlassCard。

---

## 一、结论先行

Niriko 的“真液态玻璃”（底栏、详情页 section 卡）已经具备 **AGSL 圆角折射 + 7 色彩带色散**，
且与 kyant 的 RoundedRectRefractionWithDispersionShaderString 同源（LiquidGlassShader.kt:176 已注明）。

**差距不在折射 shader，而在“一张玻璃面该有的完整表达”**：kyant 把一张 Liquid Glass 拆成
backdrop(模糊/折射)+highlight(发光边缘)+shadow(外投影)+innerShadow(内厚度)+surface(tint)，
并通过 layerBlock 把交互变换与 backdrop 捕获解耦。Niriko 目前：

- 列表卡 appleGlassCard：**纯静态“磨砂瓷”**，只有平坦渐变 + 0.75dp 细边，无发光边缘 / 无内厚度 / 无方向性外投影 → 看起来平、干、像贴纸。
- 浮层卡 GlassSectionCard / 底栏：用 miuix drawBackdrop 手动链 blur → lens，只做了折射 + 固定 saturation 1.2，**没有** kyant 的 vibrancy()、highlight、innerShadow、exportedBackdrop。

因此方案至少补三件套：**发光边缘 Highlight、玻璃内厚度 InnerShadow、朝向性外投影 Shadow + 增强的 vibrancy**，
并对有真实 backdrop 的浮层额外接上 **自适应亮度（adaptive luminance）**。

---

## 二、kyant 项目中可复用的关键模式（逐一对应到 Niriko）

### 1. 效果链 DSL（Blur → Lens → Vibrancy / ColorFilter）
- kyant：effects = { vibrancy(); blur(2.dp); lens(12.dp, 24.dp) }，BackdropEffectScope 串行 chain RenderEffect，并自动管理 padding（lens() 会把 padding -= refractionHeight，让折射采样越过圆角不裁切）。
- Niriko 现状：GlassSectionCard 手写 blurEffect + RuntimeShaderEffect 链、固定 uniform 节流（GLASS_UPDATE_INTERVAL_MS）。缺 vibrancy、缺 padding 语义。
- **可学**：把“vibrancy(饱和度 1.4–1.5) → blur → lens”作为默认浮层链；修 padding，避免边缘折射采样到卡片外空白。

### 2. Highlight（发光边缘）—— 这是卡片“不像贴纸”的关键
- kyant：Highlight(width, blurRadius, alpha, style)；HighlightStyle.Default/Ambient/Plain。
  HighlightNode 用离屏 GraphicsLayer 画一个 **模糊描边 outline + AGSL SDF 高光**（DefaultHighlightShaderString：
  gradSdRoundedRect 法线 · angle 方向的 pow(abs(dot(grad,normal)), falloff)），并 BlendMode.Plus 叠加在内容之上。
- Niriko 现状：列表卡 appleGlassCard 用 0.75dp 均匀 onSurface×0.06 细边 + 顶部线性高光；底栏用 1dp White 0.22 描边。
- **可学**：用 kyant 的 Highlight.Plain/Default 替代“一刀切描边”——发光沿圆角呈 directional falloff，且是 Plus 混合的“真发光”，而非 flat border。

### 3. Shadow / InnerShadow（厚度与层级）
- kyant：Shadow(radius, offset, color, alpha, blendMode) 用离屏层 + blur + Clear mask 画出**有方向、可调 alpha 的外投影**；InnerShadow 用 clipOutline + blur + Clear-mask 画出**卡内底部厚度**。
- Niriko 现状：appleGlassCard 只有单层 Modifier.shadow(4.dp)（接触阴影）；底栏有自写 InnerShadow 组件。
- **可学**：给卡片换“环境阴影(2dp/blur8) + 接触阴影(4dp/blur3)”的双层外投影，并加内厚度（尤其中上部一条下沉暗边），看起来“玻璃有厚度”。

### 4. Adaptive Luminance（自适应光泽）
- kyant：AdaptiveLuminanceGlassContent 持续把 backdrop 层 toImageBitmap().scale(5,5) 取平均亮度，
  驱动 colorControls(brightness/contrast/saturation)、动态 blur 半径、文字明暗，tween(1000) 平滑。
- Niriko 现状：壁纸层已有 rememberImageLuminance（p80），详情页/壁纸已做自适应 scrim；但**浮层卡/底栏的 tint/饱和度/白点是固定的**。
- **可学**：把壁纸亮度/局部 backdrop 亮度引入浮层玻璃 —— 亮 backdrop 时提高白点 + 降对比，暗时加深 tint，让玻璃“随环境呼吸”。

### 5. layerBlock（交互与 backdrop 解耦）
- kyant：drawBackdrop(layerBlock = { translationX/Y, scaleX/Y, rotationZ, transformOrigin })，变换只作用于单个 GraphicsLayer，不重新触发 backdrop 捕获/重算。
- Niriko 现状：卡片按压用外层 Box .scale，浮层卡无 layerBlock；底栏用独立的 graphicsLayer。
- **可学**：给可交互浮层（dock、sheet 把手、长按菜单）加 layerBlock 做按压 scale / 拖拽 tanh 位移（LiquidButton 的 tanh 阻尼），跳帧不重算玻璃。

### 6. exportedBackdrop（嵌套玻璃）
- kyant：drawBackdrop(exportedBackdrop = sheetBackdrop) 把本层渲染结果导出为另一个 backdrop，供内部控件（LiquidSlider）复用。
- Niriko 现状：无；收藏编辑 sheet 顶部把手玻璃化但内部控件无法引用该玻璃。
- **可学（增量）**：收藏编辑 sheet / 长按菜单把手区导出 backdrop，让内部滑块/分段控件继承“同一块玻璃”。

### 7. 色散/折射本身（已有，微调即可）
- kyant lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration) 与 Niriko LIQUID_LENS_SHADER 等价。
- 差异：kyant 用 size.minDimension 参与 refractionAmount，GlassPlaygroundContent 里 lens(24.dp, size.minDimension/2, depthEffect) 让折射随卡片尺寸自适应；Niriko 固定 12/16dp。
- **可学**：把浮层卡折射改为随卡片 minDimension 归一化（小卡片弱折射、大卡片强折射），更接近真玻璃。

---

## 三、给 Niriko 的两条路线

### 路线 A（推荐）：直接接入 kyant/backdrop 库，替换 miuix 玻璃路径
- 依赖：implementation("io.github.kyant0:backdrop:<latest>")（Maven Central）。
- 原因：Niriko 已在 GlassCard/LiquidGlassShader 注释里注明与 kyant/UISuki“同源”，直接用它拿全三件套 + DSL + adaptive luminance 示例，省去重复造轮子。miuix 与 kyant 是不同包，可共存；但同一文件需避免同名 drawBackdrop 冲突（按需 import 或起别名）。
- 改动文件范围：
  - GlassSectionCard.kt、NirikoBottomBar.kt、LiquidGlassSurface.kt 从 miuix drawBackdrop/layerBackdrop 迁到 kyant drawBackdrop/rememberLayerBackdrop，并把 effects 换成 blur+lens+vibrancy。
  - 新增 LiquidHighlight / LiquidShadow 封装（默认 Highlight.Plain、Highlight.Ambient、Shadow.Default）。
  - LiquidGlassCard.kt：卡片浮层用 Highlight + innerShadow + Shadow 包装。
  - 详情页 DetailDock、收藏编辑 sheet 把手：补 layerBlock 交互与 exportedBackdrop。
- 风险：需引入新依赖、迁移所有 miuix 调用点、验证 API 31/33 降级链。中等工作量，收益最大。

### 路线 B（保守）：仅把 kyant 的 Highlight/Shadow/InnerShadow 三件套“内化为 Niriko 组件”
- 保留 miuix drawBackdrop，只移植 kyant 三个 modifier（HighlightModifier、ShadowModifier、InnerShadowModifier）与对应 AGSL（Default/AmbientHighlightShaderString）和 SDF。
- 改动文件：新增 ui/components/glass/Highlight.kt、Shadow.kt、InnerShadow.kt；appleGlassCard 改用 Highlight.Plain + innerShadow 替代 flat 描边；GlassSectionCard/底栏在现有 miuix 链上叠加 Highlight、把 saturation 提到 1.4–1.5。
- 风险：低（无新核心依赖），但少了 kyant 的 exportedBackdrop / layerBlock / adaptive luminance DSL，需自己再写。

---

## 四、实施阶段（每阶段可独立验收）

### 阶段 1：静态卡“去贴纸感”（P1，低风险，先行）
- appleGlassCard 改造：去掉 flat 细边，改用 **Highlight.Plain/Default（发光边缘，Plus 混合）** + 底部 **InnerShadow（内厚度）** + 双层 **Shadow（方向性外投影）**；tint alpha 保持低（0.05）。
- 目标：列表卡在“零实时 RenderEffect”下先摆脱“贴纸观感”。
- 验收：同帧截图对比，卡片边缘有明显柔和发光、底部有厚度下沉，不再是均匀灰线。

### 阶段 2：浮层卡/底栏增强（P1，中风险）
- GlassSectionCard / NirikoBottomBar：
  - effects 改 vibrancy() + blur(2dp) + lens(随 minDimension 归一化)，并修正 padding（折射越过边缘）。
  - saturation 从 1.2 提到 1.4–1.5。
  - 叠加 Highlight.Plain + innerShadow。
- 目标：浮层玻璃“更透更亮、折射更自然、边缘发光”。
- 验收：详情页/底栏卡片边缘有发光 rim，折射采样不露白边。

### 阶段 3：自适应亮度（P2，中风险）
- 复用 rememberImageLuminance（壁纸 p80）与 kyant 的 backdrop-toImageBitmap 思路：
  - 浮层卡根据卡片后方 backdrop 平均亮度驱动 colorControls(brightness/contrast)、白点、文字明暗（tween 平滑）。
  - 亮背景：提白点、降对比、稍稍增加 blur；暗背景：压 tint、提对比。
- 目标：玻璃“随环境呼吸”，且白字壁纸不再穿透。
- 验收：在亮/暗两种壁纸下切换，浮层卡明暗自动过渡（对比上一版固定 tint）。

### 阶段 4：交互与嵌套玻璃（P3，增量）
- DetailDock、收藏编辑 sheet 把手、长按菜单：加 layerBlock（按压 scale + tanh 拖拽位移）与 exportedBackdrop。
- 目标：交互在玻璃上“动”而不重算 backdrop；sheet 内部控件继承同一块玻璃 backdrop。
- 验收：按压卡片玻璃不闪、不重算；sheet 把手区玻璃与内部控件光泽一致。

---

## 五、明确不做（与既有性能约束一致）

- ❌ 不做“每张列表卡实时 backdrop blur + lens”：列表卡仍保持静态材质，仅升级高光/阴影/内厚度（L0/L1 逻辑）。真玻璃只给同屏 ≤2 张浮层（底栏、详情 dock、sheet 把手）。
- ❌ 不做 3D 跟手倾斜（rotX/rotY）到列表小卡：保留在详情页大封面做 ±2° 视差的选项。
- ❌ 不做滚动联动 blur 半径动画（RenderEffect 每帧重建会掉帧）。

---

## 六、验收基线

- 同屏实时 RenderEffect ≤ 2（底栏 + 详情 dock）。
- 中端机列表滚动 P95 帧时长 ≤ 12ms（现有性能注释基线不破）。
- 换 6 个预置主题色，卡片玻璃边缘发光颜色与主题协调（Highlight 白/theme 色皆可）。
- 任意页面截图卡片区域背景文字不可辨认（壁纸消化后再叠浮层玻璃）。

---

## 七、结论 / 下一步

建议走 **路线 A（直接接入 kyant/backdrop）**，因为它就是用户找到的这个项目，
能一次拿到 lens/vibrancy/highlight/shadow/innerShadow/exportedBackdrop/layerBlock/adaptive-luminance 全套。
若担心依赖体积或迁移量，则退到 **路线 B**：只内化 Highlight/Shadow/InnerShadow（最关键的三件套），
其余（exportedBackdrop、adaptive luminance DSL、layerBlock）在 Niriko 自行补。

下文实现前，请先确认两点：
1. 是否接受引入 io.github.kyant0:backdrop 新依赖（路线 A），还是坚持零新依赖（路线 B）？
2. 本轮先落地“列表卡静态材质去贴纸感（阶段 1）”，还是直接做“浮层卡 + 自适应亮度（阶段 2+3）”？
