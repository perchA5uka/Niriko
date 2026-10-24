package com.otakup.niriko.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.theme.NirikoShapes
import com.otakup.niriko.util.RatingFeedbackPolicy

/**
 * 液态玻璃控件（开关 / 滑块 / 分段容器）。
 *
 * 材质：一律复用 [appleGlassCard]（无卡片 backdrop 时自动退化为静态玻璃，含发光边 / 内厚度）。
 * 形状：容器类圆角仍来自 [NirikoShapes] 令牌；胶囊类控件（开关 / 进度条）自 B6 起用内置
 * [com.kyant.shapes.Capsule]（连续曲率胶囊，与 kyant 上游控件同一几何口径）。
 *
 * 本轮（B5）只替换**视觉层**（轨道 / 圆钮 / 高光的绘制方式，参考 Kyant0/AndroidLiquidGlass 的
 * 控件样例）：开关的轨道选中时着色、圆钮改白芯 + 投影；滑块的已选区间改主题色渐变、
 * 圆钮改白芯 + 主题色环 + 投影。手势判定与取值数学（[offsetToValue]）保持原样。
 */

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
    val thumbOffset by animateDpAsState(
        // 圆钮滑动区间 = 内缩 pad 后的轨道区域，两端各留 pad（原 checked 端会越出玻璃胶囊外沿）。
        targetValue = glassToggleThumbOffset(trackWidth, thumbSize, pad, checked),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "glassToggleThumb",
    )
    // 轨道着色：选中 → 强调色；未选中 → 中性玻璃上的半透明底。
    val trackColor by animateColorAsState(
        targetValue = if (checked) colorScheme.primary else colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        label = "glassToggleTrack",
    )
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            // disabled 必须看得见差异（M3 Switch 自带该反馈，自绘件要显式给）
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .appleGlassCard(shape = shape)
            // 换掉 M3 Switch 后必须自己补 Role.Switch + 开关状态，否则 TalkBack 只剩「按钮」
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
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
        // 圆钮：白芯 + 投影
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {}
    }
}

/** 状态选择 / 分段容器：玻璃底（复用 appleGlassCard），child 由调用方自行排布。 */
@Composable
fun GlassSegmentedContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .appleGlassCard(shape = NirikoShapes.PillShape)
            .padding(4.dp),
    ) {
        content()
    }
}

/**
 * 液态玻璃滑块：玻璃轨道 + 主题色渐变已选区 + 白芯圆钮，支持半步进（[steps]）。
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
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinish by rememberUpdatedState(onValueChangeFinished)
    val latestStart by rememberUpdatedState(onValueChangeStarted)
    val latestCancel by rememberUpdatedState(onValueChangeCancelled)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .appleGlassCard(shape = shape)
            .pointerInput(valueRange, steps) {
                detectTapGestures { offset ->
                    latestStart?.invoke()
                    latestChange(offsetToValue(offset.x, size.width.toFloat(), valueRange, steps))
                    // 点击也算「落定」：点一下直接把分数设到某处时也要走松手回调（F16）
                    latestFinish?.invoke()
                }
            }
            .pointerInput(valueRange, steps) {
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
        // 圆钮：白芯 + 主题色环 + 投影
        Box(
            modifier = Modifier
                // 圆钮按「左边缘对齐轨道起点」派生：fraction = 1 时右边缘正好落在轨道终点，
                // 不会像原公式那样越出玻璃胶囊 8dp（取值数学未动，仍走 offsetToValue）。
                .offset(x = horizontalPad + (trackWidth - thumbSize) * fraction)
                .size(thumbSize)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(width = 2.dp, color = colorScheme.primary, shape = CircleShape),
        )
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
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "ratingCelebrateScale",
    )
    val perfectGold = Color(0xFFF59E0B)
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
internal fun glassToggleThumbOffset(trackWidth: Dp, thumbSize: Dp, pad: Dp, checked: Boolean): Dp =
    if (checked) (trackWidth - thumbSize - pad * 2).coerceAtLeast(0.dp) else 0.dp

/**
 * 进度值收敛到 0..1（纯函数）。NaN（视图层除零得来）按 0 处理，
 * 否则 `fillMaxWidth(NaN)` 会抛 IllegalArgumentException。
 */
internal fun progressFraction(progress: Float): Float =
    if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

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
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(shape)
                .background(color),
        )
    }
}

