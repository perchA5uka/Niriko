package com.otakup.niriko.viewmodel

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.SuggestionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索建议链路单测（第 6 轮 F4）：四态判定 + 合并去重。
 *
 * 改造前建议只有「有 / 无」两种表现，且远程建议整体限时 2.5s —— 弱网下必然为空，
 * UI 只能什么都不显示（用户看到的正是「搜索建议一直不展示」）。
 */
class SuggestionMergeTest {

    private fun subject(id: Long, title: String, titleCn: String? = null) = SubjectEntity(
        subjectId = id,
        title = title,
        titleCN = titleCn,
        type = SubjectType.ANIME,
    )

    // ==================== 四态 ====================

    @Test
    fun disabledWinsOverEverything() {
        assertEquals(
            SuggestionState.DISABLED,
            suggestionStateOf(enabled = false, query = "巨人", itemCount = 3, remoteFailed = false),
        )
    }

    @Test
    fun blankQueryIsNoInput() {
        assertEquals(
            SuggestionState.NO_INPUT,
            suggestionStateOf(enabled = true, query = "  ", itemCount = 0, remoteFailed = false),
        )
    }

    @Test
    fun itemsMeanAvailable() {
        assertEquals(
            SuggestionState.AVAILABLE,
            suggestionStateOf(enabled = true, query = "巨人", itemCount = 2, remoteFailed = true),
        )
    }

    @Test
    fun networkFailureIsDistinctFromNoMatch() {
        assertEquals(
            SuggestionState.NETWORK_FAILED,
            suggestionStateOf(enabled = true, query = "巨人", itemCount = 0, remoteFailed = true),
        )
        assertEquals(
            SuggestionState.NO_MATCH,
            suggestionStateOf(enabled = true, query = "巨人", itemCount = 0, remoteFailed = false),
        )
    }

    @Test
    fun noticeExplainsEveryEmptyStateAndStaysSilentWhenUseful() {
        assertNull(suggestionNoticeOf(SuggestionState.AVAILABLE))
        assertNull(suggestionNoticeOf(SuggestionState.NO_INPUT))
        assertNotNull(suggestionNoticeOf(SuggestionState.DISABLED))
        assertNotNull(suggestionNoticeOf(SuggestionState.NO_MATCH))
        assertNotNull(suggestionNoticeOf(SuggestionState.NETWORK_FAILED))
        // 三种「没有建议」的原因必须是不同的文案，否则 UI 说不清是哪一种
        val notices = listOf(
            suggestionNoticeOf(SuggestionState.DISABLED),
            suggestionNoticeOf(SuggestionState.NO_MATCH),
            suggestionNoticeOf(SuggestionState.NETWORK_FAILED),
        )
        assertEquals(3, notices.toSet().size)
    }

    // ==================== 本地部分 ====================

    @Test
    fun localItemsPutHistoryFirstAndSkipDuplicateTitles() {
        val items = buildSuggestionItems(
            localSubjects = listOf(subject(1, "巨人"), subject(2, "进击的巨人")),
            historyMatches = listOf("巨人"),
        )
        assertTrue(items.first() is SuggestionItem.HistoryKeyword)
        // 标题与历史关键词相同 → 不重复展示
        assertEquals(2, items.size)
        assertEquals("进击的巨人", (items[1] as SuggestionItem.LocalSubject).subject.title)
    }

    @Test
    fun localItemsAreCappedAtThreeEach() {
        val items = buildSuggestionItems(
            localSubjects = (1L..10L).map { subject(it, "作品" + it) },
            historyMatches = listOf("a", "b", "c", "d"),
        )
        assertEquals(6, items.size)
    }

    // ==================== 合并去重 ====================

    @Test
    fun mergeKeepsLocalFirstThenDedupesRemote() {
        val local = listOf(
            SuggestionItem.HistoryKeyword("巨人"),
            SuggestionItem.LocalSubject(subject(1, "本地命中")),
        )
        val remote = listOf(
            subject(1, "本地命中"),
            subject(2, "进击的巨人"),
            subject(3, "巨人之星"),
            subject(4, "巨人 最终季"),
        )
        val merged = mergeSuggestions(local, remote, history = listOf("巨人"), maxRemote = 2)
        // 本地 2 条 + 远程最多 2 条；id=1 与本地重复、标题命中历史关键词的条目被剔除
        assertEquals(4, merged.size)
        assertTrue(merged[0] is SuggestionItem.HistoryKeyword)
        val remoteItems = merged.filterIsInstance<SuggestionItem.RemoteSuggestion>()
        assertEquals(2, remoteItems.size)
        assertEquals(listOf(2L, 3L), remoteItems.map { it.subject.subjectId })
    }

    @Test
    fun mergeIsIdempotentForDuplicateRemoteIds() {
        val remote = listOf(subject(9, "同名作品"), subject(9, "同名作品"))
        val merged = mergeSuggestions(emptyList(), remote, history = emptyList(), maxRemote = 5)
        assertEquals(1, merged.size)
    }

    @Test
    fun mergeSkipsRemoteTitleEqualToHistory() {
        val merged = mergeSuggestions(
            local = emptyList(),
            remote = listOf(subject(5, "巨人")),
            history = listOf("巨人"),
            maxRemote = 5,
        )
        assertTrue(merged.isEmpty())
    }
}
