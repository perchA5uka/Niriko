# 7 同步、导入与备份（16 条 v1 + 6 条新增）

> 一句话结论：本域最值得先做的是 **7.17 乐观并发（ETag/If-Match + 本地上传基线）**（P0）——现在「上传」是**不读远端直接 PUT**（`WebDavClient.put` 只有基本 PUT），两台设备会静默互相覆盖；其次是 **7.11 同步范围补全**（P1，`watchedVolumes` 卷进度在 WebDAV 与 JSON 备份里**根本不参与序列化**，换设备必丢）与 **7.13 逐条失败原因**（P1，现在一条坏行让整批事务回滚，只留下一个字符串）。
>
> 本域全部结论对照 `data/sync/{SyncManager,SyncMergePolicy,WebDavClient}.kt`、`data/sync/bangumi/*`、`data/backup/{BackupManager,RestoreMergePolicy,BackupFolderRestore,BackupFolderNames}.kt`、`data/refresh/*`、`NirikoApplication.kt` 实测源码。

## 7.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 7.1 | 按类别勾选导出/导入 | 部分 | P1 | M | mihon `BackupOptions`（10 个布尔 + 依赖联动） |
| 7.2 | 导入时剔除无效条目 | 部分 | P1 | M | mihon `BackupFileValidator`；bookwyrm `ImportItem.fail_reason` |
| 7.3 | 导入导出显示各类条目计数 | 部分 | P1 | S | mihon `BackupOptions` 计数 + ryot `import_report` |
| 7.4 | 增量覆盖式恢复的冲突提示 | 部分 | P1 | M | 本项目 `RestoreMergePolicy` + 1.4 的 CellDiff |
| 7.5 | Markdown / CSV 清单导出 | 无 | P1 | S | MAL CSV 列名对齐（可直接回灌 7.9） |
| 7.6 | 报告导出为离线 HTML | 无 | P2 | M | ryot `export_operations` 分类流式写出 |
| 7.7 | 导出内容分级（含/不含 NSFW） | 部分 | P2 | S | 现有 `nsfwEnabled` 只用于搜索 |
| 7.8 | 跨平台收藏迁移 | 部分 | P2 | M | ryot MAL 导入的逆向映射 |
| 7.9 | 兼容通用格式导入（CSV/XML） | 无 | P1 | L | ryot MAL（gzip XML）+ bookwyrm Goodreads CSV |
| 7.10 | 目录 / 清单（List）概念 | 部分 | P2 | M | ryot `collection` + `collection_to_entity` |
| 7.11 | WebDAV 同步范围补全 | 部分 | P1 | L | 本项目 `SyncManager` 字段清单 vs 备份字段清单 |
| 7.12 | 同步冲突可视化 | 部分 | P1 | M | actual `messages_crdt`；kopia `RetentionReasons` |
| 7.13 | 同步失败的逐条原因 | 部分 | P1 | M | bookwyrm `ImportItem.fail_reason`；ryot `import_report` |
| 7.14 | 备份兼容性提示 | 部分 | P1 | S | actual `messages_pending`（保留未知字段） |
| 7.15 | 定时/触发式备份与保留策略 | 部分 | P1 | M | kopia `RetentionPolicy`+`ComputeRetentionReasons` |
| 7.16 | 第三方导入的失败重试 | 部分 | P2 | M | bookwyrm `ImportJob.status` + `pending_items` |
| 7.17 | 乐观并发（ETag / If-Match） | 无 · 新提出 | **P0** | M | actual HULC 时间戳；HTTP `If-Match` |
| 7.18 | 同步日记（可回溯的运行记录） | 无 · 新提出 | P1 | S | ryot `import_report`；本项目 `RefreshStatusLabels` |
| 7.19 | cell 级 CRDT（逐字段合并） | 无 · 新提出 | P1 | L | actual `messages_crdt` + `Timestamp`(HULC) |
| 7.20 | 备份去重与完整性校验 | 无 · 新提出 | P1 | S | kopia `IgnoreIdenticalSnapshots` |
| 7.21 | 导入 dry-run 预检报告 | 无 · 新提出 | P1 | M | mihon `BackupFileValidator` 的「先验后写」 |
| 7.22 | 设备指纹与旧版客户端检测 | 无 · 新提出 | P2 | M | actual `makeClientId`（node id） |

## 7.1 逐条详解

### 7.1 按类别勾选导出/导入

- **现状**｜部分：`BackupManager.exportToJson` 是**整包**——一次无条件写出 `subjects/collections/workItems/searchHistory/personCollections/externalIds/myEpisodeRatings/manualAwards/coverOverrides/libraryFolders/settings` 十个块（`data/backup/BackupManager.kt:84-169`）；`importFromJson` 也按固定清单全部解析（191-201 行），调用方只能在「合并 / 全覆盖」之间选（且设置页固定用合并）。
- **提升**｜(a) 用户可见：导出前能勾「只要收藏和评分，不要搜索历史/设置」，导出后弹「约 1.2 MB · 812 条」；导入前能勾「只恢复作品库分区」；(b) 工程上把「某表太大不想带」从改代码变成用户选择，也天然解决 1.20 截图、7.6 报告这类「可选大块」。
- **原理**｜照 mihon 的形状做 `data class BackupSelection(` 10 个 Boolean `)`，与导出器一一对应：`libraryEntries / episodeRatings / externalIds / awards / covers / folders / history / persons / settings / subjects`。`canCreate()` 至少选一项；带依赖的项置灰（勾「评分」自动要求 `libraryEntries`，这是 mihon 的 `Entry.enabled = { it.libraryEntries }`）。导出器改成 `if (sel.collections) put("collections", ...)`，导入器对称：**未选中的块视为「本次不动」而不是「清空」**（合并模式下 `keepOnlyNew` 天然满足；全覆盖模式要改成「只清选中的表」）。
- **效果**｜导出弹层是勾选列表 + 每项右侧计数（`collections 312`）；关掉「设置」后文件里真的没有 `settings` 键；导入后 Snackbar「已恢复：收藏 312、分区 4」；只勾分区导入时收藏不受影响。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupOptions.kt` [码] `asBooleanArray()/canCreate()/libraryOptions/Entry(getter,setter,enabled)`；`.../create/BackupCreator.kt` [码] 按选项插桩各 `*BackupCreator`。
- **成本/风险**｜M｜风险：导出块与导入块必须共用同一份 key 常量，否则「导出有、导入不认」——现在 `BackupManager` 里字符串是散写的（`"externalIds"`、`"myEpisodeRatings"` 各出现两次）。
- **优先级**｜P1

### 7.2 导入时剔除无效条目

- **现状**｜部分：已有三层防护但**没有逐项跳过**：①版本闸 `version < 1 || > EXPORT_VERSION` 直接失败（`BackupManager.kt:186`）；②`collections` 只要有一条指向不存在的 subject，**整包失败**并抛「备份数据不完整：存在收藏指向不存在的作品」（209-211 行）；③BUG-8 之后 `externalIds/myEpisodeRatings/manualAwards` 会按 `knownSubjectIds` 过滤（204-206 行）。被过滤掉多少条、为什么，都没有返回。
- **提升**｜(a) 用户可见：恢复结果页出现「导入 812 条 · 跳过 3 条（2 条指向不存在的作品、1 条状态值未知）」，并可展开看逐条；(b) 工程上把「整包失败」改成「逐条跳过」，用户不会因为一条脏数据丢掉整份备份。
- **原理**｜把校验分两级：**fatal**（JSON 解析失败、版本不支持）与**skippable**（指向不存在的 subject、`WatchStatus.valueOf` 抛错、日期 parse 失败、分区成员不存在、`epId` 与 `subjectId` 不一致）。skippable 收集成 `data class SkippedItem(table: String, key: String, reason: String)`，导入事务照常提交，返回 `ImportResult(skipped: List<SkippedItem>)`。原本「collections 校验失败即整包失败」改成「跳过该条并在结果页展示 5 条样例」。
- **效果**｜恢复完成弹层分两段：「已导入 812 条（收藏 310 / 分区 4 / 评分 498）」与「跳过 3 条 ▸」（展开有表名 + 主键 + 原因）；全部成功时不显示第二段。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupFileValidator.kt` [码] `Results(missingSources, missingTrackers)`（把缺失项列成清单）；`bookwyrm-social/bookwyrm@main:bookwyrm/models/import_job.py` [码] `ImportItem.fail_reason`（每条自带失败原因）。
- **成本/风险**｜M｜风险：孤儿 `collection` 跳过会留下 `SubjectEntity` 占位（`"未知作品"`），下次同步又会被 `SyncManager` 的 FK 补全逻辑写回——需要在结果里区分「跳过」与「以占位导入」。
- **优先级**｜P1

### 7.3 导入导出显示各类条目计数

- **现状**｜部分：`ImportResult` 有 `subjectsCount/collectionsCount/workItemsCount/historyCount/foldersCount`，但**只有 inserted 数**，没有 skipped/updated（`BackupManager.kt:790-800`）；导出侧完全没有计数预览（`exportToJson` 直接把数据写出来）。
- **提升**｜(a) 用户可见：导出前看到「将导出 312 条收藏 / 498 条单集评分 / 4 个分区」，导入前看到「本文件含 310 条收藏」；(b) 工程上把「备份里到底有什么」变成可核对的事实，避免用户拿一个 30 KB 的空备份覆盖掉 3000 条数据。
- **原理**｜引入 `BackupSummary(library, episodes, externalIds, awards, covers, folders, persons, history, settingsIncluded: Boolean, bytes: Long)`，由一组 `SELECT COUNT(*)` 在同一 `withContext(Dispatchers.IO)` 里并发取（Room 支持挂起查询）；导出流程变成「先 `summarize()` → 弹层确认 → `exportToJson(selection)`」。导入侧 `ImportResult` 增加 `skippedCount` 与 `updatedCount`（当前恒 0，因为合并策略是「本地优先、只并入新记录」）。
- **效果**｜导出弹层每项后面跟数字，底部「约 1.2 MB」；导入确认弹层显示「本文件：收藏 310 · 评分 498 · 分区 4 · 版本 v4（2026-09-30 导出）」；导入完成后同一个弹层把「将导入」换成「已导入」。
- **参考**｜`mihonapp/mihon@main:.../backup/create/BackupOptions.kt` [码]（勾选项与数据块一一对应，因此计数天然可算）；`IgnisDa/ryot@main:crates/models/database/src/import_report.rs` [码] `progress: Option<Decimal>`（导入进度是持久字段）。
- **成本/风险**｜S｜风险：`COUNT(*)` 在 20 万条 subjects 上会有几十毫秒，必须在 IO 线程且不能阻塞弹层动画。
- **优先级**｜P1

### 7.4 增量覆盖式恢复的冲突提示

- **现状**｜部分：恢复固定走 `merge = true`（`SyncBackupSettingsScreen.kt:299`），`RestoreMergePolicy.keepOnlyNew` 的语义是「本地已存在的一律丢弃备份值」（`data/backup/RestoreMergePolicy.kt`），因此**冲突项被静默丢弃**——数量都不返回，更没有逐条列表。Confirm 弹窗只有一句「与本地冲突的记录以本地为准」。
- **提升**｜(a) 用户可见：恢复前列出「12 条与本机冲突，其中 2 条备份里的更新时间更新（本机仍会保留）」并允许逐条选择；(b) 工程上恢复不再是「要么全信本地、要么全信备份」，也顺带修好「这条策略让恢复不具备回滚能力」这个已知副作用（该文件注释已自认）。
- **原理**｜复用 1.4 的 `CellDiff`：`RestoreDiffCalculator.diff(backup, local)` 返回三组——`onlyBackup`（将并入）、`onlyLocal`（不动）、`conflict`（双方都有且字段不同）。冲突行给三态：`KEEP_LOCAL`（默认，等于现状）、`TAKE_BACKUP`、`SKIP`；用户改判的结果以 `Map<(table,key), Choice>` 传给 `importFromJson(overrides)`，事务内按 overrides 决定 `keepOnlyNew` 还是 `replace`。
- **效果**｜恢复确认弹层多一段「冲突 12 条 ▸」，展开是三列（条目 / 本机 / 备份）与单选；默认全选「保留本机」；底部按钮文案随选择变化「恢复（保留本机 10 条、采用备份 2 条）」。
- **参考**｜本项目 `data/backup/RestoreMergePolicy.kt` [码]（`keepOnlyNew` 是本次改造的落点）；`actualbudget/actual@master:packages/loot-core/src/server/sync/index.ts` [码]（冲突按 cell 决策，而不是按整行）。
- **成本/风险**｜M｜风险：冲突判定依赖 `updateTime`，而 `updateStatus` 不更新它（见 1.3），会出现「字段不同但时间相同」→ 全部算冲突；需要先把时间戳修好。
- **优先级**｜P1

### 7.5 Markdown / CSV 清单导出

- **现状**｜无：只有机读 JSON（v4）与单作品分享卡（`ui/share/ShareCardRenderer`），没有列表级人可读导出。`BackupViewModel.export` 直接把 JSON 交给 SAF 写文件。
- **提升**｜(a) 用户可见：一键得到「我的收藏.md」（按状态分组、含进度/评分/标签）与「我的收藏.csv」（Excel 直接开）；(b) 工程上纯函数生成 + 快照测试，零 IO、零新依赖，且 CSV 列名对齐 MAL 后**同一份文件可以直接回灌 7.9**。
- **原理**｜纯函数 `LibraryExporter.renderMarkdown(items, options): String` 与 `renderCsv(items, options): String`。CSV 用 RFC4180（字段含逗号/引号/换行时加双引号并转义），UTF-8 加 BOM 保证 Excel 不乱码；列名取 MAL 导出习惯：`series_title, series_type, my_watched_episodes, my_score, my_status, my_start_date, my_finish_date, my_tags, my_comments`（MAL 的 `my_score` 是 10 制整数 → 导出时四舍五入，原值另存一列 `niriko_score` 保真）。Markdown 按 5 状态分组，行格式「`- [10/12] 标题（8.5）· #标签`」，超长标题截到 40 字。
- **效果**｜导出菜单出现「Markdown 清单 / CSV 清单」两项；生成后在系统分享面板可选「保存到文件 / 发到微信」；Markdown 在 GitHub/语雀里渲染正常，CSV 在 Excel 里列对齐且中文不乱码；空收藏时按钮置灰并提示「还没有收藏」。
- **参考**｜`IgnisDa/ryot@main:crates/services/exporter/src/export_operations.rs` [码] `JsonStreamWriter` + 按 `ExportItem` 分类逐个 `begin_array/end_array`（分类导出的结构范式）；`bookwyrm-social/bookwyrm@main:bookwyrm/importers/importer.py` [码] `row_mappings_guesses`（CSV 列名的多别名猜测表，反向就是导出列名）。
- **成本/风险**｜S｜风险：中文宽度导致 Markdown 表格不对齐；用纯列表格式（非表格）即可规避，别为对齐写复杂 padding。
- **优先级**｜P1

### 7.6 报告导出为离线 HTML

- **现状**｜无：统计页是 Compose 实时渲染（`ui/stats/*`），没有任何「导出成文件」的路径；分享卡只能渲单张图（`ShareCardRenderer`）。
- **提升**｜(a) 用户可见：生成一个可直接在浏览器打开、可打印成 PDF 的「年度报告.html」，包含统计数字、年度图表、Top 收藏；(b) 工程上把统计渲染从「只能在 App 里看」变成「可以留存/打印」，复用已有纯函数数据源，不引入新图表库。
- **原理**｜单文件 HTML：`<style>` 内联（用主题色算出的 CSS 变量），图表用**内联 SVG**——把 `NirikoBarChart/NirikoDonutChart` 的数据（`ChartSeriesCalculator` 的输出）用 Kotlin 字符串模板画成 `<rect>/<path>`，不依赖 JS。封面图默认用远程 URL（离线打开会空），可选「内嵌封面」把前 40 张转 base64（超出用占位色块）。文件通过 SAF `ACTION_CREATE_DOCUMENT` 写出。
- **效果**｜设置→统计多「导出年度报告」；生成后提示「已保存 Niriko-2026.html（约 480 KB）」，文件名带年份；报告页首屏是年份 + 四个大数字（作品数/总集数/均分/完成率），下面是柱图与 Top 10；用浏览器打印时隐藏导航。
- **参考**｜`IgnisDa/ryot@main:crates/services/exporter/src/export_operations.rs` [码]（先 `begin_object` 再按类别写，顺序稳定）；本项目 `data/calculator/ChartSeriesCalculator.kt` [码]（图表数据已与 UI 解耦，可直接复用）。
- **成本/风险**｜M｜风险：HTML 里的 CSS 与项目主题会漂（两套色值），必须从同一份 `SeedColorScheme` 导出 6 个色值，而不是手抄十六进制。
- **优先级**｜P2
### 7.7 导出内容分级（含/不含 NSFW）

- **现状**｜部分：导出与备份只做了一件事——`filter { !it.isPrivate }`（`BackupManager.kt:69`、`SyncManager.kt:95`）；`nsfwEnabled` 仅作为搜索参数（`SubjectSearchViewModel` 传给 `SearchRequestDto`），**不参与任何导出过滤**。
- **提升**｜(a) 用户可见：导出弹层有「排除 NSFW 内容」开关，默认跟随搜索设置；开启后文件里没有 R-18 条目（也不含其评分/单集数据）；(b) 工程上导出一份可安全分享的清单不再需要手动删条目。
- **原理**｜新增纯函数 `ExportFilter.shouldInclude(subject, collection, policy)`，判据三层：①`subjects.tags` 含 `R-18`/`NSFW`/`成人向`（Bangumi tag 实测值）；②`subjects.nsfw` 若未来有该列；③用户显式`isPrivate`。过滤要在**全表落盘前**做，且级联过滤 `episode_ratings/episode_my_ratings`（否则会导出「孤儿评分」）。开关存 AppSettings，默认值 = `nsfwEnabled`。
- **效果**｜导出弹层「内容过滤」一栏有两个开关（排除 NSFW / 排除仅本机）；勾选后弹层顶部提示「已排除 7 条」，文件内相应条目数与评分条数同步减少；导入时被排除的条目当然不存在，不会造成「本地有远端无 → 被删」的错觉（同步是并集，不是镜像）。
- **参考**｜`IgnisDa/ryot@main:crates/models/media/src/import_export.rs` [码] `ImportOrExportItemReview.visibility`（导出格式里带可见性字段，而不是靠外部约定）；本项目 `data/settings/AppSettings.kt` [码] `nsfwEnabled`。
- **成本/风险**｜S｜风险：`subjects.tags` 的 NSFW 标记来自 Bangumi，跨源（AniList/TMDb）条目可能没有 tag，会被漏过——应写「按 Bangumi 标签判定」而不是承诺「绝不含」。
- **优先级**｜P2

### 7.8 跨平台收藏迁移

- **现状**｜部分：进得来但出不去——Bangumi 双向同步（`BangumiSyncManager`）、B站导入（`plugin/bilibili/BilibiliImporter`）、AniList 绑定与拉取（`AniListBindingEntity` + `AniListRepository`）都有，但**没有任何「导出成 MAL / AniList 格式」**，用户无法把自己的库搬到别处。
- **提升**｜(a) 用户可见：设置里出现「导出为 MAL 格式（.xml.gz）」，下载后可直接上传到 MAL 的 Import 页面；(b) 工程上补齐「数据可带走」这一条，也让 7.5 的 CSV 有第二个消费者。
- **原理**｜MAL 导出是 **gzip 压缩的 XML**（导入侧同理：`GzDecoder` 解压后 `serde_xml_rs` 解析）。根节点 `<myanimelist><anime>…</anime><manga>…</manga></myanimelist>`，每条字段：`series_animedb_id / series_title / series_type / series_episodes / my_watched_episodes / my_start_date / my_finish_date / my_score / my_status / my_tags / my_comments`。映射：`WATCHING→1/Watching`、`COMPLETED→2`、`ON_HOLD→3`、`DROPPED→4`、`PLAN_TO_WATCH→6`（MAL 的 5=Plan to Watch 已被弃用、实际用 6——此位按 MAL 导入页的既有约定，本次未从上述仓库取证，标 **[推断]**）；`my_score` 取 `rating.roundToInt()`。只导出 ANIME/MANGA 类型（MAL 没有游戏/音乐）。
- **效果**｜导出后得到 `niriko-mal-2026-10.xml.gz`（约 40 KB / 300 条）；MAL 导入页识别成功并显示「Imported 300 entries」；游戏/音乐条目被跳过并在弹层提示「已跳过 12 条（MAL 不支持的类型）」。
- **参考**｜`IgnisDa/ryot@main:crates/services/importer/myanimelist/src/lib.rs` [码] `decode_data`（`GzDecoder` + `serde_xml_rs`）、`struct Item` 的 `serde(alias = "series_animedb_id")` 映射表；`crates/models/media/src/import_export.rs` [码] `DeployMalImportInput { anime_path, manga_path }`。
- **成本/风险**｜M｜风险：MAL 的状态码（1/2/3/4/6）与 Bangumi（1 想看/2 看过/3 在看/4 搁置/5 抛弃）**不同**，绝不能复用 `BangumiStatusMapper`，必须独立映射表 + 单测。
- **优先级**｜P2

### 7.9 兼容通用格式导入（CSV / XML）

- **现状**｜无：能导入的只有自家 JSON（v1–v4）、B站追番、Kazumi 备份、Steam 库；没有任何通用 CSV/XML 适配器，也没有「列名映射」概念。
- **提升**｜(a) 用户可见：导入页多两个来源「MAL 导出（.xml.gz）」「Goodreads / 通用 CSV」；(b) 工程上把「从别的服务搬家」从手工复制变成一次导入，这是纯客户端应用唯一现实的换机路径。
- **原理**｜适配器接口 `ImportAdapter { fun sniff(header: ByteArray): Boolean; suspend fun parse(stream: InputStream, onRow: (NormalizedRow) -> Unit) }`，注册表按 `sniff` 顺序试（MAL 看魔数 `0x1f 0x8b` 且解压后含 `<myanimelist`；CSV 看首行是否同时含 `title` 与 `my rating`/`shelf`）。`NormalizedRow` 字段与 7.8 一致。CSV 列名用**多别名猜测表**（`my rating`/`rating`/`star rating` 都算评分），解析后先落 `ImportItem`（带 `fail_reason`）再统一提交，避免边解析边写。
- **效果**｜选中文件后先显示嗅探结果「识别为 MAL 导出 · 312 条」；dry-run 预览前 20 行（标题 / 进度 / 评分 / 状态），可取消；开始导入后进度条按行推进；结束后「成功 300 · 失败 12 ▸」。
- **参考**｜`IgnisDa/ryot@main:crates/services/importer/myanimelist/src/lib.rs` [码]（gzip + XML + 进度聚合：`(1..item.done+1)` 展开成 N 条 seen）；`bookwyrm-social/bookwyrm@main:bookwyrm/importers/importer.py` [码] `row_mappings_guesses / shelf_mapping_guesses / create_row_mappings / get_shelf`（列名与状态名的模糊猜测，正是通用 CSV 的核心难点）。
- **成本/风险**｜L｜风险：MAL 的 `my_watched_episodes` 是标量，Niriko 想要的是同一条记录上的 `watchedEpisodes`——**不要**学 ryot 展开成 N 条 seen（那需要每集实体），应直接写 `watchedEpisodes = N`，否则会造出几千条无标题剧集。
- **优先级**｜P1

### 7.10 目录 / 清单（List）概念

- **现状**｜部分：`library_folder` 已经是「清单」的雏形（多对多成员 + 顺序 + 折叠态），但**不对外**：不能导出成独立文件、不能分享、不能跨设备（WebDAV 明确不含，`SyncBackupSettingsScreen.kt:237-243` 有免责文案）。
- **提升**｜(a) 用户可见：分区长按可「导出清单（.json / .md）」发给朋友，对方「从清单导入」后得到同名分区；(b) 工程上把分区从「本机组织方式」升级为「可交换内容」，且不需要服务端。
- **原理**｜清单格式独立于整包备份：`{ "kind":"niriko.list", "version":1, "name":"2026 秋", "items":[{subjectId, title, type, coverUrl, note}] }`——**带标题与类型快照**，因为对方的 subjectId 可能不存在。导入时三级匹配：①同 subjectId 且类型相同 → 直接加；②标题规范化（复用 `TitleResolver`）相同 → 提示「可能是同一部」；③匹配不到 → 用 Bangumi 搜索（`DataSourceChain`）给候选。导出 Markdown 时复用 7.5 的渲染器。
- **效果**｜分区菜单出现「导出清单」「复制为文本」；导入一个朋友的清单后出现新分区「来自清单：2026 秋」，未匹配项以「待确认」灰卡展示，点一下可搜索绑定。
- **参考**｜`IgnisDa/ryot@main:crates/models/database/src/collection.rs` [码]（`collection` 是一等实体，带 `name/description/last_updated_on`）；`crates/models/database/src/collection_to_entity.rs` [码] `rank/information`（成员带排序与自由信息）；`crates/services/exporter/src/collection_exports.rs` [码]（清单可单独导出）。
- **成本/风险**｜M｜风险：清单跨设备时 `subjectId` 是 Bangumi 的，若对方用别的数据源做主源，ID 体系不同——必须允许「以标题+类型」为主键的降级路径。
- **优先级**｜P2

### 7.11 WebDAV 同步范围补全

- **现状**｜部分，且**存在实际数据丢失**。`SyncManager` 的 sync JSON（`SYNC_VERSION = 3`）包含：`collections / workItems / searchHistory / externalIds / episodeMyRatings / manualAwards / coverOverrides`。**不在其中**的有：`libraryFolders`（分区）、`personCollections`（人物收藏）、`subjects`（有意不传，太大）、权威 `episode_ratings`（有意见不传，可重拉）。更严重的是**字段级缺口**：①`watchedVolumes`（书籍/漫画卷进度）在 `SyncManager.collectionToJson`（386-404 行）与 `parseCollections`（430-452 行）里**完全没出现**，也在 `BackupManager.collectionToJson`（359-377 行）里没出现——换设备/恢复备份后卷进度归零；②`isPrivate` 只作导出过滤（`getAll().filter { !it.isPrivate }`），从不回传，且 `mergeCollections` 可能用远端行（`isPrivate=false`）覆盖本地私密行，导致私密条目变公开。
- **提升**｜(a) 用户可见：换设备后「灌篮高手 31 卷」还在，私密条目仍然私密，分区也自动出现；(b) 工程上同步字段清单从「散在 if/put 里」收敛成一张表，这类漏字段错误以后能被单测直接抓住。
- **原理**｜定义 `SyncScope`（代码内表，不是数据库表）：

```kotlin
data class SyncField(val key: String, val merge: Merge, val localOnly: Boolean = false)
enum class Merge { LWW, MAX, UNION, LOCAL_WINS }
// collections: status=LWW, watchedEpisodes=MAX, watchedVolumes=MAX, rating=LWW,
//   startDate/finishDate/personalImpression/remark=LWW, personalTags=UNION,
//   watchedTrackIds=UNION, isPrivate=LOCAL_ONLY(LOCAL_WINS), createTime=MIN, updateTime=LWW
// folders: name(identity), sortOrder=LWW, isCollapsed=LWW, members=UNION
// persons: personId(identity), name/nameCn/career=LOCAL_WINS(快照), createTime=MIN
```

编码器按这张表生成/解析（`SyncJsonCodec`），并把 `SYNC_VERSION` 提到 4。**向后兼容的关键规则：远端缺某字段 → 保留本地值，绝不当作 null 覆盖**（现在 `parseCollections` 对缺字段直接落默认值，这是旧版客户端能把新字段清空的原因）。
- **效果**｜两台设备各自改卷进度 → 同步后取较大值；私密条目在同步后仍带锁；旧版客户端上传的 JSON（无 `watchedVolumes`）不会把新版设备的卷进度清零；诊断页「同步范围」列出 9 个数据块与各自策略。
- **参考**｜本项目 `data/sync/SyncManager.kt` [码]（现清单）、`data/backup/BackupManager.kt` [码]（备份清单，多出 persons/folders）、`data/sync/SyncMergePolicy.kt` [码] `lastWriteWins/localFirst`（现有两种 merge 原语）；`actualbudget/actual@master:packages/loot-core/src/server/sync/index.ts` [码]（列级合并天然按字段而不是按行）。
- **成本/风险**｜L｜风险：①一旦开始传 `watchedVolumes`，**旧客户端会把它丢掉并在回传时抹空**——必须先发版让所有设备升级，或接受「旧客户端上传后新版需重新拉一次」；②`SYNC_VERSION` 从 3 提到 4 会让旧版直接读不懂新文件，需要双读单写过渡。
- **优先级**｜P1

### 7.12 同步冲突可视化

- **现状**｜部分：策略有两套（WebDAV = 按 `updateTime` 的 LWW，`SyncMergePolicy.lastWriteWins`；Bangumi = 整条状态按 `BangumiSyncPriority` 二选一，`BangumiSyncPlanner` 产出 `Conflict` 列表），但**结果不可见**：`SyncResult` 只有 `collectionsMerged/workItemsMerged` 两个数字（`SyncManager.kt:35-42`），`BangumiSyncResult` 只有 `uploaded/downloaded/match/failed`。
- **提升**｜(a) 用户可见：同步后弹「本次合并：本机胜 3 · 云端胜 2 · 新增 7 · 一致 300」，点开看逐条；(b) 工程上「上次同步到底发生了什么」可回放，用户报「我的评分丢了」时能直接定位。
- **原理**｜合并函数从「返回合并结果」改成「返回结果 + 决策」：`mergeCollections(local, remote): MergeOutcome(collections, decisions: List<MergeDecision>)`，`MergeDecision(table, key, field, localValue, remoteValue, localStamp, remoteStamp, winner, rule)`。运行摘要 `SyncRunSummary(at, direction, counts, decisions.take(200), failures)` 序列化成 JSON 存 DataStore key `sync.lastRun.v1`（**不放 Room**：生命周期短、无需迁移）。UI 两处消费：同步完成后 bottom sheet（前 10 条 + 「全部」入口），和刷新诊断页的「上次同步」行。
- **效果**｜同步完成的 sheet 顶部一行计数，中段是「孤独摇滚！ · 状态 想看→看过 · 云端胜（云端 1 小时前 / 本机 3 天前）」这样的行；无冲突时只显示「312 条一致 · 新增 7」；点「查看全部」进只读列表页。
- **参考**｜`actualbudget/actual@master:packages/loot-core/src/server/sync/index.ts` [码] `messages_crdt(dataset,row,column,timestamp,value)`（冲突的最小粒度是 cell，因此决策天然可列）；`kopia/kopia@master:snapshot/policy/retention_policy.go` [码] `RetentionReasons []string`（「为什么保留/生效」被显式记录并可展示）。
- **成本/风险**｜M｜风险：200 条决策 JSON 约 40 KB，每设备每天同步几次就会顶掉 DataStore 的 apply 频率；必须只存摘要 + 采样，或按 run 保留最近 3 次。
- **优先级**｜P1

### 7.13 同步失败的逐条原因

- **现状**｜部分：Bangumi 侧只有计数器（`failedOps++`、`failed++`，`BangumiSyncManager.kt:126-139`），`uploadTo` 失败只返回 false（200-216 行把 HTTP 码吞成布尔）；WebDAV 侧更粗——`downloadLocked`/`uploadLocked` 整个 try/catch 包住，异常转成 `"下载失败: ${e.message}"`；更糟的是**批量写库在一个大事务里**（`SyncManager.kt:178-237`），任何一行违反约束都会整批回滚，错误信息只剩 `"数据写入失败: …"`。另外 `WebDavClient.get` 在 401/500/超时时都返回 null，于是「远程无同步数据」被当成成功（`SyncManager.kt:137-138`）。
- **提升**｜(a) 用户可见：失败列表「3 条因 401 登录失效、1 条因评分超出 0–10、其余成功」，每条可单独重试；(b) 工程上把「一条坏行」从「整批失败」降级为「跳过该条」，网络错误与「远端为空」也能区分开。
- **原理**｜三件事：①`WebDavClient.get` 改成返回 `SealedResult.Ok(body, etag) | NotFound | Error(code, msg)`，不再用 null 表达三种语义；②批量写入改为**分段事务**（每 100 行一个 done，或逐表独立事务），单行失败 `continue` 并收集；③失败模型 `FailureItem(entityKind, entityKey, titleSnapshot, kind: NETWORK|AUTH|HTTP_4XX|HTTP_5XX|PARSE|FK|CONSTRAINT|UNKNOWN, message, attempt, at)`，与 7.12 的 `SyncRunSummary` 一起持久化，UI 按 `kind` 分组显示。
- **效果**｜同步失败时 sheet 变成「同步未完成 · 已上传 5 · 失败 3」，失败分组标题「登录失效（3）」下列出条目名；每条右侧「重试」，只重跑该条（配合 7.16）；网络恢复后再点「重试全部失败项」。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/import_job.py` [码] `ImportItem.fail_reason` + `ImportJob.pending_items`（失败项是一等数据，不是计数器）；`IgnisDa/ryot@main:crates/models/database/src/import_report.rs` [码] `was_success/details/source_result`（一次导入/同步的成功与否与明细同表）。
- **成本/风险**｜M｜风险：分段事务会让「部分成功」成为常态，必须保证 `clearAll + insertAll` 的写法被彻底改造（现在 download 是先 `clearAll()` 再插入——一旦中途失败且不在同一事务，会得到**空库**）。这是本条最大的坑。
- **优先级**｜P1
### 7.14 备份兼容性提示

- **现状**｜部分：只做了版本闸——`version < 1 || version > EXPORT_VERSION(4)` 时报「不支持的备份版本: N」（`BackupManager.kt:185-188`）；v2 及更早的备份**不恢复设置**（317 行有注释说明「直接恢复会把未备份字段清成默认值」），v3→v4 新增 `libraryFolders` 但旧备份缺该键时解析成空列表（780-782 行注释）。这些规则都埋在代码注释里，**用户看不到**；也没有「本备份来自 v2，以下内容不会恢复」的提示。
- **提升**｜(a) 用户可见：导入前弹「该备份来自 v2（appVersion 1.0.x）：设置不会恢复；分区 / 单集评分 / 外部绑定将在导入后为空」，并给出「仍然导入 / 取消」；(b) 工程上把版本兼容规则变成一张声明式表，新增 v5 时不会有人忘记写提示。
- **原理**｜`BackupVersionMatrix`：`mapOf(1 to FieldSet(...), 2 to ..., 3 to ..., 4 to ...)`，每个版本声明「本版本有哪些块」。导入时 `missing = currentFields - backupVersionFields`，渲染成人类可读的缺失清单（`settings → 设置`、`libraryFolders → 作品库分区`）。同时保留**未知键**：把 `root` 里当前代码不认识的 top-level key 收进 `unknownSections`，写回时原样带回（见下条参考）——这样「用新版备份、旧版 App 导入、再导出」不会把新字段永久丢掉。
- **效果**｜导入弹层多一段「兼容性」：绿色行「设置：可恢复」，灰色行「作品库分区：该备份不含」；未知字段显示「本版本不识别 2 个区块，将原样保留」；导入完成后提示「如需使用分区，请升级到 v1.2.0+」。
- **参考**｜`actualbudget/actual@master:packages/loot-core/src/server/sync/index.ts` [码] `messages_pending`：不认识的 schema 消息**不丢弃**，按 cell 去重保留最新值，等迁移后再 `replayPendingMessages`；`mihonapp/mihon@main:.../data/backup/models/Backup.kt` [码] `@ProtoNumber(101/104/105/106)` 的字段编号策略（新增字段永远加编号，旧解析器忽略）。
- **成本/风险**｜S｜风险：`version >= 3` 才恢复设置这条规则如果被改成「能恢复就恢复」，会把 v2 备份里缺失的字段清成默认值——必须保留白名单而不是黑名单。
- **优先级**｜P1

### 7.15 定时/触发式备份与保留策略

- **现状**｜部分：**自动同步**有（`NirikoApplication.runAutoSyncIfEnabled`，回前台且 `webDavAutoSync` 打开时 download→upload），但**自动本地备份没有**：`exportToJson` 只能由用户手动从设置页触发、写到一个用户选的 SAF 位置；没有任何轮换/保留/去重。
- **提升**｜(a) 用户可见：设置「自动备份：每天一次，保留最近 7 天 + 每周 1 份」，备份列表显示每份的时间/大小/「保留原因：最新」；(b) 工程上用户不必再记得手动导出，且不会被 30 份同名备份淹没。
- **原理**｜两层：①**触发**——`WorkManager` 周期任务（`PeriodicWorkRequest 1d`，约束 `requiresBatteryNotLow`/`requiresDeviceIdle` 不强制，参考 `WallpaperRotationScheduler` 的写法），或「距上次备份 > 24h 且 App 回到前台」这一轻量判据（复用 `AppForegroundSignals`）；②**保留**——kopia 的保留策略形状：`keepLatest/kDaily/kWeekly/kMonthly` 计数 + 从「最近一份成功备份」往前算 cutoff，逐份算 `RetentionReasons`（`["latest"]`、`["daily"]`、`["weekly"]`、`[]` 表示可删）；`ignoreIdentical` 用 payload 的 SHA-256 与上一份比较，相同则不新建。
- **效果**｜备份列表每行右侧显示「最新」「每周」徽标，可删的行有删除按钮；打开开关后第一次立即备份一次（不让用户等一天）；存储不足时保留最新 1 份并提示「空间不足，已只保留最新备份」。
- **参考**｜`kopia/kopia@master:snapshot/policy/retention_policy.go` [码] `RetentionPolicy{KeepLatest,KeepHourly,KeepDaily,KeepWeekly,KeepMonthly,KeepAnnual,IgnoreIdenticalSnapshots}`、`ComputeRetentionReasons`、`cutoffTime`（以 `maxCompleteStartTime` 为基准向前算窗口）。
- **成本/风险**｜M｜风险：Android 的周期任务最小间隔 15 分钟、且在 Doze 下会漂——文案只能写「大约每天」，不能承诺时点；另外本地备份与 WebDAV 上传会同时读全库，需错峰（复用 `AsyncSerialQueue` 或 `SyncManager.isBusy`）。
- **优先级**｜P1

### 7.16 第三方导入的失败重试

- **现状**｜部分：导入器有计数但**不持久化**——`SteamLibraryImporter` 返回 `skippedExisting/skippedNotMatched`（`data/remote/steam/SteamLibraryImporter.kt:24-25`），B站导入有 `failed` 计数，Kazumi 导入同理；导入一旦结束，失败的条目就再也不知道是哪些，只能整批重来。`BackupManager` 的导入也没有「只重试失败项」的入口。
- **提升**｜(a) 用户可见：导入结果页「成功 300 · 失败 12」下有「重试这 12 条」；(b) 工程上把「一次导入」变成可恢复的任务，网络抖动不用整批重来（B站导入每条都要一次评分请求，重来成本极高）。
- **原理**｜两张表（或一张 + JSON 列）：`import_run(id, source, startedAt, finishedAt, status[PENDING|ACTIVE|DONE|STOPPED], total, success, failed)` 与 `import_row(runId, index, payloadJson, normalizedJson, subjectId?, failReason?, attempt, updatedAt)`。导入流程改为「先全量落 `import_row`（status=pending）→ 逐行处理 → 成功标 done 并把 `subjectId` 写回、失败写 `failReason` 并 `attempt++`」。重试 = 筛 `failReason != null && attempt < 3` 重跑。跑完 `pending == 0` 时把 run 标 DONE。
- **效果**｜导入页底部出现「上次导入（B站）：300 成功 / 12 失败 · 重试失败项」；重试只处理 12 条、进度条从 0/12 开始；三次仍失败的条目在详情里显示原因「该条目在 B站已下架」。
- **参考**｜`bookwyrm-social/bookwyrm@main:bookwyrm/models/import_job.py` [码] `ImportJob.status(pending/active/complete/stopped)` + `retry: Boolean` + `pending_items` + `ImportItem.fail_reason/task_id`；`crates/services/importer/src/job_operations.rs`（ryot）[码] `deploy_import_job` 失败时删 report 避免「永远排队」。
- **成本/风险**｜M｜风险：新增表要进备份（否则换设备后「上次导入」消失）——但导入任务数据其实是**临时**的，建议**不进备份**并在 7 天后自动清，避免无谓膨胀。
- **优先级**｜P2

## 7.9 本域新增候选（发散）

### 7.17 乐观并发（ETag / If-Match + 上传基线）

- **现状**｜无 · 新提出：`WebDavClient` 只有 `mkcol/put/get/delete/exists` 五个方法，`put` 是**裸 PUT**（`data/sync/WebDavClient.kt:35-40`），既不读远端也不带 `If-Match`；`SyncManager.uploadLocked` 直接用本地全量数据构建 JSON 后 PUT（`SyncManager.kt:88-127`）。设置页「上传」按钮走的就是这条路径——**A 机上传会静默覆盖 B 机此前的写入**。自动同步虽然是 download→upload，但两者之间仍有窗口。
- **提升**｜(a) 用户可见：手动上传时若云端已变，弹「云端在你上次同步后已更新（另一台设备 · 2 小时前）· 先合并再上传」而不是无声覆盖；(b) 工程上把「丢数据」从概率事件变成可检测的 412，并给出恢复路径——这是纯 WebDAV 方案里唯一能拿到的并发控制。
- **原理**｜本地保存基线：`syncBaseline = { etag: String?, payloadHash: String, syncTime: Long, deviceId: String }`（DataStore 一个 JSON key）。流程：`download` 时从响应头取 `ETag` 并存基线；`upload` 前若基线有 etag 则发 `If-Match: <etag>`；服务器返回 **412 Precondition Failed** → 自动执行一次「download → merge → upload」（只重试一次，防止死循环）；第二次仍 412 → 中止并抛 `SyncConflict`，UI 进 1.4 的 diff 页。服务器不支持 ETag（部分轻量实现）时退化为：先用 `HEAD` 取 `Content-Length + Last-Modified`，与基线比对，不一致就提示。
- **效果**｜手动点「上传」时若云端未变 → 正常上传 + toast「已上传」；云端已变 → 弹窗三选一「先合并（推荐）/ 覆盖云端 / 取消」；自动同步遇到 412 会自己合并一次并在诊断页留一条「检测到并发写入，已自动合并」。
- **参考**｜`actualbudget/actual@master:packages/crdt/src/crdt/timestamp.ts` [码] `Timestamp`（HULC：`millis-counter-node` 的 46 字符串 + `maxDrift 5min`，解决「两台设备时钟不一致时谁更新」）；`IgnisDa/ryot@main:crates/models/database/src/collection.rs` [码] `last_updated_on`（但只有时间戳并不能防覆盖，所以必须配 ETag）。HTTP 语义（412/If-Match）为 **`[推断]`**：协议事实，未从上述仓库取证。
- **为什么是 Niriko**｜纯客户端 + 自建 WebDAV 没有服务端锁，`If-Match`/ETag 是唯一可用的并发原语；而「上传」按钮就在设置页一级入口，误覆盖概率高于有服务端的产品。
- **成本/风险**｜M｜风险：WebDAV 服务器实现差异大（Nextcloud 支持 ETag，坚果云部分接口不稳定），必须有「无 ETag 时的降级路径」；`WebDavClient.get` 现在只返回 body，要改成能拿响应头（会波及 `SyncManager` 的调用点）。
- **优先级**｜**P0**

### 7.18 同步日记（可回溯的运行记录）

- **现状**｜无 · 新提出：只有一个「最近一次」状态——`RefreshCoordinator` 的 `FreshnessSnapshot`（`lastSuccessAt/failureCount/backoffUntil/lastError`）+ `SyncResult.message`。没有「上周三那次同步为什么失败」「昨天到底上传了多少条」这类历史。
- **提升**｜(a) 用户可见：诊断页多一个「同步记录」时间线（最近 20 次：时间、方向、结果、条数、耗时）；(b) 工程上把 7.12/7.13/7.15 的产物集中在一处，排障时不用再让用户复现。
- **原理**｜DataStore 一个 ring buffer key：`sync.journal.v1 = [{at, kind: WEBDAV_MERGE|WEBDAV_UPLOAD|BANGUMI|LOCAL_BACKUP, ok, durationMs, counts: {localWins, remoteWins, inserted, skipped}, failures: [{kind, count}], bytes}]`，写入时机在每次 run 结束（成功/失败都写），超过 20 条丢最旧的。UI 是一条 `LazyColumn` 时间线，失败项展开显示 7.13 的 `FailureItem` 前 5 条。**不写 Room**：这类数据生命周期短、不需要迁移与备份。
- **效果**｜诊断页出现「同步记录（20）」：绿色「今天 14:32 · WebDAV 合并 · 312 条 · 1.8s」、红色「昨天 09:10 · Bangumi · 失败（401）▸」；点条目可「重试这一次」。
- **参考**｜`IgnisDa/ryot@main:crates/models/database/src/import_report.rs` [码]（`started_on/finished_on/was_success/details` 就是一次运行的可查询记录，`user_import_reports` 按 `StartedOn` 倒序返回）；本项目 `data/refresh/RefreshStatusLabels.kt` [码] `formatAgo/describe`（现成的「人话化」文案函数，可直接复用）。
- **为什么是 Niriko**｜排障入口已经收在「刷新诊断」一页（`RefreshCoordinator` 的 snapshots），同步日记放同一页可零新导航补齐「历史」这一维；也避免为日志引入 Room 表与迁移。
- **成本/风险**｜S｜风险：诊断页已经有「资源状态」列表，两个列表容易混淆——需要明确分组标题（「资源新鲜度」vs「同步记录」）。
- **优先级**｜P1

### 7.19 cell 级 CRDT（逐字段合并）

- **现状**｜无 · 新提出：合并粒度是**整行**——`mergeCollections` 按 `subjectId` 取 `updateTime` 较大的一整条（`SyncManager.kt:267-280`）。于是「A 机改了评分、B 机改了进度」这种最常见的情形，**必有一边的修改被整条丢弃**。
- **提升**｜(a) 用户可见：两台设备各自改同一条目的不同字段，同步后两边修改都在；(b) 工程上把「谁更新」从行级降到字段级，同时不再依赖 `updateTime` 是否被正确 bump（1.3 里发现的那个坑会自动消失）。
- **原理**｜仿 actual 的三元组：表 `sync_cell(table, rowKey, column, value, hlc, deviceId, PRIMARY KEY(table,rowKey,column))`，每条只存**变过的字段**（未变不写，天然压缩）。写入用 HLC：`hlc = "%s-%04X-%s"`（ISO 毫秒、4 位十六进制计数、16 位设备 id），本地单调递增、收到远端时用 `max(本地, 远端+1)` 抬升（`maxDrift` 5 分钟容忍时钟回拨）。合并 = 对每个 `(rowKey,column)` 取 `hlc` 最大者；数值型可再叠加 1.3 的语义规则（`watchedEpisodes` 取 max 而非 hlc，因为进度是单调量）。落库时把 `sync_cell` 折叠成 `collections` 行（物化视图）。
- **效果**｜A 机把评分 7→9（14:00）、B 机把进度 6→8（14:01）→ 同步后评分 9 且进度 8；同一字段两边都改 → 取设备时钟更晚的一侧并在 7.12 里显示「另一侧值 8 被覆盖」；UI 上没有任何额外操作。
- **参考**｜`actualbudget/actual@master:packages/loot-core/src/server/sync/index.ts` [码] `messages_crdt(timestamp, dataset, row, column, value)` + `merkle.diff`（只传差异）；`packages/crdt/src/crdt/timestamp.ts` [码] `Timestamp.send()/recv()`、`MAX_COUNTER 0xFFFF`、`maxDrift`。
- **为什么是 Niriko**｜Niriko 是典型的单人双设备（手机 + 平板/备用机），冲突几乎总是「不同字段各改一半」，行级 LWW 在这里损失率最高；而 `collections` 只有十几列、几千行，是做 cell 级 CRDT 成本最低的一张表。
- **成本/风险**｜L｜风险：这是本域最重的一条——需要新表 + 迁移 + 所有写路径改造 + 物化视图重建。建议**先只覆盖 `collections` 一张表**（行数量级最小、冲突最常见），其余表继续走 7.11 的字段级 LWW。
- **优先级**｜P1

### 7.20 备份去重与完整性校验

- **现状**｜无 · 新提出：`exportToJson` 每次都重新生成整包，没有任何指纹；导入时只查 `version` 与结构（`root["collections"]` 能否解析），**无法发现文件被截断或篡改**——一个写到一半的 JSON 可能解析出「0 条收藏」并被当成合法空备份（`parseCollections` 对 `null` 返回 `emptyList`）。
- **提升**｜(a) 用户可见：导入损坏文件时明确提示「文件不完整（校验失败）」而不是「已恢复 0 条」；自动备份不会把内容相同的文件存第二份；(b) 工程上让「备份可用性」可证明，而不是靠用户运气。
- **原理**｜导出时先构建 `payload`（除 `checksum` 外的全部内容，键按字典序 `prettyPrint` 稳定序列化），算 `sha256(payload)`，再放进 `{"version":4, "checksum":"…", "content": {...}}`（或写成两行 header + body）。导入时：①有 `checksum` → 重算比对；②无 `checksum`（v1–v3 老备份）→ 只做启发式检查「`collections` 键存在且是数组，否则视为不完整」。自动备份用同一 hash 做 `ignoreIdentical`（与上一份相同则跳过并只更新时间戳）。
- **效果**｜导入一个被截断的文件 → 红字「备份文件不完整：校验不通过（可能下载/传输中断），未做任何修改」；正常导入无变化；自动备份列表里不会出现连续两份「今天 14:00 / 14:01」完全相同的内容。
- **参考**｜`kopia/kopia@master:snapshot/policy/retention_policy.go` [码] `IgnoreIdenticalSnapshots`（内容一致不新建快照）；`actualbudget/actual@master:packages/loot-core/src/server/sync/index.ts` [码] `merkle` 哈希（用哈希判断「有没有变」而不是逐字段比）。
- **为什么是 Niriko**｜备份是纯客户端唯一的数据保险，而现在的解析对「空/截断 JSON」会静默得到 0 条（`parseCollections` 对 null 返回 `emptyList`）——在没有服务端兜底的前提下，必须能证明文件完整。
- **成本/风险**｜S｜风险：`checksum` 改变了文件顶层结构，v4→v5 要升版本；旧版 App 读到带 `content` 外层的新文件会直接报「不支持的格式」，所以**必须保持 v4 布局**（把 `checksum` 作为普通字段加进去，旧解析器 `ignoreUnknownKeys` 会忽略它）。
- **优先级**｜P1

### 7.21 导入 dry-run 预检报告

- **现状**｜无 · 新提出：导入是「一键写库」——选文件 → Confirm 弹窗 → 直接 `importFromJson` 进事务。用户看不到「这份备份会新增什么、跳过什么、覆盖什么」，只能事后从 Snackbar 里读一个数字。
- **提升**｜(a) 用户可见：导入前先出预览页「将新增 300 · 已有 12（保留本机）· 冲突 3 · 跳过 2」，可回退；(b) 工程上把 7.2/7.4/7.14 的判定统一到一次「只算不写」的 pass，事故率骤降。
- **原理**｜`ImportPlanner.plan(bytes, localSnapshot): ImportPlan`，与 `importFromJson` **共用同一套解析器**（抽 `BackupCodec`），输出四组：`inserts / existing / conflicts / skipped`，每组是 `(table,key,title)` 列表。UI 只读渲染；用户确认后 `apply(plan)` 才在单事务里写——**plan 与 apply 之间校验本地快照未变**（比对 `COUNT(*)` 与 `MAX(updateTime)`，变了就重算）。
- **效果**｜选文件后进入「导入预览」页，四段可折叠，底部「导入 300 条（保留本机 12 条）」；取消不写任何数据；大备份（10 万条）预览在 1 秒内完成（只解析不信箱）。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupFileValidator.kt` [码]（恢复前先解码一遍并收集 `missingSources/missingTrackers`，是标准「pre-flight」）；`bookwyrm-social/bookwyrm@main:bookwyrm/importers/importer.py` [码] `create_job → create_item`（先建任务与条目，再逐条处理，天然支持预览）。
- **为什么是 Niriko**｜本项目恢复是「一键合并」（`RestoreMergePolicy` 本地优先且不可回滚），一次误操作不可逆；先预览后写能把不可逆变成可取消，符合它「无服务端、错了没处找回」的处境。
- **成本/风险**｜M｜风险：`plan` 与 `apply` 之间用户可能切后台很久，本地快照变化 → 必须重算而不是硬套旧 plan。
- **优先级**｜P1

### 7.22 设备指纹与旧版客户端检测

- **现状**｜无 · 新提出：sync JSON 只有 `version` 与 `syncTime`（`SyncManager.kt:325-326`），没有写入者标识。于是「另一台设备还在用旧版客户端」这件事完全不可见——而那正是 7.11 里 `watchedVolumes` 被抹掉的根因。
- **提升**｜(a) 用户可见：同步页显示「本机：Pixel 8 · v1.2.0 · 今天 14:30」与「另一台：MI 14 · v1.1.3 · 2 天前（旧版，可能丢字段）」；(b) 工程上给「旧客户端写回」这个不可见风险一个可发现入口，也让 7.14 的兼容提示有数据支撑。
- **原理**｜`deviceId` = 首次启动生成的 16 位随机串（DataStore），sync JSON 增加 `writer: { deviceId, appVersion, syncVersion, at }`；上传时把远端已有的 `writers` 数组与自己这条合并（按 `deviceId` 去重、保留最近 5 条、按 `at` 倒序）。同步后 UI 判 `writers.any { it.syncVersion < CURRENT_MIN_COMPAT }` → 显示横幅。**不收集任何硬件标识**（不碰 ANDROID_ID/IMEI），纯随机串。
- **效果**｜同步与备份页顶部多一行「同步设备」：本机绿点 + 另一台灰点（带「旧版」徽标）；发现旧版时横幅「另一台设备（MI 14 · v1.1.3）不支持卷进度同步，请升级后再同步，否则卷进度可能丢失」。
- **参考**｜`actualbudget/actual@master:packages/crdt/src/crdt/timestamp.ts` [码] `makeClientId()`（`uuidv4().replace(/-/g,'').slice(-16)`，纯随机节点 id，不涉及硬件指纹）+ `Timestamp` 里 node 参与排序。
- **为什么是 Niriko**｜多设备共用一份 sync.json 是 WebDAV 方案的固有形态，而 7.11 的字段扩展会让旧版客户端变成「静默破坏者」；设备指纹是唯一能让用户提前知道「那台机器在拖后腿」的手段。
- **成本/风险**｜M｜风险：`writers` 是「谁写过」，不是「谁在线」——两台设备长期不同步时会显示很久以前的时间，文案要写「上次写入」而不是「上次在线」。
- **优先级**｜P2

## 7.A 本域新增参考库

| 仓库 | 许可 | star | 语言 | 为何与本项目相关 | 已验证文件 |
| --- | --- | --- | --- | --- | --- |
| actualbudget/actual | MIT | 29.6K | TypeScript | 列级 CRDT（`messages_crdt`）、冲突消息暂存（`messages_pending`）、HULC 混合逻辑时钟、merkle 差异同步 | `packages/loot-core/src/server/sync/index.ts`、`packages/crdt/src/crdt/timestamp.ts` |
| kopia/kopia | Apache-2.0 | 14K | Go | 快照保留策略（latest/daily/weekly/monthly）与「为什么保留这一份」的原因计算；相同内容不重复建快照 | `snapshot/policy/retention_policy.go` |
| mihonapp/mihon | Apache-2.0 | 24.5K | Kotlin | 备份按类别勾选（`BackupOptions`）、恢复前预检（`BackupFileValidator`）、按 key 合并的待提交队列 | `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupOptions.kt`、`.../data/backup/BackupFileValidator.kt`、`.../domain/track/store/DelayedTrackingStore.kt` |
| IgnisDa/ryot | GPL-3.0 | 3.6K | Rust | 导入任务与报告表（进度/成功/明细）、MAL gzip-XML 导入、流式分类导出 | `crates/models/database/src/import_report.rs`、`crates/services/importer/src/job_operations.rs`、`crates/services/importer/myanimelist/src/lib.rs`、`crates/services/exporter/src/export_operations.rs` |
| bookwyrm-social/bookwyrm | GitHub 未识别 | 2.8K | Python/Django | 导入任务/条目模型（`ImportJob.status` + `ImportItem.fail_reason`，支持停/重试）、CSV 列名猜测、Goodreads 迁移 | `bookwyrm/models/import_job.py`、`bookwyrm/importers/importer.py`、`bookwyrm/importers/goodreads_import.py` |

> star 数据来源 img.shields.io / badgen.net，取数时间 **2026-10**。未取到许可的仓库不写猜测值。

## 7.B 计数自检

- 汇总表行数：**22**（7.1–7.16 共 16 条 v1 条目 + 7.17–7.22 共 6 条新增候选）。
- 详解条目数：**22**（7.1–7.16 全部保留原编号与名称，无重排、无删除）。
- 新增候选：**6** 条（7.17–7.22），全部标注「无 · 新提出」。
- 22 = 22 ✓。
- 跨域合并：1.26 与本域 7.5 是同一功能，已在 1.26 标题注明「合并自 7.5」并把实现写在本文件。
- 本域另有两条**超出 v1 范围、但必须让 Lead 知道**的实测缺陷（不是新功能）：①`WebDavClient.get` 把 401/500/超时统一返回 null，导致「远程无同步数据 = 成功」（`SyncManager.kt:137-138`）；②`updateStatus` 不更新 `updateTime`（`CollectionViewModel.kt:191-199`）会让 LWW 判错新旧。两条分别落在 7.13 与 7.12 的成本/风险里。
