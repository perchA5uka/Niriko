# Niriko 代码 / UI / 数据层 体检摘要（2026-10）

> 来源：2026-10-24 三个并行调研（UI 设计系统 / 代码规范 / 数据层与发布）+ Lead 抽查复核。
> 结论按「该不该现在动手」分级；**行号会漂移**，引用前请重新 `grep` 符号名。
> 相关 skill：`niriko-ui-review`、`niriko-compose-conventions`、`niriko-data-layer`。

---

## P0 · 建议优先处理（数据安全 / 可读性 / 无障碍）

### 1. 备份设置覆盖不全 → 恢复后静默重置约 32 个设置字段
- `data/backup/BackupManager.kt` 的 `settingsToJson` / `parseSettings` 只序列化 `AppSettings` 79 个字段中的 47 个；
- 恢复入口 `BackupViewModel` 拿 `result.settings` 调 `data/settings/SettingsDataStore.kt` 的 `restoreFrom()`，该函数**整体回写 78 个键**；
- 结果：导出→恢复后，未序列化的 32 个字段（含 TMDb / OMDb / IGDB / RAWG / Discogs / OpenCritic 的 API Key、Bangumi OAuth Secret、灰通道、详情布局、玻璃档位、分享项等）被写成默认值。
- 另：`restoreFrom` 未写 `GLASS_EFFECT`（80 个键中唯一漏写）。
- 建议：先补「`AppSettings` 全字段序列化 + 一条覆盖单测（反射比对字段名集合）」，再考虑把 `restoreFrom` 改成只回写备份里出现的键。

### 2. 观看状态有两套色表
- `ui/components/Badges.kt` 的 `watchStatusColor()`（`F59E0B / 22C55E / 9CA3AF / 8B5CF6 / 9CA3AF`）与 `ui/theme/StatusColors.kt` 的 `statusTone()` 语义同、色值不同；
- UI 实际用 `statusTone`，前者仅剩单测（`GlassChipTokensTest`）引用 → 两套都「算活着」，换色时容易改错一处。
- 建议：删除 `watchStatusColor` 或改为委托 `statusTone().accent`，并改对应测试。

### 3. `GlassSlider` / `GlassToggle` 无障碍缺口
- `ui/components/GlassControls.kt`：`GlassSlider` 无 `ProgressBarRangeInfo`、无 clickable/可聚焦语义，高度 34dp；`GlassToggle` 独立使用时高 30dp；
- 在设置行内靠整行点击兜底，但独立使用（如评分滑杆）TalkBack 无法操作，触控目标也 < 48dp。
- 建议：补 `semantics { progressBarRangeInfo = ...; role = Role.Slider }` 或改用 `Slider` 语义；交互命中区 ≥ 48dp（视觉可小）。

### 4. 对话框圆角只统一了 3/34
- 已统一：`SyncBackupSettingsScreen`、`DataSourceSettingsScreen`、`WallpaperLibraryScreen` 的 3 个高频对话框用 `NirikoShapes.Sheet` 24dp + `surfaceContainerHigh`；
- 其余多数 `AlertDialog` 仍是 M3 默认圆角（README 口径：已换 3 个高频、还有 21 个待统一；`grep 'AlertDialog('` 统计到 34 处调用点 / 19 个文件，含非对话框场景）。README 已把它列为「本版未做」，属已登记欠账，不是新回归。
- 建议：按页面使用频次继续替换，并把 `DialogAndSheetSurfaceTest` 的契约扩展到新替换的对话框。

---

## P1 · 值得排期（一致性与正确性风险）

### 数据 / 同步
- **备份明文含凭据**：JSON 备份包含 WebDAV 密码、Bangumi Token、Steam Token，导出文件可被任意读取；建议至少加导出警告 + 可选「不含凭据」模式。
- **无基线首次上传 = 裸 PUT**（`SyncManager` 在 `Overwrite` 分支不带 `If-Match`）：换机/清数据后首次同步可能静默覆盖云端；建议云端存在内容时要求用户确认。
- `cover_overrides` 存本机 `content://` URI，跨设备/换机恢复后失效（写入前需再校验可读性）。
- `work_items` / `manual_awards` 用自增 id 做跨设备键，两台设备会撞车（`docs/rollout-plan.md` 已记录，改动需业务键 + 迁移）。
- 同步范围不含 `library_folder*`（有意），但每次下载会 `prune` 幽灵成员，边界要写清。

### 文档与实现漂移（顺手就能修）
- `README.md` 写「备份格式 v4，读 v1–v4」，代码是 `EXPORT_VERSION = 5`（读 v1–v5）。
- `README.md` 把外链 ID / 每集评分 / 手工成绩 / 封面覆盖列为同步待办，实际 v3 起已同步（仅分区仍不同步）；`README.md` 的 412「保留本地/用远端/合并」与 UI 实际三选项（先合并/覆盖云端/取消）不符；`deviceId` 不进 `sync.json`。
- `THIRD-PARTY-NOTICES.md` 漏 Vico / kizitonwose calendar / compose-shimmer / lifecycle，测试期漏 Robolectric / room-testing。
- `v1.2.1` 无 tag、无 `docs/release-v1.2.1.md`；`v1.0.0` / `v1.1.0` tag 未指向发布提交。
- `docs/ios-port/09-database-kmp-draft.md` 仍以 Room v20 为基线；`docs/v1.2.1/batch-c-storage.md` 称新增 3 个 DataStore 键「随备份自动带走」，实际未序列化。

### 代码规整
- 巨型文件：`ui/subject/SubjectDetailScreen.kt` 4473 行（`SubjectDetailBody` 单 Composable ≈1192 行）、`viewmodel/SubjectDetailViewModel.kt` 2275、`viewmodel/SubjectSearchViewModel.kt` 2201、`ui/stats/CalendarCard.kt` 1491、`ui/screens/Screens.kt` 1120、`ui/stats/StatsScreen.kt` 1025。
- `collectAsState()` 40 处 vs `collectAsStateWithLifecycle()` 1 处（`ui/bilibili/BilibiliSyncScreen.kt`）；新代码按 `niriko-compose-conventions` R5 逐个文件改，不做批量替换。
- `openSubject` 两份实现：`navigation/NirikoNavHost.kt` 完整版（带 `SubjectNavigationSeed` + `SubjectCoverHandoff`）与 `navigation/MainPager.kt` 简化版（无 heroKey）→ 从顶层页进详情时封面共享元素可能退化。
- 版本目录漂移：`gradle/libs.versions.toml` 声明 `miuix = 0.9.3`，`app/build.gradle.kts` 实际硬编码 `0.9.0`；`cloudy` 声明未使用。
- 豆瓣 `frodo` key 硬编码在源码、无设置覆盖项。
- 文案：`res/values/strings.xml` 仅 5 条，`Text("中文…")` 内联约 296 处（与现状一致，不要为国际化批量迁移）。

### UI / 动效
- `ui/theme` 之外仍有 68 处 `Color(0x…)`，集中在 `ui/share/ShareCardLayout.kt`(21)、`ui/components/CardRarityMaterial.kt`(17)、`ui/components/Badges.kt`(8)；分享卡是刻意隔离域，其余新代码禁止新增。
- 字号：`ui/stats/CalendarCard.kt`、`StatsTodaySection`、`CalendarDaySheet`、`LibraryGalleryCard` 等处出现 9sp 正文（正文下限 `bodySmall` 13sp，10sp 只允许压图徽标）。
- 搜索卡 28dp 圆角未纳入 `NirikoShapes` 令牌，属「历史第六档」，不要再扩散。
- 动效字面量绕过 `NirikoMotionSpecs`：`ui/components/NirikoNonModalSheet.kt`、`TouchGlow.kt`、`GlassCard.kt` 里仍有 `spring(数字,数字)`；sheet 的两个 `animateTo` 未传 `initialVelocity`。
- `TouchGlow` 只读事件不消费，滚动时残留按压态；`GlassControls` 在组合期读动画值（每帧重组/重测，改 `graphicsLayer` 可只重绘）。

---

## P2 · 清理项（不急，改到附近时顺手）

- 迁移测试注释过时：`app/src/test/.../data/local/RoomMigrationTest.kt` 仍写「27 个 Migration」（实际 30）、「15 个提交」（实际 19）。
- 静态迁移链测试只解析 `execSQL("字面量")`，SQL 放进变量/常量会被静默跳过。
- `plans/05-platform-modernization-and-guardrails.md` 仍写「78 个测试类 / 696 用例」（实际 183 类 / 1419 用例）。
- 死代码：`WorkViewModel` / `WorkRepository` / `resolveSeasonId`；3 处过期 `@Suppress("unused")`。
- 文本：11 个 CRLF 文件（1 个 CRLF/LF 混合）、3 处重复 import、23 行 >200 字符、8 个文件 EOF 多空行。
- `SettingsViewModelFactory` 样板在 NavHost 复制 8 次；`formatCount` 三份逐字重复；3 个 ViewModel 放 `ui/`、11 个放 `viewmodel/` 无成文规则。
- 无间距 token（1565 处 `.dp` 字面量），暂时沿用 4/8/12/16/20/24/32 节奏。

---

## 刻意保留，**不要**当缺陷报（避免评审噪音）

- 设置页组容器 20dp（主页）/ 24dp（二级页）并存——有意层级差；
- 220ms 底栏隐藏过渡、350ms 详情材质渐显、dock 的 kyant 上游弹簧值——已登记刻意值；
- 10sp 压图徽标；
- 21 个未统一圆角的 `AlertDialog`——已登记欠账（可继续推进，但别当回归）；
- `Theme.kt` 里 6 套遗留 `ThemePalettes` / `setThemeColorIndex`——无 UI 入口的旧数据兼容；
- shimmer 在低档位退化为静态灰块、统计图表静态化、详情 hero 1080×1440 保持高清；
- Robolectric 下无法渲染任何走 `drawBackdrop`/AGSL 的真玻璃（不要试 Roborazzi 截图基线）。

---

## 建议处理顺序

1. **P0-1 备份字段覆盖**（数据丢失级，先补序列化 + 覆盖单测）。
2. **P1 文档一致性**（README 三处 + THIRD-PARTY + v1.2.1 release notes/tag，成本低、收益直接）。
3. **P0-2/3/4 UI 一致性**：删第二套状态色表、补滑杆/开关无障碍、继续统一对话框圆角（用 `niriko-ui-review` 做验证矩阵）。
4. **代码规整**：先拆 6 个超大文件 + 收敛 `openSubject` 双实现；`collectAsStateWithLifecycle` 随改动推进。
5. **P2 清理**与文档数字同步（改到相关文件时顺手）。

---

## 复核命令（只读）

```powershell
# 超大文件
Get-ChildItem app/src/main/java -Recurse -Filter *.kt |
  ForEach-Object { [pscustomobject]@{ Lines = (Get-Content $_.FullName | Measure-Object -Line).Lines; Path = $_.FullName } } |
  Sort-Object Lines -Descending | Select-Object -First 10

# 状态色两套表
rg -n "watchStatusColor|fun statusTone" app/src/main
# 硬编码颜色（排除 theme / share）
rg -n "Color\(0x" app/src/main/java/com/otakup/niriko/ui --glob '!theme/**'
# 对话框圆角
rg -n "AlertDialog\(" app/src/main/java/com/otakup/niriko | Measure-Object
# collectAsState 现状
rg -c "collectAsState\(" app/src/main/java --stats
# 备份版本 / 同步版本
rg -n "EXPORT_VERSION = |SYNC_VERSION = " app/src/main/java
```
