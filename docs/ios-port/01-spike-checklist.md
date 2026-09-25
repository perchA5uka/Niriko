# Phase 0：技术预研 Spike Checklist

> 目的：在正式迁移前，用最小 Demo 验证 iOS 端关键依赖是否可用、现有复杂 UI 是否能在 Compose Multiplatform 上运行。

## 环境要求

- macOS + Xcode（iOS 编译必需）
- JDK 17+
- 可访问 Maven Central / Google Maven
- Android Studio（可选，用于 Android target 调试）

## 0. 工程骨架

- [ ] 新建 KMP 项目，包含 `androidTarget()` 与 `iosArm64()/iosSimulatorArm64()`
- [ ] 接入 Compose Multiplatform Gradle Plugin
- [ ] Android 能运行一个 Compose 页面
- [ ] iOS 能通过 Xcode 运行同一个 Compose 页面
- [ ] 确认 `embedAndSignAppleFrameworkForXcode` 或 Swift Package 集成方式可用

## 1. 数据层验证

### Room KMP
- [ ] Room KMP 是否支持当前 Room 2.8.4 / KSP 2.3.11
- [ ] 现有实体 / DAO / TypeConverter 是否可不改动迁入 `commonMain`
- [ ] 现有 `SupportSQLiteDatabase` Migration 在 iOS 上是否可用，若不可用需要改成什么 API
- [ ] 用现有 `app/schemas/*/20.json` 在 iOS 上创建 v20 数据库并跑通迁移链
- [ ] 外键、索引、事务、Flow 查询在 iOS 上行为一致

### DataStore
- [ ] `androidx.datastore:datastore-preferences` 多平台版本在 iOS 上可用
- [ ] 设置读写、文件位置与 Android 一致

### 网络层
- [ ] Ktor Client Darwin 引擎在 iOS 上可发起 HTTPS 请求
- [ ] Bangumi 动态 baseUrl 重写逻辑可迁移
- [ ] Authorization / User-Agent / Cookie 行为与 OkHttp 对齐
- [ ] WebDAV 的 PROPFIND/PUT/GET 等自定义方法在 Ktor 上可用
- [ ] 超时、错误体解析、取消请求行为一致

### 图片加载
- [ ] Coil 3 在 commonMain 可加载网络图
- [ ] 图片 URL 重写 Interceptor 可迁移
- [ ] 封面共享元素过渡所需图片缓存行为可接受

## 2. UI 层验证

- [ ] Compose Multiplatform 能渲染当前 Material 3 主题
- [ ] `HorizontalPager` 可用
- [ ] 自定义底部导航、悬浮胶囊可用
- [ ] 共享元素过渡（SharedTransitionLayout）在 iOS 上可用/可替代
- [ ] 搜索组件中的手势锁、软键盘控制、焦点管理在 iOS 上可用
- [ ] 列表滚动性能（作品库、发现页长列表）达到可接受水平
- [ ] 玻璃/模糊效果在 iOS 上可用；miuix blur 不可用时的降级方案

## 3. 平台能力验证

- [ ] WKWebView 可承载 Steam OpenID 登录
- [ ] WKWebView 可承载 B 站登录 + JS 注入取数
- [ ] UIDocumentPicker 可导入/导出 `.nirikotheme`
- [ ] UIActivityViewController 可分享 JSON 备份和分享卡片
- [ ] NWPathMonitor 网络状态监测可用
- [ ] AVPlayer 动态壁纸可行性；不可行则首版关闭
- [ ] 后台任务限制：前台恢复触发自动匹配/预取

## 4. 输出物

- [ ] 一份“可用/不可用/需替换”依赖清单
- [ ] 一份 CMP 在 iOS 上的 UI 兼容性报告
- [ ] 是否继续正式迁移的 Go/No-Go 结论
