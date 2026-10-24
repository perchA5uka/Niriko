# 批次 A · 数据正确性任务卡（A-D1–A-D7）

> 范围：`docs/release-v1.2.1-plan.md` §3「修复」行的 7 条（观看量混单位 / 时区只写不读 / 卷进度不同步 / WebDAV 裸 PUT / 401 当成功 / updateStatus 不更新时间戳 / 源顺序重启丢失），逐条依据见 `docs/feature-refs/05-stats.md` 5.6+5.19、`04-calendar.md` 4.15、`07-sync.md` 7.11+7.17、`02-sources.md` 2.3。
> 编号：本文件 A-D1…A-D7 = 任务板 D1…D7。模板照 `docs/v1.2.1/_tasks-brief.md`（9 字段）。
> 只读产出：本文件不含任何代码改动；所有 `file:line` 均已回当前工作区逐条读过确认。
> 共同前置：`./gradlew :app:assembleDebug` 通过；新增纯函数配 `:app:testDebugUnitTest`。Room 当前 `data/local/NirikoDatabase.kt:62 → version = 32`；**本文件 7 张卡均不动表结构**（不占 v33），也不新增写 DAO。

## ⚠ 与计划不符（7 张卡共 8 条，先看这里）

| 卡 | 计划/域文件写法 | 代码实际 |
| --- | --- | --- |
| A-D1 | 5.19 回退链含 `subject.durationSeconds` | `SubjectEntity` **没有** duration 字段（只有 `SubjectDetailData.kt:61`、`EpisodeEntity.kt:33`），"作品级时长"回退不存在；且 `computeStats` 只有 items，拿不到 episodes |
| A-D1 | 只改统计页 | 同一缺陷还有第二份实现：`CollectionViewModel.kt:145` → `CollectionDashboard.kt:43`「累计 X 集」；另有 `StatsScreen.kt:615,662` 的年表口径；集数里还混入音乐曲目（`SubjectDetailViewModel.kt:1360-1361`） |
| A-D2 | 4.15 收益一半给 .ics | 4.8 `.ics` **未实现**（`grep BEGIN:VCALENDAR app/src` = 0），本轮只影响详情页/日历/通知三处显示；且 `timeCN→中国时间` 的换算方向全仓无代码可证（`OnAirRepository.load` 直接取子串），必须抽查 |
| A-D3 | 7.11 只需补序列化字段 | 行号全对，但**只加字段不够**：`SyncManager.mergeCollections:267-280` 是整行 LWW，A 机改评分/B 机改卷进度必丢一边，必须同时改合并与"缺键≠null" |
| A-D4 | 7.17 `exists` 可做 HEAD 降级 | `WebDavClient.exists:67-71` **全仓 0 调用**，降级路径要新写；且 `_syncResult`（`SettingsViewModel.kt:342-343`）没有任何 UI 收集，412 提示无处显示 |
| A-D5 | 7.13「401/500/超时当成功」 | 精确地说：`download` 把 `get()==null` 当成功（`SyncManager.kt:137-138`）；`upload` 失败**没有**被吞（:117-122），被吞的是 `mkcol` 返回值（:91）与异常类型（`WebDavClient.kt:94-97`）；用户侧表现是"点了没反应"（`isSyncing.value` 不触发重组） |
| A-D6 | 计划位置 `CollectionViewModel.kt:191-199` | 位置对但**不止一处**：`batchUpdateStatus`（:224-243，写入在 :231）同样漏 bump；`setProgress`（:217）与 `SubjectDetailViewModel.updateStatus`（:1311）已正确；根因是 `CollectionRepository.update`（:30-31）不 bump |
| A-D7 | 2.3「设置行 ✓ 当前」读持久化值 | `DataSourceSettingsScreen.kt:134` 读的是 `primaryPluginId` = `pluginManager.primary?.id`（`SettingsViewModel.kt:91`），**不读** DataStore，所以重启后 UI 与行为一起回 Bangumi；**并且本卡与 A-X2 互斥**（X2 要删这两行 picker 与 `activeDataSourceId`，见 A-D7 依赖栏） |

## 🧭 同步层三张卡的顺序：**A-D5 → A-D3 → A-D4**

1. **A-D5 先**：它把 `WebDavClient.get/put` 的返回类型与 `SyncResult` 定型（401/5xx/超时/404/412 在同一套类型里）。D3 要在这个返回类型上判"是登录失败还是远端真没数据"，D4 要从中挑出 412；先做 D3/D4 会被迫改两遍。
2. **A-D3 居中**：D4 的 412 自动重合并会调用 D3 改好的字段级合并；若 D3 未落地，冲突恢复后卷进度仍会被整行 LWW 覆盖，等于 D4 白做。
3. **A-D4 最后**：它同时依赖 D5 的类型体系与 D3 的合并语义，且自身会再动一次 `SyncManager.uploadLocked/downloadLocked`。

> A-D6 建议排在 A-D3 之前（跨卡但同窗口）：D3 的 LWW 可信度建立在 `updateTime` 真实之上，否则 D3 验收时无法区分"合并丢数据"与"时间戳没变"。

| 卡 | 主题 | 预估 | 依赖 | 动 Room | v33 迁移 | 新增 DAO |
| --- | --- | --- | --- | --- | --- | --- |
| A-D1 | 观看量分流（集数/时长/游戏分钟） | M | 无 | 否 | **否** | 可选（只读查询） |
| A-D2 | 放送时区换算 | S | 无 | 否 | **否** | 否 |
| A-D3 | 卷进度参与同步与备份 | M | **A-D5** | 否 | **否** | 否 |
| A-D4 | WebDAV 条件写入（ETag/If-Match） | M | **A-D5 → A-D3** | 否 | **否** | 否 |
| A-D5 | 同步错误分类 | M | 无（是 D3/D4 的前置） | 否 | **否** | 否 |
| A-D6 | 状态变更更新时间戳 | S | 无（建议先于 D3） | 否 | **否** | 否 |
| A-D7 | 源顺序持久化 + 链快照 | S | 无（**需先与 A-X2 裁决**） | 否 | **否** | 否 |

---

## A-D1 观看量分流（集数 / 时长 / 游戏分钟）

- **⚠ 与计划不符**｜① 5.6/5.19 的 `minutesPerEpisode` 回退链写了 `subject.durationSeconds`，但 `SubjectEntity`（`data/local/entity/SubjectEntity.kt:22-60`）**没有该字段**——全仓只有 `SubjectDetailData.kt:61` 与 `EpisodeEntity.kt:33`；② `StatsCalculator.computeStats(items)`（`StatsCalculator.kt:41`）只吃 items，`episodesBySubject` 是 `StatsViewModel` 的第 8 个 combine 分支（:122），进不了计算器；③ 还有第二份同缺陷实现（作品库顶栏）与第三份年份口径；④ 计划未提集数里混着音乐曲目。
- **问题**｜`StatsCalculator.kt:47` `totalWatchedEpisodes = items.sumOf { it.collection.watchedEpisodes ?: 0 }` 对所有类型一视同仁；GAME 的 `watchedEpisodes` 存的是游玩**分钟**（`CollectionCard.kt:49` 注释"game 时长存于 watchedEpisodes" + `SteamLibraryImporter.kt:174` 写入 `playtimeForeverMinutes` + 换算见 `PlaytimeConverter.kt`）→ 玩 3000 分钟的游戏给"集数"加 3000。UI 把它当"已看集数"（`StatsOverviewSection.kt:115`）、"累计 X 集"（`CollectionDashboard.kt:43`）、"观看集数"（`StatsScreen.kt:615,662`）。`durationSeconds` 从未进入统计（全仓 UI 侧只剩 `TrackListSection.kt:137` 拿它显示曲目时长）。
- **改哪里**｜新增 `data/calculator/WatchTotals.kt`（纯函数）；`data/calculator/StatsCalculator.kt:41-120`（47、89、116 行）；`data/model/stats/StatsUiState.kt:24-30`；`viewmodel/StatsViewModel.kt:395-434,448-460`；`ui/stats/StatsOverviewSection.kt:104-116`、`ui/stats/StatsScreen.kt:605-620,655-665`；`viewmodel/CollectionViewModel.kt:141-148` + `ui/library/CollectionDashboard.kt:42-47`。
- **怎么改**｜
  1. 新建纯函数文件：
     ```kotlin
     enum class MinutesSource { EXACT, TYPE_DEFAULT }
     data class IntEstimate(val minutes: Int, val source: MinutesSource)
     fun minutesPerEpisode(subject: SubjectEntity, episodes: List<EpisodeInfo>): IntEstimate
     data class WatchTotals(val videoEpisodes: Int, val minutes: Int, val estimatedSubjects: Int,
                            val gameMinutes: Int, val readUnits: Int, val trackCount: Int)
     fun computeWatchTotals(items: List<CollectionWithSubject>,
                            episodesBySubject: Map<Long, List<EpisodeInfo>>): WatchTotals
     ```
     `minutesPerEpisode` 只有两级：集级真值（`episodes.filter{it.type==0 && it.durationSeconds>0}` 的均值）→ 类型默认（ANIME 24 / REAL 45 / 其他 0 = 不计时长）。**不要**编造 Subject 级回退。
  2. 分桶规则写进 KDoc：ANIME/REAL → `videoEpisodes=watchedEpisodes` 且 `minutes=episodes×minutesPerEpisode`；GAME → `gameMinutes=watchedEpisodes`（不并入集数）；BOOK/MANGA → `readUnits=watchedVolumes ?: watchedEpisodes`（页/卷不是时间，不计分钟）；MUSIC → `trackCount=watchedEpisodes`（`SubjectDetailViewModel.kt:1360-1361` 把已听曲目数写进 watchedEpisodes）。
  3. `StatsUiState` 增 5 个带默认值的字段（`videoEpisodes/minutes/estimatedMinutesSubjects/gameMinutes/readUnits`）；`totalWatchedEpisodes` **保留字段名**但改成"视频集数"语义（预览 `StatsScreen.kt:908` 与 `StatsOverviewSection.kt:115` 不用改类型）。
  4. `computeStats(items, episodesBySubject = emptyMap())`，47 行改为 `totals.videoEpisodes`；89 行 `YearlyStats.totalEpisodes` 改为按年只累加视频集数（游戏分钟不进年表）。
  5. `StatsViewModel.computeBaseStats` 传 episodes，缓存键从 `items.hashCode()`（:453）扩为 `items.hashCode()*31 + episodesBySubject.hashCode()` —— 否则预取完成后时长永远停在第一版。
  6. UI：概览新增"累计集数 / 累计时长（约，N 部估算）/ 游戏 A 小时"三行，并把 :115 的"已看集数"从「分类明细」挪出（它和动画/漫画/游戏的**计数**不同量纲，现在混在同一个 typeRow 里）。
  7. 作品库顶栏最小改：`CollectionViewModel.kt:145` 排除 GAME，`CollectionDashboard.kt:43` 文案改「累计集数」，游戏另起一棵胶囊；**v33 迁移：否**；若要更准的时长可加只读 DAO `EpisodeDao.avgDurationSecondsBySubject()`（不改表，D1 可选不做）。
- **测试**｜新增 `WatchTotalsTest`（纯 JVM）：① 动画 12 集 + 游戏 600 分钟 → `videoEpisodes=12, gameMinutes=600, minutes=288, estimatedSubjects=1`；② episodes 里 `durationSeconds=1440` → `source=EXACT`、`estimatedSubjects=0`；③ BOOK `watchedVolumes=3` → `readUnits=3, minutes=0`；④ MUSIC `watchedEpisodes=9` → `trackCount=9` 且不进集数；⑤ 空库全 0 不除零；⑥ 动画无 `totalEpisodes` 但有 episodes → 仍用真实单集时长。再扩 `StatsCalculator` 断言：1 部 3000 分钟游戏 → `computeStats(...).totalWatchedEpisodes` ≠ 3000。
- **验收**｜① 真机造 1 部游戏 3000 分钟 + 1 部动画 12 集：统计页"累计集数"=12、"游戏"=50 小时、"累计时长"≈4.8 小时且带"约"（改前显示 3012 集）；② 断网重进统计页数字不变；③ 作品库顶栏与统计页一致，不再出现 3012；④ `./gradlew :app:testDebugUnitTest --tests '*WatchTotals*'` 绿。
- **风险/回滚**｜总集数变小（游戏/音乐被移出）→ 用户以为数据丢：更新说明写"游戏时长改到独立胶囊"，时长前缀"约"；回滚只需还原 47 行一句 sumOf 与两处 UI 文案（新字段留着不显示）。缓存键扩 episodes 会让 60 部预取每完成一批就重算一次，若掉帧就只把 totals 单独缓存。**写侧仍有一座坑**：`SubjectDetailViewModel.kt:1306` 在书籍标"看过"时把卷数写进 `watchedEpisodes`，本卡只改读侧（所以 `readUnits` 必须 `watchedVolumes ?: watchedEpisodes` 兜底），写侧另开卡。
- **预估**｜M
- **依赖**｜无。

---

## A-D2 放送时刻的时区换算（airTimeZone 只写不读）

- **⚠ 与计划不符**｜① 4.15 说它的价值一半在 `.ics`（4.8），但 4.8 **未实现**（`grep -r "BEGIN:VCALENDAR|VEVENT" app/src` 无命中），本轮只能修"App 内显示"；② `timeCN` 是否为"日本时间换算后的中国时间"仍未取证：`OnAirRepository.load`（:58-78）对 `timeCN` 直接 `substring(0,2)/(2,4)`，全仓无任何时区换算代码，方向必须抽查；③ 4.15 说"grep airTimeZone 只有 3 处"实际 4 处（多一处 KDoc），不影响结论。
- **问题**｜`OnAirRepository.enrich:44-52` 写 `airTimeZone = subject.airTimeZone ?: "CN"`（列由 `MIGRATION_20_21` 加，`NirikoDatabase.kt:398`；字段 `SubjectEntity.kt:34`），但**全仓零读取**：`airTimeMinutes` 被直接格式化——`SubjectDetailScreen.kt:2271`、`CalendarDaySheet.kt:336`、`AiringNotificationComposer.kt:44-46`（格式化在 :77）、`AiringReminderWorker.kt:77`。非 UTC+8 用户看到的"23:30 放送"其实是中国时间。
- **改哪里**｜新增 `data/model/AirTime.kt`；`ui/subject/SubjectDetailScreen.kt:2267-2274`；`ui/stats/CalendarDaySheet.kt:335-343`；`data/notification/AiringNotificationComposer.kt:5-11,44-47,77`；`data/notification/AiringReminderWorker.kt:73-81`。写入侧 `OnAirRepository.kt:44-52` **不改**。
- **怎么改**｜
  1. 值对象：`data class AirTime(val minutes: Int, val zoneId: String)`，`companion fun of(subject: SubjectEntity): AirTime?`（`airTimeMinutes ?: return null`；`airTimeZone` 映射 `CN→Asia/Shanghai`、`JP→Asia/Tokyo`、`LOCAL`/未知 → 设备默认区）。
  2. `fun toLocal(date: LocalDate): LocalDateTime` = `ZonedDateTime.of(date, LocalTime.of(m/60, m%60), ZoneId.of(zoneId)).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()`；**跨日交给 LocalDateTime**（23:30 CN → 次日 00:30 JST），不要用 `plusHours` 猜。
  3. `fun formatLocal(date)` 返回 `"00:30"`，跨日加后缀 `"（次日）"`；`fun formatSource()` 保留 `"23:30"`。设备区 == 源区时**只显示一个时刻**，不要出现"23:30（23:30）"。
  4. 三处 UI 统一调它；详情页那行本来就是 `airParts` 拼接（`SubjectDetailScreen.kt:2267-2274`），只替换取时刻的那一句。
  5. 通知：`AiringNotificationItem` 增 `airTimeZone: String?`（`AiringReminderWorker.kt:75-81` 传 `subject.airTimeZone`），`compose` 用 `AirTime` 生成文案；**保留** `formatMinutes`（:77）当同区实现的内部函数，避免打断既有 `AiringNotificationComposerTest`。
  6. 日历/放送的**排序键**（`StatsCalculator.computeCalendarEvents` 内按 `airTimeMinutes` 的地方）不换算：排序用源时区才稳定，只有显示层换算。
  7. **v33 迁移：否**（列已在 v21 存在）；无新 DAO、无新表。
- **测试**｜新增 `AirTimeTest`（纯 JVM，zone 作为入参不读设备）：① `AirTime(1410,"CN")` + 2026-10-17 + `Asia/Tokyo` → `2026-10-18 00:30` 且跨日标记为真；② `AirTime(55,"CN")` + Tokyo → 01:55 同日、无跨日；③ 同区（Shanghai）→ 文本等于源文本且不带后缀；④ `airTimeZone=null` → 回退 CN；⑤ `LOCAL` → 用传入 zone。`AiringNotificationComposerTest` 增一例：CN 源 + Tokyo 设备 → 文案 "今日 00:30 放送"。
- **验收**｜① 系统时区切 Asia/Tokyo：详情页放送行显示 `周六 · 00:30（次日）`，日历放送条同值，通知文案同步；切回 Shanghai 显示 `23:30`；② 先抽查 3 部番（例 349039：`weekDayCN 6 / timeCN 0055`）与 Bangumi 网页时刻比对，确认 `timeCN` 是中国时间（若实为 JST，本卡先停并改换算方向，别硬上）；③ `./gradlew :app:testDebugUnitTest --tests '*AirTime*' --tests '*AiringNotificationComposer*'` 绿。
- **风险/回滚**｜换算方向搞反 → 全部时刻偏一小时：先做验收②再合并；回滚 = UI 退回 `formatMinutes`，`AirTime` 只留纯函数（不影响编译与数据）。存量 `airTimeZone` 可能为 null/异常值 → 一律回退 CN（与写入侧一致）。文案变化会让固定断言失效：新测试显式传同区，旧测试不动。
- **预估**｜S
- **依赖**｜无（4.8 `.ics` 未实现，不构成本卡前置；将来 4.8 应直接复用 `AirTime`，并用 `DTSTART;TZID=Asia/Shanghai` 而非换算后的本地时刻）。

---

## A-D3 卷进度参与同步与备份（watchedVolumes）

- **⚠ 与计划不符**｜7.11 的行号（`SyncManager.collectionToJson:386-404`、`parseCollections:430-452`、`BackupManager.collectionToJson:359-377`）**全部对得上**，但计划漏了两件必须一起做的事：① `mergeCollections`（`SyncManager.kt:267-280`）是**整行 LWW**，"只把字段加进 JSON"仍会在两台设备分别改评分/改卷进度时整条覆盖；② 版本号：`SYNC_VERSION = 3`（:31）、`BackupManager.EXPORT_VERSION = 4`（:783），计划没提要不要提版本。
- **问题**｜卷进度只存在于本地 `collections.watchedVolumes`（`CollectionEntity.kt:33`，v21 迁移加入；UI 写入见 `SubjectDetailScreen.kt:2212`、快捷 +1 见 `CollectionViewModel.kt:216`）。WebDAV 与 JSON 备份的收藏编解码都没有这个键：`SyncManager.collectionToJson:386-404`、`SyncManager.parseCollections:430-452`、`BackupManager.collectionToJson:359-377`、`BackupManager.parseCollections:540-560`。换设备/恢复备份后书籍漫画卷进度归零（集数还在）。
- **改哪里**｜`data/sync/SyncManager.kt`：31、267-280、386-404、430-452；`data/backup/BackupManager.kt`：69、359-377、540-560、783；`data/sync/SyncMergePolicy.kt`（新增纯函数）；`app/src/test/java/com/otakup/niriko/data/sync/SyncMergePolicyTest.kt`。
- **怎么改**｜
  1. 两处 `collectionToJson` 各加一行 `c.watchedVolumes?.let { put("watchedVolumes", JsonPrimitive(it)) }`；两处 `parseCollections` 各加 `watchedVolumes = o["watchedVolumes"]?.jsonPrimitive?.content?.toIntOrNull()`。
  2. **缺键 ≠ null**：解析时同时记"键是否存在"（`val hasVolumes = o.containsKey("watchedVolumes")`），合并结果里"远端缺键"必须保留本地值——这是旧客户端回传能把新字段抹空的唯一防线。
  3. `SyncMergePolicy` 增纯函数 `fun maxOfNullable(a: Int?, b: Int?): Int? = when { a == null -> b; b == null -> a; else -> maxOf(a, b) }`（放这里才可单测）。
  4. `mergeCollections` 改两步：先用现有 LWW 选出基准行，再对进度做字段级合并 `watchedEpisodes = maxOfNullable(a, b)`、`watchedVolumes = maxOfNullable(a, b)`（其余字段仍取基准行）。集数用 MAX 与卷进度同口径，避免"一个字段用 LWW、另一个用 MAX"的混乱。
  5. 版本：`SYNC_VERSION` 3→4，`EXPORT_VERSION` 4→5。备份导入对更高版本会**显式拒绝**（`BackupManager.kt:186`），即旧版读到新备份只会报错、不会静默丢数据（可接受）；同步没有版本校验，所以更新说明必须写"两台设备都升到 v1.2.1 再同步"。
  6. 备份设置块里的 `activeDataSourceId`（:458/:733）不动（见 A-X2/A-D7）；本卡只碰收藏块。**v33 迁移：否**；**新增 DAO：否**（`collectionDao.getAll()` 已返回全字段）。
- **测试**｜`SyncMergePolicyTest` 增：`maxOfNullable(null,3)=3`、`(3,null)=3`、`(3,5)=5`、`(null,null)=null`。把编解码抽成 `internal object CollectionCodec { fun toJson(c): JsonObject; fun fromJson(o, hasKey...): CollectionEntity }` 后加 `CollectionJsonRoundTripTest`（纯 JVM，手搓 JSON，不连 Room/OkHttp）：① 卷进度往返一致；② 旧 JSON 无该键 → 解析为 null 且合并后本地 12 卷仍在；③ 远端 12 卷 + 本地 15 卷 → 合并得 15；④ 远端 20 卷（updateTime 更新）+ 本地 15 → 既拿远端的评分又保留 20 卷。
- **验收**｜① A 机书籍填"已读 12 卷"→ 上传；B 机下载 → 卡片/详情显示 12 卷（改前 0）；② A 机改卷 15、B 机改评分 9 → 双向同步后两边都是"15 卷 + 9 分"（只做序列化时会丢一边）；③ 导出 → 清数据 → 导入 → 卷进度一致，且 `version:5` 的新备份可导入、v1.1/v4 旧备份仍可导入；④ 用无 `watchedVolumes` 的旧 JSON 下载 → 本地卷进度不被清零。
- **风险/回滚**｜旧客户端回传抹空（风险最高）：靠第 2 步"缺键不覆盖" + 版本号双保险；若无法保证两端升级，先只改 `BackupManager`（本地迁移闭环），`SyncManager` 两处留到下一版，回滚即撤这两个文件。MAX 对"用户主动把 15 卷改回 12"不友好（下次同步会被拉回 15）：与现有集数 MAX 同口径，本卡接受并写进更新说明；真要修需要逐字段时间戳（7.19 量级，不做）。**范围外但要记录**：`isPrivate` 只做上传过滤（`SyncManager.kt:95`），下载后 `clearAll + insertAll`（:202）会用远端非私密行覆盖本地私密行——本卡不修，另开卡。
- **预估**｜M
- **依赖**｜**A-D5 先**（先定型 `WebDavClient`/失败分类，否则本卡的 401/远端为空无法区分、"卷丢了"归因不了）；**A-D4 在后**（D4 的 412 自动重合并复用本卡的字段级合并）。与 A-X4（U15 只对单本隐藏卷进度输入）**兼容**：X4 只隐藏输入、保留列与值，正好让本卡有数据可同步；X4 必须守住"不删列、不清值"。

---

## A-D4 WebDAV 条件写入（ETag / If-Match + uploadBaseline）

- **⚠ 与计划不符**｜① 7.17 说 `WebDavClient` 有 `mkcol/put/get/delete/exists` 五个方法——对；但 `exists`（:67-71）**全仓 0 调用**，计划里的"HEAD 取 Content-Length + Last-Modified"降级路径没有现成实现，要新写；② 计划假设"设置页上传会静默覆盖"——覆盖属实（`SettingsViewModel.uploadToWebDav:364-379` → `SyncManager.uploadLocked:88-127` 裸 PUT 在 :117），但**UI 根本不显示结果**：`SyncBackupSettingsScreen.kt:219,221,228,230` 直读 `viewModel.isSyncing.value`（非 `collectAsState`，不重组），`syncResult`（`SettingsViewModel.kt:342-343`）无人收集 → 412 检测对了用户也看不到，必须顺带补结果出口；③ `SyncManager` 构造只注入 `database/webDavClient/coverOverrideStore`（`NirikoApplication.kt:625-631`），**没有 DataStore**，基线要新注入一个 store。
- **问题**｜上传是裸 PUT：不读远端、不带 `If-Match`（`WebDavClient.put:35-40`；`SyncManager.uploadLocked:117`），A 机上传会静默覆盖 B 机写入；下载也不留存任何远端版本标识（`WebDavClient.get:43-57` 只回 body，头部全丢）。自动同步虽是 download→upload，但两者之间仍有窗口。
- **改哪里**｜`data/sync/WebDavClient.kt`：35-40、43-57、67-71、74-98（新增 `RemoteMeta(etag,lastModified,length)`）；`data/sync/SyncManager.kt`：88-127、135-262；新增 `data/sync/SyncBaselineStore.kt`（照 `data/settings/CoverOverrideStore.kt` 的独立 `preferencesDataStore` 写法）；`NirikoApplication.kt:625-631`；`viewmodel/SettingsViewModel.kt:364-395`；`ui/settings/pages/SyncBackupSettingsScreen.kt:210-235`。
- **怎么改**｜
  1. `WebDavClient` 增 `data class RemoteMeta(val etag: String?, val lastModified: String?, val length: Long?)`；`get` 返回 **A-D5 定义的那套** `WebDavResult`（`Ok(body, meta) | NotFound | Failure(kind, code, detail)`）；`put` 增参与返回 `If-Match: ifMatch` 并区分 `PreconditionFailed(412)`。`exists` 改成返回 `RemoteMeta?`（HEAD 取头）。
  2. `SyncBaselineStore(context)`：键 `sync_baseline_v1`，值 JSON `{url, etag, lastModified, length, payloadHash, syncTime, deviceId}`；`suspend read()/write()/clear()`；`deviceId` 用随机 UUID 持久化（仅用于诊断文案"另一台设备"）。
  3. 下载成功（含"远端为空"分支）后写基线：`etag/lastModified/length` + 本次 body 的 SHA-256（`payloadHash`）。
  4. 上传前读基线：有 etag → 发 `If-Match`；412 → 在锁内自动执行一次 download→merge→upload（`AsyncSerialQueue` 已串行，:72，不会重入）；成功后提示"检测到并发写入，已自动合并"。
  5. 第二次仍 412 → 返回 `SyncResult(success=false, failure=CONFLICT)`，UI 弹三选一"先合并（推荐）/ 覆盖云端 / 取消"；"覆盖云端"= 忽略 If-Match 再 PUT 一次，**必须显式点击**，不许自动。
  6. 无 ETag 的服务器：退化为 `lastModified + length` 比对，不一致只**提示**不阻断（弱校验）；两者都取不到 → 保持裸 PUT 但在结果文案里标注"该服务器不支持并发检测"。
  7. **v33 迁移：否**；基线在 DataStore 不进 Room；无新 DAO。
- **测试**｜项目**没有** mockwebserver 依赖（`gradle/libs.versions.toml:69` 只有 logging-interceptor），不要为此新增依赖——把决策抽成纯函数 `fun decide(baseline: Baseline?, remote: RemoteMeta?, etagSupported: Boolean): Decision` 并单测：① 无基线 → `Overwrite(noPrecondition)`；② 基线 etag == 远端 → `Proceed(ifMatch=etag)`；③ 不等 → `Conflict`；④ 无 etag 且 lastModified+length 相等 → `Proceed(null)`；⑤ 无 etag 且 lastModified 变了 → `WarnThenProceed`；⑥ 两者都无 → `Proceed(null)`。另测 `SyncBaseline` 的 JSON 编解码往返（抽成纯 object，不碰 Context）与"同一 payload 哈希稳定"。
- **验收**｜① A 机上传成功；B 机此时上传 → 服务器 412 → 自动合并一次，A 的数据不丢，诊断文案出现"已自动合并"；② 真冲突（两台同改同一部评分）→ 第二次 412 → 设置页弹"云端在你上次同步后已更新"三选一，只有点"覆盖云端"才真覆盖；③ 不支持 ETag 的服务器（轻量 WebDAV/坚果云部分接口）不报错，按弱校验提示后上传且文案含"不支持并发检测"；④ 抓包/服务端日志确认 PUT 带 `If-Match`。
- **风险/回滚**｜服务器对 `If-Match` 回 400/501 而不是 412 → 必须把 400/409/412/501 分开处理，否则用户永远传不上去；降级必须可人工触发（设置里"忽略并发检查再上传一次"）。自动重试是**一次性**（`retried` 布尔），写成循环会两端互刷。`get` 返回类型与 A-D5 共用、与 A-D3 的合并共用，三卡必须同窗口提交（中间态编译不过）。回滚：`ifMatch` 恒传 null 即退回裸 PUT，基线与 meta 采集保留（无副作用）。
- **预估**｜M
- **依赖**｜**A-D5 → A-D3 → A-D4**（理由见文首"同步层三张卡的顺序"）。另需 A-D5 一起补的 UI 结果出口，否则 412 的提示无处落地。

---

## A-D5 同步错误分类（401 / 5xx / 超时不再当成功）

- **⚠ 与计划不符**｜① 7.13 说"401/500/超时都返回 null → 当成功（`SyncManager.kt:137-138`）"——**下载侧**确认为真；但**上传侧**没把失败当成功（`put=false → SyncResult(false)`，:117-122），真正被吞的是 `mkcol` 返回值（:91 丢弃结果）和异常类型（`WebDavClient.kt:94-97` catch 一切转 false）；② 计划没写：`SettingsViewModel._syncResult`（:342-343）**没有任何 UI 收集**，`isSyncing` 也在 Composable 里直读 `.value`（`SyncBackupSettingsScreen.kt:219,221,228,230`）→ 用户今天的真实体验是"点了下载，什么都没发生"，连 loading 都不转；③ Bangumi 侧同样把 HTTP 码吞成布尔（`BangumiSyncManager.kt:200-216` `uploadTo` 只回 false），本卡只管主链。
- **问题**｜`WebDavClient.get:43-57` 在 401/500/超时/文件不存在时**一律返回 null**，`SyncManager.downloadLocked:137-138` 把 null 当"远程无同步数据"并 `success=true`；`execute:94-97` 把异常吞成 false、`mkcol:29-31` 的结果在 :91 被丢弃；`SyncResult`（:35-42）只有 `success/message`，没有错误种类，UI 无法分组。
- **改哪里**｜`data/sync/WebDavClient.kt`：43-57、74-98（顺带 28-32、60-71 统一返回类型）；`data/sync/SyncManager.kt`：35-42、88-127、135-262；`viewmodel/SettingsViewModel.kt`：342-395；`ui/settings/pages/SyncBackupSettingsScreen.kt`：215-235；文案联动 `NirikoApplication.kt:330-347`（自动同步把失败抛成 `IllegalStateException(message)`）。
- **怎么改**｜
  1. 新增 `sealed interface WebDavResult<out T> { data class Ok<T>(val value: T) : WebDavResult<T>; data object NotFound : WebDavResult<Nothing>; data class Failure(val kind: SyncFailureKind, val code: Int?, val detail: String?) : WebDavResult<Nothing> }` 与 `enum class SyncFailureKind { AUTH, NOT_FOUND, HTTP_4XX, HTTP_5XX, TIMEOUT, NETWORK, PARSE, UNKNOWN }`（放 `data/sync/SyncFailureKind.kt`，不依赖 Android）。
  2. `WebDavClient` 的 `get/put/mkcol/delete/exists` 全部返回该类型：`fun classify(code: Int): SyncFailureKind?`（200..299 → null；401/403 → AUTH；404 → NOT_FOUND；其他 4xx → HTTP_4XX；5xx → HTTP_5XX；MKCOL 的 405 特例视为成功）。**412 的语义留给 A-D4**：`put` 里在调 `classify` 之前先单独判 412 → `PreconditionFailed`，`classify` 只作兜底（归入 HTTP_4XX），别把"冲突"降级成普通 4xx；`fun classifyException(t: Throwable): SyncFailureKind`（`SocketTimeoutException/InterruptedIOException → TIMEOUT`、`UnknownHostException/IOException → NETWORK`、其他 → UNKNOWN）。
  3. `downloadLocked`：`NotFound → success("远程无同步数据")`；`AUTH → SyncResult(false, "登录已失效（401），请检查用户名与密码")`；`HTTP_5XX → "服务器错误（500），请稍后重试"`；`TIMEOUT → "连接超时（30 秒）"`；`NETWORK → "网络不可达，请检查服务器地址"`。**"远端为空"永远不能变成错误**。
  4. `uploadLocked`：`mkcol` 失败仅当返回非 405 且非 2xx 时才算失败，并把 code 带进文案；PUT 失败按 `classify` 给具体原因，不要再出现笼统"上传失败"。
  5. `SyncResult` 增 `val failure: SyncFailureKind? = null`（带默认值，所有现有调用点免改）。
  6. UI 出口：`SyncBackupSettingsScreen` 用 `collectAsState()` 收集 `isSyncing` 与 `syncResult`；失败走 Snackbar 并按 kind 给人话（不打印 `null`、不打印异常类名），成功也提示"已上传/已下载 N 条"。
  7. **v33 迁移：否**；无新 DAO、无新表。
- **测试**｜`SyncFailureClassificationTest`（纯 JVM）：`classify(401)=AUTH`、`403=AUTH`、`404=NOT_FOUND`、`409/412=HTTP_4XX`、`500/503=HTTP_5XX`、`200/204/207=null`（成功）；`classifyException(SocketTimeoutException) = TIMEOUT`、`classifyException(UnknownHostException) = NETWORK`、`classifyException(IllegalStateException) = UNKNOWN`；MKCOL 的 405 特例在 mkcol 封装层断言为成功。`SyncResultMessageTest`：每种 kind 的中文文案非空、不含 `null`/异常类名/堆栈片段。
- **验收**｜① 密码填错点"下载" → Snackbar"登录已失效（401）…"，**不再**显示"远程无同步数据"，按钮 loading 正确恢复；② 服务器地址写错 → NETWORK/TIMEOUT 文案不同，30 秒内出结果（`WebDavClient.kt:22-24` 的 readTimeout=30s）；③ 服务器返 500（临时反代）→ "服务器错误（500）"，绝不提示成功；④ 远端确实没有 `sync.json` → 仍显示"远程无同步数据"；⑤ 自动同步失败后刷新诊断页能看到同一条分类文案。
- **风险/回滚**｜把非 2xx 一律当失败会让某些实现的 207/301 行为变化 → 成功码必须显式白名单（200..299，MKCOL 加 405）。`get` 返回类型改动大且与 A-D4 共用 → 两卡同窗口提交。回滚：保留 `classify` 但不接 UI、`SyncResult.failure` 默认 null 即恢复旧行为（旧文案路径仍在）。
- **预估**｜M
- **依赖**｜**无前置**；是 **A-D3、A-D4 的前置**。

---

## A-D6 状态变更更新时间戳（updateStatus 不 bump updateTime）

- **⚠ 与计划不符**｜计划只给了 `CollectionViewModel.kt:191-199`，实际**有两处**：单条 `:191-199` 与批量 `:224-243`（写入在 :231）都是 `existing.copy(status = status)`；而 `setProgress`（:209-221，:217 显式取 now）与 `SubjectDetailViewModel.updateStatus`（:1283-1319，:1311 用 now）**已经正确**。根因不是某个调用点忘了写，而是 `CollectionRepository.update`（`CollectionRepository.kt:30-31`）只是 `dao.update`，bump 靠每个调用点手写。
- **问题**｜长按卡片快捷改状态 → `updateStatus`（:191-199）→ `collectionRepository.update(existing.copy(status = status))`，`updateTime` 保持旧值。后果 ①：作品库默认排序是"最近修改"（`AppSettings.kt:126` `defaultSortOrder = UPDATE_TIME`；`CollectionViewModel.kt:121` 按 `updateTime` 降序），改状态后条目**不动**，而改评分会跳到顶部（详情页有 bump），同一份数据两种行为；②：WebDAV 冲突判定是整行 LWW（`SyncManager.mergeCollections:267-280` 比 `updateTime`），旧时间戳的本地状态会被远端更旧的行覆盖——A-D3 的验收也依赖这条时间戳可信。
- **改哪里**｜`viewmodel/CollectionViewModel.kt:191-199`（单条）、`:224-243`（批量）；建议同时给 `data/repository/CollectionRepository.kt:30-31` 加"带 bump 的更新入口"；测试放 `app/src/test/java/com/otakup/niriko/viewmodel/`（同域已有 `CollectionCompletionTest.kt`）。
- **怎么改**｜
  1. 单条：`collectionRepository.update(existing.copy(status = status, updateTime = System.currentTimeMillis()))`。
  2. 批量：循环内每行同样加 now（**不要在循环外取一次**，否则 50 条同毫秒、排序退化），保持 `statusWriteMutex` 与现有 `firstCompletion` 逻辑不变。
  3. 更彻底（推荐，避免下一个调用点再犯）：`CollectionRepository` 增 `suspend fun updateWithNow(collection: CollectionEntity, now: Long = System.currentTimeMillis()) = dao.update(collection.copy(updateTime = now)) > 0`，两个方法改走它；把 `fun bump(entity, now) = entity.copy(updateTime = now)` 放 `CollectionEntity` companion 便于纯单测。
  4. **不要动** `setProgress`（已正确）与 `SubjectDetailViewModel`（已正确），避免顺手改坏 F14 的边沿判定。
  5. 注意远端回写语义不变：`BangumiSyncManager.kt:234` 会用远端 `updatedAt` 写 `updateTime`（既有行为），Bangumi 同步的冲突判定按**状态比对**而非时间戳（`BangumiSyncPlanner`），所以 bump 不会改变 Bangumi 侧结果。
  6. **v33 迁移：否**（`updateTime` 列早已存在）；无新 DAO。
- **测试**｜新增 `CollectionTimestampTest`（纯 JVM）：`bump(CollectionEntity(id=1, updateTime=1000), 5000)` → `updateTime=5000` 且其余字段逐一相等；连续两次 bump（now 递减）→ 若加 `coerceAtLeast` 则序号不倒退，不加则按传入值（二选一并断言）。行为测试（Robolectric + fake repo，照 `CollectionCompletionTest` 写法）：调 `updateStatus(id, COMPLETED)` → 捕获写入实体的 `updateTime > 原值`；`batchUpdateStatus(listOf(a,b), WATCHING)` → 两条都被 bump。
- **验收**｜① 真机作品库按"最近修改"排序，长按 A 改"在看"→ A 立刻到顶部（改前不动）；② A 机改状态 → 上传 → B 机下载 → B 显示 A 的状态（改前被 B 的旧行盖掉）；③ 批量选 3 部改状态 → 3 部都上浮且顺序稳定；④ `./gradlew :app:testDebugUnitTest --tests '*CollectionTimestamp*'` 绿。
- **风险/回滚**｜改状态后条目跳位，可能被当成"列表乱动"——这是与改评分一致的既有语义，更新说明提一句；不要为"看起来不跳"去按 createTime 排序（会让"最近修改"名不副实）。批次内同毫秒时间戳靠稳定排序兜底，不要人为加偏移。回滚：删两处 `updateTime = ...` 即可（仓库新方法可保留不用）。
- **预估**｜S
- **依赖**｜无。**建议排在 A-D3 之前**（LWW 可信度依赖它）。

---

## A-D7 数据源顺序持久化与 DataSourceChain 快照

- **⚠ 与计划不符**｜① 2.3 写 `NirikoApplication.kt:430-436` 传快照——声明处是 :430-436，`plugins = pluginManager.plugins` 在 **:432**，`PluginManager.plugins` 是 `_plugins.toList()`（`PluginManager.kt:18`）确认为快照；`DataSourceChain` 里 `plugins` 被读 **16 处**（:83/114/207/242/284/304/312/324/331/340/348/371/394/411/418/428…），改构造参数必须让它们全走"当前值"；② 2.3 说"设置行 ✓ 当前"读持久化值——实际 `DataSourceSettingsScreen.kt:134` 读 `primaryPluginId`（`SettingsViewModel.kt:91` = `pluginManager.primary?.id`），不读 DataStore，所以重启后 UI 与行为**一起**回 Bangumi（用户只看到"选择丢了"，不会看到"显示 AniList 实际 Bangumi"）；③ **本卡与 A-X2 互斥**：X2 要删掉这两行插件 picker 与 `activeDataSourceId` 字段，而本卡需要"顺序 + 一个写入口"，见依赖栏。
- **问题**｜`PluginManager.moveToTop`（:34-41）只改内存 `_plugins`；`SettingsViewModel.setActiveDataSourceId`（:247-252）写 DataStore 的 `activeDataSourceId`（`SettingsDataStore.kt:452-454`），但**冷启动无人回放**（全仓只有设置页与备份读写该键：`AppSettings.kt:146`、`SettingsDataStore.kt:208,452-454,686`、`BackupManager.kt:458,733`）。同一进程内先建链后切源也不生效（`DataSourceChain` 持构造时快照）。
- **改哪里**｜`plugin/PluginManager.kt:15-18,34-41`（新增 `applyOrder`）；`plugin/DataSourceChain.kt:41-47`（构造参数改 provider，16 处读取自动生效）；`NirikoApplication.kt:402-425,430-436,80-89`；`data/settings/AppSettings.kt:146`、`SettingsDataStore.kt:87,208,452-454,686`；`viewmodel/SettingsViewModel.kt:247-252`；新增测试 `app/src/test/java/com/otakup/niriko/plugin/PluginManagerTest.kt`。
- **怎么改**｜
  1. `PluginManager` 增 `fun applyOrder(ids: List<String>): Boolean`：按 ids 重排已知插件，未知 id 忽略，未列出的按现有相对顺序追加到末尾；已一致返回 false 且不写日志（幂等）。`plugins` 仍返回 `toList()`。
  2. `DataSourceChain` 主构造改 `private val pluginsProvider: () -> List<DataSourcePlugin>`，加 `private val plugins: List<DataSourcePlugin> get() = pluginsProvider()` → **16 处读取零改动**；再留一个 `constructor(plugins: List<DataSourcePlugin>, subjectDao: SubjectDao, ...) : this({ plugins }, ...)` 兼容现有 `HistoricalDataSourceChainTest.kt`（首参是 List）。
  3. `AppSettings` 加 `val sourceOrder: List<String> = emptyList()`；`SettingsDataStore` 加 `SOURCE_ORDER`（逗号分隔），读取挂在 :208 附近，写入与 `ACTIVE_DATA_SOURCE_ID` 同时发生（`setActiveDataSourceId` 改成先 `applyOrder` 再写 `sourceOrder = pluginManager.plugins.map { it.id }`）。
  4. 启动回放：直接复用 `onCreate` 里**已经存在**的 `settingsDataStore.settings.collect`（:80-89），加 `pluginManager.applyOrder(s.sourceOrder.ifEmpty { listOf(s.activeDataSourceId) })` —— 幂等，设置变化时也自动生效，不必新起协程。
  5. 迁移旧值：`sourceOrder` 为空但 `activeDataSourceId != "bangumi"` → 回放时把它移到首位。
  6. 可选：`BackupManager` 设置块（:458/:733）顺带带上 `sourceOrder`（不带也不崩，缺省走 5 的降级）。
  7. **v33 迁移：否**（顺序在 DataStore，不进 Room）；无新 DAO。
- **测试**｜`PluginManagerTest`（Robolectric，因 `android.util.Log`）：① `applyOrder(["anilist","bangumi"])` → `primary.id == "anilist"`；② 未知 id 被忽略且不改变相对顺序；③ 传子集 → 未列出的按原顺序追加；④ 重复调用返回 false 且顺序不变；⑤ `moveToTop` 已在首位返回 false（现有契约）。`HistoricalDataSourceChainTest` 扩一条：用可变 List 建链 → 第一次调用命中 A → 把 B 移到前面 → 第二次调用断言 B 先被调用（验证 provider 生效）；并保留原 `DataSourceChain(plugins = listOf(...))` 构造编译（具名参数兼容）。
- **验收**｜① 设置 → 数据源与账号点 AniList → 显示"✓ 当前"，杀进程重进仍是"✓ 当前"；② 搜一个两源都有的关键词，日志里首个请求发往 AniList（改前重启后回 Bangumi）；③ 同进程先搜一次（已建链）→ 切源 → 再搜，第二次按新顺序（验证快照失效）；④ `./gradlew :app:testDebugUnitTest --tests '*PluginManager*' --tests '*HistoricalDataSourceChain*'` 绿。
- **风险/回滚**｜DataStore 首读异步：首帧发现页可能先发一次 Bangumi 优先后才回放 → 接受"首帧一次旧顺序"，回放后自动修正；若要求零窗口需在 `onCreate` 里 `runBlocking { settings.first() }`（有主线程阻塞风险，不推荐）。`moveUp/moveDown`（:44-57）没有持久化入口，本轮只持久化"当前源 + 顺序"，UI 只暴露"点击置顶"。回滚：主构造参数换回 `List` 并删掉 `onCreate` 里的 `applyOrder` 调用（DataStore 新键无害）。
- **预估**｜S
- **依赖**｜无硬依赖，但**需 Lead 先裁决与 A-X2 的冲突**：
  - 情形 A（保留源切换）：X2 只删"兜底开关"本身、保留这两行 picker 与 `activeDataSourceId` → 本卡按上面全做，X2 的删除范围要收窄。
  - 情形 B（X2 照原样删掉 picker 与字段）：本卡**只做第 1、2 步**（`applyOrder` + 链动态读取，这两步与 UI 无关、是纯 bug 修复），第 3–6 步（持久化与回放）暂停——但那样"用户偏好顺序"就只剩备份恢复一个入口，等于 2.3/2.4 的功能不做。建议至少在数据源页保留一行"默认数据源"下拉再删旧 picker。
  - 无论哪种情形，**第 2 步（链快照）都必须做**：它和其它任何将来改顺序的路径（备份恢复、数据源自检、D7 自身）都会踩到。

---

## 收尾说明

- 本文件为只读产出：未改任何代码，未新增/删除文件（除本 md）。
- 7 张卡全部 **不占 v33**：A-D1 可直接用现有列；A-D2/A-D3 用的 `airTimeZone`/`watchedVolumes` 都是 v21 加的旧列；A-D4/A-D5/A-D7 的数据存 DataStore；A-D6 用 `updateTime` 旧列。
- 三张同步卡（A-D5 → A-D3 → A-D4）请按依赖栏顺序合入，别并行改 `WebDavClient`/`SyncManager`。
- A-D1 的"写侧单位混用"（`SubjectDetailViewModel.kt:1306` 书籍标完成写卷数进 `watchedEpisodes`）与 `isPrivate` 本地优先（`SyncManager.kt:95,202`）是本轮**范围外**发现，建议各开一张后续卡，别塞进这 7 张。
