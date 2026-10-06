package com.otakup.niriko.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.collection.LibraryFolderSummary

/**
 * 作品库分区导航条（F09）。
 *
 * 「全部」+ 每个分区的胶囊 + 「新建分区」。
 *
 * - **点一下**分区 = 进入该分区查看（写进筛选器的 folderId）；再点当前分区 = 回到「全部」。
 *   因此不需要额外的收起按钮，「展开/收起」的语义就是「是否正在看这个分区」。
 * - **长按**分区 = 重命名 / 删除（这两个动作不该被误触，所以放在长按里）。
 * - 折叠状态（§10.1 要求持久化）由 ViewModel 落库，这里只按 [selectedFolderId] 显示展开箭头。
 *
 * 胶囊是自绘 Box 而**不是** Material 的 FilterChip：FilterChip 的点击回调与长按回调
 * 挂在同一个 Modifier 上会互相吞事件，自绘后只用一次 combinedClickable，语义清晰。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderNavBar(
    folders: List<LibraryFolderSummary>,
    selectedFolderId: Long?,
    onSelectFolder: (Long?) -> Unit,
    onCreateFolder: () -> Unit,
    onFolderLongPress: (LibraryFolderSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedKey = selectedFolderId?.toString() ?: "all"
    com.otakup.niriko.ui.common.VerticalTopSelector(
        selectedKey = selectedKey,
        options = listOf(com.otakup.niriko.ui.common.TopSelectorOption("all", "全部作品") { onSelectFolder(null) }) +
            folders.map { folder ->
                com.otakup.niriko.ui.common.TopSelectorOption(folder.id.toString(), folderDisplayName(folder.name)) {
                    onSelectFolder(folder.id)
                }
            },
        actions = listOf(com.otakup.niriko.ui.common.TopSelectorOption("create", "新建分区", onCreateFolder)) +
            folders.map { folder ->
                com.otakup.niriko.ui.common.TopSelectorOption("manage_${folder.id}", "管理 · ${folderDisplayName(folder.name)}") {
                    onFolderLongPress(folder)
                }
            },
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    memberCount: Int? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) contentColor else MaterialTheme.colorScheme.onSurface,
        )
        if (memberCount != null) {
            Text(
                text = memberCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

/**
 * 点一个分区胶囊之后应该选中谁（F09）。
 *
 * 规则只有一条但很容易写错：**点当前正在看的分区 = 回到「全部」**（收起），
 * 点别的分区 = 切过去。把它抽成纯函数是为了让「收起」这条路径有唯一实现 ——
 * 否则「全部」按钮、分区按钮、长按菜单会各自决定一次，行为迟早不一致。
 *
 * @param current 当前选中的分区（null = 全部）
 * @param clicked 被点到的分区
 * @return 新的选中分区（null = 全部）
 */
fun nextSelectedFolder(current: Long?, clicked: Long): Long? =
    if (current == clicked) null else clicked

/**
 * 分区名字是否可用于新建/改名：去空白后非空。
 *
 * 抽成纯函数是为了让「空名不创建分区」这条规则只有一处实现 —— UI 的确认按钮
 * 与 ViewModel 的落库前校验共用它，不会出现「按钮亮着但什么都没发生」。
 */
fun isUsableFolderName(name: String): Boolean = name.trim().isNotEmpty()

/**
 * 一个分区的展示名（空名兜底，避免导航条上出现一个空白胶囊）。
 *
 * 正常路径不会出现空名（新建/改名/恢复都会挡掉），这里是**兜底**：
 * 手工改过的备份或以后某次迁移写进空名时，界面也要有一个可读的名字，
 * 而不是一个点不出东西的空白胶囊。
 */
fun folderDisplayName(name: String): String = name.trim().ifBlank { "未命名分区" }
