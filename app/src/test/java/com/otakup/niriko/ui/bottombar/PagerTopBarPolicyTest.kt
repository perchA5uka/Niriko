package com.otakup.niriko.ui.bottombar

import com.otakup.niriko.ui.common.SearchPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B8 纯判定：哪一页显示哪套悬浮按钮；切页后搜索态一律收起。 */
class PagerTopBarPolicyTest {

    @Test
    fun libraryAndDiscoverOwnTheirSlots() {
        assertEquals(TopBarSlot.LIBRARY, topBarSlotFor(0))
        assertEquals(TopBarSlot.DISCOVER, topBarSlotFor(1))
    }

    @Test
    fun statsAndSettingsHaveNoFloatingTopBar() {
        assertNull(topBarSlotFor(2))
        assertNull(topBarSlotFor(3))
    }

    @Test
    fun outOfRangePageIsNullInsteadOfCrashing() {
        assertNull(topBarSlotFor(-1))
        assertNull(topBarSlotFor(4))
        assertNull(topBarSlotFor(Int.MAX_VALUE))
    }

    @Test
    fun pageSwitchAlwaysCollapsesSearch() {
        SearchPhase.entries.forEach { phase ->
            assertEquals("切页后必须收起：$phase", SearchPhase.COLLAPSED, phaseAfterPageSwitch(phase))
        }
    }
}
