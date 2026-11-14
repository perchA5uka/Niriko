package com.otakup.niriko.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.animation.orSnap
import com.otakup.niriko.ui.theme.NirikoShapes

/**
 * 分段选择器（V-11 收口，全站唯一的「一组里选一个」控件）。
 *
 * ## 为什么不再给每个页面各写一份
 *
 * 改造前同一种语义有四种画法：详情页观看状态（深底 + **实心主色**胶囊 12sp）、统计页模式
 * （M3 FilterChip 一行横向滚动）、详情页评分档、发现页筛选。而当时已经存在的
 * GlassSegmentedContainer 是**零调用**的死壳 —— 于是「像玻璃一样的分段控件」写了却没接
 * （该壳已在本批删除，能力由本文件承担）。
 *
 * 这里把它做成真实现：
 *
 * - 容器：玻璃胶囊（[NirikoShapes.PillShape]）+ 4dp 内距，段与容器同族材质；
 * - 选中：主色 20% 淡底 + 1dp 主色描边。**不用实心主色**：全站实心主色只留给
 *   「必须一眼看到的那一个」状态（如底栏当前 tab），一行里每个值都可能被选中，
 *   实心绿会让整块区域变成色块墙；
 * - 等宽：每段 `weight(1f)`，文案长短不同也平分，切换时不会左右跳；
 * - 文字 13sp（[MaterialTheme.typography.labelMedium]）：改造前是硬编码 12sp，低于设计下限。
 *
 * 命中区：段高 ≥ 40dp + 容器内距 8dp = 48dp 的行高，符合 48dp 触控下限。
 *
 * @param options 段文案（等宽排列，过长省略号截断）
 * @param selectedIndex 当前选中下标；越界时不高亮任何一段（不崩溃）
 * @param onSelect 点击回调。相同下标不会重复回调 —— 重复选中没有语义
 * @param enabled 是否可交互。保存中/加载中可整体禁用（旧详情页是 `clickable(enabled = !isUpdating)`，
 *   禁用时整块压到 38% 不透明度，视觉上「点了也不会变」）
 * @param details 每段的第二行（如作品库搜索的「全部 12 / 在看 3」计数）。为 null 时只显示 [options]；
 *   给了就必须与 [options] 等长（越界按空串处理，不崩）
 * @param nestedInGlass 父级是不是一张玻璃卡。放在玻璃卡内部（如统计页放送日历）时必须传 `true`：
 *   玻璃套玻璃会让 FULL 档叠出第二层实时折射，既糊又费 —— 此时轨道退化成半透明实底 + 细描边。
 */
@Composable
fun NirikoSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    details: List<String>? = null,
    nestedInGlass: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val shape = NirikoShapes.PillShape
    // 轨道材质二选一：独立使用时是玻璃胶囊；嵌在玻璃卡里时退成「凹槽」实底，
    // 避免同屏出现两层实时折射（D1 预算），也避免两层玻璃把背后内容糊成一片。
    val trackModifier = if (nestedInGlass) {
        Modifier
            .background(colorScheme.surfaceContainerHighest.copy(alpha = TRACK_BG_ALPHA), shape)
            .border(width = 0.5.dp, color = colorScheme.outline.copy(alpha = TRACK_BORDER_ALPHA), shape)
    } else {
        Modifier.appleGlassCard(shape = shape)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(trackModifier)
            .padding(4.dp)
            // 禁用态整块压暗：分段里没有单个「按钮」的禁用色，统一压透明度最直观，
            // 也不会出现「部分段能点、部分不能」的错觉。
            .alpha(if (enabled) 1f else SEGMENT_DISABLED_ALPHA),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                // 颜色过渡走 effects 档：背景色是「效果」不是「形体」，与全站材质淡入同档。
                val background by animateColorAsState(
                    targetValue = if (selected) colorScheme.primary.copy(alpha = SEGMENT_SELECTED_BG_ALPHA) else Color.Transparent,
                    animationSpec = NirikoMotionSpecs.effectsFast<Color>().orSnap(reduceMotion),
                    label = "segmentBackground",
                )
                val borderColor by animateColorAsState(
                    targetValue = if (selected) colorScheme.primary.copy(alpha = SEGMENT_SELECTED_BORDER_ALPHA) else Color.Transparent,
                    animationSpec = NirikoMotionSpecs.effectsFast<Color>().orSnap(reduceMotion),
                    label = "segmentBorder",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SEGMENT_MIN_HEIGHT)
                        .clip(shape)
                        .background(background, shape)
                        // 描边始终占位（宽度固定 1dp，未选中透明）：否则选中瞬间段内可用宽度变化，
                        // 文字会左右抖一下。
                        .border(width = 1.dp, color = borderColor, shape = shape)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            // 反馈靠自己画的淡底 + 描边，不再叠 M3 水波（双反馈）
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { if (!selected) onSelect(index) },
                        )
                        // 两行版（主行 + 计数/说明）压到 2dp 竖向内距才能保持「容器外 48dp」不破：
                        // 作品库搜索面板把本组件塞在 58dp 的筛选行里，多出来的高度会顶破布局。
                        .padding(horizontal = 6.dp, vertical = if (details == null) 10.dp else 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (details == null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        // 带计数的分段（作品库搜索状态行）：主行是计数、第二行是状态名。
                        // 选中态统一「主色主行 + 淡底描边」，改造前是「计数字用主色 + 自己画的 16dp 圆角底」。
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) colorScheme.primary else colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                text = details.getOrNull(index).orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 选中段的底色透明度：够看出「选中」，又不至于把玻璃底完全盖掉。 */
private const val SEGMENT_SELECTED_BG_ALPHA = 0.20f

/** 选中段描边的不透明度：比底色实一档，形成清晰的边界。 */
private const val SEGMENT_SELECTED_BORDER_ALPHA = 0.70f

/** 禁用态不透明度：与站内其它禁用控件（0.38）同值。 */
private const val SEGMENT_DISABLED_ALPHA = 0.38f

/** 段最小高度：加容器 4dp 内距后总高 48dp = 触控下限。 */
private val SEGMENT_MIN_HEIGHT = 40.dp

/** 嵌在玻璃卡里时的轨道底色透明度：比表面亮一点、比背景实一点才读成「凹槽」。 */
private const val TRACK_BG_ALPHA = 0.55f

/** 嵌在玻璃卡里时的轨道描边透明度：只用来勾出边界，不抢选中段的描边。 */
private const val TRACK_BORDER_ALPHA = 0.18f
