package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRT 老电视模式（F08）的纯规则测试。
 *
 * §11.2 / §16 的三条约束：**触发面极窄**（动画 + 明确年份 < 2000 + 用户开启）、
 * **默认关闭**（默认值在 AppSettings 里）、**约 4 秒后恢复静态**（有结束判据）。
 * 「年份未知不自动触发」是这里最要紧的一条 —— 它决定这个彩蛋会不会在数据缺失时乱放。
 */
class CrtPolicyTest {

    @Test
    fun `年份解析_覆盖线上出现过的形状`() {
        assertEquals(1999, CrtPolicy.yearOf("1999-10-20"))
        assertEquals(1999, CrtPolicy.yearOf("1999"))
        assertEquals(1999, CrtPolicy.yearOf("1999年10月"))
        assertEquals(1999, CrtPolicy.yearOf("1999-10"))
        assertEquals(1995, CrtPolicy.yearOf("  1995  "))
        assertEquals(2000, CrtPolicy.yearOf("2000-01-01"))
    }

    @Test
    fun `年份未知或脏数据一律返回 null`() {
        assertNull(CrtPolicy.yearOf(null))
        assertNull(CrtPolicy.yearOf(""))
        assertNull(CrtPolicy.yearOf("   "))
        assertNull(CrtPolicy.yearOf("未知"))
        assertNull(CrtPolicy.yearOf("20XX"))
        assertNull(CrtPolicy.yearOf("19/99"))
        assertNull("小于 1900 视为脏数据", CrtPolicy.yearOf("1234"))
        assertNull("超过 2100 视为脏数据", CrtPolicy.yearOf("9999"))
    }

    @Test
    fun `四条同时成立才播`() {
        // 正常的 1999 年动画 + 用户开启 + 没有减少动态效果
        assertTrue(
            CrtPolicy.shouldPlay(SubjectType.ANIME, "1999-10-20", userEnabled = true, reduceMotion = false),
        )
    }

    @Test
    fun `默认关闭时绝不播`() {
        assertFalse(
            CrtPolicy.shouldPlay(SubjectType.ANIME, "1999-10-20", userEnabled = false, reduceMotion = false),
        )
    }

    @Test
    fun `只对动画_其他类型不播`() {
        for (type in listOf(SubjectType.BOOK, SubjectType.MANGA, SubjectType.GAME, SubjectType.MUSIC, SubjectType.REAL)) {
            assertFalse(
                "类型 $type 不该播",
                CrtPolicy.shouldPlay(type, "1999-10-20", userEnabled = true, reduceMotion = false),
            )
        }
        assertFalse(CrtPolicy.shouldPlay(null, "1999-10-20", userEnabled = true, reduceMotion = false))
    }

    @Test
    fun `2000 年及以后不播_年份未知不播`() {
        assertFalse(CrtPolicy.shouldPlay(SubjectType.ANIME, "2000-01-01", true, false))
        assertFalse(CrtPolicy.shouldPlay(SubjectType.ANIME, "2024-04", true, false))
        assertFalse("年份未知不自动触发（§16）", CrtPolicy.shouldPlay(SubjectType.ANIME, null, true, false))
        assertFalse(CrtPolicy.shouldPlay(SubjectType.ANIME, "未知", true, false))
    }

    @Test
    fun `减少动态效果时直接关闭`() {
        assertFalse(CrtPolicy.shouldPlay(SubjectType.ANIME, "1999-10-20", userEnabled = true, reduceMotion = true))
    }

    @Test
    fun `总时长约四秒_最后一段线性淡出`() {
        assertEquals(4000, CrtPolicy.DURATION_MS)
        assertEquals(900, CrtPolicy.FADE_OUT_MS)
        // 前段满强度
        assertEquals(1f, CrtPolicy.envelope(0L), 0.001f)
        assertEquals(1f, CrtPolicy.envelope(1000L), 0.001f)
        assertEquals(1f, CrtPolicy.envelope((CrtPolicy.DURATION_MS - CrtPolicy.FADE_OUT_MS).toLong()), 0.001f)
        // 淡出段：中点约 0.5，且单调下降
        val mid = CrtPolicy.envelope(CrtPolicy.DURATION_MS - CrtPolicy.FADE_OUT_MS / 2L)
        assertEquals(0.5f, mid, 0.02f)
        assertTrue(CrtPolicy.envelope(3600L) < CrtPolicy.envelope(3200L))
        // 到点后彻底归零（恢复静态）
        assertEquals(0f, CrtPolicy.envelope(CrtPolicy.DURATION_MS.toLong()), 0.001f)
        assertEquals(0f, CrtPolicy.envelope(CrtPolicy.DURATION_MS + 5000L), 0.001f)
        // 负时间（理论上不会出现）也不能给出负强度
        assertEquals(0f, CrtPolicy.envelope(-1L), 0.001f)
    }

    @Test
    fun `结束判据与扫描带相位`() {
        assertFalse(CrtPolicy.isFinished(0L))
        assertFalse(CrtPolicy.isFinished(CrtPolicy.DURATION_MS.toLong() - 1L))
        assertTrue(CrtPolicy.isFinished(CrtPolicy.DURATION_MS.toLong()))
        // 相位在 0..1 之间循环，且非法周期不崩（不除零）
        for (t in listOf(0L, 300L, 1200L, 3000L)) {
            val phase = CrtPolicy.scanlinePhase(t)
            assertTrue("phase=$phase", phase >= 0f && phase <= 1f)
        }
        assertEquals(0f, CrtPolicy.scanlinePhase(500L, periodMs = 0L), 0.001f)
    }

    @Test
    fun `各层强度都在「轻微」区间`() {
        // §11.2 要求「轻微」，且不能毁掉文字可读性
        assertTrue(CrtPolicy.SCANLINE_ALPHA in 0.02f..0.15f)
        assertTrue(CrtPolicy.CHROMA_ALPHA in 0.01f..0.10f)
        assertTrue(CrtPolicy.NOISE_ALPHA in 0.01f..0.06f)
        assertTrue(CrtPolicy.VIGNETTE_ALPHA in 0.05f..0.20f)
    }
}
