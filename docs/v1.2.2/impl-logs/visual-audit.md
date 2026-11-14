# task-11 实施日志 · 统计数字三档 + 放送日历分段（visual-audit）

## 1. 原字号 → 新档位映射表

| 位置（file:line） | 改造前 | 改造后 | 判断理由 |
| --- | --- | --- | --- |
| `ui/stats/StatsOverviewSection.kt:88-96` | `headlineLarge`(32) + Bold | `headlineSmall`(24/w600) | 收藏总数是主指标，但 32sp 压过页面标题；五档里最大那档归 24 |
| `ui/stats/StatsOverviewSection.kt:165-172` | `titleLarge`(22) + Bold | `headlineSmall`(24/w600) | 均分同为「主指标」，与总数同值不同位才需分层，此处同层 |
| `ui/stats/StatsOverviewSection.kt:180-190` | `titleLarge`(22) + Bold | `headlineSmall`(24/w600) | 完成率同上；22/24 只差 2sp 没必要并存 |
| `ui/stats/StatsOverviewSection.kt:279-284` | `fontSize = 26.sp` + Bold | `style = headlineSmall` | 26sp 是手写第六档；语义色（暗取 onContainer / 浅取 accent）保留 |
| `ui/subject/DetailStatCard.kt:133-138` | `headlineMedium`(28) 再被 `fontSize = 20.sp` 覆写 | `headlineSmall`(24/w600) | token 与覆盖互相打架，等于实际 20sp 却让人以为 28sp |
| `ui/subject/DetailStatCard.kt:139-143` | `labelSmall` + `fontSize = 10.sp` | `labelSmall`(11.5) | 标签是注释档，10sp 低于正文下限（V-03） |
| `ui/subject/RatingComparisonSection.kt:80/94/112` | `headlineSmall` + Bold | `headlineSmall`(w600) | 三处数值同档，但 Bold 让它们比同页其它 24sp 更粗，属「手写档」；语义色 primary/secondary/tertiary 保留 |
| `ui/stats/CalendarCard.kt:465-488` | M3 `FilterChip` + `horizontalScroll` | `NirikoSegmentedControl`（3 段等宽） | 「一组里选一个」在全站有 4 副面孔（V-11），这里收口到唯一分段控件；3 项也无需横滑 |
| `ui/stats/CalendarCard.kt:479-481` | M3 `FilterChip`（周/月） | `NirikoFilterPill` | 周/月是两个独立开关，用可点胶囊；若塞进分段容器会读成一个四选一 |
| `ui/stats/CalendarCard.kt:504-535` | 图例 5 处 `fontSize = 9.sp` 覆盖 | 删覆盖 → `labelSmall`(11.5) | 9sp 正文越界；图例行有余量，不需要压字 |
| `ui/stats/CalendarCard.kt:552-575` | 停播/延期提示 2 处 `fontSize = 9.sp` | `labelSmall`(11.5) | 同上；提示行是可读句子，不是压图徽标 |
| `ui/stats/CalendarCard.kt:576-585` | 自绘 48dp 玻璃片（GlassChipTokens 手画） | `NirikoActionPill` + `heightIn(48)` | 上一批已定为 48dp 胶囊（保留现状），这里改为共享组件，不再各画一份 |
| `ui/stats/CalendarCard.kt:589-607` | 9sp 裸绿文字（命中区约 20×14dp） | `NirikoActionPill` + `heightIn(48)` | 裸文字看不出可点；与「刷新」同族 |
| `ui/stats/CalendarCard.kt:610-633` | 对话框内 3 个 M3 `FilterChip` | `NirikoFilterPill` | 弹层里不该再长第 4 副 chip 面孔（V-12） |

## 2. 为什么一起改

同一屏 32/28/26/22/20 五档数字，用户读不出「哪个是主指标」；同一语义（多选一）有 FilterChip / 实心主色胶囊 / 玻璃 toggle 四种画法，同一行还混着「裸绿字 = 可点」的旧习惯。字号与控件各自收口后，统计页只留一条阅读线：**24 主指标 → 13 标签 → 11.5 注释**。

## 3. 保留了什么（刻意不动）

- `ui/stats/StatsScreen.kt` **零改动**：页面大标题「收藏统计」保持 `headlineMedium` 28/w700——页面标题不是统计数字。
- 年度总结四联数字（`YearlyStatItem` 的 `titleMedium`+Bold、「2026 年」`titleLarge`）未并入三档：属年度汇总块，不在 lead 点名位置，改了会动一整块的排版节奏。
- 日历日格内压图小字保留原值：`:990` 续 8sp、`:999` 日期 10/9sp、`:1015` 停播/延期角标 8sp、`:1148` `+N` 7sp。45–50dp 格宽里 11.5sp 会溢出，属于「压图徽标」例外。
- `RatingComparisonSection` 的「评分分布」canvas 轴标签 8sp 未动（Canvas 里 `Paint.textSize`，不是 Compose Text，改字号要重算布局）；该卡也**未**改 `GlassSectionCard`（D1 已否决新增实时折射）。
- 材质分层、色板、`statusTone` 语义色、`CalendarAccents` 全部未动。

## 4. 未验证项（需 lead 编译 + 真机）

1. **未编译**（按要求不跑 gradle）；改动的 4 个文件已做括号平衡与 import 自检，被删符号（`FilterChip`/`horizontalScroll`/`GlassChipTokens`/`LocalDarkTheme`/`LocalGlassEffect`/`LocalCardGlassLevel`）已无代码引用。
2. 三段等宽中文截断：估算段宽 ≈ 90dp，最长「个人记录」4 字@13sp ≈ 52dp + 段内 padding 12dp = 64dp，理论放得下，但未真机确认。
3. 视觉高度差：分段容器 48dp（40 段高 + 8 容器内距）与周/月 `NirikoFilterPill` 34dp 并排，垂直居中后差 14dp；若观感突兀，最小修法是给两枚胶囊外包 `Box(Modifier.heightIn(48.dp), contentAlignment = Alignment.Center)`（只对齐、不扩命中区）。
4. `NirikoActionPill` 被调用点硬压到 48dp 后文字靠 Row 的 `CenterVertically` 居中（不会顶到上沿），但会比其它页面 34dp 的动作胶囊高 14dp——同一组件两种高度；若不可接受，建议组件加 `height` 参数而非调用点压。
5. **组件层 a11y 缺口（归 lead）**：`NirikoFilterPill` 34dp / `NirikoTagPill` 28dp 命中区 < 48dp，且未加 `minimumInteractiveComponentSize`；这是全站 chip 的系统性问题，本批未改组件签名。
6. 日历卡片总高：模式行由 FilterChip(32dp) 变 48dp 容器，卡片增高约 16dp，首屏是否被推低未确认。
7. **玻璃套玻璃（需 lead 拍板）**：`NirikoSegmentedControl` 内部自带 `appleGlassCard(PillShape)`，而它被放进 `CalendarCard` 的 `appleGlassCard` 容器内（`CalendarCard.kt:234` 与 `:475`）——FULL 档下同屏多一层实时折射，属于我在 V-04 里报过的材质层级问题。本批按指令不碰材质、未改组件；建议给组件加 `glass: Boolean = true` 或在容器内传降级参数，由 lead 决定。
