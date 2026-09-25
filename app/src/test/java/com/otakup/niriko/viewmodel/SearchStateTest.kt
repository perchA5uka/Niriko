package com.otakup.niriko.viewmodel

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.repository.SearchOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索状态机纯逻辑单测（第 6 轮 F1/F2/F5）。
 *
 * SubjectSearchViewModel 本身在 JVM 单测里无法实例化（viewModelScope 需要主线程），
 * 因此把「活跃查询 / 取消不写状态 / 部分失败仍出结果 / 全失败才算失败」这些语义
 * 抽成纯函数后在这里钉死 —— 它们是本轮搜索修复的核心不变量。
 */
class SearchStateTest {

    private fun subject(id: Long, title: String = "作品$id") = SubjectEntity(
        subjectId = id,
        title = title,
        type = SubjectType.ANIME,
    )

    // ==================== F1 活跃查询 ====================

    /** 有输入时活跃查询取输入值（回归 F1：输入同时写回两条流）。 */
    @Test
    fun activeQueryPrefersInput() {
        assertEquals("巨人", resolveActiveQuery("巨人", ""))
        assertEquals("巨人", resolveActiveQuery("巨人", "旧关键词"))
    }

    /** 没有输入时回退到 uiState 的查询（人物模式只写 uiState）。 */
    @Test
    fun activeQueryFallsBackToState() {
        assertEquals("旧关键词", resolveActiveQuery("", "旧关键词"))
        assertEquals("", resolveActiveQuery("", ""))
    }

    /**
     * 复审修复：人物模式以 uiState.query 为准，且回切作品模式不能拿到上一次作品搜索的旧词。
     *
     * 背景：人物分支只写 uiState.query（不写 queryInput），若仍让输入流优先，
     * 从人物模式切回作品模式时 activeQuery() 会返回残留的旧作品搜索词。
     */
    @Test
    fun activeQueryPrefersStateQueryInPersonMode() {
        assertEquals(
            "人物关键词",
            resolveActiveQuery(input = "上一次作品搜索词", stateQuery = "人物关键词", isPersonSearch = true),
        )
        assertEquals(
            "新输入",
            resolveActiveQuery(input = "新输入", stateQuery = "旧词", isPersonSearch = false),
        )
        // 人物模式下即便输入流为空，也以 uiState 为准（两者一致时不产生差异）
        assertEquals(
            "人物关键词",
            resolveActiveQuery(input = "", stateQuery = "人物关键词", isPersonSearch = true),
        )
    }

    /**
     * setType 方向（round 4 复审修复，R1-residual）：人物模式下点类型行必须用「输入框里显示的词」。
     *
     * 复现链（t16 finding 原文）：作品搜「巨人」（queryInput = 巨人）→ 切人物改成「花泽」
     * （人物分支只写 uiState.query，queryInput 仍是「巨人」）→ 点类型行「动画」。
     * 修复前 setType 先翻 isPersonSearch = false 再调 activeQuery()，判定落到作品模式、
     * 按「输入流优先」取到陈旧的「巨人」（allResults 非空时还会直接客户端过滤旧结果）。
     *
     * 注意：SubjectSearchViewModel 在本项目的 JVM 单测里无法实例化（viewModelScope 需要
     * Android 主线程，且无 Robolectric/mockito/coroutines-test），所以这里驱动的是
     * **setType 自己的取词入口** [typeSwitchPlan]，并额外把「修复前的顺序」钉成反向断言，
     * 防止有人再把调用顺序改回去。
     */
    @Test
    fun typeSwitchAfterPersonModeUsesVisibleQuery() {
        val plan = typeSwitchPlan(input = "巨人", stateQuery = "花泽")

        assertEquals("必须用输入框里显示的「花泽」，而不是残留的「巨人」", "花泽", plan.query)
        assertFalse("点类型行必然退出人物模式", plan.isPersonSearch)

        // 反向锁：修复前的顺序（先翻标志 → activeQuery() 按当前模式分支）会取到陈旧的「巨人」
        val legacyOrderQuery = resolveActiveQuery(input = "巨人", stateQuery = "花泽", isPersonSearch = false)
        assertEquals("巨人", legacyOrderQuery)
        assertNotEquals("新旧顺序的结果必须不同，否则说明回归没修", legacyOrderQuery, plan.query)

        // 两边都空 → 空白，调用方据此走趋势/缓存分支
        assertEquals("", typeSwitchPlan(input = "", stateQuery = "").query)
        // 人物模式没输入过、只有旧作品词时，uiState.query 为空 → 不误用残留输入
        assertEquals("", typeSwitchPlan(input = "巨人", stateQuery = "").query)
    }

    /**
     * round 3 复审修复（R1）：模式切换前的取值方向必须钉死。
     *
     * 之前的用例只断言了 isPersonSearch = true 方向的取值，覆盖不到「调用顺序」——
     * setPersonSearch 先翻转标志再取活跃查询，回切作品模式时判定已变成 false，
     * 于是落回 input.ifBlank { state } 取到陈旧的 queryInput（搜「巨人」→ 切人物改「花泽」→
     * 切回作品仍用「巨人」重查，而输入框显示「花泽」）。把纯函数的两个方向都断言掉。
     */
    @Test
    fun queryAfterModeSwitchPinsBothDirections() {
        // 回切作品模式：必须以人物模式写在 uiState 里的关键词为准，不能取 queryInput 的残留
        assertEquals(
            "人物关键词",
            queryAfterModeSwitch(enabled = false, input = "上一次作品搜索词", stateQuery = "人物关键词"),
        )
        // 切到人物模式：用切换前的作品查询（输入流优先）
        assertEquals(
            "上一次作品搜索词",
            queryAfterModeSwitch(enabled = true, input = "上一次作品搜索词", stateQuery = "人物关键词"),
        )
        // 切到人物且输入流为空时回退 uiState
        assertEquals(
            "人物关键词",
            queryAfterModeSwitch(enabled = true, input = "", stateQuery = "人物关键词"),
        )
        // 回切时即便输入流为空也取 uiState（两者一致时不产生差异）
        assertEquals(
            "人物关键词",
            queryAfterModeSwitch(enabled = false, input = "", stateQuery = "人物关键词"),
        )
    }

    /** 有输入时改筛选必须重查搜索 —— 改造前四个 setter 读 uiState.query（恒为空）。 */
    @Test
    fun filterChangeResearchesWhenQueryPresent() {
        assertTrue(shouldResearchOnFilterChange(resolveActiveQuery("巨人", "")))
    }

    /** 没有输入时改筛选只刷新趋势区。 */
    @Test
    fun filterChangeOnlyRefreshesTrendingWithoutQuery() {
        assertFalse(shouldResearchOnFilterChange(resolveActiveQuery("", "")))
    }

    // ==================== F2 取消与失败分开 ====================

    /** 业务异常 → 失败（带原因）。 */
    @Test
    fun businessExceptionBecomesFailure() = runBlocking {
        val fetched = attemptRemote<Int> { throw IllegalStateException("boom") }
        assertTrue(fetched.isFailure)
        assertEquals("boom", fetched.error?.message)
        assertNull(fetched.value)
    }

    /** 取消原样抛出，不能被当成失败（catch 顺序：CancellationException 在 Exception 之前）。 */
    @Test
    fun cancellationIsRethrownNotRecordedAsFailure() = runBlocking {
        var wroteState = 0
        val job = launch(Dispatchers.Default) {
            try {
                attemptRemote<Int> {
                    delay(10_000)
                    1
                }
                wroteState++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                wroteState++
            }
        }
        delay(80)
        job.cancel()
        job.join()
        assertTrue("取消后 job 必须是 cancelled", job.isCancelled)
        assertEquals("取消不允许写任何状态", 0, wroteState)
    }

    /** 取消时既不清空已有结果，也不写 error。 */
    @Test
    fun cancellationKeepsExistingResults() = runBlocking {
        var existingResults = listOf(subject(1))
        var error: String? = null
        val job = launch(Dispatchers.Default) {
            try {
                attemptRemote<Int> { delay(10_000); 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "搜索失败，请重试"
                existingResults = emptyList()
            }
        }
        delay(80)
        job.cancel()
        job.join()
        assertNull(error)
        assertEquals(1, existingResults.size)
    }

    // ==================== F5 失败要说话 ====================

    /** 部分插件成功 → 照常出结果（不置 error）。 */
    @Test
    fun partialFailureStillShowsResults() {
        val attempts = listOf(
            SearchAttempt(error = "与信息源断开连接"),
            SearchAttempt(results = listOf(subject(1), subject(2)), total = 2),
        )
        val batch = SearchBatch(
            attempts = attempts,
            total = 2,
            results = mergeSearchAttempts(attempts),
        )
        assertFalse("只要有一路成功就不算失败", batch.allFailed)
        assertEquals(2, batch.results.size)
    }

    /** 全部失败 → allFailed（UI 显示网络失败 + 重试）。 */
    @Test
    fun allFailureBecomesError() {
        val attempts = listOf(
            SearchAttempt(error = "与信息源断开连接"),
            SearchAttempt(error = "与网络断开连接"),
        )
        val batch = SearchBatch(
            attempts = attempts,
            total = 0,
            results = mergeSearchAttempts(attempts),
        )
        assertTrue(batch.allFailed)
        assertEquals("与信息源断开连接", batch.firstFailure)
        assertTrue(batch.results.isEmpty())
    }

    /** 没有任何请求发生 → 不算失败。 */
    @Test
    fun noAttemptIsNotFailure() {
        val batch = SearchBatch(attempts = emptyList(), total = 0, results = emptyList())
        assertFalse(batch.allFailed)
        assertNull(batch.firstFailure)
    }

    /** 正常返回空结果 ≠ 网络失败。 */
    @Test
    fun emptyResultIsNotFailure() {
        val attempts = listOf(SearchAttempt(error = null, results = emptyList(), total = 0))
        val batch = SearchBatch(attempts = attempts, total = 0, results = emptyList())
        assertFalse(batch.allFailed)
    }

    /** 离线兜底：结果照常出，但要标注（offline）而不是全屏错误。 */
    @Test
    fun offlineFallbackIsMarkedButStillResults() {
        val outcome = SearchOutcome(
            results = listOf(subject(7)),
            total = 1,
            remoteFailed = true,
            offline = true,
            failureReason = "与网络断开连接",
        )
        val attempt = RemoteFetch(value = outcome).toAttempt()
        assertTrue(attempt.offline)
        assertEquals(1, attempt.results.size)
        assertEquals("与网络断开连接", attempt.error)

        val batch = SearchBatch(
            attempts = listOf(attempt),
            total = attempt.total,
            results = mergeSearchAttempts(listOf(attempt)),
        )
        assertFalse("本地有命中就不是全失败", batch.allFailed)
        assertTrue(batch.hasOffline)
    }

    /** 远程失败且本地无命中 → 转成失败文案（全失败）。 */
    @Test
    fun remoteFailureWithoutLocalFallbackIsFailure() {
        val outcome = SearchOutcome(
            results = emptyList(),
            total = 0,
            remoteFailed = true,
            offline = false,
            failureReason = "网络请求失败，请重试",
        )
        val attempt = RemoteFetch(value = outcome).toAttempt()
        assertEquals("网络请求失败，请重试", attempt.error)
        assertFalse(attempt.offline)
        assertTrue(SearchBatch(listOf(attempt), 0, emptyList()).allFailed)
    }

    /** 合并去重按 subjectId，保留首次出现。 */
    @Test
    fun mergeKeepsFirstOccurrence() {
        val attempts = listOf(
            SearchAttempt(results = listOf(subject(1, "先出现"), subject(2)), total = 2),
            SearchAttempt(results = listOf(subject(1, "后出现"), subject(3)), total = 2),
        )
        val merged = mergeSearchAttempts(attempts)
        assertEquals(3, merged.size)
        assertEquals("先出现", merged.first().title)
    }
}
