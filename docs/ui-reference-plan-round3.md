# Niriko 参考项目落地计划 · 第三轮

> **依据**：用户 m00240 —— ① 参考 open-ani/animeko 的**探索页海报视图 UI**（本项目发现页海报视图尚未实现；**卡片列表视图与代码逻辑不变**）；② easybangumiorg/EasyBangumi **不再需要**（其 UI 不及本项目）；③ 需要一份**详细的参考计划**；④ UI 改动部分按 `docs/detail-page-prototype.html` 的风格做原型。
>
> **原型**：`docs/discover-poster-prototype.html`（4 屏：现状卡片视图 / 改后海报视图 / 海报卡细节 / 切换按钮与列数）
>
> **本轮性质**：计划书 + 原型，**不改任何 Kotlin 源码、不改 gradle 版本目录**。

---

## 〇 本轮结论（先看这里）

1. **EasyBangumi 出局**：仓库、许可、代码全部移出参考池（用户判定其 UI 不及本项目；GPL-3.0 也不可抄）。参考项目从 10 个收敛为 **9 个**。
2. **animeko 只取一件事**：探索页**海报视图的 UI 形态**。它的数据层、LazyPaging 分页、云收藏同步、电视剧集进度逻辑**一律不参考**。
3. **落点是「新增第三种布局」**，而不是改造现有两种：发现页布局从二态（卡片 / 宫格）扩为三态（**卡片 / 宫格 / 海报**），新增 `DiscoveryLayout.POSTER`。
4. **零改动的硬约束**：`ui/search/TrendingSection.kt`（卡片列表视图）、`ui/search/DiscoverGridPane.kt`（宫格视图）、数据层与分页逻辑、`gradle/libs.versions.toml` **一行不改**（仅两处 `private` → `internal` 可见性调整，不改渲染）。
5. **P0 零新依赖**：海报视图全部复用本项目已有组件（`CoverImage` / `RatingBadge` / `FavoriteBadge` / `PosterGridSkeletonCard` / `NirikoGridColumns` / `RevealOnScroll` / `reportBottomBarScroll`）。
6. **实现状态（m00429 批准后已落盘）**：P0 已实现并通过 `:app:compileDebugKotlin` + `:app:testDebugUnitTest`（72 类 / 635 用例 / 0 失败，零编译告警）。新增 `ui/search/DiscoverPosterPane.kt`、`ui/search/DiscoverPosterCard.kt`、`test/java/com/otakup/niriko/data/model/search/DiscoveryLayoutTest.kt`；`README.md` 单测口径同步为 635 / 72。视觉（真机观感）尚未人工验收。
7. **实现状态（P1，m00558「直接继续」后已落盘）**：R2 + R3 已实现并通过 `:app:compileDebugKotlin` + `:app:testDebugUnitTest`（**73 类 / 645 用例 / 0 失败**）。新增 `ui/components/GlassSource.kt`、`data/wallpaper/{WallpaperLibraryEntry,WallpaperLibraryFiles,WallpaperRotation,WallpaperRotationWorker,WallpaperRotationScheduler}.kt`、`ui/settings/pages/WallpaperLibraryScreen.kt`、`test/java/com/otakup/niriko/data/wallpaper/WallpaperLibraryTest.kt`（10 用例）；`gradle/libs.versions.toml` 未改，**零新依赖**。视觉（真机观感）与 WorkManager 实际轮换尚未人工验收。
8. **实现状态（P2，m00743「继续」后已落盘）**：R4 + R5 已实现并通过 `:app:compileDebugKotlin` + `:app:testDebugUnitTest`（**74 类 / 653 用例 / 0 失败**，P2 新增 1 类 8 例）。R4 的方案在探针阶段被改写——**compose-charts 1.0.0 里没有雷达图**（只有 Column/Line/Pie/Row），因此不引依赖、改为自绘 `ui/stats/NirikoRadarChart.kt`；R5 采纳 `me.saket.telephoto`（`gradle/libs.versions.toml` 新增 `telephoto = "0.19.0"` 与两个坐标，探针证明不产生任何额外顶版）。`README.md`（单测口径 + 技术栈）与 `THIRD-PARTY-NOTICES.md` 已同步。真机观感（雷达排版 / 长图内存与缩放边界）尚未人工验收。

9. **实现状态（P3，m00981「继续」后已落盘）**：R6 + R9 已实现并通过 `:app:compileDebugKotlin` + `:app:testDebugUnitTest`（**77 类 / 673 用例 / 0 失败**，P3 新增 3 类 20 例，P3 新增代码零告警）。R6：新增 `ui/components/NirikoNonModalSheet.kt`（无 scrim 的非模态玻璃面板，把手拖动关闭，阈值 72dp / 900dp/s）；`ui/share/SharePreviewSheet.kt` 开启半展开停靠档（`skipPartiallyExpanded = false`）；`ui/stats/CalendarDaySheet.kt` 去掉 `ModalBottomSheet` 与 `WindowBlurBehindEffect()` 改非模态，宿主 `ui/stats/StatsScreen.kt` 相应包一层 `Box`。R9：`ui/stats/CalendarCard.kt` 新增 31 天日期条、`ui/screens/Screens.kt` 新增 `TopLevelPageIndicator` 并挂到 `MainActivity.kt` 顶层 Box；`FlowLayout` 落点经核查**已天然满足**（全仓 `FlowRow` 40 处），不再改动。**零新依赖**（`gradle/libs.versions.toml` 未改）。
10. **实现状态（P4，m01180「继续」后已落盘）**：R7 已实现并通过 `:app:compileDebugKotlin` + `:app:testDebugUnitTest`（**78 类 / 696 用例 / 0 失败**，P4 新增 1 类 23 例，P4 新增代码零告警）。**依赖探针未通过**：`com.halilozercan.compose-richtext` 在 Maven Central 不存在（`.../com/halilozercan/` 目录 404、search.maven.org numFound=0），故按工程约定（README:428「纯函数优先」/ :432「优先平台 API 与已有库」）自研：新增纯 Kotlin `util/RichTextParser.kt`（HTML / BBCode → 段落 + 行内样式，无 Android 依赖、可 JVM 单测）与 `ui/components/RichText.kt`（Compose 渲染层，`LinkAnnotation.Url` 可点链接、`bgm.tv/subject/{id}` 优先站内跳转）；接入作品简介与 VNDB 简介（`ui/subject/SubjectDetailScreen.kt:998` / `:3399`）、人物小传（`ui/person/PersonDetailScreen.kt:243`）、角色介绍（`ui/character/CharacterDetailScreen.kt:181`）；并把 6 处纯文本消费方（卡片副标题、Steam / AniList / VNDB 数据源、分享文本与分享卡）统一走 `RichTextParser.toPlainText(...)`，删除 3 份行为不一致的私有 `stripHtml`（原实现把 `<br>` 变空串导致前后文字粘连）。**零新依赖**（`gradle/libs.versions.toml` 未改）。

---

## 一 参考项目总表（9 项）

| 编号 | 仓库 | 星标 / 许可 | 参考什么（UI） | 本项目落点 | 批次 |
|---|---|---|---|---|---|
| **R1** | **open-ani/animeko** | 20.4k / **AGPL-3.0** | 探索页**海报视图**：封面优先网格、图下两行标题、每格骨架占位 | 新增 `ui/search/DiscoverPosterPane.kt` + `DiscoverPosterCard.kt` | **P0（本轮）** |
| R2 | chrisbanes/haze | 2.6k / Apache-2.0 | 声明式 backdrop 模糊/玻璃的 **API 形态**与降级分层 | **新增** `ui/components/GlassSource.kt`；`ui/subject/SubjectDetailScreen.kt:460-509` | P1 ✅ |
| R3 | Hamza417/Peristyle | 721 / Apache-2.0 | 玻璃化**壁纸管理器**：壁纸库 / 标签 / 自动轮换 | **新增** `data/wallpaper/*`、`ui/settings/pages/WallpaperLibraryScreen.kt`；`AppearanceSettingsScreen.kt`、`NirikoNavHost.kt`、`MainActivity.kt`、`NirikoApplication.kt` | P1 ✅ |
| R4 | ehsannarmani/ComposeCharts | 917 / Apache-2.0 | **已发布的 1.0.0 里没有雷达图**（只有 Column/Line/Pie/Row）→ **不引依赖**，改自绘 Canvas 雷达 | **新增** `ui/stats/NirikoRadarChart.kt`；`ui/stats/StatsScreen.kt`（「口味雷达」段） | P2 ✅ |
| R5 | saket/telephoto | 1.6k / Apache-2.0 | **子采样缩放**：超长图不 OOM、缩放态平移边界 | `ui/common/ImageViewer.kt` + `libs.versions.toml`（zoomable / zoomable-image-coil 0.19.0） | P2 ✅ |
| R6 | skydoves/FlexibleBottomSheet | 1.1k / Apache-2.0 | **分段 / 非模态** bottom sheet（背景可交互） | **新增** `ui/components/NirikoNonModalSheet.kt`；`ui/share/SharePreviewSheet.kt`、`ui/stats/CalendarDaySheet.kt` + `StatsScreen.kt` | P3 ✅ |
| R7 | halilozercan/compose-richtext | 992 / Apache-2.0 | **Markdown / 富文本**渲染 | 依赖探针**未通过**（库未发布到 Maven Central）→ **自研**：新增 `util/RichTextParser.kt` + `ui/components/RichText.kt`；接入 `ui/subject/SubjectDetailScreen.kt:998` / `:3399`、`ui/person/PersonDetailScreen.kt:243`、`ui/character/CharacterDetailScreen.kt:181`，并统一 6 处纯文本消费方 | P4 ✅ |
| R8 | emertozd/Compose-Material-3-Expressive-Catalog | 69 / **无 license** | M3 Expressive 逐组件**形态比对** | `ui/components/*`、`ui/theme/Type.kt`、`NirikoShapes.kt` | P5 ✅（仅比对，未引代码；交付 `docs/m3-expressive-comparison.md`） |
| R9 | ltttttttttttt/ComposeViews | 568 / Apache-2.0 | DateSelector / Pager Indicator（StarBar 已按 m00429 去掉；FlowLayout 已天然满足） | **新增** `ui/stats/CalendarCard.kt` 日期条 + `ui/screens/Screens.kt` 指示器；`MainActivity.kt` | P3 ✅ |

---

## 二 R1 · animeko 探索页海报视图（本轮重点）

### 2.1 参考侧事实（只读，AGPL-3.0 禁抄）

- 模块：`app/shared/ui-exploration/`，子包 `ui/exploration/{ExplorationScreen.kt, recommend/RecommendedSubjectsVerticalGrid.kt, trends/, schedule/, search/}`；另有 androidTv 专属实现（`TvExploration*`）**不作参考**。
- 海报网格 `app/shared/ui-exploration/src/commonMain/kotlin/ui/exploration/recommend/RecommendedSubjectsVerticalGrid.kt`：
  `fun LazyGridScope.recommendationItems(data: LazyPagingItems<RecommendedItemInfo>, loadError: LoadError?, onClick, layoutParams: RecommendationLayoutParams)`
  —— `LazyVerticalGrid` + `items(count, key = "recommendation-$index-${item.id}", contentType = ...)`；每项 `Modifier.animateItem(...)`；item 为空时渲染**同一个** `SubjectCoverCard(isPlaceholder = true)` 骨架；错误时 `item(span = { GridItemSpan(maxLineSpan) }) { LoadErrorCard(...) }`。
- 海报卡 `app/shared/ui-foundation/src/commonMain/kotlin/ui/subject/SubjectCoverCard.kt`：
  `fun SubjectCoverCard(name: String?, image: String?, isPlaceholder: Boolean, onClick, modifier, shape, imageModifier, brushLayerModifier)`
  —— 结构 = `BasicCarouselItem(label = { CarouselItemDefaults.Text(name ?: "", maxLines = 2) }, maskShape = shape)` 内嵌 `AsyncImage(modifier.aspectRatio(9f / 16).fillMaxWidth(), ContentScale.Crop)`。
  **关键三点**：① 封面外**没有玻璃容器**——封面本身就是卡；② 封面上**没有任何角标**；③ 标题在封面**下方、最多 2 行**。
- 网格几何 `ui/subject/SubjectGridLayoutParams.kt`：`GridCells.Adaptive(minSize = 100.dp / 128.dp / 150.dp / 180.dp)`，间距 8 / 12 / 16dp，`cardShape = MaterialTheme.shapes.large`。

### 2.2 借鉴 5 条 / 不借鉴 3 条

**借鉴（UI 形态）**
1. **封面优先的无容器网格**：网格项 = 封面 + 图下标题，不再套 `appleGlassCard` 玻璃卡；
2. **图下标题两行**（对比本项目卡片视图 1 行）；
3. **每格独立骨架占位**（不是整屏转圈，也不是单张列表骨架）；
4. **逐项 `animateItem()` 入场**；
5. **列数随窗口自适应**（本项目已有 `NirikoGridColumns` 断点表，直接沿用而不抄它的 `GridCells.Adaptive`）。

**不借鉴**
1. 数据与分页（`LazyPagingItems`）——本项目沿用 `SubjectSearchViewModel` 现有 `trendingResults / hasMore / loadNextTrendingPage()`；
2. 云收藏 / Bangumi 同步 / 剧集进度（`AiringLabel`）——不属于「海报视图的 UI」；
3. 9:16 封面比例（理由见 §3.8）。

### 2.3 落点与改法

**新增第三种布局**，三个文件动、两个新文件建：

| 动作 | 文件 | 说明 |
|---|---|---|
| 改 | `app/src/main/java/com/otakup/niriko/data/model/search/DiscoveryLayout.kt` | 加 `POSTER("海报", "POSTER")`，`toggled` 改三态循环 |
| 改 | `app/src/main/java/com/otakup/niriko/ui/common/SearchToolbar.kt:104-134` | 切换按钮图标按「下一个布局」三态映射 |
| 改 | `app/src/main/java/com/otakup/niriko/ui/subject/SubjectSearchScreen.kt:159-173` | 内容区分支加 `POSTER` |
| 新建 | `app/src/main/java/com/otakup/niriko/ui/search/DiscoverPosterPane.kt` | 海报网格 + 载/错/空三态 |
| 新建 | `app/src/main/java/com/otakup/niriko/ui/search/DiscoverPosterCard.kt` | 海报卡（封面 + 评分 + 收藏 + 两行标题） |
| 可见性 | `app/src/main/java/com/otakup/niriko/ui/search/TrendingSection.kt:53`、`:257` | `TrendingFreshnessRow` / `TrendingEmptyState` 由 `private` 改 `internal`，**不改渲染** |

---

## 三 发现页海报视图 · 设计定稿（可直接实现）

### 3.1 布局枚举（`DiscoveryLayout.kt`）

```kotlin
enum class DiscoveryLayout(val label: String, val key: String) {
    CARD("卡片", "CARD"),
    GRID("宫格", "GRID"),
    POSTER("海报", "POSTER");

    /** 三态循环：卡片 → 宫格 → 海报 → 卡片。 */
    val toggled: DiscoveryLayout get() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** 兜底不变：未知值一律回到 CARD。 */
        fun fromKey(raw: String?): DiscoveryLayout =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: CARD
    }
}
```

- **兼容性**：`fromKey` 与持久化 key `AppSettings.discoveryLayout`（默认 `"CARD"`，见 `data/settings/AppSettings.kt:103`、`SettingsDataStore.kt` 的 `Keys.DISCOVERY_LAYOUT`）均不改；老用户的 `"GRID"` 依然落在宫格。
- **行为差异（必须写进 release notes）**：停在宫格时，以前按一次回卡片，现在按一次进海报、再按一次回卡片。

### 3.2 切换按钮（`SearchToolbar.kt:104-134`）

保持 `Box(align(CenterEnd).padding(end = 72.dp).size(36.dp).clip(CircleShape).clickable)`、20dp、`tint = colorScheme.primary` 全部不动，只把「当前布局」换「下一个布局」：

| 当前布局 | 图标 | contentDescription |
|---|---|---|
| 卡片 | `Icons.Default.Menu`（保持现状） | 切到宫格视图 |
| 宫格 | `Icons.Outlined.ViewModule`（新增） | 切到海报视图 |
| 海报 | `Icons.Default.List`（保持现状） | 切回卡片视图 |

`material-icons-extended` 已是依赖（`app/build.gradle.kts:114`），`ViewModule` 可直接用。

### 3.3 宿主分支（`SubjectSearchScreen.kt:159-173`）

现有 `if (GRID && query.isBlank()) DiscoverGridPane else { BrowseFilterPanel? + PullToRefreshBox { TrendingSection } }` 只加一层 `when`：

```kotlin
if (state.discoveryLayout == DiscoveryLayout.GRID && query.isBlank()) {
    DiscoverGridPane(...)                      // 原样不动
} else {
    Column(Modifier.fillMaxSize().padding(top = contentTopInset)) {
        if (query.isBlank() && state.trendingMode == TrendingMode.ALL_TIME) {
            BrowseFilterPanel(...)             // 原样不动：海报视图也照旧显示 9 维筛选器
        }
        PullToRefreshBox(...) {
            if (state.discoveryLayout == DiscoveryLayout.POSTER && query.isBlank()) {
                DiscoverPosterPane(/* 参数与 TrendingSection 调用点逐个对齐 */)
            } else {
                TrendingSection(...)           // 原样不动
            }
        }
    }
}
```

规则：**海报视图只在 `query.isBlank()` 时生效**（与宫格同规则）；搜索态、沉浸搜索态、人物搜索一律走原路径。

### 3.4 新文件 `DiscoverPosterPane.kt`

签名与 `TrendingSection`（`TrendingSection.kt:106-122`）**逐参对齐**，让调用点只是换一个函数名：

```kotlin
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DiscoverPosterPane(
    state: SubjectSearchUiState,
    query: String,
    onLoadMore: () -> Unit = {},
    onSubjectClick: (Long) -> Unit,
    onRetry: () -> Unit = {},
    onScrollPosChange: (Int, Int) -> Unit = { _, _ -> },
    initialScrollPos: Pair<Int, Int> = 0 to 0,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    lastUpdatedAt: Long = 0L,
    onForceRefresh: (() -> Unit)? = null,
    onOpenAllTime: (() -> Unit)? = null,
)
```

结构：

```kotlin
val gridState = rememberLazyGridState()
val columns = NirikoGridColumns.fromLayout(currentNirikoWindowLayout())
when {
    state.error != null && query.isBlank() -> ErrorContent(message = state.error, onRetry = onRetry)
    state.trendingResults.isNotEmpty() -> Column {
        Box(Modifier.padding(horizontal = 12.dp)) {   // 实测采用 12dp：与网格首列对齐
            TrendingFreshnessRow(lastUpdatedAt = lastUpdatedAt, onForceRefresh = onForceRefresh)
        }
        // 恢复位置：与卡片视图共用同一份 initialScrollPos，仅 ALL_TIME
        val latest = rememberUpdatedState(initialScrollPos)
        LaunchedEffect(state.trendingVersion) {
            val (idx, off) = latest.value
            if (state.trendingMode == TrendingMode.ALL_TIME && state.trendingResults.isNotEmpty()) {
                gridState.scrollToItem(idx.coerceIn(0, state.trendingResults.lastIndex), off)
            }
        }
        // 上报位置：index 语义与列表一致
        LaunchedEffect(gridState) {
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .collect { (i, o) -> onScrollPosChange(i, o) }
        }
        // 触底加载：算法与 `TrendingSection.kt:156-175` 完全相同（LazyGridLayoutInfo 与 LazyListLayoutInfo 同名字段）
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.reportBottomBarScroll(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(state.trendingResults, key = { _, s -> s.subjectId }) { index, subject ->
                RevealOnScroll(staggerIndex = index % columns) {
                    DiscoverPosterCard(
                        subject = subject,
                        isCollected = subject.subjectId in state.collectedSubjectIds,
                        onClick = { onSubjectClick(subject.subjectId) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.isLoadingMore) {
                items(columns) { PosterGridSkeletonCard() }
            } else if (!state.hasMore) {
                item(span = { GridItemSpan(maxLineSpan) }) { 到底文案（与卡片视图一致） }
            }
        }
    }
    state.isLoadingTrending -> repeat(4 行) 的 12 格 PosterGridSkeletonCard
    state.trendingMode == TrendingMode.STEAM -> ErrorContent(与 `TrendingSection.kt:217-221` 同文案)
    else -> TrendingEmptyState(message = 按 mode 的三段文案, onRetry, onOpenAllTime)
}
```

### 3.5 新文件 `DiscoverPosterCard.kt`

```kotlin
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DiscoverPosterCard(
    subject: SubjectEntity,
    isCollected: Boolean,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(PosterCardMetrics.CornerRadius))
            .clickable(onClick = onClick),
    ) {
        Box {
            CoverImage(
                coverUrl = subject.coverUrl,
                contentDescription = subject.displayTitle,
                shape = RoundedCornerShape(PosterCardMetrics.CornerRadius),
                aspectRatio = PosterCardMetrics.CoverAspectRatio,
                sharedElementKey = "cover_" + subject.subjectId,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                subjectId = subject.subjectId,
            )
            RatingBadge(
                rating = subject.ratingScore,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 6.dp),
            )
            if (isCollected) {
                FavoriteBadge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 6.dp),
                    filled = true,
                )
            }
        }
        Text(
            text = subject.displayTitle,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
        )
    }
}
```

**刻意减法**：海报格只保留 封面 · 标题（2 行） · 评分胶囊 · 已收藏心形。类型标签 / 平台·集数 / 简介 / 进度条 / 我的评分 / 个人标签**不进海报格**——那是卡片视图的职责（animeko 封面卡上同样不放任何角标）。

### 3.6 复用清单（P0 不新增任何依赖）

| 复用 | 位置 |
|---|---|
| `CoverImage(coverUrl, contentDescription, shape, aspectRatio, sharedElementKey, sharedTransitionScope, animatedVisibilityScope, subjectId)` | `ui/components/CoverImage.kt:52` |
| `RatingBadge(rating: Float?, modifier, shape)` / `FavoriteBadge(modifier, filled, shape)` | `ui/components/Badges.kt:77` / `:122` |
| `PosterGridSkeletonCard(modifier)`（public，几何与真卡一致） | `ui/library/PosterGridCard.kt` |
| `PosterCardMetrics{ CornerRadius = 16.dp, CoverAspectRatio = 2f/3f }`（internal，同模块可见） | `ui/library/PosterGridCard.kt` |
| `NirikoGridColumns.fromLayout(layout)` / `currentNirikoWindowLayout()` | `ui/adaptive/WindowClass.kt:104` / `:66` |
| `Modifier.reportBottomBarScroll()` | `ui/common/BottomBarScroll.kt` |
| `RevealOnScroll(staggerIndex)` | `ui/animation/RevealOnScroll.kt:37` |
| `ErrorContent` / `SkeletonSubjectCard` | `ui/components/StateComponents.kt` |

### 3.7 需要的最小可见性改动

- `TrendingSection.kt:53` `private fun TrendingFreshnessRow(...)` → `internal`；
- `TrendingSection.kt:257` `private fun TrendingEmptyState(...)` → `internal`。

两处**只改可见性，函数体、参数、渲染一律不动**；卡片视图的 UI 输出保持字节级一致。

### 3.8 几何与比例决策

| 决策 | 取值 | 理由 |
|---|---|---|
| 封面比例 | **2:3**（`PosterCardMetrics.CoverAspectRatio`） | animeko 用 9:16；本项目作品库海报格已是 2:3，且 Bangumi 封面多为竖版，9:16 会裁掉封面上下信息。同一份封面在两个页面不该有两种裁切 |
| 圆角 | 16dp | 与作品库海报格同源 |
| 网格间距 / 页面边距 | 10dp / 12dp | 与 `ui/screens/Screens.kt:485-487` 作品库网格一致（3 列需要比卡片视图的 16dp 更宽的可用宽度） |
| 列数 | 手机 3 / 600–839dp 4 / ≥840dp 6 | 复用 `NirikoGridColumns`，不抄 animeko 的 `GridCells.Adaptive(minSize)`——两张网格保持同一断点表 |
| 标题 | `labelSmall` + Medium，`minLines = maxLines = 2` | 照 animeko `CarouselItemDefaults.Text(maxLines = 2)` 的「最多两行」；加 `minLines` 是为了网格行高整齐 |

### 3.9 滚动位置与分页

- 位置状态沿用 `SubjectSearchUiState.trendingScrollIndex / trendingScrollOffset`（**按 `selectedType` 各一份**，`data/model/search/SubjectSearchUiState.kt:54-57`），由 `SubjectSearchViewModel.saveTrendingScrollPos(type, index, offset)` / `getTrendingScrollPos(type)` 存取——与卡片视图**共用同一份数据**，布局互切后回到同一位置。
- `LazyGridState.firstVisibleItemIndex` 与 `LazyListState` 同为「item 序号」，因此恢复逻辑可整段照搬；像素 `offset` 因行高不同，允许 ≤1 行误差（验收标准里写明）。
- 触底加载阈值沿用「剩余未组合项数 × 最后可见项高度 < 200dp」（`TrendingSection.kt:156-175` 同一算法，`LazyGridLayoutInfo` 字段同名，可整段复制），条件仍是 `hasMore && !isLoadingMore`。

### 3.10 载 / 错 / 空三态

| 状态 | 海报视图 | 卡片视图（对照，不改） |
|---|---|---|
| 首次加载 | 12 格 `PosterGridSkeletonCard`（3 列 × 4 行） | 3 张 `SkeletonSubjectCard` |
| 加载更多 | 网格尾部追加 1 行骨架 | 尾部 1 张 `SkeletonSubjectCard` |
| 错误 | 整屏 `ErrorContent(state.error, onRetry)` | 同 |
| Steam 空态 | `ErrorContent` + 同一段兜底文案 | `TrendingSection.kt:217-221` |
| 空态 | `TrendingEmptyState` + 按 mode 的三段文案（当季热门额外给「查看历史排名 ›」出口） | `TrendingSection.kt:235-246` |
| 到底 | 跨列整行「— 已经到底了 —」 | 尾部单行文案 |

### 3.11 动画与骨架

- 入场：`RevealOnScroll(staggerIndex = index % columns)`（3 列时段宽错峰，替代卡片视图的 `% 5`）；
- 重排/淡入淡出：`Modifier.animateItem()`（同卡片视图）；
- 共享元素：`sharedElementKey = "cover_" + subjectId` 与卡片视图、作品库网格同 key → 点海报进详情同样有封面飞入；
- 骨架：几何与真卡一致（16dp 圆角 + 2:3 封面 + 同高标题行），不复用卡片骨架。

### 3.12 无障碍 / 文案

| 元素 | contentDescription |
|---|---|
| 切换按钮（当前卡片） | 切到宫格视图 |
| 切换按钮（当前宫格） | 切到海报视图 |
| 切换按钮（当前海报） | 切回卡片视图 |
| 海报封面 | `subject.displayTitle`（与作品库海报格一致） |

### 3.13 测试清单

**JVM 单测（现有 628 个必须全绿，另加）**
1. `DiscoveryLayoutTest`：`toggled` 三态循环（CARD→GRID→POSTER→CARD）、`fromKey("GRID")/("POSTER")/("poster ")`、未知值兜底 CARD；
2. `NirikoGridColumnsTest`（若尚无）：`fromLayout` 三档列数 3 / 4 / 6；
3. 海报卡映射：`subject.ratingScore = null` 时 `RatingBadge` 不渲染、`isCollected = false` 时无心形（可用现有 Compose 测试基建或纯逻辑断言）。

**手工必测**
1. 手机 <600dp：卡片视图与宫格视图**逐像素无变化**（对照 `docs/screenshots/`）；
2. 三态切换循环 + 杀进程重进后布局保持（DataStore 持久化）；
3. 历史排名（ALL_TIME）：筛选面板展开/收起、滚动到第 3 屏 → 切布局 → 位置大致保持（≤1 行）；
4. 触底分页：网格滚动到底自动加载下一页，尾部骨架出现后消失；
5. 断网 → 错误态 → 重试；
6. 点海报 → 详情页封面共享元素动画正常返回；
7. 旋转/折叠屏：列数 3 → 4 → 6 自动重排；
8. 平板 + 折叠屏大屏：封面不被拉伸到失真（§4·R1 备选：给封面加 `sizeIn(maxWidth = 300.dp)`，animeko 同款做法）。

### 3.14 风险与回退

| 风险 | 处置 |
|---|---|
| 切布局后像素滚动位置跳动 | 只保证 index 级对齐；`trendingScrollOffset` 在网格下按行高换算或直接置 0 |
| 海报格高亮「已收藏」心形与作品库心形撞位置 | 两页不同时可见，无冲突；共用 `Badges.kt` 组件保证观感一致 |
| 大屏 6 列时封面过大 | 备选 `sizeIn(maxWidth = 300.dp)`（animeko 同款注释：限制最大宽度避免大屏解码压力） |
| 用户不接受三态循环 | 回退点极小：删 `POSTER` 分支 + `toggled` 恢复二态即可，其它两个视图未被触碰 |

---

## 四 R2–R9 落点明细（后续批次）

### R2 chrisbanes/haze（Apache-2.0，2.6k★）· P1
- **现状缺口**：本项目玻璃层是自研三件套——`ui/components/GlassCard.kt:168`（vibrancy → blur → lens 真液态玻璃管线）、`:191`（静态降级）、`:105`（按壁纸亮度自适应 tint）、`ui/components/GlassBackdrop.kt:8-18`（用 kyant backdrop 捕获窗口，`null = 无壁纸/二级页/玻璃关闭 → 退化为静态材质`）。
- **借鉴**：haze 把「哪些 composable 参与模糊」显式声明（`hazeSource` + `hazeBlur(HazeInput.Sources(state), HazeBlurStyle)`），并把材质拆成 `haze / haze-blur / haze-blur-materials / haze-glass` 分层。本项目对应问题是「二级页没有 backdrop 就整体退化为静态」——可参考其「输入源显式化」的 API 形态，为二级页提供替代模糊源（如页面自身背景快照），而**不引入依赖**（haze 2.x 与 miuix 一样有 Kotlin/AGP 版本门槛）。
- **UI 改动**：二级页（详情/统计/设置）玻璃卡从「静态材质」升级为「快照模糊材质」，与首页观感统一。
- **验收**：真机对照首页与详情页玻璃；模糊开关关闭时行为不变。
- **实现（m00558）**：新增 `ui/components/GlassSource.kt` —— `rememberPageCardGlassBackdrop(enabled): LayerBackdrop?`（kyant `rememberLayerBackdrop { drawRect(surface); drawContent() }`）、`Modifier.pageCardGlassBackdrop(backdrop: LayerBackdrop?)`、`ProvideCardGlassBackdrop(backdrop: Backdrop?, content)`（就近覆盖 `LocalCardGlassBackdrop`）。**只覆盖 kyant 卡片源**：分区卡 `GlassSectionCard` 走 miuix `top.yukonga.miuix.kmp.blur.Backdrop` 且需调用方显式传参，`ui/components/GlassSectionCard.kt:47` **未改动**。`SubjectDetailScreen.kt:460-473` 保留 miuix 封面背景墙，另加 page card backdrop 捕获（仅 `glassEffect == FULL`），`:474` 用它挂载封面层、`:509` 起 `ProvideCardGlassBackdrop(pageCardBackdrop)` 包住主内容 → 详情页内的玻璃卡从「静态降级」变为折射页面封面背景墙。REDUCED / OFF 档行为完全不变。

### R3 Hamza417/Peristyle（Apache-2.0，721★）· P1
- **现状缺口**：`ui/wallpaper/WallpaperHost.kt:62`（全局壁纸层，仅顶层四页显示，二级路由纯 surface）、`:57-58`（消化管线：色彩消化 → 高斯柔化 → 自适应 scrim → 结构化渐变）、`data/settings/WallpaperAtmosphere`、`util/WallpaperPage`、`ui/settings/pages/ThemePackSection.kt`。
- **借鉴**：Peristyle 的「壁纸库」形态——网格缩略图 + 标签 + 定时自动轮换 + 原生 live wallpaper picker 入口。
- **UI 改动**：外观设置里增加壁纸库（网格选择、标签/收藏、每日轮换、按页覆盖 `perPageUris` 的可视化编辑）。
- **验收**：轮换在 WorkManager 上跑通；壁纸切换后玻璃 tint 自适应跟随（`GlassCard.kt:105`）。
- **实现（m00558）**：新增 `data/wallpaper/{WallpaperLibraryEntry,WallpaperLibraryFiles,WallpaperRotation,WallpaperRotationWorker,WallpaperRotationScheduler}.kt`（`@Serializable` 条目 + 编解码器、SAF 导入复制到 `filesDir/wallpapers/`、纯函数轮换候选/顺延、`wallpaper_rotation_periodic` 每日 Worker）与 `ui/settings/pages/WallpaperLibraryScreen.kt`（缩略图网格用 `chunked(3)` 的行列布局而非 `LazyVerticalGrid`，避免嵌在 `verticalScroll` 下崩溃；点按应用目标、长按收藏/标签/删除、导入多选、每日轮换与「仅收藏」开关、「立即换一张」）。设置侧新增 3 个字段（`AppSettings.kt:72-74`）→ 3 个 DataStore 键 + setter + `restoreFrom`，并在 `BackupManager` 导入/导出两处同步（否则备份丢字段）；`SettingsViewModel` 加 3 个 action；外观页加「壁纸库」入口行 + `onOpenWallpaperLibrary`；`NirikoNavHost` 新增 `SETTINGS_WALLPAPER_LIBRARY_ROUTE = "settings_wallpaper_library"`（宽屏 `AdaptiveSettingsPane` 用同名字面量）；`NirikoApplication` 按 `wallpaperRotationEnabled` 响应式调度/取消 Worker；新增纯函数 `util/WallpaperPage.kt: effectiveWallpaperUri(globalUri, perPageUri)` 供 `MainActivity` 计算 tint 亮度（**按页覆盖优先**，修掉原先只按全局 URI 的缺口）。轮换语义：只改全局槽位，不动各页覆盖。

### R4 ehsannarmani/ComposeCharts（Apache-2.0，917★）· P2 ✅（方案改为自绘）
- **现状缺口**：图表为自研 `ui/stats/NirikoBarChart.kt`、`NirikoDonutChart.kt`、`TagWordCloud.kt`、`ui/subject/EpisodeRatingChart.kt`、配色 `ui/theme/ChartPalette.kt`。
- **探针实测（P2 批次）**：`io.github.ehsannarmani:compose-charts:1.0.0` 的 sources jar 里**没有雷达图**——全仓 grep `radar` = **0 命中**，实际只有 `ColumnChart` / `LineChart` / `PieChart` / `RowChart`（没有 Bubble、没有 Candle），Maven 版本序列止于 `1.0.0`。
- **方案变更**：**不引入该依赖**，改为用 Compose `Canvas` **自绘雷达**（只借鉴其「对称轴均分 + 入场动画」的设计）。柱状 / 折线 / 环形仍由 Vico 3.2.0 承载，两者不冲突；`gradle/libs.versions.toml` 里没有留下任何 compose-charts 坐标。
- **实现（已落盘）**：新增 `ui/stats/NirikoRadarChart.kt`——`data class RadarAxis(label, value, color)`、`@Composable fun NirikoRadarChart(axes: List<RadarAxis>, modifier: Modifier = Modifier)`（4 圈网格 + 辐条、primary 半透明填充 + 描边、逐轴色顶点圆点、半径 0→满格用 `AnimDurationLong` + `AnimEasingDefault`、`motionEnabled()` 为假时瞬时到位）、纯函数 `internal object RadarGeometry { angleDegrees(index, count) / normalize(value, max) / point(center, radius, angleDegrees) }`、常量 `MIN_RADAR_AXES = 3` / `MAX_RADAR_AXES = 8`。统计页在「作品类型分布」之后插入 `item(key = "tasteRadar")` + 分隔线，段体 `TasteRadarSection`（取 `tasteRadarTags(state)` = `state.tagStats` 中 `count > 0` 的前 8 个；轴色取 `themeChartPalette()`；图例两列 + 计数；标签不足 3 个时整段不渲染）。
- **未做**：原计划里「单集评分的蜡烛 / 气泡形态对比」本轮不做（compose-charts 里没有这两种图型可参考），`ui/subject/EpisodeRatingChart.kt` 未改动。
- **验收**：`app/src/test/java/com/otakup/niriko/ui/stats/NirikoRadarChartTest.kt` 8 例（角度均分与退化轴数、归一化夹取、非正/非有限最大值、极坐标投影四方向、零半径、任意角半径不变、轴数常量）；编译 + 全量单测见 §五 批次状态。真机观感仍待人工验收。

### R5 saket/telephoto（Apache-2.0，1.6k★）· P2 ✅
- **现状缺口**：`ui/common/ImageViewer.kt` 对长海报整张解码；旧实现的平移上限按**容器尺寸**算（`size.width * (scale - 1f) / 2f`），长图放大后仍能拖出画面。
- **依赖（已加入）**：`me.saket.telephoto:zoomable:0.19.0` + `me.saket.telephoto:zoomable-image-coil:0.19.0`（Apache-2.0，随附 `sub-sampling-image`；`libs.versions.toml` 里 `telephoto = "0.19.0"`，别名 `telephoto-zoomable` / `telephoto-zoomable-image-coil`）。B0 口径探针结论：**不产生任何额外顶版**——基线本身就是 `org.jetbrains.kotlin:kotlin-stdlib -> 2.3.21` 与 `org.jetbrains.compose.foundation:foundation -> 1.11.0`（由 `miuix-blur-android:0.9.0` 与 `calendar:2.10.1` 带来），而 `zoomable-image-coil` 的 Coil 依赖恰为本工程同款 `io.coil-kt:coil-compose:2.7.0`。
- **实现（已落盘）**：`ImageViewer` 每页一份 `rememberZoomableState(ZoomSpec(maxZoomFactor = IMAGE_MAX_ZOOM_FACTOR = 5f))` + `rememberZoomableImageState`，封面渲染换成 `ZoomableAsyncImage(state = ..., contentScale = ContentScale.Fit)`（大图自动走 `BitmapRegionDecoder` 子采样）；缩放状态存进 `remember(urls.size) { mutableStateMapOf<Int, ZoomableImageState>() }`（翻页回来仍停在原缩放位置），`HorizontalPager.userScrollEnabled = !isCurrentPageZoomed`，其中 `isCurrentPageZoomed` 读当前页 `zoomableState.zoomFraction > 0f`。原 `detectTransformGestures` / `detectTapGestures` / `graphicsLayer` / 翻页重置缩放全部删除。**公开签名 `fun ImageViewer(urls, initialIndex, title, referer, onDismiss)` 不变**，权限申请、保存到相册（`saveImageToGallery`，24MB 上限 / Referer / UA）、浏览器打开等逻辑原样保留。3 处调用点未改。
- **验收**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` 通过（见 §五）。真机「打开 3 张超长图内存平稳、缩放平移无越界」仍需人工验收（JVM 测试无法覆盖渲染）。

### R6 skydoves/FlexibleBottomSheet（Apache-2.0，1.1k★）· P3 ✅
- **现状**：`ModalBottomSheet` 共 3 处 —— `ui/share/SharePreviewSheet.kt:71`、`ui/subject/SubjectDetailScreen.kt:1509`、`ui/stats/CalendarDaySheet.kt:72`。
- **借鉴**：分段（partially expanded 停靠）/ 非模态（背景可交互）/ 跳过中间状态；**只借交互形态，自研实现**，不引依赖。
- **实现（P3）**：
  - **非模态面板（自研）**：新增 `ui/components/NirikoNonModalSheet.kt` —— 无 scrim 的 `Box(fillMaxSize)` + 底部对齐玻璃面板（`appleGlassCard` + `RoundedCornerShape(topStart/topEnd = 24.dp)`，高度默认 `heightFraction = 0.62f`）；面板自身 `clickable(indication = null)` 吃掉落在它上面的点击（防背景日历格误触），背景保持可点击 / 可滚动。关闭手势 = 顶部 28dp 把手的 `draggable`，阈值 `SHEET_DISMISS_DISTANCE_DP = 72f` / `SHEET_DISMISS_VELOCITY_DP = 900f`，判定抽成纯函数 `shouldDismissSheet(dragDistancePx, dragVelocityPx, distanceThresholdPx, velocityThresholdPx)`；入场用 `Animatable`，`motionEnabled()` 为 false 时直接落位。
  - **`ui/stats/CalendarDaySheet.kt`**：`ModalBottomSheet` / `rememberModalBottomSheetState` / `@OptIn(ExperimentalMaterial3Api)` / `WindowBlurBehindEffect()` 全部移除（后者只对独立 window 的弹窗有效，非模态浮层无意义 —— 函数本身保留在 `ui/components/GlassCard.kt:313-335`），改为 `BackHandler(onBack = onDismiss)` + `NirikoNonModalSheet { LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(bottom = 32.dp)) }`。
  - **`ui/stats/StatsScreen.kt`**：`StatsContent` 与日详情面板此前是根级兄弟（能叠放只是因为 `ModalBottomSheet` 自成 window），现包进 `Box(modifier.fillMaxSize())` 才成立。
  - **`ui/share/SharePreviewSheet.kt`**：`rememberModalBottomSheetState(skipPartiallyExpanded = false)` 并显式传给 `ModalBottomSheet`（此前未传 sheetState，中间档永远不生效），形成「预览」半展开档 + 「自定义」全展开档；半展开时在预览下方提示「上滑展开自定义选项」（`sheetState.currentValue == SheetValue.PartiallyExpanded`）。
  - **明确不做**：`ui/subject/SubjectDetailScreen.kt:1509` 的第 3 处 `ModalBottomSheet` 保持现状 —— 它是「选完即关」的选择器，非模态化只会让未选完的用户误触背景。
- **验收**：手势不打架由结构保证 —— 只有把手吃竖向拖动，面板内容（`LazyColumn`）与背景日历各自的滚动互不影响。`app/src/test/java/com/otakup/niriko/ui/components/NirikoNonModalSheetTest.kt` 6 例（位移 / 速度 / 双低三态、边界值、向上拖动不关闭、阈值常量）。真机 60fps 与手感仍需人工验收。

### R7 halilozercan/compose-richtext（Apache-2.0，992★）· P4 ✅（依赖探针失败 → 自研）
- **现状缺口**：全仓库 `Markdown / BBCode / RichText / fromHtml` **0 命中**；`ui/subject/SubjectDetailScreen.kt:983` 的简介是 `Text(subject.summary, maxLines = 20)`，`:3375-3388` 是 VNDB 简介的展开/收起，全部按纯文本渲染。
- **借鉴**：Markdown / BBCode 渲染（换行、加粗、链接、列表）。
- **UI 改动**：作品简介 / 人物小传 / 角色介绍按富文本渲染，链接可点（站内跳转优先）。
- **依赖探针（m01180，未通过）**：`com.halilozercan.compose-richtext` 未发布到 Maven Central —— `https://repo1.maven.org/maven2/com/halilozercan/compose-richtext/richtext-commonmark/maven-metadata.xml` 与 `…-android/…` 均 404，组目录 `…/com/halilozercan/` 本身 404，search.maven.org `q=g:com.halilozercan.compose-richtext` numFound=0。备选 `com.mohamedrejeb.richeditor:richeditor-compose`（Maven Central latest 1.2.1）存在，但它是**编辑器**取向的重型依赖、HTML 解析无法在 JVM 单测里验证；按 README:428「纯函数优先」/ :432「引入新第三方依赖前先讨论，优先使用平台 API 与已有库实现」与 P2 先例（compose-charts 1.0.0 无雷达图 → 自绘），改为**自研**。
- **实现（P4）**：新增 `util/RichTextParser.kt`（纯 Kotlin、无 Android 依赖：HTML / BBCode → `List<RichTextParagraph>`；含实体解码、裸 URL 自动成链、`<script>`/`<style>` 内容丢弃、孤立 `<` 与超长伪标签不吞正文）与 `ui/components/RichText.kt`（Compose 渲染层，`LinkAnnotation.Url` + `TextLinkStyles` 下划线链接，`bgm.tv/subject/{id}` 优先走 `onSubjectLink` 站内跳转，其余走 `onLink` 或系统浏览器）。接入作品简介（`ui/subject/SubjectDetailScreen.kt:998`）、VNDB 简介展开/收起（`:3399`，去掉原内联正则与 `take(160)/take(1200)`）、人物小传（`ui/person/PersonDetailScreen.kt:243`，`onSubjectLink = onSubjectClick`）、角色介绍（`ui/character/CharacterDetailScreen.kt:181`）。
- **纯文本消费方统一**：`util/SubjectCardDisplayMapper.kt:55-56`、`data/remote/game/SteamGameDataSource.kt:52`、`data/remote/anilist/AniListMapper.kt:41`、`data/remote/vndb/VndbGameDataSource.kt:114`、`data/remote/anilist/AniListGameDataSource.kt:194`、分享文本与 `ShareCardData`（`ui/subject/SubjectDetailScreen.kt:3728` / `:3755` / `:3766`）全部改走 `RichTextParser.toPlainText(...)`，并删除 `AniListMapper` / `VndbGameDataSource` 里 3 份互相不一致的私有 `stripHtml`（原实现把 `<br>` 变成空串，导致前后文字粘连）。分享位图仍只画纯文本。
- **验收**：`app/src/test/java/com/otakup/niriko/util/RichTextParserTest.kt` 23 例全绿（段落 / 硬换行 / 空白折叠、加粗斜体、显式与自动链接、HTML 实体、列表、`<script>` 丢弃、BBCode、`bgmSubjectIdFromUrl`、超长伪标签保护）；`:app:compileDebugKotlin :app:testDebugUnitTest` → **78 类 / 696 用例 / 0 失败**，P4 新增代码零告警。
- **未做**：真机观感（链接色 / 下划线、长简介排版与折叠高度）未人工验收。

### R8 emertozd/Compose-Material-3-Expressive-Catalog（无 license，69★）· P5 ✅（纯文档，未改源码）
- **定位**：**只做规范比对，不抄任何代码**（仓库无 license 文件）。本轮只把它 49 个 sample 的**文件名清单**当「哪些东西属于 M3 Expressive」的索引，**未读任何 sample 源码**。
- **决定性前提**：本工程 material3 实际解析为 **1.4.0**（`gradle/libs.versions.toml:8` 的 `composeBom = "2025.12.01"`；证据 = Gradle 缓存 `material3-android/1.4.0/.../material3.aar` + `javap -v -p` 读常量池里的 composable 签名与**参数名**）。参考仓库用的是 1.5.0-alpha26，因此它列举的组件里有一部分我们**只有 tokens、没有 composable**（`ButtonGroupKt` / `SplitButtonKt` / `ToggleButtonKt` / `FloatingToolbarKt` / `LoadingIndicatorKt` / `MaterialShapesKt` 均不存在）。
- **比对对象与结论**：`ui/components/GlassControls.kt`、`ui/components/Badges.kt`、`ui/components/StateComponents.kt`、`ui/theme/Type.kt`、`ui/theme/NirikoShapes.kt`（squircle，来源 miuix）→ **4 迁移**（P0 进度指示器 gap + 停止点〔1.4.0 已有，旧重载已 deprecated〕、P0 动效统一到 `MaterialTheme.motionScheme`、P1 `GlassSlider`/`GlassRatingSlider` 槽位化、P1 `GlassToggle` 几何 + `thumbContent`）+ **2 项 P2**（徽标令牌对齐、排版数值校准）+ **1 保留自研**（底部导航：自研玻璃条优于 `ShortNavigationBar`）+ **1 推迟**（`MaterialShapes` 形状，需 1.5.0-alpha）。
- **验收**：比对表中每一项都有 M3 官方文档依据（m3.material.io 逐条 URL，2026-10-01 实测 200；另有 5 条 404 URL 已记录以免误引）。
- **未做**：未改任何 Kotlin 源码、未升依赖（最新 material3 = **1.5.0-alpha29**，升它会连带把 Compose UI 顶到 1.13.0-alpha，不采纳）。

### R9 ltttttttttttt/ComposeViews（Apache-2.0，568★）· P3 ✅
- **落点与借鉴**：
  - `DateSelector` → `ui/stats/CalendarCard.kt`：新增 **31 天横向日期条**（`CalendarDateStrip`，插在 `CalendarHeader` 与 `WeekdayHeader` 之间）—— 选中日高亮、今天淡色底、有事件的天带 4dp 圆点；窗口以今天为中心固定（`dateStripDates` / `dateStripCenter`：锚点落在窗口内就整条不移动，避免点一天后整条在手指下平移），点选走 `onAnchorDateChange`（与日历网格共用同一状态）；套 `Modifier.calendarGestureLock(gestureLock)` 与顶级 Pager 互斥。
  - `Pager / Indicator` → **新增** `ui/screens/Screens.kt` 的 `TopLevelPageIndicator(pagerState, modifier, segmentCount)`：4 段 3dp 高胶囊、间距 4dp，宽度 6→18dp、alpha 0.28→1.0 由 `pageIndicatorWeight(index, pagePosition)`（`pagePosition = currentPage + currentPageOffsetFraction`）连续插值，拖动时逐帧跟手；挂载在 `MainActivity.kt` 顶层 `Box`（`Modifier.align(Alignment.TopCenter)`，仅 `showBottomBar` 时显示）。此前全应用没有任何顶部页面指示。
  - `FlowLayout` → **已天然满足**：全仓 `FlowRow` 已有 40 处（`ui/subject/SubjectDetailScreen.kt` 的标签流、个人标签、`ui/stats/TagWordCloud.kt`、`ui/library/CollectionDashboard.kt` 等），本轮不改动。
- **验收**：每个控件只借形态、自研实现（**零新依赖**）。`app/src/test/java/com/otakup/niriko/ui/stats/CalendarDateStripTest.kt` 7 例（窗口升序 / 自定义半径 / 跨月跨年 / 窗口中心保持与重开窗 / 周日为 0 的星期索引）+ `app/src/test/java/com/otakup/niriko/ui/screens/PageIndicatorTest.kt` 7 例（权重端点 / 拖动插值 / 夹取 / 宽度上下界）。
- **用户复核（第 3 轮批准，m00429）**：去掉 `StarBar` 星级条参考项（不需要），其余落点保留。

---

## 五 批次与顺序

| 批次 | 内容 | 依赖 | 交付物 |
|---|---|---|---|
| **P0（本轮）** | 发现页海报视图（R1） | 无 | 本计划书 + `docs/discover-poster-prototype.html`；实现后：2 个新文件 + 4 处改动 |
| P1 | 玻璃与壁纸一致性（R2 + R3） | P0 合入 | 玻璃材质统一表；壁纸库 UI |
| P2 | 图表与媒体（R4 + R5） | B0 依赖探针结论 | ✅ **R4 改为自绘雷达** `ui/stats/NirikoRadarChart.kt` + 统计页「口味雷达」（compose-charts 无雷达图，不引依赖）；✅ R5 `ui/common/ImageViewer.kt` 换 telephoto 子采样缩放 |
| P3 | 交互部件（R6 + R9） | 无 | ✅ R6 新增 `ui/components/NirikoNonModalSheet.kt` + 分享 sheet 分段 + 日详情改非模态；✅ R9 日期条 + 顶部页面指示（FlowLayout 已满足，未改） |
| P4 | 文本排版（R7） | 依赖探针**未通过** → 自研 | ✅ 新增 `util/RichTextParser.kt` + `ui/components/RichText.kt`；接入 4 处简介渲染 + 统一 6 处纯文本消费方 |
| P5 | M3 Expressive 比对（R8） | 无 | ✅ `docs/m3-expressive-comparison.md`：版本能力矩阵（material3 实测 1.4.0）+ 8 项控件比对表（4 迁移 / 1 保留 / 3 推迟）+ P0/P1/P2 分级迁移清单，逐项含 M3 官方文档依据 |
| **P6** | M3 P0 迁移落地（进度指示器 + 动效统一） | P5 的比对清单 | ✅ 新增 `ui/animation/NirikoMotionSpecs.kt`（1.4.0 的 `MotionScheme`/`MaterialTheme.motionScheme` 为 internal → 按字节码实测自建 StandardMotionTokens 六档弹簧）；8 文件 12 处动效改用 spatial/effects 规格；9 处行内小 spinner 收紧 `gapSize = 0.dp` |

> **批次状态**：**P0 已完成**（m00429 批准 → 已落盘并过编译/单测）；**P1 已完成**（m00558「直接继续」→ 已落盘，`:app:compileDebugKotlin` + `:app:testDebugUnitTest` → 73 类 / 645 用例 / 0 失败）；**P2 已完成**（m00743「继续」→ 已落盘，`:app:compileDebugKotlin` + `:app:testDebugUnitTest` → **74 类 / 653 用例 / 0 失败**，P2 新增代码零告警）；**P3 已完成**（m00981「继续」→ 已落盘，`:app:compileDebugKotlin` + `:app:testDebugUnitTest` → **77 类 / 673 用例 / 0 失败**，P3 新增 3 类 20 例、零告警）；**P4 已完成**（m01180「继续」→ 依赖探针失败后自研，已落盘，`:app:compileDebugKotlin` + `:app:testDebugUnitTest` → **78 类 / 696 用例 / 0 失败**，P4 新增 1 类 23 例、新增代码零告警）；**P5 已完成**（m01180 之后的「继续」→ **只比对不写码**的文档批次，已落盘 `docs/m3-expressive-comparison.md`：material3 实测 1.4.0 的版本能力矩阵 + 8 项控件比对表 + P0/P1/P2 分级迁移清单，未改任何源码与依赖）；**P6 / M3-P0 已完成**（m01556 批准「在动效修改后生成apk供我测试」→ 已落盘，`:app:compileDebugKotlin` + `:app:testDebugUnitTest` → **78 类 / 696 用例 / 0 失败**，P6 新增代码零告警；已 `:app:assembleDebug` 出 debug APK 交用户真机验收）；**P6 之后无已批准的下一批次**。P1 的视觉（真机观感）与 WorkManager 实际轮换、P2 的雷达排版与长图内存、P3 的非模态面板手感与日期条 / 顶部指示器观感、P4 的富文本观感、P6 的动效手感（tween → spatial/effects 弹簧后的列表入场 / 非模态面板 / 页面过渡节奏）与 9 处行内小 spinner 的缺口观感仍未人工验收（P5 为纯文档批次，无观感验收项；其迁移清单里的每项都留了真机检查点）。

**P0 与后续批次的关系**：P0 不引入任何依赖、不改版本目录（`gradle/libs.versions.toml`），因此可与 B0 依赖探针并行推进，互不阻塞。

---

## 六 本轮范围外（明确不做）

1. 卡片列表视图（`TrendingSection.kt`）的任何 UI 改动；
2. 宫格视图（`DiscoverGridPane.kt`）的任何改动；
3. `SubjectSearchViewModel` 的分页/刷新/筛选数据逻辑；
4. 抄 animeko 的分页、云收藏、剧集进度代码（AGPL-3.0 且非本轮目标）；
5. 引入 haze / telephoto / ComposeCharts / FlexibleBottomSheet / compose-richtext 依赖；
6. 壁纸、图表、sheet、富文本的实际实现（属 P1–P4）。

---

## 七 许可与红线

| 仓库 | 许可 | 红线 |
|---|---|---|
| open-ani/animeko | **AGPL-3.0** | **只读设计，禁止抄代码**（源文件头明确 GNU AGPLv3） |
| EasyBangumi | GPL-3.0 | **已出局**，不参考不引用 |
| emertozd/Compose-Material-3-Expressive-Catalog | **无 license 文件** | 无授权即不可复制，只可对照 M3 官方规范 |
| haze / Peristyle / ComposeCharts / telephoto / FlexibleBottomSheet / compose-richtext / ComposeViews | Apache-2.0 | 可参考、可按需引入，但引入前必须过 B0 依赖探针 |

---

## 八 原型索引（`docs/discover-poster-prototype.html`）

| 屏 | 内容 | 标注 |
|---|---|---|
| 第 1 屏 | 现状 · 发现页卡片视图 | 趋势胶囊行 / 上次更新行 / 底部导航标「保持现状」 |
| 第 2 屏 | 改后 · 发现页海报视图（3 列网格） | ① 切换按钮三态 ② 网格几何 ③ 评分+收藏 ④ 两行标题 ⑤ 骨架与入场 |
| 第 3 屏 | 改后 · 海报卡细节 | 单卡放大（两角标位置）、骨架几何、与卡片视图的信息取舍 |
| 第 4 屏 | 改后 · 切换按钮与列数 | 三态按钮映射、3/4/6 列断点、加载与滚动复用的现状说明 |

---

## 九 验收标准（P0 实现后逐条核对）

1. 现有 **628** 个 JVM 单测全绿，新增枚举/列数测试通过；
2. 手机 <600dp 上，卡片视图与宫格视图的渲染**与改造前一致**（对照 `docs/screenshots/`）；
3. 发现页顶部切换按钮三态循环可用，重启 App 后布局保持；
4. 海报视图在手机 3 列 / 平板 4 列 / 大屏 6 列，封面 2:3、圆角 16dp、间距 10dp；
5. 触底自动分页 + 尾部骨架 + 「已经到底了」跨列整行；
6. 断网错误态、Steam 空态、当季热门空态文案与卡片视图一致；
7. 点海报进详情有封面共享元素动画，返回位置不丢；
8. 历史排名筛选器在海报视图下仍可用；
9. 真机滚动 60fps（`RevealOnScroll` + `animateItem` 无明显掉帧）；
10. `gradle/libs.versions.toml` 与 `app/build.gradle.kts` **零改动**。
