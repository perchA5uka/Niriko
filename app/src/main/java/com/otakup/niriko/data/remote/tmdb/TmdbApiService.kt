package com.otakup.niriko.data.remote.tmdb

import com.otakup.niriko.data.remote.tmdb.dto.TmdbConfigurationDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbExternalIdsDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbFindResultDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbImagesDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbMovieSummaryDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbPagedDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbSeasonDetailDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbTvDetailDto
import com.otakup.niriko.data.remote.tmdb.dto.TmdbTvSummaryDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDb v3 API。
 *
 * api_key 由 [TmdbClient] 的拦截器统一附加（用户可在设置页随时更换 key），
 * 因此这里每个方法都不带 key 参数。
 */
interface TmdbApiService {

    /** Key 校验端点：key 无效时返回 401。 */
    @GET("3/configuration")
    suspend fun configuration(): TmdbConfigurationDto

    @GET("3/search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("language") language: String = TmdbLanguage.DEFAULT.code,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("first_air_date_year") firstAirDateYear: Int? = null,
    ): TmdbPagedDto<TmdbTvSummaryDto>

    @GET("3/search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("language") language: String = TmdbLanguage.DEFAULT.code,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("year") year: Int? = null,
    ): TmdbPagedDto<TmdbMovieSummaryDto>

    /** 剧集详情；appendToResponse 传 "external_ids" 可一次带回 IMDb id。 */
    @GET("3/tv/{tvId}")
    suspend fun tvDetail(
        @Path("tvId") tvId: Int,
        @Query("language") language: String = TmdbLanguage.DEFAULT.code,
        @Query("append_to_response") appendToResponse: String? = "external_ids",
    ): TmdbTvDetailDto

    /** 季详情：**一次调用返回整季每集评分 / 票数 / 剧照**。 */
    @GET("3/tv/{tvId}/season/{seasonNumber}")
    suspend fun seasonDetail(
        @Path("tvId") tvId: Int,
        @Path("seasonNumber") seasonNumber: Int,
        @Query("language") language: String = TmdbLanguage.DEFAULT.code,
    ): TmdbSeasonDetailDto

    /** 单集 IMDb id（用于叠加 OMDb 逐集评分）。 */
    @GET("3/tv/{tvId}/season/{seasonNumber}/episode/{episodeNumber}/external_ids")
    suspend fun episodeExternalIds(
        @Path("tvId") tvId: Int,
        @Path("seasonNumber") seasonNumber: Int,
        @Path("episodeNumber") episodeNumber: Int,
    ): TmdbExternalIdsDto

    /** 剧集候选海报 / 背景图。include_image_language 用逗号分隔（含 null 表示无语言图）。 */
    @GET("3/tv/{tvId}/images")
    suspend fun tvImages(
        @Path("tvId") tvId: Int,
        @Query("include_image_language") includeImageLanguage: String = "zh,ja,en,null",
    ): TmdbImagesDto

    @GET("3/movie/{movieId}/images")
    suspend fun movieImages(
        @Path("movieId") movieId: Int,
        @Query("include_image_language") includeImageLanguage: String = "zh,ja,en,null",
    ): TmdbImagesDto

    @GET("3/movie/{movieId}")
    suspend fun movieDetail(
        @Path("movieId") movieId: Int,
        @Query("language") language: String = TmdbLanguage.DEFAULT.code,
        @Query("append_to_response") appendToResponse: String? = "external_ids",
    ): com.otakup.niriko.data.remote.tmdb.dto.TmdbMovieDetailDto

    /** 由 IMDb id 反查 TMDb 条目。 */
    @GET("3/find/{externalId}")
    suspend fun findByExternalId(
        @Path("externalId") externalId: String,
        @Query("external_source") externalSource: String,
        @Query("language") language: String = TmdbLanguage.DEFAULT.code,
    ): TmdbFindResultDto
}
