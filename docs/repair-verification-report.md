# 修复与验收记录

## F19 向开发者捐赠入口（最新）

设置 → 其他新增一条「向开发者捐赠 / 帮助我继续更新」。实现沿用「关于」的既有结构，不新增任何设置项、不写 DataStore、不联网：

- 资源：用户提供的两张个人收款码放进 `res/drawable-nodpi`（`donate_alipay.jpg` 1080×1620、`donate_wechat.png` 1242×1692）。nodpi 避免被密度分档缩放。
- 正文：`ui/settings/pages/DonateSettingsScreen.kt` 的 `DonateSettingsContent()` —— 一组说明（完全自愿 / 怎么支持）+ 支付宝、微信两条收款码。用 Coil 按显示尺寸解码而不是 `painterResource`，避免为一个很少打开的页面常驻十几 MB 位图；宽屏右列宽度封顶 420dp。
- 窄屏：`SettingsScreen` 的「其他」组硬编码第三行，点击跳 `SETTINGS_DONATE_ROUTE`；`NirikoNavHost` 注册该路由到 `DonateSettingsScreen`（脚手架 + 返回箭头）。
- 宽屏：`SettingsCategory.DONATE` 进左列导航，`AdaptiveSettingsPane` 的图标映射与右列 `when` 各加一条；两栏右列直接调用同一份正文，窄屏/宽屏不会各写一套。

### 用户复测发现的遮挡：设置页底部没有为 dock 留高

用户复测：新增的那一条被悬浮 dock 压住。根因是设置主页（顶层页，dock 挂在 `MainPager` 上盖在内容之上）原本只留 `Spacer(32.dp)` —— 32dp 小于 dock 的占位（胶囊 64dp + 距底 14dp + 系统导航栏 inset），分组最后一行必然落在胶囊后面；内容本身不足一屏时不会滚动、也就永远看不到那一行。

修法：底部留白改为 `DockBottomReserve = 120.dp`（与发现/搜索/统计信息流同款量级）并**叠加** `navigationBarsPadding()`，写法与 dock 自己一致。这样在两种 inset 归属模型下（外层 Scaffold 是否已消化底部 inset）最后一行到 dock 顶边的净空都是约 42dp。宽屏两栏不需要这层留白：`useVerticalDock` 等于 EXPANDED，宽屏 dock 竖排在侧边，`MainPager` 也不挂横向底栏。

新增回归（`SettingsCategoryTest`，共 15 例）：从 `LiquidBottomTabs` 源文件里读出真实胶囊高度 `.height(64f.dp)`，断言设置页的留白常量存在且 **严格大于 64dp**，并断言底部 Spacer 带 `navigationBarsPadding()`。这样把「留白必须超过一个 dock 高度」钉成跨文件不变量，而不是只留一个魔数。

验证：完整 `testDebugUnitTest` **1124 项 / 143 个类，0 failures / 0 errors / 0 skipped**；`assembleDebug` 与 `assembleRelease`（含 lintVital 全流程）成功。未使用设备或 ADB，实际遮挡是否消失仍需用户实机确认。

边界：收款码是版主本人的个人收款码、随源码与 APK 一起分发；是否适合公开在仓库里由发布者决定。应用内无法识别自己屏幕上的二维码，页面文案按「用另一台设备扫码」写。设置组「其他」的描述仍是「诊断与版本信息」，本轮按「不改动既有 UI 文案」保留。

## 性能轮次破坏的功能复原与共享键合同

用户要求「按记录对比恢复被性能修改破坏的功能」，本轮逐条比对首轮性能优化实际保留的改动（见 phone-shared-motion-profile.md），只复原有证据的功能损失，不动已验收的 200ms 路径/弹性、Dock、玻璃数值与横滑记忆。

### 已复原：封面加载失败不再显示占位图

首轮把 `CoverImage` 从 `SubcomposeAsyncImage` 改成普通 `AsyncImage` 时删掉了 `loading`/`error` 两个槽位，只留下 `if (failed && sharedElementKey == null) PlaceholderContent()`。后果是**任何挂了共享键的卡片**（作品库网格、发现海报、关联、猜你喜欢、角色/人员）在封面永久加载失败时只剩一块纯色圆角矩形，「这张封面没有」的图标再也看不到；v1.1.0 是 error → 占位图。

复原方式是最小改动：`if (failed)` 无条件渲染占位图，并且把占位图放到 `AsyncImage` **之前**绘制 —— 成功图后画会自然盖住它，因此既恢复失败态可见性，又不会在共享飞行中盖住正常图片。不恢复 loading 槽位：首帧用 `placeholderMemoryCacheKey` + 导航种子已能避免「默认图 → 真实图」双解码，加载中显示中性底色是首轮有意的视觉改进，不是功能损失。

### 横滑轨道共享键改为具名函数

「猜你喜欢」的共享键原本是 `GuessYouLikeSection` 里内联拼的字符串，关联条目则有 `relationCoverKey`。本轮把前者提成 `recommendationCoverKey(parentId, subjectId, index)`，与后者同形（只换来源段），键值逐字不变。目的不是重构，而是让「源卡键 = 目标页 hero 键」这条链路能在 JVM 单测里钉住。

### 新增回归

`DetailNavigationRegressionTest` 增三条：

- `relationAndRecommendationRailsHandTheVerySameKeyToTheChildHero`：两条横轨都走 `源卡键 → SubjectCoverHandoff.prepare → consume(...) ?: "cover_<id>"`，断言键原样到达；同一 subject 出现在关联与推荐两处时键必须不同；口令被别的条目先消费不能串给本条。
- `bothRailsUseNamedKeysAndTheChildHeroPrefersTheHandedKeyOverTheDefault`：两条横轨都用具名键，目标页 hero 优先用路由带来的键，NavHost 侧回落表达式保留。
- `cardGlassSourceKeepsOneProviderSlotSoSharedSubtreesAreNeverRebuilt`：`ProvideCardGlassBackdrop` 的函数体内 `CompositionLocalProvider` 调用点恰好一个，且没有 `if (backdrop != null)` 分支 —— 即上一节根因不会以另一种写法回归。

这些是源码/组合层合同，配合已通过的 `GlassProviderIdentityTest`（真实 Compose lifecycle，remembered=1 / disposed=0）使用；**不**等于实机逐帧视觉验收。

### 本轮验证

- 完整 `testDebugUnitTest`：**1121 项 / 143 个测试类，0 failures / 0 errors / 0 skipped**，结果来自本次运行新写的 TEST-*.xml（全部同一时间戳，无混入旧结果）。
- `assembleRelease` 在**不跳过任何任务**的情况下成功：`generateReleaseLintVitalReportModel`、`lintVitalAnalyzeRelease`、`lintVitalReportRelease`、`lintVitalRelease` 全部实际执行，lint-vital 文本报告为 `No issues found.`，return-value 为 0。此前轮次的构建带 `-x lintVital*`，本轮补齐。
- `assembleDebug` 成功；`git diff --check` 只报 AndroidManifest.xml 的 CRLF 提示（既有，非本轮）。
- 产物：`app-release.apk`（正式证书 CN=Niriko，SHA-256 `7ce3caa0e8bb4260d13b96c03a5956d569a7ff360bdffc0b9316957fcc5b1a15`）；`app-profile-debugsigned.apk`（同一 Release 二进制用本机 debug 证书重签，SHA-256 `b0f35887435023c348cd682eb73aee4a8c933e1ee75506371a5e25d17a68a847`，与用户已安装包同证书，可覆盖升级并保留数据）。

### 本轮边界

未使用设备、ADB 或更改用户记录；v1.2.0 / versionCode 3 不变；未改数据库 schema、未升级依赖、未回退用户其他未提交改动。实机详情 A→B→返回的中间 Rect、预测返回取消与冷暖封面观感仍需用户复核。

## 关联/猜你喜欢仍无中间运动的组合身份修复

用户确认横滑记忆与头像进入已正常，本轮不改这些实现、不调200ms参数或材质。独立审查完整callback/scope链无漏传，同route NavHost仍用entry.id区分；前两轮改共享key不足以解决实际症状。

确认结构性根因：性能优化在sharedTransitionScope.isTransitionActive时将glassBackdrop/pageCardBackdrop置null；ProvideCardGlassBackdrop之前非null走CompositionLocalProvider(content)，null直接content()，调用位置不同。共享匹配启动→来源置空→正文shared子树被销毁重建，匹配自身失效。关联GlassSectionCard又因实时源/降级分支重建源卡，形成第二层破坏。它是代码可证的问题，完整设备帧时序未验证。

最小修复：去掉两条!coverFlightActive来源门控，让实例/正文保持；Provider始终唯一CompositionLocalProvider调用点，null继承父源。保留固定图片解码、原始比例交接、200ms路径、横滑保存和头像预览。未来性能冻结必须只动绘制/捕获而非更换content组合位置；本轮不做额外GlassSectionCard大重构，因为门控移除使其在共享飞行中不切来源。

真实Compose lifecycle测试GlassProviderIdentityTest在null→live→null→live四次源变更中assert remembered=1、disposed=0，组合最终销毁才disposed=1；不是仅contains字符串。用Android Studio JBR21运行通过（Backdrop JVM classfile65，JDK17不能运行这个测试），CI设置JDK21、README说明同步，不改应用bytecode target17。尝试完整Navigation Compose UI runtime回归因本机离线缺espresso/ext-junit依赖无法解析，新增UI test依赖已撤回，不升级应用库，也不能宣称真实overlay中间Rect已验收。

最终完整1118测试通过，0失败/错误/跳过；Debug/Release构建成功。首次完整运行曾Room迁移测试隔离异常32→2，单独3迁移复测及最终完整回归均通过，没改schema或生产迁移。构建采用诊断-x Release vital lint，本轮不重复发布lint（上一轮曾成功），不把缓存参数当完整生产lint通过。未使用设备、ADB或更改用户记录，v1.2.0/code3保持。


## v1.2.0共享入口及横滑回归恢复（最新）

上轮仅将共享键写到navigate后的SavedStateHandle，不保证目标首帧能读取，横滑轨道也只依赖组件内部默认state；头像目标在网络详情/作品列表返回前为loading，无匹配端点。上轮“已修复所有入口”的表述不充分，用户实测回归优先于静态合同。

本轮不改200ms曲线/路径/弹性/缓存策略：关联和猜你喜欢的handoff共享键改为URI编码的可空路由query参数heroKey，从目标第一帧和返回栈恢复直接可用，兼容没有query的旧导航。NavHost所有subject_detail route判断均startsWith归一化，Navigation 2.8.5源码核实AnimatedContent contentKey用entry.id，同路由不同作品仍独立动画，不替换导航所有权。

新增DetailRailState，关联LazyRow保存index/offset、猜你喜欢ScrollState保存pixels，各键存父详情SavedStateHandle，同时保存离开时最后偏移；角色和人员轨道也独立保存。所有恢复忽略未测量/空列表，避免无内容首帧把旧位置覆盖0；不增加外部scrollToItem重新跳到原位。垂直详情state加入同样空测量保护，保留单/双栏各自key。

SharedAvatar源节点登记当前卡片已有真实名字和图片（64项仅metadata缓存，不持有bitmap）。Character/Person ViewModel创建时先显示对应preview，加载不把已有detail改成loading骨架，正式接口继续并行拉详情及作品；preview不含伪造生日/简介/职业，未落数据库。现源范围为原详情角色/人员/cv共用SharedAvatar入口；完全不经该组件的其他打开来源无preview时保留原加载分支，不能虚称任何冷人物直链都有Hero。

新增SharedEntryRestorationTest实际Robolectric断言两个ViewModel创建即非loading且头像端点字段存在、未知id不复用别条preview，route键首帧接线与4轨道偏移静态守卫。完整1117项测试通过0失败/错误/跳过；Debug与Release assemble成功，本次Release vital lint已实际执行通过，未跳过。git差异范围检查通过。不调用ADB、不安装手机、不提交推送，按用户已拔设备要求仅产安装包。

JVM与静态测试仍非真实Android共享overlay像素验收；用户需在修复包验证关联/猜你喜欢点入回收和轨道偏移、角色人物点入/退出。README结构保持，只将测试数量同步最新；v1.2.0/code3不变。


## v1.2.0发布准备（最新）

本轮停止共享动画性能调参，只修复用户指出的功能回归。徽标心红/星琥珀/状态多色恢复，文字对比算法按实际黑白对比选择极性，深色小件稳定底不让前景被冲到白色，装饰色点/星标保留原语义色。自定义主题和图片luma仍生效。

详情导航每个entry拥有SavedStateHandle，单栏/OVERVIEW/CONTENT分别保存Lazy item index与pixel offset，快照订阅及onDispose保存，不在返回时重置0。关联卡按父作品+目标+index唯一Hero key，点击handoff后目标详情继承并存entry；重复关系、猜你喜欢同目标不抢匹配，猜你喜欢独立recommendation key。入/退栈匹配模板和具体路由；原角色/人物头像共享节点补当前200ms path bounds。范围内不再调整时长/曲线/弹性或触碰手机收藏。

应用versionName1.2.0、versionCode3。README保留所有标题/目录/截图路径，更新产品介绍与已实现功能，注明部分历史截图；单测当前1112全部通过。Debug和Release assemble成功。发布诊断构建沿用此前-x lintVitalAnalyzeRelease/-Report/-Release，因此这不是完整发布lint验收；未代用户提交、打tag、推送或上传GitHub Release。

安全清理：本轮生成32个手机XML/帧文本/perfetto配置及trace、JVM崩溃文件删除，生成工具目录按清单清理；保留schema30–32、所有源码/tests/fixtures、截图与原型、用户plans和设计文档。build/.gradle/.idea/local.properties/签名材料本就忽略，核查git ls-files未追踪；新增crash/trace/heapdump忽略，README引用均保持现有。报告生成资源链接已标明清理，避免发布死链接和用户记录原文。

剩余验收：JVM saved-handle/key合同不等于Compose实机A→B→返回滚动/预测取消、头像路径和所有明暗主题像素验收，需要用户在新APK实测。完全冷图片比例未知的已知边界保留；不承诺所有节点零帧跳或120Hz性能。


## 200ms有路径的展开与轻量弹性（最新）

按用户批准方案恢复200ms。新增SubjectCoverPath：稳定矩形中心走垂直于起终点连线的浅弧，峰值min(10dp,路程6%)；尺寸进度较位置略滞后，在同一飞行内追上；不改变源/目标列表和详情常态几何，不在到位后再测量矫正。每10ms采样生成Rect关键帧、由同Navigation Transition寻址，回程复用反向同路径。几何轨迹无过冲，端点严格等于输入Rect。

落位弹性在约145–200ms由Transition驱动的单次sin²脉冲产生，峰值X尺度1.015/Y尺度1.009、Y位移3dp，结束严格1/0；仅内部graphicsLayer，不写layout/bounds，不再用卡顿模拟回弹。形变位于shared测量之后、clip之前，图片与轮廓一起变形；保留原按压和翻卡交互。Transition.isSeeking时弹性为0，减少动态效果时为0；取消/commit仍Navigation处理剩余，不新增计时器/返回handler或重播整段。commit释放后若剩余seek时间进入落位区只应用剩余脉冲；跨seek/commit视觉连续性仍需设备逐帧复核。

最终clip层快照完整回归1099项通过、0失败/错误/跳过，assembleDebug成功，新增精确端点/100步反向同弧/10dp限幅/位置领先宽度/短距离减弱/重心重合无绕路/寻址与减动零弹性测试。原有共享缓存、真实图片比例、稳定header、Dock和其他已验收UI不改。不升依赖、不改Shapes、不覆盖原型、不回退用户工作区。

本轮没有实机或像素验收，轨迹和弹性为数学/Compose API编译与JVM验证。完全冷图原始比例未知的此前边界仍保留；等面积但不同宽高的少见共享端点不保证完全对称的设计方向，需在实际出现时补对照。普通点击来回、手势停住/取消/commit、横纵海报和画廊出入是复测重点。


## Kazumi参考的海报变形与速度设计（最新）

读取用户提供Kazumi-main：[Hero飞行图片](<C:/what i download/Kazumi-main/lib/bean/card/network_img_layer.dart#L37>)实际仅返回源侧（push source、pop destination）hero.child并capture主题，无自定义createRectTween或第二阶段展开；[收藏源端](<C:/what i download/Kazumi-main/lib/pages/collect/collect_library_card.dart#L341>)与[详情端](<C:/what i download/Kazumi-main/lib/bean/card/bangumi_info_card.dart#L146>)皆transitionOnUserGestures=true；Android/iOS主题用CupertinoPageTransitionsBuilder。没有读取其Flutter SDK版本内部默认曲线，所以不冒称逐字移植默认速度数值。本次依此机制做Niriko独立设计，不复制Flutter源码。

本次解决之前单纯缩短线性时长缺少节奏的问题：180ms（比上一版200ms略快），展开q=t+0.75t(1-t)，回收为镜像1-q(1-t)，单调、无超调，末端导数下限0.25，每个四分之一手势仍至少10%空间路程。共享bounds按目标面积选择展开/回收，页面effects按详情路由方向使用同曲线；Navigation仍唯一seek所有者，不加第二PredictiveBackHandler。详情→详情两个封面几何不同的特殊路径仍需要实机复核方向协调，不声称所有极端case视觉完全一致。

列表/网格、画廊、关联作品、参与作品和详情hero尺寸比例约束放在共享节点外；来源列表比例保持原值，详情原始比例保持，内层图片在动画矩形约束下连续Crop展开。移除原固定比例/高度在共享节点内抑制中间尺寸重排的结构，不追加转场完成后的二次展开。不涉及文字、Dock、顶部控件、材质或历史月状态。

完整testDebugUnitTest 1095项通过，0失败/错误/跳过；assembleDebug成功，限定diff --check通过。测试覆盖180ms、前段快速/末段持续移动、100步正反单调与镜像、源目标bounds同duration及尺寸modifier外置契约。几何modifier为静态契约+JVM曲线验证，不等同设备逐帧渲染；本轮未实机验收速度观感、冷图比例和预测取消，需用户复测，不能承诺完全冷图已有intrinsic ratio。


## 共享转场速度微调（最新）

用户已验收其他改动，本次仅修改NirikoMotionSpecs共享时间轴参数与专属测试。360ms Linear缩短为现有AnimDurationShort=200ms，源/目标Rect与页面effects仍同步，预测返回仍按完整手势比例seek，不恢复原page scale/弹簧尾段早收敛风险。旧弹簧没有固定可还原毫秒数，200ms是贴近原快速观感的调校，不宣称等同旧物理曲线。完整1093测试通过，0失败/错误/跳过；assembleDebug成功；未进行本轮实机速度观感验收，其他已通过功能不改。

## 已批准顶部控件方案实施（最新）

### 用户补充约束已落实

搜索仍使用实际组件 fullW=(screenW-32dp).coerceAtLeast(anchorDp) 与原右上锚点，不照搬HTML窄面板；发现“作品/人物”模式保留。作品库/发现各有自己的左侧竖向选择器，分区/榜单状态独立，锚点与基线一致、字宽通过可中断transition伸缩。视图工具进入展开搜索框下/过滤行上方靠右；作品库排序、标签、多选、分区新建管理及3种视图保留，小统计删除，人物收藏放回滚动内容。状态数按真实关键词/分区/标签范围统计，不受当前状态二次过滤。

搜索和选择器展开不再改变信息流inset/padding或滚动位置；两页保持固定收起初始安全padding。库页窗口安全inset也改由控件自己处理，避免两页按钮基线不一致。现有HTML和两份Dock源码SHA256与实施前完全一致。Shapes未升级，玻璃数值未调整。

### 历史月新增确定根因

本机反代 POST /v0/search/subjects 使用应用历史窗口和 sort=date，返回HTTP400 description="sort not supported"。官方 [OpenAPI schema](https://bangumi.github.io/api/dist.json) 确认 POST sort 只支持 match/heat/rank/score，而date是GET列表规则。已将getSubjectsInDateRange请求改rank并保留air_date与完整分页。公开rank请求有超时，不宣称实网历史全部成功；其契约通过专属HistoricalSearchContractTest锁住。

补充每个年月key自己的LOADING/SUCCESS/EMPTY/ERROR、lastSuccessAt、partial标记与事件数。月表不再显示本周成功时间；ANIME成功而REAL失败时保留动画及旧候选，显式报部分失败，不能写全量成功TTL。request range/type、合法日期/platform/eventCount/coverless计数为脱敏诊断，不打印账号令牌。StatsBroadcastMonthTest通过公共方法覆盖10→9月、刷新仍9月、失败/空重试、在途去重与部分失败保留。

### 海报时间轴与详情玻璃菜单

只有真实共享封面到/离开subject_detail的边移除页级scale，其他设置/剧集/人员等普通边保留原SharedAxis Z。源目标Rect与页面effects采用同360ms Linear seek时间轴，不增第二PredictiveBackHandler；取消和commit仍Navigation驱动。外框pressTilt移入共享视觉层，header初值与同pass高度都为statusTop+56dp，不再onSizeChanged回写造成8dp矫正。源缓存placeholder存在时绘真实painter，而非被纯色loading覆盖；解码保留实际比例。

分享/传送门菜单改同页GlassAnchoredMenu，避让安全区、LTR/RTL/长菜单/返回焦点与外点关闭，采样页背景+正文但不含菜单自己，动作均保留。详情loading/header/overlay的实际几何仍需逐帧实机观察；完全冷图片无ratio时无法预知原始比例，未用冻结3:4或转场后展开掩盖。顶部竖选择器仍使用focusable Popup，其跨窗口玻璃取样/窄屏外点需要实机复核，不能从JVM规则证明GPU材质正确。

### 最终验收与缺口

完整 testDebugUnitTest：1092项，0 failures/errors/skipped；assembleDebug成功，单worker/no-daemon串行以避免主机内存争用。新增ApprovedTopControlsTest覆盖选择器排序、状态计数、原搜索宽度、作品人物行及固定feed inset；已有月份、Rect线性seek、菜单placement回归均执行。限定修改diff --check通过。未提交commit、不回退用户改动。

设备安装/启动成功、AndroidRuntime无崩溃输出；UI树可确认作品库只留“全部作品”选择器和搜索，展开后6状态数量及排序/标签/多选/视图入口均存在。首次快速打开搜索时抓到排序子菜单，切页检查未得到稳定完成；模拟器后续System UI ANR/ADB掉线，内存约仅600MB可用，所以终止本轮自己启动的模拟器，不把不稳定结果算通过。未完成发现完整互动、预测返回逐帧、冷暖海报或九月实网视觉验收，优先由用户实机复核。

两个并行执行在收尾中断，主会话接管实际文件并修正编译反馈、补充控件专属回归，完成完整测试与最终构建；没有将其失败虚报为成员完整交接。


## 剩余数据与封面修复（本轮）

用户已验收 Dock 与发现/详情渐隐，本轮冻结这些交互和材质，不改变玻璃参数，也不重做已通过页面。

### VNDB 实数据根因

本机公开 Kana 查询中文“魔法少女的魔女审判”和英文/罗马音均取得 v50283，真实返回 rating=80.7（小数）、released=2025-07-18，多语言标题中有正式中文名。项目 VndbVisualNovelDto 的 rating 为 Int，序列化失败后被 runCatching 转为空候选；修为 Double 并沿关联标题数据结构保留精度。并非缺少该作品，也不需要硬编码ID或降低0.92阈值。

与 app 相同完整详情字段查询也成功：113个标签、9张截图、4条关联（该时点的公开API快照，不是固定常量）。统一评分源另有100分标尺误当10分的问题，已更正 rating 80.7 → score 8.07/10，nativeScore 80.7/100。

真实响应保存在 test/resources/vndb/witch-trials.json，新增 VndbLiveSchemaTest 覆盖解析、多语言匹配、候选ID落库与评分换算。测试使用已抓取公开fixture+模拟DAO，不冒称用户设备已绑定。

### 历史放送

此前只修 SeasonalFetcher 错误传播仍不够：NirikoApplication 实际注入的 DataSourceChain 在更上层吞掉区间查询异常，不支持历史检索的 AniList 返回空，导致失败当合法空写成功TTL。本轮新增日期范围能力（兼容月浏览能力），只调用声明能力的插件，失败/无能力不成为空成功；合法空结果仍保留；取消透传。新增 HistoricalDataSourceChainTest。

另一个确定缺陷是已有预取 episode 日期没有接入日历。公开 Bangumi 414461（僵尸百分百）首播2023-07-09、总14条含SP，正篇10/11/12实际同在2023-12-25；旧估算完结2023-10-15必漏。现在已有完整正篇日期优先，改星期/延期/同日多集不丢，SP不当正篇；日期不全保留估算并合入已知日期。StatsViewModel转发现有episode数据，专属回归覆盖公开日期形状。Bangumi evidence来自代理云端API读取，不把它混同本机/设备在线验收。

预取只给真正入选的前60个ID作标记，不再把超预算未请求ID永久标记。剧集请求失败不先变成空成功，仓储失败保留已有历史日期，取消透传；新增 EpisodeRepositoryFailureTest。

### 共享封面与横轨

关联条目、人物/角色参与作品、画廊卡原先直接AsyncImage绕过封面覆盖与成功图尺寸登记。本轮以 SharedSubjectCover 统一effective URL/intrinsic ratio/memoryCacheKey，原几何/共享key/ContentScale不改。

冷关联条目缺Room metadata时，从接口真实relation/person subject字段生成临时预览（ID、标题、类型、封面），不写Room、不伪造评分/集数/日期；导航优先Room，再临时预览，详情正式刷新仍走原链。已知数据不需阻塞网络才能进入共享转场。未知类型不虚构映射。猜你喜欢原本已经用CoverImage，只补标题固定两行避免高度差。

### 本轮验证边界

冻结已验收Dock/渐隐；无新增数据库schema与库升级；没有回退用户未提交改动。公开VNDB候选存在与完整字段协议已确认，实际设备是否自动绑定仍取决于Bangumi标题/网络/当前缓存。历史日期修复需要成功获取剧集；缺数据仍只能估算，6个月候选窗口与60条预取上限没有无限扩大。冷封面缓存被淘汰或图片尚未加载仍需实机复核，不能承诺每个入口逐帧无闪。

本轮最终Gradle：完整 testDebugUnitTest 1069项，0 failures/errors/skipped；assembleDebug成功。限定修复文件diff --check通过。未做本轮设备端数据绑定或录屏视觉验收，不把公开API与离线fixture测试等同实机验收。

## 上一阶段范围

基于 root-cause-analysis-remaining-issues.md 的用户认可方案执行。保留现有未提交改动、不升级 Compose/backdrop、不改变 Dock 的 blur/lens/表面颜色/按压尺寸与弹簧数值；对齐参考 catalog 的动画与手势职责，而非替换成另一套导航。

## 本轮用户复测后的修正

此前“移动指示器负责拖动、标签负责点击”的实现没有覆盖用户需要的整栏触控，本轮以此反馈为回归基线，不能沿用上一轮只验证选中片拖动的验收结论。

- 固定整条胶囊的唯一输入平面位于导出标签和移动玻璃之上，不随指示器移动；支持四标签任意区域单击、按住吸附、跟手拖动和边缘触控。透明导出标签不再有输入所有权。
- DockTouchState 独立保存触点所属格、抓取偏移和累计位移；命中用 floor 单元，绘制中心按偏移连续更新，RTL/竖向对应映射。测试发现按住不动的浮点运算顺序误差（2.0000002），已通过先做差再相加修复，不放宽断言。
- MainActivity 不再把整个 NavHost 下移到状态栏底部。NavHost/Pager全窗，发现内容和详情内容从窗口 y=0 绘制；作品库/统计/设置及其他二级路由保留 route-local safe-top。
- 发现/详情 alpha mask 显式 hiddenHeight=0，从状态栏顶部开始短渐隐，不画横跨按键区域的大矩形背景。发现入口分别为液态玻璃胶囊；布局/搜索独立表面；详情返回/分享/传送门分别为玻璃圆按钮。玻璃原有数值不变。
- 详情头部与正文是独立兄弟，header仅给Lazy内容初始padding，不缩小滚动viewport；正文能滚入状态栏，浮动按键始终位于mask之外。
- 最新完整Gradle验证：1051 tests，0 failures/errors/skipped，assembleDebug成功；本轮限定diff --check通过。
- Medium_Phone 实测：四格从非选中页逐一点击均到正确页；作品库状态在非选中统计格按住900ms到统计；统计状态从非选中设置格拖到发现到正确页；胶囊左边缘点击回作品库。UI XML证据位于 app/build/tap-*.xml、hold-other.xml、drag-other.xml、dock-edge.xml；录屏 app/build/dock-anywhere.mp4。
- 当前模型无法读取图像，以上设备结论来自真实命令与UI层级；渐隐和玻璃按钮源码/构建已核实，但不冒称已人工逐帧检查观感。cutout/平板/预测返回及实际详情滚动需继续视觉复核。

## Dock 对齐（上一轮记录，输入职责由上节替代）

- 恢复位置弹簧追踪，不再用 snapTo 将指示器直接跳到目标。
- 恢复按压进度、X/Y 形变、按运动速度的形变以及捕获标签 1.2 倍缩放；软件当前参数保持不变。
- 原仓库由移动指示器负责拖动、可见标签负责点击。本轮恢复此职责，透明导出标签层 disabled，不参与点击。
- 松手按 roundToInt 吸附展示中心，拖动基准从当前 presentation value 开始，取消回到外部目标但不提交选择。
- 放开后等待靠近目标并让按压动画可感知，等待设 500ms 上限且 re-grab 可取消，不带回旧版无限等待复位问题。
- Pager 外部目标单向同步；避免 currentPage 经过中间页导致反复回调切页，手势中不覆盖拖动目标。
- 手机重选回调补齐 MainActivity → NavHost → MainPager → NavSuite。保留发现回顶/顶部刷新、作品库/统计回顶、宽屏竖向 Dock、主题和原隐藏行为。
- ScrollToTop 改成负向 animateScrollBy，取消向上传播，动画正常结束后必要时校正到顶部。

## 其他修复

- 搜索四态和键盘/手势锁/布局切换保留，玻璃采样为壁纸 + 本页信息流，排除搜索控件本身；采用 Dock 基底机制，不用高不透明垫层盖掉折射。
- 发现页工具栏成为信息流同父兄弟叠层；列表 viewport 不因工具栏或菜单被外 padding 缩短，初始留白进入 contentPadding。历史筛选与 freshness 进入可滚动 header。
- 收藏/发现信息流以独立 Offscreen + DstIn alpha mask 消失，背景和按钮不参与 mask。控件下方短带渐隐，最大滚动强度顶部全透明；不是黑色 SrcOver 遮罩。
- 详情刷新进度条为 overlay，不推移海报。源 CoverImage 登记成功图真实比例与 Coil memoryCacheKey，导航先读取本地条目，VM 初始预填；详情请求用缓存占位和已知比例，不改变列表固定比例。
- 评分索引不再双量化，使用最新回调和同步操作值，区分成功释放与取消；保留满分放大/金色确认。
- 音乐曲目只显示在收藏编辑 Sheet，详情不再重复表格。
- 普通人员卡/查看全部尾卡统一字体缩放高度规则。
- 完成波浪从整个区块扫光改为按角标索引依次点亮，订阅上提页级；直接改为看过的进度写满也发完成边沿事件。重复完整值保存不重复播。
- CRT API33+ 使用缓存 AGSL 桶形和 RGB 采样偏移，低版本/失败 Canvas 降级；用户开关、减动、年份条件与约4秒周期保留。
- VNDB 低质量非空 primary 追加有界子串召回；VNDB 提前停止阈值与 0.92 自动绑定门槛对齐，其他源评分规则未改。
- 历史放送主请求失败不转合法空、不写成功 TTL；保留旧数据和重试，在既有错误区域展示月份失败，已有 Bangumi 分页和月份窗口保持。

## 已执行验证

- Gradle 离线 compileDebugKotlin 成功。
- 最终完整 testDebugUnitTest：1045 项，0 failures、0 errors、0 skipped；assembleDebug 成功。结果来自最新 TEST-*.xml 汇总，非 README 历史数字。
- 已在 Medium_Phone Android 模拟器安装、启动修复 APK，无 AndroidRuntime 崩溃输出。
- 初次设备拖动测试发现 Row 手势/透明标签命中回归，修复后从当前作品库指示器拖到统计，UI 树确认显示“收藏统计”。
- UI 树的手机四标签 bounds 相邻等宽，未看到统计专属扩大命中区域。
- 同设备安装运行参考仓库自带 catalog APK，定位 Bottom tabs 示例，并保存参考与修复录屏。
- 限定本轮源码 git diff --check 通过；没有为全仓已有 CharacterDetailScreen EOF 空行做无关修复。

## 验收边界

- 当前模型不能读取图片；虽然已生成截图/录屏并读取UI树，不能声称逐像素观感一致或已经人工看过所有动画帧。
- 模拟器 GPU 驱动回退、主机并行 Gradle 编译和 Debug 模式影响时序；运行 gfxinfo 具有高延迟，不能作为手机真机帧率承诺或未对照的性能提升结论。
- 本轮模拟器没有实际收藏数据，详情海报、多集点亮、音乐、CRT及历史月份实数据的视觉效果仍需用户在已有数据上复核。
- VNDB 测试使用离线合成候选，未向公开 API 验证魔女审判的具体条目命中。自动绑定阈值不降低。
- 首帧海报契约覆盖使用 CoverImage 且已有加载图片/本地条目的入口。关联条目或其它直接 AsyncImage 入口没有完整预填时，仍可能走加载回退，不宣称所有冷数据入口完全消除闪烁。
- 未提交任何 commit，也未回退用户工作区。

## 交付

修复 APK：app/build/outputs/apk/debug/app-debug.apk。
模拟器交互：app/build/niriko-dock-final.mp4。
原库参考：app/build/upstream-bottom-tabs.mp4。
后续优先用原速对照录屏复核 Dock 按压/弹簧追踪/形变复位；针对有实际数据的作品检查海报冷/热缓存返回、评分停顿、完成事件及历史月份错误重试。
