# Niriko v1.2.2

versionName: 1.2.2 · versionCode: 5

## 更新摘要

本版没有新表、没有迁移（Room 23 张表 / v32），也没有新增 DataStore 键；范围是「一致性 + 动效 + 反馈」收口。

- **二级设置页横向推入**：复用 v1.2.1 写好但零调用的 `enterFromRight/exitToRight`，并新增返回方向；顶部四页与封面共享转场不变
- **设置页对齐**：三种左缘（0/16/20dp）统一 16dp、「外观」页两档 20dp 回 24dp、行高 56/72dp、箭头统一 20dp、当前值只留文字、禁用态 38% 不透明、搜索框改胶囊玻璃
- **设置行按压反馈**：M3 圆水波换成整行矩形高光（α0.08，进快出慢）
- **玻璃开关可拖拽**：跟手拖动 + 速度/过半落位 + 按下放大 8%；玻璃滑块按下放大 10%
- **卡片受光跟手**：倾斜压卡时高光角度 ±20° 偏移；不倾斜时逐像素等于改动前
- **菜单 / 面板 / 折叠 / 封面**：锚定菜单从触发控件缩放展开并有退场；非模态面板松手带速度惯性；折叠区块高度展开 + 箭头旋转；封面 150ms 淡入（飞行期不淡入）
- **详情页三处破相**：「详细信息」标题不再被 420dp 滚动区裁切；剧集结论条窄屏不再重叠；「评分分布」包进玻璃卡并与评分卡左缘对齐
- **反馈宿主**：窗口层统一 Snackbar（收藏 / 发现 / 搜索 / 统计页此前全静默，且给 dock 让出 96dp）；分享图失败不再永久「生成中…」；评分刷新失败保留旧数据
- **多选可见性**：列表视图选中项描边环；多选时长按不误弹浮层
- **令牌收口**：`MaterialTheme` 补 `shapes`（4/8/12/16/24）与新 `headlineSmall`(24sp/SemiBold)；六档弹簧令牌未改
- **输入框统一**：全站 37 处 M3 描边输入框 → `NirikoTextField`（12dp 圆角、半透明填充、聚焦细描边）
- **选集 chip 玻璃化**：详情页「选集」从 8dp 方块改胶囊（保留长按）
- **「减少动态效果」收口**：搜索卡 12 个过渡值、筛选面板展开、发现页 tab 底色、顶栏滚动隐藏、底栏重选回顶全部降级为瞬时；第二批再补 9 处折叠面板的展开收起、详情页材质墙与 chrome 收起、状态分段色、列表与搜索结果的内容尺寸变化。跟手/按压/触感刻意保留
- **评分弹跳只在改分时播**（原来进详情页就先弹一下）
- **骨架 → 正文的揭示**：详情页 / 统计页从同帧硬切改为 180ms 淡入包络（0.55→1）；首次进入才播，下拉刷新不压暗
- **列表 ↔ 网格换视图**：作品库换视图改为「呼吸式」替换（先压到 60% 再 180ms 回满）；刻意不用 `AnimatedContent`，避免两棵 Lazy 同时组合
- **圆角令牌化**：83 处与令牌等值的手写圆角（999 / 24 / 20 / 16 / 12 / 4dp 与 `percent = 50`）改为引用 `NirikoShapes` 六档令牌（零像素变化）；剩 60 处无对应档位的小圆角记账，新增 `ShapeLiteralPolicyTest` 锁死基线
- **动效时长令牌化**：顶/底栏与详情 chrome 的 220 / 180ms、内容尺寸的 300ms 改走 `CHROME_HIDE_DURATION_MS` / `DETAIL_CHROME_DURATION_MS` / `AnimDurationNormal`（**取值一个毫秒未改**），新增 `MotionLiteralPolicyTest` 锁住手写时长
- **强调色令牌化**：11 个跨文件重复写的语义色（评分金 / 集数蓝 / 收藏红 / 状态四色 / 日历四色）收进 `ui/theme/AccentColors.kt`（取值不变），新增 `AccentLiteralPolicyTest` 防止再散写；图表柱条改 4dp 圆角、标签由写死 24 像素改 `8.sp`（3x 屏逐像素一致）；收藏概览胶囊弱形态由半透明改实底并过 `ensureReadable`

## 第八批（2026-10-31）：控件语言四项 + 帧阶段 + 反馈收口

> 这一批把「同名元素只有一种长相」从设置树推到全站，并把动效从「有过渡」推到「按帧阶段跑」。审计输入是 `docs/v1.2.2/gap-report-2026-10-31.md`（只读差距审计，218 行），变更清单是 `docs/v1.2.2/impl-logs/round-2026-10-31-changes.md`。

### 控件语言（外观四项，A 方案）

- **分段**：`NirikoSegmentedControl` 成为唯一实现（Pill + 主色 20% 淡底 + 1dp 描边 + 等宽 13sp + 48dp 行高），接线统计页日历模式 / 详情页观看状态 / 作品库状态计数行；空壳 `GlassSegmentedContainer` 删除
- **chip**：`NirikoTagPill` / `NirikoFilterPill` / `NirikoSelectionPill` / `NirikoActionPill` 四档语义；旧 `TagChip` 删除，`ui/` 下 M3 `FilterChip` / `AssistChip` = 0
- **菜单**：`NirikoMenu` + `GlassMenuOverlay` 唯一入口（24dp 玻璃、锚点缩放、退场、Back/Esc、焦点进出/归还）；旧 `GlassAnchoredMenu` 删除，M3 `DropdownMenu` = 0；详情页 5 处双行 `DropdownMenuItem` 刻意保留
- **统计数字**：`StatNumber`(24/w600/tnum) + `StatLabel`(13) + `StatNote`(11.5) + `StatTile` 同源，统计页 20/22/26/28/32 五档清零
- **控件迁移（V-13）**：设置页滑块 / 搜索筛选 `Switch`·`RangeSlider` / B 站同步开关 / 剧集评分全部换玻璃控件（新 `GlassRangeSliderMath` 纯函数）；`ui/` 下 M3 开关与滑块 = 0
- **搜索框（P2-1）**：新 `NirikoSearchField`（Pill 玻璃 + 放大镜 + 48dp 清除 + focus 描边）接进设置页，替换自绘 `BasicTextField`

### 语言与层级

- **V-15**：`SectionHeader` 真实调用 4 → **43 处**；剩 6 处 `titleMedium+Bold` 是数值强调，按文件锁死
- **V-16**：6 个 drill-in 页标题 `titleLarge`(22) → `headlineSmall`(24/w600)
- **V-02**：`CardRarityMaterial` 23 色 → `RarityPalette.kt`、搜索挡板 2 绿 → `SearchIndicatorAccents`、`LibraryGalleryCard` 2 色清零；`ui/` 下 `Color(0x…)` 只剩 `theme/`、`share/`、`LiquidGlassTokens.kt`
- **V-03**：统计/日历/详情小字走 `labelSmall`/`labelMedium`，日历角标改实底 badge；`fontSize = N.sp` 只剩 `Type.kt`（定义）/`Badges.kt`（压图 10sp）/`share/**`

### 动效 / 帧阶段 / 减少动态效果

- **B-13**：新 `StateHost`（单实例、0.55→1、180ms、首帧不播、reduceMotion 瞬切）接 7 屏；刻意不用 `AnimatedContent`
- **B-15**：日历标题 `AnimatedContent`（只包标题、`sizeTransform = null`、方向纯函数、reduceMotion 两侧 None）
- **B-14 / B-16**：筛选面板补 `animateContentSize`、8 处手写 spring 收进 `panelEnter/panelExit`；搜索结果 `animateItem()` 逐条入场核实
- **B-11**：搜索卡 12 处字面量 → 六档令牌、5 处 reduceMotion、尺寸/位移改布局/绘制期读（`AnimatedLayoutSize.kt`）；**未做结构重写**
- **A.9**：底栏/dock 隐藏进度改 `offset {}`/`graphicsLayer {}` 内读 + `derivedStateOf`；时间线偏移、页面指示器（Canvas）、进度条与 range thumb 均移出组合期
- **A.8**：`spring(` 业务字面量 21 → 0（只剩令牌与 kyant 移植值）；手写 tween 数字只剩 CardRarity 3000×2、详情 350×1
- **B-9**：navigation-compose 2.8.5 自带 `PredictiveBackHandler + SeekableTransitionState` 跟手 seek（aar 字节码核实），新增 `PredictiveBackSupportTest`；**真机逐帧未验**
- **P2-2**：进程级 `CoverRevealTracker`（512 FIFO）修「列表滚回重播 150ms 淡入」

### 反馈 / 命中区 / 细节

- **F-05**：`isBroadcastRefreshing` 外露，日历刷新胶囊显示「刷新中…」并禁用
- **S02/S03/S04**：剧集徽标移到 meta 下一行、行尾常驻 chevron、48dp 命中盒
- **S07**：TMDb/AniList/VNDB 解绑二次确认 + 回执；**S08**：infobox 打开链接改 `NirikoActionPill(OpenInNew)`
- **F-07/F-08/F-10/F-12/F-14/F-15**：长按触感、菜单 chip 按压 + selected、TrendTab 按压、关闭钮 48dp 外壳、5 张主卡 `hapticEnabled = true`、保存回执改 `Confirm`
- **F-11**：hero 单击翻面（保留双击，约 300ms 双击等待已记账）

### 第八批验证

- `.\.gradlew.bat :app:compileDebugKotlin --console=plain` → **BUILD SUCCESSFUL**；`.\.gradlew.bat :app:spotlessApply :app:spotlessCheck :app:testDebugUnitTest :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL in 5m 53s**（同一条命令一次通过；含格式门、单测与打包）；`.\.gradlew.bat :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**（debug APK 32,742,166 B（≈31.2 MiB），versionName 1.2.2 / versionCode 5，已复制为 `dist/niriko-v1.2.2-debug.apk`）
- 单测聚合（`app/build/test-results/testDebugUnitTest/TEST-*.xml`，2026-10-31 12:11 本轮结果）：**210 个测试类 / 1551 项 / 0 失败 / 0 错误 / 0 跳过**
- 新增护栏：15 个测试类（`GlassMenuOverlayTest` 替代 `GlassAnchoredMenuTest`，净增 14 类）、约 72 个用例；更新 `ShapeLiteralPolicyTest` / `MotionLiteralPolicyTest` 精确基线
- **未验证**：真机矩阵（320/360/411/600/840dp × 7 主题 × 3 玻璃档 × reduceMotion × fontScale）、同屏 RenderEffect 实测、截图基线（`screenshot/` 仍是 2026-09-25 旧图）

## 验证（前七批，第八批见上）

- `.\.gradlew.bat :app:compileDebugKotlin --console=plain` → BUILD SUCCESSFUL
- `./gradlew.bat :app:testDebugUnitTest --console=plain` → BUILD SUCCESSFUL，**195 类 / 1482 项**（前七批结束时口径；第八批后为 **210 类 / 1551 项**，见上，既有 1419 + 新增 63：设置推入策略、M3 形状映射、RenderEffect 预算护栏、设置布局契约、开关结算纯函数、评分弹跳判据、阶段包络判据、详情页阶段派生、形状字面量护栏、强调色字面量护栏、动效字面量护栏、减少动态效果覆盖护栏…）
- `.\.gradlew.bat :app:spotlessCheck --console=plain` → BUILD SUCCESSFUL（`spotlessApply` 已应用 import 排序）
- `./gradlew.bat :app:assembleDebug --console=plain` → BUILD SUCCESSFUL（前七批结束时的 debug APK = 33,268,552 B（≈31.7 MiB），`android:versionName="1.2.2"` 已在合并清单核实；debug APK 尺寸随 dex 分段重排有数百 KB 波动，不作为门禁；第八批最新包见上）
- 第一批四件事合成一轮：`spotlessApply spotlessCheck testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL in 13m 6s；第二批 `spotlessApply spotlessCheck testDebugUnitTest` 1m 36s + `assembleDebug` 3m 50s；第三批 `spotlessApply spotlessCheck testDebugUnitTest` 2m 36s + `assembleDebug` 2m 45s；第四批 `compileDebugKotlin` 49s + `spotlessApply spotlessCheck testDebugUnitTest` 1m 58s + `assembleDebug` 59s；第五批 `compileDebugKotlin` 50s + `spotlessApply spotlessCheck testDebugUnitTest` 1m 46s（一次通过）+ `assembleDebug` 1m 13s；第六批 `compileDebugKotlin` 1m 14s + `spotlessApply spotlessCheck testDebugUnitTest` 2m 26s（首跑因 KDoc 内出现注释起始符失败，修后一次过）+ `assembleDebug` 37s；第七批 `compileDebugKotlin` 1m 1s（两次类型参数推断失败后修）+ `spotlessApply spotlessCheck testDebugUnitTest` 2m 9s + `assembleDebug` 1m 18s；前七批累计的单测 XML 聚合为 **195 类 / 1482 项 / 0 失败 / 0 跳过**（第八批另计）
- `git diff --check` 通过
- **未做**：真机截图对比（仓库无 roborazzi/paparazzi）、同屏 RenderEffect 实测、7 主题 × 1.5× 字体矩阵目视；风险清单见 `docs/v1.2.2/impl-summary.md` §10

## 范围与接管

- 审计输入：`docs/v1.2.2/00-inventory.md` … `05-hyperlight-and-external-motion.md`（70 条问题，P0 8 / P1 33 / P2 21 上下），路线图 `06-roadmap.md`，实施记录 `impl-summary.md`，交叉评审 `review-visual.md`
- 用户裁决：D1 材质分层**不收**（只加 RenderEffect 预算护栏）、D2/D3 同意、D4 Lead 自定（M3 Shapes 映射 4/8/12/16/24）
- 第八批之后的未收项（记账）：真机矩阵与截图基线（无设备，`screenshot/` 仍是 2026-09-25 旧图）、同屏 RenderEffect 实测、B-11 结构重写（单一 progress / graphicsLayer 尺寸近似）、V-04 材质分层（D1 裁决）、58 处无对应档位的圆角字面量、详情页 5 处双行 M3 `DropdownMenuItem`、`RatingComparisonSection` Canvas 刻度 8.sp、hero 单击翻面约 300ms 双击等待、剧集评分「停止拖动 600ms 落库」、V-15 剩余 6 处数值强调
- 已完成收口（前七批 + 第八批）：M3 `FilterChip`/`AssistChip`/`DropdownMenu`/`Switch`/`Slider`/`RangeSlider` 全部归零（按 `ControlSourceTest` / `DetailTypeScaleContractTest` 判定）、`GlassSegmentedContainer` 与 `TagChip` 删除、V-21 五档字号清零、日历 7–9sp 清零、B-10 chrome 时长与 B-9 预测返回均有护栏测试

## GitHub 准备

源码、测试、`docs/v1.2.2/` 一起提交；APK 作为 Release 附件而非源码提交；签名材料与 build 输出已忽略。
**本轮（含第八批）未执行 `git add/commit/tag/push`**——这些属于发布者动作，需确认后再做。

## 产物

- `app/build/outputs/apk/debug/app-debug.apk` = **32,742,166 B（≈31.2 MiB）**（2026-10-31 门禁构建，`versionName=1.2.2` / `versionCode=5` 由 `output-metadata.json` 核实；已复制为 `dist/niriko-v1.2.2-debug.apk`，未附到 Release）
