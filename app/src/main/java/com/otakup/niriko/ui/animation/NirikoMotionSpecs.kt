package com.otakup.niriko.ui.animation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.ui.graphics.TransformOrigin

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

    fun <T> subjectCoverSeek(contracting: Boolean = false): FiniteAnimationSpec<T> = tween(
        durationMillis = SUBJECT_COVER_DURATION_MS,
        easing = if (contracting) subjectCoverContractEasing else subjectCoverExpandEasing
    )

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

    // ── 下表是 v1.2.2 新增的「页面过渡层」符号 ────────────────────────────────
    // 为什么不再新开弹簧档：上面六档逐字等于 material3 1.4.0 的 StandardMotionTokens，
    // 一旦多出第 7 档，这个「复刻」性质就没了（见本文件顶部注释）。所以下面只做
    // 组合与语义命名，不引入新的 damping/stiffness。

    /**
     * 微交互 spatial：按下 / 松手 / 选中态这类**近零位移**的变化。
     *
     * 取 damping 0.7 + stiffness 700（≈217ms 稳定、轻微过冲）：比 [spatialDefault] 的回弹更明显，
     * 让开关 thumb、按压高光这种"手指正在碰"的东西有生命感；但**不用于**页面级位移
     * （页面级用 [spatialDefault]，避免整页跟着回弹）。
     */
    fun <T> micro(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = 700f)

    // orSnap 已改为顶层扩展函数（见文件末尾）：成员扩展无法被 import，而它要在 object 外
    // 被 20+ 个文件直接 .orSnap(reduceMotion) 调用。此处不留 KDoc，避免 class_body 内悬空注释。

    /**
     * 同一容器内「大块内容切换」的过渡（详情页加载态、统计页分段、列表↔网格）。
     *
     * 只做 effects 级的淡入淡出 + 极轻微 scale（0.98），**不做位移**：
     * 内容块切换时位移会与页面的滚动位置打架；尺寸差异由容器裁剪兜住，因此
     * `sizeTransform = null`（不做 size 动画，避免每帧重测）。
     */
    fun contentTransform(reduceMotion: Boolean = false): ContentTransform {
        if (reduceMotion) {
            return ContentTransform(
                targetContentEnter = EnterTransition.None,
                initialContentExit = ExitTransition.None,
                sizeTransform = null,
            )
        }
        return ContentTransform(
            targetContentEnter = fadeIn(effectsFast<Float>()) +
                scaleIn(initialScale = 0.98f, animationSpec = spatialDefault<Float>()),
            initialContentExit = fadeOut(effectsFast<Float>()) +
                scaleOut(targetScale = 0.98f, animationSpec = spatialDefault<Float>()),
            sizeTransform = null,
        )
    }

    /**
     * 浮层（锚定菜单、下拉、气泡）的进场：从**锚点**长出来。
     *
     * 过去 `GlassAnchoredMenu` 是 `if (!expanded) return` 硬切；拉开时若用 Center 作为
     * transformOrigin，浮层会从自身中心"胀大"，与锚点是脱开的。origin 由调用点用
     * `glassMenuTransformOrigin(anchor, host)` 算出来传进来（纯函数，可单测）。
     */
    fun overlayEnter(
        origin: TransformOrigin = TransformOrigin.Center,
        reduceMotion: Boolean = false,
    ): EnterTransition = if (reduceMotion) {
        EnterTransition.None
    } else {
        fadeIn(effectsFast<Float>()) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = spatialDefault<Float>(),
                transformOrigin = origin,
            )
    }

    /** 浮层的退场：比进场快一档（进快出慢的反面——消失要利落，不要拖着）。 */
    fun overlayExit(
        origin: TransformOrigin = TransformOrigin.Center,
        reduceMotion: Boolean = false,
    ): ExitTransition = if (reduceMotion) {
        ExitTransition.None
    } else {
        fadeOut(effectsFast<Float>()) +
            scaleOut(
                targetScale = 0.96f,
                animationSpec = spatialFast<Float>(),
                transformOrigin = origin,
            )
    }

    /** 封面图（普通列表卡）淡入时长；**共享元素飞行期间必须为 0**，否则拖影。 */
    const val COVER_IMAGE_CROSSFADE_MS = 150

    /** 底栏 / 顶栏 chrome 隐藏时长（值沿用既有 220ms，仅补命名，不改观感）。 */
    const val CHROME_HIDE_DURATION_MS = 220

    /** 详情页顶栏在滚动后收起 / 展开的时长（既有 180ms）。 */
    const val DETAIL_CHROME_DURATION_MS = 180
}

/**
 * 「减少动态效果」时把任意规格降级为瞬时切换。
 *
 * 为什么放**顶层**而不是放进 [NirikoMotionSpecs]：成员扩展函数无法通过 import 使用，
 * 调用点只能写成 `with(NirikoMotionSpecs) { spec.orSnap(reduceMotion) }`；而「降级为瞬时」
 * 几乎每处动画都要用一遍，顶层扩展可以让调用点 import 后直接 `.orSnap(reduceMotion)`。
 */
fun <T> AnimationSpec<T>.orSnap(reduceMotion: Boolean): AnimationSpec<T> = if (reduceMotion) snap<T>() else this

/**
 * 折叠 / 展开类 [androidx.compose.animation.AnimatedVisibility] 的默认入场，附带「减少动态效果」降级。
 *
 * 取值与 Compose 默认一致（fadeIn + expandIn），所以 reduceMotion=false 时逐帧等价于不传参数；
 * reduceMotion=true 时瞬现——折叠面板「啪」一下出现，比慢慢长出来更符合系统级「减少动画」的预期。
 */
fun expandEnter(reduceMotion: Boolean): EnterTransition = if (reduceMotion) {
    EnterTransition.None
} else {
    fadeIn() + expandIn()
}

/** 与 [expandEnter] 配对的退场（默认值等价于 shrinkOut + fadeOut）。 */
fun collapseExit(reduceMotion: Boolean): ExitTransition = if (reduceMotion) {
    ExitTransition.None
} else {
    shrinkOut() + fadeOut()
}

/**
 * [orSnap] 的有限时长版本：animateContentSize 等 API 的形参类型是 [FiniteAnimationSpec]，
 * 而 [orSnap] 返回 [AnimationSpec]，不能直接传，故单独提供一个只接受有限规格的等价扩展。
 */
fun <T> FiniteAnimationSpec<T>.orSnapFinite(reduceMotion: Boolean): FiniteAnimationSpec<T> = if (reduceMotion) snap<T>() else this
