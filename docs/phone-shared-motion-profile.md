# 实机共享海报性能测试

## 第三阶段：CPU封面分析缓存契约优化（最新）

本轮实际新增[CoverImageAnalysis.kt](<../app/src/main/java/com/otakup/niriko/ui/common/CoverImageAnalysis.kt>)，亮度/主色/模糊共享480px软件Bitmap请求（allowHardware=false），按allocationByteCount限制8MiB，8个URL散列Mutex合并同URL并发，不全局锁住所有图片。hero/屏幕显示图片仍用原硬件与尺寸策略，480px保持原模糊源分辨率，不改200ms路径/弹性或玻璃参数。

原ImageBrightness与GlassCard AmbientTint另发32px请求且没指定software，像素读取可能因hardware bitmap失败；原BlurredCoverCache超过8条时clear全部。现亮度/主色提取转Dispatchers.Default，取消异常透传，模糊缓存为8项LruCache（仅淘汰旧项，不主动recycle仍可能被UI使用的位图）。此为具体缓存/分析契约修复，不宣称整个解码链完全单飞：主色/亮度的结果cache仍为各自轻量数值，blur操作本身未加所有调用者单飞、源显示图与分析图仍可能独立解码。

新增CoverAnalysisCacheTest：同URL复用、8并发仅执行一次load、合法null可重试、取消不缓存、8MiB大小及blur无wholesale clear静态契约。最终完整testDebugUnitTest1106项通过、0失败/错误/跳过，Debug/Release assemble成功，限定diff-check通过；发布lint在低内存主机长时间无进展后，诊断打包临时用-x跳过lintVitalAnalyze/Report/Release，未修改持久Gradle配置，不将此诊断包当完整生产发布验收。

非Debug新诊断副本使用本机标准debug证书重签，SHA256与已装包一致，install-r成功，未卸载或清用户数据。正式app-release.apk证书不用于用户手机覆盖。原型/Dock/数据库不改，用户收藏状态未手动写入。手机已有网络数据缓存可能依应用原逻辑刷新。

采样情况：第一32MiB短trace填满，进程映射缺失，空查询不能当收益；第二64MiB短trace有效映射，应用measureAndLayout最大66.34ms、Recomposer最大15.00ms，没有检出decodeBitmap片段，但采样前UIAutomator null root，未确认该窗口是与基线同海报完全匹配的工作量。因此不能宣称解码0、FPS提升或整体卡顿已消除；只保留证据为最新分析缓存trace（本地生成诊断产物，v1.2.0发布准备时已清理；统计摘要保留）待核验。没有录屏或持续后台采集，trace只本机处理。应用AndroidRuntime无崩溃输出。手机最终保留新非Debug诊断副本供用户测试。

下一步：在手机明确停留全部网格、确认海报和返回坐标且无人同时操作的窗口，先暖一次再采匹配短trace；必要时对cpu分析cache打无用户数据的hit/miss计数，再比较不同作品循环访问超过8项的LRU效果。原BlurredCover RenderEffect/software Canvas回退路径应单独可靠性审查，不能因软件源修复宣称模糊渲染所有设备已验收。


## 第二轮结论与非Debug交付（最新）

两个结构实验均没有净收益，因此最终源代码保留首轮固定图片请求与额外捕获门控，不保留分阶段详情或sharedBounds Crop实验。原200ms路径/弹性/Dock/数据功能不改。

进一步做同源代码非Debug对照（Release原buildType，isMinifyEnabled仍false，未加R8混淆），验证Debug额外开销。为不清手机数据，正式签名APK不直接安装：临时Gradle init的debug signing override没有实际覆盖正式证书，安装返回UPDATE_INCOMPATIBLE且未更换手机原包；该失败安装后误采的trace不算非Debug证据。随后仅用本机标准Android debug证书重签诊断副本，apksigner验证与现有Debug相同SHA256 b0f35887435023c348cd682eb73aee4a8c933e1ee75506371a5e25d17a68a847，install-r成功。应用package flags确认无DEBUGGABLE，不卸载不清数据，不改生产签名配置或读取正式keystore秘密。

最初非Debug样本处于运动分区而非全部网格，且trace出现Popup，因此不采用其40.72ms结果作为严格前后收益。恢复全部作品并再次确认同海报详情返回坐标后，采相同6.5秒两次暖往返，无录屏和窗口内UI扫描。

| 指标 | 首轮优化Debug | 匹配海报非Debug |
|---|---:|---:|
| 测量/布局最大 | 183.77ms | 62.84ms |
| 测量/布局总时长 | 699.47ms | 221.13ms |
| Recomposer重组最大 | 45.99ms | 18.78ms |
| Recomposer重组总时长 | 440.19ms | 164.89ms |
| 迟呈现且App Deadline Missed | 15条 | 10条 |

Debug开销是残余峰值的重要组成，不能将其误报为全部来自布局算法。单窗口对照仍受JIT/缓存/后台网络/动态刷新/厂商调度影响；非Debug解码76片段、总742.73ms、最长67.29ms，说明高质量图片及详情数据刷新仍有成本，也说明不能只选有利指标。63ms远高于120Hz 8.3ms预算，不能称已完全修复流畅度或保证固定FPS。

最终手机保留非Debug诊断副本，数据保留、全部作品页面恢复、AndroidRuntime无崩溃输出。文件为非Debug同证书测试APK（本地生成诊断产物，v1.2.0发布准备时已清理；统计摘要保留），仅供当前debug证书用户保留数据测试，不是正式发布签名构建。标准正式签名输出app-release.apk没有对用户手机覆盖，报告不引导用户混装两证书。非Debug没有Debug方法级trace和run-as能力，下一次复杂调用栈可用单独profileable诊断配置，但本轮没持久修改manifest/buildTypes。

最终Debug源码回归1102项全部通过（撤回实验后部分Gradle FROM-CACHE），Debug/Release assemble均成功；Release未做完整设备功能覆盖，需用户复测分享/传送门/搜索/预测返回、冷暖图与高分材质。工具/短trace仅本机保存未上传。下一轮优先追图片重复解码与详情异步刷新，避免再依赖Debug观感继续调速度。已完成首轮修复后的实机对照：固定图片请求与转场捕获隔离有效；分阶段详情组合和stable sharedBounds实验均撤回。最后保留的Debug源码回归为1102项通过，assembleDebug成功；同证书非Debug诊断副本在同机采样显示测量峰值约62.84ms、重组18.78ms，较Debug明显改善但仍非120Hz完全流畅。手机最终保留非Debug诊断副本，测试结束回到全部作品。

## 第二轮残余长帧实验

保留首轮有效方案，第二轮对两个新实验做同机6.5秒两次暖海报往返对照：

| 实验 | 最大测量布局 | 最长重组 | 解码片段 | 迟呈现且应用超时 |
|---|---:|---:|---:|---:|
| 首轮保留优化 | 183.77ms | 45.99ms | 28 | 15 |
| 详情非关键区块入口分阶段 | 228.63ms | 58.39ms | 68 | 19 |
| stable sharedBounds等比Crop | 231.73ms | 59.27ms | 76 | 22 |

两项没有净收益，均已撤回，只撤回本轮自己新增的代码/测试，原custom section order、列表布局及共享element机制恢复。分阶段只把成本挪到另一帧而且增加解码，不作为最终交付；sharedBounds内容在源目标同时组合，不能假设稳定测量一定比sharedElement快，也不能凭静态API声称视觉保持。

当前仍保留普通Box/AsyncImage+固定解码尺寸和转场额外捕获暂停，不改200ms曲线/Dock/用户设置。实测单窗口值受JIT/缓存/后台调度影响，不是稳定FPS保证。同源非Debug APK A/B已完成，见最新节。

## 首轮性能优化与同机对照

### 保留的改动

- CoverImage从BoxWithConstraints+SubcomposeAsyncImage改普通Box+AsyncImage，不再让动画中间约束触发子组合；固定请求宽度根据屏幕/显式端点输入计算（手机1200px对应800px解码），ImageRequest按URL/cache/固定尺寸remember，高度未指定时Undefined保留原图比例，Coil缓存占位保持。
- 详情共享飞行期间暂停miuix/kyant额外背景捕获并推迟背景渐入；静态背景模糊继续绘制避免突然清晰。飞行结束恢复原玻璃参数和背景效果，无全局设置或材质数值变化。
- 200ms浅弧线/视觉弹性/共享缓存目标几何均未改。没有实施未经验证的多项曲线调整。

### 同机同海报暖路径观测

使用同样6.5秒窗口，两次进入详情和返回，窗口内无录屏或UI树读取。保留优化第一次有效trace为优化后跟踪（本地生成诊断产物，v1.2.0发布准备时已清理；统计摘要保留），原始为基线跟踪（本地生成诊断产物，v1.2.0发布准备时已清理；统计摘要保留）。

| 指标 | 基线 | 首轮保留优化 |
|---|---:|---:|
| 最长AndroidOwner测量布局 | 274.63ms | 183.77ms |
| 最长Recomposer重组 | 55.06ms | 45.99ms |
| decodeBitmap片段次数 | 64 | 28 |
| decodeBitmap总时长 | 496.48ms | 309.67ms |
| Late Present且App Deadline Missed条目 | 29 | 15 |

这是单组同机工作量观测，不是统计稳定的性能保证或精准FPS提升，后台缓存/JIT/系统调度和厂商插帧仍可能影响结果。收益明确但残余183ms峰值仍足以打断200ms转场，不能宣称已达120Hz流畅。

### 被撤掉的实验

尝试在共享转场期间将Pager邻页预组合从1降0。第二样本超过32MB环buffer，丢进程映射；可见测量峰值约181.77ms，与首轮无明显收益，UIAutomator持续不空闲。保守撤掉本轮新增门控，恢复beyondViewportPageCount=1并保留原Dock/横滑行为；没有据无效trace宣称性能更好。

优化后最差布局片段仍有19次Compose recompose、190次Text测量和12个AsyncImagePainter记忆/恢复。因此剩余主要是源页/详情页的复杂内容恢复，而不仅是原封面子组合。后续需要更精细调用栈/节点A/B；真正轻量飞行独立绘制层和源页复用尚未完全实现，本轮只落低风险可测收益，不把前述方案全部标完成。

### 构建与设备安全

最终完整1102项测试通过，0失败/错误/跳过，assembleDebug成功。CoverFlightPerformanceTest覆盖稳定解码、无子组合、缓存占位与静态模糊不闪变契约。最终install -r同签名更新成功，未卸载/清数据，UI树回到作品库全部作品，未触碰评分进度或同步。没有修改数据库schema/Shapes/玻璃全局参数，未覆盖原型，未回退用户其他改动。

最初手机仅测已安装APK；用户批准开始优化后才做上述data-preserving安装。此最新节取代旧“本轮不覆盖安装/不改源码”边界，旧节保留基线历史。


## 测试边界

使用用户授权连接的物理手机及其已安装Niriko，不清空/卸载/覆盖安装，不写入收藏、评分、进度、同步或设置。只进入作品库可见海报详情并返回。测试结束已回到作品库“全部作品”。未修改Android源码/动画参数，本轮仅性能测试和报告。打开详情会按应用本身现有逻辑读写网络元数据缓存，不能称完全没有缓存变更。

设备：型号2509FPN0BC，Android API37，1200×2608、480dpi，读取时刷新率120Hz、系统动画倍率1.0。当前包versionName1.1.0/versionCode2、DEBUGGABLE。未做Release/Profile替换，因此测量反映用户当前Debug APK，不等同Release最终性能。

## 方法

1. 只使用ADB导航点按，无屏幕录制。按应用UI树定位海报和返回键，不读取应用数据库或账号秘密。
2. 进入与返回各自reset gfxinfo（只重置性能计数），保存framestats。第一次进入是本测试第一次，不宣称磁盘冷缓存；后续同条目再进为暖路径。
3. 捕获短时Perfetto：gfx/view应用trace、Android FrameTimeline。初次config文件路径被手机SELinux拒绝，使用stdin配置采集成功，无root或权限设置变更。
4. 最终干净采样6.5秒、32MB缓冲，仅两次点入/返回；窗口内无uiautomator、录屏或人工滚动，避免语义扫描和录屏干扰。早期8MB scheduling trace环缓冲覆盖了部分进程名且UI树扫描混入，未据此定位最终根因。
5. 使用官方Perfetto v58.2 Windows分析器，本机下载SHA256与官方manifest一致（adfa6bad3d72be3ba9b83fa2b17b69fa13b3ab1cad0f42e52b86188bd5f0f997）。用户trace只在本机分析，未上传网站。

## 确定发现

### 不是只靠主观感觉：存在实际长帧

120Hz一帧预算约8.3ms，200ms共有约24个显示机会。gfxinfo的厂商系统补帧使新Janky与legacy数值差异很大，不能把低Janky比例当流畅。帧完成总时间包括排队/显示管线，不能把它全部称CPU或GPU执行时间。

单独warm-enter采样开头的应用生成帧UI工作约113.98ms（HandleInputStart→SyncQueued），AnimationStart→PerformTraversalsStart约44.23ms，DrawStart→SyncQueued约69.68ms。第一次返回对应一帧UI约317.91ms，其中DrawStart→SyncQueued约304.87ms。

这些是gfxinfo阶段区间，不足以精确归因某个Composable，且Vendor Flags32/2存在补帧需区分；因此再用干净Perfetto确认。

### 干净Perfetto确认主要问题在Compose主线程工作

最终6.5秒roundtrip trace的进程为com.otakup.niriko，已验证以下长片段：

| 片段 | 最长 | 说明 |
|---|---:|---|
| Choreographer doFrame | 309.13ms | 单个回调超过整个200ms动画 |
| AndroidOwner:measureAndLayout | 274.63ms / 251.19ms | 两次严重测量布局停顿 |
| Record View draw | 296.77ms / 275.48ms | 包含测量布局子片段，不可与上行相加 |
| Recomposer:recompose | 55.06ms | UI重组也明显超出8.3ms |
| decodeBitmap | 35.38ms |后台图片工作存在；不能直接当主线程阻塞证据 |

实际FrameTimeline在整个短测试窗口（含进出和邻近背景更新，不是只200ms飞行段）显示29条Late Present且包含App Deadline Missed，最大相关帧140.06ms。厂商仍有resynced/buffer stuffing和补帧，未计算成精确“动画FPS”。

结论：主线程测量/布局/重组过重是明确性能瓶颈；不够流畅不能只靠修缓动或拉长时长解决。尚未通过方法采样/callstack锁定274ms测量中哪个具体Compose节点最耗时，不能把全部责任直接归给共享组件或玻璃shader。

## 与当前源码的关联与优先级

### P0：稳定飞行测量和图片请求

当前sharedElement在动画中改变测量约束。CoverImage使用BoxWithConstraints，再以constraints.maxWidth推reqW/reqH（不传固定requestWidth的网格等入口）并构造ImageRequest。因此动画改变尺寸可能导致subcomposition和新的size请求，暖缓存也未必代表无需重新请求/解码。此为高可信代码链，不是trace已识别每次request的证明。

修法：外层列表/详情常态布局使用稳定端点尺寸，飞行使用缓存图片和只包含图片的轻量独立绘制层，图像内容用矩形裁切/transform表现宽高展开，不让飞行中间size驱动整页Lazy或复杂海报材质反复测量。不能直接用ScaleToBounds导致整张图拉伸或丢失原要求的连续Crop展开；需明确画像绘制/crop策略。

固定飞行ImageRequest size/cache key；飞行先复用现有bitmap/painter，目标高清图预热或在几何不变条件下交接，不在飞行中每帧更改解码请求。保留原始比例与用户覆盖URL契约。

### P1：避免同一200ms窗口争做复杂详情工作

当前详情同时有350ms全屏模糊背景渐入、两套backdrop采样、Lazy区块数据刷新、影子/稀有度材质，源列表也有多卡离屏材质/遮罩。布局和draw录制长停顿已证实，但单项贡献需逐项A/B。

优先保留轻量hero与稳定背景，转场窗口冻结非必要采样/材质更新和重复入场动画，非关键区块延后到交接完成；不是全局关闭玻璃/降图片清晰度，也不改变静止页面参数。不要用任意delay掩盖请求，应以transition完成/缓存状态作为边界。

### P2：修连续曲线和回弹节奏

当前position=u+0.75u(1-u)，端点导数为1.75/0.25，随后正常布局速度为0，所以起停速度不连续。Rect轨迹每10ms点间Linear插值，位置连续但切线在采样点不连续；不是每10ms才刷一帧。sin²弹性只在末55ms（60Hz约3.3帧、120Hz约6.6帧），叠加3dp位移可能显抽动。

在性能隔离基础上，用连续可求导Rect路径/平滑曲线或样条统一位置与尺寸，端点软收稳；保持200ms，不通过加很多keyframe解决。降低回弹幅度、延长其在同一200ms内的作用段，先去独立上下位移保留微量横纵形变。预测拖动不播自主弹性，commit/cancel交接从presentation继续。

### P3：Release/Profile同机复核

Debug Compose额外开销影响当前结果，但这是用户实际体验，不能简单归咎Debug后忽略。需要未来在明确授权且签名兼容/可保留数据的前提下对同机Release/Profile做对照；本轮未安装替代APK。先A/B固定飞行size，再A/B冻结采样，再修曲线，各自验证FrameTimeline及手势取消，不混改所有参数。

## 本地证据

- 首次进入framestats（本地生成诊断产物，v1.2.0发布准备时已清理；统计摘要保留）
- 首次返回framestats（本地生成诊断产物，v1.2.0发布准备时已清理；统计摘要保留）
- 暖路径进入framestats（本地生成诊断产物，v1.2.0发布准备时已清理；统计摘要保留）
- 干净Perfetto跟踪（本地生成诊断产物，v1.2.0发布准备时已清理；统计摘要保留）

UI XML包含用户条目名称，只保存在本机build诊断目录，不在报告复述全部收藏，不做公开发布。原手机作品库已返回，应用记录未手动改动。ADB、trace服务均未留持续后台采集；本机性能工具文件和手机临时trace为本次诊断生成，未清用户缓存/数据。
