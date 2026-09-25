# 目标模块结构

> 迁移最终形态：`shared` 持有全部业务与 UI，`androidApp` 和 `iosApp` 只做平台壳。

## 推荐目录

```text
Niriko/
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/otakup/niriko/
│       │   ├── AppGraph.kt              # 双端依赖组装
│       │   ├── data/
│       │   │   ├── local/               # Room entities/dao/database/migrations
│       │   │   ├── remote/              # Ktor clients + DTO + fetchers
│       │   │   ├── repository/
│       │   │   ├── calculator/
│       │   │   ├── search/
│       │   │   ├── settings/
│       │   │   ├── backup/
│       │   │   ├── sync/
│       │   │   └── themepack/
│       │   ├── navigation/
│       │   ├── ui/                      # Compose Multiplatform 页面/组件/主题
│       │   ├── viewmodel/
│       │   ├── plugin/
│       │   └── util/
│       ├── androidMain/kotlin/com/otakup/niriko/
│       │   ├── Platform.android.kt       # expect/actual 的 Android 实现
│       │   ├── database/
│       │   ├── webview/
│       │   ├── share/
│       │   ├── wallpaper/
│       │   └── icon/
│       └── iosMain/kotlin/com/otakup/niriko/
│           ├── Platform.ios.kt
│           ├── MainViewController.kt    # CMP 入口
│           ├── database/
│           ├── webview/
│           ├── share/
│           └── wallpaper/
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/otakup/niriko/
│           ├── NirikoApplication.kt
│           └── MainActivity.kt
├── iosApp/
│   ├── iosApp.xcodeproj
│   ├── iosApp/
│   │   ├── iOSApp.swift
│   │   ├── Info.plist
│   │   └── Assets.xcassets
│   └── Configuration/Config.xcconfig
└── gradle/libs.versions.toml
```

## 代码放置规则

### 必须放 `commonMain`
- 所有业务模型、DTO、Entity
- DAO、Repository、Calculator、Parser、Matcher
- ViewModel、导航、Compose UI、主题
- 备份格式、WebDAV 同步、Bangumi 同步
- `expect` 声明和 `interface` 平台抽象

### 必须放 `androidMain`
- `android.*` / `androidx.*` 平台 API
- Application/Activity 壳
- Android WebView、FileProvider、ShortcutManager、Media3、miuix blur

### 必须放 `iosMain`
- `UIKit` / `Foundation` / `WKWebView`
- `MainViewController`
- iOS 文件系统、分享、NWPathMonitor、AVPlayer

### 必须放 `iosApp`（Swift）
- `@main` 入口
- `ComposeUIViewController` 的 SwiftUI 包装
- `Info.plist`、App Icon、LaunchScreen

## 迁移顺序

1. `shared` 模块创建，仅包含纯 Kotlin 代码
2. 数据层迁移（Room/Ktor）
3. ViewModel/导航迁移
4. UI 迁移
5. `androidApp` 瘦身
6. `iosApp` 接入
