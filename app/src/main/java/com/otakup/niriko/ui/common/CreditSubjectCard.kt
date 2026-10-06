package com.otakup.niriko.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otakup.niriko.ui.animation.subjectCoverPresentation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.SharedSubjectCover
import com.otakup.niriko.data.remote.PersonSubjectInfo
import com.otakup.niriko.data.remote.displayTitle
import com.otakup.niriko.ui.animation.pressTilt
import com.otakup.niriko.ui.components.appleGlassCard

/**
 * 参与作品卡（F01）的**共享几何**。
 *
 * 角色页（出演作品）与人物页（参与作品）用的是同一张卡的同一组数字 ——
 * 这正是 F01 的验收要求「统一图片比例、标题高度和无封面状态」。
 * 数字集中在这里而不是散落在两个调用点：两处各写一份、日后改一处忘一处的
 * 结果就是同一张卡在两个页面高度不一样。
 *
 * 全部是 `val` + 纯函数，因此可以在 JVM 单测里直接断言（见 CreditSubjectCardTest）。
 */
object CreditSubjectCardMetrics {
    /** 卡片宽度（固定）：横滑轨道的步长。 */
    val width = 120.dp

    /** 封面比例 = 宽 / 高 = 2/3（海报竖向比例，与列表卡、作品库网格同源）。 */
    const val posterAspectRatio = 2f / 3f

    /** 卡片圆角（与人物页原样式一致）。 */
    val cornerRadius = 20.dp

    /** 卡片内边距。 */
    val contentPadding = 8.dp

    /** 横滑轨道里卡片之间的间距。 */
    val railSpacing = 10.dp

    /**
     * 文字区最小高度。
     *
     * 标题**恒定一行**（超长省略，见下方 maxLines=1），所以这里按「标题一行 + 两个标签行」
     * 留足空间：只给标题的作品不会塌成矮卡，有标签的也不会把卡片撑高。
     */
    val minTextHeight = 38.dp

    /**
     * 卡片内容的最小高度 = 封面高度 + 间距 + 文字区最小高度。
     *
     * 这是一个**纯函数**：封面高度由宽度与比例推出来（[posterHeight]），不是写死的数字 ——
     * 改宽度或比例时这里会跟着变，不会留下「比例改了但高度没改」的错位。
     */
    fun minContentHeight(): androidx.compose.ui.unit.Dp =
        posterHeight() + 6.dp + minTextHeight

    /** 封面高度 = 宽度 ÷ 比例（宽 / 高 = ratio ⇒ 高 = 宽 / ratio）。 */
    fun posterHeight(): androidx.compose.ui.unit.Dp = width / posterAspectRatio
}

/**
 * 参与作品横滑卡：封面（2:3）+ 标题 + 参与身份/角色 + 类型徽标。
 *
 * 角色详情页的「出演作品」与人物详情页的「参与作品」共用这一张卡：
 * 两处原先各有一份实现（角色页还是纵向整行大卡），于是同一部作品在两个页面
 * 尺寸、比例、标题处理都不一样。
 *
 * 刻意保留的东西（不做「顺手重构」）：
 * - **共享元素 key**：`cover_{subjectId}`，与作品详情页封面的全局约定一致；
 * - **点击语义**：`Modifier.clickable` + 项目统一的按压倾斜（`pressTilt`）；
 * - **玻璃**：`appleGlassCard`（真折射 / 静态玻璃按档位自动降级）。
 *
 * @param subtitle 参与身份（人物页「导演 / 原作…」）或角色名（角色页「饰演：…」）。空则不占位。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CreditSubjectCard(
    subject: PersonSubjectInfo,
    onClick: () -> Unit = {},
    /** 副标题（角色页传「饰演：xxx」）。null/空白时不生成这一行。 */
    subtitle: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember(subject.subjectId) { MutableInteractionSource() }
    // 共享元素：参与作品封面 → 作品详情封面（key 与全局 "cover_{subjectId}" 约定一致）
    val coverModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState("cover_${subject.subjectId}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = com.otakup.niriko.ui.animation.NirikoMotionSpecs.subjectCoverPathBounds(androidx.compose.ui.platform.LocalDensity.current.density),
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .width(CreditSubjectCardMetrics.width)
            .heightIn(min = CreditSubjectCardMetrics.minContentHeight())
            .appleGlassCard(shape = RoundedCornerShape(CreditSubjectCardMetrics.cornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    com.otakup.niriko.util.SubjectNavigationSeed.preparePreview(subject)
                    onClick()
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(CreditSubjectCardMetrics.contentPadding),
        ) {
            SharedSubjectCover(
                subjectId = subject.subjectId,
                coverUrl = subject.imageUrl,
                contentDescription = subject.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CreditSubjectCardMetrics.posterAspectRatio)
                    .then(coverModifier)
                    .subjectCoverPresentation(animatedVisibilityScope)
                    .clip(RoundedCornerShape(6.dp))
                    // 无封面 / 加载中：同一块 surfaceVariant 底 —— 空态不是「透明的洞」
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pressTilt(interactionSource),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subject.displayTitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                // 恒定一行：横滑轨道里卡片高度不随标题长度变化（F01 验收项）
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 副标题优先（角色页「饰演：xxx」），其次参与身份（人物页「导演 / 原作…」）。
            //
            // B03 更正（这一条推翻了我 §29 的判断）：当初写的是「两者都空时整行不生成」，
            // 理由是「空字符串会占掉一行高度」—— 但那是**反的**：不生成整行才会让这张卡比同轨道的
            // 其它卡矮一行，而 LazyRow 的高度取最高的一张，于是出现「横滑时轨道高度抖动」。
            // 正确做法与其余轨道一致：**无条件保留一行**（空值渲染空文本 + minLines = 1）。
            val secondary = subtitle?.takeIf { it.isNotBlank() } ?: subject.staff?.takeIf { it.isNotBlank() }
            Text(
                text = secondary.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                minLines = com.otakup.niriko.util.RailCardPolicy.LABEL_LINES,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subjectTypeLabel(subject.type),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
