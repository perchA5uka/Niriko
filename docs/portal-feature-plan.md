# Niriko「传送门」功能方案

> 状态：方案（未实现，未改任何代码）
> 目标：在作品详情页新增「传送门」按钮，点击展开按作品类型分组的下拉菜单，将当前作品一键跳转到其他 App / 网页。
> 本文档基于阅读 Niriko 源码与 Kazumi（`C:\what i download\Kazumi-main`）源码后的结论撰写。

---

## 一、现状结论（读代码确认）

1. **项目定位**：Niriko 是 Kotlin + Jetpack Compose + Material 3 单 Activity 追番收藏统计 App，以 **Bangumi 为主源**，AniList 兜底，Steam / VNDB 作为游戏补充信息源。
2. **作品类型枚举**：`SubjectType`（`app/src/main/java/com/otakup/niriko/data/model/SubjectType.kt`）= ANIME / MANGA / BOOK / GAME / MUSIC / REAL / PERSON / OTHER。
3. **详情页顶栏位置**：`ui/subject/SubjectDetailScreen.kt` 第 **338–394 行**，结构为「返回键 + 作品详情标题 + 弹性 Spacer + 右侧分享 DropdownMenu」。传送门按钮加在分享按钮旁。
4. **最大的差异化机会——详情页已持有整套跨平台 ID**。`SubjectDetailUiState`（`viewmodel/SubjectDetailViewModel.kt` 第 35 行起）已提供：
   - `subject.subjectId` → **Bangumi Subject ID**
   - `subject.biliSeasonId` → **Bilibili 番剧 season_id**（bilibili_sync 命中即得，可为 null）
   - `state.steam.appId`（`SteamGameEntity`）或 `sourceKey="steam:{appid}"` → **Steam appid**
   - `state.vndbBinding.vndbId`（如 "v17"）→ **VNDB id**
   - `state.anilistBinding.anilistId` → **AniList Media id**
   - `subject.title` / `subject.titleCN` → 搜索用标题
5. **Kazumi 源码结论**：Flutter 应用，`applicationId = com.predidit.kazumi`。**当前版本未注册任何自定义 URL Scheme / 深链**——AndroidManifest 仅有 LAUNCHER 的 `MAIN` intent-filter，无 `app_links`/`uni_links` 插件；`kazumi://` 仅是插件分享链接的 base64 编码，不是深链。因此**对原版 Kazumi 只能「拉起 App」（launch-only）**。但 Kazumi 内部**已有路线与原生桥**，具备被深度对接的潜力（见第六节）。
6. **现有缺口**：Niriko 没有通用的「拉起 App / 打开 URL / 检测已装应用」工具（唯一外链打开是 `ui/bilibili/BilibiliSyncScreen.kt` 里的 `ACTION_VIEW + startActivity`）。传送门需新增一个小的跳转层。

---

## 二、需求与定位

「传送门」= 详情页一个按钮 → 弹出按 `SubjectType` 分组的菜单 → 每一项把一个作品「跳」到另一个 App / 网页。

**核心架构决定**：把每个跳转目标定义成数据条目（注册表），用统一的「三级降级」走完，而不是一长串 if/else。

```
精确跳转（有 ID）  →  scheme 带 ID 直接定位
      ↓ 未安装 / scheme 失败
搜索跳转（无 ID）  →  Intent + 包名 + 标题关键词
      ↓ 仍失败
网页兜底（保底）   →  打开各平台网页版搜索 / 详情页
```

---

## 三、跳转对象取舍（按类型）

原则：优先做**有精确 ID** 的（Niriko 已持有，体验最好）→ 其次**按标题搜** → 最后**仅拉起 App**。

### 动画 ANIME
| 目标 | 值 | 依据 ID | 跳转方式 | 级别 |
|---|---|---|---|---|
| Bilibili 番剧 | ★★★ | `biliSeasonId` | `bilibili://bangumi/season/<seasonId>` 精确 | **P0** |
| Bangumi App (czy0729) | ★★★ | `subjectId` | 通用链接 `https://bgm.tv/subject/<id>`（优先）或 `bangumi://subject/<id>` | **P0** |
| Kazumi | ★★☆ | 无 | 仅拉起 `com.predidit.kazumi`（用户已装比例高）；若 fork 可深链（见第六节） | **P0** |
| AniList / Aniyomi / Dantotsu | ★★☆ | `anilistId` | scheme 跳转（需对应 App 已装） | P1 |

### 漫画 MANGA
| 目标 | 值 | 依据 ID | 级别 |
|---|---|---|---|
| Mihon | ★★★ | 无（按标题搜） | P1 |
| VeneraNext | ★★★ | 无 | P1 |
| Kotatsu / Kototoro | ★☆ | 无 | P2 |

> 漫画阅读器没有统一跨平台 ID，Niriko 也不持有它们的「源 + 漫画ID」，只能**拉起 App + 用户手动搜**。菜单项做成「打开 App」，若其支持 search intent 则带标题，否则只拉起。这与「Venera → VeneraNext」的结论一致——值得做，但只能做「拉起」。

### 音乐 MUSIC
| 目标 | 值 | 方式 | 级别 |
|---|---|---|---|
| 网易云音乐 | ★★★ | `orpheus://search?keyword=<title>` + web 兜底 | P1 |
| QQ 音乐 | ★★★ | `qqmusic://` search | P1 |
| Spotify | ★★☆ | `spotify:search:<title>` + open.spotify.com | P2 |

### 游戏 GAME
| 目标 | 值 | 依据 ID | 方式 | 级别 |
|---|---|---|---|---|
| Steam | ★★★ | `steamAppId` | `steam://store/<appid>` 精确 | **P0** |
| VNDB（视觉小说） | ★★★ | `vndbId` | `https://vndb.org/v<id>`（无官方 App，开网页） | **P0** |
| TapTap | ★★☆ | 无 | search scheme + 网页 | P1 |
| 应用商店（Play / App Store） | ★★☆ | 无 | store 搜索链接 | P2 |

### 三次元 REAL / 书籍 BOOK
| 目标 | 值 | 方式 | 级别 |
|---|---|---|---|
| Bilibili | ★★★ | search / season | P1 |
| 腾讯视频 / 爱奇艺 / 优酷 | ★★☆ | search scheme + 网页 | P2 |
| Netflix / Disney+ | ★☆ | search + 网页 | P2 |
| 书籍 → 豆瓣 / 网页 | ★★ | 搜 | P2 |

### 落地建议（克制版）
**真正的 P0** 只有 5 个，撑起第一版且都用到 Niriko 已在手的数据：**Bilibili(番剧)、Steam、VNDB、Bangumi App、Kazumi(拉起)**。
建议第一版只做「有精确 ID 的精确跳转 + 能稳定拉起的 App」，把「按标题搜 / 网页兜底」做成统一降级能力，而非每个目标各写两套。这样改动最小、价值最高、最易维护。

---

## 四、技术方案（Niriko 侧）

### 4.1 数据模型：PortalTarget 注册表
新增 `PortalTarget` + 按 `SubjectType` 分组注册表 `PortalRegistry`。每个 target 声明：适用类型、`appName`、`packageName`、图标/品牌色、`resolve(subject, state) -> PortalAction?`。

```kotlin
sealed interface PortalAction {
    data class UriScheme(val uri: String) : PortalAction
    data class LaunchPackage(val packageName: String, val searchTitle: String? = null) : PortalAction
    data class WebUrl(val url: String) : PortalAction
}

data class PortalTarget(
    val id: String,                 // "kazumi" / "steam" / "bilibili"...
    val appName: String,
    val packageName: String?,        // 仅拉起用
    val subjectTypes: Set<SubjectType>,
    // 由详情数据解析成具体动作；缺 ID 时返回 null（该 target 对当前条目失效）
    val resolve: (SubjectEntity, SubjectDetailUiState) -> PortalAction?,
)

object PortalRegistry {
    fun targetsFor(type: SubjectType): List<PortalTarget> = all.filter { type in it.subjectTypes }
}
```

菜单 = `PortalRegistry.targetsFor(subject.type)`，天然可扩展、可单测。

### 4.2 三级降级实现
- 精确：有 ID → 构造 scheme（`bilibili://bangumi/season/xxx`、`steam://store/xxx`）
- 搜索：无 ID → 构造 search scheme（`bilibili://search?keyword=标题`、`orpheus://search?keyword=标题`）
- 网页兜底：scheme 失败 / 未装 → 打开平台网页搜索 / 详情页（`https://bgm.tv/subject/id`、`https://www.bilibili.com/search?keyword=`、`https://store.steampowered.com/app/<id>`）

判定：`Intent(ACTION_VIEW, uri).resolveActivity(pm)` 能找到处理者 → 用 `setPackage()` 限定 + `FLAG_ACTIVITY_NEW_TASK` 启动；找不到 → 降级网页。

### 4.3 Android 11+ 包可见性（必须做）
minSdk=26 / targetSdk=34。Android 11+ 默认查不到未列出包，`resolveActivity` 返回 null。**必须在 `app/src/main/AndroidManifest.xml` 加 `<queries>`**，列出候选包名或 `ACTION_VIEW` intent-filter（当前 Manifest 尚无 `<queries>`）。这是落地的硬前提，第一版就要做。

### 4.4 跳转封装 PortalLauncher
`util/PortalLauncher.kt`：
- `fun launch(action: PortalAction)`：统一 `startActivity`，带 `FLAG_ACTIVITY_NEW_TASK`
- `catch ActivityNotFoundException` → Snackbar「未安装或无法打开」+ 可选自动走网页兜底
- 搜索标题用 `titleCN ?: title`，复用现有 `util/TitleResolver` 归一化
- 跳转失败**绝不**影响详情页

### 4.5 交互设计
- 按钮：顶栏右侧、分享按钮旁，`Icons.Outlined.OpenInNew` / `GridView` 图标 IconButton，contentDescription「传送门」
- 菜单：`DropdownMenu`（与分享菜单一致），按 `SubjectType` 分组展示
- 已安装 / 可精确跳转的项高亮；仅能拉起的项显示「打开」提示；可隐藏未装 App
- 搜索型 target 副文案显示将搜索的标题（如「搜索：进击的巨人」），给用户预期

---

## 五、ID 生态映射（Niriko 的差异化优势）

| 目标平台 | Niriko 已持有 | 精确跳转用 |
|---|---|---|
| Bilibili 番剧 | `biliSeasonId` | `bilibili://bangumi/season/<id>` |
| Bangumi | `subjectId` | `https://bgm.tv/subject/<id>` / `bangumi://` |
| Steam | `steam.appId` / `sourceKey` | `steam://store/<appid>` |
| VNDB | `vndbBinding.vndbId` | `https://vndb.org/v<id>` |
| AniList | `anilistBinding.anilistId` | 相关客户端 scheme |

这些绑定本就是 Niriko 为「补充数据源」维护的（`steam_bindings` / `vndb_bindings` / `anilist_bindings` 表），传送门零成本复用。

---

## 六、Kazumi 深度对接：能否自动填入搜索？【重点】

### 6.1 结论
**能实现「自动填入搜索 / 定位到具体番剧」，但必须 fork / 自行 patch 并重新构建 Kazumi。** 原版上架 Kazumi **无法**通过外部跳转自动填入内容，因为它没有注册任何深链 / URL Scheme，外部 App 无法向其写入参数。

### 6.2 为什么值得（Kazumi 内部已具备 90% 条件）
通读 Kazumi 源码后发现其**已具备**深度对接所需的三块基石：

1. **搜索路由已支持「预填 + 自动搜索」**：`lib/pages/search/search_module.dart` 已注册 `/search/:tag`，把 `inputTag` 传给 `SearchPage`；`lib/pages/search/search_page.dart` 的 `initState`（第 45–53 行）检测到 `inputTag != ''` 时，构造 `'tag:'+keyword` 并调用 `_applyFilterState(..., search: true)` 自动触发搜索。
2. **已有原生 ↔ Dart 桥**：`android/.../MainActivity.kt` 使用 `AudioServiceActivity`，并已初始化多个 `MethodChannel`（如 `com.predidit.kazumi/intent`），有仿 `notifyFlutterPipAction` 的「原生 → Dart 通知」现成模式；`MainActivity` 甚至已有 `openWithMime` 处理外部 URL。
3. **主键互通**：Kazumi 与 Niriko 都以 **Bangumi subjectId** 为主键，跳转参数可无缝对齐。

### 6.3 需要改动的点（Kazumi 侧，最小集）
- **AndroidManifest.xml**（`android/app/src/main/AndroidManifest.xml`）：给 `MainActivity` 加自定义 scheme intent-filter：
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:scheme="kazumi" />
  </intent-filter>
  ```
- **MainActivity.kt**：接收冷启动 `onCreate` / 热启动 `onNewIntent` 的 `intent.data`，经 `MethodChannel` 推给 Dart（复用现成 `intent` 通道或新增 `deepLink` 通道），仿照「原生 → Dart」回调写法。
- **Dart**：在 `app_widget.dart` / `main.dart` 里注册（或用 `app_links` 插件更省事，但需新增依赖）监听 deep link。收到 URI 后，用 `Modular.to.navigate('/search/<encodedKeyword>')` 跳到搜索页。
- **SearchPage 微调**：当前 `inputTag` 会被强制加 `tag:` 前缀（用于标签筛选）。对**普通关键词**需新增一种语义（例如新路由 `/search/query/:q` 或 `initialQuery` 参数），使其设置 `searchController.text = q` 后调用 `_submitSearch(q)` 而不是 `tag:` 前缀。

### 6.4 两种跳转目标（取决于是「搜」还是「精确」）
| 方式 | URI 形态 | 效果 | 等级 |
|---|---|---|---|
| 按标题搜 | `kazumi://search?q=<urlencoded 标题>` | 打开 Kazumi 搜索页并自动填入 + 触发搜索 | 通用（无 ID 也可用） |
| 精确定位 | `kazumi://info?id=<Bangumi subjectId>` | 构造最小 `BangumiItem(id: x)` 后 `Modular.to.navigate('/info')` 直接打开该番剧 | 更佳（Niriko 已有 subjectId） |

> 注：`infoModule` 的路由要求 `state.arguments is BangumiItem`（`lib/pages/info/info_module.dart`），故需由深链构造一个携带 `id` 的最小 `BangumiItem` 后导航；其余字段 `InfoPage` 会按 id 查询补全。

### 6.5 前提与代价
- **必须自建 Kazumi 分发包**（fork + 打补丁 + 重新构建）；**用户设备上只有装了「打过补丁的 Kazumi」才生效**。
- 原版 Kazumi 用户只能走「仅拉起」降级。
- 因此建议把 Kazumi 在传送门里做成**两级**：
  1. 若检测到已装 Kazumi → 优先用 `kazumi://search` / `kazumi://info` 精确跳转；
  2. 任一失败 → 降级「仅拉起 `com.predidit.kazumi`」；未装 → 提示 / 隐藏。

### 6.6 若不 fork 的降级方案
仅「拉起 App + 提示用户自己搜」，或改为复制标题到剪贴板 + 提示粘贴到 Kazumi 搜索框。体验一般，是下策。

---

## 七、改动文件清单（Niriko 侧）

| 文件 | 改动 |
|---|---|
| `data/model/PortalTarget.kt` | 新增 target / action 模型 + `PortalRegistry` |
| `util/PortalLauncher.kt` | 新建跳转 / 检测 / 三级降级封装 |
| `ui/subject/SubjectDetailScreen.kt` | 顶栏加「传送门」按钮 + `DropdownMenu`（338–394 行区间） |
| `app/src/main/AndroidManifest.xml` | 新增 `<queries>` 包可见性 |
| （建议）`viewmodel/SubjectDetailViewModel.kt` | 若需，暴露 `portalTargets(type)`；推荐由 UI 读 `state` 构建，ViewModel 不直接依赖跳转 |

---

## 八、风险与不确定点

1. **URL scheme 不一定公开 / 稳定**：网易云、QQ音乐、Spotify、TapTap、Mihon/Aniyomi 的具体 scheme 需**真机逐一验证**。注册表 + 三级降级正好兜住：scheme 失效自动落网页。
2. **Bangumi App(czy0729) 深链有兼容问题**（业界有案例分析），建议优先用通用链接 `https://bgm.tv/subject/<id>`，真机验证后再定。
3. **漏加 `<queries>` 会静默降级**，务必上线前全量核对候选包名。
4. **私密 / 无网**：跳转本身不需网络；网页兜底需联网，断网时降级为「复制标题到剪贴板」。
5. **Kazumi 需自建分发包**才支持深链，注意版本管理与分发说明。

---

## 九、开发里程碑

- **P0（第一版，改动小价值高）**：Bilibili 番剧(`biliSeasonId`) / Steam(`appId`) / VNDB(网页 `vndbId`) / Bangumi App(`subjectId`) / Kazumi(仅拉起 + 预留深链位)。先写 `PortalLauncher` + `AndroidManifest <queries>` + 顶栏按钮 / 菜单。
- **P1**：Mihon / VeneraNext(漫画拉起)、网易云 / QQ音乐(搜索)、AniList / Dantotsu。
- **P2**：Spotify、TapTap、腾讯 / 爱奇艺 / 优酷 / Netflix、Kotatsu / Kototoro、书籍平台。
- **可选延伸**：Kazumi 深链 patch（见第六节），可在 P1 之后单独作为「Kazumi 定制版」协作项。

---

## 十、验收清单（建议）

- [ ] 详情页顶栏出现「传送门」图标，点击弹出按类型分组的菜单
- [ ] 动画条目 → 出现 Bilibili / Bangumi / Kazumi 等；游戏 → Steam / VNDB
- [ ] 有 ID 的条目走精确 scheme，未装时自动降级网页兜底
- [ ] 未安装的 App 隐藏或不报错
- [ ] 跳转失败弹出 Snackbar，且不影响详情页
- [ ] Android 11+ 真机确认 `<queries>` 生效（能正确检测已装应用）
- [ ] 搜索型 target 的副文案显示将搜索的标题

---

## P1 实施记录（本轮完成）

已在 Niriko 中实现 P1 目标，`PortalRegistry` 新增：

- **AniList**（ANIME/MANGA）——精确跳转 `https://anilist.co/<anime|manga>/<anilistId>`（复用 `anilistBinding.anilistId`，自动区分 anime/manga）
- **网易云音乐**（MUSIC）——搜索 `orpheus://search?keyword=<title>` + 网页兜底 `https://music.163.com/#/search/m/?s=<title>`
- **QQ音乐**（MUSIC）——搜索 `qqmusic://search?keyword=<title>` + 网页兜底 `https://y.qq.com/n/ryqq/search?w=<title>`
- **Mihon**（MANGA）——仅拉起 `eu.kanade.tachiyomi`；未安装时菜单项自动隐藏

新增能力：
- `PortalLauncher.anyLaunchable(context, actions)`：菜单过滤已安装/可启动目标。带网页兜底的（音乐/AniList）必然可见；仅拉起的（Mihon）未装即隐藏。
- `AndroidManifest` 的 `<queries>` 追加 `orpheus`、`qqmusic` scheme 与 `com.netease.cloudmusic`、`com.tencent.qqmusic`、`eu.kanade.tachiyomi` 包。

### 待确认（P2 / 下一步）
- **VeneraNext**、**Danotsu** 的 Android 包名无法在无网络环境下确认，本轮暂未接入（避免错误包名导致静默隐藏）。真机/仓库确认包名后按 `LaunchPackage` 模式补一行即可。
- 网易云 / QQ 音乐搜索 scheme 的精确字符串（`keyword` 参数名等）建议真机点亮验证；失效时自动走网页兜底。


---

## 十一、补充：Bangumi App (czy0729) 深链确认（已读源码）

阅读 `C:\\what i download\\Bangumi-master`（React Native / Expo App）确认：

- **Android 包名**：`com.czy0729.bangumi`（`app.json` 的 expo.android.package）
- **自定义 scheme**：Expo `scheme: "bangumi"` → 注册 `bangumi://` 与 `com.czy0729.bangumi://`
- **App Links**：`MainActivity` 的 intent-filter 还声明了 `http/https` + 主机 `bgm.tv` / `bangumi.tv` / `chii.in`，但**未加 `android:autoVerify="true"`**，属于"非验证 App Link"——点击会弹「打开方式」选择，默认不直启 Bangumi。
- **应用内路由**：深链由 `src/components/deep-link/index.tsx` → `appNavigate`（`src/utils/app/app.ts`）处理；其调用 `matchBgmLink`，**只有形如 `https://bgm.tv/subject/<id>` 的链接才会在应用内 `navigation.push` 到条目页**；`bangumi://subject/<id>` 之类因不含 `HOST`（`https://bgm.tv`）会被当作外部链接交给浏览器（浏览器打不开，等于无效）。

### 结论
- 对 Bangumi App 的最佳跳转是 **`https://bgm.tv/subject/<id>`**（应用内可直达条目页；应用未接管时至少浏览器打开网页，天然兜底）。**这正是 P0 当前实现的 `WebUrl` 方案。**
- 因非 autoVerify，真机上很可能弹「打开方式」（Bangumi / 浏览器）。若想要"一键直启 Bangumi"，需该 App 侧加 `android:autoVerify="true"` + assetlinks 配置（其自行改动），或接受 chooser。
- 不建议用 `bangumi://subject/<id>`：只会拉起 App / 无效；`getLaunchIntentForPackage("com.czy0729.bangumi")` 仅到首页。

### 下一步计划更新
- P0 已实现（`PortalTarget.kt` / `PortalLauncher.kt` / 顶栏入口 / `<queries>`）。
- 可将 `com.czy0729.bangumi` 加入 `<queries>` 以在菜单标记「已安装」；是否额外提供「打开 Bangumi 首页」由产品取舍。
- P1 / P2 优先级不变（Mihon / VeneraNext / 网易云 / QQ音乐 / Spotify / TapTap / 视频平台）。

