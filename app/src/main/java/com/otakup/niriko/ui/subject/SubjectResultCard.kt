@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.subject

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.otakup.niriko.data.model.SubjectCardDisplayModel
import com.otakup.niriko.ui.theme.NirikoTheme

/**
 * 搜索结果卡片。轻量包装，直接委托给 UniversalSubjectCard。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SubjectResultCard(
    model: SubjectCardDisplayModel,
    isInCollection: Boolean = false,
    onClick: () -> Unit = {},
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    UniversalSubjectCard(
        model = model,
        isInCollection = isInCollection,
        onClick = onClick,
        sharedElementKey = sharedElementKey,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun SubjectResultCardPreview() {
    NirikoTheme {
        SubjectResultCard(
            model = SubjectCardDisplayModel(
                cover = null,
                primaryTitle = "孤独摇滚！",
                secondaryTitle = "ぼっち・ざ・ろっく！",
                typeLabel = "动画",
                ratingText = "8.6",
                secondaryInfo = "TV · 12集",
                description = "作为网络吉他手而广受好评的后藤一里…",
            ),
            isInCollection = true,
        )
    }
}
