# 依赖迁移对照表

> 基于 `app/build.gradle.kts` 和 `gradle/libs.versions.toml` 整理。

## 当前依赖 → 多平台目标

| 当前依赖 | 当前用途 | 目标方案 | 风险 |
|---|---|---|---|
| Jetpack Compose BOM | UI | Compose Multiplatform（`org.jetbrains.compose.*`） | 中：部分 API 在 iOS 行为差异 |
| Material 3 | UI 组件 | Compose Multiplatform Material 3 | 低 |
| Material Icons Extended | 图标 | CMP Material Icons 或自绘图标 | 低 |
| Navigation Compose | 导航 | CMP Navigation 或 Voyager/Decompose | 中：路由/返回栈需验证 |
| Room | 本地数据库 | Room KMP | 高：Migration API 兼容性需 Spike |
| DataStore Preferences | 设置 | DataStore 多平台 | 低 |
| Retrofit + OkHttp | 网络 | Ktor Client | 高：需要逐客户端迁移 |
| Kotlin Serialization | JSON | 保留 | 低 |
| Coil 2 | 图片加载 | Coil 3 | 中：API 差异 |
| material-color-utilities | HCT 配色 | 验证是否 common；不行则 expect/actual | 中 |
| Media3 / ExoPlayer | 动态壁纸 | AVPlayer（iOS）或首版裁剪 | 中 |
| core-splashscreen | 开屏 | iOS LaunchScreen | 低 |
| miuix-blur | 玻璃模糊 | CMP 模糊 / UIKit 模糊 / 降级 | 中 |
| WebView | Steam/B 站登录 | WKWebView（iOS） | 中：Cookie/JS 注入 |
| FileProvider / SAF | 文件分享/导入 | UIDocumentPicker / UIActivityViewController | 中 |
| ShortcutManager | 动态桌面图标 | iOS Alternate Icon（受限）或裁剪 | 中 |

## 需要替换的具体代码区域

### 网络客户端
- `BangumiClient`：OkHttp Interceptor → Ktor Plugin/请求管线
- `AniListClient`：OkHttp 直接请求 → Ktor
- `BilibiliRatingClient`：Retrofit → Ktor
- `SteamApiClient`：Retrofit + OkHttp → Ktor
- `VndbApiClient`：Retrofit → Ktor
- `WebDavClient`：OkHttp 自定义方法 → Ktor

### 平台能力
- `NetworkMonitor`：ConnectivityManager → expect/actual（Android 保留，iOS 用 NWPathMonitor）
- `SettingsDataStore`：Context 初始化 → expect/actual 路径
- `NirikoDatabase.getInstance(Context)` → expect/actual 数据库工厂
- `ThemePackManager`：Context/SAF → expect/actual 文件访问
- `BackupManager`：文件读写/分享 → expect/actual
- `ShareFlow` / `ShareCardRenderer`：Bitmap/FileProvider → CMP 渲染 + iOS Share
- `SteamLoginScreen` / `BilibiliSyncScreen`：Android WebView → expect/actual WebView 容器
- `WallpaperHost`：Media3 → expect/actual 视频播放
- `AppIconManager`：Android ShortcutManager → iOS 裁剪/Alternate Icon

### UI 平台 API
- `LocalConfiguration.screenWidthDp`：CMP 对应 API 或 `LocalWindowInfo`
- `LocalSoftwareKeyboardController`：CMP 对应 API
- `LocalViewConfiguration`：CMP 对应 API
- `SharedTransitionLayout`：验证 CMP 支持
- `layerBackdrop` / miuix blur：验证或替换

## 迁移顺序建议

1. 先迁纯 Kotlin 代码（model/DTO/calculator/parser/matcher）
2. 再迁 Repository + Room
3. 再迁网络层（Ktor）
4. 再迁 ViewModel + 导航
5. 最后迁 UI 与平台壳
