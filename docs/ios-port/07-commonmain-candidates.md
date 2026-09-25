# 可进入 commonMain 的代码候选清单

> 基于静态扫描：当前 `app/src/main/java` 中约 252 个 Kotlin 文件，
> 其中 207 个没有直接 `import android.*`。
> 但“没有 android.*”不等于“可直接进 commonMain”，还需要替换 Retrofit/OkHttp/Coil/Java stdlib 等平台相关依赖。
> 本清单用于指导迁移顺序，不代表原样复制。

## A 类：纯 Kotlin 业务代码（迁移成本最低）

这些文件基本只依赖 Kotlin 标准库 / kotlinx.serialization / kotlinx.coroutines / Room 注解，优先迁移。

```text
data/calculator/StatsCalculator.kt
data/calculator/TrendingCalculator.kt
data/filter/FilterDimension.kt
data/filter/PresetFilters.kt
data/model/**                                     # 全部 model
data/search/SearchQueryParser.kt
data/search/TagAliasMap.kt
data/remote/game/GameItem.kt
data/remote/game/GameItemMapper.kt
data/remote/game/PlaytimeConverter.kt
data/remote/mapper/SubjectMapper.kt
data/remote/steam/SteamTitleMatcher.kt
data/remote/steam/SteamChartEntry.kt
data/remote/steam/SteamAchievements.kt
data/remote/steam/dto/**                           # Steam DTO
data/remote/bangumi/dto/**                         # Bangumi DTO
data/remote/anilist/AniListMapper.kt
data/remote/anilist/AniListQueries.kt
data/remote/vndb/dto/VndbDtos.kt
data/sync/bangumi/*                                # Bangumi 同步计划/映射/Planner
data/themepack/ThemePackModels.kt
data/backup/BackupManager.kt                       # 需替换 android.util.Log
plugin/bilibili/BilibiliWebBridge.kt
plugin/bilibili/BilibiliSyncModels.kt
plugin/bilibili/BilibiliMatcher.kt
plugin/kazumi/KazumiHiveReader.kt                 # 需确认是否依赖 Android/Hive 格式
util/**                                            # 工具类
```

## B 类：Room 数据层（迁移成本中等）

需要 Room KMP 支持，实体/DAO 基本不动，但 Database 构建和 Migration API 需要平台化。

```text
data/local/Converters.kt
data/local/WorkDao.kt
data/local/WorkItem.kt
data/local/dao/**                                  # 全部 DAO
data/local/entity/**                               # 全部 Entity
data/local/NirikoDatabase.kt                       # 构建方式需改
data/local/SteamPlaceholderMigrator.kt
```

## C 类：网络层（迁移成本高）

当前使用 Retrofit/OkHttp，需要改写为 Ktor Client。

```text
data/remote/BangumiClient.kt
data/remote/bangumi/BangumiApiService.kt
data/remote/bangumi/BangumiAuthApi.kt
data/remote/anilist/AniListClient.kt
data/remote/bilibili/BilibiliApiService.kt
data/remote/bilibili/BilibiliRatingClient.kt
data/remote/steam/SteamApiClient.kt
data/remote/steam/SteamApiService.kt
data/remote/steam/SteamOpenIdClient.kt              # 需替换 java.net.URLEncoder/URLDecoder
data/remote/vndb/VndbApiClient.kt
data/remote/vndb/VndbApiService.kt
data/sync/WebDavClient.kt
```

## D 类：Repository / ViewModel（大部分可进 commonMain）

```text
data/repository/CollectionRepository.kt
data/repository/WorkRepository.kt
data/repository/SubjectRepository.kt
data/repository/SteamRepository.kt
data/repository/VndbRepository.kt
data/repository/AniListRepository.kt
viewmodel/*.kt
```

注意：
- `SubjectRepository`、`SteamRepository`、`VndbRepository` 等文件虽然当前有 `android.*` import，但通常是 `Context` 或 `Log`，需要抽象后迁移；
- ViewModel 中若直接使用 Android `SavedStateHandle` / `ViewModelProvider.Factory`，需切换为 CMP Lifecycle 对应 API。

## E 类：Compose UI（迁移到 CMP，成本中高）

以下文件没有 `android.*`，但使用了 Jetpack Compose API，需要把依赖切换到 Compose Multiplatform：

```text
navigation/**                 # 导航、底部栏、Pager
ui/**                         # 全部页面/组件/主题
ui/animation/**               # 动画
ui/common/**                  # 搜索组件等
ui/theme/**                   # 主题
```

重点确认：
- `LocalConfiguration` / `LocalSoftwareKeyboardController` / `LocalViewConfiguration`
- `SharedTransitionLayout`
- `HorizontalPager`
- Material Icons Extended

## F 类：只能留在平台层或需要 expect/actual

```text
MainActivity.kt
NirikoApplication.kt
NirikoAppExt.kt
data/repository/NetworkMonitor.kt
data/themepack/ThemePackManager.kt
ui/icon/AppIconManager.kt
ui/wallpaper/WallpaperHost.kt
ui/share/ShareFlow.kt
ui/share/ShareCardRenderer.kt
ui/components/GlassCard.kt
ui/components/BlurredGlassCard.kt
ui/components/liquidglass/*.kt
ui/steam/SteamLoginScreen.kt
ui/bilibili/BilibiliSyncScreen.kt
```

这些文件需要先抽象出平台接口，再把业务部分移入 commonMain，平台实现留在 `androidMain` / `iosMain`。
