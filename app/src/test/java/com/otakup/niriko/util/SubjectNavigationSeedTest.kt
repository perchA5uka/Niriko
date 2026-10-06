package com.otakup.niriko.util

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.*
import org.junit.Test

class SubjectNavigationSeedTest {
    @Test fun relationPreviewUsesOnlyObservedFieldsWithoutFabricatingMetadata() {
        SubjectNavigationSeed.preparePreview(com.otakup.niriko.data.remote.SubjectRelationInfo(
            99101L, "original", "中文名", 4, "续作", "cover-url",
        ))
        val preview = SubjectNavigationSeed.takeSubject(99101L)!!
        assertEquals(SubjectType.GAME, preview.type)
        assertEquals("cover-url", preview.coverUrl)
        assertNull(preview.totalEpisodes)
        assertNull(preview.ratingScore)
        assertNull(preview.airDate)
        assertNull(SubjectNavigationSeed.peekSubject(99101L))
    }
    @Test fun unknownSubjectTypeDoesNotInventAType() {
        SubjectNavigationSeed.prepare(null)
        SubjectNavigationSeed.preparePreview(com.otakup.niriko.data.remote.PersonSubjectInfo(
            99102L, "title", null, 99, null, imageUrl = "cover",
        ))
        assertNull(SubjectNavigationSeed.takeSubject(99102L))
    }

    @Test fun sourceRatioIsAvailableBeforeDestinationPainterLoads() {
        SubjectNavigationSeed.rememberCover(99001L, "cover", 600, 900, null)
        assertEquals(2f / 3f, SubjectNavigationSeed.coverFor(99001L)!!.aspectRatio, 0.0001f)
    }

    @Test fun onlyMatchingNavigationConsumesSubject() {
        val subject = SubjectEntity(99002L, "title", type = SubjectType.ANIME)
        SubjectNavigationSeed.prepare(subject)
        assertEquals(subject, SubjectNavigationSeed.takeSubject(99002L))
        assertNull(SubjectNavigationSeed.takeSubject(99002L))
    }

    @Test fun invalidDimensionsDoNotReplaceLoadedCover() {
        SubjectNavigationSeed.rememberCover(99003L, "loaded", 100, 200, null)
        SubjectNavigationSeed.rememberCover(99003L, "invalid", 0, 200, null)
        assertEquals("loaded", SubjectNavigationSeed.coverFor(99003L)!!.url)
    }
}
