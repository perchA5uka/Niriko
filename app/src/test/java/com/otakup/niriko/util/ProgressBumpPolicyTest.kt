package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 长按浮层进度 ± 单元测试（计划 B1-2）：单位判定、上下限、文案。
 */
class ProgressBumpPolicyTest {

    // ===== 单位判定 =====

    @Test
    fun unitOf_animeAndReal_episode() {
        assertEquals(ProgressUnit.EPISODE, ProgressBumpPolicy.unitOf(SubjectType.ANIME, null))
        assertEquals(ProgressUnit.EPISODE, ProgressBumpPolicy.unitOf(SubjectType.REAL, null))
    }

    @Test
    fun unitOf_bookAndManga_volumeOnlyWhenTrackingVolumes() {
        assertEquals(ProgressUnit.EPISODE, ProgressBumpPolicy.unitOf(SubjectType.BOOK, null))
        assertEquals(ProgressUnit.VOLUME, ProgressBumpPolicy.unitOf(SubjectType.BOOK, 3))
        assertEquals(ProgressUnit.VOLUME, ProgressBumpPolicy.unitOf(SubjectType.MANGA, 0))
    }

    @Test
    fun unitOf_game_minute_othersUnsupported() {
        assertEquals(ProgressUnit.MINUTE, ProgressBumpPolicy.unitOf(SubjectType.GAME, null))
        assertEquals(ProgressUnit.UNSUPPORTED, ProgressBumpPolicy.unitOf(SubjectType.MUSIC, null))
        assertEquals(ProgressUnit.UNSUPPORTED, ProgressBumpPolicy.unitOf(SubjectType.OTHER, null))
        assertEquals(ProgressUnit.UNSUPPORTED, ProgressBumpPolicy.unitOf(SubjectType.PERSON, null))
    }

    // ===== 快照 =====

    @Test
    fun snapshot_episode_usesWatchedEpisodesAndTotal() {
        val s = ProgressBumpPolicy.snapshot(SubjectType.ANIME, watchedEpisodes = 7, watchedVolumes = null, totalEpisodes = 12)!!
        assertEquals(ProgressUnit.EPISODE, s.unit)
        assertEquals(7, s.value)
        assertEquals(12, s.total)
    }

    @Test
    fun snapshot_nullProgress_isZero() {
        val s = ProgressBumpPolicy.snapshot(SubjectType.ANIME, null, null, 12)!!
        assertEquals(0, s.value)
        assertEquals(12, s.total)
    }

    @Test
    fun snapshot_nonPositiveTotal_isUnknown() {
        assertNull(ProgressBumpPolicy.snapshot(SubjectType.ANIME, 1, null, 0)!!.total)
        assertNull(ProgressBumpPolicy.snapshot(SubjectType.ANIME, 1, null, -3)!!.total)
    }

    @Test
    fun snapshot_gameValueIsMinutes() {
        val s = ProgressBumpPolicy.snapshot(SubjectType.GAME, watchedEpisodes = 150, watchedVolumes = null, totalEpisodes = null)!!
        assertEquals(ProgressUnit.MINUTE, s.unit)
        assertEquals(150, s.value)
        assertNull(s.total)
    }

    @Test
    fun snapshot_unsupportedType_isNull() {
        assertNull(ProgressBumpPolicy.snapshot(SubjectType.MUSIC, 3, null, 12))
    }

    // ===== 上下限 =====

    @Test
    fun next_clampsToTotal() {
        val s = ProgressSnapshot(ProgressUnit.EPISODE, 12, 12)
        assertEquals(12, ProgressBumpPolicy.next(s, 1))
        val almost = ProgressSnapshot(ProgressUnit.EPISODE, 11, 12)
        assertEquals(12, ProgressBumpPolicy.next(almost, 5))
    }

    @Test
    fun next_clampsToZero() {
        val s = ProgressSnapshot(ProgressUnit.EPISODE, 0, 12)
        assertEquals(0, ProgressBumpPolicy.next(s, -1))
    }

    @Test
    fun next_unknownTotalHasNoUpperBound() {
        val s = ProgressSnapshot(ProgressUnit.VOLUME, 3, null)
        assertEquals(4, ProgressBumpPolicy.next(s, 1))
        assertEquals(99, ProgressBumpPolicy.next(ProgressSnapshot(ProgressUnit.VOLUME, 98, null), 1))
    }

    // ===== 文案 =====

    @Test
    fun label_episode() {
        assertEquals("7 / 12 集", ProgressBumpPolicy.label(ProgressSnapshot(ProgressUnit.EPISODE, 7, 12)))
        assertEquals("7 集", ProgressBumpPolicy.label(ProgressSnapshot(ProgressUnit.EPISODE, 7, null)))
    }

    @Test
    fun label_volumeAndMinutes() {
        assertEquals("3 卷", ProgressBumpPolicy.label(ProgressSnapshot(ProgressUnit.VOLUME, 3, null)))
        assertEquals("3 / 31 卷", ProgressBumpPolicy.label(ProgressSnapshot(ProgressUnit.VOLUME, 3, 31)))
        assertEquals("2h30m", ProgressBumpPolicy.label(ProgressSnapshot(ProgressUnit.MINUTE, 150, null)))
        assertEquals("", ProgressBumpPolicy.label(ProgressSnapshot(ProgressUnit.UNSUPPORTED, 0, null)))
    }

    // ===== 进度条（B11：单位与分母必须和编辑入口一致）=====

    @Test
    fun barFraction_mangaTrackedByVolume_usesVolumeDenominator() {
        // 《灌篮高手》：31 卷 / 276 话。改造前这里算的是 (watchedEpisodes ?: 0) / 276，
        // 读满 31 卷也只是 0.11；正确结果是 1.0。
        val full = ProgressBumpPolicy.barFraction(
            type = SubjectType.MANGA,
            watchedEpisodes = 0,
            watchedVolumes = 31,
            totalEpisodes = 276,
            totalVolumes = 31,
        )
        assertEquals(1f, full!!, 0.0001f)

        val half = ProgressBumpPolicy.barFraction(
            type = SubjectType.MANGA,
            watchedEpisodes = 100,
            watchedVolumes = 10,
            totalEpisodes = 276,
            totalVolumes = 20,
        )
        assertEquals(0.5f, half!!, 0.0001f)
    }

    @Test
    fun barFraction_mangaWithoutVolumeProgress_usesEpisodeDenominator() {
        // 没在记卷进度时仍按集/话走，不能因为作品有总卷数就改口径
        val f = ProgressBumpPolicy.barFraction(
            type = SubjectType.MANGA,
            watchedEpisodes = 138,
            watchedVolumes = null,
            totalEpisodes = 276,
            totalVolumes = 31,
        )
        assertEquals(0.5f, f!!, 0.0001f)
    }

    @Test
    fun barFraction_animeUsesEpisodesAndClamps() {
        val f = ProgressBumpPolicy.barFraction(SubjectType.ANIME, 12, null, 12, null)
        assertEquals(1f, f!!, 0.0001f)
        val over = ProgressBumpPolicy.barFraction(SubjectType.ANIME, 99, null, 12, null)
        assertEquals(1f, over!!, 0.0001f)
        val zero = ProgressBumpPolicy.barFraction(SubjectType.ANIME, null, null, 12, null)
        assertEquals(0f, zero!!, 0.0001f)
    }

    @Test
    fun barFraction_unknownDenominator_isNullNotGuessed() {
        // 总卷数未知时不能回退到「话」当分母，也不能画一条错的进度条
        assertNull(ProgressBumpPolicy.barFraction(SubjectType.MANGA, 5, 3, 276, null))
        assertNull(ProgressBumpPolicy.barFraction(SubjectType.MANGA, 5, 3, 276, 0))
        assertNull(ProgressBumpPolicy.barFraction(SubjectType.ANIME, 5, null, null, null))
        assertNull(ProgressBumpPolicy.barFraction(SubjectType.MANGA, null, null, null, null))
    }

    @Test
    fun barFraction_gameAndMusic_haveNoBar() {
        assertNull(ProgressBumpPolicy.barFraction(SubjectType.GAME, 150, null, null, null))
        assertNull(ProgressBumpPolicy.barFraction(SubjectType.MUSIC, 3, null, 12, null))
    }

    @Test
    fun snapshot_totalVolumes_setsVolumeUpperBound() {
        val s = ProgressBumpPolicy.snapshot(
            type = SubjectType.MANGA,
            watchedEpisodes = null,
            watchedVolumes = 30,
            totalEpisodes = 276,
            totalVolumes = 31,
        )!!
        assertEquals(ProgressUnit.VOLUME, s.unit)
        assertEquals(31, s.total)
        // 加到上限就停住（不自动切换「看过」）
        assertEquals(31, ProgressBumpPolicy.next(s, 5))
    }
}
