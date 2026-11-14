package com.otakup.niriko.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.animation.NirikoPressSpecs
import com.otakup.niriko.ui.animation.PressTiltLighting
import com.otakup.niriko.ui.animation.orSnap
import com.otakup.niriko.ui.animation.pressTilt
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.NirikoAccents
import com.otakup.niriko.ui.theme.NirikoShapes
import com.otakup.niriko.util.RatingFeedbackPolicy

// 液态玻璃控件（开关 / 滑块 / 分段容器）。
//
// 材质：一律复用 appleGlassCard（无卡片 backdrop 时自动退化为静态玻璃，含发光边 / 内厚度）。
// 形状：容器类圆角仍来自 NirikoShapes 令牌；胶囊类控件（开关 / 进度条）自 B6 起用内置
// com.kyant.shapes.Capsule（连续曲率胶囊，与 kyant 上游控件同一几何口径）。
//
// 视觉层（B5 起：轨道 / 圆钮 / 高光的绘制方式，参考 Kyant0/AndroidLiquidGlass 的控件样例）：
// 开关的轨道选中时着色、圆钮改白芯 + 投影；滑块的已选区间改主题色渐变、圆钮由白芯 + 色环
// 改为 surfaceContainerHighest + 0.5dp 描边（WP10）。滑块取值数学仍走 offsetToValue 保持原样；
// v1.2.2（WP10）另补手感：开关可拖拽圆钮，松手按位置/速度落位（glassToggleValueForOffset），
// 拖动中轨道即时预览；开关与滑块都接 interactionSource 做按压缩放与玻璃边缘高光抬升。
// （行注释而非 KDoc：文件头 KDoc 后面紧跟 GlassToggle 自己的 KDoc，ktlint 判 dangling。）

/**
 * 液态玻璃开关：玻璃胶囊底 + 着色轨道 + 白色圆钮。
 *
 * 使用方：收藏编辑 sheet 的「私密收藏」，以及设置页的**全部**开关行
 * （[com.otakup.niriko.ui.settings.SettingsSwitchRow]，B6 起替代 M3 Switch）。
 *
 * 视觉：轨道 = appleGlassCard 玻璃底板 + 选中态强调色填充（动画过渡）；
 * 圆钮 = 白芯 + 柔和投影（保持玻璃上的对比度，深色模式同样可读）。
 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val trackWidth = 52.dp
    val trackHeight = 30.dp
    val thumbSize = 24.dp
    val pad = 3.dp
    // 形状用内置 Shapes 的 Capsule（连续曲率胶囊）：与 kyant 上游开关同一几何口径，
    // 也让「玻璃控件形状」从 RoundedCornerShape 世界收敛到一套库上。
    val shape: Shape = Capsule()
    // 玻璃高光的受光角度由 pressTilt 的 rotationY 驱动（GlassCard.kt 的 highlight lambda 在 draw
    // 阶段才读它，所以这里每帧变化的只有重绘、不会让整行开关重组）。
    val lighting = remember { PressTiltLighting() }
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    // 位移一律在 px 里算：拖拽 delta / velocity 天生是 px，来回换算 dp 会让 thumb 越拖越偏。
    // 行程 = 内缩区宽 − 圆钮 = 46dp − 24dp = 22dp，与 dp 口径的 glassToggleThumbOffset 一致。
    val innerTrackPx = with(density) { (trackWidth - pad * 2).toPx() }
    val thumbPx = with(density) { thumbSize.toPx() }
    val travelPx = glassToggleTargetOffset(innerTrackPx, thumbPx, on = true)
    val restTargetPx = glassToggleTargetOffset(innerTrackPx, thumbPx, on = checked)
    // 用 Animatable 而不是 animateDpAsState：松手后要从「手指拖到的位置」继续弹，
    // animateDpAsState 只会从上一个目标值开始，会先跳一帧再动。
    val thumbOffsetPx = remember { Animatable(restTargetPx) }
    var dragging by remember { mutableStateOf(false) }
    var dragPx by remember { mutableFloatStateOf(restTargetPx) }
    LaunchedEffect(checked, reduceMotion) {
        if (!dragging) {
            thumbOffsetPx.animateTo(
                targetValue = restTargetPx,
                animationSpec = NirikoMotionSpecs.spatialDefault<Float>().orSnap(reduceMotion),
            )
        }
    }
    // 开关 / 滑块 / 玻璃高光共用一个 interactionSource：appleGlassCard 只有在拿到
    // interactionSource 时才会把边缘高光从 0.65 抬到 0.95（GlassCard.kt:143-149）；
    // 之前这里没传，「按下去玻璃亮一下」从来没有发生过。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    // P2-4：触感从「按下」挪到「状态落定」。改造前这里收 PressInteraction，34 个设置开关
    // 连续拨 5 个就是 5 次「按下」的轻响；而按下并不代表值真的变了（按一下再拖回原位也响）。
    // 现在只在值确实翻转时振一次，语义用 Confirm（03 文档 §4 的三种既有语义之一，不新造）。
    val confirmSettle = {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) NirikoPressSpecs.THUMB_PRESS_SCALE else 1f,
        // 按下快、抬起慢：手指离开后 thumb 收得温柔，不会「啪」一下弹回 24dp
        animationSpec = if (pressed) NirikoPressSpecs.pressIn() else NirikoPressSpecs.pressOut(),
        label = "glassToggleThumbScale",
    )
    // 拖动中的即时预览：thumb 一过中线轨道就变色，松手前已经能看到结果（回弹只是收尾）。
    val previewOn = dragging && glassToggleValueForOffset(dragPx, travelPx, velocityPxPerSec = 0f)
    val trackColor by animateColorAsState(
        targetValue = if (if (dragging) previewOn else checked) {
            colorScheme.primary
        } else {
            colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        },
        animationSpec = if (reduceMotion) snap() else NirikoMotionSpecs.effectsFast<Color>(),
        label = "glassToggleTrack",
    )
    val dragState = rememberDraggableState { delta ->
        dragPx = glassToggleDragOffset(dragPx, delta, travelPx)
    }
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            // 按下时整块玻璃轻微倾斜（3°）：比卡片（7°）小，因为开关只有 30dp 高，
            // 大角度会让圆钮看起来脱离轨道；倾斜同时写进 lighting，让边缘高光换受光方向。
            // 缩放交给 thumb 自己做（pressScale = 1f），否则玻璃和圆钮一起放大会显得「肿」。
            .pressTilt(
                interactionSource = interactionSource,
                maxRotation = 3f,
                pressScale = 1f,
                lighting = lighting,
            )
            // disabled 必须看得见差异（M3 Switch 自带该反馈，自绘件要显式给）
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .appleGlassCard(shape = shape, interactionSource = interactionSource, lighting = lighting)
            // 换掉 M3 Switch 后必须自己补 Role.Switch + 开关状态，否则 TalkBack 只剩「按钮」
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
            // indication = null：玻璃上盖矩形水波会破坏材质；按压反馈由 thumb 放大 + 边缘高光承担
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                confirmSettle()
                onCheckedChange(!checked)
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = {
                    // 从「当前可见位置」起拖：弹簧还没到位时若从目标值起算会跳一帧
                    dragPx = thumbOffsetPx.value
                    dragging = true
                },
                onDragStopped = { velocity ->
                    dragging = false
                    val next = glassToggleValueForOffset(dragPx, travelPx, velocity)
                    val nextTargetPx = glassToggleTargetOffset(innerTrackPx, thumbPx, next)
                    // 先 snap 到松手位置再弹：否则会从旧目标值往回跳一帧
                    thumbOffsetPx.snapTo(dragPx)
                    if (next != checked) {
                        confirmSettle()
                        onCheckedChange(next)
                    }
                    thumbOffsetPx.animateTo(
                        targetValue = nextTargetPx,
                        animationSpec = NirikoMotionSpecs.spatialDefault<Float>().orSnap(reduceMotion),
                    )
                },
            )
            .padding(pad),
        contentAlignment = Alignment.CenterStart,
    ) {
        // 轨道填充（玻璃之上的材质层）
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(trackColor),
        )
        // 圆钮：白芯 + 投影；位移走 graphicsLayer.translationX（原 .offset 每帧触发布局重排）
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = if (dragging) dragPx else thumbOffsetPx.value
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .size(thumbSize)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {}
    }
}

// v1.2.2：这里原本是 GlassSegmentedContainer（玻璃底 + 4dp 内距的空壳，全仓 0 调用）。
// 分段控件现在由 ui/components/NirikoSegmentedControl.kt 提供真正的实现（等宽、选中淡底 + 描边、
// reduceMotion 降级），两套入口会让调用方继续各写各的，所以删掉空壳而不是留着当「兼容层」。

/**
 * 液态玻璃滑块：玻璃轨道 + 主题色渐变已选区 + 玻璃同族圆钮（按下放大），支持半步进（[steps]）。
 *
 * 手势与取值数学未变：点击 / 拖拽都走 [offsetToValue]（steps = 19 ⇒ 0.5 步进，0..10 共 21 档）。
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..10f,
    steps: Int = 19,
    modifier: Modifier = Modifier,
    /** 禁用时拦截手势并压暗；M3 Slider 自带该反馈，自绘件必须显式给（同 [GlassToggle]）。 */
    enabled: Boolean = true,
    /**
     * 手指离开 / 点击落定后回调一次（F16 的「松手确认」需要它）。
     *
     * 刻意与 [onValueChange] 分开：拖动过程中的每一次变化都会调 [onValueChange]，
     * 而「松手」只发生一次 —— 满分庆祝必须是后者。
     */
    onValueChangeFinished: (() -> Unit)? = null,
    onValueChangeStarted: (() -> Unit)? = null,
    onValueChangeCancelled: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = NirikoShapes.PillShape
    val trackHeight = 8.dp
    val thumbSize = 22.dp
    val horizontalPad = 14.dp
    val reduceMotion = LocalReduceMotion.current
    // P2-3：深色主题下圆钮原本也用 surfaceContainerHighest，与轨道同源色 —— 只剩 0.5dp/40% 的
    // 描边能区分，圆钮在深色玻璃上等于消失。改走纯白（不透明，对比度可离线断言）。
    val thumbColor = glassSliderThumbColor(
        isDark = LocalDarkTheme.current,
        lightThumb = colorScheme.surfaceContainerHighest,
    )
    // 滑块自己发 PressInteraction：它的手势是裸 pointerInput（没有 clickable），
    // 不补这一层的话 appleGlassCard 的按压高光与 thumb 放大永远不会触发。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) NirikoPressSpecs.SLIDER_THUMB_PRESS_SCALE else 1f,
        // 按下快、抬起慢：手指离开后 thumb 收得温柔，不会「啪」一下缩回 22dp
        animationSpec = if (pressed) NirikoPressSpecs.pressIn() else NirikoPressSpecs.pressOut(),
        label = "glassSliderThumbScale",
    )
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinish by rememberUpdatedState(onValueChangeFinished)
    val latestStart by rememberUpdatedState(onValueChangeStarted)
    val latestCancel by rememberUpdatedState(onValueChangeCancelled)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .appleGlassCard(shape = shape, interactionSource = interactionSource)
            // 语义（V-13 迁移后不能比 M3 Slider 更差）：TalkBack 需要「可调节量」才能拖动与朗读。
            // 迁移前 M3 Slider 自带 ProgressBarRangeInfo + setProgress，自绘件不补就等于把无障碍退掉。
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                    steps = steps,
                )
                setProgress { target ->
                    if (!enabled) {
                        false
                    } else {
                        latestChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                        true
                    }
                }
            }
            // 只观察、不消费事件的按压跟踪：拖拽 / 点击仍由下面两个 pointerInput 负责，
            // 这里只把「手指是否还在屏幕上」翻译成 PressInteraction 给玻璃高光与 thumb 用。
            .pointerInput(interactionSource, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    var released = false
                    try {
                        while (true) {
                            // Final pass：父级滚动容器是否吃掉手势，以最终消费状态为准
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                break
                            }
                        }
                    } finally {
                        interactionSource.tryEmit(
                            if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                        )
                    }
                }
            }
            .pointerInput(valueRange, steps, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    latestStart?.invoke()
                    latestChange(offsetToValue(offset.x, size.width.toFloat(), valueRange, steps))
                    // 点击也算「落定」：点一下直接把分数设到某处时也要走松手回调（F16）
                    latestFinish?.invoke()
                }
            }
            .pointerInput(valueRange, steps, enabled) {
                if (!enabled) return@pointerInput
                var active = false
                try {
                    detectDragGestures(
                        onDragStart = { offset ->
                            active = true
                            latestStart?.invoke()
                            latestChange(offsetToValue(offset.x, size.width.toFloat(), valueRange, steps))
                        },
                        onDragEnd = {
                            active = false
                            latestFinish?.invoke()
                        },
                        onDragCancel = {
                            active = false
                            latestCancel?.invoke()
                        },
                    ) { change, _ ->
                        change.consume()
                        latestChange(offsetToValue(change.position.x, size.width.toFloat(), valueRange, steps))
                    }
                } finally {
                    // Pointer input can be cancelled by disposal or a changed range.
                    if (active) latestCancel?.invoke()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val trackWidth = maxWidth - horizontalPad * 2
        // 轨道（未选区间：半透明中性底）
        Box(
            modifier = Modifier
                .padding(horizontal = horizontalPad)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            // 已选区间：主题色渐变
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(colorScheme.primary, colorScheme.primaryContainer),
                        ),
                    ),
            )
        }
        // 圆钮：与玻璃同族的浅面 + 0.5dp 细描边 + 投影。
        // 为什么不再用「纯白芯 + 2dp 主题色环」：白块在浅色玻璃上没有材质关系，2dp 色环还是
        // M3 控件的口径；surfaceContainerHighest + 0.5dp 描边才和玻璃的边缘反光同族。
        Box(
            modifier = Modifier
                // 圆钮按「左边缘对齐轨道起点」派生：fraction = 1 时右边缘正好落在轨道终点，
                // 不会像原公式那样越出玻璃胶囊 8dp（取值数学未动，仍走 offsetToValue）。
                // A.9 #5：改造前用 .offset(x = …) —— 进度值在布局期读，拖动时每帧重测圆钮；
                // 现在布局位置固定，位移写进 graphicsLayer（绘制期），拖动只重绘。
                .graphicsLayer {
                    translationX = (horizontalPad + (trackWidth - thumbSize) * fraction).toPx()
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .size(thumbSize)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
                .border(
                    width = 0.5.dp,
                    color = colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape,
                ),
        )
    }
}

/**
 * 区间玻璃滑块（V-13）：搜索筛选面板的「评分区间 / 评分人数 / 排名区间」用。
 *
 * 为什么需要双圆钮而不是两个独立 [GlassSlider]：三个区间行共用一屏，四个滑块会让面板高度翻倍；
 * 而且「区间」这件事在视觉上就是一段被夹住的轨道（已选区段高亮），两个独立滑块表达不出这件事。
 *
 * 取值与结算全部走纯函数（[offsetToValue] / [rangeSliderGrabsStart] / [rangeSliderDrag]），
 * 本 Composable 只负责绘制与手势转发 —— 与 [GlassSlider] 的分工一致。
 *
 * @param valueRange 量程（区间行各自不同：0..10 / 0..5000 / 0..2000）
 * @param steps 离散档数（0 = 连续）
 */
@Composable
fun GlassRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..10f,
    steps: Int = 19,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = NirikoShapes.PillShape
    val trackHeight = 8.dp
    val thumbSize = 22.dp
    val horizontalPad = 14.dp
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val padPx = with(density) { horizontalPad.toPx() }
    val thumbColor = glassSliderThumbColor(
        isDark = LocalDarkTheme.current,
        lightThumb = colorScheme.surfaceContainerHighest,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) NirikoPressSpecs.SLIDER_THUMB_PRESS_SCALE else 1f,
        animationSpec = if (pressed) NirikoPressSpecs.pressIn() else NirikoPressSpecs.pressOut(),
        label = "glassRangeThumbScale",
    )
    val latestChange by rememberUpdatedState(onValueChange)
    val latestValue by rememberUpdatedState(value)
    // 抓的是哪个圆钮：整段拖动期间保持不变（手指中途换目标会让两个字一起跳）
    var draggingStart by remember { mutableStateOf(true) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .appleGlassCard(shape = shape, interactionSource = interactionSource)
            // 读屏只需知道「这是一个区间」；双圆钮的拖动无法用单一 setProgress 表达，不做假动作。
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.start,
                    range = valueRange,
                    steps = steps,
                )
            }
            .pointerInput(interactionSource, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                break
                            }
                        }
                    } finally {
                        interactionSource.tryEmit(
                            if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                        )
                    }
                }
            }
            .pointerInput(valueRange, steps, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    // 圆钮画在「内缩后的轨道」上：先减去左右内距再归一化，否则点圆钮边缘会偏一档
                    val trackPx = size.width.toFloat() - padPx * 2f
                    val grabsStart = rangeSliderGrabsStart(
                        offsetX = offset.x - padPx,
                        widthPx = trackPx,
                        current = latestValue,
                        range = valueRange,
                    )
                    val target = offsetToValue(offset.x - padPx, trackPx, valueRange, steps)
                    latestChange(rangeSliderDrag(latestValue, target, grabsStart))
                }
            }
            .pointerInput(valueRange, steps, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val trackPx = size.width.toFloat() - padPx * 2f
                        draggingStart = rangeSliderGrabsStart(
                            offsetX = offset.x - padPx,
                            widthPx = trackPx,
                            current = latestValue,
                            range = valueRange,
                        )
                    },
                ) { change, _ ->
                    change.consume()
                    val trackPx = size.width.toFloat() - padPx * 2f
                    val target = offsetToValue(change.position.x - padPx, trackPx, valueRange, steps)
                    latestChange(rangeSliderDrag(latestValue, target, draggingStart))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val startFraction = rangeSliderFraction(value.start, valueRange)
        val endFraction = rangeSliderFraction(value.endInclusive, valueRange)
        val trackWidth = maxWidth - horizontalPad * 2
        // 轨道（未选区间：半透明中性底）
        Box(
            modifier = Modifier
                .padding(horizontal = horizontalPad)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        )
        // 已选区间（两端圆钮之间）：与 [GlassSlider] 同一主题色渐变。
        // 位置/宽度仍由区间值决定，但起点用绘制期位移表达（区间变化 = 重绘一次，不重测整条轨道）。
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = (horizontalPad + (trackWidth - thumbSize) * startFraction).toPx()
                }
                .width((trackWidth - thumbSize) * (endFraction - startFraction) + thumbSize)
                .height(trackHeight)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(colorScheme.primary, colorScheme.primaryContainer),
                    ),
                ),
        )
        // 两个圆钮：与 [GlassSlider] 同一材质/按压缩放（玻璃同族浅面 + 0.5dp 描边）
        listOf(startFraction, endFraction).forEach { fraction ->
            Box(
                modifier = Modifier
                    // A.9-6：两个圆钮都用绘制期位移，拖动时只重绘、不重排
                    .graphicsLayer {
                        translationX = (horizontalPad + (trackWidth - thumbSize) * fraction).toPx()
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .size(thumbSize)
                    .shadow(elevation = 3.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(thumbColor)
                    .border(
                        width = 0.5.dp,
                        color = colorScheme.outline.copy(alpha = 0.4f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * 评分玻璃滑块：0.5 分辨率（steps=19 → 0..10 共 21 档）。
 *
 * F15 / F16（计划 §11.1）两条反馈接在这里，触发条件由 [RatingFeedbackPolicy] 判定：
 * - **跨刻度轻触感**：跨到新刻度响一次，同一刻度内抖动不重复；快速甩动一次跨多格**只响一声**
 *   （按格数连响会变成一串噪音）；
 * - **满分确认**：仅在**松手**且这一拖把自己推到满分时触发一次（放大 + 金色），
 *   因此打开编辑面板时本来就有的 10 分不会自己闪。
 *
 * 两条约束：
 * - 触感走系统轻触感（`TextHandleMove`）/ 确认触感（`Confirm`），受系统触感设置控制；
 * - 减少动态效果时**不缩放**，但保留金色这一静态视觉反馈 —— 反馈不能只剩触感
 *   （§11.1「触感不能是唯一反馈」）。
 */
@Composable
fun GlassRatingSlider(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    // 这一拖的起点：用来区分「用户刚把自己推到满分」与「恢复已保存的满分」
    var dragStartValue by remember { mutableStateOf<Float?>(null) }
    var lastTickIndex by remember { mutableIntStateOf(RatingFeedbackPolicy.tickIndex(rating)) }
    var gestureValue by remember { mutableStateOf(rating) }
    LaunchedEffect(rating) {
        if (dragStartValue == null) {
            lastTickIndex = RatingFeedbackPolicy.tickIndex(rating)
            gestureValue = rating
        }
    }
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(celebrating) {
        if (celebrating) {
            kotlinx.coroutines.delay(RatingFeedbackPolicy.CELEBRATE_DURATION_MS.toLong())
            celebrating = false
        }
    }
    val celebrateScale by animateFloatAsState(
        targetValue = if (celebrating && RatingFeedbackPolicy.shouldAnimateScale(reduceMotion)) {
            RatingFeedbackPolicy.CELEBRATE_SCALE
        } else {
            1f
        },
        // A.8：0.45/520 是第 N 套手写手感 → 走 micro() 档（0.7/700，≈217ms + 轻微过冲）。
        // 满分弹跳要「脆」但不要「晃」，micro 的回弹比原值略小，符合一次性确认反馈的调性。
        animationSpec = NirikoMotionSpecs.micro<Float>().orSnap(reduceMotion),
        label = "ratingCelebrateScale",
    )
    val perfectGold = NirikoAccents.RatingGold
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "我的评分：%.1f / 10".format(rating),
            style = MaterialTheme.typography.bodyMedium,
            // 满分确认的静态反馈：金色（减少动态效果时这是唯一的视觉反馈）
            color = if (celebrating) perfectGold else Color.Unspecified,
            modifier = Modifier.graphicsLayer {
                scaleX = celebrateScale
                scaleY = celebrateScale
            },
        )
        GlassSlider(
            value = rating,
            onValueChange = { next ->
                gestureValue = next
                // 跨刻度才响；跨多格也只响一次（把基准刻度直接更新到最后跨到的那个）
                if (RatingFeedbackPolicy.crossesTickIndex(lastTickIndex, RatingFeedbackPolicy.tickIndex(next))) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastTickIndex = RatingFeedbackPolicy.tickIndex(next)
                onRatingChange(next)
            },
            valueRange = 0f..10f,
            steps = 19,
            modifier = modifier.fillMaxWidth(),
            onValueChangeStarted = {
                dragStartValue = rating
                gestureValue = rating
                lastTickIndex = RatingFeedbackPolicy.tickIndex(rating)
            },
            onValueChangeCancelled = {
                dragStartValue = null
                lastTickIndex = RatingFeedbackPolicy.tickIndex(rating)
            },
            onValueChangeFinished = {
                val start = dragStartValue ?: return@GlassSlider
                dragStartValue = null
                if (RatingFeedbackPolicy.shouldCelebratePerfect(gestureValue, start)) {
                    celebrating = true
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            },
        )
    }
}

// ==================== 进度条 ====================

/**
 * 开关圆钮的滑动位移（纯函数，便于 JVM 单测穷举）。
 *
 * 为什么单独抽出来：位移量是「轨道宽 − 圆钮 − 两侧内缩」，改任一个尺寸都要重新算；
 * 旧实现把这算式写在 `animateDpAsState` 的 targetValue 里，测试只能靠肉眼看。
 */
internal fun glassToggleThumbOffset(trackWidth: Dp, thumbSize: Dp, pad: Dp, checked: Boolean): Dp = if (checked) (trackWidth - thumbSize - pad * 2).coerceAtLeast(0.dp) else 0.dp

/**
 * 进度值收敛到 0..1（纯函数）。NaN（视图层除零得来）按 0 处理，
 * 否则 `fillMaxWidth(NaN)` 会抛 IllegalArgumentException。
 */
internal fun progressFraction(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

/**
 * 液态玻璃进度条（替代 Material3 `LinearProgressIndicator`）。
 *
 * 为什么不直接用 M3 的：它的高度 / 圆角 / 轨道色由各调用点各自传（6dp / 8dp / 默认 4dp、
 * `shapes.small` 与默认方角混用），同屏能出现三种形态；这里固定「胶囊轨道 + 主题色填充」，
 * 圆角来自内置 Shapes 的 `Capsule()`，与玻璃开关同一几何口径。
 *
 * @param progress 0..1；越界与 NaN 会被 [progressFraction] 收敛
 * @param height 轨道高度（历史口径：收藏卡与统计坚持 6dp、详情成就条 8dp）
 * @param color 可传状态色（统计页按状态分组着色）
 */
@Composable
fun GlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
) {
    val target = progressFraction(progress)
    val reduceMotion = LocalReduceMotion.current
    val animated by animateFloatAsState(
        targetValue = target,
        // 进度填充属于「效果类」动效（颜色/尺寸占比），减少动态效果时直接跳变
        animationSpec = if (reduceMotion) snap() else NirikoMotionSpecs.effectsDefault(),
        label = "glassProgress",
    )
    val shape = remember { Capsule() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor)
            // A.9 #6：改造前子 Box 用 fillMaxWidth(animated) —— 进度值在布局期读，每帧重测整条进度条。
            // 现在尺寸固定，填充宽度在绘制期算（drawBehind 里读 State 只触发重绘，
            // 且外层 clip(Capsule) 会把矩形裁成胶囊，与原先「子 Box 同款 clip」逐像素一致）。
            .drawBehind {
                val filled = size.width * animated
                if (filled > 0f) {
                    drawRect(color = color, size = Size(filled, size.height))
                }
            },
    )
}
