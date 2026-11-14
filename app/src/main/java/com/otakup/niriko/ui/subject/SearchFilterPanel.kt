package com.otakup.niriko.ui.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.discover.BrowseArea
import com.otakup.niriko.data.discover.BrowseFilter
import com.otakup.niriko.data.discover.BrowseSort
import com.otakup.niriko.data.discover.BrowseStatus
import com.otakup.niriko.data.discover.BrowseVersion
import com.otakup.niriko.data.discover.BrowseYear
import com.otakup.niriko.data.filter.FilterDimension
import com.otakup.niriko.data.model.search.SearchSortMode
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.animation.orSnapFinite
import com.otakup.niriko.ui.components.GlassRangeSlider
import com.otakup.niriko.ui.components.GlassToggle
import com.otakup.niriko.ui.components.NirikoFilterPill
import com.otakup.niriko.ui.components.NirikoMenu
import com.otakup.niriko.ui.components.NirikoMenuItem
import com.otakup.niriko.ui.components.NirikoTextField

// ==================== 锚定菜单宿主（V-14 收口） ====================

/**
 * 筛选面板的锚定菜单宿主状态（**一个页面一个实例**）。
 *
 * 为什么不在面板里直接弹菜单：筛选面板挂在 LazyColumn 的 item / headerContent 这类懒容器里，
 * 那里主轴约束是无限高 —— 菜单的定位与裁剪都拿不到整页尺寸（GlassMenuOverlay 用约束高度当可视区），
 * 而且菜单会跟着列表一起滚走。所以面板只「请求一次菜单」，真正的菜单由页面根层的
 * [FilterMenuHost] 渲染一次（页面级 overlay，与正文 / 顶栏同级）。
 */
@Stable
class FilterMenuHostState {
    /** 最近一次菜单请求：**收起时不清空**，清空会让菜单组合树立刻被摘掉，退场动画播不出来。 */
    var request: FilterMenuRequest? by mutableStateOf(null)
        private set

    /** 菜单是否展开（只翻这个，不销毁 [request]）。 */
    var expanded: Boolean by mutableStateOf(false)
        private set

    fun show(
        anchorInRoot: Rect,
        content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
    ) {
        request = FilterMenuRequest(anchorInRoot, content)
        expanded = true
    }

    fun dismiss() {
        expanded = false
    }
}

/** 一次菜单请求：锚点（root 坐标）+ 菜单项内容；dismiss 由宿主注入。 */
class FilterMenuRequest(
    val anchorInRoot: Rect,
    val content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
)

/**
 * 页面根层渲染的菜单宿主：整页尺寸的 overlay，作为根 Box 的最后一个子节点即可。
 *
 * [NirikoMenu] 自带 fillMaxSize 与 zIndex，所以它盖在正文与顶栏之上；
 * 收起后 [FilterMenuHostState.request] 仍保留（退场动画需要），此时宿主什么都不画。
 */
@Composable
fun FilterMenuHost(state: FilterMenuHostState, modifier: Modifier = Modifier) {
    val request = state.request ?: return
    NirikoMenu(
        expanded = state.expanded,
        anchorInRoot = request.anchorInRoot,
        onDismissRequest = state::dismiss,
        modifier = modifier,
    ) {
        request.content.invoke(this, state::dismiss)
    }
}

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
    /** V-14：页面级锚定菜单宿主；面板挂在懒容器里，自己弹不出正确的菜单定位与裁剪。 */
    menuHost: FilterMenuHostState? = null,
    modifier: Modifier = Modifier,
) {
    if (dimensions.isEmpty() && !nsfwEnabled) return

    // 展开/收起属「状态切换」：reduceMotion 时直接显示/隐藏，不做高度与透明度过渡。
    val reduceMotion = LocalReduceMotion.current
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

        // 展开面板 — 高度用 spatial 档（体型变化），透明度用 effects 档（A.8：原来 8 处手写
        // spring(StiffnessMediumLow) 是第 N 套手感，改成与全站同一组六档）
        AnimatedVisibility(
            visible = isExpanded,
            enter = panelEnter(reduceMotion),
            exit = panelExit(reduceMotion),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    // B-14：面板内选项会变（切搜索类型 → 维度条数与文案变化），容器高度原先硬切一帧。
                    // animateContentSize 只补「内容尺寸差」这一段，展开/收起仍归 AnimatedVisibility，
                    // 两者职责不重叠（否则高度会被算两次）。
                    .animateContentSize(
                        animationSpec = NirikoMotionSpecs.spatialDefault<IntSize>().orSnapFinite(reduceMotion),
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 排序方式
                    SortModeSelector(
                        currentMode = sortMode,
                        onModeSelected = onSortModeSelected,
                        menuHost = menuHost,
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
                        // V-13：M3 Switch 在玻璃面板里是唯一一块实心材质，换成玻璃开关后
                        // 与设置页 34 个开关同族（同一组件负责 Role.Switch 语义与 48dp 行兜底）。
                        GlassToggle(
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
            // 统一胶囊：改造前是 M3 FilterChip（8dp 小圆角 + 选中实心 primaryContainer），
            // 与设置页/详情页的胶囊语言不一致，也是「五副 chip 面孔」里的一副。
            NirikoFilterPill(
                text = option.label,
                selected = isSelected,
                onClick = { onOptionSelected(option.label) },
            )
        }
    }
}

/** 菜单项选中标记：NirikoMenuItem 没有 selected 态，用文字后缀保持行内对齐（不用图标，避免选中行缩进错位）。 */
private fun menuItemLabel(label: String, selected: Boolean): String = if (selected) label + "  ✓" else label

/** 排序方式下拉选择器（关键词搜索用）。 */
@Composable
private fun SortModeSelector(
    currentMode: SearchSortMode,
    onModeSelected: (SearchSortMode) -> Unit,
    menuHost: FilterMenuHostState? = null,
) {
    // V-14：菜单浮层由页面根层渲染，这里只记锚点（root 坐标，在懒容器里也是整页坐标）。
    var anchorInRoot by remember { mutableStateOf<Rect?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "排序",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInRoot()
                if (rect != anchorInRoot) anchorInRoot = rect
            },
            onClick = {
                anchorInRoot?.let { anchor ->
                    menuHost?.show(anchor) { dismiss ->
                        SearchSortMode.entries.forEach { mode ->
                            NirikoMenuItem(
                                text = menuItemLabel(mode.label, mode == currentMode),
                                onClick = {
                                    onModeSelected(mode)
                                    dismiss()
                                },
                            )
                        }
                    }
                }
            },
        ) {
            Text(
                currentMode.label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "展开排序选项")
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
    /** V-14：页面级锚定菜单宿主（同 [SearchFilterPanel]）。 */
    menuHost: FilterMenuHostState? = null,
    modifier: Modifier = Modifier,
) {
    // 同 [SearchFilterPanel]：展开/收起降级为瞬时切换。
    val reduceMotion = LocalReduceMotion.current
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
            enter = panelEnter(reduceMotion),
            exit = panelExit(reduceMotion),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    // B-14：面板内选项会变（切搜索类型 → 维度条数与文案变化），容器高度原先硬切一帧。
                    // animateContentSize 只补「内容尺寸差」这一段，展开/收起仍归 AnimatedVisibility，
                    // 两者职责不重叠（否则高度会被算两次）。
                    .animateContentSize(
                        animationSpec = NirikoMotionSpecs.spatialDefault<IntSize>().orSnapFinite(reduceMotion),
                    ),
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

                    SortRow(current = filter.sort, onSort = onSort, menuHost = menuHost)
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
                    YearRow(filter = filter, nowYear = nowYear, onYear = onYear, menuHost = menuHost)
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
                            NirikoFilterPill(
                                text = tag,
                                selected = tag in filter.tags,
                                onClick = { onToggleTag(tag) },
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
                        value = (filter.minRatingCount ?: 0).toFloat()..(filter.maxRatingCount ?: RATING_COUNT_SLIDER_MAX.toInt()).toFloat(),
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
                        value = (filter.rankFrom ?: 0).toFloat()..(filter.rankTo ?: RANK_SLIDER_MAX.toInt()).toFloat(),
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
@Composable
private fun SortRow(
    current: BrowseSort,
    onSort: (BrowseSort) -> Unit,
    menuHost: FilterMenuHostState? = null,
) {
    var anchorInRoot by remember { mutableStateOf<Rect?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text("排序", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        TextButton(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInRoot()
                if (rect != anchorInRoot) anchorInRoot = rect
            },
            onClick = {
                anchorInRoot?.let { anchor ->
                    menuHost?.show(anchor) { dismiss ->
                        BrowseSort.entries.forEach { mode ->
                            NirikoMenuItem(
                                text = menuItemLabel(mode.label, mode == current),
                                onClick = {
                                    onSort(mode)
                                    dismiss()
                                },
                            )
                        }
                    }
                }
            },
        ) {
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
    }
}

/** 年份维度：单选下拉（当前年 … 2001 + 「2000以前」）。 */
@Composable
private fun YearRow(
    filter: BrowseFilter,
    nowYear: Int,
    onYear: (BrowseYear?) -> Unit,
    menuHost: FilterMenuHostState? = null,
) {
    var anchorInRoot by remember { mutableStateOf<Rect?>(null) }
    val options = BrowseFilter.yearOptions(nowYear)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text("年份", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        TextButton(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInRoot()
                if (rect != anchorInRoot) anchorInRoot = rect
            },
            onClick = {
                anchorInRoot?.let { anchor ->
                    menuHost?.show(anchor) { dismiss ->
                        NirikoMenuItem(
                            text = menuItemLabel("不限", filter.year == null),
                            onClick = {
                                onYear(null)
                                dismiss()
                            },
                        )
                        options.forEach { option ->
                            NirikoMenuItem(
                                text = menuItemLabel(option.label, option == filter.year),
                                onClick = {
                                    onYear(option)
                                    dismiss()
                                },
                            )
                        }
                    }
                }
            },
        ) {
            Text(filter.year?.label ?: "不限", style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "展开年份选项")
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
            NirikoTextField(
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
            NirikoFilterPill(
                text = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
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
        // V-13：M3 RangeSlider → 玻璃双圆钮区间滑块（同一份「淡底 + 主题色渐变已选区」语言）。
        // 取值/抓取/相撞结算全在 GlassRangeSliderMath.kt 的纯函数里，有单测。
        GlassRangeSlider(
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
        GlassToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 筛选面板的展开进场：高度走 spatial 档、透明度走 effects 档。
 *
 * 为什么抽成函数而不是各写一份 if：本文件有两个面板（关键词搜索 / 历史排名），改造前各自抄了
 * 4 处 spring(StiffnessMediumLow)（合计 8 处 = A.8 里「同一屏六个时长并存」的一处来源）。
 */
private fun panelEnter(reduceMotion: Boolean): EnterTransition = if (reduceMotion) {
    EnterTransition.None
} else {
    fadeIn(animationSpec = NirikoMotionSpecs.effectsFast<Float>()) +
        expandVertically(animationSpec = NirikoMotionSpecs.spatialDefault<IntSize>())
}

/** 与 [panelEnter] 配对的收起；收起与展开同档（两侧同一组六档，不另调时长）。 */
private fun panelExit(reduceMotion: Boolean): ExitTransition = if (reduceMotion) {
    ExitTransition.None
} else {
    fadeOut(animationSpec = NirikoMotionSpecs.effectsFast<Float>()) +
        shrinkVertically(animationSpec = NirikoMotionSpecs.spatialDefault<IntSize>())
}
