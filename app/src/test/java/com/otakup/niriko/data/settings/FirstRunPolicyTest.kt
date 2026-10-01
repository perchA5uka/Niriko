package com.otakup.niriko.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首次启动引导判定单元测试（计划 B1-1）。
 *
 * 关键回归：**升级安装的老用户不能被引导拦截**（键缺失但已有历史设置）。
 */
class FirstRunPolicyTest {

    @Test
    fun firstInstallWithoutStoredSettings_showsGuide() {
        assertTrue(FirstRunPolicy.shouldShowFirstRun(firstRunCompleted = null, hasStoredSettings = false))
    }

    @Test
    fun upgradedInstallWithStoredSettings_hidesGuide() {
        assertFalse(FirstRunPolicy.shouldShowFirstRun(firstRunCompleted = null, hasStoredSettings = true))
    }

    @Test
    fun completed_hidesGuide() {
        assertFalse(FirstRunPolicy.shouldShowFirstRun(firstRunCompleted = true, hasStoredSettings = false))
        assertFalse(FirstRunPolicy.shouldShowFirstRun(firstRunCompleted = true, hasStoredSettings = true))
    }

    @Test
    fun explicitlyNotCompleted_showsGuide() {
        assertTrue(FirstRunPolicy.shouldShowFirstRun(firstRunCompleted = false, hasStoredSettings = false))
        assertTrue(FirstRunPolicy.shouldShowFirstRun(firstRunCompleted = false, hasStoredSettings = true))
    }
}
