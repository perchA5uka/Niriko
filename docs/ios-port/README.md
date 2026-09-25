# Niriko iOS 移植工作区

> 本目录是 iOS 移植（Kotlin Multiplatform + Compose Multiplatform）的工程化工作区。
> 目标：最大化共享 Android/iOS 代码，保证后续功能更新双端同步。

## 当前状态

- 已创建独立分支：`feature/ios-port-kmp`
- 已完成方案选型：**KMP 共享业务 + Compose Multiplatform 共享 UI**
- 本机（Windows）无法完成 iOS 编译/链接，且当前 Gradle 缓存缺少 Ktor/CMP Gradle Plugin，
  因此 Phase 0 的实际构建 Spike 需要在有网络/有 macOS/Xcode 的环境执行。

## 工作区内容

| 文件 | 说明 |
|---|---|
| `01-spike-checklist.md` | Phase 0 技术预研清单 |
| `02-dependency-migration-map.md` | Android 依赖 → 多平台依赖迁移对照 |
| `03-feature-parity-matrix.md` | 功能双端对齐矩阵 |
| `04-module-layout.md` | 目标模块结构与代码放置规范 |
| `05-ci-and-sync-process.md` | 长期双端同步与 CI 流程 |
| `06-platform-api-inventory.md` | Android 平台 API 存量清单与迁移提示 |
| `07-commonmain-candidates.md` | 可进入 commonMain 的代码候选清单 |
| `08-network-ktor-migration-draft.md` | Retrofit/OkHttp → Ktor 迁移草案 |
| `09-database-kmp-draft.md` | Room KMP + DataStore 迁移草案 |
| `10-platform-abstractions.md` | expect/actual 平台抽象接口草案 |
| `11-gradle-module-draft.md` | shared KMP 模块 Gradle 草案 |

## 执行顺序

1. 在可联网 + 有 Xcode 的机器上跑通 `01-spike-checklist.md`；
2. 按 `02-dependency-migration-map.md` 迁移数据层；
3. 按 `04-module-layout.md` 拆分模块；
4. 按 `03-feature-parity-matrix.md` 验收功能；
5. 按 `05-ci-and-sync-process.md` 固化双端同步机制。
