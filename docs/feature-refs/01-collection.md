# 1 收藏与进度（26 条 v1 + 6 条新增）

> 一句话结论：本域最值得做的是 **1.3 待同步队列**（P0）——现在「离线能写」是真的，但「联网后补交」完全不存在，Bangumi/WebDAV 两条写回链路都要靠人手动点全量同步；其次是 **1.13+1.14+1.24 合并成一张 `collection_readthrough` 表**（P1，一次建表同时解决进度基线、重看次数、重看计划），以及 **1.18 收藏时间戳可见**（P1，字段早已存在、卡片接了 `updateTime` 参数却没渲染出来）。
>
> 本域所有判断基于 `data/model/WatchStatus.kt`、`data/local/entity/CollectionEntity.kt`、`data/sync/`、`data/backup/` 的实测源码；外部参考只写真正 fetch 到并读过符号的文件。

## 1.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 1.1 | 类型动词映射显式化 | 部分 | P1 | S | 本项目 `WatchStatusVerb.verbFor`；bookwyrm 用稳定 identifier 而非本地化标签 |
| 1.2 | 系列级 vs 分季级条目 | 无 | P2 | M | ryot `show_season_number`/`manga_volume_number` 分轴计数 |
| 1.3 | 离线标记、联网自动同步 | 部分 | **P0** | M | mihon `DelayedTrackingStore`（按 key 合并、只增不减、成功即删） |
| 1.4 | 同步状态页 | 无 | P1 | M | mihon `BackupFileValidator` 预检 + 本项目 `RefreshDiagnosticsScreen` |
| 1.5 | 自定义分区纳入同步 | 部分 | P1 | M | 分区名做身份（本项目 `BackupFolderNames`）+ mihon `BackupCategory` |
| 1.6 | 剧集类型分组 | 部分 | P1 | S | `EpisodeEntity.type` 已存在；ryot 三轴字段（ep/vol/season） |
| 1.7 | 剧集按播出星期标注 | 部分 | P2 | S | `EpisodeEntity.airdate` + `AiringStatus.isWeeklyAnime` |
| 1.8 | 「看到」一键跳转 | 部分 | P1 | S | 本项目单集页「看到这里」已有写回链路 |
| 1.9 | 评分前隐藏他人评分 | 无 | P1 | S | bookwyrm `User.show_ratings` 开关范式 |
| 1.10 | 抛弃自动勾选「仅自己可见」 | 无 | P1 | S | bookwyrm `Shelf.privacy` + STOPPED_READING 独立书架 |
| 1.11 | 条目私人备注 | 部分 | P2 | S | `CollectionEntity.remark` 已存在但**无任何 UI 入口** |
| 1.12 | 自定义元数据字段 | 无 | P2 | L | ryot `CollectionExtraInformation` 模板 + `collection_to_entity.information` |
| 1.13 | 进度基线 | 无 | P1 | M | bookwyrm `ReadThrough.start_date` + 多轮记录 |
| 1.14 | 重复观看次数 | 部分 | P1 | M | navidrome `PlayCount`；bookwyrm 一书多 ReadThrough |
| 1.15 | 想看优先级排序 | 无 | P1 | M | ryot `collection_to_entity.rank`（Decimal 秩） |
| 1.16 | 「还剩多少」估算 | 无 | P2 | S | `EpisodeEntity.durationSeconds` 已有；ryot `manual_time_spent` |
| 1.17 | 连续追番记录 | 无 | P2 | S | 本项目 `StatsCalculator.computeTimelineEvents` |
| 1.18 | 收藏时间戳展示 | 部分 | P1 | S | `UniversalSubjectCard` 收了 `updateTime` 却没画 |
| 1.19 | 分类级进度汇总 | 部分 | P2 | S | `ProgressBumpPolicy.barFraction` 可复用 |
| 1.20 | 单集笔记与截图关联 | 无 | P2 | M | 无强参考（图片存储与备份体积，建议不做） |
| 1.21 | 收藏变更历史 | 部分 | P2 | M | bookwyrm `ProgressUpdate` 追加表；navidrome 逐动作时间戳 |
| 1.22 | 评分刻度可自定义 | 部分 | P2 | S | `RatingFeedbackPolicy.STEP=0.5` 硬编码 |
| 1.23 | 观看平台标记 | 无 | P1 | S | ryot `providers_consumed_on` |
| 1.24 | 复习/重刷计划 | 无 | P2 | M | 与 `collection_readthrough`（1.13/1.14）共用表 |
| 1.25 | 弃番原因记录 | 无 | P2 | S | bookwyrm `stopped_date`（也没有原因字段，说明可低优先） |
| 1.26 | 收藏导出为可读清单 | 部分 | P1 | S | 与 7.5 同源，实现合并到 7.5 |
| 1.27 | 状态迁移守卫与副作用 | 无 · 新提出 | P1 | S | ryot `MetadataProgressUpdateChange` 命令枚举 |
| 1.28 | 选集网格区间框选 | 无 · 新提出 | P2 | S | 本项目 1..total FlowRow |
| 1.29 | 进度回退撤销 | 无 · 新提出 | P1 | S | 本项目 `AsyncSerialQueue` 串行化写入 |
| 1.30 | 收藏体检（本地一致性巡检） | 无 · 新提出 | P1 | M | `pruneMembersNotInCollections` 已有单点修复 |
| 1.31 | 可见性三级（公开/仅自己/仅本机） | 无 · 新提出 | P1 | M | ryot `Visibility`；bookwyrm `Shelf.privacy` |
| 1.32 | 观看会话日志 | 无 · 新提出 | P2 | M | bookwyrm `ProgressUpdate`；navidrome `PlayDate` |

## 1.1 逐条详解

### 1.1 类型动词映射显式化

- **现状**｜部分：映射已抽出，`data/model/WatchStatusVerb.kt` 的 `WatchStatus.verbFor(type: SubjectType)` 按 5 状态 × 6 类型硬编码 `when`；调用点三处（`ui/subject/SubjectDetailScreen.kt:2107`、`ui/subject/DetailCardBack.kt:68`、`ui/components/Badges.kt:110`）。但 `WatchStatus.fromLabel()` 仍按**中文 label 反查**，label 一改或加语言就静默失配。
- **提升**｜(a) 用户可见：同一条「在看」在动画详情显示「在看」、漫画详情显示「在读」、音乐显示「在听」，卡片徽章与详情页文案永远同源；(b) 工程上省掉 `fromLabel` 这套反向解析（它是备份/同步解析的隐患点），单测从 0 组变成 30 组穷举。
- **原理**｜把 `when` 换成一张显式表，键是 `(SubjectType, WatchStatus)`，值是一个 `VerbSpec(id: String, zh: String)`；`id` 只用于内部与 CSV 导入（如 `plan/doing/done/hold/drop`），`zh` 只用于显示。`verbFor` 变成查表 + `?: status.label` 兜底。与 Bangumi 数值的映射继续由 `data/sync/bangumi/BangumiStatusMapper` 单向负责（**不能**用 `WatchStatus.ordinal`——该文件注释已明确本地顺序与官方 1=想看 2=看过 3=在看 4=搁置 5=抛弃 不一致）。
- **效果**｜详情页状态分段条（5 段）与收藏卡片右上徽章文字一致；导出/导入走 `id`，即使用户把 label 改成别的语言也不会丢状态；新增类型时只加一行表项。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/shelf.py` [码] `Shelf.TO_READ/READING/READ_FINISHED/STOPPED_READING`（稳定 identifier 与展示名分离）；本项目 `app/src/main/java/com/otakup/niriko/data/model/WatchStatusVerb.kt` [码]。
- **成本/风险**｜S｜风险低；唯一坑是导入旧 JSON 里 `status` 存的是 `WatchStatus.name`（`SyncManager.collectionToJson` 第 389 行），换表后必须兼容 name 与 id 两套键。
- **优先级**｜P1

### 1.2 系列级 vs 分季级条目

- **现状**｜无：`SubjectEntity` 只有 `series: Boolean?`（`data/local/entity/SubjectEntity.kt:47`）与 `volumes`，没有 seriesId/seasonIndex；分季关系只能靠 Bangumi 关联条目（`SubjectRelationCacheEntity`）+ 详情页「关联条目」区块呈现，收藏表里每季是独立 `subjectId`，彼此不知道对方存在。
- **提升**｜(a) 用户可以在某一季详情页看到「本系列第 2/4 季 · 其它季进度」，并一键把整系列标成同一状态；(b) 工程上统计页的「系列完成度」不再需要 N 次关联条目查询。
- **原理**｜在 `subjects` 加 `seriesRootId: Long?` + `seasonIndex: Int?`（迁移 v33，可空，不填即旧行为）。写入时机有二：①详情页加载关联条目时，把 type=系列（Bangumi relation「系列」）的条目批量回填 `seriesRootId = min(subjectId)`；②导入/备份恢复后跑一次 `SeriesRootBackfill`（幂等，类似 `SubjectWriteGateway` 的批量写）。展示层用 `SELECT * FROM subjects WHERE seriesRootId = ? ORDER BY seasonIndex`。
- **效果**｜详情页关联区顶部出现「系列」胶囊行（4 个季封面 + 当前季高亮）；收藏卡片不合并（仍是一季一卡），但长按浮层多一行「设置整系列状态」。
- **参考**｜`IgnisDa/ryot@main:crates/models/media/src/import_export.rs` [码] `ImportOrExportMetadataItemSeen.show_season_number`（季是计数轴而不是条目）；`IgnisDa/ryot@main:crates/models/database/src/collection_to_entity.rs` [码] `metadata_group_id`（分组与条目分离）。
- **成本/风险**｜M｜风险：Bangumi 关联条目里「系列」是中文 relation，跨源（AniList relations）语义不同，回填逻辑必须**只在能确认时**写，否则会把不同作品并成一个系列。
- **优先级**｜P2

### 1.3 离线标记、联网自动同步

- **现状**｜部分：本地写入确实是本地优先（`CollectionViewModel.updateStatus/setProgress` 直接写 Room，无网络依赖），但**全仓没有任何待同步队列**（对 `outbox|PendingOp|pendingQueue|SyncQueue|待同步` 全量 grep 命中 0）。联网后的「补交」只有两条粗粒度路径：`NirikoApplication.runAutoSyncIfEnabled()`（回前台时 WebDAV 下载+上传、Bangumi `syncOnce()`）与设置页手动按钮；两者都是**全量扫描**，且 Bangumi 侧要分 5 类分页拉取、每页间隔 250ms（`BangumiSyncManager.kt:90-112`）。
- **提升**｜(a) 用户可见：飞机上连点 20 集进度，落地开网后自动补交，诊断页出现「待提交 3 项 / 正在提交 / 已提交 20 项」；(b) 工程上省掉一次「为改 1 集而拉 5 类全量收藏」的往返（Bangumi 侧最坏 5×N 页），也省掉现在只能在设置页看、看不出脏数据的盲区。
- **原理**｜新增一张 **cell 级队列**（不是 op log，避免队列无限增长），Room v33：

```sql
CREATE TABLE sync_outbox (
  entityKind TEXT NOT NULL,   -- 'collection' | 'episode_my_rating' | 'folder_member'
  entityKey  TEXT NOT NULL,   -- 'c:12345' | 'e:99001' | 'f:3:12345'
  field      TEXT NOT NULL,   -- 'status' | 'watchedEpisodes' | 'rating' | '*'
  valueJson  TEXT NOT NULL,   -- 目标值（序列化）
  mergeRule  TEXT NOT NULL,   -- 'lww' | 'max' | 'union' | 'set'
  stamp      TEXT NOT NULL,   -- HLC 字符串（见 7.19），排序用
  attempts   INTEGER NOT NULL DEFAULT 0,
  nextAt     INTEGER NOT NULL DEFAULT 0,
  lastError  TEXT,
  state      TEXT NOT NULL DEFAULT 'pending', -- pending|inflight|dead
  PRIMARY KEY (entityKind, entityKey, field)
);
```

写入侧：每个 `CollectionRepository.update` 调用点包一层 `OutboxRecorder`（比较 before/after，只记**变了的字段**）。合并规则三分：进度类 `max`（只增不减，用户显式减要带 `force=true`）、状态/评分/日期 `lww`、`personalTags`/`watchedTrackIds` `union`。消费侧：`NetworkMonitor`（已存在）+ `AppForegroundSignals`（已存在）触发 drain；每次 drain 走 `RefreshCoordinator.refresh(RefreshKeys.SYNC_OUTBOX, RefreshResource.SYNC_OUTBOX)`，于是**退避梯（30/60/120/300s）与诊断页展示自动复用**，不新增状态存储。成功即删行，失败 `attempts++` 并写 `lastError`；`attempts>=8` 转 `dead` 并在诊断页聚合显示。
- **效果**｜设置→刷新诊断多一行「待同步队列」：`待提交 N 项 · 上次 X 分钟前`，失败时显示 `3 项失败：401 登录失效`；单集页「看到这里」在离线时立即变色、联网后角标从「未提交」变回正常；队列为空时该行显示「无待提交变更」（不显示 0 项的空壳）。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/track/store/DelayedTrackingStore.kt` [码]——SharedPreferences 文件 `tracking_queue`，`add(trackId, lastChapterRead)` **只在更大时写入**（天然合并同一条目的多次进度），`remove(trackId)` 在回写成功后删；`actualbudget/actual@master:packages/loot-core/src/server/sync/index.ts` [码] `messages_pending` 的 `ON CONFLICT(dataset,row,column) DO UPDATE ... WHERE excluded.timestamp > messages_pending.timestamp`（cell 级合并 + 只留最新）。
- **成本/风险**｜M｜最大坑：`CollectionViewModel.updateStatus`（`viewmodel/CollectionViewModel.kt:191-199`）用 `existing.copy(status = status)` **没有更新 updateTime**，而 `setProgress`（217 行）更新了——队列一旦按 updateTime 判新旧，长按改的状态会被判成「旧值」。必须先修这个不一致。
- **优先级**｜**P0**

### 1.4 同步状态页

- **现状**｜无：只有资源级诊断（`ui/settings/pages/RefreshDiagnosticsScreen.kt` 渲染全部 `snapshots`，含 `RefreshKeys.AUTO_SYNC_WEBDAV/BANGUMI` 的 `lastSuccessAt/backoffUntil/lastError`），**没有「本地 vs 远端逐条差异」**；WebDAV 同步后 UI 只弹 `SyncResult.message`（`data/sync/SyncManager.kt:120-122`）。
- **提升**｜(a) 用户可见：一页看到「本机 312 条 / 云端 305 条 · 7 条仅本机 · 2 条双方不同 · 其中 1 条将以云端为准」，并能逐条改判；(b) 工程上把 `mergeCollections` 的判定结果从「扔掉」变成「可回放」，冲突不再是黑盒。
- **原理**｜纯函数 `SyncDiffCalculator.diff(local: List<CollectionEntity>, remote: List<RemoteCollection>, now: Long): SyncDiff`，返回 `rows: List<CellDiff>`：`data class CellDiff(subjectId, field, localValue, remoteValue, localStamp, remoteStamp, proposedWinner)`。它**只读**——不写库、不写远端，因此可以在「下载」之前单独跑（`WebDavClient.get` 拿原始 JSON → 解析 → diff）。UI 用 `LazyColumn` + 每行三段 chip（本地 / 自动 / 远端），用户点远端即把该 cell 标成 override，`apply(diff, overrides)` 再走现有事务。远端 JSON 只解析不落库这一步复用 `SyncManager` 里已有的 `parseCollections`（private → 需提取成 `internal object SyncJsonCodec`）。
- **效果**｜设置→同步与备份多一个「查看差异（只读）」入口；diff 页空态显示「两边完全一致（312 条）」；每行显示 `孤独摇滚！ 想看→看过 · 本机 3 天前 / 云端 1 小时前`；底部「应用远端为胜的 2 条」按钮，未选中时禁用。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupFileValidator.kt` [码] `Results(missingSources, missingTrackers)`——**先算清单再问用户**；`IgnisDa/ryot@main:crates/models/database/src/import_report.rs` [码] `progress/was_success/details` 让一次合并有可查询的结果行。
- **成本/风险**｜M｜风险：远端 JSON 解析目前对坏行是静默 `mapNotNull`（`parseCollections` 里 `catch { null }`），diff 页会把「解析失败的 3 条」显示成「云端没有」——需要把解析失败计数一并带出。
- **优先级**｜P1

### 1.5 自定义分区纳入同步

- **现状**｜部分：`library_folder` + `library_folder_subject`（`data/local/entity/LibraryFolderEntity.kt`）随 **JSON 备份**走（`BackupManager` 导出 `libraryFolders[].members`，身份用分区名，见 `BackupFolderNames.normalizedFolderName`），但**明确不随 WebDAV 同步**：`SyncManager` 的 sync JSON 无该字段，设置页还写了一段免责文案（`SyncBackupSettingsScreen.kt:237-243`）。副作用是 `SyncManager.kt:228-236` 那段 `foldersWithOrphanMembers` → `pruneMembersNotInCollections` 的「幽灵成员」补救。
- **提升**｜(a) 用户可见：换设备/重装后分区自动出现，不用先导备份；双设备各加一个分区会合并而不是互相覆盖；(b) 工程上可以删掉 `orphanFolders` 那段特判（清空重写 collections 时不会再有指向不存在收藏的成员）。
- **原理**｜sync JSON 加 `folders: [{name, sortOrder, isCollapsed, createdAt, members: [subjectId]}]`，**身份仍是规范化分区名**（`BackupFolderNames.normalizedFolderName`：trim + lowercase），不用自增 id——这是项目已在备份里验证过的决策。合并按名匹配，成员做**并集**（不是 LWW：两边各自加的作品都不该丢），`sortOrder` 冲突按「本机优先」；空名丢弃。序列化必须与 `BackupManager` 用同一段代码（抽 `FolderJson`），否则两个格式会漂。
- **效果**｜同步后分区条数量 = 两边并集；分区详情里出现「1 部来自另一台设备」的成员；设置页那段「分区不随同步」的免责文案可以删掉。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/CategoriesBackupCreator.kt` + `.../models/BackupCategory.kt` [码]（分类按名字作为独立可导出单元）；本项目 `data/backup/BackupFolderNames.kt` [码]。
- **成本/风险**｜M｜风险：成员并集会让「我在 A 机移除的作品」同步后回魂。需要一张 `folder_member_removed` 墓碑（`(folderName, subjectId, removedAt)`）才能表达删除，否则只能接受「只增不删」。
- **优先级**｜P1

### 1.6 剧集类型分组

- **现状**｜部分：`EpisodeEntity.type` 已有完整语义（`0=本篇，1=SP，2=OP，3=ED`，`data/local/entity/EpisodeEntity.kt:41-43`，音乐曲目同用），统计侧也已排除非本篇（`ui/subject/EpisodeRatingSections.kt:269` 显示「另有 N 个特别篇 / OP / ED（不参与评分曲线）」）。但详情页「选集」网格是 `for (ep in 1..total)` 的纯数字方块（`SubjectDetailScreen.kt:2518`），**不分组、不显示 SP/OP/ED**，长按进单集页也只能在本篇里遍历。
- **提升**｜(a) 用户可见：选集区变成「本篇 12 / 特别篇 3 / OP·ED 4」三个可折叠段，SP 用独立角标；(b) 工程上省掉统计页那句「另有 N 个…」的临时判断（分组本身就是数据源）。
- **原理**｜把 `ProgressInputSection` 的数字循环换成 `state.episodes.groupBy { it.type }`，渲染顺序固定 `[0,1,2,3]`，每段标题用新的纯函数对象 `EpisodeTypeLabels`（可单测）。点击 SP 时写的是 `watchedEpisodes`（不跨段累加），进度显示改为「本篇 6/12 · SP 1/3」。上一集/下一集（`EpisodeDetailScreen` 的 SkipPrevious/SkipNext）按同 type 的 `sort` 排序取邻接，而不是全局 sort。
- **效果**｜选集区出现分段标题，未播出的本篇 chip 灰化；SP 段落为空时整段隐藏；单集页左右箭头不再从第 12 集跳到 SP1；统计页只剩分组数字、没有「另有」这句。
- **参考**｜`IgnisDa/ryot@main:crates/models/media/src/import_export.rs` [码] `show_episode_number / manga_volume_number / show_season_number` 三轴并存（集不是唯一计数单位）；本项目 `app/src/main/java/com/otakup/niriko/data/local/entity/EpisodeEntity.kt:41-43` [码]。
- **成本/风险**｜S｜风险：`episodes` 表对 MOVIE/OVA 可能没有 type 数据（全 0），分组标题要能在「全部 type=0」时退化成现在的单一网格。
- **优先级**｜P1

### 1.7 剧集按播出星期标注

- **现状**｜部分：`EpisodeEntity` 已有 `airdate`（ISO 字符串）与 `status`（`Air/Today/Tomorrow/NA`），`util/AiringStatus.isWeeklyAnime` 也能判 TV/WEB；但选集 chip 只显示 `"$ep $title"`，不显示「每周三」。日历页有周播信息，剧集列表没有。
- **提升**｜(a) 用户可见：选集网格里每集下有 `周三` 小字（或只在「未播出集」上显示）；(b) 工程上日历/详情/提醒三处共用同一个星期推导，省掉各自再从 `subject.airDate` 估。
- **原理**｜纯函数 `AiringSlotResolver.weekdayOf(subject, episodes)`：优先用 `episodes.first { it.airdate != null }` 的 `LocalDate.dayOfWeek`（每集日期互相校验，取众数），退化到 `subject.airDate` + `isWeeklyAnime`。星期文本走 i18n 资源；时间点用 Bangumi 日历响应里的 `air_weekday`，缓存到 `subjects` 新列 `airWeekday: Int?`。
- **效果**｜未播出集 chip 第二行显示「周三」；未来 7 天内的集显示「明天/今天」；非周播（剧场版）不显示该行，布局不变。
- **参考**｜本项目 `app/src/main/java/com/otakup/niriko/util/AiringStatus.kt` [码] `isWeeklyAnime/isReleaseDateType`；`data/remote/bangumi/dto/CalendarResponseDto.kt` [码]（周播数据来源）。星期展示本身是 UI 决策，外部无更优实现可引，**该条为 [推断]**。
- **成本/风险**｜S｜风险：跨季/跨年长番的 airdate 众数可能因停播周漂移，只用于展示不参与判定即可。
- **优先级**｜P2

### 1.8 「看到」一键跳转

- **现状**｜部分：**单集页内**已有完整闭环（`ui/episode/EpisodeDetailScreen.kt:345`「看到这里」写回 `watchedEpisodes`、上一集/下一集不压栈），但**入口只有长按选集 chip**（`SubjectDetailScreen.kt:2529`）。收藏列表卡片、详情页顶部都没有「继续看第 N 集」。
- **提升**｜(a) 用户可见：详情页剧集区顶部出现一行「继续 · 第 7 集（已看 6/12）」，收藏卡长按浮层多一项「继续看」；(b) 工程上省掉用户每次「进详情 → 找第 7 格 → 长按」的三步。
- **原理**｜纯函数 `ContinuePoint.of(collection, episodes, type)`：`next = (watchedEpisodes ?: 0) + 1`，夹到 `totalEpisodes`；过滤 `episodes.filter { it.type == 0 }` 后按 `sort` 找 `ep >= next` 的第一条取 `epId`；找不到（剧集未拉取）则回退成「打开选集」。UI 是一个 `FilledTonalButton`，点击直接 `navController.navigate(EpisodeDetail(epId))`；无剧集数据时整行不渲染（不留死按钮）。
- **效果**｜「看过 6/12」的番详情页顶部有「继续看第 7 集」；已看完显示「重看第 1 集」；未收藏显示「开始记录第 1 集」并跳转收藏弹层；连载未播到第 7 集时显示「第 7 集未播出 · 8 月 12 日」并禁用。
- **参考**｜本项目 `ui/episode/EpisodeDetailScreen.kt` [码]（写回链路已有，只缺入口）；`util/ProgressBumpPolicy.kt` [码]（书籍/漫画要走卷而不是集，入口文案需按单位切换）。
- **成本/风险**｜S｜风险：游戏（进度单位=分钟）、音乐（逐首）不能套「下一集」，必须按 `ProgressUnit` 分支隐藏。
- **优先级**｜P1

### 1.9 评分前隐藏他人评分

- **现状**｜无：详情页无条件展示社区分（`SubjectMetaSection` 里 `subject.ratingScore`，`SubjectDetailScreen.kt:2237`）与评分分布（`RatingDistribution`），`myRating` 为空时也不遮。
- **提升**｜(a) 用户可见：自己没打分时社区分显示为「评分已隐藏 · 你打完后展开」，点「仍然查看」可临时解开；(b) 工程上不需要新表，只是渲染条件。
- **原理**｜设置项 `hideCommunityRatingUntilRated: Boolean`（AppSettings + DataStore key），详情页 `val reveal = myRating != null || overridden`。遮罩不是模糊图片而是**同布局占位**（避免跳动）：`AnimatedContent` 在 hidden/shown 间切换，hidden 态显示「—」+ 一行说明 + 文字按钮。分布直方图同理，未知时不画柱子但保留 x 轴刻度。评分一旦写入 Room（`saveRecord` 成功）就自动 reveal。
- **效果**｜未打分条目：社区分位置是「—」与小字「评分已隐藏，避免先入为主」，点「查看」后数字淡入；已打分的条目无变化；设置项默认关闭（不打扰老用户）。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/migrations/0209_user_show_ratings.py` [码] `user.show_ratings` 布尔（评分可见性被当作一等设置）；「未打分前遮罩」的交互无源码证据，**标 [推断]**。
- **成本/风险**｜S｜风险：详情页评分区块同时承担「对比外部源」功能（`RatingComparisonSection`），遮罩必须只遮 Bangumi 社区分，不遮 TMDb/IMDb 等外部分，否则用户会以为数据没拉到。
- **优先级**｜P1
### 1.10 抛弃自动勾选「仅自己可见」

- **现状**｜无：`onUpdateStatus(DROPPED)` 只写 `status`（`SubjectDetailScreen.kt:2101`），`isPrivate` 是独立开关（2162 行），默认 false，没有任何联动。
- **提升**｜(a) 用户可见：点「抛弃」时私密开关自动打开并给出可撤销提示；(b) 工程上省掉用户「抛弃 → 再滚到下面打开私密 → 保存」的三步。
- **原理**｜把状态写入与私密默认值合成一条**意图**：`StatusChangeIntent(status, autoPrivate = status == DROPPED)`，在 ViewModel 层（不是 UI 层）计算 `targetPrivate = if (intent.autoPrivate) true else existing.isPrivate`。提示用一次 Snackbar「已设为私密，仅本机可见 · 撤销」，撤销把 `isPrivate` 写回 false 并 bump `updateTime`。批量改状态（`batchUpdateStatus`）同样套用但要**跳过已私密的条目**（不覆盖用户原选择）。
- **效果**｜选「抛弃」瞬间私密开关变开、当前卡片右上出现锁图标；Snackbar 5 秒内可撤销；从「抛弃」改回「在看」不会自动关私密（需用户自己关，避免猜测）。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/shelf.py` [码] `Shelf.STOPPED_READING` 是独立书架且带自己的 `privacy` 字段——「停读」与可见性天然同属一次表态。
- **成本/风险**｜S｜风险：`isPrivate` 目前**不随 WebDAV/备份走**（见 7.11），自动置私密会让那条记录从别的设备「消失」，文案必须写清「仅本机」而不是「仅自己」。
- **优先级**｜P1

### 1.11 条目私人备注

- **现状**｜部分：`CollectionEntity.remark` 列存在且被备份/同步序列化（`BackupManager.kt:371`、`SyncManager.kt:398`），但**全 UI 目录 grep `remark` 命中 0 行**——没有任何入口能写它；`saveRecord` 只是把 `s.remark` 原样回写（`SubjectDetailViewModel.kt:1382`）。真正能写的是 `personalImpression`（「个人感想」，会随 Bangumi 同步作为 comment 上传）。
- **提升**｜(a) 用户可见：多一个「私人备注」多行框，内容只在本机/备份里；(b) 工程上把已存在但闲置的列用起来，**零新增字段**。
- **原理**｜在编辑弹层「个人感想」下方加「私人备注（仅本机可见，不随 Bangumi 同步）」输入框，写 `remark`；`saveRecord` 增加 `remark: String?` 参数（当前签名没有）。同步侧天然安全：`BangumiSyncManager.uploadTo` 只发 `UpdateCollectionRequest(type=...)`，不带 comment，而 WebDAV 会带 remark——所以文案应写「随 WebDAV 备份，不上传 Bangumi」。
- **效果**｜详情页编辑弹层底部多一个折叠的「私人备注」区（默认收起，有内容时显示预览一行）；列表不展示；备份 JSON 里能看到 `remark`。
- **参考**｜本项目 `data/local/entity/CollectionEntity.kt` + `data/backup/BackupManager.kt:371` [码]（字段已就绪）；`IgnisDa/ryot@main:crates/models/media/src/import_export.rs` [码] `ImportOrExportItemReview.text` 与 `visibility` 分开建模。
- **成本/风险**｜S｜风险：与 `personalImpression` 语义重叠，必须在 UI 上并排给出区分说明，否则用户会把两边都写一遍。
- **优先级**｜P2

### 1.12 自定义元数据字段

- **现状**｜无：`collections` 只有固定列（`personalTags`/`personalImpression`/`remark`），`data/filter/FilterDimension.kt` 也只覆盖内置维度，无法「给任意条目加键值对并进入筛选」。
- **提升**｜(a) 用户可见：可以定义「字幕组」「放映厅」「购买价格」这类字段，详情页出现对应输入，作品库可按字段筛选；(b) 工程上把「一个用户想要的新维度」从发版变成配置。
- **原理**｜三张表（v33）：`field_def(id, key, label, kind[TEXT|NUMBER|DATE|ENUM], optionsJson, sortOrder, createdAt)`；`field_value(subjectId, fieldId, textValue, numValue, dateValue, PRIMARY KEY(subjectId, fieldId))`。筛选直接复用 `CollectionFilter` 加一个 `customFilters: Map<Long, String>`，查询用 `EXISTS (SELECT 1 FROM field_value ...)`。为避免 N+1，给 `CollectionWithSubject` 加 `@Relation` 的 `fieldValues`。
- **效果**｜设置→作品库多「自定义字段管理」页（新增/排序/删除，删除时二次确认「将清除 N 条记录的值」）；详情页在个人记录区尾部出现字段输入；作品库筛选面板多一列「自定义」chips，值为空的条目不参与该筛选。
- **参考**｜`IgnisDa/ryot@main:crates/models/database/src/collection.rs` [码] `information_template: Option<Vec<CollectionExtraInformation>>`（列表自带字段模板）；`IgnisDa/ryot@main:crates/models/database/src/collection_to_entity.rs` [码] `information: Option<serde_json::Value>`（每个成员一条自由 JSON，而不是全表统一 schema）。
- **成本/风险**｜L｜风险：新表要进备份/同步/删除级联三处（FK CASCADE 必须配 `field_value → subjects`），且**默认不进 WebDAV** 会重演 1.5 的「换设备就丢」。
- **优先级**｜P2

### 1.13 进度基线

- **现状**｜无：`CollectionEntity.watchedEpisodes` 只存「已看到第几」，没有「从第几集开始看」。补番从第 30 集起看时，统计会把 1–29 集算作「一次看完」，`StatsCalculator` 的月度/年度口径全部偏大。
- **提升**｜(a) 用户可见：收藏编辑里可填「从第 N 集开始」，卡片进度条仍显示 30/50，但统计与「还剩多少」按 21 集算；(b) 工程上让统计口径有据可依，省掉「补番条目在统计里永远虚高」这个无法解释的偏差。
- **原理**｜优先复用**一张读薄表**（与 1.14、1.24 合并）：

```kotlin
@Entity(tableName = "collection_readthrough",
  primaryKeys = ["subjectId", "round"],           // round = 1,2,3…（重看轮次）
  indices = [Index("subjectId")])
data class ReadthroughEntity(
  val subjectId: Long, val round: Int = 1,
  val startEpisode: Int? = null,   // 基线：本轮从第几集/卷/分钟开始
  val progress: Int? = null,       // 本轮进度（单位随类型）
  val startDate: LocalDate? = null, val finishDate: LocalDate? = null,
  val droppedAt: LocalDate? = null,  // 本轮弃了
  val isActive: Boolean = true,
  val note: String? = null, val updatedAt: Long = 0L,
)
```

`collections.watchedEpisodes` 继续作为「当前轮进度」的**派生缓存**（迁移时回填 round=1），统计取 `progress - (startEpisode-1)`，`coerceAtLeast(0)` 兜底。
- **效果**｜收藏编辑多一行「从第 __ 集开始（统计按此计算）」，留空表示从第 1 集；补番条目的「已看」在统计页从 50 变成 21；卡片进度条仍显示真实位置 30/50（不变）。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/readthrough.py` [码] `ReadThrough(start_date, finish_date, stopped_date, is_active, progress, progress_mode)` + `ProgressUpdate` 追加表；`IgnisDa/ryot@main:crates/utils/dependent/progress/src/lib.rs` [码] `MetadataProgressUpdateStartedOrFinishedOnDateInput`（日期是独立命令参数，不是状态附属）。
- **成本/风险**｜M｜风险：现有 23 张表 + v32 迁移链，新增表必须写 v32→v33 显式迁移，并把「当前轮进度」的三个双写点（详情页 `saveRecord`、长按 `setProgress`、Bangumi 同步落库）全覆盖，否则派生缓存会飘。
- **优先级**｜P1

### 1.14 重复观看次数

- **现状**｜部分：只有**单集级**布尔 `EpisodeMyRatingEntity.rewatch`（`data/local/entity/EpisodeMyRatingEntity.kt:28`，单集页「二刷」开关），没有「整部看了 3 遍」的计数，也没有每轮的起止日期。
- **提升**｜(a) 用户可见：详情页显示「已看 3 遍 · 最近 2026-08 重看」，年度总结出现「重看最多的 5 部」；(b) 工程上重看数据与长评（1.21）共用一张表，不再新增日志表。
- **原理**｜直接用 1.13 的 `collection_readthrough`：`round` 递增即重看次数，`COUNT(*) GROUP BY subjectId` 得次数，`MAX(startDate)` 得最近重看。发起重看 = 插一行 `round = max+1, isActive = true` 并把上一轮 `isActive = false`（与 bookwyrm「active readthrough 必须有未设的 finish_date」一致）。单集级 `rewatch` 保留（粒度更细），不合并。
- **效果**｜详情页个人记录标题显示「第 2 遍」，下有「开始新一轮重看」按钮；重看进行中时卡片进度条按本轮算（0/12），收藏列表徽章显示「2 遍」；统计页时间线出现「重看了 X」事件。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/readthrough.py` [码]（同一本书可有多条 ReadThrough，无唯一约束）；`navidrome/navidrome@master:model/annotation.go` [码] `PlayCount int64 / PlayDate *time.Time`（计数与最近时间成对）。
- **成本/风险**｜M｜风险：`watchedEpisodes` 与「本轮 progress」谁是真相必须一次定死，否则重看会污染统计；建议 `collections.watchedEpisodes` 只在 round 1 维护，round≥2 一律读写 readthrough。
- **优先级**｜P1

### 1.15 想看优先级排序

- **现状**｜无：`SortOrder` 只有 5 档（`UPDATE_TIME/CREATE_TIME/MY_RATING/TITLE/WATCH_PROGRESS`，`data/model/collection/SortOrder.kt`），全部是**计算排序**，没有用户手动顺序；分区内顺序（`LibraryFolderSubjectEntity.sortOrder`）存在但只在分区视图生效且无拖拽 UI。
- **提升**｜(a) 用户可见：「想看」列表可长按拖拽排序，重启后保持，随备份/同步走；(b) 工程上省掉「想看 200 条只能按时间倒序，新的永远压在最上」的挫败。
- **原理**｜分数秩（fractional indexing）：`collections.manualRank: Double?`（v33 新列，null = 未手动排）。拖到 a、b 之间 → `newRank = (rank(a) + rank(b)) / 2`；落在头部 → `rank(a) - 1.0`，尾部 → `+1.0`。当相邻秩差 `< 1e-6` 时触发一次 `RebalanceRanks`（按当前顺序重写成 1024, 2048, …，单事务 UPDATE）。`SortOrder` 增 `MANUAL("手动排序")`，排序实现为「`manualRank` 非空优先、再按 updateTime」。
- **效果**｜排序菜单出现「手动排序」；进入后卡片右上出现拖拽把手（长按 200ms 抬起），松手有 0.2s 落位动画；首次进入提示「拖动可调整顺序」；新增条目默认 `manualRank = null` 排在手动项之后。
- **参考**｜`IgnisDa/ryot@main:crates/models/database/src/collection_to_entity.rs` [码] `rank: Decimal`（成员秩）；`bookwyrm-social/bookwyrm@main:bookwyrm/models/shelf.py` [码] `ShelfBook.shelved_date` + `OrderedCollectionMixin`（有序集合而非按时间隐式排序）。
- **成本/风险**｜M｜风险：与 `LibraryFolderSubjectEntity.sortOrder` 两套顺序会打架——需先决定「全局手动顺序」还是「分区内手排」，建议只做全局。
- **优先级**｜P1

### 1.16 「还剩多少」估算

- **现状**｜无：卡片只显示 `已看/总数`，没有时间维度的剩余量；`EpisodeEntity.durationSeconds` 已经存了每集时长（`data/local/entity/EpisodeEntity.kt:39`），但只被评分/统计曲线用。
- **提升**｜(a) 用户可见：详情页显示「还剩 6 集 · 约 2 小时 18 分」，作品库排序可按剩余时长；(b) 工程上把已抓到的 duration 用起来，零新请求。
- **原理**｜纯函数 `RemainingEstimator.estimate(collection, episodes, unit)`：`remaining = episodes.filter { it.type == 0 && it.ep > watched }.sumOf { it.durationSeconds }`；若缺时长，退化为 `(total - watched) * medianDuration`（中位数抗单集异常），再退化 `(total-watched) * 24min` 并标 `estimated=true`。输出 `Remaining(episodes, minutes, estimated)`，UI 按 `estimated` 决定是否显示「约」。
- **效果**｜详情页进度区下方一行「还剩 6 集 · 约 2 小时 18 分（其中 2 集时长未知）」；已看完/未开播不显示；计算放在 `Dispatchers.Default`（与 `CollectionViewModel` 现有 `flowOn` 一致）。
- **参考**｜`IgnisDa/ryot@main:crates/models/media/src/import_export.rs` [码] `manual_time_spent`（时长与集数并列存储）；本项目 `data/local/entity/EpisodeEntity.kt:39` [码] `durationSeconds`。
- **成本/风险**｜S｜风险：`durationSeconds` 对部分源为 0（VNDB/Bangumi 老数据），中位数兜底必须显式标「估算」，否则用户会当成精确值。
- **优先级**｜P2

### 1.17 连续追番记录

- **现状**｜无：`StatsCalculator.computeTimelineEvents` 只从 `createTime/startDate/finishDate` 造事件（`data/calculator/StatsCalculator.kt:405-451`），没有「连续 N 天」聚合，也没有打卡概念。
- **提升**｜(a) 用户可见：统计页出现「连续追番 12 天 · 最长 31 天」，年度总结里有一块热力条；(b) 工程上完全基于已有字段，不新增表、不新增抓取。
- **原理**｜把事件日期投影到 `Set<LocalDate>`，再算最长连续段：排序去重后 `days[i+1] == days[i].plusDays(1)` 计数。日期来源三处加权：`episode_my_ratings.ratedAt`（最准，用户打分那天）、`collections.updateTime`（改进度那天）、`finishDate`。当前连击 = 从今天/昨天往前数（允许 1 天容忍）。纯函数 `StreakCalculator.current/longest(days, today)` + 单测（跨月、闰年、时区）。
- **效果**｜统计页顶部卡片「连续 12 天」+ 一条 30 格热力条；断签后显示「今天看一集就能续上」；没有任何日期数据时整块不渲染。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/readthrough.py` [码]（`save()` 里 `self.user.update_active_date()`，说明「活跃日」是被单独记录的跨实体概念）；`navidrome/navidrome@master:model/annotation.go` [码] `PlayDate`。
- **成本/风险**｜S｜风险：**数据基础薄**——只有用户手动打分/改进度才有日期，纯「点开就看完」不会留下日期，连击会低报。必须在 UI 标注口径（「按你留下记录的日子算」），否则用户会认为算错。
- **优先级**｜P2

### 1.18 收藏时间戳展示

- **现状**｜部分：`CollectionEntity.createTime/updateTime` 存在，`CollectionCard` 也把 `updateTime` 透传给了 `UniversalSubjectCard`（`ui/library/CollectionCard.kt:63`），但该 Composable 收了参数**从未渲染**（`ui/subject/UniversalSubjectCard.kt` 里 `updateTime` 只有参数声明与 KDoc，无使用点）。
- **提升**｜(a) 用户可见：卡片角标「3 天前更新」，详情页「加入于 2025-11-02 · 最近修改 3 天前」；(b) 工程上零新增字段、零新增查询，只是把已传进来的值画出来。
- **原理**｜纯函数 `RelativeTime.format(at, now)` 复用 `RefreshStatusLabels.formatAgo` 的口径（刚刚/分钟/小时/天），但**不直接引用**（那个在 data/refresh 里带刷新语义），应抽到 `util/RelativeTime.kt` 供两处共用。卡片只在 `status == WATCHING` 或 `updateTime` 距今 < 7 天时显示，避免每张卡都堆字。
- **效果**｜连更中的番卡片右下角有「2 天前」小字；7 天以上不显示；详情页日期行变成「加入于 … · 最近修改 … · 开始 … · 完成 …」四段，分隔符统一「·」。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/shelf.py` [码] `ShelfBook.shelved_date`（「何时上架」是一等展示字段）；本项目 `ui/subject/UniversalSubjectCard.kt` [码]（参数已就位）。
- **成本/风险**｜S｜风险：`updateTime` 目前**不是每次写入都更新**（`CollectionViewModel.updateStatus` 就没更新，见 1.3 风险），展示出来会让「刚刚改的状态」显示成「3 天前」——必须先修 1.3 的同源问题。
- **优先级**｜P1

### 1.19 分类级进度汇总

- **现状**｜部分：`LibraryFolderSummary` 有 `memberCount`（`CollectionViewModel` 的 `folders` 流），分区导航条只显示作品数；没有「这个分区整体看完多少」。
- **提升**｜(a) 用户可见：分区 chip 变成「待补 · 12 部 · 38%」，一眼知道这个分区还剩多少；(b) 工程上复用 `ProgressBumpPolicy.barFraction`，不需要新查询（列表本来已经全量在内存里）。
- **原理**｜在 `CollectionViewModel` 计算 `CollectionListUiState` 的同一次 `map` 里，按 `folderId` 分组，对 `barFraction(type, watchedEp, watchedVol, totalEp, totalVol)` 取**集数加权平均**（`sum(已看)/sum(总)`，不是各条百分比的平均，后者会被短番污染）；返回 null（分母未知）的条目从分母剔除并计数。分区未选中时也对全部条目算一遍，作为 `FolderNavBar` 的副标题。
- **效果**｜分区导航 chip 两行：「待补」+「12 部 · 38%」，选中态加一条 3dp 进度线；全部完成显示「已看完」；某分区全部无总集数时只显示「12 部」。
- **参考**｜本项目 `util/ProgressBumpPolicy.kt` [码] `barFraction/unitOf`（B11 已修「卷/话」分母错配）；`IgnisDa/ryot@main:crates/models/database/src/collection_to_entity.rs` [码] `rank/collection_id`（成员与集合的关系表，适合承载聚合）。
- **成本/风险**｜S｜风险：游戏（分钟）与音乐（逐首）不能进「百分比」平均，必须按 `ProgressUnit` 过滤掉 UNSUPPORTED，否则分母会出现 0。
- **优先级**｜P2
### 1.20 单集笔记与截图关联

- **现状**｜无：`EpisodeMyRatingEntity` 只有 `score/comment/rewatch/ratedAt`，没有图片字段，也没有图片存储目录约定。
- **提升**｜(a) 用户可见：单集页可贴一张截图作为「名场面」，详情页时间线上以缩略图展示；(b) 工程上没有正收益——这会引入新的二进制资产管线与备份体积问题。
- **原理**｜若要做：图片存 app 私有目录 `filesDir/episode_shots/{epId}.webp`（与 `data/wallpaper/WallpaperLibraryFiles` 同款「文件 + 路径表」模式），表只存相对路径；备份时**默认不打包图片**，仅在设置里勾「包含截图」时以 zip 附加。缩略图用 `ImageDecoder` 采样到 320px。
- **效果**｜单集页「我的评价」旁出现「添加截图」；有截图时作品详情的时间线/剧集卡出现小图；备份弹窗明示「截图不包含在默认备份中」。
- **参考**｜无强参考：bookwyrm 与 ryot 都不带用户图片附件。本条**标 [推断]**，建议维持 v1 结论（默认不做）。
- **成本/风险**｜M｜风险：备份体积、SAF 权限、跨设备丢失三者叠加，收益远低于成本。
- **优先级**｜P2

### 1.21 收藏变更历史

- **现状**｜部分：时间线是**推导**出来的，不是记录（`StatsCalculator.computeTimelineEvents` 用 `createTime` + `startDate` + `finishDate` 造 3 类事件），所以「改了评分」「从在看改成搁置」这类事件根本不存在；`collections` 也没有任何 audit 列。
- **提升**｜(a) 用户可见：条目详情底部出现「2026-08-02 从『在看』改为『搁置』；2026-07-11 评分 7→8」，年度总结能算「今年改分最多的 5 部」；(b) 工程上变更历史与 1.3 的 outbox 天然同源，同一份 `(field, before, after, stamp)` 数据既能补交也能展示。
- **原理**｜追加表 `collection_change(id, subjectId, field, oldValue, newValue, source[LOCAL|BANGUMI|WEBDAV|IMPORT], stamp, deviceId)`，**只追加不更新**，按 `subjectId` 建索引；超过 5000 行时按 `stamp` 归档（每年首末各留一条）。写入点收敛到一处：`OutboxRecorder`（1.3）在比较 before/after 时顺手写 change 行——一份数据两用。展示层分页读最近 20 条。
- **效果**｜详情页「变更历史」折叠区（默认收起，显示「共 7 次修改」）；首次进入无记录时整块隐藏；导入的条目首条记录标「来自备份」。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/readthrough.py` [码] `ProgressUpdate`（追加式进度表，`create_update()` 每次写新行）；`navidrome/navidrome@master:model/annotation.go` [码] `RatedAt/StarredAt`（每个动作带自己的时间戳，而不是一个全局 updateTime）。
- **成本/风险**｜M｜风险：写放大——批量导入 3000 条会一次写 3000 行 change，必须支持「导入期间关闭 change 记录」的开关，否则首次导入会卡。
- **优先级**｜P2

### 1.22 评分刻度可自定义

- **现状**｜部分：刻度硬编码为 0.5 步进、0–10（`util/RatingFeedbackPolicy.kt` `STEP = 0.5f`、`MAX_RATING = 10f`），滑块 `steps=19`；「半分/整数舍入」只用于均分生成，不是用户配置。
- **提升**｜(a) 用户可见：可切「整数 0–10 / 半分 0–10 / 五星（0.5 星）」三种刻度，选择落库并随备份走；(b) 工程上把三处硬编码（滑块、反馈策略、卡片显示）收敛成一处配置。
- **原理**｜`RatingScale(step: Float, max: Float, style: INT|HALF|STAR)` 进 AppSettings；`RatingFeedbackPolicy` 的 `snapped/tickIndex/crossesTick` 已全部接收 `step` 参数（当前默认值就是 0.5），只需把默认值改成从设置注入。存库仍统一为 0–10 浮点（**绝不按刻度存原始值**），展示时再量化——这样切换刻度不丢精度。
- **效果**｜设置→动作与反馈多一项「评分刻度」；切到「整数」后滑块变 10 格、拖动无中间值、触感每格一次；已存的 7.5 分在整数模式下显示 8（旁边小字「7.5」保真）；分享卡按当前刻度渲染星形。
- **参考**｜`IgnisDa/ryot@main:crates/services/importer/myanimelist/src/lib.rs` [码] `item.my_score * 10`（外部刻度与内部刻度之间必须显式换算，不能靠默认值）；本项目 `util/RatingFeedbackPolicy.kt` [码]（参数化已就绪）。
- **成本/风险**｜S｜风险：Bangumi 上传只用 `UpdateCollectionRequest(type=...)`，不同步评分；UI 必须说明「Bangumi 只同步状态，不同步评分」，否则用户会以为刻度切了就能传五星。
- **优先级**｜P2

### 1.23 观看平台标记

- **现状**｜无：`collections` 没有平台字段；能表达平台信息的只有 Steam 绑定（`SteamBindingEntity`）、B 站季 id（`subjects.biliSeasonId`）这类**数据来源**信息，不是「我在哪看的」。
- **提升**｜(a) 用户可见：收藏编辑多一栏「在哪看的」（可多选：B站/Netflix/本地/影院/其他），统计页出现「按平台分布」；(b) 工程上为 7.9 导入提供落点（MAL/B站导入的记录天然知道来源），不再丢失这层信息。
- **原理**｜`collections.watchSources: List<String>`（Room TypeConverter 存逗号串，与 `personalTags` 同款），值域用受控枚举 + 自定义兜底：`builtin = [bilibili, netflix, disney, cinema, local, tv, other]`。统计用 `groupBy` 计数，与 `personalTags` 的词云共用渲染。导入时：B站导入写 `bilibili`，Steam 导入写 `steam`，MAL 导入写 `mal`（来源≠观看平台，但作为兜底比空着好）。
- **效果**｜详情页「我的记录」多一行「在哪看的」chips；统计页「观看平台」环图（复用 `ui/stats/NirikoDonutChart`）；空值条目不进环图并在图下注「N 部未标注」。
- **参考**｜`IgnisDa/ryot@main:crates/models/media/src/import_export.rs` [码] `providers_consumed_on: Option<Vec<String>>`（一个条目可多个消费平台）；`IgnisDa/ryot@main:crates/services/importer/myanimelist/src/lib.rs` [码] `providers_consumed_on: Some(vec![ImportSource::Myanimelist])`。
- **成本/风险**｜S｜风险：与 `personalTags` 的「来源标签」用法重叠，老用户可能已在标签里写「B站」——需要一次性迁移提示，而不是静默双写。
- **优先级**｜P1

### 1.24 复习/重刷计划

- **现状**｜无：没有任何「计划何时重看」的字段或调度；`data/notification/` 只有放送提醒（`AiringReminderScheduler` + `AiringReminderWorker`）。
- **提升**｜(a) 用户可见：可以给「看过」的条目定一个「2026-12 重看」计划，到点收到一条通知；(b) 工程上**不新建调度器**——复用已有一整套 WorkManager + 通知合成。
- **原理**｜扩展 `collection_readthrough`：`plannedStart: LocalDate?`（未开始轮次）。调度走 `OneTimeWorkRequest + setInitialDelay`，worker 入队时现算 delay，重排时 `cancelUniqueWork("rewatch:{subjectId}") + enqueueUniqueWork`（与 `AiringReminderScheduler` 的 `enqueueUniquePeriodicWork` 同款幂等键思路）。通知样式复用 `AiringNotificationComposer`。
- **效果**｜详情页「已看过」状态下出现「计划重看」日期选择；到点通知「你计划今天重看《X》」，点开直达详情；计划过期未开始则显示「计划已过期 · 重新安排」。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/readthrough.py` [码] `start_date` 允许未来值（一条 ReadThrough 可在开始前就存在）；本项目 `data/notification/AiringReminderScheduler.kt` [码]。
- **成本/风险**｜M｜风险：Android 12+ 精确闹钟权限；若只做「每日巡检一次」的实现，到点误差可达数小时，文案不能说「准时」。
- **优先级**｜P2

### 1.25 弃番原因记录

- **现状**｜无：`status = DROPPED` 之外没有任何原因字段；`remark` 可承载但无 UI（见 1.11）。
- **提升**｜(a) 用户可见：抛弃时可选原因（节奏/画风/剧情/没时间/其他）+ 一句话；(b) 工程上给「为什么不再看」一个可统计维度，年度总结能说「今年弃了 12 部，主因节奏」。
- **原理**｜不做新表：`collections.dropReason: String?`（受控 key）+ 复用 `remark` 存自由文本。抛弃流程：`StatusChangeIntent(DROPPED)` 弹一层底部小面板（5 个 chip + 可选文本），跳过一次即不再问（同 session 内）。统计用 `groupBy(dropReason)`。
- **效果**｜选「抛弃」后底部滑出「为什么弃？（可跳过）」；跳过则不写原因，卡片不显示；有原因时卡片长按浮层显示「弃因：节奏」。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/readthrough.py` [码] `stopped_date`（停读只记日期，**也没有原因字段**——连成熟产品都当低优先，可据此定 P2）。
- **成本/风险**｜S｜风险：多一次点击会让「快速抛弃」变慢，必须有「跳过」且默认聚焦跳过。
- **优先级**｜P2

### 1.26 收藏导出为可读清单（合并自 7.5）

> 合并说明：v1 的 1.26 与 7.5 是同一件事（人可读清单导出）。**实现方案、成本、验收全部写在 7.5**，此处只保留 1.26 的编号与收藏视角差异，避免两处写不同结论。

- **现状**｜部分：只有机读 JSON（`data/backup/BackupManager.exportToJson`，v4）与单作品分享卡（`ui/share/ShareCardRenderer`），没有列表级的 Markdown/CSV。
- **提升**｜同 7.5：(a) 用户可见：一键得到「我的收藏.md」，可直接贴到博客/群聊；(b) 工程上纯函数生成 + 快照测试，零 IO 依赖。
- **原理**｜同 7.5：`LibraryExporter.renderMarkdown(items, options)` 纯函数；收藏视角额外要求**按状态分组**（在看/想看/看过/搁置/抛弃 五个 section），每组内按 `updateTime` 倒序，行格式「- [10/12] 标题（8.5）· 标签」。
- **效果**｜同 7.5；收藏视角多一行「按状态分组」开关（默认开）。
- **参考**｜同 7.5：`IgnisDa/ryot@main:crates/services/exporter/src/export_operations.rs` [码]（按类别流式导出）。
- **成本/风险**｜S｜同 7.5，风险是中文宽度对齐。
- **优先级**｜P1

## 1.9 本域新增候选（发散）

### 1.27 状态迁移守卫与副作用

- **现状**｜无 · 新提出：`onUpdateStatus`（`SubjectDetailScreen.kt:2101`）与 `updateStatus`（`CollectionViewModel.kt:191`）都是「直接覆盖 status」，没有前置校验也没有副作用：想看→看过不会补 `finishDate`，看过→在看不会清 `finishDate`，进度也不会自动填满。
- **提升**｜(a) 用户可见：点「看过」时进度自动补到总集数、完成日期自动填今天；(b) 工程上把「状态」从自由字段变成有语义的状态机，统计口径（完成率、完结日期）不再依赖用户手动补日期。
- **原理**｜`StatusTransition.plan(from, to, collection, subject): TransitionPlan` 纯函数返回 `TransitionPlan(status, finishedProgress: Int?, finishDate: LocalDate?, clearFinishDate: Boolean)`。规则：→COMPLETED 时 `finishedProgress = total`（未知则不动）、`finishDate = today`（已有则保留）；COMPLETED→其它时 `clearFinishDate = true`；PLAN_TO_WATCH→WATCHING 时若无 `startDate` 则填今天；→DROPPED 联动私密（1.10）。ViewModel 执行 plan 后再落库，**UI 只发意图**。
- **效果**｜把「在看 6/12」点成「看过」→ 进度变 12/12、完成日期跳到今天、完成庆祝动画只播一次；再改回「在看」→ 完成日期消失（进度保留 12）；无总集数时进度不变、日期照填；每次迁移有单测覆盖 20 组（5×5 排除自身）。
- **参考**｜`IgnisDa/ryot@main:crates/utils/dependent/progress/src/lib.rs` [码] `MetadataProgressUpdateChange` 命令枚举 + `MetadataProgressUpdateChangeCreateNewCompletedInput::StartedAndFinishedOnDate`（「变更」是显式命令，日期是命令参数）；`bookwyrm-social/bookwyrm@main:bookwyrm/models/readthrough.py` [码]（active 与 finish_date 互斥由 `save()` 强制）。
- **为什么是 Niriko**｜本项目状态只有 `WatchStatus` 一个枚举列、没有服务端状态机，所以「迁移副作用」只能由本地纯函数补齐；而 1124 项 JVM 单测的底子让它能做成 20 组穷举而不增加手工回归成本。
- **成本/风险**｜S｜风险：批量改状态（`batchUpdateStatus`）会对 300 条同时补日期，应改为「批量不补日期，只在单条时补」。
- **优先级**｜P1

### 1.28 选集网格区间框选

- **现状**｜无 · 新提出：选集是 `FlowRow` 的独立 `SelectionChip`，点第 7 格就写 7；补番时「从第 3 集看到第 9 集」要点 7 次（每次一次 `update` + 一次可能的同步入队）。
- **提升**｜(a) 用户可见：长按第 3 格拖到第 9 格，一次框选 7 集；(b) 工程上把 7 次写入合并成 1 次（1 次 Room update、1 个 outbox cell），也避免 7 次完成动画。
- **原理**｜用 `pointerInput + detectDragGesturesAfterLongPress` 维护 `anchorIndex` 与 `hoverIndex`，区间 = `min..max`；拖动中只更新本地 `selectedRange` 状态（不写库），抬手一次性 `onRangeSelected(from, to)`。ViewModel 侧 `setProgressRange` 只写一次并只入队一个 cell（`watchedEpisodes` 取 max）。框选视觉：区间内 chip 半透明主色，首尾加圆角差异。
- **效果**｜长按任一集出现「拖到目标集」提示与跟随手指的高亮；松手后 7 格同时变实心、进度数字一次跳到 9；拖到已看区间则提示「框选只能前进」并不写入。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/domain/track/store/DelayedTrackingStore.kt` [码]（进度回写只保留最大值，天然支持「一次跳多集」）；多选手势无源码证据，**标 [推断]**。
- **为什么是 Niriko**｜补番是 Bangumi 主源用户的核心场景（选集网格上限已被提到 300 集正是为此），而纯本地写入允许「抬手一次落库」，不必考虑服务端限流或批量接口。
- **成本/风险**｜S｜风险：`FlowRow` 换行导致 chip 位置不稳定，拖拽命中测试要自己算网格坐标，易调难精。
- **优先级**｜P2

### 1.29 进度回退撤销

- **现状**｜无 · 新提出：长按浮层「−」与详情页进度输入都能把进度改小，写完直接落库（并可能入队同步），没有撤销；误触只能凭记忆改回去。
- **提升**｜(a) 用户可见：任何进度回退后 5 秒内出现「已从 9 改为 8 · 撤销」；(b) 工程上把「回退」变成需要显式的第二动作，顺带解决 1.3 队列里「max 合并规则会吞掉回退」的问题。
- **原理**｜回退操作在 outbox 里以 `mergeRule = 'set'`（而非 `max`）+ `force = true` 入队，并在 UI 侧保存 `pendingUndo = (subjectId, field, oldValue, outboxKey)`。撤销 = 写回 oldValue 并**删除该 outbox 行**（若尚未 flush）；若已 flush，则再入队一次 `set(oldValue)`。用 `LaunchedEffect(pendingUndo)` + `delay(5000)` 自动清空。
- **效果**｜点「−」后 Snackbar「8 集 · 撤销」；5 秒后消失；撤销后进度回到 9 且**不产生第二次同步流量**（未 flush 时直接删行）；连续快速回退只保留最后一个 Snackbar。
- **参考**｜`actualbudget/actual@master:packages/loot-core/src/server/sync/index.ts` [码]（cell 级消息可被更新值覆盖，撤销等价于写回旧值）；本项目 `util/AsyncSerialQueue.kt` [码]（串行化写入，避免撤销与写入交错）。
- **为什么是 Niriko**｜无服务端意味着没有「服务端已接受」这道兜底，本地误触就是最终结果；1.3 的队列还会在后台把错误值提交出去——所以撤销必须做在入队层，而不是 UI 层。
- **成本/风险**｜S｜风险：与 `max` 合并规则冲突——必须先落地 1.3 的 `mergeRule` 字段，否则回退会被 max 吞掉。
- **优先级**｜P1

### 1.30 收藏体检（本地一致性巡检）

- **现状**｜无 · 新提出：现有只有**单点补丁**——`pruneMembersNotInCollections`（`SyncManager.kt:234`）、`SteamPlaceholderMigrator`、`CollectionRepository.observeAllWithSubject().catch { emit(emptyList()) }`（遇到 Room `@Relation` 缺行就整表变空）。没有一处能主动告诉用户「你的库有 3 条孤儿、2 条进度越界、1 条重复绑定」。
- **提升**｜(a) 用户可见：设置→维护多一个「收藏体检」，列出问题条目并给出「一键修复」；(b) 工程上把散落的三处修复逻辑收进一个可重跑的 `LibraryIntegrityChecker`，也省掉「同步后整表变空且不知道为什么」的排查成本。
- **原理**｜一组只读查询 + 规则（修复才写）：①孤儿收藏（`collections LEFT JOIN subjects WHERE s.subjectId IS NULL`）；②进度越界（`watchedEpisodes > totalEpisodes` 或卷/分钟）；③重复外部绑定（同 `externalId+provider` 指向多个 subject）；④分区幽灵成员（`library_folder_subject` 指向不存在收藏）；⑤空标题占位（`title = '未知作品'` 且从未拉详情）。每条规则返回 `Issue(kind, subjectId, detail, fixable)`，UI 分组列出；修复动作复用已有方法。
- **效果**｜体检页显示「发现 6 个问题 · 4 个可自动修复」，每行「孤儿收藏 · subjectId 12345 · 修复」；全部正常时显示「没有问题（312 条收藏，23 个分区）」；修复后行逐条消失并 toast「已修复 4 项」。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupFileValidator.kt` [码]（把「缺什么」做成可列举的 `Results` 而非布尔）；本项目 `data/local/dao/LibraryFolderDao.kt` [码] `pruneMembersNotInCollections/foldersWithOrphanMembers`。
- **为什么是 Niriko**｜本项目 23 张表 + 显式迁移 v2→v32，且同步用 `clearAll/insertAll` 整体重写 collections——孤儿与幽灵成员是结构性风险；诊断页与纯函数单测的传统已具备「把校验写成可单测查询」的条件。
- **成本/风险**｜M｜风险：修复动作必须逐条可预览，「删除孤儿」建议只报告 + 打开详情，不自动删。
- **优先级**｜P1

### 1.31 可见性三级（公开 / 仅自己 / 仅本机）

- **现状**｜无 · 新提出：可见性是布尔 `isPrivate`，语义混着两件事——「不上传 Bangumi（公开站）」与「不进备份/同步（本机）」。用户无法表达「Bangumi 私密但我要备份」。
- **提升**｜(a) 用户可见：三档可选「公开 / 仅自己（站内私密，仍进备份与同步）/ 仅本机（不进任何导出）」；(b) 工程上把「隐私」与「可传输性」两个维度拆开，`SyncManager`/`BackupManager` 里两处 `filter { !it.isPrivate }` 硬过滤才有明确依据。
- **原理**｜`enum class Visibility { PUBLIC, PRIVATE_REMOTE, LOCAL_ONLY }` 存 `collections.visibility: String`（迁移把 `isPrivate=true` → `LOCAL_ONLY`，false → `PUBLIC`）。传播规则表：Bangumi 上传仅 `PUBLIC` 写评论；WebDAV/备份排除 `LOCAL_ONLY`、包含 `PRIVATE_REMOTE`；分享卡默认排除 `LOCAL_ONLY`。
- **效果**｜详情页开关变成三档分段（公开/仅自己/仅本机）+ 一行解释当前档位会去哪；选「仅本机」时卡片右上出现锁图标；备份确认弹窗文案从「私密收藏本来就不在备份里」改成「N 条『仅本机』记录不会进备份」。
- **参考**｜`IgnisDa/ryot@main:crates/models/media/src/import_export.rs` [码] `Visibility` 作用在 `ImportOrExportItemReview` 上；`bookwyrm-social/bookwyrm@main:bookwyrm/models/shelf.py` [码] `privacy = fields.PrivacyField()`（可见性是独立枚举而非布尔）。
- **为什么是 Niriko**｜纯客户端 + Bangumi 主源 + 备份/同步双通道，「可见性」在这里要同时回答「上不上 Bangumi」和「进不进备份」两个问题，布尔字段表达不了；玻璃卡片的锁角标又是现成的展示位。
- **成本/风险**｜M｜风险：`isPrivate` 现在**不参与序列化**（见 7.11），升级时要保证 `LOCAL_ONLY` 不泄露、同时 `PRIVATE_REMOTE` 真的能传——需要改 7.11 的实现而不是只改枚举。
- **优先级**｜P1

### 1.32 观看会话日志

- **现状**｜无 · 新提出：系统里没有「一次观看」的概念，只有最终进度值；1.16/1.17/1.21 全都缺原始数据基础。
- **提升**｜(a) 用户可见：年度总结能有「观看总时长」「最活跃时段」，而不是只有集数；(b) 工程上一次记录同时喂饱剩余估算、连击、变更历史三条需求，避免各写一套推导。
- **原理**｜表 `watch_session(id, subjectId, epId?, startedAt, endedAt, minutes, source[MANUAL|IMPORT], note?)`。写入方式**默认不打扰**：详情页「保存记录」时若 `watchedEpisodes` 增加 k 集，则生成一条 `minutes = k * 中位时长` 的会话（`source=MANUAL`）；单集页「看到这里」生成一条以该集时长为 minutes 的会话。结束时间 = 写入时刻，开始时间 = 结束 − minutes。日志页只读，按月聚合。
- **效果**｜统计页出现「本月观看 18 小时 40 分」；年度总结有「最活跃时段 22:00–24:00」；无会话数据时这些块不渲染（不显示 0 小时）。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/readthrough.py` [码] `ProgressUpdate`（每次进度变化都留一行，历史是事实不是推导）；`navidrome/navidrome@master:model/annotation.go` [码] `PlayCount + PlayDate`。
- **为什么是 Niriko**｜统计页全部由纯函数算出、没有任何埋点上报，缺的正是原始事件；本项目不做播放器，拿不到播放器侧的观看时长，只能在自家写路径上生成会话——这正是它比别家更需要的理由。
- **成本/风险**｜M｜风险：`source=MANUAL` 的分钟数是**估算**，若当作真实时长展示会误导；聚合必须带「估算」标记，或只用于相对比较（热力条）不显示绝对时长。
- **优先级**｜P2

## 1.A 本域新增参考库

| 仓库 | 许可 | star | 语言 | 为何与本项目相关 | 已验证文件 |
| --- | --- | --- | --- | --- | --- |
| IgnisDa/ryot | GPL-3.0 | 3.6K | Rust + TypeScript | 跨类型追踪的状态/进度/日期/命令模型；自定义字段模板；导入报告表 | `crates/models/database/src/collection.rs`、`crates/models/database/src/collection_to_entity.rs`、`crates/models/media/src/import_export.rs`、`crates/utils/dependent/progress/src/lib.rs`、`crates/models/database/src/import_report.rs`、`crates/services/importer/myanimelist/src/lib.rs` |
| mihonapp/mihon | Apache-2.0 | 24.5K | Kotlin | 待同步队列的唯一形（按 key 合并、只增不减、成功即删）；备份分类勾选与预检 | `app/src/main/java/eu/kanade/domain/track/store/DelayedTrackingStore.kt`、`.../data/backup/create/BackupOptions.kt`、`.../data/backup/BackupFileValidator.kt` |
| bookwyrm-social/bookwyrm | GitHub 未识别 | 2.8K | Python/Django | 「一次阅读」= ReadThrough 实体（多轮、起止、暂停）；进度追加日志；评分可见性开关 | `bookwyrm/models/readthrough.py`、`bookwyrm/models/shelf.py`、`bookwyrm/migrations/0209_user_show_ratings.py` |
| navidrome/navidrome | 徽章未取到 | 24.2K | Go | 计数/评分/收藏各自带时间戳（逐动作时间戳，而非单一 updateTime） | `model/annotation.go` |
| actualbudget/actual | MIT | 29.6K | TypeScript | cell 级变更合并（`messages_crdt` / `messages_pending`，冲突按列取新） | `packages/loot-core/src/server/sync/index.ts`、`packages/crdt/src/crdt/timestamp.ts` |

> star 数据来源 img.shields.io / badgen.net，取数时间 **2026-10**；两个「未取到」的仓库不编造数值。

## 1.B 计数自检

- 汇总表行数：**32**（1.1–1.26 共 26 条 v1 条目 + 1.27–1.32 共 6 条新增候选）。
- 详解条目数：**32**（1.1–1.26 全部保留原编号与名称；1.26 标题标注「合并自 7.5」并交叉指向）。
- 新增候选：**6** 条（1.27–1.32），全部标注「无 · 新提出」并给出「为什么是 Niriko 需要」。
- 32 = 32 ✓；本域无条目删除、无编号重排。
- 新增候选全部带 `**为什么是 Niriko**` 字段（与本项目纯客户端 / Bangumi 主源 / 1124 单测 / 无 DI / 玻璃 UI 的约束逐条挂钩）。
- [码] 证据：外部 5 个仓库的 12 个文件全部实际 fetch 并读过符号；[推断] 仅用于 1.7（星期展示）、1.9（遮罩交互）、1.20（截图）、1.28（多选手势）四处，均已就地标注。
