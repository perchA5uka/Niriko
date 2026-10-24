# 批次 A · 用户直报问题任务卡（X1–X5）

> 范围：`docs/release-v1.2.1-plan.md` §1 的 U1 / U2 / U3a / U15 / U17，对应本文件 A-X1…A-X5。§7 已拍板决定中的 #1（单本卷进度）、#2（AniList）、#3（R18 只修动画）直接约束 A-X4 / A-X2 / A-X3。
> 只读产出：本文件不含任何代码改动；所有 `file:line` 均已回当前工作区代码确认。
> 共同前置：`./gradlew :app:assembleDebug` 通过；新增纯函数配 `:app:testDebugUnitTest`。Room 当前 `NirikoDatabase.kt:62 → version = 32`，本节 5 张卡**均不动表**（不占 v33）。

## ⚠ 与计划不符（5 处，先看这里）

| 卡 | 计划写法 | 代码实际 |
| --- | --- | --- |
| A-X1 | U1 第①步「确认 realm/return_to 都是 https」 | 已是 https（`SteamOpenIdClient.kt:38-43,55`）且有单测锁定；真正缺的是第②步 UA，以及没有 `onReceivedHttpError` 导致 403 不可见 |
| A-X2 | 「两个兜底开关」+ 字段名待核 | 是「数据源」分组里两行**插件单选 picker**（非布尔开关）；唯一偏好 `activeDataSourceId` 全仓**无读取方** |
| A-X3 | `SearchSettingsScreen.kt:44` 是 `onCheckedChange` | :44 是标题文本；`checked` 在 :46、`onCheckedChange` 在 :47 |
| A-X4 | `WorkType.BOOK && volumes <= 1` | 详情页域是 `SubjectType`，`WorkType` 只属 works/备份域；且已有 `series == false` 旧规则（`SubjectDetailScreen.kt:2582`） |
| A-X5 | 问题在「保存回调」 | 根因是屏幕级 `LaunchedEffect` 的字符串分支（`SubjectDetailScreen.kt:311-322` → :318 `onBack()`），且写库失败也走成功路径 |

---

## A-X1 Steam OpenID 登录报 Access Denied（errors.edgesuite.net）

- **⚠ 与计划不符**｜§1-U1 排查第①步（确认 realm/return_to 都是 https）在代码里**已经成立**：`SteamOpenIdClient.kt:38-43` 的 `RETURN_TO_URL = https://localhost/steam-auth`、`:55` 的 `realm = https://localhost`，并被 `SteamOpenIdClientTest.buildLoginUrl_usesHttpsReturnTo` 锁定。真正缺的是第②步（UA）与「失败不可见」。
- **问题**｜首跳就被 Akamai 拦：`SteamLoginScreen.kt:177-189` 建 WebView 时**没有** `settings.userAgentString`（用系统默认 UA，含 `; wv`）；同时只监听网络层 `onReceivedError`（:191-207），没有 `onReceivedHttpError`，于是 403 / Access Denied 页被当正常页面渲染，用户只看到 edgesuite 报错页。
- **现状 WebView 流程**｜VM `init` 用 `SteamOpenIdClient.buildLoginUrl()` 生成登录地址（`SteamLoginViewModel.kt:58-64`）→ `AndroidView` factory 里 `WebView(ctx).loadUrl(url)`（`SteamLoginScreen.kt:246`），此时**无 UA 设置** → `shouldOverrideUrlLoading`（:211-232）拦截 `https://localhost/steam-auth`（并兼容 http 前缀，:218-221）→ 拦截后 `loadUrl(store.steampowered.com)`（:227）触发 store 域 SSO → `onPageFinished`（:234-244）取 store Cookie → `viewModel.captureWebApiToken(cookie)`（`SteamLoginViewModel.kt:115-128`）。注意 `SteamLoginScreen.kt:40` 的 KDoc 仍写旧回跳 `niriko://steam-auth`，与实际 `https://localhost/steam-auth` 不一致（纯注释漂移，顺手改）。
- **改哪里**｜`ui/steam/SteamLoginScreen.kt` → `SteamOpenIdWebView`（:166-254）；`ui/steam/SteamLoginViewModel.kt` → `restartLogin`（:83-85）；`data/remote/steam/SteamOpenIdClient.kt`（:53-68 只负责 URL）；UA 写法参照 `ui/common/OAuthWebView.kt:39-41,130-142`（不改它）。
- **怎么改**｜
  1. 抓现场：给 `SteamOpenIdWebView` 加 `onPageStarted`，`Log.i("SteamOpenId", "$url | ua=${view.settings.userAgentString}")`，`adb logcat -s SteamOpenId`。判据：Access Denied 出现在**首跳** `steamcommunity.com/openid/login` → UA/反爬；出现在**回跳后** → return_to/协议（已被单测排除）。
  2. 最小改法（一行）：`factory` 里加 `settings.userAgentString = DESKTOP_UA`（Chrome/120 Windows 桌面串）。**不要**用 `OAuthWebView` 的 `MOBILE_USER_AGENT` 兜底——Steam 登录页是桌面布局，移动 UA 更容易被风控拦。
  3. 让失败可见：加 `onReceivedHttpError`，`request.isForMainFrame && statusCode >= 400` 时复用现有 `loadData` 错误页并区分文案（403 → 「被 Steam 拒绝访问，多为网络/风控，可改用手动 SteamID64」）。
  4. 顺手修 `restartLogin`：它只改 `status`，`loginUrl` 不变，`AndroidView.update` 的 `view.url != url`（:250）不成立 → WebView 根本没重载。改成 VM 里加 `reloadSeq`，`update` 块按 seq 变化 `view.loadUrl(url)`。
  5. 仍 403 时**不要**切 Custom Tab / 系统浏览器：`return_to` 是 `https://localhost/steam-auth`，只有 WebView 能拦，系统浏览器回不到 App；保留既有手动 SteamID64 兜底（`SteamLoginScreen.kt:109-138` + `SteamLoginViewModel.isValidSteamId64`），错误页指向它。
- **测试**｜把 :216-221 的内联回跳判定抽成纯函数 `SteamOpenIdClient.isCallbackUrl(url)`，JVM 单测覆盖：https 回跳 → true、http 回跳 → true、`https://store.steampowered.com/` → false、空串 → false；`buildLoginUrl(returnTo, realm)` 已可注入，补一条「return_to 与 realm 必须同源前缀」断言；保留 `SteamOpenIdClientTest` 现有 6 项为绿。
- **验收**｜① 真机（国区直连 + 代理各一次）进登录页，首帧不再是 Access Denied，能看到账号密码表单；② `adb logcat` 第一条 URL 为 `https://steamcommunity.com/openid/login?...return_to=https%3A%2F%2Flocalhost%2Fsteam-auth`；③ 完成登录后停在「已登录：<steamid64>」且 `settings.steamId64` 落库；④ 断网时出现可见错误页，手动 SteamID64 可用；⑤ 点「重新登录」WebView 真的重载。
- **风险/回滚**｜桌面 UA 会让页面按桌面布局渲染（已开 `useWideViewPort`，影响可控）；个别内核版本会因 UA 与内核不匹配被 Steam 更严拦。回滚 = 删 UA 一行 + 删 `onReceivedHttpError`，其余保留。
- **预估**｜S
- **依赖**｜无

---

## A-X2 删除两个兜底开关（Bangumi 番组计划 / AniList）

- **⚠ 与计划不符**｜① 这两个不是布尔开关，而是「数据源」分组里两行**插件单选 picker**：`DataSourceSettingsScreen.kt:132-143` 遍历 `viewModel.plugins` 渲染 `SettingsPickerRow`，名称来自插件注册 `NirikoApplication.kt:403-410`（`"Bangumi 番组计划"`）与 :415-423（`"AniList"`）；② 全仓**不存在** `aniListFallback` 一类 per-source 开关（已 grep）；③ 对应偏好只有一个 `AppSettings.activeDataSourceId`（`AppSettings.kt:146`，默认 `"bangumi"`），且**没有任何读取方**——`DataSourceChain` 不读它，全仓只有 `SettingsDataStore.kt:87,208,452-454,686` 与 `BackupManager.kt:458,733` 的读写/序列化 → 「已无意义」在代码上成立。
- **问题**｜两行点一下调 `SettingsViewModel.setActiveDataSourceId`（:247-252）→ `PluginManager.moveToTop`（`PluginManager.kt:35-39`）改内存顺序 + 写 `activeDataSourceId`；但顺序不跨进程、字段无人读，用户点完只有「✓ 当前」文案变化，实际行为零变化。
- **改哪里**｜`ui/settings/pages/DataSourceSettingsScreen.kt:132-143`（唯一 UI）；`viewmodel/SettingsViewModel.kt:87-93,247-252`；`data/settings/AppSettings.kt:146`；`data/settings/SettingsDataStore.kt:87,208,452-454,686`；`data/backup/BackupManager.kt:458,733`。
- **怎么改**｜
  1. 删 UI：`datasourceRows` 里 `viewModel.plugins.forEach { ... }` 整块（:132-143）。保留同组的「Bangumi 接口」（:118-131）、「Steam API Key」（:144-152）、「Steam 账号与游戏库」（:153-161），外层 `if (viewModel.plugins.isNotEmpty())`（:117）保留以压小 diff。
  2. 删 VM：`plugins` getter（:87-90）、`primaryPluginId`（:91-93）、`setActiveDataSourceId`（:247-252）——三者删后零引用（唯一调用点就是上面那块 UI）。`pluginManager` **构造参数保留**，`MainActivity.kt:117-120`、`NirikoNavHost.kt` 8 处、`AdaptiveSettingsPane.kt:386` 的工厂调用点都不必改。
  3. 删字段与偏好：`AppSettings.activeDataSourceId`（:146）；`Keys.ACTIVE_DATA_SOURCE_ID`（:87）、读（:208）、写（:452-454）、重置写入（:686）。
  4. 删备份两行（`BackupManager.kt:458,733`）；读取侧本就是 `?: defaults...` 形式，**旧备份里多出来的键会被忽略**，不需要迁移。
  5. 明确**不动**：`NirikoApplication.kt` 插件注册与 `pluginManager`、`DataSourceChain`、`AniListClient`/`AniListRepository`、`anilist_bindings` 表（`NirikoDatabase.kt:373-390`）、详情页 AniList 补充（`SubjectDetailViewModel.kt:959-1042`）与评分补充。**v33 迁移：否**；不新增 DAO 方法。
- **测试**｜无新增业务纯函数，不硬造测；改为回归断言：`grep -rn "activeDataSourceId\|setActiveDataSourceId" app/src/main` 只剩注释甚至为空；`BackupFolderRestoreTest`/`BackupFolderNamesTest` 保持绿；构造一份含 `"activeDataSourceId":"anilist"` 的旧备份 JSON，导入后落默认值且不抛异常。
- **验收**｜设置 → 数据源与账号 →「数据源」只剩 Bangumi 接口 / Steam API Key / Steam 账号，无「Bangumi 番组计划」「AniList」两行；发现页、搜索、日历照常出数据（链式兜底仍由 `DataSourceChain` 完成，不依赖 UI 顺序）；详情页 AniList 区块/绑定/候选/评分补充全部正常；导出→导入旧档不崩且 AniList 绑定不丢。
- **风险/回滚**｜最可能翻车：顺手把 `NirikoApplication.kt:403-423` 的插件注册也删掉 → 发现页直接空。回滚 = 单文件 revert；想留退路可先只删 UI 两行（偏好字段留着不读）观察一版。
- **预估**｜S
- **依赖**｜无。但要与任何改 `SettingsDataStore`/`AppSettings` 的任务（设置搜索、数据源自检）串行，避免同文件冲突。

---

## A-X3 R18 开关每进一次页面就重放切换动画

- **⚠ 与计划不符**｜§1-U3a 写 `SearchSettingsScreen.kt:44` 是 `onCheckedChange = viewModel::setNsfwEnabled`；实际 :44 是 `title = "允许显示 R18 内容"`，`checked` 在 :46、`onCheckedChange` 在 :47。
- **问题**｜根因是「设置页各自新建 ViewModel + 种子值是默认 AppSettings」：`SettingsViewModel.kt:79-83` 的 `settings` 用 `stateIn(..., initialValue = initialSettings ?: AppSettings())`，而 `AppSettings.kt:131` 的 `nsfwEnabled` 默认 `false`；设置二级页在自己的 `composable` 里 `viewModel<SettingsViewModel>(factory = SettingsViewModelFactory(app.settingsDataStore, ...))` 且**都没传 `initialSettings`**（`NirikoNavHost.kt:494-511` 搜索页，:424-430 等同款）。于是每次进页：新 VM → 首帧 `false` → DataStore 真实值（用户开着则为 `true`）到达 → M3 `Switch` 把这次变化当用户切换播放动画（`SettingsItem.kt:135-160` 用原生 `Switch`，动画在内建 `animateFloatAsState`/spring 里，UI 侧没有关闭参数）。
- **改哪里**｜`viewmodel/SettingsViewModel.kt:36-45`（构造）、:79-83（种子）、:232-234（`setNsfwEnabled` 写路径）；`MainActivity.kt:105-120`（把已同步读到的 `initialSettings` 写缓存）；`ui/settings/pages/SearchSettingsScreen.kt:42-48`（**不需要**改）。
- **怎么改**｜
  1. 加进程内缓存（放 `SettingsViewModel` 伴生对象即可，`@Volatile var lastKnown: AppSettings?`），在 `dataStore.settings` 链上加 `.onEach { lastKnown = it }`，再 `stateIn(..., initialValue = initialSettings ?: lastKnown ?: AppSettings())` —— 即计划原话「初值直接取当前值」。
  2. `MainActivity` 拿到 `initialSettings` 后写 `lastKnown = initialSettings`（冷启动首进也正确）。
  3. 写路径同步缓存：`setNsfwEnabled` 等 setter 落库后直接更新 `lastKnown`，避免「关掉开关 → 立刻返回 → 再进页面」时种子是旧值又动画一次。
  4. UI 不改：保持 `checked = settings.nsfwEnabled`；首帧即真值，`Switch` 首次组合不动。
  5. 兜底（若不允许动 ViewModel）：只能让 `SettingsViewModel` 额外暴露 `settingsHydrated: StateFlow<Boolean>`（`onEach` 里置 true），UI 用 `key(hydrated) { SettingsSwitchRow(...) }` 重建开关实例；**不要**用 `LaunchedEffect(Unit)` 跳首帧——它早于 DataStore 发射，时序更差。
- **测试**｜把种子策略抽成纯函数 `SettingsSeedPolicy.seed(initial: AppSettings?, cached: AppSettings?): AppSettings`，JVM 单测：`initial!=null` 用 initial；`initial=null, cached!=null` 用 cached；两者皆空回 `AppSettings()`（断言 `nsfwEnabled == false`）。
- **验收**｜先把 R18 开关打开 → 退出设置 → 再进「搜索」页 ×5 次，开关不再从左滑到右；关闭后同样静默；进出外观/数据源页的 `Switch` 一并静默（同一修复覆盖）；冷启动进程首次进入允许可见一次动画（缓存为空），记录为已知边界。
- **风险/回滚**｜缓存可能短暂为旧值（恢复备份、外部改 DataStore）→ 最长一个 DataStore 发射周期后被 `onEach` 覆盖；回滚 = 种子改回 `initialSettings ?: AppSettings()`，缓存代码留着无副作用。
- **预估**｜S
- **依赖**｜无。宽屏走 `AdaptiveSettingsPane.kt:386` 的同一 VM 修复，不需要另改。

---

## A-X4 单本（书籍）不应显示卷进度

- **⚠ 与计划不符**｜§7-#1 写 `WorkType.BOOK && volumes <= 1`；详情页域里没有 `WorkType`——它属于 works / 备份域（`BackupManager.kt:569`、`Converters.kt:17-23`），详情页用的是 `SubjectType`。另外同一个弹层上方的进度区**已经有**一条单本旧规则 `if (subject.series == false) { /* 单本不展示进度 */ }`（`SubjectDetailScreen.kt:2582`），与计划谓词不是一套。
- **问题**｜编辑弹层对 BOOK/MANGA **无条件**渲染「卷进度」输入：`SubjectDetailScreen.kt:2133-2137`（`if (subject.type == SubjectType.MANGA || subject.type == SubjectType.BOOK)`）。因此单本（`volumes` 为空或 1）进编辑层仍看到「卷进度」，而它上面的进度区已被 `series == false` 隐掉 → 两处判定自相矛盾。
- **改哪里**｜新增 `util/VolumeProgressPolicy.kt`（纯函数，唯一判定源）；`ui/subject/SubjectDetailScreen.kt:2133-2137`（弹层）；:2582（进度区复用同一谓词）。
- **怎么改**｜
  1. 纯函数：`fun showVolumeField(type: SubjectType, volumes: Int?): Boolean = when (type) { SubjectType.MANGA -> true; SubjectType.BOOK -> (volumes ?: 0) > 1; else -> false }`；`null` / `0` / `1` 都按单本处理。
  2. 弹层改为 `if (VolumeProgressPolicy.showVolumeField(subject.type, subject.volumes)) { OutlinedTextField("卷进度") }`。**不新增传参**：`CollectionEditSheet` 本来就拿着 `subject`（:2038），谓词就地算。
  3. 进度区 :2582 保留既有 `series == false` 分支（**不动合集行为**），在 `BOOK` 分支复用同一谓词并加一行注释「单本判据统一在 VolumeProgressPolicy」；若担心两套判据分叉，本卡只做弹层，进度区对齐列为后续项。
  4. 提交链路不动：`watchedVolumesText` 仍原样进 `onSaveRecord`，:2212 的 `.takeIf { subject.type == MANGA || subject.type == BOOK }` 保留 → 隐藏输入框**不会**把已填卷数写空（已填数据不丢）。
  5. **v33 迁移：否**：不动 `CollectionEntity.kt:33` 的 `watchedVolumes`、不加 DAO 方法、不改 `NirikoDatabase.kt`（当前 version = 32）。
- **测试**｜新增 `util/VolumeProgressPolicyTest`：(BOOK, null)→false、(BOOK, 0)→false、(BOOK, 1)→false、(BOOK, 2)→true、(BOOK, 24)→true、(MANGA, 1)→true、(ANIME, null)→false、(GAME, null)→false。
- **验收**｜① 打开 `series=false` / `volumes=null` 的书籍详情，编辑弹层无「卷进度」；② 多卷书（如 24 卷）仍显示且可写；③ 漫画单卷仍显示（计划只要求书籍）；④ 已填过卷数的单本保存后查 `sqlite3 collections`，`watchedVolumes` 不变；⑤ 合集（`series=true`,`volumes=null`）仍显示；⑥ `./gradlew :app:testDebugUnitTest` 通过。
- **风险/回滚**｜隐藏后用户失去「清空已填卷数」的入口（计划默认接受；若要留出口可加「清除卷进度」小按钮）；某些源用 `volumes=0` 表示未知，会被判成单本 → 可接受（未知总卷时卷进度本身无意义）。回滚 = 谓词改回 `type == BOOK || type == MANGA`。
- **预估**｜S
- **依赖**｜无。与 A-X5 改同一个 `SubjectDetailScreen.kt`，按 A-X4 → A-X5 串行，避免同行冲突。

---

## A-X5 保存收藏后应留在详情页，不能直接退出

- **⚠ 与计划不符**｜§1-U17 把问题定位在「`SubjectDetailScreen` 的保存回调」；实际根因是屏幕级 `LaunchedEffect` 对消息文本做字符串比较：`SubjectDetailScreen.kt:311-322`（`if (msg == "记录已保存") { haptic; snackbar; onBack() }`，`onBack()` 在 :318），而 `onBack` = `navController.popBackStack()`（`NirikoNavHost.kt:302-303`）。另外**失败也走成功路径**：`SubjectDetailViewModel.kt:1406` 无条件写 `snackbarMessage = "记录已保存"`，`written`（`CollectionRepository.update` 的 Boolean，`CollectionRepository.kt:30`）只喂庆祝动画 → 写库失败同样退页且提示成功。
- **问题**｜点「保存记录」→ VM 置 `snackbarMessage="记录已保存"` → 屏幕 LaunchedEffect 打点 + 吐司 + `onBack()` 退详情；编辑层 `showCollectionSheet`（:1000、:1750）从未被关闭，是随页面销毁被动消失的。计划要的「反向动画收回二级菜单」根本不存在，也没有失败分支。
- **改哪里**｜`viewmodel/SubjectDetailViewModel.kt:232`（UiState 加字段）、:1345-1406（`saveRecord` + 新增 `clearSnackbarMessage`）；`ui/subject/SubjectDetailScreen.kt:311-322`（去掉 `onBack`）、:1750-1800（在 `if (showCollectionSheet)` 内收层）。
- **怎么改**｜
  1. `saveRecord` 末尾分两支：`written=true` → `snackbarMessage = "记录已保存"` 且 `recordSavedSeq = s.recordSavedSeq + 1`；`written=false` → `snackbarMessage = "保存失败，请重试"` 且**不**增 seq。UiState 加 `val recordSavedSeq: Long = 0`（与既有 `collectionMutation` 同风格，见 `SubjectDetailViewModel.kt:273`）。
  2. 屏幕 `LaunchedEffect` 删掉 `onBack()`，只留 haptic + `snackbarHostState.showSnackbar(msg)`；再加 `viewModel.clearSnackbarMessage()`（新方法，置 `snackbarMessage = null`），否则 `completionPlayback.isActive` 二次翻转会把同一条消息再弹一次（现在靠退页掩盖了这个缺陷）。
  3. 弹层收层：`if (showCollectionSheet)` 块里已有 `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`（:1751），加 `LaunchedEffect(state.recordSavedSeq) { if (state.recordSavedSeq > 0 && !completionPlayback.isActive) { sheetState.hide(); showCollectionSheet = false } }` → `hide()` 提供反向收层动画，等它返回再置 false，避免销毁态被读。
  4. 庆祝不被截断：`completionPlayback.isActive`（`CompletionCelebration.kt:46-63`）期间只等不收，把它也作为 key，播放结束自动收层（与屏幕现有 `if (completionPlayback.isActive) return` 判据一致）。
  5. 「只有新建收藏才允许跳转」：`addToCollection`（:1264-1281）成功时记 `isNewCollection=true`；当前没有「加入后自动编辑再返回」的入口，所以本轮**一律不跳**。将来若加 `subject_detail/{id}?autoEdit=true`，用同一 seq 通道 + `CollectionSavePolicy.shouldAutoBack(isNewCollection, written)` 决定是否 `onBack()`。
  6. **v33 迁移：否**；不加 DAO 方法、不改 `collections` 表。
- **测试**｜新增纯函数 `util/CollectionSavePolicy.kt`：`messageFor(written)` → `"记录已保存"` / `"保存失败，请重试"`；`shouldAutoBack(isNewCollection, written)` → `(false,true)=false`、`(true,true)=true`、`(*,false)=false`，JVM 单测。另扩展 `viewmodel/CollectionCompletionTest.kt`：其 `TestDao` 已有 `succeed` 开关，`succeed=false` 时断言 `snackbarMessage` 为失败文案、`recordSavedSeq` 不变。
- **验收**｜① 已收藏条目 → 编辑 → 保存：弹层**带动画收起**、停在详情页、snackbar「记录已保存」，dock 上评分/进度即时更新；② 查 `sqlite3 collections` 值已写；③ 让 `CollectionRepository.update` 返回 false → 提示「保存失败，请重试」，弹层与详情页都**不**关；④ 进度写满触发庆祝时，庆祝播完再收层；⑤ 「取消收藏」与状态切换不受影响；⑥ 系统返回键仍能退回列表，且不残留 snackbar。
- **风险/回滚**｜① 不等 `completionPlayback.isActive` 就 `hide()` 会截断庆祝动画，必须按第 4 步判据；② `snackbarMessage` 是全页共用的单一字段（VNDB / AniList / Steam 绑定提示都写它：:830、:904、:1083、:1258），加 `clear` 后要把这几条提示一起回归；③ `sheetState.hide()` 期间用户再点保存已被 `isUpdating`/isSaveEnabled 挡住，但 seq 必须自增、别用布尔。回滚 = 恢复 :318 的 `onBack()`。
- **预估**｜M
- **依赖**｜A-X4（同文件、同弹层，串行）；U18 若把「加入收藏」改成悬浮按钮，需同步 :1697 的 `showCollectionSheet = true` 入口。
