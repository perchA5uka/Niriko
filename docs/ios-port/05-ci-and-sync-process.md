# 长期双端同步与 CI 流程

## 核心原则

1. **功能默认进 `commonMain`**
2. **平台代码只做“能力适配”，不做业务**
3. **数据库 schema、备份 JSON、WebDAV 协议、同步计划必须双端唯一**
4. **每次 PR 必须同时通过 Android 与 iOS 构建**

## 分支与提交规范

- 新功能从 `master` 或 `develop` 拉分支
- 涉及 UI/业务的功能，diff 中 `commonMain` 应占绝大多数
- 如果某个功能只能在单端实现，必须写清理由并更新功能矩阵

## CI 要求

### Android
```bash
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
```

### iOS
```bash
xcodebuild \
  -workspace iosApp/iosApp.xcworkspace \
  -scheme iosApp \
  -sdk iphonesimulator \
  -configuration Debug \
  build
```

### 质量检查
- `commonMain` 禁止出现 `android.*` / `UIKit` / `Foundation` import
- 可使用 detekt + 自定义规则自动拦截
- 数据库 migration 测试必须在 Android 与 iOS 都跑
- 备份 JSON 兼容性测试双端跑

## 新功能开发流程

1. 在 `commonMain` 添加模型/Repository/ViewModel/UI
2. 如遇平台能力，在 `commonMain` 定义 `expect` 或接口
3. 在 `androidMain` / `iosMain` 分别实现 actual
4. 更新功能矩阵
5. 提交 PR，双端 CI 通过后合并

## 版本发布

- Android 与 iOS 使用同一版本号
- 数据库 migration、备份格式变更必须同版本发布
- WebDAV/Bangumi 同步协议变更需要向后兼容，至少保留一个版本窗口

## 文档维护

- `README.md` 增加“跨平台架构”章节
- `docs/ios-port/*` 随迁移进度持续更新
