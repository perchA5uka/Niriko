package com.otakup.niriko.ui.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.filter.FilterDimension
import com.otakup.niriko.data.filter.SelectMode
import com.otakup.niriko.data.model.search.SearchSortMode

/**
 * 高级筛选面板。
 * 根据当前类型的 FilterDimension 列表动态渲染每一行。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterPanel(
    dimensions: List<FilterDimension>,
    filterSelections: Map<String, List<String>>,
    sortMode: SearchSortMode,
    nsfwEnabled: Boolean,
    isExpanded: Boolean,
    hasActiveFilters: Boolean,
    onToggleExpand: () -> Unit,
    onSortModeSelected: (SearchSortMode) -> Unit,
    onFilterSelected: (dimensionKey: String, optionLabel: String) -> Unit,
    onNsfwToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (dimensions.isEmpty() && !nsfwEnabled) return

    Column(modifier = modifier.fillMaxWidth()) {
        // 展开/收起按钮
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggleExpand) {
                Icon(
                    if (isExpanded) Icons.Default.Tune else Icons.Default.FilterList,
                    contentDescription = "高级筛选",
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    if (isExpanded) "收起筛选" else "筛选",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            // 激活滤镜标记
            if (hasActiveFilters && !isExpanded) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "✦",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 展开面板 — 带淡入/淡出的弹簧动画
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 排序方式
                    SortModeSelector(
                        currentMode = sortMode,
                        onModeSelected = onSortModeSelected,
                    )
                    Spacer(Modifier.height(12.dp))

                    // 动态筛选维度
                    dimensions.forEach { dim ->
                        DimensionRow(
                            dimension = dim,
                            selections = filterSelections[dim.key] ?: emptyList(),
                            onOptionSelected = { onFilterSelected(dim.key, it) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // NSFW 开关
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "包含 R18 内容",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = nsfwEnabled,
                            onCheckedChange = onNsfwToggled,
                        )
                    }
                }
            }
        }
    }
}

/** 单一维度行：标题 + FlowRow 选项。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DimensionRow(
    dimension: FilterDimension,
    selections: List<String>,
    onOptionSelected: (String) -> Unit,
) {
    Text(
        dimension.title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        dimension.options.forEach { option ->
            val isSelected = option.label in selections
            FilterChip(
                selected = isSelected,
                onClick = { onOptionSelected(option.label) },
                label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** 排序方式下拉选择器。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortModeSelector(
    currentMode: SearchSortMode,
    onModeSelected: (SearchSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "排序",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { expanded = true }) {
            Text(
                currentMode.label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "展开排序选项")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SearchSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mode.label)
                            if (mode == currentMode) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}
