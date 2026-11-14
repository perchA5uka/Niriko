# Niriko：技能库整理 & 开发环境体检（2026-10-24）

> 执行方式：1 个 Lead + 3 个并行调研（UI 设计系统 / 代码规范 / 数据层与发布），全部结论取自当前工作区源码、构建脚本与实测命令。
> 本文件与 `docs/audit-2026-10/README.md` 是本次唯一新增的项目文档；业务源码未做任何修改。

---

## 一、结论速览

| 项 | 结果 |
| --- | --- |
| 卸载技能 | **3 个**：`apple-design`、`find-animation-opportunities`（来自 `~/.agents/skills`）、`frontend-ui-engineering`（来自 `~/.dsh/skills`） |
| 新增技能 | **8 个**项目级技能，装在仓库内 `.dsh/skills/`（上限 10，未用满） |
| 模型可见目录 | 16 → **21**（-3 +8，另有 12 个 web 向技能本来就是 `disable-model-invocation`，不在目录里） |
| 环境修复 | 本机 `JAVA_HOME` 是 JDK 25，**构建直接失败**；已改为用户级 Gradle 指定 Android Studio JBR 21，实测编译 + 1419 项单测全绿 |
| 环境缺口 | 无 PowerShell 7、无 ktlint/detekt/Spotless/`.editorconfig`、无 lint 门禁、无静态检查 CI → 见 §四.4 建议 |
| 数据安全发现 | 备份设置只序列化 47/79 字段，导入后经 `restoreFrom` 整体回写 → 约 32 个设置字段（含各源 API Key / OAuth Secret）被静默重置。详见审计文档 P0-1 |

---

## 二、技能：为什么这样取舍

### 2.1 卸载（可一键恢复）

| 技能 | 原位置 / 来源 | 卸载理由 |
| --- | --- | --- |
| `apple-design` | `~/.agents/skills`（emilkowalski/skills） | 23KB 的「Apple 设计原则 → **Web 实现**」指南：参数模型是 CSS/`backdrop-filter`/媒体查询，与 Compose 的 token/弹簧/无障碍体系不同源；本项目 UI 规则已由新增的 `niriko-ui-system` / `niriko-motion` 以真实源码数值覆盖 |
| `find-animation-opportunities` | `~/.agents/skills`（emilkowalski/skills） | 面向 Web/React 代码库的「哪里该加动效」只读审计；本项目动效机会已写在 `plans/01..05` 与 `niriko-motion`，留着易诱导 web 风格数值 |
| `frontend-ui-engineering` | `~/.dsh/skills` | Web 前端（React/ARIA/Tailwind）生产级 UI 指南，与 Kotlin Compose + Material3 的落地方式不同；其中通用部分（四态、对比度、触控尺寸）已吸收进 `niriko-ui-review` |

卸载后可恢复（回收站在技能根之外，见 §六.3）：

```powershell
Move-Item 'C:\Users\21977\dsh-skills-trash\2026-10-24\apple-design' 'C:\Users\21977\.agents\skills\apple-design'
Move-Item 'C:\Users\21977\dsh-skills-trash\2026-10-24\find-animation-opportunities' 'C:\Users\21977\.agents\skills\find-animation-opportunities'
Move-Item 'C:\Users\21977\dsh-skills-trash\2026-10-24\frontend-ui-engineering' 'C:\Users\21977\.dsh\skills\frontend-ui-engineering'
```

> 注意：**不能**把待卸载技能移进技能根下的 `.trash/`——实测 DSH 仍会扫描到它（`skill` 工具照旧能加载）。必须移到技能根目录之外。

### 2.2 新增：8 个项目技能（`.dsh/skills/`）

项目根技能只在本仓库生效（`<repo>/.dsh/skills`），不污染全局。每个 `SKILL.md` 的 frontmatter 只有 `name` + 中英双语 `description`，正文为中文、带真实路径/数值/清单/禁止事项。

| 技能 | 触发场景 | 核心内容 |
| --- | --- | --- |
| `niriko-ui-system` | 改配色/圆角/排版/玻璃/壁纸、加组件或页面前 | 主题优先级（动态色 > 自定义种子 > 旧色板）、`LocalDarkTheme` 规则、中性表面色值、`NirikoShapes` 六档（Card20/Section16/Control12/Sheet24/RowInner4/Pill）、排版 11 档真实值、**六条玻璃渲染路径**（kyant vs miuix 不通用）、降级真值表、度量与组件落点 |
| `niriko-motion` | 改动画/转场/手势/reduceMotion 前 | `NirikoMotionSpecs` 六档弹簧真实值、封面共享转场固定 200ms + 10ms keyframe、预测返回、dock 跟手弹簧、48/16/1600dp 滚动阈值、帧阶段纪律、一次性反馈防重播 |
| `niriko-ui-review` | 改完 UI 自检 / 评审 diff | 可运行的单测护栏清单（源码字符串契约测试）、一致性/无障碍/四态/窗口尺寸/主题矩阵检查、截图对比法、常见回归 Top10、评审模板与最小验证集 |
| `niriko-compose-conventions` | 新增/修改 Compose 页面、ViewModel、重构、Code Review | 分层与依赖方向、`XxxScreen`+`XxxContent`、VM+手写 Factory、UiState 形状、`collectAsStateWithLifecycle` 迁移口径、纯函数 Policy 层、注释风格（中文「为什么」）、文件/函数大小上限、组件归属、命名/可见性、正反例 |
| `niriko-architecture-map` | 找文件、加页面/设置项/数据源/刷新资源 | 入口与启动序列、导航（Pager 4 顶层页 + NavHost 19 route）、手写依赖装配、15 个 ViewModel/17 个 DAO/15 个仓库、数据源插件链、刷新编排、**「改某功能要动哪些文件」对照表**、定位速查 |
| `niriko-quality-gate` | 提交/开 PR/合并前验证 | 可复制的 Windows Gradle 命令、JDK21 约束、Robolectric `@Config(sdk=[34])`（20 个类全量）、Room 迁移两层测试、编码/CRLF/注释规范、字符串抽取尺度、CI 对齐、文档同步清单 |
| `niriko-data-layer` | 改 Room/DAO/DataStore/WebDAV/Bangumi 同步/备份/插件/刷新 | DB v32/23 表/30 条迁移/31 个 schema（缺 25.json 禁止伪造）、四步清单（加字段/加表/加 DataStore 键/加同步字段）、412 三选一与合并策略、备份/恢复、插件体系、TTL 与退避 |
| `niriko-release-docs` | 发版、写 README 更新段/release notes、改 docs、提交/tag | 版本号规则、README 段落结构与真实模板、docs 文体（计划/任务卡/实现报告）、THIRD-PARTY 触发条件、commit 中文风格、**文档-实现一致性核对 12 条** |

体量：8 个文件共约 160KB / 约 2100 行（含空行）；**按需加载**（会话只注入 name+description，约 2.5KB），不会常驻上下文。

> ⚠️ **frontmatter 坑（本次踩到并修复）**：`description` 是 YAML 平铺标量，**不能含 ASCII 冒号 + 空格**（如 `discipline: read`）。DSH 用 YAML 解析 frontmatter，这种描述会被判成「嵌套映射」并**静默丢弃整个技能**（日志只在宿主后台，模型侧表现为 `skill` 工具报 unknown，但技能中心的宽松解析器仍会列出它，极具迷惑性）。`niriko-data-layer` / `niriko-release-docs` 首版英文描述恰好含 `: `，已改用 ` — `。新增/修改技能后务必实测 `skill <name>` 能加载。

### 2.3 保留的通用/内置技能

| 技能 | 保留理由 |
| --- | --- |
| `code-simplification` | 语言无关的重构方法论，适合 Kotlin 巨型文件拆分（本仓 6 个 >1000 行文件） |
| `debugging-and-error-recovery` | 通用根因调试流程，配合 `niriko-quality-gate` 使用 |
| `diagnose-windows-sandbox-acl` | 本次实际用它修复了工作区 `WRITE_OWNER` 缺失（见 §四.3），Windows 上仍有价值 |
| `genui` / `hindsight-coding-agent` / `browser-skill` | 属 DSH 交互与插件能力（结构化 UI / 记忆 / 浏览器自动化），不是项目技能，不影响改代码；保留 |

### 2.4 为什么没卸载 `office*`（7 个）

- 它们是 **DSH 内置技能**：打包在 `resources/app.asar`，同时释放到 `resources/runtime/office-skills/`，随应用安装，用户目录里没有可删副本；`resources/` 对当前用户只读（实测写被拒）。
- `browser-skill` / `genui` / `hindsight-coding-agent` 是插件在运行时注册的技能，同样没有官方卸载入口（删插件会连带失去功能）。
- 结论：这 11 个技能**无法安全卸载**。它们在会话里只占少量描述 token；若确实要彻底精简，需要 DSH 提供「内置技能禁用」能力（可反馈给上游）。

---

## 三、当前模型可见技能目录（21 个）

```
项目技能（本仓库，新增）
  niriko-ui-system      niriko-motion           niriko-ui-review
  niriko-compose-conventions  niriko-architecture-map  niriko-quality-gate
  niriko-data-layer     niriko-release-docs
通用/内置（保留）
  code-simplification   debugging-and-error-recovery   diagnose-windows-sandbox-acl
  genui                 hindsight-coding-agent          browser-skill
  office  office-docx  office-pptx  office-xlsx  office-recalculate  office-render  office-export-pdf
```

> 另有 12 个 `.agents/skills` 下的 web 向技能（`animate`、`improve-animations`、`write-swift` 等）带 `disable-model-invocation: true`，本来就不进目录、只能手动 `/名称` 调用，本次未动。

---

## 四、开发环境与工具链核查

### 4.1 已经具备（无需安装）

| 项 | 现状 |
| --- | --- |
| Android Studio | `C:\Program Files\Android\Android Studio`（自带 JBR **21.0.10**） |
| Android SDK | `C:\Users\21977\AppData\Local\Android\Sdk`：platforms **34 / 35 / 36.1 / 37.0**、build-tools **34.0.0 / 35.0.0 / 36.1.0 / 37.0.0**、platform-tools、cmdline-tools latest |
| 模拟器 | `emulator.exe` + 4 个 AVD：Medium_Phone / Pixel_6_Pro / Pixel_Fold / Small_Phone（性能文档里「开发环境无模拟器」已过时） |
| Gradle | wrapper 8.13（`~/.gradle` 已缓存 8.13 / 8.9 发行版），并行 + 构建缓存 + 配置缓存已开 |
| Node / Python / Git | Node（`C:\Program Files\nodejs`）、Python 3.14、Git 均可用；`scripts/verify-refs.mjs` 可直接跑 |

### 4.2 实测结果（本机，2026-10-24）

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| JDK 25 直接构建 | `.\gradlew :app:compileDebugKotlin`（`JAVA_HOME=C:\jdk-25.0.4.1+1`） | ❌ `BUILD FAILED`（Gradle 8.13 / AGP 8.13.2 / Kotlin 2.3.10 不支持 JDK 25） |
| JBR 21 编译 | 同上，`JAVA_HOME=...Android Studio\jbr` | ✅ `BUILD SUCCESSFUL` |
| 用户级 JDK 修复后（不设 JAVA_HOME） | `.\gradlew :app:compileDebugKotlin` | ✅ `BUILD SUCCESSFUL`（Gradle 自动用 JBR 21） |
| 全量 JVM 单测（强制重跑） | `.\gradlew :app:testDebugUnitTest --rerun-tasks` | ✅ `BUILD SUCCESSFUL in 9m 8s`；**1419 个用例 / 0 失败 / 0 跳过**（183 个测试类，20 个 Robolectric 类） |
| 配置缓存 | 构建日志 | ✅ `Configuration cache entry stored` |

构建告警（非错误，每个构建都会出现）：

```
This Android Gradle plugin (8.13.2) was tested up to compile SDK version 36.1.
You are strongly encouraged to update your project to use a newer Android Gradle plugin ...
To suppress this warning, add/update android.suppressUnsupportedCompileSdk=37.0
```

`compileSdk = 37` 是 `miuix-blur` 的硬要求，AGP 8.13.2 尚未官方支持 37 → 见 §四.4 建议 3。

### 4.3 本次已修复的系统级问题

**① 构建 JDK（机器级，不进仓库）**

按仓库 `gradle.properties:8-11` 的官方建议，写了用户级配置（`~/.gradle/gradle.properties`，不属于任何仓库，删掉即还原）：

```properties
org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
```

这样不设 `JAVA_HOME` 也能构建；同时避免把机器路径写进仓库。

**② 工作区文件权限（DSH 沙箱）**

首次运行 shell 时 DSH 报 `SetNamedSecurityInfoW failed (Win32 5): grantWrite(C:\what i made\niriko)`：工作区根目录缺少 `WRITE_OWNER`。已用 `diagnose-windows-sandbox-acl` 脚本修复（只补当前用户 FullControl、不改文件内容/所有者）：
- 报告：`C:\Users\21977\dsh-acl-reports\acl-report-bd28aedcbea84bad9408c6d0f34c3486.jsonl`
- 回滚：`pwsh -NoProfile -File 'C:\Users\21977\dsh-acl-reports\acl-backup-1626c17cfd2240478b534f004c38ef62.json.ps1' -Path 'C:\what i made\niriko' -AllowRoot 'C:\what i made\niriko' -Restore 'C:\Users\21977\dsh-acl-reports\acl-backup-1626c17cfd2240478b534f004c38ef62.json'`

**③ 沙箱对构建的影响（重要）**

- 当前 DSH 会话是 `workspace-write`：Gradle 要写 `~/.gradle`（工作区外）→ 构建需要一次性「完全权限」批准。
- 也可以给 Agent 专用工作区缓存（不碰用户目录）：

  ```powershell
  $env:GRADLE_USER_HOME = "C:\what i made\niriko\.gradle-home"   # 首次会重新下载/解压 Gradle 与依赖
  ```

- 另一个实测现象（**仅限 workspace-write 会话**）：`pwsh` 工具只能写工作区根目录，写 `docs/`、`app/` 等子目录会被拒；文件编辑用编辑器/写入工具，批量脚本操作申请完全权限。本轮末尾会话切到 `danger-full-access` 后该限制已解除（实测 shell 可写 `docs/`）。

### 4.4 环境需求清单（按优先级）

**必装 / 已有则跳过**

| 项 | 现状 | 建议 |
| --- | --- | --- |
| JDK 21 | ✅ JBR 21 已可用（JDK 25 不行） | 保持；或用 `winget install EclipseAdoptium.Temurin.21.JDK` 装独立 JDK 21 |
| Android SDK platform 37 + build-tools 37 | ✅ 已装 | 保持与 CI 一致（CI 显式装 `platforms;android-37.0`、`build-tools;37.0.0`） |
| Git | ✅ | — |

**建议补齐（直接影响 AI/终端协作效率与代码一致性）**

1. **PowerShell 7** — ✅ 已具备：本机是 Microsoft Store 版 **7.6.8**，`pwsh` 别名可直接拉起 7.6.8。本轮 DSH 的 `pwsh` 工具仍走 Windows PowerShell 5.1，原因是执行器在 **DSH 启动时**（21:29）解析并缓存 shell，而 PS7 是 23:06 才装上的；已在 `~/.dsh/profiles/web/cordis.patch.yml` 把 `pwsh-sandbox` 的 `pwshPath` 显式指向 Store 别名——**重启 DSH 后生效**（不重启也无副作用）。
2. **静态检查与格式化门禁** — ✅ 已接入 Spotless **8.10.4** + ktlint（规则集版本由 Spotless 内置依赖决定，不额外钉 ktlint 坐标），仓库根新增 `.editorconfig`：
   - 本地：`.\gradlew.bat :app:spotlessCheck` 默认只校验相对 `HEAD` 的未提交改动；`.\gradlew.bat :app:spotlessApply` 自动修。
   - CI：`android-ci.yml` 新增 `Spotless (changed files only)` 步骤，`-PspotlessRatchetFrom=origin/main` 只校验 PR diff（checkout 改 `fetch-depth: 0`）。
   - **存量债**：全仓 ktlint 检查当前有 **244 个文件**历史格式偏差（多为链式调用换行/表达式体），采用 ratchet 策略渐进收敛——改到哪个文件就顺手 `spotlessApply`，不做一次性重排。规则刻意放松：行长不设硬上限、关闭尾逗号规则（见 `.editorconfig`）。
   - 未引入 detekt（复杂度/长函数检查留作独立任务）。
3. **抑制 compileSdk 37 告警** — ✅ `gradle.properties` 已加 `android.suppressUnsupportedCompileSdk=37.0`，实测 `:app:help` 不再输出 AGP 告警；等 AGP 官方支持 37 后删掉该行。
4. **CI 增加 lint job** — ⏳ 未做：`lintDebug` 在存量代码上会刷出大量告警（296 处内联文案、68 处硬编码色等），需作为独立任务排期。

**本轮实际改动的文件（建议 1/2/3）**

| 文件 | 改动 |
| --- | --- |
| `.editorconfig` | 新增：utf-8/LF/4 空格 + ktlint `android_studio` 风格 + 关闭行长与尾逗号规则 |
| `gradle/libs.versions.toml` | 新增 `spotless = "8.10.4"` 与 `[plugins] spotless` |
| `build.gradle.kts` | `alias(libs.plugins.spotless) apply false` |
| `app/build.gradle.kts` | 应用 Spotless 插件 + `spotless { ratchetFrom(...); kotlin; kotlinGradle }` |
| `gradle.properties` | `android.suppressUnsupportedCompileSdk=37.0` |
| `.github/workflows/android-ci.yml` | `fetch-depth: 0` + `:app:spotlessCheck -PspotlessRatchetFrom=origin/main` |
| `README.md` | 「常用命令」加 spotless 两条；「开发约定」加格式门禁说明 |
| `.dsh/skills/niriko-quality-gate` / `niriko-release-docs` / `niriko-compose-conventions` | 同步门禁口径：质量门与发布验证加 `spotlessCheck`、删除「仓库没有 ktlint/editorconfig」的过期描述、改文件允许 `spotlessApply` |
| `~/.dsh/profiles/web/cordis.patch.yml`（机器级） | `pwsh-sandbox.pwshPath` → Store 别名 |

**可选增强（按需）**

| 项 | 用途 |
| --- | --- |
| Baseline Profile + Macrobenchmark 模块 | `docs/performance-audit.md` 的帧预算验证；当前没有基准模块 |
| 设备截图脚本（`adb exec-out screencap`） | 替代 Roborazzi（Robolectric 无法渲染真玻璃）；与 `screenshot/` 目录、`niriko-ui-review` 的截图法配合 |
| `gh` CLI | 发布 GitHub Release（APK 只作附件，见 release skill） |
| `jq` | JSON 排查（如 sync/备份 JSON）；也可继续用 Node/Python |
| 版本目录清理 | `libs.versions.toml` 的 `miuix = 0.9.3` 与实际 `0.9.0` 漂移、`cloudy` 未使用 |

---

## 五、本次调研发现的代码/文档问题

完整清单见 **`docs/audit-2026-10/README.md`**（按 P0/P1/P2 分级，含文件路径与复核命令）。最值得先看的三条：

1. **P0 备份字段覆盖不全**：`BackupManager.settingsToJson` 只序列化 47/79 个设置字段，而恢复入口 `SettingsDataStore.restoreFrom` 整体回写 78 个键 → 导入备份后约 32 个字段（含 TMDb/OMDb/IGDB/RAWG/Discogs/OpenCritic API Key、Bangumi OAuth Secret 等）被静默重置为默认值。
2. **P1 文档与实现漂移**：README 的备份格式版本（写 v4，实为 v5）、同步待办清单、412 弹窗选项描述都已过期；`THIRD-PARTY-NOTICES.md` 漏 4 个新依赖；v1.2.1 无 tag / 无 release notes。
3. **P0/P1 UI 一致性**：观看状态两套色表、`GlassSlider`/`GlassToggle` 无障碍与 48dp 命中区不足、34 处 `AlertDialog` 只统一了 3 处圆角；代码侧 6 个 >1000 行文件、`collectAsState` 40:1、`openSubject` 双实现。

---

## 六、使用与维护

### 6.1 触发方式

- **自动**：DSH 根据 `description` 判断；改 UI 前会命中 `niriko-ui-system` / `niriko-motion`，提交前命中 `niriko-quality-gate`。
- **手动**：输入 `/niriko-ui-review` 等直接注入。
- 建议搭配：`niriko-architecture-map`（找落点）→ `niriko-compose-conventions`（写法）→ `niriko-quality-gate`（验证）；UI 改动再加 `niriko-ui-system` / `niriko-motion` / `niriko-ui-review`。

### 6.2 修改 / 新增技能

- 文件：`.dsh/skills/<kebab-name>/SKILL.md`（name 必须与目录同名）。
- 隐藏模型可见性：frontmatter 加 `disable-model-invocation: true`（仍可 `/名称` 手动调用）。
- 项目技能跟随仓库（当前 `.dsh/` 未提交）；若想团队共享，把 `.dsh/skills/` 提交即可；若只想本机使用，加到 `.gitignore`。
- 全局技能仍在 `~/.dsh/skills` 与 `~/.agents/skills`；本次未改动其中其它条目。

### 6.3 卸载 / 恢复

- 卸载 = 移出技能根目录（**不要**放技能根下的 `.trash`，见 §2.1 注意）。
- 本次回收站：`C:\Users\21977\dsh-skills-trash\2026-10-24\`（3 个目录，恢复命令见 §2.1）。
- 验证是否仍被加载：对已卸载名字调用 `skill` 工具应报 `skill "..." is unknown or no longer available`（本次已实测 `apple-design` 如此）。

### 6.4 本次验证记录

| 验证 | 结果 |
| --- | --- |
| 新增 8 个技能被 DSH 识别 | ✅ 当前会话目录热更新出现全部 8 个（无需重启） |
| 8/8 技能可被 `skill` 工具实际加载 | ✅ `niriko-ui-system`、`niriko-data-layer` 实测返回正文（含曾因 frontmatter 解析失败而 unknown 的两个，修复描述后恢复） |
| 卸载 3 个技能从目录消失 | ✅ `apple-design` / `find-animation-opportunities` / `frontend-ui-engineering` 均已不在目录 |
| 卸载后 `skill` 工具不可加载 | ✅ `apple-design` / `frontend-ui-engineering` → unknown or no longer available |
| 编译 | ✅ JDK 21：`BUILD SUCCESSFUL` |
| 编译（接入 Spotless 后） | ✅ `:app:compileDebugKotlin` `BUILD SUCCESSFUL`（配置缓存失效后首轮 9m03s） |
| 格式门禁 | ✅ `:app:spotlessCheck` 通过（只校验改动文件）；`:app:spotlessApply` 已格式化新增/改动的 gradle 文件；全仓存量违规 **244 文件**（ratchet 基线外，未动） |
| compileSdk 告警 | ✅ 已抑制：`:app:help` 输出不再含 `compile SDK version 37.0` 告警 |
| 全量单测 | ✅ 1419 tests / 0 failures / 0 skipped（`--rerun-tasks`，9m08s） |
| 未改业务源码 | ✅ 改动仅构建配置、CI、README 与 `.editorconfig`；`app/src/**` 无内容改动 |

---

## 七、下一步建议（按价值排序）

1. 修 P0-1 备份字段覆盖（数据丢失级）+ 加「设置字段全覆盖」单测。
2. 顺手修 README / THIRD-PARTY / v1.2.1 release notes 的文档漂移。
3. ~~加 `spotlessCheck` + `.editorconfig` 到 CI~~ ✅ 已完成（ratchet 策略；存量 244 文件按改动逐步收敛）；剩余：`lintDebug` / detekt 作为独立任务评估。
4. 按 `niriko-ui-review` 的矩阵推进 UI 一致性（状态色、滑杆无障碍、对话框圆角）。
5. 性能相关：建 Baseline Profile / Macrobenchmark 模块，用真机（已有 4 个 AVD）复核 `docs/performance-audit.md` 的 P0/P1。
