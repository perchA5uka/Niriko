package com.otakup.niriko.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Niriko 27 · S1：玻璃强度的数学与「默认零回归」保证。 */
class GlassStrengthTest {

    @Test
    fun 默认强度的倍率恰好是一() {
        // 零回归的根：默认值下所有表面的 tint 必须与改动前逐位相等
        assertEquals(1f, GlassStrength.tintMultiplier(GlassStrength.Default), 0f)
    }

    @Test
    fun 默认强度下任何基准不透明度原样返回() {
        listOf(0.14f, 0.20f, 0.30f, 0.38f, 0.42f, 0.45f, 0.50f, 0.55f, 0.75f).forEach { base ->
            assertEquals("基准 " + base + " 被默认强度改动了", base, GlassStrength.scaledAlpha(base, GlassStrength.Default), 0f)
        }
    }

    @Test
    fun 两端倍率钉在计划值() {
        assertEquals(GlassStrength.MinMultiplier, GlassStrength.tintMultiplier(GlassStrength.Min), 1e-6f)
        assertEquals(GlassStrength.MaxMultiplier, GlassStrength.tintMultiplier(GlassStrength.Max), 1e-6f)
        assertEquals(0.4f, GlassStrength.MinMultiplier, 0f)
        assertEquals(1.8f, GlassStrength.MaxMultiplier, 0f)
    }

    @Test
    fun 倍率随强度单调不减() {
        val samples = (0..100).map { it / 100f }
        samples.zipWithNext().forEach { (a, b) ->
            assertTrue("倍率应单调：" + a + " → " + b, GlassStrength.tintMultiplier(b) >= GlassStrength.tintMultiplier(a))
        }
    }

    @Test
    fun 越界输入被夹住而不是外推() {
        assertEquals(GlassStrength.tintMultiplier(0f), GlassStrength.tintMultiplier(-3f), 0f)
        assertEquals(GlassStrength.tintMultiplier(1f), GlassStrength.tintMultiplier(9f), 0f)
        assertEquals(0f, GlassStrength.clamp(-1f), 0f)
        assertEquals(1f, GlassStrength.clamp(2f), 0f)
    }

    @Test
    fun 非有限值回落默认() {
        assertEquals(GlassStrength.Default, GlassStrength.clamp(Float.NaN), 0f)
        assertEquals(GlassStrength.Default, GlassStrength.clamp(Float.POSITIVE_INFINITY), 0f)
        assertEquals(GlassStrength.Default, GlassStrength.clamp(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(1f, GlassStrength.tintMultiplier(Float.NaN), 0f)
    }

    @Test
    fun 缩放后的不透明度不超过上限也不为负() {
        listOf(0f, 0.2f, 0.5f, 0.75f, 0.9f, 1f).forEach { base ->
            (0..10).map { it / 10f }.forEach { strength ->
                val alpha = GlassStrength.scaledAlpha(base, strength)
                assertTrue("越界：base=" + base + " strength=" + strength + " → " + alpha, alpha in 0f..GlassStrength.MaxAlpha)
            }
        }
        assertEquals(0f, GlassStrength.scaledAlpha(0f, 1f), 0f)
    }

    @Test
    fun 最实一档仍然给背后内容留出空间() {
        // 区块里最实的存量值是 0.75：拉满后会被夹在上限，不会变成不透明色块
        assertEquals(GlassStrength.MaxAlpha, GlassStrength.scaledAlpha(0.75f, GlassStrength.Max), 0f)
    }

    @Test
    fun 强度越低越清晰越高越实() {
        val base = 0.30f
        assertTrue(GlassStrength.scaledAlpha(base, 0f) < base)
        assertTrue(GlassStrength.scaledAlpha(base, 1f) > base)
    }

    @Test
    fun 档位文字覆盖整个区间且两端不同() {
        assertEquals("极致清晰", GlassStrength.label(0f))
        assertEquals("均衡", GlassStrength.label(GlassStrength.Default))
        assertEquals("完全着色", GlassStrength.label(1f))
        (0..100).forEach { assertTrue(GlassStrength.label(it / 100f).isNotBlank()) }
    }

    @Test
    fun 默认值落在滑条十档的刻度上() {
        // 设置页用 steps = 9（0.0, 0.1 … 1.0）；默认值若不在刻度上，用户永远拖不回默认
        val ticks = (0..10).map { it / 10f }
        assertTrue(ticks.any { kotlin.math.abs(it - GlassStrength.Default) < 1e-6f })
    }
}
