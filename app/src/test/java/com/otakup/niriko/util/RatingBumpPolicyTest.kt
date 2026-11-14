package com.otakup.niriko.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-12 的护栏：首帧不弹、改分才弹、reduceMotion 下不弹。 */
class RatingBumpPolicyTest {

    @Test
    fun `进页面时的初值不触发弹跳`() {
        assertFalse(shouldPlayRatingBump(previous = 0f, next = 0f, reduceMotion = false))
        assertFalse(shouldPlayRatingBump(previous = 7.5f, next = 7.5f, reduceMotion = false))
    }

    @Test
    fun `用户改分时触发一次`() {
        assertTrue(shouldPlayRatingBump(previous = 7f, next = 8f, reduceMotion = false))
        assertTrue(shouldPlayRatingBump(previous = 0f, next = 0.5f, reduceMotion = false))
    }

    @Test
    fun `reduceMotion 下永不播放`() {
        assertFalse(shouldPlayRatingBump(previous = 7f, next = 8f, reduceMotion = true))
        assertFalse(shouldPlayRatingBump(previous = 0f, next = 0f, reduceMotion = true))
    }
}
