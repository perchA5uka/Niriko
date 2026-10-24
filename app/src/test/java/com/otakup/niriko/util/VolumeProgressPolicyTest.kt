package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单本判定的纯规则测试（A-X4）。
 *
 * 三条产品口径在这里变成可断言的判据：
 * 1. **书籍**：卷数缺失/0/1 都算单本，不显示卷进度；≥2 卷才显示；
 * 2. **漫画**：与书籍同一规则 —— 卷数 ≤1 也隐藏（产品口径不区分漫画 / 文字书）；`series == false` 时进度区仍隐藏；
 * 3. **其他类型**：永远不显示卷字段。
 */
class VolumeProgressPolicyTest {

    @Test
    fun `书籍单本不显示卷字段`() {
        assertFalse("总卷数未知", VolumeProgressPolicy.showVolumeField(SubjectType.BOOK, null))
        assertFalse("总卷数 0 视为未知", VolumeProgressPolicy.showVolumeField(SubjectType.BOOK, 0))
        assertFalse("单卷", VolumeProgressPolicy.showVolumeField(SubjectType.BOOK, 1))
    }

    @Test
    fun `多卷书籍仍显示卷字段`() {
        assertTrue(VolumeProgressPolicy.showVolumeField(SubjectType.BOOK, 2))
        assertTrue(VolumeProgressPolicy.showVolumeField(SubjectType.BOOK, 24))
    }

    @Test
    fun `漫画与无关类型`() {
        assertFalse("单卷漫画同样隐藏", VolumeProgressPolicy.showVolumeField(SubjectType.MANGA, 1))
        assertTrue("多卷漫画显示", VolumeProgressPolicy.showVolumeField(SubjectType.MANGA, 2))
        assertFalse(VolumeProgressPolicy.showVolumeField(SubjectType.ANIME, null))
        assertFalse(VolumeProgressPolicy.showVolumeField(SubjectType.GAME, null))
        assertFalse(VolumeProgressPolicy.showVolumeField(null, 12))
    }

    @Test
    fun `series为false优先判成单本_即使卷数很多`() {
        assertTrue(VolumeProgressPolicy.isSingleVolume(SubjectType.BOOK, 24, series = false))
        assertTrue(VolumeProgressPolicy.isSingleVolume(SubjectType.MANGA, 12, series = false))
        assertTrue("三次元的 series=false 也按单本", VolumeProgressPolicy.isSingleVolume(SubjectType.REAL, null, series = false))
    }

    @Test
    fun `书籍与漫画都按卷数判单本`() {
        assertTrue(VolumeProgressPolicy.isSingleVolume(SubjectType.BOOK, 1, series = null))
        assertTrue(VolumeProgressPolicy.isSingleVolume(SubjectType.BOOK, null, series = true))
        assertFalse(VolumeProgressPolicy.isSingleVolume(SubjectType.BOOK, 2, series = null))
        assertTrue("单卷漫画同样算单本", VolumeProgressPolicy.isSingleVolume(SubjectType.MANGA, 1, series = null))
        assertFalse(VolumeProgressPolicy.isSingleVolume(SubjectType.MANGA, 2, series = null))
        assertFalse(VolumeProgressPolicy.isSingleVolume(SubjectType.ANIME, null, series = null))
    }

    @Test
    fun `进度区与弹层判据一致_但进度区多看series`() {
        // 书籍：卷数 ≤1 时两处都藏
        assertFalse(VolumeProgressPolicy.showProgressSection(SubjectType.BOOK, 1, series = true))
        assertTrue(VolumeProgressPolicy.showProgressSection(SubjectType.BOOK, 2, series = true))
        // 漫画：单卷时两处都藏；多卷时两处都显示；series=false 仍优先隐藏
        assertFalse(VolumeProgressPolicy.showVolumeField(SubjectType.MANGA, 1))
        assertFalse(VolumeProgressPolicy.showProgressSection(SubjectType.MANGA, 1, series = null))
        assertTrue(VolumeProgressPolicy.showVolumeField(SubjectType.MANGA, 2))
        assertTrue(VolumeProgressPolicy.showProgressSection(SubjectType.MANGA, 2, series = null))
        assertFalse(VolumeProgressPolicy.showProgressSection(SubjectType.MANGA, 1, series = false))
        // 非书籍/漫画一律不展示进度区
        assertFalse(VolumeProgressPolicy.showProgressSection(SubjectType.ANIME, null, series = null))
        assertFalse(VolumeProgressPolicy.showProgressSection(SubjectType.GAME, null, series = null))
    }
}
