package com.otakup.niriko.ui.subject

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otakup.niriko.ui.animation.pressTilt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.otakup.niriko.util.RailCardPolicy
import android.graphics.Bitmap
import coil.compose.AsyncImage
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.ui.components.GlassSectionCard
import com.otakup.niriko.ui.components.appleGlassCard
import top.yukonga.miuix.kmp.blur.Backdrop

/** 角色横向滚动列表。 */
@Composable
fun CharacterSection(
    characters: List<CharacterInfo>,
    onCharacterClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    blurredCover: Bitmap? = null,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
            state = rememberDetailLazyRailState("characters"),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // key 用 index 保证绝对唯一（同一角色可能以多个身份出现）
            itemsIndexed(characters) { _, character ->
                CharacterCard(
                    character = character,
                    onClick = { onCharacterClick(character.id) },
                    onActorClick = { actorId -> onPersonClick(actorId) },
                    blurredCover = blurredCover,
                    glassBackdrop = glassBackdrop,
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
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
    blurredCover: Bitmap? = null,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember(character.id) { MutableInteractionSource() }
    GlassSectionCard(
        backdrop = glassBackdrop,
        isScrolling = isScrolling,
        modifier = modifier.width(100.dp).height(staffRailHeight(labelLines = 2, extraGapDp = 2f)),
        shape = RoundedCornerShape(20.dp),
        contentPadding = 8.dp,
    ) {
    Box(
        modifier = Modifier
            .pressTilt(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            // 角色头像（圆形）— 用 Bangumi 服务端裁好的 grid 正方形图，Crop 裁中心即脸部
            // 共享元素：角色卡头像 → 角色详情页头像（key 唯一前缀避免与人物详情冲突）
            CharacterAvatar(
                imageUrl = character.imageUrl,
                contentDescription = character.nameCn ?: character.name,
                sharedElementKey = "character_avatar_${character.id}",
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
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
            // B03：角色类型与声优**都无条件占一行**（没有数据时渲染空文本 + minLines = 1）。
            // 之前两行各自被 if / let 包着：有角色名或声优的卡更高 → 横滑时整条轨道高度变化。
            Text(
                text = character.roleName.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = com.otakup.niriko.util.RailCardPolicy.LABEL_LINES,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val actor = character.actors.firstOrNull()
            Spacer(Modifier.height(2.dp))
            Text(
                text = actor?.let { "CV: ${it.nameCn ?: it.name}" }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                minLines = com.otakup.niriko.util.RailCardPolicy.LABEL_LINES,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = if (actor != null) Modifier.clickable { onActorClick(actor.id) } else Modifier,
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
    blurredCover: Bitmap? = null,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
            state = rememberDetailLazyRailState("staff"),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // key 用 index 保证绝对唯一（同一人可能担任多个职位出现多次）
            itemsIndexed(displayList) { _, person ->
                StaffCard(
                    person = person,
                    onClick = { onPersonClick(person.id) },
                    blurredCover = blurredCover,
                    glassBackdrop = glassBackdrop,
                    isScrolling = isScrolling,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
            // 末尾"查看全部"卡片（仅当总数超过展示上限）
            if (staff.size > displayLimit) {
                item(key = "view_all") {
                    ViewAllStaffCard(
                        count = staff.size,
                        onClick = onViewAllClick,
                        glassBackdrop = glassBackdrop,
                        isScrolling = isScrolling,
                    )
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
    blurredCover: Bitmap? = null,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember(person.id) { MutableInteractionSource() }
    GlassSectionCard(
        backdrop = glassBackdrop,
        isScrolling = isScrolling,
        modifier = modifier.width(100.dp).height(staffRailHeight()),
        shape = RoundedCornerShape(20.dp),
        contentPadding = 8.dp,
    ) {
    Box(
        modifier = Modifier
            .pressTilt(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            // 人物头像（圆形，grid 正方形图）— 共享元素：制作人员卡头像 → 人物详情页头像
            CharacterAvatar(
                imageUrl = person.imageUrl,
                contentDescription = person.nameCn ?: person.name,
                sharedElementKey = "person_avatar_${person.id}",
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
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
            // B03：角色名（监督/原画/音乐等）**无条件占一行**（空值渲染空文本 + minLines = 1）
            Text(
                text = person.roleName.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = com.otakup.niriko.util.RailCardPolicy.LABEL_LINES,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
    }
}

@Composable
private fun staffRailHeight(labelLines: Int = 1, extraGapDp: Float = 0f): androidx.compose.ui.unit.Dp {
    val lineHeight = MaterialTheme.typography.labelSmall.lineHeight
        .takeIf { it.value.isFinite() } ?: 16.sp
    val lineHeightDp = with(LocalDensity.current) { lineHeight.toDp().value }
    return RailCardPolicy.staffCardHeightDp(lineHeightDp, labelLines, extraGapDp).dp
}

/** "查看全部 N 人"卡片。 */
@Composable
private fun ViewAllStaffCard(
    count: Int,
    onClick: () -> Unit = {},
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
) {
    GlassSectionCard(
        backdrop = glassBackdrop,
        isScrolling = isScrolling,
        modifier = Modifier.width(100.dp).height(staffRailHeight()),
        shape = MaterialTheme.shapes.medium,
        contentPadding = 8.dp,
    ) {
        Box(Modifier.clickable(onClick = onClick)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(staffRailHeight() - 16.dp)
                    .padding(4.dp),
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
}

/**
 * 圆形头像（角色/制作人员卡片共用），支持共享元素过渡。
 * key 约定："character_avatar_{id}"（角色 → 角色详情）、"person_avatar_{id}"（人物 → 人物详情）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CharacterAvatar(
    imageUrl: String?,
    contentDescription: String?,
    sharedElementKey: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    androidx.compose.runtime.SideEffect {
        com.otakup.niriko.navigation.AvatarNavigationSeed.remember(sharedElementKey, contentDescription.orEmpty(), imageUrl)
    }
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(sharedElementKey),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = com.otakup.niriko.ui.animation.NirikoMotionSpecs.subjectCoverPathBounds(androidx.compose.ui.platform.LocalDensity.current.density),
            )
        }
    } else {
        Modifier
    }
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(sharedModifier),
        contentScale = ContentScale.Crop,
    )
}
