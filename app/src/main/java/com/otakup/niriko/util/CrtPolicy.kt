package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType

/**
 * CRT 老电视模式（F08，计划 §11.2）。
 *
 * 这一条在计划里挂了三条硬约束，全部落成纯规则：
 * 1. **触发面极窄**：只对**动画**、**明确年份 < 2000**、且用户**自己开启**的作品启用；
 *    年份未知（数据里没有、或解析失败）一律不触发 —— §16 的边界写得很清楚：
 *    「年份未知不自动触发；只在用户启用后播放」。
 * 2. **默认关闭**：§16 的决策是默认关闭（彩蛋类效果不该替用户做主），
 *    因此设置默认值是 false；首次开启时界面给一句说明。
 * 3. **有时限**：总时长约 4 秒后**恢复静态**（不是常驻滤镜），
 *    因此这里也给出进度与结束判据，界面据此自动收尾。
 *
 * 关于性能与降级（§11.2 的另一半）：本实现**完全不用 AGSL / RuntimeShader** ——
 * 扫描线、色散、噪点都是一次性绘制的廉价图元（详见 `ui/animation/CrtOverlay.kt`），
 * 因此「AGSL 不可用 / 低端设备」这条降级路径天然满足（没有 shader 需要降级），
 * 剩下的降级只有「减少动态效果 → 不播」这一条。
 */
object CrtPolicy {

    /** 只有 2000 年以前的动画才进入这条彩蛋。 */
    const val YEAR_THRESHOLD = 2000

    /** 总时长：约 4 秒后恢复静态（§11.2）。 */
    const val DURATION_MS = 4000

    /** 收尾淡出占用的时长（最后一段整体淡出，避免「啪」地一下消失）。 */
    const val FADE_OUT_MS = 900

    /** 各层强度上限（都很低：§11.2 要求「轻微」且不能毁掉文字可读性）。 */
    const val SCANLINE_ALPHA = 0.10f
    const val CHROMA_ALPHA = 0.06f
    const val NOISE_ALPHA = 0.035f
    const val VIGNETTE_ALPHA = 0.14f

    /**
     * 从 Bangumi 的 airDate 里取年份。
     *
     * 数据里出现过这些形状：`"1999-10-20"`、`"1999"`、`"1999年10月"`、`"1999-10"`。
     * 只取**开头连续的 4 位数字**：这是「明确年份」的唯一判据；
     * 取不到（空串、`"未知"`、`"20XX"`）返回 null —— 调用方据此不触发。
     */
    fun yearOf(airDate: String?): Int? {
        val text = airDate?.trim().orEmpty()
        if (text.isEmpty()) return null
        val digits = StringBuilder()
        for (ch in text) {
            if (ch.isDigit()) {
                digits.append(ch)
                if (digits.length == 4) break
            } else if (digits.isNotEmpty()) {
                // 数字被打断（例如 "19/99"）：不再继续拼，交给下面的合法性检查
                break
            } else {
                // 开头不是数字（例如 "未知"）：直接放弃
                return null
            }
        }
        val year = digits.toString().toIntOrNull() ?: return null
        // 合理的年份区间：小于 1900 或大于当前年份的一律视为数据脏
        if (year < 1900 || year > 2100) return null
        return year
    }

    /**
     * 这次打开详情页要不要播 CRT。
     *
     * @param type 作品类型（只有 ANIME 进入）
     * @param airDate 首播日期文本
     * @param userEnabled 用户是否打开了这个彩蛋（默认关闭）
     * @param reduceMotion 减少动态效果（直接关闭）
     */
    fun shouldPlay(
        type: SubjectType?,
        airDate: String?,
        userEnabled: Boolean,
        reduceMotion: Boolean,
    ): Boolean {
        if (!userEnabled || reduceMotion) return false
        if (type != SubjectType.ANIME) return false
        val year = yearOf(airDate) ?: return false
        return year < YEAR_THRESHOLD
    }

    /** 是否播完了（到点就该收 overlay，不再常驻）。 */
    fun isFinished(elapsedMs: Long): Boolean = elapsedMs >= DURATION_MS

    /**
     * 0..1 的整体强度包络：开始即满强度、最后 [FADE_OUT_MS] 线性淡出到 0。
     *
     * 注意 `elapsedMs == 0` 返回 **1**（第一帧就该是满强度）：如果对 0 也返回 0，
     * 动画首帧会「看不见、第二帧突然全亮」—— 那一下在真机上就是闪烁。
     * 只有**负值**（理论上不会出现）才当 0 处理。
     */
    fun envelope(elapsedMs: Long): Float {
        if (elapsedMs < 0L) return 0f
        if (elapsedMs >= DURATION_MS) return 0f
        val fadeStart = DURATION_MS - FADE_OUT_MS
        if (elapsedMs <= fadeStart) return 1f
        val remaining = DURATION_MS - elapsedMs
        return (remaining.toFloat() / FADE_OUT_MS.toFloat()).coerceIn(0f, 1f)
    }

    /** 扫描线随时间缓慢滚动的位置（0..1，用于让静态纹理有一点「逐行扫描」的动感）。 */
    fun scanlinePhase(elapsedMs: Long, periodMs: Long = 1200L): Float {
        if (periodMs <= 0L) return 0f
        val t = (elapsedMs % periodMs).toFloat() / periodMs.toFloat()
        return t.coerceIn(0f, 1f)
    }
}
