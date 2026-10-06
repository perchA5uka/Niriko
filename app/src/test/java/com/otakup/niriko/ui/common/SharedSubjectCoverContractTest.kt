package com.otakup.niriko.ui.common

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static wiring guards; shared frames still require device verification. */
class SharedSubjectCoverContractTest {
    private fun source(path: String): String =
        File("src/main/java/com/otakup/niriko/$path").readText()

    @Test fun directEntrancesRegisterLoadedCoversWithoutChangingBounds() {
        for (path in listOf("ui/subject/RelationsSection.kt", "ui/common/CreditSubjectCard.kt", "ui/library/LibraryGalleryCard.kt")) {
            val code = source(path)
            assertTrue(path, code.contains("SharedSubjectCover("))
            assertFalse(path, code.contains("AsyncImage("))
        }
        val adapter = source("ui/components/SharedSubjectCover.kt")
        assertTrue(adapter.contains("coverOverrideStore.overrideFor(subjectId)"))
        assertTrue(adapter.contains("onSuccess ="))
        assertTrue(adapter.contains("SubjectNavigationSeed.rememberCover("))
        assertTrue(adapter.contains("drawable.intrinsicWidth, drawable.intrinsicHeight"))
        assertTrue(adapter.contains("state.result.memoryCacheKey"))
        assertFalse(adapter.contains("aspectRatio("))
        assertFalse(adapter.contains("SubjectNavigationSeed.prepare("))
        assertTrue(source("ui/subject/RelationsSection.kt").contains(".height(120.dp)"))
        assertTrue(source("ui/common/CreditSubjectCard.kt").contains(".aspectRatio(CreditSubjectCardMetrics.posterAspectRatio)"))
        assertTrue(source("ui/library/LibraryGalleryCard.kt").contains(".height(340.dp)"))
    }

    @Test fun recommendationsReserveBothTitleLinesAndKeepCoverImage() {
        val code = source("ui/subject/SubjectDetailScreen.kt")
            .substringAfter("private fun GuessYouLikeSection(")
            .substringBefore("private fun DatePill(")
        assertTrue(code.contains("CoverImage("))
        assertTrue(code.contains("minLines = com.otakup.niriko.util.RailCardPolicy.WIDE_TITLE_LINES"))
        assertTrue(code.contains("maxLines = com.otakup.niriko.util.RailCardPolicy.WIDE_TITLE_LINES"))
        assertTrue(code.contains("aspectRatio = 3f / 4f"))
    }
}
