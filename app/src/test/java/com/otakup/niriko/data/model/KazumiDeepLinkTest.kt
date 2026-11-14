package com.otakup.niriko.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kazumi 深链格式单测。
 *
 * 格式来自 Kazumi 2.4.0 的 AndroidManifest（scheme=kazumi, host=bangumi），
 * 对应 Predidit/Kazumi#2628。这里锁死字符串，避免以后手改拼错前缀 ——
 * 拼错不会崩，只会静默退化成「只拉起 App」，很难发现。
 */
class KazumiDeepLinkTest {

    @Test
    fun `按 Bangumi 条目 ID 生成深链`() {
        assertEquals("kazumi://bangumi/123", kazumiDeepLinkUrl(123))
    }

    @Test
    fun `大 ID 不丢精度`() {
        assertEquals("kazumi://bangumi/117393753899831", kazumiDeepLinkUrl(117393753899831L))
    }
}
