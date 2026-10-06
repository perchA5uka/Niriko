# 顶部控件、历史放送与共享转场方案

## 本轮边界

VNDB关联作品由用户实测正常，记录为已验收完成，不再修改。Dock整栏交互及已验收渐隐机制保留。用户已批准方案实施；原型及既有详情原型不再修改，应用修改以以下补充约束为准。

原型参照 [detail-page-prototype.html](<detail-page-prototype.html>) 的深色手机框与并排对照组织方式，交互用本地样例数据。CSS玻璃是展示近似，不等同 Android backdrop 的真实AGSL折射。设计批准后才实施应用。

## 用户确认后的实施约束

- 保留应用搜索部件现有展开宽度与右上锚点、展开动画及作品/人物搜索功能，不按HTML原型较窄面板重建，也不擅改为无边距edge-to-edge全屏。实际宽度规则以现有组件代码为基准。
- 发现页明确保留“作品/人物”搜索模式切换；作品库替换为收藏状态+数量与本地搜索，不把人物模式误删出发现页。
- 作品库与发现页各自拥有独立左上选择器状态和选项，两个页面都必须显示；两者位置、基线一致。发现为榜单模式，作品库为分区，各自纵向展开，文本改变驱动胶囊宽度收缩伸长。
- 搜索展开不再改变信息流topInset/contentPadding或滚动位置；仅保留收起悬浮栏下的固定初始安全内容padding。菜单覆盖信息流，不画整片header背景或为展开高度让位。左选择器展开同样不推移内容。
- 常态视图按钮移除，入口置于搜索框下、状态/类型行上方、菜单最右侧。排序、标签、多选、分区管理及原3种视图不可丢失。
- 不修改原型；不升级Shapes或玻璃库，不改已验收Dock参数。返回动画、历史选中月份状态和玻璃菜单按独立证据实施并验证。

## 一、统计页选中上月“放送信息”为空

### 正确问题定义

本问题指统计月表：本月显示放送内容，前移至上月后空白，反复刷新仍空；不是某部作品延期章节日期漏算。上一批真实剧集日期修复不能作为这项已完成的证据。

### 已核实链路

- [CalendarCard.kt:486](<../app/src/main/java/com/otakup/niriko/ui/stats/CalendarCard.kt#L486>) 前箭头调用 -1；月/周取决于当前视图粒度，必须在月视图复现。
- [StatsViewModel.kt:157](<../app/src/main/java/com/otakup/niriko/viewmodel/StatsViewModel.kt#L157>) → applyCalendarAnchor 写入选中日期，触发目标范围月份加载（183–207）。同年上月不被有效年份范围排除。
- [StatsViewModel.kt:217](<../app/src/main/java/com/otakup/niriko/viewmodel/StatsViewModel.kt#L217>) 目标月请求窗口为目标月初前6个月至下一月初，不是向 /calendar 索取历史快照。例如10月前移至9月，请求当年03-01至10-01（右边界不含）。
- [StatsViewModel.kt:327](<../app/src/main/java/com/otakup/niriko/viewmodel/StatsViewModel.kt#L327>) 刷新清除加载时间后重试当前anchor，同时另刷新本周 /calendar；在途同月请求会合并而非不断重发。
- [BangumiDataSource.kt:311](<../app/src/main/java/com/otakup/niriko/data/remote/bangumi/BangumiDataSource.kt#L311>) 使用POST v0/search/subjects，keyword为空、sort=date、type为2/6、air_date区间，已分页。不能把旧注释“只取100条”当当前根因。
- [SeasonalFetcher.kt:73](<../app/src/main/java/com/otakup/niriko/data/remote/SeasonalFetcher.kt#L73>) 依次拉ANIME和REAL，解析日期、补部分集数、合并落库。REAL失败会使整次加载失败，即使ANIME先成功。
- [StatsCalculator.kt:198](<../app/src/main/java/com/otakup/niriko/data/calculator/StatsCalculator.kt#L198>) 使用目标月份seasonal数据与日历候选，按日期/平台/章节信息生成事件。本月calendar有图不证明历史区间检索可用。

### 确定缺陷与未知项

确定的状态缺陷：历史月“上次更新”仍取本周BROADCAST_CALENDAR成功时间（[StatsViewModel.kt:68](<../app/src/main/java/com/otakup/niriko/viewmodel/StatsViewModel.kt#L68>)），没有该月独立loading/lastSuccess状态。故刷新时间更新不等于上月成功；空白无法区分正在加载、失败、合法空和无封面。日格无封面与无事件也要分别核实。

方案阶段静态源码不足以锁定用户上月全空的唯一根因。批准实施后新增实网证据：应用POST历史搜索带sort=date，公开反代HTTP400明确返回sort not supported，官方schema也只列match/heat/rank/score；此具体协议错误已修为rank并补分页契约测试。网络仍有超时风险，不能把修正协议等同所有设备月份已验收。排除“未触发上月”“只刷新本月”“TTL永久挡住刷新”“目前仅第一页”后，重点区分实际端点/POST失败、ANIME成功而REAL失败、日期映射丢弃、平台筛选、以及事件存在但封面缺失。不要继续叠加估算规则或把所有空结果当失败。

### 最小实施方案

1. 用选中年月作为独立资源key，提供 Loading / Success / Empty / Error + lastSuccessAt。月表展示该key而非本周更新时间；刷新不跳回本月，失败保留旧成功内容。
2. 补安全诊断计数：请求年月/range/type/HTTP/每页total与data长度；映射条数→合法日期条数→可显示平台条数→目标月份事件数。无需任何账号令牌或完整用户数据。
3. 按第一处计数断点修具体网络/DTO/过滤逻辑。若实际为REAL异常，设计按源Result局部保留动画候选并显式报部分失败，不能当全量成功刷新TTL。
4. 公共方法回归：固定today为某年10月，BROADCAST/月视图→switchMonth(-1)，源calendar仅放10月，区间返回7月持续到9月和9月开播条目；断言9月事件有历史作品无10月作品。覆盖失败后刷新、空成功后刷新返回非空、同月在途去重、ANIME成功REAL失败以及UI日期格/日详情。

所需运行资料：实际年月、是否月视图、点击前移与刷新后的脱敏StatsVM/SeasonalFetcher/HTTP状态。若用户不方便提供，可下一实施轮用已有模拟器复现并记录，不让诊断阻断UI原型评审。

## 二、预测返回与海报中途跳位

### 两种现象必须分开

A. 手势约半程海报已回收、后半程几乎无变化：时间轴/进度映射问题。
B. 海报先抵达屏幕三分之一以下的位置，再无动画回到常态顶部：目标坐标与绘制交接问题。

缓存和原比例交接已存在，但不保证所有冷图片首帧成功，不能因此认为几何和时钟问题已消失。

### 代码证据

- [NirikoNavHost.kt:129](<../app/src/main/java/com/otakup/niriko/navigation/NirikoNavHost.kt#L129>) 页面以SharedAxis Z淡入/缩放弹簧过渡。
- [NirikoAnimation.kt:107](<../app/src/main/java/com/otakup/niriko/ui/animation/NirikoAnimation.kt#L107>) 回程页缩放/淡出；[NirikoMotionSpecs.kt:30](<../app/src/main/java/com/otakup/niriko/ui/animation/NirikoMotionSpecs.kt#L30>) spatial spring为0.9/700。
- 海报sharedElement调用没有自定义boundsTransform；本机实际animation-android 1.11.1源码默认Rect spring为StiffnessMediumLow，默认placeholder ContentSize，目标尺寸由lookahead给出。页面与共享Rect不同spring不是一个空间进度曲线；共享bounds绑定同根Transition.DeferredAnimation，并不是独立wall-clock。系统seek整根Transition时，progress与空间位移不是线性对应，即使时长一致，spring长尾也会造成后半段变化很小。
- [PredictiveDismiss.kt:38](<../app/src/main/java/com/otakup/niriko/ui/animation/PredictiveDismiss.kt#L38>) 只服务本地搜索/弹层，不是作品详情Navigation共享转场的驱动器。不能给详情再加第二返回handler争夺输入。
- [SubjectDetailScreen.kt:475](<../app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt#L475>) headerHeight初始statusTop+48dp，实际按钮48dp外有上下4dp，onSizeChanged回写约statusTop+56dp；[SubjectDetailScreen.kt:848](<../app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt#L848>) Lazy顶部内容padding随此值变化。确定有8dp后测量修正，但8dp不能解释三分之一屏的大跳，不能错误归因。
- 共享层结束后要交还正常布局；父页面scale、Lazy lookahead/placeholder、图片真实比例变化、滚动保存位置/源overlay坐标需统一核查。

### 最小方案（先取证后实现）

记录同一帧window/global source Rect、lookahead target Rect、hero实际Rect、shared overlay Rect、page scale/transform origin、headerHeight、painter ratio/cache URL、Lazy index/offset，以及系统BackEvent.progress→Transition playTime。需覆盖0/0.1/0.25/0.5/0.75/1，向前/回退/取消再抓。

让共享层目标Rect与常态hero Rect完全一致，父页转换坐标只应用一次；先固定正确header安全高度与稳定原图ratio，禁止转场结束后再布局矫正。第一组最小对照实验是仅对作品详情共享封面路径关闭页级scale（必要时Enter/Exit None，保留其他路由），并把共享节点移到不受pressTilt父层影响的外框；不是取消全应用按压动画。[LibraryGalleryCard.kt:78](<../app/src/main/java/com/otakup/niriko/ui/library/LibraryGalleryCard.kt#L78>)父卡倾斜，[PressTilt.kt:156](<../app/src/main/java/com/otakup/niriko/ui/animation/PressTilt.kt#L156>)含rotation/scale。本机SharedContentNode明确有矩阵变换lookahead支持TODO，Rect目前取位置加未变换size，因此它是已核实的坐标契约风险，但未经逐帧数据不能直接断言就是大幅跳位唯一原因。不要仅拉长时长，不能简单认为改AnimatedSize就修所有Lazy问题。

手势阶段用同一个可seek时间轴与连续空间映射，避免默认弹簧在时间轴前半段已收敛；可以验证显式同duration的Linear Rect bounds与页面effects用于预测阶段，正常点击阶段仍保留现有物理反馈。取消时从当前presentation进度平滑回去，commit后只补剩余，不重复完整动画。实际系统progress不等于手指像素距离，不自行把系统行程加倍或改系统阈值。

验收关键：每一段手势都有可解释的连续位置变化；shared overlay退场前后一帧Rect差接近零；海报目标从开始就为最终位置；父页状态与滚动位置在取消后保持。原型不假装模拟Android Navigation真实共享转场。

## 三、两个页面共用顶部控件设计

### 固定锚点，而非固定所有文字宽度

两个页面共享顶部左侧选择器起点与右侧搜索按钮右缘、基线、尺寸和层级。左按钮可按当前标签宽度伸缩，右搜索绝不被挤动；只改变内容和职责，使页面切换时控件在同一位置。左按钮末端为向下等边三角形。长分区名设置宽度上限并省略；窄屏不与右按钮碰撞。

发现左选择器：收起显示当前“当季热门/历史排名/Steam”；向下竖直展开，当前项优先显示，其余顺序稳定。作品库左选择器：当前分区/全部作品，竖展分区列表，新建与管理分区在底部动作区；不可把新建当普通分区。选中项勾选。展开为overlay，不改变信息流padding与滚动位置，不用全窗变暗遮罩；外点/Escape/返回收起。

作品库小统计标签移除。人物收藏不是筛选按钮，建议移入可滚动信息流独立区块或分区入口，不删除数据功能。个人标签、排序、批量操作与分区管理保留在展开菜单/二级管理中，不因压缩顶部丢功能。

### 复用搜索部件而不是复制发现ViewModel

复用搜索4态UI壳（收起、展开菜单、按住拖拽、搜索），数据回调按页面注入。作品库输入搜索本地收藏，横排显示全部/想看/在看/看过/搁置/抛弃；类型图标位置显示该状态作品数。发现输入搜索远程作品，横排显示全部/动画/书籍/游戏/音乐/三次元，保留类型图标。

计数规则建议：按当前分区+关键词+个人标签等其它过滤条件统计各状态，但不被当前已选状态再过滤，避免每个状态除当前外变0。必须与真实数据库查询一致；原型计数仅样例，不硬编码到应用。

视图入口取消常态独立按钮。搜索展开后按“输入框→工具行右侧小视图按钮→状态/类型横排”的顺序；小图标+tooltip/可访问名称，不用占整行的大文字按钮。作品库保留列表/海报网格/画廊三种；发现保留自己的三布局，不能把同名字对应不同旧布局混淆。展开态按钮可打开有选中标记的玻璃菜单或循环下一个布局，原型先给显式选项便于评审。

### 伸缩与变形

Compose用TextMeasurer得到目标字宽，padding、三角、最低触控高度另算；文字切换与width通过同一个可中断Transition协调，锚点不变，记录presentation值以允许快速重选。宽度弹簧先使用当前已验证物理规格，不擅调玻璃参数；字符内容短crossfade，不伪称字体笔画真正morph。shape从胶囊到展开面板可使用Shapes lerp，但尺寸动画不是shape lerp负责。减少动态效果时直接改宽度/短淡入，点击目标仍不移动。

原型包含并排页、同步锚点、竖展选择、状态计数、搜索、视图切换、主题/窄屏与重置工具。手机内只放实际功能文案；原型说明在手机框外。

## 四、液态玻璃统一候选

优先：详情传送门/分享菜单、顶部选择器、视图菜单、作品库排序/分区操作菜单。目前详情按钮已是searchGlassSurface，但[SubjectDetailScreen.kt:588](<../app/src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt#L588>)与633仍为标准Material DropdownMenu，不是同款玻璃。

设计单一GlassAnchoredMenu，统一rounding/material/highlight/外点/返回/焦点/选中语义。采样源为按钮背后页面内容+背景，不能包含菜单自身。Material Popup属于独立窗口，页面LocalBackdrop不保证跨窗口坐标合法；优先页面同坐标系overlay，显式锚点、边界避让和焦点；若继续Popup需验证屏幕→父层偏移，而不是给Surface简单加blur。

材料管线与当前软件数值保持（Dock基底vibrancy/blur8dp/lens24/24、surface alpha0.4）；文字不做折射，FULL/REDUCED/OFF兼容。大信息卡、编辑表单、长段文字、错误提示/删除确认不宜为了“统一”全部透明；保持层级和可读性。统一的是语义同级表面，不是把所有元素贴玻璃。

## 五、Shapes 1.2.1评估

[Shapes README](<C:/what i download/Shapes-1.2.1/README.md>)只有标题，实际已读源码。[Lerp.kt](<C:/what i download/Shapes-1.2.1/shapes/src/commonMain/kotlin/com/kyant/shapes/Lerp.kt#L14>)插值四角半径并限制最大半径，RoundedRectangularShape生成轮廓；demo分别控制aspect ratio和corner ratio，不是字符morph或手势进度引擎。

适用：胶囊展开面板、非等圆角与continuous curvature，和Kyant backdrop lens形状接口配合。某些style过渡默认在fraction=0.5切换style，复杂风格需显式固定style，避免把形状插值当任何风格无跳变。

版本边界：下载仓库构建声明Kotlin2.4.10/Compose1.12.0，当前应用不处于同工具链。实施前检查实际发布POM和依赖解析能否保持项目已有版本，不能直接导入整仓或为了形状升级整个Android项目。本轮只作设计参考，未添加依赖。

## 六、实施顺序与验收

1. 用户评审HTML确定顶部锚点、菜单宽度/间距、计数、视图入口和玻璃菜单。
2. 独立修复统计“选中月”状态与10→9月份完整数据回归，不把VNDB再列待办。
3. 取共享Rect/手势进度证据，先稳定终点交接，再对齐seek映射，覆盖取消和反向。
4. 将共用UI壳接入两个页面既有数据状态，保持分区、排序、标签、多选和三布局。
5. 接入同层级玻璃菜单与可选Shapes轮廓动画，保持原玻璃参数，运行完整测试/构建与实机验收。

本轮没有Android源码变更或APK发布。新增 [顶部控件交互原型](<top-controls-prototype.html>)，既有detail-page-prototype.html不覆盖。原型inline脚本经Node vm.Script语法检查通过，使用的本地detail-1至7图片均存在，无必需外网脚本依赖；纯样例过滤路径已核验。浏览器工具缺少bsk CLI，不能声称完成实际浏览器DOM/尺寸或像素验收。原型的4个评审状态并非Android原有长按拖拽状态机的完整模拟，玻璃为CSS近似，图片使用仓库截图且演示名与截图内作品可能不同，均在手机框外说明。
