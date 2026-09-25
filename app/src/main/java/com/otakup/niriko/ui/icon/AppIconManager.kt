package com.otakup.niriko.ui.icon

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.otakup.niriko.MainActivity
import com.otakup.niriko.R
import kotlinx.coroutines.delay
import java.io.File

/**
 * 桌面图标管理（Android 平台限制下的标准方案）：
 *
 * launcher 图标只能来自 APK 预置资源（activity-alias），运行时无法换成任意图片。
 * 因此「主题色图标 / 上传图片图标」落到**固定快捷方式**（ShortcutManager，图标可为
 * 任意 Bitmap），随后禁用 LauncherAlias 隐藏本体图标（应用仍在系统设置中可见）。
 *
 * 流程保证：先请求钉入快捷方式（系统弹窗），再隐藏本体；设置页始终提供
 * [showLauncherIcon] 恢复入口，并在文案中注明误删快捷方式的找回路径。
 */
object AppIconManager {

    private const val SHORTCUT_ID = "niriko_theme_icon"
    private const val ALIAS_NAME = ".LauncherAlias"
    private const val ICON_SIZE_PX = 192
    private const val CUSTOM_ICON_FILE = "custom_icon.png"
    /** 轮询确认钉入的间隔。 */
    private const val POLL_INTERVAL_MS = 250L
    /** 旧版快捷方式广播（MIUI 系兜底通路）。 */
    private const val ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT"
    private const val EXTRA_SHORTCUT_DUPLICATE = "duplicate"

    // ===== LauncherAlias 显隐 =====

    fun hideLauncherIcon(context: Context) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, context.packageName + ALIAS_NAME),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun showLauncherIcon(context: Context) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, context.packageName + ALIAS_NAME),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun isLauncherIconHidden(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(
            ComponentName(context, context.packageName + ALIAS_NAME),
        ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    // ===== 图标生成 =====

    /** 以主题色种子生成图标：主色圆角底 + 白色 N（与启动图标同造型）。 */
    fun generateThemeIcon(context: Context, seedColorArgb: Int): Bitmap {
        val size = ICON_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size * 0.22f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = seedColorArgb }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, bg)
        // ic_launcher_foreground 的 pathData（108 viewport）：M34,30h12l18,32V30h12v48H64L46,46v32H34z
        val s = size / 108f
        val nPath = Path().apply {
            moveTo(34f * s, 30f * s)
            lineTo(46f * s, 30f * s)
            lineTo(64f * s, 62f * s)
            lineTo(64f * s, 30f * s)
            lineTo(76f * s, 30f * s)
            lineTo(76f * s, 78f * s)
            lineTo(64f * s, 78f * s)
            lineTo(46f * s, 46f * s)
            lineTo(46f * s, 78f * s)
            lineTo(34f * s, 78f * s)
            close()
        }
        // 缩到 64% 居中（与自适应图标前景安全区一致）
        val nScale = 0.64f
        val nShift = size * (1f - nScale) / 2f
        canvas.save()
        canvas.translate(nShift, nShift)
        canvas.scale(nScale, nScale)
        canvas.drawPath(nPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        canvas.restore()
        return bitmap
    }

    /** 从上传图片生成图标：中心方形裁剪 + 圆角。 */
    fun generateIconFromImage(source: Bitmap): Bitmap {
        val size = ICON_SIZE_PX
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2f
        val top = (source.height - side) / 2f
        val square = Bitmap.createBitmap(source, left.toInt(), top.toInt(), side, side)
        val radius = size * 0.22f
        val path = Path().apply {
            addRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawBitmap(square, null, Rect(0, 0, size, size), Paint(Paint.ANTI_ALIAS_FLAG))
        return out
    }

    // ===== 自定义图片图标持久化 =====

    /** 保存用户上传图标到应用私有目录（恢复/重装后可继续使用）。 */
    fun saveCustomImageIcon(context: Context, bitmap: Bitmap) {
        runCatching {
            val dir = File(context.filesDir, "icon").apply { mkdirs() }
            File(dir, CUSTOM_ICON_FILE).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    /** 读取已保存的用户上传图标（无则 null）。 */
    fun loadCustomImageIcon(context: Context): Bitmap? = runCatching {
        val file = File(File(context.filesDir, "icon"), CUSTOM_ICON_FILE)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }.getOrNull()

    /** 桌面是否仍钉有我们的快捷方式（用户可能手动删除）。 */
    fun isShortcutPinned(context: Context): Boolean =
        runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .any { it.id == SHORTCUT_ID }
        }.getOrDefault(false)

    // ===== 快捷方式 =====

    private fun buildShortcut(context: Context, icon: Bitmap): ShortcutInfoCompat {
        val launch = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.app_name))
            .setIcon(IconCompat.createWithBitmap(icon))
            .setIntent(launch)
            .build()
    }

    /**
     * 钉入快捷方式的结果（第 6 轮返工）。
     *
     * 必须区分「请求已发出」与「桌面确实已有这个快捷方式」——旧实现把两者混为一谈，
     * 在小米 HyperOS 上造成了「既没弹窗、又隐藏了本体图标」的严重后果。
     */
    enum class PinOutcome {
        /** 已确认桌面存在我们的快捷方式 → 可以安全隐藏本体图标。 */
        PINNED,
        /** 请求已发出但无法确认（部分 ROM 不弹窗 / 不回执）→ **绝不能隐藏本体图标**。 */
        UNCONFIRMED,
        /** 设备 / ROM 不支持请求钉入。 */
        UNSUPPORTED,
    }

    /**
     * 请求钉入固定快捷方式，并**等待确认**。
     *
     * ## 为什么要等确认（用户实测定位到的根因）
     *
     * 小米 HyperOS（实测 17 Pro Max / HyperOS 4.0、HyperOS 3 同样复现）上
     * `requestPinShortcut` **不弹窗**，但返回值仍是 true；旧流程据此在 1.2 秒后
     * 隐藏了 LauncherAlias —— 用户既没拿到桌面快捷方式，又丢掉了应用本体图标，
     * 从桌面彻底找不到应用。
     *
     * 现在只有确认钉入（[isShortcutPinned]）才返回 [PinOutcome.PINNED]；
     * 无法确认时再补发一次旧版 INSTALL_SHORTCUT 广播（MIUI 系在授予
     * 「桌面快捷方式」权限后依赖这条通路），仍无法确认就如实返回，由调用方保留原图标。
     */
    suspend fun pinIcon(context: Context, icon: Bitmap, timeoutMs: Long = 6_000L): PinOutcome {
        val supported = runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }
            .getOrDefault(false)
        val requested = if (supported) {
            runCatching { ShortcutManagerCompat.requestPinShortcut(context, buildShortcut(context, icon), null) }
                .getOrDefault(false)
        } else {
            false
        }
        if (!requested) installShortcutLegacy(context, icon)
        if (awaitPinned(context, timeoutMs)) return PinOutcome.PINNED
        // 兜底再试一次旧版广播（部分 ROM 要先授予「桌面快捷方式」权限才会响应）
        if (requested) installShortcutLegacy(context, icon)
        if (awaitPinned(context, 2_000L)) return PinOutcome.PINNED
        return if (supported) PinOutcome.UNCONFIRMED else PinOutcome.UNSUPPORTED
    }

    /** 轮询等待「桌面确实钉了我们的快捷方式」。 */
    private suspend fun awaitPinned(context: Context, timeoutMs: Long): Boolean {
        var waited = 0L
        while (true) {
            if (isShortcutPinned(context)) return true
            if (waited >= timeoutMs) return false
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
    }

    /**
     * 旧版快捷方式广播（API 26 起 Google Launcher 已忽略，但 MIUI/HyperOS 系仍会响应）。
     *
     * 需要 manifest 里的 com.android.launcher.permission.INSTALL_SHORTCUT 权限；
     * 静默失败（没有对应接收方时系统直接丢弃）。
     */
    @Suppress("DEPRECATION") // 旧版 extra 自 API 26 起废弃，但 MIUI/HyperOS 的兜底通路仍需要它们
    private fun installShortcutLegacy(context: Context, icon: Bitmap) {
        runCatching {
            val launch = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.sendBroadcast(
                Intent(ACTION_INSTALL_SHORTCUT).apply {
                    putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch)
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, context.getString(R.string.app_name))
                    putExtra(Intent.EXTRA_SHORTCUT_ICON, icon)
                    putExtra(EXTRA_SHORTCUT_DUPLICATE, false)
                },
            )
        }
    }

    /** 更新已钉入的快捷方式图标（主题色变化时调用，桌面图标随之刷新）。 */
    fun updatePinnedIcon(context: Context, icon: Bitmap) {
        runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(buildShortcut(context, icon))) }
    }
}
