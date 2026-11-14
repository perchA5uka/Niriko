package com.otakup.niriko.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.GlassSlider
import com.otakup.niriko.ui.components.GlassToggle
import com.otakup.niriko.ui.components.nirikoRowIndication
import com.otakup.niriko.ui.theme.NirikoShapes

// ==================== 行度量（对齐 Kazumi SettingsTile / _TileLabel） ====================

/** 行内 leading 图标尺寸（Kazumi 24dp）。 */
private val RowIconSize = 24.dp

/** leading 图标与标题的间距（Kazumi 16dp）。 */
private val RowIconGap = 16.dp

/** 行水平内边距。 */
private val RowHorizontalPadding = 16.dp

/** 行垂直内边距（点击行 / 信息行）。 */
private val RowVerticalPadding = 12.dp

/** 禁用行的整体不透明度（V-09 / F09）：0.38 是 M3 的 disabled content 档位。 */
private const val DisabledRowAlpha = 0.38f

/**
 * 行最小高度（V-07）：单行 56dp、带说明 72dp，常量见 [SettingsLayout]。
 * 此前高度靠内边距和内容自己撑：单行 48dp、两行 70dp，同屏行高参差。
 */
private fun rowMinHeight(hasDescription: Boolean) = if (hasDescription) SettingsRowTwoLineMinHeight else SettingsRowMinHeight

// ==================== 公共解剖 ====================

/**
 * 设置行统一标签（Kazumi SettingsTile 的 _TileLabel 等价物）：
 * leading 图标 24dp（onSurfaceVariant）+ 标题 bodyLarge + 可选描述 bodySmall（次要色）。
 */
@Composable
private fun SettingsRowLabel(
    icon: ImageVector?,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(RowIconSize),
            )
            Spacer(Modifier.width(RowIconGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 行尾的 value 文本（V-09）：右对齐、最长两行。
 *
 * 刻意不抢 weight：标题用 weight(1f) 吃剩余宽度，值按内容宽度贴右 ——
 * 与整改前的裸文字行为一致，长标题不会被「限定权重」的值挤成两行。
 */
@Composable
private fun RowScope.SettingsRowValue(value: String) {
    SettingsValueText(value = value, modifier = Modifier.padding(start = 8.dp))
}

/**
 * 「当前值」的唯一排版（V-09）：labelLarge + onSurfaceVariant 的裸文字。
 *
 * 整改前「当前值」有两种长相——选择行是裸文字，滑块行是一枚 8dp 圆角
 * secondaryContainer 徽标，同一页里两种值视觉权重不同，读起来像两种控件。
 * 现在只留裸文字（徽标变体已删），滑块行拖动时同样只换文字，不再闪一块底色。
 */
@Composable
private fun SettingsValueText(value: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = value,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

// ==================== 行组件 ====================

/**
 * 开关设置行。整行可点（点行 = 切换开关），与 Kazumi 的 switch tile 行为一致。
 *
 * @param icon 必填的 leading 图标
 * @param description 标题下方的说明（bodySmall）
 */
@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = rememberRowInteractionSource()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight(description != null))
            // M1：去掉 M3 矩形水波，改用整行圆角覆盖（按压进度由 nirikoRowIndication 驱动）
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            )
            .nirikoRowIndication(
                interactionSource = interactionSource,
                cornerRadius = NirikoShapes.RowInner,
                enabled = enabled,
            )
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowLabel(
            icon = icon,
            title = title,
            description = description,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        // B6：设置行是 34 个开关的唯一入口 —— 在这里换掉 M3 Switch，全站开关材质即统一。
        // checked 仍由调用方（设置流）传入，首帧策略不动（批次 A 的 X3 种子依赖它）。
        GlassToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * 点击选择设置行：leading 图标 + 标题 + 可选描述 + 右侧当前值 + 可选尾部控件 + chevron 图标。
 *
 * @param trailing 额外的尾部控件（如「清除」按钮）；chevron 始终在最后
 */
@Composable
fun SettingsPickerRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    description: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = rememberRowInteractionSource()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight(description != null))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .nirikoRowIndication(
                interactionSource = interactionSource,
                cornerRadius = NirikoShapes.RowInner,
                enabled = enabled,
            )
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowLabel(
            icon = icon,
            title = title,
            description = description,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            SettingsRowValue(value)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        Spacer(Modifier.width(8.dp))
        SettingsChevron()
    }
}

/**
 * 就地单选行（Kazumi SettingsTile.radioTile 等价物）。
 * 整行可点即选中；尾部 Radio 只做展示，避免双重点击区域。
 */
@Composable
fun SettingsRadioRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = rememberRowInteractionSource()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight(description != null))
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .nirikoRowIndication(
                interactionSource = interactionSource,
                cornerRadius = NirikoShapes.RowInner,
                enabled = enabled,
            )
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowLabel(
            icon = icon,
            title = title,
            description = description,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}

/**
 * 信息行（不可点击）：leading 图标 + 标题 + 可选描述 + 可选尾部值。
 * 长段落说明放进 description（随标题换行），短状态值放 value（右对齐）。
 */
@Composable
fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight(description != null))
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowLabel(
            icon = icon,
            title = title,
            description = description,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            SettingsRowValue(value)
        }
    }
}

/**
 * 可点击动作行（原 SettingsActionRow 与 SettingsIconActionRow 合并为一种）：
 * leading 图标 + 标题 + 可选描述 + 可选尾部值 + 可选 chevron / 进行中指示。
 *
 * @param showChevron 尾部是否显示 chevron（导航/弹窗类为 true；导出、导入、刷新等纯动作为 false）
 */
@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    val interactionSource = rememberRowInteractionSource()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight(description != null))
            // F09：禁用态此前和可用态一模一样（只少了一个水波），用户不知道这行点不了。
            // 整行压到 0.38 的 content alpha + enabled 透传给 clickable，视觉与行为同时表达禁用。
            .alpha(if (enabled) 1f else DisabledRowAlpha)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick,
            )
            .nirikoRowIndication(
                interactionSource = interactionSource,
                cornerRadius = NirikoShapes.RowInner,
                enabled = enabled && !isLoading,
            )
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowLabel(
            icon = icon,
            title = title,
            description = description,
            modifier = Modifier.weight(1f),
        )
        if (isLoading) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, gapSize = 0.dp)
        } else if (value != null) {
            SettingsRowValue(value)
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            SettingsChevron()
        }
    }
}

/**
 * 数值设置行（Kazumi SettingsSliderTile 等价物）：标题 + 当前值 + 滑杆。
 *
 * 拖动期间只更新本地状态，松手（onValueChangeFinished）才回调 onValueChange ——
 * 避免每一帧都写 DataStore；行为与原弹窗「确定后生效」一致。
 *
 * 滑杆是 [GlassSlider]（V-13）：与同组开关行同一套玻璃材质，不再是 M3 的细轨实心点。
 *
 * @param valueLabel 当前值文案（用当前拖动值格式化）
 */
@Composable
fun SettingsSliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: (Float) -> String,
    enabled: Boolean = true,
) {
    var dragValue by remember(value) { mutableStateOf(value) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsRowLabel(
                icon = icon,
                title = title,
                description = description,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            // V-09：原来是 8dp 圆角 secondaryContainer 徽标，现在与选择行同一套裸文字。
            SettingsValueText(valueLabel(dragValue))
        }
        Spacer(Modifier.height(4.dp))
        // V-13：这里是设置页最后一处 M3 Slider。同组的开关行早就是玻璃开关（GlassToggle），
        // 只有这一行还是 M3 的细轨 + 实心圆点 —— 同一张组卡里两种材质，用户能一眼看出「没做完」。
        // 命中区：GlassSlider 视觉高 34dp，外层 heightIn(min = 48dp) 给足竖向点击范围
        // （M3 Slider 自带 48dp，自绘件必须显式补，否则这一行比别的行难点）。
        GlassSlider(
            value = dragValue,
            onValueChange = { dragValue = it },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.heightIn(min = SettingsRowMinHeight),
            enabled = enabled,
            onValueChangeFinished = { onValueChange(dragValue) },
        )
    }
}
