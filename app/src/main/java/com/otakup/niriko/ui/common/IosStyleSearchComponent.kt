package com.otakup.niriko.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.otakup.niriko.ui.components.appleGlassCard
import com.otakup.niriko.ui.theme.NirikoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** 拖拽提交阈值：滑块接近最左端（0.9）即触发沉浸，无需精确拖到边缘。 */
private const val COMMIT_THRESHOLD = 0.9f

/** 沉浸态绿条变暗后的底色（原型 rgba(46,125,50,.16)）。 */
private val IndicatorDim = Color(0x292E7D32)
/** 拖拽态绿条亮绿（原型 #43A047）。 */
private val IndicatorBright = Color(0xFF43A047)

/**
 * iOS 风格搜索组件 —— 对齐 HTML 原型的 4 状态状态机。
 *
 * 状态机（由 [SearchViewModel] 驱动，纯 UI 层）：
 * - [SearchPhase.COLLAPSED]        56×56 圆角玻璃按钮（放大镜）
 * - [SearchPhase.MENU_EXPANDED]    短按按钮 → 以右上角为锚点展开全宽菜单
 *                                   （行1 假轨道 + 行2 模式 + 行3 类型标题 + 行4 类型）
 * - [SearchPhase.DRAGGING]         在轨道上水平拖动 → Canvas 绘制绿条拉长（右缘固定、左缘跟手）
 * - [SearchPhase.IMMERSIVE_SEARCH] 拖到最左阈值 → 绿条变暗化为底色，假轨道→真 BasicTextField，
 *                                   放大镜滑到最左，X 从右侧分裂弹出；点 X → COLLAPSED
 *
 * 原生实现规范：
 * 1. 手势隔离：轨道区 [detectHorizontalDragGestures]，onHorizontalDrag 中显式
 *    [androidx.compose.ui.input.pointer.PointerInputChange.consume]，配合 onGestureLockChange
 *    锁住外层 Pager，杜绝滑动冲突。
 * 2. 绿条拉长：Canvas + drawRoundRect 按拖拽相对坐标绘制，**不改布局尺寸**，无重测卡顿。
 * 3. 假输入框：COLLAPSED/MENU/DRAGGING 只渲染 Text + 背景装饰；IMMERSIVE 才渲染
 *    BasicTextField 并 requestFocus 唤起软键盘。
 * 4. 细胞分裂：菜单卡片 graphicsLayer transformOrigin=(1,0) 右上角锚点缩放浮现；
 *    X 按钮 AnimatedVisibility(fadeIn + slideInHorizontally) 从右侧弹簧滑出。
 */
@Composable
fun IosStyleSearchComponent(
    viewModel: SearchViewModel,
    onModeSelected: (SearchMode) -> Unit,
    onTypeSelected: (ContentType) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onGestureLockChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    val phase = viewModel.phase
    val query = viewModel.query
    val progress = viewModel.dragProgress
    val filterExpanded = viewModel.filterRowExpanded

    // ===== 尺寸常量（dp，与原型一致） =====
    val anchorDp = 56.dp            // COLLAPSED 表面/锚点尺寸
    val menuH = 200.dp              // 菜单/沉浸展开高度
    val collapsedH = 68.dp          // 滚动折叠后高度（仅留搜索框）
    val colPad = 8.dp
    val trackH = 56.dp
    val anchorSmall = 44.dp         // 菜单/沉浸态锚点（滑块）尺寸

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // ===== 展开卡片尺寸（仅在 phase 切换时动画，非手势帧 → 无卡顿） =====
    val isCollapsed = phase == SearchPhase.COLLAPSED
    val isImmersive = phase == SearchPhase.IMMERSIVE_SEARCH
    val immersiveCollapsed = isImmersive && !viewModel.editing && !filterExpanded
    val cardSpring = spring<Dp>(dampingRatio = 0.8f, stiffness = 400f) // Apple 质感：顺滑不过弹

    BoxWithConstraints(modifier = modifier) {
        // 卡片全宽 = 屏幕宽 - 左右 16dp 边距，改用真实屏幕宽驱动：
        // 隔离 Box 的高度被锁死 56dp 后，BoxWithConstraints.maxWidth 会退化成 56dp，
        // 若仍用 maxWidth-32dp 会把展开卡片宽度塌缩成 24dp；改屏幕宽彻底规避。
        val screenW = with(LocalConfiguration.current) { screenWidthDp.dp }
        val fullW = (screenW - 32.dp).coerceAtLeast(anchorDp)
        val cardW by animateDpAsState(
            targetValue = if (isCollapsed) anchorDp else fullW,
            animationSpec = cardSpring,
            label = "cardW",
        )
        val cardH by animateDpAsState(
            targetValue = when {
                isCollapsed -> anchorDp
                immersiveCollapsed -> collapsedH
                else -> menuH
            },
            animationSpec = cardSpring,
            label = "cardH",
        )

        // 卡片浮现：右上角锚点缩放 + 透明度（transformOrigin = (1, 0)）
        val reveal by animateFloatAsState(
            targetValue = if (isCollapsed) 0f else 1f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
            label = "reveal",
        )
        // 行内容交错出现
        val contentAlpha by animateFloatAsState(
            targetValue = if (isCollapsed) 0f else 1f,
            animationSpec = tween(240),
            label = "contentAlpha",
        )
        val rowsAlpha by animateFloatAsState(
            targetValue = if (immersiveCollapsed) 0f else 1f,
            animationSpec = tween(220),
            label = "rowsAlpha",
        )
        val rowsShift by animateFloatAsState(
            targetValue = if (immersiveCollapsed) -10f else 0f,
            animationSpec = tween(220),
            label = "rowsShift",
        )

        // ===== 锚点（放大镜）位置：COLLAPSED 填满 / MENU 右端 / DRAGGING 跟手 / IMMERSIVE 最左 =====
        // 单一驱动源：DRAGGING 态下 X = 初始右端 - progress*maxTravel，由手势 progress 实时线性驱动，
        // 绝对 1:1 跟手、无动画竞争抽搐；MENU/IMMERSIVE 用固定目标值（IMMERSIVE 由动画收敛到最左）。
        // 滑块几何：真实行程 = 轨道宽 - 滑块宽，progress=1 时滑块左缘恰好到轨道左缘（无空缺/无溢出）。
        val trackW = fullW - colPad * 2f
        val maxTravelDp = trackW - anchorSmall
        // 锚点基础 X（MENU=右端 / IMMERSIVE=最左 / COLLAPSED=填满；DRAGGING 停在右端）
        val anchorX by animateDpAsState(
            targetValue = when (phase) {
                SearchPhase.COLLAPSED -> 0.dp
                SearchPhase.MENU_EXPANDED, SearchPhase.DRAGGING -> fullW - colPad - anchorSmall
                SearchPhase.IMMERSIVE_SEARCH -> colPad + 6.dp
            },
            animationSpec = cardSpring,
            label = "anchorX",
        )
        // 拖拽位移（布局级 offset 叠加，非动画）：DRAGGING 时 = -progress*maxTravel，
        // 与手势 progress 严格 1:1 同步；其余状态为 0（由 anchorX 收敛）
        val dragOffsetX = if (phase == SearchPhase.DRAGGING) -maxTravelDp * progress else 0.dp
        val anchorY by animateDpAsState(
            // 垂直居中于 56dp 轨道：轨道 Box 在卡片内 colPad(8dp) 之下，轨道中心 = 8+28=36dp；
            // 锚点高 44dp → y = 36-22 = 14dp = colPad + (trackH-anchorSmall)/2（COLLAPSED 填满卡片为 0）
            targetValue = if (isCollapsed) 0.dp else colPad + (trackH - anchorSmall) / 2,
            animationSpec = cardSpring,
            label = "anchorY",
        )
        val anchorSize by animateDpAsState(
            targetValue = if (isCollapsed) anchorDp else anchorSmall,
            animationSpec = cardSpring,
            label = "anchorSize",
        )

        // ===== 手势几何（px，稳定值不随布局动画变化） =====
        val maxTravelPx = with(density) { maxTravelDp.toPx() }
        val anchorSmallPx = with(density) { anchorSmall.toPx() }
        val trackTopPx = with(density) { 4.dp.toPx() }
        val trackHPx = with(density) { (trackH - 8.dp).toPx() }

        val phaseState = rememberUpdatedState(phase)
        val progressState = rememberUpdatedState(progress)
        val vm = rememberUpdatedState(viewModel)
        val lockChanged = rememberUpdatedState(onGestureLockChange)

        // 手势参数：移动容差（进入拖拽的判定阈值）
        val viewConfig = LocalViewConfiguration.current
        val touchSlop = viewConfig.touchSlop

        // 沉浸态聚焦输入框（唤起软键盘与光标）。
        // 从保存态恢复的沉浸态（详情页返回/进程重建，suppressAutoFocus=true）不自动聚焦：
        // 用户看完作品返回不应弹键盘重新搜索；仅用户主动展开（拖拽/点击）时聚焦。
        LaunchedEffect(phase) {
            if (phase == SearchPhase.IMMERSIVE_SEARCH && !viewModel.suppressAutoFocus) {
                searchFocusRequester.requestFocus()
                // 兜底：部分设备 requestFocus 后软键盘不自动弹出，显式唤起（用户反馈"无法输入"）
                keyboardController?.show()
            }
        }

        // ============ 表面（唯一玻璃实体） ============
        Box(
            modifier = Modifier
                // 强制右上角几何溢出：内容超过容器时以 TopEnd 为锚点放置，unbounded 允许溢出，
                // 展开只向左/向下"吐出"；不再使用任何 offset/align 位移补偿
                .wrapContentSize(align = Alignment.TopEnd, unbounded = true)
                // required*：无视父约束强制尺寸——SearchToolbar 的 56dp Box 会把普通 width/height
                // clamp 到 56dp，导致 MENU 只展开成一条搜索栏、行 2-4 不可见（用户反馈根因）
                .requiredWidth(cardW)
                .requiredHeight(cardH)
                .graphicsLayer {
                    // 纯物理尺寸展开：无 scale/transformOrigin（避免缩放原点在容器外造成"上顶"），
                    // 仅保留内容淡入；尺寸由 requiredWidth/Height + animateDpAsState 驱动
                    alpha = if (isCollapsed) 1f else reveal.coerceIn(0f, 1f)
                }
                // 液态玻璃表面：appleGlassCard（真折射/静态玻璃自动降级）。
                // 额外在内容层下方垫一层近不透明 surface，保证展开菜单叠在滚动列表上仍可读。
                .appleGlassCard(shape = RoundedCornerShape(28.dp))
                // 手势挂在不移动的卡片容器上（非锚点）：position 相对卡片稳定，dx 即手指真实位移，
                // 1:1 跟手（修复"锚点参考系漂移 → 半速跟手/50% 封顶"的根因）。
                // down 时校验手指落在右侧锚点区域才处理拖拽/短按，其余区域放行给模式/类型点击。
                .then(
                    if (!isImmersive) {
                        Modifier.pointerInput(maxTravelPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // 锚点命中区：COLLAPSED 锚点填满整卡(56dp)；MENU/DRAGGING 为右侧 anchorSmall
                                val anchorZone = if (vm.value.phase == SearchPhase.COLLAPSED) size.width.toFloat() else anchorSmallPx
                                if (down.position.x < size.width.toFloat() - anchorZone) return@awaitEachGesture
                                lockChanged.value(true)
                                try {
                                    // 按下即拖的位移基准：手指落在锚点上的位置（卡片坐标，稳定）
                                    var startX = down.position.x
                                    val startY = down.position.y
                                    var dragging = false
                                    var ended = false

                                    while (!ended) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                        if (!change.pressed) {
                                            // 松手
                                            if (dragging) {
                                                if (vm.value.phase == SearchPhase.DRAGGING) {
                                                    vm.value.exitDraggingToMenu()
                                                }
                                            } else {
                                                // 短按：COLLAPSED→菜单；MENU→收起
                                                when (vm.value.phase) {
                                                    SearchPhase.COLLAPSED -> vm.value.setMenuExpanded(true)
                                                    SearchPhase.MENU_EXPANDED -> vm.value.setMenuExpanded(false)
                                                    else -> {}
                                                }
                                            }
                                            ended = true
                                            break
                                        }

                                        if (change.isConsumed) { ended = true; break }

                                        // 按下即拖：移动超过 touchSlop 立即进入拖拽
                                        if (!dragging) {
                                            val movedX = abs(change.position.x - startX)
                                            val movedY = abs(change.position.y - startY)
                                            if (movedX > touchSlop || movedY > touchSlop) {
                                                if (vm.value.phase == SearchPhase.COLLAPSED) {
                                                    vm.value.setMenuExpanded(true)
                                                }
                                                if (vm.value.phase == SearchPhase.MENU_EXPANDED) {
                                                    dragging = true
                                                    vm.value.enterDragging()
                                                    vm.value.updateDragProgress(0f)
                                                    // 重置基准：COLLAPSED 展开动画期间锚点/卡片参考系变化，
                                                    // 以当前手指位置为新起点，避免进度跳变
                                                    startX = change.position.x
                                                } else {
                                                    ended = true
                                                    break
                                                }
                                            } else {
                                                continue
                                            }
                                        }

                                        // 拖拽中：左移跟手（卡片坐标稳定，dx=手指真实位移）
                                        change.consume()
                                        val dx = startX - change.position.x
                                        val p = (dx / maxTravelPx).coerceIn(0f, 1f)
                                        vm.value.updateDragProgress(p)
                                        if (p >= COMMIT_THRESHOLD) {
                                            vm.value.expandSearch()
                                            ended = true
                                            break
                                        }
                                    }
                                } finally {
                                    lockChanged.value(false)
                                }
                            }
                        }
                    } else Modifier
                ),
        ) {
            // 垫层：展开菜单压在滚动列表上时保证可读性（玻璃之上、内容之下）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            ) {}

            // ===== 内容列（COLLAPSED 时整体淡出） =====
            // 自顶向下排布：Arrangement.Top 使第一行紧贴卡片顶部，卡片从 56dp 向下生长时
            // 第一行位置零移动（"从搜索按钮向下吐出"）；行 2/3/4 用固定高度+padding，不均分
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(colPad)
                    .graphicsLayer { alpha = contentAlpha },
                verticalArrangement = Arrangement.Top,
            ) {
                // ---- 行 1：轨道（假输入框 / Canvas 绿条 / 真输入框 / 拖拽手势） ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackH),
                ) {
                    // 假输入框背景（COLLAPSED/MENU/DRAGGING 可见；IMMERSIVE 淡出）
                    val fakeAlpha by animateFloatAsState(
                        targetValue = if (isImmersive) 0f else 1f,
                        animationSpec = tween(200),
                        label = "fakeAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .graphicsLayer { alpha = fakeAlpha }
                            .appleGlassCard(shape = RoundedCornerShape(24.dp)),
                    )
                    // 假输入框占位文字（仅非沉浸态）：提示左划解锁
                    if (!isImmersive) {
                        Text(
                            text = "← 左划开启搜索",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 22.dp)
                                .graphicsLayer { alpha = fakeAlpha },
                        )
                    }

                    // ---- 绿条指示器：Canvas 绘制，绝不修改布局尺寸 ----
                    val indSettle by animateFloatAsState(
                        targetValue = if (isImmersive) 1f else 0f,
                        animationSpec = tween(260),
                        label = "indSettle",
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val visible = phaseState.value == SearchPhase.DRAGGING ||
                            phaseState.value == SearchPhase.IMMERSIVE_SEARCH
                        if (!visible) return@Canvas
                        // 滑块中心（轨道坐标）：初始在轨道右端（右缘 - 滑块半宽），随 progress 左移 1:1
                        val thumbHalf = anchorSmallPx / 2f
                        val sliderCenter = size.width - thumbHalf - progressState.value * maxTravelPx
                        // 绿色区域：始终从滑块位置延伸到轨道右缘（左端=滑块，右端=轨道右缘，无空缺、完全贴合）
                        val w = if (phaseState.value == SearchPhase.IMMERSIVE_SEARCH) {
                            size.width * indSettle
                        } else {
                            (size.width - sliderCenter).coerceAtLeast(0f)
                        }
                        if (w <= 0f) return@Canvas
                        val color = if (phaseState.value == SearchPhase.IMMERSIVE_SEARCH) {
                            IndicatorDim
                        } else {
                            IndicatorBright
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(size.width - w, trackTopPx),
                            size = Size(w, trackHPx),
                            cornerRadius = CornerRadius(trackHPx / 2f),
                        )
                    }

                    // ---- 真输入框：仅 IMMERSIVE 渲染（避免劫持触摸/软键盘冲突） ----
                    if (isImmersive) {
                        val inputAlpha by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = tween(260),
                            label = "inputAlpha",
                        )
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChanged,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 58.dp, end = 60.dp)
                                .graphicsLayer { alpha = inputAlpha }
                                .focusRequester(searchFocusRequester)
                                // 编辑态（光标存在）：通知状态机，菜单强制保持展开不折叠
                                .onFocusChanged { vm.value.markEditing(it.isFocused) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                            decorationBox = { innerTextField ->
                                // contentAlignment=Center：占位文字与输入内容在轨道内垂直居中，
                                // 与放大镜/X 的中轴线一致（此前默认 TopStart 导致整体偏上）
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "搜索作品、人物…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }
                }
                // ---- 行 2：模式切换（作品/人物） ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(top = 4.dp)
                        .graphicsLayer { alpha = rowsAlpha; translationY = rowsShift },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip(
                        "作品", viewModel.mode == SearchMode.WORKS,
                        onClick = { onModeSelected(SearchMode.WORKS) },
                    )
                    Chip(
                        "人物", viewModel.mode == SearchMode.CHARACTERS,
                        onClick = { onModeSelected(SearchMode.CHARACTERS) },
                    )
                }
                // ---- 行 3：类型标题 ----
                Text(
                    text = "类型",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .height(18.dp)
                        .padding(top = 4.dp, start = 12.dp)
                        .graphicsLayer { alpha = rowsAlpha; translationY = rowsShift },
                )
                // ---- 行 4：类型（含「全部」；横向可滚动） ----
                // 第 5 轮 D21/D22：
                //  - **「全部」回来了**。第 4 轮为了去掉「发现页的『全部』」删掉了
                //    SELECTABLE_CONTENT_TYPES 里的 ALL，但那个常量**只被本组件使用**
                //    （当时「找条目」另有自己的类型常量；第 6 轮它已并入历史排名并被删除），
                //    结果发现页没改对、搜索菜单的「全部」反而被误删。
                //    搜索场景下「不按类型过滤」是真实需求，必须保留。
                //  - **改回 SpaceEvenly 均分**（第 5 轮返工）。
                //    中间那版为了塞下 6 个类型改成了横向滚动 + spacedBy，结果每个 cell
                //    缩成内容宽：图标看起来变小、右侧留出一大片空白 —— 用户明确反馈了这两点。
                //    6 个 cell 在 360dp 下每个约 60dp，放得下 20dp 图标 + 3 字标签。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(top = 4.dp)
                        .graphicsLayer { alpha = rowsAlpha; translationY = rowsShift },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SELECTABLE_CONTENT_TYPES.forEach { type ->
                        TypeCell(
                            label = type.label,
                            type = type,
                            selected = viewModel.contentType == type,
                            onClick = {
                                // 「全部」本身就是合法选择；其余类型再点一次取消 → 回到「全部」
                                if (type != ContentType.ALL && viewModel.contentType == type) {
                                    onTypeSelected(ContentType.ALL)
                                } else {
                                    onTypeSelected(type)
                                }
                            },
                        )
                    }
                }
            }

            // ===== 锚点（放大镜）：唯一手势入口（COLLAPSED 短按 ⇄ 菜单） =====
            Box(
                modifier = Modifier
                    // 布局级叠加：baseAnchorX(动画收敛) + dragOffsetX(拖拽 1:1 跟手)
                    .offset(x = anchorX + dragOffsetX, y = anchorY)
                    .size(anchorSize)
                    .graphicsLayer {
                        // 细胞分裂：放大镜从右端滑到左端。
                        // 位置完全由 anchorX（progress 直接驱动）负责，此处不再叠加 translationX，
                        // 避免双重状态源冲突导致的抽搐闪回（单一驱动源）
                        alpha = if (isCollapsed) 1f else 0.95f
                    }
                    // 凸起玻璃按钮：MENU/DRAGGING 态与 fake-track 拉出层级差
                    .then(
                        if (!isCollapsed && !isImmersive) {
                            Modifier.appleGlassCard(shape = RoundedCornerShape(24.dp))
                        } else {
                            Modifier.clip(RoundedCornerShape(if (isCollapsed) 18.dp else 24.dp))
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(if (isCollapsed) 22.dp else 19.dp),
                )
            }

            // ===== X 关闭按钮（细胞分裂：从右侧弹簧滑出） =====
            AnimatedVisibility(
                visible = phase == SearchPhase.IMMERSIVE_SEARCH,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // 垂直居中于 56dp 轨道：X 高 44dp → y = colPad + (56-44)/2 = 14dp，与锚点中轴线一致
                    .offset(x = -colPad - 6.dp, y = colPad + (trackH - anchorSmall) / 2),
                enter = fadeIn(tween(160)) +
                    slideInHorizontally(animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f)) { it },
                exit = fadeOut(tween(140)) +
                    slideOutHorizontally(animationSpec = tween(200)) { it },
            ) {
                Box(
                    modifier = Modifier
                        .size(anchorSmall)
                        .appleGlassCard(shape = RoundedCornerShape(anchorSmall / 2))
                        .clickable {
                            focusManager.clearFocus()
                            // 清空已输入内容（同步 SubjectSearchViewModel 与 SearchViewModel 镜像），
                            // 避免下次展开还残留上次搜索内容
                            onQueryChanged("")
                            vm.value.collapse()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭搜索",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 小胶囊（菜单内模式选项）。 */
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                else Color.Transparent
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 类型项（图标 + 文字，一行 5 个）。 */
@Composable
private fun TypeCell(
    label: String,
    type: ContentType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TypeIcon(type)
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 类型图标（对齐原型：番剧/书籍/游戏/音乐/三次元）。 */
@Composable
private fun TypeIcon(type: ContentType) {
    val icon = when (type) {
        ContentType.ALL -> Icons.Default.Search
        ContentType.ANIME -> Icons.Default.PlayArrow
        ContentType.BOOK -> Icons.Default.MenuBook
        ContentType.GAME -> Icons.Default.VideogameAsset
        ContentType.MUSIC -> Icons.Default.MusicNote
        ContentType.REAL -> Icons.Default.PhotoCamera
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

/** 独立预览：可在 Android Studio 中直接交互验证 4 状态状态机。 */
@Preview(showBackground = true, widthDp = 390, heightDp = 300)
@Composable
private fun IosStyleSearchComponentPreview() {
    NirikoTheme {
        val vm = remember { SearchViewModel() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFE6EEE0),
                            Color(0xFFC8D5C0),
                        )
                    )
                ),
        ) {
            // 顶栏示意：左侧趋势标签 + 右上角搜索组件
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "当季热门 · 历史排名",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IosStyleSearchComponent(
                        viewModel = vm,
                        onModeSelected = { mode ->
                            vm.syncMode(mode == SearchMode.CHARACTERS)
                        },
                        onTypeSelected = { type -> vm.syncContentType(type.bangumiType) },
                        onQueryChanged = vm::syncQuery,
                        onSearchSubmit = {},
                        onGestureLockChange = {},
                    )
                }
                // 状态机调试输出
                Text(
                    text = "State: ${phaseName(vm.phase)}${if (vm.phase == SearchPhase.DRAGGING) " · ${(vm.dragProgress * 100).roundToInt()}%" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun phaseName(phase: SearchPhase): String = when (phase) {
    SearchPhase.COLLAPSED -> "1 COLLAPSED"
    SearchPhase.MENU_EXPANDED -> "2 MENU_EXPANDED"
    SearchPhase.DRAGGING -> "3 DRAGGING"
    SearchPhase.IMMERSIVE_SEARCH -> "4 IMMERSIVE_SEARCH"
}
