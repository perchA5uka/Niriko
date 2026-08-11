# Niriko — 追番收藏统计应用

Kotlin + Jetpack Compose + Material 3 单 Activity 原生 Android 应用。
以 Bangumi 为主数据源（AniList 自动兜底、Steam 补充游戏商业数据），提供作品收藏管理、发现/搜索、收藏统计与 WebDAV 数据同步。

## 功能特性

### 底部导航（四个顶级页面，HorizontalPager 承载）

| Tab | 说明 |
| --- | --- |
| 作品库 | 收藏管理：状态/评分/观看进度/个人标签，关键词搜索、状态与标签筛选、多种排序，长按卡片快速改状态；另有"收藏的人物"横向分区 |
| 发现 | 当季热门 / 历史排名榜单，按类型浏览与触底分页；支持 Bangumi 语法搜索（`tag:`/`sort:`）、NSFW 开关、地区/版本/类型等高级筛选面板、搜索建议与历史记录 |
| 统计 | 作品日历（个人记录 / 放送信息 / 全部三种模式，跨月番剧准确落格）、时间线卡片堆叠、收藏概览（作品数/均分/完成率）、收藏状态分布环形图、作品类型分布、个人评分 vs Bangumi 评分分布、年度总结（当前年 + 历年） |
| 设置 | 主题（跟随系统 / 浅色 / 深色、动态取色、OLED 深色）、默认排序、启动页、NSFW 与搜索建议开关、Steam API Key（可选）、WebDAV 同步配置（含自动同步）、JSON 全量备份导出 / 导入恢复、Kazumi 收藏导入、哔哩哔哩追番与评分导入 |

### 二级页面

- 作品详情：元数据、角色 / 制作人员 / 关联作品、评分分布（跳转路由 `subject_detail/{id}`）
- 人物详情、角色详情、Staff 列表（`person_detail/{id}`、`character_detail/{id}`、`staff_list/{id}`）
- 全屏搜索、漫搜：作品库空态"开始探索"进入的沉浸式搜索页（`subject_search`）；发现页顶栏内置趋势标签/类型切换的搜索入口

### 其他

- 封面共享元素过渡（列表卡封面 → 详情页缩放飞入）
- Liquid Glass 风格悬浮胶囊底栏与玻璃卡片（纯 Compose 自实现，无第三方模糊库）
- 搜索建议浮层背景模糊（Android 12+ 生效，低版本自动降级）
- 离线可用：搜索结果与详情自动落库，远程失败时回退本地缓存
- **Steam 游戏补充数据**：GAME 类型作品在搜索/作品库/详情页自动匹配 Steam（storesearch + 标题置信度匹配，绑定落库免重复匹配），补充展示价格、当前在线人数、开发商/发行商、Metacritic、Steam 标签与截图；数据存独立扩展表 `steam_games` / `steam_bindings`，不污染核心作品模型，未来可扩展 VNDB/PSN 等平台
- **Steam 账号与游戏库导入**：OpenID 2.0 登录（WebView + 手动 SteamID64 兜底）→ GetOwnedGames 拉取游戏库 → 匹配 Bangumi → 勾选导入收藏（游玩时长推断状态）；Bangumi 无词条的游戏以「Steam 独占」占位条目展示，可重新匹配升级为正式词条
- **哔哩哔哩导入**：设置页进入导入工作流，WebView 内登录 B 站后自动拉取追番列表、用户评分与短评（参考 Bangumi-master bilibili-sync 方案，经注入 JS 在 B 站会话内取数），按 season_id / 标题匹配到 Bangumi 条目后勾选一键导入本地收藏；原始数据单独落 `bilibili_sync_items` 表，默认不覆盖已有评分

## 技术栈

- **UI**：Jetpack Compose + Material 3（Compose BOM）、Navigation Compose、Material Icons Extended
- **数据层**：Room（收藏 / 作品元数据 / 手工作品 / 搜索历史 / 人物收藏 / Steam 扩展表与库快照，含迁移链 v2→v16）、DataStore Preferences（设置持久化）
- **网络**：Retrofit + OkHttp + Kotlin Serialization；数据源以插件链形式注册（Bangumi 主源 + AniList 兜底，可按能力调度）；Steam 商店/Web API 作为游戏补充数据源（storesearch / appdetails / GetNumberOfCurrentPlayers，公开接口无需 key）
- **图片**：Coil Compose（显示尺寸感知解码 + 小图约束）
- **架构**：单 Activity + ViewModel（手写 Factory，无 DI 框架）、Repository 层、纯函数计算器（StatsCalculator / TrendingCalculator，可单测）

## Steam 数据源

Steam 作为 **GAME 类型作品的补充数据源**（非替代）：Bangumi 仍负责作品语义元数据（标题/简介/评分/角色/Staff/关联），Steam 负责商业与运行数据（价格/开发商/发行商/Metacritic/当前在线/标签/截图），两者通过 `subjectId` 关联融合展示。

### 数据模型（独立扩展表，不污染 subjects 表）

- `steam_bindings`：`subjectId ↔ steamAppId` 绑定关系（matchMethod / confidence），命中后免重复匹配
- `steam_games`：Steam 扩展数据（价格分/币种/开发商/发行商/Metacritic/当前在线/标签/截图/发行日期）

未来可同样方式扩展 VNDB / PSN / Xbox 等平台数据。

### 匹配流程

1. 搜索/作品库/详情页遇到 GAME 类型条目 → 检查是否已有绑定
2. 未绑定 → `storesearch`（`l=schinese&cc=CN`）按标题搜索 → `SteamTitleMatcher` 归一化 + 置信度评分（阈值 0.7）→ 绑定落库
3. 详情页打开 → `appdetails` 拉取完整详情 + `GetNumberOfCurrentPlayers` 拉取在线人数（30 分钟缓存）→ 落库
4. 匹配失败静默，不影响搜索/详情主流程

### 账号登录与游戏库导入

设置页 →「Steam 账号与游戏库」进入导入工作流：登录 → 拉取游戏库 → 匹配 Bangumi → 勾选导入收藏。

- **登录（Steam OpenID 2.0）**：WebView 承载 `steamcommunity.com/openid/login`（社区常称 "Sign in through Steam"），回跳 `niriko://steam-auth` 拦截并提取 SteamID64；登录本身无需 API key。`steamcommunity.com` 不可达时（如无代理）可**手动输入 SteamID64** 兜底
- **拉库（GetOwnedGames）**：`IPlayerService/GetOwnedGames` 需要 `key` + `steamid64`——用**用户自己的 key 查自己的库**（隐私私密也可见），key 在设置页「Steam API Key」配置；快照落 `steam_library_items` 表
- **匹配**：优先复用 `steam_bindings` 已绑定关系，未绑定用标题走 Bangumi 搜索（GAME）+ 置信度匹配
- **导入**：勾选后导入收藏；状态按游玩时长推断（`playtime>0 → WATCHING`，未玩 `→ PLAN_TO_WATCH`），游玩时长（分钟）写入游戏时长字段；已存在收藏跳过
- **Steam 独占占位条目**：Steam 有词条、Bangumi 无的游戏，以负数占位 `subjectId = -appId` 创建条目（`sourceId="steam"`），像普通作品一样进作品库/可收藏/可打开详情页，卡片与详情页显示「Steam 独占」标记；可在详情页或导入预览页点「重新匹配」升级为正式 Bangumi 词条（收藏/绑定/扩展数据原子迁移）

### API 策略

- 公开接口（storesearch / appdetails / GetNumberOfCurrentPlayers）无需 API key，开箱即用
- 设置页可选填 Steam Web API key（DataStore 持久化，预留扩展用；当前请求不附加 key 避免日志泄漏）
- 匹配请求分块并发（每批 5 个）防止打满商店接口限流

## 环境要求

- Android Studio Hedgehog 或更新版本
- JDK 17+（推荐使用 Android Studio 自带 JBR）
- Android SDK 34+

## 打开与运行

1. 用 Android Studio 打开本目录（`niriko`）
2. 等待 Gradle Sync 完成
3. 选择模拟器或真机，点击 Run

命令行构建（需 JDK 17）：

```bash
.\gradlew.bat assembleDebug
```

构建产物位于 `app\build\outputs\apk\debug\app-debug.apk`。

运行单元测试：

```bash
.\gradlew.bat testDebugUnitTest
```

## 包名与 SDK

| 项 | 值 |
| --- | --- |
| ApplicationId | `com.otakup.niriko` |
| minSdk | 26 |
| targetSdk | 34 |
| compileSdk | 37（miuix-blur 0.9.0 硬要求；运行时行为由 targetSdk 34 决定） |

## 目录结构

```
app/src/main/java/com/otakup/niriko/
├── data/
│   ├── local/          # Room 数据库、DAO、实体（含 steam_games/steam_bindings/steam_library_items）
│   ├── remote/         # Bangumi / AniList / Steam 客户端、DTO、Fetcher
│   ├── repository/     # Collection / Subject / Work / Steam 仓库
│   ├── calculator/     # 统计与趋势纯函数计算
│   ├── filter/         # 搜索筛选维度与预设
│   ├── search/         # 搜索语法解析与标签别名
│   ├── settings/       # DataStore 设置
│   ├── backup/         # JSON 备份导出/恢复
│   └── sync/           # WebDAV 同步（LWW 合并）
├── plugin/             # 数据源插件链 + 导入器（DataSourceChain / PluginManager / kazumi / bilibili）
├── navigation/         # 底部导航、Pager、NavHost
├── ui/                 # 各页面与通用组件（玻璃卡片、封面、搜索栏等）
├── viewmodel/          # ViewModel 与工厂
└── util/               # 标题解析、封面 URL、状态辅助等
```

## 主要依赖

- Compose BOM + Material 3、Material Icons Extended
- Navigation Compose
- Room（Runtime / KTX / Compiler）
- Coil Compose
- DataStore Preferences
- Retrofit + OkHttp + Kotlin Serialization
