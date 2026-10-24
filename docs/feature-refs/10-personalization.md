# 10 个性化与外观

> 一句话结论：本域最值得做的是 **(a) 把「玻璃降级」从两档用户偏好升级成「设备能力 × 用户偏好」的四位计划（10.1 + 10.4 合并做，P1）**、**(b) 把 10.2/10.3 的「锁 1.0.6」从口头结论变成可执行判据 + 可回退边界（P1，成本只有 S）**、**(c) 设置项搜索（10.17，P1，Mihon 有完整可抄的实现）**。10.2 的升级判定已用源码逐字对比完成：**2.0.1 与 1.0.6 的公开 API 完全一致，demo 组件字节级相同，升级收益只在构建链（KMP 制品/Compose 1.12/AGP 9.3.2/Kotlin 2.4.10），对本项目的用户可见收益为零 → 结论是继续锁 1.0.6**。

## 10.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 10.1 | 设备能力驱动的自动降级 | 无（仅手动档位；设备判据散落 6 处内联 SDK_INT） | P1 | M | Cloudy `BlurTier.resolve` + haze `HazeSampling` + 本项目 `GlassDecision` |
| 10.2 | 玻璃边缘折射 / 形状合并 / gel-press | 已搬运（锁 1.0.6，dock 自研 319+310 行） | P1 | S | kyant 1.0.6 vs 2.0.1 逐字对比结论 |
| 10.3 | 指纹：backdrop 版本与拷贝代码同步 | 无（已漂移且无 provenance 头） | P1 | S | 上游 tag + 本地 155/77 行对照 |
| 10.4 | 玻璃降级语义统一 | 部分（真值表 + 221 行测试已就位，但仍是 bool×2） | P1 | M | `GlassDecision.kt` + `GlassDecisionTest.kt` |
| 10.5 | 壁纸自动轮换策略 | 部分（1 天硬编码周期，无频率/权重/排除） | P2 | S | `WallpaperRotation`/`Scheduler`/`Worker` |
| 10.6 | 主题包能力扩展 | 部分（不含详情页布局/动效档位） | P2 | M | `ThemePackManifest` 单向下兼容 |
| 10.7 | 底栏自定义 | 无（4 项写死，且「索引即页面」） | P2 | M | `TopLevelDestination` + pager |
| 10.8 | 看板娘（Live2D） | 无 · 建议不做 | P2 | L | —（记录不采用理由） |
| 10.9 | 分享卡多版式 | 部分（已有卡片 + 海报两种） | P2 | S | `ShareCardLayout`/`SharePosterCard`/`ShareCardRenderer` |
| 10.10 | 字体与字号可调 | 无（跟随系统 fontScale） | P2 | M | Compose `Density(fontScale)` |
| 10.11 | 圆角 / 形状主题 | 部分（`NirikoShapes` 四档 + squircle 0 调用） | P2 | S | `NirikoShapes.squircle` + kyant shapes |
| 10.12 | 玻璃 token 收敛 | 已收敛（两类不同用途，无重复维度；v1 判断有误） | P2 | S | `GlassSurfaceTokens`/`GlassChipTokens` |
| 10.13 | 顶栏/底栏密度档位 | 无 | P2 | M | `SettingsGroup` 行解剖 |
| 10.14 | 列表视图的更多形态 | 部分（LIST/GRID/GALLERY 三视图） | P2 | S | `LibraryViewStyle` |
| 10.15 | 深色模式对比度优化 | 部分（小件已有 WCAG 4.5 机械判据，正文无） | P1 | S | `GlassChipTokens.contrastRatio/ensureReadable` |
| 10.16 | 动效档位与系统设置联动 | 部分（单向 OR，无「跟随系统」三态） | P1 | S | `MainActivity` ANIMATOR_DURATION_SCALE |
| 10.17 | 设置项搜索 | 无 | P1 | M | Mihon `SearchableSettings`+`SettingsSearchScreen` |
| 10.18 | 外观快照与一键回滚（新） | 无 · 新提出 | P2 | M | 本项目设置快照思路 |
| 10.19 | 视频壁纸的电量/前台策略（新） | 无 · 新提出 | P1 | S | `WallpaperHost` + media3 |
| 10.20 | 玻璃实时性能回退（新） | 无 · 新提出 | P2 | M | FrameTiming 自适应 |
| 10.21 | 主题包安装前预览（新） | 无 · 新提出 | P2 | M | `ThemePackManager.buildApply` |
| 10.22 | 每页独立种子色（新） | 无 · 新提出 | P2 | S | `SeedColorScheme` |
| 10.23 | 外观设置子集导出（新） | 部分（备份是全量） | P2 | S | `BackupManager` 字段清单 |

## 10.1 逐条详解

### 10.1 设备能力驱动的自动降级

- **现状**｜无。只有两个**用户手动**档位：`GlassEffectLevel{FULL,REDUCED,OFF}` 与 `CardGlassLevel{FULL,COLLECTION_ONLY,OFF}`（`app/src/main/java/com/otakup/niriko/data/settings/AppSettings.kt:26-31,49-52`）。真正的「设备能力」判定散落成内联 `Build.VERSION.SDK_INT`：`ui/components/GlassCard.kt:251/328/337`、`ui/components/liquidglass/LiquidGlassShader.kt:169`（<S 直接 return null）、`ui/wallpaper/WallpaperHost.kt:139/181`、`ui/subject/SubjectDetailScreen.kt:511`（`canRenderEffect = SDK_INT >= 31`）、`ui/theme/Theme.kt:237`（动态取色 ≥S）、`ui/animation/CrtOverlay.kt:110/116`。全仓无 `ActivityManager.isLowRamDevice`、无 GLES 版本、无 RenderEffect 能力探测。
- **提升**｜用户侧：低端机首次进入就是「该降的已降」，不再先白一帧再跳降级，也不必自己猜 REDUCED 是什么。工程侧：6 处内联 SDK 分支收敛成一次判定并缓存，`GlassDecision` 的入参从 3 维扩到 4 维仍是有限真值表。
- **原理**｜新增纯函数 `DeviceCapability`（不依赖 Compose）：

  ```kotlin
  enum class GlassTier { FULL, BLUR_ONLY, STATIC }   // 同序即可比大小
  data class DeviceCapability(val sdk: Int, val isLowRam: Boolean, val hasRenderEffect: Boolean)
  fun tierOf(c: DeviceCapability): GlassTier = when {
      !c.hasRenderEffect -> GlassTier.STATIC          // API<31：无 AGSL/RenderEffect，真折射不可能
      c.isLowRam -> GlassTier.BLUR_ONLY               // 有 GPU 但内存吃紧：保模糊，弃折射
      else -> GlassTier.FULL
  }
  // 与用户档位取更保守者：ordinal 越大越保守
  fun effective(user: GlassEffectLevel, dev: GlassTier) = minOf(user.ordinal, dev.ordinal)
  ```

  缓存策略：结果写 DataStore 单键 `device_glass_tier`，用 `Build.FINGERPRINT.hashCode()` 做失效；判定只在 `NirikoApplication` 冷启动读一次，UI 侧只读 CompositionLocal（不新增每帧开销）。`isLowRam` 用 `ActivityManager.isLowRamDevice`，`hasRenderEffect` 用 `Build.VERSION.SDK_INT >= 31`（Kotlin 侧不要反射 `RenderEffect`，会吃到 R8 的反射限制）。
- **效果**｜设置页「玻璃/特效强度」多一行灰字「自动（本机：降低）」，用户手动改档后该行变「手动」。列表卡在低端机首帧即静态玻璃（`GlassCard` 的 static 分支），详情页区块卡走 `BlurredGlassSurface`（有封面模糊位图时）而不是不透明色块。玻璃档位为 STATIC 时，设置页那两个档位开关变灰并提示「本机不支持实时玻璃」。
- **参考**｜`skydoves/Cloudy@main:cloudy/src/androidMain/kotlin/com/skydoves/cloudy/internal/BlurTier.android.kt` [码] —— `enum BlurTier{Gpu,Cpu,Scrim}` + `resolve(isApi31Plus, cpuBlurEnabled)`，与本设计同构（三档、纯函数、每帧解析但不分配）；`chrisbanes/haze@2.0.1:haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffect.kt` [码] —— `HazeSampling{FullResolution,Adaptive,Fixed(pixelFraction)}` 把「采样分辨率」做成可降级旋钮（比我们的「开/关」更细）；本项目 `ui/components/GlassDecision.kt` [码]。
- **成本/风险**｜M｜最可能翻车：把「低端」写成 `isLowRamDevice` 一条判据，误伤 8GB 中端机；必须提供「自动/强制全开」覆盖开关，且失败时默认 FULL。
- **优先级**｜P1

### 10.2 玻璃边缘折射 / 形状合并 / gel-press（升级 2.x vs 锁 1.0.6）

- **现状**｜已搬运且已用上：`app/build.gradle.kts:128` 锁 `io.github.kyant0:backdrop:1.0.6`（注释写明 2.0.1 需 AGP 9.1/Compose 1.12）；demo 三件套在 `ui/bottombar/utils/{DampedDragAnimation,InteractiveHighlight,DragGestureInspector}.kt`，组件在 `ui/bottombar/{LiquidBottomTabs,LiquidVerticalDock,LiquidBottomTab,DockTouchState}.kt`（319/310/53/30 行）。
- **提升**｜不是加效果，而是**把决策钉死**，省掉每轮「要不要跟上游」的讨论成本；同时把「gel-press 参数跟上游还是自研」这个悬案收口（结论：自研，因为本地已改出按压超时逻辑）。
- **原理**｜源码逐字对比（本轮实测，全部 `[码]`）：
  0. **版本线**：上游默认分支是 `kmp`（不是 main/master），2.x 是**仍在推进**的线（默认分支最新活跃 2026-08-26，文件集 185 个与 2.0.1 tag 一致）；1.0.6 是 1.x 末版（Android-only 线）。因此「锁 1.0.6」= 冻结在停更线上，代价是拿不到上游后续 bugfix —— 这条必须写进决策文档，并用 10.3 的「单文件 cherry-pick」兜底。
  1. **构建形态**：1.0.6 = `android.library`，`backdrop/src/main/java/`，compileSdk 36，AGP 9.0.0，Kotlin 2.3.10，Compose 1.10.3，`kyant shapes 1.2.0`；2.0.1 = `android.multiplatform.library` + `kotlin.multiplatform`，`commonMain/androidMain/skikoMain`，compileSdk 37，AGP 9.3.2，Kotlin 2.4.10，Compose 1.12.0，`kyant shapes 1.2.1`，依赖 `org.jetbrains.compose.*`。
  2. **公开 API 无差异**：`Backdrop.kt`（`interface Backdrop{ isCoordinatesDependent; DrawScope.drawBackdrop(...) }`）、`effects/Lens.kt`、`backdrops/CombinedBackdrop.kt`（三重重载 `rememberCombinedBackdrop`）、`backdrops/LayerBackdrop.kt`（`rememberLayerBackdrop` / `class LayerBackdrop internal constructor`）、`DrawBackdropModifier.kt`（`Modifier.drawBackdrop` 参数表逐项相同）、`highlight/HighlightModifier.kt`、`BackdropEffectScope.kt` 在 1.0.6 与 2.0.1 逐文件比对**公开声明一致**。
  3. **demo 字节级对比**：`LiquidBottomTabs.kt` 1.0.6 与 2.0.1 都是 11729 B **完全相同**；`LiquidBottomTab.kt` 1628 B 相同；`DragGestureInspector.kt` 2819 B 相同、`LiquidButton.kt` 4688 B 相同；`DampedDragAnimation.kt` 138→138 行，6 行差异全是 import 顺序 + `System.currentTimeMillis()` → `Clock.System.now()`；`InteractiveHighlight.kt` 122→122 行，差异是去掉 `android.graphics.RuntimeShader`/`android.os.Build` import（平台化）。
  → **判据**：(i) 用户可见收益 = 0；(ii) 依赖链代价 = 整链升 Compose 1.12 + AGP 9.3.2 + Kotlin 2.4.10（KSP 必须配套，`libs.versions.toml` 里 KSP 当前 2.3.11 按 Kotlin 版本绑定）；(iii) 可回退性 = 见下。三条都指向**锁 1.0.6**。
- **效果**｜用户看不到变化——这正是目的。可执行产出是：`docs/dock-kyant-migration-plan.md` 增「锁定声明」一节，写清 1.0.6 是**刻意锁定**、升 2.x 的前置条件是 Compose 升到 1.12 且 AGP 9.3.x，以及三条判据；避免下轮把「没升级」误判成技术债。
- **参考**｜`Kyant0/AndroidLiquidGlass@1.0.6:backdrop/build.gradle.kts` 与 `Kyant0/AndroidLiquidGlass@2.0.1:backdrop/build.gradle.kts` [码]；`Kyant0/AndroidLiquidGlass@1.0.6:gradle/libs.versions.toml` 与 `Kyant0/AndroidLiquidGlass@2.0.1:gradle/libs.versions.toml` [码]；`Kyant0/AndroidLiquidGlass@1.0.6:catalog/src/main/java/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt` vs `Kyant0/AndroidLiquidGlass@2.0.1:app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt` [码]。
- **成本/风险**｜S（只写决策 + 收敛 import）｜风险：把「拷贝的 demo 代码」当库用，上游 2.x 若修了 InteractiveHighlight 的 bug，我们拿不到——缓解见 10.3（记录上游基线，允许 cherry-pick 单文件）。
- **优先级**｜P1

### 10.3 指纹：backdrop 版本与拷贝代码的同步

- **现状**｜无。本地拷贝已明显漂移：`ui/bottombar/utils/DampedDragAnimation.kt` 155 行 vs 上游 138 行（本地多了 `withTimeoutOrNull`/`Job`/`delay` 的按压超时）；`InteractiveHighlight.kt` 77 行 vs 上游 122 行（本地删掉了大量 hover/指针分支）。三个文件**都没有任何 provenance 头注释**（无 upstream tag、无 commit、无「本地改动点」列表）。
- **提升**｜升级时不用靠记忆猜「这 155 行里哪 17 行是我改的」。工程上把「拷贝」从口约变成可断言事实。
- **原理**｜三层：
  1. 文件头 provenance 注释（tag + 上游路径 + 本地改动摘要）；
  2. 仓库根 `third_party/copy-manifest.json`：`{localPath, upstream: "Kyant0/AndroidLiquidGlass@1.0.6", upstreamPath, bodySha256, localEdits: ["timeout", "hover-removed"]}`，`bodySha256` 只对「去掉 header 后的正文」算；
  3. JVM 测试 `VendoredUpstreamFingerprintTest`：读 manifest → 算正文 hash → 与记录比对。改代码的人要么同步改 manifest（显式承认漂移），要么被测试拦住。
- **效果**｜开发者改这三个文件时测试失败并打印「本地正文与记录不一致，请更新 copy-manifest.json 并补充 localEdits」。升级 PR 里能机械列出「需人工复检的 5 个文件」。
- **参考**｜`Kyant0/AndroidLiquidGlass@1.0.6:catalog/src/main/java/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt`（138 行）[码]；本地 `ui/bottombar/utils/DampedDragAnimation.kt`（155 行）[码]；`docs/dock-kyant-migration-plan.md` 第二节「要搬迁的文件」已列清单，但无版本指纹 [码]。
- **成本/风险**｜S｜风险：把「注释/hash」当契约会让人绕过（改 manifest 不改代码也能过）；需要 Code Review 规则配合。
- **优先级**｜P1

### 10.4 玻璃降级语义统一

- **现状**｜部分。真值表已抽成纯函数 `ui/components/GlassDecision.kt`：`cardUsesRealGlass(cardBackdrop, glassEffect, cardGlassLevel, isCollectionCard)` 与 `sectionBackdropUsable(backdrop, glassEffect)`；测试 `app/src/test/java/com/otakup/niriko/ui/components/GlassDecisionTest.kt` 221 行 8 个 `@Test`（`cardTruthTableCoversEveryCombination`、`reducedDivergesBetweenCardAndSection` 等）已穷举。两处**有意不对称**：卡片只把 `OFF` 当关玻璃（REDUCED 与 FULL 等效），区块卡只有 `FULL` 才折射。
- **提升**｜用户侧：REDUCED 档目前会出现「列表卡有玻璃、详情区块卡没有」的割裂观感；统一后 REDUCED = 有模糊无折射，全站一致。工程侧：把 bool 返回值换成 4 位能力集，10.1 的设备档位才有地方落。
- **原理**｜把「一体两面」的假设拆成能力位：

  ```kotlin
  data class GlassPlan(val refraction: Boolean, val blur: Boolean, val highlight: Boolean, val shadow: Boolean)
  fun planFor(surface: Surface, backdrop: T?, user: GlassEffectLevel, dev: GlassTier, card: CardGlassLevel, isCollection: Boolean): GlassPlan
  // 卡片：需要 highlight/shadow，不需要 refraction（静态渐变已足够）→ REDUCED 保留玻璃
  // 区块卡：需要 refraction 才成立 → REDUCED 退到 blur-only
  ```

  关键取舍：先把「REDUCED 到底关什么」定义成 **blur 保留 / refraction 关闭 / highlight+shadow 保留**，再让两个 surface 都按这个定义解析；不对称只在「卡片永远不需要 refraction」这一条事实上，而不是在档位语义上。
- **效果**｜REDUCED 档下详情页区块卡从「静态不透明」变为「高斯模糊 + 无折射 + 有色散缺失」，与卡片观感统一；低端机滚动时不再出现一半玻璃一半实心的跳变。
- **参考**｜本项目 `ui/components/GlassDecision.kt` + `GlassDecisionTest.kt` [码]；`skydoves/Cloudy@main:cloudy/src/androidMain/kotlin/com/skydoves/cloudy/internal/BlurTier.android.kt` [码]（三档而非 bool）；`chrisbanes/haze@2.0.1:haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffect.kt` [码]（`HazeSampling` 同样是「分档而非开关」的建模方式）。
- **成本/风险**｜M｜风险：把 REDUCED 语义改动会推翻现有 8 个测试的 2 个断言（`reducedDivergesBetweenCardAndSection` 必改）——必须先改测试再改实现，否则「测试通过」是假的。
- **优先级**｜P1

### 10.5 壁纸自动轮换策略

- **现状**｜部分。`data/wallpaper/WallpaperRotation.kt` 是纯函数（`candidates(entries, favoritesOnly)` / `next(entries, currentUri, favoritesOnly)`，环形顺延、池空返回 null）；`WallpaperRotationScheduler.kt` 固定 `PeriodicWorkRequestBuilder<WallpaperRotationWorker>(1, TimeUnit.DAYS)` + `ExistingPeriodicWorkPolicy.KEEP`（`WORK_NAME = "wallpaper_rotation_periodic"`）；Worker 读 settings 首帧，开关关闭直接 success。**没有**：轮换频率、权重、排除项、充电/WiFi 约束、手动「下一张」。
- **提升**｜用户能设「每 6 小时/每天/每周」并排除某些壁纸；工程上把「1 天」从代码常量搬成设置字段，顺带修掉一个隐患：一旦频率可调，`KEEP` 会让新频率**不生效**（改了设置任务不重排）。
- **原理**｜`AppSettings` 增 `wallpaperRotationIntervalHours: Int = 24` 与 `wallpaperRotationExcludedKeys: String = ""`（沿用 `detailHiddenSections` 的「逗号分隔 key 串」惯例，设置层不依赖 UI 枚举）。`WallpaperRotation.next` 增加排除过滤；权重用「`epochDay` 作种子 + 加权抽样」，保证同一天同一台机上多次调用结果稳定（可单测）。调度改为：

  ```kotlin
  PeriodicWorkRequestBuilder<WallpaperRotationWorker>(intervalHours.toLong(), TimeUnit.HOURS)
  WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
  ```

  `KEEP` → `UPDATE` 是这次的关键词：前者「已存在就不动」，后者「按新 spec 替换」。
- **效果**｜外观页「壁纸轮换」多一行「频率：6 小时/每天/每周」；壁纸库里每张多一个「排除」勾选；关掉轮换再打开后按新频率走（不再永远 24h）。
- **参考**｜本项目 `data/wallpaper/WallpaperRotation.kt`、`WallpaperRotationScheduler.kt`、`WallpaperRotationWorker.kt` [码]；`ExistingPeriodicWorkPolicy.UPDATE` 的语义按 androidx.work 文档 [推断]（本机未拉 androidx 源码逐字核对）。
- **成本/风险**｜S｜风险：WorkManager 的周期任务最小间隔 15 分钟，把它放开到「1 小时」以下会被系统钳制并抛 `IllegalArgumentException`；UI 上直接限定 ≥6 小时。
- **优先级**｜P2

### 10.6 主题包能力扩展

- **现状**｜部分。`data/themepack/ThemePackModels.kt` 的 `ThemePackManifest(formatVersion=1, id, name, author, version, minAppVersion, colors{light,dark}:ThemePackColorSeed(primary/secondary/tertiary), wallpaper{type,file,blurRadiusDp}, pages{library,discover,stats,settings}, splash{enabled})`；`ThemePackManager` 已有 `import/listInstalled/delete/exportInstalledPack/buildApply/export/parseAndValidate`。兼容策略明确：未知字段忽略、缺失回落默认、`formatVersion` 不做硬门禁。**不含**详情页部件布局（`detailHiddenSections`/`detailSectionOrder`）、动效档位、玻璃/形状/字号。
- **提升**｜主题包能带「详情页部件顺序」→ 创作者可做「图鉴向/影评向」布局；工程上这些设置**已经是 `AppSettings` 里的现成字段**，加进 manifest 的成本只是序列化 + 一条「只覆盖非空字段」的规则。
- **原理**｜manifest 增 `layout: ThemePackLayout(detailHiddenSections: String = "", detailSectionOrder: String = "", reduceMotion: Boolean? = null)`；`buildApply` 只写入「非 null / 非空」字段（null = 不覆盖用户），并把「本次改动的字段名」记进 `appliedThemePackFields`，卸载时按 `SettingsDataStore` 的 last-known 快照回滚。单向兼容已成立：老 App 读新包会忽略 `layout`，新 App 读老包得到默认空串。
- **效果**｜装包后详情页立刻按包内顺序重排（`DetailLayoutPolicy.resolveOrder` 直接吃这个串）；卸载后回到装包前的顺序。
- **参考**｜`data/themepack/ThemePackModels.kt`、`ThemePackManager.kt`（`buildApply`/`parseAndValidate`/`isSafeEntry`）[码]；`ui/settings/pages/ThemePackSection.kt` [码]。
- **成本/风险**｜M｜风险：主题包可隐藏「收藏/评分」等交互区块 → 必须白名单，只允许隐藏非交互区块与排序，硬禁止隐藏收藏按钮与评分入口（否则外部包可以做成钓鱼界面）。
- **优先级**｜P2

### 10.7 底栏自定义

- **现状**｜无。`navigation/TopLevelDestination.kt:22` 是固定 4 项枚举（Library/Discover/Stats/Settings，含 `route/titleRes/selectedIcon/unselectedIcon`）；`MainActivity.kt:222` `rememberPagerState(pageCount = { TopLevelDestination.entries.size })`；`navigation/NirikoBottomBar.kt:68` 传 `tabsCount = TopLevelDestination.entries.size`；`navigation/MainPager.kt:103` 用 `entries[page]` —— **索引即页面身份**。`data/settings` 无任何 tab 显隐 key。
- **提升**｜用户能隐藏不看的页（典型：只用作品库 + 发现）。工程上必须先解掉「索引即身份」，否则一显隐就翻错页。
- **原理**｜设置存 CSV key 串 `bottomBarDestinations`（如 `"library,discover,settings"`）→ 解析成 `List<TopLevelDestination>`（未知 key 忽略、保底至少 2 项、Settings 不可隐藏）。`pageCount = enabled.size`；`MainPager` 由 `enabled[page]` 取目的地；`selected` 用 `enabled.indexOf(current)`；「上次停留页」改存 **route 字符串**而不是 index（`SavedStateHandle` 里 index 会随显隐失效）。指示器宽度由 `LiquidBottomTabs(tabsCount = enabled.size)` 自动变成 1/N，无需改动画。
- **效果**｜设置「外观 › 底栏」出现 4 行勾选（设置项置灰不可取消）；取消「统计」后 dock 变 3 个胶囊、指示器变宽，左右滑动只在这 3 页之间；重启后回到原来的页面（按 route 恢复）。
- **参考**｜`navigation/TopLevelDestination.kt`、`MainPager.kt:103`、`ui/bottombar/LiquidBottomTabs.kt`（`tabsCount` 驱动指示器宽度）[码]；设置侧 `data/settings/AppSettings.kt` 的 `detailSectionOrder` 串式 schema 可复用 [码]。
- **成本/风险**｜M｜风险：隐藏当前所在页后 pager 位置非法；必须写「重定位到最近可见页」的单测（`PagePager` 已有类似纯函数可扩展）。
- **优先级**｜P2

### 10.8 看板娘（Live2D）

- **现状**｜无 · 建议**不做**。当前 UI 已有大量 GPU 负载源（真折射卡片、`drawBackdrop` 每帧捕获、视频壁纸、稀有度材质），`docs/performance-audit.md` 的 P0 就是「底部胶囊 drawBackdrop 每帧捕获全页」。
- **提升**｜如实记录「不做」的理由，避免每轮重提；如果一定要「有生命感」，用**替代方案**：空态/启动页一段 Rive 或 Lottie（单文件 <2 MB，无骨骼运行时不驻留），而不是 Live2D 全量 SDK。
- **原理**｜Live2D Cubism SDK 的代价结构：模型（`.moc3` + 贴图，通常 5–20 MB）常驻内存 + 每帧顶点形变与贴图采样 + 需要独立 GL 线程/上下文；与 Compose 的 `GraphicsLayer` 捕获管线叠加时，会与 `rememberLayerBackdrop` 的全页捕获争 GPU。Rive/Lottie 路线是「播放器渲染一帧位图」接入现有 `Image`/`Canvas`，不引入第二个渲染上下文。
- **效果**｜（采用替代方案时）启动页右侧有一只 2–3 秒循环的小角色；减少动态效果开启时只显示静态首帧。
- **参考**｜本项目 `docs/performance-audit.md`（P0 顶部捕获结论）[码]；Live2D 成本为通用工程常识 [推断]，未读 Cubism SDK 源码。
- **成本/风险**｜L｜风险：模型授权（角色形象版权）比技术更麻烦；且体积会让 APK 从 ~20 MB 级跳到 40 MB+。
- **优先级**｜P2（记录不采用）

### 10.9 分享卡多版式

- **现状**｜**部分**，且 v1 的「单一版式」已过时：现在有 `ui/share/ShareCardLayout.kt`（`ShareCardData` + `HeroCoverShare`/`HeadMeta`/`ProgressLine`/`RatingCompareCard`/`CollectionSection`）与 `ui/share/SharePosterCard.kt` 两种；渲染走 `ShareCardRenderer.kt` 的 `ShareBitmapHost.render`/`renderPoster`（ComposeView attach 到窗口 + `View.draw` → `Bitmap.Config.ARGB_8888`）；包含项有 `shareIncludeRating/Progress/Tags` 三个开关。
- **提升**｜补「纯文字数据卡/榜单卡」：无封面，规避封面版权与「不想暴露在看列表」的顾虑；工程上把版式抽成 skin，渲染管线参数化一次，后面加版式是纯增量。
- **原理**｜`interface ShareCardSkin { @Composable fun Content(data: ShareCardData) }` + `object ShareCardSkins { val Hero, Poster, TextOnly }`；`render(skin, width, background, scale)` 把现在两个几乎重复的 `render`/`renderPoster` 合并成一条（两者差异是宽度、背景与是否含封面）。预览 Sheet 里横向切换 skin，选中即重渲染缩略图。
- **效果**｜分享预览底部出现 3 个版式缩略 tab（卡片/海报/纯文字）；纯文字版在深色背景上按主题色出图，仍保留评分对比与进度两行。
- **参考**｜`ui/share/ShareCardLayout.kt`、`SharePosterCard.kt`、`ShareCardRenderer.kt`、`ShareFlow.kt` [码]。
- **成本/风险**｜S｜风险：`ShareBitmapHost` 依赖真实 Activity 窗口，Robolectric 下渲染不出（`docs/ui-upgrade-plan-2026.md` 已记录 AGSL 在 Robolectric 不可用），所以版式正确性只能靠「不抛异常 + 尺寸断言」测。
- **优先级**｜P2

### 10.10 字体与字号可调

- **现状**｜待核 → **已核：无**。`ui/theme/Type.kt` 定义 `NirikoTypography`（逐条 `letterSpacing` 手调），全仓文字用 `MaterialTheme.typography` + `sp`，随系统 `fontScale`；`data/settings` 无 `fontScale`/`fontSize` key。
- **提升**｜用户不必为了看清字去改系统字体（改系统字体还会连带影响其它 App）。工程上「设置内字号」必须在 Theme 层一次生效，否则要改所有 `sp` 调用点（本项目约数百处）。
- **原理**｜在 `NirikoTheme` 内、`MaterialTheme` 外覆盖 `LocalDensity`：

  ```kotlin
  val base = LocalDensity.current
  CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * userScale)) { MaterialTheme(...) }
  ```

  坑：`Dp`↔`Sp` 互转也会跟着缩放，dock 高度/图标尺寸这类「必须稳定」的尺寸会漂。稳妥做法是给这类组件提供**未缩放的 density**（`LocalStableDensity`），或者只对文本相关 token 生效（更推荐：`NirikoTypography` 里每个 `fontSize` 乘 `userScale`，布局完全不动）。
- **效果**｜外观页多一行「字号：小/标准/大/特大（90/100/115/130%）」；列表标题、正文、设置行文字随之缩放，卡片高度与 dock 尺寸不变；系统已放大字体时按两者乘积显示（并提示）。
- **参考**｜`ui/theme/Type.kt`、`ui/theme/Theme.kt:226-256` [码]；`Density(fontScale)` 行为按 Compose 文档 [推断]（未逐字读 `Density.kt`）。
- **成本/风险**｜M｜风险：用 `LocalDensity` 覆盖法会让所有 `dp` 布局跟着变（上面的坑）；必须选「只缩放 Typography」这一条路并接受「`Modifier.size(16.dp)` 图标不缩放」的取舍。
- **优先级**｜P2

### 10.11 圆角 / 形状主题

- **现状**｜部分。`ui/theme/NirikoShapes.kt`：四档令牌 `Card=20.dp / Section=16.dp / Pill=999.dp(→percent 50) / Control=12.dp`，`rounded()`/`squircle(radius, smoothing=0.6)`，`SquircleCornerShape` 为自研几何（文件头声明算法来源 miuix 0.9.0，Apache-2.0，**非逐字拷贝**）。`squircle()` 目前**零调用点**。
- **提升**｜加一个形状档位 = 让四档令牌可选圆角/超椭圆/小圆角，用户侧只是「更 iOS / 更硬朗」；工程上因为令牌已收敛，改动是 `CardShape` getter 里一个 `when`。
- **原理**｜`enum ShapeMode{ROUNDED, SQUIRCLE, SHARP}` 进 `AppSettings`；`NirikoShapes.CardShape` 变成 `fun cardShape(mode) = when(mode){ ROUNDED -> rounded(Card); SQUIRCLE -> squircle(Card, 0.6f); SHARP -> RoundedCornerShape(4.dp) }`。玻璃折射的圆角来自 `Shape`（`lens` 的 `cornerRadii` 由 `shape` 推出），因此换形状时 lens/高光/内阴影自动跟随，无需改玻璃代码。
- **效果**｜外观页「形状」三选项；切到「平滑」时卡片圆角变成连续曲率（视觉上更 iOS），切到「硬朗」变成 4dp 小圆角；玻璃卡的高光边会跟着新圆角走。
- **参考**｜`ui/theme/NirikoShapes.kt`（`DefaultSmoothing = 0.6f`、`SquircleCorner`）[码]；`Kyant0/AndroidLiquidGlass@1.0.6:backdrop/build.gradle.kts` 依赖 `io.github.kyant0:shapes:1.2.0`（2.0.1 为 1.2.1），说明上游有独立 shapes 库 [码]。
- **成本/风险**｜S｜风险：`squircle` 从未被渲染过（零调用），首次启用必有视觉瑕疵（角部高光与阴影的钳制）；先只对 `Section` 档试用。
- **优先级**｜P2

### 10.12 玻璃 token 收敛

- **现状**｜**已收敛**，v1 判断需修正：全仓只有两个 token 对象 —— `ui/theme/GlassSurfaceTokens.kt`（搜索卡**垫层**：`searchBackingAlpha(expanded, glassEnabled)`，0.55/0.72 与降级 0.90/0.96，`MIN_EXPANDED_BACKING_ALPHA = 0.65`）与 `ui/theme/GlassChipTokens.kt`（压在图片上的**小件底**：`LIGHT_BASE_ALPHA=0.78`/`DARK_BASE_ALPHA=0.62`/`OPAQUE_BASE_ALPHA=0.92`，`MIN_CONTRAST=4.5`，`LUMINANCE_ADAPT_RANGE=0.16`，`contrastRatio`/`ensureReadable`）。v1 提到的 `LiquidGlassTokens` **不存在**；两者用途不同（垫层 vs 小件底），**不是重复维度**。
- **提升**｜把「不重复」这个结论落到测试里（`GlassSurfaceTokensTest`/`GlassChipTokensTest` 已存在），并补一条「同一 `GlassEffectLevel` 下两个对象给出的不透明度是否落在同一档位区间」的断言，防止以后各改各的。
- **原理**｜`GlassEffectLevel` 实际是一个三值旋钮，被至少 5 处独立消费：`GlassDecision`（是否折射）、`GlassSurfaceTokens`（垫层多实）、`ShimmerPolicy`（骨架是否扫光）、`GlassCard`（静态分支）、`MainActivity` 的 CompositionLocal。建议加一层 `GlassLevelProfile` 纯数据：`data class GlassLevelProfile(val refraction: Boolean, val minBackingAlpha: Float, val shimmer: Boolean)`，各消费者从 profile 取值而不是各自 `when(level)`。
- **效果**｜用户不可见；效果是改一档（例如把 REDUCED 定义成「有模糊」）时三处不再打架。
- **参考**｜`ui/theme/GlassSurfaceTokens.kt`、`GlassChipTokens.kt`（含 WCAG 判据与测试口径）[码]；`ui/components/ShimmerPolicy.kt` [码]。
- **成本/风险**｜S｜风险：把已有常量搬进 profile 会顺手改数值——必须逐字搬运（`GlassDecision.kt` 的教训：只搬运、不改语义）。
- **优先级**｜P2

### 10.13 顶栏/底栏密度档位

- **现状**｜无。设置行高/分组间距/栏高散落在 `ui/settings/SettingsItem.kt`、`SettingsGroup.kt`、`navigation/NirikoBottomBar.kt`、`ui/screens/Screens.kt` 的 dp 字面量里，无密度设置 key。
- **提升**｜小屏/单手用户能选「紧凑」多看两行；工程上把散落 dp 收成一组令牌，顺带统一三个顶级页的栏高。
- **原理**｜`enum DensityMode{COMPACT, COMFORTABLE}` + `object NirikoMetrics(val rowMinHeight: Dp, val sectionSpacing: Dp, val headerHeight: Dp, val cardAspect: Float)`，通过 `LocalNirikoMetrics` 提供；**不要**用 `LocalDensity` 缩 dp（会同时缩小触控区域，违反最小 48dp 触控目标）。触控区域在 COMPACT 下仍保持 ≥48dp，只压缩间距与内边距。
- **效果**｜设置里「列表密度：舒适/紧凑」；紧凑下同一屏多出约一行半，分组间距从 16 变 8，行高从 56 变 48。
- **参考**｜`ui/settings/SettingsItem.kt`/`SettingsGroup.kt` [码]；`ui/theme/NirikoShapes.kt` 的「令牌对象 + 测试」范式可直接套用 [码]。
- **成本/风险**｜M｜风险：密度是全局的，玻璃卡的 backdrop 捕获区域与 `TopFadePolicy.FADE_DISTANCE_DP` 都是常量，改密度后渐隐带高度需重新核对。
- **优先级**｜P2

### 10.14 列表视图的更多形态

- **现状**｜部分。`ui/library/LibraryViewStyle.kt` 三档 `LIST/GRID/GALLERY`；发现页另有 `data/model/search/DiscoveryLayout.kt` 的 `CARD/GRID`。无「时间轴视图」「按年份/状态分组」。
- **提升**｜长收藏（数百项）用户能按「年份」扫；工程上只是把已排序列表做 `groupBy` 并插入 sticky header，不改 ViewModel 之外的层。
- **原理**｜`LibraryViewStyle.TIMELINE` 新增一档；数据侧在 `CollectionViewModel` 里对现有 `items` 做 `groupBy { yearOf(airDate) }`，产出 `List<LibrarySection(header, items)>` 供 `LazyColumn` 用 `stickyHeader`。排序键复用现有 `SortOrder`，分组头只在「按时间」类排序下出现（按评分排序时隐藏，避免语义冲突）。
- **效果**｜视图切换里多一个「时间轴」图标；滚动时年份头吸顶（2024 / 2023…），每组头右侧显示「N 部 · 平均 ★x.x」。
- **参考**｜`ui/library/LibraryViewStyle.kt`、`data/model/search/DiscoveryLayout.kt` [码]；`ui/screens/Screens.kt:423-390` 的三视图 `when` 分支 [码]。
- **成本/风险**｜S｜风险：`LazyColumn` 的 `stickyHeader` 与 `TopFadePolicy` 的 index+offset 判据会互相干扰（首项是 header 时 offset 语义不同），要一并核对渐隐。
- **优先级**｜P2

### 10.15 深色模式对比度优化

- **现状**｜部分，且比 v1 记录的好：`GlassChipTokens` 已有**机械判据** —— `MIN_CONTRAST = 4.5f`（WCAG AA 小字）、`contrastRatio`、`ensureReadable`（保留色相只调明度，把品牌色压/提到达标），并按「压在纯黑图/纯白图」两个最坏情况合成后判定，配套 `GlassChipTokensTest`。**没有**的是：正文/设置页/玻璃卡内文字/OLED 纯黑分支的全量对比度验收。
- **提升**｜深色 + OLED 纯黑下正文不再发灰。工程上把已有判据从「小件」推广到「文字 token」，一条 JVM 测试覆盖 4 种子色 × 亮/暗 × OLED。
- **原理**｜`fun contrastRatio(fg: Color, bg: Color): Float` 已有，扩展成 `ThemeContrastAudit`：对 `ThemePalettes` 每个种子色 × `buildDarkScheme(seed, oledDark=true/false)` 的 `ColorScheme`，断言 `onSurface/surface ≥ 4.5`、`onSurfaceVariant/surface ≥ 3.0`（大字 3:1）、`onPrimary/primary ≥ 4.5`。玻璃卡内文字的特殊性：底色是半透明，要先用 `composite(base, wallpaperLuminance∈{0.05,0.95})` 取最坏值再算。
- **效果**｜深色下 `bodySmall`（12sp 灰字）在 OLED 纯黑上对比度从可能的 3.x 提到 ≥4.5；这条没有界面变化，只有「不再眯眼」。
- **参考**｜`ui/theme/GlassChipTokens.kt`（`contrastRatio`/`ensureReadable`/`MIN_CONTRAST`）[码]；`ui/theme/Theme.kt:236-248`（`buildDarkScheme(seed.dark, oledDark)`）[码]。
- **成本/风险**｜S｜风险：调 onSurfaceVariant 会改变全站灰字观感（设计上是有意为之的层级）；建议只改达不到 3:1 的那几个组合。
- **优先级**｜P1

### 10.16 动效档位与系统设置联动

- **现状**｜部分且**单向**。`AppSettings.reduceMotion = false`（`AppSettings.kt:87`）；`MainActivity.kt:183-198` 读 `Settings.Global.ANIMATOR_DURATION_SCALE == 0` 得 `systemAnimatorOff`，然后 `reduceMotion = settings.reduceMotion || systemAnimatorOff` 传给 `NirikoTheme` → `LocalReduceMotion`（`ui/animation/NirikoAnimation.kt:65`）；`ShimmerPolicy.shouldAnimate(reduceMotion, systemAnimatorOff, glassEffect)` 还额外吃一个 `systemAnimatorOff`（`ui/components/StateComponents.kt:80`）。没有反向写入，也没有「跟随系统」这个状态。
- **提升**｜用户在设置里看到「减少动态效果：开」时不再困惑「我明明没开」（其实是系统关了动画）；工程上把「系统关」与「用户关」分开存，才谈得上覆盖矩阵（见 12 域）。
- **原理**｜`enum MotionSetting{SYSTEM, ON, OFF}` 替代 boolean；解析函数 `fun resolve(setting, systemAnimatorOff) = when(setting){ ON->true; OFF->false; SYSTEM->systemAnimatorOff }`（纯函数、可穷举 6 组）。设置页显示解析结果 + 来源（「已由系统动画设置关闭」）。迁移：旧备份里的 `reduceMotion=true` 映射为 `ON`，`false`→`SYSTEM`（而不是 `OFF`，否则老用户会被系统设置重新绑住）。
- **效果**｜设置页开关变三态单选；系统关闭动画时该行显示「跟随系统（当前：已关闭）」，用户仍可强制打开（对晕动症用户是反例，需要保留覆盖）。
- **参考**｜`MainActivity.kt:183-198`、`NirikoAnimation.kt:65-69`、`ShimmerPolicy.kt:26-30`、`data/backup/BackupManager.kt:437/718`（backup 里已有 `reduceMotion` 字段）[码]。
- **成本/风险**｜S｜风险：三态迁移写错会让老用户「突然全动画」或「突然没动画」；迁移公式与备份兼容必须各有单测。
- **优先级**｜P1

### 10.17 设置项搜索

- **现状**｜无。设置是 8 个分类（`ui/settings/SettingsCategory.kt` 的 `SettingsCategory` 枚举：APPEARANCE/LIBRARY/SEARCH/DATASOURCE/…）+ 11 个二级页（`ui/settings/pages/*.kt`），窄屏单列卡片、宽屏两栏（`AdaptiveSettingsPane.kt`）。无搜索入口。
- **提升**｜用户找「壁纸柔化」不用点进 3 层。工程上需要一张可测的索引表——这恰好也能给「设置项可达性」做回归（新加设置忘了进索引会被测试抓）。
- **原理**｜Mihon 的完整范式（`[码]`）：每个设置页实现 `SearchableSettings` 的 `getPreferences(): List<Preference>`；搜索页里有 `val settingScreens = listOf(SettingsAppearanceScreen, ..., SettingsAdvancedScreen)`，`getIndex()` 把每页拉平成 `SettingsData(title=stringResource(screen.getTitleRes()), route=screen, contents=screen.getPreferences())`；`search()` 只搜 `visible && title.isNotBlank()` 的项，`PreferenceGroup` 标题命中给 group 本身、成员命中用 group 标题做面包屑，`title/subtitle` 用 `contains(searchKey, true)`，最后 `.take(10)`；点击时 `SearchableSettings.highlightKey = result.highlightKey` 再 `navigator.replace(route)`，目标页消费 `highlightKey` 做滚动 + 高亮闪烁。搜索读取用 `produceState + snapshotFlow`，注释明确「查询在 composition 外读，避免每次按键重建索引」。
  本项目适配：设置页不是 Preference 框架而是手写 Compose（`SettingsItem.kt`/`SettingsGroup.kt`），因此索引改为纯数据表 `SettingsSearchIndex`：`(pageKey, title, keywords, route, anchorId)`，字符串命中（中文 + 拼音首字母，复用已有 pinyin4j）；命中后 `scrollTo(anchorId)` + 复用 `SettingsGroup` 的动画做高亮。
- **效果**｜设置主页顶部一个搜索框（全宽玻璃条，与 `UniversalSearchBar` 同款）：输入「壁纸」列出「外观 › 壁纸柔化」「外观 › 壁纸轮换」等，点击跳到对应二级页并高亮 1.2 秒；空结果显示「没有找到这个设置」。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/presentation/more/settings/screen/SearchableSettings.kt` [码]（`interface SearchableSettings{ getTitleRes(); getPreferences(); highlightKey }`）；`mihonapp/mihon@main:app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSearchScreen.kt` [码]（`getIndex()`/`search()`/`settingScreens`/`.take(10)`/`highlightKey`）；本项目 `ui/settings/{SettingsCategory,SettingsGroup,SettingsItem,AdaptiveSettingsPane}.kt` [码]。
- **成本/风险**｜M｜风险：手写 Compose 设置页没有「Preference 自描述」，索引表会与真实设置项脱节；缓解：索引表放在同一文件里紧邻 UI 定义，并加「每个 `SettingsPickerRow` 必须带 anchorId」的 lint 式测试。
- **优先级**｜P1

## 10.18 本域新增候选（发散）

### 10.18 外观快照与一键回滚

- **现状**｜无 · 新提出｜改了一套外观（主题色 + 玻璃档 + 壁纸 + 形状）后想回到上一套，目前只能一项项改回；`BackupManager` 是全量 JSON 备份，粒度太粗。
- **提升**｜用户能「试一下这套配色，不行一键回去」；工程上省掉「改坏了说不清哪项」（备份全量恢复会覆盖数据）。
- **原理**｜`AppearanceSnapshot` 只含外观字段（`ThemeMode/dynamicColor/oledDark/customSeedColor/wallpaper*/cardGlassLevel/glassEffect/shapeMode/splashEnabled`），序列化成一行 JSON 存 DataStore 的最近 5 个环；应用 = 逐字段 `SettingsDataStore` 写回（不碰收藏/同步）。自动快照点在「主题包安装前」「主题色改名前后」。
- **效果**｜外观页底部「最近修改」列表，点一条即恢复；恢复前弹一次玻璃 Snackbar 可撤销。
- **参考**｜`data/backup/BackupManager.kt`（字段清单现成）[码]；`data/themepack/ThemePackManager.buildApply` [码]。
- **成本/风险**｜M｜风险：字段清单会随设置增长而漏（必须由 `AppSettings` 单一真源派生，不能手抄）。｜**优先级** P2
- **为什么是 Niriko**：本项目外观字段有 20+ 个（壁纸/玻璃/形状/主题包/开屏），是全应用最容易「调坏」的地方，且纯本地无云端可回滚。

### 10.19 视频壁纸的电量/前台策略

- **现状**｜无 · 新提出｜`ui/wallpaper/WallpaperHost.kt` + media3 ExoPlayer 播视频壁纸，无「省电模式暂停 / 息屏暂停 / 低电量降级」策略。
- **提升**｜用户开着视频壁纸不再看到「应用耗电 18%」；工程上省掉后台无效解码（`docs/performance-audit.md` 已指出叠加视频壁纸时 backdrop 捕获更重）。
- **原理**｜订阅 `PowerManager.isPowerSaveMode` + `BatteryManager` 广播（`ACTION_POWER_SAVE_MODE_CHANGED`），省电模式 → 视频壁纸退化为静态首帧（`ExoPlayer.setVideoSurface(null)` + 保留封面 Bitmap）；`ProcessLifecycleOwner` 的 ON_STOP → `player.pause()`。判定写成纯函数 `videoAllowed(playSave, screenOn, appForeground): Boolean` 便于穷举。
- **效果**｜省电模式下壁纸变静帧（无提示，避免打扰）；退出应用立即停止解码，返回时从当前位置续播。
- **参考**｜本项目 `ui/wallpaper/WallpaperHost.kt`（`SDK_INT >= S` 的模糊分支）[码]；`androidx.media3` 的 `ExoPlayer.pause`/`setVideoSurface` [推断]。
- **成本/风险**｜S｜风险：视频壁纸的暂停/续播在 surface 重建时会闪黑一帧。｜**优先级** P1
- **为什么是 Niriko**：壁纸是本项目的核心视觉卖点（玻璃折射需要壁纸才有意义），视频壁纸是它的重负载变体，必须自己兜住电量。

### 10.20 玻璃实时性能回退

- **现状**｜无 · 新提出｜降级只有「用户手动档」与（10.1 之后的）「设备静态档」，没有「运行时发现掉帧就自动降一档」。`docs/performance-audit.md` 的 P0 正是每帧 `drawBackdrop` 捕获。
- **提升**｜中端机在 120Hz 上保持流畅而不是整段掉到 45fps；工程上把「降级」从发布期决策变成运行时闭环。
- **原理**｜在 `MainActivity` 的 `Choreographer.FrameCallback` 或 Compose `withFrameNanos` 采样帧间隔，滑动窗口 60 帧算 p90；p90 > 20ms 连续 2 个窗口 → 下发 `GlassEffectLevel.REDUCED`（仅本次会话，不写 DataStore）；恢复需连续 300 帧 p90 < 14ms。冷却时间 30s 防抖。纯函数 `nextLevel(current, p90, windows)` 可测。
- **效果**｜用户无感（只是不再卡），日志/诊断页显示「玻璃已自动降档 1 次（原因：p90 22ms）」。
- **参考**｜本项目 `docs/performance-audit.md`（gfxinfo framestats 方法）[码]；`ui/components/GlassSectionCard.kt:124` 的「静止时把 uniform 更新间隔拉到 500ms」已是同类思路 [码]。
- **成本/风险**｜M｜风险：自动降档会造成「同屏有的卡降了有的没降」的闪烁，必须整页统一降。｜**优先级** P2
- **为什么是 Niriko**：全站真折射是本项目独有负载，通用性能库无法替我们判断「这一档玻璃该不该关」。

### 10.21 主题包安装前预览

- **现状**｜无 · 新提出｜`ThemePackManager.import` 直接落地到 `filesDir/themes/<id>/` 并写 `activeThemePackId`，用户装完才知道长什么样。
- **提升**｜装包前能看「配色/壁纸/开屏」三宫格预览；工程上省掉「装了又删」的目录残留（`delete` 已有，但用户常常懒得删）。
- **原理**｜新增 `ThemePackManager.preview(uri): ThemePackPreview`（解压到 `cacheDir` 临时目录、解析 manifest、用 HCT 生成两套 `ColorScheme`、解码壁纸缩略图），返回后由 UI 渲染三格；用户确认才 `commit(uri)`（移动到 filesDir + 写设置），取消则删缓存。
- **效果**｜从文件管理器「打开方式 → Niriko」后先出现全屏预览（顶部包名/作者/版本，下方三格），底部「应用/取消」。
- **参考**｜`data/themepack/ThemePackManager.kt`（`import`/`parseAndValidate`/`buildApply`）[码]；`ui/settings/pages/ThemePackSection.kt` [码]。
- **成本/风险**｜M｜风险：zip 解压要防 `../` 路径穿越（`isSafeEntry` 已有一半），预览路径必须复用同一校验。｜**优先级** P2
- **为什么是 Niriko**：主题包是唯一「外部输入 + 会改全局外观」的入口，安装前预览等于给外部内容加了一道门。

### 10.22 每页独立种子色

- **现状**｜无 · 新提出｜每页可独立壁纸（`wallpaperLibraryUri/DiscoverUri/StatsUri/SettingsUri`），但主题色是全局一个（`customSeedColor`）。
- **提升**｜壁纸换页时配色跟着换（发现页暖色、统计页冷色），玻璃卡取色更协调；工程上 `SeedColorScheme` 已是纯函数，换种子只是一次重算。
- **原理**｜`AppSettings` 增 `pageSeedColors: String`（CSV：`library=#2E7D32,stats=#1565C0`）；`MainPager` 每个页面外层包一层 `NirikoTheme(customSeedColor = pageSeed ?: globalSeed)`。成本注意：`SeedColorScheme` 生成 HCT 调色板不算便宜，需 `remember(pageSeed)` 缓存，切换动画时不要每帧重算。
- **效果**｜滑到统计页时状态栏/强调色渐变到该页配色（约 200ms crossfade）。
- **参考**｜`ui/theme/SeedColorScheme.kt`、`ui/theme/Theme.kt:236-248`、`data/settings/AppSettings.kt` 的「每页壁纸」四字段 [码]。
- **成本/风险**｜S｜风险：两套配色同时存在时，跨页共享元素（封面→详情）会在转场中跳色。｜**优先级** P2
- **为什么是 Niriko**：本项目已经有「每页壁纸」这个罕见设计，配色跟不上反而更割裂。

### 10.23 外观设置子集导出

- **现状**｜部分 · `BackupManager` 是全量 JSON（含收藏），没有「只导出外观」。｜想把自己调好的配色分享给朋友，只能整份备份（含隐私数据）。
- **提升**｜一键导出「外观设置片段」（几 KB JSON），朋友导入即复刻；工程上复用主题包的 apply 通路，不必新增导入器。
- **原理**｜`AppearanceSubsetCodec`：从 `AppSettings` 只取白名单字段 → JSON → 也可直接包成最小 `.nirikotheme`（`colors/wallpaper/splash`，无 `pages`），复用 `ThemePackManager.export` 的 zip 逻辑。导入走现有主题包导入（同一套校验）。
- **效果**｜外观页「导出我的外观」生成一个 `.nirikotheme`（<200KB，壁纸可选是否包含）；对方用「打开方式」导入。
- **参考**｜`data/themepack/ThemePackManager.export`、`ThemePackModels.kt`、`data/backup/BackupManager.kt:434-437`（外观字段清单）[码]。
- **成本/风险**｜S｜风险：两份字段清单（备份 / 外观子集）会漂移，需由同一个 `AppearanceFields` 常量派生。｜**优先级** P2
- **为什么是 Niriko**：主题包生态已存在，缺的是「从自己机器上产出一个包」的低摩擦路径。

## 10.A 本域新增参考库

| 仓库 | 许可 | star | 语言 | 为何与本项目相关 | 已验证文件 |
| --- | --- | --- | --- | --- | --- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Apache-2.0（build.gradle.kts 的 pom license 块） | 4194（截至 2026-10，Lead 核对；默认分支 `kmp`） | Kotlin / CMP | 本项目玻璃上游；1.0.6 已锁，2.0.1 的 KMP 化结论直接决定要不要升级 | `backdrop/build.gradle.kts`（1.0.6 与 2.0.1）、`gradle/libs.versions.toml`（两版）、下述 backdrop 路径为 2.0.1/kmp 布局（1.0.6 同文件位于 `backdrop/src/main/java/com/kyant/backdrop/…`）、`backdrop/src/commonMain/kotlin/com/kyant/backdrop/backdrops/{Backdrop.kt,CanvasBackdrop.kt,CombinedBackdrop.kt,EmptyBackdrop.kt,LayerBackdrop.kt,LayerBackdropModifier.kt}`、`backdrop/src/commonMain/kotlin/com/kyant/backdrop/DrawBackdropModifier.kt`、`backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/{Blur.kt,ColorFilter.kt,Lens.kt,RenderEffect.kt}`、`backdrop/src/commonMain/kotlin/com/kyant/backdrop/highlight/{Highlight.kt,HighlightModifier.kt,HighlightStyle.kt}`、`backdrop/src/commonMain/kotlin/com/kyant/backdrop/shadow/{InnerShadow.kt,InnerShadowModifier.kt,Shadow.kt,ShadowModifier.kt}`、demo `catalog/src/main/java/com/kyant/backdrop/catalog/components/{LiquidBottomTabs.kt,LiquidBottomTab.kt}` 与 `catalog/src/main/java/com/kyant/backdrop/catalog/utils/{DampedDragAnimation.kt,InteractiveHighlight.kt,DragGestureInspector.kt}` |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | Apache-2.0 | 2.6k（截至 2026-10，shields） | Kotlin / CMP | 「模糊分档」与 `HazeSampling` 的分级建模方式，是 10.1/10.4 的直接参照（本项目用 kyant，不引 haze） | `haze/src/commonMain/kotlin/dev/chrisbanes/haze/Haze.kt`（`rememberHazeState/HazeState/Modifier.hazeSource`）、`haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffect.kt`（`HazeSampling{FullResolution,Adaptive,Fixed}`、泛型 `Modifier.hazeEffect(factory,input,style,sampling)`）、`haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInput.kt` |
| [skydoves/Cloudy](https://github.com/skydoves/Cloudy) | Apache-2.0 | 未取到（shields 超时） | Kotlin / CMP | 三档模糊降级 `BlurTier(Gpu/Cpu/Scrim)` + `CloudyState`（`Applied`/`Captured`），是「设备能力 → 降级」最干净的参考；本项目 `libs.versions.toml` 里已声明 `cloudy = "1.0.0-alpha01"` 但**主源码零调用**（待清理） | `cloudy/src/androidMain/kotlin/com/skydoves/cloudy/internal/BlurTier.android.kt`、`cloudy/src/androidMain/kotlin/com/skydoves/cloudy/internal/BlurStrategy.android.kt`、`cloudy/src/androidMain/kotlin/com/skydoves/cloudy/internal/LegacyBackdropBlurrer.kt`、`cloudy/src/androidMain/kotlin/com/skydoves/cloudy/internal/LegacyForegroundBlurrer.kt`、`cloudy/src/commonMain/kotlin/com/skydoves/cloudy/Cloudy.kt`、`cloudy/src/commonMain/kotlin/com/skydoves/cloudy/CloudyState.kt`、`cloudy/src/commonMain/kotlin/com/skydoves/cloudy/CloudyBackground.kt` |
| [mihonapp/mihon](https://github.com/mihonapp/mihon) | Apache-2.0 | 24k（截至 2026-10，shields） | Kotlin | 设置项搜索的完整可抄范式（接口 + 索引 + 面包屑 + `highlightKey` 跳转高亮） | `app/src/main/java/eu/kanade/presentation/more/settings/screen/SearchableSettings.kt`、`.../SettingsSearchScreen.kt`（`getIndex`/`search`/`settingScreens`/`.take(10)`） |
| [patrykandpatrick/vico](https://github.com/patrykandpatrick/vico) | Apache-2.0（项目 `libs.versions.toml` 注释口径） | 未取到（shields 超时） | Kotlin / CMP | 本项目 `ui/stats/NirikoBarChart.kt` 与 `ui/subject/EpisodeRatingChart.kt` **已经在用** vico 3.2.0；`CartesianChartHost` 的 `animateIn`/`animationSpec` 默认值决定了图表动效是否受 `reduceMotion` 约束（见 12 域矩阵） | `vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/CartesianChartHost.kt`（`animationSpec: AnimationSpec<Float>? = defaultCartesianDiffAnimationSpec`、`animateIn: Boolean = true`） |
| androidx/compose-samples | Apache-2.0 | — | Kotlin | 本机 CDN 取不到（`gcore.jsdelivr.net/gh/androidx/compose-samples@main/README.md` 与 `data.jsdelivr.com/.../flat` 均 404），**本轮未取得任何源码证据**，因此 10/12 域未引用它 | 无 |

> 注：本项目 `app/build.gradle.kts` 已声明 `cloudy = "1.0.0-alpha01"`（`gradle/libs.versions.toml`）但 `app/src/main/java` 内 grep 不到任何 `com.skydoves.cloudy` 引用——**未使用依赖**，建议在 10.1 落地时决定「用它替 `BlurredCover`」还是「从 catalog 删除」。

## 计数自检

- 汇总表 23 行 = 详解 17 条（10.1–10.17）+ 新增候选 6 条（10.18–10.23）。✔
- 每条参考均带 `owner/repo@branch:path` 与 `[码]/[README]/[推断]`（本文件 `[码]` 为主，仅 WorkManager `UPDATE` 语义、Compose `Density(fontScale)`、media3 接口、Live2D 成本标 `[推断]`）。✔
- star 数：haze 2.6k、mihon 24k（shields 实取）、Kyant0 4194（Lead 核对）；其余写「未取到」。✔
- 上游 Kyant0 的目录证据走 tag（`@1.0.6`/`@2.0.1`）与默认分支 `kmp`（`@kmp/flat`，185 文件，与 2.0.1 一致）。✔
- 与 v1 的修正：10.9（「单一版式」→已有两种）、10.12（`LiquidGlassTokens` 不存在，两类 token 用途不同）、10.15（小件已有 WCAG 4.5 机械判据）、10.2（升级判定已完成，结论锁 1.0.6）。✔
