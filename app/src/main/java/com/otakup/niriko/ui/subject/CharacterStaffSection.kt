package com.otakup.niriko.ui.subject

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.ui.components.appleGlassCard

/** 角色横向滚动列表。 */
@Composable
fun CharacterSection(
    characters: List<CharacterInfo>,
    onCharacterClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (characters.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "角色",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // key 用 index 保证绝对唯一（同一角色可能以多个身份出现）
            itemsIndexed(characters) { _, character ->
                CharacterCard(
                    character = character,
                    onClick = { onCharacterClick(character.id) },
                    onActorClick = { actorId -> onPersonClick(actorId) },
                )
            }
        }
    }
}

/** 单个角色卡片：头像 + 角色名 + 声优。 */
@Composable
private fun CharacterCard(
    character: CharacterInfo,
    onClick: () -> Unit = {},
    onActorClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(100.dp)
            .appleGlassCard(shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            // 角色头像（圆形）— 用 Bangumi 服务端裁好的 grid 正方形图，Crop 裁中心即脸部
            AsyncImage(
                model = character.imageUrl,
                contentDescription = character.nameCn ?: character.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(6.dp))
            // 角色名
            Text(
                text = character.nameCn ?: character.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            // 角色类型
            character.roleName?.let { role ->
                Text(
                    text = role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 声优（点击跳转人物详情）
            character.actors.firstOrNull()?.let { actor ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "CV: ${actor.nameCn ?: actor.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { onActorClick(actor.id) },
                )
            }
        }
    }
}

/** 制作人员横向滚动列表（与角色表一致的卡片样式）。
 * 只显示前 [displayLimit] 个主创，末尾"查看全部 N 人"卡片点击进入完整列表页。
 */
@Composable
fun StaffSection(
    staff: List<StaffInfo>,
    onPersonClick: (Long) -> Unit = {},
    onViewAllClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (staff.isEmpty()) return

    val displayLimit = 20
    val displayList = staff.take(displayLimit)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "制作人员",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // key 用 index 保证绝对唯一（同一人可能担任多个职位出现多次）
            itemsIndexed(displayList) { _, person ->
                StaffCard(
                    person = person,
                    onClick = { onPersonClick(person.id) },
                )
            }
            // 末尾"查看全部"卡片（仅当总数超过展示上限）
            if (staff.size > displayLimit) {
                item(key = "view_all") {
                    ViewAllStaffCard(count = staff.size, onClick = onViewAllClick)
                }
            }
        }
    }
}

/** 单个制作人员卡片（与角色卡片一致的样式：头像 + 人名 + 角色名）。 */
@Composable
private fun StaffCard(
    person: StaffInfo,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(100.dp)
            .appleGlassCard(shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            // 人物头像（圆形，grid 正方形图）
            AsyncImage(
                model = person.imageUrl,
                contentDescription = person.nameCn ?: person.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(6.dp))
            // 人名
            Text(
                text = person.nameCn ?: person.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            // 角色名（监督/原画/音乐等）
            person.roleName?.let { role ->
                Text(
                    text = role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** "查看全部 N 人"卡片。 */
@Composable
private fun ViewAllStaffCard(
    count: Int,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .appleGlassCard(shape = MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(8.dp),
        ) {
            Text(
                text = "查看全部",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "$count 人",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
