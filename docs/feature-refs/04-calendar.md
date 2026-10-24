# 4 日历、放送与提醒

> **一句话结论**：本域不是"要不要换库"的问题——**日历已经换成 kizitonwose 2.10.1、柱/环图已经换成 Vico 3.2.0**（`gradle/libs.versions.toml:99` / `:97`，`ui/stats/CalendarCard.kt:62-68`、`ui/stats/NirikoBarChart.kt:14-35`），v1 与简报里"日历与图表都是自绘"的前提**已过时**，本轮不要再评估换库，评估**已经换过之后剩下的三件事**：① 数据准确性（4.7 停播、4.10 延期、4.14 时区/新鲜度）——当前 `AiringStatus.getEstimatedEndDate` 用 `airDate + 总集数×7` 外推，没有停播概念；② 聚合能力（4.3 未来七天 / 4.4 今晚看什么 / 4.5 漏看判定）——所需数据（`episodes.airdate` 精确集日期、`watchedEpisodes`）都在库里，只缺一层纯函数；③ 出口（4.8 .ics 导出）——完全空白，`grep -r "BEGIN:VCALENDAR|VEVENT|\.ics" app/src` = 0 命中。
> **本域最值得做的 3 件**：**P0** 4.3 未来七天（`StatsCalculator.computeCalendarEventsForRange` 已能算任意区间，只差一个 `days=7` 入口）、**P0** 4.5+4.4 漏看判定与"今晚看什么"聚合（一个纯函数 + 一个卡片）、**P0** 4.8 .ics（纯字符串生成，零依赖，规则见下）。4.7 停播感知是 P1 但**必须先做**，否则 4.3/4.8 会把停播当更新导出给系统日历。

## 4.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 4.1 | 个人记录 / 放送信息 / 全部 三模式 | 已实现 | — | — | `CalendarMode` 三值 + `StatsViewModel._calendarMode` |
| 4.2 | 当日更新看板 | 部分 | P1 | M | 当日明细在 `CalendarDaySheet`，无独立看板 |
| 4.3 | 未来七天预告 | 无 | **P0** | S | ryot `user_upcoming_calendar_events` + `NextDays(d)` |
| 4.4 | 今日更新 + 继续观看聚合 | 无 | **P0** | S | bandi 首页"今天看什么/看到第几集/下集什么时候" |
| 4.5 | 漏看提醒 | 部分 | **P0** | S | bandi"同作品同时命中时优先漏看" |
| 4.6 | 逐作品提醒开关 | 无 | P2 | M | 全局只有 `airingReminderEnabled` |
| 4.7 | 休息日 / 停播感知 | 部分 | P1 | M | Yamtrack 一集一 `Event`（真实日期优先） |
| 4.8 | 时间表导出（.ics） | 无 | **P0** | S | ical4j `Recur`（RRULE 语义）+ RFC 5545 |
| 4.9 | 季度番表信息补全 | 部分 | P1 | M | `assets/data/onair` 2023S2–2026S3 + `SeasonalFetcher` |
| 4.10 | 停播与延期展示 | 部分 | P1 | M | 无 `delayed` 字段；日历无角标 |
| 4.11 | 每日提醒时间可配置 | 无 | P1 | S | `PeriodicWorkRequest(1, DAYS)` 无可配时刻 |
| 4.12 | 提醒去重与合并 | 已实现 | P2 | S | `AiringNotificationComposer`（MERGED_ID 1001 / 48 字） |
| 4.13 | 观看历史与日历联动 | 部分 | P1 | M | day sheet 只跳详情，无写回 |
| 4.14 | 日历数据新鲜度可见 | 已实现 | P2 | S | `BroadcastMonthState` + `RefreshStatusLabels.formatAgo` |
| 4.15 | 放送时刻的时区换算 | 无 · 新提出 | P0 | S | `airTimeZone="CN"` 只写不读 |
| 4.16 | 日历事件物化表（增量 upsert） | 无 · 新提出 | P2 | L | Yamtrack `Event` 唯一键 + `bulk_update` |
| 4.17 | 日历/看板一键补进度 | 无 · 新提出 | P1 | S | 复用 4.13 的写回通道 |
| 4.18 | 静默时段与逐作品静音 | 无 · 新提出 | P1 | S | 官方约定"不做免打扰"→ 需重新定取舍 |
| 4.19 | 本季星期表矩阵视图 | 无 · 新提出 | P2 | M | bandi 首页"本季星期表" |
| 4.20 | 放送源可信度与多源交叉 | 部分 · 新提出 | P1 | M | 现有"放送时间参考 Bangumi 日历"已诚实标注 |

## 4.1 逐条详解

### 4.1 个人记录 / 放送信息 / 全部 三模式
- **现状**｜已实现｜`data/model/stats/CalendarMode.kt` 三值 `PERSONAL/BROADCAST/ALL`；`ui/stats/CalendarCard.kt:143-148` 的 `CalendarControlRow` 渲染 FilterChip；模式状态在 `viewmodel/StatsViewModel.kt:92` 的 `_calendarMode`，**翻月/翻周不改它**，所以 v1 担心的"历史月份切换后模式丢失"不存在；`StatsCalculator.computeCalendarEvents:171-182/198-293` 按模式决定是否计算个人事件/放送事件。
- **提升**｜无需改造；本轮只需把"模式持久化到 DataStore"，让冷启动回到上次模式（现在是每次进统计页重置为 `PERSONAL`）。
- **原理**｜`_calendarMode: MutableStateFlow<CalendarMode>` 与 `_calendarAnchor: MutableStateFlow<LocalDate>` 是两个正交维度，`uiState` 的 `combine`（StatsViewModel:113-140）把两者一起喂给 `computeStats`；`CalendarRangeCalculator.visibleRange(anchor, expanded)` 只消费 `expanded`，完全不碰 `mode`。要持久化，只需在 `switchCalendarMode` 里写 `settingsDataStore.update { it.copy(calendarMode = mode.name) }`，init 时读回。
- **效果**｜杀进程重进统计页，仍停在"全部显示 · 月视图 · 2026 年 10 月"，不是跳回"个人记录 · 今天"。
- **参考**｜`IgnisDa/ryot@main:crates/services/miscellaneous/calendar/src/lib.rs` [码] `get_calendar_events(..., only_monitored, start_date, end_date, media_limit, deduplicate)`：把"只看在看"当成 bool 入参而非独立模式，比三态更省分支。
- **成本/风险**｜S｜风险：DataStore 里存 enum 名，改枚举名会静默回落默认值——加 `runCatching` + 默认值即可。
- **优先级**｜P2（本身没问题，持久化是顺手）

### 4.2 当日更新看板
- **现状**｜部分｜点日期弹 `ui/stats/CalendarDaySheet.kt:63 CalendarDaySheet(date, events, episodesBySubject, onDismiss, onSubjectClick)`，四段列表"开始观看/看过/放送中/发售日"；`estimateEpisode(date, subject)`（:502）用日期差估算集号。**没有**"今天更新了什么"的独立页面或卡片。
- **提升**｜把"今天"从"点格子后的 sheet"提前到"进页就有结论"：统计页顶部一张卡列出今天更新的在看作品（含"已看到第几集/还差几集"），省掉用户"点今天这一格"的一次交互；工程上复用 `computeCalendarEventsForRange(today, today)`，不新增数据。
- **原理**｜单日事件 = `computeCalendarEventsForRange(items, today, today, CalendarMode.BROADCAST, broadcast, seasonal, episodesBySubject)` 的 `[today]!!.broadcastSubjects`，再与 `watchingIds` 求交；排序键 `(airTimeMinutes ?: Int.MAX_VALUE, ratingScore)`，让 21:30 的在 23:00 之前。冷却：把结果 `remember(items, today, mode)` 即可，因为纯函数无副作用。
- **效果**｜卡片标题"今天 · 10 月 17 日 周五 · 4 部更新"；每行"封面《胆大党》第 3 集 23:30"；空态显示"今天没有在看的作品更新"；网络失败时卡片保留上次结果并在右上一行小字"放送数据 12 分钟前"（复用 `RefreshStatusLabels.formatAgo`）。
- **参考**｜`Ludi-Herry/bandi@main:README.md` [README]："首页只回答三件事：今天看什么、看到第几集、下集什么时候更新"；该仓库 README 自述与 v1 R16 的 `Luis-Herry/anime-tracker-desktop` 同源（改名关系**待核**，见 4.A）。
- **成本/风险**｜M｜风险：与 4.4 重叠——**先做 4.4 的聚合函数，4.2 只做它的一个分区**，否则会出现两套"今日"计算。
- **优先级**｜P1

### 4.3 未来七天预告
- **现状**｜无｜全库无"未来 N 天"入口。能力其实已在：`StatsCalculator.computeCalendarEventsForRange:363` 支持任意闭区间（内部按 `CalendarRangeCalculator.monthsCovering` 逐月算再裁剪，跨月/跨年都对），只是唯一调用方是周/月视图。
- **提升**｜用户周一早上就能看到"这周 9 部更新，其中 3 部在看的"，不用连续翻 7 天格子；工程上**零新请求**——放送数据已有 `BROADCAST_CALENDAR`（30 分钟软 TTL）+ `SEASONAL`（6 小时），未来 7 天必落在当前月或下月，下月的 `seasonal` 由 `ensureSeasonalForAnchor` 现成补拉。
- **原理**｜新增纯函数（放 `data/calculator/UpcomingAiringCalculator.kt`，与 `StatsCalculator` 同样零 Android 依赖）：
  ```kotlin
  data class UpcomingAiring(
      val date: LocalDate, val subject: SubjectEntity,
      val episodeSort: Int?,      // null = 集号未知
      val estimated: Boolean,     // true = 由 weekday 推算，非 episodes.airdate
  )
  fun upcoming(
      items: List<CollectionWithSubject>, today: LocalDate, days: Int = 7,
      broadcast: Map<DayOfWeek, List<AiringSubject>>,
      seasonal: Map<String, List<AiringSubject>>,
      episodesBySubject: Map<Long, List<EpisodeInfo>>,
      watchingOnly: Boolean = true,
  ): List<UpcomingAiring>
  ```
  复用 `computeCalendarEventsForRange(items, today, today.plusDays(days - 1), BROADCAST, ...)` 得到 `Map<LocalDate, CalendarDayEvents>`，再按 `date` 升序摊平；`estimated` 由"该 subject 是否命中 `actualSchedules[..].second == true`"决定（该判断在 `computeCalendarEvents:210-212` 已经存在，抽成 `isScheduleActual(subjectId, episodes, totalEpisodes)` 供两处共用）。边界：`days` 跨月时 `monthsCovering` 返回 2 个月；跨年由 `LocalDate.plusDays` 天然正确，**不要**用 7×24 小时毫秒。
- **效果**｜统计页新增"接下来 7 天"卡（或发现页顶部横向 7 列）：今天/明天/周几 + 每格 1–3 个封面，超出显示"+3"；有精确集日期的行不显示"约"，只有靠 weekday 推算的行右上角带一个灰色"约"字；今天为空时整卡不渲染（不是渲染一个空卡）。
- **参考**｜`IgnisDa/ryot@main:crates/services/miscellaneous/calendar/src/lib.rs` [码] `user_upcoming_calendar_events`：入参 `UserUpcomingCalendarEventInput::NextDays(d)` 或 `NextMedia(l)`（**两种模式：按天数、按条数**），并把 `deduplicate_media` 透传给 SQL 的 `distinct_on(metadata_id)`；日期边界用 `start_date.checked_add_days(Days::new(d))` 而非毫秒运算。
- **成本/风险**｜S｜风险：`seasonal` 只保留 `MAX_SEASONAL_MONTHS = 24` 个月（StatsViewModel:445），翻到很远的未来月份会"看起来没数据"——UI 必须区分"没有更新"与"该月未加载"（`BroadcastMonthState.status == LOADING`），否则用户以为这周没番。
- **优先级**｜**P0**

### 4.4 今日更新 + 继续观看聚合
- **现状**｜无｜今日更新可算（4.2），"落后多少集"目前只在详情页/统计页放送条目里显示（`StatsCalculator.computeEpisodeAirState:457` 的 `airedCount/totalCount`），没有任何地方把两者合成一个"今晚看什么"。
- **提升**｜把"打开 App → 决定看什么"压缩成 1 次扫视：一个"今晚"卡片 = 今日更新 ∩ 在看 ∪ 落后 ≥1 集。工程上省掉用户 3 次导航（统计→日历→点日期），也让 4.5 的判定有唯一实现点（避免 Worker 与 UI 各写一套阈值）。
- **原理**｜一个聚合器 + 固定排序（这是本域最容易被写成两套的地方，必须先定）：
  ```kotlin
  enum class TonightReason { BEHIND, AIRING_TODAY }
  data class TonightItem(val cws: CollectionWithSubject, val reason: TonightReason,
                         val behind: Int, val nextEp: Int?, val airAtMinutes: Int?)
  // 排序：BEHIND 在前（按 behind 降序），其次 AIRING_TODAY（按 airTime 升序），同组按 ratingScore 降序
  ```
  `behind = airedCount - (collection.watchedEpisodes ?: 0)`，其中 `airedCount` 来自 `computeEpisodeAirState(...).airedCount`（内部已处理 `status == "Air" || "Today"` 与 `airdate <= today` 两条路）；**同一作品同时命中时只保留 BEHIND 一条**（去重键 `subjectId`）。卡片上限 8 条，其余收进"还有 N 部"。
- **效果**｜卡片标题"今晚看什么"；漏看行走在最前："《X》落后 3 集（已播 8 / 已看 5）"、右端一个"补到第 8 集"按钮；今日更新行显示"23:30 放送"；全空时显示"今晚没有要追的，明天见"而不是空白。
- **参考**｜`Ludi-Herry/bandi@main:README.md` [README]：明确"同一作品同时触发'今日更新'和'漏看'时，应用会优先提醒漏看，避免你直接跳过中间集数"——这条规则直接照抄，别自己发明排序。
- **成本/风险**｜S｜风险：`episodes` 未预取时 `airedCount` 会退化成 0（`prefetchEpisodes` 单条 3 秒超时会静默失败），于是"落后"全变 0；必须让聚合器返回 `dataQuality: EXACT | ESTIMATED` 并在 UI 上降级成"约"。
- **优先级**｜**P0**

### 4.5 漏看提醒
- **现状**｜部分｜提醒是**事件型**：`data/notification/AiringReminderWorker.kt` 只在"今天是放送日且在看的条目"上发通知（网络 `getCalendar()` 优先，失败回退 `localAirsToday`），**没有**"已播 N 集 − 本地进度 > 0"的落后判定；`CollectionEntity` 也没有 `lastProgressAt`（只有 `updateTime`，评分/备注也会改它）。
- **提升**｜把"有更新就推"改成"你落后了才推"：通知量下降（多部同时更新时从 N 条变 1 条已实现，但"没在看当天的番也推"这条路还在），且推送里带"落后几集"这一可操作信息；工程上漏看判定沉成纯函数后，Worker 与 4.4/4.2 卡片共用一份逻辑，省掉第二个阈值实现。
- **原理**｜`data/calculator/CatchUpCalculator.kt`：
  ```kotlin
  data class Lag(val subjectId: Long, val aired: Int, val watched: Int,
                 val behind: Int, val lastAirDate: LocalDate?, val quality: Quality)
  fun lagOf(cws: CollectionWithSubject, episodes: List<EpisodeInfo>, today: LocalDate): Lag?
  // 门槛：status != WATCHING -> null；behind <= 0 -> null；finishDate != null -> null
  ```
  `aired` 用 `computeEpisodeAirState(episodes, subject.totalEpisodes, today).airedCount`；无 episodes 数据时退化为 `estimatedAired(subject, today)`（`(today - airDate).days / 7 + 1` 夹在 `0..totalEpisodes`）并标 `ESTIMATED`。Worker 的新分支：先算 `lags`，**若 `lags` 非空则只推漏看摘要**（"《A》落后 3 集、《B》落后 1 集"），否则保留现有"今日更新"路径；两条路径都要过 `AiringNotificationComposer.compose`（复用 48 字上限与合并 id）。
- **效果**｜周日一条通知："3 部落后了：《A》落后 3 集、《B》等 2 部落后 1 集"，点击落到"在看 + 落后"过滤后的列表；没有落后时周日不发任何通知（现在的行为是"今天有番就发"）。
- **参考**｜`Ludi-Herry/bandi@main:README.md` [README]（漏看优先规则）；`IgnisDa/ryot@main:crates/services/statistics/src/daily_activity_operations.rs` [码] 说明"按天聚合"能避免每次重扫全表——漏看判定同理，宜每自然日算一次并缓存。
- **成本/风险**｜S｜风险：`watchedEpisodes` 对"抛弃后重看"会是 0 而 `aired` 很大 → 若只按数值判会推"落后 12 集"的骚扰；必须叠加 `AiringStatus.getPhase == AIRING` 且 `startDate` 在 90 天内。
- **优先级**｜**P0**

### 4.6 逐作品提醒开关
- **现状**｜无｜`data/settings/AppSettings.kt:143` 只有全局 `airingReminderEnabled`（默认 false），`SettingsDataStore.kt:200/682` 一读一写；`CollectionEntity` 无 `remindEnabled` 列，`AiringReminderWorker` 的过滤条件只有 `status == WATCHING`。
- **提升**｜用户能对"只想安静追"的作品关掉提醒（现状只能全关→连想追的也一起没）；工程上把"哪些作品要提醒"从隐式（status）变显式（字段），Worker 的 `watchingIds` 与 4.5 的 `lags` 都读同一份集合，减少一处特判。
- **原理**｜迁移 v33：`ALTER TABLE collections ADD COLUMN remindEnabled INTEGER NOT NULL DEFAULT 1`（Room `@ColumnInfo(defaultValue = "1")`，注意 `app/schemas/` 需提交）。Worker 内改为 `watching.filter { it.remindEnabled }`；UI 放在详情页"在看"状态下的一个 Switch（不放到设置页，避免找不到）。默认值取 1 而不是 0：新收藏默认提醒，符合现有行为。
- **效果**｜详情页"追番提醒"一行开关；关掉后 4.5/4.2 的通知都不含它，但日历格子仍显示放送点（提醒是提醒，数据是数据）。
- **参考**｜无对口源码（ryot 的 `notification_platform` 是服务端渠道配置，粒度不同，不适用）；本条主要是本项目自己的数据模型补齐。
- **成本/风险**｜M｜风险：Room 显式迁移 v2→v32 已很长，再加一列要同时改 `NirikoDatabase.kt` 的 `MIGRATION_32_33` 与迁移测试；漏了会在升级用户上崩。
- **优先级**｜P2

### 4.7 休息日 / 停播感知
- **现状**｜部分｜有"精确优先"的雏形：`computeCalendarEvents:201-213` 在 `episodes.airdate` **完整覆盖声明集数**时用真实日期（`actualSchedules[id].second == true`），否则回退到 `broadcast[dow]` 的星期外推；但外推的区间上界是 `airing.estimatedEndDate ?: airing.airDate.plusMonths(3)`（:225-227），`AiringStatus.getEstimatedEndDate` 则是 `airDate + totalEpisodes*7`（`util/AiringStatus.kt:53-61`）——**两者都没有"停播周"概念**，停播时会把那一周照样标成放送。
- **提升**｜停播周不误报：日历上那一格不再有金色菱形，.ics（4.8）不会把错日期写进系统日历，通知不会在停播的周五响；工程上省掉"用户发现错了 → 手动忽略"的隐性成本，也让 `estimatedEndDate` 的 3 个月魔数有据可依。
- **原理**｜把"缺口即停播"显式化。输入是 `episodes` 里本篇（`type == 0 && sort > 0`）的 `airdate` 升序序列；判定 `gapDays = ChronoUnit.DAYS.between(ep[i].airdate, ep[i+1].airdate)`，`gapDays > 10` 时该区间记一条 `Hiatus(subjectId, fromDate, toDate)`。渲染时：某日 `d` 有 `broadcast[dow]` 命中但在 `Haitus` 区间内 → **不加入** `broadcastByDate`。完全没有 episodes 数据时**不估算提醒**（与 v1 建议一致），日历仍按现有星期外推显示，但 `CalendarDayEvents` 增 `isEstimatedBroadcast: Boolean`，UI 用空心菱形区分。
- **效果**｜停播那周的格子只显示灰色空心点（"本应放送，已停播"），长按 tooltip"因特番停播一周"；恢复放送后自动回到实心。
- **参考**｜`FuzzyGrim/Yamtrack@0.26.3:src/events/calendar/main.py` [码] `fetch_releases → save_events → cleanup_invalid_events`：一集一个 `Event`（唯一键 `(item_id, content_number)`），**用真实集日期 upsert，并把不再有效的 Event 删掉**——"停播/改期"在数据层表现为 update + cleanup，而不是渲染期修修补补。
- **成本/风险**｜M｜风险：`episodes` 预取有 60 条上限 + `Semaphore(2)` + 3 秒超时（StatsViewModel:367-387），缺口算法对"缺了中间几集"和"停播"无法区分——必须只在 `complete == true` 时启用缺口判定，否则误报停播。
- **优先级**｜P1（但 4.8 依赖它）

### 4.8 时间表导出（.ics）
- **现状**｜无｜`grep -r "BEGIN:VCALENDAR\|VEVENT\|\.ics" app/src` 零命中；没有 FileProvider 导出日历的路径，也没有任何 iCalendar 相关依赖。
- **提升**｜把追番表推进系统日历（Google Calendar / 第三方日历 App 都能订阅或导入），用户能在"日历 App 里和会议一起看"；工程上**零新依赖**（纯字符串拼接 + 现有 `FileProvider`），且导出后可验证（Google Calendar 导入会校验 RRULE/UID，等于免费测试）。
- **原理**｜新增纯函数 `data/calculator/IcsBuilder.kt`，输入 `List<IcsEvent>`、`Instant`、`ZoneId`，输出 `String`。**具体生成规则（照此实现即可）**：
  1. **时区**：本项目 `OnAirRepository` 注释写明 `timeCN` 是"中国时区"、`airTimeZone = "CN"`（`data/onair/OnAirRepository.kt:20-35,49`），因此固定 `DTSTART;TZID=Asia/Shanghai:YYYYMMDDTHHMMSS`，并**内联一个 `VTIMEZONE`**（Outlook 必需；Google/Apple 可省，但加上无害）：`TZID:Asia/Shanghai` + `BEGIN:STANDARD / TZOFFSETFROM:+0800 / TZOFFSETTO:+0800 / DTSTART:19700101T000000`（中国 1991 年后无夏令时，只需一段 STANDARD）。
  2. **UID（幂等键，最重要）**：逐集事件 `niriko-{subjectId}-ep{sort}@niriko.local`；整季 RRULE 事件 `niriko-{subjectId}@niriko.local`。同一 UID 重复导入时日历端会更新而不是新增——这是"导两次不会变成两份"的唯一保障。
  3. **RRULE**：仅在"估算模式"（4.7 的 `complete == false`）使用 `RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=N`，`N = (totalEpisodes ?: 12) - 已播集数`，夹 `1..200`；**不用 `UNTIL`**（RFC 5545 要求 UTC 形式，且停播会让它落到错误日期）。停播/集日期完整时改为一集一个无 RRULE 的 VEVENT。
  4. **DTSTAMP / SEQUENCE**：`DTSTAMP:{导出时刻 UTC}`；`SEQUENCE = (subject.updateTime / 1000).toInt()`（单调递增，改时刻后日历端才会覆盖）。
  5. **其余**：`SUMMARY:《{标题}》第 {n} 集`；`DURATION:PT{durationSeconds/60 ?: 24}M`；`TRANSP:TRANSPARENT`（不占忙闲）；`URL:https://bgm.tv/subject/{id}`；估算行 SUMMARY 后缀"（预估）"。
  6. **转义/折行**：`\` → `\\`、`;` → `\;`、`,` → `\,`、换行 → `\n`；每行 ```` 75 octets ```` 折行（CRLF + 一个空格）。
  7. **范围与出口**：导出 `status ∈ {WATCHING, PLAN_TO_WATCH} && !isPrivate` 且日期落在 `[today, today+90d]`；写 `cacheDir/niriko-airing-YYYYMMDD.ics`，`FileProvider.getUriForFile` + `ACTION_SEND` + `FLAG_GRANT_READ_URI_PERMISSION`（不申请存储权限）。
- **效果**｜设置→"数据源与账号"下新增"导出放送日历（.ics）"；点击后先弹范围内计数"将导出 12 部 / 138 个事件（其中 41 个为预估）"，确认后走系统分享；导入 Google Calendar 后每周同一时刻出现"《X》第 3 集 23:30-23:54"。
- **参考**｜`ical4j/ical4j@master:src/main/java/net/fortuna/ical4j/model/Recur.java` [码]（BSD-3，头部许可文本已读）：`net.fortuna.ical4j.transform.recurrence` 包把 RRULE 拆成 `ByDay/ByMonthDay/BySetPos` 等 transform——**我们不需要引依赖，但必须按它对 RRULE 的解释来生成**（尤其 `COUNT` 与 `INTERVAL` 的组合语义）。`FuzzyGrim/Yamtrack@0.26.3:src/events/calendar/__init__.py` [码]（目录确认）表明"一集一事件 + 唯一键"是可持续做法。
- **成本/风险**｜S｜风险：`airTimeZone` 只写不读（见 4.15），若 `timeCN` 实际是 JST 而未 +1 小时，导出的全部事件会早 1 小时——**上线前抽查 3 部番与 Bangumi 页面比对**，并在设置里留一个"放送时刻基准"说明。
- **优先级**｜**P0**

### 4.9 季度番表信息补全
- **现状**｜部分｜两级来源：静态 `app/src/main/assets/data/onair/*.json`（**实测 14 个季度文件：2023S2 … 2026S3**，字段 `weekDayCN` + `timeCN`，如 `{"323651":{"weekDayCN":1,"timeCN":"2100"}}`）+ 动态 `SeasonalFetcher`（`StatsViewModel.ensureSeasonalDataForMonth` 拉 `[目标月初 −6 个月, 目标月末)` 开播的番）。`OnAirRepository.enrich` 只在空缺时补（`?: p.totalMinutes`），不覆盖已有值。缺：更早季度（<2023S2）无静态数据；`airTimeZone` 存了不用。
- **提升**｜把"季度番表"从"日历的副产物"变成可浏览的一页：用户能看"2026 秋有哪些番、几点播、多少话"，而不是只能靠日历反推；工程上让静态数据与 API 数据的**职责边界**明确（静态补精确时刻，API 补集数与封面），减少 `computeCalendarEvents` 里 `coverFallbackMap`/`episodeFallbackMap` 这类补丁式回填。
- **原理**｜新增 `SeasonalTableRepository`：内存 `Map<String /*yyyy-S*/, List<AiringSubject>>`，来源优先级 静态 onair → `SeasonalFetcher` 结果；`enrich` 顺序改为"先 API 后静态补时刻"（当前是静态先、API 后，会让静态的旧记录挡住 API 的新集数）；季度键统一由 `AiringStatus.getCurrentSeasonRange` 的四季划分（1/4/7/10 月边界）派生，**不要**再造第三套季度定义。
- **效果**｜发现页或统计页新增"本季番表"入口：按周一~周日分组，每行"封面 标题 21:30 · 12 话 · TV"；缺失数据行显示"放送时刻待补"，不显示 00:00 这种假数据。
- **参考**｜`Ludi-Herry/bandi@main:README.md` [README]：以"长门番堂"为档期与播出情报主源、Bangumi 补详情，并用四档期 + 年/类型/题材筛选；本项目受"不做自建服务端"约束，只能继续用 Bangumi + 静态资产，**但"档期筛选 + 缺数据显式标注"这两个 UI 结论可以照搬**。
- **成本/风险**｜M｜风险：静态 `onair` 是提交进仓库的资产，不会自动更新；若把它当权威，2027 年后新番全无时刻——必须保留"API 优先、静态仅补空"的顺序（现顺序相反）。
- **优先级**｜P1

### 4.10 停播与延期展示
- **现状**｜部分｜`SubjectEntity` 有 `airDate`、`airWeekday`、`airTimeMinutes`（`data/local/entity/SubjectEntity.kt:32-34`），**没有** delay/延期字段；日历格子 `buildMarks`（CalendarCard:854）只有 STARTED/COMPLETED/BROADCAST/RELEASE 四类标记，没有"延期"角标；详情页只显示日期文本。
- **提升**｜延期作品在日历上"挪到新日期 + 标角标"，用户不会在某天等一部其实延期的番；工程上把"延期"从散在 UI 的文案变成数据（一次写入，日历/通知/.ics 三处自动一致）。
- **原理**｜新增表 `airing_adjustments(subjectId, fromDate, deltaDays: Int, deltaMinutes: Int?, reason: String?, updatedAt)`，或最小实现：`SubjectEntity` 加 `airDelayWeeks: Int = 0` + `airDelayReason: String? = null`。计算层在 `computeCalendarEvents` 生成 `broadcastByDate` 后统一应用：`effectiveDate = date.plusWeeks(delayWeeks)`，并对 `hasContinuing/gap` 判定同步。标记：`MarkType` 增 `DELAYED`，`MarkInfo` 带 `label = "延期"`；`.ics` 的 `SUMMARY` 加"（延期）"。
- **效果**｜日历格子上一个橙色小三角；点开 sheet 该行右侧"原定 10/10 · 延期至 10/17"；通知文案改为"《X》延期后今日更新"。
- **参考**｜`FuzzyGrim/Yamtrack@0.26.3:src/events/calendar/main.py` [码] `save_events`：唯一键相同就 `existing_event.datetime = event.datetime` 并 `bulk_update`——"延期"= 同一事件换时间，天然可表达，不需要新概念。
- **成本/风险**｜M｜风险：延期数据没有自动来源（Bangumi 的 `air_date` 是首播日，不含单集改期），**只能靠 episodes.airdate 变化被动发现**；若想主动标注，必须人工录入——先只做"从集日期缺口推断已延期"的只读展示，别做录入 UI。
- **优先级**｜P1

### 4.11 每日提醒时间可配置
- **现状**｜无｜`AiringReminderScheduler.schedule` 用 `PeriodicWorkRequestBuilder<AiringReminderWorker>(1, TimeUnit.DAYS)`，**没有 initialDelay / flex**（`AiringReminderScheduler.kt:56-63`），触发时刻由 WorkManager 上次执行时间 + 系统调度决定，用户无法指定"每天 22:30 提醒"。
- **提升**｜用户能在"放送后 10 分钟"收到提醒而不是凌晨 3 点；工程上把"周期任务"换成"每日一次性任务自链"，顺带解决 WorkManager 周期任务在 Doze 下漂移导致的"提醒越来越晚"。
- **原理**｜三处改动：① `AppSettings` 加 `airingReminderHour: Int = 21`、`airingReminderMinute: Int = 0`；② `schedule(context)` 改注册 `OneTimeWorkRequest` + `setInitialDelay(delayUntilNext(hour, minute, now))`，Worker 结束前再 `enqueueUniqueWork(..., REPLACE)` 排下一次（自链）；③ `AiringReminderBootReceiver` 开机后重排（已有，只需改调用）。`delayUntilNext` 是纯函数：`now.toLocalTime()` 与目标时刻比较，跨天用 `plusDays(1)` 而非 86_400_000 毫秒（DST 无关但可读性更好）。
- **效果**｜设置→"收藏与展示"（或新"提醒"小节）出现"提醒时间 21:00"，点开是系统时间选择器；改完立刻生效（当前周期不变，下一次按新时刻）。
- **参考**｜`IgnisDa/ryot@main:crates/services/miscellaneous/background/src/calendar.rs`（目录级确认，未读内容）说明"后台日历+通知"是独立可调度单元；实现层面 WorkManager 自链是 Android 官方推荐做法 [推断]。
- **成本/风险**｜S｜风险：`ExistingPeriodicWorkPolicy.KEEP` 改成 `OneTime` 自链后，如果 Worker 在 `doWork` 里抛异常，链就断了（通知永久静默）——必须在 `finally` 里重排，且用 `Result.retry()` 兜底。
- **优先级**｜P1

### 4.12 提醒去重与合并
- **现状**｜已实现｜`AiringNotificationComposer`：1 条 → "《X》今日更新" + "今日 HH:mm 放送，记得观看"；2 条 → "《A》《B》今日更新"；≥3 条 → "《A》《B》等 N 部今日更新"；标题超 `MERGED_TITLE_LIMIT = 48` 字降级为"N 部作品今日更新"；合并通知固定 `MERGED_NOTIFICATION_ID = 1001`（同一天重复执行会覆盖，天然去重）；单条用 `subjectId` 作 id。点击：合并 → `EXTRA_OPEN_AIRING_LIST` 落到"在看"列表，单条 → `EXTRA_SUBJECT_ID` 直达详情。
- **提升**｜v1 判"部分"，实际已闭环。本轮可补的是"跨天不覆盖"：目前 `1001` 是全局固定 id，昨天的合并通知未读会被今天的覆盖。
- **原理**｜把 id 改成 `MERGED_NOTIFICATION_ID + epochDay`（如 `1001 + 20000` 会溢出 int 语义混乱，改用 `("merged-$epochDay").hashCode()`），并在 `compose` 里加 `epochDay` 参数；`NotificationManagerCompat.cancel` 旧 id 需要额外记账（SharedPreferences 存 lastMergedId）。
- **效果**｜今天与昨天的合并通知同时留在通知栏（各带日期），而不是昨天被吞。
- **参考**｜`open-ani/animeko@6.3.0:README.md` [README]（本轮仅确认 README 可读，源码 CDN 403，未读实现）。
- **成本/风险**｜S｜风险：同一作品"今日更新"与"漏看摘要"（4.5）若都发，会两条并存——需要一个"每日最多 1 条放送类通知"的仲裁，建议 4.5 落地时一并做。
- **优先级**｜P2

### 4.13 观看历史与日历联动
- **现状**｜部分｜`CalendarDaySheet` 的签名只有 `onDismiss` 与 `onSubjectClick`（`CalendarDaySheet.kt:63-69`），四段列表点击全部跳详情，**没有任何写回**；日历能看到"看到第几集"（`BroadcastEventItem` 读了 `episodes`），但要改进度必须离开日历。
- **提升**｜在日历上直接写"看到这里"：用户扫日历→发现落后→原地补齐，省掉"进详情→找到剧集→点进度"三步；工程上复用现有 `CollectionRepository` 写回通道，不新增数据流。
- **原理**｜`CalendarDaySheet` 增 `onMarkWatched: (subjectId: Long, episode: Int) -> Unit`；`BroadcastEventItem` 行尾加"标记第 N 集"（N = `estimateEpisode(date, subject)`，已有）；`StatsViewModel` 暴露 `markWatched(subjectId, ep)` → `collectionRepository.updateProgress(...)`（乐观更新 `uiState`，失败回滚 + Snackbar）。注意 sheet 是非模态（`NirikoNonModalSheet`，无 scrim），写入后**不要**自动关闭，否则用户连写多集会断。
- **效果**｜长按/行尾按钮 → 该行变"已看到第 8 集 ✓"，格子上的"落后"角标立刻消失；连续给 3 部作品补进度不需要退出日历。
- **参考**｜`FuzzyGrim/Yamtrack@0.26.3:src/templates/app/components/fill_track_episode.html`（目录存在）[推断]：其 UI 有"补齐已播集数"的一键组件，思路同源。
- **成本/风险**｜M｜风险：日历事件由 `combine(items, ...)` 派生，写回后 `items` 变化会整页重算 `computeCalendarEvents`（当前在 `Dispatchers.Default`，月视图 31 天 × 全部作品），可能出现明显卡顿——需要先给 `computeCalendarEvents` 加按 subjectId 的增量或对 `baseItemsSignature` 做更细的缓存（StatsViewModel:392-457 已有签名缓存，可扩展到日历）。
- **优先级**｜P1

### 4.14 日历数据新鲜度可见
- **现状**｜已实现｜`BroadcastStatusHint`（CalendarCard:429-478）在 `calendarMode != PERSONAL` 时显示 `broadcastMonthMessage(state)`（"…放送信息加载中/已更新/暂无/加载失败"）+ "上次更新 X 分钟前"（`RefreshStatusLabels.formatAgo`，每 15 秒走字）+ "刷新"按钮；每月独立状态在 `BroadcastMonthState(key, status, lastSuccessAt, error, isPartial, eventCount)`，`StatsViewModel` 里 `_broadcastMonths` 按 key 存；**历史月份的错误与时间戳不会被当月数据覆盖**（`uiState` 里 `monthState.error` 优先，周视图才退回全局 `broadcastError`，StatsViewModel:134-137）。`isPartial` 时 `lastSuccessAt` 保持旧值（:278），诚实。`refreshBroadcastSchedule` 会清 `prefetchedSubjectIds` 与 `seasonalLoadedAt`（修过"手动刷新什么都不拉"的 bug，:355-361）。
- **提升**｜v1 判"部分"应改判已实现。剩余小缺口：`MAX_SEASONAL_MONTHS = 24`（:445）之外的月份会被 LRU 丢掉，回翻时状态重置为 `LOADING` 且无提示"这是重新加载"。
- **原理**｜已到位，无需改造；若要补 LRU 提示，只需在 `updateMonth` 的淘汰路径打一个 `evictedKeys` 集合，UI 读到时显示"该月份数据已释放，正在重新加载"。
- **效果**｜现状已满足"用户能判断日历是新的还是旧的"；补丁后，翻回 3 年前月份不会诡异地停顿无提示。
- **参考**｜本项目自身即范本（`BroadcastMonthState` 模式同时被 4.3/4.8 复用）；`IgnisDa/ryot@main:crates/services/statistics/src/daily_activity_operations.rs` [码] 的 `user_analytics_parameters`（先查 min/max 日期再决定粒度）是同一种"先看数据边界，再决定展示"的思路。
- **成本/风险**｜S｜风险：无。
- **优先级**｜P2

## 4.9 本域新增候选（发散）· 条目 4.15–4.20

### 4.15 放送时刻的时区换算
- **现状**｜无 · 新提出｜`OnAirRepository.enrich` 写入 `airTimeZone = "CN"`（`data/onair/OnAirRepository.kt:49`），但全仓 `grep airTimeZone` 只有 3 处：DB 列（`NirikoDatabase.kt:398`）、实体字段（`SubjectEntity.kt:34`）、写入点——**没有任何读取方**。`airTimeMinutes` 被直接 `"%02d:%02d"` 格式化显示（`SubjectDetailScreen.kt:2271`、`CalendarDaySheet.kt:336`、`AiringNotificationComposer.formatMinutes`）。
- **提升**｜非 UTC+8 用户看到的"23:30 放送"才是真的 23:30；工程上把"时刻 + 时区"当成一个值对象，`.ics`（4.8）与通知文案（4.12）都从同一处取，不再各写一遍 `formatMinutes`。
- **为什么是 Niriko 需要**｜本项目是**纯客户端 + Bangumi 主源**，放送时刻只有一份静态 `timeCN`（中国时区），而用户可能在日本/欧美；又因为 `.ics` 导出会把这个时刻固化进系统日历，错误时区会**离开 App 之后继续错**——这是别家（Bangumi 服务端/网页）不用管的。
- **原理**｜引入 `data/model/AirTime(val minutes: Int, val zoneId: String)`，转换函数 `fun AirTime.toLocal(zone: ZoneId): LocalTime`（把 `LocalDate + minutes` 组装成 `ZonedDateTime.of(date, time, ZoneId.of("Asia/Shanghai")).withZoneSameInstant(zone)`，注意**跨日**：23:30 CN → 次日 00:30 JST，日期要一起进位）。UI 只调 `AirTime.formatLocal()`；`.ics` 用原 zone 生成 `DTSTART;TZID=Asia/Shanghai`，由日历端自己换算（更稳）。
- **效果**｜设备时区为 `Asia/Tokyo` 时，详情页显示"00:30 放送（10 月 18 日）"，日历格子上仍在 10 月 17 日（按源时区），并在图例旁小字"放送时刻以中国时区（UTC+8）显示，设备本地 00:30"。
- **参考**｜`ical4j/ical4j@master:src/main/java/net/fortuna/ical4j/model/Recur.java` [码] 说明"时刻 + 时区 + 规则"必须成套处理；本项目自身 `airTimeZone` 字段就是为它预留的。
- **成本/风险**｜S｜风险：`timeCN` 是否已是"JST 换成的中国时间"**待核**——需抽查 3 部番（例：`349039` 显示 `weekDayCN 6 / timeCN 0055`）与 Bangumi 页面比对，否则换算方向会反。
- **优先级**｜**P0**（.ics 前置）

### 4.16 日历事件物化表（增量 upsert）
- **现状**｜无 · 新提出｜`computeCalendarEvents` 每次都从 `items` + `broadcast` + `seasonal` + `episodesBySubject` 全量重算，放在 `StatsViewModel.uiState` 的 `combine` 里（9 个上游任一变化 → 整个月重算），只在 `Dispatchers.Default` 上缓解，没有缓存。
- **提升**｜翻月/写进度/切模式不再触发全量重算；工程上把"日历"从派生视图变成可查询数据，4.3/4.4/4.5/.ics 都能直接查表，而不是各自调一遍计算器。
- **为什么是 Niriko 需要**｜本项目无服务端，日历完全在设备上现算；收藏量 1000+ 时 `31 天 × 全部作品` 的双重循环在低端机上是真实成本，而换库（kizitonwose）只优化了渲染、没优化计算。
- **原理**｜新增表 `calendar_events(date, subjectId, kind /*BROADCAST|RELEASE|STARTED|COMPLETED*/, episodeSort, estimated, sourceFingerprint)`，唯一键 `(date, subjectId, kind)`；写入走 `upsert(list)`，只重写"指纹变化"的行（指纹 = `airWeekday|airTime|totalEpisodes|episodesHash`）。渲染改为 `SELECT ... WHERE date BETWEEN ? AND ?` → 组装 `CalendarDayEvents`。清理：`DELETE WHERE sourceFingerprint NOT IN (...)`（对应"停播/改期"）。
- **效果**｜切月瞬时（一次索引查询），写进度后只重算受影响作品的那几行；离线冷启动也能秒开日历（表里已有上次数据）。
- **参考**｜`FuzzyGrim/Yamtrack@0.26.3:src/events/calendar/main.py` [码] `save_events`（唯一键 → `bulk_update` / `bulk_create`）+ `cleanup_invalid_events`；`IgnisDa/ryot@main:crates/models/database/src/calendar_event.rs`（目录确认）同为"物化日历事件"。
- **成本/风险**｜L｜风险：多一张表 = 多一次 Room 迁移（v33）+ `app/schemas/` 变更 + 迁移测试；且缓存失效逻辑写错会出现"日历与详情页不一致"，比现在慢但正确更糟。
- **优先级**｜P2

### 4.17 日历/看板一键补进度
- **现状**｜无 · 新提出｜（见 4.13 的读侧；本条是**批量**形态）日历上看得到"落后 3 集"，但没有任何"一次补到最新"的入口。
- **提升**｜"我看完了但懒得点"的场景一步到位：看板每行一个"补到第 8 集"，工程上仍是一次 `updateProgress`，只是把 `N` 从"逐集 +1"变成"直接设值"。
- **为什么是 Niriko 需要**｜本项目进度字段是 `watchedEpisodes: Int?`（**支持跳跃**，不是事件流），天然适合"设值"；而 Bangumi 写回是按集提交的，跳跃会触发多次写回——这条要顺带设计"写入合并"。
- **原理**｜`markWatched(subjectId, ep)`（4.13）已能设值；批量版 `catchUpAll(items: List<Lag>)` 串行执行（**不要并发**：Bangumi 写回有速率限制），每部之间 `delay(300)`，整体进度用 `NotificationCompat` 进度条或页内一行"补齐中 3/8"。失败集合保留并给"重试剩余"。
- **效果**｜看板右上"全部补到最新（8 部）"，确认后逐行打勾；完成后卡片变空态"都追平了"。
- **参考**｜`Ludi-Herry/bandi@main:README.md` [README]："已放送但尚未下载时提供'找资源'"——同为"看板级动作"的设计取向。
- **成本/风险**｜S｜风险：Bangumi 写回失败会留下"本地已补、远端没补"的不一致；必须先有位 1.3 的"待同步队列"再上批量。
- **优先级**｜P1

### 4.18 静默时段与逐作品静音
- **现状**｜无 · 新提出｜`AiringReminderWorker` 注释明确"**不做**免打扰时段"（用户此前确认），只有全局开关；`AiringReminderScheduler` 无时段概念。
- **提升**｜凌晨放送的番（如 `timeCN 0055` 那类）不再在 4 点响；工程上把"提醒"从布尔扩成 `(enabled, quietFrom, quietTo, perWork)`，与 4.6 的逐作品开关共用 UI。
- **为什么是 Niriko 需要**｜Bangumi 放送时刻大量落在 00:00–02:00（本项目静态数据里 `0055/0123/0029` 比比皆是），而提醒是每日周期任务，触发时刻不可控（4.11）——两者叠加就是真实的夜间打扰，比一般"每日打卡"类 App 严重。
- **原理**｜静默判定是纯函数 `shouldNotify(now: LocalTime, from: LocalTime, to: LocalTime): Boolean`（跨零点时 `from > to` 的区间用 `now >= from || now <= to`）；命中静默时不发通知，但**把内容投递到"延后队列"**（SharedPreferences 存 `pendingNotification: String?`），在静默结束后的下一次 Worker 执行时补发一条"昨晚有 3 部更新"。逐作品静音复用 4.6 的 `remindEnabled`。
- **效果**｜设置里"夜间免打扰 23:00–08:00"开关；开启后凌晨剧集的通知在早上 8 点合并成一条"昨晚 3 部更新：《A》《B》等"。
- **参考**｜`AiringNotificationComposer` 的合并能力（已实现）可直接复用为"补发摘要"；无外部对照实现。
- **成本/风险**｜S｜风险：延后队列存在本地，若用户整夜不开机，补发会丢（WorkManager 不保证跨 Doze 准点）——UI 文案写"会在下次打开时提醒"，别承诺准点。
- **优先级**｜P1

### 4.19 本季星期表矩阵视图
- **现状**｜无 · 新提出｜日历是"日期 × 事件"（`CalendarDayEvents`），没有"星期 × 作品"的矩阵；用户想知道"周三有什么番"只能翻到周三。
- **提升**｜一屏看完整周的放送结构，适合"决定这季追哪些"；工程上是同一个 `Map<DayOfWeek, List<AiringSubject>>`（`_broadcastSchedule` 已存在）的另一种渲染，**零新数据**。
- **为什么是 Niriko 需要**｜本项目主源就是 Bangumi `/calendar`（返回的是星期分组），矩阵视图才是它的原生形态；现在的日历把这层信息压成了日期，反而多绕一步。
- **原理**｜`BroadcastMatrixSection(byDay: Map<DayOfWeek, List<AiringSubject>>, watchingIds: Set<Long>)`：7 列（或横滑），列内按 `airTimeMinutes` 升序，每项显示封面 + 时间 + 标题（在看的高亮/加"在看"点）；列头显示"周三 · 6 部"。数据源 `StatsViewModel._broadcastSchedule`，无需变更。
- **效果**｜统计页日历卡下方"本季星期表"折叠区，展开后 7 列矩阵；点击任一项进详情。
- **参考**｜`Ludi-Herry/bandi@main:README.md` [README]："本季星期表"是首页四块之一。
- **成本/风险**｜M｜风险：7 列在窄屏会挤成 4px 宽——必须横滑或折叠成"每天一行"的列表；本项目已有 `maxDayCellSize * 7` 的宽度收敛模式（CalendarCard:170）可复用。
- **优先级**｜P2

### 4.20 放送源可信度与多源交叉
- **现状**｜部分 · 新提出｜图例里已有诚实文案"放送时间参考 Bangumi 日历"（CalendarCard:410，9sp、55% 透明），`BroadcastMonthState.isPartial` 也表达了"部分来源失败"；但没有"这一格的数据来自哪个源、可不可信"的逐条标注，也没有 Bangumi 与静态 `onair` 的交叉校验。
- **提升**｜把"精确/估算/多源一致"变成每个事件的一个小标记，用户能判断"这个 23:30 靠不靠谱"；工程上让 `computeCalendarEvents` 的三条来源路径（episodes 实日期 / broadcast 星期 / seasonal 回填）各自带 source 标签，替掉现在散落的 `coverFallbackMap` 式补丁。
- **为什么是 Niriko 需要**｜本项目是**单源（Bangumi）+ 静态资产**，没有服务端做二次校验；而放送时刻是"错了会浪费一个晚上"的数据，透明度比覆盖率更重要。这也是本项目一贯的"可信度可见"风格（`RefreshStatusLabels`、`isPartial`、`GlassDecision` 降级真值表都是同一种设计语言）。
- **原理**｜`CalendarDayEvents` 的放送项改为 `BroadcastHit(subject, source: Source, confidence: Conf)`，`Source ∈ {EPISODE_DATE, CALENDAR_WEEKDAY, STATIC_ONAIR, SEASONAL_FALLBACK}`，`Conf ∈ {EXACT, WEEKDAY, STALE}`；冲突时（например episodes 说 10/16，calendar 说周五）**保留两个并标"源冲突"**，而不是静默择一。UI 用一个 2px 圆点颜色区分，图例增加一行。
- **效果**｜日历格子上的菱形由实心（EXACT）→ 半透明（WEEKDAY）→ 空心带虚边（STALE）；点开 sheet 每条右侧一行极小字"来源：Bangumi 章节日期"或"来源：日历星期推算（可能滞后）"；出现源冲突时该行显示两个日期并标"待核"。
- **参考**｜`open-ani/animeko@6.3.0:README.md` [README]（v1 R1 的"能力声明 + 多源选择"取向）；`IgnisDa/ryot@main:crates/services/miscellaneous/calendar/src/lib.rs` [码] 把 `only_monitored/deduplicate` 作为显式入参，同属"把不确定性做成参数而不是隐藏行为"。
- **成本/风险**｜M｜风险：`refresh status` 与"事件级可信度"是两层概念，混用会让 UI 文案膨胀；先只做放送模式，个人记录模式不加。
- **优先级**｜P1

## 4.A 本域新增参考库

| 仓库 | 许可 | star | 语言 | 为何与本项目相关 | 已验证文件 |
| --- | --- | --- | --- | --- | --- |
| kizitonwose/Calendar | MIT（shields 取到） | 5.6k（截至 2026-10） | Kotlin（Compose，含 KMP 变体） | **本项目已在用** 2.10.1：`CalendarCard` 的月/周网格；其 `HeatMapCalendar` / `YearCalendar` 是 5.21 热力图与年历的现成组件，无需再引别的日历库 | `compose/src/main/java/com/kizitonwose/calendar/compose/heatmapcalendar/HeatMapCalendar.kt` [码] `HeatMapCalendarImpl(state: HeatMapCalendarState, weekHeaderPosition, ...)`（目录亦确认 `yearcalendar/YearCalendarState.kt`） |
| patrykandpatrick/vico | 未取到（本项目 `libs.versions.toml:96` 注释记 Apache-2.0） | 3.2k（截至 2026-10） | Kotlin（Compose + KMP） | **本项目已在用** 3.2.0：柱/环图；marker（长按读数）、`CartesianMarkerVisibilityListener` 是 5.24 的现成 API；3.3.1 因顶 Kotlin/Compose 版本被拒（`libs.versions.toml:26-28`） | `vico/compose/src/main/java/com/patrykandpatrick/vico/compose/cartesian/marker/DefaultCartesianMarker.kt` [码]（目录确认；本项目 `ui/subject/EpisodeRatingChart.kt` 已用 `rememberDefaultCartesianMarker`） |
| IgnisDa/ryot | GPL-3.0（shields 取到） | 未取到 | Rust + React | "未来 N 天/接下来 N 部"的入参设计与去重开关；每日活动聚合表是 5.4/5.12 的范式 | `crates/services/miscellaneous/calendar/src/lib.rs` [码] `user_upcoming_calendar_events` / `UserUpcomingCalendarEventInput::NextDays` / `deduplicate_media`；`crates/services/statistics/src/daily_activity_operations.rs` [码] day/month/year 自动分桶（≥500 天→年，≥200 天→月） |
| FuzzyGrim/Yamtrack | 未取到 | 未取到 | Python（Django） | 日历事件**物化 + 幂等 upsert + 清理**的完整实现，是 4.16 的直接蓝本；一集一事件，唯一键 `(item, content_number)` | `src/events/calendar/main.py` [码] `fetch_releases` / `save_events` / `cleanup_invalid_events`；`src/events/calendar/` 已按 anime/tv/comic/other 分文件（目录确认） |
| Ludi-Herry/bandi | GPL-3.0（README 自述） | 未能复取（ungh 限流；Lead 18:20 曾取得原仓库 `anime-tracker-desktop` = 2 star，未复核） | TypeScript/Web（据 README，源码未读） | 首页四问模型（今天看什么/看到第几集/下集何时/本季星期表）+ "漏看优先于今日更新"的产品规则；是 4.2/4.4/4.19 的**产品级**参照 | `README.md` [README]（v1 的 `Luis-Herry/anime-tracker-desktop` 疑已改名至此；jsDelivr 对二者均报 "package size exceeded 50MB"，**源码未读**） |
| ical4j/ical4j | BSD-3-Clause（`Recur.java` 头部许可文本已读） | 未取到 | Java | RRULE 的权威解析实现；**不引入依赖**，只按其语义生成 .ics（4.8） | `src/main/java/net/fortuna/ical4j/model/Recur.java` [码]（`net.fortuna.ical4j.transform.recurrence` 包） |
| open-ani/animeko | AGPL-3.0（v1 R1 记录） | 21k（截至 2026-10） | Kotlin Multiplatform | v1 的主参考；本轮仅确认 README 可读，**源码与目录树在 jsDelivr 全部 403**，未能升级到 [码] | `README.md` [README]（`@6.3.0` 可读；`data.jsdelivr.com/.../open-ani/animeko@6.3.0/flat` → 403） |

## 4.B 计数自检

- 详解条目：**14**（4.1–4.14，编号与 v1 完全一致，无合并）。
- 新增候选：**6**（4.15–4.20）。
- 汇总表行数：**20** = 14 + 6 ✅。
- 证据等级分布：本项目侧全部 [码]（实读文件/行号）；外部侧 [码] 5 处（ryot ×2、Yamtrack、kizitonwose、ical4j）、[README] 2 处（bandi、animeko）、[推断] 1 处（WorkManager 自链为官方推荐，4.11）。
- 待核 2 处：① `timeCN` 是否为 JST 转中国时间（4.15/4.8）；② bandi 源码不可达（jsDelivr 报 package size exceeded 50MB），其实现细节未验证（4.4/4.19/4.20 仅产品结论）；其与 `Luis-Herry/anime-tracker-desktop` 的改名关系亦未核实。
- star 取数日期：2026-10；未取到的写"未取到"，未编造。
