# Niriko 计划落实 · 实施计划（含本轮新功能）

> 依据：`docs/niriko-plan-wireframes.html`（本轮线框图 + 11 条决策结论）、`README.md`（功能总览 / 已知限制与后续计划 / 开发约定）、`docs/remaining-phases.md`、`docs/ui-redesign-plan.md`、`docs/portal-feature-plan.md`。
> 本文把线框图里的清单**落实为可执行计划**：批次、落点、验收、单测、风险。
> **范围纪律**：本文是唯一范围来源。清单里没有的改动属于新增需求，须先补进本文再动工。
> **基线门槛**：每批次开始前先跑 `assembleDebug` + `testDebugUnitTest`，全绿才继续；每批次结束同样要全绿。

---

## 〇、范围总览

| 类别 | 条目 | 处理方式 |
| --- | --- | --- |
| 本轮新功能 | 0-2 首次启动引导（TMDb Key）、2-2 长按浮层进度 ±、2-5 放送通知多条合并 | B1 实施 |
| 行为变更（已确认） | 0-6 相册权限时机、1-4 批量删除二次确认、4-6 Kazumi 换系统文件选择器、4-10 恢复冲突本地优先 | B2 实施 |
| 已决策·不改动 | 0-4 通知被拒不做二次引导、1-8 筛选面板保持内联可折叠、1-17 门户包名不补、1-18 音乐 scheme 暂不做真机验证 | 仅记录，见 §四 |
| 可视化与体验债 | 3-2 图表配色派生、3-3 图表容器、3-4 排版层级、4-13 豆瓣防剧透、4-14 TMDb 季号 | B3 实施 |
| 数据与同步债 | 4-8 WebDAV 纳入新增数据、4-12 按源限流闸门 | B4 实施 |
| 工程与质量债 | 6-2 Room 迁移测试、6-3 补 v25 schema、6-4 ExtendedSnapshot、6-5 release 签名与 CI | B5 实施 |
| 暂缓 | 6-1 平板/折叠屏、7-9 SMB 本地媒体、7-10 年鉴/VIB/目录、7-11 社区只读 | §7 记录，不做 |
| 参考项目建议（未批准） | 上一轮 10 个 GitHub 参考项目 | §8 待单独确认 |

---

## 一、B0 基线（动工前）

1. 跑 `assembleDebug` + `testDebugUnitTest`（当前 436 个 JVM 用例），全绿留档。
2. 真机确认当前行为基线：长按卡片浮层、通知逐条弹出、Kazumi 导入路径、JSON 恢复表现。
3. 记录本批次要动的文件清单（见各条目「落点」），避免并行改动冲突：B1 与 B2 都动 `ui/screens/Screens.kt`，**必须串行**。

---

## 二、B1 本轮新功能（3 项）

### B1-1 · 0-2 首次启动引导：TMDb API Key 设置页

**目标**：首次安装启动后进入单页引导，只要求填 TMDb API Key；**每一页都提供「跳过」**；保存或跳过后进入主界面，之后不再出现。补填入口保留在设置里。

**落点**

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 新页面 | `ui/onboarding/FirstRunScreen.kt`（新增） | 一页：说明 + TMDb Key 输入 + 「保存并继续」/「跳过」（跳过放右上角）；视觉复用 `ui/settings/SettingsItem.kt`、`SettingsGroup.kt` |
| 首启标记 | `data/settings/AppSettings.kt` | 新增 `firstRunCompleted: Boolean`；`data/settings/SettingsDataStore.kt` 增加对应 Preferences key 与读写映射 |
| 入口判定 | `MainActivity.kt` | 首帧判定是否展示引导（该文件已有 settings 的 LaunchedEffect，可同处决策）；引导期间保持开屏，避免闪主界面 |
| 路由 | `navigation/NirikoNavHost.kt` | 新增 `onboarding` 路由或顶层条件分支 |
| Key 字段 | `data/settings/AppSettings.kt` 已有 `tmdbApiKey` | **复用**，不新增字段；可选同时暴露 `tmdbApiUrl` / `tmdbImageUrl`（国区镜像必需） |
| 补填入口 | `ui/settings/pages/DataSourceSettingsScreen.kt` → `ui/settings/RatingSourceSettingsSection.kt` | 已存在 TMDb Key / API / 图片域 三行，无需新增，仅确认可达 |

**验收**

1. 全新安装 → 首屏是引导页；填 Key 保存 → 进主界面，`tmdbApiKey` 已持久化，详情页剧照可用。
2. 点「跳过」→ 不写 Key、不进设置页，直接进主界面；再次启动不再出现引导。
3. 升级安装（已有数据）→ **不出现引导**（见风险 1）。
4. 引导页每页都有「跳过」（当前只有一页，规则先立着，后续加页自动继承）。

**单测**：把「是否需要展示引导」抽成不依赖 Android 的纯函数（输入：设置键集合 / `firstRunCompleted`；输出：是否展示），覆盖：全新安装、老用户升级、已完成后再次启动。

**风险**

1. **老用户被引导拦截**：`firstRunCompleted` 新键默认 `false` 会让升级用户也进引导。实现规则：该键缺失 **且** DataStore 里已存在任意历史设置键 → 视为已完成。此规则需你确认（§十一 Q1）。
2. 首帧闪烁：DataStore 读取是挂起操作，需要在开屏期间完成判定。

### B1-2 · 2-2 长按浮层内进度 ±（新增功能）

**目标**：作品库长按卡片时，浮层除改状态外，可直接对该作品进度做 − / + 快捷调整。

**落点**

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 浮层 UI | `ui/screens/Screens.kt`（长按菜单，约 398–420 行） | 由「状态列表」扩为「状态 + 进度 `7 / 12 集` [−][+]」 |
| 卡片入口 | `ui/library/CollectionCard.kt`（`onLongClick`）、`ui/subject/UniversalSubjectCard.kt`（`combinedClickable`） | 传参不变，浮层内容变化 |
| 写库 | `viewmodel/CollectionViewModel.kt` | 新增 `bumpProgress(subjectId, delta)`，复用现有收藏写库路径（同 `batchUpdateStatus` 的调用面） |
| 进度语义 | 收藏模型（动画/三次元=集、书籍/漫画=卷、游戏=分钟） | 按类型取单位与总集数，与现有卡片进度条一致 |

**验收**

1. 长按卡片 → 浮层显示当前进度 `7 / 12 集`，点 `+` 立即写库（不弹确认），卡片进度条同步。
2. 下限 0、上限 = 已知总集数；总集数未知时不设上限。
3. 游戏显示小时/分钟与现状一致；书籍显示卷。

**单测**：进度边界纯函数（0 下限、总集数上限、总集数未知、按类型的单位换算）。
**待确认**：进度加到总集数时是否自动切「看过」（默认：不自动，见 §十一 Q2）。

### B1-3 · 2-5 放送提醒通知多条合并（新增功能）

**目标**：同一天命中的多部「在看」作品合并为一条通知；**不做免打扰时段**。

**落点**

| 位置 | 文件 | 改动 |
| --- | --- | --- |
| 通知构造 | `data/notification/AiringReminderWorker.kt` | 当前逐条发通知 → 改为先收集今日命中列表，再统一构造 |
| 文案与合并 | 新增纯函数（建议 `data/notification/AiringNotificationComposer.kt`） | 1 条 = 现有文案「《作品名》今日更新」+「今日放送，记得观看」；≥2 条 = 「《A》《B》等 N 部今日更新」；通知 id 固定（同日合并覆盖） |
| 调度 | `data/notification/AiringReminderScheduler.kt` | 不需要改周期，仅确认合并后 id 稳定 |
| 开关 | `AppSettings.airingReminderEnabled`（已有） | 不变；免打扰不实现 |

**验收**

1. 模拟 3 部作品今日放送 → 通知栏只出现 **1** 条，文案含 3 部作品；单部作品时文案与现状一致。
2. 关闭开关 → 不产生任何通知（现状保持）。
3. 未授权通知权限（API 33+）→ 静默跳过（现状保持）。

**单测**：composer 纯函数覆盖 0 / 1 / 2 / 3 条、标题超长截断、同日重复触发幂等。
**待确认**：合并通知点击后的落点（见 §十一 Q3）。

---

## 三、B2 行为变更（4 项）

### B2-1 · 0-6 相册权限时机：初次使用保存功能时申请

**落点**：`ui/common/ImageViewer.kt`（保存到相册）+ `app/src/main/AndroidManifest.xml`。

**实现要点**

- 按平台规则分级：**API 29+（含 33/34）** 走 MediaStore 写入相册**无需运行时权限**；**API 26–28** 需要 `WRITE_EXTERNAL_STORAGE`。当前 manifest 未声明该权限，如需支持 26–28 则补声明（加 `android:maxSdkVersion="28"`）并在**首次点击保存时**申请。
- 权限请求放在保存动作里（`rememberLauncherForActivityResult`），不在进入查看器时申请。
- 拒绝后：不保存、不崩溃、**不做二次引导**（与 0-4 的决策一致），用户可再次点击时再触发。

**验收**：API 29+ 点保存直接成功且无弹窗；API 26–28 首次点保存才弹权限；拒绝后重复点击行为稳定（不无限弹）。

### B2-2 · 1-4 批量删除二次确认

**落点**：`ui/screens/Screens.kt`（批量栏 `onBatchDelete` 调用点，约 110 行）、`viewmodel/CollectionViewModel.kt`（`batchDelete`，约 158 行）、新增确认对话框组件放 `ui/components/`（与现有对话框风格一致）。

**验收**：多选 → 删除 → 弹确认（说明将删除 N 项、不可撤销）→ 取消不删；确认后才删；删除完成退出多选，列表即时刷新。**单测**：无（UI 层），但数量与文案来自 state，保持可测。

### B2-3 · 4-6 Kazumi 导入改用系统文件选择器

**落点**：`ui/settings/KazumiImportSection.kt`、`plugin/kazumi/KazumiImporter.kt`、`plugin/kazumi/KazumiHiveReader.kt`。

**实现要点（主要工作量在读取层）**：用 `ActivityResultContracts.OpenDocument`（SAF）拿 `content://` URI；**hive 读取目前若依赖文件路径，需要改成 `InputStream`**，否则 SAF URI 无法直接读。选择器 MIME 建议 `*/*`（.hive 无标准 MIME）。

**验收**：能选中 collectibles.hive；选中后出现预览列表；取消选择不报错、不崩溃；导入行为（仅动画、默认跳过已存在）与现状一致。**单测**：若解析改为流式，补解析器单测。

### B2-4 · 4-10 JSON 恢复：冲突则本地优先

**落点**：`data/backup/BackupManager.kt`、`viewmodel/BackupViewModel.kt`、`ui/settings/pages/SyncBackupSettingsScreen.kt`。

**实现要点**：恢复前二次确认；合并策略 = 本地已有记录（同一身份键）**保留本地值**，备份中本地没有的记录**并入**；私密收藏仍不进导出（现状）。

**验收**：造「本地已改评分 + 备份里是旧评分」→ 恢复后本地评分不变；备份里多出的新作品被并入；确认前取消 → 无任何写入。**单测**：合并策略纯函数（本地优先 / 缺失并入 / 空备份 / 空本地）。

---

## 四、已决策·不改动（4 项，仅记录）

| 编号 | 决策 | 说明 |
| --- | --- | --- |
| 0-4 | 通知被拒后不做二次引导 | 拒绝权限后不在应用内提示、不弹引导；设置页开关项保留 |
| 1-8 | 筛选面板保持现有形态 | 历史排名继续用内联可折叠面板（默认收起），不改弹出式 sheet |
| 1-17 | VeneraNext / Danotsu 包名暂不补 | 无网络环境无法确认包名，保持未接入，避免错误包名导致静默隐藏 |
| 1-18 | 网易云 / QQ 音乐 scheme 暂不做真机验证 | 继续走现有三级降级（失效自动网页兜底） |

---

## 五、B3 可视化与体验债

| 编号 | 内容 | 落点 | 验收 |
| --- | --- | --- | --- |
| 3-2 | 图表配色从主题派生（现状硬编码 `Color(0xFF…)`，换主题色图表不变） | `ui/stats/StatsScreen.kt`（约 85–105 行）、`ui/stats/StatsOverviewSection.kt`（约 71–76 行，浅粉彩硬编码 + emoji 图标） | 切换主题色 / 动态取色 / OLED 后，状态色与图表色随之变化；深色模式无浅色补丁 |
| 3-3 | 图表加卡片容器 | `ui/stats/StatsScreen.kt` 图表区块 | 环形图/柱状图不再直接压在壁纸上，壁纸文字不穿过刻度 |
| 3-4 | 排版层级 + 数字 tabular figures | `ui/theme/Type.kt`（当前只覆写 4 个样式） | 列表卡标题/原名/简介有层级差；统计数字刷新不跳动 |
| 4-13 | 豆瓣防剧透翻页参数启用 | 豆瓣链路实现处 | 参数生效，失败静默降级不影响其它剧照源 |
| 4-14 | TMDb 多季手动指定季号 | TMDb 详情/剧集侧 | 可手动指定季号并即时重取 |
| 1-7 | 评分区间 / 人数区间服务端化 | 检索通路 | **已确认暂缓**：Bangumi 检索通路未暴露这两个参数，保持候选池内本地过滤（见 §十一） |

---

## 六、B4 数据与同步债

| 编号 | 内容 | 落点 | 验收 |
| --- | --- | --- | --- |
| 4-8 | WebDAV 纳入 `subject_external_ids` / `episode_my_ratings` / `manual_awards` / 封面覆盖 | `data/sync/SyncManager.kt`、`data/sync/WebDavClient.kt`、`data/backup/BackupManager.kt` | 双端同步后上述数据一致；LWW 与串行互斥不受影响 |
| 4-12 | 按源限流闸门（IGDB 4 req/s、MusicBrainz 1 req/s、VNDB 200 次 / 5 分钟） | 对应 Remote 客户端或 OkHttp 层 | 压测不超限；单源失败不影响其它源 |

---

## 七、B5 工程与质量债

| 编号 | 内容 | 落点 | 验收 |
| --- | --- | --- | --- |
| 6-3 | 补 `app/schemas/.../25.json` | `app/schemas/com.otakup.niriko.data.local.NirikoDatabase/` | 迁移链 24→25→26 有据可查 |
| 6-2 | Room 迁移测试（v2→v29） | 新增测试源集（Room testing / Robolectric） | 每个版本都能开库；DDL 与 schema 导出逐字一致 |
| 6-4 | `ExtendedSnapshot` 补全新字段 | `viewmodel/SubjectDetailViewModel.kt` | 二次进详情页不重跑外部源 / 剧照候选 |
| 6-5 | release 签名 + CI | `app/build.gradle.kts`、`.github/workflows/` | `assembleRelease` 出签名包；CI 跑 `assembleDebug` + `testDebugUnitTest` |

---

## 八、暂缓项（本轮不做，仅记录）

| 编号 | 内容 | 暂缓依据 |
| --- | --- | --- |
| 6-1 | 平板 / 折叠屏自适应布局（导航栏 + 详情双栏） | `docs/fix-and-redesign-plan-round6.md` §8.3.6「本轮不做，只记录」；README 工程待办 |
| 7-9 | 本地媒体文件夹（SMB） | `docs/bangumi-reference-plan.md` 阶段 I，P2 暂缓 |
| 7-10 | 年鉴 / 评分月刊 VIB / 目录 | `docs/bangumi-reference-plan.md` §0，非官方稳定 API |
| 1-7 | 评分区间 / 人数区间服务端化 | 外部依赖：Bangumi 检索通路未暴露参数（本轮确认暂缓） |
| 7-11 | 社区只读（超展开 / 小组） | `docs/bangumi-reference-plan.md` 阶段 J，保持收藏统计定位 |

---

## 九、参考项目建议（未批准，需单独确认）

| 参考项目 | 拟用途 | 许可 / 门槛 |
| --- | --- | --- |
| xiaoyvyv/bangumi | 同域 KMP 客户端对照（功能 / 模块化 / iOS 移植先例） | GPL-3.0：只读思路，不抄代码 |
| axiel7/AniHyou-android | AniList 客户端：GraphQL 组织、分页缓存、多模块 | GPL-3.0：只读思路 |
| patrykandpatrick/vico | 替换统计页手写 Canvas 图表（3-3 相关） | Apache-2.0：可直接依赖 |
| kizitonwose/Calendar | 作品日历月/周视图 | MIT：可直接依赖 |
| android/nowinandroid | 离线优先数据层、Room 迁移测试（6-2）、基准与截图测试 | Apache-2.0 |
| Calvin-LL/Reorderable | 作品库自定义拖拽排序（新需求，需先立项） | Apache-2.0 |
| valentinilk/compose-shimmer | 加载态骨架屏 | Apache-2.0 |
| takahirom/roborazzi | JVM 截图测试（与 6-5 CI 合并做） | Apache-2.0 |
| Kyant0/AndroidLiquidGlass（backdrop 上游） | 移植 LiquidBottomTabs 等组件 | Apache-2.0；2.0.1 需 AGP 9.1，当前不可升级 |
| compose-miuix-ui/miuix | squircle / shader / preference 等模块 | Apache-2.0；main 需 Kotlin 2.4.20，当前只能读源码移植 |

> 说明：`skydoves/Cloudy` 已在 `gradle/libs.versions.toml` 声明（`cloudy = "1.0.0-alpha01"`）但 `app/build.gradle.kts` 未引用；若要用其 CPU 回退做玻璃降级，先补依赖声明，不要新增库。

---

## 十、实施顺序、单测与门槛

### 批次与依赖

| 批次 | 内容 | 依赖 | 门槛 |
| --- | --- | --- | --- |
| **B0 基线** | 构建 + 单测全绿，记录现状 | — | 全绿 |
| **B1 新功能** | B1-1 引导页、B1-2 进度 ±、B1-3 通知合并 | B0 | 全绿 + 真机验证（引导只出现一次、通知只出现一条） |
| **B2 行为变更** | 相册权限、删除确认、Kazumi SAF、恢复本地优先 | B1（同动 `ui/screens/Screens.kt`，串行） | 全绿 + 真机开关验证 |
| **B3 可视化债** | 3-2 / 3-3 / 3-4 / 4-13 / 4-14；1-7 视服务端支持 | 可与 B2 并行（不同文件） | 全绿 + 截图对照 |
| **B4 数据同步债** | 4-8 / 4-12 | B0 | 全绿 + 双端同步验证 |
| **B5 工程债** | 6-3 → 6-2 → 6-4 → 6-5 | B0 | 全绿 + CI 通过 |

### 新增单测清单（按仓库约定：新增逻辑必须补 JVM 单测）

| 覆盖对象 | 用例要点 |
| --- | --- |
| 首启判定（B1-1） | 全新安装 / 老用户升级 / 已完成后再启动 |
| 进度 ±（B1-2） | 0 下限、总集数上限、总集数未知、按类型单位换算 |
| 通知合并（B1-3） | 0 / 1 / 2 / 3 条命中、超长标题截断、同日重复触发幂等 |
| 恢复合并（B2-4） | 本地优先、缺失并入、空备份、空本地 |

### 回滚

每个批次单独提交；B1-1 的引导可整体回滚（删路由 + 首启判定），B1-3 的通知合并可回退为逐条发送，B2 四项均可独立回退，互不阻塞。

---

## 十一、本轮决策落实（Q1–Q3 已确认）

| 编号 | 结论 | 落地位置 |
| --- | --- | --- |
| Q1 老用户判定 | **视为已完成**：引导键缺失且 DataStore 已有任意历史设置键 → 不展示引导，老用户不被拦截 | `data/settings/FirstRunPolicy.kt`、`SettingsDataStore.hasStoredSettings()` |
| Q2 进度到顶 | **不自动**切「看过」：只把进度写到总集数上限 | `util/ProgressBumpPolicy.next()` |
| Q3 合并通知落点 | 打开**作品库「在看」列表** | `data/notification/AiringListFilterRequest.kt` + `MainActivity` / `LibraryScreen` |
| 外部依赖 1-7 | **暂缓**：Bangumi 检索通路未暴露评分区间 / 评分人数区间参数，保持候选池内本地过滤 | 见 §八 暂缓项 |

> 1-7 已按此结论从 B3 移入暂缓，B3 不再包含该项。

---

## 十二、B1 实施记录（三项新功能）

动工前基线：`assembleDebug` + `testDebugUnitTest` 全绿（门槛见 §十）。

**验收结果（本轮已跑）**

| 项 | 结果 |
| --- | --- |
| `assembleDebug` | BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 28MB） |
| `testDebugUnitTest` | BUILD SUCCESSFUL，**460 个用例全绿**（原 436 + 本轮新增 24），0 失败 |
| 新增用例 | FirstRunPolicyTest 4 · ProgressBumpPolicyTest 13 · AiringNotificationComposerTest 7 |
| 环境 | JDK 用 Android Studio 自带 JBR（21）；本机 PATH 上是 Java 8，直接跑会报「Dependency requires at least JVM runtime version 11」 |

> 注：`assembleRelease` 仍未配置签名（见 §七 6-5），本轮未动。

| 编号 | 新增 / 修改文件 | 说明 |
| --- | --- | --- |
| 0-2 | **新增** `data/settings/FirstRunPolicy.kt`、`ui/onboarding/FirstRunScreen.kt`；**修改** `data/settings/AppSettings.kt`、`data/settings/SettingsDataStore.kt`、`MainActivity.kt` | 首启单页只填 TMDb API Key，右上角与底部各一个「跳过」；走完或跳过后写 `first_run_completed`，不再出现 |
| 2-2 | **新增** `util/ProgressBumpPolicy.kt`；**修改** `viewmodel/CollectionViewModel.kt`、`ui/screens/Screens.kt`、`ui/library/PosterGridCard.kt` | 长按浮层新增进度 − / +；抽成 `LibraryLongPressMenu` 供列表与网格共用；单位按类型（集 / 卷 / 分钟） |
| 2-5 | **新增** `data/notification/AiringNotificationComposer.kt`、`data/notification/AiringListFilterRequest.kt`；**修改** `data/notification/AiringReminderWorker.kt`、`AiringReminderScheduler.kt`、`MainActivity.kt`、`ui/screens/Screens.kt` | 同一天多条合并为一条（固定通知 id），单条保持原文案与直达详情；合并通知点击 → 作品库「在看」筛选 |

**新增单测**（JVM，无需设备）

- `app/src/test/java/com/otakup/niriko/data/settings/FirstRunPolicyTest.kt`（含「升级安装不展示引导」回归）
- `app/src/test/java/com/otakup/niriko/util/ProgressBumpPolicyTest.kt`（单位判定 / 上下限 / 文案）
- `app/src/test/java/com/otakup/niriko/data/notification/AiringNotificationComposerTest.kt`（0 / 1 / 2 / 3 条、超长标题降级、时间格式）

**本轮已知缺口（未做，等确认再排）**

1. 2-2 长按入口覆盖 **列表 + 网格**；**画廊大卡视图暂无长按**（`LibraryGalleryCard` 目前没有 `onLongClick`，加它属于额外改动）。
2. 2-5 合并通知点击落到「在看」列表**并按该筛选呈现**；「定位到今日放送的具体条目」需要作品库支持按放送排序，本轮未做。
3. 0-2 引导页只暴露 TMDb API Key；国区镜像地址（`tmdbApiUrl` / `tmdbImageUrl`）仍只在「设置 → 数据源与账号」里配置（与决策原文一致）。

---

## 十三、B2 实施记录（四项行为变更）

**验收结果**

| 项 | 结果 |
| --- | --- |
| `assembleDebug` | BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 28MB） |
| `testDebugUnitTest` | BUILD SUCCESSFUL，**467 个用例全绿**（B1 后的 460 + 本轮新增 7），0 失败，54 个测试类 |
| 新增用例 | `RestoreMergePolicyTest` 7 例 |
| 环境 | 同 B1：JDK 用 Android Studio 自带 JBR（21）；本机 PATH 上的 Java 8 会直接失败 |

| 编号 | 文件 | 说明 |
| --- | --- | --- |
| 0-6 相册权限时机 | 修改 `app/src/main/AndroidManifest.xml`、`ui/common/ImageViewer.kt` | Manifest 增加 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion=28`）；**API ≤ 28 时在初次点击「保存到相册」才申请**；API 29+ 走 MediaStore 分区存储直接写入、不申请；拒绝后不做二次引导（与 0-4 的决策一致），再次点击仍会走系统询问 |
| 1-4 批量删除二次确认 | 修改 `ui/screens/Screens.kt` | 删除 Chip 改为先弹 `AlertDialog`（显示「将删除所选 N 个收藏（含进度与评分），删除后不可撤销」），确认后才调用 `batchDelete` 并退出多选 |
| 4-6 Kazumi 文件选择 | **无需改动（已满足）** | 现有实现已是 `ActivityResultContracts.OpenDocument()` + `contentResolver.openInputStream(uri).readBytes()`，`KazumiHiveReader` 吃 `ByteArray` —— SAF 通路天然满足「系统文件选择器」的决策。计划里「reader 依赖文件路径、需要改成流式」的判断与实际不符，此处更正 |
| 4-10 恢复冲突本地优先 | 新增 `data/backup/RestoreMergePolicy.kt`；修改 `data/backup/BackupManager.kt`、`ui/settings/pages/SyncBackupSettingsScreen.kt` | 合并模式先按业务主键剔除「本地已有」的备份记录（subjects / collections / workItems / searchHistory / personCollections / externalIds / myEpisodeRatings / manualAwards / 封面覆盖），只并入本地没有的；设置页改用 `merge = true`，确认弹窗文案同步改成「冲突以本地为准」 |

**语义变更提醒（重要）**

4-10 落地后，「从备份恢复」**不再具备回滚能力**：本地已有记录一律保留，备份里的旧值不会覆盖回来；要回滚某条记录，需先删除本地记录再恢复。全覆盖模式（`merge = false`）仍保留在 [BackupManager] 内部，但设置页不再暴露（决策就是「本地优先」，如需一个「我真的要覆盖」开关，需另行确认）。

**B2 未做**

1. 「完全覆盖」入口未在 UI 暴露（见上）。
2. API ≤ 28 的权限流程只有真机能验（单测与模拟器覆盖不到），本轮只保证编译通过与逻辑正确。
3. 恢复合并只覆盖设置页的 JSON 恢复；WebDAV 同步走的是独立的 `SyncManager`，不受本次改动影响。

---

## 十四、B3 实施记录（可视化与体验债）

**验收结果**

| 项 | 结果 |
| --- | --- |
| `assembleDebug` | BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 28MB） |
| `testDebugUnitTest` | BUILD SUCCESSFUL，**470 个用例全绿**（B2 后 467 + 本轮新增 3），0 失败，55 个测试类 |

**先更正：3-2 / 3-3 / 3-4 已在代码里落地，本计划的「现状」描述是改造前的旧状态**

| 编号 | 实际状态 | 证据 |
| --- | --- | --- |
| 3-2 图表配色从主题派生 | ✅ 已实现 | `ui/theme/ChartPalette.kt` 存在并有 `chartStatusColor / chartTypeColor / chartBarDefaultColor / chartBarAccentColor`（HCT 从 `colorScheme.primary` 派生 6 色，深浅两套 tone）；`StatsScreen` 与 `StatsOverviewSection` 已改为调用它 |
| 3-3 图表加卡片容器 | ✅ 已实现 | `StatsScreen.kt` 的 `StatsSectionCard`（`appleGlassCard`）+ `CalendarCard` / `TimelineStackCard` 均已容器化，图表不再直接压壁纸 |
| 3-4 排版层级 + tabular | ✅ 已实现 | `ui/theme/Type.kt` 已按 §5 全量展开（displayLarge 56sp/w700/`fontFeatureSettings = "tnum"` 等）；`UniversalSubjectCard` 已改为 titleSmall / bodySmall / labelSmall 分层（不再全 Bold）；`StatsOverviewSection` 的 emoji 图标已换 Material Icons |

**本轮实际改动**

| 编号 | 文件 | 说明 |
| --- | --- | --- |
| 4-14 TMDb 多季手动指定季号 | 修改 `data/repository/ExternalIdRepository.kt`（新增 `updateSubKey`）、`viewmodel/SubjectDetailViewModel.kt`（新增 `setTmdbSeason`）、`ui/subject/TmdbBindingSection.kt`、`ui/subject/SubjectDetailScreen.kt` | 绑定区块的展开详情里新增「季号」行 + 季选择 Chip（显示「第 N 季（M 集）」）；选中后只改绑定的 `subKey`（保留外部 ID / 标题快照 / 置信度 / 绑定方式 / 绑定时间），再重取 TMDb 详情与每集评分。此前多季作品只能按年份 + 集数自动挑季，挑错没有出口 |

**新增单测**

- `app/src/test/java/com/otakup/niriko/data/repository/ExternalIdRepositorySubKeyTest.kt`（3 例）：改季号**不冲掉**其余绑定字段（用 `bind()` 重写就会冲掉，这正是本实现要避免的回归）、绑定不存在时为空操作、传 null 可清回自动挑季

**4-13 豆瓣「防剧透翻页」→ 待确认（不自行补全）**

参数侧已就绪（`DoubanClient` 的 photos 端点带 `start`，注释写明「防剧透时用靠后的位置」），但**触发条件在任何文档里都没有定义**：是用户开关、按观看进度自动、还是始终取后页？三种选择对用户看到的剧照影响完全不同，因此本轮不动代码，等确认后再落。

**B3 残留（记录，未做）**

1. 日历的作品类型色仍是 `CalendarCard` 里的硬编码 `CalendarTypeColors`（与统计页的 `chartTypeColor` 是两套映射）。迁移的障碍是这些颜色在 Canvas `DrawScope`（`drawPath(path, color = …)`）里使用，属于非 Composable 上下文，需要先把色板提升到组合函数再传入——属于额外重构，本轮按「不改超出落点的文件」处理，记录待排。
2. 3-4 的 tabular figures 目前只在 `displayLarge`（统计大数字）显式启用；其余数字展示沿用 `CollectionDashboard` 那样的调用点级 `tnum` 写法。

---

## 十五、B4 实施记录（数据与同步债 + 4-13）

**验收结果**

| 项 | 结果 |
| --- | --- |
| `assembleDebug` | BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 28MB） |
| `testDebugUnitTest` | BUILD SUCCESSFUL，**488 个用例全绿**（B3 后 473 + 本轮新增 15），0 失败，58 个测试类 |
| 本轮新增用例 | `DoubanSpoilerPolicyTest` 3 · `SourceRateLimiterTest` 8 · `SyncMergePolicyTest` 7 |

### 4-13 豆瓣剧照防剧透（用户开关，按你的选择）

| 项 | 内容 |
| --- | --- |
| 新文件 | `data/remote/douban/DoubanSpoilerPolicy.kt`（纯策略：开启时起点 = 一页，即跳过第一页） |
| 改文件 | `data/settings/AppSettings.kt`（`doubanAntiSpoiler`，默认开）、`SettingsDataStore.kt`（键 + 映射 + setter + 恢复）、`viewmodel/SubjectDetailViewModel.kt`（取图带 start，取空回退第 0 页）、`ui/settings/GrayChannelSettingsSection.kt` + `pages/DataSourceSettingsScreen.kt` + `SettingsViewModel.kt`（「剧照防剧透」开关） |
| 行为 | 开关开 → 从第二页起取图；**取不到图自动回退第 0 页**（不能因为开了防剧透把剧照弄没） |

**顺带修掉一个真 bug（恢复备份会清空豆瓣设置）**

`BackupManager` 的 settings JSON 里从来没有豆瓣相关键，而 `SettingsDataStore.restoreFrom` 会逐字段写回——
于是「恢复备份」会把豆瓣剧照开关与 Referer 重置为默认值。这次把 `doubanPhotosEnabled / doubanAntiSpoiler /
doubanImageReferer / doubanApiReferer` 补进 `settingsToJson` 与 `parseSettings`。
同时发现 **B1 新增的 `firstRunCompleted` 也有同样问题**（恢复后会被重置为 false → 又弹一次引导），一并补上。

### 4-12 按源限流闸门

| 项 | 内容 |
| --- | --- |
| 新文件 | `util/SourceRateLimiter.kt`（纯逻辑，注入时钟）、`util/SourceRateLimits.kt`（全进程共享实例 + 挂起 / 同步两个入口） |
| 规则 | IGDB `api.igdb.com` 4 req/s · MusicBrainz `musicbrainz.org`（含子域）1 req/s · VNDB `api.vndb.org` 200 次 / 5 分钟 |
| 接入点 | `RatingHttp.requestRaw`（IGDB / MusicBrainz / Discogs / Books / 豆瓣统一出口）+ `VndbApiClient` 的 OkHttp 拦截器（Retrofit，非挂起上下文用 `runBlocking` 包一层） |
| 语义 | 窗口内配额未满 → 立即放行（允许突发到上限）；用满后按 `window / maxRequests` **匀速排队** |

> 匀速排队不是随手写的：我第一版按「等到队首过期」实现，被自己的单测抓出窗口边界缺陷——
> MusicBrainz 那批并发请求会在 t=1000 一起放行两条，瞬时仍超限。测试先失败才改成匀速。

### 4-8 WebDAV 纳入新增数据

| 项 | 内容 |
| --- | --- |
| 新文件 | `data/sync/SyncMergePolicy.kt`（LWW / 本地优先的纯逻辑） |
| 协议版本 | `SYNC_VERSION` 2 → **3**，新增 `externalIds` / `episodeMyRatings` / `manualAwards` / `coverOverrides` 四个字段 |
| 改文件 | `data/sync/SyncManager.kt`（上传采集 + 下载解析 + 合并 + 事务写回）、`data/local/dao/{ExternalIdDao,ExternalRatingDao,ManualAwardDao}.kt`（各加一个 `clearAll`）、`NirikoApplication.kt`（注入 `coverOverrideStore`） |
| 合并规则 | 外部绑定按 `boundAt`、我的每集评分按 `ratedAt`、手动成绩按 `createTime` 走 **LWW**；封面覆盖无时间戳 → **本地优先**（只并入远端独有的键） |
| 兼容 | 旧 v2 远端文件缺这些字段 → 解析为空列表，合并结果等于本地值，不会误删 |

**B4 已知限制（记录，未做）**

1. `work_items` 与 `manual_awards` 的主键是**自增 id**：两台设备各自生成的 id 可能撞车，LWW 会把它们当成同一条。这是同步协议早期就有的设计（不是我引入的），v3 沿用；要彻底解决需要引入稳定的业务键（如 `(subjectId, sourceId)`）做迁移。
2. 封面覆盖没有修改时间，只能本地优先——两台设备都换过封面时，远端那次不会覆盖本地。
3. 限流是**进程内**的：应用被杀掉重开后配额重置。对个人应用足够（源站按真实时间窗统计，重启后仍会自然降速），但不等于服务端配额。

---

## 十六、B5 实施记录（工程债）

**验收结果**

| 项 | 结果 |
| --- | --- |
| `assembleDebug` / `assembleRelease` | 均 BUILD SUCCESSFUL；产物 `app-debug.apk`（28.6MB）与 `app-release-unsigned.apk`（18.7MB） |
| `testDebugUnitTest` | BUILD SUCCESSFUL，**493 个用例全绿**（B4 后 488 + 本轮新增 5），0 失败，59 个测试类 |
| 签署 | 本地无 `keystore.properties` → release 仍产出未签名包（符合预期，构建不失败） |

| 编号 | 状态 | 说明 |
| --- | --- | --- |
| 6-4 详情页快照补全 | ✅ 完成 | `ExtendedSnapshot` 补入 23 个字段（权威评分 / 每集评分 / IMDb 状态 / TMDb 详情与候选 / 剧照 / 豆瓣候选等），缓存命中路径同步回填。此前这些字段不在快照里，二次进详情页会重跑外部源与剧照候选——正是 README 记的那条 |
| 6-2 Room 迁移测试 | ⚠️ 静态版完成 | 新增 `NirikoMigrationChainTest`（5 例）：迁移链连续性（from 必须 2..28 无断档）、`ALTER TABLE … ADD COLUMN` 与目标版本 schema 的列对得上、`CREATE TABLE` 的列集合与 schema 一致、identityHash 格式合法。**这是解析源码与 schema JSON 的静态校验，不等于真机跑一遍 SQLite**；真正的「每个版本都能开库」需要 Robolectric + room-testing（新增测试依赖，按 README「引入新第三方依赖前先讨论」需你点头） |
| 6-3 补 v25 schema | ⚠️ 未生成（有意） | Room 的 `identityHash` 由注解处理器在编译期计算，**手写会得到错哈希**——比缺文件更糟（以后有人跑 MigrationTestHelper 会撞上莫名报错）。正确做法：检出 DB 版本为 25 的那次提交 → 构建一次（Room 自动写出 `schemas/…/25.json`）→ 提交该文件。另外 24→29 的迁移测试**不需要** 25.json（它校验的是终点 29），所以缺口只影响「起点或终点正好在 25」的测试。本轮改由测试把缺口显式钉住：除 v25 外再缺任何一个版本都会失败 |
| 6-5 签名 + CI | ✅ 完成 | `app/build.gradle.kts` 增加 `signingConfigs.release`，凭据只从本地 `keystore.properties` 读（该文件与 `*.jks`/`*.keystore` 已在 .gitignore）；无该文件时不挂签名、构建照常。新增 `.github/workflows/android-ci.yml`：JDK 17 → 安装 `platforms;android-37.0` + `build-tools;37.0.0` → `testDebugUnitTest` → `assembleDebug` → 上传测试报告 |

**B5 未做 / 需你决定**

1. **Robolectric + room-testing**（真正跑 SQLite 的迁移测试）需要新增测试依赖。按 README 的约定我没有擅自加；要加的话说一声，我把 2→24、24→29 两条链的 `MigrationTestHelper` 测试补上（并把 schemas 挂到 test assets）。
2. CI 工作流**没有在真实 runner 上跑过**（本机没有 GitHub 环境），且它依赖 `platforms;android-37.0` 能装到 runner 上；若该包当时不可用，把版本号换成仓库里可用的最新 platform 即可。
3. 6-3 的 v25.json 仍需按上面写的「检出旧提交 + 构建」方式生成，本轮只把缺口钉住、没有伪造哈希。

---

> 与线框图的关系：`docs/niriko-plan-wireframes.html` 是视觉与状态对照（47 屏 / 79 状态 / 77 条清单），本文是它的可执行版本。两者冲突时**以本文为准**，并回头修正线框图。
