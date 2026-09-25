package com.otakup.niriko.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 液态玻璃开关（P3 收藏编辑 sheet）：玻璃胶囊底 + 圆钮。
 * 复用 appleGlassCard（无卡片 backdrop 时自动退化为静态玻璃，含发光边/内厚度）。
 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val trackWidth = 52.dp
    val trackHeight = 30.dp
    val thumbSize = 24.dp
    val pad = 3.dp
    val shape = RoundedCornerShape(trackHeight / 2)
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - pad else pad,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "glassToggleThumb",
    )
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .appleGlassCard(shape = shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(pad),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {}
    }
}

/** 状态选择/分段容器：玻璃底（复用 appleGlassCard），child 由调用方自行排布。 */
@Composable
fun GlassSegmentedContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .appleGlassCard(shape = RoundedCornerShape(999.dp))
            .padding(4.dp),
    ) {
        content()
    }
}

/** 液态玻璃滑块：玻璃轨道 + 圆钮，支持半步进（steps）。 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..10f,
    steps: Int = 19,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val trackHeight = 8.dp
    val thumbSize = 22.dp
    val horizontalPad = 14.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .appleGlassCard(shape = shape)
            .pointerInput(valueRange, steps) {
                detectTapGestures { offset ->
                    onValueChange(offsetToValue(offset.x, size.width.toFloat(), valueRange, steps))
                }
            }
            .pointerInput(valueRange, steps) {
                detectDragGestures { change, _ ->
                    onValueChange(offsetToValue(change.position.x, size.width.toFloat(), valueRange, steps))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val trackWidth = maxWidth - horizontalPad * 2
        // 轨道
        Box(
            modifier = Modifier
                .padding(horizontal = horizontalPad)
                .fillMaxWidth()
                .height(trackHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(trackHeight)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        // 圆钮
        Box(
            modifier = Modifier
                .offset(x = horizontalPad + trackWidth * fraction)
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** 评分玻璃滑块：0.5 分辨率（steps=19 → 0..10 共 21 档）。 */
@Composable
fun GlassRatingSlider(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "我的评分：%.1f / 10".format(rating),
            style = MaterialTheme.typography.bodyMedium,
        )
        GlassSlider(
            value = rating,
            onValueChange = onRatingChange,
            valueRange = 0f..10f,
            steps = 19,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/** 滑块位置 → 离散值（steps = 端点间点数，如 0..10 的 0.5 步进 → steps=19）。 */
private fun offsetToValue(
    xPx: Float,
    widthPx: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    if (widthPx <= 0f) return range.start
    val fraction = (xPx / widthPx).coerceIn(0f, 1f)
    val span = range.endInclusive - range.start
    val raw = range.start + fraction * span
    if (steps <= 0) return raw
    val step = span / (steps + 1)
    val snapped = (raw / step).roundToInt() * step
    return snapped.coerceIn(range.start, range.endInclusive)
}
