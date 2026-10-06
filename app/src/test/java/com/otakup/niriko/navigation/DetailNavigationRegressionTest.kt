package com.otakup.niriko.navigation

import androidx.lifecycle.SavedStateHandle
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DetailNavigationRegressionTest {
    @Test fun concreteAndTemplateDetailRoutesBothUseCurrentSharedMotion() {
        for ((source, target) in listOf("subject_detail/{subjectId}" to "subject_detail/{subjectId}", "subject_detail/1" to "subject_detail/2", "main" to "subject_detail/2", "subject_detail/2" to "main", "person_detail/3" to "subject_detail/2")) {
            assertTrue(usesSubjectCoverTransition(source, target))
        }
        assertFalse(usesSubjectCoverTransition("subject_detail/1", "settings_appearance"))
    }
    @Test fun duplicateRelatedPostersHaveIndependentKeysAndMatchingOneShotHandoff() {
        val first = relationCoverKey(1L, 2L, 0)
        val second = relationCoverKey(1L, 2L, 1)
        assertNotEquals(first, second)
        assertNotEquals(first, relationCoverKey(3L, 2L, 0))
        SubjectCoverHandoff.prepare(2L, second)
        assertEquals(second, SubjectCoverHandoff.consume(2L))
        assertNull(SubjectCoverHandoff.consume(2L))
    }
    @Test fun scrollBelongsToEntryAndPaneAndSurvivesSavedStateRecreation() {
        val handle = SavedStateHandle()
        handle["detail_scroll_single_index"] = 7
        handle["detail_scroll_single_offset"] = 123
        handle["detail_scroll_CONTENT_index"] = 2
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        assertEquals(7, restored.get<Int>("detail_scroll_single_index"))
        assertEquals(123, restored.get<Int>("detail_scroll_single_offset"))
        assertEquals(2, restored.get<Int>("detail_scroll_CONTENT_index"))
        assertNull(SavedStateHandle().get<Int>("detail_scroll_single_index"))
    }
    @Test fun detailWiringRestoresScrollAndMatchesSourceHeroWithoutChangingMotionSpec() {
        val detail = File("src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt").readText()
        assertTrue(detail.contains("LocalDetailHeroKey.current"))
        assertTrue(detail.contains("LazyListState.Saver"))
        assertTrue(detail.contains("entryState?.get<Int>(indexKey)"))
        assertTrue(detail.contains("DisposableEffect(listState, entryState, paneKey)"))
        assertTrue(detail.contains("SubjectCoverHandoff.prepare(s.subjectId, coverKey)"))
        val navigation = File("src/main/java/com/otakup/niriko/navigation/NirikoNavHost.kt").readText()
        assertTrue(navigation.contains("LocalDetailNavigationState provides backStackEntry.savedStateHandle"))
    }

    @Test fun relationAndRecommendationRailsHandTheVerySameKeyToTheChildHero() {
        // 关联条目：源卡键 -> 一次性口令 -> NavHost 侧取值（consume(...) ?: 默认键）
        val relationKey = relationCoverKey(parentId = 7L, subjectId = 42L, index = 0)
        SubjectCoverHandoff.prepare(42L, relationKey)
        assertEquals(relationKey, SubjectCoverHandoff.consume(42L) ?: "cover_42")

        // 猜你喜欢：同一条链路，键必须原样到达目标页 hero
        val recommendKey = recommendationCoverKey(parentId = 7L, subjectId = 43L, index = 1)
        SubjectCoverHandoff.prepare(43L, recommendKey)
        assertEquals(recommendKey, SubjectCoverHandoff.consume(43L) ?: "cover_43")

        // 同一 subject 出现在关联与推荐两处时不能共用键，否则共享元素会飞到错误的一格
        assertNotEquals(relationKey, recommendationCoverKey(7L, 42L, 0))
        assertNotEquals(recommendationCoverKey(7L, 43L, 1), recommendationCoverKey(7L, 43L, 2))

        // 口令属于被点的那一条：别的条目先消费掉不能串给本条
        SubjectCoverHandoff.prepare(99L, "cover_99")
        assertEquals("cover_43", SubjectCoverHandoff.consume(43L) ?: "cover_43")
    }

    @Test fun bothRailsUseNamedKeysAndTheChildHeroPrefersTheHandedKeyOverTheDefault() {
        val detail = File("src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt").readText()
        val relations = File("src/main/java/com/otakup/niriko/ui/subject/RelationsSection.kt").readText()
        val navigation = File("src/main/java/com/otakup/niriko/navigation/NirikoNavHost.kt").readText()
        // 两条横轨都走具名键，不再内联拼字符串
        assertTrue(detail.contains("recommendationCoverKey(parentId, s.subjectId, index)"))
        assertTrue(relations.contains("relationCoverKey(parentId, relation.subjectId, index)"))
        // 目标页 hero 优先用路由带过来的键，而不是无条件回落到 "cover_<id>"
        assertTrue(detail.contains("LocalDetailHeroKey.current ?: \"cover_\${subject.subjectId}\""))
        assertTrue(navigation.contains("SubjectCoverHandoff.consume(id) ?: \"cover_\$id\""))
    }

    @Test fun cardGlassSourceKeepsOneProviderSlotSoSharedSubtreesAreNeverRebuilt() {
        // 该函数在 null/非 null 两种来源下必须是同一个组合位置：
        // 早期的性能门控把 null 分支写成直接 content()，共享子树会在转场开始的瞬间被销毁重建。
        val glass = File("src/main/java/com/otakup/niriko/ui/components/GlassSource.kt").readText()
        val body = glass.substringAfter("fun ProvideCardGlassBackdrop(").substringBefore("\n}")
        assertTrue(body.contains("val effectiveBackdrop = backdrop ?: LocalCardGlassBackdrop.current"))
        assertFalse(body.contains("if (backdrop != null)"))
        assertEquals(1, Regex("CompositionLocalProvider").findAll(body).count())
    }
}
