package com.otakup.niriko.ui.animation

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.LinearEasing

/**
 * M3 动效规格（P0-B）：spatial 用于位置 / 尺寸 / 形状变化，effects 用于透明度 / 颜色变化。
 *
 * 为什么绕开 material3 的 MotionScheme：本工程 material3 = 1.4.0，其中
 * `androidx.compose.material3.MotionScheme` 与 `MaterialTheme.motionScheme` 都是 `internal`，
 * 外部模块引用会直接编译失败：
 *   "Cannot access 'val motionScheme: MotionScheme': it is internal in 'androidx.compose.material3.MaterialTheme'"
 * 因此这里直接复刻 framework 的 StandardMotionTokens 六档弹簧参数，取值来自 material3 1.4.0
 * 字节码 `MotionScheme$StandardMotionSchemeImpl.<clinit>`（探针 javap 实测）：
 *   defaultSpatial damping 0.9 / stiffness 700   defaultEffects 1.0 / 1600
 *   fastSpatial    0.9 / 1400                    fastEffects    1.0 / 3800
 *   slowSpatial    0.9 / 300                     slowEffects    1.0 / 800
 * 一旦 material3 公开 MotionScheme（1.5+），只需把这里的实现改为由 MaterialTheme.motionScheme
 * 取值即可，调用点（spatial* / effects* 六个方法名）无需改动。
 *
 * 注意：泛型是必须的——slideInHorizontally 需要 FiniteAnimationSpec<IntOffset>，
 * scaleIn / fadeIn 需要 <Float>，同一个规格必须能服务多种类型。
 */
object NirikoMotionSpecs {

    // One finite timeline for page effects and Hero bounds. Never add a post-flight expansion.
    // Kazumi-style Hero shuttle: fast launch, smooth settle, still seekable by Navigation.
    const val SUBJECT_COVER_DURATION_MS = 200

    // Bounded deceleration leaves spatial response at the end of a predictive gesture.
    val subjectCoverExpandEasing = Easing { fraction ->
        val t = fraction.coerceIn(0f, 1f)
        t + 0.75f * t * (1f - t)
    }
    val subjectCoverContractEasing = Easing { fraction ->
        1f - subjectCoverExpandEasing.transform(1f - fraction.coerceIn(0f, 1f))
    }

    fun <T> subjectCoverSeek(contracting: Boolean = false): FiniteAnimationSpec<T> =
        tween(durationMillis = SUBJECT_COVER_DURATION_MS,
            easing = if (contracting) subjectCoverContractEasing else subjectCoverExpandEasing)

    @OptIn(ExperimentalSharedTransitionApi::class)
    val subjectCoverBounds = subjectCoverPathBounds()

    @OptIn(ExperimentalSharedTransitionApi::class)
    fun subjectCoverPathBounds(density: Float = 1f) = BoundsTransform { initial, target ->
        keyframes {
            durationMillis = SUBJECT_COVER_DURATION_MS
            for (time in 0..SUBJECT_COVER_DURATION_MS step 10) {
                subjectCoverPathRect(initial, target, time.toFloat() / SUBJECT_COVER_DURATION_MS, density) at time using LinearEasing
            }
        }
    }

    /** 快速 spatial：小范围位移、状态切换附近的形变（stiffness 1400）。 */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** 默认 spatial：列表项入场、面板就位、页面缩放（stiffness 700）。 */
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    /** 慢速 spatial：图表生长等幅度较大的形变（stiffness 300）。 */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)

    /** 快速 effects：小面积淡入淡出（stiffness 3800，无回弹）。 */
    fun <T> effectsFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 3800f)

    /** 默认 effects：常规淡入淡出（stiffness 1600，无回弹）。 */
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 1600f)

    /** 慢速 effects：大面积淡入（stiffness 800，无回弹）。 */
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 800f)
}
