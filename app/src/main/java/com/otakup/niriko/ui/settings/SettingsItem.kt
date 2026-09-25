package com.otakup.niriko.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ==================== 行度量（对齐 Kazumi SettingsTile / _TileLabel） ====================

/** 行内 leading 图标尺寸（Kazumi 24dp）。 */
private val RowIconSize = 24.dp

/** leading 图标与标题的间距（Kazumi 16dp）。 */
private val RowIconGap = 16.dp

/** 行水平内边距。 */
private val RowHorizontalPadding = 16.dp

/** 行垂直内边距（点击行 / 信息行）。 */
private val RowVerticalPadding = 12.dp

/** 尾部 chevron 图标尺寸（入口页用 24dp，组内行用 20dp）。 */
private val RowChevronSize = 20.dp

/** 数值徽标的圆角（Kazumi SettingsSliderTile 用 8）。 */
private val ValueBadgeShape = RoundedCornerShape(8.dp)

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

/** 行尾的 value 文本（bodyMedium 次要色，右对齐，最长两行）。 */
@Composable
private fun RowScope.SettingsRowValue(value: String) {
    Box(
        modifier = Modifier.weight(1f).padding(start = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 行尾的 chevron 图标（替代旧的文本箭头「›」）。 */
@Composable
private fun SettingsRowChevron() {
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(RowChevronSize),
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
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
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
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
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
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onClick = onClick,
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
        SettingsRowChevron()
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
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
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
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled && !isLoading,
                onClick = onClick,
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
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (value != null) {
            SettingsRowValue(value)
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            SettingsRowChevron()
        }
    }
}

/**
 * 数值设置行（Kazumi SettingsSliderTile 等价物）：标题 + 数值徽标 + 滑杆。
 *
 * 拖动期间只更新本地状态，松手（onValueChangeFinished）才回调 onValueChange ——
 * 避免每一帧都写 DataStore；行为与原弹窗「确定后生效」一致。
 *
 * @param valueLabel 徽标文案（用当前拖动值格式化）
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
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = ValueBadgeShape,
            ) {
                Text(
                    text = valueLabel(dragValue),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onValueChange(dragValue) },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}
