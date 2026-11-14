package com.otakup.niriko.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.otakup.niriko.ui.theme.NirikoShapes

// 锚定菜单统一层（V-14 收口）。
// 改造前全站有三套菜单：详情页的 24dp 玻璃 GlassAnchoredMenu、作品库/搜索的 20dp 玻璃
// DropdownMenu、以及 20dp 普通 DropdownMenu。同一个「点一个图标弹出一列选项」的动作，
// 三处形状（20 / 24）、行高（36 / 44）、材质（玻璃 / 纯色）都不同，用户看到的是三种控件。
//
// 这一层把它们收成一个入口：**24dp 玻璃 + 从锚点缩放的进场**（缩放与透明度动画在
// GlassMenuOverlay 里，v1.2.2 已经补上退场）。调用方只提供选项。

/**
 * 统一锚定菜单（24dp 玻璃面）。
 *
 * 位置、缩放中心、退场动画都由 [GlassMenuOverlay] 负责：它会在一次布局里先量菜单尺寸、
 * 再用 [glassMenuTransformOrigin] 算出「从锚点那一角展开」的缩放中心，所以菜单不是
 * 凭空淡入，而是从被点的那个按钮「长出来」。
 *
 * @param expanded 是否展开。**不要在外面再写 `if (expanded)`** —— 退场动画需要组合树活着
 * @param anchorInRoot 锚点矩形（root 坐标），null 时什么都不画（首帧还没量到锚点）
 * @param source 玻璃采样源；默认取页面提供的 [LocalSearchGlassBackdrop]
 * @param alignRight 菜单是否右对齐锚点（左侧窄屏上的图标按钮通常右对齐）
 * @param maxMenuHeight 菜单内容的最大高度（超出后需要调用方给 [columnModifier] 加 verticalScroll）
 * @param safePadding 安全区（带纵向滚动的长菜单要避开导航栏/状态栏）
 * @param columnModifier 追加到菜单面之后的修饰符（宽度、滚动、键盘焦点等）。**在玻璃面之后**追加，
 *   所以不会影响材质；需要自己控制宽高/滚动的调用点用这个槽
 * @param requestMenuFocus 展开时把焦点移进菜单、关闭时还给 [anchorFocusRequester]（旧 GlassAnchoredMenu
 *   的键盘/读屏行为；纯触摸菜单可以不开）
 */
@Composable
fun NirikoMenu(
    expanded: Boolean,
    anchorInRoot: Rect?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alignRight: Boolean = true,
    source: Backdrop? = LocalSearchGlassBackdrop.current,
    maxMenuHeight: Dp? = null,
    safePadding: PaddingValues = PaddingValues(0.dp),
    columnModifier: Modifier = Modifier,
    requestMenuFocus: Boolean = false,
    anchorFocusRequester: FocusRequester? = null,
    restoreAnchorFocus: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassMenuOverlay(
        anchorInRoot = anchorInRoot,
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        // 调用方的 modifier 必须在前（外层）：顶栏里的调用点用「requiredSize + 负 offset」跳出
        // 56dp 父约束（见 LibraryTopBar / VerticalTopSelector），而 requiredSize 只有作为最外层
        // 才能让 GlassMenuOverlay 的 Layout 拿到整窗约束（否则 fillMaxSize 会把它压回 56dp，
        // 可视区退化成一条，菜单被夹在顶部）。
        modifier = modifier.fillMaxSize().zIndex(NIRIKO_MENU_Z_INDEX),
        // 4dp 是「贴着锚点但不重叠」的距离；24dp 圆角在顶部会吃掉一点视觉间距，故取 6dp
        gap = NIRIKO_MENU_GAP,
        maxMenuWidth = NIRIKO_MENU_MAX_WIDTH,
        maxMenuHeight = maxMenuHeight,
        alignRight = alignRight,
        safePadding = safePadding,
        requestMenuFocus = requestMenuFocus,
        anchorFocusRequester = anchorFocusRequester,
        restoreAnchorFocus = restoreAnchorFocus,
        columnModifier = Modifier
            .searchGlassSurface(NirikoShapes.SheetShape, source)
            .padding(vertical = NIRIKO_MENU_VERTICAL_PADDING)
            .then(columnModifier),
    ) {
        content()
    }
}

/**
 * 统一菜单项：行高 ≥ 44dp（触控下限内），左图标可选，危险项用 error 色。
 *
 * 行本身用主题色文字（不用纯白/纯黑）：菜单是玻璃面，浅色主题下白字会糊掉。
 */
@Composable
fun ColumnScope.NirikoMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = if (danger) colorScheme.error else colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NIRIKO_MENU_ITEM_MIN_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = NIRIKO_MENU_ITEM_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(NIRIKO_MENU_ICON_SIZE),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** 菜单项最小高度：44dp 落在 48dp 触控下限内（菜单行不叠加内距，44 是视觉行高）。 */
private val NIRIKO_MENU_ITEM_MIN_HEIGHT = 44.dp

/** 菜单左右内距。 */
private val NIRIKO_MENU_ITEM_HORIZONTAL_PADDING = 14.dp

/** 菜单图标尺寸：18dp 与列表行图标同档。 */
private val NIRIKO_MENU_ICON_SIZE = 18.dp

/** 菜单与锚点的间距。 */
private val NIRIKO_MENU_GAP: Dp = 6.dp

/** 菜单默认 zIndex：盖住同页正文与玻璃卡，但让调用方能用更大的 zIndex 覆盖（如顶栏内的选择器）。 */
private const val NIRIKO_MENU_Z_INDEX = 10f

/** 菜单最大宽度（与旧 GlassAnchoredMenu 一致，窄屏不撑满）。 */
private val NIRIKO_MENU_MAX_WIDTH: Dp = 280.dp

/** 菜单面上下内距：首末项与玻璃边缘留 6dp，选项不贴边。 */
private val NIRIKO_MENU_VERTICAL_PADDING: Dp = 6.dp
