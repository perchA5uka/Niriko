# Niriko 动效审计与优化方案（plans 索引）

交付物对应关系：
- 用户诉求 ① = 在 AOSP 与 Material Components for Android 中找可跟进内容（预测性返回为重点） → [01-predictive-back-android-mdc-followups.md](01-predictive-back-android-mdc-followups.md)
- 用户诉求 ② = 本应用动效优化方案 → 02 / 03 / 04 / 05 四份可执行方案

## 文件清单

| 文件 | 内容 |
|---|---|
| [01-predictive-back-android-mdc-followups.md](01-predictive-back-android-mdc-followups.md) | 交付物①：AOSP/AndroidX 可跟进清单（预测性返回落地 3 步 + MDC 定位 + material3 立即可用/等 1.5.0） |
| [02-motion-phase-performance.md](02-motion-phase-performance.md) | H2 阶段问题：P1 iOS 搜索组件、P2 底栏隐藏、P3 Pager 指示器、P4 玻璃控件、P5 shimmer 上限、P6 RevealOnScroll 错峰、P7 局部重绘 |
| [03-gesture-physics-interruptibility.md](03-gesture-physics-interruptibility.md) | H3/H4：G1 跟手 snapTo、G2 initialVelocity、G3 MutatorMutex、G4 撤销等值门、G5 sheet 初速、G6 退场动画、G7 pop 过渡、G8-G10 consume/slop、G11 sheet 图层化、G12 dragOffsetX 跳变、G13 待真机 |
| [04-tokens-and-reduce-motion.md](04-tokens-and-reduce-motion.md) | H5 reduce-motion 覆盖（G1-G20 + 落地顺序）、M1 令牌内聚（T1-T20 + NirikoSprings 建议）、C 注释/文档脱节 |
| [05-platform-modernization-and-guardrails.md](05-platform-modernization-and-guardrails.md) | H 触感词表（3 处只有 1 处语义错）、O overscroll（结论：不做）、S 共享元素/animateBounds、T 单测与 CI 报告 |

## 统一判定口径（所有发现都按这个口径分级）

判「动画值在哪一阶段被读取」，而不是「有没有动画」：
- 在 **composition** 读动画/手势进度值 → 每帧重组（若用于 size/offset 再叠加每帧 measure/layout）；
- 在 `Modifier.offset { }` / `Modifier.layout { }` lambda 读 → 每帧 measure/layout，不重组；
- 在 `Modifier.graphicsLayer { }` / `drawWithContent` / `Canvas` 里读 → 只重绘，最优。

健康范本（改法参照）：ui/animation/RevealOnScroll.kt:81-86（graphicsLayer 读 3 个动画值）、ui/adaptive/AdaptiveInsets.kt:30-56（layout lambda 延迟读 hideFraction）、ui/search/TrendingSection.kt:148、ui/search/DiscoverPosterPane.kt:91、ui/subject/SubjectSearchScreen.kt:102（snapshotFlow 读滚动状态）、ui/components/StateComponents.kt:75-89（shimmer 单一入口 + ShimmerPolicy 裁决）。

## 杠杆排序总表

| 级别 | # | 问题 | 载体 | 出处 |
|---|---|---|---|---|
| HIGH | H1 | 预测性返回完全未接入（manifest 无 enableOnBackInvokedCallback；全仓无 PredictiveBackHandler） | 01 §A | 手势审计 #9；用户点名 |
| HIGH | H2 | 组合期读动画/手势进度值 → 每帧重组 + 重测（iOS 搜索组件 5 个 animateDpAsState + dragOffsetX、底栏 hideFraction、顶层 Pager 指示器、玻璃开关/滑块） | 02 §P1-P4 | 性能审计 #1-#8 |
| HIGH | H3 | 底栏手势物理断裂：跟手用 animateTo 追手（非 snapTo）、速度只喂形变、`MutatorMutex` 空转、release 等值才回弹 | 03 §G1-G4 | 手势审计 #1-#4 |
| HIGH | H4 | 非模态 sheet：松手速度不进位移（无 initialVelocity）、返回键关闭无退场动画 | 03 §G5-G6 | 手势审计 #6-#7 |
| HIGH | H5 | 减少动态效果只覆盖 8/170 处动画（33 个文件） | 04 §A | 无障碍审计 G1-G20 |
| MEDIUM | M1 | 动效令牌不内聚：9+ 套按压/揭示弹簧、9 个自造 tween 时长、令牌层零调用（pressSpring/panelSpring/countTween/tabFade） | 04 §B | 令牌审计 T1-T20 |
| MEDIUM | M2 | 二级页无 popEnter/popExit → 返回沿用前进规格（同向 0.92 缩放），enterFromRight/exitToRight 零引用 | 03 §G7 | 手势审计 #8 |
| MEDIUM | M3 | shimmer 并发无上限：统计页加载态约 60 个扫光、Discover 首屏 24 个 | 02 §P5 | 性能审计 #9-#10 |
| MEDIUM | M4 | RevealOnScroll 错峰失效（delayMillis 全仓 0 调用点） | 02 §P6 | 性能审计 #25 / 我的取证 |
| MEDIUM | M5 | 手势不 consume / 无独占：底栏 DragGestureInspector、GlassSlider、TouchGlow 按住滚动 | 03 §G8-G10 | 手势审计 #5/#15/#16/#18 |
| MEDIUM | M6 | sheet 每 delta 起协程 snapTo + `.offset{}` 每帧重排整块玻璃面板 | 03 §G11 | 性能审计 #11/#15 / 手势审计 #14 |
| MEDIUM | M7 | **material3 组件与自研组件各用一套弹簧**：ui/animation/NirikoMotionSpecs.kt 只是普通 object，未作为 `MotionScheme` 传进 ui/theme/Theme.kt:250-258 的 `MaterialTheme`（1.4.0 的 `getMotionScheme()` 已 public、接口可自定义实现） | 01 §C-1b / 04 §B-附 | MDC 子代理回补 |
| LOW | L1 | 局部重绘成本：TimelineSection 组合期读 Pager 偏移、InnerShadow 每帧 record+BlurEffect、LiquidBottomTabs 双层 content 组合、GlassCard Brush remember key、SettingsGroup morph 半径 | 02 §P7 | 性能审计 #12/#17/#18/#21/#24 |
| LOW | L2 | haptics 只用 LongPress（3 处），未用 Compose 1.10 的新词表（Confirm/SegmentTick/ToggleOn/ToggleOff/GestureThresholdActivate） | 05 §H | 我的一手取证（aar 常量） |
| LOW | L3 | 未用平台级拉伸 overscroll（OverscrollFactory/StretchOverscrollNode），自研无 fling | 05 §O | 我的一手取证（aar 类） |
| LOW | L4 | 9 处 sharedElement 未传 boundsTransform/spec（默认 spring），未用 animateBounds/LookaheadScope/ResizeMode（SharedTransition 自 1.10.3 起已 stable，**无版本迁移工作**，纯观感/守卫改进） | 05 §S | 无障碍审计 G3-G4；aar 类证据 + AOSP 子代理回补 |
| LOW | L5 | 注释/文档与代码脱节（NirikoRadarChart.kt:56 说 AnimDurationLong、实际 spatialSlow；m3-expressive-comparison.md:75 说 39 处引用） | 04 §C | 令牌审计 T20 |
| LOW | L6 | 未对齐 M3 Expressive 弹簧（0.8/380、0.6/800、0.8/200…）：改常量即可提前吃到，但属**观感改版需用户先选** | 04 §B-附 | MDC 子代理回补（`ExpressiveMotionTokens` 字节码） |
| LOW | L7 | 冗余的 `@OptIn(ExperimentalSharedTransitionApi)`：animation 1.10.5 字节码里 `sharedElement`/`sharedBounds` 已无该注解 | 01 §D | 我的 javap 实测 |

## 覆盖缺口声明（必读）

1. 本次审计环境**无法联网**：`web_search`/`read_page` 全部失败（firecrawl keyless 429，retry_after≈13821s；`web_fetch` TypeError: fetch failed），因此 01 中「AOSP/AndroidX/MDC 版本时间线」以**本机 Gradle 缓存的 AAR/字节码实测 + 模型知识**为依据，凡未经实测的版本号均标注 ⚠️待联网核对。
2. **两个调研子代理均已回补交付（2026-10-01）**：
   - **AOSP/AndroidX（id 2b747d9a-738e-411b-99cb-8f8e776e8363）**：证据 = dl.google.com Maven 元数据 + `-sources.jar` 实测 + 本地 AOSP `android-36.1` 源码 + android-35/36.1 `android.jar` 的 javap 符号对比 → 结论已并入 01 §A-1/§A-2/§A-6/§D 与 05 §S。
   - **MDC/material3（id 2376e991-7205-40f8-8612-4d7638280b62）**：证据 = MDC 仓库 README/releases + 官方 Maven 元数据/POM + material3 AAR 字节码 javap → 结论已并入 01 §B/§C-1b/§C-2 与 04 §B-附。**MDC 已确认进入维护模式（README `[MAINTENANCE MODE]`，最后功能版本 1.14.0 / 2026-05-13）**，其 transition 家族（MaterialContainerTransform/MaterialSharedAxis/MaterialFadeThrough）**没有**拿到预测性返回支持。
   - **两份报告的一处冲突已由我仲裁**：MDC 子代理称「共享元素仍 `@ExperimentalSharedTransitionApi`、未稳定」，AOSP 子代理称「该注解已不被任何 API 使用」→ 我用 Android Studio jbr 的 `javap` 直接反编译 animation-android **1.10.5** aar 的 `SharedTransitionScope`：**整个 class dump 对 `ExperimentalSharedTransitionApi` 命中 0**，而注解类本身仍在（`@RequiresOptIn`、BINARY retention）→ **采信 AOSP 侧**，现有 `@OptIn` 属冗余可清理（记在 01 §D）。
   - 两份报告的共同不确定性：本机无外网、developer.android.com 未逐页核对；alpha 线（material3 1.5.0-alpha29、animation 1.13.0-alpha03）的公开 API 名称仍在变动。
3. 无模拟器/真机：所有「每帧成本」判断为静态阶段分析，标注「需真机确认」的条目未做 profiling。


---
**基线**：git HEAD `97ce7df`（工作树有 20+ 未提交改动）。审计日期 2026-10-01。约束：**不改动任何源码**。
**工程口径**：com.otakup.niriko / Kotlin 2.3.10 / AGP 8.13.2 / composeBom 2025.12.01（Compose UI 1.10.x、material3 1.4.0）/ navigationCompose 2.8.5 / activityCompose 1.9.3 / targetSdk 34 / minSdk 26。
**验收基线**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest`（P0 批次时点为 78 个测试类 / 696 用例 / 0 失败）；涉及帧预算的项另需真机 `adb shell dumpsys gfxinfo com.otakup.niriko framestats`。
**不得再报的刻意取舍**（docs/m3-expressive-comparison.md:108、docs/dock-kyant-migration-plan.md:1-36、docs/ui-upgrade-plan-2026.md:154/166/256/264/273、docs/performance-audit.md 全篇）：NirikoBottomBar.kt:49 与 NirikoNavSuite.kt:98 的 180ms tween；SubjectDetailScreen.kt:442-445 的 350ms 材质渐显；pressSpring/appearSpring/panelSpring/tabFadeIn/tabFadeOut 保留；ui/bottombar/** 原样搬迁 kyant（其中 spring(1f,1000f,…)=DampedDragAnimation.kt:30 等为上游取值）；shimmer 静态退化、dock 恢复动画跳过、布局切换动画跳过；Stats 图表静态化、CoverImage 移除外层 crossfade、详情页 hero 大图 1080×1440；performance-audit.md 的 P0/P1/P2 性能结论（本批不重复，只补其未覆盖的「动画阶段」维度）。
