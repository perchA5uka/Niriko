# Niriko UI 重设计方案

> 基于代码实证（非仅凭截图观感）。本文档先做诊断，再给设计总则、分系统方案、实施路线，最后附与上一版方案的对照。

---

## 〇、实证诊断：问题的真正根源

### 1. 「脏玻璃」不是玻璃不够强，而是**根本没有玻璃**

代码证据：

- `ui/components/GlassCard.kt:76` `appleGlassCard`：列表卡的"玻璃"是**纯静态绘制**——75% 不透明的 `#1C1C1E` 平涂 + 渐变高光 + 0.75dp 描边，**没有任何 backdrop blur**。
- `ui/subject/UniversalSubjectCard.kt:84-87` 注释明确写道："静态玻璃卡片……零 GPU 滤镜"。
- `ui/wallpaper/WallpaperHost.kt:92-99`：壁纸只做了一层**平铺遮罩**（深色黑 0.62 / 浅色白 0.72），没有模糊、没有降饱和。

因果链：壁纸被压暗 62% → 卡片再透 25% → 壁纸上巨大的白色标题文字（君の名は）以约 10% 的有效对比度穿透卡片 → "鬼影文字"。这不是"毛玻璃脏"，是**两层半透明叠加后的残留高频信息**。

> 推论：上一版方案"每张卡 blur 40dp + 动态厚度"在 LazyColumn 里意味着十几个实时高斯并发，GPU 必过载——项目自己在 `UniversalSubjectCard` 注释里已经否决过这条路。正确解法不是"卡片加模糊"，而是**在壁纸层一次性消化掉高频信息**（见 §2）。

### 2. 色彩失控的根源是**硬编码绕开主题系统**，不是"没取壁纸色"

代码证据：

- `ui/stats/StatsScreen.kt:85-105`：状态五色、类型七色、柱状图绿/蓝全部 `Color(0xFF…)` 字面量，与 `MaterialTheme.colorScheme` 无关——用户在设置里换任何主题色，图表永远是那组绿蓝。
- `ui/stats/StatsOverviewSection.kt:71-76`："看过/在看/想看/搁置"四张状态卡是**浅粉彩硬编码**（`0xFFD9F2E1` 等），在深色模式 + 深色壁纸上像四块浅色补丁——这是全部截图里最刺眼的违和，上一版方案完全没有发现。
- 同文件 `:47-50`：用 emoji（📚✓▶🔖⏸）当图标，与全站 Material Icons 体系不一致，且各设备渲染不一。

> 推论：上一版"从壁纸提取强调色"会引入新问题——壁纸是可换的装饰品，把品牌色锚定在装饰品上会让 UI 每次换壁纸都"变脸"，且与用户的主题色选择（设置里已有 6 色 + 自定义种子 + 动态取色）打架。正确方向：**图表色从主题色派生（HCT 类比色板），壁纸只影响环境层（遮罩/tint），封面色只在详情页限域生效。**

### 3. 图表没有容器

截图 `17-22-40`：环形图、进度条、两组柱状图**直接画在壁纸上**，壁纸文字从图表刻度后面穿过。`StatsScreen` 的图表区块不像 `StatsOverviewSection` 那样有卡片容器。

### 4. 排版扁平

`ui/theme/Type.kt` 只覆写了 4 个样式。截图可见：列表卡的类型行/标题/原名/简介/我的评分几乎同一字重（全 Bold），缺少"编辑感"层级；统计大数字与标签同级；数字没有 tabular figures，滚动刷新时会跳动。

### 5. 其余实证问题

- 作品库顶部统计条 `180 部作品 均分 6.3 累计 5106 集 完成率 68% 看过 1…`——**最右端被屏幕裁切**（截图 1）。
- 详情页底部"★ 想看 · 未评分"是实心品牌绿大横条（截图 `17-15-47`），与封面色调完全无关，且视觉上压住了整页。
- 标签 chip 是 hairline 描边 + 透明底，在封面模糊背景上可读性差（截图 `17-15-47`）。
- 设置页图标是实心绿圆（截图 `17-22-42`），在壁纸上显得"贴纸感"。
- 画廊模式的状态 chip 直接压在封面文字上（截图 `17-22-23`，「事件」二字与 chip 重叠）。

---

## 一、设计总则：三层材质学（Glass is a privilege, not a skin）

与上一版"万物皆玻璃"相反。iOS 26 / visionOS 的真实做法是：**内容层实、浮层玻璃**。一屏内玻璃面越少，玻璃越显高级；玻璃泛滥 = 每个面都在折射 = 视觉噪音 + GPU 过载。

| 层级 | 材质 | 用于 | 规则 |
| --- | --- | --- | --- |
| L0 环境层 | 消化后的壁纸（§2） | 四顶层页背景 | 只出现在页边距、卡片间隙、区块间距——**永远不出现在文字正后方** |
| L1 内容层 | 「磨砂瓷」：高不透明 tonal 面（92–100%）+ 1px 顶部高光 + 双层阴影 | 列表卡、图表容器、设置组卡、统计卡 | 承载一切阅读内容；深色模式比背景**亮一档**（M3 elevation 逻辑），而不是"透一档" |
| L2 浮层 | 真液态玻璃（共享 backdrop 捕获 + AGSL 折射，现有 `drawBackdrop` 管线） | 底栏胶囊、搜索建议浮层、详情页底部状态 dock、长按菜单、收藏编辑 sheet 把手区 | **同屏 ≤ 2 个**；降级链已有（`LocalGlassEffect` FULL/REDUCED/OFF） |

一条判别规则：**这个面背后有没有"值得透出的内容"？** 底栏背后有滚动的封面流 → 玻璃；列表卡背后是壁纸文字 → 瓷。

---

## 二、壁纸消化管线（Wallpaper Treatment Pipeline）

现状 `WallpaperHost`：壁纸 →（可选 blur）→ 平铺 0.62 黑纱。改为四级处理，**全部发生在壁纸层一次**，其下游所有表面（含底栏折射采样的共享 backdrop）自动受益：

```
壁纸原图
 → ① 色彩消化：饱和度 ×0.75、亮度 −8%（AGSL ColorFilter，API 31+；低版本用 scrim 补偿）
 → ② 高斯柔化：保留现有 0–40dp 设置
 → ③ 自适应 scrim：采样壁纸 32px 缩略图算平均亮度 L，
       scrimAlpha = lerp(0.45, 0.72, L)（亮壁纸加深、暗壁纸减轻，告别一刀切 0.62）
 → ④ 结构化渐变：顶部状态栏区 24dp 渐变加深、底部底栏区 96dp 渐变加深、四角 5% 径向暗角
```

要点：

- 壁纸上的大字之所以穿透，是因为**亮度差**没有被消除；模糊不能消灭低频大块白色（君の名は的标题字就是低频），只有压暗+降饱和能消灭它。这是与"加 blur"思路的本质区别。
- 设置项升级：「壁纸柔化」保留；新增「壁纸氛围」三档（浓郁 / 均衡 / 素净）控制 ①③ 的强度。
- 视频壁纸同理（ExoPlayer 上叠 scrim 层即可，①用 `PlayerView` 上层 ColorFilter 近似）。

## 三、玻璃分级落地

### L1「磨砂瓷」卡片（改造 `appleGlassCard`）

- 底色：`surfaceContainer`（深色 92% 不透明，不是现在的 75% `#1C1C1E`）；OLED 模式下用 `surfaceContainerLow`。
- 顶部 1px 高光 `white × 0.06`、底部 1px 沉降 `black × 0.10`（现有 highlight 渐变保留但收敛）。
- 描边从对角渐变（0.8 alpha 白，过亮）收敛为 `onSurface × 0.06` 均匀细边。
- 阴影改双层：环境 `elevation 2dp / blur 8dp` + 接触 `elevation 4dp / blur 3dp`；按压时阴影收缩（见 §6）。
- 封面 Ambient Tint（`rememberCoverTint` 已有进程级缓存）保留，但 alpha 从 0.09 降到 0.05 并限制在卡片上 1/3 区域——"余光"，不是染色。

### L2 浮层真玻璃（复用现有管线，零新依赖）

- 底栏胶囊：保持现状（已是 SukiSU 同款折射），仅把 tint 从 `surfaceContainer 0.5` 微调，并加滚动联动（§6）。
- 详情页底部状态 dock：**从实心 primary 大横条改为浮层玻璃胶囊**（复用 `drawBackdrop` + `LIQUID_LENS_SHADER`），宽度收缩为内容宽、悬浮 16dp，内嵌「状态 · 评分 · 进度」三件套。这是全 app 视觉收益最大的单点改动。
- 收藏编辑 sheet：内容区保持不透明 sheet（可读性），仅顶部把手区玻璃化；`WindowBlurBehindEffect` 已有，保持。

### L0 容器化补漏

- `StatsScreen` 的环形图、类型分布、评分分布、Bangumi 评分、年度总结、词云——全部包进 L1 容器卡（圆角 20dp，内边距 20dp），图表不再直接接触壁纸。

## 四、色彩秩序

### 4.1 状态语义色 token 化

新建 `ui/theme/StatusColors.kt`：

```kotlin
data class StatusTone(val container: Color, val onContainer: Color, val accent: Color)
// 看过=绿系 / 在看=橙系 / 想看=蓝灰系 / 搁置=紫系 / 抛弃=红系，light/dark 两套 tonal 值
```

替换全部硬编码：`StatsScreen.kt:85-89`、`StatsOverviewSection.kt:71-76`、网格卡状态 chip、收藏编辑 sheet 状态页签。同色相、全站一致。

### 4.2 图表色板从主题派生

新建 `ui/theme/ChartPalette.kt`：用项目已有的 material-color-utilities HCT，从 `colorScheme.primary` 生成 6 色类比板（hue −40/−20/0/+20/+40/+180，chroma 收敛到 48–64，tone 深色模式 70–80）。`StatsScreen.kt:94-105` 的字面量全部替换。效果：**用户换主题色，图表自动协调**；绿色图表 vs 深蓝壁纸的问题在根上消失（因为图表色从此属于主题系统，而不是游离字面量）。

### 4.3 pastel 状态卡 → tonal 深色卡

`StatsOverviewSection` 四张卡：背景 = `statusTone.accent × 0.14` 叠在 `surfaceContainer` 上，数字用 `statusTone.accent`（深色取 tone 80），标签 `onSurfaceVariant`。浅粉彩补丁消失。

### 4.4 emoji 图标 → Material Symbols

`StatsOverviewSection.kt:47-50` 的 📚✓▶🔖⏸ 换为 Material Icons（项目已依赖 material-icons-extended），tint 用对应 statusTone。

### 4.5 封面色：限域使用（与上一版"全局取色"的关键分歧）

`rememberCoverTint` 升级为 `rememberCoverPalette`（dominant + vibrant，复用现有 32px 采样与进程缓存），**只在详情页生效**：dock tint、评分星、进度条。全 app 主题色保持用户选择不动。理由：壁纸/封面是内容，主题色是界面；内容可以影响局部氛围，不能劫持界面身份。

## 五、排版系统

扩展 `Type.kt`（现仅 4 个覆写）：

| 用途 | 样式 | 规格 |
| --- | --- | --- |
| 统计大数字 | `displayLarge` | 56sp / w700 / **tnum** / −0.5sp 字距 |
| 页面大标题 | `headlineMedium` | 28sp / w600 |
| 区块标题 | `titleMedium` | 16sp / w600 |
| 列表卡标题 | `titleSmall` | 15sp / w600 |
| 卡片段标题/原名 | `bodyMedium` | 14sp / w400 / onSurfaceVariant |
| 简介 | `bodySmall` | 13sp / w400 / onSurfaceVariant / maxLines 2 |
| 元信息（类型·评分·人数） | `labelSmall` | 11.5sp / w500 / tertiary 色 |

所有统计数字启用 tabular figures（`FontFeature("tnum")`），滚动刷新不再跳动。列表卡（`UniversalSubjectCard`）按上表重排五行信息层级：现在全 Bold 的"喊叫感"消失。

## 六、动效系统

保留现有弹簧规范（`NirikoAnimation.kt`：入场 0.85 / 按压 0.75——已是正确基准），增量：

1. **按压下沉**：卡片 press 时 scale 0.97（已有）+ 阴影 elevation 10→4 同步收缩——"按进玻璃里"。
2. **底栏滚动联动**：列表向下滚 → 底栏 translateY 8dp + alpha 0.85（让出内容）；停止/上滚回弹。用 velocity 阈值 800px/s 触发，不走 RenderEffect。
3. **页面过渡**：二级页从"右侧滑入"改为 M3 SharedAxis Z（fade + scale 0.92→1），与封面共享元素过渡（已有）叠加更自然。
4. **数字滚动**：统计页大数字用现有 `countTween()` 做计数动画。
5. **评分交互**：滑块改"星条 + 数字弹簧"——拖动时经过的星依次点亮（stagger 30ms），松手数字 1.15→1 弹回。
6. **选集 chip**：选中态 spring 填充 + 边框 1dp→1.5dp。

**明确不做**（与上一版分歧，附理由）：

- ❌ 滚动速度联动 blur 半径：RenderEffect 重建 = 每帧 GPU 分配；模糊半径动画必掉帧。且"玻璃变薄"在弱视下是闪烁。
- ❌ 3D 倾斜卡片（rotationX/Y 跟手）：网格小卡片上视觉噪音大、与按压手势冲突；如需要，仅在详情页封面做 ±2° 视差。
- ❌ overscroll 橡皮筋自定义：Android 12+ 系统 stretch 已足够，自绘会与 Pager 手势打架。

## 七、逐页面改造清单

### 作品库
- 顶部统计条修复溢出：改为**可横滑胶囊行**（`180 收藏` `均分 6.3` `5106 集` `68% 完成`…chip 化，labelSmall + tnum 数字），或收成两行网格。
- 搜索条升级 L2 玻璃。
- 海报网格：状态/评分 chip 统一为 85% 不透明 `surfaceContainerHighest` + 1px 边（不再用半透明绿/黄直接压封面）；红心改 tonal。
- 画廊模式：标题区加底部渐变遮罩（透明→black 60%，高 72dp），chip 不再压封面文字。

### 发现
- 「当季热门·历史排名·Steam」分段控件药丸化（选中 tonal 填充 + spring 指示器），替代现在的纯文本「·」分隔。
- 卡片与库统一 L1。

### 详情
- **封面头部保持现状**：全宽大图卡 + 标题/元信息排列在图下方 + 模糊封面铺底背景，均不改动（用户明确保留原版形式）。
- 各区块（评分分布/角色/制作/标签/取景地标/猜你喜欢）统一 L1 容器，圆角/间距 token 化（20dp / 16dp）。
- 标签 chip：hairline 描边 → `secondaryContainer` tonal 填充。
- 底部 dock：实心绿横条 → L2 玻璃胶囊（§3）。
- 取景地标缩略图的字幕（`上学道 EP1·00:03·#1`）加底部渐变保护。

### 统计
- 全部图表容器化（§3 L0）。
- 「180 总计」→ displayLarge + 中心标签缩小；环形图描边端圆角、间隙 2dp。
- 状态卡 tonal 化（§4.3）、emoji → 图标（§4.4）。
- 日历格子的封面马赛克保持，图例行（●开始 ○完成 ◆放送 ▲发售）收进卡片底部并压缩字号。

### 设置
- 分组大卡化：每组一张 L1 卡（圆角 20dp），组内 hairline divider（inset 56dp），组间距 24dp；组标题 labelLarge primary 色。
- 行图标：实心绿圆 → 12dp 圆角 tonal 方块（`primaryContainer` / `onPrimaryContainer`）。
- 行高 56dp、副标题 bodySmall variant 色。

### 收藏编辑 sheet
- 状态选择改分段控件（选中 spring 填充）。
- 评分星条（§6.5）；选集 chip（§6.6）。
- 「保存记录」保持实心 primary（表单主按钮用实心是对的——玻璃不做主按钮），但宽度收缩并上移 8dp 脱离底边。

## 八、实施路线（按依赖与收益排序）

| 阶段 | 内容 | 主要文件 | 风险 |
| --- | --- | --- | --- |
| P0a 色彩秩序 | StatusColors / ChartPalette token 化；清除统计页全部字面量；pastel 卡 tonal 化；emoji→图标 | `ui/theme/StatusColors.kt`(新)、`ChartPalette.kt`(新)、`StatsScreen.kt`、`StatsOverviewSection.kt` | 低，纯换色 |
| P0b 壁纸消化管线 | §2 四级处理 + 自适应 scrim + 设置项 | `WallpaperHost.kt`、设置存储 | 中（需验证 layerBackdrop 捕获顺序含 scrim） |
| P1 玻璃分级 | `appleGlassCard` 改磨砂瓷；图表容器化；详情 dock 玻璃化；标签 chip tonal 化 | `GlassCard.kt`、`StatsScreen.kt`、`SubjectDetailScreen.kt`、`NirikoBottomBar.kt`(复用) | 中 |
| P2 排版+逐页 | Type.kt 扩展；库顶栏/网格 chip/发现分段/设置组卡 | `Type.kt`、`Screens.kt`、`UniversalSubjectCard.kt`、`SettingsScreen.kt` 等 | 低 |
| P3 动效 | 按压阴影、底栏联动、SharedAxis、星条评分 | `NirikoAnimation.kt`、各交互组件 | 低 |

每阶段独立可交付、可回滚；P0a+P0b 即可完成"观感翻身"的 80%。

## 九、验收标准

- 正文文字对比度 ≥ 4.5:1、大标题 ≥ 3:1（对消化后壁纸实测，君の名は白字壁纸为用例）。
- 任意页面截图中，壁纸文字在卡片区域内不可辨认（目测鬼影消除）。
- 换 6 个预置主题色，统计页图表色彩协调（无色相冲突）。
- 性能：同屏实时 RenderEffect ≤ 2；中端机列表滚动 P95 帧时长 ≤ 12ms（现有性能注释基线不破）。

---

## 附：与上一版方案的对照

| 上一版主张 | 本方案态度 | 理由 |
| --- | --- | --- |
| 卡片分层 blur 40dp + 动态厚度 | **反对** | 列表卡实时 blur = GPU 过载，项目注释已否决；根源在壁纸未消化，不在卡片模糊不足 |
| 壁纸取色决定主题色/图表色 | **修正** | 改为主题色 HCT 派生图表板；壁纸只影响环境层；封面色限详情页 |
| 边缘高光/厚度感 | **采纳并收敛** | 保留 1px 高光但 alpha 砍半；不做"厚度动画" |
| 设置页组卡化、图标背景统一 | **采纳** | 与 §7 设置一致；图标用 tonal 方块而非"壁纸取色暗色" |
| 统计数字杂志化、详情封面沉浸 | **部分采纳** | 数字杂志化采纳（§5）；封面沉浸头经原型评审后**放弃**，封面头部保持原版布局（全宽大图卡+信息在下方+模糊铺底不变） |
| Spring 动效、按压、sheet | **采纳** | 项目已有弹簧规范，做增量 |
| 3D 倾斜、滚动联动 blur、橡皮筋 | **反对** | 性能/噪音/手势冲突，§6 逐条说明 |
| （未发现）pastel 卡、emoji 图标、图表无容器、统计条溢出、dock 实心绿 | **本方案新增** | 实证诊断 §0.2/§0.3/§0.5 |
