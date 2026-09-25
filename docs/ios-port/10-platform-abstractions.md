# 平台抽象草案：expect/actual 与接口

> 目标：把 Android/iOS 平台差异隔离在 `androidMain` / `iosMain`，业务代码只依赖 common 接口。
> 这些接口只是草案，最终需要按实际调用点调整。

## 1. 应用上下文

```kotlin
// commonMain
interface NirikoPlatformContext {
    val filesDir: String
    val cacheDir: String
    val packageName: String
}

expect fun currentPlatformContext(): NirikoPlatformContext
```

## 2. 网络状态

```kotlin
// commonMain
interface NetworkMonitor {
    val isOnline: Boolean
    val connectivity: Flow<Boolean>
}

expect fun createNetworkMonitor(): NetworkMonitor
```

Android 保留现有 `ConnectivityManager` 实现；iOS 用 `NWPathMonitor` 实现。

## 3. 日志

```kotlin
// commonMain
interface NirikoLogger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

expect fun nirikoLogger(): NirikoLogger
```

Android 可继续委托 `android.util.Log`；iOS 可委托 `NSLog` / `os.Logger`。

## 4. 文件系统

```kotlin
// commonMain
interface PlatformFileSystem {
    fun readBytes(path: String): ByteArray?
    fun writeBytes(path: String, bytes: ByteArray): Boolean
    fun delete(path: String): Boolean
    fun listFiles(dir: String): List<String>
    fun createDirectories(path: String): Boolean
}

expect fun platformFileSystem(): PlatformFileSystem
```

用于替代：
- `ThemePackManager` 中的 `java.io.File`
- `BackupManager` 的导入/导出文件读写
- `ShareFlow` 的临时文件写入

## 5. 分享

```kotlin
// commonMain
interface ShareLauncher {
    fun shareText(text: String)
    fun shareImage(imageBytes: ByteArray, fileName: String, summaryText: String)
    fun shareFile(path: String, mimeType: String)
}

expect fun createShareLauncher(): ShareLauncher
```

Android：FileProvider + `ACTION_SEND`；iOS：`UIActivityViewController`。

## 6. 文件选择器

```kotlin
// commonMain
interface FilePicker {
    suspend fun pickFile(mimeTypes: List<String>): String?
    suspend fun pickThemePack(): String?
    suspend fun exportFile(fileName: String, bytes: ByteArray)
}
```

Android：SAF / `ActivityResultContracts`；iOS：`UIDocumentPickerViewController` / `UIDocumentPickerDelegate`。

## 7. WebView 登录容器

```kotlin
// commonMain
interface LoginWebViewHost {
    fun open(
        url: String,
        onPageStarted: (String) -> Unit,
        onUrlIntercepted: (String) -> Boolean,
        onPageFinished: (String) -> Unit,
    )
    fun evaluateJavascript(script: String)
    fun close()
}
```

Android：`WebView` / `WebViewClient`；iOS：`WKWebView` / `WKNavigationDelegate` / `WKScriptMessageHandler`。

## 8. 动态壁纸视频播放

```kotlin
// commonMain
interface VideoWallpaperPlayer {
    fun setVideo(uri: String)
    fun play()
    fun pause()
    fun release()
}
```

Android：Media3 ExoPlayer；iOS：AVPlayer / AVPlayerLayer。

## 9. 主题包文件访问

```kotlin
// commonMain
interface ThemePackFileAccess {
    suspend fun importFromUri(uri: String): ThemePackImportResult
    fun exportInstalledPack(id: String): String?
    fun exportCurrentTheme(...): String?
    fun deleteInstalledPack(id: String)
}
```

该接口可直接包住 `ThemePackManager` 的平台文件部分。

## 10. 数据库工厂

```kotlin
// commonMain
expect fun createNirikoDatabase(): NirikoDatabase
expect fun createSettingsDataStore(): DataStore<Preferences>
```

## 11. 迁移约束

- `commonMain` 中禁止直接出现 `android.*` / `UIKit` / `Foundation`；
- 平台接口要按“调用方需要什么”设计，而不是把 Android API 原样搬过去；
- 新增平台能力时先加接口 + `expect`，再分别实现 actual。
