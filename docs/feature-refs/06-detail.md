# 6 详情与元数据

> 一句话结论：本域最值得做的是 **6.7 详情页离线完整性**（把已建好的 `DetailCacheStore` 从 4 个 sourceKey 扩到所有源，并删掉进程内 `extendedCache` 双轨）与 **6.15 外部源区块的折叠**（它其实已进入部件表，只差「收起」）。
> 6.4 的 TAB 化不是信息密度问题，而是「18 个部件顺序拼接」的信息架构问题——先做 6.20 的区块深链，成本 S 就能验证是否真需要 TAB。
> v1 有两条判断需要纠正：6.15「外部源可能未纳入部件列表」**已过时**（`DetailSectionId` 里 STEAM/ANILIST/VNDB 都在）；6.8 建议关闭（本项目无内容生产）。

## 6.0 汇总表

| # | 功能 | 现状 | 优先级 | 成本 | 参考实现（简） |
| --- | --- | --- | --- | --- | --- |
| 6.1 | 巡礼地图内嵌地图交互 | 部分 | P2 | L | anitabi-swift-app 的 `MapCameraCommand` 命令式相机 |
| 6.2 | 巡礼打卡与整理 | 无 | P1 | M | WorkPointList 分组 + 新表 `anitabi_visits` |
| 6.3 | 巡礼对比图生成 | 无 | P2 | M | 复用 `ShareCardRenderer` 的 Canvas 管线 |
| 6.4 | 详情页二级分页（TAB） | 部分 | P2 | M | Kotatsu `ChaptersPagesAdapter` 固定位置映射 |
| 6.5 | 衍生资源聚合 | 部分 | P1 | S | TMDB videos + 关联条目 type=music |
| 6.6 | 单集时长与首播日完整性 | 部分 | P1 | S | 字段已在库，缺解析/回填/展示 |
| 6.7 | 详情页离线完整性 | 部分 | **P0** | M | Kotatsu `DetailsLoadUseCase` 渐进 Flow |
| 6.8 | 简介富文本的编辑态 | 部分 | P2 | S | 建议只补「复制」，不做编辑 |
| 6.9 | 剧照来源优先级可配 | 部分 | P1 | S | 有序回退链 + 复用 `DetailLayoutPolicy` |
| 6.10 | 图片查看器的原图与缓存策略 | 部分 | P1 | S | Mihon `CoverCache` 独立目录+删除计数 |
| 6.11 | infobox 值的可操作化 | 部分 | P1 | S | Mihon 标签「搜索/复制」双动作 |
| 6.12 | 关联条目的关系类型分组 | 部分 | P1 | S | Kotatsu `Map<String?, List<>>` 分组 |
| 6.13 | 评分争议度解释 | 部分 | P2 | S | 纯函数已有，缺解释弹层 |
| 6.14 | 详情自动重试匹配 | 部分 | P1 | M | Steam 已有通路，抽成三源共用 |
| 6.15 | 外部源区块的可折叠与顺序 | **已实现**（v1 过时） | P1 | S | 只差 collapsed 状态 |
| 6.16 | 详情页分享内容可配置 | 部分 | P2 | S | Kotatsu 按载荷拆分享通道 |
| 6.17 | 无词条条目的详情兜底 | 部分 | P1 | L | Kotatsu `MangaOverride` + Ryot `is_partial` |
| 6.18 | 详情页「一屏摘要」卡（新） | 无 · 新提出 | P2 | S | Mihon `MangaContentInfo` |
| 6.19 | 详情页「来源与时间戳」脚注（新） | 无 · 新提出 | P1 | S | 数据已在 `DetailCacheStore` |
| 6.20 | 区块级深链（新） | 无 · 新提出 | P1 | S | Kotatsu `MangaIntent` 解析 |
| 6.21 | 只刷新当前区块（新） | 无 · 新提出 | P1 | S | `DetailCacheDao.invalidateDetail` 已支持 |
| 6.22 | 阅读位置记忆（新） | 无 · 新提出 | P2 | S | 复用 `DetailSectionId.key` |
| 6.23 | 元数据完整度体检（新） | 无 · 新提出 | P2 | S | Ryot `is_partial` |
| 6.24 | 详情页「同一制作班底」推荐（新） | 无 · 新提出 | P2 | S | `PersonJobAnalyzer` 已有 |

## 6.1 逐条详解

### 6.1 巡礼地图内嵌地图交互
- **现状**｜部分｜`ui/subject/SubjectDetailScreen.kt:2291 AnitabiSection` 只有一个 `Text("巡礼地图")` + `Intent.ACTION_VIEW` 跳 `https://anitabi.cn/map?bangumiId=`（2304–2316 行）；地标卡点击只开 `ui/common/ImageViewer.kt`。数据来自 `api.anitabi.cn/bangumi/{id}/lite`，`AnitabiLitePoint` 只有 `id/cn/name/ep/s/image`，**没有经纬度**。
- **提升**｜用户不离开 app 就能回答「这集 12:34 那个坡道在哪」并直接导航；工程上省掉一次浏览器跳转 + 一次跨 app 返回栈，且把「地标」从图片升级为坐标（后续 6.2 打卡、6.3 对比图都依赖它）。
- **原理**｜两步，先通坐标再接地图：
  1) 新 DTO `AnitabiGeoPoint(id, cn, name, ep, s, lng, lat, image)`，取带坐标的端点（待核：`/bangumi/{id}/points/detail` 或 `/bangumi/{id}` 的 points 字段），落库时把 `anitabi_points.litePointsJson` 升级为 `pointsJson` + 加 `schemaVersion` 列（与 `SubjectDetailCacheEntity.schemaVersion` 同款约定）。
  2) 地图用命令式相机：`sealed interface CameraCommand { data class FlyTo(lng,lat,zoom); data class FitPoints(list) ; data object FitWork }`，UI 只发命令、相机状态单点持有。
  3) 密度分级（照抄上游可跑通的经验值）：zoom 越小只显示聚合点，`threshold = interpolate(zoom, base=1.75, stops=[(12,4),(18,8),(22,16)])`。
  ```kotlin
  sealed interface CameraCommand {
      data class FlyTo(val lng: Double, val lat: Double, val zoom: Double) : CameraCommand
      data class Fit(val points: List<AnitabiGeoPoint>) : CameraCommand
  }
  ```
- **效果**｜区块右上按钮从「巡礼地图」变成「地图上看」，打开全屏地图：底部横滑当前集地标缩略图，点缩略图相机飞过去；点地图上的点开 `ImageViewer`。无坐标数据时入口不出现（退回今天的截图列表 + 外链），不出现空地图。
- **参考**｜`anitabi/anitabi-swift-app@main:anitabi/Map/MapEngine.swift` [码] `MapWorkSeed`/`MapViewport`/`pointThreshold(zoom:)`/`dotRadius(zoom:)`；`anitabi/anitabi-swift-app@main:anitabi/Map/MapCanvas.swift` [码] `MapCameraCommand(.center/.region/.flyTo/.fitWork/.place)`；`anitabi/anitabi-swift-app@main:anitabi/Sheets/WorkPointList.swift` [码] `WorkListRow`/`flatten`。
- **成本/风险**｜L｜坐标端点**未核实**（本轮只验证了 lite 端点无坐标）；地图 SDK 体积与国内合规是主要风险。可先做 S 成本的「跳系统地图 `geo:lat,lng?q=`」验证需求。
- **优先级**｜P2

### 6.2 巡礼打卡与整理
- **现状**｜无｜`data/local/entity/AnitabiPointEntity.kt` 只有快照（`subjectId/city/pointsLength/imagesLength/litePointsJson/fetchedAt`），**没有任何 per-point 的本地状态**；`data/repository/AnitabiRepository.kt` 的 `load()` 只做 D7 缓存读取，无写入口。
- **提升**｜用户能标「已到访 3/18」并在巡礼区看到进度；工程上只加一张小表和一条 Flow，不动详情主链路，且可给分享卡供一个新数字。
- **原理**｜新表 + 稳定点键：
  ```kotlin
  @Entity(tableName = "anitabi_visits", primaryKeys = ["subjectId", "pointId"])
  data class AnitabiVisitEntity(
      val subjectId: Long, val pointId: String,
      val visitedAt: Long, val note: String? = null,
  )
  ```
  点键优先用 `AnitabiLitePoint.id`；为 null 时用 `"ep${ep}:s${s}"` 生成（Anitabi lite 里 `id` 是 `String?`，实测可能缺）。`AnitabiDao` 加 `observeVisits(subjectId): Flow<List<AnitabiVisitEntity>>`，UI 用 `collectAsStateWithLifecycle`。整理：按 `ep` 分组（`groupBy`），组内「未到访优先」排序。
- **效果**｜地标卡右上出现可点圆圈；区块标题变 `取景地标 · 3/18 已到访`；勾最后一个时触发一次完成反馈（复用 `CompletionCelebrationState` 的一次性通道）；导出备份时多一类 `anitabiVisits`。
- **参考**｜`anitabi/anitabi-swift-app@main:anitabi/Sheets/WorkPointList.swift` [码] `WorkPointGroupHeader(onToggle:)`/`selectedPointID`/`flatten()`；`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/details/data/MangaDetails.kt` [码] `chapters: Map<String?, List<MangaChapter>>`。
- **成本/风险**｜M｜Room version 33 迁移 + 备份格式要「读旧不炸」；老备份没有该类时默认空集合。
- **优先级**｜P1

### 6.3 巡礼对比图生成
- **现状**｜无｜`ui/share/ShareCardRenderer.kt` 只渲染单作品卡；无本地相册读取、无两图合成（`ui/common/CoverImageAnalysis.kt` 只做取色/模糊）。
- **提升**｜用户能产出「现实照片 vs 动画截图」对比图并直接分享；工程上复用现成的 `ShareFlow.saveAndGetUri` + FileProvider，新增的只有一个合成纯函数。
- **原理**｜输入两张 Bitmap + 分割比例 `split ∈ [0.2,0.8]`；输出 1080×1440：上 `split` 高画现实照、下画动画截图，两图都 `centerCrop` 到同宽；中间 4dp 亮线 + 右下角两行水印（作品名 / `EP3 12:34`）。
  ```kotlin
  fun compose(real: Bitmap, shot: Bitmap, split: Float, label: String): Bitmap {
      val out = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
      Canvas(out).use { c ->
          c.drawBitmap(real.crop(1080, (1440 * split).toInt()), 0f, 0f, null)
          c.drawBitmap(shot.crop(1080, 1440 - (1440 * split).toInt()), 0f, 1440f * split, null)
      }
      return out
  }
  ```
- **效果**｜地标卡长按菜单多一项「做对比图」；合成页可拖动分割线实时预览；确认后进系统分享，失败（没选照片/图下载失败）给出「换一张」。
- **参考**｜本项目 `ui/share/ShareCardRenderer.kt` [码]（软件 Canvas 管线已跑通，含 `allowHardware(false)` 的坑）；`anitabi/image-merge` [推断]（v1 引用，本轮**未取到源码**，仅作同名功能存在性参考）。
- **成本/风险**｜M｜相册读取要 `PickVisualMedia`（免权限）；两张 1080 宽位图峰值约 12MB，需 `inSampleSize` 预缩放。
- **优先级**｜P2

### 6.4 详情页二级分页（TAB）
- **现状**｜部分｜详情页是**单个 LazyColumn**，18 个部件按源码顺序拼接（`SubjectDetailScreen.kt:914 LazyListScope.detailPaneItem`，分派在 1081–1688 行）；宽屏才按 `NirikoDetailPane.OVERVIEW/CONTENT` 分两栏。无 TAB、无「记住上次看哪一页」。
- **提升**｜用户先看概览、要看分集评分/曲目再进第二页，少滚 2000dp；工程上把「部件在哪一页」从源码位置变成数据（`DetailSectionId.pane` 已是雏形），后续加部件不再改布局代码。
- **原理**｜沿用现成的过滤通道，不新建框架：把 `pane` 泛化成 `tab`，`detailPaneItem` 的判断从 `activePane == pane` 换成 `activeTab == tab`；手机单栏时 `activeTab == null` 的全量路径保留（这是「零行为变化」的保险）；
  ```kotlin
  private fun LazyListScope.detailPaneItem(id: DetailSectionId, activeTab: DetailTab?, ...) {
      if (!DetailLayoutPolicy.isVisible(id, hidden)) return   // 现有规则不动
      if (activeTab == null || activeTab == id.tab) item(key = id.key, content = content)
  }
  ```
  TAB 状态 `rememberSaveable(subjectId)`；每个 TAB 一个独立 `LazyListState`（`ui/subject/DetailRailState.kt` 已是同类封装）。
- **效果**｜顶部出现 `概览 | 内容 | 剧集` 三枚 TAB；切 TAB 各自保留滚动位置，返回再进恢复上次 TAB；「详情部件」设置页的隐藏/顺序依然生效（隐藏的部件在两个 TAB 里都不出现）；宽屏两栏维持现状不变。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/details/ui/pager/ChaptersPagesAdapter.kt` [码] `FragmentStateAdapter` + `getItemCount()` 按 `isPagesTabEnabled` 在 2/3 间切换 + `createFragment(position)` 固定位置映射；`mihonapp/mihon@main:app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt` [码] `MangaScreenSmallImpl` / `MangaScreenLargeImpl` 按 `isTabletUi` 二选一。
- **成本/风险**｜M｜「部件顺序」在 TAB 内还是全局会变成歧义；建议 TAB 内保留用户顺序、跨 TAB 不重排。TAB ≥3 时与顶栏渐隐/共享元素转场的层级要重排。
- **优先级**｜P2（先做 6.20 深链验证需求，成本 S）

### 6.5 衍生资源聚合
- **现状**｜部分｜详情页只有 `EXTENDED`（标签/简介）、`TRACKS`（曲目）、`THUMBS`（剧照）三块；PV/主题曲/周边全靠 infobox 里的 URL 手点（`InfoBoxSection` 的 `extractFirstUrl`）。TMDb 只取了 backdrop/still（`state.tmdbBackdrops` / `state.episodeStills`）。
- **提升**｜详情页多一块「衍生」，把 PV、主题曲、周边一次摆出来；工程上复用已接的 TMDb key 与 Bangumi 关联条目，**不新增数据源**。
- **原理**｜三路数据落成同一个模型：
  ```kotlin
  enum class DerivedKind { PV, MUSIC, GOODS }
  data class DerivedLink(val kind: DerivedKind, val label: String, val url: String, val source: String)
  ```
  PV：TMDb `/tv/{id}/videos` 或 `/movie/{id}/videos`（`site == "YouTube"` 且 `type in {Trailer, Teaser}`，现在只有 `TmdbImageUrl`，**videos 端点未接入**）；MUSIC：`subject_relation_cache` 里 type=MUSIC 的关联条目；GOODS：infobox 键含「周边/通贩/Amazon/淘宝」。新部件 `DERIVED("derived", …)` 追加到 `DetailSectionId` 末尾——`DetailLayoutPolicy.resolveOrder` 保证老用户升级后它自动出现在尾部。
- **效果**｜新卡「PV 与衍生」，横滑分三类；点 PV 走系统浏览器（与「不做播放器」边界一致）；无数据时整块不渲染（`if (links.isEmpty()) return`）。
- **参考**｜`xiaoyvyv/bangumi@master:README.md` [README] 「衍生资源聚合与发现：Mikan 蜜柑计划、Anime-Pictures/Pixiv、图片/视频媒体预览」；本项目 `data/remote/tmdb/` [码]（`TmdbImageUrl` 在用）。
- **成本/风险**｜S｜YouTube 链接在境内不可达，需在卡片上标「需网络环境」；不要把外链做成内置播放。
- **优先级**｜P1

### 6.6 单集时长与首播日完整性
- **现状**｜部分｜`data/local/entity/EpisodeEntity.kt` 已有 `airdate: String?` / `duration: String?` / `durationSeconds: Int = 0`，但 UI 只在 `ui/episode/EpisodeDetailScreen.kt:211` 展示 `airdate`；`durationSeconds` 在 **UI 层**只被曲目区 `ui/subject/TrackListSection.kt:137` 使用，单集区从不读它，缺失时也无回填。
- **提升**｜单集页显示「24 分钟 · 2026-01-09」，统计才能算「剩余时长」而不是只数集数；工程上少一处「按总集数瞎猜」，`AiringStatus` 也有了精确输入。
- **原理**｜解析 + 回填两级，全在纯函数里：
  ```kotlin
  object EpisodeDurationBackfill {
      // "00:24:00" / "24分钟" / "24 min" → 1440；解析失败返回 null（不要写 0）
      fun parse(raw: String?): Int?
      // 优先级：自身 duration → TMDb episode_run_time → 同作品所有集的众数
      fun resolve(ep: EpisodeEntity, seriesMedian: Int?): Int?
  }
  ```
  首播日缺失时取同季 `airdate + 7d × (ep 差)`，仅在「同季连载且首集有日期」时启用。放在 `data/calculator/`（与 `EpisodeTitleBackfill.kt` 同层），JVM 单测覆盖 3 种格式 + 2 个回填分支。
- **效果**｜单集行末出现 `24m`；单集详情 meta 行变 `第 3 集 · 24 分钟 · 2026-01-09`；未知时显示「时长未知」而不是 0；统计页「剩余时长」仅在 ≥80% 集数有时长时展示（否则退回集数）。
- **参考**｜`IgnisDa/ryot@main:crates/models/database/src/metadata.rs` [码] `show_specifics`/`anime_specifics` 按媒体类型分桶 + `publish_date`，说明「集级字段应该集中在一处而不是散落」。
- **成本/风险**｜S｜Bangumi `duration` 格式不统一，解析必须容错；「未知」与「0」要区分（0 会被当成「时长极短」）。
- **优先级**｜P1

### 6.7 详情页离线完整性
- **现状**｜部分｜B15 已把 4 类写进 Room：集合（角色/Staff/关联作品）、`INFOBOX`、`RATING_DISTRIBUTION`、`EXTERNAL_RATINGS`（`data/repository/DetailCacheStore.kt` + `SubjectDetailCacheEntity.SourceKeys`）。但 `SourceKeys` 里 `VNDB/ANILIST/TMDb/DOUBAN/STEAM/ANITABI/GUESS_YOU_LIKE/MANUAL_AWARDS/EPISODE_RATINGS/COVER_CANDIDATES` **grep 不到任何使用点**；VNDB/AniList 详情仍活在进程内 `TtlCache`（`SubjectDetailViewModel.kt:615 extendedCache`），杀进程即丢。界面不区分「缓存/过期」。
- **提升**｜断网或重启后详情页不再是空壳，用户能看出「这是 3 天前的数据」；工程上把 `DetailCacheStore` 从 4 个 key 扩到全部、删掉 `extendedCache` 这条双轨，少一套失效逻辑。
- **原理**｜把已有读写器用满（`readRow`/`writeRow` 在 `DetailCacheStore.kt:158` 已存在），并加一个「来源状态」出口：
  ```kotlin
  data class SourceStamp(val key: String, val fetchedAt: Long, val stale: Boolean, val failed: Boolean)
  // 读：先出缓存 + stamp，再发网络；失败只 markFailure（payload 不动）
  val stamps: List<SourceStamp> = detailCacheStore.stamps(subjectId)
  ```
  具体：`loadVndb/loadAniList/loadTmdb` 的结果 `Json.encodeToString` 写进对应 key；TTL 分档（infobox 24h / 集合 24h / 评分分布 6h / VNDB 7d，Anitabi 已是 D7）；`SubjectDetailUiState` 增 `staleSources: Set<String>`，由 `expiresAt < now || errorSummary != null` 填。
- **效果**｜飞行模式进详情：标题/封面/简介/角色/关联/评分分布都在；VNDB/AniList 区块显示上次内容并带角标「离线 · 2 天前」；从未缓存过的源显示一条「需联网」占位而不是空白。列表页的详情缓存命中不再依赖「进程还活着」。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/details/domain/DetailsLoadUseCase.kt` [码] `Flow<MangaDetails>` 先 emit 本地（`isLoaded=false`）→ `networkState.isOfflineOrRestricted()` 跳过网络 → 远端失败时 `remoteDetails ?: localDetails`；`KotatsuApp/Kotatsu@master:.../details/data/MangaDetails.kt` [码] `isLoaded`/`localManga`/`mergedManga`。
- **成本/风险**｜M｜JSON 结构变更必须递增 `schemaVersion`，否则旧行 decode 抛异常（现在靠 `ignoreUnknownKeys` 只挡了一半）；每个 write 都要 `runCatching`，缓存写失败绝不能影响 UI。
- **优先级**｜**P0**

### 6.8 简介富文本的编辑态
- **现状**｜部分｜`ui/components/RichText.kt` 负责 HTML/BBCode 渲染；无编辑态（本项目无内容生产）。v1 的建议是「关闭该项」。
- **提升**｜不做编辑，只补「可选中 + 复制原文」：用户能把简介带走粘贴到别处；工程上 3 行改动，不引入编辑器。
- **原理**｜`SelectionContainer` 包住 `RichText`；长按菜单由系统提供「复制/全选」；若某段带 `URLSpan`，点击优先走链接（`LinkAnnotation` 优先级高于选择）。不需自绘 toolbar。
- **效果**｜长按简介出现系统选择手柄；可拖选跨段；「复制」得到纯文本（不是 HTML）。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt` [码] `ExpandableMangaDescription` 只读展示 + `onCopyTagToClipboard`（复制而非编辑）。
- **成本/风险**｜S｜`SelectionContainer` 与链接点击/顶部渐隐手势有冲突，需真机验证长按起手。
- **优先级**｜P2

### 6.9 剧照来源优先级可配
- **现状**｜部分｜`SubjectDetailScreen.kt` 里 `thumbItems` **硬编码**顺序：TMDb backdrops → 每集 still → 豆瓣 → `distinctBy { url }`；无任何设置项（`AppSettings` 里没有剧照相关键）。
- **提升**｜用户能把豆瓣图提到前面（中文条目豆瓣更准）或关掉 TMDb 剧照；工程上把硬编码列表换成一条策略，以后加第四源不用改 UI。
- **原理**｜枚举 + 复用现成的顺序解析器：
  ```kotlin
  enum class ThumbSource(val key: String) { TMDB_BACKDROP("tmdb"), TMDB_STILL("still"), DOUBAN("douban") }
  // 与 DetailLayoutPolicy.resolveOrder 同款语义：未知 key 忽略、缺省按默认顺序追加尾部
  fun resolveThumbOrder(encoded: String?): List<ThumbSource>
  ```
  `thumbItems` 改成 `order.flatMap { src -> when (src) { ... } }.distinctBy { it.url }`；关掉的源连请求都不发（豆瓣剧照目前是灰色通道，关掉直接省一次请求）。
- **效果**｜设置 → 详情 → 「剧照来源」三行可上移/下移 + 开关；详情页剧照区第一张随设置变化；关掉豆瓣后 `state.doubanThumbs` 保持空、不再请求。
- **参考**｜`KotatsuApp/Kotatsu@master:.../details/data/MangaDetails.kt` [码] `coverUrl = manga.coverUrl.ifNullOrEmpty { localManga?.manga?.coverUrl }` 的有序回退链；本项目 `ui/subject/DetailSectionId.kt` [码] `DetailLayoutPolicy.resolveOrder` 可直接搬。
- **成本/风险**｜S｜`distinctBy` 依赖顺序，换序后同一 URL 的 caption/source 会变（属预期，但分享卡要跟着变）。
- **优先级**｜P1

### 6.10 图片查看器的原图与缓存策略
- **现状**｜部分｜`ui/common/ImageViewer.kt` 已有 Telephoto 缩放、`BitmapRegionDecoder` 子采样、保存相册、浏览器打开原图；但全局 `ImageLoader` 没配磁盘缓存——`NirikoApplication.kt:191` 是 `ImageLoader.Builder(this).components { … }.build()`，用 Coil 默认（约可用空间 2%）；**没有占用显示、没有清理入口**。
- **提升**｜用户能看到图片缓存占多大并一键清理；工程上避免剧照把默认磁盘配额挤满后静默淘汰封面（表现为「封面反复重新下载」）。
- **原理**｜显式磁盘缓存 + 一个只读统计口：
  ```kotlin
  .diskCache {
      DiskCache.Builder()
          .directory(cacheDir.resolve("image_cache"))
          .maxSizePercent(0.03)
          .build()
  }
  ```
  设置页显示 `imageLoader.diskCache?.size`（IO 线程），清理 `diskCache?.clear()` 后刷新数字；查看器加「原图」开关：默认走采样，点开才发 `Size.ORIGINAL`（且只对当前页）。
- **效果**｜设置 → 存储出现「图片缓存 128 MB · 清理」；点后变 `0 B` + Snackbar「已清理」；查看器右上出现「原图」按钮，切换时该页短暂显示加载态。
- **参考**｜`mihonapp/mihon@main:app/src/main/java/eu/kanade/tachiyomi/data/cache/CoverCache.kt` [码] 独立封面缓存目录 + `DiskUtil.hashKeyForDisk` + `deleteFromCache` 返回删除数；同样式还有 `ChapterCache.kt`（存在，未细读）。
- **成本/风险**｜S｜清理会让图片重新下载（用户可感知的一次流量）；目录大小要在 IO 线程算，别在主线程 `walk`。
- **优先级**｜P1

### 6.11 infobox 值的可操作化
- **现状**｜部分｜`SubjectDetailScreen.kt:3788 InfoBoxSection` 只对「值里含 URL」的行加「打开链接」（`extractFirstUrl` + `URL_REGEX`）；别名、制作人、ISBN、厂牌等纯文本值全部不可点。
- **提升**｜点「京都动画」直接搜人物、点别名直接搜条目、点 ISBN 直接复制；工程上把「键 → 动作」做成纯函数表，新增键不用改 UI。
- **原理**｜一个纯函数 + 一行 chip：
  ```kotlin
  sealed interface InfoAction {
      data class Search(val query: String) : InfoAction
      data class Copy(val text: String) : InfoAction
      data class Open(val url: String) : InfoAction
  }
  fun actionsFor(key: String, value: String): List<InfoAction> = buildList {
      extractFirstUrl(value)?.let { add(InfoAction.Open(it)) }
      if (key.contains("别名") || key.contains("別名")) add(InfoAction.Search(value.firstField()))
      if (key.contains("原作") || key.contains("动画制作")) add(InfoAction.Search(value))
      if (key.contains("ISBN") || key.contains("ASIN")) add(InfoAction.Copy(value.trim()))
  }
  ```
  值用 `、`/`/` 切成最多 2 个 chip，避免一行挤爆（infobox 卡已限高 420dp + 内部滚动）。
- **效果**｜infobox 每行右侧出现小 chip（`搜「京都动画」`、`复制 ISBN`）；点搜索跳到搜索页并预填关键词；点复制弹 Snackbar「已复制」。
- **参考**｜`mihonapp/mihon@main:.../manga/components/MangaInfoHeader.kt` [码] 标签的 `onTagSearch` / `onCopyTagToClipboard` 双动作模式；`xiaoyvyv/bangumi@master:README.md` [README] 「演职员与关系网」。
- **成本/风险**｜S｜infobox 键是主源用户自由编辑的文本，匹配必须宽松且**永不报错**（匹配不到就维持纯文本）。
- **优先级**｜P1

### 6.12 关联条目的关系类型分组
- **现状**｜部分｜`ui/subject/RelationsSection.kt:64` 是单个 `LazyRow`，`itemsIndexed(relations)` 按 API 顺序铺；`SubjectRelationInfo` 自带 relation 文案但 UI 不分组、不排序。
- **提升**｜用户一眼分清「前传/续作/同系列/改编」；工程上纯 UI 排序 + 小标题，零数据层改动。
- **原理**｜纯函数分组 + 组内排序：
  ```kotlin
  private val PRIORITY = listOf("前传", "续作", "系列", "改编", "其他")
  fun groupRelations(rels: List<SubjectRelationInfo>): List<Pair<String, List<SubjectRelationInfo>>> =
      rels.groupBy { r -> PRIORITY.firstOrNull { r.relation.contains(it) } ?: "其他" }
          .toList().sortedBy { PRIORITY.indexOf(it.first) }
  ```
  每组一个 `Text(label)` + 自己的 `LazyRow`；只有一组时不渲染标题（保持今天的观感）。
- **效果**｜关联条目变成 2–4 个小节，标题右侧显示条数（`前传 · 2`）；点卡片仍走共享元素转场（`relationCoverKey` 不变）。
- **参考**｜`KotatsuApp/Kotatsu@master:.../details/data/MangaDetails.kt` [码] `chapters: Map<String?, List<MangaChapter>> by lazy` + `mergeChapters()`；`anitabi/anitabi-swift-app@main:.../Sheets/WorkPointList.swift` [码] `WorkPointGroupHeader` 分组头。
- **成本/风险**｜S｜relation 是自由文本，未知类型一律落「其他」且**不许丢条目**；音乐条目 subjectId 会重复（现有代码已为此用 index 做 key）。
- **优先级**｜P1

### 6.13 评分争议度解释
- **现状**｜部分｜`data/calculator/RatingInsights.kt` 有 `deviation()`（标准差）与 `disputeLabel()`（阈值 1.0/1.15/1.3/1.45/1.6/1.75）；`ui/subject/RatingComparisonSection.kt:120-122` 只在无障碍语义里 `add("评分争议度：$it")`，界面上没有任何口径说明。
- **提升**｜用户点「莫衷一是」能看懂「这是标准差、我在哪」；工程上把口径数据化，减少「这分数怎么算的」反复解释。
- **原理**｜解释模型 + 底部弹层：
  ```kotlin
  data class DisputeExplanation(
      val label: String, val deviation: Float,
      val thresholds: List<Pair<Float, String>>, val sampleSize: Int,
  )
  ```
  `ModalBottomSheet` 里放一条 0–2 σ 刻度尺，指针落 `deviation`，阈值刻线直接取 `disputeLabel` 的 when 分支常量（改阈值只改一处）；底部「样本 N 人（来自评分分布）」。
- **效果**｜争议度 chip 右出现 `info` 小图标；点开显示 `σ = 1.32 · 略有分歧 → 莫衷一是` 的刻度尺与一句解释；无分布数据时 chip 显示 `—` 且不可点。
- **参考**｜本项目 `data/calculator/RatingInsights.kt` [码]；外部**无源码级参考**（v1 的 R11 只到 README 级），本条以本项目纯函数为准。
- **成本/风险**｜S｜阈值常量要 export 给 UI（现在是 private 分支），避免两处漂移。
- **优先级**｜P2

### 6.14 详情自动重试匹配
- **现状**｜部分｜只有 Steam 独立条目会「打开即重试」：`SubjectDetailViewModel.kt:670 if (current.isSteamPlaceholder)` → `rematchPlaceholder()`（`:1192`）；VNDB/AniList 独立条目没有对应通路（`:953` 注释写明「仅 bangumi 词条触发」AniList 补充）。
- **提升**｜从 VNDB/AniList 搜进来的薄条目，等主源有词条后会自动升级成完整条目，用户不用手动再找；工程上把「占位升级」抽成一个共用 use case，三源一套逻辑。
- **原理**｜分派 + 节流 + 幂等写：
  ```kotlin
  class PlaceholderRematcher(private val matchers: Map<String, Matcher>) {
      suspend fun tryUpgrade(subject: SubjectEntity): Long? {
          val key = subject.sourceKey?.substringBefore(':') ?: return null
          if (throttled(subject.subjectId, key)) return null        // 24h 内不重试
          val hit = matchers[key]?.match(subject) ?: return null
          return bindAndRefresh(subject.subjectId, hit)             // 写绑定 + 覆盖 subjects + 失效缓存
      }
  }
  ```
  成功路径要 `detailCacheDao.invalidateDetail(subjectId, listOf(SourceKeys.VNDB/ANILIST/TMDb))`；失败静默（不弹错，只在刷新诊断里记一条）。
- **效果**｜打开薄条目时顶部一条细进度条（不遮内容）；升级成功后封面/简介/角色一次到位，Snackbar「已匹配到 Bangumi 词条」；24h 内再进不重试，无网络请求。
- **参考**｜`IgnisDa/ryot@main:apps/frontend/app/components/routes/media-item/modals/merge-metadata-modal.tsx` [码] `intent: "mergeMetadata"` + `mergeFrom/mergeInto` 双 ID 合并（页面明写不可逆）；`KotatsuApp/Kotatsu@master:.../details/domain/DetailsLoadUseCase.kt` [码] `mangaDataRepository.getOverride` + 本地/远端合并。
- **成本/风险**｜M｜自动改库必须幂等且不覆盖用户编辑（6.17 的 override 表落地后要先套用）；节流键随条目删除要能清理。
- **优先级**｜P1

### 6.15 外部源区块的可折叠与顺序（纠正：已纳入部件表）
- **现状**｜**已实现（v1 判「部分/可能未纳入」已过时）**｜`ui/subject/DetailSectionId.kt` 枚举含 `STEAM/ANILIST/VNDB/EXTERNAL_RATING/TMDB_BINDING`，顺序与隐藏统一走 `DetailLayoutPolicy`，且有 `DetailLayoutPolicyTest` 钉住默认顺序。真正缺的是**折叠**（现在只有「显示/隐藏」，隐藏后整块消失、无法「收起但留标题」）。
- **提升**｜从「要么全显示要么全隐藏」升级为「默认收起、点开再看」；工程上把 `collapsed` 与 `hidden` 分开存，不动用户已有设置。
- **原理**｜新设置 + 折叠壳：
  ```kotlin
  // 编码方式与 hidden 完全一致，可复用 encode/decode
  val collapsed: Set<DetailSectionId> = DetailLayoutPolicy.collapsedFromEncoded(settings.detailCollapsedSections)
  ```
  `detailPaneItem` 内包一层 `Collapsible(id)`：折叠时只渲染 48dp 标题行（标题 + 条数摘要 + 箭头），展开才组合内容——沿用「不组合 = 不发请求」的现有约定。
- **效果**｜Steam/AniList/VNDB/权威评分首次进入时只有一行（`Steam · 38 成就` / `AniList · 已绑定`），点箭头展开；折叠状态重启后保留；「详情部件」页多一列「默认收起」开关。
- **参考**｜`mihonapp/mihon@main:.../manga/components/MangaInfoHeader.kt` [码] `ExpandableMangaDescription` 的 `rememberSaveable { expanded }` + `MangaSummary` + `animateContentSize(spring())`；`anitabi/anitabi-swift-app@main:.../Sheets/WorkPointList.swift` [码] `WorkPointGroupHeader(onToggle:)`。
- **成本/风险**｜S｜现在这些区块的数据在 ViewModel 初始化时就开始拉，「折叠时不发请求」需要把 load 拆到「首次展开」事件上（可先只做视觉折叠，网络行为不变）。
- **优先级**｜P1

### 6.16 详情页分享内容可配置
- **现状**｜部分｜只有三个开关：`shareIncludeRating` / `shareIncludeProgress` / `shareIncludeTags`（`data/settings/AppSettings.kt:121-123`，`SettingsDataStore.kt:63-65`）；分享卡不含权威评分、外部评分、巡礼、我的短评。
- **提升**｜分享卡可选「带上我的短评/权威评分/巡礼进度」，卡片从「作品卡」变成「我的评价」；工程上 `ShareCardData` 的字段与开关一一对应，加字段零成本。
- **原理**｜配置驱动渲染（现有 `ShareCardLayout` 已是这个形态）：`ShareCardData` 增 `externalRating: String?`、`anitabiCount: Int?`、`impression: String?`；开关扩到 5 个 `shareInclude*`，新键默认 `false`（保证老用户卡片不变）；渲染器按开关测量高度，避免空白行。
- **效果**｜分享弹层多两个 checkbox；卡片上多一行 `Bangumi 8.4 · 豆瓣 8.1` 或底部一行小字短评；全关时卡片与今天逐像素一致。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/core/util/ShareHelper.kt` [码] 按载荷拆 `shareMangaLink`/`shareMangaLinks`/`shareCbz`/`shareImage`/`getShareTextIntent`，而不是一个万能开关。
- **成本/风险**｜S｜卡片高度是量出来的，多字段后要防「文字被裁」；开关与备份/同步的键名要一起迁移。
- **优先级**｜P2

### 6.17 无词条条目的详情兜底
- **现状**｜部分｜外部源已是「一等条目」（`sourceKey` 非空，如 `steam:{appid}`，见 `SubjectDetailViewModel.kt:659`），详情页能开但字段薄：封面可本地覆盖（`coverOverrideStore`），标题/简介/标签**无本地编辑**；`isSteamPlaceholder` 由 6.14 的自动匹配兜。
- **提升**｜用户能给没有 Bangumi 词条的作品补封面/简介/标签，让它不再残缺，且这些编辑不会被刷新覆盖；工程上把「本地覆盖」变成一等概念，同步/刷新/备份都能按它做取舍。
- **原理**｜一张覆盖表 + 一个纯合并函数：
  ```kotlin
  @Entity(tableName = "subject_overrides")
  data class SubjectOverrideEntity(
      @PrimaryKey val subjectId: Long,
      val title: String? = null, val description: String? = null,
      val tagsCsv: String? = null, val coverUrl: String? = null,
      val updatedAt: Long = 0L,
  )
  fun mergeDisplay(subject: SubjectEntity, o: SubjectOverrideEntity?) = subject.copy(
      title = o?.title ?: subject.title, description = o?.description ?: subject.description,
  )
  ```
  写回只走独立通道（不走 `SubjectWriteGateway`，避免与主源 diff 互相覆盖）；仅 `subject.sourceKey != null` 时开放编辑入口。
- **效果**｜薄条目详情页顶部出现「本地条目」小徽标；「扩展信息」区多「编辑本地信息」按钮，可改标题/简介/标签/封面；改完立刻生效且刷新不清空；有 Bangumi 词条时入口消失（防冲突）。
- **参考**｜`KotatsuApp/Kotatsu@master:app/src/main/kotlin/org/koitharu/kotatsu/core/ui/model/MangaOverride.kt` [码] `coverUrl/title/contentRating`；`KotatsuApp/Kotatsu@master:.../details/data/MangaDetails.kt` [码] `mergedManga` 按字段合并 override；`IgnisDa/ryot@main:crates/models/database/src/metadata.rs` [码] `is_partial: Option<bool>` + `source/identifier/source_url` 把「不完整条目」显式化。
- **成本/风险**｜L｜新表 + Room 33 迁移 + 备份 v5 + 与 WebDAV 同步的边界（建议先只本地，明确写进文档）；合并优先级一旦有歧义会出现「用户改了又被刷新改回去」。
- **优先级**｜P1

## 6.新增 本域新增候选（发散）

> 说明：模板里本节编号为「6.9」，但 6.9 已是既有条目，故用「6.新增」以免与条目混淆；新增条目编号从 6.18 起。

### 6.18 详情页「一屏摘要」卡
- **现状**｜无 · 新提出｜18 个部件顺序拼接，用户要滑很久才能拼出「这是什么、我看了多少」；`STAT_GRID` 只在部分类型出现。
- **提升**｜为什么是 Niriko：详情页是最高频落地页，而 `SubjectDetailUiState` 里评分/集数/状态/进度/时长都已就绪，做一个 4 数字条是零请求、零新表的纯 UI 收益。
- **原理**｜`COVER` 与 `TITLE` 之间插一个 `DetailSectionId.SUMMARY`（hideable=true）；数据用 `remember(state)` 从现有字段派生，缺项显示 `—`；横排 4 个 `StatCell`，窄屏 2×2。
- **效果**｜封面下方一条玻璃条：`8.4 评分 · 12 集 · 看到 5 · 还剩 3.5h`；无值项灰显而不是消失（保持四列稳定）。
- **参考**｜`mihonapp/mihon@main:.../manga/components/MangaInfoHeader.kt` [码] `MangaContentInfo(author, status, …)` 把散字段收成一行；`IgnisDa/ryot@main:crates/models/database/src/metadata.rs` [码] `provider_rating`/`publish_year`/`production_status` 同屏建模。
- **成本/风险**｜S｜别把已有 `STAT_GRID` 重复一遍；「还剩 3.5h」依赖 6.6 的时长回填，缺时长时只显示集数。
- **优先级**｜P2

### 6.19 详情页「来源与时间戳」脚注
- **现状**｜无 · 新提出｜项目 23 个数据源 + 4 类缓存，「这个评分/简介是哪来的、什么时候的」界面无法回答；`DetailCacheStore` 已存 `fetchedAt/errorSummary` 但 UI 不读。
- **提升**｜为什么是 Niriko：纯客户端没有服务端背书，信任只能靠 UI 自证；而且出错时用户能自己看出「原来是缓存」。
- **原理**｜`SourceStamp` 列表（见 6.7）+ 每张 `GlassSectionCard` 可选 `footnote` 参数；默认隐藏，**长按区块标题**才显示 10sp 脚注（避免每块都挂灰字）。
- **效果**｜长按「评分与分布」标题 → 卡片底部出现 `Bangumi · 2026-10-15 08:12 · 缓存`；失败过则显示 `上次失败：timeout · 08:20`。
- **参考**｜`mihonapp/mihon@main:.../manga/components/MangaInfoHeader.kt` [码] 用一行 `MangaContentInfo` 承载次要信息；`KotatsuApp/Kotatsu@master:.../details/data/MangaDetails.kt` [码] `isLoaded` 布尔即「数据到没到」的显式状态。
- **成本/风险**｜S｜时间格式化要跟系统时区；失败信息可能含 URL，复制前要脱敏。
- **优先级**｜P1

### 6.20 区块级深链
- **现状**｜无 · 新提出｜通知点进来只能到详情页顶部；用户想看「第 8 集评分」得自己滚；`NirikoNavHost` 的详情路由不接受 `section` 参数。
- **提升**｜为什么是 Niriko：`items(key = id.key)` 已经给每个部件稳定 key，深链只差一次 `scrollToItem`；通知/分享/日历三处入口都能受益。
- **原理**｜`niriko://subject/{id}?section=episodes_rating` → NavHost 解析 → 详情页 `LaunchedEffect(section)` 在 `orderedSections` 里 `indexOfFirst { it.key == section }` → `listState.animateScrollToItem(index)`（两栏时按 `pane` 选对应 state）。
- **效果**｜从放送通知进来直接停在「分集评分」；返回时详情页仍在该位置；无法识别的 section 退化为停在顶部。
- **参考**｜`KotatsuApp/Kotatsu@master:.../details/domain/DetailsLoadUseCase.kt` [码] `invoke(intent: MangaIntent, force)` 用 intent 决定打开对象（导航意图与加载策略解耦）；本项目 `navigation/NirikoNavHost.kt` + `SubjectDetailScreen.kt` 的 `item(key = id.key)` [码]。
- **成本/风险**｜S｜部件被用户隐藏时 index 不存在，需回退到相邻可见部件；深链要防越权打开未收藏条目（本来就允许，确认产品口径）。
- **优先级**｜P1

### 6.21 只刷新当前区块
- **现状**｜无 · 新提出｜下拉 = 整页刷新，`loadExtendedData` 里一次并发 12 路（`SubjectDetailViewModel.kt:438-530`）；`DetailCacheDao.invalidateDetail(subjectId, listOf(key))` 已支持按 key 失效，但 UI 没暴露。
- **提升**｜为什么是 Niriko：详情页是「多源聚合」页，单源失败/单源过期很常见；只刷一块能省下 11 次请求，也避免整页闪烁。
- **原理**｜区块标题长按菜单「只刷新这一块」 → 映射到 `sourceKey` 列表 → `invalidateDetail` → 调对应 `load*`（每个 load 已是独立 suspend 函数，只需传 `force=true` 的范围参数）。
- **效果**｜长按「权威评分」→「只刷新」→ 该卡右上转圈，其余不动；失败只在该卡显示「刷新失败 · 重试」。
- **参考**｜本项目 `data/local/dao/DetailCacheDao.kt` [码] `invalidateDetail(subjectId, sourceKeys)`（注释明写「只失效指定来源」）；`KotatsuApp/Kotatsu@master:.../details/domain/DetailsLoadUseCase.kt` [码] `invoke(intent, force)` 的 force 粒度。
- **成本/风险**｜S｜有些源是「一起取的」（集合三类），单刷要按组失效，否则出现半份列表（`DetailCacheStore.readCollections` 的条数校验会兜住）。
- **优先级**｜P1

### 6.22 详情页阅读位置记忆
- **现状**｜无 · 新提出｜详情页很长（`SubjectDetailScreen.kt` 4051 行），每次进来回到顶部；用户常看的区块固定（多半是分集评分/曲目）。
- **提升**｜为什么是 Niriko：这是「二次进入成本」问题，而项目已经把部件 ID 稳定化了，记一个 key 就够，不需要存像素偏移。
- **原理**｜离开时记 `firstVisibleItemIndex` 对应的 `DetailSectionId.key`（用 `listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key`），存 `DataStore` 的 `detailAnchor_{subjectId}`（或新表的 `anchorKey` 列）；进入时 `scrollToItem(indexOf(key))`，跳过「首屏封面」时给 300ms 一次性高亮。
- **效果**｜二次进详情直接落在上次位置；顶部浮出 1.5s 的「已回到分集评分」提示，点提示回顶部。
- **参考**｜`mihonapp/mihon@main:.../manga/components/MangaInfoHeader.kt` [码] 只把 `expanded` 存 `rememberSaveable`（进程内），说明「轻量状态持久化」的边界；本项目 `ui/subject/DetailRailState.kt` [码]（横滑轨道状态封装，可同法做纵向）。
- **成本/风险**｜S｜部件被隐藏/顺序变更后 key 可能失效，需回退到顶部；不要把「分享卡打开」也记忆（应回顶部）。
- **优先级**｜P2

### 6.23 元数据完整度体检
- **现状**｜无 · 新提出｜`RefreshDiagnosticsScreen` 只看刷新，没人告诉用户「这条作品缺封面/缺时长/缺简介」；缺字段会拖累统计与日历。
- **提升**｜为什么是 Niriko：多源聚合最容易「看起来有、其实缺一半」；给一条可执行的补全清单，比再做一个数据源更有用。
- **原理**｜纯函数评分：
  ```kotlin
  data class Completeness(val score: Int, val missing: List<String>)
  fun of(s: SubjectEntity, eps: List<EpisodeEntity>, info: List<InfoBoxEntry>): Completeness
  // 封面/简介/标签/集数/首播日/时长 各占权重，缺一项扣分并输出人话
  ```
  只在 `score < 100` 时渲染在详情页底部；「补全」按钮按缺失项给 2–3 个可执行动作（绑定 TMDb / 补时长 / 换封面）。
- **效果**｜详情页底部一行 `元数据完整度 78% · 缺 3 集首播日`；点开列出缺失项与对应按钮；100% 时整行不出现。
- **参考**｜`IgnisDa/ryot@main:crates/models/database/src/metadata.rs` [码] `is_partial`（把「不完整」变成显式字段而不是隐含状态）；本项目 `ui/settings/pages/RefreshDiagnosticsScreen.kt` [码]（已有「诊断」页形态可承载）。
- **成本/风险**｜S｜权重是主观的，文案要写「仅供参考」；别把「无时长」当成错误（很多老番本来没有）。
- **优先级**｜P2

### 6.24 详情页「同一制作班底」推荐
- **现状**｜无 · 新提出｜已有 `GUESS`（猜你喜欢，本地共现）在详情页；但「同监督/同公司的其它收藏」没有入口，尽管 `PersonCollectionEntity` + `PersonJobAnalyzer` 已在库里。
- **提升**｜为什么是 Niriko：这是纯本地 SQL 就能出的推荐，零网络、零新源，且比共现推荐更可解释（「因为同监督」）。
- **原理**｜从 Staff 取 top-2 关键职位（监督/系列构成/音乐），`person_collections` join 自己的收藏，排除当前条目与已出现过的 GUESS 项，取评分最高 6 条；结果按 `remember(subjectId)` 缓存，避免每次重组重算。
- **效果**｜详情页多一张「同一制作班底」，每卡角标写「同监督 · 山田尚子」；无命中时整块不出现。
- **参考**｜`KotatsuApp/Kotatsu@master:.../details/domain/RelatedMangaUseCase.kt` [码]（详情页「相关作品」独立 use case 的分层方式）；本项目 `data/calculator/PersonJobAnalyzer.kt` [码]。
- **成本/风险**｜S｜同一人可能挂多职，要先归一；冷启动库小的时候容易空。
- **优先级**｜P2

## 6.A 本域新增参考库

| 仓库 | 许可 | star（截至 2026-10） | 语言 | 为何与本项目相关 | 已验证文件 |
| --- | --- | --- | --- | --- | --- |
| `mihonapp/mihon` | Apache-2.0 | 24k | Kotlin | 详情页信息密度（Small/Large 双实现）、展开式简介、独立封面缓存、onboarding 步进模型——本域 6.4/6.8/6.10/6.11/6.15/11.10 的主要范式来源 | `app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt` · `.../presentation/manga/components/MangaInfoHeader.kt` · `.../data/cache/CoverCache.kt` · `.../presentation/more/onboarding/OnboardingScreen.kt` |
| `KotatsuApp/Kotatsu` | GPL-3.0 | 8.9k | Kotlin | 详情页「本地优先 + 渐进 Flow」、TAB 适配器、本地覆盖字段（`MangaOverride`）、按载荷拆分的分享、错误详情复制 | `app/src/main/kotlin/org/koitharu/kotatsu/details/domain/DetailsLoadUseCase.kt` · `.../details/data/MangaDetails.kt` · `.../details/ui/pager/ChaptersPagesAdapter.kt` · `.../core/ui/model/MangaOverride.kt` · `.../core/util/ShareHelper.kt` · `.../core/ui/dialog/ErrorDetailsDialog.kt` |
| `IgnisDa/ryot` | 未取到 | 未取到（badge 返回 invalid） | Rust + TypeScript | 「不完整条目」显式化（`is_partial`）、跨媒体元数据分桶、metadata 合并动作——6.5/6.6/6.17/6.23 的建模参考 | `crates/models/database/src/metadata.rs` · `apps/frontend/app/components/routes/media-item/modals/merge-metadata-modal.tsx` |
| `xiaoyvyv/bangumi` | GPL-3.0（README badge） | 1.1k | Kotlin（KMP + CMP） | 同为 Bangumi 客户端：详情信息架构、衍生资源聚合、巡礼地图入口、跨端分层 | `README.md`（模块树与特性列表；源码树 >50MB，jsDelivr flat 不可用，**未读源码**） |
| `anitabi/anitabi-swift-app` | 未取到 | 未取到（badge invalid） | Swift/SwiftUI | 巡礼地图的相机命令状态机与按 zoom 分级的地标密度——6.1/6.2/6.12 的直接范本 | `anitabi/Map/MapCanvas.swift` · `anitabi/Map/MapEngine.swift` · `anitabi/Sheets/WorkPointList.swift` |
| `anitabi/image-merge` | 未取到 | 未取到 | 未知 | 6.3 对比图功能的同名参考（v1 沿用，**未取到源码**） | 无（[推断]） |

## 计数自检

- v1 条目：17 条（6.1–6.17），本文件逐条详解 **17** 条，编号与名称未改。
- 新增候选：**7** 条（6.18–6.24）。
- 汇总表行数：17 + 7 = **24** 行，与详解条数一致。
- 需要纠正的 v1 判断：**6.15**（外部源已在部件表，非「部分未纳入」）；**6.8**（建议关闭，只保留复制）。
- 未能取证、标 `待核` 的地方：6.1 的带坐标端点；6.13 的外部源码级参考；6.3 的 `anitabi/image-merge`（未取到源码）。
- 未取到的 star：`IgnisDa/ryot`、`anitabi/anitabi-swift-app`（shields badge 返回 `invalid`，未编造）。
