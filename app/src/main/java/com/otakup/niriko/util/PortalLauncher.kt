package com.otakup.niriko.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.otakup.niriko.data.model.PortalAction

/**
 * 传送门跳转封装：统一处理「精确 scheme → 搜索 scheme → 网页兜底」三级降级。
 *
 * - 自定义 scheme（如 bilibili:// / steam://）先判断是否有 App 能处理（未装则跳过）；
 * - 网页（https://）交给浏览器 / App Link；
 * - 异常捕获，绝不因跳转失败影响详情页。
 *
 * 注意：Android 11+ 需在 AndroidManifest <queries> 声明目标包或 scheme，否则 resolveActivity 恒为 null。
 */
object PortalLauncher {
    private const val TAG = "PortalLauncher"

    /**
     * 按优先级尝试 [actions]（调用方已排好序），返回是否成功启动。
     */
    fun launch(context: Context, actions: List<PortalAction>): Boolean {
        for (action in actions) {
            if (launchOne(context, action)) return true
        }
        return false
    }

    /**
     * 是否存在任一可启动的动作。用于菜单过滤：未安装的「仅拉起」目标（如 Mihon）自动隐藏，
     * 而带网页兜底的目标（音乐搜索等）必然可启动。
     */
    fun anyLaunchable(context: Context, actions: List<PortalAction>): Boolean =
        actions.any { canLaunch(context, it) }

    private fun canLaunch(context: Context, action: PortalAction): Boolean = when (action) {
        is PortalAction.UriScheme -> {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.uri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.resolveActivity(context.packageManager) != null
        }
        is PortalAction.WebUrl -> true
        is PortalAction.LaunchPackage ->
            context.packageManager.getLaunchIntentForPackage(action.packageName) != null
    }

    private fun launchOne(context: Context, action: PortalAction): Boolean {
        return try {
            when (action) {
                is PortalAction.UriScheme -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.uri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // 未安装 / 未在 <queries> 中声明 → resolveActivity 为 null，降级到下一级
                    if (intent.resolveActivity(context.packageManager) == null) return false
                    context.startActivity(intent)
                    true
                }

                is PortalAction.WebUrl -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                }

                is PortalAction.LaunchPackage -> {
                    val launchIntent = context.packageManager
                        .getLaunchIntentForPackage(action.packageName) ?: return false
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    true
                }
            }
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Portal 跳转失败（无目标 Activity）: $action", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Portal 跳转异常: $action", e)
            false
        }
    }
}
