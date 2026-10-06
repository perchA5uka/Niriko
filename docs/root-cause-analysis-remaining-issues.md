# 剩余问题根因分析

> 分析基线：HEAD `97ce7df` 加当前未提交工作区；结论针对当前源码而不是单独的 HEAD。  
> 分析范围：`C:\what i made\niriko` 当前工作区源码、用户问题清单，以及 `C:\what i download\AndroidLiquidGlass-kmp` 参考仓库。  
> 本轮原则：只读分析，不修改业务代码；清单中的“未实现”不直接等同于当前缺陷。

## 结论摘要

当前最确定的代码缺陷有四项：

1. 手机/中等宽度 Dock 没有把重选回调传入 `MainPager`，发现页“再次点击当前 Dock”无法稳定到达页面消费者。
2. `ScrollToTop` 把正向距离传给 `animateScrollBy`，存在向下滚动而不是回顶的方向错误；取消后的兜底行为也与注释不一致。
3. Dock 指示器把“手指所在格坐标”直接当成“指示器左缘索引”，按下跟手时存在约半格至一格的几何偏移，松手再 floor 吸附时会回跳。
4. 评分触感把刻度索引再次当评分值量化，造成同一刻度内重复震动；满分松手的金色放大反馈实际上已经存在。

共享海报闪烁的高可信根因是“转场两端几何不稳定 + 详情图像/数据异步就绪”，不是单一的列表卡片比例问题。详情页根据图片 intrinsic size 动态改变海报比例；列表统一用固定比例；同时详情页存在异步刷新和首次加载占位。需要真机逐帧验证闪烁来自比例变化、共享快照缺失、图片占位、还是父页面转场叠加。

顶部渐隐/悬浮按钮与横滑卡片问题多数已有部分修复或实现。当前残留更像层级、测量和接线问题：发现页渐隐层与工具栏不在同一父级，工具栏内部 `zIndex` 不能保证跨父级盖过后绘制的渐隐层；详情横滑轨道基本固定了文本行，但“查看全部”卡仍使用不同高度策略。

## 证据分级

- **确定代码事实**：由当前源码静态结构直接推出，不代表已经在设备上看到。
- **高可信根因**：代码存在明确的几何/时序链，但最终视觉症状仍需设备验证。
- **待验证假设**：需要真机、Layout Inspector、Logcat 或 Perfetto 才能确认。
- **清单过期/语义差异**：功能已经存在，或“未实现”描述与当前代码不一致。

## 1. 共享海报闪烁与轨迹不连续

### 当前结构

- 列表视图的 `CoverThumbnail` 固定宽度、使用 `3:4`；海报网格则使用 `2:3`。两者以 `cover_{subjectId}` 作为共享 key，不能把所有入口都认为是同一个比例：
  - [CoverImage.kt:154](<../app/src/main/java/com/otakup/niriko/ui/components/CoverImage.kt#L154>)
  - [PosterGridCard.kt:101](<../app/src/main/java/com/otakup/niriko/ui/library/PosterGridCard.kt#L101>)
  - [Screens.kt:416](<../app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt#L416>)
- 详情端不是固定比例：图片成功后读取 `painter.intrinsicSize`，按原图宽高比重新给 `DetailPosterCard` 测量；加载中或失败才回到 `3:4`：
  - [SubjectDetailScreen.kt:1007](<../app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt#L1007>)
- 详情端图片请求异步，且 ViewModel 先读本地条目再后台刷新：
  - [SubjectDetailViewModel.kt:295](<../app/src/main/java/com/otakup/niriko/viewmodel/SubjectDetailViewModel.kt#L295>)
- NavHost 同时启用页面 `SharedAxis Z` 过渡和 shared element：
  - [NirikoNavHost.kt:108](<../app/src/main/java/com/otakup/niriko/navigation/NirikoNavHost.kt#L108>)
  - [NirikoAnimation.kt:119](<../app/src/main/java/com/otakup/niriko/ui/animation/NirikoAnimation.kt#L119>)

### 根因组成

1. **目标比例异步变化**：列表的共享端点固定为 3:4/2:3，详情端在图片成功后从兜底 3:4 切换到原图比例，这是确定的代码行为。两端比例不同本身允许连续插值，不足以解释闪烁；真正风险是动画过程中或之后目标 bounds 再次变化。如果成功事件恰好发生在共享动画中，会重定向目标；若发生在其后，会出现单独展开。是否对应所述‘先到位再矫正’需要逐帧验证。
2. **目标端不保证首帧出现**：ViewModel 初始为 `subject=null/isLoading=true`，异步读 Room 后才关闭 loading；详情页 loading 分支是没有共享 key 的骨架，真实共享端点只在正文分支出现。源码注释声称海报应首帧存在，但当前数据链不保证这一点。证据：[SubjectDetailViewModel.kt:263](<../app/src/main/java/com/otakup/niriko/viewmodel/SubjectDetailViewModel.kt#L263>)、[SubjectDetailScreen.kt:689](<../app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt#L689>)。冷数据时目标可能错过源元素可匹配的转场窗口。
3. **目标位置还会变**：缓存展示会置 `isRefreshing=true`，页面在正文 Column 上方插入 LinearProgressIndicator，刷新结束撤掉它；这是实际布局组件，不是 overlay，海报及正文的垂直起点随之变化。证据：[SubjectDetailScreen.kt:696](<../app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt#L696>)、[SubjectDetailViewModel.kt:304](<../app/src/main/java/com/otakup/niriko/viewmodel/SubjectDetailViewModel.kt#L304>)。具体跳动是否与比例变化同帧仍待录屏确认。
4. **图片绘制与盒子几何耦合**：详情端使用 `ContentScale.Crop`；如果目标比例变化，视觉上可能同时出现裁剪窗口变化和位置变化，容易被误认为“轨迹不是直线”。
5. **页面转场叠加**：SharedAxis Z 的缩放/淡入与海报共享 bounds 同时发生。若闪烁帧不是海报本身而是页面层，可来自两种动画叠加，不应只改图片动画参数。

### 尚不能确认的部分

- 当前未运行真机，不能断言“Coil 占位一定是闪烁源”。`CoverImage` 在 shared key 存在时用 surfaceVariant 占位，已主动避免通用图片图标闪现：[CoverImage.kt:116](<../app/src/main/java/com/otakup/niriko/ui/components/CoverImage.kt#L116>)。
- 需要逐帧确认详情目标比例是在 shared transition 期间改变，还是导航页面/列表卡本身被重组。

### 最小修复方向（后续实现轮次）

- 首选让详情最终原始比例在转场开始前已经可知并稳定，使位置/尺寸在同一 bounds 动画中连续到位。固定列表比例本身不是 bug，不需要改变列表设计。若只能采用临时比例，需设计成明确的连续变形阶段，不能转场结束后再突变比例；否则仍会重现用户不希望的‘先到位再展开’。
- 将刷新进度改为不改变正文起点的绘制层，并为异步数据阶段提供稳定的共享端点。上述为后续方向，本轮不实施。
- 检查列表端与详情端是否在首帧使用同一有效封面 URL（包括用户覆盖），避免 key 相同但内容源不同。
- 分离“shared element 盒子”和“详情真实图片盒子”，不要在 shared element 期间由 painter 成功事件直接改变外层尺寸。
- 用 Layout Inspector/录屏逐帧记录 source bounds、destination bounds、intrinsic ratio、painter state 和导航 transition progress，再决定是否需要调整 SharedAxis Z，而不是先调时长。

## 2. Dock 卡顿、指示器形变与触控范围

### 指示器回跳：确定几何错误

本地 `LiquidBottomTabs`：

- `tabWidth=(maxWidth-8dp)/tabsCount`：[LiquidBottomTabs.kt:83](<../app/src/main/java/com/otakup/niriko/ui/bottombar/LiquidBottomTabs.kt#L83>)。
- 按下时用 `x/tabWidth` 写入 `dragTarget`：`:119-124`。
- 松手时用 `dragTarget.toInt()` floor 后吸附：`:127-131`。
- 指示器绘制左缘使用 `value*tabWidth`：`:260-266`。

这把“手指位于第 n 格的连续坐标”当成了“指示器左缘的 tab 索引”。点击格子中部时，连续值本身约为 `n+0.5`，但绘制代码把它当左缘，导致指示器偏右；松手再 floor 到 `n`，于是回跳。末格还会受到 value range clamp，左右表现不对称。竖向 Dock 的 y 坐标存在同类风险。

**后续方向**：将命中归属索引、指示器展示中心、拖动增量三者分离；保留当前材质、弹簧和形变参数。不要直接套参考仓库旧实现。

### 形变不归位：旧根因已被本地修掉，剩余为待验证

本地 `DampedDragAnimation` 已将 position、shape、velocity 分 Job，`release()` 不再等待位置动画收敛：
[DampedDragAnimation.kt:37](<../app/src/main/java/com/otakup/niriko/ui/bottombar/utils/DampedDragAnimation.kt#L37>)。

参考仓库的旧版 `release()` 会等待 value 接近 target 后才恢复 scale；不能把这个旧链直接归因到当前项目。当前仍有两个候选：

- `DragGestureInspector` 没有 try/finally；pointerInput 被框架取消或回调异常时，未必执行 release：[DragGestureInspector.kt:21](<../app/src/main/java/com/otakup/niriko/ui/bottombar/utils/DragGestureInspector.kt#L21>)。
- 正常结束、取消和 Pager 回流可能重复 cancel/restart shape Job；这是竞态候选，不是已在真机确认的永久卡死。

### 卡顿与触控范围：不能仅凭源码定论

- 本地可见 Row 整体挂了 interactiveHighlight 和 drag modifier：[LiquidBottomTabs.kt:191](<../app/src/main/java/com/otakup/niriko/ui/bottombar/LiquidBottomTabs.kt#L191>)；两个手势观察者可能在同一按下上仲裁。
- `DragGestureInspector` 在 Initial pass 立即开始，移动时 consume，未实现 touch slop/方向仲裁：[DragGestureInspector.kt:21](<../app/src/main/java/com/otakup/niriko/ui/bottombar/utils/DragGestureInspector.kt#L21>)。
- Dock 的两份 backdrop 绘制和 combined backdrop 指示器都存在 blur/lens；是否为 GPU 瓶颈必须用 Perfetto/FrameTimeline 验证，不能从源码推导毫秒级卡顿。
- 统计页没有发现“统计按钮扩大到覆盖整个页面”的直接代码证据。真正的 hit path 需要 Layout Inspector 与坐标日志确认。

**后续方向**：唯一 drag owner、明确 touch slop/轴向策略、取消只恢复状态而不提交 tab、隔离 Pager currentPage 回流；修复前先保留现有玻璃参数。

### 发现页重选回顶/刷新：事件断链与方向错误

页面消费者已经存在（TrendingSection、DiscoverPosterPane、DiscoverGridPane），但手机/中等宽度的 `MainPager` 调用 `NirikoNavSuite` 没传 `onTabReselected`；其默认值为 null，NirikoBottomBar 最终 invoke 空回调。宽屏窗口层有传入回调，不能把所有形态视为同一状态。证据：[MainPager.kt:172](<../app/src/main/java/com/otakup/niriko/navigation/MainPager.kt#L172>)、[NirikoNavSuite.kt:56](<../app/src/main/java/com/otakup/niriko/ui/adaptive/NirikoNavSuite.kt#L56>)、[MainActivity.kt:408](<../app/src/main/java/com/otakup/niriko/MainActivity.kt#L408>)。

另有独立错误：`ScrollToTop` 在 index=0/offset>0 的确定场景得到正 distance，却执行正向 animateScrollBy；标准非 reverseLayout 列表上这是向后滚，成功后直接 return 不校正。多项时还用首项高度估算所有项高度，异高列表不保证准确。列表/网格版本都应统一回顶方向并验证最终落点。catch Throwable 后会继续到 scrollToItem，与‘取消不强制回顶’的注释相反；取消作用域中是否成功执行该兜底需运行时检查，不能把它当已确认成功抢回顶。证据：[ScrollToTop.kt:23](<../app/src/main/java/com/otakup/niriko/util/ScrollToTop.kt#L23>)。

错误态/空态的重选消费者还可能不在组合树中，后续不能只验正常有数据列表。

## 3. VNDB 绑定失败（以《魔法少女的魔女审判》为例）

### 当前不是“完全未接入”

- 游戏详情才走 VNDB 补充：[SubjectDetailViewModel.kt:718](<../app/src/main/java/com/otakup/niriko/viewmodel/SubjectDetailViewModel.kt#L718>)。
- 当前匹配流程包含 infobox 明确 ID、多查询串、多语言标题字段、候选打分和手动候选：
  - [VndbProviderMatcher.kt:57](<../app/src/main/java/com/otakup/niriko/data/match/VndbProviderMatcher.kt#L57>)
  - [ExternalMatchService.kt:439](<../app/src/main/java/com/otakup/niriko/data/match/ExternalMatchService.kt#L439>)
- 只有置信度 `0.92` 以上才自动写绑定，否则展示候选让用户确认：[SubjectDetailViewModel.kt:747](<../app/src/main/java/com/otakup/niriko/viewmodel/SubjectDetailViewModel.kt#L747>)。

### 根因判断

当前源码不能确认该具体作品的 VNDB ID 是否命中。可能原因是：

1. Bangumi 条目没有可解析的 infobox VNDB ID；
2. VNDB API 全文与子串查询失败或返回空；而当前子串兜底只在 primary 为空时启动，即使 primary 非空但都是相似的错误作品，也不会扩展子串候选。这是召回能力的条件性缺口，不是该具体作品已证实的 API 结果；
3. 多标题候选存在，但最高置信度低于 0.92，因此被正确地留在候选区；
4. 用户尚未在候选区执行手动绑定。

VNDB 查询异常被静默转为空结果并记录 warning：[VndbProviderMatcher.kt:85](<../app/src/main/java/com/otakup/niriko/data/match/VndbProviderMatcher.kt#L85>)；因此 UI 上的“无候选”不能单独证明“VNDB 没有该条目”。

### 验证要求

用该作品详情页抓取 Logcat 中 `loadVndbSupplement matches/best`、具体 query failed、候选数量和候选 ID；同时记录是否存在 infobox ID。若候选低于阈值，应优先改善可解释的手动绑定入口，而不是降低全局自动绑定阈值。

## 4. 历史放送信息不显示

### 当前能力

历史月份已经有按可见范围加载、日期区间分页和跨月延续逻辑：

- [StatsViewModel.kt:193](<../app/src/main/java/com/otakup/niriko/viewmodel/StatsViewModel.kt#L193>)：按可见月份请求，过去约 5 年范围内允许加载，窗口为目标月前 6 个月至目标月末。
- [BangumiDataSource.kt:311](<../app/src/main/java/com/otakup/niriko/data/remote/bangumi/BangumiDataSource.kt#L311>)：日期范围请求分页，不再只取单页 100 条。
- [StatsCalculator.kt:225](<../app/src/main/java/com/otakup/niriko/data/calculator/StatsCalculator.kt#L225>)：按月份、开播日、预计结束日和星期生成历史事件。

### 仍可能导致“看起来为空”的根因

- `SeasonalFetcher.fetchSeasonalInRange` 失败时返回空列表，调用层仍可能把空结果写入月份缓存并标记加载时间：[SeasonalFetcher.kt:69](<../app/src/main/java/com/otakup/niriko/data/remote/SeasonalFetcher.kt#L69>)、[StatsViewModel.kt:235](<../app/src/main/java/com/otakup/niriko/viewmodel/StatsViewModel.kt#L235>)。
- 查询日期范围、分页总数或某一数据源异常时，UI 可能只表现为没有事件，没有把“请求失败”和“合法空结果”清晰分开。
- AniList 数据源明确不支持按日期范围，返回空；历史放送应确认当前链路实际使用 Bangumi，而不是错误地落入 AniList：[AniListDataSource.kt:142](<../app/src/main/java/com/otakup/niriko/data/remote/anilist/AniListDataSource.kt#L142>)。

**结论**：不能再把“当月以前不支持”作为当前根因；应记录月份、rangeStart/rangeEnd、每页 total/条数、最终过滤前后数量和错误状态。

## 5. 顶部悬浮按钮、渐隐与黑色截断

### 收藏页：已有大部分正确结构

[Screens.kt](<../app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt>) 采用外层 Box，列表顶部 padding 使用表头实测高度加间距：

- 外层 overlay：[Screens.kt:359](<../app/src/main/java/com/otakup/niriko/ui/screens/Screens.kt#L359>)。
- 浮动表头测量：`:521-532`。
- 渐隐层在表头之前绘制，且无 pointerInput：`:521-526`。
- 顶部 padding 来源于 `headerHeightPx`：`:336-341`。

不能因已有 overlay 就认定满足用户效果：收藏表头把搜索、分区、统计、人物区块和筛选一起固定在上方，移动的是作品列表，不是整个信息流。若实测表头很高，首项初始留白也很大；不过列表 contentPadding 是内容内留白，不会自行缩小 viewport，不能把它当父级裁剪。问题清单称效果错误，本轮确认已有结构但尚未通过视觉验收。

### 发现页：层级与测量仍有结构性风险

- `SubjectSearchScreen` 以 Column 先放 SearchToolbar，再放内容：[SubjectSearchScreen.kt:166](<../app/src/main/java/com/otakup/niriko/ui/subject/SubjectSearchScreen.kt#L166>)。
- 内容避让使用按 SearchPhase 写死的 76/208dp，而不是工具栏实测高度：`:142-149`。
- `SearchToolbar` 自身外框锁 56dp，展开内容通过 required size 溢出，内部 zIndex 只在其父级生效：[SearchToolbar.kt:74](<../app/src/main/java/com/otakup/niriko/ui/common/SearchToolbar.kt#L74>)。
- `TopFadeOverlay` 是外层 Box 的兄弟，默认 96dp，使用 background 色渐变：[TopFadeOverlay.kt:29](<../app/src/main/java/com/otakup/niriko/ui/components/TopFadeOverlay.kt#L29>)。

因此‘内容→渐隐→工具栏’的注释与实际层级不同：外层渐隐兄弟后画在 Column 上方，内部工具栏 zIndex 无法跨越父级。发现工具栏 56dp + 顶部 8dp 已真实占位，再对内容施加 phase padding；内容的滚动 viewport 从工具栏下方开始，不会自然滚入工具栏背后。展开时的额外 padding 又把 viewport 下移，与列表 contentPadding 不是同一种机制。这是‘仍像 AppBar’的明确结构来源，但没有发现能据此认定全局 clipToBounds 的证据。

更直接的材质错误是 TopFadeOverlay 画的是背景颜色的 SrcOver 渐变，并未改变信息流 alpha。深色背景下它会压暗文字/图片，且只揭示纯背景色而非真实壁纸；用户要求的是内容透明度降低。这是确定的实现方式差异。TopFadePolicy 的 24dp 是滚动强度变化距离，不是空间渐隐带；空间带仍为默认 96dp，最大强度 0.92 也不能保证顶部内容完全不可见。证据：[TopFadeOverlay.kt:29](<../app/src/main/java/com/otakup/niriko/ui/components/TopFadeOverlay.kt#L29>)、[TopFadePolicy.kt:18](<../app/src/main/java/com/otakup/niriko/util/TopFadePolicy.kt#L18>)。

**后续方向**：只调整相关页面的局部叠层，不重构导航和手势。内容、工具栏分别作为同父兄弟，真正的 alpha mask 只作用于信息流绘制层，例如独立离屏层上的 DstIn，不能对包括工具栏的整个页面做 mask，也不能直接在窗口 framebuffer 上做混合。首次可见内容让位采用内容内 padding，不缩小 viewport。把空间消失区与滚动强度分开；工具栏位于清晰的最上层。保持当前玻璃材质参数，不用继续调黑色渐变掩盖布局/合成错误。新增离屏合成成本需性能验收。

## 6. 详情横滑卡片上下抖动

### 已完成的部分

当前多个横滑卡已经使用固定封面高度、固定文本行数或最小高度：

- 关联条目：图片 120dp、关系 1 行、标题 2 行：[RelationsSection.kt:126](<../app/src/main/java/com/otakup/niriko/ui/subject/RelationsSection.kt#L126>)。
- 角色/制作人员：角色名、职位等保留空行：[CharacterStaffSection.kt:264](<../app/src/main/java/com/otakup/niriko/ui/subject/CharacterStaffSection.kt#L264>)。
- 人物/角色参与作品：标题、secondary、类型行和最小内容高度：[CreditSubjectCard.kt:121](<../app/src/main/java/com/otakup/niriko/ui/common/CreditSubjectCard.kt#L121>)。
- 统一规则在 [RailCardPolicy.kt](<../app/src/main/java/com/otakup/niriko/util/RailCardPolicy.kt>) 中已有纯函数和测试。

### 仍可能抖动的根因

- `ViewAllStaffCard` 使用固定 `130dp` 高度，而普通 StaffCard 由头像、文本、padding 自然测量：[CharacterStaffSection.kt:243](<../app/src/main/java/com/otakup/niriko/ui/subject/CharacterStaffSection.kt#L243>)。混入同一 LazyRow 后，在字体缩放、主题字体度量或内容变化下仍可能产生轨道高度差。
- 其余轨道若包含不同组件实现，需按“图片盒子 + 每个文本槽位 + padding”统一公式验收，不能只看标题是否两行。

## 7. 已实现但清单标成未实现的功能

### 音乐曲目

曲目当前同时出现在详情区块和编辑 Sheet：

- 详情：[SubjectDetailScreen.kt:1600](<../app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt#L1600>)。
- 编辑：`:2074-2082`。
- 状态保存使用共享的 `watchedTrackIdsLocal`，ViewModel 保存后按已听曲目数更新进度。

所以“移动到编辑页”已经部分完成，但如果产品目标是“详情页彻底去除曲目表”，当前仍未完成。另一个待验证语义是关闭编辑 Sheet 后草稿是否应回滚：页级 `rememberSaveable` 会保留本地勾选，直到下一次保存或页面销毁。

### CRT

CRT 已接入详情页，当前是轻量图元近似，不是真正逐像素桶形畸变：扫描线、边缘色散、暗角、亮带、有限噪点和 0.6% 整体缩放均已存在：[CrtOverlay.kt:28](<../app/src/main/java/com/otakup/niriko/ui/animation/CrtOverlay.kt#L28>)。它属于效果增强，不是“未实现”的逻辑缺陷。若要加强桶形畸变，需要评估 RuntimeShader/AGSL 和性能，而不是继续堆 Canvas 图元。

### 集数全点亮

完成波浪已经通过“进度写入跨过总量”的边沿触发：

- 判据：[EpisodeCompletionPolicy.kt:44](<../app/src/main/java/com/otakup/niriko/util/EpisodeCompletionPolicy.kt#L44>)。
- 事件：[CompletionEvents.kt:41](<../app/src/main/java/com/otakup/niriko/viewmodel/CompletionEvents.kt#L41>)。
- 绘制：[EpisodeCompletionWave.kt:48](<../app/src/main/java/com/otakup/niriko/ui/animation/EpisodeCompletionWave.kt#L48>)。

现有动画是对 EpisodeRatingSection 整块绘制横向渐变扫光，没有逐个角标的索引/延迟/激活状态。事件已接入，但效果不是‘一排角标依次亮起’，这是明确的实现粒度缺口。直接把状态切成‘看过’只走状态庆祝通道，不一定发进度完成事件；需覆盖用户实际的‘全部标记看过’入口。进度事件 replay=0，只有区块被组合且生命周期 RESUMED 时才订阅；若区块在屏幕外未组合，事件可能丢失，而非缓存到滚到它时播放（[CompletionEvents.kt:23](<../app/src/main/java/com/otakup/niriko/viewmodel/CompletionEvents.kt#L23>)、[EpisodeCompletionWave.kt:57](<../app/src/main/java/com/otakup/niriko/ui/animation/EpisodeCompletionWave.kt#L57>)）。‘只播一次’目前是每次不完整→完整边沿一次，并非终身一次；重复保存完整值不播，回退再完成可以重播。

### 评分满分确认

“拖到 10 分松手时数字放大 + 金色”已经存在：[GlassControls.kt:234](<../app/src/main/java/com/otakup/niriko/ui/components/GlassControls.kt#L234>)。当前已确认的触感 bug 是单位错配，但不能由满分代码存在推导它运行正常：

- `lastTickIndex` 存的是 `tickIndex(rating)`：`:241-244`。
- `crossesTick` 需要评分值并会再次调用 `tickIndex`：[RatingFeedbackPolicy.kt:46](<../app/src/main/java/com/otakup/niriko/util/RatingFeedbackPolicy.kt#L46>)。
- 调用处却传入 `lastTickIndex.toFloat()`：[GlassControls.kt:274](<../app/src/main/java/com/otakup/niriko/ui/components/GlassControls.kt#L274>)。

另一个高可信接入风险是 GlassSlider 的 pointerInput 仅以 valueRange/steps 为 key，没有 rememberUpdatedState 包裹最新回调；rating 变化未必重启长寿命手势协程，finished 可能仍读旧评分闭包。点击时 onValueChange 后紧接 finished，也不能保证父状态已经重组到新分值。证据： [GlassControls.kt:163](<../app/src/main/java/com/otakup/niriko/ui/components/GlassControls.kt#L163>)。因此满分确认‘已有实现’不代表最新松手值正确，需覆盖连续两拖与点按到满分。

例如评分 5 的刻度索引为 10，再次量化后变成 20，而 next=5 量化为 10，比较恒为不等。静止没有 pointer 回调时代码不会凭空定时震动；停顿期间的微小移动/重复相同值回调会重复触发，符合用户停顿仍震动的现象。修复时应统一 API 的单位：要么保存上次评分值，要么提供接收刻度索引的独立判定函数；不要同时量化两次。另需真机验证手势取消是否应该触发“松手确认”，当前 `onDragCancel` 也调用 finished：[GlassControls.kt:170](<../app/src/main/java/com/otakup/niriko/ui/components/GlassControls.kt#L170>)。

## 8. 液态玻璃参考边界

参考仓库 `AndroidLiquidGlass-kmp` 的 README 明确说明它提供的是底层库，不包含高层组件；示例组件只是 catalog：`C:\what i download\AndroidLiquidGlass-kmp\README.md:13-21`。

参考源码中：

- `lens` 使用圆角半径、refractionHeight、refractionAmount、depthEffect 和可选 chromaticAberration：`backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/Lens.kt:16-60`。
- 示例 Dock 的典型管线是 `vibrancy → blur(8dp) → lens(24dp,24dp)`，交互指示器另有动态 lens、高光、shadow、innerShadow：`app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt:161-187、231-260`。
- 示例拖动动画将位置、速度、按压进度和 X/Y 形变分开，但参考仓库当前坐标/依赖版本与本项目不完全一致：参考仓库 `gradle/libs.versions.toml` 为 Kotlin 2.4.10/Compose 1.12.0，项目实际 `debugRuntimeClasspath` 的 Compose animation 解析为 1.11.1，项目声明 backdrop 1.0.6；不能直接把参考仓库 2.0.1 源码当作当前依赖实现。
- 当前项目 Dock 使用 `vibrancy、blur 8dp、lens 24dp` 等参数：[LiquidBottomTabs.kt:196](<../app/src/main/java/com/otakup/niriko/ui/bottombar/LiquidBottomTabs.kt#L196>)；这些参数本轮不改。
- 搜索组件走 `appleGlassCard`，采样源、额外垫层和 Dock 不同，因此两者观感差异是结构性差异，不应只调透明度或 blur 数值：[IosStyleSearchComponent.kt:271](<../app/src/main/java/com/otakup/niriko/ui/common/IosStyleSearchComponent.kt#L271>)。

### 搜索表面为何不像 Dock：采样和合成的差别

Dock 采样壁纸 + 当前页内容，搜索 appleGlassCard 的全局卡片源主要捕获壁纸；在无壁纸/档位关闭/仅收藏玻璃而搜索非收藏的条件下，还可能直接静态降级。证据：[MainPager.kt:67](<../app/src/main/java/com/otakup/niriko/navigation/MainPager.kt#L67>)、[MainActivity.kt:278](<../app/src/main/java/com/otakup/niriko/MainActivity.kt#L278>)、[GlassCard.kt:165](<../app/src/main/java/com/otakup/niriko/ui/components/GlassCard.kt#L165>)。

当前参数应登记而非擅改：Dock 基底 vibrancy、blur 8dp、lens 24/24dp、surfaceContainer alpha 0.4；指示器 lens 10/14dp 随按压变化并带色散、动态高光/内外阴影。搜索卡管线是 vibrancy、blur 12dp、lens 20/40dp，depthEffect=true、色散=true，又叠加白/黑垫层（收起 0.55、展开 0.72；降级 0.90/0.96）及内层卡片。两者不只是颜色不同，而是采样对象、材料和嵌套绘制不同。后续应采用 Dock/参考 LiquidButton 的正确采样与层次结构，使用软件当前参数基线；‘同款 Dock 参数’还是保留搜索原参数的歧义需在实现前确认，不直接套参考演示数值。

## 9. 验证矩阵

| 编号 | 场景 | 需要记录 | 目的 |
|---|---|---|---|
| A | 列表卡进入详情，短/长比例封面，冷缓存/热缓存 | source/destination bounds、intrinsic ratio、painter state、transition progress | 区分比例跳变、占位和页面叠加 |
| B | Dock 各格左缘/中心/右缘，LTR/RTL，最后一格 | down x、dragTarget、绘制中心、最终 tab | 复现半格偏移与末格不对称 |
| C | Dock 普通 up、消费取消、系统取消、旋转、resize、Pager 动画中重抓 | pressProgress、scaleX/Y、velocity、release 次数、currentPage | 区分形变清理、手势仲裁和 Pager 回流 |
| D | 手机/宽屏 Dock 重选，发现页列表/海报/宫格/空态/错误态 | 回调链、列表 index/offset、刷新请求数 | 验证手机接线与所有状态消费者 |
| E | 历史月、网络失败、分页大结果 | range、每页 total/条数、缓存状态、最终过滤数量 | 区分合法空结果和失败被缓存 |
| F | 评分同刻度抖动、跨 0.5、拖到 10 松手、取消、原本就是 10 | haptic 次数、celebrating、颜色/缩放 | 验证单位修复和取消语义 |
| G | 详情横滑含普通卡与查看全部卡，系统大字体 | 每个 item 的 bounds / row height | 验证轨道是否仍受尾卡影响 |
| H | 搜索收起/展开、发现三种布局、不同背景/玻璃档位 | layer tree、截图、首项可见性、fade alpha | 验证跨父级绘制顺序和黑带来源 |

## 10. 后续实现优先级

1. 修复手机 Dock 重选回调接线和 `ScrollToTop` 方向/取消语义。
2. 修复 Dock 坐标域分离，并补充唯一手势 owner；暂不改玻璃参数。
3. 先用逐帧证据稳定共享海报外层几何，再处理页面转场叠加。
4. 修复评分触感单位错配，补充取消与闭包时序测试。
5. 统一详情横滑尾卡高度；验证发现页跨父级渐隐层级后再做最小布局调整。
6. 最后处理 CRT 视觉增强、直接“看过”是否触发剧集波浪、音乐曲目详情页是否移除等产品语义决定。

## 11. 验证边界与需要补充的资料

本轮已执行离线 dependencyInsight，确认 debugRuntimeClasspath 中 Compose animation 实际解析为 1.11.1；命令成功退出。这只是依赖解析，不是 assemble、单测或视觉验收。本轮没有运行 APK、真机/UI 测试，也没有请求 VNDB/Bangumi 在线 API；所以特定作品命中、触控重叠、真实掉帧及逐帧轨迹仍未验收。仓库已有大量用户未提交改动，本次只新增这份文档，不覆盖它们。

不必先下载大仓库才能完成已确认的问题。共享转场实际使用 AndroidX Jetpack Compose Animation（SharedTransitionScope/sharedElement），并不是独立的 Kazumi 共享元素库。若继续核对框架内部，资料名称为 AndroidX/androidx 中 compose/animation，版本对应 animation-android 1.11.1；本机已发现该版本 sources.jar，可优先使用，无需整仓下载。若希望对照‘Kazumi 同款’设计，再补充当前参照的 Kazumi 仓库及其详情转场版本，不能把 Kotlin 注释当作同一实现。

液态玻璃参考目录是无 .git 的源码快照，声明 backdrop 2.0.1；项目执行依赖为 1.0.6。如要核查精确 shader 行为，需要 io.github.kyant0:backdrop:1.0.6 对应 sources 或发布 tag，而不是直接升级到参考快照。最有价值的运行资料是原速/慢放录屏、出问题的条目 ID、设备/API/刷新率/字体倍率和玻璃档位；VNDB 与历史放送追加脱敏 Logcat。不要提供账号密钥或令牌。

本报告没有修改业务代码，也没有把未运行的设备现象写成已复现事实。
