package com.otakup.niriko.data.remote.bangumi

import com.otakup.niriko.data.remote.bangumi.dto.CollectionsResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.UserMeDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Bangumi v0 用户/收藏接口（需要 Bearer Access Token）。
 *
 * 路径对齐 Bangumi 官方 v0 API（参考 Kazumi request/apis/bangumi_api.dart 与
 * Bangumi-master src/utils/fetch.v0）：
 * - `GET /v0/me`                                  当前用户（token 校验）
 * - `GET /v0/users/{username}/collections`        用户收藏（分页，subject_type=2 动画）
 * - `PUT /v0/users/-/collections/{subjectId}`     写入收藏（Bangumi-master 用 PUT；
 *                                                 若端点不支持会自动转 POST，见实现注释）
 */
interface BangumiAuthApi {

    @GET("v0/me")
    suspend fun me(): UserMeDto

    @GET("v0/users/{username}/collections")
    suspend fun collections(
        @Path("username") username: String,
        @Query("subject_type") subjectType: Int = 2,
        @Query("type") type: Int,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): CollectionsResponseDto

    /**
     * 写入/更新收藏状态。body = {"type": n}，n 为 Bangumi 官方 CollectionType（1-5）。
     * 部分反代端点不支持 PUT（502/405），调用方应捕获后改用 POST 同 endpoint。
     */
    @PUT("v0/users/-/collections/{subjectId}")
    suspend fun updateCollection(
        @Path("subjectId") subjectId: Long,
        @Body body: UpdateCollectionRequest,
    )

    @POST("v0/users/-/collections/{subjectId}")
    suspend fun createCollection(
        @Path("subjectId") subjectId: Long,
        @Body body: UpdateCollectionRequest,
    )
}

@Serializable
data class UpdateCollectionRequest(
    /** Bangumi 官方 CollectionType：1=想看 2=看过 3=在看 4=搁置 5=抛弃。 */
    val type: Int,
    /** 看到话数（可选）。 */
    @SerialName("ep_status")
    val epStatus: Int? = null,
    /** 用户评分 0-10（可选）。 */
    val rate: Int? = null,
    /** 评论（可选，二期支持）。 */
    val comment: String? = null,
    /** 标签（可选）。 */
    val tags: List<String>? = null,
)