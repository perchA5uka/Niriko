# 批次 A 实现报告（v1.2.1）

> 日期：2026-10-18 · 范围：13 张任务卡（`docs/release-v1.2.1-batch-a.md`）
> 结论：**13 张全部落地**；`compileDebugKotlin` 与 `assembleDebug` 通过；定向单测 7 个包全绿；全量 **1220 项中 1219 通过，1 项环境阻塞**（见 §4）。

---

## 1. 每张卡的状态

| 卡 | 状态 | 主要文件 | 测试 |
| --- | --- | --- | --- |
| A-D5 同步错误分类 | ✅ | data/sync/{WebDavClient,SyncManager}.kt、新增 SyncFailureKind.kt | SyncFailureClassificationTest 11 / SyncResultMessageTest 7 |
| A-D6 时间戳 | ✅ | data/repository/CollectionRepository.kt（maxOf(now, 旧值)） | CollectionTimestampTest 5 |
| A-D3 卷进度同步 | ✅ | data/sync/{SyncMergePolicy,CollectionCodec}.kt、data/backup/BackupManager.kt | SyncMergePolicyTest +98、CollectionJsonRoundTripTest 7 |
| A-D8 私密并集 | ✅ | 与 D3 同一处（isPrivate = local ‖ remote） | 同上（合并用例） |
| A-D4 条件写入 | ✅ | 新增 SyncConcurrencyPolicy.kt / SyncBaselineStore.kt；SyncManager/WebDavClient；SettingsViewModel/SyncBackupSettingsScreen（412 三选一） | SyncConcurrencyPolicyTest 11 |
| A-D1 观看量分流 | ✅ | 新增 data/calculator/WatchTotals.kt；StatsCalculator / CollectionViewModel / CollectionDashboard / StatsScreen / StatsViewModel / StatsUiState | WatchTotalsTest 8 |
| A-D2 放送时区 | ✅ | 新增 data/onair/AirTime.kt；CalendarDaySheet、通知 composer/worker、详情页放送行 | AirTimeTest 8、AiringNotificationComposerZoneTest 5（既有 7 例仍绿） |
| A-X1 Steam 登录 | ✅ | SteamOpenIdClient（桌面 UA / isCallbackUrl / httpErrorPage）、SteamLoginScreen（UA / onReceivedHttpError / reloadSeq）、SteamLoginViewModel（loggingIn） | SteamOpenIdClientTest +7、SteamLoginUiStateTest 3 |
| A-X2 删兜底开关 | ✅ | DataSourceSettingsScreen（删 picker 块）/ SettingsViewModel（删 getter 与 setter）/ AppSettings / SettingsDataStore（4 处）/ BackupManager（2 处） | 引用残留 0 处（仅 1 句注释提到旧名） |
| A-D7 链动态读取 | ✅ | data/plugin/DataSourceChain.kt（主构造改 `() -> List` + 兼容 List 构造）、NirikoApplication（传 live provider） | 新增 DataSourceChainProviderOrderTest 2 例（换序即时生效 / 旧构造仍是快照） |
| A-X3 R18 首帧动画 | ✅ | NirikoNavHost（rootSettings 种子 + LocalSettingsSeed）、AdaptiveSettingsPane（宽屏同一份种子）；SearchSettingsScreen 零改动 | SettingsFirstFrameSeedTest 4 |
| A-X4 单本隐藏卷进度 | ✅ | 新增 util/VolumeProgressPolicy.kt；SubjectDetailScreen 两处调用 | VolumeProgressPolicyTest 6 |
| A-X5 保存不退出详情 | ✅ | SubjectDetailScreen（删 onBack、成功事件收层）、SubjectDetailViewModel（recordSavedEvents、written 分支）、新增 util/CollectionSavePolicy.kt | CollectionSavePolicyTest + 3 个策略类共 16 例 |
| D1 写入侧 | ✅ | util/CompletionProgressPolicy.kt + SubjectDetailViewModel.updateStatus | CompletionProgressPolicyTest 6 |

> 我在集成阶段改动/补充的地方：① `SubjectDetailScreen` 的 `recordSavedEvents` 没接线（编译红），我补了 `Flow<Unit>` 参数贯通；② 把 MANGA 单卷也纳入隐藏（按你确认的裁决 2），同时改了 `VolumeProgressPolicyTest`；③ 详情页放送行接上 `AirTime`（refs-time 留的一行尾巴）。

---

## 2. 版本与兼容

| 常量 | 旧 | 新 | 兼容行为 |
| --- | --- | --- | --- |
| `SYNC_VERSION` | 3 | **4** | v4 增 `watchedVolumes`；旧远端文件缺键 → 解析为 null，不覆盖本地 |
| `EXPORT_VERSION` | 4 | **5** | 旧备份（≤v4）照常导入；旧 App 读 v5 会显式拒绝（不静默降级） |

---

## 3. 验证

- `:app:compileDebugKotlin` → BUILD SUCCESSFUL（9m29s，49 个改动文件）。
- `:app:assembleDebug` → BUILD SUCCESSFUL（5m22s）。
- 定向单测（sync / calculator / onair / util / settings-seed / steam / notification）→ BUILD SUCCESSFUL。
- 全量 `:app:testDebugUnitTest` → **1220 项，1219 通过，1 项环境阻塞**（5m13s）。

---

## 4. 唯一失败项：不是代码问题

```
GlassProviderIdentityTest > initializationError
java.lang.UnsupportedClassVersionError: com/kyant/backdrop/Backdrop
  has been compiled by a more recent version of the Java Runtime (class file version 65.0),
  this version of the Java Runtime only recognizes class file versions up to 61.0
```

`kyant backdrop` 的制品是 Java 21 编译的（65.0），本机 JDK 是 17（61.0），且机器上只装了 JDK 17 与 JRE 8。**与本批次改动无关**：这个测试只要加载 `Backdrop` 类就会失败。
**已解决（2026-10-18）**：改用 JDK 21（`C:\Program Files\Android\Android Studio\jbr`，21.0.10）重跑全量 → **1220 项全绿，BUILD SUCCESSFUL（17m49s）**，无需改任何测试。参考命令：`./gradlew :app:testDebugUnitTest -Dorg.gradle.java.home="C:\Program Files\Android\Android Studio\jbr"`。

---

## 5. 遗留（需真机或后续批次）

| 项 | 为什么没做完 | 怎么验 |
| --- | --- | --- |
| Steam 登录首帧 | 需要真实网络（国区 + 代理各一次） | 进登录页不再 403；`adb logcat -s SteamOpenId` 第一条 URL 带 https return_to |
| WebDAV 412 端到端 | 需要两台设备/两个客户端 | A 改评分、B 改卷进度 → B 上传得 412 → 三选一弹窗 → 自动重合并一次 |
| `timeCN` 基准 | 代码无法证明是 JST 还是已换算的中国时间 | 抽 3 部番比对真实首播；若为 JST，把调用点或 `AirTime.DEFAULT_ZONE_ID` 改成 `Asia/Tokyo` |
| D4 多一次 HEAD | 设计取舍（HEAD 决定 If-Match） | 观察同步日志请求数；弱校验服务器会退回基线 etag |
| 统计时长配色 | 沿用 `chartTypeColor(REAL)` | 视觉确认后换 token |
| 死代码清理 | `SettingsViewModel.pluginManager` 参数已无人使用；`PluginManager.moveToTop/moveUp/moveDown/primary` 无调用点 | 下一轮清掉（不在本批次授权文件） |
| 全量绿灯 | JDK 17 环境限制 | 见 §4 |

---

## 6. 下一步

批次 B（视觉与交互一致性）可以直接开始：它只改 UI 层，与批次 A 的数据层改动无依赖。批次 C 需要 v33 迁移，建议等 B 收口后单独开一个迁移窗口。