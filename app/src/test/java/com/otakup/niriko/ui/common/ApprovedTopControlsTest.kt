package com.otakup.niriko.ui.common

import com.otakup.niriko.data.model.WatchStatus
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ApprovedTopControlsTest {
    @Test fun selectedItemMovesFirstAndOtherOptionsKeepTheirOrder() {
        val options = listOf("season", "history", "steam").map { TopSelectorOption(it, it) {} }
        assertEquals(listOf("history", "season", "steam"), orderedTopSelectorOptions(options, "history").map { it.key })
        assertEquals(options, orderedTopSelectorOptions(options, "unknown"))
    }
    @Test fun libraryCountsShowAllStatusesInsteadOfOnlySelectedStatus() {
        val counts = mapOf(WatchStatus.PLAN_TO_WATCH to 2, WatchStatus.WATCHING to 3, WatchStatus.COMPLETED to 4)
        assertEquals(9, librarySearchStatusCount(counts, null))
        assertEquals(3, librarySearchStatusCount(counts, WatchStatus.WATCHING))
        assertEquals(0, librarySearchStatusCount(counts, WatchStatus.DROPPED))
    }
    @Test fun sharedSearchPreservesWidthAndDiscoverWorksPeopleMode() {
        val text = source("ui/common/IosStyleSearchComponent.kt")
        assertTrue(text.contains("(screenW - 32.dp).coerceAtLeast(anchorDp)"))
        assertTrue(text.contains("SearchMode.WORKS"))
        assertTrue(text.contains("SearchMode.CHARACTERS"))
        assertTrue(text.contains("if (filters == null)"))
    }
    @Test fun searchExpansionDoesNotChangeEitherFeedInset() {
        val discover = source("ui/subject/SubjectSearchScreen.kt")
        val library = source("ui/screens/Screens.kt")
        assertTrue(discover.contains("val contentTopInset: Dp = windowTopInset + TopFadePolicy.searchContentPaddingDp(56f).dp"))
        assertTrue(library.contains("val headerTopPadding = windowTopInset + TopFadePolicy.searchContentPaddingDp(56f).dp"))
        assertTrue(source("ui/common/SearchToolbar.kt").contains("VerticalTopSelector("))
        assertTrue(source("ui/library/FolderNavBar.kt").contains("VerticalTopSelector("))
    }
    private fun source(file: String) = File("src/main/java/com/otakup/niriko/" + file).readText()
}
