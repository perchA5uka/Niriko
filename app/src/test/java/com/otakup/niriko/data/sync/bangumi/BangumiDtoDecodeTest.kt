package com.otakup.niriko.data.sync.bangumi

import com.otakup.niriko.data.remote.bangumi.dto.CollectionsResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.UserCollectionDto
import com.otakup.niriko.data.remote.bangumi.dto.UserMeDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DTO 契约解码测试(步骤1验收):用贴近官方响应的样例 JSON 验证不丢字段。
 * 结构对齐 Bangumi-master types/response.ts 与 Kazumi bangumi_api.dart。
 */
class BangumiDtoDecodeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `me 解码成功且不丢字段`() {
        val dto = json.decodeFromString<UserMeDto>(
            """
            {
              "id": 12345,
              "username": "sai",
              "nickname": "Sai",
              "avatar": { "large": "https://lain.bgm.tv/pic/user/l/000/00/00/1.jpg" },
              "sign": "hello",
              "user_group": 11
            }
            """.trimIndent(),
        )
        assertEquals(12345L, dto.id)
        assertEquals("sai", dto.username)
        assertEquals("Sai", dto.nickname)
        assertEquals("https://lain.bgm.tv/pic/user/l/000/00/00/1.jpg", dto.avatar?.large)
        assertEquals("hello", dto.sign)
        assertEquals(11, dto.userGroup)
    }

    @Test
    fun `collections 分页响应解码全字段`() {
        val dto = json.decodeFromString<CollectionsResponseDto>(
            """
            {
              "data": [
                {
                  "subject_id": 1234,
                  "subject_type": 2,
                  "type": 3,
                  "rate": 9,
                  "comment": "好看",
                  "tags": ["科幻", "原创"],
                  "ep_status": 12,
                  "vol_status": 0,
                  "updated_at": "2024-05-01T12:34:56.000Z",
                  "subject": {
                    "id": 1234,
                    "name": "Example",
                    "name_cn": "示例",
                    "type": 2,
                    "summary": "简介",
                    "images": {"large": "https://lain.bgm.tv/pic/cover/l/12.jpg"},
                    "total_episodes": 24,
                    "eps": 24,
                    "rating": {"rank": 100, "total": 3, "score": 8.1},
                    "date": "2024-04",
                    "air_weekday": 1,
                    "tags": [{"name": "科幻", "count": 55}]
                  }
                }
              ],
              "total": 1,
              "limit": 100,
              "offset": 0
            }
            """.trimIndent(),
        )
        assertEquals(1, dto.total)
        assertEquals(100, dto.limit)
        assertEquals(0, dto.offset)

        val item: UserCollectionDto = dto.data.first()
        assertEquals(1234L, item.subjectId)
        assertEquals(2, item.subjectType)
        assertEquals(3, item.type)
        assertEquals(9, item.rate)
        assertEquals("好看", item.comment)
        assertEquals(listOf("科幻", "原创"), item.tags)
        assertEquals(12, item.epStatus)
        assertEquals("2024-05-01T12:34:56.000Z", item.updatedAt)

        val subject = item.subject!!
        assertEquals("Example", subject.name)
        assertEquals("示例", subject.nameCn)
        assertEquals("https://lain.bgm.tv/pic/cover/l/12.jpg", subject.images?.large)
        assertEquals(24, subject.totalEpisodes)
        assertEquals(8.1, subject.rating?.score ?: 0.0, 0.001)
        assertEquals("2024-04", subject.date)
    }

    @Test
    fun `me 空响应不崩溃使用默认值`() {
        val dto = json.decodeFromString<UserMeDto>("{}")
        assertEquals(0L, dto.id)
        assertEquals("", dto.username)
    }

    @Test
    fun `collection 未知键被忽略`() {
        val dto = json.decodeFromString<UserCollectionDto>(
            """{"subject_id": 1, "type": 2, "some_new_field": {"x": 1}}""",
        )
        assertEquals(1L, dto.subjectId)
        assertEquals(2, dto.type)
        assertTrue(dto.tags.isEmpty())
    }
}