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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
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
import com.otakup.niriko.data.discover.BrowseArea
import com.otakup.niriko.data.discover.BrowseFilter
import com.otakup.niriko.data.discover.BrowseSort
import com.otakup.niriko.data.discover.BrowseStatus
import com.otakup.niriko.data.discover.BrowseVersion
import com.otakup.niriko.data.discover.BrowseYear
import com.otakup.niriko.data.filter.FilterDimension
import com.otakup.niriko.data.model.search.SearchSortMode

/**
 * 高级筛选面板（**关键词搜索**用）。
 * 根据当前类型的 FilterDimension 列表动态渲染每一行。
 *
 * 历史排名的筛选器是另一个组件 [BrowseFilterPanel]（9 维度 + 增强），
 * 两者共用本文件的动画/卡片/行样式，但状态来源不同（搜索走 filterSelections，
 * 历史排名走 [BrowseFilter]）。
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

/** 排序方式下拉选择器（关键词搜索用）。 */
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

// ==================== 历史排名筛选器（第 6 轮 §6.3） ====================

/** 评分人数滑块的上界（大于等于它的都算「不限上界」）。 */
private const val RATING_COUNT_SLIDER_MAX = 5000f

/** 排名滑块的上界（大于等于它的都算「不限上界」）。 */
private const val RANK_SLIDER_MAX = 2000f

/**
 * 「历史排名」的筛选器：Bangumi-master 找条目的 **9 个维度** + 我们保留的三项增强。
 *
 * - 地区 / 版本 / 季度 / 状态 → 单选 chips（落 filter.tag / filter.air_date）；
 * - 年份 → 下拉（含「2000以前」，落单边 air_date）；
 * - 类型 → 46 词表多选且（落 filter.tag）；
 * - 制作 → 输入公司名（落 filter.tag，用别名变体多请求合并；标注「按标签匹配」）；
 * - 排序 → 下拉（排名/上映时间/评分人数/评分→官方 sort；随机/名称 → 本地）；
 * - 收藏 → 开关（本地过滤已收藏）；
 * - 增强：评分区间 / 排名区间 / 评分人数 / NSFW（规格 §12 已确认保留）。
 *
 * **改动即查询**：每个回调直达 ViewModel 的 setBrowse* 方法，后者统一走 refreshForActiveQuery()。
 * 状态维度额外标注「按开播日推算」（官方没有放送状态字段）。
 *
 * @param resultCount 当前列表条数（「共 N 条」）。
 * @param poolCount 筛选后的候选池条数（「候选池 N 条」）。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseFilterPanel(
    filter: BrowseFilter,
    resultCount: Int,
    poolCount: Int,
    isExpanded: Boolean,
    nowYear: Int,
    onToggleExpand: () -> Unit,
    onArea: (BrowseArea?) -> Unit,
    onVersion: (BrowseVersion?) -> Unit,
    onYear: (BrowseYear?) -> Unit,
    onQuarter: (Int?) -> Unit,
    onStatus: (BrowseStatus?) -> Unit,
    onToggleTag: (String) -> Unit,
    onStudio: (String?) -> Unit,
    onSort: (BrowseSort) -> Unit,
    onHideCollected: (Boolean) -> Unit,
    onRatingRange: (Float?, Float?) -> Unit,
    onRatingCountRange: (Int?, Int?) -> Unit,
    onRankRange: (Int?, Int?) -> Unit,
    onNsfw: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // —— 工具条：筛选开关 + 已筛选标记 + 计数 ——
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggleExpand) {
                Icon(
                    if (isExpanded) Icons.Default.Tune else Icons.Default.FilterList,
                    contentDescription = "筛选",
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(if (isExpanded) "收起筛选" else "筛选", style = MaterialTheme.typography.labelLarge)
            }
            if (filter.hasAnyCondition) {
                Text(
                    "✦",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "共 " + resultCount + " 条 / 候选池 " + poolCount + " 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
                    // —— 条件摘要（规格 §6.3 要求保留的筛选摘要行） ——
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = filter.summary().ifBlank { "没有附加条件" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClear) {
                            Text("清空", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    SortRow(current = filter.sort, onSort = onSort)
                    ChoiceChipsRow(
                        title = "地区",
                        labels = listOf("不限") + BrowseArea.entries.map { it.label },
                        selectedIndex = BrowseArea.entries.indexOf(filter.area) + 1,
                        onSelect = { index -> onArea(BrowseArea.entries.getOrNull(index - 1)) },
                    )
                    ChoiceChipsRow(
                        title = "版本",
                        labels = listOf("不限") + BrowseVersion.entries.map { it.label },
                        selectedIndex = BrowseVersion.entries.indexOf(filter.version) + 1,
                        onSelect = { index -> onVersion(BrowseVersion.entries.getOrNull(index - 1)) },
                    )
                    YearRow(filter = filter, nowYear = nowYear, onYear = onYear)
                    ChoiceChipsRow(
                        title = "季度",
                        note = "需要年份",
                        labels = listOf("不限") + BrowseFilter.QUARTERS.map { it.toString() + "月" },
                        selectedIndex = BrowseFilter.QUARTERS.indexOf(filter.quarter) + 1,
                        onSelect = { index -> onQuarter(BrowseFilter.QUARTERS.getOrNull(index - 1)) },
                    )
                    ChoiceChipsRow(
                        title = "状态",
                        note = BrowseFilter.STATUS_NOTE,
                        labels = listOf("不限") + BrowseStatus.entries.map { it.label },
                        selectedIndex = BrowseStatus.entries.indexOf(filter.status) + 1,
                        onSelect = { index -> onStatus(BrowseStatus.entries.getOrNull(index - 1)) },
                    )

                    // —— 类型：46 词表，多选（AND 语义） ——
                    Text(
                        "类型（多选且）",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BrowseFilter.CONTENT_TAGS.forEach { tag ->
                            FilterChip(
                                selected = tag in filter.tags,
                                onClick = { onToggleTag(tag) },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    StudioRow(filter = filter, onStudio = onStudio)
                    ToggleRow(
                        title = "隐藏已收藏",
                        checked = filter.hideCollected,
                        onCheckedChange = onHideCollected,
                    )

                    // —— 我们的增强项 ——
                    RangeRow(
                        title = "评分区间",
                        value = (filter.minRating ?: 0f)..(filter.maxRating ?: 10f),
                        valueRange = 0f..10f,
                        steps = 19,
                        format = { value -> (kotlin.math.round(value * 10f) / 10f).toString() },
                        onChange = { range ->
                            onRatingRange(
                                range.start.takeIf { it > 0f },
                                range.endInclusive.takeIf { it < 10f },
                            )
                        },
                        onClear = { onRatingRange(null, null) },
                    )
                    RangeRow(
                        title = "评分人数",
                        value = (filter.minRatingCount ?: 0).toFloat()..
                            (filter.maxRatingCount ?: RATING_COUNT_SLIDER_MAX.toInt()).toFloat(),
                        valueRange = 0f..RATING_COUNT_SLIDER_MAX,
                        steps = 24,
                        format = { value -> value.toInt().toString() },
                        onChange = { range ->
                            onRatingCountRange(
                                range.start.toInt().takeIf { it > 0 },
                                range.endInclusive.toInt()
                                    .takeIf { it < RATING_COUNT_SLIDER_MAX.toInt() },
                            )
                        },
                        onClear = { onRatingCountRange(null, null) },
                    )
                    RangeRow(
                        title = "排名区间",
                        value = (filter.rankFrom ?: 0).toFloat()..
                            (filter.rankTo ?: RANK_SLIDER_MAX.toInt()).toFloat(),
                        valueRange = 0f..RANK_SLIDER_MAX,
                        steps = 19,
                        format = { value -> value.toInt().toString() },
                        onChange = { range ->
                            onRankRange(
                                range.start.toInt().takeIf { it > 0 },
                                range.endInclusive.toInt()
                                    .takeIf { it < RANK_SLIDER_MAX.toInt() },
                            )
                        },
                        onClear = { onRankRange(null, null) },
                    )

                    ToggleRow(
                        title = "包含 R18 内容",
                        checked = filter.nsfw,
                        onCheckedChange = onNsfw,
                    )
                }
            }
        }
    }
}

/** 排序维度（9 个维度之一）：排名 / 上映时间 / 评分人数 / 评分 / 随机 / 名称。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortRow(current: BrowseSort, onSort: (BrowseSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text("排序", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { expanded = true }) {
            Text(current.label, style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "展开排序选项")
        }
        if (current.isLocalOnly) {
            Text(
                "（本地）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BrowseSort.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mode.label)
                            if (mode == current) {
                                Spacer(Modifier.width(8.dp))
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    onClick = {
                        onSort(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 年份维度：单选下拉（当前年 … 2001 + 「2000以前」）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearRow(filter: BrowseFilter, nowYear: Int, onYear: (BrowseYear?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = BrowseFilter.yearOptions(nowYear)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text("年份", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { expanded = true }) {
            Text(filter.year?.label ?: "不限", style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "展开年份选项")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("不限") },
                onClick = {
                    onYear(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(option.label)
                            if (option == filter.year) {
                                Spacer(Modifier.width(8.dp))
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    onClick = {
                        onYear(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 制作维度：公司名当标签用（变体展开后多请求合并），因此标注「按标签匹配」。 */
@Composable
private fun StudioRow(filter: BrowseFilter, onStudio: (String?) -> Unit) {
    var input by remember { mutableStateOf(filter.studio.orEmpty()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("制作", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text(
                BrowseFilter.STUDIO_NOTE,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                label = { Text("公司名（规范名，如 MAPPA）", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = input != filter.studio.orEmpty(),
                onClick = { onStudio(input) },
            ) { Text("应用") }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** 单选 chips 一行（index 0 = 「不限」，其余按 options 顺序）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChipsRow(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    note: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        if (note != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** 区间行：标题 + 当前区间 + 「不限」，下面是滑杆。 */
@Composable
private fun RangeRow(
    title: String,
    value: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                format(value.start) + " – " + format(value.endInclusive),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClear) { Text("不限", style = MaterialTheme.typography.labelSmall) }
        }
        RangeSlider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

/** 开关行。 */
@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
