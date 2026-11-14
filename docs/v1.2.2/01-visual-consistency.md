# Niriko v1.2.2 设计评审 · 视觉一致性审计

> 任务：task-1（视觉一致性专项）。范围＝全部用户可见界面（ui/settings、ui/components、ui/screens、ui/library、ui/search、ui/subject、ui/stats、ui/share、ui/steam、ui/bilibili、ui/person、ui/character、ui/episode、ui/onboarding、ui/common、ui/theme）。
> 结论以 **git HEAD = v1.2.1 的源码**为唯一事实来源；截图只作为「观感线索」，每条都要能在源码里落到行号。

---

## 0.0 2026-10-31 复审状态（第八批后，覆盖下文旧判）

> 下表是第八批（2026-10-31）之后对 V-01…V-25 的最终判定；下文 §1–§8 保留为 v1.2.1 审计原文，**旧行号与「未修」计数已过期**，不要引用。
> 证据均在当前 `app/src` 复核过；行号为本轮 grep 实测，后续改动会漂移，以符号名为准。

| 编号 | 状态 | 证据（当前源码 / 护栏） |
|---|---|---|
| V-01 | 部分修 / 记账保留 | 与令牌等值的字面量已迁；剩 58 处无档位字面量由 `ShapeLiteralPolicyTest.kt:43-55` 的精确相等表锁定 |
| V-02 | ✅ 已修 | `ui/theme/RarityPalette.kt:27-52`（原 CardRarityMaterial 23 色）、`ui/common/IosStyleSearchComponent.kt:498-500`（SearchIndicatorAccents）；`ThemeColorAuditTest.kt` 守「`Color(0x…)` 只在 theme/share/LiquidGlassTokens」 |
| V-03 | ✅ 已修 | `ui/stats/CalendarCard.kt:1164-1173`（角标改实底 badge）；`TypographyLiteralAuditTest.kt` 守字号只来自 Type/Badges/share |
| V-04 | ⛔ 刻意不做 | D1 裁决「不收」；`GlassRenderEffectBudgetTest.kt` 只守「不新增构造点」 |
| V-05 | ✅ 已修 | `ui/settings/SettingsLayout.kt` 的 `SettingsPageHorizontalInset = 16.dp` 统一左缘；`SettingsLayoutContractTest.kt` |
| V-06 | ✅ 已修 | `ui/settings/pages/ThemePackSection.kt` 两组改 `SettingsPageSplitGroup`(24dp) |
| V-07 | ✅ 已修 | `ui/settings/SettingsItem.kt` 全部行 `nirikoRowIndication`；`SettingsLayout.kt` 的 `SettingsChevron()` |
| V-08 | ✅ 已修 | `ui/settings/AdaptiveSettingsPane.kt` 宽屏选中行 = secondaryContainer α0.35 + CardShape + `SettingsChevron(onDark = selected)` |
| V-09 | ✅ 已修 | `SettingsItem.kt` 当前值只留 `SettingsValueText`（裸文字），8dp 徽标删除 |
| V-10 | ✅ 已修 | `NirikoTextField` 37 个调用点（15 文件），`OutlinedTextField(` 只剩 `ui/components/NirikoTextField.kt:55` 基座；`ControlSourceTest.kt` 锁死 |
| V-11 | ✅ 已修 | `ui/components/NirikoSegmentedControl.kt:63` 唯一实现，接线 `ui/stats/CalendarCard.kt:507`、`ui/subject/SubjectDetailScreen.kt:2670`、`ui/common/LibrarySearchControls.kt:27`；`GlassSegmentedContainer` 删除 |
| V-12 | ✅ 已修 | `ui/components/NirikoChip.kt`（Tag/Filter/Selection/Action 四档，`:55/:86/:132/:181`）；M3 `FilterChip`/`AssistChip` = 0；`TagChip` 删除 |
| V-13 | ✅ 已修 | `GlassToggle`/`GlassSlider`/`GlassRangeSlider`（`ui/components/GlassControls.kt:458`）+ `GlassRangeSliderMath.kt`；M3 `Switch`/`Slider`/`RangeSlider` = 0；`ControlSourceTest.kt` |
| V-14 | ✅ 已修 | `ui/components/NirikoMenu.kt:56` + `GlassMenuOverlay.kt` 唯一入口（7 个调用点）；M3 `DropdownMenu` = 0；详情 5 处双行 `DropdownMenuItem` 刻意保留 |
| V-15 | ✅ 已修（剩 6 处数值强调） | `ui/components/SectionHeader.kt:31`，真实调用 43 处；`DetailTypeScaleContractTest.kt` 断言 ≥42 且 `titleMedium+Bold` 精确 6 处 |
| V-16 | ✅ 已修 | 6 个 drill-in 页 `headlineSmall`（`SubjectDetailScreen.kt:647`、`PersonDetailScreen.kt:94`、`CharacterDetailScreen.kt:69`、`StaffListScreen.kt:59`、`EpisodeDetailScreen.kt:105`、`SteamLoginScreen.kt:61`）；`DetailTypeScaleContractTest.kt` |
| V-17 | ✅ 已修 | 详情「详细信息」标题移出 420dp 滚动容器（`SubjectDetailScreen.kt:4383` 的 `SectionHeader` 与滚动兄弟结构） |
| V-18 | ✅ 已修 | `ui/subject/EpisodeRatingChart.kt` `FlowRow` + 两 Text `maxLines=1` |
| V-19 | ✅ 已修 | `EpisodeRatingChart.kt` 双线走 `themeChartPalette()[0]/[1]` + 图例行；`ChartPaletteContrastTest.kt` |
| V-20 | ✅ 已修 | `ui/subject/RatingComparisonSection.kt:225` `SectionHeader("评分分布")` + 玻璃卡；柱图 4dp 圆角（`:194` 8.sp 刻度保留，见 §7） |
| V-21 | ✅ 已修 | `ui/components/StatNumber.kt:60`（24sp/w600/tnum）+ `StatLabel`/`StatNote` + `StatTile`；统计页 20/22/26/28/32 五档清零 |
| V-22 | ✅ 已修 | 收藏概览胶囊弱形态改实底 + `ensureReadable`（`ui/library/CollectionDashboard.kt`） |
| V-23 | ✅ 已修 | `RatingComparisonSection.kt` 柱条 `drawRoundRect` + 4dp 圆角 |
| V-24 | ✅ 已修 | `ui/components/StateComponents.kt` 错误态补标题/64dp 图标/`NirikoPillButton` |
| V-25 | ✅ 已修 | `StateComponents.kt` 骨架卡 `Card`(20dp) 与真实卡对齐 |

**过期计数更正**：`FilterChip` / `AssistChip` / `DropdownMenu` = **0**；`OutlinedTextField` = **1**（组件内部基座）；M3 `Switch` / `Slider` / `RangeSlider` = **0**；`TagChip` 已删除。

## 0 审计方法

**路径前缀**：除特别注明，本文所有 路径:行号 省略前缀 app/src/main/java/com/otakup/niriko/，即 ui/settings/SettingsScreen.kt:118 = app/src/main/java/com/otakup/niriko/ui/settings/SettingsScreen.kt:118。测试路径前缀为 app/src/test/java/com/otakup/niriko/。

**手段**

1. 通读 token 层与材质层：ui/theme/{NirikoShapes,Type,GlassChipTokens,StatusColors,ChartPalette}.kt、ui/components/{GlassCard,GlassSectionCard,GlassControls,StateComponents}.kt。
2. 通读设置页全链路：ui/settings/{SettingsScreen,SettingsGroup,SettingsItem,AdaptiveSettingsPane,SettingsCategory}.kt + pages/ 12 个二级页的调用点。
3. 机械 grep 兜底（只信数字，不信印象）：
   - RoundedCornerShape(<数字>.dp) **144 处**；fontSize = **82 处**；
   - Color(0x…) 在 ui 下 **256 处** → theme 合法 188 + share/ 隔离域 24 + 材质令牌 1 ⇒ **越界 39 处**（见 V-02）；
   - OutlinedTextField( **38 处 / 17 文件**；FilterChip( **15 处**；AssistChip 7 处；Switch( 3 处；Slider( 2 处；RangeSlider 1 处；appleGlassCard( 调用 26 处；GlassToggle( 2 处；GlassSlider( **0 处**（仅被 GlassRatingSlider 内部调用）。
4. read_image 逐张看 screenshot/ 17 张 v1.2.1 真机截图。**注意版本漂移**：README.md:128-130 声明截图「部分反映早期版本界面」，实测已确认一处——截图里「观看状态编辑」层有把手，当前源码 ui/subject/SubjectDetailScreen.kt:2021 是 dragHandle = null。因此截图只用于「哪块区域值得怀疑」，判据一律是源码。

**不做的事**：不改 app/src；不跑 gradle；不做像素回归（README.md:617 已明确 Robolectric 4.17 无法编译 kyant/AGSL，真玻璃渲不出来）。

**分级**：P0 = 用户一眼看出不对（排版错位/文字被裁/元素自相矛盾）；P1 = 明显但不致命（同一控件两副面孔、圆角/字号跳变、token 被绕开）；P2 = 打磨。
**标签**：[已知欠账] = docs/audit-2026-10/README.md 已登记，本次只做推进不当初见；[刻意保留] = 文档明确要求勿报，本文只在需要划界时引用。

**现有护栏**（方案里的验收尽量挂到它们上面）：settings/SettingsSurfaceTokenTest.kt（已钉 4 页 24dp、AboutSettingsScreen 20dp、NirikoShapes.Card/RowInner/Sheet 来源）、DialogAndSheetSurfaceTest、NirikoShapesTest、GlassChipTokensTest、GlassDecisionTest、ChartPaletteTest。

---

## 1 结论摘要表

| 编号 | 级别 | 区域 | 一句话 | 关键证据 | 验收 |
|---|---|---|---|---|---|
| V-01 | P1 | token | 圆角 token 齐全却被绕开：最高频的列表卡自己写 RoundedCornerShape(20.dp)，全仓 144 处字面量 | ui/subject/UniversalSubjectCard.kt:105-106；ui/components/TagChip.kt:47 | NirikoShapesTest 扩展 + 迁移表 |
| V-02 | P1 | token | 39 处 Color(0x…) 越界，同一枚金色写了三遍，日历四色写了两份 | GlassControls.kt:317、Badges.kt:51-65、CalendarCard.kt:118-123 ≡ CalendarDaySheet.kt:294-297 | 新 token + ThemeColorAuditTest |
| V-03 | P1 | token | 正文/图例出现 7sp–10sp，图表里甚至直接写像素 textSize = 24f | CalendarCard.kt:510/992/1152、RatingComparisonSection.kt:203、LibraryGalleryCard.kt:157 | 目视 + 字号扫描契约 |
| V-04 | P1 | 材质 | FULL 档下**所有** appleGlassCard 都升级成 L2 真折射，「瓷/玻璃」分层与「同屏 ≤2 RenderEffect」同时失守 | GlassCard.kt:167-198、GlassDecision.kt:48-50 | GlassDecisionTest 加档位真值表 |
| **V-05** | **P0** | 设置页 | **同一屏三种水平缩进：组卡 0dp 贴边、组标题 16dp、页面标题/搜索框 20dp** | SettingsScreen.kt:99-131/153、SettingsGroup.kt:214-239 | SettingsLayoutContractTest（新） |
| V-06 | P1 | 设置页 | 「外观」页 11 组 24dp 里夹着主题包的 2 组 20dp，滚动时圆角跳变 | AppearanceSettingsScreen.kt:485/516/571、ThemePackSection.kt:171/195 | 同页组圆角唯一断言 |
| V-07 | P1 | 设置页 | 设置树内三种「行」：分类行 36dp 圆底图标+24dp chevron、二级页行 24dp 裸图标+20dp chevron、搜索命中行裸 24dp 图标+24dp chevron | SettingsScreen.kt:254-307/316-355、SettingsItem.kt:43-58 | SettingsRowContractTest |
| V-08 | P1 | 设置页 | 宽屏左列选中态 = 半透明行底 + 同色不透明圆底，糊成一片；且宽屏行没有 chevron | AdaptiveSettingsPane.kt:190-245 | 真机 EXPANDED 目视 |
| V-09 | P2 | 设置页 | 同组两种「当前值」：选择行是裸文字，滑块行是 8dp 字面量圆角徽标 | SettingsItem.kt:99-113/350-398 | 源码映射 |
| V-10 | P1 | 跨页 | 38 处 M3 OutlinedTextField（默认 4dp + 1dp 描边）与站内 Control(12dp) 无描边形态冲突 | SubjectDetailScreen.kt:2517/2549/2556、DataSourceSettingsScreen.kt:389 | ControlSourceTest（新） |
| V-11 | P1 | 跨页 | 分段/选择器 4 套；已有 GlassSegmentedContainer **0 调用** | GlassControls.kt:144；SubjectDetailScreen.kt:2466-2496；CalendarCard.kt:480-494 | 统一 NirikoSegmented |
| V-12 | P1 | 跨页 | chip 至少 5 副面孔（FilterChip 15 / AssistChip 7 / TagChip / 8dp 小胶囊 / 绿描边选集） | TagChip.kt:37、Screens.kt:1057、SubjectDetailScreen.kt:1760 | 统一到 TagChip |
| V-13 | P1 | 跨页 | 玻璃开关/玻璃滑块只覆盖了设置页，别处仍是 M3 Switch/Slider（GlassSlider 全站仅 1 处真实使用） | SettingsItem.kt:166/389、SearchFilterPanel.kt:151/665/650、BilibiliSyncScreen.kt:542 | 材质统一契约 |
| V-14 | P1 | 跨页 | 锚定菜单三套实现/圆角：24dp 玻璃、20dp 玻璃 DropdownMenu、20dp 普通 DropdownMenu | GlassAnchoredMenu.kt:212、LibraryTopBar.kt:92-98/145-151、Screens.kt:967-971 | 统一走 GlassAnchoredMenu |
| V-15 | P1 | 跨页 | 分区标题两套语言：设置页「绿 15sp+描述行」vs 详情页「白 16sp Bold 无描述」，图表里还有第三种「15sp Medium」 | SettingsGroup.kt:214-239、SubjectDetailScreen.kt:1354/4190、RatingComparisonSection.kt:171-176 | NirikoSectionHeader |
| V-16 | P1 | 跨页 | 页面标题 22/24/28 三档，且 headlineSmall(24sp) 未进 Type.kt ⇒ 字重 400，设置二级页标题比主页「更淡」 | SettingsGroup.kt:180、SubjectDetailScreen.kt:631、SettingsScreen.kt:107 | 二级标题统一 titleLarge |
| **V-17** | **P0** | 详情 | **「详细信息」小节标题被玻璃卡上边缘裁掉一半**（可滚动区内标题跟随滚动） | SubjectDetailScreen.kt:4182-4190 + 1367-1378 | 目视：内层滚动后标题仍在 |
| **V-18** | **P0** | 详情 | **剧集结论条文字互相串行/挤行**（Row + spacedBy(14.dp) 无换行、无 maxLines） | EpisodeRatingChart.kt:576-604 | 目视：320dp 宽不重叠 |
| V-19 | P1 | 详情 | 剧集折线两条线同色族、无图例；color/averageColor/movingAverageColor 默认全落在绿系 | EpisodeRatingChart.kt:99-105/207-230 | 图例 + ChartPalette 二色 |
| V-20 | P1 | 详情 | 「评分分布」柱图**没有任何卡片容器**，直接压壁纸；同页其他图表都在 GlassSectionCard 里 | SubjectDetailScreen.kt:1652、RatingComparisonSection.kt:164-213 | 目视：卡片边界 |
| V-21 | P2 | 统计 | 统计数值 20/22/24/28/32sp 五档 + 磁贴三套内部布局 | StatsOverviewSection.kt:90、DetailStatCard.kt:131-140、RatingComparisonSection.kt:74 | StatNumber 组件 |
| V-22 | P2 | 作品库 | 统计条一颗胶囊三种形态（强/弱/状态色文字），弱形态 α0.7 在亮壁纸下对比不足 | CollectionDashboard.kt:38-57/70-94 | 目视亮壁纸 |
| V-23 | P2 | 统计 | 柱状图直角矩形，与全站 4/12/16/20/24 圆角语言无关 | RatingComparisonSection.kt:195-199、NirikoBarChart | 目视 |
| V-24 | P2 | 四态 | 错误态只有一句 message（无标题）、图标 56 vs 空态 64、重试是全站唯一 M3 实心 Button | StateComponents.kt:257-298/327-370 | 四态对照 |
| V-25 | P2 | 四态 | 骨架卡圆角 12dp，真实列表卡 20dp ⇒ 加载完成瞬间圆角跳变；同文件另一个骨架却用了 20dp | StateComponents.kt:146-173 vs 217-246 | 目视：列表加载完成 |

---

## 2 逐条

### 2.1 主题与 token（根因层）

#### V-01 P1 圆角 token 齐全，却被字面量绕开

**现象**：全站最高频的组件——作品库/发现/搜索共用的列表卡——圆角是手写 20.dp，而不是 NirikoShapes.Card。同类还有标签胶囊的 999.dp、骨架的 12/20/4.dp、搜索层的 28.dp。改 token 时这些地方不会跟着动，这就是「同一元素在不同页面长得不一样」的机制来源。

**证据**

```kotlin
// ui/subject/UniversalSubjectCard.kt:105-106
.appleGlassCard(
    shape = RoundedCornerShape(20.dp),   // ← NirikoShapes.Card 就是 20dp，这里绕开了它
```
```kotlin
// ui/components/TagChip.kt:47
val shape = RoundedCornerShape(999.dp)   // ← 已有 NirikoShapes.Pill
```

分布（grep 计数，均可在文件内逐个核对）：RoundedCornerShape(<n>.dp) 共 **144** 处；用户可见且不属于 share/ 隔离域的代表：ui/library/LibraryGalleryCard.kt:80/81/93 = 24.dp、ui/subject/PosterGridCard.kt:116/122/129/140 = 16.dp 与 :225 = 4.dp、ui/common/IosStyleSearchComponent.kt:286/383 = 28.dp、SettingsItem.kt:58 = 8.dp、StateComponents.kt:108/146/233 = 12.dp、ui/screens/Screens.kt:807/849 = 10.dp、ui/stats/CalendarDaySheet.kt:213/316 = 6.dp。

**归因**：违反「圆角必须来自 NirikoShapes（Card 20 / Section 16 / Control 12 / Sheet 24 / RowInner 4 / Pill 999，禁止第六档）」。现有契约只钉了设置页两个半径（SettingsSurfaceTokenTest.kt:22-29），覆盖面不足。

**方案**
1. 迁移映射（不加新档，只允许这 6 档）：28/24/26/30 → NirikoShapes.Sheet(24)；20 → Card；16/14 → Section；12/10/8 → Control；6/4 → RowInner；999 → Pill。
2. 首批（影响面最大、改动最小）：UniversalSubjectCard.kt:106 → NirikoShapes.Card；TagChip.kt:47 → NirikoShapes.Pill；SettingsItem.kt:58 ValueBadgeShape → RoundedCornerShape(NirikoShapes.Control)；StateComponents.kt:146/108 → Card、:187 → RowInner。
3. 源码扫描白名单目录：ui/share/**、ui/theme/**、ui/components/liquidglass/**。

**验收**：NirikoShapesTest 增加「ui/ 下 RoundedCornerShape(<数字>.dp) 只允许出现在白名单目录」的源文本断言（本仓已有读源码断言的先例：SettingsSurfaceTokenTest 就是 text.contains(...)）；真机目视列表卡/搜索卡/骨架圆角一致。

---

#### V-02 P1 色值越界 39 处，同色三写、四色两写

**现象**：Color(0x…) 在 ui/ 下 256 处，扣掉 theme 合法值 188、share 隔离域 24、材质令牌 1，仍有 **39 处**落在用户可见组件里。最典型的是同一枚「评分金」被三个文件各写一遍，日历事件四色被两个文件各写一遍。

**证据**

```kotlin
// ui/components/GlassControls.kt:317
val perfectGold = Color(0xFFF59E0B)               // 组件内硬编码
// ui/components/Badges.kt:59
internal val RATING_ACCENT = Color(0xFFF59E0B)    // 同色第二处
// ui/subject/DetailStatCard.kt:48
Icon(Icons.Filled.Star, tint = Color(0xFFF59E0B)) // 同色第三处
```
```kotlin
// ui/stats/CalendarCard.kt:118-123  与  ui/stats/CalendarDaySheet.kt:294-297 完全重复
val BroadcastColor = Color(0xFFFDD835); val ReleaseColor = Color(0xFF66BB6A)
val HiatusColor = Color(0xFF90A4AE);    val DelayColor = Color(0xFFFB8C00)
```

其余：DetailStatCard.kt:81（0xFF3B82F6）、:91（0xFF2E7D32，浅色主题 primary，深色主题下对比不足）、LibraryGalleryCard.kt:157/162（0xFF4a4f47/0xFF1a1c19 压图文字色）、IosStyleSearchComponent.kt:100/102/748/749。Badges.kt:51-55 watchStatusColor() 是第二套观看状态色 —— **[已知欠账]**，本次不重复计数，但方案里一并解决。

**归因**：违反「Color(0x…) 只允许出现在 ui/theme」与「状态色唯一权威 = statusTone()」。

**方案**
- 新文件 ui/theme/AccentColors.kt：RatingAccent = 0xFFF59E0B、EpisodeAccent = 0xFF3B82F6、FavoriteAccent = 0xFFDC2626，三处引用改为读它。
- 新文件 ui/theme/CalendarEventColors.kt：calendarEventColors() 返回 4 色，CalendarCard 与 CalendarDaySheet 都改成读它（消掉重复定义）。
- LibraryGalleryCard 的压图文字色改为 BadgeTokens.overlayScrim/onScrim（Badges.kt 已有「黑纱底 Black α0.55」令牌）。
- 删除 watchStatusColor()，调用点改 statusTone(status).accent（[已知欠账] 的收口）。

**验收**：新增 ThemeColorAuditTest（扫描 ui/ 下 Color(0x 只允许出现在 ui/theme/、ui/share/、liquidglass/LiquidGlassTokens.kt）；真机在深色 OLED 与高饱和种子下看星标/日历角标不发灰。

---

#### V-03 P1 7sp–10sp 正文与「像素字号」

**现象**：统计页日历图例/角标、今日卡片、图库卡、详情小字大量使用 7–10sp，远低于 bodySmall 13sp；图表里还直接写 textSize = 24f（px，约 8sp）。

**证据**

```kotlin
// ui/stats/CalendarCard.kt:1152  fontSize = 7.sp      // 图例
// ui/stats/CalendarCard.kt:992   fontSize = 8.sp
// ui/subject/RatingComparisonSection.kt:203
labelPaint.textSize = 24f        // 原始像素，密度 3x ≈ 8sp
```

其余：CalendarCard.kt:510/515/522/527/531/576/585/616 = 9.sp、StatsTodaySection.kt:213(10sp)/219(9sp)/242(11sp)、CalendarDaySheet.kt:382(9sp)、LibraryGalleryCard.kt:157(9sp)、DetailStatCard.kt:140(10sp)。注意 Badges.kt:112/136/141/164 = 10.sp 是压图徽标 —— **[刻意保留]**，不在本条。

**归因**：违反「正文 ≥ bodySmall 13sp；10sp 仅限压图徽标」；fontSize = <n>.sp 字面量本身就绕过 NirikoTypography。

**方案**：图例/角标/刻度统一 labelSmall(11.5sp)；RatingDistributionChart 的 Canvas 文字改为「绘制前用 with(density){ labelSmall.fontSize.toPx() } 换算」，不再写 24f；空间不够时**减元素**（去掉 1..10 的奇数刻度）而不是缩字号；CalendarCard 在 COMPACT 宽下改为「点按弹出 CalendarDaySheet」，不继续压字。

**验收**：新增 TypographyLiteralAuditTest（ui/ 下 fontSize = <数字>.sp 白名单 = Badges.kt、ui/share/**）；真机 411dp 宽看日历图例与评分分布刻度可读。

---

#### V-04 P1 材质层级失守：所有卡都走了 L2 真折射

**现象**：设计系统是三层材质（L0 壁纸 / L1 磨砂瓷=列表卡、设置组卡、图表容器 / L2 真折射=胶囊底栏、搜索展开面、详情 dock、非模态 sheet），并要求「同屏实时 RenderEffect ≤2」。但 FULL 档下，appleGlassCard 也满足真玻璃判定，于是**设置页 3 组卡、详情页 4 张 section 卡同时做 blur+lens**。

**证据**

```kotlin
// ui/components/GlassDecision.kt:48-50
return cardBackdrop != null && glassEffect != OFF &&
    (glassEffect == FULL || (cardGlassLevel == COLLECTION_ONLY && isCollectionCard))
```
```kotlin
// ui/components/GlassCard.kt:179-193（真路径）
this.drawBackdrop(backdrop = realGlassBackdrop, shape = { shape }, effects = {
    vibrancy(); blur(12.dp.toPx())
    lens(refractionHeight = 20.dp.toPx(), refractionAmount = 40.dp.toPx(),
         depthEffect = true, chromaticAberration = true)
})
```

设置页一屏 3 张组卡（SettingsScreen.kt:153/182/202 三组），详情页一屏可见「评分/评分分布/权威评分」多张（SubjectDetailScreen.kt:1367-1378）。

**归因**：违反「L1 瓷 = 静态 tint + 高光 + 内阴影，零 GPU 滤镜；L2 = 真折射；同屏 ≤2」与「判据是背后有没有值得透出的内容」。卡片背后是壁纸（没有滚动内容透出），本应是瓷。

**方案**（推荐 A，B 作为降级选项）
- **A（推荐）**：GlassDecision 增加 cardSurfaceTier(glassEffect, cardGlassLevel, isCollectionCard, behindIsWallpaper = true): Tier 纯函数；FULL 档下 appleGlassCard 恒为 Tier.Porcelain（走 emptyBackdrop() 分支，保留 highlight/innerShadow/shadow），只有 GlassSectionCard（详情滚动内容）与浮层继续走 lens。这样「卡片=瓷」在代码里成立，同屏 RenderEffect 自然 ≤2。
- **B**：保留现状但加「同屏预算」LocalGlassBudget 计数，超过 2 的卡强制降级——需要组合期计数，复杂度更高，且滚动进出会让卡片忽明忽暗（更丑）。
- 无论 A/B，GlassDecisionTest 必须新增真值表：FULL + 列表卡 → Porcelain、FULL + section 卡 → Refractive。

**验收**：GlassDecisionTest 新真值表；真机开 FULL（壁纸开）目视：设置组卡不应随壁纸细节「流动」，详情 section 卡仍能透出封面背景墙；滑动设置页不掉帧。

---

### 2.2 设置页（用户原话命中区）

#### V-05 **P0** 同一屏三种水平缩进：组卡贴边、组标题 16dp、标题/搜索 20dp

**现象**：设置主页从上到下四种左边缘——页面标题 20dp、组标题与描述 16dp、组卡 **0dp（全宽贴边）**、搜索框 20dp。组卡是页面上面积最大的元素，它左右顶到屏幕边缘，与组标题的缩进也对不齐。这是「设置页排列/外形外观不与玻璃卡片协调」最直接的一条。

**证据**

```kotlin
// ui/settings/SettingsScreen.kt:99-131
Column(modifier = modifier.fillMaxSize().verticalScroll(...)) {
    Text("设置", style = headlineMedium,
         modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp))   // 20dp
    OutlinedTextField(..., modifier = Modifier.fillMaxWidth()
         .padding(horizontal = 20.dp, vertical = 4.dp))                             // 20dp
    ...
    SettingsGroupTitle("偏好", description = "外观、收藏展示与搜索")                 // 16dp（SettingsGroup.kt:214-239）
    SettingsSplitGroup(content = listOf(...))                                       // ← 无 modifier，0dp
```
```kotlin
// ui/settings/SettingsGroup.kt:214-239
Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
```

宽屏分支又是第三种：AdaptiveSettingsPane.kt:83-112 → Row(padding(horizontal = 12.dp, vertical = 8.dp))，组卡左右 12dp。

**归因**：无「页面水平间距」token（全仓 1565 处 .dp 字面量，已登记），设置页三条链路各写各的：标题 20 / 组标题 16 / 组卡 0 / 宽屏 12。

**方案**
1. 在 ui/settings/SettingsGroup.kt 新增常量 SettingsPageHorizontalInset = 16.dp（与组标题同值，16 也是全站最常用内距）。
2. SettingsCategoryListScreen 的标题与搜索框从 20dp 改 16dp；SettingsSplitGroup 调用处统一传 Modifier.padding(horizontal = SettingsPageHorizontalInset)——**或者**直接在 SettingsSplitGroup 内部给外层 Box 加 padding，避免 11 个调用点各写一遍。
3. 宽屏 SettingsExpandedScreen 的 12dp 改为同一常量（列表页左右 16dp 与栏间距 16dp 一致）。
4. 二级页 SettingsDetailScaffold（SettingsGroup.kt:169-207）的 content() 也包同一 inset，保证二级页与主页左右边缘对齐。

**验收**：新增 SettingsLayoutContractTest（源文本扫描）：SettingsScreen.kt 中 padding(start = 20.dp 不得出现；SettingsSplitGroup(/SettingsPageSplitGroup( 必须跟随 Modifier.padding(horizontal = SettingsPageHorizontalInset) 或由 SettingsSplitGroup 内置。真机目视：从上到下一个左边缘，组卡不再顶到屏幕边。

---

#### V-06 P1 「外观」页里 24dp 组之间夹着 20dp 组

**现象**：外观页 11 个组都是 24dp（SettingsPageSplitGroup），但中间的「主题包」是 ThemePackSection 内部用 20dp（SettingsSplitGroup），于是同页滚动时圆角从 24 跳 20 再跳回 24。用户原话「设置页排列/外形外观」很可能就是指这一页。

**证据**

```kotlin
// ui/settings/pages/AppearanceSettingsScreen.kt
:485  SettingsPageSplitGroup { /* 壁纸氛围：浓郁/均衡/素净 */ }   // 24dp
:516  ThemePackSection(viewModel, snackbarHostState)              // 内部 :171/:195 = 20dp
:571  SettingsPageSplitGroup { /* 桌面图标 */ }                   // 24dp
```
```kotlin
// ui/settings/pages/ThemePackSection.kt:43
import com.otakup.niriko.ui.settings.SettingsSplitGroup   // ← 只有 20dp 版本可用
```

注：SettingsSurfaceTokenTest.kt:76-81 显式允许**其它页**用 20dp，但那是「页与页之间的层级差」；这里是**同一页内部**混用，不属于被保留的层级差。

**归因**：违反「同一页内组卡只有一个圆角档」；ThemePackSection 是共享组件，被塞进 24dp 语境却没参数化。

**方案**：ThemePackSection 增加参数 outerRadius: Dp = NirikoShapes.Card，内部改用 SettingsSplitGroup(outerRadius = ...)；外观页调用处传 NirikoShapes.Sheet。若 grep 确认只有 AppearanceSettingsScreen 调用它，直接把 ThemePackSection.kt:171/195 改成 SettingsPageSplitGroup 并删掉 20dp import。

**验收**：SettingsSurfaceTokenTest 增加「AppearanceSettingsScreen.kt 内不得出现 SettingsSplitGroup(」断言（与既有 4 页断言同款写法，追加文件名即可）。真机目视：外观页从顶滚到底，圆角不跳。

---

#### V-07 P1 设置树内三种「行」与两种 chevron 尺寸

**现象**：设置主页分类行 = 36dp 圆底 + 18dp 图标 + **24dp** chevron；设置二级页行 = 裸 24dp 图标 + **20dp** chevron（SettingsItem.kt:55 RowChevronSize = 20.dp）；同一页的搜索结果行 = 裸 24dp 图标 + **24dp** chevron。同一个 App 的「可点行」三种骨架、两种箭头大小，搜索时命中行与分类行相邻，同屏可直接对比。

**证据**

```kotlin
// ui/settings/SettingsScreen.kt:254-307（分类行）
Box(Modifier.size(36.dp).clip(CircleShape).background(secondaryContainer)) { Icon(18.dp) }
Icon(Icons.Outlined.ChevronRight, Modifier.size(24.dp))            // ← 24dp
// ui/settings/SettingsScreen.kt:316-355（搜索结果行）
Icon(Icons.Outlined.Search, Modifier.size(24.dp))                  // ← 裸图标，无圆底
Icon(Icons.Outlined.ChevronRight, Modifier.size(24.dp))
// ui/settings/SettingsItem.kt:117-124（二级页行）
Icon(Icons.Outlined.ChevronRight, Modifier.size(RowChevronSize = 20.dp))
```

另外 SettingsSearchResultRow 的 clickable 没有传 rememberRowInteractionSource()（SettingsScreen.kt:344 附近），与其它行的按压反馈源不一致。

**归因**：设置页没有「行类型」概念，三种行各写一套；chevron 尺寸两处字面量（24/20）没走 RowChevronSize。

**方案**
- 定义两种行类型并固化成组件：SettingsCategoryRow（36dp 圆底 + 18dp 图标 + 20dp chevron，用于「跳页」）与 SettingsItemRow（24dp 裸图标 + 20dp chevron，用于「组内项」）。
- 搜索结果命中 = 跳到分类页 ⇒ 改用 SettingsCategoryRow（icon 换成各分类图标），顺带消除「裸图标」这第三种形态。
- 三处 chevron 全部改为 RowChevronSize（20dp），SettingsScreen.kt 内的 24.dp 删掉。

**验收**：SettingsRowContractTest（源扫描）：SettingsScreen.kt/AdaptiveSettingsPane.kt/SettingsItem.kt 中 ChevronRight 的 size 必须引用 RowChevronSize；真机目视搜索命中行与分类行对齐。

---

#### V-08 P1 宽屏左列选中态「糊在一起」，且与窄屏行为不同

**现象**：EXPANDED 布局左列导航行，选中时整行铺 secondaryContainer α0.55，行内却还压着一个**不透明**的同色 36dp 圆底 → 圆底与行底同色同族，边界消失，看起来像「图标下面有一块脏斑」。另外窄屏分类行有 chevron、宽屏导航行没有（靠整行底色+加粗表达选中），同一层级两种交互提示。

**证据**

```kotlin
// ui/settings/AdaptiveSettingsPane.kt:190-245
Row(Modifier.background(if (selected) secondaryContainer.copy(alpha = 0.55f) else Transparent)
        .selectable(selected, interactionSource, indication = ripple(), role = Role.Tab)
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(secondaryContainer))  // 不透明同色
    Icon(18.dp)
    Text(title, bodyLarge, fontWeight = if (selected) SemiBold else Normal)
    // 无 chevron
}
```

**归因**：选中态没有 token（secondaryContainer 直接当选中底色），且与图标底色共用同一容器色，层级无差。

**方案**：引入 SettingsNavSelectedContainer = surfaceContainerHigh（比 secondaryContainer 低饱和），选中行底用它、图标圆底保持 secondaryContainer；或反过来——行底去掉，只把 36dp 圆底换成 primaryContainer 并加 2dp primary 描边（更符合「一屏只有一处强调」）。宽屏行补 20dp chevron，由 selected 控制透明度（未选中 0.4），与窄屏一致。

**验收**：真机 sw600dp/平板目视（本条**未在真机宽屏验证**，见 §5）；NirikoWindowLayoutTest 可加「左列选中色不得等于图标底色」的常量断言。

---

#### V-09 P2 同组两种「当前值」形态

**现象**：SettingsPickerRow 的当前值是右侧裸文字（bodyMedium / onSurfaceVariant）；SettingsSliderRow 的当前值是 secondaryContainer 底 + labelMedium 胶囊，圆角是字面量 8.dp。同一组内两种「值」，且 8dp 不在 token 表里。

**证据**

```kotlin
// ui/settings/SettingsItem.kt:58
private val ValueBadgeShape = RoundedCornerShape(8.dp)     // ← 字面量
// ui/settings/SettingsItem.kt:350-398（滑块行）
Surface(color = secondaryContainer, contentColor = onSecondaryContainer, shape = ValueBadgeShape) {
    Text(valueLabel, style = labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
}
Slider(...)   // ← M3 原生滑块，见 V-13
```

**方案**：值徽标统一 RoundedCornerShape(NirikoShapes.Control) + surfaceContainerHigh 底；同时把 SettingsPickerRow 的值也包进同一个 SettingsValueBadge（把 :58-64 抽成组件，两处复用）。若嫌噪，另一选择是两边都用裸文字，但**不能一边有一边无**。

**验收**：SettingsSurfaceTokenTest 增加「SettingsItem.kt 不得出现 RoundedCornerShape(8.dp)」；目视同一组内值对齐。

---


### 2.3 跨页控件语言（同一样东西在不同页面长得不一样）

#### V-10 P1 输入框：38 处 M3 OutlinedTextField vs Control(12dp)

**现象**：站内表单几乎全是 M3 OutlinedTextField——默认形状是 M3 的 4dp 圆角 + 1dp 描边，与 Niriko 的「半透明 surfaceContainer 底、无描边、12dp」完全不是一族。设置页搜索框是唯一显式改成 Control 的（SettingsScreen.kt:130），于是「设置页里两个输入框长得不一样」也能被用户看到（主页搜索 vs 二级页里的 WebDAV/标签输入）。

**证据**（grep OutlinedTextField( 共 38 处 / 17 文件，用户最常撞上的）

```
ui/subject/SubjectDetailScreen.kt              9 处：1803, 2517, 2549, 2556, 2942, 2952, 3006, 3343, 3709
ui/settings/pages/DataSourceSettingsScreen.kt  5 处：389, 426, 433, 442, 478
ui/settings/GrayChannelSettingsSection.kt      4 处：214, 220, 251, 257
ui/subject/ExternalRatingSection.kt            3 处：276, 285, 295
ui/settings/pages/SyncBackupSettingsScreen.kt  3 处：370, 378, 385
ui/screens/Screens.kt                          2 处：687, 756
ui/subject/ProviderBindingSection.kt           2 处：238, 254
ui/subject/TmdbBindingSection.kt               2 处：372, 390
其余单处：onboarding/FirstRunScreen.kt:102、theme/ThemeColorPicker.kt:126、episode/EpisodeDetailScreen.kt:276、
  steam/SteamLoginScreen.kt:123、settings/SettingsScreen.kt:118、subject/SearchFilterPanel.kt:572、
  settings/RatingSourceSettingsSection.kt:288、settings/pages/WallpaperLibraryScreen.kt:292
```

截图侧证：收藏编辑层里「个人标签」「个人感想」两个框是 4–6dp 描边方框，而同一层的评分滑块是玻璃胶囊——同一层内两种材质（源码 SubjectDetailScreen.kt:2517/2549/2556）。

**归因**：没有 NirikoTextField 组件；M3 默认值直接外泄。违反「控件圆角来自 NirikoShapes.Control，材质与玻璃族一致」。

**方案**：新增 ui/components/NirikoTextField.kt：

```kotlin
@Composable fun NirikoTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String? = null, singleLine: Boolean = false, minLines: Int = 1,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
)
// 内部：BasicTextField + Modifier.clip(NirikoShapes.Control)
//   .background(surfaceContainerHigh, NirikoShapes.Control)   // 无描边
//   .padding(horizontal = 14.dp, vertical = 12.dp)
//   焦点态换成 primary α0.35 的 1dp 描边（不是 M3 双层描边）
```

迁移分批：设置页 7 个二级页（DataSource/GrayChannel/SyncBackup/RatingSource/WallpaperLibrary）→ 详情页 9 处 → 其余。SettingsScreen.kt:118 的搜索框也换成它（现在它是唯一显式 12dp 的，正好当模板）。

**验收**：新增 ControlSourceTest（源扫描）：ui/ 下禁止出现 OutlinedTextField(（白名单 ui/share/**）；真机目视「设置→数据源与账号」里 5 个输入框与搜索框同族。

---

#### V-11 P1 分段/选择器 4 套，而统一组件 0 调用

**现象**：这大概是「设计语言不协调」最刺眼的一处——同一件事（从 N 个里选 1 个）有四种长相：

1. M3 FilterChip（选中 = primaryContainer 实心，未选中 = 描边胶囊）：统计页日历模式（ui/stats/CalendarCard.kt:480-494，CalendarChip 直接包 FilterChip）、剧集数据源（EpisodeRatingSections.kt:153/170/176）、外部评分筛选（ExternalRatingSection.kt:267）；
2. 手写「玻璃胶囊容器 + 选中段填色」：收藏编辑的观看状态五段（SubjectDetailScreen.kt:2466-2496，选中色来自 statusTone(status).accent，文字 fontSize = 12.sp 字面量）；
3. 玻璃胶囊下拉 + 浮层菜单：发现页顶部（ui/common/VerticalTopSelector.kt:100 容器 28dp / :137 菜单 24dp）；
4. 自绘状态计数行：ui/common/LibrarySearchControls.kt:24-45（16dp 圆角、选中 primary α0.15）。

而 GlassSegmentedContainer（ui/components/GlassControls.kt:144）**全站 0 个调用点**。

**证据**

```kotlin
// ui/subject/SubjectDetailScreen.kt:2466-2496（第 2 套）
Row(Modifier.fillMaxWidth().appleGlassCard(shape = RoundedCornerShape(999.dp)).padding(4.dp)) {
    Box(Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
        .background(segBg.copy(alpha = if (selected) 0.92f else 0f)) ...)
    Text(..., fontSize = 12.sp)     // ← 字面量字号
}
```
```kotlin
// ui/stats/CalendarCard.kt:485-493（第 1 套）
FilterChip(selected = selected, onClick = onClick,
    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, ...))
```

**归因**：统一组件写好了但没接线（GlassSegmentedContainer 是 0 调用的死组件）；M3 FilterChip 的默认视觉（描边+primaryContainer）与 Niriko 玻璃族冲突。违反「同类控件单一形态」。

**方案**：以 GlassSegmentedContainer 为唯一实现，把它升级成 NirikoSegmented：

```kotlin
@Composable fun <T> NirikoSegmented(
    options: List<T>, selected: T, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, label: (T) -> String,
    selectedContainer: Color = MaterialTheme.colorScheme.surfaceContainerHighest,  // 或状态色
    shape: Shape = RoundedCornerShape(NirikoShapes.Pill),
)
```

把上面 4 套（统计页日历模式、剧集数据源、收藏编辑状态、作品库状态行）全部换成它；GlassSegmentedContainer 若复用它则改名，否则删掉，避免再出现第二个「写好了没人用」。容器统一 Pill，段内容 labelLarge(14sp)——顺带消掉 12.sp 字面量。

**验收**：NirikoSegmentedTest（纯函数测选择语义）；源扫描禁止 FilterChip( 出现在 ui/stats、ui/subject、ui/library 的「模式选择」位置；真机同屏对比四页分段。

---

#### V-12 P1 chip 五副面孔

**现象**：胶囊在站内至少五种：M3 FilterChip 15 处、M3 AssistChip 7 处、TagChip（GlassChipTokens 族）、详情「打开链接」8dp 小胶囊（labelSmall + primaryContainer α0.5）、收藏编辑里的绿描边选集 chip。用户能在同一屏看到 2–3 种：作品库筛选行（灰底 FilterChip）、详情标签（TagChip）、详情 hero（实心绿 chip）。

**证据**

```
FilterChip 15 处：Screens.kt:1057、PersonDetailScreen.kt:170/209、EpisodeDetailScreen.kt:350、
  ExternalRatingSection.kt:267、TmdbBindingSection.kt:231、EpisodeRatingSections.kt:153/170/176、
  SubjectDetailScreen.kt:3167、SearchFilterPanel.kt:182/395/615、CalendarCard.kt:485/640
AssistChip  7 处：EpisodeDetailScreen.kt:343/360、ExternalRatingSection.kt:171、
  EpisodeRatingSections.kt:214/448/462、SubjectDetailScreen.kt:1760
TagChip（唯一「正确」的）：ui/components/TagChip.kt:37（GlassChipTokens + Pill + 0.5dp 描边 + h10/v5 内距）
8dp 小胶囊：SubjectDetailScreen.kt:4210 附近（labelSmall 11sp，RoundedCornerShape(8.dp)）
```

**归因**：TagChip 的注释里写得很清楚（「改造前三套标签样式…现在只有这一枚」，TagChip.kt:22-35），但只改造了详情标签区，其余地方继续用 M3 chip 与自绘胶囊。违反「同类 chip 单一形态 + 统一 GlassChipTokens 保证压图可读性」。

**方案**：TagChip 增加 leading / trailing / onClick / selected 参数，覆盖 FilterChip/AssistChip 的全部用法（它们目前只当「可选/可点的标签」，没有 M3 特有信息）；「打开链接」改为 TagChip(onClick = ...)；选集 chip 统一 TagChip(selected = ...)。

**验收**：源扫描禁止 FilterChip(/AssistChip(（白名单 ui/share/**）；真机海报上的徽标与页面标签同族（GlassChipTokensTest 已覆盖材质数值）。

---

#### V-13 P1 开关与滑块：玻璃只覆盖了设置页

**现象**：GlassToggle 全站 2 处调用（设置页所有开关 + 收藏编辑「私密收藏」），而 M3 Switch 仍有 3 处：搜索筛选面板 2、B站同步 1。滑块更乱：GlassSlider 全站仅被 GlassRatingSlider 内部调用 1 次（收藏评分），设置页「壁纸柔化」用 M3 Slider，剧集评分也是 M3 Slider，搜索筛选用 M3 RangeSlider。于是：

- 设置页：**开了玻璃开关的行** 紧挨着 **M3 细轨滑块的「壁纸柔化」行**（SettingsItem.kt:350-398）；
- 收藏编辑层：玻璃滑块；
- 剧集详情：M3 滑块。

**证据**

```kotlin
// ui/settings/SettingsItem.kt:166
GlassToggle(checked, onCheckedChange, enabled)      // 设置页开关
// ui/settings/SettingsItem.kt:389
Slider(value = dragValue, ...)                       // 同组内的滑块 ← M3
// ui/subject/SearchFilterPanel.kt:151 / :665
Switch(...)                                          // 未迁移
// ui/subject/SearchFilterPanel.kt:650
RangeSlider(...)
```
```
GlassSlider( 调用点：仅 GlassControls.kt:329（GlassRatingSlider 内部）
GlassRatingSlider 调用点：SubjectDetailScreen.kt:2534（收藏编辑「我的评分」）
M3 Switch 另一处：BilibiliSyncScreen.kt:542
```

**归因**：v1.2.1 只做了「设置页 34 处开关」的迁移（README v1.2.1 更新段自述），滑块与筛选面板被漏下；GlassSlider 能力齐备（8dp 轨道/22dp 白芯/2dp 主题色环）却没被设置页用。违反「同类控件单一材质」。

**方案**
1. SettingsSliderRow 的 Slider(...) → GlassSlider(...)（组件已有，高 34dp，需给与 SettingsSwitchRow 同样的 48dp 行命中区）。
2. SearchFilterPanel.kt:151/665、BilibiliSyncScreen.kt:542 的 Switch( → GlassToggle(。
3. SearchFilterPanel.kt:650 的 RangeSlider 需要 GlassRangeSlider（GlassSlider 加双 thumb 变体），或降级为两个 GlassSlider（最低/最高）。
4. EpisodeDetailScreen.kt:237 M3 Slider → GlassRatingSlider。

**验收**：ControlSourceTest 增加「ui/ 下禁止 Switch( / Slider( / RangeSlider(」（M3 版）；真机在 设置→外观 与 详情→收藏编辑 两处看滑块同族。注意 GlassToggle(30dp)/GlassSlider(34dp) 的语义缺口是 **[已知欠账]**，本条不重复要求，但迁移时应顺带补 role=Role.Switch。

---

#### V-14 P1 锚定菜单三套实现/圆角

**现象**：同窗口菜单（不用 Popup）有三种落地方式：GlassAnchoredMenu（searchGlassSurface(24.dp)）、LibraryTopBar 里的 M3 DropdownMenu + searchGlassSurface(20.dp)、Screens.kt 长按菜单的 M3 DropdownMenu(shape = RoundedCornerShape(20.dp))。前两者是「玻璃浮层」，第三者其实还是 M3 菜单（阴影、间距、进场动画都是 M3 的），只有圆角被改了。

**证据**

```kotlin
// ui/components/GlassAnchoredMenu.kt:212
searchGlassSurface(RoundedCornerShape(24.dp))
// ui/screens/LibraryTopBar.kt:92-98（排序菜单）/ :145-151（视图菜单）
DropdownMenu(..., modifier = Modifier.searchGlassSurface(RoundedCornerShape(20.dp)),
             containerColor = Color.Transparent, shadowElevation = 0.dp)
// ui/screens/Screens.kt:967-971（作品库长按菜单）
DropdownMenu(shape = RoundedCornerShape(20.dp), ...)
```

**归因**：GlassAnchoredMenu 只有部分入口在用；另两处「用 M3 菜单假装玻璃」，M3 菜单的 elevation/inset/padding 与手写浮层不一致，正好违反 README v1.2.1 自述的「同窗口菜单浮层不用 Popup」。

**方案**：LibraryTopBar 的排序/视图菜单、Screens.kt 的长按菜单全部改 GlassAnchoredMenu；圆角统一 NirikoShapes.Sheet(24)。若需要菜单项分组，给 GlassAnchoredMenu 加 HorizontalDivider 支持。

**验收**：GlassAnchoredMenuTest（已有）扩一条「菜单圆角来自 NirikoShapes.Sheet」；源扫描禁止 DropdownMenu( 出现在 ui/screens、ui/library。

---

#### V-15 P1 分区标题两套语言（+ 图表内第三套）

**现象**：App 里「小节标题」有三种写法：

- 设置页 SettingsGroupTitle：titleSmall 15sp + SemiBold + primary(绿) + 可选描述行 bodyMedium（SettingsGroup.kt:214-239）；
- 详情/统计/人物/角色/剧集页 30+ 处手写：titleMedium 16sp + Bold + onSurface(白)，无描述（SubjectDetailScreen.kt:1354「简介」/1721「标签」/2500「我的记录」/2690「取景地标」/2767「猜你喜欢」/4190「详细信息」、PersonDetailScreen.kt:184/202/246/271/320、CharacterDetailScreen.kt:146/187/201、EpisodeDetailScreen.kt:202/228/263/294/328、StatsScreen.kt:657/892 等）；
- 图表内部第三套：titleSmall 15sp + Medium（RatingComparisonSection.kt:171-176 的「评分分布」）。

截图侧证：设置页「偏好」是绿色小字 + 更粗更白的描述（层级倒挂观感），同一屏「设置」大标题又更重；统计页「时间线」是白色粗体。同一个人在两页之间来回切，视觉语言换了。

**归因**：同一个语义（分区标题）没有组件，三处各写；设置页「标题小于描述」是因为 titleSmall(15) 的视觉重量低于 bodyMedium 描述行（描述 14sp 但颜色 onSurfaceVariant、换行更宽）。

**方案**：新增 ui/components/SectionHeader.kt：

```kotlin
@Composable fun NirikoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,               // 设置页用
    action: (@Composable () -> Unit)? = null,  // 「全部 23 张」「刷新」用
    tone: SectionHeaderTone = SectionHeaderTone.Primary,  // Primary=绿 / Neutral=onSurface
)
// 排版：titleSmall(15sp)/SemiBold（**不再用 16sp Bold**），description bodyMedium(14sp)/onSurfaceVariant，
// 上 20 / 下 8（与设置页一致）；无描述时下 12
```

设置页用 Primary，详情页用 Neutral（保持白色但统一 15sp/SemiBold，与设置页同字号同字重）。这一条会动 30+ 处，建议分两批：先详情页（数量最多、收益最大），再人物/角色/剧集/统计。

**验收**：SectionHeaderContractTest（源扫描）：不允许 titleMedium + FontWeight.Bold 直接作为独立 Text 出现（白名单 ui/settings/pages/** 的行标题）；真机目视设置页与详情页小节标题同一观感。

---

#### V-16 P1 页面标题 22/24/28 三档 + headlineSmall 是 400 字重

**现象**：一级 Tab 页（设置 28sp / 收藏统计 28sp）> 设置二级页 24sp > 详情/人物/角色/剧集 22sp。两处 drill-in 页标题字号不同（24 vs 22），而且 headlineSmall(24sp) 没被 NirikoTypography 覆写 ⇒ 字重是 M3 默认 **Normal(400)**，于是「设置二级页标题」比「详情页标题」更大却更细——尺寸与重量给出相反信号。

**证据**

```kotlin
// ui/settings/SettingsGroup.kt:176-182（二级页）
TopAppBar(title = { Text(title, style = MaterialTheme.typography.headlineSmall, ...) },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, ...),
          windowInsets = WindowInsets(0, 0, 0, 0))
// ui/settings/SettingsScreen.kt:107（一级页）
Text("设置", style = MaterialTheme.typography.headlineMedium)   // 28sp w600
// ui/subject/SubjectDetailScreen.kt:631（钻取页）
Text(..., style = MaterialTheme.typography.titleLarge)          // 22sp w600
// ui/stats/StatsScreen.kt:203  headlineMedium 28sp w600
```

（ui/theme/Type.kt 覆写了 displayLarge/headlineLarge/Medium/titleLarge/…，**没有** headlineSmall，所以它走 M3 默认 24sp/Normal。）

**归因**：违反「同一层级元素同字号同字重」；headlineSmall 是唯一一个「被用了但没进 token 表」的字号档，说明 Type.kt 有漏。

**方案**
1. 语义分层固定为：一级（Tab 页）headlineMedium(28/40/600)；drill-in 页 titleLarge(22/28/600)。
2. SettingsDetailScaffold 的 headlineSmall → titleLarge（SettingsGroup.kt:180）；AdaptiveSettingsPane.kt:314 的右列页标题同样改 titleLarge。
3. Type.kt 顺手补 headlineSmall（24/32/600）与 labelMedium，把「4 档未覆写」补齐——否则任何新代码写 headlineSmall 都会再踩一次。

**验收**：新增 TypeTokenTest：Typography 全 15 档都必须来自 NirikoTypography（不允许 M3 默认混入）；目视设置二级页与详情页标题同观感。

---

### 2.4 详情页

#### V-17 **P0** 「详细信息」标题被玻璃卡上边缘裁掉一半

**现象**：真机截图里，详细信息卡顶部露出半截灰字「详细信息」，像被裁掉一行。原因是小节标题写在了「高度上限 420dp + 内部滚动」的 Column **里面**：内层一滚，标题就往上跑出卡片裁剪区。

**证据**

```kotlin
// ui/subject/SubjectDetailScreen.kt:4182-4190
@Composable private fun InfoBoxSection(entries: List<InfoBoxEntry>) {
    Column(Modifier.fillMaxWidth()
        .heightIn(max = 420.dp)
        .verticalScroll(rememberScrollState())) {
        Text("详细信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ...
```
```kotlin
// ui/subject/SubjectDetailScreen.kt:1367-1378
GlassSectionCard(backdrop = glassBackdrop, isScrolling = isListScrolling, fallbackBitmap = blurredCover) {
    InfoBoxSection(entries)     // 卡片本身带 16dp contentPadding 与 clip(shape)
}
```

**归因**：把「标题」和「可滚动内容」放进同一个滚动容器 —— 结构错误（标题属于卡片，不属于滚动内容）。同时违反「滚动后仍应知道自己在看什么」。

**方案**

```kotlin
Column {
    NirikoSectionHeader("详细信息")                 // 移到滚动区外
    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
        entries.forEach { ... }
    }
    // 可选：scrollState.canScrollForward 时底部加 8dp 渐隐提示
}
```

若要更彻底：改成 LazyColumn + stickyHeader { NirikoSectionHeader("详细信息") }，并把 GlassSectionCard 的 contentPadding 从 16dp 改 0、内层自己 padding，避免标题贴边。

**验收**：真机 411dp 宽，进入详情页滚内层 info 区 400pt：标题必须一直可见、不出现半截字（截图附录对比）。

---

#### V-18 **P0** 剧集结论条文字串行

**现象**：截图里「最低 7.6 (EP10)」「波动 0.53（较为稳定）」「集数 13」几段文字互相压在一起、换行错位。根因是结论条用 Row(spacedBy(14.dp)) 摆 3 个不可压缩的 ChartStat，宽度不够时子项被压缩后内部换行，行与行内容互相穿插。

**证据**

```kotlin
// ui/subject/EpisodeRatingChart.kt:576-590
Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    ChartStat("平均", "%.2f".format(stats.average))
    stats.max?.let { ChartStat("最高", "%.1f".format(it.score) + " (EP${formatEpisodeNumber(it.ep)})") }
    stats.min?.let { ChartStat("最低", ...) }
}
Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    ChartStat("趋势", trendLabel(stats.direction) + " " + "%+.2f".format(stats.slope) + "/集")
    ChartStat("波动", "%.2f" + "（" + volatilityLabel + "）")
    ChartStat("集数", stats.count.toString())
}
// ChartStat 内部（:607-622）两个 Text，没有 maxLines、没有 weight
```

**归因**：Row 不换行 + 子项无 weight/maxLines；排版级问题，违反「正文行不可重叠」。

**方案**

```kotlin
FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) { ... }   // maxItemsInEachRow 可设 2
// ChartStat 的 value Text 加 maxLines = 1, overflow = Ellipsis
```

或改成两列 key-value 网格（Row 内两个 Column(weight(1f))），数值右对齐。推荐 FlowRow，改动最小。

**验收**：320dp（小屏/分屏）与 411dp 下目视不重叠；后续若加布局测试，可断言结论条高度随宽度变化且不超行数。

---

#### V-19 P1 剧集折线「两条同色线、没有图例」

**现象**：「剧集」卡里两条浅绿曲线（主评分线 + 移动平均线）+ 一条虚线（全季平均），没有图例说明谁是谁；用户只能靠「移动平均/全部票数」两个 chip 猜。而统计页的环形图/柱图都有图例。

**证据**

```kotlin
// ui/subject/EpisodeRatingChart.kt:99-105
fun EpisodeRatingChart(..., color: Color = MaterialTheme.colorScheme.primary,
    averageColor: Color = MaterialTheme.colorScheme.tertiary,
    movingAverageColor: Color = MaterialTheme.colorScheme.secondary, ...)
// :207-230 三条 series（main / movingAverage / average），无 Legend 组件
```

**归因**：数据可视化缺少「系列 → 视觉编码」映射（ChartPalette 已有 6 色派生但这里没用）；同色族两条实线在深色主题下几乎不可分。

**方案**：用 ChartPalette.themeChartPalette()[0]/[1] 给两条实线（alpha 保持一致），虚线保持 onSurfaceVariant α0.6；图上方加一行 NirikoChartLegend（8dp 圆点 + labelSmall 文案：实线「单集评分」、实线「移动平均」、虚线「全季平均」），并与 ui/stats/CalendarCard.kt:498+ 的 CalendarLegend 共用同一组件。

**验收**：深色 + 浅色各目视一次可区分；ChartPaletteTest 可选新增「两条 series 色差 ΔL* > 12」断言。

---

#### V-20 P1 「评分分布」没有卡片容器，直接压在壁纸上

**现象**：详情页「评分」区块里，评分对照卡有玻璃卡，紧接着的「评分分布」柱图却是**裸图**——没有卡片、只有 16dp 水平内距，柱子直接压在壁纸上；而统计页所有图表都在卡里。同一 App 两种图表容器口径。

**证据**

```kotlin
// ui/subject/SubjectDetailScreen.kt:1651-1653
RatingComparisonSection(...)                       // 内部 GlassSectionCard
Spacer(Modifier.height(8.dp))
// 评分分布（整宽柱状图，保持改造前的版式）
RatingDistributionChart(distribution = state.ratingDistribution)   // ← 裸调用
```
```kotlin
// ui/subject/RatingComparisonSection.kt:164-176（自带小标题、16dp 内距、无卡片）
Column(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.padding(horizontal = 16.dp))) {
    Text("评分分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
```

**归因**：违反「图表容器统一为 L1 瓷卡（appleGlassCard/GlassSectionCard）」；并且图表内自带标题，与 NirikoSectionHeader 又形成第四套小节标题。

**方案**：把 RatingDistributionChart 包进 GlassSectionCard(backdrop = glassBackdrop, isScrolling = isListScrolling)（与同区块评分卡同材质同圆角），去掉 compact 分支的内置标题，改由外部 NirikoSectionHeader("评分分布")；柱色改 chartPalette[0] α0.85。

**验收**：真机目视：详情页「评分」区块从卡到卡连续、无裸图；可选 ChartContainerContractTest（源扫描）：RatingDistributionChart( 必须出现在 GlassSectionCard 内。

---

### 2.5 统计 / 作品库

#### V-21 P2 统计数值五档字号 + 磁贴三套内部布局

**现象**：都是「一个大数字」，却用了 20/22/24/28/32sp 五档：StatsOverviewSection.kt:90 的 180 是 headlineLarge 32；「3.5 均分」是 titleLarge 22（:168/185）；「我的评分 7.0」是 headlineSmall 24（RatingComparisonSection.kt:74/86/105）；DetailStatCard.kt:131 是 headlineMedium 28 又被 :133 fontSize = 20.sp 覆写；StatsScreen.kt:693 是 titleMedium+Bold。磁贴内部布局也有三种：图标块+数字（左大卡）、数字+单位（右小卡）、状态色块（四张状态卡）。

**证据**：见上；截图侧证（统计页示例2）：左上大卡 180/收藏作品、右上两张「3.5 均分」「65% 完成率」、下面四张状态卡确实三种排版。

**归因**：没有「统计数值」排版角色；displayLarge 的 tnum 只在那一个地方用，数字宽度不一导致对齐跳动。

**方案**：新增 ui/components/StatNumber.kt：

```kotlin
@Composable fun StatNumber(value: String, unit: String? = null, size: StatSize = StatSize.Large)
// Large: headlineMedium(28)/w600/tnum；Medium: titleLarge(22)/w600/tnum；Small: titleMedium(16)/w600/tnum
@Composable fun StatTile(icon: ImageVector?, value: String, label: String,
                         container: Color? = null, contentColor: Color? = null, modifier: Modifier = Modifier)
```

四张状态卡用 StatTile(container = statusTone(s).container, contentColor = statusTone(s).onContainer)（现在它们已经是这套色，只是布局各异）。

**验收**：目视统计页四张状态卡与两个数字卡对齐；StatNumber 单测只测「字号档位映射」。

---

#### V-22 P2 作品库统计条：一颗胶囊三种形态

**现象**：CollectionDashboard 的 chip 分三档：emphasized（surfaceContainerHighest）、普通（surfaceContainer α0.7）、状态（普通底 + secondary 文字）。截图（早期版本）里更夸张——只有前几颗有底色、其余是裸绿字，同类指标两种形态；按当前源码，α0.7 的底在亮壁纸上几乎看不出来，也等于「裸字」。同时这一行 chip 与 TagChip（GlassChipTokens）不是同一套材质。

**证据**

```kotlin
// ui/library/CollectionDashboard.kt:38-57
DashboardChip("${stats.totalCount} 部作品", emphasized = true)
DashboardChip("累计 ${stats.totalWatchedEpisodes} 集")
stats.statusCounts.entries.forEach { DashboardChip("${status.label} $count", color = secondary) }
// :79-83
.background(if (emphasized) surfaceContainerHighest else surfaceContainer.copy(alpha = 0.7f), shape)
```

**归因**：又一个自绘胶囊（TagChip/DashboardChip/LibraryStatusSearchRow/…），且底色 alpha 未经过 ensureReadable 对比度校验。

**方案**：DashboardChip 改为复用 TagChip（GlassChipTokens.surfaceChipBackground + ensureReadable），只保留一个 emphatic = true 变体；状态 chip 的去色（secondary 文字）改 statusTone(status).accent，让状态色与统计页四张卡同源。

**验收**：GlassChipTokensTest 已覆盖对比度；真机壁纸 RICH 亮壁纸下统计条可读。

---

#### V-23 P2 柱状图直角 vs 全站圆角语言

**现象**：RatingDistributionChart 与 NirikoBarChart 的柱子都是 drawRect 直角矩形；而全站形状语言是 4/12/16/20/24 的圆角。截图侧证：评分分布、Bangumi 分布两张柱图的柱子都是硬直角，与周围玻璃卡圆角形成对比。

**证据**：ui/subject/RatingComparisonSection.kt:195-199（drawRect）、ui/stats/NirikoBarChart.kt。

**方案**：柱子顶端加圆角（drawRoundRect(..., cornerRadius = CapRadius)，CapRadius = 4.dp 即 NirikoShapes.RowInner，只在顶部两角），底部保持贴基线；柱宽 ≥ 8dp 才加圆角，否则保持直角（避免视觉锯齿）。

**验收**：目视两张柱图。

---

### 2.6 空 / 错 / 加载

#### V-24 P2 错误态结构不对称 + 全站唯一的 M3 实心按钮

**现象**：EmptyContent 是「64dp 图标 + titleMedium 标题 + bodyMedium 副标题 + action」；ErrorContent 是「56dp 图标 + 一句 bodyLarge message + M3 实心 Button」。错误态没有标题、图标小一号、按钮是全站唯一的 M3 默认 Filled Button（其它主按钮都是胶囊/玻璃）。InlineErrorHint 又是第三种（行内 bodySmall + TextButton）。

**证据**

```kotlin
// ui/components/StateComponents.kt:257-298
Icon(Modifier.size(56.dp), tint = error.copy(alpha = 0.6f))
Text(message, style = bodyLarge, color = onSurfaceVariant)
Button(onClick = onRetry) { Icon(Refresh, Modifier.size(18.dp)); Text("重试") }   // M3 默认
// :327-370
Icon(Modifier.size(64.dp), ...); Text(title, style = titleMedium); Text(subtitle, style = bodyMedium)
```

**方案**：ErrorContent 结构对齐空态：Icon(64dp) + titleMedium 标题（新增 title: String = "加载失败" 参数）+ bodyMedium 原因 + 重试按钮改 GlassChipTokens 族胶囊（TagChip 的 emphatic = true 或新增 NirikoPillButton）。InlineErrorHint 的 TextButton 改成同一 NirikoPillButton(size = Small)。

**验收**：断网进入作品库/统计/详情，三种错误态视觉结构一致；StateComponentsTest 若存在则补「错误态必须传标题」。

---

#### V-25 P2 骨架圆角与实际卡片不符（加载完成瞬间会跳）

**现象**：SkeletonList 用的 SkeletonCard 是 12dp 圆角（StateComponents.kt:146），而真实列表卡是 20dp（UniversalSubjectCard.kt:106）；同文件的 SkeletonSubjectCard 却已经用了 20dp（:217-246）。作品库首屏加载时列表骨架是 12dp 窄圆角，加载完变成 20dp 玻璃卡。

**证据**

```kotlin
// :146-173
Surface(shape = RoundedCornerShape(12.dp)) { ... }   // ← 与真实卡 20dp 不符
// :217-246
.appleGlassCard(shape = RoundedCornerShape(20.dp))   // ← 对了，但也是字面量
```

**方案**：SkeletonCard/SkeletonBlock 的圆角改为参数并默认 NirikoShapes.Card/Section；SkeletonLine 保持 RowInner；SkeletonSubjectCard 用 NirikoShapes.Card。

**验收**：作品库/搜索首次加载，骨架与内容圆角一致（目视）；NirikoShapesTest 的源扫描覆盖 StateComponents.kt。

---

## 3 组件形态统一表

| 元素 | 现状（页面 → 形态） | 目标 | 落点 / token |
|---|---|---|---|
| 页面标题 | 设置 28 / 收藏统计 28 / 设置二级页 24(Normal) / 详情·人物·角色·剧集 22 | 一级 28/600，drill-in 22/600 | headlineMedium / titleLarge；SettingsGroup.kt:180 |
| 分区标题 | 设置 15 SemiBold 绿 + 描述 / 详情 16 Bold 白 / 图表内 15 Medium | 单一 NirikoSectionHeader（15 SemiBold，tone=Primary/Neutral） | 新 ui/components/SectionHeader.kt |
| 组卡圆角 | 主页 20 / 高频二级页 24 / 主题包 20 / 宽屏左列 20 | 主页 20、二级页 24、同页唯一 | NirikoShapes.Card / Sheet |
| 页面水平内距 | 标题 20 / 组标题 16 / 组卡 0 / 宽屏 12 | 16 统一 | 新 SettingsPageHorizontalInset = 16.dp |
| 行 leading | 分类行 36 圆底+18 / 二级行 24 裸 / 搜索命中行 24 裸 | 跳页=36 圆底，组内项=24 裸 | SettingsCategoryRow / SettingsItemRow |
| 行 chevron | 24 / 24 / 20 | 统一 20 | SettingsItem.RowChevronSize |
| 行内值 | 选择行裸文字 / 滑块行 8dp 徽标 | 统一 SettingsValueBadge（Control 12dp） | NirikoShapes.Control |
| 输入框 | M3 OutlinedTextField 38 处 / 设置搜索 Control 12dp | NirikoTextField（12dp 无描边） | 新 ui/components/NirikoTextField.kt |
| 开关 | GlassToggle 2 / M3 Switch 3 | GlassToggle 全部 | GlassControls.kt:81 |
| 滑块 | GlassRatingSlider 1 / M3 Slider 3 / RangeSlider 1 | GlassSlider 全部 | GlassControls.kt:164 |
| 分段 | FilterChip 15 / 手写玻璃五段 / 玻璃下拉 / 状态计数行 | NirikoSegmented（Pill 容器） | GlassControls.kt:144 升级 |
| chip | FilterChip 15 / AssistChip 7 / TagChip / 8dp 小胶囊 / 绿描边 | TagChip 唯一 | ui/components/TagChip.kt:37 |
| 锚定菜单 | GlassAnchoredMenu 24 / DropdownMenu+玻璃 20 / DropdownMenu 20 | GlassAnchoredMenu + Sheet 24 | GlassAnchoredMenu.kt:212 |
| 图表容器 | 统计全部有卡 / 详情评分分布裸图 | 全部 GlassSectionCard | GlassSectionCard.kt:47 |
| 图表系列色 | 剧集两条同色 / 柱图直角 | ChartPalette 二色 + 圆角柱 4dp | ChartPalette.kt |
| 空态 | 64 图标 + 标题 + 副标题 | 保持 | StateComponents.kt:327 |
| 错误态 | 56 图标 + 一句 message + M3 Button | 64 图标 + 标题 + 原因 + 胶囊重试 | StateComponents.kt:257 |
| 加载骨架 | 12dp 卡 / 20dp 卡混用 | Card(20) / Section(16) | StateComponents.kt:146/217 |
| 统计数值 | 20/22/24/28/32 五档 | StatNumber 三档（28/22/16，tnum） | 新 ui/components/StatNumber.kt |
| 玻璃层级 | FULL 档全部真折射 | L1 瓷（列表/组卡/图表）/ L2 玻璃（浮层 + 详情 section） | GlassDecision.kt:48 |

---

## 4 刻意保留 / 不建议做

**刻意保留（勿当缺陷报）**

1. **设置主页 20dp vs 二级页 24dp 并存** —— 有意层级差，SettingsSurfaceTokenTest.kt:42-46/76-81 已钉死。V-06 只报「同一页内混用」，不报跨页差异。
2. **底栏 220ms 隐藏过渡、详情 350ms 材质渐显、dock 的 kyant 上游弹簧** —— niriko-motion 明确不得统一。
3. **10sp 压图徽标**（Badges.kt:112/136/141/164）—— 只在封面小图上用，不在 V-03 范围。
4. **ui/share/ 的字面量圆角/色值/字号** —— 分享卡是刻意隔离域（导出图，不跟随 App 主题）。
5. **shimmer 低档位退化为静态灰块、统计图表静态化、详情 hero 1080×1440、GlassSectionCard 50ms/500ms 节流** —— 性能取舍，已定案。
6. **21 个仍用 M3 默认圆角的 AlertDialog** —— [已知欠账]（34 处只统一 3 处）。可继续推进，但不算本版回归；V-10/V-12 不覆盖它们。
7. **Theme.kt 6 套遗留 ThemePalettes / setThemeColorIndex** —— 无 UI 入口的兼容层。
8. **Screens.kt:1099 的 headlineLarge 32sp** —— viewModel == null 的兜底占位，用户不可见。
9. **观看状态两套色表（Badges.watchStatusColor）** —— [已知欠账]，V-02 顺带收口但不单列。

**不建议做**

1. 不要为统一而新增第 6 档圆角（28/26/30 一律并入 24 或 20）；也不要引入新的弹簧/时长 token。
2. 不要把 appleGlassCard 改成 background() 来实现「瓷」——瓷必须保留 highlight/innerShadow/shadow（GlassCard.kt:155-165/201-209 的降级分支已经是正确形态，V-04 方案 A 就是让它成为常态）。
3. 不要为了统一图表容器把详情 section 全换成 GlassSectionCard（那是实时 blur，同屏会爆）；统计页保持 appleGlassCard。
4. 不要动 Theme.kt 的 ensureContrast（深色 4.5:1 是硬约束）。
5. 不要在本次统一里顺手改 share/ 的分享卡视觉——它有自己的设计目标（导出/打印）。

---

## 5 未验证项

1. **宽屏 / 平板真机未验**：V-08（左列选中态）、设置页 EXPANDED 两栏外边距、SettingsNavWidth = 300.dp 的实际观感，只有源码推断，无截图。
2. **同屏 RenderEffect 实际数量未做 GPU 测量**：V-04 的「>2」来自代码路径与设计约束（docs/ui-redesign-plan.md:51-59）推断，未用 dumpsys gfxinfo / Perfetto 核实。
3. **截图版本漂移**：screenshot/ 17 张里至少「观看状态编辑」与当前源码不一致（截图有把手，源码 SubjectDetailScreen.kt:2021 dragHandle = null）。因此**没有以截图作为唯一证据提交任何一条**。
4. **23 个二级设置页只抽看了 5 个**（外观 / 壁纸库 / 数据源 / 同步备份 / 关于 的 scaffold 与组调用），其余仅按 grep 统计圆角档位，未逐页读排版。
5. **未跑 7 组主题矩阵截图对照**（浅色默认绿关玻璃 / 深色 OLED 亮壁纸 / 深色高饱和种子 / GlassEffect OFF / COLLECTION_ONLY / reduceMotion / 壁纸 MINIMAL-RICH）。V-01/V-02/V-22 的对比度需在这 7 组下各看一次才算闭环。
6. **未做无障碍尺寸实测**：V-13 提到的 GlassToggle 30dp / GlassSlider 34dp 命中区属 [已知欠账]，本次未测量真实触控矩形。
7. **GlassSegmentedContainer 是否有意保留**（0 调用是死代码还是有计划）需作者确认；V-11 按「升级复用」处理，若决定删除则改为新建 NirikoSegmented。
8. **V-06 的 ThemePackSection 调用点**：grep 显示仅 AppearanceSettingsScreen.kt:516 调用，未 100% 确认没有别名引用。
