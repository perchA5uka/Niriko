# Android 平台 API 存量清单

> 通过扫描 `app/src/main/java` 中 `import android.*` 得到。
> 这些文件是迁移到 KMP/CMP 时最需要抽象或拆分平台壳的部分。

## 统计

- 扫描范围：`app/src/main/java`
- 存在 `android.*` import 的文件：约 50 个
- 高密度文件：图标管理、模糊玻璃、分享卡片、壁纸、WebView 登录

## 高优先级文件（Android 平台 API 最多）

| 文件 | Android API 数 | 说明 |
|---|---:|---|
| `ui/icon/AppIconManager.kt` | 10 | 动态桌面图标，iOS 基本不适用 |
| `ui/components/liquidglass/BlurredCover.kt` | 9 | RenderEffect/RenderNode 模糊 |
| `ui/share/ShareCardRenderer.kt` | 8 | Bitmap/Canvas 分享卡片渲染 |
| `ui/components/GlassCard.kt` | 6 | miuix blur / RenderEffect |
| `ui/components/liquidglass/LiquidGlassShader.kt` | 6 | RuntimeShader |
| `data/repository/NetworkMonitor.kt` | 5 | ConnectivityManager |
| `ui/subject/SubjectDetailScreen.kt` | 5 | Context/系统服务等 |
| `ui/share/ShareFlow.kt` | 4 | FileProvider/Intent 分享 |
| `ui/bilibili/BilibiliSyncScreen.kt` | 4 | WebView 登录 |
| `ui/wallpaper/WallpaperHost.kt` | 4 | Media3/壁纸宿主 |
| `ui/steam/SteamLoginScreen.kt` | 4 | WebView 登录 |
| `ui/settings/pages/AppearanceSettingsScreen.kt` | 3 | 主题/图标相关 |
| `data/themepack/ThemePackManager.kt` | 2 | Context/文件 |
| `ui/settings/pages/ThemePackSection.kt` | 2 | 文件选择 |

## 需要 expect/actual 的领域

### 1. 网络状态
- `data/repository/NetworkMonitor.kt`
- Android：ConnectivityManager / NetworkRequest
- iOS：NWPathMonitor

### 2. WebView 登录
- `ui/steam/SteamLoginScreen.kt`
- `ui/bilibili/BilibiliSyncScreen.kt`
- `plugin/bilibili/BilibiliWebBridge.kt`（JS 注入）
- Android：WebView / WebViewClient
- iOS：WKWebView / WKScriptMessageHandler

### 3. 文件与分享
- `data/backup/BackupManager.kt`
- `data/themepack/ThemePackManager.kt`
- `ui/share/ShareFlow.kt`
- `ui/share/ShareCardLayout.kt`
- `ui/settings/pages/ThemePackSection.kt`
- `ui/settings/pages/SyncBackupSettingsScreen.kt`
- Android：FileProvider / SAF / Intent
- iOS：UIDocumentPicker / UIActivityViewController

### 4. 壁纸与开屏
- `ui/wallpaper/WallpaperHost.kt`
- `ui/splash/SplashExitOverlay.kt`
- `MainActivity.kt`
- Android：Media3 / SplashScreen / Window
- iOS：AVPlayer / LaunchScreen

### 5. 图标与主题
- `ui/icon/AppIconManager.kt`
- `ui/settings/pages/AppearanceSettingsScreen.kt`
- Android：ShortcutManager / LauncherAlias / Bitmap
- iOS：Alternate Icon 或裁剪

### 6. 模糊与玻璃
- `ui/components/liquidglass/*`
- `ui/components/GlassCard.kt`
- `ui/components/BlurredGlassCard.kt`
- Android：RenderEffect / RenderNode / miuix blur
- iOS：待 Spike 确认 CMP 模糊能力，必要时用 UIKit 模糊

### 7. 应用入口与全局
- `MainActivity.kt`
- `NirikoApplication.kt`
- `NirikoAppExt.kt`
- Android：Application / Activity / Context
- iOS：SwiftUI App / ComposeUIViewController

## 迁移提示

- 不要直接把这些文件“复制”到 `commonMain`；
- 应先把它们依赖的 Android API 抽象成接口或 `expect/actual`，再移动业务部分；
- `ui/share/ShareCardRenderer.kt` 如果使用 `android.graphics.Bitmap`，可考虑改为 CMP 的 `ImageBitmap` / Skia Canvas，使渲染逻辑共享。
