package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SubjectRepository.mergeBiliSupplementary 回归测试。
 * 验证:远程详情(不含 bili 列)覆盖写库时,本地已存的 B 站评分不被抹掉。
 */
class SubjectRepositoryBiliMergeTest {

    private val remote = SubjectEntity(
        subjectId = 100L,
        title = "示例",
        type = SubjectType.ANIME,
        ratingScore = 8.5f,
        sourceId = "bangumi",
    )

    @Test
    fun `远程详情不含 bili 字段时保留本地 bili 评分`() {
        val cached = remote.copy(
            biliScore = 9.8f,
            biliRatingTotal = 84312,
            biliSeasonId = 3398,
        )
        val merged = SubjectRepository.mergeBiliSupplementary(remote, cached)

        assertEquals(9.8f, merged.biliScore ?: 0f, 0.001f)
        assertEquals(84312, merged.biliRatingTotal)
        assertEquals(3398, merged.biliSeasonId)
        // 其他字段仍以远端为准
        assertEquals(8.5f, merged.ratingScore ?: 0f, 0.001f)
    }

    @Test
    fun `远端 bili 字段非空时优先用远端`() {
        val cached = remote.copy(biliScore = 7.0f)
        val remoteWithBili = remote.copy(biliScore = 9.5f, biliRatingTotal = 100)
        val merged = SubjectRepository.mergeBiliSupplementary(remoteWithBili, cached)

        assertEquals(9.5f, merged.biliScore ?: 0f, 0.001f)
        assertEquals(100, merged.biliRatingTotal)
    }

    @Test
    fun `无本地缓存时返回远端`() {
        val merged = SubjectRepository.mergeBiliSupplementary(remote, null)
        assertEquals(8.5f, merged.ratingScore ?: 0f, 0.001f)
        assertNull(merged.biliScore)
        assertNull(merged.biliSeasonId)
    }
}