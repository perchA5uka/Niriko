package com.otakup.niriko.data.remote.vndb

import com.otakup.niriko.data.remote.vndb.dto.VndbQueryRequest
import com.otakup.niriko.data.remote.vndb.dto.VndbQueryResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * VNDB REST API v2（Kana）查询接口。
 *
 * 全部为 POST /kana/vn 查询（VNDB 自定义过滤器语法，无需 API key；
 * 限流约 200 次/5 分钟，需带 User-Agent）。
 * 参照 GalSpace 项目与官方 api-kana.md。
 */
interface VndbApiService {

    /** 查询视觉小说（搜索/详情/标签均用此端点，filters 区分）。 */
    @POST("vn")
    suspend fun query(@Body request: VndbQueryRequest): VndbQueryResponse
}
