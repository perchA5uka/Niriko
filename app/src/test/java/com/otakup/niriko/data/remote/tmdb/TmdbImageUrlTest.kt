package com.otakup.niriko.data.remote.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbImageUrlTest {

    @Test
    fun `路径拼成完整URL`() {
        TmdbImageUrl.imageBaseUrl = TmdbImageUrl.DEFAULT_IMAGE_BASE
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            TmdbImageUrl.url("/abc.jpg", TmdbImageUrl.POSTER_LARGE),
        )
    }

    @Test
    fun `缺失前导斜杠也会被规范化`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w185/abc.jpg",
            TmdbImageUrl.url("abc.jpg", TmdbImageUrl.POSTER_SMALL),
        )
    }

    @Test
    fun `空路径返回null`() {
        assertNull(TmdbImageUrl.url(null))
        assertNull(TmdbImageUrl.url("   "))
    }

    @Test
    fun `完整URL原样返回`() {
        val url = "https://image.tmdb.org/t/p/w500/abc.jpg"
        assertEquals(url, TmdbImageUrl.url(url))
    }

    @Test
    fun `从完整URL反解出存储路径`() {
        assertEquals(
            "/abc.jpg",
            TmdbImageUrl.storagePath("https://image.tmdb.org/t/p/w500/abc.jpg"),
        )
    }

    @Test
    fun `反解支持带子目录的路径`() {
        assertEquals(
            "/profiles/frieren.jpg",
            TmdbImageUrl.storagePath("https://image.tmdb.org/t/p/w185/profiles/frieren.jpg"),
        )
    }

    @Test
    fun `已是路径时规范化返回`() {
        assertEquals("/abc.jpg", TmdbImageUrl.storagePath("/abc.jpg"))
        assertEquals("/abc.jpg", TmdbImageUrl.storagePath("abc.jpg"))
    }

    @Test
    fun `切换镜像基地址后生效`() {
        TmdbImageUrl.imageBaseUrl = "https://img.example.com/t/p/"
        assertEquals(
            "https://img.example.com/t/p/w300/abc.jpg",
            TmdbImageUrl.url("/abc.jpg", TmdbImageUrl.STILL_MEDIUM),
        )
        TmdbImageUrl.imageBaseUrl = TmdbImageUrl.DEFAULT_IMAGE_BASE
    }
}
