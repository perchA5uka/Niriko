package com.otakup.niriko.ui.share

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分享卡 Bitmap 渲染桥（ComposeView attach 窗口 + View.draw 截图）。
 *
 * 背景：GraphicsLayer 离屏录制方案在 Compose 1.7 下会因 Occlusion Culling
 * （隐形节点绘制被剔除）导致快照全白；纯离屏 ComposeView.measure() 会因
 * 「未 attach 窗口」崩溃（Cannot locate windowRecomposer）。
 *
 * 本方案：
 *   1. 把临时 ComposeView attach 到 Activity 的 decorView（负 margin 移出
 *      屏幕外，用户不可见），窗口 attach 后 windowRecomposer 可用；
 *   2. 主线程同步 measure(1080×1400) → layout → view.draw(Canvas(bitmap))。
 *      View.draw 是主动绘制，不受 Compose 遮挡剔除影响，100% 输出内容；
 *   3. finally 中 removeView 清理，无残留、无闪烁。
 *
 * 渲染内容用 [ShareCardLayout]（纯 Compose、严格对齐 HTML 原型），
 * Density(1f) 保证 1dp = 1px；#101410 不透明底已在卡片根组件固化。
 */
class ShareBitmapHost {

    /** 在主线程同步渲染分享卡并返回 1080×1400 位图。 */
    suspend fun render(context: Context, data: ShareCardData): Bitmap = withContext(Dispatchers.Main) {
        val activity = context.findActivity() ?: return@withContext errorBitmap()
        val container = (activity.window?.decorView as? ViewGroup)
            ?: return@withContext errorBitmap()
        val composeView = ComposeView(activity).apply {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ShareCardLayout(data = data)
                }
            }
        }
        // attach 到窗口（负 margin 移到屏幕外，用户不可见但 windowRecomposer 可用）
        val params = FrameLayout.LayoutParams(
            ShareCardRenderer.WIDTH,
            ShareCardRenderer.HEIGHT,
        ).apply {
            leftMargin = -ShareCardRenderer.WIDTH - 200
            topMargin = -ShareCardRenderer.HEIGHT - 200
        }
        container.addView(composeView, params)
        try {
            // 关键：Compose 首次组合是异步帧调度。addView 后立即 draw 可能输出「未组合/上一次组合」
            // 的内容（改动不生效的根因）。等待 2 帧确保 Recomposer 完成组合后再截图。
            withFrameNanos { }
            withFrameNanos { }
            val spec = View.MeasureSpec.makeMeasureSpec(
                ShareCardRenderer.WIDTH,
                View.MeasureSpec.EXACTLY,
            )
            val hSpec = View.MeasureSpec.makeMeasureSpec(
                ShareCardRenderer.HEIGHT,
                View.MeasureSpec.EXACTLY,
            )
            composeView.measure(spec, hSpec)
            composeView.layout(0, 0, ShareCardRenderer.WIDTH, ShareCardRenderer.HEIGHT)
            val bitmap = Bitmap.createBitmap(
                ShareCardRenderer.WIDTH,
                ShareCardRenderer.HEIGHT,
                Bitmap.Config.ARGB_8888,
            )
            composeView.draw(Canvas(bitmap))
            bitmap
        } finally {
            container.removeView(composeView)
        }
    }

    /** 失败兜底：返回一张 #101410 纯色位图，避免分享流程崩溃/白图。 */
    private fun errorBitmap(): Bitmap =
        Bitmap.createBitmap(
            ShareCardRenderer.WIDTH,
            ShareCardRenderer.HEIGHT,
            Bitmap.Config.ARGB_8888,
        ).apply {
            eraseColor(0xFF101410.toInt())
        }

    /** 从 Context 逐层向上查找 Activity。 */
    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}

object ShareCardRenderer {
    const val WIDTH = 1620
    const val HEIGHT = 1080
}