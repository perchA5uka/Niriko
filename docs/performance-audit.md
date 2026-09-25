# Niriko 性能体检报告（静态分析 + 真机测量清单）

> 现状：开发环境**无模拟器**，改用「静态代码审查 + 真机可执行测量清单」定位卡顿。
> 结论摘要：核心损耗集中在 **① 玻璃 backdrop 捕获 + 模糊（详情页/底部栏）** 与 **② 封面双重解码（网格卡）**。建议按 P0 / P1 / P2 落地。
>
> ⚠️ **明确豁免**：详情页作品封面大图（`SubjectDetailScreen` hero cover，第 632 行，1080×1440 解码）**保持高清**，不纳入「不大图」。其余列表 / 网格 / 人物封面执行「不大图」（`CoverImage` 常规 cap 800×1000；`CoverThumbnail` / `PosterGridCard` 用 requestWidth 精确解码）。

---

## 一、测量方式（因无模拟器）

真机（USB 调试 + `adb`）可直接执行：

- 重置计数：`adb shell dumpsys gfxinfo com.otakup.niriko reset`
- 流畅滚动一段后看 Janky frames / 95th percentile：`adb shell dumpsys gfxinfo com.otakup.niriko`
- 单帧耗时：`adb shell dumpsys gfxinfo com.otakup.niriko framestats`
- Android Studio Profiler（CPU / GPU / Energy）录 10s；Layout Inspector 看 Recomposition 火警。

> 无模拟器时先做静态定位，后续用真机复核上面命令确认。

---

## 二、高危点（按优先级）

### P0 · 详情页整页玻璃 backdrop + 模糊（单页最大开销）
- 位置：`ui/subject/SubjectDetailScreen.kt`
  - 背景墙：`AsyncImage` size 360×480 + `RenderEffect.createBlurEffect(30dp)` + `Modifier.layerBackdrop(glassBackdrop)`（352–374 行）。
  - `glassBackdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }`（352–357 行）：整页内容每帧被捕获，供各 `GlassSectionCard` 折射。
- **风险**：整页 `drawContent` 捕获 + 全屏 blur + 背景墙随滚动，叠加 hero 1080px 大图与共享元素过渡 = 详情页卡顿主因。
- **建议**：
  - `GlassSectionCard` 的 `layerBackdrop` 仅在可见 / 滚动静止时启用；用 `snapshotFlow { isScrollInProgress }` 临时关 blur。
  - 背景墙 blur 改为仅静止时施加；`rememberLayerBackdrop` 捕获范围尽量收窄（卡片局部而非整页）。

### P0 · 底部胶囊 drawBackdrop 每帧捕获全页 + AGSL lens
- 位置：`navigation/NirikoBottomBar.kt`（155–190 行）。
- 现状：chain 已按尺寸缓存（165–181 行），但 `drawBackdrop` 本身每帧需捕获底部页面层；叠加视频壁纸时更重。
- **建议**：保持 chain 缓存；仅当 Pager 滚动 / 内容变化时更新 backdrop；低端机用静态 `colorControls` 降级（关闭 AGSL lens，仅 tint）；提供「玻璃强度」设置。

### P1 · PosterGridCard / CoverImage 封面双重解码
- 位置：`ui/library/PosterGridCard.kt`（58–68 行）+ `ui/components/CoverImage.kt`（88–123 行）。
- 现状：`PosterGridCard` 传 `fillMaxWidth()` 但不传 `requestWidth/Height` → `CoverImage` 走 `onSizeChanged` 路径：默认先按 600×800 解码一次，再按实际 cell（×2、cap 800×1000）二次解码。
- **影响**：3 列网格大量卡片 → 每卡 2 次解码 + 重复 Bitmap 分配 = 内存 / GC / 掉帧。
- **建议**：给 `PosterGridCard` 传 `requestWidth/requestHeight`（用已知 cell 宽 × 2 · cap 800，与 `CoverThumbnail` 一致）；或 `CoverImage` 在已知宽高比 + 宽度时直接用 `Modifier.size` 推算。

### P1 · 逐项 Coil 请求（rememberCoverTint / loadBlurredCover）
- 位置：`ui/components/GlassCard.kt` `rememberCoverTint`（314–339 行）；`ui/components/liquidglass/BlurredCover.kt` `loadBlurredCover`。
- 现状：封面取色走 `LaunchedEffect(url)` + 进程级 `coverTintCache`（304 行）。已缓存，但首次进入大列表仍会逐项解码 32px 图；详情页 `loadBlurredCover` 每次打开 `LaunchedEffect` 加载模糊位图。
- **建议**：`rememberCoverTint` 仅对可见项触发；`loadBlurredCover` 用 `remember(url)` + 进程缓存，且仅在实际 `GlassSectionCard` 可见时加载。

### P1 · RevealOnScroll 滚动重播
- 位置：`ui/search/TrendingSection.kt`（102 行 `RevealOnScroll(staggerIndex = index % 5)`）+ `ui/animation/RevealOnScroll.kt`。
- 现状：Lazy 项离屏销毁 → 滚回重组 → 每次重播 300ms alpha+scale；搜索趋势区快速滚动时多个项同时动画争帧。
- **建议**：趋势区只对首屏项动画，滚回不重播（`rememberSaveable` 或改一次性 `LaunchedEffect` 内 `snapshotFlow`）；或把 `entered` 状态离屏后缓存。

### P2 · CollectionCard 每重组重建 DisplayModel
- 位置：`ui/library/CollectionCard.kt`（41 行 `subject.toCardDisplayModel(steam)`）。
- 影响：每次 item 重组（滚动 / 状态更新 / `animateContentSize`）都重建 model。
- **建议**：`remember(subject, steam) { subject.toCardDisplayModel(steam) }`。

### P2 · 统计 / 趋势计算未缓存
- 位置：`viewmodel/StatsViewModel.kt`（185 / 205 行）+ `viewmodel/SubjectSearchViewModel.kt`（229 行）。
- **建议**：把 `StatsCalculator.computeStats` / `computeCalendarEvents`、`TrendingCalculator.computeSeasonDateRange` 的结果按「数据版本 + 区间」写入 Room/DataStore 缓存，进页面直接读缓存。

### P2 · 低端机特效过重
- 视频壁纸（`ui/wallpaper/WallpaperHost.kt` `VideoWallpaper`）+ 底部 blur + 详情页 blur 三重叠加。
- **建议**：设置「性能模式 / 降低特效」：低端机自动关视频壁纸、降 blur 半径、关闭 AGSL lens。

---

## 三、建议改动优先级

1. **P0**：详情页 backdrop 仅可见 / 静止时启用；底栏大页面 blur 加降级开关并提供「玻璃强度」设置。
2. **P1**：`PosterGridCard` 传 `requestWidth`；`rememberCoverTint` 仅可见项；`RevealOnScroll` 趋势区不重播。
3. **P2**：`CollectionCard` 用 `remember`；`Stats/Trending` 结果缓存；低端机性能模式。

---

## 四、真机测量命令（无模拟器时用）

`adb shell dumpsys gfxinfo com.otakup.niriko reset`
重置后滚动收藏页 / 详情页，再：
`adb shell dumpsys gfxinfo com.otakup.niriko`   # 看 Janky frames / 95th percentile
`adb shell dumpsys gfxinfo com.otakup.niriko framestats`  # 单帧耗时

Android Studio Profiler → CPU / GPU / Energy 录 10s；Layout Inspector 看 Recomposition 火警。

> 注：以上为**静态审查结论**，落地前应在真机复测上述指标，P0 改动（blur/backdrop）尤其要 A/B 验证观感与帧率。
