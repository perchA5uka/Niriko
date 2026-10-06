package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「看完」边沿判据（F14）的单测。
 *
 * §11.1 的验收原文是「完成**边沿**触发而非初次加载」。这条不能靠「只播一次」的补丁实现 ——
 * 它不是「播放次数」问题，而是「有没有发生边沿」问题。因此这里把边沿判据逐条钉死。
 */
class EpisodeCompletionPolicyTest {

    private fun snap(
        episodes: Int? = null,
        volumes: Int? = null,
        totalEpisodes: Int? = null,
        totalVolumes: Int? = null,
    ) = EpisodeCompletionPolicy.ProgressSnapshot(episodes, volumes, totalEpisodes, totalVolumes)

    @Test
    fun `动画`() {
        // 11/12 → 12/12：边沿成立
        assertTrue(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = 11, totalEpisodes = 12),
                after = snap(episodes = 12, totalEpisodes = 12),
            ),
        )
        // 12/12 → 12/12：**不是边沿**（反复保存同一份进度不重复庆祝）
        assertFalse(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = 12, totalEpisodes = 12),
                after = snap(episodes = 12, totalEpisodes = 12),
            ),
        )
        // 12/12 → 11/12：往回改也不是边沿
        assertFalse(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = 12, totalEpisodes = 12),
                after = snap(episodes = 11, totalEpisodes = 12),
            ),
        )
    }

    @Test
    fun `初次加载不触发_因为压根没有写入`() {
        // 「从没有记录」到「已经 12/12」：这是用户一次真实的写入，**应该**庆祝
        assertTrue(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = null, totalEpisodes = 12),
                after = snap(episodes = 12, totalEpisodes = 12),
            ),
        )
        // 而「读取到 12/12 之后再读一次」在数据层不会产生 before/after 对，
        // 因此不经过本判据 —— 这一条由调用点（只在写入后调用）保证，见 ViewModel 注释。
    }

    @Test
    fun `总量未知不伪造完成`() {
        assertFalse(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = 5, totalEpisodes = null),
                after = snap(episodes = 6, totalEpisodes = null),
            ),
        )
        assertFalse(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = 0, totalEpisodes = 0),
                after = snap(episodes = 0, totalEpisodes = 0),
            ),
        )
        // 写入值是 null（清空进度）也不算完成
        assertFalse(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = 11, totalEpisodes = 12),
                after = snap(episodes = null, totalEpisodes = 12),
            ),
        )
    }

    @Test
    fun `漫画按卷判断_不拿话数当分母`() {
        val type = SubjectType.MANGA
        // 31/31 卷 + 话数只填了 100：按卷 → 边沿成立
        assertTrue(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = 100, volumes = 30, totalEpisodes = 276, totalVolumes = 31),
                after = snap(episodes = 100, volumes = 31, totalEpisodes = 276, totalVolumes = 31),
                type = type,
            ),
        )
        // 话数写满但卷没写满：**不**庆祝（§6.4：卷与话是两个单位）
        assertFalse(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = 275, volumes = 10, totalEpisodes = 276, totalVolumes = 31),
                after = snap(episodes = 276, volumes = 10, totalEpisodes = 276, totalVolumes = 31),
                type = type,
            ),
        )
    }

    @Test
    fun `漫画没有总卷数时退回按集判断`() {
        val type = SubjectType.MANGA
        assertFalse(EpisodeCompletionPolicy.usesVolumes(type, snap(totalVolumes = null)))
        assertFalse(EpisodeCompletionPolicy.usesVolumes(type, snap(totalVolumes = 0)))
        assertTrue(EpisodeCompletionPolicy.usesVolumes(type, snap(totalVolumes = 31)))
        // 动画永远按集（即使数据里混进了 volumes）
        assertFalse(EpisodeCompletionPolicy.usesVolumes(SubjectType.ANIME, snap(totalVolumes = 12)))
        // 没有类型时也按集
        assertFalse(EpisodeCompletionPolicy.usesVolumes(null, snap(totalVolumes = 12)))
    }

    @Test
    fun `超过总量也算完成_不因为多填了几集就不庆祝`() {
        assertTrue(
            EpisodeCompletionPolicy.shouldCelebrate(
                before = snap(episodes = 11, totalEpisodes = 12),
                after = snap(episodes = 13, totalEpisodes = 12),
            ),
        )
    }

    @Test
    fun `进度文本按单位给出`() {
        assertEquals(
            "12/12 集",
            EpisodeCompletionPolicy.progressLabel(snap(episodes = 12, totalEpisodes = 12)),
        )
        assertEquals(
            "31/31 卷",
            EpisodeCompletionPolicy.progressLabel(
                snap(volumes = 31, totalVolumes = 31),
                type = SubjectType.MANGA,
            ),
        )
    }
}
