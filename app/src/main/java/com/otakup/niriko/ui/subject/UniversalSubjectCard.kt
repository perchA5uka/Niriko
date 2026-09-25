package com.otakup.niriko.ui.subject

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.otakup.niriko.data.model.SubjectCardDisplayModel
import com.otakup.niriko.ui.components.CoverThumbnail
import com.otakup.niriko.ui.components.appleGlassCard
import com.otakup.niriko.ui.components.rememberCoverTint
import com.otakup.niriko.ui.components.touchGlow
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.ui.animation.pressSpring

/**
 * 通用作品卡片。发现页、搜索页、收藏页统一使用。
 *
 * model          : 核心展示数据（封面/标题/类型/评分/简介等）
 * isInCollection : 发现页“已收藏”标记
 * status         : 收藏状态标签（收藏页）
 * watchedEpisodes: 已看集数（收藏页）
 * totalEpisodes  : 总集数/总卷数（收藏页）
 * completionTime : 游戏时长（收藏页，单位：分钟）
 * myRating       : 个人评分（收藏页）
 * personalTags   : 个人标签（收藏页）
 * updateTime     : 最近更新时间戳（收藏页）
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun UniversalSubjectCard(
    model: SubjectCardDisplayModel,
    isInCollection: Boolean = false,
    status: WatchStatus? = null,
    watchedEpisodes: Int? = null,
    totalEpisodes: Int? = null,
    completionTime: Int? = null,
    myRating: Float? = null,
    personalTags: List<String> = emptyList(),
    updateTime: Long = 0L,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    /** 阶段 E：条目 id，非空时封面优先读取用户覆盖（更换封面）。 */
    subjectId: Long? = null,
    /** 是否为收藏列表卡（用于“仅已收藏”档位下启用真玻璃）。 */
    isCollectionCard: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Ambient Tint：封面弱主色 → 玻璃环境反射（仅背景层，不影响内容）
    val ambientTint = rememberCoverTint(model.cover)

    // 静态玻璃卡片（appleGlassCard）：纯 Compose 渐变/高光/阴影，零 GPU 滤镜。
    // 列表卡片不使用 AGSL 液态玻璃——RenderEffect 每帧实时执行，列表多卡并发时
    // GPU 过载（发热/卡顿）；静态玻璃与全站 15 处列表卡一致，开销可忽略。
    // tintColor 仅作用于玻璃背景层（Ambient Tint 环境反射），绝不上色内容。
    Box(
        modifier = Modifier
            .touchGlow()
            .appleGlassCard(
                shape = RoundedCornerShape(20.dp),
                tintColor = ambientTint,
                isCollectionCard = isCollectionCard,
                interactionSource = interactionSource,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = interactionSource,
                indication = null,
            ),
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // 封面区 — 只限宽度，高度由 aspectRatio (3:4) 自动撑开
                Box(modifier = Modifier.width(80.dp)) {
                    CoverThumbnail(
                        coverUrl = model.cover,
                        subjectId = subjectId,
                        contentDescription = model.primaryTitle,
                        width = 80.dp,
                        sharedElementKey = sharedElementKey,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    // 「已收藏」标记叠加在封面右下
                    if (isInCollection) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                        ) {
                            androidx.compose.material3.Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    "已收藏",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }

                // 信息区
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    // 第 1 行：类型 + 评分
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        model.ratingText?.let {
                            Text(" ★$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        // Steam 独占标记（Bangumi 无词条的占位作品）
                        model.placeholderLabel?.let {
                            Text(
                                " $it ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }

                    // 第 2 行：主标题
                    Text(
                        model.primaryTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    // 第 3 行：副标题（原名）
                    model.secondaryTitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // 第 4 行：收藏状态标签
                    status?.let { s ->
                        Text(s.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 1.dp))
                    }

                    // 第 5 行：辅助信息（集数/平台/类型细分类）
                    model.secondaryInfo?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
                    }

                    // Steam 补充信息（游戏卡：价格/在线等）
                    model.steamInfoText?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 1.dp))
                    }

                    // 第 6 行：简介
                    model.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }

                    // ===== 收藏扩展信息 =====

                    // 第 7 行：进度条（删文字，仅保留进度条）
                    if (watchedEpisodes != null) {
                        Spacer(Modifier.height(4.dp))
                        if (totalEpisodes != null && totalEpisodes > 0) {
                            val progress = watchedEpisodes.toFloat().coerceIn(0f, totalEpisodes.toFloat()) / totalEpisodes
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                            )
                        } else if (completionTime != null && completionTime > 0) {
                            Text("游戏时长：${completionTime}分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // 第 8 行：我的评分
                    myRating?.let { rating ->
                        Text("我的评分：%.1f".format(rating), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 2.dp))
                    }

                    // 第 9 行：个人标签（最多 2 个，其余折叠）
                    if (personalTags.isNotEmpty()) {
                        Row(modifier = Modifier.padding(top = 2.dp)) {
                            personalTags.take(2).forEach { tag ->
                                Text(" $tag ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (personalTags.size > 2) {
                                Text(" +${personalTags.size - 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
