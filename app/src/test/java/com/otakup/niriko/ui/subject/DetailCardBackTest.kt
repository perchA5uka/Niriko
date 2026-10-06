package com.otakup.niriko.ui.subject

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.viewmodel.SubjectDetailUiState
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailCardBackTest {
    private val subject = SubjectEntity(subjectId = 1L, title = "Original", titleCN = "Personal title",
        type = SubjectType.ANIME, totalEpisodes = 12, ratingScore = 10f)
    private val collected = SubjectDetailUiState(isInCollection = true, currentStatus = WatchStatus.COMPLETED)
    private val utc = ZoneId.of("UTC")

    @Test fun uncollectedSubjectHasNoBackEvenWithCommunityScore() {
        assertNull(detailCardBackModel(subject, SubjectDetailUiState(), utc))
    }

    @Test fun absentPersonalFieldsStayAbsentAndNeverUseCommunityMetadata() {
        val model = detailCardBackModel(subject, collected, utc)!!
        assertEquals("Personal title", model.title)
        assertEquals("看过", model.status)
        assertNull(model.rating)
        assertTrue(model.progress.isEmpty())
        assertNull(model.collected)
        assertTrue(model.tags.isEmpty())
        assertNull(model.impression)
    }

    @Test fun backUsesRealCollectionValues() {
        val state = collected.copy(myRating = 9.5f, watchedEpisodes = 12,
            collectionCreateTime = Instant.parse("2026-09-18T12:00:00Z").toEpochMilli(),
            personalTags = listOf(" favorite ", "", "revisit"), personalImpression = " My note ")
        val model = detailCardBackModel(subject, state, utc)!!
        assertEquals(listOf("12 / 12 集"), model.progress)
        assertEquals("2026.09.18", model.collected)
        assertEquals(listOf("favorite", "revisit"), model.tags)
        assertEquals("My note", model.impression)
        assertEquals(String.format(java.util.Locale.getDefault(), "%.1f / 10", 9.5f), model.rating)
    }

    @Test fun datesUseDeviceZoneNotUtcDay() {
        val state = collected.copy(collectionCreateTime = Instant.parse("2026-09-18T23:00:00Z").toEpochMilli())
        assertEquals("2026.09.19", detailCardBackModel(subject, state, ZoneId.of("Asia/Shanghai"))!!.collected)
    }

    @Test fun zeroProgressIsRealNotMissing() {
        assertEquals(listOf("0 / 12 集"), detailCardBackModel(subject, collected.copy(watchedEpisodes = 0), utc)!!.progress)
    }

    @Test fun unknownTotalDoesNotInventADenominator() {
        assertEquals(listOf("3 集"), detailCardBackModel(subject.copy(totalEpisodes = null),
            collected.copy(watchedEpisodes = 3), utc)!!.progress)
    }

    @Test fun bookVolumesAndChaptersKeepTheirOwnUnitsAndTotals() {
        val model = detailCardBackModel(subject.copy(type = SubjectType.BOOK, volumes = 4, totalEpisodes = 40),
            collected.copy(watchedEpisodes = 20, watchedVolumes = 2), utc)!!
        assertEquals("读过", model.status)
        assertEquals(listOf("20 / 40 话", "2 / 4 卷"), model.progress)
    }

    @Test fun gameMinutesAreNotEpisodeCounts() {
        val model = detailCardBackModel(subject.copy(type = SubjectType.GAME), collected.copy(watchedEpisodes = 125), utc)!!
        assertEquals("玩过", model.status)
        assertEquals(listOf("2h5m"), model.progress)
        assertEquals(listOf("0m"), detailCardBackModel(subject.copy(type = SubjectType.GAME),
            collected.copy(watchedEpisodes = 0), utc)!!.progress)
    }

    @Test fun musicUsesActualTrackIdsWhenPresent() {
        val model = detailCardBackModel(subject.copy(type = SubjectType.MUSIC),
            collected.copy(watchedEpisodes = 99, watchedTrackIds = setOf(1L, 2L, 3L)), utc)!!
        assertEquals("听过", model.status)
        assertEquals(listOf("3 / 12 首"), model.progress)
    }

    @Test fun invalidOptionalFieldsAreHiddenNotFabricated() {
        val model = detailCardBackModel(subject, collected.copy(myRating = Float.NaN, watchedEpisodes = -1,
            collectionCreateTime = 0L, personalTags = listOf(" "), personalImpression = "  "), utc)!!
        assertNull(model.rating)
        assertNull(model.collected)
        assertTrue(model.progress.isEmpty())
        assertTrue(model.tags.isEmpty())
        assertNull(model.impression)
        listOf(-1f, 11f, Float.POSITIVE_INFINITY).forEach {
            assertNull(detailCardBackModel(subject, collected.copy(myRating = it), utc)!!.rating)
        }
    }
}
