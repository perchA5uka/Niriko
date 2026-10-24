package com.otakup.niriko.data.diagnose

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.otakup.niriko.data.settings.GlassEffectLevel

/**
 * 玻璃自动档（10.1）：设备能力 → 档位，再与用户档位取「更保守」的一个。
 *
 * 纯函数 + 一个 Android 侧的探测壳（[deviceTier]）。判定阈值只在这里，
 * 这样「低端机被误判成全开」这类回归可以在 JVM 单测里复现。
 */
enum class GlassTier {
    /** 无 RenderEffect/AGSL（API < 31）：只能静态玻璃。 */
    STATIC,

    /** 有 GPU 但内存吃紧：保模糊、关折射。 */
    BLUR_ONLY,

    /** 全效果。 */
    FULL;

    companion object {
        /** 存储/展示用的稳定 key（进 DataStore 与报告，不要改）。 */
        fun fromKey(key: String?): GlassTier? = entries.firstOrNull { it.name == key }
    }
}

/** API 31 起才有 RenderEffect（真折射/模糊的硬前提）。 */
const val RENDER_EFFECT_MIN_SDK = 31

/** 纯判定：不看机型名，只看两个可解释的信号。 */
fun resolveGlassTier(sdkInt: Int, isLowRamDevice: Boolean): GlassTier = when {
    sdkInt < RENDER_EFFECT_MIN_SDK -> GlassTier.STATIC
    isLowRamDevice -> GlassTier.BLUR_ONLY
    else -> GlassTier.FULL
}

/**
 * 用户档位 ∩ 设备档位：取更保守者（ordinal 越大越保守）。
 * 用户选 OFF 时永远 OFF；设备是 STATIC 时用户选 FULL 也只能 STATIC。
 */
fun effectiveGlassLevel(user: GlassEffectLevel, tier: GlassTier): GlassEffectLevel = when (tier) {
    GlassTier.FULL -> user
    GlassTier.BLUR_ONLY -> if (user == GlassEffectLevel.FULL) GlassEffectLevel.REDUCED else user
    GlassTier.STATIC -> GlassEffectLevel.OFF
}

/** 设置行里显示的中文档名。 */
fun glassTierLabel(tier: GlassTier): String = when (tier) {
    GlassTier.FULL -> "全效果"
    GlassTier.BLUR_ONLY -> "降低"
    GlassTier.STATIC -> "关闭"
}

/** 用户档位的中文档名（与上面同一套措辞，便于「本机 X / 生效 Y」并排显示）。 */
fun glassLevelLabel(level: GlassEffectLevel): String = when (level) {
    GlassEffectLevel.FULL -> "全效果"
    GlassEffectLevel.REDUCED -> "降低"
    GlassEffectLevel.OFF -> "关闭"
}

/** 自动档开关的取值（存 DataStore 的 glass_auto_tier 键）。 */
object GlassAutoMode {
    /** 自动：按本机能力降档。 */
    const val AUTO = "AUTO"

    /** 手动：完全听用户档位。 */
    const val MANUAL = "MANUAL"

    fun isAuto(raw: String?): Boolean = raw != MANUAL
}

/**
 * Android 侧探测（唯一带 Context 的入口）。
 *
 * 只读 [ActivityManager.isLowRamDevice] 与 SDK 版本，不碰 GPU 厂商/GLES 字符串：
 * 后者在不同 ROM 上不可靠，而 RenderEffect 的可用性由 SDK 版本精确决定。
 */
fun deviceTier(context: Context): GlassTier {
    val lowRam = runCatching {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }.getOrDefault(false)
    return resolveGlassTier(Build.VERSION.SDK_INT, lowRam)
}
