package com.otakup.niriko.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchStatusVerbTest {
    @Test
    fun verbsByType() {
        assertEquals("在看", WatchStatus.WATCHING.verbFor(SubjectType.ANIME))
        assertEquals("在读", WatchStatus.WATCHING.verbFor(SubjectType.MANGA))
        assertEquals("在玩", WatchStatus.WATCHING.verbFor(SubjectType.GAME))
        assertEquals("在听", WatchStatus.WATCHING.verbFor(SubjectType.MUSIC))
        assertEquals("读过", WatchStatus.COMPLETED.verbFor(SubjectType.BOOK))
        assertEquals("想看", WatchStatus.PLAN_TO_WATCH.verbFor(SubjectType.ANIME))
    }
}
