package com.otakup.niriko.viewmodel

import com.otakup.niriko.data.repository.VndbRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * ExtendedSnapshot 的「写入 ↔ 恢复」完备性单测（B14：ExtendedSnapshot 补全）。
 *
 * 审计方式不是肉眼比对 70 多个字段，而是把不变量写成断言：
 * 1. 快照里每个字段都必须在 SubjectDetailUiState 里有同名同类型的字段（否则恢复不了）；
 * 2. UiState 里每个字段都必须被**显式分类** —— 要么进快照，要么在 `transientOrDerived` 清单里；
 *    新增结果类字段却忘了进快照时，这条会失败；
 * 3. 往返一致：写进快照再恢复，值必须逐字段相同。
 *
 * 为什么这三条重要：快照只覆盖「缓存命中」路径，而缓存命中会提前 `return` 掉产生数据的外部源。
 * 于是漏一个字段的后果不是「少显示一点」，而是**二次进详情页时那一块直接消失**。
 */
class SubjectDetailSnapshotTest {

    /**
     * UiState 里**刻意不进快照**的字段。
     * 分类理由见 `SubjectDetailSnapshot.kt` 里 `toExtendedSnapshot` 的 KDoc。
     */
    private val transientOrDerived = setOf(
        // 由 loadCollectionState 单独加载，或纯加载态
        "subject", "isLoading", "isRefreshing", "isUpdating", "isExtendedLoading",
        "isInCollection", "collectionId", "collectionCreateTime", "currentStatus",
        "watchedEpisodes", "watchedVolumes", "isPrivate", "myRating", "personalTags",
        "personalImpression", "remark", "watchedTrackIds", "startDate", "finishDate",
        // 一次性反馈 / 错误
        "error", "snackbarMessage", "vndbManualMessage", "anilistManualMessage",
        "tmdbManualQuery", "tmdbManualLoading", "tmdbManualMessage",
        // 加载态
        "imdbEpisodesLoading", "doubanLoading", "coverCandidatesLoading",
        // 派生值：恢复路径上用 snapshot.ratingDistribution 重算
        "ratingDispute", "localPercentile",
        // 与「打开封面选择器」这个交互绑定、按需加载
        "coverCandidates",
    )

    private fun fieldNames(cls: Class<*>): List<String> = cls.declaredFields
        .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
        .map { it.name }

    @Test
    fun `快照的每个字段在 UI 状态里都有同名同类型字段`() {
        val uiTypes = SubjectDetailUiState::class.java.declaredFields.associate { it.name to it.type }
        val problems = mutableListOf<String>()
        for (f in ExtendedSnapshot::class.java.declaredFields) {
            if (f.isSynthetic || Modifier.isStatic(f.modifiers)) continue
            val uiType = uiTypes[f.name]
            if (uiType == null) {
                problems += "${f.name}: 快照里有、UI 状态里没有（恢复时会丢）"
            } else if (uiType != f.type) {
                problems += "${f.name}: 类型不一致 UI=$uiType SNAPSHOT=${f.type}"
            }
        }
        assertTrue("快照与 UI 状态的字段对不上：$problems", problems.isEmpty())
    }

    @Test
    fun `UI 状态的每个字段都被显式分类_新增结果字段忘了进快照会失败`() {
        val snapshotFields = fieldNames(ExtendedSnapshot::class.java).toSet()
        val unclassified = fieldNames(SubjectDetailUiState::class.java)
            .filter { it !in snapshotFields && it !in transientOrDerived }
        assertTrue(
            "这些 UiState 字段既不在快照里、也不在 transientOrDerived 清单里 —— " +
                "要么把它加进 ExtendedSnapshot，要么说清它是瞬时/派生值：$unclassified",
            unclassified.isEmpty(),
        )
    }

    @Test
    fun `往返一致_VNDB 匹配结果簇不丢`() {
        // 报告过的缺陷：这 4 个字段由 loadVndbSupplement 写入，此前不在快照里 ——
        // 缓存命中路径会直接 return 掉该函数，于是二次进详情页时
        //「VNDB 关联作品」与「候选匹配理由」整块消失（第一次进有、退出去再进来就没了）。
        val state = SubjectDetailUiState(
            vndbRelations = listOf(
                VndbRepository.RelationWithTitle(
                    vndbId = "v12345",
                    relation = "seq",
                    relationLabel = "续作",
                    title = "タイトル",
                    ctitle = "中文名",
                    released = "2020-01-01",
                    rating = 80.0,
                    coverUrl = "https://example.invalid/cover.jpg",
                    official = true,
                ),
            ),
            vndbCandidateReasons = mapOf("v54321" to listOf("标题完全一致", "平台重叠（win）")),
            vndbRawMatchCount = 7,
            vndbMatchReasons = listOf("infobox 明确 ID"),
        )
        val restored = SubjectDetailUiState().withExtendedSnapshot(state.toExtendedSnapshot())
        assertEquals(state.vndbRelations, restored.vndbRelations)
        assertEquals(state.vndbCandidateReasons, restored.vndbCandidateReasons)
        assertEquals(state.vndbRawMatchCount, restored.vndbRawMatchCount)
        assertEquals(state.vndbMatchReasons, restored.vndbMatchReasons)
    }

    @Test
    fun `往返一致_其余结果簇与标量字段也逐字段相同`() {
        val state = SubjectDetailUiState(
            episodeStills = mapOf(1L to "still-1", 2L to "still-2"),
            doubanThumbs = listOf("d1", "d2"),
            doubanBoundId = "1292052",
            doubanEnabled = true,
            anitabiCity = "东京",
            anitabiPointsLength = 12,
            anitabiImagesLength = 34,
            tmdbSupported = true,
            imdbEpisodeEntryEnabled = true,
            myEpisodeRatings = mapOf(1L to 8.5f),
        )
        // 接收方故意带着「扩展数据正在加载」的状态：恢复是纯数据映射，不该顺手改加载态
        val restored = SubjectDetailUiState(isExtendedLoading = true)
            .withExtendedSnapshot(state.toExtendedSnapshot())
        assertEquals(state.episodeStills, restored.episodeStills)
        assertEquals(state.doubanThumbs, restored.doubanThumbs)
        assertEquals(state.doubanBoundId, restored.doubanBoundId)
        assertEquals(state.doubanEnabled, restored.doubanEnabled)
        assertEquals(state.anitabiCity, restored.anitabiCity)
        assertEquals(state.anitabiPointsLength, restored.anitabiPointsLength)
        assertEquals(state.anitabiImagesLength, restored.anitabiImagesLength)
        assertEquals(state.tmdbSupported, restored.tmdbSupported)
        assertEquals(state.imdbEpisodeEntryEnabled, restored.imdbEpisodeEntryEnabled)
        assertEquals(state.myEpisodeRatings, restored.myEpisodeRatings)
        // 加载态不属于快照：恢复不动它，由调用方（缓存命中路径）自己置 false
        assertTrue("恢复不应顺手改加载态", restored.isExtendedLoading)
    }
}
