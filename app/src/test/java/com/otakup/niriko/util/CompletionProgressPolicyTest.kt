package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「标记看过」写入目标的纯规则测试（A-D1 写入侧）。
 *
 * 核心回归：一本 24 卷的书标「看过」，**不能**把 24 写进 `watchedEpisodes`
 * （那是话数，统计侧会当「已看 24 集」）。要写进 `watchedVolumes`，且不动已有话数。
 * 同时锁住「无卷时的旧行为」没被顺手改掉：总集数仍写 `watchedEpisodes`，游戏/音乐仍清空。
 */
class CompletionProgressPolicyTest {

    @Test
    fun `多卷书标看过_写卷数且不动话数`() {
        val target = CompletionProgressPolicy.targetFor(
            type = SubjectType.BOOK, status = WatchStatus.COMPLETED,
            totalEpisodes = 276, volumes = 24,
            currentEpisodes = 3, currentVolumes = null,
        )
        assertEquals(CompletionProgressPolicy.Target(watchedEpisodes = 3, watchedVolumes = 24), target)
    }

    @Test
    fun `多卷书没有旧话数时_话数保持为空`() {
        val target = CompletionProgressPolicy.targetFor(
            type = SubjectType.BOOK, status = WatchStatus.COMPLETED,
            totalEpisodes = null, volumes = 24,
            currentEpisodes = null, currentVolumes = null,
        )
        assertEquals(CompletionProgressPolicy.Target(null, 24), target)
    }

    @Test
    fun `漫画同样按卷写`() {
        val target = CompletionProgressPolicy.targetFor(
            type = SubjectType.MANGA, status = WatchStatus.COMPLETED,
            totalEpisodes = 50, volumes = 5,
            currentEpisodes = null, currentVolumes = null,
        )
        assertEquals(CompletionProgressPolicy.Target(null, 5), target)
    }

    @Test
    fun `无卷时保持旧行为_写总集数`() {
        val target = CompletionProgressPolicy.targetFor(
            type = SubjectType.BOOK, status = WatchStatus.COMPLETED,
            totalEpisodes = 12, volumes = null,
            currentEpisodes = null, currentVolumes = 2,
        )
        assertEquals(CompletionProgressPolicy.Target(12, 2), target)

        val anime = CompletionProgressPolicy.targetFor(
            type = SubjectType.ANIME, status = WatchStatus.COMPLETED,
            totalEpisodes = 12, volumes = null,
            currentEpisodes = 5, currentVolumes = null,
        )
        assertEquals(CompletionProgressPolicy.Target(12, null), anime)
    }

    @Test
    fun `卷数为0按未知处理_回退总集数`() {
        // 老逻辑 volumes ?: totalEpisodes 对 0 会得到 0 → takeIf 清空；这里保持同值以免顺带改行为
        val target = CompletionProgressPolicy.targetFor(
            type = SubjectType.BOOK, status = WatchStatus.COMPLETED,
            totalEpisodes = 12, volumes = 0,
            currentEpisodes = 4, currentVolumes = 1,
        )
        assertEquals(CompletionProgressPolicy.Target(null, 1), target)
    }

    @Test
    fun `游戏与音乐标看过_保持旧行为清空集数`() {
        assertEquals(
            CompletionProgressPolicy.Target(null, null),
            CompletionProgressPolicy.targetFor(SubjectType.GAME, WatchStatus.COMPLETED, null, null, 600, null),
        )
        assertEquals(
            CompletionProgressPolicy.Target(null, null),
            CompletionProgressPolicy.targetFor(SubjectType.MUSIC, WatchStatus.COMPLETED, null, null, 9, null),
        )
    }

    @Test
    fun `非完成态原样保留两个进度`() {
        assertEquals(
            CompletionProgressPolicy.Target(5, 2),
            CompletionProgressPolicy.targetFor(SubjectType.BOOK, WatchStatus.WATCHING, 24, 3, 5, 2),
        )
        assertEquals(
            CompletionProgressPolicy.Target(5, 2),
            CompletionProgressPolicy.targetFor(SubjectType.ANIME, WatchStatus.DROPPED, 12, null, 5, 2),
        )
    }

    @Test
    fun `想看清空集数但保留卷数`() {
        assertEquals(
            CompletionProgressPolicy.Target(null, 3),
            CompletionProgressPolicy.targetFor(SubjectType.BOOK, WatchStatus.PLAN_TO_WATCH, 24, 3, 5, 3),
        )
    }
}
