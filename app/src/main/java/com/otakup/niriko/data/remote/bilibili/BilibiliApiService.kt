package com.otakup.niriko.data.remote.bilibili

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Bilibili 番剧(番剧区 PGC) 评分 API。
 * 免登录、纯 UA 即可访问(实测 season_id 有效时返回 code=0, result.rating{score,count})。
 */
interface BilibiliApiService {

    /**
     * 获取番剧季详情(含社区评分)。
     * @param seasonId 季 ID(bilibili_site_map 里的 b / bhmt 值)
     */
    @GET("pgc/view/web/season")
    suspend fun getSeasonDetail(
        @Query("season_id") seasonId: Long,
    ): BilibiliSeasonResponseDto
}