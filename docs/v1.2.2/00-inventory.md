# 00 · Niriko v1.2.2 逆向总览（审计基线）

> 本文件是 v1.2.2 动效/视觉方案包的**事实基线**：只记录逆向所得的结构、落点、数值与已核实问题，不含方案。
> 方案见 `01`–`06`。所有行号基于 `86dd21b`（v1.2.1 收敛后）。
> 采集方式：源码逐文件阅读 + grep 统计 + `screenshot/` 17 张 v1.2.1 真机截图目视 + 既有 `docs/audit-2026-10/`、`plans/` 基线。

---

## 0. 六条硬事实（本轮的判断前提）

1. **材质语言已经有三层，但落点不成体系**：L0 壁纸（`ui/wallpaper/WallpaperHost.kt`）、L1「磨砂瓷」`appleGlassCard`（26 处调用）、L2 真折射（kyant `searchGlassSurface` 14 处 + `LiquidGlassSurface` 3 处 + dock、miuix `GlassSectionCard` 22 处）。真正的不协调不在材质本身，而在**同一层级页面用了不同层材质/不同形状档位**（见 §4、§9）。
2. **动效令牌已经存在，但大量调用点绕过它**：`NirikoMotionSpecs` 六条弹簧（`ui/animation/NirikoMotionSpecs.kt`）是唯一合法时序源，仓内仍有 35 处裸 `spring(`、37 处裸 `tween(`、34 处 `animate*AsState(` 未接令牌。
3. **页面转场是「全局统一 + 两处特例」**：`navigation/NirikoNavHost.kt:188-215` 对全部 20 条路由统一挂 enter/exit/popEnter/popExit（`enterSharedAxisZ` 家族 + 封面共享元素特例 + reduceMotion 短路）。**任何"某页转场不一样"的方案都必须在这里做，不能逐页写**。
4. **反馈只有三件套**：点击 ripple（`clickable` 48 处 / `combinedClickable` 6 处）、按压形变（`Modifier.pressTilt`、设置行圆角 morph、`Modifier.touchGlow`）、触感（`LocalHapticFeedback` 11 处 / `HapticFeedbackType` 12 处 / `performHapticFeedback` 7 处，实测只有 7 个调用点、3 种语义）。**"操作成功但没有反馈"的缺口集中在这三件套覆盖不到的地方**（见 §7）。
5. **三处「禁止动」的既有设计**：封面共享转场 200ms 定值（`NirikoMotionSpecs.kt:34-60`，实测依据 `docs/phone-shared-motion-profile.md:66-74`）、底栏隐藏 220ms、详情 hero 材质渐显 350ms。方案只能扩，不能"统一"掉它们。
6. **视觉回归没有像素级护栏**：Robolectric 4.17 无法编译 kyant/AGSL，评审靠字符串契约测试 + 真机截图（`README.md:617`、`docs/audit-2026-10/README.md`）。因此本方案包必须给出**可写进单测的判定函数**，而不是「看起来更顺」。

---

## 1. 架构与导航地图

```
MainActivity.kt（单 Activity，enableOnBackInvokedCallback=true:59 Manifest）
└── NirikoApp / SharedTransitionScope + Backdrop 宿主
    ├── WallpaperHost（L0 壁纸 + scrim + 渐晕 + 可选视差）
    ├── NirikoWindowLayout（COMPACT/MEDIUM/EXPANDED，adaptive/WindowClass.kt）
    └── NirikoNavHost（navigation/NirikoNavHost.kt，20 条 composable 路由）
        ├── "main" → MainPager（4 个顶层页，横向 Pager）
        │   ├── 作品库 LibraryScreenContent / LibraryTopBar
        │   ├── 发现 DiscoverPosterPane / DiscoverGridPane
        │   ├── 统计 StatsScreen
        │   └── 设置 SettingsScreen（COMPACT/MEDIUM=分类列表；EXPANDED=两栏）
        └── 19 条二级路由（见 §3）
```

- 顶层页 chrome 在 **pager 外层**统一渲染（`ui/bottombar/PagerTopBar.kt` + `PagerTopBarPolicy`），左右滑动时控件不动、只有内容动（`LibraryTopBar.kt:38-45` 注释即契约）。
- 宽屏：`ui/adaptive/NirikoNavSuite.kt` 用竖向玻璃 dock 取代胶囊底栏，设置页右列复用二级页正文。
- 共享元素：全仓 `sharedElement(` 8 处，全部走 `subjectCoverPathBounds`；调用方只需给 `CoverImage` 传 `sharedElementKey`（`ui/components/CoverImage.kt:80-97`，15 个调用点）。
- 预测返回：`ui/animation/PredictiveDismiss.kt` 统一入口，当前只接入 2 处（非模态 sheet、搜索页）。

---

## 2. 顶层四页的 chrome 差异（第一处不协调）

| 顶层页 | 标题控件 | 搜索入口 | 材质 |
| --- | --- | --- | --- |
| 作品库 | **无页面标题**（顶栏 = 文件夹胶囊 + 搜索组件，`LibraryTopBar.kt:59`） | `IosStyleSearchComponent`（可展开为全宽玻璃搜索面，`searchGlassSurface`） | L2 玻璃 |
| 发现 | **无页面标题**（顶栏 = 三个 tab 胶囊 + 圆形玻璃搜索钮） | 圆形玻璃钮 → 展开 | L2 玻璃 |
| 统计 | `headlineMedium` 28sp 裸文字（`StatsScreen.kt:203`） | 无 | 裸文字 + L2 玻璃卡 |
| 设置 | `headlineMedium` 28sp 裸文字（`SettingsScreen.kt:105-109`） | **`OutlinedTextField`（12dp Control 圆角 + 描边，`SettingsScreen.kt:118-131`）** | 裸文字 + L1 玻璃组 |

> 事实：四个顶层页有 **三种顶部形态**（无标题 / 28sp 裸标题 / 描边输入框）与 **三种搜索入口形态**（展开式玻璃面 / 圆形玻璃钮 / M3 OutlinedTextField）。这是"设置页与整体不协调"的第一因，不是圆角问题。

---

## 3. 路由与页面清单（20 条）

| # | 路由 | 页面文件 | 主容器 | 备注 |
| --- | --- | --- | --- | --- |
| 1 | `main` | MainPager | — | 四顶层页 |
| 2 | `subject_search` | ui/subject/SubjectSearchScreen.kt | L2 搜索面 | 预测返回已接 |
| 3 | `bilibili_sync` | ui/bilibili/BilibiliSyncScreen.kt | M3 TopAppBar | 自建顶栏 |
| 4 | `steam_sync` | ui/steam/SteamSyncScreen.kt | M3 TopAppBar | 自建顶栏 |
| 5 | `subject_detail/{id}` | ui/subject/SubjectDetailScreen.kt | hero + GlassSectionCard | 4473 行，封面共享元素 |
| 6 | `episode_detail` | ui/episode/EpisodeDetailScreen.kt | titleLarge 裸标题 | |
| 7 | `character_detail` | ui/character/CharacterDetailScreen.kt | titleLarge 裸标题 | |
| 8 | `person_detail` | ui/person/PersonDetailScreen.kt | titleLarge 裸标题 | |
| 9 | `staff_list` | ui/subject/StaffListScreen.kt | titleLarge 裸标题 | |
| 10 | `settings_appearance` | pages/AppearanceSettingsScreen.kt | SettingsDetailScaffold | **组卡 24dp** |
| 11 | `settings_library` | pages/LibrarySettingsScreen.kt | SettingsDetailScaffold | **组卡 20dp** |
| 12 | `settings_search` | pages/SearchSettingsScreen.kt | SettingsDetailScaffold | **组卡 20dp** |
| 13 | `settings_datasource` | pages/DataSourceSettingsScreen.kt | SettingsDetailScaffold | **组卡 24dp** |
| 14 | `settings_sync` | pages/SyncBackupSettingsScreen.kt | SettingsDetailScaffold | **组卡 24dp** |
| 15 | `settings_refresh` | pages/RefreshDiagnosticsScreen.kt（+DiagnosticsSections.kt） | SettingsDetailScaffold | **组卡 24dp** |
| 16 | `settings_about` | pages/AboutSettingsScreen.kt | SettingsDetailScaffold | **组卡 20dp** |
| 17 | `settings_donate` | pages/DonateSettingsScreen.kt | SettingsDetailScaffold | **组卡 20dp** |
| 18 | `settings_detail_sections` | pages/DetailSectionsSettingsScreen.kt | SettingsDetailScaffold | **组卡 20dp** |
| 19 | `settings_wallpaper_library` | pages/WallpaperLibraryScreen.kt | SettingsDetailScaffold | **组卡 20dp** |
| 20 | `settings_*` 其它 | — | — | |

### 3.1 二级设置页组卡圆角分裂（已核实）

- `SettingsSplitGroup`（`ui/settings/SettingsGroup.kt:96-144`）= 组容器 `NirikoShapes.Card` 20dp。
- `SettingsPageSplitGroup`（`SettingsGroup.kt:151-161`）= 同实现 + `outerRadius = NirikoShapes.Sheet` 24dp。
- 使用 24dp 的页：Appearance、DataSource、SyncBackup、RefreshDiagnostics（+共享的 DiagnosticsSections）。
- 使用 20dp 的页：Library、Search、About、Donate、DetailSections、WallpaperLibrary、ThemePackSection（Appearance 内局部）。
- 契约测试 `ui/settings/SettingsSurfaceTokenTest` 只钉住「4 个页必须是 24dp」，**没有钉住"其余页面也必须是二级页语义"** → 于是 20/24 并存是"合法"的，用户看到的是同一层级页面圆角不一致。

### 3.2 `SettingsDetailScaffold` 的排版漂移（已核实）

- 顶栏：透明 `TopAppBar` + 标题 `headlineSmall`（`SettingsGroup.kt:169-207`）。`headlineSmall` 是 **M3 默认未覆写档**（24sp / Normal / 无 letterSpacing），而仓内其它页面标题用 `titleLarge` 22sp SemiBold（`Type.kt:46`）→ 二级页标题比详情页标题更"轻却更大"。
- 组标题 `SettingsGroupTitle`（`SettingsGroup.kt:213-239`）：titleSmall 15sp primary + description bodyMedium 14sp onSurfaceVariant。**15 → 14 只差 1sp，且 description 用更高对比度色**，实机（`screenshot/设置页.jpg`）读感是「白色副标题比绿色标题更大更醒目」，层级反转。

---

## 4. 材质矩阵与六条渲染路径

| 路径 | 定义 | 后端 | 数量 | 典型落点 |
| --- | --- | --- | --- | --- |
| `Modifier.appleGlassCard` | `ui/components/GlassCard.kt:97` | kyant `drawBackdrop` | 26 | 列表卡、设置组卡、图表容器、开关底座 |
| `LiquidGlassSurface` / `LiquidGlassCard` | `GlassCard.kt:227` / `ui/components/LiquidGlassCard.kt:33` | kyant | 3 | 详情浮层、非模态 sheet |
| `Modifier.searchGlassSurface` | `ui/components/SearchGlassSurface.kt:24` | kyant | 14 | 搜索面、顶部选择器、玻璃菜单 |
| `GlassSectionCard` | `ui/components/GlassSectionCard.kt:47` | **miuix** | 22 | 详情页 section |
| `BlurredGlassSurface` | `ui/components/BlurredGlassCard.kt:34` | 纯 Canvas（降级） | 3 | 无 backdrop 时的兜底 |
| `LiquidBottomTabs` / `LiquidVerticalDock` | `ui/bottombar/*` | kyant | 2 入口 | 胶囊底栏、宽屏 dock |

判定函数（必须复用，不要各页自己 if）：
- `GlassDecision.cardUsesRealGlass(...)` — 卡片（`ui/components/GlassDecision.kt:43`）
- `GlassDecision.sectionBackdropUsable(...)` — 详情 section
- 档位：`GlassEffectLevel{FULL,REDUCED,OFF}`、`CardGlassLevel{FULL,COLLECTION_ONLY,OFF}`
- 性能红线：同屏实时 RenderEffect ≤ 2；列表滚动 P95 ≤ 12ms（`docs/ui-redesign-plan.md:216`）

---

## 5. 组件落点表（可直接引用，勿另起炉灶）

- 玻璃容器：`appleGlassCard` / `GlassSectionCard` / `LiquidGlassSurface` / `searchGlassSurface` / `BlurredGlassSurface`
- 控件：`GlassControls.kt` → `GlassToggle` / `GlassSlider` / `GlassRatingSlider` / `GlassProgressBar` / `GlassSegmentedContainer`
- 徽标：`Badges.kt` → `StatusBadge` / `RatingBadge` / `EpisodeBadge` / `FavoriteBadge`；`TagChip.kt`
- 四态：`StateComponents.kt` → `LoadingContent` / `EmptyContent` / `ErrorContent` / `SkeletonBlock|List|Circle|SubjectCard`
- 弹层：`NirikoNonModalSheet.kt`、`GlassAnchoredMenu.kt`、`SharePreviewSheet.kt`、34 处 `AlertDialog(`
- 设置：`SettingsGroup.kt` → `SettingsSplitGroup` / `SettingsPageSplitGroup` / `SettingsDetailScaffold` / `SettingsGroupTitle` / `SettingsInfoRow` / `SingleChoiceDialog`
- 适配：`ui/adaptive/*`（WindowClass、AdaptiveInsets、AdaptiveDetailScaffold、NirikoNavSuite）

---

## 6. 动效清单

### 6.1 令牌层
- `NirikoMotionSpecs`（唯一时序源）：spatialFast `spring(0.9,1400)` / spatialDefault `spring(0.9,700)` / spatialSlow `spring(0.9,300)` / effectsFast `spring(1.0,3800)` / effectsDefault `spring(1.0,1600)` / effectsSlow `spring(1.0,800)`；封面专用 `subjectCoverSeek` + `subjectCoverPathBounds`（200ms，定值）。
- `NirikoAnimation.kt`：200/300/500ms 旧档 + `pressSpring`/`appearSpring`/`panelSpring`/`countTween`/`tabFade`。
- 转场全家桶：`enterSharedAxisZ` / `exitSharedAxisZ` / `popEnterSharedAxisZ` / `popExitSharedAxisZ` / `enterFromRight` / `exitToRight`。

### 6.2 现有动效类别
| 类别 | 落点 | 规格 |
| --- | --- | --- |
| 页面转场 | NirikoNavHost 全域 | sharedAxisZ + reduceMotion 短路 |
| 封面共享 | 8 处 `sharedElement` | 200ms 弧线 + 预测返回 seek |
| 底栏显隐 | `NirikoNavSuite.kt:98-130`、`NirikoBottomBar.kt:52-64` | 220ms tween（刻意保留） |
| 底栏跟手 | `bottombar/utils/DampedDragAnimation.kt:32-36` | kyant 上游 5 条弹簧（勿改） |
| 设置行按压 | `SettingsGroup.kt:111-139` | 圆角 morph（spatialDefault / snap） |
| 卡片按压 | `Modifier.pressTilt`（7° / 0.99 / spatialFast） | reduceMotion 短路 |
| 触摸光晕 | `Modifier.touchGlow`（scale 0.97 + 光晕 spring(0.5,300)） | 已知不消费事件 |
| 滚动揭示 | `RevealOnScroll.kt`（错峰失效，delayMillis 零调用） | reduceMotion 守卫 |
| 一次性庆祝 | `CompletionCelebration` 1050ms/24 粒子、`EpisodeCompletionWave` 700ms、`SubjectReturnGlow` 300ms、评分满分 600ms、CRT 4000ms | 均"只播一次" |
| 骨架 shimmer | `ShimmerPolicy` + `StateComponents.kt:75-89` | 档位降级/并发无上限 |
| 图表入场 | `NirikoBarChart`/`Donut`/`Radar`/`CalendarCard` | 部分静态化 |

---

## 7. 反馈清单与缺口

- **点击**：`clickable` 48 / `combinedClickable` 6。7 处显式 `indication = null`（自绘反馈位）。
- **按压形变**：pressTilt、设置行 morph、touchGlow、dock 跟手 scale。
- **触感**：11 个 `LocalHapticFeedback` 取用点、12 个 `HapticFeedbackType`、7 个 `performHapticFeedback` 调用；语义仅 LongPress / TextHandleMove / Confirm 三种。**无 "toggle 切换"、"拖拽吸附"、"收藏/心愿单切换"、"保存成功" 等语义**。
- **状态反馈**：`StateComponents` 四态齐备，但**行内操作**（同步单条、刷新单个数据源、立即收藏）多为文字变化，无按压→处理中→成功的连续视觉。
- **可点区域**：`IconButton` 默认 48dp 达标；但 `GlassToggle`（30dp 高）、`GlassSlider`（34dp 高）与自绘 chip 存在 <48dp 触控目标（`GlassControls.kt:189-267`）。

---

## 8. 度量与形状令牌现状

- 形状：`Card` 20 / `Section` 16 / `Control` 12 / `Sheet` 24 / `RowInner` 4 / `Pill`。squircle smoothing 0.6。
- 历史第六档：搜索卡 28dp（`searchGlassSurface` 硬编码路径之一）。
- 间距：无 token，1565 处 `.dp` 字面量，节奏 4/8/12/16/20/24/32。
- 顶部：渐隐带 24dp（alpha .35→.92）、悬浮控件行高 56dp、列表顶部内距 `inset + 72dp`、顶栏距顶 8dp。
- 底部：`DockBottomReserve = 120.dp`（`SettingsScreen.kt:247`），各页各自留白，无统一"底栏避让 + 底部渐隐"组件。

---

## 9. 已核实的横切问题（按严重度，均有源码/截图依据）

### P0（用户一眼能看见，且与设计语言直接冲突）
1. **二级设置页圆角 20/24 混用**（§3.1）——同一层级页面组卡形状不一致。
2. **顶层四页三种顶部形态 + 三种搜索入口**（§2）——设置页的描边输入框是全仓唯一一处 M3 OutlinedTextField 搜索框。
3. **设置组标题层级反转**（§3.2）——15sp/14sp 仅差 1sp，副标题更亮；实机读感「副标题是主标题」。
4. **详情页正文裸压壁纸**（`screenshot/详情页示例1.jpg`）：hero 封面下是纯文本直铺，无 L1 承载；同一 App 里列表/统计有卡、详情没有 → "点进去像换了个 App"。
5. **顶部三个图标压彩色封面**（`SubjectDetailScreen` 顶栏透明）：返回/分享/外链在亮封面（如白底封面）上可读性无保障，且无滚动后玻璃化过渡。

### P1（局部不协调/缺反馈）
6. **状态色双轨**：`StatusColors.statusTone` 与 `Badges.watchStatusColor`（`Badges.kt:50-56`）两套色；绿色同时承担 primary、状态、评分、链接 4 种语义（列表卡、发现卡、统计图例均可复现）。
7. **输入框族不一致**：观看状态编辑页（`screenshot/观看状态编辑.jpg`）用两种无填充直角描边框（标签/感想），与 `GlassControls` 的 pill/胶囊语言冲突；提示文案单行溢出被裁。
8. **分段控件选中态用实心 primary**（统计页、观看状态编辑页）而非玻璃高亮，与"玻璃分段"命名不符，且各段宽度随文案不等，视觉不齐。
9. **底部 dock 与内容硬碰撞**：截图可见列表/设置最后一行与 dock 胶囊直接贴边，无底部渐隐或"内容让位"过渡。
10. **9sp 正文**（`CalendarCard.kt:492`、`StatsTodaySection`、`CalendarDaySheet`、`LibraryGalleryCard`）——低于 bodySmall 13sp 下限。
11. **统计日历信息过载**：`screenshot/统计页示例1.jpg` 7×5 封面墙 + 每格 2 个角标 + 图例一行 7 项。

### P2（打磨）
12. 4 个未覆写排版档（displayMedium/displaySmall/headlineSmall/labelMedium）中 `headlineSmall` 已被二级页标题占用。
13. `GlassToggle` 用 `.offset()` 每帧重测（`GlassControls.kt:189-267`）而非 `graphicsLayer`。
14. 触摸光晕不 consume 事件 → 滚动残留按压态。
15. 非模态 sheet 两处 `animateTo` 未传 `initialVelocity`（`NirikoNonModalSheet.kt:148,151`）。

---

## 10. 已知欠账与"刻意保留"（不要在本轮"修复"）

- 欠账清单见 `docs/audit-2026-10/README.md`（备份字段丢失、双套状态色、对话框圆角 21 处未统一、巨型文件等）。
- **刻意保留**（评审时不得报为问题）：设置主页 20dp / 二级页 24dp 的**分档意图**（本轮要修的是"哪一页属于哪一档"的误判，不是取消分档）、220ms 底栏隐藏、350ms 详情材质渐显、dock 的 kyant 上游弹簧、10sp 压图徽标、21 个未统一圆角 `AlertDialog`、`ThemePalettes` 遗留 6 套、shimmer 低档位静态化、统计图表静态化、详情 hero 1080×1440、Robolectric 无法渲染真玻璃。
- 动效不得改的定值：`SUBJECT_COVER_DURATION_MS=200`、`NirikoMotionSpecs` 六条弹簧数值、`DampedDragAnimation` 五条弹簧、性能红线（RenderEffect ≤ 2 / P95 ≤ 12ms）。

---

## 11. 证据索引

- 真机截图（`screenshot/`，17 张）：作品库列表视图、发现页当季热门/历史排名/steam排名、详情页示例 1–7、观看状态编辑、统计页示例 1–4、设置页。
- kyant 参考源码：`C:\\what i download\\AndroidLiquidGlass-kmp`（88 个 .kt）、`C:\\what i download\\Shapes-1.2.1`（16 个 .kt）。
- 仓内既有分析：`docs/audit-2026-10/README.md`、`plans/README.md`、`docs/phone-shared-motion-profile.md`、`docs/ui-redesign-plan.md`。

---

## 13. 事实复核（kyant-ref，v1.2.2）

> 范围：只校验本文事实断言（文件是否存在、行号、数值、签名、计数）。不评方案、不重做审计。判定口径：**纠正** = 与源码不符；**待定** = 需真机/需跑 gradle/本轮未取到证据；未列出的断言未逐条复核。
> 取证方式：`read`/`grep` 逐行读 `app/src` 与 `docs`；`.dp` 计数用 `grep -rohE "[0-9]+(\.[0-9]+)?\.dp"`。行号均为 v1.2.1 HEAD 实测。

| 出处（§/行） | 我写的 | 实际 | 判定 |
| --- | --- | --- | --- |
| §0.3:13、§1:27、§3:56 | 路由 20 条 | **19 个** `composable(`（`navigation/NirikoNavHost.kt:217` 起至 `:646`），全仓无嵌套 `navigation(` 图；§3 第 20 行「`settings_*` 其它」无对应源码，应删 | 纠正 |
| §3:68 | `staff_list` 排 #9 | 源码顺序 `staff_list`（`:359`）在 `episode_detail`（`:380`）/`character_detail`（`:409`）/`person_detail`（`:432`）之前 | 纠正 |
| §3:64 | `subject_detail/{id}` | `subject_detail/{subjectId}?heroKey={heroKey}`（`NirikoNavHost.kt:303`） | 纠正 |
| §0.4:14 | `LocalHapticFeedback` 11 处 / `HapticFeedbackType` 12 处 | 各含 5 处 import，真实取用 **6 / 7** 处（7 = `performHapticFeedback` 的 7 个调用点） | 纠正 |
| §0.1:11 | `LiquidGlassSurface` 3 处 | **2 个调用点**（`ui/subject/SuggestionDropdown.kt:64`、`ui/components/LiquidGlassCard.kt:41`）；`LiquidGlassCard`（`:33` 定义）全仓 0 调用 → 活入口 1 处 | 纠正 |
| §4:101 | `LiquidGlassSurface` 后端 = kyant | **自建 AGSL**：`GlassCard.kt:250-254` `tryLoadLiquidGlassShader`、`:264` `buildLiquidGlassRenderEffect`、`:278` `buildFrostedRenderEffect`、`:297-299` `graphicsLayer { renderEffect = effect }`；不走 kyant `drawBackdrop` | 纠正 |
| §0.1:11、§4:100-104 | 26 / 14 / 22 处调用 | 均**含定义行**，真实调用点 25 / 13 / 21（定义：`GlassCard.kt:97`、`SearchGlassSurface.kt:24`、`GlassSectionCard.kt:47`） | 纠正 |
| §1:38 | `CoverImage` 15 个调用点 | **7 处**：`CoverImage(` 5 处外部（`PosterGridCard.kt:100`、`DiscoverPosterCard.kt:65`、`CalendarDaySheet.kt:210`、`:313`、`SubjectDetailScreen.kt:2788`）+ `CoverThumbnail(` 2 处（`UniversalSubjectCard.kt:127`、`TimelineSection.kt:155`）；15 实为 `sharedElementKey =` 的出现次数 | 纠正 |
| §4:104 | `BlurredGlassSurface` 3 处 / 「无 backdrop 时的兜底」 | **2 处**调用：`PersonCollectionSection.kt:110-112`（封面模糊背景墙，非兜底）、`GlassSectionCard.kt:78`（miuix 不可用降级）；定义为 `BlurredGlassCard.kt:34`（预模糊 Bitmap + clip + tint + 高光边框） | 纠正 |
| §7:157、§9:190 | `GlassToggle` 与 `GlassSlider` 均引 `GlassControls.kt:189-267` | `:189-267` 是 **`GlassSlider`**；`GlassToggle` = `:80-139`，`.offset` 每帧重测的证据行是 `:132` | 纠正 |
| §9:185 | 9sp 在 `CalendarCard.kt:492` | `:492` 是 `FilterChipDefaults.filterChipColors(...)`；9sp 实际在 `:576` / `:585` / `:616` / `:1001` | 纠正 |
| §6.2:139 | 220ms tween 落点 `NirikoNavSuite.kt:98-130`、`NirikoBottomBar.kt:52-64` | 220ms 在 `MainActivity.kt:280`（注释）/`:290` `tween(durationMillis = 220)` 与 `MainPager.kt:147-151`；被引两文件只消费 `hideProgress`（`:100`/`:121`/`:124`；`:54`/`:62`/`:64`），无 tween | 纠正 |
| §8:165 | 1565 处 `.dp` 字面量 | **1839** 处（`app/src/main`；`app/src` 全量 1896） | 纠正 |
| §0.5:15 | 200ms 依据 `docs/phone-shared-motion-profile.md:66-74` | 该段是暖路径性能表（最长测量布局 274.63→183.77ms、重组 55.06→45.99ms、Late Present 29→15），不含 200ms；200ms 唯一出处是 `NirikoMotionSpecs.kt:34` | 纠正 |
| §0.6:16 | `README.md:617` 证「真玻璃不可离屏光栅化」 | 证据在 **`README.md:624`**（B6 探针 + SkSL 报错原文）；`:617` 是限流闸门条目 | 纠正 |
| §6.2:145 | 评分满分 600ms | 无 600ms 常量；实际 `spring(dampingRatio = 0.45f, stiffness = 520f)`（`GlassControls.kt:314`）+ 金色 `0xFFF59E0B` | 纠正 |
| §0.3:13 | NavHost 四段转场 lambda | `NirikoNavHost.kt:188` `NavHost(`、`:192-215` enter/exit/popEnter/popExit（含 reduceMotion 短路与封面共享元素分支） | OK |
| §3.1、§3.2 | `SettingsGroup.kt` 各段行号与排版档 | `:97-144` / `:152-161` / `:169-207` / `:214-239` 全部吻合；`Type.kt` 无 `headlineSmall` 覆写（M3 默认 24sp） | OK |
| §3.1 | 24dp 页 / 20dp 页两张清单 | 24dp = Appearance（11 处）、DataSource（`:340`/`:343`/`:359`）、SyncBackup（`:210`/`:265`）、Refresh（4 处）+ `DiagnosticsSections`（10 处）；20dp = Library/Search/About/Donate/DetailSections/WallpaperLibrary/ThemePack/Adaptive/Gray/RatingSource + `SharePreviewSheet.kt:133`（补充） | OK |
| §4:96-105 | drawBackdrop 10 处；`GlassSectionCard` = miuix | Dock 3（`:198`/`:229`/`:259`）+ Tabs 3（`:199`/`:237`/`:270`）+ GlassCard 2（`:179`/`:201`）+ SearchGlass 1（`:34`）+ GlassSectionCard 1（`:118`）；miuix import `:25-27`、`colorControls(saturation = 1.45f)` `:135`、节流 `50ms`/`500ms` `:168`/`:171` | OK |
| §6.1 | 六条弹簧 + 200ms + keyframes | `NirikoMotionSpecs.kt:63-78`（0.9/1400、0.9/700、0.9/300、1.0/3800、1.0/1600、1.0/800）、`:34` 200ms、`:53-60` 10ms 步长 keyframes | OK |
| §6.2:145 | 1050ms/24 粒子、700ms、300ms、4000ms | `CompletionCelebration.kt:36`/`:37`、`EpisodeWavePolicy.kt:8`、`SubjectReturnGlow.kt:44`、`CrtPolicy.kt:28`（+ `FADE_OUT_MS 900` `:31`）；`RevealOnScroll` 的 `delayMillis` 无调用方传入 | OK |
| §7:157 | `GlassSlider` 34dp / thumb 22 / pad 14；`GlassToggle` 52×30 / thumb 24 | `:192`/`:183`/`:184`；`:88-89`；thumb 24 见 `:133` | OK |
| §8 | 24dp 渐隐、α .35→.92、56dp 控件行、距顶 8dp、Dock 底部 120dp、28dp 搜索卡 | `TopFadePolicy.kt:18`/`:21`/`:24`/`:27`/`:30`；`SettingsScreen.kt:236`+`:247`；`IosStyleSearchComponent.kt:286`、`VerticalTopSelector.kt:100`、`GlassAnchoredMenu.kt:212`（24dp） | OK |
| §1:38、§0.4:14 | sharedElement 8 处；预测返回 2 处；clickable 48 / combinedClickable 6 / AlertDialog 34（19 文件）/ performHapticFeedback 7 | 全部与 grep 计数吻合 | OK |
| §0.5:15、§11:206 | Manifest、Robolectric 版本、文件行数、截图数、P95 12ms | `Manifest:59` `enableOnBackInvokedCallback="true"`；`libs.versions.toml:39` robolectric 4.17；`SubjectDetailScreen.kt` 4473 行；`screenshot/` 17 个文件；`docs/ui-redesign-plan.md:216` P95 12ms | OK |
| §10:199 | `ThemePalettes` 遗留 6 套 | `ui/theme/Theme.kt:52`/`:71`/`:90`/`:109`/`:128`/`:147` 共 6 个 `label`（绿/蓝/紫/橙/红/青） | OK |
| §9 P0-4 / P0-5 | 详情页正文裸压壁纸、顶部三图标压彩色封面 | 属 `screenshot/` 目视结论，本轮未做视觉核验 | 待定 |
| §9 P2#12 | 4 个未覆写排版档（displayMedium/displaySmall/headlineSmall/labelMedium） | 只确认 `headlineSmall` 未覆写，其余 3 档未逐档查 `Type.kt` | 待定 |
| §8:165 | 「列表顶部内距 inset + 72dp」 | 72dp 数值成立（`SearchResultsPane.kt:91` 默认参数、`DiscoverPosterPane.kt:184`、`TrendingSection.kt:257`），未见「inset + 72dp」的加法表达式 | 待定 |

**汇总**：16 条纠正（7 条行号/路径与归属错位、5 条计数偏差、4 条引证或机制标错）+ 11 组 OK + 3 条待定。
